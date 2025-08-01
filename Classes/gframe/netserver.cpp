#include "config.h"
#include "netserver.h"
#include "single_duel.h"
#include "tag_duel.h"
#include "deck_manager.h"
#include <thread>

namespace ygo {
std::unordered_map<bufferevent*, DuelPlayer> NetServer::users;
unsigned short NetServer::server_port = 0;
event_base* NetServer::net_evbase = 0;
event* NetServer::broadcast_ev = 0;
evconnlistener* NetServer::listener = 0;
DuelMode* NetServer::duel_mode = 0;
unsigned char NetServer::net_server_write[SIZE_NETWORK_BUFFER];
size_t NetServer::last_sent = 0;

bool NetServer::StartServer(unsigned short port) {
	if(net_evbase)
		return false;
	net_evbase = event_base_new();
	if(!net_evbase)
		return false;
	sockaddr_in sin;
	std::memset(&sin, 0, sizeof sin);
	server_port = port;
	sin.sin_family = AF_INET;
	sin.sin_addr.s_addr = htonl(INADDR_ANY);
	sin.sin_port = htons(port);
	listener = evconnlistener_new_bind(net_evbase, ServerAccept, nullptr,
	                                   LEV_OPT_CLOSE_ON_FREE | LEV_OPT_REUSEABLE, -1, (sockaddr*)&sin, sizeof(sin));
	if(!listener) {
		event_base_free(net_evbase);
		net_evbase = 0;
		return false;
	}
	evconnlistener_set_error_cb(listener, ServerAcceptError);
	std::thread(ServerThread).detach();
	return true;
}
bool NetServer::StartBroadcast() {
	if(!net_evbase)
		return false;
	SOCKET udp = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	int opt = TRUE;
	setsockopt(udp, SOL_SOCKET, SO_BROADCAST, (const char*)&opt, sizeof opt);
	setsockopt(udp, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof opt);
	sockaddr_in addr;
	std::memset(&addr, 0, sizeof addr);
	addr.sin_family = AF_INET;
	addr.sin_port = htons(7920);
	addr.sin_addr.s_addr = 0;
	if(bind(udp, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
		closesocket(udp);
		return false;
	}
	broadcast_ev = event_new(net_evbase, udp, EV_READ | EV_PERSIST, BroadcastEvent, nullptr);
	event_add(broadcast_ev, nullptr);
	return true;
}
void NetServer::StopServer() {
	if(!net_evbase)
		return;
	if(duel_mode)
		duel_mode->EndDuel();
	event_base_loopexit(net_evbase, 0);
}
void NetServer::StopBroadcast() {
	if(!net_evbase || !broadcast_ev)
		return;
	event_del(broadcast_ev);
	evutil_socket_t fd;
	event_get_assignment(broadcast_ev, 0, &fd, 0, 0, 0);
	evutil_closesocket(fd);
	event_free(broadcast_ev);
	broadcast_ev = 0;
}
void NetServer::StopListen() {
	evconnlistener_disable(listener);
	StopBroadcast();
}
void NetServer::BroadcastEvent(evutil_socket_t fd, short events, void* arg) {
	sockaddr_in bc_addr;
	socklen_t sz = sizeof(sockaddr_in);
	char buf[256];
	int ret = recvfrom(fd, buf, 256, 0, (sockaddr*)&bc_addr, &sz);
	if(ret == -1)
		return;
	HostRequest packet;
	std::memcpy(&packet, buf, sizeof packet);
	const HostRequest* pHR = &packet;
	if(pHR->identifier == NETWORK_CLIENT_ID) {
		SOCKADDR_IN sockTo;
		sockTo.sin_addr.s_addr = bc_addr.sin_addr.s_addr;
		sockTo.sin_family = AF_INET;
		sockTo.sin_port = htons(7921);
		HostPacket hp;
		hp.identifier = NETWORK_SERVER_ID;
		hp.port = server_port;
		hp.version = PRO_VERSION;
		hp.host = duel_mode->host_info;
		BufferIO::CopyCharArray(duel_mode->name, hp.name);
		sendto(fd, (const char*)&hp, sizeof(HostPacket), 0, (sockaddr*)&sockTo, sizeof(sockTo));
	}
}
void NetServer::ServerAccept(evconnlistener* listener, evutil_socket_t fd, sockaddr* address, int socklen, void* ctx) {
	bufferevent* bev = bufferevent_socket_new(net_evbase, fd, BEV_OPT_CLOSE_ON_FREE);
	DuelPlayer dp;
	dp.name[0] = 0;
	dp.type = 0xff;
	dp.bev = bev;
	users[bev] = dp;
	bufferevent_setcb(bev, ServerEchoRead, nullptr, ServerEchoEvent, nullptr);
	bufferevent_enable(bev, EV_READ);
}
void NetServer::ServerAcceptError(evconnlistener* listener, void* ctx) {
	event_base_loopexit(net_evbase, 0);
}
/*
* packet_len: 2 bytes
* proto: 1 byte
* [data]: (packet_len - 1) bytes
*/
void NetServer::ServerEchoRead(bufferevent *bev, void *ctx) {
	evbuffer* input = bufferevent_get_input(bev);
	int len = evbuffer_get_length(input);
	if (len < 2)
		return;
	unsigned char* net_server_read = new unsigned char[SIZE_NETWORK_BUFFER];
	uint16_t packet_len = 0;
	while (len >= 2) {
		evbuffer_copyout(input, &packet_len, sizeof packet_len);
		if (len < packet_len + 2)
			break;
		int read_len = evbuffer_remove(input, net_server_read, packet_len + 2);
		if (read_len > 2)
			HandleCTOSPacket(&users[bev], &net_server_read[2], read_len - 2);
		len -= packet_len + 2;
	}
	delete[] net_server_read;
}
void NetServer::ServerEchoEvent(bufferevent* bev, short events, void* ctx) {
	if (events & (BEV_EVENT_EOF | BEV_EVENT_ERROR)) {
		DuelPlayer* dp = &users[bev];
		DuelMode* dm = dp->game;
		if(dm)
			dm->LeaveGame(dp);
		else
			DisconnectPlayer(dp);
	}
}
int NetServer::ServerThread() {
	event_base_dispatch(net_evbase);
	for(auto bit = users.begin(); bit != users.end(); ++bit) {
		bufferevent_disable(bit->first, EV_READ);
		bufferevent_free(bit->first);
	}
	users.clear();
	evconnlistener_free(listener);
	listener = 0;
	if(broadcast_ev) {
		evutil_socket_t fd;
		event_get_assignment(broadcast_ev, 0, &fd, 0, 0, 0);
		evutil_closesocket(fd);
		event_free(broadcast_ev);
		broadcast_ev = 0;
	}
	if(duel_mode) {
		event_free(duel_mode->etimer);
		delete duel_mode;
	}
	duel_mode = 0;
	event_base_free(net_evbase);
	net_evbase = 0;
	return 0;
}
void NetServer::DisconnectPlayer(DuelPlayer* dp) {
	auto bit = users.find(dp->bev);
	if(bit != users.end()) {
		bufferevent_flush(dp->bev, EV_WRITE, BEV_FLUSH);
		bufferevent_disable(dp->bev, EV_READ);
		bufferevent_free(dp->bev);
		users.erase(bit);
	}
}
void NetServer::HandleCTOSPacket(DuelPlayer* dp, unsigned char* data, int len) {
	auto pdata = data;
	unsigned char pktType = BufferIO::Read<uint8_t>(pdata);
	if((pktType != CTOS_SURRENDER) && (pktType != CTOS_CHAT) && (dp->state == 0xff || (dp->state && dp->state != pktType)))
		return;
	switch(pktType) {
	case CTOS_RESPONSE: {
		if(!dp->game || !duel_mode->pduel)
			return;
		if (len < 1 + (int)sizeof(unsigned char))
			return;
		duel_mode->GetResponse(dp, pdata, len - 1);
		break;
	}
	case CTOS_TIME_CONFIRM: {
		if(!dp->game || !duel_mode->pduel)
			return;
		duel_mode->TimeConfirm(dp);
		break;
	}
	case CTOS_CHAT: {
		if(!dp->game)
			return;
		if (len < 1 + sizeof(uint16_t) * 1)
			return;
		if (len > 1 + sizeof(uint16_t) * LEN_CHAT_MSG)
			return;
		if ((len - 1) % sizeof(uint16_t))
			return;
		duel_mode->Chat(dp, pdata, len - 1);
		break;
	}
	case CTOS_UPDATE_DECK: {
		if(!dp->game)
			return;
		if (len < 1 + (int)sizeof(int32_t) + (int)sizeof(int32_t))
			return;
		if (len > 1 + (int)sizeof(CTOS_DeckData))
			return;
		duel_mode->UpdateDeck(dp, pdata, len - 1);
		break;
	}
	case CTOS_HAND_RESULT: {
		if(!dp->game)
			return;
		if (len < 1 + (int)sizeof(CTOS_HandResult))
			return;
		CTOS_HandResult packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		dp->game->HandResult(dp, pkt->res);
		break;
	}
	case CTOS_TP_RESULT: {
		if(!dp->game)
			return;
		if (len < 1 + (int)sizeof(CTOS_TPResult))
			return;
		CTOS_TPResult packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		dp->game->TPResult(dp, pkt->res);
		break;
	}
	case CTOS_PLAYER_INFO: {
		if (len < 1 + (int)sizeof(CTOS_PlayerInfo))
			return;
		CTOS_PlayerInfo packet;
		std::memcpy(&packet, pdata, sizeof packet);
		auto pkt = &packet;
		BufferIO::NullTerminate(pkt->name);
		BufferIO::CopyCharArray(pkt->name, dp->name);
		break;
	}
	case CTOS_EXTERNAL_ADDRESS: {
		// for other server & reverse proxy use only
		/*
		wchar_t hostname[LEN_HOSTNAME];
		uint32_t real_ip = ntohl(BufferIO::Read<int32_t>(pdata));
		BufferIO::CopyCharArray((uint16_t*)pdata, hostname);
		*/
		break;
	}
	case CTOS_CREATE_GAME: {
		if(dp->game || duel_mode)
			return;
		if (len < 1 + (int)sizeof(CTOS_CreateGame))
			return;
		CTOS_CreateGame packet;
		std::memcpy(&packet, pdata, sizeof packet);
		auto pkt = &packet;
		if(pkt->info.rule > CURRENT_RULE)
			pkt->info.rule = CURRENT_RULE;
		if(pkt->info.mode > MODE_TAG)
			pkt->info.mode = MODE_SINGLE;
		bool found = false;
		for (const auto& lflist : deckManager._lfList) {
			if(pkt->info.lflist == lflist.hash) {
				found = true;
				break;
			}
		}
		if (!found) {
			if (deckManager._lfList.size())
				pkt->info.lflist = deckManager._lfList[0].hash;
			else
				pkt->info.lflist = 0;
		}
		if (pkt->info.mode == MODE_SINGLE) {
			duel_mode = new SingleDuel(false);
			duel_mode->etimer = event_new(net_evbase, 0, EV_TIMEOUT | EV_PERSIST, SingleDuel::SingleTimer, duel_mode);
		}
		else if (pkt->info.mode == MODE_MATCH) {
			duel_mode = new SingleDuel(true);
			duel_mode->etimer = event_new(net_evbase, 0, EV_TIMEOUT | EV_PERSIST, SingleDuel::SingleTimer, duel_mode);
		}
		else if (pkt->info.mode == MODE_TAG) {
			duel_mode = new TagDuel();
			duel_mode->etimer = event_new(net_evbase, 0, EV_TIMEOUT | EV_PERSIST, TagDuel::TagTimer, duel_mode);
		}
		else
			return;
#ifdef _IRR_ANDROID_PLATFORM_
        HostInfo tmp;
        memcpy(&tmp, &pkt->info, sizeof(struct HostInfo));
        duel_mode->host_info = tmp;
#else
		duel_mode->host_info = pkt->info;
#endif
		BufferIO::NullTerminate(pkt->name);
		BufferIO::NullTerminate(pkt->pass);
		BufferIO::CopyCharArray(pkt->name, duel_mode->name);
		BufferIO::CopyCharArray(pkt->pass, duel_mode->pass);
		duel_mode->JoinGame(dp, 0, true);
		StartBroadcast();
		break;
	}
	case CTOS_JOIN_GAME: {
		if (!duel_mode)
			return;
		if (len < 1 + (int)sizeof(CTOS_JoinGame))
			return;
		duel_mode->JoinGame(dp, pdata, false);
		break;
	}
	case CTOS_LEAVE_GAME: {
		if (!duel_mode)
			return;
		duel_mode->LeaveGame(dp);
		break;
	}
	case CTOS_SURRENDER: {
		if (!duel_mode)
			return;
		duel_mode->Surrender(dp);
		break;
	}
	case CTOS_HS_TODUELIST: {
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->ToDuelist(dp);
		break;
	}
	case CTOS_HS_TOOBSERVER: {
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->ToObserver(dp);
		break;
	}
	case CTOS_HS_READY:
	case CTOS_HS_NOTREADY: {
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->PlayerReady(dp, (CTOS_HS_NOTREADY - pktType) != 0);
		break;
	}
	case CTOS_HS_KICK: {
		if (!duel_mode || duel_mode->pduel)
			return;
		if (len < 1 + (int)sizeof(CTOS_Kick))
			return;
		CTOS_Kick packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		duel_mode->PlayerKick(dp, pkt->pos);
		break;
	}
	case CTOS_HS_START: {
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->StartDuel(dp);
		break;
	}
	}
}
size_t NetServer::CreateChatPacket(unsigned char* src, int src_size, unsigned char* dst, uint16_t dst_player_type) {
	uint16_t src_msg[LEN_CHAT_MSG];
	std::memcpy(src_msg, src, src_size);
	const int src_len = src_size / sizeof(uint16_t);
	if (src_msg[src_len - 1] != 0)
		return 0;
	// STOC_Chat packet
	auto pdst = dst;
	BufferIO::Write<uint16_t>(pdst, dst_player_type);
	std::memcpy(pdst, src_msg, src_size);
	pdst += src_size;
	return sizeof(dst_player_type) + src_size;
}

}

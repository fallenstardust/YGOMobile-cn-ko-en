#include "config.h"
#include "tag_duel.h"
#include "netserver.h"
#include "game.h"
#include "data_manager.h"
#include "../ocgcore/mtrandom.h"

namespace ygo {

TagDuel::TagDuel() {
	for(int i = 0; i < 4; ++i) {
		players[i] = 0;
		ready[i] = false;
		surrender[i] = false;
	}
}
TagDuel::~TagDuel() {
}
void TagDuel::Chat(DuelPlayer* dp, unsigned char* pdata, int len) {
	unsigned char scc[SIZE_STOC_CHAT];
	const auto scc_size = NetServer::CreateChatPacket(pdata, len, scc, dp->type);
	if (!scc_size)
		return;
	for(int i = 0; i < 4; ++i)
		NetServer::SendBufferToPlayer(players[i], STOC_CHAT, scc, scc_size);
	for(auto pit = observers.begin(); pit != observers.end(); ++pit)
		NetServer::ReSendToPlayer(*pit);
}
void TagDuel::JoinGame(DuelPlayer* dp, unsigned char* pdata, bool is_creater) {
	if(!is_creater) {
		if(dp->game && dp->type != 0xff) {
			STOC_ErrorMsg scem;
			scem.msg = ERRMSG_JOINERROR;
			scem.code = 0;
			NetServer::SendPacketToPlayer(dp, STOC_ERROR_MSG, scem);
			NetServer::DisconnectPlayer(dp);
			return;
		}
		CTOS_JoinGame packet;
		std::memcpy(&packet, pdata, sizeof packet);
		auto pkt = &packet;
		if(pkt->version != PRO_VERSION) {
			STOC_ErrorMsg scem;
			scem.msg = ERRMSG_VERERROR;
			scem.code = PRO_VERSION;
			NetServer::SendPacketToPlayer(dp, STOC_ERROR_MSG, scem);
			NetServer::DisconnectPlayer(dp);
			return;
		}
		wchar_t jpass[20];
		BufferIO::NullTerminate(pkt->pass);
		BufferIO::CopyCharArray(pkt->pass, jpass);
		if(std::wcscmp(jpass, pass)) {
			STOC_ErrorMsg scem;
			scem.msg = ERRMSG_JOINERROR;
			scem.code = 1;
			NetServer::SendPacketToPlayer(dp, STOC_ERROR_MSG, scem);
			return;
		}
	}
	dp->game = this;
	if(!players[0] && !players[1] && !players[2] && !players[3] && observers.size() == 0)
		host_player = dp;
	STOC_JoinGame scjg;
	scjg.info = host_info;
	STOC_TypeChange sctc;
	sctc.type = (host_player == dp) ? 0x10 : 0;
	if(!players[0] || !players[1] || !players[2] || !players[3]) {
		STOC_HS_PlayerEnter scpe;
		BufferIO::CopyCharArray(dp->name, scpe.name);
		if(!players[0])
			scpe.pos = 0;
		else if(!players[1])
			scpe.pos = 1;
		else if(!players[2])
			scpe.pos = 2;
		else
			scpe.pos = 3;
		for(int i = 0; i < 4; ++i)
			if(players[i])
				NetServer::SendPacketToPlayer(players[i], STOC_HS_PLAYER_ENTER, scpe);
		for(auto pit = observers.begin(); pit != observers.end(); ++pit)
			NetServer::SendPacketToPlayer(*pit, STOC_HS_PLAYER_ENTER, scpe);
		players[scpe.pos] = dp;
		dp->type = scpe.pos;
		sctc.type |= scpe.pos;
	} else {
		observers.insert(dp);
		dp->type = NETPLAYER_TYPE_OBSERVER;
		sctc.type |= NETPLAYER_TYPE_OBSERVER;
		STOC_HS_WatchChange scwc;
		scwc.watch_count = (unsigned short)observers.size();
		for(int i = 0; i < 4; ++i)
			if(players[i])
				NetServer::SendPacketToPlayer(players[i], STOC_HS_WATCH_CHANGE, scwc);
		for(auto pit = observers.begin(); pit != observers.end(); ++pit)
			NetServer::SendPacketToPlayer(*pit, STOC_HS_WATCH_CHANGE, scwc);
	}
	NetServer::SendPacketToPlayer(dp, STOC_JOIN_GAME, scjg);
	NetServer::SendPacketToPlayer(dp, STOC_TYPE_CHANGE, sctc);
	for(int i = 0; i < 4; ++i)
		if(players[i]) {
			STOC_HS_PlayerEnter scpe;
			BufferIO::CopyCharArray(players[i]->name, scpe.name);
			scpe.pos = i;
			NetServer::SendPacketToPlayer(dp, STOC_HS_PLAYER_ENTER, scpe);
			if(ready[i]) {
				STOC_HS_PlayerChange scpc;
				scpc.status = (i << 4) | PLAYERCHANGE_READY;
				NetServer::SendPacketToPlayer(dp, STOC_HS_PLAYER_CHANGE, scpc);
			}
		}
	if(observers.size()) {
		STOC_HS_WatchChange scwc;
		scwc.watch_count = (unsigned short)observers.size();
		NetServer::SendPacketToPlayer(dp, STOC_HS_WATCH_CHANGE, scwc);
	}
}
void TagDuel::LeaveGame(DuelPlayer* dp) {
	if(dp == host_player) {
		EndDuel();
		NetServer::StopServer();
	} else if(dp->type == NETPLAYER_TYPE_OBSERVER) {
		observers.erase(dp);
		if(duel_stage == DUEL_STAGE_BEGIN) {
			STOC_HS_WatchChange scwc;
			scwc.watch_count = (unsigned short)observers.size();
			for(int i = 0; i < 4; ++i)
				if(players[i])
					NetServer::SendPacketToPlayer(players[i], STOC_HS_WATCH_CHANGE, scwc);
			for(auto pit = observers.begin(); pit != observers.end(); ++pit)
				NetServer::SendPacketToPlayer(*pit, STOC_HS_WATCH_CHANGE, scwc);
		}
		NetServer::DisconnectPlayer(dp);
	} else {
		if(duel_stage == DUEL_STAGE_BEGIN) {
			STOC_HS_PlayerChange scpc;
			players[dp->type] = 0;
			ready[dp->type] = false;
			scpc.status = (dp->type << 4) | PLAYERCHANGE_LEAVE;
			for(int i = 0; i < 4; ++i)
				if(players[i])
					NetServer::SendPacketToPlayer(players[i], STOC_HS_PLAYER_CHANGE, scpc);
			for(auto pit = observers.begin(); pit != observers.end(); ++pit)
				NetServer::SendPacketToPlayer(*pit, STOC_HS_PLAYER_CHANGE, scpc);
			NetServer::DisconnectPlayer(dp);
		} else if(duel_stage != DUEL_STAGE_END) {
			EndDuel();
			DuelEndProc();
		}
		NetServer::DisconnectPlayer(dp);
	}
}
void TagDuel::ToDuelist(DuelPlayer* dp) {
	if(players[0] && players[1] && players[2] && players[3])
		return;
	if(dp->type == NETPLAYER_TYPE_OBSERVER) {
		observers.erase(dp);
		STOC_HS_PlayerEnter scpe;
		BufferIO::CopyCharArray(dp->name, scpe.name);
		if(!players[0])
			dp->type = 0;
		else if(!players[1])
			dp->type = 1;
		else if(!players[2])
			dp->type = 2;
		else
			dp->type = 3;
		players[dp->type] = dp;
		scpe.pos = dp->type;
		STOC_HS_WatchChange scwc;
		scwc.watch_count = (unsigned short)observers.size();
		for(int i = 0; i < 4; ++i)
			if(players[i]) {
				NetServer::SendPacketToPlayer(players[i], STOC_HS_PLAYER_ENTER, scpe);
				NetServer::SendPacketToPlayer(players[i], STOC_HS_WATCH_CHANGE, scwc);
			}
		for(auto pit = observers.begin(); pit != observers.end(); ++pit) {
			NetServer::SendPacketToPlayer(*pit, STOC_HS_PLAYER_ENTER, scpe);
			NetServer::SendPacketToPlayer(*pit, STOC_HS_WATCH_CHANGE, scwc);
		}
		STOC_TypeChange sctc;
		sctc.type = (dp == host_player ? 0x10 : 0) | dp->type;
		NetServer::SendPacketToPlayer(dp, STOC_TYPE_CHANGE, sctc);
	} else {
		if(ready[dp->type])
			return;
		unsigned char dptype = (dp->type + 1) % 4;
		while(players[dptype])
			dptype = (dptype + 1) % 4;
		STOC_HS_PlayerChange scpc;
		scpc.status = (dp->type << 4) | dptype;
		for(int i = 0; i < 4; ++i)
			if(players[i])
				NetServer::SendPacketToPlayer(players[i], STOC_HS_PLAYER_CHANGE, scpc);
		for(auto pit = observers.begin(); pit != observers.end(); ++pit)
			NetServer::SendPacketToPlayer(*pit, STOC_HS_PLAYER_CHANGE, scpc);
		STOC_TypeChange sctc;
		sctc.type = (dp == host_player ? 0x10 : 0) | dptype;
		NetServer::SendPacketToPlayer(dp, STOC_TYPE_CHANGE, sctc);
		players[dptype] = dp;
		players[dp->type] = 0;
		dp->type = dptype;
	}
}
void TagDuel::ToObserver(DuelPlayer* dp) {
	if(dp->type > 3)
		return;
	STOC_HS_PlayerChange scpc;
	scpc.status = (dp->type << 4) | PLAYERCHANGE_OBSERVE;
	for(int i = 0; i < 4; ++i)
		if(players[i])
			NetServer::SendPacketToPlayer(players[i], STOC_HS_PLAYER_CHANGE, scpc);
	for(auto pit = observers.begin(); pit != observers.end(); ++pit)
		NetServer::SendPacketToPlayer(*pit, STOC_HS_PLAYER_CHANGE, scpc);
	players[dp->type] = 0;
	ready[dp->type] = false;
	dp->type = NETPLAYER_TYPE_OBSERVER;
	observers.insert(dp);
	STOC_TypeChange sctc;
	sctc.type = (dp == host_player ? 0x10 : 0) | dp->type;
	NetServer::SendPacketToPlayer(dp, STOC_TYPE_CHANGE, sctc);
}
void TagDuel::PlayerReady(DuelPlayer* dp, bool is_ready) {
	if(dp->type > 3 || ready[dp->type] == is_ready)
		return;
	if(is_ready) {
		unsigned int deckerror = 0;
		if(!host_info.no_check_deck) {
			if(deck_error[dp->type]) {
				deckerror = (DECKERROR_UNKNOWNCARD << 28) + deck_error[dp->type];
			} else {
				deckerror = deckManager.CheckDeck(pdeck[dp->type], host_info.lflist, host_info.rule);
			}
		}
		if(deckerror) {
			STOC_HS_PlayerChange scpc;
			scpc.status = (dp->type << 4) | PLAYERCHANGE_NOTREADY;
			NetServer::SendPacketToPlayer(dp, STOC_HS_PLAYER_CHANGE, scpc);
			STOC_ErrorMsg scem;
			scem.msg = ERRMSG_DECKERROR;
			scem.code = deckerror;
			NetServer::SendPacketToPlayer(dp, STOC_ERROR_MSG, scem);
			return;
		}
	}
	ready[dp->type] = is_ready;
	STOC_HS_PlayerChange scpc;
	scpc.status = (dp->type << 4) | (is_ready ? PLAYERCHANGE_READY : PLAYERCHANGE_NOTREADY);
	for(int i = 0; i < 4; ++i)
		if(players[i])
			NetServer::SendPacketToPlayer(players[i], STOC_HS_PLAYER_CHANGE, scpc);
	for(auto pit = observers.begin(); pit != observers.end(); ++pit)
		NetServer::SendPacketToPlayer(*pit, STOC_HS_PLAYER_CHANGE, scpc);
}
void TagDuel::PlayerKick(DuelPlayer* dp, unsigned char pos) {
	if(pos > 3 || dp != host_player || dp == players[pos] || !players[pos])
		return;
	LeaveGame(players[pos]);
}
void TagDuel::UpdateDeck(DuelPlayer* dp, unsigned char* pdata, int len) {
	if(dp->type > 3 || ready[dp->type])
		return;
	if (len < 8 || len > sizeof(CTOS_DeckData))
		return;
	bool valid = true;
	CTOS_DeckData deckbuf;
	std::memcpy(&deckbuf, pdata, len);
	if (deckbuf.mainc < 0 || deckbuf.mainc > MAINC_MAX)
		valid = false;
	else if (deckbuf.sidec < 0 || deckbuf.sidec > SIDEC_MAX)
		valid = false;
	else if (len < (2 + deckbuf.mainc + deckbuf.sidec) * (int)sizeof(int32_t))
		valid = false;
	if (!valid) {
		STOC_ErrorMsg scem;
		scem.msg = ERRMSG_DECKERROR;
		scem.code = 0;
		NetServer::SendPacketToPlayer(dp, STOC_ERROR_MSG, scem);
		return;
	}
	deck_error[dp->type] = DeckManager::LoadDeck(pdeck[dp->type], deckbuf.list, deckbuf.mainc, deckbuf.sidec);
}
void TagDuel::StartDuel(DuelPlayer* dp) {
	if(dp != host_player)
		return;
	if(!ready[0] || !ready[1] || !ready[2] || !ready[3])
		return;
	NetServer::StopListen();
	//NetServer::StopBroadcast();
	for(int i = 0; i < 4; ++i)
		NetServer::SendPacketToPlayer(players[i], STOC_DUEL_START);
	for(auto oit = observers.begin(); oit != observers.end(); ++oit) {
		(*oit)->state = CTOS_LEAVE_GAME;
		NetServer::ReSendToPlayer(*oit);
	}
	unsigned char deckbuff[12];
	auto pbuf = deckbuff;
	BufferIO::Write<uint16_t>(pbuf, (uint16_t)pdeck[0].main.size());
	BufferIO::Write<uint16_t>(pbuf, (uint16_t)pdeck[0].extra.size());
	BufferIO::Write<uint16_t>(pbuf, (uint16_t)pdeck[0].side.size());
	BufferIO::Write<uint16_t>(pbuf, (uint16_t)pdeck[2].main.size());
	BufferIO::Write<uint16_t>(pbuf, (uint16_t)pdeck[2].extra.size());
	BufferIO::Write<uint16_t>(pbuf, (uint16_t)pdeck[2].side.size());
	NetServer::SendBufferToPlayer(players[0], STOC_DECK_COUNT, deckbuff, 12);
	NetServer::ReSendToPlayer(players[1]);
	char tempbuff[6];
	std::memcpy(tempbuff, deckbuff, 6);
	std::memcpy(deckbuff, deckbuff + 6, 6);
	std::memcpy(deckbuff + 6, tempbuff, 6);
	NetServer::SendBufferToPlayer(players[2], STOC_DECK_COUNT, deckbuff, 12);
	NetServer::ReSendToPlayer(players[3]);
	NetServer::SendPacketToPlayer(players[0], STOC_SELECT_HAND);
	NetServer::ReSendToPlayer(players[2]);
	hand_result[0] = 0;
	hand_result[1] = 0;
	players[0]->state = CTOS_HAND_RESULT;
	players[2]->state = CTOS_HAND_RESULT;
	duel_stage = DUEL_STAGE_FINGER;
}
void TagDuel::HandResult(DuelPlayer* dp, unsigned char res) {
	if(res > 3 || dp->state != CTOS_HAND_RESULT)
		return;
	if(dp->type == 0)
		hand_result[0] = res;
	else
		hand_result[1] = res;
	if(hand_result[0] && hand_result[1]) {
		STOC_HandResult schr;
		schr.res1 = hand_result[0];
		schr.res2 = hand_result[1];
		NetServer::SendPacketToPlayer(players[0], STOC_HAND_RESULT, schr);
		NetServer::ReSendToPlayer(players[1]);
		for(auto oit = observers.begin(); oit != observers.end(); ++oit)
			NetServer::ReSendToPlayer(*oit);
		schr.res1 = hand_result[1];
		schr.res2 = hand_result[0];
		NetServer::SendPacketToPlayer(players[2], STOC_HAND_RESULT, schr);
		NetServer::ReSendToPlayer(players[3]);
		if(hand_result[0] == hand_result[1]) {
			NetServer::SendPacketToPlayer(players[0], STOC_SELECT_HAND);
			NetServer::ReSendToPlayer(players[2]);
			hand_result[0] = 0;
			hand_result[1] = 0;
			players[0]->state = CTOS_HAND_RESULT;
			players[2]->state = CTOS_HAND_RESULT;
		} else if((hand_result[0] == 1 && hand_result[1] == 2)
		          || (hand_result[0] == 2 && hand_result[1] == 3)
		          || (hand_result[0] == 3 && hand_result[1] == 1)) {
			NetServer::SendPacketToPlayer(players[2], STOC_SELECT_TP);
			players[0]->state = 0xff;
			players[2]->state = CTOS_TP_RESULT;
			duel_stage = DUEL_STAGE_FIRSTGO;
		} else {
			NetServer::SendPacketToPlayer(players[0], STOC_SELECT_TP);
			players[2]->state = 0xff;
			players[0]->state = CTOS_TP_RESULT;
			duel_stage = DUEL_STAGE_FIRSTGO;
		}
	}
}
void TagDuel::TPResult(DuelPlayer* dp, unsigned char tp) {
	if(dp->state != CTOS_TP_RESULT)
		return;
	duel_stage = DUEL_STAGE_DUELING;
	bool swapped = false;
	pplayer[0] = players[0];
	pplayer[1] = players[1];
	pplayer[2] = players[2];
	pplayer[3] = players[3];
	if((tp && dp->type == 2) || (!tp && dp->type == 0)) {
		std::swap(players[0], players[2]);
		std::swap(players[1], players[3]);
		players[0]->type = 0;
		players[1]->type = 1;
		players[2]->type = 2;
		players[3]->type = 3;
		std::swap(pdeck[0], pdeck[2]);
		std::swap(pdeck[1], pdeck[3]);
		swapped = true;
	}
	turn_count = 0;
	cur_player[0] = players[0];
	cur_player[1] = players[3];
	dp->state = CTOS_RESPONSE;
	std::random_device rd;
	ExtendedReplayHeader rh;
	rh.base.id = REPLAY_ID_YRP2;
	rh.base.version = PRO_VERSION;
	rh.base.flag = REPLAY_UNIFORM | REPLAY_TAG;
	rh.base.start_time = (uint32_t)std::time(nullptr);
	for (auto& x : rh.seed_sequence)
		x = rd();
	mtrandom rnd(rh.seed_sequence, SEED_COUNT);
	last_replay.BeginRecord();
	last_replay.WriteHeader(rh);
	last_replay.WriteData(players[0]->name, 40, false);
	last_replay.WriteData(players[1]->name, 40, false);
	last_replay.WriteData(players[2]->name, 40, false);
	last_replay.WriteData(players[3]->name, 40, false);
	if(!host_info.no_shuffle_deck) {
		rnd.shuffle_vector(pdeck[0].main);
		rnd.shuffle_vector(pdeck[1].main);
		rnd.shuffle_vector(pdeck[2].main);
		rnd.shuffle_vector(pdeck[3].main);
	}
	time_limit[0] = host_info.time_limit;
	time_limit[1] = host_info.time_limit;
	set_script_reader(DataManager::ScriptReaderEx);
	set_card_reader(DataManager::CardReader);
	set_message_handler(TagDuel::MessageHandler);
	pduel = create_duel_v2(rh.seed_sequence);
	set_player_info(pduel, 0, host_info.start_lp, host_info.start_hand, host_info.draw_count);
	set_player_info(pduel, 1, host_info.start_lp, host_info.start_hand, host_info.draw_count);
	unsigned int opt = (unsigned int)host_info.duel_rule << 16;
	if(host_info.no_shuffle_deck)
		opt |= DUEL_PSEUDO_SHUFFLE;
	opt |= DUEL_TAG_MODE;
	last_replay.WriteInt32(host_info.start_lp, false);
	last_replay.WriteInt32(host_info.start_hand, false);
	last_replay.WriteInt32(host_info.draw_count, false);
	last_replay.WriteInt32(opt, false);
	last_replay.Flush();
	auto load_single = [&](const std::vector<code_pointer>& deck_container, uint8_t p, uint8_t location) {
		last_replay.WriteInt32(deck_container.size(), false);
		for (auto cit = deck_container.rbegin(); cit != deck_container.rend(); ++cit) {
			new_card(pduel, (*cit)->first, p, p, location, 0, POS_FACEDOWN_DEFENSE);
			last_replay.WriteInt32((*cit)->first, false);
		}
	};
	auto load_tag = [&](const std::vector<code_pointer>& deck_container, uint8_t p, uint8_t location) {
		last_replay.WriteInt32(deck_container.size(), false);
		for (auto cit = deck_container.rbegin(); cit != deck_container.rend(); ++cit) {
			new_tag_card(pduel, (*cit)->first, p, location);
			last_replay.WriteInt32((*cit)->first, false);
		}
	};
	load_single(pdeck[0].main, 0, LOCATION_DECK);
	load_single(pdeck[0].extra, 0, LOCATION_EXTRA);
	load_tag(pdeck[1].main, 0, LOCATION_DECK);
	load_tag(pdeck[1].extra, 0, LOCATION_EXTRA);
	load_single(pdeck[3].main, 1, LOCATION_DECK);
	load_single(pdeck[3].extra, 1, LOCATION_EXTRA);
	load_tag(pdeck[2].main, 1, LOCATION_DECK);
	load_tag(pdeck[2].extra, 1, LOCATION_EXTRA);
	last_replay.Flush();
	unsigned char startbuf[32]{};
	auto pbuf = startbuf;
	BufferIO::Write<uint8_t>(pbuf, MSG_START);
	BufferIO::Write<uint8_t>(pbuf, 0);
	BufferIO::Write<uint8_t>(pbuf, host_info.duel_rule);
	BufferIO::Write<int32_t>(pbuf, host_info.start_lp);
	BufferIO::Write<int32_t>(pbuf, host_info.start_lp);
	BufferIO::Write<uint16_t>(pbuf, query_field_count(pduel, 0, LOCATION_DECK));
	BufferIO::Write<uint16_t>(pbuf, query_field_count(pduel, 0, LOCATION_EXTRA));
	BufferIO::Write<uint16_t>(pbuf, query_field_count(pduel, 1, LOCATION_DECK));
	BufferIO::Write<uint16_t>(pbuf, query_field_count(pduel, 1, LOCATION_EXTRA));
	NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, startbuf, 19);
	NetServer::ReSendToPlayer(players[1]);
	startbuf[1] = 1;
	NetServer::SendBufferToPlayer(players[2], STOC_GAME_MSG, startbuf, 19);
	NetServer::ReSendToPlayer(players[3]);
	if(!swapped)
		startbuf[1] = 0x10;
	else startbuf[1] = 0x11;
	for(auto oit = observers.begin(); oit != observers.end(); ++oit)
		NetServer::SendBufferToPlayer(*oit, STOC_GAME_MSG, startbuf, 19);
	RefreshExtra(0);
	RefreshExtra(1);
	start_duel(pduel, opt);
	if(host_info.time_limit) {
		time_elapsed = 0;
		timeval timeout = { 1, 0 };
		event_add(etimer, &timeout);
	}
	Process();
}
void TagDuel::Process() {
	std::vector<unsigned char> engineBuffer;
	engineBuffer.reserve(SIZE_MESSAGE_BUFFER);
	unsigned int engFlag = 0;
	int engLen = 0;
	int stop = 0;
	while (!stop) {
		if (engFlag == PROCESSOR_END)
			break;
		unsigned int result = process(pduel);
		engLen = result & PROCESSOR_BUFFER_LEN;
		engFlag = result & PROCESSOR_FLAG;
		if (engLen > 0) {
			if (engLen > (int)engineBuffer.size())
				engineBuffer.resize(engLen);
			get_message(pduel, engineBuffer.data());
			stop = Analyze(engineBuffer.data(), engLen);
		}
	}
	if(stop == 2)
		DuelEndProc();
}
void TagDuel::DuelEndProc() {
	NetServer::SendPacketToPlayer(players[0], STOC_DUEL_END);
	NetServer::ReSendToPlayer(players[1]);
	NetServer::ReSendToPlayer(players[2]);
	NetServer::ReSendToPlayer(players[3]);
	for(auto oit = observers.begin(); oit != observers.end(); ++oit)
		NetServer::ReSendToPlayer(*oit);
	duel_stage = DUEL_STAGE_END;
}
void TagDuel::Surrender(DuelPlayer* dp) {
	if(dp->type > 3 || !pduel)
		return;
	uint32_t player = dp->type;
	if(surrender[player])
		return;
	static const uint32_t teammatemap[] = { 1, 0, 3, 2 };
	uint32_t teammate = teammatemap[player];
	if(!surrender[teammate]) {
		surrender[player] = true;
		NetServer::SendPacketToPlayer(players[player], STOC_TEAMMATE_SURRENDER);
		NetServer::SendPacketToPlayer(players[teammate], STOC_TEAMMATE_SURRENDER);
		return;
	}
	static const uint32_t winplayermap[] = { 1, 1, 0, 0 };
	unsigned char wbuf[3];
	wbuf[0] = MSG_WIN;
	wbuf[1] = winplayermap[player];
	wbuf[2] = 0;
	NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, wbuf, 3);
	NetServer::ReSendToPlayer(players[1]);
	NetServer::ReSendToPlayer(players[2]);
	NetServer::ReSendToPlayer(players[3]);
	for(auto oit = observers.begin(); oit != observers.end(); ++oit)
		NetServer::ReSendToPlayer(*oit);
	EndDuel();
	DuelEndProc();
	event_del(etimer);
}
int TagDuel::Analyze(unsigned char* msgbuffer, unsigned int len) {
	unsigned char* offset, *pbufw, *pbuf = msgbuffer;
	int player, count, type;
	while (pbuf - msgbuffer < (int)len) {
		offset = pbuf;
		unsigned char engType = BufferIO::Read<uint8_t>(pbuf);
		switch (engType) {
		case MSG_RETRY: {
			WaitforResponse(last_response);
			NetServer::SendBufferToPlayer(cur_player[last_response], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_HINT: {
			type = BufferIO::Read<uint8_t>(pbuf);
			player = BufferIO::Read<uint8_t>(pbuf);
			BufferIO::Read<int32_t>(pbuf);
			switch (type) {
			case 1:
			case 2:
			case 3:
			case 5: {
				NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
				break;
			}
			case 4:
			case 6:
			case 7:
			case 8:
			case 9:
			case 11: {
				for(int i = 0; i < 4; ++i)
					if(players[i] != cur_player[player])
						NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, offset, pbuf - offset);
				for(auto oit = observers.begin(); oit != observers.end(); ++oit)
					NetServer::ReSendToPlayer(*oit);
				break;
			}
			case 10: {
				for(int i = 0; i < 4; ++i)
					NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, offset, pbuf - offset);
				for(auto oit = observers.begin(); oit != observers.end(); ++oit)
					NetServer::ReSendToPlayer(*oit);
				break;
			}
			}
			break;
		}
		case MSG_WIN: {
			player = BufferIO::Read<uint8_t>(pbuf);
			type = BufferIO::Read<uint8_t>(pbuf);
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			EndDuel();
			return 2;
		}
		case MSG_SELECT_BATTLECMD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 11;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 8 + 2;
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			RefreshHand(0);
			RefreshHand(1);
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_IDLECMD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 11 + 3;
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			RefreshHand(0);
			RefreshHand(1);
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_EFFECTYN: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 12;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_YESNO: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_OPTION: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_CARD:
		case MSG_SELECT_TRIBUTE: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 3;
			count = BufferIO::Read<uint8_t>(pbuf);
			int c/*, l, s, ss, code*/;
			for (int i = 0; i < count; ++i) {
				pbufw = pbuf;
				/*code = */BufferIO::Read<int32_t>(pbuf);
				c = BufferIO::Read<uint8_t>(pbuf);
				/*l = */BufferIO::Read<uint8_t>(pbuf);
				/*s = */BufferIO::Read<uint8_t>(pbuf);
				/*ss = */BufferIO::Read<uint8_t>(pbuf);
				if (c != player) BufferIO::Write<int32_t>(pbufw, 0);
			}
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_UNSELECT_CARD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4;
			count = BufferIO::Read<uint8_t>(pbuf);
			int c/*, l, s, ss, code*/;
			for (int i = 0; i < count; ++i) {
				pbufw = pbuf;
				/*code = */BufferIO::Read<int32_t>(pbuf);
				c = BufferIO::Read<uint8_t>(pbuf);
				/*l = */BufferIO::Read<uint8_t>(pbuf);
				/*s = */BufferIO::Read<uint8_t>(pbuf);
				/*ss = */BufferIO::Read<uint8_t>(pbuf);
				if (c != player) BufferIO::Write<int32_t>(pbufw, 0);
			}
			count = BufferIO::Read<uint8_t>(pbuf);
			for (int i = 0; i < count; ++i) {
				pbufw = pbuf;
				/*code = */BufferIO::Read<int32_t>(pbuf);
				c = BufferIO::Read<uint8_t>(pbuf);
				/*l = */BufferIO::Read<uint8_t>(pbuf);
				/*s = */BufferIO::Read<uint8_t>(pbuf);
				/*ss = */BufferIO::Read<uint8_t>(pbuf);
				if (c != player) BufferIO::Write<int32_t>(pbufw, 0);
			}
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_CHAIN: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 9 + count * 14;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_PLACE:
		case MSG_SELECT_DISFIELD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_POSITION: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_COUNTER: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 9;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SELECT_SUM: {
			pbuf++;
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 6;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 11;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 11;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_SORT_CARD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_CONFIRM_DECKTOP: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CONFIRM_EXTRATOP: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for (auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CONFIRM_CARDS: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 1;
			count = BufferIO::Read<uint8_t>(pbuf);
			if(pbuf[5] != LOCATION_DECK) {
				pbuf += count * 7;
				NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
				NetServer::ReSendToPlayer(players[1]);
				NetServer::ReSendToPlayer(players[2]);
				NetServer::ReSendToPlayer(players[3]);
				for(auto oit = observers.begin(); oit != observers.end(); ++oit)
					NetServer::ReSendToPlayer(*oit);
			} else {
				pbuf += count * 7;
				NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			}
			break;
		}
		case MSG_SHUFFLE_DECK: {
			player = BufferIO::Read<uint8_t>(pbuf);
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_SHUFFLE_HAND: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, (pbuf - offset) + count * 4);
			for(int i = 0; i < count; ++i)
				BufferIO::Write<int32_t>(pbuf, 0);
			for(int i = 0; i < 4; ++i)
				if(players[i] != cur_player[player])
					NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, offset, pbuf - offset);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshHand(player, 0x781fff, 0);
			break;
		}
		case MSG_SHUFFLE_EXTRA: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, (pbuf - offset) + count * 4);
			for(int i = 0; i < count; ++i)
				BufferIO::Write<int32_t>(pbuf, 0);
			for(int i = 0; i < 4; ++i)
				if(players[i] != cur_player[player])
					NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, offset, pbuf - offset);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshExtra(player);
			break;
		}
		case MSG_REFRESH_DECK: {
			pbuf++;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_SWAP_GRAVE_DECK: {
			player = BufferIO::Read<uint8_t>(pbuf);
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshGrave(player);
			break;
		}
		case MSG_REVERSE_DECK: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_DECK_TOP: {
			pbuf += 6;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_SHUFFLE_SET_CARD: {
			unsigned int loc = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			if(loc == LOCATION_MZONE) {
				RefreshMzone(0, 0x181fff, 0);
				RefreshMzone(1, 0x181fff, 0);
			} else {
				RefreshSzone(0, 0x181fff, 0);
				RefreshSzone(1, 0x181fff, 0);
			}
			break;
		}
		case MSG_NEW_TURN: {
			pbuf++;
			time_limit[0] = host_info.time_limit;
			time_limit[1] = host_info.time_limit;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			if(turn_count > 0) {
				if(turn_count % 2 == 0) {
					if(cur_player[0] == players[0])
						cur_player[0] = players[1];
					else
						cur_player[0] = players[0];
				} else {
					if(cur_player[1] == players[2])
						cur_player[1] = players[3];
					else
						cur_player[1] = players[2];
				}
			}
			turn_count++;
			for(int i = 0; i < 4; ++i) {
				surrender[i] = false;
			}
			break;
		}
		case MSG_NEW_PHASE: {
			pbuf += 2;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			RefreshHand(0);
			RefreshHand(1);
			break;
		}
		case MSG_MOVE: {
			pbufw = pbuf;
			int pc = pbuf[4];
			int pl = pbuf[5];
			/*int ps = pbuf[6];*/
			/*int pp = pbuf[7];*/
			int cc = pbuf[8];
			int cl = pbuf[9];
			int cs = pbuf[10];
			int cp = pbuf[11];
			pbuf += 16;
			NetServer::SendBufferToPlayer(cur_player[cc], STOC_GAME_MSG, offset, pbuf - offset);
			if (!(cl & (LOCATION_GRAVE + LOCATION_OVERLAY)) && ((cl & (LOCATION_DECK + LOCATION_HAND)) || (cp & POS_FACEDOWN)))
				BufferIO::Write<int32_t>(pbufw, 0);
			for(int i = 0; i < 4; ++i)
				if(players[i] != cur_player[cc])
					NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, offset, pbuf - offset);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			if (cl != 0 && (cl & LOCATION_OVERLAY) == 0 && (cl != pl || pc != cc))
				RefreshSingle(cc, cl, cs);
			break;
		}
		case MSG_POS_CHANGE: {
			int cc = pbuf[4];
			int cl = pbuf[5];
			int cs = pbuf[6];
			int pp = pbuf[7];
			int cp = pbuf[8];
			pbuf += 9;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			if((pp & POS_FACEDOWN) && (cp & POS_FACEUP))
				RefreshSingle(cc, cl, cs);
			break;
		}
		case MSG_SET: {
			BufferIO::Write<int32_t>(pbuf, 0);
			pbuf += 4;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_SWAP: {
			int c1 = pbuf[4];
			int l1 = pbuf[5];
			int s1 = pbuf[6];
			int c2 = pbuf[12];
			int l2 = pbuf[13];
			int s2 = pbuf[14];
			pbuf += 16;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshSingle(c1, l1, s1);
			RefreshSingle(c2, l2, s2);
			break;
		}
		case MSG_FIELD_DISABLED: {
			pbuf += 4;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_SUMMONING: {
			pbuf += 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_SUMMONED: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			break;
		}
		case MSG_SPSUMMONING: {
			pbuf += 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_SPSUMMONED: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			break;
		}
		case MSG_FLIPSUMMONING: {
			RefreshSingle(pbuf[4], pbuf[5], pbuf[6]);
			pbuf += 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_FLIPSUMMONED: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			break;
		}
		case MSG_CHAINING: {
			pbuf += 16;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CHAINED: {
			pbuf++;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			RefreshHand(0);
			RefreshHand(1);
			break;
		}
		case MSG_CHAIN_SOLVING: {
			pbuf++;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CHAIN_SOLVED: {
			pbuf++;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			RefreshHand(0);
			RefreshHand(1);
			break;
		}
		case MSG_CHAIN_END: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			RefreshSzone(0);
			RefreshSzone(1);
			RefreshHand(0);
			RefreshHand(1);
			break;
		}
		case MSG_CHAIN_NEGATED: {
			pbuf++;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CHAIN_DISABLED: {
			pbuf++;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CARD_SELECTED: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			break;
		}
		case MSG_RANDOM_SELECTED: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			NetServer::SendBufferToPlayer(players[player], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_BECOME_TARGET: {
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_DRAW: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbufw = pbuf;
			pbuf += count * 4;
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			for (int i = 0; i < count; ++i) {
				if(!(pbufw[3] & 0x80))
					BufferIO::Write<int32_t>(pbufw, 0);
				else
					pbufw += 4;
			}
			for(int i = 0; i < 4; ++i)
				if(players[i] != cur_player[player])
					NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, offset, pbuf - offset);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_DAMAGE: {
			pbuf += 5;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_RECOVER: {
			pbuf += 5;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_EQUIP: {
			pbuf += 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_LPUPDATE: {
			pbuf += 5;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_UNEQUIP: {
			pbuf += 4;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CARD_TARGET: {
			pbuf += 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_CANCEL_TARGET: {
			pbuf += 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_PAY_LPCOST: {
			pbuf += 5;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_ADD_COUNTER: {
			pbuf += 7;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_REMOVE_COUNTER: {
			pbuf += 7;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_ATTACK: {
			pbuf += 8;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_BATTLE: {
			pbuf += 26;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_ATTACK_DISABLED: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_DAMAGE_STEP_START: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			break;
		}
		case MSG_DAMAGE_STEP_END: {
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshMzone(0);
			RefreshMzone(1);
			break;
		}
		case MSG_MISSED_EFFECT: {
			player = pbuf[0];
			pbuf += 8;
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			break;
		}
		case MSG_TOSS_COIN: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_TOSS_DICE: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_ROCK_PAPER_SCISSORS: {
			player = BufferIO::Read<uint8_t>(pbuf);
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_HAND_RES: {
			pbuf += 1;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for (auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_ANNOUNCE_RACE: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_ANNOUNCE_ATTRIB: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_ANNOUNCE_CARD:
		case MSG_ANNOUNCE_NUMBER: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4 * count;
			WaitforResponse(player);
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			return 1;
		}
		case MSG_CARD_HINT: {
			pbuf += 9;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_PLAYER_HINT: {
			pbuf += 6;
			NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, offset, pbuf - offset);
			NetServer::ReSendToPlayer(players[1]);
			NetServer::ReSendToPlayer(players[2]);
			NetServer::ReSendToPlayer(players[3]);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			break;
		}
		case MSG_TAG_SWAP: {
			player = BufferIO::Read<uint8_t>(pbuf);
			/*int mcount = */BufferIO::Read<uint8_t>(pbuf);
			int ecount = BufferIO::Read<uint8_t>(pbuf);
			/*int pcount = */BufferIO::Read<uint8_t>(pbuf);
			int hcount = BufferIO::Read<uint8_t>(pbuf);
			pbufw = pbuf + 4;
			pbuf += hcount * 4 + ecount * 4 + 4;
			NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, offset, pbuf - offset);
			for (int i = 0; i < hcount; ++i) {
				if(!(pbufw[3] & 0x80))
					BufferIO::Write<int32_t>(pbufw, 0);
				else
					pbufw += 4;
			}
			for (int i = 0; i < ecount; ++i) {
				if(!(pbufw[3] & 0x80))
					BufferIO::Write<int32_t>(pbufw, 0);
				else
					pbufw += 4;
			}
			for(int i = 0; i < 4; ++i)
				if(players[i] != cur_player[player])
					NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, offset, pbuf - offset);
			for(auto oit = observers.begin(); oit != observers.end(); ++oit)
				NetServer::ReSendToPlayer(*oit);
			RefreshExtra(player);
			RefreshMzone(0, 0x81fff, 0);
			RefreshMzone(1, 0x81fff, 0);
			RefreshSzone(0, 0x681fff, 0);
			RefreshSzone(1, 0x681fff, 0);
			RefreshHand(0, 0x781fff, 0);
			RefreshHand(1, 0x781fff, 0);
			break;
		}
		case MSG_MATCH_KILL: {
			pbuf += 4;
			break;
		}
		}
	}
	return 0;
}
void TagDuel::GetResponse(DuelPlayer* dp, unsigned char* pdata, unsigned int len) {
	unsigned char resb[SIZE_RETURN_VALUE]{};
	if (len > SIZE_RETURN_VALUE)
		len = SIZE_RETURN_VALUE;
	std::memcpy(resb, pdata, len);
	last_replay.Write<uint8_t>(len);
	last_replay.WriteData(resb, len);
	set_responseb(pduel, resb);
	players[dp->type]->state = 0xff;
	if(host_info.time_limit) {
		int resp_type = dp->type < 2 ? 0 : 1;
		if(time_limit[resp_type] >= time_elapsed)
			time_limit[resp_type] -= time_elapsed;
		else time_limit[resp_type] = 0;
		time_elapsed = 0;
	}
	Process();
}
void TagDuel::EndDuel() {
	if(!pduel)
		return;
	last_replay.EndRecord();
	char replaybuf[0x2000], *pbuf = replaybuf;
	std::memcpy(pbuf, &last_replay.pheader, sizeof last_replay.pheader);
	pbuf += sizeof last_replay.pheader;
	std::memcpy(pbuf, last_replay.comp_data, last_replay.comp_size);
	NetServer::SendBufferToPlayer(players[0], STOC_REPLAY, replaybuf, sizeof last_replay.pheader + last_replay.comp_size);
	NetServer::ReSendToPlayer(players[1]);
	NetServer::ReSendToPlayer(players[2]);
	NetServer::ReSendToPlayer(players[3]);
	for(auto oit = observers.begin(); oit != observers.end(); ++oit)
		NetServer::ReSendToPlayer(*oit);
	end_duel(pduel);
	event_del(etimer);
	pduel = 0;
}
void TagDuel::WaitforResponse(int playerid) {
	last_response = playerid;
	unsigned char msg = MSG_WAITING;
	for(int i = 0; i < 4; ++i)
		if(players[i] != cur_player[playerid])
			NetServer::SendPacketToPlayer(players[i], STOC_GAME_MSG, msg);
	if(host_info.time_limit) {
		STOC_TimeLimit sctl;
		sctl.player = playerid;
		sctl.left_time = time_limit[playerid];
		NetServer::SendPacketToPlayer(players[0], STOC_TIME_LIMIT, sctl);
		NetServer::ReSendToPlayer(players[1]);
		NetServer::ReSendToPlayer(players[2]);
		NetServer::ReSendToPlayer(players[3]);
		cur_player[playerid]->state = CTOS_TIME_CONFIRM;
	} else
		cur_player[playerid]->state = CTOS_RESPONSE;
}
void TagDuel::TimeConfirm(DuelPlayer* dp) {
	if(host_info.time_limit == 0)
		return;
	if(dp != cur_player[last_response])
		return;
	cur_player[last_response]->state = CTOS_RESPONSE;
	if(time_elapsed < 10)
		time_elapsed = 0;
}
inline int TagDuel::WriteUpdateData(int& player, int location, int& flag, unsigned char*& qbuf, int& use_cache) {
	flag |= (QUERY_CODE | QUERY_POSITION);
	BufferIO::Write<uint8_t>(qbuf, MSG_UPDATE_DATA);
	BufferIO::Write<uint8_t>(qbuf, player);
	BufferIO::Write<uint8_t>(qbuf, location);
	int len = query_field_card(pduel, player, location, flag, qbuf, use_cache);
	return len;
}
void TagDuel::RefreshMzone(int player, int flag, int use_cache) {
	std::vector<unsigned char> query_buffer;
	query_buffer.resize(SIZE_QUERY_BUFFER);
	auto qbuf = query_buffer.data();
	auto len = WriteUpdateData(player, LOCATION_MZONE, flag, qbuf, use_cache);
	int pid = (player == 0) ? 0 : 2;
	NetServer::SendBufferToPlayer(players[pid], STOC_GAME_MSG, query_buffer.data(), len + 3);
	NetServer::ReSendToPlayer(players[pid + 1]);
	int qlen = 0;
	while(qlen < len) {
		int clen = BufferIO::Read<int32_t>(qbuf);
		qlen += clen;
		if (clen <= LEN_HEADER)
			continue;
		auto position = GetPosition(qbuf, 8);
		if (position & POS_FACEDOWN)
			std::memset(qbuf, 0, clen - 4);
		qbuf += clen - 4;
	}
	pid = 2 - pid;
	NetServer::SendBufferToPlayer(players[pid], STOC_GAME_MSG, query_buffer.data(), len + 3);
	NetServer::ReSendToPlayer(players[pid + 1]);
	for(auto pit = observers.begin(); pit != observers.end(); ++pit)
		NetServer::ReSendToPlayer(*pit);
}
void TagDuel::RefreshSzone(int player, int flag, int use_cache) {
	std::vector<unsigned char> query_buffer;
	query_buffer.resize(SIZE_QUERY_BUFFER);
	auto qbuf = query_buffer.data();
	auto len = WriteUpdateData(player, LOCATION_SZONE, flag, qbuf, use_cache);
	int pid = (player == 0) ? 0 : 2;
	NetServer::SendBufferToPlayer(players[pid], STOC_GAME_MSG, query_buffer.data(), len + 3);
	NetServer::ReSendToPlayer(players[pid + 1]);
	int qlen = 0;
	while(qlen < len) {
		int clen = BufferIO::Read<int32_t>(qbuf);
		qlen += clen;
		if (clen <= LEN_HEADER)
			continue;
		auto position = GetPosition(qbuf, 8);
		if (position & POS_FACEDOWN)
			std::memset(qbuf, 0, clen - 4);
		qbuf += clen - 4;
	}
	pid = 2 - pid;
	NetServer::SendBufferToPlayer(players[pid], STOC_GAME_MSG, query_buffer.data(), len + 3);
	NetServer::ReSendToPlayer(players[pid + 1]);
	for(auto pit = observers.begin(); pit != observers.end(); ++pit)
		NetServer::ReSendToPlayer(*pit);
}
void TagDuel::RefreshHand(int player, int flag, int use_cache) {
	std::vector<unsigned char> query_buffer;
	query_buffer.resize(SIZE_QUERY_BUFFER);
	auto qbuf = query_buffer.data();
	auto len = WriteUpdateData(player, LOCATION_HAND, flag, qbuf, use_cache);
	NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, query_buffer.data(), len + 3);
	int qlen = 0;
	while(qlen < len) {
		int slen = BufferIO::Read<int32_t>(qbuf);
		qlen += slen;
		if (slen <= LEN_HEADER)
			continue;
		auto position = GetPosition(qbuf, 8);
		if(!(position & POS_FACEUP))
			std::memset(qbuf, 0, slen - 4);
		qbuf += slen - 4;
	}
	for(int i = 0; i < 4; ++i)
		if(players[i] != cur_player[player])
			NetServer::SendBufferToPlayer(players[i], STOC_GAME_MSG, query_buffer.data(), len + 3);
	for(auto pit = observers.begin(); pit != observers.end(); ++pit)
		NetServer::ReSendToPlayer(*pit);
}
void TagDuel::RefreshGrave(int player, int flag, int use_cache) {
	std::vector<unsigned char> query_buffer;
	query_buffer.resize(SIZE_QUERY_BUFFER);
	auto qbuf = query_buffer.data();
	auto len = WriteUpdateData(player, LOCATION_GRAVE, flag, qbuf, use_cache);
	NetServer::SendBufferToPlayer(players[0], STOC_GAME_MSG, query_buffer.data(), len + 3);
	NetServer::ReSendToPlayer(players[1]);
	NetServer::ReSendToPlayer(players[2]);
	NetServer::ReSendToPlayer(players[3]);
	for(auto pit = observers.begin(); pit != observers.end(); ++pit)
		NetServer::ReSendToPlayer(*pit);
}
void TagDuel::RefreshExtra(int player, int flag, int use_cache) {
	std::vector<unsigned char> query_buffer;
	query_buffer.resize(SIZE_QUERY_BUFFER);
	auto qbuf = query_buffer.data();
	auto len = WriteUpdateData(player, LOCATION_EXTRA, flag, qbuf, use_cache);
	NetServer::SendBufferToPlayer(cur_player[player], STOC_GAME_MSG, query_buffer.data(), len + 3);
}
void TagDuel::RefreshSingle(int player, int location, int sequence, int flag) {
	flag |= (QUERY_CODE | QUERY_POSITION);
	unsigned char query_buffer[0x1000];
	auto qbuf = query_buffer;
	BufferIO::Write<uint8_t>(qbuf, MSG_UPDATE_CARD);
	BufferIO::Write<uint8_t>(qbuf, player);
	BufferIO::Write<uint8_t>(qbuf, location);
	BufferIO::Write<uint8_t>(qbuf, sequence);
	int len = query_card(pduel, player, location, sequence, flag, qbuf, 0);
	auto position = GetPosition(qbuf, 12);
	if(location & LOCATION_ONFIELD) {
		int pid = (player == 0) ? 0 : 2;
		NetServer::SendBufferToPlayer(players[pid], STOC_GAME_MSG, query_buffer, len + 4);
		NetServer::ReSendToPlayer(players[pid + 1]);
		if(position & POS_FACEUP) {
			pid = 2 - pid;
			NetServer::SendBufferToPlayer(players[pid], STOC_GAME_MSG, query_buffer, len + 4);
			NetServer::ReSendToPlayer(players[pid + 1]);
			for(auto pit = observers.begin(); pit != observers.end(); ++pit)
				NetServer::ReSendToPlayer(*pit);
		}
	} else {
		int pid = (player == 0) ? 0 : 2;
		NetServer::SendBufferToPlayer(players[pid], STOC_GAME_MSG, query_buffer, len + 4);
		NetServer::ReSendToPlayer(players[pid + 1]);
		if(location == LOCATION_REMOVED && (position & POS_FACEDOWN))
			return;
		if (location & 0x90) {
			for(int i = 0; i < 4; ++i)
				if(players[i] != cur_player[player])
					NetServer::ReSendToPlayer(players[i]);
			for(auto pit = observers.begin(); pit != observers.end(); ++pit)
				NetServer::ReSendToPlayer(*pit);
		}
	}
}
uint32_t TagDuel::MessageHandler(intptr_t fduel, uint32_t type) {
	if(!enable_log)
		return 0;
	char msgbuf[1024];
	get_log_message(fduel, msgbuf);
	mainGame->AddDebugMsg(msgbuf);
	return 0;
}
void TagDuel::TagTimer(evutil_socket_t fd, short events, void* arg) {
	TagDuel* sd = static_cast<TagDuel*>(arg);
	sd->time_elapsed++;
	if(sd->time_elapsed >= sd->time_limit[sd->last_response] || sd->time_limit[sd->last_response] <= 0) {
		unsigned char wbuf[3];
		uint32_t player = sd->last_response;
		wbuf[0] = MSG_WIN;
		wbuf[1] = 1 - player;
		wbuf[2] = 0x3;
		NetServer::SendBufferToPlayer(sd->players[0], STOC_GAME_MSG, wbuf, 3);
		NetServer::ReSendToPlayer(sd->players[1]);
		NetServer::ReSendToPlayer(sd->players[2]);
		NetServer::ReSendToPlayer(sd->players[3]);
		sd->EndDuel();
		sd->DuelEndProc();
		event_del(sd->etimer);
		return;
	}
	timeval timeout = { 1, 0 };
	event_add(sd->etimer, &timeout);
}

}

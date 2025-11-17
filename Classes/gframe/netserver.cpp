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

/**
 * @brief 启动网络服务器
 * @param port 服务器监听的端口号
 * @return 启动成功返回true，失败返回false
 *
 * 该函数初始化网络事件基础结构，创建监听套接字，并启动服务器线程来处理网络事件。
 */
bool NetServer::StartServer(unsigned short port) {
	// 检查服务器是否已经启动
	if(net_evbase)
		return false;

	// 创建网络事件基础结构
	net_evbase = event_base_new();
	if(!net_evbase)
		return false;

	// 初始化服务器地址结构
	sockaddr_in sin;
	std::memset(&sin, 0, sizeof sin);
	server_port = port;
	sin.sin_family = AF_INET;
	sin.sin_addr.s_addr = htonl(INADDR_ANY);
	sin.sin_port = htons(port);

	// 创建监听器并绑定到指定端口
	listener = evconnlistener_new_bind(net_evbase, ServerAccept, nullptr,
	                                   LEV_OPT_CLOSE_ON_FREE | LEV_OPT_REUSEABLE, -1, (sockaddr*)&sin, sizeof(sin));
	if(!listener) {
		// 监听器创建失败，清理资源并返回false
		event_base_free(net_evbase);
		net_evbase = 0;
		return false;
	}

	// 设置监听器错误回调函数
	evconnlistener_set_error_cb(listener, ServerAcceptError);

	// 启动服务器事件处理线程
	std::thread(ServerThread).detach();
	return true;
}

/**
 * @brief 启动网络广播服务
 *
 * 该函数创建UDP套接字并配置为广播模式，绑定到指定端口，
 * 并注册事件监听器来处理广播消息。
 *
 * @return bool - 启动成功返回true，失败返回false
 */
bool NetServer::StartBroadcast() {
	// 检查事件基础对象是否存在
	if(!net_evbase)
		return false;

	// 创建UDP套接字用于广播通信
	SOCKET udp = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);

	// 设置套接字选项：允许广播和地址复用
	int opt = TRUE;
	setsockopt(udp, SOL_SOCKET, SO_BROADCAST, (const char*)&opt, sizeof opt);
	setsockopt(udp, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof opt);

	// 配置套接字地址结构：绑定到所有网络接口的7920端口
	sockaddr_in addr;
	std::memset(&addr, 0, sizeof addr);
	addr.sin_family = AF_INET;
	addr.sin_port = htons(7920);
	addr.sin_addr.s_addr = 0;

	// 绑定套接字到指定地址和端口
	if(bind(udp, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
		closesocket(udp);
		return false;
	}

	// 创建并添加广播事件监听器
	broadcast_ev = event_new(net_evbase, udp, EV_READ | EV_PERSIST, BroadcastEvent, nullptr);
	event_add(broadcast_ev, nullptr);

	return true;
}
/**
 * @brief 停止网络服务器
 *
 * 该函数用于停止当前的网络服务器实例。如果服务器正在运行，
 * 它会结束正在进行的决斗（如果有的话），然后退出事件循环。
 *
 * @note 该函数没有参数
 * @note 该函数没有返回值
 */
void NetServer::StopServer() {
	// 检查网络事件基础结构是否存在，不存在则直接返回
	if(!net_evbase)
		return;

	// 如果处于决斗模式，结束当前决斗
	if(duel_mode)
		duel_mode->EndDuel();

	// 退出事件循环
	event_base_loopexit(net_evbase, 0);
}
/**
 * @brief 停止网络广播功能
 *
 * 该函数用于停止服务器的广播功能，释放相关的事件资源和套接字。
 * 主要包括删除事件监听、关闭套接字和释放事件对象。
 *
 * @note 该函数没有参数
 * @note 该函数没有返回值
 */
void NetServer::StopBroadcast() {
	// 检查事件基础结构和广播事件是否有效，无效则直接返回
	if(!net_evbase || !broadcast_ev)
		return;

	// 从事件循环中删除广播事件
	event_del(broadcast_ev);

	// 获取广播事件关联的套接字描述符
	evutil_socket_t fd;
	event_get_assignment(broadcast_ev, 0, &fd, 0, 0, 0);

	// 关闭广播套接字
	evutil_closesocket(fd);

	// 释放广播事件对象并置空指针
	event_free(broadcast_ev);
	broadcast_ev = 0;
}
/**
 * @brief 停止服务器监听功能
 *
 * 该函数用于停止网络服务器的监听操作，包括禁用事件监听器和停止广播服务。
 * 调用此函数后，服务器将不再接受新的客户端连接请求。
 */
void NetServer::StopListen() {
	// 禁用事件监听器，停止接收新的连接请求
	evconnlistener_disable(listener);
	// 停止广播服务
	StopBroadcast();
}
/**
 * @brief 广播事件处理函数，用于处理网络广播消息并响应客户端请求
 * @param fd 套接字文件描述符
 * @param events 事件类型
 * @param arg 用户自定义参数
 */
void NetServer::BroadcastEvent(evutil_socket_t fd, short events, void* arg) {
	// 接收广播数据包
	sockaddr_in bc_addr;
	socklen_t sz = sizeof(sockaddr_in);
	char buf[256];
	int ret = recvfrom(fd, buf, 256, 0, (sockaddr*)&bc_addr, &sz);
	if(ret == -1)
		return;

	// 解析接收到的数据包
	HostRequest packet;
	std::memcpy(&packet, buf, sizeof packet);
	const HostRequest* pHR = &packet;

	// 验证数据包标识符，如果匹配则发送服务器信息响应
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
/**
 * @brief 服务器接受新连接的回调函数
 *
 * 当有新的客户端连接到达时，此函数会被libevent调用。它负责创建新的缓冲事件，
 * 初始化玩家信息，并将新连接添加到用户管理容器中。
 *
 * @param listener 监听器对象指针
 * @param fd 新连接的套接字文件描述符
 * @param address 客户端地址信息
 * @param socklen 地址信息长度
 * @param ctx 用户自定义上下文数据指针
 */
void NetServer::ServerAccept(evconnlistener* listener, evutil_socket_t fd, sockaddr* address, int socklen, void* ctx) {
	// 创建新的缓冲事件，用于处理网络I/O操作
	bufferevent* bev = bufferevent_socket_new(net_evbase, fd, BEV_OPT_CLOSE_ON_FREE);

	// 初始化新玩家信息
	DuelPlayer dp;
	dp.name[0] = 0;
	dp.type = 0xff;
	dp.bev = bev;

	// 将新连接添加到用户映射表中
	users[bev] = dp;

	// 设置缓冲事件的回调函数并启用读取事件
	bufferevent_setcb(bev, ServerEchoRead, nullptr, ServerEchoEvent, nullptr);
	bufferevent_enable(bev, EV_READ);
}
/**
 * @brief 服务器接受连接错误处理函数
 *
 * 当服务器在接受客户端连接时发生错误，该函数会被调用。
 * 函数会退出事件循环，停止服务器运行。
 *
 * @param listener 发生错误的监听器对象
 * @param ctx 用户自定义上下文数据
 */
void NetServer::ServerAcceptError(evconnlistener* listener, void* ctx) {
	/* 退出事件循环，停止服务器 */
	event_base_loopexit(net_evbase, 0);
}
/*
* 函数名: ServerEchoRead
* 描述: 处理服务器回显读取事件，解析网络数据包并转发给相应的处理函数
* 参数:
*   bev - 指向bufferevent结构的指针，表示网络事件缓冲区
*   ctx - 上下文指针，用户自定义数据（本函数中未使用）
* 返回值: 无
* 数据包格式:
*   packet_len: 2 bytes - 数据包长度（包括proto字段）
*   proto: 1 byte - 协议类型
*   [data]: (packet_len - 1) bytes - 实际数据内容
*/
void NetServer::ServerEchoRead(bufferevent *bev, void *ctx) {
	// 获取输入缓冲区并检查是否有足够数据
	evbuffer* input = bufferevent_get_input(bev);
	int len = evbuffer_get_length(input);
	if (len < 2)
		return;

	// 分配临时缓冲区用于存储读取的数据
	unsigned char* net_server_read = new unsigned char[SIZE_NETWORK_BUFFER];
	uint16_t packet_len = 0;

	// 循环处理输入缓冲区中的完整数据包
	while (len >= 2) {
		// 读取数据包长度字段（不移除数据）
		evbuffer_copyout(input, &packet_len, sizeof packet_len);
		// 检查缓冲区中是否有完整的数据包
		if (len < packet_len + 2)
			break;
		// 从缓冲区移除完整数据包并存储到临时缓冲区
		int read_len = evbuffer_remove(input, net_server_read, packet_len + 2);
		// 如果数据包长度大于2字节，则处理数据部分（跳过长度字段）
		if (read_len > 2)
			HandleCTOSPacket(&users[bev], &net_server_read[2], read_len - 2);
		// 更新剩余数据长度
		len -= packet_len + 2;
	}

	// 释放临时缓冲区内存
	delete[] net_server_read;
}
/**
 * @brief 服务器回显事件处理函数
 *
 * 当缓冲事件发生时，检查是否为连接断开或错误事件，
 * 如果是则处理玩家离开游戏或断开连接的逻辑。
 *
 * @param bev 触发事件的缓冲事件对象
 * @param events 发生的事件类型
 * @param ctx 用户自定义上下文数据（未使用）
 */
void NetServer::ServerEchoEvent(bufferevent* bev, short events, void* ctx) {
	// 检查是否为连接结束或错误事件
	if (events & (BEV_EVENT_EOF | BEV_EVENT_ERROR)) {
		// 获取触发事件的玩家对象
		DuelPlayer* dp = &users[bev];
		// 获取玩家所在的游戏模式对象
		DuelMode* dm = dp->game;
		// 根据玩家是否在游戏中的状态，执行不同的断开处理逻辑
		if(dm)
			dm->LeaveGame(dp);
		else
			DisconnectPlayer(dp);
	}
}
/**
 * @brief 服务器主线程函数，处理网络事件循环和资源清理
 *
 * 该函数启动libevent事件循环，等待并处理网络事件。当事件循环结束时，
 * 函数会清理所有分配的资源，包括用户连接、监听器、广播事件等。
 *
 * @return int 返回0表示正常退出
 */
int NetServer::ServerThread() {
	// 启动事件循环，处理网络IO事件
	event_base_dispatch(net_evbase);

	// 清理所有用户连接资源
	for(auto bit = users.begin(); bit != users.end(); ++bit) {
		bufferevent_disable(bit->first, EV_READ);
		bufferevent_free(bit->first);
	}
	users.clear();

	// 释放监听器资源
	evconnlistener_free(listener);
	listener = 0;

	// 关闭并释放广播事件资源
	if(broadcast_ev) {
		evutil_socket_t fd;
		event_get_assignment(broadcast_ev, 0, &fd, 0, 0, 0);
		evutil_closesocket(fd);
		event_free(broadcast_ev);
		broadcast_ev = 0;
	}

	// 清理决斗模式相关资源
	if(duel_mode) {
		event_free(duel_mode->etimer);
		delete duel_mode;
	}
	duel_mode = 0;

	// 释放事件基础结构
	event_base_free(net_evbase);
	net_evbase = 0;

	return 0;
}
/**
 * @brief 断开指定玩家的网络连接
 * @param dp 指向要断开连接的玩家对象的指针
 * @return 无返回值
 *
 * 该函数负责安全地断开指定玩家的网络连接，包括清理相关的缓冲事件
 * 和从用户列表中移除该玩家
 */
void NetServer::DisconnectPlayer(DuelPlayer* dp) {
	// 查找玩家对应的缓冲事件是否存在于用户列表中
	auto bit = users.find(dp->bev);
	if(bit != users.end()) {
		// 强制刷新输出缓冲区，确保所有数据都发送完毕
		bufferevent_flush(dp->bev, EV_WRITE, BEV_FLUSH);
		// 禁用缓冲事件的读取功能
		bufferevent_disable(dp->bev, EV_READ);
		// 释放缓冲事件资源
		bufferevent_free(dp->bev);
		// 从用户列表中移除该玩家
		users.erase(bit);
	}
}
/**
 * @brief 处理客户端发送到服务器的数据包（Client To Server Packet）
 *
 * 此函数根据数据包类型分发处理逻辑。它首先读取数据包的类型，
 * 并检查当前玩家状态是否允许处理该类型的包。
 * 若不允许则直接返回；否则进入对应的 switch-case 分支进行具体处理。
 *
 * @param dp 指向发送此数据包的 DuelPlayer 对象指针
 * @param data 包含原始数据包内容的缓冲区指针
 * @param len 数据包长度（字节）
 */
void NetServer::HandleCTOSPacket(DuelPlayer* dp, unsigned char* data, int len) {
	auto pdata = data;
	unsigned char pktType = BufferIO::Read<uint8_t>(pdata);

	// 验证数据包类型合法性：若不是投降或聊天包，并且玩家状态无效或与包类型不符，则拒绝处理
	if((pktType != CTOS_SURRENDER) && (pktType != CTOS_CHAT) && (dp->state == 0xff || (dp->state && dp->state != pktType)))
		return;

	switch(pktType) {
	case CTOS_RESPONSE: {
		// 响应消息必须在游戏已经开始的情况下才能被接受
		if(!dp->game || !duel_mode->pduel)
			return;
		if (len < 1 + (int)sizeof(unsigned char))
			return;
		duel_mode->GetResponse(dp, pdata, len - 1);
		break;
	}
	case CTOS_TIME_CONFIRM: {
		// 时间确认消息用于同步回合时间，在游戏中有效时才可处理
		if(!dp->game || !duel_mode->pduel)
			return;
		duel_mode->TimeConfirm(dp);
		break;
	}
	case CTOS_CHAT: {
		// 聊天信息可以在房间内传递，但需要验证格式正确性
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
		// 更新卡组请求仅在房间阶段有效
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
		// 手牌结果通知只在房间中有效
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
		// 先攻选择结果通知只在房间中有效
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
		// 设置玩家基本信息（如昵称），无需处于特定游戏状态
		if (len < 1 + (int)sizeof(CTOS_PlayerInfo))
			return;
		CTOS_PlayerInfo packet;
		std::memcpy(&packet, pdata, sizeof packet);
		auto pkt = &packet;
		BufferIO::NullTerminate(pkt->name); // 确保字符串以空字符结尾
		BufferIO::CopyCharArray(pkt->name, dp->name); // 将名称复制给玩家对象
		break;
	}
	case CTOS_EXTERNAL_ADDRESS: {
		// 供其他服务器或反向代理使用，暂未启用
		/*
		wchar_t hostname[LEN_HOSTNAME];
		uint32_t real_ip = ntohl(BufferIO::Read<int32_t>(pdata));
		BufferIO::CopyCharArray((uint16_t*)pdata, hostname);
		*/
		break;
	}
	case CTOS_CREATE_GAME: {
		// 创建新游戏房间，需确保当前没有正在进行的游戏
		if(dp->game || duel_mode)
			return;
		if (len < 1 + (int)sizeof(CTOS_CreateGame))
			return;
		CTOS_CreateGame packet;
		std::memcpy(&packet, pdata, sizeof packet);
		auto pkt = &packet;

		// 校验规则和模式设置的有效性并修正非法值
		if(pkt->info.rule > CURRENT_RULE)
			pkt->info.rule = CURRENT_RULE;
		if(pkt->info.mode > MODE_TAG)
			pkt->info.mode = MODE_SINGLE;

		// 查找合法的禁限卡表哈希值，如果找不到则默认使用第一个可用项
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

		// 根据游戏模式创建相应的决斗实例
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

		// 设置房间名和密码，并加入游戏
		BufferIO::NullTerminate(pkt->name);
		BufferIO::NullTerminate(pkt->pass);
		BufferIO::CopyCharArray(pkt->name, duel_mode->name);
		BufferIO::CopyCharArray(pkt->pass, duel_mode->pass);
		duel_mode->JoinGame(dp, 0, true);
		StartBroadcast(); // 启动广播服务以便其他玩家发现房间
		break;
	}
	case CTOS_JOIN_GAME: {
		// 加入已有房间，必须存在一个正在等待中的房间
		if (!duel_mode)
			return;
		if (len < 1 + (int)sizeof(CTOS_JoinGame))
			return;
		duel_mode->JoinGame(dp, pdata, false);
		break;
	}
	case CTOS_LEAVE_GAME: {
		// 玩家离开房间操作
		if (!duel_mode)
			return;
		duel_mode->LeaveGame(dp);
		break;
	}
	case CTOS_SURRENDER: {
		// 投降操作
		if (!duel_mode)
			return;
		duel_mode->Surrender(dp);
		break;
	}
	case CTOS_HS_TODUELIST: {
		// 请求成为决斗者角色
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->ToDuelist(dp);
		break;
	}
	case CTOS_HS_TOOBSERVER: {
		// 请求成为观战者角色
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->ToObserver(dp);
		break;
	}
	case CTOS_HS_READY:
	case CTOS_HS_NOTREADY: {
		// 准备/取消准备状态切换
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->PlayerReady(dp, (CTOS_HS_NOTREADY - pktType) != 0);
		break;
	}
	case CTOS_HS_KICK: {
		// 房主踢出某个位置上的玩家
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
		// 开始决斗命令，由房主发出
		if (!duel_mode || duel_mode->pduel)
			return;
		duel_mode->StartDuel(dp);
		break;
	}
	}
}
/**
 * @brief 创建聊天数据包
 * @param src 源消息内容，以uint16_t数组形式存储
 * @param src_size 源消息的字节大小
 * @param dst 目标缓冲区，用于存储构造好的数据包
 * @param dst_player_type 目标玩家类型标识
 * @return 返回构造的数据包总字节数，如果源消息格式不正确则返回0
 */
size_t NetServer::CreateChatPacket(unsigned char* src, int src_size, unsigned char* dst, uint16_t dst_player_type) {
	// 将源数据复制到本地缓冲区并验证消息格式
	uint16_t src_msg[LEN_CHAT_MSG];
	std::memcpy(src_msg, src, src_size);
	const int src_len = src_size / sizeof(uint16_t);
	if (src_msg[src_len - 1] != 0)
		return 0;

	// 构造STOC_Chat数据包
	auto pdst = dst;
	BufferIO::Write<uint16_t>(pdst, dst_player_type);
	std::memcpy(pdst, src_msg, src_size);
	pdst += src_size;
	return sizeof(dst_player_type) + src_size;
}

}

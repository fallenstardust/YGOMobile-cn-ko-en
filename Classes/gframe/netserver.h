#ifndef NETSERVER_H
#define NETSERVER_H

#include <unordered_map>
#include "config.h"
#include "network.h"

namespace ygo {

class NetServer {
private:
	static std::unordered_map<bufferevent*, DuelPlayer> users;
	static unsigned short server_port;
	static event_base* net_evbase;
	static event* broadcast_ev;
	static evconnlistener* listener;
	static DuelMode* duel_mode;
	static unsigned char net_server_write[SIZE_NETWORK_BUFFER];
	static size_t last_sent;

public:
	static bool StartServer(unsigned short port);
	static bool StartBroadcast();
	static void StopServer();
	static void StopBroadcast();
	static void StopListen();
	static void BroadcastEvent(evutil_socket_t fd, short events, void* arg);
	static void ServerAccept(evconnlistener* listener, evutil_socket_t fd, sockaddr* address, int socklen, void* ctx);
	static void ServerAcceptError(evconnlistener *listener, void* ctx);
	static void ServerEchoRead(bufferevent* bev, void* ctx);
	static void ServerEchoEvent(bufferevent* bev, short events, void* ctx);
	static int ServerThread();
	static void DisconnectPlayer(DuelPlayer* dp);
	static void HandleCTOSPacket(DuelPlayer* dp, unsigned char* data, int len);
	static size_t CreateChatPacket(unsigned char* src, int src_size, unsigned char* dst, uint16_t dst_player_type);
	/**
	 * 向指定玩家发送数据包
	 * @param dp 目标玩家对象指针，如果为NULL则只准备数据不发送
	 * @param proto 协议类型字节
	 *
	 * 该函数构造一个3字节的数据包并发送给指定玩家：
	 * - 第1-2字节：固定值1（包长度）
	 * - 第3字节：协议类型
	 */
	static void SendPacketToPlayer(DuelPlayer* dp, unsigned char proto) {
		// 准备数据包缓冲区
		auto p = net_server_write;

		// 写入包长度(1)和协议类型
		BufferIO::Write<uint16_t>(p, 1);
		BufferIO::Write<uint8_t>(p, proto);

		// 记录最后发送的包大小
		last_sent = 3;

		// 如果目标玩家存在，则发送数据包
		if (dp)
			bufferevent_write(dp->bev, net_server_write, 3);
	}
	/**
	 * @brief 向指定玩家发送数据包
	 *
	 * 该函数将指定的数据结构序列化后通过网络发送给目标玩家。
	 * 数据包格式为：长度(2字节) + 协议号(1字节) + 数据内容
	 *
	 * @tparam ST 数据包结构体类型
	 * @param dp 目标玩家对象指针，如果为nullptr则只构建数据包不发送
	 * @param proto 协议号，标识数据包类型
	 * @param st 要发送的数据结构体引用
	 */
	template<typename ST>
	static void SendPacketToPlayer(DuelPlayer* dp, unsigned char proto, const ST& st) {
		// 获取网络写入缓冲区指针
		auto p = net_server_write;

		// 编译时检查数据包大小是否超过限制
		static_assert(sizeof(ST) <= MAX_DATA_SIZE, "Packet size is too large.");

		// 写入数据包总长度（协议号1字节 + 数据长度）
		BufferIO::Write<uint16_t>(p, (uint16_t)(1 + sizeof(ST)));

		// 写入协议号
		BufferIO::Write<uint8_t>(p, proto);

		// 拷贝数据结构体内容到缓冲区
		std::memcpy(p, &st, sizeof(ST));

		// 记录最后发送的数据包长度
		last_sent = sizeof(ST) + 3;

		// 如果目标玩家存在，则实际发送数据包
		if (dp)
			bufferevent_write(dp->bev, net_server_write, sizeof(ST) + 3);
	}
	/**
	 * @brief 发送数据缓冲区到指定玩家
	 *
	 * 该函数将指定的数据缓冲区内容发送给指定的决斗玩家。数据会被封装成网络消息格式，
	 * 包含协议类型和数据长度信息。
	 *
	 * @param dp 目标玩家对象指针，如果为nullptr则只准备数据不实际发送
	 * @param proto 协议类型字节，用于标识消息类型
	 * @param buffer 要发送的数据缓冲区指针
	 * @param len 要发送的数据长度
	 */
		static void SendBufferToPlayer(DuelPlayer* dp, unsigned char proto, void* buffer, size_t len) {
			auto p = net_server_write;
			// 限制发送数据长度不超过最大允许值
			if (len > MAX_DATA_SIZE)
				len = MAX_DATA_SIZE;
			// 写入数据包总长度（1字节协议号 + 数据长度）
			BufferIO::Write<uint16_t>(p, (uint16_t)(1 + len));
			// 写入协议类型字节
			BufferIO::Write<uint8_t>(p, proto);
			// 拷贝实际数据到发送缓冲区
			std::memcpy(p, buffer, len);
			last_sent = len + 3;
			// 如果目标玩家存在，则通过bufferevent发送数据
			if (dp)
				bufferevent_write(dp->bev, net_server_write, len + 3);
		}
	/**
	 * @brief 重新发送数据给指定的决斗玩家
	 *
	 * @param dp 指向决斗玩家对象的指针
	 *
	 * @note 该函数会检查玩家指针是否有效，如果有效则将缓冲区中的数据重新发送给该玩家
	 */
		static void ReSendToPlayer(DuelPlayer* dp) {
			// 如果玩家指针有效，则重新发送数据
			if(dp)
				bufferevent_write(dp->bev, net_server_write, last_sent);
		}
};

}

#endif //NETSERVER_H

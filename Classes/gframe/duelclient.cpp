#include "config.h"
#include "duelclient.h"
#include "client_card.h"
#include "materials.h"
#include "image_manager.h"
#include "sound_manager.h"
#include "single_mode.h"
#include "game.h"
#include "deck_manager.h"
#include "replay.h"
#include <thread>
#include <array>
#ifdef _IRR_ANDROID_PLATFORM_
#include <android/android_tools.h>
#endif

namespace ygo {

unsigned DuelClient::connect_state = 0;
unsigned char DuelClient::response_buf[SIZE_RETURN_VALUE];
size_t DuelClient::response_len = 0;
unsigned int DuelClient::watching = 0;
unsigned char DuelClient::selftype = 0;
bool DuelClient::is_host = false;
event_base* DuelClient::client_base = 0;
bufferevent* DuelClient::client_bev = 0;
unsigned char DuelClient::duel_client_write[SIZE_NETWORK_BUFFER];
bool DuelClient::is_closing = false;
bool DuelClient::is_swapping = false;
int DuelClient::select_hint = 0;
int DuelClient::select_unselect_hint = 0;
int DuelClient::last_select_hint = 0;
unsigned char DuelClient::last_successful_msg[SIZE_NETWORK_BUFFER];
size_t DuelClient::last_successful_msg_length = 0;
wchar_t DuelClient::event_string[256];
std::mt19937 DuelClient::rnd;
std::uniform_real_distribution<float> DuelClient::real_dist;

bool DuelClient::is_refreshing = false;
int DuelClient::match_kill = 0;
std::vector<HostPacket> DuelClient::hosts;
std::set<std::pair<unsigned int, unsigned short>> DuelClient::remotes;
event* DuelClient::resp_event = 0;

/**
 * @brief 启动客户端连接到指定的服务器
 * @param ip 服务器IP地址（网络字节序）
 * @param port 服务器端口号（主机字节序）
 * @param create_game 是否创建游戏房间
 * @return 连接成功返回true，失败返回false
 */
bool DuelClient::StartClient(unsigned int ip, unsigned short port, bool create_game) {
	// 检查当前是否已经处于连接状态
	if(connect_state)
		return false;
	sockaddr_in sin;
	// 创建libevent事件基础结构
	client_base = event_base_new();
	if(!client_base)
		return false;
	// 初始化socket地址结构
	std::memset(&sin, 0, sizeof sin);
	sin.sin_family = AF_INET;
	sin.sin_addr.s_addr = htonl(ip);
	sin.sin_port = htons(port);
	// 创建buffered event用于异步socket操作
	client_bev = bufferevent_socket_new(client_base, -1, BEV_OPT_CLOSE_ON_FREE);
	// 设置事件回调函数
	bufferevent_setcb(client_bev, ClientRead, nullptr, ClientEvent, (void*)create_game);
	// 尝试连接到服务器
	if (bufferevent_socket_connect(client_bev, (sockaddr*)&sin, sizeof(sin)) < 0) {
		// 连接失败，清理资源
		bufferevent_free(client_bev);
		event_base_free(client_base);
		client_bev = 0;
		client_base = 0;
		return false;
	}
	// 设置连接状态为已连接
	connect_state = 0x1;
	// 初始化随机数种子
	rnd.seed(std::random_device()());
	// 如果不是创建游戏，则设置连接超时事件
	if(!create_game) {
		timeval timeout = {5, 0};
		event* timeout_event = event_new(client_base, 0, EV_TIMEOUT, ConnectTimeout, 0);
		event_add(timeout_event, &timeout);
	}
	// 启动客户端处理线程
	std::thread(ClientThread).detach();
	return true;
}
/**
 * @brief 处理客户端连接超时事件的回调函数
 *
 * 当客户端连接服务器超时时，该函数会被libevent库调用。主要功能包括：
 * 1. 检查当前连接状态，避免重复处理
 * 2. 恢复界面上相关按钮的可用状态
 * 3. 根据是否为机器人模式显示相应的窗口
 * 4. 播放提示音效并显示连接超时的错误信息
 * 5. 退出事件循环
 *
 * @param fd 触发事件的套接字文件描述符
 * @param events 发生的事件类型
 * @param arg 传递给回调函数的用户数据指针
 */
void DuelClient::ConnectTimeout(evutil_socket_t fd, short events, void* arg) {
	// 如果连接状态为0x7（已完成状态），则直接返回不进行任何处理
	if(connect_state == 0x7)
		return;

	// 检查客户端是否正在关闭过程中
	if(!is_closing) {
		// 恢复主界面所有操作按钮的可用状态
		mainGame->btnCreateHost->setEnabled(true);
		mainGame->btnJoinHost->setEnabled(true);
		mainGame->btnJoinCancel->setEnabled(true);
		mainGame->btnStartBot->setEnabled(true);
		mainGame->btnBotCancel->setEnabled(true);

		// 加锁保护GUI操作
		mainGame->gMutex.lock();

		// 根据机器人模式决定显示单人游戏窗口还是局域网窗口
		if(bot_mode && !mainGame->wSinglePlay->isVisible())
			mainGame->ShowElement(mainGame->wSinglePlay);
		else if(!bot_mode && !mainGame->wLanWindow->isVisible())
			mainGame->ShowElement(mainGame->wLanWindow);

		// 播放连接失败的提示音效
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
		// 显示连接超时的系统提示信息
		mainGame->addMessageBox(L"", dataManager.GetSysString(1400));

		// 解锁GUI操作
		mainGame->gMutex.unlock();
	}

	// 退出libevent事件循环
	event_base_loopbreak(client_base);
}
/**
 * @brief 停止客户端连接
 * @param is_exiting 是否正在退出程序
 *
 * 该函数用于停止客户端的网络连接。当连接状态不为0x7时直接返回，
 * 否则设置关闭标志并中断事件循环。
 */
void DuelClient::StopClient(bool is_exiting) {
	// 检查连接状态，如果不是0x7状态则直接返回
	if(connect_state != 0x7)
		return;

	// 设置关闭标志
	is_closing = is_exiting;

	// 中断客户端事件循环
	event_base_loopbreak(client_base);
}
/**
 * @brief 处理客户端网络数据读取的回调函数
 *
 * 该函数作为libevent的bufferevent读回调函数，负责从输入缓冲区中读取网络数据包，
 * 解析数据包长度，并将完整的数据包传递给处理函数进行处理。
 *
 * @param bev 指向bufferevent结构的指针，表示当前的网络连接事件
 * @param ctx 用户自定义上下文context指针，此处未使用
 *
 * @note 该函数假设数据包格式为：2字节长度字段 + 数据内容
 * @note 数据包长度字段不包含自身长度(2字节)
 */
void DuelClient::ClientRead(bufferevent* bev, void* ctx) {
	// 获取输入缓冲区并检查是否有足够数据
	evbuffer* input = bufferevent_get_input(bev);
	int len = evbuffer_get_length(input);
	if (len < 2)
		return;

	// 分配临时缓冲区用于存储读取的数据包
	unsigned char* duel_client_read = new unsigned char[SIZE_NETWORK_BUFFER];
	uint16_t packet_len = 0;

	// 循环处理输入缓冲区中的完整数据包
	while (len >= 2) {
		// 读取数据包长度字段（不移除数据）
		evbuffer_copyout(input, &packet_len, sizeof packet_len);

		// 检查缓冲区中是否有完整的数据包
		if (len < packet_len + 2)
			break;

		// 从缓冲区中移除并读取完整数据包
		int read_len = evbuffer_remove(input, duel_client_read, packet_len + 2);

		// 处理有效的数据包内容（排除长度字段）
		if (read_len > 2)
			HandleSTOCPacketLan(&duel_client_read[2], read_len - 2);

		// 更新剩余数据长度
		len -= packet_len + 2;
	}

	// 释放临时缓冲区
	delete[] duel_client_read;
}
/**
 * @brief 处理客户端网络事件的回调函数。
 *
 * 当 libevent 的 bufferevent 发生特定事件（如连接成功、断开或错误）时，
 * 此函数会被调用。根据事件类型执行不同的逻辑处理：
 * - 若是连接成功的事件，则发送玩家信息及创建/加入房间请求；
 * - 若是连接关闭或出错事件，则进行界面恢复与提示操作。
 *
 * @param bev 指向触发事件的 bufferevent 对象。
 * @param events 表示发生的事件标志组合（例如：BEV_EVENT_CONNECTED 等）。
 * @param ctx 上下文context指针，用于传递额外数据（此处表示是否创建游戏）。
 */
void DuelClient::ClientEvent(bufferevent* bev, short events, void* ctx) {
	if (events & BEV_EVENT_CONNECTED) {
		// 判断当前是否需要创建新游戏
		bool create_game = (intptr_t)ctx;

		// 如果不是创建游戏而是加入已有房间，则发送主机名等地址相关信息到服务器
		if (!create_game) {
			uint16_t hostname_buf[LEN_HOSTNAME];
			auto hostname_len = BufferIO::CopyCharArray(mainGame->ebJoinHost->getText(), hostname_buf);
			auto hostname_msglen = (hostname_len + 1) * sizeof(uint16_t);
			char buf[LEN_HOSTNAME * sizeof(uint16_t) + sizeof(uint32_t)];
			memset(buf, 0, sizeof(uint32_t)); // real_ip 字段初始化为0
			memcpy(buf + sizeof(uint32_t), hostname_buf, hostname_msglen);
			SendBufferToServer(CTOS_EXTERNAL_ADDRESS, buf, hostname_msglen + sizeof(uint32_t));
		}

		// 发送玩家昵称给服务器
		CTOS_PlayerInfo cspi;
		BufferIO::CopyCharArray(mainGame->ebNickName->getText(), cspi.name);
		SendPacketToServer(CTOS_PLAYER_INFO, cspi);

		// 根据是否创建游戏分别构造并发送对应的数据包
		if (create_game) {
			CTOS_CreateGame cscg;
			if (bot_mode) {
				// 设置机器人模式下的默认配置
				BufferIO::CopyCharArray(L"Bot Game", cscg.name);
				BufferIO::CopyCharArray(L"", cscg.pass);
				cscg.info.rule = 5;
				cscg.info.mode = 0;
				cscg.info.start_hand = 5;
				cscg.info.start_lp = 8000;
				cscg.info.draw_count = 1;
				cscg.info.time_limit = 0;
				cscg.info.lflist = 0;
				cscg.info.duel_rule = mainGame->cbBotRule->getSelected() + 3;
				cscg.info.no_check_deck = mainGame->chkBotNoCheckDeck->isChecked();
				cscg.info.no_shuffle_deck = mainGame->chkBotNoShuffleDeck->isChecked();
			} else {
				// 获取用户输入的游戏设置，并填充至结构体中
				BufferIO::CopyCharArray(mainGame->ebServerName->getText(), cscg.name);
				BufferIO::CopyCharArray(mainGame->ebServerPass->getText(), cscg.pass);
				cscg.info.rule = mainGame->cbRule->getSelected();
				cscg.info.mode = mainGame->cbMatchMode->getSelected();
				cscg.info.start_hand = std::wcstol(mainGame->ebStartHand->getText(), nullptr, 10);
				cscg.info.start_lp = std::wcstol(mainGame->ebStartLP->getText(), nullptr, 10);
				cscg.info.draw_count = std::wcstol(mainGame->ebDrawCount->getText(), nullptr, 10);
				cscg.info.time_limit = std::wcstol(mainGame->ebTimeLimit->getText(), nullptr, 10);
				cscg.info.lflist = mainGame->gameConf.enable_genesys_mode == 1 ? mainGame->cbHostGenesysLFlist->getItemData(mainGame->cbHostGenesysLFlist->getSelected()) : mainGame->cbHostLFlist->getItemData(mainGame->cbHostLFlist->getSelected());
				cscg.info.duel_rule = mainGame->cbDuelRule->getSelected() + 1;
				cscg.info.no_check_deck = mainGame->chkNoCheckDeck->isChecked();
				cscg.info.no_shuffle_deck = mainGame->chkNoShuffleDeck->isChecked();
			}
			SendPacketToServer(CTOS_CREATE_GAME, cscg);
		} else {
			// 构造并发送加入房间的信息
			CTOS_JoinGame csjg;
			csjg.version = PRO_VERSION;
			csjg.gameid = 0;
			BufferIO::CopyCharArray(mainGame->ebJoinPass->getText(), csjg.pass);
			SendPacketToServer(CTOS_JOIN_GAME, csjg);
		}

		// 启用读取事件监听，并更新连接状态标记
		bufferevent_enable(bev, EV_READ);
		connect_state |= 0x2;
	} else if (events & (BEV_EVENT_EOF | BEV_EVENT_ERROR)) {
		// 禁用该 bufferevent 的读取功能
		bufferevent_disable(bev, EV_READ);

		// 在未主动关闭的情况下根据不同连接阶段显示不同提示信息并重置界面
		if (!is_closing) {
			if (connect_state == 0x1) {
				// 连接尚未完成即中断的情况
				mainGame->btnCreateHost->setEnabled(true);
				mainGame->btnJoinHost->setEnabled(true);
				mainGame->btnJoinCancel->setEnabled(true);
				mainGame->btnStartBot->setEnabled(true);
				mainGame->btnBotCancel->setEnabled(true);
				mainGame->gMutex.lock();
				if (bot_mode && !mainGame->wSinglePlay->isVisible())
					mainGame->ShowElement(mainGame->wSinglePlay);
				else if (!bot_mode && !mainGame->wLanWindow->isVisible())
					mainGame->ShowElement(mainGame->wLanWindow);
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
				mainGame->addMessageBox(L"", dataManager.GetSysString(1400));
				mainGame->gMutex.unlock();
			} else if (connect_state == 0x7) {
				// 已进入准备阶段后连接中断的情况
				if (!mainGame->dInfo.isStarted && !mainGame->is_building) {
					// 尚未开始决斗且不在卡组编辑器中的情况
					mainGame->btnCreateHost->setEnabled(true);
					mainGame->btnJoinHost->setEnabled(true);
					mainGame->btnJoinCancel->setEnabled(true);
					mainGame->btnStartBot->setEnabled(true);
					mainGame->btnBotCancel->setEnabled(true);
					mainGame->gMutex.lock();
					mainGame->HideElement(mainGame->wHostPrepare);
					if (bot_mode)
						mainGame->ShowElement(mainGame->wSinglePlay);
					else
						mainGame->ShowElement(mainGame->wLanWindow);
					mainGame->wChat->setVisible(false);
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					if (events & BEV_EVENT_EOF)
						mainGame->addMessageBox(L"", dataManager.GetSysString(1401));
					else
						mainGame->addMessageBox(L"", dataManager.GetSysString(1402));
					mainGame->gMutex.unlock();
				} else {
					// 决斗正在进行或在卡组构建过程中断开连接的情况
					mainGame->gMutex.lock();
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->addMessageBox(L"", dataManager.GetSysString(1502));
					mainGame->btnCreateHost->setEnabled(true);
					mainGame->btnJoinHost->setEnabled(true);
					mainGame->btnJoinCancel->setEnabled(true);
					mainGame->btnStartBot->setEnabled(true);
					mainGame->btnBotCancel->setEnabled(true);
					mainGame->stTip->setVisible(false);
					mainGame->gMutex.unlock();

					// 触发关闭流程并等待其完成
					mainGame->closeDoneSignal.Reset();
					mainGame->closeSignal.Set();
					mainGame->closeDoneSignal.Wait();

					// 清除相关状态变量并重新加载主菜单界面
					mainGame->gMutex.lock();
					mainGame->dInfo.isStarted = false;
					mainGame->dInfo.isInDuel = false;
					mainGame->dInfo.isFinished = false;
					mainGame->is_building = false;
					mainGame->ResizeChatInputWindow();
					mainGame->device->setEventReceiver(&mainGame->menuHandler);
					if (bot_mode)
						mainGame->ShowElement(mainGame->wSinglePlay);
					else
						mainGame->ShowElement(mainGame->wLanWindow);
					mainGame->gMutex.unlock();
				}
			}
		}

		// 停止事件循环以结束客户端通信过程
		event_base_loopexit(client_base, 0);
	}
}
/**
 * @brief 客户端线程主函数，处理网络事件循环
 *
 * 该函数负责启动事件循环，处理客户端的网络通信，
 * 并在退出时清理相关的资源。
 *
 * @return int 返回0表示正常退出
 */
int DuelClient::ClientThread() {
	// 启动事件循环，处理网络事件
	event_base_dispatch(client_base);

	// 清理缓冲事件和事件基础结构
	bufferevent_free(client_bev);
	event_base_free(client_base);

	// 重置相关指针和状态
	client_bev = 0;
	client_base = 0;
	connect_state = 0;

	return 0;
}

void DuelClient::HandleSTOCPacketLan(unsigned char* data, int len) {
	unsigned char* pdata = data;
	unsigned char pktType = BufferIO::Read<uint8_t>(pdata);
	switch(pktType) {
	case STOC_GAME_MSG: {
		if (len < 1 + (int)sizeof(unsigned char))
			return;
		ClientAnalyze(pdata, len - 1);
		break;
	}
	case STOC_ERROR_MSG: {
		if (len < 1 + (int)sizeof(STOC_ErrorMsg))
			return;
		STOC_ErrorMsg packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		switch(pkt->msg) {
        // 加入房间时候出错时，显示各种错误消息
		case ERRMSG_JOINERROR: {
			mainGame->btnCreateHost->setEnabled(true);
			mainGame->btnJoinHost->setEnabled(true);
			mainGame->btnJoinCancel->setEnabled(true);
			mainGame->btnStartBot->setEnabled(true);
			mainGame->btnBotCancel->setEnabled(true);
			mainGame->gMutex.lock();
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
			if(pkt->code == 0)
				mainGame->addMessageBox(L"", dataManager.GetSysString(1403));
			else if(pkt->code == 1)
				mainGame->addMessageBox(L"", dataManager.GetSysString(1404));
			else if(pkt->code == 2)
				mainGame->addMessageBox(L"", dataManager.GetSysString(1405));
			mainGame->gMutex.unlock();
			event_base_loopbreak(client_base);
			break;
		}
		case ERRMSG_DECKERROR: {
			mainGame->gMutex.lock();
			unsigned int code = pkt->code & MAX_CARD_ID;
			unsigned int flag = pkt->code >> 28;
			wchar_t msgbuf[256];
			switch(flag)
			{
			case DECKERROR_LFLIST: {
				myswprintf(msgbuf, dataManager.GetSysString(1407), dataManager.GetName(code));
				break;
			}
			case DECKERROR_OCGONLY: {
				myswprintf(msgbuf, dataManager.GetSysString(1413), dataManager.GetName(code));
				break;
			}
			case DECKERROR_TCGONLY: {
				myswprintf(msgbuf, dataManager.GetSysString(1414), dataManager.GetName(code));
				break;
			}
			case DECKERROR_UNKNOWNCARD: {
				myswprintf(msgbuf, dataManager.GetSysString(1415), dataManager.GetName(code), code);
				break;
			}
			case DECKERROR_CARDCOUNT: {
				myswprintf(msgbuf, dataManager.GetSysString(1416), dataManager.GetName(code));
				break;
			}
			case DECKERROR_MAINCOUNT: {
				myswprintf(msgbuf, dataManager.GetSysString(1417), code);
				break;
			}
			case DECKERROR_EXTRACOUNT: {
				if(code>0)
					myswprintf(msgbuf, dataManager.GetSysString(1418), code);
				else
					myswprintf(msgbuf, dataManager.GetSysString(1420));
				break;
			}
			case DECKERROR_SIDECOUNT: {
				myswprintf(msgbuf, dataManager.GetSysString(1419), code);
				break;
			}
			case DECKERROR_NOTAVAIL: {
				myswprintf(msgbuf, dataManager.GetSysString(1432), dataManager.GetName(code));
				break;
			}
			default: {
				myswprintf(msgbuf, dataManager.GetSysString(1406));
				break;
			}
			}
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
			mainGame->addMessageBox(L"", msgbuf);
			mainGame->cbCategorySelect->setEnabled(true);
			mainGame->cbDeckSelect->setEnabled(true);
			mainGame->btnHostDeckSelect->setEnabled(true);
			mainGame->btnHostPrepStart->setEnabled(true);
			mainGame->gMutex.unlock();
			break;
		}
		case ERRMSG_SIDEERROR: {
			mainGame->gMutex.lock();
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
			mainGame->addMessageBox(L"", dataManager.GetSysString(1408));
			mainGame->gMutex.unlock();
			break;
		}
		case ERRMSG_VERERROR: {
			mainGame->btnCreateHost->setEnabled(true);
			mainGame->btnJoinHost->setEnabled(true);
			mainGame->btnJoinCancel->setEnabled(true);
			mainGame->btnStartBot->setEnabled(true);
			mainGame->btnBotCancel->setEnabled(true);
			mainGame->gMutex.lock();
			wchar_t msgbuf[256];
			myswprintf(msgbuf, dataManager.GetSysString(1411), pkt->code >> 12, (pkt->code >> 4) & 0xff, pkt->code & 0xf);
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
			mainGame->addMessageBox(L"", msgbuf);
			mainGame->gMutex.unlock();
			event_base_loopbreak(client_base);
			break;
		}
		}
		break;
	}
	case STOC_SELECT_HAND: {
		mainGame->wHand->setVisible(true);
		break;
	}
	case STOC_SELECT_TP: {
		mainGame->gMutex.lock();
		mainGame->PopupElement(mainGame->wFTSelect);
		mainGame->gMutex.unlock();
		break;
	}
	case STOC_HAND_RESULT: {
		if (len < 1 + (int)sizeof(STOC_HandResult))
			return;
		STOC_HandResult packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		mainGame->stHintMsg->setVisible(false);
		mainGame->showcardcode = (pkt->res1 - 1) + ((pkt->res2 - 1) << 16);
		mainGame->showcarddif = 50;
		mainGame->showcardp = 0;
		mainGame->showcard = 100;
		mainGame->WaitFrameSignal(60);
		break;
	}
	case STOC_TP_RESULT: {
		break;
	}
	case STOC_CHANGE_SIDE: {
		mainGame->gMutex.lock();
		mainGame->dInfo.isStarted = false;
		mainGame->dInfo.isInDuel = false;
		mainGame->dField.Clear();
		mainGame->is_building = true;
		mainGame->is_siding = true;
		mainGame->CloseGameWindow();
		mainGame->ResizeChatInputWindow();
		mainGame->wChat->setVisible(false);
		mainGame->imgChat->setVisible(false);
		mainGame->imgEmoticon->setVisible(false);
		mainGame->wDeckEdit->setVisible(false);
		mainGame->wFilter->setVisible(false);
		mainGame->wSort->setVisible(false);
		if(mainGame->dInfo.player_type < 7)
			mainGame->btnLeaveGame->setVisible(false);
		mainGame->btnSideOK->setVisible(true);
		mainGame->btnSideShuffle->setVisible(true);
		mainGame->btnSideSort->setVisible(true);
		mainGame->btnSideReload->setVisible(true);
		mainGame->deckBuilder.result_string[0] = L'0';
		mainGame->deckBuilder.result_string[1] = 0;
		mainGame->deckBuilder.results.clear();
		mainGame->deckBuilder.hovered_code = 0;
		mainGame->deckBuilder.is_draging = false;
		mainGame->deckBuilder.is_starting_dragging = false;
		mainGame->deckBuilder.readonly = false;
		mainGame->deckBuilder.showing_pack = false;
		mainGame->deckBuilder.pre_mainc = deckManager.current_deck.main.size();
		mainGame->deckBuilder.pre_extrac = deckManager.current_deck.extra.size();
		mainGame->deckBuilder.pre_sidec = deckManager.current_deck.side.size();
		mainGame->device->setEventReceiver(&mainGame->deckBuilder);
		mainGame->gMutex.unlock();
		break;
	}
	case STOC_WAITING_SIDE: {
		mainGame->dInfo.isInDuel = false;
		mainGame->gMutex.lock();
		mainGame->dField.Clear();
		mainGame->stHintMsg->setText(dataManager.GetSysString(1409));
		mainGame->stHintMsg->setVisible(true);
		mainGame->gMutex.unlock();
		break;
	}
	case STOC_DECK_COUNT: {
		if (len < 1 + (int)sizeof(int16_t) * 6)
			return;
		mainGame->gMutex.lock();
		int deckc = BufferIO::Read<uint16_t>(pdata);
		int extrac = BufferIO::Read<uint16_t>(pdata);
		int sidec = BufferIO::Read<uint16_t>(pdata);
		mainGame->dField.Initial(0, deckc, extrac, sidec);
		deckc = BufferIO::Read<uint16_t>(pdata);
		extrac = BufferIO::Read<uint16_t>(pdata);
		sidec = BufferIO::Read<uint16_t>(pdata);
		mainGame->dField.Initial(1, deckc, extrac, sidec);
		mainGame->gMutex.unlock();
		break;
	}
	// 处理加入游戏房间的服务器响应消息
    case STOC_JOIN_GAME: {
		// 检查数据包长度是否足够，不足则返回
		if (len < 1 + (int)sizeof(STOC_JoinGame))
			return;
		// 创建并复制数据包内容到结构体
		STOC_JoinGame packet;
		std::memcpy(&packet, pdata, sizeof packet);
		// 获取数据包指针
		const auto* pkt = &packet;
		// 创建用于显示房间信息的字符串
		std::wstring str;
		/* 在决斗准备的窗口显示该房间的设置信息，一般结构如下：
		 * 禁限卡表：
		 * 卡片允许：
		 * 决斗模式：
		 * 每回合时间：
		 * ==========
		 * 初始基本分：
		 * 初始手卡数：
		 * 每回合抽卡：
		 * *大师规则（3,2017）--目前不是默认大师规则2020才显示
		 * *不检查卡组 --特殊设置才显示
		 * *不洗切卡组 --特殊设置才显示
		 * */
		wchar_t msgbuf[256];
		// 禁限卡表信息到房间信息文本
		myswprintf(msgbuf, L"%ls%ls\n", dataManager.GetSysString(1226), deckManager.GetLFListName(pkt->info.lflist));
		str.append(msgbuf);
		// 卡片允许信息到房间信息文本
		myswprintf(msgbuf, L"%ls%ls\n", dataManager.GetSysString(1225), dataManager.GetSysString(1481 + pkt->info.rule));
		str.append(msgbuf);
		// 决斗模式信息到房间信息文本
		myswprintf(msgbuf, L"%ls%ls\n", dataManager.GetSysString(1227), dataManager.GetSysString(1244 + pkt->info.mode));
		str.append(msgbuf);
		// 如果设置了回合时间限制，就添加时限信息到房间信息文本
		if(pkt->info.time_limit) {
			myswprintf(msgbuf, L"%ls%d\n", dataManager.GetSysString(1237), pkt->info.time_limit);
			str.append(msgbuf);
		}
		// 添加分隔线
		str.append(L"==========\n");
		// 初始基本分信息到显示字符串
		myswprintf(msgbuf, L"%ls%d\n", dataManager.GetSysString(1231), pkt->info.start_lp);
		str.append(msgbuf);
		// 初始手卡数信息到显示字符串
		myswprintf(msgbuf, L"%ls%d\n", dataManager.GetSysString(1232), pkt->info.start_hand);
		str.append(msgbuf);
		// 每回合抽卡数信息到显示字符串
		myswprintf(msgbuf, L"%ls%d\n", dataManager.GetSysString(1233), pkt->info.draw_count);
		str.append(msgbuf);
		// 如果决斗规则不是默认规则，添加决斗规则信息（大师规则3，新大师规则2017，等）到显示字符串
		if(pkt->info.duel_rule != DEFAULT_DUEL_RULE) {
			myswprintf(msgbuf, L"*%ls\n", dataManager.GetSysString(1260 + pkt->info.duel_rule - 1));
			str.append(msgbuf);
		}
		// 如果不检查卡组，添加 *不检查卡组 提示信息到显示字符串
		if(pkt->info.no_check_deck) {
			myswprintf(msgbuf, L"*%ls\n", dataManager.GetSysString(1229));
			str.append(msgbuf);
		}
		// 如果不洗牌，添加*不洗切卡组 提示信息到显示字符串
		if(pkt->info.no_shuffle_deck) {
			myswprintf(msgbuf, L"*%ls\n", dataManager.GetSysString(1230));
			str.append(msgbuf);
		}
		// 锁定图形界面互斥锁
		mainGame->gMutex.lock();
		// 根据游戏模式设置是否为TAG双打模式，并显示3楼和4楼玩家位置等相应界面元素
		if(pkt->info.mode == 2) {
			mainGame->dInfo.isTag = true;
			mainGame->chkHostPrepReady[2]->setVisible(true);
			mainGame->chkHostPrepReady[3]->setVisible(true);
			mainGame->stHostPrepDuelist[2]->setVisible(true);
			mainGame->stHostPrepDuelist[3]->setVisible(true);
		} else {
			mainGame->dInfo.isTag = false;
			mainGame->chkHostPrepReady[2]->setVisible(false);
			mainGame->chkHostPrepReady[3]->setVisible(false);
			mainGame->stHostPrepDuelist[2]->setVisible(false);
			mainGame->stHostPrepDuelist[3]->setVisible(false);
		}
		// 重置准备状态和玩家信息显示
		for(int i = 0; i < 4; ++i) {
			mainGame->chkHostPrepReady[i]->setChecked(false);
			mainGame->stHostPrepDuelist[i]->setText(L"");
			mainGame->stHostPrepDuelist[i]->setToolTipText(L"");
		}
		// 显示准备按钮，隐藏取消准备按钮
		mainGame->btnHostPrepReady->setVisible(true);
		mainGame->btnHostPrepNotReady->setVisible(false);
		// 设置游戏时间限制和时间显示
		mainGame->dInfo.time_limit = pkt->info.time_limit;
		mainGame->dInfo.time_left[0] = 0;
		mainGame->dInfo.time_left[1] = 0;
		mainGame->RefreshTimeDisplay();
		// 设置卡组编辑DeckBuilder的当前禁卡表过滤列表
		mainGame->deckBuilder.filterList = deckManager.GetLFList(pkt->info.lflist);
		if(mainGame->deckBuilder.filterList == nullptr)
			mainGame->deckBuilder.filterList = &deckManager._lfList[0];
		// 清空观战者显示
		mainGame->stHostPrepOB->setText(L"");
		// 设置房间规则信息显示
		mainGame->SetStaticText(mainGame->stHostPrepRule, 180 * mainGame->xScale, mainGame->guiFont, str.c_str());
		// 刷新卡组分类和卡组选择下拉框（注意：此处界面布局已经被隐藏，但还是初始化方便卡组选择按钮弹出的卡组分类管理窗口能调用初始化后的值）
		mainGame->RefreshCategoryDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect);
		// 启用分类和卡组选择下拉框以及卡组选择按钮（注意：此处界面布局已经被隐藏，但还是初始化方便卡组选择按钮弹出的卡组分类管理窗口能调用初始化后的值）
		mainGame->cbCategorySelect->setEnabled(true);
		mainGame->cbDeckSelect->setEnabled(true);
		mainGame->btnHostDeckSelect->setEnabled(true);
		// 隐藏创建房间、局域网和单人游戏窗口，之后只显示房间准备界面
		mainGame->HideElement(mainGame->wCreateHost);
		mainGame->HideElement(mainGame->wLanWindow);
		mainGame->HideElement(mainGame->wSinglePlay);
		// 显示房间准备界面
		mainGame->ShowElement(mainGame->wHostPrepare);
		// 调整聊天输入窗口大小（在房间准备界面，还是显示在默认底部位置）
		mainGame->ResizeChatInputWindow();
		// 保存当前选择的卡组分类和卡组（注意：此处界面布局已经被隐藏，但还是初始化方便卡组选择按钮弹出的卡组分类管理窗口能调用初始化后的值）
		mainGame->deckBuilder.prev_category = mainGame->cbCategorySelect->getSelected();
		mainGame->deckBuilder.prev_deck = mainGame->cbDeckSelect->getSelected();
        // 构造当前选择的卡组分类和卡组名称显示文本在按钮上
        wchar_t cate[256];
        wchar_t cate_deck[256];
        myswprintf(cate, L"%ls%ls", (mainGame->cbCategorySelect->getSelected())==1 ? L"" : mainGame->cbCategorySelect->getItem(mainGame->cbCategorySelect->getSelected()), (mainGame->cbCategorySelect->getSelected())==1 ? L"" : L"|");
        if (mainGame->cbDeckSelect->getItemCount() != 0) {
			myswprintf(cate_deck, L"%ls%ls", cate, mainGame->cbDeckSelect->getItem(mainGame->cbDeckSelect->getSelected()));
		} else {
			myswprintf(cate_deck, L"%ls%ls", cate, dataManager.GetSysString(1301));
		}
        // 构造文本后设置到卡组选择按钮显示文本
        mainGame->btnHostDeckSelect->setText(cate_deck);
		// 根据设置决定是否显示聊天窗口
		if(!mainGame->chkIgnore1->isChecked())
			mainGame->wChat->setVisible(true);
		// 解锁图形界面互斥锁
		mainGame->gMutex.unlock();
		// 设置决斗规则和初始LP
		mainGame->dInfo.duel_rule = pkt->info.duel_rule;
		mainGame->dInfo.start_lp = pkt->info.start_lp;
		// 重置观战者计数
		watching = 0;
		// 更新连接状态标志
		connect_state |= 0x4;
		// 跳出switch语句
		break;
	}
	case STOC_TYPE_CHANGE: {
		if (len < 1 + (int)sizeof(STOC_TypeChange))
			return;
		STOC_TypeChange packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		if(!mainGame->dInfo.isTag) {
			selftype = pkt->type & 0xf;
			is_host = ((pkt->type >> 4) & 0xf) != 0;
			mainGame->btnHostPrepKick[2]->setVisible(false);
			mainGame->btnHostPrepKick[3]->setVisible(false);
			if(is_host) {
				mainGame->btnHostPrepStart->setVisible(true);
				mainGame->btnHostPrepKick[0]->setVisible(true);
				mainGame->btnHostPrepKick[1]->setVisible(true);
			} else {
				mainGame->btnHostPrepStart->setVisible(false);
				mainGame->btnHostPrepKick[0]->setVisible(false);
				mainGame->btnHostPrepKick[1]->setVisible(false);
			}
			mainGame->chkHostPrepReady[0]->setEnabled(false);
			mainGame->chkHostPrepReady[1]->setEnabled(false);
			if(selftype < 2) {
				mainGame->chkHostPrepReady[selftype]->setEnabled(true);
				mainGame->chkHostPrepReady[selftype]->setChecked(false);
				mainGame->btnHostPrepDuelist->setEnabled(false);
				mainGame->btnHostPrepOB->setEnabled(true);
				mainGame->btnHostPrepReady->setVisible(true);
				mainGame->btnHostPrepNotReady->setVisible(false);
			} else {
				mainGame->btnHostPrepDuelist->setEnabled(true);
				mainGame->btnHostPrepOB->setEnabled(false);
				mainGame->btnHostPrepReady->setVisible(false);
				mainGame->btnHostPrepNotReady->setVisible(false);
			}
			if(mainGame->chkHostPrepReady[0]->isChecked() && mainGame->chkHostPrepReady[1]->isChecked()) {
				mainGame->btnHostPrepStart->setEnabled(true);
			} else {
				mainGame->btnHostPrepStart->setEnabled(false);
			}
		} else {
			if(selftype < 4) {
				mainGame->chkHostPrepReady[selftype]->setEnabled(false);
				mainGame->chkHostPrepReady[selftype]->setChecked(false);
			}
			selftype = pkt->type & 0xf;
			is_host = ((pkt->type >> 4) & 0xf) != 0;
			mainGame->btnHostPrepDuelist->setEnabled(true);
			if(is_host) {
				mainGame->btnHostPrepStart->setVisible(true);
				for(int i = 0; i < 4; ++i)
					mainGame->btnHostPrepKick[i]->setVisible(true);
			} else {
				mainGame->btnHostPrepStart->setVisible(false);
				for(int i = 0; i < 4; ++i)
					mainGame->btnHostPrepKick[i]->setVisible(false);
			}
			if(selftype < 4) {
				mainGame->chkHostPrepReady[selftype]->setEnabled(true);
				mainGame->btnHostPrepOB->setEnabled(true);
				mainGame->btnHostPrepReady->setVisible(true);
				mainGame->btnHostPrepNotReady->setVisible(false);
			} else {
				mainGame->btnHostPrepOB->setEnabled(false);
				mainGame->btnHostPrepReady->setVisible(false);
				mainGame->btnHostPrepNotReady->setVisible(false);
			}
			if(mainGame->chkHostPrepReady[0]->isChecked() && mainGame->chkHostPrepReady[1]->isChecked()
				&& mainGame->chkHostPrepReady[2]->isChecked() && mainGame->chkHostPrepReady[3]->isChecked()) {
				mainGame->btnHostPrepStart->setEnabled(true);
			} else {
				mainGame->btnHostPrepStart->setEnabled(false);
			}
		}
		mainGame->dInfo.player_type = selftype;
		break;
	}
	// 处理决斗开始消息
    case STOC_DUEL_START: {
		// 隐藏房间准备界面和卡组管理界面
		mainGame->HideElement(mainGame->wHostPrepare);
        mainGame->HideElement(mainGame->wDeckManage);
		// 等待11帧，确保界面切换完成
		mainGame->WaitFrameSignal(11);
		// 锁定图形界面互斥锁
		mainGame->gMutex.lock();
		// 清空游戏场地
		mainGame->dField.Clear();
		// 设置决斗状态为已开始
		mainGame->dInfo.isStarted = true;
		// 设置决斗状态为未结束
		mainGame->dInfo.isFinished = false;
		// 初始化双方LP为0
		mainGame->dInfo.lp[0] = 0;
		mainGame->dInfo.lp[1] = 0;
		// 清空LP显示字符串
		mainGame->dInfo.strLP[0][0] = 0;
		mainGame->dInfo.strLP[1][0] = 0;
		// 初始化回合数为0
		mainGame->dInfo.turn = 0;
		// 初始化双方剩余时间
		mainGame->dInfo.time_left[0] = 0;
		mainGame->dInfo.time_left[1] = 0;
		// 刷新时间显示
		mainGame->RefreshTimeDisplay();
		// 设置当前计时玩家为2(无)
		mainGame->dInfo.time_player = 2;
		// 设置回放交换状态为false
		mainGame->dInfo.isReplaySwapped = false;
		// 设置不在卡组构建状态
		mainGame->is_building = false;
		// 显示卡牌图像窗口
		mainGame->wCardImg->setVisible(true);
		// 显示信息窗口
		mainGame->wInfos->setVisible(true);
		// 显示调色板窗口
		mainGame->wPallet->setVisible(true);
		// 显示阶段窗口
		mainGame->wPhase->setVisible(true);
		// 隐藏侧边确定按钮
		mainGame->btnSideOK->setVisible(false);
		// 隐藏侧边洗牌按钮
		mainGame->btnSideShuffle->setVisible(false);
		// 隐藏侧边排序按钮
		mainGame->btnSideSort->setVisible(false);
		// 隐藏侧边重新加载按钮
		mainGame->btnSideReload->setVisible(false);
		// 隐藏阶段状态按钮
		mainGame->btnPhaseStatus->setVisible(false);
		// 隐藏战斗阶段按钮
		mainGame->btnBP->setVisible(false);
		// 隐藏主要阶段2按钮
		mainGame->btnM2->setVisible(false);
		// 隐藏结束阶段按钮
		mainGame->btnEP->setVisible(false);
		// 隐藏洗牌按钮
		mainGame->btnShuffle->setVisible(false);
		// 调整聊天输入窗口大小
		mainGame->ResizeChatInputWindow();
		// 根据设置决定是否显示聊天窗口
		if(!mainGame->chkIgnore1->isChecked())
			mainGame->wChat->setVisible(true);
		// 根据默认连锁显示设置配置连锁相关变量
		if(mainGame->chkDefaultShowChain->isChecked()) {
			mainGame->always_chain = true;
			mainGame->ignore_chain = false;
			mainGame->chain_when_avail = false;
		}
		// 设置事件接收器为决斗场地
		mainGame->device->setEventReceiver(&mainGame->dField);
		// 非Tag模式处理
		if(!mainGame->dInfo.isTag) {
			// 如果是观战者(位置大于1)
			if(selftype > 1) {
				// 设置玩家类型为观战者
				mainGame->dInfo.player_type = NETPLAYER_TYPE_OBSERVER;
				// 设置离开游戏按钮文本为"观战"
				mainGame->btnLeaveGame->setText(dataManager.GetSysString(1350));
				// 显示离开游戏按钮
				mainGame->btnLeaveGame->setVisible(true);
				// 显示观战者交换按钮
				mainGame->btnSpectatorSwap->setVisible(true);
                mainGame->imgEmoticon->setVisible(false);// 观战时暂不允许发送表情
			}
			// 根据玩家位置设置主机名和客机名
			if(selftype != 1) {
				// 复制第0位玩家的昵称作为主机名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[0]->getToolTipText().c_str(), mainGame->dInfo.hostname);
				// 复制第1位玩家的昵称作为客机名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[1]->getToolTipText().c_str(), mainGame->dInfo.clientname);
			} else {
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[1]->getToolTipText().c_str(), mainGame->dInfo.hostname);
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[0]->getToolTipText().c_str(), mainGame->dInfo.clientname);
			}
		} else {
			// Tag模式处理
			// 如果是观战者(位置大于3)
			if(selftype > 3) {
				// 设置玩家类型为观战者
				mainGame->dInfo.player_type = NETPLAYER_TYPE_OBSERVER;
				// 设置离开游戏按钮文本为"观战"
				mainGame->btnLeaveGame->setText(dataManager.GetSysString(1350));
				// 显示离开游戏按钮
				mainGame->btnLeaveGame->setVisible(true);
				// 显示观战者交换按钮
				mainGame->btnSpectatorSwap->setVisible(true);
                mainGame->imgEmoticon->setVisible(false);// 观战时暂不允许发送表情
			}
			// 根据玩家位置设置主机名、主机标签名、客机名和客机标签名
			if(selftype > 1 && selftype < 4) {
				// 复制第2位玩家的工具提示文本作为主机名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[2]->getToolTipText().c_str(), mainGame->dInfo.hostname);
				// 复制第3位玩家的工具提示文本作为主机标签名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[3]->getToolTipText().c_str(), mainGame->dInfo.hostname_tag);
				// 复制第0位玩家的工具提示文本作为客机名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[0]->getToolTipText().c_str(), mainGame->dInfo.clientname);
				// 复制第1位玩家的工具提示文本作为客机标签名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[1]->getToolTipText().c_str(), mainGame->dInfo.clientname_tag);
			} else {
				// 复制第0位玩家的工具提示文本作为主机名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[0]->getToolTipText().c_str(), mainGame->dInfo.hostname);
				// 复制第1位玩家的工具提示文本作为主机标签名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[1]->getToolTipText().c_str(), mainGame->dInfo.hostname_tag);
				// 复制第2位玩家的工具提示文本作为客机名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[2]->getToolTipText().c_str(), mainGame->dInfo.clientname);
				// 复制第3位玩家的工具提示文本作为客机标签名
				BufferIO::CopyWideString(mainGame->stHostPrepDuelist[3]->getToolTipText().c_str(), mainGame->dInfo.clientname_tag);
			}
			// 初始化Tag玩家状态
			mainGame->dInfo.tag_player[0] = false;
			mainGame->dInfo.tag_player[1] = false;
		}
		// 解锁图形界面互斥锁
		mainGame->gMutex.unlock();
		// 重置match_kill
		match_kill = 0;
		// 跳出switch语句
		break;
	}
	case STOC_DUEL_END: {
		mainGame->gMutex.lock();
		if(mainGame->dInfo.player_type < 7)
			mainGame->btnLeaveGame->setVisible(false);
		mainGame->CloseGameButtons();
		mainGame->stMessage->setText(dataManager.GetSysString(1500));
		mainGame->PopupElement(mainGame->wMessage);
		mainGame->gMutex.unlock();
		mainGame->actionSignal.Reset();
		mainGame->actionSignal.Wait();
		mainGame->closeDoneSignal.Reset();
		mainGame->closeSignal.Set();
		mainGame->closeDoneSignal.Wait();
		mainGame->gMutex.lock();
		mainGame->dInfo.isStarted = false;
		mainGame->dInfo.isInDuel = false;
		mainGame->dInfo.isFinished = true;
		mainGame->is_building = false;
		mainGame->wDeckEdit->setVisible(false);
		mainGame->btnCreateHost->setEnabled(true);
		mainGame->btnJoinHost->setEnabled(true);
		mainGame->btnJoinCancel->setEnabled(true);
		mainGame->btnStartBot->setEnabled(true);
		mainGame->btnBotCancel->setEnabled(true);
		mainGame->stTip->setVisible(false);
		mainGame->ResizeChatInputWindow();
		mainGame->device->setEventReceiver(&mainGame->menuHandler);
		if(bot_mode)
			mainGame->ShowElement(mainGame->wSinglePlay);
		else
			mainGame->ShowElement(mainGame->wLanWindow);
		mainGame->gMutex.unlock();
		mainGame->SaveConfig();
		event_base_loopbreak(client_base);
		if(exit_on_return)
			mainGame->OnGameClose();
		break;
	}
	case STOC_REPLAY: {
		if (len < 1 + (int)sizeof(ExtendedReplayHeader))
			return;
		mainGame->gMutex.lock();
		mainGame->wPhase->setVisible(false);
		if(mainGame->dInfo.player_type < 7)
			mainGame->btnLeaveGame->setVisible(false);
		mainGame->CloseGameButtons();
		auto prep = pdata;
		Replay new_replay;
		std::memcpy(&new_replay.pheader, prep, sizeof(new_replay.pheader));
		time_t starttime;
		if (new_replay.pheader.base.flag & REPLAY_UNIFORM)
			starttime = new_replay.pheader.base.start_time;
		else
			starttime = new_replay.pheader.base.seed;
		
		wchar_t timetext[40];
		std::wcsftime(timetext, sizeof timetext / sizeof timetext[0], L"%Y-%m-%d %H-%M-%S", std::localtime(&starttime));
		mainGame->ebRSName->setText(timetext);
		if(!mainGame->chkAutoSaveReplay->isChecked()) {
			mainGame->wReplaySave->setText(dataManager.GetSysString(1340));
			mainGame->PopupElement(mainGame->wReplaySave);
			mainGame->gMutex.unlock();
			mainGame->replaySignal.Reset();
			mainGame->replaySignal.Wait();
		}
		else {
			mainGame->actionParam = 1;
			wchar_t msgbuf[256];
			myswprintf(msgbuf, dataManager.GetSysString(1367), timetext);
			mainGame->SetStaticText(mainGame->stACMessage, 310, mainGame->guiFont, msgbuf);
			mainGame->PopupElement(mainGame->wACMessage, 20);
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(30);
		}
		if(mainGame->actionParam || !is_host) {
			prep += sizeof new_replay.pheader;
			std::memcpy(new_replay.comp_data, prep, len - sizeof new_replay.pheader - 1);
			new_replay.comp_size = len - sizeof new_replay.pheader - 1;
			if (mainGame->actionParam) {
				bool save_result = new_replay.SaveReplay(mainGame->ebRSName->getText());
				if (!save_result)
					new_replay.SaveReplay(L"_LastReplay");
			}
			else
				new_replay.SaveReplay(L"_LastReplay");
		}
		break;
	}
	case STOC_TIME_LIMIT: {
		if (len < 1 + (int)sizeof(STOC_TimeLimit))
			return;
		STOC_TimeLimit packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		int lplayer = mainGame->LocalPlayer(pkt->player);
		if(lplayer == 0)
			DuelClient::SendPacketToServer(CTOS_TIME_CONFIRM);
		mainGame->dInfo.time_player = lplayer;
		mainGame->dInfo.time_left[lplayer] = pkt->left_time;
		mainGame->RefreshTimeDisplay();
		break;
	}
	case STOC_CHAT: {
		if (len < 1 + sizeof(uint16_t) + sizeof(uint16_t) * 1)
			return;
		if (len > 1 + sizeof(uint16_t) + sizeof(uint16_t) * LEN_CHAT_MSG)
			return;
		const int chat_msg_size = len - 1 - sizeof(uint16_t);
		if (chat_msg_size % sizeof(uint16_t))
			return;
		uint16_t chat_player_type = BufferIO::Read<uint16_t>(pdata);
		uint16_t chat_msg[LEN_CHAT_MSG];
		std::memcpy(chat_msg, pdata, chat_msg_size);
		pdata += chat_msg_size;
		const int chat_msg_len = chat_msg_size / sizeof(uint16_t);
		if (chat_msg[chat_msg_len - 1] != 0)
			return;
		int player = chat_player_type;
		auto play_sound = false;
		if(player < 4) {
			if(mainGame->chkIgnore1->isChecked())
				break;
			auto localplayer = mainGame->ChatLocalPlayer(player);
			player = localplayer & 0xf;
			if(!(localplayer & 0x10))
				play_sound = true;
		} else {
			if(player == 8) { //system custom message.
				play_sound = true;
				if(mainGame->chkIgnore1->isChecked())
					break;
			} else if(player < 11 || player > 19) {
				if(mainGame->chkIgnore2->isChecked())
					break;
				player = 10;
			}
		}
		// UTF-16 to wchar_t
		wchar_t msg[LEN_CHAT_MSG];
		BufferIO::CopyCharArray(chat_msg, msg);
		mainGame->gMutex.lock();
		mainGame->AddChatMsg(msg, player, play_sound);
		mainGame->gMutex.unlock();
		break;
	}
	case STOC_HS_PLAYER_ENTER: {
		if (len < 1 + sizeof(STOC_HS_PlayerEnter))
			return;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::PLAYER_ENTER);
		STOC_HS_PlayerEnter packet;
		std::memcpy(&packet, pdata, sizeof(STOC_HS_PlayerEnter));
		auto pkt = &packet;
		if(pkt->pos > 3)
			break;
		wchar_t name[20];
		BufferIO::NullTerminate(pkt->name);
		BufferIO::CopyCharArray(pkt->name, name);
		if(mainGame->dInfo.isTag) {
			if(pkt->pos == 0)
				BufferIO::CopyCharArray(pkt->name, mainGame->dInfo.hostname);
			else if(pkt->pos == 1)
				BufferIO::CopyCharArray(pkt->name, mainGame->dInfo.hostname_tag);
			else if(pkt->pos == 2)
				BufferIO::CopyCharArray(pkt->name, mainGame->dInfo.clientname);
			else if(pkt->pos == 3)
				BufferIO::CopyCharArray(pkt->name, mainGame->dInfo.clientname_tag);
		} else {
			if(pkt->pos == 0)
				BufferIO::CopyCharArray(pkt->name, mainGame->dInfo.hostname);
			else if(pkt->pos == 1)
				BufferIO::CopyCharArray(pkt->name, mainGame->dInfo.clientname);
		}
		mainGame->gMutex.lock();
		if(mainGame->gameConf.hide_player_name)
			mainGame->stHostPrepDuelist[pkt->pos]->setText(L"[********]");
		else
			mainGame->stHostPrepDuelist[pkt->pos]->setText(name);
		mainGame->stHostPrepDuelist[pkt->pos]->setToolTipText(name);
		mainGame->stHostPrepDuelist[pkt->pos]->setBackgroundColor(0x60045f6a);
		mainGame->gMutex.unlock();
		break;
	}
	case STOC_HS_PLAYER_CHANGE: {
		if (len < 1 + (int)sizeof(STOC_HS_PlayerChange))
			return;
		STOC_HS_PlayerChange packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		unsigned char pos = (pkt->status >> 4) & 0xf;
		unsigned char state = pkt->status & 0xf;
		if(pos > 3)
			break;
		mainGame->gMutex.lock();
		if(state < 8) {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::PLAYER_ENTER);
			const wchar_t* prename = mainGame->stHostPrepDuelist[pos]->getToolTipText().c_str();
			if(mainGame->gameConf.hide_player_name)
				mainGame->stHostPrepDuelist[state]->setText(L"[********]");
			else
				mainGame->stHostPrepDuelist[state]->setText(prename);
			mainGame->stHostPrepDuelist[state]->setToolTipText(prename);
			mainGame->stHostPrepDuelist[state]->setBackgroundColor(0x60045f6a);
			mainGame->stHostPrepDuelist[pos]->setText(L"");
			mainGame->stHostPrepDuelist[pos]->setToolTipText(L"");
			mainGame->stHostPrepDuelist[pos]->setDrawBackground(false);
			mainGame->chkHostPrepReady[pos]->setChecked(false);
			if(pos == 0)
				BufferIO::CopyCharArray(prename, mainGame->dInfo.hostname);
			else if(pos == 1)
				BufferIO::CopyCharArray(prename, mainGame->dInfo.hostname_tag);
			else if(pos == 2)
				BufferIO::CopyCharArray(prename, mainGame->dInfo.clientname);
			else if(pos == 3)
				BufferIO::CopyCharArray(prename, mainGame->dInfo.clientname_tag);
		} else if(state == PLAYERCHANGE_READY) {
			mainGame->chkHostPrepReady[pos]->setChecked(true);
			if(pos == selftype) {
				mainGame->btnHostPrepReady->setVisible(false);
				mainGame->btnHostPrepNotReady->setVisible(true);
			}
		} else if(state == PLAYERCHANGE_NOTREADY) {
			mainGame->chkHostPrepReady[pos]->setChecked(false);
			if(pos == selftype) {
				mainGame->btnHostPrepReady->setVisible(true);
				mainGame->btnHostPrepNotReady->setVisible(false);
			}
		} else if(state == PLAYERCHANGE_LEAVE) {
			mainGame->stHostPrepDuelist[pos]->setText(L"");
			mainGame->stHostPrepDuelist[pos]->setToolTipText(L"");
			mainGame->stHostPrepDuelist[pos]->setDrawBackground(false);
			mainGame->chkHostPrepReady[pos]->setChecked(false);
		} else if(state == PLAYERCHANGE_OBSERVE) {
			watching++;
			wchar_t watchbuf[32];
			myswprintf(watchbuf, L"%ls%d", dataManager.GetSysString(1253), watching);
			mainGame->stHostPrepDuelist[pos]->setText(L"");
			mainGame->stHostPrepDuelist[pos]->setToolTipText(L"");
            mainGame->stHostPrepDuelist[pos]->setDrawBackground(false);
			mainGame->chkHostPrepReady[pos]->setChecked(false);
			mainGame->stHostPrepOB->setText(watchbuf);
		}
		if(mainGame->chkHostPrepReady[0]->isChecked() && mainGame->chkHostPrepReady[1]->isChecked()
			&& (!mainGame->dInfo.isTag || (mainGame->chkHostPrepReady[2]->isChecked() && mainGame->chkHostPrepReady[3]->isChecked()))) {
			mainGame->btnHostPrepStart->setEnabled(true);
		} else {
			mainGame->btnHostPrepStart->setEnabled(false);
		}
		mainGame->gMutex.unlock();
		break;
	}
	case STOC_HS_WATCH_CHANGE: {
		if (len < 1 + (int)sizeof(STOC_HS_WatchChange))
			return;
		STOC_HS_WatchChange packet;
		std::memcpy(&packet, pdata, sizeof packet);
		const auto* pkt = &packet;
		watching = pkt->watch_count;
		wchar_t watchbuf[32];
		myswprintf(watchbuf, L"%ls%d", dataManager.GetSysString(1253), watching);
		mainGame->gMutex.lock();
		mainGame->stHostPrepOB->setText(watchbuf);
		mainGame->gMutex.unlock();
		break;
	}
	case STOC_TEAMMATE_SURRENDER: {
		if(!mainGame->dField.tag_surrender)
			mainGame->dField.tag_teammate_surrender = true;
		mainGame->btnLeaveGame->setText(dataManager.GetSysString(1355));
		break;
	}
	}
}
// Analyze STOC_GAME_MSG packet
/**
 * @brief 分析并处理来自服务器的游戏消息包
 *
 * 该函数负责解析从服务器接收到的STOC_GAME_MSG类型数据包，根据消息类型执行相应的游戏逻辑处理。
 * 主要功能包括：
 * 1. 读取消息类型并保存到游戏信息中
 * 2. 保存最后一次成功处理的消息用于错误恢复
 * 3. 隐藏当前显示的菜单界面
 * 4. 处理非重播模式下的界面清理工作
 * 5. 处理观战者视角切换
 * 6. 根据不同的消息类型分发到对应的处理逻辑
 *
 * @param msg 指向消息数据包的指针
 * @param len 消息数据包的长度
 * @return bool 处理成功返回true，失败返回false
 *
 * @note 该函数是游戏核心逻辑处理函数，负责处理所有游戏过程中服务器发送的消息
 * @note 对于MSG_RETRY消息会进行特殊处理，尝试恢复上一次成功的操作
 * @note 在处理过程中会根据需要锁定和解锁图形界面互斥锁以保证线程安全
 */
bool DuelClient::ClientAnalyze(unsigned char* msg, int len) {
	// 定义一个指针，用于遍历消息数据
	unsigned char* pbuf = msg;
	// 定义一个宽字符缓冲区，用于存储文本信息
	wchar_t textBuffer[256];
	// 从消息数据中读取当前消息类型，并存储到游戏信息结构中
	mainGame->dInfo.curMsg = BufferIO::Read<uint8_t>(pbuf);
	// 如果当前消息不是重试消息，则将当前消息保存为最后成功处理的消息
	if(mainGame->dInfo.curMsg != MSG_RETRY) {
		// 复制消息数据到last_successful_msg缓冲区
		std::memcpy(last_successful_msg, msg, len);
		// 记录消息长度
		last_successful_msg_length = len;
	}
	// 隐藏当前显示的游戏菜单
	mainGame->dField.HideMenu();
	// 如果不是回放模式，且当前消息不是等待或卡片选择消息
	if(!mainGame->dInfo.isReplay && mainGame->dInfo.curMsg != MSG_WAITING && mainGame->dInfo.curMsg != MSG_CARD_SELECTED) {
		// 重置等待帧计数器
		mainGame->waitFrame = -1;
		// 隐藏提示信息显示
		mainGame->stHintMsg->setVisible(false);
		// 如果卡片选择窗口当前可见
		if(mainGame->wCardSelect->isVisible()) {
			// 锁定图形界面互斥锁
			mainGame->gMutex.lock();
			// 隐藏卡片选择窗口
			mainGame->HideElement(mainGame->wCardSelect);
			// 解锁图形界面互斥锁
			mainGame->gMutex.unlock();
			// 等待11帧确保界面操作完成
			mainGame->WaitFrameSignal(11);
		}
		// 如果选项窗口当前可见
		if(mainGame->wOptions->isVisible()) {
			// 锁定图形界面互斥锁
			mainGame->gMutex.lock();
			// 隐藏选项窗口
			mainGame->HideElement(mainGame->wOptions);
			// 解锁图形界面互斥锁
			mainGame->gMutex.unlock();
			// 等待11帧确保界面操作完成
			mainGame->WaitFrameSignal(11);
		}
	}
	// 如果当前计时玩家是玩家1，则将计时玩家设置为2(无)
	if(mainGame->dInfo.time_player == 1)
		mainGame->dInfo.time_player = 2;
	// 如果正在进行视角交换
	if(is_swapping) {
		// 锁定图形界面互斥锁
		mainGame->gMutex.lock();
		// 执行回放视角交换操作
		mainGame->dField.ReplaySwap();
		// 解锁图形界面互斥锁
		mainGame->gMutex.unlock();
		// 重置视角交换标志
		is_swapping = false;
	}
	// 根据消息类型分发到相应的处理函数
	switch(mainGame->dInfo.curMsg) {
	case MSG_RETRY: {
		if(last_successful_msg_length) {
			auto p = last_successful_msg;
			auto last_msg = BufferIO::Read<uint8_t>(p);
			int err_desc = 1421;
			switch(last_msg) {
			case MSG_ANNOUNCE_CARD:
				err_desc = 1422;
				break;
			case MSG_ANNOUNCE_ATTRIB:
				err_desc = 1423;
				break;
			case MSG_ANNOUNCE_RACE:
				err_desc = 1424;
				break;
			case MSG_ANNOUNCE_NUMBER:
				err_desc = 1425;
				break;
			case MSG_SELECT_EFFECTYN:
			case MSG_SELECT_YESNO:
			case MSG_SELECT_OPTION:
				err_desc = 1426;
				break;
			case MSG_SELECT_CARD:
			case MSG_SELECT_UNSELECT_CARD:
			case MSG_SELECT_TRIBUTE:
			case MSG_SELECT_SUM:
			case MSG_SORT_CARD:
				err_desc = 1427;
				break;
			case MSG_SELECT_CHAIN:
				err_desc = 1428;
				break;
			case MSG_SELECT_PLACE:
			case MSG_SELECT_DISFIELD:
				err_desc = 1429;
				break;
			case MSG_SELECT_POSITION:
				err_desc = 1430;
				break;
			case MSG_SELECT_COUNTER:
				err_desc = 1431;
				break;
			default:
				break;
			}
			mainGame->gMutex.lock();
			mainGame->stMessage->setText(dataManager.GetDesc(err_desc));
			mainGame->PopupElement(mainGame->wMessage);
			mainGame->gMutex.unlock();
			mainGame->actionSignal.Reset();
			mainGame->actionSignal.Wait();
			select_hint = last_select_hint;
			return ClientAnalyze(last_successful_msg, last_successful_msg_length);
		}
		mainGame->gMutex.lock();
		mainGame->stMessage->setText(L"Error occurs.");
		mainGame->PopupElement(mainGame->wMessage);
		mainGame->gMutex.unlock();
		mainGame->actionSignal.Reset();
		mainGame->actionSignal.Wait();
		if(!mainGame->dInfo.isSingleMode) {
			mainGame->closeDoneSignal.Reset();
			mainGame->closeSignal.Set();
			mainGame->closeDoneSignal.Wait();
			mainGame->gMutex.lock();
			mainGame->dInfo.isStarted = false;
			mainGame->dInfo.isInDuel = false;
			mainGame->dInfo.isFinished = false;
			mainGame->btnCreateHost->setEnabled(true);
			mainGame->btnJoinHost->setEnabled(true);
			mainGame->btnJoinCancel->setEnabled(true);
			mainGame->btnStartBot->setEnabled(true);
			mainGame->btnBotCancel->setEnabled(true);
			mainGame->stTip->setVisible(false);
			mainGame->device->setEventReceiver(&mainGame->menuHandler);
			if(bot_mode)
				mainGame->ShowElement(mainGame->wSinglePlay);
			else
				mainGame->ShowElement(mainGame->wLanWindow);
			mainGame->gMutex.unlock();
			event_base_loopbreak(client_base);
			if(exit_on_return)
				mainGame->OnGameClose();
		}
		return false;
	}
	case MSG_HINT: {
		int type = BufferIO::Read<uint8_t>(pbuf);
		int player = BufferIO::Read<uint8_t>(pbuf);
		int data = BufferIO::Read<int32_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		switch (type) {
		case HINT_EVENT: {
			myswprintf(event_string, L"%ls", dataManager.GetDesc(data));
			break;
		}
		case HINT_MESSAGE: {
			mainGame->gMutex.lock();
			mainGame->stMessage->setText(dataManager.GetDesc(data));
			mainGame->PopupElement(mainGame->wMessage);
			mainGame->gMutex.unlock();
			mainGame->actionSignal.Reset();
			mainGame->actionSignal.Wait();
			break;
		}
		case HINT_SELECTMSG: {
			select_hint = data;
			last_select_hint = data;
			break;
		}
		case HINT_OPSELECTED: {
			myswprintf(textBuffer, dataManager.GetSysString(1510), dataManager.GetDesc(data));
			mainGame->AddLog(textBuffer);
			mainGame->gMutex.lock();
			mainGame->SetStaticText(mainGame->stACMessage, 310 * mainGame->xScale, mainGame->guiFont, textBuffer);
			mainGame->PopupElement(mainGame->wACMessage, 20);
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(40);
			break;
		}
		case HINT_EFFECT: {
			mainGame->showcardcode = data;
			mainGame->showcarddif = 0;
			mainGame->showcard = 1;
			mainGame->WaitFrameSignal(30);
			break;
		}
		case HINT_RACE: {
			const auto& race = dataManager.FormatRace(data);
			myswprintf(textBuffer, dataManager.GetSysString(1511), race.c_str());
			mainGame->AddLog(textBuffer);
			mainGame->gMutex.lock();
			mainGame->SetStaticText(mainGame->stACMessage, 310 * mainGame->xScale, mainGame->guiFont, textBuffer);
			mainGame->PopupElement(mainGame->wACMessage, 20);
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(40);
			break;
		}
		case HINT_ATTRIB: {
			const auto& attribute = dataManager.FormatAttribute(data);
			myswprintf(textBuffer, dataManager.GetSysString(1511), attribute.c_str());
			mainGame->AddLog(textBuffer);
			mainGame->gMutex.lock();
			mainGame->SetStaticText(mainGame->stACMessage, 310 * mainGame->xScale, mainGame->guiFont, textBuffer);
			mainGame->PopupElement(mainGame->wACMessage, 20);
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(40);
			break;
		}
		case HINT_CODE: {
			myswprintf(textBuffer, dataManager.GetSysString(1511), dataManager.GetName(data));
			mainGame->AddLog(textBuffer, data);
			mainGame->gMutex.lock();
			mainGame->SetStaticText(mainGame->stACMessage, 310 * mainGame->xScale, mainGame->guiFont, textBuffer);
			mainGame->PopupElement(mainGame->wACMessage, 20);
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(40);
			break;
		}
		case HINT_NUMBER: {
			myswprintf(textBuffer, dataManager.GetSysString(1512), data);
			mainGame->AddLog(textBuffer);
			mainGame->gMutex.lock();
			mainGame->SetStaticText(mainGame->stACMessage, 310, mainGame->guiFont, textBuffer);
			mainGame->PopupElement(mainGame->wACMessage, 20);
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(40);
			break;
		}
		case HINT_CARD: {
			mainGame->showcardcode = data;
			mainGame->showcarddif = 0;
			mainGame->showcard = 1;
			mainGame->WaitFrameSignal(30);
			break;
		}
		case HINT_ZONE: {
			if(mainGame->LocalPlayer(player) == 1)
				data = (data >> 16) | (data << 16);
			for(unsigned filter = 0x1; filter != 0; filter <<= 1) {
				std::wstring str;
				if(unsigned s = filter & data) {
					if(s & 0x60) {
						str += dataManager.GetSysString(1081);
						data &= ~0x600000;
					} else if(s & 0xffff)
						str += dataManager.GetSysString(102);
					else if(s & 0xffff0000) {
						str += dataManager.GetSysString(103);
						s >>= 16;
					}
					if(s & 0x1f)
						str += dataManager.GetSysString(1002);
					else if(s & 0xff00) {
						s >>= 8;
						if(s & 0x1f)
							str += dataManager.GetSysString(1003);
						else if(s & 0x20)
							str += dataManager.GetSysString(1008);
						else if(s & 0xc0)
							str += dataManager.GetSysString(1009);
					}
					int seq = 1;
					for(int i = 0x1; i < 0x100; i <<= 1) {
						if(s & i)
							break;
						++seq;
					}
					str += L"(" + std::to_wstring(seq) + L")";
					myswprintf(textBuffer, dataManager.GetSysString(1510), str.c_str());
					mainGame->AddLog(textBuffer);
				}
			}
			mainGame->dField.selectable_field = data;
			mainGame->WaitFrameSignal(40);
			mainGame->dField.selectable_field = 0;
			break;
		}
		}
		break;
	}
	case MSG_WIN: {
		mainGame->dInfo.isFinished = true;
		int player = BufferIO::Read<uint8_t>(pbuf);
		int type = BufferIO::Read<uint8_t>(pbuf);
		mainGame->showcarddif = 110;
		mainGame->showcardp = 0;
		mainGame->dInfo.vic_string = L"";
		wchar_t vic_buf[256];
		if(player == 2)
			mainGame->showcardcode = 3;
		else {
			wchar_t vic_name[20];
			if(mainGame->LocalPlayer(player) == 0) {
				mainGame->showcardcode = 1;
				myswprintf(vic_name, L"%ls", mainGame->dInfo.clientname);
			}
			else {
				mainGame->showcardcode = 2;
				myswprintf(vic_name, L"%ls", mainGame->dInfo.hostname);
			}
			if(mainGame->gameConf.hide_player_name)
				myswprintf(vic_name, L"********");
			if(match_kill)
				myswprintf(vic_buf, dataManager.GetVictoryString(0xffff), dataManager.GetName(match_kill));
			else if(type < 0x10)
				myswprintf(vic_buf, L"[%ls] %ls", vic_name, dataManager.GetVictoryString(type));
			else
				myswprintf(vic_buf, L"%ls", dataManager.GetVictoryString(type));
			mainGame->dInfo.vic_string = vic_buf;
		}
		mainGame->showcard = 101;
		mainGame->WaitFrameSignal(120);
		mainGame->dInfo.vic_string = L"";
		mainGame->showcard = 0;
		break;
	}
	case MSG_WAITING: {
		mainGame->waitFrame = 0;
		mainGame->gMutex.lock();
		mainGame->stHintMsg->setText(dataManager.GetSysString(1390));
		mainGame->stHintMsg->setVisible(true);
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_START: {
		mainGame->showcardcode = 11;
		mainGame->showcarddif = 30;
		mainGame->showcardp = 0;
		mainGame->showcard = 101;
		mainGame->WaitFrameSignal(40);
		mainGame->showcard = 0;
		mainGame->gMutex.lock();
		mainGame->dField.Clear();
		mainGame->dInfo.isInDuel = true;
		int playertype = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dInfo.isFirst =  (playertype & 0xf) ? false : true;
		if(playertype & 0xf0)
			mainGame->dInfo.player_type = 7;
		if(mainGame->dInfo.isTag) {
			if(mainGame->dInfo.isFirst)
				mainGame->dInfo.tag_player[1] = true;
			else
				mainGame->dInfo.tag_player[0] = true;
		}
		mainGame->dInfo.duel_rule = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dInfo.lp[mainGame->LocalPlayer(0)] = BufferIO::Read<int32_t>(pbuf);
		mainGame->dInfo.lp[mainGame->LocalPlayer(1)] = BufferIO::Read<int32_t>(pbuf);
		myswprintf(mainGame->dInfo.strLP[0], L"%d", mainGame->dInfo.lp[0]);
		myswprintf(mainGame->dInfo.strLP[1], L"%d", mainGame->dInfo.lp[1]);
		int deckc = BufferIO::Read<uint16_t>(pbuf);
		int extrac = BufferIO::Read<uint16_t>(pbuf);
		mainGame->dField.Initial(mainGame->LocalPlayer(0), deckc, extrac);
		deckc = BufferIO::Read<uint16_t>(pbuf);
		extrac = BufferIO::Read<uint16_t>(pbuf);
		mainGame->dField.Initial(mainGame->LocalPlayer(1), deckc, extrac);
		mainGame->dInfo.turn = 0;
		mainGame->dInfo.is_shuffling = false;
		select_hint = 0;
		select_unselect_hint = 0;
		last_select_hint = 0;
		last_successful_msg_length = 0;
		if(mainGame->dInfo.isReplaySwapped) {
			std::swap(mainGame->dInfo.hostname, mainGame->dInfo.clientname);
			std::swap(mainGame->dInfo.hostname_tag, mainGame->dInfo.clientname_tag);
			mainGame->dInfo.isReplaySwapped = false;
			mainGame->dField.ReplaySwap();
		}
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_UPDATE_DATA: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int location = BufferIO::Read<uint8_t>(pbuf);
		mainGame->gMutex.lock();
		mainGame->dField.UpdateFieldCard(player, location, pbuf);
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_UPDATE_CARD: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int loc = BufferIO::Read<uint8_t>(pbuf);
		int seq = BufferIO::Read<uint8_t>(pbuf);
		mainGame->gMutex.lock();
		mainGame->dField.UpdateCard(player, loc, seq, pbuf);
		mainGame->gMutex.unlock();
		break;
	}
	case MSG_SELECT_BATTLECMD: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		int desc, count, con, seq/*, diratt*/;
		unsigned int code, loc;
		ClientCard* pcard;
		mainGame->dField.activatable_cards.clear();
		mainGame->dField.activatable_descs.clear();
		mainGame->dField.conti_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			desc = BufferIO::Read<int32_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			int flag = 0;
			if(code & 0x80000000) {
				flag = EDESC_OPERATION;
				code &= 0x7fffffff;
			}
			mainGame->dField.activatable_cards.push_back(pcard);
			mainGame->dField.activatable_descs.push_back(std::make_pair(desc, flag));
			if(flag & EDESC_OPERATION) {
				pcard->chain_code = code;
				mainGame->dField.conti_cards.push_back(pcard);
				mainGame->dField.conti_act = true;
			} else {
				pcard->cmdFlag |= COMMAND_ACTIVATE;
				if(pcard->location == LOCATION_GRAVE)
					mainGame->dField.grave_act[con] = true;
				else if(pcard->location == LOCATION_REMOVED)
					mainGame->dField.remove_act[con] = true;
				else if(pcard->location == LOCATION_EXTRA)
					mainGame->dField.extra_act[con] = true;
			}
		}
		mainGame->dField.attackable_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			/*code = */BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			/*diratt = */BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			mainGame->dField.attackable_cards.push_back(pcard);
			pcard->cmdFlag |= COMMAND_ATTACK;
		}
		mainGame->dField.RefreshCardCountDisplay();
		mainGame->gMutex.lock();
		if(BufferIO::Read<uint8_t>(pbuf)) {
			mainGame->btnM2->setVisible(true);
			mainGame->btnM2->setEnabled(true);
			mainGame->btnM2->setPressed(false);
		}
		if(BufferIO::Read<uint8_t>(pbuf)) {
			mainGame->btnEP->setVisible(true);
			mainGame->btnEP->setEnabled(true);
			mainGame->btnEP->setPressed(false);
		}
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_SELECT_IDLECMD: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		int desc, count, con, seq;
		unsigned int code, loc;
		ClientCard* pcard;
		mainGame->dField.summonable_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			mainGame->dField.summonable_cards.push_back(pcard);
			pcard->cmdFlag |= COMMAND_SUMMON;
		}
		mainGame->dField.spsummonable_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			mainGame->dField.spsummonable_cards.push_back(pcard);
			pcard->cmdFlag |= COMMAND_SPSUMMON;
			if (pcard->location == LOCATION_DECK) {
				pcard->SetCode(code);
				mainGame->dField.deck_act[con] = true;
			} else if (pcard->location == LOCATION_GRAVE)
				mainGame->dField.grave_act[con] = true;
			else if (pcard->location == LOCATION_REMOVED)
				mainGame->dField.remove_act[con] = true;
			else if (pcard->location == LOCATION_EXTRA)
				mainGame->dField.extra_act[con] = true;
			else {
				int left_seq = mainGame->dInfo.duel_rule >= 4 ? 0 : 6;
				if (pcard->location == LOCATION_SZONE && pcard->sequence == left_seq && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget)
					mainGame->dField.pzone_act[con] = true;
			}
		}
		mainGame->dField.reposable_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			mainGame->dField.reposable_cards.push_back(pcard);
			pcard->cmdFlag |= COMMAND_REPOS;
		}
		mainGame->dField.msetable_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			mainGame->dField.msetable_cards.push_back(pcard);
			pcard->cmdFlag |= COMMAND_MSET;
		}
		mainGame->dField.ssetable_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			mainGame->dField.ssetable_cards.push_back(pcard);
			pcard->cmdFlag |= COMMAND_SSET;
		}
		mainGame->dField.activatable_cards.clear();
		mainGame->dField.activatable_descs.clear();
		mainGame->dField.conti_cards.clear();
		count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			con = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			loc = BufferIO::Read<uint8_t>(pbuf);
			seq = BufferIO::Read<uint8_t>(pbuf);
			desc = BufferIO::Read<int32_t>(pbuf);
			pcard = mainGame->dField.GetCard(con, loc, seq);
			int flag = 0;
			if(code & 0x80000000) {
				flag = EDESC_OPERATION;
				code &= 0x7fffffff;
			}
			mainGame->dField.activatable_cards.push_back(pcard);
			mainGame->dField.activatable_descs.push_back(std::make_pair(desc, flag));
			if(flag & EDESC_OPERATION) {
				pcard->chain_code = code;
				mainGame->dField.conti_cards.push_back(pcard);
				mainGame->dField.conti_act = true;
			} else {
				pcard->cmdFlag |= COMMAND_ACTIVATE;
				if(pcard->location == LOCATION_GRAVE)
					mainGame->dField.grave_act[con] = true;
				else if(pcard->location == LOCATION_REMOVED)
					mainGame->dField.remove_act[con] = true;
				else if(pcard->location == LOCATION_EXTRA)
					mainGame->dField.extra_act[con] = true;
			}
		}
		if(BufferIO::Read<uint8_t>(pbuf)) {
			mainGame->btnBP->setVisible(true);
			mainGame->btnBP->setEnabled(true);
			mainGame->btnBP->setPressed(false);
		}
		if(BufferIO::Read<uint8_t>(pbuf)) {
			mainGame->btnEP->setVisible(true);
			mainGame->btnEP->setEnabled(true);
			mainGame->btnEP->setPressed(false);
		}
		if (BufferIO::Read<uint8_t>(pbuf)) {
			mainGame->btnShuffle->setVisible(true);
		} else {
			mainGame->btnShuffle->setVisible(false);
		}
		return false;
	}
	case MSG_SELECT_EFFECTYN: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l = BufferIO::Read<uint8_t>(pbuf);
		int s = BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pcard = mainGame->dField.GetCard(c, l, s);
		if (pcard->code != code)
			pcard->SetCode(code);
		BufferIO::Read<uint8_t>(pbuf);
		if(l != LOCATION_DECK) {
			pcard->is_highlighting = true;
			mainGame->dField.highlighting_card = pcard;
		}
		int desc = BufferIO::Read<int32_t>(pbuf);
		if(desc == 0) {
			wchar_t ynbuf[256];
			myswprintf(ynbuf, dataManager.GetSysString(200), dataManager.FormatLocation(l, s), dataManager.GetName(code));
			myswprintf(textBuffer, L"%ls\n%ls", event_string, ynbuf);
		} else if(desc == 221) {
			wchar_t ynbuf[256];
			myswprintf(ynbuf, dataManager.GetSysString(221), dataManager.FormatLocation(l, s), dataManager.GetName(code));
			myswprintf(textBuffer, L"%ls\n%ls\n%ls", event_string, ynbuf, dataManager.GetSysString(223));
		} else if(desc <= MAX_STRING_ID) {
			myswprintf(textBuffer, dataManager.GetSysString(desc), dataManager.GetName(code));
		} else {
			myswprintf(textBuffer, dataManager.GetDesc(desc), dataManager.GetName(code));
		}
		mainGame->gMutex.lock();
		mainGame->SetStaticText(mainGame->stQMessage, 350 * mainGame->xScale, mainGame->guiFont, textBuffer);
		mainGame->PopupElement(mainGame->wQuery);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_SELECT_YESNO: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		int desc = BufferIO::Read<int32_t>(pbuf);
		mainGame->dField.highlighting_card = 0;
		mainGame->gMutex.lock();
		mainGame->SetStaticText(mainGame->stQMessage, 350 * mainGame->xScale, mainGame->guiFont, dataManager.GetDesc(desc));
		mainGame->PopupElement(mainGame->wQuery);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_SELECT_OPTION: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_options.clear();
		for (int i = 0; i < count; ++i)
			mainGame->dField.select_options.push_back(BufferIO::Read<int32_t>(pbuf));
		mainGame->dField.ShowSelectOption(select_hint);
		select_hint = 0;
		return false;
	}
	case MSG_SELECT_CARD: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_cancelable = BufferIO::Read<uint8_t>(pbuf) != 0;
		mainGame->dField.select_min = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_max = BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.selectable_cards.clear();
		mainGame->dField.selected_cards.clear();
		int c, s, ss;
		unsigned int l;
		unsigned int code;
		bool panelmode = false;
		size_t hand_count[2] = { mainGame->dField.hand[0].size(), mainGame->dField.hand[1].size() };
		int select_count_in_hand[2] = { 0, 0 };
		bool select_ready = mainGame->dField.select_min == 0;
		mainGame->dField.select_ready = select_ready;
		ClientCard* pcard;
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			ss = BufferIO::Read<uint8_t>(pbuf);
			if (l & LOCATION_OVERLAY)
				pcard = mainGame->dField.GetCard(c, l & 0x7f, s)->overlayed[ss];
			else
				pcard = mainGame->dField.GetCard(c, l, s);
			if (code != 0 && pcard->code != code)
				pcard->SetCode(code);
			pcard->select_seq = i;
			mainGame->dField.selectable_cards.push_back(pcard);
			pcard->is_selectable = true;
			pcard->is_selected = false;
			if (l & 0xf1)
				panelmode = true;
			if((l & LOCATION_HAND) && hand_count[c] >= 10) {
				if(++select_count_in_hand[c] > 1)
					panelmode = true;
			}
		}
		std::sort(mainGame->dField.selectable_cards.begin(), mainGame->dField.selectable_cards.end(), ClientCard::client_card_sort);
		if(select_hint)
			myswprintf(textBuffer, L"%ls(%d-%d)", dataManager.GetDesc(select_hint),
			           mainGame->dField.select_min, mainGame->dField.select_max);
		else myswprintf(textBuffer, L"%ls(%d-%d)", dataManager.GetSysString(560), mainGame->dField.select_min, mainGame->dField.select_max);
		select_hint = 0;
		if (panelmode) {
			mainGame->gMutex.lock();
			mainGame->stCardSelect->setText(textBuffer);
			mainGame->dField.ShowSelectCard(select_ready);
			mainGame->gMutex.unlock();
		} else {
			mainGame->stHintMsg->setText(textBuffer);
			mainGame->stHintMsg->setVisible(true);
		}
		if (mainGame->dField.select_cancelable) {
			mainGame->dField.ShowCancelOrFinishButton(1);
		} else if (select_ready) {
			mainGame->dField.ShowCancelOrFinishButton(2);
		} else {
			mainGame->dField.ShowCancelOrFinishButton(0);
		}
		return false;
	}
	case MSG_SELECT_UNSELECT_CARD: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		bool finishable = BufferIO::Read<uint8_t>(pbuf) != 0;
		bool cancelable = BufferIO::Read<uint8_t>(pbuf) != 0;
		mainGame->dField.select_cancelable = finishable || cancelable;
		mainGame->dField.select_min = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_max = BufferIO::Read<uint8_t>(pbuf);
		int count1 = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.selectable_cards.clear();
		mainGame->dField.selected_cards.clear();
		int c, s, ss;
		unsigned int l;
		unsigned int code;
		bool panelmode = false;
		size_t hand_count[2] = { mainGame->dField.hand[0].size(), mainGame->dField.hand[1].size() };
		int select_count_in_hand[2] = { 0, 0 };
		mainGame->dField.select_ready = false;
		ClientCard* pcard;
		for (int i = 0; i < count1; ++i) {
			code = (unsigned int)BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			ss = BufferIO::Read<uint8_t>(pbuf);
			if (l & LOCATION_OVERLAY)
				pcard = mainGame->dField.GetCard(c, l & 0x7f, s)->overlayed[ss];
			else
				pcard = mainGame->dField.GetCard(c, l, s);
			if (code != 0 && pcard->code != code)
				pcard->SetCode(code);
			pcard->select_seq = i;
			mainGame->dField.selectable_cards.push_back(pcard);
			pcard->is_selectable = true;
			pcard->is_selected = false;
			if (l & 0xf1)
				panelmode = true;
			if((l & LOCATION_HAND) && hand_count[c] >= 10) {
				if(++select_count_in_hand[c] > 1)
					panelmode = true;
			}
		}
		int count2 = BufferIO::Read<uint8_t>(pbuf);
		for (int i = count1; i < count1 + count2; ++i) {
			code = (unsigned int)BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			ss = BufferIO::Read<uint8_t>(pbuf);
			if (l & LOCATION_OVERLAY)
				pcard = mainGame->dField.GetCard(c, l & 0x7f, s)->overlayed[ss];
			else
				pcard = mainGame->dField.GetCard(c, l, s);
			if (code != 0 && pcard->code != code)
				pcard->SetCode(code);
			pcard->select_seq = i;
			mainGame->dField.selectable_cards.push_back(pcard);
			pcard->is_selectable = true;
			pcard->is_selected = true;
			if (l & 0xf1)
				panelmode = true;
			if((l & LOCATION_HAND) && hand_count[c] >= 10) {
				if(++select_count_in_hand[c] > 1)
					panelmode = true;
			}
		}
		std::sort(mainGame->dField.selectable_cards.begin(), mainGame->dField.selectable_cards.end(), ClientCard::client_card_sort);
		if(select_hint)
			select_unselect_hint = select_hint;
		if(select_unselect_hint)
			myswprintf(textBuffer, L"%ls(%d-%d)", dataManager.GetDesc(select_unselect_hint),
			           mainGame->dField.select_min, mainGame->dField.select_max);
		else myswprintf(textBuffer, L"%ls(%d-%d)", dataManager.GetSysString(560), mainGame->dField.select_min, mainGame->dField.select_max);
		select_hint = 0;
		if (panelmode) {
			mainGame->gMutex.lock();
			mainGame->stCardSelect->setText(textBuffer);
			mainGame->dField.ShowSelectCard(mainGame->dField.select_cancelable);
			mainGame->gMutex.unlock();
		} else {
			mainGame->stHintMsg->setText(textBuffer);
			mainGame->stHintMsg->setVisible(true);
		}
		if (mainGame->dField.select_cancelable) {
			if(finishable) {
				mainGame->dField.select_ready = true;
				mainGame->dField.ShowCancelOrFinishButton(2);
			}
			else {
				mainGame->dField.ShowCancelOrFinishButton(1);
			}
		}
		else
			mainGame->dField.ShowCancelOrFinishButton(0);
		return false;
	}
	case MSG_SELECT_CHAIN: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		int specount = BufferIO::Read<uint8_t>(pbuf);
		/*int hint0 = */BufferIO::Read<int32_t>(pbuf);
		/*int hint1 = */BufferIO::Read<int32_t>(pbuf);
		int c, s, ss, desc;
		unsigned int code,l;
		ClientCard* pcard;
		bool panelmode = false;
		bool conti_exist = false;
		bool select_trigger = (specount == 0x7f);
		mainGame->dField.chain_forced = false;
		mainGame->dField.activatable_cards.clear();
		mainGame->dField.activatable_descs.clear();
		mainGame->dField.conti_cards.clear();
		for (int i = 0; i < count; ++i) {
			int flag = BufferIO::Read<uint8_t>(pbuf);
			int forced = BufferIO::Read<uint8_t>(pbuf);
			flag |= forced << 8;
			code = BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			ss = BufferIO::Read<uint8_t>(pbuf);
			desc = BufferIO::Read<int32_t>(pbuf);
			pcard = mainGame->dField.GetCard(c, l, s, ss);
			mainGame->dField.activatable_cards.push_back(pcard);
			mainGame->dField.activatable_descs.push_back(std::make_pair(desc, flag));
			pcard->is_selected = false;
			if(forced) {
				mainGame->dField.chain_forced = true;
			}
			if(flag & EDESC_OPERATION) {
				pcard->chain_code = code;
				mainGame->dField.conti_cards.push_back(pcard);
				mainGame->dField.conti_act = true;
				conti_exist = true;
			} else {
				pcard->is_selectable = true;
				if(flag & EDESC_RESET)
					pcard->cmdFlag |= COMMAND_RESET;
				else
					pcard->cmdFlag |= COMMAND_ACTIVATE;
				if(pcard->location == LOCATION_DECK) {
					pcard->SetCode(code);
					mainGame->dField.deck_act[c] = true;
				} else if(l == LOCATION_GRAVE)
					mainGame->dField.grave_act[c] = true;
				else if(l == LOCATION_REMOVED)
					mainGame->dField.remove_act[c] = true;
				else if(l == LOCATION_EXTRA)
					mainGame->dField.extra_act[c] = true;
				else if(l == LOCATION_OVERLAY)
					panelmode = true;
			}
		}
		if(!select_trigger && !mainGame->dField.chain_forced && (mainGame->ignore_chain || ((count == 0 || specount == 0) && !mainGame->always_chain)) && (count == 0 || !mainGame->chain_when_avail)) {
			SetResponseI(-1);
			mainGame->dField.ClearChainSelect();
			if(mainGame->chkWaitChain->isChecked() && !mainGame->ignore_chain) {
				mainGame->WaitFrameSignal(std::uniform_int_distribution<>(20, 40)(rnd));
			}
			DuelClient::SendResponse();
			return true;
		}
		if(mainGame->chkAutoChain->isChecked() && mainGame->dField.chain_forced && !(mainGame->always_chain || mainGame->chain_when_avail)) {
			for(size_t i = 0; i < mainGame->dField.activatable_descs.size();++i) {
				auto it = mainGame->dField.activatable_descs[i];
				if(it.second >> 8) {
					SetResponseI((int)i);
					break;
				}
			}
			mainGame->dField.ClearChainSelect();
			DuelClient::SendResponse();
			return true;
		}
		mainGame->gMutex.lock();
		if(!conti_exist)
			mainGame->stHintMsg->setText(dataManager.GetSysString(550));
		else
			mainGame->stHintMsg->setText(dataManager.GetSysString(556));
		mainGame->stHintMsg->setVisible(true);
		if(panelmode) {
			mainGame->dField.list_command = COMMAND_ACTIVATE;
			mainGame->dField.selectable_cards = mainGame->dField.activatable_cards;
			std::sort(mainGame->dField.selectable_cards.begin(), mainGame->dField.selectable_cards.end());
			auto eit = std::unique(mainGame->dField.selectable_cards.begin(), mainGame->dField.selectable_cards.end());
			mainGame->dField.selectable_cards.erase(eit, mainGame->dField.selectable_cards.end());
			mainGame->dField.ShowChainCard();
		} else {
			if(!mainGame->dField.chain_forced) {
				if(count == 0)
					myswprintf(textBuffer, L"%ls\n%ls", dataManager.GetSysString(201), dataManager.GetSysString(202));
				else if(select_trigger)
					myswprintf(textBuffer, L"%ls\n%ls\n%ls", event_string, dataManager.GetSysString(222), dataManager.GetSysString(223));
				else
					myswprintf(textBuffer, L"%ls\n%ls", event_string, dataManager.GetSysString(203));
				mainGame->SetStaticText(mainGame->stQMessage, 350 * mainGame->xScale, mainGame->guiFont, textBuffer);
				mainGame->PopupElement(mainGame->wQuery);
			}
		}
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_SELECT_PLACE:
	case MSG_SELECT_DISFIELD: {
		int selecting_player = BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_min = count > 0 ? count : 1;
		mainGame->dField.select_ready = false;
		mainGame->dField.select_cancelable = count == 0;
		mainGame->dField.selectable_field = ~BufferIO::Read<int32_t>(pbuf);
		if(selecting_player == mainGame->LocalPlayer(1))
			mainGame->dField.selectable_field = (mainGame->dField.selectable_field >> 16) | (mainGame->dField.selectable_field << 16);
		mainGame->dField.selected_field = 0;
		unsigned char respbuf[SIZE_RETURN_VALUE];
		int pzone = 0;
		if (mainGame->dInfo.curMsg == MSG_SELECT_PLACE) {
			if (select_hint) {
				myswprintf(textBuffer, dataManager.GetSysString(569), dataManager.GetName(select_hint));
			} else
				myswprintf(textBuffer, dataManager.GetSysString(560));
		} else {
			if (select_hint) {
				myswprintf(textBuffer, dataManager.GetDesc(select_hint));
			} else
				myswprintf(textBuffer, dataManager.GetSysString(570));
		}
		select_hint = 0;
		mainGame->stHintMsg->setText(textBuffer);
		mainGame->stHintMsg->setVisible(true);
		if (mainGame->dInfo.curMsg == MSG_SELECT_PLACE && (
			(mainGame->chkMAutoPos->isChecked() && mainGame->dField.selectable_field & 0x7f007f) ||
			(mainGame->chkSTAutoPos->isChecked() && !(mainGame->dField.selectable_field & 0x7f007f)))) {
			unsigned int filter;
			if (mainGame->dField.selectable_field & 0x7f) {
				respbuf[0] = mainGame->LocalPlayer(0);
				respbuf[1] = LOCATION_MZONE;
				filter = mainGame->dField.selectable_field & 0x7f;
			} else if (mainGame->dField.selectable_field & 0x3f00) {
				respbuf[0] = mainGame->LocalPlayer(0);
				respbuf[1] = LOCATION_SZONE;
				filter = (mainGame->dField.selectable_field >> 8) & 0x3f;
			} else if (mainGame->dField.selectable_field & 0xc000) {
				respbuf[0] = mainGame->LocalPlayer(0);
				respbuf[1] = LOCATION_SZONE;
				filter = (mainGame->dField.selectable_field >> 14) & 0x3;
				pzone = 1;
			} else if (mainGame->dField.selectable_field & 0x7f0000) {
				respbuf[0] = mainGame->LocalPlayer(1);
				respbuf[1] = LOCATION_MZONE;
				filter = (mainGame->dField.selectable_field >> 16) & 0x7f;
			} else if (mainGame->dField.selectable_field & 0x3f000000) {
				respbuf[0] = mainGame->LocalPlayer(1);
				respbuf[1] = LOCATION_SZONE;
				filter = (mainGame->dField.selectable_field >> 24) & 0x3f;
			} else {
				respbuf[0] = mainGame->LocalPlayer(1);
				respbuf[1] = LOCATION_SZONE;
				filter = (mainGame->dField.selectable_field >> 30) & 0x3;
				pzone = 1;
			}
			if(!pzone) {
				if(mainGame->chkRandomPos->isChecked()) {
					std::uniform_int_distribution<> dist(0, 6);
					do {
						respbuf[2] = dist(rnd);
					} while(!(filter & (0x1U << respbuf[2])));
				} else {
					if (filter & 0x40) respbuf[2] = 6;
					else if (filter & 0x20) respbuf[2] = 5;
					else if (filter & 0x4) respbuf[2] = 2;
					else if (filter & 0x2) respbuf[2] = 1;
					else if (filter & 0x8) respbuf[2] = 3;
					else if (filter & 0x1) respbuf[2] = 0;
					else if (filter & 0x10) respbuf[2] = 4;
				}
			} else {
				if (filter & 0x1) respbuf[2] = 6;
				else if (filter & 0x2) respbuf[2] = 7;
			}
			mainGame->dField.selectable_field = 0;
			SetResponseB(respbuf, 3);
			DuelClient::SendResponse();
			return true;
		}
		if(mainGame->dField.select_cancelable) {
			mainGame->dField.ShowCancelOrFinishButton(1);
		}
		return false;
	}
	case MSG_SELECT_POSITION: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		unsigned int code = (unsigned int)BufferIO::Read<int32_t>(pbuf);
		unsigned int positions = BufferIO::Read<uint8_t>(pbuf);
		if (positions == 0x1 || positions == 0x2 || positions == 0x4 || positions == 0x8) {
			SetResponseI(positions);
			return true;
		}
		int count = 0, filter = 0x1, startpos = 30;
		while(filter != 0x10) {
			if(positions & filter) count++;
			filter <<= 1;
		}
		if(positions & 0x1) {
			mainGame->imageLoading.insert(std::make_pair(mainGame->btnPSAU, code));
			mainGame->btnPSAU->setRelativePosition(mainGame->Resize_Y(startpos, 30, startpos + 168, 30 + 168));
			mainGame->btnPSAU->setVisible(true);
			startpos += 178;
		} else mainGame->btnPSAU->setVisible(false);
		if(positions & 0x2) {
			mainGame->btnPSAD->setRelativePosition(mainGame->Resize_Y(startpos, 30, startpos + 168, 30 + 168));
			mainGame->btnPSAD->setVisible(true);
			startpos += 178;
		} else mainGame->btnPSAD->setVisible(false);
		if(positions & 0x4) {
			mainGame->imageLoading.insert(std::make_pair(mainGame->btnPSDU, code));
			mainGame->btnPSDU->setRelativePosition(mainGame->Resize_Y(startpos, 30, startpos + 168, 30 + 168));
			mainGame->btnPSDU->setVisible(true);
			startpos += 178;
		} else mainGame->btnPSDU->setVisible(false);
		if(positions & 0x8) {
			mainGame->btnPSDD->setRelativePosition(mainGame->Resize_Y(startpos, 30, startpos + 168, 30 + 168));
			mainGame->btnPSDD->setVisible(true);
			startpos += 178;
		} else mainGame->btnPSDD->setVisible(false);
		irr::core::recti pos = mainGame->wPosSelect->getRelativePosition();
		irr::s32 oldcenter = pos.getCenter().X;
		pos.LowerRightCorner.X = pos.UpperLeftCorner.X + (count * 178 + 50) * mainGame->yScale;
		irr::s32 newwidth = pos.getWidth();
		pos.UpperLeftCorner.X = oldcenter - newwidth / 2;
		pos.LowerRightCorner.X = oldcenter + newwidth / 2;
		mainGame->wPosSelect->setRelativePosition(pos);
		mainGame->bgPosSelect->setRelativePosition(irr::core::rect<irr::s32>(0, 0, pos.getWidth(), pos.getHeight()));
		mainGame->gMutex.lock();
		mainGame->PopupElement(mainGame->wPosSelect);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_SELECT_TRIBUTE: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_cancelable = BufferIO::Read<uint8_t>(pbuf) != 0;
		mainGame->dField.select_min = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_max = BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.selectable_cards.clear();
		mainGame->dField.selected_cards.clear();
		mainGame->dField.selectsum_all.clear();
		mainGame->dField.selectsum_cards.clear();
		mainGame->dField.select_panalmode = false;
		int c, s, t;
		unsigned int code, l;
		ClientCard* pcard;
		mainGame->dField.select_ready = false;
		for (int i = 0; i < count; ++i) {
			code = (unsigned int)BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			t = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(c, l, s);
			if (code && pcard->code != code)
				pcard->SetCode(code);
			mainGame->dField.selectable_cards.push_back(pcard);
			mainGame->dField.selectsum_all.push_back(pcard);
			pcard->opParam = t << 16 | 1;
			pcard->select_seq = i;
			pcard->is_selectable = true;
		}
		mainGame->dField.CheckSelectTribute();
		if(select_hint)
			myswprintf(textBuffer, L"%ls(%d-%d)", dataManager.GetDesc(select_hint),
			           mainGame->dField.select_min, mainGame->dField.select_max);
		else myswprintf(textBuffer, L"%ls(%d-%d)", dataManager.GetSysString(531), mainGame->dField.select_min, mainGame->dField.select_max);
		select_hint = 0;
		mainGame->gMutex.lock();
		mainGame->stHintMsg->setText(textBuffer);
		mainGame->stHintMsg->setVisible(true);
		if (mainGame->dField.select_cancelable) {
			mainGame->dField.ShowCancelOrFinishButton(1);
		}
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_SELECT_COUNTER: {
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_counter_type = BufferIO::Read<uint16_t>(pbuf);
		mainGame->dField.select_counter_count = BufferIO::Read<uint16_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.selectable_cards.clear();
		int c, s, t/*, code*/;
		unsigned int l;
		ClientCard* pcard;
		for (int i = 0; i < count; ++i) {
			/*code = */BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			t = BufferIO::Read<uint16_t>(pbuf);
			pcard = mainGame->dField.GetCard(c, l, s);
			mainGame->dField.selectable_cards.push_back(pcard);
			pcard->opParam = (t << 16) | t;
			pcard->is_selectable = true;
		}
		myswprintf(textBuffer, dataManager.GetSysString(204), mainGame->dField.select_counter_count, dataManager.GetCounterName(mainGame->dField.select_counter_type));
		mainGame->gMutex.lock();
		mainGame->stHintMsg->setText(textBuffer);
		mainGame->stHintMsg->setVisible(true);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_SELECT_SUM: {
		mainGame->dField.select_mode = BufferIO::Read<uint8_t>(pbuf);
		/*int selecting_player = */BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_sumval = BufferIO::Read<int32_t>(pbuf);
		mainGame->dField.select_min = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.select_max = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.must_select_count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.selectsum_all.clear();
		mainGame->dField.selected_cards.clear();
		mainGame->dField.selectsum_cards.clear();
		mainGame->dField.select_panalmode = false;
		for (int i = 0; i < mainGame->dField.must_select_count; ++i) {
			unsigned int code = (unsigned int)BufferIO::Read<int32_t>(pbuf);
			int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			unsigned int l = BufferIO::Read<uint8_t>(pbuf);
			int s = BufferIO::Read<uint8_t>(pbuf);
			ClientCard* pcard = mainGame->dField.GetCard(c, l, s);
			if (code != 0 && pcard->code != code)
				pcard->SetCode(code);
			pcard->opParam = BufferIO::Read<int32_t>(pbuf);
			pcard->select_seq = 0;
			mainGame->dField.selected_cards.push_back(pcard);
		}
		int count = BufferIO::Read<uint8_t>(pbuf);
		for (int i = 0; i < count; ++i) {
			unsigned int code = (unsigned int)BufferIO::Read<int32_t>(pbuf);
			int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			unsigned int l = BufferIO::Read<uint8_t>(pbuf);
			int s = BufferIO::Read<uint8_t>(pbuf);
			ClientCard* pcard = mainGame->dField.GetCard(c, l, s);
			if (code != 0 && pcard->code != code)
				pcard->SetCode(code);
			pcard->opParam = BufferIO::Read<int32_t>(pbuf);
			pcard->select_seq = i;
			mainGame->dField.selectsum_all.push_back(pcard);
			if ((l & 0xe) == 0)
				mainGame->dField.select_panalmode = true;
		}
		std::sort(mainGame->dField.selectsum_all.begin(), mainGame->dField.selectsum_all.end(), ClientCard::client_card_sort);
		mainGame->dField.select_hint = select_hint;
		select_hint = 0;
		return mainGame->dField.ShowSelectSum(mainGame->dField.select_panalmode);
	}
	case MSG_SORT_CARD: {
		/*int player = */BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.selectable_cards.clear();
		mainGame->dField.selected_cards.clear();
		mainGame->dField.sort_list.clear();
		int c, s;
		unsigned int code, l;
		ClientCard* pcard;
		for (int i = 0; i < count; ++i) {
			code = (unsigned int)BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(c, l, s);
			if (code != 0 && pcard->code != code)
				pcard->SetCode(code);
			mainGame->dField.selectable_cards.push_back(pcard);
			mainGame->dField.sort_list.push_back(0);
		}
		mainGame->stCardSelect->setText(dataManager.GetSysString(205));
		mainGame->dField.select_min = 0;
		mainGame->dField.select_max = count;
		mainGame->dField.ShowSelectCard();
		return false;
	}
	case MSG_CONFIRM_DECKTOP: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		unsigned int code;
		ClientCard* pcard;
		mainGame->dField.selectable_cards.clear();
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			pbuf += 3;
			pcard = *(mainGame->dField.deck[player].rbegin() + i);
			if (code != 0)
				pcard->SetCode(code);
		}
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::REVEAL);
		myswprintf(textBuffer, dataManager.GetSysString(207), count);
		mainGame->AddLog(textBuffer);
		for (int i = 0; i < count; ++i) {
			pcard = *(mainGame->dField.deck[player].rbegin() + i);
			mainGame->gMutex.lock();
			myswprintf(textBuffer, L"*[%ls]", dataManager.GetName(pcard->code));
			mainGame->AddLog(textBuffer, pcard->code);
			mainGame->gMutex.unlock();
			float shift = -0.15f;
			if (player == 1) shift = 0.15f;
			pcard->dPos = irr::core::vector3df(shift, 0, 0);
			if(!mainGame->dField.deck_reversed)
				pcard->dRot = irr::core::vector3df(0, 3.14159f / 5.0f, 0);
			else pcard->dRot = irr::core::vector3df(0, 0, 0);
			pcard->is_moving = true;
			pcard->aniFrame = 5;
			mainGame->WaitFrameSignal(count > 5 ? 12 : 45);
			mainGame->dField.MoveCard(pcard, 5);
			mainGame->WaitFrameSignal(5);
		}
		return true;
	}
	case MSG_CONFIRM_EXTRATOP: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		unsigned int code;
		ClientCard* pcard;
		mainGame->dField.selectable_cards.clear();
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			pbuf += 3;
			pcard = *(mainGame->dField.extra[player].rbegin() + i + mainGame->dField.extra_p_count[player]);
			if (code != 0)
				pcard->SetCode(code);
		}
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::REVEAL);
		myswprintf(textBuffer, dataManager.GetSysString(207), count);
		mainGame->AddLog(textBuffer);
		for (int i = 0; i < count; ++i) {
			pcard = *(mainGame->dField.extra[player].rbegin() + i + mainGame->dField.extra_p_count[player]);
			mainGame->gMutex.lock();
			myswprintf(textBuffer, L"*[%ls]", dataManager.GetName(pcard->code));
			mainGame->AddLog(textBuffer, pcard->code);
			mainGame->gMutex.unlock();
			if (player == 0)
				pcard->dPos = irr::core::vector3df(0, -0.20f, 0);
			else
				pcard->dPos = irr::core::vector3df(0.15f, 0, 0);
			pcard->dRot = irr::core::vector3df(0, 3.14159f / 5.0f, 0);
			pcard->is_moving = true;
			pcard->aniFrame = 5;
			mainGame->WaitFrameSignal(45);
			mainGame->dField.MoveCard(pcard, 5);
			mainGame->WaitFrameSignal(5);
		}
		return true;
	}
	case MSG_CONFIRM_CARDS: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int skip_panel = BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		int c, s;
		unsigned int code, l;
		std::vector<ClientCard*> field_confirm;
		std::vector<ClientCard*> panel_confirm;
		ClientCard* pcard;
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			pbuf += count * 7;
			return true;
		}
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::REVEAL);
		myswprintf(textBuffer, dataManager.GetSysString(208), count);
		mainGame->AddLog(textBuffer);
		for (int i = 0; i < count; ++i) {
			code = BufferIO::Read<int32_t>(pbuf);
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			pcard = mainGame->dField.GetCard(c, l, s);
			if (code != 0)
				pcard->SetCode(code);
			mainGame->gMutex.lock();
			myswprintf(textBuffer, L"*[%ls]", dataManager.GetName(code));
			mainGame->AddLog(textBuffer, code);
			mainGame->gMutex.unlock();
			if (l & 0x41) {
				if(count == 1) {
					float shift = -0.15f;
					if (c == 0 && l == 0x40) shift = 0.15f;
					pcard->dPos = irr::core::vector3df(shift, 0, 0);
					if((l == LOCATION_DECK) && mainGame->dField.deck_reversed)
						pcard->dRot = irr::core::vector3df(0, 0, 0);
					else pcard->dRot = irr::core::vector3df(0, 3.14159f / 5.0f, 0);
					pcard->is_moving = true;
					pcard->aniFrame = 5;
					mainGame->WaitFrameSignal(45);
					mainGame->dField.MoveCard(pcard, 5);
					mainGame->WaitFrameSignal(5);
				} else {
					if(!mainGame->dInfo.isReplay)
						panel_confirm.push_back(pcard);
				}
			} else {
				if(!mainGame->dInfo.isReplay || (l & LOCATION_ONFIELD))
					field_confirm.push_back(pcard);
			}
		}
		if (field_confirm.size() > 0) {
			mainGame->WaitFrameSignal(5);
			for(int i = 0; i < (int)field_confirm.size(); ++i) {
				pcard = field_confirm[i];
				c = pcard->controler;
				l = pcard->location;
				if (l == LOCATION_HAND) {
					mainGame->dField.MoveCard(pcard, 5);
					pcard->is_highlighting = true;
				} else if (l == LOCATION_MZONE) {
					if (pcard->position & POS_FACEUP)
						continue;
					pcard->dPos = irr::core::vector3df(0, 0, 0);
					if (pcard->position == POS_FACEDOWN_ATTACK)
						pcard->dRot = irr::core::vector3df(0, 3.14159f / 5.0f, 0);
					else
						pcard->dRot = irr::core::vector3df(3.14159f / 5.0f, 0, 0);
					pcard->is_moving = true;
					pcard->aniFrame = 5;
				} else if (l == LOCATION_SZONE) {
					if (pcard->position & POS_FACEUP)
						continue;
					pcard->dPos = irr::core::vector3df(0, 0, 0);
					pcard->dRot = irr::core::vector3df(0, 3.14159f / 5.0f, 0);
					pcard->is_moving = true;
					pcard->aniFrame = 5;
				}
			}
			if (mainGame->dInfo.isReplay)
				mainGame->WaitFrameSignal(30);
			else
				mainGame->WaitFrameSignal(90);
			for(int i = 0; i < (int)field_confirm.size(); ++i) {
				pcard = field_confirm[i];
				mainGame->dField.MoveCard(pcard, 5);
				pcard->is_highlighting = false;
			}
			mainGame->WaitFrameSignal(5);
		}
		if (!skip_panel && panel_confirm.size() && mainGame->dInfo.player_type != 7) {
			std::sort(panel_confirm.begin(), panel_confirm.end(), ClientCard::client_card_sort);
			mainGame->gMutex.lock();
			mainGame->dField.selectable_cards = panel_confirm;
			myswprintf(textBuffer, dataManager.GetSysString(208), panel_confirm.size());
			mainGame->stCardSelect->setText(textBuffer);
			mainGame->dField.ShowSelectCard(true);
			mainGame->gMutex.unlock();
			mainGame->actionSignal.Reset();
			mainGame->actionSignal.Wait();
		}
		return true;
	}
	case MSG_SHUFFLE_DECK: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		if(mainGame->dField.deck[player].size() < 2)
			return true;
		bool rev = mainGame->dField.deck_reversed;
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->dField.deck_reversed = false;
			if(rev) {
				for (size_t i = 0; i < mainGame->dField.deck[player].size(); ++i)
					mainGame->dField.MoveCard(mainGame->dField.deck[player][i], 10);
				mainGame->WaitFrameSignal(10);
			}
		}
		for (size_t i = 0; i < mainGame->dField.deck[player].size(); ++i) {
			mainGame->dField.deck[player][i]->code = 0;
			mainGame->dField.deck[player][i]->is_reversed = false;
		}
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SHUFFLE);
			for (int i = 0; i < 5; ++i) {
				for (auto cit = mainGame->dField.deck[player].begin(); cit != mainGame->dField.deck[player].end(); ++cit) {
					(*cit)->dPos = irr::core::vector3df(real_dist(rnd) * 0.4f - 0.2f, 0, 0);
					(*cit)->dRot = irr::core::vector3df(0, 0, 0);
					(*cit)->is_moving = true;
					(*cit)->aniFrame = 3;
				}
				mainGame->WaitFrameSignal(3);
				for (auto cit = mainGame->dField.deck[player].begin(); cit != mainGame->dField.deck[player].end(); ++cit)
					mainGame->dField.MoveCard(*cit, 3);
				mainGame->WaitFrameSignal(3);
			}
			mainGame->dField.deck_reversed = rev;
			if(rev) {
				for (size_t i = 0; i < mainGame->dField.deck[player].size(); ++i)
					mainGame->dField.MoveCard(mainGame->dField.deck[player][i], 10);
			}
		}
		return true;
	}
	case MSG_SHUFFLE_HAND: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			if(count > 1)
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SHUFFLE);
			mainGame->WaitFrameSignal(5);
			if(player == 1 && !mainGame->dInfo.isReplay && !mainGame->dInfo.isSingleMode) {
				bool flip = false;
				for (auto cit = mainGame->dField.hand[player].begin(); cit != mainGame->dField.hand[player].end(); ++cit)
					if((*cit)->code) {
						(*cit)->dPos = irr::core::vector3df(0, 0, 0);
						(*cit)->dRot = irr::core::vector3df(1.322f / 5, 3.1415926f / 5, 0);
						(*cit)->is_moving = true;
						(*cit)->is_hovered = false;
						(*cit)->aniFrame = 5;
						flip = true;
					}
				if(flip)
					mainGame->WaitFrameSignal(5);
			}
			for (auto cit = mainGame->dField.hand[player].begin(); cit != mainGame->dField.hand[player].end(); ++cit) {
				(*cit)->dPos = irr::core::vector3df((3.9f - (*cit)->curPos.X) / 5, 0, 0);
				(*cit)->dRot = irr::core::vector3df(0, 0, 0);
				(*cit)->is_moving = true;
				(*cit)->is_hovered = false;
				(*cit)->aniFrame = 5;
			}
			mainGame->WaitFrameSignal(11);
		}
		for(auto cit = mainGame->dField.hand[player].begin(); cit != mainGame->dField.hand[player].end(); ++cit) {
			(*cit)->SetCode(BufferIO::Read<int32_t>(pbuf));
			(*cit)->desc_hints.clear();
		}
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			for (auto cit = mainGame->dField.hand[player].begin(); cit != mainGame->dField.hand[player].end(); ++cit) {
				(*cit)->is_hovered = false;
				mainGame->dField.MoveCard(*cit, 5);
			}
			mainGame->WaitFrameSignal(5);
		}
		return true;
	}
	case MSG_SHUFFLE_EXTRA: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		if((mainGame->dField.extra[player].size() - mainGame->dField.extra_p_count[player]) < 2)
			return true;
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			if(count > 1)
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SHUFFLE);
			for (int i = 0; i < 5; ++i) {
				for (auto cit = mainGame->dField.extra[player].begin(); cit != mainGame->dField.extra[player].end(); ++cit) {
					if(!((*cit)->position & POS_FACEUP)) {
						(*cit)->dPos = irr::core::vector3df(real_dist(rnd) * 0.4f - 0.2f, 0, 0);
						(*cit)->dRot = irr::core::vector3df(0, 0, 0);
						(*cit)->is_moving = true;
						(*cit)->aniFrame = 3;
					}
				}
				mainGame->WaitFrameSignal(3);
				for (auto cit = mainGame->dField.extra[player].begin(); cit != mainGame->dField.extra[player].end(); ++cit)
					if(!((*cit)->position & POS_FACEUP))
						mainGame->dField.MoveCard(*cit, 3);
				mainGame->WaitFrameSignal(3);
			}
		}
		for (auto cit = mainGame->dField.extra[player].begin(); cit != mainGame->dField.extra[player].end(); ++cit)
			if(!((*cit)->position & POS_FACEUP))
				(*cit)->SetCode(BufferIO::Read<int32_t>(pbuf));
		return true;
	}
	case MSG_REFRESH_DECK: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		return true;
	}
	case MSG_SWAP_GRAVE_DECK: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			mainGame->dField.grave[player].swap(mainGame->dField.deck[player]);
			for (auto cit = mainGame->dField.grave[player].begin(); cit != mainGame->dField.grave[player].end(); ++cit)
				(*cit)->location = LOCATION_GRAVE;
			int m = 0;
			for (auto cit = mainGame->dField.deck[player].begin(); cit != mainGame->dField.deck[player].end(); ) {
				if ((*cit)->type & (TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK)) {
					(*cit)->position = POS_FACEDOWN;
					mainGame->dField.AddCard(*cit, player, LOCATION_EXTRA, 0);
					cit = mainGame->dField.deck[player].erase(cit);
				} else {
					(*cit)->location = LOCATION_DECK;
					(*cit)->sequence = m++;
					++cit;
				}
			}
		} else {
			mainGame->gMutex.lock();
			mainGame->dField.grave[player].swap(mainGame->dField.deck[player]);
			for (auto cit = mainGame->dField.grave[player].begin(); cit != mainGame->dField.grave[player].end(); ++cit) {
				(*cit)->location = LOCATION_GRAVE;
				mainGame->dField.MoveCard(*cit, 10);
			}
			int m = 0;
			for (auto cit = mainGame->dField.deck[player].begin(); cit != mainGame->dField.deck[player].end(); ) {
				ClientCard* pcard = *cit;
				if (pcard->type & (TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK)) {
					pcard->position = POS_FACEDOWN;
					mainGame->dField.AddCard(pcard, player, LOCATION_EXTRA, 0);
					cit = mainGame->dField.deck[player].erase(cit);
				} else {
					pcard->location = LOCATION_DECK;
					pcard->sequence = m++;
					++cit;
				}
				mainGame->dField.MoveCard(pcard, 10);
			}
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(11);
		}
		return true;
	}
	case MSG_REVERSE_DECK: {
		mainGame->dField.deck_reversed = !mainGame->dField.deck_reversed;
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			for(size_t i = 0; i < mainGame->dField.deck[0].size(); ++i)
				mainGame->dField.MoveCard(mainGame->dField.deck[0][i], 10);
			for(size_t i = 0; i < mainGame->dField.deck[1].size(); ++i)
				mainGame->dField.MoveCard(mainGame->dField.deck[1][i], 10);
		}
		return true;
	}
	case MSG_DECK_TOP: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int seq = BufferIO::Read<uint8_t>(pbuf);
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		ClientCard* pcard = mainGame->dField.GetCard(player, LOCATION_DECK, mainGame->dField.deck[player].size() - 1 - seq);
		pcard->SetCode(code & 0x7fffffff);
		bool rev = (code & 0x80000000) != 0;
		if(pcard->is_reversed != rev) {
			pcard->is_reversed = rev;
			mainGame->dField.MoveCard(pcard, 5);
		}
		return true;
	}
	case MSG_SHUFFLE_SET_CARD: {
		std::vector<ClientCard*>* lst = 0;
		unsigned int loc = BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		if(loc == LOCATION_MZONE)
			lst = mainGame->dField.mzone;
		else
			lst = mainGame->dField.szone;
		ClientCard* mc[5]{ nullptr };
		ClientCard* swp;
		int c, s, ps;
		unsigned int l;
		for (int i = 0; i < count; ++i) {
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			BufferIO::Read<uint8_t>(pbuf);
			mc[i] = lst[c][s];
			mc[i]->SetCode(0);
			if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
				mc[i]->dPos = irr::core::vector3df((3.95f - mc[i]->curPos.X) / 10, 0, 0.05f);
				mc[i]->dRot = irr::core::vector3df(0, 0, 0);
				mc[i]->is_moving = true;
				mc[i]->aniFrame = 10;
			}
		}
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping)
			mainGame->WaitFrameSignal(20);
		for (int i = 0; i < count; ++i) {
			c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			l = BufferIO::Read<uint8_t>(pbuf);
			s = BufferIO::Read<uint8_t>(pbuf);
			BufferIO::Read<uint8_t>(pbuf);
			ps = mc[i]->sequence;
			if (l > 0) {
				swp = lst[c][s];
				lst[c][ps] = swp;
				lst[c][s] = mc[i];
				mc[i]->sequence = s;
				swp->sequence = ps;
			}
		}
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SHUFFLE);
			for (int i = 0; i < count; ++i) {
				mainGame->dField.MoveCard(mc[i], 10);
				for (auto cit = mc[i]->overlayed.begin(); cit != mc[i]->overlayed.end(); ++cit)
					mainGame->dField.MoveCard(*cit, 10);
			}
			mainGame->WaitFrameSignal(11);
		}
		return true;
	}
	case MSG_NEW_TURN: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		mainGame->dInfo.turn++;
		if(!mainGame->dInfo.isReplay && mainGame->dInfo.player_type < 7) {
			mainGame->dField.tag_surrender = false;
			mainGame->dField.tag_teammate_surrender = false;
			mainGame->btnLeaveGame->setText(dataManager.GetSysString(1351));
			mainGame->btnLeaveGame->setVisible(true);
		}
		mainGame->HideElement(mainGame->wSurrender);
		if(!mainGame->dInfo.isReplay && mainGame->dInfo.player_type < 7) {
			if(mainGame->gameConf.control_mode == 0) {
				mainGame->btnChainIgnore->setVisible(true);
				mainGame->btnChainAlways->setVisible(true);
				mainGame->btnChainWhenAvail->setVisible(true);
				mainGame->dField.UpdateChainButtons();
			} else {
				mainGame->btnChainIgnore->setVisible(false);
				mainGame->btnChainAlways->setVisible(false);
				mainGame->btnChainWhenAvail->setVisible(false);
				mainGame->btnCancelOrFinish->setVisible(false);
			}
		}
		if(mainGame->dInfo.isTag && mainGame->dInfo.turn != 1) {
			if(player == 0)
				mainGame->dInfo.tag_player[0] = !mainGame->dInfo.tag_player[0];
			else
				mainGame->dInfo.tag_player[1] = !mainGame->dInfo.tag_player[1];
		}
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::NEXT_TURN);
			mainGame->showcardcode = 10;
			mainGame->showcarddif = 30;
			mainGame->showcardp = 0;
			mainGame->showcard = 101;
			mainGame->WaitFrameSignal(40);
			mainGame->showcard = 0;
		}
		return true;
	}
	case MSG_NEW_PHASE: {
		unsigned short phase = BufferIO::Read<uint16_t>(pbuf);
		mainGame->btnPhaseStatus->setVisible(false);
		mainGame->btnBP->setVisible(false);
		mainGame->btnM2->setVisible(false);
		mainGame->btnEP->setVisible(false);
		mainGame->btnShuffle->setVisible(false);
		mainGame->showcarddif = 30;
		mainGame->showcardp = 0;
		mainGame->dField.RefreshCardCountDisplay();
		switch (phase) {
		case PHASE_DRAW:
			mainGame->btnPhaseStatus->setText(L"\xff24\xff30");
			mainGame->showcardcode = 4;
			break;
		case PHASE_STANDBY:
			mainGame->btnPhaseStatus->setText(L"\xff33\xff30");
			mainGame->showcardcode = 5;
			break;
		case PHASE_MAIN1:
			mainGame->btnPhaseStatus->setText(L"\xff2d\xff11");
			mainGame->showcardcode = 6;
			break;
		case PHASE_BATTLE_START:
			mainGame->btnPhaseStatus->setText(L"\xff22\xff30");
			mainGame->showcardcode = 7;
			break;
		case PHASE_MAIN2:
			mainGame->btnPhaseStatus->setText(L"\xff2d\xff12");
			mainGame->showcardcode = 8;
			break;
		case PHASE_END:
			mainGame->btnPhaseStatus->setText(L"\xff25\xff30");
			mainGame->showcardcode = 9;
			break;
		}
		mainGame->btnPhaseStatus->setPressed(true);
		mainGame->btnPhaseStatus->setVisible(true);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::PHASE);
			mainGame->showcard = 101;
			mainGame->WaitFrameSignal(40);
			mainGame->showcard = 0;
		}
		return true;
	}
	case MSG_MOVE: {
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		int pc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int pl = BufferIO::Read<uint8_t>(pbuf);
		int ps = BufferIO::Read<uint8_t>(pbuf);
		unsigned int pp = BufferIO::Read<uint8_t>(pbuf);
		int cc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int cl = BufferIO::Read<uint8_t>(pbuf);
		int cs = BufferIO::Read<uint8_t>(pbuf);
		unsigned int cp = BufferIO::Read<uint8_t>(pbuf);
		int reason = BufferIO::Read<int32_t>(pbuf);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			if(cl & LOCATION_REMOVED && pl != cl)
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BANISHED);
			else if(reason & REASON_DESTROY && pl != cl)
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::DESTROYED);
		}
		int appear = mainGame->gameConf.quick_animation ? 12 : 20;
		if (pl == 0) {
			ClientCard* pcard = new ClientCard;
			pcard->position = cp;
			pcard->SetCode(code);
			if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
				mainGame->gMutex.lock();
				mainGame->dField.AddCard(pcard, cc, cl, cs);
				mainGame->gMutex.unlock();
				mainGame->dField.GetCardLocation(pcard, &pcard->curPos, &pcard->curRot, true);
				pcard->curAlpha = 5;
				mainGame->dField.FadeCard(pcard, 255, appear);
				mainGame->WaitFrameSignal(appear);
			} else
				mainGame->dField.AddCard(pcard, cc, cl, cs);
		} else if (cl == 0) {
			ClientCard* pcard = mainGame->dField.GetCard(pc, pl, ps);
			if (code != 0 && pcard->code != code)
				pcard->SetCode(code);
			pcard->ClearTarget();
			for(auto eqit = pcard->equipped.begin(); eqit != pcard->equipped.end(); ++eqit)
				(*eqit)->equipTarget = 0;
			if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
				mainGame->dField.FadeCard(pcard, 5, appear);
				mainGame->WaitFrameSignal(appear);
				mainGame->gMutex.lock();
				mainGame->dField.RemoveCard(pc, pl, ps);
				mainGame->gMutex.unlock();
				if(pcard == mainGame->dField.hovered_card)
					mainGame->dField.hovered_card = 0;
			} else
				mainGame->dField.RemoveCard(pc, pl, ps);
			delete pcard;
		} else {
			if (!(pl & LOCATION_OVERLAY) && !(cl & LOCATION_OVERLAY)) {
				ClientCard* pcard = mainGame->dField.GetCard(pc, pl, ps);
				if (pcard->code != code && (code != 0 || cl == 0x40))
					pcard->SetCode(code);
				pcard->cHint = 0;
				pcard->chValue = 0;
				if((pl & LOCATION_ONFIELD) && (cl != pl))
					pcard->counters.clear();
				if(cl != pl) {
					pcard->ClearTarget();
					if(pcard->equipTarget) {
						pcard->equipTarget->is_showequip = false;
						pcard->equipTarget->equipped.erase(pcard);
						pcard->equipTarget = 0;
					}
				}
				pcard->is_hovered = false;
				pcard->is_showequip = false;
				pcard->is_showtarget = false;
				pcard->is_showchaintarget = false;
				if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
					mainGame->dField.RemoveCard(pc, pl, ps);
					pcard->position = cp;
					mainGame->dField.AddCard(pcard, cc, cl, cs);
				} else {
					mainGame->gMutex.lock();
					mainGame->dField.RemoveCard(pc, pl, ps);
					pcard->position = cp;
					mainGame->dField.AddCard(pcard, cc, cl, cs);
					mainGame->gMutex.unlock();
					if (pl == cl && pc == cc && (cl & 0x71)) {
						pcard->dPos = irr::core::vector3df(-0.3f, 0, 0);
						pcard->dRot = irr::core::vector3df(0, 0, 0);
						if (pc == 1) pcard->dPos.X = 0.3f;
						pcard->is_moving = true;
						pcard->aniFrame = 5;
						mainGame->WaitFrameSignal(5);
						mainGame->dField.MoveCard(pcard, 5);
						mainGame->WaitFrameSignal(5);
					} else {
						if (cl == 0x4 && pcard->overlayed.size() > 0) {
							mainGame->gMutex.lock();
							for (size_t i = 0; i < pcard->overlayed.size(); ++i)
								mainGame->dField.MoveCard(pcard->overlayed[i], 10);
							mainGame->gMutex.unlock();
							mainGame->WaitFrameSignal(10);
						}
						if (cl == 0x2) {
							mainGame->gMutex.lock();
							for (size_t i = 0; i < mainGame->dField.hand[cc].size(); ++i)
								mainGame->dField.MoveCard(mainGame->dField.hand[cc][i], 10);
							mainGame->gMutex.unlock();
						} else {
							mainGame->gMutex.lock();
							mainGame->dField.MoveCard(pcard, 10);
							if (pl == 0x2)
								for (size_t i = 0; i < mainGame->dField.hand[pc].size(); ++i)
									mainGame->dField.MoveCard(mainGame->dField.hand[pc][i], 10);
							mainGame->gMutex.unlock();
						}
						mainGame->WaitFrameSignal(5);
					}
				}
			} else if (!(pl & LOCATION_OVERLAY)) {
				ClientCard* pcard = mainGame->dField.GetCard(pc, pl, ps);
				if (code != 0 && pcard->code != code)
					pcard->SetCode(code);
				pcard->counters.clear();
				pcard->ClearTarget();
				if(pcard->equipTarget) {
					pcard->equipTarget->is_showequip = false;
					pcard->equipTarget->equipped.erase(pcard);
					pcard->equipTarget = 0;
				}
				pcard->is_showequip = false;
				pcard->is_showtarget = false;
				pcard->is_showchaintarget = false;
				ClientCard* olcard = mainGame->dField.GetCard(cc, cl & 0x7f, cs);
				if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
					mainGame->dField.RemoveCard(pc, pl, ps);
					olcard->overlayed.push_back(pcard);
					mainGame->dField.overlay_cards.insert(pcard);
					pcard->overlayTarget = olcard;
					pcard->location = LOCATION_OVERLAY;
					pcard->sequence = (unsigned char)(olcard->overlayed.size() - 1);
				} else {
					mainGame->gMutex.lock();
					mainGame->dField.RemoveCard(pc, pl, ps);
					olcard->overlayed.push_back(pcard);
					mainGame->dField.overlay_cards.insert(pcard);
					mainGame->gMutex.unlock();
					pcard->overlayTarget = olcard;
					pcard->location = LOCATION_OVERLAY;
					pcard->sequence = (unsigned char)(olcard->overlayed.size() - 1);
					if (olcard->location == LOCATION_MZONE) {
						mainGame->gMutex.lock();
						mainGame->dField.MoveCard(pcard, 10);
						if (pl == 0x2)
							for (size_t i = 0; i < mainGame->dField.hand[pc].size(); ++i)
								mainGame->dField.MoveCard(mainGame->dField.hand[pc][i], 10);
						mainGame->gMutex.unlock();
						mainGame->WaitFrameSignal(5);
					}
				}
			} else if (!(cl & LOCATION_OVERLAY)) {
				ClientCard* olcard = mainGame->dField.GetCard(pc, pl & 0x7f, ps);
				ClientCard* pcard = olcard->overlayed[pp];
				if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
					olcard->overlayed.erase(olcard->overlayed.begin() + pcard->sequence);
					pcard->overlayTarget = 0;
					pcard->position = cp;
					mainGame->dField.AddCard(pcard, cc, cl, cs);
					mainGame->dField.overlay_cards.erase(pcard);
					for (size_t i = 0; i < olcard->overlayed.size(); ++i)
						olcard->overlayed[i]->sequence = (unsigned char)i;
				} else {
					mainGame->gMutex.lock();
					olcard->overlayed.erase(olcard->overlayed.begin() + pcard->sequence);
					pcard->overlayTarget = 0;
					pcard->position = cp;
					mainGame->dField.AddCard(pcard, cc, cl, cs);
					mainGame->dField.overlay_cards.erase(pcard);
					for (size_t i = 0; i < olcard->overlayed.size(); ++i) {
						olcard->overlayed[i]->sequence = (unsigned char)i;
						mainGame->dField.MoveCard(olcard->overlayed[i], 2);
					}
					mainGame->gMutex.unlock();
					mainGame->WaitFrameSignal(5);
					mainGame->gMutex.lock();
					mainGame->dField.MoveCard(pcard, 10);
					mainGame->gMutex.unlock();
					mainGame->WaitFrameSignal(5);
				}
			} else {
				ClientCard* olcard1 = mainGame->dField.GetCard(pc, pl & 0x7f, ps);
				ClientCard* pcard = olcard1->overlayed[pp];
				ClientCard* olcard2 = mainGame->dField.GetCard(cc, cl & 0x7f, cs);
				if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
					olcard1->overlayed.erase(olcard1->overlayed.begin() + pcard->sequence);
					olcard2->overlayed.push_back(pcard);
					pcard->sequence = (unsigned char)(olcard2->overlayed.size() - 1);
					pcard->location = LOCATION_OVERLAY;
					pcard->overlayTarget = olcard2;
					for (size_t i = 0; i < olcard1->overlayed.size(); ++i) {
						olcard1->overlayed[i]->sequence = (unsigned char)i;
					}
				} else {
					mainGame->gMutex.lock();
					olcard1->overlayed.erase(olcard1->overlayed.begin() + pcard->sequence);
					olcard2->overlayed.push_back(pcard);
					pcard->sequence = (unsigned char)(olcard2->overlayed.size() - 1);
					pcard->location = LOCATION_OVERLAY;
					pcard->overlayTarget = olcard2;
					for (size_t i = 0; i < olcard1->overlayed.size(); ++i) {
						olcard1->overlayed[i]->sequence = (unsigned char)i;
						mainGame->dField.MoveCard(olcard1->overlayed[i], 2);
					}
					mainGame->dField.MoveCard(pcard, 10);
					mainGame->gMutex.unlock();
					mainGame->WaitFrameSignal(5);
				}
			}
		}
		return true;
	}
	case MSG_POS_CHANGE: {
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		int cc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int cl = BufferIO::Read<uint8_t>(pbuf);
		int cs = BufferIO::Read<uint8_t>(pbuf);
		unsigned int pp = BufferIO::Read<uint8_t>(pbuf);
		unsigned int cp = BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pcard = mainGame->dField.GetCard(cc, cl, cs);
		if((pp & POS_FACEUP) && (cp & POS_FACEDOWN)) {
			pcard->counters.clear();
			pcard->ClearTarget();
		}
		if (code != 0 && pcard->code != code)
			pcard->SetCode(code);
		pcard->position = cp;
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			myswprintf(event_string, dataManager.GetSysString(1600));
			mainGame->dField.MoveCard(pcard, 10);
			mainGame->WaitFrameSignal(11);
		}
		return true;
	}
	case MSG_SET: {
		/*int code = */BufferIO::Read<int32_t>(pbuf);
		/*int cc = mainGame->LocalPlayer*/(BufferIO::Read<uint8_t>(pbuf));
		/*int cl = */BufferIO::Read<uint8_t>(pbuf);
		/*int cs = */BufferIO::Read<uint8_t>(pbuf);
		/*int cp = */BufferIO::Read<uint8_t>(pbuf);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping)
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SET);
		myswprintf(event_string, dataManager.GetSysString(1601));
		return true;
	}
	case MSG_SWAP: {
		/*int code1 = */BufferIO::Read<int32_t>(pbuf);
		int c1 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l1 = BufferIO::Read<uint8_t>(pbuf);
		int s1 = BufferIO::Read<uint8_t>(pbuf);
		/*int p1 = */BufferIO::Read<uint8_t>(pbuf);
		/*int code2 = */BufferIO::Read<int32_t>(pbuf);
		int c2 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l2 = BufferIO::Read<uint8_t>(pbuf);
		int s2 = BufferIO::Read<uint8_t>(pbuf);
		/*int p2 = */BufferIO::Read<uint8_t>(pbuf);
		myswprintf(event_string, dataManager.GetSysString(1602));
		ClientCard* pc1 = mainGame->dField.GetCard(c1, l1, s1);
		ClientCard* pc2 = mainGame->dField.GetCard(c2, l2, s2);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->gMutex.lock();
			mainGame->dField.RemoveCard(c1, l1, s1);
			mainGame->dField.RemoveCard(c2, l2, s2);
			mainGame->dField.AddCard(pc1, c2, l2, s2);
			mainGame->dField.AddCard(pc2, c1, l1, s1);
			mainGame->dField.MoveCard(pc1, 10);
			mainGame->dField.MoveCard(pc2, 10);
			for (size_t i = 0; i < pc1->overlayed.size(); ++i)
				mainGame->dField.MoveCard(pc1->overlayed[i], 10);
			for (size_t i = 0; i < pc2->overlayed.size(); ++i)
				mainGame->dField.MoveCard(pc2->overlayed[i], 10);
			mainGame->gMutex.unlock();
			mainGame->WaitFrameSignal(11);
		} else {
			mainGame->dField.RemoveCard(c1, l1, s1);
			mainGame->dField.RemoveCard(c2, l2, s2);
			mainGame->dField.AddCard(pc1, c2, l2, s2);
			mainGame->dField.AddCard(pc2, c1, l1, s1);
		}
		return true;
	}
	case MSG_FIELD_DISABLED: {
		unsigned int disabled = BufferIO::Read<int32_t>(pbuf);
		if (!mainGame->dInfo.isFirst)
			disabled = (disabled >> 16) | (disabled << 16);
		mainGame->dField.disabled_field = disabled;
		return true;
	}
	case MSG_SUMMONING: {
        // 从消息缓冲区读取召唤的卡片密码
        unsigned int code = BufferIO::Read<int32_t>(pbuf);
        // 读取控制者位置（被注释掉，未使用）
        /*int cc = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
        // 读取召唤位置（被注释掉，未使用）
        /*int cl = */BufferIO::Read<uint8_t>(pbuf);
        // 读取召唤区域序列（被注释掉，未使用）
        /*int cs = */BufferIO::Read<uint8_t>(pbuf);
        // 读取召唤位置（被注释掉，未使用）
        /*int cp = */BufferIO::Read<uint8_t>(pbuf);
        // 如果不是回放模式或者不是跳过回放，则执行以下操作
        if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
            // 尝试播放在sound/chants/{code}.mp3 该卡片id相同文件名所在的音效，如果失败则播放普通召唤音效
            if(!mainGame->soundManager->PlayChant(code))
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SUMMON);
            // 构造召唤事件字符串，显示"召唤了xxx"，会打印在消息记录窗口中
            myswprintf(event_string, dataManager.GetSysString(1603), dataManager.GetName(code));
            // 设置要显示的卡片密码
            mainGame->showcardcode = code;
            // 设置卡片显示差异参数为0
            mainGame->showcarddif = 0;
            // 设置卡片显示位置参数为0
            mainGame->showcardp = 0;
            // 设置显示卡片类型为7（召唤动画）
            mainGame->showcard = 7;
            // 等待30帧显示召唤动画
            mainGame->WaitFrameSignal(30);
            // 重置显示卡片类型
            mainGame->showcard = 0;
            // 再等待11帧
            mainGame->WaitFrameSignal(11);
        }
        // 返回true表示处理成功
        return true;
    }
	case MSG_SUMMONED: {
		myswprintf(event_string, dataManager.GetSysString(1604));
		return true;
	}
	case MSG_SPSUMMONING: {
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		/*int cc = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		/*int cl = */BufferIO::Read<uint8_t>(pbuf);
		/*int cs = */BufferIO::Read<uint8_t>(pbuf);
		/*int cp = */BufferIO::Read<uint8_t>(pbuf);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			CardData cd;
			if(dataManager.GetData(code, &cd) && (cd.type & TYPE_TOKEN))
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::TOKEN);
			else
				if(!mainGame->soundManager->PlayChant(code))
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SPECIAL_SUMMON);
			myswprintf(event_string, dataManager.GetSysString(1605), dataManager.GetName(code));
			if(code) {
				mainGame->showcardcode = code;
				mainGame->showcarddif = 1;
				mainGame->showcard = 5;
				mainGame->WaitFrameSignal(30);
				mainGame->showcard = 0;
				mainGame->WaitFrameSignal(11);
			}
		}
		return true;
	}
	case MSG_SPSUMMONED: {
		myswprintf(event_string, dataManager.GetSysString(1606));
		return true;
	}
	case MSG_FLIPSUMMONING: {
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		int cc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int cl = BufferIO::Read<uint8_t>(pbuf);
		int cs = BufferIO::Read<uint8_t>(pbuf);
		unsigned int cp = BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pcard = mainGame->dField.GetCard(cc, cl, cs);
		pcard->SetCode(code);
		pcard->position = cp;
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			if(!mainGame->soundManager->PlayChant(code))
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::FLIP);
			myswprintf(event_string, dataManager.GetSysString(1607), dataManager.GetName(code));
			mainGame->dField.MoveCard(pcard, 10);
			mainGame->WaitFrameSignal(11);
			mainGame->showcardcode = code;
			mainGame->showcarddif = 0;
			mainGame->showcardp = 0;
			mainGame->showcard = 7;
			mainGame->WaitFrameSignal(30);
			mainGame->showcard = 0;
			mainGame->WaitFrameSignal(11);
		}
		return true;
	}
	case MSG_FLIPSUMMONED: {
		myswprintf(event_string, dataManager.GetSysString(1608));
		return true;
	}
	case MSG_CHAINING: {
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		int pcc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int pcl = BufferIO::Read<uint8_t>(pbuf);
		int pcs = BufferIO::Read<uint8_t>(pbuf);
		int subs = BufferIO::Read<uint8_t>(pbuf);
		int cc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int cl = BufferIO::Read<uint8_t>(pbuf);
		int cs = BufferIO::Read<uint8_t>(pbuf);
		int desc = BufferIO::Read<int32_t>(pbuf);
		/*int ct = */BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::ACTIVATE);
		ClientCard* pcard = mainGame->dField.GetCard(pcc, pcl, pcs, subs);
		if(pcard->code != code) {
			pcard->code = code;
			mainGame->dField.MoveCard(pcard, 10);
		}
		mainGame->showcardcode = code;
		mainGame->showcarddif = 0;
		mainGame->showcard = 1;
		pcard->is_highlighting = true;
		if(pcard->location & 0x30) {
			float shift = -0.15f;
			if(cc == 1) shift = 0.15f;
			pcard->dPos = irr::core::vector3df(shift, 0, 0);
			pcard->dRot = irr::core::vector3df(0, 0, 0);
			pcard->is_moving = true;
			pcard->aniFrame = 5;
			mainGame->WaitFrameSignal(30);
			mainGame->dField.MoveCard(pcard, 5);
		} else
			mainGame->WaitFrameSignal(30);
		pcard->is_highlighting = false;
		mainGame->dField.current_chain.chain_card = pcard;
		mainGame->dField.current_chain.code = code;
		mainGame->dField.current_chain.desc = desc;
		mainGame->dField.current_chain.controler = cc;
		mainGame->dField.current_chain.location = cl;
		mainGame->dField.current_chain.sequence = cs;
		mainGame->dField.GetChainLocation(cc, cl, cs, &mainGame->dField.current_chain.chain_pos);
		mainGame->dField.current_chain.solved = false;
		mainGame->dField.current_chain.need_distinguish = false;
		if(cl & LOCATION_ONFIELD && cc == 1) {
			for(auto it = mainGame->dField.mzone[cc].begin(); it != mainGame->dField.mzone[cc].end(); ++it) {
				if(*it && *it != pcard && (*it)->code == pcard->code)
					mainGame->dField.current_chain.need_distinguish = true;
			}
			for(auto it = mainGame->dField.szone[cc].begin(); it != mainGame->dField.szone[cc].end(); ++it) {
				if(*it && *it != pcard && (*it)->code == pcard->code)
					mainGame->dField.current_chain.need_distinguish = true;
			}
		}
		mainGame->dField.current_chain.target.clear();
		int chc = 0;
		for(auto chit = mainGame->dField.chains.begin(); chit != mainGame->dField.chains.end(); ++chit) {
			if (cl == 0x10 || cl == 0x20) {
				if (chit->controler == cc && chit->location == cl)
					chc++;
			} else {
				if (chit->controler == cc && chit->location == cl && chit->sequence == cs)
					chc++;
			}
		}
		if(cl == LOCATION_HAND)
			mainGame->dField.current_chain.chain_pos.X += 0.35f;
		else
			mainGame->dField.current_chain.chain_pos.Y += chc * 0.25f;
		return true;
	}
	case MSG_CHAINED: {
		int ct = BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		myswprintf(event_string, dataManager.GetSysString(1609), dataManager.GetName(mainGame->dField.current_chain.code));
		mainGame->gMutex.lock();
		mainGame->dField.chains.push_back(mainGame->dField.current_chain);
		mainGame->gMutex.unlock();
		if (ct > 1)
			mainGame->WaitFrameSignal(20);
		mainGame->dField.last_chain = true;
		return true;
	}
	case MSG_CHAIN_SOLVING: {
		int ct = BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		if(mainGame->dField.chains.size() > 1 || mainGame->gameConf.draw_single_chain) {
			if (mainGame->dField.last_chain)
				mainGame->WaitFrameSignal(11);
			for(int i = 0; i < 5; ++i) {
				mainGame->dField.chains[ct - 1].solved = false;
				mainGame->WaitFrameSignal(3);
				mainGame->dField.chains[ct - 1].solved = true;
				mainGame->WaitFrameSignal(3);
			}
		}
		mainGame->dField.last_chain = false;
		return true;
	}
	case MSG_CHAIN_SOLVED: {
		/*int ct = */BufferIO::Read<uint8_t>(pbuf);
		return true;
	}
	case MSG_CHAIN_END: {
		for(auto chit = mainGame->dField.chains.begin(); chit != mainGame->dField.chains.end(); ++chit) {
			for(auto tgit = chit->target.begin(); tgit != chit->target.end(); ++tgit)
				(*tgit)->is_showchaintarget = false;
			chit->chain_card->is_showchaintarget = false;
		}
		mainGame->dField.chains.clear();
		return true;
	}
	case MSG_CHAIN_NEGATED:
	case MSG_CHAIN_DISABLED: {
		int ct = BufferIO::Read<uint8_t>(pbuf);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::NEGATE);
			mainGame->showcardcode = mainGame->dField.chains[ct - 1].code;
			mainGame->showcarddif = 0;
			mainGame->showcard = 3;
			mainGame->WaitFrameSignal(30);
			mainGame->showcard = 0;
		}
		return true;
	}
	case MSG_CARD_SELECTED: {
		return true;
	}
	case MSG_RANDOM_SELECTED: {
		/*int player = */BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			pbuf += count * 4;
			return true;
		}
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::DICE);
		ClientCard* pcards[10];
		for (int i = 0; i < count; ++i) {
			int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			unsigned int l = BufferIO::Read<uint8_t>(pbuf);
			int s = BufferIO::Read<uint8_t>(pbuf);
			int ss = BufferIO::Read<uint8_t>(pbuf);
			if (l & LOCATION_OVERLAY)
				pcards[i] = mainGame->dField.GetCard(c, l & 0x7f, s)->overlayed[ss];
			else
				pcards[i] = mainGame->dField.GetCard(c, l, s);
			pcards[i]->is_highlighting = true;
		}
		mainGame->WaitFrameSignal(30);
		for(int i = 0; i < count; ++i)
			pcards[i]->is_highlighting = false;
		return true;
	}
	case MSG_BECOME_TARGET: {
		//mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::TARGET);
		int count = BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			pbuf += count * 4;
			return true;
		}
		for (int i = 0; i < count; ++i) {
			int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			unsigned int l = BufferIO::Read<uint8_t>(pbuf);
			int s = BufferIO::Read<uint8_t>(pbuf);
			/*int ss = */BufferIO::Read<uint8_t>(pbuf);
			ClientCard* pcard = mainGame->dField.GetCard(c, l, s);
			pcard->is_highlighting = true;
			mainGame->dField.current_chain.target.insert(pcard);
			if(pcard->location & LOCATION_ONFIELD) {
				for (int j = 0; j < 3; ++j) {
					mainGame->dField.FadeCard(pcard, 5, 5);
					mainGame->WaitFrameSignal(5);
					mainGame->dField.FadeCard(pcard, 255, 5);
					mainGame->WaitFrameSignal(5);
				}
			} else if(pcard->location & 0x30) {
				float shift = -0.15f;
				if(c == 1) shift = 0.15f;
				pcard->dPos = irr::core::vector3df(shift, 0, 0);
				pcard->dRot = irr::core::vector3df(0, 0, 0);
				pcard->is_moving = true;
				pcard->aniFrame = 5;
				mainGame->WaitFrameSignal(30);
				mainGame->dField.MoveCard(pcard, 5);
			} else
				mainGame->WaitFrameSignal(30);
			myswprintf(textBuffer, dataManager.GetSysString(1610), dataManager.GetName(pcard->code), dataManager.FormatLocation(l, s), s + 1);
			mainGame->AddLog(textBuffer, pcard->code);
			pcard->is_highlighting = false;
		}
		return true;
	}
	case MSG_DRAW: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pcard;
		for (int i = 0; i < count; ++i) {
			unsigned int code = BufferIO::Read<int32_t>(pbuf);
			pcard = mainGame->dField.GetCard(player, LOCATION_DECK, mainGame->dField.deck[player].size() - 1 - i);
			if(!mainGame->dField.deck_reversed || code)
				pcard->SetCode(code & 0x7fffffff);
		}
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			for (int i = 0; i < count; ++i) {
				pcard = mainGame->dField.GetCard(player, LOCATION_DECK, mainGame->dField.deck[player].size() - 1);
				mainGame->dField.deck[player].erase(mainGame->dField.deck[player].end() - 1);
				mainGame->dField.AddCard(pcard, player, LOCATION_HAND, 0);
			}
		} else {
			for (int i = 0; i < count; ++i) {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::DRAW);
				mainGame->gMutex.lock();
				pcard = mainGame->dField.GetCard(player, LOCATION_DECK, mainGame->dField.deck[player].size() - 1);
				mainGame->dField.deck[player].erase(mainGame->dField.deck[player].end() - 1);
				mainGame->dField.AddCard(pcard, player, LOCATION_HAND, 0);
				for(int j = 0; j < (int)mainGame->dField.hand[player].size(); ++j)
					mainGame->dField.MoveCard(mainGame->dField.hand[player][j], 10);
				mainGame->gMutex.unlock();
				mainGame->WaitFrameSignal(5);
			}
		}
		if (player == 0)
			myswprintf(event_string, dataManager.GetSysString(1611), count);
		else myswprintf(event_string, dataManager.GetSysString(1612), count);
		return true;
	}
	case MSG_DAMAGE: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int val = BufferIO::Read<int32_t>(pbuf);
		int final = mainGame->dInfo.lp[player] - val;
		if (final < 0)
			final = 0;
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			mainGame->dInfo.lp[player] = final;
			myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
			return true;
		}
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::DAMAGE);
		mainGame->lpd = (mainGame->dInfo.lp[player] - final) / 10;
		if (player == 0)
			myswprintf(event_string, dataManager.GetSysString(1613), val);
		else
			myswprintf(event_string, dataManager.GetSysString(1614), val);
		mainGame->lpccolor = 0xffff0000;
		mainGame->lpplayer = player;
		myswprintf(textBuffer, L"-%d", val);
		mainGame->lpcstring = textBuffer;
		mainGame->WaitFrameSignal(30);
		mainGame->lpframe = 10;
		mainGame->WaitFrameSignal(11);
		mainGame->lpcstring = L"";
		mainGame->dInfo.lp[player] = final;
		mainGame->gMutex.lock();
		myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_RECOVER: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int val = BufferIO::Read<int32_t>(pbuf);
		int final = mainGame->dInfo.lp[player] + val;
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			mainGame->dInfo.lp[player] = final;
			myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
			return true;
		}
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::RECOVER);
		mainGame->lpd = (mainGame->dInfo.lp[player] - final) / 10;
		if (player == 0)
			myswprintf(event_string, dataManager.GetSysString(1615), val);
		else
			myswprintf(event_string, dataManager.GetSysString(1616), val);
		mainGame->lpccolor = 0xff00ff00;
		mainGame->lpplayer = player;
		myswprintf(textBuffer, L"+%d", val);
		mainGame->lpcstring = textBuffer;
		mainGame->WaitFrameSignal(30);
		mainGame->lpframe = 10;
		mainGame->WaitFrameSignal(11);
		mainGame->lpcstring = L"";
		mainGame->dInfo.lp[player] = final;
		mainGame->gMutex.lock();
		myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_EQUIP: {
		int c1 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l1 = BufferIO::Read<uint8_t>(pbuf);
		int s1 = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		int c2 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l2 = BufferIO::Read<uint8_t>(pbuf);
		int s2 = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pc1 = mainGame->dField.GetCard(c1, l1, s1);
		ClientCard* pc2 = mainGame->dField.GetCard(c2, l2, s2);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			if(pc1->equipTarget)
				pc1->equipTarget->equipped.erase(pc1);
			pc1->equipTarget = pc2;
			pc2->equipped.insert(pc1);
		} else {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::EQUIP);
			mainGame->gMutex.lock();
			if(pc1->equipTarget) {
				pc1->is_showequip = false;
				pc1->equipTarget->is_showequip = false;
				pc1->equipTarget->equipped.erase(pc1);
			}
			pc1->equipTarget = pc2;
			pc2->equipped.insert(pc1);
			if (mainGame->dField.hovered_card == pc1)
				pc2->is_showequip = true;
			else if (mainGame->dField.hovered_card == pc2)
				pc1->is_showequip = true;
			mainGame->gMutex.unlock();
		}
		return true;
	}
	case MSG_LPUPDATE: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int val = BufferIO::Read<int32_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			mainGame->dInfo.lp[player] = val;
			myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
			return true;
		}
		mainGame->lpd = (mainGame->dInfo.lp[player] - val) / 10;
		mainGame->lpplayer = player;
		mainGame->lpframe = 10;
		mainGame->WaitFrameSignal(11);
		mainGame->dInfo.lp[player] = val;
		mainGame->gMutex.lock();
		myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_UNEQUIP: {
		int c1 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l1 = BufferIO::Read<uint8_t>(pbuf);
		int s1 = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pc = mainGame->dField.GetCard(c1, l1, s1);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			pc->equipTarget->equipped.erase(pc);
			pc->equipTarget = 0;
		} else {
			mainGame->gMutex.lock();
			if (mainGame->dField.hovered_card == pc)
				pc->equipTarget->is_showequip = false;
			else if (mainGame->dField.hovered_card == pc->equipTarget)
				pc->is_showequip = false;
			pc->equipTarget->equipped.erase(pc);
			pc->equipTarget = 0;
			mainGame->gMutex.unlock();
		}
		return true;
	}
	case MSG_CARD_TARGET: {
		int c1 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l1 = BufferIO::Read<uint8_t>(pbuf);
		int s1 = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		int c2 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l2 = BufferIO::Read<uint8_t>(pbuf);
		int s2 = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pc1 = mainGame->dField.GetCard(c1, l1, s1);
		ClientCard* pc2 = mainGame->dField.GetCard(c2, l2, s2);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			pc1->cardTarget.insert(pc2);
			pc2->ownerTarget.insert(pc1);
		} else {
			mainGame->gMutex.lock();
			pc1->cardTarget.insert(pc2);
			pc2->ownerTarget.insert(pc1);
			if (mainGame->dField.hovered_card == pc1)
				pc2->is_showtarget = true;
			else if (mainGame->dField.hovered_card == pc2)
				pc1->is_showtarget = true;
			mainGame->gMutex.unlock();
		}
		break;
	}
	case MSG_CANCEL_TARGET: {
		int c1 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l1 = BufferIO::Read<uint8_t>(pbuf);
		int s1 = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		int c2 = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l2 = BufferIO::Read<uint8_t>(pbuf);
		int s2 = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		ClientCard* pc1 = mainGame->dField.GetCard(c1, l1, s1);
		ClientCard* pc2 = mainGame->dField.GetCard(c2, l2, s2);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			pc1->cardTarget.erase(pc2);
			pc2->ownerTarget.erase(pc1);
		} else {
			mainGame->gMutex.lock();
			pc1->cardTarget.erase(pc2);
			pc2->ownerTarget.erase(pc1);
			if (mainGame->dField.hovered_card == pc1)
				pc2->is_showtarget = false;
			else if (mainGame->dField.hovered_card == pc2)
				pc1->is_showtarget = false;
			mainGame->gMutex.unlock();
		}
		break;
	}
	case MSG_PAY_LPCOST: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int cost = BufferIO::Read<int32_t>(pbuf);
		int final = mainGame->dInfo.lp[player] - cost;
		if (final < 0)
			final = 0;
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping) {
			mainGame->dInfo.lp[player] = final;
			myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
			return true;
		}
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::DAMAGE);
		mainGame->lpd = (mainGame->dInfo.lp[player] - final) / 10;
		mainGame->lpccolor = 0xff0000ff;
		mainGame->lpplayer = player;
		myswprintf(textBuffer, L"-%d", cost);
		mainGame->lpcstring = textBuffer;
		mainGame->WaitFrameSignal(30);
		mainGame->lpframe = 10;
		mainGame->WaitFrameSignal(11);
		mainGame->lpcstring = L"";
		mainGame->dInfo.lp[player] = final;
		mainGame->gMutex.lock();
		myswprintf(mainGame->dInfo.strLP[player], L"%d", mainGame->dInfo.lp[player]);
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_ADD_COUNTER: {
		int type = BufferIO::Read<uint16_t>(pbuf);
		int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l = BufferIO::Read<uint8_t>(pbuf);
		int s = BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint16_t>(pbuf);
		ClientCard* pc = mainGame->dField.GetCard(c, l, s);
		if (pc->counters.count(type))
			pc->counters[type] += count;
		else pc->counters[type] = count;
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::COUNTER_ADD);
		myswprintf(textBuffer, dataManager.GetSysString(1617), dataManager.GetName(pc->code), count, dataManager.GetCounterName(type));
		pc->is_highlighting = true;
		mainGame->gMutex.lock();
		mainGame->stACMessage->setText(textBuffer);
		mainGame->PopupElement(mainGame->wACMessage, 20);
		mainGame->gMutex.unlock();
		mainGame->WaitFrameSignal(40);
		pc->is_highlighting = false;
		return true;
	}
	case MSG_REMOVE_COUNTER: {
		int type = BufferIO::Read<uint16_t>(pbuf);
		int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l = BufferIO::Read<uint8_t>(pbuf);
		int s = BufferIO::Read<uint8_t>(pbuf);
		int count = BufferIO::Read<uint16_t>(pbuf);
		ClientCard* pc = mainGame->dField.GetCard(c, l, s);
		pc->counters[type] -= count;
		if (pc->counters[type] <= 0)
			pc->counters.erase(type);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::COUNTER_REMOVE);
		myswprintf(textBuffer, dataManager.GetSysString(1618), dataManager.GetName(pc->code), count, dataManager.GetCounterName(type));
		pc->is_highlighting = true;
		mainGame->gMutex.lock();
		mainGame->stACMessage->setText(textBuffer);
		mainGame->PopupElement(mainGame->wACMessage, 20);
		mainGame->gMutex.unlock();
		mainGame->WaitFrameSignal(40);
		pc->is_highlighting = false;
		return true;
	}
	case MSG_ATTACK: {
		int ca = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int la = BufferIO::Read<uint8_t>(pbuf);
		int sa = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.attacker = mainGame->dField.GetCard(ca, la, sa);
		int cd = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int ld = BufferIO::Read<uint8_t>(pbuf);
		int sd = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		float sy;
		if (ld != 0) {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::ATTACK);
			mainGame->dField.attack_target = mainGame->dField.GetCard(cd, ld, sd);
			myswprintf(event_string, dataManager.GetSysString(1619), dataManager.GetName(mainGame->dField.attacker->code),
			           dataManager.GetName(mainGame->dField.attack_target->code));
			float xa = mainGame->dField.attacker->curPos.X;
			float ya = mainGame->dField.attacker->curPos.Y;
			float xd = mainGame->dField.attack_target->curPos.X;
			float yd = mainGame->dField.attack_target->curPos.Y;
			sy = (float)sqrt((xa - xd) * (xa - xd) + (ya - yd) * (ya - yd)) / 2;
			mainGame->atk_t = irr::core::vector3df((xa + xd) / 2, (ya + yd) / 2, 0);
			if (ca == 0)
				mainGame->atk_r = irr::core::vector3df(0, 0, -atan((xd - xa) / (yd - ya)));
			else
				mainGame->atk_r = irr::core::vector3df(0, 0, 3.1415926 - atan((xd - xa) / (yd - ya)));
		} else {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::DIRECT_ATTACK);
			myswprintf(event_string, dataManager.GetSysString(1620), dataManager.GetName(mainGame->dField.attacker->code));
			float xa = mainGame->dField.attacker->curPos.X;
			float ya = mainGame->dField.attacker->curPos.Y;
			float xd = 3.95f;
			float yd = 3.5f;
			if (ca == 0)
				yd = -3.5f;
			sy = (float)sqrt((xa - xd) * (xa - xd) + (ya - yd) * (ya - yd)) / 2;
			mainGame->atk_t = irr::core::vector3df((xa + xd) / 2, (ya + yd) / 2, 0);
			if (ca == 0)
				mainGame->atk_r = irr::core::vector3df(0, 0, -atan((xd - xa) / (yd - ya)));
			else
				mainGame->atk_r = irr::core::vector3df(0, 0, 3.1415926 - atan((xd - xa) / (yd - ya)));
		}
		matManager.GenArrow(sy);
		mainGame->attack_sv = 0;
		mainGame->is_attacking = true;
		mainGame->WaitFrameSignal(40);
		mainGame->is_attacking = false;
		return true;
	}
	case MSG_BATTLE: {
		int ca = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int la = BufferIO::Read<uint8_t>(pbuf);
		int sa = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		int aatk = BufferIO::Read<int32_t>(pbuf);
		int adef = BufferIO::Read<int32_t>(pbuf);
		/*int da = */BufferIO::Read<uint8_t>(pbuf);
		int cd = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int ld = BufferIO::Read<uint8_t>(pbuf);
		int sd = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		int datk = BufferIO::Read<int32_t>(pbuf);
		int ddef = BufferIO::Read<int32_t>(pbuf);
		/*int dd = */BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->gMutex.lock();
		ClientCard* pcard = mainGame->dField.GetCard(ca, la, sa);
		if(aatk != pcard->attack) {
			pcard->attack = aatk;
			myswprintf(pcard->atkstring, L"%d", aatk);
		}
		if(adef != pcard->defense) {
			pcard->defense = adef;
			myswprintf(pcard->defstring, L"%d", adef);
		}
		if(ld) {
			pcard = mainGame->dField.GetCard(cd, ld, sd);
			if(datk != pcard->attack) {
				pcard->attack = datk;
				myswprintf(pcard->atkstring, L"%d", datk);
			}
			if(ddef != pcard->defense) {
				pcard->defense = ddef;
				myswprintf(pcard->defstring, L"%d", ddef);
			}
		}
		mainGame->gMutex.unlock();
		return true;
	}
	case MSG_ATTACK_DISABLED: {
		myswprintf(event_string, dataManager.GetSysString(1621), dataManager.GetName(mainGame->dField.attacker->code));
		return true;
	}
	case MSG_DAMAGE_STEP_START: {
		return true;
	}
	case MSG_DAMAGE_STEP_END: {
		return true;
	}
	case MSG_MISSED_EFFECT: {
		BufferIO::Read<int32_t>(pbuf);
		unsigned int code = BufferIO::Read<int32_t>(pbuf);
		myswprintf(textBuffer, dataManager.GetSysString(1622), dataManager.GetName(code));
		mainGame->AddLog(textBuffer, code);
		return true;
	}
	case MSG_TOSS_COIN: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		wchar_t* pwbuf = textBuffer;
		BufferIO::CopyWStrRef(dataManager.GetSysString(1623), pwbuf, 256);
		for (int i = 0; i < count; ++i) {
			int res = BufferIO::Read<uint8_t>(pbuf);
			*pwbuf++ = L'[';
			BufferIO::CopyWStrRef(dataManager.GetSysString(res ? 60 : 61), pwbuf, 256);
			*pwbuf++ = L']';
		}
		*pwbuf = 0;
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::COIN);
		mainGame->gMutex.lock();
		mainGame->AddLog(textBuffer);
		mainGame->stACMessage->setText(textBuffer);
		mainGame->PopupElement(mainGame->wACMessage, 20);
		mainGame->gMutex.unlock();
		mainGame->WaitFrameSignal(40);
		return true;
	}
	case MSG_TOSS_DICE: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		wchar_t* pwbuf = textBuffer;
		BufferIO::CopyWStrRef(dataManager.GetSysString(1624), pwbuf, 256);
		for (int i = 0; i < count; ++i) {
			int res = BufferIO::Read<uint8_t>(pbuf);
			*pwbuf++ = L'[';
			*pwbuf++ = L'0' + res;
			*pwbuf++ = L']';
		}
		*pwbuf = 0;
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::DICE);
		mainGame->gMutex.lock();
		mainGame->AddLog(textBuffer);
		mainGame->stACMessage->setText(textBuffer);
		mainGame->PopupElement(mainGame->wACMessage, 20);
		mainGame->gMutex.unlock();
		mainGame->WaitFrameSignal(40);
		return true;
	}
	case MSG_ROCK_PAPER_SCISSORS: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->gMutex.lock();
		mainGame->PopupElement(mainGame->wHand);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_HAND_RES: {
		int res = BufferIO::Read<uint8_t>(pbuf);
		if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
			return true;
		mainGame->stHintMsg->setVisible(false);
		int res1 = (res & 0x3) - 1;
		int res2 = ((res >> 2) & 0x3) - 1;
		if(mainGame->dInfo.isFirst)
			mainGame->showcardcode = res1 + (res2 << 16);
		else
			mainGame->showcardcode = res2 + (res1 << 16);
		mainGame->showcarddif = 50;
		mainGame->showcardp = 0;
		mainGame->showcard = 100;
		mainGame->WaitFrameSignal(60);
		return false;
	}
	case MSG_ANNOUNCE_RACE: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		mainGame->dField.announce_count = BufferIO::Read<uint8_t>(pbuf);
		int available = BufferIO::Read<int32_t>(pbuf);
		for(int i = 0, filter = 0x1; i < RACES_COUNT; ++i, filter <<= 1) {
			mainGame->chkRace[i]->setChecked(false);
			if(filter & available)
				mainGame->chkRace[i]->setVisible(true);
			else mainGame->chkRace[i]->setVisible(false);
		}
		if(select_hint)
			myswprintf(textBuffer, L"%ls", dataManager.GetDesc(select_hint));
		else myswprintf(textBuffer, dataManager.GetSysString(563));
		select_hint = 0;
		mainGame->gMutex.lock();
		mainGame->stANRace->setText(textBuffer);
		mainGame->PopupElement(mainGame->wANRace);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_ANNOUNCE_ATTRIB: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		mainGame->dField.announce_count = BufferIO::Read<uint8_t>(pbuf);
		int available = BufferIO::Read<int32_t>(pbuf);
		for(int i = 0, filter = 0x1; i < 7; ++i, filter <<= 1) {
			mainGame->chkAttribute[i]->setChecked(false);
			if(filter & available)
				mainGame->chkAttribute[i]->setVisible(true);
			else mainGame->chkAttribute[i]->setVisible(false);
		}
		if(select_hint)
			myswprintf(textBuffer, L"%ls", dataManager.GetDesc(select_hint));
		else myswprintf(textBuffer, dataManager.GetSysString(562));
		select_hint = 0;
		mainGame->gMutex.lock();
		mainGame->stANAttribute->setText(textBuffer);
		mainGame->PopupElement(mainGame->wANAttribute);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_ANNOUNCE_CARD: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->dField.declare_opcodes.clear();
		for (int i = 0; i < count; ++i)
			mainGame->dField.declare_opcodes.push_back(BufferIO::Read<uint32_t>(pbuf));
		if(select_hint)
			myswprintf(textBuffer, L"%ls", dataManager.GetDesc(select_hint));
		else myswprintf(textBuffer, dataManager.GetSysString(564));
		select_hint = 0;
		mainGame->gMutex.lock();
		mainGame->ebANCard->setText(L"");
		mainGame->stANCard->setText(textBuffer);
		mainGame->dField.UpdateDeclarableList();
		mainGame->PopupElement(mainGame->wANCard);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_ANNOUNCE_NUMBER: {
		/*int player = */mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		int count = BufferIO::Read<uint8_t>(pbuf);
		mainGame->gMutex.lock();
		mainGame->cbANNumber->clear();
		bool quickmode = count <= 12;
		if(quickmode) {
			for(int i = 0; i < 12; ++i) {
				mainGame->btnANNumber[i]->setEnabled(false);
				mainGame->btnANNumber[i]->setPressed(false);
				mainGame->btnANNumber[i]->setVisible(true);
			}
		}
		for (int i = 0; i < count; ++i) {
			int value = BufferIO::Read<int32_t>(pbuf);
			myswprintf(textBuffer, L" % d", value);
			mainGame->cbANNumber->addItem(textBuffer, value);
			if(quickmode) {
				if((value > 12 || value <= 0)) {
					quickmode = false;
				} else {
					mainGame->btnANNumber[value - 1]->setEnabled(true);
				}
			}
		}
		mainGame->cbANNumber->setSelected(0);
		if(quickmode) {
			mainGame->cbANNumber->setVisible(false);
			mainGame->btnANNumberOK->setEnabled(false);
		} else {
			for(int i = 0; i < 12; ++i) {
				mainGame->btnANNumber[i]->setVisible(false);
			}
			mainGame->cbANNumber->setVisible(true);
		}
		if(select_hint)
			myswprintf(textBuffer, L"%ls", dataManager.GetDesc(select_hint));
		else myswprintf(textBuffer, dataManager.GetSysString(565));
		select_hint = 0;
		mainGame->stANNumber->setText(textBuffer);
		mainGame->PopupElement(mainGame->wANNumber);
		mainGame->gMutex.unlock();
		return false;
	}
	case MSG_CARD_HINT: {
		int c = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		unsigned int l = BufferIO::Read<uint8_t>(pbuf);
		int s = BufferIO::Read<uint8_t>(pbuf);
		BufferIO::Read<uint8_t>(pbuf);
		int chtype = BufferIO::Read<uint8_t>(pbuf);
		int value = BufferIO::Read<int32_t>(pbuf);
		ClientCard* pcard = mainGame->dField.GetCard(c, l, s);
		if(!pcard)
			return true;
		if(chtype == CHINT_DESC_ADD) {
			pcard->desc_hints[value]++;
		} else if(chtype == CHINT_DESC_REMOVE) {
			pcard->desc_hints[value]--;
			if(pcard->desc_hints[value] == 0)
				pcard->desc_hints.erase(value);
		} else {
			pcard->cHint = chtype;
			pcard->chValue = value;
			if(chtype == CHINT_TURN) {
				if(value == 0)
					return true;
				if(mainGame->dInfo.isReplay && mainGame->dInfo.isReplaySkiping)
					return true;
				if(pcard->location & LOCATION_ONFIELD)
					pcard->is_highlighting = true;
				mainGame->showcardcode = pcard->code;
				mainGame->showcarddif = 0;
				mainGame->showcardp = value - 1;
				mainGame->showcard = 6;
				mainGame->WaitFrameSignal(30);
				pcard->is_highlighting = false;
				mainGame->showcard = 0;
			}
		}
		return true;
	}
	// 处理玩家提示消息，用于更新玩家的状态提示信息
    case MSG_PLAYER_HINT: {
		// 读取目标玩家编号并转换为本地玩家编号
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		// 读取提示类型（添加或移除提示）
		int chtype = BufferIO::Read<uint8_t>(pbuf);
		// 读取提示值（具体的状态值）
		int value = BufferIO::Read<int32_t>(pbuf);
		// 获取目标玩家的描述提示映射表引用
		auto& player_desc_hints = mainGame->dField.player_desc_hints[player];
		// 特殊处理：当值为CARD_QUESTION且是本地玩家0时，控制墓地查看权限
		if(value == CARD_QUESTION && player == 0) {
			// 如果是添加提示，则禁止查看墓地
			if(chtype == PHINT_DESC_ADD) {
				mainGame->dField.cant_check_grave = true;
			// 如果是移除提示，则允许查看墓地
			} else if(chtype == PHINT_DESC_REMOVE) {
				mainGame->dField.cant_check_grave = false;
			}
		}
		// 其他情况的通用处理
		else if(chtype == PHINT_DESC_ADD) {
			// 增加该提示值的计数
			player_desc_hints[value]++;
		} else if(chtype == PHINT_DESC_REMOVE) {
			// 减少该提示值的计数
			player_desc_hints[value]--;
			// 如果计数归零，则从映射表中移除该提示值
			if(player_desc_hints[value] == 0)
				player_desc_hints.erase(value);
		}
		// 消息处理完成，返回true
		return true;
	}
    // 处理胜利龙等比赛胜利消息，当胜利龙或者WCS奖品卡拥有这种效果且被指定为比赛胜利条件时触发
    case MSG_MATCH_KILL: {
		// 读取指定的卡片密码，存储到match_kill变量中作为当前比赛的胜利条件
		match_kill = BufferIO::Read<int32_t>(pbuf);
		// 消息处理完成，返回true表示处理成功
		return true;
	}
	case MSG_TAG_SWAP: {
		int player = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
		size_t mcount = (size_t)BufferIO::Read<uint8_t>(pbuf);
		size_t ecount = (size_t)BufferIO::Read<uint8_t>(pbuf);
		size_t pcount = (size_t)BufferIO::Read<uint8_t>(pbuf);
		size_t hcount = (size_t)BufferIO::Read<uint8_t>(pbuf);
		int topcode = BufferIO::Read<int32_t>(pbuf);
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			for (auto cit = mainGame->dField.deck[player].begin(); cit != mainGame->dField.deck[player].end(); ++cit) {
				if(player == 0) (*cit)->dPos.Y = 0.4f;
				else (*cit)->dPos.Y = -0.6f;
				(*cit)->dRot = irr::core::vector3df(0, 0, 0);
				(*cit)->is_moving = true;
				(*cit)->aniFrame = 5;
			}
			for (auto cit = mainGame->dField.hand[player].begin(); cit != mainGame->dField.hand[player].end(); ++cit) {
				if(player == 0) (*cit)->dPos.Y = 0.4f;
				else (*cit)->dPos.Y = -0.6f;
				(*cit)->dRot = irr::core::vector3df(0, 0, 0);
				(*cit)->is_moving = true;
				(*cit)->aniFrame = 5;
			}
			for (auto cit = mainGame->dField.extra[player].begin(); cit != mainGame->dField.extra[player].end(); ++cit) {
				if(player == 0) (*cit)->dPos.Y = 0.4f;
				else (*cit)->dPos.Y = -0.6f;
				(*cit)->dRot = irr::core::vector3df(0, 0, 0);
				(*cit)->is_moving = true;
				(*cit)->aniFrame = 5;
			}
			mainGame->WaitFrameSignal(5);
		}
		//
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping)
			mainGame->gMutex.lock();
		if(mainGame->dField.deck[player].size() > mcount) {
			while(mainGame->dField.deck[player].size() > mcount) {
				ClientCard* ccard = *mainGame->dField.deck[player].rbegin();
				mainGame->dField.deck[player].pop_back();
				delete ccard;
			}
		} else {
			while(mainGame->dField.deck[player].size() < mcount) {
				ClientCard* ccard = new ClientCard;
				ccard->controler = player;
				ccard->location = LOCATION_DECK;
				ccard->sequence = (unsigned char)mainGame->dField.deck[player].size();
				mainGame->dField.deck[player].push_back(ccard);
			}
		}
		if(mainGame->dField.hand[player].size() > hcount) {
			while(mainGame->dField.hand[player].size() > hcount) {
				ClientCard* ccard = *mainGame->dField.hand[player].rbegin();
				mainGame->dField.hand[player].pop_back();
				delete ccard;
			}
		} else {
			while(mainGame->dField.hand[player].size() < hcount) {
				ClientCard* ccard = new ClientCard;
				ccard->controler = player;
				ccard->location = LOCATION_HAND;
				ccard->sequence = (unsigned char)mainGame->dField.hand[player].size();
				mainGame->dField.hand[player].push_back(ccard);
			}
		}
		if(mainGame->dField.extra[player].size() > ecount) {
			while(mainGame->dField.extra[player].size() > ecount) {
				ClientCard* ccard = *mainGame->dField.extra[player].rbegin();
				mainGame->dField.extra[player].pop_back();
				delete ccard;
			}
		} else {
			while(mainGame->dField.extra[player].size() < ecount) {
				ClientCard* ccard = new ClientCard;
				ccard->controler = player;
				ccard->location = LOCATION_EXTRA;
				ccard->sequence = (unsigned char)mainGame->dField.extra[player].size();
				mainGame->dField.extra[player].push_back(ccard);
			}
		}
		mainGame->dField.extra_p_count[player] = pcount;
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping)
			mainGame->gMutex.unlock();
		//
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			for (auto cit = mainGame->dField.deck[player].begin(); cit != mainGame->dField.deck[player].end(); ++cit) {
				ClientCard* pcard = *cit;
				mainGame->dField.GetCardLocation(pcard, &pcard->curPos, &pcard->curRot);
				if(player == 0) pcard->curPos.Y += 2.0f;
				else pcard->curPos.Y -= 3.0f;
				mainGame->dField.MoveCard(*cit, 5);
			}
			if(mainGame->dField.deck[player].size())
				(*mainGame->dField.deck[player].rbegin())->code = topcode;
			for (auto cit = mainGame->dField.hand[player].begin(); cit != mainGame->dField.hand[player].end(); ++cit) {
				ClientCard* pcard = *cit;
				pcard->code = BufferIO::Read<int32_t>(pbuf);
				mainGame->dField.GetCardLocation(pcard, &pcard->curPos, &pcard->curRot);
				if(player == 0) pcard->curPos.Y += 2.0f;
				else pcard->curPos.Y -= 3.0f;
				mainGame->dField.MoveCard(*cit, 5);
			}
			for (auto cit = mainGame->dField.extra[player].begin(); cit != mainGame->dField.extra[player].end(); ++cit) {
				ClientCard* pcard = *cit;
				pcard->code = BufferIO::Read<int32_t>(pbuf) & 0x7fffffff;
				mainGame->dField.GetCardLocation(pcard, &pcard->curPos, &pcard->curRot);
				if(player == 0) pcard->curPos.Y += 2.0f;
				else pcard->curPos.Y -= 3.0f;
				mainGame->dField.MoveCard(*cit, 5);
			}
			mainGame->WaitFrameSignal(5);
		}
		mainGame->dField.RefreshCardCountDisplay();
		break;
	}
	// 处理重新加载场地消息，用于同步游戏场地状态
    case MSG_RELOAD_FIELD: {
		// 如果不是回放模式或不处于跳过状态，则锁定图形界面互斥锁
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->gMutex.lock();
		}
		// 清空当前场地所有卡片信息
		mainGame->dField.Clear();
		// 读取当前决斗规则
		mainGame->dInfo.duel_rule = BufferIO::Read<uint8_t>(pbuf);
		int val = 0;
		// 循环处理两名玩家的场地信息
		for(int i = 0; i < 2; ++i) {
			// 获取本地玩家编号
			int p = mainGame->LocalPlayer(i);
			// 读取玩家生命值并更新显示字符串
			mainGame->dInfo.lp[p] = BufferIO::Read<int32_t>(pbuf);
			myswprintf(mainGame->dInfo.strLP[p], L"%d", mainGame->dInfo.lp[p]);
			// 处理怪兽区域(主怪兽区)的卡片
			for(int seq = 0; seq < 7; ++seq) {
				// 读取该位置是否有卡片
				val = BufferIO::Read<uint8_t>(pbuf);
				if(val) {
					// 创建新卡片对象并添加到场地
					ClientCard* ccard = new ClientCard;
					mainGame->dField.AddCard(ccard, p, LOCATION_MZONE, seq);
					// 读取卡片位置信息(正反面、攻击/防守状态等)
					ccard->position = BufferIO::Read<uint8_t>(pbuf);
					// 读取叠放卡片数量
					val = BufferIO::Read<uint8_t>(pbuf);
					if(val) {
						// 为每张叠放卡片创建对象并建立叠放关系
						for(int xyz = 0; xyz < val; ++xyz) {
							ClientCard* xcard = new ClientCard;
							ccard->overlayed.push_back(xcard);
							mainGame->dField.overlay_cards.insert(xcard);
							xcard->overlayTarget = ccard;
							xcard->location = LOCATION_OVERLAY;
							xcard->sequence = (unsigned char)(ccard->overlayed.size() - 1);
							xcard->owner = p;
							xcard->controler = p;
						}
					}
				}
			}
			// 处理魔法陷阱区域的卡片
			for(int seq = 0; seq < 8; ++seq) {
				// 读取该位置是否有卡片
				val = BufferIO::Read<uint8_t>(pbuf);
				if(val) {
					// 创建新卡片对象并添加到场地
					ClientCard* ccard = new ClientCard;
					mainGame->dField.AddCard(ccard, p, LOCATION_SZONE, seq);
					// 读取卡片位置信息
					ccard->position = BufferIO::Read<uint8_t>(pbuf);
				}
			}
			// 处理卡组中的卡片
			val = BufferIO::Read<uint8_t>(pbuf);
			for(int seq = 0; seq < val; ++seq) {
				ClientCard* ccard = new ClientCard;
				mainGame->dField.AddCard(ccard, p, LOCATION_DECK, seq);
			}
			// 处理手牌
			val = BufferIO::Read<uint8_t>(pbuf);
			for(int seq = 0; seq < val; ++seq) {
				ClientCard* ccard = new ClientCard;
				mainGame->dField.AddCard(ccard, p, LOCATION_HAND, seq);
			}
			// 处理墓地中的卡片
			val = BufferIO::Read<uint8_t>(pbuf);
			for(int seq = 0; seq < val; ++seq) {
				ClientCard* ccard = new ClientCard;
				mainGame->dField.AddCard(ccard, p, LOCATION_GRAVE, seq);
			}
			// 处理除外状态的卡片
			val = BufferIO::Read<uint8_t>(pbuf);
			for(int seq = 0; seq < val; ++seq) {
				ClientCard* ccard = new ClientCard;
				mainGame->dField.AddCard(ccard, p, LOCATION_REMOVED, seq);
			}
			// 处理额外卡组中的卡片
			val = BufferIO::Read<uint8_t>(pbuf);
			for(int seq = 0; seq < val; ++seq) {
				ClientCard* ccard = new ClientCard;
				mainGame->dField.AddCard(ccard, p, LOCATION_EXTRA, seq);
			}
			// 读取额外卡组中灵摆卡片的数量
			val = BufferIO::Read<uint8_t>(pbuf);
			mainGame->dField.extra_p_count[p] = val;
		}
		// 刷新所有卡片的显示状态
		mainGame->dField.RefreshAllCards();
		// 读取连锁信息数量
		val = BufferIO::Read<uint8_t>(pbuf); //chains
		// 处理所有连锁信息
		for(int i = 0; i < val; ++i) {
			// 读取连锁卡片的密码
			unsigned int code = BufferIO::Read<int32_t>(pbuf);
			// 读取发动卡片的控制者
			int pcc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			// 读取发动卡片的位置
			unsigned int pcl = BufferIO::Read<uint8_t>(pbuf);
			// 读取发动卡片的序列号
			int pcs = BufferIO::Read<uint8_t>(pbuf);
			// 读取子序列号(用于叠放卡片)
			int subs = BufferIO::Read<uint8_t>(pbuf);
			// 读取处理效果的控制者
			int cc = mainGame->LocalPlayer(BufferIO::Read<uint8_t>(pbuf));
			// 读取处理效果的位置
			unsigned int cl = BufferIO::Read<uint8_t>(pbuf);
			// 读取处理效果的序列号
			int cs = BufferIO::Read<uint8_t>(pbuf);
			// 读取效果描述
			int desc = BufferIO::Read<int32_t>(pbuf);
			// 获取发动效果的卡片对象
			ClientCard* pcard = mainGame->dField.GetCard(pcc, pcl, pcs, subs);
			// 设置当前连锁信息
			mainGame->dField.current_chain.chain_card = pcard;
			mainGame->dField.current_chain.code = code;
			mainGame->dField.current_chain.desc = desc;
			mainGame->dField.current_chain.controler = cc;
			mainGame->dField.current_chain.location = cl;
			mainGame->dField.current_chain.sequence = cs;
			// 获取连锁效果的显示位置
			mainGame->dField.GetChainLocation(cc, cl, cs, &mainGame->dField.current_chain.chain_pos);
			mainGame->dField.current_chain.solved = false;
			// 计算连锁编号，用于确定显示位置
			int chc = 0;
			for(auto chit = mainGame->dField.chains.begin(); chit != mainGame->dField.chains.end(); ++chit) {
				if(cl == 0x10 || cl == 0x20) {
					if(chit->controler == cc && chit->location == cl)
						chc++;
				} else {
					if(chit->controler == cc && chit->location == cl && chit->sequence == cs)
						chc++;
				}
			}
			// 根据（手卡和那以外）位置调整连锁效果的显示位置
			if(cl == LOCATION_HAND)
				mainGame->dField.current_chain.chain_pos.X += 0.35f;
			else
				mainGame->dField.current_chain.chain_pos.Y += chc * 0.25f;
			// 将当前连锁添加到连锁列表中
			mainGame->dField.chains.push_back(mainGame->dField.current_chain);
		}
		// 如果存在连锁，则更新事件字符串显示
		if(val) {
			myswprintf(event_string, dataManager.GetSysString(1609), dataManager.GetName(mainGame->dField.current_chain.code));
			mainGame->dField.last_chain = true;
		}
		// 如果不是回放模式或不处于跳过状态，则解锁图形界面互斥锁
		if(!mainGame->dInfo.isReplay || !mainGame->dInfo.isReplaySkiping) {
			mainGame->gMutex.unlock();
		}
		break;
	}
	}
	return true;
}
/**
 * @brief 切换场地状态
 *
 * 该函数用于标记当前正在进行场地切换操作，
 * 通过设置is_swapping标志位来表示场地正在交换中。
 *
 * @note 该函数不接受任何参数，无返回值
 */
void DuelClient::SwapField() {
	is_swapping = true;
}

/**
 * @brief 设置响应数据为32位整数
 *
 * 将传入的32位整数复制到响应缓冲区中，并设置响应数据长度
 *
 * @param respI 要设置的32位整数响应数据
 */
void DuelClient::SetResponseI(int32_t respI) {
	// 将整数数据拷贝到响应缓冲区
	std::memcpy(response_buf, &respI, sizeof respI);
	// 设置响应数据长度为整数大小
	response_len = sizeof respI;
}

/**
 * @brief 设置客户端的响应数据缓冲区
 * @param respB 指向响应数据的指针
 * @param len 响应数据的长度
 *
 * 该函数将传入的响应数据复制到内部缓冲区中，
 * 并更新响应数据的实际长度。
 */
void DuelClient::SetResponseB(void* respB, size_t len) {
	// 限制复制长度不超过缓冲区最大容量
	if (len > SIZE_RETURN_VALUE)
		len = SIZE_RETURN_VALUE;

	// 将响应数据复制到内部缓冲区
	std::memcpy(response_buf, respB, len);

	// 更新响应数据长度
	response_len = len;
}

/**
 * @brief 发送游戏响应消息到服务器或单机模式处理函数
 *
 * 根据当前游戏消息类型清理相应的界面状态，并将响应数据发送到服务器
 * 或设置给单机模式处理。主要用于处理用户在各种选择界面的操作结果。
 *
 * @note 该函数无参数且无返回值
 */
void DuelClient::SendResponse() {
	// 根据当前消息类型清理对应的界面元素和选择状态
	switch(mainGame->dInfo.curMsg) {
	// 处理战斗阶段命令选择消息
	case MSG_SELECT_BATTLECMD: {
		mainGame->dField.ClearCommandFlag();
		mainGame->btnM2->setVisible(false);
		mainGame->btnEP->setVisible(false);
		break;
	}
	// 处理空闲阶段命令选择消息
	case MSG_SELECT_IDLECMD: {
		mainGame->dField.ClearCommandFlag();
		mainGame->btnBP->setVisible(false);
		mainGame->btnEP->setVisible(false);
		mainGame->btnShuffle->setVisible(false);
		break;
	}
	// 处理各种卡片选择相关消息
	case MSG_SELECT_CARD:
	case MSG_SELECT_UNSELECT_CARD:
	case MSG_SELECT_TRIBUTE:
	case MSG_SELECT_SUM:
	case MSG_SELECT_COUNTER: {
		mainGame->dField.ClearSelect();
		break;
	}
	// 处理连锁选择消息
	case MSG_SELECT_CHAIN: {
		mainGame->dField.ClearChainSelect();
		break;
	}
	}

	// 根据游戏模式将响应数据发送到相应的目标
	if(mainGame->dInfo.isSingleMode) {
		// 单机模式：设置响应数据并触发信号
		SingleMode::SetResponse(response_buf, response_len);
		mainGame->singleSignal.Set();
	} else {
		// 联机模式：重置计时器并将响应数据发送到服务器
		mainGame->dInfo.time_player = 2;
		SendBufferToServer(CTOS_RESPONSE, response_buf, response_len);
	}
}
void DuelClient::SendUpdateDeck(const Deck& deck) {
	std::vector<unsigned char> deckbuf;
	deckbuf.reserve(1024);
	BufferIO::VectorWrite<int32_t>(deckbuf, static_cast<int32_t>(deck.main.size() + deck.extra.size()));
	BufferIO::VectorWrite<int32_t>(deckbuf, static_cast<int32_t>(deck.side.size()));
	for (const auto& card: deck.main)
		BufferIO::VectorWrite<uint32_t>(deckbuf, card->first);
	for (const auto& card: deck.extra)
		BufferIO::VectorWrite<uint32_t>(deckbuf, card->first);
	for (const auto& card: deck.side)
		BufferIO::VectorWrite<uint32_t>(deckbuf, card->first);
	SendBufferToServer(CTOS_UPDATE_DECK, deckbuf.data(), deckbuf.size());
}
/**
 * @brief 开始刷新局域网主机列表
 *
 * 此函数用于启动局域网内主机的发现过程。它会初始化网络套接字并广播一个请求，
 * 等待其他主机响应以构建可用主机列表。
 *
 * 主要功能包括：
 * - 检查是否正在刷新，防止重复调用
 * - 清空旧的主机列表和相关状态
 * - 创建事件循环用于监听响应
 * - 绑定接收响应的UDP端口（7921）
 * - 启动独立线程处理事件循环
 * - 构造并向局域网广播主机请求（端口7920）
 *
 * @note 此函数不接受参数也不返回值，所有操作基于对象内部状态
 */
void DuelClient::BeginRefreshHost() {
	// 防止重复刷新
	if(is_refreshing)
		return;
	is_refreshing = true;

	// 禁用刷新按钮并清空现有列表
	mainGame->btnLanRefresh->setEnabled(false);
	mainGame->lstHostList->clear();
	remotes.clear();
	hosts.clear();

	// 创建事件基础结构用于异步I/O处理
	event_base* broadev = event_base_new();

#ifdef _IRR_ANDROID_PLATFORM_
	// Android平台下获取本地IP地址
	int ipaddr = irr::android::getLocalAddr(mainGame->appMain);
	if (ipaddr == -1) {
		return;
	}
#else
	// 其他平台通过主机名解析本地地址
	char hname[256];
	gethostname(hname, 256);
	hostent* host = gethostbyname(hname);
	if(!host)
		return;
#endif

	// 创建UDP套接字用于接收主机响应
	SOCKET reply = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	sockaddr_in reply_addr;
	std::memset(&reply_addr, 0, sizeof reply_addr);
	reply_addr.sin_family = AF_INET;
	reply_addr.sin_port = htons(7921);         // 监听端口7921
	reply_addr.sin_addr.s_addr = 0;            // 绑定到所有接口
	if(bind(reply, (sockaddr*)&reply_addr, sizeof(reply_addr)) == SOCKET_ERROR) {
		closesocket(reply);
		return;
	}

	// 设置超时时间，并注册事件处理器
	timeval timeout = {3, 0};  // 3秒超时
	resp_event = event_new(broadev, reply, EV_TIMEOUT | EV_READ | EV_PERSIST, BroadcastReply, broadev);
	event_add(resp_event, &timeout);

	// 启动新线程运行事件循环
	std::thread(RefreshThread, broadev).detach();

	// 准备发送广播请求的数据包
	SOCKADDR_IN local;
	local.sin_family = AF_INET;
	local.sin_port = htons(7922);              // 发送源端口7922
	SOCKADDR_IN sockTo;
	sockTo.sin_addr.s_addr = htonl(INADDR_BROADCAST);  // 广播地址
	sockTo.sin_family = AF_INET;
	sockTo.sin_port = htons(7920);             // 目标端口7920

	HostRequest hReq;
	hReq.identifier = NETWORK_CLIENT_ID;

#ifdef _IRR_ANDROID_PLATFORM_
	// Android平台发送单次广播请求
	local.sin_addr.s_addr = ipaddr;
	SOCKET sSend = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	if (sSend == INVALID_SOCKET)
		return;
	int opt = TRUE;
	setsockopt(sSend, SOL_SOCKET, SO_BROADCAST, (const char*) &opt, sizeof opt);
	if (bind(sSend, (sockaddr*) &local, sizeof(sockaddr)) == SOCKET_ERROR) {
		closesocket(sSend);
		return;
	}
	sendto(sSend, (const char*) &hReq, sizeof(HostRequest), 0, (sockaddr*) &sockTo, sizeof(sockaddr));
	closesocket(sSend);
#else
	// 其他平台遍历多个本地地址发送广播请求
	for(int i = 0; i < 8; ++i) {
		if(host->h_addr_list[i] == 0)
			break;
		unsigned int local_addr = 0;
		std::memcpy(&local_addr, host->h_addr_list[i], sizeof local_addr);
		local.sin_addr.s_addr = local_addr;
		SOCKET sSend = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
		if(sSend == INVALID_SOCKET)
			break;
		int opt = TRUE;
		setsockopt(sSend, SOL_SOCKET, SO_BROADCAST, (const char*)&opt, sizeof opt);
		if(bind(sSend, (sockaddr*)&local, sizeof(sockaddr)) == SOCKET_ERROR) {
			closesocket(sSend);
			break;
		}
		sendto(sSend, (const char*)&hReq, sizeof(HostRequest), 0, (sockaddr*)&sockTo, sizeof(sockaddr));
		closesocket(sSend);
	}
#endif
}

/**
 * @brief 刷新线程处理函数
 * @param broadev 事件基础结构体指针，用于事件循环处理
 * @return 返回0表示执行成功
 *
 * 该函数负责处理刷新线程的事件循环，包括事件分发、资源清理等操作
 */
int DuelClient::RefreshThread(event_base* broadev) {
	// 启动事件循环，处理网络事件
	event_base_dispatch(broadev);

	// 获取事件关联的套接字描述符并关闭连接
	evutil_socket_t fd;
	event_get_assignment(resp_event, 0, &fd, 0, 0, 0);
	evutil_closesocket(fd);

	// 释放事件和事件基础结构体资源
	event_free(resp_event);
	event_base_free(broadev);

	// 更新刷新状态标志
	is_refreshing = false;
	return 0;
}

/**
 * @brief 广播回复处理函数，用于处理局域网中其他主机广播的回应。
 *
 * 当监听到网络事件（如超时或可读事件）时被调用。如果是超时事件，则关闭套接字并终止事件循环；
 * 如果是可读事件，则接收来自远程主机的数据包，并根据协议规则解析、验证后更新本地主机列表。
 *
 * @param fd 触发事件的套接字描述符
 * @param events 发生的事件类型（EV_TIMEOUT 或 EV_READ）
 * @param arg 指向 event_base 的指针，用于控制事件循环
 */
void DuelClient::BroadcastReply(evutil_socket_t fd, short events, void * arg) {
	// 处理超时事件：关闭套接字并退出事件循环
	if(events & EV_TIMEOUT) {
		evutil_closesocket(fd);
		event_base_loopbreak((event_base*)arg);
		if(!is_closing)
			mainGame->btnLanRefresh->setEnabled(true);
	}
	// 处理可读事件：接收并解析广播数据包
	else if(events & EV_READ) {
		sockaddr_in bc_addr;
		socklen_t sz = sizeof(sockaddr_in);
		char buf[256];
		/*int ret = */recvfrom(fd, buf, 256, 0, (sockaddr*)&bc_addr, &sz);
		HostPacket packet;
		std::memcpy(&packet, buf, sizeof packet);
		HostPacket* pHP = &packet;

		// 验证数据包合法性及版本兼容性
		if(is_closing || pHP->identifier != NETWORK_SERVER_ID)
			return;
		if(pHP->version != PRO_VERSION)
			return;

		// 构造远程地址信息并检查是否已存在
		unsigned int ipaddr = bc_addr.sin_addr.s_addr;
		const auto remote = std::make_pair(ipaddr, pHP->port);
		if(remotes.find(remote) == remotes.end()) {
			// 添加新发现的主机到列表中
			mainGame->gMutex.lock();
			remotes.insert(remote);
			pHP->ipaddr = ipaddr;
			hosts.push_back(*pHP);

			// 构建显示字符串并添加到界面列表中
			std::wstring hoststr;
			hoststr.append(L"[");
			hoststr.append(deckManager.GetLFListName(pHP->host.lflist));
			hoststr.append(L"][");
			hoststr.append(dataManager.GetSysString(pHP->host.rule + 1481));
			hoststr.append(L"][");
			hoststr.append(dataManager.GetSysString(pHP->host.mode + 1244));
			hoststr.append(L"][");

			// 判断游戏规则是否为默认设置
			if(pHP->host.draw_count == 1 && pHP->host.start_hand == 5 && pHP->host.start_lp == 8000
			        && !pHP->host.no_check_deck && !pHP->host.no_shuffle_deck
			        && pHP->host.duel_rule == DEFAULT_DUEL_RULE)
				hoststr.append(dataManager.GetSysString(1247)); // 默认规则
			else
				hoststr.append(dataManager.GetSysString(1248)); // 自定义规则

			hoststr.append(L"]");

			// 获取并附加房间名称
			wchar_t gamename[20];
			BufferIO::CopyCharArray(pHP->name, gamename);
			hoststr.append(gamename);

			mainGame->lstHostList->addItem(hoststr.c_str());
			mainGame->gMutex.unlock();
		}
	}
}

}

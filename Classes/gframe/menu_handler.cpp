#include "config.h"
#include "menu_handler.h"
#include "netserver.h"
#include "duelclient.h"
#include "deck_manager.h"
#include "replay_mode.h"
#include "single_mode.h"
#include "image_manager.h"
#include "sound_manager.h"
#include "game.h"

namespace ygo {

void UpdateDeck() {
    char linebuf[256];
	BufferIO::CopyWStr(mainGame->cbCategorySelect->getItem(mainGame->cbCategorySelect->getSelected()), mainGame->gameConf.lastcategory, 64);
	BufferIO::EncodeUTF8(mainGame->gameConf.lastcategory, linebuf);
    android::setLastCategory(mainGame->appMain, linebuf);

	BufferIO::CopyWStr(mainGame->cbDeckSelect->getItem(mainGame->cbDeckSelect->getSelected()), mainGame->gameConf.lastdeck, 64);
	BufferIO::EncodeUTF8(mainGame->gameConf.lastdeck, linebuf);
	android::setLastDeck(mainGame->appMain, linebuf);
		
	unsigned char deckbuf[1024];
	auto pdeck = deckbuf;
	BufferIO::WriteInt32(pdeck, deckManager.current_deck.main.size() + deckManager.current_deck.extra.size());
	BufferIO::WriteInt32(pdeck, deckManager.current_deck.side.size());
	for(size_t i = 0; i < deckManager.current_deck.main.size(); ++i)
		BufferIO::WriteInt32(pdeck, deckManager.current_deck.main[i]->first);
	for(size_t i = 0; i < deckManager.current_deck.extra.size(); ++i)
		BufferIO::WriteInt32(pdeck, deckManager.current_deck.extra[i]->first);
	for(size_t i = 0; i < deckManager.current_deck.side.size(); ++i)
		BufferIO::WriteInt32(pdeck, deckManager.current_deck.side[i]->first);
	DuelClient::SendBufferToServer(CTOS_UPDATE_DECK, deckbuf, pdeck - deckbuf);
}

void ShowHostPrepareDeckManage() {
    mainGame->RefreshCategoryDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect, false);
    mainGame->cbCategorySelect->setSelected(mainGame->deckBuilder.prev_category);
    mainGame->RefreshDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect);
    mainGame->cbDeckSelect->setSelected(mainGame->deckBuilder.prev_deck);
    irr::gui::IGUIListBox* lstCategories = mainGame->lstCategories;
    lstCategories->clear();
    lstCategories->addItem(dataManager.GetSysString(1451));
    lstCategories->addItem(dataManager.GetSysString(1452));
    lstCategories->addItem(dataManager.GetSysString(1453));
    FileSystem::TraversalDir(L"./deck", [lstCategories](const wchar_t* name, bool isdir) {
        if(isdir) {
            lstCategories->addItem(name);
        }
    });
	lstCategories->setSelected(mainGame->deckBuilder.prev_category);
    mainGame->deckBuilder.RefreshDeckList();
	mainGame->lstDecks->setSelected(mainGame->deckBuilder.prev_deck);
    mainGame->btnNewCategory->setEnabled(false);
    mainGame->btnRenameCategory->setEnabled(false);
    mainGame->btnDeleteCategory->setEnabled(false);
    mainGame->btnNewDeck->setEnabled(false);
    mainGame->btnRenameDeck->setEnabled(false);
    mainGame->btnDMDeleteDeck->setEnabled(false);
    mainGame->btnMoveDeck->setEnabled(false);
    mainGame->btnCopyDeck->setEnabled(false);
    mainGame->PopupElement(mainGame->wDeckManage);
}


void ChangeHostPrepareDeckCategory(int catesel) {
    mainGame->RefreshDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect);
    mainGame->cbDeckSelect->setSelected(0);
    mainGame->deckBuilder.is_modified = false;
    mainGame->deckBuilder.prev_category = catesel;
    mainGame->deckBuilder.prev_deck = 0;
}

bool MenuHandler::OnEvent(const irr::SEvent& event) {
	if(mainGame->dField.OnCommonEvent(event))
		return false;
#ifdef _IRR_ANDROID_PLATFORM_
	irr::SEvent transferEvent;
	if (irr::android::TouchEventTransferAndroid::OnTransferCommon(event, false)) {
		return true;
	}
#endif
	switch(event.EventType) {
	case irr::EET_GUI_EVENT: {
		irr::gui::IGUIElement* caller = event.GUIEvent.Caller;
		s32 id = caller->getID();
		if(mainGame->wQuery->isVisible() && id != BUTTON_YES && id != BUTTON_NO) {
			mainGame->wQuery->getParent()->bringToFront(mainGame->wQuery);
			break;
		}
		if(mainGame->wReplaySave->isVisible() && id != BUTTON_REPLAY_SAVE && id != BUTTON_REPLAY_CANCEL) {
			mainGame->wReplaySave->getParent()->bringToFront(mainGame->wReplaySave);
			break;
		}
		switch(event.GUIEvent.EventType) {
		case irr::gui::EGET_BUTTON_CLICKED: {
			if(id < 110)
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::SOUND_MENU);
			else
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
			switch(id) {
             case BUTTON_HP_DECK_SELECT: {
                 if (!mainGame->wQuery->isVisible()) {
                     ShowHostPrepareDeckManage();
                 }
                 break;
             }
             case BUTTON_CLOSE_DECKMANAGER: {
                 mainGame->HideElement(mainGame->wDeckManage);
                 break;
             }
			case BUTTON_MODE_EXIT: {
				mainGame->soundManager->StopBGM();
				mainGame->SaveConfig();
				mainGame->OnGameClose();
				break;
			}
			case BUTTON_LAN_MODE: {
				mainGame->btnCreateHost->setEnabled(true);
				mainGame->btnJoinHost->setEnabled(true);
				mainGame->btnJoinCancel->setEnabled(true);
				mainGame->HideElement(mainGame->wMainMenu);
				mainGame->ShowElement(mainGame->wLanWindow);
				break;
			}
			case BUTTON_JOIN_HOST: {
				mainGame->HideElement(mainGame->wDeckManage);
				bot_mode = false;
				mainGame->TrimText(mainGame->ebJoinHost);
				mainGame->TrimText(mainGame->ebJoinPort);
				char ip[20];
				const wchar_t* pstr = mainGame->ebJoinHost->getText();
				BufferIO::CopyWStr(pstr, ip, 16);
				unsigned int remote_addr = htonl(inet_addr(ip));
				if(remote_addr == -1) {
					char hostname[100];
					char port[6];
					BufferIO::CopyWStr(pstr, hostname, 100);
					BufferIO::CopyWStr(mainGame->ebJoinPort->getText(), port, 6);
					struct evutil_addrinfo hints;
					struct evutil_addrinfo *answer = NULL;
					memset(&hints, 0, sizeof(hints));
					hints.ai_family = AF_INET;
					hints.ai_socktype = SOCK_STREAM;
					hints.ai_protocol = IPPROTO_TCP;
					hints.ai_flags = EVUTIL_AI_ADDRCONFIG;
					int status = evutil_getaddrinfo(hostname, port, &hints, &answer);
					if(status != 0) {
						mainGame->gMutex.lock();
						mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
						mainGame->addMessageBox(L"", dataManager.GetSysString(1412));
						mainGame->gMutex.unlock();
						break;
					} else {
						sockaddr_in * sin = ((struct sockaddr_in *)answer->ai_addr);
						evutil_inet_ntop(AF_INET, &(sin->sin_addr), ip, 20);
						remote_addr = htonl(inet_addr(ip));
						evutil_freeaddrinfo(answer);
					}
				}
				unsigned int remote_port = _wtoi(mainGame->ebJoinPort->getText());
				BufferIO::CopyWStr(pstr, mainGame->gameConf.lasthost, 100);
				BufferIO::CopyWStr(mainGame->ebJoinPort->getText(), mainGame->gameConf.lastport, 20);
				if(DuelClient::StartClient(remote_addr, remote_port, false)) {
					mainGame->btnCreateHost->setEnabled(false);
					mainGame->btnJoinHost->setEnabled(false);
					mainGame->btnJoinCancel->setEnabled(false);
				}
				break;
			}
			case BUTTON_JOIN_CANCEL: {
				mainGame->HideElement(mainGame->wDeckManage);
				mainGame->HideElement(mainGame->wLanWindow);
				mainGame->ShowElement(mainGame->wMainMenu);
				if(exit_on_return)
					mainGame->OnGameClose();
				break;
			}
			case BUTTON_LAN_REFRESH: {
				DuelClient::BeginRefreshHost();
				break;
			}
			case BUTTON_CREATE_HOST: {
				mainGame->btnHostConfirm->setEnabled(true);
				mainGame->btnHostCancel->setEnabled(true);
				mainGame->HideElement(mainGame->wLanWindow);
				mainGame->ShowElement(mainGame->wCreateHost);
				break;
			}
			case BUTTON_HOST_CONFIRM: {
				bot_mode = false;
				BufferIO::CopyWStr(mainGame->ebServerName->getText(), mainGame->gameConf.gamename, 20);
				if(!NetServer::StartServer(mainGame->gameConf.serverport)) {
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));
					break;
				}
				if(!DuelClient::StartClient(0x7f000001, mainGame->gameConf.serverport)) {
					NetServer::StopServer();
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));
					break;
				}
				mainGame->btnHostConfirm->setEnabled(false);
				mainGame->btnHostCancel->setEnabled(false);
				break;
			}
			case BUTTON_HOST_CANCEL: {
				mainGame->btnCreateHost->setEnabled(true);
				mainGame->btnJoinHost->setEnabled(true);
				mainGame->btnJoinCancel->setEnabled(true);
				mainGame->HideElement(mainGame->wCreateHost);
				mainGame->ShowElement(mainGame->wLanWindow);
				break;
			}
			case BUTTON_HP_DUELIST: {
				mainGame->HideElement(mainGame->wDeckManage);
				mainGame->cbCategorySelect->setEnabled(true);
				mainGame->cbDeckSelect->setEnabled(true);
				mainGame->btnHostDeckSelect->setEnabled(true);
				DuelClient::SendPacketToServer(CTOS_HS_TODUELIST);
				break;
			}
			case BUTTON_HP_OBSERVER: {
				mainGame->HideElement(mainGame->wDeckManage);
				DuelClient::SendPacketToServer(CTOS_HS_TOOBSERVER);
				break;
			}
			case BUTTON_HP_KICK: {
				int id = 0;
				while(id < 4) {
					if(mainGame->btnHostPrepKick[id] == caller)
						break;
					id++;
				}
				CTOS_Kick csk;
				csk.pos = id;
				DuelClient::SendPacketToServer(CTOS_HS_KICK, csk);
				break;
			}
			case BUTTON_HP_READY: {
				if(mainGame->cbCategorySelect->getSelected() == -1 || mainGame->cbDeckSelect->getSelected() == -1 ||
					!deckManager.LoadDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect)) {
					mainGame->gMutex.lock();
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->env->addMessageBox(L"", dataManager.GetSysString(1406));
					mainGame->gMutex.unlock();
					break;
				}
				UpdateDeck();
				DuelClient::SendPacketToServer(CTOS_HS_READY);
				mainGame->HideElement(mainGame->wDeckManage);
				mainGame->cbCategorySelect->setEnabled(false);
				mainGame->cbDeckSelect->setEnabled(false);
                mainGame->btnHostDeckSelect->setEnabled(false);
				break;
			}
			case BUTTON_HP_NOTREADY: {
				DuelClient::SendPacketToServer(CTOS_HS_NOTREADY);
                mainGame->HideElement(mainGame->wDeckManage);
				mainGame->cbCategorySelect->setEnabled(true);
				mainGame->cbDeckSelect->setEnabled(true);
                mainGame->btnHostDeckSelect->setEnabled(true);
				break;
			}
			case BUTTON_HP_START: {
				DuelClient::SendPacketToServer(CTOS_HS_START);
                mainGame->HideElement(mainGame->wDeckManage);
				break;
			}
			case BUTTON_HP_CANCEL: {
				DuelClient::StopClient();
				mainGame->btnCreateHost->setEnabled(true);
				mainGame->btnJoinHost->setEnabled(true);
				mainGame->btnJoinCancel->setEnabled(true);
				mainGame->btnStartBot->setEnabled(true);
				mainGame->btnBotCancel->setEnabled(true);
				mainGame->HideElement(mainGame->wDeckManage);
				mainGame->HideElement(mainGame->wHostPrepare);
				if(bot_mode)
					mainGame->ShowElement(mainGame->wSinglePlay);
				else
					mainGame->ShowElement(mainGame->wLanWindow);
				mainGame->wChat->setVisible(false);
				mainGame->SaveConfig();
				if(exit_on_return)
					mainGame->OnGameClose();
				break;
			}
			case BUTTON_REPLAY_MODE: {
				mainGame->HideElement(mainGame->wMainMenu);
				mainGame->ShowElement(mainGame->wReplay);
				mainGame->ebRepStartTurn->setText(L"1");
				mainGame->stReplayInfo->setText(L"");
				mainGame->RefreshReplay();
				break;
			}
			case BUTTON_SINGLE_MODE: {
				mainGame->HideElement(mainGame->wMainMenu);
				mainGame->ShowElement(mainGame->wSinglePlay);
				mainGame->RefreshSingleplay();
				mainGame->RefreshBot();
				break;
			}
			case BUTTON_LOAD_REPLAY: {
					if(mainGame->lstReplayList->getSelected() == -1)
						break;
					if(!ReplayMode::cur_replay.OpenReplay(mainGame->lstReplayList->getListItem(mainGame->lstReplayList->getSelected())))
						break;
				mainGame->ClearCardInfo();
				mainGame->imgCard->setScaleImage(true);
				mainGame->wCardImg->setVisible(true);
				mainGame->wInfos->setVisible(true);
				mainGame->wPallet->setVisible(true);
				mainGame->imgChat->setVisible(false);
				mainGame->wReplay->setVisible(true);
				mainGame->wReplayControl->setVisible(true);
				mainGame->btnReplayStart->setVisible(false);
				mainGame->btnReplayPause->setVisible(true);
				mainGame->btnReplayStep->setVisible(false);
				mainGame->btnReplayUndo->setVisible(false);
				mainGame->wPhase->setVisible(true);
				mainGame->dField.Clear();
				mainGame->HideElement(mainGame->wReplay);
				mainGame->device->setEventReceiver(&mainGame->dField);
				unsigned int start_turn = _wtoi(mainGame->ebRepStartTurn->getText());
				if(start_turn == 1)
					start_turn = 0;
				ReplayMode::StartReplay(start_turn);
				break;
			}
			case BUTTON_DELETE_REPLAY: {
				int sel = mainGame->lstReplayList->getSelected();
				if(sel == -1)
					break;
				mainGame->gMutex.lock();
				wchar_t textBuffer[256];
				myswprintf(textBuffer, L"%ls\n%ls", mainGame->lstReplayList->getListItem(sel), dataManager.GetSysString(1363));
				mainGame->SetStaticText(mainGame->stQMessage, 370 * mainGame->xScale, mainGame->guiFont, textBuffer);
				mainGame->PopupElement(mainGame->wQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				prev_sel = sel;
				break;
			}
				case BUTTON_SHARE_REPLAY: {
					int sel = mainGame->lstReplayList->getSelected();
					if(sel == -1)
						break;
					mainGame->gMutex.lock();
                    char name[1024];
					BufferIO::EncodeUTF8(mainGame->lstReplayList->getListItem(sel), name);
					mainGame->gMutex.unlock();
					prev_operation = id;
					prev_sel = sel;
#ifdef _IRR_ANDROID_PLATFORM_
					ALOGD("1share replay file=%s", name);
					android::OnShareFile(mainGame->appMain, "yrp", name);
					ALOGD("2after share replay file:index=%d", sel);
#endif
					break;
				}
			case BUTTON_RENAME_REPLAY: {
				int sel = mainGame->lstReplayList->getSelected();
				if(sel == -1)
					break;
				mainGame->gMutex.lock();
				mainGame->wReplaySave->setText(dataManager.GetSysString(1364));
				mainGame->ebRSName->setText(mainGame->lstReplayList->getListItem(sel));
				mainGame->PopupElement(mainGame->wReplaySave);
				mainGame->gMutex.unlock();
				prev_operation = id;
				prev_sel = sel;
				break;
			}
			case BUTTON_CANCEL_REPLAY: {
				mainGame->HideElement(mainGame->wReplay);
				mainGame->ShowElement(mainGame->wMainMenu);
				break;
			}
			case BUTTON_EXPORT_DECK: {
				if(mainGame->lstReplayList->getSelected() == -1)
					break;
				Replay replay;
				wchar_t ex_filename[256];
				wchar_t namebuf[4][20];
				wchar_t filename[256];
				myswprintf(ex_filename, L"%ls", mainGame->lstReplayList->getListItem(mainGame->lstReplayList->getSelected()));
				if(!replay.OpenReplay(ex_filename))
					break;
				const ReplayHeader& rh = replay.pheader;
				if(rh.flag & REPLAY_SINGLE_MODE)
					break;
				int max = (rh.flag & REPLAY_TAG) ? 4 : 2;
				//player name
				for(int i = 0; i < max; ++i)
					replay.ReadName(namebuf[i]);
				//skip pre infos
				for(int i = 0; i < 4; ++i)
					replay.ReadInt32();
				//deck
				for(int i = 0; i < max; ++i) {
					int main = replay.ReadInt32();
					Deck tmp_deck;
					for (int j = 0; j < main; ++j) {
						auto card = dataManager.GetCodePointer(replay.ReadInt32());
						if (card != dataManager.datas_end)
							tmp_deck.main.push_back(card);
					}
					int extra = replay.ReadInt32();
					for (int j = 0; j < extra; ++j) {
						auto card = dataManager.GetCodePointer(replay.ReadInt32());
						if (card != dataManager.datas_end)
							tmp_deck.extra.push_back(card);
					}
					FileSystem::SafeFileName(namebuf[i]);
					myswprintf(filename, L"deck/%ls-%d %ls.ydk", ex_filename, i + 1, namebuf[i]);
					deckManager.SaveDeck(tmp_deck, filename);
				}
				mainGame->stACMessage->setText(dataManager.GetSysString(1335));
				mainGame->PopupElement(mainGame->wACMessage, 20);
				break;
			}
			case BUTTON_BOT_START: {
				int sel = mainGame->lstBotList->getSelected();
				if(sel == -1)
					break;
				bot_mode = true;
#ifdef _WIN32
				if(!NetServer::StartServer(mainGame->gameConf.serverport)) {
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));
					break;
				}
				if(!DuelClient::StartClient(0x7f000001, mainGame->gameConf.serverport)) {
					NetServer::StopServer();
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));
					break;
				}
				STARTUPINFOW si;
				PROCESS_INFORMATION pi;
				ZeroMemory(&si, sizeof(si));
				si.cb = sizeof(si);
				ZeroMemory(&pi, sizeof(pi));
				wchar_t cmd[MAX_PATH];
				wchar_t arg1[512];
				if(mainGame->botInfo[sel].select_deckfile) {
					wchar_t botdeck[256];
					deckManager.GetDeckFile(botdeck, mainGame->cbBotDeckCategory, mainGame->cbBotDeck);
					myswprintf(arg1, L"%ls DeckFile='%ls'", mainGame->botInfo[sel].command, botdeck);
				}
				else
					myswprintf(arg1, L"%ls", mainGame->botInfo[sel].command);
				int flag = 0;
				flag += (mainGame->chkBotHand->isChecked() ? 0x1 : 0);
				myswprintf(cmd, L"Bot.exe \"%ls\" %d %d", arg1, flag, mainGame->gameConf.serverport);
				if(!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi))
				{
					NetServer::StopServer();
					break;
				}
#elif defined(_IRR_ANDROID_PLATFORM_)
				char args[512];
				wchar_t warg1[512];
				if(mainGame->botInfo[sel].select_deckfile) {
					wchar_t botdeck[256];
					deckManager.GetDeckFile(botdeck, mainGame->cbBotDeckCategory, mainGame->cbBotDeck);
					myswprintf(warg1, L"%ls DeckFile='%ls'", mainGame->botInfo[sel].command, botdeck);
				}
				else
					myswprintf(warg1, L"%ls", mainGame->botInfo[sel].command);
				char arg1[512];
				BufferIO::EncodeUTF8(warg1, arg1);
				char arg2[32];
				arg2[0]=0;
				if(mainGame->chkBotHand->isChecked())
					sprintf(arg2, " Hand=1");
				char arg3[32];
				sprintf(arg3, " Port=%d", mainGame->gameConf.serverport);
				sprintf(args, "%s%s%s", arg1, arg2, arg3);
				android::runWindbot(mainGame->appMain, args);
				if(!NetServer::StartServer(mainGame->gameConf.serverport)) {
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));	
					break;
					}
				if(!DuelClient::StartClient(0x7f000001, mainGame->gameConf.serverport)) {
					NetServer::StopServer();
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));
					break;
				}
#else
				if(fork() == 0) {
					usleep(100000);
					wchar_t warg1[512];
					if(mainGame->botInfo[sel].select_deckfile) {
						wchar_t botdeck[256];
						deckManager.GetDeckFile(botdeck, mainGame->cbBotDeckCategory, mainGame->cbBotDeck);
						myswprintf(warg1, L"%ls DeckFile='%ls'", mainGame->botInfo[sel].command, botdeck);
					}
					else
						myswprintf(warg1, L"%ls", mainGame->botInfo[sel].command);
					char arg1[512];
					BufferIO::EncodeUTF8(warg1, arg1);
					int flag = 0;
					flag += (mainGame->chkBotHand->isChecked() ? 0x1 : 0);
					char arg2[8];
					sprintf(arg2, "%d", flag);
					char arg3[8];
					sprintf(arg3, "%d", mainGame->gameConf.serverport);
					execl("./bot", "bot", arg1, arg2, arg3, NULL);
					exit(0);
				} else {
					if(!NetServer::StartServer(mainGame->gameConf.serverport)) {
						mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
						mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));
						break;
					}
					if(!DuelClient::StartClient(0x7f000001, mainGame->gameConf.serverport)) {
						NetServer::StopServer();
						mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
						mainGame->env->addMessageBox(L"", dataManager.GetSysString(1402));
						break;
					}
				}
#endif
				mainGame->btnStartBot->setEnabled(false);
				mainGame->btnBotCancel->setEnabled(false);
				break;
			}
			case BUTTON_LOAD_SINGLEPLAY: {
                mainGame->imgChat->setVisible(false);
				if(mainGame->lstSinglePlayList->getSelected() == -1)
					break;
				mainGame->singleSignal.SetNoWait(false);
				SingleMode::StartPlay();
				break;
			}
			case BUTTON_CANCEL_SINGLEPLAY: {
				mainGame->HideElement(mainGame->wSinglePlay);
				mainGame->ShowElement(mainGame->wMainMenu);
				break;
			}
			case BUTTON_DECK_EDIT: {
				mainGame->RefreshCategoryDeck(mainGame->cbDBCategory, mainGame->cbDBDecks);
				if(mainGame->cbDBCategory->getSelected() != -1 && mainGame->cbDBDecks->getSelected() != -1) {
					deckManager.LoadDeck(mainGame->cbDBCategory, mainGame->cbDBDecks);
					mainGame->ebDeckname->setText(L"");
				}
				mainGame->HideElement(mainGame->wMainMenu);
				mainGame->deckBuilder.Initialize();
				break;
			}
			case BUTTON_YES: {
				mainGame->HideElement(mainGame->wQuery);
				if(prev_operation == BUTTON_DELETE_REPLAY) {
					if(Replay::DeleteReplay(mainGame->lstReplayList->getListItem(prev_sel))) {
						mainGame->stReplayInfo->setText(L"");
						mainGame->lstReplayList->removeItem(prev_sel);
					}
				}
				prev_operation = 0;
				prev_sel = -1;
				break;
			}
			case BUTTON_NO: {
				mainGame->HideElement(mainGame->wQuery);
				prev_operation = 0;
				prev_sel = -1;
				break;
			}
			case BUTTON_REPLAY_SAVE: {
				mainGame->HideElement(mainGame->wReplaySave);
				if(prev_operation == BUTTON_RENAME_REPLAY) {
					wchar_t newname[256];
					BufferIO::CopyWStr(mainGame->ebRSName->getText(), newname, 256);
					if(wcsncasecmp(newname + wcslen(newname) - 4, L".yrp", 4)) {
						myswprintf(newname, L"%ls.yrp", mainGame->ebRSName->getText());
					}
					if(Replay::RenameReplay(mainGame->lstReplayList->getListItem(prev_sel), newname)) {
						mainGame->lstReplayList->setItem(prev_sel, newname, -1);
					} else {
						mainGame->addMessageBox(L"", dataManager.GetSysString(1365));
					}
				}
				prev_operation = 0;
				prev_sel = -1;
				break;
			}
			case BUTTON_REPLAY_CANCEL: {
				mainGame->HideElement(mainGame->wReplaySave);
				prev_operation = 0;
				prev_sel = -1;
				break;
			}
			case BUTTON_SETTINGS: {
                mainGame->HideElement(mainGame->wMainMenu);
                mainGame->ShowElement(mainGame->wSettings);
			    break;
			}
			case BUTTON_CLOSE_SETTINGS: {
                mainGame->HideElement(mainGame->wSettings);
                mainGame->ShowElement(mainGame->wMainMenu);
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_LISTBOX_CHANGED: {
			switch(id) {
				case LISTBOX_CATEGORIES: {
                    if(mainGame->wDMQuery->isVisible()) {
                        mainGame->lstCategories->setSelected(mainGame->deckBuilder.prev_category);
                        break;
                    }
                    int catesel = mainGame->lstCategories->getSelected();
                    if(catesel == 2) {
                        catesel = 1;
                        mainGame->lstCategories->setSelected(catesel);
                        if(mainGame->deckBuilder.prev_category == catesel)
                            break;
                    }
                    mainGame->deckBuilder.RefreshDeckList();
                    mainGame->lstDecks->setSelected(0);
                    mainGame->cbCategorySelect->setSelected(catesel);
                    ChangeHostPrepareDeckCategory(catesel);
					wchar_t cate[256];
					wchar_t cate_deck[256];
					myswprintf(cate, L"%ls%ls", (mainGame->lstCategories->getSelected())==1 ? L"" : mainGame->lstCategories->getListItem(mainGame->lstCategories->getSelected()), (mainGame->lstCategories->getSelected())==1 ? L"" : L"|");
					myswprintf(cate_deck, L"%ls%ls", cate, mainGame->lstDecks->getListItem(mainGame->lstDecks->getSelected()));
					mainGame->btnHostDeckSelect->setText(cate_deck);
                    break;
				}
				case LISTBOX_DECKS: {
                    if(mainGame->wDMQuery->isVisible()) {
                        mainGame->lstDecks->setSelected(mainGame->deckBuilder.prev_deck);
                        break;
                    }
                    int decksel = mainGame->lstDecks->getSelected();
                    mainGame->cbDeckSelect->setSelected(decksel);
                    if(decksel == -1)
                        break;
                    wchar_t cate[256];
                    wchar_t cate_deck[256];
                    myswprintf(cate, L"%ls%ls", (mainGame->lstCategories->getSelected())==1 ? L"" : mainGame->lstCategories->getListItem(mainGame->lstCategories->getSelected()), (mainGame->lstCategories->getSelected())==1 ? L"" : L"|");
                    myswprintf(cate_deck, L"%ls%ls", cate, mainGame->lstDecks->getListItem(mainGame->lstDecks->getSelected()));
                    mainGame->btnHostDeckSelect->setText(cate_deck);
                    mainGame->deckBuilder.RefreshPackListScroll();
                    mainGame->deckBuilder.prev_deck = decksel;
                    break;
				}
			case LISTBOX_LAN_HOST: {
				int sel = mainGame->lstHostList->getSelected();
				if(sel == -1)
					break;
				int addr = DuelClient::hosts[sel].ipaddr;
				int port = DuelClient::hosts[sel].port;
				wchar_t buf[20];
				myswprintf(buf, L"%d.%d.%d.%d", addr & 0xff, (addr >> 8) & 0xff, (addr >> 16) & 0xff, (addr >> 24) & 0xff);
				mainGame->ebJoinHost->setText(buf);
				myswprintf(buf, L"%d", port);
				mainGame->ebJoinPort->setText(buf);
				break;
			}
			case LISTBOX_REPLAY_LIST: {
				int sel = mainGame->lstReplayList->getSelected();
				if(sel == -1)
					break;
				if(!ReplayMode::cur_replay.OpenReplay(mainGame->lstReplayList->getListItem(sel)))
					break;
				wchar_t infobuf[256];
				std::wstring repinfo;
				time_t curtime;
				if(ReplayMode::cur_replay.pheader.flag & REPLAY_UNIFORM)
					curtime = ReplayMode::cur_replay.pheader.start_time;
				else
					curtime = ReplayMode::cur_replay.pheader.seed;
				tm* st = localtime(&curtime);
				wcsftime(infobuf, 256, L"%Y/%m/%d %H:%M:%S\n", st);
				repinfo.append(infobuf);
				wchar_t namebuf[4][20];
				ReplayMode::cur_replay.ReadName(namebuf[0]);
				ReplayMode::cur_replay.ReadName(namebuf[1]);
				if(ReplayMode::cur_replay.pheader.flag & REPLAY_TAG) {
					ReplayMode::cur_replay.ReadName(namebuf[2]);
					ReplayMode::cur_replay.ReadName(namebuf[3]);
				}
				if(ReplayMode::cur_replay.pheader.flag & REPLAY_TAG)
					myswprintf(infobuf, L"%ls\n%ls\n===VS===\n%ls\n%ls\n", namebuf[0], namebuf[1], namebuf[2], namebuf[3]);
				else
					myswprintf(infobuf, L"%ls\n===VS===\n%ls\n", namebuf[0], namebuf[1]);
				repinfo.append(infobuf);
				mainGame->ebRepStartTurn->setText(L"1");
				mainGame->SetStaticText(mainGame->stReplayInfo, 180 * mainGame->xScale, mainGame->guiFont, (wchar_t*)repinfo.c_str());
				break;
			}
			case LISTBOX_SINGLEPLAY_LIST: {
				int sel = mainGame->lstSinglePlayList->getSelected();
				if(sel == -1)
					break;
				const wchar_t* name = mainGame->lstSinglePlayList->getListItem(sel);
				wchar_t fname[256];
				myswprintf(fname, L"./single/%ls", name);
				FILE *fp;
#ifdef _WIN32
				fp = _wfopen(fname, L"rb");
#else
				char filename[256];
				BufferIO::EncodeUTF8(fname, filename);
				fp = fopen(filename, "rb");
#endif
				if(!fp) {
					mainGame->stSinglePlayInfo->setText(L"");
					break;
				}
				char linebuf[1024];
				wchar_t wlinebuf[1024];
				std::wstring message = L"";
				bool in_message = false;
				while(fgets(linebuf, 1024, fp)) {
					if(!strncmp(linebuf, "--[[message", 11)) {
						size_t len = strlen(linebuf);
						char* msgend = strrchr(linebuf, ']');
						if(len <= 13) {
							in_message = true;
							continue;
						} else if(len > 15 && msgend) {
							*(msgend - 1) = '\0';
							BufferIO::DecodeUTF8(linebuf + 12, wlinebuf);
							message.append(wlinebuf);
							break;
						}
					}
					if(!strncmp(linebuf, "]]", 2)) {
						in_message = false;
						break;
					}
					if(in_message) {
						BufferIO::DecodeUTF8(linebuf, wlinebuf);
						message.append(wlinebuf);
					}
				}
				fclose(fp);
				mainGame->SetStaticText(mainGame->stSinglePlayInfo, 200 * mainGame->xScale, mainGame->guiFont, message.c_str());
				break;
			}
			case LISTBOX_BOT_LIST: {
				int sel = mainGame->lstBotList->getSelected();
				if(sel == -1)
					break;
				mainGame->SetStaticText(mainGame->stBotInfo, 200 * mainGame->xScale, mainGame->guiFont, mainGame->botInfo[sel].desc);
				mainGame->cbBotDeckCategory->setVisible(mainGame->botInfo[sel].select_deckfile);
				mainGame->cbBotDeck->setVisible(mainGame->botInfo[sel].select_deckfile);
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_CHECKBOX_CHANGED: {
			switch(id) {
			case CHECKBOX_HP_READY: {
				if(!caller->isEnabled())
					break;
				mainGame->env->setFocus(mainGame->wHostPrepare);
                mainGame->HideElement(mainGame->wDeckManage);
				if(static_cast<irr::gui::IGUICheckBox*>(caller)->isChecked()) {
					if(mainGame->cbCategorySelect->getSelected() == -1 || mainGame->cbDeckSelect->getSelected() == -1 ||
						!deckManager.LoadDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect)) {
						mainGame->gMutex.lock();
						static_cast<irr::gui::IGUICheckBox*>(caller)->setChecked(false);
						mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
						mainGame->env->addMessageBox(L"", dataManager.GetSysString(1406));
						mainGame->gMutex.unlock();
						break;
					}
					UpdateDeck();
					DuelClient::SendPacketToServer(CTOS_HS_READY);
					mainGame->cbCategorySelect->setEnabled(false);
					mainGame->cbDeckSelect->setEnabled(false);
                    mainGame->btnHostDeckSelect->setEnabled(false);
				} else {
					DuelClient::SendPacketToServer(CTOS_HS_NOTREADY);
					mainGame->cbCategorySelect->setEnabled(true);
					mainGame->cbDeckSelect->setEnabled(true);
                    mainGame->btnHostDeckSelect->setEnabled(true);
				}
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_COMBO_BOX_CHANGED: {
			switch(id) {
			case COMBOBOX_BOT_RULE: {
				mainGame->RefreshBot();
				break;
			}
            case COMBOBOX_HP_CATEGORY: {
				int catesel = mainGame->cbCategorySelect->getSelected();
				if(catesel == 3) {
					catesel = 2;
					mainGame->cbCategorySelect->setSelected(2);
				}
				if(catesel >= 0) {
					mainGame->RefreshDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect);
					mainGame->cbDeckSelect->setSelected(0);
				}
				break;
			}
			case COMBOBOX_BOT_DECKCATEGORY: {
				int catesel = mainGame->cbBotDeckCategory->getSelected();
				if(catesel == 3) {
					catesel = 2;
					mainGame->cbBotDeckCategory->setSelected(2);
				}
				if(catesel >= 0) {
					mainGame->RefreshDeck(mainGame->cbBotDeckCategory, mainGame->cbBotDeck);
					mainGame->cbBotDeck->setSelected(0);
				}
				break;
			}
			}
			break;
		}
		default: break;
		}
		break;
	}
	default: break;
	}
	return false;
}

}

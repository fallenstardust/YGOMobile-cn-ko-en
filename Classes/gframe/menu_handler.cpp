#include "config.h"
#include "menu_handler.h"
#include "myfilesystem.h"
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
    irr::android::setLastCategory(mainGame->appMain, linebuf);

	BufferIO::CopyWStr(mainGame->cbDeckSelect->getItem(mainGame->cbDeckSelect->getSelected()), mainGame->gameConf.lastdeck, 64);
	BufferIO::EncodeUTF8(mainGame->gameConf.lastdeck, linebuf);
    irr::android::setLastDeck(mainGame->appMain, linebuf);
		
	unsigned char deckbuf[1024]{};
	auto pdeck = deckbuf;
	BufferIO::Write<int32_t>(pdeck, static_cast<int32_t>(deckManager.current_deck.main.size() + deckManager.current_deck.extra.size()));
	BufferIO::Write<int32_t>(pdeck, static_cast<int32_t>(deckManager.current_deck.side.size()));
	for(size_t i = 0; i < deckManager.current_deck.main.size(); ++i)
		BufferIO::Write<uint32_t>(pdeck, deckManager.current_deck.main[i]->first);
	for(size_t i = 0; i < deckManager.current_deck.extra.size(); ++i)
		BufferIO::Write<uint32_t>(pdeck, deckManager.current_deck.extra[i]->first);
	for(size_t i = 0; i < deckManager.current_deck.side.size(); ++i)
		BufferIO::Write<uint32_t>(pdeck, deckManager.current_deck.side[i]->first);
	DuelClient::SendBufferToServer(CTOS_UPDATE_DECK, deckbuf, pdeck - deckbuf);
}

void ShowHostPrepareDeckManage(irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDecks) {
    mainGame->RefreshCategoryDeck(cbCategory, cbDecks, false);
    cbCategory->setSelected(mainGame->deckBuilder.prev_category);
    mainGame->RefreshDeck(cbCategory, cbDecks);
    cbDecks->setSelected(mainGame->deckBuilder.prev_deck);
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
    mainGame->deckBuilder.RefreshDeckList(false);
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
	if (mainGame->wHostPrepare->isVisible()) {
		mainGame->RefreshDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect);
		mainGame->cbDeckSelect->setSelected(0);
	}
	if (mainGame->wSinglePlay->isVisible()) {
		mainGame->RefreshDeck(mainGame->cbBotDeckCategory, mainGame->cbBotDeck);
		mainGame->cbBotDeck->setSelected(0);
	}
    mainGame->deckBuilder.is_modified = false;
    mainGame->deckBuilder.prev_category = catesel;
    mainGame->deckBuilder.prev_deck = 0;
}

void reSetCategoryDeckNameOnButton(irr::gui::IGUIButton* button, const wchar_t* string){
    wchar_t cate[256];
    wchar_t cate_deck[256];
    myswprintf(cate, L"%ls%ls", (mainGame->lstCategories->getSelected())==1 ? L"" : mainGame->lstCategories->getListItem(mainGame->lstCategories->getSelected()), (mainGame->lstCategories->getSelected())==1 ? L"" : string);
    if (mainGame->lstDecks->getItemCount() != 0) {
		myswprintf(cate_deck, L"%ls%ls", cate, mainGame->lstDecks->getListItem(mainGame->lstDecks->getSelected()));
	} else {
		myswprintf(cate_deck, L"%ls%ls", cate, dataManager.GetSysString(1301));
	}
    button->setText(cate_deck);
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
		irr::s32 id = caller->getID();
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
                     ShowHostPrepareDeckManage(mainGame->cbCategorySelect, mainGame->cbDeckSelect);
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
				#ifdef _IRR_ANDROID_PLATFORM_
				wchar_t pstr[100];
				wchar_t portstr[10];
				BufferIO::CopyWideString(mainGame->ebJoinHost->getText(), pstr);
				BufferIO::CopyWideString(mainGame->ebJoinPort->getText(), portstr);
				HostResult remote= DuelClient::ParseHost(pstr, portstr);
				if(!remote.isValid()) {
					mainGame->gMutex.lock();
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->addMessageBox(L"", dataManager.GetSysString(1412));
					mainGame->gMutex.unlock();
					break;
				}
				unsigned int remote_port = std::wcstol(portstr, nullptr, 10);
				BufferIO::CopyWideString(pstr, mainGame->gameConf.lasthost);
				BufferIO::CopyWideString(portstr, mainGame->gameConf.lastport);
				if(DuelClient::StartClient(remote.host, remote.port, false)) {
					mainGame->btnCreateHost->setEnabled(false);
					mainGame->btnJoinHost->setEnabled(false);
					mainGame->btnJoinCancel->setEnabled(false);
				}
				#endif
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
				BufferIO::CopyWideString(mainGame->ebServerName->getText(), mainGame->gameConf.gamename);
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
				int index = 0;
				while(index < 4) {
					if(mainGame->btnHostPrepKick[index] == caller)
						break;
					++index;
				}
				CTOS_Kick csk;
				csk.pos = index;
				DuelClient::SendPacketToServer(CTOS_HS_KICK, csk);
				break;
			}
			case BUTTON_HP_READY: {
				if(mainGame->cbCategorySelect->getSelected() == -1 || mainGame->cbDeckSelect->getSelected() == -1 ||
					!deckManager.LoadCurrentDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect)) {
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
				int start_turn = 1;
				auto selected = mainGame->lstReplayList->getSelected();
				if(selected == -1)
					break;
				wchar_t replay_path[256]{};
				myswprintf(replay_path, L"./replay/%ls", mainGame->lstReplayList->getListItem(selected));
				if (!ReplayMode::cur_replay.OpenReplay(replay_path))
					break;
				start_turn = std::wcstol(mainGame->ebRepStartTurn->getText(), nullptr, 10);
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
				ALOGD("cc menu_handler: 1share replay file=%s", name);
                irr::android::OnShareFile(mainGame->appMain, "yrp", name);
				ALOGD("cc menu_handler: 2after share replay file:index=%d", sel);
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
				auto selected = mainGame->lstReplayList->getSelected();
				if(selected == -1)
					break;
				Replay replay;
				wchar_t replay_filename[256]{};
				wchar_t namebuf[4][20]{};
				wchar_t filename[256]{};
				wchar_t replay_path[256]{};
				BufferIO::CopyWideString(mainGame->lstReplayList->getListItem(selected), replay_filename);
				myswprintf(replay_path, L"./replay/%ls", replay_filename);
				if (!replay.OpenReplay(replay_path))
					break;
				if (replay.pheader.base.flag & REPLAY_SINGLE_MODE)
					break;
				for (size_t i = 0; i < replay.decks.size(); ++i) {
					BufferIO::CopyWideString(replay.players[Replay::GetDeckPlayer(i)].c_str(), namebuf[i]);
					FileSystem::SafeFileName(namebuf[i]);
				}
				for (size_t i = 0; i < replay.decks.size(); ++i) {
					myswprintf(filename, L"./deck/%ls-%d %ls.ydk", replay_filename, i + 1, namebuf[i]);
					DeckManager::SaveDeckArray(replay.decks[i], filename);
				}
				mainGame->stACMessage->setText(dataManager.GetSysString(1335));
				mainGame->PopupElement(mainGame->wACMessage, 20);
				break;
			}
            case BUTTON_BOT_DECK_SELECT: {
				ShowHostPrepareDeckManage(mainGame->cbBotDeckCategory, mainGame->cbBotDeck);
                break;
            }
			case BUTTON_BOT_START: {
				int sel = mainGame->lstBotList->getSelected();
				if(sel == -1)
					break;
				bot_mode = true;
#ifdef _IRR_ANDROID_PLATFORM_
				char args[512];
				wchar_t warg1[512];
				if(mainGame->botInfo[sel].select_deckfile) {
					wchar_t botdeck[256];
					DeckManager::GetDeckFile(botdeck, mainGame->cbBotDeckCategory, mainGame->cbBotDeck);
					myswprintf(warg1, L"%ls DeckFile='%ls'", mainGame->botInfo[sel].command, botdeck);
				}
				else
					myswprintf(warg1, L"%ls", mainGame->botInfo[sel].command);
				char arg1[512];
				BufferIO::EncodeUTF8(warg1, arg1);
				char arg2[32];
				arg2[0]=0;
				if(mainGame->chkBotHand->isChecked())
					mysnprintf(arg2, " Hand=1");
				char arg3[32];
				mysnprintf(arg3, " Port=%d", mainGame->gameConf.serverport);
				mysnprintf(args, "%s%s%s", arg1, arg2, arg3);
                irr::android::runWindbot(mainGame->appMain, args);
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
					deckManager.LoadCurrentDeck(mainGame->cbDBCategory, mainGame->cbDBDecks);
					mainGame->ebDeckname->setText(L"");
				}
				mainGame->HideElement(mainGame->wMainMenu);
				mainGame->chatMsg->clear();
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
					BufferIO::CopyWideString(mainGame->ebRSName->getText(), newname);
					if(mywcsncasecmp(newname + std::wcslen(newname) - 4, L".yrp", 4)) {
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
                    mainGame->deckBuilder.RefreshDeckList(false);
                    mainGame->lstDecks->setSelected(0);
                    mainGame->cbCategorySelect->setSelected(catesel);
                    mainGame->cbBotDeckCategory->setSelected(catesel);
					ChangeHostPrepareDeckCategory(catesel);
					if (mainGame->wSinglePlay->isVisible()) {
						reSetCategoryDeckNameOnButton(mainGame->btnBotDeckSelect, L"|");
					}
					if (mainGame->wHostPrepare->isVisible()) {
						reSetCategoryDeckNameOnButton(mainGame->btnHostDeckSelect, L"|");
					}
                    break;
				}
				case LISTBOX_DECKS: {
                    if(mainGame->wDMQuery->isVisible()) {
                        mainGame->lstDecks->setSelected(mainGame->deckBuilder.prev_deck);
                        break;
                    }
                    int decksel = mainGame->lstDecks->getSelected();
					if (mainGame->wSinglePlay->isVisible()) {
						reSetCategoryDeckNameOnButton(mainGame->btnBotDeckSelect, L"|");
                    	mainGame->cbBotDeck->setSelected(decksel);
					}
					if (mainGame->wHostPrepare->isVisible()) {
						reSetCategoryDeckNameOnButton(mainGame->btnHostDeckSelect, L"|");
						mainGame->cbDeckSelect->setSelected(decksel);
					}
                    if(decksel == -1)
                        break;
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
				if(sel < 0)
					break;
				auto filename = mainGame->lstReplayList->getListItem(sel);
				if (!filename)
					break;
				wchar_t replay_path[256]{};
				myswprintf(replay_path, L"./replay/%ls", filename);
				if (!temp_replay.OpenReplay(replay_path)) {
					mainGame->stReplayInfo->setText(L"Error");
					break;
				}
				wchar_t infobuf[256]{};
				std::wstring repinfo;
				time_t curtime;
				const auto& rh = temp_replay.pheader.base;
				if(temp_replay.pheader.base.flag & REPLAY_UNIFORM)
					curtime = rh.start_time;
				else{
					curtime = rh.seed;
					wchar_t version_info[256]{};
					myswprintf(version_info, L"version 0x%X\n", rh.version);
					repinfo.append(version_info);
				}
				std::wcsftime(infobuf, sizeof infobuf / sizeof infobuf[0], L"%Y/%m/%d %H:%M:%S\n", std::localtime(&curtime));
				repinfo.append(infobuf);
				if (rh.flag & REPLAY_SINGLE_MODE) {
					wchar_t path[256]{};
					BufferIO::DecodeUTF8(temp_replay.script_name.c_str(), path);
					repinfo.append(path);
					repinfo.append(L"\n");
				}
				const auto& player_names = temp_replay.players;
				if(rh.flag & REPLAY_TAG)
					myswprintf(infobuf, L"%ls\n%ls\n===VS===\n%ls\n%ls\n", player_names[0].c_str(), player_names[1].c_str(), player_names[2].c_str(), player_names[3].c_str());
				else
					myswprintf(infobuf, L"%ls\n===VS===\n%ls\n", player_names[0].c_str(), player_names[1].c_str());
				repinfo.append(infobuf);
				mainGame->ebRepStartTurn->setText(L"1");
				mainGame->SetStaticText(mainGame->stReplayInfo, 180 * mainGame->xScale, mainGame->guiFont, repinfo.c_str());
				break;
			}
			case LISTBOX_SINGLEPLAY_LIST: {
				int sel = mainGame->lstSinglePlayList->getSelected();
				if(sel == -1)
					break;
				const wchar_t* name = mainGame->lstSinglePlayList->getListItem(sel);
				wchar_t fname[256];
				myswprintf(fname, L"./single/%ls", name);
				FILE* fp = mywfopen(fname, "rb");
				if(!fp) {
					mainGame->stSinglePlayInfo->setText(L"");
					break;
				}
				char linebuf[1024];
				wchar_t wlinebuf[1024];
				std::wstring message = L"";
				bool in_message = false;
				while(std::fgets(linebuf, 1024, fp)) {
					if(!std::strncmp(linebuf, "--[[message", 11)) {
						size_t len = std::strlen(linebuf);
						char* msgend = std::strrchr(linebuf, ']');
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
					if(!std::strncmp(linebuf, "]]", 2)) {
						in_message = false;
						break;
					}
					if(in_message) {
						BufferIO::DecodeUTF8(linebuf, wlinebuf);
						message.append(wlinebuf);
					}
				}
				std::fclose(fp);
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
				mainGame->btnBotDeckSelect->setVisible(mainGame->botInfo[sel].select_deckfile);
				wchar_t cate[256];
				wchar_t cate_deck[256];
				myswprintf(cate, L"%ls%ls", (mainGame->cbBotDeckCategory->getSelected())==1 ? L"" : mainGame->cbBotDeckCategory->getItem(mainGame->cbBotDeckCategory->getSelected()), (mainGame->cbBotDeckCategory->getSelected())==1 ? L"" : L"|");
				if (mainGame->cbBotDeck->getItemCount() != 0) {
					myswprintf(cate_deck, L"%ls%ls", cate, mainGame->cbBotDeck->getItem(mainGame->cbBotDeck->getSelected()));
				} else {
					myswprintf(cate_deck, L"%ls%ls", cate, dataManager.GetSysString(1301));
				}
				mainGame->btnBotDeckSelect->setText(cate_deck);
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
						!deckManager.LoadCurrentDeck(mainGame->cbCategorySelect, mainGame->cbDeckSelect)) {
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

#include "event_handler.h"
#include "client_field.h"
#include "network.h"
#include "game.h"
#include "duelclient.h"
#include "data_manager.h"
#include "image_manager.h"
#include "sound_manager.h"
#include "deck_manager.h"
#include "replay_mode.h"
#include "single_mode.h"
#include "materials.h"

namespace ygo {

bool ClientField::OnEvent(const irr::SEvent& event) {
	if(OnCommonEvent(event))
		return false;

	// 定义一个事件变量用于处理Android平台的触摸事件转换
    irr::SEvent transferEvent;
    // 调用Android平台的触摸事件转换处理函数，如果事件已被处理则返回true
    if (irr::android::TouchEventTransferAndroid::OnTransferCommon(event, false)) {
        return true;
    }
	switch(event.EventType) {
	case irr::EET_GUI_EVENT: {
		if(mainGame->fadingList.size())
			break;
		irr::s32 id = event.GUIEvent.Caller->getID();
		switch(event.GUIEvent.EventType) {
		case irr::gui::EGET_BUTTON_CLICKED: {
			switch(id) {
			// 处理猜拳选择的3个按钮的点击事件
            case BUTTON_HAND1:
            case BUTTON_HAND2:
            case BUTTON_HAND3: {
                // 隐藏手势选择窗口
                mainGame->wHand->setVisible(false);
                // 判断当前消息是否为猜拳(MSG_ROCK_PAPER_SCISSORS)
                if(mainGame->dInfo.curMsg == MSG_ROCK_PAPER_SCISSORS) {
                    // 设置响应值为按钮索引+1(1=石头,2=布,3=剪刀)
                    DuelClient::SetResponseI(id - BUTTON_HAND1 + 1);
                    // 发送响应给服务器
                    DuelClient::SendResponse();
                } else {
                    // 清空提示信息文本
                    mainGame->stHintMsg->setText(L"");
                    // 显示提示信息窗口
                    mainGame->stHintMsg->setVisible(true);
                    // 创建手势结果数据包
                    CTOS_HandResult cshr;
                    // 设置手势结果为按钮索引+1
                    cshr.res = id - BUTTON_HAND1 + 1;
                    // 发送手势结果数据包到服务器
                    DuelClient::SendPacketToServer(CTOS_HAND_RESULT, cshr);
                }
                break;
            }
            // 处理先后攻选择按钮的点击事件
            case BUTTON_FIRST:
            case BUTTON_SECOND: {
                // 播放按钮点击音效
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
                // 隐藏先后手选择窗口
                mainGame->HideElement(mainGame->wFTSelect);
                // 创建先后手选择结果数据包
                CTOS_TPResult cstr;
                // 设置选择结果(BUTTON_SECOND-BUTTON_FIRST=1表示先手，BUTTON_SECOND-BUTTON_SECOND=0表示后手)
                cstr.res = BUTTON_SECOND - id;
                // 发送先后手选择结果数据包到服务器
                DuelClient::SendPacketToServer(CTOS_TP_RESULT, cstr);
                break;
            }
			case BUTTON_REPLAY_START: {
				if(!mainGame->dInfo.isReplay)
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->btnReplayStart->setVisible(false);
				mainGame->btnReplayPause->setVisible(true);
				mainGame->btnReplayStep->setVisible(false);
				mainGame->btnReplayUndo->setVisible(false);
				ReplayMode::Pause(false, false);
				break;
			}
			case BUTTON_REPLAY_PAUSE: {
				if(!mainGame->dInfo.isReplay)
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->btnReplayStart->setVisible(true);
				mainGame->btnReplayPause->setVisible(false);
				mainGame->btnReplayStep->setVisible(true);
				mainGame->btnReplayUndo->setVisible(true);
				ReplayMode::Pause(true, false);
				break;
			}
			case BUTTON_REPLAY_STEP: {
				if(!mainGame->dInfo.isReplay)
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				ReplayMode::Pause(false, true);
				break;
			}
			case BUTTON_REPLAY_EXIT: {
				if(!mainGame->dInfo.isReplay)
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				ReplayMode::StopReplay();
				break;
			}
			case BUTTON_REPLAY_SWAP: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				if(mainGame->dInfo.isReplay)
					ReplayMode::SwapField();
				else if(mainGame->dInfo.player_type == 7)
					DuelClient::SwapField();
				break;
			}
			case BUTTON_REPLAY_UNDO: {
				if(!mainGame->dInfo.isReplay)
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				ReplayMode::Undo();
				break;
			}
			case BUTTON_REPLAY_SAVE: {
				if(mainGame->ebRSName->getText()[0] == 0)
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->actionParam = 1;
				mainGame->HideElement(mainGame->wReplaySave);
				mainGame->replaySignal.Set();
				break;
			}
			case BUTTON_REPLAY_CANCEL: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->actionParam = 0;
				mainGame->HideElement(mainGame->wReplaySave);
				mainGame->replaySignal.Set();
				break;
			}
			case BUTTON_LEAVE_GAME: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				if(mainGame->dInfo.isSingleMode) {
					mainGame->singleSignal.SetNoWait(true);
					SingleMode::StopPlay(false);
					break;
				}
				if(mainGame->dInfo.player_type == 7) {
					DuelClient::StopClient();
					mainGame->dInfo.isStarted = false;
					mainGame->dInfo.isInDuel = false;
					mainGame->dInfo.isFinished = false;
					mainGame->device->setEventReceiver(&mainGame->menuHandler);
					mainGame->CloseDuelWindow();
					mainGame->btnCreateHost->setEnabled(true);
					mainGame->btnJoinHost->setEnabled(true);
					mainGame->btnJoinCancel->setEnabled(true);
					mainGame->btnStartBot->setEnabled(true);
					mainGame->btnBotCancel->setEnabled(true);
					if(bot_mode)
						mainGame->ShowElement(mainGame->wSinglePlay);
					else
						mainGame->ShowElement(mainGame->wLanWindow);
					if(exit_on_return)
						mainGame->OnGameClose();
				} else {
					if(!(mainGame->dInfo.isTag && mainGame->dField.tag_surrender))
						mainGame->PopupElement(mainGame->wSurrender);
				}
				break;
			}
			case BUTTON_SURRENDER_YES: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				DuelClient::SendPacketToServer(CTOS_SURRENDER);
				mainGame->HideElement(mainGame->wSurrender);
				mainGame->dField.tag_surrender = true;
				break;
			}
			case BUTTON_SURRENDER_NO: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->dField.tag_teammate_surrender = false;
				mainGame->HideElement(mainGame->wSurrender);
				break;
			}
			case BUTTON_SETTINGS: {
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
			    if (mainGame->imgSettings->isPressed()) {
                    mainGame->ShowElement(mainGame->wSettings);
                    mainGame->imgSettings->setPressed(true);
			    } else {
                    mainGame->HideElement(mainGame->wSettings);
                    mainGame->imgSettings->setPressed(false);
                }
			    break;
			}
			case BUTTON_CLOSE_SETTINGS: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->HideElement(mainGame->wSettings);
                mainGame->imgSettings->setPressed(false);
				break;
			}
			case BUTTON_SHOW_LOG: {
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
                if (mainGame->imgLog->isPressed()) {
                    mainGame->ShowElement(mainGame->wLogs);
                    mainGame->imgLog->setPressed(true);
                } else {
                    mainGame->HideElement(mainGame->wLogs);
                    mainGame->imgLog->setPressed(false);
                }
				break;
			}
			case BUTTON_CLOSE_LOG: {
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
                mainGame->HideElement(mainGame->wLogs);
                mainGame->imgLog->setPressed(false);
			    break;
			}
			case BUTTON_BGM: {
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
                if (mainGame->gameConf.enable_music) {
                    mainGame->gameConf.enable_music = false;
                    mainGame->imgVol->setImage(imageManager.tMute);
                } else {
                    mainGame->gameConf.enable_music = true;
                    mainGame->imgVol->setImage(imageManager.tPlay);
                }
                mainGame->chkEnableMusic->setChecked(mainGame->gameConf.enable_music);
                mainGame->soundManager->EnableMusic(mainGame->chkEnableMusic->isChecked());
				break;
			}
            case BUTTON_QUICK_ANIMIATION: {
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
                if (mainGame->gameConf.quick_animation) {
                        mainGame->gameConf.quick_animation = false;
                        mainGame->imgQuickAnimation->setImage(imageManager.tOneX);
				} else {
					mainGame->gameConf.quick_animation = true;
					mainGame->imgQuickAnimation->setImage(imageManager.tDoubleX);
				}
				mainGame->chkQuickAnimation->setChecked(mainGame->gameConf.quick_animation);
				break;
            }
			case BUTTON_CHATTING: {
			    mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				if (mainGame->gameConf.chkIgnore1) {
					mainGame->gameConf.chkIgnore1 = false;
					mainGame->imgChat->setImage(imageManager.tTalk);
				} else {
					mainGame->gameConf.chkIgnore1 = true;
					mainGame->imgChat->setImage(imageManager.tShut);
				}
				mainGame->chkIgnore1->setChecked(mainGame->gameConf.chkIgnore1);
				bool show = !mainGame->is_building && !mainGame->chkIgnore1->isChecked();
				mainGame->wChat->setVisible(show);
				if(!show)
					mainGame->ClearChatMsg();
			    break;
			}
            case BUTTON_EMOTICON: {
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
                mainGame->wEmoticon->isVisible() ? mainGame->HideElement(mainGame->wEmoticon) : mainGame->ShowElement(mainGame->wEmoticon);
                break;
            }
            case BUTTON_EMOTICON_0:
            case BUTTON_EMOTICON_1:
            case BUTTON_EMOTICON_2:
            case BUTTON_EMOTICON_3:
            case BUTTON_EMOTICON_4:
            case BUTTON_EMOTICON_5:
            case BUTTON_EMOTICON_6:
            case BUTTON_EMOTICON_7:
            case BUTTON_EMOTICON_8:
            case BUTTON_EMOTICON_9:
            case BUTTON_EMOTICON_10:
            case BUTTON_EMOTICON_11:
            case BUTTON_EMOTICON_12:
            case BUTTON_EMOTICON_13:
            case BUTTON_EMOTICON_14:
            case BUTTON_EMOTICON_15: {
                mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
                mainGame->HideElement(mainGame->wEmoticon);
                int emoticonIndex = id - BUTTON_EMOTICON_0;
                if (emoticonIndex >= 0 && emoticonIndex < 16) {
                    const wchar_t* emoticonText = imageManager.emoticonCodes[emoticonIndex];
                    // 使用与聊天输入框相同的发送逻辑
                    uint16_t msgbuf[LEN_CHAT_MSG];
                    int len = BufferIO::CopyCharArray(emoticonText, msgbuf);
                    if(len > 0) {
                        DuelClient::SendBufferToServer(CTOS_CHAT, msgbuf, (len + 1) * sizeof(uint16_t));
                    }
                }
                break;
            }
			case BUTTON_CHAIN_IGNORE: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->ignore_chain = mainGame->btnChainIgnore->isPressed();
				mainGame->always_chain = false;
				mainGame->chain_when_avail = false;
				UpdateChainButtons();
				break;
			}
			case BUTTON_CHAIN_ALWAYS: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->always_chain = mainGame->btnChainAlways->isPressed();
				mainGame->ignore_chain = false;
				mainGame->chain_when_avail = false;
				UpdateChainButtons();
				break;
			}
			case BUTTON_CHAIN_WHENAVAIL: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->chain_when_avail = mainGame->btnChainWhenAvail->isPressed();
				mainGame->always_chain = false;
				mainGame->ignore_chain = false;
				UpdateChainButtons();
				break;
			}
			case BUTTON_CANCEL_OR_FINISH: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				CancelOrFinish();
				break;
			}
			case BUTTON_MSG_OK: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->HideElement(mainGame->wMessage);
				mainGame->actionSignal.Set();
				break;
			}
			case BUTTON_YES: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				switch(mainGame->dInfo.curMsg) {
				case MSG_SELECT_YESNO:
				case MSG_SELECT_EFFECTYN: {
					if(highlighting_card)
						highlighting_card->is_highlighting = false;
					highlighting_card = 0;
					DuelClient::SetResponseI(1);
					mainGame->HideElement(mainGame->wQuery, true);
					break;
				}
				case MSG_SELECT_CARD:
				case MSG_SELECT_TRIBUTE:
				case MSG_SELECT_SUM: {
					mainGame->HideElement(mainGame->wQuery);
					if(select_panalmode)
						mainGame->dField.ShowSelectCard(true);
					break;
				}
				case MSG_SELECT_CHAIN: {
					mainGame->HideElement(mainGame->wQuery);
					if (!chain_forced) {
						ShowCancelOrFinishButton(1);
					}
					break;
				}
				default: {
					mainGame->HideElement(mainGame->wQuery);
					break;
				}
				}
				break;
			}
			case BUTTON_NO: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				switch(mainGame->dInfo.curMsg) {
				case MSG_SELECT_YESNO:
				case MSG_SELECT_EFFECTYN: {
					if(highlighting_card)
						highlighting_card->is_highlighting = false;
					highlighting_card = 0;
					DuelClient::SetResponseI(0);
					mainGame->HideElement(mainGame->wQuery, true);
					break;
				}
				case MSG_SELECT_CHAIN: {
					DuelClient::SetResponseI(-1);
					mainGame->HideElement(mainGame->wQuery, true);
					ShowCancelOrFinishButton(0);
					break;
				}
				case MSG_SELECT_CARD:
				case MSG_SELECT_TRIBUTE:
				case MSG_SELECT_SUM: {
					SetResponseSelectedCards();
					ShowCancelOrFinishButton(0);
					mainGame->HideElement(mainGame->wQuery, true);
					break;
				}
				default: {
					mainGame->HideElement(mainGame->wQuery);
					break;
				}
				}
				break;
			}
			case BUTTON_POS_AU: {
				DuelClient::SetResponseI(POS_FACEUP_ATTACK);
				mainGame->HideElement(mainGame->wPosSelect, true);
				break;
			}
			case BUTTON_POS_AD: {
				DuelClient::SetResponseI(POS_FACEDOWN_ATTACK);
				mainGame->HideElement(mainGame->wPosSelect, true);
				break;
			}
			case BUTTON_POS_DU: {
				DuelClient::SetResponseI(POS_FACEUP_DEFENSE);
				mainGame->HideElement(mainGame->wPosSelect, true);
				break;
			}
			case BUTTON_POS_DD: {
				DuelClient::SetResponseI(POS_FACEDOWN_DEFENSE);
				mainGame->HideElement(mainGame->wPosSelect, true);
				break;
			}
			case BUTTON_OPTION_PREV: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				selected_option--;
				mainGame->btnOptionn->setVisible(true);
				if(selected_option == 0)
					mainGame->btnOptionp->setVisible(false);
				mainGame->SetStaticText(mainGame->stOptions, 350 * mainGame->xScale, mainGame->guiFont, dataManager.GetDesc(select_options[selected_option]));
				break;
			}
			case BUTTON_OPTION_NEXT: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				selected_option++;
				mainGame->btnOptionp->setVisible(true);
				if(selected_option == select_options.size() - 1)
					mainGame->btnOptionn->setVisible(false);
				mainGame->SetStaticText(mainGame->stOptions, 350 * mainGame->xScale, mainGame->guiFont, dataManager.GetDesc(select_options[selected_option]));
				break;
			}
			case BUTTON_OPTION_0:
			case BUTTON_OPTION_1:
			case BUTTON_OPTION_2:
			case BUTTON_OPTION_3:
			case BUTTON_OPTION_4: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				int step = mainGame->scrOption->isVisible() ? mainGame->scrOption->getPos() : 0;
				selected_option = id - BUTTON_OPTION_0 + step;
				SetResponseSelectedOption();
				ShowCancelOrFinishButton(0);
				break;
			}
			case BUTTON_OPTION_OK: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				SetResponseSelectedOption();
				ShowCancelOrFinishButton(0);
				break;
			}
			case BUTTON_ANNUMBER_1:
			case BUTTON_ANNUMBER_2:
			case BUTTON_ANNUMBER_3:
			case BUTTON_ANNUMBER_4:
			case BUTTON_ANNUMBER_5:
			case BUTTON_ANNUMBER_6:
			case BUTTON_ANNUMBER_7:
			case BUTTON_ANNUMBER_8:
			case BUTTON_ANNUMBER_9:
			case BUTTON_ANNUMBER_10:
			case BUTTON_ANNUMBER_11:
			case BUTTON_ANNUMBER_12: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				for(int i = 0; i < (int)mainGame->cbANNumber->getItemCount(); ++i) {
					if(id - BUTTON_ANNUMBER_1 + 1 == mainGame->cbANNumber->getItemData(i)) {
						mainGame->cbANNumber->setSelected(i);
						break;
					}
				}
				for(int i = 0; i < 12; ++i) {
					mainGame->btnANNumber[i]->setPressed(event.GUIEvent.Caller == mainGame->btnANNumber[i]);
				}
				mainGame->btnANNumberOK->setEnabled(true);
				break;
			}
			case BUTTON_ANNUMBER_OK: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				DuelClient::SetResponseI(mainGame->cbANNumber->getSelected());
				mainGame->HideElement(mainGame->wANNumber, true);
				break;
			}
			case BUTTON_ANCARD_OK: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				int sel = mainGame->lstANCard->getSelected();
				if(sel == -1)
					break;
				DuelClient::SetResponseI(ancard[sel]);
				mainGame->HideElement(mainGame->wANCard, true);
				break;
			}
			case BUTTON_CMD_SHUFFLE: {
				mainGame->btnShuffle->setVisible(false);
				DuelClient::SetResponseI(8);
				DuelClient::SendResponse();
				break;
			}
			case BUTTON_CMD_ACTIVATE:
			case BUTTON_CMD_RESET: {
				HideMenu();
				ShowCancelOrFinishButton(0);
				if(!list_command) {
					if(!menu_card)
						break;
					select_options.clear();
					select_options_index.clear();
					for (size_t i = 0; i < activatable_cards.size(); ++i) {
						if (activatable_cards[i] == menu_card) {
							if(activatable_descs[i].second & EDESC_OPERATION)
								continue;
							else if(activatable_descs[i].second & EDESC_RESET) {
								if(id == BUTTON_CMD_ACTIVATE) continue;
							} else {
								if(id == BUTTON_CMD_RESET) continue;
							}
							select_options.push_back(activatable_descs[i].first);
							select_options_index.push_back(i);
						}
					}
					if (select_options.size() == 1) {
						int index = select_options_index[0];
						if (mainGame->dInfo.curMsg == MSG_SELECT_IDLECMD) {
							DuelClient::SetResponseI((index << 16) + 5);
						} else if (mainGame->dInfo.curMsg == MSG_SELECT_BATTLECMD) {
							DuelClient::SetResponseI(index << 16);
						} else {
							DuelClient::SetResponseI(index);
						}
						DuelClient::SendResponse();
					} else {
						command_card = menu_card;
						ShowSelectOption();
						select_ready = false;
						ShowCancelOrFinishButton(1);
					}
				} else {
					selectable_cards.clear();
					bool is_continuous = false;
					switch(command_location) {
					case LOCATION_DECK: {
						for(size_t i = 0; i < deck[command_controler].size(); ++i)
							if(deck[command_controler][i]->cmdFlag & COMMAND_ACTIVATE)
								selectable_cards.push_back(deck[command_controler][i]);
						break;
					}
					case LOCATION_GRAVE: {
						for(size_t i = 0; i < grave[command_controler].size(); ++i)
							if(grave[command_controler][i]->cmdFlag & COMMAND_ACTIVATE)
								selectable_cards.push_back(grave[command_controler][i]);
						break;
					}
					case LOCATION_REMOVED: {
						for(size_t i = 0; i < remove[command_controler].size(); ++i)
							if(remove[command_controler][i]->cmdFlag & COMMAND_ACTIVATE)
								selectable_cards.push_back(remove[command_controler][i]);
						break;
					}
					case LOCATION_EXTRA: {
						for(size_t i = 0; i < extra[command_controler].size(); ++i)
							if(extra[command_controler][i]->cmdFlag & COMMAND_ACTIVATE)
								selectable_cards.push_back(extra[command_controler][i]);
						break;
					}
					case POSITION_HINT: {
						is_continuous = true;
						selectable_cards = conti_cards;
						std::sort(selectable_cards.begin(), selectable_cards.end());
						auto eit = std::unique(selectable_cards.begin(), selectable_cards.end());
						selectable_cards.erase(eit, selectable_cards.end());
						break;
					}
					}
					if (!is_continuous) {
						mainGame->stCardSelect->setText(dataManager.GetSysString(566));
						list_command = COMMAND_ACTIVATE;
					} else {
						mainGame->stCardSelect->setText(dataManager.GetSysString(568));
						list_command = COMMAND_OPERATION;
					}
					std::sort(selectable_cards.begin(), selectable_cards.end(), ClientCard::client_card_sort);
					ShowSelectCard(true, is_continuous);
				}
				break;
			}
			case BUTTON_CMD_SUMMON: {
                // 隐藏右键菜单
                HideMenu();
                // 如果没有选中卡片则退出
                if(!menu_card)
                    break;
                // 遍历可召唤卡片列表，找到与菜单选中卡片相同的卡片
                for(size_t i = 0; i < summonable_cards.size(); ++i) {
                    if(summonable_cards[i] == menu_card) {
                        // 清除所有卡片的命令标记
                        ClearCommandFlag();
                        // 设置响应为召唤操作，i<<16表示召唤第i张可召唤的卡片
                        DuelClient::SetResponseI(i << 16);
                        // 发送响应到服务器
                        DuelClient::SendResponse();
                        break;
                    }
                }
                break;
            }
            case BUTTON_CMD_SPSUMMON: {
                // 隐藏右键菜单
                HideMenu();
                // 如果不是列表命令模式
                if(!list_command) {
                    // 如果没有选中菜单卡片则退出
                    if(!menu_card)
                        break;
                    // 遍历可特殊召唤卡片列表，找到与菜单选中卡片相同的卡片
                    for(size_t i = 0; i < spsummonable_cards.size(); ++i) {
                        if(spsummonable_cards[i] == menu_card) {
                            // 清除所有卡片的命令标记
                            ClearCommandFlag();
                            // 设置响应为特殊召唤操作，(i<<16)+1表示特殊召唤第i张可特殊召唤的卡片
                            DuelClient::SetResponseI((i << 16) + 1);
                            // 发送响应到服务器
                            DuelClient::SendResponse();
                            break;
                        }
                    }
                } else {
                    // 清空可选择卡片列表
                    selectable_cards.clear();
                    // 根据命令位置选择相应的卡片
                    switch(command_location) {
                        case LOCATION_DECK: {
                            // 从卡组中筛选出可特殊召唤的卡片
                            for(size_t i = 0; i < deck[command_controler].size(); ++i)
                                if(deck[command_controler][i]->cmdFlag & COMMAND_SPSUMMON)
                                    selectable_cards.push_back(deck[command_controler][i]);
                            break;
                        }
                        case LOCATION_GRAVE: {
                            // 从墓地中筛选出可特殊召唤的卡片
                            for(size_t i = 0; i < grave[command_controler].size(); ++i)
                                if(grave[command_controler][i]->cmdFlag & COMMAND_SPSUMMON)
                                    selectable_cards.push_back(grave[command_controler][i]);
                            break;
                        }
                        case LOCATION_EXTRA: {
                            // 从额外卡组中筛选出可特殊召唤的卡片
                            for(size_t i = 0; i < extra[command_controler].size(); ++i)
                                if(extra[command_controler][i]->cmdFlag & COMMAND_SPSUMMON)
                                    selectable_cards.push_back(extra[command_controler][i]);
                            break;
                        }
                    }
                    // 设置列表命令为特殊召唤命令
                    list_command = COMMAND_SPSUMMON;
                    // 设置卡片选择窗口标题为"选择怪兽"
                    mainGame->stCardSelect->setText(dataManager.GetSysString(509));
                    // 显示卡片选择窗口
                    ShowSelectCard();
                    // 设置选择未就绪状态
                    select_ready = false;
                    // 显示取消/完成按钮
                    ShowCancelOrFinishButton(1);
                }
                break;
            }
            case BUTTON_CMD_MSET: {
                // 隐藏右键菜单
                HideMenu();
                // 如果没有选中菜单卡片则退出
                if(!menu_card)
                    break;
                // 遍历可放置卡片列表，找到与菜单选中卡片相同的卡片
                for(size_t i = 0; i < msetable_cards.size(); ++i) {
                    if(msetable_cards[i] == menu_card) {
                        // 设置响应为放置操作，(i<<16)+3表示放置第i张可放置的卡片
                        DuelClient::SetResponseI((i << 16) + 3);
                        // 发送响应到服务器
                        DuelClient::SendResponse();
                        break;
                    }
                }
                break;
            }
			case BUTTON_CMD_SSET: {
				HideMenu();
				if(!menu_card)
					break;
				for(size_t i = 0; i < ssetable_cards.size(); ++i) {
					if(ssetable_cards[i] == menu_card) {
						DuelClient::SetResponseI((i << 16) + 4);
						DuelClient::SendResponse();
						break;
					}
				}
				break;
			}
			case BUTTON_CMD_REPOS: {
				HideMenu();
				if(!menu_card)
					break;
				for(size_t i = 0; i < reposable_cards.size(); ++i) {
					if(reposable_cards[i] == menu_card) {
						DuelClient::SetResponseI((i << 16) + 2);
						DuelClient::SendResponse();
						break;
					}
				}
				break;
			}
			case BUTTON_CMD_ATTACK: {
				HideMenu();
				if(!menu_card)
					break;
				for(size_t i = 0; i < attackable_cards.size(); ++i) {
					if(attackable_cards[i] == menu_card) {
						DuelClient::SetResponseI((i << 16) + 1);
						DuelClient::SendResponse();
						break;
					}
				}
				break;
			}
			case BUTTON_CMD_SHOWLIST: {
				HideMenu();
				selectable_cards.clear();
				wchar_t formatBuffer[2048];
				switch(command_location) {
				case LOCATION_DECK: {
					selectable_cards.assign(deck[command_controler].rbegin(), deck[command_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1000), deck[command_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_MZONE: {
					ClientCard* pcard = mzone[command_controler][command_sequence];
					selectable_cards.assign(pcard->overlayed.begin(), pcard->overlayed.end());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1007), pcard->overlayed.size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_GRAVE: {
					selectable_cards.assign(grave[command_controler].rbegin(), grave[command_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1004), grave[command_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_REMOVED: {
					selectable_cards.assign(remove[command_controler].rbegin(), remove[command_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1005), remove[command_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_EXTRA: {
					selectable_cards.assign(extra[command_controler].rbegin(), extra[command_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1006), extra[command_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				}
				list_command = COMMAND_LIST;
				std::sort(selectable_cards.begin(), selectable_cards.end(), ClientCard::client_card_sort);
				ShowSelectCard(true);
				break;
			}
			case BUTTON_PHASE: {
				mainGame->btnPhaseStatus->setPressed(true);
				break;
			}
			case BUTTON_BP: {
				if(mainGame->dInfo.curMsg == MSG_SELECT_IDLECMD) {
					DuelClient::SetResponseI(6);
					DuelClient::SendResponse();
				}
				break;
			}
			case BUTTON_M2: {
				if(mainGame->dInfo.curMsg == MSG_SELECT_BATTLECMD) {
					DuelClient::SetResponseI(2);
					DuelClient::SendResponse();
				}
				break;
			}
			case BUTTON_EP: {
				if(mainGame->dInfo.curMsg == MSG_SELECT_BATTLECMD) {
					DuelClient::SetResponseI(3);
					DuelClient::SendResponse();
				} else if(mainGame->dInfo.curMsg == MSG_SELECT_IDLECMD) {
					DuelClient::SetResponseI(7);
					DuelClient::SendResponse();
				}
				break;
			}
			case BUTTON_CARD_0:
			case BUTTON_CARD_1:
			case BUTTON_CARD_2:
			case BUTTON_CARD_3:
			case BUTTON_CARD_4: {
			    if (is_selectable) {
    				if(mainGame->dInfo.isReplay)
    					break;
    				switch(mainGame->dInfo.curMsg) {
    				case MSG_SELECT_IDLECMD:
    				case MSG_SELECT_BATTLECMD:
    				case MSG_SELECT_CHAIN: {
    					if(list_command == COMMAND_LIST)
    						break;
    					if(list_command == COMMAND_SPSUMMON) {
    						command_card = selectable_cards[id - BUTTON_CARD_0 + mainGame->scrCardList->getPos() / 10];
    						int index = 0;
    						while(spsummonable_cards[index] != command_card) index++;
    						DuelClient::SetResponseI((index << 16) + 1);
    						mainGame->HideElement(mainGame->wCardSelect, true);
    						ShowCancelOrFinishButton(0);
    						break;
    					}
    					if(list_command == COMMAND_ACTIVATE || list_command == COMMAND_OPERATION) {
    						command_card = selectable_cards[id - BUTTON_CARD_0 + mainGame->scrCardList->getPos() / 10];
    						select_options.clear();
							select_options_index.clear();
    						for (size_t i = 0; i < activatable_cards.size(); ++i) {
    							if (activatable_cards[i] == command_card) {
									if(activatable_descs[i].second & EDESC_OPERATION) {
    									if(list_command == COMMAND_ACTIVATE) continue;
    								} else {
    									if(list_command == COMMAND_OPERATION) continue;
    								}
    								select_options.push_back(activatable_descs[i].first);
									select_options_index.push_back(i);
    							}
    						}
    						if (select_options.size() == 1) {
								int index = select_options_index[0];
    							if (mainGame->dInfo.curMsg == MSG_SELECT_IDLECMD) {
    								DuelClient::SetResponseI((index << 16) + 5);
    							} else if (mainGame->dInfo.curMsg == MSG_SELECT_BATTLECMD) {
    								DuelClient::SetResponseI(index << 16);
    							} else {
    								DuelClient::SetResponseI(index);
    							}
	    						mainGame->HideElement(mainGame->wCardSelect, true);
		    				} else {
			    				mainGame->wCardSelect->setVisible(false);
				    			ShowSelectOption();
					    	}
						    break;
    					}
    					break;
	    			}
		    		case MSG_SELECT_CARD: {
			    		command_card = selectable_cards[id - BUTTON_CARD_0 + mainGame->scrCardList->getPos() / 10];
				    	if (command_card->is_selected) {
					    	command_card->is_selected = false;
						    int i = 0;
    						while(selected_cards[i] != command_card) i++;
	    					selected_cards.erase(selected_cards.begin() + i);
		    				if(command_card->controler)
			    				mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0xff5a5a5a);
				    		else mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0xff56649f);
					    } else {
						    command_card->is_selected = true;
    						mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0x6011113d);
    						selected_cards.push_back(command_card);
    					}
    					int sel = selected_cards.size();
    					if (sel >= select_max) {
    						SetResponseSelectedCards();
    						ShowCancelOrFinishButton(0);
    						mainGame->HideElement(mainGame->wCardSelect, true);
    					} else if (sel >= select_min) {
    						select_ready = true;
    						mainGame->btnSelectOK->setVisible(true);
    						ShowCancelOrFinishButton(2);
    					} else {
    						select_ready = false;
    						mainGame->btnSelectOK->setVisible(false);
    						if (select_cancelable && sel == 0)
    							ShowCancelOrFinishButton(1);
    						else
    							ShowCancelOrFinishButton(0);
    					}
    					break;
    				}
    				case MSG_SELECT_UNSELECT_CARD: {
    					command_card = selectable_cards[id - BUTTON_CARD_0 + mainGame->scrCardList->getPos() / 10];
    					if (command_card->is_selected) {
    						command_card->is_selected = false;
    						if(command_card->controler)
    							mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0xff5a5a5a);
    						else mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0xff56649f);
    					} else {
    						command_card->is_selected = true;
	    					mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0x6011113d);
		    			}
			    		selected_cards.push_back(command_card);
				    	if (selected_cards.size() > 0) {
					    	SetResponseSelectedCards();
						    ShowCancelOrFinishButton(0);
    						mainGame->HideElement(mainGame->wCardSelect, true);
	    				}
		    			break;
			    	}
				    case MSG_SELECT_SUM: {
					    command_card = selectable_cards[id - BUTTON_CARD_0 + mainGame->scrCardList->getPos() / 10];
						if (command_card->is_selected) {
							command_card->is_selected = false;
							int i = 0;
							while(selected_cards[i] != command_card) i++;
							selected_cards.erase(selected_cards.begin() + i);
							if(command_card->controler)
								mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0xff5a5a5a);
							else mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0xff56649f);
						} else {
							command_card->is_selected = true;
							mainGame->stCardPos[id - BUTTON_CARD_0]->setBackgroundColor(0x6011113d);
							selected_cards.push_back(command_card);
						}
	    				ShowSelectSum(true);
		    			break;
			    	}
				    case MSG_SORT_CARD: {
					    int offset = mainGame->scrCardList->getPos() / 10;
    					int sel_seq = id - BUTTON_CARD_0 + offset;
	    				wchar_t formatBuffer[2048];
		    			if(sort_list[sel_seq]) {
			    			select_min--;
				    		int sel = sort_list[sel_seq];
					    	sort_list[sel_seq] = 0;
						    for(int i = 0; i < select_max; ++i)
							    if(sort_list[i] > sel)
								    sort_list[i]--;
    						for(int i = 0; i < 5; ++i) {
	    						if(offset + i >= select_max)
		    						break;
			    				if(sort_list[offset + i]) {
				    				myswprintf(formatBuffer, L"%d", sort_list[offset + i]);
					    			mainGame->stCardPos[i]->setText(formatBuffer);
						    	} else mainGame->stCardPos[i]->setText(L"");
    						}
	    				} else {
		    				select_min++;
			    			sort_list[sel_seq] = select_min;
				    		myswprintf(formatBuffer, L"%d", select_min);
					    	mainGame->stCardPos[id - BUTTON_CARD_0]->setText(formatBuffer);
						    if(select_min == select_max) {
								unsigned char respbuf[SIZE_RETURN_VALUE];
    							for(int i = 0; i < select_max; ++i)
	    							respbuf[i] = sort_list[i] - 1;
		    					DuelClient::SetResponseB(respbuf, select_max);
			    				mainGame->HideElement(mainGame->wCardSelect, true);
				    			sort_list.clear();
					    	}
    					}
	    				break;
		    		}
			    	}
                }
				break;
			}
			case BUTTON_CARD_SEL_OK: {
				if(mainGame->dInfo.isReplay) {
					mainGame->HideElement(mainGame->wCardSelect);
					break;
				}
				if(mainGame->dInfo.curMsg == MSG_SELECT_CARD || mainGame->dInfo.curMsg == MSG_SELECT_SUM) {
					if(select_ready) {
						SetResponseSelectedCards();
						ShowCancelOrFinishButton(0);
						mainGame->HideElement(mainGame->wCardSelect, true);
					}
					break;
				} else if(mainGame->dInfo.curMsg == MSG_CONFIRM_CARDS) {
					mainGame->HideElement(mainGame->wCardSelect);
					mainGame->actionSignal.Set();
					break;
				} else if(mainGame->dInfo.curMsg == MSG_SELECT_UNSELECT_CARD){
					DuelClient::SetResponseI(-1);
					ShowCancelOrFinishButton(0);
					mainGame->HideElement(mainGame->wCardSelect, true);
				} else {
					mainGame->HideElement(mainGame->wCardSelect);
					if(mainGame->dInfo.curMsg == MSG_SELECT_CHAIN && !chain_forced)
						ShowCancelOrFinishButton(1);
					break;
				}
				break;
			}
			case BUTTON_CARD_DISP_OK: {
				mainGame->HideElement(mainGame->wCardDisplay);
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_CHECKBOX_CHANGED: {
			switch(id) {
			case CHECK_ATTRIBUTE: {
				int att = 0, filter = 0x1, count = 0;
				for(int i = 0; i < 7; ++i, filter <<= 1) {
					if(mainGame->chkAttribute[i]->isChecked()) {
						att |= filter;
						count++;
					}
				}
				if(count == announce_count) {
					DuelClient::SetResponseI(att);
					mainGame->HideElement(mainGame->wANAttribute, true);
				}
				break;
			}
			case CHECK_RACE: {
				int rac = 0, filter = 0x1, count = 0;
				for(int i = 0; i < RACES_COUNT; ++i, filter <<= 1) {
					if(mainGame->chkRace[i]->isChecked()) {
						rac |= filter;
						count++;
					}
				}
				if(count == announce_count) {
					DuelClient::SetResponseI(rac);
					mainGame->HideElement(mainGame->wANRace, true);
				}
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_LISTBOX_CHANGED: {
			switch(id) {
			case LISTBOX_ANCARD: {
				int sel = mainGame->lstANCard->getSelected();
				if(sel != -1) {
					mainGame->ShowCardInfo(ancard[sel]);
				}
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_SCROLL_BAR_CHANGED: {
			switch(id) {
			case SCROLL_OPTION_SELECT: {
				int step = mainGame->scrOption->isVisible() ? mainGame->scrOption->getPos() : 0;
				for(int i = 0; i < 5; i++) {
					const wchar_t* option = dataManager.GetDesc(select_options[i + step]);
					mainGame->btnOption[i]->setText(option);
				}
				break;
			}
			case SCROLL_CARD_SELECT: {
				int pos = mainGame->scrCardList->getPos() / 10;
				for(int i = 0; i < 5; ++i) {
					// draw selectable_cards[i + pos] in btnCardSelect[i]
					mainGame->stCardPos[i]->enableOverrideColor(false);
					// image
					if(selectable_cards[i + pos]->code)
						mainGame->btnCardSelect[i]->setImage(imageManager.GetTexture(selectable_cards[i + pos]->code));
					else if(select_continuous)
						mainGame->btnCardSelect[i]->setImage(imageManager.GetTexture(selectable_cards[i + pos]->chain_code));
					else
						mainGame->btnCardSelect[i]->setImage(imageManager.tCover[selectable_cards[i + pos]->controler + 2]);
					mainGame->btnCardSelect[i]->setRelativePosition(irr::core::rect<irr::s32>((30 + i * 125)  * mainGame->yScale, 65 * mainGame->yScale, (30 + 120 + i * 125)  * mainGame->yScale, 235  * mainGame->yScale));
					// text
					wchar_t formatBuffer[2048];
					if(mainGame->dInfo.curMsg == MSG_SORT_CARD) {
						if(sort_list[pos + i])
							myswprintf(formatBuffer, L"%d", sort_list[pos + i]);
						else
							myswprintf(formatBuffer, L"");
					} else {
						if (select_continuous)
							myswprintf(formatBuffer, L"%ls", dataManager.unknown_string);
						else if (cant_check_grave && selectable_cards[i]->location == LOCATION_GRAVE)
							myswprintf(formatBuffer, L"%ls", dataManager.FormatLocation(selectable_cards[i]->location, 0));
						else if (selectable_cards[i + pos]->location == LOCATION_OVERLAY)
							myswprintf(formatBuffer, L"%ls[%d](%d)",
								dataManager.FormatLocation(selectable_cards[i + pos]->overlayTarget), selectable_cards[i + pos]->overlayTarget->sequence + 1, selectable_cards[i + pos]->sequence + 1);
						else
							myswprintf(formatBuffer, L"%ls[%d]", dataManager.FormatLocation(selectable_cards[i + pos]), selectable_cards[i + pos]->sequence + 1);
					}
					mainGame->stCardPos[i]->setText(formatBuffer);
					// color
					if(select_continuous)
						mainGame->stCardPos[i]->setBackgroundColor(0xff56649f);
					else if(selectable_cards[i + pos]->location == LOCATION_OVERLAY) {
						if(selectable_cards[i + pos]->owner != selectable_cards[i + pos]->overlayTarget->controler)
							mainGame->stCardPos[i]->setOverrideColor(0xff000099);
						if(selectable_cards[i + pos]->is_selected)
							mainGame->stCardPos[i]->setBackgroundColor(0x6011113d);
						else if(selectable_cards[i + pos]->overlayTarget->controler)
							mainGame->stCardPos[i]->setBackgroundColor(0xff5a5a5a);
						else
							mainGame->stCardPos[i]->setBackgroundColor(0xff56649f);
					} else if(selectable_cards[i + pos]->location == LOCATION_DECK || selectable_cards[i + pos]->location == LOCATION_EXTRA || selectable_cards[i + pos]->location == LOCATION_REMOVED) {
						if(selectable_cards[i + pos]->position & POS_FACEDOWN)
							mainGame->stCardPos[i]->setOverrideColor(0xff000099);
						if(selectable_cards[i + pos]->is_selected)
							mainGame->stCardPos[i]->setBackgroundColor(0x6011113d);
						else if(selectable_cards[i + pos]->controler)
							mainGame->stCardPos[i]->setBackgroundColor(0xff5a5a5a);
						else
							mainGame->stCardPos[i]->setBackgroundColor(0xff56649f);
					} else {
						if(selectable_cards[i + pos]->is_selected)
							mainGame->stCardPos[i]->setBackgroundColor(0x6011113d);
						else if(selectable_cards[i + pos]->controler)
							mainGame->stCardPos[i]->setBackgroundColor(0xff5a5a5a);
						else
							mainGame->stCardPos[i]->setBackgroundColor(0xff56649f);
					}
				}
				break;
			}
			case SCROLL_CARD_DISPLAY: {
				int pos = mainGame->scrDisplayList->getPos() / 10;
				for(int i = 0; i < 5; ++i) {
					// draw display_cards[i + pos] in btnCardDisplay[i]
					mainGame->stDisplayPos[i]->enableOverrideColor(false);
					if(display_cards[i + pos]->code)
						mainGame->btnCardDisplay[i]->setImage(imageManager.GetTexture(display_cards[i + pos]->code));
					else
						mainGame->btnCardDisplay[i]->setImage(imageManager.tCover[display_cards[i + pos]->controler + 2]);
					mainGame->btnCardDisplay[i]->setRelativePosition(irr::core::rect<irr::s32>((30 + i * 125) * mainGame->yScale, 65 * mainGame->yScale, (30 + 120 + i * 125) * mainGame->yScale, 235 * mainGame->yScale));
					wchar_t formatBuffer[2048];
					if(display_cards[i + pos]->location == LOCATION_OVERLAY)
						myswprintf(formatBuffer, L"%ls[%d](%d)",
							dataManager.FormatLocation(display_cards[i + pos]->overlayTarget), display_cards[i + pos]->overlayTarget->sequence + 1, display_cards[i + pos]->sequence + 1);
					else
						myswprintf(formatBuffer, L"%ls[%d]", dataManager.FormatLocation(display_cards[i + pos]), display_cards[i + pos]->sequence + 1);
					mainGame->stDisplayPos[i]->setText(formatBuffer);
					if(display_cards[i + pos]->location == LOCATION_OVERLAY) {
						if(display_cards[i + pos]->owner != display_cards[i + pos]->overlayTarget->controler)
							mainGame->stDisplayPos[i]->setOverrideColor(0xff000099);
						// BackgroundColor: controller of the xyz monster
						if(display_cards[i + pos]->overlayTarget->controler)
							mainGame->stDisplayPos[i]->setBackgroundColor(0xff5a5a5a);
						else
							mainGame->stDisplayPos[i]->setBackgroundColor(0xff56649f);
					} else if(display_cards[i + pos]->location == LOCATION_EXTRA || display_cards[i + pos]->location == LOCATION_REMOVED) {
						if(display_cards[i + pos]->position & POS_FACEDOWN)
							mainGame->stDisplayPos[i]->setOverrideColor(0xff000099);
						if(display_cards[i + pos]->controler)
							mainGame->stDisplayPos[i]->setBackgroundColor(0xff5a5a5a);
						else
							mainGame->stDisplayPos[i]->setBackgroundColor(0xff56649f);
					} else {
						if(display_cards[i + pos]->controler)
							mainGame->stDisplayPos[i]->setBackgroundColor(0xff5a5a5a);
						else
							mainGame->stDisplayPos[i]->setBackgroundColor(0xff56649f);
					}
				}
				break;
			}
			break;
			}
		}
		case irr::gui::EGET_EDITBOX_CHANGED: {
			switch(id) {
			case EDITBOX_ANCARD: {
				UpdateDeclarableList();
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_EDITBOX_ENTER: {
			switch(id) {
			case EDITBOX_ANCARD: {
				UpdateDeclarableList();
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_ELEMENT_HOVERED: {
			if(id >= BUTTON_CARD_0 && id <= BUTTON_CARD_4) {
				int pos = mainGame->scrCardList->getPos() / 10;
				ClientCard* mcard = selectable_cards[id - BUTTON_CARD_0 + pos];
				SetShowMark(mcard, true);
				ShowCardInfoInList(mcard, mainGame->btnCardSelect[id - BUTTON_CARD_0], mainGame->wCardSelect);
				if(mcard->code) {
					mainGame->ShowCardInfo(mcard->code);
				} else {
					mainGame->ClearCardInfo(mcard->controler);
				}
			}
			if(id >= BUTTON_DISPLAY_0 && id <= BUTTON_DISPLAY_4) {
				int pos = mainGame->scrDisplayList->getPos() / 10;
				ClientCard* mcard = display_cards[id - BUTTON_DISPLAY_0 + pos];
				SetShowMark(mcard, true);
				ShowCardInfoInList(mcard, mainGame->btnCardDisplay[id - BUTTON_DISPLAY_0], mainGame->wCardDisplay);
				if(mcard->code) {
					mainGame->ShowCardInfo(mcard->code);
				} else {
					mainGame->ClearCardInfo(mcard->controler);
				}
			}
			if(id == TEXT_CARD_LIST_TIP) {
				mainGame->stCardListTip->setVisible(true);
			}
			break;
		}
		case irr::gui::EGET_ELEMENT_LEFT: {
			if(id >= BUTTON_CARD_0 && id <= BUTTON_CARD_4 && mainGame->stCardListTip->isVisible()) {
				int pos = mainGame->scrCardList->getPos() / 10;
				ClientCard* mcard = selectable_cards[id - BUTTON_CARD_0 + pos];
				SetShowMark(mcard, false);
				mainGame->stCardListTip->setVisible(false);
			}
			if(id >= BUTTON_DISPLAY_0 && id <= BUTTON_DISPLAY_4 && mainGame->stCardListTip->isVisible()) {
				int pos = mainGame->scrDisplayList->getPos() / 10;
				ClientCard* mcard = display_cards[id - BUTTON_DISPLAY_0 + pos];
				SetShowMark(mcard, false);
				mainGame->stCardListTip->setVisible(false);
			}
			if(id == TEXT_CARD_LIST_TIP) {
				mainGame->stCardListTip->setVisible(false);
			}
			break;
		}
		default:
			break;
		}
		break;
	}
	case irr::EET_MOUSE_INPUT_EVENT: {
		switch(event.MouseInput.Event) {
		case irr::EMIE_LMOUSE_LEFT_UP: {
			if(!mainGame->dInfo.isStarted)
				break;
			irr::s32 x = event.MouseInput.X;
			irr::s32 y = event.MouseInput.Y;
			hovered_location = 0;
			irr::core::vector2di pos(x, y);
			if(x < 300 * mainGame->xScale)
				break;
			if(mainGame->gameConf.control_mode == 1) {
				mainGame->always_chain = event.MouseInput.isLeftPressed();
				mainGame->ignore_chain = false;
				mainGame->chain_when_avail = false;
				UpdateChainButtons();
			}
			if(mainGame->wCmdMenu->isVisible() && !mainGame->wCmdMenu->getRelativePosition().isPointInside(pos))
				HideMenu();
			if(mainGame->btnBP->isVisible() && mainGame->btnBP->getAbsolutePosition().isPointInside(pos))
				break;
			if(mainGame->btnM2->isVisible() && mainGame->btnM2->getAbsolutePosition().isPointInside(pos))
				break;
			if(panel && panel->isVisible())
				break;
			GetHoverField(x, y);
			if(hovered_location & 0xe)
				clicked_card = GetCard(hovered_controler, hovered_location, hovered_sequence);
			else clicked_card = 0;
			wchar_t formatBuffer[2048];
			if(mainGame->dInfo.isReplay) {
				if(mainGame->wCardSelect->isVisible())
					break;
				selectable_cards.clear();
				switch(hovered_location) {
				case LOCATION_DECK: {
					if(deck[hovered_controler].size() == 0)
						break;
					selectable_cards.assign(deck[hovered_controler].rbegin(), deck[hovered_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1000), deck[hovered_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_MZONE: {
					if(!clicked_card || clicked_card->overlayed.size() == 0)
						break;
					selectable_cards.assign(clicked_card->overlayed.begin(), clicked_card->overlayed.end());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1007), clicked_card->overlayed.size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_GRAVE: {
					if(grave[hovered_controler].size() == 0)
						break;
					selectable_cards.assign(grave[hovered_controler].rbegin(), grave[hovered_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1004), grave[hovered_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_REMOVED: {
					if(remove[hovered_controler].size() == 0)
						break;
					selectable_cards.assign(remove[hovered_controler].rbegin(), remove[hovered_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1005), remove[hovered_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_EXTRA: {
					if(extra[hovered_controler].size() == 0)
						break;
					selectable_cards.assign(extra[hovered_controler].rbegin(), extra[hovered_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1006), extra[hovered_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				}
				if(selectable_cards.size())
					ShowSelectCard(true);
				break;
			}
			if(mainGame->dInfo.player_type == 7) {
				if(mainGame->wCardSelect->isVisible())
					break;
				selectable_cards.clear();
				switch(hovered_location) {
				case LOCATION_MZONE: {
					if(!clicked_card || clicked_card->overlayed.size() == 0)
						break;
					selectable_cards.assign(clicked_card->overlayed.begin(), clicked_card->overlayed.end());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1007), clicked_card->overlayed.size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_GRAVE: {
					if(grave[hovered_controler].size() == 0)
						break;
					selectable_cards.assign(grave[hovered_controler].rbegin(), grave[hovered_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1004), grave[hovered_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_REMOVED: {
					if (remove[hovered_controler].size() == 0)
						break;
					selectable_cards.assign(remove[hovered_controler].rbegin(), remove[hovered_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1005), remove[hovered_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				case LOCATION_EXTRA: {
					if (extra[hovered_controler].size() == 0)
						break;
					selectable_cards.assign(extra[hovered_controler].rbegin(), extra[hovered_controler].rend());
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(1006), extra[hovered_controler].size());
					mainGame->stCardSelect->setText(formatBuffer);
					break;
				}
				}
				if(selectable_cards.size())
					ShowSelectCard(true);
				break;
			}
			command_controler = hovered_controler;
			command_location = hovered_location;
			command_sequence = hovered_sequence;
			switch(mainGame->dInfo.curMsg) {
			case MSG_WAITING: {
				switch(hovered_location) {
				case LOCATION_MZONE:
				case LOCATION_SZONE: {
					if(!clicked_card || clicked_card->overlayed.size() == 0)
						break;
					ShowMenu(COMMAND_LIST, x, y);
					break;
				}
				case LOCATION_GRAVE: {
					if(grave[hovered_controler].size() == 0)
						break;
					if(cant_check_grave)
						break;
					ShowMenu(COMMAND_LIST, x, y);
					break;
				}
				case LOCATION_REMOVED: {
					if(remove[hovered_controler].size() == 0)
						break;
					ShowMenu(COMMAND_LIST, x, y);
					break;
				}
				case LOCATION_EXTRA: {
					if(extra[hovered_controler].size() == 0)
						break;
					ShowMenu(COMMAND_LIST, x, y);
					break;
				}
				}
				break;
			}
			case MSG_SELECT_BATTLECMD:
			case MSG_SELECT_IDLECMD:
			case MSG_SELECT_CHAIN: {
				switch(hovered_location) {
				case LOCATION_DECK: {
					int command_flag = 0;
					if(deck[hovered_controler].size() == 0)
						break;
					for(size_t i = 0; i < deck[hovered_controler].size(); ++i)
						command_flag |= deck[hovered_controler][i]->cmdFlag;
					if(mainGame->dInfo.isSingleMode)
						command_flag |= COMMAND_LIST;
					list_command = 1;
					ShowMenu(command_flag, x, y);
					break;
				}
				case LOCATION_HAND:
				case LOCATION_MZONE:
				case LOCATION_SZONE: {
					if(!clicked_card)
						break;
					int command_flag = clicked_card->cmdFlag;
					if(clicked_card->overlayed.size())
						command_flag |= COMMAND_LIST;
					list_command = 0;
					ShowMenu(command_flag, x, y);
					break;
				}
				case LOCATION_GRAVE: {
					int command_flag = 0;
					if(grave[hovered_controler].size() == 0)
						break;
					if(cant_check_grave)
						break;
					for(size_t i = 0; i < grave[hovered_controler].size(); ++i)
						command_flag |= grave[hovered_controler][i]->cmdFlag;
					command_flag |= COMMAND_LIST;
					list_command = 1;
					ShowMenu(command_flag, x, y);
					break;
				}
				case LOCATION_REMOVED: {
					int command_flag = 0;
					if(remove[hovered_controler].size() == 0)
						break;
					for(size_t i = 0; i < remove[hovered_controler].size(); ++i)
						command_flag |= remove[hovered_controler][i]->cmdFlag;
					command_flag |= COMMAND_LIST;
					list_command = 1;
					ShowMenu(command_flag, x, y);
					break;
				}
				case LOCATION_EXTRA: {
					int command_flag = 0;
					if(extra[hovered_controler].size() == 0)
						break;
					for(size_t i = 0; i < extra[hovered_controler].size(); ++i)
						command_flag |= extra[hovered_controler][i]->cmdFlag;
					command_flag |= COMMAND_LIST;
					list_command = 1;
					ShowMenu(command_flag, x, y);
					break;
				}
				case POSITION_HINT: {
					int command_flag = 0;
					if(conti_cards.size() == 0)
						break;
					command_flag |= COMMAND_OPERATION;
					list_command = 1;
					ShowMenu(command_flag, x, y);
					break;
				}
				}
				break;
			}
			case MSG_SELECT_PLACE:
			case MSG_SELECT_DISFIELD: {
				if (!(hovered_location & LOCATION_ONFIELD))
					break;
				unsigned int flag = 0x1U << (hovered_sequence + (hovered_controler << 4) + ((hovered_location == LOCATION_MZONE) ? 0 : 8));
				if (flag & selectable_field) {
					if (flag & selected_field) {
						selected_field &= ~flag;
						select_min++;
					} else {
						selected_field |= flag;
						select_min--;
						if (select_min == 0) {
							unsigned char respbuf[80];
							int filter = 1;
							int p = 0;
							for (int i = 0; i < 7; ++i, filter <<= 1) {
								if (selected_field & filter) {
									respbuf[p] = mainGame->LocalPlayer(0);
									respbuf[p + 1] = LOCATION_MZONE;
									respbuf[p + 2] = i;
									p += 3;
								}
							}
							filter = 0x100;
							for (int i = 0; i < 8; ++i, filter <<= 1) {
								if (selected_field & filter) {
									respbuf[p] = mainGame->LocalPlayer(0);
									respbuf[p + 1] = LOCATION_SZONE;
									respbuf[p + 2] = i;
									p += 3;
								}
							}
							filter = 0x10000;
							for (int i = 0; i < 7; ++i, filter <<= 1) {
								if (selected_field & filter) {
									respbuf[p] = mainGame->LocalPlayer(1);
									respbuf[p + 1] = LOCATION_MZONE;
									respbuf[p + 2] = i;
									p += 3;
								}
							}
							filter = 0x1000000;
							for (int i = 0; i < 8; ++i, filter <<= 1) {
								if (selected_field & filter) {
									respbuf[p] = mainGame->LocalPlayer(1);
									respbuf[p + 1] = LOCATION_SZONE;
									respbuf[p + 2] = i;
									p += 3;
								}
							}
							selectable_field = 0;
							selected_field = 0;
							DuelClient::SetResponseB(respbuf, p);
							DuelClient::SendResponse();
							ShowCancelOrFinishButton(0);
						}
					}
				}
				break;
			}
			case MSG_SELECT_CARD: {
				if(!(hovered_location & 0xe) || !clicked_card || !clicked_card->is_selectable)
					break;
				if(clicked_card->is_selected) {
					clicked_card->is_selected = false;
					int i = 0;
					while(selected_cards[i] != clicked_card) i++;
					selected_cards.erase(selected_cards.begin() + i);
				} else {
					clicked_card->is_selected = true;
					selected_cards.push_back(clicked_card);
				}
				if((int)selected_cards.size() >= select_max) {
					SetResponseSelectedCards();
					ShowCancelOrFinishButton(0);
					DuelClient::SendResponse();
				} else if((int)selected_cards.size() >= select_min) {
					if(selected_cards.size() == selectable_cards.size()) {
						SetResponseSelectedCards();
						ShowCancelOrFinishButton(0);
						DuelClient::SendResponse();
					} else {
						select_ready = true;
						ShowCancelOrFinishButton(2);
					}
				} else {
					select_ready = false;
					if(select_cancelable && selected_cards.size() == 0)
						ShowCancelOrFinishButton(1);
					else
						ShowCancelOrFinishButton(0);
				}
				break;
			}
			case MSG_SELECT_TRIBUTE: {
				if (!(hovered_location & 0xe) || !clicked_card || !clicked_card->is_selectable)
					break;
				if(clicked_card->is_selected) {
					auto it = std::find(selected_cards.begin(), selected_cards.end(), clicked_card);
					selected_cards.erase(it);
				} else {
					selected_cards.push_back(clicked_card);
				}
				if(CheckSelectTribute()) {
					if(selectsum_cards.size() == 0) {
						SetResponseSelectedCards();
						ShowCancelOrFinishButton(0);
						DuelClient::SendResponse();
					} else {
						select_ready = true;
						ShowCancelOrFinishButton(2);
					}
				} else {
					select_ready = false;
					if (select_cancelable && selected_cards.size() == 0)
						ShowCancelOrFinishButton(1);
					else
						ShowCancelOrFinishButton(0);
				}
				break;
			}
			case MSG_SELECT_UNSELECT_CARD: {
				if (!(hovered_location & 0xe) || !clicked_card || !clicked_card->is_selectable)
					break;
				if (clicked_card->is_selected) {
					clicked_card->is_selected = false;
				} else {
					clicked_card->is_selected = true;
				}
				selected_cards.push_back(clicked_card);
				if (selected_cards.size() > 0) {
					ShowCancelOrFinishButton(0);
					SetResponseSelectedCards();
					DuelClient::SendResponse();
				}
				break;
			}
			case MSG_SELECT_COUNTER: {
				if (!clicked_card || !clicked_card->is_selectable)
					break;
				clicked_card->opParam--;
				if ((clicked_card->opParam & 0xffff) == 0)
					clicked_card->is_selectable = false;
				select_counter_count--;
				if (select_counter_count == 0) {
					unsigned short int respbuf[32];
					for(size_t i = 0; i < selectable_cards.size(); ++i)
						respbuf[i] = (selectable_cards[i]->opParam >> 16) - (selectable_cards[i]->opParam & 0xffff);
					mainGame->stHintMsg->setVisible(false);
					ClearSelect();
					DuelClient::SetResponseB(respbuf, selectable_cards.size() * 2);
					DuelClient::SendResponse();
				} else {
					myswprintf(formatBuffer, dataManager.GetSysString(204), select_counter_count, dataManager.GetCounterName(select_counter_type));
					mainGame->stHintMsg->setText(formatBuffer);
				}
				break;
			}
			case MSG_SELECT_SUM: {
				if (!clicked_card || !clicked_card->is_selectable)
					break;
				if (clicked_card->is_selected) {
					auto it = std::find(selected_cards.begin(), selected_cards.end(), clicked_card);
					selected_cards.erase(it);
				} else
					selected_cards.push_back(clicked_card);
				ShowSelectSum(false);
				break;
			}
			}
			break;
		}
		case irr::EMIE_RMOUSE_LEFT_UP: {
			if(mainGame->dInfo.isReplay)
				break;
			if(event.MouseInput.isLeftPressed())
				break;
			if(mainGame->gameConf.control_mode == 1 && event.MouseInput.X > 300 * mainGame->xScale) {
				mainGame->ignore_chain = event.MouseInput.isRightPressed();
				mainGame->always_chain = false;
				mainGame->chain_when_avail = false;
				UpdateChainButtons();
			}
			mainGame->HideElement(mainGame->wSurrender);
			HideMenu();
			if(mainGame->fadingList.size())
				break;
			CancelOrFinish();
			break;
		}
		case irr::EMIE_MOUSE_MOVED: {
			if(!mainGame->dInfo.isStarted)
				break;
			bool should_show_tip = false;
			irr::s32 x = event.MouseInput.X;
			irr::s32 y = event.MouseInput.Y;
			irr::core::vector2di pos(x, y);
			wchar_t formatBuffer[2048];
			if(x < 300) {
				irr::gui::IGUIElement* root = mainGame->env->getRootGUIElement();
				irr::gui::IGUIElement* elem = root->getElementFromPoint(pos);
				if(elem == mainGame->btnCancelOrFinish) {
					should_show_tip = true;
					myswprintf(formatBuffer, dataManager.GetSysString(1700), mainGame->btnCancelOrFinish->getText());
					mainGame->stTip->setText(formatBuffer);
					irr::core::dimension2d<unsigned int> dtip = mainGame->guiFont->getDimension(formatBuffer) + irr::core::dimension2d<unsigned int>(10, 10);
					mainGame->stTip->setRelativePosition(irr::core::recti(x - 10 * mainGame->xScale - dtip.Width, y - 10 * mainGame->yScale - dtip.Height, x - 10 * mainGame->xScale, y - 10 * mainGame->yScale));
				}
				mainGame->stTip->setVisible(should_show_tip);
				break;
			}
			hovered_location = 0;
			ClientCard* mcard = 0;
			int mplayer = -1;
			if(!panel || !panel->isVisible() || !panel->getRelativePosition().isPointInside(pos)) {
				GetHoverField(x, y);
				if(hovered_location & 0xe)
					mcard = GetCard(hovered_controler, hovered_location, hovered_sequence);
				else if(hovered_location == LOCATION_GRAVE) {
					if(grave[hovered_controler].size())
						mcard = grave[hovered_controler].back();
				} else if(hovered_location == LOCATION_REMOVED) {
					if(remove[hovered_controler].size()) {
						mcard = remove[hovered_controler].back();
						if(mcard->position & POS_FACEDOWN)
							mcard = 0;
					}
				} else if(hovered_location == LOCATION_EXTRA) {
					if(extra[hovered_controler].size()) {
						mcard = extra[hovered_controler].back();
						if(mcard->position & POS_FACEDOWN)
							mcard = 0;
					}
				} else if(hovered_location == LOCATION_DECK) {
					if(deck[hovered_controler].size())
						mcard = deck[hovered_controler].back();
				} else {
					if(mainGame->Resize(327, 8, 630, 72).isPointInside(pos))
						mplayer = 0;
					else if(mainGame->Resize(689, 8, 991, 72).isPointInside(pos))
						mplayer = 1;
				}
			}
			if(hovered_location == LOCATION_HAND && (mainGame->dInfo.is_shuffling || mainGame->dInfo.curMsg == MSG_SHUFFLE_HAND))
				mcard = 0;
			if(mcard == 0 && mplayer < 0)
				should_show_tip = false;
			else if(mcard == hovered_card && mplayer == hovered_player) {
				if(mainGame->stTip->isVisible()) {
					should_show_tip = true;
					irr::core::recti tpos = mainGame->stTip->getRelativePosition();
					mainGame->stTip->setRelativePosition(irr::core::vector2di(x - tpos.getWidth() - 10  * mainGame->xScale, mcard ? y - tpos.getHeight() - (10 * mainGame->yScale) : y + 10 * mainGame->xScale));
				}
			}
			if(mcard != hovered_card) {
				if(hovered_card) {
					if(hovered_card->location == LOCATION_HAND && !mainGame->dInfo.is_shuffling && mainGame->dInfo.curMsg != MSG_SHUFFLE_HAND) {
						hovered_card->is_hovered = false;
						MoveCard(hovered_card, 5);
						if(hovered_controler == 0)
							mainGame->hideChat = false;
					}
					SetShowMark(hovered_card, false);
				}
				if(mcard) {
					if(mcard != menu_card)
						HideMenu();
					if(hovered_location == LOCATION_HAND) {
						mcard->is_hovered = true;
						MoveCard(mcard, 5);
						if(hovered_controler == 0)
							mainGame->hideChat = true;
					}
					SetShowMark(mcard, true);
					if(mcard->code) {
						mainGame->ShowCardInfo(mcard->code);
						if(mcard->location & 0xe) {
							std::wstring str;
							myswprintf(formatBuffer, L"%ls", dataManager.GetName(mcard->code));
							str.append(formatBuffer);
							if(mcard->type & TYPE_MONSTER) {
								if(mcard->alias && std::wcscmp(dataManager.GetName(mcard->code), dataManager.GetName(mcard->alias))) {
									myswprintf(formatBuffer, L"\n(%ls)", dataManager.GetName(mcard->alias));
									str.append(formatBuffer);
								}
								myswprintf(formatBuffer, L"\n%ls/%ls", mcard->atkstring, mcard->defstring);
								str.append(formatBuffer);
								if(!(mcard->type & TYPE_LINK)) {
									const wchar_t* form = L"\u2605";
									if (mcard->rank) form = L"\u2606";
									myswprintf(formatBuffer, L"\n%ls%d", form, (mcard->level ? mcard->level : mcard->rank));
									str.append(formatBuffer);
								} else {
									myswprintf(formatBuffer, L"\nLINK-%d", mcard->link);
									str.append(formatBuffer);
								}
								const auto& race = dataManager.FormatRace(mcard->race);
								const auto& attribute = dataManager.FormatAttribute(mcard->attribute);
								myswprintf(formatBuffer, L" %ls/%ls", race.c_str(), attribute.c_str());
								str.append(formatBuffer);
								if(mcard->location == LOCATION_HAND && (mcard->type & TYPE_PENDULUM)) {
									myswprintf(formatBuffer, L"\n%d/%d", mcard->lscale, mcard->rscale);
									str.append(formatBuffer);
								}
							} else {
								if(mcard->alias && std::wcscmp(dataManager.GetName(mcard->code), dataManager.GetName(mcard->alias))) {
									myswprintf(formatBuffer, L"\n(%ls)", dataManager.GetName(mcard->alias));
									str.append(formatBuffer);
								}
								if(mcard->location == LOCATION_SZONE && (mcard->type & TYPE_PENDULUM)) {
									myswprintf(formatBuffer, L"\n%d/%d", mcard->lscale, mcard->rscale);
									str.append(formatBuffer);
								}
							}
							for(auto ctit = mcard->counters.begin(); ctit != mcard->counters.end(); ++ctit) {
								myswprintf(formatBuffer, L"\n[%ls]: %d", dataManager.GetCounterName(ctit->first), ctit->second);
								str.append(formatBuffer);
							}
							if(mcard->cHint && mcard->chValue && (mcard->location & LOCATION_ONFIELD)) {
								if(mcard->cHint == CHINT_TURN)
									myswprintf(formatBuffer, L"\n%ls%d", dataManager.GetSysString(211), mcard->chValue);
								else if(mcard->cHint == CHINT_CARD)
									myswprintf(formatBuffer, L"\n%ls%ls", dataManager.GetSysString(212), dataManager.GetName(mcard->chValue));
								else if(mcard->cHint == CHINT_RACE) {
									const auto& race = dataManager.FormatRace(mcard->chValue);
									myswprintf(formatBuffer, L"\n%ls%ls", dataManager.GetSysString(213), race.c_str());
								}
								else if(mcard->cHint == CHINT_ATTRIBUTE) {
									const auto& attribute = dataManager.FormatAttribute(mcard->chValue);
									myswprintf(formatBuffer, L"\n%ls%ls", dataManager.GetSysString(214), attribute.c_str());
								}
								else if(mcard->cHint == CHINT_NUMBER)
									myswprintf(formatBuffer, L"\n%ls%d", dataManager.GetSysString(215), mcard->chValue);
								str.append(formatBuffer);
							}
							for(auto iter = mcard->desc_hints.begin(); iter != mcard->desc_hints.end(); ++iter) {
								myswprintf(formatBuffer, L"\n*%ls", dataManager.GetDesc(iter->first));
								str.append(formatBuffer);
							}
							should_show_tip = true;
							irr::core::dimension2d<unsigned int> dtip = mainGame->guiFont->getDimension(str.c_str()) + irr::core::dimension2d<unsigned int>(10 * mainGame->xScale, 10 * mainGame->yScale);
							mainGame->stTip->setRelativePosition(irr::core::recti(x - 10 * mainGame->xScale - dtip.Width, y - 10 * mainGame->yScale - dtip.Height, x - 10 * mainGame->xScale, y - 10 * mainGame->yScale));
							mainGame->stTip->setText(str.c_str());
						}
					} else {
						should_show_tip = false;
						mainGame->ClearCardInfo(mcard->controler);
					}
				}
				hovered_card = mcard;
			}
			if(mplayer != hovered_player) {
				if(mplayer >= 0) {
					const wchar_t* player_name;
					if(mplayer == 0) {
						if(!mainGame->dInfo.isTag || !mainGame->dInfo.tag_player[0])
							player_name = mainGame->dInfo.hostname;
						else
							player_name = mainGame->dInfo.hostname_tag;
					} else {
						if(!mainGame->dInfo.isTag || !mainGame->dInfo.tag_player[1])
							player_name = mainGame->dInfo.clientname;
						else
							player_name = mainGame->dInfo.clientname_tag;
					}
					std::wstring str(player_name);
					const auto& mplayer_hints = mainGame->dField.player_desc_hints[mplayer];
					for(auto iter = mplayer_hints.begin(); iter != mplayer_hints.end(); ++iter) {
						myswprintf(formatBuffer, L"\n*%ls", dataManager.GetDesc(iter->first));
						str.append(formatBuffer);
					}
					if(mainGame->dInfo.turn == 1) {
						if(mplayer == 0 && mainGame->dInfo.isFirst || mplayer != 0 && !mainGame->dInfo.isFirst)
							myswprintf(formatBuffer, L"\n*%ls", dataManager.GetSysString(100));
						else
							myswprintf(formatBuffer, L"\n*%ls", dataManager.GetSysString(101));
						str.append(formatBuffer);
					}
					should_show_tip = true;
					irr::core::dimension2d<unsigned int> dtip = mainGame->guiFont->getDimension(str.c_str()) + irr::core::dimension2d<unsigned int>(10 * mainGame->xScale, 10 * mainGame->yScale);
					mainGame->stTip->setRelativePosition(irr::core::recti(x - 10 * mainGame->xScale - dtip.Width, y + 10 * mainGame->yScale, x - 10 * mainGame->xScale, y + 10 * mainGame->yScale + dtip.Height));
					mainGame->stTip->setText(str.c_str());
				}
				hovered_player = mplayer;
			}
			mainGame->stTip->setVisible(should_show_tip);
			break;
		}
		case irr::EMIE_MOUSE_WHEEL: {
			break;
		}
		case irr::EMIE_LMOUSE_PRESSED_DOWN: {
			if(!mainGame->dInfo.isStarted)
				break;
			//if(mainGame->wCardSelect->isVisible())
			    //break;
			if (mainGame->wQuery->isVisible() || mainGame->wANAttribute->isVisible()
				|| mainGame->wANCard->isVisible() || mainGame->wANNumber->isVisible()
				|| mainGame->wCardSelect->isVisible()|| mainGame->wCardDisplay->isVisible()
				||mainGame->wOptions->isVisible()){
                display_cards.clear();
                int loc_id = 0;
                switch(hovered_location) {
                    /*
                    case LOCATION_MZONE: {
                        if(!clicked_card || clicked_card->overlayed.size() == 0)
                            break;
                        loc_id = 1007;
                        for(auto it = mzone[hovered_controler].begin(); it != mzone[hovered_controler].end(); ++it) {
                            if(*it) {
                                for(auto oit = (*it)->overlayed.begin(); oit != (*it)->overlayed.end(); ++oit)
                                    display_cards.push_back(*oit);
                            }
                        }
                        break;
                    }*/
                    case LOCATION_GRAVE: {
                        if(grave[hovered_controler].size() == 0)
                            break;
						if(cant_check_grave)
							break;
                        loc_id = 1004;
                        for(auto it = grave[hovered_controler].rbegin(); it != grave[hovered_controler].rend(); ++it)
                            display_cards.push_back(*it);
                        break;
                    }
                    case LOCATION_REMOVED: {
                        if(remove[hovered_controler].size() == 0)
                            break;
                        loc_id = 1005;
                        for(auto it = remove[hovered_controler].rbegin(); it != remove[hovered_controler].rend(); ++it)
                            display_cards.push_back(*it);
                        break;
                    }
                    case LOCATION_EXTRA: {
                        if(extra[hovered_controler].size() == 0)
                            break;
                        loc_id = 1006;
                        for(auto it = extra[hovered_controler].rbegin(); it != extra[hovered_controler].rend(); ++it)
                            display_cards.push_back(*it);
                        break;
                    }
                }
                if(display_cards.size()) {
                    wchar_t formatBuffer[2048];
                    myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(loc_id), display_cards.size());
                    mainGame->stCardDisplay->setText(formatBuffer);
                    ShowLocationCard();
                }
			}
			break;
		}
		case irr::EMIE_RMOUSE_PRESSED_DOWN: {
			if(!mainGame->dInfo.isStarted)
				break;
			if(mainGame->gameConf.control_mode == 1 && event.MouseInput.X > 300 * mainGame->xScale) {
				mainGame->ignore_chain = event.MouseInput.isRightPressed();
				mainGame->always_chain = false;
				mainGame->chain_when_avail = false;
				UpdateChainButtons();
			}
			break;
		}
		default:
			break;
		}
		break;
	}
	case irr::EET_KEY_INPUT_EVENT: {
		switch(event.KeyInput.Key) {
		case irr::KEY_KEY_A: {
			if(mainGame->gameConf.control_mode == 0 && !mainGame->HasFocus(irr::gui::EGUIET_EDIT_BOX)) {
				mainGame->always_chain = event.KeyInput.PressedDown;
				mainGame->ignore_chain = false;
				mainGame->chain_when_avail = false;
				UpdateChainButtons();
			}
			break;
		}
		case irr::KEY_KEY_S: {
			if(mainGame->gameConf.control_mode == 0 && !mainGame->HasFocus(irr::gui::EGUIET_EDIT_BOX)) {
				mainGame->ignore_chain = event.KeyInput.PressedDown;
				mainGame->always_chain = false;
				mainGame->chain_when_avail = false;
				UpdateChainButtons();
			}
			break;
		}
		case irr::KEY_KEY_D: {
			if(mainGame->gameConf.control_mode == 0 && !mainGame->HasFocus(irr::gui::EGUIET_EDIT_BOX)) {
				mainGame->chain_when_avail = event.KeyInput.PressedDown;
				mainGame->always_chain = false;
				mainGame->ignore_chain = false;
				UpdateChainButtons();
			}
			break;
		}
		case irr::KEY_F1:
		case irr::KEY_F2:
		case irr::KEY_F3:
		case irr::KEY_F4:
		case irr::KEY_F5:
		case irr::KEY_F6:
		case irr::KEY_F7:
		case irr::KEY_F8: {
			if(!event.KeyInput.PressedDown && !mainGame->dInfo.isReplay && mainGame->dInfo.player_type != 7 && mainGame->dInfo.isStarted
					&& !mainGame->wCardDisplay->isVisible() && !mainGame->HasFocus(irr::gui::EGUIET_EDIT_BOX)) {
				int loc_id = 0;
				display_cards.clear();
				switch(event.KeyInput.Key) {
					case irr::KEY_F1:
						if(cant_check_grave)
							break;
						loc_id = 1004;
						for(auto it = grave[0].rbegin(); it != grave[0].rend(); ++it)
							display_cards.push_back(*it);
						break;
					case irr::KEY_F2:
						loc_id = 1005;
						for(auto it = remove[0].rbegin(); it != remove[0].rend(); ++it)
							display_cards.push_back(*it);
						break;
					case irr::KEY_F3:
						loc_id = 1006;
						for(auto it = extra[0].rbegin(); it != extra[0].rend(); ++it)
							display_cards.push_back(*it);
						break;
					case irr::KEY_F4:
						loc_id = 1007;
						for(auto it = mzone[0].begin(); it != mzone[0].end(); ++it) {
							if(*it) {
								for(auto oit = (*it)->overlayed.begin(); oit != (*it)->overlayed.end(); ++oit)
									display_cards.push_back(*oit);
							}
						}
						break;
					case irr::KEY_F5:
						if(cant_check_grave)
							break;
						loc_id = 1004;
						for(auto it = grave[1].rbegin(); it != grave[1].rend(); ++it)
							display_cards.push_back(*it);
						break;
					case irr::KEY_F6:
						loc_id = 1005;
						for(auto it = remove[1].rbegin(); it != remove[1].rend(); ++it)
							display_cards.push_back(*it);
						break;
					case irr::KEY_F7:
						loc_id = 1006;
						for(auto it = extra[1].rbegin(); it != extra[1].rend(); ++it)
							display_cards.push_back(*it);
						break;
					case irr::KEY_F8:
						loc_id = 1007;
						for(auto it = mzone[1].begin(); it != mzone[1].end(); ++it) {
							if(*it) {
								for(auto oit = (*it)->overlayed.begin(); oit != (*it)->overlayed.end(); ++oit)
									display_cards.push_back(*oit);
							}
						}
						break;
				}
				if(display_cards.size()) {
					wchar_t formatBuffer[2048];
					myswprintf(formatBuffer, L"%ls(%d)", dataManager.GetSysString(loc_id), display_cards.size());
					mainGame->stCardDisplay->setText(formatBuffer);
					ShowLocationCard();
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
bool ClientField::OnCommonEvent(const irr::SEvent& event) {
	switch(event.EventType) {
	case irr::EET_GUI_EVENT: {
		irr::s32 id = event.GUIEvent.Caller->getID();
		if(mainGame->wSysMessage->isVisible() && id != BUTTON_SYS_MSG_OK) {
			mainGame->wSysMessage->getParent()->bringToFront(mainGame->wSysMessage);
			//mainGame->env->setFocus(mainGame->wSysMessage);
			return true;
			break;
		}
		switch(event.GUIEvent.EventType) {
//dont merge these cases
		case irr::gui::EGET_BUTTON_CLICKED: {
			switch(id) {
			case BUTTON_CLEAR_LOG: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->lstLog->clear();
				mainGame->logParam.clear();
				return true;
				break;
			}
			case BUTTON_SYS_MSG_OK: {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
				mainGame->HideElement(mainGame->wSysMessage);
				return true;
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_CHECKBOX_CHANGED: {
			switch(id) {
			case CHECKBOX_AUTO_SEARCH: {
				mainGame->gameConf.auto_search_limit = mainGame->chkAutoSearch->isChecked() ? 0 : -1;
				if(mainGame->is_building && !mainGame->is_siding)
					mainGame->deckBuilder.InstantSearch();
				return true;
				break;
			}
			case CHECKBOX_MULTI_KEYWORDS: {
				mainGame->gameConf.search_multiple_keywords = mainGame->chkMultiKeywords->isChecked() ? 1 : 0;
				if(mainGame->is_building && !mainGame->is_siding)
					mainGame->deckBuilder.InstantSearch();
				return true;
				break;
			}
			case CHECKBOX_DISABLE_CHAT: {
				if (mainGame->gameConf.chkIgnore1) {
					mainGame->gameConf.chkIgnore1 = false;
					mainGame->imgChat->setImage(imageManager.tTalk);
				} else {
					mainGame->gameConf.chkIgnore1 = true;
					mainGame->imgChat->setImage(imageManager.tShut);
				}
				mainGame->chkIgnore1->setChecked(mainGame->gameConf.chkIgnore1);
				bool show = !(mainGame->is_building && !mainGame->is_siding) && !mainGame->chkIgnore1->isChecked();
				mainGame->wChat->setVisible(show);
				if(!show)
					mainGame->ClearChatMsg();
				return true;
				break;
			}
			case CHECKBOX_QUICK_ANIMATION: {
				mainGame->gameConf.quick_animation = mainGame->chkQuickAnimation->isChecked() ? 1 : 0;
				return true;
				break;
			}
			case CHECKBOX_DRAW_SINGLE_CHAIN: {
				mainGame->gameConf.draw_single_chain = mainGame->chkDrawSingleChain->isChecked() ? 1 : 0;
				return true;
				break;
			}
			case CHECKBOX_HIDE_PLAYER_NAME: {
				mainGame->gameConf.hide_player_name = mainGame->chkHidePlayerName->isChecked() ? 1 : 0;
				if(mainGame->gameConf.hide_player_name)
					mainGame->ClearChatMsg();
				return true;
				break;
			}
            /*
			case CHECKBOX_PREFER_EXPANSION: {
				mainGame->gameConf.prefer_expansion_script = mainGame->chkPreferExpansionScript->isChecked() ? 1 : 0;
				return true;
				break;
			}*/
            case CHECKBOX_ENABLE_GENESYS_MODE: {
				mainGame->gameConf.enable_genesys_mode = mainGame->chkEnableGenesysMode->isChecked() ? 1 : 0;
                if (mainGame->gameConf.enable_genesys_mode == 1) {// 判断是否启用genesys模式
                    // 常规禁卡表开关和下拉菜单隐藏
                    mainGame->chkLFlist->setVisible(false);
                    mainGame->cbLFlist->setVisible(false);
                    // 局域网建主-常规禁卡表下拉菜单隐藏
                    mainGame->cbHostLFlist->setVisible(false);
                    // genesys禁卡表开关和下拉菜单显示
                    mainGame->chkGenesysLFlist->setVisible(true);
                    mainGame->cbGenesysLFlist->setVisible(true);
                    mainGame->cbHostGenesysLFlist->setVisible(true);
                    // 立刻启用被选择的禁卡表
                    mainGame->deckBuilder.filterList = &deckManager._genesys_lfList[mainGame->cbGenesysLFlist->getSelected()];

                } else {// 如果是禁限常规模式
                    // 常规禁卡表开关和下拉菜单显示
                    mainGame->chkLFlist->setVisible(true);
                    mainGame->cbLFlist->setVisible(true);
                    // 局域网建主-常规禁卡表下拉菜单显示
                    mainGame->cbHostLFlist->setVisible(true);
                    // genesys禁卡表开关和下拉菜单隐藏
                    mainGame->chkGenesysLFlist->setVisible(false);
                    mainGame->cbGenesysLFlist->setVisible(false);
                    // 局域网建主-genesys禁卡表下拉菜单隐藏
                    mainGame->cbHostGenesysLFlist->setVisible(false);
                    // 立刻启用被选择的禁卡表
                    mainGame->deckBuilder.filterList = &deckManager._lfList[mainGame->cbLFlist->getSelected()];
                }

                return true;
				break;
			}
			case CHECKBOX_LFLIST: {
				mainGame->gameConf.use_lflist = mainGame->chkLFlist->isChecked() ? 1 : 0;
				mainGame->cbLFlist->setEnabled(mainGame->gameConf.use_lflist);
                // 获取保存的最后禁卡表名称
                wchar_t lastLimitName[256];
                BufferIO::CopyWideString(mainGame->gameConf.last_limit_list_name, lastLimitName);
                // 在禁卡表列表中查找匹配的名称
                int selectedIndex = -1;
                for (unsigned int i = 0; i < mainGame->cbLFlist->getItemCount(); i++) {
                    if (!wcscmp(lastLimitName, mainGame->cbLFlist->getItem(i))) {
                        mainGame->gameConf.default_lflist = i;
                        break;
                    }
                }
                // 重设2个禁卡表选择combobox的选中项,如果use_lflist的值代表未启用，则设置选项为N/A
                mainGame->cbLFlist->setSelected(mainGame->gameConf.use_lflist ? mainGame->gameConf.default_lflist : mainGame->cbLFlist->getItemCount() - 1);
                mainGame->cbHostLFlist->setSelected(mainGame->gameConf.use_lflist ? mainGame->gameConf.default_lflist : mainGame->cbHostLFlist->getItemCount() - 1);
                // 立刻启用被选择的禁卡表
				mainGame->deckBuilder.filterList = &deckManager._lfList[mainGame->cbLFlist->getSelected()];
				return true;
				break;
			}
            case CHECKBOX_GENESYS_LFLIST: {
                mainGame->gameConf.use_genesys_lflist = mainGame->chkGenesysLFlist->isChecked() ? 1 : 0;
                mainGame->cbGenesysLFlist->setEnabled(mainGame->gameConf.use_genesys_lflist);
                // 获取保存的最后禁卡表名称
                wchar_t lastLimitName[256];
                BufferIO::CopyWideString(mainGame->gameConf.last_genesys_limit_list_name, lastLimitName);
                // 在禁卡表列表中查找匹配的名称
                int selectedIndex = -1;
                for (unsigned int i = 0; i < mainGame->cbGenesysLFlist->getItemCount(); i++) {
                    if (!wcscmp(lastLimitName, mainGame->cbGenesysLFlist->getItem(i))) {
                        mainGame->gameConf.default_genesys_lflist = i;
                        break;
                    }
                }
                // 重设2个禁卡表选择combobox的选中项,如果use_lflist的值代表未启用，则设置选项为N/A
                mainGame->cbGenesysLFlist->setSelected(mainGame->gameConf.use_genesys_lflist ? mainGame->gameConf.default_genesys_lflist : mainGame->cbGenesysLFlist->getItemCount() - 1);
                mainGame->cbHostGenesysLFlist->setSelected(mainGame->gameConf.use_genesys_lflist ? mainGame->gameConf.default_genesys_lflist : mainGame->cbHostGenesysLFlist->getItemCount() - 1);
                // 立刻启用被选择的禁卡表
                mainGame->deckBuilder.filterList = &deckManager._genesys_lfList[mainGame->cbGenesysLFlist->getSelected()];
                return true;
                break;
            }
 			case CHECKBOX_DRAW_FIELD_SPELL: {
 			    mainGame->gameConf.draw_field_spell = mainGame->chkDrawFieldSpell->isChecked() ? 1 : 0;
				return true;
				break;
			}
			case CHECKBOX_ENABLE_SOUND: {
				mainGame->soundManager->EnableSounds(mainGame->chkEnableSound->isChecked());
				return true;
				break;
			}
			case CHECKBOX_ENABLE_MUSIC: {
				mainGame->soundManager->EnableMusic(mainGame->chkEnableMusic->isChecked());
				if (mainGame->gameConf.enable_music) {
				    mainGame->imgVol->setImage(imageManager.tPlay);
                } else {
                    mainGame->imgVol->setImage(imageManager.tMute);
                }
				return true;
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_COMBO_BOX_CHANGED: {
			switch(id) {
			case COMBOBOX_LFLIST: {
				mainGame->gameConf.default_lflist = mainGame->cbLFlist->getSelected();
				mainGame->cbHostLFlist->setSelected(mainGame->gameConf.default_lflist);
                // 保存最后使用的禁卡表名称
                BufferIO::CopyWideString(mainGame->cbLFlist->getItem(mainGame->gameConf.default_lflist), mainGame->gameConf.last_limit_list_name);
				mainGame->deckBuilder.filterList = &deckManager._lfList[mainGame->gameConf.default_lflist];
				return true;
				break;
			}
            case COMBOBOX_GENESYS_LFLIST: {
                mainGame->gameConf.default_genesys_lflist = mainGame->cbGenesysLFlist->getSelected();
                mainGame->cbHostLFlist->setSelected(mainGame->gameConf.default_genesys_lflist);
                // 保存最后使用的禁卡表名称
                BufferIO::CopyWideString(mainGame->cbGenesysLFlist->getItem(mainGame->gameConf.default_genesys_lflist), mainGame->gameConf.last_genesys_limit_list_name);
                mainGame->deckBuilder.filterList = &deckManager._genesys_lfList[mainGame->gameConf.default_genesys_lflist];
                return true;
                break;
            }
			}
			break;
		}
		case irr::gui::EGET_LISTBOX_CHANGED: {
			switch(id) {
			case LISTBOX_LOG: {
				int sel = mainGame->lstLog->getSelected();
				if(sel != -1 && (int)mainGame->logParam.size() >= sel && mainGame->logParam[sel]) {
					mainGame->ShowCardInfo(mainGame->logParam[sel]);
				}
				return true;
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_LISTBOX_SELECTED_AGAIN: {
			switch(id) {
			case LISTBOX_LOG: {
				int sel = mainGame->lstLog->getSelected();
				if(sel != -1 && (int)mainGame->logParam.size() >= sel && mainGame->logParam[sel] && is_selectable) {
					//mainGame->wInfos->setActiveTab(0);
				}
				return true;
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_SCROLL_BAR_CHANGED: {
			switch(id) {
			case SCROLL_CARDTEXT: {
				if(!mainGame->scrCardText->isVisible()) {
					return true;
					break;
				}
				irr::u32 pos = mainGame->scrCardText->getPos();
				mainGame->SetStaticText(mainGame->stText, mainGame->stText->getRelativePosition().getWidth(), mainGame->textFont, mainGame->showingtext, pos);
				return true;
				break;
			}
			case SCROLL_VOLUME: {
				mainGame->gameConf.sound_volume = (double)mainGame->scrSoundVolume->getPos() / 100;
				mainGame->gameConf.music_volume = (double)mainGame->scrMusicVolume->getPos() / 100;
				mainGame->soundManager->SetSoundVolume(mainGame->gameConf.sound_volume);
				mainGame->soundManager->SetMusicVolume(mainGame->gameConf.music_volume);
				return true;
				break;
			}
			case SCROLL_SETTINGS: {
                irr::core::rect<irr::s32> pos = mainGame->wSettings->getRelativePosition();
				mainGame->wSettings->setRelativePosition(irr::core::recti(350 * mainGame->xScale, mainGame->scrTabSystem->getPos() * -1, pos.LowerRightCorner.X, pos.LowerRightCorner.Y));
				return true;
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_EDITBOX_ENTER: {
			switch(id) {
			case EDITBOX_CHAT: {
				if(mainGame->dInfo.isReplay)
					break;
				const wchar_t* input = mainGame->ebChatInput->getText();
				if(input[0]) {
					uint16_t msgbuf[LEN_CHAT_MSG];
					int len = BufferIO::CopyCharArray(input, msgbuf);
					DuelClient::SendBufferToServer(CTOS_CHAT, msgbuf, (len + 1) * sizeof(uint16_t));
					mainGame->ebChatInput->setText(L"");
					return true;
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
	case irr::EET_KEY_INPUT_EVENT: {
		switch(event.KeyInput.Key) {
		case irr::KEY_KEY_R: {
			if(mainGame->gameConf.control_mode == 0
				&& !event.KeyInput.PressedDown && !mainGame->HasFocus(irr::gui::EGUIET_EDIT_BOX)) {
				mainGame->textFont->setTransparency(true);
				mainGame->guiFont->setTransparency(true);
			}
			return true;
			break;
		}
		case irr::KEY_F9: {
			if(mainGame->gameConf.control_mode == 1
				&& !event.KeyInput.PressedDown && !mainGame->HasFocus(irr::gui::EGUIET_EDIT_BOX)) {
				mainGame->textFont->setTransparency(true);
				mainGame->guiFont->setTransparency(true);
			}
			return true;
			break;
		}
		case irr::KEY_ESCAPE: {
			if(!mainGame->HasFocus(irr::gui::EGUIET_EDIT_BOX))
				mainGame->device->minimizeWindow();
			return true;
			break;
		}
		default: break;
		}
		break;
	}
	case irr::EET_MOUSE_INPUT_EVENT: {
        irr::gui::IGUIElement* root = mainGame->env->getRootGUIElement();
        irr::core::vector2di mousepos = irr::core::vector2di(event.MouseInput.X, event.MouseInput.Y);
		irr::gui::IGUIElement* eventElement = root->getElementFromPoint(mousepos);
        irr::u32 static presstime, leftuptime;
	    switch(event.MouseInput.Event) {
	        case irr::EMIE_LMOUSE_PRESSED_DOWN: {
                presstime = mainGame->device->getTimer()->getRealTime();
	            //vertical scrollbar
	            if(eventElement == mainGame->stText) {
	                if(!mainGame->scrCardText->isVisible()) {
	                    break;
	                }
	                is_dragging_cardtext = true;
	                dragging_tab_start_pos = mainGame->scrCardText->getPos();
	                dragging_tab_start_y = event.MouseInput.Y;
	                return true;
	            }
                if(eventElement == mainGame->lstLog) {
                    if(!mainGame->lstLog->getVerticalScrollBar()->isVisible()) {
                        break;
                    }
                    is_dragging_lstLog = true;
                    dragging_tab_start_pos = mainGame->lstLog->getVerticalScrollBar()->getPos();
                    dragging_tab_start_y = event.MouseInput.Y;
                    return true;
                }
                if(eventElement == mainGame->lstReplayList) {
                    if(!mainGame->lstReplayList->getVerticalScrollBar()->isVisible()) {
                        break;
                    }
                    is_dragging_lstReplayList = true;
                    dragging_tab_start_pos = mainGame->lstReplayList->getVerticalScrollBar()->getPos();
                    dragging_tab_start_y = event.MouseInput.Y;
                    return true;
                }
                if(eventElement == mainGame->lstSinglePlayList) {
                    if(!mainGame->lstSinglePlayList->getVerticalScrollBar()->isVisible()) {
                        break;
                    }
                    is_dragging_lstSinglePlayList = true;
                    dragging_tab_start_pos = mainGame->lstSinglePlayList->getVerticalScrollBar()->getPos();
                    dragging_tab_start_y = event.MouseInput.Y;
                    return true;
                }
                if(eventElement == mainGame->lstBotList) {
                    if(!mainGame->lstBotList->getVerticalScrollBar()->isVisible()) {
                        break;
                    }
                    is_dragging_lstBotList = true;
                    dragging_tab_start_pos = mainGame->lstBotList->getVerticalScrollBar()->getPos();
                    dragging_tab_start_y = event.MouseInput.Y;
                    return true;
                }
                if(eventElement == mainGame->lstDecks) {
                    if(!mainGame->lstDecks->getVerticalScrollBar()->isVisible()) {
                        break;
                    }
                    is_dragging_lstDecks = true;
                    dragging_tab_start_pos = mainGame->lstDecks->getVerticalScrollBar()->getPos();
                    dragging_tab_start_y = event.MouseInput.Y;
                    return true;
                }
				if(eventElement == mainGame->lstANCard) {
					if(!mainGame->lstANCard->getVerticalScrollBar()->isVisible()) {
						break;
					}
					is_dragging_lstANCard = true;
					dragging_tab_start_pos = mainGame->lstANCard->getVerticalScrollBar()->getPos();
					dragging_tab_start_y = event.MouseInput.Y;
					return true;
				}
	            break;
	        }
	        case irr::EMIE_LMOUSE_LEFT_UP: {
                if (eventElement == mainGame->stText ||
                    eventElement == mainGame->wHostPrepare ||
                    eventElement == mainGame->imgCard ||
                    eventElement == mainGame->wReplay ||
                    eventElement == mainGame->wSinglePlay ||
                    eventElement == mainGame->wLanWindow) {
                    if (mainGame->wEmoticon->isVisible()) mainGame->HideElement(mainGame->wEmoticon);
                    mainGame->textFont->setTransparency(true);
					mainGame->guiFont->setTransparency(true);
                    mainGame->ClearChatMsg();

                    break;
                }//touch the target place to refresh textfonts
                leftuptime = mainGame->device->getTimer()->getRealTime();
                if(leftuptime - presstime > 0 && leftuptime - presstime < 500) {
                    is_selectable = true;
                } else {
                    is_selectable = false;
                }
	            is_dragging_cardtext = false;
                is_dragging_lstLog = false;
                is_dragging_lstReplayList = false;
                is_dragging_lstSinglePlayList = false;
                is_dragging_lstBotList = false;
                is_dragging_lstDecks = false;
                is_dragging_lstANCard = false;
	            break;
	        }
	        case irr::EMIE_MOUSE_MOVED: {
	            //vertical scrollbar
	            if(is_dragging_cardtext) {
	                if(!mainGame->scrCardText->isVisible()) {
	                    is_dragging_cardtext = false;
	                    break;
	                }
	                int step = mainGame->guiFont->getDimension(L"A").Height + mainGame->guiFont->getKerningHeight();
	                int pos = dragging_tab_start_pos + (dragging_tab_start_y - event.MouseInput.Y) / step;
	                int max = mainGame->scrCardText->getMax();
	                if(pos < 0) pos = 0;
	                if(pos > max) pos = max;
	                mainGame->scrCardText->setPos(pos);
	                mainGame->SetStaticText(mainGame->stText, mainGame->stText->getRelativePosition().getWidth(), mainGame->textFont, mainGame->showingtext, pos);
	            }
                if(is_dragging_lstLog) {
                    if(!mainGame->lstLog->getVerticalScrollBar()->isVisible()) {
                        is_dragging_lstLog = false;
                        break;
                    }
                    irr::core::rect<irr::s32> lstLogpos = mainGame->lstLog->getRelativePosition();
                    int pos = dragging_tab_start_pos + ((dragging_tab_start_y - event.MouseInput.Y));
                    int max = mainGame->lstLog->getVerticalScrollBar()->getMax();
                    if(pos < 0) pos = 0;
                    if(pos > max) pos = max;
                    mainGame->lstLog->getVerticalScrollBar()->setPos(pos);
                    mainGame->lstLog->getItemAt(lstLogpos.UpperLeftCorner.X, mainGame->lstLog->getVerticalScrollBar()->getPos());
                }
                if(is_dragging_lstReplayList) {
                    if(!mainGame->lstReplayList->getVerticalScrollBar()->isVisible()) {
                        is_dragging_lstReplayList = false;
                        break;
                    }
                    irr::core::rect<irr::s32> lstReplayListpos = mainGame->lstReplayList->getRelativePosition();
                    int pos = dragging_tab_start_pos + ((dragging_tab_start_y - event.MouseInput.Y));
                    int max = mainGame->lstReplayList->getVerticalScrollBar()->getMax();
                    if(pos < 0) pos = 0;
                    if(pos > max) pos = max;
                    mainGame->lstReplayList->getVerticalScrollBar()->setPos(pos);
                    mainGame->lstReplayList->getItemAt(lstReplayListpos.UpperLeftCorner.X, mainGame->lstReplayList->getVerticalScrollBar()->getPos());
                }
                if(is_dragging_lstSinglePlayList) {
                    if(!mainGame->lstSinglePlayList->getVerticalScrollBar()->isVisible()) {
                        is_dragging_lstSinglePlayList = false;
                        break;
                    }
                    irr::core::rect<irr::s32> lstSinglePlayListpos = mainGame->lstSinglePlayList->getRelativePosition();
                    int pos = dragging_tab_start_pos + ((dragging_tab_start_y - event.MouseInput.Y));
                    int max = mainGame->lstSinglePlayList->getVerticalScrollBar()->getMax();
                    if(pos < 0) pos = 0;
                    if(pos > max) pos = max;
                    mainGame->lstSinglePlayList->getVerticalScrollBar()->setPos(pos);
                    mainGame->lstSinglePlayList->getItemAt(lstSinglePlayListpos.UpperLeftCorner.X, mainGame->lstSinglePlayList->getVerticalScrollBar()->getPos());
                }
                if(is_dragging_lstBotList) {
                    if(!mainGame->lstBotList->getVerticalScrollBar()->isVisible()) {
                        is_dragging_lstBotList = false;
                        break;
                    }
                    irr::core::rect<irr::s32> lstBotListpos = mainGame->lstBotList->getRelativePosition();
                    int pos = dragging_tab_start_pos + ((dragging_tab_start_y - event.MouseInput.Y));
                    int max = mainGame->lstBotList->getVerticalScrollBar()->getMax();
                    if(pos < 0) pos = 0;
                    if(pos > max) pos = max;
                    mainGame->lstBotList->getVerticalScrollBar()->setPos(pos);
                    mainGame->lstBotList->getItemAt(lstBotListpos.UpperLeftCorner.X, mainGame->lstBotList->getVerticalScrollBar()->getPos());
                }
                if(is_dragging_lstDecks) {
                    if(!mainGame->lstDecks->getVerticalScrollBar()->isVisible()) {
                        is_dragging_lstDecks = false;
                        break;
                    }
                    irr::core::rect<irr::s32> lstDeckspos = mainGame->lstDecks->getRelativePosition();
                    int pos = dragging_tab_start_pos + ((dragging_tab_start_y - event.MouseInput.Y));
                    int max = mainGame->lstDecks->getVerticalScrollBar()->getMax();
                    if(pos < 0) pos = 0;
                    if(pos > max) pos = max;
                    mainGame->lstDecks->getVerticalScrollBar()->setPos(pos);
                    mainGame->lstDecks->getItemAt(lstDeckspos.UpperLeftCorner.X, mainGame->lstDecks->getVerticalScrollBar()->getPos());
                }
                if(is_dragging_lstANCard) {
                    if(!mainGame->lstANCard->getVerticalScrollBar()->isVisible()) {
                        is_dragging_lstANCard = false;
                        break;
                    }
                    irr::core::rect<irr::s32> lstANCardpos = mainGame->lstANCard->getRelativePosition();
                    int pos = dragging_tab_start_pos + ((dragging_tab_start_y - event.MouseInput.Y));
                    int max = mainGame->lstANCard->getVerticalScrollBar()->getMax();
                    if(pos < 0) pos = 0;
                    if(pos > max) pos = max;
                    mainGame->lstANCard->getVerticalScrollBar()->setPos(pos);
                    mainGame->lstANCard->getItemAt(lstANCardpos.UpperLeftCorner.X, mainGame->lstANCard->getVerticalScrollBar()->getPos());
                }
	        }
	        default: break;
	    }
	    break;
	}
	default: break;
	}
	return false;
}
void ClientField::GetHoverField(int x, int y) {
	irr::core::recti sfRect(430 * mainGame->xScale, 504 * mainGame->yScale, 875 * mainGame->xScale, 600 * mainGame->yScale);
	irr::core::recti ofRect(531 * mainGame->xScale, 135 * mainGame->yScale, 800 * mainGame->xScale, 191 * mainGame->yScale);
	irr::core::vector2di pos(x, y);
	int rule = (mainGame->dInfo.duel_rule >= 4) ? 1 : 0;
	if(sfRect.isPointInside(pos)) {
		int hc = hand[0].size();
		int cardSize = 66;
		int cardSpace = 10;
		if(hc == 0)
			hovered_location = 0;
		else if(hc < 7) {
			int left = sfRect.UpperLeftCorner.X + (cardSize + cardSpace) * (6 - hc) / 2 * mainGame->xScale;
			if(x < left)
				hovered_location = 0;
			else {
				int seq = (x - left) / ((cardSize + cardSpace) * mainGame->xScale);
				if(seq >= hc) seq = hc - 1;
				if(x - left - (cardSize + cardSpace) * seq  * mainGame->xScale < cardSize * mainGame->xScale) {
					hovered_controler = 0;
					hovered_location = LOCATION_HAND;
					hovered_sequence = seq;
				} else hovered_location = 0;
			}
		} else {
			hovered_controler = 0;
			hovered_location = LOCATION_HAND;
			if(x >= sfRect.UpperLeftCorner.X + (cardSize + cardSpace) * 5 * mainGame->xScale)
				hovered_sequence = hc - 1;
			else
				hovered_sequence = (x - sfRect.UpperLeftCorner.X) * (hc - 1) / ((cardSize + cardSpace) * 5 * mainGame->xScale);
		}
	} else if(ofRect.isPointInside(pos)) {
		int hc = hand[1].size();
		int cardSize = 39;
		int cardSpace = 7;
		if(hc == 0)
			hovered_location = 0;
		else if(hc < 7) {
			int left = ofRect.UpperLeftCorner.X + (cardSize + cardSpace) * (6 - hc) / 2 * mainGame->xScale;
			if(x < left)
				hovered_location = 0;
			else {
				int seq = (x - left) / ((cardSize + cardSpace) * mainGame->xScale);
				if(seq >= hc) seq = hc - 1;
				if(x - left - (cardSize + cardSpace) * mainGame->xScale * seq < cardSize * mainGame->xScale) {
					hovered_controler = 1;
					hovered_location = LOCATION_HAND;
					hovered_sequence = hc - 1 - seq;
				} else hovered_location = 0;
			}
		} else {
			hovered_controler = 1;
			hovered_location = LOCATION_HAND;
			if(x >= ofRect.UpperLeftCorner.X + (cardSize + cardSpace) * 5 * mainGame->xScale)
				hovered_sequence = 0;
			else
				hovered_sequence = hc - 1 - (x - ofRect.UpperLeftCorner.X) * (hc - 1) / (int)((cardSize + cardSpace) * 5 * mainGame->xScale);
		}
	} else {
		double screenx = x / (GAME_WIDTH * mainGame->xScale) * 1.35  - 0.90;
		double screeny = y / (GAME_HEIGHT * mainGame->yScale) * 0.84 - 0.42;
		double angle = 0.798056 - atan(screeny);	//0.798056 = arctan(8.0/7.8)
		double vlen = sqrt(1.0 + screeny * screeny);
		double boardx = 4.2 + 7.8 * screenx / vlen / cos(angle);
		double boardy = 8.0 - 7.8 * tan(angle);
		hovered_location = 0;
		if(boardx >= matManager.vFieldExtra[0][0].Pos.X && boardx <= matManager.vFieldExtra[0][1].Pos.X) {
			if(boardy >= matManager.vFieldExtra[0][0].Pos.Y && boardy <= matManager.vFieldExtra[0][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_EXTRA;
			} else if(boardy >= matManager.vFieldSzone[0][5][rule][0].Pos.Y && boardy <= matManager.vFieldSzone[0][5][rule][2].Pos.Y) {//field
				hovered_controler = 0;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 5;
			} else if(boardy >= matManager.vFieldSzone[0][6][rule][0].Pos.Y && boardy <= matManager.vFieldSzone[0][6][rule][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 6;
			} else if(rule == 1 && boardy >= matManager.vFieldRemove[1][rule][2].Pos.Y && boardy <= matManager.vFieldRemove[1][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_REMOVED;
			} else if(rule == 0 && boardy >= matManager.vFieldSzone[1][7][rule][2].Pos.Y && boardy <= matManager.vFieldSzone[1][7][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 7;
			} else if(boardy >= matManager.vFieldGrave[1][rule][2].Pos.Y && boardy <= matManager.vFieldGrave[1][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_GRAVE;
			} else if(boardy >= matManager.vFieldDeck[1][2].Pos.Y && boardy <= matManager.vFieldDeck[1][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_DECK;
			}
		} else if (boardx >= matManager.vFieldContiAct[0].X && boardx <= matManager.vFieldContiAct[1].X
				&& boardy >= matManager.vFieldContiAct[0].Y && boardy <= matManager.vFieldContiAct[2].Y) {
			hovered_controler = 0;
			hovered_location = POSITION_HINT;
		} else if(rule == 0 && boardx >= matManager.vFieldRemove[1][rule][1].Pos.X && boardx <= matManager.vFieldRemove[1][rule][0].Pos.X) {
			if(boardy >= matManager.vFieldRemove[1][rule][2].Pos.Y && boardy <= matManager.vFieldRemove[1][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_REMOVED;
			}
		} else if(rule == 1 && boardx >= matManager.vFieldSzone[1][7][rule][1].Pos.X && boardx <= matManager.vFieldSzone[1][7][rule][2].Pos.X) {
			// deprecated szone[7]
			if(boardy >= matManager.vFieldSzone[1][7][rule][2].Pos.Y && boardy <= matManager.vFieldSzone[1][7][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 7;
			}
		} else if(boardx >= matManager.vFieldDeck[0][0].Pos.X && boardx <= matManager.vFieldDeck[0][1].Pos.X) {
			if(boardy >= matManager.vFieldDeck[0][0].Pos.Y && boardy <= matManager.vFieldDeck[0][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_DECK;
			} else if(boardy >= matManager.vFieldGrave[0][rule][0].Pos.Y && boardy <= matManager.vFieldGrave[0][rule][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_GRAVE;
			} else if(boardy >= matManager.vFieldSzone[1][6][rule][2].Pos.Y && boardy <= matManager.vFieldSzone[1][6][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 6;
			} else if(rule == 0 && boardy >= matManager.vFieldSzone[0][7][rule][0].Pos.Y && boardy <= matManager.vFieldSzone[0][7][rule][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 7;
			} else if(rule == 1 && boardy >= matManager.vFieldRemove[0][rule][0].Pos.Y && boardy <= matManager.vFieldRemove[0][rule][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_REMOVED;
			} else if(boardy >= matManager.vFieldSzone[1][5][rule][2].Pos.Y && boardy <= matManager.vFieldSzone[1][5][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 5;
			} else if(boardy >= matManager.vFieldExtra[1][2].Pos.Y && boardy <= matManager.vFieldExtra[1][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_EXTRA;
			}
		} else if(rule == 1 && boardx >= matManager.vFieldSzone[0][7][rule][0].Pos.X && boardx <= matManager.vFieldSzone[0][7][rule][1].Pos.X) {
			// deprecated szone[7]
			if(boardy >= matManager.vFieldSzone[0][7][rule][0].Pos.Y && boardy <= matManager.vFieldSzone[0][7][rule][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 7;
			}
		} else if(rule == 0 && boardx >= matManager.vFieldRemove[0][rule][0].Pos.X && boardx <= matManager.vFieldRemove[0][rule][1].Pos.X) {
			if(boardy >= matManager.vFieldRemove[0][rule][0].Pos.Y && boardy <= matManager.vFieldRemove[0][rule][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_REMOVED;
			}
		} else if(boardx >= matManager.vFieldMzone[0][0][0].Pos.X && boardx <= matManager.vFieldMzone[0][4][1].Pos.X) {
			int sequence = (boardx - matManager.vFieldMzone[0][0][0].Pos.X) / (matManager.vFieldMzone[0][0][1].Pos.X - matManager.vFieldMzone[0][0][0].Pos.X);
			if(sequence > 4)
				sequence = 4;
			if(boardy > matManager.vFieldSzone[0][0][rule][0].Pos.Y && boardy <= matManager.vFieldSzone[0][0][rule][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = sequence;
			} else if(boardy >= matManager.vFieldMzone[0][0][0].Pos.Y && boardy <= matManager.vFieldMzone[0][0][2].Pos.Y) {
				hovered_controler = 0;
				hovered_location = LOCATION_MZONE;
				hovered_sequence = sequence;
			} else if(boardy >= matManager.vFieldMzone[0][5][0].Pos.Y && boardy <= matManager.vFieldMzone[0][5][2].Pos.Y) {
				if(sequence == 1) {
					if (mzone[0][5]) {
						hovered_controler = 0;
						hovered_location = LOCATION_MZONE;
						hovered_sequence = 5;
					}
					else if(mzone[1][6]) {
						hovered_controler = 1;
						hovered_location = LOCATION_MZONE;
						hovered_sequence = 6;
					}
					else if((mainGame->dInfo.curMsg == MSG_SELECT_PLACE || mainGame->dInfo.curMsg == MSG_SELECT_DISFIELD)) {
						if (mainGame->dField.selectable_field & (0x1 << (16 + 6))) {
							hovered_controler = 1;
							hovered_location = LOCATION_MZONE;
							hovered_sequence = 6;
						}
						else {
							hovered_controler = 0;
							hovered_location = LOCATION_MZONE;
							hovered_sequence = 5;
						}
					}
					else{
						hovered_controler = 0;
						hovered_location = LOCATION_MZONE;
						hovered_sequence = 5;
					}
				}
				else if(sequence == 3) {
					if (mzone[0][6]) {
						hovered_controler = 0;
						hovered_location = LOCATION_MZONE;
						hovered_sequence = 6;
					}
					else if (mzone[1][5]) {
						hovered_controler = 1;
						hovered_location = LOCATION_MZONE;
						hovered_sequence = 5;
					}
					else if ((mainGame->dInfo.curMsg == MSG_SELECT_PLACE || mainGame->dInfo.curMsg == MSG_SELECT_DISFIELD)) {
						if (mainGame->dField.selectable_field & (0x1 << (16 + 5))) {
							hovered_controler = 1;
							hovered_location = LOCATION_MZONE;
							hovered_sequence = 5;
						}
						else {
							hovered_controler = 0;
							hovered_location = LOCATION_MZONE;
							hovered_sequence = 6;
						}
					}
					else {
						hovered_controler = 0;
						hovered_location = LOCATION_MZONE;
						hovered_sequence = 6;
					}
				}
			} else if(boardy >= matManager.vFieldMzone[1][0][2].Pos.Y && boardy <= matManager.vFieldMzone[1][0][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_MZONE;
				hovered_sequence = 4 - sequence;
			} else if(boardy >= matManager.vFieldSzone[1][0][rule][2].Pos.Y && boardy < matManager.vFieldSzone[1][0][rule][0].Pos.Y) {
				hovered_controler = 1;
				hovered_location = LOCATION_SZONE;
				hovered_sequence = 4 - sequence;
			}
		}
	}
}
/**
 * @brief 显示右键菜单界面，根据传入的标志位决定显示哪些操作按钮。
 *
 * 此函数用于在游戏客户端中响应用户点击卡牌时弹出的操作菜单。它会根据传入的 flag 参数，
 * 判断需要显示哪些命令按钮（如召唤、特殊召唤、设置等），并动态调整这些按钮的位置。
 * 同时禁用部分主界面按钮以防止误操作，并将菜单窗口定位到指定坐标位置。
 *
 * @param flag 操作命令标志位组合，每一位代表一个可执行的操作类型。
 * @param x 菜单左上角横坐标（屏幕像素）。
 * @param y 菜单左上角纵坐标（屏幕像素）。
 */
void ClientField::ShowMenu(int flag, int x, int y) {
	// 如果没有可用操作，则隐藏当前菜单并直接返回
	if(!flag) {
		HideMenu();
		return;
	}

	// 记录被点击的卡牌对象
	menu_card = clicked_card;

	// 初始化菜单高度计数器，从顶部开始排列按钮
	int height = 1;

	// 根据标志位依次判断是否启用各个功能按钮，并设置其可见性和相对位置

	// 【发动】按钮处理逻辑
	if(flag & COMMAND_ACTIVATE) {
		mainGame->btnActivate->setVisible(true);
		mainGame->btnActivate->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnActivate->setVisible(false);
	}

	// 【召唤】按钮处理逻辑
	if(flag & COMMAND_SUMMON) {
		mainGame->btnSummon->setVisible(true);
		mainGame->btnSummon->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnSummon->setVisible(false);
	}

	// 【特殊召唤】按钮处理逻辑
	if(flag & COMMAND_SPSUMMON) {
		mainGame->btnSPSummon->setVisible(true);
		mainGame->btnSPSummon->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnSPSummon->setVisible(false);
	}

	// 【盖放（怪兽区）】按钮处理逻辑
	if(flag & COMMAND_MSET) {
		mainGame->btnMSet->setVisible(true);
		mainGame->btnMSet->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnMSet->setVisible(false);
	}

	// 【盖放（魔法·陷阱区）】按钮处理逻辑
	if(flag & COMMAND_SSET) {
		// 区分怪兽卡与其他类型卡片的文字提示
		if(!(clicked_card->type & TYPE_MONSTER))
			mainGame->btnSSet->setText(dataManager.GetSysString(1153)); // 设置魔法陷阱卡
		else
			mainGame->btnSSet->setText(dataManager.GetSysString(1159)); // 设置怪兽卡为里侧守备
		mainGame->btnSSet->setVisible(true);
		mainGame->btnSSet->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnSSet->setVisible(false);
	}

	// 【改变表示形式】按钮处理逻辑
	if(flag & COMMAND_REPOS) {
		// 根据当前卡牌状态设置不同的按钮文字
		if(clicked_card->position & POS_FACEDOWN)
			mainGame->btnRepos->setText(dataManager.GetSysString(1154)); // 反转
		else if(clicked_card->position & POS_ATTACK)
			mainGame->btnRepos->setText(dataManager.GetSysString(1155)); // 改为守备
		else
			mainGame->btnRepos->setText(dataManager.GetSysString(1156)); // 改为攻击
		mainGame->btnRepos->setVisible(true);
		mainGame->btnRepos->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnRepos->setVisible(false);
	}

	// 【攻击宣言】按钮处理逻辑
	if(flag & COMMAND_ATTACK) {
		mainGame->btnAttack->setVisible(true);
		mainGame->btnAttack->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnAttack->setVisible(false);
	}

	// 【查看连锁列表】按钮处理逻辑
	if(flag & COMMAND_LIST) {
		mainGame->btnShowList->setVisible(true);
		mainGame->btnShowList->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnShowList->setVisible(false);
	}

	// 【操作】按钮处理逻辑
	if(flag & COMMAND_OPERATION) {
		mainGame->btnOperation->setVisible(true);
		mainGame->btnOperation->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnOperation->setVisible(false);
	}

	// 【重置】按钮处理逻辑
	if(flag & COMMAND_RESET) {
		mainGame->btnReset->setVisible(true);
		mainGame->btnReset->setRelativePosition(irr::core::vector2di(1, height));
		height += 60 * mainGame->yScale;
	} else {
		mainGame->btnReset->setVisible(false);
	}

	// 设置当前面板为命令菜单窗口，并使其可见
	panel = mainGame->wCmdMenu;
	mainGame->wCmdMenu->setVisible(true);

	// 禁用战斗阶段相关按钮避免冲突
	mainGame->btnBP->setEnabled(false);
	mainGame->btnM2->setEnabled(false);
	mainGame->btnEP->setEnabled(false);

	// 设置悬浮命令菜单窗口的位置与尺寸
	mainGame->wCmdMenu->setRelativePosition(
		irr::core::recti(
			x - 20 * mainGame->xScale,
			y - 30 * mainGame->yScale - height,
			x + 130 * mainGame->xScale,
			y - 30 * mainGame->yScale
		)
	);
}

void ClientField::HideMenu() {
	mainGame->wCmdMenu->setVisible(false);
	mainGame->btnBP->setEnabled(true);
	mainGame->btnM2->setEnabled(true);
	mainGame->btnEP->setEnabled(true);
}
void ClientField::UpdateChainButtons() {
	if(mainGame->btnChainAlways->isVisible()) {
		mainGame->btnChainIgnore->setPressed(mainGame->ignore_chain);
		mainGame->btnChainAlways->setPressed(mainGame->always_chain);
		mainGame->btnChainWhenAvail->setPressed(mainGame->chain_when_avail);
	}
}
void ClientField::ShowCancelOrFinishButton(int buttonOp) {
	if (!mainGame->gameConf.hide_hint_button && !mainGame->dInfo.isReplay) {
		switch (buttonOp) {
		case 1:
			mainGame->btnCancelOrFinish->setText(dataManager.GetSysString(1295));
			mainGame->btnCancelOrFinish->setVisible(true);
			break;
		case 2:
			mainGame->btnCancelOrFinish->setText(dataManager.GetSysString(1296));
			mainGame->btnCancelOrFinish->setVisible(true);
			break;
		case 0:
		default:
			mainGame->btnCancelOrFinish->setVisible(false);
			break;
		}
	} else {
		mainGame->btnCancelOrFinish->setVisible(false);
	}
}
void ClientField::SetShowMark(ClientCard* pcard, bool enable) {
	if(pcard->equipTarget)
		pcard->equipTarget->is_showequip = enable;
	for (auto& card : pcard->equipped)
		card->is_showequip = enable;
	for (auto& card : pcard->cardTarget)
		card->is_showtarget = enable;
	for (auto& card : pcard->ownerTarget)
		card->is_showtarget = enable;
	for (auto& ch : chains) {
		if (pcard == ch.chain_card) {
			for (auto& tg : ch.target)
				tg->is_showchaintarget = enable;
		}
		if (ch.target.find(pcard) != ch.target.end())
			ch.chain_card->is_showchaintarget = enable;
	}
}
void ClientField::ShowCardInfoInList(ClientCard* pcard, irr::gui::IGUIElement* element, irr::gui::IGUIElement* parent) {
	std::wstring str(L"");
	wchar_t formatBuffer[2048];
	if(pcard->code) {
		str.append(dataManager.GetName(pcard->code));
	}
	if (pcard->location != LOCATION_DECK) {
		if (pcard->overlayTarget) {
			myswprintf(formatBuffer, dataManager.GetSysString(225), dataManager.GetName(pcard->overlayTarget->code), pcard->overlayTarget->sequence + 1);
			str.append(L"\n").append(formatBuffer);
		}
		if ((pcard->status & STATUS_PROC_COMPLETE)
			&& (pcard->type & (TYPE_RITUAL | TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK | TYPE_SPSUMMON)))
			str.append(L"\n").append(dataManager.GetSysString(224));
		for (auto iter = pcard->desc_hints.begin(); iter != pcard->desc_hints.end(); ++iter) {
			myswprintf(formatBuffer, L"\n*%ls", dataManager.GetDesc(iter->first));
			str.append(formatBuffer);
		}
		for (size_t i = 0; i < chains.size(); ++i) {
			const auto& chit = chains[i];
			if (pcard == chit.chain_card) {
				myswprintf(formatBuffer, dataManager.GetSysString(216), i + 1);
				str.append(L"\n").append(formatBuffer);
			}
			if (chit.target.find(pcard) != chit.target.end()) {
				myswprintf(formatBuffer, dataManager.GetSysString(217), i + 1, dataManager.GetName(chit.chain_card->code));
				str.append(L"\n").append(formatBuffer);
			}
		}
	}
	if(str.length() > 0) {
		parent->addChild(mainGame->stCardListTip);
		irr::core::rect<irr::s32> ePos = element->getRelativePosition();
		irr::s32 x = (ePos.UpperLeftCorner.X + ePos.LowerRightCorner.X) / 2;
		irr::s32 y = ePos.LowerRightCorner.Y;
		mainGame->SetStaticText(mainGame->stCardListTip, 320 * mainGame->xScale, mainGame->guiFont, str.c_str());
		irr::core::dimension2d<unsigned int> dTip = mainGame->guiFont->getDimension(mainGame->stCardListTip->getText()) + irr::core::dimension2d<unsigned int>(10, 10);
		irr::s32 w = dTip.Width / 2;
		if(x - w < 10 * mainGame->xScale)
			x = w + 10 * mainGame->xScale;
		if(x + w > 670 * mainGame->xScale)
			x = 670 * mainGame->xScale - w;
		mainGame->stCardListTip->setRelativePosition(irr::core::recti(x - dTip.Width / 2, y - 10 * mainGame->yScale, x + dTip.Width / 2, y - 10 * mainGame->yScale + dTip.Height));
		mainGame->stCardListTip->setVisible(true);
	}
}
/**
 * @brief 设置客户端响应中选中的卡片信息
 *
 * 该函数将当前选中的卡片序列号打包成响应数据，并发送给决斗客户端。
 * 响应数据格式为：第一个字节表示选中卡片数量，后续字节依次为各卡片的选择序号。
 *
 * @param 无参数
 * @return 无返回值
 */
void ClientField::SetResponseSelectedCards() const {
	// 准备响应数据缓冲区
	unsigned char respbuf[SIZE_RETURN_VALUE]{};

	// 获取选中卡片数量，最多不超过UINT8_MAX个
	int len = (int)selected_cards.size();
	if (len > UINT8_MAX)
		len = UINT8_MAX;

	// 设置响应数据：第一个字节为卡片数量
	respbuf[0] = (unsigned char)len;

	// 依次填充每张选中卡片的选择序号
	for (int i = 0; i < len; ++i)
		respbuf[i + 1] = selected_cards[i]->select_seq;

	// 发送响应数据到决斗客户端
	DuelClient::SetResponseB(respbuf, len + 1);
}
/**
 * @brief 设置客户端选择的响应选项
 *
 * 根据当前游戏消息类型和选择的选项，设置相应的响应数据，
 * 并隐藏选项窗口
 *
 * @param 无
 * @return 无
 */
void ClientField::SetResponseSelectedOption() const {
	// 处理选项选择消息
	if(mainGame->dInfo.curMsg == MSG_SELECT_OPTION) {
		DuelClient::SetResponseI(selected_option);
	} else {
		// 获取选择选项的索引
		int index = select_options_index[selected_option];
		// 根据不同的消息类型设置响应数据
		if(mainGame->dInfo.curMsg == MSG_SELECT_IDLECMD) {
			DuelClient::SetResponseI((index << 16) + 5);
		} else if(mainGame->dInfo.curMsg == MSG_SELECT_BATTLECMD) {
			DuelClient::SetResponseI(index << 16);
		} else {
			DuelClient::SetResponseI(index);
		}
	}
	// 隐藏选项窗口
	mainGame->HideElement(mainGame->wOptions, true);
}
/**
 * @brief 处理取消或完成操作的函数
 *
 * 根据当前游戏消息类型处理相应的取消或完成逻辑，包括隐藏界面元素、设置响应数据、发送响应等操作
 */
void ClientField::CancelOrFinish() {
	// 根据当前游戏消息类型进行不同的处理
	switch(mainGame->dInfo.curMsg) {
        case MSG_WAITING: {
            // 如果卡片选择窗口可见，则隐藏它并隐藏取消/完成按钮
            if(mainGame->wCardSelect->isVisible()) {
                mainGame->HideElement(mainGame->wCardSelect);
                ShowCancelOrFinishButton(0);
            }
            break;
        }
        case MSG_SELECT_BATTLECMD: {
            // 如果卡片选择窗口可见，则隐藏它并隐藏取消/完成按钮
            if(mainGame->wCardSelect->isVisible()) {
                mainGame->HideElement(mainGame->wCardSelect);
                ShowCancelOrFinishButton(0);
            }
            // 如果选项窗口可见，则隐藏它并隐藏取消/完成按钮
            if(mainGame->wOptions->isVisible()) {
                mainGame->HideElement(mainGame->wOptions);
                ShowCancelOrFinishButton(0);
            }
            break;
        }
        case MSG_SELECT_IDLECMD: {
            // 如果卡片选择窗口可见，则隐藏它并隐藏取消/完成按钮
            if(mainGame->wCardSelect->isVisible()) {
                mainGame->HideElement(mainGame->wCardSelect);
                ShowCancelOrFinishButton(0);
            }
            // 如果选项窗口可见，则隐藏它并隐藏取消/完成按钮
            if(mainGame->wOptions->isVisible()) {
                mainGame->HideElement(mainGame->wOptions);
                ShowCancelOrFinishButton(0);
            }
            break;
        }
        case MSG_SELECT_YESNO:
        case MSG_SELECT_EFFECTYN: {
            // 如果有高亮显示的卡片，则取消高亮
            if(highlighting_card)
                highlighting_card->is_highlighting = false;
            highlighting_card = 0;
            // 设置响应为否(0)
            DuelClient::SetResponseI(0);
            // 隐藏询问窗口并发送响应
            mainGame->HideElement(mainGame->wQuery, true);
            break;
        }
        case MSG_SELECT_CARD: {
            // 如果没有选择任何卡片且可以选择取消
            if(selected_cards.size() == 0) {
                if(select_cancelable) {
                    // 设置响应为-1表示取消选择
                    DuelClient::SetResponseI(-1);
                    ShowCancelOrFinishButton(0);
                    // 如果卡片选择窗口可见则隐藏它，否则直接发送响应
                    if(mainGame->wCardSelect->isVisible())
                        mainGame->HideElement(mainGame->wCardSelect, true);
                    else
                        DuelClient::SendResponse();
                }
            }
            // 如果询问窗口可见
            if(mainGame->wQuery->isVisible()) {
                // 设置选中卡片的响应并隐藏询问窗口
                SetResponseSelectedCards();
                ShowCancelOrFinishButton(0);
                mainGame->HideElement(mainGame->wQuery, true);
                break;
            }
            // 如果已准备好选择
            if(select_ready) {
                // 设置选中卡片的响应
                SetResponseSelectedCards();
                ShowCancelOrFinishButton(0);
                // 如果卡片选择窗口可见则隐藏它，否则直接发送响应
                if(mainGame->wCardSelect->isVisible())
                    mainGame->HideElement(mainGame->wCardSelect, true);
                else
                    DuelClient::SendResponse();
            }
            break;
        }
        case MSG_SELECT_UNSELECT_CARD: {
            // 如果可以选择取消
            if (select_cancelable) {
                // 设置响应为-1表示取消选择
                DuelClient::SetResponseI(-1);
                ShowCancelOrFinishButton(0);
                // 如果卡片选择窗口可见则隐藏它，否则直接发送响应
                if (mainGame->wCardSelect->isVisible())
                    mainGame->HideElement(mainGame->wCardSelect, true);
                else
                    DuelClient::SendResponse();
            }
            break;
        }
        case MSG_SELECT_TRIBUTE: {
            // 如果没有选择任何卡片且可以选择取消
            if(selected_cards.size() == 0) {
                if(select_cancelable) {
                    // 设置响应为-1表示取消选择
                    DuelClient::SetResponseI(-1);
                    ShowCancelOrFinishButton(0);
                    // 如果卡片选择窗口可见则隐藏它，否则直接发送响应
                    if(mainGame->wCardSelect->isVisible())
                        mainGame->HideElement(mainGame->wCardSelect, true);
                    else
                        DuelClient::SendResponse();
                }
                break;
            }
            // 如果询问窗口可见
            if(mainGame->wQuery->isVisible()) {
                // 设置选中卡片的响应并隐藏询问窗口
                SetResponseSelectedCards();
                ShowCancelOrFinishButton(0);
                mainGame->HideElement(mainGame->wQuery, true);
                break;
            }
            // 如果已准备好选择
            if(select_ready) {
                // 设置选中卡片的响应并发送
                SetResponseSelectedCards();
                ShowCancelOrFinishButton(0);
                DuelClient::SendResponse();
            }
            break;
        }
        case MSG_SELECT_SUM: {
            // 如果已准备好选择
            if (select_ready) {
                // 设置选中卡片的响应
                SetResponseSelectedCards();
                ShowCancelOrFinishButton(0);
                // 如果卡片选择窗口可见则隐藏它，否则直接发送响应
                if(mainGame->wCardSelect->isVisible())
                    mainGame->HideElement(mainGame->wCardSelect, true);
                else
                    DuelClient::SendResponse();
            }
            break;
        }
        case MSG_SELECT_CHAIN: {
            // 如果必须选择连锁则不能取消
            if(chain_forced)
                break;
            // 如果卡片选择窗口可见则隐藏它
            if(mainGame->wCardSelect->isVisible()) {
                mainGame->HideElement(mainGame->wCardSelect);
                break;
            }
            // 如果询问窗口可见
            if(mainGame->wQuery->isVisible()) {
                // 设置响应为-1表示不选择连锁并隐藏询问窗口
                DuelClient::SetResponseI(-1);
                ShowCancelOrFinishButton(0);
                mainGame->HideElement(mainGame->wQuery, true);
            } else {
                // 弹出询问窗口
                mainGame->PopupElement(mainGame->wQuery);
                ShowCancelOrFinishButton(0);
            }
            // 如果选项窗口可见
            if(mainGame->wOptions->isVisible()) {
                // 设置响应为-1表示不选择选项并隐藏选项窗口
                DuelClient::SetResponseI(-1);
                ShowCancelOrFinishButton(0);
                mainGame->HideElement(mainGame->wOptions);
            }
            break;
        }
        case MSG_SORT_CARD: {
            // 如果卡片选择窗口可见
            if(mainGame->wCardSelect->isVisible()) {
                // 设置响应为-1表示取消排序并隐藏卡片选择窗口
                DuelClient::SetResponseI(-1);
                mainGame->HideElement(mainGame->wCardSelect, true);
                // 清空排序列表
                sort_list.clear();
            }
            break;
        }
        case MSG_SELECT_PLACE: {
            // 如果可以选择取消
            if(select_cancelable) {
                // 准备响应数据，表示不选择任何位置
                unsigned char respbuf[3];
                respbuf[0] = mainGame->LocalPlayer(0);
                respbuf[1] = 0;
                respbuf[2] = 0;
                // 清空可选择字段
                mainGame->dField.selectable_field = 0;
                // 设置并发送响应
                DuelClient::SetResponseB(respbuf, 3);
                DuelClient::SendResponse();
                // 隐藏取消/完成按钮
                ShowCancelOrFinishButton(0);
            }
            break;
        }
	}
}

}

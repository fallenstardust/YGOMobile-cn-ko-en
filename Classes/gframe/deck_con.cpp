#include <array>
#include "config.h"
#include "deck_con.h"
#include "myfilesystem.h"
#include "image_manager.h"
#include "game.h"
#include "duelclient.h"

namespace ygo {

static int parse_filter(const wchar_t* pstr, unsigned int* type) {
	if(*pstr == L'=') {
		*type = 1;
		return BufferIO::GetVal(pstr + 1);
	} else if(*pstr >= L'0' && *pstr <= L'9') {
		*type = 1;
		return BufferIO::GetVal(pstr);
	} else if(*pstr == L'>') {
		if(*(pstr + 1) == L'=') {
			*type = 2;
			return BufferIO::GetVal(pstr + 2);
		} else {
			*type = 3;
			return BufferIO::GetVal(pstr + 1);
		}
	} else if(*pstr == L'<') {
		if(*(pstr + 1) == L'=') {
			*type = 4;
			return BufferIO::GetVal(pstr + 2);
		} else {
			*type = 5;
			return BufferIO::GetVal(pstr + 1);
		}
	} else if(*pstr == L'?') {
		*type = 6;
		return 0;
	}
	*type = 0;
	return 0;
}


static inline bool havePopupWindow() {
	return mainGame->wQuery->isVisible() || mainGame->wCategories->isVisible() || mainGame->wLinkMarks->isVisible() || mainGame->wDeckManage->isVisible() || mainGame->wDMQuery->isVisible();
}

void SetCategoryDeckNameOnButton(irr::gui::IGUIButton* button, wchar_t* string){
	wchar_t cate[256];
	wchar_t cate_deck[256];
	myswprintf(cate, L"%ls%ls", (mainGame->cbDBCategory->getSelected())==2 ? L"" : mainGame->cbDBCategory->getItem(mainGame->cbDBCategory->getSelected()), (mainGame->cbDBCategory->getSelected())==2 ? L"" : string);
    if (mainGame->cbDBDecks->getItemCount() != 0) {
        myswprintf(cate_deck, L"%ls%ls", cate, mainGame->cbDBDecks->getItem(mainGame->cbDBDecks->getSelected()));
    } else {
        myswprintf(cate_deck, L"%ls%ls", cate, dataManager.GetSysString(1301));
    }
	button->setText(cate_deck);
}

static inline void get_deck_file(wchar_t* ret) {
	deckManager.GetDeckFile(ret, mainGame->cbDBCategory, mainGame->cbDBDecks);
}

static inline void load_current_deck(irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDeck) {
	deckManager.LoadCurrentDeck(cbCategory, cbDeck);
}

DeckBuilder::DeckBuilder() {
	std::random_device rd;
	std::array<uint32_t, 8> seed{};
	for (auto& x : seed)
		x = rd();
	std::seed_seq seq(seed.begin(), seed.end());
	rnd.seed(seq);
}
void DeckBuilder::Initialize() {
	mainGame->is_building = true;
	mainGame->is_siding = false;
	mainGame->ClearCardInfo();
	mainGame->wInfos->setVisible(true);
	mainGame->wCardImg->setVisible(true);
	mainGame->wDeckEdit->setVisible(true);
	mainGame->wFilter->setVisible(true);
	mainGame->wSort->setVisible(true);
	mainGame->btnLeaveGame->setVisible(true);
	mainGame->btnLeaveGame->setText(dataManager.GetSysString(1306));
	mainGame->wPallet->setVisible(true);
    mainGame->btnDeleteDeck->setVisible(true);
    mainGame->btnShuffleDeck->setVisible(true);
    mainGame->btnSortDeck->setVisible(true);
    mainGame->btnClearDeck->setVisible(true);
	mainGame->imgChat->setVisible(false);
	mainGame->imgQuickAnimation->setVisible(false);
	mainGame->btnSideOK->setVisible(false);
	mainGame->btnSideShuffle->setVisible(false);
	mainGame->btnSideSort->setVisible(false);
	mainGame->btnSideReload->setVisible(false);
	if (mainGame->gameConf.use_lflist) {
		if (mainGame->gameConf.default_lflist >= 0 && mainGame->gameConf.default_lflist < (int)deckManager._lfList.size()) {
			filterList = &deckManager._lfList[mainGame->gameConf.default_lflist];
		}
		else {
			mainGame->gameConf.default_lflist = 0;
			filterList = &deckManager._lfList.front();
		}
	}
	else {
		filterList = &deckManager._lfList.back();
	}
	ClearSearch();
	mouse_pos.set(0, 0);
	hovered_code = 0;
	hovered_pos = 0;
	hovered_seq = -1;
	is_lastcard = 0;
	is_draging = false;
	is_starting_dragging = false;
	prev_deck = mainGame->cbDBDecks->getSelected();
	prev_category = mainGame->cbDBCategory->getSelected();
	RefreshReadonly(prev_category);
	RefreshPackListScroll();
    SetCategoryDeckNameOnButton(mainGame->btnManageDeck, L"\n");
	prev_operation = 0;
	prev_sel = -1;
	is_modified = false;
	mainGame->device->setEventReceiver(this);
}
void DeckBuilder::Terminate() {
	mainGame->is_building = false;
	mainGame->ClearCardInfo();
	mainGame->wDeckEdit->setVisible(false);
	mainGame->wCategories->setVisible(false);
	mainGame->wFilter->setVisible(false);
	mainGame->wSort->setVisible(false);
	mainGame->wCardImg->setVisible(false);
	mainGame->wInfos->setVisible(false);
	mainGame->btnLeaveGame->setVisible(false);
    mainGame->wPallet->setVisible(false);
    mainGame->btnDeleteDeck->setVisible(false);
    mainGame->btnShuffleDeck->setVisible(false);
    mainGame->btnSortDeck->setVisible(false);
    mainGame->btnClearDeck->setVisible(false);
	mainGame->ResizeChatInputWindow();
    mainGame->imgChat->setVisible(true);
    mainGame->imgQuickAnimation->setVisible(true);
    mainGame->wSettings->setVisible(false);
    mainGame->wLogs->setVisible(false);
	mainGame->PopupElement(mainGame->wMainMenu);
	mainGame->device->setEventReceiver(&mainGame->menuHandler);
	mainGame->wACMessage->setVisible(false);
	mainGame->ClearTextures();
	mainGame->scrFilter->setVisible(false);
	mainGame->scrPackCards->setVisible(false);
	mainGame->scrPackCards->setPos(0);
	int catesel = mainGame->cbDBCategory->getSelected();
	char linebuf[256];
	if (catesel >= 0)
		BufferIO::CopyWideString(mainGame->cbDBCategory->getItem(catesel), mainGame->gameConf.lastcategory);
	    BufferIO::EncodeUTF8(mainGame->gameConf.lastcategory, linebuf);
		irr::android::setLastCategory(mainGame->appMain, linebuf);
		//irr:os::Printer::log("setLastCategory", linebuf);
	int decksel = mainGame->cbDBDecks->getSelected();
	if (decksel >= 0)
		BufferIO::CopyWideString(mainGame->cbDBDecks->getItem(decksel), mainGame->gameConf.lastdeck);
		BufferIO::EncodeUTF8(mainGame->gameConf.lastdeck, linebuf);
		irr::android::setLastDeck(mainGame->appMain, linebuf);
		//os::Printer::log("setLastDeck", linebuf);
	mainGame->SaveConfig();
	if(exit_on_return)
		mainGame->OnGameClose();
}
bool DeckBuilder::OnEvent(const irr::SEvent& event) {
#ifdef _IRR_ANDROID_PLATFORM_
	irr::SEvent transferEvent;
	if (irr::android::TouchEventTransferAndroid::OnTransferDeckEdit(event)) {
		return true;
	}
#endif
	if(mainGame->dField.OnCommonEvent(event))
		return false;
	switch(event.EventType) {
	case irr::EET_GUI_EVENT: {
		irr::s32 id = event.GUIEvent.Caller->getID();
		if(((mainGame->wCategories->isVisible() && id != BUTTON_CATEGORY_OK) ||
			(mainGame->wQuery->isVisible() && id != BUTTON_YES && id != BUTTON_NO) ||
			(mainGame->wLinkMarks->isVisible() && id != BUTTON_MARKERS_OK) ||
			(mainGame->wDMQuery->isVisible() && id != BUTTON_DM_OK && id != BUTTON_DM_CANCEL) ||
			(mainGame->wDeckManage->isVisible() && !(id >= WINDOW_DECK_MANAGE && id < COMBOBOX_LFLIST)))
			&& event.GUIEvent.EventType != irr::gui::EGET_LISTBOX_CHANGED
			&& event.GUIEvent.EventType != irr::gui::EGET_COMBO_BOX_CHANGED) {
			if(mainGame->wDMQuery->isVisible())
				mainGame->wDMQuery->getParent()->bringToFront(mainGame->wDMQuery);
			break;
		}
		switch(event.GUIEvent.EventType) {
/*		case irr::gui::EGET_ELEMENT_CLOSED: {
*			if(id == WINDOW_DECK_MANAGE) {
*				mainGame->HideElement(mainGame->wDeckManage);
*				return true;
*				break;
*			}
*		}*/
		case irr::gui::EGET_BUTTON_CLICKED: {
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::BUTTON);
			switch(id) {
			case BUTTON_CLEAR_DECK: {
				mainGame->gMutex.lock();
				mainGame->SetStaticText(mainGame->stQMessage, 370, mainGame->guiFont, dataManager.GetSysString(1339));
				mainGame->PopupElement(mainGame->wQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_SORT_DECK: {
				std::sort(deckManager.current_deck.main.begin(), deckManager.current_deck.main.end(), DataManager::deck_sort_lv);
				std::sort(deckManager.current_deck.extra.begin(), deckManager.current_deck.extra.end(), DataManager::deck_sort_lv);
				std::sort(deckManager.current_deck.side.begin(), deckManager.current_deck.side.end(), DataManager::deck_sort_lv);
				break;
			}
			case BUTTON_SHUFFLE_DECK: {
				std::shuffle(deckManager.current_deck.main.begin(), deckManager.current_deck.main.end(), rnd);
				break;
			}
			case BUTTON_SAVE_DECK: {
				int sel = mainGame->cbDBDecks->getSelected();
				if(sel == -1)
					break;
				wchar_t filepath[256];
				get_deck_file(filepath);
				if(deckManager.SaveDeck(deckManager.current_deck, filepath)) {
					mainGame->stACMessage->setText(dataManager.GetSysString(1335));
					mainGame->PopupElement(mainGame->wACMessage, 20);
					is_modified = false;
				}
				break;
			}
			case BUTTON_SAVE_DECK_AS: {
				const wchar_t* dname = mainGame->ebDeckname->getText();
				if(*dname == 0)
					break;
				int sel = -1;
				for(int i = 0; i < (int)mainGame->cbDBDecks->getItemCount(); ++i) {
					if(!std::wcscmp(dname, mainGame->cbDBDecks->getItem(i))) {
						sel = i;
						break;
					}
				}
				if(sel >= 0)
					mainGame->cbDBDecks->setSelected(sel);
				else {
					mainGame->cbDBDecks->addItem(dname);
					mainGame->cbDBDecks->setSelected(mainGame->cbDBDecks->getItemCount() - 1);
				}
				int catesel = mainGame->cbDBCategory->getSelected();
				wchar_t catepath[256];
				deckManager.GetCategoryPath(catepath, catesel, mainGame->cbDBCategory->getText(), true);
				wchar_t filepath[256];
				myswprintf(filepath, L"%ls/%ls.ydk", catepath, dname);
				if(deckManager.SaveDeck(deckManager.current_deck, filepath)) {
					mainGame->stACMessage->setText(dataManager.GetSysString(1335));
					mainGame->PopupElement(mainGame->wACMessage, 20);
					is_modified = false;
					if(catesel == -1) {
						catesel = 2;
						prev_category = catesel;
						RefreshReadonly(catesel);
						mainGame->cbDBCategory->setSelected(catesel);
						mainGame->btnManageDeck->setEnabled(true);
						mainGame->cbDBCategory->setEnabled(true);
						mainGame->cbDBDecks->setEnabled(true);
					}
				}
				break;
			}
			case BUTTON_DELETE_DECK: {
				int sel = mainGame->cbDBDecks->getSelected();
				if(sel == -1)
					break;
				mainGame->gMutex.lock();
				wchar_t textBuffer[256];
				myswprintf(textBuffer, L"%ls\n%ls", mainGame->cbDBDecks->getItem(sel), dataManager.GetSysString(1337));
				mainGame->SetStaticText(mainGame->stQMessage, 370 * mainGame->xScale, mainGame->guiFont, textBuffer);
				mainGame->PopupElement(mainGame->wQuery);
				mainGame->gMutex.unlock();
                SetCategoryDeckNameOnButton(mainGame->btnManageDeck, L"\n");
				prev_operation = id;
				prev_sel = sel;
				break;
			}
			case BUTTON_LEAVE_GAME: {
				if(is_modified && !readonly && !mainGame->chkIgnoreDeckChanges->isChecked()) {
					mainGame->gMutex.lock();
					mainGame->SetStaticText(mainGame->stQMessage, 370 * mainGame->xScale, mainGame->guiFont, dataManager.GetSysString(1356));
					mainGame->PopupElement(mainGame->wQuery);
					mainGame->gMutex.unlock();
					prev_operation = id;
					break;
				}
				Terminate();
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
			case BUTTON_EFFECT_FILTER: {
				mainGame->PopupElement(mainGame->wCategories);
				break;
			}
			case BUTTON_START_FILTER: {
				StartFilter();
				if(!mainGame->gameConf.separate_clear_button)
					ClearFilter();
				break;
			}
			case BUTTON_CLEAR_FILTER: {
				ClearSearch();
				break;
			}
			case BUTTON_CATEGORY_OK: {
				filter_effect = 0;
				long long filter = 0x1;
				for(int i = 0; i < 32; ++i, filter <<= 1)
					if(mainGame->chkCategory[i]->isChecked())
						filter_effect |= filter;
				mainGame->btnEffectFilter->setPressed(filter_effect > 0);
				mainGame->HideElement(mainGame->wCategories);
				InstantSearch();
				break;
			}
			case BUTTON_MANAGE_DECK: {
				if(is_modified && !readonly && !mainGame->chkIgnoreDeckChanges->isChecked()) {
					mainGame->gMutex.lock();
					mainGame->SetStaticText(mainGame->stQMessage, 370 * mainGame->xScale, mainGame->guiFont, dataManager.GetSysString(1356));
					mainGame->PopupElement(mainGame->wQuery);
					mainGame->gMutex.unlock();
					prev_operation = id;
					break;
				}
				ShowDeckManage();
				break;
			}
			case BUTTON_NEW_CATEGORY: {
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1469));
				mainGame->ebDMName->setVisible(true);
				mainGame->ebDMName->setText(L"");
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_RENAME_CATEGORY: {
				if(mainGame->lstCategories->getSelected() < 4)
					break;
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1469));
				mainGame->ebDMName->setVisible(true);
				mainGame->ebDMName->setText(mainGame->lstCategories->getListItem(mainGame->lstCategories->getSelected()));
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_DELETE_CATEGORY: {
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1470));
				mainGame->stDMMessage2->setVisible(true);
				mainGame->stDMMessage2->setText(mainGame->lstCategories->getListItem(mainGame->lstCategories->getSelected()));
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_NEW_DECK: {
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1471));
				mainGame->ebDMName->setVisible(true);
				mainGame->ebDMName->setText(L"");
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_RENAME_DECK: {
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1471));
				mainGame->ebDMName->setVisible(true);
				mainGame->ebDMName->setText(mainGame->lstDecks->getListItem(mainGame->lstDecks->getSelected()));
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_DELETE_DECK_DM: {
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1337));
				mainGame->stDMMessage2->setVisible(true);
				mainGame->stDMMessage2->setText(mainGame->lstDecks->getListItem(mainGame->lstDecks->getSelected()));
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_MOVE_DECK: {
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1472));
				mainGame->cbDMCategory->setVisible(true);
				mainGame->cbDMCategory->clear();
				int catesel = mainGame->lstCategories->getSelected();
				if(catesel != 2)
					mainGame->cbDMCategory->addItem(dataManager.GetSysString(1452));
				for(int i = 4; i < (int)mainGame->lstCategories->getItemCount(); i++) {
					if(i != catesel)
						mainGame->cbDMCategory->addItem(mainGame->lstCategories->getListItem(i));
				}
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_COPY_DECK: {
				mainGame->gMutex.lock();
				mainGame->stDMMessage->setText(dataManager.GetSysString(1473));
				mainGame->cbDMCategory->setVisible(true);
				mainGame->cbDMCategory->clear();
				int catesel = mainGame->lstCategories->getSelected();
				if(catesel != 2)
					mainGame->cbDMCategory->addItem(dataManager.GetSysString(1452));
				for(int i = 4; i < (int)mainGame->lstCategories->getItemCount(); i++) {
					if(i != catesel)
						mainGame->cbDMCategory->addItem(mainGame->lstCategories->getListItem(i));
				}
				mainGame->PopupElement(mainGame->wDMQuery);
				mainGame->gMutex.unlock();
				prev_operation = id;
				break;
			}
			case BUTTON_DM_OK: {
				switch(prev_operation) {
				case BUTTON_NEW_CATEGORY: {
					int catesel = 0;
					const wchar_t* catename = mainGame->ebDMName->getText();
					if(deckManager.CreateCategory(catename)) {
						mainGame->cbDBCategory->addItem(catename);
						mainGame->lstCategories->addItem(catename);
						catesel = mainGame->lstCategories->getItemCount() - 1;
					} else {
						for(int i = 3; i < (int)mainGame->lstCategories->getItemCount(); i++) {
							if(!mywcsncasecmp(mainGame->lstCategories->getListItem(i), catename, 256)) {
								catesel = i;
								mainGame->stACMessage->setText(dataManager.GetSysString(1474));
								mainGame->PopupElement(mainGame->wACMessage, 20);
								break;
							}
						}
					}
					if(catesel > 0) {
						mainGame->lstCategories->setSelected(catesel);
						RefreshDeckList(true);
						mainGame->lstDecks->setSelected(0);
						mainGame->cbDBCategory->setSelected(catesel);
						ChangeCategory(catesel);
					}
					break;
				}
				case BUTTON_RENAME_CATEGORY: {
					int catesel = mainGame->lstCategories->getSelected();
					const wchar_t* oldcatename = mainGame->lstCategories->getListItem(catesel);
					const wchar_t* newcatename = mainGame->ebDMName->getText();
					if(deckManager.RenameCategory(oldcatename, newcatename)) {
						mainGame->cbDBCategory->removeItem(catesel);
						mainGame->cbDBCategory->addItem(newcatename);
						mainGame->lstCategories->removeItem(catesel);
						mainGame->lstCategories->addItem(newcatename);
						catesel = mainGame->lstCategories->getItemCount() - 1;
					} else {
						catesel = 0;
						for(int i = 3; i < (int)mainGame->lstCategories->getItemCount(); i++) {
							if(!mywcsncasecmp(mainGame->lstCategories->getListItem(i), newcatename, 256)) {
								catesel = i;
								mainGame->stACMessage->setText(dataManager.GetSysString(1474));
								mainGame->PopupElement(mainGame->wACMessage, 20);
								break;
							}
						}
					}
					if(catesel > 0) {
						mainGame->lstCategories->setSelected(catesel);
						RefreshDeckList(true);
						mainGame->lstDecks->setSelected(0);
						mainGame->cbDBCategory->setSelected(catesel);
						ChangeCategory(catesel);
					}
					break;
				}
				case BUTTON_DELETE_CATEGORY: {
					int catesel = mainGame->lstCategories->getSelected();
					const wchar_t* catename = mainGame->lstCategories->getListItem(catesel);
					if(deckManager.DeleteCategory(catename)) {
						mainGame->cbDBCategory->removeItem(catesel);
						mainGame->lstCategories->removeItem(catesel);
						catesel = 2;
						mainGame->lstCategories->setSelected(catesel);
						RefreshDeckList(true);
						mainGame->lstDecks->setSelected(0);
						mainGame->cbDBCategory->setSelected(catesel);
						ChangeCategory(catesel);
					} else {
						mainGame->stACMessage->setText(dataManager.GetSysString(1476));
						mainGame->PopupElement(mainGame->wACMessage, 20);
					}
					break;
				}
				case BUTTON_NEW_DECK: {
					const wchar_t* deckname = mainGame->ebDMName->getText();
					wchar_t catepath[256];
					deckManager.GetCategoryPath(catepath, mainGame->cbDBCategory->getSelected(), mainGame->cbDBCategory->getText(), true);
					wchar_t filepath[256];
					myswprintf(filepath, L"%ls/%ls.ydk", catepath, deckname);
					bool res = false;
					if(!FileSystem::IsFileExists(filepath)) {
						deckManager.current_deck.main.clear();
						deckManager.current_deck.extra.clear();
						deckManager.current_deck.side.clear();
						res = deckManager.SaveDeck(deckManager.current_deck, filepath);
						RefreshDeckList(true);
						ChangeCategory(mainGame->lstCategories->getSelected());
					}
					for(int i = 0; i < (int)mainGame->lstDecks->getItemCount(); i++) {
						if(!mywcsncasecmp(mainGame->lstDecks->getListItem(i), deckname, 256)) {
							deckManager.LoadCurrentDeck(filepath);
							prev_deck = i;
							mainGame->cbDBDecks->setSelected(prev_deck);
							mainGame->lstDecks->setSelected(prev_deck);
							if(!res) {
								mainGame->stACMessage->setText(dataManager.GetSysString(1475));
								mainGame->PopupElement(mainGame->wACMessage, 20);
							}
							break;
						}
					}
					break;
				}
				case BUTTON_RENAME_DECK: {
					int catesel = mainGame->lstCategories->getSelected();
					int decksel = mainGame->lstDecks->getSelected();
					const wchar_t* catename = mainGame->lstCategories->getListItem(catesel);
					wchar_t oldfilepath[256];
					get_deck_file(oldfilepath);
					const wchar_t* newdeckname = mainGame->ebDMName->getText();
					wchar_t newfilepath[256];
					if(catesel == 2) {
						myswprintf(newfilepath, L"./deck/%ls.ydk", newdeckname);
					} else {
						myswprintf(newfilepath, L"./deck/%ls/%ls.ydk", catename, newdeckname);
					}
					bool res = false;
					if(!FileSystem::IsFileExists(newfilepath)) {
						res = FileSystem::Rename(oldfilepath, newfilepath);
					}
					RefreshDeckList(true);
					ChangeCategory(catesel);
					for(int i = 0; i < (int)mainGame->lstDecks->getItemCount(); i++) {
						if(!mywcsncasecmp(mainGame->lstDecks->getListItem(i), newdeckname, 256)) {
							deckManager.LoadCurrentDeck(newfilepath);
							prev_deck = i;
							mainGame->cbDBDecks->setSelected(prev_deck);
							mainGame->lstDecks->setSelected(prev_deck);
							if(!res) {
								mainGame->stACMessage->setText(dataManager.GetSysString(1475));
								mainGame->PopupElement(mainGame->wACMessage, 20);
							}
							break;
						}
					}
					break;
				}
				case BUTTON_DELETE_DECK_DM: {
					int decksel = mainGame->lstDecks->getSelected();
					wchar_t filepath[256];
					get_deck_file(filepath);
					if(deckManager.DeleteDeck(filepath)) {
						mainGame->lstDecks->removeItem(decksel);
						mainGame->cbDBDecks->removeItem(decksel);
						decksel--;
						if(decksel == -1) {
							decksel = mainGame->lstDecks->getItemCount() - 1;
						}
						if(decksel != -1) {
							mainGame->lstDecks->setSelected(decksel);
							mainGame->cbDBDecks->setSelected(decksel);
							load_current_deck(mainGame->cbDBCategory, mainGame->cbDBDecks);
						}
						RefreshReadonly(prev_category);
						prev_deck = decksel;
					} else {
						mainGame->stACMessage->setText(dataManager.GetSysString(1476));
						mainGame->PopupElement(mainGame->wACMessage, 20);
					}
					break;
				}
				case BUTTON_MOVE_DECK: {
					int oldcatesel = mainGame->lstCategories->getSelected();
					int newcatesel = mainGame->cbDMCategory->getSelected();
					int decksel = mainGame->lstDecks->getSelected();
					const wchar_t* newcatename = mainGame->cbDMCategory->getText();
					const wchar_t* olddeckname = mainGame->lstDecks->getListItem(decksel);
					wchar_t deckname[256];
					BufferIO::CopyWideString(olddeckname, deckname);
					wchar_t oldfilepath[256];
					get_deck_file(oldfilepath);
					wchar_t newfilepath[256];
					if(oldcatesel != 2 && newcatesel == 0) {
						myswprintf(newfilepath, L"./deck/%ls.ydk", deckname);
					} else {
						myswprintf(newfilepath, L"./deck/%ls/%ls.ydk", newcatename, deckname);
					}
					bool res = false;
					if(!FileSystem::IsFileExists(newfilepath)) {
						res = FileSystem::Rename(oldfilepath, newfilepath);
					}
					mainGame->lstCategories->setSelected(newcatename);
					int catesel = mainGame->lstCategories->getSelected();
					RefreshDeckList(true);
					mainGame->cbDBCategory->setSelected(catesel);
					ChangeCategory(catesel);
					for(int i = 0; i < (int)mainGame->lstDecks->getItemCount(); i++) {
						if(!mywcsncasecmp(mainGame->lstDecks->getListItem(i), deckname, 256)) {
							deckManager.LoadCurrentDeck(newfilepath);
							prev_deck = i;
							mainGame->cbDBDecks->setSelected(prev_deck);
							mainGame->lstDecks->setSelected(prev_deck);
							if(!res) {
								mainGame->stACMessage->setText(dataManager.GetSysString(1475));
								mainGame->PopupElement(mainGame->wACMessage, 20);
							}
							break;
						}
					}
					break;
				}
				case BUTTON_COPY_DECK: {
					int oldcatesel = mainGame->lstCategories->getSelected();
					int newcatesel = mainGame->cbDMCategory->getSelected();
					int decksel = mainGame->lstDecks->getSelected();
					const wchar_t* newcatename = mainGame->cbDMCategory->getText();
					const wchar_t* olddeckname = mainGame->lstDecks->getListItem(decksel);
					wchar_t deckname[256];
					BufferIO::CopyWideString(olddeckname, deckname);
					wchar_t newfilepath[256];
					if(oldcatesel != 2 && newcatesel == 0) {
						myswprintf(newfilepath, L"./deck/%ls.ydk", deckname);
					} else {
						myswprintf(newfilepath, L"./deck/%ls/%ls.ydk", newcatename, deckname);
					}
					bool res = false;
					if(!FileSystem::IsFileExists(newfilepath)) {
						res = deckManager.SaveDeck(deckManager.current_deck, newfilepath);
					}
					mainGame->lstCategories->setSelected(newcatename);
					int catesel = mainGame->lstCategories->getSelected();
					RefreshDeckList(true);
					mainGame->cbDBCategory->setSelected(catesel);
					ChangeCategory(catesel);
					for(int i = 0; i < (int)mainGame->lstDecks->getItemCount(); i++) {
						if(!mywcsncasecmp(mainGame->lstDecks->getListItem(i), deckname, 256)) {
							deckManager.LoadCurrentDeck(newfilepath);
							prev_deck = i;
							mainGame->cbDBDecks->setSelected(prev_deck);
							mainGame->lstDecks->setSelected(prev_deck);
							if(!res) {
								mainGame->stACMessage->setText(dataManager.GetSysString(1475));
								mainGame->PopupElement(mainGame->wACMessage, 20);
							}
							break;
						}
					}
					break;
				}
				default:
					break;
				}
				prev_operation = 0;
				mainGame->HideElement(mainGame->wDMQuery);
				mainGame->stDMMessage2->setVisible(false);
				mainGame->ebDMName->setVisible(false);
				mainGame->cbDMCategory->setVisible(false);
                SetCategoryDeckNameOnButton(mainGame->btnManageDeck, L"\n");
				break;
			}
			case BUTTON_DM_CANCEL: {
				mainGame->HideElement(mainGame->wDMQuery);
				mainGame->stDMMessage2->setVisible(false);
				mainGame->ebDMName->setVisible(false);
				mainGame->cbDMCategory->setVisible(false);
				break;
			}
			case BUTTON_CLOSE_DECKMANAGER: {
            		mainGame->HideElement(mainGame->wDeckManage);
            		break;
			}
			case BUTTON_SIDE_OK: {
				if(deckManager.current_deck.main.size() != pre_mainc
					|| deckManager.current_deck.extra.size() != pre_extrac
					|| deckManager.current_deck.side.size() != pre_sidec) {
					mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::INFO);
					mainGame->addMessageBox(L"", dataManager.GetSysString(1410));
					break;
				}
				mainGame->ClearCardInfo();
                mainGame->imgChat->setVisible(true);
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
				break;
			}
			case BUTTON_SIDE_RELOAD: {
				load_current_deck(mainGame->cbCategorySelect, mainGame->cbDeckSelect);
				break;
			}
			case BUTTON_MSG_OK: {
				mainGame->HideElement(mainGame->wMessage);
				mainGame->actionSignal.Set();
				break;
			}
			case BUTTON_YES: {
				mainGame->HideElement(mainGame->wQuery);
				if(!mainGame->is_building || mainGame->is_siding)
					break;
				if(prev_operation == BUTTON_CLEAR_DECK) {
					deckManager.current_deck.main.clear();
					deckManager.current_deck.extra.clear();
					deckManager.current_deck.side.clear();
				} else if(prev_operation == BUTTON_DELETE_DECK) {
					int sel = prev_sel;
					mainGame->cbDBDecks->setSelected(sel);
					wchar_t filepath[256];
					get_deck_file(filepath);
					if(deckManager.DeleteDeck(filepath)) {
						mainGame->cbDBDecks->removeItem(sel);
						int count = mainGame->cbDBDecks->getItemCount();
						if(sel >= count)
							sel = count - 1;
						mainGame->cbDBDecks->setSelected(sel);
						if(sel != -1)
							load_current_deck(mainGame->cbDBCategory, mainGame->cbDBDecks);
						mainGame->stACMessage->setText(dataManager.GetSysString(1338));
						mainGame->PopupElement(mainGame->wACMessage, 20);
						prev_deck = sel;
						is_modified = false;
					}
                    SetCategoryDeckNameOnButton(mainGame->btnManageDeck, L"\n");
					prev_sel = -1;
				} else if(prev_operation == BUTTON_LEAVE_GAME) {
					Terminate();
				} else if(prev_operation == COMBOBOX_DBCATEGORY) {
					int catesel = mainGame->cbDBCategory->getSelected();
					ChangeCategory(catesel);
				} else if(prev_operation == COMBOBOX_DBDECKS) {
					int decksel = mainGame->cbDBDecks->getSelected();
					load_current_deck(mainGame->cbDBCategory, mainGame->cbDBDecks);
					prev_deck = decksel;
					is_modified = false;
				} else if(prev_operation == BUTTON_MANAGE_DECK) {
					ShowDeckManage();
				}
				prev_operation = 0;
				break;
			}
			case BUTTON_NO: {
				mainGame->HideElement(mainGame->wQuery);
				if(prev_operation == COMBOBOX_DBCATEGORY) {
					mainGame->cbDBCategory->setSelected(prev_category);
				} else if(prev_operation == COMBOBOX_DBDECKS) {
					mainGame->cbDBDecks->setSelected(prev_deck);
				}
				prev_operation = 0;
				break;
			}
			case BUTTON_MARKS_FILTER: {
				mainGame->PopupElement(mainGame->wLinkMarks);
				break;
			}
			case BUTTON_MARKERS_OK: {
				filter_marks = 0;
				if (mainGame->btnMark[0]->isPressed())
					filter_marks |= 0100;
				if (mainGame->btnMark[1]->isPressed())
					filter_marks |= 0200;
				if (mainGame->btnMark[2]->isPressed())
					filter_marks |= 0400;
				if (mainGame->btnMark[3]->isPressed())
					filter_marks |= 0010;
				if (mainGame->btnMark[4]->isPressed())
					filter_marks |= 0040;
				if (mainGame->btnMark[5]->isPressed())
					filter_marks |= 0001;
				if (mainGame->btnMark[6]->isPressed())
					filter_marks |= 0002;
				if (mainGame->btnMark[7]->isPressed())
					filter_marks |= 0004;
				mainGame->HideElement(mainGame->wLinkMarks);
				mainGame->btnMarksFilter->setPressed(filter_marks > 0);
				InstantSearch();
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_SCROLL_BAR_CHANGED: {
			switch(id) {
			case SCROLL_FILTER: {
				GetHoveredCard();
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_EDITBOX_ENTER: {
			switch(id) {
			case EDITBOX_INPUTS:
			case EDITBOX_KEYWORD: {
				StartFilter();
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_EDITBOX_CHANGED: {
			switch(id) {
			case EDITBOX_KEYWORD: {
				InstantSearch();
				break;
			}
			}
			break;
		}
		case irr::gui::EGET_COMBO_BOX_CHANGED: {
			switch(id) {
			case COMBOBOX_DBCATEGORY: {
				if(havePopupWindow()) {
					mainGame->cbDBCategory->setSelected(prev_category);
					break;
				}
				int catesel = mainGame->cbDBCategory->getSelected();
				if(catesel == 3) {
					catesel = 2;
					mainGame->cbDBCategory->setSelected(2);
					if(prev_category == 2)
						break;
				}
				if(is_modified && !readonly && !mainGame->chkIgnoreDeckChanges->isChecked()) {
					mainGame->gMutex.lock();
					mainGame->SetStaticText(mainGame->stQMessage, 370 * mainGame->xScale, mainGame->guiFont, dataManager.GetSysString(1356));
					mainGame->PopupElement(mainGame->wQuery);
					mainGame->gMutex.unlock();
					prev_operation = id;
					break;
				}
				ChangeCategory(catesel);
				break;
			}
			case COMBOBOX_DBDECKS: {
				if(havePopupWindow()) {
					mainGame->cbDBDecks->setSelected(prev_deck);
					break;
				}
				if(is_modified && !readonly && !mainGame->chkIgnoreDeckChanges->isChecked()) {
					mainGame->gMutex.lock();
					mainGame->SetStaticText(mainGame->stQMessage, 370 * mainGame-> xScale, mainGame->guiFont, dataManager.GetSysString(1356));
					mainGame->PopupElement(mainGame->wQuery);
					mainGame->gMutex.unlock();
					prev_operation = id;
					break;
				}
				int decksel = mainGame->cbDBDecks->getSelected();
				if(decksel >= 0) {
					load_current_deck(mainGame->cbDBCategory, mainGame->cbDBDecks);
				}
				prev_deck = decksel;
				is_modified = false;
				break;
			}
			case COMBOBOX_MAINTYPE: {
				mainGame->cbCardType2->setSelected(0);
				mainGame->cbAttribute->setSelected(0);
				mainGame->cbRace->setSelected(0);
				mainGame->ebAttack->setText(L"");
				mainGame->ebDefense->setText(L"");
				mainGame->ebStar->setText(L"");
				mainGame->ebScale->setText(L"");
				switch(mainGame->cbCardType->getSelected()) {
				case 0: {
					mainGame->cbCardType2->setEnabled(false);
					mainGame->cbCardType2->setSelected(0);
					mainGame->cbRace->setEnabled(false);
					mainGame->cbAttribute->setEnabled(false);
					mainGame->ebAttack->setEnabled(false);
					mainGame->ebDefense->setEnabled(false);
					mainGame->ebStar->setEnabled(false);
					mainGame->ebScale->setEnabled(false);
					break;
				}
				case 1: {
					wchar_t normaltuner[32];
					wchar_t normalpen[32];
					wchar_t syntuner[32];
					mainGame->cbCardType2->setEnabled(true);
					mainGame->cbRace->setEnabled(true);
					mainGame->cbAttribute->setEnabled(true);
					mainGame->ebAttack->setEnabled(true);
					mainGame->ebDefense->setEnabled(true);
					mainGame->ebStar->setEnabled(true);
					mainGame->ebScale->setEnabled(true);
					mainGame->cbCardType2->clear();
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1080), 0);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1054), TYPE_MONSTER + TYPE_NORMAL);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1055), TYPE_MONSTER + TYPE_EFFECT);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1056), TYPE_MONSTER + TYPE_FUSION);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1057), TYPE_MONSTER + TYPE_RITUAL);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1063), TYPE_MONSTER + TYPE_SYNCHRO);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1073), TYPE_MONSTER + TYPE_XYZ);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1074), TYPE_MONSTER + TYPE_PENDULUM);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1076), TYPE_MONSTER + TYPE_LINK);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1075), TYPE_MONSTER + TYPE_SPSUMMON);
					myswprintf(normaltuner, L"%ls|%ls", dataManager.GetSysString(1054), dataManager.GetSysString(1062));
					mainGame->cbCardType2->addItem(normaltuner, TYPE_MONSTER + TYPE_NORMAL + TYPE_TUNER);
					myswprintf(normalpen, L"%ls|%ls", dataManager.GetSysString(1054), dataManager.GetSysString(1074));
					mainGame->cbCardType2->addItem(normalpen, TYPE_MONSTER + TYPE_NORMAL + TYPE_PENDULUM);
					myswprintf(syntuner, L"%ls|%ls", dataManager.GetSysString(1063), dataManager.GetSysString(1062));
					mainGame->cbCardType2->addItem(syntuner, TYPE_MONSTER + TYPE_SYNCHRO + TYPE_TUNER);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1062), TYPE_MONSTER + TYPE_TUNER);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1061), TYPE_MONSTER + TYPE_DUAL);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1060), TYPE_MONSTER + TYPE_UNION);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1059), TYPE_MONSTER + TYPE_SPIRIT);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1071), TYPE_MONSTER + TYPE_FLIP);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1072), TYPE_MONSTER + TYPE_TOON);
					break;
				}
				case 2: {
					mainGame->cbCardType2->setEnabled(true);
					mainGame->cbRace->setEnabled(false);
					mainGame->cbAttribute->setEnabled(false);
					mainGame->ebAttack->setEnabled(false);
					mainGame->ebDefense->setEnabled(false);
					mainGame->ebStar->setEnabled(false);
					mainGame->ebScale->setEnabled(false);
					mainGame->cbCardType2->clear();
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1080), 0);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1054), TYPE_SPELL);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1066), TYPE_SPELL + TYPE_QUICKPLAY);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1067), TYPE_SPELL + TYPE_CONTINUOUS);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1057), TYPE_SPELL + TYPE_RITUAL);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1068), TYPE_SPELL + TYPE_EQUIP);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1069), TYPE_SPELL + TYPE_FIELD);
					break;
				}
				case 3: {
					mainGame->cbCardType2->setEnabled(true);
					mainGame->cbRace->setEnabled(false);
					mainGame->cbAttribute->setEnabled(false);
					mainGame->ebAttack->setEnabled(false);
					mainGame->ebDefense->setEnabled(false);
					mainGame->ebStar->setEnabled(false);
					mainGame->ebScale->setEnabled(false);
					mainGame->cbCardType2->clear();
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1080), 0);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1054), TYPE_TRAP);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1067), TYPE_TRAP + TYPE_CONTINUOUS);
					mainGame->cbCardType2->addItem(dataManager.GetSysString(1070), TYPE_TRAP + TYPE_COUNTER);
					break;
				}
				}
				mainGame->env->setFocus(0);
				InstantSearch();
				break;
			}
			case COMBOBOX_SORTTYPE: {
				SortList();
				mainGame->env->setFocus(0);
				break;
			}
			case COMBOBOX_SECONDTYPE: {
				if (mainGame->cbCardType->getSelected() == 1) {
					if (mainGame->cbCardType2->getSelected() == 8) {
						mainGame->ebDefense->setEnabled(false);
						mainGame->ebDefense->setText(L"");
					} else {
						mainGame->ebDefense->setEnabled(true);
					}
				}
				mainGame->env->setFocus(0);
				InstantSearch();
				break;
			}
			case COMBOBOX_ATTRIBUTE:
			case COMBOBOX_RACE:
			case COMBOBOX_LIMIT:
				mainGame->env->setFocus(0);
				InstantSearch();
				break;
			}
		}
		case irr::gui::EGET_LISTBOX_CHANGED: {
			switch(id) {
			case LISTBOX_CATEGORIES: {
				if(mainGame->wDMQuery->isVisible()) {
					mainGame->lstCategories->setSelected(prev_category);
					break;
				}
				int catesel = mainGame->lstCategories->getSelected();
				if(catesel == 3) {
					catesel = 2;
					mainGame->lstCategories->setSelected(catesel);
					if(prev_category == catesel)
						break;
				}
				RefreshDeckList(true);
				mainGame->lstDecks->setSelected(0);
				mainGame->cbDBCategory->setSelected(catesel);
				ChangeCategory(catesel);
                SetCategoryDeckNameOnButton(mainGame->btnManageDeck, L"\n");
				break;
			}
			case LISTBOX_DECKS: {
				if(mainGame->wDMQuery->isVisible()) {
					mainGame->lstDecks->setSelected(prev_deck);
					break;
				}
				int decksel = mainGame->lstDecks->getSelected();
				mainGame->cbDBDecks->setSelected(decksel);
				if(decksel == -1)
					break;
				wchar_t filepath[256];
				wchar_t catepath[256];
				deckManager.GetCategoryPath(catepath, mainGame->lstCategories->getSelected(), mainGame->lstCategories->getListItem(mainGame->lstCategories->getSelected()), true);
				myswprintf(filepath, L"%ls/%ls.ydk", catepath, mainGame->lstDecks->getListItem(decksel));
				deckManager.LoadCurrentDeck(filepath, showing_pack);
				RefreshPackListScroll();
				prev_deck = decksel;
                SetCategoryDeckNameOnButton(mainGame->btnManageDeck, L"\n");
				break;
			}
			}
			break;
		}
		default: break;
		}
		break;
	}
	case irr::EET_MOUSE_INPUT_EVENT: {
		switch(event.MouseInput.Event) {
		case irr::EMIE_LMOUSE_PRESSED_DOWN: {
			irr::gui::IGUIElement* root = mainGame->env->getRootGUIElement();
			if(root->getElementFromPoint(mouse_pos) != root)
				break;
			if(havePopupWindow())
				break;
			if(hovered_pos == 0 || hovered_seq == -1)
				break;
			click_pos = hovered_pos;
			if(readonly)
				break;
			dragx = event.MouseInput.X;
			dragy = event.MouseInput.Y;
			draging_pointer = dataManager.GetCodePointer(hovered_code);
			if(draging_pointer == dataManager.datas_end())
				break;
			if(hovered_pos == 4) {
				if(!check_limit(draging_pointer))
					break;
			}
			is_starting_dragging = true;
			break;
		}
		case irr::EMIE_LMOUSE_LEFT_UP: {
			is_starting_dragging = false;
			irr::gui::IGUIElement* root = mainGame->env->getRootGUIElement();
			if(!is_draging)
				break;
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::CARD_DROP);
			bool pushed = false;
			if(hovered_pos == 1)
				pushed = push_main(draging_pointer, hovered_seq);
			else if(hovered_pos == 2)
				pushed = push_extra(draging_pointer, hovered_seq + is_lastcard);
			else if(hovered_pos == 3)
				pushed = push_side(draging_pointer, hovered_seq + is_lastcard);
			else if(hovered_pos == 4 && !mainGame->is_siding)
				pushed = true;
			if(!pushed) {
				if(click_pos == 1)
					push_main(draging_pointer);
				else if(click_pos == 2)
					push_extra(draging_pointer);
				else if(click_pos == 3)
					push_side(draging_pointer);
			}
			is_draging = false;
			break;
		}
		case irr::EMIE_LMOUSE_DOUBLE_CLICK: {
			irr::gui::IGUIElement* root = mainGame->env->getRootGUIElement();
			if(!is_draging && !mainGame->is_siding && root->getElementFromPoint(mouse_pos) == root && hovered_code) {
				break;
			}
			break;
		}
		case irr::EMIE_RMOUSE_LEFT_UP: {
			if(mainGame->is_siding) {
				if(is_draging)
					break;
				if(hovered_pos == 0 || hovered_seq == -1)
					break;
				auto pointer = dataManager.GetCodePointer(hovered_code);
				if(pointer == dataManager.datas_end())
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::CARD_DROP);
				if(hovered_pos == 1) {
					if(push_side(pointer))
						pop_main(hovered_seq);
				} else if(hovered_pos == 2) {
					if(push_side(pointer))
						pop_extra(hovered_seq);
				} else {
					if(push_extra(pointer) || push_main(pointer))
						pop_side(hovered_seq);
				}
				break;
			}
			if(havePopupWindow())
				break;
			if(!is_draging) {
				if(hovered_pos == 0 || hovered_seq == -1)
					break;
				if(readonly)
					break;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::CARD_DROP);
				if(hovered_pos == 1) {
					pop_main(hovered_seq);
				} else if(hovered_pos == 2) {
					pop_extra(hovered_seq);
				} else if(hovered_pos == 3) {
					pop_side(hovered_seq);
				} else {
					auto pointer = dataManager.GetCodePointer(hovered_code);
					if(pointer == dataManager.datas_end())
						break;
					if(!check_limit(pointer))
						break;
					if(!push_extra(pointer) && !push_main(pointer))
						push_side(pointer);
				}
			} else {
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::CARD_PICK);
				if(click_pos == 1) {
					push_side(draging_pointer);
				} else if(click_pos == 2) {
					push_side(draging_pointer);
				} else if(click_pos == 3) {
					if(!push_extra(draging_pointer))
						push_main(draging_pointer);
				} else {
					push_side(draging_pointer);
				}
				is_draging = false;
			}
			break;
		}
		case irr::EMIE_MMOUSE_LEFT_UP: {
			if (mainGame->is_siding)
				break;
			if (havePopupWindow())
				break;
			if (hovered_pos == 0 || hovered_seq == -1)
				break;
			if (readonly)
				break;
			if (is_draging)
				break;
			auto pointer = dataManager.GetCodePointer(hovered_code);
			if (pointer == dataManager.datas_end())
				break;
			if(!check_limit(pointer))
				break;
			mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::CARD_PICK);
			if (hovered_pos == 1) {
				if(!push_main(pointer))
					push_side(pointer);
			} else if (hovered_pos == 2) {
				if(!push_extra(pointer))
					push_side(pointer);
			} else if (hovered_pos == 3) {
				if(!push_side(pointer) && !push_extra(pointer))
					push_main(pointer);
			} else {
				if(!push_extra(pointer) && !push_main(pointer))
					push_side(pointer);
			}
			break;
		}
		case irr::EMIE_MOUSE_MOVED: {
			if(is_starting_dragging) {
				is_draging = true;
				mainGame->soundManager->PlaySoundEffect(SoundManager::SFX::CARD_PICK);
				if(hovered_pos == 1)
					pop_main(hovered_seq);
				else if(hovered_pos == 2)
					pop_extra(hovered_seq);
				else if(hovered_pos == 3)
					pop_side(hovered_seq);
				is_starting_dragging = false;
			}
			mouse_pos.set(event.MouseInput.X, event.MouseInput.Y);
			GetHoveredCard();
			break;
		}
		case irr::EMIE_MOUSE_WHEEL: {
			irr::gui::IGUIElement* root = mainGame->env->getRootGUIElement();
			if(!mainGame->scrFilter->isVisible())
				break;
			if(mainGame->env->hasFocus(mainGame->scrFilter))
				break;
			if(root->getElementFromPoint(mouse_pos) != root)
				break;
			if(event.MouseInput.Wheel < 0) {
				if(mainGame->scrFilter->getPos() < mainGame->scrFilter->getMax())
					mainGame->scrFilter->setPos(mainGame->scrFilter->getPos() + 1);
			} else {
				if(mainGame->scrFilter->getPos() > 0)
					mainGame->scrFilter->setPos(mainGame->scrFilter->getPos() - 1);
			}
			GetHoveredCard();
			break;
		}
		default: break;
		}
		break;
	}
	case irr::EET_KEY_INPUT_EVENT: {
		break;
	}
	default: break;
	}
	return false;
}
void DeckBuilder::GetHoveredCard() {
	int pre_code = hovered_code;
	hovered_pos = 0;
	hovered_code = 0;
	irr::gui::IGUIElement* root = mainGame->env->getRootGUIElement();
	if(root->getElementFromPoint(mouse_pos) != root)
		return;
	int x = mouse_pos.X;
	int y = mouse_pos.Y;
	is_lastcard = 0;
	if(x >= 314 * mainGame->xScale && x <= 794 * mainGame->xScale) {
		if(showing_pack) {
			if((x <= 772 * mainGame->xScale || !mainGame->scrPackCards->isVisible()) && y >= 164 * mainGame->yScale && y <= 624 * mainGame->yScale) {
				int mainsize = deckManager.current_deck.main.size();
				int lx = 10;
				int dy = 68 * mainGame->yScale;
				if(mainsize > 10 * 7)
					lx = 11;
				if(mainsize > 11 * 7)
					lx = 12;
				if(mainsize > 60)
					dy = 66 * mainGame->yScale;
				int px;
				int py = (y - 164 * mainGame->yScale) / dy;
				hovered_pos = 1;
				if(x >= 750 * mainGame->xScale)
					px = lx - 1;
				else
					px = (x - 314 * mainGame->xScale) * (lx - 1) / ((mainGame->scrPackCards->isVisible() ? 414.0f : 436.0f) * mainGame->xScale);
				hovered_seq = py * lx + px + mainGame->scrPackCards->getPos() * lx;
				if(hovered_seq >= mainsize) {
					hovered_seq = -1;
					hovered_code = 0;
				} else {
					hovered_code = deckManager.current_deck.main[hovered_seq]->first;
				}
			}
		} else if(y >= 164 * mainGame->yScale && y <= 435 * mainGame->yScale) {
			int lx = 10, px, py = (y - 164 * mainGame->yScale) / (68 * mainGame->yScale);
			hovered_pos = 1;
			if(deckManager.current_deck.main.size() > DECK_MIN_SIZE)
				lx = (deckManager.current_deck.main.size() - 41) / 4 + 11;
			if(x >= 750 * mainGame->xScale)
				px = lx - 1;
			else
				px = (x - 314 * mainGame->xScale) * (lx - 1) / (436 * mainGame->xScale);
			hovered_seq = py * lx + px;
			if(hovered_seq >= (int)deckManager.current_deck.main.size()) {
				hovered_seq = -1;
				hovered_code = 0;
			} else {
				hovered_code = deckManager.current_deck.main[hovered_seq]->first;
			}
		} else if(y >= 466 * mainGame->yScale && y <= 530 * mainGame->yScale) {
			int lx = deckManager.current_deck.extra.size();
			hovered_pos = 2;
			if(lx < 10)
				lx = 10;
			if(x >= 750 * mainGame->xScale)
				hovered_seq = lx - 1;
			else
				hovered_seq = (x - 314 * mainGame->xScale) * (lx - 1) / (436 * mainGame->xScale);
			if(hovered_seq >= (int)deckManager.current_deck.extra.size()) {
				hovered_seq = -1;
				hovered_code = 0;
			} else {
				hovered_code = deckManager.current_deck.extra[hovered_seq]->first;
				if(x >= 772 * mainGame->xScale)
					is_lastcard = 1;
			}
		} else if (y >= 564 * mainGame->yScale && y <= 628 * mainGame->yScale) {
			int lx = deckManager.current_deck.side.size();
			hovered_pos = 3;
			if(lx < 10)
				lx = 10;
			if(x >= 750 * mainGame->xScale)
				hovered_seq = lx - 1;
			else
				hovered_seq = (x - 314 * mainGame->xScale) * (lx - 1) / (436 * mainGame->xScale);
			if(hovered_seq >= (int)deckManager.current_deck.side.size()) {
				hovered_seq = -1;
				hovered_code = 0;
			} else {
				hovered_code = deckManager.current_deck.side[hovered_seq]->first;
				if(x >= 772 * mainGame->xScale)
					is_lastcard = 1;
			}
		}
	} else if(x >= 805 * mainGame->xScale && x <= 995 * mainGame->xScale && y >= 165 * mainGame->yScale && y <= 626 * mainGame->yScale) {
		hovered_pos = 4;
        hovered_seq = (y - 165 * mainGame->yScale) / (66 * mainGame->yScale);
		int current_pos = mainGame->scrFilter->getPos() + hovered_seq;
		if(current_pos >= (int)results.size()) {
			hovered_seq = -1;
			hovered_code = 0;
		} else {
			hovered_code = results[current_pos]->first;
		}
	}
	if(is_draging) {
		dragx = x;
		dragy = y;
	}
	if(!is_draging && pre_code != hovered_code) {
		if(hovered_code)
			mainGame->ShowCardInfo(hovered_code);
		if(pre_code)
			imageManager.RemoveTexture(pre_code);
	}
}
void DeckBuilder::StartFilter() {
	filter_type = mainGame->cbCardType->getSelected();
	filter_type2 = mainGame->cbCardType2->getItemData(mainGame->cbCardType2->getSelected());
	filter_lm = mainGame->cbLimit->getSelected();
	if(filter_type == 1) {
		filter_attrib = mainGame->cbAttribute->getItemData(mainGame->cbAttribute->getSelected());
		filter_race = mainGame->cbRace->getItemData(mainGame->cbRace->getSelected());
		filter_atk = parse_filter(mainGame->ebAttack->getText(), &filter_atktype);
		filter_def = parse_filter(mainGame->ebDefense->getText(), &filter_deftype);
		filter_lv = parse_filter(mainGame->ebStar->getText(), &filter_lvtype);
		filter_scl = parse_filter(mainGame->ebScale->getText(), &filter_scltype);
	}
	FilterCards();
}
void DeckBuilder::FilterCards() {
	results.clear();
	struct element_t {
		std::wstring keyword;
		std::vector<unsigned int> setcodes;
		enum class type_t {
			all,
			name,
			setcode
		} type{ type_t::all };
		bool exclude{ false };
	};
	const wchar_t* pstr = mainGame->ebCardName->getText();
	int trycode = BufferIO::GetVal(pstr);
	std::wstring str{ pstr };
	std::vector<element_t> query_elements;
	if(mainGame->gameConf.search_multiple_keywords) {
		const wchar_t separator = mainGame->gameConf.search_multiple_keywords == 1 ? L' ' : L'+';
		const wchar_t minussign = L'-';
		const wchar_t quotation = L'\"';
		size_t element_start = 0;
		for(;;) {
			element_start = str.find_first_not_of(separator, element_start);
			if(element_start == std::wstring::npos)
				break;
			element_t element;
			if(str[element_start] == minussign) {
				element.exclude = true;
				element_start++;
			}
			if(element_start >= str.size())
				break;
			if(str[element_start] == L'$') {
				element.type = element_t::type_t::name;
				element_start++;
			} else if(str[element_start] == L'@') {
				element.type = element_t::type_t::setcode;
				element_start++;
			}
			if(element_start >= str.size())
				break;
			wchar_t delimiter = separator;
			if(str[element_start] == quotation) {
				delimiter = quotation;
				element_start++;
			}
			size_t element_end = str.find_first_of(delimiter, element_start);
			if(element_end != std::wstring::npos) {
				size_t length = element_end - element_start;
				element.keyword = str.substr(element_start, length);
			} else
				element.keyword = str.substr(element_start);
			element.setcodes = dataManager.GetSetCodes(element.keyword);
			query_elements.push_back(element);
			if(element_end == std::wstring::npos)
				break;
			element_start = element_end + 1;
		}
	} else {
		element_t element;
		size_t element_start = 0;
		if(str[element_start] == L'$') {
			element.type = element_t::type_t::name;
			element_start++;
		} else if(str[element_start] == L'@') {
			element.type = element_t::type_t::setcode;
			element_start++;
		}
		if(element_start < str.size()) {
			element.keyword = str.substr(element_start);
			element.setcodes = dataManager.GetSetCodes(element.keyword);
			query_elements.push_back(element);
		}
	}
	for(code_pointer ptr = dataManager.datas_begin(); ptr != dataManager.datas_end(); ++ptr) {
		const CardDataC& data = ptr->second;
		auto strpointer = dataManager.GetStringPointer(ptr->first);
		if (strpointer == dataManager.strings_end())
			continue;
		const CardString& strings = strpointer->second;
		if(data.type & TYPE_TOKEN)
			continue;
		switch(filter_type) {
		case 1: {
			if(!(data.type & TYPE_MONSTER) || (data.type & filter_type2) != filter_type2)
				continue;
			if(filter_race && data.race != filter_race)
				continue;
			if(filter_attrib && data.attribute != filter_attrib)
				continue;
			if(filter_atktype) {
				if((filter_atktype == 1 && data.attack != filter_atk) || (filter_atktype == 2 && data.attack < filter_atk)
				        || (filter_atktype == 3 && data.attack <= filter_atk) || (filter_atktype == 4 && (data.attack > filter_atk || data.attack < 0))
				        || (filter_atktype == 5 && (data.attack >= filter_atk || data.attack < 0)) || (filter_atktype == 6 && data.attack != -2))
					continue;
			}
			if(filter_deftype) {
				if((filter_deftype == 1 && data.defense != filter_def) || (filter_deftype == 2 && data.defense < filter_def)
				        || (filter_deftype == 3 && data.defense <= filter_def) || (filter_deftype == 4 && (data.defense > filter_def || data.defense < 0))
				        || (filter_deftype == 5 && (data.defense >= filter_def || data.defense < 0)) || (filter_deftype == 6 && data.defense != -2)
				        || (data.type & TYPE_LINK))
					continue;
			}
			if(filter_lvtype) {
				if((filter_lvtype == 1 && data.level != filter_lv) || (filter_lvtype == 2 && data.level < filter_lv)
				        || (filter_lvtype == 3 && data.level <= filter_lv) || (filter_lvtype == 4 && data.level > filter_lv)
				        || (filter_lvtype == 5 && data.level >= filter_lv) || filter_lvtype == 6)
					continue;
			}
			if(filter_scltype) {
				if((filter_scltype == 1 && data.lscale != filter_scl) || (filter_scltype == 2 && data.lscale < filter_scl)
				        || (filter_scltype == 3 && data.lscale <= filter_scl) || (filter_scltype == 4 && (data.lscale > filter_scl))
				        || (filter_scltype == 5 && (data.lscale >= filter_scl)) || filter_scltype == 6
				        || !(data.type & TYPE_PENDULUM))
					continue;
			}
			break;
		}
		case 2: {
			if(!(data.type & TYPE_SPELL))
				continue;
			if(filter_type2 && data.type != filter_type2)
				continue;
			break;
		}
		case 3: {
			if(!(data.type & TYPE_TRAP))
				continue;
			if(filter_type2 && data.type != filter_type2)
				continue;
			break;
		}
		}
		if(filter_effect && !(data.category & filter_effect))
			continue;
		if(filter_marks && (data.link_marker & filter_marks) != filter_marks)
			continue;
		if(filter_lm) {
			if(filter_lm <= 3 && (!filterList->content.count(ptr->first) || filterList->content.at(ptr->first) != filter_lm - 1))
				continue;
			if(filter_lm == 4 && !(data.ot & AVAIL_OCG))
				continue;
			if(filter_lm == 5 && !(data.ot & AVAIL_TCG))
				continue;
			if(filter_lm == 6 && !(data.ot & AVAIL_SC))
				continue;
			if(filter_lm == 7 && !(data.ot & AVAIL_CUSTOM))
				continue;
			if(filter_lm == 8 && ((data.ot & AVAIL_OCGTCG) != AVAIL_OCGTCG))
				continue;
		}
		bool is_target = true;
		for (auto elements_iterator = query_elements.begin(); elements_iterator != query_elements.end(); ++elements_iterator) {
			bool match = false;
			if (elements_iterator->type == element_t::type_t::name) {
				match = CardNameContains(strings.name.c_str(), elements_iterator->keyword.c_str());
			} else if (elements_iterator->type == element_t::type_t::setcode) {
				match = data.is_setcodes(elements_iterator->setcodes);
			} else if (trycode && (data.code == trycode || data.alias == trycode && is_alternative(data.code, data.alias))){
				match = true;
			} else {
				match = CardNameContains(strings.name.c_str(), elements_iterator->keyword.c_str())
					|| strings.text.find(elements_iterator->keyword) != std::wstring::npos
					|| data.is_setcodes(elements_iterator->setcodes);
			}
			if(elements_iterator->exclude)
				match = !match;
			if(!match) {
				is_target = false;
				break;
			}
		}
		if(is_target)
			results.push_back(ptr);
		else
			continue;
	}
	myswprintf(result_string, L"%d", results.size());
	if(results.size() > 7) {
		mainGame->scrFilter->setVisible(true);
		mainGame->scrFilter->setMax(results.size() - 7);
		mainGame->scrFilter->setPos(0);
	} else {
		mainGame->scrFilter->setVisible(false);
		mainGame->scrFilter->setPos(0);
	}
	SortList();
}
void DeckBuilder::InstantSearch() {
	if(mainGame->gameConf.auto_search_limit >= 0 && ((int)std::wcslen(mainGame->ebCardName->getText()) >= mainGame->gameConf.auto_search_limit))
		StartFilter();
}
void DeckBuilder::ClearSearch() {
	mainGame->cbCardType->setSelected(0);
	mainGame->cbCardType2->setSelected(0);
	mainGame->cbCardType2->setEnabled(false);
	mainGame->cbRace->setEnabled(false);
	mainGame->cbAttribute->setEnabled(false);
	mainGame->ebAttack->setEnabled(false);
	mainGame->ebDefense->setEnabled(false);
	mainGame->ebStar->setEnabled(false);
	mainGame->ebScale->setEnabled(false);
	mainGame->ebCardName->setText(L"");
	mainGame->scrFilter->setVisible(false);
	mainGame->scrFilter->setPos(0);
	ClearFilter();
	results.clear();
	myswprintf(result_string, L"%d", 0);
}
void DeckBuilder::ClearFilter() {
	mainGame->cbAttribute->setSelected(0);
	mainGame->cbRace->setSelected(0);
	mainGame->cbLimit->setSelected(0);
	mainGame->ebAttack->setText(L"");
	mainGame->ebDefense->setText(L"");
	mainGame->ebStar->setText(L"");
	mainGame->ebScale->setText(L"");
	filter_effect = 0;
	for(int i = 0; i < 32; ++i)
		mainGame->chkCategory[i]->setChecked(false);
	filter_marks = 0;
	for(int i = 0; i < 8; i++)
		mainGame->btnMark[i]->setPressed(false);
	mainGame->btnEffectFilter->setPressed(false);
	mainGame->btnMarksFilter->setPressed(false);
}
void DeckBuilder::SortList() {
	auto left = results.begin();
	const wchar_t* pstr = mainGame->ebCardName->getText();
	for(auto it = results.begin(); it != results.end(); ++it) {
		if(std::wcscmp(pstr, dataManager.GetName((*it)->first)) == 0) {
			std::iter_swap(left, it);
			++left;
		}
	}
	switch(mainGame->cbSortType->getSelected()) {
	case 0:
		std::sort(left, results.end(), DataManager::deck_sort_lv);
		break;
	case 1:
		std::sort(left, results.end(), DataManager::deck_sort_atk);
		break;
	case 2:
		std::sort(left, results.end(), DataManager::deck_sort_def);
		break;
	case 3:
		std::sort(left, results.end(), DataManager::deck_sort_name);
		break;
	}
}

void DeckBuilder::RefreshDeckList(bool showPack) {
	irr::gui::IGUIListBox* lstCategories = mainGame->lstCategories;
	irr::gui::IGUIListBox* lstDecks = mainGame->lstDecks;
	wchar_t catepath[256];
	deckManager.GetCategoryPath(catepath, lstCategories->getSelected(), lstCategories->getListItem(lstCategories->getSelected()), showPack);
	lstDecks->clear();
	mainGame->RefreshDeck(catepath, [lstDecks](const wchar_t* item) { lstDecks->addItem(item); });
}
void DeckBuilder::RefreshReadonly(int catesel) {
	bool hasDeck = mainGame->cbDBDecks->getItemCount() != 0;
	readonly = catesel < 2;
	showing_pack = catesel == 0;
	mainGame->btnSaveDeck->setEnabled(!readonly);
	mainGame->btnSaveDeckAs->setEnabled(!readonly);
	mainGame->btnClearDeck->setEnabled(!readonly);
	mainGame->btnShuffleDeck->setEnabled(!readonly);
	mainGame->btnSortDeck->setEnabled(!readonly);
	mainGame->btnDeleteDeck->setEnabled(hasDeck && !readonly);
	mainGame->btnRenameCategory->setEnabled(catesel > 3);
	mainGame->btnDeleteCategory->setEnabled(catesel > 3);
	mainGame->btnNewDeck->setEnabled(!readonly);
	mainGame->btnRenameDeck->setEnabled(hasDeck && !readonly);
	mainGame->btnDMDeleteDeck->setEnabled(hasDeck && !readonly);
	mainGame->btnMoveDeck->setEnabled(hasDeck && !readonly);
	mainGame->btnCopyDeck->setEnabled(hasDeck);
}
void DeckBuilder::RefreshPackListScroll() {
	if(showing_pack) {
		mainGame->scrPackCards->setPos(0);
		int mainsize = deckManager.current_deck.main.size();
		if(mainsize <= 7 * 12) {
			mainGame->scrPackCards->setVisible(false);
		} else {
			mainGame->scrPackCards->setVisible(true);
			mainGame->scrPackCards->setMax((int)ceil(((float)mainsize - 7 * 12) / 12.0f));
		}
	} else {
		mainGame->scrPackCards->setVisible(false);
		mainGame->scrPackCards->setPos(0);
	}
}
void DeckBuilder::ChangeCategory(int catesel) {
	mainGame->RefreshDeck(mainGame->cbDBCategory, mainGame->cbDBDecks);
	mainGame->cbDBDecks->setSelected(0);
	RefreshReadonly(catesel);
	load_current_deck(mainGame->cbDBCategory, mainGame->cbDBDecks);
	is_modified = false;
	prev_category = catesel;
	prev_deck = 0;
}
void DeckBuilder::ShowDeckManage() {
	mainGame->RefreshCategoryDeck(mainGame->cbDBCategory, mainGame->cbDBDecks, false);
	mainGame->cbDBCategory->setSelected(prev_category);
	mainGame->RefreshDeck(mainGame->cbDBCategory, mainGame->cbDBDecks);
	mainGame->cbDBDecks->setSelected(prev_deck);
	irr::gui::IGUIListBox* lstCategories = mainGame->lstCategories;
	lstCategories->clear();
	lstCategories->addItem(dataManager.GetSysString(1450));
	lstCategories->addItem(dataManager.GetSysString(1451));
	lstCategories->addItem(dataManager.GetSysString(1452));
	lstCategories->addItem(dataManager.GetSysString(1453));
	FileSystem::TraversalDir(L"./deck", [lstCategories](const wchar_t* name, bool isdir) {
		if(isdir) {
			lstCategories->addItem(name);
		}
	});
	lstCategories->setSelected(prev_category);
	RefreshDeckList(true);
	RefreshReadonly(prev_category);
	mainGame->lstDecks->setSelected(prev_deck);
	mainGame->PopupElement(mainGame->wDeckManage);
}
static inline wchar_t NormalizeChar(wchar_t c) {
	/*
	// Convert all symbols and punctuations to space.
	if (c != 0 && c < 128 && !isalnum(c)) {
		return ' ';
	}
	*/
	// Convert latin chararacters to uppercase to ignore case.
	if (c < 128 && isalpha(c)) {
		return toupper(c);
	}
	// Remove some accentued characters that are not supported by the editbox.
	if (c >= 232 && c <= 235) {
		return 'E';
	}
	if (c >= 238 && c <= 239) {
		return 'I';
	}
	return c;
}
bool DeckBuilder::CardNameContains(const wchar_t* haystack, const wchar_t* needle) {
	if(!needle[0]) {
		return true;
	}
	if(!haystack) {
		return false;
	}
	int i = 0;
	int j = 0;
	while(haystack[i]) {
		wchar_t ca = NormalizeChar(haystack[i]);
		wchar_t cb = NormalizeChar(needle[j]);
		if(ca == cb) {
			j++;
			if(!needle[j]) {
				return true;
			}
		} else {
			i -= j;
			j = 0;
		}
		i++;
	}
	return false;
}
bool DeckBuilder::push_main(code_pointer pointer, int seq) {
	if(pointer->second.type & (TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK))
		return false;
	auto& container = deckManager.current_deck.main;
	int maxc = mainGame->is_siding ? DECK_MAX_SIZE + 5 : DECK_MAX_SIZE;
	if((int)container.size() >= maxc)
		return false;
	if(seq >= 0 && seq < (int)container.size())
		container.insert(container.begin() + seq, pointer);
	else
		container.push_back(pointer);
	is_modified = true;
	GetHoveredCard();
	return true;
}
bool DeckBuilder::push_extra(code_pointer pointer, int seq) {
	if(!(pointer->second.type & (TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK)))
		return false;
	auto& container = deckManager.current_deck.extra;
	int maxc = mainGame->is_siding ? EXTRA_MAX_SIZE + 5 : EXTRA_MAX_SIZE;
	if((int)container.size() >= maxc)
		return false;
	if(seq >= 0 && seq < (int)container.size())
		container.insert(container.begin() + seq, pointer);
	else
		container.push_back(pointer);
	is_modified = true;
	GetHoveredCard();
	return true;
}
bool DeckBuilder::push_side(code_pointer pointer, int seq) {
	auto& container = deckManager.current_deck.side;
	int maxc = mainGame->is_siding ? SIDE_MAX_SIZE + 5 : SIDE_MAX_SIZE;
	if((int)container.size() >= maxc)
		return false;
	if(seq >= 0 && seq < (int)container.size())
		container.insert(container.begin() + seq, pointer);
	else
		container.push_back(pointer);
	is_modified = true;
	GetHoveredCard();
	return true;
}
void DeckBuilder::pop_main(int seq) {
	auto& container = deckManager.current_deck.main;
	container.erase(container.begin() + seq);
	is_modified = true;
	GetHoveredCard();
}
void DeckBuilder::pop_extra(int seq) {
	auto& container = deckManager.current_deck.extra;
	container.erase(container.begin() + seq);
	is_modified = true;
	GetHoveredCard();
}
void DeckBuilder::pop_side(int seq) {
	auto& container = deckManager.current_deck.side;
	container.erase(container.begin() + seq);
	is_modified = true;
	GetHoveredCard();
}
bool DeckBuilder::check_limit(code_pointer pointer) {
	auto limitcode = pointer->second.alias ? pointer->second.alias : pointer->first;
	int limit = 3;
	auto flit = filterList->content.find(limitcode);
	if(flit != filterList->content.end())
		limit = flit->second;
	for(auto it = deckManager.current_deck.main.begin(); it != deckManager.current_deck.main.end(); ++it) {
		if((*it)->first == limitcode || (*it)->second.alias == limitcode)
			limit--;
	}
	for(auto it = deckManager.current_deck.extra.begin(); it != deckManager.current_deck.extra.end(); ++it) {
		if((*it)->first == limitcode || (*it)->second.alias == limitcode)
			limit--;
	}
	for(auto it = deckManager.current_deck.side.begin(); it != deckManager.current_deck.side.end(); ++it) {
		if((*it)->first == limitcode || (*it)->second.alias == limitcode)
			limit--;
	}
	return limit > 0;
}
}

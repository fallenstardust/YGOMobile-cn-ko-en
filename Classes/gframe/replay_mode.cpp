#include "replay_mode.h"
#include "duelclient.h"
#include "game.h"
#include "data_manager.h"
#include <random>
#include <thread>

namespace ygo {

intptr_t ReplayMode::pduel = 0;
Replay ReplayMode::cur_replay;
bool ReplayMode::is_continuing = true;
bool ReplayMode::is_closing = false;
bool ReplayMode::is_pausing = false;
bool ReplayMode::is_paused = false;
bool ReplayMode::is_swaping = false;
bool ReplayMode::is_restarting = false;
bool ReplayMode::exit_pending = false;
int ReplayMode::skip_turn = 0;
int ReplayMode::current_step = 0;
int ReplayMode::skip_step = 0;

bool ReplayMode::StartReplay(int skipturn) {
	skip_turn = skipturn;
	if(skip_turn < 0)
		skip_turn = 0;
	std::thread(ReplayThread).detach();
	return true;
}
void ReplayMode::StopReplay(bool is_exiting) {
	is_pausing = false;
	is_continuing = false;
	is_closing = is_exiting;
	exit_pending = true;
	mainGame->actionSignal.Set();
}
void ReplayMode::SwapField() {
	if(is_paused)
		mainGame->dField.ReplaySwap();
	else
		is_swaping = true;
}
void ReplayMode::Pause(bool is_pause, bool is_step) {
	if(is_pause)
		is_pausing = true;
	else {
		if(!is_step)
			is_pausing = false;
		mainGame->actionSignal.Set();
	}
}
bool ReplayMode::ReadReplayResponse() {
	unsigned char resp[SIZE_RETURN_VALUE];
	bool result = cur_replay.ReadNextResponse(resp);
	if(result)
		set_responseb(pduel, resp);
	return result;
}
int ReplayMode::ReplayThread() {
	const auto& rh = cur_replay.pheader.base;
	mainGame->dInfo.Clear();
	mainGame->dInfo.isFirst = true;
	mainGame->dInfo.isTag = !!(rh.flag & REPLAY_TAG);
	mainGame->dInfo.isSingleMode = !!(rh.flag & REPLAY_SINGLE_MODE);
	mainGame->dInfo.tag_player[0] = false;
	mainGame->dInfo.tag_player[1] = false;
	set_script_reader(DataManager::ScriptReaderEx);
	set_card_reader(DataManager::CardReader);
	set_message_handler(ReplayMode::MessageHandler);
	if(!StartDuel()) {
		EndDuel();
		return 0;
	}
	mainGame->dInfo.isStarted = true;
	mainGame->dInfo.isFinished = false;
	mainGame->dInfo.isReplay = true;
	mainGame->dInfo.isReplaySkiping = (skip_turn > 0);
	std::vector<unsigned char> engineBuffer;
	engineBuffer.resize(SIZE_MESSAGE_BUFFER);
	is_continuing = true;
	skip_step = 0;
	if(mainGame->dInfo.isSingleMode) {
		int len = get_message(pduel, engineBuffer.data());
		if (len > 0)
			is_continuing = ReplayAnalyze(engineBuffer.data(), len);
	} else {
		ReplayRefreshDeck(0);
		ReplayRefreshDeck(1);
		ReplayRefreshExtra(0);
		ReplayRefreshExtra(1);
	}
	exit_pending = false;
	current_step = 0;
	if(mainGame->dInfo.isReplaySkiping)
		mainGame->gMutex.lock();
	while (is_continuing && !exit_pending) {
		unsigned int result = process(pduel);
		int len = result & PROCESSOR_BUFFER_LEN;
		if (len > 0) {
			if (len > (int)engineBuffer.size())
				engineBuffer.resize(len);
			get_message(pduel, engineBuffer.data());
			is_continuing = ReplayAnalyze(engineBuffer.data(), len);
			if(is_restarting) {
				mainGame->gMutex.lock();
				is_restarting = false;
				mainGame->dInfo.isReplaySkiping = true;
				Restart(false);
				int step = current_step - 1;
				if(step < 0)
					step = 0;
				if(mainGame->dInfo.isSingleMode) {
					is_continuing = true;
					skip_step = 0;
					int len = get_message(pduel, engineBuffer.data());
					if (len > 0) {
						is_continuing = ReplayAnalyze(engineBuffer.data(), len);
					}
				} else {
					ReplayRefreshDeck(0);
					ReplayRefreshDeck(1);
					ReplayRefreshExtra(0);
					ReplayRefreshExtra(1);
				}
				if(step == 0) {
					Pause(true, false);
					mainGame->dInfo.isStarted = true;
					mainGame->dInfo.isFinished = false;
					mainGame->dInfo.isReplaySkiping = false;
					mainGame->dField.RefreshAllCards();
					mainGame->gMutex.unlock();
				}
				skip_step = step;
				current_step = 0;
			}
		}
	}
	if(mainGame->dInfo.isReplaySkiping) {
		mainGame->dInfo.isReplaySkiping = false;
		mainGame->dField.RefreshAllCards();
		mainGame->gMutex.unlock();
	}
	EndDuel();
	pduel = 0;
	is_continuing = true;
	is_closing = false;
	is_pausing = false;
	is_paused = false;
	is_swaping = false;
	is_restarting = false;
	exit_pending = false;
	skip_turn = 0;
	current_step = 0;
	skip_step = 0;
	return 0;
}
bool ReplayMode::StartDuel() {
	const auto& rh = cur_replay.pheader.base;
	cur_replay.SkipInfo();
	if(rh.flag & REPLAY_TAG) {
		BufferIO::CopyWideString(cur_replay.players[0].c_str(), mainGame->dInfo.hostname);
		BufferIO::CopyWideString(cur_replay.players[1].c_str(), mainGame->dInfo.hostname_tag);
		BufferIO::CopyWideString(cur_replay.players[2].c_str(), mainGame->dInfo.clientname_tag);
		BufferIO::CopyWideString(cur_replay.players[3].c_str(), mainGame->dInfo.clientname);
	} else {
		BufferIO::CopyWideString(cur_replay.players[0].c_str(), mainGame->dInfo.hostname);
		BufferIO::CopyWideString(cur_replay.players[1].c_str(), mainGame->dInfo.clientname);
	}
	if(rh.id == REPLAY_ID_YRP1) {
		std::mt19937 rnd(rh.seed);
		pduel = create_duel(rnd());
	} else {
		pduel = create_duel_v2(cur_replay.pheader.seed_sequence);
	}
	mainGame->dInfo.duel_rule = cur_replay.params.duel_flag >> 16;
	set_player_info(pduel, 0, cur_replay.params.start_lp, cur_replay.params.start_hand, cur_replay.params.draw_count);
	set_player_info(pduel, 1, cur_replay.params.start_lp, cur_replay.params.start_hand, cur_replay.params.draw_count);
	mainGame->dInfo.lp[0] = cur_replay.params.start_lp;
	mainGame->dInfo.lp[1] = cur_replay.params.start_lp;
	mainGame->dInfo.start_lp = cur_replay.params.start_lp;
	myswprintf(mainGame->dInfo.strLP[0], L"%d", mainGame->dInfo.lp[0]);
	myswprintf(mainGame->dInfo.strLP[1], L"%d", mainGame->dInfo.lp[1]);
	mainGame->dInfo.turn = 0;
	if(!(rh.flag & REPLAY_SINGLE_MODE)) {
		if(!(rh.flag & REPLAY_TAG)) {
			for (int i = 0; i < 2; ++i) {
				for (const auto& code : cur_replay.decks[i].main)
					new_card(pduel, code, i, i, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
				for (const auto& code : cur_replay.decks[i].extra)
					new_card(pduel, code, i, i, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
				mainGame->dField.Initial(mainGame->LocalPlayer(i), cur_replay.decks[i].main.size(), cur_replay.decks[i].extra.size());
			}
		} else {
			for (const auto& code : cur_replay.decks[0].main)
				new_card(pduel, code, 0, 0, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
			for (const auto& code : cur_replay.decks[0].extra)
				new_card(pduel, code, 0, 0, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
			mainGame->dField.Initial(mainGame->LocalPlayer(0), cur_replay.decks[0].main.size(), cur_replay.decks[0].extra.size());
			for (const auto& code : cur_replay.decks[1].main)
				new_tag_card(pduel, code, 0, LOCATION_DECK);
			for (const auto& code : cur_replay.decks[1].extra)
				new_tag_card(pduel, code, 0, LOCATION_EXTRA);
			for (const auto& code : cur_replay.decks[2].main)
				new_card(pduel, code, 1, 1, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
			for (const auto& code : cur_replay.decks[2].extra)
				new_card(pduel, code, 1, 1, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
			mainGame->dField.Initial(mainGame->LocalPlayer(1), cur_replay.decks[2].main.size(), cur_replay.decks[2].extra.size());
			for (const auto& code : cur_replay.decks[3].main)
				new_tag_card(pduel, code, 1, LOCATION_DECK);
			for (const auto& code : cur_replay.decks[3].extra)
				new_tag_card(pduel, code, 1, LOCATION_EXTRA);
		}
	} else {
		char filename[256]{};
		std::snprintf(filename, sizeof filename, "./single/%s", cur_replay.script_name.c_str());
		if(!preload_script(pduel, filename)) {
			return false;
		}
	}
	start_duel(pduel, cur_replay.params.duel_flag);
	return true;
}
void ReplayMode::EndDuel() {
	end_duel(pduel);
	if(!is_closing) {
		mainGame->actionSignal.Reset();
		mainGame->gMutex.lock();
		mainGame->stMessage->setText(dataManager.GetSysString(1501));
		mainGame->HideElement(mainGame->wCardSelect);
		mainGame->PopupElement(mainGame->wMessage);
		mainGame->gMutex.unlock();
		mainGame->actionSignal.Wait();
		mainGame->gMutex.lock();
		mainGame->dInfo.isStarted = false;
		mainGame->dInfo.isInDuel = false;
		mainGame->dInfo.isFinished = true;
		mainGame->dInfo.isReplay = false;
		mainGame->dInfo.isSingleMode = false;
		mainGame->gMutex.unlock();
		mainGame->closeDoneSignal.Reset();
		mainGame->closeSignal.Set();
		mainGame->closeDoneSignal.Wait();
		mainGame->gMutex.lock();
		mainGame->ShowElement(mainGame->wReplay);
		mainGame->stTip->setVisible(false);
		mainGame->device->setEventReceiver(&mainGame->menuHandler);
		mainGame->gMutex.unlock();
		if(exit_on_return)
			mainGame->OnGameClose();
	}
}
void ReplayMode::Restart(bool refresh) {
	end_duel(pduel);
	mainGame->dInfo.isStarted = false;
	mainGame->dInfo.isInDuel = false;
	mainGame->dInfo.isFinished = true;
	mainGame->dField.Clear();
	//mainGame->device->setEventReceiver(&mainGame->dField);
	cur_replay.Rewind();
	mainGame->dInfo.tag_player[0] = false;
	mainGame->dInfo.tag_player[1] = false;
	if(!StartDuel()) {
		EndDuel();
	}
	if(refresh) {
		mainGame->dField.RefreshAllCards();
		mainGame->dInfo.isStarted = true;
		mainGame->dInfo.isFinished = false;
	}
	if (mainGame->dInfo.isReplaySwapped){
		std::swap(mainGame->dInfo.lp[0], mainGame->dInfo.lp[1]);
		std::swap(mainGame->dInfo.strLP[0], mainGame->dInfo.strLP[1]);
		std::swap(mainGame->dInfo.hostname, mainGame->dInfo.clientname);
		std::swap(mainGame->dInfo.hostname_tag, mainGame->dInfo.clientname_tag);
	}
	skip_turn = 0;
}
void ReplayMode::Undo() {
	if(skip_step > 0 || current_step == 0)
		return;
	is_restarting = true;
	Pause(false, false);
}
bool ReplayMode::ReplayAnalyze(unsigned char* msg, unsigned int len) {
	unsigned char* pbuf = msg;
	int player, count;
	is_restarting = false;
	while (pbuf - msg < (int)len) {
		if(is_closing)
			return false;
		if(is_restarting) {
			//is_restarting = false;
			return true;
		}
		if(is_swaping) {
			mainGame->gMutex.lock();
			mainGame->dField.ReplaySwap();
			mainGame->gMutex.unlock();
			is_swaping = false;
		}
		auto offset = pbuf;
		bool pauseable = true;
		mainGame->dInfo.curMsg = BufferIO::Read<uint8_t>(pbuf);
		switch (mainGame->dInfo.curMsg) {
		case MSG_RETRY: {
			if(mainGame->dInfo.isReplaySkiping) {
				mainGame->dInfo.isReplaySkiping = false;
				mainGame->dField.RefreshAllCards();
				mainGame->gMutex.unlock();
			}
			mainGame->gMutex.lock();
			mainGame->stMessage->setText(L"Error occurs.");
			mainGame->PopupElement(mainGame->wMessage);
			mainGame->gMutex.unlock();
			mainGame->actionSignal.Reset();
			mainGame->actionSignal.Wait();
			return false;
		}
		case MSG_HINT: {
			pbuf += 6;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_WIN: {
			if(mainGame->dInfo.isReplaySkiping) {
				mainGame->dInfo.isReplaySkiping = false;
				mainGame->dField.RefreshAllCards();
				mainGame->gMutex.unlock();
			}
			pbuf += 2;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			return false;
		}
		case MSG_SELECT_BATTLECMD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 11;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 8 + 2;
			ReplayRefresh();
			return ReadReplayResponse();
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
			ReplayRefresh();
			return ReadReplayResponse();
		}
		case MSG_SELECT_EFFECTYN: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 12;
			return ReadReplayResponse();
		}
		case MSG_SELECT_YESNO: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4;
			return ReadReplayResponse();
		}
		case MSG_SELECT_OPTION: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			return ReadReplayResponse();
		}
		case MSG_SELECT_CARD:
		case MSG_SELECT_TRIBUTE: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 3;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 8;
			return ReadReplayResponse();
		}
		case MSG_SELECT_UNSELECT_CARD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 8;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 8;
			return ReadReplayResponse();
		}
		case MSG_SELECT_CHAIN: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 9 + count * 14;
			return ReadReplayResponse();
		}
		case MSG_SELECT_PLACE:
		case MSG_SELECT_DISFIELD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			return ReadReplayResponse();
		}
		case MSG_SELECT_POSITION: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			return ReadReplayResponse();
		}
		case MSG_SELECT_COUNTER: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 9;
			return ReadReplayResponse();
		}
		case MSG_SELECT_SUM: {
			pbuf++;
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 6;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 11;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 11;
			return ReadReplayResponse();
		}
		case MSG_SORT_CARD: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			return ReadReplayResponse();
		}
		case MSG_CONFIRM_DECKTOP: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_CONFIRM_EXTRATOP: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_CONFIRM_CARDS: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 1;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 7;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_SHUFFLE_DECK: {
			player = BufferIO::Read<uint8_t>(pbuf);
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefreshDeck(player);
			break;
		}
		case MSG_SHUFFLE_HAND: {
			/*int oplayer = */BufferIO::Read<uint8_t>(pbuf);
			int count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_SHUFFLE_EXTRA: {
			/*int oplayer = */BufferIO::Read<uint8_t>(pbuf);
			int count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_REFRESH_DECK: {
			pbuf++;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_SWAP_GRAVE_DECK: {
			player = BufferIO::Read<uint8_t>(pbuf);
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefreshGrave(player);
			break;
		}
		case MSG_REVERSE_DECK: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefreshDeck(0);
			ReplayRefreshDeck(1);
			break;
		}
		case MSG_DECK_TOP: {
			pbuf += 6;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_SHUFFLE_SET_CARD: {
			pbuf++;
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_NEW_TURN: {
			if(skip_turn) {
				skip_turn--;
				if(skip_turn == 0) {
					mainGame->dInfo.isReplaySkiping = false;
					mainGame->dField.RefreshAllCards();
					mainGame->gMutex.unlock();
				}
			}
			player = BufferIO::Read<uint8_t>(pbuf);
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_NEW_PHASE: {
			pbuf += 2;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			break;
		}
		case MSG_MOVE: {
			int pc = pbuf[4];
			int pl = pbuf[5];
			/*int ps = pbuf[6];*/
			/*int pp = pbuf[7];*/
			int cc = pbuf[8];
			int cl = pbuf[9];
			int cs = pbuf[10];
			/*int cp = pbuf[11];*/
			pbuf += 16;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			if(cl && !(cl & LOCATION_OVERLAY) && (pl != cl || pc != cc))
				ReplayRefreshSingle(cc, cl, cs);
			else if(pl == cl && cl == LOCATION_DECK)
				ReplayRefreshDeck(cc);
			break;
		}
		case MSG_POS_CHANGE: {
			pbuf += 9;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_SET: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_SWAP: {
			pbuf += 16;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_FIELD_DISABLED: {
			pbuf += 4;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_SUMMONING: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_SUMMONED: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			break;
		}
		case MSG_SPSUMMONING: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_SPSUMMONED: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			break;
		}
		case MSG_FLIPSUMMONING: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_FLIPSUMMONED: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			break;
		}
		case MSG_CHAINING: {
			pbuf += 16;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_CHAINED: {
			pbuf++;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			break;
		}
		case MSG_CHAIN_SOLVING: {
			pbuf++;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_CHAIN_SOLVED: {
			pbuf++;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			pauseable = false;
			break;
		}
		case MSG_CHAIN_END: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			pauseable = false;
			break;
		}
		case MSG_CHAIN_NEGATED: {
			pbuf++;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_CHAIN_DISABLED: {
			pbuf++;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_CARD_SELECTED:
		case MSG_RANDOM_SELECTED: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_BECOME_TARGET: {
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_DRAW: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count * 4;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_DAMAGE: {
			pbuf += 5;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_RECOVER: {
			pbuf += 5;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_EQUIP: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_LPUPDATE: {
			pbuf += 5;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_UNEQUIP: {
			pbuf += 4;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_CARD_TARGET: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_CANCEL_TARGET: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_PAY_LPCOST: {
			pbuf += 5;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_ADD_COUNTER: {
			pbuf += 7;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_REMOVE_COUNTER: {
			pbuf += 7;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_ATTACK: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_BATTLE: {
			pbuf += 26;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_ATTACK_DISABLED: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			pauseable = false;
			break;
		}
		case MSG_DAMAGE_STEP_START: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			pauseable = false;
			break;
		}
		case MSG_DAMAGE_STEP_END: {
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefresh();
			pauseable = false;
			break;
		}
		case MSG_MISSED_EFFECT: {
			pbuf += 8;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_TOSS_COIN: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_TOSS_DICE: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += count;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_ROCK_PAPER_SCISSORS: {
			player = BufferIO::Read<uint8_t>(pbuf);
			return ReadReplayResponse();
		}
		case MSG_HAND_RES: {
			pbuf += 1;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_ANNOUNCE_RACE: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			return ReadReplayResponse();
		}
		case MSG_ANNOUNCE_ATTRIB: {
			player = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 5;
			return ReadReplayResponse();
		}
		case MSG_ANNOUNCE_CARD:
		case MSG_ANNOUNCE_NUMBER: {
			player = BufferIO::Read<uint8_t>(pbuf);
			count = BufferIO::Read<uint8_t>(pbuf);
			pbuf += 4 * count;
			return ReadReplayResponse();
		}
		case MSG_CARD_HINT: {
			pbuf += 9;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_PLAYER_HINT: {
			pbuf += 6;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			break;
		}
		case MSG_MATCH_KILL: {
			pbuf += 4;
			break;
		}
		case MSG_TAG_SWAP: {
			player = pbuf[0];
			pbuf += pbuf[2] * 4 + pbuf[4] * 4 + 9;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayRefreshDeck(player);
			ReplayRefreshExtra(player);
			break;
		}
		case MSG_RELOAD_FIELD: {
			pbuf++;
			for(int p = 0; p < 2; ++p) {
				pbuf += 4;
				for(int seq = 0; seq < 7; ++seq) {
					int val = BufferIO::Read<uint8_t>(pbuf);
					if(val)
						pbuf += 2;
				}
				for(int seq = 0; seq < 8; ++seq) {
					int val = BufferIO::Read<uint8_t>(pbuf);
					if(val)
						pbuf++;
				}
				pbuf += 6;
			}
			pbuf++;
			DuelClient::ClientAnalyze(offset, pbuf - offset);
			ReplayReload();
			mainGame->dField.RefreshAllCards();
			break;
		}
		case MSG_AI_NAME: {
			int len = BufferIO::Read<uint16_t>(pbuf);
			pbuf += len + 1;
			break;
		}
		case MSG_SHOW_HINT: {
			int len = BufferIO::Read<uint16_t>(pbuf);
			pbuf += len + 1;
			break;
		}
		}
		if(pauseable) {
			current_step++;
			if(skip_step) {
				skip_step--;
				if(skip_step == 0) {
					Pause(true, false);
					mainGame->dInfo.isStarted = true;
					mainGame->dInfo.isFinished = false;
					mainGame->dInfo.isReplaySkiping = false;
					mainGame->dField.RefreshAllCards();
					mainGame->gMutex.unlock();
				}
			}
			if(is_pausing) {
				is_paused = true;
				mainGame->actionSignal.Reset();
				mainGame->actionSignal.Wait();
				is_paused = false;
			}
		}
	}
	return true;
}
inline void ReplayMode::ReloadLocation(int player, int location, int flag, std::vector<unsigned char>& queryBuffer) {
	query_field_card(pduel, player, location, flag, queryBuffer.data(), 0);
	mainGame->dField.UpdateFieldCard(mainGame->LocalPlayer(player), location, queryBuffer.data());
}
void ReplayMode::ReplayRefresh(int flag) {
	std::vector<unsigned char> queryBuffer;
	queryBuffer.resize(SIZE_QUERY_BUFFER);
	ReloadLocation(0, LOCATION_MZONE, flag, queryBuffer);
	ReloadLocation(1, LOCATION_MZONE, flag, queryBuffer);
	ReloadLocation(0, LOCATION_SZONE, flag, queryBuffer);
	ReloadLocation(1, LOCATION_SZONE, flag, queryBuffer);
	ReloadLocation(0, LOCATION_HAND, flag, queryBuffer);
	ReloadLocation(1, LOCATION_HAND, flag, queryBuffer);
}
void ReplayMode::ReplayRefreshLocation(int player, int location, int flag) {
	std::vector<unsigned char> queryBuffer;
	queryBuffer.resize(SIZE_QUERY_BUFFER);
	ReloadLocation(player, location, flag, queryBuffer);
}
inline void ReplayMode::ReplayRefreshHand(int player, int flag) {
	ReplayRefreshLocation(player, LOCATION_HAND, flag);
}
inline void ReplayMode::ReplayRefreshGrave(int player, int flag) {
	ReplayRefreshLocation(player, LOCATION_GRAVE, flag);
}
inline void ReplayMode::ReplayRefreshDeck(int player, int flag) {
	ReplayRefreshLocation(player, LOCATION_DECK, flag);
}
inline void ReplayMode::ReplayRefreshExtra(int player, int flag) {
	ReplayRefreshLocation(player, LOCATION_EXTRA, flag);
}
void ReplayMode::ReplayRefreshSingle(int player, int location, int sequence, int flag) {
	unsigned char queryBuffer[0x1000];
	/*int len = */query_card(pduel, player, location, sequence, flag, queryBuffer, 0);
	mainGame->dField.UpdateCard(mainGame->LocalPlayer(player), location, sequence, queryBuffer);
}
void ReplayMode::ReplayReload() {
	std::vector<unsigned char> queryBuffer;
	queryBuffer.resize(SIZE_QUERY_BUFFER);
	unsigned int flag = 0xffdfff;
	ReloadLocation(0, LOCATION_MZONE, flag, queryBuffer);
	ReloadLocation(1, LOCATION_MZONE, flag, queryBuffer);
	ReloadLocation(0, LOCATION_SZONE, flag, queryBuffer);
	ReloadLocation(1, LOCATION_SZONE, flag, queryBuffer);
	ReloadLocation(0, LOCATION_HAND, flag, queryBuffer);
	ReloadLocation(1, LOCATION_HAND, flag, queryBuffer);

	ReloadLocation(0, LOCATION_DECK, flag, queryBuffer);
	ReloadLocation(1, LOCATION_DECK, flag, queryBuffer);
	ReloadLocation(0, LOCATION_EXTRA, flag, queryBuffer);
	ReloadLocation(1, LOCATION_EXTRA, flag, queryBuffer);
	ReloadLocation(0, LOCATION_GRAVE, flag, queryBuffer);
	ReloadLocation(1, LOCATION_GRAVE, flag, queryBuffer);
	ReloadLocation(0, LOCATION_REMOVED, flag, queryBuffer);
	ReloadLocation(1, LOCATION_REMOVED, flag, queryBuffer);
}
uint32_t ReplayMode::MessageHandler(intptr_t fduel, uint32_t type) {
	if(!enable_log)
		return 0;
	char msgbuf[1024];
	get_log_message(fduel, msgbuf);
	mainGame->AddDebugMsg(msgbuf);
	return 0;
}

}

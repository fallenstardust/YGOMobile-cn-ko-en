#include "deck_manager.h"
#include "game.h"
#include "myfilesystem.h"
#include "network.h"

namespace ygo {

char DeckManager::deckBuffer[0x10000]{};
DeckManager deckManager;

void DeckManager::LoadLFListSingle(const char* path) {
	auto cur = _lfList.rend();
	FILE* fp = myfopen(path, "r");
	char linebuf[256]{};
	wchar_t strBuffer[256]{};
	if(fp) {
		while(std::fgets(linebuf, sizeof linebuf, fp)) {
			if(linebuf[0] == '#')
				continue;
			if(linebuf[0] == '!') {
				auto len = std::strcspn(linebuf, "\r\n");
				linebuf[len] = 0;
				BufferIO::DecodeUTF8(&linebuf[1], strBuffer);
				LFList newlist;
				newlist.listName = strBuffer;
				newlist.hash = 0x7dfcee6a;
				_lfList.push_back(newlist);
				cur = _lfList.rbegin();
				continue;
			}
			if (cur == _lfList.rend())
				continue;
			char* pos = linebuf;
			errno = 0;
			auto result = std::strtoul(pos, &pos, 10);
			if (errno || result > UINT32_MAX)
				continue;
			if (pos == linebuf || *pos != ' ')
				continue;
			uint32_t code = static_cast<uint32_t>(result);
			errno = 0;
			int count = std::strtol(pos, &pos, 10);
			if (errno)
				continue;
			if (count < 0 || count > 2)
				continue;
			cur->content[code] = count;
			cur->hash = cur->hash ^ ((code << 18) | (code >> 14)) ^ ((code << (27 + count)) | (code >> (5 - count)));
		}
		std::fclose(fp);
	}
}
void DeckManager::LoadLFList(irr::android::InitOptions *options) {
    irr::io::path workingDir = options->getWorkDir();
	LoadLFListSingle((workingDir + path("/expansions/lflist.conf")).c_str());
	LoadLFListSingle((workingDir + path("/lflist.conf")).c_str());
	LFList nolimit;
	nolimit.listName = L"N/A";
	nolimit.hash = 0;
	_lfList.push_back(nolimit);
}
const wchar_t* DeckManager::GetLFListName(unsigned int lfhash) {
	auto lit = std::find_if(_lfList.begin(), _lfList.end(), [lfhash](const ygo::LFList& list) {
		return list.hash == lfhash;
	});
	if(lit != _lfList.end())
		return lit->listName.c_str();
	return dataManager.unknown_string;
}
const LFList* DeckManager::GetLFList(unsigned int lfhash) {
	auto lit = std::find_if(_lfList.begin(), _lfList.end(), [lfhash](const ygo::LFList& list) {
		return list.hash == lfhash;
	});
	if (lit != _lfList.end())
		return &(*lit);
	return nullptr;
}
static unsigned int checkAvail(unsigned int ot, unsigned int avail) {
	if((ot & avail) == avail)
		return 0;
	if((ot & AVAIL_OCG) && (avail != AVAIL_OCG))
		return DECKERROR_OCGONLY;
	if((ot & AVAIL_TCG) && (avail != AVAIL_TCG))
		return DECKERROR_TCGONLY;
	return DECKERROR_NOTAVAIL;
}
unsigned int DeckManager::CheckDeck(const Deck& deck, unsigned int lfhash, int rule) {
	std::unordered_map<int, int> ccount;
	// rule
	if(deck.main.size() < DECK_MIN_SIZE || deck.main.size() > DECK_MAX_SIZE)
		return (DECKERROR_MAINCOUNT << 28) | (unsigned)deck.main.size();
	if(deck.extra.size() > EXTRA_MAX_SIZE)
		return (DECKERROR_EXTRACOUNT << 28) | (unsigned)deck.extra.size();
	if(deck.side.size() > SIDE_MAX_SIZE)
		return (DECKERROR_SIDECOUNT << 28) | (unsigned)deck.side.size();
	auto lflist = GetLFList(lfhash);
	if (!lflist)
		return 0;
	auto& list = lflist->content;
	const unsigned int rule_map[6] = { AVAIL_OCG, AVAIL_TCG, AVAIL_SC, AVAIL_CUSTOM, AVAIL_OCGTCG, 0 };
	unsigned int avail = 0;
	if (rule >= 0 && rule < (int)(sizeof rule_map / sizeof rule_map[0]))
		avail = rule_map[rule];
	for (auto& cit : deck.main) {
		auto gameruleDeckError = checkAvail(cit->second.ot, avail);
		if(gameruleDeckError)
			return (gameruleDeckError << 28) | cit->first;
		if (cit->second.type & (TYPES_EXTRA_DECK | TYPE_TOKEN))
			return (DECKERROR_MAINCOUNT << 28);
		int code = cit->second.alias ? cit->second.alias : cit->first;
		ccount[code]++;
		int dc = ccount[code];
		if(dc > 3)
			return (DECKERROR_CARDCOUNT << 28) | cit->first;
		auto it = list.find(code);
		if(it != list.end() && dc > it->second)
			return (DECKERROR_LFLIST << 28) | cit->first;
	}
	for (auto& cit : deck.extra) {
		auto gameruleDeckError = checkAvail(cit->second.ot, avail);
		if(gameruleDeckError)
			return (gameruleDeckError << 28) | cit->first;
		if (!(cit->second.type & TYPES_EXTRA_DECK) || cit->second.type & TYPE_TOKEN)
			return (DECKERROR_EXTRACOUNT << 28);
		int code = cit->second.alias ? cit->second.alias : cit->first;
		ccount[code]++;
		int dc = ccount[code];
		if(dc > 3)
			return (DECKERROR_CARDCOUNT << 28) | cit->first;
		auto it = list.find(code);
		if(it != list.end() && dc > it->second)
			return (DECKERROR_LFLIST << 28) | cit->first;
	}
	for (auto& cit : deck.side) {
		auto gameruleDeckError = checkAvail(cit->second.ot, avail);
		if(gameruleDeckError)
			return (gameruleDeckError << 28) | cit->first;
		if (cit->second.type & TYPE_TOKEN)
			return (DECKERROR_SIDECOUNT << 28);
		int code = cit->second.alias ? cit->second.alias : cit->first;
		ccount[code]++;
		int dc = ccount[code];
		if(dc > 3)
			return (DECKERROR_CARDCOUNT << 28) | cit->first;
		auto it = list.find(code);
		if(it != list.end() && dc > it->second)
			return (DECKERROR_LFLIST << 28) | cit->first;
	}
	return 0;
}
uint32_t DeckManager::LoadDeck(Deck& deck, uint32_t dbuf[], int mainc, int sidec, bool is_packlist) {
	deck.clear();
	uint32_t errorcode = 0;
	CardData cd;
	for(int i = 0; i < mainc; ++i) {
		auto code = dbuf[i];
		if(!dataManager.GetData(code, &cd)) {
			errorcode = code;
			continue;
		}
		if (cd.type & TYPE_TOKEN) {
			errorcode = code;
			continue;
		}
		if(is_packlist) {
			deck.main.push_back(dataManager.GetCodePointer(code));
			continue;
		}
		if (cd.type & TYPES_EXTRA_DECK) {
			if (deck.extra.size() < EXTRA_MAX_SIZE)
				deck.extra.push_back(dataManager.GetCodePointer(code));
		}
		else {
			if (deck.main.size() < DECK_MAX_SIZE)
				deck.main.push_back(dataManager.GetCodePointer(code));
		}
	}
	for(int i = 0; i < sidec; ++i) {
		auto code = dbuf[mainc + i];
		if(!dataManager.GetData(code, &cd)) {
			errorcode = code;
			continue;
		}
		if (cd.type & TYPE_TOKEN) {
			errorcode = code;
			continue;
		}
		if(deck.side.size() < SIDE_MAX_SIZE)
			deck.side.push_back(dataManager.GetCodePointer(code));
	}
	return errorcode;
}
uint32_t DeckManager::LoadDeckFromStream(Deck& deck, std::istringstream& deckStream, bool is_packlist) {
	int ct = 0;
	int mainc = 0, sidec = 0;
	uint32_t cardlist[PACK_MAX_SIZE]{};
	bool is_side = false;
	std::string linebuf;
	while (std::getline(deckStream, linebuf, '\n') && ct < PACK_MAX_SIZE) {
		if (linebuf[0] == '!') {
			is_side = true;
			continue;
		}
		if (linebuf[0] < '0' || linebuf[0] > '9')
			continue;
		errno = 0;
		auto code = std::strtoul(linebuf.c_str(), nullptr, 10);
		if (errno || code > UINT32_MAX)
			continue;
		cardlist[ct++] = code;
		if (is_side)
			++sidec;
		else
			++mainc;
	}
	return LoadDeck(deck, cardlist, mainc, sidec, is_packlist);
}
bool DeckManager::LoadSide(Deck& deck, uint32_t dbuf[], int mainc, int sidec) {
	std::unordered_map<uint32_t, int> pcount;
	std::unordered_map<uint32_t, int> ncount;
	for(size_t i = 0; i < deck.main.size(); ++i)
		pcount[deck.main[i]->first]++;
	for(size_t i = 0; i < deck.extra.size(); ++i)
		pcount[deck.extra[i]->first]++;
	for(size_t i = 0; i < deck.side.size(); ++i)
		pcount[deck.side[i]->first]++;
	Deck ndeck;
	LoadDeck(ndeck, dbuf, mainc, sidec);
	if (ndeck.main.size() != deck.main.size() || ndeck.extra.size() != deck.extra.size() || ndeck.side.size() != deck.side.size())
		return false;
	for(size_t i = 0; i < ndeck.main.size(); ++i)
		ncount[ndeck.main[i]->first]++;
	for(size_t i = 0; i < ndeck.extra.size(); ++i)
		ncount[ndeck.extra[i]->first]++;
	for(size_t i = 0; i < ndeck.side.size(); ++i)
		ncount[ndeck.side[i]->first]++;
	for (auto& cdit : ncount)
		if (cdit.second != pcount[cdit.first])
			return false;
	deck = ndeck;
	return true;
}
void DeckManager::GetCategoryPath(wchar_t* ret, int index, const wchar_t* text, bool showPack) {//hide packlist if showing on duelling ready
	wchar_t catepath[256];
	switch(index) {
	case 0:
		if (showPack) {
			myswprintf(catepath, L"./pack");
		} else {
			myswprintf(catepath, L"./windbot/Decks");
		}
		break;
	case 1:
		if (showPack) {
			myswprintf(catepath, L"./windbot/Decks");
		} else {
			myswprintf(catepath, L"./deck");
		}
		break;
	case -1:
	case 2:
	case 3:
		if (showPack) {
			myswprintf(catepath, L"./deck");
		} else {
			myswprintf(catepath, L"./deck/%ls", text);
		}
		break;
	default:
		myswprintf(catepath, L"./deck/%ls", text);
	}
	BufferIO::CopyWStr(catepath, ret, 256);
}
void DeckManager::GetDeckFile(wchar_t* ret, irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDeck) {
	wchar_t filepath[256];
	wchar_t catepath[256];
	const wchar_t* deckname = cbDeck->getItem(cbDeck->getSelected());
	if(deckname != nullptr) {
		GetCategoryPath(catepath, cbCategory->getSelected(), cbCategory->getText(), cbCategory == mainGame->cbDBCategory);
		myswprintf(filepath, L"%ls/%ls.ydk", catepath, deckname);
		BufferIO::CopyWStr(filepath, ret, 256);
	}
	else {
		BufferIO::CopyWStr(L"", ret, 256);
	}
}
FILE* DeckManager::OpenDeckFile(const wchar_t* file, const char* mode) {
	FILE* fp = mywfopen(file, mode);
	return fp;
}
irr::io::IReadFile* DeckManager::OpenDeckReader(const wchar_t* file) {
	char file2[256];
	BufferIO::EncodeUTF8(file, file2);
	auto reader = DataManager::FileSystem->createAndOpenFile(file2);
	return reader;
}
bool DeckManager::LoadCurrentDeck(std::istringstream& deckStream, bool is_packlist) {
	LoadDeckFromStream(current_deck, deckStream, is_packlist);
	return true;  // the above LoadDeck has return value but we ignore it here for now
}
bool DeckManager::LoadCurrentDeck(const wchar_t* file, bool is_packlist) {
	current_deck.clear();
	auto reader = OpenDeckReader(file);
	if(!reader) {
		wchar_t localfile[256];
		myswprintf(localfile, L"./deck/%ls.ydk", file);
		reader = OpenDeckReader(localfile);
	}
	if(!reader && !mywcsncasecmp(file, L"./pack", 6)) {
		wchar_t zipfile[256];
		myswprintf(zipfile, L"%ls", file + 2);
		reader = OpenDeckReader(zipfile);
	}
	if(!reader)
		return false;
	std::memset(deckBuffer, 0, sizeof deckBuffer);
	int size = reader->read(deckBuffer, sizeof deckBuffer);
	reader->drop();
	if (size >= (int)sizeof deckBuffer) {
		return false;
	}
	std::istringstream deckStream(deckBuffer);
	LoadDeckFromStream(current_deck, deckStream, is_packlist);
	return true;  // the above function has return value but we ignore it here for now
}
bool DeckManager::LoadCurrentDeck(irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDeck) {
	wchar_t filepath[256];
	GetDeckFile(filepath, cbCategory, cbDeck);
	bool is_packlist = (cbCategory->getSelected() == 0);
	bool res = LoadCurrentDeck(filepath, is_packlist);
	if (res && mainGame->is_building)
		mainGame->deckBuilder.RefreshPackListScroll();
	return res;
}
void DeckManager::SaveDeck(const Deck& deck, std::stringstream& deckStream) {
	deckStream << "#created by ..." << std::endl;
	deckStream << "#main" << std::endl;
	for(size_t i = 0; i < deck.main.size(); ++i)
		deckStream << deck.main[i]->first << std::endl;
	deckStream << "#extra" << std::endl;
	for(size_t i = 0; i < deck.extra.size(); ++i)
		deckStream << deck.extra[i]->first << std::endl;
	deckStream << "!side" << std::endl;
	for(size_t i = 0; i < deck.side.size(); ++i)
		deckStream << deck.side[i]->first << std::endl;
}
bool DeckManager::SaveDeck(const Deck& deck, const wchar_t* file) {
	if(!FileSystem::IsDirExists(L"./deck") && !FileSystem::MakeDir(L"./deck"))
		return false;
	FILE* fp = OpenDeckFile(file, "w");
	if(!fp)
		return false;
	std::stringstream deckStream;
	SaveDeck(deck, deckStream);
	std::fwrite(deckStream.str().c_str(), 1, deckStream.str().length(), fp);
	std::fclose(fp);
	return true;
}
bool DeckManager::DeleteDeck(const wchar_t* file) {
	return FileSystem::RemoveFile(file);
}
bool DeckManager::CreateCategory(const wchar_t* name) {
	if(!FileSystem::IsDirExists(L"./deck") && !FileSystem::MakeDir(L"./deck"))
		return false;
	if(name[0] == 0)
		return false;
	wchar_t localname[256];
	myswprintf(localname, L"./deck/%ls", name);
	return FileSystem::MakeDir(localname);
}
bool DeckManager::RenameCategory(const wchar_t* oldname, const wchar_t* newname) {
	if(!FileSystem::IsDirExists(L"./deck") && !FileSystem::MakeDir(L"./deck"))
		return false;
	if(newname[0] == 0)
		return false;
	wchar_t oldlocalname[256];
	wchar_t newlocalname[256];
	myswprintf(oldlocalname, L"./deck/%ls", oldname);
	myswprintf(newlocalname, L"./deck/%ls", newname);
	return FileSystem::Rename(oldlocalname, newlocalname);
}
bool DeckManager::DeleteCategory(const wchar_t* name) {
	wchar_t localname[256];
	myswprintf(localname, L"./deck/%ls", name);
	if(!FileSystem::IsDirExists(localname))
		return false;
	return FileSystem::DeleteDir(localname);
}
bool DeckManager::SaveDeckArray(const DeckArray& deck, const wchar_t* name) {
	if (!FileSystem::IsDirExists(L"./deck") && !FileSystem::MakeDir(L"./deck"))
		return false;
	FILE* fp = OpenDeckFile(name, "w");
	if (!fp)
		return false;
	std::fprintf(fp, "#created by ...\n#main\n");
	for (const auto& code : deck.main)
		std::fprintf(fp, "%u\n", code);
	std::fprintf(fp, "#extra\n");
	for (const auto& code : deck.extra)
		std::fprintf(fp, "%u\n", code);
	std::fprintf(fp, "!side\n");
	for (const auto& code : deck.side)
		std::fprintf(fp, "%u\n", code);
	std::fclose(fp);
	return true;
}
int DeckManager::TypeCount(std::vector<code_pointer> list, unsigned int ctype) {
	int res = 0;
	for(size_t i = 0; i < list.size(); ++i) {
		code_pointer cur = list[i];
		if(cur->second.type & ctype)
			res++;
	}
	return res;
}
}

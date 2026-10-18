#ifndef DECKMANAGER_H
#define DECKMANAGER_H

#include <unordered_map>
#include <vector>
#include <sstream>
#include "deck.h"
#include "config.h"

namespace irr {
	namespace io {
		class IReadFile;
	}
}

namespace ygo {

constexpr int DECK_MAX_SIZE = 60;
constexpr int DECK_MIN_SIZE = 40;
constexpr int EXTRA_MAX_SIZE = 15;
constexpr int SIDE_MAX_SIZE = 15;
constexpr int PACK_MAX_SIZE = 1000;

constexpr int MAINC_MAX = 250;	// the limit of card_state
constexpr int SIDEC_MAX = MAINC_MAX;

constexpr int DECK_CATEGORY_PACK = 0;
constexpr int DECK_CATEGORY_BOT = 1;
constexpr int DECK_CATEGORY_NONE = 2;
constexpr int DECK_CATEGORY_SEPARATOR = 3;
constexpr int DECK_CATEGORY_CUSTOM = 4;

struct LFList {
	unsigned int hash{};
	std::wstring listName;
	std::unordered_map<uint32_t, int> content;
	std::unordered_map<std::wstring, uint32_t> credit_limits;
	std::unordered_map<uint32_t, std::unordered_map<std::wstring, uint32_t>> credits;
	// GeneSys: 由lflist.conf中的"$__extra_score__"行解析而来，
	// 表示主卡组超过DECK_MIN_SIZE(40)张后，每多一张卡可为积分上限提高的分数（小数）。
	// 未配置该行的禁卡表默认为0，即积分上限不随主卡组数量变化。
	double extra_score{};

	// 根据当前主卡组数量计算即时生效的积分上限。
	// 规则：主卡组超过40张后，每多一张，上限提高 extra_score 分；
	// 主卡组不足40张时不向下扣减，仍为基础上限；
	// 最终上限取整（丢弃小数部分）。
	uint32_t GetEffectiveCreditLimit(uint32_t base_limit, size_t main_count) const {
		if(extra_score <= 0.0)
			return base_limit;
		if(main_count <= DECK_MIN_SIZE)
			return base_limit;
		double extra = static_cast<double>(main_count - DECK_MIN_SIZE) * extra_score;
		double effective = static_cast<double>(base_limit) + extra;
		if(effective < static_cast<double>(base_limit))
			return base_limit; // 不做向下扣减保护
		return static_cast<uint32_t>(effective); // 正数强转即向下取整，丢弃小数
	}
};

class DeckManager {
public:
	Deck current_deck;
	std::vector<LFList> _lfList;
	std::vector<LFList> _genesys_lfList;

	static constexpr int MAX_YDK_SIZE = 0x10000;

	void LoadLFListSingle(const char* path);
	void LoadLFList(irr::android::InitOptions *options);
	const wchar_t* GetLFListName(unsigned int lfhash);
	const LFList* GetLFList(unsigned int lfhash);
	uint32_t CheckDeck(const Deck& deck, unsigned int lfhash, size_t rule);
	bool LoadCurrentDeck(const wchar_t* file, bool is_packlist = false);
	bool LoadCurrentDeck(irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDeck);
	bool LoadCurrentDeck(std::istringstream& deckStream, bool is_packlist = false);

	static uint32_t LoadDeck(Deck& deck, uint32_t dbuf[], uint32_t mainc, uint32_t sidec, bool is_packlist = false);
	static uint32_t LoadDeckFromStream(Deck& deck, std::istringstream& deckStream, bool is_packlist = false);
	static bool LoadSide(Deck& deck, uint32_t dbuf[], uint32_t mainc, uint32_t sidec);
	static void GetCategoryPath(wchar_t* ret, int index, const wchar_t* text, bool showPack);//
	static void GetDeckFile(wchar_t* ret, irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDeck);
	static FILE* OpenDeckFile(const wchar_t* file, const char* mode);
	static irr::io::IReadFile* OpenDeckReader(const wchar_t* file);
	static bool SaveDeck(const Deck& deck, const wchar_t* file, bool requestNewId = true);
	static void SaveDeck(const Deck& deck, std::stringstream& deckStream);
	static bool DeleteDeck(const wchar_t* file);
	static bool CreateCategory(const wchar_t* name);
	static bool RenameCategory(const wchar_t* oldname, const wchar_t* newname);
	static bool DeleteCategory(const wchar_t* name);
	static bool SaveDeckArray(const DeckArray& deck, const wchar_t* name);
	
	int TypeCount(std::vector<const CardDataC*> list, unsigned int ctype);
private:
    static std::vector<std::wstring> deckComments;  // 存储以##和###开头的注释行
};

extern DeckManager deckManager;

}

#endif //DECKMANAGER_H

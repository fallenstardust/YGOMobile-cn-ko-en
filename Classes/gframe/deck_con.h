#ifndef DECK_CON_H
#define DECK_CON_H

#include <unordered_map>
#include <vector>
#include <random>
#include <irrlicht.h>
#include "data_manager.h"
#include "deck_manager.h"
#ifdef _IRR_ANDROID_PLATFORM_
#include <android/TouchEventTransferAndroid.h>
#include <android/android_tools.h>
#endif

namespace ygo {

class DeckBuilder: public irr::IEventReceiver {
public:
	DeckBuilder();
	bool OnEvent(const irr::SEvent& event) override;
	void Initialize();
	void Terminate();
	void GetHoveredCard();
	void FilterCards();
	void StartFilter();
	void ClearFilter();
	void InstantSearch();
	void ClearSearch();
	void SortList();

	void RefreshDeckList(bool showPack);//
	void RefreshReadonly(int catesel);
	void RefreshPackListScroll();
	void ChangeCategory(int catesel);
	void ShowDeckManage();

	bool CardNameContains(const wchar_t *haystack, const wchar_t *needle);

	bool push_main(code_pointer pointer, int seq = -1);
	bool push_extra(code_pointer pointer, int seq = -1);
	bool push_side(code_pointer pointer, int seq = -1);
	void pop_main(int seq);
	void pop_extra(int seq);
	void pop_side(int seq);
	bool check_limit(code_pointer pointer);

	unsigned long long filter_effect{};
	unsigned int filter_type{};
	unsigned int filter_type2{};
	unsigned int filter_attrib{};
	unsigned int filter_race{};
	unsigned int filter_atktype{};
	int filter_atk{};
	unsigned int filter_deftype{};
	int filter_def{};
	unsigned int filter_lvtype{};
	unsigned int filter_lv{};
	unsigned int filter_scltype{};
	unsigned int filter_scl{};
	unsigned int filter_marks{};
	int filter_lm{};
	irr::core::vector2di mouse_pos;
	int hovered_code{};
	int hovered_pos{};
	int hovered_seq{ -1 };
	int is_lastcard{};
	int click_pos{};
	bool is_draging{};
	bool is_starting_dragging{};
	int dragx{};
	int dragy{};
	int bigcard_code{};
	float bigcard_zoom{};
	size_t pre_mainc{};
	size_t pre_extrac{};
	size_t pre_sidec{};
	code_pointer draging_pointer;
	int prev_category{};
	int prev_deck{};
	irr::s32 prev_operation{};
	int prev_sel{ -1 };
	bool is_modified{};
	bool readonly{};
	bool showing_pack{};
	std::mt19937 rnd;

	const LFList* filterList{};
	std::vector<code_pointer> results;
	wchar_t result_string[8]{};
	std::vector<std::wstring> expansionPacks;
};

}

#endif //DECK_CON

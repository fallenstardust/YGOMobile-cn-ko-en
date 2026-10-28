package cn.garymb.ygomobile.game.deck;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.dialogs.deck.EffectCategoryPopupWindow;
import cn.garymb.ygomobile.ui.dialogs.deck.LinkMarkerPopupWindow;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

/*
* ygopro横屏卡组编辑的搜索器部分
* 由deckeditorManager调用此处代码
* */
public class CardSearcherManager extends CardSearcherManagerBase {

    public CardSearcherManager(Activity activity) {
        super(activity);
    }

    public void bindViews(View root) {
        tvSearchResult = root.findViewById(R.id.tv_deck_search_result);
        rvSearchResults = root.findViewById(R.id.rv_deck_search_results);
        spinnerFilterType = root.findViewById(R.id.spinner_filter_type);
        spinnerFilterType2 = root.findViewById(R.id.spinner_filter_type2);
        spinnerFilterAttribute = root.findViewById(R.id.spinner_filter_attribute);
        spinnerFilterRace = root.findViewById(R.id.spinner_filter_race);
        spinnerFilterLimit = root.findViewById(R.id.spinner_filter_limit);
        spinnerSortType = root.findViewById(R.id.spinner_sort_type);
        etAttack = root.findViewById(R.id.et_filter_attack);
        etDefense = root.findViewById(R.id.et_filter_defense);
        etStar = root.findViewById(R.id.et_filter_star);
        etScale = root.findViewById(R.id.et_filter_scale);
        etKeyword = root.findViewById(R.id.et_filter_keyword);
        btnFilterEffect = root.findViewById(R.id.btn_filter_effect);
        btnFilterMarks = root.findViewById(R.id.btn_filter_marks);
        btnFilterSearch = root.findViewById(R.id.btn_filter_search);
        btnFilterClear = root.findViewById(R.id.btn_filter_clear);
    }

    public void setupLabels() {
        searchResultPrefix = mStringManager.getSystemString(1333, "搜索结果:");
        // 筛选按钮文字复用 gframe 既有系统字符串（对齐 game.cpp wFilter）：
        // btnEffectFilter 1326 效果 / btnMarksFilter 1374 连接标记
        if (btnFilterEffect != null) btnFilterEffect.setText(mStringManager.getSystemString(1326, "效果"));
        if (btnFilterMarks != null) btnFilterMarks.setText(mStringManager.getSystemString(1374, "连接标记"));
    }

    public void setupSearchRecyclerView(ImageLoader imageLoader, CardDragHelper dragHelper,
                                        DeckEditorManager editorManager) {
        rvSearchResults.setLayoutManager(new LinearLayoutManager(activity));
        searchAdapter = new DeckCardAdapter(imageLoader, editorManager, null, dragHelper);
        searchAdapter.setLimitList(mLimitList);
        rvSearchResults.setAdapter(searchAdapter);
    }

    /**
     * 旋转重建后把常驻内存的上次搜索结果回填到新视图树的空适配器：
     * setupSearchRecyclerView 每次新建 DeckCardAdapter（初始无卡），而
     * searchResults 随本实例跨旋转保留，不回填则搜索结果列表清空。
     */
    public void rebindResultsToNewAdapter() {
        if (searchAdapter == null || searchResults.isEmpty()) return;
        searchAdapter.setCards(searchResults);
        if (tvSearchResult != null)
            tvSearchResult.setText(searchResultPrefix + " " + searchResults.size());
    }

    public void setupSpinners() {
        int dropBg = YGOUtil.c(R.color.ygopro_list_background);
        List<SimpleSpinnerItem> typeItems = new ArrayList<>();
        typeItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        typeItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1312, "怪兽")));
        typeItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1313, "魔法")));
        typeItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1314, "陷阱")));
        if (spinnerFilterType != null) {
            spinnerFilterType.setAdapter(createSpinnerAdapter(typeItems, Color.WHITE, dropBg));
            spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    updateType2Spinner(pos);
                }

                @Override
                public void onNothingSelected(AdapterView<?> p) {
                    updateType2Spinner(0);
                }
            });
        }
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);
        if (spinnerFilterType2 != null) {
            spinnerFilterType2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    updateDefenseEditState();
                }

                @Override
                public void onNothingSelected(AdapterView<?> p) {
                    updateDefenseEditState();
                }
            });
        }
        List<SimpleSpinnerItem> attrItems = new ArrayList<>();
        attrItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        for (CardAttribute attr : CardAttribute.values())
            attrItems.add(new SimpleSpinnerItem(attr.getId(), mStringManager.getSystemString(attr.getLanguageIndex(), attr.name())));
        attrAdapter = createSpinnerAdapter(attrItems, Color.WHITE, dropBg);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setAdapter(attrAdapter);
        List<SimpleSpinnerItem> raceItems = new ArrayList<>();
        raceItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        for (CardRace race : CardRace.values())
            raceItems.add(new SimpleSpinnerItem(race.value(), mStringManager.getSystemString(race.getLanguageIndex(), race.name())));
        raceAdapter = createSpinnerAdapter(raceItems, Color.WHITE, dropBg);
        if (spinnerFilterRace != null) spinnerFilterRace.setAdapter(raceAdapter);
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);
        List<SimpleSpinnerItem> limitItems = new ArrayList<>();
        limitItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        limitItems.add(new SimpleSpinnerItem(LimitType.Forbidden.getId(), mStringManager.getSystemString(LimitType.Forbidden.getLanguageIndex(), LimitType.Forbidden.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.Limit.getId(), mStringManager.getSystemString(LimitType.Limit.getLanguageIndex(), LimitType.Limit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.SemiLimit.getId(), mStringManager.getSystemString(LimitType.SemiLimit.getLanguageIndex(), LimitType.SemiLimit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.GeneSys.getId(), mStringManager.getSystemString(LimitType.GeneSys.getLanguageIndex(), LimitType.GeneSys.name())));
        limitItems.add(new SimpleSpinnerItem(6, mStringManager.getSystemString(1481, "OCG")));
        limitItems.add(new SimpleSpinnerItem(7, mStringManager.getSystemString(1482, "TCG")));
        limitItems.add(new SimpleSpinnerItem(8, mStringManager.getSystemString(1483, "简体中文")));
        limitItems.add(new SimpleSpinnerItem(9, mStringManager.getSystemString(1484, "自定义")));
        limitItems.add(new SimpleSpinnerItem(10, mStringManager.getSystemString(1487, "OCG独有")));
        limitItems.add(new SimpleSpinnerItem(11, mStringManager.getSystemString(1488, "TCG独有")));
        limitItems.add(new SimpleSpinnerItem(12, mStringManager.getSystemString(1485, "无独有卡")));
        if (spinnerFilterLimit != null) {
            spinnerFilterLimit.setAdapter(createSpinnerAdapter(limitItems, Color.WHITE, dropBg));
            spinnerFilterLimit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    // availLm 更新由 DeckEditorManager 通过 onAvailDisplayChanged 回调处理
                }

                @Override
                public void onNothingSelected(AdapterView<?> p) {
                }
            });
        }
        List<SimpleSpinnerItem> sortItems = new ArrayList<>();
        sortItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1370, "星数↑")));
        sortItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1371, "攻击↑")));
        sortItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1372, "守备↑")));
        sortItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1373, "名称↓")));
        if (spinnerSortType != null)
            spinnerSortType.setAdapter(createSpinnerAdapter(sortItems, Color.WHITE, dropBg));
    }

    public void setupButtons() {
        setClickListener(btnFilterEffect, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            showEffectCategoryPopup();
        });
        setClickListener(btnFilterMarks, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            showLinkMarkerPopup();
        });
        setClickListener(btnFilterSearch, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            startFilter();
        });
        setClickListener(btnFilterClear, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            clearSearch();
        });
        setupKeywordInput();
    }

    // === 对应 deck_con.cpp: StartFilter / FilterCards ===
    public void startFilter() {
        if (pendingKeywordSearch != null) {
            mainHandler.removeCallbacks(pendingKeywordSearch);
            pendingKeywordSearch = null;
        }
        filterType = spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0;
        filterType2 = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterType2);
        filterLm = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterLimit);
        filterAttrib = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterAttribute);
        filterRace = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterRace);
        int[] atk = parseFilterType(etAttack != null ? etAttack.getText().toString() : "");
        filterAtkType = atk[0];
        filterAtk = atk[1];
        int[] def = parseFilterType(etDefense != null ? etDefense.getText().toString() : "");
        filterDefType = def[0];
        filterDef = def[1];
        int[] lv = parseFilterType(etStar != null ? etStar.getText().toString() : "");
        filterLvType = lv[0];
        filterLv = lv[1];
        int[] scl = parseFilterType(etScale != null ? etScale.getText().toString() : "");
        filterSclType = scl[0];
        filterScl = scl[1];
        executeFilterAsync();
    }

    protected void setupKeywordInput() {
        if (etKeyword == null) return;
        etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startFilter();
                return true;
            }
            return false;
        });
        keywordTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingKeywordSearch != null) mainHandler.removeCallbacks(pendingKeywordSearch);
                pendingKeywordSearch = () -> startFilter();
                mainHandler.postDelayed(pendingKeywordSearch, 300);
            }
        };
        etKeyword.addTextChangedListener(keywordTextWatcher);
    }

    protected void updateType2Spinner(int typePos) {
        if (spinnerFilterType2 == null) return;
        boolean enabled = typePos != 0;
        List<SimpleSpinnerItem> items = new ArrayList<>();
        items.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1080, "（N/A）")));
        long m = CardType.Monster.getId();
        switch (typePos) {
            case 1:
                addSpinnerItems(items, m);
                break;
            case 2:
                items.add(new SimpleSpinnerItem(CardType.Spell.getId(), mStringManager.getSystemString(1054, "通常")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.QuickPlay.getId(), mStringManager.getSystemString(1066, "速攻")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Continuous.getId(), mStringManager.getSystemString(1067, "永续")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Ritual.getId(), mStringManager.getSystemString(1057, "仪式")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Equip.getId(), mStringManager.getSystemString(1068, "装备")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Field.getId(), mStringManager.getSystemString(1069, "场地")));
                break;
            case 3:
                items.add(new SimpleSpinnerItem(CardType.Trap.getId(), mStringManager.getSystemString(1054, "通常")));
                items.add(new SimpleSpinnerItem(CardType.Trap.getId() | CardType.Continuous.getId(), mStringManager.getSystemString(1067, "永续")));
                items.add(new SimpleSpinnerItem(CardType.Trap.getId() | CardType.Counter.getId(), mStringManager.getSystemString(1070, "反击")));
                break;
            default:
                break;
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(activity);
        adapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        adapter.setColor(enabled ? Color.WHITE : Color.GRAY);
        adapter.setTextSize(8f);
        adapter.set(items);
        spinnerFilterType2.setAdapter(adapter);
        spinnerFilterType2.setSelection(0);
        boolean monsterEnabled = typePos == 1;
        setSpinnerEnabled(spinnerFilterType2, null, enabled);
        setSpinnerEnabled(spinnerFilterAttribute, attrAdapter, monsterEnabled);
        setSpinnerEnabled(spinnerFilterRace, raceAdapter, monsterEnabled);
        setEditTextEnabled(etStar, monsterEnabled);
        setEditTextEnabled(etScale, monsterEnabled);
        setEditTextEnabled(etAttack, monsterEnabled);
        updateDefenseEditState();
    }

    protected void addSpinnerItems(List<SimpleSpinnerItem> items, long m) {
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId(), mStringManager.getSystemString(1054, "通常")));
        items.add(new SimpleSpinnerItem(m | CardType.Effect.getId(), mStringManager.getSystemString(1055, "效果")));
        items.add(new SimpleSpinnerItem(m | CardType.Fusion.getId(), mStringManager.getSystemString(1056, "融合")));
        items.add(new SimpleSpinnerItem(m | CardType.Ritual.getId(), mStringManager.getSystemString(1057, "仪式")));
        items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId(), mStringManager.getSystemString(1063, "同调")));
        items.add(new SimpleSpinnerItem(m | CardType.Xyz.getId(), mStringManager.getSystemString(1073, "超量")));
        items.add(new SimpleSpinnerItem(m | CardType.Pendulum.getId(), mStringManager.getSystemString(1074, "灵摆")));
        items.add(new SimpleSpinnerItem(m | CardType.Link.getId(), mStringManager.getSystemString(1076, "连接")));
        items.add(new SimpleSpinnerItem(m | CardType.Sp_Summon.getId(), mStringManager.getSystemString(1075, "特殊召唤")));
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Tuner.getId(), mStringManager.getSystemString(1054, "通常") + "|" + mStringManager.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Pendulum.getId(), mStringManager.getSystemString(1054, "通常") + "|" + mStringManager.getSystemString(1074, "灵摆")));
        items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId() | CardType.Tuner.getId(), mStringManager.getSystemString(1063, "同调") + "|" + mStringManager.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Tuner.getId(), mStringManager.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Gemini.getId(), mStringManager.getSystemString(1061, "二重")));
        items.add(new SimpleSpinnerItem(m | CardType.Union.getId(), mStringManager.getSystemString(1060, "同盟")));
        items.add(new SimpleSpinnerItem(m | CardType.Spirit.getId(), mStringManager.getSystemString(1059, "灵魂")));
        items.add(new SimpleSpinnerItem(m | CardType.Flip.getId(), mStringManager.getSystemString(1071, "反转")));
        items.add(new SimpleSpinnerItem(m | CardType.Toon.getId(), mStringManager.getSystemString(1072, "卡通")));
    }

    protected void setSpinnerEnabled(Spinner spinner, SimpleSpinnerAdapter adapter, boolean enabled) {
        if (spinner == null) return;
        spinner.setEnabled(enabled);
        if (!enabled) {
            spinner.setSelection(0);
        }
        if (adapter != null) {
            adapter.setColor(enabled ? Color.WHITE : Color.GRAY);
            adapter.notifyDataSetChanged();
        }
    }

    protected void setEditTextEnabled(EditText et, boolean enabled) {
        if (et == null) return;
        et.setEnabled(enabled);
        if (!enabled) {
            et.setText("");
        }
        et.setBackground(activity.getDrawable(enabled ? R.drawable.ygopro_base_background : R.drawable.ygopro_unable_background));
    }

    protected void updateDefenseEditState() {
        boolean monster = spinnerFilterType != null && spinnerFilterType.getSelectedItemPosition() == 1;
        if (!monster) {
            setEditTextEnabled(etDefense, false);
            return;
        }
        long value = SimpleSpinnerAdapter.getSelect(spinnerFilterType2);
        boolean isLink = (value & CardType.Link.getId()) != 0;
        setEditTextEnabled(etDefense, !isLink);
    }

    protected SimpleSpinnerAdapter createSpinnerAdapter(List<SimpleSpinnerItem> items, int color, int dropBg) {
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(activity);
        adapter.setColor(color);
        adapter.setDropDownBackgroundColor(dropBg);
        adapter.setTextSize(8f);
        adapter.set(items);
        return adapter;
    }

    /**
     * 卡组编辑模式下点击卡片描述高亮关键词时的搜索入口：把关键词填入检索词输入框并立即搜索，
     * 结果直接呈现在卡组编辑的搜索结果列表（{@code rv_deck_search_results}）上，不再另弹关键词列表弹窗。
     * 保留当前其他筛选条件（与手动在输入框输入后回车搜索完全一致）。setText 会触发
     * keywordTextWatcher 的防抖搜索，随后 startFilter 会取消其 pending 并立即执行，故只跑一次。
     */
    public void searchByKeyword(String keyword) {
        if (etKeyword == null) return;
        etKeyword.setText(keyword == null ? "" : keyword);
        etKeyword.setSelection(etKeyword.getText().length());
        startFilter();
    }

    /**
     * 卡组编辑模式下点击关联卡片按钮的展示入口：把已算好的关联卡列表按当前排序直接
     * 呈现在卡组编辑搜索结果列表（{@code rv_deck_search_results}）上，不再走关键词筛选。取消可能 pending 的
     * 防抖关键词搜索，并用新搜索代号同步提交，避免与进行中的异步筛选互相覆盖。
     */
    public void showRelatedCards(List<Card> cards) {
        if (pendingKeywordSearch != null) {
            mainHandler.removeCallbacks(pendingKeywordSearch);
            pendingKeywordSearch = null;
        }
        List<Card> results = cards == null ? new ArrayList<>() : new ArrayList<>(cards);
        Collections.sort(results, buildSortComparator());
        applySearchResults(searchGeneration.incrementAndGet(), results);
    }
}

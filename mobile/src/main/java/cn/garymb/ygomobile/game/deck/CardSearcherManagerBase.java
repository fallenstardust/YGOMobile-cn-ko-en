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
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
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


/**
 * Deck-editor card searcher, base layer of the split inheritance
 * chain (CardSearcherManagerBase <- CardSearcherManager). Owns all
 * state fields, the SearchListener contract, the small accessors,
 * the asynchronous filter engine with its matcher / parser helpers,
 * the sort comparators, ClearSearch and the filter popup displays.
 * The original public constructor body lives here under the base
 * name so the final activity field keeps its finality.
 * <p>
 * 本类公开：搜索结果排序比较器（{@link #searchComparator(int)}）是卡组编辑器与
 * 卡查页、卡组编辑页搜索结果列共用的唯一实现，跨包调用需本类可访问。
 */
public abstract class CardSearcherManagerBase {

    protected static final String TAG = "CardSearcherManager";

    public interface SearchListener {
        void onSearchResultsUpdated(int count);

        /**
         * 禁限下拉当前选中项 id 变了（6=OCG、7=TCG、8=简体中文、10=OCG独有、11=TCG独有 这几档
         * 卡面要画赛制独有标识，其余档位不画）。默认空实现：只关心结果的调用方不必接管。
         */
        default void onAvailDisplayChanged(int availLm) {
        }
    }

    protected final Activity activity;
    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected final StringManager mStringManager = DataManager.get().getStringManager();
    protected SoundManager soundManager;

    protected final List<Card> searchResults = new ArrayList<>();
    protected SearchListener listener;

    protected TextView tvSearchResult;
    protected RecyclerView rvSearchResults;
    protected Spinner spinnerFilterType;
    protected Spinner spinnerFilterType2, spinnerFilterAttribute, spinnerFilterRace, spinnerFilterLimit;
    protected Spinner spinnerSortType;
    /**
     * 搜索结果列的展现模式切换键（行/宫格），与卡查页、卡组编辑页同一语义：
     * 图标始终表示当前模式，开关状态按本页设置项持久化
     */
    protected ImageButton btnResultViewMode;
    protected SimpleSpinnerAdapter attrAdapter, raceAdapter;
    protected EditText etAttack, etDefense, etStar, etScale, etKeyword;
    protected Button btnFilterEffect, btnFilterSearch, btnFilterClear, btnFilterMarks;

    protected DeckCardAdapter searchAdapter;
    protected LinkMarkerPopupWindow linkMarkerPopup;
    protected EffectCategoryPopupWindow effectCategoryPopup;

    protected Runnable pendingKeywordSearch;
    protected final AtomicInteger searchGeneration = new AtomicInteger(0);
    protected TextWatcher keywordTextWatcher;

    protected int filterType = 0;
    protected int filterType2 = 0;
    protected int filterAttrib = 0;
    protected int filterRace = 0;
    protected int filterAtkType = 0;
    protected int filterAtk = 0;
    protected int filterDefType = 0;
    protected int filterDef = 0;
    protected int filterLvType = 0;
    protected int filterLv = 0;
    protected int filterSclType = 0;
    protected int filterScl = 0;
    protected int filterLm = 0;
    protected long filterEffect = 0;
    protected int filterMarks = 0;

    protected LimitList mLimitList;

    protected CardSearcherManagerBase(Activity activity) {
        this.activity = activity;
    }

    // 从 DeckEditorManager 获取 SoundManager 引用
    public void setSoundManager(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    public void setLimitList(LimitList limitList) {
        this.mLimitList = limitList;
        if (searchAdapter != null) searchAdapter.setLimitList(mLimitList);
    }

    public void setCardSize(int w, int h) {
        if (searchAdapter != null) searchAdapter.setCardSize(w, h);
        // 单格卡宽随卡组网格定高回调变化，宫格列数要跟着复核
        applyResultLayout();
    }

    public void setDragState(int touchSlop, boolean disabled) {
        if (searchAdapter != null) searchAdapter.setDragState(touchSlop, disabled);
    }

    public RecyclerView getSearchRecyclerView() {
        return rvSearchResults;
    }

    // ==== 搜索结果列的展现模式（行/宫格）====

    /**
     * 按当前展现模式装配搜索结果列的布局管理器：布局管理器与列数变化都会 requestLayout，
     * 故 post 到本轮布局之外；宽或卡尺寸还没量出来时适配器先给 1 列兜底，
     * 等 {@link #setCardSize} 与下一次刷新再复核
     */
    public void applyResultLayout() {
        if (rvSearchResults == null || searchAdapter == null) return;
        rvSearchResults.post(() -> searchAdapter.applyListLayout(rvSearchResults));
    }

    /**
     * 展现模式切换键的入口：翻转行/宫格并成对换上布局管理器（条目布局与布局管理器不能错开），
     * 然后把图标与本页设置项同步。点击回调不在布局过程中，换 LM 可当场做
     */
    protected void toggleResultViewMode() {
        if (searchAdapter == null || rvSearchResults == null) return;
        boolean grid = searchAdapter.toggleListLayout(rvSearchResults);
        AppsSettings.get().saveIntSettings(Constants.PREF_DECK_EDITOR_RESULT_GRID, grid ? 1 : 0);
        refreshResultViewModeIcon();
    }

    /** 本页设置项里保存的展现模式（缺省列表），视图装配时读一次 */
    protected boolean loadResultViewMode() {
        return AppsSettings.get().getIntSettings(Constants.PREF_DECK_EDITOR_RESULT_GRID,
                Constants.PREF_DEF_RESULT_GRID) == 1;
    }

    /** 展现模式按钮的图标即当前模式：宫格→宫格图标，列表→列表图标 */
    protected void refreshResultViewModeIcon() {
        if (btnResultViewMode == null || searchAdapter == null) return;
        btnResultViewMode.setImageResource(searchAdapter.isGridLayout()
                ? R.drawable.ic_view_grid_24 : R.drawable.ic_view_list_24);
    }

    public DeckCardAdapter getSearchAdapter() {
        return searchAdapter;
    }

    public List<Card> getSearchResults() {
        return searchResults;
    }

    protected void setClickListener(Button btn, View.OnClickListener l) {
        if (btn != null) btn.setOnClickListener(l);
    }

    public int getSelectedSortType() {
        return spinnerSortType != null ? spinnerSortType.getSelectedItemPosition() : 0;
    }

    public int getSelectedFilterLimit() {
        return (int) SimpleSpinnerAdapter.getSelect(spinnerFilterLimit);
    }

    /**
     * 把禁限下拉当前选中项 id 作为赛制独有标识档位下发：搜索结果条目走
     * {@link DeckCardAdapter#setAvailLm(int)}，三个卡组网格经宿主的
     * {@link SearchListener#onAvailDisplayChanged(int)} 重画。
     * <p>
     * 这一路原先是断的：setupSpinners 的监听里只留了一句“availLm 更新由 DeckEditorManager 通过
     * onAvailDisplayChanged 回调处理”，而该回调从未定义、setAvailLm 也从未被任何人调用，
     * 于是选了 OCG/TCG/简体中文/OCG独有/TCG独有 也看不到卡底的独有标识（用户报的问题）
     */
    protected void applyAvailDisplay() {
        int availLm = getSelectedFilterLimit();
        if (searchAdapter != null) searchAdapter.setAvailLm(availLm);
        if (listener != null) listener.onAvailDisplayChanged(availLm);
    }


    /**
     * 结果计数格只显示统计数字（用户规格：不再带“搜索结果:”前缀），搜索中走省略号
     */
    protected void showSearchResultCount(int count) {
        if (tvSearchResult != null) tvSearchResult.setText(String.valueOf(count));
    }

    protected void showSearchResultSearching() {
        if (tvSearchResult != null) tvSearchResult.setText("…");
    }

    protected void executeFilterAsync() {
        showSearchResultSearching();
        int gen = searchGeneration.incrementAndGet();
        List<String> keywordTerms = parseKeywordTerms();
        new Thread(() -> {
            List<Card> results = new ArrayList<>();
            SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
            for (int i = 0; i < allCards.size(); i++) {
                if (searchGeneration.get() != gen) return;
                Card card = allCards.valueAt(i);
                if (card == null || Card.isType(card.Type, CardType.Token)) continue;
                if (!matchesAllFilters(card, keywordTerms)) continue;
                results.add(card);
            }
            Collections.sort(results, buildSortComparator());
            mainHandler.post(() -> applySearchResults(gen, results));
        }).start();
    }

    protected void applySearchResults(int gen, List<Card> results) {
        if (searchGeneration.get() != gen) return;
        searchResults.clear();
        // 卡名与关键词完全命中的卡一律置顶，且不随排序档位变化（用户规格：
        // 卡组管理页、卡查页、本页三处都要保持这个顺序）
        searchResults.addAll(pinKeywordMatches(results, getKeywordText()));
        showSearchResultCount(searchResults.size());
        if (searchAdapter != null) searchAdapter.setCards(searchResults);
        if (rvSearchResults != null) rvSearchResults.scrollToPosition(0);
        if (listener != null) listener.onSearchResultsUpdated(searchResults.size());
    }

    public Comparator<Card> buildSortComparator() {
        int sortSel = spinnerSortType != null ? spinnerSortType.getSelectedItemPosition() : 0;
        return makeComparator(sortSel);
    }

    /**
     * 卡组编辑搜索结果的默认排序（sortSel=0）：怪兽(通常→效果→仪式→融合→同调→超量→连接)→
     * 魔法→陷阱，各类内部按子类/等级。供关键词卡片列表等外部复用，保证两处排序逻辑同源、观感一致。
     */
    public static Comparator<Card> defaultSearchComparator() {
        return makeComparator(0);
    }

    /**
     * 按排序位序取比较器：sortSel 与编辑器搜索结果排序下拉的选中位序同义
     * （0=星数↑ / 1=攻击↑ / 2=守备↑ / 3=名称↓），公开给卡查页与卡组编辑页的
     * 搜索结果列表，使三处的规则切换共用同一套比较器而不各自实现。
     */
    public static Comparator<Card> searchComparator(int sortSel) {
        return makeComparator(sortSel);
    }

    /** 排序比较器统一实现：类秩(怪兽→魔法→陷阱)优先，再按 sortSel 解算子类/攻守/名称，末尾以卡码兜底 */
    protected static Comparator<Card> makeComparator(int sortSel) {
        return (a, b) -> {
            int classA = getCardClassRank(a), classB = getCardClassRank(b);
            if (classA != classB) return Integer.compare(classA, classB);
            int cmp = (classA == 0) ? compareMonsterBySortType(a, b, sortSel)
                    : Integer.compare(getCardSubTypeRank(a, classA), getCardSubTypeRank(b, classB));
            if (cmp == 0 && classA != 0 && sortSel == 3) {
                String na = a.Name != null ? a.Name : "", nb = b.Name != null ? b.Name : "";
                cmp = na.compareTo(nb);
            }
            return cmp != 0 ? cmp : Integer.compare(a.Code, b.Code);
        };
    }

    protected boolean matchesAllFilters(Card card, List<String> keywordTerms) {
        if (!matchesTypeFilter(card) || !matchesType2Filter(card)) return false;
        if (!matchesKeywordFilter(card, keywordTerms) || !matchesLimitFilter(card)) return false;

        if (filterEffect != 0 && (card.Category & filterEffect) == 0) return false;
        if (filterMarks != 0 && !((card.Defense & filterMarks) == filterMarks && Card.isType(card.Type, CardType.Link)))
            return false;
        if (filterType == 1) {
            if (filterAttrib != 0 && card.Attribute != filterAttrib) return false;
            if (filterRace != 0 && card.Race != filterRace) return false;
            if (filterAtkType != 0 && !matchesNumericFilter(card.Attack, filterAtkType, filterAtk))
                return false;
            if (filterDefType != 0) {
                if (Card.isType(card.Type, CardType.Link)) return false;
                if (!matchesNumericFilter(card.Defense, filterDefType, filterDef)) return false;
            }
            if (filterLvType != 0 && !matchesNumericFilter(card.getStar(), filterLvType, filterLv))
                return false;
            if (filterSclType != 0) {
                if (!Card.isType(card.Type, CardType.Pendulum)) return false;
                if (!matchesNumericFilter(card.LeftScale, filterSclType, filterScl)) return false;
            }
        }
        return true;
    }


    // === 对应 deck_con.cpp: ClearSearch ===
    public void clearSearch() {
        if (spinnerFilterType != null) spinnerFilterType.setSelection(0);
        if (spinnerFilterType2 != null) spinnerFilterType2.setSelection(0);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setSelection(0);
        if (spinnerFilterRace != null) spinnerFilterRace.setSelection(0);
        if (spinnerFilterLimit != null) spinnerFilterLimit.setSelection(0);
        clearEditTexts();
        filterEffect = 0;
        filterMarks = 0;
        filterType = 0;
        filterType2 = 0;
        filterAttrib = 0;
        filterRace = 0;
        filterLm = 0;
        filterAtkType = 0;
        filterAtk = 0;
        filterDefType = 0;
        filterDef = 0;
        filterLvType = 0;
        filterLv = 0;
        filterSclType = 0;
        filterScl = 0;
        updateFilterMarksDisplay();
        updateFilterEffectDisplay();
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) linkMarkerPopup.dismiss();
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing())
            effectCategoryPopup.dismiss();
        searchResults.clear();
        showSearchResultCount(0);
        if (searchAdapter != null) searchAdapter.setCards(searchResults);
        // 禁限下拉已被重置回“（无）”：独有标识也跟着收掉
        applyAvailDisplay();
        if (listener != null) listener.onSearchResultsUpdated(0);
    }

    protected void clearEditTexts() {
        if (pendingKeywordSearch != null) {
            mainHandler.removeCallbacks(pendingKeywordSearch);
            pendingKeywordSearch = null;
        }
        if (etKeyword != null && keywordTextWatcher != null) {
            etKeyword.removeTextChangedListener(keywordTextWatcher);
        }
        if (etAttack != null) etAttack.setText("");
        if (etDefense != null) etDefense.setText("");
        if (etStar != null) etStar.setText("");
        if (etScale != null) etScale.setText("");
        if (etKeyword != null) etKeyword.setText("");
        if (etKeyword != null && keywordTextWatcher != null) {
            etKeyword.addTextChangedListener(keywordTextWatcher);
        }
    }

    // === 对应 deck_con.cpp: SortList ===
    public void sortSearchResults() {
        Collections.sort(searchResults, buildSortComparator());
        // 重排后补回完全命中置顶：排序不得把同名卡押回普通位置（稳定分区，组内次序不变）
        List<Card> repinned = pinKeywordMatches(searchResults, getKeywordText());
        if (repinned != searchResults) {
            // 没命中时上面直接返的是入参本身，此时 clear 再 addAll 等于把列表清空，必须避开
            searchResults.clear();
            searchResults.addAll(repinned);
        }
    }

    /** 检索词输入框的当前原文，完全命中置顶的判定依据 */
    public String getKeywordText() {
        return etKeyword != null ? etKeyword.getText().toString() : "";
    }

    /**
     * 卡名与关键词完全命中的卡置顶（用户规格：三处搜索结果列、任何排序档位都保持）。
     * <p>
     * 判定为“卡名去空白后与检索词全等”（忽略大小写），多词检索时任一分词项全等也算命中；
     * 排除项 {@code -xx} 与串名项 {@code @系列} 不参与全等判定。
     * <p>
     * 实现上是个<b>稳定分区</b>：命中组与非命中组各自保持传入列表的次序，所以不会打乱
     * 已选定的排序档位，也不会因为重绘而抖动；命中为空或关键词为空时原样返回，不新建列表。
     * 命中组与非命中组拼成一个新列表返回，绝不改动入参（那是搜索线程或全卡表的产物）。
     */
    public static List<Card> pinKeywordMatches(List<Card> cards, String keyword) {
        if (cards == null || cards.isEmpty()) {
            return cards == null ? new ArrayList<>() : cards;
        }
        String key = keyword == null ? "" : keyword.trim();
        if (key.isEmpty()) {
            return cards;
        }
        // 整串 + 分词项都当候选关键词：单串时两者一致，多词时任一词全等也算命中
        List<String> terms = new ArrayList<>();
        terms.add(key.toLowerCase(Locale.US));
        terms.addAll(splitKeywordTerms(key));
        List<Card> hits = new ArrayList<>();
        List<Card> rest = new ArrayList<>(cards.size());
        for (Card card : cards) {
            if (isExactNameMatch(card, terms)) {
                hits.add(card);
            } else {
                rest.add(card);
            }
        }
        if (hits.isEmpty()) {
            return cards;
        }
        hits.addAll(rest);
        return hits;
    }

    private static boolean isExactNameMatch(Card card, List<String> terms) {
        if (card == null || card.Name == null) {
            return false;
        }
        String name = card.Name.trim().toLowerCase(Locale.US);
        if (name.isEmpty()) {
            return false;
        }
        for (String term : terms) {
            if (term.isEmpty() || term.startsWith("-") || term.startsWith("@")) continue;
            if (name.equals(term)) {
                return true;
            }
        }
        return false;
    }

    protected static int getCardClassRank(Card card) {
        return Card.isType(card.Type, CardType.Monster) ? 0 : Card.isType(card.Type, CardType.Spell) ? 1 : Card.isType(card.Type, CardType.Trap) ? 2 : 3;
    }

    protected static int getCardSubTypeRank(Card card, int classRank) {
        if (classRank == 0) {
            if (Card.isType(card.Type, CardType.Link)) return 6;
            if (Card.isType(card.Type, CardType.Xyz)) return 5;
            if (Card.isType(card.Type, CardType.Synchro)) return 4;
            if (Card.isType(card.Type, CardType.Fusion)) return 3;
            if (Card.isType(card.Type, CardType.Ritual)) return 2;
            if (Card.isType(card.Type, CardType.Normal)) return 0;
            return 1;
        }
        if (classRank == 1) {
            if (Card.isType(card.Type, CardType.Ritual)) return 1;
            if (Card.isType(card.Type, CardType.QuickPlay)) return 2;
            if (Card.isType(card.Type, CardType.Continuous)) return 3;
            if (Card.isType(card.Type, CardType.Equip)) return 4;
            if (Card.isType(card.Type, CardType.Field)) return 5;
            return 0;
        }
        if (classRank == 2) {
            if (Card.isType(card.Type, CardType.Continuous)) return 1;
            if (Card.isType(card.Type, CardType.Counter)) return 2;
            return 0;
        }
        return 0;
    }

    protected static int compareMonsterBySortType(Card a, Card b, int sortSel) {
        int subA = getCardSubTypeRank(a, 0), subB = getCardSubTypeRank(b, 0);
        if (sortSel == 3) {
            String na = a.Name != null ? a.Name : "", nb = b.Name != null ? b.Name : "";
            return na.compareTo(nb);
        }
        int priA = (sortSel == 1) ? b.Attack : (sortSel == 2) ? b.Defense : subA;
        int priB = (sortSel == 1) ? a.Attack : (sortSel == 2) ? a.Defense : subB;
        int cmp = Integer.compare(priA, priB);
        if (cmp != 0) return cmp;
        int secA = (sortSel == 1) ? b.Defense : (sortSel == 2) ? b.Attack : (b.Level & 0xff);
        int secB = (sortSel == 1) ? a.Defense : (sortSel == 2) ? a.Attack : (a.Level & 0xff);
        cmp = Integer.compare(secA, secB);
        if (cmp != 0) return cmp;
        int terA = (sortSel <= 2) ? (b.Level & 0xff) : b.Attack;
        int terB = (sortSel <= 2) ? (a.Level & 0xff) : a.Attack;
        cmp = Integer.compare(terA, terB);
        if (cmp != 0) return cmp;
        int qA = (sortSel <= 2) ? subA : b.Defense;
        int qB = (sortSel <= 2) ? subB : a.Defense;
        return Integer.compare(qA, qB);
    }

    protected boolean matchesTypeFilter(Card card) {
        switch (filterType) {
            case 1:
                return Card.isType(card.Type, CardType.Monster);
            case 2:
                return Card.isType(card.Type, CardType.Spell);
            case 3:
                return Card.isType(card.Type, CardType.Trap);
            default:
                return true;
        }
    }

    protected boolean matchesType2Filter(Card card) {
        if (filterType2 == 0) return true;
        if (filterType == 1) {
            return (card.Type & filterType2) == filterType2;
        }
        if (filterType == 2 || filterType == 3) {
            return card.Type == filterType2;
        }
        return true;
    }

    protected boolean matchesKeywordFilter(Card card, List<String> terms) {
        return matchesKeywordTerms(card, terms);
    }

    /**
     * 单卡关键词命中判定（与卡组编辑筛选同一实现，供决斗内检索复用）：空格分出的每一项
     * 都要命中；{@code -} 前缀项为排除，{@code @} 前缀项按串名（字段）取 setcode 命中，
     * 其余按卡名/描述包含与卡码全等命中。
     */
    public static boolean matchesKeywordTerms(Card card, List<String> terms) {
        if (terms.isEmpty()) return true;
        StringManager stringManager = DataManager.get().getStringManager();
        for (String term : terms) {
            boolean exclude = term.startsWith("-") && term.length() > 1;
            String body = exclude ? term.substring(1) : term;
            boolean matched = false;
            if (body.startsWith("@") && body.length() > 1) {
                String setName = body.substring(1);
                long setcode = stringManager.getSetCode(setName, true);
                if (setcode != 0 && card.isSetCode(setcode)) matched = true;
            } else {
                String searchText = body.isEmpty() ? term : body;
                if (card.Name != null && card.Name.toLowerCase().contains(searchText))
                    matched = true;
                if (card.Desc != null && card.Desc.toLowerCase().contains(searchText))
                    matched = true;
                if (String.valueOf(card.Code).equals(searchText)) matched = true;
            }
            if (exclude && matched) return false;
            if (!exclude && !matched) return false;
        }
        return true;
    }

    protected List<String> parseKeywordTerms() {
        return splitKeywordTerms(etKeyword != null ? etKeyword.getText().toString() : "");
    }

    /** 检索串按空格切分为关键词项（小写归一，与卡组编辑切词同一实现） */
    public static List<String> splitKeywordTerms(String raw) {
        List<String> terms = new ArrayList<>();
        if (raw == null) return terms;
        // 中文输入法在中文标点状态下会打出全角空格（U+3000），归一成分隔符后才能正常分词
        raw = raw.replace('\u3000', ' ').trim().toLowerCase();
        if (raw.isEmpty()) return terms;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                if (current.length() > 0) {
                    terms.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) terms.add(current.toString());
        return terms;
    }

    /**
     * 决斗内检索入口（停用聊天后聊天输入框转作关键词检索）：切词与命中判定复用卡组编辑
     * 实现，遍历全卡表（排除衍生物）返回命中集并按卡组编辑默认序排序；空查询返回空表。
     */
    public static List<Card> searchCardsByKeywords(String raw) {
        List<Card> results = new ArrayList<>();
        List<String> terms = splitKeywordTerms(raw);
        if (terms.isEmpty()) return results;
        SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
        if (allCards == null) return results;
        for (int i = 0; i < allCards.size(); i++) {
            Card card = allCards.valueAt(i);
            if (card == null || Card.isType(card.Type, CardType.Token)) continue;
            if (matchesKeywordTerms(card, terms)) results.add(card);
        }
        Collections.sort(results, defaultSearchComparator());
        return results;
    }

    protected int[] parseFilterType(String text) {
        if (text == null || (text = text.trim()).isEmpty()) return new int[]{0, 0};
        String op = "";
        for (String s : new String[]{">=", "<=", ">", "<", "="}) {
            if (text.startsWith(s)) {
                op = s;
                break;
            }
        }
        String numStr = op.isEmpty() ? text : text.substring(op.length());
        int type = parseOp(op);
        int val = parseNum(numStr);
        return val >= 0 ? new int[]{type, val} : new int[]{0, 0};
    }

    protected int parseOp(String op) {
        switch (op) {
            case "=":
                return 1;
            case ">":
                return 2;
            case ">=":
                return 3;
            case "<":
                return 4;
            case "<=":
                return 5;
            default:
                return 1;
        }
    }

    protected int parseNum(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    protected boolean matchesNumericFilter(int value, int filterType, int filterValue) {
        switch (filterType) {
            case 1:
                return value == filterValue;
            case 2:
                return value > filterValue;
            case 3:
                return value >= filterValue;
            case 4:
                return value < filterValue;
            case 5:
                return value <= filterValue;
            case 6:
                return value < 0;
            default:
                return true;
        }
    }

    protected boolean matchesLimitFilter(Card card) {
        if (filterLm == 0) return true;
        int code = (card.Alias > 0) ? card.Alias : card.Code;

        if (filterLm >= 1 && filterLm <= 3) {
            if (mLimitList == null) return false;
            LimitType type;
            if (filterLm == 1) type = LimitType.Forbidden;
            else if (filterLm == 2) type = LimitType.Limit;
            else type = LimitType.SemiLimit;
            return mLimitList.check(code, card.Alias, type);
        }

        if (filterLm == 100) {
            if (mLimitList == null || mLimitList.getCredits() == null) return false;
            boolean hasCredit = mLimitList.getCredits().containsKey(code);
            if (!hasCredit && card.Alias > 0) {
                hasCredit = mLimitList.getCredits().containsKey(card.Alias);
            }
            return hasCredit;
        }

        int ot = card.Ot;
        switch (filterLm) {
            case 6:
                return (ot & 0x1) != 0;
            case 7:
                return (ot & 0x2) != 0;
            case 8:
                return (ot & 0x8) != 0;
            case 9:
                return (ot & 0x4) != 0;
            case 10:
                return (ot & 0x1) != 0 && (ot & 0x2) == 0;
            case 11:
                return (ot & 0x2) != 0 && (ot & 0x1) == 0;
            case 12:
                return (ot & 0x1) != 0 && (ot & 0x2) != 0;
            default:
                return true;
        }
    }

    protected void showLinkMarkerPopup() {
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) {
            linkMarkerPopup.dismiss();
        }
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing()) {
            effectCategoryPopup.dismiss();
        }

        linkMarkerPopup = new LinkMarkerPopupWindow(activity, filterMarks, newFilterMarks -> {
            filterMarks = newFilterMarks;
            updateFilterMarksDisplay();
        });
        linkMarkerPopup.show(btnFilterMarks);
    }

    protected void updateFilterMarksDisplay() {
        if (btnFilterMarks == null) return;
        if (filterMarks != 0) {
            String[] arrows = {"↙", "↓", "↘", "←", "", "→", "↖", "↑", "↗"};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                if (i == 4) continue;
                if (((filterMarks >> i) & 1) == 1) {
                    sb.append(arrows[i]);
                }
            }
            btnFilterMarks.setText(sb.toString());
            btnFilterMarks.setBackground(activity.getDrawable(R.drawable.sbutton_p));
        } else {
            btnFilterMarks.setText(DataManager.get().getStringManager().getSystemString(1374, "连接标记"));
            btnFilterMarks.setBackground(activity.getDrawable(R.drawable.button3_bg));
        }
    }

    protected void showEffectCategoryPopup() {
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) {
            linkMarkerPopup.dismiss();
        }
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing()) {
            effectCategoryPopup.dismiss();
        }

        effectCategoryPopup = new EffectCategoryPopupWindow(activity, filterEffect, newFilterEffect -> {
            filterEffect = newFilterEffect;
            updateFilterEffectDisplay();
        });
        effectCategoryPopup.show(btnFilterEffect);
    }

    protected void updateFilterEffectDisplay() {
        if (btnFilterEffect == null) return;
        if (filterEffect != 0) {
            btnFilterEffect.setText("效果:*");
            btnFilterEffect.setBackgroundResource(R.drawable.sbutton_p);
        } else {
            btnFilterEffect.setText("效果分类");
            btnFilterEffect.setBackgroundResource(R.drawable.button3_bg);
        }
    }

    public void updateSearchResultCount() {
        showSearchResultCount(searchResults.size());
    }

    public void scrollToTop() {
        if (rvSearchResults != null) rvSearchResults.scrollToPosition(0);
    }

    public int getFilterMarks() {
        return filterMarks;
    }

    public long getFilterEffect() {
        return filterEffect;
    }

    public void dismissPopups() {
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) linkMarkerPopup.dismiss();
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing())
            effectCategoryPopup.dismiss();
    }
}

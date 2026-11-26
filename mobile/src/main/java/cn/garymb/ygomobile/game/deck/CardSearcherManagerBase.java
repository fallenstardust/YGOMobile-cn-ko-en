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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import ocgcore.PackManager;
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
     * 卡组编辑搜索结果的默认排序（sortSel=0）：怪兽按星数从大到小分段，同一数字上连接（LINK-X）
     * 在前、超量（阶级）其次、等级（星）在后，所以不会出现两个同号分段；再往下是魔法→陷阱。
     * 攻守与名称档不分卡种，见 {@link #makeComparator(int, boolean)}。
     * 段内再按子类/攻守。供关键词卡片列表等外部复用，保证两处排序逻辑同源、观感一致。
     */
    public static Comparator<Card> defaultSearchComparator() {
        return makeComparator(0);
    }

    /**
     * 按排序位序取比较器：sortSel 与搜索结果排序按钮的位序同义
     * （0=星数 / 1=攻击 / 2=守备 / 3=名称 / 4=时间），公开给卡查页与卡组管理页的
     * 搜索结果列表，使三处的规则切换共用同一套比较器而不各自实现。
     */
    public static Comparator<Card> searchComparator(int sortSel) {
        return makeComparator(sortSel, false);
    }

    /** 带方向的重载：reverse=true 时按该规则的反向排序（如 攻击↓ → 攻击↑） */
    public static Comparator<Card> searchComparator(int sortSel, boolean reverse) {
        return makeComparator(sortSel, reverse);
    }

    /** 排序位序：按卡包发售日期从早到晚（时间轴那几个包日期就是排序依据） */
    public static final int SORT_RULE_RELEASE_DATE = 4;

    /**
     * 排序比较器统一实现，末尾都以卡码兜底：
     * <ul>
     *     <li>星数档：类秩(怪兽→魔法→陷阱)优先，再按星数与分段家族；</li>
     *     <li>攻守档：攻/守数值就是主键，<b>不分卡种</b>（与电脑端 deck_con 的 SortList 同口径）；</li>
     *     <li>名称档：首字母段（# / 0 / A~Z）就是主键，同样不分卡种；</li>
     *     <li>时间档：按卡包发售日期，不分卡种。</li>
     * </ul>
     * 攻守与名称两档之所以不能先按卡种分组：搜索结果列的分割线段名就是数值区间/首字母，
     * 分组会让同段的卡不相邻，魔法与陷阱里那些数值相同的卡还会再切出一条同名分割线
     * （卡查页“按攻击排序时出现一堆只有一张的重复分割线”的根因）。
     * <p>
     * reverse=true 就是把该档正向的次序整体反过来：星数档的星数与同数字上的家族先后一起反
     * （星数↓ LINK→阶→星 变成 星数↑ 星→阶→LINK），攻守数值、名称段与段内次序、时间年份同样反向
     */
    protected static Comparator<Card> makeComparator(int sortSel) {
        return makeComparator(sortSel, false);
    }

    protected static Comparator<Card> makeComparator(int sortSel, boolean reverse) {
        if (sortSel == SORT_RULE_RELEASE_DATE) {
            final PackManager packManager = DataManager.get().getPackManager();
            return (a, b) -> {
                int dateA = packManager.getCardReleaseDate(a);
                int dateB = packManager.getCardReleaseDate(b);
                if (dateA == dateB) {
                    return Integer.compare(a.Code, b.Code);
                }
                // 没有卡包记录的卡（0）不论正反向都排在最后
                if (dateA == 0) return 1;
                if (dateB == 0) return -1;
                int cmp = Integer.compare(dateA, dateB);
                return reverse ? -cmp : cmp;
            };
        }
        if (sortSel == 1 || sortSel == 2) {
            return (a, b) -> {
                int cmp = compareByStat(a, b, sortSel, reverse);
                return cmp != 0 ? cmp : Integer.compare(a.Code, b.Code);
            };
        }
        if (sortSel == 3) {
            return (a, b) -> {
                int cmp = compareByName(a, b, reverse);
                return cmp != 0 ? cmp : Integer.compare(a.Code, b.Code);
            };
        }
        return (a, b) -> {
            int classA = getCardClassRank(a), classB = getCardClassRank(b);
            if (classA != classB) return Integer.compare(classA, classB);
            // 走到这里只剩星数档（攻守与名称档已经在上面分走，时间档更早返回）
            int cmp = (classA == 0) ? compareMonsterBySortType(a, b, sortSel, reverse)
                    : Integer.compare(getCardSubTypeRank(a, classA), getCardSubTypeRank(b, classB));
            return cmp != 0 ? cmp : Integer.compare(a.Code, b.Code);
        };
    }

    /**
     * 攻守档比较器：先比本档的数值（正向降序、反向升序），再比另一项数值、怪兽子类与星数。
     * 不分卡种，所以魔法/陷阱与同数值的怪兽落在同一条分割线里，段名不会重复
     */
    protected static int compareByStat(Card a, Card b, int sortSel, boolean reverse) {
        int mainA = sortSel == 1 ? a.Attack : a.Defense;
        int mainB = sortSel == 1 ? b.Attack : b.Defense;
        int cmp = reverse ? Integer.compare(mainA, mainB) : Integer.compare(mainB, mainA);
        if (cmp != 0) return cmp;
        int otherA = sortSel == 1 ? a.Defense : a.Attack;
        int otherB = sortSel == 1 ? b.Defense : b.Attack;
        cmp = reverse ? Integer.compare(otherA, otherB) : Integer.compare(otherB, otherA);
        if (cmp != 0) return cmp;
        int classA = getCardClassRank(a), classB = getCardClassRank(b);
        if (classA != classB) return Integer.compare(classA, classB);
        cmp = Integer.compare(getCardSubTypeRank(a, classA), getCardSubTypeRank(b, classB));
        if (cmp != 0) return cmp;
        return Integer.compare(b.getStar(), a.getStar());
    }

    /**
     * 名称档比较器：首字母段（# / 0 / A~Z）是主键，段内再按卡名本身比；
     * reverse=true 时段与段内次序一起反，就是正向的完整镜像
     */
    protected static int compareByName(Card a, Card b, boolean reverse) {
        String na = a.Name != null ? a.Name : "", nb = b.Name != null ? b.Name : "";
        int cmp = Character.compare(nameSectionKey(na), nameSectionKey(nb));
        if (cmp != 0) return reverse ? -cmp : cmp;
        cmp = na.compareTo(nb);
        return reverse ? -cmp : cmp;
    }

    /** 首字的拼音首字母缓存（GB2312 转码不便宜，全卡表排序会算很多次） */
    private static final Map<Character, Character> PINYIN_INITIALS = new ConcurrentHashMap<>();

    /**
     * GB2312 一级汉字里 23 个拼音分区的起始码（GB2312 没有以 I/U/V 开头的分区，所以是 23 不是 26）。
     * 逐个对应 {@link #PINYIN_SECTION_LETTERS}：0xB0A1=啊 0xB0C5=芭 0xB2C1=擦 0xB4EE=搭 0xB6EA=蛾
     * 0xB7A2=发 0xB8C1=噶 0xB9FE=哈 0xBBF7=击 0xBFA6=喀 0xC0AC=垃 0xC2E8=妈 0xC4C3=拿 0xC5B6=哦
     * 0xC5BE=啪 0xC6DA=期 0xC8BB=然 0xC8F6=撒 0xCBFA=tā段首字 0xCDDA=挖 0xCEF4=昔 0xD1B9=压 0xD4D1=匝。
     * 直接写成十六进制码值而不是汉字：源文件编码变动时这张表不会跟着错
     */
    private static final int[] PINYIN_SECTION_START = new int[]{
            0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE, 0xBBF7, 0xBFA6,
            0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA, 0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA,
            0xCEF4, 0xD1B9, 0xD4D1};

    /** 与 {@link #PINYIN_SECTION_START} 逐段对应的拼音首字母 */
    private static final char[] PINYIN_SECTION_LETTERS = "ABCDEFGHJKLMNOPQRSTWXYZ".toCharArray();

    /** 一级汉字最后一个字（座）的码值；二级字按部首而非拼音排，取不到首字母 */
    private static final int GB2312_LEVEL1_LAST = 0xD7F9;

    /**
     * 名称档这张卡属于哪个段：只用 {@code #}（符号与其他文字开头）、{@code 0}（数字开头）
     * 与 {@code A~Z}（英文首字母或汉字第一个字的拼音首字母）这三类段名（用户规格）。
     * 假名、谚文与 GB2312 收不到的汉字取不到拼音首字母，一律归 {@code #}
     */
    public static char nameSectionKey(String name) {
        if (name == null || name.isEmpty()) return '#';
        char c = name.charAt(0);
        if (c >= '0' && c <= '9') return '0';
        if (c >= 'A' && c <= 'Z') return c;
        if (c >= 'a' && c <= 'z') return (char) (c - 'a' + 'A');
        char initial = pinyinInitial(c);
        return initial == 0 ? '#' : initial;
    }

    /** 单个汉字的拼音首字母；不是 GB2312 一级汉字（含假名、谚文、符号）时返回 0 */
    private static char pinyinInitial(char c) {
        Character cached = PINYIN_INITIALS.get(c);
        if (cached != null) return cached.charValue();
        char letter = lookupPinyinInitial(c);
        PINYIN_INITIALS.put(c, letter);
        return letter;
    }

    private static char lookupPinyinInitial(char c) {
        int code = gb2312Code(c);
        // 不在一级字区（0xB0A1~0xD7F9）的字不能按拼音分区查
        if (code < PINYIN_SECTION_START[0] || code > GB2312_LEVEL1_LAST) return 0;
        for (int i = PINYIN_SECTION_START.length - 1; i >= 0; i--) {
            if (code >= PINYIN_SECTION_START[i]) {
                return i < PINYIN_SECTION_LETTERS.length ? PINYIN_SECTION_LETTERS[i] : 'Z';
            }
        }
        return 0;
    }

    /** 字符的 GB2312 双字节码值（无符号）；ASCII、符号区与转不了的字符都返回 0 */
    private static int gb2312Code(char c) {
        byte[] bytes;
        try {
            bytes = String.valueOf(c).getBytes("GB2312");
        } catch (Exception e) {
            return 0;
        }
        // 不在 GB2312 里的字符会被转成单个 '?'
        if (bytes.length < 2) return 0;
        int high = bytes[0] & 0xFF;
        int low = bytes[1] & 0xFF;
        // 0xA1~0xAF 是符号与序号区，不是汉字
        if (high < 0xB0) return 0;
        return high * 256 + low;
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

    /**
     * 列表开头属于“完全命中置顶组”的张数（与 {@link #pinKeywordMatches} 完全同口径）。
     * <p>
     * 置顶组是拼在结果最前的一段连续条目，分段说明需要把这一段与本体分开：否则置顶的那张卡
     * 会在本体里再出一次自己的分段，分割线出现两个同号段（如两次“6星”）。关键词为空或首张不命中返回 0。
     */
    public static int countLeadingKeywordMatches(List<Card> cards, String keyword) {
        String key = keyword == null ? "" : keyword.trim();
        if (cards == null || cards.isEmpty() || key.isEmpty()) {
            return 0;
        }
        List<String> terms = new ArrayList<>();
        terms.add(key.toLowerCase(Locale.US));
        terms.addAll(splitKeywordTerms(key));
        int count = 0;
        while (count < cards.size() && isExactNameMatch(cards.get(count), terms)) {
            count++;
        }
        return count;
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

    /** 星数档分段家族：连接（按连接数，星数↓时排在该数字最前） */
    public static final int STAR_SECTION_LINK = 0;
    /** 星数档分段家族：超量（按阶级） */
    public static final int STAR_SECTION_XYZ = 1;
    /** 星数档分段家族：其他怪兽（按等级，排在该数字最后） */
    public static final int STAR_SECTION_LEVEL = 2;

    /**
     * 星数档（sortSel=0）这张卡属于哪个分段家族，取值 {@link #STAR_SECTION_LINK} /
     * {@link #STAR_SECTION_XYZ} / {@link #STAR_SECTION_LEVEL}。
     * <p>
     * 这个值是星数相同时的次键（用户规格：星数↓ 时 LINK-6→6阶→6星），所以连接在前、
     * 超量其次、等级在后；星数↑ 则是它的完整镜像（6星→6阶→LINK-6）。
     * 卡查页与卡组管理页的分割线文字也按它取“LINK/阶/星”口径，与排序键同源。
     */
    public static int getStarSectionFamily(Card card) {
        if (Card.isType(card.Type, CardType.Xyz)) return STAR_SECTION_XYZ;
        if (Card.isType(card.Type, CardType.Link)) return STAR_SECTION_LINK;
        return STAR_SECTION_LEVEL;
    }

    protected static int compareMonsterBySortType(Card a, Card b, int sortSel, boolean reverse) {
        int subA = getCardSubTypeRank(a, 0), subB = getCardSubTypeRank(b, 0);
        if (sortSel == 3) {
            String na = a.Name != null ? a.Name : "", nb = b.Name != null ? b.Name : "";
            int cmp = na.compareTo(nb);
            return reverse ? -cmp : cmp;
        }
        if (sortSel == 0) {
            // 星数档：主键是星数本身（星数↓时 13 在前、星数↑时 1 在前），同一个数字上再按
            // LINK→阶→星 先后排（星数↓：LINK-6→6阶→6星）；反向就是这套次序的完整镜像，
            // 这样每个段名在整列里只出现一次（分割线不会重复）
            int cmp = reverse ? Integer.compare(a.getStar(), b.getStar()) : Integer.compare(b.getStar(), a.getStar());
            if (cmp != 0) return cmp;
            int famA = getStarSectionFamily(a), famB = getStarSectionFamily(b);
            cmp = reverse ? Integer.compare(famB, famA) : Integer.compare(famA, famB);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(subA, subB);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.Attack, a.Attack);
            if (cmp != 0) return cmp;
            return Integer.compare(b.Defense, a.Defense);
        }
        // 主键：攻击/守备档直接比数值（缺省降序），其余档位按子类在前、星数在次键
        int priA, priB;
        if (sortSel == 1) {
            priA = reverse ? a.Attack : b.Attack;
            priB = reverse ? b.Attack : a.Attack;
        } else if (sortSel == 2) {
            priA = reverse ? a.Defense : b.Defense;
            priB = reverse ? b.Defense : a.Defense;
        } else {
            priA = subA;
            priB = subB;
        }
        int cmp = Integer.compare(priA, priB);
        if (cmp != 0) return cmp;
        int secA, secB;
        if (sortSel == 1) {
            secA = b.Defense;
            secB = a.Defense;
        } else if (sortSel == 2) {
            secA = b.Attack;
            secB = a.Attack;
        } else {
            // 兜底档位的星数比较随反向翻转，其余兜底键不变
            secA = reverse ? (a.Level & 0xff) : (b.Level & 0xff);
            secB = reverse ? (b.Level & 0xff) : (a.Level & 0xff);
        }
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

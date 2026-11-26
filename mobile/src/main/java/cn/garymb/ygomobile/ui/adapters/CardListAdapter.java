package cn.garymb.ygomobile.ui.adapters;

import static cn.garymb.ygomobile.Constants.ASSET_ATTR_RACE;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.tubb.smrv.SwipeHorizontalMenuLayout;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.game.deck.CardSearcherManagerBase;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.CardListProvider;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.widget.CardGridInfoView;
import cn.garymb.ygomobile.ui.widget.SectionDividerDecoration;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.CardUtils;
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

public class CardListAdapter extends BaseRecyclerAdapterPlus<Card, BaseViewHolder> implements CardListProvider,
        SectionDividerDecoration.SectionTitleProvider {
    private final StringManager mStringManager;
    private ImageTop mImageTop;
    private TextView tv_limit_num;
    private LimitList mLimitList;//同时只能适用一种禁卡表，所以不管是常规禁限还是genesys禁限，只能使用一个
    private boolean mItemBg;
    private final ImageLoader imageLoader;
    private boolean mEnableSwipe = false;
    private String mSearchKeyword = null;

    public CardListAdapter(Context context, ImageLoader imageLoader) {
        super(context, R.layout.item_search_card);
        this.imageLoader = imageLoader;
        mStringManager = DataManager.get().getStringManager();
    }

    /**
     * 条目布局按宫格标志选：行模式用 item_search_card（卡图 + 卡名/属性/攻守等文字），
     * 宫格模式用 item_search_card_grid（只有卡图与左上角角标）；
     * 布局一律按 viewType 选，与 {@link #getDefItemViewType} 同一口径，不在这里读 mGrid
     * （mGrid 可能在条目创建与绑定之间被切换）；
     * 返回类型写全限定名：本类体内简名 BaseViewHolder 被
     * {@link BaseRecyclerAdapterPlus.BaseViewHolder} 遮蔽，与父类签名不匹配
     */
    @Override
    protected com.chad.library.adapter.base.viewholder.BaseViewHolder onCreateDefViewHolder(
            ViewGroup parent, int viewType) {
        com.chad.library.adapter.base.viewholder.BaseViewHolder holder = createBaseViewHolder(parent,
                viewType == VIEW_TYPE_GRID ? R.layout.item_search_card_grid : R.layout.item_search_card);
        if (viewType == VIEW_TYPE_GRID) {
            // 卡查页与卡组编辑页的宫格数字沿用原来的 8sp（用户规格：这两页文字大小保持不变，
            // 放大只给决斗内卡组编辑器），行高与图标仍按卡宽等比走
            CardGridInfoView info = holder.getView(R.id.card_grid_info);
            if (info != null) {
                info.setFixedTextSizeSp(GRID_TEXT_SP_ORIGINAL);
            }
        }
        return holder;
    }

    /** 宫格两行数字的固定字号（sp），与列表态与改造前的宫格一致 */
    private static final float GRID_TEXT_SP_ORIGINAL = 8f;

    /**
     * 行/宫格两种条目布局各自的 viewType（不得与父类的 HEADER/FOOTER/EMPTY/LOAD_MORE 同值）：
     * RecyclerView 的复用池与缓存队列都按 viewType 分桶，两态共用一个 viewType 时，
     * 切换模式后旧布局的条目会被当成新模式直接复用，而两种布局的控件树完全不同——
     * 宫格态会拿到 item_search_card（自带 list_item_bg 背景与整行高度、且带左右外边距），
     * 于是卡图被拉成一整行、背景残留，宫格排列散乱（用户报的问题）
     */
    private static final int VIEW_TYPE_LIST = 1;
    private static final int VIEW_TYPE_GRID = 2;

    /** 父类 {@code getItemViewType} 内部路由到本方法（头/尾/空布局的类型由父类自己占用） */
    @Override
    protected int getDefItemViewType(int position) {
        return mGrid ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
    }

    public void setSearchKeyword(String keyword) {
        mSearchKeyword = keyword;
    }

    public String getSearchKeyword() {
        return mSearchKeyword;
    }

    @Override
    public int getCardsCount() {
        return getItemCount();
    }

    @Override
    public Card getCard(int posotion) {
        return getItem(posotion);
    }

    public boolean isShowMenu(View view) {
        if (view != null) {
            Object tag = view.getTag(view.getId());
            if (tag != null && tag instanceof ViewHolder) {
                ViewHolder viewHolder = (ViewHolder) tag;
                return viewHolder.mMenuLayout.isMenuOpen();
            }
        }
        return false;
    }

    // === 搜索结果两种展现（行模式：卡图 + 文字信息；宫格模式：只卡图与禁限/起源点角标）===

    /** 当前是否宫格展现；缺省 false（列表），三个页面各自在初始化时按本页设置项调 {@link #setGridLayout} */
    private boolean mGrid;
    /** 宫格单列最小宽（px），列数 = 列表可用宽 / 该值；取行模式的卡图宽 card_small_width 保证两处观感一致 */
    private int mGridMinCell;
    /**
     * 宫格固定列数；0 = 按可用宽自适应（卡查页）。
     * 卡组管理页的搜索结果固定每行 4 个（用户规格），不随抽屉宽窄浮动
     */
    private int mGridSpanCount;
    /** 当前宫格列数（applyListLayout 写入）；分段断行与分段末行收宽度都要用 */
    private int mGridSpan = 1;
    /**
     * 宫格单格内卡图的固定宽度（px，applyListLayout 写入 = 单列宽 - 条目左右 padding）：
     * 段末那张卡为了断行会跨多列，卡图必须按这个固定宽度绘制才能与其他卡完全同尺寸
     *（百分比 1/span 相对跨列格子的内宽计算，会多出几个 dp，用户反馈仍偏大）
     */
    private int mGridCellImageWidth;

    /**
     * 切换搜索结果列的展现形式（宫格/行）。本方法只改标志并重建 holder：标志一变
     * {@link #getDefItemViewType} 跟着换档，旧布局的条目因此不会被新模式复用（两种条目布局的
     * 控件树完全不同）；布局管理器要另外调 {@link #applyListLayout(RecyclerView)}
     * 或直接用 {@link #toggleListLayout(RecyclerView)}
     */
    public void setGridLayout(boolean grid) {
        if (mGrid == grid) return;
        mGrid = grid;
        notifyDataSetChanged();
    }

    public boolean isGridLayout() {
        return mGrid;
    }

    /**
     * 设定宫格固定列数（传 0 恢复“按可用宽自适应”）。
     * 已挂着的 GridLayoutManager 要等下一次 {@link #applyListLayout(RecyclerView)} 才换列数
     */
    public void setGridSpanCount(int spanCount) {
        mGridSpanCount = Math.max(0, spanCount);
    }

    /**
     * 按当前展现模式装配布局管理器：宫格 = GridLayoutManager（列数默认按可用宽 / 单列最小宽
     * 自适应，页面可用 {@link #setGridSpanCount(int)} 改成固定列数；宽度还没量出来时先给
     * 1 列，下一轮填结果时再复核）；列表 = 原来的 {@link FastScrollLinearLayoutManager}
     * （带右侧快速滚动条）。已挂对类型的则只改列数，不重建布局管理器（避免滚动位置被摸清）。
     * 列数变化会 requestLayout，所以下游页面要在布局之外（post）调用；单格尺寸不在此计算：
     * 宫格条目宽高比由 item_search_card_grid 的 dimensionRatio 派生，列宽变化自动跟随
     */
    public void applyListLayout(RecyclerView list) {
        if (list == null) return;
        RecyclerView.LayoutManager lm = list.getLayoutManager();
        if (!mGrid) {
            // GridLayoutManager 也是 LinearLayoutManager 的子类，判断列表态时必须先排除宫格
            boolean linear = lm instanceof LinearLayoutManager && !(lm instanceof GridLayoutManager);
            if (!linear) {
                list.setLayoutManager(new FastScrollLinearLayoutManager(list.getContext()));
            }
            return;
        }
        int span;
        if (mGridSpanCount > 0) {
            // 页面指定了固定列数（卡组管理页每行 4 个）：不踩宽度，免得抽屉一宽就多出一列
            span = mGridSpanCount;
        } else {
            if (mGridMinCell <= 0) {
                mGridMinCell = context.getResources().getDimensionPixelSize(R.dimen.card_small_width);
            }
            int avail = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
            span = avail > 0 ? Math.max(1, avail / mGridMinCell) : 1;
        }
        boolean gridMetricsChanged = false;
        if (mGridSpan != span) {
            mGridSpan = span;
            // 分段断行与末行收宽都靠列数算，列数变了要让已绑定的格子按新跨度重画
            gridMetricsChanged = true;
        }
        // 段末卡的卡图固定为单列宽（条目左右各 2dp padding）：只按宽度比例收会在跨列格子的
        // 内宽上算百分比，结果比普通格子的卡图大几个像素，用户仍能看到最后一张卡偏大
        int avail = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        int cellPad = (int) (2 * context.getResources().getDisplayMetrics().density + 0.5f);
        int cellImageWidth = avail > 0 ? Math.max(0, avail / span - cellPad * 2) : 0;
        if (mGridCellImageWidth != cellImageWidth) {
            mGridCellImageWidth = cellImageWidth;
            gridMetricsChanged = true;
        }
        if (gridMetricsChanged) {
            notifyDataSetChanged();
        }
        if (lm instanceof GridLayoutManager) {
            GridLayoutManager grid = (GridLayoutManager) lm;
            if (grid.getSpanCount() != span) {
                grid.setSpanCount(span);
            }
            ensureSectionSpanLookup(grid);
        } else {
            GridLayoutManager grid = new GridLayoutManager(list.getContext(), span);
            ensureSectionSpanLookup(grid);
            list.setLayoutManager(grid);
        }
    }

    /** 宫格必备：每段最后一张占满本行剩余列，下一段必定从新的一行开始 */
    private void ensureSectionSpanLookup(GridLayoutManager grid) {
        if (!(grid.getSpanSizeLookup() instanceof SectionSpanSizeLookup)) {
            grid.setSpanSizeLookup(new SectionSpanSizeLookup(this));
        }
    }

    /** 宫格分段断行的跨度计算器（列表模式不用） */
    private static final class SectionSpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
        private final CardListAdapter mAdapter;

        SectionSpanSizeLookup(CardListAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public int getSpanSize(int position) {
            return mAdapter.spanSizeAt(position);
        }
    }

    /**
     * 宫格里这张卡占几列：本段最后一张占满本行剩余列（宽格子里只画一张正常宽度的卡），
     * 其余每张 1 列。非宫格、没有分段、或列数为 1 时都当 1 列
     */
    private int spanSizeAt(int position) {
        if (!mGrid || mGridSpan <= 1 || position < 0 || position >= mSectionKeys.size()) {
            return 1;
        }
        String key = mSectionKeys.get(position);
        if (key == null) {
            return 1;
        }
        // 下一张还属于同一段 → 不是本段最后一张
        if (position + 1 < mSectionKeys.size() && key.equals(mSectionKeys.get(position + 1))) {
            return 1;
        }
        int column = (position - getSectionStart(position)) % mGridSpan;
        return mGridSpan - column;
    }

    /** 该位置所属分段的起点位置（无分段时就是它自己） */
    private int getSectionStart(int position) {
        if (position < 0 || position >= mSectionHeads.size()) {
            return position;
        }
        return mSectionHeads.get(position);
    }

    /**
     * 顶部展现模式按钮的入口：翻转列表/宫格两态并成对换上对应的布局管理器
     * （条目布局与布局管理器不能错开，否则宫格布局配线性 LM 会把卡图拉成满宽）
     *
     * @return 切换后的模式（true = 宫格），页面据此刷新按钮图标并写入本页设置项
     */
    public boolean toggleListLayout(RecyclerView list) {
        setGridLayout(!mGrid);
        applyListLayout(list);
        return mGrid;
    }

    /**
     * 星数档（排序按钮“星数↓”，再点一次变“星数↑”）：卡查页与卡组管理页都按
     * “星数 + {@link CardSearcherManagerBase#getStarSectionFamily 分段家族}”排，
     * 而不是直接沿用搜索线程交来的序列，以保证同一个星数段在整列里只出现一次。
     * 置顶不靠这里，一律由 {@link CardSearcherManagerBase#pinKeywordMatches} 在任何档位后统一保证。
     * 决斗内卡组编辑器不读这里，它用自己的档位（{@link CardSearcherManagerBase} 的比较器）
     */
    public static final int SORT_RULE_ORIGINAL = 0;

    /**
     * 搜索线程交来的原始顺序（未套本页规则）：规则按钮重排时始终以它为底（并经发售日期过滤），
     * 而不是一档一档在已排过的结果上叠加，切回任何一档都回到该档自己的确定次序
     */
    private final List<Card> mRawCards = new ArrayList<>();

    /**
     * 发售日期过滤范围（yyyyMMdd，两端都含）；为 0 表示不启用过滤。
     * 由卡查页与卡组管理页顶部的时间轴写入，只作用于本适配器当前展示的搜索结果
     */
    private int mFilterStartDate;
    private int mFilterEndDate;

    /** 当前排序是否按规则反向（重复点已选中的排序按钮切换），由页面写入，下次 applySortRule 生效 */
    private boolean mSortReversed;

    /**
     * 分段结构（跟随当前排序规则与方向重算）：{@link #mSectionCards} 是排好序、含已收起段的
     * 完整结果，适配器真正展示的是其中未收起的段。每个可见位置都有自己的段名键（null = 本档不分段）
     * 与所属段的首位置（宫格断行算列号、分割线留高都用它）
     */
    private final List<Card> mSectionCards = new ArrayList<>();
    private final List<String> mSectionKeys = new ArrayList<>();
    private final List<Integer> mSectionHeads = new ArrayList<>();
    /** 段首位置 → 该行上方要画的分割带（自上而下，先排紧随其上的已收起段，末尾是本段自己） */
    private final SparseArray<List<SectionDividerDecoration.SectionBand>> mTopBands = new SparseArray<>();
    /** 末尾整段被收起时挂在最后一个可见条目下方的分割带（一条条目都没有时也用它从顶部画） */
    private List<SectionDividerDecoration.SectionBand> mTailBands;
    /** 已收起的分段（集合里存的是不含张数的段名键） */
    private final Set<String> mCollapsedSections = new HashSet<>();
    /** 当前排序档位：点分割带折叠/展开时要按同一档位重算段名，不能让页面再传一次 */
    private int mSortSel = SORT_RULE_ORIGINAL;

    /**
     * 记住原始顺序；各页面在拿到搜索结果时先调本方法，再调 {@link #applySortRule(int)}。
     * 新一批结果进来会把上一轮的折叠状态收掉（段名键与旧结果无关，全部展开才符合预期）
     */
    public void setRawCards(List<Card> cards) {
        mRawCards.clear();
        if (cards != null) {
            mRawCards.addAll(cards);
        }
        mCollapsedSections.clear();
    }

    /**
     * 设定按发售日期过滤的范围（yyyyMMdd，两端都含）。页面时间轴两端都拉到头尾时
     * 应该改调 {@link #clearReleaseDateFilter()}：全范围等于“不按日期挑卡”，
     * 未收录发售日期的卡（不在任何卡包 ydk 里）那时也一并保留
     */
    public void setReleaseDateFilter(int startDate, int endDate) {
        mFilterStartDate = startDate;
        mFilterEndDate = endDate;
    }

    /** 取消发售日期过滤，恢复完整结果 */
    public void clearReleaseDateFilter() {
        mFilterStartDate = 0;
        mFilterEndDate = 0;
    }

    private boolean hasReleaseDateFilter() {
        return mFilterStartDate > 0 && mFilterEndDate >= mFilterStartDate;
    }

    /**
     * 只留发售日期落在过滤范围内的卡。发售日期取卡包管理器记录的最早一次发售，
     * xxxx-misc 这类只知道年份的卡按“范围与该年份有交集”判定；
     * 过滤启用时，没有任何卡包记录的卡同样不保留（它们没有日期可比）
     */
    private List<Card> filterByReleaseDate(List<Card> cards) {
        if (!hasReleaseDateFilter() || cards == null || cards.isEmpty()) {
            return cards;
        }
        PackManager packManager = DataManager.get().getPackManager();
        List<Card> kept = new ArrayList<>(cards.size());
        for (Card card : cards) {
            if (packManager.isCardReleasedBetween(card, mFilterStartDate, mFilterEndDate)) {
                kept.add(card);
            }
        }
        return kept;
    }

    /**
     * 按本页当前选中的规则档位把原始顺序填入适配器：五个档位都走 {@link #sortByRule}
     * 同一套比较器（星数档的正反向都按“星数 + 分段家族”排，见
     * {@link CardSearcherManagerBase#getStarSectionFamily}，这样每个星数段在整列里只出现一次）；
     * 无论哪一档，卡名与检索词完全命中的卡都排到最前（用户规格：置顶不随排序档位变）。
     * 本方法自己 notifyDataSetChanged，调用方不必再设数据
     */
    public void applySortRule(int sortSel) {
        // 换了规则档位就把折叠状态清掉：段名键是按档位算的（星数/攻守区间/首字母/年份），
        // 把上一档的键带到新档只会莫名盖住几段；同一档位内切正反向则保持折叠结果
        if (sortSel != mSortSel) {
            mCollapsedSections.clear();
        }
        mSortSel = sortSel;
        List<Card> filtered = filterByReleaseDate(mRawCards);
        List<Card> ordered = sortByRule(filtered, sortSel, mSortReversed);
        mSectionCards.clear();
        // 置顶靠稳定分区完成：命中组与非命中组各自保持上面排好的次序，不会打乱档位
        mSectionCards.addAll(CardSearcherManagerBase.pinKeywordMatches(ordered, mSearchKeyword));
        rebuildSections(false);
    }

    /** 分段完整结果条数（含已收起的段）；顶部计数用它在折叠前后都不变 */
    public int getTotalCardCount() {
        return mSectionCards.size();
    }

    /** 该位置所属分段首位置上方要画的分割带（同段内任意位置问出来都同一份） */
    @Override
    public List<SectionDividerDecoration.SectionBand> getTopBands(int position) {
        int head = getSectionHead(position);
        if (head < 0) {
            return null;
        }
        return mTopBands.get(head);
    }

    /** 该位置下方要画的分割带：只有末尾几段整段被收起时挂在最后一个可见条目下面 */
    @Override
    public List<SectionDividerDecoration.SectionBand> getBottomBands(int position) {
        if (mTailBands == null || mTailBands.isEmpty() || position != mSectionKeys.size() - 1) {
            return null;
        }
        return mTailBands;
    }

    /** 该位置所属分段的首位置（适配器数据里的下标）；越界或不分段返回 -1 */
    @Override
    public int getSectionHead(int position) {
        if (position < 0 || position >= mSectionHeads.size()) {
            return -1;
        }
        return mSectionHeads.get(position);
    }

    /** 全部分段都被收起时没有可见条目，分割带改从列表顶部画起，否则再也没法点开 */
    @Override
    public List<SectionDividerDecoration.SectionBand> getEmptyBands() {
        if (!mSectionKeys.isEmpty() || mTailBands == null || mTailBands.isEmpty()) {
            return null;
        }
        return mTailBands;
    }

    /**
     * 点分割带的回调（{@link SectionDividerDecoration} 的手势识别到后调过来）：
     * 翻转该段的折叠状态并重建可见条目与分割带。段名按不含张数的键存，
     * 所以同一档正反向切换、张数变化都仍然对得上
     */
    @Override
    public void toggleSection(String sectionKey) {
        if (sectionKey == null || sectionKey.isEmpty()) {
            return;
        }
        if (!mCollapsedSections.remove(sectionKey)) {
            mCollapsedSections.add(sectionKey);
        }
        // 折叠与展开都走增量通知，ItemAnimator 才能播增删动画（用户规格：要有快速的过渡动画）
        rebuildSections(true);
    }

    /**
     * 按当前排序规则给结果分段并算出分割带：相邻两卡的段名键不同即新一段，与排序方向无关。
     * 段说明末尾带上本段卡片张数（单位随语言的量词，见 section_title_with_count）
     * <p>
     * 开头的“完全命中置顶组”整段用检索词本身当段名：这几张卡被稳定分区提到了最前，
     * 若继续按卡自身属性起名，就会在本体同星数/同攻守那一段之外再画出一条同名分割段。
     * <p>
     * 被收起的段不交出可见条目，它的分割带累积起来挂到下一个可见段首行的上方（自上而下），
     * 末尾没有可见段时挂在最后一个可见条目下方（一个可见条目都没有时交给 {@link #getEmptyBands()}）。
     *
     * @param animate 本次重建是否来自折叠/展开：是则发精确的区间通知让 ItemAnimator 播动画
     */
    private void rebuildSections(boolean animate) {
        // 旧可见条目要留着算增量：折叠/展开前后的两段差集就是本次要动画的区间
        List<Card> previous = new ArrayList<>(getData());
        mSectionKeys.clear();
        mSectionHeads.clear();
        mTopBands.clear();
        mTailBands = null;
        int size = mSectionCards.size();
        // 置顶组就是开头的连续 pinnedCount 张（稳定分区保证），按张数分段而不是逐张判定，
        // 免得在整列结果上重复算一遍分词与全等
        int pinnedCount = CardSearcherManagerBase.countLeadingKeywordMatches(mSectionCards, mSearchKeyword);
        String pinnedKey = pinnedCount > 0 ? mSearchKeyword.trim() : null;
        // 第一遍：每张卡的段名键（不含张数）
        List<String> keys = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            keys.add(pinnedKey != null && i < pinnedCount
                    ? pinnedKey : sectionTitleOf(mSectionCards.get(i), mSortSel));
        }
        // 第二遍：切段（段名键 + 起始下标 + 张数），再按段的折叠状态生成分割带与可见条目
        List<SectionDividerDecoration.SectionBand> pending = null;
        List<Card> display = new ArrayList<>(size);
        int i = 0;
        while (i < size) {
            String key = keys.get(i);
            int end = i + 1;
            while (end < size && sameTitle(keys.get(end), key)) {
                end++;
            }
            int count = end - i;
            if (key != null && mCollapsedSections.contains(key)) {
                // 整段收起：不留条目，只把分割带（箭头朝上）攒着等下一段或末尾画
                pending = appendBand(pending, new SectionDividerDecoration.SectionBand(
                        key, sectionDisplayTitle(key, count), true));
                i = end;
                continue;
            }
            int head = display.size();
            for (int k = i; k < end; k++) {
                display.add(mSectionCards.get(k));
                mSectionKeys.add(key);
                mSectionHeads.add(head);
            }
            // 段首行上方的分割带：先前面被收起的段（保持原有上下顺序），再本段自己
            List<SectionDividerDecoration.SectionBand> bands =
                    pending == null ? new ArrayList<>(1) : new ArrayList<>(pending);
            pending = null;
            if (key != null) {
                bands.add(new SectionDividerDecoration.SectionBand(
                        key, sectionDisplayTitle(key, count), false));
                mTopBands.put(head, bands);
            } else if (!bands.isEmpty()) {
                // 本档不分段（理论上不会走到，防一手）：已收起段的带子仍要能画出来
                mTopBands.put(head, bands);
            }
            i = end;
        }
        mTailBands = pending;
        applyDisplay(previous, display, animate);
    }

    /**
     * 把新算出的可见条目写回适配器：
     * <ul>
     *     <li>animate=true（只用于点分割带折叠/展开）：先用公共前缀与公共后缀夹出本次真正变动的
     *     一段，再发 notifyItemRangeRemoved / notifyItemRangeInserted，条目才能被 ItemAnimator
     *     淡出/淡入，而不是整表瞬间重排（用户反馈“太僵硬”的根因就是走了 notifyDataSetChanged）；</li>
     *     <li>animate=false（新一批结果、换排序规则或方向）：整表刷新，不播入场动画。</li>
     * </ul>
     * 折叠/展开只会让某一段整段消失或整段回来，所以夹出来的一定是一段连续区间，
     * 与数据变化严格一致（不一致会让 RecyclerView 直接抛 inconsistent 异常）
     */
    private void applyDisplay(List<Card> previous, List<Card> next, boolean animate) {
        List<Card> data = getData();
        data.clear();
        data.addAll(next);
        if (!animate) {
            notifyDataSetChanged();
            return;
        }
        int oldSize = previous.size();
        int newSize = next.size();
        int first = 0;
        while (first < oldSize && first < newSize && previous.get(first) == next.get(first)) {
            first++;
        }
        int lastOld = oldSize - 1;
        int lastNew = newSize - 1;
        while (lastOld >= first && lastNew >= first && previous.get(lastOld) == next.get(lastNew)) {
            lastOld--;
            lastNew--;
        }
        int removed = lastOld - first + 1;
        int inserted = lastNew - first + 1;
        if (removed <= 0 && inserted <= 0) {
            // 条目集合没变（只是段名/张数文本变了）：重绘即可
            notifyDataSetChanged();
            return;
        }
        int header = getHeaderLayoutCount();
        if (removed > 0) {
            notifyItemRangeRemoved(first + header, removed);
        }
        if (inserted > 0) {
            notifyItemRangeInserted(first + header, inserted);
        }
    }

    /** 往待画的分割带列表里追加一条（列表按需创建，没被收起过就不分配） */
    private static List<SectionDividerDecoration.SectionBand> appendBand(
            List<SectionDividerDecoration.SectionBand> bands, SectionDividerDecoration.SectionBand band) {
        List<SectionDividerDecoration.SectionBand> list =
                bands == null ? new ArrayList<>(2) : bands;
        list.add(band);
        return list;
    }

    private static boolean sameTitle(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 段首说明 + 张数；该档不分段时返回 null */
    private String sectionDisplayTitle(String base, int count) {
        if (base == null) {
            return null;
        }
        return context.getString(R.string.section_title_with_count, base, count);
    }

    /**
     * 当前排序档位下这张卡属于哪一段（返回值直接当段落说明，null = 该档不分段）：
     * 星数档→按怪兽的分段家族写 LINK/阶级/星数（连接「LINK-3」、超量「6阶」、
     * 等级怪兽「13星」，魔陷按种类）、攻守档→数值区间、名称档→# / 0 / A~Z、时间档→年份。
     * 文字取本地化资源，随界面语言；只根据卡片自身属性判断，所以反向排序时分段自然跟着反向。
     */
    private String sectionTitleOf(Card card, int sortSel) {
        if (card == null) {
            return null;
        }
        switch (sortSel) {
            case SORT_RULE_ORIGINAL:
                if (!Card.isType(card.Type, CardType.Monster)) {
                    if (Card.isType(card.Type, CardType.Spell)) return context.getString(R.string.section_title_spell);
                    if (Card.isType(card.Type, CardType.Trap)) return context.getString(R.string.section_title_trap);
                    return context.getString(R.string.section_title_other);
                }
                // 怪兽段名按分段家族取，与 CardSearcherManagerBase 星数档的排序键同源：
                // 连接的 getStar() 拿到的是 LINK 值、超量拿到的是阶级，两者都不能写“X星”
                switch (CardSearcherManagerBase.getStarSectionFamily(card)) {
                    case CardSearcherManagerBase.STAR_SECTION_LINK:
                        return context.getString(R.string.section_title_link, card.getLinkNumber());
                    case CardSearcherManagerBase.STAR_SECTION_XYZ:
                        return context.getString(R.string.section_title_rank, card.getStar());
                    default:
                        return context.getString(R.string.section_title_star, card.getStar());
                }
            case 1:
                return statBucketTitle(card.Attack);
            case 2:
                return statBucketTitle(card.Defense);
            case 3:
                // 名称档只分 #（符号与其他文字）、0（数字）与 A~Z（英文首字母或汉字第一个字的
                // 拼音首字母）；段名与 CardSearcherManagerBase 名称档的排序主键同一实现，不会错位
                return String.valueOf(CardSearcherManagerBase.nameSectionKey(card.Name));
            case CardSearcherManagerBase.SORT_RULE_RELEASE_DATE:
                int date = DataManager.get().getPackManager().getCardReleaseDate(card);
                return date == 0 ? context.getString(R.string.section_title_unknown)
                        : context.getString(R.string.section_title_year, date / 10000);
            default:
                return null;
        }
    }

    /**
     * 攻守分段的说明：500 一档写成区间（5000~4501、4500~4001 … 500~1），
     * 0 单独一段，攻守为「?」（负数）的卡也单独成段；大于 5000 的自然向上延伸（如 6000~5501）
     */
    private static String statBucketTitle(int value) {
        if (value < 0) {
            return "?";
        }
        if (value == 0) {
            return "0";
        }
        int top = (value + 499) / 500 * 500;
        return top + "~" + (top - 499);
    }

    /** 设定当前排序是否反向（再次点击已选中的排序按钮时切换）；下一次 applySortRule 生效 */
    public void setSortReversed(boolean reversed) {
        mSortReversed = reversed;
    }

    public boolean isSortReversed() {
        return mSortReversed;
    }

    /**
     * 按卡组编辑器的搜索结果排序规则重排（sortSel 与排序按钮位序同义：
     * 0=星数 / 1=攻击 / 2=守备 / 3=名称 / 4=时间；reverse=反向），
     * 比较器取自 {@link CardSearcherManagerBase}。返回新列表，不改动传入列表
     */
    public static List<Card> sortByRule(List<Card> cards, int sortSel, boolean reverse) {
        List<Card> sorted = new ArrayList<>(cards == null ? 0 : cards.size());
        if (cards != null) sorted.addAll(cards);
        Collections.sort(sorted, CardSearcherManagerBase.searchComparator(sortSel, reverse));
        return sorted;
    }

    /** 排序规则按钮的点击回调：位序（0=星数 / 1=攻击 / 2=守备 / 3=名称 / 4=时间）+ 是否反向 */
    public interface OnSortRuleChangedListener {
        void onSortRuleChanged(int sortSel, boolean reversed);
    }

    /**
     * 结果排序按钮的公共装项（卡查页与卡组管理页共用）：按钮固定写在布局里（按位序排好），
     * 这里负责按系统字符串设文字（随数据语言本地化）、按当前档位与方向切底色、接点击。
     * <p>
     * 点未选中的按钮 = 切到该规则的正向；再次点已选中的按钮 = 正向↔反向切换
     * （星数↓↔星数↑ …），按钮文字跟着换箭头，反向时底色用 radius_p 区分；
     * 重排本页当前结果由页面自己接（不重新发起搜索）
     */
    public static void setupSortButtons(LinearLayout container, int selected, boolean reversed,
                                        OnSortRuleChangedListener listener) {
        if (container == null) return;
        final List<String> labels = sortRuleLabels(container.getContext());
        final List<String> reversedLabels = sortRuleReversedLabels(labels);
        final List<TextView> buttons = new ArrayList<>(labels.size());
        // 与 buttons 一一对应的排序档位（按按钮 id 认，布局里怎么调顺序都不会错位）
        final List<Integer> rules = new ArrayList<>(labels.size());
        // 点击时要用“当前”状态判断是换规则还是切方向，用单元素数组持有可变状态
        final int[] selectedRef = {selected};
        final boolean[] reversedRef = {reversed};
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            final int sortSel = sortRuleOfButton(child.getId());
            if (sortSel < 0) continue; // 不是排序按钮
            TextView button = (TextView) child;
            button.setOnClickListener(v -> {
                if (sortSel == selectedRef[0]) {
                    // 重复点同一个按钮：在正向与反向之间切换
                    reversedRef[0] = !reversedRef[0];
                } else {
                    // 换规则：回到该规则的正向
                    selectedRef[0] = sortSel;
                    reversedRef[0] = false;
                }
                applySortButtonState(buttons, rules, labels, reversedLabels, selectedRef[0], reversedRef[0]);
                if (listener != null) {
                    listener.onSortRuleChanged(selectedRef[0], reversedRef[0]);
                }
            });
            buttons.add(button);
            rules.add(sortSel);
        }
        applySortButtonState(buttons, rules, labels, reversedLabels, selected, reversed);
    }

    /**
     * 排序按钮 id → 排序档位（与 CardSearcherManagerBase 的规则编号一致）：
     * 0=星数 / 1=攻击 / 2=守备 / 3=名称 / 4=时间；不是排序按钮返回 -1
     */
    private static int sortRuleOfButton(int id) {
        if (id == R.id.btn_sort_star) return SORT_RULE_ORIGINAL;
        if (id == R.id.btn_sort_atk) return 1;
        if (id == R.id.btn_sort_def) return 2;
        if (id == R.id.btn_sort_name) return 3;
        if (id == R.id.btn_sort_time) return CardSearcherManagerBase.SORT_RULE_RELEASE_DATE;
        return -1;
    }

    /**
     * 五档排序规则名称：前四档沿用系统字符串（随数据语言本地化）；
     * 末档“时间”是新增规则，用本地化资源（随界面语言，即 res 下各个 values
     * 目录里 strings.xml 的 sort_rule_time），不再往卡包数据包的 strings.conf 里加串
     */
    private static List<String> sortRuleLabels(Context context) {
        StringManager sm = DataManager.get().getStringManager();
        List<String> labels = new ArrayList<>(5);
        labels.add(sm.getSystemString(1370, "星数↓"));
        labels.add(sm.getSystemString(1371, "攻击↓"));
        labels.add(sm.getSystemString(1372, "守备↓"));
        labels.add(sm.getSystemString(1373, "名称↑"));
        labels.add(context != null ? context.getString(R.string.sort_rule_time) : "时间↓");
        return labels;
    }

    /** 反向排序时按钮上的文字：把箭头翻转（↓↔↑）；文本里没有箭头时退回中文反向名称 */
    private static List<String> sortRuleReversedLabels(List<String> labels) {
        String[] fallback = {"星数↑", "攻击↑", "守备↑", "名称↓", "时间↑"};
        List<String> reversed = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (label != null && label.contains("↓")) {
                reversed.add(label.replace("↓", "↑"));
            } else if (label != null && label.contains("↑")) {
                reversed.add(label.replace("↑", "↓"));
            } else {
                reversed.add(i < fallback.length ? fallback[i] : label);
            }
        }
        return reversed;
    }

    /**
     * 按钮状态：选中项按方向用 radius（正向）/ radius_p（反向）底 + 金字并换成对应方向的文字；
     * 其余按钮恢复 button_radius_black_transparents 底 + 白字、正向文字。
     * 文案与底色都按 rules 里的排序档位取，不看按钮在布局里的先后
     */
    private static void applySortButtonState(List<TextView> buttons, List<Integer> rules,
                                             List<String> labels, List<String> reversedLabels,
                                             int selected, boolean reversed) {
        for (int i = 0; i < buttons.size(); i++) {
            int rule = rules.get(i);
            boolean on = rule == selected;
            boolean reversedNow = on && reversed;
            TextView button = buttons.get(i);
            button.setText(reversedNow ? reversedLabels.get(rule) : labels.get(rule));
            button.setBackgroundResource(!on
                    ? R.drawable.button_radius_black_transparents
                    : (reversedNow ? R.drawable.radius_p : R.drawable.radius));
            button.setTextColor(YGOUtil.c(on ? R.color.gold : R.color.white));
        }
    }

    public void notifyItemChanged(Card card) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getCard(i).Code == card.Code) {
                notifyItemChanged(i);
            }
        }
    }

    private boolean showAddButtons = true; // 默认显示添加按钮

    public void setShowAddButtons(boolean show) {
        this.showAddButtons = show;
    }

    private int getColor(int id) {
        return context.getResources().getColor(id);
    }

    public void setItemBg(boolean itemBg) {
        this.mItemBg = itemBg;
    }

    public void setLimitList(LimitList limitList) {
        if (limitList.getCreditLimits() != null) {
            AppsSettings.get().setGenesysMode(1);
        } else {
            AppsSettings.get().setGenesysMode(0);
        }
        mLimitList = limitList;
    }

    /**
     * 绑定菜单项的点击事件和滑动功能
     * 为菜单中的添加按钮设置点击监听器，并启用滑动菜单功能
     *
     * @param holder   ViewHolder对象，用于获取视图组件
     * @param position 当前菜单项在列表中的位置索引
     */
    // 注：本类继承 BaseRecyclerAdapterPlus，其嵌套类型 BaseViewHolder（RecyclerView.ViewHolder 子类）
    // 在类体作用域内遮蔽了 import 的 chad BaseViewHolder，所以这两处必须写全限定名
    public void bindMenu(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, int position) {
        if (showAddButtons) {
            // 设置主卡组添加按钮的点击事件监听器
            holder.getView(R.id.btn_add_main).setOnClickListener((v) -> {

                EventBus.getDefault().post(new CardInfoEvent(position, true));
            });
            // 设置副卡组添加按钮的点击事件监听器
            holder.getView(R.id.btn_add_side).setOnClickListener((v) -> {
                EventBus.getDefault().post(new CardInfoEvent(position, false));
            });
        }
    }

    @Override
    protected void convert(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, Card item) {
        // 宫格模式只呈现卡图、角标与底部两行卡面信息（用户规格），
        // 卡名/灵摆刻度/加卡键不属于宫格条目；
        // 判据取条目自己的 viewType 而非 mGrid，避免旧布局的 holder 走到新模式的绑定分支
        final boolean grid = holder.getItemViewType() == VIEW_TYPE_GRID;
        if (grid) {
            int gridPosition = holder.getBindingAdapterPosition() - getHeaderLayoutCount();
            // 分段末行的那张卡跨了多列，这里把卡图收回到一列的宽度
            bindGridItemWidth(holder, gridPosition);
            imageLoader.bindImage(holder.getView(R.id.card_image), item, ImageLoader.Type.small);
            bindLimitBadge(holder, item);
            CardGridInfoView info = holder.getView(R.id.card_grid_info);
            if (info != null) {
                info.bind(item);
            }
            return;
        }
        int position = holder.getBindingAdapterPosition() - getHeaderLayoutCount();
        imageLoader.bindImage(holder.getView(R.id.card_image), item, ImageLoader.Type.small);

        if (mSearchKeyword != null && !mSearchKeyword.isEmpty()) {
            SpannableString highlightedName = highlightKeyword(item.Name, mSearchKeyword);
            holder.setText(R.id.card_name, highlightedName);
        } else {
            holder.setText(R.id.card_name, item.Name);
        }

        if (item.isType(CardType.Monster)) {
            holder.setGone(R.id.layout_atk, false);
            holder.setGone(R.id.layout_def, false);
            holder.setGone(R.id.star_attr_race_scale, false);

            holder.setGone(R.id.card_level, false);
            holder.setGone(R.id.card_attr, false);
            holder.setGone(R.id.icon_race, false);
            holder.setGone(R.id.card_race, false);
            //星数等级
            if (item.isType(CardType.Xyz)) {
                holder.setTextColor(R.id.card_level, getColor(R.color.star_rank));
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "star_rank.png", 0, 0));
            } else {
                holder.setTextColor(R.id.card_level, getColor(R.color.star));
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "star_level.png", 0, 0));
            }
            holder.setText(R.id.card_level, String.valueOf(item.getStar()));

            // 属性
            if (item.Attribute == CardAttribute.Dark.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dark.png", 0, 0));
            if (item.Attribute == CardAttribute.Light.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "light.png", 0, 0));
            if (item.Attribute == CardAttribute.Earth.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "earth.png", 0, 0));
            if (item.Attribute == CardAttribute.Wind.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "wind.png", 0, 0));
            if (item.Attribute == CardAttribute.Water.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "water.png", 0, 0));
            if (item.Attribute == CardAttribute.Fire.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fire.png", 0, 0));
            if (item.Attribute == CardAttribute.Divine.getId())
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "divine.png", 0, 0));
            holder.setText(R.id.card_attr, mStringManager.getAttributeString(item.Attribute));

            // 种族
            if (item.Race == CardRace.Warrior.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "warrior.png", 0, 0));
            if (item.Race == CardRace.SpellCaster.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "spellcaster.png", 0, 0));
            if (item.Race == CardRace.Fairy.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fairy.png", 0, 0));
            if (item.Race == CardRace.Fiend.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fiend.png", 0, 0));
            if (item.Race == CardRace.Zombie.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "zombie.png", 0, 0));
            if (item.Race == CardRace.Machine.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "machine.png", 0, 0));
            if (item.Race == CardRace.Aqua.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "aqua.png", 0, 0));
            if (item.Race == CardRace.Pyro.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "pyro.png", 0, 0));
            if (item.Race == CardRace.Rock.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "rock.png", 0, 0));
            if (item.Race == CardRace.WingedBeast.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "winged_beast.png", 0, 0));
            if (item.Race == CardRace.Plant.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "plant.png", 0, 0));
            if (item.Race == CardRace.Insect.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "insect.png", 0, 0));
            if (item.Race == CardRace.Thunder.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "thunder.png", 0, 0));
            if (item.Race == CardRace.Dragon.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dragon.png", 0, 0));
            if (item.Race == CardRace.Beast.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "beast.png", 0, 0));
            if (item.Race == CardRace.BeastWarrior.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "beast_warrior.png", 0, 0));
            if (item.Race == CardRace.Dinosaur.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dinosaur.png", 0, 0));
            if (item.Race == CardRace.Fish.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fish.png", 0, 0));
            if (item.Race == CardRace.SeaSerpent.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "sea_serpent.png", 0, 0));
            if (item.Race == CardRace.Reptile.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "reptile.png", 0, 0));
            if (item.Race == CardRace.Psychic.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "psychic.png", 0, 0));
            if (item.Race == CardRace.DivineBeast.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "divine_beast.png", 0, 0));
            if (item.Race == CardRace.CreatorGod.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "creator_god.png", 0, 0));
            if (item.Race == CardRace.Wyrm.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "wyrm.png", 0, 0));
            if (item.Race == CardRace.Cyberse.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cyberse.png", 0, 0));
            if (item.Race == CardRace.Illusion.value())
                holder.setImageBitmap(R.id.icon_race, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "illusion.png", 0, 0));
            holder.setText(R.id.card_race, mStringManager.getRaceString(item.Race));

            // 攻击力
            holder.setText(R.id.card_atk, (item.Attack < 0 ? "?" : String.valueOf(item.Attack)));

            if (item.isType(CardType.Pendulum)) {
                holder.setGone(R.id.p_scale, false);
                holder.setText(R.id.card_scale, String.valueOf(item.LeftScale));
            } else {
                holder.setGone(R.id.p_scale, true);
            }
            if (item.isType(CardType.Link)) {
                holder.setGone(R.id.card_star, true);
                holder.setGone(R.id.card_level, true);
                holder.setGone(R.id.search_link_arrows, false);
                holder.setText(R.id.card_def, item.getStar() < 0 ? "?" : String.valueOf(item.getStar()));
                holder.setText(R.id.TextDef, "LINK-");
                BaseActivity.showLinkArrows(item, holder.getView(R.id.search_link_arrows));
            } else {
                holder.setGone(R.id.card_star, false);
                holder.setGone(R.id.card_level, false);
                holder.setGone(R.id.search_link_arrows, true);
                holder.setText(R.id.card_def, (item.Defense < 0 ? "?" : String.valueOf(item.Defense)));
                holder.setText(R.id.TextDef, "DEF/");
            }

        } else {
            holder.setGone(R.id.card_star, false);
            if (item.isType(CardType.Spell))
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_spell.png", 0, 0));
            if (item.isType(CardType.Trap))
                holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_trap.png", 0, 0));

            if (item.onlyType(CardType.Spell)) {
                //holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_spell.png", 0, 0));
                holder.setImageBitmap(R.id.icon_attr, null);
            } else if (item.onlyType(CardType.Trap)) {
                //holder.setImageBitmap(R.id.card_star, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_trap.png", 0, 0));
                holder.setImageBitmap(R.id.icon_attr, null);
            }
            if (item.isType(CardType.QuickPlay)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "quickplay.png", 0, 0));
            } else if (item.isType(CardType.Continuous)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "continuous.png", 0, 0));
            } else if (item.isType(CardType.Equip)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "equip.png", 0, 0));
            } else if (item.isType(CardType.Field)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "field.png", 0, 0));
            } else if (item.isType(CardType.Counter)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "counter.png", 0, 0));
            } else if (item.isType(CardType.Ritual)) {
                holder.setImageBitmap(R.id.icon_attr, BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "ritual.png", 0, 0));
            }
            holder.setGone(R.id.card_level, true);
            holder.setGone(R.id.card_attr, true);
            holder.setGone(R.id.icon_race, true);
            holder.setGone(R.id.card_race, true);
            holder.setGone(R.id.p_scale, true);
            holder.setGone(R.id.search_link_arrows, true);
            holder.setGone(R.id.layout_atk, true);
            holder.setGone(R.id.layout_def, true);
        }
        bindLimitBadge(holder, item);
        //卡片类型
        holder.setText(R.id.card_type, CardUtils.getAllTypeString(item, mStringManager));

        holder.setText(R.id.card_code, String.format("%08d", item.getCode()));

        // 根据设置控制添加按钮的可见性
        if (item.isType(CardType.Token)) {//衍生物不显示添加按钮
            holder.getView(R.id.ll_add_btn).setVisibility(View.GONE);
        } else {
            holder.getView(R.id.ll_add_btn).setVisibility(showAddButtons ? View.VISIBLE : View.GONE);
        }

        bindMenu(holder, position);
        if (mItemBg) {
            holder.setBackgroundResource(R.id.swipe_layout, R.drawable.list_item_bg);
        }
    }

    /**
     * 宫格分段末行的那张卡跨了多列（为了把本行填满、让下一段从新行开始），
     * 这里把卡图宽度收回到「一列」的固定尺寸，避免整张卡被拉宽；普通格子恢复占满单列。
     * <p>
     * 宽度用 applyListLayout 算好的固定像素值（单列宽 - 条目左右 padding），不用百分比：
     * 百分比相对「跨列格子的内宽」计算，结果比普通格子的卡图大 4dp×(1-1/span)，
     * 仍会看出最后一张卡偏大。卡图左右都约束到父布局，宽度固定后默认水平偏移（bias 0.5）
     * 会把它摆到格子居中；改为 0 贴左，与普通格子的列线对齐。卡图宽高比仍由
     * item_search_card_grid 的 dimensionRatio 派生
     */
    private void bindGridItemWidth(com.chad.library.adapter.base.viewholder.BaseViewHolder holder,
                                   int position) {
        View image = holder.getView(R.id.card_image);
        if (image == null || !(image.getLayoutParams() instanceof ConstraintLayout.LayoutParams)) {
            return;
        }
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) image.getLayoutParams();
        int span = spanSizeAt(position);
        if (span > 1 && mGridCellImageWidth > 0) {
            if (params.width == mGridCellImageWidth
                    && params.horizontalBias == 0f
                    && params.matchConstraintDefaultWidth != ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT) {
                return;
            }
            // 恢复普通约束形态后改固定宽（防止残留百分比模式影响子视图度量）
            params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD;
            params.matchConstraintPercentWidth = 1f;
            params.width = mGridCellImageWidth;
            params.horizontalBias = 0f;
            image.setLayoutParams(params);
        } else if (params.width != 0
                || params.matchConstraintDefaultWidth == ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT
                || params.horizontalBias != 0.5f) {
            // 普通格子：宽度 0dp + start/end 撑满单列，bias 居中
            params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD;
            params.matchConstraintPercentWidth = 1f;
            params.width = 0;
            params.horizontalBias = 0.5f;
            image.setLayoutParams(params);
        }
    }

    /**
     * 禁限 / 起源点(GeneSys)点数角标绑定：行模式与宫格模式共用这一段（两种条目布局的
     * right_top / tv_limit_num 同名），保证两种展现下的判定口径与贴图完全一致。
     * <p>
     * 数字TextView的可见性必须在这里当场定（有数字才显）：它原先只靠布局默认值，
     * 行模式 item_search_card 写的是 visible 所以能显形，宫格 item_search_card_grid
     * 写的是 gone，于是宫格切换后禁止与起源点的数字全部消失。
     */
    private void bindLimitBadge(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, Card item) {
        if (mImageTop == null) {
            mImageTop = new ImageTop(context);
        }
        TextView tv_limit_num = holder.getView(R.id.tv_limit_num);//创建该布局的对象，以便对它的字体颜色，大小，文本进行自由设置
        // 角标图与数字的判定一次走完，末尾统一写回文本与可见性
        boolean badgeShown = false;
        String limitNum = "";
        if (mLimitList != null) {
            if (mLimitList.check(item, LimitType.Forbidden)) {
                badgeShown = true;
                holder.setImageBitmap(R.id.right_top, mImageTop.forbidden);
            } else if (mLimitList.check(item, LimitType.Limit)) {
                badgeShown = true;
                holder.setImageBitmap(R.id.right_top, mImageTop.limit);
                limitNum = "1";
                tv_limit_num.setTextSize(12);
                tv_limit_num.setTextColor(YGOUtil.c(R.color.yellow));
            } else if (mLimitList.check(item, LimitType.SemiLimit)) {
                badgeShown = true;
                holder.setImageBitmap(R.id.right_top, mImageTop.semiLimit);
                limitNum = "2";
                tv_limit_num.setTextSize(12);
                tv_limit_num.setTextColor(YGOUtil.c(R.color.yellow));
            } else if (mLimitList.check(item, LimitType.GeneSys)) {
                // 统一使用 getGeneSysCredit 取值：与 check(GeneSys) 同口径，命中“宽松同名折叠键”或“卡片自身code”
                Integer creditValue = mLimitList.getGeneSysCredit(item);
                badgeShown = true;
                holder.setImageBitmap(R.id.right_top, mImageTop.credits);
                limitNum = creditValue == null ? "---" : creditValue.toString();
                tv_limit_num.setTextSize(creditValue == null ? 8 : ((creditValue > -10 && creditValue < 100) ? 10 : 8));
                tv_limit_num.setTextColor(creditValue == null ? YGOUtil.c(R.color.red) : YGOUtil.c(R.color.holo_blue_bright));
            }
        }
        holder.setGone(R.id.right_top, !badgeShown);
        holder.setText(R.id.tv_limit_num, limitNum);
        tv_limit_num.setVisibility(limitNum.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * 高亮显示文本中的关键词
     * 在文本中查找所有匹配的关键词，并为其添加蓝色背景高亮效果
     * 支持空格分隔的多个关键词
     *
     * @param text 需要处理的原始文本
     * @param keyword 需要高亮的关键词（可以是空格分隔的多个关键词）
     * @return 包含高亮效果的SpannableString对象，如果文本或关键词为空则返回未处理的SpannableString
     */
    private SpannableString highlightKeyword(String text, String keyword) {
        SpannableString spannableString = new SpannableString(text);
        if (text == null || keyword == null || keyword.isEmpty()) {
            return spannableString;
        }
        
        // 按空格分割关键词
        String[] keywords = keyword.trim().split("\\s+");
        
        // 对每个关键词进行高亮处理
        for (String singleKeyword : keywords) {
            if (singleKeyword.isEmpty()) {
                continue;
            }
            
            int startIndex = 0;
            while (startIndex < text.length()) {
                int index = text.indexOf(singleKeyword, startIndex);
                if (index == -1) {
                    break;
                }
                
                int endIndex = index + singleKeyword.length();
                BackgroundColorSpan bgSpan = new BackgroundColorSpan(YGOUtil.c(R.color.item_bg));
                spannableString.setSpan(bgSpan, index, endIndex, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                
                startIndex = endIndex;
            }
        }
        
        return spannableString;
    }
}

class ViewHolder extends BaseRecyclerAdapterPlus.BaseViewHolder {
    ImageView cardImage;
    TextView cardName;
    TextView cardLevel;
    TextView cardattr;
    TextView cardrace;
    TextView cardType;
    TextView cardAtk;
    TextView cardDef;
    TextView TextDef;
    TextView cardScale;
    TextView tv_limit_num;
    ImageView rightImage;
    View layout_atk;
    View layout_def;
    View view_bar;
    View layout_star_attr_race_scale;
    View layout_p_scale;
    View linkArrow;
    TextView codeView;
    View btnMain, btnSide;
    SwipeHorizontalMenuLayout mMenuLayout;

    ViewHolder(View view) {
        super(view);
        view.setTag(view.getId(), this);
        cardImage = $(R.id.card_image);
        cardName = $(R.id.card_name);
        cardType = $(R.id.card_type);
        cardAtk = $(R.id.card_atk);
        cardDef = $(R.id.card_def);
        layout_star_attr_race_scale = $(R.id.star_attr_race_scale);
        layout_p_scale = $(R.id.p_scale);
        cardLevel = $(R.id.card_level);
        cardattr = $(R.id.card_attr);
        cardrace = $(R.id.card_race);
        cardScale = $(R.id.card_scale);
        layout_atk = $(R.id.layout_atk);
        layout_def = $(R.id.layout_def);
        view_bar = $(R.id.view_bar);
        rightImage = $(R.id.right_top);
        tv_limit_num = $(R.id.tv_limit_num);
        codeView = $(R.id.card_code);
        TextDef = $(R.id.TextDef);
        btnMain = $(R.id.btn_add_main);
        btnSide = $(R.id.btn_add_side);
        mMenuLayout = $(R.id.swipe_layout);
        linkArrow = $(R.id.search_link_arrows);
//            File outFile = new File(AppsSettings.get().getCoreSkinPath(), Constants.UNKNOWN_IMAGE);
//            ImageLoader.get().$(context, outFile, cardImage, outFile.getName().endsWith(Constants.BPG), 0, null);
    }
}


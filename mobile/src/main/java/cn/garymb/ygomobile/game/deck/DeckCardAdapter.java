package cn.garymb.ygomobile.game.deck;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.widget.CardGridInfoView;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class DeckCardAdapter extends RecyclerView.Adapter<DeckCardAdapter.CardViewHolder> {

    // === 对应 game.h: AVAIL_OCG / AVAIL_TCG / AVAIL_OCGTCG ===
    private static final int AVAIL_OCG = 0x1;
    private static final int AVAIL_TCG = 0x2;
    private static final int AVAIL_OCGTCG = AVAIL_OCG | AVAIL_TCG;

    private final ImageLoader imageLoader;
    private final DeckEditorManager editorManager;
    private final DeckInfo.Type deckType;
    private final CardDragHelper dragHelper;
    /**
     * 独立模式条目点击回调（供决斗内关键词卡片列表弹窗复用本 adapter：
     * 无 DeckEditorManager / CardDragHelper，点击仅回调外部，不触发任何卡组/拖拽逻辑）。
     */
    private final OnCardItemClickListener standaloneListener;
    private final List<Card> cards = new ArrayList<>();
    private ImageTop mImageTop;
    private LimitList mLimitList;
    private int mCardWidth = -1;
    private int mCardHeight = -1;
    private int mSelectedPosition = RecyclerView.NO_POSITION;
    private int mAvailLm = 0;
    private int mTouchSlop;
    private boolean mReadonly;
    /**
     * 搜索结果列是否宫格展现（卡图与禁限/起源点角标，不带卡名/属性/攻守文字）；
     * 缺省 false 走行列表，由 {@code CardSearcherManager} 按本页设置项初始化
     */
    private boolean mGrid;

    public DeckCardAdapter(ImageLoader imageLoader, DeckEditorManager editorManager, DeckInfo.Type deckType, CardDragHelper dragHelper) {
        this.imageLoader = imageLoader;
        this.editorManager = editorManager;
        this.deckType = deckType;
        this.dragHelper = dragHelper;
        this.standaloneListener = null;
    }

    /**
     * 独立模式构造：仅传图片加载器与条目点击回调，editorManager / dragHelper 均为空。
     * 卡组编辑搜索结果以外的场景（如决斗内关键词卡片列表）复用同款 item/adapter 时使用；
     * 此模式下不附加拖拽触摸监听，条目点击只回调 standaloneListener。
     */
    public DeckCardAdapter(ImageLoader imageLoader, OnCardItemClickListener listener) {
        this.imageLoader = imageLoader;
        this.editorManager = null;
        this.deckType = null;
        this.dragHelper = null;
        this.standaloneListener = listener;
    }

    /** 独立模式（无 DeckEditorManager）下的条目点击回调 */
    public interface OnCardItemClickListener {
        void onCardClick(Card card);
    }

    public void setLimitList(LimitList limitList) {
        this.mLimitList = limitList;
        notifyDataSetChanged();
    }

    /**
     * 设置赛制可用性标识模式，availLm为spinner_filter_limit选中项id（6=OCG、7=TCG、8=简体中文、
     * 10=OCG独有、11=TCG独有）；由 CardSearcherManagerBase.applyAvailDisplay 下发。
     */
    public void setAvailLm(int availLm) {
        if (mAvailLm == availLm) return;
        mAvailLm = availLm;
        notifyDataSetChanged();
    }

    public void setCards(List<Card> newCards) {
        cards.clear();
        if (newCards != null) {
            cards.addAll(newCards);
        }
        mSelectedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public void setCardSize(int width, int height) {
        mCardWidth = width;
        mCardHeight = height;
        notifyDataSetChanged();
    }

    /**
     * 设置拖拽触发所需的触摸阈值与只读状态，由DeckEditorManager在setupDragAndDrop时调用
     */
    public void setDragState(int touchSlop, boolean readonly) {
        mTouchSlop = touchSlop;
        mReadonly = readonly;
    }

    // === 搜索结果两种展现（行模式：卡图 + 三行文字；宫格模式：同一尺寸的卡图
    //   + 叠在卡图下缘的 CardGridInfoView 信息条）===

    /**
     * 行/宫格两种条目布局各自的 viewType：RecyclerView 的复用池与缓存队列都按 viewType 分桶，
     * 两态共用一个 viewType 时切换模式后旧布局的条目会被当成新模式直接复用，
     * 而两种布局的控件树完全不同，宫格态会残留整行卡片布局与行间距、排列散乱
     */
    private static final int VIEW_TYPE_LIST = 1;
    private static final int VIEW_TYPE_GRID = 2;

    /** item_deck_card_grid 根节点的内边距（dp），与布局文件里的 padding 同口径，算列数时要算进单格宽 */
    private static final float GRID_ITEM_PADDING_DP = 3f;

    /**
     * 攻守数字字号上限在卡组编辑器里的再放大倍数：宫格卡图要与列表态同尺寸（比卡查页的宫格
     * 卡图小很多），按比例推出来的数字只剩 4sp 级几乎看不清，本页单独把这一行放大（用户规格）。
     * 上限给得比实际能放的宽即可：真正定字号的是 CardGridInfoView 里的定长样本回缩
     * （攻守按 0000/0000 四位那一档、连接按 0000/LINK-0 那一档），
     * 因此同屏卡片字号一致且不会撑出卡宽；卡查页与卡组编辑页不走这里，仍保持原来的 8sp
     */
    private static final float ATK_TEXT_SCALE_IN_EDITOR = 2.2f;

    /**
     * 搜索结果卡片允许起拖前必须按住的最短时长（毫秒）：原先卡图把手在按下那一刻就禁止父级拦截、
     * 位移过阈值即起拖，想上下翻滚列表而手指落在卡图上时会被误判成拖卡。现多加 0.1 秒门槛（用户规格），
     * 期间手势仍归列表滚动（滚动本身与阈值不动）；卡组网格那一路不走此门槛
     */
    private static final long SEARCH_DRAG_START_DELAY_MS = 100L;

    @Override
    public int getItemViewType(int position) {
        return mGrid ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
    }

    /**
     * 切换搜索结果列的展现形式（宫格/行）。本方法只改标志并重建条目，
     * 布局管理器要另外调 {@link #applyListLayout(RecyclerView)} 或直接用
     * {@link #toggleListLayout(RecyclerView)}
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
     * 按当前展现模式装配布局管理器：宫格 = GridLayoutManager（列数 = 列表可用宽 / 单格宽，
     * 单格宽 = 卡组网格下发的 {@link #setCardSize} 卡宽 + 条目内边距，即卡图与行模式同大小，
     * 宽度或卡尺寸还没量出来时先给 1 列兜底，由调用方在两者就位后复核）；
     * 列表 = 原来的 {@link LinearLayoutManager}。已是目标类型时只改列数，不重建布局管理器
     * （避免滚动位置被摸清）。列数变化会 requestLayout，故必须在布局之外（post）调用
     */
    public void applyListLayout(RecyclerView list) {
        if (list == null) return;
        RecyclerView.LayoutManager lm = list.getLayoutManager();
        if (!mGrid) {
            // GridLayoutManager 也是 LinearLayoutManager 的子类，判断列表态时必须先排除宫格
            boolean linear = lm instanceof LinearLayoutManager && !(lm instanceof GridLayoutManager);
            if (!linear) {
                list.setLayoutManager(new LinearLayoutManager(list.getContext()));
            }
            return;
        }
        int cell = (mCardWidth > 0 ? mCardWidth : YGOUtil.dp2px(40f))
                + YGOUtil.dp2px(GRID_ITEM_PADDING_DP * 2);
        int avail = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        int span = avail > 0 ? Math.max(1, avail / cell) : 1;
        if (lm instanceof GridLayoutManager) {
            if (((GridLayoutManager) lm).getSpanCount() != span) {
                ((GridLayoutManager) lm).setSpanCount(span);
            }
        } else {
            list.setLayoutManager(new GridLayoutManager(list.getContext(), span));
        }
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

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(viewType == VIEW_TYPE_GRID
                        ? R.layout.item_deck_card_grid : R.layout.item_deck_card_horizontal,
                        parent, false);
        if (mImageTop == null) {
            mImageTop = new ImageTop(parent.getContext());
        }
        CardViewHolder holder = new CardViewHolder(view);
        if (viewType == VIEW_TYPE_GRID && holder.gridInfo != null) {
            holder.gridInfo.setAtkDefTextScale(ATK_TEXT_SCALE_IN_EDITOR);
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        // 卡面尺寸两种展现共用卡组网格下发的同一尺寸（用户规格：宫格的卡图要和列表态一样大），
        // 尺寸不为 0 才写，否则保留布局里的默认值
        if (mCardWidth > 0 && mCardHeight > 0) {
            ViewGroup.LayoutParams lp = holder.cardContainer.getLayoutParams();
            lp.width = mCardWidth;
            lp.height = mCardHeight;
            holder.cardContainer.setLayoutParams(lp);
        }
        applyOverlaySize(holder);

        Card card = cards.get(position);
        if (card == null) {
            holder.ivCard.setImageResource(R.drawable.unknown);
            holder.ivLimitTop.setVisibility(View.GONE);
            holder.tvLimitNum.setVisibility(View.GONE);
            if (holder.ivAvailBottom != null) holder.ivAvailBottom.setVisibility(View.GONE);
            if (holder.tvName != null) holder.tvName.setText("");
            if (holder.tvInfo != null) holder.tvInfo.setText("");
            if (holder.tvAtkDef != null) holder.tvAtkDef.setText("");
            if (holder.gridInfo != null) holder.gridInfo.bind(null);
            return;
        }

        imageLoader.bindImage(holder.ivCard, card, ImageLoader.Type.small);
        bindLimitOverlay(holder, card);
        bindAvailOverlay(holder, card);
        bindCardInfo(holder, card);
        // 宫格布局特有的叠在卡图下缘的信息条（星数/箭头与数字重叠 + 属性 + 种族均等分三格，
        // 第二行攻守；魔陷为种类 + 子类），尺寸随卡宽等比；
        // 行布局没这块（引用为 null），行模式仍走上面的三行文字
        if (holder.gridInfo != null) {
            holder.gridInfo.bind(card);
        }
        //选中项加一层holo_blue_bright背景，未选中恢复透明（holder复用安全）
        holder.itemView.setBackgroundColor(position == mSelectedPosition
                ? YGOUtil.c(R.color.blackLinght) : Color.TRANSPARENT);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            setSelectedPosition(pos);
            if (deckType == null) {
                if (editorManager != null) {
                    editorManager.onSearchCardClicked(card);
                } else if (standaloneListener != null) {
                    standaloneListener.onCardClick(card);
                }
            } else {
                editorManager.onDeckCardClicked(deckType, pos);
            }
        });

        if (deckType == null) {
            // 拖拽触摸仅在存在拖拽助手（卡组编辑场景）时附加；独立模式（dragHelper 为空）不响应拖拽
            if (dragHelper != null) {
                //整个item可横向拖动触发拖拽，但拖拽阴影统一用卡图生成
                holder.itemView.setOnTouchListener(createSearchDragTouchListener(card, holder.ivCard, mTouchSlop, mReadonly));
                //卡图作为拖拽把手：任意方向拖动即可触发拖拽；点击卡图仍等同点击整个item
                holder.ivCard.setOnTouchListener(createSearchImageDragTouchListener(card, mTouchSlop, mReadonly));
            }
            holder.ivCard.setOnClickListener(v -> holder.itemView.performClick());
        }
    }

    /**
     * 禁限角标高度为卡图高度的0.3，宽度与高度一致，
     * 角标数字与角标同宽高，字号随角标尺寸等比缩放。
     */
    private void applyOverlaySize(@NonNull CardViewHolder holder) {
        int cardHeight = holder.cardContainer.getLayoutParams().height;
        if (cardHeight <= 0) return;
        int overlaySize = Math.max(1, Math.round(cardHeight * Constants.CARD_LIMIT_OVERLAY_RATIO));
        holder.overlaySize = overlaySize;

        ViewGroup.LayoutParams ivLp = holder.ivLimitTop.getLayoutParams();
        ivLp.width = overlaySize;
        ivLp.height = overlaySize;
        holder.ivLimitTop.setLayoutParams(ivLp);

        ViewGroup.LayoutParams tvLp = holder.tvLimitNum.getLayoutParams();
        tvLp.width = overlaySize;
        tvLp.height = overlaySize;
        holder.tvLimitNum.setLayoutParams(tvLp);
    }

    private void setLimitNumTextSize(@NonNull CardViewHolder holder, float scale) {
        if (holder.overlaySize > 0) {
            holder.tvLimitNum.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.overlaySize * scale);
        }
    }

    private void bindCardInfo(@NonNull CardViewHolder holder, Card card) {
        if (holder.tvName == null || holder.tvInfo == null || holder.tvAtkDef == null) {
            return;
        }
        holder.tvName.setText(card.Name);
        StringManager sm = DataManager.get().getStringManager();
        String avail = getAvailSuffix(card);
        if (card.isSpellTrap()) {
            holder.tvInfo.setText(CardUtils.getAllTypeString(card, sm));
            holder.tvAtkDef.setText(avail.trim());
        } else {
            String attr = sm.getAttributeString(card.Attribute);
            String race = sm.getRaceString(card.Race);
            String atk = card.Attack < 0 ? "?" : String.valueOf(card.Attack);
            if (card.isType(CardType.Link)) {
                holder.tvInfo.setText(attr + "/" + race + "  LINK-" + card.getStar());
                holder.tvAtkDef.setText(atk + "/-" + avail);
            } else {
                String def = card.Defense < 0 ? "?" : String.valueOf(card.Defense);
                holder.tvInfo.setText(attr + "/" + race + "  ★" + card.getStar());
                holder.tvAtkDef.setText(atk + "/" + def + avail);
            }
        }
    }

    // === 对应 drawing.cpp: availBuffer OCG/TCG独有标识 ===
    private String getAvailSuffix(Card card) {
        int availOcgTcg = card.Ot & AVAIL_OCGTCG;
        if (availOcgTcg == AVAIL_OCG) return " [OCG]";
        if (availOcgTcg == AVAIL_TCG) return " [TCG]";
        return "";
    }

    private void bindAvailOverlay(@NonNull CardViewHolder holder, Card card) {
        if (holder.ivAvailBottom == null) return;
        applyAvailPlacement(holder);
        android.graphics.Bitmap bitmap = null;
        if (mImageTop != null) {
            //OCG/TCG标识仅独有卡显示（OCG、TCG共有卡不显示），简中标识按Ot含简中位显示
            int otOcgTcg = card.Ot & 0x3;
            if ((mAvailLm == 6 || mAvailLm == 10) && otOcgTcg == 0x1) {
                bitmap = mImageTop.otOcg;
            } else if ((mAvailLm == 7 || mAvailLm == 11) && otOcgTcg == 0x2) {
                bitmap = mImageTop.otTcg;
            } else if (mAvailLm == 8 && (card.Ot & 0x8) != 0) {
                bitmap = mImageTop.otSc;
            }
        }
        if (bitmap != null) {
            holder.ivAvailBottom.setImageBitmap(bitmap);
            holder.ivAvailBottom.setVisibility(View.VISIBLE);
        } else {
            holder.ivAvailBottom.setVisibility(View.GONE);
        }
    }

    /**
     * 赛制独有标识的落位：行模式贴卡底居中（与布局 XML 同）；宫格下卡底那一条被信息条占着，
     * 用 bottomMargin 把它抬到信息条之上——仍然是“卡片底部的图标”，也不与左上角的禁限角标打架，
     * 也不会被信息条的半透底条盖掉（原先因为本页 availLm 从未下发，这两个分支根本走不到）
     */
    private void applyAvailPlacement(@NonNull CardViewHolder holder) {
        ViewGroup.LayoutParams params = holder.ivAvailBottom.getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) params;
        int gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        int inset = 0;
        if (mGrid) {
            inset = holder.gridInfo != null ? holder.gridInfo.getBarHeight() : 0;
            if (inset <= 0 && mCardHeight > 0) {
                // 首次绑定先于信息条测量：按卡高比例先让开（实测信息条约占卡高 0.45），
                // 下一轮绑定拿到真实高度后自然校正
                inset = Math.round(mCardHeight * 0.45f);
            }
        }
        if (flp.gravity == gravity && flp.bottomMargin == inset) return;
        flp.gravity = gravity;
        flp.bottomMargin = inset;
        holder.ivAvailBottom.setLayoutParams(flp);
    }

    private void bindLimitOverlay(@NonNull CardViewHolder holder, Card card) {
        if (mImageTop == null || mLimitList == null) {
            holder.ivLimitTop.setVisibility(View.GONE);
            holder.tvLimitNum.setVisibility(View.GONE);
            return;
        }

        if (mLimitList.check(card, LimitType.Forbidden)) {
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.forbidden);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText("");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.white));
            setLimitNumTextSize(holder, 0.6f);
        } else if (mLimitList.check(card, LimitType.Limit)) {
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.limit);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText("1");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
            setLimitNumTextSize(holder, 0.6f);
        } else if (mLimitList.check(card, LimitType.SemiLimit)) {
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.semiLimit);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText("2");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
            setLimitNumTextSize(holder, 0.6f);
        } else if (mLimitList.check(card, LimitType.GeneSys)) {
            Integer creditValue = 0;
            if (mLimitList.getCredits() != null) {
                creditValue = mLimitList.getCredits().get(card.getCode());
            }
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.credits);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText(creditValue != null ? creditValue.toString() : "0");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
            //两位数以内用标准比例，更多位数缩小字号保证放得下
            setLimitNumTextSize(holder,
                    (creditValue != null && creditValue > -10 && creditValue < 100) ? 0.6f : 0.45f);
        } else {
            holder.ivLimitTop.setVisibility(View.GONE);
            holder.tvLimitNum.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    private void setSelectedPosition(int position) {
        if (mSelectedPosition == position) return;
        int old = mSelectedPosition;
        mSelectedPosition = position;
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
        notifyItemChanged(mSelectedPosition);
    }

    /**
     * 按下后移动超过touchSlop即开始拖动；返回false保证点击/长按仍可触发。
     * 搜索结果item整体仅横向拖动触发，避免与列表纵向滚动冲突；
     * 卡图把手（disallowIntercept=true）任意方向均可触发。
     * <p>
     * startDelayMs 是允许起拖前必须按住的最短时长（搜索结果列传 100ms，卡组网格传 0）：
     * 本监听原先在 ACTION_DOWN 那一刻就禁止父级拦截，手指落在卡图上的快速上滑会被判成拖卡
     * 而不是翻滚列表（用户报的误触）。改后未达时长的 MOVE 一律不动作也不抢手势，
     * 列表滚动路径与阈值保持不变；达到时长后才把本次手势交给拖拽
     * <p>
     * shadowView不为null时，拖拽阴影用shadowView（卡图）生成而非触发拖拽的view。
     * 拖拽触发后事件转交CardDragHelper，实现应用内自定义拖拽（规避系统拖拽被OEM浮窗拦截）。
     */
    private static class CardDragTouchListener implements View.OnTouchListener {
        private final DeckInfo.Type source;
        private final int index;
        private final Card card;
        private final boolean horizontalOnly;
        private final boolean disallowIntercept;
        private final View shadowView;
        private final int touchSlop;
        private final boolean readonly;
        private final long startDelayMs;
        private final CardDragHelper dragHelper;
        private float downX, downY;
        private long downTime;
        /** 本次手势是否已按住到允许起拖的时长（已把禁止拦截交给父级） */
        private boolean holdReady;

        CardDragTouchListener(DeckInfo.Type source, int index, Card card, boolean horizontalOnly,
                              boolean disallowIntercept, View shadowView,
                              int touchSlop, boolean readonly, long startDelayMs,
                              CardDragHelper dragHelper) {
            this.source = source;
            this.index = index;
            this.card = card;
            this.horizontalOnly = horizontalOnly;
            this.disallowIntercept = disallowIntercept;
            this.shadowView = shadowView;
            this.touchSlop = touchSlop;
            this.readonly = readonly;
            this.startDelayMs = startDelayMs;
            this.dragHelper = dragHelper;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            //拖拽进行中：后续触摸事件全部转交拖拽助手
            if (dragHelper.isDragging()) {
                return dragHelper.onTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downTime = event.getEventTime();
                    holdReady = false;
                    //上一张卡的拖拽阴影若因抬手丢失（被父级抢事件、弹窗、重绑换监听）而留在
                    //内容层上，就是玩家目击的「场上残留一个点不动的卡片矩形」；本监听没在拖时
                    //任何按下都先清扫一次，让残影在下一碰卡片时自愈
                    DragLayers.sweep();
                    // 不在按下时就禁止父级拦截：那会抢掉本应归列表的滚动，
                    // 拖拽门槛改到下面的 MOVE 分支里按时长再交
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (readonly || card == null) return false;
                    if (!holdReady) {
                        // 还没按住到允许起拖的时长：本次手势仍归列表滚动，不抢手势也不起拖
                        if (event.getEventTime() - downTime < startDelayMs) return false;
                        holdReady = true;
                        if (disallowIntercept && v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    boolean triggered = horizontalOnly
                            ? Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)
                            : Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop;
                    if (triggered) {
                        //本事件被接管后 View.onTouchEvent 不再被调用，它自带的“移动超阈值即取消长按”
                        //也就永远不会触发；不显式 cancelLongPress，按住拖到 500ms 会被当成长按
                        //（卡组网格上会误入批量勾选模式），体感与原本的单卡拖拽不一致
                        v.cancelLongPress();
                        //禁止父级（尤其RecyclerView）拦截后续事件，保证拖拽期间持续收到MOVE/UP
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        dragHelper.startDrag(shadowView != null ? shadowView : v, source, index, card, event);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdReady = false;
                    return false;
                default:
                    return false;
            }
        }
    }

    /**
     * 供DeckEditorManager调用：卡组网格卡片任意方向拖动触发拖拽（无时长门槛，保持原有手感）
     */
    public View.OnTouchListener createDragTouchListener(DeckInfo.Type source, int index, Card card,
                                                        int touchSlop, boolean readonly) {
        return new CardDragTouchListener(source, index, card, false, false, null,
                touchSlop, readonly, 0L, dragHelper);
    }

    /**
     * 搜索结果item整体仅横向拖动触发，避免与列表纵向滚动冲突；拖拽阴影用shadowView（卡图）生成；
     * 起拖前需先按住 {@link #SEARCH_DRAG_START_DELAY_MS}
     */
    public View.OnTouchListener createSearchDragTouchListener(Card card, View shadowView,
                                                              int touchSlop, boolean readonly) {
        return new CardDragTouchListener(null, -1, card, true, false, shadowView,
                touchSlop, readonly, SEARCH_DRAG_START_DELAY_MS, dragHelper);
    }

    /**
     * 卡图作为拖拽把手：任意方向拖动可触发拖拽，但先要按住 {@link #SEARCH_DRAG_START_DELAY_MS}，
     * 到时长后才禁止列表拦截本次手势（快速上滑仍然归列表翻滚）
     */
    public View.OnTouchListener createSearchImageDragTouchListener(Card card,
                                                                   int touchSlop, boolean readonly) {
        return new CardDragTouchListener(null, -1, card, false, true, null,
                touchSlop, readonly, SEARCH_DRAG_START_DELAY_MS, dragHelper);
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        ViewGroup cardContainer;
        ImageView ivCard;
        ImageView ivLimitTop;
        ImageView ivAvailBottom;
        TextView tvLimitNum;
        TextView tvName;
        TextView tvInfo;
        TextView tvAtkDef;
        /** 宫格布局才有的底部信息块（行布局为 null） */
        CardGridInfoView gridInfo;
        int overlaySize;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardContainer = itemView.findViewById(R.id.card_container);
            ivCard = itemView.findViewById(R.id.iv_deck_card_item);
            ivLimitTop = itemView.findViewById(R.id.iv_deck_card_limit_top);
            ivAvailBottom = itemView.findViewById(R.id.iv_deck_card_avail_bottom);
            tvLimitNum = itemView.findViewById(R.id.tv_deck_limit_num);
            tvName = itemView.findViewById(R.id.tv_deck_card_name);
            tvInfo = itemView.findViewById(R.id.tv_deck_card_info);
            tvAtkDef = itemView.findViewById(R.id.tv_deck_card_atkdef);
            gridInfo = itemView.findViewById(R.id.card_grid_info);
        }
    }
}
package cn.garymb.ygomobile.game;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
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
    private final List<Card> cards = new ArrayList<>();
    private ImageTop mImageTop;
    private LimitList mLimitList;
    private int mCardWidth = -1;
    private int mCardHeight = -1;
    private int mSelectedPosition = RecyclerView.NO_POSITION;
    private int mAvailLm = 0;
    private int mTouchSlop;
    private boolean mReadonly;

    public DeckCardAdapter(ImageLoader imageLoader, DeckEditorManager editorManager, DeckInfo.Type deckType, CardDragHelper dragHelper) {
        this.imageLoader = imageLoader;
        this.editorManager = editorManager;
        this.deckType = deckType;
        this.dragHelper = dragHelper;
    }

    public void setLimitList(LimitList limitList) {
        this.mLimitList = limitList;
        notifyDataSetChanged();
    }

    /**
     * 设置赛制可用性标识模式，availLm为spinner_filter_limit选中项id（6=OCG、7=TCG、8=简体中文）。
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

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_deck_card_horizontal, parent, false);
        if (mImageTop == null) {
            mImageTop = new ImageTop(parent.getContext());
        }
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
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
            return;
        }

        imageLoader.bindImage(holder.ivCard, card, ImageLoader.Type.small);
        bindLimitOverlay(holder, card);
        bindAvailOverlay(holder, card);
        bindCardInfo(holder, card);
        //选中项加一层holo_blue_bright背景，未选中恢复透明（holder复用安全）
        holder.itemView.setBackgroundColor(position == mSelectedPosition
                ? YGOUtil.c(R.color.blackLinght) : Color.TRANSPARENT);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            setSelectedPosition(pos);
            if (deckType == null) {
                editorManager.onSearchCardClicked(card);
            } else {
                editorManager.onDeckCardClicked(deckType, pos);
            }
        });

        if (deckType == null) {
            //整个item可横向拖动触发拖拽，但拖拽阴影统一用卡图生成
            holder.itemView.setOnTouchListener(createSearchDragTouchListener(card, holder.ivCard, mTouchSlop, mReadonly));
            //卡图作为拖拽把手：任意方向拖动即可触发拖拽；点击卡图仍等同点击整个item
            holder.ivCard.setOnTouchListener(createSearchImageDragTouchListener(card, mTouchSlop, mReadonly));
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
        private final CardDragHelper dragHelper;
        private float downX, downY;

        CardDragTouchListener(DeckInfo.Type source, int index, Card card, boolean horizontalOnly,
                              boolean disallowIntercept, View shadowView,
                              int touchSlop, boolean readonly, CardDragHelper dragHelper) {
            this.source = source;
            this.index = index;
            this.card = card;
            this.horizontalOnly = horizontalOnly;
            this.disallowIntercept = disallowIntercept;
            this.shadowView = shadowView;
            this.touchSlop = touchSlop;
            this.readonly = readonly;
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
                    if (disallowIntercept && v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (readonly || card == null) return false;
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    boolean triggered = horizontalOnly
                            ? Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)
                            : Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop;
                    if (triggered) {
                        //禁止父级（尤其RecyclerView）拦截后续事件，保证拖拽期间持续收到MOVE/UP
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        dragHelper.startDrag(shadowView != null ? shadowView : v, source, index, card, event);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }
    }

    /**
     * 供DeckEditorManager调用：卡组网格卡片任意方向拖动触发拖拽
     */
    public View.OnTouchListener createDragTouchListener(DeckInfo.Type source, int index, Card card,
                                                        int touchSlop, boolean readonly) {
        return new CardDragTouchListener(source, index, card, false, false, null,
                touchSlop, readonly, dragHelper);
    }

    /**
     * 搜索结果item整体仅横向拖动触发，避免与列表纵向滚动冲突；拖拽阴影用shadowView（卡图）生成
     */
    public View.OnTouchListener createSearchDragTouchListener(Card card, View shadowView,
                                                              int touchSlop, boolean readonly) {
        return new CardDragTouchListener(null, -1, card, true, false, shadowView,
                touchSlop, readonly, dragHelper);
    }

    /**
     * 卡图作为拖拽把手：任意方向拖动即触发拖拽，并阻止列表拦截滚动
     */
    public View.OnTouchListener createSearchImageDragTouchListener(Card card,
                                                                   int touchSlop, boolean readonly) {
        return new CardDragTouchListener(null, -1, card, false, true, null,
                touchSlop, readonly, dragHelper);
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
        }
    }
}
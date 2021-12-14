package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemChildClickListener;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.tubb.smrv.SwipeHorizontalMenuLayout;

import org.greenrobot.eventbus.EventBus;

import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.CardListProvider;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.utils.CardUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class CardListAdapter extends BaseRecyclerAdapterPlus<Card, BaseViewHolder> implements CardListProvider {
    private StringManager mStringManager;
    private ImageTop mImageTop;
    private LimitList mLimitList;
    private boolean mItemBg;
    private ImageLoader imageLoader;
    private boolean mEnableSwipe = false;

    public CardListAdapter(Context context, ImageLoader imageLoader) {
        super(context,R.layout.item_search_card_swipe);
        this.imageLoader = imageLoader;
        mStringManager = DataManager.get().getStringManager();
    }

    @Override
    public int getCardsCount() {
        return getItemCount();
    }

    @Override
    public Card getCard(int posotion) {
        return getItem(posotion);
    }

    public void setEnableSwipe(boolean enableSwipe) {
        mEnableSwipe = enableSwipe;
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

    public void notifyItemChanged(Card card) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getCard(i).Code == card.Code) {
                notifyItemChanged(i);
            }
        }
    }

    public void hideMenu(View view) {
        if (view == null) {
            view = mShowMenuView;
        }
        if (view != null) {
            Object tag = view.getTag(view.getId());
            if (tag != null && tag instanceof ViewHolder) {
                ViewHolder viewHolder = (ViewHolder) tag;
                if (viewHolder.mMenuLayout.isMenuOpen()) {
                    viewHolder.mMenuLayout.smoothCloseMenu();
                }
            }
        }
    }

    public void showMenu(View view) {
        if (view != null) {
            Object tag = view.getTag(view.getId());
            if (tag != null && tag instanceof ViewHolder) {
                ViewHolder viewHolder = (ViewHolder) tag;
                viewHolder.mMenuLayout.smoothOpenMenu();
            }
        }
    }

//    @NonNull
//    @Override
//    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
//        View view = inflate(R.layout.item_search_card_swipe, parent, false);
//        if (mItemBg) {
//            view.setBackgroundResource(R.drawable.blue);
//        }
//        ViewHolder viewHolder = new ViewHolder(view);
//        viewHolder.mMenuLayout.setSwipeEnable(mEnableSwipe);
//        return viewHolder;
//    }

//    @Override
//    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
//        Card item = getCard(position);
//        if(item == null){
//            return;
//        }
//        imageLoader.bindImage(holder.cardImage, item, ImageLoader.Type.small);
//        holder.cardName.setText(item.Name);
//        if (item.isType(CardType.Monster)) {
//            holder.layout_atk.setVisibility(View.VISIBLE);
//            holder.layout_def.setVisibility(View.VISIBLE);
//            holder.layout_star_attr_race_scale.setVisibility(View.VISIBLE);
////            holder.view_bar.setVisibility(View.VISIBLE);
//            String star = "★" + item.getStar();
//            holder.cardLevel.setText(star);
//            holder.cardattr.setText(mStringManager.getAttributeString(item.Attribute));
//            holder.cardrace.setText(mStringManager.getRaceString(item.Race));
//            holder.cardAtk.setText((item.Attack < 0 ? "?" : String.valueOf(item.Attack)));
//            if (item.isType(CardType.Xyz)) {
//                holder.cardLevel.setTextColor(getColor(R.color.star_rank));
//            } else {
//                holder.cardLevel.setTextColor(getColor(R.color.star));
//            }
//            if (item.isType(CardType.Pendulum)) {
//                holder.layout_p_scale.setVisibility(View.VISIBLE);
//                holder.cardScale.setText(String.valueOf(item.LeftScale));
//            } else {
//                holder.layout_p_scale.setVisibility(View.GONE);
//            }
//            if (item.isType(CardType.Link)) {
//                holder.cardLevel.setVisibility(View.GONE);
//                holder.linkArrow.setVisibility(View.VISIBLE);
//                holder.cardDef.setText(item.getStar() < 0 ? "?" : "LINK-" + String.valueOf(item.getStar()));
//                holder.TextDef.setText("");
//                BaseActivity.showLinkArrows(item, holder.linkArrow);
//            } else {
//                holder.cardLevel.setVisibility(View.VISIBLE);
//                holder.linkArrow.setVisibility(View.GONE);
//                holder.cardDef.setText((item.Defense < 0 ? "?" : String.valueOf(item.Defense)));
//                holder.TextDef.setText("DEF/");
//            }
//
//
//        } else {
////            if (!showCode) {
////                holder.view_bar.setVisibility(View.INVISIBLE);
////            }
//            holder.layout_star_attr_race_scale.setVisibility(View.INVISIBLE);
//            holder.linkArrow.setVisibility(View.GONE);
//            holder.layout_atk.setVisibility(View.GONE);
//            holder.layout_def.setVisibility(View.GONE);
//        }
//        if (mImageTop == null) {
//            mImageTop = new ImageTop(context);
//        }
//        if (mLimitList != null) {
//            holder.rightImage.setVisibility(View.VISIBLE);
//            if (mLimitList.check(item, LimitType.Forbidden)) {
//                holder.rightImage.setImageBitmap(mImageTop.forbidden);
//            } else if (mLimitList.check(item, LimitType.Limit)) {
//                holder.rightImage.setImageBitmap(mImageTop.limit);
//            } else if (mLimitList.check(item, LimitType.SemiLimit)) {
//                holder.rightImage.setImageBitmap(mImageTop.semiLimit);
//            } else {
//                holder.rightImage.setVisibility(View.GONE);
//            }
//        } else {
//            holder.rightImage.setVisibility(View.GONE);
//        }
//        //卡片类型
//        holder.cardType.setText(CardUtils.getAllTypeString(item, mStringManager));
//        if (holder.codeView != null) {
//            holder.codeView.setText(String.format("%08d", item.getCode()));
//        }
//        bindMenu(holder, position);
//    }


    private int getColor(int id) {
        return context.getResources().getColor(id);
    }

    public void setItemBg(boolean itemBg) {
        this.mItemBg = itemBg;
    }

    public void setLimitList(LimitList limitList) {
        mLimitList = limitList;
    }

    public void bindMenu(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, int position) {

//        setOnItemChildClickListener((adapter, view, position1) -> {
//            switch (view.getId()){
//                case R.id.btn_add_main:
//
//                    mShowMenuView = holder.itemView;
//                    EventBus.getDefault().post(new CardInfoEvent(position1, true));
//                    break;
//                case R.id.btn_add_side:
//                    mShowMenuView = holder.itemView;
//                    EventBus.getDefault().post(new CardInfoEvent(position1, false));
//                    break;
//            }
//        });

//        if (holder.btnMain != null) {
           holder.getView(R.id.btn_add_main).setOnClickListener((v) -> {
                mShowMenuView = holder.itemView;
                EventBus.getDefault().post(new CardInfoEvent(position, true));
               ((SwipeHorizontalMenuLayout) holder.getView(R.id.swipe_layout)).smoothCloseMenu();
            });
//        }
//        if (holder.btnSide != null) {
            holder.getView(R.id.btn_add_side).setOnClickListener((v) -> {
                mShowMenuView = holder.itemView;
                EventBus.getDefault().post(new CardInfoEvent(position, false));
                ((SwipeHorizontalMenuLayout) holder.getView(R.id.swipe_layout)).smoothCloseMenu();
            });
//        }
        ((SwipeHorizontalMenuLayout) holder.getView(R.id.swipe_layout)).setSwipeEnable(mEnableSwipe);
    }

    private View mShowMenuView;

    @Override
    protected void convert(com.chad.library.adapter.base.viewholder.BaseViewHolder holder, Card item) {
        int position=holder.getAdapterPosition()-getHeaderLayoutCount();
        imageLoader.bindImage(holder.getView(R.id.card_image), item, ImageLoader.Type.small);
        holder.setText(R.id.card_name,item.Name);
        if (item.isType(CardType.Monster)) {
            holder.setGone(R.id.layout_atk,false);
            holder.setGone(R.id.layout_def,false);
            holder.setGone(R.id.star_attr_race_scale,false);
//            holder.view_bar.setVisibility(View.VISIBLE);
            String star = "★" + item.getStar();
            holder.setText(R.id.card_level,star);
            holder.setText(R.id.card_attr,mStringManager.getAttributeString(item.Attribute));
            holder.setText(R.id.card_race,mStringManager.getRaceString(item.Race));
            holder.setText(R.id.card_atk,(item.Attack < 0 ? "?" : String.valueOf(item.Attack)));
            if (item.isType(CardType.Xyz)) {
                holder.setTextColor(R.id.card_level,getColor(R.color.star_rank));
            } else {
                holder.setTextColor(R.id.card_level,getColor(R.color.star));
            }
            if (item.isType(CardType.Pendulum)) {
                holder.setGone(R.id.p_scale,false);
                holder.setText(R.id.card_scale,String.valueOf(item.LeftScale));
            } else {
                holder.setGone(R.id.p_scale,true);
            }
            if (item.isType(CardType.Link)) {
                holder.setGone(R.id.card_level,true);
                holder.setGone(R.id.search_link_arrows,false);
                holder.setText(R.id.card_def,item.getStar() < 0 ? "?" : "LINK-" + String.valueOf(item.getStar()));
                holder.setText(R.id.card_def,"");
                BaseActivity.showLinkArrows(item, holder.getView(R.id.search_link_arrows));
            } else {
                holder.setGone(R.id.card_level,false);
                holder.setGone(R.id.search_link_arrows,true);
                holder.setText(R.id.card_def,(item.Defense < 0 ? "?" : String.valueOf(item.Defense)));
                holder.setText(R.id.TextDef,"DEF/");
            }


        } else {
//            if (!showCode) {
//                holder.view_bar.setVisibility(View.INVISIBLE);
//            }
            holder.setVisible(R.id.star_attr_race_scale,false);
            holder.setGone(R.id.search_link_arrows,true);
            holder.setGone(R.id.layout_atk,true);
            holder.setGone(R.id.layout_def,true);
        }
        if (mImageTop == null) {
            mImageTop = new ImageTop(context);
        }
        if (mLimitList != null) {
            holder.setGone(R.id.right_top,false);
            if (mLimitList.check(item, LimitType.Forbidden)) {
                holder.setImageBitmap(R.id.right_top,mImageTop.forbidden);
            } else if (mLimitList.check(item, LimitType.Limit)) {
                holder.setImageBitmap(R.id.right_top,mImageTop.limit);
            } else if (mLimitList.check(item, LimitType.SemiLimit)) {
                holder.setImageBitmap(R.id.right_top,mImageTop.semiLimit);
            } else {
                holder.setGone(R.id.right_top,true);
            }
        } else {
            holder.setGone(R.id.right_top,true);
        }
        //卡片类型
        holder.setText(R.id.card_type,CardUtils.getAllTypeString(item, mStringManager));
        if (holder.getView(R.id.card_code) != null) {
            holder.setText(R.id.card_code,String.format("%08d", item.getCode()));
        }
        bindMenu(holder, position);
        if (mItemBg) {
            holder.setBackgroundResource(R.id.swipe_layout,R.drawable.blue);
        }
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


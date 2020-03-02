package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.tubb.smrv.SwipeHorizontalMenuLayout;

import org.greenrobot.eventbus.EventBus;

import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.CardListProvider;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.utils.CardUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.CardData;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class CardListAdapter extends BaseRecyclerAdapterPlus<Card, ViewHolder> implements CardListProvider {
    private StringManager mStringManager;
    private ImageTop mImageTop;
    private LimitList mLimitList;
    private boolean mItemBg;
    private ImageLoader imageLoader;
    private boolean mEnableSwipe = false;

    public CardListAdapter(Context context, ImageLoader imageLoader) {
        super(context);
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

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = inflate(R.layout.item_search_card_swipe, parent, false);
        if (mItemBg) {
            view.setBackgroundResource(R.drawable.blue);
        }
        ViewHolder viewHolder = new ViewHolder(view);
        viewHolder.mMenuLayout.setSwipeEnable(mEnableSwipe);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Card item = getItem(position);
        imageLoader.bindImage(holder.cardImage, item.Code);
        holder.cardName.setText(item.Name);
        if (item.isType(CardType.Monster)) {
            holder.cardLevel.setVisibility(View.VISIBLE);
            holder.layout_atk.setVisibility(View.VISIBLE);
            holder.layout_def.setVisibility(View.VISIBLE);
            if (item.isType(CardType.Link)) {
                holder.cardLevel.setVisibility(View.INVISIBLE);
            }
//            holder.view_bar.setVisibility(View.VISIBLE);
            String star = "★"+ item.getStar();
            /*for (int i = 0; i < item.getStar(); i++) {
                star += "★";
            }*/
            holder.cardLevel.setText(star);
            if (item.isType(CardType.Xyz)) {
                holder.cardLevel.setTextColor(getColor(R.color.star_rank));
            } else {
                holder.cardLevel.setTextColor(getColor(R.color.star));
            }
            holder.cardAtk.setText((item.Attack < 0 ? "?" : String.valueOf(item.Attack)));
            if (item.isType(CardType.Link)) {
                holder.cardDef.setText(item.getStar() < 0 ? "?" : "LINK-" + String.valueOf(item.getStar()));
                holder.TextDef.setText("");
            } else {
                holder.cardDef.setText((item.Defense < 0 ? "?" : String.valueOf(item.Defense)));
/*            }
                if(item.isType(CardType.Link)) {
                    holder.TextDef.setText(" ");
*/
            }


        } else {
//            if (!showCode) {
//                holder.view_bar.setVisibility(View.INVISIBLE);
//            }
            holder.cardLevel.setVisibility(View.INVISIBLE);
            holder.layout_atk.setVisibility(View.GONE);
            holder.layout_def.setVisibility(View.GONE);
        }
        if (mImageTop == null) {
            mImageTop = new ImageTop(context);
        }
        if (mLimitList != null) {
            holder.rightImage.setVisibility(View.VISIBLE);
            if (mLimitList.check(item, LimitType.Forbidden)) {
                holder.rightImage.setImageBitmap(mImageTop.forbidden);
            } else if (mLimitList.check(item, LimitType.Limit)) {
                holder.rightImage.setImageBitmap(mImageTop.limit);
            } else if (mLimitList.check(item, LimitType.SemiLimit)) {
                holder.rightImage.setImageBitmap(mImageTop.semiLimit);
            } else {
                holder.rightImage.setVisibility(View.GONE);
            }
        } else {
            holder.rightImage.setVisibility(View.GONE);
        }
        //卡片类型
        holder.cardType.setText(CardUtils.getAllTypeString(item, mStringManager));
        if (holder.codeView != null) {
            int t = item.Alias - item.Code;
            if (t > 10 || t < -10) {
                holder.codeView.setText(String.format("%08d", item.Code));
            } else {
                holder.codeView.setText(String.format("%08d", item.Alias));
            }
        }
        bindMenu(holder, position);
    }


    private int getColor(int id) {
        return context.getResources().getColor(id);
    }

    public void setItemBg(boolean itemBg) {
        this.mItemBg = itemBg;
    }

    public void setLimitList(LimitList limitList) {
        mLimitList = limitList;
    }

    public void bindMenu(ViewHolder holder, int position) {
        if (holder.btnMain != null) {
            holder.btnMain.setOnClickListener((v) -> {
                mShowMenuView = holder.itemView;
                EventBus.getDefault().post(new CardInfoEvent(position, true));
//                holder.mMenuLayout.smoothCloseMenu();
            });
        }
        if (holder.btnSide != null) {
            holder.btnSide.setOnClickListener((v) -> {
                mShowMenuView = holder.itemView;
                EventBus.getDefault().post(new CardInfoEvent(position, false));
//                holder.mMenuLayout.smoothCloseMenu();
            });
        }
    }

    private View mShowMenuView;
}

class ViewHolder extends BaseRecyclerAdapterPlus.BaseViewHolder {
    ImageView cardImage;
    TextView cardName;
    TextView cardLevel;
    TextView cardType;
    TextView cardAtk;
    TextView cardDef;
    TextView TextDef;


    ImageView rightImage;
    View layout_atk;
    View layout_def;
    View view_bar;
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
        cardLevel = $(R.id.card_level);
        layout_atk = $(R.id.layout_atk);
        layout_def = $(R.id.layout_def);
        view_bar = $(R.id.view_bar);
        rightImage = $(R.id.right_top);
        codeView = $(R.id.card_code);
        TextDef = $(R.id.TextDef);
        btnMain = $(R.id.btn_add_main);
        btnSide = $(R.id.btn_add_side);
        mMenuLayout = $(R.id.swipe_layout);
//            File outFile = new File(AppsSettings.get().getCoreSkinPath(), Constants.UNKNOWN_IMAGE);
//            ImageLoader.get().$(context, outFile, cardImage, outFile.getName().endsWith(Constants.BPG), 0, null);
    }
}


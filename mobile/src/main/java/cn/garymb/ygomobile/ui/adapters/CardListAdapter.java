package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.tubb.smrv.SwipeHorizontalMenuLayout;

import org.greenrobot.eventbus.EventBus;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.CardListProvider;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class CardListAdapter extends BaseRecyclerAdapterPlus<Card, BaseViewHolder> implements CardListProvider {
    private final StringManager mStringManager;
    private ImageTop mImageTop;
    private TextView tv_limit_num;
    private LimitList mLimitList;//同时只能适用一种禁卡表，所以不管是常规禁限还是genesys禁限，只能使用一个
    private boolean mItemBg;
    private final ImageLoader imageLoader;
    private boolean mEnableSwipe = false;

    public CardListAdapter(Context context, ImageLoader imageLoader) {
        super(context, R.layout.item_search_card_swipe);
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
        int position = holder.getBindingAdapterPosition() - getHeaderLayoutCount();
        imageLoader.bindImage(holder.getView(R.id.card_image), item, ImageLoader.Type.small);
        holder.setText(R.id.card_name, item.Name);
        if (item.isType(CardType.Monster)) {
            holder.setGone(R.id.layout_atk, false);
            holder.setGone(R.id.layout_def, false);
            holder.setGone(R.id.star_attr_race_scale, false);
//            holder.view_bar.setVisibility(View.VISIBLE);
            String star = "★" + item.getStar();
            holder.setText(R.id.card_level, star);
            holder.setText(R.id.card_attr, mStringManager.getAttributeString(item.Attribute));
            holder.setText(R.id.card_race, mStringManager.getRaceString(item.Race));
            holder.setText(R.id.card_atk, (item.Attack < 0 ? "?" : String.valueOf(item.Attack)));
            if (item.isType(CardType.Xyz)) {
                holder.setTextColor(R.id.card_level, getColor(R.color.star_rank));
            } else {
                holder.setTextColor(R.id.card_level, getColor(R.color.star));
            }
            if (item.isType(CardType.Pendulum)) {
                holder.setGone(R.id.p_scale, false);
                holder.setText(R.id.card_scale, String.valueOf(item.LeftScale));
            } else {
                holder.setGone(R.id.p_scale, true);
            }
            if (item.isType(CardType.Link)) {
                holder.setGone(R.id.card_level, true);
                holder.setGone(R.id.search_link_arrows, false);
                holder.setText(R.id.card_def, item.getStar() < 0 ? "?" : String.valueOf(item.getStar()));
                holder.setText(R.id.TextDef, "LINK-");
                BaseActivity.showLinkArrows(item, holder.getView(R.id.search_link_arrows));
            } else {
                holder.setGone(R.id.card_level, false);
                holder.setGone(R.id.search_link_arrows, true);
                holder.setText(R.id.card_def, (item.Defense < 0 ? "?" : String.valueOf(item.Defense)));
                holder.setText(R.id.TextDef, "DEF/");
            }


        } else {
//            if (!showCode) {
//                holder.view_bar.setVisibility(View.INVISIBLE);
//            }
            holder.setVisible(R.id.star_attr_race_scale, false);
            holder.setGone(R.id.search_link_arrows, true);
            holder.setGone(R.id.layout_atk, true);
            holder.setGone(R.id.layout_def, true);
        }
        if (mImageTop == null) {
            mImageTop = new ImageTop(context);
        }
        TextView tv_limit_num = holder.getView(R.id.tv_limit_num);//创建该布局的对象，以便对它的字体颜色，大小，文本进行自由设置
        if (mLimitList != null) {

            holder.setGone(R.id.right_top, false);
            if (mLimitList.check(item, LimitType.Forbidden)) {
                holder.setImageBitmap(R.id.right_top, mImageTop.forbidden);
                tv_limit_num.setText("");
            } else if (mLimitList.check(item, LimitType.Limit)) {
                holder.setImageBitmap(R.id.right_top, mImageTop.limit);
                tv_limit_num.setText("1");
                tv_limit_num.setTextSize(12);
                tv_limit_num.setTextColor(YGOUtil.c(R.color.yellow));
            } else if (mLimitList.check(item, LimitType.SemiLimit)) {
                holder.setImageBitmap(R.id.right_top, mImageTop.semiLimit);
                tv_limit_num.setText("2");
                tv_limit_num.setTextSize(12);
                tv_limit_num.setTextColor(YGOUtil.c(R.color.yellow));
            } else if (mLimitList.check(item, LimitType.GeneSys)) {
                Integer creditValue = 0;
                if (mLimitList.getCredits() != null) {
                    creditValue = mLimitList.getCredits().get(item.Alias == 0 ? item.Code : item.Alias);
                    holder.setImageBitmap(R.id.right_top, mImageTop.credits);
                    tv_limit_num.setText(creditValue.toString());
                    tv_limit_num.setTextSize((creditValue > -10 && creditValue < 100) ? 10 : 8);
                    tv_limit_num.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
                }
            } else {
                holder.setGone(R.id.right_top, true);
                holder.setText(R.id.tv_limit_num, "");
            }
        } else {
            holder.setGone(R.id.right_top, true);
            holder.setText(R.id.tv_limit_num, "");
        }
        //卡片类型
        holder.setText(R.id.card_type, CardUtils.getAllTypeString(item, mStringManager));

        holder.setText(R.id.card_code, String.format("%08d", item.getCode()));

        // 根据设置控制添加按钮的可见性
        holder.getView(R.id.ll_add_btn).setVisibility(showAddButtons ? View.VISIBLE : View.GONE);

        bindMenu(holder, position);
        if (mItemBg) {
            holder.setBackgroundResource(R.id.swipe_layout, R.drawable.list_item_bg);
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


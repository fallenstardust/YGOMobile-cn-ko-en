package cn.garymb.ygomobile.ui.cards.deck;


import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.DeckLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.CardListProvider;
import cn.garymb.ygomobile.utils.CardSort;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;
/**
 * 在对应的recyclerview中添加了很多透明的item来“占位”，占位后便于根据“拖动卡片的位置”计算添加的卡片类型（是主卡组、额外卡组还是副卡组）
 */
public class DeckAdapater extends DeckAdapaterBase {

    public DeckAdapater(Context context, RecyclerView recyclerView, ImageLoader imageLoader) {
        super(context, recyclerView, imageLoader);
    }

    /**
     * 获取当前卡组状态的副本
     *
     * @return 当前卡组状态的副本
     */
    public DeckInfo getCurrentState() {
        // 创建当前状态的深拷贝
        DeckInfo currentState = new DeckInfo();

        // 从 mItems 中提取不同类型的卡片
        List<Card> mainCards = new ArrayList<>();
        List<Card> extraCards = new ArrayList<>();
        List<Card> sideCards = new ArrayList<>();

        for (DeckItem item : mItems) {
            if (item != null && item.getCardInfo() != null) {
                switch (item.getType()) {
                    case MainCard:
                        mainCards.add(item.getCardInfo());
                        break;
                    case ExtraCard:
                        extraCards.add(item.getCardInfo());
                        break;
                    case SideCard:
                        sideCards.add(item.getCardInfo());
                        break;
                }
            }
        }

        // 必须通过 setMainCards/setExtraCards/setSideCards 赋值，
        // 以同步刷新 DeckInfo 内部的 mainCount/extraCount/sideCount，
        // 否则 getMainCount() 会一直是 0，导致积分上限无法随主卡组数量变化。
        currentState.setMainCards(mainCards);
        currentState.setExtraCards(extraCards);
        currentState.setSideCards(sideCards);
        // 通过 mDeckInfo 获取 source
        if (mDeckInfo != null) {
            currentState.source = mDeckInfo.source;
        }
        return currentState;
    }

    public boolean AddCard(Card cardInfo, DeckItemType type) {
        if (cardInfo == null) return false;
        if (cardInfo.isType(CardType.Token)) {
            return false;
        }
        if (type == DeckItemType.MainCard) {
            if (getMainCount() >= Constants.DECK_MAIN_MAX) {
                return false;
            }
            int id = DeckItem.MainStart + getMainCount();
            removeItem(DeckItem.MainEnd);
            addItem(id, new DeckItem(cardInfo, type));
            //notifyItemChanged(DeckItem.MainEnd);//todo 这行好像没啥用啊？？
            notifyItemChanged(id);
            notifyItemChanged(DeckItem.MainLabel);
            return true;
        }
        if (type == DeckItemType.ExtraCard) {
            if (getExtraCount() >= Constants.DECK_EXTRA_MAX) {
                return false;
            }
            int id = DeckItem.ExtraStart + getExtraCount();
            removeItem(DeckItem.ExtraEnd);
            addItem(id, new DeckItem(cardInfo, type));
            notifyItemChanged(DeckItem.ExtraEnd);
            notifyItemChanged(id);
            notifyItemChanged(DeckItem.ExtraLabel);
            return true;
        }
        if (type == DeckItemType.SideCard) {
            if (getSideCount() >= Constants.DECK_SIDE_MAX) {
                return false;
            }
            int id = DeckItem.SideStart + getSideCount();
            removeItem(DeckItem.SideEnd);
            addItem(id, new DeckItem(cardInfo, type));
            notifyItemChanged(DeckItem.SideEnd);
            notifyItemChanged(id);
            notifyItemChanged(DeckItem.SideLabel);
            return true;
        }
        return false;
    }

    /**
     * 拖拽定点插入的结果：成功 或 各类失败原因（用于toast提示）
     * 禁限/积分数量限制不在此校验，由调用方先行校验(FAILED_LIMIT)
     */
    public enum DragInsertResult {
        SUCCESS,
        FAILED_TOKEN,        // 衍生物不能加入卡组
        WRONG_PLACE_MAIN,    // 额外卡组卡牌不能插入主卡组区域
        WRONG_PLACE_EXTRA,   // 普通卡牌不能插入额外卡组区域
        MAIN_FULL,           // 主卡组已满
        EXTRA_FULL,          // 额外卡组已满
        SIDE_FULL,           // 副卡组已满
        FAILED_LIMIT,        // 卡片数量或积分限制（具体原因由校验方提示）
        FAILED               // 其他失败
    }

    /**
     * 将卡片插入到指定卡区的精确位置（拖拽松手后落点卡到停留位置，挤开此处其他卡片）。
     * 各区域item总数不变：目标区域末尾空格被移除，插入点新增卡片，副产物空格补回区域末尾。
     *
     * @param cardInfo 要插入的卡片
     * @param type     目标卡区类型（MainCard/ExtraCard/SideCard）
     * @param insertPos 在mItems中的绝对插入位置（分隔栏落点传区域末尾）
     * @return 插入结果
     */
    public DragInsertResult dragInsertAt(Card cardInfo, DeckItemType type, int insertPos) {
        if (cardInfo == null || type == null) {
            return DragInsertResult.FAILED;
        }
        if (cardInfo.isType(CardType.Token)) {
            return DragInsertResult.FAILED_TOKEN;
        }
        switch (type) {
            case MainCard: {
                if (cardInfo.isExtraCard()) {
                    return DragInsertResult.WRONG_PLACE_MAIN;
                }
                int count = getMainCount();
                if (count >= Constants.DECK_MAIN_MAX) {
                    return DragInsertResult.MAIN_FULL;
                }
                int rel = insertPos - DeckItem.MainStart;
                if (rel < 0) rel = 0;
                if (rel > count) rel = count;
                int pos = DeckItem.MainStart + rel;
                DeckItem space = removeItem(DeckItem.MainEnd);
                addItem(pos, new DeckItem(cardInfo, DeckItemType.MainCard));
                addItem(DeckItem.SideEnd, space);
                // 主区：末尾空格移除+插入点新增，插入点后的卡依次后移一位；副区末尾新增空格
                notifyItemRemoved(DeckItem.MainEnd);
                notifyItemInserted(pos);
                if (pos + 1 <= DeckItem.MainEnd - 1) {
                    notifyItemRangeChanged(pos + 1, DeckItem.MainEnd - 1 - pos);
                }
                notifyItemChanged(DeckItem.SideEnd);
                notifyItemChanged(DeckItem.MainLabel);
                notifyItemChanged(DeckItem.SideLabel);
                return DragInsertResult.SUCCESS;
            }
            case ExtraCard: {
                if (!cardInfo.isExtraCard()) {
                    return DragInsertResult.WRONG_PLACE_EXTRA;
                }
                int count = getExtraCount();
                if (count >= Constants.DECK_EXTRA_MAX) {
                    return DragInsertResult.EXTRA_FULL;
                }
                int rel = insertPos - DeckItem.ExtraStart;
                if (rel < 0) rel = 0;
                if (rel > count) rel = count;
                int pos = DeckItem.ExtraStart + rel;
                DeckItem space = removeItem(DeckItem.ExtraEnd);
                addItem(pos, new DeckItem(cardInfo, DeckItemType.ExtraCard));
                addItem(DeckItem.SideEnd, space);
                // 额外区末尾空格被移除：主区末尾空格前移顶替额外区末尾，副区末尾新增空格
                notifyItemRemoved(DeckItem.ExtraEnd);
                notifyItemInserted(pos);
                notifyItemChanged(DeckItem.MainEnd);
                if (pos + 1 <= DeckItem.ExtraEnd - 1) {
                    notifyItemRangeChanged(pos + 1, DeckItem.ExtraEnd - 1 - pos);
                }
                notifyItemChanged(DeckItem.SideEnd);
                notifyItemChanged(DeckItem.ExtraLabel);
                notifyItemChanged(DeckItem.SideLabel);
                return DragInsertResult.SUCCESS;
            }
            case SideCard: {
                int count = getSideCount();
                if (count >= Constants.DECK_SIDE_MAX) {
                    return DragInsertResult.SIDE_FULL;
                }
                int rel = insertPos - DeckItem.SideStart;
                if (rel < 0) rel = 0;
                if (rel > count) rel = count;
                int pos = DeckItem.SideStart + rel;
                DeckItem space = removeItem(DeckItem.SideEnd);
                addItem(pos, new DeckItem(cardInfo, DeckItemType.SideCard));
                // 副卡组后方仅剩尾部占位项，移除+插入已覆盖全部变化
                notifyItemRemoved(DeckItem.SideEnd);
                notifyItemInserted(pos);
                notifyItemChanged(DeckItem.SideLabel);
                return DragInsertResult.SUCCESS;
            }
            default:
                return DragInsertResult.FAILED;
        }
    }

    public void setDeck(DeckInfo deckinfo, boolean isPack) {
        mDeckInfo = deckinfo;
        if (deckinfo != null) {
            loadData(deckinfo, isPack);
        }
        mDeckMd5 = DeckItemUtils.makeMd5(mItems);
    }

    public DeckInfo read(CardLoader cardLoader, File file, LimitList limitList) {
        if (limitList != null) {
            setLimitList(limitList);
        }
        return DeckLoader.readDeck(cardLoader, file);
    }

    public boolean save(File file, boolean withoutId) {
        //保存了，记录状态
        mDeckMd5 = DeckItemUtils.makeMd5(mItems);
        return DeckItemUtils.save(mItems, withoutId ? null : deckId, withoutId ? null : userId, file);
    }

    public Deck toDeck(File file) {
        return DeckItemUtils.toDeck(mItems, file);
    }

    protected void loadData(DeckInfo deckInfo, boolean isPack) {
        deckId = null;
        userId = null;
        mCount.clear();
        mMainCount = 0;
        mExtraCount = 0;
        mSideCount = 0;
        mMainMonsterCount = 0;
        mMainSpellCount = 0;
        mMainTrapCount = 0;
        mExtraFusionCount = 0;
        mExtraXyzCount = 0;
        mExtraSynchroCount = 0;
        mExtraLinkCount = 0;
        mSideMonsterCount = 0;
        mSideSpellCount = 0;
        mSideTrapCount = 0;
        mItems.clear();
        DeckItemUtils.makeItems(deckInfo, isPack, this);
    }

    public boolean isChanged() {
        String md5 = DeckItemUtils.makeMd5(mItems);
        //Log.d("kk", mDeckMd5 + "/" + md5);
        return !TextUtils.equals(mDeckMd5, md5);
    }

    public void notifyItemChanged(Card card) {
        for (int i = 0; i < getItemCount(); i++) {
            DeckItem item = getItem(i);
            Card c = item.getCardInfo();
            if (c != null && c.Code == card.Code) {
                notifyItemChanged(i);
            }
        }
    }

    @Override
    public DeckViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflating a layout from XML and returning the holder
        View view = mLayoutInflater.inflate(R.layout.item_deck_card, parent, false);
        return new DeckViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DeckViewHolder holder, int position) {
        //Populating data into the item through holder
        //根据data的内容调整view的样式

        DeckItem item = mItems.get(position);

        // 确保每次绑定时都重置视图状态
        holder.clear(); // 清理旧图片和状态

        if (item != null && item.getCardInfo() != null) {
            // 绑定新数据
            holder.bind(item, imageLoader);
            holder.showImage();
        } else {
            // 显示空状态
            holder.showEmpty();
        }

        holder.setItemType(item.getType());
        if (item.getType() == DeckItemType.MainLabel || item.getType() == DeckItemType.SideLabel
                || item.getType() == DeckItemType.ExtraLabel) {//处理分隔栏的item view
            if (item.getType() == DeckItemType.MainLabel) {
                holder.setText(getMainString());
            } else if (item.getType() == DeckItemType.SideLabel) {
                holder.setText(getSideString());
            } else if (item.getType() == DeckItemType.ExtraLabel) {
                holder.setText(getExtraString());
            }
        } else {//处理展示卡图的item view
            /* 动态计算控件的高度，优先根据卡图的实际大小计算高度，如果结果<=0，则根据recyclerView的宽度计算控件高度 */
            if (mHeight <= 0) {
                if (holder.cardImage.getMeasuredWidth() > 0) {
                    mWidth = holder.cardImage.getMeasuredWidth();
                    if (mWidth >= 0) {
                        mHeight = scaleHeight(mWidth);
                    }
                }
                if (mHeight <= 0) {
                    makeHeight();
                }
//                Log.i("kk", "w=" + mWidth + ",h=" + mHeight);
            }
//            holder.cardImage.setLayoutParams(new RelativeLayout.LayoutParams(holder.cardImage.getMeasuredWidth(), mHeight));
            //显示卡片
            holder.showImage();
            holder.setSize(mHeight);
            if (item.getType() == DeckItemType.Space) {//占位，不显示卡图
                holder.setCardType(0);
                holder.showEmpty();
            } else {//非占位，显示卡图

                Card cardInfo = item.getCardInfo();
//                holder.cardImage.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        if(cardInfo!=null)
//                            Toast.makeText(context,"不为空点击"+cardInfo.Name,Toast.LENGTH_SHORT).show();
//                        else
//                            Toast.makeText(context,"空点击"+position,Toast.LENGTH_SHORT).show();
//                    }
//                });//将cardInfo在LimitList中检查，根据结果（限制、准限制、禁止）对应显示卡图
                if (cardInfo != null) {
                    holder.setCardType(cardInfo.Type);
                    if (mImageTop == null) {
                        mImageTop = new ImageTop(context);
                    }
                    if (mLimitList != null) {
                        if (mLimitList.check(cardInfo, LimitType.Forbidden)) {
                            holder.setRightImage(mImageTop.forbidden);
                            holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                        } else if (mLimitList.check(cardInfo, LimitType.Limit)) {
                            holder.setRightImage(mImageTop.limit);
                            holder.setLimitText("1", YGOUtil.c(R.color.yellow), 10);
                        } else if (mLimitList.check(cardInfo, LimitType.SemiLimit)) {
                            holder.setRightImage(mImageTop.semiLimit);
                            holder.setLimitText("2", YGOUtil.c(R.color.yellow), 10);
                        } else if (mLimitList.check(cardInfo, LimitType.GeneSys)) {
                            // 统一使用 getGeneSysCredit 取值：与 check(GeneSys) 同口径，既命中“宽松同名折叠键”，
                            // 也能在“卡片 code 在表中、但 alias 不在表中(|alias-code|<=20)”时回退到 code 的分值
                            Integer creditValue = mLimitList.getGeneSysCredit(cardInfo);
                            if (creditValue != null) {
                                holder.setRightImage(mImageTop.credits);
                                holder.setLimitText(creditValue.toString(), YGOUtil.c(R.color.holo_blue_bright), (creditValue > -10 && creditValue < 100) ? 8 : 6);
                            } else {
                                holder.setRightImage(null);
                                holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                            }
                        } else {
                            holder.setRightImage(null);
                            holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                        }
                    } else {
                        holder.setRightImage(null);
                        holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                    }
//                    holder.useDefault();
                    imageLoader.bindImage(holder.cardImage, cardInfo, ImageLoader.Type.small);
                } else {
                    holder.setCardType(0);
                    holder.setRightImage(null);
                    holder.useDefault(imageLoader, mWidth, mHeight);
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }
}

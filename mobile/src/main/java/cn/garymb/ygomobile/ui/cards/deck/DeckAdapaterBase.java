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
 * Base layer of the split inheritance chain (DeckAdapaterBase <- DeckAdapater):
 * owns the deck item list and all count bookkeeping fields, the card-count
 * add/remove accounting, the in-place sort routines, the geometry / string
 * helpers and every plain getter/setter. The composite public API (state
 * snapshot, add / drag-insert, file IO, load and the RecyclerView view
 * binding) lives in the leaf class and calls down into these primitives.
 */
abstract class DeckAdapaterBase extends RecyclerView.Adapter<DeckViewHolder> implements CardListProvider {

    protected final List<DeckItem> mItems = new ArrayList<>();
    protected final SparseArray<Integer> mCount = new SparseArray<>();
    protected final Context context;
    protected final LayoutInflater mLayoutInflater;
    protected final int Padding = 1;
    protected final RecyclerView recyclerView;
    protected final Random mRandom;
    protected final ImageLoader imageLoader;
    protected ImageTop mImageTop;
    protected TextView tv_deck_limit_num;
    protected int mMainCount;
    protected int mExtraCount;
    protected int mSideCount;
    protected int mMainMonsterCount;
    protected int mMainSpellCount;
    protected int mMainTrapCount;
    protected int mExtraFusionCount;
    protected int mExtraXyzCount;
    protected int mExtraSynchroCount;
    protected int mExtraLinkCount;
    protected int mSideMonsterCount;
    protected int mSideSpellCount;
    protected int mSideTrapCount;
    protected int mFullWidth;
    protected int mWidth;
    protected int mHeight;
    protected DeckViewHolder mHeadHolder;
    protected DeckItem mRemoveItem;
    protected int mRemoveIndex;
    protected LimitList mLimitList;//同时只能适用一种禁卡表，所以不管是常规禁限还是genesys禁限，只能使用一个
    protected boolean showHead = false;
    protected String mDeckMd5;
    protected DeckInfo mDeckInfo;
    protected String deckId = null;
    protected Integer userId = null;

    /**
     * Constructor body anchor kept verbatim so the final fields are
     * assigned exactly once from the leaf forwarding constructor.
     */
    protected DeckAdapaterBase(Context context, RecyclerView recyclerView, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
        this.recyclerView = recyclerView;
        mLayoutInflater = LayoutInflater.from(context);
        mRandom = new Random(System.currentTimeMillis() + SystemClock.elapsedRealtime());
    }

    public Context getContext() {
        return context;
    }

    protected void makeHeight() {
        mFullWidth = recyclerView.getMeasuredWidth() - recyclerView.getPaddingRight() - recyclerView.getPaddingLeft();
        mWidth = mFullWidth / Constants.DECK_WIDTH_COUNT - 2 * Padding;
        mHeight = scaleHeight(mWidth);
    }

    protected int scaleHeight(int width) {
        return Math.round((float) width * ((float) Constants.CORE_SKIN_CARD_COVER_SIZE[1] / (float) Constants.CORE_SKIN_CARD_COVER_SIZE[0]));
    }

    public SparseArray<Integer> getCardCount() {
        return mCount;
    }

    public void unSort() {
        if (mMainCount == 0) return;
        for (int i = 0; i < mMainCount; i++) {
            int index = mRandom.nextInt(mMainCount - i);
            Collections.swap(mItems, DeckItem.MainStart + i, DeckItem.MainStart + i + index);
        }
        notifyItemRangeChanged(DeckItem.MainStart, DeckItem.MainStart + getMainCount());
    }

    protected boolean comp(DeckItem d1, DeckItem d2) {
        if (d1.getType() == d2.getType()) {
            Card c1 = d1.getCardInfo();
            Card c2 = d2.getCardInfo();
            return CardSort.FULL_ASC.compare(c1, c2) > 0;
        }
        return (d1.getType().ordinal() - d2.getType().ordinal()) > 0;
    }

    protected int sortMain() {
        int len = getMainCount();
        for (int i = 0; i < len - 1; i++) {
            for (int j = 0; j < len - 1 - i; j++) {
                DeckItem d1 = mItems.get(DeckItem.MainStart + j);
                DeckItem d2 = mItems.get(DeckItem.MainStart + j + 1);
                if (comp(d1, d2)) {
                    DeckItem tmp = new DeckItem(d2);
                    d2.set(d1);
                    d1.set(tmp);
                }
            }
        }
        return len;
    }

    @Override
    public int getCardsCount() {
        return mMainCount + mExtraCount + mSideCount;
    }

    @Override
    public @Nullable
    Card getCard(int posotion) {
        int count = mMainCount;
        int index = 0;
        if (posotion < count) {
            index = DeckItem.MainStart + posotion;
        } else {
            count += mExtraCount;
            if (posotion < count) {
                index = DeckItem.ExtraStart + (posotion - mMainCount);
            } else {
                count += mSideCount;
                if (posotion < count) {
                    index = DeckItem.SideStart + (posotion - mMainCount - mExtraCount);
                }
            }
        }
        DeckItem deckItem = getItem(index);
//        Log.d("CardDetail", "posotion=" + posotion + ",index=" + index+",main=" + mMainCount + ",extra=" + mExtraCount + ",side=" + mSideCount);
        if (deckItem != null) {
            return deckItem.getCardInfo();
        }
        return null;
    }

    public int getCardPosByView(int pos) {
        //调整pos 前面的数量和当前的位置
        int index;
        if (pos < DeckItem.MainEnd) {
            index = pos - DeckItem.MainStart;
        } else if (pos < DeckItem.ExtraEnd) {
            index = mMainCount + (pos - DeckItem.ExtraStart);
        } else {
            index = mMainCount + mExtraCount + (pos - DeckItem.SideStart);
        }
//        Log.d("CardDetail", "adapter " + pos + ",index=" + index + ",main=" + mMainCount + ",extra=" + mExtraCount + ",side=" + mSideCount);
        return index;
    }

    protected int sortExtra() {
        int len = getExtraCount();
        for (int i = 0; i < len - 1; i++) {
            for (int j = 0; j < len - 1 - i; j++) {
                DeckItem d1 = mItems.get(DeckItem.ExtraStart + j);
                DeckItem d2 = mItems.get(DeckItem.ExtraStart + j + 1);
                if (comp(d1, d2)) {
//                    Log.d("kk", "swap extra:" + j + "->" + (j + 1));
                    DeckItem tmp = new DeckItem(d2);
                    d2.set(d1);
                    d1.set(tmp);
                }
            }
        }
        return len;
    }

    protected int sortSide() {
        int len = getSideCount();
        for (int i = 0; i < len - 1; i++) {
            for (int j = 0; j < len - 1 - i; j++) {
                DeckItem d1 = mItems.get(DeckItem.SideStart + j);
                DeckItem d2 = mItems.get(DeckItem.SideStart + j + 1);
                if (comp(d1, d2)) {
                    DeckItem tmp = new DeckItem(d2);
                    d2.set(d1);
                    d1.set(tmp);
                }
            }
        }
        return len;
    }

    public void sort() {
        int main = sortMain();
        int extra = sortExtra();
        int side = sortSide();
        notifyItemRangeChanged(DeckItem.MainStart, DeckItem.MainStart + main);
        notifyItemRangeChanged(DeckItem.ExtraStart, DeckItem.ExtraStart + extra);
        notifyItemRangeChanged(DeckItem.SideStart, DeckItem.SideStart + side);
    }

    protected void addCount(Card cardInfo, DeckItemType type) {
        if (cardInfo == null) return;
        Integer code = cardInfo.getGameCode();
        Integer i = mCount.get(code);
        if (i == null) {
            mCount.put(code, 1);
        } else {
            mCount.put(code, i + 1);
        }
        switch (type) {
            case MainCard:
                mMainCount++;
                if (cardInfo.isType(CardType.Monster)) {
                    mMainMonsterCount++;
                } else if (cardInfo.isType(CardType.Spell)) {
                    mMainSpellCount++;
                } else if (cardInfo.isType(CardType.Trap)) {
                    mMainTrapCount++;
                }
                break;
            case ExtraCard:
                mExtraCount++;
                if (cardInfo.isType(CardType.Fusion)) {
                    mExtraFusionCount++;
                } else if (cardInfo.isType(CardType.Synchro)) {
                    mExtraSynchroCount++;
                } else if (cardInfo.isType(CardType.Xyz)) {
                    mExtraXyzCount++;
                } else if (cardInfo.isType(CardType.Link)) {
                    mExtraLinkCount++;
                }
                break;
            case SideCard:
                mSideCount++;
                if (cardInfo.isType(CardType.Monster)) {
                    mSideMonsterCount++;
                } else if (cardInfo.isType(CardType.Spell)) {
                    mSideSpellCount++;
                } else if (cardInfo.isType(CardType.Trap)) {
                    mSideTrapCount++;
                }
                break;
        }
    }

    protected void removeCount(Card cardInfo, DeckItemType type) {
        if (cardInfo == null) return;
        int code = cardInfo.getGameCode();
        Integer i = mCount.get(code);
        if (i == null) {
            mCount.put(code, 0);
        } else {
            mCount.put(code, Math.max(0, i - 1));
        }
        switch (type) {
            case MainCard:
                mMainCount--;
                if (cardInfo.isType(CardType.Monster)) {
                    mMainMonsterCount--;
                } else if (cardInfo.isType(CardType.Spell)) {
                    mMainSpellCount--;
                } else if (cardInfo.isType(CardType.Trap)) {
                    mMainTrapCount--;
                }
                break;
            case ExtraCard:
                mExtraCount--;
                if (cardInfo.isType(CardType.Fusion)) {
                    mExtraFusionCount--;
                } else if (cardInfo.isType(CardType.Synchro)) {
                    mExtraSynchroCount--;
                } else if (cardInfo.isType(CardType.Xyz)) {
                    mExtraXyzCount--;
                } else if (cardInfo.isType(CardType.Link)) {
                    mExtraLinkCount--;
                }
                break;
            case SideCard:
                mSideCount--;
                if (cardInfo.isType(CardType.Monster)) {
                    mSideMonsterCount--;
                } else if (cardInfo.isType(CardType.Spell)) {
                    mSideSpellCount--;
                } else if (cardInfo.isType(CardType.Trap)) {
                    mSideTrapCount--;
                }
                break;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public DeckItem getItem(int position) {
        return mItems.get(position);
    }

    public int getMainCount() {
        return mMainCount;
    }

    public int getExtraCount() {
        return mExtraCount;
    }

    public int getSideCount() {
        return mSideCount;
    }

    public DeckInfo getDeckInfo() {
        return mDeckInfo;
    }

    public LimitList getLimitList() {
        return mLimitList;
    }

    public void setLimitList(LimitList limitList) {
        if (limitList.getCreditLimits() != null) {
            AppsSettings.get().setGenesysMode(1);
        } else {
            AppsSettings.get().setGenesysMode(0);
        }
        mLimitList = limitList;
    }

    public @Nullable
    File getYdkFile() {
        if (mDeckInfo != null) {
            return mDeckInfo.source;
        }
        return null;
    }

    protected <T> int length(List<T> list) {
        return list == null ? 0 : list.size();
    }

    protected String getString(int id, Object... args) {
        return context.getString(id, args);
    }

    protected String getMainString() {
        return getString(R.string.deck_main, mMainCount, mMainMonsterCount, mMainSpellCount, mMainTrapCount);
    }

    protected String getExtraString() {

        return getString(R.string.deck_extra, mExtraCount, mExtraFusionCount, mExtraSynchroCount, mExtraXyzCount, mExtraLinkCount);
    }

    protected String getSideString() {
        return getString(R.string.deck_side, mSideCount, mSideMonsterCount, mSideSpellCount, mSideTrapCount);
    }

    public void addItem(DeckItem deckItem) {
        addCount(deckItem.getCardInfo(), deckItem.getType());
        mItems.add(deckItem);
    }

    public void addItem(int pos, DeckItem deckItem) {
        if (deckItem.getCardInfo() != null) {
            if (pos >= DeckItem.MainStart && pos <= DeckItem.MainEnd) {
                deckItem.setType(DeckItemType.MainCard);
            } else if (pos >= DeckItem.ExtraStart && pos <= DeckItem.ExtraEnd) {
                deckItem.setType(DeckItemType.ExtraCard);
            } else if (pos >= DeckItem.SideStart && pos <= DeckItem.SideEnd) {
                deckItem.setType(DeckItemType.SideCard);
            }
        }
        synchronized (this) {
            addCount(deckItem.getCardInfo(), deckItem.getType());
            mItems.add(pos, deckItem);
        }
    }

    public int getRemoveIndex() {
        return mRemoveIndex;
    }

    public DeckItem getRemoveItem() {
        return mRemoveItem;
    }

    public DeckItem removeItem(int pos) {
        DeckItem deckItem = null;
        synchronized (this) {
            deckItem = mItems.remove(pos);
            removeCount(deckItem.getCardInfo(), deckItem.getType());
        }
        mRemoveIndex = pos;
        mRemoveItem = deckItem;
        return deckItem;
    }

    public DeckViewHolder getHeadHolder() {
        return mHeadHolder;
    }

    public int getItemHeight() {
        return mHeight;
    }

    public void showHeadView() {
        showHead = true;
//        notifyItemChanged(DeckItem.HeadView);
    }

    public void hideHeadView() {
        showHead = false;
//        notifyItemChanged(DeckItem.HeadView);
    }

    public void setDeckId(String id) {
        deckId = id;
    }

    public void setUserId(Integer id) {
        userId = id;
    }
}

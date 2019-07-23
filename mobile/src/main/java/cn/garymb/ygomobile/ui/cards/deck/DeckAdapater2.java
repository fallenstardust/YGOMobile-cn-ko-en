package cn.garymb.ygomobile.ui.cards.deck;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

public class DeckAdapater2 extends RecyclerView.Adapter<DeckViewHolder> {
    private final ImageLoader mImageLoader;
    private final DeckInfo mDeck;
    private LimitList mLimitList;
    private ImageTop mImageTop;
    private final LabelInfo mLabelInfo;
    private Context mContext;
    private LayoutInflater mLayoutInflater;

    public final static int MainLabel = 0;
    public final static int MainStart = MainLabel + 1;
    public final static int MainEnd = MainStart + Constants.DECK_MAIN_MAX - 1;

    public final static int ExtraLabel = MainEnd + 1;
    public final static int ExtraStart = ExtraLabel + 1;
    public final static int ExtraEnd = ExtraStart + Constants.DECK_EXTRA_MAX - 1;
    public final static int SideLabel = ExtraEnd + 1;
    public final static int SideStart = SideLabel + 1;
    public final static int SideEnd = SideStart + Constants.DECK_SIDE_MAX - 1;
    private int cardWidth, cardHeight;

    public DeckAdapater2(Context context, int width, int height) {
        mContext = context;
        cardWidth = width;
        cardHeight = height;
        mLayoutInflater = LayoutInflater.from(context);
        mImageLoader = ImageLoader.get(context);
        mDeck = new DeckInfo();
        mLabelInfo = new LabelInfo(context);
    }

    public Context getContext() {
        return mContext;
    }

    public void updateDeck(DeckInfo deck) {
        mDeck.update(deck);
        mLabelInfo.update(deck);
    }

    public void setLimitList(LimitList limitList) {
        mLimitList = limitList;
    }

    public ImageTop getImageTop() {
        if (mImageTop == null) {
            mImageTop = new ImageTop(getContext());
        }
        return mImageTop;
    }

    public boolean isLabel(int pos) {
        return pos == ExtraLabel || pos == MainLabel || pos == SideLabel;
    }

    @Override
    public DeckViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mLayoutInflater.inflate(R.layout.item_deck_card2, parent, false);
        return new DeckViewHolder(view);
    }

    @Override
    public int getItemCount() {
        return Constants.DECK_EXTRA_MAX + Constants.DECK_MAIN_MAX + Constants.DECK_SIDE_MAX + 3;
    }

    @Override
    public void onBindViewHolder(DeckViewHolder holder, int position) {
        if (position == ExtraLabel) {
            //61 62-76
            holder.setText(mLabelInfo.getExtraString());
        } else if (position == MainLabel) {
            //0 1-60
            holder.setText(mLabelInfo.getMainString());
        } else if (position == SideLabel) {
            //77 78-92
            holder.setText(mLabelInfo.getSideString());
        } else {
            holder.setSize(cardWidth, cardHeight);

            Card card = null;
            if (inMain(position)) {
                card = mDeck.getMainCard(getMainIndex(position));
            } else if (inExtra(position)) {
                card = mDeck.getExtraCard(getExtraIndex(position));
            } else if (inSide(position)) {
                card = mDeck.getSideCard(getSideIndex(position));
            }
            if (card == null) {
                holder.showEmpty();
            } else {
                holder.showImage();
                if (mLimitList != null) {
                    if (mLimitList.check(card, LimitType.Forbidden)) {
                        holder.setRightImage(getImageTop().forbidden);
                    } else if (mLimitList.check(card, LimitType.Limit)) {
                        holder.setRightImage(getImageTop().limit);
                    } else if (mLimitList.check(card, LimitType.SemiLimit)) {
                        holder.setRightImage(getImageTop().semiLimit);
                    } else {
                        holder.setRightImage(null);
                    }
                } else {
                    holder.setRightImage(null);
                }
                mImageLoader.bindImage(holder.cardImage, card.Code);
            }
        }
    }

    private int getMainIndex(int pos) {
        return pos - MainStart;
    }

    private int getExtraIndex(int pos) {
        return pos - ExtraStart;
    }

    private int getSideIndex(int pos) {
        return pos - SideStart;
    }

    private boolean inMain(int pos) {
        return pos >= MainStart && pos <= MainEnd;
    }

    private boolean inExtra(int pos) {
        return pos >= ExtraStart && pos <= ExtraEnd;
    }

    private boolean inSide(int pos) {
        return pos >= SideStart && pos <= SideEnd;
    }
}

class DeckData {

}

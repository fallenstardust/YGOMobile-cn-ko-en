package cn.garymb.ygomobile.ui.cards.deck2;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.ItemTouchHelperPlus;
import androidx.recyclerview.widget.OnItemDragListener;
import androidx.recyclerview.widget.RecyclerView;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.data.Card;

import static cn.garymb.ygomobile.bean.DeckInfo.Type.Extra;
import static cn.garymb.ygomobile.bean.DeckInfo.Type.Main;
import static cn.garymb.ygomobile.bean.DeckInfo.Type.Side;


public class DeckAdapter extends RecyclerView.Adapter<DeckViewHolder> implements IDeckLayout {
    private final Context mContext;
    private final RecyclerView mRecyclerView;
    private final LayoutInflater mLayoutInflater;
    private final DeckLayoutManager mDeckLayoutManager;
    private int mWidth;
    private int mHeight;
    private int mPWidth;
    private final DeckInfo mDeckInfo;
    private ImageLoader mImageLoader;

    public DeckAdapter(Context context, RecyclerView recyclerView, OnItemDragListener listener) {
        mDeckInfo = new DeckInfo();
        mImageLoader = ImageLoader.get(context);
        mContext = context;
        mRecyclerView = recyclerView;
        mLayoutInflater = LayoutInflater.from(context);
        recyclerView.addItemDecoration(new DeckItemDecoration(this));
        mDeckLayoutManager = new DeckLayoutManager(getContext(), getLineLimitCount(), this);
        recyclerView.setLayoutManager(mDeckLayoutManager);

        DeckHelperCallback deckHelperCallback = new DeckHelperCallback(this);
        ItemTouchHelperPlus touchHelper = new ItemTouchHelperPlus(getContext(), deckHelperCallback);
        touchHelper.setEnableClickDrag(true);
        touchHelper.attachToRecyclerView(recyclerView);
        touchHelper.setItemDragListener(listener);

        deckHelperCallback.setItemTouchHelper(touchHelper);
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    public DeckViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mLayoutInflater.inflate(R.layout.item_deck_card2, parent, false);
        return new DeckViewHolder(view);
    }

    @Override
    public int getLineLimitCount() {
        return 15;
    }

    //region width/height
    private void makeHeight() {
        mWidth = getWidth10();
        mHeight = scaleHeight(mWidth);
    }

    public void setDeckInfo(DeckInfo deckInfo) {
        mDeckInfo.update(deckInfo);
    }

    @Override
    public int getMaxWidth() {
        if (mPWidth == 0) {
            mPWidth = mRecyclerView.getMeasuredWidth()
                    - mRecyclerView.getPaddingRight()
                    - mRecyclerView.getPaddingLeft();
        }
        return mPWidth;
    }

    @Override
    public int getWidth15() {
        return getMaxWidth() / 15;
    }

    @Override
    public int getWidth10() {
        return getMaxWidth() / 10;
    }

    private int scaleHeight(int width) {
        return Math.round((float) width * ((float) 255 / 177));
    }

    //endregion

    //region count/limit
    @Override
    public int getMainCount() {
        return Math.max(31, getMainRealCount());
    }

    public int getMainRealCount() {
        return mDeckInfo.getMainCount();
    }

    @Override
    public int getExtraCount() {
        return Math.max(1, getExtraRealCount());
    }

    public int getExtraRealCount() {
        return mDeckInfo.getExtraCount();
    }

    @Override
    public int getSideCount() {
        return Math.max(1, getSideRealCount());
    }

    public int getSideRealCount() {
        return mDeckInfo.getSideCount();
    }

    @Override
    public int getMainLimit() {
        return Math.max(10, (int) Math.ceil(getMainCount() / 4.0f));
    }

    @Override
    public int getExtraLimit() {
        return Math.max(getLineCardCount(), getExtraCount());
    }

    @Override
    public int getSideLimit() {
        return Math.max(getLineCardCount(), getSideCount());
    }

    //endregion

    //region index

    @Override
    public boolean isMain(int pos) {
        return pos >= getMainStart() && pos <= getMainEnd();
    }

    @Override
    public boolean isExtra(int pos) {
        return pos >= getExtraStart() && pos <= getExtraEnd();
    }

    @Override
    public boolean isSide(int pos) {
        return pos >= getSideStart() && pos <= getSideEnd();
    }

    @Override
    public boolean isLabel(int position) {
        if (position == getMainLabel() || position == getExtraLabel() || position == getSideLabel()) {
            return true;
        }
        return false;
    }

    private int getMainLabel() {
        return 0;
    }

    private int getMainStart() {
        return getMainLabel() + 1;
    }

    private int getMainEnd() {
        return getMainStart() + getMainCount() - 1;
    }

    private int getExtraLabel() {
        return getMainEnd() + 1;
    }

    private int getExtraStart() {
        return getExtraLabel() + 1;
    }

    private int getExtraEnd() {
        return getExtraStart() + getExtraCount() - 1;
    }

    private int getSideLabel() {
        return getExtraEnd() + 1;
    }

    private int getSideStart() {
        return getSideLabel() + 1;
    }

    private int getSideEnd() {
        return getSideStart() + getSideCount() - 1;
    }

    @Override
    public int getMainIndex(int pos) {
        return pos - getMainStart();
    }

    @Override
    public int getExtraIndex(int pos) {
        return pos - getExtraStart();
    }

    @Override
    public int getSideIndex(int pos) {
        return pos - getSideStart();
    }

    //endregion

    @Override
    public int getItemCount() {
        return getMainCount() + getExtraCount() + getSideCount() + 3;
    }

    @Override
    public int getLineCardCount() {
        return 10;
    }

    @Override
    public void onBindViewHolder(DeckViewHolder holder, int position) {
        if (isLabel(position)) {
            if (position == getMainLabel()) {
                holder.setText("main");
            } else if (position == getExtraLabel()) {
                holder.setText("extra");
            } else if (position == getSideLabel()) {
                holder.setText("side");
            }
            holder.setSize(-1, -1);
        } else {
            holder.showImage();
            if (mHeight <= 0) {
                makeHeight();
            }
            holder.setSize(mWidth, mHeight);
            if (isMain(position)) {
                position = getMainIndex(position);
                if (position >= getMainRealCount()) {
                    holder.empty();
                } else {
                    Card card = mDeckInfo.getMainCard(position);
                    if (card == null) {
                        holder.useDefault();
                    } else {
                        mImageLoader.bindImage(holder.getCardImage(), card.Code);
                    }
                }
            } else if (isExtra(position)) {
                position = getExtraIndex(position);
                if (position >= getExtraRealCount()) {
                    holder.empty();
                } else {
                    Card card = mDeckInfo.getExtraCard(position);
                    if (card == null) {
                        holder.useDefault();
                    } else {
                        mImageLoader.bindImage(holder.getCardImage(), card.Code);
                    }
                }
            } else if (isSide(position)) {
                position = getSideIndex(position);
                if (position >= getSideRealCount()) {
                    holder.empty();
                } else {
                    Card card = mDeckInfo.getSideCard(position);
                    if (card == null) {
                        holder.useDefault();
                    } else {
                        mImageLoader.bindImage(holder.getCardImage(), card.Code);
                    }
                }
            }
        }
    }


    @Override
    public boolean moveItem(int from, int to) {
        if (isMain(from)) {
            if (isMain(to)) {
                int pos = getMainIndex(to);
                if (pos >= getMainRealCount()) {
                    to = getMainRealCount() - 1 + getMainStart();
                }
                mDeckInfo.move(Main, getMainIndex(from), getMainIndex(to));
                notifyItemMoved(from, to);
                return true;
            }
            if (isSide(to)) {
                //TODO check side
                if (getSideRealCount() >= Constants.DECK_SIDE_MAX) {
                    return false;
                }
                boolean resize = getMainRealCount() % 4 == 1;

                Card card = mDeckInfo.removeMain(getMainIndex(from));
                mDeckInfo.addSideCards(getSideIndex(to), card);

                Log.d("kk", "move main -> side " + getMainIndex(from) + "->" + getSideIndex(to));

                notifyItemMoved(from, to);
                notifyItemRemoved(getSideEnd());
                notifyItemInserted(getMainEnd());

                notifyItemChanged(getMainLabel());
                notifyItemChanged(getSideLabel());

                if (resize) {
                    notifyItemRangeChanged(getMainStart(), getMainStart() + getMainEnd());
                }
                return true;
            }
        } else if (isExtra(from)) {
            if (isExtra(to)) {
                mDeckInfo.move(Extra, getExtraIndex(from), getExtraIndex(to));
                notifyItemMoved(from, to);
                return true;
            }
            if (isSide(to)) {
                //TODO check side
                if (getSideRealCount() >= Constants.DECK_SIDE_MAX) {
                    return false;
                }
                Card card = mDeckInfo.removeExtra(getExtraIndex(from));
                mDeckInfo.addSideCards(getSideIndex(to), card);

                Log.d("kk", "move extra -> side " + getExtraIndex(from) + "->" + getSideIndex(to));

                notifyItemMoved(from, to);
                notifyItemRemoved(getSideEnd());
                notifyItemInserted(getExtraEnd());

                notifyItemChanged(getExtraLabel());
                notifyItemChanged(getSideLabel());
                return true;
            }
        } else if (isSide(from)) {
            if (isSide(to)) {
                mDeckInfo.move(Side, getSideIndex(from), getSideIndex(to));
                notifyItemMoved(from, to);
                return true;
            }
            if (isExtra(to)) {
                //TODO check extra
                if (getExtraRealCount() >= Constants.DECK_EXTRA_MAX) {
                    return false;
                }
                Card card = mDeckInfo.removeSide(getSideIndex(from));
                mDeckInfo.addExtraCards(getExtraIndex(to), card);

                Log.d("kk", "move side -> extra " + getSideIndex(from) + "->" + getExtraIndex(to));

                notifyItemMoved(from, to);
                notifyItemRemoved(getExtraEnd());
                notifyItemInserted(getSideEnd());

                notifyItemChanged(getExtraLabel());
                notifyItemChanged(getSideLabel());
                return true;
            }
            if (isMain(to)) {
                //TODO check main
                if (getMainRealCount() >= Constants.DECK_MAIN_MAX) {
                    return false;
                }
                int pos = getMainIndex(to);
                if (pos >= getMainRealCount()) {
                    to = getMainRealCount() - 1 + getMainStart();
                }
                boolean resize = getMainRealCount() % 4 == 0;
                Card card = mDeckInfo.removeSide(getSideIndex(from));
                mDeckInfo.addMainCards(getMainIndex(to), card);

                Log.d("kk", "move side -> main " + getSideIndex(from) + "->" + getMainIndex(to));

                notifyItemMoved(from, to);
                notifyItemRemoved(getMainEnd());
                notifyItemInserted(getSideEnd());

                notifyItemChanged(getMainLabel());
                notifyItemChanged(getSideLabel());

                if (resize) {
                    notifyItemRangeChanged(getMainStart(), getMainStart() + getMainEnd());
                }
                return true;
            }
        }
        return false;
    }
}
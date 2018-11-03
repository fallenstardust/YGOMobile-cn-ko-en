package cn.garymb.ygomobile.ui.cards.deck;

import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

class DeckViewHolder extends RecyclerView.ViewHolder {
    private long mCardType;
    private DeckItemType mItemType;

    public DeckViewHolder(View view) {
        super(view);
        this.view = view;
        view.setTag(view.getId(), this);
        cardImage = $(R.id.card_image);
        rightImage = $(R.id.right_top);
        labelText = $(R.id.label);
        textlayout = $(R.id.layout_label);
        headView = null;// $(R.id.head);
    }

    public DeckItemType getItemType() {
        return mItemType;
    }

    public void setItemType(DeckItemType itemType) {
        mItemType = itemType;
    }

    public long getCardType() {
        return mCardType;
    }

    public void setCardType(long cardType) {
        mCardType = cardType;
    }

    public void setSize(int height) {
        setSize(-1, height);
    }

    public void setSize(int width, int height) {
        if (width > 0) {
            cardImage.setMinimumWidth(width);
            cardImage.setMaxWidth(width);
            ViewGroup.LayoutParams layoutParams = cardImage.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.width = width;
            }
            cardImage.setLayoutParams(layoutParams);
        }
        if (height > 0) {
            cardImage.setMinimumHeight(height);
            cardImage.setMaxHeight(height);
            rightImage.setMaxWidth(height / 5);
            rightImage.setMaxHeight(height / 5);
            ViewGroup.LayoutParams layoutParams = cardImage.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = height;
            }
            cardImage.setLayoutParams(layoutParams);
        }
    }

    public void useDefault(ImageLoader imageLoader, int w, int h) {
        cardImage.setImageResource(R.drawable.unknown);
        //TODO sdcard的卡背
//        File outFile = new File(AppsSettings.get().getCoreSkinPath(), Constants.UNKNOWN_IMAGE);
//        ViewGroup.LayoutParams layoutParams = cardImage.getLayoutParams();
//        if (layoutParams != null) {
//            layoutParams.height = h;
//        }
//        imageLoader.$(outFile, cardImage, outFile.getName().endsWith(Constants.BPG), 0, null);
    }

    public void setText(String text) {
        labelText.setText(text);
        textlayout.setVisibility(View.VISIBLE);
        cardImage.setVisibility(View.GONE);
        rightImage.setVisibility(View.GONE);
    }

    public void showImage() {
        textlayout.setVisibility(View.GONE);
        cardImage.setVisibility(View.VISIBLE);
        rightImage.setVisibility(View.VISIBLE);
    }

    public void showEmpty() {
        textlayout.setVisibility(View.GONE);
        cardImage.setVisibility(View.INVISIBLE);
        rightImage.setVisibility(View.GONE);
    }

    public void setRightImage(Bitmap bitmap) {
        rightImage.setImageBitmap(bitmap);
    }

    protected <T extends View> T $(int id) {
        return (T) view.findViewById(id);
    }

    public void setHeadVisibility(int visibility) {
        if (headView != null)
            headView.setVisibility(visibility);
    }

    private final View view;
    private final View headView;
    private final View textlayout;
    private final TextView labelText;
    public final ImageView cardImage;
    private final ImageView rightImage;
}

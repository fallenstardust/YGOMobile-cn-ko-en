package cn.garymb.ygomobile.ui.cards.deck2;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManagerPlus;

import cn.garymb.ygomobile.lite.R;
import ocgcore.data.Card;


class DeckViewHolder extends GridLayoutManagerPlus.GridViewHolder {
    DeckViewHolder(View view) {
        super(view);
        this.view = view;
        view.setTag(view.getId(), this);
        cardImage = $(R.id.card_image);
        labelText = $(R.id.label);
        textLayout = $(R.id.layout_label);
        rightImage = $(R.id.right_top);
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T $(int id) {
        return (T) view.findViewById(id);
    }

    void setSize(int width, int height) {
        view.setMinimumWidth(width);
        view.setMinimumHeight(height);
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null) {
            if (width > 0) {
                layoutParams.width = width;
            } else {
                layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            if (height > 0) {
                layoutParams.height = height;
            } else {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        }
        view.setLayoutParams(layoutParams);
    }

    public void empty() {
        showEmpty();
    }

    public void useDefault() {
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
        textLayout.setVisibility(View.VISIBLE);
        cardImage.setVisibility(View.GONE);
        rightImage.setVisibility(View.GONE);
    }

    public void showImage() {
        textLayout.setVisibility(View.GONE);
        cardImage.setVisibility(View.VISIBLE);
        rightImage.setVisibility(View.VISIBLE);
        //
        cardImage.setImageResource(R.drawable.unknown);
    }

    public void showEmpty() {
        textLayout.setVisibility(View.GONE);
        cardImage.setVisibility(View.INVISIBLE);
        rightImage.setVisibility(View.GONE);
    }

    public ImageView getCardImage() {
        return cardImage;
    }

    /**
     * 左上角图标
     * @param bitmap
     */
    public void setRightImage(Bitmap bitmap) {
        rightImage.setImageBitmap(bitmap);
    }

    public void setHeadVisibility(int visibility) {
    }

    private final View view;
    private final View textLayout;
    private final TextView labelText;
    private final ImageView cardImage;
    private final ImageView rightImage;
    private Card mCard;
    private Type mType;

    public Card getCard() {
        return mCard;
    }

    public void setCard(Card card) {
        mCard = card;
    }

    public Type getType() {
        return mType;
    }

    public void setType(Type type) {
        mType = type;
    }

    public enum Type {
        None,
        Main,
        Extra,
        Side
    }
}

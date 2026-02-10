package cn.garymb.ygomobile.ui.cards.deck;


import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

// Provide a direct reference to each of the views within a data item
// Used to cache the views within the item layout for fast access
class DeckViewHolder extends RecyclerView.ViewHolder {
    // Your holder should contain a member variable
    // for any view that will be set as you render a row
    private final View view;
    private final View headView;
    private final View textlayout;
    private final TextView labelText;
    private final TextView tv_deck_limit_num;
    public final ImageView cardImage;
    private final ImageView rightImage;

    private long mCardType;
    private DeckItemType mItemType;

    // Create a constructor that accepts the entire item row
    // and does the view lookups to find each subview
    public DeckViewHolder(View view) {
        // Stores the view in a public final member variable that can be used
        // to access the context from any ViewHolder instance.
        super(view);
        this.view = view;
        view.setTag(view.getId(), this);
        cardImage = $(R.id.card_image);
        rightImage = $(R.id.right_top);
        labelText = $(R.id.label);
        textlayout = $(R.id.layout_label);
        tv_deck_limit_num = $(R.id.tv_deck_limit_num);
        headView = null;// $(R.id.head);
    }

    public void clear() {
        if (cardImage != null) {
            // 清除图片加载任务
            GlideCompat.with(itemView.getContext()).clear(cardImage);
            // 设置默认占位图
            cardImage.setImageResource(R.drawable.unknown);
        }
        // 清理禁限数字显示
        if (tv_deck_limit_num != null) {
            tv_deck_limit_num.setText("");
            tv_deck_limit_num.setVisibility(View.GONE);
        }
        // 确保视图处于正确状态
        showEmpty();
    }

    public void bind(DeckItem item, ImageLoader imageLoader) {
        if (item != null && item.getCardInfo() != null) {
            showImage(); // 显示卡片图片
            // 使用 setResourceImage 方法加载卡片图片
            imageLoader.bindImage(cardImage, item.getCardInfo(), ImageLoader.Type.small);
        } else {
            showEmpty(); // 显示空状态
        }
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
//        imageLoader.$(outFile, cardImage, outFile.getName().endsWith(Constants.JPG), 0, null);
    }

    /**
     * 只展示分隔标签（例如“主卡组：60怪兽：21“），隐藏掉卡图ImageView
     *
     * @param text
     */
    public void setText(String text) {
        labelText.setText(text);
        textlayout.setVisibility(View.VISIBLE);
        cardImage.setVisibility(View.GONE);
        rightImage.setVisibility(View.GONE);
    }

    public void setLimitText(String text, int color, int textSize) {
        tv_deck_limit_num.setText(text);
        tv_deck_limit_num.setTextColor(color);
        tv_deck_limit_num.setTextSize(textSize);
    }

    /**
     * 展示卡图，隐藏分隔标签
     */
    public void showImage() {
        textlayout.setVisibility(View.GONE);
        cardImage.setVisibility(View.VISIBLE);
        rightImage.setVisibility(View.VISIBLE);
        // 禁限数字在需要时再单独设置
        if (tv_deck_limit_num != null) {
            tv_deck_limit_num.setVisibility(View.VISIBLE);
        }
    }

    public void showEmpty() {
        textlayout.setVisibility(View.GONE);
        cardImage.setVisibility(View.INVISIBLE);
        rightImage.setVisibility(View.GONE);
        // 同时隐藏禁限数字
        if (tv_deck_limit_num != null) {
            tv_deck_limit_num.setVisibility(View.GONE);
        }
    }

    public void setRightImage(Bitmap bitmap) {
        rightImage.setImageBitmap(bitmap);
    }

    protected <T extends View> T $(int id) {
        return view.findViewById(id);
    }

    public void setHeadVisibility(int visibility) {
        if (headView != null)
            headView.setVisibility(visibility);
    }
}

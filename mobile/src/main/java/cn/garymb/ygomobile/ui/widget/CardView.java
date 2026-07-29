package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

public class CardView extends FrameLayout {
    private final ImageView mCardView, mTopImage;
    private final TextView mLimitNum;
    private Card mCard;
    private int mOverlaySize;
    private float mLimitNumScale = 0.6f;

    public CardView(Context context) {
        this(context, null);
    }

    public CardView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardView(Context context, int width) {
        super(context);
        mCardView = new ImageView(context);
        mTopImage = new ImageView(context);
        mLimitNum = new TextView(context);
        initCountView(Math.round(width / 9.0f * 4.0f));
    }

    public CardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mCardView = new ImageView(context);
        mTopImage = new ImageView(context);
        mLimitNum = new TextView(context);
        initCountView((int) getResources().getDimension(R.dimen.right_size2));
    }

    private void initCountView(int w) {
        mCardView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        int p = (int) getResources().getDimension(R.dimen.card_padding);
        lp.setMargins(p, p, p, p);
        addView(mCardView, lp);

        LayoutParams lp2 = new LayoutParams(w, w);
        lp2.gravity = Gravity.LEFT | Gravity.TOP;
        mTopImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(mTopImage, lp2);

        LayoutParams lp3 = new LayoutParams(w, w);
        lp3.gravity = Gravity.LEFT | Gravity.TOP;
        mLimitNum.setGravity(Gravity.CENTER);
        mLimitNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        mLimitNum.setTextColor(Color.WHITE);
        mLimitNum.setVisibility(View.GONE);
        addView(mLimitNum, lp3);
    }

    /**
     * 角标尺寸随卡片实际尺寸自适应：
     * 高度为卡图（去除上下padding后）高度的1/4，宽高一致，字号等比缩放。
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int p = (int) getResources().getDimension(R.dimen.card_padding);
        int imageHeight = h - 2 * p;
        if (imageHeight <= 0) return;
        int size = Math.max(1, imageHeight / 4);
        if (size == mOverlaySize) return;
        mOverlaySize = size;
        //onSizeChanged处于布局流程中，直接改LayoutParams可能不生效，布局完成后再应用
        post(this::applyOverlaySize);
    }

    private void applyOverlaySize() {
        LayoutParams lp2 = (LayoutParams) mTopImage.getLayoutParams();
        lp2.width = mOverlaySize;
        lp2.height = mOverlaySize;
        mTopImage.setLayoutParams(lp2);

        LayoutParams lp3 = (LayoutParams) mLimitNum.getLayoutParams();
        lp3.width = mOverlaySize;
        lp3.height = mOverlaySize;
        mLimitNum.setLayoutParams(lp3);

        setLimitNumTextSize(mLimitNumScale);
    }

    private void setLimitNumTextSize(float scale) {
        mLimitNumScale = scale;
        if (mOverlaySize > 0) {
            mLimitNum.setTextSize(TypedValue.COMPLEX_UNIT_PX, mOverlaySize * scale);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (selected) {
            setBackgroundResource(R.drawable.selected);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                setBackground(null);
            } else {
                setBackgroundDrawable(null);
            }
        }
    }

    public void updateLimit(ImageTop imageTop, LimitList limitList) {
        if (mCard != null && imageTop != null) {
            mTopImage.setVisibility(View.VISIBLE);
            mLimitNum.setVisibility(View.VISIBLE);
            if (limitList != null) {
                if (limitList.check(mCard, LimitType.Forbidden)) {
                    mTopImage.setImageBitmap(imageTop.forbidden);
                    mLimitNum.setText("");
                    mLimitNum.setTextColor(YGOUtil.c(R.color.white));
                    setLimitNumTextSize(0.6f);
                } else if (limitList.check(mCard, LimitType.Limit)) {
                    mTopImage.setImageBitmap(imageTop.limit);
                    mLimitNum.setText("1");
                    mLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
                    setLimitNumTextSize(0.6f);
                } else if (limitList.check(mCard, LimitType.SemiLimit)) {
                    mTopImage.setImageBitmap(imageTop.semiLimit);
                    mLimitNum.setText("2");
                    mLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
                    setLimitNumTextSize(0.6f);
                } else if (limitList.check(mCard, LimitType.GeneSys)) {
                    Integer creditValue = 0;
                    if (limitList.getCredits() != null) {
                        creditValue = limitList.getCredits().get(mCard.getCode());
                    }
                    if (creditValue != null && creditValue > 0) {
                        mTopImage.setImageBitmap(imageTop.credits);
                        mLimitNum.setText(creditValue.toString());
                        mLimitNum.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
                        setLimitNumTextSize((creditValue > -10 && creditValue < 100) ? 0.6f : 0.45f);
                    } else {
                        mTopImage.setVisibility(View.GONE);
                        mLimitNum.setVisibility(View.GONE);
                    }
                } else {
                    mTopImage.setVisibility(View.GONE);
                    mLimitNum.setVisibility(View.GONE);
                }
            } else {
                mTopImage.setVisibility(View.GONE);
                mLimitNum.setVisibility(View.GONE);
            }
        } else {
            mTopImage.setVisibility(View.GONE);
            mLimitNum.setVisibility(View.GONE);
        }
    }


    public void showCard(ImageLoader imageLoader, Card cardInfo) {
        if (mCard != null && mCard.equals(cardInfo)) return;
        mCard = cardInfo;
        if (cardInfo != null && imageLoader != null) {
            imageLoader.bindImage(mCardView, cardInfo, ImageLoader.Type.small);
        } else {
            mTopImage.setVisibility(View.GONE);
            mLimitNum.setVisibility(View.GONE);
            mCardView.setImageBitmap(null);
        }
    }

    public Card getCard() {
        return mCard;
    }
}

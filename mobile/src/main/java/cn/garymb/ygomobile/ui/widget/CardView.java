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
                } else if (limitList.check(mCard, LimitType.Limit)) {
                    mTopImage.setImageBitmap(imageTop.limit);
                    mLimitNum.setText("1");
                    mLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
                    mLimitNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
                } else if (limitList.check(mCard, LimitType.SemiLimit)) {
                    mTopImage.setImageBitmap(imageTop.semiLimit);
                    mLimitNum.setText("2");
                    mLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
                    mLimitNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
                } else if (limitList.check(mCard, LimitType.GeneSys)) {
                    Integer creditValue = 0;
                    if (limitList.getCredits() != null) {
                        creditValue = limitList.getCredits().get(mCard.getCode());
                    }
                    if (creditValue != null && creditValue > 0) {
                        mTopImage.setImageBitmap(imageTop.credits);
                        mLimitNum.setText(creditValue.toString());
                        mLimitNum.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
                        mLimitNum.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                (creditValue > -10 && creditValue < 100) ? 8 : 6);
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

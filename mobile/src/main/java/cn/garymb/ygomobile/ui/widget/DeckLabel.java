package cn.garymb.ygomobile.ui.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.Nullable;

import cn.garymb.ygomobile.lite.R;

@SuppressLint("AppCompatCustomView")
public class DeckLabel extends TextView {
    public DeckLabel(Context context) {
        this(context, null);
    }

    public DeckLabel(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeckLabel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundResource(R.drawable.veil);
        setGravity(Gravity.CENTER_VERTICAL);
        int labelLeft = (int) getResources().getDimension(R.dimen.deck_label_left);
        setPadding(labelLeft, 0, 0, 0);
//        setTextSize(getResources().getDimension(R.dimen.deck_label_text));
        setMinLines(1);
        setSingleLine();
    }
}

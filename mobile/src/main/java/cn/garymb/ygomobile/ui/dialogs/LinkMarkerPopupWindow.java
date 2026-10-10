package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;

import cn.garymb.ygomobile.lite.R;

public class LinkMarkerPopupWindow extends PopupWindow {

    public interface OnLinkMarkerChangeListener {
        void onLinkMarkerChanged(int filterMarks);
    }

    private final String[] btnVals = new String[]{"0", "0", "0", "0", "0", "0", "0", "0", "0"};
    private int filterMarks;
    private OnLinkMarkerChangeListener listener;

    private final int[] enImgs = {
            R.drawable.left_bottom_1,
            R.drawable.bottom_1,
            R.drawable.right_bottom_1,
            R.drawable.left_1,
            0,
            R.drawable.right_1,
            R.drawable.left_top_1,
            R.drawable.top_1,
            R.drawable.right_top_1,
    };

    private final int[] disImgs = {
            R.drawable.left_bottom_0,
            R.drawable.bottom_0,
            R.drawable.right_bottom_0,
            R.drawable.left_0,
            0,
            R.drawable.right_0,
            R.drawable.left_top_0,
            R.drawable.top_0,
            R.drawable.right_top_0,
    };

    public LinkMarkerPopupWindow(Context context, int currentFilterMarks,
                                 OnLinkMarkerChangeListener listener) {
        super();
        this.filterMarks = currentFilterMarks;
        this.listener = listener;

        setBackgroundDrawable(context.getDrawable(R.drawable.button_bg));
        setOutsideTouchable(true);
        setFocusable(true);

        View popupView = LayoutInflater.from(context).inflate(R.layout.item_searcher_linkmarker, null);

        Button[] buttons = new Button[]{
                popupView.findViewById(R.id.button_1),
                popupView.findViewById(R.id.button_2),
                popupView.findViewById(R.id.button_3),
                popupView.findViewById(R.id.button_4),
                popupView.findViewById(R.id.button_5),
                popupView.findViewById(R.id.button_6),
                popupView.findViewById(R.id.button_7),
                popupView.findViewById(R.id.button_8),
                popupView.findViewById(R.id.button_9)
        };

        for (int i = 0; i < 9; i++) {
            if (((currentFilterMarks >> i) & 1) == 1) {
                btnVals[i] = "1";
                if (i != 4 && enImgs[i] != 0) {
                    buttons[i].setBackgroundResource(enImgs[i]);
                }
            }
        }

        buttons[4].setVisibility(View.VISIBLE);
        buttons[4].setText("确定");
        buttons[4].setTextSize(8f);
        buttons[4].setTextColor(Color.WHITE);
        buttons[4].setBackground(context.getDrawable(R.drawable.button3_bg));
        buttons[4].setOnClickListener(v -> dismiss());

        for (int i = 0; i < buttons.length; i++) {
            if (i == 4) continue;
            final int index = i;
            Button button = buttons[index];
            if (button == null) continue;
            button.setOnClickListener(btn -> {
                if ("0".equals(btnVals[index])) {
                    btn.setBackgroundResource(enImgs[index]);
                    btnVals[index] = "1";
                } else {
                    btn.setBackgroundResource(disImgs[index]);
                    btnVals[index] = "0";
                }
                String mLinkStr = btnVals[8] + btnVals[7] + btnVals[6]
                        + btnVals[5] + "0"
                        + btnVals[3] + btnVals[2] + btnVals[1]
                        + btnVals[0];
                filterMarks = Integer.parseInt(mLinkStr, 2);
            });
        }

        setContentView(popupView);
        setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public int getFilterMarks() {
        return filterMarks;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (listener != null) {
            listener.onLinkMarkerChanged(filterMarks);
        }
    }

    public void show(View anchor) {
        showAsDropDown(anchor);
    }
}
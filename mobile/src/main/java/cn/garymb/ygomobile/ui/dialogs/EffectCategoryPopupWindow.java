package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.PopupWindow;

import cn.garymb.ygomobile.lite.R;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.enums.CardCategory;

public class EffectCategoryPopupWindow extends PopupWindow {

    public interface OnEffectCategoryChangeListener {
        void onEffectCategoryChanged(long filterEffect);
    }

    private long selectedEffect;
    private OnEffectCategoryChangeListener listener;

    public EffectCategoryPopupWindow(Context context, long currentFilterEffect,
                                     OnEffectCategoryChangeListener listener) {
        super();
        this.selectedEffect = currentFilterEffect;
        this.listener = listener;

        setOutsideTouchable(true);
        setFocusable(true);

        View contentView = LayoutInflater.from(context).inflate(R.layout.popup_effect_category, null);
        Button btnOk = contentView.findViewById(R.id.btn_effect_category_ok);

        StringManager sm = DataManager.get().getStringManager();
        CardCategory[] categories = CardCategory.values();

        for (int i = 0; i < categories.length; i++) {
            final int idx = i;
            int resId = context.getResources().getIdentifier("cb_cat_" + i, "id", context.getPackageName());
            CheckBox cb = contentView.findViewById(resId);
            if (cb == null) continue;

            cb.setText(sm.getSystemString(categories[i].getLanguageIndex(), categories[i].name()));
            cb.setChecked((selectedEffect & categories[i].value()) != 0);
            cb.setButtonTintList(ColorStateList.valueOf(Color.WHITE));

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                long bitVal = categories[idx].value();
                if (isChecked) {
                    selectedEffect |= bitVal;
                } else {
                    selectedEffect &= ~bitVal;
                }
            });
        }

        btnOk.setOnClickListener(v -> dismiss());

        setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        setContentView(contentView);
    }

    public long getSelectedEffect() {
        return selectedEffect;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (listener != null) {
            listener.onEffectCategoryChanged(selectedEffect);
        }
    }

    public void show(View anchor) {
        showAsDropDown(anchor);
    }
}
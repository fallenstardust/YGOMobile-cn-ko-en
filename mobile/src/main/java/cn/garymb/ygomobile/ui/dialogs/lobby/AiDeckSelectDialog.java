package cn.garymb.ygomobile.ui.dialogs.lobby;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.utils.AiDeckUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;

/** 大厅 “/ai” 人机卡组选择弹窗：首项为“随机-不指定”，其余显示 bot.conf 人机名；确定后回调卡组名（空串表示随机）。 */
class AiDeckSelectDialog {

    interface OnDeckSelectedListener {
        void onDeckSelected(String deckName);
    }

    private static final int DIALOG_WIDTH_DP = 240;
    private static final int DIALOG_HEIGHT_DP = 300;

    private final Context context;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    private SimpleListAdapter adapter;
    private final List<AiDeckUtil.AiDeckOption> options = new ArrayList<>();
    private OnDeckSelectedListener listener;
    private int selectedPosition = -1;

    AiDeckSelectDialog(Context context) {
        this.context = context;
    }

    void setOnDeckSelectedListener(OnDeckSelectedListener listener) {
        this.listener = listener;
    }

    void show(View anchorView, List<AiDeckUtil.AiDeckOption> deckOptions, String banlistName) {
        dismiss();
        options.clear();
        if (deckOptions != null) options.addAll(deckOptions);
        selectedPosition = -1;

        View contentView = LayoutInflater.from(context).inflate(R.layout.popup_window_ai_deck_select, null);
        TextView tvTitle = contentView.findViewById(R.id.tv_ai_deck_title);
        // 标题按房间禁卡表动态显示：符合<卡表名>的人机列表（N/A 表示无禁限）
        tvTitle.setText(context.getString(R.string.ai_deck_select_title,
                banlistName == null ? "N/A" : banlistName));
        ListView listView = contentView.findViewById(R.id.lv_ai_deck_list);
        Button btnOk = contentView.findViewById(R.id.btn_ai_deck_ok);

        List<String> displayNames = new ArrayList<>(options.size());
        for (AiDeckUtil.AiDeckOption option : options) displayNames.add(option.displayName);
        adapter = new SimpleListAdapter(context);
        adapter.set(displayNames);
        adapter.setSelectedPosition(-1);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedPosition = position;
            adapter.setSelectedPosition(position);
            btnOk.setEnabled(true);
        });
        btnOk.setEnabled(false);
        btnOk.setOnClickListener(v -> {
            if (selectedPosition < 0 || selectedPosition >= options.size()) return;
            String deckName = options.get(selectedPosition).deckName;
            dismiss();
            if (listener != null) listener.onDeckSelected(deckName);
        });
        contentView.findViewById(R.id.btn_ai_deck_cancel).setOnClickListener(v -> dismiss());

        float density = context.getResources().getDisplayMetrics().density;
        int width = (int) (DIALOG_WIDTH_DP * density);
        int height = (int) (DIALOG_HEIGHT_DP * density);
        popupWindow = new PopupWindow(contentView, width, height, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "ai_deck_select_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, contentView, width, height);
        draggableHelper.showPopup(popupWindow, anchorView, Gravity.CENTER, 0, 0);
    }

    void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        popupWindow = null;
    }
}

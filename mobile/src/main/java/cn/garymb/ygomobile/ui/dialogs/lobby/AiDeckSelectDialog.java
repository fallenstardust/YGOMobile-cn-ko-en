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
import java.util.Set;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.utils.AiDeckUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;

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
    private Button btnOk;
    /** 传入的原始顺序（已按禁卡表过滤），重排始终以此为基准，取消收藏即回原位 */
    private final List<AiDeckUtil.AiDeckOption> baseOptions = new ArrayList<>();
    /** 当前显示顺序：收藏项置顶，收藏项与其余项各自保持传入的原始顺序 */
    private final List<AiDeckUtil.AiDeckOption> options = new ArrayList<>();
    private OnDeckSelectedListener listener;
    /** 当前选中条目：重排后按对象找回新下标，避免选中位置漂移 */
    private AiDeckUtil.AiDeckOption selectedOption;

    AiDeckSelectDialog(Context context) {
        this.context = context;
    }

    void setOnDeckSelectedListener(OnDeckSelectedListener listener) {
        this.listener = listener;
    }

    void show(View anchorView, List<AiDeckUtil.AiDeckOption> deckOptions, String banlistName) {
        dismiss();
        baseOptions.clear();
        if (deckOptions != null) baseOptions.addAll(deckOptions);
        selectedOption = null;

        View contentView = LayoutInflater.from(context).inflate(R.layout.popup_window_ai_deck_select, null);
        TextView tvTitle = contentView.findViewById(R.id.tv_ai_deck_title);
        // 标题按房间禁卡表动态显示：符合<卡表名>的人机列表（N/A 表示无禁限）
        tvTitle.setText(context.getString(R.string.ai_deck_select_title,
                banlistName == null ? "N/A" : banlistName));
        ListView listView = contentView.findViewById(R.id.lv_ai_deck_list);
        btnOk = contentView.findViewById(R.id.btn_ai_deck_ok);

        // 条目左侧收藏星标：与“人机模式”列表共用同一份收藏记录，收藏项置顶；
        // 须在首次布局前启用，避免列表复用池里混用两种条目布局
        adapter = new SimpleListAdapter(context);
        adapter.setFavoriteNames(SharedPreferenceUtil.getFavoriteBotNames());
        adapter.setOnFavoriteClickListener((name, position) -> toggleFavorite(name));
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= options.size()) return;
            selectedOption = options.get(position);
            adapter.setSelectedPosition(position);
            btnOk.setEnabled(true);
        });
        btnOk.setEnabled(false);
        btnOk.setOnClickListener(v -> {
            if (selectedOption == null) return;
            String deckName = selectedOption.deckName;
            dismiss();
            if (listener != null) listener.onDeckSelected(deckName);
        });
        contentView.findViewById(R.id.btn_ai_deck_cancel).setOnClickListener(v -> dismiss());
        refreshList();

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

    /**
     * 收藏项置顶重建显示顺序：先收藏项、后其余，两组各自保持传入的原始顺序；
     * 取消收藏后条目自然回到它在原列表中的位置。
     */
    private void rebuildDisplayOptions() {
        Set<String> favorites = SharedPreferenceUtil.getFavoriteBotNames();
        options.clear();
        for (AiDeckUtil.AiDeckOption option : baseOptions) {
            if (favorites.contains(option.displayName)) options.add(option);
        }
        for (AiDeckUtil.AiDeckOption option : baseOptions) {
            if (!favorites.contains(option.displayName)) options.add(option);
        }
    }

    /** 按当前显示顺序刷新条目与星标态；选中项跟随条目对象，重排后仍保持选中 */
    private void refreshList() {
        rebuildDisplayOptions();
        List<String> displayNames = new ArrayList<>(options.size());
        for (AiDeckUtil.AiDeckOption option : options) displayNames.add(option.displayName);
        int selected = selectedOption == null ? -1 : options.indexOf(selectedOption);
        adapter.setDisplayState(displayNames, null, selected, null);
        if (btnOk != null) btnOk.setEnabled(selected >= 0);
    }

    /** 点星切换收藏（落 SharedPreference），重排列表并保持当前选中项 */
    private void toggleFavorite(String name) {
        SharedPreferenceUtil.setBotFavorite(name, !SharedPreferenceUtil.getFavoriteBotNames().contains(name));
        adapter.setFavoriteNames(SharedPreferenceUtil.getFavoriteBotNames());
        refreshList();
    }
}

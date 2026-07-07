package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;

public class LanModeDialog {

    private Context context;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

    public interface OnLanModeListener {
        void onCreateHostConfirmed(String banlist, String rule, String cardAllowed,
                                   String startLP, String duelMode, String startHand,
                                   String timeLimit, String drawCount,
                                   boolean noCheckDeck, boolean noShuffleDeck,
                                   String hostName, String password);
        void onExitLan();
    }

    private OnLanModeListener listener;

    public LanModeDialog(Context context, OnLanModeListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(listener);
        }
    }

    public void show(View anchorView) {
        View customView = LayoutInflater.from(context).inflate(R.layout.dialog_lan_connection, null);

        View layoutLanMain = customView.findViewById(R.id.layout_lan_main);
        View layoutCreateHost = customView.findViewById(R.id.layout_create_host_settings);
        View layoutPlayerWaiting = customView.findViewById(R.id.layout_player_waiting);

        EditText etNickname = layoutLanMain.findViewById(R.id.et_nickname);
        EditText etHostIp = layoutLanMain.findViewById(R.id.et_host_ip);
        EditText etHostPort = layoutLanMain.findViewById(R.id.et_host_port);
        EditText etRoomPassword = layoutLanMain.findViewById(R.id.et_room_password);
        Button btnCreateHost = layoutLanMain.findViewById(R.id.btn_create_host);
        Button btnRefreshLan = layoutLanMain.findViewById(R.id.btn_refresh_lan);
        Button btnJoinGame = layoutLanMain.findViewById(R.id.btn_join_game);
        Button btnExitLan = layoutLanMain.findViewById(R.id.btn_exit_lan);
        ListView lvHostList = layoutLanMain.findViewById(R.id.lv_host_list);

        SimpleListAdapter hostAdapter = new SimpleListAdapter(context);
        lvHostList.setAdapter(hostAdapter);

        lvHostList.setOnItemClickListener((parent, view, position, id) -> {
            hostAdapter.setSelectedPosition(position);
            String selectedHost = hostAdapter.getDataItem(position);
            if (selectedHost != null) {
                String[] parts = selectedHost.split(":");
                if (parts.length >= 2) {
                    etHostIp.setText(parts[0].trim());
                    etHostPort.setText(parts[1].trim());
                }
            }
        });

        Spinner spinnerBanlist = layoutCreateHost.findViewById(R.id.spinner_banlist);
        Spinner spinnerRule = layoutCreateHost.findViewById(R.id.spinner_rule);
        Spinner spinnerCardAllowed = layoutCreateHost.findViewById(R.id.spinner_card_allowed);
        EditText etStartLP = layoutCreateHost.findViewById(R.id.et_start_lp);
        Spinner spinnerDuelMode = layoutCreateHost.findViewById(R.id.spinner_duel_mode);
        EditText etStartHand = layoutCreateHost.findViewById(R.id.et_start_hand);
        EditText etTimeLimit = layoutCreateHost.findViewById(R.id.et_time_limit);
        EditText etDrawCount = layoutCreateHost.findViewById(R.id.et_draw_count);
        CheckBox chkNoCheckDeck = layoutCreateHost.findViewById(R.id.chk_no_check_deck);
        CheckBox chkNoShuffleDeck = layoutCreateHost.findViewById(R.id.chk_no_shuffle_deck);
        EditText etHostName = layoutCreateHost.findViewById(R.id.et_host_name);
        EditText etHostPassword = layoutCreateHost.findViewById(R.id.et_host_password);
        Button btnConfirmCreate = layoutCreateHost.findViewById(R.id.btn_confirm_create);
        Button btnCancelCreate = layoutCreateHost.findViewById(R.id.btn_cancel_create);

        EditText etPlayer1Name = layoutPlayerWaiting.findViewById(R.id.et_player1_name);
        EditText etPlayer2Name = layoutPlayerWaiting.findViewById(R.id.et_player2_name);
        CheckBox chkPlayer1Ready = layoutPlayerWaiting.findViewById(R.id.chk_player1_ready);
        CheckBox chkPlayer2Ready = layoutPlayerWaiting.findViewById(R.id.chk_player2_ready);
        Button btnDuelistMode = layoutPlayerWaiting.findViewById(R.id.btn_duelist_mode);
        Button btnSpectatorMode = layoutPlayerWaiting.findViewById(R.id.btn_spectator_mode);
        Button btnReady = layoutPlayerWaiting.findViewById(R.id.btn_ready);
        Spinner spinnerDeckSelect = layoutPlayerWaiting.findViewById(R.id.spinner_deck_select);
        TextView tvBanlist = layoutPlayerWaiting.findViewById(R.id.tv_banlist);
        TextView tvCardAllowed = layoutPlayerWaiting.findViewById(R.id.tv_card_allowed);
        TextView tvDuelMode = layoutPlayerWaiting.findViewById(R.id.tv_duel_mode);
        TextView tvStartLP = layoutPlayerWaiting.findViewById(R.id.tv_start_lp);
        TextView tvStartHand = layoutPlayerWaiting.findViewById(R.id.tv_start_hand);
        TextView tvDrawCount = layoutPlayerWaiting.findViewById(R.id.tv_draw_count);
        Button btnExitWaiting = layoutPlayerWaiting.findViewById(R.id.btn_exit_waiting);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "lan_mode_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, customView);

        btnCreateHost.setOnClickListener(v -> {
            layoutLanMain.setVisibility(View.GONE);
            layoutCreateHost.setVisibility(View.VISIBLE);
        });

        btnCancelCreate.setOnClickListener(v -> {
            layoutCreateHost.setVisibility(View.GONE);
            layoutLanMain.setVisibility(View.VISIBLE);
        });

        btnConfirmCreate.setOnClickListener(v -> {
            String banlist = spinnerBanlist.getSelectedItem() != null ? spinnerBanlist.getSelectedItem().toString() : "";
            String rule = spinnerRule.getSelectedItem() != null ? spinnerRule.getSelectedItem().toString() : "";
            String cardAllowed = spinnerCardAllowed.getSelectedItem() != null ? spinnerCardAllowed.getSelectedItem().toString() : "";
            String startLP = etStartLP.getText().toString();
            String duelMode = spinnerDuelMode.getSelectedItem() != null ? spinnerDuelMode.getSelectedItem().toString() : "";
            String startHand = etStartHand.getText().toString();
            String timeLimit = etTimeLimit.getText().toString();
            String drawCount = etDrawCount.getText().toString();
            boolean noCheckDeck = chkNoCheckDeck.isChecked();
            boolean noShuffleDeck = chkNoShuffleDeck.isChecked();
            String hostName = etHostName.getText().toString();
            String password = etHostPassword.getText().toString();

            if (listener != null) {
                listener.onCreateHostConfirmed(banlist, rule, cardAllowed, startLP,
                        duelMode, startHand, timeLimit, drawCount,
                        noCheckDeck, noShuffleDeck, hostName, password);
            }

            layoutCreateHost.setVisibility(View.GONE);
            layoutPlayerWaiting.setVisibility(View.VISIBLE);

            tvBanlist.setText(banlist.isEmpty() ? "N/A" : banlist);
            tvCardAllowed.setText(cardAllowed.isEmpty() ? "所有卡片" : cardAllowed);
            tvDuelMode.setText(duelMode.isEmpty() ? "单局模式" : duelMode);
            tvStartLP.setText(startLP.isEmpty() ? "8000" : startLP);
            tvStartHand.setText(startHand.isEmpty() ? "5" : startHand);
            tvDrawCount.setText(drawCount.isEmpty() ? "1" : drawCount);
        });

        btnExitLan.setOnClickListener(v -> popupWindow.dismiss());

        btnExitWaiting.setOnClickListener(v -> popupWindow.dismiss());

        btnReady.setOnClickListener(v -> {
            btnReady.setEnabled(false);
            btnReady.setText("已准备");
        });

        btnDuelistMode.setOnClickListener(v -> {
            btnDuelistMode.setEnabled(false);
            btnSpectatorMode.setEnabled(true);
        });

        btnSpectatorMode.setOnClickListener(v -> {
            btnSpectatorMode.setEnabled(false);
            btnDuelistMode.setEnabled(true);
        });

        btnRefreshLan.setOnClickListener(v -> {
            // TODO: 实际扫描局域网主机后调用 hostAdapter.set(hostNames) 刷新列表
        });

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }
}

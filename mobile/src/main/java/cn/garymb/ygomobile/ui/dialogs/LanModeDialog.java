package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.LimitManager;

public class LanModeDialog {

    private Context context;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    private DeckSelectorDialog deckSelectorDialog;
    
    private String currentDeckCategory = "";
    private String currentDeckName = "";
    private String currentDeckPath = "";

    private View layoutLanMain;
    private View layoutCreateHost;
    private View layoutPlayerWaiting;

    private EditText etPwPlayer1Name, etPwPlayer2Name, etPwPlayer3Name, etPwPlayer4Name;
    private CheckBox chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready;
    private Button btnPwDuelistMode, btnPwSpectatorMode, btnPwReady, btnPwDeckSelect, btnPwExitWaiting;
    private TextView tvPwBanlist, tvPwCardAllowed, tvPwDuelMode, tvPwStartLP, tvPwStartHand, tvPwDrawCount;
    private View layoutTagPlayers;

    public interface OnLanModeListener {
        void onCreateHostConfirmed(String banlist, String rule, String cardAllowed,
                                   String startLP, String duelMode, String startHand,
                                   String timeLimit, String drawCount,
                                   boolean noCheckDeck, boolean noShuffleDeck,
                                   String hostName, String password);
        void onJoinGameRequested(String ip, String port, String password, String nickname);
        void onExitLan();
        void onPlayerWaitingReady();
        void onPlayerWaitingToDuelist();
        void onPlayerWaitingToObserver();
        void onPlayerWaitingExit();
        void onPlayerWaitingDeckSelected(String deckPath, String deckName, String categoryName);
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

        layoutLanMain = customView.findViewById(R.id.layout_lan_main);
        layoutCreateHost = customView.findViewById(R.id.layout_create_host_settings);
        layoutPlayerWaiting = customView.findViewById(R.id.layout_player_waiting);

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

        initPlayerWaitingViews(layoutPlayerWaiting);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "lan_mode_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, customView);

        loadLastDeckInfo(btnPwDeckSelect);

        deckSelectorDialog = new DeckSelectorDialog(context);
        deckSelectorDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(String deckPath, String deckName, String categoryName) {
                currentDeckPath = deckPath;
                currentDeckName = deckName;
                currentDeckCategory = categoryName;
                updateDeckButtonText(btnPwDeckSelect);
                if (listener != null) {
                    listener.onPlayerWaitingDeckSelected(deckPath, deckName, categoryName);
                }
            }

            @Override
            public void onCancelled() {
            }
        });

        btnPwDeckSelect.setOnClickListener(v -> {
            if (deckSelectorDialog != null) {
                deckSelectorDialog.show(btnPwDeckSelect);
            }
        });

        setupSpinners(spinnerBanlist, spinnerRule, spinnerCardAllowed, spinnerDuelMode);

        btnCreateHost.setOnClickListener(v -> {
            layoutLanMain.setVisibility(View.GONE);
            layoutCreateHost.setVisibility(View.VISIBLE);
        });

        btnCancelCreate.setOnClickListener(v -> {
            layoutCreateHost.setVisibility(View.GONE);
            layoutLanMain.setVisibility(View.VISIBLE);
        });

        btnConfirmCreate.setOnClickListener(v -> {
            String banlist = SimpleSpinnerAdapter.getSelectText(spinnerBanlist);
            String rule = SimpleSpinnerAdapter.getSelectText(spinnerRule);
            String cardAllowed = SimpleSpinnerAdapter.getSelectText(spinnerCardAllowed);
            String startLP = etStartLP.getText().toString();
            String duelMode = SimpleSpinnerAdapter.getSelectText(spinnerDuelMode);
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
            showPlayerWaiting();

            tvPwBanlist.setText(banlist.isEmpty() ? "N/A" : banlist);
            tvPwCardAllowed.setText(cardAllowed.isEmpty() ? "所有卡片" : cardAllowed);
            tvPwDuelMode.setText(duelMode.isEmpty() ? "单局模式" : duelMode);
            tvPwStartLP.setText(startLP.isEmpty() ? "8000" : startLP);
            tvPwStartHand.setText(startHand.isEmpty() ? "5" : startHand);
            tvPwDrawCount.setText(drawCount.isEmpty() ? "1" : drawCount);
            
            AppsSettings.get().setLastLimit(banlist);
        });

        btnExitLan.setOnClickListener(v -> popupWindow.dismiss());

        btnPwExitWaiting.setOnClickListener(v -> {
            if (listener != null) listener.onPlayerWaitingExit();
            popupWindow.dismiss();
        });

        btnPwReady.setOnClickListener(v -> {
            if (listener != null) listener.onPlayerWaitingReady();
            btnPwReady.setEnabled(false);
            btnPwReady.setText("已准备");
        });

        btnPwDuelistMode.setOnClickListener(v -> {
            if (listener != null) listener.onPlayerWaitingToDuelist();
            btnPwDuelistMode.setEnabled(false);
            btnPwSpectatorMode.setEnabled(true);
        });

        btnPwSpectatorMode.setOnClickListener(v -> {
            if (listener != null) listener.onPlayerWaitingToObserver();
            btnPwSpectatorMode.setEnabled(false);
            btnPwDuelistMode.setEnabled(true);
        });

        btnRefreshLan.setOnClickListener(v -> {
        });

        btnJoinGame.setOnClickListener(v -> {
            String ip = etHostIp.getText().toString().trim();
            String port = etHostPort.getText().toString().trim();
            String password = etRoomPassword.getText().toString().trim();
            String nickname = etNickname.getText().toString().trim();

            if (ip.isEmpty()) {
                Toast.makeText(context, "请输入主机IP", Toast.LENGTH_SHORT).show();
                return;
            }
            if (port.isEmpty()) {
                Toast.makeText(context, "请输入端口", Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                listener.onJoinGameRequested(ip, port, password, nickname);
            }

            layoutLanMain.setVisibility(View.GONE);
            showPlayerWaiting();

            tvPwBanlist.setText("N/A");
            tvPwCardAllowed.setText("所有卡片");
            tvPwDuelMode.setText("单局模式");
            tvPwStartLP.setText("8000");
            tvPwStartHand.setText("5");
            tvPwDrawCount.setText("1");

            etPwPlayer1Name.setText(nickname.isEmpty() ? Constants.PlayerName : nickname);
        });

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    private void initPlayerWaitingViews(View layoutPlayerWaiting) {
        etPwPlayer1Name = layoutPlayerWaiting.findViewById(R.id.et_player1_name);
        etPwPlayer2Name = layoutPlayerWaiting.findViewById(R.id.et_player2_name);
        etPwPlayer3Name = layoutPlayerWaiting.findViewById(R.id.et_player3_name);
        etPwPlayer4Name = layoutPlayerWaiting.findViewById(R.id.et_player4_name);
        chkPwPlayer1Ready = layoutPlayerWaiting.findViewById(R.id.chk_player1_ready);
        chkPwPlayer2Ready = layoutPlayerWaiting.findViewById(R.id.chk_player2_ready);
        chkPwPlayer3Ready = layoutPlayerWaiting.findViewById(R.id.chk_player3_ready);
        chkPwPlayer4Ready = layoutPlayerWaiting.findViewById(R.id.chk_player4_ready);
        btnPwDuelistMode = layoutPlayerWaiting.findViewById(R.id.btn_duelist_mode);
        btnPwSpectatorMode = layoutPlayerWaiting.findViewById(R.id.btn_spectator_mode);
        btnPwReady = layoutPlayerWaiting.findViewById(R.id.btn_ready);
        btnPwDeckSelect = layoutPlayerWaiting.findViewById(R.id.btn_deck_select);
        btnPwExitWaiting = layoutPlayerWaiting.findViewById(R.id.btn_exit_waiting);
        tvPwBanlist = layoutPlayerWaiting.findViewById(R.id.tv_banlist);
        tvPwCardAllowed = layoutPlayerWaiting.findViewById(R.id.tv_card_allowed);
        tvPwDuelMode = layoutPlayerWaiting.findViewById(R.id.tv_duel_mode);
        tvPwStartLP = layoutPlayerWaiting.findViewById(R.id.tv_start_lp);
        tvPwStartHand = layoutPlayerWaiting.findViewById(R.id.tv_start_hand);
        tvPwDrawCount = layoutPlayerWaiting.findViewById(R.id.tv_draw_count);
        layoutTagPlayers = layoutPlayerWaiting.findViewById(R.id.layout_tag_players);
    }

    public void showPlayerWaiting() {
        if (layoutLanMain != null) layoutLanMain.setVisibility(View.GONE);
        if (layoutCreateHost != null) layoutCreateHost.setVisibility(View.GONE);
        if (layoutPlayerWaiting != null) layoutPlayerWaiting.setVisibility(View.VISIBLE);

        resetPlayerWaitingState();
    }

    public void resetPlayerWaitingState() {
        if (etPwPlayer1Name != null) etPwPlayer1Name.setText("");
        if (etPwPlayer2Name != null) etPwPlayer2Name.setText("");
        if (etPwPlayer3Name != null) etPwPlayer3Name.setText("");
        if (etPwPlayer4Name != null) etPwPlayer4Name.setText("");
        if (chkPwPlayer1Ready != null) chkPwPlayer1Ready.setChecked(false);
        if (chkPwPlayer2Ready != null) chkPwPlayer2Ready.setChecked(false);
        if (chkPwPlayer3Ready != null) chkPwPlayer3Ready.setChecked(false);
        if (chkPwPlayer4Ready != null) chkPwPlayer4Ready.setChecked(false);
        if (btnPwReady != null) {
            btnPwReady.setEnabled(true);
            btnPwReady.setText("点击准备");
        }
        if (btnPwDuelistMode != null) btnPwDuelistMode.setEnabled(false);
        if (btnPwSpectatorMode != null) btnPwSpectatorMode.setEnabled(true);
        if (layoutTagPlayers != null) layoutTagPlayers.setVisibility(View.GONE);
    }

    public void setPlayerName(int pos, String name) {
        switch (pos) {
            case 0: if (etPwPlayer1Name != null) etPwPlayer1Name.setText(name); break;
            case 1: if (etPwPlayer2Name != null) etPwPlayer2Name.setText(name); break;
            case 2: if (etPwPlayer3Name != null) etPwPlayer3Name.setText(name); break;
            case 3: if (etPwPlayer4Name != null) etPwPlayer4Name.setText(name); break;
        }
    }

    public void setPlayerReady(int pos, boolean ready) {
        switch (pos) {
            case 0: if (chkPwPlayer1Ready != null) chkPwPlayer1Ready.setChecked(ready); break;
            case 1: if (chkPwPlayer2Ready != null) chkPwPlayer2Ready.setChecked(ready); break;
            case 2: if (chkPwPlayer3Ready != null) chkPwPlayer3Ready.setChecked(ready); break;
            case 3: if (chkPwPlayer4Ready != null) chkPwPlayer4Ready.setChecked(ready); break;
        }
    }

    public void clearPlayerPos(int pos) {
        setPlayerName(pos, "");
        setPlayerReady(pos, false);
    }

    public void movePlayer(int fromPos, int toPos) {
        String name = "";
        switch (fromPos) {
            case 0: name = etPwPlayer1Name != null ? etPwPlayer1Name.getText().toString() : ""; break;
            case 1: name = etPwPlayer2Name != null ? etPwPlayer2Name.getText().toString() : ""; break;
            case 2: name = etPwPlayer3Name != null ? etPwPlayer3Name.getText().toString() : ""; break;
            case 3: name = etPwPlayer4Name != null ? etPwPlayer4Name.getText().toString() : ""; break;
        }
        setPlayerName(toPos, name);
        clearPlayerPos(fromPos);
    }

    public void updateRoomInfo(int mode, int startLp, int startHand, int drawCount) {
        if (tvPwStartLP != null) tvPwStartLP.setText(String.valueOf(startLp));
        if (tvPwStartHand != null) tvPwStartHand.setText(String.valueOf(startHand));
        if (tvPwDrawCount != null) tvPwDrawCount.setText(String.valueOf(drawCount));

        String duelModeText;
        switch (mode) {
            case 0: duelModeText = "单局模式"; break;
            case 1: duelModeText = "三局两胜"; break;
            case 2: duelModeText = "TAG"; break;
            default: duelModeText = "单局模式";
        }
        if (tvPwDuelMode != null) tvPwDuelMode.setText(duelModeText);

        if (layoutTagPlayers != null) {
            layoutTagPlayers.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        }
    }

    public void updateTypeChange(int selfType, boolean isTag) {
        if (selfType < 2 || (isTag && selfType < 4)) {
            if (btnPwReady != null) btnPwReady.setEnabled(true);
            if (btnPwDuelistMode != null) btnPwDuelistMode.setEnabled(false);
            if (btnPwSpectatorMode != null) btnPwSpectatorMode.setEnabled(true);
        } else {
            if (btnPwReady != null) btnPwReady.setEnabled(false);
            if (btnPwDuelistMode != null) btnPwDuelistMode.setEnabled(true);
            if (btnPwSpectatorMode != null) btnPwSpectatorMode.setEnabled(false);
        }
    }

    public boolean isPlayerWaitingVisible() {
        return layoutPlayerWaiting != null && layoutPlayerWaiting.getVisibility() == View.VISIBLE;
    }

    private void setupSpinners(Spinner spinnerBanlist, Spinner spinnerRule, 
                               Spinner spinnerCardAllowed, Spinner spinnerDuelMode) {
        LimitManager limitManager = DataManager.get().getLimitManager();
        
        List<SimpleSpinnerItem> banlistItems = new ArrayList<>();
        banlistItems.add(new SimpleSpinnerItem(0, "N/A"));
        
        boolean isGenesysMode = AppsSettings.get().getGenesysMode() == 1;
        List<String> limitNames = isGenesysMode ? 
            limitManager.getGenesysLimitNames() : limitManager.getLimitNames();
        
        for (String limitName : limitNames) {
            banlistItems.add(new SimpleSpinnerItem(banlistItems.size(), limitName));
        }
        
        SimpleSpinnerAdapter banlistAdapter = new SimpleSpinnerAdapter(context);
        banlistAdapter.setColor(Color.WHITE);
        banlistAdapter.set(banlistItems);
        spinnerBanlist.setAdapter(banlistAdapter);
        
        String lastLimit = isGenesysMode ? 
            AppsSettings.get().getLastGenesysLimit() : AppsSettings.get().getLastLimit();
        
        int selectedIndex = 0;
        for (int i = 0; i < banlistItems.size(); i++) {
            if (banlistItems.get(i).text.equals(lastLimit)) {
                selectedIndex = i;
                break;
            }
        }
        spinnerBanlist.setSelection(selectedIndex);

        List<SimpleSpinnerItem> ruleItems = new ArrayList<>();
        ruleItems.add(new SimpleSpinnerItem(0, "大师规则4"));
        ruleItems.add(new SimpleSpinnerItem(1, "大师规则2020"));
        ruleItems.add(new SimpleSpinnerItem(2, "新大师规则"));
        ruleItems.add(new SimpleSpinnerItem(3, "大师规则"));
        
        SimpleSpinnerAdapter ruleAdapter = new SimpleSpinnerAdapter(context);
        ruleAdapter.setColor(Color.WHITE);
        ruleAdapter.set(ruleItems);
        spinnerRule.setAdapter(ruleAdapter);
        spinnerRule.setSelection(1);

        List<SimpleSpinnerItem> cardAllowedItems = new ArrayList<>();
        cardAllowedItems.add(new SimpleSpinnerItem(0, "所有卡片"));
        cardAllowedItems.add(new SimpleSpinnerItem(1, "仅OCG"));
        cardAllowedItems.add(new SimpleSpinnerItem(2, "仅TCG"));
        
        SimpleSpinnerAdapter cardAllowedAdapter = new SimpleSpinnerAdapter(context);
        cardAllowedAdapter.setColor(Color.WHITE);
        cardAllowedAdapter.set(cardAllowedItems);
        spinnerCardAllowed.setAdapter(cardAllowedAdapter);
        spinnerCardAllowed.setSelection(0);

        List<SimpleSpinnerItem> duelModeItems = new ArrayList<>();
        duelModeItems.add(new SimpleSpinnerItem(0, "单局模式"));
        duelModeItems.add(new SimpleSpinnerItem(1, "三局两胜"));
        duelModeItems.add(new SimpleSpinnerItem(2, "TAG"));
        
        SimpleSpinnerAdapter duelModeAdapter = new SimpleSpinnerAdapter(context);
        duelModeAdapter.setColor(Color.WHITE);
        duelModeAdapter.set(duelModeItems);
        spinnerDuelMode.setAdapter(duelModeAdapter);
        spinnerDuelMode.setSelection(0);
    }

    private void loadLastDeckInfo(Button btnDeckSelect) {
        AppsSettings settings = AppsSettings.get();
        currentDeckCategory = settings.getLastCategory();
        currentDeckName = settings.getLastDeckName();
        currentDeckPath = settings.getLastDeckPath();
        updateDeckButtonText(btnDeckSelect);
    }

    private void updateDeckButtonText(Button btnDeckSelect) {
        if (currentDeckName != null && !currentDeckName.isEmpty()) {
            String displayText;
            if (currentDeckCategory != null && !currentDeckCategory.isEmpty()) {
                displayText = "[" + currentDeckCategory + "]" + currentDeckName;
            } else {
                displayText = currentDeckName;
            }
            btnDeckSelect.setText(displayText);
        } else {
            btnDeckSelect.setText("请选择卡组");
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        if (deckSelectorDialog != null) {
            deckSelectorDialog.dismiss();
        }
    }
}

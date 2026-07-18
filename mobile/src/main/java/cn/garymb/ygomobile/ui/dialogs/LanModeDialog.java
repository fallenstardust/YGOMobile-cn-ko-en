package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.YGOUtil;
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

    private TextView etPwPlayer1Name, etPwPlayer2Name, etPwPlayer3Name, etPwPlayer4Name;
    private CheckBox chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready;
    private Button btnPwDuelistMode, btnPwSpectatorMode, btnPwReady, btnPwDeckSelect, btnPwExitWaiting;
    private Button btnPwStartGame;
    private ImageButton btnPwKickPlayer1, btnPwKickPlayer2, btnPwKickPlayer3, btnPwKickPlayer4;
    private TextView tvPwBanlist, tvPwCardAllowed, tvPwDuelMode, tvPwStartLP, tvPwStartHand, tvPwDrawCount, tvPwTimeLimit;
    private TextView tvWatchCount;
    private View layoutTagPlayers;
    private int selfPos = 0;
    private boolean isSelfReady = false;
    private boolean isHost = false;
    private int watchCount = 0;
    private List<String> observerNames = new ArrayList<>();

    public interface OnLanModeListener {
        void onCreateHostConfirmed(int lflist, int ruleIdx, int modeIdx, int duelRule,
                                   int startLP, int startHand, int drawCount, int timeLimit,
                                   boolean noCheckDeck, boolean noShuffleDeck,
                                   String hostName, String password);

        void onJoinGameRequested(String ip, String port, String password, String nickname);

        void onExitLan();

        void onPlayerWaitingReady();

        void onPlayerWaitingNotReady();

        void onPlayerWaitingToDuelist();

        void onPlayerWaitingToObserver();

        void onPlayerWaitingExit();

        void onPlayerWaitingDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side);

        void onStartGameRequested();

        void onKickPlayerRequested(int pos);
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

        initPlayerWaitingViews(customView);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                return true;
            }
            return false;
        });
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
                sendDeckIfLoaded();
            }

            @Override
            public void onCancelled() {
            }
        });

        btnPwDeckSelect.setOnClickListener(v -> {
            if (deckSelectorDialog != null) {
                deckSelectorDialog.show(btnPwDeckSelect);
                deckSelectorDialog.setOperationButtonsEnabled(false);
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
            String startLPStr = etStartLP.getText().toString();
            String duelMode = SimpleSpinnerAdapter.getSelectText(spinnerDuelMode);
            String startHandStr = etStartHand.getText().toString();
            String timeLimitStr = etTimeLimit.getText().toString();
            String drawCountStr = etDrawCount.getText().toString();
            boolean noCheckDeck = chkNoCheckDeck.isChecked();
            boolean noShuffleDeck = chkNoShuffleDeck.isChecked();
            String hostName = etHostName.getText().toString();
            String password = etHostPassword.getText().toString();

            int lflist = parseBanlistIndex(banlist);
            int ruleIdx = parseRuleIndex(rule);
            int modeIdx = parseDuelModeIndex(duelMode);
            int duelRule = ruleIdx + 1;
            int lp = parseIntSafe(startLPStr, 8000);
            int hand = parseIntSafe(startHandStr, 5);
            int draw = parseIntSafe(drawCountStr, 1);
            int time = parseIntSafe(timeLimitStr, 0);

            if (listener != null) {
                listener.onCreateHostConfirmed(lflist, ruleIdx, modeIdx, duelRule,
                        lp, hand, draw, time,
                        noCheckDeck, noShuffleDeck, hostName, password);
            }

            layoutCreateHost.setVisibility(View.GONE);
            showPlayerWaiting();

            tvPwBanlist.setText(banlist.isEmpty() ? "N/A" : banlist);
            tvPwCardAllowed.setText(cardAllowed.isEmpty() ? "所有卡片" : cardAllowed);
            tvPwDuelMode.setText(duelMode.isEmpty() ? "单局模式" : duelMode);
            tvPwStartLP.setText(startLPStr.isEmpty() ? "8000" : startLPStr);
            tvPwStartHand.setText(startHandStr.isEmpty() ? "5" : startHandStr);
            tvPwDrawCount.setText(drawCountStr.isEmpty() ? "1" : drawCountStr);
            tvPwTimeLimit.setText(timeLimitStr.isEmpty() ? "0" : timeLimitStr);

            if (layoutTagPlayers != null) {
                layoutTagPlayers.setVisibility("TAG".equals(duelMode) ? View.VISIBLE : View.INVISIBLE);
            }

            etPwPlayer1Name.setText(etNickname.getText().toString().isEmpty() ? Constants.PlayerName : etNickname.getText().toString());
        });

        btnExitLan.setOnClickListener(v -> popupWindow.dismiss());

        btnPwExitWaiting.setOnClickListener(v -> {
            if (listener != null) listener.onPlayerWaitingExit();
            popupWindow.dismiss();
        });

        setupSelfReadyInteraction();

        btnPwDuelistMode.setOnClickListener(v -> {
            boolean isSpectator = (selfPos >= 4);
            int targetPos;
            if (isSpectator) {
                targetPos = findFirstEmptyPos();
            } else {
                targetPos = findNextEmptyPos(selfPos);
            }
            if (targetPos >= 0) {
                boolean wasReady = isSelfReady;
                String selfName = "";
                if (isSpectator) {
                    selfName = getPlayerName(targetPos);
                } else {
                    selfName = getPlayerName(selfPos);
                    setPlayerName(targetPos, selfName);
                    setPlayerReady(targetPos, wasReady);
                }
                selfPos = targetPos;
                isSelfReady = wasReady;
                updateSelfCheckboxInteractivity();
                if (listener != null) listener.onPlayerWaitingToDuelist();
                btnPwSpectatorMode.setEnabled(true);
                if (btnPwReady != null) btnPwReady.setVisibility(View.VISIBLE);
                refreshPlayerDisplay();
            }
        });

        btnPwSpectatorMode.setOnClickListener(v -> {
            String selfName = "";
            if (selfPos < 4) {
                selfName = getPlayerName(selfPos);
                setPlayerName(selfPos, "");
                setPlayerReady(selfPos, false);
                isSelfReady = false;
            }
            CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
            for (int i = 0; i < checkboxes.length; i++) {
                if (checkboxes[i] != null) {
                    checkboxes[i].setEnabled(false);
                    checkboxes[i].setClickable(false);
                    checkboxes[i].setOnCheckedChangeListener(null);
                }
            }
            selfPos = 7;
            if (!selfName.isEmpty()) {
                addObserver(selfName);
            }
            if (listener != null) listener.onPlayerWaitingToObserver();
            btnPwSpectatorMode.setEnabled(false);
            btnPwDuelistMode.setEnabled(true);
            if (btnPwReady != null) btnPwReady.setVisibility(View.INVISIBLE);
            refreshPlayerDisplay();
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
            if (tvPwTimeLimit != null) tvPwTimeLimit.setText("0");

            if (layoutTagPlayers != null) layoutTagPlayers.setVisibility(View.GONE);

            etPwPlayer1Name.setText(nickname.isEmpty() ? Constants.PlayerName : nickname);
        });

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    private void initPlayerWaitingViews(View root) {
        View layoutPlayerWaiting = root.findViewById(R.id.layout_player_waiting);
        if (layoutPlayerWaiting == null) return;

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
        btnPwStartGame = layoutPlayerWaiting.findViewById(R.id.btn_start_game);
        btnPwKickPlayer1 = layoutPlayerWaiting.findViewById(R.id.btn_kick_player1);
        btnPwKickPlayer2 = layoutPlayerWaiting.findViewById(R.id.btn_kick_player2);
        btnPwKickPlayer3 = layoutPlayerWaiting.findViewById(R.id.btn_kick_player3);
        btnPwKickPlayer4 = layoutPlayerWaiting.findViewById(R.id.btn_kick_player4);
        tvPwBanlist = layoutPlayerWaiting.findViewById(R.id.tv_banlist);
        tvPwCardAllowed = layoutPlayerWaiting.findViewById(R.id.tv_card_allowed);
        tvPwDuelMode = layoutPlayerWaiting.findViewById(R.id.tv_duel_mode);
        tvPwStartLP = layoutPlayerWaiting.findViewById(R.id.tv_start_lp);
        tvPwStartHand = layoutPlayerWaiting.findViewById(R.id.tv_start_hand);
        tvPwDrawCount = layoutPlayerWaiting.findViewById(R.id.tv_draw_count);
        tvPwTimeLimit = layoutPlayerWaiting.findViewById(R.id.tv_time_limit);
        tvWatchCount = layoutPlayerWaiting.findViewById(R.id.tv_watch_count);
        layoutTagPlayers = layoutPlayerWaiting.findViewById(R.id.layout_tag_players);
        
        setupWatchView();
    }

    private void setupWatchView() {
        if (tvWatchCount != null) {
            tvWatchCount.setOnClickListener(v -> showWatchersToast());
        }
    }

    private void showWatchersToast() {
        if (observerNames.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < observerNames.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(observerNames.get(i));
        }
        Toast toast = Toast.makeText(context, sb.toString(), Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.END | Gravity.CENTER_VERTICAL, 0, 0);
        toast.show();
    }

    public void updateWatchCount(int count) {
        watchCount = count;
        if (count == 0) {
            observerNames.clear();
        }
        refreshWatchCountDisplay();
    }

    public void addObserver(String name) {
        if (name != null && !name.isEmpty() && !observerNames.contains(name)) {
            observerNames.add(name);
            watchCount++;
            refreshWatchCountDisplay();
        }
    }

    public void removeObserver(String name) {
        if (observerNames.remove(name)) {
            watchCount--;
            if (watchCount < 0) watchCount = 0;
            refreshWatchCountDisplay();
        }
    }

    public void clearObservers() {
        observerNames.clear();
        watchCount = 0;
        refreshWatchCountDisplay();
    }

    private void refreshWatchCountDisplay() {
        if (tvWatchCount != null) {
            tvWatchCount.setText("当前观战人数: " + watchCount);
            if (watchCount > 0 || selfPos >= 4) {
                tvWatchCount.setVisibility(View.VISIBLE);
            } else {
                tvWatchCount.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void showPlayerWaiting() {
        if (layoutLanMain != null) layoutLanMain.setVisibility(View.GONE);
        if (layoutCreateHost != null) layoutCreateHost.setVisibility(View.GONE);
        if (layoutPlayerWaiting != null) layoutPlayerWaiting.setVisibility(View.VISIBLE);

        resetPlayerWaitingState();
        setupKickButtons();
        setupStartButton();
    }

    public void resetPlayerWaitingState() {
        if (etPwPlayer1Name != null) etPwPlayer1Name.setText("");
        if (etPwPlayer2Name != null) etPwPlayer2Name.setText("");
        if (etPwPlayer3Name != null) etPwPlayer3Name.setText("");
        if (etPwPlayer4Name != null) etPwPlayer4Name.setText("");
        if (chkPwPlayer1Ready != null) {
            chkPwPlayer1Ready.setChecked(false);
            chkPwPlayer1Ready.setOnCheckedChangeListener(null);
        }
        if (chkPwPlayer2Ready != null) {
            chkPwPlayer2Ready.setChecked(false);
            chkPwPlayer2Ready.setOnCheckedChangeListener(null);
        }
        if (chkPwPlayer3Ready != null) {
            chkPwPlayer3Ready.setChecked(false);
            chkPwPlayer3Ready.setOnCheckedChangeListener(null);
        }
        if (chkPwPlayer4Ready != null) {
            chkPwPlayer4Ready.setChecked(false);
            chkPwPlayer4Ready.setOnCheckedChangeListener(null);
        }
        isSelfReady = false;
        selfPos = 0;
        if (btnPwReady != null) {
            btnPwReady.setEnabled(true);
            btnPwReady.setText("点击准备");
            btnPwReady.setPressed(false);
        }
        if (btnPwSpectatorMode != null) btnPwSpectatorMode.setEnabled(true);
        
        if (btnPwStartGame != null) btnPwStartGame.setVisibility(View.INVISIBLE);
        if (layoutTagPlayers != null) layoutTagPlayers.setVisibility(View.INVISIBLE);
        
        watchCount = 0;
        observerNames.clear();
        if (tvWatchCount != null) tvWatchCount.setVisibility(View.INVISIBLE);
        if (tvWatchCount != null) tvWatchCount.setText("当前观战人数: 0");

        updateSelfCheckboxInteractivity();
    }

    public void setPlayerName(int pos, String name) {
        switch (pos) {
            case 0:
                if (etPwPlayer1Name != null) etPwPlayer1Name.setText(name);
                break;
            case 1:
                if (etPwPlayer2Name != null) etPwPlayer2Name.setText(name);
                break;
            case 2:
                if (etPwPlayer3Name != null) etPwPlayer3Name.setText(name);
                break;
            case 3:
                if (etPwPlayer4Name != null) etPwPlayer4Name.setText(name);
                break;
        }
    }

    public String getPlayerName(int pos) {
        TextView nameField = null;
        switch (pos) {
            case 0:
                nameField = etPwPlayer1Name;
                break;
            case 1:
                nameField = etPwPlayer2Name;
                break;
            case 2:
                nameField = etPwPlayer3Name;
                break;
            case 3:
                nameField = etPwPlayer4Name;
                break;
        }
        return nameField != null ? nameField.getText().toString() : "";
    }

    public void setPlayerReady(int pos, boolean ready) {
        if (pos >= 4) return;
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        if (pos >= 0 && pos < checkboxes.length && checkboxes[pos] != null) {
            if (pos == selfPos) {
                isSelfReady = ready;
                if (btnPwReady != null) {
                    btnPwReady.setText(ready ? "取消准备" : "点击准备");
                    btnPwReady.setPressed(ready);
                    btnPwReady.setBackground(ready ? context.getDrawable(R.drawable.sbutton_p) : context.getDrawable(R.drawable.sbutton));
                }
            }
            checkboxes[pos].setChecked(ready);
        }
    }

    public void clearPlayerPos(int pos) {
        setPlayerName(pos, "");
        setPlayerReady(pos, false);
    }

    public void movePlayer(int fromPos, int toPos) {
        String name = "";
        switch (fromPos) {
            case 0:
                name = etPwPlayer1Name != null ? etPwPlayer1Name.getText().toString() : "";
                break;
            case 1:
                name = etPwPlayer2Name != null ? etPwPlayer2Name.getText().toString() : "";
                break;
            case 2:
                name = etPwPlayer3Name != null ? etPwPlayer3Name.getText().toString() : "";
                break;
            case 3:
                name = etPwPlayer4Name != null ? etPwPlayer4Name.getText().toString() : "";
                break;
        }

        boolean wasReady = false;
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        if (fromPos >= 0 && fromPos < checkboxes.length && checkboxes[fromPos] != null) {
            wasReady = checkboxes[fromPos].isChecked();
        }

        setPlayerName(toPos, name);
        setPlayerReady(toPos, wasReady);
        clearPlayerPos(fromPos);
    }

    public void refreshPlayerDisplay() {
        for (int i = 0; i < 4; i++) {
            String name = getPlayerName(i);
            if (etPwPlayer1Name != null && i == 0) etPwPlayer1Name.setText(name);
            if (etPwPlayer2Name != null && i == 1) etPwPlayer2Name.setText(name);
            if (etPwPlayer3Name != null && i == 2) etPwPlayer3Name.setText(name);
            if (etPwPlayer4Name != null && i == 3) etPwPlayer4Name.setText(name);
        }

        if (selfPos < 4) {
            if (btnPwReady != null) {
                btnPwReady.setText(isSelfReady ? "取消准备" : "点击准备");
                btnPwReady.setPressed(isSelfReady);
                btnPwReady.setBackground(isSelfReady
                        ? context.getDrawable(R.drawable.sbutton_p)
                        : context.getDrawable(R.drawable.sbutton));
            }
            CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
            if (selfPos >= 0 && selfPos < checkboxes.length && checkboxes[selfPos] != null) {
                checkboxes[selfPos].setChecked(isSelfReady);
            }
        }

        if (tvWatchCount != null) {
            tvWatchCount.setText("当前观战人数: " + watchCount);
            if (watchCount > 0 || selfPos >= 4) {
                tvWatchCount.setVisibility(View.VISIBLE);
            } else {
                tvWatchCount.setVisibility(View.INVISIBLE);
            }
        }

        updateSelfCheckboxInteractivity();
    }

    private int findNextEmptyPos(int currentPos) {
        for (int i = currentPos + 1; i < 4; i++) {
            if (getPlayerName(i).isEmpty()) return i;
        }
        for (int i = 0; i < currentPos; i++) {
            if (getPlayerName(i).isEmpty()) return i;
        }
        return -1;
    }

    private int findFirstEmptyPos() {
        for (int i = 0; i < 4; i++) {
            if (getPlayerName(i).isEmpty()) return i;
        }
        return -1;
    }

    public void updateRoomInfo(int mode, int startLp, int startHand, int drawCount, int timeLimit) {
        if (tvPwStartLP != null) tvPwStartLP.setText(String.valueOf(startLp));
        if (tvPwStartHand != null) tvPwStartHand.setText(String.valueOf(startHand));
        if (tvPwDrawCount != null) tvPwDrawCount.setText(String.valueOf(drawCount));
        if (tvPwTimeLimit != null) tvPwTimeLimit.setText(timeLimit > 0 ? String.valueOf(timeLimit) : "无限制");

        String duelModeText;
        switch (mode) {
            case 0:
                duelModeText = "单局模式";
                break;
            case 1:
                duelModeText = "比赛模式";
                break;
            case 2:
                duelModeText = "TAG";
                break;
            default:
                duelModeText = "unknown mode";
        }
        if (tvPwDuelMode != null) tvPwDuelMode.setText(duelModeText);

        if (layoutTagPlayers != null) {
            layoutTagPlayers.setVisibility(mode == 2 ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void updateTypeChange(int selfType, boolean isTag, boolean isHost) {
        selfPos = selfType;
        this.isHost = isHost;
        updateSelfCheckboxInteractivity();

        if (selfType < 2 || (isTag && selfType < 4)) {
            if (btnPwReady != null) btnPwReady.setEnabled(true);
        } else if (selfType < 4) {
            if (btnPwReady != null) btnPwReady.setEnabled(true);
        } else {
            if (btnPwReady != null) btnPwReady.setEnabled(false);
        }

        if (selfType >= 4) {
            if (btnPwReady != null) btnPwReady.setVisibility(View.INVISIBLE);
            if (btnPwSpectatorMode != null) {
                btnPwSpectatorMode.setEnabled(false);
                btnPwSpectatorMode.setTextColor(Color.GRAY);
            }
            if (btnPwDuelistMode != null) {
                btnPwDuelistMode.setEnabled(true);
                btnPwDuelistMode.setTextColor(Color.WHITE);
            }
        } else {
            if (btnPwReady != null) btnPwReady.setVisibility(View.VISIBLE);
            if (btnPwSpectatorMode != null) {
                btnPwSpectatorMode.setEnabled(true);
                btnPwSpectatorMode.setTextColor(Color.WHITE);
            }
            if (btnPwDuelistMode != null) {
                if (!isTag || isSelfReady) {
                    btnPwDuelistMode.setEnabled(false);
                    btnPwDuelistMode.setTextColor(Color.GRAY);
                } else {
                    btnPwDuelistMode.setEnabled(true);
                    btnPwDuelistMode.setTextColor(Color.WHITE);
                }
            }
        }

        if (selfType >= 4 && tvWatchCount != null) {
            tvWatchCount.setVisibility(View.VISIBLE);
        }

        if (btnPwStartGame != null) {
            btnPwStartGame.setVisibility(isHost ? View.VISIBLE : View.INVISIBLE);
        }

        if (isHost) {
            if (btnPwKickPlayer1 != null) btnPwKickPlayer1.setVisibility(View.VISIBLE);
            if (btnPwKickPlayer2 != null) btnPwKickPlayer2.setVisibility(View.VISIBLE);
            if (btnPwKickPlayer3 != null) btnPwKickPlayer3.setVisibility(View.VISIBLE);
            if (btnPwKickPlayer4 != null) btnPwKickPlayer4.setVisibility(View.VISIBLE);
        } else {
            if (btnPwKickPlayer1 != null) btnPwKickPlayer1.setVisibility(View.INVISIBLE);
            if (btnPwKickPlayer2 != null) btnPwKickPlayer2.setVisibility(View.INVISIBLE);
            if (btnPwKickPlayer3 != null) btnPwKickPlayer3.setVisibility(View.INVISIBLE);
            if (btnPwKickPlayer4 != null) btnPwKickPlayer4.setVisibility(View.INVISIBLE);
        }
    }

    public boolean isPlayerWaitingVisible() {
        return layoutPlayerWaiting != null && layoutPlayerWaiting.getVisibility() == View.VISIBLE;
    }

    public void preFillConnectionFields(String nickname, String hostIp, String port, String roomPassword) {
        if (layoutLanMain == null) return;
        EditText etNickname = layoutLanMain.findViewById(R.id.et_nickname);
        EditText etHostIp = layoutLanMain.findViewById(R.id.et_host_ip);
        EditText etHostPort = layoutLanMain.findViewById(R.id.et_host_port);
        EditText etRoomPassword = layoutLanMain.findViewById(R.id.et_room_password);
        if (nickname != null && !nickname.isEmpty() && etNickname != null) etNickname.setText(nickname);
        if (hostIp != null && !hostIp.isEmpty() && etHostIp != null) etHostIp.setText(hostIp);
        if (port != null && !port.isEmpty() && etHostPort != null) etHostPort.setText(port);
        if (roomPassword != null && etRoomPassword != null) etRoomPassword.setText(roomPassword);
    }

    public void showLanMain() {
        if (layoutPlayerWaiting != null) layoutPlayerWaiting.setVisibility(View.GONE);
        if (layoutCreateHost != null) layoutCreateHost.setVisibility(View.GONE);
        if (layoutLanMain != null) layoutLanMain.setVisibility(View.VISIBLE);
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

    private void setupSelfReadyInteraction() {
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        for (int i = 0; i < checkboxes.length; i++) {
            if (checkboxes[i] == null) continue;
            checkboxes[i].setEnabled(false);
            checkboxes[i].setClickable(false);
        }

        if (btnPwReady != null) {
            btnPwReady.setOnClickListener(v -> {
                if (!isSelfReady) {
                    if (currentDeckPath == null || currentDeckPath.isEmpty()) {
                        Toast.makeText(context, "请先选择卡组", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendDeckIfLoaded();
                    isSelfReady = true;
                    setSelfCheckboxChecked(true);
                    btnPwReady.setText("取消准备");
                    btnPwReady.setPressed(true);
                    if (btnPwDuelistMode != null) {
                        btnPwDuelistMode.setEnabled(false);
                        btnPwDuelistMode.setTextColor(Color.GRAY);
                    }
                    if (listener != null) listener.onPlayerWaitingReady();
                } else {
                    isSelfReady = false;
                    setSelfCheckboxChecked(false);
                    btnPwReady.setText("点击准备");
                    btnPwReady.setPressed(false);
                    if (btnPwDuelistMode != null) {
                        btnPwDuelistMode.setEnabled(true);
                        btnPwDuelistMode.setTextColor(Color.WHITE);
                    }
                    if (listener != null) listener.onPlayerWaitingNotReady();
                }
            });
        }
    }

    private void setSelfCheckboxChecked(boolean checked) {
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        if (selfPos >= 0 && selfPos < checkboxes.length && checkboxes[selfPos] != null) {
            checkboxes[selfPos].setChecked(checked);
        }
    }

    private void updateSelfCheckboxInteractivity() {
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        for (int i = 0; i < checkboxes.length; i++) {
            if (checkboxes[i] == null) continue;
            if (i == selfPos) {
                checkboxes[i].setEnabled(true);
                checkboxes[i].setClickable(true);
                final int pos = i;
                checkboxes[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (pos != selfPos) return;
                    if (isChecked) {
                        if (currentDeckPath == null || currentDeckPath.isEmpty()) {
                            Toast.makeText(context, "请先选择卡组", Toast.LENGTH_SHORT).show();
                            buttonView.setChecked(false);
                            isSelfReady = false;
                            btnPwReady.setText("点击准备");
                            btnPwReady.setPressed(false);
                            return;
                        }
                        sendDeckIfLoaded();
                        isSelfReady = true;
                        btnPwReady.setText("取消准备");
                        btnPwReady.setPressed(true);
                        if (btnPwDuelistMode != null) {
                            btnPwDuelistMode.setEnabled(false);
                            btnPwDuelistMode.setTextColor(Color.GRAY);
                        }
                        if (listener != null) listener.onPlayerWaitingReady();
                    } else {
                        isSelfReady = false;
                        btnPwReady.setText("点击准备");
                        btnPwReady.setPressed(false);
                        if (btnPwDuelistMode != null) {
                            btnPwDuelistMode.setEnabled(true);
                            btnPwDuelistMode.setTextColor(Color.WHITE);
                        }
                        if (listener != null) listener.onPlayerWaitingNotReady();
                    }
                });
            } else {
                checkboxes[i].setEnabled(false);
                checkboxes[i].setClickable(false);
                checkboxes[i].setOnCheckedChangeListener(null);
            }
        }
    }

    private void sendDeckIfLoaded() {
        if (currentDeckPath == null || currentDeckPath.isEmpty()) return;
        File ydkFile = new File(currentDeckPath);
        if (!ydkFile.exists()) return;

        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(ydkFile))) {
            String line;
            int section = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("#main")) {
                    section = 1;
                    continue;
                }
                if (line.equalsIgnoreCase("#extra")) {
                    section = 2;
                    continue;
                }
                if (line.equalsIgnoreCase("!side")) {
                    section = 3;
                    continue;
                }
                if (line.startsWith("#")) continue;
                try {
                    int code = Integer.parseInt(line);
                    switch (section) {
                        case 1: main.add(code); break;
                        case 2: extra.add(code); break;
                        case 3: side.add(code); break;
                    }
                } catch (NumberFormatException e) { /* skip */ }
            }
        } catch (Exception e) {
            Toast.makeText(context, "卡组加载失败", Toast.LENGTH_SHORT).show();
            return;
        }

        if (listener != null) {
            listener.onPlayerWaitingDeckUpdate(main, extra, side);
        }
    }

    public static int parseBanlistIndex(String banlist) {
        if (banlist == null || banlist.equals("N/A")) return 0;
        LimitManager limitManager = DataManager.get().getLimitManager();
        boolean isGenesysMode = AppsSettings.get().getGenesysMode() == 1;
        List<String> limitNames = isGenesysMode ?
                limitManager.getGenesysLimitNames() : limitManager.getLimitNames();
        for (int i = 0; i < limitNames.size(); i++) {
            if (limitNames.get(i).equals(banlist)) return i + 1;
        }
        return 0;
    }

    public static int parseRuleIndex(String rule) {
        if (rule == null) return 1;
        switch (rule) {
            case "大师规则4":
                return 0;
            case "大师规则2020":
                return 1;
            case "新大师规则":
                return 2;
            case "大师规则":
                return 3;
            default:
                return 1;
        }
    }

    public static int parseDuelModeIndex(String duelMode) {
        if (duelMode == null) return 0;
        switch (duelMode) {
            case "单局模式":
                return 0;
            case "三局两胜":
                return 1;
            case "TAG":
                return 2;
            default:
                return 0;
        }
    }

    public static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
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

    private void setupKickButtons() {
        if (btnPwKickPlayer1 != null) {
            btnPwKickPlayer1.setOnClickListener(v -> sendKickPacket(0));
        }
        if (btnPwKickPlayer2 != null) {
            btnPwKickPlayer2.setOnClickListener(v -> sendKickPacket(1));
        }
        if (btnPwKickPlayer3 != null) {
            btnPwKickPlayer3.setOnClickListener(v -> sendKickPacket(2));
        }
        if (btnPwKickPlayer4 != null) {
            btnPwKickPlayer4.setOnClickListener(v -> sendKickPacket(3));
        }
    }

    private void sendKickPacket(int pos) {
        if (!isHost) {
            return;
        }
        String playerName = getPlayerName(pos);
        if (playerName.isEmpty()) {
            return;
        }
        if (pos == selfPos) {
            return;
        }
        if (listener != null) {
            listener.onKickPlayerRequested(pos);
        }
    }

    private void setupStartButton() {
        if (btnPwStartGame != null) {
            btnPwStartGame.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStartGameRequested();
                }
            });
        }
    }

    private void updateStartButtonVisibility() {
        if (btnPwStartGame != null && isHost) {
            btnPwStartGame.setVisibility(View.VISIBLE);
        }
    }
}

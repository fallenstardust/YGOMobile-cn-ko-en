package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;

/**
 * 玩家等待（大厅）界面（layout_player_waiting）：玩家/观战席位、准备状态、卡组选择、
 * 踢人、开始游戏、房间信息展示，以及来自引擎的玩家/房间/卡组校验事件处理。
 */
public class PlayerWaitingDialog {
    public static final StringManager mStringManager = DataManager.get().getStringManager();

    private final Context context;
    private PopupWindow popupWindow;
    private boolean suppressDismiss = false;
    private PopupWindow.OnDismissListener externalDismissListener;
    private DraggablePopupHelper draggableHelper;
    private DeckSelectorDialog deckSelectorDialog;

    private String currentDeckCategory = "";
    private String currentDeckName = "";
    private String currentDeckPath = "";

    private TextView etPwPlayer1Name, etPwPlayer2Name, etPwPlayer3Name, etPwPlayer4Name;
    private CheckBox chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready;
    private Button btnPwDuelistMode, btnPwSpectatorMode, btnPwReady, btnPwDeckSelect, btnPwExitWaiting;
    private Button btnPwStartGame;
    private ImageButton btnPwKickPlayer1, btnPwKickPlayer2, btnPwKickPlayer3, btnPwKickPlayer4;
    private TextView tvRoomInfo;
    private TextView tvWatchCount;
    private View layoutTagPlayers;
    private int selfPos = 0;
    private boolean isSelfReady = false;
    private boolean isHost = false;
    private boolean isTagMode = false;
    private int watchCount = 0;
    private final List<String> observerNames = new ArrayList<>();

    public interface OnPlayerWaitingListener {
        void onPlayerWaitingReady();

        void onPlayerWaitingNotReady();

        void onPlayerWaitingToDuelist();

        void onPlayerWaitingToObserver();

        /** 点击“退出”等待界面：外部执行断线并返回局域网主界面 */
        void onExitWaiting();

        void onPlayerWaitingDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side);

        void onStartGameRequested();

        void onKickPlayerRequested(int pos);

        /** player waiting 界面已显示：Activity 切换到大厅聊天显示 */
        void onPlayerWaitingShown();
    }

    private final OnPlayerWaitingListener listener;

    private final PopupWindow.OnDismissListener internalDismissListener = () -> {
        if (suppressDismiss) return;
        if (externalDismissListener != null) externalDismissListener.onDismiss();
    };

    public PlayerWaitingDialog(Context context, OnPlayerWaitingListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        this.externalDismissListener = listener;
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(internalDismissListener);
        }
    }

    public void show(View anchorView) {
        View customView = LayoutInflater.from(context).inflate(R.layout.popup_window_player_waiting, null);

        initPlayerWaitingViews(customView);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(internalDismissListener);

        customView.setFocusableInTouchMode(true);
        customView.requestFocus();

        draggableHelper = new DraggablePopupHelper(context, "player_waiting_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight);

        loadLastDeckInfo();

        deckSelectorDialog = new DeckSelectorDialog(context);
        deckSelectorDialog.setDisableOperationButtons(true);
        deckSelectorDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(String deckPath, String deckName, String categoryName) {
                currentDeckPath = deckPath;
                currentDeckName = deckName;
                currentDeckCategory = categoryName;
                AppsSettings.get().setLastDeckPath(deckPath);
                updateDeckButtonText();
                // 选择卡组仅更新本地状态，不再自动发卡/进入准备流程；
                // 发卡（准备第一步）推迟到点击"准备"(btnPwReady) 或勾选自选框时由 sendDeckIfLoaded() 统一触发

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

        btnPwExitWaiting.setOnClickListener(v -> {
            if (listener != null) listener.onExitWaiting();
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
                String selfName;
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
            for (CheckBox checkbox : checkboxes) {
                if (checkbox != null) {
                    checkbox.setEnabled(false);
                    checkbox.setClickable(false);
                    checkbox.setOnCheckedChangeListener(null);
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

        resetRoomInfo();
        resetPlayerWaitingState();
        setupKickButtons();
        setupStartButton();

        draggableHelper.showPopup(popupWindow, anchorView);

        if (listener != null) listener.onPlayerWaitingShown();
    }

    private void initPlayerWaitingViews(View root) {
        etPwPlayer1Name = root.findViewById(R.id.et_player1_name);
        etPwPlayer2Name = root.findViewById(R.id.et_player2_name);
        etPwPlayer3Name = root.findViewById(R.id.et_player3_name);
        etPwPlayer4Name = root.findViewById(R.id.et_player4_name);
        chkPwPlayer1Ready = root.findViewById(R.id.chk_player1_ready);
        chkPwPlayer2Ready = root.findViewById(R.id.chk_player2_ready);
        chkPwPlayer3Ready = root.findViewById(R.id.chk_player3_ready);
        chkPwPlayer4Ready = root.findViewById(R.id.chk_player4_ready);
        btnPwDuelistMode = root.findViewById(R.id.btn_duelist_mode);
        btnPwSpectatorMode = root.findViewById(R.id.btn_spectator_mode);
        btnPwReady = root.findViewById(R.id.btn_ready);
        btnPwDeckSelect = root.findViewById(R.id.btn_deck_select);
        btnPwExitWaiting = root.findViewById(R.id.btn_exit_waiting);
        btnPwStartGame = root.findViewById(R.id.btn_start_game);
        btnPwKickPlayer1 = root.findViewById(R.id.btn_kick_player1);
        btnPwKickPlayer2 = root.findViewById(R.id.btn_kick_player2);
        btnPwKickPlayer3 = root.findViewById(R.id.btn_kick_player3);
        btnPwKickPlayer4 = root.findViewById(R.id.btn_kick_player4);
        tvRoomInfo = root.findViewById(R.id.tv_room_info);
        tvWatchCount = root.findViewById(R.id.tv_watch_count);
        layoutTagPlayers = root.findViewById(R.id.layout_tag_players);

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

    public void setTagPlayersVisible(boolean visible) {
        if (layoutTagPlayers != null) {
            layoutTagPlayers.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
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
            tvWatchCount.setText(mStringManager.getSystemString(1253, "当前观战人数: ") + watchCount);
            if (watchCount > 0 || selfPos >= 4) {
                tvWatchCount.setVisibility(View.VISIBLE);
            } else {
                tvWatchCount.setVisibility(View.INVISIBLE);
            }
        }
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
        updateDeckSelectButtonState();
        selfPos = 0;
        if (btnPwReady != null) {
            btnPwReady.setEnabled(true);
            btnPwReady.setText(mStringManager.getSystemString(1218, "点击准备"));
            btnPwReady.setPressed(false);
        }
        if (btnPwSpectatorMode != null) btnPwSpectatorMode.setEnabled(true);

        if (btnPwStartGame != null) btnPwStartGame.setVisibility(View.INVISIBLE);
        if (layoutTagPlayers != null) layoutTagPlayers.setVisibility(View.INVISIBLE);

        watchCount = 0;
        observerNames.clear();
        if (tvWatchCount != null) tvWatchCount.setVisibility(View.INVISIBLE);
        if (tvWatchCount != null)
            tvWatchCount.setText(mStringManager.getSystemString(1253, "当前观战人数: ") + 0);

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
                    btnPwReady.setText(ready ? mStringManager.getSystemString(1219, "取消准备")
                            : mStringManager.getSystemString(1218, "点击准备"));
                    btnPwReady.setPressed(ready);
                    btnPwReady.setBackground(ready ? context.getDrawable(R.drawable.sbutton_p) : context.getDrawable(R.drawable.sbutton));
                }
                updateDeckSelectButtonState();
            }
            checkboxes[pos].setChecked(ready);
        }
        updateStartButtonState();
    }

    public void clearPlayerPos(int pos) {
        setPlayerName(pos, "");
        setPlayerReady(pos, false);
    }

    public void movePlayer(int fromPos, int toPos) {
        String name;
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
            default:
                name = "";
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
                btnPwReady.setText(isSelfReady ? mStringManager.getSystemString(1219, "取消准备")
                        : mStringManager.getSystemString(1218, "点击准备"));
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
            tvWatchCount.setText(mStringManager.getSystemString(1253, "当前观战人数: ") + watchCount);
            if (watchCount > 0 || selfPos >= 4) {
                tvWatchCount.setVisibility(View.VISIBLE);
            } else {
                tvWatchCount.setVisibility(View.INVISIBLE);
            }
        }

        updateSelfCheckboxInteractivity();
        updateStartButtonState();
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

    public void updateRoomInfo(int lflist, int rule, int mode, int duelRule,
                               int noCheckDeck, int noShuffleDeck,
                               int startLp, int startHand, int drawCount, int timeLimit) {
        StringBuilder sb = new StringBuilder();
        sb.append(mStringManager.getSystemString(1226, "禁限卡表：")).append(getBanlistName(lflist)).append("\n");
        sb.append(mStringManager.getSystemString(1225, "卡片允许：")).append(getCardAllowedName(rule)).append("\n");

        String duelModeText;
        switch (mode) {
            case 0:
                duelModeText = mStringManager.getSystemString(1244, "单局模式");
                break;
            case 1:
                duelModeText = mStringManager.getSystemString(1245, "比赛模式");
                break;
            case 2:
                duelModeText = mStringManager.getSystemString(1246, "TAG");
                break;
            default:
                duelModeText = "unknown mode";
        }
        sb.append(mStringManager.getSystemString(1227, "决斗模式：")).append(duelModeText).append("\n");

        if (timeLimit > 0) {
            sb.append(mStringManager.getSystemString(1237, "回合时间：")).append(timeLimit).append("\n");
        }

        sb.append("==========\n");
        sb.append(mStringManager.getSystemString(1231, "初始基本分：")).append(startLp).append("\n");
        sb.append(mStringManager.getSystemString(1232, "初始手卡数：")).append(startHand).append("\n");
        sb.append(mStringManager.getSystemString(1233, "每回合抽卡：")).append(drawCount).append("\n");

        if (duelRule != 5) {
            sb.append("*").append(getDuelRuleName(duelRule)).append("\n");
        }
        if (noCheckDeck != 0) {
            sb.append("*").append(mStringManager.getSystemString(1229, "不检查卡组")).append("\n");
        }
        if (noShuffleDeck != 0) {
            sb.append("*").append(mStringManager.getSystemString(1230, "不洗切卡组")).append("\n");
        }

        if (tvRoomInfo != null) tvRoomInfo.setText(sb.toString());

        isTagMode = (mode == 2);

        if (layoutTagPlayers != null) {
            layoutTagPlayers.setVisibility(mode == 2 ? View.VISIBLE : View.INVISIBLE);
        }
        updateStartButtonState();
    }

    private void resetRoomInfo() {
        if (tvRoomInfo != null) {
            String dash = "----";
            String info = mStringManager.getSystemString(1226, "禁限卡表：") + dash + "\n"
                    + mStringManager.getSystemString(1225, "卡片允许：") + dash + "\n"
                    + mStringManager.getSystemString(1227, "决斗模式：") + dash + "\n"
                    + mStringManager.getSystemString(1237, "回合时间：") + dash + "\n"
                    + "==========\n"
                    + mStringManager.getSystemString(1231, "初始基本分：") + dash + "\n"
                    + mStringManager.getSystemString(1232, "初始手卡数：") + dash + "\n"
                    + mStringManager.getSystemString(1233, "每回合抽卡：") + dash;
            tvRoomInfo.setText(info);
        }
    }

    private String getDuelRuleName(int duelRule) {
        switch (duelRule) {
            case 1:
                return mStringManager.getSystemString(1260, "大师规则");
            case 2:
                return mStringManager.getSystemString(1261, "大师规则２");
            case 3:
                return mStringManager.getSystemString(1262, "大师规则３");
            case 4:
                return mStringManager.getSystemString(1263, "新大师规则（2017）");
            case 5:
                return mStringManager.getSystemString(1264, "大师规则(2020)");
            default:
                return mStringManager.getSystemString(1264, "大师规则(2020)");
        }
    }

    private String getBanlistName(int lflist) {
        if (lflist == 0) return "N/A";
        LimitManager limitManager = DataManager.get().getLimitManager();
        boolean isGenesysMode = AppsSettings.get().getGenesysMode() == 1;
        String name = isGenesysMode
                ? limitManager.getGenesysLimitNameByHash(lflist)
                : limitManager.getLimitNameByHash(lflist);
        return name != null ? name : "N/A";
    }

    private String getCardAllowedName(int rule) {
        switch (rule) {
            case 1:
                return mStringManager.getSystemString(1487, "ＯＣＧ独有");
            case 2:
                return mStringManager.getSystemString(1488, "ＴＣＧ独有");
            case 0:
            default:
                return mStringManager.getSystemString(1486, "所有卡片");
        }
    }

    public void updateTypeChange(int selfType, boolean isTag, boolean isHost) {
        selfPos = selfType;
        this.isHost = isHost;
        this.isTagMode = isTag;
        updateSelfCheckboxInteractivity();

        if (selfType < 4) {
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
        updateStartButtonState();

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

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private void loadLastDeckInfo() {
        AppsSettings settings = AppsSettings.get();
        currentDeckCategory = settings.getLastCategory();
        currentDeckName = settings.getLastDeckName();
        currentDeckPath = settings.getLastDeckPath();
        updateDeckButtonText();
    }

    private void updateDeckButtonText() {
        if (btnPwDeckSelect == null) return;
        if (currentDeckName != null && !currentDeckName.isEmpty()) {
            String uncatName = context.getString(R.string.category_Uncategorized);
            if (currentDeckCategory != null && !currentDeckCategory.isEmpty()
                    && !currentDeckCategory.equals(uncatName)) {
                btnPwDeckSelect.setText("[" + currentDeckCategory + "]" + currentDeckName);
            } else {
                btnPwDeckSelect.setText(currentDeckName);
            }
        } else {
            btnPwDeckSelect.setText(mStringManager.getSystemString(1707, "请选择卡组"));
        }
    }

    private void updateDeckSelectButtonState() {
        if (btnPwDeckSelect != null) {
            btnPwDeckSelect.setEnabled(!isSelfReady);
            btnPwDeckSelect.setTextColor(isSelfReady ? Color.GRAY : Color.WHITE);
        }
    }

    private void setupSelfReadyInteraction() {
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        for (CheckBox checkbox : checkboxes) {
            if (checkbox == null) continue;
            checkbox.setEnabled(false);
            checkbox.setClickable(false);
        }

        if (btnPwReady != null) {
            btnPwReady.setOnClickListener(v -> {
                if (!isSelfReady) {
                    if (!sendDeckIfLoaded()) {
                        Toast.makeText(context, mStringManager.getSystemString(1406, "无效卡组。"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    isSelfReady = true;
                    btnPwReady.setText(mStringManager.getSystemString(1219, "取消准备"));
                    btnPwReady.setPressed(true);
                    updateDeckSelectButtonState();
                    if (listener != null) listener.onPlayerWaitingReady();
                } else {
                    isSelfReady = false;
                    btnPwReady.setText(mStringManager.getSystemString(1218, "点击准备"));
                    btnPwReady.setPressed(false);
                    updateDeckSelectButtonState();
                    if (listener != null) listener.onPlayerWaitingNotReady();
                }
            });
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
                        if (!sendDeckIfLoaded()) {
                            Toast.makeText(context, mStringManager.getSystemString(1406, "无效卡组。"), Toast.LENGTH_SHORT).show();
                            buttonView.setChecked(false);
                            isSelfReady = false;
                            btnPwReady.setText(mStringManager.getSystemString(1218, "点击准备"));
                            btnPwReady.setPressed(false);
                            return;
                        }
                        isSelfReady = true;
                        btnPwReady.setText(mStringManager.getSystemString(1219, "取消准备"));
                        btnPwReady.setPressed(true);
                        if (listener != null) listener.onPlayerWaitingReady();
                    } else {
                        isSelfReady = false;
                        btnPwReady.setText(mStringManager.getSystemString(1218, "点击准备"));
                        btnPwReady.setPressed(false);
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

    private boolean sendDeckIfLoaded() {
        if (currentDeckPath == null || currentDeckPath.isEmpty()) return false;
        File ydkFile = new File(currentDeckPath);
        if (!ydkFile.exists()) return false;

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
                        case 1:
                            main.add(code);
                            break;
                        case 2:
                            extra.add(code);
                            break;
                        case 3:
                            side.add(code);
                            break;
                    }
                } catch (NumberFormatException e) { /* skip */ }
            }
        } catch (Exception e) {
            Toast.makeText(context, mStringManager.getSystemString(1406, "无效卡组"), Toast.LENGTH_SHORT).show();
            return false;
        }

        if (main.isEmpty()) return false;

        if (listener != null) {
            listener.onPlayerWaitingDeckUpdate(main, extra, side);
        }
        return true;
    }

    private void setupKickButtons() {
        if (btnPwKickPlayer1 != null) btnPwKickPlayer1.setOnClickListener(v -> sendKickPacket(0));
        if (btnPwKickPlayer2 != null) btnPwKickPlayer2.setOnClickListener(v -> sendKickPacket(1));
        if (btnPwKickPlayer3 != null) btnPwKickPlayer3.setOnClickListener(v -> sendKickPacket(2));
        if (btnPwKickPlayer4 != null) btnPwKickPlayer4.setOnClickListener(v -> sendKickPacket(3));
    }

    private void sendKickPacket(int pos) {
        if (!isHost) return;
        String playerName = getPlayerName(pos);
        if (playerName.isEmpty()) return;
        if (pos == selfPos) return;
        if (listener != null) listener.onKickPlayerRequested(pos);
    }

    private void setupStartButton() {
        if (btnPwStartGame != null) {
            btnPwStartGame.setOnClickListener(v -> {
                if (listener != null) listener.onStartGameRequested();
            });
            btnPwStartGame.setEnabled(false);
            btnPwStartGame.setTextColor(Color.GRAY);
        }
    }

    private void updateStartButtonState() {
        if (btnPwStartGame == null) return;
        if (!isHost) {
            btnPwStartGame.setEnabled(false);
            btnPwStartGame.setTextColor(Color.GRAY);
            return;
        }
        int duelistCount = isTagMode ? 4 : 2;
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        for (int i = 0; i < duelistCount; i++) {
            if (getPlayerName(i).isEmpty()) {
                btnPwStartGame.setEnabled(false);
                btnPwStartGame.setTextColor(Color.GRAY);
                return;
            }
            if (checkboxes[i] == null || !checkboxes[i].isChecked()) {
                btnPwStartGame.setEnabled(false);
                btnPwStartGame.setTextColor(Color.GRAY);
                return;
            }
        }
        btnPwStartGame.setEnabled(true);
        btnPwStartGame.setTextColor(Color.WHITE);
    }

    // === 引擎事件处理（由 YGOProActivity 转发） ===

    public void handlePlayerEnter(String name, int pos) {
        if (!isShowing()) return;
        removeObserver(name);
        setPlayerName(pos, name);
        refreshPlayerDisplay();
    }

    public void handlePlayerChange(int status) {
        if (!isShowing()) return;
        int pos = (status >> 4) & 0x0F;
        int state = status & 0x0F;

        if (state < 8) {
            String oldName = getPlayerName(pos);
            movePlayer(pos, state);
            if (!oldName.isEmpty() && state >= 4) {
                addObserver(oldName);
            }
            if (pos >= 4 && !oldName.isEmpty() && state < 4) {
                removeObserver(oldName);
            }
        } else if (state == 0x8) {
            String observerName = getPlayerName(pos);
            clearPlayerPos(pos);
            if (!observerName.isEmpty()) {
                addObserver(observerName);
            }
        } else if (state == 0x9) {
            setPlayerReady(pos, true);
        } else if (state == 0xa) {
            setPlayerReady(pos, false);
        } else if (state == 0xb) {
            String leavingName = getPlayerName(pos);
            clearPlayerPos(pos);
            if (!leavingName.isEmpty()) {
                removeObserver(leavingName);
            }
        }
        refreshPlayerDisplay();
    }

    public void handleWatchChange(int watchCount) {
        if (!isShowing()) return;
        updateWatchCount(watchCount);
        refreshPlayerDisplay();
    }

    public void handleJoinGame(int lflist, int rule, int mode, int duelRule, int noCheckDeck, int noShuffleDeck, int startLp, int startHand, int drawCount, int timeLimit) {
        if (!isShowing()) return;
        updateRoomInfo(lflist, rule, mode, duelRule, noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, timeLimit);
    }

    public void handleTypeChange(int type, boolean isTag) {
        if (!isShowing()) return;
        int selfType = type & 0x0F;
        boolean isHost = ((type >> 4) & 0x0F) != 0;
        updateTypeChange(selfType, isTag, isHost);
    }

    public void handleDeckError(int errorType, int cardCode) {
        String errorDesc;
        switch (errorType) {
            case YGOProtocol.DECKERROR_LFLIST:
                errorDesc = mStringManager.getSystemString(1407, "「%ls」的数量不符合当前禁限卡表设定。")
                        .replace("%ls", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_OCGONLY:
                errorDesc = mStringManager.getSystemString(1413, "「%ls」为OCG独有卡，不允许在当前设定下使用。")
                        .replace("%ls", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_TCGONLY:
                errorDesc = mStringManager.getSystemString(1414, "「%ls」为TCG独有卡，不允许在当前设定下使用。")
                        .replace("%ls", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_UNKNOWNCARD:
                errorDesc = mStringManager.getSystemString(1415, "卡组中「%ls(%d)」尚不支持在本主机使用")
                        .replace("%ls", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_CARDCOUNT:
                errorDesc = mStringManager.getSystemString(1416, "卡组中「%ls」的总数量超过3张。")
                        .replace("%ls", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_MAINCOUNT:
                errorDesc = mStringManager.getSystemString(1417, "主卡组数量应为40-60张，当前卡组数量为%d张。")
                        .replace("%d", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_EXTRACOUNT:
                errorDesc = mStringManager.getSystemString(1418, "额外卡组数量超限(%ls张)")
                        .replace("%ls", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_SIDECOUNT:
                errorDesc = mStringManager.getSystemString(1419, "副卡组数量应不超过15张，当前卡组数量为%d张。")
                        .replace("%d", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_NOTAVAIL:
                errorDesc = mStringManager.getSystemString(1420, "有额外卡组卡片存在于主卡组，可能是额外卡组数量超过15张。");
                break;
            default:
                errorDesc = mStringManager.getSystemString(1421, "未知卡组错误(type=%ls)");
                break;
        }

        String cardName = "";
        if (cardCode > 0 && errorType != YGOProtocol.DECKERROR_MAINCOUNT
                && errorType != YGOProtocol.DECKERROR_EXTRACOUNT
                && errorType != YGOProtocol.DECKERROR_SIDECOUNT) {
            cardName = cardNameResolver != null ? cardNameResolver.resolve(cardCode) : "";
        }

        String title = mStringManager.getSystemString(1725, "卡组验证失败");
        String message = errorDesc;
        if (!cardName.isEmpty()) {
            String cardTemplate = mStringManager.getSystemString(1726, "卡片: %ls(%ls)");
            message += "\n" + cardTemplate
                    .replaceFirst("%ls", Matcher.quoteReplacement(cardName))
                    .replaceFirst("%ls", String.valueOf(cardCode));
        }

        YesOrNoDialog dialog = new YesOrNoDialog(context);
        dialog.setTitle(title).setMessage(message);
        dialog.show();
    }

    public interface CardNameResolver {
        String resolve(int cardCode);
    }

    private CardNameResolver cardNameResolver;

    public void setCardNameResolver(CardNameResolver resolver) {
        this.cardNameResolver = resolver;
    }

    /** 内部跳转（决斗开始/返回主界面）：抑制外部 dismiss 回调后关闭 */
    public void hideForNavigation() {
        if (popupWindow == null) return;
        suppressDismiss = true;
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        suppressDismiss = false;
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        if (deckSelectorDialog != null) {
            deckSelectorDialog.dismiss();
        }
    }

    // === 静态入口：由 YGOProActivity 调用（直连/人机直接进入玩家等待界面） ===

    public static void showPlayerWaitingForDirectJoin(YGOProActivity activity, YGOGameOptions options) {
        activity.hideGameUI();
        String name = Constants.PlayerName;
        if (options != null && options.mUserName != null && !options.mUserName.isEmpty()) {
            name = options.mUserName;
        }
        final String playerName = name;
        View anchor = activity.getDialogContainer();
        anchor.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            PlayerWaitingDialog dialog = new PlayerWaitingDialog(activity, activity.getPlayerWaitingListener());
            activity.setPlayerWaitingDialog(dialog);
            dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
            dialog.show(anchor);
            dialog.setPlayerName(0, playerName);
            activity.getSoundManager().playBGM(SoundManager.BGM.DUEL);
        });
    }

    public static void showPlayerWaitingForBotHost(YGOProActivity activity) {
        activity.hideGameUI();
        final String playerName = Constants.PlayerName;
        View anchor = activity.getDialogContainer();
        anchor.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            PlayerWaitingDialog dialog = new PlayerWaitingDialog(activity, activity.getPlayerWaitingListener());
            activity.setPlayerWaitingDialog(dialog);
            dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
            dialog.show(anchor);
            dialog.setPlayerName(0, playerName);
            activity.getSoundManager().playBGM(SoundManager.BGM.DUEL);
        });
    }
}
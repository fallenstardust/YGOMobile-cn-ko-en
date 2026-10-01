package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
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

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.DialogScale;
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

    /** 拖拽布局持久化 id：本弹窗是唯一“退出即释放”的对话框，退出时需清除此 key 的位置缓存。 */
    private static final String DIALOG_ID = "player_waiting_dialog";

    private final Context context;
    private PopupWindow popupWindow;
    private boolean suppressDismiss = false;
    private PopupWindow.OnDismissListener externalDismissListener;
    private DraggablePopupHelper draggableHelper;
    private DeckSelectorDialog deckSelectorDialog;

    private String currentDeckCategory = "";
    private String currentDeckName = "";
    private String currentDeckPath = "";

    // Solo mode (LAN 纯单人模式): 房主自己为双方分别选卡，server 依次写入 slot0 与 slot1
    // deckPickTarget: 0 = 正在为“我方”选卡 (写入 currentDeckPath), 1/2/3 = 为其他席位选卡 (写入 soloSeatDeckPaths[1..3])
    // Solo TAG 建房时四席位都需要房主代选，slot1/2/3 各自对应 etPwPlayer2/3/4Name 点击选卡组；
    // 非 TAG solo 只用到 slot1。
    private boolean soloMode = false;
    private int deckPickTarget = 0;
    /** slot 1..3 席位卡组路径（solo 模式专用）；index 0 保留未用 */
    private final String[] soloSeatDeckPaths = new String[]{"", "", "", ""};
    /** slot 1..3 席位卡组名（solo 模式专用），选好后作为玩家名展示在席位与 GameTopInfo 上 */
    private final String[] soloSeatDeckNames = new String[]{"", "", "", ""};

    private TextView etPwPlayer1Name, etPwPlayer2Name, etPwPlayer3Name, etPwPlayer4Name;
    private CheckBox chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready;
    private Button btnPwDuelistMode, btnPwSpectatorMode, btnPwReady, btnPwDeckSelect, btnPwExitWaiting;
    private Button btnPwStartGame;
    private ImageButton btnPwKickPlayer1, btnPwKickPlayer2, btnPwKickPlayer3, btnPwKickPlayer4;
    private TextView tvRoomInfo;
    private TextView tvWatchCount;
    private TextView tvDeckSelectLabel;
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

        /** Solo 模式下某个席位（slot 0..3）已选定卡组：
         *  seat 为席位下标，deckName 需同步回填到 GameTopInfo 展示；
         *  deckPath 为该席位卡组文件路径，match 换 side 时据此载入对应卡组供编辑 */
        void onSoloSeatDeckUpdated(int seat, String deckName, String deckPath);
    }

    private final OnPlayerWaitingListener listener;

    private final PopupWindow.OnDismissListener internalDismissListener = () -> {
        if (suppressDismiss) {
            suppressDismiss = false;
            return;
        }
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
        View customView = LayoutInflater.from(DialogScale.wrap(context)).inflate(R.layout.popup_window_player_waiting, null);

        initPlayerWaitingViews(customView);

        float density = context.getResources().getDisplayMetrics().density * DialogScale.factor(context);
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        // 传设计尺寸给 setupDraggablePopup：由其按当前屏宽统一限宽，并在屏幕旋转后按新屏宽重新解算
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 等待界面自身无输入控件：改为非获焦弹窗（同 MainMenuDialog），把窗口焦点留给 Activity，
        // 否则大厅聊天输入框 et_chat_input 无法获得窗口焦点弹起输入法；
        // outsideTouchable 同步关闭，避免点外部区域误 dismiss 弹窗触发回主菜单
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(internalDismissListener);

        // 全屏弹窗窗口不得作为 touch-modal：否则等待界面会像一层遮罩截走落在全屏窗口
        // 范围内的系统窗口（软键盘）交互，导致输入法弹出后无法点击按键；
        // 置为非模态后输入法窗口始终位于本弹窗之上一层且可正常响应触摸
        popupWindow.setTouchModal(false);

        draggableHelper = new DraggablePopupHelper(context, DIALOG_ID);
        // 竖屏宽铺满屏宽且高与宽相等（正方形），转回横屏恢复设计宽高比（用户规格）
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight, true);

        loadLastDeckInfo();

        deckSelectorDialog = new DeckSelectorDialog(context);
        deckSelectorDialog.setDisableOperationButtons(true);
        deckSelectorDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(String deckPath, String deckName, String categoryName) {
                if (deckPickTarget >= 1) {
                    // Solo mode: 当前正在为席位 1/2/3 选卡，写入 soloSeatDeckPaths/Names；
                    // slot1 同步旧字段以保持旧有 sendDeckIfLoaded 逻辑兼容；
                    // 不污染本地默认卡组与 AppsSettings.lastDeckPath
                    int seat = deckPickTarget;
                    soloSeatDeckPaths[seat] = deckPath;
                    soloSeatDeckNames[seat] = deckName;
                    // 将卡组名作为玩家名展示在席位上（代替默认玩家名，对齐需求描述）
                    setPlayerName(seat, deckName);
                    // 席位已选定卡组 → 其后的准备勾选框自动打勾（solo 席位“准备”=已选卡组）；
                    // 重估开始按钮：所有所需席位选齐即可开始
                    setSoloSeatChecked(seat, true);
                    updateStartButtonState();
                    if (listener != null) listener.onSoloSeatDeckUpdated(seat, deckName, deckPath);
                    return;
                }
                currentDeckPath = deckPath;
                currentDeckName = deckName;
                currentDeckCategory = categoryName;
                AppsSettings.get().setLastDeckPath(deckPath);
                updateDeckButtonText();
                // Solo 下我方卡组名同步射向 engine.soloSeatDeckNames[0]，enterDuelingUI 时同对方选完的
                // slot1 一并写回 GameTopInfo（非 solo 不写，不影响协议回显昵称）
                if (soloMode) {
                    soloSeatDeckNames[0] = deckName;
                    soloSeatDeckPaths[0] = deckPath;
                    updateStartButtonState();
                    if (listener != null) listener.onSoloSeatDeckUpdated(0, deckName, deckPath);
                }
                // 选择卡组仅更新本地状态，不再自动发卡/进入准备流程；
                // 发卡（准备第一步）推迟到点击"准备"(btnPwReady) 或勾选自选框时由 sendDeckIfLoaded() 统一触发

            }

            @Override
            public void onCancelled() {
            }
        });

        // 再次点击"选择卡组"按钮收起已展开的卡组选择窗（切换式交互）
        btnPwDeckSelect.setOnClickListener(v -> {
            if (deckSelectorDialog == null) return;
            if (deckSelectorDialog.isShowing()) {
                deckSelectorDialog.dismiss();
            } else {
                deckPickTarget = 0;
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
        tvDeckSelectLabel = root.findViewById(R.id.tv_deck_select_label);
        layoutTagPlayers = root.findViewById(R.id.layout_tag_players);

        // 界面文字统一复用 gframe 既有系统字符串（1251 →决斗者、1252 →观战、
        // 1215 开始、1210 退出、1254 卡组选择），XML 中的中文仅作兜底
        if (btnPwDuelistMode != null)
            btnPwDuelistMode.setText(mStringManager.getSystemString(1251, "→决斗者"));
        if (btnPwSpectatorMode != null)
            btnPwSpectatorMode.setText(mStringManager.getSystemString(1252, "→观战"));
        if (btnPwStartGame != null)
            btnPwStartGame.setText(mStringManager.getSystemString(1215, "开始"));
        if (btnPwExitWaiting != null)
            btnPwExitWaiting.setText(mStringManager.getSystemString(1210, "退出"));
        if (tvDeckSelectLabel != null)
            tvDeckSelectLabel.setText(mStringManager.getSystemString(1254, "卡组选择："));

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

    /** 席位有人加入时的半透明背景色
     * （对齐 gframe duelclient.cpp STOC_HS_PLAYER_ENTER L1175 /
     *  STOC_HS_PLAYER_CHANGE L1198：stHostPrepDuelist[pos]->setBackgroundColor(0x60045f6a)） */
    private static final int SEAT_OCCUPIED_COLOR = 0x60045F6A;

    public void setPlayerName(int pos, String name) {
        TextView seat = null;
        switch (pos) {
            case 0:
                seat = etPwPlayer1Name;
                break;
            case 1:
                seat = etPwPlayer2Name;
                break;
            case 2:
                seat = etPwPlayer3Name;
                break;
            case 3:
                seat = etPwPlayer4Name;
                break;
        }
        if (seat == null) return;
        seat.setText(name);
        // 玩家加入(名字非空)时叠加半透明色提醒该席位已被占据，离开/清空时恢复透明
        seat.setBackgroundColor(TextUtils.isEmpty(name) ? Color.TRANSPARENT : SEAT_OCCUPIED_COLOR);
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

    /** 卡片允许：协议 HostInfo.rule 0..5，对齐 game.cpp cbRule 六项（1481-1486） */
    private String getCardAllowedName(int rule) {
        switch (rule) {
            case 0:
                return mStringManager.getSystemString(1481, "ＯＣＧ");
            case 1:
                return mStringManager.getSystemString(1482, "ＴＣＧ");
            case 2:
                return mStringManager.getSystemString(1483, "简体中文");
            case 3:
                return mStringManager.getSystemString(1484, "自定义卡片");
            case 4:
                return mStringManager.getSystemString(1485, "无独有卡");
            case 5:
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

        // 进入等待界面时，若最后选择的分类是"卡包展示"（其卡组不可用于对战），
        // 默认改为选中"未分类卡组"的第一个卡组，并保存为最后选择的分类与最后选择的卡组。
        // 此逻辑仅在 PlayerWaitingDialog 生效，不影响其他入口的卡组选择。
        if (TextUtils.equals(context.getString(R.string.category_pack), currentDeckCategory)) {
            List<DeckFile> uncatDecks = DeckUtil.getDeckList(settings.getDeckDir());
            if (uncatDecks != null && !uncatDecks.isEmpty()) {
                DeckFile first = uncatDecks.get(0);
                currentDeckPath = first.getPath();
                currentDeckName = first.getName();
                currentDeckCategory = first.getTypeName();
                // setLastDeckPath 依据卡组路径重新写入最后分类与最后卡组名，
                // 使随后展开的 DeckSelectorDialog 默认选中该未分类卡组
                settings.setLastDeckPath(currentDeckPath);
            } else {
                // 未分类下无卡组：清空选择，等待界面回退到"请选择卡组"
                currentDeckPath = "";
                currentDeckName = "";
                currentDeckCategory = "";
            }
        }

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
                // 修复：主机自身没有 STOC_PLAYER_CHANGE 回显回路，点准备按钮后必须静默同步
                // 自身 checkbox，否则 updateStartButtonState 判定主机位永远未勾选，
                // 开始按钮永远无法恢复可用（远端玩家经 handlePlayerChange 回显无此问题）
                syncSelfCheckboxAndStartState(checkboxes);
            });
        }
    }

    /** 静默同步自身 ready checkbox 并重新评估开始按钮可用状态（不触发重发卡组的监听回调） */
    private void syncSelfCheckboxAndStartState(CheckBox[] checkboxes) {
        if (selfPos < 0 || selfPos >= checkboxes.length) return;
        CheckBox self = checkboxes[selfPos];
        if (self == null) return;
        if (self.isChecked() != isSelfReady) {
            self.setOnCheckedChangeListener(null);
            self.setChecked(isSelfReady);
            // 重挂原监听（updateSelfCheckboxInteractivity 内按 selfPos 重新绑定）
            updateSelfCheckboxInteractivity();
        }
        updateStartButtonState();
    }

    /**
     * 自身位置/状态变更时更新 checkbox 交互态与就绪指示。
     *
     * 关键修正：对房主而言，点击准备 (isChecked == true) 时应立即上传卡组到通讯
     * (sendDeckIfLoaded())，而非延迟到后续某次 STOC_PLAYER_CHANGE 回显时才触发。
     * 否则当房主在其他玩家进入前就勾选准备，卡组永远不会被同步，导致所有玩家准备好后
     * 开始按钮仍无法点击。
     */
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
                        // 房主随时准备都要立即上传卡组
                        if (!sendDeckIfLoaded()) {
                            Toast.makeText(context, mStringManager.getSystemString(1406, "无效卡组。"), Toast.LENGTH_SHORT).show();
                            buttonView.setChecked(false);
                            isSelfReady = false;
                            btnPwReady.setText(mStringManager.getSystemString(1218, "点击准备"));
                            btnPwReady.setPressed(false);
                            updateDeckSelectButtonState();
                            return;
                        }
                        // 发送卡组后立即同步自身状态（避免后续 STOC_PLAYER_CHANGE 回显丢失）
                        isSelfReady = true;
                        btnPwReady.setText(mStringManager.getSystemString(1219, "取消准备"));
                        btnPwReady.setPressed(true);
        

                        if (listener != null) listener.onPlayerWaitingReady();
                    } else {
                        isSelfReady = false;
                        btnPwReady.setText(mStringManager.getSystemString(1218, "点击准备"));
                        btnPwReady.setPressed(false);
                        updateDeckSelectButtonState();
                        if (listener != null) listener.onPlayerWaitingNotReady();
                    }
                    // 直点自身 checkbox 是与 btnPwReady 平行的独立准备入口：
                    // 主机无 STOC_PLAYER_CHANGE 回环，必须在此重估开始按钮可用状态，
                    // 否则最后一名玩家（主机）经勾选框准备后开始按钮永远停留灰色
                    updateStartButtonState();
                });
            } else {
                checkboxes[i].setEnabled(false);
                checkboxes[i].setClickable(false);
                checkboxes[i].setOnCheckedChangeListener(null);
            }
        }
    }

    /**
     * 非 solo：点击准备即上传房主自身卡组（单一 currentDeckPath）。
     * solo：卡组在点击“开始”时由 {@link #soloCommitDecksForStart()} 按席位顺序统一上传，
     * 此处不发卡（避免“先上传席位0、其余席位未选→return false”造成的服务端部分上传脏状态），
     * 准备仅作为本地标记，返回 true。
     */
    private boolean sendDeckIfLoaded() {
        if (soloMode) return true;
        List<List<Integer>> sec = parseDeckSections(currentDeckPath);
        if (sec == null) {
            Toast.makeText(context, mStringManager.getSystemString(1406, "无效卡组"), Toast.LENGTH_SHORT).show();
            return false;
        }
        if (listener != null) listener.onPlayerWaitingDeckUpdate(sec.get(0), sec.get(1), sec.get(2));
        return true;
    }

    /**
     * 解析 ydk 卡组文件为 [主卡组, 额外卡组, 副卡组] 三列表；路径为空/文件不存在/主卡组为空
     * 时返回 null。供准备上传与 solo 开始时统一上传共用。
     */
    private List<List<Integer>> parseDeckSections(String path) {
        if (path == null || path.isEmpty()) return null;
        File ydkFile = new File(path);
        if (!ydkFile.exists()) return null;
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(ydkFile))) {
            String line;
            int section = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("#main")) { section = 1; continue; }
                if (line.equalsIgnoreCase("#extra")) { section = 2; continue; }
                if (line.equalsIgnoreCase("!side")) { section = 3; continue; }
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
            return null;
        }
        if (main.isEmpty()) return null;
        List<List<Integer>> out = new ArrayList<>(3);
        out.add(main);
        out.add(extra);
        out.add(side);
        return out;
    }

    /** solo：席位 seat（0..required-1）是否已选定卡组。 */
    private boolean soloSeatHasDeck(int seat) {
        return seat >= 0 && seat < soloSeatDeckPaths.length
                && soloSeatDeckPaths[seat] != null && !soloSeatDeckPaths[seat].isEmpty();
    }

    /** solo：开局所需的全部席位（0..requiredSoloSeats()-1）是否都已选定卡组。 */
    private boolean soloAllSeatsHaveDeck() {
        int required = requiredSoloSeats();
        for (int seat = 0; seat < required; seat++) {
            if (!soloSeatHasDeck(seat)) return false;
        }
        return true;
    }

    /**
     * solo：点击开始时按席位顺序 0..required-1 原子上传全部卡组。
     * 先全部解析校验（任一无效即整体失败、不发送任何一份），再依序发送，确保服务端
     * decks[targetSlot=soloDeckCount++] 按席位顺序落位并在收齐 required 份后自动 ready。
     */
    private boolean soloCommitDecksForStart() {
        int required = requiredSoloSeats();
        List<List<List<Integer>>> parsed = new ArrayList<>(required);
        for (int seat = 0; seat < required; seat++) {
            List<List<Integer>> sec = parseDeckSections(soloSeatDeckPaths[seat]);
            if (sec == null) {
                Toast.makeText(context, soloSeatHasDeck(seat)
                        ? mStringManager.getSystemString(1406, "无效卡组")
                        : mStringManager.getSystemString(1301, "请先选择卡组"), Toast.LENGTH_SHORT).show();
                return false;
            }
            parsed.add(sec);
        }
        for (int seat = 0; seat < required; seat++) {
            List<List<Integer>> sec = parsed.get(seat);
            if (listener != null) listener.onPlayerWaitingDeckUpdate(sec.get(0), sec.get(1), sec.get(2));
        }
        return true;
    }

    /** 由外部（MainMenuNavigator.showPlayerWaiting）在建主完成后同步开/关 solo UI */
    public void setSoloMode(boolean solo) {
        this.soloMode = solo;
        // 子标签与对方选卡按钮在 initPlayerWaitingViews 中已统一置为 GONE，此处不再恢复；
        // solo 时席位自行接收选卡交互，非 solo 时则当任何玩家自己发送 STC_DECK_UPDATE。
        if (solo) {
            setupSoloSeatClickHandlers();
            // 席位 0（房主自身）的卡组路径此前只在 onDeckSelected 且当时 soloMode 已为 true 时
            // 写入；而本方法由建主流程在 show()（含 loadLastDeckInfo）之后才调用，沿用“上次卡组”
            // 开局时房主不会再点一次选卡，soloSeatDeckPaths[0] 便恒为空 → soloAllSeatsHaveDeck()
            // 永假 → 全部席位准备后开始按钮仍永久置灰。故在此把已加载的 currentDeck* 回填到席位 0。
            if (!soloSeatHasDeck(0) && currentDeckPath != null && !currentDeckPath.isEmpty()) {
                soloSeatDeckPaths[0] = currentDeckPath;
                soloSeatDeckNames[0] = currentDeckName == null ? "" : currentDeckName;
                // 席位“准备”= 已选卡组：回填后同步打勾，避免视觉上“席位 0 没准备”
                setSoloSeatChecked(0, true);
                if (listener != null)
                    listener.onSoloSeatDeckUpdated(0, soloSeatDeckNames[0], soloSeatDeckPaths[0]);
            }
        } else {
            clearSoloSeatClickHandlers();
        }
        // 无论回填是否发生都要重估：非 solo→solo 的切换本身就会改变判定分支
        updateStartButtonState();
    }

    /** Solo 模式下给 etPwPlayer2/3/4Name 三个席位名字框安装点击选卡回调：
     *  点击后以对应席位为 deckPickTarget 开 DeckSelectorDialog；选中后 onDeckSelected
     *  将卡组名写回席位，并把路径缓存到 soloSeatDeckPaths[seat]。 */
    private void setupSoloSeatClickHandlers() {
        if (etPwPlayer2Name != null) {
            etPwPlayer2Name.setClickable(true);
            etPwPlayer2Name.setHint(R.string.select_deck);
            etPwPlayer2Name.setOnClickListener(v -> openDeckSelectorForSeat(1));
        }
        if (etPwPlayer3Name != null) {
            etPwPlayer3Name.setClickable(true);
            etPwPlayer3Name.setHint(R.string.select_deck);
            etPwPlayer3Name.setOnClickListener(v -> openDeckSelectorForSeat(2));
        }
        if (etPwPlayer4Name != null) {
            etPwPlayer4Name.setClickable(true);
            etPwPlayer4Name.setHint(R.string.select_deck);
            etPwPlayer4Name.setOnClickListener(v -> openDeckSelectorForSeat(3));
        }
    }

    private void clearSoloSeatClickHandlers() {
        if (etPwPlayer2Name != null) { etPwPlayer2Name.setOnClickListener(null); etPwPlayer2Name.setClickable(false); etPwPlayer2Name.setHint((CharSequence) null); }
        if (etPwPlayer3Name != null) { etPwPlayer3Name.setOnClickListener(null); etPwPlayer3Name.setClickable(false); etPwPlayer3Name.setHint((CharSequence) null); }
        if (etPwPlayer4Name != null) { etPwPlayer4Name.setOnClickListener(null); etPwPlayer4Name.setClickable(false); etPwPlayer4Name.setHint((CharSequence) null); }
    }

    private void openDeckSelectorForSeat(int seat) {
        if (deckSelectorDialog == null) return;
        if (deckSelectorDialog.isShowing() && deckPickTarget == seat) {
            deckSelectorDialog.dismiss();
        } else {
            if (deckSelectorDialog.isShowing()) deckSelectorDialog.dismiss();
            deckPickTarget = seat;
            View anchor = (seat == 1) ? etPwPlayer2Name : (seat == 2) ? etPwPlayer3Name : etPwPlayer4Name;
            deckSelectorDialog.show(anchor);
        }
    }

    /** 非 TAG solo 只展设 slot 1；TAG solo 展设 slot 1/2/3；无卡组时 GONE 多余席位。 */
    private int requiredSoloSeats() {
        return isTagMode ? 4 : 2;
    }

    private void setupKickButtons() {
        if (btnPwKickPlayer1 != null) btnPwKickPlayer1.setOnClickListener(v -> sendKickPacket(0));
        if (btnPwKickPlayer2 != null) btnPwKickPlayer2.setOnClickListener(v -> sendKickPacket(1));
        if (btnPwKickPlayer3 != null) btnPwKickPlayer3.setOnClickListener(v -> sendKickPacket(2));
        if (btnPwKickPlayer4 != null) btnPwKickPlayer4.setOnClickListener(v -> sendKickPacket(3));
    }

    private void sendKickPacket(int pos) {
        if (!isHost) return;
        if (soloMode) {
            // solo 虚拟席位无真实玩家可踢：kick 按钮 = 清除该席位已选卡组与展示名、
            // 取消打勾，并重估开始按钮（不再发送网络踢人包）
            clearSoloSeatDeck(pos);
            return;
        }
        String playerName = getPlayerName(pos);
        if (playerName.isEmpty()) return;
        if (pos == selfPos) return;
        if (listener != null) listener.onKickPlayerRequested(pos);
    }

    /** solo：清除席位 seat 的卡组选择（路径/名称置空、席位名清空恢复 hint、取消勾选）并重估开始按钮。 */
    private void clearSoloSeatDeck(int seat) {
        if (seat < 0 || seat >= soloSeatDeckPaths.length) return;
        if (seat == 0) {
            // 房主自身席位：清空本地卡组选择（卡组选择按钮回到“请选择卡组”）
            soloSeatDeckPaths[0] = "";
            soloSeatDeckNames[0] = "";
            currentDeckPath = "";
            currentDeckName = "";
            currentDeckCategory = "";
            updateDeckButtonText();
        } else {
            soloSeatDeckPaths[seat] = "";
            soloSeatDeckNames[seat] = "";
            setPlayerName(seat, "");
        }
        setSoloSeatChecked(seat, false);
        updateStartButtonState();
        // 同步引擎侧席位名/路径缓存（换 side 载入、GameTopInfo 展示）一并清空
        if (listener != null) listener.onSoloSeatDeckUpdated(seat, "", "");
    }

    /** solo：设置某席位“准备”勾选框的勾选态（席位“准备”=已选卡组）；先摘监听避免触发上传回调。 */
    private void setSoloSeatChecked(int seat, boolean checked) {
        if (seat < 0) return;
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        if (seat >= checkboxes.length) return;
        CheckBox cb = checkboxes[seat];
        if (cb == null) return;
        cb.setOnCheckedChangeListener(null);
        cb.setChecked(checked);
        updateSelfCheckboxInteractivity();
    }

    private void setupStartButton() {
        if (btnPwStartGame != null) {
            btnPwStartGame.setOnClickListener(v -> {
                // Solo：开始即按席位顺序原子上传全部所需卡组（服务端收齐后自动 ready），
                // 再发开始；任一席位缺卡/无效则中止并不发送，保持按钮可再次点击。
                if (soloMode && !soloCommitDecksForStart()) {
                    return;
                }
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
        // Solo 模式：房主一人代控全部席位，开始按钮以“开局所需的全部席位都已选定卡组”为准，
        // 与房主自身的“准备”解耦（solo 准备不再上传卡组，卡组在开始时按席位顺序统一上传），
        // 避免“未选齐就先点准备→上传失败/部分脏状态→之后选齐仍无法开始”。
        if (soloMode) {
            boolean ok = soloAllSeatsHaveDeck();
            btnPwStartGame.setEnabled(ok);
            btnPwStartGame.setTextColor(ok ? Color.WHITE : Color.GRAY);
            return;
        }
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        // 对齐 duelclient.cpp STOC_HS_PLAYER_CHANGE L1238-1243：开始按钮仅以各座位准备勾选判定，
        // 不附加玩家名非空门槛——名字回显缺失/时序异常时曾导致全员准备后按钮仍永久置灰
        for (int i = 0; i < duelistCount; i++) {
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
    // 注：以下 handleXxx 不得以 isShowing() 丢弃事件——弹窗窗口经 DraggablePopupHelper
    // bringHostPopupToFront 重排（removeViewImmediate+addView）后 isShowing 会误报 false，
    // 导致主机侧玩家名/ready 回显与 STOC_TYPE_CHANGE 被静默丢弃，
    // 开始按钮永远灰色、WindBot 加入不可见（同 handleJoinGame 修复缘由）；
    // 视图均为 show() 中同步创建且各 setter 自带判空，事件早到/窗已关时写入无害

    public void handlePlayerEnter(String name, int pos) {
        removeObserver(name);
        setPlayerName(pos, name);
        refreshPlayerDisplay();
    }

    public void handlePlayerChange(int status) {
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
        updateWatchCount(watchCount);
        refreshPlayerDisplay();
    }

    public void handleJoinGame(int lflist, int rule, int mode, int duelRule, int noCheckDeck, int noShuffleDeck, int startLp, int startHand, int drawCount, int timeLimit) {
        // 视图在 show() 中已同步创建：不再以 isShowing() 丢弃回调
        //（弹窗真正就绪可能晚于 STOC_JOIN_GAME 到达，丢弃后房间信息永久停留“----”）
        updateRoomInfo(lflist, rule, mode, duelRule, noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, timeLimit);
    }

    public void handleTypeChange(int type, boolean isTag) {
        int selfType = type & 0x0F;
        boolean isHost = ((type >> 4) & 0x0F) != 0;
        updateTypeChange(selfType, isTag, isHost);
    }

    public void handleDeckError(int errorType, int cardCode) {
        // 卡名直接拼入错误文本的「%ls」占位（解析不到卡名时回退显示 code），不再另起一行提示卡名
        String cardName = "";
        if (cardCode > 0 && errorType != YGOProtocol.DECKERROR_MAINCOUNT
                && errorType != YGOProtocol.DECKERROR_EXTRACOUNT
                && errorType != YGOProtocol.DECKERROR_SIDECOUNT
                && cardNameResolver != null) {
            String resolved = cardNameResolver.resolve(cardCode);
            cardName = resolved != null ? resolved : "";
        }
        String nameOrCode = cardName.isEmpty() ? String.valueOf(cardCode) : cardName;
        String errorDesc;
        switch (errorType) {
            case YGOProtocol.DECKERROR_LFLIST:
                errorDesc = mStringManager.getSystemString(1407, "「%ls」的数量不符合当前禁限卡表设定。")
                        .replace("%ls", nameOrCode);
                break;
            case YGOProtocol.DECKERROR_OCGONLY:
                errorDesc = mStringManager.getSystemString(1413, "「%ls」为OCG独有卡，不允许在当前设定下使用。")
                        .replace("%ls", nameOrCode);
                break;
            case YGOProtocol.DECKERROR_TCGONLY:
                errorDesc = mStringManager.getSystemString(1414, "「%ls」为TCG独有卡，不允许在当前设定下使用。")
                        .replace("%ls", nameOrCode);
                break;
            case YGOProtocol.DECKERROR_UNKNOWNCARD:
                errorDesc = mStringManager.getSystemString(1415, "卡组中「%ls(%d)」尚不支持在本主机使用")
                        .replace("%ls", nameOrCode)
                        .replace("%d", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_CARDCOUNT:
                errorDesc = mStringManager.getSystemString(1416, "卡组中「%ls」的总数量超过3张。")
                        .replace("%ls", nameOrCode);
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
                errorDesc = mStringManager.getSystemString(1421, "未知卡组错误(type=%ls)")
                        .replace("%ls", String.valueOf(errorType));
                break;
        }

        String title = mStringManager.getSystemString(1725, "卡组验证失败");
        YesOrNoDialog dialog = new YesOrNoDialog(context);
        dialog.setTitle(title).setMessage(errorDesc);
        dialog.show();
    }

    public interface CardNameResolver {
        String resolve(int cardCode);
    }

    private CardNameResolver cardNameResolver;

    public void setCardNameResolver(CardNameResolver resolver) {
        this.cardNameResolver = resolver;
    }

    /** 内部跳转（决斗开始/返回主界面）：抑制外部 dismiss 回调后关闭；
     * 弹窗退场动画会延迟触发 onDismiss，抑制标志由回调内消费复位 */
    public void hideForNavigation() {
        if (popupWindow == null) return;
        if (popupWindow.isShowing()) {
            suppressDismiss = true;
            popupWindow.dismiss();
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

    /**
     * 退出等待界面时彻底释放本弹窗：区别于其他“仅隐藏、下次复用保留布局”的对话框，
     * PlayerWaitingDialog 每次都新建实例，故退出时须清除其持久化的拖拽布局位置，
     * 使下次进入回到默认居中布局；同时收起卡组选择子窗、置空引用以释放视图。
     * 抑制 dismiss→restoreMainMenu 兜底回调，返回导航由调用方（onExitWaiting）独占。
     */
    public void releaseOnExit() {
        if (deckSelectorDialog != null) {
            deckSelectorDialog.dismiss();
            deckSelectorDialog = null;
        }
        if (popupWindow != null) {
            if (popupWindow.isShowing()) {
                suppressDismiss = true;
                popupWindow.setOnDismissListener(null);
                popupWindow.dismiss();
            }
            popupWindow = null;
        }
        draggableHelper = null;
        externalDismissListener = null;
        DraggablePopupHelper.clearPosition(context, DIALOG_ID);
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
		});
    }
}
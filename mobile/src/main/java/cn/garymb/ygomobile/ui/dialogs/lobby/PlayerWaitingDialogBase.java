package cn.garymb.ygomobile.ui.dialogs.lobby;
import cn.garymb.ygomobile.ui.dialogs.deck.DeckSelectorDialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;

/**
 * Player waiting lobby dialog, base layer of the split inheritance chain
 * (base <- mid <- PlayerWaitingDialog). Holds all state fields, the public
 * listener/resolver callback interfaces and the pure seat/watch/room/deck
 * state helpers that every higher layer shares. Kept under 800 lines per
 * class while preserving the original behavior line for line.
 */
abstract class PlayerWaitingDialogBase {

    public static final StringManager mStringManager = DataManager.get().getStringManager();

    /** 拖拽布局持久化 id：本弹窗是唯一“退出即释放”的对话框，退出时需清除此 key 的位置缓存。 */
    protected static final String DIALOG_ID = "player_waiting_dialog";

    protected final Context context;
    protected PopupWindow popupWindow;
    protected boolean suppressDismiss = false;
    protected PopupWindow.OnDismissListener externalDismissListener;
    protected DraggablePopupHelper draggableHelper;
    protected DeckSelectorDialog deckSelectorDialog;

    protected String currentDeckCategory = "";
    protected String currentDeckName = "";
    protected String currentDeckPath = "";

    // Solo mode (LAN 纯单人模式): 房主自己为双方分别选卡，server 依次写入 slot0 与 slot1
    // deckPickTarget: 0 = 正在为“我方”选卡 (写入 currentDeckPath), 1/2/3 = 为其他席位选卡 (写入 soloSeatDeckPaths[1..3])
    // Solo TAG 建房时四席位都需要房主代选，slot1/2/3 各自对应 etPwPlayer2/3/4Name 点击选卡组；
    // 非 TAG solo 只用到 slot1。
    protected boolean soloMode = false;
    protected int deckPickTarget = 0;
    /** slot 1..3 席位卡组路径（solo 模式专用）；index 0 保留未用 */
    protected final String[] soloSeatDeckPaths = new String[]{"", "", "", ""};
    /** slot 1..3 席位卡组名（solo 模式专用），选好后作为玩家名展示在席位与 GameTopInfo 上 */
    protected final String[] soloSeatDeckNames = new String[]{"", "", "", ""};

    protected TextView etPwPlayer1Name, etPwPlayer2Name, etPwPlayer3Name, etPwPlayer4Name;
    /** 大厅席位表情气泡（用户规格：在 player waiting 发送表情，气泡跟随对应席位名右对齐显示） */
    protected final ImageView[] pwSeatEmoteBubbles = new ImageView[4];
    /** 气泡定时隐藏任务（按席位下标，重复发送时先撤销上一条再重新计时） */
    protected final Runnable[] pwSeatEmoteHiders = new Runnable[4];
    protected final Handler pwEmoteHandler = new Handler(Looper.getMainLooper());
    /** 表情气泡显示时长（毫秒），与决斗态头像气泡一致 */
    protected static final long PW_EMOTE_DURATION_MS = 3000L;
    protected CheckBox chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready;
    protected Button btnPwDuelistMode, btnPwSpectatorMode, btnPwReady, btnPwDeckSelect, btnPwExitWaiting;
    protected Button btnPwStartGame;
    protected ImageButton btnPwKickPlayer1, btnPwKickPlayer2, btnPwKickPlayer3, btnPwKickPlayer4;
    protected TextView tvRoomInfo;
    protected TextView tvWatchCount;
    protected TextView tvDeckSelectLabel;
    protected View layoutTagPlayers;
    protected int selfPos = 0;
    protected boolean isSelfReady = false;
    protected boolean isHost = false;
    protected boolean isTagMode = false;
    protected int watchCount = 0;
    protected final List<String> observerNames = new ArrayList<>();

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

    protected final OnPlayerWaitingListener listener;

    protected final PopupWindow.OnDismissListener internalDismissListener = () -> {
        if (suppressDismiss) {
            suppressDismiss = false;
            return;
        }
        if (externalDismissListener != null) externalDismissListener.onDismiss();
    };


    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        this.externalDismissListener = listener;
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(internalDismissListener);
        }
    }

    /**
     * 绑定 4 个席位表情气泡视图与其定时隐藏任务（每次 show 重建视图树后重新绑定）：
     * 隐藏任务运行时按席位下标取当前视图，故重绑后旧 post 延时任务作废、不会误隐新视图。
     */
    protected void bindSeatEmoteBubbles(View root) {
        int[] ids = {R.id.iv_pw_emote_1, R.id.iv_pw_emote_2, R.id.iv_pw_emote_3, R.id.iv_pw_emote_4};
        for (int i = 0; i < 4; i++) {
            final int pos = i;
            pwSeatEmoteBubbles[i] = root.findViewById(ids[i]);
            if (pwSeatEmoteHiders[i] != null) pwEmoteHandler.removeCallbacks(pwSeatEmoteHiders[i]);
            pwSeatEmoteHiders[i] = () -> {
                ImageView iv = pwSeatEmoteBubbles[pos];
                if (iv != null) iv.setVisibility(View.GONE);
            };
        }
    }

    /**
     * 在指定席位（pos 0..3）名字右对齐处显示表情气泡（用户规格）：
     * 大厅收到该席位的表情聊天时由 FieldChatBoard 调用，定时自动隐藏。
     */
    public void showSeatEmote(int pos, String code) {
        if (pos < 0 || pos >= 4) return;
        ImageView iv = pwSeatEmoteBubbles[pos];
        if (iv == null) return;
        Bitmap bmp = TextureLoader.get() != null ? TextureLoader.get().getEmoticon(code) : null;
        if (bmp == null || bmp.isRecycled()) return;
        iv.setImageBitmap(bmp);
        iv.setVisibility(View.VISIBLE);
        if (pwSeatEmoteHiders[pos] != null) {
            pwEmoteHandler.removeCallbacks(pwSeatEmoteHiders[pos]);
            pwEmoteHandler.postDelayed(pwSeatEmoteHiders[pos], PW_EMOTE_DURATION_MS);
        }
    }

    /** 撤销全部席位气泡及其延时隐藏（dismiss/重置/释放时调用，避免残留与泄漏） */
    protected void hideAllSeatEmotes() {
        for (int i = 0; i < 4; i++) {
            if (pwSeatEmoteHiders[i] != null) pwEmoteHandler.removeCallbacks(pwSeatEmoteHiders[i]);
            if (pwSeatEmoteBubbles[i] != null) pwSeatEmoteBubbles[i].setVisibility(View.GONE);
        }
    }

    protected void showWatchersToast() {
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

    protected void refreshWatchCountDisplay() {
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
        hideAllSeatEmotes();
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
        seat.setBackground(TextUtils.isEmpty(name) ? context.getDrawable(R.drawable.ygopro_selected_background) : context.getDrawable(R.drawable.ygopro_base_background));
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

    protected int findNextEmptyPos(int currentPos) {
        for (int i = currentPos + 1; i < 4; i++) {
            if (getPlayerName(i).isEmpty()) return i;
        }
        for (int i = 0; i < currentPos; i++) {
            if (getPlayerName(i).isEmpty()) return i;
        }
        return -1;
    }

    protected int findFirstEmptyPos() {
        for (int i = 0; i < 4; i++) {
            if (getPlayerName(i).isEmpty()) return i;
        }
        return -1;
    }
    protected void resetRoomInfo() {
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

    protected String getDuelRuleName(int duelRule) {
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

    protected String getBanlistName(int lflist) {
        if (lflist == 0) return "N/A";
        LimitManager limitManager = DataManager.get().getLimitManager();
        boolean isGenesysMode = AppsSettings.get().getGenesysMode() == 1;
        String name = isGenesysMode
                ? limitManager.getGenesysLimitNameByHash(lflist)
                : limitManager.getLimitNameByHash(lflist);
        return name != null ? name : "N/A";
    }

    /** 卡片允许：协议 HostInfo.rule 0..5，对齐 game.cpp cbRule 六项（1481-1486） */
    protected String getCardAllowedName(int rule) {
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
    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }
    protected void updateDeckButtonText() {
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

    protected void updateDeckSelectButtonState() {
        if (btnPwDeckSelect != null) {
            btnPwDeckSelect.setEnabled(!isSelfReady);
            btnPwDeckSelect.setTextColor(isSelfReady ? Color.GRAY : Color.WHITE);
        }
    }
    /** 静默同步自身 ready checkbox 并重新评估开始按钮可用状态（不触发重发卡组的监听回调） */
    protected void syncSelfCheckboxAndStartState(CheckBox[] checkboxes) {
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
    protected void updateSelfCheckboxInteractivity() {
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
    protected boolean sendDeckIfLoaded() {
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
    protected List<List<Integer>> parseDeckSections(String path) {
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
    protected boolean soloSeatHasDeck(int seat) {
        return seat >= 0 && seat < soloSeatDeckPaths.length
                && soloSeatDeckPaths[seat] != null && !soloSeatDeckPaths[seat].isEmpty();
    }

    /** solo：开局所需的全部席位（0..requiredSoloSeats()-1）是否都已选定卡组。 */
    protected boolean soloAllSeatsHaveDeck() {
        int required = requiredSoloSeats();
        for (int seat = 0; seat < required; seat++) {
            if (!soloSeatHasDeck(seat)) return false;
        }
        return true;
    }
    /** 非 TAG solo 只展设 slot 1；TAG solo 展设 slot 1/2/3；无卡组时 GONE 多余席位。 */
    protected int requiredSoloSeats() {
        return isTagMode ? 4 : 2;
    }
    /** solo：设置某席位“准备”勾选框的勾选态（席位“准备”=已选卡组）；先摘监听避免触发上传回调。 */
    protected void setSoloSeatChecked(int seat, boolean checked) {
        if (seat < 0) return;
        CheckBox[] checkboxes = {chkPwPlayer1Ready, chkPwPlayer2Ready, chkPwPlayer3Ready, chkPwPlayer4Ready};
        if (seat >= checkboxes.length) return;
        CheckBox cb = checkboxes[seat];
        if (cb == null) return;
        cb.setOnCheckedChangeListener(null);
        cb.setChecked(checked);
        updateSelfCheckboxInteractivity();
    }
    protected void updateStartButtonState() {
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
    public interface CardNameResolver {
        String resolve(int cardCode);
    }

    protected CardNameResolver cardNameResolver;

    public void setCardNameResolver(CardNameResolver resolver) {
        this.cardNameResolver = resolver;
    }

    protected PlayerWaitingDialogBase(Context context, OnPlayerWaitingListener listener) {
        this.context = context;
        this.listener = listener;
    }
}

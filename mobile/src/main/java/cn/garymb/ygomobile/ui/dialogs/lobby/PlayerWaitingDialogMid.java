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
 * Player waiting lobby dialog, middle layer of the split inheritance chain.
 * Assembles the popup window (show / view init), deck loading, ready
 * interaction wiring, room/type info rendering and the solo/kick/start
 * button setup on top of the base state helpers.
 */
abstract class PlayerWaitingDialogMid extends PlayerWaitingDialogBase {

    protected PlayerWaitingDialogMid(Context context, OnPlayerWaitingListener listener) {
        super(context, listener);
    }

    public void show(View anchorView) {
        View customView = LayoutInflater.from(context).inflate(R.layout.popup_window_player_waiting, null);

        initPlayerWaitingViews(customView);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        
        // 横屏时，根据 Activity 的实际宽度的 2/3 来限制弹窗宽度；竖屏保持正方形样式
        final android.app.Activity act = context instanceof android.app.Activity
                ? (android.app.Activity) context : null;
        if (act != null && act.getWindow() != null && act.getWindow().getAttributes() != null) {
            DisplayMetrics screen = new DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(screen);
            // 判断横屏还是竖屏
            boolean isLandscape = screen.widthPixels >= screen.heightPixels;
            if (isLandscape) {
                // 横屏：宽度取屏幕宽度的 2/3
                int maxWidth = (int) (screen.widthPixels * 0.66f);
                int[] fitted = DraggablePopupHelper.fitSizeToScreen(context, popupWidth, popupHeight);
                if (fitted[0] > maxWidth) {
                    // 按比例缩小高度
                    float ratio = (float) maxWidth / fitted[0];
                    fitted[0] = maxWidth;
                    fitted[1] = (int) (fitted[1] * ratio);
                }
                popupWidth = fitted[0];
                popupHeight = fitted[1];
            }
            // 竖屏：保持原有设计尺寸不变（已适配正方形）
        }
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

        // 全屏弹窗窗口不得作为 touch-modal：置为非模态后窗口边界外的触摸仍会派发给其他窗口。
        // 但软键盘落在全屏窗口的边界“内”，单靠本设置挡不住键盘区域触摸被全屏层吞掉——
        // Android 12+ 的分层修复见下方 setInputMethodMode 块
        popupWindow.setTouchModal(false);

        // 输入法分层修复（用户反馈：等待对话框显示时点大厅聊天输入框，输入法弹在 dialog
        // 下方且按键点不动）：
        // Android 12(S) 起系统把软键盘改为相对“IME 目标窗口”分层——键盘只垫在目标窗口之上，
        // 普通非获焦 PopupWindow 不被视为目标，反而压在键盘之上；本弹窗又被 DraggablePopupHelper
        // 铺成全屏，键盘区域触摸落在全屏层界内、被包装层转发给 Activity decor，IME 收不到事件，
        // 表现为按键点不动。Android 11 及以下键盘恒在所有应用窗口之上，无此问题亦无需本设置
        // （旧系统里同组合语义相反：自动补全下拉压键盘）。
        // 非获焦弹窗置 INPUT_METHOD_NEEDED 使 PopupWindow.computeFlags() 为本窗口加
        // FLAG_ALT_FOCUSABLE_IM，其官方语义（见 WindowManager.LayoutParams 注释）为
        // “不可获焦但按输入法目标对待，分层在输入法窗口之下”：键盘显示在 dialog 上方、
        // 按键直达 IME 窗口；窗口焦点仍留给 Activity，et_chat_input 获焦与输入链路不受影响
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
            // 用户新规格：输入法抬高聊天输入框时，输入框与输入法都显示在弹窗前面——
            // 弹窗随键盘 resize：全屏包装层可视区收缩、居中内容整体上移到键盘之上，
            // 腾出贴着键盘上沿的输入框条带，不再被弹窗内容压住（旧 ADJUST_NOTHING 仅保
            // 证键盘可见，输入框仍可能被弹窗遮挡，已废弃）
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        draggableHelper = new DraggablePopupHelper(context, DIALOG_ID);
        // 竖屏宽铺满屏宽且高与宽相等（正方形），转回横屏恢复设计宽高比（用户规格）
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight, true);
        // 用户规格：本 dialog 横竖屏均不可拖动（对齐 C++ 定位弹窗），其他 dialog 仍可拖动
        draggableHelper.setDragEnabled(popupWindow, false);

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
        // 输入法抬升联动（用户规格：本弹窗维持原本整个 dialog 被抬高的方式，不做自动最小化，
        // 故 autoMinimize=false）：几何判定驱动——聊天输入框实测被抬高（不论输入法是否显示）
        // 时底边贴输入框上沿，输入框未被抬高则保持原处，键盘显示期由工具类冻结本窗口
        // 的 IME 自适应，消除之前「输入框未抬高时快速上下闪动」的 WMS 二次 resize 拉扯
        ImePopupAdjuster.attach(popupWindow, false);

        if (listener != null) listener.onPlayerWaitingShown();
    }

    protected void initPlayerWaitingViews(View root) {
        etPwPlayer1Name = root.findViewById(R.id.et_player1_name);
        etPwPlayer2Name = root.findViewById(R.id.et_player2_name);
        etPwPlayer3Name = root.findViewById(R.id.et_player3_name);
        etPwPlayer4Name = root.findViewById(R.id.et_player4_name);
        bindSeatEmoteBubbles(root);
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
    protected void loadLastDeckInfo() {
        AppsSettings settings = AppsSettings.get();
        currentDeckCategory = settings.getLastCategory();
        currentDeckName = settings.getLastDeckName();
        currentDeckPath = settings.getLastDeckPath();

        // 进入等待界面时，需要保证选中的是一个可用于对战的卡组。以下情形视为“无有效最后卡组”，
        // 需默认选中一个可用卡组：
        //   1) 最后分类是“卡包展示”（其卡组不可用于对战）；
        //   2) 首次安装/无 last category & last deck：分类仍是默认值、或按路径拼出的卡组文件不存在。
        // 默认选取顺序：未分类卡组第一个 → 未分类无卡组则人机卡组第一个 → 均无则清空回退“请选择卡组”。
        // 选中后写回 setLastDeckPath，使随后展开的 DeckSelectorDialog 默认停在该分类并加载其卡组。
        // 此逻辑仅在 PlayerWaitingDialog 生效，不影响其他入口的卡组选择。
        boolean packCategory = TextUtils.equals(context.getString(R.string.category_pack), currentDeckCategory);
        boolean invalidDeck = currentDeckPath == null || currentDeckPath.isEmpty()
                || !new File(currentDeckPath).exists();
        if (packCategory || invalidDeck) {
            DeckFile defaultDeck = pickDefaultDeck(settings);
            if (defaultDeck != null) {
                currentDeckPath = defaultDeck.getPath();
                currentDeckName = defaultDeck.getName();
                currentDeckCategory = defaultDeck.getTypeName();
                // setLastDeckPath 依据卡组路径重新写入最后分类与最后卡组名，
                // 使随后展开的 DeckSelectorDialog 默认选中该分类并加载其卡组
                settings.setLastDeckPath(currentDeckPath);
            } else {
                // 未分类与人机卡组均无卡组：清空选择，等待界面回退到"请选择卡组"
                currentDeckPath = "";
                currentDeckName = "";
                currentDeckCategory = "";
            }
        }

        updateDeckButtonText();
    }

    /** 默认卡组选取：未分类卡组第一个优先，未分类无卡组时回退人机卡组第一个；均无返回 null。 */
    protected DeckFile pickDefaultDeck(AppsSettings settings) {
        List<DeckFile> uncatDecks = DeckUtil.getDeckList(settings.getDeckDir());
        if (uncatDecks != null && !uncatDecks.isEmpty()) {
            return uncatDecks.get(0);
        }
        List<DeckFile> aiDecks = DeckUtil.getDeckList(settings.getAiDeckDir());
        if (aiDecks != null && !aiDecks.isEmpty()) {
            return aiDecks.get(0);
        }
        return null;
    }
    protected void setupSelfReadyInteraction() {
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
    /**
     * solo：点击开始时按席位顺序 0..required-1 原子上传全部卡组。
     * 先全部解析校验（任一无效即整体失败、不发送任何一份），再依序发送，确保服务端
     * decks[targetSlot=soloDeckCount++] 按席位顺序落位并在收齐 required 份后自动 ready。
     */
    protected boolean soloCommitDecksForStart() {
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
    protected void setupSoloSeatClickHandlers() {
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

    protected void clearSoloSeatClickHandlers() {
        if (etPwPlayer2Name != null) { etPwPlayer2Name.setOnClickListener(null); etPwPlayer2Name.setClickable(false); etPwPlayer2Name.setHint((CharSequence) null); }
        if (etPwPlayer3Name != null) { etPwPlayer3Name.setOnClickListener(null); etPwPlayer3Name.setClickable(false); etPwPlayer3Name.setHint((CharSequence) null); }
        if (etPwPlayer4Name != null) { etPwPlayer4Name.setOnClickListener(null); etPwPlayer4Name.setClickable(false); etPwPlayer4Name.setHint((CharSequence) null); }
    }

    protected void openDeckSelectorForSeat(int seat) {
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
    protected void setupKickButtons() {
        if (btnPwKickPlayer1 != null) btnPwKickPlayer1.setOnClickListener(v -> sendKickPacket(0));
        if (btnPwKickPlayer2 != null) btnPwKickPlayer2.setOnClickListener(v -> sendKickPacket(1));
        if (btnPwKickPlayer3 != null) btnPwKickPlayer3.setOnClickListener(v -> sendKickPacket(2));
        if (btnPwKickPlayer4 != null) btnPwKickPlayer4.setOnClickListener(v -> sendKickPacket(3));
    }

    protected void sendKickPacket(int pos) {
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
    protected void clearSoloSeatDeck(int seat) {
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
    protected void setupStartButton() {
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
    /** 内部跳转（决斗开始/返回主界面）：抑制外部 dismiss 回调后关闭；
     * 弹窗退场动画会延迟触发 onDismiss，抑制标志由回调内消费复位 */
    public void hideForNavigation() {
        if (popupWindow == null) return;
        if (popupWindow.isShowing()) {
            suppressDismiss = true;
            popupWindow.dismiss();
        }
    }

    // 输入法抬升/还原已抽取到 cn.garymb.ygomobile.utils.ImePopupAdjuster：本弹窗与其他 dialog
    // （决斗中的是/否询问、宣言类、猜拳、先后攻、位置选择、录像保存等）走同一条逻辑——
    // 抬键盘时底边贴聊天输入框上沿，收起后回中央，横竖屏切换亦回中央；登记见 show() 末尾。

    public void dismiss() {
        hideAllSeatEmotes();
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
        hideAllSeatEmotes();
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
}

package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.game.ReplayPlayer;
import cn.garymb.ygomobile.game.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;

/**
 * 左侧卡片详情面板与全部控制按钮的管理类：
 * 卡片详情展示 / 左列功能按钮 / 底部行动按钮 / 录像控制按钮 / 取消或完成按钮 / 卡组操作栏
 */
public class CardDetailPanel {

    private final YGOProActivity activity;
    // 卡片详情绑定器（同包）：详情控件/卡码/卡背状态已平移，本面板留显隐协调与按钮域
    private final CardInfoBinder binder = new CardInfoBinder();

    private LinearLayout layout;
    /** include 根（activity_ygo_game.xml 中 layout_card_detail_panel）：
     *  本字段与 layout 并行控制，避免 PlayerWaitingDialog 进入时（hideGameUI）
     *  仅 layout_card_detail 子列隐去、左侧侧图标栏所在根仍默认可见。 */
    private LinearLayout panelRoot;

    private ImageButton btnSettings, btnChat, btnSound, btnSpeed, btnEmote, btnNote;

    private LinearLayout layoutBottomActions;
    private Button btnSurrender, btnIgnoreTiming, btnShowTiming, btnAvailableTiming;
    private Button btnCancelOrFinish, btnShuffleHand;
    // 撤回入口不在本面板：已按用户规格改放顶部信息条中央回合数下方的发光 ic_undo
    //（layout_game_right 的 iv_undo，由 GameTopInfoManager.setUndoPrompt 按 STOC_UNDO_STATE 闪动）

    private LinearLayout layoutReplayControl;
    private Button btnReplayPlay, btnReplayPause, btnReplayNext, btnReplayLast, btnReplayShuffle, btnReplayQuit;
    private LinearLayout layoutDeckControl;

    /** 观战模式：左侧面板常驻录像控制条但只显示「切换视角/退出」（播控按钮 INVISIBLE），
     *  两按钮点击分流到观战版视角交换/退出观战（对齐 event_handler.cpp player_type==7 分支） */
    private boolean spectatorMode;

    private StringManager mStringManager = DataManager.get().getStringManager();

    // === 时点三态（对齐 gframe game.h: ignore_chain / always_chain / chain_when_avail，三者互斥） ===
    /** 忽略时点（sys1292）：跳过所有非强制、非诱发的时点询问 */
    private boolean ignoreChain;
    /** 显示时点（sys1293）：即使没有可发动的卡也询问每个时点 */
    private boolean alwaysChain;
    /** 可用时点（sys1294）：只要存在可发动的卡就询问 */
    private boolean chainWhenAvail;

    // 选择上下文（cancelOrFinish 决策所需，由 YGOProActivity 注册同步）
    private int currentSelectType = -1;
    private YesOrNoDialog currentDialog;
    private CardSelectDialog cardSelectDialog;
    private CardDisplayDialog cardDisplayDialog;

    // cancelOrFinish 与洗手卡互斥显隐协调（取消/完成按钮显示时隐藏洗手卡，隐藏后按通讯允许恢复）
    /** 通讯（MSG_SELECT_IDLECMD show_shuffle）是否允许洗切手卡 */
    private boolean shuffleAllowedByMsg;
    /** cancelOrFinish 按钮当前是否处于可见态（VISIBLE） */
    private boolean cancelOrFinishShown;
    /** cancelOrFinish 按钮周围的蚂蚁线高亮动画 */
    private MarchingAntsDrawable antsHighlight;

    public CardDetailPanel(YGOProActivity activity) {
        this.activity = activity;
    }

    public void bindViews() {
        layout = activity.findViewById(R.id.layout_card_detail);
        panelRoot = activity.findViewById(R.id.layout_card_detail_panel);
        binder.attachViews(layout, panelRoot,
                activity.findViewById(R.id.iv_card_image),
                activity.findViewById(R.id.tv_card_name),
                activity.findViewById(R.id.tv_card_setname),
                activity.findViewById(R.id.tv_card_attr),
                activity.findViewById(R.id.tv_card_level),
                activity.findViewById(R.id.tv_card_desc),
                activity.findViewById(R.id.sv_card_desc),
                // 关键词卡片列表弹窗停靠锚点：详情描述高亮关键词点击后显示在本区域最左侧
                activity.findViewById(R.id.layout_game_right));

        btnSettings = activity.findViewById(R.id.btn_settings);
        btnChat = activity.findViewById(R.id.btn_chat);
        btnSound = activity.findViewById(R.id.btn_sound);
        btnSpeed = activity.findViewById(R.id.btn_speed);
        btnEmote = activity.findViewById(R.id.btn_emote);
        btnNote = activity.findViewById(R.id.btn_note);

        layoutBottomActions = activity.findViewById(R.id.layout_bottom_actions);
        btnSurrender = activity.findViewById(R.id.btn_surrender);
        btnIgnoreTiming = activity.findViewById(R.id.btn_ignore_timing);
        btnShowTiming = activity.findViewById(R.id.btn_show_timing);
        btnAvailableTiming = activity.findViewById(R.id.btn_available_timing);
        btnShuffleHand = activity.findViewById(R.id.btn_shuffle_hand);
        btnCancelOrFinish = activity.findViewById(R.id.btn_cancel_or_finish);

        // 投降/洗切手卡按钮文本复用 gframe 既有系统字符串（1351 投降、1297 洗切手卡），
        // XML 中的中文仅作兜底，切换语言后由对应语言覆盖
        if (btnSurrender != null)
            btnSurrender.setText(mStringManager.getSystemString(1351, "投降"));
        if (btnShuffleHand != null)
            btnShuffleHand.setText(mStringManager.getSystemString(1297, "洗切手卡"));

        layoutReplayControl = activity.findViewById(R.id.layout_replay_control);
        btnReplayPlay = activity.findViewById(R.id.btn_replay_play);
        btnReplayPause = activity.findViewById(R.id.btn_replay_pause);
        btnReplayNext = activity.findViewById(R.id.btn_replay_next);
        btnReplayLast = activity.findViewById(R.id.btn_replay_last);
        btnReplayShuffle = activity.findViewById(R.id.btn_replay_shuffle);
        btnReplayQuit = activity.findViewById(R.id.btn_replay_quit);
        layoutDeckControl = activity.findViewById(R.id.layout_deck_control);

        // 录像控制按钮文字复用 gframe 既有系统字符串（对齐 game.cpp wReplayControl）：
        // 播放 1343 / 暂停 1344 / 下一步 1345 / 上一步 1360 / 切换视角 1346 / 退出 1347
        applyReplayButtonTitles();
        // 时点三键文字在此先行着色（对齐 game.cpp 1292/1293/1294），MSG_NEW_TURN 的 showChainButtons 会再次刷新
        applyChainButtonTitles();

        // 对齐 game.cpp L1357-1359：三个时点按钮创建后默认隐藏，MSG_NEW_TURN 时才显示
        hideChainButtons();

        setupListeners();
    }

    public void setImageLoader(ImageLoader imageLoader) {
        binder.setImageLoader(imageLoader);
    }

    /**
     * 注入/清除卡组编辑关键词导航器（{@code DeckEditorManager} 在进入时注入搜索到卡组列表、退出时置空）：
     * 非空时卡详描述里的高亮词点击不再弹关键词列表弹窗，而是填入卡组检索并搜索。
     */
    public void setDeckKeywordNavigator(CardInfoBinder.KeywordNavigator navigator) {
        binder.setDeckKeywordNavigator(navigator);
    }

    /**
     * 面板初始化时统一从 TextureLoader 获取侧边功能按钮图标
     * （对齐 gframe image_manager.cpp extra 图标：tSettings/tLogs/tPlay/tOneX/tEmoticon/tTalk），
     * 必须在 TextureLoader.init() 之后调用；纹理缺失时保留 XML 默认图兜底
     */
    public void bindSideButtonIcons() {
        TextureLoader tl = TextureLoader.get();
        if (btnSettings != null) {
            Bitmap bmp = tl.getSettingsTexture();
            if (bmp != null) btnSettings.setImageBitmap(bmp);
        }
        if (btnNote != null) {
            Bitmap bmp = tl.getLogsTexture();
            if (bmp != null) btnNote.setImageBitmap(bmp);
        }
        if (btnSound != null) {
            // 对齐 gframe game.cpp imgVol：声音开启显示 tPlay、全部静音显示 tMute
            boolean soundOn = AppsSettings.get().getIntSettings("chkEnableSound", 1) == 1
                    || AppsSettings.get().getIntSettings("chkEnableMusic", 1) == 1;
            Bitmap bmp = soundOn ? tl.getPlayTexture() : tl.getMuteTexture();
            if (bmp != null) btnSound.setImageBitmap(bmp);
        }
        if (btnSpeed != null) {
            // 对齐 gframe game.cpp imgQuickAnimation：快速动画 tDoubleX、常速 tOneX
            boolean quick = AppsSettings.get().getIntSettings("chkQuickAnimation", 0) == 1;
            Bitmap bmp = quick ? tl.getDoubleXTexture() : tl.getOneXTexture();
            if (bmp != null) btnSpeed.setImageBitmap(bmp);
        }
        if (btnEmote != null) {
            Bitmap bmp = tl.getEmoticonTexture();
            if (bmp != null) btnEmote.setImageBitmap(bmp);
        }
        if (btnChat != null) {
            // 对齐 gframe game.cpp L1319：初始图标跟随停用聊天设置（chkDisableChatting），停用显示 tShut、启用显示 tTalk
            boolean ignored = AppsSettings.get().getIntSettings("chkDisableChatting", 0) == 1;
            Bitmap bmp = ignored ? tl.getShutTexture() : tl.getTalkTexture();
            if (bmp != null) btnChat.setImageBitmap(bmp);
        }
    }

    /** 更新聊天按钮图标（对齐 gframe event_handler.cpp BUTTON_CHATTING：停用聊天显示 tShut、启用显示 tTalk） */
    public void updateChatIcon(boolean ignored) {
        if (btnChat == null) return;
        Bitmap bmp = ignored ? TextureLoader.get().getShutTexture() : TextureLoader.get().getTalkTexture();
        if (bmp != null) btnChat.setImageBitmap(bmp);
    }

    /**
     * 聊天开关按钮显隐（由 YGOProActivity.updateChatUIVisibility 按场景集中控制：
     * 卡组编辑/录像/残局这些无聊天对象场景隐藏，对齐 gframe 隐藏 wChat；
     * enter/exitDeckEditorMode 不再直接操作，避免与集中控制冲突）
     */
    public void setChatToggleVisible(boolean visible) {
        if (btnChat != null) btnChat.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** 更新声音按钮图标（对齐 gframe imgVol：声音开启显示 tPlay、静音显示 tMute） */
    public void updateSoundIcon(boolean enabled) {
        if (btnSound == null) return;
        Bitmap bmp = enabled ? TextureLoader.get().getPlayTexture() : TextureLoader.get().getMuteTexture();
        if (bmp != null) btnSound.setImageBitmap(bmp);
    }

    /** 更新速度按钮图标（对齐 gframe imgQuickAnimation：快速动画 tDoubleX、常速 tOneX） */
    public void updateSpeedIcon(boolean quick) {
        if (btnSpeed == null) return;
        Bitmap bmp = quick ? TextureLoader.get().getDoubleXTexture() : TextureLoader.get().getOneXTexture();
        if (bmp != null) btnSpeed.setImageBitmap(bmp);
    }

    private void setupListeners() {
        // 竖屏布局（layout-port）同样保留全部按钮 ID，但旋转/裁剪场景仍可能取到 null，统一空保护
        // 点击投降不再直接发送通讯：交由 Activity 弹 YesOrNoDialog 二次确认
        //（对齐 event_handler.cpp BUTTON_LEAVE_GAME → PopupElement(wSurrender) → BUTTON_SURRENDER_YES → CTOS_SURRENDER）
        if (btnSurrender != null) {
            btnSurrender.setOnClickListener(v -> {
                playButtonSound();
                activity.requestSurrender();
            });
        }
        // 撤回不做二次确认且入口已移到顶部回合数下方的 ic_undo（点击由 GameTopInfoManager 转发）；
        // 结果（成功 / 重建 / 不可撤回）由服务端经 STOC_UNDO_ACK 提示
        // 时点按钮（对齐 event_handler.cpp L297-320 BUTTON_CHAIN_IGNORE/ALWAYS/WHENAVAIL）：
        // gframe 用 setIsPushButton(true) 实现"推送式开关"，点击后 isPressed() 即为新状态；
        // 这里等价为翻转自身标志，并强制清掉另外两态（三态互斥），最后刷新按下态显示
        if (btnIgnoreTiming != null) {
            btnIgnoreTiming.setOnClickListener(v -> {
                playButtonSound();
                ignoreChain = !ignoreChain;
                alwaysChain = false;
                chainWhenAvail = false;
                updateChainButtons();
            });
        }
        if (btnShowTiming != null) {
            btnShowTiming.setOnClickListener(v -> {
                playButtonSound();
                alwaysChain = !alwaysChain;
                ignoreChain = false;
                chainWhenAvail = false;
                updateChainButtons();
            });
        }
        if (btnAvailableTiming != null) {
            btnAvailableTiming.setOnClickListener(v -> {
                playButtonSound();
                chainWhenAvail = !chainWhenAvail;
                alwaysChain = false;
                ignoreChain = false;
                updateChainButtons();
            });
        }
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> activity.showSettingsDialog());
        }
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> activity.toggleChatInput());
        }
        if (btnSound != null) {
            btnSound.setOnClickListener(v -> activity.toggleSoundMute());
        }
        // 速度开关（对齐 gframe imgQuickAnimation 点击切换 quick_animation）
        if (btnSpeed != null) {
            btnSpeed.setOnClickListener(v -> activity.toggleQuickAnimation());
        }
        // 表情入口（对齐 gframe BUTTON_EMOTICON）：开关切换 4x4 表情面板
        if (btnEmote != null) {
            final ImageButton emoteBtn = btnEmote;
            btnEmote.setOnClickListener(v -> activity.toggleEmotionDialog(emoteBtn));
        }
        // 日志入口（对齐 gframe imgLog 开关 wLogs）：切换决斗日志面板显示/隐藏
        if (btnNote != null) {
            btnNote.setOnClickListener(v -> activity.showDuelLogDialog());
        }

        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setOnClickListener(v -> cancelOrFinish());
        }

        // 洗切手卡（对齐 event_handler.cpp BUTTON_CMD_SHUFFLE L495-499）：点击即隐藏并应答 8，
        // 显隐由 MSG_SELECT_IDLECMD 的 show_shuffle 驱动（duelclient.cpp L1859-1865）；
        // 点击后同时把「通讯允许洗切」标志置假，避免后续 cancelOrFinish 切换时误恢复
        if (btnShuffleHand != null) {
            btnShuffleHand.setOnClickListener(v -> {
                updateShuffleButton(false);
                activity.sendResponseInt(8);
            });
        }

        if (btnReplayPlay != null) {
            btnReplayPlay.setOnClickListener(v -> {
                ReplayPlayer rp = replayPlayer();
                if (rp != null) rp.resume();
            });
        }
        if (btnReplayPause != null) {
            btnReplayPause.setOnClickListener(v -> {
                ReplayPlayer rp = replayPlayer();
                if (rp != null) rp.pause();
            });
        }
        if (btnReplayNext != null) {
            btnReplayNext.setOnClickListener(v -> {
                ReplayPlayer rp = replayPlayer();
                if (rp != null) rp.skipAhead();
            });
        }
        if (btnReplayLast != null) {
            btnReplayLast.setOnClickListener(v -> {
                ReplayPlayer rp = replayPlayer();
                if (rp != null) rp.undo();
            });
        }
        if (btnReplayShuffle != null) {
            btnReplayShuffle.setOnClickListener(v -> {
                // 观战：实况管线视角交换（DuelClient::SwapField 观战分支）；回放：ReplayPlayer 暂停态交换
                if (spectatorMode) {
                    activity.onSpectatorSwapField();
                    return;
                }
                ReplayPlayer rp = replayPlayer();
                if (rp != null) rp.swapField();
            });
        }
        if (btnReplayQuit != null) {
            btnReplayQuit.setOnClickListener(v -> {
                // 观战：退出观战断开连接回局域网主界面；回放：退出录像播放
                if (spectatorMode) activity.quitSpectator();
                else activity.quitReplay();
            });
        }
    }

    /** 当前回放播放器（GameEngine 协作件，恒存在；仅在真正有回放会话时返回） */
    private ReplayPlayer replayPlayer() {
        ReplayPlayer rp = activity.getReplayPlayer();
        return rp != null && rp.hasActiveSession() ? rp : null;
    }

    // === 卡片详情面板（实现已平移至 CardInfoBinder，以下为签名不变的薄委托） ===

    public void showCardInfo(GameField.ClientCard card) {
        binder.showCardInfo(card);
    }

    public void showDefault() {
        binder.showDefault();
    }

    public void showCard(GameField.ClientCard clientCard) {
        binder.showCard(clientCard);
    }

    public void showCard(Card card) {
        binder.showCard(card);
    }

    public void hide() {
        binder.setCurrentCardCode(-1);
        // 面板隐藏同时收起关键词卡片列表弹窗，避免残留遮挡
        binder.dismissKeywordListDialog();
        if (layout != null) {
            layout.setVisibility(View.GONE);
        }
        // 同时收起 include 根，修复横屏下 PlayerWaitingDialog 进入时左侧侧栏图标仍默认可见
        if (panelRoot != null) {
            panelRoot.setVisibility(View.GONE);
        }
    }

    public boolean isShowing() {
        return layout != null && layout.getVisibility() == View.VISIBLE;
    }

    /** 旋转重建后回显卡片详情（委托 CardInfoBinder；savedCode 为重建前快照的卡码，无则 <=0） */
    public void restoreAfterRebind(boolean wasShowingBeforeRebind, int savedCode) {
        binder.restoreAfterRebind(wasShowingBeforeRebind, savedCode);
    }

    public int getCurrentCardCode() {
        return binder.getCurrentCardCode();
    }

    public void closeGameButtons() {
        // 对齐 game.cpp Game::CloseGameButtons() L2395-2400：隐藏时点按钮、取消/完成与洗切手卡按钮
        hideChainButtons();
        hideCancelOrFinishButton();
        updateShuffleButton(false);
        // 决斗结束隐藏按钮行用 INVISIBLE 而非 GONE（用户规格）：竖屏顶部按钮行为 weight 布局，
        // GONE 会整行塌陷/其他控件跳位，INVISIBLE 保持占位稳定
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.INVISIBLE);
    }

    // === 时点按钮 (对应 C++ btnChainIgnore / btnChainAlways / btnChainWhenAvail) ===

    /** 显示时点（gframe always_chain）：供 ShowDialogUtil 在 MSG_SELECT_CHAIN 中判定自动应答 */
    public boolean isAlwaysChain() {
        return alwaysChain;
    }

    /** 忽略时点（gframe ignore_chain） */
    public boolean isIgnoreChain() {
        return ignoreChain;
    }

    /** 可用时点（gframe chain_when_avail） */
    public boolean isChainWhenAvail() {
        return chainWhenAvail;
    }

    /**
     * 刷新三个时点按钮的按下态（对齐 event_handler.cpp L2874-2880 ClientField::UpdateChainButtons）。
     * drawable/button3_bg.xml 中 state_selected 与 state_pressed 同为 @drawable/sbutton_p，
     * 因此 setSelected(flag) 等价于 gframe 的
     * ChangeToIGUIImageButton(btn, tButton_S, tButton_S_pressed) + setPressed(flag)
     */
    public void updateChainButtons() {
        if (btnIgnoreTiming != null) btnIgnoreTiming.setSelected(ignoreChain);
        if (btnShowTiming != null) btnShowTiming.setSelected(alwaysChain);
        if (btnAvailableTiming != null) btnAvailableTiming.setSelected(chainWhenAvail);
    }

    /** 按钮文字取自 strings.conf（对齐 game.cpp L1343/1344/1345/1360/1346/1347 的录像控制按钮） */
    private void applyReplayButtonTitles() {
        mStringManager = DataManager.get().getStringManager();
        if (btnReplayPlay != null)
            btnReplayPlay.setText(mStringManager.getSystemString(1343, "播放"));
        if (btnReplayPause != null)
            btnReplayPause.setText(mStringManager.getSystemString(1344, "暂停"));
        if (btnReplayNext != null)
            btnReplayNext.setText(mStringManager.getSystemString(1345, "下一步"));
        if (btnReplayLast != null)
            btnReplayLast.setText(mStringManager.getSystemString(1360, "上一步"));
        if (btnReplayShuffle != null)
            btnReplayShuffle.setText(mStringManager.getSystemString(1346, "切换视角"));
        if (btnReplayQuit != null)
            btnReplayQuit.setText(mStringManager.getSystemString(1347, "退出"));
    }

    /** 按钮文字取自 strings.conf（对齐 game.cpp L1348/1350/1352 的 GetSysString(1292/1293/1294)） */
    private void applyChainButtonTitles() {
        mStringManager = DataManager.get().getStringManager();
        if (btnIgnoreTiming != null) {
            btnIgnoreTiming.setText(mStringManager.getSystemString(1292, "忽略时点"));
        }
        if (btnShowTiming != null) {
            btnShowTiming.setText(mStringManager.getSystemString(1293, "显示时点"));
        }
        if (btnAvailableTiming != null) {
            btnAvailableTiming.setText(mStringManager.getSystemString(1294, "可用时点"));
        }
    }

    /**
     * MSG_NEW_TURN 时显示时点按钮（对齐 duelclient.cpp L2865-2877）：
     * control_mode == 0 → 显示三键并刷新按下态；否则隐藏三键与取消/完成按钮
     */
    public void showChainButtons() {
        if (AppsSettings.get().getIntSettings("control_mode", 0) != 0) {
            hideChainButtons();
            hideCancelOrFinishButton();
            return;
        }
        applyChainButtonTitles();
        if (btnIgnoreTiming != null) btnIgnoreTiming.setVisibility(View.VISIBLE);
        if (btnShowTiming != null) btnShowTiming.setVisibility(View.VISIBLE);
        if (btnAvailableTiming != null) btnAvailableTiming.setVisibility(View.VISIBLE);
        updateChainButtons();
    }

    /**
     * 隐藏时点按钮。用 INVISIBLE 而非 GONE（与本布局 btn_shuffle_hand 一致）：
     * layout_bottom_actions 是 weight 布局，GONE 会让"投降"按钮尺寸跳变
     */
    public void hideChainButtons() {
        if (btnIgnoreTiming != null) {
            btnIgnoreTiming.setSelected(false);
            btnIgnoreTiming.setVisibility(View.INVISIBLE);
        }
        if (btnShowTiming != null) {
            btnShowTiming.setSelected(false);
            btnShowTiming.setVisibility(View.INVISIBLE);
        }
        if (btnAvailableTiming != null) {
            btnAvailableTiming.setSelected(false);
            btnAvailableTiming.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * STOC_GAME_START 时初始化时点三态（对齐 duelclient.cpp L912-916）：
     * 勾选「开局默认显示所有时点」(chkDefaultShowChain) → always_chain = true，另两态清零。
     * 未勾选时保持原值不清零，与 gframe 一致（Game 成员跨局保留，
     * 下一局首个 MSG_NEW_TURN 会由 UpdateChainButtons 还原按下态显示）
     */
    public void onDuelStarted() {
        if (AppsSettings.get().getIntSettings("chkDefaultShowChain", 0) == 1) {
            alwaysChain = true;
            ignoreChain = false;
            chainWhenAvail = false;
        }
        updateChainButtons();
    }

    /** 对齐 gframe 各 BUTTON_* handler 开头的 PlaySoundEffect(SoundManager::SFX::BUTTON) */
    private void playButtonSound() {
        SoundManager soundManager = activity.getSoundManager();
        if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
    }

    // === 取消或完成按钮 (对应 C++ ClientField::CancelOrFinish) ===

    // 选择上下文注册（由 YGOProActivity 在创建/关闭选择对话框时同步）
    public void setSelectType(int selectType) {
        this.currentSelectType = selectType;
    }

    public int getSelectType() {
        return currentSelectType;
    }

    public void setCurrentDialog(YesOrNoDialog dialog) {
        this.currentDialog = dialog;
    }

    public void setCardSelectDialog(CardSelectDialog dialog) {
        this.cardSelectDialog = dialog;
    }

    public void setCardDisplayDialog(CardDisplayDialog dialog) {
        this.cardDisplayDialog = dialog;
    }

    public void showCancelOrFinishButton(String text) {
        setCancelOrFinishShown(true, text);
    }

    /**
     * 隐藏完成选择/取消按钮：用 INVISIBLE 而非 GONE（同 hideChainButtons）——
     * layout_bottom_actions 为 weight 布局，GONE 会让其余按钮尺寸跳变
     */
    public void hideCancelOrFinishButton() {
        setCancelOrFinishShown(false, null);
    }

    /**
     * cancelOrFinish 可见态统一入口：切换按钮显隐、同步蚂蚁线高亮、并联动洗切手卡按钮显隐
     *（显示时暂时隐藏洗切手卡，隐藏后若通讯允许则恢复）。
     */
    private void setCancelOrFinishShown(boolean visible, String text) {
        if (btnCancelOrFinish == null) return;
        if (text != null) btnCancelOrFinish.setText(text);
        cancelOrFinishShown = visible;
        btnCancelOrFinish.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        updateAntsHighlight();
        applyShuffleVisibility();
    }

    /** cancelOrFinish 可见则挂上并启动蚂蚁线，否则停止并移除 */
    private void updateAntsHighlight() {
        if (btnCancelOrFinish == null) return;
        if (cancelOrFinishShown) {
            if (antsHighlight == null) {
                float density = activity.getResources().getDisplayMetrics().density;
                antsHighlight = new MarchingAntsDrawable(density);
            }
            btnCancelOrFinish.setForeground(antsHighlight);
            antsHighlight.start();
        } else {
            if (antsHighlight != null) antsHighlight.stop();
            btnCancelOrFinish.setForeground(null);
        }
    }

    /**
     * 洗切手卡按钮显隐（对齐 duelclient.cpp MSG_SELECT_IDLECMD L1859-1865：
     * show_shuffle → btnShuffle->setVisible(true/false)）；记录通讯允许态，
     * 实际可见性另受 cancelOrFinish 是否显示约束（见 applyShuffleVisibility）
     */
    public void updateShuffleButton(boolean visible) {
        shuffleAllowedByMsg = visible;
        applyShuffleVisibility();
    }

    /** 洗切手卡可见 = 通讯允许 且 cancelOrFinish 未显示；均用 INVISIBLE 保持 weight 布局稳定 */
    private void applyShuffleVisibility() {
        if (btnShuffleHand != null) {
            btnShuffleHand.setVisibility(shuffleAllowedByMsg && !cancelOrFinishShown
                    ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void updateCancelOrFinishButton(boolean ready, boolean cancelable, boolean hasSelection) {
        if (btnCancelOrFinish == null) return;
        mStringManager = DataManager.get().getStringManager();
        if (ready) {
            // 完成选择（对齐 game.cpp 1296）
            setCancelOrFinishShown(true, mStringManager.getSystemString(1296, "完成选择"));
        } else if (cancelable && !hasSelection) {
            // 取消操作（对齐 game.cpp 1295）
            setCancelOrFinishShown(true, mStringManager.getSystemString(1295, "取消操作"));
        } else {
            setCancelOrFinishShown(false, null);
        }
    }

    // 对齐 C++ ClientField::CancelOrFinish：按当前选择类型执行完成/取消
    public void cancelOrFinish() {
        // 注：selectType 12/13（EFFECTYN/YESNO 是/否询问）不在此列——对齐 gframe wQuery，
        // 该询问只有弹窗内「是/否」两键，cancelOrFinish 按钮不参与也不显示
        switch (currentSelectType) {
            case 15:
            case 20: {
                if (cardSelectDialog != null) {
                    if (cardSelectDialog.getSelectedCount() >= cardSelectDialog.getMinSelect()) {
                        cardSelectDialog.confirm();
                    } else if (cardSelectDialog.isCancelable() && cardSelectDialog.getSelectedCount() == 0) {
                        activity.sendResponseInt(-1);
                        hideCancelOrFinishButton();
                        cardSelectDialog.dismiss();
                    }
                } else {
                    // 场上/手牌直接选择模式（无弹窗）：由 GameFieldController 完成/取消
                    GameFieldController fieldCtl = activity.getFieldCtl();
                    if (fieldCtl != null && fieldCtl.finishCardSelect()) {
                        hideCancelOrFinishButton();
                    }
                }
                break;
            }
            case 23: {
                if (cardSelectDialog != null) {
                    if (cardSelectDialog.isReady()) {
                        cardSelectDialog.confirm();
                    }
                } else {
                    // 场上/手牌合计选择模式（无弹窗）：selectReady 时由 GameFieldController 应答（C++ L3156-3158）
                    GameFieldController fieldCtl = activity.getFieldCtl();
                    if (fieldCtl != null && fieldCtl.finishCardSelect()) {
                        hideCancelOrFinishButton();
                    }
                }
                break;
            }
            case 26: {
                // event_handler.cpp L968-971：UNSELECT 的完成/取消按钮 = 发送 -1
                if (cardSelectDialog == null) {
                    // 场上/手牌直接选择模式（无弹窗，连接手续逐步选素材）：
                    // 应答 -1 并清理场上会话（finishCardSelect 内走 UNSELECT 分支→cancelCardSelect）
                    GameFieldController fieldCtl = activity.getFieldCtl();
                    if (fieldCtl != null && fieldCtl.finishCardSelect()) {
                        hideCancelOrFinishButton();
                    }
                    break;
                }
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                cardSelectDialog.dismiss();
                break;
            }
            case 27: {
                hideCancelOrFinishButton();
                if (cardDisplayDialog != null) cardDisplayDialog.dismiss();
                break;
            }
            case 16: {
                // 连锁（MSG_SELECT_CHAIN）取消：优先按 wQuery 语义处理——场上点击模式下点击「取消操作」
                // 重新弹出询问窗（对齐 CancelOrFinish 的 PopupElement(wQuery)），实现「暂时隐藏」后可恢复；
                // 无询问窗（panelmode 列表）时回退到直接应答 -1。handleChainCancel 内部自行管理 selectType，
                // 故此处直接 return，跳过方法末尾的 currentSelectType=-1 复位
                ShowDialogUtil du = activity.getDialogUtil();
                if (du != null && du.handleChainCancel()) {
                    return;
                }
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 25: {
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 18:
            case 24: {
                GameFieldController fieldCtl = activity.getFieldCtl();
                if (fieldCtl != null && fieldCtl.cancelPlaceSelect()) {
                    hideCancelOrFinishButton();
                }
                break;
            }
            default: {
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
        }
        currentSelectType = -1;
    }

    // === 整体可见性 ===
    
    public void stopAntsHighlightImmediately() {
        if (antsHighlight != null) {
            antsHighlight.stop();
            btnCancelOrFinish.setForeground(null);
            antsHighlight = null;
        }
    }

    public void onGameUIShown() {
        // 每次进入决斗/回放 UI 先清掉上一场残留的时点三键/洗切手卡/录像控制条，
        // 后续通讯状态（DUELING / 回放 PLAYING / MSG_NEW_TURN）会重新点亮，
        // 修复「看完录像再决斗（或反之）一开头显示不该出现的按钮」
        hideChainButtons();
        updateShuffleButton(false);
        hideReplayControls();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
        // 投降按钮默认隐藏：猜拳(HAND_SELECT)/选先后(TP_SELECT)阶段不显示，
        // 进入决斗(DUELING)且为对战玩家时由 Activity 调用 setSurrenderVisible(true) 显示
        //（对齐 gframe：btnLeaveGame 在 STOC_DUEL_START 前不出现，观战者不显示投降）
        setSurrenderVisible(false);
        showDefault();
        hideCancelOrFinishButton();
    }

    /**
     * 控制投降按钮显隐。使用 INVISIBLE 而非 GONE（与本布局链式按钮一致）：
     * layout_bottom_actions 为 weight 布局，GONE 会让其它按钮尺寸跳变
     */
    public void setSurrenderVisible(boolean visible) {
        if (btnSurrender != null)
            btnSurrender.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    // 撤回图标的显隐/闪动已下沉到 {@code GameTopInfoManager.setUndoPrompt}（顶部回合数下方）

    public void onGameUIHidden() {
        hide();
        spectatorMode = false;
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        hideCancelOrFinishButton();
    }

    /**
     * 统一关闭当前打开的选择/确认/展示对话框（断线或决斗结束时清理），
     * 避免残留弹窗遮挡重新显示的局域网主界面
     */
    public void dismissOpenDialogs() {
        if (currentDialog != null) currentDialog.dismiss();
        if (cardSelectDialog != null) cardSelectDialog.dismiss();
        if (cardDisplayDialog != null) cardDisplayDialog.dismiss();
        currentDialog = null;
        cardSelectDialog = null;
        cardDisplayDialog = null;
        currentSelectType = -1;
        hideCancelOrFinishButton();
    }

    public void showBottomActions() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
    }

    // === 卡组编辑器模式 ===

    public void enterDeckEditorMode() {
        if (btnNote != null) btnNote.setVisibility(View.INVISIBLE);
        if (btnSpeed != null) btnSpeed.setVisibility(View.INVISIBLE);
        if (btnEmote != null) btnEmote.setVisibility(View.INVISIBLE);
        // btnChat 不再在此直接隐藏：聊天开关由 YGOProActivity.updateChatUIVisibility
        // 集中按场景核算（卡组编辑属抑制场景），避免 enter/exit 与集中控制互相覆盖
        showDefault();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);
    }

    public void exitDeckEditorMode() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        hide();
        if (btnNote != null) btnNote.setVisibility(View.VISIBLE);
        if (btnSpeed != null) btnSpeed.setVisibility(View.VISIBLE);
        if (btnEmote != null) btnEmote.setVisibility(View.VISIBLE);
        // btnChat 同 enterDeckEditorMode：由集中核算按退出后的实际场景重定显隐
    }

    /** 副卡组替换模式下隐藏卡组编辑控制栏（洗牌/排序/清空/删除/退出按钮区） */
    public void hideDeckControl() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
    }

    /** 退出副卡组替换模式后恢复卡组编辑控制栏 */
    public void showDeckControl() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);
    }

    // === 录像控制条 ===

    public void showReplayControls() {
        spectatorMode = false;
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.VISIBLE);
        if (btnReplayShuffle != null) btnReplayShuffle.setVisibility(View.VISIBLE);
        if (btnReplayQuit != null) btnReplayQuit.setVisibility(View.VISIBLE);
        // 初始状态：正在播放（显示暂停，隐藏播放/上一步/下一步）
        updateReplayButtonStates(false);
    }

    /**
     * 观战控制面板（对齐 event_handler.cpp：观战无行动/时点权限，btnSwapY 与退出可用）：
     * 隐藏底部行动区与时点三键，左侧面板常驻录像控制条但只显示「切换视角/退出」，
     * 播控四键（播放/暂停/上一步/下一步）保持 INVISIBLE 占位不跳变
     */
    public void showSpectatorControls() {
        spectatorMode = true;
        hideChainButtons();
        hideCancelOrFinishButton();
        updateShuffleButton(false);
        setSurrenderVisible(false);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl == null) return;
        layoutReplayControl.setVisibility(View.VISIBLE);
        if (btnReplayPlay != null) btnReplayPlay.setVisibility(View.INVISIBLE);
        if (btnReplayPause != null) btnReplayPause.setVisibility(View.INVISIBLE);
        if (btnReplayNext != null) btnReplayNext.setVisibility(View.INVISIBLE);
        if (btnReplayLast != null) btnReplayLast.setVisibility(View.INVISIBLE);
        if (btnReplayShuffle != null) btnReplayShuffle.setVisibility(View.VISIBLE);
        if (btnReplayQuit != null) btnReplayQuit.setVisibility(View.VISIBLE);
    }

    /** 当前是否处于观战控制面板模式 */
    public boolean isSpectatorMode() {
        return spectatorMode;
    }

    /**
     * 录像控制按钮互斥显隐：播放中显示暂停按钮、隐藏播放/上一步/下一步；
     * 暂停时隐藏暂停、显示播放/上一步/下一步。切换时使用 INVISIBLE 不破坏布局。
     * 对齐 replay_mode.cpp 中 pause 状态下 UI 元素可用性逻辑。
     */
    public void updateReplayButtonStates(boolean isPaused) {
        if (btnReplayPause != null)
            btnReplayPause.setVisibility(isPaused ? View.INVISIBLE : View.VISIBLE);
        if (btnReplayPlay != null)
            btnReplayPlay.setVisibility(isPaused ? View.VISIBLE : View.INVISIBLE);
        if (btnReplayLast != null)
            btnReplayLast.setVisibility(isPaused ? View.VISIBLE : View.INVISIBLE);
        if (btnReplayNext != null)
            btnReplayNext.setVisibility(isPaused ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * 隐藏录像控制条。不再顺手把 layoutBottomActions 置 VISIBLE：底部按钮区的可见性
     * 由 onGameUIShown / showBottomActions / closeGameButtons 按场景统一管控，
     * 避免退出回放瞬间露出不该出现的投降/时点等按钮
     */
    public void hideReplayControls() {
        spectatorMode = false;
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
    }

}
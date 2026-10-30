package cn.garymb.ygomobile.render.card;
import cn.garymb.ygomobile.game.gamefield.EffectActivatableHints;
import cn.garymb.ygomobile.render.field.TextureLoader;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.EffectActivationLedger;
import cn.garymb.ygomobile.game.gamefield.GameFieldController;
import cn.garymb.ygomobile.game.replay.ReplayPlayer;
import cn.garymb.ygomobile.game.select.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.dialogs.duel.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;

/**
 * 左侧卡片详情面板与全部控制按钮的管理类：
 * 卡片详情展示 / 左列功能按钮 / 底部行动按钮 / 录像控制按钮 / 取消或完成按钮 / 卡组操作栏
 */
public class CardDetailPanel extends CardDetailPanelMid {

    public CardDetailPanel(YGOProActivity activity) {
        super(activity);
    }

    /** 发动账本变更通知（引擎派发线程在 MSG_CHAINING 登记/MSG_NEW_TURN 回合推进/MSG_START、
     * MSG_WIN 清零后调用）：若卡详正在展示同一张卡则及时重绑描述段落标记文本，
     * 无需玩家重新点卡；实现下沉到 CardInfoBinder.notifyLedgerChanged（本类是包外可见入口） */
    public static void refreshCardDetailMarks() {
        CardInfoBinder.notifyLedgerChanged();
    }

    /** 效果发动的门控刷新入口（MSG_CHAINING 登记后调用，用户规格2）：只有发动卡与正在
     *  展示卡同码/同 alias（异画与本卡）才重绑标记，其它卡码的发动不打扰面板，
     *  避免每次发动都走一遍后台高亮/关联卡重建 */
    public static void refreshCardDetailMarks(int activatedCode) {
        CardInfoBinder.notifyLedgerChanged(activatedCode);
    }

    /** 视角切换统一入口（观战切视角/单人模式自动翻转/回放交换，用户规格3）：
     *  发动账本与可发动登记的半场归属随视角整体对调，展示中卡的绿/红勾与
     *  绿/橙发光同步互换（纯单人逐回合自动翻转下不翻转会使自己方的可发动条目被记到对面） */
    public static void notifyViewpointSwapped() {
        EffectActivationLedger.swapSides();
        EffectActivatableHints.swapSides();
        CardInfoBinder.notifyViewpointSwapped();
    }

    public void bindViews() {
        layout = activity.findViewById(R.id.layout_card_detail);
        panelRoot = activity.findViewById(R.id.layout_card_detail_panel);
        btnRelatedCards = activity.findViewById(R.id.btn_related_cards);
        // 初始统一收起为 INVISIBLE（用户规格：不再 GONE，恒定占位避免描述区高度逐卡跳动），
        // 显隐交由 binder 随当前卡是否有关联卡刷新（updateRelatedButtonVisibility）
        if (btnRelatedCards != null) btnRelatedCards.setVisibility(View.INVISIBLE);
        // 关联卡按钮引用注入 binder：由卡详绑定统一管控显隐（仅当前卡有关联卡时显示）
        binder.setRelatedButton(btnRelatedCards);
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

        btnSettings = findSideButton(R.id.btn_settings);
        btnChat = findSideButton(R.id.btn_chat);
        btnSound = findSideButton(R.id.btn_sound);
        btnSpeed = findSideButton(R.id.btn_speed);
        btnEmote = findSideButton(R.id.btn_emote);
        btnNote = findSideButton(R.id.btn_note);

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
        layoutSpectatorControl = activity.findViewById(R.id.layout_spectator_control);
        btnSpectatorSkip = activity.findViewById(R.id.btn_spectator_skip);
        btnSpectatorSwap = activity.findViewById(R.id.btn_spectator_swap);
        btnSpectatorQuit = activity.findViewById(R.id.btn_spectator_quit);
        layoutDeckControl = activity.findViewById(R.id.layout_deck_control);

        // 录像控制按钮文字复用 gframe 既有系统字符串（对齐 game.cpp wReplayControl）：
        // 播放 1343 / 暂停 1344 / 下一步 1345 / 上一步 1360 / 切换视角 1346 / 退出 1347
        applyReplayButtonTitles();
        // 观战控制集文字：切换视角 1346 / 退出 1347 复用 gframe 系统字符串，
        // 跳到当前无对应索引，走 XML 的 @string/spectator_skip_to_current（需求5用户指定）
        applySpectatorButtonTitles();
        // 时点三键文字在此先行着色（对齐 game.cpp 1292/1293/1294），MSG_NEW_TURN 的 showChainButtons 会再次刷新
        applyChainButtonTitles();

        // 对齐 game.cpp L1357-1359：三个时点按钮创建后默认隐藏，MSG_NEW_TURN 时才显示
        hideChainButtons();

        setupListeners();
    }

    protected void setupListeners() {
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

        // 关联卡片入口（横竖屏同 ID 的 btn_related_cards 图标按钮）：点击交由 binder 计算关联卡
        // 并按场景分流（卡组编辑态填搜索结果列表、决斗态弹 KeywordCardListDialog）；
        // list_item_bg 背景随 pressed 态自绘按压反馈，无需额外 setBackgroundResource
        if (btnRelatedCards != null) {
            btnRelatedCards.setOnClickListener(v -> {
                playButtonSound();
                binder.showRelatedCards();
            });
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

        // 观战控制集（需求5）：与录像控制条同位互斥，仅观战时可见
        if (btnSpectatorSkip != null) {
            btnSpectatorSkip.setOnClickListener(v -> {
                playButtonSound();
                // 跳到当前：无视动画直接落到最后一条 msg 的决斗局面
                activity.onSpectatorSkipToCurrent();
            });
        }
        if (btnSpectatorSwap != null) {
            btnSpectatorSwap.setOnClickListener(v -> {
                playButtonSound();
                // 切换视角（与录像条 shuffle 观战分支一致，功能不变）
                activity.onSpectatorSwapField();
            });
        }
        if (btnSpectatorQuit != null) {
            btnSpectatorQuit.setOnClickListener(v -> {
                playButtonSound();
                // 退出观战（与录像条 quit 观战分支一致，功能不变）
                activity.quitSpectator();
            });
        }
    }
}

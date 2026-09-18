package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.game.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.utils.CardUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/**
 * 左侧卡片详情面板与全部控制按钮的管理类：
 * 卡片详情展示 / 左列功能按钮 / 底部行动按钮 / 录像控制按钮 / 取消或完成按钮 / 卡组操作栏
 */
public class CardDetailPanel {

    private final YGOProActivity activity;
    private ImageLoader imageLoader;

    private LinearLayout layout;
    private ImageView ivCardImage;
    private TextView tvCardName, tvCardSetname, tvCardAttr, tvCardLevel, tvCardDesc;
    private ScrollView svCardDesc;

    private ImageButton btnSettings, btnChat, btnSound, btnSpeed, btnEmote, btnNote;

    private LinearLayout layoutBottomActions;
    private Button btnSurrender, btnIgnoreTiming, btnShowTiming, btnAvailableTiming;
    private Button btnCancelOrFinish;

    private LinearLayout layoutReplayControl;
    private Button btnReplayPlay, btnReplayPause, btnReplayNext, btnReplayLast, btnReplayShuffle, btnReplayQuit;
    private LinearLayout layoutDeckControl;

    private int currentCardCode = -1;
    private Bitmap coverBitmap;
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

    public CardDetailPanel(YGOProActivity activity) {
        this.activity = activity;
    }

    public void bindViews() {
        layout = activity.findViewById(R.id.layout_card_detail);
        ivCardImage = activity.findViewById(R.id.iv_card_image);
        tvCardName = activity.findViewById(R.id.tv_card_name);
        tvCardSetname = activity.findViewById(R.id.tv_card_setname);
        tvCardAttr = activity.findViewById(R.id.tv_card_attr);
        tvCardLevel = activity.findViewById(R.id.tv_card_level);
        tvCardDesc = activity.findViewById(R.id.tv_card_desc);
        svCardDesc = activity.findViewById(R.id.sv_card_desc);

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
        btnCancelOrFinish = activity.findViewById(R.id.btn_cancel_or_finish);

        layoutReplayControl = activity.findViewById(R.id.layout_replay_control);
        btnReplayPlay = activity.findViewById(R.id.btn_replay_play);
        btnReplayPause = activity.findViewById(R.id.btn_replay_pause);
        btnReplayNext = activity.findViewById(R.id.btn_replay_next);
        btnReplayLast = activity.findViewById(R.id.btn_replay_last);
        btnReplayShuffle = activity.findViewById(R.id.btn_replay_shuffle);
        btnReplayQuit = activity.findViewById(R.id.btn_replay_quit);
        layoutDeckControl = activity.findViewById(R.id.layout_deck_control);

        // 对齐 game.cpp L1357-1359：三个时点按钮创建后默认隐藏，MSG_NEW_TURN 时才显示
        hideChainButtons();

        setupListeners();
    }

    public void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
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
        // 点击投降不再直接发送通讯：交由 Activity 弹 YesOrNoDialog 二次确认
        //（对齐 event_handler.cpp BUTTON_LEAVE_GAME → PopupElement(wSurrender) → BUTTON_SURRENDER_YES → CTOS_SURRENDER）
        btnSurrender.setOnClickListener(v -> {
            playButtonSound();
            activity.requestSurrender();
        });
        // 时点按钮（对齐 event_handler.cpp L297-320 BUTTON_CHAIN_IGNORE/ALWAYS/WHENAVAIL）：
        // gframe 用 setIsPushButton(true) 实现"推送式开关"，点击后 isPressed() 即为新状态；
        // 这里等价为翻转自身标志，并强制清掉另外两态（三态互斥），最后刷新按下态显示
        btnIgnoreTiming.setOnClickListener(v -> {
            playButtonSound();
            ignoreChain = !ignoreChain;
            alwaysChain = false;
            chainWhenAvail = false;
            updateChainButtons();
        });
        btnShowTiming.setOnClickListener(v -> {
            playButtonSound();
            alwaysChain = !alwaysChain;
            ignoreChain = false;
            chainWhenAvail = false;
            updateChainButtons();
        });
        btnAvailableTiming.setOnClickListener(v -> {
            playButtonSound();
            chainWhenAvail = !chainWhenAvail;
            alwaysChain = false;
            ignoreChain = false;
            updateChainButtons();
        });
        btnSettings.setOnClickListener(v -> activity.showSettingsDialog());
        btnChat.setOnClickListener(v -> activity.toggleChatInput());
        btnSound.setOnClickListener(v -> activity.toggleSoundMute());
        // 速度开关（对齐 gframe imgQuickAnimation 点击切换 quick_animation）
        btnSpeed.setOnClickListener(v -> activity.toggleQuickAnimation());
        // 表情入口（对齐 gframe BUTTON_EMOTICON）：开关切换 4x4 表情面板
        btnEmote.setOnClickListener(v -> activity.toggleEmotionDialog(btnEmote));
        // 日志入口（对齐 gframe imgLog 开关 wLogs）：切换决斗日志面板显示/隐藏
        btnNote.setOnClickListener(v -> activity.showDuelLogDialog());

        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setOnClickListener(v -> cancelOrFinish());
        }

        if (btnReplayPlay != null) {
            btnReplayPlay.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.resume();
            });
        }
        if (btnReplayPause != null) {
            btnReplayPause.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.pause();
            });
        }
        if (btnReplayNext != null) {
            btnReplayNext.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.skipAhead();
            });
        }
        if (btnReplayLast != null) {
            btnReplayLast.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.undo();
            });
        }
        if (btnReplayShuffle != null) {
            btnReplayShuffle.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.swapField();
            });
        }
        if (btnReplayQuit != null) {
            btnReplayQuit.setOnClickListener(v -> activity.quitReplay());
        }
    }

    // === 卡片详情面板 ===

    public void showCardInfo(GameField.ClientCard card) {
        // 移除 code<=0 检查：对于 CardSelectDialog 中的已知 code 的暗卡（里侧怪兽、卡组表侧等），
        // 只要能读取到卡片图案就应该显示详细信息，而不是当作未知卡处理
        if (card == null) return;
        showCard(card);
    }

    public void showDefault() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (ivCardImage != null) {
            Bitmap cover = getCoverBitmap();
            if (cover != null) {
                ivCardImage.setImageBitmap(cover);
            } else {
                ivCardImage.setImageResource(R.drawable.unknown);
            }
        }
        if (tvCardName != null) {
            tvCardName.setText("");
        }
        if (tvCardSetname != null) {
            tvCardSetname.setText("");
            tvCardSetname.setVisibility(View.GONE);
        }
        if (tvCardAttr != null) {
            tvCardAttr.setText("");
        }
        if (tvCardLevel != null) {
            tvCardLevel.setText("");
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText("");
        }
    }

    public void showCard(GameField.ClientCard clientCard) {
        // code<=0 视为未知/暗卡：对齐 event_handler.cpp L1130-1133 ClearCardInfo 显示卡背，
        // 而不是走 getCard(0) 失败后误报「???」；unknown 仅保留给 code>0 但卡表查无此卡
        if (clientCard == null || clientCard.code <= 0) {
            showDefault();
            return;
        }

        int code = clientCard.code;
        Card cardData = DataManager.get().getCardManager().getCard(code);
        if (cardData == null) {
            showUnknownCard();
            return;
        }

        currentCardCode = code;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }

        bindCardImage(code);
        bindCardName(cardData, code);
        bindCardSetname(cardData);
        bindCardAttr(cardData);
        // 详情面板只显示卡表原始数据（对齐 game.cpp Game::ShowCardInfo(int code) 全部取 cd 原值），
        // 通讯修改后的等级/属性/种族/攻守/刻度一律不进详情，差异在长按标签中显示
        bindCardLevel(cardData);
        bindCardDesc(cardData);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    public void showCard(Card card) {
        if (card == null || card.Code <= 0) {
            showUnknownCard();
            return;
        }

        //卡图使用card.Code对应的文件名id（异画卡显示自己的卡图，不受RealCode影响）
        int imageCode = card.Code;
        currentCardCode = imageCode;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }

        bindCardImage(imageCode);
        bindCardNameByGameCode(card);
        bindCardSetname(card);
        bindCardAttr(card);
        bindCardLevel(card);
        bindCardDesc(card);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    public void hide() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.GONE);
        }
    }

    public boolean isShowing() {
        return layout != null && layout.getVisibility() == View.VISIBLE;
    }

    private void showUnknownCard() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (ivCardImage != null) {
            ivCardImage.setImageResource(R.drawable.unknown);
        }
        if (tvCardName != null) {
            tvCardName.setText("???");
        }
        if (tvCardSetname != null) {
            tvCardSetname.setText("");
            tvCardSetname.setVisibility(View.GONE);
        }
        if (tvCardAttr != null) {
            tvCardAttr.setText("");
        }
        if (tvCardLevel != null) {
            tvCardLevel.setText("");
            tvCardLevel.setVisibility(View.GONE);
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText(R.string.tip_card_info_diff);
        }
        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    public int getCurrentCardCode() {
        return currentCardCode;
    }

    private void bindCardImage(int code) {
        if (imageLoader != null && ivCardImage != null) {
            imageLoader.bindImage(ivCardImage, code, ImageLoader.Type.origin);
        }
    }

    private void bindCardName(Card cardData, int code) {
        if (tvCardName == null) return;
        String name = cardData.Name;
        if (name == null || name.isEmpty()) name = "Unknown Card";
        tvCardName.setText(name + "[" + code + "]");
    }

    //卡名根据getGameCode()判断显示（规则同名卡显示本家卡名），卡图仍使用card.Code
    private void bindCardNameByGameCode(Card cardData) {
        if (tvCardName == null) return;
        int gameCode = cardData.getGameCode();
        Card gameCard = DataManager.get().getCardManager().getCard(gameCode);
        String name = (gameCard != null && gameCard.Name != null && !gameCard.Name.isEmpty())
                ? gameCard.Name : cardData.Name;
        if (name == null || name.isEmpty()) name = "Unknown Card";
        tvCardName.setText(name + "[" + gameCode + "]");
    }

    private void bindCardSetname(Card cardData) {
        if (tvCardSetname == null) return;
        mStringManager = DataManager.get().getStringManager();
        long[] setCodes = cardData.getSetCode();
        StringBuilder sb = new StringBuilder();
        boolean hasSet = false;
        for (long sc : setCodes) {
            if (sc == 0) continue;
            if (hasSet) sb.append("|");
            sb.append(mStringManager.getSetName(sc));
            hasSet = true;
        }
        if (hasSet) {
            tvCardSetname.setText("字段：" + sb);
            tvCardSetname.setVisibility(View.VISIBLE);
        } else {
            tvCardSetname.setVisibility(View.GONE);
        }
    }

    private void bindCardAttr(Card cardData) {
        if (tvCardAttr == null) return;
        mStringManager = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder();

        String typeStr = CardUtils.getAllTypeString(cardData, mStringManager).replace("/", "|");
        sb.append("[").append(typeStr).append("]");

        if (cardData.isType(CardType.Monster)) {
            String raceStr = mStringManager.getRaceString(cardData.Race);
            String attrStr = mStringManager.getAttributeString(cardData.Attribute);
            sb.append(" ").append(raceStr).append("/").append(attrStr);
        }

        tvCardAttr.setText(sb.toString());
    }

    private void bindCardLevel(Card cardData) {
        if (tvCardLevel == null) return;

        if (cardData.isType(CardType.Spell) || cardData.isType(CardType.Trap)) {
            tvCardLevel.setText("");
            tvCardLevel.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (cardData.isLink()) {
            sb.append("LINK-").append(cardData.getLinkNumber());
        } else if (cardData.isType(CardType.Xyz)) {
            sb.append("☆").append(cardData.getStar());
        } else if (cardData.isType(CardType.Monster)) {
            sb.append("★").append(cardData.getStar());
        }

        if (cardData.isType(CardType.Monster)) {
            int atk = cardData.Attack;
            int def = cardData.Defense;
            String atkStr = atk < 0 ? "?" : String.valueOf(atk);
            String defStr = cardData.isLink() ? "-" : (def < 0 ? "?" : String.valueOf(def));
            if (sb.length() > 0) sb.append("  ");
            sb.append(atkStr).append("/").append(defStr);
        }

        if (cardData.LeftScale > 0 || cardData.RightScale > 0) {
            int lsc = cardData.LeftScale;
            int rsc = cardData.RightScale;
            if (sb.length() > 0) sb.append("  ");
            sb.append("灵摆 ").append(lsc).append("/").append(rsc);
        }

        tvCardLevel.setText(sb.toString());
        tvCardLevel.setVisibility(View.VISIBLE);
    }

    private void bindCardDesc(Card cardData) {
        if (tvCardDesc == null) return;
        String desc = cardData.Desc;
        if (desc == null || desc.isEmpty()) {
            tvCardDesc.setText("");
        } else {
            tvCardDesc.setText(desc);
        }
    }

    public void closeGameButtons() {
        // 对齐 game.cpp Game::CloseGameButtons() L2395-2398：隐藏三个时点按钮与取消/完成按钮
        hideChainButtons();
        hideCancelOrFinishButton();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
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
        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setText(text);
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        }
    }

    public void hideCancelOrFinishButton() {
        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setVisibility(View.GONE);
        }
    }

    public void updateCancelOrFinishButton(boolean ready, boolean cancelable, boolean hasSelection) {
        if (btnCancelOrFinish == null) return;
        if (ready) {
            btnCancelOrFinish.setText("完成选择");
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        } else if (cancelable && !hasSelection) {
            btnCancelOrFinish.setText("取消");
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        } else {
            btnCancelOrFinish.setVisibility(View.GONE);
        }
    }

    // 对齐 C++ ClientField::CancelOrFinish：按当前选择类型执行完成/取消
    public void cancelOrFinish() {
        switch (currentSelectType) {
            case 13:
            case 12: {
                activity.sendResponseInt(0);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
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
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (cardSelectDialog != null) cardSelectDialog.dismiss();
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

    public void onGameUIShown() {
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

    public void onGameUIHidden() {
        hide();
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
        if (btnChat != null) btnChat.setVisibility(View.INVISIBLE);
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
        if (btnChat != null) btnChat.setVisibility(View.VISIBLE);
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
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.VISIBLE);
    }

    public void hideReplayControls() {
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
    }

    private Bitmap getCoverBitmap() {
        if (coverBitmap == null || coverBitmap.isRecycled()) {
            File coverFile = new File(AppsSettings.get().getCoreSkinPath(), "cover.jpg");
            if (coverFile.exists()) {
                coverBitmap = BitmapFactory.decodeFile(coverFile.getAbsolutePath());
            }
        }
        return coverBitmap;
    }
}
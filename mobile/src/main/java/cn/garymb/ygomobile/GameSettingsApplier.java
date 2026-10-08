package cn.garymb.ygomobile;

import android.view.View;

import cn.garymb.ygomobile.audio.SoundManager;

/**
 * === 已保存设置到各功能件的应用与快捷开关（对齐 gframe game.cpp LoadConfig /
 * event_handler.cpp imgVol、imgQuickAnimation 点击切换）===
 * 自 YGOProActivity 拆出：音频开关与音量、卡片详情面板侧栏图标（声音 / 速度 / 聊天）、
 * 动画速度（场上卡片与居中特效两套）、禁限卡表刷新统一经本类应用；
 * 对外入口仍是 {@link YGOProActivity#applySettingsToEngine()} 等门面方法。
 */
class GameSettingsApplier {

    // === 动画速度（对齐 gframe gameConf.quick_animation：WaitFrameSignal 截半、appear 12/20，≈ 2 倍速） ===

    /** 基础动画速度倍率（quick_animation 关闭） */
    private static final float ANIM_SPEED_NORMAL = 1f;
    /** 加速动画速度倍率（quick_animation 开启，C++ 等待帧数截半的等价实现） */
    private static final float ANIM_SPEED_QUICK = 2f;

    private final YGOProActivity activity;

    GameSettingsApplier(YGOProActivity activity) {
        this.activity = activity;
    }

    /**
     * 按 AppsSettings 当前值统一应用全部已保存设置（启动初始化与设置对话框变更均经此）
     */
    void apply() {
        AppsSettings appsSettings = AppsSettings.get();
        boolean enableSound = appsSettings.getIntSettings("chkEnableSound", 1) == 1;
        boolean enableMusic = appsSettings.getIntSettings("chkEnableMusic", 1) == 1;
        SoundManager soundManager = activity.soundManager;
        if (soundManager != null) {
            soundManager.enableSounds(enableSound);
            soundManager.enableMusic(enableMusic);
            soundManager.setSoundVolume(appsSettings.getIntSettings("soundVolume", 50) / 100.0);
            soundManager.setMusicVolume(appsSettings.getIntSettings("musicVolume", 50) / 100.0);
            soundManager.setMusicMode(appsSettings.getIntSettings("chkSwitchBGM", 1) == 1);
            // 音频开关/场景切换模式变更后立即重算场景：勾选「随场景切换」即时切曲，
            // 重新启用音乐时恢复播放（同场景由 SoundManager 去重不重复起曲）
            activity.updateBGM();
        }
        if (activity.cardDetailPanel != null) {
            // 对齐 gframe imgVol/imgQuickAnimation：声音与速度按钮图标同步设置状态
            activity.cardDetailPanel.updateSoundIcon(enableSound || enableMusic);
            activity.cardDetailPanel.updateSpeedIcon(appsSettings.getIntSettings("chkQuickAnimation", 0) == 1);
            // 对齐 gframe BUTTON_CHATTING：聊天按钮图标同步停用聊天设置；
            // 输入框与开关可见性交集中场景核算（停用设置 + 卡组编辑/录像/残局抑制场景）
            activity.cardDetailPanel.updateChatIcon(
                    appsSettings.getIntSettings("chkDisableChatting", 0) == 1);
            activity.updateChatUIVisibility();
        }
        // 用户规格：停用聊天要隐藏聊天信息。经开关按钮停用已在 toggleChatInput 回调清屏，
        // 经设置对话框停用则在此补清已显示的聊天行（新消息的丢弃由 FieldChatBoard 承担）
        if (appsSettings.getIntSettings("chkDisableChatting", 0) == 1 && activity.fieldCtl != null) {
            activity.fieldCtl.clearChatMessages();
        }
        // 动画速度随 chkQuickAnimation 即时生效（启动初始化与设置对话框变更均经此）
        applyAnimationSpeed();
        if (activity.deckEditorManager != null) {
            activity.deckEditorManager.refreshLimitList();
        }
    }

    void toggleSoundMute() {
        SoundManager soundManager = activity.soundManager;
        if (soundManager == null) return;
        // 对齐 gframe imgVol 开关：走 AppsSettings 保存（与 SettingsDialog 的
        // chkEnableSound/chkEnableMusic 同一存储），避免设置对话框与声音按钮脱节
        AppsSettings settings = AppsSettings.get();
        boolean currentSound = settings.getIntSettings("chkEnableSound", 1) == 1;
        boolean currentMusic = settings.getIntSettings("chkEnableMusic", 1) == 1;
        boolean muted = currentSound || currentMusic;
        settings.saveIntSettings("chkEnableSound", muted ? 0 : 1);
        settings.saveIntSettings("chkEnableMusic", muted ? 0 : 1);
        soundManager.enableSounds(!muted);
        soundManager.enableMusic(!muted);
        if (activity.cardDetailPanel != null) activity.cardDetailPanel.updateSoundIcon(!muted);
        // 恢复音乐后必须重算场景重新起播：enableMusic 仅恢复开关标志，不会自行选曲，
        // 缺此调用则点击静音再点开声音后 BGM 不再播放（对齐 apply() 里同名的 updateBGM 收尾）。
        // updateBGM 依 BgmSceneController 按当前场景（决斗/优势/劣势/胜利/失败/卡组/菜单）选曲；
        // 静音态下调用无副作用，playBGM 首行 !musicEnabled 直接返回
        activity.updateBGM();
    }

    /**
     * 决斗速度开关（对齐 gframe imgQuickAnimation 点击切换 quick_animation 并保存）
     */
    void toggleQuickAnimation() {
        AppsSettings settings = AppsSettings.get();
        boolean quick = settings.getIntSettings("chkQuickAnimation", 0) == 1;
        settings.saveIntSettings("chkQuickAnimation", quick ? 0 : 1);
        if (activity.cardDetailPanel != null) activity.cardDetailPanel.updateSpeedIcon(!quick);
        // 切换后立即应用新速度（对齐 event_handler.cpp BUTTON_QUICK_ANIMIATION 同步设置生效）
        applyAnimationSpeed();
    }

    /**
     * 按 chkQuickAnimation 当前值随时调节动画速度：场上卡片移动/淡入淡出
     * （GameFieldController→GameFieldView）与居中特效（SpecEffectOverlay）两套动画同步，
     * 设置对话框 checkbox、详情面板按钮与启动时 applySettingsToEngine 均经此入口生效。
     * 屏幕旋转重建视图树后也需重新调用（新 GameFieldView 实例的 animSpeedMultiplier 默认 1f）。
     */
    void applyAnimationSpeed() {
        boolean quick = AppsSettings.get().getIntSettings("chkQuickAnimation", 0) == 1;
        float speed = quick ? ANIM_SPEED_QUICK : ANIM_SPEED_NORMAL;
        if (activity.fieldCtl != null) activity.fieldCtl.setAnimationSpeed(speed);
        if (activity.engineCallback != null) activity.engineCallback.setAnimationSpeed(speed);
    }
}

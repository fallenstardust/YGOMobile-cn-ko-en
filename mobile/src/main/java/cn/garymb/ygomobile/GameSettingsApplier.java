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
            soundManager.setMusicMode(appsSettings.getIntSettings("chkSwitchBGM", 0) == 1);
        }
        if (activity.cardDetailPanel != null) {
            // 对齐 gframe imgVol/imgQuickAnimation：声音与速度按钮图标同步设置状态
            activity.cardDetailPanel.updateSoundIcon(enableSound || enableMusic);
            activity.cardDetailPanel.updateSpeedIcon(appsSettings.getIntSettings("chkQuickAnimation", 0) == 1);
            // 对齐 gframe BUTTON_CHATTING：聊天按钮图标与输入框可见性同步停用聊天设置
            boolean chatDisabled = appsSettings.getIntSettings("chkDisableChatting", 0) == 1;
            activity.cardDetailPanel.updateChatIcon(chatDisabled);
            if (activity.etChatInput != null) {
                activity.etChatInput.setVisibility(chatDisabled ? View.GONE : View.VISIBLE);
            }
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
     * 设置对话框 checkbox、详情面板按钮与启动时 applySettingsToEngine 均经此入口生效
     */
    private void applyAnimationSpeed() {
        boolean quick = AppsSettings.get().getIntSettings("chkQuickAnimation", 0) == 1;
        float speed = quick ? ANIM_SPEED_QUICK : ANIM_SPEED_NORMAL;
        if (activity.fieldCtl != null) activity.fieldCtl.setAnimationSpeed(speed);
        if (activity.engineCallback != null) activity.engineCallback.setAnimationSpeed(speed);
    }
}

package cn.garymb.ygomobile.game.gamefield;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.game.engine.GameEngine;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.function.Consumer;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.utils.YGOUtil;

/**
 * layout_top_info 顶部玩家信息条统一管理类（供 YGOProActivity 调用）。
 * 集中初始化 layout_top_info 相关布局，功能对照 drawing.cpp::DrawMisc()：
 * - 双方头像（drawing.cpp L992-994 tAvatar）
 * - 玩家名称（drawing.cpp L1031-1050 hostname/clientname），LP 以血条呈现（tLPBar）
 * - 回合计数 + 当前回合方面板高亮（drawing.cpp L996-1003 LPBarFrame 彩色/灰色、L1052-1057 回合数字）
 * - 手卡数/总卡数与颜色规则（drawing.cpp L1014-1025 str_card_count + card_count_color，
 *   颜色逻辑复用 GameField.refreshCardCountDisplay() 的忠实移植）
 * - 决斗倒计时与颜色分档（drawing.cpp L1005-1012 str_time_left + time_color，
 *   分档规则与 game.cpp RefreshTimeDisplay 一致）
 */
public class GameTopInfoManager extends GameTopInfoManagerBase {

    /**
     * Constructor (public entry of the split chain; the assignment lives
     * in the base layer).
     */
    public GameTopInfoManager(YGOProActivity activity, Handler mainHandler) {
        super(activity, mainHandler);
    }

    /** 统一初始化 layout_top_info 全部视图（由 YGOProActivity.initViews 调用） */
    public void initViews() {
        layoutGameRight = activity.findViewById(R.id.layout_game_right);
        layoutTopInfo = activity.findViewById(R.id.layout_top_info);
        layoutPlayerPanel = activity.findViewById(R.id.layout_player_panel);
        layoutOpponentPanel = activity.findViewById(R.id.layout_opponent_panel);
        layoutPlayerInfoCol = activity.findViewById(R.id.layout_player_info_col);
        layoutOpponentInfoCol = activity.findViewById(R.id.layout_opponent_info_col);
        ivPlayerAvatar = activity.findViewById(R.id.iv_player_avatar);
        ivOpponentAvatar = activity.findViewById(R.id.iv_opponent_avatar);
        layoutPlayerAvatarBox = activity.findViewById(R.id.layout_player_avatar_box);
        layoutOpponentAvatarBox = activity.findViewById(R.id.layout_opponent_avatar_box);
        ivPlayerAvatarFrame = activity.findViewById(R.id.iv_player_avatar_frame);
        ivPlayerBarFrame = activity.findViewById(R.id.iv_player_bar_frame);
        ivOpponentAvatarFrame = activity.findViewById(R.id.iv_opponent_avatar_frame);
        ivOpponentBarFrame = activity.findViewById(R.id.iv_opponent_bar_frame);
        ivPlayerLpBar = activity.findViewById(R.id.iv_player_lp_bar);
        ivPlayerLpBarLayer = activity.findViewById(R.id.iv_player_lp_bar_layer);
        ivOpponentLpBar = activity.findViewById(R.id.iv_opponent_lp_bar);
        ivOpponentLpBarLayer = activity.findViewById(R.id.iv_opponent_lp_bar_layer);
        tvPlayerName = activity.findViewById(R.id.tv_player_name);
        tvPlayerTime = activity.findViewById(R.id.tv_player_time);
        tvPlayerCardCount = activity.findViewById(R.id.tv_player_card_count);
        tvOpponentName = activity.findViewById(R.id.tv_opponent_name);
        tvOpponentTime = activity.findViewById(R.id.tv_opponent_time);
        tvOpponentCardCount = activity.findViewById(R.id.tv_opponent_card_count);
        tvPlayerLpNumber = activity.findViewById(R.id.tv_player_lp_number);
        tvOpponentLpNumber = activity.findViewById(R.id.tv_opponent_lp_number);
        tvTurnCounter = activity.findViewById(R.id.tv_turn_counter);
        ivUndo = activity.findViewById(R.id.iv_undo);
        if (ivUndo != null) {
            // 点击即请求回退本方最近一个动作（服务端按锚点整段回退并重挂该动作前的询问）
            ivUndo.setOnClickListener(v -> activity.requestUndo());
        }
        ivPlayerCardBack = activity.findViewById(R.id.iv_player_card_back);
        ivOpponentCardBack = activity.findViewById(R.id.iv_opponent_card_back);

        setupAvatarImages();
        setupCardBackImages();
        applyTopInfoSize();
        // layout_game_right 尺寸变化（旋转/分屏/平板高分辨率）时重新等比缩放 gameTopInfo
        if (layoutGameRight != null) {
            layoutGameRight.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int w = r - l, hgt = b - t;
                int ow = or - ol, oh = ob - ot;
                if ((w != ow || hgt != oh) && w > 0 && hgt > 0) applyTopInfoSize();
            });
        }
        reset();
    }

    public float getHudScaleFactor() {
        return hudScaleFactor;
    }

    /** 注册 HUD 缩放系数变化监听；当前值由调用方自行 getHudScaleFactor 取用 */
    public void addHudScaleObserver(Consumer<Float> observer) {
        if (observer != null) hudScaleObservers.add(observer);
    }

    /** 恢复对局开始前的初始显示 */
    public void reset() {
        stopTimer();
        stopUndoPulse();
        setPlayerDisplay(0, Constants.PlayerName, DEFAULT_LP_TEXT);
        setPlayerDisplay(1, "Opponent", DEFAULT_LP_TEXT);
        setTurnText(DEFAULT_TURN_TEXT);
        if (tvPlayerTime != null) tvPlayerTime.setVisibility(View.GONE);
        if (tvOpponentTime != null) tvOpponentTime.setVisibility(View.GONE);
        // 猜拳前回合方未定：双方 lpbarf 均灰色、名字均为默认白色，
        // 待 MSG_NEW_TURN/MSG_NEW_PHASE 到来后由 updateTurn 切到真实回合方
        applyTurnHighlight(TURN_PLAYER_NONE);
        updateLpBar(DEFAULT_MAX_LP, DEFAULT_MAX_LP, ivPlayerLpBar, ivPlayerLpBarLayer, Gravity.START);
        updateLpBar(DEFAULT_MAX_LP, DEFAULT_MAX_LP, ivOpponentLpBar, ivOpponentLpBarLayer, Gravity.END);
    }

    public void show() {
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.VISIBLE);
    }

    /**
     * layout_game_right 显示时第一时间的初始化（猜拳前可见的初始状态）：
     * - 头像：从 TextureLoader 加载 me.jpg / opponent.jpg（此时 init() 已完成）
     * - 玩家名称：优先通讯下发的 playerInfos（STOC_PLAYER_ENTER），缺省回退默认名
     * - 房间血量设定：以房间初始 LP（STOC_JOIN_GAME）渲染双方满血条与数字
     */
    public void prepareForDisplay() {
        show();
        setupAvatarImages();
        setupCardBackImages();
        reset();
        GameEngine engine = activity.getEngine();
        int startLp = engine != null ? engine.getGameStartLp() : 0;
        if (startLp <= 0) startLp = DEFAULT_MAX_LP;
        String myName = Constants.PlayerName;
        String oppName = "Opponent";
        if (engine != null) {
            // 统一取视角绑定显示名（对战方 STOC_DUEL_START bindViewNames、回放/残局由各自 runner
            // 按视角写入，观战同样可得，修复观战进入时无玩家名称）；
            // tag 模式当前行动者为队友时自动切队友名（对齐 drawing.cpp L1036-1049）
            String en = engine.displayName(0);
            if (en != null && !en.isEmpty()) myName = en;
            en = engine.displayName(1);
            if (en != null && !en.isEmpty()) oppName = en;
        }
        setPlayerDisplay(0, myName, String.valueOf(startLp));
        setPlayerDisplay(1, oppName, String.valueOf(startLp));
        updateLpBar(startLp, startLp, ivPlayerLpBar, ivPlayerLpBarLayer, Gravity.START);
        updateLpBar(startLp, startLp, ivOpponentLpBar, ivOpponentLpBarLayer, Gravity.END);
    }

    public void hide() {
        stopTimer();
        stopUndoPulse();
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.GONE);
    }

    /** 我方 LP 血条实测宽度（聊天消息最大宽度基准，对齐 drawing.cpp 玩家聊天 maxwidth） */
    public int getPlayerLpBarWidth() {
        return ivPlayerLpBar != null ? ivPlayerLpBar.getWidth() : 0;
    }

    /** 对方 LP 血条实测宽度（聊天消息最大宽度基准） */
    public int getOpponentLpBarWidth() {
        return ivOpponentLpBar != null ? ivOpponentLpBar.getWidth() : 0;
    }

    /**
     * 我方 LP 血条在窗口中的顶边坐标与高度（系统/观战弹幕带带顶锚点：带挂在血条底边，
     * 由 SpecEffectOverlay 的 PopupWindow 承载、在 GL 曲面之上，GameFieldView 为
     * setZOrderOnTop(true) 的 GLSurfaceView，普通 View 会被场地纹理遮挡）。
     * 以委托方式暴露，避免 GameFieldController 直接访问私有视图字段。
     * @return int[]{血条顶边窗口 Y 坐标, 血条高度(px)}；血条尚未布局完成时返回 null
     */
    public int[] getLpBarPositionAndHeight() {
        if (ivPlayerLpBar == null || ivPlayerLpBar.getHeight() <= 0) return null;
        int[] loc = new int[2];
        ivPlayerLpBar.getLocationInWindow(loc);
        return new int[]{loc[1], ivPlayerLpBar.getHeight()};
    }

    /**
     * 指定侧 LP 血条的窗口坐标矩形：int[]{left, top, width, height}，未布局时返回 null。
     * 玩家聊天行容器（SpecEffectOverlay.obtainChatRowLayer）以（left, 血条底边, 血条宽）
     * 定位到该侧血条正下方：我方（含 tag 队友）消息列在我方血条下、对方消息列在对方血条下，
     * 从上到下追加显示（对齐 gframe 分侧聊天，非横向弹幕滚动）。
     */
    public int[] getLpBarRectInWindow(int player) {
        ImageView bar = player == 0 ? ivPlayerLpBar : ivOpponentLpBar;
        if (bar == null || bar.getWidth() <= 0 || bar.getHeight() <= 0) return null;
        int[] loc = new int[2];
        bar.getLocationInWindow(loc);
        return new int[]{loc[0], loc[1], bar.getWidth(), bar.getHeight()};
    }

    // === 玩家名称 / LP（drawing.cpp L1027-1050） ===

    public void setPlayerDisplay(int player, String name, String lpText) {
        // 对齐 gframe chkHidePlayerName：勾选隐藏昵称时决斗内名字以 ******** 显示
        String displayName = name;
        if (AppsSettings.get().getIntSettings("chkHideNickName", 0) == 1) {
            displayName = "********";
        }
        if (player == 0) {
            if (tvPlayerName != null) tvPlayerName.setText(displayName);
        } else {
            if (tvOpponentName != null) tvOpponentName.setText(displayName);
        }
        // LP 数字显示（兼容录像模式传入的 "LP: 8000" 前缀格式）
        if (lpText != null) {
            String num = lpText.startsWith("LP: ") ? lpText.substring(4) : lpText;
            setLpNumberText(player, num);
        }
    }

    // === 回合计数与当前回合方高亮（drawing.cpp L996-1003、L1052-1057） ===

    public void setTurnText(String text) {
        if (tvTurnCounter != null) tvTurnCounter.setText(text);
    }

    // === 局域网撤回提示（回合数下方 ic_undo：闪动发光 = 当前有可整段回退的动作） ===

    /**
     * 按服务端下发的可撤回状态切换撤回图标（STOC_UNDO_STATE）：
     * <p>available=true：点亮回合数下方的 ic_undo 并循环播放「变亮放大 → 变暗缩小」的呼吸动画，
     * {@code @drawable/undo_glow} 的光晕底随之明暗，表示本方上一个行动宣言（召唤 / 反转召唤 /
     * 特殊召唤 / 盖卡 / 发动效果 / 攻击宣言 / 切换阶段）已全部处理完毕、可整段撤回重做；
     * <p>available=false：停止动画并隐藏（不可撤回时不留可点击的空图标）。
     * <p>已处于闪动状态时重复调用不重启动画，避免每步应答都让呼吸相位归零。
     */
    public void setUndoPrompt(boolean available) {
        if (ivUndo == null) return;
        if (!available) {
            stopUndoPulse();
            return;
        }
        if (undoPulse != null && undoPulse.isRunning()) return;
        ivUndo.setVisibility(View.VISIBLE);
        undoPulse = ValueAnimator.ofFloat(0f, 1f);
        undoPulse.setDuration(UNDO_PULSE_HALF_MS);
        undoPulse.setRepeatCount(ValueAnimator.INFINITE);
        undoPulse.setRepeatMode(ValueAnimator.REVERSE);
        undoPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        undoPulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float p = (float) a.getAnimatedValue();
                ivUndo.setAlpha(0.35f + 0.65f * p);
                float s = 0.9f + 0.2f * p;
                ivUndo.setScaleX(s);
                ivUndo.setScaleY(s);
            }
        });
        undoPulse.start();
    }

    /**
     * 更新回合数并切换回合方视觉高亮（LPBarFrame 彩色/灰色 + 玩家名字文字色）
     * @param turn     当前回合数
     * @param isMyTurn 本地视角：是否为我方回合
     */
    public void updateTurn(int turn, boolean isMyTurn) {
        setTurnText(String.valueOf(turn));
        applyTurnHighlight(isMyTurn ? TURN_PLAYER_ME : TURN_PLAYER_OPP);
    }

    // === LP 血条与 LPBarFrame（drawing.cpp L936-973、L996-1003） ===

    /**
     * 根据场上数据刷新双方血条与 LP 数字（数据源为显示值 dInfo.lp，TAG 战上限减半）：
     * 无动画进行中 → 显示值直接对齐通讯真实 LP（players[].lp）后一次性刷新；
     * 有动画进行中 → 启动 16ms 心跳驱动 GameField.updateLpAnimation()，
     * 血条长度与数字随 dInfo.lp 每帧过渡（对齐 drawing.cpp L975-981 strLP 推进）
     */
    public void updateLpBars(GameField field) {
        if (field == null) return;
        mainHandler.removeCallbacks(lpBarTicker);
        pendingLpField = null;
        if (!field.isLpAnimating()) {
            field.dInfo.lp[0] = field.players[0].lp;
            field.dInfo.lp[1] = field.players[1].lp;
            refreshLpDisplay(field);
        } else {
            refreshLpDisplay(field);
            pendingLpField = field;
            mainHandler.postDelayed(lpBarTicker, LP_ANIM_TICK_MS);
        }
    }

    // === 手卡数/总卡数（drawing.cpp L1014-1025，颜色规则见 GameField.refreshCardCountDisplay） ===

    public void updateCardCountDisplay(GameField field) {
        if (field == null) return;
        field.refreshCardCountDisplay();
        if (tvPlayerCardCount != null) {
            tvPlayerCardCount.setText(String.valueOf(field.dInfo.cardCount[0]));
            tvPlayerCardCount.setTextColor(field.dInfo.cardCountColor[0]);
        }
        if (tvOpponentCardCount != null) {
            tvOpponentCardCount.setText(String.valueOf(field.dInfo.cardCount[1]));
            tvOpponentCardCount.setTextColor(field.dInfo.cardCountColor[1]);
        }
    }

    // === 决斗倒计时（drawing.cpp L1005-1012，颜色分档同 RefreshTimeDisplay） ===

    public void onTimeLimitUpdate(int player, int leftTime, int engineTimeLimit) {
        if (duelTimeLimit <= 0) {
            duelTimeLimit = Math.max(engineTimeLimit, leftTime);
        }
        duelTimePlayer = player;
        duelTimeLeft[player] = leftTime;
        mainHandler.removeCallbacks(duelTimeTicker);
        mainHandler.postDelayed(duelTimeTicker, 1000);
        updateTimeDisplay();
    }

    public void stopTimer() {
        mainHandler.removeCallbacks(duelTimeTicker);
        mainHandler.removeCallbacks(lpBarTicker);
        pendingLpField = null;
        duelTimePlayer = -1;
        // LP 动画心跳停止后不再有 refreshLpDisplay 驱动，显式隐藏浮字并关闭浮字层 PopupWindow，
        // 避免残留与窗口泄漏（reset/hide/onDestroy 均经此）
        if (tvPlayerLpFloat != null) tvPlayerLpFloat.setVisibility(View.GONE);
        if (tvOpponentLpFloat != null) tvOpponentLpFloat.setVisibility(View.GONE);
        dismissFloatWindow();
    }
}

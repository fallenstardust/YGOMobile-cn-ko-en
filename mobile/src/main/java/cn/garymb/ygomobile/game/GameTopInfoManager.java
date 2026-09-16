package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.TextureLoader;
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
public class GameTopInfoManager {

    private static final String DEFAULT_LP_TEXT = "8000";
    private static final String DEFAULT_TURN_TEXT = "1";
    private static final int DEFAULT_MAX_LP = 8000;
    /** lpbarf.png 行索引（drawing.cpp L996-1003）：回合方彩色、非回合方灰色 */
    private static final int FRAME_ROW_ME_ACTIVE = 0;      // 我方回合：左框绿色 recti(0,0,305,70)
    private static final int FRAME_ROW_ME_INACTIVE = 1;    // 我方非回合：左框灰色 recti(0,70,305,140)
    private static final int FRAME_ROW_OPP_ACTIVE = 2;     // 对方回合：右框红色 recti(0,140,305,210)
    private static final int FRAME_ROW_OPP_INACTIVE = 3;   // 对方非回合：右框灰色 recti(0,210,305,280)
    /** 本地视角回合方索引：0=我方回合 */
    private static final int TURN_PLAYER_ME = 0;
    /** 本地视角回合方索引：1=对方回合 */
    private static final int TURN_PLAYER_OPP = 1;
    /** 回合尚未决定（layout_game_right 刚显示 / 猜拳阶段）：双方均按非回合样式显示 */
    private static final int TURN_PLAYER_NONE = -1;
    /** 我方回合（左半区）玩家名字文字色：holo blue bright */
    private static final int NAME_COLOR_ME_ACTIVE = YGOUtil.c(R.color.holo_blue_bright);
    /** 对方回合（右半区）玩家名字文字色：holo orange bright */
    private static final int NAME_COLOR_OPP_ACTIVE = YGOUtil.c(R.color.holo_orange_bright);
    /** 非回合玩家名字文字色：白色，与布局 tv_*_name 默认 textColor 一致 */
    private static final int NAME_COLOR_INACTIVE = YGOUtil.c(R.color.white);
    /** 非回合玩家阴影色：黑色 */
    private static final int LP_BAR_LEVEL_FULL = 10000;
    /** LP 动画心跳周期（约 60fps，对齐 drawing.cpp 每帧推进 lpframe） */
    private static final long LP_ANIM_TICK_MS = 16;
    /** LP 变化浮字字号（sp）：悬浮于决斗场之上，比原信息面板内 20sp 略大以更醒目 */
    private static final float LP_FLOAT_TEXT_SIZE_SP = 30f;
    /** 我方 LP 浮字垂直位置（占 layout_game_right 高度比例，>0.5 即中轴偏下）：
     *  对齐 drawing.cpp L986 lpplayer==0 的 Resize(400,470,920,520)，y 中心 495/640≈0.77 */
    private static final float LP_FLOAT_FRAC_PLAYER = 0.77f;
    /** 对方 LP 浮字垂直位置（<0.5 即中轴偏上）：
     *  对齐 drawing.cpp L988 lpplayer==1 的 Resize(400,160,920,210)，y 中心 185/640≈0.29 */
    private static final float LP_FLOAT_FRAC_OPPONENT = 0.29f;
    /** 伤害浮字的引擎侧标识色 RGB（对齐 duelclient.cpp MSG_DAMAGE 的 lpccolor=0xffff0000 纯红）：
     *  GameEngine/ReplayEngine 的 onDamage 仍以纯红下发「伤害」语义，展示层据此 RGB 识别后改绘为 colorAccent；
     *  仅匹配 FF0000，回复绿(00ff00)/支付蓝(0000ff) 不受影响，故对两个引擎的伤害一致生效 */
    private static final int LP_FLOAT_DAMAGE_RGB = 0x00FF0000;
    /** 伤害数字展示色：colorAccent。类加载时解析一次，避免逐帧心跳重复取色 */
    private static final int LP_FLOAT_DAMAGE_COLOR = YGOUtil.c(R.color.colorAccent);

    private final YGOProActivity activity;
    private final Handler mainHandler;

    private FrameLayout layoutGameRight;
    private LinearLayout layoutTopInfo;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private ImageView ivPlayerCardBack, ivOpponentCardBack;
    private ImageView ivPlayerLpFrame, ivOpponentLpFrame;
    private ImageView ivPlayerLpBar, ivPlayerLpBarLayer, ivOpponentLpBar, ivOpponentLpBarLayer;
    private TextView tvPlayerName, tvPlayerTime, tvPlayerCardCount;
    private TextView tvOpponentName, tvOpponentTime, tvOpponentCardCount;
    private TextView tvPlayerLpNumber, tvOpponentLpNumber;
    private TextView tvTurnCounter;
    /** LP 变化浮字（-1000/+500，对齐 drawing.cpp L984-990 lpcstring/lpccolor）：
     *  GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，GL 曲面合成在 Activity 窗口之上，
     *  作为 layout_game_right 子 View 的浮字会被场地/卡片纹理遮挡；故改由「透明、不抢焦点、不拦截触摸的
     *  PopupWindow」承载（图层约定同 SpecEffectOverlay/RPSDialog），稳定显示在 GL 曲面之上。
     *  PopupWindow 精确覆盖 layout_game_right，我方居中轴偏下、对方居中轴偏上，随心跳显隐并按 lpccolor 的 alpha 淡出 */
    private PopupWindow lpFloatWindow;
    private FrameLayout lpFloatContainer;
    private TextView tvPlayerLpFloat, tvOpponentLpFloat;
    private final int[] duelTimeLeft = new int[2];
    private int duelTimePlayer = -1;
    private int duelTimeLimit = 0;
    private final Runnable duelTimeTicker = new Runnable() {
        @Override
        public void run() {
            if (duelTimePlayer >= 0 && duelTimeLeft[duelTimePlayer] > 0) {
                duelTimeLeft[duelTimePlayer]--;
            }
            updateTimeDisplay();
            mainHandler.postDelayed(this, 1000);
        }
    };

    /** LP 动画进行中的血条/数字刷新心跳（驱动 GameField.updateLpAnimation） */
    private GameField pendingLpField;
    private final Runnable lpBarTicker = new Runnable() {
        @Override
        public void run() {
            GameField field = pendingLpField;
            if (field == null) return;
            field.updateLpAnimation();
            refreshLpDisplay(field);
            if (field.isLpAnimating()) {
                mainHandler.postDelayed(this, LP_ANIM_TICK_MS);
            } else {
                pendingLpField = null;
                // 动画收尾：显示值对齐通讯真实 LP，防止整除截断产生残留偏差
                field.dInfo.lp[0] = field.players[0].lp;
                field.dInfo.lp[1] = field.players[1].lp;
                refreshLpDisplay(field);
            }
        }
    };

    public GameTopInfoManager(YGOProActivity activity, Handler mainHandler) {
        this.activity = activity;
        this.mainHandler = mainHandler;
    }

    /** 统一初始化 layout_top_info 全部视图（由 YGOProActivity.initViews 调用） */
    public void initViews() {
        layoutGameRight = activity.findViewById(R.id.layout_game_right);
        layoutTopInfo = activity.findViewById(R.id.layout_top_info);
        ivPlayerAvatar = activity.findViewById(R.id.iv_player_avatar);
        ivOpponentAvatar = activity.findViewById(R.id.iv_opponent_avatar);
        ivPlayerLpFrame = activity.findViewById(R.id.iv_player_lp_frame);
        ivOpponentLpFrame = activity.findViewById(R.id.iv_opponent_lp_frame);
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
        ivPlayerCardBack = activity.findViewById(R.id.iv_player_card_back);
        ivOpponentCardBack = activity.findViewById(R.id.iv_opponent_card_back);

        setupAvatarImages();
        setupCardBackImages();
        reset();
    }

    /** 恢复对局开始前的初始显示 */
    public void reset() {
        stopTimer();
        setPlayerDisplay(0, cn.garymb.ygomobile.Constants.PlayerName, DEFAULT_LP_TEXT);
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
        String myName = cn.garymb.ygomobile.Constants.PlayerName;
        String oppName = "Opponent";
        if (engine != null) {
            // playerInfos 按座位号存储：我方取 selfType 座位、对方取另一座位（1v1），
            // 先后攻交换只影响协议玩家索引，不影响座位与名称的对应
            int selfSeat = engine.getClient().selfType;
            int oppSeat = selfSeat ^ 1;
            if (selfSeat >= 0 && selfSeat < engine.playerInfos.length
                    && !engine.playerInfos[selfSeat].name.isEmpty()) {
                myName = engine.playerInfos[selfSeat].name;
            }
            if (oppSeat >= 0 && oppSeat < engine.playerInfos.length
                    && !engine.playerInfos[oppSeat].name.isEmpty()) {
                oppName = engine.playerInfos[oppSeat].name;
            }
        }
        setPlayerDisplay(0, myName, String.valueOf(startLp));
        setPlayerDisplay(1, oppName, String.valueOf(startLp));
        updateLpBar(startLp, startLp, ivPlayerLpBar, ivPlayerLpBarLayer, Gravity.START);
        updateLpBar(startLp, startLp, ivOpponentLpBar, ivOpponentLpBarLayer, Gravity.END);
    }

    public void hide() {
        stopTimer();
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
     * 我方 LP 血条在窗口中的顶边坐标与高度（弹幕垂直定位锚点）。
     * GameFieldView 为 setZOrderOnTop(true) 的 GLSurfaceView，只有血条所在的顶部透明带
     * 才不会被场地/手卡纹理遮挡；弹幕锚定该带才能与血条同一高度且稳定可见。
     * 以委托方式暴露，避免 GameFieldController 直接访问私有视图字段。
     * @return int[]{血条顶边窗口 Y 坐标, 血条高度(px)}；血条尚未布局完成时返回 null
     */
    public int[] getLpBarPositionAndHeight() {
        if (ivPlayerLpBar == null || ivPlayerLpBar.getHeight() <= 0) return null;
        int[] loc = new int[2];
        ivPlayerLpBar.getLocationInWindow(loc);
        return new int[]{loc[1], ivPlayerLpBar.getHeight()};
    }

    /** 双方头像（drawing.cpp L992-994） */
    private void setupAvatarImages() {
        Bitmap myAvatar = TextureLoader.get().getAvatar(true);
        if (myAvatar != null && ivPlayerAvatar != null) ivPlayerAvatar.setImageBitmap(myAvatar);
        Bitmap opAvatar = TextureLoader.get().getAvatar(false);
        if (opAvatar != null && ivOpponentAvatar != null) ivOpponentAvatar.setImageBitmap(opAvatar);
    }

    /** 双方卡背图标（对齐 ImageManager::tCover[0/1]：我方 cover.jpg，对方 cover2.jpg） */
    private void setupCardBackImages() {
        Bitmap myCover = TextureLoader.get().getCardCover(false);
        if (myCover != null && ivPlayerCardBack != null) ivPlayerCardBack.setImageBitmap(myCover);
        Bitmap opCover = TextureLoader.get().getCardCover(true);
        if (opCover != null && ivOpponentCardBack != null) ivOpponentCardBack.setImageBitmap(opCover);
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

    private void setLpNumberText(int player, String text) {
        TextView tv = player == 0 ? tvPlayerLpNumber : tvOpponentLpNumber;
        if (tv != null) tv.setText(text);
    }

    // === 回合计数与当前回合方高亮（drawing.cpp L996-1003、L1052-1057） ===

    public void setTurnText(String text) {
        if (tvTurnCounter != null) tvTurnCounter.setText(text);
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

    /**
     * 回合方视觉高亮统一入口：LPBarFrame 与名字文字色同步切换，避免两处状态不一致。
     * @param turnPlayer {@link #TURN_PLAYER_ME} / {@link #TURN_PLAYER_OPP} /
     *                   {@link #TURN_PLAYER_NONE}（回合未定，双方均非回合样式）
     */
    private void applyTurnHighlight(int turnPlayer) {
        applyLpBarFrames(turnPlayer);
        applyNameTextColor(turnPlayer);
    }

    /**
     * 仅切换回合玩家名字的文字色，不做阴影/外发光变化：
     * 我方（左半区）回合 → holo blue bright，对方（右半区）回合 → holo orange bright，
     * 非回合玩家与回合未定（{@link #TURN_PLAYER_NONE}）→ 白色；
     * 文字阴影沿用布局 tv_*_name 的 shadowColor/shadowRadius 固定值
     */
    private void applyNameTextColor(int turnPlayer) {
        if (tvPlayerName != null) {
            tvPlayerName.setTextColor(turnPlayer == TURN_PLAYER_ME
                    ? NAME_COLOR_ME_ACTIVE : NAME_COLOR_INACTIVE);
        }
        if (tvOpponentName != null) {
            tvOpponentName.setTextColor(turnPlayer == TURN_PLAYER_OPP
                    ? NAME_COLOR_OPP_ACTIVE : NAME_COLOR_INACTIVE);
        }
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

    /** 每帧刷新：双方血条长度 + LP 数字（取值均为动画显示值 dInfo.lp） */
    private void refreshLpDisplay(GameField field) {
        int maxLp = field.isTag ? Math.max(field.dInfo.startLp / 2, 1) : field.dInfo.startLp;
        if (maxLp <= 0) maxLp = DEFAULT_MAX_LP;
        updateLpBar(field.dInfo.lp[0], maxLp, ivPlayerLpBar, ivPlayerLpBarLayer, Gravity.START);
        updateLpBar(field.dInfo.lp[1], maxLp, ivOpponentLpBar, ivOpponentLpBarLayer, Gravity.END);
        setLpNumberText(0, String.valueOf(Math.max(0, field.dInfo.lp[0])));
        setLpNumberText(1, String.valueOf(Math.max(0, field.dInfo.lp[1])));
        refreshLpFloatText(field);
    }

    /**
     * 惰性创建承载 LP 浮字的 PopupWindow（图层约定同 SpecEffectOverlay/RPSDialog）：
     * GameFieldView 为 setZOrderOnTop(true) 的 GLSurfaceView，GL 曲面合成在 Activity 窗口之上，
     * 普通子 View 会被场地/卡片纹理遮挡；PopupWindow 是独立子窗口，合成在 GL 曲面之上，
     * 透明、不抢焦点、不拦截触摸，事件穿透到下层游戏 UI。浮字 TextView 加在其内容容器上。
     */
    private void ensureFloatWindow() {
        if (lpFloatWindow != null) return;
        lpFloatContainer = new FrameLayout(activity);
        lpFloatContainer.setClipChildren(false);
        tvPlayerLpFloat = createLpFloatText(lpFloatContainer);
        tvOpponentLpFloat = createLpFloatText(lpFloatContainer);
        lpFloatWindow = new PopupWindow(lpFloatContainer,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpFloatWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 纯展示层：不抢焦点、不拦截触摸，事件穿透到下层游戏 UI（对齐 SpecEffectOverlay）
        lpFloatWindow.setFocusable(false);
        lpFloatWindow.setOutsideTouchable(false);
        lpFloatWindow.setTouchable(false);
    }

    /**
     * 将浮字 PopupWindow 精确覆盖到 layout_game_right 区域（显示在 GL 曲面之上，不被卡片遮挡）：
     * 已在显示则直接返回，避免逐帧心跳重复 show 的开销；窗口尺寸＝layout_game_right 尺寸，
     * 故浮字 gravity CENTER 即居中于决斗场中轴，translationY 按高度比例上/下偏移。
     * 定位惯例对齐 DuelLogDialog.showAtGameRightTopRight：以 layout_game_right 为锚 + getLocationInWindow 坐标。
     */
    private void showFloatWindow() {
        if (lpFloatWindow != null && lpFloatWindow.isShowing()) return;
        if (layoutGameRight == null) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        int w = layoutGameRight.getWidth();
        int h = layoutGameRight.getHeight();
        if (w <= 0 || h <= 0) return;
        View decor = activity.getWindow().getDecorView();
        if (decor == null || decor.getWindowToken() == null) return;
        ensureFloatWindow();
        int[] loc = new int[2];
        layoutGameRight.getLocationInWindow(loc);
        lpFloatWindow.setWidth(w);
        lpFloatWindow.setHeight(h);
        try {
            lpFloatWindow.showAtLocation(layoutGameRight, Gravity.NO_GRAVITY, loc[0], loc[1]);
        } catch (Exception ignored) {
        }
    }

    private void dismissFloatWindow() {
        try {
            if (lpFloatWindow != null && lpFloatWindow.isShowing()) lpFloatWindow.dismiss();
        } catch (Exception ignored) {
        }
    }

    /** 在浮字 PopupWindow 的内容容器内创建一个居中于中轴、初始隐藏的 LP 变化浮字 TextView */
    private TextView createLpFloatText(FrameLayout parent) {
        if (parent == null) return null;
        TextView tv = new TextView(activity);
        tv.setTextSize(LP_FLOAT_TEXT_SIZE_SP);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setVisibility(View.GONE);
        tv.setClickable(false);
        tv.setFocusable(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        // gravity 居中：浮字中心先落在容器（＝layout_game_right）中轴，上/下偏移再由 applyLpFloat 的 translationY 施加
        lp.gravity = Gravity.CENTER;
        parent.addView(tv, lp);
        return tv;
    }

    /**
     * LP 变化浮字刷新（对齐 drawing.cpp L984-990）：lpcstring 非空时先把浮字层 PopupWindow 覆盖到决斗场
     * （GL 曲面之上，不被卡片遮挡），再按 lpplayer 在对应半区显示浮字，文字色取 lpccolor
     * （其 alpha 在扣减的 10 帧内每帧 -0x19 衰减，浮字随之淡出）；为空则隐藏两侧浮字并关闭浮字层。
     * 由 lpBarTicker 心跳每 16ms 驱动，与血条/数字过渡同步。
     */
    private void refreshLpFloatText(GameField field) {
        String s = field.lpcstring;
        boolean show = s != null && !s.isEmpty();
        if (show) showFloatWindow();
        applyLpFloat(tvPlayerLpFloat, show && field.lpplayer == 0, s, field.lpccolor, LP_FLOAT_FRAC_PLAYER);
        applyLpFloat(tvOpponentLpFloat, show && field.lpplayer == 1, s, field.lpccolor, LP_FLOAT_FRAC_OPPONENT);
        if (!show) dismissFloatWindow();
    }

    private void applyLpFloat(TextView tv, boolean visible, String text, int color, float targetFraction) {
        if (tv == null) return;
        if (!visible) {
            if (tv.getVisibility() != View.GONE) tv.setVisibility(View.GONE);
            return;
        }
        tv.setText(text);
        // 伤害数字（colorAccent）：仅替换 RGB，保留淡出动画的 alpha 分量
        int drawColor = color;
        if ((drawColor & 0x00FFFFFF) == LP_FLOAT_DAMAGE_RGB) {
            drawColor = (drawColor & 0xFF000000) | (LP_FLOAT_DAMAGE_COLOR & 0x00FFFFFF);
        }
        tv.setTextColor(drawColor);
        // 阴影色对齐 C++ DrawShadowText 的 lpccolor|0x00ffffff（白描边，alpha 随浮字一同淡出）
        tv.setShadowLayer(2f, 2f, 2f, drawColor | 0x00FFFFFF);
        // 垂直定位：gravity 已使浮字居中于容器（＝layout_game_right）中轴，translationY 按目标比例上/下偏移
        // （targetFraction>0.5 偏下＝我方、<0.5 偏上＝对方，对齐 drawing.cpp Resize y 坐标）
        if (layoutGameRight != null) {
            int h = layoutGameRight.getHeight();
            if (h > 0) tv.setTranslationY((targetFraction - 0.5f) * h);
        }
        if (tv.getVisibility() != View.VISIBLE) tv.setVisibility(View.VISIBLE);
    }

    /**
     * 单方血条填充（对照 drawing.cpp L936-972，每 maxLp 为一节）：
     * LP 未超一节 → barView 以首行颜色按 lp/maxLp 比例裁剪填充，叠加层隐藏；
     * LP 超出一节 → barView 以已完成节颜色整条打底，layerView 在其上叠加
     * 下一节颜色，长度 = (lp % maxLp)/maxLp，颜色行按节数循环（lp3.png 共 5 行）
     */
    private void updateLpBar(int lp, int maxLp, ImageView barView, ImageView layerView, int gravity) {
        if (barView == null || maxLp <= 0) return;
        if (lp < 0) lp = 0;
        if (lp >= maxLp) {
            int layerCount = lp / maxLp;
            int partial = lp % maxLp;
            BitmapDrawable base = newLpBarTile((layerCount - 1) % 5);
            if (base != null) barView.setImageDrawable(base);
            if (layerView != null) {
                ClipDrawable clip = newLpBarClip(layerCount % 5, gravity);
                if (clip != null) {
                    layerView.setImageDrawable(clip);
                    layerView.setVisibility(View.VISIBLE);
                    clip.setLevel(partial > 0 ? partial * LP_BAR_LEVEL_FULL / maxLp : 0);
                }
            }
        } else {
            if (layerView != null) layerView.setVisibility(View.GONE);
            ClipDrawable clip = newLpBarClip(0, gravity);
            if (clip != null) {
                barView.setImageDrawable(clip);
                clip.setLevel(lp * LP_BAR_LEVEL_FULL / maxLp);
            }
        }
    }

    /** lp3.png 颜色行横向平铺 Drawable（每次新建，避免共享实例的 level 状态互相干扰） */
    private BitmapDrawable newLpBarTile(int colorRow) {
        Bitmap bmp = TextureLoader.get().getLpBarColorRow(colorRow);
        if (bmp == null) return null;
        BitmapDrawable d = new BitmapDrawable(activity.getResources(), bmp);
        d.setTileModeX(Shader.TileMode.REPEAT);
        return d;
    }

    private ClipDrawable newLpBarClip(int colorRow, int gravity) {
        BitmapDrawable tile = newLpBarTile(colorRow);
        if (tile == null) return null;
        return new ClipDrawable(tile, gravity, ClipDrawable.HORIZONTAL);
    }

    /**
     * drawing.cpp L996-1003：回合方取彩色行（我方绿 row0 / 对方红 row2），
     * 非回合方取灰色行（我方 row1 / 对方 row3）；
     * turnPlayer 为 {@link #TURN_PLAYER_NONE} 时双方都取灰色行；贴图缺失时保留原图层
     */
    private void applyLpBarFrames(int turnPlayer) {
        if (ivPlayerLpFrame != null) {
            BitmapDrawable d = newFrameDrawable(
                    turnPlayer == TURN_PLAYER_ME ? FRAME_ROW_ME_ACTIVE : FRAME_ROW_ME_INACTIVE);
            if (d != null) ivPlayerLpFrame.setImageDrawable(d);
        }
        if (ivOpponentLpFrame != null) {
            BitmapDrawable d = newFrameDrawable(
                    turnPlayer == TURN_PLAYER_OPP ? FRAME_ROW_OPP_ACTIVE : FRAME_ROW_OPP_INACTIVE);
            if (d != null) ivOpponentLpFrame.setImageDrawable(d);
        }
    }

    private BitmapDrawable newFrameDrawable(int row) {
        Bitmap bmp = TextureLoader.get().getLpBarFrameRow(row);
        if (bmp == null) return null;
        return new BitmapDrawable(activity.getResources(), bmp);
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

    private void updateTimeDisplay() {
        if (duelTimeLimit <= 0) return;
        if (tvPlayerTime != null) {
            tvPlayerTime.setVisibility(View.VISIBLE);
            tvPlayerTime.setText("\u23F1 " + duelTimeLeft[0]);
            tvPlayerTime.setTextColor(getTimeColor(0));
        }
        if (tvOpponentTime != null) {
            tvOpponentTime.setVisibility(View.VISIBLE);
            tvOpponentTime.setText("\u23F1 " + duelTimeLeft[1]);
            tvOpponentTime.setTextColor(getTimeColor(1));
        }
    }

    private int getTimeColor(int player) {
        if (duelTimeLeft[player] > 0 && duelTimeLimit > 0) {
            if (duelTimeLeft[player] >= duelTimeLimit / 2) return 0xFF00FF00;
            if (duelTimeLeft[player] >= duelTimeLimit / 3) return 0xFFFFFF00;
            if (duelTimeLeft[player] >= duelTimeLimit / 6) return 0xFFFF7F00;
            return 0xFFFF0000;
        }
        return 0xFFFFFFFF;
    }
}
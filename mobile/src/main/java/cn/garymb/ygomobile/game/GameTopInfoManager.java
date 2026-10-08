package cn.garymb.ygomobile.game;

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
     *  GameEngine（实况与回放共用同一管线）的 onDamage 仍以纯红下发「伤害」语义，展示层据此 RGB 识别后改绘为 colorAccent；
     *  仅匹配 FF0000，回复绿(00ff00)/支付蓝(0000ff) 不受影响，故对实况与回放的伤害一致生效 */
    private static final int LP_FLOAT_DAMAGE_RGB = 0x00FF0000;
    /** 伤害数字展示色：colorAccent。类加载时解析一次，避免逐帧心跳重复取色 */
    private static final int LP_FLOAT_DAMAGE_COLOR = YGOUtil.c(R.color.colorAccent);

    private final YGOProActivity activity;
    private final Handler mainHandler;

    private FrameLayout layoutGameRight;
    // 横竖屏同为水平 LinearLayout（我方5:计数器1:对方5），但仅作可见性控制故声明为 View
    private View layoutTopInfo;
    // 双方血条面板（lpbarf 容器）：运行时按 layout_game_right 尺寸等比缩放面板高度与内容尺寸（applyTopInfoSize）
    private View layoutPlayerPanel;
    private View layoutOpponentPanel;
    // 双方顶层信息列（名字/卡数/倒计时）：头像尺寸随面板高度变化后同步调整避开头像的内边距
    private View layoutPlayerInfoCol;
    private View layoutOpponentInfoCol;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private ImageView ivPlayerCardBack, ivOpponentCardBack;
    // 头像框容器（含头像贴图 + 叠加的头像框）：宽度按面板高 × 64/70 设定，令头像框贴合头像不横向拉伸
    private View layoutPlayerAvatarBox, layoutOpponentAvatarBox;
    // lpbarf 拆分后的头像框与血条框（分别叠加在头像容器与血条容器之上，取代旧的整面板单框）
    private ImageView ivPlayerAvatarFrame, ivPlayerBarFrame;
    private ImageView ivOpponentAvatarFrame, ivOpponentBarFrame;
    private ImageView ivPlayerLpBar, ivPlayerLpBarLayer, ivOpponentLpBar, ivOpponentLpBarLayer;
    private TextView tvPlayerName, tvPlayerTime, tvPlayerCardCount;
    private TextView tvOpponentName, tvOpponentTime, tvOpponentCardCount;
    private TextView tvPlayerLpNumber, tvOpponentLpNumber;
    private TextView tvTurnCounter;
    /**
     * 局域网撤回入口（用户规格：置于中央回合数字下方，平时隐藏）：
     * 收到服务端 STOC_UNDO_STATE=1（本席位有一个已处理完整、可整段回退的行动宣言）时
     * 点亮并循环播放呼吸式闪动发光，点击即发起 CTOS_UNDO。
     */
    private ImageView ivUndo;
    /** iv_undo 的呼吸动画（可撤回期间常驻循环）；null = 未播放 */
    private ValueAnimator undoPulse;
    /** 撤回提示图标 XML 基准尺寸（dp，f=1）与内边距（露出 undo_glow 光晕） */
    private static final float UNDO_BASE_WIDTH_DP = 26f;
    private static final float UNDO_BASE_HEIGHT_DP = 20f;
    private static final float UNDO_BASE_PADDING_DP = 5f;
    /** 呼吸动画单程时长（REVERSE 循环，一个完整明暗周期 ≈ 2×此值） */
    private static final long UNDO_PULSE_HALF_MS = 620L;
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

    /** lpbarf.png 单格框图尺寸 305×70（drawing.cpp tLPBarFrame recti(0,0,305,70)）的宽高比 */
    private static final float LPBARF_ASPECT = 305f / 70f;
    /** 头像框方块宽高比 64:70（lpbarf 行首/行尾 64px 宽、70px 高的方形镂空框） */
    private static final float AVATAR_FRAME_ASPECT = 64f / 70f;
    /** 竖屏血条面板基准高度（dp）：对应 XML 内置头像 34dp/字号基准，放大系数 f=面板高/基准高 */
    private static final float HUD_BASE_HEIGHT_DP = 44f;
    /** 横屏血条面板基准高度（dp）：对应旧布局头像 40dp + 上下 5dp 边距，f=1 时的设计高度 */
    private static final float LANDSCAPE_BASE_HEIGHT_DP = 50f;
    /** 横屏面板高占 layout_game_right 实测高度的比例（对齐 drawing.cpp 70/640≈0.109 的窗口高度占比） */
    private static final float LANDSCAPE_PANEL_HEIGHT_FRACTION = 0.11f;

    /** 最近一次 applyTopInfoSize 解算出的 HUD 等比系数（f=面板高/基准高），供聊天输入框等游戏内 UI 同源缩放 */
    private float hudScaleFactor = 1f;
    /** HUD 缩放系数观察者（layout_game_right 尺寸变化重解后同步回调） */
    private final java.util.List<java.util.function.Consumer<Float>> hudScaleObservers = new ArrayList<>();

    public float getHudScaleFactor() {
        return hudScaleFactor;
    }

    /** 注册 HUD 缩放系数变化监听；当前值由调用方自行 getHudScaleFactor 取用 */
    public void addHudScaleObserver(java.util.function.Consumer<Float> observer) {
        if (observer != null) hudScaleObservers.add(observer);
    }

    /**
     * gameTopInfo 随 layout_game_right 尺寸等比缩放（横竖屏通用）：
     * <p>面板高度 h 决定整条 HUD 的缩放基准 f = h / 基准高，头像框/昵称/LP数字/卡数/倒计时/
     * 回合数/撤回图标/卡背图标均乘 f，故不论 layout_game_right 多大（如 >1080p 平板）比例恒定。
     * <p>竖屏：面板宽≈屏宽×5/11，h 由 lpbarf 305:70 宽高比反推并夹在 [44dp,72dp]（保持原竖屏表现）。
     * <p>横屏：h = layout_game_right 实测高 × 0.11，下限为基准高（手机不缩），上限 150dp（平板不过厚）。
     * <p>头像框容器宽度按 64:70 比例随 h 设定，使头像框贴合头像、血条框（占余宽）自由横向拉伸。
     */
    private void applyTopInfoSize() {
        if (layoutPlayerPanel == null || layoutOpponentPanel == null) return;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean portrait = activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int h;
        float baseH;
        if (portrait) {
            int gw = (layoutGameRight != null && layoutGameRight.getWidth() > 0)
                    ? layoutGameRight.getWidth() : dm.widthPixels;
            float panelWidthPx = gw * 5f / 11f;
            h = Math.round(panelWidthPx / LPBARF_ASPECT);
            int min = Math.round(HUD_BASE_HEIGHT_DP * dm.density);
            int max = Math.round(72f * dm.density);
            h = Math.max(min, Math.min(max, h));
            baseH = min;
        } else {
            int gh = (layoutGameRight != null && layoutGameRight.getHeight() > 0)
                    ? layoutGameRight.getHeight() : dm.heightPixels;
            int min = Math.round(LANDSCAPE_BASE_HEIGHT_DP * dm.density);
            int max = Math.round(150f * dm.density);
            h = Math.round(gh * LANDSCAPE_PANEL_HEIGHT_FRACTION);
            h = Math.max(min, Math.min(max, h));
            baseH = min;
        }
        setPanelHeight(layoutPlayerPanel, h);
        setPanelHeight(layoutOpponentPanel, h);
        // 内容随面板高度等比放大（f=1 为各自基准档）
        float f = h / baseH;
        // 头像框容器：宽 = h × 64/70（贴合头像框方形比例），高 = h；头像贴图 match_parent 填满容器（内边距避让边框）
        int boxW = Math.round(h * AVATAR_FRAME_ASPECT);
        setSize(layoutPlayerAvatarBox, boxW, h);
        setSize(layoutOpponentAvatarBox, boxW, h);
        if (layoutPlayerInfoCol != null)
            layoutPlayerInfoCol.setPadding(boxW, 0, 0, 0);
        if (layoutOpponentInfoCol != null)
            layoutOpponentInfoCol.setPadding(0, 0, boxW, 0);
        setTextSp(tvPlayerName, 10f * f);
        setTextSp(tvOpponentName, 10f * f);
        setTextSp(tvPlayerLpNumber, 12f * f);
        setTextSp(tvOpponentLpNumber, 12f * f);
        setTextSp(tvPlayerCardCount, 10f * f);
        setTextSp(tvOpponentCardCount, 10f * f);
        setTextSp(tvPlayerTime, 10f * f);
        setTextSp(tvOpponentTime, 10f * f);
        setTextSp(tvTurnCounter, 30f * f);
        // 撤回提示图标随面板高度等比放大（含光晕内边距，保持光晕与图标的比例）
        if (ivUndo != null) {
            setSize(ivUndo, Math.round(UNDO_BASE_WIDTH_DP * f * dm.density),
                    Math.round(UNDO_BASE_HEIGHT_DP * f * dm.density));
            int pad = Math.round(UNDO_BASE_PADDING_DP * f * dm.density);
            ivUndo.setPadding(pad, pad, pad, pad);
        }
        int cardBackH = Math.round(10f * f * dm.scaledDensity); // XML 基准高 10sp
        setSize(ivPlayerCardBack, -1, cardBackH);
        setSize(ivOpponentCardBack, -1, cardBackH);
        // 缓存系数并通知观察者（聊天输入框等随 HUD 同源等比例缩放）；
        // 遍历副本：观察者回调内可能触发 requestLayout 连带重入本方法
        hudScaleFactor = f;
        for (java.util.function.Consumer<Float> obs :
                new ArrayList<>(hudScaleObservers)) {
            obs.accept(f);
        }
    }

    /** 设置面板（lpbarf 容器）固定高度，令整条 HUD 高度由 h 主导、内容 match_parent 填满 */
    private void setPanelHeight(View panel, int h) {
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            panel.setLayoutParams(lp);
        }
    }

    private void setSize(View v, int size) {
        setSize(v, size, size);
    }

    /** 视图布局尺寸（传 -1 保持原值） */
    private void setSize(View v, int w, int hgt) {
        if (v == null) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        if (w >= 0) lp.width = w;
        if (hgt >= 0) lp.height = hgt;
        v.setLayoutParams(lp);
    }

    private void setTextSp(TextView tv, float sp) {
        if (tv != null) tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /** 恢复对局开始前的初始显示 */
    public void reset() {
        stopTimer();
        stopUndoPulse();
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

    /** 停止闪动并隐藏撤回图标（不可撤回、重开一局、顶部信息条隐藏时均经此）。 */
    private void stopUndoPulse() {
        if (undoPulse != null) {
            undoPulse.cancel();
            undoPulse = null;
        }
        if (ivUndo != null) {
            ivUndo.setAlpha(1f);
            ivUndo.setScaleX(1f);
            ivUndo.setScaleY(1f);
            ivUndo.setVisibility(View.INVISIBLE);
        }
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
        int meRow = turnPlayer == TURN_PLAYER_ME ? FRAME_ROW_ME_ACTIVE : FRAME_ROW_ME_INACTIVE;
        int oppRow = turnPlayer == TURN_PLAYER_OPP ? FRAME_ROW_OPP_ACTIVE : FRAME_ROW_OPP_INACTIVE;
        // 我方头像框在行首（左），对方头像框在行尾（右）
        applyFramePair(ivPlayerAvatarFrame, ivPlayerBarFrame, meRow, true);
        applyFramePair(ivOpponentAvatarFrame, ivOpponentBarFrame, oppRow, false);
    }

    /** 将 lpbarf 第 row 行拆出的头像框与血条框分别贴到对应 ImageView（贴图缺失时保留原图层） */
    private void applyFramePair(ImageView avatarFrameView, ImageView barFrameView, int row, boolean avatarOnLeft) {
        BitmapDrawable af = newDrawable(TextureLoader.get().getLpBarAvatarFrame(row, avatarOnLeft));
        if (af != null && avatarFrameView != null) avatarFrameView.setImageDrawable(af);
        BitmapDrawable bf = newDrawable(TextureLoader.get().getLpBarBarFrame(row, avatarOnLeft));
        if (bf != null && barFrameView != null) barFrameView.setImageDrawable(bf);
    }

    private BitmapDrawable newDrawable(Bitmap bmp) {
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
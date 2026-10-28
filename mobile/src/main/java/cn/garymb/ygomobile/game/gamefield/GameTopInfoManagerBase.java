package cn.garymb.ygomobile.game.gamefield;
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

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.utils.YGOUtil;


/**
 * Base layer of the split inheritance chain (GameTopInfoManagerBase <- GameTopInfoManager):
 * owns the HUD state fields, the two animation tickers, the proportional
 * top-info sizing engine, the LP bar / float text renderers and the duel
 * countdown display helpers. Public entry points that wire the views and
 * drive whole-widget refreshes live in the leaf class.
 */
abstract class GameTopInfoManagerBase {

    protected static final String DEFAULT_LP_TEXT = "8000";
    protected static final String DEFAULT_TURN_TEXT = "1";
    protected static final int DEFAULT_MAX_LP = 8000;
    /** lpbarf.png 行索引（drawing.cpp L996-1003）：回合方彩色、非回合方灰色 */
    protected static final int FRAME_ROW_ME_ACTIVE = 0;      // 我方回合：左框绿色 recti(0,0,305,70)
    protected static final int FRAME_ROW_ME_INACTIVE = 1;    // 我方非回合：左框灰色 recti(0,70,305,140)
    protected static final int FRAME_ROW_OPP_ACTIVE = 2;     // 对方回合：右框红色 recti(0,140,305,210)
    protected static final int FRAME_ROW_OPP_INACTIVE = 3;   // 对方非回合：右框灰色 recti(0,210,305,280)
    /** 本地视角回合方索引：0=我方回合 */
    protected static final int TURN_PLAYER_ME = 0;
    /** 本地视角回合方索引：1=对方回合 */
    protected static final int TURN_PLAYER_OPP = 1;
    /** 回合尚未决定（layout_game_right 刚显示 / 猜拳阶段）：双方均按非回合样式显示 */
    protected static final int TURN_PLAYER_NONE = -1;
    /** 我方回合（左半区）玩家名字文字色：holo blue bright */
    protected static final int NAME_COLOR_ME_ACTIVE = YGOUtil.c(R.color.holo_blue_bright);
    /** 对方回合（右半区）玩家名字文字色：holo orange bright */
    protected static final int NAME_COLOR_OPP_ACTIVE = YGOUtil.c(R.color.holo_orange_bright);
    /** 非回合玩家名字文字色：白色，与布局 tv_*_name 默认 textColor 一致 */
    protected static final int NAME_COLOR_INACTIVE = YGOUtil.c(R.color.white);
    /** 非回合玩家阴影色：黑色 */
    protected static final int LP_BAR_LEVEL_FULL = 10000;
    /** LP 动画心跳周期（约 60fps，对齐 drawing.cpp 每帧推进 lpframe） */
    protected static final long LP_ANIM_TICK_MS = 16;
    /** LP 变化浮字字号（sp）：悬浮于决斗场之上，比原信息面板内 20sp 略大以更醒目 */
    protected static final float LP_FLOAT_TEXT_SIZE_SP = 30f;
    /** 我方 LP 浮字垂直位置（占 layout_game_right 高度比例，>0.5 即中轴偏下）：
     *  对齐 drawing.cpp L986 lpplayer==0 的 Resize(400,470,920,520)，y 中心 495/640≈0.77 */
    protected static final float LP_FLOAT_FRAC_PLAYER = 0.77f;
    /** 对方 LP 浮字垂直位置（<0.5 即中轴偏上）：
     *  对齐 drawing.cpp L988 lpplayer==1 的 Resize(400,160,920,210)，y 中心 185/640≈0.29 */
    protected static final float LP_FLOAT_FRAC_OPPONENT = 0.29f;
    /** 伤害浮字的引擎侧标识色 RGB（对齐 duelclient.cpp MSG_DAMAGE 的 lpccolor=0xffff0000 纯红）：
     *  GameEngine（实况与回放共用同一管线）的 onDamage 仍以纯红下发「伤害」语义，展示层据此 RGB 识别后改绘为 colorAccent；
     *  仅匹配 FF0000，回复绿(00ff00)/支付蓝(0000ff) 不受影响，故对实况与回放的伤害一致生效 */
    protected static final int LP_FLOAT_DAMAGE_RGB = 0x00FF0000;
    /** 伤害数字展示色：colorAccent。类加载时解析一次，避免逐帧心跳重复取色 */
    protected static final int LP_FLOAT_DAMAGE_COLOR = YGOUtil.c(R.color.colorAccent);

    protected final YGOProActivity activity;
    protected final Handler mainHandler;

    // layout_game_right 横竖屏根节点均为 LinearLayout（六按钮组并入底部行），
    // 本类只用 View 级 API（宽高/坐标/布局监听），声明为 View 免隐式强转 CCE
    protected View layoutGameRight;
    // 横竖屏同为水平 LinearLayout（我方5:计数器1:对方5），但仅作可见性控制故声明为 View
    protected View layoutTopInfo;
    // 双方血条面板（lpbarf 容器）：运行时按 layout_game_right 尺寸等比缩放面板高度与内容尺寸（applyTopInfoSize）
    protected View layoutPlayerPanel;
    protected View layoutOpponentPanel;
    // 双方顶层信息列（名字/卡数/倒计时）：头像尺寸随面板高度变化后同步调整避开头像的内边距
    protected View layoutPlayerInfoCol;
    protected View layoutOpponentInfoCol;
    protected ImageView ivPlayerAvatar, ivOpponentAvatar;
    protected ImageView ivPlayerCardBack, ivOpponentCardBack;
    // 头像框容器（含头像贴图 + 叠加的头像框）：宽度按面板高 × 64/70 设定，令头像框贴合头像不横向拉伸
    protected View layoutPlayerAvatarBox, layoutOpponentAvatarBox;
    // lpbarf 拆分后的头像框与血条框（分别叠加在头像容器与血条容器之上，取代旧的整面板单框）
    protected ImageView ivPlayerAvatarFrame, ivPlayerBarFrame;
    protected ImageView ivOpponentAvatarFrame, ivOpponentBarFrame;
    protected ImageView ivPlayerLpBar, ivPlayerLpBarLayer, ivOpponentLpBar, ivOpponentLpBarLayer;
    protected TextView tvPlayerName, tvPlayerTime, tvPlayerCardCount;
    protected TextView tvOpponentName, tvOpponentTime, tvOpponentCardCount;
    protected TextView tvPlayerLpNumber, tvOpponentLpNumber;
    protected TextView tvTurnCounter;
    /**
     * 局域网撤回入口（用户规格：置于中央回合数字下方，平时隐藏）：
     * 收到服务端 STOC_UNDO_STATE=1（本席位有一个已处理完整、可整段回退的行动宣言）时
     * 点亮并循环播放呼吸式闪动发光，点击即发起 CTOS_UNDO。
     */
    protected ImageView ivUndo;
    /** iv_undo 的呼吸动画（可撤回期间常驻循环）；null = 未播放 */
    protected ValueAnimator undoPulse;
    /** 撤回提示图标 XML 基准尺寸（dp，f=1）与内边距（露出 undo_glow 光晕） */
    protected static final float UNDO_BASE_WIDTH_DP = 26f;
    protected static final float UNDO_BASE_HEIGHT_DP = 20f;
    protected static final float UNDO_BASE_PADDING_DP = 5f;
    /** 呼吸动画单程时长（REVERSE 循环，一个完整明暗周期 ≈ 2×此值） */
    protected static final long UNDO_PULSE_HALF_MS = 620L;
    /** LP 变化浮字（-1000/+500，对齐 drawing.cpp L984-990 lpcstring/lpccolor）：
     *  GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，GL 曲面合成在 Activity 窗口之上，
     *  作为 layout_game_right 子 View 的浮字会被场地/卡片纹理遮挡；故改由「透明、不抢焦点、不拦截触摸的
     *  PopupWindow」承载（图层约定同 SpecEffectOverlay/RPSDialog），稳定显示在 GL 曲面之上。
     *  PopupWindow 精确覆盖 layout_game_right，我方居中轴偏下、对方居中轴偏上，随心跳显隐并按 lpccolor 的 alpha 淡出 */
    protected PopupWindow lpFloatWindow;
    protected FrameLayout lpFloatContainer;
    protected TextView tvPlayerLpFloat, tvOpponentLpFloat;
    protected final int[] duelTimeLeft = new int[2];
    protected int duelTimePlayer = -1;
    protected int duelTimeLimit = 0;
    protected final Runnable duelTimeTicker = new Runnable() {
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
    protected GameField pendingLpField;
    protected final Runnable lpBarTicker = new Runnable() {
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

    /**
     * Constructor body anchor kept verbatim so the final fields are
     * assigned exactly once from the leaf forwarding constructor.
     */
    protected GameTopInfoManagerBase(YGOProActivity activity, Handler mainHandler) {
        this.activity = activity;
        this.mainHandler = mainHandler;
    }

    /** lpbarf.png 单格框图尺寸 305×70（drawing.cpp tLPBarFrame recti(0,0,305,70)）的宽高比 */
    protected static final float LPBARF_ASPECT = 305f / 70f;
    /** 头像框方块宽高比 64:70（lpbarf 行首/行尾 64px 宽、70px 高的方形镂空框） */
    protected static final float AVATAR_FRAME_ASPECT = 64f / 70f;
    /** 竖屏血条面板基准高度（dp）：对应 XML 内置头像 34dp/字号基准，放大系数 f=面板高/基准高 */
    protected static final float HUD_BASE_HEIGHT_DP = 44f;
    /** 横屏血条面板基准高度（dp）：对应旧布局头像 40dp + 上下 5dp 边距，f=1 时的设计高度 */
    protected static final float LANDSCAPE_BASE_HEIGHT_DP = 50f;
    /** 横屏面板高占 layout_game_right 实测高度的比例（对齐 drawing.cpp 70/640≈0.109 的窗口高度占比） */
    protected static final float LANDSCAPE_PANEL_HEIGHT_FRACTION = 0.11f;
    /**
     * 横屏 HUD 纵向占地占视图高的参照比例：取自老式 16:9 横屏实机（实测净空 74.1%），
     * 也是用户已经满意的平板净空（76.6%）与 16:9 之间的保守取值。全面屏 17~24:9 的
     * 纵向 dp 高只 375~430dp（16:9 为 540dp），同样 dp 定值的 HUD 要挤占约 30%，
     * 而决斗场/卡片在相机姿态不变时只能装进两条锚线之间的净空带（卡片投影高≈净空带高的
     * 12.6%），故本参照把超出的占地等比下放给 HUD，回收的像素 1:1 还给取景（用户规格：
     * 保持倾斜度的前提下把卡片与决斗场画得更大，卡片宽高比 177:254 严格不变）
     */
    protected static final float LANDSCAPE_HUD_BUDGET_RATIO = 0.259f;
    /** HUD 等比内缩下限：再小 LP 数字（基准 12sp）/昵称（基准 10sp）可读性不保 */
    protected static final float LANDSCAPE_HUD_RELIEF_MIN = 0.8f;
    /** 参与 HUD 内缩的场区宽高比下限：0.7×16/9（横屏决斗场视图只占屏宽 0.7） */
    protected static final float LANDSCAPE_RELIEF_ASPECT_REF = 1.24f;

    /** 最近一次 applyTopInfoSize 解算出的 HUD 等比系数（f=面板高/基准高），供聊天输入框等游戏内 UI 同源缩放 */
    protected float hudScaleFactor = 1f;
    /** HUD 缩放系数观察者（layout_game_right 尺寸变化重解后同步回调） */
    protected final java.util.List<java.util.function.Consumer<Float>> hudScaleObservers = new ArrayList<>();

    /**
     * gameTopInfo 随 layout_game_right 尺寸等比缩放（横竖屏通用）：
     * <p>面板高度 h 决定整条 HUD 的缩放基准 f = h / 基准高，头像框/昵称/LP数字/卡数/倒计时/
     * 回合数/撤回图标/卡背图标均乘 f，故不论 layout_game_right 多大（如 >1080p 平板）比例恒定。
     * <p>竖屏：面板宽≈屏宽×5/11，h 由 lpbarf 305:70 宽高比反推并夹在 [44dp,72dp]（保持原竖屏表现）。
     * <p>横屏：h = layout_game_right 实测高 × 0.11 × relief，下限为基准高×relief（relief 为全面屏
     * 专用等比内缩系数，16:9/平板恒为 1 → 与旧行为一致），上限 150dp（平板不过厚）。全面屏的
     * relief &lt; 1 把省下的纵向空间 1:1 让给相机上锚线，使决斗场与场上卡片等比变大（仰角不变，
     * 详见 {@link #landscapeHudRelief} 与 FieldCamera 的净空带取景说明）。
     * <p>头像框容器宽度按 64:70 比例随 h 设定，使头像框贴合头像、血条框（占余宽）自由横向拉伸。
     */
    protected void applyTopInfoSize() {
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
            int gw = (layoutGameRight != null && layoutGameRight.getWidth() > 0)
                    ? layoutGameRight.getWidth() : dm.widthPixels;
            // 全面屏专用等比内缩系数（relief=1 时代入即原公式 → 16:9/平板结果与旧行为完全一致）
            float relief = landscapeHudRelief(gw, gh, dm.density);
            int min = Math.round(LANDSCAPE_BASE_HEIGHT_DP * relief * dm.density);
            int max = Math.round(150f * dm.density);
            h = Math.round(gh * LANDSCAPE_PANEL_HEIGHT_FRACTION * relief);
            h = Math.max(min, Math.min(max, h));
            // 基准高不随 relief 变：于是 f = h/基准高 就是 relief 本身（内容随之内缩），
            // 而 relief 只由设备几何决定 → 同一设备恒定，不会随对局内容改变上/下锚线（不晃动）
            baseH = Math.round(LANDSCAPE_BASE_HEIGHT_DP * dm.density);
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

    /**
     * 横屏 HUD 等比内缩系数（1=不内缩）。只有同时满足两个条件才 &lt;1：
     * <ul>
     * <li>场区宽高比超过 16:9 参照（{@link #LANDSCAPE_RELIEF_ASPECT_REF}）——排除 4:3/16:10 平板与
     * 折叠屏横屏（它们的决斗场走竖屏近俯视分支，内缩对取景尺寸无益反而只把字变小）；</li>
     * <li>按 {@link #LANDSCAPE_HUD_BUDGET_RATIO} 算出的本机 HUD 预算（dp）小于名义占地
     * {@link GameFieldView#LANDSCAPE_HUD_NOMINAL_DP}——16:9 手机（540dp）预算 140dp &gt; 125dp，自然不内缩。</li>
     * </ul>
     * 返回值只由 (场区宽高、场区高、屏幕密度) 三个设备恒定量决定，因而上/下锚线仍为恒定量：
     * 不会引入「提示/聊天显隐→取景重解」的晃动，也与 FieldCamera 的仰角抬升互不干扰。
     */
    protected float landscapeHudRelief(int gw, int gh, float density) {
        if (gw <= 0 || gh <= 0 || density <= 0.001f) return 1f;
        if ((float) gw / gh <= LANDSCAPE_RELIEF_ASPECT_REF) return 1f;
        float budgetDp = LANDSCAPE_HUD_BUDGET_RATIO * gh / density;
        float relief = budgetDp / GameFieldView.LANDSCAPE_HUD_NOMINAL_DP;
        return Math.max(LANDSCAPE_HUD_RELIEF_MIN, Math.min(1f, relief));
    }

    /** 设置面板（lpbarf 容器）固定高度，令整条 HUD 高度由 h 主导、内容 match_parent 填满 */
    protected void setPanelHeight(View panel, int h) {
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            panel.setLayoutParams(lp);
        }
    }

    protected void setSize(View v, int size) {
        setSize(v, size, size);
    }

    /** 视图布局尺寸（传 -1 保持原值） */
    protected void setSize(View v, int w, int hgt) {
        if (v == null) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        if (w >= 0) lp.width = w;
        if (hgt >= 0) lp.height = hgt;
        v.setLayoutParams(lp);
    }

    protected void setTextSp(TextView tv, float sp) {
        if (tv != null) tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /** 双方头像（drawing.cpp L992-994） */
    protected void setupAvatarImages() {
        Bitmap myAvatar = TextureLoader.get().getAvatar(true);
        if (myAvatar != null && ivPlayerAvatar != null) ivPlayerAvatar.setImageBitmap(myAvatar);
        Bitmap opAvatar = TextureLoader.get().getAvatar(false);
        if (opAvatar != null && ivOpponentAvatar != null) ivOpponentAvatar.setImageBitmap(opAvatar);
    }

    /** 双方卡背图标（对齐 ImageManager::tCover[0/1]：我方 cover.jpg，对方 cover2.jpg） */
    protected void setupCardBackImages() {
        Bitmap myCover = TextureLoader.get().getCardCover(false);
        if (myCover != null && ivPlayerCardBack != null) ivPlayerCardBack.setImageBitmap(myCover);
        Bitmap opCover = TextureLoader.get().getCardCover(true);
        if (opCover != null && ivOpponentCardBack != null) ivOpponentCardBack.setImageBitmap(opCover);
    }

    protected void setLpNumberText(int player, String text) {
        TextView tv = player == 0 ? tvPlayerLpNumber : tvOpponentLpNumber;
        if (tv != null) tv.setText(text);
    }

    /** 停止闪动并隐藏撤回图标（不可撤回、重开一局、顶部信息条隐藏时均经此）。 */
    protected void stopUndoPulse() {
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
     * 回合方视觉高亮统一入口：LPBarFrame 与名字文字色同步切换，避免两处状态不一致。
     * @param turnPlayer {@link #TURN_PLAYER_ME} / {@link #TURN_PLAYER_OPP} /
     *                   {@link #TURN_PLAYER_NONE}（回合未定，双方均非回合样式）
     */
    protected void applyTurnHighlight(int turnPlayer) {
        applyLpBarFrames(turnPlayer);
        applyNameTextColor(turnPlayer);
    }

    /**
     * 仅切换回合玩家名字的文字色，不做阴影/外发光变化：
     * 我方（左半区）回合 → holo blue bright，对方（右半区）回合 → holo orange bright，
     * 非回合玩家与回合未定（{@link #TURN_PLAYER_NONE}）→ 白色；
     * 文字阴影沿用布局 tv_*_name 的 shadowColor/shadowRadius 固定值
     */
    protected void applyNameTextColor(int turnPlayer) {
        if (tvPlayerName != null) {
            tvPlayerName.setTextColor(turnPlayer == TURN_PLAYER_ME
                    ? NAME_COLOR_ME_ACTIVE : NAME_COLOR_INACTIVE);
        }
        if (tvOpponentName != null) {
            tvOpponentName.setTextColor(turnPlayer == TURN_PLAYER_OPP
                    ? NAME_COLOR_OPP_ACTIVE : NAME_COLOR_INACTIVE);
        }
    }

    /** 每帧刷新：双方血条长度 + LP 数字（取值均为动画显示值 dInfo.lp） */
    protected void refreshLpDisplay(GameField field) {
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
    protected void ensureFloatWindow() {
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
    protected void showFloatWindow() {
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

    protected void dismissFloatWindow() {
        try {
            if (lpFloatWindow != null && lpFloatWindow.isShowing()) lpFloatWindow.dismiss();
        } catch (Exception ignored) {
        }
    }

    /** 在浮字 PopupWindow 的内容容器内创建一个居中于中轴、初始隐藏的 LP 变化浮字 TextView */
    protected TextView createLpFloatText(FrameLayout parent) {
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
    protected void refreshLpFloatText(GameField field) {
        String s = field.lpcstring;
        boolean show = s != null && !s.isEmpty();
        if (show) showFloatWindow();
        applyLpFloat(tvPlayerLpFloat, show && field.lpplayer == 0, s, field.lpccolor, LP_FLOAT_FRAC_PLAYER);
        applyLpFloat(tvOpponentLpFloat, show && field.lpplayer == 1, s, field.lpccolor, LP_FLOAT_FRAC_OPPONENT);
        if (!show) dismissFloatWindow();
    }

    protected void applyLpFloat(TextView tv, boolean visible, String text, int color, float targetFraction) {
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
    protected void updateLpBar(int lp, int maxLp, ImageView barView, ImageView layerView, int gravity) {
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
    protected BitmapDrawable newLpBarTile(int colorRow) {
        Bitmap bmp = TextureLoader.get().getLpBarColorRow(colorRow);
        if (bmp == null) return null;
        BitmapDrawable d = new BitmapDrawable(activity.getResources(), bmp);
        d.setTileModeX(Shader.TileMode.REPEAT);
        return d;
    }

    protected ClipDrawable newLpBarClip(int colorRow, int gravity) {
        BitmapDrawable tile = newLpBarTile(colorRow);
        if (tile == null) return null;
        return new ClipDrawable(tile, gravity, ClipDrawable.HORIZONTAL);
    }

    /**
     * drawing.cpp L996-1003：回合方取彩色行（我方绿 row0 / 对方红 row2），
     * 非回合方取灰色行（我方 row1 / 对方 row3）；
     * turnPlayer 为 {@link #TURN_PLAYER_NONE} 时双方都取灰色行；贴图缺失时保留原图层
     */
    protected void applyLpBarFrames(int turnPlayer) {
        int meRow = turnPlayer == TURN_PLAYER_ME ? FRAME_ROW_ME_ACTIVE : FRAME_ROW_ME_INACTIVE;
        int oppRow = turnPlayer == TURN_PLAYER_OPP ? FRAME_ROW_OPP_ACTIVE : FRAME_ROW_OPP_INACTIVE;
        // 我方头像框在行首（左），对方头像框在行尾（右）
        applyFramePair(ivPlayerAvatarFrame, ivPlayerBarFrame, meRow, true);
        applyFramePair(ivOpponentAvatarFrame, ivOpponentBarFrame, oppRow, false);
    }

    /** 将 lpbarf 第 row 行拆出的头像框与血条框分别贴到对应 ImageView（贴图缺失时保留原图层） */
    protected void applyFramePair(ImageView avatarFrameView, ImageView barFrameView, int row, boolean avatarOnLeft) {
        BitmapDrawable af = newDrawable(TextureLoader.get().getLpBarAvatarFrame(row, avatarOnLeft));
        if (af != null && avatarFrameView != null) avatarFrameView.setImageDrawable(af);
        BitmapDrawable bf = newDrawable(TextureLoader.get().getLpBarBarFrame(row, avatarOnLeft));
        if (bf != null && barFrameView != null) barFrameView.setImageDrawable(bf);
    }

    protected BitmapDrawable newDrawable(Bitmap bmp) {
        if (bmp == null) return null;
        return new BitmapDrawable(activity.getResources(), bmp);
    }

    protected void updateTimeDisplay() {
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

    // 限时倒计时文字色（与 GameFieldMotion.refreshTimeDisplay 同源阈值）：
    // 原 0xFFRRGGBB 硬编码集中到 colors.xml，类级缓存避免逐次查资源
    protected static final int TIME_GREEN = YGOUtil.c(R.color.pure_green);
    protected static final int TIME_YELLOW = YGOUtil.c(R.color.yellow);
    protected static final int TIME_ORANGE = YGOUtil.c(R.color.timer_orange);
    protected static final int TIME_RED = YGOUtil.c(R.color.pure_red);
    protected static final int TIME_WHITE = YGOUtil.c(R.color.white);

    protected int getTimeColor(int player) {
        if (duelTimeLeft[player] > 0 && duelTimeLimit > 0) {
            if (duelTimeLeft[player] >= duelTimeLimit / 2) return TIME_GREEN;
            if (duelTimeLeft[player] >= duelTimeLimit / 3) return TIME_YELLOW;
            if (duelTimeLeft[player] >= duelTimeLimit / 6) return TIME_ORANGE;
            return TIME_RED;
        }
        return TIME_WHITE;
    }
}

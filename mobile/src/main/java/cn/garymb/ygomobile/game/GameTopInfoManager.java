package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.TextureLoader;

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
    private static final int FRAME_ROW_ME_ACTIVE = 0;
    private static final int FRAME_ROW_ME_INACTIVE = 1;
    private static final int FRAME_ROW_OPP_INACTIVE = 2;
    private static final int FRAME_ROW_OPP_ACTIVE = 3;
    private static final int LP_BAR_LEVEL_FULL = 10000;
    /** LP 动画心跳周期（约 60fps，对齐 drawing.cpp 每帧推进 lpframe） */
    private static final long LP_ANIM_TICK_MS = 16;

    private final YGOProActivity activity;
    private final Handler mainHandler;

    private LinearLayout layoutTopInfo;
    private FrameLayout layoutPlayerPanel, layoutOpponentPanel;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private ImageView ivPlayerLpFrame, ivOpponentLpFrame;
    private ImageView ivPlayerLpBar, ivPlayerLpBarLayer, ivOpponentLpBar, ivOpponentLpBarLayer;
    private TextView tvPlayerName, tvPlayerTime, tvPlayerCardCount;
    private TextView tvOpponentName, tvOpponentTime, tvOpponentCardCount;
    private TextView tvPlayerLpNumber, tvOpponentLpNumber;
    private TextView tvTurnCounter;

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
        layoutTopInfo = activity.findViewById(R.id.layout_top_info);
        layoutPlayerPanel = activity.findViewById(R.id.layout_player_panel);
        layoutOpponentPanel = activity.findViewById(R.id.layout_opponent_panel);
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

        setupAvatarImages();
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
        applyLpBarFrames(true);
        updateLpBar(DEFAULT_MAX_LP, DEFAULT_MAX_LP, ivPlayerLpBar, ivPlayerLpBarLayer, Gravity.START);
        updateLpBar(DEFAULT_MAX_LP, DEFAULT_MAX_LP, ivOpponentLpBar, ivOpponentLpBarLayer, Gravity.END);
    }

    public void show() {
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.VISIBLE);
    }

    public void hide() {
        stopTimer();
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.GONE);
    }

    /** 双方头像（drawing.cpp L992-994） */
    private void setupAvatarImages() {
        Bitmap myAvatar = TextureLoader.get().getAvatar(true);
        if (myAvatar != null && ivPlayerAvatar != null) ivPlayerAvatar.setImageBitmap(myAvatar);
        Bitmap opAvatar = TextureLoader.get().getAvatar(false);
        if (opAvatar != null && ivOpponentAvatar != null) ivOpponentAvatar.setImageBitmap(opAvatar);
    }

    // === 玩家名称 / LP（drawing.cpp L1027-1050） ===

    public void setPlayerDisplay(int player, String name, String lpText) {
        if (player == 0) {
            if (tvPlayerName != null) tvPlayerName.setText(name);
        } else {
            if (tvOpponentName != null) tvOpponentName.setText(name);
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
     * 更新回合数并切换双方 LPBarFrame 彩色/灰色
     * @param turn     当前回合数
     * @param isMyTurn 本地视角：是否为我方回合
     */
    public void updateTurn(int turn, boolean isMyTurn) {
        setTurnText(String.valueOf(turn));
        applyLpBarFrames(isMyTurn);
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

    /** drawing.cpp L996-1003：我方回合=我方彩色框+对方灰色框，对方回合反之；贴图缺失时保留原图层 */
    private void applyLpBarFrames(boolean isMyTurn) {
        if (ivPlayerLpFrame != null) {
            BitmapDrawable d = newFrameDrawable(isMyTurn ? FRAME_ROW_ME_ACTIVE : FRAME_ROW_ME_INACTIVE);
            if (d != null) ivPlayerLpFrame.setImageDrawable(d);
        }
        if (ivOpponentLpFrame != null) {
            BitmapDrawable d = newFrameDrawable(isMyTurn ? FRAME_ROW_OPP_INACTIVE : FRAME_ROW_OPP_ACTIVE);
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
package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.os.Handler;
import android.view.View;
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
 * - 玩家名称 / LP 数值（drawing.cpp L1027-1050 strLP + hostname/clientname）
 * - 回合计数 + 当前回合方面板高亮（drawing.cpp L996-1003 LPBarFrame 彩色/灰色、L1052-1057 回合数字）
 * - 手卡数/总卡数与颜色规则（drawing.cpp L1014-1025 str_card_count + card_count_color，
 *   颜色逻辑复用 GameField.refreshCardCountDisplay() 的忠实移植）
 * - 决斗倒计时与颜色分档（drawing.cpp L1005-1012 str_time_left + time_color，
 *   分档规则与 game.cpp RefreshTimeDisplay 一致）
 */
public class GameTopInfoManager {

    private static final String DEFAULT_LP_TEXT = "8000";
    private static final String DEFAULT_TURN_TEXT = "1";
    /** 对照 drawing.cpp L996-1003：回合方 LPBarFrame 彩色，非回合方灰色，此处以面板透明度体现 */
    private static final float PANEL_ALPHA_ACTIVE = 1.0f;
    private static final float PANEL_ALPHA_INACTIVE = 0.65f;

    private final YGOProActivity activity;
    private final Handler mainHandler;

    private LinearLayout layoutTopInfo;
    private LinearLayout layoutPlayerPanel, layoutOpponentPanel;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private TextView tvPlayerName, tvPlayerLp, tvPlayerTime, tvPlayerHandCount;
    private TextView tvOpponentName, tvOpponentLp, tvOpponentTime, tvOpponentHandCount;
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
        tvPlayerName = activity.findViewById(R.id.tv_player_name);
        tvPlayerLp = activity.findViewById(R.id.tv_player_lp);
        tvPlayerTime = activity.findViewById(R.id.tv_player_time);
        tvPlayerHandCount = activity.findViewById(R.id.tv_player_hand_count);
        tvOpponentName = activity.findViewById(R.id.tv_opponent_name);
        tvOpponentLp = activity.findViewById(R.id.tv_opponent_lp);
        tvOpponentTime = activity.findViewById(R.id.tv_opponent_time);
        tvOpponentHandCount = activity.findViewById(R.id.tv_opponent_hand_count);
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
        if (layoutPlayerPanel != null) layoutPlayerPanel.setAlpha(PANEL_ALPHA_ACTIVE);
        if (layoutOpponentPanel != null) layoutOpponentPanel.setAlpha(PANEL_ALPHA_ACTIVE);
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
            if (tvPlayerLp != null) tvPlayerLp.setText(lpText);
        } else {
            if (tvOpponentName != null) tvOpponentName.setText(name);
            if (tvOpponentLp != null) tvOpponentLp.setText(lpText);
        }
    }

    // === 回合计数与当前回合方高亮（drawing.cpp L996-1003、L1052-1057） ===

    public void setTurnText(String text) {
        if (tvTurnCounter != null) tvTurnCounter.setText(text);
    }

    /**
     * 更新回合数并高亮当前回合方面板
     * @param turn     当前回合数
     * @param isMyTurn 本地视角：是否为我方回合
     */
    public void updateTurn(int turn, boolean isMyTurn) {
        setTurnText(String.valueOf(turn));
        if (layoutPlayerPanel != null) {
            layoutPlayerPanel.setAlpha(isMyTurn ? PANEL_ALPHA_ACTIVE : PANEL_ALPHA_INACTIVE);
        }
        if (layoutOpponentPanel != null) {
            layoutOpponentPanel.setAlpha(isMyTurn ? PANEL_ALPHA_INACTIVE : PANEL_ALPHA_ACTIVE);
        }
    }

    // === 手卡数/总卡数（drawing.cpp L1014-1025，颜色规则见 GameField.refreshCardCountDisplay） ===

    public void updateCardCountDisplay(GameField field) {
        if (field == null) return;
        field.refreshCardCountDisplay();
        int[] handCount = new int[2];
        for (int p = 0; p < 2; p++) {
            int c = 0;
            for (GameField.ClientCard cc : field.players[p].hand) {
                if (cc != null) c++;
            }
            handCount[p] = c;
        }
        if (tvPlayerHandCount != null) {
            tvPlayerHandCount.setText("手卡:" + handCount[0] + " 总:" + field.dInfo.cardCount[0]);
            tvPlayerHandCount.setTextColor(field.dInfo.cardCountColor[0]);
        }
        if (tvOpponentHandCount != null) {
            tvOpponentHandCount.setText("手卡:" + handCount[1] + " 总:" + field.dInfo.cardCount[1]);
            tvOpponentHandCount.setTextColor(field.dInfo.cardCountColor[1]);
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
        duelTimePlayer = -1;
    }

    private void updateTimeDisplay() {
        if (duelTimeLimit <= 0) return;
        if (tvPlayerTime != null) {
            tvPlayerTime.setVisibility(View.VISIBLE);
            tvPlayerTime.setText("\u23F1 " + formatDuelTime(duelTimeLeft[0]));
            tvPlayerTime.setTextColor(getTimeColor(0));
        }
        if (tvOpponentTime != null) {
            tvOpponentTime.setVisibility(View.VISIBLE);
            tvOpponentTime.setText("\u23F1 " + formatDuelTime(duelTimeLeft[1]));
            tvOpponentTime.setTextColor(getTimeColor(1));
        }
    }

    private String formatDuelTime(int sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
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
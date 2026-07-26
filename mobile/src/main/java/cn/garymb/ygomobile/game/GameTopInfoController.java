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
 * layout_top_info 区域管理类：
 * 双方 LP/名字/手卡数/计时/头像 + 回合数 + 提示信息 + 聊天气泡 + 决斗倒计时
 */
public class GameTopInfoController {

    private final YGOProActivity activity;
    private final Handler mainHandler;

    private LinearLayout layoutTopInfo;
    private TextView tvPlayerLp, tvPlayerName, tvOpponentLp, tvOpponentName;
    private TextView tvTurnCounter;
    private TextView tvPlayerHandCount, tvOpponentHandCount;
    private TextView tvPlayerTime, tvOpponentTime;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private TextView tvHintMessage;
    private LinearLayout layoutChatMessages;
    private TextView tvChatMessage1, tvChatMessage2;

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

    public GameTopInfoController(YGOProActivity activity, Handler mainHandler) {
        this.activity = activity;
        this.mainHandler = mainHandler;
    }

    public void bindViews() {
        layoutTopInfo = activity.findViewById(R.id.layout_top_info);
        tvPlayerLp = activity.findViewById(R.id.tv_player_lp);
        tvPlayerName = activity.findViewById(R.id.tv_player_name);
        tvOpponentLp = activity.findViewById(R.id.tv_opponent_lp);
        tvOpponentName = activity.findViewById(R.id.tv_opponent_name);
        tvTurnCounter = activity.findViewById(R.id.tv_turn_counter);
        tvPlayerHandCount = activity.findViewById(R.id.tv_player_hand_count);
        tvOpponentHandCount = activity.findViewById(R.id.tv_opponent_hand_count);
        tvPlayerTime = activity.findViewById(R.id.tv_player_time);
        tvOpponentTime = activity.findViewById(R.id.tv_opponent_time);
        tvHintMessage = activity.findViewById(R.id.tv_hint_message);
        ivPlayerAvatar = activity.findViewById(R.id.iv_player_avatar);
        ivOpponentAvatar = activity.findViewById(R.id.iv_opponent_avatar);
        layoutChatMessages = activity.findViewById(R.id.layout_chat_messages);
        tvChatMessage1 = activity.findViewById(R.id.tv_chat_message_1);
        tvChatMessage2 = activity.findViewById(R.id.tv_chat_message_2);
        setupAvatarImages();
    }

    private void setupAvatarImages() {
        Bitmap myAvatar = TextureLoader.get().getAvatar(true);
        if (myAvatar != null) ivPlayerAvatar.setImageBitmap(myAvatar);
        Bitmap opAvatar = TextureLoader.get().getAvatar(false);
        if (opAvatar != null) ivOpponentAvatar.setImageBitmap(opAvatar);
    }

    public void show() {
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.VISIBLE);
    }

    public void hide() {
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.GONE);
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
    }

    // === 玩家信息 ===

    public void setPlayerDisplay(int player, String name, String lpText) {
        if (player == 0) {
            tvPlayerLp.setText(lpText);
            tvPlayerName.setText(name);
        } else {
            tvOpponentLp.setText(lpText);
            tvOpponentName.setText(name);
        }
    }

    public void setTurnText(String text) {
        tvTurnCounter.setText(text);
    }

    public void updateCardCountDisplay(GameField field) {
        int[] handCount = new int[2];
        int[] cardCount = new int[2];
        for (int p = 0; p < 2; p++) {
            int c = 0;
            for (GameField.ClientCard cc : field.players[p].hand) {
                if (cc != null) c++;
            }
            handCount[p] = c;
            for (GameField.ClientCard cc : field.players[p].monsterZone) {
                if (cc != null) c++;
            }
            for (GameField.ClientCard cc : field.players[p].spellZone) {
                if (cc != null) c++;
            }
            cardCount[p] = c;
        }
        int color0, color1;
        if (cardCount[0] > cardCount[1]) {
            color0 = 0xFFFFFF00;
            color1 = 0xFFFF2A00;
        } else if (cardCount[1] > cardCount[0]) {
            color1 = 0xFFFFFF00;
            color0 = 0xFFFF2A00;
        } else {
            color0 = 0xFFFFFFFF;
            color1 = 0xFFFFFFFF;
        }
        if (tvPlayerHandCount != null) {
            tvPlayerHandCount.setText("手卡:" + handCount[0] + " 总:" + cardCount[0]);
            tvPlayerHandCount.setTextColor(color0);
        }
        if (tvOpponentHandCount != null) {
            tvOpponentHandCount.setText("手卡:" + handCount[1] + " 总:" + cardCount[1]);
            tvOpponentHandCount.setTextColor(color1);
        }
    }

    // === 提示信息 ===

    public void showHint(String msg, int durationMs) {
        tvHintMessage.setText(msg);
        tvHintMessage.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(() -> tvHintMessage.setVisibility(View.GONE), durationMs);
    }

    // === 聊天气泡 ===

    public void appendChat(String player, String message) {
        String chatLine = "[" + player + "] " + message;

        if (tvChatMessage1 != null && tvChatMessage1.getVisibility() == View.GONE) {
            tvChatMessage1.setText(chatLine);
            tvChatMessage1.setVisibility(View.VISIBLE);
        } else if (tvChatMessage2 != null && tvChatMessage2.getVisibility() == View.GONE) {
            tvChatMessage2.setText(chatLine);
            tvChatMessage2.setVisibility(View.VISIBLE);
        } else {
            if (tvChatMessage1 != null) {
                tvChatMessage1.setText(tvChatMessage2 != null ? tvChatMessage2.getText() : "");
            }
            if (tvChatMessage2 != null) {
                tvChatMessage2.setText(chatLine);
                tvChatMessage2.setVisibility(View.VISIBLE);
            }
        }

        if (layoutChatMessages != null) {
            layoutChatMessages.setVisibility(View.VISIBLE);
        }
    }

    // === 决斗倒计时 ===

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
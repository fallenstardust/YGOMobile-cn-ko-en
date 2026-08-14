package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.DuelFieldManager;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.GameFieldViewController;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import ocgcore.DataManager;
import ocgcore.enums.DuelPhase;

/**
 * 决斗场管理类：卡片/区域点击与长按、卡片命令菜单、放置区域选择、
 * 高亮、连锁动画、cmdContext 状态；
 * layout_top_info 区域：双方 LP/名字/手卡数/计时/头像 + 回合数 + 提示信息 + 聊天气泡 + 决斗倒计时；
 * 以及阶段按钮（DP/SP/M1/BP/M2/EP 切换与响应）
 * 以及 DuelFieldManager 驱动的 XML 区域视图（怪兽区/魔陷区/堆叠区）
 */
public class GameFieldController implements GameFieldView.OnCardClickListener {

    private static final String TAG = "YGONativeGame";
    static final int CMD_CONTEXT_IDLE = 1;
    static final int CMD_CONTEXT_BATTLE = 2;

    private final YGOProActivity activity;
    private final Handler mainHandler;
    private GameFieldViewController viewController;
    private DuelFieldManager duelFieldManager;
    private GameEngine engine;
    private int cmdContext = 0;
    private boolean isPlaceSelecting = false;

    private LinearLayout layoutTopInfo;
    private TextView tvPlayerLp, tvPlayerName, tvOpponentLp, tvOpponentName;
    private TextView tvTurnCounter;
    private TextView tvPlayerHandCount, tvOpponentHandCount;
    private TextView tvPlayerTime, tvOpponentTime;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private TextView tvHintMessage;
    private FrameLayout layoutChatMessages;
    private TextView tvChatMessage1, tvChatMessage2;

    private Button btnPhaseCurrent, btnPhaseNext, btnEp;
    private FrameLayout layoutOppHandArea, layoutMyHandArea;

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

    public GameFieldController(YGOProActivity activity, Handler mainHandler) {
        this.activity = activity;
        this.mainHandler = mainHandler;
    }

    public void create() {
        viewController = new GameFieldViewController(activity);
        duelFieldManager = new DuelFieldManager(activity);
        bindTopInfoViews();
        bindPhaseButtons();
        duelFieldManager.setOnZoneClickListener(this::onZoneClick);
        layoutOppHandArea = activity.findViewById(R.id.layout_opp_hand_area);
        layoutMyHandArea = activity.findViewById(R.id.layout_my_hand_area);
    }

    private void bindTopInfoViews() {
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

    private void bindPhaseButtons() {
        btnPhaseCurrent = activity.findViewById(R.id.btn_phase_current);
        btnPhaseNext = activity.findViewById(R.id.btn_phase_next);
        btnEp = activity.findViewById(R.id.btn_ep);

        if (btnPhaseNext != null) {
            btnPhaseNext.setOnClickListener(v -> {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                String label = btnPhaseNext.getText().toString();
                if ("BP".equals(label) && activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(6);
                } else if ("M2".equals(label) && activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(2);
                }
            });
        }

        if (btnEp != null) {
            btnEp.setOnClickListener(v -> {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                if (activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(3);
                } else if (activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(7);
                }
            });
        }
    }

    public void init(GameEngine engine, ImageLoader imageLoader) {
        this.engine = engine;
        viewController.init(engine.getField(), imageLoader, this);
        duelFieldManager.setImageLoader(imageLoader);
    }

    public void show() {
        if (viewController != null) viewController.show();
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.VISIBLE);
        if (layoutOppHandArea != null) layoutOppHandArea.setVisibility(View.VISIBLE);
        if (layoutMyHandArea != null) layoutMyHandArea.setVisibility(View.VISIBLE);
    }

    public void hide() {
        if (viewController != null) viewController.hide();
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.GONE);
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
        if (btnPhaseCurrent != null) btnPhaseCurrent.setVisibility(View.GONE);
        if (btnPhaseNext != null) btnPhaseNext.setVisibility(View.GONE);
        if (btnEp != null) btnEp.setVisibility(View.GONE);
        if (layoutOppHandArea != null) layoutOppHandArea.setVisibility(View.GONE);
        if (layoutMyHandArea != null) layoutMyHandArea.setVisibility(View.GONE);
    }

    public void invalidate() {
        viewController.invalidate();
        if (engine != null && duelFieldManager != null) {
            duelFieldManager.updateFromField(engine.getField());
        }
    }

    public void selectCardWithAutoClear(int controler, int location, int sequence, int durationMs) {
        viewController.selectCardWithAutoClear(controler, location, sequence, durationMs);
    }

    void setCmdContext(int context) {
        cmdContext = context;
    }

    // === 放置区域选择 ===

    public void beginPlaceSelect(boolean isDisfield) {
        isPlaceSelecting = true;
        int mask = engine.selectFieldMask;
        viewController.highlightField(mask);
        // 同步高亮到 DuelFieldManager 区域视图
        duelFieldManager.applyHighlightMask(mask);
        String msg = isDisfield ? "请选择要禁用的区域" : "请选择放置位置";
        showHint(msg, 3000);
    }

    public boolean cancelPlaceSelect() {
        if (!isPlaceSelecting) return false;
        int selfType = engine.getClient().selfType;
        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) selfType);
        buf.put((byte) 0);
        buf.put((byte) 0);
        engine.sendResponse(buf.array());
        isPlaceSelecting = false;
        viewController.clearHighlight();
        duelFieldManager.clearAllHighlights();
        return true;
    }

    // === 区域点击处理（来自 DuelFieldManager） ===

    @Override
    public void onZoneClick(int player, int location, int sequence) {
        if (engine == null) return;
        GameField field = engine.getField();
        if (isPlaceSelecting) {
            handlePlaceSelection(player, location, sequence);
            return;
        }
        GameField.ClientCard card = field.getCard(player, location, sequence);
        if (card != null && card.cmdFlag != 0) {
            showCardCommandMenu(card);
            return;
        }
        // 堆叠区点击：查看卡片信息
        boolean isPile = (location == 0x01 || location == 0x10
                || location == 0x20 || location == 0x40);
        if (isPile && card != null && card.code > 0) {
            activity.showCardInfoPanel(card);
            return;
        }
        // 场上卡片点击：查看信息
        if (card != null && card.code > 0) {
            activity.showCardInfoPanel(card);
        }
    }

    private void handlePlaceSelection(int player, int location, int sequence) {
        int bitPos = getZoneBitPos(player, location, sequence);
        if (bitPos < 0 || (engine.selectFieldMask & (1 << bitPos)) == 0) {
            showHint("该区域不可选择", 3000);
            return;
        }
        isPlaceSelecting = false;
        viewController.clearHighlight();
        duelFieldManager.clearAllHighlights();

        // player 为本地方位索引(0=我方,1=对方)，协议响应需转换为服务端 player 索引
        int respPlayer = (player == 0)
                ? engine.getClient().selfType
                : (1 - engine.getClient().selfType);
        int respLocation;
        if (location == 0x04) {
            respLocation = 0x04;
        } else if (location == 0x08) {
            respLocation = 0x08;
        } else {
            respLocation = location;
        }
        int respSeq = sequence;

        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) respPlayer);
        buf.put((byte) respLocation);
        buf.put((byte) respSeq);
        engine.sendResponse(buf.array());
    }

    private int getZoneBitPos(int player, int location, int sequence) {
        // player 为本地方位索引(0=我方,1=对方)，mask 已归一化：0-15=我方, 16-31=对方
        int base = (player == 0) ? 0 : 16;
        if (location == 0x04) return base + sequence;
        if (location == 0x08) {
            if (sequence < 6) return base + 8 + sequence;
            if (sequence == 6) return base + 14;
            if (sequence == 7) return base + 15;
        }
        return -1;
    }

    // === 卡片命令菜单 ===

    private void showCardCommandMenu(GameField.ClientCard card) {
        int flag = card.cmdFlag;
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        String cardName = activity.getCardDisplayName(card.code);

        if ((flag & GameEngine.COMMAND_ACTIVATE) != 0) {
            List<GameEngine.CmdCardInfo> matches = new ArrayList<>();
            for (int i = 0; i < engine.activatableCards.size(); i++) {
                if (engine.activatableCards.get(i).card == card) {
                    matches.add(engine.activatableCards.get(i));
                }
            }
            if (matches.size() == 1) {
                GameEngine.CmdCardInfo info = matches.get(0);
                String descStr = info.desc > 0
                        ? DataManager.get().getStringManager().getSystemString(info.desc, "发动")
                        : "发动";
                options.add("✦ " + descStr);
                final int idx = info.index;
                if (cmdContext == CMD_CONTEXT_BATTLE) {
                    actions.add(() -> activity.sendResponseInt(idx << 16));
                } else {
                    actions.add(() -> activity.sendResponseInt((idx << 16) + 5));
                }
            } else if (matches.size() > 1) {
                for (GameEngine.CmdCardInfo info : matches) {
                    String descStr = info.desc > 0
                            ? DataManager.get().getStringManager().getSystemString(info.desc, "效果")
                            : "效果";
                    options.add("✦ " + descStr);
                    final int idx = info.index;
                    if (cmdContext == CMD_CONTEXT_BATTLE) {
                        actions.add(() -> activity.sendResponseInt(idx << 16));
                    } else {
                        actions.add(() -> activity.sendResponseInt((idx << 16) + 5));
                    }
                }
            }
        }

        if ((flag & GameEngine.COMMAND_ATTACK) != 0) {
            int idx = -1;
            for (int i = 0; i < engine.attackableCards.size(); i++) {
                if (engine.attackableCards.get(i).card == card) {
                    idx = engine.attackableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("⚔ 攻击");
                final int attackIdx = idx;
                actions.add(() -> activity.sendResponseInt((attackIdx << 16) + 1));
            }
        }

        if ((flag & GameEngine.COMMAND_SUMMON) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.summonableCards.size(); i++) {
                if (engine.summonableCards.get(i).card == card) {
                    idx = engine.summonableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("召唤");
                final int summonIdx = idx;
                actions.add(() -> activity.sendResponseInt(summonIdx << 16));
            }
        }

        if ((flag & GameEngine.COMMAND_SPSUMMON) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.spsummonableCards.size(); i++) {
                if (engine.spsummonableCards.get(i).card == card) {
                    idx = engine.spsummonableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("特殊召唤");
                final int spIdx = idx;
                actions.add(() -> activity.sendResponseInt((spIdx << 16) + 1));
            }
        }

        if ((flag & GameEngine.COMMAND_REPOS) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.reposableCards.size(); i++) {
                if (engine.reposableCards.get(i).card == card) {
                    idx = engine.reposableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                String reposText;
                if ((card.position & 0xA) != 0) {
                    reposText = "反转";
                } else if (card.isAttack()) {
                    reposText = "改为守备";
                } else {
                    reposText = "改为攻击";
                }
                options.add(reposText);
                final int reposIdx = idx;
                actions.add(() -> activity.sendResponseInt((reposIdx << 16) + 2));
            }
        }

        if ((flag & GameEngine.COMMAND_MSET) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.msetableCards.size(); i++) {
                if (engine.msetableCards.get(i).card == card) {
                    idx = engine.msetableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("盖放(怪兽)");
                final int msetIdx = idx;
                actions.add(() -> activity.sendResponseInt((msetIdx << 16) + 3));
            }
        }

        if ((flag & GameEngine.COMMAND_SSET) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.ssetableCards.size(); i++) {
                if (engine.ssetableCards.get(i).card == card) {
                    idx = engine.ssetableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("设置(魔陷)");
                final int ssetIdx = idx;
                actions.add(() -> activity.sendResponseInt((ssetIdx << 16) + 4));
            }
        }

        options.add("ℹ 查看卡片信息");
        actions.add(() -> activity.showCardInfoPanel(card));

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle(cardName);
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(R.id.tv_select_title).setVisibility(View.GONE);
        contentView.findViewById(R.id.tv_select_hint).setVisibility(View.GONE);
        contentView.findViewById(R.id.layout_select_buttons).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(R.id.layout_options);

        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(activity);
            btn.setText(options.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(13);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                actions.get(idx).run();
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
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

    // === 阶段按钮 ===

    public void updateActionButtonsForPhase(int phase, boolean isMyTurn) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;

        if (btnPhaseCurrent != null) {
            btnPhaseCurrent.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
        }
        if (btnPhaseCurrent == null) return;

        if (btnPhaseNext != null) btnPhaseNext.setVisibility(View.GONE);
        if (btnEp != null) btnEp.setVisibility(View.GONE);

        switch (dp) {
            case Draw:
                btnPhaseCurrent.setText("DP");
                break;
            case Standby:
                btnPhaseCurrent.setText("SP");
                break;
            case Main1:
                btnPhaseCurrent.setText("M1");
                if (isMyTurn) {
                    if (btnPhaseNext != null) {
                        btnPhaseNext.setText("BP");
                        btnPhaseNext.setVisibility(View.VISIBLE);
                    }
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case BattleStart:
            case BattleStep:
            case Damage:
            case DamageCal:
            case Battle:
                btnPhaseCurrent.setText("BP");
                if (isMyTurn) {
                    if (btnPhaseNext != null) {
                        btnPhaseNext.setText("M2");
                        btnPhaseNext.setVisibility(View.VISIBLE);
                    }
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case Main2:
                btnPhaseCurrent.setText("M2");
                if (isMyTurn) {
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case End:
                btnPhaseCurrent.setText("EP");
                break;
            default:
                btnPhaseCurrent.setText(dp.name());
                break;
        }
    }

    /**
     * 录像回放时仅更新阶段文字，不处理可见性
     */
    public void setPhaseByValue(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (btnPhaseCurrent == null || dp == null) return;
        switch (dp) {
            case Draw:
                btnPhaseCurrent.setText("DP");
                break;
            case Standby:
                btnPhaseCurrent.setText("SP");
                break;
            case Main1:
                btnPhaseCurrent.setText("M1");
                break;
            case BattleStart:
            case BattleStep:
            case Battle:
            case Damage:
            case DamageCal:
                btnPhaseCurrent.setText("BP");
                break;
            case Main2:
                btnPhaseCurrent.setText("M2");
                break;
            case End:
                btnPhaseCurrent.setText("EP");
                break;
            default:
                btnPhaseCurrent.setText(dp.name());
                break;
        }
    }

    public void setPhaseText(String text) {
        if (btnPhaseCurrent != null) btnPhaseCurrent.setText(text);
    }

    /**
     * 对局结束时清空并隐藏阶段按钮（配合 CardDetailPanel.closeGameButtons）
     */
    public void closePhaseButtons() {
        if (btnPhaseCurrent != null) {
            btnPhaseCurrent.setText("");
            btnPhaseCurrent.setVisibility(View.GONE);
        }
        if (btnPhaseNext != null) btnPhaseNext.setVisibility(View.GONE);
        if (btnEp != null) btnEp.setVisibility(View.GONE);
    }

    // === GameFieldView.OnCardClickListener ===

    @Override
    public void onCardClick(int player, int location, int sequence) {
        Log.d(TAG, "Card click: p=" + player + " loc=" + location + " seq=" + sequence);
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.cmdFlag != 0) {
            showCardCommandMenu(card);
            return;
        }
        if (card != null) {
            activity.showCardInfoPanel(card);
        }
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence) {
        Log.d(TAG, "Long press: p=" + player + " loc=" + location + " seq=" + sequence);
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.code > 0) {
            activity.showCardInfoPanel(card);
        }
    }
}
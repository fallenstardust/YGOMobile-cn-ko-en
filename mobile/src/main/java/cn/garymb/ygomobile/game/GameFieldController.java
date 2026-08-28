package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.GameFieldViewController;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import ocgcore.DataManager;
import ocgcore.enums.DuelPhase;

/**
 * 决斗场管理类：卡片/区域点击与长按、卡片命令菜单、放置区域选择、
 * 高亮、连锁动画、cmdContext 状态；
 * 提示信息 + 聊天气泡；
 * 以及阶段按钮（DP/SP/M1/BP/M2/EP 切换与响应）
 * 以及 GameFieldView 的 Canvas 3D 渲染（场地/区域/场上卡/堆叠区/双方手卡）
 * layout_top_info 区域（双方 LP/名字/手卡数/计时/头像 + 回合数）已迁移至 GameTopInfoManager
 */
public class GameFieldController implements GameFieldView.OnCardClickListener {

    private static final String TAG = "YGONativeGame";
    static final int CMD_CONTEXT_IDLE = 1;
    static final int CMD_CONTEXT_BATTLE = 2;

    private final YGOProActivity activity;
    private final Handler mainHandler;
    private final GameTopInfoManager topInfoManager;
    private GameFieldViewController viewController;
    private GameEngine engine;
    private int cmdContext = 0;
    private boolean isPlaceSelecting = false;

    private TextView tvHintMessage;
    private FrameLayout layoutChatMessages;
    private TextView tvChatMessage1, tvChatMessage2;

    // 阶段按钮状态：按钮本体由 GameFieldView 场内绘制（双方怪兽区之间、平行屏幕），
    // 控制器仅维护显示状态并按通讯协议应答点击
    private boolean phaseCurrentVisible = false;
    private String phaseCurrentLabel = "";
    private String phaseNextLabel = "";
    private boolean phaseEpVisible = false;

    public GameFieldController(YGOProActivity activity, Handler mainHandler, GameTopInfoManager topInfoManager) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.topInfoManager = topInfoManager;
    }

    public void create() {
        viewController = new GameFieldViewController(activity);
        bindChatViews();
        setupPhaseButtons();
    }

    private void bindChatViews() {
        tvHintMessage = activity.findViewById(R.id.tv_hint_message);
        layoutChatMessages = activity.findViewById(R.id.layout_chat_messages);
        tvChatMessage1 = activity.findViewById(R.id.tv_chat_message_1);
        tvChatMessage2 = activity.findViewById(R.id.tv_chat_message_2);
    }

    /**
     * 阶段按钮由 GameFieldView 场内绘制：控制器只监听点击并按协议应答。
     * 下一阶段：idle 命令(selectType=11)下 BP→6，battle 命令(selectType=10)下 M2→2；
     * 结束阶段：selectType=10→3，selectType=11→7
     */
    private void setupPhaseButtons() {
        if (viewController == null) return;
        viewController.setPhaseButtonListener(new GameFieldView.OnPhaseButtonListener() {
            @Override
            public void onPhaseNextClicked() {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                if ("BP".equals(phaseNextLabel) && activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(6);
                } else if ("M2".equals(phaseNextLabel) && activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(2);
                }
            }

            @Override
            public void onPhaseEpClicked() {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                if (activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(3);
                } else if (activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(7);
                }
            }
        });
    }

    private void pushPhaseDisplay() {
        if (viewController != null) {
            viewController.setPhaseDisplay(phaseCurrentVisible, phaseCurrentLabel,
                    phaseNextLabel, phaseEpVisible);
        }
    }

    public void init(GameEngine engine, ImageLoader imageLoader) {
        this.engine = engine;
        viewController.init(engine.getField(), imageLoader, this);
    }

    public void show() {
        if (viewController != null) viewController.show();
        if (topInfoManager != null) topInfoManager.show();
    }

    public void hide() {
        if (viewController != null) viewController.hide();
        if (topInfoManager != null) topInfoManager.hide();
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
        phaseCurrentVisible = false;
        phaseNextLabel = "";
        phaseEpVisible = false;
        pushPhaseDisplay();
    }

    public void invalidate() {
        viewController.invalidate();
    }

    public void selectCardWithAutoClear(int controler, int location, int sequence, int durationMs) {
        viewController.selectCardWithAutoClear(controler, location, sequence, durationMs);
    }

    void setCmdContext(int context) {
        cmdContext = context;
    }

    // === 场上命令模式（主阶/战斗阶段：直接点击场上卡片操作，不弹模态对话框） ===

    public void beginIdleCommand() {
        setCmdContext(CMD_CONTEXT_IDLE);
        if (engine == null) return;
        // 与原弹窗逻辑一致：无任何可执行操作时直接结束阶段
        if (!engine.hasIdleCommands()) {
            activity.sendResponseInt(7);
            return;
        }
        // 通讯（MSG_SELECT_IDLE_CMD）允许进入结束阶段
        setEpButtonAllowed(true);
        showHint("点击手牌或场上卡片进行操作", 2500);
    }

    public void beginBattleCommand() {
        setCmdContext(CMD_CONTEXT_BATTLE);
        if (engine == null) return;
        if (!engine.hasBattleCommands()) {
            activity.sendResponseInt(3);
            return;
        }
        // 通讯（MSG_SELECT_BATTLE_CMD）允许进入结束阶段
        setEpButtonAllowed(true);
        showHint("点击卡片进行攻击或发动", 2500);
    }

    // === 放置区域选择 ===

    public void beginPlaceSelect(boolean isDisfield) {
        isPlaceSelecting = true;
        int mask = engine.selectFieldMask;
        viewController.highlightField(mask);
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

    // === 阶段按钮 ===

    /**
     * 按通讯显示/隐藏结束阶段按钮：仅当服务端下发
     * MSG_SELECT_IDLE_CMD(selectType=11) / MSG_SELECT_BATTLE_CMD(selectType=10)
     * 请求本地行动时，才允许进入结束阶段
     */
    public void setEpButtonAllowed(boolean allowed) {
        phaseEpVisible = allowed;
        pushPhaseDisplay();
    }

    public void updateActionButtonsForPhase(int phase, boolean isMyTurn) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;

        phaseCurrentVisible = isMyTurn;
        // 阶段切换即重置：下一阶段/结束阶段按钮由通讯（阶段与指令请求）重新驱动
        phaseNextLabel = "";
        phaseEpVisible = false;

        switch (dp) {
            case Draw:
                phaseCurrentLabel = "DP";
                break;
            case Standby:
                phaseCurrentLabel = "SP";
                break;
            case Main1:
                phaseCurrentLabel = "M1";
                if (isMyTurn) phaseNextLabel = "BP";
                break;
            case BattleStart:
            case BattleStep:
            case Damage:
            case DamageCal:
            case Battle:
                phaseCurrentLabel = "BP";
                if (isMyTurn) phaseNextLabel = "M2";
                break;
            case Main2:
                phaseCurrentLabel = "M2";
                break;
            case End:
                phaseCurrentLabel = "EP";
                break;
            default:
                phaseCurrentLabel = dp.name();
                break;
        }
        pushPhaseDisplay();
    }

    /**
     * 录像回放时仅更新阶段文字，不处理可见性
     */
    public void setPhaseByValue(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;
        switch (dp) {
            case Draw:
                phaseCurrentLabel = "DP";
                break;
            case Standby:
                phaseCurrentLabel = "SP";
                break;
            case Main1:
                phaseCurrentLabel = "M1";
                break;
            case BattleStart:
            case BattleStep:
            case Battle:
            case Damage:
            case DamageCal:
                phaseCurrentLabel = "BP";
                break;
            case Main2:
                phaseCurrentLabel = "M2";
                break;
            case End:
                phaseCurrentLabel = "EP";
                break;
            default:
                phaseCurrentLabel = dp.name();
                break;
        }
        pushPhaseDisplay();
    }

    public void setPhaseText(String text) {
        phaseCurrentLabel = text == null ? "" : text;
        pushPhaseDisplay();
    }

    /**
     * 对局结束时清空并隐藏阶段按钮（配合 CardDetailPanel.closeGameButtons）
     */
    public void closePhaseButtons() {
        phaseCurrentVisible = false;
        phaseCurrentLabel = "";
        phaseNextLabel = "";
        phaseEpVisible = false;
        pushPhaseDisplay();
    }

    // === GameFieldView.OnCardClickListener ===

    @Override
    public void onCardClick(int player, int location, int sequence) {
        Log.d(TAG, "Card click: p=" + player + " loc=" + location + " seq=" + sequence);
        if (engine == null) return;
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.cmdFlag != 0) {
            showCardCommandMenu(card);
            return;
        }
        if (card != null) {
            // 手卡确认由 GameFieldView 场内动画完成（抬高/翻面+虚线框），不弹卡面展示
            if (location == 0x02) return;
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
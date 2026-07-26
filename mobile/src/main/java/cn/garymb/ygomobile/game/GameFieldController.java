package cn.garymb.ygomobile.game;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

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

/**
 * GameFieldView 管理类：卡片/区域点击与长按、卡片命令菜单、放置区域选择、
 * 高亮、连锁动画、cmdContext 状态
 */
public class GameFieldController implements GameFieldView.OnCardClickListener {

    private static final String TAG = "YGONativeGame";
    static final int CMD_CONTEXT_IDLE = 1;
    static final int CMD_CONTEXT_BATTLE = 2;

    private final YGOProActivity activity;
    private GameFieldViewController viewController;
    private GameEngine engine;
    private int cmdContext = 0;
    private boolean isPlaceSelecting = false;

    public GameFieldController(YGOProActivity activity) {
        this.activity = activity;
    }

    public void create() {
        viewController = new GameFieldViewController(activity);
    }

    public void init(GameEngine engine, ImageLoader imageLoader) {
        this.engine = engine;
        viewController.init(engine.getField(), imageLoader, this);
    }

    public void show() {
        if (viewController != null) viewController.show();
    }

    public void hide() {
        if (viewController != null) viewController.hide();
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

    // === 放置区域选择 ===

    public void beginPlaceSelect(boolean isDisfield) {
        isPlaceSelecting = true;
        int mask = engine.selectFieldMask;
        viewController.highlightField(mask);
        String msg = isDisfield ? "请选择要禁用的区域" : "请选择放置位置";
        activity.showHintMessage(msg);
    }

    /**
     * 对应 cancelOrFinish 中 case 18/24：正在选位时发送取消响应。返回是否处理
     */
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
    public void onZoneClick(int player, int location, int sequence) {
        Log.d(TAG, "Zone click: p=" + player + " loc=" + location);
        if (isPlaceSelecting) {
            handlePlaceSelection(player, location, sequence);
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

    private void handlePlaceSelection(int player, int location, int sequence) {
        int bitPos = getZoneBitPos(player, location, sequence);
        if (bitPos < 0 || (engine.selectFieldMask & (1 << bitPos)) == 0) {
            activity.showHintMessage("该区域不可选择");
            return;
        }
        isPlaceSelecting = false;
        viewController.clearHighlight();

        int respPlayer = player;
        int respLocation;
        if (location == 0x04) {
            respLocation = 0x04;
        } else if (location == 0x08) {
            respLocation = 0x08;
        } else {
            respLocation = location;
        }
        int respSeq = sequence;

        if (respPlayer != engine.getClient().selfType) {
            respPlayer = engine.getClient().selfType;
        }

        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) respPlayer);
        buf.put((byte) respLocation);
        buf.put((byte) respSeq);
        engine.sendResponse(buf.array());
    }

    private int getZoneBitPos(int player, int location, int sequence) {
        int base = (player == engine.getClient().selfType) ? 0 : 16;
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
}
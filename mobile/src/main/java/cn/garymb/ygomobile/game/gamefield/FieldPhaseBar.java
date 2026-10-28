package cn.garymb.ygomobile.game.gamefield;

import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;

import ocgcore.enums.DuelPhase;

/**
 * 阶段按钮协作类（由 GameFieldController 按 // === 分栏拆分而来）：
 * 按钮本体由 GameFieldView 场内绘制（双方怪兽区之间、平行屏幕），控制器仅维护显示状态
 * 并按通讯协议应答点击。通过包级私有直连门面 GameFieldController 的 viewController/activity。
 */
class FieldPhaseBar {

    private final GameFieldController ctl;

    // 阶段按钮状态：按钮本体由 GameFieldView 场内绘制（双方怪兽区之间、平行屏幕），
    // 控制器仅维护显示状态并按通讯协议应答点击
    boolean phaseCurrentVisible = false;
    String phaseCurrentLabel = "";
    String phaseNextLabel = "";
    boolean phaseEpVisible = false;

    FieldPhaseBar(GameFieldController ctl) {
        this.ctl = ctl;
    }

    /**
     * 阶段按钮由 GameFieldView 场内绘制：控制器只监听点击并按协议应答。
     * 下一阶段：idle 命令(selectType=11)下 BP→6，battle 命令(selectType=10)下 M2→2；
     * 结束阶段：selectType=10→3，selectType=11→7
     */
    void setupPhaseButtons() {
        if (ctl.viewController == null) return;
        ctl.viewController.setPhaseButtonListener(new GameFieldView.OnPhaseButtonListener() {
            @Override
            public void onPhaseNextClicked() {
                if (ctl.activity.getEngine() == null || ctl.activity.getEngine().getClient() == null)
                    return;
                if ("BP".equals(phaseNextLabel) && ctl.activity.getCurrentSelectType() == 11) {
                    ctl.activity.sendResponseInt(6);
                } else if ("M2".equals(phaseNextLabel) && ctl.activity.getCurrentSelectType() == 10) {
                    ctl.activity.sendResponseInt(2);
                }
            }

            @Override
            public void onPhaseEpClicked() {
                if (ctl.activity.getEngine() == null || ctl.activity.getEngine().getClient() == null)
                    return;
                if (ctl.activity.getCurrentSelectType() == 10) {
                    ctl.activity.sendResponseInt(3);
                } else if (ctl.activity.getCurrentSelectType() == 11) {
                    ctl.activity.sendResponseInt(7);
                }
            }
        });
    }

    void pushPhaseDisplay() {
        if (ctl.viewController != null) {
            ctl.viewController.setPhaseDisplay(phaseCurrentVisible, phaseCurrentLabel,
                    phaseNextLabel, phaseEpVisible);
        }
    }

    /** 设置下一阶段按钮文字（BP/M2），空串表示隐藏；由 idle/battle 指令按通讯可用性驱动 */
    void setNextPhaseButton(String label) {
        phaseNextLabel = label == null ? "" : label;
        pushPhaseDisplay();
    }

    // === 阶段按钮 ===

    /**
     * 即时收起下一阶段(BP/M2)与结束阶段(EP)按钮（对齐 duelclient.cpp
     * DuelClient::SendResponse L4507-4520：对 MSG_SELECT_IDLECMD/MSG_SELECT_BATTLECMD
     * 的任何应答——发动/召唤/盖放/攻击/切阶段——离场瞬间即隐藏 btnBP/btnM2/btnEP，
     * 连锁与效果处理期间不再显示，直至下一条指令通讯（beginIdleCommand/
     * beginBattleCommand）或阶段切换（updateActionButtonsForPhase）重新点亮
     */
    void hideNextAndEpButtons() {
        if (phaseNextLabel.isEmpty() && !phaseEpVisible) return;
        phaseNextLabel = "";
        phaseEpVisible = false;
        pushPhaseDisplay();
    }

    /**
     * 按通讯显示/隐藏结束阶段按钮：仅当服务端下发
     * MSG_SELECT_IDLE_CMD(selectType=11) / MSG_SELECT_BATTLE_CMD(selectType=10)
     * 请求本地行动时，才允许进入结束阶段
     */
    void setEpButtonAllowed(boolean allowed) {
        phaseEpVisible = allowed;
        pushPhaseDisplay();
    }

    void updateActionButtonsForPhase(int phase, boolean isMyTurn) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;

        // Always show current phase indicator for both players' turns (communication-driven phase display)
        phaseCurrentVisible = true;
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
                // BP 不再于阶段切换时无条件显示，改由 beginIdleCommand 依通讯(MSG_SELECT_IDLECMD btnBP)驱动
                break;
            case BattleStart:
            case BattleStep:
            case Battle:
            case Damage:
            case DamageCal:
                phaseCurrentLabel = "BP";
                // M2 改由 beginBattleCommand 依通讯(MSG_SELECT_BATTLECMD btnM2)驱动
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
    void setPhaseByValue(int phase) {
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

    void setPhaseText(String text) {
        phaseCurrentLabel = text == null ? "" : text;
        pushPhaseDisplay();
    }

    /**
     * 对局结束时清空并隐藏阶段按钮（配合 CardDetailPanel.closeGameButtons）
     */
    void closePhaseButtons() {
        phaseCurrentVisible = false;
        phaseCurrentLabel = "";
        phaseNextLabel = "";
        phaseEpVisible = false;
        pushPhaseDisplay();
    }

    /** 清场（GameFieldController.hide）：重置阶段按钮显示状态并刷新 */
    void resetOnHide() {
        phaseCurrentVisible = false;
        phaseNextLabel = "";
        phaseEpVisible = false;
        pushPhaseDisplay();
    }
}

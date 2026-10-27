package cn.garymb.ygomobile.game.engine;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.ui.dialogs.duel.DuelLogDialog;
import ocgcore.DataManager;

/** 网络对局消息的 UI 处理实现（自 GameMessageParser 拆出，对齐 gframe duelclient.cpp ClientAnalyze）：场地事件类 handler 由 DuelEventHandler 转发至此，共享状态经 engine 访问。 */
public class MessageUIHandler {
    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "GameMsgParser";

    private final GameEngine engine;

    /** 网络对局 UI 消息处理构造（GameEngine 装配） */
    public MessageUIHandler(GameEngine engine) {
        this.engine = engine;
    }

    public void onRetry() {
        if (engine.replayMode) return; // 回放：应答已录制在文件里，重放缓存消息无意义
        Log.w(TAG, "Retry message received");
        // 对齐 duelclient.cpp L1320-1404：无效应答后服务端只回 MSG_RETRY 不重发原 SELECT，重放缓存上一条消息重建选择 UI 供重新应答（否则选择弹窗永久丢失、对局冻结）；桌面端不显示提示栏文本
        engine.replayLastGameMsg();
    }

    public void onHint(int type, int player, int data) {
        // 诊断：所有 MSG_HINT 落盘一条（旧版 constant.lua 无 HINT_ZONE 时 Duel.Hint 经 lua_tointeger(nil)=0 降级 type=0，此处可见真实 type 以定位脚本包问题）
        Log.i(TAG, "hint type=" + type + " player=" + player
                + " data=0x" + Integer.toHexString(data));
        String hintText = "";
        switch (type) {
            // HINT_EVENT（对齐 duelclient.cpp L1445-1447）：静默写入 event_string=GetDesc(data)，不弹提示
            case 1:
                engine.field.eventString = DataManager.get().getDesc(data, "");
                return;
            // HINT_MESSAGE（duelclient.cpp L1449-1456）：文本为 GetDesc(data)，取自 strings.conf 上提示栏
            case 2:
                hintText = DataManager.get().getDesc(data, "");
                break;
            case 3:
                // HINT_SELECTMSG：保存下一条选择对话框标题的 sys 索引，消费语义同 gframe select_hint（L1458-1461）
                engine.field.selectHint = data;
                return;
            // HINT_OPSELECTED（对齐 duelclient.cpp L1463-1472）：记「已选择」日志 + 居中 AC 文本动画
            case 4:
                showActionMessage(DuelLogDialog.addOpSelectedLog(data));
                return;
            // HINT_EFFECT（L1473-1478）：桌面端播效果图标动画，无提示栏文本，不显示
            case 5:
                return;
            // HINT_RACE（对齐 duelclient.cpp L1480-1490）：宣告种族记日志 + 居中 AC 文本动画
            case 6:
                showActionMessage(DuelLogDialog.addSelectedRaceLog(data));
                return;
            // HINT_ATTRIB（对齐 duelclient.cpp L1491-1501）：宣告属性记日志 + 居中 AC 文本动画
            case 7:
                showActionMessage(DuelLogDialog.addSelectedAttributeLog(data));
                return;
            // HINT_CODE（对齐 duelclient.cpp L1502-1511）：宣言卡名记日志（sys1511「玩家宣言了」），携带卡代码供点击查看 + 居中 AC 文本动画
            case 8: {
                String acText = DuelLogDialog.formatDeclared(DataManager.get().getName(data));
                DuelLogDialog.addLog(acText, data);
                showActionMessage(acText);
                return;
            }
            // HINT_NUMBER（对齐 duelclient.cpp L1512-1521）：宣告数字记日志（sys1512「玩家选择了」）+ 居中 AC 文本动画
            case 9: {
                String acText = DuelLogDialog.sysFormat(1512, "已选择数字：%d", data);
                DuelLogDialog.addLog(acText);
                engine.soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
                showActionMessage(acText);
                return;
            }
            // HINT_ZONE（duelclient.cpp L1529-1572）：Duel.Hint(HINT_ZONE,tp,mask) 宣告区域，掩码按 LocalPlayer 交换两半后仅展开 SZONE 位为同纵列魔陷格玫红呼吸标注至下个 NEW_TURN，MZ 位封锁类在 applyHintZone 内跳过（白X同纵列魔陷格不得误显泡影提示）；case 0 为旧版 constant.lua 无 HINT_ZONE 时 lua_tointeger 降级 type=0 的兼容分支
            case 0:
            case 11: {
                int zones = data;
                Log.i(TAG, "HINT_ZONE player=" + player + " raw=0x" + Integer.toHexString(zones)
                        + " local=" + engine.localPlayer(player));
                if (engine.localPlayer(player) == 1)
                    zones = (zones >>> 16) | (zones << 16);
                engine.field.applyHintZone(zones);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onFieldChanged();
                });
                return;
            }
            // 其余 HINT（HINT_CARD=10 及未知类型）：桌面端为图标动画/区域高亮/日志，不弹调试文本
            default:
                Log.d(TAG, "Unhandled hint type=" + type + " data=" + data);
                return;
        }
        final String finalHint = hintText;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHintMessage(finalHint);
        });
    }

    /** MSG_HINT 居中消息文本动画（duelclient.cpp L1463-1521：SetStaticText+PopupElement+WaitFrameSignal(40)）：网络线程调用，经 UI 层 SpecEffectOverlay 于右侧中央以 12sp 小字展示，并设约 40 帧动画屏障使 isAnyAnimationBusy 在持有期内恒真以串行插入后续消息。 */
    private void showActionMessage(String text) {
        if (text == null || text.isEmpty()) return;
        if (engine.replaySkip) return; // 回放快进：丢弃居中消息文本，不占用动画屏障
        final String acText = text;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onActionMessage(acText);
        });
        engine.animHoldUntilMs = Math.max(engine.animHoldUntilMs,
                System.currentTimeMillis() + 40L * 17L);
    }

    public void onWaiting() {
        if (engine.replayMode) return; // 回放：无真实等待方，启动等待轮换提示会悬挂
        Log.d(TAG, "Waiting...");
        // 对齐 duelclient.cpp L1610-1616 + game.cpp L1624-1633：waitFrame=0，显示"等待行动中..."并轮换
        engine.hintManager.startWaitHint();
    }

    public void onStart(int playerType, int duelRule, int lp0, int lp1, int deck0, int extra0, int deck1, int extra1) {
        engine.field.clear();
        engine.inDuel = true;
        engine.siding = false;
        engine.field.setDuelRule(duelRule);
        // 回放视角恒以录制者为参照（ReplayPlayer.startSession 置恒等映射/切换翻转），不得由 MSG_START 先攻方重新推断，否则 undo/重放重投本消息会把用户已切视角静默翻回
        if (!engine.replayMode) {
            engine.duelIsFirst = (playerType & 1) == 0;
        }
        // tag_player 初始化（duelclient.cpp L1644-1649：isTag 非先动队首回合由队友先打），后续由 MSG_NEW_TURN 逐回合翻转
        if (engine.field.isTag) {
            engine.tagPlayer[0] = false;
            engine.tagPlayer[1] = false;
            if (engine.duelIsFirst) {
                engine.tagPlayer[1] = true;
            } else {
                engine.tagPlayer[0] = true;
            }
        }
        int p0 = engine.localPlayer(0);
        int p1 = engine.localPlayer(1);
        engine.playerInfos[p0].lp = lp0;
        engine.playerInfos[p1].lp = lp1;
        engine.playerInfos[p0].startLp = lp0;
        engine.playerInfos[p1].startLp = lp1;
        engine.field.players[p0].lp = lp0;
        engine.field.players[p1].lp = lp1;
        engine.field.dInfo.startLp = Math.max(lp0, lp1);
        engine.field.dInfo.lp[p0] = lp0;
        engine.field.dInfo.lp[p1] = lp1;
        // ClientField::Initial：为双方卡组/额外创建全部 ClientCard（背面朝下、带堆叠高度）
        engine.field.initial(p0, deck0, extra0, 0);
        engine.field.initial(p1, deck1, extra1, 0);
        // 回放（含带消息流录像 MSG_START）不切 DUELING 状态：该状态会经 EngineCallbackDelegate 弹底部行动区，回放左侧面板只应显示录像控制条
        if (!engine.replayMode) {
            engine.setState(GameEngine.GameState.DUELING);
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(0);
                engine.listener.onPlayerInfoUpdated(1);
            }
        });
    }

    public void onWin(int player, int reason) {
        // 回放结算由 ReplayPlayer 的 MSG_WIN → listener.onReplayFinished 路径负责，不走现网比分累计与结算弹窗
        if (engine.replayMode) return;
        // 场景 BGM 胜负切换统一由 UI 层集中决策（game.cpp Game::playBGM）：经 onDuelResult → YGOProActivity 处理，此处不切歌
        engine.currentMatch++;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onDuelResult(player, reason);
        });
    }

    public void onUpdateData(int player, int location, ByteBuffer data) {
        engine.dataParser.parseUpdateData(engine.localPlayer(player), location, data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        engine.dataParser.parseUpdateCard(engine.localPlayer(player), location, sequence, data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    public void onRequestDeck(int player) {
        if (engine.replayMode) return; // 回放：卡组公开由消息流自身保证，不进入 DECK_SELECT 状态
        engine.setState(GameEngine.GameState.DECK_SELECT);
    }

    public void onSelectBattleCmd(ByteBuffer data) {
        if (engine.replayMode) return; // 回放：SELECT 询问已录制应答，不弹选择窗不发提示
        engine.field.pendulumSelectPending = false; // 进入战斗指令 = 灵摆召唤流程已结束/取消
        engine.dataParser.parseBattleCmd(data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(10, null);
        });
    }

    public void onSelectIdleCmd(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.field.pendulumSelectPending = false; // 回到待机指令 = 灵摆召唤流程已结束/取消
        engine.dataParser.parseIdleCmd(data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(11, null);
        });
    }

    public void onSelectEffectYn(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(12, data);
        });
    }

    public void onSelectYesNo(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(13, data);
        });
    }

    public void onSelectOption(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(14, data);
        });
    }

    public void onSelectCard(ByteBuffer data) {
        if (engine.replayMode) return;
        // duelclient.cpp L1958-1978：panelmode（候选含非场上区卡/手牌拥挤）时文案进弹窗标题，仅非 panelmode 由 stHintMsg 显示"提示(min-max)"
        if (!engine.hintManager.selectCardPanelMode(data, 4, 1)) {
            String hint = engine.hintManager.selectRangeHint(data, 560, "选择卡片");
            if (hint != null) engine.hintManager.postDuelHint(hint);
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(15, data);
        });
    }

    public void onSelectChain(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.field.pendulumSelectPending = false; // 连锁窗口 = 灵摆放置询问已结束/取消
        // 对齐 duelclient.cpp L2158-2163：存在"发动并作为连锁"项(EDESC_OPERATION=1)时用 556，否则 550
        boolean contiExist = false;
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.get(); // selecting_player
            int count = dup.get() & 0xFF;
            dup.get(); // specount
            dup.getInt(); // hint0
            dup.getInt(); // hint1
            for (int i = 0; i < count && dup.remaining() >= 14; i++) {
                int flag = dup.get() & 0xFF;
                dup.get(); // forced
                dup.getInt(); // code
                dup.position(dup.position() + 4); // c l s ss
                dup.getInt(); // desc
                if ((flag & 0x1) != 0) contiExist = true;
            }
        } catch (Exception ignored) {
        }
        engine.hintManager.postDuelHint(engine.hintManager.sysString(contiExist ? 556 : 550,
                contiExist ? "选择发动效果并作为连锁" : "选择发动效果"));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(16, data);
        });
    }

    public void onSelectPlace(int player, int count, int fieldMask) {
        if (engine.replayMode) return;
        engine.clearCommandFlags();
        engine.selectFieldPlayer = player;
        engine.selectFieldCount = count;
        engine.selectFieldMask = ~fieldMask;
        // fieldMask 低 16 位=选择方半场，仅选择方与我不同半场时交换高低 16 位使低位恒为我方；用 localPlayer 判断（player&1 取边以兼容 tag）
        if (engine.localPlayer(player & 1) == 1) {
            engine.selectFieldMask = (engine.selectFieldMask >>> 16) | (engine.selectFieldMask << 16);
        }
        // 对齐 duelclient.cpp L2199-2208：MSG_SELECT_PLACE 提示 sys569「请选择[%ls]的位置」（select_hint 此时是卡号）/ sys560
        if (engine.field.selectHint > 0) {
            engine.hintManager.postDuelHint(DataManager.get().formatSystemString(569, "请选择[%s]的位置",
                    DataManager.get().getName(engine.field.selectHint)));
        } else {
            engine.hintManager.postDuelHint(engine.hintManager.sysString(560, "请选择放置位置"));
        }
        engine.field.selectHint = 0;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(18, null);
        });
    }

    public void onSelectPosition(int player, int code, int positions) {
        if (engine.replayMode) return; // 单一形式时的自动应答分支也必须拦截，否则会向服务器发 Response
        positions &= 0x0F;
        // duelclient.cpp L2275-2278：仅一种表示形式可选时直接以该形式应答，不弹窗
        if (positions == 0x1 || positions == 0x2 || positions == 0x4 || positions == 0x8) {
            ByteBuffer resp = ByteBuffer.allocate(4);
            resp.order(ByteOrder.LITTLE_ENDIAN);
            resp.putInt(positions);
            // 经 engine.sendResponse 统一路由：残局态转 singleRunner.submitResponse 喂引擎，直连 client.sendResponse 会掉应答致单一姿态永久悬挂
            engine.sendResponse(resp.array());
            return;
        }
        // 打包 code(4) + positions(4) 传给 UI 层：用于显示卡图与按位掩码显示形式按钮
        ByteBuffer data = ByteBuffer.allocate(8);
        data.order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(code);
        data.putInt(positions);
        data.flip();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(19, data);
        });
    }

    public void onSelectTribute(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 duelclient.cpp L2330-2335：stHintMsg 显示"提示(min-max)"（hint 优先 selectHint，默认 531）
        String hint = engine.hintManager.selectRangeHint(data, 531, "解放选择");
        if (hint != null) engine.hintManager.postDuelHint(hint);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(20, data);
        });
    }

    public void onSortChain(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(21, data);
        });
    }

    public void onSelectCounter(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 duelclient.cpp L2362-2365：stHintMsg 显示 GetSysString(204)（移除 N 个指示物）
        String hint = engine.hintManager.counterHint(data);
        if (hint != null) engine.hintManager.postDuelHint(hint);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(22, data);
        });
    }

    public void onSelectSum(ByteBuffer data) {
        if (engine.replayMode) return;
        // client_field.cpp L1092-1118 ShowSelectSum：提示文案 "hint(当前值/目标值)" 由选择会话建立时展示，此处不预先显示裸标题（C++ 无此行为）
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(23, data);
        });
    }

    public void onSelectDisfield(int player, int count, int fieldMask) {
        if (engine.replayMode) return;
        engine.clearCommandFlags();
        engine.selectFieldPlayer = player;
        engine.selectFieldCount = count;
        engine.selectFieldMask = ~fieldMask;
        if (engine.localPlayer(player & 1) == 1) {
            engine.selectFieldMask = (engine.selectFieldMask >>> 16) | (engine.selectFieldMask << 16);
        }
        // 对齐 duelclient.cpp L2205-2210：MSG_SELECT_DISFIELD 提示 GetDesc(select_hint ?: 570)
        int hint = engine.field.selectHint > 0 ? engine.field.selectHint : 570;
        engine.hintManager.postDuelHint(DataManager.get().getDesc(hint, "请选择要禁用的区域"));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(24, null);
        });
    }

    public void onSortCard(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(25, data);
        });
    }

    public void onSelectUnselectCard(ByteBuffer data) {
        if (engine.replayMode) return;
        // 问题②：检测本消息是否为灵摆召唤的手卡选怪兽阶段，置起 pendulumSelectPending 门控后续闪动动画
        detectPendulumSelect(data);
        // duelclient.cpp L2022-2069：panelmode 遍历两批候选（头为 player/finishable/cancelable/min/max），仅非 panelmode 显示提示栏
        if (!engine.hintManager.selectCardPanelMode(data, 5, 2)) {
            String hint = engine.hintManager.selectRangeHint(data, 560, "选择卡片", 3);
            if (hint != null) engine.hintManager.postDuelHint(hint);
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(26, data);
        });
    }

    /**
     * 检测本次 MSG_SELECT_UNSELECT_CARD 是否为灵摆召唤的"从手卡选怪兽"阶段
     *（对齐 ocgcore operations.cpp PROC_G L3011-3093 的 UNSELECT_CARD→SELECT_PLACE 序列）：
     * 候选卡含 LOCATION_HAND 且选择方两侧灵摆区有卡时置 pendulumSelectPending=true，
     * 后续 MSG_SELECT_PLACE 据此门控灵摆闪动预演。
     */
    private void detectPendulumSelect(ByteBuffer data) {
        GameField field = engine.field;
        field.pendulumSelectPending = false;
        if (data == null || data.remaining() < 6) return;
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            int selectingPlayer = engine.localPlayer(dup.get() & 0xFF); // player
            dup.get(); // finishable
            dup.get(); // cancelable
            dup.get(); // min
            dup.get(); // max
            int count1 = dup.get() & 0xFF;
            boolean hasHand = false;
            for (int i = 0; i < count1 && dup.remaining() >= 8; i++) {
                dup.getInt(); // code
                int ctrl = engine.localPlayer(dup.get() & 0xFF);
                int loc = dup.get() & 0xFF;
                dup.get(); // seq
                dup.get(); // subseq
                if ((loc & 0x02) != 0 && ctrl == selectingPlayer) {
                    hasHand = true;
                }
            }
            if (!hasHand) return;
            // 检查两侧灵摆区是否有卡（MR4: szone 0/4，旧规则: szone 6/7）
            int duelRule = field.dInfo.duelRule;
            int leftSeq = duelRule >= 4 ? 0 : 6;
            int rightSeq = duelRule >= 4 ? 4 : 7;
            if (selectingPlayer < 0 || selectingPlayer > 1) return;
            GameField.PlayerField pf = field.players[selectingPlayer];
            if (pf == null) return;
            ClientCard left = pf.spellZone.size() > leftSeq ? pf.spellZone.get(leftSeq) : null;
            ClientCard right = pf.spellZone.size() > rightSeq ? pf.spellZone.get(rightSeq) : null;
            if (left != null && left.code != 0 && right != null && right.code != 0) {
                field.pendulumSelectPending = true;
            }
        } catch (Exception ignored) {
        }
    }
}

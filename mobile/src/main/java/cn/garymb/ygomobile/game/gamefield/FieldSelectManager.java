package cn.garymb.ygomobile.game.gamefield;
import cn.garymb.ygomobile.game.engine.GameEngine;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.duel.CardSelectDialog;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;
/**
 * 场上/手牌选择会话协作类（由 GameFieldController 按 // === 分栏拆分而来）：
 * 放置区域选择、场上/手牌直接选择、场上/手牌合计选择（MSG_SELECT_SUM）。
 * 通过包级私有直连门面 GameFieldController 的共享状态（engine/viewController/activity）
 * 与底层提示/命令菜单原语（ctl.showHint/ctl.showCardCommandMenu/ctl.dismissCmdMenu）。
 */
class FieldSelectManager extends FieldSelectManagerBase {

    /**
     * Constructor (package entry of the split chain, called by GameFieldController;
     * the ctl assignment lives in the base layer).
     */
    FieldSelectManager(GameFieldController ctl) {
        super(ctl);
    }

    // === 放置区域选择 ===

    void beginPlaceSelect(boolean isDisfield) {
        // 灵摆召唤的位置选择先播放“两侧灵摆刻度卡明暗脉冲+青色高亮连续闪动 3 轮 →
        // 场上可放置区蚂蚁线供选点”的预演（对齐用户描述的灵摆召唤动画）；非灵摆召唤直接
        // 进入真正的位置选择会话（闪动样式取自 duelclient.cpp MSG_BECOME_TARGET L3502-3524
        // 的 is_highlighting + FadeCard(5↔255)×3，描边颜色取自 drawing.cpp L646-651）
        if (!isDisfield && startPendulumScalePrelude(() -> doBeginPlaceSelect(false))) {
            return; // 预演结束后会自动回调 doBeginPlaceSelect
        }
        doBeginPlaceSelect(isDisfield);
    }

    /**
     * 若本次 MSG_SELECT_PLACE 为灵摆召唤（灵摆召唤方两侧灵摆区都立着灵摆怪、
     * 且放置掩码含怪兽区），则先对左/右刻度卡同时做 C++ 同款 FadeCard(5↔255)×3
     * 明暗脉冲 + is_highlighting 的青色轮廓闪烁，再高亮场上可放置区。
     * <p>
     * 完全对齐 C++ duelclient.cpp MSG_BECOME_TARGET L3488-3527：is_highlighting=true 后渲染层绘
     * 青色轮廓（drawing.cpp L646-651 的 0xff00ffff），场上表侧卡 for(j<3){FadeCard(5,5);Wait(5);
     * FadeCard(255,5);Wait(5);}。alpha 取 C++ 原值 5/255：curAlpha=5 → 0.0196 仍在 drawCard 的
     * 早退阈值（0.01f）之上，卡面会真的“消失—再现”，与 PC 版观感一致。
     *
     * @param onDone 预演播完后在主线程执行的后继动作（开启选格会话，或完成自动放置应答）
     * @return true=已启动预演（后继动作将在预演末尾执行）；false=非灵摆召唤，调用方需立即自行执行
     */
    protected boolean startPendulumScalePrelude(final Runnable onDone) {
        GameEngine engine = ctl.engine;
        if (engine == null) return false;
        GameField field = engine.getField();
        // 问题②门控：仅在上游 onSelectUnselectCard 检测到灵摆召唤流程（手卡候选+两侧刻度存在）
        // 置起 pendulumSelectPending 后才允许闪动，非灵摆召唤的 MSG_SELECT_PLACE 不播放
        if (!field.pendulumSelectPending) return false;
        // 召唤方推断：引擎引入 EFFECT_OPPO_SELECT_SPSUMMON_ZONE 后，MSG_SELECT_PLACE 的 player
        // 是「选择方」而非「召唤方」（ocgcore operations.cpp move_to_field step1：
        // select_player = 1 - move_player），再按 selectFieldPlayer 取半场会读到选择方那侧的刻度。
        // 灵摆召唤只能由当回合方进行，故取 field.currentPlayer（本地视角的当回合方），同时覆盖
        // 「自己选位」「对手替我选」「solo 下我替对手选」三条路径；越界时退回旧口径
        int summonLocal = field.currentPlayer;
        if (summonLocal < 0 || summonLocal > 1) summonLocal = engine.localPlayer(engine.selectFieldPlayer & 1);
        if (summonLocal < 0 || summonLocal > 1) return false;
        GameField.PlayerField pf = field.players[summonLocal];
        if (pf == null) return false;
        // 灵摆刻度所在的 spellZone 下标按规则分流（对齐 ocgcore field.cpp get_pzone_sequence
        // L499-512：MR4 灵摆区 = szone 0/4，旧规则 = szone 6/7；gframe drawing.cpp L696-718 同此）。
        // 旧实现写死 6/7，在当前恒为 MR4 的环境下取到的是两张空格，预演条件直接不成立，
        // 动画从未播放——即“闪动看不见”的根因
        int[] scaleSeq = pendulumScaleSeq(field.dInfo.duelRule);
        ClientCard left = asActivePendulumScale(pf, scaleSeq[0]);
        ClientCard right = asActivePendulumScale(pf, scaleSeq[1]);
        if (left == null || right == null) return false; // 灵摆召唤需两侧刻度都立着
        // 放置掩码须含怪兽区：怪兽区掩码位是 0-6（MR4 的 6/7 位为额外怪兽区，灵摆召唤
        // 完全可能落在那里），口径与 gframe duelclient.cpp MSG_SELECT_PLACE 自动放置的
        // selectable_field & 0x7f 判定一致（L2218）；旧实现只测 0x1f，仅剩额外怪兽区可选
        // 时预演被误判为「非灵摆召唤」而不播
        int mask = engine.selectFieldMask;
        if ((mask & (0x7f | 0x7f0000)) == 0) return false;
        // 一次灵摆召唤会为每只被特殊召唤的怪兽各下发一条 MSG_SELECT_PLACE（ocgcore
        // special_summon 逐张 add SPSUMMON_STEP，每张一次 PROCESSOR_SELECT_PLACE），若不
        // 抑制就会每只闪一次；仅在同批首次询问闪动，两侧刻度对象未换期间不再重复，
        // 整组特殊召唤结束（MSG_SPSUMMONED）后由 onSpSummoned 清空记录
        if (left == field.pendulumPreludeL && right == field.pendulumPreludeR) return false;
        field.pendulumPreludeL = left;
        field.pendulumPreludeR = right;
        final ClientCard[] scales = {left, right};
        final int rounds = 3;                // C++ L3508 for (int j = 0; j < 3; ++j)
        final int pulseFrames = 5;           // C++ L3510/3512 FadeCard(pcard, 5/255, 5)
        final long stepMs = pulseFrames * 17L; // 本项目 1 帧 = 17ms，等价 C++ WaitFrameSignal(5)
        // 预演共耗时约 0.51s（3 轮 × 淡出/淡入各 5 帧），其间本次询问可能被撤回（局面回退、
        // 询问重挂）：捕获询问代次令牌，到期时代次已变则整段预演作废——否则它会拿已失效的
        // selectFieldMask 开启一个旧询问语义的选格会话，玩家随后的点击会把选格应答发进新到达
        // 的其他询问里
        final long token = engine.captureQuestionToken();
        for (ClientCard c : scales) c.is_highlighting = true;   // C++ L3502-3503：设置青色高亮框
        final int[] stage = {0};
        final Runnable[] ticker = new Runnable[1];
        ticker[0] = new Runnable() {
            @Override
            public void run() {
                if (engine.isQuestionStale(token)) { // 询问已撤回：复位脉冲态并放弃预演
                    endPendulumScalePrelude(scales, true);
                    return;
                }
                if (stage[0] >= rounds * 2) {        // 3 轮脉冲播完，交回后继动作
                    endPendulumScalePrelude(scales, false);
                    onDone.run();
                    return;
                }
                // C++ L3510-3513：奇偶台阶交替淡到 5 / 回到 255，两侧刻度同起同落
                int alpha = (stage[0]++ & 1) == 0 ? 5 : 255;
                for (ClientCard c : scales) field.fadeCard(c, alpha, pulseFrames);
                if (ctl.viewController != null) ctl.viewController.invalidate();
                engine.mainHandler.postDelayed(ticker[0], stepMs);
            }
        };
        ticker[0].run();
        return true;
    }

    /** 预演收尾：清两侧刻度的青色高亮框；stale 时把脉冲中途的透明度复位，防卡面残留半透明 */
    private void endPendulumScalePrelude(ClientCard[] scales, boolean stale) {
        for (ClientCard c : scales) {
            c.is_highlighting = false;
            if (stale && c.is_fading) {
                c.is_fading = false;
                c.curAlpha = 255f;
            }
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
    }

    /** 灵摆刻度所在 spellZone 下标：MR4（duel_rule>=4）为 0(左)/4(右)，旧规则为 6(左)/7(右)。
     *  对齐 ocgcore field.cpp get_pzone_sequence L499-512 与 gframe drawing.cpp L696-718。 */
    protected static int[] pendulumScaleSeq(int duelRule) {
        return duelRule >= 4 ? new int[]{0, 4} : new int[]{6, 7};
    }

    /** 取灵摆区指定序号上已揭示的灵摆怪（非空、code!=0、TYPE_PENDULUM），否则 null */
    protected static ClientCard asActivePendulumScale(GameField.PlayerField pf, int seq) {
        if (pf.spellZone.size() <= seq) return null;
        ClientCard c = pf.spellZone.get(seq);
        if (c == null || c.code == 0) return null;
        // ClientCard.type may remain 0: C++ ClientCard::SetCode queries local DB for type,
        // but this Java port's setCode only writes code. Fallback to DB lookup to align.
        long type = c.type;
        if (type == 0) {
            Card cardData = DataManager.get().getCardManager().getCard(c.code);
            if (cardData != null) type = cardData.Type;
        }
        if ((type & CardType.Pendulum.getId()) == 0) return null;
        return c;
    }

    protected void doBeginPlaceSelect(boolean isDisfield) {
        isPlaceSelecting = true;
        // 选格询问取代旧的指示物取除会话：旧会话的候选标记已由上一条应答的 ClearSelect 清掉，
        // 此处只需关闭点击路由入口，不再把本会话的点击当取除处理
        isCounterSelecting = false;
        int mask = ctl.engine.selectFieldMask;
        int count = ctl.engine.selectFieldCount;
        placeSelectRemain = count > 0 ? count : 1;
        placeSelectCancelable = count == 0;
        selectedFieldBits = 0;
        ctl.viewController.highlightField(mask);
        // 提示栏文本由 GameMessageParser 的 onSelectPlace/onSelectDisfield 走 strings.conf
        // 系统字符串下发，此处不再叠加硬编码引导（对齐 gframe：仅 stHintMsg 一处来源）
        // count==0（select_cancelable）→ 显示「取消」按钮（对齐 gframe ShowCancelOrFinishButton(1)）
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) panel.updateCancelOrFinishButton(false, placeSelectCancelable, false);
    }

    /**
     * 自动放置（复刻 gframe duelclient.cpp MSG_SELECT_PLACE 自动放置 L2211-2264）：
     * chkMAutoPos/chkSTAutoPos 勾选且可选区域含怪兽区/魔陷区时，按优先级
     *（我方怪兽→我方魔陷→我方灵摆→对方怪兽→对方魔陷→对方灵摆）选区自动应答
     * byte[3]{player, location, sequence}；chkRandomPos 决定普通区是否随机取位
     *
     * @return true=已自动放置并应答，false=需弹窗手动选择
     */
    boolean tryAutoPlaceSelect() {
        if (ctl.engine == null) return false;
        // 多区域（count>1）需玩家逐个点选，自动放置只适用于单区域
        if (ctl.engine.selectFieldCount > 1) return false;
        AppsSettings settings = AppsSettings.get();
        int mask = ctl.engine.selectFieldMask;
        // 对齐 gframe 条件：怪兽区可选(0x7f007f)看 chkMAutoPos，否则看 chkSTAutoPos
        if ((mask & 0x7f007f) != 0) {
            if (settings.getIntSettings("chkMAutoPos", 0) != 1) return false;
        } else {
            if (settings.getIntSettings("chkSTAutoPos", 0) != 1) return false;
        }
        // 勾选自动放置时本次询问直接应答、不进入选格会话，刻度闪动会因此一帧都不播
        //（“看不见灵摆召唤动画”的另一条路径）：这里同样先走预演，末尾回到 doAutoPlace 取位应答
        if (startPendulumScalePrelude(this::doAutoPlace)) return true;
        return doAutoPlace();
    }

    /** 自动放置的取位与应答本体（原 tryAutoPlaceSelect 主体，对齐 duelclient.cpp L2211-2264） */
    private boolean doAutoPlace() {
        AppsSettings settings = AppsSettings.get();
        int mask = ctl.engine.selectFieldMask;

        int filter;
        int respLocation;
        int respPlayer;
        boolean pzone;
        if ((mask & 0x7f) != 0) {
            respPlayer = ctl.engine.localPlayer(0);
            respLocation = 0x04;
            filter = mask & 0x7f;
            pzone = false;
        } else if ((mask & 0x3f00) != 0) {
            respPlayer = ctl.engine.localPlayer(0);
            respLocation = 0x08;
            filter = (mask >> 8) & 0x3f;
            pzone = false;
        } else if ((mask & 0xc000) != 0) {
            respPlayer = ctl.engine.localPlayer(0);
            respLocation = 0x08;
            filter = (mask >> 14) & 0x3;
            pzone = true;
        } else if ((mask & 0x7f0000) != 0) {
            respPlayer = ctl.engine.localPlayer(1);
            respLocation = 0x04;
            filter = (mask >> 16) & 0x7f;
            pzone = false;
        } else if ((mask & 0x3f000000) != 0) {
            respPlayer = ctl.engine.localPlayer(1);
            respLocation = 0x08;
            filter = (mask >> 24) & 0x3f;
            pzone = false;
        } else if ((mask & 0xc0000000) != 0) {
            respPlayer = ctl.engine.localPlayer(1);
            respLocation = 0x08;
            filter = (mask >>> 30) & 0x3;
            pzone = true;
        } else {
            return false;
        }

        int seq;
        if (!pzone) {
            if (settings.getIntSettings("chkRandomPos", 0) == 1) {
                // 随机取位（对齐 gframe chkRandomPos：dist(0,6) 直到命中可选位）
                do {
                    seq = random.nextInt(7);
                } while ((filter & (1 << seq)) == 0);
            } else {
                // 固定次序（对齐 gframe：0x40→6, 0x20→5, 0x4→2, 0x2→1, 0x8→3, 0x1→0, 0x10→4）
                if ((filter & 0x40) != 0) seq = 6;
                else if ((filter & 0x20) != 0) seq = 5;
                else if ((filter & 0x4) != 0) seq = 2;
                else if ((filter & 0x2) != 0) seq = 1;
                else if ((filter & 0x8) != 0) seq = 3;
                else if ((filter & 0x1) != 0) seq = 0;
                else seq = 4;
            }
        } else {
            // 灵摆区：序列固定 6(左)/7(右)，gframe 不对灵摆区随机取位
            seq = (filter & 0x1) != 0 ? 6 : 7;
        }

        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) respPlayer);
        buf.put((byte) respLocation);
        buf.put((byte) seq);
        ctl.engine.sendResponse(buf.array());
        return true;
    }

    boolean cancelPlaceSelect() {
        if (!isPlaceSelecting) return false;
        // 协议应答需要协议侧玩家索引：localPlayer 为对合映射，
        // localPlayer(0) 即我方对应的协议索引（先攻=0/后攻=1），
        // selfType 是座位号，换座场景下不能直接使用
        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) ctl.engine.localPlayer(0));
        buf.put((byte) 0);
        buf.put((byte) 0);
        ctl.engine.sendResponse(buf.array());
        finishPlaceSelect();
        return true;
    }

    /**
     * 撤回后无应答复位一切进行中的选择会话（选格 / 选卡 / 合计 / UNSELECT）。
     * 与 {@link #cancelPlaceSelect()} 的区别：本方法只清状态，绝不对服务端发任何应答——
     * 这些会话由已被撤销的询问建立，任其存活则下一次点击会把旧询问语义的应答（选格三元组、
     * 选卡索引）发进回退后的新局面 → 非法应答 → MSG_RETRY。回退后的新局面若仍是个询问，
     * 会由新到达的 SELECT 重新开启自己的会话。
     */
    void abortSelectSessions() {
        if (isPlaceSelecting) finishPlaceSelect();
        // 指示物取除会话同样只清状态不发应答：候选卡的 opParam 属于已被撤销的那次询问，
        // 任其存活则下一次点卡会把旧询问的取除个数应答发进回退后的新局面
        if (isCounterSelecting) finishCounterSelect();
        if (isCardSelecting || isUnselectSelecting || isSumSelecting) endCardSelect();
    }

    // === 区域点击处理（来自 DuelFieldManager） ===

    void onZoneClick(int player, int location, int sequence, float tapX, float tapY) {
        if (ctl.engine == null) return;
        GameField field = ctl.engine.getField();
        if (isPlaceSelecting) {
            handlePlaceSelection(player, location, sequence);
            return;
        }
        // 指示物取除询问期间点在场格（非卡体命中）：同 C++ 需 clicked_card，此处按格定位后取除，
        // 不得落到下方的命令菜单/卡详分支打断连点取除
        if (isCounterSelecting) {
            handleCounterClick(player, location, sequence);
            return;
        }
        ClientCard card = field.getCard(player, location, sequence);
        if (card != null && card.cmdFlag != 0) {
            ctl.showCardCommandMenu(card, tapX, tapY);
            return;
        }
        // 无可执行命令：关闭残留的命令菜单，只显示卡片信息
        ctl.dismissCmdMenu();
        // 堆叠区点击：查看卡片信息
        boolean isPile = (location == 0x01 || location == 0x10
                || location == 0x20 || location == 0x40);
        // 回放态（纯消息驱动）：无选择通讯故不下发 cmdFlag，双方卡组/额外/墓地/除外
        // 恒为公开信息（卡码由 MSG_START 回填 + MOVE 携带），点堆叠区直接弹整列表正面查看
        if (ctl.engine.replayMode && isPile && showReplayPileView(field, player, location)) {
            return;
        }
        // 卡码门槛统一用 displayCode：场上里侧卡一经公开（POS_REVEAL 解遮蔽 / 泡影类
        // POS_CHANGE 携码）后即便被遮蔽刷新洗回 code，仍按 chain_code 备份弹详情
        if (isPile && card != null && card.displayCode() > 0) {
            ctl.activity.showCardInfoPanel(card);
            return;
        }
        // 场上卡片点击：查看信息
        if (card != null && card.displayCode() > 0) {
            ctl.activity.showCardInfoPanel(card);
        }
    }

    /** 回放态堆叠区查看：取该区首张已知卡（实况卡码或前卡码 chain_code）作视角侧样本
     *  （controler/location 与 players[] 同索引），交由列表弹窗展开全部卡片；
     *  卡组/额外整区无已知卡码时仍问录像头部权威卡码（ReplayPlayer.getReplayZoneCodes）：
     *  头部有数据即弹窗展开全部，不再回落「无卡可看」（修复回填时序竞争导致的时灵时不灵） */
    protected boolean showReplayPileView(GameField field, int player, int location) {
        List<ClientCard> list = field.players[player].getLocationList(location);
        if (list == null || list.isEmpty()) return false;
        ClientCard sample = null;
        for (ClientCard c : list) {
            if (c == null) continue;
            if (sample == null) sample = c;
            if (c.code > 0 || c.chain_code > 0) {
                sample = c;
                break;
            }
        }
        if (sample == null) return false;
        if (sample.code <= 0 && sample.chain_code <= 0) {
            List<Integer> auth = ctl.engine.getReplayZoneCodes(player, location);
            if (auth == null || auth.isEmpty()) return false;
        }
        ctl.showReplayPileView(sample);
        return true;
    }

    protected void handlePlaceSelection(int player, int location, int sequence) {
        int bitPos = getZoneBitPos(player, location, sequence);
        if (bitPos < 0 || (ctl.engine.selectFieldMask & (1 << bitPos)) == 0) {
            return;
        }
        // 点击已选区域 → 取消该位（对齐 gframe：selected_field 位取反，select_min 回升）
        if ((selectedFieldBits & (1 << bitPos)) != 0) {
            selectedFieldBits &= ~(1 << bitPos);
            placeSelectRemain++;
            return;
        }
        selectedFieldBits |= (1 << bitPos);
        placeSelectRemain--;
        if (placeSelectRemain > 0) {
            return;
        }
        // 数量满足：按 gframe 固定次序组装多条应答并发送
        byte[] resp = buildPlaceResponse(selectedFieldBits);
        finishPlaceSelect();
        ctl.engine.sendResponse(resp);
    }

    /** 结束放置区域选择会话：清状态、去高亮、隐藏 cancelOrFinish（联动恢复洗切手卡） */
    protected void finishPlaceSelect() {
        isPlaceSelecting = false;
        selectedFieldBits = 0;
        placeSelectRemain = 0;
        ctl.viewController.clearHighlight();
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) panel.hideCancelOrFinishButton();
    }

    /**
     * 组装放置/禁用区域应答：对齐 gframe event_handler.cpp L1411-1448 的固定次序——
     * 我方 MZone(位0-6) → 我方 SZone(位8-15) → 对方 MZone(位16-22) → 对方 SZone(位24-31)，
     * 每个置位区域追加 3 字节 {协议玩家索引, LOCATION, sequence}。
     */
    protected byte[] buildPlaceResponse(int bits) {
        int myP = ctl.engine.localPlayer(0);
        int oppP = ctl.engine.localPlayer(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 7; i++) if ((bits & (1 << i)) != 0) { out.write(myP); out.write(0x04); out.write(i); }
        for (int i = 0; i < 8; i++) if ((bits & (1 << (8 + i))) != 0) { out.write(myP); out.write(0x08); out.write(i); }
        for (int i = 0; i < 7; i++) if ((bits & (1 << (16 + i))) != 0) { out.write(oppP); out.write(0x04); out.write(i); }
        for (int i = 0; i < 8; i++) if ((bits & (1 << (24 + i))) != 0) { out.write(oppP); out.write(0x08); out.write(i); }
        return out.toByteArray();
    }

    protected int getZoneBitPos(int player, int location, int sequence) {
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

    // === 场上/手牌直接选择（对齐 gframe drawing.cpp DrawCard 卡片选择轮廓，不弹 CardSelectDialog）===

    /**
     * 进入场上选择模式：候选卡标记 is_selectable（GameFieldView 绘制黄色虚线行进轮廓），
     * 玩家直接点击场上/手牌卡片切换选中；达到 max 自动应答，或点「完成选择」按钮应答
     */
    void beginCardSelect(List<CardSelectDialog.CardItem> items, int min, int max, boolean cancelable) {
        if (ctl.engine == null || items == null) return;
        GameField field = ctl.engine.getField();
        field.clearSelect();
        field.selectableCards.clear();
        field.selectedCards.clear();
        field.selectsumAll.clear();
        field.selectsumCards.clear();
        cardSelectClickOrder.clear();
        isSumSelecting = false;
        isUnselectSelecting = false;
        cardSelectMin = min;
        cardSelectMax = max;
        cardSelectCancelable = cancelable;
        // 本会话取代可能仍在生效的指示物取除会话（上方 clearSelect 已清两边候选标记）
        isCounterSelecting = false;
        for (CardSelectDialog.CardItem it : items) {
            // 消息里的 ctrl 是协议索引，getCard 需本地索引（localPlayer 为对合映射）
            int lp = ctl.engine.localPlayer(it.controler);
            ClientCard card = field.getCard(lp, it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.is_selectable = true;
            card.is_selected = false;
            card.select_seq = it.selectSeq;
            field.selectableCards.add(card);
        }
        isCardSelecting = true;
        if (ctl.viewController != null) ctl.viewController.invalidate();
    }

    /**
     * 进入 UNSELECT 场上直接选择会话（MSG_SELECT_UNSELECT_CARD，连接召唤手续逐步选素材）：
     * 对齐 duelclient.cpp L2003-2051——count1 可选卡 is_selectable/未选中（行进虚线），
     * count2 已确认素材 is_selectable 且 is_selected（实线框，不入 selected_cards，同 gframe
     * selected_cards 仅存玩家点选卡片的语义）；点击任意可选卡立即应答（event_handler.cpp
     * L1520-1535），完成/取消按钮应答 -1（gframe L2067-2077 select_cancelable=finishable||cancelable）
     */
    void beginUnselectCardSelect(List<CardSelectDialog.CardItem> items, int selectableCount,
                                 int min, int max, boolean finishable, boolean cancelable) {
        if (ctl.engine == null || items == null || items.isEmpty()) return;
        GameField field = ctl.engine.getField();
        field.clearSelect();
        field.selectableCards.clear();
        field.selectedCards.clear();
        field.selectsumAll.clear();
        field.selectsumCards.clear();
        cardSelectClickOrder.clear();
        isSumSelecting = false;
        isUnselectSelecting = true;
        isCardSelecting = true;
        isCounterSelecting = false;
        cardSelectMin = min;
        cardSelectMax = max;
        // gframe L1989：select_cancelable = finishable || cancelable
        cardSelectCancelable = finishable || cancelable;
        for (int i = 0; i < items.size(); i++) {
            CardSelectDialog.CardItem it = items.get(i);
            ClientCard card = field.getCard(ctl.engine.localPlayer(it.controler),
                    it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.is_selectable = true;
            // count2（i≥selectableCount）预选中 → 实线框（gframe L2043）
            card.is_selected = i >= selectableCount;
            card.select_seq = it.selectSeq;
            field.selectableCards.add(card);
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            // gframe L2067-2077：finishable → 「完成」，否则 cancelable → 「取消」，皆无 → 隐藏
            panel.updateCancelOrFinishButton(finishable, cancelable, false);
        }
    }

    // === 场上指示物取除会话（MSG_SELECT_COUNTER，不弹窗：对齐 gframe 场上直接点卡取除）===

    /**
     * 开启指示物取除会话。候选卡与其 {@code opParam} 已由引擎侧
     * {@code MessageUIHandler.applyCounterCandidates} 按 duelclient.cpp L2345-2364 登记完成
     *（同 C++：ClientAnalyze 在消息解析时即标好 is_selectable/opParam，仅开一个本地点卡会话）。
     * 无候选/无需取除时不开会话——与 C++ 同样无卡可点，但本端不能因此挂住应答闸门。
     */
    void beginCounterSelect() {
        GameEngine engine = ctl.engine;
        if (engine == null) return;
        GameField field = engine.getField();
        if (field.selectableCards.isEmpty() || field.selectCounterCount <= 0) return;
        isCounterSelecting = true;
        // C++ 本询问不显示 CancelOrFinish（duelclient.cpp L2366-2369 只置 stHintMsg），强制收起
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) panel.hideCancelOrFinishButton();
        if (ctl.viewController != null) ctl.viewController.invalidate();
    }

    /**
     * 场上点卡取除一个指示物（event_handler.cpp L1536-1556）：候选卡 opParam 低 16 位递减，
     * 减到 0 则该卡不再可选；待取除总数归零时按候选顺序把每张卡已取除的个数（初始高 16 位
     * 减当前低 16 位）以 uint16 数组一次性应答，否则刷新提示栏剩余个数。
     */
    void handleCounterClick(int player, int location, int sequence) {
        GameEngine engine = ctl.engine;
        if (engine == null) return;
        GameField field = engine.getField();
        ClientCard card = field.getCard(player, location, sequence);
        // C++ L1537：!clicked_card || !clicked_card->is_selectable → 静默忽略本次点击
        if (card == null || !card.is_selectable) return;
        card.opParam--;
        if ((card.opParam & 0xffff) == 0) card.is_selectable = false;
        field.selectCounterCount--;
        if (field.selectCounterCount > 0) {
            // L1552-1553：仍按 GetSysString(204) 刷新尚需取除的个数
            ctl.showDuelHint(engine.hintManager.counterHint(field.selectCounterCount,
                    field.selectCounterType));
            if (ctl.viewController != null) ctl.viewController.invalidate();
            return;
        }
        // L1544-1550：逐候选卡编码「已取除个数」为 uint16 数组（长度=候选数×2）并应答
        int n = field.selectableCards.size();
        ByteBuffer buf = ByteBuffer.allocate(n * 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            ClientCard c = field.selectableCards.get(i);
            buf.putShort((short) ((c.opParam >>> 16) - (c.opParam & 0xffff)));
        }
        ctl.hideDuelHint();          // L1547：stHintMsg->setVisible(false)
        finishCounterSelect();       // L1548：ClearSelect()
        engine.sendResponse(buf.array());
    }

    /** 结束指示物取除会话：清候选可选态（先清标记再清列表）、归零计数并收起提示/按钮 */
    void finishCounterSelect() {
        isCounterSelecting = false;
        if (ctl.engine != null) {
            GameField field = ctl.engine.getField();
            field.clearSelect();
            field.selectableCards.clear();
            field.selectCounterCount = 0;
        }
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) panel.hideCancelOrFinishButton();
        if (ctl.viewController != null) ctl.viewController.invalidate();
    }

    void handleCardSelection(int player, int location, int sequence) {
        if (ctl.engine == null) return;
        GameField field = ctl.engine.getField();
        ClientCard card = field.getCard(player, location, sequence);
        // 合计选择（SUM）模式：交给专用点击-重校验循环（event_handler.cpp L1557-1566）
        if (isSumSelecting) {
            handleSumSelectionClick(card);
            return;
        }
        // UNSELECT 场上模式（连接手续逐步选素材，event_handler.cpp L1520-1535）：
        // 点击任意 is_selectable 卡 → 立即应答 [1, select_seq]，无效点击静默忽略（同 C++ break）
        if (isUnselectSelecting) {
            if (card != null && card.is_selectable) {
                ByteBuffer buf = ByteBuffer.allocate(2);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.put((byte) 1);
                buf.put((byte) card.select_seq);
                ctl.engine.sendResponse(buf.array());
                endCardSelect();
            }
            return;
        }
        if (card == null || !card.is_selectable) {
            return;
        }
        if (card.is_selected) {
            card.is_selected = false;
            field.selectedCards.remove(card);
            cardSelectClickOrder.remove(Integer.valueOf(card.select_seq));
        } else {
            if (cardSelectMax > 0 && cardSelectClickOrder.size() >= cardSelectMax) {
                return;
            }
            card.is_selected = true;
            field.selectedCards.add(card);
            cardSelectClickOrder.add(card.select_seq);
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
        // 达到要求数量自动应答通讯
        if (cardSelectMax > 0 && cardSelectClickOrder.size() >= cardSelectMax) {
            confirmCardSelect();
            return;
        }
        // 对齐 event_handler.cpp L1475-1479：已达 min 且候选全部选中 → 无需再等，自动应答
        if (cardSelectClickOrder.size() >= cardSelectMin
                && cardSelectClickOrder.size() >= field.selectableCards.size()) {
            confirmCardSelect();
            return;
        }
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            panel.updateCancelOrFinishButton(cardSelectClickOrder.size() >= cardSelectMin,
                    cardSelectCancelable, !cardSelectClickOrder.isEmpty());
        }
    }

    /** 「完成选择」按钮：达到 min 即应答，否则可取消时应答 -1 */
    boolean finishCardSelect() {
        if (!isCardSelecting) return false;
        // UNSELECT 场上会话：无部分确认（单卡点击即提交），完成/取消统一应答 -1
        //（event_handler.cpp L968-971：UNSELECT 的 CancelOrFinish = 发送 -1）
        if (isUnselectSelecting) {
            return cancelCardSelect();
        }
        // SUM 模式：仅在 selectReady 时应答（event_handler.cpp L3156-3158 CancelOrFinish），SUM 不可取消
        if (isSumSelecting) {
            if (ctl.engine.getField().selectReady) {
                sendSumSelectResponse();
                return true;
            }
            return false;
        }
        if (cardSelectClickOrder.size() >= cardSelectMin) {
            return confirmCardSelect();
        }
        return cancelCardSelect();
    }

    protected boolean confirmCardSelect() {
        if (!isCardSelecting || ctl.engine == null) return false;
        if (cardSelectClickOrder.size() < cardSelectMin) {
            return false;
        }
        // C++ SetResponseSelectedCards：respbuf[0]=len，其后按点击顺序填 select_seq
        ByteBuffer buf = ByteBuffer.allocate(1 + cardSelectClickOrder.size());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) cardSelectClickOrder.size());
        for (int seq : cardSelectClickOrder) {
            buf.put((byte) seq);
        }
        ctl.engine.sendResponse(buf.array());
        endCardSelect();
        // 立即停止蚂蚁线动画（先于网络应答）
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            panel.stopAntsHighlightImmediately();
        }
        return true;
    }

    protected boolean cancelCardSelect() {
        if (!isCardSelecting || isSumSelecting) return false;
        if (cardSelectCancelable && cardSelectClickOrder.isEmpty()) {
            ctl.activity.sendResponseInt(-1);
            endCardSelect();
            return true;
        }
        return false;
    }
}

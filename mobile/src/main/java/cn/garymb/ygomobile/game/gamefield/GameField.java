package cn.garymb.ygomobile.game.gamefield;
import cn.garymb.ygomobile.game.replay.ReplayPosSnap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.enums.CardLocation;
import ocgcore.enums.CardPosition;

public class GameField {
    public static final int MAX_MONSTER_ZONE = 7;
    public static final int MAX_SPELL_ZONE = 8;
    public static final int MAX_FIELD_SIZE = 8;
    public static final int MAX_HAND = 20;
    public static final int MAX_GRAVE = 128;
    public static final int MAX_REMOVED = 128;
    public static final int MAX_EXTRA = 32;
    public static final int MAX_DECK = 128;
    public static final int MAX_LAYER_COUNT = 6;

    public static final int QUERY_CODE = 0x01;
    public static final int QUERY_POSITION = 0x02;
    public static final int QUERY_ALIAS = 0x04;
    public static final int QUERY_TYPE = 0x08;
    public static final int QUERY_LEVEL = 0x10;
    public static final int QUERY_RANK = 0x20;
    public static final int QUERY_ATTRIBUTE = 0x40;
    public static final int QUERY_RACE = 0x80;
    public static final int QUERY_ATTACK = 0x100;
    public static final int QUERY_DEFENSE = 0x200;
    public static final int QUERY_BASE_ATTACK = 0x400;
    public static final int QUERY_BASE_DEFENSE = 0x800;
    public static final int QUERY_REASON = 0x1000;
    public static final int QUERY_REASON_CARD = 0x2000;
    public static final int QUERY_EQUIP_CARD = 0x4000;
    public static final int QUERY_TARGET_CARD = 0x8000;
    public static final int QUERY_OVERLAY_CARD = 0x10000;
    public static final int QUERY_COUNTERS = 0x20000;
    public static final int QUERY_OWNER = 0x40000;
    public static final int QUERY_STATUS = 0x80000;
    public static final int QUERY_IS_PUBLIC = 0x100000;
    public static final int QUERY_LSCALE = 0x200000;
    public static final int QUERY_RSCALE = 0x400000;
    public static final int QUERY_LINK = 0x800000;

    // client_field.cpp / materials.cpp 常量
    public static final float PI = 3.1415926f;
    public static final int POS_FACEUP = 0x5;
    public static final int POS_FACEDOWN = 0xA;
    public static final int POS_ATTACK = 0x3;
    public static final int POS_DEFENSE = 0xC;

    /** client_field.h STATUS_PROC_COMPLETE：以正规程序特殊召唤过的标记 */
    public static final int STATUS_PROC_COMPLETE = 0x0008;
    /** ocgcore common.h STATUS_DISABLED：效果被无效（无限泡影类） */
    public static final int STATUS_DISABLED = 0x0001;
    /** ocgcore common.h STATUS_FORBIDDEN：表示形式/行动被禁止（技能抽取类） */
    public static final int STATUS_FORBIDDEN = 0x04000000;
    /** duelclient.cpp MSG_CARD_HINT L4104 CHINT_DESC_ADD：为卡片追加一条效果文字提示 */
    public static final int CHINT_DESC_ADD = 6;
    /** duelclient.cpp MSG_CARD_HINT L4106 CHINT_DESC_REMOVE：移除一条提示，计数归零才真正删除 */
    public static final int CHINT_DESC_REMOVE = 7;
    /** ocgcore common.h L379 PHINT_DESC_ADD：MSG_PLAYER_HINT 追加一条玩家级描述提示 */
    public static final int PHINT_DESC_ADD = 6;
    /** ocgcore common.h L380 PHINT_DESC_REMOVE：MSG_PLAYER_HINT 撤回一条玩家级描述提示 */
    public static final int PHINT_DESC_REMOVE = 7;
    /** gframe client_field.h L195 CARD_QUESTION（谜题）：作为 MSG_PLAYER_HINT 的 value 下发时，
     *  本端进入/解除「不可确认墓地」禁止态（对齐 duelclient.cpp MSG_PLAYER_HINT L4139-4150） */
    public static final int CARD_QUESTION = 38723936;

    public static class PlayerField {
        public int lp;
        public final List<ClientCard> deck = new ArrayList<>();
        public final List<ClientCard> hand = new ArrayList<>();
        public final List<ClientCard> monsterZone = new ArrayList<>();
        public final List<ClientCard> spellZone = new ArrayList<>();
        public final List<ClientCard> grave = new ArrayList<>();
        public final List<ClientCard> removed = new ArrayList<>();
        public final List<ClientCard> extra = new ArrayList<>();
        public ClientCard fieldSpell;

        public PlayerField() {
            for (int i = 0; i < MAX_DECK; i++) deck.add(null);
            for (int i = 0; i < MAX_HAND; i++) hand.add(null);
            for (int i = 0; i < MAX_MONSTER_ZONE; i++) monsterZone.add(null);
            for (int i = 0; i < MAX_SPELL_ZONE; i++) spellZone.add(null);
            for (int i = 0; i < MAX_GRAVE; i++) grave.add(null);
            for (int i = 0; i < MAX_REMOVED; i++) removed.add(null);
            for (int i = 0; i < MAX_EXTRA; i++) extra.add(null);
        }

        public void clear() {
            lp = 8000;
            for (int i = 0; i < deck.size(); i++) deck.set(i, null);
            for (int i = 0; i < hand.size(); i++) hand.set(i, null);
            for (int i = 0; i < monsterZone.size(); i++) monsterZone.set(i, null);
            for (int i = 0; i < spellZone.size(); i++) spellZone.set(i, null);
            for (int i = 0; i < grave.size(); i++) grave.set(i, null);
            for (int i = 0; i < removed.size(); i++) removed.set(i, null);
            for (int i = 0; i < extra.size(); i++) extra.set(i, null);
            fieldSpell = null;
        }

        public List<ClientCard> getLocationList(int loc) {
            if (loc == CardLocation.Deck.value()) return deck;
            if (loc == CardLocation.Hand.value()) return hand;
            if (loc == CardLocation.MonsterZone.value()) return monsterZone;
            if (loc == CardLocation.SpellZone.value()) return spellZone;
            if (loc == CardLocation.Grave.value()) return grave;
            if (loc == CardLocation.Removed.value()) return removed;
            if (loc == CardLocation.Extra.value()) return extra;
            return null;
        }
    }

    public static class ChainInfo {
        public ClientCard chainCard;
        public int code;
        public int desc;
        public int controler;
        public int location;
        public int sequence;
        public boolean solved;
        public boolean needDistinguish;
        public List<ClientCard> targets = new ArrayList<>();
        /** 连锁图标（chain 旋转图 / number 序号）位置快照：MSG_CHAINED 时捕捉发动卡 xyz，卡因结算离开原位时图标不跟随，停在原地直到连锁消失。 */
        public boolean iconPosCaptured;
        public float iconX, iconY, iconZ;
    }

    /** 待挂超量素材——素材 MSG_MOVE 先于超量怪兽 MSG_MOVE 到达时暂存，待怪兽入格再补挂 */
    static final class PendingOverlay {
        final ClientCard card;
        final int ctrl;
        final int loc;
        final int seq;
        PendingOverlay(ClientCard card, int ctrl, int loc, int seq) {
            this.card = card;
            this.ctrl = ctrl;
            this.loc = loc;
            this.seq = seq;
        }
    }

    public final PlayerField[] players = new PlayerField[2];
    public int currentPlayer;
    public int currentPhase;
    public int turnCount;
    public boolean isTag;
    /** 回放快进重排期间的即时落位模式：移动/淡入淡出/LP/洗牌动画全部直接到位，不产生 aniFrame——动画闸门天然不阻塞，快进队列可单帧排空（GameEngine.drainReplayQueueNow）；投喂线程置位/复位、主线程派发链读取，volatile 防 JIT 提升旧值使落位门控失效 */
    public volatile boolean instantPlace = false;
    /** 回放态遮蔽保护（用户规格：不引擎重跑也要对方手卡正面）：合并的客户端视角录像里，对方手卡的 UPDATE_DATA/UPDATE_CARD 块被服务器中继遮蔽（整块清零或 code=0），而同一张卡的真实卡码已由流内 MSG_DRAW/MSG_MOVE 未遮蔽中继写入卡对象；若再让零块覆盖会把对方手卡永远压成背面。置位时手卡区已有卡码的卡忽略零块（ClientCard 为独立类，故本开关也为 static；仅随回放会话置位/复位，实况遮蔽语义不受影响） */
    public static boolean preserveMaskedHandCode = false;
    public List<ChainInfo> chains = new ArrayList<>();

    /** 正在构建中的连锁（对齐 dField.current_chain）：MSG_CHAINING 重建、BECOME_TARGET 追加 target、CHAINED 压入 chains；供生成「在连锁%d发动」等标签。 */
    public ChainInfo currentChain = new ChainInfo();
    public List<ClientCard> overlayCards = new ArrayList<>();
    /** 淡出中的已离场卡片（对齐 MSG_MOVE cl==0 FadeCard 后 RemoveCard）：结构性移除同步完成、卡片暂存本列表继续渲染，动画播完由 updateCardAnimation purge，避免消失卡瞬间掉帧。 */
    public final List<ClientCard> fadingCards = new ArrayList<>();
    /** C++ duelclient.cpp L2958 appear = quick_animation ? 12 : 20（本工程无快动画开关，取 20）：MSG_MOVE pl==0 / cl==0 出现与消失卡片的淡入淡出帧数 */
    public static final int APPEAR_FRAME = 20;
    final List<PendingOverlay> pendingOverlays = new ArrayList<>();
    public int[] extraPCount = new int[2];
    public long disabledField;
    /** HINT_ZONE 通讯（duelclient.cpp L1529-1572）展开出的被标注魔陷格集，位布局与 disabledField 同构（本地视角：seat0 MZ bits0-6 / SZ bits8-15，seat1 高 16 位）；仅接受无效类效果的 SZ 位来位——无限泡影类脚本效果处理成功时 Duel.Hint(HINT_ZONE, tp, 0x1<<(seq+8)) 宣告自身所在列的 SZONE 位，本端把它标到同列两张魔陷格——同侧 SZONE 格（同 seq）与对侧镜像 SZONE 格（4-seq，aux.GetColumn 同式）；仅主列 seq 0-4（额外怪兽列无魔陷对应格）。MZ 位（bit0-6/16-22）为封锁格子类卡片（封锁龙/黄金国亡灵 等，效果伴随 MSG_FIELD_DISABLED 白 X）的怪兽纵列宣告，桌面端只对掩码位瞬时高亮 40 帧、从不扩张魔陷格；本端若同样展开会出现「白 X 同纵列魔陷格错误显示泡影标注」的误报，故不产生持久标注。效果携 RESET_PHASE+PHASE_END，故下一个 MSG_NEW_TURN 清除。 */
    public int hintZoneMask;
    /** MSG_SELECT_EFFECTYN 询问中的卡（用户规格：诱发效果被询问是否发动时，该卡矩形角上
     *  显示与可发动同款的绿色呼吸点）：由询问窗展示时置入、dismiss/新询问到达时清除。
     *  不写 cmdFlag——COMMAND_ACTIVATE 置位会让点卡路由（GameFieldController）误弹命令菜单，
     *  而询问期间点该卡应直接出卡详发光；渲染侧（CardOverlayRenderer）按本引用强制绘绿点。
     *  GL 线程读、UI 线程写，volatile 保证可见性 */
    public volatile ClientCard effectYnAsking;
    public boolean deckReversed;
    /** 本次灵摆召唤预演已闪动的两侧刻度卡：同批逐张放置询问（ocgcore special_summon
     *  每张怪兽各发一条 MSG_SELECT_PLACE）只在首次闪动，刻度对象未换期间不重复；
     *  整组特殊召唤结束（MSG_SPSUMMONED）后清空，下次灵摆召唤重新闪动（仅主线程读写） */
    public ClientCard pendulumPreludeL;
    public ClientCard pendulumPreludeR;
    /** 灵摆召唤标记：MSG_SELECT_UNSELECT_CARD 含 LOCATION_HAND 候选且两侧灵摆区有卡时置 true，
     *  MSG_SELECT_PLACE 据此判断是否播放灵摆闪动预演；MSG_SPSUMMONED / IDLECMD / BATTLECMD / CHAIN 时清除 */
    public boolean pendulumSelectPending;
    public boolean cantCheckGrave;
    public boolean tagSurrender;
    public boolean tagTeammateSurrender;

    public List<ClientCard> activatableCards = new ArrayList<>();
    public List<ClientCard> summonableCards = new ArrayList<>();
    public List<ClientCard> spsummonableCards = new ArrayList<>();
    public List<ClientCard> msetableCards = new ArrayList<>();
    public List<ClientCard> ssetableCards = new ArrayList<>();
    public List<ClientCard> reposableCards = new ArrayList<>();
    public List<ClientCard> attackableCards = new ArrayList<>();
    public List<ClientCard> contiCards = new ArrayList<>();
    public boolean[] deckAct = new boolean[2];
    public boolean[] graveAct = new boolean[2];
    public boolean[] removeAct = new boolean[2];
    public boolean[] extraAct = new boolean[2];
    public boolean[] pzoneAct = new boolean[2];
    public boolean contiAct;

    /** 攻击宣言绿色弧形流动动画状态（对齐 MSG_ATTACK L3817-3866 + GenArrow/drawing attack_sv）：onAttack 写入攻击者/目标卡与时间戳，drawAttackArc 在约 0.9s 内绘制；arcTarget==null 表示直接攻击。 */
    public volatile ClientCard arcAttacker;
    public volatile ClientCard arcTarget;
    public volatile long arcStartMs;

    /** 战斗阶段点击「攻击宣言」选中的怪兽：cmdFlag 已清空但尚未进入 arcAttacker 前需隐藏其 attack 箭头；该卡通讯中还有剩余攻击次数时会重新显示（见 CardOverlayRenderer.drawFieldCardOverlays）。 */
    public volatile ClientCard selectAttacking;

    public List<ClientCard> selectableCards = new ArrayList<>();
    public List<ClientCard> selectedCards = new ArrayList<>();
    /** 正在「揭示」展示的手牌卡——由 applyMoveToHandReveal 加入，SelectionOutlineRenderer 对其绘行进蚂蚁线（不受 is_selectable 门控）；展示结束/洗切时清空，亦复用为灵摆召唤两张刻度卡。 */
    public final List<ClientCard> revealHighlightCards = new ArrayList<>();
    public List<ClientCard> selectsumCards = new ArrayList<>();
    public List<ClientCard> selectsumAll = new ArrayList<>();
    public List<ClientCard> displayCards = new ArrayList<>();
    public int[] sortList;
    public int selectMin, selectMax, mustSelectCount;
    public int selectSumval, selectMode;
    public int selectHint;
    /** 对齐 duelclient.cpp L40 event_string：由 HINT_EVENT 及召唤/抽卡/伤害/攻击/连锁等事件消息写入，在 MSG_SELECT_CHAIN / MSG_SELECT_EFFECTYN 询问前作为「触发时点」前缀拼接（L2176/2178） */
    public String eventString = "";
    public boolean selectCancelable;
    public boolean selectReady;
    public int selectCurvalL, selectCurvalH;

    /** DuelInfo 等价物（game.h dInfo 中 HUD 相关字段） */
    public static class DuelInfo {
        public int startLp = 8000;
        public int duelRule;                       // 通讯下发的 master rule（1-5），场地贴图按 >=4 分流
        public int[] lp = {8000, 8000};          // 显示值（LP 动画的中间值）
        public int timeLimit;// 秒，0=无限时
        public int[] timeLeft = new int[2];
        public int timePlayer = -1;
        public int[] timeColor = {YGOUtil.c(R.color.white), YGOUtil.c(R.color.white)};
        public int[] cardCount = new int[2];
        public int[] cardCountColor = {YGOUtil.c(R.color.white), YGOUtil.c(R.color.white)};
        public int[] totalAttack = new int[2];
        public int[] totalAttackColor = {YGOUtil.c(R.color.white), YGOUtil.c(R.color.white)};
    }

    public final DuelInfo dInfo = new DuelInfo();

    /** dInfo.duelRule 的静态镜像：几何门面入口（getZoneRect/getPileRect）为 static，需按 master rule 选 materials.cpp 的 rule0(field2)/rule1(field3) 格子布局；经 {@link #setDuelRule(int)} 统一写入（GameMessageParser/ReloadFieldBuilder/ReplayPlayer），默认 4（当前环境恒 New Master Rule） */
    public static volatile int sDuelRule = 4;

    /** 设置本局 master rule：同时写实例 dInfo 与静态镜像，几何分支单一入口 */
    public void setDuelRule(int rule) {
        dInfo.duelRule = rule;
        sDuelRule = rule;
    }

    /** 同包协作类：坐标几何 / 卡片增删改 / 动画与 HUD。构造注入门面引用，经包级私有直连共享状态。 */
    final GameFieldGeometry geometry;
    final GameFieldCards cards;
    final GameFieldMotion motion;

    // === Game::lpframe/lpplayer/lpd/lpccolor/lpcstring LP 动画状态机（推进逻辑在 GameFieldMotion）===
    public int lpframe;
    public int lpplayer;
    public int lpd;
    public int lpccolor;
    public String lpcstring = "";
    int lpFinal;
    int lpDelay;        // 等价 WaitFrameSignal(30)：浮字先全亮展示 30 帧
    boolean lpPending;
    long lastTimeTickMs;

    // === 区域规格 / 场地底板 / 格子与堆叠尺寸真值（materials.cpp）；落点计算在 GameFieldGeometry ===
    /** 区域规格：怪兽/魔陷区宽 290px、高 254px，格子间 2px 间隔 → 横向间距 292px；X 坐标以场地中心 3.95 为轴按 (292px世界长)/1.1 放大，决斗场更宽且与 field3.png 拉伸适配 */
    static final float X_SCALE = (292f * 0.8f / 177f) / 1.1f;

    /** C++ materials.cpp vField：底板贴图 uv(0,0)-(1,1) 覆盖的世界矩形；底板、格子、卡片落点共用 fx()/恒等 Y 这同一仿射映射，故贴图网格与格子必然完美重叠 */
    public static final float FIELD_TEX_X_MIN = -1f;
    public static final float FIELD_TEX_X_MAX = 9f;
    public static final float FIELD_TEX_Y_MIN = -4f;
    public static final float FIELD_TEX_Y_MAX = 4f;
    /** C++ materials.cpp vFieldSpell：场地魔法背景图矩形（z=-0.01，绘于底板之下） */
    public static final float FIELD_SPELL_X_MIN = 1.2f;
    public static final float FIELD_SPELL_X_MAX = 6.7f;
    public static final float FIELD_SPELL_Y_MIN = -3.2f;
    public static final float FIELD_SPELL_Y_MAX = 3.2f;

    /** 格子尺寸：materials.cpp 怪兽/魔陷格 1.1×1.2、堆叠区 0.8×1.2（X 经 fx 缩放） */
    public static final float ZONE_W = 1.1f * X_SCALE;
    public static final float ZONE_H = 1.2f;
    public static final float PILE_W = 0.8f * X_SCALE;
    public static final float PILE_H = 1.2f;

    // 动画速度倍率：1 为原速，2 即 2 倍速
    public float animationSpeed = 1f;

    public GameField() {
        players[0] = new PlayerField();
        players[1] = new PlayerField();
        geometry = new GameFieldGeometry(this);
        cards = new GameFieldCards(this);
        motion = new GameFieldMotion(this);
    }

    public void clear() {
        for (int i = 0; i < 2; i++) {
            players[i].clear();
            deckAct[i] = false;
            graveAct[i] = false;
            removeAct[i] = false;
            extraAct[i] = false;
            pzoneAct[i] = false;
        }
        overlayCards.clear();
        fadingCards.clear();
        pendingOverlays.clear();
        chains.clear();
        currentChain = new ChainInfo();
        activatableCards.clear();
        summonableCards.clear();
        spsummonableCards.clear();
        msetableCards.clear();
        ssetableCards.clear();
        reposableCards.clear();
        attackableCards.clear();
        contiCards.clear();
        revealHighlightCards.clear();
        // 断线/结束/新开一局时选择态列表必须随场一并清空，旧实现漏清致残留 is_selectable 蚂蚁线（SelectionOutlineRenderer 仍绘线）
        selectableCards.clear();
        selectedCards.clear();
        selectsumCards.clear();
        selectsumAll.clear();
        displayCards.clear();
        contiAct = false;
        disabledField = 0;
        hintZoneMask = 0;
        effectYnAsking = null;
        deckReversed = false;
        pendulumPreludeL = null;
        pendulumPreludeR = null;
        pendulumSelectPending = false;
        cantCheckGrave = false;
        tagSurrender = false;
        tagTeammateSurrender = false;
        currentPlayer = 0;
        currentPhase = 0;
        turnCount = 0;
        extraPCount[0] = 0;
        extraPCount[1] = 0;
        eventString = "";
        // LP 增减动画状态机必须随场一并复位，旧实现漏清致残留到下一局、局间重显决斗场时重播上一局最后一次伤害的浮字/扣血动画（复位后 lpcstring 为空，浮字自动隐藏）
        lpPending = false;
        lpDelay = 0;
        lpframe = 0;
        lpplayer = 0;
        lpd = 0;
        lpFinal = 0;
        lpccolor = 0;
        lpcstring = "";
    }

    /** 接收 HINT_ZONE 掩码（本地视角）展开为魔陷格标注集存 hintZoneMask：仅 SZONE 位标注同纵列两格（同侧 seq 与对侧镜像 4-seq），MZ 位为封区类宣告不做持久标注，来位已被 disabledField 封锁时跳过，仅主列 seq 0-4 */
    public void applyHintZone(int zones) {
        for (int bit = 0; bit < 32; bit++) {
            if ((zones & (1 << bit)) == 0) continue;
            if ((bit & 8) == 0) continue;                       // MZ 位：封区宣告，跳过
            if ((disabledField & (1L << bit)) != 0) continue;   // 该格已带白 X：不叠加标注
            markSpellColumn(bit >= 16 ? 1 : 0, (bit & 7));
        }
    }

    private void markSpellColumn(int seat, int seq) {
        if (seq < 0 || seq > 4) return;
        hintZoneMask |= 1 << (seat * 16 + 8 + seq);
        hintZoneMask |= 1 << ((1 - seat) * 16 + 8 + (4 - seq));
    }

    /** 该本地座位的场上格子是否被 HINT_ZONE 纵列标注（location 为已去叠加位的 0x04/0x08） */
    public boolean isZoneColumnMarked(int seat, int location, int sequence) {
        if (seat < 0 || seat > 1) return false;
        boolean mz = location == CardLocation.MonsterZone.value();
        if (!mz && location != CardLocation.SpellZone.value()) return false;
        return (hintZoneMask & (1 << (seat * 16 + (mz ? sequence : 8 + sequence)))) != 0;
    }

    /** 决斗结束（MSG_WIN/STOC_DUEL_END）与断开连接时清除全部选择态蚂蚁线显示：不清场仅复位选择标记并清空轮廓渲染器读取的各列表，视图侧格子高亮 mask 由 GameFieldController.clearSelectionVisuals 归零 */
    public void clearSelectionVisuals() {
        resetSelectFlags(selectableCards);
        resetSelectFlags(activatableCards);
        resetSelectFlags(selectedCards);
        resetSelectFlags(selectsumCards);
        resetSelectFlags(selectsumAll);
        revealHighlightCards.clear();
        selectableCards.clear();
        activatableCards.clear();
        selectedCards.clear();
        selectsumCards.clear();
        selectsumAll.clear();
        displayCards.clear();
    }

    private static void resetSelectFlags(List<ClientCard> list) {
        if (list == null) return;
        for (ClientCard c : list) {
            if (c == null) continue;
            c.is_selectable = false;
            c.is_selected = false;
        }
    }

    public void initial(int player, int deckc, int extrac, int sidec) {
        for (int i = 0; i < deckc && i < players[player].deck.size(); i++) {
            ClientCard pcard = new ClientCard();
            pcard.owner = player;
            pcard.controler = player;
            pcard.location = CardLocation.Deck.value();
            pcard.sequence = i;
            pcard.position = CardPosition.FaceDownDefence.value();
            players[player].deck.set(i, pcard);
            setCardPos(pcard);
        }
        for (int i = 0; i < extrac && i < players[player].extra.size(); i++) {
            ClientCard pcard = new ClientCard();
            pcard.owner = player;
            pcard.controler = player;
            pcard.location = CardLocation.Extra.value();
            pcard.sequence = i;
            pcard.position = CardPosition.FaceDownDefence.value();
            players[player].extra.set(i, pcard);
            setCardPos(pcard);
        }
        for (int i = 0; i < sidec && i < players[player].removed.size(); i++) {
            ClientCard pcard = new ClientCard();
            pcard.owner = player;
            pcard.controler = player;
            pcard.location = CardLocation.Removed.value();
            pcard.sequence = i;
            pcard.position = CardPosition.FaceDownDefence.value();
            players[player].removed.set(i, pcard);
            setCardPos(pcard);
        }
    }

    public void resetSequence(List<ClientCard> list, boolean resetHeight) {
        int seq = 0;
        for (ClientCard pcard : list) {
            if (pcard != null) {
                pcard.sequence = seq++;
                if (resetHeight) {
                    pcard.curZ = 0.01f + 0.01f * pcard.sequence;
                }
            }
        }
    }

    public ClientCard getCard(int controler, int location, int sequence) {
        if (controler < 0 || controler > 1) return null;
        boolean isXyz = (location & CardLocation.Overlay.value()) != 0;
        location &= 0x7f;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null || sequence < 0 || sequence >= list.size()) return null;
        ClientCard card = list.get(sequence);
        if (isXyz && card != null) {
            return null;
        }
        return card;
    }

    public ClientCard getCard(int controler, int location, int sequence, int subSeq) {
        if (controler < 0 || controler > 1) return null;
        boolean isXyz = (location & CardLocation.Overlay.value()) != 0;
        location &= 0x7f;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null || sequence < 0 || sequence >= list.size()) return null;
        ClientCard card = list.get(sequence);
        if (isXyz && card != null) {
            if (subSeq >= 0 && subSeq < card.overlayed.size()) {
                return card.overlayed.get(subSeq);
            }
            return null;
        }
        return card;
    }

    public int getCardCount(int controler, int location) {
        if (controler < 0 || controler > 1) return 0;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null) return 0;
        int count = 0;
        for (ClientCard card : list) {
            if (card != null) count++;
        }
        return count;
    }

    // === 转发桩：行为实现逐行等价移植至同包协作类，对外契约（签名/可见性）不变；增删改·超量·命令选择清理→GameFieldCards(cards)，坐标几何/格子·堆叠矩形→GameFieldGeometry(geometry)，LP 动画·HUD·卡片动画·布局→GameFieldMotion(motion) ===========

    // === LP 变化动画 / HUD 时间·计数显示（GameFieldMotion）===

    public void startLpChange(int player, int finalLp, int color, String text, boolean showText) {
        motion.startLpChange(player, finalLp, color, text, showText);
    }

    public void updateLpAnimation() {
        motion.updateLpAnimation();
    }

    public boolean isLpAnimating() {
        return motion.isLpAnimating();
    }

    public void refreshTimeDisplay() {
        motion.refreshTimeDisplay();
    }

    public void resetTimeTick() {
        motion.resetTimeTick();
    }

    public void tickTime(long nowMs) {
        motion.tickTime(nowMs);
    }

    public void refreshCardCountDisplay() {
        motion.refreshCardCountDisplay();
    }

    // === 卡片增删改 / 超量素材 / 提示（GameFieldCards）===

    public void applyCardHint(int localControler, int location, int sequence, int hintType, int value) {
        cards.applyCardHint(localControler, location, sequence, hintType, value);
    }

    /**
     * 玩家级描述提示（对齐 duelclient.cpp MSG_PLAYER_HINT L4139-4150）：仅当 CARD_QUESTION（谜题）
     * 提示挂在本地玩家（localPlayer==0）时，ADD 置、REMOVE 清 cantCheckGrave「不可确认墓地」禁止态，
     * 由本端点击墓地处（GameFieldControllerBase.showCardCommandMenu）拦截弹 cmdmenudialog。对方座位
     *（localPlayer==1）挂同类提示由其各自客户端自行禁止，本端墓地不受影响，故此处直接忽略。
     */
    public void applyPlayerHint(int localPlayer, int hintType, int value) {
        if (value == CARD_QUESTION && localPlayer == 0) {
            cantCheckGrave = (hintType == PHINT_DESC_ADD);
        }
    }

    public void addChainTarget(int localControler, int location, int sequence) {
        cards.addChainTarget(localControler, location, sequence);
    }

    public void addCard(int controler, int location, int sequence, ClientCard card) {
        cards.addCard(controler, location, sequence, card);
    }

    public ClientCard removeCard(int controler, int location, int sequence) {
        return cards.removeCard(controler, location, sequence);
    }

    public ClientCard attachOverlayMaterial(ClientCard pcard, int oldCtrl, int oldLoc, int oldSeq,
                                            int newCtrl, int newLocBase, int xyzSeq) {
        return cards.attachOverlayMaterial(pcard, oldCtrl, oldLoc, oldSeq, newCtrl, newLocBase, xyzSeq);
    }

    public ClientCard detachOverlayMaterial(int oldCtrl, int oldLocBase, int xyzSeq, int subSeq,
                                            int newCtrl, int newLoc, int newSeq, int newPos) {
        return cards.detachOverlayMaterial(oldCtrl, oldLocBase, xyzSeq, subSeq, newCtrl, newLoc, newSeq, newPos);
    }

    public void moveOverlayMaterials(ClientCard monster, int frame) {
        cards.moveOverlayMaterials(monster, frame);
    }

    public void updateCard(int controler, int location, int sequence, ByteBuffer data) {
        cards.updateCard(controler, location, sequence, data);
    }

    public void moveCard(ClientCard pcard, int frame) {
        cards.moveCard(pcard, frame);
    }

    public void fadeCard(ClientCard pcard, int alpha, int frame) {
        cards.fadeCard(pcard, alpha, frame);
    }

    // === 坐标几何（GameFieldGeometry）：render 包静态入口 + 落点真值，方法体转发 ===

    public static float fx(float x) {
        return GameFieldGeometry.fx(x);
    }

    public static float fieldBoardMinX() {
        return GameFieldGeometry.fieldBoardMinX();
    }

    public static float fieldBoardMaxX() {
        return GameFieldGeometry.fieldBoardMaxX();
    }

    public static float[] getZoneRect(int controler, int location, int sequence) {
        return GameFieldGeometry.getZoneRect(controler, location, sequence);
    }

    public static float[] getPileRect(int controler, int location) {
        return GameFieldGeometry.getPileRect(controler, location);
    }

    /** 返回 {x, y, z, rotX, rotY, rotZ}，与 ClientField::GetCardLocation 一致 */
    public float[] getCardLocation(ClientCard pcard) {
        return geometry.getCardLocation(pcard);
    }

    /** 连锁图标锚点（移植 ClientField::GetChainLocation）：手卡固定于魔陷区后中央不随卡离场，无对应锚点（OVERLAY 等）返回 null 由调用方回退用卡片当前坐标 */
    public float[] getChainLocation(int controler, int location, int sequence) {
        return geometry.getChainLocation(controler, location, sequence);
    }

    // === 卡片动画更新 / 落点归位 / 手卡布局（GameFieldMotion）===

    public void updateCardAnimation(int frame) {
        motion.updateCardAnimation(frame);
    }

    public boolean isAnimating() {
        return motion.isAnimating();
    }

    public void refreshAllCards() {
        motion.refreshAllCards();
    }

    /** 回放「上一步」逆向倒放：重算前抓取当前各显示卡的渲染位置快照（步骤 N 显示态），供投喂线程记录后于落点交主线程 applyReverseAnimation 逆向补间；委托 motion */
    public java.util.List<ReplayPosSnap> collectDisplayPositions() {
        return motion.collectDisplayPositions();
    }

    /** 回放「上一步」逆向倒放：重算落定后把发生位移的卡从步骤 N 显示位逆向补间回 N-1 落定位（仅改渲染层 cur*，不动模型字段），须在主线程调用；委托 motion */
    public void applyReverseAnimation(java.util.List<ReplayPosSnap> snapN) {
        motion.applyReverseAnimation(snapN);
    }

    void setCardPos(ClientCard pcard) {
        motion.setCardPos(pcard);
    }

    /** 飞行起点兜底落位（仅供从未定位过的卡使用）：临时回写旧 c/l/s 求旧区域几何位置再恢复 addCard 已写入的新值，使后续 moveCardAnimated 从旧位置飞向新位置，正常链路不会触达此路径 */
    public void setCardPosForMove(ClientCard pcard, int oldCtrl, int oldLocBase, int oldSeq) {
        if (pcard == null || oldLocBase == 0) return;
        int keepCtrl = pcard.controler;
        int keepLoc = pcard.location;
        int keepSeq = pcard.sequence;
        pcard.controler = oldCtrl;
        pcard.location = oldLocBase;
        pcard.sequence = oldSeq;
        motion.setCardPos(pcard);
        pcard.controler = keepCtrl;
        pcard.location = keepLoc;
        pcard.sequence = keepSeq;
    }

    public void moveCardAnimated(ClientCard pcard, int frame) {
        motion.moveCardAnimated(pcard, frame);
    }

    public void moveCardAnimated(ClientCard pcard, int frame, int delay) {
        motion.moveCardAnimated(pcard, frame, delay);
    }

    /** 带同区滑出抖动（±0.3/帧，对齐 duelclient.cpp MSG_MOVE L3025-3033）的移动动画，jitterX 与动画启动原子设置，消除渲染线程抢先以 jitter=0 收敛的竞态 */
    public void moveCardAnimated(ClientCard pcard, int frame, int delay, float jitterX) {
        motion.moveCardAnimated(pcard, frame, delay, jitterX);
    }

    /** 发动效果抬跳小动画（用户规格：卡片矩形明显抬高后落回原高度，幅度见
     *  GameFieldMotion.BOUNCE_LIFT_Z），供玩家视觉确认怪兽区/额外怪兽区/魔陷区是哪张卡
     *  在发动效果；独立计数器不持动画闸门；委托 motion */
    public void bounceCard(ClientCard pcard) {
        motion.bounceCard(pcard);
    }

    /** 卡组抖动单动画（duelclient.cpp MSG_SHUFFLE_DECK L2637-2650：5 轮 × (3 帧抖开 + 3 帧回位)） */
    public void startDeckShake(ClientCard pcard) {
        motion.startDeckShake(pcard);
    }

    /** 洗手卡聚拢单动画（duelclient.cpp MSG_SHUFFLE_HAND L2662-2699） */
    public void startHandShuffle(ClientCard pcard) {
        motion.startHandShuffle(pcard);
    }

    /** 卡片经效果入手的揭示：对方手卡从卡背翻到正面供对手确认，己方卡仅亮出不翻面 */
    public void startHandReveal(ClientCard pcard, boolean flip) {
        motion.startHandReveal(pcard, flip);
    }

    /** 卡组 / 额外卡组顶卡揭示翻开（duelclient.cpp MSG_CONFIRM_DECKTOP / MSG_CONFIRM_EXTRATOP）：顶卡横向滑出堆叠并旋转面朝相机露出卡面，停留段亮面，调用方随后 moveCardAnimated 转回原位盖回 */
    public void revealTopCardOut(ClientCard pcard, float shiftX, int frame) {
        motion.revealTopCardOut(pcard, shiftX, frame);
    }

    /** 场上里侧卡确认揭示（duelclient.cpp MSG_CONFIRM_CARDS field_confirm）：盖放到场上格子的里侧卡
     *  收到携真实卡码的确认消息后，在本格原地翻到面朝相机露出卡面，停留段后调用方 moveCardAnimated 盖回 */
    public void revealFieldCard(ClientCard pcard, int frame) {
        motion.revealFieldCard(pcard, frame);
    }

    public void setAnimationSpeed(float speed) {
        motion.setAnimationSpeed(speed);
    }

    public void updateHandLayout(int controler, int frame) {
        motion.updateHandLayout(controler, frame);
    }

    // === 命令标记 / 选择状态清理 / 交换场地（GameFieldCards）===

    /** client_field.cpp ClientField::SetShowMark：点击卡片（移动端悬停语义）时联动开关其装备/对象/连锁对象标记 */
    public void setShowMark(ClientCard pcard, boolean enable) {
        cards.setShowMark(pcard, enable);
    }

    public void clearCommandFlag() {
        cards.clearCommandFlag();
    }

    public void clearSelect() {
        cards.clearSelect();
    }

    public void clearChainSelect() {
        cards.clearChainSelect();
    }

    public void swapField() {
        cards.swapField();
    }

    public static boolean clientCardSort(ClientCard c1, ClientCard c2) {
        return GameFieldCards.clientCardSort(c1, c2);
    }
}

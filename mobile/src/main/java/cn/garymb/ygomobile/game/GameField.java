package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ocgcore.enums.CardLocation;
import ocgcore.enums.CardPosition;
import ocgcore.enums.CardType;

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
    /** duelclient.cpp MSG_CARD_HINT L4104 CHINT_DESC_ADD：为卡片追加一条效果文字提示 */
    public static final int CHINT_DESC_ADD = 6;
    /** duelclient.cpp MSG_CARD_HINT L4106 CHINT_DESC_REMOVE：移除一条提示，计数归零才真正删除 */
    public static final int CHINT_DESC_REMOVE = 7;

    public static class ClientCard {
        public int code;
        public int alias;
        public int type;
        public int level;
        public int rank;
        public int attribute;
        public long race;
        public int attack;
        public int defense;
        public int baseAttack;
        public int baseDefense;
        public int position;
        public int owner;
        public int controler;
        public int location;
        public int sequence;
        public int lScale;
        public int rScale;
        public boolean isPublic;
        public boolean isDisabled;
        public int reason;
        public ClientCard equipCard;
        public List<ClientCard> targetCards = new ArrayList<>();
        public List<ClientCard> overlayCards = new ArrayList<>();
        public Map<Integer, Integer> counters = new HashMap<>();
        public int turnCounter;

        public int cmdFlag;
        public int opParam;
        public int select_seq;
        public boolean is_selectable;
        public boolean is_selected;
        public boolean is_highlighting;
        public int chain_code;
        public int link;
        public int link_marker;
        public ClientCard equipTarget;
        public List<ClientCard> equipped = new ArrayList<>();
        /** client_field.h ClientCard::ownerTarget：被哪些卡的永续效果以其为对象（MSG_CARD_TARGET 反向登记） */
        public List<ClientCard> ownerTarget = new ArrayList<>();
        public List<ClientCard> overlayed = new ArrayList<>();
        public ClientCard overlayTarget;
        public int status;

        /**
         * duelclient.cpp MSG_CARD_HINT 的 desc_hints：效果文字描述 id → 引用计数。
         * TreeMap 保证与 C++ std::map 一致按 desc 升序遍历（event_handler.cpp L2933-2936）。
         * 注意：C++ ClientCard::ClearData() 不清 desc_hints，故此处 clearData 也不清。
         */
        public final Map<Integer, Integer> descHints = new TreeMap<>();

        public boolean is_moving;
        public boolean is_fading;
        public boolean is_hovered;
        public boolean is_reversed;
        public boolean is_showequip;
        public boolean is_showtarget;
        public boolean is_showchaintarget;

        public String atkString = "";
        public String defString = "";
        public String lvString = "";
        public String linkString = "";
        public String lscString = "";
        public String rscString = "";

        public float curX, curY, curZ;
        public float curRotX, curRotY, curRotZ;
        public float dPosX, dPosY, dPosZ;
        public float dRotX, dRotY, dRotZ;
        public int aniFrame;
        public float curAlpha = 255;
        public float dAlpha;
        // 动画插值起点/终点与总帧数（缓动轨迹）
        public float animFromX, animFromY, animFromZ;
        public float animToX, animToY, animToZ;
        public float animFromRotX, animFromRotY, animFromRotZ;
        public float animToRotX, animToRotY, animToRotZ;
        public float animFromAlpha, animToAlpha;
        public int animTotalFrame;
        /** 动画启动延迟帧（对齐 duelclient.cpp MSG_MOVE L3032-3038 的 WaitFrameSignal(10)：
         *  带素材怪兽移动时素材先归位，本体延迟若干帧再落上去），延迟期内不插值不扣 aniFrame */
        public float animDelayFrame;
        /** 卡组抖动动画进行中标记（duelclient.cpp MSG_SHUFFLE_DECK L2637-2650 的 5 轮抖动，
         *  轨迹由 GameFieldMotion.updateListAnimation 关键帧推进，见 startDeckShake） */
        public boolean is_deck_shake;
        /** 洗手卡聚拢/翻面动画进行中标记（duelclient.cpp MSG_SHUFFLE_HAND L2662-2699，
         *  轨迹由 GameFieldMotion.updateListAnimation 关键帧推进，见 startHandShuffle） */
        public boolean is_hand_shuffle;
        /** 卡组抖动每轮随机幅度（对应 C++ 每轮 dPos = real_dist(rnd)*0.4-0.2，3 帧总位移 = 3×dPos） */
        public final float[] deckShakeDx = new float[5];
        /** 洗手卡关键帧基准：起始姿态 / 新布局落点 / 聚拢中线位置与翻面终值 */
        public float hsFromX, hsFromY, hsFromZ, hsFromRotX, hsFromRotY;
        public float hsToX, hsToY, hsToZ, hsToRotX, hsToRotY;
        public float hsGatherX, hsFlipRotX, hsFlipRotY;
        /** 洗手卡是否含对手手卡翻面段（duelclient.cpp L2666-2679：player==1 且非回放非单机） */
        public boolean hsFlip;

        public boolean isFaceUp() {
            return (position & (CardPosition.FaceUpAttack.value() | CardPosition.FaceUpDefence.value())) != 0;
        }

        public boolean isAttack() {
            return (position & (CardPosition.FaceUpAttack.value() | CardPosition.FaceDownAttack.value())) != 0;
        }

        public boolean isMonster() {
            return (type & CardType.Monster.getId()) != 0;
        }

        public boolean isExtraCard() {
            return (type & (CardType.Fusion.getId() | CardType.Synchro.getId()
                    | CardType.Xyz.getId() | CardType.Link.getId())) != 0;
        }

        public boolean isLink() {
            return (type & CardType.Link.getId()) != 0;
        }

        public boolean isXyz() {
            return (type & CardType.Xyz.getId()) != 0;
        }

        public void clearCmdFlag() {
            cmdFlag = 0;
            is_selectable = false;
            is_selected = false;
            is_highlighting = false;
        }

        public void setCode(int newCode) {
            if (code == newCode) return;
            if (newCode == 0) {
                chain_code = code;
            }
            code = newCode;
        }

        public void clearData() {
            alias = 0;
            type = 0;
            level = 0;
            rank = 0;
            race = 0;
            attribute = 0;
            attack = 0;
            defense = 0;
            baseAttack = 0;
            baseDefense = 0;
            lScale = 0;
            rScale = 0;
            link = 0;
            link_marker = 0;
            status = 0;

            atkString = "";
            defString = "";
            lvString = "";
            linkString = "";
            lscString = "";
            rscString = "";
            counters.clear();
        }

        /** duelclient.cpp L4105：desc_hints[value]++ */
        public void addDescHint(int desc) {
            Integer c = descHints.get(desc);
            descHints.put(desc, c == null ? 1 : c + 1);
        }

        /** duelclient.cpp L4107-4109：desc_hints[value]--，归零 erase */
        public void removeDescHint(int desc) {
            Integer c = descHints.get(desc);
            if (c == null) return;
            if (c - 1 <= 0) descHints.remove(desc);
            else descHints.put(desc, c - 1);
        }

        /** duelclient.cpp MSG_SHUFFLE_HAND L2691：洗手卡后逐张 desc_hints.clear() */
        public void clearDescHints() {
            descHints.clear();
        }

        public void clearTarget() {
            for (ClientCard pcard : targetCards) {
                pcard.is_showtarget = false;
                pcard.overlayCards.remove(this);
            }
            for (ClientCard pcard : overlayCards) {
                pcard.is_showtarget = false;
                pcard.targetCards.remove(this);
            }
            targetCards.clear();
            overlayCards.clear();
        }

        public void updateQuery(ByteBuffer buf) {
            if (buf.remaining() < 4) return;
            int flag = buf.getInt();
            if (flag == 0) {
                clearData();
                return;
            }
            if ((flag & QUERY_CODE) != 0 && buf.remaining() >= 4) {
                int pdata = buf.getInt();
                if (pdata == 0) clearData();
                setCode(pdata);
            }
            if ((flag & QUERY_POSITION) != 0 && buf.remaining() >= 4) {
                int pdata = (buf.getInt() >> 24) & 0xff;
                position = pdata;
            }
            if ((flag & QUERY_ALIAS) != 0 && buf.remaining() >= 4) alias = buf.getInt();
            if ((flag & QUERY_TYPE) != 0 && buf.remaining() >= 4) type = buf.getInt();
            if ((flag & QUERY_LEVEL) != 0 && buf.remaining() >= 4) {
                int pdata = buf.getInt();
                if (level != pdata) {
                    level = pdata;
                    lvString = "L" + level;
                }
            }
            if ((flag & QUERY_RANK) != 0 && buf.remaining() >= 4) {
                int pdata = buf.getInt();
                if (pdata != 0 && rank != pdata) {
                    rank = pdata;
                    lvString = "R" + rank;
                }
            }
            if ((flag & QUERY_ATTRIBUTE) != 0 && buf.remaining() >= 4) attribute = buf.getInt();
            if ((flag & QUERY_RACE) != 0 && buf.remaining() >= 4) race = buf.getInt();
            if ((flag & QUERY_ATTACK) != 0 && buf.remaining() >= 4) {
                attack = buf.getInt();
                atkString = attack < 0 ? "?" : String.valueOf(attack);
            }
            if ((flag & QUERY_DEFENSE) != 0 && buf.remaining() >= 4) {
                defense = buf.getInt();
                if (isLink()) {
                    defString = "-";
                } else {
                    defString = defense < 0 ? "?" : String.valueOf(defense);
                }
            }
            if ((flag & QUERY_BASE_ATTACK) != 0 && buf.remaining() >= 4) baseAttack = buf.getInt();
            if ((flag & QUERY_BASE_DEFENSE) != 0 && buf.remaining() >= 4)
                baseDefense = buf.getInt();
            if ((flag & QUERY_REASON) != 0 && buf.remaining() >= 4) reason = buf.getInt();
            if ((flag & QUERY_REASON_CARD) != 0 && buf.remaining() >= 4) buf.getInt();
            if ((flag & QUERY_EQUIP_CARD) != 0 && buf.remaining() >= 4) {
                buf.get();
                buf.get();
                buf.get();
                buf.get();
            }
            if ((flag & QUERY_TARGET_CARD) != 0 && buf.remaining() >= 4) {
                int count = buf.getInt();
                for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                    buf.get();
                    buf.get();
                    buf.get();
                    buf.get();
                }
            }
            if ((flag & QUERY_OVERLAY_CARD) != 0 && buf.remaining() >= 4) {
                int count = buf.getInt();
                for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                    int ocode = buf.getInt();
                    if (i >= overlayed.size()) {
                        ClientCard xcard = new ClientCard();
                        overlayed.add(xcard);
                        xcard.overlayTarget = this;
                        xcard.location = CardLocation.Overlay.value();
                        xcard.sequence = overlayed.size() - 1;
                        xcard.owner = controler;
                        xcard.controler = controler;
                    }
                    overlayed.get(i).setCode(ocode);
                }
            }
            if ((flag & QUERY_COUNTERS) != 0 && buf.remaining() >= 4) {
                int count = buf.getInt();
                for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                    int ctype = buf.getShort() & 0xFFFF;
                    int ccount = buf.getShort() & 0xFFFF;
                    counters.put(ctype, ccount);
                }
            }
            if ((flag & QUERY_OWNER) != 0 && buf.remaining() >= 4) owner = buf.getInt();
            if ((flag & QUERY_STATUS) != 0 && buf.remaining() >= 4) status = buf.getInt();
            if ((flag & QUERY_LSCALE) != 0 && buf.remaining() >= 4) {
                lScale = buf.getInt();
                lscString = String.valueOf(lScale);
            }
            if ((flag & QUERY_RSCALE) != 0 && buf.remaining() >= 4) {
                rScale = buf.getInt();
                rscString = String.valueOf(rScale);
            }
            if ((flag & QUERY_LINK) != 0 && buf.remaining() >= 8) {
                int pdata = buf.getInt();
                if (link != pdata) {
                    link = pdata;
                }
                linkString = "L\u2013" + link;
                int pdata2 = buf.getInt();
                if (link_marker != pdata2) {
                    link_marker = pdata2;
                }
            }
        }
    }

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
        /**
         * 连锁图标（chain 旋转图 / number 序号）位置快照：连锁成立（MSG_CHAINED）时捕捉发动卡
         * 所在 xyz，此后卡片因结算等离开原位时图标不跟随移动，停留在原地直到连锁消失。
         */
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
    public List<ChainInfo> chains = new ArrayList<>();

    /**
     * 正在构建中的连锁（对齐 duelclient.cpp dField.current_chain）：
     * MSG_CHAINING 重建、MSG_BECOME_TARGET 追加 target、MSG_CHAINED 压入 chains。
     * 供 ClientField::ShowCardInfoInList 生成「在连锁%d发动」「被连锁%d的[%ls]选择为对象」标签。
     */
    public ChainInfo currentChain = new ChainInfo();
    public List<ClientCard> overlayCards = new ArrayList<>();
    final List<PendingOverlay> pendingOverlays = new ArrayList<>();
    public int[] extraPCount = new int[2];
    public long disabledField;
    public boolean deckReversed;
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

    /**
     * 攻击宣言绿色弧形流动动画状态（对齐 duelclient.cpp MSG_ATTACK L3817-3866 +
     * materials.cpp GenArrow + drawing.cpp L1504-1513 attack_sv 窗口流动）。
     * onAttack 时写入攻击者/目标卡与起始时间戳，GameFieldView.drawAttackArc 读取并在约 0.9s 内绘制。
     * arcTarget 为 null 表示直接攻击，绘制时落到对方场地一侧的固定点。
     */
    public ClientCard arcAttacker;
    public ClientCard arcTarget;
    public volatile long arcStartMs;

    public List<ClientCard> selectableCards = new ArrayList<>();
    public List<ClientCard> selectedCards = new ArrayList<>();
    public List<ClientCard> selectsumCards = new ArrayList<>();
    public List<ClientCard> selectsumAll = new ArrayList<>();
    public List<ClientCard> displayCards = new ArrayList<>();
    public int[] sortList;
    public int selectMin, selectMax, mustSelectCount;
    public int selectSumval, selectMode;
    public int selectHint;
    /** 对齐 duelclient.cpp L40 event_string：由 HINT_EVENT 及召唤/抽卡/伤害/攻击/连锁等事件消息写入，
     *  在 MSG_SELECT_CHAIN / MSG_SELECT_EFFECTYN 询问前作为「触发时点」前缀拼接（L2176/2178） */
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
        public int[] timeColor = {0xFFFFFFFF, 0xFFFFFFFF};
        public int[] cardCount = new int[2];
        public int[] cardCountColor = {0xFFFFFFFF, 0xFFFFFFFF};
        public int[] totalAttack = new int[2];
        public int[] totalAttackColor = {0xFFFFFFFF, 0xFFFFFFFF};
    }

    public final DuelInfo dInfo = new DuelInfo();

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
    /** 区域规格：怪兽/魔陷区宽 290px、高 254px，格子间 2px 间隔 → 横向间距 292px；
     *  X 坐标以场地中心 3.95 为轴按 (292px世界长)/1.1 放大，决斗场更宽且与 field3.png 拉伸适配 */
    static final float X_SCALE = (292f * 0.8f / 177f) / 1.1f;

    /** C++ materials.cpp vField：底板贴图 uv(0,0)-(1,1) 覆盖的世界矩形。
     *  底板、格子、卡片落点共用 fx()/恒等 Y 这同一仿射映射，故贴图网格与格子必然完美重叠 */
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
        contiAct = false;
        disabledField = 0;
        deckReversed = false;
        cantCheckGrave = false;
        tagSurrender = false;
        tagTeammateSurrender = false;
        currentPlayer = 0;
        currentPhase = 0;
        turnCount = 0;
        extraPCount[0] = 0;
        extraPCount[1] = 0;
        eventString = "";
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

    // ================================================================================================
    //  以下为转发桩：行为实现逐行等价移植至同包协作类，对外契约（签名 / 可见性）保持不变。
    //  · 卡片增删改 / 超量素材 / 命令·选择状态清理   → GameFieldCards (cards)
    //  · 坐标几何 GetCardLocation / 格子·堆叠矩形      → GameFieldGeometry (geometry)
    //  · LP 动画 / HUD 时间·计数 / 卡片动画更新 / 布局 → GameFieldMotion (motion)
    // ================================================================================================

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

    void setCardPos(ClientCard pcard) {
        motion.setCardPos(pcard);
    }

    public void moveCardAnimated(ClientCard pcard, int frame) {
        motion.moveCardAnimated(pcard, frame);
    }

    public void moveCardAnimated(ClientCard pcard, int frame, int delay) {
        motion.moveCardAnimated(pcard, frame, delay);
    }

    /** 卡组抖动单动画（duelclient.cpp MSG_SHUFFLE_DECK L2637-2650：5 轮 × (3 帧抖开 + 3 帧回位)） */
    public void startDeckShake(ClientCard pcard) {
        motion.startDeckShake(pcard);
    }

    /** 洗手卡聚拢/翻面单动画（duelclient.cpp MSG_SHUFFLE_HAND L2662-2699） */
    public void startHandShuffle(ClientCard pcard, boolean flip) {
        motion.startHandShuffle(pcard, flip);
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

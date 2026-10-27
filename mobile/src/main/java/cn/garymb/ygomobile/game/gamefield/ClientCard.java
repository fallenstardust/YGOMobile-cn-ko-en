package cn.garymb.ygomobile.game.gamefield;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ocgcore.enums.CardLocation;
import ocgcore.enums.CardPosition;
import ocgcore.enums.CardType;

/** 卡片客户端数据模型（自 GameField 的静态内部类拆出，对齐 client_field.cpp / materials.cpp ClientCard）：卡面属性、命令/选择标记、动画插值状态与引擎查询块解码；查询开关沿用 GameField 的静态字段。 */
public class ClientCard {
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

    /** duelclient.cpp MSG_CARD_HINT 的 desc_hints：效果文字 id→引用计数；TreeMap 对齐 C++ std::map 升序遍历，ClearData 不清（与 C++ 一致）。 */
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
    /** 手卡点击抬高动画进度（0=原位，1=完全抬高）：渲染线程逐帧线性推进，对齐 client_card.cpp 手卡 MoveCard(5)（约 5 帧）。 */
    public float handLiftAnim;
    // 动画插值起点/终点与总帧数（缓动轨迹）
    public float animFromX, animFromY, animFromZ;
    public float animToX, animToY, animToZ;
    public float animFromRotX, animFromRotY, animFromRotZ;
    public float animToRotX, animToRotY, animToRotZ;
    public float animFromAlpha, animToAlpha;
    public int animTotalFrame;
    /** 同区重排抖动横向速度（对齐 MSG_MOVE L3022-3030 侧移后回正）：非零时改用「先侧移后归位」两阶段轨迹，符号 pc==1 取正。 */
    public float animJitterX;
    /** 动画启动延迟帧（对齐 MSG_MOVE L3032-3038 WaitFrameSignal(10)：素材先归位、本体延迟再落），延迟期内不插值不扣 aniFrame。 */
    public float animDelayFrame;
    /** 发动效果抬跳动画进行中标记（用户规格：怪兽区/额外怪兽区/魔陷区的卡发动效果时，
     *  卡片矩形明显抬高并落回原高度的短暂小动画，供玩家视觉确认是哪张卡发动；
     *  幅度见 GameFieldMotion.BOUNCE_LIFT_Z）：
     *  由 GameFieldMotion.bounceCard 触发，逐帧段在 updateListAnimation 以独立计数器推进，
     *  不占用 aniFrame——短暂提示动画不参与统一动画屏障（isAnimating）持闸。 */
    public boolean is_bounce;
    /** 抬跳基准 Z（起动瞬间记录）：卡正处移动动画时取 animToZ（移动终态高度），否则取 curZ；
     *  抬高量叠加在此基准上，结束精确落回，与 is_moving 的 Z 插值互不冲突（场上卡移动前后 Z 同权威位） */
    public float bounceBaseZ;
    /** 抬跳剩余帧（独立于 aniFrame 计数，animationSpeed 逐帧扣减） */
    public float bounceFrame;
    /** 抬跳总帧数（进度归一化用，正弦半周期：起终点高度=基准，峰值=基准+BOUNCE_LIFT_Z） */
    public int bounceTotal;
    /** 抬跳发光强度 0..1（用户规格：围绕卡片矩形边缘的外发光随抬高一起亮、回落恢复原状，
     *  颜色随卡种——怪兽 holo 橙/魔法荧光绿/陷阱荧光玫红）：
     *  由 updateListAnimation 每帧写入与抬高量同一条正弦半周期曲线，渲染层
     *  FieldCardRenderer.drawBounceGlow 按此控制边缘发光的亮度与晕带宽度 */
    public float bounceGlow;
    /** 卡组抖动动画进行中标记（duelclient.cpp MSG_SHUFFLE_DECK L2637-2650 的 5 轮抖动，轨迹由 GameFieldMotion.updateListAnimation 关键帧推进，见 startDeckShake） */
    public boolean is_deck_shake;
    /** 洗手卡聚拢动画进行中标记（duelclient.cpp MSG_SHUFFLE_HAND L2662-2699，轨迹由 GameFieldMotion.updateListAnimation 关键帧推进，见 startHandShuffle） */
    public boolean is_hand_shuffle;
    /** 卡组抖动每轮随机幅度（对应 C++ 每轮 dPos = real_dist(rnd)*0.4-0.2，3 帧总位移 = 3×dPos） */
    public final float[] deckShakeDx = new float[5];
    /** 洗手卡关键帧基准：新布局落点与聚拢中线位置 */
    public float hsToX, hsToY, hsToZ, hsToRotX, hsToRotY;
    public float hsGatherX;
    /** 手卡翻面进度：1=正面、0=卡背、-1=未参与翻面；目标值恒由卡码决定（code!=0→1），updateHandFlip 每帧 0.2 靠拢（5 帧，对齐 MSG_SHUFFLE_HAND L2672 dRot 步进）。手卡走相机 billboard 不读 curRot*，只有本值驱动的 X 轴挤压+正/背面贴图切换才能真正翻面；效果入手时把对方手卡置 0 供其确认，洗切清零后自动翻回卡背。 */
    public float handFlipT = -1f;
    /** 入手揭示持闸窗口内：本卡正处于「非抽卡入手向对手翻面确认」的展示期（DeckHandMotionManager.applyMoveToHandReveal 置位，展示结束即清除）。揭示用的真实卡码来自公开来源入手的 MSG_MOVE 或随后的 MSG_CONFIRM_CARDS，而紧随的手卡遮蔽刷新（MSG_UPDATE_DATA/MSG_UPDATE_CARD/MSG_SHUFFLE_HAND 零码块）会把它打回 0，翻面确认半途变回卡背；持闸窗口内与回放态 preserveMaskedHandCode 同法保护，窗口结束后恢复正常遮蔽语义。 */
    public boolean revealHeld;
    /** 遮蔽入手冻结待码：客机加入遮蔽型服务器时，对方「卡组→手卡」的 MSG_MOVE 卡码为 0，而真实卡码由紧随其后的 MSG_CONFIRM_CARDS 送达。本标记的卡已入手但冻结在卡组顶坐标不动（不参与手牌重排/飞入），等写入卡码后由 applyMoveToHandReveal 直接以正面飞入手卡，消除「背面入手→翻开」两步观感；无确认消息时超时兜底释放照旧背面入位。 */
    public boolean pendingFaceupAdd;
    /** 揭示翻面态（对齐 duelclient.cpp MSG_CONFIRM_DECKTOP / MSG_CONFIRM_EXTRATOP 的 dRot(π/5)+is_moving，
     *  及 MSG_CONFIRM_CARDS field_confirm L2580-2597 场上里侧卡的同型翻开停留段）：顶卡揭示与场上里侧卡
     *  确认揭示期间置位，表示本卡正被动画转到面朝相机亮出卡面（非表侧卡亦按卡码贴卡面，与 C++
     *  drawing.cpp 移动中卡片同绘正反两面等效；渲染层实际按「朝向+卡码」判定，本标记为动画语义登记）。
     *  由 GameFieldMotion.revealTopCardOut / revealFieldCard 置位，翻回卡背后由 DeckHandMotionManager
     *  调度清除（不入通用落定清理，以免中途抹掉停留段）。 */
    public boolean top_reveal;

    public boolean isFaceUp() {
        return (position & (CardPosition.FaceUpAttack.value() | CardPosition.FaceUpDefence.value())) != 0;
    }

    /** 本卡是否为叠在下层的超量素材（location==OVERLAY）：素材恒表侧公开，但引擎查询只携宿主姿态，position 全凭客户端补齐；凡需判「这张卡一定朝上」统一经此，避免占位卡带 0 姿态被画成卡背。 */
    public boolean isOverlayMaterial() {
        return location == CardLocation.Overlay.value();
    }

    /** 展示用卡码（对齐 event_handler.cpp L1673 hover 只判 mcard->code 的语义扩展）：场上里侧表示卡一旦被公开（POS_REVEAL 解遮蔽 / 无限泡影等 MSG_POS_CHANGE 携真实码），后续遮蔽型零码查询即便把 code 洗回 0，setCode(0) 也已把原公开码备份进 chain_code（client_card.cpp SetCode L37-48 同款备份，本为飞行途中继续绘卡面而设），点击详情 / 长按悬浮 / 渲染层浮现卡图一律经本方法取「已公开的最新卡码」。 */
    public int displayCode() {
        return code != 0 ? code : chain_code;
    }

    /** 本卡码是否属于「已公开过卡码的场上里侧卡」（reveal-once-stay-known 保码判据） */
    public boolean isRevealedFieldFaceDown() {
        return (location & 0x0C) != 0 && !isFaceUp() && code != 0;
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
            // 遮蔽保护（回放态 GameField.preserveMaskedHandCode / 实况入手揭示持闸 revealHeld）：整块清零的遮蔽刷新不清掉手卡区已有的真实卡码；场上里侧卡一经公开（POS_REVEAL 解遮蔽 / 泡影类 POS_CHANGE 携码）同样保留，不洗回 0
            if (!((GameField.preserveMaskedHandCode || revealHeld) && (location & 0x02) != 0 && code != 0)
                    && !isRevealedFieldFaceDown()) {
                clearData();
            }
            return;
        }
        if ((flag & GameField.QUERY_CODE) != 0 && buf.remaining() >= 4) {
            int pdata = buf.getInt();
            // 遮蔽保护：仅卡码被服务器置零的手卡块——保留已揭示的真实卡码（回放态或入手揭示持闸窗口内）；场上已公开的里侧卡同理保码
            if (pdata == 0 && ((GameField.preserveMaskedHandCode || revealHeld) && (location & 0x02) != 0 && code != 0)) {
                // 跳过 clearData/setCode(0)，其余字段照旧解析
            } else if (pdata == 0 && isRevealedFieldFaceDown()) {
                // 场上里侧已公开：跳过洗码，姿态等其余字段照旧解析
            } else {
                if (pdata == 0) clearData();
                setCode(pdata);
            }
        }
        if ((flag & GameField.QUERY_POSITION) != 0 && buf.remaining() >= 4) {
            int pdata = (buf.getInt() >> 24) & 0xff;
            // 翻转召唤/位置变更：position 改变需强制客户端重置动画插值状态并重绘。旧实现仅写 position，未清除动画字段，导致卡片仍沿用旧 curRotX/curRotY 插值结果渲染（如仍显示里侧守备），玩家误以为操作未生效；此处清动画字段可让 buildCardModel 立即用新姿态计算正确旋转角。
            if (position != pdata && code > 0) {
                position = pdata;
                /** 重置动画插值起点/终点与帧数，确保下帧立即用新姿态目标值，不继续旧动画 */
                animFromX = animToX = curX;
                animFromY = animToY = curY;
                animFromZ = animToZ = curZ;
                animFromRotX = animToRotX = dRotX;
                animFromRotY = animToRotY = dRotY;
                animFromRotZ = animToRotZ = dRotZ;
                animTotalFrame = 0;
            } else {
                position = pdata;
            }
        }
        if ((flag & GameField.QUERY_ALIAS) != 0 && buf.remaining() >= 4) alias = buf.getInt();
        if ((flag & GameField.QUERY_TYPE) != 0 && buf.remaining() >= 4) type = buf.getInt();
        if ((flag & GameField.QUERY_LEVEL) != 0 && buf.remaining() >= 4) {
            int pdata = buf.getInt();
            if (level != pdata) {
                level = pdata;
                // 连接怪兽无等级（引擎查询 level=0，链接值另由 QUERY_LINK 呈现）：左上角不显示等级文本，避免「L0」；其余怪兽照旧（TYPE 在 LEVEL 之前读入，此处 isLink() 已反映本块最新卡型）
                lvString = (pdata != 0 && !isLink()) ? "L" + level : "";
            }
        }
        if ((flag & GameField.QUERY_RANK) != 0 && buf.remaining() >= 4) {
            int pdata = buf.getInt();
            if (pdata != 0 && rank != pdata) {
                rank = pdata;
                lvString = "R" + rank;
            }
        }
        if ((flag & GameField.QUERY_ATTRIBUTE) != 0 && buf.remaining() >= 4) attribute = buf.getInt();
        if ((flag & GameField.QUERY_RACE) != 0 && buf.remaining() >= 4) race = buf.getInt();
        if ((flag & GameField.QUERY_ATTACK) != 0 && buf.remaining() >= 4) {
            attack = buf.getInt();
            atkString = attack < 0 ? "?" : String.valueOf(attack);
        }
        if ((flag & GameField.QUERY_DEFENSE) != 0 && buf.remaining() >= 4) {
            defense = buf.getInt();
            if (isLink()) {
                defString = "-";
            } else {
                defString = defense < 0 ? "?" : String.valueOf(defense);
            }
        }
        if ((flag & GameField.QUERY_BASE_ATTACK) != 0 && buf.remaining() >= 4) baseAttack = buf.getInt();
        if ((flag & GameField.QUERY_BASE_DEFENSE) != 0 && buf.remaining() >= 4)
            baseDefense = buf.getInt();
        if ((flag & GameField.QUERY_REASON) != 0 && buf.remaining() >= 4) reason = buf.getInt();
        if ((flag & GameField.QUERY_REASON_CARD) != 0 && buf.remaining() >= 4) buf.getInt();
        if ((flag & GameField.QUERY_EQUIP_CARD) != 0 && buf.remaining() >= 4) {
            buf.get();
            buf.get();
            buf.get();
            buf.get();
        }
        if ((flag & GameField.QUERY_TARGET_CARD) != 0 && buf.remaining() >= 4) {
            int count = buf.getInt();
            for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                buf.get();
                buf.get();
                buf.get();
                buf.get();
            }
        }
        if ((flag & GameField.QUERY_OVERLAY_CARD) != 0 && buf.remaining() >= 4) {
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
                ClientCard mat = overlayed.get(i);
                // 本块查询不携素材姿态（引擎只给宿主的），而渲染层靠 isFaceUp() 决定正/背面，故凡从本块认得的素材一律钉成表侧：新建的占位如此，MSG_RELOAD_FIELD 已建的占位（此处只补卡码的那条路径）也如此——后者若不重设，一旦占位来自未带姿态的旧数据就会被画成卡背，而卡码明明存在（查看详情可见卡面讯息）
                mat.position = GameField.POS_FACEUP;
                mat.setCode(ocode);
            }
        }
        if ((flag & GameField.QUERY_COUNTERS) != 0 && buf.remaining() >= 4) {
            int count = buf.getInt();
            for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                int ctype = buf.getShort() & 0xFFFF;
                int ccount = buf.getShort() & 0xFFFF;
                counters.put(ctype, ccount);
            }
        }
        if ((flag & GameField.QUERY_OWNER) != 0 && buf.remaining() >= 4) owner = buf.getInt();
        if ((flag & GameField.QUERY_STATUS) != 0 && buf.remaining() >= 4) status = buf.getInt();
        if ((flag & GameField.QUERY_LSCALE) != 0 && buf.remaining() >= 4) {
            lScale = buf.getInt();
            lscString = String.valueOf(lScale);
        }
        if ((flag & GameField.QUERY_RSCALE) != 0 && buf.remaining() >= 4) {
            rScale = buf.getInt();
            rscString = String.valueOf(rScale);
        }
        if ((flag & GameField.QUERY_LINK) != 0 && buf.remaining() >= 8) {
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

package ocgcore.enums;

public enum CardCategory {
    None(0),
    DESTROY(0x1, 1100),//破坏效果
    RELEASE(0x2, 1101),//解放效果
    REMOVE(0x4, 1102),//除外效果
    TOHAND(0x8, 1103),//加入手牌效果
    TODECK(0x10, 1104),//回卡组效果
    TOGRAVE(0x20, 1105),//送去墓地效果
    DECKDES(0x40, 1106),//卡组破坏效果
    HANDES(0x80, 1107),//手牌破坏效果
    SUMMON(0x100, 1108),//含召唤的效果
    SPECIAL_SUMMON(0x200, 1109),//含特殊召唤的效果
    TOKEN(0x400, 1110),//含衍生物效果
    FLIP(0x800, 1111),//含翻转效果
    POSITION(0x1000, 1112),//改变表示形式效果
    CONTROL(0x2000, 1113),//改变控制权效果
    DISABLE(0x4000, 1114),//使效果无效效果
    DISABLE_SUMMON(0x8000, 1115),//无效召唤效果
    DRAW(0x10000, 1116),//抽卡效果
    SEARCH(0x20000, 1117),//检索卡组效果
    EQUIP(0x40000, 1118),//装备效果
    DAMAGE(0x80000, 1119),//伤害效果
    RECOVER(0x100000, 1120),//回复效果
    ATKCHANGE(0x200000, 1121),//改变攻击效果
    DEFCHANGE(0x400000, 1122),//改变防御效果
    COUNTER(0x800000, 1123),//指示物效果
    COIN(0x1000000, 1124),//硬币效果
    DICE(0x2000000, 1125),//骰子效果
    LEAVE_GRAVE(0x4000000, 1126),//离开墓地效果
    LVCHANGE(0x8000000, 1127),//改变等级效果
    NEGATE(0x10000000, 1128),//融合相关
    ANNOUNCE(0x20000000, 1129),//同调相关
    FUSION_SUMMON(0x40000000, 1130),//超量相关
    NEGATE_EFFECT(0x80000000L, 1131);//效果无效

    private long value = 0;
    private final int lang_index;

    public int getLanguageIndex() {
        return lang_index;
    }

    CardCategory(long value){
        this(value, 0);
    }

    CardCategory(long value, int lang_index) {
        this.value = value;
        this.lang_index = lang_index;
    }

    public static CardCategory valueOf(long value) {
        CardCategory[] attributes = CardCategory.values();
        for (CardCategory attribute : attributes) {
            if (attribute.value() == value) {
                return attribute;
            }
        }
        return null;
    }

    public long value() {
        return this.value;
    }
}

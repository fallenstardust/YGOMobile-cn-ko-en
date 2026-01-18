package ocgcore.enums;

public enum CardCategory {
    //None(0),
    DESTROY_SPELL_TRAP(0x1, 1100),//魔陷破坏
    DESTROY_MONSTER(0x2, 1101),//怪兽破坏
    BANISH(0x4, 1102),//卡片除外
    TOGRAVEYARD(0x8, 1103),//送去墓地
    RETURNTOHAND(0x10, 1104),//返回手卡
    RETURNTODECK(0x20, 1105),//返回卡组
    DESTROY_HAND(0x40, 1106),//手卡破坏
    DESTROY_DECK(0x80, 1107),//卡组破坏
    DRAW(0x100, 1108),//抽卡辅助
    SEARCH(0x200, 1109),//卡组检索
    RECOVERY(0x400, 1110),//卡片回收
    POSITION(0x800, 1111),//表示形式
    CONTROL(0x1000, 1112),//控制权
    CHANGE_ATK_DEF(0x2000, 1113),//攻守变化
    PIERCING(0x4000, 1114),//穿刺伤害
    REPEAT_ATTACK(0x8000, 1115),//多次攻击
    LIMIT_ATTACK(0x10000, 1116),//攻击限制
    DIRECT_ATTACK(0x20000, 1117),//直接攻击
    SPECIAL_SUMMON(0x40000, 1118),//特殊召唤
    TOKEN(0x80000, 1119),//衍生物
    RACE_RELATED(0x100000, 1120),//种族相关
    ATTRIBUTE_RELATED(0x200000, 1121),//属性相关
    DAMAGE_LP(0x400000, 1122),//LP伤害
    RECOVER_LP(0x800000, 1123),//LP回复
    UNDESTORYABLE(0x1000000, 1124),//破坏耐性
    UNEFFECTIVE(0x2000000, 1125),//效果耐性
    COUNTER(0x4000000, 1126),//指示物
    GAMBLE(0x8000000, 1127),//幸运
    FUSION_RELATED(0x10000000, 1128),//融合相关
    SYNCHRO_RELATED(0x20000000, 1129),//同调相关
    XYZ_RELATED(0x40000000, 1130),//超量相关
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

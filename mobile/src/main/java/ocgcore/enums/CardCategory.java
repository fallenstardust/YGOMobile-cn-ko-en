package ocgcore.enums;

public enum CardCategory {
    None(0),
    DESTROY(0x1),//破坏效果
    RELEASE(0x2),//解放效果
    REMOVE(0x4),//除外效果
    TOHAND(0x8),//加入手牌效果
    TODECK(0x10),//回卡组效果
    TOGRAVE(0x20),//送去墓地效果
    DECKDES(0x40),//卡组破坏效果
    HANDES(0x80),//手牌破坏效果
    SUMMON(0x100),//含召唤的效果
    SPECIAL_SUMMON(0x200),//含特殊召唤的效果
    TOKEN(0x400),//含衍生物效果
    FLIP(0x800),//含翻转效果
    POSITION(0x1000),//改变表示形式效果
    CONTROL(0x2000),//改变控制权效果
    DISABLE(0x4000),//使效果无效效果
    DISABLE_SUMMON(0x8000),//无效召唤效果
    DRAW(0x10000),//抽卡效果
    SEARCH(0x20000),//检索卡组效果
    EQUIP(0x40000),//装备效果
    DAMAGE(0x80000),//伤害效果
    RECOVER(0x100000),//回复效果
    ATKCHANGE(0x200000),//改变攻击效果
    DEFCHANGE(0x400000),//改变防御效果
    COUNTER(0x800000),//指示物效果
    COIN(0x1000000),//硬币效果
    DICE(0x2000000),//骰子效果
    LEAVE_GRAVE(0x4000000),//离开墓地效果
    LVCHANGE(0x8000000),//改变等级效果
    NEGATE(0x10000000),//使发动无效效果
    ANNOUNCE(0x20000000),//發動時宣言卡名的效果
    FUSION_SUMMON(0x40000000),
    NEGATE_EFFECT(0x80000000L);//效果无效
    private long value = 0;

    private CardCategory(long value) {
        this.value = value;
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

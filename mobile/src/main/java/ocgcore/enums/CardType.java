package ocgcore.enums;

public enum CardType {
    None(0),
    Normal(0x10),
    Effect(0x20),
    Fusion(0x40),
    Ritual(0x80),
    Synchro(0x2000),
    Pendulum(0x1000000L),
    Xyz(0x800000L),
    Link(0x4000000L),
    Non_Effect(0x8000000L),

    Spirit(0x200),
    Union(0x400),
    Dual(0x800),
    Tuner(0x1000),
    Flip(0x200000),
    Toon(0x400000),
    Sp_Summon(0x2000000),

    QuickPlay(0x10000),
    Continuous(0x20000),
    Equip(0x40000),
    Field(0x80000),
    Counter(0x100000),

    Monster(0x1),
    Spell(0x2),
    TrapMonster(0x100),
    Trap(0x4),
    Token(0x4000);
    private long value = 0;

    private CardType(long value) {
        this.value = value;
    }

    public static CardType valueOf(long value) {
        CardType[] attributes = CardType.values();
        for (CardType attribute : attributes) {
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

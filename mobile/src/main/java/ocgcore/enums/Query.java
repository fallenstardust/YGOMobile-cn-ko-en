package ocgcore.enums;

public enum Query {
    Code(0x01),
    Position(0x02),
    Alias(0x04),
    Type(0x08),
    Level(0x10),
    Rank(0x20),
    Attribute(0x40),
    Race(0x80),
    Attack(0x100),
    Defence(0x200),
    BaseAttack(0x400),
    BaseDefence(0x800),
    Reason(0x1000),
    ReasonCard(0x2000),
    EquipCard(0x4000),
    TargetCard(0x8000),
    OverlayCard(0x10000),
    Counters(0x20000),
    Owner(0x40000),
    IsDisabled(0x80000),
    IsPublic(0x100000),
    LScale(0x200000),
    RScale(0x400000);
    private int value = 0;

    Query(int value) {
        this.value = value;
    }

    public static Query valueOf(int value) {
        Query[] attributes = Query.values();
        for (Query attribute : attributes) {
            if (attribute.value() == value) {
                return attribute;
            }
        }
        return null;
    }

    public int value() {
        return this.value;
    }
}

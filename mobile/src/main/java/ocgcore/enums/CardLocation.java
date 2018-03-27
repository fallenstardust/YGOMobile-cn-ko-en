package ocgcore.enums;

public enum CardLocation {
    Deck(0x01),
    Hand(0x02),
    MonsterZone(0x04),
    SpellZone(0x08),
    Grave(0x10),
    Removed(0x20),
    Extra(0x40),
    Overlay(0x80),
    Onfield(0x0C);
    private int value = 0;

    private CardLocation(int value) {
        this.value = value;
    }
    public static CardLocation valueOf(int value) {
        CardLocation[] attributes = CardLocation.values();
        for (CardLocation attribute : attributes) {
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

package ocgcore.enums;

public enum CardAttribute {
    None(0),
    Earth(0x01),
    Water(0x02),
    Fire(0x04),
    Wind(0x08),
    Light(0x10),
    Dark(0x20),
    Divine(0x40);

    private int value = 0;

    private CardAttribute(int value) {
        this.value = value;
    }

    public static CardAttribute valueOf(int value) {
        CardAttribute[] attributes = CardAttribute.values();
        for (CardAttribute attribute : attributes) {
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
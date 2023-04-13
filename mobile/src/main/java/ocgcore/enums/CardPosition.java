package ocgcore.enums;

public enum CardPosition {
    FaceUpAttack(0x1),
    FaceDownAttack(0x2),
    FaceUpDefence(0x4),
    FaceDownDefence(0x8),
    FaceUp(0x5),
    FaceDown(0xA),
    Attack(0x3),
    Defence(0xC);

    private int value = 0;

    CardPosition(int value) {
        this.value = value;
    }

    public static CardPosition valueOf(int value) {
        CardPosition[] attributes = CardPosition.values();
        for (CardPosition attribute : attributes) {
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

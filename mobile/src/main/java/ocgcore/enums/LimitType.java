package ocgcore.enums;

public enum LimitType {
    None(0),
    All(999),
    Forbidden(1),
    Limit(2),
    SemiLimit(3);

    private long value = 0;

    private LimitType(long value) {
        this.value = value;
    }

    public static LimitType valueOf(long value) {
        LimitType[] attributes = LimitType.values();
        for (LimitType attribute : attributes) {
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

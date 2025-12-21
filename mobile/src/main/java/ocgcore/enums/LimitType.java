package ocgcore.enums;

import androidx.annotation.Nullable;

public enum LimitType {
    None(0),
    All(999, 1310),
    GeneSys(100, 1699),
    Forbidden(1, 1316),
    Limit(2, 1317),
    SemiLimit(3, 1318);

    private long value = 0;
    private final Integer lang_index;

    public Integer getLanguageIndex() {
        return lang_index;
    }

    LimitType(long value){
        this(value, 0);
    }

    LimitType(long value, Integer lang_index) {
        this.value = value;
        this.lang_index = lang_index;
    }

    public static @Nullable LimitType valueOf(long value) {
        LimitType[] attributes = LimitType.values();
        for (LimitType attribute : attributes) {
            if (attribute.getId() == value) {
                return attribute;
            }
        }
        return null;
    }

    public long getId() {
        return this.value;
    }
}

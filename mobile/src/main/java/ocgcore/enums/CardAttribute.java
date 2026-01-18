package ocgcore.enums;

import androidx.annotation.Nullable;

public enum CardAttribute {
    //None(0),
    Earth(0x01, 1010),
    Water(0x02, 1011),
    Fire(0x04, 1012),
    Wind(0x08, 1013),
    Light(0x10, 1014),
    Dark(0x20, 1015),
    Divine(0x40, 1016);

    private long value = 0;
    private final int lang_index;

    public int getLanguageIndex() {
        return lang_index;
    }

    CardAttribute(long value){
        this(value, 0);
    }

    CardAttribute(long value, int lang_index) {
        this.value = value;
        this.lang_index = lang_index;
    }

    public static @Nullable  CardAttribute valueOf(long value) {
        CardAttribute[] attributes = CardAttribute.values();
        for (CardAttribute attribute : attributes) {
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
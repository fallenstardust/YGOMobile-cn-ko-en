package ocgcore.enums;

import androidx.annotation.Nullable;

public enum CardType {
    None(0),
    Monster(0x1, 1050),
    Spell(0x2, 1051),
    Trap(0x4, 1052),
    UNK1(0x8, 1053),
    Normal(0x10, 1054),
    Effect(0x20, 1055),
    Fusion(0x40, 1056),
    Ritual(0x80, 1057),
    TrapMonster(0x100, 1058),//
    Spirit(0x200, 1059),
    Union(0x400, 1060),
    Gemini(0x800, 1061),//
    Tuner(0x1000, 1062),
    Synchro(0x2000, 1063),
    Token(0x4000, 1064),
    UNK2(0x8000, 1065),
    QuickPlay(0x10000, 1066),
    Continuous(0x20000, 1067),
    Equip(0x40000, 1068),
    Field(0x80000, 1069),
    Counter(0x100000, 1070),
    Flip(0x200000, 1071),
    Toon(0x400000, 1072),
    Xyz(0x800000, 1073),
    Pendulum(0x1000000, 1074),
    Sp_Summon(0x2000000, 1075),
    Link(0x4000000, 1076),
    Non_Effect(0x8000000, 1077);

    private long value = 0;
    private final int lang_index;

    public int getLanguageIndex() {
        return lang_index;
    }

    CardType(long value){
        this(value, 0);
    }

    CardType(long value, int lang_index) {
        this.value = value;
        this.lang_index = lang_index;
    }

    public static @Nullable CardType valueOf(long value) {
        CardType[] attributes = CardType.values();
        for (CardType attribute : attributes) {
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

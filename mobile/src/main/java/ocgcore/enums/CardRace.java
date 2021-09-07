package ocgcore.enums;

import androidx.annotation.Nullable;

public enum CardRace {
    None(0),
    Warrior(0x1, 1020),
    BestWarrior(0x8000, 1021),
    SpellCaster(0x2, 1022),
    Fairy(0x4, 1023),
    Fiend(0x8, 1024),
    Zombie(0x10, 1025),
    Machine(0x20, 1026),
    Aqua(0x40, 1027),
    Pyro(0x80, 1028),
    Rock(0x100, 1029),
    WindBeast(0x200, 1030),
    Plant(0x400, 1031),
    Insect(0x800, 1032),
    Thunder(0x1000, 1033),
    Dragon(0x2000, 1034),
    Beast(0x4000, 1035),
    Dinosaur(0x10000, 1036),
    Fish(0x20000, 1037),
    SeaSerpent(0x40000, 1038),
    Reptile(0x80000, 1039),
    Psycho(0x100000, 1040),
    DivineBeast(0x200000, 1041),
    Creatorgod(0x400000, 1042),
    Wyrm(0x800000, 1043),
    Cyberse(0x1000000, 1044);

    private long value = 0;
    private final int lang_index;

    public int getLanguageIndex() {
        return lang_index;
    }

    CardRace(long value){
        this(value, 0);
    }

    CardRace(long value, int lang_index) {
        this.value = value;
        this.lang_index = lang_index;
    }

    public static @Nullable CardRace valueOf(long value) {
        CardRace[] attributes = CardRace.values();
        for (CardRace attribute : attributes) {
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

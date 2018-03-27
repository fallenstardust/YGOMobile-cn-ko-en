package ocgcore.enums;

public enum CardRace {
    None(0),
    Warrior(0x1),
    BestWarrior(0x8000),
    SpellCaster(0x2),
    Fairy(0x4),
    Fiend(0x8),
    Zombie(0x10),
    Machine(0x20),
    Aqua(0x40),
    Pyro(0x80),
    Rock(0x100),
    WindBeast(0x200),
    Plant(0x400),
    Insect(0x800),
    Thunder(0x1000),
    Dragon(0x2000),
    Beast(0x4000),
    Dinosaur(0x10000),
    Fish(0x20000),
    SeaSerpent(0x40000),
    Reptile(0x80000),
    Psycho(0x100000),
    DivineBeast(0x200000),
    Creatorgod(0x400000),
    Wyrm(0x800000),
    Cyberse(0x1000000);

    private long value = 0;

    private CardRace(long value) {
        this.value = value;
    }

    public static CardRace valueOf(long value) {
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

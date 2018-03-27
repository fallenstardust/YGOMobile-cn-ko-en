package ocgcore.enums;

public enum DuelPhase {
    Draw(0x01),
    Standby(0x02),
    Main1(0x04),
    BattleStart(0x08),
    BattleStep(0x10),
    Damage(0x20),
    DamageCal(0x40),
    Battle(0x80),
    Main2(0x100),
    End(0x200);
    private int value = 0;

    private DuelPhase(int value) {
        this.value = value;
    }

    public static DuelPhase valueOf(int value) {
        DuelPhase[] attributes = DuelPhase.values();
        for (DuelPhase attribute : attributes) {
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

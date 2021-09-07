package ocgcore.enums;

public enum CardOt {
    ALL(0),
    OCG(1),
    TCG(2),
    NO_EXCLUSIVE(3),
    CUSTOM(4),
    SC_OCG(8),
    UNKNOWN(999);/*简中*/

    private final int value;

    private CardOt(int value) {
        this.value = value;
    }

    public int getId(){
        return value;
    }

    public static CardOt of(int value){
        for(CardOt cardOt: values()){
            if(cardOt.value == value){
                return cardOt;
            }
        }
        return UNKNOWN;
    }
}

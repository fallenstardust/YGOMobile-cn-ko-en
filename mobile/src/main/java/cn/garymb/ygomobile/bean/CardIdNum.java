package cn.garymb.ygomobile.bean;

/**
 * Create By feihua  On 2021/9/25
 */
public class CardIdNum {
    private String cardIdNum;
    private String CardIdNumCompressed;

    public CardIdNum(String cardIdNum, String cardIdNumCompressed) {
        this.cardIdNum = cardIdNum;
        CardIdNumCompressed = cardIdNumCompressed;
    }

    public String getCardIdNum() {
        return cardIdNum;
    }

    public void setCardIdNum(String cardIdNum) {
        this.cardIdNum = cardIdNum;
    }

    public String getCardIdNumCompressed() {
        return CardIdNumCompressed;
    }

    public void setCardIdNumCompressed(String cardIdNumCompressed) {
        CardIdNumCompressed = cardIdNumCompressed;
    }
}

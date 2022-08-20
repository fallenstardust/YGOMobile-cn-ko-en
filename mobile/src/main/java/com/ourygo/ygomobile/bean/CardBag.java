package com.ourygo.ygomobile.bean;

/**
 * 新卡包
 * Create By feihua  On 2022/8/20
 */
public class CardBag {
    //介绍标题
    private String title;
    //介绍内容
    private String message;
    //卡包卡组名
    private String deckName;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDeckName() {
        return deckName;
    }

    public void setDeckName(String deckName) {
        this.deckName = deckName;
    }
}

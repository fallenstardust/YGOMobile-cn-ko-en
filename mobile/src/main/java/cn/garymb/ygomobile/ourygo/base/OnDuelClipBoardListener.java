package cn.garymb.ygomobile.ourygo.base;

public interface OnDuelClipBoardListener {

    void onDeckCode(String deckCode,boolean isDebounce);
    void onDeckUrl(String deckUrl,boolean isDebounce);
    void onCardQuery(String cardSearchMessage,boolean isDebounce);
    void onDuelPassword(String password,boolean isDebounce);
    boolean isEffective();

}

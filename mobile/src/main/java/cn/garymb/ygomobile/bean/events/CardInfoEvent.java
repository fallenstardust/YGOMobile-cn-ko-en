package cn.garymb.ygomobile.bean.events;

public class CardInfoEvent {
    public int position;
    public boolean toMain;

    public CardInfoEvent() {
    }

    public CardInfoEvent(int position, boolean toMain) {
        this.position = position;
        this.toMain = toMain;
    }
}

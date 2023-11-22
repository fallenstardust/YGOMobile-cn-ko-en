package cn.garymb.ygomobile.bean.events;

/**
 * 用于EventBus的事件类型。
 */
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

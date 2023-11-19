package cn.garymb.ygomobile.bean.events;

/**
 * 用于EventBus的事件类型。
 */
public class ExCardEvent {
    public enum EventType {
        exCardPackageChange,//扩展卡包变化
        exCardPrefChange//扩展卡设置变化
    }

    private EventType eventType;

    public ExCardEvent(EventType eventType) {
        this.eventType = eventType;
    }

    public EventType getType() {
        return eventType;
    }

    public void setType(EventType eventType) {
        this.eventType = eventType;
    }

}

package cn.garymb.ygomobile.bean.events;

public class ExCardEvent {
    public enum EventType {
        exCardPackageChange,exCardPrefChange
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

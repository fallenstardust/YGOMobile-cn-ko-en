package cn.garymb.ygomobile.bean.events;

public class ServerInfoEvent {
    public int position;
    public boolean delete;
    public boolean join;

    public ServerInfoEvent() {
    }

    public ServerInfoEvent(int position, boolean delete) {
        this.position = position;
        this.delete = delete;
    }
}

package cn.garymb.ygomobile.bean.events;

import cn.garymb.ygomobile.bean.ServerInfo;
/**
 * 用于EventBus的事件类型。
 */
public class ServerInfoEvent {
    public int position;
    public ServerInfo serverInfo;//为了让接受event的HomeFragment能获取到当前server的端口号等信息，加入该属性
    public boolean delete;
    public boolean join;

    public ServerInfoEvent() {
    }

    public ServerInfoEvent(int position, boolean delete, ServerInfo serverInfo) {
        this.position = position;
        this.delete = delete;
        this.serverInfo = serverInfo;
    }
}

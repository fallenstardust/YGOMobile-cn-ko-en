package cn.garymb.ygomobile.bean;

public class DeckType extends TextSelect {
    public enum ServerType {
        LOCAL,
        SQUARE,
        MY_SQUARE,
    }

    private String name;
    private String path;
    private ServerType onServer;//true代表云端卡组，false代表本地卡组

    public DeckType(String name, String path) {
        this.name = name;
        this.path = path;
        onServer = ServerType.LOCAL;
        super.setName(name);
        setObject(this);
    }

    public DeckType(String name, String path, ServerType onServer) {
        this.name = name;
        this.path = path;
        this.onServer = onServer;
        setObject(this);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public ServerType getOnServer() {
        return onServer;
    }

    public void setOnServer(ServerType onServer) {
        this.onServer = onServer;
    }

    //true代表卡组位于本地
    public boolean isLocal() {
        return (this.onServer == ServerType.LOCAL);
    }
}

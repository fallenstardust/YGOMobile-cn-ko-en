package cn.garymb.ygomobile.bean;

public class DeckType extends TextSelect {
    private String name;
    private String path;

    public DeckType(String name, String path) {
        this.name = name;
        this.path = path;
        super.setName(name);
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

}

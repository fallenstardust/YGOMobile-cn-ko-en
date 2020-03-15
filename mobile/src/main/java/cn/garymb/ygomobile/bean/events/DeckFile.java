package cn.garymb.ygomobile.bean.events;

import java.io.File;

import cn.garymb.ygomobile.bean.TextSelect;

public class DeckFile extends TextSelect {

    private String name;
    private String path;
    private Long date;

    public DeckFile(String path) {
        this.path = path;
        name = new File(path).getName();
        int end = name.lastIndexOf(".");
        if (end != -1)
            name = name.substring(0, end);
        super.setName(name);
        setObject(this);
    }

    public DeckFile(File file) {
        path = file.getAbsolutePath();
        name = file.getName();
        name = name.substring(0, name.lastIndexOf("."));
        date = file.lastModified();
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

    public Long getDate() { return date; }

}

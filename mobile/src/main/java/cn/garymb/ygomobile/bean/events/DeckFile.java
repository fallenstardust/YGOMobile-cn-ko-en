package cn.garymb.ygomobile.bean.events;

import java.io.File;

import cn.garymb.ygomobile.bean.TextSelect;

public class DeckFile extends TextSelect {

    private final File path;
    private final String fullName;

    public DeckFile(String path) {
        this(new File(path));
    }

    public DeckFile(File file) {
        path = file;
        fullName = file.getName();
        String name = fullName;
        int index = name.lastIndexOf(".");
        if(index > 0) {
            name = name.substring(0, index);
        }
        super.setName(name);
        setObject(this);
    }

    public String getFileName() {
        return fullName;
    }

    public File getPathFile() {
        return path;
    }

    public String getPath() {
        return path.getAbsolutePath();
    }

    public Long getDate() {
        return path.lastModified();
    }

}

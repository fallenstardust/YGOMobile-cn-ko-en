package cn.garymb.ygomobile.bean.events;

import java.io.File;

import cn.garymb.ygomobile.bean.TextSelect;
import cn.garymb.ygomobile.utils.DeckUtil;

public class DeckFile extends TextSelect {

    private final File path;
    private final String fullName;
    //分类
    private String typeName;
    private int firstCode;

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
        typeName = DeckUtil.getDeckTypeName(path.getAbsolutePath());
        firstCode = DeckUtil.getFirstCardCode(path.getAbsolutePath());
        super.setName(name);
        setObject(this);
    }


    public int getFirstCode() {
        return firstCode;
    }

    public void setFirstCode(int firstCode) {
        this.firstCode = firstCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
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

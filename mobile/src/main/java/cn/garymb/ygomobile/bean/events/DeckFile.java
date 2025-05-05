package cn.garymb.ygomobile.bean.events;

import java.io.File;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.TextSelect;
import cn.garymb.ygomobile.utils.DeckUtil;

public class DeckFile extends TextSelect {
    private File path;
    private String fileFullName;//本地文件对应的名称，仅在onServer为false时有效
    //    private final File path;
//    private final String fullName;
    //分类
    private String typeName;//可取值包括cacheDeck，Pack
    private int firstCode;
    private DeckType.ServerType onServer;//true代表云端卡组，false代表本地卡组
    private String deckId;//如果onServer为true，代表该卡组存储在云上，deckId是唯一id

//    public DeckFile(boolean onServer) {
//        this.onServer = onServer;
//    }

    public DeckFile(String name, String typeName, DeckType.ServerType onServer, String deckId) {
        this.typeName = typeName;
        this.onServer = onServer;
        this.deckId = deckId;
        this.setName(name);
        this.fileFullName = null;
        this.path = null;
        this.firstCode = -1;
        setObject(this);
    }

    public DeckFile(String path) {
        this(new File(path));
    }

    public DeckFile(File file) {
        path = file;
        fileFullName = file.getName();
        String name = fileFullName;
        int index = name.lastIndexOf(".");
        if (index > 0) {
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
        return fileFullName;
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


    public DeckType.ServerType getOnServer() {
        return onServer;
    }

    public void setOnServer(DeckType.ServerType onServer) {
        this.onServer = onServer;
    }

    //true代表卡组位于本地
    public boolean isLocal() {
        return (this.onServer == DeckType.ServerType.LOCAL);
    }


    public String getDeckId() {
        return deckId;
    }

    public void setDeckId(String deckId) {
        this.deckId = deckId;
    }
}

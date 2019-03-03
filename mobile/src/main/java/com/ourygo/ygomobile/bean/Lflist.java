package com.ourygo.ygomobile.bean;

public class Lflist {

    public static final int TYPE_OCG = 0;
    public static final int TYPE_TCG = 1;

    private String name;
    private String message;
    private int type;
    private String typeName;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
        switch (type) {
            case TYPE_OCG:
                typeName="OCG";
                break;
            case TYPE_TCG:
                typeName="TCG";
                break;
        }
    }


    public String getTypeName() {
        return typeName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static Lflist toLflist(String name, String message,int type) {
        Lflist lflist = new Lflist();
        lflist.setName(name);
        lflist.setMessage(message);
        lflist.setType(type);
        return lflist;
    }

}

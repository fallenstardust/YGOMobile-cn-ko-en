package com.ourygo.ygomobile.bean;

/**
 * Create By feihua  On 2022/1/24
 */
public class VerTab {
    public static final int ICON_NULL=-1;

    private int icon;
    private String name;

    public VerTab(){
        this(null,ICON_NULL);
    }

    public VerTab(String name,int icon) {
        this.icon = icon;
        this.name = name;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

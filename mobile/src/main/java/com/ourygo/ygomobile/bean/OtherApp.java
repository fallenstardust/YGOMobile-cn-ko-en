package com.ourygo.ygomobile.bean;

/**
 * Create By feihua  On 2021/11/9
 */
public class OtherApp {

    //app名字
    private String name;
    //app版本号
    private String versionName;
    //图标
    private int icon;
    //app地址
    private String appUrl;
    //app说明
    private String message;

    private int groupId;


    public OtherApp() {
        this.groupId=0;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public void setAppUrl(String appUrl) {
        this.appUrl = appUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

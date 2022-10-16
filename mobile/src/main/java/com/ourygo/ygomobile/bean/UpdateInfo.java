package com.ourygo.ygomobile.bean;

import java.io.Serializable;

/**
 * Create By feihua  On 2022/10/15
 */
public class UpdateInfo implements Serializable {
    //版本名称
    private String versionName;
    //版本号
    private int versionCode;
    //标题
    private String title;
    //更新内容
    private String message;
    //安装包大小
    private long size;
    //下载链接
    private String url;
    //下载链接验证码
    private String code;

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

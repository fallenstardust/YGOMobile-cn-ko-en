package com.ourygo.ygomobile.bean;

import com.google.gson.annotations.SerializedName;
import com.ourygo.ygomobile.util.Record;
import com.stx.xhb.androidx.entity.BaseBannerInfo;

import java.io.Serializable;

import org.litepal.crud.LitePalSupport;

public class McNews extends LitePalSupport implements BaseBannerInfo, Serializable {

    @SerializedName("id")
    private String newsId;
    private String title;
    private String message;
    private String news_url;
    private String image_url;
    private int type;
    private String create_time;

    public String getNewId() {
        return newsId;
    }

    public void setNewId(String id) {
        this.newsId = id;
        setNews_url(Record.getMycardPostUrl(id));
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCreate_time() {
        return create_time;
    }

    public void setCreate_time(String create_time) {
        this.create_time = create_time;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNews_url() {
        return news_url;
    }

    public void setNews_url(String news_url) {
        this.news_url = news_url;
    }

    public String getImage_url() {
        return image_url;
    }

    public void setImage_url(String image_url) {
        this.image_url = image_url;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Override
    public Object getXBannerUrl() {
        return null;
    }

    @Override
    public String getXBannerTitle() {
        return title;
    }
}

package com.ourygo.ygomobile.bean;

/**
 * Create By feihua  On 2021/10/23
 */
public class ImageSelectItem {
    private int id;
    private String name;
    private int image;

    public ImageSelectItem(int id, String name, int image) {
        this.id = id;
        this.name = name;
        this.image = image;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getImage() {
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }
}

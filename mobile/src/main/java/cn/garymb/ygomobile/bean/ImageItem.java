package cn.garymb.ygomobile.bean;

public class ImageItem {
    private String imagePath;
    private boolean isSpecialItem;

    public ImageItem(String imagePath, boolean isSpecialItem) {
        this.imagePath = imagePath;
        this.isSpecialItem = isSpecialItem;
    }

    public String getImagePath() {
        return imagePath;
    }

    public boolean isSpecialItem() {
        return isSpecialItem;
    }
}

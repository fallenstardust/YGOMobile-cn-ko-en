package cn.garymb.ygomobile.interfaces;

public class GameSize {
    private int width;
    private int height;
    private int touchX;
    private int touchY;

    private int fullW;
    private int fullH;
    private int actW;
    private int actH;

    public void update(GameSize size) {
        synchronized (this) {
            this.width = size.width;
            this.height = size.height;
            this.touchX = size.touchX;
            this.touchY = size.touchY;
        }
    }

    public void setTouch(int touchX, int touchY) {
        synchronized (this) {
            this.touchX = touchX;
            this.touchY = touchY;
        }
    }

    public int getWidth() {
        synchronized (this) {
            return width;
        }
    }

    public int getHeight() {
        synchronized (this) {
            return height;
        }
    }

    public int getTouchX() {
        synchronized (this) {
            return touchX;
        }
    }

    public int getTouchY() {
        synchronized (this) {
            return touchY;
        }
    }

    public int getFullW() {
        return fullW;
    }

    public int getFullH() {
        return fullH;
    }

    public int getActW() {
        return actW;
    }

    public int getActH() {
        return actH;
    }

    public void setScreen(int fullW, int fullH, int actW, int actH) {
        this.fullW = fullW;
        this.fullH = fullH;
        this.actW = actW;
        this.actH = actH;
    }

    public GameSize() {

    }

    public GameSize(int width, int height, int touchX, int touchY) {
        this.width = width;
        this.height = height;
        this.touchX = touchX < 0 ? 0 : touchX;
        this.touchY = touchY < 0 ? 0 : touchY;
    }

    @Override
    public String toString() {
        return "GameSize{" +
                "width=" + width +
                ", height=" + height +
                ", touchX=" + touchX +
                ", touchY=" + touchY +
                ", fullW=" + fullW +
                ", fullH=" + fullH +
                ", actW=" + actW +
                ", actH=" + actH +
                '}';
    }
}

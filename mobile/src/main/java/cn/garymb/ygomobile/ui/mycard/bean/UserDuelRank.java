package cn.garymb.ygomobile.ui.mycard.bean;

import com.google.gson.annotations.SerializedName;

public class UserDuelRank {

    @SerializedName("username")
    private String username;

    @SerializedName("exp")
    private double exp;

    @SerializedName("pt")
    private double pt;

    @SerializedName("entertain_win")
    private int entertainWin;

    @SerializedName("entertain_lose")
    private int entertainLose;

    @SerializedName("entertain_draw")
    private int entertainDraw;

    @SerializedName("entertain_all")
    private int entertainAll;

    @SerializedName("athletic_win")
    private int athleticWin;

    @SerializedName("athletic_lose")
    private int athleticLose;

    @SerializedName("athletic_draw")
    private int athleticDraw;

    @SerializedName("athletic_all")
    private int athleticAll;

    @SerializedName("id")
    private Integer id;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public double getExp() {
        return exp;
    }

    public void setExp(double exp) {
        this.exp = exp;
    }

    public double getPt() {
        return pt;
    }

    public void setPt(double pt) {
        this.pt = pt;
    }

    public int getEntertainWin() {
        return entertainWin;
    }

    public void setEntertainWin(int entertainWin) {
        this.entertainWin = entertainWin;
    }

    public int getEntertainLose() {
        return entertainLose;
    }

    public void setEntertainLose(int entertainLose) {
        this.entertainLose = entertainLose;
    }

    public int getEntertainDraw() {
        return entertainDraw;
    }

    public void setEntertainDraw(int entertainDraw) {
        this.entertainDraw = entertainDraw;
    }

    public int getEntertainAll() {
        return entertainAll;
    }

    public void setEntertainAll(int entertainAll) {
        this.entertainAll = entertainAll;
    }

    public int getAthleticWin() {
        return athleticWin;
    }

    public void setAthleticWin(int athleticWin) {
        this.athleticWin = athleticWin;
    }

    public int getAthleticLose() {
        return athleticLose;
    }

    public void setAthleticLose(int athleticLose) {
        this.athleticLose = athleticLose;
    }

    public int getAthleticDraw() {
        return athleticDraw;
    }

    public void setAthleticDraw(int athleticDraw) {
        this.athleticDraw = athleticDraw;
    }

    public int getAthleticAll() {
        return athleticAll;
    }

    public void setAthleticAll(int athleticAll) {
        this.athleticAll = athleticAll;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public float getAthleticWinRate() {
        if (athleticAll == 0) return 0;
        return (float) athleticWin / athleticAll * 100;
    }

    public float getEntertainWinRate() {
        if (entertainAll == 0) return 0;
        return (float) entertainWin / entertainAll * 100;
    }

    @Override
    public String toString() {
        return "UserDuelRank{" +
                "username='" + username + '\'' +
                ", pt=" + pt +
                ", athleticAll=" + athleticAll +
                ", athleticWin=" + athleticWin +
                '}';
    }
}

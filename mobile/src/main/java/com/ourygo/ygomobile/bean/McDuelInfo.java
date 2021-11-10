package com.ourygo.ygomobile.bean;

import java.io.Serializable;

/**
 * Create By feihua  On 2021/10/20
 */

public class McDuelInfo implements Serializable {
    private Integer exp;
    @com.google.gson.annotations.SerializedName("pt")
    private Integer dp;
    @com.google.gson.annotations.SerializedName("entertain_win")
    private Integer funWin;
    @com.google.gson.annotations.SerializedName("entertain_lose")
    private Integer funLose;
    @com.google.gson.annotations.SerializedName("entertain_draw")
    private Integer funDraw;
    @com.google.gson.annotations.SerializedName("entertain_all")
    private Integer funAll;
    @com.google.gson.annotations.SerializedName("entertain_wl_ratio")
    private float funWinRratio;
    @com.google.gson.annotations.SerializedName("exp_rank")
    private Integer funRank;
    @com.google.gson.annotations.SerializedName("athletic_win")
    private Integer matchWin;
    @com.google.gson.annotations.SerializedName("athletic_lose")
    private Integer matchLose;
    @com.google.gson.annotations.SerializedName("athletic_draw")
    private Integer matchDraw;
    @com.google.gson.annotations.SerializedName("athletic_all")
    private Integer matchAll;
    @com.google.gson.annotations.SerializedName("athletic_wl_ratio")
    private float matchWinRatio;
    @com.google.gson.annotations.SerializedName("arena_rank")
    private Integer matchRank;

    public Integer getExp() {
        return exp;
    }

    public void setExp(Integer exp) {
        this.exp = exp;
    }

    public Integer getDp() {
        return dp;
    }

    public void setDp(Integer dp) {
        this.dp = dp;
    }

    public Integer getFunWin() {
        return funWin;
    }

    public void setFunWin(Integer funWin) {
        this.funWin = funWin;
    }

    public Integer getFunLose() {
        return funLose;
    }

    public void setFunLose(Integer funLose) {
        this.funLose = funLose;
    }

    public Integer getFunDraw() {
        return funDraw;
    }

    public void setFunDraw(Integer funDraw) {
        this.funDraw = funDraw;
    }

    public Integer getFunAll() {
        return funAll;
    }

    public void setFunAll(Integer funAll) {
        this.funAll = funAll;
    }

    public float getFunWinRratio() {
        return funWinRratio;
    }

    public void setFunWinRratio(float funWinRratio) {
        this.funWinRratio = funWinRratio;
    }

    public Integer getFunRank() {
        return funRank;
    }

    public void setFunRank(Integer funRank) {
        this.funRank = funRank;
    }

    public Integer getMatchWin() {
        return matchWin;
    }

    public void setMatchWin(Integer matchWin) {
        this.matchWin = matchWin;
    }

    public Integer getMatchLose() {
        return matchLose;
    }

    public void setMatchLose(Integer matchLose) {
        this.matchLose = matchLose;
    }

    public Integer getMatchDraw() {
        return matchDraw;
    }

    public void setMatchDraw(Integer matchDraw) {
        this.matchDraw = matchDraw;
    }

    public Integer getMatchAll() {
        return matchAll;
    }

    public void setMatchAll(Integer matchAll) {
        this.matchAll = matchAll;
    }

    public float getMatchWinRatio() {
        return matchWinRatio;
    }

    public void setMatchWinRatio(float matchWinRatio) {
        this.matchWinRatio = matchWinRatio;
    }

    public Integer getMatchRank() {
        return matchRank;
    }

    public void setMatchRank(Integer matchRank) {
        this.matchRank = matchRank;
    }
}

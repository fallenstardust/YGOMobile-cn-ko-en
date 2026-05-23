package cn.garymb.ygomobile.ui.mycard.bean;

import com.google.gson.annotations.SerializedName;

public class McDuelResult {
    @SerializedName("end_time")
    private String endTime;
    private String type;
    private String usernamea;
    private String usernameb;
    private String winner;
    private int userscorea;
    private int userscoreb;
    private int pta;
    private int ptb;
    @SerializedName("pta_ex")
    private int ptaEx;
    @SerializedName("ptb_ex")
    private int ptbEx;
    private int expa;
    private int expb;
    @SerializedName("expa_ex")
    private int expaEx;
    @SerializedName("expb_ex")
    private int expbEx;
    private boolean isfirstwin;

    public String getEndTime() {
        return endTime;
    }

    public String getType() {
        return type;
    }

    public String getUsernamea() {
        return usernamea;
    }

    public String getUsernameb() {
        return usernameb;
    }

    public String getWinner() {
        return winner;
    }

    public int getUserscorea() {
        return userscorea;
    }

    public int getUserscoreb() {
        return userscoreb;
    }

    public int getPtFor(String username) {
        return username != null && username.equals(usernamea) ? pta : ptb;
    }

    public int getPtExFor(String username) {
        return username != null && username.equals(usernamea) ? ptaEx : ptbEx;
    }

    public int getExpFor(String username) {
        return username != null && username.equals(usernamea) ? expa : expb;
    }

    public int getExpExFor(String username) {
        return username != null && username.equals(usernamea) ? expaEx : expbEx;
    }

    public boolean isFirstWin() {
        return isfirstwin;
    }

    public String getOpponent(String username) {
        if (username == null) {
            return "";
        }
        return username.equals(usernamea) ? usernameb : usernamea;
    }

    public boolean isDraw() {
        return winner == null || winner.length() == 0;
    }

    public boolean isWin(String username) {
        return username != null && username.equals(winner);
    }
}

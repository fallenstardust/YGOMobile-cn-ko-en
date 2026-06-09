package cn.garymb.ygomobile.ui.mycard.bean;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class McHistoryResponse implements Serializable {
    private Integer total;
    private List<HistoryItem> data;

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public List<HistoryItem> getData() {
        return data;
    }

    public void setData(List<HistoryItem> data) {
        this.data = data;
    }

    public static class HistoryItem implements Serializable {
        private String usernamea;
        private String usernameb;

        @SerializedName("userscorea")
        private Integer userscorea;

        @SerializedName("userscoreb")
        private Integer userscoreb;

        private Double expa;
        private Double expb;

        @SerializedName("expa_ex")
        private Double expaEx;

        @SerializedName("expb_ex")
        private Double expbEx;

        private Double pta;
        private Double ptb;

        @SerializedName("pta_ex")
        private Double ptaEx;

        @SerializedName("ptb_ex")
        private Double ptbEx;

        private String type;

        @SerializedName("start_time")
        private String startTime;

        @SerializedName("end_time")
        private String endTime;

        private String winner;

        @SerializedName("isfirstwin")
        private Boolean isfirstwin;

        private String decka;
        private String deckb;

        public String getUsernamea() {
            return usernamea;
        }

        public void setUsernamea(String usernamea) {
            this.usernamea = usernamea;
        }

        public String getUsernameb() {
            return usernameb;
        }

        public void setUsernameb(String usernameb) {
            this.usernameb = usernameb;
        }

        public Integer getUserscorea() {
            return userscorea;
        }

        public void setUserscorea(Integer userscorea) {
            this.userscorea = userscorea;
        }

        public Integer getUserscoreb() {
            return userscoreb;
        }

        public void setUserscoreb(Integer userscoreb) {
            this.userscoreb = userscoreb;
        }

        public Double getExpa() {
            return expa;
        }

        public void setExpa(Double expa) {
            this.expa = expa;
        }

        public Double getExpb() {
            return expb;
        }

        public void setExpb(Double expb) {
            this.expb = expb;
        }

        public Double getExpaEx() {
            return expaEx;
        }

        public void setExpaEx(Double expaEx) {
            this.expaEx = expaEx;
        }

        public Double getExpbEx() {
            return expbEx;
        }

        public void setExpbEx(Double expbEx) {
            this.expbEx = expbEx;
        }

        public Double getPta() {
            return pta;
        }

        public void setPta(Double pta) {
            this.pta = pta;
        }

        public Double getPtEx() {
            return ptaEx;
        }

        public void setPtEx(Double ptaEx) {
            this.ptaEx = ptaEx;
        }

        public Double getPtb() {
            return ptb;
        }

        public void setPtb(Double ptb) {
            this.ptb = ptb;
        }

        public Double getPtbEx() {
            return ptbEx;
        }

        public void setPtbEx(Double ptbEx) {
            this.ptbEx = ptbEx;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public String getWinner() {
            return winner;
        }

        public void setWinner(String winner) {
            this.winner = winner;
        }

        public Boolean getIsfirstwin() {
            return isfirstwin;
        }

        public void setIsfirstwin(Boolean isfirstwin) {
            this.isfirstwin = isfirstwin;
        }

        public String getDecka() {
            return decka;
        }

        public void setDecka(String decka) {
            this.decka = decka;
        }

        public String getDeckb() {
            return deckb;
        }

        public void setDeckb(String deckb) {
            this.deckb = deckb;
        }
    }
}

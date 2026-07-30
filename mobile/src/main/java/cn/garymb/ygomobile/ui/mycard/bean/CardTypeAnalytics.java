package cn.garymb.ygomobile.ui.mycard.bean;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CardTypeAnalytics {

    private List<CardItem> monster;
    private List<CardItem> spell;
    private List<CardItem> trap;
    private List<CardItem> ex;
    private List<CardItem> side;

    public List<CardItem> getMonster() {
        return monster;
    }

    public void setMonster(List<CardItem> monster) {
        this.monster = monster;
    }

    public List<CardItem> getSpell() {
        return spell;
    }

    public void setSpell(List<CardItem> spell) {
        this.spell = spell;
    }

    public List<CardItem> getTrap() {
        return trap;
    }

    public void setTrap(List<CardItem> trap) {
        this.trap = trap;
    }

    public List<CardItem> getEx() {
        return ex;
    }

    public void setEx(List<CardItem> ex) {
        this.ex = ex;
    }

    public List<CardItem> getSide() {
        return side;
    }

    public void setSide(List<CardItem> side) {
        this.side = side;
    }

    public static class CardItem {
        private int id;
        private String recent_time;
        private String category;
        private String source;
        private String frequency;
        private String numbers;
        private String putone;
        private String puttwo;
        private String putthree;
        private String putoverthree;
        private CardName name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getRecent_time() {
            return recent_time;
        }

        public void setRecent_time(String recent_time) {
            this.recent_time = recent_time;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getFrequency() {
            return frequency;
        }

        public void setFrequency(String frequency) {
            this.frequency = frequency;
        }

        public String getNumbers() {
            return numbers;
        }

        public void setNumbers(String numbers) {
            this.numbers = numbers;
        }

        public String getPutone() {
            return putone;
        }

        public void setPutone(String putone) {
            this.putone = putone;
        }

        public String getPuttwo() {
            return puttwo;
        }

        public void setPuttwo(String puttwo) {
            this.puttwo = puttwo;
        }

        public String getPutthree() {
            return putthree;
        }

        public void setPutthree(String putthree) {
            this.putthree = putthree;
        }

        public String getPutoverthree() {
            return putoverthree;
        }

        public void setPutoverthree(String putoverthree) {
            this.putoverthree = putoverthree;
        }

        public CardName getName() {
            return name;
        }

        public void setName(CardName name) {
            this.name = name;
        }

        public String getCardName(String locale) {
            if (name == null) {
                return "Unknown";
            }
            switch (locale) {
                case "zh-CN":
                    return name.getZh_CN() != null ? name.getZh_CN() : name.getEn_US();
                case "ja-JP":
                    return name.getJa_JP() != null ? name.getJa_JP() : name.getEn_US();
                default:
                    return name.getEn_US() != null ? name.getEn_US() : name.getZh_CN();
            }
        }
    }

    public static class CardName {
        @SerializedName("zh-CN")
        private String zh_CN;
        @SerializedName("en-US")
        private String en_US;
        @SerializedName("ja-JP")
        private String ja_JP;

        public String getZh_CN() {
            return zh_CN;
        }

        public void setZh_CN(String zh_CN) {
            this.zh_CN = zh_CN;
        }

        public String getEn_US() {
            return en_US;
        }

        public void setEn_US(String en_US) {
            this.en_US = en_US;
        }

        public String getJa_JP() {
            return ja_JP;
        }

        public void setJa_JP(String ja_JP) {
            this.ja_JP = ja_JP;
        }
    }
}
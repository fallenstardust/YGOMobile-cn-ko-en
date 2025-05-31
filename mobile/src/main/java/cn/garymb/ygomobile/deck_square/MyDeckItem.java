package cn.garymb.ygomobile.deck_square;

public class MyDeckItem {
    //0代表未推到服务器，3代表包含deckId，1代表服务器存在可下载到本地，2代表已同步
    public int idUploaded;

    public int userId;
    public String deckName;

    public String deckId;

    public String updateDate;

    public int deckSouce;//卡组来源，0代表来自本地，1代表来自服务器

    public String deckPath;//本地卡组时，存储卡组路径

    public int deckCoverCard1;

    public int getIdUploaded() {
        return idUploaded;
    }

    public void setIdUploaded(int idUploaded) {
        this.idUploaded = idUploaded;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getDeckName() {
        return deckName;
    }

    public void setDeckName(String deckName) {
        this.deckName = deckName;
    }

    public String getDeckId() {
        return deckId;
    }

    public void setDeckId(String deckId) {
        this.deckId = deckId;
    }

    public String getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(String updateDate) {
        this.updateDate = updateDate;
    }

    public int getDeckSouce() {
        return deckSouce;
    }

    public void setDeckSouce(int deckSouce) {
        this.deckSouce = deckSouce;
    }

    public int getDeckCoverCard1() {
        return deckCoverCard1;
    }

    public void setDeckCoverCard1(int deckCoverCard1) {
        this.deckCoverCard1 = deckCoverCard1;
    }


    public String getDeckPath() {
        return deckPath;
    }

    public void setDeckPath(String deckPath) {
        this.deckPath = deckPath;
    }
}

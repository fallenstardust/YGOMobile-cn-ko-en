package cn.garymb.ygomobile.ui.cards.deck_square.bo;

public class MyDeckItem {
    //0代表未推到服务器，3代表包含deckId，1代表服务器存在可下载到本地，2代表已同步
    private int idUploaded;

    private int userId;

    private String deckName;

    private String deckType;

    private String deckId;

    private long updateTimestamp;

    private String deckPath;//本地卡组时，存储卡组路径

    private int deckCoverCard1;

    private Boolean isPublic;

    private Boolean isDelete;

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

    public String getDeckType() {
        return deckType;
    }

    public void setDeckType(String deckType) {
        this.deckType = deckType;
    }

    public String getDeckId() {
        return deckId;
    }

    public void setDeckId(String deckId) {
        this.deckId = deckId;
    }

    public long getUpdateTimestamp() {
        return updateTimestamp;
    }

    public void setUpdateTimestamp(long updateTimestamp) {
        this.updateTimestamp = updateTimestamp;
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

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
    }

    public Boolean isDelete() {
        return isDelete;
    }

    public void setDelete(Boolean aDelete) {
        isDelete = aDelete;
    }

    @Override
    public String toString() {
        return "MyDeckItem{" +
                "idUploaded=" + idUploaded +
                ", userId=" + userId +
                ", deckName='" + deckName + '\'' +
                ", deckType='" + deckType + '\'' +
                ", deckId='" + deckId + '\'' +
                ", updateTimestamp=" + updateTimestamp +
                ", deckPath='" + deckPath + '\'' +
                ", deckCoverCard1=" + deckCoverCard1 +
                ", isPublic=" + isPublic +
                '}';
    }
}

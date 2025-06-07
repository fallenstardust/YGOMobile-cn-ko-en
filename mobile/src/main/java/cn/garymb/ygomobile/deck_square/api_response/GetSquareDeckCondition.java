package cn.garymb.ygomobile.deck_square.api_response;

public class GetSquareDeckCondition {
    Integer page;
    Integer size;
    String keyWord;
    Boolean sortLike;
    Boolean sortRank;
    String contributer;

    public GetSquareDeckCondition() {
    }

    public GetSquareDeckCondition(Integer page, Integer size, String keyWord, Boolean sortLike, Boolean sortRank, String contributer) {
        this.page = page;
        this.size = size;
        this.keyWord = keyWord;
        this.sortLike = sortLike;
        this.sortRank = sortRank;
        this.contributer = contributer;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getKeyWord() {
        return keyWord;
    }

    public void setKeyWord(String keyWord) {
        this.keyWord = keyWord;
    }

    public Boolean getSortLike() {
        return sortLike;
    }

    public void setSortLike(Boolean sortLike) {
        this.sortLike = sortLike;
    }

    public Boolean getSortRank() {
        return sortRank;
    }

    public void setSortRank(Boolean sortRank) {
        this.sortRank = sortRank;
    }

    public String getContributer() {
        return contributer;
    }

    public void setContributer(String contributer) {
        this.contributer = contributer;
    }
}

package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

public class GetSquareDeckCondition {
    Integer page;
    Integer size;
    String keyWord;
    Boolean sortLike;
    Boolean sortRank;
    String contributor;

    public GetSquareDeckCondition() {
    }

    public GetSquareDeckCondition(Integer page, Integer size, String keyWord, Boolean sortLike, Boolean sortRank, String contributor) {
        this.page = page;
        this.size = size;
        this.keyWord = keyWord;
        this.sortLike = sortLike;
        this.sortRank = sortRank;
        this.contributor = contributor;
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

    public String getContributor() {
        return contributor;
    }

    public void setContributor(String contributor) {
        this.contributor = contributor;
    }
}

package cn.garymb.ygomobile.deck_square.api_response;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.deck_square.api_response.PushCardJson.DeckData;

/*卡组同步请求类*/
public class SyncDeckReq {
    private String deckContributor;
    private Integer userId;
    private List<DeckData> deckDataList;

    public String getDeckContributor() {
        return deckContributor;
    }

    public void setDeckContributor(String deckContributor) {
        this.deckContributor = deckContributor;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public List<DeckData> getDeckDataList() {
        return deckDataList;
    }

    public void setDeckDataList(List<DeckData> _deckDataList) {
        this.deckDataList = _deckDataList;
    }
}

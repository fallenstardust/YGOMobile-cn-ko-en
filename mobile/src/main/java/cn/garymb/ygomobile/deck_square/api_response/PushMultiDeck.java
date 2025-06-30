package cn.garymb.ygomobile.deck_square.api_response;

import java.util.List;

import cn.garymb.ygomobile.deck_square.api_response.PushSingleDeck.DeckData;

/*卡组同步请求类*/
public class PushMultiDeck {
    private String deckContributor;
    private Integer userId;
    private List<DeckData> decks;

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

    public List<DeckData> getDecks() {
        return decks;
    }

    public void setDecks(List<DeckData> _deckDataList) {
        this.decks = _deckDataList;
    }
}

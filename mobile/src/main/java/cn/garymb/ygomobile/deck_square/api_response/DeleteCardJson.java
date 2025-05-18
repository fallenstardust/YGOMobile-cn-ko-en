package cn.garymb.ygomobile.deck_square.api_response;

public class DeleteCardJson {

    private String deckContributor;
    private Integer userId;
    private DeckData deck;

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

    public DeckData getDeck() {
        return deck;
    }

    public void setDeck(DeckData deck) {
        this.deck = deck;
    }

    public static class DeckData {

        private boolean isDelete = false;


        public boolean isDelete() {
            return isDelete;
        }

        public void setDelete(boolean delete) {
            isDelete = delete;
        }
    }
}

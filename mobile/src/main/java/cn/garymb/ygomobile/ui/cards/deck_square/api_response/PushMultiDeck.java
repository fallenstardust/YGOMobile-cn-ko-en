package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

import java.util.List;

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


    public void setDecks(List<DeckData> decks_) {
        this.decks = decks_;
    }




    public static class DeckData {
        private String deckId;
        private String deckName;
        private String deckType;
        private Integer deckCoverCard1 = 0;
        private Integer deckCoverCard2 = 0;
        private Integer deckCoverCard3 = 0;
        private Integer deckCase = 0;
        private Integer deckProtector = 0;
        private long timestamp = 0;
        private String deckYdk;
        private boolean isDelete = false;

        public String getDeckId() {
            return deckId;
        }

        public void setDeckId(String deckId) {
            this.deckId = deckId;
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

        public Integer getDeckCoverCard1() {
            return deckCoverCard1;
        }

        public void setDeckCoverCard1(Integer deckCoverCard1) {
            this.deckCoverCard1 = deckCoverCard1;
        }

        public Integer getDeckCoverCard2() {
            return deckCoverCard2;
        }

        public void setDeckCoverCard2(Integer deckCoverCard2) {
            this.deckCoverCard2 = deckCoverCard2;
        }

        public Integer getDeckCoverCard3() {
            return deckCoverCard3;
        }

        public void setDeckCoverCard3(Integer deckCoverCard3) {
            this.deckCoverCard3 = deckCoverCard3;
        }

        public Integer getDeckCase() {
            return deckCase;
        }

        public void setDeckCase(Integer deckCase) {
            this.deckCase = deckCase;
        }

        public Integer getDeckProtector() {
            return deckProtector;
        }

        public void setDeckProtector(Integer deckProtector) {
            this.deckProtector = deckProtector;
        }

        public long getDeckUpdateTime() {
            return timestamp;
        }

        public void setDeckUpdateTime(long deckUpdateTime) {
            this.timestamp = deckUpdateTime;
        }

        public String getDeckYdk() {
            return deckYdk;
        }

        public void setDeckYdk(String deckYdk) {
            this.deckYdk = deckYdk;
        }

        public boolean isDelete() {return isDelete;}

        public void setDelete(boolean isDelete) {this.isDelete = isDelete;}

    }
}

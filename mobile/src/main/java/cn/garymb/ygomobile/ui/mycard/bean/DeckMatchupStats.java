package cn.garymb.ygomobile.ui.mycard.bean;

import java.util.HashMap;
import java.util.Map;

public class DeckMatchupStats {
    private String deckName;
    private Map<String, MatchStats> firstGoMatchupMap;
    private Map<String, MatchStats> secondGoMatchupMap;
    private Map<String, MatchStats> totalMatchupMap;
    private MatchStats aggregatedFirstGoStats;
    private MatchStats aggregatedSecondGoStats;
    private MatchStats aggregatedTotalStats;

    public DeckMatchupStats(String deckName) {
        this.deckName = deckName;
        this.firstGoMatchupMap = new HashMap<>();
        this.secondGoMatchupMap = new HashMap<>();
        this.totalMatchupMap = new HashMap<>();
        this.aggregatedFirstGoStats = new MatchStats();
        this.aggregatedSecondGoStats = new MatchStats();
        this.aggregatedTotalStats = new MatchStats();
    }

    public void addFirstGoMatchup(String opponentDeck, int win, int draw, int lose) {
        MatchStats stats = firstGoMatchupMap.computeIfAbsent(opponentDeck, k -> new MatchStats());
        stats.addMatch(win, draw, lose);
        aggregatedFirstGoStats.addMatch(win, draw, lose);
        updateTotalMatchup(opponentDeck);
    }

    public void addSecondGoMatchup(String opponentDeck, int win, int draw, int lose) {
        MatchStats stats = secondGoMatchupMap.computeIfAbsent(opponentDeck, k -> new MatchStats());
        stats.addMatch(win, draw, lose);
        aggregatedSecondGoStats.addMatch(win, draw, lose);
        updateTotalMatchup(opponentDeck);
    }

    private void updateTotalMatchup(String opponentDeck) {
        MatchStats firstGo = firstGoMatchupMap.get(opponentDeck);
        MatchStats secondGo = secondGoMatchupMap.get(opponentDeck);

        if (firstGo != null || secondGo != null) {
            MatchStats total = totalMatchupMap.computeIfAbsent(opponentDeck, k -> new MatchStats());
            total.win = (firstGo != null ? firstGo.win : 0) + (secondGo != null ? secondGo.win : 0);
            total.draw = (firstGo != null ? firstGo.draw : 0) + (secondGo != null ? secondGo.draw : 0);
            total.lose = (firstGo != null ? firstGo.lose : 0) + (secondGo != null ? secondGo.lose : 0);
        }

        aggregatedTotalStats.win = aggregatedFirstGoStats.win + aggregatedSecondGoStats.win;
        aggregatedTotalStats.draw = aggregatedFirstGoStats.draw + aggregatedSecondGoStats.draw;
        aggregatedTotalStats.lose = aggregatedFirstGoStats.lose + aggregatedSecondGoStats.lose;
    }

    public MatchStats getFirstGoMatchup(String opponentDeck) {
        return firstGoMatchupMap.get(opponentDeck);
    }

    public MatchStats getSecondGoMatchup(String opponentDeck) {
        return secondGoMatchupMap.get(opponentDeck);
    }

    public MatchStats getTotalMatchup(String opponentDeck) {
        return totalMatchupMap.get(opponentDeck);
    }

    public MatchStats getFirstGoStats() {
        return aggregatedFirstGoStats;
    }

    public MatchStats getSecondGoStats() {
        return aggregatedSecondGoStats;
    }

    public MatchStats getTotalStats() {
        return aggregatedTotalStats;
    }

    public String getDeckName() {
        return deckName;
    }

    public int getTotalMatches() {
        return aggregatedTotalStats.getTotalMatches();
    }

    public static class MatchStats {
        public int win;
        public int draw;
        public int lose;

        public void addMatch(int win, int draw, int lose) {
            this.win += win;
            this.draw += draw;
            this.lose += lose;
        }

        public int getTotalMatches() {
            return win + draw + lose;
        }

        public float getWinRate() {
            int total = getTotalMatches();
            return total > 0 ? (float) win / total * 100 : 0;
        }
    }
}

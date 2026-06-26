package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import ocgcore.enums.CardLocation;
import ocgcore.enums.CardPosition;
import ocgcore.enums.CardType;

public class GameField {
    public static final int MAX_MONSTER_ZONE = 7;
    public static final int MAX_SPELL_ZONE = 8;
    public static final int MAX_FIELD_SIZE = 8;
    public static final int MAX_HAND = 20;
    public static final int MAX_GRAVE = 128;
    public static final int MAX_REMOVED = 128;
    public static final int MAX_EXTRA = 32;
    public static final int MAX_DECK = 128;

    public static class ClientCard {
        public int code;
        public int alias;
        public int type;
        public int level;
        public int rank;
        public int attribute;
        public long race;
        public int attack;
        public int defense;
        public int baseAttack;
        public int baseDefense;
        public int position;
        public int owner;
        public int controler;
        public int location;
        public int sequence;
        public int lScale;
        public int rScale;
        public boolean isPublic;
        public boolean isDisabled;
        public int reason;
        public ClientCard equipCard;
        public List<ClientCard> targetCards = new ArrayList<>();
        public List<ClientCard> overlayCards = new ArrayList<>();
        public List<int[]> counters = new ArrayList<>();
        public int turnCounter;

        public boolean isFaceUp() {
            return (position & (CardPosition.FaceUpAttack.value() | CardPosition.FaceUpDefence.value())) != 0;
        }

        public boolean isAttack() {
            return (position & (CardPosition.FaceUpAttack.value() | CardPosition.FaceDownAttack.value())) != 0;
        }

        public boolean isMonster() {
            return (type & CardType.Monster.getId()) != 0;
        }

        public boolean isExtraCard() {
            return (type & (CardType.Fusion.getId() | CardType.Synchro.getId()
                    | CardType.Xyz.getId() | CardType.Link.getId())) != 0;
        }

        public void updateQuery(ByteBuffer buf) {
            // Parse query flags from buffer
        }
    }

    public static class PlayerField {
        public int lp;
        public final List<ClientCard> deck = new ArrayList<>();
        public final List<ClientCard> hand = new ArrayList<>();
        public final List<ClientCard> monsterZone = new ArrayList<>();
        public final List<ClientCard> spellZone = new ArrayList<>();
        public final List<ClientCard> grave = new ArrayList<>();
        public final List<ClientCard> removed = new ArrayList<>();
        public final List<ClientCard> extra = new ArrayList<>();
        public ClientCard fieldSpell;

        public PlayerField() {
            for (int i = 0; i < MAX_DECK; i++) deck.add(null);
            for (int i = 0; i < MAX_HAND; i++) hand.add(null);
            for (int i = 0; i < MAX_MONSTER_ZONE; i++) monsterZone.add(null);
            for (int i = 0; i < MAX_SPELL_ZONE; i++) spellZone.add(null);
            for (int i = 0; i < MAX_GRAVE; i++) grave.add(null);
            for (int i = 0; i < MAX_REMOVED; i++) removed.add(null);
            for (int i = 0; i < MAX_EXTRA; i++) extra.add(null);
        }

        public void clear() {
            lp = 8000;
            for (int i = 0; i < deck.size(); i++) deck.set(i, null);
            for (int i = 0; i < hand.size(); i++) hand.set(i, null);
            for (int i = 0; i < monsterZone.size(); i++) monsterZone.set(i, null);
            for (int i = 0; i < spellZone.size(); i++) spellZone.set(i, null);
            for (int i = 0; i < grave.size(); i++) grave.set(i, null);
            for (int i = 0; i < removed.size(); i++) removed.set(i, null);
            for (int i = 0; i < extra.size(); i++) extra.set(i, null);
            fieldSpell = null;
        }

        public List<ClientCard> getLocationList(int loc) {
            if (loc == CardLocation.Deck.value()) return deck;
            if (loc == CardLocation.Hand.value()) return hand;
            if (loc == CardLocation.MonsterZone.value()) return monsterZone;
            if (loc == CardLocation.SpellZone.value()) return spellZone;
            if (loc == CardLocation.Grave.value()) return grave;
            if (loc == CardLocation.Removed.value()) return removed;
            if (loc == CardLocation.Extra.value()) return extra;
            return null;
        }
    }

    public final PlayerField[] players = new PlayerField[2];
    public int currentPlayer;
    public int currentPhase;
    public int turnCount;
    public boolean isTag;

    public GameField() {
        players[0] = new PlayerField();
        players[1] = new PlayerField();
    }

    public void clear() {
        players[0].clear();
        players[1].clear();
        currentPlayer = 0;
        currentPhase = 0;
        turnCount = 0;
    }

    public ClientCard getCard(int controler, int location, int sequence) {
        if (controler < 0 || controler > 1) return null;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null || sequence < 0 || sequence >= list.size()) return null;
        return list.get(sequence);
    }

    public void addCard(int controler, int location, int sequence, ClientCard card) {
        if (controler < 0 || controler > 1) return;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null) return;
        while (list.size() <= sequence) list.add(null);
        list.set(sequence, card);
        if (card != null) {
            card.controler = controler;
            card.location = location;
            card.sequence = sequence;
        }
    }

    public void removeCard(int controler, int location, int sequence) {
        if (controler < 0 || controler > 1) return;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null || sequence >= list.size()) return;
        list.set(sequence, null);
    }

    public int getCardCount(int controler, int location) {
        if (controler < 0 || controler > 1) return 0;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null) return 0;
        int count = 0;
        for (ClientCard card : list) {
            if (card != null) count++;
        }
        return count;
    }
}

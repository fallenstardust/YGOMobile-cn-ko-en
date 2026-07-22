package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    public static final int MAX_LAYER_COUNT = 5;

    public static final int QUERY_CODE         = 0x01;
    public static final int QUERY_POSITION     = 0x02;
    public static final int QUERY_ALIAS        = 0x04;
    public static final int QUERY_TYPE         = 0x08;
    public static final int QUERY_LEVEL        = 0x10;
    public static final int QUERY_RANK         = 0x20;
    public static final int QUERY_ATTRIBUTE    = 0x40;
    public static final int QUERY_RACE         = 0x80;
    public static final int QUERY_ATTACK       = 0x100;
    public static final int QUERY_DEFENSE      = 0x200;
    public static final int QUERY_BASE_ATTACK  = 0x400;
    public static final int QUERY_BASE_DEFENSE = 0x800;
    public static final int QUERY_REASON       = 0x1000;
    public static final int QUERY_REASON_CARD  = 0x2000;
    public static final int QUERY_EQUIP_CARD   = 0x4000;
    public static final int QUERY_TARGET_CARD  = 0x8000;
    public static final int QUERY_OVERLAY_CARD = 0x10000;
    public static final int QUERY_COUNTERS     = 0x20000;
    public static final int QUERY_OWNER        = 0x40000;
    public static final int QUERY_STATUS       = 0x80000;
    public static final int QUERY_IS_PUBLIC    = 0x100000;
    public static final int QUERY_LSCALE       = 0x200000;
    public static final int QUERY_RSCALE       = 0x400000;
    public static final int QUERY_LINK         = 0x800000;

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
        public Map<Integer, Integer> counters = new HashMap<>();
        public int turnCounter;

        public int cmdFlag;
        public int opParam;
        public int select_seq;
        public boolean is_selectable;
        public boolean is_selected;
        public boolean is_highlighting;
        public int chain_code;
        public int link;
        public int link_marker;
        public ClientCard equipTarget;
        public List<ClientCard> equipped = new ArrayList<>();
        public List<ClientCard> overlayed = new ArrayList<>();
        public ClientCard overlayTarget;
        public int status;

        public boolean is_moving;
        public boolean is_fading;
        public boolean is_hovered;
        public boolean is_reversed;
        public boolean is_showequip;
        public boolean is_showtarget;
        public boolean is_showchaintarget;

        public String atkString = "";
        public String defString = "";
        public String lvString = "";
        public String linkString = "";
        public String lscString = "";
        public String rscString = "";

        public float curX, curY, curZ;
        public float curRotX, curRotY, curRotZ;
        public float dPosX, dPosY, dPosZ;
        public float dRotX, dRotY, dRotZ;
        public int aniFrame;
        public float curAlpha = 255;
        public float dAlpha;

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

        public boolean isLink() {
            return (type & CardType.Link.getId()) != 0;
        }

        public boolean isXyz() {
            return (type & CardType.Xyz.getId()) != 0;
        }

        public void clearCmdFlag() {
            cmdFlag = 0;
            is_selectable = false;
            is_selected = false;
            is_highlighting = false;
        }

        public void setCode(int newCode) {
            if (code == newCode) return;
            if (newCode == 0) {
                chain_code = code;
            }
            code = newCode;
        }

        public void clearData() {
            alias = 0;
            type = 0;
            level = 0;
            rank = 0;
            race = 0;
            attribute = 0;
            attack = 0;
            defense = 0;
            baseAttack = 0;
            baseDefense = 0;
            lScale = 0;
            rScale = 0;
            link = 0;
            link_marker = 0;
            status = 0;

            atkString = "";
            defString = "";
            lvString = "";
            linkString = "";
            lscString = "";
            rscString = "";
            counters.clear();
        }

        public void clearTarget() {
            for (ClientCard pcard : targetCards) {
                pcard.is_showtarget = false;
                pcard.overlayCards.remove(this);
            }
            for (ClientCard pcard : overlayCards) {
                pcard.is_showtarget = false;
                pcard.targetCards.remove(this);
            }
            targetCards.clear();
            overlayCards.clear();
        }

        public void updateQuery(ByteBuffer buf) {
            if (buf.remaining() < 4) return;
            int flag = buf.getInt();
            if (flag == 0) {
                clearData();
                return;
            }
            if ((flag & QUERY_CODE) != 0 && buf.remaining() >= 4) {
                int pdata = buf.getInt();
                if (pdata == 0) clearData();
                setCode(pdata);
            }
            if ((flag & QUERY_POSITION) != 0 && buf.remaining() >= 4) {
                int pdata = (buf.getInt() >> 24) & 0xff;
                position = pdata;
            }
            if ((flag & QUERY_ALIAS) != 0 && buf.remaining() >= 4) alias = buf.getInt();
            if ((flag & QUERY_TYPE) != 0 && buf.remaining() >= 4) type = buf.getInt();
            if ((flag & QUERY_LEVEL) != 0 && buf.remaining() >= 4) {
                int pdata = buf.getInt();
                if (level != pdata) {
                    level = pdata;
                    lvString = "L" + level;
                }
            }
            if ((flag & QUERY_RANK) != 0 && buf.remaining() >= 4) {
                int pdata = buf.getInt();
                if (pdata != 0 && rank != pdata) {
                    rank = pdata;
                    lvString = "R" + rank;
                }
            }
            if ((flag & QUERY_ATTRIBUTE) != 0 && buf.remaining() >= 4) attribute = buf.getInt();
            if ((flag & QUERY_RACE) != 0 && buf.remaining() >= 4) race = buf.getInt();
            if ((flag & QUERY_ATTACK) != 0 && buf.remaining() >= 4) {
                attack = buf.getInt();
                atkString = attack < 0 ? "?" : String.valueOf(attack);
            }
            if ((flag & QUERY_DEFENSE) != 0 && buf.remaining() >= 4) {
                defense = buf.getInt();
                if (isLink()) {
                    defString = "-";
                } else {
                    defString = defense < 0 ? "?" : String.valueOf(defense);
                }
            }
            if ((flag & QUERY_BASE_ATTACK) != 0 && buf.remaining() >= 4) baseAttack = buf.getInt();
            if ((flag & QUERY_BASE_DEFENSE) != 0 && buf.remaining() >= 4) baseDefense = buf.getInt();
            if ((flag & QUERY_REASON) != 0 && buf.remaining() >= 4) reason = buf.getInt();
            if ((flag & QUERY_REASON_CARD) != 0 && buf.remaining() >= 4) buf.getInt();
            if ((flag & QUERY_EQUIP_CARD) != 0 && buf.remaining() >= 4) {
                buf.get();
                buf.get();
                buf.get();
                buf.get();
            }
            if ((flag & QUERY_TARGET_CARD) != 0 && buf.remaining() >= 4) {
                int count = buf.getInt();
                for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                    buf.get();
                    buf.get();
                    buf.get();
                    buf.get();
                }
            }
            if ((flag & QUERY_OVERLAY_CARD) != 0 && buf.remaining() >= 4) {
                int count = buf.getInt();
                for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                    int ocode = buf.getInt();
                    if (i >= overlayed.size()) {
                        ClientCard xcard = new ClientCard();
                        overlayed.add(xcard);
                        xcard.overlayTarget = this;
                        xcard.location = CardLocation.Overlay.value();
                        xcard.sequence = overlayed.size() - 1;
                        xcard.owner = controler;
                        xcard.controler = controler;
                    }
                    overlayed.get(i).setCode(ocode);
                }
            }
            if ((flag & QUERY_COUNTERS) != 0 && buf.remaining() >= 4) {
                int count = buf.getInt();
                for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                    int ctype = buf.getShort() & 0xFFFF;
                    int ccount = buf.getShort() & 0xFFFF;
                    counters.put(ctype, ccount);
                }
            }
            if ((flag & QUERY_OWNER) != 0 && buf.remaining() >= 4) owner = buf.getInt();
            if ((flag & QUERY_STATUS) != 0 && buf.remaining() >= 4) status = buf.getInt();
            if ((flag & QUERY_LSCALE) != 0 && buf.remaining() >= 4) {
                lScale = buf.getInt();
                lscString = String.valueOf(lScale);
            }
            if ((flag & QUERY_RSCALE) != 0 && buf.remaining() >= 4) {
                rScale = buf.getInt();
                rscString = String.valueOf(rScale);
            }
            if ((flag & QUERY_LINK) != 0 && buf.remaining() >= 8) {
                int pdata = buf.getInt();
                if (link != pdata) {
                    link = pdata;
                }
                linkString = "L\u2013" + link;
                int pdata2 = buf.getInt();
                if (link_marker != pdata2) {
                    link_marker = pdata2;
                }
            }
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

    public static class ChainInfo {
        public ClientCard chainCard;
        public int code;
        public int desc;
        public int controler;
        public int location;
        public int sequence;
        public boolean solved;
        public boolean needDistinguish;
        public List<ClientCard> targets = new ArrayList<>();
    }

    public final PlayerField[] players = new PlayerField[2];
    public int currentPlayer;
    public int currentPhase;
    public int turnCount;
    public boolean isTag;
    public List<ChainInfo> chains = new ArrayList<>();
    public List<ClientCard> overlayCards = new ArrayList<>();
    public int[] extraPCount = new int[2];
    public long disabledField;
    public boolean deckReversed;
    public boolean cantCheckGrave;
    public boolean tagSurrender;
    public boolean tagTeammateSurrender;

    public List<ClientCard> activatableCards = new ArrayList<>();
    public List<ClientCard> summonableCards = new ArrayList<>();
    public List<ClientCard> spsummonableCards = new ArrayList<>();
    public List<ClientCard> msetableCards = new ArrayList<>();
    public List<ClientCard> ssetableCards = new ArrayList<>();
    public List<ClientCard> reposableCards = new ArrayList<>();
    public List<ClientCard> attackableCards = new ArrayList<>();
    public List<ClientCard> contiCards = new ArrayList<>();
    public boolean[] deckAct = new boolean[2];
    public boolean[] graveAct = new boolean[2];
    public boolean[] removeAct = new boolean[2];
    public boolean[] extraAct = new boolean[2];
    public boolean[] pzoneAct = new boolean[2];
    public boolean contiAct;

    public List<ClientCard> selectableCards = new ArrayList<>();
    public List<ClientCard> selectedCards = new ArrayList<>();
    public List<ClientCard> selectsumCards = new ArrayList<>();
    public List<ClientCard> selectsumAll = new ArrayList<>();
    public List<ClientCard> displayCards = new ArrayList<>();
    public int[] sortList;
    public int selectMin, selectMax, mustSelectCount;
    public int selectSumval, selectMode;
    public int selectHint;
    public boolean selectCancelable;
    public boolean selectReady;
    public int selectCurvalL, selectCurvalH;

    public GameField() {
        players[0] = new PlayerField();
        players[1] = new PlayerField();
    }

    public void clear() {
        for (int i = 0; i < 2; i++) {
            players[i].clear();
            deckAct[i] = false;
            graveAct[i] = false;
            removeAct[i] = false;
            extraAct[i] = false;
            pzoneAct[i] = false;
        }
        overlayCards.clear();
        chains.clear();
        activatableCards.clear();
        summonableCards.clear();
        spsummonableCards.clear();
        msetableCards.clear();
        ssetableCards.clear();
        reposableCards.clear();
        attackableCards.clear();
        contiCards.clear();
        contiAct = false;
        disabledField = 0;
        deckReversed = false;
        cantCheckGrave = false;
        tagSurrender = false;
        tagTeammateSurrender = false;
        currentPlayer = 0;
        currentPhase = 0;
        turnCount = 0;
        extraPCount[0] = 0;
        extraPCount[1] = 0;
    }

    public void initial(int player, int deckc, int extrac, int sidec) {
        for (int i = 0; i < deckc && i < players[player].deck.size(); i++) {
            ClientCard pcard = new ClientCard();
            pcard.owner = player;
            pcard.controler = player;
            pcard.location = CardLocation.Deck.value();
            pcard.sequence = i;
            pcard.position = CardPosition.FaceDownDefence.value();
            players[player].deck.set(i, pcard);
        }
        for (int i = 0; i < extrac && i < players[player].extra.size(); i++) {
            ClientCard pcard = new ClientCard();
            pcard.owner = player;
            pcard.controler = player;
            pcard.location = CardLocation.Extra.value();
            pcard.sequence = i;
            pcard.position = CardPosition.FaceDownDefence.value();
            players[player].extra.set(i, pcard);
        }
        for (int i = 0; i < sidec && i < players[player].removed.size(); i++) {
            ClientCard pcard = new ClientCard();
            pcard.owner = player;
            pcard.controler = player;
            pcard.location = CardLocation.Removed.value();
            pcard.sequence = i;
            pcard.position = CardPosition.FaceDownDefence.value();
            players[player].removed.set(i, pcard);
        }
    }

    public void resetSequence(List<ClientCard> list, boolean resetHeight) {
        int seq = 0;
        for (ClientCard pcard : list) {
            if (pcard != null) {
                pcard.sequence = seq++;
                if (resetHeight) {
                    pcard.curZ = 0.01f + 0.01f * pcard.sequence;
                }
            }
        }
    }

    public ClientCard getCard(int controler, int location, int sequence) {
        if (controler < 0 || controler > 1) return null;
        boolean isXyz = (location & CardLocation.Overlay.value()) != 0;
        location &= 0x7f;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null || sequence < 0 || sequence >= list.size()) return null;
        ClientCard card = list.get(sequence);
        if (isXyz && card != null) {
            return null;
        }
        return card;
    }

    public ClientCard getCard(int controler, int location, int sequence, int subSeq) {
        if (controler < 0 || controler > 1) return null;
        boolean isXyz = (location & CardLocation.Overlay.value()) != 0;
        location &= 0x7f;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null || sequence < 0 || sequence >= list.size()) return null;
        ClientCard card = list.get(sequence);
        if (isXyz && card != null) {
            if (subSeq >= 0 && subSeq < card.overlayed.size()) {
                return card.overlayed.get(subSeq);
            }
            return null;
        }
        return card;
    }

    public void addCard(int controler, int location, int sequence, ClientCard card) {
        if (controler < 0 || controler > 1) return;
        if (card != null) {
            card.controler = controler;
            card.location = location;
            card.sequence = sequence;
        }
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null) return;
        while (list.size() <= sequence) list.add(null);
        switch (location) {
            case 0x01: {
                if (sequence == 0 && !list.isEmpty() && list.get(0) != null) {
                    list.add(0, card);
                } else {
                    list.set(sequence, card);
                }
                resetSequence(list, true);
                if (card != null) {
                    card.is_reversed = false;
                    card.clearData();
                    card.clearTarget();
                }
                break;
            }
            case 0x02: {
                list.set(sequence, card);
                resetSequence(list, false);
                break;
            }
            case 0x04: {
                list.set(sequence, card);
                break;
            }
            case 0x08: {
                list.set(sequence, card);
                break;
            }
            case 0x10: {
                list.set(sequence, card);
                if (card != null) card.sequence = sequence;
                break;
            }
            case 0x20: {
                list.set(sequence, card);
                if (card != null) card.sequence = sequence;
                break;
            }
            case 0x40: {
                if (extraPCount[controler] == 0 || (card != null && card.isFaceUp())) {
                    list.set(sequence, card);
                } else {
                    int faceupBegin = list.size() - extraPCount[controler];
                    if (faceupBegin < 0) faceupBegin = 0;
                    if (faceupBegin > list.size()) faceupBegin = list.size();
                    list.add(faceupBegin, card);
                }
                resetSequence(list, true);
                if (card != null && card.isFaceUp()) {
                    extraPCount[controler]++;
                }
                break;
            }
        }
    }

    public ClientCard removeCard(int controler, int location, int sequence) {
        if (controler < 0 || controler > 1) return null;
        List<ClientCard> list = players[controler].getLocationList(location);
        if (list == null || sequence < 0 || sequence >= list.size()) return null;
        ClientCard pcard = list.get(sequence);
        switch (location) {
            case 0x01: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                break;
            }
            case 0x02: {
                list.set(sequence, null);
                resetSequence(list, false);
                break;
            }
            case 0x04: {
                list.set(sequence, null);
                break;
            }
            case 0x08: {
                list.set(sequence, null);
                break;
            }
            case 0x10: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                break;
            }
            case 0x20: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                break;
            }
            case 0x40: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                if (pcard != null && pcard.isFaceUp()) {
                    extraPCount[controler]--;
                }
                break;
            }
        }
        if (pcard != null) pcard.location = 0;
        return pcard;
    }

    public void updateCard(int controler, int location, int sequence, ByteBuffer data) {
        ClientCard pcard = getCard(controler, location, sequence);
        if (pcard != null && data.remaining() >= 4) {
            int len = data.getInt();
            if (len > 4 && data.remaining() >= len - 4) {
                pcard.updateQuery(data);
            }
        }
    }

    public void moveCard(ClientCard pcard, int frame) {
        if (pcard == null) return;
        pcard.is_moving = true;
        pcard.aniFrame = frame;
    }

    public void fadeCard(ClientCard pcard, int alpha, int frame) {
        if (pcard == null) return;
        pcard.dAlpha = ((float) alpha - pcard.curAlpha) / frame;
        pcard.is_fading = true;
        pcard.aniFrame = frame;
    }

    public float[] getCardLocation(ClientCard pcard) {
        float[] result = new float[]{0, 0, 0, 0, 0, 0};
        int controler = pcard.controler;
        int sequence = pcard.sequence;
        int location = pcard.location;
        boolean flipped = (controler == 1);
        float halfFieldH = 1.0f;

        switch (location) {
            case 0x01: {
                result[0] = flipped ? 0.15f : 0.75f;
                result[1] = flipped ? 0.1f : 0.75f;                result[2] = 0.01f * sequence;
                break;
            }
            case 0x02: {
                List<ClientCard> hand = players[controler].hand;
                int count = 0;
                for (ClientCard c : hand) if (c != null) count++;
                int idx = 0;
                for (int i = 0; i < hand.size(); i++) {
                    if (hand.get(i) == pcard) { idx = i; break; }
                    if (hand.get(i) != null) idx++;
                }
                if (count <= 6) {
                    result[0] = (5.5f - 0.8f * count) / 2f + 1.55f + idx * 0.8f;
                } else {
                    result[0] = 1.9f + idx * 4.0f / (count - 1);
                }
                if (flipped) {
                    result[0] = 6.25f - result[0] + 1.55f;
                    result[1] = -3.4f;
                    result[2] = 0.5f - 0.001f * idx;
                } else {                    result[1] = 4.0f;
                    result[2] = 0.5f + 0.001f * idx;
                }
                break;
            }
            case 0x04: {
                result[0] = 1.5f + sequence * 0.85f;
                result[1] = flipped ? -1.5f : 1.5f;
                result[2] = 0.02f;
                break;
            }
            case 0x08: {
                result[0] = 1.5f + sequence * 0.85f;
                result[1] = flipped ? -2.5f : 2.5f;
                result[2] = 0.01f;
                break;
            }
            case 0x10: {
                result[0] = flipped ? 0.5f : 0.5f; result[1] = flipped ? -0.5f : 0.5f;
                result[2] = 0.01f * sequence;
                break;
            }
            case 0x20: {
                result[0] = flipped ? 0.8f : 0.8f;
                result[1] = flipped ? -0.8f : 0.8f;
                result[2] = 0.01f * sequence;
                break;
            }
            case 0x40: {
                result[0] = flipped ? 0.2f : 0.2f;
                result[1] = flipped ? -0.2f : 0.2f;
                result[2] = 0.01f * sequence;
                break;
            }
            case 0x80: {
                if (pcard.overlayTarget != null && pcard.overlayTarget.location == 0x04) {
                    int oseq = pcard.overlayTarget.sequence;
                    int mseq = Math.min(pcard.sequence, MAX_LAYER_COUNT - 1);
                    result[0] = 1.5f + oseq * 0.85f + (flipped ? -0.12f + 0.06f * mseq : 0.12f - 0.06f * mseq);
                    result[1] = (flipped ? -1.5f : 1.5f) + (flipped ? -0.05f : 0.05f);
                    result[2] = 0.001f + mseq * 0.003f;
                }
                break;
            }
        }

        if (flipped) {
            result[4] = 0;
            result[5] = 0;
            result[3] = (float) Math.PI; }

        return result;
    }

    public void updateCardAnimation(int frame) {        for (int p = 0; p < 2; p++) {
            updateListAnimation(players[p].deck);
            updateListAnimation(players[p].hand);
            updateListAnimation(players[p].monsterZone);
            updateListAnimation(players[p].spellZone);
            updateListAnimation(players[p].grave);
            updateListAnimation(players[p].removed);
            updateListAnimation(players[p].extra);
        }
        updateListAnimation(overlayCards);
    }

    private void updateListAnimation(List<ClientCard> list) {
        for (ClientCard pcard : list) {
            if (pcard == null) continue;
            if (pcard.is_moving) {
                pcard.curX += pcard.dPosX;
                pcard.curY += pcard.dPosY;
                pcard.curZ += pcard.dPosZ;
                pcard.curRotX += pcard.dRotX;
                pcard.curRotY += pcard.dRotY;
                pcard.curRotZ += pcard.dRotZ;
                pcard.aniFrame--;
                if (pcard.aniFrame <= 0) {
                    pcard.is_moving = false;
                    pcard.aniFrame = 0;
                }
            }
            if (pcard.is_fading) {
                pcard.curAlpha += pcard.dAlpha;
                pcard.aniFrame--;
                if (pcard.aniFrame <= 0) {
                    pcard.is_fading = false;
                    pcard.aniFrame = 0;
                }
            }
        }
    }

    public void refreshAllCards() {
        for (int p = 0; p < 2; p++) {
            for (ClientCard c : players[p].deck) {
                if (c != null) { setCardPos(c); c.is_moving = false; }
            }
            for (ClientCard c : players[p].hand) {
                if (c != null) { setCardPos(c); c.is_moving = false; }
            }
            for (ClientCard c : players[p].monsterZone) {
                if (c != null) { setCardPos(c); c.is_moving = false; }
            }
            for (ClientCard c : players[p].spellZone) {
                if (c != null) { setCardPos(c); c.is_moving = false; }
            }
            for (ClientCard c : players[p].grave) {
                if (c != null) { setCardPos(c); c.is_moving = false; }
            }
            for (ClientCard c : players[p].removed) {
                if (c != null) { setCardPos(c); c.is_moving = false; }
            }
            for (ClientCard c : players[p].extra) {
                if (c != null) { setCardPos(c); c.is_moving = false; }
            }
        }
        for (ClientCard c : overlayCards) {
            if (c != null) { setCardPos(c); c.is_moving = false; }
        }
    }

    private void setCardPos(ClientCard pcard) {
        float[] loc = getCardLocation(pcard);
        pcard.curX = loc[0];
        pcard.curY = loc[1];
        pcard.curZ = loc[2];
        pcard.curRotX = loc[3];
        pcard.curRotY = loc[4];
        pcard.curRotZ = loc[5];
    }

    public void moveCardAnimated(ClientCard pcard, int frame) {
        if (pcard == null || frame <= 0) return;
        float[] loc = getCardLocation(pcard);
        float transX = loc[0], transY = loc[1], transZ = loc[2];
        float rotX = loc[3], rotY = loc[4], rotZ = loc[5];

        pcard.dPosX = (transX - pcard.curX) / frame;
        pcard.dPosY = (transY - pcard.curY) / frame;
        pcard.dPosZ = (transZ - pcard.curZ) / frame;

        float diff;
        diff = rotX - pcard.curRotX;
        while (diff < 0) diff += (float) (Math.PI * 2);
        while (diff > Math.PI * 2) diff -= (float) (Math.PI * 2);
        pcard.dRotX = (diff < Math.PI) ? diff / frame : -(float) (Math.PI * 2 - diff) / frame;

        diff = rotY - pcard.curRotY;
        while (diff < 0) diff += (float) (Math.PI * 2);
        while (diff > Math.PI * 2) diff -= (float) (Math.PI * 2);
        pcard.dRotY = (diff < Math.PI) ? diff / frame : -(float) (Math.PI * 2 - diff) / frame;

        diff = rotZ - pcard.curRotZ;
        while (diff < 0) diff += (float) (Math.PI * 2);
        while (diff > Math.PI * 2) diff -= (float) (Math.PI * 2);
        pcard.dRotZ = (diff < Math.PI) ? diff / frame : -(float) (Math.PI * 2 - diff) / frame;

        pcard.is_moving = true;
        pcard.aniFrame = frame;
    }

    public void clearCommandFlag() {
        for (ClientCard c : activatableCards) if (c != null) { c.cmdFlag = 0; c.chain_code = 0; c.is_selectable = false; c.is_selected = false; }
        for (ClientCard c : summonableCards) if (c != null) { c.cmdFlag = 0; c.is_selectable = false; c.is_selected = false; }
        for (ClientCard c : spsummonableCards) if (c != null) { c.cmdFlag = 0; c.is_selectable = false; c.is_selected = false; }
        for (ClientCard c : msetableCards) if (c != null) { c.cmdFlag = 0; c.is_selectable = false; c.is_selected = false; }
        for (ClientCard c : ssetableCards) if (c != null) { c.cmdFlag = 0; c.is_selectable = false; c.is_selected = false; }
        for (ClientCard c : reposableCards) if (c != null) { c.cmdFlag = 0; c.is_selectable = false; c.is_selected = false; }
        for (ClientCard c : attackableCards) if (c != null) { c.cmdFlag = 0; c.is_selectable = false; c.is_selected = false; }
        for (int i = 0; i < 2; i++) {
            deckAct[i] = false;
            extraAct[i] = false;
            graveAct[i] = false;
            removeAct[i] = false;
            pzoneAct[i] = false;
        }
        contiCards.clear();
        contiAct = false;
        activatableCards.clear();
        summonableCards.clear();
        spsummonableCards.clear();
        msetableCards.clear();
        ssetableCards.clear();
        reposableCards.clear();
        attackableCards.clear();
    }

    public void clearSelect() {
        for (ClientCard c : selectableCards) {
            if (c != null) { c.is_selectable = false; c.is_selected = false; }
        }
        for (ClientCard c : selectedCards) {
            if (c != null) { c.is_selectable = false; c.is_selected = false; }
        }
        for (ClientCard c : selectsumAll) {
            if (c != null) { c.is_selectable = false; c.is_selected = false; }
        }
        for (ClientCard c : selectsumCards) {
            if (c != null) { c.is_selectable = false; c.is_selected = false; }
        }
    }

    public void clearChainSelect() {
        for (ClientCard c : activatableCards) {
            if (c != null) { c.cmdFlag = 0; c.chain_code = 0; c.is_selectable = false; c.is_selected = false; }
        }
        for (int i = 0; i < 2; i++) {
            deckAct[i] = false;
            extraAct[i] = false;
            graveAct[i] = false;
            removeAct[i] = false;
            pzoneAct[i] = false;
        }
        contiCards.clear();
        contiAct = false;
    }

    public void swapField() {
        PlayerField temp = players[0];
        players[0] = players[1];
        players[1] = temp;
        for (int p = 0; p < 2; p++) {
            updateCardControler(players[p], p);
        }
        int tmpExtraP = extraPCount[0];
        extraPCount[0] = extraPCount[1];
        extraPCount[1] = tmpExtraP;
        for (ClientCard card : overlayCards) {
            if (card != null) card.controler = 1 - card.controler;
        }
        for (ChainInfo ch : chains) {
            ch.controler = 1 - ch.controler;
        }
        disabledField = (disabledField >> 16) | (disabledField << 16);
    }

    public static boolean clientCardSort(ClientCard c1, ClientCard c2) {
        if (c1.is_selected != c2.is_selected)
            return !c1.is_selected;
        int cp1 = c1.overlayTarget != null ? c1.overlayTarget.controler : c1.controler;
        int cp2 = c2.overlayTarget != null ? c2.overlayTarget.controler : c2.controler;
        if (cp1 != cp2) return cp1 < cp2;
        if (c1.location != c2.location) return c1.location < c2.location;
        if (c1.location == CardLocation.Overlay.value()) {
            if (c1.overlayTarget != c2.overlayTarget)
                return c1.overlayTarget.sequence < c2.overlayTarget.sequence;
            else
                return c1.sequence < c2.sequence;
        } else if (c1.location == CardLocation.Deck.value()) {
            return c1.sequence > c2.sequence;
        } else if ((c1.location & (CardLocation.Grave.value() | CardLocation.Removed.value() | CardLocation.Extra.value())) != 0) {
            return c1.sequence > c2.sequence;
        } else {
            return c1.sequence < c2.sequence;
        }
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

    private void updateCardControler(PlayerField playerField, int controler) {
        updateListControler(playerField.deck, controler);
        updateListControler(playerField.hand, controler);
        updateListControler(playerField.monsterZone, controler);
        updateListControler(playerField.spellZone, controler);
        updateListControler(playerField.grave, controler);
        updateListControler(playerField.removed, controler);
        updateListControler(playerField.extra, controler);
        if (playerField.fieldSpell != null) {
            playerField.fieldSpell.controler = controler;
        }
    }

    private void updateListControler(List<ClientCard> list, int controler) {
        for (ClientCard card : list) {
            if (card != null) {
                card.controler = controler;
            }
        }
    }
}

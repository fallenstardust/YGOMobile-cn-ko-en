package cn.garymb.ygomobile.loader;

import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.data.Card;
import ocgcore.data.LimitList;

public class DeckLoader {
    public static DeckInfo readDeck(CardLoader cardLoader, File file, LimitList limitList) {
        DeckInfo deckInfo = null;
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            deckInfo = readDeck(cardLoader, inputStream, limitList);
            if(deckInfo != null){
                deckInfo.source = file;
            }
        } catch (Exception e) {
            Log.e("deckreader", "read 1", e);
        } finally {
            IOUtils.close(inputStream);
        }
        return deckInfo;
    }

    private static DeckInfo readDeck(CardLoader cardLoader, InputStream inputStream, LimitList limitList) {
        Deck deck = new Deck();
        SparseArray<Integer> mIds = new SparseArray<>();
        InputStreamReader in = null;
        try {
            in = new InputStreamReader(inputStream, "utf-8");
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            DeckItemType type = DeckItemType.Space;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("!side")) {
                    type = DeckItemType.SideCard;
                    continue;
                }
                if (line.startsWith("#")) {
                    if (line.startsWith("#main")) {
                        type = DeckItemType.MainCard;
                    } else if (line.startsWith("#extra")) {
                        type = DeckItemType.ExtraCard;
                    }
                    continue;
                }
                line = line.trim();
                if (line.length() == 0 || !TextUtils.isDigitsOnly(line)) {
                    if (Constants.DEBUG)
                        Log.w("kk", "read not number " + line);
                    continue;
                }
                Integer id = Integer.parseInt(line);
                if (type == DeckItemType.MainCard && deck.getMainCount() < Constants.DECK_MAIN_MAX) {
                    Integer i = mIds.get(id);
                    if (i == null) {
                        mIds.put(id, 1);
                        deck.addMain(id);
                    } else if (i < Constants.CARD_MAX_COUNT) {
                        mIds.put(id, i + 1);
                        deck.addMain(id);
                    }
                } else if (type == DeckItemType.ExtraCard && deck.getExtraCount() < Constants.DECK_EXTRA_MAX) {
                    Integer i = mIds.get(id);
                    if (i == null) {
                        mIds.put(id, 1);
                        deck.addExtra(id);
                    } else if (i < Constants.CARD_MAX_COUNT) {
                        mIds.put(id, i + 1);
                        deck.addExtra(id);
                    }
                } else if (type == DeckItemType.SideCard && deck.getSideCount() < Constants.DECK_SIDE_MAX) {
                    Integer i = mIds.get(id);
                    if (i == null) {
                        mIds.put(id, 1);
                        deck.addSide(id);
                    } else if (i < Constants.CARD_MAX_COUNT) {
                        mIds.put(id, i + 1);
                        deck.addSide(id);
                    }
                }
            }
        } catch (IOException e) {
            Log.e("deckreader", "read 2", e);
        } finally {
            IOUtils.close(in);
        }
        DeckInfo deckInfo = new DeckInfo();
        SparseArray<Card> tmp = cardLoader.readCards(deck.getMainlist(), limitList);
        for (Integer id : deck.getMainlist()) {
            deckInfo.addMainCards(tmp.get(id));
        }
        tmp = cardLoader.readCards(deck.getExtraList(), limitList);
        for (Integer id : deck.getExtraList()) {
            deckInfo.addExtraCards(tmp.get(id));
        }
        tmp = cardLoader.readCards(deck.getSideList(), limitList);
//        Log.i("kk", "desk:" + tmp.size()+"/"+side.size());
        for (Integer id : deck.getSideList()) {
            deckInfo.addSideCards(tmp.get(id));
        }
        return deckInfo;
    }
}

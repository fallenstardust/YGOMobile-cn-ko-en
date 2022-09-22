package cn.garymb.ygomobile.loader;

import static cn.garymb.ygomobile.Constants.newIDsArray;
import static cn.garymb.ygomobile.Constants.oldIDsArray;

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
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.hutool.core.util.ArrayUtil;
import ocgcore.data.Card;
import ocgcore.data.LimitList;

public class DeckLoader {
    private static Boolean isChanged;

    public static DeckInfo readDeck(CardLoader cardLoader, File file, LimitList limitList) {
        DeckInfo deckInfo = null;
        FileInputStream fileinputStream = null;
        try {
            fileinputStream = new FileInputStream(file);
            deckInfo = readDeck(cardLoader, fileinputStream, limitList);
            if (deckInfo != null) {
                deckInfo.source = file;
                if (isChanged) {
                    DeckUtils.save(deckInfo, deckInfo.source);
                    isChanged = false;
                }
            }
        } catch (Exception e) {
            Log.e("deckreader", "read 1", e);
        } finally {
            IOUtils.close(fileinputStream);
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
                if (line.length() > 9) {//密码如果大于9位直接过滤
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
        SparseArray<Card> tmp = cardLoader.readCards(deck.getMainlist(), true);
        int code;
        isChanged = false;
        for (Integer id : deck.getMainlist()) {
            if (ArrayUtil.contains(oldIDsArray, tmp.get(id).getCode())) {
                code = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, tmp.get(id).getCode()));
                tmp.remove(id);
                tmp.put(id, cardLoader.readAllCardCodes().get(code));
                isChanged = true;
            }
            deckInfo.addMainCards(tmp.get(id));
        }
        tmp = cardLoader.readCards(deck.getExtraList(), true);
        for (Integer id : deck.getExtraList()) {
            if (ArrayUtil.contains(oldIDsArray, tmp.get(id).getCode())) {
                code = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, tmp.get(id).getCode()));
                tmp.remove(id);
                tmp.put(id, cardLoader.readAllCardCodes().get(code));
                isChanged = true;
            }
            deckInfo.addExtraCards(tmp.get(id));
        }
        tmp = cardLoader.readCards(deck.getSideList(), true);
//        Log.i("kk", "desk:" + tmp.size()+"/"+side.size());
        for (Integer id : deck.getSideList()) {
            if (ArrayUtil.contains(oldIDsArray, tmp.get(id).getCode())) {
                code = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, tmp.get(id).getCode()));
                tmp.remove(id);
                tmp.put(id, cardLoader.readAllCardCodes().get(code));
                isChanged = true;
            }
            deckInfo.addSideCards(tmp.get(id));
        }
        return deckInfo;
    }
}

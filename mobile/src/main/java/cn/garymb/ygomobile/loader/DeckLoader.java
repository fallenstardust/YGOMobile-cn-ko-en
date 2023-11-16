package cn.garymb.ygomobile.loader;

import static cn.garymb.ygomobile.Constants.newIDsArray;
import static cn.garymb.ygomobile.Constants.oldIDsArray;
import static cn.garymb.ygomobile.ui.home.HomeActivity.pre_code_list;
import static cn.garymb.ygomobile.ui.home.HomeActivity.released_code_list;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.home.HomeActivity;
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
        DeckItemType type = DeckItemType.Space;
        try {
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);
            String line = null;
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
                    } else {
                        type = DeckItemType.Pack;
                    }
                    continue;
                }
                line = line.trim();
                if (line.length() == 0 || !TextUtils.isDigitsOnly(line)) {
                    if (Constants.DEBUG)
                        Log.w("kk", "read not number " + line);
                    continue;
                }
                if (line.equals("0") || line.length() > 9) {//密码为0或者长度大于9位直接过滤
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
                } else if (type == DeckItemType.Pack) {
                    Integer i = mIds.get(id);
                    if (i == null) {
                        mIds.put(id, 1);
                        deck.addPack(id);
                    } else if (i < Constants.CARD_MAX_COUNT) {
                        mIds.put(id, i + 1);
                        deck.addPack(id);
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
            if (released_code_list.contains(tmp.get(id).getCode())) {//先查看id对应的卡片密码是否在正式数组中存在
                code = pre_code_list.get(released_code_list.indexOf(tmp.get(id).getCode()));//替换成对应先行数组里的code
                if (cardLoader.readAllCardCodes().get(code) != null) {//万一他还没下载扩展卡包就不执行否则会空指错误
                    tmp.set(id, cardLoader.readAllCardCodes().get(code));
                }
            }//执行完后变成先行密码，如果constants对照表里存在该密码，则如下又转换一次，确保正式更新后不会出错，最好发布app后必须及时更新在线对照表
            if (ArrayUtil.contains(oldIDsArray, tmp.get(id).getCode())) {
                code = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, tmp.get(id).getCode()));
                tmp.set(id, cardLoader.readAllCardCodes().get(code));
                isChanged = true;
            }
            deckInfo.addMainCards(id, tmp.get(id), type == DeckItemType.Pack);
        }
        tmp = cardLoader.readCards(deck.getExtraList(), true);
        for (Integer id : deck.getExtraList()) {
            if (released_code_list.contains(tmp.get(id).getCode())) {
                code = pre_code_list.get(released_code_list.indexOf(tmp.get(id).getCode()));
                    if (cardLoader.readAllCardCodes().get(code) != null) {
                        tmp.set(id, cardLoader.readAllCardCodes().get(code));
                    }
            }
            if (ArrayUtil.contains(oldIDsArray, tmp.get(id).getCode())) {
                code = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, tmp.get(id).getCode()));
                tmp.set(id, cardLoader.readAllCardCodes().get(code));
                isChanged = true;
            }
            deckInfo.addExtraCards(tmp.get(id));
        }
        tmp = cardLoader.readCards(deck.getSideList(), true);
//        Log.i("kk", "desk:" + tmp.size()+"/"+side.size());
        for (Integer id : deck.getSideList()) {
            if (released_code_list.contains(tmp.get(id).getCode())) {
                code = pre_code_list.get(released_code_list.indexOf(tmp.get(id).getCode()));
                if (cardLoader.readAllCardCodes().get(code) != null) {
                    tmp.set(id, cardLoader.readAllCardCodes().get(code));
                }
            }
            if (ArrayUtil.contains(oldIDsArray, tmp.get(id).getCode())) {
                code = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, tmp.get(id).getCode()));
                tmp.set(id, cardLoader.readAllCardCodes().get(code));
                isChanged = true;
            }
            deckInfo.addSideCards(tmp.get(id));
        }
        Log.w("deck.source",deckInfo.toLongString());
        return deckInfo;
    }
}

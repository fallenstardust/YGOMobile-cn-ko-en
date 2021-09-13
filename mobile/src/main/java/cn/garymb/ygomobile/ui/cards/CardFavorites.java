package cn.garymb.ygomobile.ui.cards;

import android.text.TextUtils;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.loader.ICardSearcher;
import cn.garymb.ygomobile.utils.FileUtils;
import ocgcore.data.Card;

public class CardFavorites {
    private final List<Integer> mList = new ArrayList<>();

    private static final CardFavorites sCardFavorites = new CardFavorites();

    public static CardFavorites get() {
        return sCardFavorites;
    }

    private CardFavorites() {

    }

    public boolean toggle(Integer id) {
        if (!mList.contains(id)) {
            //添加
            mList.add(id);
            return true;
        } else {
            //移除
            mList.remove(id);
            return false;
        }
    }

    public boolean add(Integer id) {
        if (!mList.contains(id)) {
            mList.add(id);
            return true;
        }
        return false;
    }

    public boolean hasCard(Integer id){
        return mList.contains(id);
    }

    public List<Integer> getCardIds() {
        return mList;
    }

    public List<Card> getCards(ICardSearcher cardLoader) {
        SparseArray<Card> id = cardLoader.readCards(mList, false);
        List<Card> list = new ArrayList<>();
        if (id != null) {
            for (int i = 0; i < id.size(); i++) {
                list.add(id.valueAt(i));
            }
        }
        return cardLoader.sort(list);
    }

    public void remove(Integer id) {
        mList.remove(id);
    }

    public void load() {
        List<String> lines = FileUtils.readLines(AppsSettings.get().getFavoriteTxt(), Constants.DEF_ENCODING);
        mList.clear();
        for (String line : lines) {
            String tmp = line.trim();
            if (TextUtils.isDigitsOnly(tmp)) {
                mList.add(Integer.parseInt(tmp));
            }
        }
    }

    public void save() {
        List<String> ret = new ArrayList<>();
        for (Integer id : mList) {
            ret.add(String.valueOf(id));
        }
        FileUtils.writeLines(AppsSettings.get().getFavoriteTxt(), ret, Constants.DEF_ENCODING, "\n");
    }
}

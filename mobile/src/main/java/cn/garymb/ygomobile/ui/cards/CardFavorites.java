package cn.garymb.ygomobile.ui.cards;

import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.loader.ICardSearcher;
import cn.garymb.ygomobile.utils.FileUtils;
import ocgcore.data.Card;

public class CardFavorites {
    private final List<Integer> mList = new ArrayList<>();
    private static final String TAG = "CardFavorites";
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

    public boolean hasCard(Integer id) {
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
        mList.clear();
        File config = AppsSettings.get().getFavoriteFile();
        List<String> lines;
        if (config.exists()) {
            //重命名
            if (!config.renameTo(AppsSettings.get().getSystemConfig())) {
                Log.w(TAG, "copy txt to conf");
                try {
                    FileUtils.copyFile(AppsSettings.get().getFavoriteFile().getPath(), AppsSettings.get().getSystemConfig().getPath());
                } catch (IOException e) {
                    //TODO 复制失败，直接删除?
                    FileUtils.deleteFile(AppsSettings.get().getFavoriteFile());
                }
            } else {
                Log.d(TAG, "rename txt to conf");
            }
            config = AppsSettings.get().getSystemConfig();
        } else {
            config = AppsSettings.get().getSystemConfig();
        }
        if (!config.exists()) {
            Log.w(TAG, "config is no exists:" + config.getPath());
            return;
        }
        //Log.d(TAG, "load favorites:"+config.getPath());
        lines = FileUtils.readLines(config.getPath(), Constants.DEF_ENCODING);
        for (String line : lines) {
            String tmp = line.trim();
            if (TextUtils.isDigitsOnly(tmp)) {
                mList.add(Integer.parseInt(tmp));
            }
        }
        Log.d(TAG, "load favorites success:"+mList.size());
    }

    public void save() {
        List<String> ret = new ArrayList<>();
        for (Integer id : mList) {
            ret.add(String.valueOf(id));
        }
        File conf = AppsSettings.get().getSystemConfig();
        FileUtils.writeLines(conf.getPath(), ret, Constants.DEF_ENCODING, "\n");
    }
}

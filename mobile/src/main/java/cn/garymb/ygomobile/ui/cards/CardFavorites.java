package cn.garymb.ygomobile.ui.cards;

import static cn.garymb.ygomobile.ui.home.HomeActivity.pre_code_list;
import static cn.garymb.ygomobile.ui.home.HomeActivity.released_code_list;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.newIDsArray;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.oldIDsArray;

import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.loader.ICardSearcher;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.hutool.core.util.ArrayUtil;
import ocgcore.data.Card;

/**
 * 静态类，
 */
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

    /**
     * 从cardLoader查询收藏的卡片
     *
     * @param cardLoader
     * @return 排序后的列表
     */
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
        List<String> lines;
        File config = AppsSettings.get().getSystemConfig();
        if (!config.exists()) {
            Log.w(TAG, "config is no exists:" + config.getPath());
            return;
        }
        //Log.d(TAG, "load favorites:"+config.getPath());
        lines = FileUtils.readLines(config.getPath(), Constants.DEF_ENCODING);
        for (String line : lines) {
            String tmp = line.trim();
            if (!tmp.isEmpty() && TextUtils.isDigitsOnly(tmp)) {
                Integer id = Integer.parseInt(tmp);
                if (released_code_list.contains(id)) {//先查看id对应的卡片密码是否在正式数组中存在
                    id = pre_code_list.get(released_code_list.indexOf(id));//替换成对应先行数组里的code
                }//执行完后变成先行密码，如果constants对照表里存在该密码，则如下又转换一次，所以发布app后必须及时更新在线对照表
                if (ArrayUtil.contains(oldIDsArray, id)) {
                    id = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, id));
                }
                mList.add(id);
            }
        }
        Log.d(TAG, "load favorites success:" + mList.size());
    }

    /**
     * 将卡片收藏保存到sharedStorage中
     */
    public void save() {
        List<String> ret = new ArrayList<>();
        if (!mList.isEmpty()) {
            for (Integer id : mList) {
                ret.add(String.valueOf(id));
            }
            File conf = AppsSettings.get().getSystemConfig();
            FileUtils.writeLines(conf.getPath(), ret, Constants.DEF_ENCODING, "\n");
        } else {
            load();
        }
    }
}

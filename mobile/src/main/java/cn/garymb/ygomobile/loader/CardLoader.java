package cn.garymb.ygomobile.loader;

import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.CardSort;
import cn.garymb.ygomobile.utils.LogUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

/**
 * 包括LimitManager、CardManager、LimitList
 * <p>
 * LimitList负责判断禁止卡等
 * LimitManager、CardManager已封装成单例，使用时不需要构造实例
 */
public class CardLoader implements ICardSearcher {
    private final LimitManager mLimitManager;
    private final CardManager mCardManager;
    private CallBack mCallBack;
    private LimitList mLimitList;
    private LimitList mGenesys_LimitList;
    private static final String TAG = CardLoader.class.getSimpleName();
    private final static boolean DEBUG = false;

    public interface CallBack {
        void onSearchStart();

        void onLimitListChanged(LimitList limitList);

        void onSearchResult(List<Card> Cards, boolean isHide);

        void onResetSearch();
    }

    public CardLoader() {
        mLimitManager = DataManager.get().getLimitManager();
        mCardManager = DataManager.get().getCardManager();

        // 读取上次使用的LimitList，如果有非空值存在且和禁卡表列表中有相同名称对应，则使用，否则设置第一个禁卡表
        mLimitList = mLimitManager.getLastLimit() != null ? mLimitManager.getLastLimit() : mLimitManager.getTopLimit();
        mGenesys_LimitList = mLimitManager.getLastGenesysLimit() != null ? mLimitManager.getLastGenesysLimit() : mLimitManager.getGenesysTopLimit();
    }

    @Override
    public void setLimitList(LimitList limitList) {
        if (limitList != null) {
            if(limitList.getCreditLimits() != null) {
                mGenesys_LimitList = limitList;
                AppsSettings.get().setLastGenesysLimit(limitList.getName());
                AppsSettings.get().setGenesysMode(1);
            } else {
                mLimitList = limitList;
                AppsSettings.get().setLastLimit(limitList.getName());
                AppsSettings.get().setGenesysMode(0);
            }
        }
    }

    /**
     * @param ids
     * @param isSorted
     * @return
     */
    @Override
    public SparseArray<Card> readCards(List<Integer> ids, boolean isSorted) {
        if (!isOpen()) {
            return null;
        }
        SparseArray<Card> map = new SparseArray<>();
        if (isSorted) {
            for (Integer id : ids) {
                if (id != 0) {
                    map.put(id, mCardManager.getCard(id));
                }
            }
        } else {
            for (int i = 0; i < ids.size(); i++)
                map.put(i, mCardManager.getCard(ids.get(i)));
        }
        return map;
    }

    @Override
    public boolean isOpen() {
        return mCardManager.getCount() > 0;
    }

    public void setCallBack(CallBack callBack) {
        mCallBack = callBack;
    }

    public void loadData() {
        loadData(null, null);
    }

    /**
     * 获取限制列表
     * 这即是ICardSearcher的getLimitList()映射的方法
     * @return 返回当前对象的限制列表，非空
     */
    @Override
    public @NonNull
    LimitList getLimitList() {
        return mLimitList;
    }

    @Override
    public @NonNull
    LimitList getGenesysLimitList() {
        return mGenesys_LimitList;
    }

    public SparseArray<Card> readAllCardCodes() {
        if (DEBUG) {
            SparseArray<Card> tmp = new SparseArray<>();
            tmp.put(269012, new Card(269012).type(524290L));
            tmp.put(27551, new Card(27551).type(131076L));
            tmp.put(32864, new Card(32864).type(131076L));
            tmp.put(62121, new Card(62121).type(131076L));
            tmp.put(135598, new Card(135598).type(131076L));
            return tmp;
        } else {
            return mCardManager.getAllCards();
        }
    }

    private void loadData(List<CardSearchInfo> searchInfos, List<Integer> inCards) {
        if (!isOpen()) {
            return;
        }
        if (Constants.DEBUG)
            LogUtil.i(TAG, "searchInfo=" + searchInfos);
        if (mCallBack != null) {
            mCallBack.onSearchStart();
        }
        //Dialog wait = DialogPlus.show(context, null, context.getString(R.string.searching));
        VUiKit.defer().when(() -> {
            SparseArray<Card> cards = mCardManager.getAllCards();
            List<Card> list = new ArrayList<>();
            List<Card> keywordtmp = new ArrayList<>();
            for (int i = 0; i < cards.size(); i++) {
                Card card = cards.valueAt(i);
                if (inCards != null && !inCards.contains(card.getCode())) {
                    continue;
                }
                if (searchInfos != null && card.Name.equals(searchInfos.get(0).getKeyWord().getValue())) {
                    cards.remove(i);
                    keywordtmp.add(card);
                    continue;//避免重复
                }
                if (searchInfos == null || searchInfos.stream().anyMatch(searchInfo -> searchInfo.isValid(card))) {
                    list.add(card);
                }
            }
            Collections.sort(list, CardSort.ASC);
            keywordtmp.addAll(list);
            return keywordtmp;
        }).fail((e) -> {
            if (mCallBack != null) {
                ArrayList<Card> noting = new ArrayList<Card>();
                mCallBack.onSearchResult(noting, false);
            }
            LogUtil.e("cc", "search", e);
            //wait.dismiss();
        }).done((tmp) -> {
            if (mCallBack != null) {
                mCallBack.onSearchResult(tmp, false);
            }
            //wait.dismiss();
        });
    }

    @Override
    public List<Card> sort(List<Card> cards) {
        cards.sort(CardSort.ASC);
        return cards;
    }

    private static final Comparator<Card> ASCode = (o1, o2) -> o1.Code - o2.Code;

    private static final Comparator<Card> ASC = (o1, o2) -> {
        if (o1.getStar() == o2.getStar()) {
            if (o1.Attack == o2.Attack) {
                return o2.Code - o1.Code;
            } else {
                return o2.Attack - o1.Attack;
            }
        } else {
            return o2.getStar() - o1.getStar();
        }
    };

    @Override
    public void onReset() {
        if (mCallBack != null) {
            mCallBack.onResetSearch();
        }
    }

    @Override
    public void search(List<CardSearchInfo> searchInfos) {
        String limitName = searchInfos.get(0).getLimitName();
        int limit = searchInfos.get(0).getLimitType();
        LimitList limitList = null;
        List<Integer> inCards = null;
        if (!TextUtils.isEmpty(limitName)) {
            if(limitName.toLowerCase().contains("genesys")) {
                limitList = mLimitManager.getGenesysLimit(limitName);
            } else {
                limitList = mLimitManager.getLimit(limitName);
            }

            setLimitList(limitList);
            if (limitList != null) {
                LimitType cardLimitType = LimitType.valueOf(limit);
                List<Integer> ids;
                if (cardLimitType == LimitType.Forbidden) {
                    ids = limitList.forbidden;
                } else if (cardLimitType == LimitType.Limit) {
                    ids = limitList.limit;
                } else if (cardLimitType == LimitType.SemiLimit) {
                    ids = limitList.semiLimit;
                } else if (cardLimitType == LimitType.GeneSys) {
                    ids = limitList.getGeneSysCodeList();
                } else if (cardLimitType == LimitType.All) {
                    ids = limitList.getCodeList();
                } else {
                    ids = null;
                }
                inCards = ids;
            }
        } else {
            setLimitList(null);
        }
        loadData(searchInfos, inCards);
    }

    @Override
    public void search(CardSearchInfo searchInfo) {
        search(new ArrayList<>(Arrays.asList(searchInfo)));
    }
}

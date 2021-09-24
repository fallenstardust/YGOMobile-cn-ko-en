package cn.garymb.ygomobile.loader;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.CardSort;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

public class CardLoader implements ICardSearcher {
    private final LimitManager mLimitManager;
    private final CardManager mCardManager;
    private final StringManager mStringManager;
    private final Context context;
    private CallBack mCallBack;
    private LimitList mLimitList;
    private static final String TAG = CardLoader.class.getSimpleName();
    private final static boolean DEBUG = false;

    public interface CallBack {
        void onSearchStart();

        void onLimitListChanged(LimitList limitList);

        void onSearchResult(List<Card> Cards, boolean isHide);

        void onResetSearch();
    }

    public CardLoader(Context context) {
        this.context = context;
        mLimitManager = DataManager.get().getLimitManager();
        mCardManager = DataManager.get().getCardManager();
        mStringManager = DataManager.get().getStringManager();
        mLimitList = mLimitManager.getTopLimit();
    }

    @Override
    public void setLimitList(LimitList limitList) {
        mLimitList = limitList;
        if (mCallBack != null) {
            mCallBack.onLimitListChanged(limitList);
        }
    }

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

    @Override
    public @NonNull
    LimitList getLimitList() {
        return mLimitList;
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

    private void loadData(CardSearchInfo searchInfo, List<Integer> inCards) {
        if (!isOpen()) {
            return;
        }
        if (Constants.DEBUG)
            Log.i(TAG, "searchInfo=" + searchInfo);
        if (mCallBack != null) {
            mCallBack.onSearchStart();
        }
        Dialog wait = DialogPlus.show(context, null, context.getString(R.string.searching));
        VUiKit.defer().when(() -> {
            SparseArray<Card> cards = mCardManager.getAllCards();
            List<Card> list = new ArrayList<>();
            for (int i = 0; i < cards.size(); i++) {
                Card card = cards.valueAt(i);
                //指定搜索范围
                if (inCards != null && (!inCards.contains(card.Code) && !inCards.contains(card.Alias))) {
                    continue;
                }
                if (searchInfo == null || searchInfo.isValid(card)) {
                    list.add(card);
                }
            }
            Collections.sort(list, CardSort.ASC);
            return list;
        }).fail((e) -> {
            if (mCallBack != null) {
                ArrayList<Card> noting = new ArrayList<Card>();
                mCallBack.onSearchResult(noting, false);
            }
            Log.e("kk", "search", e);
            wait.dismiss();
        }).done((tmp) -> {
            if (mCallBack != null) {
                mCallBack.onSearchResult(tmp, false);
            }
            wait.dismiss();
        });
    }

    @Override
    public List<Card> sort(List<Card> cards){
        Collections.sort(cards, CardSort.ASC);
        return cards;
    }

    private static final Comparator<Card> ASCode = (o1, o2) -> o1.Code - o2.Code;

    private static final Comparator<Card> ASC = (o1, o2) -> {
        if (o1.getStar() == o2.getStar()) {
            if (o1.Attack == o2.Attack) {
                return (int) (o2.Code - o1.Code);
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
    public void search(CardSearchInfo searchInfo) {
        String limitName = searchInfo.getLimitName();
        int limit = searchInfo.getLimitType();
        LimitList limitList = null;
        List<Integer> inCards = null;
        if (!TextUtils.isEmpty(limitName)) {
            limitList = mLimitManager.getLimit(limitName);
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
        loadData(searchInfo, inCards);
    }
}

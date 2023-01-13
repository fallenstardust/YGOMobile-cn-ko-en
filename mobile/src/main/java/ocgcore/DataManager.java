package ocgcore;

import android.util.Log;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.loader.CardLoader;

public class DataManager {
    private static DataManager sLoader = null;

    public static DataManager get() {
        if (sLoader != null) {
            return sLoader;
        }
        synchronized (CardLoader.class) {
            if (sLoader == null) {
                sLoader = new DataManager();
            }
        }
        return sLoader;
    }

    private final StringManager mStringManager;
    private final LimitManager mLimitManager;
    private final CardManager mCardManager;

    private DataManager() {
        mStringManager = new StringManager();
        mLimitManager = new LimitManager();
        mCardManager = new CardManager(
                AppsSettings.get().getDataBasePath(),
                AppsSettings.get().getExpansionsPath().getAbsolutePath());
    }

    public StringManager getStringManager() {
        return mStringManager;
    }

    public LimitManager getLimitManager() {
        return mLimitManager;
    }

    public CardManager getCardManager() {
        return mCardManager;
    }

    private boolean mInit;

    public void load(boolean force) {

        Log.i("webCrawler", "DataManager load data");
        boolean needLoad = false;
        synchronized (this) {
            if (!mInit || force) {
                needLoad = true;
            }
            mInit = true;
        }
        if(needLoad) {
            mStringManager.load();
            mLimitManager.load();
            mCardManager.loadCards();
        }
    }
}

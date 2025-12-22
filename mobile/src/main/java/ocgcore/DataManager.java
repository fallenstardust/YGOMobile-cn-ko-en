package ocgcore;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.loader.CardLoader;

/**
 * 单例模式，使用get()方式自动获取单例
 * 其field包括StringManager、PackManager、LimitManager、CardManager
 */
public class DataManager {
    private static DataManager sLoader = null;

    private static final String TAG = String.valueOf(DataManager.class);

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
    private final PackManager mPackManager;
    private final LimitManager mLimitManager;
    private final CardManager mCardManager;

    private DataManager() {
        mStringManager = new StringManager();
        mPackManager = new PackManager();
        mLimitManager = new LimitManager();
        mCardManager = new CardManager(
                AppsSettings.get().getDataBasePath(),
                AppsSettings.get().getExpansionsPath().getAbsolutePath());
    }

    public StringManager getStringManager() {
        return mStringManager;
    }

    public PackManager getPackManager() {
        return mPackManager;
    }

    public LimitManager getLimitManager() {
        return mLimitManager;
    }

    public CardManager getCardManager() {
        return mCardManager;
    }

    private boolean mInit;

    public void load() {
        //LogUtil.i("webCrawler", "DataManager load data");
        boolean needLoad = false;
        synchronized (this) {
            if (!mInit) {
                needLoad = true;
            }
            mInit = true;
        }
        if (needLoad) {
            mStringManager.load();
            mPackManager.load();
            mLimitManager.load();
            mCardManager.loadCards();
        }
    }
}

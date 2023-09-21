package ocgcore;

import org.greenrobot.eventbus.EventBus;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.events.ExCardEvent;
import cn.garymb.ygomobile.loader.CardLoader;

/**
 * 单例模式，使用get()方式自动获取单例
 * 其field包括StringManager、LimitManager、CardManager
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
        //LogUtil.i("webCrawler", "DataManager load data");
        boolean needLoad = false;
        synchronized (this) {
            if (!mInit || force) {
                needLoad = true;
            }
            mInit = true;
        }
        if (needLoad) {
            mStringManager.load();
            mLimitManager.load();
            mCardManager.loadCards();
        }
    }
}

package ocgcore;

import android.text.TextUtils;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.loader.CardLoader;
import ocgcore.data.Card;

/**
 * 单例模式，使用get()方式自动获取单例
 * 其field包括StringManager、PackManager、LimitManager、CardManager
 */
public class DataManager {
    private static DataManager sLoader = null;

    private static final String TAG = String.valueOf(DataManager.class);

    /** 对齐 C++ data_manager.h MAX_STRING_ID：<=0x7ff 为系统字符串，大于则为 卡号*16+n */
    public static final int MAX_STRING_ID = 0x7ff;

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
            mPackManager.load();
            mLimitManager.load();
            mCardManager.loadCards();
        }
    }

    /**
     * 对齐 C++ DataManager::GetDesc（data_manager.cpp L314-325）：
     * desc <= MAX_STRING_ID 取系统字符串（strings.conf）；
     * 否则高 28 位为卡片代码、低 4 位为 str 索引(0~15)，
     * 取该卡缓存的脚本提示文字（cdb texts.str1~str16，见 Card.Stras）。
     * 通讯无法解析时（desc 为负数高位）按无符号处理，与 C++ uint32_t 行为一致。
     */
    public String getDesc(int descRaw, String def) {
        long desc = descRaw & 0xFFFFFFFFL;
        if (desc <= MAX_STRING_ID) {
            return mStringManager.getSystemString((int) desc, def);
        }
        int code = (int) ((desc >> 4) & 0x0fffffffL);
        int offset = (int) (desc & 0xf);
        Card card = mCardManager.getCard(code);
        if (card != null && card.Strs != null && offset >= 0 && offset < card.Strs.length) {
            String str = card.Strs[offset];
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }
        return def;
    }
}

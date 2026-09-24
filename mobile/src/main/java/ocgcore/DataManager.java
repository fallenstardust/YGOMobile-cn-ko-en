package ocgcore;

import android.text.TextUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.loader.CardLoader;
import ocgcore.data.Card;
import ocgcore.enums.CardLocation;

/**
 * 单例模式，使用get()方式自动获取单例
 * 其field包括StringManager、PackManager、LimitManager、CardManager
 */
public class DataManager {
    private static DataManager sLoader = null;

    private static final String TAG = String.valueOf(DataManager.class);

    /** 对齐 C++ data_manager.h MAX_STRING_ID：<=0x7ff 为系统字符串，大于则为 卡号*16+n */
    public static final int MAX_STRING_ID = 0x7ff;

    /** 对齐 C++ data_manager.h unknown_string */
    public static final String UNKNOWN_STRING = "???";

    /** 对齐 C++ data_manager.h STRING_ID_LOCATION：!system 1000~1009 = 卡组/手卡/怪兽区/… */
    public static final int STRING_ID_LOCATION = 1000;

    /** 对齐 ocgcore common.h LOCATION_SZONE(0x08)：复用已有枚举，避免重复魔数 */
    private static final int LOCATION_SZONE = CardLocation.SpellZone.value();

    /**
     * C printf 转换符：%[flags][width][.precision][长度修饰符]转换字符
     * strings.conf 里主要是 %ls / %d / %u / %x / %%
     */
    private static final Pattern C_FORMAT_SPEC =
            Pattern.compile("%([-+ #0,]*)(\\d*)(\\.\\d+)?([hlLzjt]*)([a-zA-Z%])");

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

    /** 对齐 data_manager.cpp DataManager::GetName(code) */
    public String getName(int code) {
        if (code <= 0) {
            return UNKNOWN_STRING;
        }
        Card card = mCardManager.getCard(code);
        if (card != null && !TextUtils.isEmpty(card.Name)) {
            return card.Name;
        }
        return UNKNOWN_STRING;
    }

    /** 对齐 data_manager.cpp DataManager::GetCounterName(countertype) */
    public String getCounterName(int counterType) {
        return mStringManager.getCounterName(counterType, UNKNOWN_STRING);
    }

    /**
     * 对齐 data_manager.cpp DataManager::FormatLocation(location, sequence)（L383-403）：
     * LOCATION_SZONE 时按 sequence 细分为 魔法陷阱区(1003)/场地区(1008)/灵摆区(1009)，
     * 其余按位序映射到 1000+i，无匹配返回 "???"
     */
    public String formatLocation(int location, int sequence) {
        if (location == LOCATION_SZONE) {
            if (sequence < 5) {
                return mStringManager.getSystemString(1003, UNKNOWN_STRING);
            }
            if (sequence == 5) {
                return mStringManager.getSystemString(1008, UNKNOWN_STRING);
            }
            return mStringManager.getSystemString(1009, UNKNOWN_STRING);
        }
        for (int i = 0; i < 10; i++) {
            if ((0x1 << i) == location) {
                return mStringManager.getSystemString(STRING_ID_LOCATION + i, UNKNOWN_STRING);
            }
        }
        return UNKNOWN_STRING;
    }

    /**
     * 取系统字符串并用通讯参数替换其中的 C 格式符（%ls / %d / %u / %x）。
     * 对齐 gframe 各调用点的 myswprintf(buf, GetSysString(id), args...)。
     * 注意 defText 里请写 Java 风格的 %s/%d，它同样会经过格式化。
     */
    public String formatSystemString(int index, String defText, Object... args) {
        return cFormat(mStringManager.getSystemString(index, defText), args);
    }

    /** 取 desc（系统字符串或卡片脚本提示）并格式化；C++ 多数场合把 GetDesc 结果当数据打印，需要格式化时才用本方法 */
    public String formatDesc(int descRaw, String defText, Object... args) {
        return cFormat(getDesc(descRaw, defText), args);
    }

    /**
     * 用 C printf 语义格式化字符串。
     * Java 的 Formatter 不认长度修饰符（%ls 会抛 UnknownFormatConversionException: 'l'），
     * 因此先把 C 转换符翻译为 Java 转换符；仍失败时退化为纯文本按序替换，保证不会漏出 "%ls"。
     */
    public static String cFormat(String format, Object... args) {
        if (TextUtils.isEmpty(format)) {
            return format == null ? "" : format;
        }
        if (args == null) {
            args = new Object[0];
        }
        Matcher m = C_FORMAT_SPEC.matcher(format);
        StringBuilder out = new StringBuilder();
        int last = 0;
        int used = 0;
        boolean found = false;
        while (m.find()) {
            // 原文中未被识别的裸 '%' 转义为 "%%"，避免 Formatter 抛 UnknownFormatConversion
            out.append(escapePercent(format.substring(last, m.start())));
            String javaSpec = toJavaSpec(m.group(1), m.group(2), m.group(3), m.group(5).charAt(0));
            out.append(javaSpec);
            if (!"%%".equals(javaSpec)) {
                used++;
            }
            last = m.end();
            found = true;
        }
        if (!found) {
            // 无格式符：原样返回（此时文本里的 '%' 是普通字符）
            return format;
        }
        out.append(escapePercent(format.substring(last)));
        Object[] real = new Object[used];
        for (int i = 0; i < used; i++) {
            real[i] = i < args.length && args[i] != null ? args[i] : "";
        }
        try {
            return String.format(Locale.US, out.toString(), real);
        } catch (Exception e) {
            return fallbackReplace(format, real);
        }
    }

    private static String escapePercent(String text) {
        return text.indexOf('%') < 0 ? text : text.replace("%", "%%");
    }

    /**
     * C 转换符 → Java 转换符：去掉长度修饰符，i/u→d，s/S/c/C/b/B/p 及其它一律按字符串处理。
     * '#' 标志仅对 x/X/o 合法；字符串转换不支持 0/+/,/# 等数字标志。
     */
    private static String toJavaSpec(String flags, String width, String precision, char conv) {
        char c;
        boolean numeric;
        switch (conv) {
            case 'd':
            case 'i':
            case 'u':
                c = 'd';
                numeric = true;
                break;
            case 'x':
            case 'X':
            case 'o':
                c = conv;
                numeric = true;
                break;
            case 'f':
            case 'F':
            case 'e':
            case 'E':
            case 'g':
            case 'G':
            case 'a':
            case 'A':
                c = Character.toLowerCase(conv);
                numeric = true;
                break;
            case '%':
                return "%%";
            default:
                c = 's';
                numeric = false;
                break;
        }
        StringBuilder sb = new StringBuilder("%");
        if (numeric) {
            for (int i = 0; i < flags.length(); i++) {
                char f = flags.charAt(i);
                if (f == '#' && c != 'x' && c != 'X' && c != 'o') {
                    continue;
                }
                sb.append(f);
            }
        } else if (flags.indexOf('-') >= 0) {
            sb.append('-');
        }
        sb.append(width == null ? "" : width);
        sb.append(precision == null ? "" : precision);
        sb.append(c);
        return sb.toString();
    }

    /**
     * 兜底：不走 Formatter，直接按出现顺序把每个转换符替换成对应参数。
     * 参数类型不匹配 / 个数不足时也能出可读文本，绝不会残留 "%ls"。
     */
    private static String fallbackReplace(String format, Object[] args) {
        Matcher m = C_FORMAT_SPEC.matcher(format);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        int i = 0;
        while (m.find()) {
            sb.append(format, last, m.start());
            String javaSpec = toJavaSpec(m.group(1), m.group(2), m.group(3), m.group(5).charAt(0));
            if ("%%".equals(javaSpec)) {
                sb.append('%');
            } else {
                sb.append(i < args.length && args[i] != null ? String.valueOf(args[i]) : "");
                i++;
            }
            last = m.end();
        }
        sb.append(format, last, format.length());
        return sb.toString();
    }
}

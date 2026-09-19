package cn.garymb.ygomobile.engine;

import android.util.Log;

/**
 * 薄 JNI 决斗引擎门面（libygoengine.so）。
 *
 * <p>本类是 ocgcore 引擎全部 native 方法的<b>唯一</b>声明处，调用方（局域网服务器的
 * ServerDuel、残局、人机）只通过它访问引擎，不直接接触 JNI。
 *
 * <p>库缺失（未打包 / 非 ARM ABI）时 {@link #isAvailable()} 返回 false，
 * 所有 native 包装方法在未加载时安全返回默认值，避免 {@link UnsatisfiedLinkError}
 * 令调用线程静默崩溃。
 *
 * <p>典型驱动顺序（无状态转发，与 gframe single_duel.cpp 对偶）：
 * <pre>
 *   OcgDuelEngine.init(rootPath, cdbPaths);
 *   long duel = OcgDuelEngine.createDuelV2(seed8);
 *   OcgDuelEngine.setPlayerInfo(duel, 0, lp, hand, draw);
 *   OcgDuelEngine.setPlayerInfo(duel, 1, lp, hand, draw);
 *   for (code : deckMain)  OcgDuelEngine.newCard(duel, code, p, p, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
 *   for (code : deckExtra) OcgDuelEngine.newCard(duel, code, p, p, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
 *   OcgDuelEngine.startDuel(duel, options);
 *   while (running) {
 *       int r = OcgDuelEngine.process(duel);
 *       int len = r &amp; PROCESSOR_BUFFER_LEN;
 *       if (len &gt; 0) handleMessage(OcgDuelEngine.getMessage(duel));
 *       ...
 *   }
 *   OcgDuelEngine.endDuel(duel);
 * </pre>
 */
public final class OcgDuelEngine {
    private static final String TAG = "OcgDuelEngine";

    private static final boolean LOADED = loadLibraryQuietly();

    // ===== process() 返回值解码（对齐 common.h） =====
    public static final int PROCESSOR_BUFFER_LEN = 0x0fffffff;
    public static final int PROCESSOR_FLAG = 0xf0000000;
    public static final int PROCESSOR_NONE = 0;
    public static final int PROCESSOR_WAITING = 0x10000000;
    public static final int PROCESSOR_END = 0x20000000;

    // ===== 卡片位置（对齐 common.h LOCATION_*） =====
    public static final int LOCATION_DECK = 0x01;
    public static final int LOCATION_HAND = 0x02;
    public static final int LOCATION_MZONE = 0x04;
    public static final int LOCATION_SZONE = 0x08;
    public static final int LOCATION_GRAVE = 0x10;
    public static final int LOCATION_REMOVED = 0x20;
    public static final int LOCATION_EXTRA = 0x40;

    // ===== 布阵姿态（对齐 common.h POS_*） =====
    public static final int POS_FACEUP_ATTACK = 0x1;
    public static final int POS_FACEDOWN_ATTACK = 0x2;
    public static final int POS_FACEUP_DEFENSE = 0x4;
    public static final int POS_FACEDOWN_DEFENSE = 0x8;

    // ===== 决斗规则位（对齐 common.h DUEL_*） =====
    public static final int DUEL_PSEUDO_SHUFFLE = 0x10;
    public static final int DUEL_TAG_MODE = 0x20;

    // ===== 查询字段掩码（对齐 common.h QUERY_*） =====
    public static final int QUERY_CODE = 0x1;
    public static final int QUERY_POSITION = 0x2;
    public static final int QUERY_ALIAS = 0x4;
    public static final int QUERY_TYPE = 0x8;
    public static final int QUERY_LEVEL = 0x10;
    public static final int QUERY_RANK = 0x20;
    public static final int QUERY_ATTRIBUTE = 0x40;
    public static final int QUERY_RACE = 0x80;
    public static final int QUERY_ATTACK = 0x100;
    public static final int QUERY_DEFENSE = 0x200;
    public static final int QUERY_BASE_ATTACK = 0x400;
    public static final int QUERY_BASE_DEFENSE = 0x800;
    public static final int QUERY_REASON = 0x1000;
    public static final int QUERY_REASON_CARD = 0x2000;
    public static final int QUERY_EQUIP_CARD = 0x4000;
    public static final int QUERY_TARGET_CARD = 0x8000;
    public static final int QUERY_OVERLAY_CARD = 0x10000;
    public static final int QUERY_COUNTERS = 0x20000;
    public static final int QUERY_OWNER = 0x40000;
    public static final int QUERY_STATUS = 0x80000;
    public static final int QUERY_LSCALE = 0x200000;
    public static final int QUERY_RSCALE = 0x400000;
    public static final int QUERY_LINK = 0x800000;

    private OcgDuelEngine() {
    }

    private static boolean loadLibraryQuietly() {
        try {
            System.loadLibrary("ygoengine");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "libygoengine.so 未加载，决斗引擎不可用", t);
            return false;
        }
    }

    /** 引擎是否可用（native 库是否成功加载）。 */
    public static boolean isAvailable() {
        return LOADED;
    }

    /** 不可用时抛异常前统一走此判断，返回 false 表示调用方应放弃并给出用户反馈。 */
    private static boolean notReady(String op) {
        if (LOADED) {
            return false;
        }
        Log.w(TAG, "引擎不可用，忽略调用: " + op);
        return true;
    }

    // ==================== native 方法（唯一声明处） ====================

    private static native boolean nativeInit(String rootPath, String[] cdbPaths);

    private static native void nativeReloadCards(String[] cdbPaths);

    private static native long nativeCreateDuelV2(int[] seedSequence);

    private static native long nativeCreateDuel(int seed);

    private static native void nativeSetPlayerInfo(long duel, int playerid, int lp, int startCount,
            int drawCount);

    private static native void nativeNewCard(long duel, int code, int owner, int controller,
            int location, int sequence, int position);

    private static native void nativeNewTagCard(long duel, int code, int owner, int location);

    private static native void nativeStartDuel(long duel, int options);

    private static native void nativeEndDuel(long duel);

    private static native int nativeProcess(long duel);

    private static native byte[] nativeGetMessage(long duel);

    private static native void nativeSetResponseI(long duel, int value);

    private static native void nativeSetResponseB(long duel, byte[] resp);

    private static native int nativeQueryFieldCount(long duel, int player, int location);

    private static native byte[] nativeQueryFieldCard(long duel, int player, int location, int flag,
            int useCache);

    private static native byte[] nativeQueryCard(long duel, int player, int location, int sequence,
            int flag, int useCache);

    private static native byte[] nativeQueryFieldInfo(long duel);

    private static native int nativePreloadScript(long duel, String scriptName);

    // ==================== 安全包装 ====================

    /**
     * 初始化引擎。
     *
     * @param rootPath 资源根目录（其下含 script/、single/，可选 expansions/）
     * @param cdbPaths 卡片数据库绝对路径；主库放最后（覆盖扩展同名卡）
     * @return 成功返回 true；库不可用或加载失败返回 false
     */
    public static boolean init(String rootPath, String[] cdbPaths) {
        if (notReady("init") || rootPath == null) {
            return false;
        }
        try {
            return nativeInit(rootPath, cdbPaths == null ? new String[0] : cdbPaths);
        } catch (Throwable t) {
            Log.e(TAG, "nativeInit 失败", t);
            return false;
        }
    }

    /** 重新装载卡片数据（卡组/扩展变更后），沿用已设置的 rootPath。 */
    public static void reloadCards(String[] cdbPaths) {
        if (notReady("reloadCards")) {
            return;
        }
        try {
            nativeReloadCards(cdbPaths == null ? new String[0] : cdbPaths);
        } catch (Throwable t) {
            Log.e(TAG, "nativeReloadCards 失败", t);
        }
    }

    /** 以 8×32 位种子序列创建决斗（v2 随机源，对齐 create_duel_v2）。返回句柄，失败返回 0。 */
    public static long createDuelV2(int[] seedSequence) {
        if (notReady("createDuelV2")) {
            return 0L;
        }
        return nativeCreateDuelV2(seedSequence);
    }

    /** 以单一种子创建决斗（v1 随机源，兼容旧回放/残局）。返回句柄，失败返回 0。 */
    public static long createDuel(int seed) {
        if (notReady("createDuel")) {
            return 0L;
        }
        return nativeCreateDuel(seed);
    }

    public static void setPlayerInfo(long duel, int playerid, int lp, int startCount, int drawCount) {
        if (notReady("setPlayerInfo") || duel == 0L) {
            return;
        }
        nativeSetPlayerInfo(duel, playerid, lp, startCount, drawCount);
    }

    public static void newCard(long duel, int code, int owner, int controller, int location,
            int sequence, int position) {
        if (notReady("newCard") || duel == 0L) {
            return;
        }
        nativeNewCard(duel, code, owner, controller, location, sequence, position);
    }

    public static void newTagCard(long duel, int code, int owner, int location) {
        if (notReady("newTagCard") || duel == 0L) {
            return;
        }
        nativeNewTagCard(duel, code, owner, location);
    }

    public static void startDuel(long duel, int options) {
        if (notReady("startDuel") || duel == 0L) {
            return;
        }
        nativeStartDuel(duel, options);
    }

    public static void endDuel(long duel) {
        if (notReady("endDuel") || duel == 0L) {
            return;
        }
        nativeEndDuel(duel);
    }

    /** 推进引擎，返回 process 结果（用 {@link #PROCESSOR_FLAG}/{@link #PROCESSOR_BUFFER_LEN} 解码）。 */
    public static int process(long duel) {
        if (notReady("process") || duel == 0L) {
            return PROCESSOR_END;
        }
        return nativeProcess(duel);
    }

    /** 取出一条引擎消息；无消息或不可用时返回空数组。 */
    public static byte[] getMessage(long duel) {
        if (notReady("getMessage") || duel == 0L) {
            return new byte[0];
        }
        byte[] msg = nativeGetMessage(duel);
        return msg == null ? new byte[0] : msg;
    }

    public static void setResponseI(long duel, int value) {
        if (notReady("setResponseI") || duel == 0L) {
            return;
        }
        nativeSetResponseI(duel, value);
    }

    public static void setResponseB(long duel, byte[] resp) {
        if (notReady("setResponseB") || duel == 0L) {
            return;
        }
        nativeSetResponseB(duel, resp);
    }

    public static int queryFieldCount(long duel, int player, int location) {
        if (notReady("queryFieldCount") || duel == 0L) {
            return 0;
        }
        return nativeQueryFieldCount(duel, player, location);
    }

    /**
     * 查询某玩家某区域全部卡片数据（对齐 query_field_card）。
     * 返回字段数据块序列（不含头部），每块 {@code [int32 clen(含自身)][int32 flag][code][position]...}。
     * flag 会自动并入 QUERY_CODE|QUERY_POSITION；useCache：1=用缓存（默认），0=强制刷新（洗牌等）。
     */
    public static byte[] queryFieldCard(long duel, int player, int location, int flag, int useCache) {
        if (notReady("queryFieldCard") || duel == 0L) {
            return new byte[0];
        }
        byte[] buf = nativeQueryFieldCard(duel, player, location, flag | (QUERY_CODE | QUERY_POSITION), useCache);
        return buf == null ? new byte[0] : buf;
    }

    /**
     * 查询单张卡片数据（对齐 query_card）。返回字段数据块（不含头部）。
     * flag 自动并入 QUERY_CODE|QUERY_POSITION；RefreshSingle 需 useCache=0。
     */
    public static byte[] queryCard(long duel, int player, int location, int sequence, int flag, int useCache) {
        if (notReady("queryCard") || duel == 0L) {
            return new byte[0];
        }
        byte[] buf = nativeQueryCard(duel, player, location, sequence, flag | (QUERY_CODE | QUERY_POSITION), useCache);
        return buf == null ? new byte[0] : buf;
    }

    public static byte[] queryFieldInfo(long duel) {
        if (notReady("queryFieldInfo") || duel == 0L) {
            return new byte[0];
        }
        byte[] buf = nativeQueryFieldInfo(duel);
        return buf == null ? new byte[0] : buf;
    }

    /** 残局模式：预加载脚本（如 "./single/xxx.lua"），返回非 0 表示成功。 */
    public static int preloadScript(long duel, String scriptName) {
        if (notReady("preloadScript") || duel == 0L) {
            return 0;
        }
        return nativePreloadScript(duel, scriptName);
    }
}

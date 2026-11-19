package cn.garymb.ygomobile.engine;

import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 卡片脚本 / 卡数据加载进度的进程级聚合点（玩家等待界面进度条与开局闸门共用）。
 *
 * <p>局域网（含人机、纯单人）进入 PlayerWaitingDialog 后、资源检查仍在拷贝
 * {@code scripts.zip}/{@code cards.cdb}，或 native 引擎首次引导（扫档案 + 装载卡数据）
 * 尚未完成时：卡组里缺卡数据/脚本的卡会被 ocgcore 以空 {@code c<code>} 表永久缓存，
 * 之后资源补齐也不会再加载，表现为「有的卡能发动，有的卡只能盖放」。因此由本类把
 * 三段加载进度汇总到一起：
 * <ul>
 *   <li>脚本档案（ResCheckTask 的 scripts.zip 拷贝单元）；</li>
 *   <li>卡片数据库（ResCheckTask 的 cards.cdb 拷贝单元）；</li>
 *   <li>native 引擎引导（{@link NativeScriptBootstrap#ensureEngineReady()}）。</li>
 * </ul>
 * 三段各占 0..1，总进度取最小值（谁慢显示谁），全部到 1 才算就绪：
 * 等待界面据 {@link #snapshot()} 显示进度并在就绪前禁用“开始”，
 * 服务端开局据 {@link #awaitDuelReady(long)} 阻塞到就绪，保证不会带着半套脚本开局。
 */
public final class ScriptLoadStatus {

    private static final String TAG = "ScriptLoadStatus";

    /** snapshot.stage：脚本档案（scripts.zip 拷贝 / 旧实体目录清理） */
    public static final int STAGE_SCRIPT = 0;
    /** snapshot.stage：卡片数据库（cards.cdb 拷贝） */
    public static final int STAGE_CDB = 1;
    /** snapshot.stage：native 引擎引导（扫档案 + 装载卡数据） */
    public static final int STAGE_ENGINE = 2;

    /** 引擎引导失败后的最长重试窗口（期间 warmUp 的延迟重试仍可能成功） */
    private static final long ENGINE_FAIL_RETRY_MS = 10_000L;

    /** 某一时刻的加载进度快照（不可变，主线程直接读） */
    public static final class Snapshot {
        /** 三段是否全部就绪（唯一可安全开局的判据） */
        public final boolean ready;
        /** 0..100，就绪前恒 < 100 */
        public final int percent;
        /** 当前最拖后腿的阶段，取值 {@link #STAGE_SCRIPT}/{@link #STAGE_CDB}/{@link #STAGE_ENGINE} */
        public final int stage;
        /** 引擎引导是否已失败（就绪前为 true 时界面提示失败而非继续等） */
        public final boolean failed;

        Snapshot(boolean ready, int percent, int stage, boolean failed) {
            this.ready = ready;
            this.percent = percent;
            this.stage = stage;
            this.failed = failed;
        }
    }

    // === 资源检查（ResCheckTask）===
    /** 并发运行的检查数（检测互斥口径同 ResCheckTask.sRunningChecks，0 = 未运行） */
    private static final AtomicInteger sResCheckCount = new AtomicInteger();
    private static volatile double sScriptFrac = 1d;
    private static volatile double sCdbFrac = 1d;

    // === 引擎引导（NativeScriptBootstrap）===
    private static volatile double sEngineFrac;
    private static volatile double sEngineCreepCap = 1d;
    private static volatile long sEngineStageAt;
    private static volatile boolean sEngineLoading;
    private static volatile boolean sEngineReady;
    private static volatile boolean sEngineFailed;

    private ScriptLoadStatus() {
    }

    // ==================================================================
    // 资源检查进度（ResCheckTask 发布）
    // ==================================================================

    /** 资源检查开始：首个并发检查把脚本/卡数据库进度归零（未运行时视为已就绪） */
    public static void resourceCheckStarted() {
        if (sResCheckCount.incrementAndGet() == 1) {
            sScriptFrac = 0d;
            sCdbFrac = 0d;
        }
    }

    /** 资源检查收尾：最后一个检查结束时恢复成就绪（失败也放行，由引擎引导兜底） */
    public static void resourceCheckFinished() {
        if (sResCheckCount.decrementAndGet() <= 0) {
            sResCheckCount.set(0);
            sScriptFrac = 1d;
            sCdbFrac = 1d;
        }
    }

    /** 脚本档案单元进度（0..1，只前进不回退） */
    public static void updateScriptArchiveProgress(double frac) {
        sScriptFrac = Math.max(sScriptFrac, clampFrac(frac));
    }

    /** 卡片数据库单元进度（0..1，只前进不回退） */
    public static void updateCardDatabaseProgress(double frac) {
        sCdbFrac = Math.max(sCdbFrac, clampFrac(frac));
    }

    // ==================================================================
    // 引擎引导进度（NativeScriptBootstrap 发布）
    // ==================================================================

    /** 引擎引导开始：清掉上次失败标记，进度从 0 起 */
    static void engineLoadStarted() {
        sEngineLoading = true;
        sEngineFailed = false;
        engineProgress(0.02d, 0.05d);
    }

    /**
     * 进入新阶段：{@code base} 为阶段起点（0..1），{@code creepCap} 为阶段内允许的
     * “缓慢爬行”上限——native 装载卡数据等长阶段拿不到细分进度，按时间小幅推进，
     * 避免进度条长时间停在同一数字上看起来像卡死。
     */
    static void engineProgress(double base, double creepCap) {
        sEngineFrac = clampFrac(base);
        sEngineCreepCap = Math.max(sEngineFrac, clampFrac(creepCap));
        sEngineStageAt = SystemClock.uptimeMillis();
    }

    /** 引擎引导成功（含“已就绪”快路径）：进度定格 100% */
    static void engineLoadSucceeded() {
        sEngineReady = true;
        sEngineLoading = false;
        sEngineFailed = false;
        sEngineFrac = 1d;
        sEngineCreepCap = 1d;
    }

    /** 引擎引导失败：保留既有就绪态，未就绪时由界面提示失败 */
    static void engineLoadFailed() {
        sEngineLoading = false;
        sEngineFailed = true;
    }

    // ==================================================================
    // 快照与开局闸门
    // ==================================================================

    /** 当前进度快照（纯静态读，UI 线程可每 250ms 轮询） */
    public static Snapshot snapshot() {
        boolean resRunning = sResCheckCount.get() > 0;
        double scriptFrac = resRunning ? sScriptFrac : 1d;
        double cdbFrac = resRunning ? sCdbFrac : 1d;
        double engineFrac = currentEngineFraction();
        boolean ready = sEngineReady && scriptFrac >= 1d && cdbFrac >= 1d;
        // 选最拖后腿的阶段做文案：谁的分值最小显示谁
        int stage = STAGE_SCRIPT;
        double worst = scriptFrac;
        if (cdbFrac < worst) {
            worst = cdbFrac;
            stage = STAGE_CDB;
        }
        if (engineFrac < worst) {
            worst = engineFrac;
            stage = STAGE_ENGINE;
        }
        int percent = ready ? 100 : Math.min(99, (int) Math.floor(worst * 100d));
        boolean failed = !ready && sEngineFailed && !sEngineLoading;
        return new Snapshot(ready, percent, stage, failed);
    }

    /** 引擎阶段当前值：阶段起点 + 按停留时间的小幅爬行，封顶于 creepCap */
    private static double currentEngineFraction() {
        if (sEngineReady) return 1d;
        double f = sEngineFrac;
        if (sEngineLoading && sEngineCreepCap > f) {
            long stay = SystemClock.uptimeMillis() - sEngineStageAt;
            if (stay > 0) {
                f = Math.min(sEngineCreepCap, f + stay / 1000d * 0.006d);
            }
        }
        return clampFrac(f);
    }

    /**
     * 开局闸门（服务端房间线程调用）：阻塞到资源检查与引擎引导双双就绪。
     *
     * <p>顺序要点：资源检查未结束时**先不引导引擎**——cards.cdb 在拷贝前会被删除，
     * 此窗口内初始化只能拿到半套卡数据，随后被 {@code sInitedRootPath} 缓存成“已初始化”。
     * 等资源检查完成后本方法调用 {@link NativeScriptBootstrap#ensureEngineReady()}，
     * 既完成首次引导，也会因资源指纹变化（见 NativeScriptBootstrap）热重载卡数据。
     *
     * @param timeoutMs 最长等待时间；引擎引导已失败时提前放弃（最多再观察 10 秒重试）
     * @return 就绪返回 true；超时或引导失败返回 false
     */
    public static boolean awaitDuelReady(long timeoutMs) {
        long start = SystemClock.uptimeMillis();
        long deadline = start + Math.max(1000L, timeoutMs);
        long failDeadline = 0L;
        while (true) {
            boolean resReady = sResCheckCount.get() <= 0;
            if (resReady && NativeScriptBootstrap.ensureEngineReady()) {
                long waited = SystemClock.uptimeMillis() - start;
                if (waited > 300L) {
                    Log.i(TAG, "脚本/卡数据加载完成，开局闸门等待 " + waited + "ms");
                }
                return true;
            }
            long now = SystemClock.uptimeMillis();
            if (sEngineFailed && !sEngineLoading) {
                // 已失败：给 warmUp 的延迟重试留一个短窗口，超出即判定不可用
                if (failDeadline == 0L) {
                    failDeadline = now + ENGINE_FAIL_RETRY_MS;
                } else if (now >= failDeadline) {
                    Log.e(TAG, "引擎引导持续失败，放弃等待（开局终止）");
                    return false;
                }
            }
            if (now >= deadline) {
                Log.e(TAG, "等待脚本/卡数据加载超时（" + (now - start) + "ms），放弃开局");
                return false;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static double clampFrac(double frac) {
        if (Double.isNaN(frac)) return 0d;
        return Math.max(0d, Math.min(1d, frac));
    }
}

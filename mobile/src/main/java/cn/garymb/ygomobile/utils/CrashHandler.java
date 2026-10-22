package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;

/**
 * 全局异常与运行诊断落盘（{@code ygocore/log}）。
 *
 * <p>三条挂钩链路：
 * <ul>
 *   <li>{@code App.init} 装为 {@code Thread.setDefaultUncaughtExceptionHandler}，
 *       任何线程未捕获异常（含横屏决斗中的 Activity/GL/回放线程）都先落盘再退出进程；</li>
 *   <li>{@link #hookThread(Thread, String)} 给游戏工作线程（回放投喂/加载、GL 渲染、网络收发）
 *       单独挂处理器，附带线程场景名，崩溃时能区分「在跑哪条线程、处于哪个界面阶段」；</li>
 *   <li>{@link #report(String, Throwable)} / {@link #writeReport(String, String)} 供 engine/game/dialog
 *       在非致命路径主动写诊断（消息派发异常、回放卡码映射统计），记录后原样抛出，不改变既有行为。</li>
 * </ul>
 *
 * <p>日志目录由 {@link #ensureLogDir()} 保证存在并逐级兼容回退（资源目录 → 外部分区 → 缓存目录），
 * 修复旧实现因 {@code ygocore/log} 目录不存在、{@code FileOutputStream} 静默失败而丢日志的问题。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    public static final String TAG = "YGOMobile-Exception";
    public static final CrashHandler INSTANCE = new CrashHandler();

    private final Map<String, String> infos = new HashMap<String, String>();
    private final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
    private Context context;
    private Thread.UncaughtExceptionHandler defaultHandler;
    /** 当前场景（主菜单 / 横屏决斗 / 回放 / 对话框…），由 Activity 与游戏线程挂钩时更新，随崩溃落盘 */
    private volatile String scene = "unknown";
    /** 同一进程内只弹一次 Toast，避免多线程连环崩溃时 UI 卡死 */
    private volatile boolean toastShown;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        this.context = context;
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        ensureLogDir();     // 启动即建好 ygocore/log，崩溃时目录已存在
    }

    /** 记录当前场景（如「游戏-横屏决斗」「回放-PLAYING」「对话框-录像选择」），崩溃日志会带上 */
    public void setScene(String scene) {
        if (scene != null && !scene.isEmpty()) this.scene = scene;
    }

    public String getScene() {
        return scene;
    }

    /**
     * 给游戏工作线程挂崩溃捕获：先以「线程场景」落盘，再交回全局处理器走正常崩溃流程。
     * 回放投喂/加载、GL 渲染、网络收发等线程在横屏游戏中崩溃时靠此拿到堆栈。
     */
    public void hookThread(Thread thread, String threadScene) {
        if (thread == null) return;
        thread.setUncaughtExceptionHandler((t, ex) -> {
            String prev = scene;
            scene = threadScene + "(" + t.getName() + ")";
            try {
                handleException(ex);
            } catch (Throwable ignored) {
            } finally {
                scene = prev;
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(t, ex);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        });
    }

    /**
     * 非致命诊断上报：写日志文件但<b>不弹 Toast、不退出进程</b>，供 game/engine 在
     * 「记录后继续抛出」或「可恢复异常」路径调用。
     */
    public void report(String scene, Throwable ex) {
        try {
            String prev = this.scene;
            if (scene != null) this.scene = scene;
            collectDeviceInfo(context);
            saveCrashInfo2File(ex);
            this.scene = prev;
        } catch (Throwable ignored) {
            // 诊断落盘本身绝不能影响业务流程
        }
    }

    /** 非异常文本诊断（回放卡码映射统计等），落同一目录，文件名前缀 {@code 【YGO-tag】} */
    public synchronized void writeReport(String tag, String content) {
        File dir = ensureLogDir();
        if (dir == null || content == null) return;
        File file = new File(dir, "【YGO-" + tag + "】" + formatter.format(new Date()) + ".log");
        if (file.exists()) file.delete();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buildHeader(tag).append(content).toString().getBytes());
        } catch (Exception e) {
            Log.e(TAG, "an error occurred while writing report " + file.getName(), e);
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(ex) && defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "error : ", e);
            }

            System.exit(1);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        // 使用Toast来显示异常信息（独立线程 + 仅一次，避免多线程崩溃时重复起 Looper 线程）
        if (!toastShown) {
            toastShown = true;
            new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    try {
                        YGOUtil.showTextToast(context.getString(R.string.crashed), Toast.LENGTH_LONG);
                    } catch (Throwable ignored) {
                        // context 可能为空（未在 App 初始化）：Toast 失败不影响日志落盘
                    }
                    Looper.loop();
                }
            }.start();
        }
        collectDeviceInfo(context);
        saveCrashInfo2File(ex);
        return true;
    }

    public void collectDeviceInfo(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName == null ? "null" : pi.versionName;
                String versionCode = pi.versionCode + "";
                infos.put("versionName", versionName);
                infos.put("versionCode", versionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "an error occured when collect package info", e);
        }
        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                infos.put(field.getName(), field.get(null).toString());
                Log.d(TAG, field.getName() + " : " + field.get(null));
            } catch (Exception e) {
                Log.e(TAG, "an error occured when collect crash info", e);
            }
        }
    }

    /** 堆栈摘要写入统一头部（时间 / 场景 / 线程 / 版本与机型信息）+ 给定正文 */
    private StringBuilder buildHeader(String tag) {
        StringBuilder sb = new StringBuilder();
        sb.append("== ").append(tag).append(" @ ")
                .append(formatter.format(new Date())).append(" ==\n")
                .append("scene=").append(scene).append('\n')
                .append("thread=").append(Thread.currentThread().getName()).append('\n')
                .append("orientation=").append(orientationText()).append('\n');
        for (Map.Entry<String, String> entry : infos.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return sb;
    }

    /** 屏幕方向（1=竖屏 2=横屏）：用于确认“横屏游戏中崩溃”的场景 */
    private String orientationText() {
        try {
            int o = context.getResources().getConfiguration().orientation;
            return o == 2 ? "landscape" : (o == 1 ? "portrait" : String.valueOf(o));
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * 日志目录保障：优先 {@code ygocore/log}（AppsSettings.getMobileLogPath），不存在则 mkdirs；
     * 资源目录不可写（未授权/只读）时逐级回退到应用外部文件目录、再到缓存目录，
     * 避免因目录缺失直接抛出使日志静默丢失。
     */
    private File ensureLogDir() {
        String path = null;
        try {
            path = AppsSettings.get().getMobileLogPath();
        } catch (Throwable t) {
            Log.w(TAG, "getMobileLogPath failed", t);
        }
        File dir = mkdirs(path);
        if (dir != null) return dir;
        try {
            if (context != null) {
                File ext = context.getExternalFilesDir("ygocore/log");
                dir = mkdirs(ext == null ? null : ext.getAbsolutePath());
                if (dir != null) return dir;
                dir = mkdirs(new File(context.getCacheDir(), "ygocore/log").getAbsolutePath());
                if (dir != null) return dir;
            }
        } catch (Throwable t) {
            Log.w(TAG, "fallback log dir failed", t);
        }
        return null;
    }

    private static File mkdirs(String path) {
        if (path == null || path.isEmpty()) return null;
        File dir = new File(path);
        if (dir.isDirectory() || dir.mkdirs()) {
            return dir.canWrite() ? dir : null;
        }
        return null;
    }

    private synchronized String saveCrashInfo2File(Throwable ex) {
        String fileName = null;
        File dir = ensureLogDir();
        try {
            fileName = "【YGO】" + formatter.format(new Date()) + ".log";
            StringBuilder sb = buildHeader("CRASH");
            sb.append(ex).append('\n');
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            ex.printStackTrace(printWriter);
            Throwable cause = ex.getCause();
            while (cause != null) {
                cause.printStackTrace(printWriter);
                cause = cause.getCause();
            }
            printWriter.close();
            sb.append(writer.toString());
            String content = sb.toString();
            Log.e(TAG, content);
            if (dir != null) {
                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(content.getBytes());
                    fos.flush();
                } catch (Exception e) {
                    Log.e(TAG, "an error occurred while writing file " + file, e);
                }
            } else {
                Log.e(TAG, "no writable log directory, crash log kept in logcat only");
            }
        } catch (Exception e) {
            Log.e(TAG, "an error occurred while preparing crash log", e);
        }
        return fileName == null ? null : (dir == null ? null : dir + "/" + fileName);
    }
}

package cn.garymb.ygomobile.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
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
 * <p>日志目录由 {@link #ensureLogDir()} / {@link #logDirCandidates()} 逐级兼容（外部根目录
 * {@code /sdcard/ygocore/log} → 资源目录 → 应用外部分区 → 缓存目录），写入时逐个候选重试，
 * 修复旧实现因 {@code ygocore/log} 目录不存在、{@code FileOutputStream} 静默失败而丢日志的问题。
 * 已授予外部存储读取权限时优先落公开的外部根目录，错误退出后直接用文件管理器就能取到日志。
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

    /** 非异常文本诊断（回放卡码映射统计等），落同一批候选目录，文件名前缀 {@code 【YGO-tag】} */
    public synchronized void writeReport(String tag, String content) {
        if (content == null) return;
        writeToLogDir("【YGO-" + tag + "】" + formatter.format(new Date()) + ".log",
                buildHeader(tag).append(content).toString());
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
     * 日志目录保障：返回第一个可用（已存在或 mkdirs 成功且可写）的候选目录，语义见
     * {@link #logDirCandidates()}；全部不可用时返回 null，写日志侧降级为只进 logcat。
     */
    private File ensureLogDir() {
        List<File> dirs = logDirCandidates();
        return dirs.isEmpty() ? null : dirs.get(0);
    }

    /**
     * 按优先级给出日志候选目录（均已确保存在且可写，不可用的不入列）：
     * <ol>
     *   <li>外部存储根目录下的 {@code ygocore/log}（{@link Constants#ORI_LOG}，与备份卡组用的
     *       {@code ygocore/deck} 同级）：仅在已授予外部存储读取权限时才用。这个目录用户用文件
     *       管理器直接能看到，软件错误退出后不必再先进应用私有目录取日志；</li>
     *   <li>{@code AppsSettings.getMobileLogPath()}：应用资源目录（私有外部目录）下的 ygocore/log，
     *       未授权时的主用位置；</li>
     *   <li>应用外部私有文件目录、内部缓存目录：前两者不可写时的兜底，避免日志静默丢失。</li>
     * </ol>
     */
    private List<File> logDirCandidates() {
        List<File> dirs = new ArrayList<>(4);
        addDir(dirs, externalLogPath());
        try {
            addDir(dirs, AppsSettings.get().getMobileLogPath());
        } catch (Throwable t) {
            Log.w(TAG, "getMobileLogPath failed", t);
        }
        try {
            if (context != null) {
                File ext = context.getExternalFilesDir("ygocore/log");
                addDir(dirs, ext == null ? null : ext.getAbsolutePath());
                addDir(dirs, new File(context.getCacheDir(), "ygocore/log").getAbsolutePath());
            }
        } catch (Throwable t) {
            Log.w(TAG, "fallback log dir failed", t);
        }
        return dirs;
    }

    private static void addDir(List<File> dirs, String path) {
        File dir = mkdirs(path);
        if (dir != null && !dirs.contains(dir)) {
            dirs.add(dir);
        }
    }

    /**
     * 外部根目录的 ygocore/log：没拿到外部存储读取权限时该路径读写不了，返回 null 交给后续候选。
     * 目标 SDK 为 29 且清单里 {@code requestLegacyExternalStorage} 已开，授权后可直接写。
     */
    private String externalLogPath() {
        return hasExternalStorageAccess() ? Constants.ORI_LOG : null;
    }

    /** 是否已允许读取外部存储（Android M 以下安装即授予，无需检查） */
    private boolean hasExternalStorageAccess() {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            Log.w(TAG, "check storage permission failed", t);
            return false;
        }
    }

    /**
     * 依次向候选目录写 {@code fileName}，成功返回落盘文件的绝对路径，全部失败返回 null。
     * “目录可建可写”不等于“文件一定写得进去”（部分 ROM 上外部根目录仍会拒写），
     * 故写入本身也逐候选重试；FileOutputStream 默认覆盖写，同名旧日志不会追加。
     */
    private String writeToLogDir(String fileName, String content) {
        if (fileName == null || content == null) {
            return null;
        }
        for (File dir : logDirCandidates()) {
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes());
                fos.flush();
                return file.getAbsolutePath();
            } catch (Throwable t) {
                Log.w(TAG, "write report " + file.getName() + " into " + dir + " failed", t);
            }
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
        String saved = null;
        try {
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
            // 逐个候选目录试写（已授权时先写外部根目录 ygocore/log），全失败则只保留 logcat
            saved = writeToLogDir("【YGO】" + formatter.format(new Date()) + ".log", content);
            if (saved == null) {
                Log.e(TAG, "no writable log directory, crash log kept in logcat only");
            }
        } catch (Exception e) {
            Log.e(TAG, "an error occurred while preparing crash log", e);
        }
        return saved;
    }
}

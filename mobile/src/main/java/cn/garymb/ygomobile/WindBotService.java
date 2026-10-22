package cn.garymb.ygomobile;

import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import libwindbot.windbot.WindBot;

/**
 * windbot 全局监听（原逻辑分散在 ResCheckTask 中，仅 MainActivity 创建路径初始化）：
 * RUN_WINDBOT 接收器进程级只注册一次；WindBot.initAndroid 每个进程只成功执行一次
 * （Embeddinator/JNA 直进 libmonosgen，重复 init 会在上一轮 bot 的 C# 线程仍存活时
 * 重建 Mono 全局状态导致 SIGSEGV），仅资源更新后（ResCheckTask han case 0）以
 * forceReinit=true 强制重初始化。
 * YGOProActivity 启动时即后台线程调用 {@link #startListening(Context)}，
 * 使人机对战无需等到进入 PlayerWaitingDialog 才初始化，节约时间；
 * 该路径在 MainActivity 已初始化过时会直接跳过 initAndroid。
 */
public final class WindBotService {
    private static final String TAG = "WindBotService";
    public static final String ACTION_RUN_WINDBOT = "RUN_WINDBOT";
    public static final String EXTRA_ARGS = "args";

    /** 进程级接收器：注册一次后常驻，随进程结束释放 */
    private static BroadcastReceiver sReceiver;
    /** initAndroid 是否已在当前进程成功执行（Mono 域已建立），成功后仅 forceReinit 可再次执行 */
    private static boolean sInitialized = false;

    private WindBotService() {
    }

    /**
     * 初始化 WindBot（进程内仅一次）并（仅首次）注册 RUN_WINDBOT 监听。
     * 可在任意线程调用：initAndroid 较耗时，YGOProActivity 路径应在后台线程执行；
     * registerReceiver 使用 applicationContext，与注册线程无关，回调仍在主线程。
     */
    public static void startListening(Context context) {
        startListening(context, false);
    }

    /**
     * @param forceReinit true 时强制重新执行 initAndroid（资源更新后按最新
     *                    bot.conf / cards.cdb 重初始化）；false 时若本进程已成功
     *                    初始化则跳过，避免 Mono 重复初始化崩溃。
     */
    public static synchronized void startListening(Context context, boolean forceReinit) {
        AppsSettings settings = AppsSettings.get();
        if (!sInitialized || forceReinit) {
            Log.i(TAG, "init windbot: " + settings.getResourcePath()
                    + (forceReinit ? " (force reinit)" : ""));
            try {
                WindBot.initAndroid(settings.getResourcePath(),
                        settings.getDataBasePath() + "/" + DATABASE_NAME,
                        settings.getResourcePath() + "/" + CORE_BOT_CONF_PATH);
                sInitialized = true;
            } catch (Throwable e) {
                Log.e(TAG, "init windbot error " + e.getMessage());
            }
        } else {
            Log.i(TAG, "windbot already initialized, skip initAndroid");
        }
        if (sReceiver == null) {
            sReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (ACTION_RUN_WINDBOT.equals(intent.getAction())) {
                        String args = intent.getStringExtra(EXTRA_ARGS);
                        // 与 initAndroid 共用类监视器：禁止 runAndroid 与后台线程
                        // 正在执行的 initAndroid 并发进入 Mono 运行时
                        synchronized (WindBotService.class) {
                            try {
                                WindBot.runAndroid(args);
                            } catch (Throwable e) {
                                Log.e(TAG, "WindBot.runAndroid error: " + e.getMessage(), e);
                            }
                        }
                    }
                }
            };
            context.getApplicationContext()
                    .registerReceiver(sReceiver, new IntentFilter(ACTION_RUN_WINDBOT));
            Log.i(TAG, "RUN_WINDBOT listener registered");
        }
    }
}

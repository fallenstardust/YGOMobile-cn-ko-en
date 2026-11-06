package cn.garymb.ygomobile.ui.home;

import static cn.garymb.ygomobile.Constants.ASSETS_EN;
import static cn.garymb.ygomobile.Constants.ASSETS_ES;
import static cn.garymb.ygomobile.Constants.ASSETS_JP;
import static cn.garymb.ygomobile.Constants.ASSETS_KOR;
import static cn.garymb.ygomobile.Constants.ASSETS_PATH;
import static cn.garymb.ygomobile.Constants.ASSETS_PT;
import static cn.garymb.ygomobile.Constants.BOT_CONF;
import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.CORE_LIMIT_PATH;
import static cn.garymb.ygomobile.Constants.CORE_SINGLE_PATH;
import static cn.garymb.ygomobile.Constants.CORE_STRING_PATH;
import static cn.garymb.ygomobile.Constants.CORE_SYSTEM_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.AppsSettings.languageEnum;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.WindBotService;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SystemUtils;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.LimitManager;

public class ResCheckTask extends AsyncTask<Void, Integer, Integer> {
    public static final int ERROR_NONE = 0;
    public static final int ERROR_CORE_CONFIG = -1;
    public static final int ERROR_COPY = -2;
    public static final int ERROR_CORE_CONFIG_LOST = -3;
    private static final String TAG = "ResCheckTask";
    /** 上一版方案（从 assets/data/script 散文件拷贝）留下的完成标记，已无任何读取方。 */
    private static final String OLD_SCRIPT_COPY_MARK = ".scripts_copy_mark";
    /**
     * 进度权重表，合计正好 100：串行前置（清缓存/建目录）3 与串行收尾（卡数据加载）11 固定，
     * 其余由 9 个并行单元按实际耗时量级分摊（卡图包档案 34、数据库/残局/配置 12、
     * 卡组+卡包 9、音效 8、皮肤 7、人机 6、脚本档案拷贝与旧目录清理 4、字体 2、整理卡图 2）。
     * 主脚本不再落地解压，脚本单元只剩“拷档案 + 清旧目录”，权重从 18 降到 4，腾出的部分
     * 归给真正耗时的卡图拷贝与卡数据加载。单元无论实际执行还是因“已就绪/无需更新”而跳过
     * 都全额结算，因此百分比必定单调推进到 100
     */
    private static final int W_PREPARE = 3, W_LOAD = 11;
    private static final int W_PICS = 34, W_SCRIPT = 4, W_LANGUAGE = 12, W_CARDS = 9,
            W_SOUND = 8, W_TEXTURES = 7, W_WINDBOT = 6, W_FONT = 2, W_MOVE_PICS = 2;
    /** 脚本单元内部刻度：档案拷贝 0–0.7，旧实体目录清理 0.7–1 */
    private static final double SCRIPT_COPY_RATIO = 0.7;
    /** 检查并行路数上限：总共只有 9 个单元，再多也没活可分 */
    private static final int RES_CHECK_MAX_THREADS = 8;
    /**
     * 进程级“正在检查”计数：主页首次检查、ACTION_RELOAD 重跑与对外资源分享都可能并发
     * 跑一个检查，故用计数而不是布尔，避免先结束的那次把状态误清成“未检查”
     */
    private static final AtomicInteger sRunningChecks = new AtomicInteger();
    private final AppsSettings mSettings;
    private final Context mContext;
    private final ResCheckListener mListener;
    private final Handler handler;
    protected int mError = ERROR_NONE;
    private Exception taskException = null;
    Handler han = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    // 资源更新后强制重初始化，按最新 bot.conf / cards.cdb 重建 WindBot 状态
                    checkWindbot(true);
                    break;
            }
        }
    };
    private DialogPlus dialog = null;
    /** 顶部进度条浮窗及其步骤文字/进度条；mBanner 为 null 表示本次退回模态对话框。 */
    private View mBanner;
    private TextView mBannerText;
    private ProgressBar mBannerBar;
    /** 并行单元表：{@link #buildUnits} 构造，顺序即提交顺序（重的先提交，避免长尾） */
    private final List<Unit> mUnits = new ArrayList<>();
    /** 进度锁：各并行单元在自己的池线程里上报，汇总与取最大值都在这把锁内 */
    private final Object mProgressLock = new Object();
    /** 串行阶段（前置/收尾）结算掉的固定百分比，只在进度锁内改 */
    private int mFixedPct;
    /** 串行阶段的显式标签；为 null 时标签改取“最拖后腿的单元”名 */
    private String mStepText;
    /** 汇总出的百分比（0..100），只在进度锁内改 */
    private int mProgressPct;
    private boolean isNewVersion;
    private int mPendingVersion;

    @SuppressWarnings("deprecation")
    public ResCheckTask(Context context, ResCheckListener listener) {
        mContext = context;
        mListener = listener;
        handler = new Handler(context.getMainLooper());
        mSettings = AppsSettings.get();
        checkWindbot();
    }

    /**
     * 资源检查是否正在进行中，供 {@link cn.garymb.ygomobile.YGOStarter#startGame} 拒绝开局：
     * 检查未完成时 cards.cdb / 卡包 / 脚本可能尚未落地，进对局会读到空卡池或半套脚本。
     *
     * <p>只读计数、不做任何阻塞，界面其余操作不受影响；从未跑过检查的路径（如“仅游戏”
     * 模式由 LogoActivity 直接进决斗）恒返回 false，维持原有行为。
     */
    public static boolean isCheckRunning() {
        return sRunningChecks.get() > 0;
    }

    public static String getDatapath(String path) {
        if (TextUtils.isEmpty(ASSETS_PATH)) {
            return path;
        }
        if (path.startsWith(ASSETS_PATH)) {
            return path;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return ASSETS_PATH + path;
    }

    public static boolean checkDataBase(String path) {
        if (!new File(path).exists()) {
            return false;
        }
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(path, null,
                    SQLiteDatabase.OPEN_READWRITE);
            Cursor cursor = db.rawQuery("select * from datas,texts where datas.id=texts.id limit 1;", null);
            if (cursor == null) {
                return false;
            }
            cursor.close();
        } catch (Exception e) {
            return false;
        } finally {
            IOUtils.close(db);
        }
        return true;
    }

    public static void doSomeTrickOnDatabase(String myPath)
            throws SQLiteException {
        SQLiteDatabase db = null;
        db = SQLiteDatabase.openDatabase(myPath, null,
                SQLiteDatabase.OPEN_READWRITE);
        try {
            db.rawQuery("select * from datas where datas._id = 0;", null);
            db.close();
            return;
        } catch (Exception e) {

        }
        try {
            db.beginTransaction();
            db.execSQL("ALTER TABLE datas RENAME TO datas_backup;");
            db.execSQL("CREATE TABLE datas (_id integer PRIMARY KEY, ot integer, alias integer, setcode integer, type integer,"
                    + " atk integer, def integer, level integer, race integer, attribute integer, category integer);");
            db.execSQL("INSERT INTO datas (_id, ot, alias, setcode, type, atk, def, level, race, attribute, category) "
                    + "SELECT id, ot, alias, setcode, type, atk, def, level, race, attribute, category FROM datas_backup;");
            db.execSQL("DROP TABLE datas_backup;");
            db.execSQL("ALTER TABLE texts RENAME TO texts_backup;");
            db.execSQL("CREATE TABLE texts (_id integer PRIMARY KEY, name varchar(128), \"desc\" varchar(1024),"
                    + " str1 varchar(256), str2 varchar(256), str3 varchar(256), str4 varchar(256), str5 varchar(256),"
                    + " str6 varchar(256), str7 varchar(256), str8 varchar(256), str9 varchar(256), str10 varchar(256),"
                    + " str11 varchar(256), str12 varchar(256), str13 varchar(256), str14 varchar(256), str15 varchar(256), str16 varchar(256));");
            db.execSQL("INSERT INTO texts (_id, name, \"desc\", str1, str2, str3, str4, str5, str6, str7, str8, str9, str10, str11, str12, str13, str14, str15, str16)"
                    + " SELECT id, name, \"desc\", str1, str2, str3, str4, str5, str6, str7, str8, str9, str10, str11, str12, str13, str14, str15, str16 FROM texts_backup;");
            db.execSQL("DROP TABLE texts_backup;");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (db != null) {
            db.close();
        }
    }

    public void unregisterMReceiver() {
        // RUN_WINDBOT 接收器已改为 WindBotService 进程级常驻监听，此处无需注销
    }

    /**
     * 查询sharedPreference中的版本号和package的版本号，比较后得出是否需要更新，将结果存入isNewVersion。
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        sRunningChecks.incrementAndGet();
        showProgress();
        int vercode = SystemUtils.getVersion(mContext);
        if (mSettings.getAppVersion() < vercode) {//刚安装app时，mSettings.getAppVersion()返回值为0
            isNewVersion = true;
            // 版本号不在这里提交：只有在资源复制成功后（onPostExecute）才写入，
            // 否则一旦大文件（pics.zip/scripts.zip）复制失败或进程被杀，
            // 下次启动 isNewVersion 为 false 就不会再强制重新复制，导致资源永久丢失。
            mPendingVersion = vercode;
        } else {
            isNewVersion = false;
        }
    }

    @Override
    protected void onPostExecute(final Integer result) {
        super.onPostExecute(result);
        //先解除“检查进行中”闸门，本方法尾部的检查回调（可能立即拉起对局）才不会被拦下
        sRunningChecks.decrementAndGet();
        if (taskException != null) {
            Toast.makeText(mContext, "ERROR COPY: " + taskException.getMessage(), Toast.LENGTH_LONG).show();
        }
        // 只有复制成功才提交版本号，失败时下次启动仍会以 needsUpdate=true 重试
        if (isNewVersion && result != null && result == ERROR_NONE) {
            mSettings.setAppVersion(mPendingVersion);
        }
        //收尾：无论中途是否因异常提前返回，百分比都定格到 100，随后收起提示
        finishProgress();
        hideProgress();
        if (mListener != null) {
            mListener.onResCheckFinished(result, isNewVersion);
        }
    }

    /** 一个并行单元：标签名、进度权重与执行体；fraction 只在进度锁内读写 */
    private static final class Unit {
        final int nameResId;
        final int weight;
        final UnitTask body;
        double fraction;

        Unit(int nameResId, int weight, UnitTask body) {
            this.nameResId = nameResId;
            this.weight = weight;
            this.body = body;
        }
    }

    /** 单元执行体：{@code unit} 是本单元在 {@link #mUnits} 里的下标，用于把进度归到自己名下 */
    private interface UnitTask {
        void run(int unit) throws Exception;
    }

    /** 登记一个并行单元，返回它的下标 */
    private int addUnit(int nameResId, int weight, UnitTask body) {
        mUnits.add(new Unit(nameResId, weight, body));
        return mUnits.size() - 1;
    }

    /**
     * 单元进度：同一单元内取 max（IOUtils 一个单元可能分多段上报，解压还会多个分段线程
     * 同时上报），保证只前进不回退；{@code unit<0} 表示串行调用，直接忽略
     */
    private void advanceUnit(int unit, double fraction) {
        if (unit < 0) {
            return;
        }
        boolean changed;
        synchronized (mProgressLock) {
            Unit u = mUnits.get(unit);
            double f = fraction < 0 ? 0 : Math.min(fraction, 1);
            changed = f > u.fraction;
            if (changed) {
                u.fraction = f;
                mProgressPct = computePctLocked();
            }
        }
        if (changed) {
            postProgress();
        }
    }

    /** 给 IOUtils 拷贝用的进度回调：把 done/total 映到本单元的 {@code [from,to]} 区间 */
    private IOUtils.CopyProgress progressRange(final int unit, final double from, final double to) {
        if (unit < 0) {
            return null;
        }
        return new IOUtils.CopyProgress() {
            @Override
            public void onProgress(long done, long total) {
                if (total <= 0) {
                    return;
                }
                double r = Math.min(1.0, (double) done / (double) total);
                advanceUnit(unit, from + (to - from) * r);
            }
        };
    }

    /** 串行阶段结算：把固定百分比推进 weight（前置与卡数据加载用） */
    private void settle(int weight) {
        synchronized (mProgressLock) {
            mFixedPct = Math.min(100, mFixedPct + weight);
            mProgressPct = computePctLocked();
        }
        postProgress();
    }

    /** 串行阶段直接指定标签；传 null 交回给“最拖后腿的单元”策略 */
    private void setLabel(String text) {
        mStepText = text;
        postProgress();
    }

    /** 在进度锁内算总百分比（向下取整，避免舍入让进度提前跑到 100） */
    private int computePctLocked() {
        double pct = mFixedPct;
        for (Unit u : mUnits) {
            pct += u.weight * u.fraction;
        }
        return (int) Math.min(100, Math.floor(pct));
    }

    /** 标签取“剩余工作量（权重×未完成比例）最大”的单元，一眼看出谁在拖 */
    private String buildTextLocked() {
        String label = mStepText;
        if (label == null) {
            Unit lag = null;
            double best = 0;
            for (Unit u : mUnits) {
                double left = u.weight * (1 - u.fraction);
                if (left > best) {
                    best = left;
                    lag = u;
                }
            }
            label = lag == null ? mContext.getString(R.string.check_res)
                    : mContext.getString(R.string.check_things, mContext.getString(lag.nameResId));
        }
        return label + " " + mProgressPct + "%";
    }

    /** 把当前百分比与标签投到主线程：浮窗同时推确定态进度条 */
    private void postProgress() {
        final int pct;
        final String text;
        synchronized (mProgressLock) {
            pct = mProgressPct;
            text = buildTextLocked();
        }
        handler.post(() -> {
            if (mBannerBar != null) {
                mBannerBar.setProgress(pct);
            }
            if (mBannerText != null) {
                mBannerText.setText(text);
            } else if (dialog != null) {
                dialog.setMessage(text);
            }
        });
    }

    /** 收尾定格 100%：异常提前返回时也要走完，不让提示停在半途 */
    private void finishProgress() {
        mStepText = null;
        synchronized (mProgressLock) {
            mFixedPct = 100;
            for (Unit u : mUnits) {
                u.fraction = 1;
            }
            mProgressPct = 100;
        }
        postProgress();
    }

    /** 配置的检查并行路数，限到 {@code [1, RES_CHECK_MAX_THREADS]} */
    private int resCheckThreads() {
        int want = AppsSettings.get().getIntSettings(Constants.PREF_RES_CHECK_THREADS,
                Constants.DEF_PREF_RES_CHECK_THREADS);
        return Math.max(1, Math.min(RES_CHECK_MAX_THREADS, want));
    }

    /**
     * 并行跑完所有单元：固定线程池，提交顺序即 {@link #mUnits} 顺序（工作量大的先占坑）；
     * 成功的单元结束后结算满权重（跳过或无工作量也照样推进行程）。
     *
     * <p>一个单元报错不中断其它单元：全部结束后才抛出第一个异常，交给 doInBackground 走原有的
     * ERROR_COPY 分支（版本号因此不提交，下次启动以 needsUpdate 重试），失败的单元留下的半成品
     * 文件下次会被对应单元的“已存在则不重写”或 CRC/指纹判定重新补全。
     */
    private void runUnitsInParallel() throws Exception {
        if (mUnits.isEmpty()) {
            return;
        }
        int threads = Math.min(resCheckThreads(), mUnits.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final AtomicReference<Exception> first = new AtomicReference<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < mUnits.size(); i++) {
                final int unit = i;
                futures.add(pool.submit(() -> {
                    try {
                        mUnits.get(unit).body.run(unit);
                        advanceUnit(unit, 1);
                    } catch (Exception e) {
                        first.compareAndSet(null, e);
                        Log.e(TAG, "并行单元失败：单元#" + unit + " "
                                + mContext.getString(mUnits.get(unit).nameResId), e);
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    first.compareAndSet(null, e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    first.compareAndSet(null, cause instanceof Exception
                            ? (Exception) cause : e);
                }
            }
        } finally {
            pool.shutdown();
        }
        Exception err = first.get();
        if (err != null) {
            throw err;
        }
    }

    /**
     * 进度提示入口：优先用顶部进度条浮窗（非模态，检查期间界面其余部分照常可操作），
     * 内容视图尚不可得时（如无 setContentView 的透明 Activity）退回原来的模态对话框。
     */
    private void showProgress() {
        if (attachBanner()) {
            return;
        }
        dialog = DialogPlus.show(mContext, null, mContext.getString(R.string.check_res));
    }

    /**
     * 把浮窗挂到 Activity 的内容视图根上（即 android.R.id.content，为 FrameLayout），因此
     * 不是悬浮窗、不需 SYSTEM_ALERT_WINDOW 权限；也不抢焦点，与下方界面共处一块屏幕。
     *
     * @return 挂载成功返回 true
     */
    private boolean attachBanner() {
        if (!(mContext instanceof Activity)) {
            return false;
        }
        Activity activity = (Activity) mContext;
        if (activity.isFinishing()) {
            return false;
        }
        View root = activity.findViewById(android.R.id.content);
        // 只在已真正装载过界面（内容视图非空）的 Activity 上挂浮窗：透明且未 setContentView
        // 的 Activity（如对外共享资源的 ResourceShareActivity）挂上去会浮在调用方界面上，退回对话框
        if (!(root instanceof ViewGroup) || ((ViewGroup) root).getChildCount() == 0) {
            return false;
        }
        mBanner = LayoutInflater.from(activity)
                .inflate(R.layout.view_res_check_banner, (ViewGroup) root, false);
        mBannerText = mBanner.findViewById(R.id.tv_res_check_step);
        mBannerBar = mBanner.findViewById(R.id.pb_res_check);
        ((ViewGroup) root).addView(mBanner);
        // 从屏幕顶沿下滑入场
        mBanner.setAlpha(0f);
        mBanner.post(() -> {
            if (mBanner == null) {
                return;     // 检查在布局完成前就结束了，交给 hideProgress 做移除
            }
            mBanner.setTranslationY(-mBanner.getHeight());
            mBanner.animate().alpha(1f).translationY(0f).setDuration(200).start();
        });
        return true;
    }

    /** 收起进度提示：浮窗向上滑出并移出视图树，对话框直接 dismiss。 */
    private void hideProgress() {
        final View banner = mBanner;
        mBanner = null;
        mBannerText = null;
        mBannerBar = null;
        if (banner != null) {
            float off = banner.getHeight();
            banner.animate().alpha(0f).translationY(-off).setDuration(200).withEndAction(() -> {
                if (banner.getParent() instanceof ViewGroup) {
                    ((ViewGroup) banner.getParent()).removeView(banner);
                }
            }).start();
        }
        if (dialog != null) {
            try {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Exception e) {
                // dismiss 异常不影响检查回调
            }
        }
    }

    /**
     * 清除下载缓存
     * 如果是新安装ygomobile，则将资源文件从assets拷贝到游戏目录中。游戏目录为app-specific external storage
     *
     * @param params The parameters of the task.
     * @return
     */
    @Override
    protected Integer doInBackground(Void... params) {
        Log.d(TAG, "check start");
        final boolean needsUpdate = isNewVersion;
        // ---- 串行前置：目录与缓存状态必须先就绪，所有并行单元都往这些目录里写 ----
        setLabel(mContext.getString(R.string.check_things, mContext.getString(R.string.core_config)));
        //清空下载缓存
        File imgDir = new File(mSettings.getCardImagePath());
        File[] files = imgDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".tmp");
            }
        });
        if (files != null) {
            for (File file : files) {
                FileUtils.deleteFile(file);
            }
        }
        //res
        try {
            String resPath = mSettings.getResourcePath();
            //创建游戏目录
            IOUtils.createNoMedia(resPath);
            //检查文件夹
            checkDirs();
            if (mSettings.isUseExtraCards()) {
                //自定义数据库无效，则用默认的
                if (!CardManager.checkDataBase(mSettings.getDatabaseFile())) {
                    mSettings.setUseExtraCards(false);
                }
            }
            settle(W_PREPARE);
            // ---- 并行：各单元写各自的目录/档案，互不交叉；标签改由“谁最拖腿”决定 ----
            setLabel(null);
            buildUnits(resPath, needsUpdate);
            long begin = SystemClock.uptimeMillis();
            int lanes = Math.min(resCheckThreads(), mUnits.size());
            runUnitsInParallel();
            Log.i(TAG, "并行资源检查完成：" + mUnits.size() + " 单元 / " + lanes
                    + " 路，耗时=" + (SystemClock.uptimeMillis() - begin) + "ms");
            // 人机重初始化依赖 bot.conf 与 cards.cdb 已落盘，必须在并行单元之后
            han.sendEmptyMessage(0);
            // ---- 串行收尾：卡数据要等 cdb/禁卡表/strings 全部落盘 ----
            setLabel(mContext.getString(R.string.loading));
            loadData();
            settle(W_LOAD);
        } catch (Exception e) {
            taskException = e;
            Log.e(TAG, "ERROR COPY", e);
            return ERROR_COPY;
        }
        return ERROR_NONE;
    }

    /**
     * 登记并行单元。提交顺序按耗时降序（卡图包→脚本→语言资料→卡组卡包→…），否则小单元
     * 先占完坑、大单元排在后面会拉高总耗时。
     *
     * <p>单元之间必须互不依赖：卡组/卡包/音效/皮肤/字体/人机/语言资料各写各自的子目录，
     * 卡图包与脚本 zip 是资源根目录下两个不同名的单档案（各自的 {@code .sig} 也不同名），
     * 整理卡图只搬运 {@code pics/} 里已有的散图，与其他单元写的路径无交集；脚本单元内部自己
     * 按“zip 落地 → 起解压 → 等解压”顺序跑（解压又是多段并行）。
     */
    private void buildUnits(final String resPath, final boolean needsUpdate) {
        mUnits.clear();
        addUnit(R.string.images, W_PICS, (unit) -> {
            //复制卡图压缩包：只在内容真变化时重写，避免升级白拷数百 MB 并扰动卡图时间戳
            if (IOUtils.hasAssets(mContext, getDatapath(Constants.CORE_PICS_ZIP))) {
                IOUtils.copyFileFromAssetsIfChanged(mContext, getDatapath(Constants.CORE_PICS_ZIP),
                        new File(resPath), needsUpdate, progressRange(unit, 0, 1));
            }
        });
        addUnit(R.string.scripts, W_SCRIPT, (unit) -> {
            //上一版散文件方案的完成标记，与本方案无关，顺手清掉
            deleteStaleScriptsCopyMark(resPath);
            //包内 scripts.zip 落到资源根目录（CRC 未变不碰盘）：native 直接读这个档案，
            //不再需要解压成 script/ 下的上万个 lua
            if (IOUtils.hasAssets(mContext, getDatapath(Constants.CORE_SCRIPTS_ZIP))) {
                NativeScriptBootstrap.copyScriptsZipFromAssets(mContext,
                        getDatapath(Constants.CORE_SCRIPTS_ZIP), new File(resPath), needsUpdate,
                        progressRange(unit, 0, SCRIPT_COPY_RATIO));
            }
            advanceUnit(unit, SCRIPT_COPY_RATIO);
            //老安装里解压落地的 script/ 目录：让位给档案直读，顺便回占的 31.5MB
            pruneLegacyScriptsDir(resPath, unit);
        });
        addUnit(R.string.cards_cdb, W_LANGUAGE, (unit) -> copyLanguageData(true, unit));
        addUnit(R.string.tip_new_deck, W_CARDS, (unit) -> {
            if (!needsUpdate) {
                return;
            }
            LogUtil.i(TAG, "needsUpdate");
            //复制卡组
            File deckFiles = new File(mSettings.getDeckDir());
            String[] localDecks = deckFiles.list();
            if (localDecks != null && localDecks.length == 0) {
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_DECK_PATH),
                        mSettings.getDeckDir(), needsUpdate, progressRange(unit, 0, 0.1));
            }
            //复制卡包
            File pack = new File(AppsSettings.get().getPackDeckDir());
            File[] subYdks = pack.listFiles();
            if (subYdks != null) {
                for (File packs : subYdks) {
                    packs.delete();
                }
            }
            IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_PACK_PATH),
                    AppsSettings.get().getPackDeckDir(), needsUpdate, progressRange(unit, 0.1, 1));
        });
        addUnit(R.string.game_sound, W_SOUND, (unit) -> {
            String[] sound1 = mContext.getAssets().list(getDatapath(Constants.CORE_SOUND_PATH));
            String[] sound2 = new File(mSettings.getSoundPath()).list();
            //如果sound文件夹不存在/sound资源数量不够/是更新则复制,但是不强制复制
            if (sound2 == null || (sound1 != null && sound1.length > sound2.length) || needsUpdate) {
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SOUND_PATH),
                        mSettings.getSoundPath(), false, progressRange(unit, 0, 1));
            }
        });
        addUnit(R.string.game_skins, W_TEXTURES, (unit) -> {
            String[] textures1 = mContext.getAssets().list(getDatapath(Constants.CORE_SKIN_PATH));
            String[] textures2 = new File(mSettings.getCoreSkinPath()).list();
            //如果textures文件夹不存在/textures资源数量不够/是更新则复制,但是不强制复制
            if (textures2 == null || (textures1 != null && textures1.length > textures2.length) || needsUpdate) {
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SKIN_PATH),
                        mSettings.getCoreSkinPath(), false, progressRange(unit, 0, 1));
            }
        });
        addUnit(R.string.font_files, W_FONT, (unit) -> IOUtils.copyFilesFromAssets(mContext,
                getDatapath(Constants.FONT_DIRECTORY), mSettings.getFontDirPath(), needsUpdate,
                progressRange(unit, 0, 1)));
        addUnit(R.string.core_config, W_WINDBOT, (unit) -> {
            //复制人机资源（无专用标签文案，标签就用单元名“游戏核心”）
            IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.WINDBOT_PATH),
                    mSettings.getResourcePath(), needsUpdate, progressRange(unit, 0, 1));
        });
        addUnit(R.string.images, W_MOVE_PICS, (unit) -> movePicsToExpansions(unit));
    }

    /**
     * 按系统语言（或用户在设置里指定的资料语言）复制 cdb/残局/配置；进度全部归到 {@code unit}
     * 名下（数据库 0–0.4、残局 0.4–0.7、配置 0.7–1.0）
     */
    private void copyLanguageData(boolean needsUpdate, int unit) throws IOException {
        String language = mContext.getResources().getConfiguration().locale.getLanguage();
        if (language.isEmpty()) {
            return;
        }
        if (mSettings.getDataLanguage() == -1) {
            if (language.equals(languageEnum.Chinese.name)) {
                copyCnData(needsUpdate, unit);
            } else if (language.equals(languageEnum.Korean.name)) {
                copyKorData(needsUpdate, unit);
            } else if (language.equals(languageEnum.Spanish.name)) {
                copyEsData(needsUpdate, unit);
            } else if (language.equals(languageEnum.Japanese.name)) {
                copyJpData(needsUpdate, unit);
            } else if (language.equals(languageEnum.Portuguese.name)) {
                copyPtData(needsUpdate, unit);
            } else {
                copyEnData(needsUpdate, unit);
            }
            return;
        }
        if (mSettings.getDataLanguage() == languageEnum.Chinese.code) copyCnData(needsUpdate, unit);
        if (mSettings.getDataLanguage() == languageEnum.Korean.code) copyKorData(needsUpdate, unit);
        if (mSettings.getDataLanguage() == languageEnum.English.code) copyEnData(needsUpdate, unit);
        if (mSettings.getDataLanguage() == languageEnum.Spanish.code) copyEsData(needsUpdate, unit);
        if (mSettings.getDataLanguage() == languageEnum.Japanese.code) copyJpData(needsUpdate, unit);
        if (mSettings.getDataLanguage() == languageEnum.Portuguese.code) copyPtData(needsUpdate, unit);
    }

    /**
     * 清掉上一版方案（从 assets/data/script 散文件目录拷贝）留下的完成标记：本方案重新用回
     * 包内 scripts.zip + 解压指纹标记，{@code scripts.zip} 与它的校验和记录都是正常档案，不能删。
     * 标记不存在时只剩一次 isFile() 探测。
     */
    private void deleteStaleScriptsCopyMark(String resPath) {
        File mark = new File(resPath, Constants.CORE_SCRIPT_PATH + "/" + OLD_SCRIPT_COPY_MARK);
        if (mark.isFile() && FileUtils.deleteFile(mark)) {
            Log.i(TAG, "已清理旧的脚本拷贝完成标记");
        }
    }

    /**
     * 清掉更早版本“把 scripts.zip 解压落地”留下的 {@code <res>/script/} 实体目录：主脚本现在
     * 由 native 直读 {@code scripts.zip}，实体目录只是最低优先级的兜底档，留着白占 31.5MB，
     * 还可能让升级后未重写的那部分 lua 假装成“已就绪”。
     *
     * <p>{@code expansions/script/} 不在此列：它是 native 眼中的最高优先级档，只放手工调试
     * 脚本（卡包脚本不再解压物化到此处，而由 native 直读包档案；旧版物化残留由
     * {@code NativeScriptBootstrap#pruneLegacyMaterialized} 负责回收）。目录不存在时只剩一次
     * isDirectory() 探测。
     */
    private void pruneLegacyScriptsDir(String resPath, int unit) {
        File scriptDir = new File(resPath, Constants.CORE_SCRIPT_PATH);
        if (scriptDir.isDirectory()) {
            Log.i(TAG, "清理旧版解压落地的脚本目录（native 已改直读 scripts.zip）: "
                    + scriptDir.getAbsolutePath());
            FileUtils.delFile(scriptDir.getAbsolutePath());
        }
        advanceUnit(unit, 1);
    }

    // 移动 picsFolder 文件到 expansionsCardImagePath（并行单元内调用，进度归到该单元）
    public void movePicsToExpansions(int unit) {
        File picsFolder = new File(mSettings.getCardImagePath());
        if (picsFolder.exists() && picsFolder.isDirectory()) {
            File[] picFiles = picsFolder.listFiles();
            if (picFiles != null && picFiles.length > 0) {
                File expansionsFolder = new File(mSettings.getExpansionCardImagePath());
                if (!expansionsFolder.exists()) {
                    expansionsFolder.mkdirs();
                }
                
                try {
                    int moved = 0;
                    for (File picFile : picFiles) {
                        File destFile = new File(expansionsFolder, picFile.getName());
                        // 执行移动操作
                        FileUtils.moveFile(picFile.getAbsolutePath(), destFile.getAbsolutePath());
                        advanceUnit(unit, ++moved / (double) picFiles.length);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to move file: " + picsFolder.getAbsolutePath(), e);
                }
            }
        }
    }

    public int copyCnData(Boolean needsUpdate, int unit) throws IOException {
        //复制数据库
        copyCdbFile(getDatapath(DATABASE_NAME), needsUpdate, unit);
        //复制残局
        IOUtils.copyFilesFromAssets(mContext, getDatapath(CORE_SINGLE_PATH), mSettings.getSingleDir(),
                needsUpdate, progressRange(unit, 0.4, 0.7));
        advanceUnit(unit, 0.7);
        //复制游戏配置文件
        copyCoreConfig(getDatapath("conf") + "/" + CORE_STRING_PATH,
                getDatapath("conf") + "/" + BOT_CONF,
                mSettings.getResourcePath(), needsUpdate, unit);
        mSettings.setDataLanguage(languageEnum.Chinese.code);
        return ERROR_NONE;
    }

    public int copyEnData(Boolean needsUpdate, int unit) throws IOException {
        String enStringConf = ASSETS_EN + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String enBotConf = ASSETS_EN + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String enCdb = ASSETS_EN + getDatapath(DATABASE_NAME);
        String enSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(enCdb, true, unit);
        //复制残局
        IOUtils.copyFilesFromAssets(mContext, enSingle, mSettings.getSingleDir(), needsUpdate,
                progressRange(unit, 0.4, 0.7));
        advanceUnit(unit, 0.7);
        //复制游戏配置文件
        copyCoreConfig(enStringConf, enBotConf, mSettings.getResourcePath(), needsUpdate, unit);
        mSettings.setDataLanguage(languageEnum.English.code);
        return ERROR_NONE;
    }

    public int copyKorData(Boolean needsUpdate, int unit) throws IOException {
        String korStringConf = ASSETS_KOR + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String korBotConf = ASSETS_KOR + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String korCdb = ASSETS_KOR + getDatapath(DATABASE_NAME);
        String korSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(korCdb, true, unit);
        //复制残局
        IOUtils.copyFilesFromAssets(mContext, korSingle, mSettings.getSingleDir(), needsUpdate,
                progressRange(unit, 0.4, 0.7));
        advanceUnit(unit, 0.7);
        //复制游戏配置文件
        copyCoreConfig(korStringConf, korBotConf, mSettings.getResourcePath(), needsUpdate, unit);
        mSettings.setDataLanguage(languageEnum.Korean.code);
        return ERROR_NONE;
    }

    public int copyEsData(Boolean needsUpdate, int unit) throws IOException {
        String esStringConf = ASSETS_ES + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String esBotConf = ASSETS_ES + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String esCdb = ASSETS_ES + getDatapath(DATABASE_NAME);
        String enSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(esCdb, true, unit);
        //复制残局
        IOUtils.copyFilesFromAssets(mContext, enSingle, mSettings.getSingleDir(), needsUpdate,
                progressRange(unit, 0.4, 0.7));
        advanceUnit(unit, 0.7);
        //复制游戏配置文件
        copyCoreConfig(esStringConf, esBotConf, mSettings.getResourcePath(), needsUpdate, unit);
        mSettings.setDataLanguage(languageEnum.Spanish.code);
        return ERROR_NONE;
    }

    public int copyJpData(Boolean needsUpdate, int unit) throws IOException {
        String jpStringConf = ASSETS_JP + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String jpBotConf = ASSETS_JP + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String jpCdb = ASSETS_JP + getDatapath(DATABASE_NAME);
        String enSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(jpCdb, true, unit);
        //复制残局
        IOUtils.copyFilesFromAssets(mContext, enSingle, mSettings.getSingleDir(), needsUpdate,
                progressRange(unit, 0.4, 0.7));
        advanceUnit(unit, 0.7);
        //复制游戏配置文件
        copyCoreConfig(jpStringConf, jpBotConf, mSettings.getResourcePath(), needsUpdate, unit);
        mSettings.setDataLanguage(languageEnum.Japanese.code);
        return ERROR_NONE;
    }

    public int copyPtData(Boolean needsUpdate, int unit) throws IOException {
        String ptStringConf = ASSETS_PT + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String ptBotConf = ASSETS_PT + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String ptCdb = ASSETS_PT + getDatapath(DATABASE_NAME);
        String enSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(ptCdb, true, unit);
        //复制残局
        IOUtils.copyFilesFromAssets(mContext, enSingle, mSettings.getSingleDir(), needsUpdate,
                progressRange(unit, 0.4, 0.7));
        advanceUnit(unit, 0.7);
        //复制游戏配置文件
        copyCoreConfig(ptStringConf, ptBotConf, mSettings.getResourcePath(), needsUpdate, unit);
        mSettings.setDataLanguage(languageEnum.Portuguese.code);
        return ERROR_NONE;
    }

    private void loadData() {
        setLabel(mContext.getString(R.string.loading));
        DataManager.get().load(true);
    }

    /**
     * 复制卡片数据库；{@code unit} 非负时进度归到语言单元名下（占该单元的 0–0.4）。
     * 数据库已存在且不需更新时直接返回，但仍要结算 0.4，否则总百分比到不了 100。
     */
    void copyCdbFile(String assetPath, boolean needsUpdate, int unit) throws IOException {
        File dbFile = new File(mSettings.getDataBasePath(), DATABASE_NAME);
        //如果数据库存在
        if (dbFile.exists()) {
            //如果是更新或者数据库大小小于1m
            if (needsUpdate || dbFile.length() < 1024 * 1024)
                dbFile.delete();
            else {
                advanceUnit(unit, 0.4);
                return;
            }
        }
        IOUtils.copyFilesFromAssets(mContext, assetPath, mSettings.getDataBasePath(), needsUpdate,
                progressRange(unit, 0, 0.4));
        advanceUnit(unit, 0.4);
    }

    private void checkDirs() {
        String[] dirs = {
                //脚本文件夹
                Constants.CORE_SCRIPT_PATH,
                //残局文件夹
                CORE_SINGLE_PATH,
                //卡组文件夹
                Constants.CORE_DECK_PATH,
                //pack文件夹
                Constants.CORE_PACK_PATH,
                //录像文件夹
                Constants.CORE_REPLAY_PATH,
                //字体文件夹
                Constants.FONT_DIRECTORY,
                //资源文件夹
                Constants.CORE_SKIN_PATH,
                //卡图文件夹
                Constants.CORE_IMAGE_PATH,
                //log文件夹
                Constants.MOBILE_LOG,
                //卡组分享截图文件夹
                Constants.MOBILE_DECK_SHARE,
                //额外卡库文件夹
                Constants.CORE_EXPANSIONS,
                //人机资源文件夹
                Constants.WINDBOT_PATH
        };
        File dirFile = null;
        for (String dir : dirs) {
            dirFile = new File(mSettings.getResourcePath(), dir);
            IOUtils.createFolder(dirFile);
        }
    }

    private String getCurVersion(File verPath) {
        if (!verPath.exists()) {
            Log.e(TAG, "check core config no exists " + verPath);
            return null;
        }
        String[] files = verPath.list();
        if (files == null) {
            return null;
        }
        for (String file : files) {
            File f = new File(verPath, file);
            if (f.isDirectory()) {
                return f.getName();
            } else {
                Log.e(TAG, "check core config is file " + f.getAbsolutePath());
            }
        }
        return null;
    }

    /**
     * 复制 system/lflist/strings/bot 四个配置；{@code unit} 非负时把本方法占的语言单元区间
     * 0.7–1.0 再细分（system 0.70–0.76、禁卡表 0.76–0.86、strings 0.86–0.93、bot 0.93–1.00）
     */
    private int copyCoreConfig(String assetStringPath, String assetBotPath, String toPath,
            boolean needsUpdate, int unit) throws IOException {
        File systemfile = new File(mSettings.getResourcePath(), CORE_SYSTEM_PATH);
        File stringfile = new File(mSettings.getResourcePath(), CORE_STRING_PATH);
        File botfile = new File(mSettings.getResourcePath(), BOT_CONF);
        if (!systemfile.exists()) {
            IOUtils.copyFilesFromAssets(mContext, getDatapath("conf") + "/" + CORE_SYSTEM_PATH,
                    toPath, false, progressRange(unit, 0.7, 0.76));
        }
        
        // 在更新禁卡表之前，先保存旧的内置禁卡表列表的第一个名称和最后使用的禁卡表名称
        String oldBuiltInFirstLimitName = null;
        String lastUsedLimit = null;
        if (needsUpdate) {
            
            // 获取旧的内置禁卡表（通过临时读取当前的 CORE_LIMIT_PATH 文件）
            File currentBuiltInFile = new File(mSettings.getResourcePath(), Constants.CORE_LIMIT_PATH);
            if (currentBuiltInFile.exists()) {
                try {
                    LimitManager tempOldBuiltInManager = new LimitManager();
                    tempOldBuiltInManager.loadFile(currentBuiltInFile);
                    
                    if (!tempOldBuiltInManager.getLimitNames().isEmpty()) {
                        oldBuiltInFirstLimitName = tempOldBuiltInManager.getLimitNames().get(0);
                    }
                    tempOldBuiltInManager.close();
                } catch (Exception e) {
                    LogUtil.e(TAG, "Error reading old built-in limit list", e);
                }
            }
            
            lastUsedLimit = AppsSettings.get().getLastLimit();
        }
        
        IOUtils.copyFilesFromAssets(mContext, getDatapath("conf") + "/" + CORE_LIMIT_PATH, toPath,
                needsUpdate, progressRange(unit, 0.76, 0.86));
        IOUtils.copyFilesFromAssets(mContext, assetStringPath, toPath, needsUpdate,
                progressRange(unit, 0.86, 0.93));
        IOUtils.copyFilesFromAssets(mContext, assetBotPath, toPath, needsUpdate,
                progressRange(unit, 0.93, 1));
        
        // 如果更新了禁卡表，检查并调整最后使用的禁卡表
        if (needsUpdate && oldBuiltInFirstLimitName != null && lastUsedLimit != null) {
            // 判断最后使用的禁卡表是否是内置的第一个禁卡表
            if (oldBuiltInFirstLimitName.equals(lastUsedLimit)) {
                // 读取新的内置禁卡表文件，获取新的第一个禁卡表名称
                try {
                    File newBuiltInFile = new File(mSettings.getResourcePath(), Constants.CORE_LIMIT_PATH);
                    if (newBuiltInFile.exists()) {
                        LimitManager tempNewBuiltInManager = new LimitManager();
                        tempNewBuiltInManager.loadFile(newBuiltInFile);
                        
                        if (!tempNewBuiltInManager.getLimitNames().isEmpty()) {
                            String newBuiltInFirstLimitName = tempNewBuiltInManager.getLimitNames().get(0);
                            
                            // 将最后使用的禁卡表更新为新的内置第一个禁卡表
                            AppsSettings.get().setLastLimit(newBuiltInFirstLimitName);
                        }
                        
                        tempNewBuiltInManager.close();
                    }
                } catch (Exception e) {
                    LogUtil.e(TAG, "Error updating limit list preference", e);
                }
            }
            // 如果最后使用的禁卡表不是内置的第一个，则保持不变（不做任何操作）
        }
        
        //替换换行符
        fixString(stringfile.getAbsolutePath());
        fixString(botfile.getAbsolutePath());
        advanceUnit(unit, 1);
        return ERROR_NONE;
    }

    private void fixString(String stringfile) {
        List<String> lines = FileUtils.readLines(stringfile, Constants.DEF_ENCODING);
        FileUtils.writeLines(stringfile, lines, Constants.DEF_ENCODING, "\n");
    }

    public void checkWindbot() {
        checkWindbot(false);
    }

    public void checkWindbot(boolean forceReinit) {
        // windbot 初始化与 RUN_WINDBOT 监听已抽取到 WindBotService：
        // 接收器进程级只注册一次；initAndroid 进程内仅成功执行一次，
        // 资源更新后(han case 0)以 forceReinit=true 强制重初始化
        WindBotService.startListening(mContext, forceReinit);
    }

    public interface ResCheckListener {
        void onResCheckFinished(int result, boolean isNewVersion);
    }
}

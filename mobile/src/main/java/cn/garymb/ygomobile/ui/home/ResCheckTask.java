package cn.garymb.ygomobile.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.SystemUtils;
import libwindbot.windbot.WindBot;
import ocgcore.CardManager;
import ocgcore.ConfigManager;
import ocgcore.DataManager;

import static cn.garymb.ygomobile.Constants.ASSETS_PATH;
import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;

public class ResCheckTask extends AsyncTask<Void, Integer, Integer> {
    public static final int ERROR_NONE = 0;
    public static final int ERROR_CORE_CONFIG = -1;
    public static final int ERROR_COPY = -2;
    public static final int ERROR_CORE_CONFIG_LOST = -3;
    private static final String TAG = "ResCheckTask";
    protected int mError = ERROR_NONE;
    MessageReceiver mReceiver = new MessageReceiver();
    private AppsSettings mSettings;
    private Context mContext;
    Handler han = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    checkWindbot();
                    break;
            }
        }
    };
    private ResCheckListener mListener;
    private DialogPlus dialog = null;
    private Handler handler;
    private boolean isNewVersion;

    @SuppressWarnings("deprecation")
    public ResCheckTask(Context context, ResCheckListener listener) {
        mContext = context;
        mListener = listener;
        handler = new Handler(context.getMainLooper());
        mSettings = AppsSettings.get();
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
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        dialog = DialogPlus.show(mContext, null, mContext.getString(R.string.check_res));
        int vercode = SystemUtils.getVersion(mContext);
        if (mSettings.getAppVersion() < vercode) {
            mSettings.setAppVersion(vercode);
            isNewVersion = true;
        } else {
            isNewVersion = false;
        }
    }

    @Override
    protected void onPostExecute(final Integer result) {
        super.onPostExecute(result);
        //关闭异常
        if (dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception e) {

            }
        }
        if (mListener != null) {
            mListener.onResCheckFinished(result, isNewVersion);
        }
    }

    private void setMessage(String msg) {
        handler.post(() -> {
            dialog.setMessage(msg);
        });
    }

    @Override
    protected Integer doInBackground(Void... params) {
        if (Constants.DEBUG)
            Log.d(TAG, "check start");
        boolean needsUpdate = isNewVersion;
        //core config
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.core_config)));
        //res
        try {
            String resPath = mSettings.getResourcePath();
            //创建游戏目录
            IOUtils.createNoMedia(resPath);
            //检查文件夹
            checkDirs();
            //复制游戏配置文件
            copyCoreConfig(resPath, needsUpdate);
            if (AppsSettings.get().isUseExtraCards()) {
                //自定义数据库无效，则用默认的
                if (!CardManager.checkDataBase(AppsSettings.get().getDataBaseFile())) {
                    AppsSettings.get().setUseExtraCards(false);
                }
            }

            //设置字体
            ConfigManager systemConf = DataManager.openConfig(mSettings.getSystemConfig());
            systemConf.setFontSize(mSettings.getFontSize());
            systemConf.close();

            //如果是新版本
            if (needsUpdate) {
                //复制卡组
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.tip_new_deck)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_DECK_PATH),
                        mSettings.getDeckDir(), needsUpdate);
                //复制卡包
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_PACK_PATH),
                        mSettings.get().getPackDeckDir(), needsUpdate);
                //复制残局
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.single_lua)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SINGLE_PATH),
                        mSettings.getSingleDir(), needsUpdate);
            }
            String[] sound1 = mContext.getAssets().list(getDatapath(Constants.CORE_SOUND_PATH));
            String[] sound2 = new File(mSettings.getSoundPath()).list();

            String[] textures1 = mContext.getAssets().list(getDatapath(Constants.CORE_SKIN_PATH));
            String[] textures2 = new File(mSettings.getCoreSkinPath()).list();

            //复制资源文件夹
            //如果sound文件夹不存在/sound资源数量不够/是更新则复制,但是不强制复制
            if (sound2 == null || (sound1 != null && sound1.length > sound2.length) || needsUpdate) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.game_sound)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SOUND_PATH),
                        mSettings.getSoundPath(), false);
            }
            //如果textures文件夹不存在/textures资源数量不够/是更新则复制,但是不强制复制
            if (textures2 == null || (textures1 != null && textures1.length > textures2.length) || needsUpdate) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.game_skins)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SKIN_PATH),
                        mSettings.getCoreSkinPath(), false);
            }
            //复制字体
            setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.font_files)));
            IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.FONT_DIRECTORY),
                    mSettings.getFontDirPath(), needsUpdate);
            //复制脚本压缩包
            if (IOUtils.hasAssets(mContext, getDatapath(Constants.CORE_SCRIPTS_ZIP))) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.scripts)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SCRIPTS_ZIP),
                        resPath, needsUpdate);
            }
            //复制数据库
            copyCdbFile(needsUpdate);
            //复制卡图压缩包
            if (IOUtils.hasAssets(mContext, getDatapath(Constants.CORE_PICS_ZIP))) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.images)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_PICS_ZIP),
                        resPath, needsUpdate);
            }
            //复制人机资源
            IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.WINDBOT_PATH),
                    resPath, needsUpdate);//mContext.getFilesDir().getPath()
            han.sendEmptyMessage(0);

            loadData();
        } catch (Exception e) {
            if (Constants.DEBUG)
                Log.e(TAG, "check", e);
            return ERROR_COPY;
        }
        return ERROR_NONE;
    }

    private void loadData() {
        setMessage(mContext.getString(R.string.loading));
        DataManager.get().load(false);
    }

    void copyCdbFile(boolean needsUpdate) throws IOException {
        File dbFile = new File(mSettings.getDataBasePath(), DATABASE_NAME);
        //如果数据库存在
        if (dbFile.exists()) {
            //如果是更新或者数据库大小小于1m
            if (needsUpdate || dbFile.length() < 1024 * 1024)
                dbFile.delete();
            else
                return;
        }
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.cards_cdb)));
        IOUtils.copyFilesFromAssets(mContext, getDatapath(DATABASE_NAME), mSettings.getDataBasePath(), needsUpdate);
    }

    private void checkDirs() {
        String[] dirs = {
                //脚本文件夹
                Constants.CORE_SCRIPT_PATH,
                //残局文件夹
                Constants.CORE_SINGLE_PATH,
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
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
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

    private int copyCoreConfig(String toPath, boolean needsUpdate) {
        try {
            String path = getDatapath("conf");
            int count = IOUtils.copyFilesFromAssets(mContext, path, toPath, needsUpdate);
            if (count < 3) {
                return ERROR_CORE_CONFIG_LOST;
            }
            //替换换行符
            File stringfile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_STRING_PATH);
            File botfile = new File(AppsSettings.get().getResourcePath(), Constants.BOT_CONF);
            fixString(stringfile.getAbsolutePath());
            fixString(botfile.getAbsolutePath());
            return ERROR_NONE;
        } catch (IOException e) {
            if (Constants.DEBUG)
                Log.e(TAG, "copy", e);
            mError = ERROR_COPY;
            return ERROR_COPY;
        }
    }

    private void fixString(String stringfile) {
        String encoding = "utf-8";
        List<String> lines = FileUtils.readLines(stringfile, encoding);
        FileUtils.writeLines(stringfile, lines, encoding, "\n");
    }

    public void checkWindbot() {
        Log.i("路径", mContext.getFilesDir().getPath());
        Log.i("路径2", mSettings.getDataBasePath() + "/" + DATABASE_NAME);
        try {
            WindBot.initAndroid(mSettings.getResourcePath(),
                    mSettings.getDataBasePath() + "/" + DATABASE_NAME,
                    mSettings.getResourcePath() + "/" + CORE_BOT_CONF_PATH);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("RUN_WINDBOT");
        mContext.registerReceiver(mReceiver, filter);
    }

    public interface ResCheckListener {
        void onResCheckFinished(int result, boolean isNewVersion);
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("RUN_WINDBOT")) {
                String args = intent.getStringExtra("args");
                WindBot.runAndroid(args);
            }
        }
    }
}

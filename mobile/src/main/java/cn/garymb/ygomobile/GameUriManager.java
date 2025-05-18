package cn.garymb.ygomobile;

import static cn.garymb.ygomobile.Constants.ACTION_OPEN_DECK;
import static cn.garymb.ygomobile.Constants.ACTION_OPEN_GAME;
import static cn.garymb.ygomobile.Constants.CORE_EXPANSIONS;
import static cn.garymb.ygomobile.Constants.CORE_LIMIT_PATH;
import static cn.garymb.ygomobile.Constants.ARG_OPEN_DECK_PATH;
import static cn.garymb.ygomobile.Constants.CORE_REPLAY_PATH;
import static cn.garymb.ygomobile.Constants.CORE_SINGLE_PATH;
import static cn.garymb.ygomobile.Constants.QUERY_NAME;
import static cn.garymb.ygomobile.Constants.REQUEST_SETTINGS_CODE;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;
import static cn.garymb.ygomobile.Constants.YPK_FILE_EX;
import static cn.garymb.ygomobile.Constants.YRP_FILE_EX;
import static cn.garymb.ygomobile.utils.ServerUtil.loadServerInfoFromZipOrYpk;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.ourygo.lib.duelassistant.util.YGODAUtil;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;


public class GameUriManager {
    private final Activity activity;
    private LimitManager limitManager;
    private StringManager stringManager;

    public GameUriManager(Activity activity) {
        this.activity = activity;
        limitManager = new LimitManager();
        stringManager = new StringManager();
    }

    /**
     * 根据intent的getData()和getXXXExtra()执行逻辑，
     *
     * @param intent
     * @return false当传入的intent.getAction()不符合可处理的action时，不做处理，返回false
     */
    public boolean doIntent(Intent intent) {
        Log.i(Constants.TAG, "doIntent");
        if (ACTION_OPEN_DECK.equals(intent.getAction())) {
            if (intent.getData() != null) {
                doUri(intent.getData());
            } else {
                String name = intent.getStringExtra(Intent.EXTRA_TEXT);
                doOpenPath(name);
            }
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (intent.getData() != null) {
                doUri(intent.getData());
            } else {
                activity.finish();
            }
        } else if (ACTION_OPEN_GAME.equals(intent.getAction())) {
            try {
                YGOGameOptions options = new YGOGameOptions();
                options.mServerAddr = intent.getStringExtra(Constants.QUERY_HOST);
                options.mUserName = intent.getStringExtra(Constants.QUERY_USER);
                options.mPort = intent.getIntExtra(Constants.QUERY_PORT, 0);
                options.mRoomName = intent.getStringExtra(Constants.QUERY_ROOM);
                YGOStarter.startGame(getActivity(), options);
            } catch (Exception e) {
                YGOUtil.showTextToast(activity.getString(R.string.start_game_error));
                activity.finish();
            }
        } else {
            return false;
        }
        return true;
    }

    public Activity getActivity() {
        return activity;
    }


    private String getPathName(String path, boolean withOutEx) {
        Log.d(Constants.TAG, "path=" + path);
        if (path != null) {
            int index = path.lastIndexOf("/");
            if (index > 0) {
                String name = path.substring(index + 1);
                if (withOutEx) {
                    index = name.lastIndexOf(".");
                    if (index > 0) {
                        //1.ydk
                        name = name.substring(0, index);
                    }
                }
                return name;
            }
        }
        return "tmp_" + System.currentTimeMillis();
    }

    private File getDeckFile(File dir, String name) {
        File file = new File(dir, name);
        if (file.exists()) {
            for (int i = 2; i < 10; i++) {
                file = new File(dir, name + "(" + i + ").ydk");
                if (!file.exists()) {
                    return file;
                }
            }
            return new File(dir, "tmp_" + System.currentTimeMillis() + YDK_FILE_EX);
        } else {
            IOUtils.createFolder(dir);
            file = new File(dir, name + YDK_FILE_EX);
        }
        return file;
    }

    private boolean isDeckDir(File file) {
        if (!Constants.COPY_YDK_FILE) {
            return true;
        }
        String deck = new File(AppsSettings.get().getDeckDir()).getAbsolutePath();
        return TextUtils.equals(deck, file.getParentFile().getAbsolutePath());
    }

    private File toLocalFile(Uri uri) {
        String path = uri.getPath();
        File remoteFile = null;
        if ("file".equals(uri.getScheme())) {
            remoteFile = new File(uri.getPath());
            if (getActivity().getApplicationInfo().targetSdkVersion > 28) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:$packageName"));
                        getActivity().startActivityForResult(intent, REQUEST_SETTINGS_CODE);
                        return null;
                    }
                }
            }
            try {
                if (!remoteFile.canRead()) {
                    Log.w(Constants.TAG, "don't read file " + remoteFile.getAbsolutePath());
                    return null;
                }
            } catch (Throwable e) {
                Log.e(Constants.TAG, "don't read file " + remoteFile.getAbsolutePath(), e);
                return null;
            }
        }
        String name = getPathName(path, false);
        File local;
        if (name.toLowerCase(Locale.US).endsWith(YDK_FILE_EX)) {
            File dir = Constants.COPY_YDK_FILE ? new File(AppsSettings.get().getDeckDir()) : new File(getActivity().getApplicationInfo().dataDir, "cache");
            local = getDeckFile(dir, getPathName(path, true));
        } else if (name.toLowerCase(Locale.US).endsWith(YPK_FILE_EX)) {
            String[] words = name.trim().split("[()（） ]+");
            File[] ypkList = AppsSettings.get().getExpansionFiles();
            for (int i = 0; i < ypkList.length; i++) {
                if (ypkList[i].getName().contains(words[0])) {
                    FileUtils.delFile(AppsSettings.get().getExpansionsPath().getAbsolutePath() + "/" + ypkList[i].getName());
                }
            }
            local = new File(AppsSettings.get().getExpansionsPath(), name);
        } else if (name.toLowerCase(Locale.US).endsWith(YRP_FILE_EX)) {
            local = new File(AppsSettings.get().getResourcePath() + "/" + CORE_REPLAY_PATH, name);
        } else if (name.toLowerCase(Locale.US).endsWith(".lua")) {
            local = new File(AppsSettings.get().getResourcePath() + "/" + CORE_SINGLE_PATH, name);
        } else if (name.toLowerCase(Locale.US).endsWith(".conf")) {
            String rename = name.contains("lflist") ? CORE_LIMIT_PATH : name;
            local = new File(AppsSettings.get().getResourcePath() + "/" + CORE_EXPANSIONS, rename);
        } else {
            local = new File(AppsSettings.get().getResourcePath() + "/temp", name);
        }
        if (local.exists()) {
            Log.w(Constants.TAG, "Overwrite file " + local.getAbsolutePath());
        }
        if (remoteFile != null && TextUtils.equals(remoteFile.getAbsolutePath(), local.getAbsolutePath())) {
            //is same path
            Log.i(Constants.TAG, "is same file " + remoteFile.getAbsolutePath() + "==" + local.getAbsolutePath());
            return local;
        }
        //copy
        ParcelFileDescriptor pfd = null;
        FileInputStream input = null;
        try {
            IOUtils.createFolder(local.getParentFile());
            if (remoteFile != null) {
                FileUtils.copyFile(remoteFile, local);
            } else {
                pfd = getActivity().getContentResolver().openFileDescriptor(uri, "r");
                input = new FileInputStream(pfd.getFileDescriptor());
                FileUtils.copyFile(input, local);
            }
        } catch (Throwable e) {
            Log.w(Constants.TAG, "copy file " + path + "->" + local.getAbsolutePath(), e);
            return null;
        } finally {
            IOUtils.close(input);
            IOUtils.close(pfd);
        }
        return local;
    }

    private void doUri(Uri uri) {
        Intent startSetting = new Intent(activity, MainActivity.class);
        if ("file".equals(uri.getScheme()) || "content".equals(uri.getScheme())) {
            File file = toLocalFile(uri);
            if (file == null || !file.exists()) {
                YGOUtil.showTextToast("open file error", Toast.LENGTH_LONG);
                return;
            }
            boolean isYdk = file.getName().toLowerCase(Locale.US).endsWith(YDK_FILE_EX);
            boolean isYpk = file.getName().toLowerCase(Locale.US).endsWith(YPK_FILE_EX);
            boolean isYrp = file.getName().toLowerCase(Locale.US).endsWith(YRP_FILE_EX);
            boolean isLua = file.getName().toLowerCase(Locale.US).endsWith(".lua");
            boolean isConf = file.getName().toLowerCase(Locale.US).endsWith(".conf");
            Log.i(Constants.TAG, "open file:" + uri + "->" + file.getAbsolutePath());
            if (isYdk) {
                startSetting.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
                activity.startActivity(startSetting);
            } else if (isYpk) {
                if (!AppsSettings.get().isReadExpansions()) {
                    startSetting.putExtra("flag", 4);
                    activity.startActivity(startSetting);//todo ??再次打开MainActivity?
                    YGOUtil.showTextToast(activity.getString(R.string.start_game_error), Toast.LENGTH_LONG);
                } else {
                    DataManager.get().load(true);
                    YGOUtil.showTextToast(activity.getString(R.string.ypk_installed), Toast.LENGTH_LONG);
                    loadServerInfoFromZipOrYpk(getActivity(), file);
                  //ypk不与excard机制相干涉

                }
            } else if (isYrp) {
                if (!YGOStarter.isGameRunning(getActivity())) {
                    YGOStarter.startGame(getActivity(), null, "-r", file.getName());
                    YGOUtil.showTextToast(activity.getString(R.string.file_installed), Toast.LENGTH_LONG);
                } else {
                    Log.w(Constants.TAG, "game is running");
                }
            } else if (isLua) {
                if (!YGOStarter.isGameRunning(getActivity())) {
                    YGOStarter.startGame(getActivity(), null, "-s", file.getName());
                    YGOUtil.showTextToast("load single lua file", Toast.LENGTH_LONG);
                } else {
                    Log.w(Constants.TAG, "game is running");
                }
            } else if (isConf) {
                DataManager.get().load(true);
                YGOUtil.showTextToast(activity.getString(R.string.restart_app), Toast.LENGTH_LONG);
            }
        } else {
            String host = uri.getHost();
//            if (!Constants.URI_HOST.equalsIgnoreCase(host)) {
//                return;
//            }
            if (Constants.URI_DECK.equals(host)) {
                String name = uri.getQueryParameter(ARG_OPEN_DECK_PATH);
                if (!TextUtils.isEmpty(name)) {
                    doOpenPath(name);
                } else {
                    YGODAUtil.deDeckListener(uri, (uri1, mainList, exList, sideList, isCompleteDeck, exception) -> {
                        if (!TextUtils.isEmpty(exception)) {
                            YGOUtil.showTextToast("卡组解析失败，原因为：" + exception);
                            return;
                        }
                        Deck deckInfo = new Deck(uri, mainList, exList, sideList);
                        File file = deckInfo.saveTemp(AppsSettings.get().getDeckDir());
                        if (!deckInfo.isCompleteDeck()) {
                            YGOUtil.showTextToast(activity.getString(R.string.tip_deckInfo_isNot_completeDeck));
                        }
                        startSetting.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
                        activity.startActivity(startSetting);
                    });
                }
            } else if (Constants.URI_ROOM.equals(host)) {
                YGODAUtil.deRoomListener(uri, (host1, port, password, exception) -> {
                    if (TextUtils.isEmpty(exception))
                        if (activity instanceof MainActivity) {
                            ((HomeActivity) activity).fragment_home.quickjoinRoom(host1, port, password);
                        } else {
                            YGOUtil.showTextToast(exception);
                        }
                });
            }
//            else if (PATH_ROOM.equals(path)) {
//                try {
//                    YGOGameOptions options = new YGOGameOptions();
//                    options.mServerAddr = uri.getQueryParameter(Constants.QUERY_HOST);
//                    options.mUserName = uri.getQueryParameter(Constants.QUERY_USER);
//                    options.mPort = Integer.parseInt(uri.getQueryParameter(Constants.QUERY_PORT));
//                    options.mRoomName = uri.getQueryParameter(Constants.QUERY_ROOM);
//                    YGOStarter.startGame(getActivity(), options, null);
//                } catch (Exception e) {
//                    Toast.makeText(getActivity(), R.string.start_game_error, Toast.LENGTH_SHORT).show();
//                    activity.finish();
//                }
//            }
        }
    }

    private void doOpenPath(String name) {
        File deck = null;
        if (!TextUtils.isEmpty(name)) {
            deck = new File(name);
            if (!deck.exists()) {
                deck = new File(AppsSettings.get().getDeckDir(), name);
            }
        }
        if (deck != null && deck.exists()) {
            Intent startSetting = new Intent(activity, MainActivity.class);
            startSetting.putExtra(Intent.EXTRA_TEXT, deck.getAbsolutePath());
            activity.startActivity(startSetting);
        } else {
            Log.w("kk", "no find " + name);
            activity.finish();
        }
    }
}

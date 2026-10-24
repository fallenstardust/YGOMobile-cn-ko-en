package cn.garymb.ygomobile.ui.settings;

import static cn.garymb.ygomobile.Constants.CORE_SKIN_AVATAR_SIZE;
import static cn.garymb.ygomobile.Constants.CORE_SKIN_BG_SIZE;
import static cn.garymb.ygomobile.Constants.CORE_SKIN_CARD_COVER_SIZE;
import static cn.garymb.ygomobile.Constants.ORI_EXPANSIONS;
import static cn.garymb.ygomobile.Constants.ORI_PICS;
import static cn.garymb.ygomobile.Constants.ORI_REPLAY;
import static cn.garymb.ygomobile.Constants.PREF_CHANGE_LOG;
import static cn.garymb.ygomobile.Constants.PREF_CHECK_UPDATE;
import static cn.garymb.ygomobile.Constants.PREF_DATA_LANGUAGE;
import static cn.garymb.ygomobile.Constants.PREF_FONT_ANTIALIAS;
import static cn.garymb.ygomobile.Constants.PREF_GAME_FONT;
import static cn.garymb.ygomobile.Constants.PREF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_KEEP_SCALE;
import static cn.garymb.ygomobile.Constants.PREF_LOCK_SCREEN;
import static cn.garymb.ygomobile.Constants.PREF_NATIVE_GAME_MODE;
import static cn.garymb.ygomobile.Constants.PREF_OPENGL_VERSION;
import static cn.garymb.ygomobile.Constants.PREF_PENDULUM_SCALE;
import static cn.garymb.ygomobile.Constants.PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_RESET_GAME_RES;
import static cn.garymb.ygomobile.Constants.PREF_START_SERVICEDUELASSISTANT;
import static cn.garymb.ygomobile.Constants.PREF_WINDOW_TOP_BOTTOM;
import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_FILE;
import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_FOLDER;
import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_IMG;
import static cn.garymb.ygomobile.Constants.URL_BILIBILI_DYNAMIC;
import static cn.garymb.ygomobile.Constants.URL_HOME_VERSION;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.getDatapath;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.ourygo.lib.duelassistant.service.DuelAssistantService;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.config.ISListConfig;
import com.yuyh.library.imgsel.ui.ISListActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.adapter.DialogImageAdapter;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.ImageItem;
import cn.garymb.ygomobile.bean.events.ExCardEvent;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.file.FileActivity;
import cn.garymb.ygomobile.ui.file.FileOpenType;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.CurImageInfo;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.ServerUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.SystemUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


/**
 * Settings fragment, base layer of the split inheritance chain
 * (SettingFragmentBase <- SettingFragment). Owns all state fields, the
 * preference read/write and UI-sync helpers, the language/quality/scale
 * selection logic, the whole skin image preview & picker subsystem, the
 * activity-result routing of file/image choosers and the nested privacy
 * policy callback/event types shared by the upper layer.
 */
abstract class SettingFragmentBase extends BaseFragemnt {

    protected static final int TYPE_SETTING_GET_VERSION_OK = 0;
    protected static final int TYPE_SETTING_GET_VERSION_FAILED = 1;
    public static String Version;
    public static String Cache_link;

    //皮肤图片预览类型
    protected static final int KIND_AVATAR_ME = 0;
    protected static final int KIND_AVATAR_OPPONENT = 1;
    protected static final int KIND_COVER1 = 2;
    protected static final int KIND_COVER2 = 3;
    protected static final int KIND_BG_GAME = 4;
    protected static final int KIND_BG_MENU = 5;
    protected static final int KIND_BG_DECK = 6;
    //字体文件选择用途
    protected static final int PURPOSE_FONT = 100;

    protected AppsSettings mSettings;
    protected HomeActivity activity;
    protected SharedPreferences mSharedPreferences;
    protected int FailedCount;
    protected PrivacyPolicyCallback privacyPolicyCallback;
    protected boolean updatingUi = false;

    protected View rootView;
    protected View llYgoproJniSettings;
    protected Switch swReadEx, swDuelAssistant, swImmersive, swLockScreen, swAntiAlias, swPendulum, swNativeGame;
    protected TextView tvVersionInfo, tvOpenGLValue, tvPaddingValue;
    protected TextView btnQualityLow, btnQualityHigh;
    protected final TextView[] langViews = new TextView[6];
    protected ImageView imgAvatarMe, imgAvatarOpponent, imgCover1, imgCover2, imgBgGame, imgBgMenu, imgBgDeck;
    protected ImageView imgScaleOriginal, imgScaleFull;

    //文件/图片选择状态（替代原curPreference机制）
    protected CurImageInfo mCurImageInfo;
    protected int mPendingPurpose = -1;//REQUEST_CHOOSE_IMG选中后要刷新的预览类型
    protected int mFileChoosePurpose = -1;//REQUEST_CHOOSE_FILE的用途（字体选择）
    /***
     * 从偏好设置读取全部状态并刷新UI
     */
    protected void syncUiState() {
        updatingUi = true;
        //版本信息与作者显示在检查更新宫格
        tvVersionInfo.setText(SystemUtils.getVersionName(getContext()) + "(" + SystemUtils.getVersion(getContext()) + ")\n"
                + YGOUtil.s(R.string.settings_about_author_pref) + " : " + YGOUtil.s(R.string.settings_author));
        setSwitch(swReadEx, getPrefBool(PREF_READ_EX, Constants.DEF_PREF_READ_EX));
        setSwitch(swDuelAssistant, mSettings.isServiceDuelAssistant());
        updateScaleSelection(mSettings.isKeepScale());
        setSwitch(swImmersive, mSettings.isImmerSiveMode());
        updateQualitySelection(mSettings.getCardQuality() != 0);
        setSwitch(swLockScreen, mSettings.isLockSreenOrientation());
        setSwitch(swAntiAlias, mSettings.isFontAntiAlias());
        setSwitch(swPendulum, mSettings.isPendulumScale());
        setSwitch(swNativeGame, mSettings.isNativeGameMode());
        updateJniSettingsVisibility();
        //OpenGL 与瀑布屏边距
        String[] oglEntries = getResources().getStringArray(R.array.opengl_version);
        int ogl = Constants.PREF_DEF_OPENGL_VERSION;
        try {
            ogl = Integer.parseInt(mSharedPreferences.getString(PREF_OPENGL_VERSION, "" + ogl));
        } catch (Exception e) {
            //忽略
        }
        tvOpenGLValue.setText(oglEntries.length > ogl ? oglEntries[ogl] : String.valueOf(ogl));
        String[] padEntries = getResources().getStringArray(R.array.screen_top_bottom_desc);
        String[] padValues = getResources().getStringArray(R.array.screen_top_bottom_value);
        String padValue = mSharedPreferences.getString(PREF_WINDOW_TOP_BOTTOM, "" + Constants.DEF_PREF_WINDOW_TOP_BOTTOM);
        int padIndex = 0;
        for (int i = 0; i < padValues.length; i++) {
            if (padValues[i].equals(padValue)) {
                padIndex = i;
                break;
            }
        }
        tvPaddingValue.setText(padEntries.length > padIndex ? padEntries[padIndex] : padValue);
        //语言选中态
        updateLangSelection(mSettings.getDataLanguage());
        updatingUi = false;
    }

    protected void setSwitch(Switch sw, boolean checked) {
        boolean old = updatingUi;
        updatingUi = true;
        sw.setChecked(checked);
        updatingUi = old;
    }

    protected boolean getPrefBool(String key, boolean def) {
        return mSharedPreferences.getBoolean(key, def);
    }

    protected void putPrefBool(String key, boolean value) {
        mSharedPreferences.edit().putBoolean(key, value).apply();
    }

    protected void putPrefString(String key, String value) {
        mSharedPreferences.edit().putString(key, value).apply();
    }
    /**
     * 根据原生 UI 游戏模式开关状态，显示/隐藏 JNI 设置项
     */
    protected void updateJniSettingsVisibility() {
        if (llYgoproJniSettings != null) {
            boolean nativeGameMode = mSettings.isNativeGameMode();
            llYgoproJniSettings.setVisibility(nativeGameMode ? View.GONE : View.VISIBLE);
        }
    }

    protected void setSwitchChecked(Switch sw, boolean checked) {
        if (sw == null) return;
        setSwitch(sw, checked);
    }

    /**
     * 原始比例开关：点击两张示意图切换，写入与原CheckBoxPreference相同的键
     */
    protected void setKeepScale(boolean keepScale) {
        mSettings.setKeepScale(keepScale);
        updateScaleSelection(keepScale);
    }

    protected void updateScaleSelection(boolean keepScale) {
        if (imgScaleOriginal == null || imgScaleFull == null) return;
        imgScaleOriginal.setSelected(keepScale);
        imgScaleFull.setSelected(!keepScale);
        imgScaleOriginal.setAlpha(keepScale ? 1f : 0.35f);
        imgScaleFull.setAlpha(keepScale ? 0.35f : 1f);
    }

    /**
     * 游戏图片质量：点击高/低按钮切换，写入与原ListPreference相同的int值（0=低 1=高）
     */
    protected void setCardQuality(boolean high) {
        mSettings.setCardQuality(high ? 1 : 0);
        updateQualitySelection(high);
    }

    protected void updateQualitySelection(boolean high) {
        if (btnQualityLow == null || btnQualityHigh == null) return;
        btnQualityLow.setBackgroundResource(high ? R.drawable.radius_p : R.drawable.radius);
        btnQualityHigh.setBackgroundResource(high ? R.drawable.radius : R.drawable.radius_p);
    }
    /***
     * 统一处理隐私政策拒绝的操作：取消服务决斗助手勾选并停止相关服务
     */
    protected void handlePrivacyPolicyRejected() {
        setSwitchChecked(swDuelAssistant, false);
        // 更新共享偏好设置
        AppsSettings.get().setServiceDuelAssistant(false);
        // 停止服务
        getContext().stopService(new Intent(getContext(), DuelAssistantService.class));
    }
    // ==================== 资料语言 ====================

    protected void updateLangSelection(int code) {
        if (rootView == null) return;
        for (int i = 0; i < langViews.length; i++) {
            if (langViews[i] != null) {
                langViews[i].setBackgroundResource(i == code ? R.drawable.radius : R.drawable.radius_p);
            }
        }
    }

    protected void changeDataLanguage(int code) {
        if (mSettings.getDataLanguage() == code) return;
        Dialog dlg = DialogPlus.show(getContext(), null, getString(R.string.message));
        VUiKit.defer().when(() -> {
            try {
                AppsSettings.languageEnum lang = AppsSettings.languageEnum.values()[code];
                if (lang == AppsSettings.languageEnum.Chinese) {
                    mSettings.copyCnData();
                } else if (lang == AppsSettings.languageEnum.Korean) {
                    mSettings.copyKorData();
                } else if (lang == AppsSettings.languageEnum.English) {
                    mSettings.copyEnData();
                } else if (lang == AppsSettings.languageEnum.Spanish) {
                    mSettings.copyEsData();
                } else if (lang == AppsSettings.languageEnum.Japanese) {
                    mSettings.copyJpData();
                } else if (lang == AppsSettings.languageEnum.Portuguese) {
                    mSettings.copyPtData();
                }
                putPrefString(PREF_DATA_LANGUAGE, String.valueOf(code));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).fail((e) -> {
            dlg.dismiss();
            Log.e(Constants.TAG, "change language failed " + e);
            YGOUtil.showTextToast(R.string.loading_failed);
        }).done((rs) -> {
            dlg.dismiss();
            YGOUtil.showTextToast(R.string.restart_app, Toast.LENGTH_LONG);
            DataManager.get().load(true);
            updateLangSelection(mSettings.getDataLanguage());
        });
    }
    // ==================== 皮肤图片（头像/卡背/背景） ====================

    protected void refreshSkinPreviews() {
        String skin = mSettings.getCoreSkinPath();
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_AVATAR_ME, CORE_SKIN_AVATAR_SIZE[0], CORE_SKIN_AVATAR_SIZE[1], imgAvatarMe);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_AVATAR_OPPONENT, CORE_SKIN_AVATAR_SIZE[0], CORE_SKIN_AVATAR_SIZE[1], imgAvatarOpponent);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_COVER, CORE_SKIN_CARD_COVER_SIZE[0], CORE_SKIN_CARD_COVER_SIZE[1], imgCover1);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_COVER2, CORE_SKIN_CARD_COVER_SIZE[0], CORE_SKIN_CARD_COVER_SIZE[1], imgCover2);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgGame);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG_MENU, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgMenu);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG_DECK, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgDeck);
    }

    protected void refreshPreviewByKind(int kind) {
        String skin = mSettings.getCoreSkinPath();
        switch (kind) {
            case KIND_AVATAR_ME:
                mSettings.setImage(skin + "/" + Constants.CORE_SKIN_AVATAR_ME, CORE_SKIN_AVATAR_SIZE[0], CORE_SKIN_AVATAR_SIZE[1], imgAvatarMe);
                break;
            case KIND_AVATAR_OPPONENT:
                mSettings.setImage(skin + "/" + Constants.CORE_SKIN_AVATAR_OPPONENT, CORE_SKIN_AVATAR_SIZE[0], CORE_SKIN_AVATAR_SIZE[1], imgAvatarOpponent);
                break;
            case KIND_COVER1:
                mSettings.setImage(skin + "/" + Constants.CORE_SKIN_COVER, CORE_SKIN_CARD_COVER_SIZE[0], CORE_SKIN_CARD_COVER_SIZE[1], imgCover1);
                break;
            case KIND_COVER2:
                mSettings.setImage(skin + "/" + Constants.CORE_SKIN_COVER2, CORE_SKIN_CARD_COVER_SIZE[0], CORE_SKIN_CARD_COVER_SIZE[1], imgCover2);
                break;
            case KIND_BG_GAME:
                mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgGame);
                break;
            case KIND_BG_MENU:
                mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG_MENU, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgMenu);
                break;
            case KIND_BG_DECK:
                mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG_DECK, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgDeck);
                break;
            default:
                break;
        }
    }

    protected String outFileOfKind(int kind) {
        String skin = mSettings.getCoreSkinPath();
        switch (kind) {
            case KIND_AVATAR_ME:
                return new File(skin, Constants.CORE_SKIN_AVATAR_ME).getAbsolutePath();
            case KIND_AVATAR_OPPONENT:
                return new File(skin, Constants.CORE_SKIN_AVATAR_OPPONENT).getAbsolutePath();
            case KIND_COVER1:
                return new File(skin, Constants.CORE_SKIN_COVER).getAbsolutePath();
            case KIND_COVER2:
                return new File(skin, Constants.CORE_SKIN_COVER2).getAbsolutePath();
            case KIND_BG_GAME:
                return new File(skin, Constants.CORE_SKIN_BG).getAbsolutePath();
            case KIND_BG_MENU:
                return new File(skin, Constants.CORE_SKIN_BG_MENU).getAbsolutePath();
            case KIND_BG_DECK:
                return new File(skin, Constants.CORE_SKIN_BG_DECK).getAbsolutePath();
            default:
                return null;
        }
    }

    protected ImageView imageViewOfKind(int kind) {
        switch (kind) {
            case KIND_AVATAR_ME:
                return imgAvatarMe;
            case KIND_AVATAR_OPPONENT:
                return imgAvatarOpponent;
            case KIND_COVER1:
                return imgCover1;
            case KIND_COVER2:
                return imgCover2;
            case KIND_BG_GAME:
                return imgBgGame;
            case KIND_BG_MENU:
                return imgBgMenu;
            case KIND_BG_DECK:
                return imgBgDeck;
            default:
                return null;
        }
    }

    protected void openImagePicker(int kind) {
        String imagePath;
        int[] size;
        switch (kind) {
            case KIND_AVATAR_ME:
            case KIND_AVATAR_OPPONENT:
                imagePath = mSettings.getAvatarPath();
                size = CORE_SKIN_AVATAR_SIZE;
                break;
            case KIND_COVER1:
            case KIND_COVER2:
                imagePath = mSettings.getCoverPath();
                size = CORE_SKIN_CARD_COVER_SIZE;
                break;
            default:
                imagePath = mSettings.getBgPath();
                size = CORE_SKIN_BG_SIZE;
                break;
        }
        DialogloadImages(imageViewOfKind(kind), imagePath, size, outFileOfKind(kind), kind);
    }

    protected void DialogloadImages(ImageView imageView, String imagePath, int[] itemWidth_itemHeight, String outFile, int kind) {
        final DialogPlus dlg = new DialogPlus(getContext());
        dlg.setContentView(R.layout.dialog_image_select);
        dlg.setTitle(R.string.dialog_select_image);
        dlg.show();
        GridView vImgSel = dlg.bind(R.id.gridView);
        ArrayList<ImageItem> items = new ArrayList<>();
        // 先添加相册选择item，保证对话框即时弹出
        items.add(new ImageItem("album_item", true));

        // 设置适配器
        DialogImageAdapter dialogImageAdapter = new DialogImageAdapter(dlg, getContext(), imageView, items, itemWidth_itemHeight, outFile, (outFilePath, title, width, height) -> {
            // 相册入口：跳系统图片选择+裁剪，回调后刷新对应预览
            showImageCropChooser(title, outFilePath, true, itemWidth_itemHeight[0], itemWidth_itemHeight[1], kind);
        });
        vImgSel.setAdapter(dialogImageAdapter);

        // 本地图片过多时，目录扫描(listFiles)会阻塞主线程导致弹窗卡顿，改到后台线程执行，扫描完再回填
        final ArrayList<ImageItem> scanned = new ArrayList<>();
        VUiKit.defer().when(() -> {
            File directory = new File(imagePath);
            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        if (file.isFile() && (name.endsWith(".jpg") || name.endsWith(".png"))) {
                            scanned.add(new ImageItem(file.getAbsolutePath(), false));
                        }
                    }
                }
            }
        }).done((re) -> dialogImageAdapter.addItems(scanned));
    }

    // ==================== 文件/图片选择（原PreferenceFragmentPlus逻辑） ====================

    protected void showFileChooser(int purpose, String type, String defPath, String title) {
        mFileChoosePurpose = purpose;
        Intent intent = FileActivity.getIntent(getActivity(), title, type, defPath, false, FileOpenType.SelectFile);
        startActivityForResult(intent, REQUEST_CHOOSE_FILE);
    }

    protected void showFolderChooser(String defPath, String title) {
        mFileChoosePurpose = -1;
        Intent intent = FileActivity.getIntent(getActivity(), title, null, defPath, false, FileOpenType.SelectFolder);
        startActivityForResult(intent, REQUEST_CHOOSE_FOLDER);
    }

    protected void showImageCropChooser(String title, String outFile, boolean isJpeg, int width, int height, int kind) {
        mCurImageInfo = new CurImageInfo();
        mCurImageInfo.mOutFile = outFile;
        mCurImageInfo.mJpeg = isJpeg;
        mCurImageInfo.width = width;
        mCurImageInfo.height = height;
        mCurImageInfo.mCurTitle = title;
        mPendingPurpose = kind;
        ISListConfig config = new ISListConfig.Builder()
                // 是否多选, 默认true
                .multiSelect(false)
                // 是否记住上次选中记录, 仅当multiSelect为true的时候配置，默认为true
                .rememberSelected(false)
                // "确定"按钮背景色
                .btnBgColor(Color.BLACK)
                // "确定"按钮文字颜色
                .btnTextColor(Color.WHITE)
                // 使用沉浸式状态栏
                .statusBarColor(Color.parseColor("#11113d"))
                // 返回图标ResId
                .backResId(R.drawable.ic_back)
                // 标题
                .title(getString(R.string.images))
                // 标题文字颜色
                .titleColor(Color.WHITE)
                // TitleBar背景色
                .titleBgColor(Color.parseColor("#11113d"))
                .needCrop(true)
                // 裁剪大小。needCrop为true的时候配置
                .cropSize(mCurImageInfo.width, mCurImageInfo.height, mCurImageInfo.width, mCurImageInfo.height)
                // 第一个是否显示相机，默认true
                .needCamera(false)
                // 最大选择图片数量，默认9
                .maxNum(1)
                .build();

        // 跳转到图片选择器
        ISNav.getInstance().toListActivity(this, config, REQUEST_CHOOSE_IMG);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHOOSE_IMG && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> photos = data.getStringArrayListExtra(ISListActivity.INTENT_RESULT);
            if (mCurImageInfo != null && photos != null && !photos.isEmpty()) {
                String cachePath = photos.get(0);
                try {
                    FileUtils.copyFile(cachePath, mCurImageInfo.mOutFile);
                    refreshPreviewByKind(mPendingPurpose);
                } catch (IOException e) {
                    YGOUtil.showTextToast(e + "", Toast.LENGTH_LONG);
                }
            }
            mCurImageInfo = null;
            mPendingPurpose = -1;
        } else if (requestCode == REQUEST_CHOOSE_FILE) {
            //选择文件
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    File file = new File(uri.getPath());
                    if (file.exists()) {
                        onChooseFileOk(file.getAbsolutePath());
                        return;
                    }
                }
            }
            mFileChoosePurpose = -1;
        } else if (requestCode == REQUEST_CHOOSE_FOLDER) {
            //选择文件夹
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    File file = new File(uri.getPath());
                    if (file.exists()) {
                        onChooseFolderOk(file.getAbsolutePath());
                        return;
                    }
                }
            }
            mFileChoosePurpose = -1;
        }
    }

    protected void onChooseFileOk(String file) {
        if (mFileChoosePurpose == PURPOSE_FONT) {
            //选择ttf字体：与旧实现一致，写入偏好键并同步字体路径
            putPrefString(PREF_GAME_FONT, file);
            mSettings.setFontPath(file);
            YGOUtil.showTextToast(R.string.restart_app, Toast.LENGTH_LONG);
        }
        mFileChoosePurpose = -1;
    }

    protected void onChooseFolderOk(String folder) {
        //预留：游戏资源目录选择（当前界面无入口，与原注释掉的PREF_GAME_PATH项一致）
        mFileChoosePurpose = -1;
    }
    protected void setPendlumScale(boolean ok) {
        if (Constants.DEBUG)
            Log.i("kk", "setPendlumScale " + ok);
        File dir = new File(mSettings.getResourcePath(), Constants.CORE_SKIN_PENDULUM_PATH);
        if (ok) {
            //rename
            Dialog dlg = DialogPlus.show(getContext(), null, getString(R.string.coping_pendulum_image));
            VUiKit.defer().when(() -> {
                try {
                    IOUtils.createFolder(dir);
                    IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_SKIN_PENDULUM_PATH),
                            dir.getAbsolutePath(), false);
                } catch (IOException e) {
                }
            }).done((re) -> {
                dlg.dismiss();
            });
        } else {
            IOUtils.delete(dir);
        }
    }
    protected void arrangeCodeList(String code) {
        BufferedReader br = new BufferedReader(new StringReader(code));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                String[] words = line.trim().split("[ ]+");
                activity.pre_code_list.add(Integer.valueOf(words[0]));
                activity.released_code_list.add(Integer.valueOf(words[1]));

            }
        } catch (Exception e) {
            Log.e(Constants.TAG, e + "");
        } finally {

        }
    }
    // 添加隐私政策回调接口
    public interface PrivacyPolicyCallback {
        void onPrivacyPolicyResult(boolean agreed);
    }

    // 添加事件监听类
    public static class PrivacyPolicyAgreedEvent {
        public boolean agreed;

        public PrivacyPolicyAgreedEvent(boolean agreed) {
            this.agreed = agreed;
        }
    }
}

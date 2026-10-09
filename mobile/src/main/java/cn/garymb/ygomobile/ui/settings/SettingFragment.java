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
 * 设置页：由Preference体系迁移为宫格自定义布局，完全继承BaseFragemnt。
 * 所有偏好读写沿用原Constants键与AppsSettings的SharedPreferences，保证功能不变。
 */
public class SettingFragment extends BaseFragemnt implements View.OnClickListener {
    private static final int TYPE_SETTING_GET_VERSION_OK = 0;
    private static final int TYPE_SETTING_GET_VERSION_FAILED = 1;
    public static String Version;
    public static String Cache_link;

    //皮肤图片预览类型
    private static final int KIND_AVATAR_ME = 0;
    private static final int KIND_AVATAR_OPPONENT = 1;
    private static final int KIND_COVER1 = 2;
    private static final int KIND_COVER2 = 3;
    private static final int KIND_BG_GAME = 4;
    private static final int KIND_BG_MENU = 5;
    private static final int KIND_BG_DECK = 6;
    //字体文件选择用途
    private static final int PURPOSE_FONT = 100;

    private AppsSettings mSettings;
    private HomeActivity activity;
    private SharedPreferences mSharedPreferences;
    private int FailedCount;
    private PrivacyPolicyCallback privacyPolicyCallback;
    private boolean updatingUi = false;

    private View rootView;
    private Switch swReadEx, swDuelAssistant, swImmersive, swLockScreen, swAntiAlias, swPendulum, swNativeGame;
    private TextView tvVersionInfo, tvOpenGLValue, tvPaddingValue;
    private TextView btnQualityLow, btnQualityHigh;
    private final TextView[] langViews = new TextView[6];
    private ImageView imgAvatarMe, imgAvatarOpponent, imgCover1, imgCover2, imgBgGame, imgBgMenu, imgBgDeck;
    private ImageView imgScaleOriginal, imgScaleFull;

    //文件/图片选择状态（替代原curPreference机制）
    private CurImageInfo mCurImageInfo;
    private int mPendingPurpose = -1;//REQUEST_CHOOSE_IMG选中后要刷新的预览类型
    private int mFileChoosePurpose = -1;//REQUEST_CHOOSE_FILE的用途（字体选择）

    public SettingFragment() {

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view;
        if (isHorizontal)
            view = inflater.inflate(R.layout.fragment_settings_grid_horizontal, container, false);
        else
            view = inflater.inflate(R.layout.fragment_settings_grid, container, false);
        rootView = view;
        activity = (HomeActivity) getActivity();
        mSettings = AppsSettings.get();
        mSharedPreferences = mSettings.getSharedPreferences();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        initViews(view);
        syncUiState();
        refreshSkinPreviews();
        return view;
    }

    private void initViews(View view) {
        //宫格按钮
        view.findViewById(R.id.cell_reset_res).setOnClickListener(this);
        view.findViewById(R.id.cell_check_update).setOnClickListener(this);
        view.findViewById(R.id.cell_check_update).setOnLongClickListener(v -> {
            //长按选择游戏字体ttf
            showFileChooser(PURPOSE_FONT, "*.ttf", mSettings.getFontDirPath(), getString(R.string.dialog_select_file));
            return true;
        });
        view.findViewById(R.id.cell_change_log).setOnClickListener(this);
        view.findViewById(R.id.cell_join_qq).setOnClickListener(this);
        view.findViewById(R.id.cell_bilibili).setOnClickListener(this);
        view.findViewById(R.id.cell_opengl).setOnClickListener(this);
        view.findViewById(R.id.cell_screen_padding).setOnClickListener(this);
        view.findViewById(R.id.cell_delete_ex).setOnClickListener(this);
        view.findViewById(R.id.cell_privacy_policy).setOnClickListener(this);
        tvVersionInfo = view.findViewById(R.id.tv_version_info);
        tvOpenGLValue = view.findViewById(R.id.tv_opengl_value);
        tvPaddingValue = view.findViewById(R.id.tv_padding_value);

        //皮肤图片预览
        imgAvatarMe = view.findViewById(R.id.img_avatar_me);
        imgAvatarOpponent = view.findViewById(R.id.img_avatar_opponent);
        imgCover1 = view.findViewById(R.id.img_cover1);
        imgCover2 = view.findViewById(R.id.img_cover2);
        imgBgGame = view.findViewById(R.id.img_bg_game);
        imgBgMenu = view.findViewById(R.id.img_bg_menu);
        imgBgDeck = view.findViewById(R.id.img_bg_deck);
        imgAvatarMe.setOnClickListener(this);
        imgAvatarOpponent.setOnClickListener(this);
        imgCover1.setOnClickListener(this);
        imgCover2.setOnClickListener(this);
        imgBgGame.setOnClickListener(this);
        imgBgMenu.setOnClickListener(this);
        imgBgDeck.setOnClickListener(this);

        //原始比例示意图（点击切换）
        imgScaleOriginal = view.findViewById(R.id.img_scale_original);
        imgScaleFull = view.findViewById(R.id.img_scale_full);
        imgScaleOriginal.setOnClickListener(this);
        imgScaleFull.setOnClickListener(this);

        //开关
        swReadEx = view.findViewById(R.id.switch_read_ex);
        swDuelAssistant = view.findViewById(R.id.switch_duel_assistant);
        swImmersive = view.findViewById(R.id.switch_immersive);
        swLockScreen = view.findViewById(R.id.switch_lock_screen);
        swAntiAlias = view.findViewById(R.id.switch_font_antialias);
        swPendulum = view.findViewById(R.id.switch_pendulum);
        swNativeGame = view.findViewById(R.id.switch_native_game);
        swReadEx.setOnCheckedChangeListener(this::onSwitchChanged);
        swDuelAssistant.setOnCheckedChangeListener(this::onSwitchChanged);
        swImmersive.setOnCheckedChangeListener(this::onSwitchChanged);
        swLockScreen.setOnCheckedChangeListener(this::onSwitchChanged);
        swAntiAlias.setOnCheckedChangeListener(this::onSwitchChanged);
        swPendulum.setOnCheckedChangeListener(this::onSwitchChanged);
        swNativeGame.setOnCheckedChangeListener(this::onSwitchChanged);

        //游戏图片质量：高/低两个按钮切换
        btnQualityLow = view.findViewById(R.id.btn_quality_low);
        btnQualityHigh = view.findViewById(R.id.btn_quality_high);
        btnQualityLow.setOnClickListener(this);
        btnQualityHigh.setOnClickListener(this);

        //语言圆形单选
        int[] langIds = new int[]{R.id.lang_0, R.id.lang_1, R.id.lang_2, R.id.lang_3, R.id.lang_4, R.id.lang_5};
        for (int i = 0; i < langIds.length; i++) {
            langViews[i] = view.findViewById(langIds[i]);
            final int code = i;
            langViews[i].setOnClickListener(v -> changeDataLanguage(code));
        }
    }

    /***
     * 从偏好设置读取全部状态并刷新UI
     */
    private void syncUiState() {
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
        //OpenGL与瀑布屏边距
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

    private void setSwitch(Switch sw, boolean checked) {
        boolean old = updatingUi;
        updatingUi = true;
        sw.setChecked(checked);
        updatingUi = old;
    }

    private boolean getPrefBool(String key, boolean def) {
        return mSharedPreferences.getBoolean(key, def);
    }

    private void putPrefBool(String key, boolean value) {
        mSharedPreferences.edit().putBoolean(key, value).apply();
    }

    private void putPrefString(String key, String value) {
        mSharedPreferences.edit().putString(key, value).apply();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.cell_reset_res) {
            updateImages();
        } else if (id == R.id.cell_check_update) {
            FailedCount = 0;
            checkUpgrade(URL_HOME_VERSION);
        } else if (id == R.id.cell_change_log) {
            new DialogPlus(getContext())
                    .setTitleText(getString(R.string.settings_about_change_log))
                    .loadUrl("file:///android_asset/changelog.html", Color.TRANSPARENT)
                    .show();
        } else if (id == R.id.cell_join_qq) {
            String groupkey = "anEjPCDdhLgxtfLre-nT52G1Coye3LkK";
            joinQQGroup(groupkey);
        } else if (id == R.id.cell_bilibili) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(URL_BILIBILI_DYNAMIC));
            startActivity(intent);
        } else if (id == R.id.cell_opengl) {
            showOpenGLDialog();
        } else if (id == R.id.cell_screen_padding) {
            showScreenPaddingDialog();
        } else if (id == R.id.cell_privacy_policy) {
            // 为隐私政策条目点击也设置回调处理
            PrivacyPolicyCallback policyCallback = new PrivacyPolicyCallback() {
                @Override
                public void onPrivacyPolicyResult(boolean agreed) {
                    if (agreed) {
                        // 用户同意隐私政策时，自动勾选服务决斗助手
                        setSwitchChecked(swDuelAssistant, true);
                    } else {
                        // 用户拒绝隐私政策时的特殊处理
                        handlePrivacyPolicyRejected();
                    }
                }
            };
            activity.showPrivacyPolicyDialogWithCallback(policyCallback);
        } else if (id == R.id.img_avatar_me) {
            openImagePicker(KIND_AVATAR_ME);
        } else if (id == R.id.img_avatar_opponent) {
            openImagePicker(KIND_AVATAR_OPPONENT);
        } else if (id == R.id.img_cover1) {
            openImagePicker(KIND_COVER1);
        } else if (id == R.id.img_cover2) {
            openImagePicker(KIND_COVER2);
        } else if (id == R.id.img_bg_game) {
            openImagePicker(KIND_BG_GAME);
        } else if (id == R.id.img_bg_menu) {
            openImagePicker(KIND_BG_MENU);
        } else if (id == R.id.img_bg_deck) {
            openImagePicker(KIND_BG_DECK);
        } else if (id == R.id.img_scale_original) {
            setKeepScale(true);
        } else if (id == R.id.img_scale_full) {
            setKeepScale(false);
        } else if (id == R.id.btn_quality_low) {
            setCardQuality(false);
        } else if (id == R.id.btn_quality_high) {
            setCardQuality(true);
        } else if (id == R.id.cell_delete_ex) {
            showDeleteExpDialog();
        }
    }

    /**
     * 统一开关处理，写入与旧CheckBoxPreference完全相同的键与类型
     */
    public void onSwitchChanged(CompoundButton button, boolean checked) {
        if (updatingUi) return;
        int id = button.getId();
        if (id == R.id.switch_read_ex) {
            putPrefBool(PREF_READ_EX, checked);
            //设置使用额外卡库后重新加载卡片数据
            DataManager.get().load(true);
            EventBus.getDefault().postSticky(new ExCardEvent(ExCardEvent.EventType.exCardPrefChange));
        } else if (id == R.id.switch_duel_assistant) {
            //开关决斗助手
            if (checked) {
                if (!SharedPreferenceUtil.isPrivacyPolicyAgreed()) {
                    // 设置回调来处理隐私政策结果
                    privacyPolicyCallback = new PrivacyPolicyCallback() {
                        @Override
                        public void onPrivacyPolicyResult(boolean agreed) {
                            if (agreed) {
                                // 用户同意隐私政策，自动勾选并启动服务
                                AppsSettings.get().setServiceDuelAssistant(true);
                                setSwitchChecked(swDuelAssistant, true);
                                getContext().startService(new Intent(getContext(), DuelAssistantService.class));
                            } else {
                                // 用户拒绝隐私政策，统一处理拒绝操作
                                handlePrivacyPolicyRejected();
                            }
                            privacyPolicyCallback = null; // 清除回调引用
                        }
                    };
                    activity.showPrivacyPolicyDialogWithCallback(privacyPolicyCallback);
                } else {
                    // 已经同意隐私政策，直接启动服务
                    putPrefBool(PREF_START_SERVICEDUELASSISTANT, true);
                    AppsSettings.get().setServiceDuelAssistant(true);
                    getContext().startService(new Intent(getContext(), DuelAssistantService.class));
                }
            } else {
                // 取消勾选，停止服务
                getContext().stopService(new Intent(getContext(), DuelAssistantService.class));
                AppsSettings.get().setServiceDuelAssistant(false);
            }
        } else if (id == R.id.switch_immersive) {
            mSettings.setImmerSiveMode(checked);
        } else if (id == R.id.switch_lock_screen) {
            putPrefBool(PREF_LOCK_SCREEN, checked);
        } else if (id == R.id.switch_font_antialias) {
            putPrefBool(PREF_FONT_ANTIALIAS, checked);
        } else if (id == R.id.switch_pendulum) {
            putPrefBool(PREF_PENDULUM_SCALE, checked);
            setPendlumScale(checked);
        } else if (id == R.id.switch_native_game) {
            putPrefBool(PREF_NATIVE_GAME_MODE, checked);
        }
    }

    private void setSwitchChecked(Switch sw, boolean checked) {
        if (sw == null) return;
        setSwitch(sw, checked);
    }

    /**
     * 原始比例开关：点击两张示意图切换，写入与原CheckBoxPreference相同的键
     */
    private void setKeepScale(boolean keepScale) {
        mSettings.setKeepScale(keepScale);
        updateScaleSelection(keepScale);
    }

    private void updateScaleSelection(boolean keepScale) {
        if (imgScaleOriginal == null || imgScaleFull == null) return;
        imgScaleOriginal.setSelected(keepScale);
        imgScaleFull.setSelected(!keepScale);
        imgScaleOriginal.setAlpha(keepScale ? 1f : 0.35f);
        imgScaleFull.setAlpha(keepScale ? 0.35f : 1f);
    }

    /**
     * 游戏图片质量：点击高/低按钮切换，写入与原ListPreference相同的int值（0=低 1=高）
     */
    private void setCardQuality(boolean high) {
        mSettings.setCardQuality(high ? 1 : 0);
        updateQualitySelection(high);
    }

    private void updateQualitySelection(boolean high) {
        if (btnQualityLow == null || btnQualityHigh == null) return;
        btnQualityLow.setBackgroundResource(high ? R.drawable.radius_p : R.drawable.radius);
        btnQualityHigh.setBackgroundResource(high ? R.drawable.radius : R.drawable.radius_p);
    }

    /**
     * 删除扩展卡包：弹窗列出已安装的ypk文件，长按对应名称删除（沿用原实现逻辑）
     */
    private void showDeleteExpDialog() {
        File dir = mSettings.getExpansionsPath();
        File[] ypks = dir.listFiles();
        List<String> list = new ArrayList<>();
        if (ypks != null) {
            for (File file : ypks) {
                list.add(file.getName());
            }
        }
        SimpleListAdapter simpleListAdapter = new SimpleListAdapter(getContext());
        simpleListAdapter.set(list);
        final DialogPlus dialog = new DialogPlus(getContext());
        dialog.setTitle(R.string.ypk_delete);
        dialog.setContentView(R.layout.dialog_edit_and_list);
        EditText editText = dialog.bind(R.id.room_name);
        editText.setVisibility(View.GONE);//不显示输入框
        ListView listView = dialog.bind(R.id.room_list);
        listView.setAdapter(simpleListAdapter);
        listView.setOnItemLongClickListener((a, v, i, index) -> {
            /* 删除先行卡 */
            String name = simpleListAdapter.getItemById(index);
            int pos = simpleListAdapter.findItem(name);
            if (pos >= 0) {
                simpleListAdapter.remove(pos);
                simpleListAdapter.notifyDataSetChanged();
                FileUtils.delFile(mSettings.getExpansionsPath().getAbsolutePath() + "/" + name);
                DataManager.get().load(true);
                YGOUtil.showTextToast(R.string.done, Toast.LENGTH_LONG);
                if (name.contains(Constants.officialExCardPackageName)) {//如果删除的是官方先行卡ypk，则更新其相关UI状态
                    SharedPreferenceUtil.setExpansionDataVer(null);//删除先行卡后，更新版本状态
                    ServerUtil.exCardState = ServerUtil.ExCardState.NEED_UPDATE;
                    EventBus.getDefault().postSticky(new ExCardEvent(ExCardEvent.EventType.exCardPackageChange));//删除后，通知UI做更新
                }
            }
            return true;
        });
        dialog.show();
    }

    /***
     * 统一处理隐私政策拒绝的操作：取消服务决斗助手勾选并停止相关服务
     */
    private void handlePrivacyPolicyRejected() {
        setSwitchChecked(swDuelAssistant, false);
        // 更新共享偏好设置
        AppsSettings.get().setServiceDuelAssistant(false);
        // 停止服务
        getContext().stopService(new Intent(getContext(), DuelAssistantService.class));
    }

    // ==================== OpenGL / 屏幕边距 ====================

    private void showOpenGLDialog() {
        String[] entries = getResources().getStringArray(R.array.opengl_version);
        String[] values = getResources().getStringArray(R.array.opengl_version_value);
        int current = mSettings.getOpenglVersion();
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.settings_game_opengl)
                .setSingleChoiceItems(entries, current, (dlg, which) -> {
                    putPrefString(PREF_OPENGL_VERSION, values[which]);
                    syncUiState();
                    dlg.dismiss();
                })
                .setNegativeButton(R.string.Cancel, null)
                .show();
    }

    private void showScreenPaddingDialog() {
        String[] entries = getResources().getStringArray(R.array.screen_top_bottom_desc);
        String[] values = getResources().getStringArray(R.array.screen_top_bottom_value);
        String padValue = mSharedPreferences.getString(PREF_WINDOW_TOP_BOTTOM, "" + Constants.DEF_PREF_WINDOW_TOP_BOTTOM);
        int current = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(padValue)) {
                current = i;
                break;
            }
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.settings_screen_padding)
                .setSingleChoiceItems(entries, current, (dlg, which) -> {
                    putPrefString(PREF_WINDOW_TOP_BOTTOM, values[which]);
                    syncUiState();
                    dlg.dismiss();
                })
                .setNegativeButton(R.string.Cancel, null)
                .show();
    }

    // ==================== 资料语言 ====================

    private void updateLangSelection(int code) {
        if (rootView == null) return;
        for (int i = 0; i < langViews.length; i++) {
            if (langViews[i] != null) {
                langViews[i].setBackgroundResource(i == code ? R.drawable.radius : R.drawable.radius_p);
            }
        }
    }

    private void changeDataLanguage(int code) {
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

    private void refreshSkinPreviews() {
        String skin = mSettings.getCoreSkinPath();
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_AVATAR_ME, CORE_SKIN_AVATAR_SIZE[0], CORE_SKIN_AVATAR_SIZE[1], imgAvatarMe);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_AVATAR_OPPONENT, CORE_SKIN_AVATAR_SIZE[0], CORE_SKIN_AVATAR_SIZE[1], imgAvatarOpponent);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_COVER, CORE_SKIN_CARD_COVER_SIZE[0], CORE_SKIN_CARD_COVER_SIZE[1], imgCover1);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_COVER2, CORE_SKIN_CARD_COVER_SIZE[0], CORE_SKIN_CARD_COVER_SIZE[1], imgCover2);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgGame);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG_MENU, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgMenu);
        mSettings.setImage(skin + "/" + Constants.CORE_SKIN_BG_DECK, CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1], imgBgDeck);
    }

    private void refreshPreviewByKind(int kind) {
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

    private String outFileOfKind(int kind) {
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

    private ImageView imageViewOfKind(int kind) {
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

    private void openImagePicker(int kind) {
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

    private void DialogloadImages(ImageView imageView, String imagePath, int[] itemWidth_itemHeight, String outFile, int kind) {
        final DialogPlus dlg = new DialogPlus(getContext());
        dlg.setContentView(R.layout.dialog_image_select);
        dlg.setTitle(R.string.dialog_select_image);
        dlg.show();
        GridView vImgSel = dlg.bind(R.id.gridView);
        ArrayList<ImageItem> items = new ArrayList<>();
        // 添加相册选择item
        items.add(new ImageItem("album_item", true));
        File directory = new File(imagePath);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (file.isFile() && (file.getName().endsWith(".jpg") || file.getName().endsWith(".png"))) {
                    items.add(new ImageItem(file.getAbsolutePath(), false));
                }
            }
        }

        // 设置适配器
        DialogImageAdapter dialogImageAdapter = new DialogImageAdapter(dlg, getContext(), imageView, items, itemWidth_itemHeight, outFile, (outFilePath, title, width, height) -> {
            // 相册入口：跳系统图片选择+裁剪，回调后刷新对应预览
            showImageCropChooser(title, outFilePath, true, itemWidth_itemHeight[0], itemWidth_itemHeight[1], kind);
        });
        vImgSel.setAdapter(dialogImageAdapter);
    }

    // ==================== 文件/图片选择（原PreferenceFragmentPlus逻辑） ====================

    private void showFileChooser(int purpose, String type, String defPath, String title) {
        mFileChoosePurpose = purpose;
        Intent intent = FileActivity.getIntent(getActivity(), title, type, defPath, false, FileOpenType.SelectFile);
        startActivityForResult(intent, REQUEST_CHOOSE_FILE);
    }

    protected void showFolderChooser(String defPath, String title) {
        mFileChoosePurpose = -1;
        Intent intent = FileActivity.getIntent(getActivity(), title, null, defPath, false, FileOpenType.SelectFolder);
        startActivityForResult(intent, REQUEST_CHOOSE_FOLDER);
    }

    private void showImageCropChooser(String title, String outFile, boolean isJpeg, int width, int height, int kind) {
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

    private void onChooseFileOk(String file) {
        if (mFileChoosePurpose == PURPOSE_FONT) {
            //选择ttf字体：与旧实现一致，写入偏好键并同步字体路径
            putPrefString(PREF_GAME_FONT, file);
            mSettings.setFontPath(file);
            YGOUtil.showTextToast(R.string.restart_app, Toast.LENGTH_LONG);
        }
        mFileChoosePurpose = -1;
    }

    private void onChooseFolderOk(String folder) {
        //预留：游戏资源目录选择（当前界面无入口，与原注释掉的PREF_GAME_PATH项一致）
        mFileChoosePurpose = -1;
    }

    // ==================== 以下功能自旧实现平移 ====================

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_SETTING_GET_VERSION_OK:
                    parseVersionJson(msg.obj.toString());
                    break;
                case TYPE_SETTING_GET_VERSION_FAILED:
                    ++FailedCount;
                    if (FailedCount <= 2) {
                        checkUpgrade(URL_HOME_VERSION);
                    } else {
                        showBilibiliDialog();
                    }
                    break;
            }

        }
    };

    private void setPendlumScale(boolean ok) {
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

    public void updateImages() {
        DialogPlus dialog = DialogPlus.show(getContext(), null, getString(R.string.message));
        dialog.show();
        VUiKit.defer().when(() -> {
            try {
                //.nomedia
                IOUtils.createNoMedia(mSettings.getResourcePath());
                //删除script文件夹，因为已经直接从scripts.zip读取script
                FileUtils.delFile(mSettings.getResourcePath() + "/" + Constants.CORE_SCRIPT_PATH);
                //复制卡图包
                if (IOUtils.hasAssets(getContext(), getDatapath(Constants.CORE_PICS_ZIP))) {
                    IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_PICS_ZIP), mSettings.getResourcePath(), true);
                }
                //复制脚本包
                if (IOUtils.hasAssets(getContext(), getDatapath(Constants.CORE_SCRIPTS_ZIP))) {
                    IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_SCRIPTS_ZIP), mSettings.getResourcePath(), true);
                }
                //复制textures下的贴图文件
                IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_SKIN_PATH), mSettings.getCoreSkinPath(), false);
                //先删除已存在的字体再复制字体
                String fonts = mSettings.getResourcePath() + "/" + Constants.FONT_DIRECTORY;
                if (new File(fonts).list() != null)
                    FileUtils.delFile(fonts);
                IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.FONT_DIRECTORY), mSettings.getFontDirPath(), true);
                //根据系统语言复制特定资料文件
                if (mSettings.getDataLanguage() == -1) {//如果未在App中指定语言，则查询系统语言并进行设置
                    String language = getContext().getResources().getConfiguration().locale.getLanguage();
                    if (!language.isEmpty()) {
                        if (language.equals(AppsSettings.languageEnum.Chinese.name)) {
                            mSettings.copyCnData();
                        } else if (language.equals(AppsSettings.languageEnum.Korean.name)) {
                            mSettings.copyKorData();
                        } else if (language.equals(AppsSettings.languageEnum.Spanish.name)) {
                            mSettings.copyEsData();
                        } else if (language.equals(AppsSettings.languageEnum.Japanese.name)) {
                            mSettings.copyJpData();
                        } else if (language.equals(AppsSettings.languageEnum.Portuguese.name)) {
                            mSettings.copyPtData();
                        } else {
                            mSettings.copyEnData();
                        }
                    }
                } else {
                    if (mSettings.getDataLanguage() == AppsSettings.languageEnum.Chinese.code)
                        mSettings.copyCnData();
                    if (mSettings.getDataLanguage() == AppsSettings.languageEnum.Korean.code)
                        mSettings.copyKorData();
                    if (mSettings.getDataLanguage() == AppsSettings.languageEnum.English.code)
                        mSettings.copyEnData();
                    if (mSettings.getDataLanguage() == AppsSettings.languageEnum.Spanish.code)
                        mSettings.copyEsData();
                    if (mSettings.getDataLanguage() == AppsSettings.languageEnum.Portuguese.code)
                        mSettings.copyPtData();
                }

                /*复制原目录文件
                if (new File(ORI_DECK).list() != null)
                    FileUtils.copyDir(ORI_DECK, mSettings.getDeckDir(), false);*/
                if (new File(ORI_REPLAY).list() != null)
                    FileUtils.copyDir(ORI_REPLAY, mSettings.getResourcePath() + "/" + Constants.CORE_REPLAY_PATH, false);
                if (new File(ORI_PICS).list() != null)
                    FileUtils.copyDir(ORI_PICS, mSettings.getCardImagePath(), false);
                if (new File(ORI_EXPANSIONS).list() != null)
                    FileUtils.copyDir(ORI_EXPANSIONS, mSettings.getExpansionsPath().getAbsolutePath(), false);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("SettingFragment", "错误" + e);
            }
        }).done((rs) -> {
            YGOUtil.showTextToast(R.string.done);
            dialog.dismiss();
            refreshSkinPreviews();
        });
    }

    public boolean joinQQGroup(String key) {
        Intent intent = new Intent();
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D" + key));
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            // 未安装手Q或安装的版本不支持
            return false;
        }
    }

    public void checkUpgrade(String url) {
        OkhttpUtil.get(url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Message message = new Message();
                message.what = TYPE_SETTING_GET_VERSION_FAILED;
                message.obj = e;
                handler.sendMessage(message);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();
                Message message = new Message();
                message.what = TYPE_SETTING_GET_VERSION_OK;
                message.obj = json;
                handler.sendMessage(message);
            }
        });
    }

    private void arrangeCodeList(String code) {
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

    private void parseVersionJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            Version = json.optString("versionname");
            HomeActivity.Update_time = json.optString("update_time");
            JSONArray downloadLink = json.optJSONArray("download_link");
            if (downloadLink != null && downloadLink.length() > 0) {
                Cache_link = downloadLink.optString(0);
            }
            JSONObject preReleaseCode = json.optJSONObject("pre_release_code");
            if (preReleaseCode != null && preReleaseCode.length() > 0) {
                StringBuilder sb = new StringBuilder();
                Iterator<String> keys = preReleaseCode.keys();
                while (keys.hasNext()) {
                    String preCode = keys.next();
                    sb.append(preCode).append(" ").append(preReleaseCode.optString(preCode)).append("\n");
                }
                activity.Cache_pre_release_code = sb.toString();
            }
            if (activity.Cache_pre_release_code != null && !activity.Cache_pre_release_code.isEmpty()) {
                activity.pre_code_list.clear();
                activity.released_code_list.clear();
                arrangeCodeList(activity.Cache_pre_release_code);
            }
            if (Version != null && !Version.isEmpty() && Cache_link != null && !Cache_link.isEmpty()
                    && Version.compareTo(BuildConfig.VERSION_NAME) > 0) {
                DialogPlus dialog = new DialogPlus(getContext());
                dialog.setMessage(R.string.Found_Update);
                dialog.setLeftButtonText(R.string.download_home);
                dialog.setLeftButtonListener((dlg, s) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(Cache_link));
                    startActivity(intent);
                    dialog.dismiss();
                });
                dialog.show();
            } else {
                showBilibiliDialog();
            }
        } catch (JSONException e) {
            Log.e(Constants.TAG, "parse version json error: " + e);
            YGOUtil.showTextToast(R.string.Checking_Update_Failed);
        }
    }

    private void showBilibiliDialog() {
        DialogPlus dialog = new DialogPlus(getContext());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        dialog.setMessage(R.string.Already_Lastest);
        dialog.setLeftButtonText(R.string.Cancel);
        dialog.setLeftButtonListener((dlg, s) -> {
            dialog.dismiss();
        });
        dialog.setRightButtonText(R.string.OK);
        dialog.setRightButtonListener((dlg, s) -> {
            intent.setData(Uri.parse(URL_BILIBILI_DYNAMIC));
            startActivity(intent);
            dialog.dismiss();
        });
        dialog.show();
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPrivacyPolicyAgreed(PrivacyPolicyAgreedEvent event) {
        if (event.agreed) {
            // 更新开关的显示状态
            setSwitchChecked(swDuelAssistant, true);
        }
    }

    @Override
    public void onDestroyView() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        rootView = null;
        super.onDestroyView();
    }

    // ==================== BaseFragemnt 生命周期 ====================

    @Override
    public void onFirstUserVisible() {

    }

    @Override
    public void onUserVisible() {
        //页面重新可见时同步最新设置状态
        if (rootView != null) {
            syncUiState();
            refreshSkinPreviews();
        }
    }

    @Override
    public void onFirstUserInvisible() {

    }

    @Override
    public void onUserInvisible() {

    }

    @Override
    public void onBackHome() {

    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}

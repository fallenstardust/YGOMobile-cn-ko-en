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
 * Public entry of the split inheritance chain (SettingFragmentBase <- this).
 * Keeps the original class name and public API: view creation/wiring,
 * click & switch event handling, delete-expansion dialog, OpenGL/screen
 * padding dialogs, the version upgrade check subsystem (handler,
 * checkUpgrade, parseVersionJson, showBilibiliDialog), resource reset
 * (updateImages), QQ group join and the fragment lifecycle callbacks.
 */
public class SettingFragment extends SettingFragmentBase implements View.OnClickListener {

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

    protected void initViews(View view) {
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
        llYgoproJniSettings = view.findViewById(R.id.ll_ygopro_jni_settings);
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
            String groupkey = "7yPZyvF3VmNgZe3Lt6Ai9WlQOCFyhChS";
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
            updateJniSettingsVisibility();
        }
    }
    /**
     * 删除扩展卡包：弹窗列出已安装的ypk文件，长按对应名称删除（沿用原实现逻辑）
     */
    protected void showDeleteExpDialog() {
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
    // ==================== OpenGL / 屏幕边距 ====================

    protected void showOpenGLDialog() {
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

    protected void showScreenPaddingDialog() {
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
    protected void parseVersionJson(String jsonStr) {
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

    protected void showBilibiliDialog() {
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

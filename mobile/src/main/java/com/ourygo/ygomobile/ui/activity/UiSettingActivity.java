package com.ourygo.ygomobile.ui.activity;

import static cn.garymb.ygomobile.Constants.CORE_SKIN_AVATAR_SIZE;
import static cn.garymb.ygomobile.Constants.CORE_SKIN_BG_SIZE;
import static cn.garymb.ygomobile.Constants.CORE_SKIN_CARD_COVER_SIZE;
import static cn.garymb.ygomobile.Constants.ORI_DECK;
import static cn.garymb.ygomobile.Constants.ORI_PICS;
import static cn.garymb.ygomobile.Constants.ORI_REPLAY;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.getDatapath;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.ygomobile.adapter.SettingRecyclerViewAdapter1;
import com.ourygo.ygomobile.bean.ImageSelectItem;
import com.ourygo.ygomobile.bean.OYSelect;
import com.ourygo.ygomobile.bean.SettingItem;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import com.ourygo.ygomobile.util.StatUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;

/**
 * Create By feihua  On 2021/10/27
 */
public class UiSettingActivity extends BaseActivity implements View.OnClickListener {

    private static final int REQUEST_AVATAR_1 = 0;
    private static final int REQUEST_AVATAR_2 = 1;
    private static final int REQUEST_COVER_1 = 2;
    private static final int REQUEST_COVER_2 = 3;
    private static final int REQUEST_BG_1 = 4;
    private static final int REQUEST_BG_2 = 5;
    private static final int REQUEST_BG_3 = 6;

    private static final int ID_SCALE = 0;
    private static final int ID_MODE = 1;
    private static final int ID_HORIZONTAL = 2;
    private static final int ID_RE_RES = 3;
    private static final int ID_DECK_EDIT_TYPE = 4;

    private static final int GROUP_SCALE = 0;
    private static final int GROUP_MODE = 1;
    private static final int GROUP_HORIZONTAL = 2;

    private static final int ID_SCALE_MATCH = 0;
    private static final int ID_SCALE_ORIGINAL = 1;
    private static final int ID_MODE_IMMERSE = 2;
    private static final int ID_MODE_DEFAULT = 3;
    AppsSettings appsSettings;

    private RecyclerView rv_list;
    private SettingRecyclerViewAdapter1 settingAdp;
    private View footView;

    private ImageView iv_avatar1, iv_avatar2, iv_cover1, iv_cover2, iv_bg1, iv_bg2, iv_bg3;
    private DialogUtils dialogUtils;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_setting_activity);

        initView();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_avatar1:
                com.ourygo.ygomobile.util.ImageUtil.startImageSelect(this, REQUEST_AVATAR_1, 1
                        , CORE_SKIN_AVATAR_SIZE[0]
                        , CORE_SKIN_AVATAR_SIZE[1]
                        , CORE_SKIN_AVATAR_SIZE[0]
                        , CORE_SKIN_AVATAR_SIZE[1]);
                break;
            case R.id.iv_avatar2:
                com.ourygo.ygomobile.util.ImageUtil.startImageSelect(this, REQUEST_AVATAR_2, 1
                        , CORE_SKIN_AVATAR_SIZE[0]
                        , CORE_SKIN_AVATAR_SIZE[1]
                        , CORE_SKIN_AVATAR_SIZE[0]
                        , CORE_SKIN_AVATAR_SIZE[1]);
                break;
            case R.id.iv_cover1:
                com.ourygo.ygomobile.util.ImageUtil.startImageSelect(this, REQUEST_COVER_1, 1
                        , CORE_SKIN_CARD_COVER_SIZE[0]
                        , CORE_SKIN_CARD_COVER_SIZE[1]
                        , CORE_SKIN_CARD_COVER_SIZE[0]
                        , CORE_SKIN_CARD_COVER_SIZE[1]);
                break;
            case R.id.iv_cover2:
                com.ourygo.ygomobile.util.ImageUtil.startImageSelect(this, REQUEST_COVER_2, 1
                        , CORE_SKIN_CARD_COVER_SIZE[0]
                        , CORE_SKIN_CARD_COVER_SIZE[1]
                        , CORE_SKIN_CARD_COVER_SIZE[0]
                        , CORE_SKIN_CARD_COVER_SIZE[1]);
                break;
            case R.id.iv_bg1:
                com.ourygo.ygomobile.util.ImageUtil.startImageSelect(this, REQUEST_BG_1, 1
                        , CORE_SKIN_BG_SIZE[0]
                        , CORE_SKIN_BG_SIZE[1]
                        , CORE_SKIN_BG_SIZE[0]
                        , CORE_SKIN_BG_SIZE[1]);
                break;
            case R.id.iv_bg2:
                com.ourygo.ygomobile.util.ImageUtil.startImageSelect(this, REQUEST_BG_2, 1
                        , CORE_SKIN_BG_SIZE[0]
                        , CORE_SKIN_BG_SIZE[1]
                        , CORE_SKIN_BG_SIZE[0]
                        , CORE_SKIN_BG_SIZE[1]);
                break;
            case R.id.iv_bg3:
                com.ourygo.ygomobile.util.ImageUtil.startImageSelect(this, REQUEST_BG_3, 1
                        , CORE_SKIN_BG_SIZE[0]
                        , CORE_SKIN_BG_SIZE[1]
                        , CORE_SKIN_BG_SIZE[0]
                        , CORE_SKIN_BG_SIZE[1]);
                break;
        }
    }

    private void initView() {
        rv_list = findViewById(R.id.rv_list);


        footView = LayoutInflater.from(this).inflate(R.layout.ui_setting_foot, null);
        appsSettings = AppsSettings.get();

        iv_avatar1 = footView.findViewById(R.id.iv_avatar1);
        iv_avatar2 = footView.findViewById(R.id.iv_avatar2);
        iv_cover1 = footView.findViewById(R.id.iv_cover1);
        iv_cover2 = footView.findViewById(R.id.iv_cover2);
        iv_bg1 = footView.findViewById(R.id.iv_bg1);
        iv_bg2 = footView.findViewById(R.id.iv_bg2);
        iv_bg3 = footView.findViewById(R.id.iv_bg3);

        dialogUtils=DialogUtils.getInstance(this);

        rv_list.setLayoutManager(new LinearLayoutManager(this));

        List<SettingItem> settingItemList = new ArrayList<>();
        SettingItem settingItem;

        settingItem = SettingItem.toSettingItem(ID_HORIZONTAL, "游戏横屏锁定", SettingItem.ITEM_SWITCH, GROUP_HORIZONTAL);
        settingItem.setObject(appsSettings.isLockSreenOrientation());
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_RE_RES, "重置资源", SettingItem.ITEM_SAME, GROUP_HORIZONTAL);
        settingItem.setNext(false);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_DECK_EDIT_TYPE, "卡组编辑跳转", SettingItem.ITEM_SAME, GROUP_HORIZONTAL);
        String type="";
        switch (SharedPreferenceUtil.getDeckEditType()){
            case SharedPreferenceUtil.DECK_EDIT_TYPE_LOCAL:
                type="横屏YGO";
                break;
            case SharedPreferenceUtil.DECK_EDIT_TYPE_DECK_MANAGEMENT:
                type="卡组管理";
                break;
            case SharedPreferenceUtil.DECK_EDIT_TYPE_OURYGO_EZ:
                type="OURYGO EZ";
                break;
        }
        settingItem.setMessage(type);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_SCALE, null, SettingItem.ITEM_IMAGE_SELECT, GROUP_SCALE, "比例");
        OYSelect oySelect = new OYSelect();
        List<ImageSelectItem> imageSelectItemList = new ArrayList<>();
        imageSelectItemList.add(new ImageSelectItem(ID_SCALE_MATCH, "填充", R.drawable.ic_scale_match));
        imageSelectItemList.add(new ImageSelectItem(ID_SCALE_ORIGINAL, "原始", R.drawable.ic_scale_original));
        oySelect.setPosition(appsSettings.isKeepScale() ? 1 : 0);
        oySelect.setObject(imageSelectItemList);
        settingItem.setObject(oySelect);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_MODE, null, SettingItem.ITEM_IMAGE_SELECT, GROUP_MODE, "模式");
        oySelect = new OYSelect();
        imageSelectItemList = new ArrayList<>();
        imageSelectItemList.add(new ImageSelectItem(ID_MODE_IMMERSE, "沉浸", R.drawable.ic_scale_match));
        imageSelectItemList.add(new ImageSelectItem(ID_MODE_DEFAULT, "默认", R.drawable.ic_mode_default));
        oySelect.setPosition(appsSettings.isImmerSiveMode() ? 0 : 1);
        oySelect.setObject(imageSelectItemList);
        settingItem.setObject(oySelect);
        settingItemList.add(settingItem);

        settingAdp = new SettingRecyclerViewAdapter1(this, settingItemList);
        rv_list.setAdapter(settingAdp);
        settingAdp.addFooterView(footView);

        initToolbar("界面设置");

        com.ourygo.ygomobile.util.ImageUtil.show(this, new File(appsSettings.getCoreSkinPath()
                        , Constants.CORE_SKIN_AVATAR_ME).getAbsolutePath(), iv_avatar1
                , new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_AVATAR_ME).lastModified() + "");

        com.ourygo.ygomobile.util.ImageUtil.show(this, new File(appsSettings.getCoreSkinPath()
                        , Constants.CORE_SKIN_AVATAR_OPPONENT).getAbsolutePath(), iv_avatar2
                , new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_AVATAR_OPPONENT).lastModified() + "");

        com.ourygo.ygomobile.util.ImageUtil.show(this, new File(appsSettings.getCoreSkinPath()
                        , Constants.CORE_SKIN_COVER).getAbsolutePath(), iv_cover1
                , new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_COVER).lastModified() + "");

        com.ourygo.ygomobile.util.ImageUtil.show(this, new File(appsSettings.getCoreSkinPath()
                        , Constants.CORE_SKIN_COVER2).getAbsolutePath(), iv_cover2
                , new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_COVER2).lastModified() + "");

        com.ourygo.ygomobile.util.ImageUtil.show(this, new File(appsSettings.getCoreSkinPath()
                        , Constants.CORE_SKIN_BG).getAbsolutePath(), iv_bg1
                , new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_BG).lastModified() + "");

        com.ourygo.ygomobile.util.ImageUtil.show(this, new File(appsSettings.getCoreSkinPath()
                        , Constants.CORE_SKIN_BG_MENU).getAbsolutePath(), iv_bg2
                , new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_BG_MENU).lastModified() + "");

        com.ourygo.ygomobile.util.ImageUtil.show(this, new File(appsSettings.getCoreSkinPath()
                        , Constants.CORE_SKIN_BG_DECK).getAbsolutePath(), iv_bg3
                , new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_BG_DECK).lastModified() + "");

        settingAdp.setOnSettingCheckListener((id, isCheck) -> {
            switch (id) {
                case ID_HORIZONTAL:
                    SharedPreferenceUtil.setHorizontal(isCheck);
                    break;
            }
        });

        iv_avatar1.setOnClickListener(this);
        iv_avatar2.setOnClickListener(this);
        iv_cover1.setOnClickListener(this);
        iv_cover2.setOnClickListener(this);
        iv_bg1.setOnClickListener(this);
        iv_bg2.setOnClickListener(this);
        iv_bg3.setOnClickListener(this);

        settingAdp.setOnItemClickListener((adapter, view, position) -> {
            switch (settingAdp.getItem(position).getId()){
                case ID_RE_RES:
                    updateImages();
                    break;
                case ID_DECK_EDIT_TYPE:
                   setDeckEditType();
                    break;
            }
        });

        settingAdp.setOnSelectListener((id, imageSelectItem, lastPosition, position) -> {
            switch (id) {
                case ID_SCALE:
                    switch (imageSelectItem.getId()) {
                        case ID_SCALE_MATCH:
                            SharedPreferenceUtil.setKeepScale(false);
                            break;
                        case ID_SCALE_ORIGINAL:
                            SharedPreferenceUtil.setKeepScale(true);
                            break;
                    }
                    break;
                case ID_MODE:
                    switch (imageSelectItem.getId()) {
                        case ID_MODE_IMMERSE:
                            SharedPreferenceUtil.setImmersiveMode(true);
                            break;
                        case ID_MODE_DEFAULT:
                            SharedPreferenceUtil.setImmersiveMode(false);
                            break;
                    }
                    break;
            }
        });


    }

    private void setDeckEditType() {
        List<String> data =new ArrayList<>();
        data.add("横屏YGO");
        data.add("卡组管理");
        data.add("OURYGO EZ");
        dialogUtils.dialogRadio("瀑布屏高度", data, SharedPreferenceUtil.getDeckEditType()).setOnRadioListener((data1, position) -> {
            SharedPreferenceUtil.setDeckEditType(position);
            settingAdp.getItem2Id(ID_DECK_EDIT_TYPE).setMessage(data1.get(position));
            settingAdp.notifyItemChanged(settingAdp.getItem2IdPosition(ID_DECK_EDIT_TYPE));
            dialogUtils.dis();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;
        String imagePath;
        switch (requestCode) {
            case REQUEST_AVATAR_1:
                imagePath = com.ourygo.ygomobile.util.ImageUtil.getImageList(data).get(0);
                com.ourygo.ygomobile.util.ImageUtil.show(this, imagePath, iv_avatar1, new File(imagePath).lastModified() + "");
                try {
                    FileUtils.copyFile(imagePath, new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_AVATAR_ME).getAbsolutePath());
                } catch (IOException e) {
                    OYUtil.snackExceptionToast(this, toolbar, "替换失败", e.toString());
                }
                break;
            case REQUEST_AVATAR_2:
                imagePath = com.ourygo.ygomobile.util.ImageUtil.getImageList(data).get(0);
                com.ourygo.ygomobile.util.ImageUtil.show(this, imagePath, iv_avatar2, new File(imagePath).lastModified() + "");
                try {
                    FileUtils.copyFile(imagePath, new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_AVATAR_OPPONENT).getAbsolutePath());
                } catch (IOException e) {
                    OYUtil.snackExceptionToast(this, toolbar, "替换失败", e.toString());
                }
                break;
            case REQUEST_COVER_1:
                imagePath = com.ourygo.ygomobile.util.ImageUtil.getImageList(data).get(0);
                com.ourygo.ygomobile.util.ImageUtil.show(this, imagePath, iv_cover1, new File(imagePath).lastModified() + "");
                try {
                    FileUtils.copyFile(imagePath, new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_COVER).getAbsolutePath());
                } catch (IOException e) {
                    OYUtil.snackExceptionToast(this, toolbar, "替换失败", e.toString());
                }
                break;
            case REQUEST_COVER_2:
                imagePath = com.ourygo.ygomobile.util.ImageUtil.getImageList(data).get(0);
                com.ourygo.ygomobile.util.ImageUtil.show(this, imagePath, iv_cover2, new File(imagePath).lastModified() + "");
                try {
                    FileUtils.copyFile(imagePath, new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_COVER2).getAbsolutePath());
                } catch (IOException e) {
                    OYUtil.snackExceptionToast(this, toolbar, "替换失败", e.toString());
                }
                break;
            case REQUEST_BG_1:
                imagePath = com.ourygo.ygomobile.util.ImageUtil.getImageList(data).get(0);
                com.ourygo.ygomobile.util.ImageUtil.show(this, imagePath, iv_bg1, new File(imagePath).lastModified() + "");
                try {
                    FileUtils.copyFile(imagePath, new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_BG).getAbsolutePath());
                } catch (IOException e) {
                    OYUtil.snackExceptionToast(this, toolbar, "替换失败", e.toString());
                }
                break;
            case REQUEST_BG_2:
                imagePath = com.ourygo.ygomobile.util.ImageUtil.getImageList(data).get(0);
                com.ourygo.ygomobile.util.ImageUtil.show(this, imagePath, iv_bg2, new File(imagePath).lastModified() + "");
                try {
                    FileUtils.copyFile(imagePath, new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_BG_MENU).getAbsolutePath());
                } catch (IOException e) {
                    OYUtil.snackExceptionToast(this, toolbar, "替换失败", e.toString());
                }
                break;
            case REQUEST_BG_3:
                imagePath = com.ourygo.ygomobile.util.ImageUtil.getImageList(data).get(0);
                com.ourygo.ygomobile.util.ImageUtil.show(this, imagePath, iv_bg3, new File(imagePath).lastModified() + "");
                try {
                    FileUtils.copyFile(imagePath, new File(appsSettings.getCoreSkinPath(), Constants.CORE_SKIN_BG_DECK).getAbsolutePath());
                } catch (IOException e) {
                    OYUtil.snackExceptionToast(this, toolbar, "替换失败", e.toString());
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        StatUtil.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        StatUtil.onPause(this);
    }

    public void updateImages() {
        Log.e("MainActivity", "重置资源");
        dialogUtils.dialogj1(null, getString(R.string.message));
        VUiKit.defer().when(() -> {
            Log.e("MainActivity", "开始复制");
            try {
                IOUtils.createNoMedia(AppsSettings.get().getResourcePath());

                FileUtils.delFile(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_SCRIPT_PATH);

                if (IOUtils.hasAssets(this, getDatapath(Constants.CORE_PICS_ZIP))) {
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_PICS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                }
                if (IOUtils.hasAssets(this, getDatapath(Constants.CORE_SCRIPTS_ZIP))) {
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SCRIPTS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                }
                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.DATABASE_NAME),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_STRING_PATH),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.WINDBOT_PATH),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SKIN_PATH),
                        AppsSettings.get().getCoreSkinPath(), true);
                String fonts = AppsSettings.get().getResourcePath() + "/" + Constants.FONT_DIRECTORY;
                if (new File(fonts).list() != null)
                    FileUtils.delFile(fonts);
                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.FONT_DIRECTORY),
                        AppsSettings.get().getFontDirPath(), true);
                /*
                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SOUND_PATH),
                        AppsSettings.get().getSoundPath(), false);*/

                //复制原目录文件
//                if (new File(ORI_DECK).list() != null)
//                    FileUtils.copyDir(ORI_DECK, AppsSettings.get().getDeckDir(), false);
//                if (new File(ORI_REPLAY).list() != null)
//                    FileUtils.copyDir(ORI_REPLAY, AppsSettings.get().getResourcePath() + "/" + Constants.CORE_REPLAY_PATH, false);
//                if (new File(ORI_PICS).list() != null)
//                    FileUtils.copyDir(ORI_PICS, AppsSettings.get().getCardImagePath(), false);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("MainActivity", "错误" + e);
            }
        }).done((rs) -> {
            Log.e("MainActivity", "复制完毕");
            dialogUtils.dis();
            OYUtil.snackShow(toolbar,"重置资源成功");
        });
    }

}

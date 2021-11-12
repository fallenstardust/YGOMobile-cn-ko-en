package com.ourygo.ygomobile.ui.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.ygomobile.adapter.SettingRecyclerViewAdapter1;
import com.ourygo.ygomobile.bean.SettingItem;
import com.ourygo.ygomobile.util.ExpansionsUtil;
import com.ourygo.ygomobile.util.HandlerUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.Record;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import com.ourygo.ygomobile.util.StatUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import ocgcore.DataManager;

/**
 * Create By feihua  On 2021/10/23
 */
public class ExpansionsSettingActivity extends BaseActivity {

    private static final int ID_EXPANSIONS_SWITCH = 0;
    private static final int ID_DEL_ALL = 1;
    private static final int ID_EXPANSIONS_CARD = 2;

    private static final int GROUP_SWITCH = 0;
    private static final int GROUP_CARD = 1;

    private static final int HANDLE_START_EXPANSIONS = 0;
    private static final int ADVACE_DEL_ALL = 1;
    private static final int ADVACE_DEL_OK = 2;
    private static final int ADVACE_DEL_EXCAPTION = 3;

    private RecyclerView rv_list;
    private SettingRecyclerViewAdapter1 settingAdp;
    private boolean currentIsEx;
    private DialogUtils du;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLE_START_EXPANSIONS:
                    du.dis();
                    openExpansions();
                    break;
                case ADVACE_DEL_ALL:
                    du.dis();
                    if ((boolean) msg.obj) {
                        OYUtil.snackShow(toolbar, "删除成功");
                        removeOther();
                    } else {
                        OYUtil.snackWarning(toolbar, "删除失败");
                    }
                    break;
                case ADVACE_DEL_OK:
                    du.dis();
                    OYUtil.snackShow(toolbar, "删除成功");
                    int position = (int) msg.obj;
                    boolean islast = false, isNext = false;
                    if (position != 0)
                        islast = true;
                    if (position != settingAdp.getItemCount())
                        isNext = true;
                    settingAdp.removeAt(position);
                    if (islast)
                        settingAdp.notifyItemChanged(position - 1);
                    if (isNext)
                        settingAdp.notifyItemChanged(position);
                    break;
                case ADVACE_DEL_EXCAPTION:
                    du.dis();
                    OYUtil.snackWarning(toolbar, "删除失败");
                    break;
            }
        }
    };

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.expansions_setting_activity);

        initView();

    }

    private void initView() {
        rv_list = findViewById(R.id.rv_list);
        rv_list.setLayoutManager(new LinearLayoutManager(this));
        du = DialogUtils.getInstance(this);

        initToolbar("扩展卡包");

        currentIsEx = AppsSettings.get().isReadExpansions();

        List<SettingItem> settingItemList = new ArrayList<>();
        SettingItem settingItem;

        settingItem = SettingItem.toSettingItem(ID_EXPANSIONS_SWITCH, "扩展卡包", SettingItem.ITEM_SWITCH, GROUP_SWITCH);
        settingItem.setObject(currentIsEx);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_DEL_ALL, "删除所有扩展卡包", GROUP_SWITCH);
        settingItem.setNext(false);
        settingItem.setNameColor(OYUtil.c(R.color.red));
        settingItemList.add(settingItem);

        settingAdp = new SettingRecyclerViewAdapter1(this, settingItemList);
        rv_list.setAdapter(settingAdp);


        settingAdp.setOnSettingCheckListener((id, isCheck) -> {
            Log.e("ExpansionsSettin", "额外卡包" + isCheck);
            switch (id) {
                case ID_EXPANSIONS_SWITCH:
                    SharedPreferenceUtil.setReadExpansions(isCheck);
                    if (isCheck) {
                        du.dialogj1(null, "启用中，请稍等");
                        //设置使用额外卡库后重新加载卡片数据
                        new Thread(() -> {
                            DataManager.get().load(true);
                            handler.sendEmptyMessage(HANDLE_START_EXPANSIONS);
                        }).start();
                    } else {
                        closeExpansions();
                    }

                    break;
            }
        });
        settingAdp.setOnItemClickListener((adapter, view, position) -> {
            SettingItem settingItem1 = settingAdp.getItem(position);
            switch (settingItem1.getId()) {
                case ID_DEL_ALL:
                    du.dialogj1(null, "删除中，请稍等");
                    ExpansionsUtil.delExpansionsAll((path, isOk) -> {
                        Log.e("ExpansionsUtil", "接收回调" + isOk);
                        HandlerUtil.sendMessage(handler, ADVACE_DEL_ALL, isOk);
                    });
                    break;
            }
        });
        toolbar.setOnclickListener(v -> {
            onBackPressed();
        });
        settingAdp.addChildClickViewIds(R.id.tv_message);
        settingAdp.setOnItemChildClickListener((adapter, view, position) -> {
            SettingItem settingItem1 = settingAdp.getItem(position);
            switch (settingItem1.getId()) {
                case ID_EXPANSIONS_CARD:
                    switch (view.getId()) {
                        case R.id.tv_message:
                            Log.e("Expansion","删除回调");
                            du.dialogj1(null, "删除中，请稍等");
                            ExpansionsUtil.delExpansions((File) settingItem1.getObject(), (path, isOk) ->
                                    HandlerUtil.sendMessage(handler, isOk ? "" : "删除失败", ADVACE_DEL_OK, position, ADVACE_DEL_EXCAPTION));
                            break;
                    }
                    break;
            }
        });
        if (currentIsEx)
            openExpansions();
    }

    private void initExpansionsList() {

    }

    @Override
    public void onBackPressed() {
        int resultCode = (currentIsEx == AppsSettings.get().isReadExpansions()) ? RESULT_CANCELED : RESULT_OK;
        setResult(resultCode);
        super.onBackPressed();
    }

    private void closeExpansions() {
        removeOther();
    }

    private void openExpansions() {
        loadingExpansions();
    }

    private void removeOther() {
        List<SettingItem> settingItemList = new ArrayList<>(settingAdp.getData());
        while (settingItemList.size() > 2) {
            settingItemList.remove(settingItemList.size() - 1);
        }
        settingAdp.setNewInstance(settingItemList);
    }

    private void loadingExpansions() {
        SettingItem mSettingItem = SettingItem.toSettingItem(ID_EXPANSIONS_CARD, "删除所有扩展卡包", GROUP_CARD, "已安装");
        mSettingItem.setNext(false);
        mSettingItem.setMessageColor(OYUtil.c(R.color.red));
        mSettingItem.setMessage("删除");
        mSettingItem.setContent(false);
        mSettingItem.setLoading(true);
        settingAdp.addData(mSettingItem);

        ExpansionsUtil.findExpansionsList(fileList -> {
            List<SettingItem> settingItemList = new ArrayList<>();
            for (File file : fileList) {
                String name = file.getName();
                if (name.equals(Record.ARG_OTHER))
                    name = "其他卡包";
                SettingItem settingItem1 = SettingItem.toSettingItem(ID_EXPANSIONS_CARD, name, GROUP_CARD, "已安装");
                settingItem1.setNext(false);
                settingItem1.setMessageColor(OYUtil.c(R.color.red));
                settingItem1.setMessage("删除");
                settingItem1.setObject(file);
                settingItemList.add(settingItem1);
            }
            removeOther();
            settingAdp.addData(settingItemList);
        });
    }
}

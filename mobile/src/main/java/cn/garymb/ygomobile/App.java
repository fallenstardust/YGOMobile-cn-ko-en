package cn.garymb.ygomobile;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.sdk.QbSdk;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.common.ImageLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.ui.cards.deck.MyDeckItem;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.ProcessUtils;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

public class App extends GameApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        AppsSettings.init(this);
        //初始化异常工具类
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());
        //初始化bugly
        //initBugly();
        if(!ProcessUtils.getCurrentProcessName(this).endsWith(":game")){
            //初始化图片选择器
            initImgsel();
            //x5
            HashMap map = new HashMap();
            map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
            map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
            QbSdk.initTbsSettings(map);
        }
    }

    @Override
    public NativeInitOptions getNativeInitOptions() {
        return AppsSettings.get().getNativeInitOptions();
    }

    @Override
    public float getSmallerSize() {
        return AppsSettings.get().getSmallerSize();
    }

    @Override
    public void attachGame(Activity activity) {
        super.attachGame(activity);
        AppsSettings.get().update(activity);
    }

    @Override
    public float getXScale() {
        return AppsSettings.get().getXScale(getGameWidth(), getGameHeight());
    }

    @Override
    public float getYScale() {
        return AppsSettings.get().getYScale(getGameWidth(), getGameHeight());
    }
//
//    @Override
//    public int getGameHeight() {
//        return 720;
//    }
//
//    @Override
//    public int getGameWidth() {
//        return 1280;
//    }

    @Override
    public String getCardImagePath() {
        return AppsSettings.get().getCardImagePath();
    }

    @Override
    public String getFontPath() {
        return AppsSettings.get().getFontPath();
    }

    @Override
    public boolean isKeepScale() {
        return AppsSettings.get().isKeepScale();
    }

    @Override
    public void saveSetting(String key, String value) {
        AppsSettings.get().saveSettings(key, value);
    }

    @Override
    public String getSetting(String key) {
        return AppsSettings.get().getSettings(key);
    }

    @Override
    public int getIntSetting(String key, int def) {
        return AppsSettings.get().getIntSettings(key, def);
    }

    @Override
    public void saveIntSetting(String key, int value) {
        AppsSettings.get().saveIntSettings(key, value);
    }

    @Override
    public float getScreenWidth() {
        return AppsSettings.get().getScreenWidth();
    }

    @Override
    public boolean isLockSreenOrientation() {
        return AppsSettings.get().isLockSreenOrientation();
    }

    @Override
    public boolean canNdkCash() {
        return false;
    }

    @Override
    public boolean isImmerSiveMode() {
        return AppsSettings.get().isImmerSiveMode();
    }

    public boolean isSensorRefresh() {
        return AppsSettings.get().isSensorRefresh();
    }

    @Override
    public float getScreenHeight() {
        return AppsSettings.get().getScreenHeight();
    }

    @Override
    public void runWindbot(String args) {
        Intent intent = new Intent();
        intent.putExtra("args", args);
        intent.setAction("RUN_WINDBOT");
        getBaseContext().sendBroadcast(intent);
    }

    public void deleteDeckSync(String deckPath) {
        DeckFile deckFile = new DeckFile(new File(deckPath));
        List<DeckFile> deckFileList = new ArrayList<>();
        deckFileList.add(deckFile);
        DeckSquareApiUtil.deleteDecks(deckFileList);
    }

    public void deleteCategoryDecksSync(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return;
        }
        
        // 获取该分类下的所有卡组文件
        File categoryDir = new File(AppsSettings.get().getDeckDir(), categoryName);
        if (!categoryDir.exists() || !categoryDir.isDirectory()) {
            return;
        }
        
        File[] ydkFiles = categoryDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".ydk"));
        if (ydkFiles == null || ydkFiles.length == 0) {
            return;
        }
        
        // 创建 DeckFile 列表
        List<DeckFile> deckFileList = new ArrayList<>();
        for (File file : ydkFiles) {
            DeckFile deckFile = new DeckFile(file);
            deckFile.setTypeName(categoryName);
            deckFileList.add(deckFile);
        }
        
        // 调用批量删除
        DeckSquareApiUtil.deleteDecks(deckFileList);
    }

    public void renameCategoryDecksSync(String oldCategoryName, String newCategoryName) {
        if (oldCategoryName == null || oldCategoryName.isEmpty() 
                || newCategoryName == null || newCategoryName.isEmpty()) {
            return;
        }
        
        // 检查用户是否登录
        if (DeckSquareApiUtil.needLogin()) {
            return;
        }
        
        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if (loginToken == null) {
            return;
        }
        
        // 在后台线程执行
        VUiKit.defer().when(() -> {
            // 获取在线卡组列表
            MyDeckResponse result = DeckSquareApiUtil.getUserDecks(loginToken);
            if (result == null || result.getData() == null) {
                throw new RuntimeException("获取用户卡组信息失败");
            }
            
            List<MyOnlineDeckDetail> onlineDecks = result.getData();
            boolean hasChanges = false;
            
            // 遍历所有在线卡组，找到属于旧分类的卡组并更新分类名
            for (MyOnlineDeckDetail deck : onlineDecks) {
                if (oldCategoryName.equals(deck.getDeckType())) {
                    deck.setDeckType(newCategoryName);
                    hasChanges = true;
                    LogUtil.d("App", "重命名卡组分类: " + deck.getDeckName()
                            + " 从 [" + oldCategoryName + "] 到 [" + newCategoryName + "]");
                }
            }
            
            // 如果有修改，上传更新
            if (hasChanges) {
                DeckUtil deckUtil = new DeckUtil();
                DeckSquareApiUtil.UploadMyDecks(deckUtil.toDeckItemList(onlineDecks), loginToken);
                LogUtil.d("App", "卡组分类重命名同步成功: " + oldCategoryName + " -> " + newCategoryName);
            } else {
                LogUtil.d("App", "没有找到需要更新的在线卡组: " + oldCategoryName);
            }
            
            return true;
        }).fail((e) -> {
            LogUtil.e("App", "重命名卡组分类失败!", e);
        }).done((result) -> {
            LogUtil.d("App", "卡组分类重命名完成");
        });
    }

    public void syncSaveDeck(String deckPath) {
        if (deckPath == null || deckPath.isEmpty()) {
            return;
        }
        
        java.io.File deckFile = new java.io.File(deckPath);
        if (!deckFile.exists()) {
            LogUtil.w("App", "卡组文件不存在，无法同步: " + deckPath);
            return;
        }
        
        // 检查用户是否登录
        if (DeckSquareApiUtil.needLogin()) {
            return;
        }
        
        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if (loginToken == null) {
            return;
        }
        
        // 从文件中读取 deckId
        String deckId = DeckUtil.getDeckId(deckFile);
        if (deckId == null || deckId.isEmpty()) {
            LogUtil.d("App", "卡组没有 deckId，跳过同步: " + deckPath);
            return;
        }
        
        // 在后台线程执行
        VUiKit.defer().when(() -> {
            // 获取在线卡组列表
            MyDeckResponse result =
                    DeckSquareApiUtil.getUserDecks(loginToken);
            if (result == null || result.getData() == null) {
                throw new RuntimeException("获取用户卡组信息失败");
            }
            
            List<MyOnlineDeckDetail> onlineDecks = result.getData();
            MyOnlineDeckDetail targetDeck = null;
            
            // 查找匹配的在线卡组
            for (MyOnlineDeckDetail deck : onlineDecks) {
                if (deckId.equals(deck.getDeckId())) {
                    targetDeck = deck;
                    break;
                }
            }
            
            if (targetDeck == null) {
                LogUtil.d("App", "未找到匹配的在线卡组，跳过同步: deckId=" + deckId);
                return null;
            }
            
            // 构建 MyDeckItem 用于上传
            MyDeckItem deckItem = new MyDeckItem();
            deckItem.setDeckPath(deckPath);
            deckItem.setDeckId(deckId);
            deckItem.setDeckName(targetDeck.getDeckName());
            deckItem.setDeckType(targetDeck.getDeckType());
            deckItem.setUpdateTimestamp(System.currentTimeMillis());
            deckItem.setUserId(loginToken.getUserId());
            deckItem.setDelete(false); // 明确设置为 false，避免 null
            
            // 提取封面卡片
            deckItem.setDeckCoverCard1(DeckUtil.getFirstCardCode(deckFile.getPath())); // Changed from DeckUtil.getFirstCardCode(deckFile);
            
            // 上传更新
            java.util.List<MyDeckItem> deckItems = new java.util.ArrayList<>();
            deckItems.add(deckItem);
            
            DeckSquareApiUtil.UploadMyDecks(deckItems, loginToken);
            LogUtil.d("App", "卡组同步保存成功: " + targetDeck.getDeckName() + " (deckId=" + deckId + ")");
            
            return true;
        }).fail((e) -> {
            LogUtil.e("App", "同步保存卡组失败!", e);
        }).done((result) -> {
            if (result != null && result) {
                LogUtil.d("App", "卡组同步完成");
            }
        });
    }

    private void initImgsel() {
        // 自定义图片加载器
        ISNav.getInstance().init(new ImageLoader() {
            @Override
            public void displayImage(Context context, String path, ImageView imageView) {
                GlideCompat.with(context).load(path).into(imageView);
            }
        });
    }
/*
    public void initBugly() {
        Beta.initDelay = 0;
        Beta.showInterruptedStrategy = true;
        Beta.largeIconId = R.drawable.ic_icon_round;
        Beta.defaultBannerId = R.drawable.ic_icon_round;
        Beta.strToastYourAreTheLatestVersion = this.getString(R.string.Already_Lastest);
        Beta.strToastCheckingUpgrade = this.getString(R.string.Checking_Update);
        Beta.upgradeDialogLayoutId = R.layout.dialog_upgrade;
        Beta.enableHotfix = false;
        Beta.autoCheckHotfix = false;
        Beta.autoCheckUpgrade = false;
        Beta.autoCheckAppUpgrade = false;
        //添加可显示弹窗的Activity
        Beta.canShowUpgradeActs.add(MainActivity.class);
        ApplicationInfo appInfo = null;
        try {
            appInfo = this.getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String msg = appInfo.metaData.getString("BUGLY_APPID");
        Bugly.init(this, msg, BuildConfig.DEBUG_MODE);
    }*/
}

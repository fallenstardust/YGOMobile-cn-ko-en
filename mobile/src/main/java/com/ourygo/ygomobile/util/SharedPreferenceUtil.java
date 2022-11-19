package com.ourygo.ygomobile.util;

import static cn.garymb.ygomobile.Constants.PREF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_KEEP_SCALE;
import static cn.garymb.ygomobile.Constants.PREF_LOCK_SCREEN;
import static cn.garymb.ygomobile.Constants.PREF_OPENGL_VERSION;
import static cn.garymb.ygomobile.Constants.PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_WINDOW_TOP_BOTTOM;

import android.content.SharedPreferences;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;

public class SharedPreferenceUtil {

    public static final int DECK_EDIT_TYPE_LOCAL = 0;
    public static final int DECK_EDIT_TYPE_DECK_MANAGEMENT = 1;
    public static final int DECK_EDIT_TYPE_OURYGO_EZ = 2;

    public static final int SERVER_LIST_TYPE_LIST=0;
    public static final int SERVER_LIST_TYPE_GRID=1;


    //获取存放路径的share
    public static SharedPreferences getSharePath() {
        return App.get().getSharedPreferences("path", App.get().MODE_PRIVATE);
    }

    //获取存放类型的share
    public static SharedPreferences getShareType() {
        return App.get().getSharedPreferences("type", App.get().MODE_PRIVATE);
    }

    //获取存放开关状态的share
    public static SharedPreferences getShareKaiguan() {
        return App.get().getSharedPreferences("kaiguan", App.get().MODE_PRIVATE);
    }

    //获取各种记录的share
    public static SharedPreferences getShareRecord() {
        return App.get().getSharedPreferences("record", App.get().MODE_PRIVATE);
    }

    public static boolean addAppStartTimes() {
        return getShareRecord().edit().putInt("StartTimes", getAppStartTimes() + 1).commit();
    }

    //获取应用的启动次数
    public static int getAppStartTimes() {
        return getShareRecord().getInt("StartTimes", 0);
    }

    public static String getUserName() {
        return getShareRecord().getString("userName", null);
    }

    public static String getUserPassword() {
        return getShareRecord().getString("userPassword", null);
    }

    public static String getUserAccount() {
        return getShareRecord().getString("userAccount", null);
    }

    public static String getHttpSessionId() {
        return getShareRecord().getString("sessionId", null);
    }

    public static boolean setHttpSessionId(String sessionid) {
        return getShareRecord().edit().putString("sessionId", sessionid).commit();
        // TODO: Implement this method
    }

    public static boolean setMyCardUserName(String mycardUserName) {
        return getShareRecord().edit().putString(Record.ARG_MC_NAME, mycardUserName).commit();
    }

    public static String getMyCardUserName() {
        return getShareRecord().getString(Record.ARG_MC_NAME, null);
    }

    public static boolean setUserName(String name) {
        return getShareRecord().edit().putString("userName", name).commit();
    }

    public static boolean setUserAccount(String account) {
        return getShareRecord().edit().putString("userAccount", account).commit();
    }

    public static boolean setUserPassword(String password) {
        return getShareRecord().edit().putString("userPassword", password).commit();
    }

    public static boolean setScreenPadding(String paddding) {
        return AppsSettings.get().getSharedPreferences().edit().putString(PREF_WINDOW_TOP_BOTTOM, paddding).commit();
    }

    public static boolean setScreenPadding(int position) {
        return setScreenPadding(OYUtil.getArray(R.array.screen_top_bottom_value)[position]);
    }

    public static boolean setReadExpansions(boolean isReadExpansions) {
        return AppsSettings.get().getSharedPreferences().edit().putBoolean(PREF_READ_EX, isReadExpansions).commit();
    }

    public static boolean setOpenglVersion(int position) {
        return setOpenglVersion(OYUtil.getArray(R.array.opengl_version_value)[position]);
    }

    public static boolean setOpenglVersion(String opengl) {
        return AppsSettings.get().getSharedPreferences().edit().putString(PREF_OPENGL_VERSION, opengl).commit();
    }

    public static int getScreenPaddingPosition() {
        String value = AppsSettings.get().getScreenPadding() + "";
        String[] valueList = OYUtil.getArray(R.array.screen_top_bottom_value);
        for (int i = 0; i < valueList.length; i++) {
            String s = valueList[i];
            if (s.equals(value))
                return i;
        }
        return -1;
    }

    public static int getOpenglVersionPosition() {
        String value = AppsSettings.get().getOpenglVersion() + "";
        String[] valueList = OYUtil.getArray(R.array.opengl_version_value);
        for (int i = 0; i < valueList.length; i++) {
            String s = valueList[i];
            if (s.equals(value))
                return i;
        }
        return -1;
    }

    public static boolean setImmersiveMode(boolean isImmeriveMode) {
        return AppsSettings.get().getSharedPreferences().edit().putBoolean(PREF_IMMERSIVE_MODE, isImmeriveMode).commit();
    }

    public static boolean setKeepScale(boolean isKeepScale) {
        return AppsSettings.get().getSharedPreferences().edit().putBoolean(PREF_KEEP_SCALE, isKeepScale).commit();
    }

    public static boolean setHorizontal(boolean isHorizontal) {
        return AppsSettings.get().getSharedPreferences().edit().putBoolean(PREF_LOCK_SCREEN, isHorizontal).commit();
    }

    public static boolean isShowEz() {
        return getShareKaiguan().getBoolean("isShowEz", true);
    }

    public static boolean setIsShowEz(boolean isShow) {
        return getShareKaiguan().edit().putBoolean("isShowEz", isShow).commit();
    }

    public static boolean isShowVisitDeck() {
        return getShareKaiguan().getBoolean("isShowVisitDeck", true);
    }

    public static boolean setShowVisitDeck(boolean isShow) {
        return getShareKaiguan().edit().putBoolean("isShowVisitDeck", isShow).commit();
    }

    public static boolean isFristStart() {
        return getShareRecord().getBoolean("isFirstStart", true);
    }

    public static boolean setFirstStart(boolean isFirstStart) {
        return getShareRecord().edit().putBoolean("isFirstStart", isFirstStart).commit();
    }

    public static int getNextAifadianNum() {
        return getShareRecord().getInt("nextAifadianNum", (10 + (int) (Math.random() * 20)));
    }

    public static void setNextAifadianNum(int num) {
        getShareRecord().edit().putInt("nextAifadianNum", num).apply();
    }

    public static int getDeckEditType() {
        return getShareType().getInt("deckEditType", DECK_EDIT_TYPE_DECK_MANAGEMENT);
    }

    public static void setDeckEditType(int type) {
        getShareType().edit().putInt("deckEditType", type).apply();
    }

    public static int getServerListType() {
        return getShareType().getInt("serverListMode", SERVER_LIST_TYPE_LIST);
    }

    public static void setServerListType(int type) {
        getShareType().edit().putInt("serverListMode", type).apply();
    }

    public static long getVersionUpdateTime() {
        return getShareRecord().getLong("versionUpdateTime", 0);
    }

    public static void setVersionUpdateTime(long versionUpdateTime) {
        getShareRecord().edit().putLong("versionUpdateTime", versionUpdateTime).apply();
    }

    public static boolean isToastNewCardBag() {
        return getShareRecord().getBoolean("isToastNewCardBag", true);
    }

    public static void setToastNewCardBag(boolean toastNewCardBag) {
        getShareRecord().edit().putBoolean("isToastNewCardBag", toastNewCardBag).apply();
    }

    public static void setTodayStartTime(long versionUpdateTime) {
        getShareRecord().edit().putLong("todayStartTime", versionUpdateTime).apply();
    }

    public static long getTodayStartTime() {
        return getShareRecord().getLong("todayStartTime", 0);
    }

}

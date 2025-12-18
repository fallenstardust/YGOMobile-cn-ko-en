package cn.garymb.ygomobile.utils;

import static cn.garymb.ygomobile.Constants.PREF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_KEEP_SCALE;
import static cn.garymb.ygomobile.Constants.PREF_LOCK_SCREEN;
import static cn.garymb.ygomobile.Constants.PREF_OPENGL_VERSION;
import static cn.garymb.ygomobile.Constants.PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_WINDOW_TOP_BOTTOM;

import android.content.Context;
import android.content.SharedPreferences;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.MyCard;

public class SharedPreferenceUtil {

    public static final int DECK_EDIT_TYPE_LOCAL = 0;
    public static final int DECK_EDIT_TYPE_DECK_MANAGEMENT = 1;
    public static final int DECK_EDIT_TYPE_OURYGO_EZ = 2;


    //获取存放路径的share
    public static SharedPreferences getSharePath() {
        return App.get().getSharedPreferences("path", Context.MODE_PRIVATE);
    }

    //获取存放类型的share
    public static SharedPreferences getShareType() {
        return App.get().getSharedPreferences("type", Context.MODE_PRIVATE);
    }

    //获取存放开关状态的share
    public static SharedPreferences getShareKaiguan() {
        return App.get().getSharedPreferences("kaiguan", Context.MODE_PRIVATE);
    }

    //获取各种记录的share
    public static SharedPreferences getShareRecord() {
        return App.get().getSharedPreferences("record", Context.MODE_PRIVATE);
    }

    public static boolean addAppStartTimes() {
        return getShareRecord().edit().putInt("StartTimes", getAppStartTimes() + 1).commit();
    }

    //获取应用的启动次数
    public static int getAppStartTimes() {
        return getShareRecord().getInt("StartTimes", 0);
    }

    public static void setExpansionDataVer(String dataVer) {
        getShareRecord().edit().putString("ExpansionsDataVer", dataVer).commit();
    }

    public static String getExpansionDataVer() {
        return getShareRecord().getString("ExpansionsDataVer", null);
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
        return getShareRecord().edit().putString(MyCard.ARG_MC_NAME, mycardUserName).commit();
    }

    public static String getMyCardUserName() {
        return getShareRecord().getString(MyCard.ARG_MC_NAME, null);
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
        return setScreenPadding(getArray(R.array.screen_top_bottom_value)[position]);
    }

    public static boolean setReadExpansions(boolean isReadExpansions) {
        return AppsSettings.get().getSharedPreferences().edit().putBoolean(PREF_READ_EX, isReadExpansions).commit();
    }

    public static boolean setOpenglVersion(int position) {
        return setOpenglVersion(getArray(R.array.opengl_version_value)[position]);
    }

    public static boolean setOpenglVersion(String opengl) {
        return AppsSettings.get().getSharedPreferences().edit().putString(PREF_OPENGL_VERSION, opengl).commit();
    }

    public static int getScreenPaddingPosition() {
        String value = AppsSettings.get().getScreenPadding() + "";
        String[] valueList = getArray(R.array.screen_top_bottom_value);
        for (int i = 0; i < valueList.length; i++) {
            String s = valueList[i];
            if (s.equals(value))
                return i;
        }
        return -1;
    }

    public static int getOpenglVersionPosition() {
        String value = AppsSettings.get().getOpenglVersion() + "";
        String[] valueList = getArray(R.array.opengl_version_value);
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
        return getShareType().getInt("deckEditType", DECK_EDIT_TYPE_LOCAL);
    }

    public static void setDeckEditType(int type) {
        getShareType().edit().putInt("deckEditType", type).apply();
    }

    public static String getServerToken() {
        return getShareRecord().getString("server_token", null);
    }

    public static boolean setServerToken(String token) {
        return getShareRecord().edit().putString("server_token", token).commit();
    }

    public static Integer getServerUserId() {
        return getShareType().getInt("server_user_id", -1);
    }

    public static void setServerUserId(int userId) {
        getShareType().edit().putInt("server_user_id", userId).apply();
    }

    public static void putString(String key, String value){
        getShareRecord().edit().putString(key, value).commit();
    }

    public static String getString(String key, String defValue){
        return getShareRecord().getString(key, defValue);
    }

    public static boolean deleteServerToken() {
        // Get SharedPreferences instance
        SharedPreferences sharedPreferences = getShareRecord();

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("server_token");  // Replace "key_name" with your actual key
        return editor.commit();  // Or editor.commit() if you need immediate results
    }

    public static String[] getArray(int id) {
        return App.get().getResources().getStringArray(id);
    }
}

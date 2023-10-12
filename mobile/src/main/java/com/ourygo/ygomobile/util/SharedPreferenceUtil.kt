package com.ourygo.ygomobile.util

import android.content.Context
import android.content.SharedPreferences
import cn.garymb.ygomobile.App
import cn.garymb.ygomobile.AppsSettings
import cn.garymb.ygomobile.Constants
import cn.garymb.ygomobile.lite.R

object SharedPreferenceUtil {
    const val DECK_EDIT_TYPE_LOCAL = 0
    const val DECK_EDIT_TYPE_DECK_MANAGEMENT = 1
    const val DECK_EDIT_TYPE_OURYGO_EZ = 2
    const val SERVER_LIST_TYPE_LIST = 0
    const val SERVER_LIST_TYPE_GRID = 1
    val sharePath: SharedPreferences
        //获取存放路径的share
        get() = App.get().getSharedPreferences("path", Context.MODE_PRIVATE)
    val shareType: SharedPreferences
        //获取存放类型的share
        get() = App.get().getSharedPreferences("type", Context.MODE_PRIVATE)
    val shareKaiguan: SharedPreferences
        //获取存放开关状态的share
        get() = App.get().getSharedPreferences("kaiguan", Context.MODE_PRIVATE)
    val shareRecord: SharedPreferences
        //获取各种记录的share
        get() = App.get().getSharedPreferences("record", Context.MODE_PRIVATE)

    fun addAppStartTimes(): Boolean {
        return shareRecord.edit().putInt("StartTimes", appStartTimes + 1).commit()
    }

    val appStartTimes: Int
        //获取应用的启动次数
        get() = shareRecord.getInt("StartTimes", 0)
    val userName: String?
        get() = shareRecord.getString("userName", null)
    val userPassword: String?
        get() = shareRecord.getString("userPassword", null)
    val userAccount: String?
        get() = shareRecord.getString("userAccount", null)
    val httpSessionId: String?
        get() = shareRecord.getString("sessionId", null)

    fun setHttpSessionId(sessionid: String?): Boolean {
        return shareRecord.edit().putString("sessionId", sessionid).commit()
        // TODO: Implement this method
    }

    fun setMyCardUserName(mycardUserName: String?): Boolean {
        return shareRecord.edit().putString(Record.ARG_MC_NAME, mycardUserName).commit()
    }

    val myCardUserName: String?
        get() = shareRecord.getString(Record.ARG_MC_NAME, null)

    fun setUserName(name: String?): Boolean {
        return shareRecord.edit().putString("userName", name).commit()
    }

    fun setUserAccount(account: String?): Boolean {
        return shareRecord.edit().putString("userAccount", account).commit()
    }

    fun setUserPassword(password: String?): Boolean {
        return shareRecord.edit().putString("userPassword", password).commit()
    }

    fun setScreenPadding(paddding: String?): Boolean {
        return AppsSettings.get().sharedPreferences.edit()
            .putString(Constants.PREF_WINDOW_TOP_BOTTOM, paddding).commit()
    }

    @JvmStatic
    fun setScreenPadding(position: Int): Boolean {
        return setScreenPadding(OYUtil.getArray(R.array.screen_top_bottom_value)[position])
    }

    fun setReadExpansions(isReadExpansions: Boolean): Boolean {
        return AppsSettings.get().sharedPreferences.edit()
            .putBoolean(Constants.PREF_READ_EX, isReadExpansions).commit()
    }

    @JvmStatic
    fun setOpenglVersion(position: Int): Boolean {
        return setOpenglVersion(OYUtil.getArray(R.array.opengl_version_value)[position])
    }

    fun setOpenglVersion(opengl: String?): Boolean {
        return AppsSettings.get().sharedPreferences.edit()
            .putString(Constants.PREF_OPENGL_VERSION, opengl).commit()
    }

    @JvmStatic
    val screenPaddingPosition: Int
        get() {
            val value = AppsSettings.get().screenPadding.toString() + ""
            val valueList = OYUtil.getArray(R.array.screen_top_bottom_value)
            for (i in valueList!!.indices) {
                val s = valueList[i]
                if (s == value) return i
            }
            return -1
        }
    @JvmStatic
    val openglVersionPosition: Int
        get() {
            val value = AppsSettings.get().openglVersion.toString() + ""
            val valueList = OYUtil.getArray(R.array.opengl_version_value)
            for (i in valueList!!.indices) {
                val s = valueList[i]
                if (s == value) return i
            }
            return -1
        }

    @JvmStatic
    fun setImmersiveMode(isImmeriveMode: Boolean): Boolean {
        return AppsSettings.get().sharedPreferences.edit()
            .putBoolean(Constants.PREF_IMMERSIVE_MODE, isImmeriveMode).commit()
    }

    @JvmStatic
    fun setKeepScale(isKeepScale: Boolean): Boolean {
        return AppsSettings.get().sharedPreferences.edit()
            .putBoolean(Constants.PREF_KEEP_SCALE, isKeepScale).commit()
    }

    @JvmStatic
    fun setHorizontal(isHorizontal: Boolean): Boolean {
        return AppsSettings.get().sharedPreferences.edit()
            .putBoolean(Constants.PREF_LOCK_SCREEN, isHorizontal).commit()
    }

    val isShowEz: Boolean
        get() = shareKaiguan.getBoolean("isShowEz", true)

    fun setIsShowEz(isShow: Boolean): Boolean {
        return shareKaiguan.edit().putBoolean("isShowEz", isShow).commit()
    }

    val isShowVisitDeck: Boolean
        get() = shareKaiguan.getBoolean("isShowVisitDeck", true)

    fun setShowVisitDeck(isShow: Boolean): Boolean {
        return shareKaiguan.edit().putBoolean("isShowVisitDeck", isShow).commit()
    }

    val isFristStart: Boolean
        get() = shareRecord.getBoolean("isFirstStart", true)

    fun setFirstStart(isFirstStart: Boolean): Boolean {
        return shareRecord.edit().putBoolean("isFirstStart", isFirstStart).commit()
    }

    var nextAifadianNum: Int
        get() = shareRecord.getInt("nextAifadianNum", 10 + (Math.random() * 20).toInt())
        set(num) {
            shareRecord.edit().putInt("nextAifadianNum", num).apply()
        }
    @JvmStatic
    var deckEditType: Int
        get() = shareType.getInt("deckEditType", DECK_EDIT_TYPE_DECK_MANAGEMENT)
        set(type) {
            shareType.edit().putInt("deckEditType", type).apply()
        }
    @JvmStatic
    var serverListType: Int
        get() = shareType.getInt("serverListMode", SERVER_LIST_TYPE_LIST)
        set(type) {
            shareType.edit().putInt("serverListMode", type).apply()
        }
    @JvmStatic
    var versionUpdateTime: Long
        get() = shareRecord.getLong("versionUpdateTime", 0)
        set(versionUpdateTime) {
            shareRecord.edit().putLong("versionUpdateTime", versionUpdateTime).apply()
        }
    @JvmStatic
    var isToastNewCardBag: Boolean
        get() = shareRecord.getBoolean("isToastNewCardBag", true)
        set(toastNewCardBag) {
            shareRecord.edit().putBoolean("isToastNewCardBag", toastNewCardBag).apply()
        }
    var todayStartTime: Long
        get() = shareRecord.getLong("todayStartTime", 0)
        set(versionUpdateTime) {
            shareRecord.edit().putLong("todayStartTime", versionUpdateTime).apply()
        }
}
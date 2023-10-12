package com.ourygo.ygomobile.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import cn.garymb.ygomobile.ui.mycard.bean.McUser
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement
import com.ourygo.ygomobile.base.listener.OnMcUserListener
import org.litepal.LitePal

/**
 * Create By feihua  On 2021/10/21
 */
class McUserManagement private constructor() {
    var user: McUser? = null
        private set
    private val userListenerList by lazy {
        ArrayList<OnMcUserListener>()
    }

    fun addListener(onMcUserListener: OnMcUserListener) {
        userListenerList.add(onMcUserListener)
    }

    fun removeListener(onMcUserListener: OnMcUserListener) {
        userListenerList.remove(onMcUserListener)
    }

    fun login(mUser: McUser, isUpdate: Boolean) {
        var isUpdate = isUpdate
        if (isUpdate && user != null) {
            if (!TextUtils.isEmpty(mUser.name)) user!!.name = mUser.name
            if (mUser.external_id > 0) {
                Log.e("McUserManagement", "重设" + mUser.external_id)
                user!!.external_id = mUser.external_id
            }
            if (!TextUtils.isEmpty(mUser.username)) user!!.username = mUser.username
            if (!TextUtils.isEmpty(mUser.email)) user!!.email = mUser.email
            if (!TextUtils.isEmpty(mUser.avatar_url)) user!!.avatar_url = mUser.avatar_url
            val isSave = user!!.save()
            isUpdate = true
        } else {
            user = mUser
            LitePal.deleteAll(McUser::class.java)
            Log.e("McUserManagement", "保存前" + user!!.id)
            val isSave = user!!.save()
            Log.e(
                "McUserManagement",
                (LitePal.findFirst(McUser::class.java) != null).toString() + "保存情况  " + isSave
            )
            Log.e("McUserManagement", "保存后" + user!!.id)
            isUpdate = false
        }
        HandlerUtil.sendMessage(handler, HANDLE_USER_LOGIN, isUpdate)
    }

    var handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                HANDLE_USER_LOGIN -> {
                    SharedPreferenceUtil.setMyCardUserName(user?.username)
                    var i = 0
                    while (i < userListenerList.size) {
                        val ul = userListenerList[i]
                        if (ul.isListenerEffective) {
                            if (msg.obj as Boolean) {
                                //                    Log.e("McUserManagement","回调更新"+ul.getClass().getName());
                                ul.onUpdate(user)
                            } else {
                                ul.onLogin(user, null)
                            }
                        } else {
                            userListenerList.removeAt(i)
                            i--
                        }
                        i++
                    }
                }

                HANDLE_USER_LOGOUT -> {
                    var i = 0
                    while (i < userListenerList.size) {
                        val ul = userListenerList[i]
                        if (ul.isListenerEffective) {
                            ul.onLogout(msg.obj as String)
                        } else {
                            userListenerList.removeAt(i)
                            i--
                        }
                        i++
                    }
                }

                HANDLE_USER_UPDATE -> {}
            }
        }
    }

    init {
        user = LitePal.findFirst(McUser::class.java)
        Log.e("McUserManagement", "初始化  " + (user != null))
    }

    val isLogin: Boolean
        get() = user != null

    fun logout(message: String?) {
        user = null
        LitePal.deleteAll(McUser::class.java)
        Log.e("McUserManagement", "退出登录")

//        SharedPreferences lastModified = OYApplication.get().getSharedPreferences("lastModified", Context.MODE_PRIVATE);
        ServiceManagement.getDx().disSerVice()
        HandlerUtil.sendMessage(handler, HANDLE_USER_LOGOUT, message)
    }

    companion object {
        @JvmStatic
        val instance = McUserManagement()
        private const val HANDLE_USER_LOGIN = 0
        private const val HANDLE_USER_UPDATE = 1
        private const val HANDLE_USER_LOGOUT = 2
    }
}
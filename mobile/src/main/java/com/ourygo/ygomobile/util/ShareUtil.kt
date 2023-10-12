package com.ourygo.ygomobile.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.widget.Toast
import cn.garymb.ygomobile.lite.R
import java.io.File

/**
 * Create By feihua  On 2021/10/18
 */
class ShareUtil {
    /**
     * 分享图片给QQ好友
     *
     * @param bitmap
     */
    fun shareImageToQQ(mContext: Context, bitmap: Bitmap?) {
        if (PlatformUtil.isInstallApp(mContext, PlatformUtil.PACKAGE_MOBILE_QQ)) {
            try {
                val uriToImage = Uri.parse(
                    MediaStore.Images.Media.insertImage(
                        mContext.contentResolver, bitmap, null, null
                    )
                )
                val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.putExtra(Intent.EXTRA_STREAM, uriToImage)
                shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                shareIntent.type = "image/*"
                // 遍历所有支持发送图片的应用。找到需要的应用
                val componentName = ComponentName(
                    "com.tencent.mobileqq",
                    "com.tencent.mobileqq.activity.JumpActivity"
                )
                shareIntent.component = componentName
                // mContext.startActivity(shareIntent);
                mContext.startActivity(Intent.createChooser(shareIntent, "Share"))
            } catch (e: Exception) {
//            ContextUtil.getInstance().showToastMsg("分享图片到**失败");
            }
        }
    }

    companion object {
        /**
         * 直接分享纯文本内容至QQ好友
         * @param mContext
         * @param content
         */
        fun shareQQ(mContext: Context, content: String?) {
            if (PlatformUtil.isInstallApp(mContext, PlatformUtil.PACKAGE_MOBILE_QQ)) {
                val intent = Intent("android.intent.action.SEND")
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_SUBJECT, "分享")
                intent.putExtra(Intent.EXTRA_TEXT, content)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.component = ComponentName(
                    "com.tencent.mobileqq",
                    "com.tencent.mobileqq.activity.JumpActivity"
                )
                mContext.startActivity(intent)
            } else {
                Toast.makeText(mContext, "您需要安装QQ客户端", Toast.LENGTH_LONG).show()
            }
        }

        /**
         * 直接分享图片到微信好友
         * @param context
         * @param picFile
         */
        fun shareWechatFriend(context: Context, content: String?, picFile: File?) {
            if (PlatformUtil.isInstallApp(context, PlatformUtil.PACKAGE_WECHAT)) {
                val intent = Intent()
                val cop = ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI")
                intent.component = cop
                intent.action = Intent.ACTION_SEND
                intent.type = "image/*"
                if (picFile != null) {
                    if (picFile.isFile && picFile.exists()) {
                        var uri: Uri? = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                        uri = FileProvider.getUriForFile(context, ShareToolUtil.AUTHORITY, picFile);
                        } else {
                            uri = Uri.fromFile(picFile)
                        }
                        intent.putExtra(Intent.EXTRA_STREAM, uri)
                        //                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri);
                    }
                }
                //            intent.putExtra("Kdescription", !TextUtils.isEmpty(content) ? content : "");
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                // context.startActivity(intent);
                context.startActivity(Intent.createChooser(intent, "Share"))
            } else {
                Toast.makeText(context, "您需要安装微信客户端", Toast.LENGTH_LONG).show()
            }
        }

        /**
         * 直接分享文本到微信好友
         *
         * @param context 上下文
         */
        fun shareWechatFriend(context: Context, content: String?) {
            if (PlatformUtil.isInstallApp(context, PlatformUtil.PACKAGE_WECHAT)) {
                val intent = Intent()
                val cop = ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI")
                intent.component = cop
                intent.action = Intent.ACTION_SEND
                intent.putExtra("android.intent.extra.TEXT", content)
                //            intent.putExtra("sms_body", content);
                intent.putExtra("Kdescription", if (!TextUtils.isEmpty(content)) content else "")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "您需要安装微信客户端", Toast.LENGTH_LONG).show()
            }
        }

        fun share(context: Context, message: String?) {
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            if (message != null) {
                intent.putExtra(Intent.EXTRA_TEXT, message)
            } else {
                intent.putExtra(Intent.EXTRA_TEXT, "")
            }
            intent.type = "text/plain" //设置分享发送的数据类型
            //未指定选择器，部分定制系统首次选择后，后期将无法再次改变
            //        startActivity(intent);
            //指定选择器选择使用有发送文本功能的App
            context.startActivity(Intent.createChooser(intent, OYUtil.s(R.string.app_name)))
        } //    之前有同学说在分享QQ和微信的时候，发现只要QQ或微信在打开的情况下，再调用分享只是打开了QQ和微信，却没有调用选择分享联系人的情况，这里，我要感觉一下@[努力搬砖]同学，他找出了原因。
        //    解决办法如下：
        //            mActivity.startActivity(intent);//如果微信或者QQ已经唤醒或者打开，这样只能唤醒微信，不能分享
        //    请使用 mActivity.startActivity(Intent.createChooser(intent, "Share"));
        //
    }
}
package com.ourygo.ygomobile.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.text.TextUtils
import android.widget.ImageView
import cn.garymb.ygomobile.lite.R
import cn.garymb.ygomobile.utils.BitmapUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.ourygo.ygomobile.base.listener.OnBlurImageListener
import com.yuyh.library.imgsel.ISNav
import com.yuyh.library.imgsel.config.ISListConfig
import com.yuyh.library.imgsel.ui.ISListActivity
import jp.wasabeef.glide.transformations.BlurTransformation
import java.io.File

/**
 * Create By feihua  On 2021/10/28
 */
object ImageUtil {
    val imageOption = RequestOptions
        .diskCacheStrategyOf(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.ic_image_load)
        .skipMemoryCache(true)

    @JvmStatic
    fun startImageSelect(
        context: Context?,
        request: Int,
        num: Int,
        x: Int,
        y: Int,
        outX: Int,
        outY: Int
    ) {
        ISNav.getInstance().toListActivity(context, getPicConfig(num, x, y, outX, outY), request)
    }

    fun getPicConfig(num: Int): ISListConfig {
        return getPicConfig(num, 0, 0, 0, 0)
    }

    fun getPicConfig(num: Int, x: Int, y: Int, outX: Int, outY: Int): ISListConfig {
        var dx = false
        if (num > 1) dx = true
        // 自由配置选项
        val config = ISListConfig.Builder() // 是否多选, 默认true
            .multiSelect(dx) // 是否记住上次选中记录, 仅当multiSelect为true的时候配置，默认为true
            .rememberSelected(false) // “确定”按钮背景色
            .btnBgColor(OYUtil.c(R.color.colorPrimary)) // “确定”按钮文字颜色
            .btnTextColor(Color.WHITE) // 使用沉浸式状态栏
            .statusBarColor(OYUtil.c(R.color.colorPrimary)) // 返回图标ResId
            .backResId(androidx.appcompat.R.drawable.abc_ic_ab_back_material) // 标题
            .title("图片选择") // 标题文字颜色
            .titleColor(Color.WHITE) // TitleBar背景色
            .titleBgColor(OYUtil.c(R.color.colorPrimary)) // 第一个是否显示相机，默认true
            .needCamera(true) // 最大选择图片数量，默认9
            .maxNum(num)
        if (x != 0 && y != 0) {
            config.needCrop(true)
                .cropSize(x, y, outX, outY)
        }
        return config.build()
    }

    @JvmStatic
    fun getImageList(data: Intent?): List<String>? {
        return data?.getStringArrayListExtra(ISListActivity.INTENT_RESULT)
    }

    @JvmStatic
    fun show(context: Context?, uri: String?, im: ImageView?, objectKey: String?) {
        if (!OYUtil.isContextExisted(context)) return
        if (TextUtils.isEmpty(objectKey)) {
            Glide.with(context!!)
                .load(uri)
                .apply(imageOption)
                .into(im!!)
        } else {
            Glide.with(context!!)
                .load(uri)
                .signature(ObjectKey(objectKey!!))
                .apply(imageOption)
                .into(im!!)
        }
    }

    fun showBlur(context: Context?, uri: String?, im: ImageView?, objectKey: String?) {
        if (!OYUtil.isContextExisted(context)) return
        Glide.with(context!!)
            .load(uri)
            .signature(ObjectKey(objectKey!!))
            .apply(RequestOptions.bitmapTransform(BlurTransformation(50)))
            .into(im!!)
    }

    fun getBlurImage(
        context: Context?,
        imagePath: String?,
        onBlurImageListener: OnBlurImageListener
    ) {
//        SimpleTarget<Bitmap> simpleTarget=
        Glide.with(context!!)
            .asBitmap()
            .load(imagePath)
            .apply(RequestOptions.bitmapTransform(BlurTransformation(50)))
            .into(object : SimpleTarget<Bitmap?>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                    val path = File(
                        Record.imageCachePath,
                        System.currentTimeMillis().toString() + ".jpg"
                    ).absolutePath
                    if (BitmapUtil.saveBitmap(resource, path, 100)) onBlurImageListener.onBlurImage(
                        path,
                        null
                    ) else onBlurImageListener.onBlurImage(null, "保存失败")
                }
            })
        //        new ViewTarget<ImageView, Drawable>(iv_bg1) {
//                    @Override
//                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
//                        Drawable current = resource.getCurrent();
//                        //设置背景图
//                        //image2.setBackground(current);
//                        //设置图片
//                        iv_bg1.setImageDrawable(current);
//                    }
//                });
    }
}
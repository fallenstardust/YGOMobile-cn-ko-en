package com.ourygo.ygomobile.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.config.ISListConfig;
import com.yuyh.library.imgsel.ui.ISListActivity;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

/**
 * Create By feihua  On 2021/10/28
 */
public class ImageUtil {

    public static final RequestOptions imageOption = RequestOptions
            .diskCacheStrategyOf(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_image_load)
            .skipMemoryCache(true);

    public static void startImageSelect(Context context, int request, int num, int x, int y, int outX, int outY) {
        ISNav.getInstance().toListActivity(context, getPicConfig(num, x, y, outX, outY), request);
    }

    public static ISListConfig getPicConfig(int num) {
        return getPicConfig(num, 0, 0, 0, 0);
    }

    public static ISListConfig getPicConfig(int num, int x, int y, int outX, int outY) {
        boolean dx = false;
        if (num > 1)
            dx = true;
        // 自由配置选项
        ISListConfig.Builder config = new ISListConfig.Builder()
                // 是否多选, 默认true
                .multiSelect(dx)
                // 是否记住上次选中记录, 仅当multiSelect为true的时候配置，默认为true
                .rememberSelected(false)
                // “确定”按钮背景色
                .btnBgColor(OYUtil.c(R.color.colorPrimary))
                // “确定”按钮文字颜色
                .btnTextColor(Color.WHITE)
                // 使用沉浸式状态栏
                .statusBarColor(OYUtil.c(R.color.colorPrimary))
                // 返回图标ResId
                .backResId(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                // 标题
                .title("图片选择")
                // 标题文字颜色
                .titleColor(Color.WHITE)
                // TitleBar背景色
                .titleBgColor(OYUtil.c(R.color.colorPrimary))
                // 第一个是否显示相机，默认true
                .needCamera(true)
                // 最大选择图片数量，默认9
                .maxNum(num);

        if (x != 0 && y != 0) {
            config.needCrop(true)
                    .cropSize(x, y, outX, outY);
        }


        return config.build();
    }

    public static List<String> getImageList(Intent data) {
        if (data == null)
            return null;
        return data.getStringArrayListExtra(ISListActivity.INTENT_RESULT);
    }


    public static void show(Context context, String uri, final ImageView im, String objectKey) {
        if (!OYUtil.isContextExisted(context))
            return;
        if (TextUtils.isEmpty(objectKey)) {
            Glide.with(context)
                    .load(uri)
                    .apply(imageOption)
                    .into(im);
        } else {
            Glide.with(context)
                    .load(uri)
                    .signature(new ObjectKey(objectKey))
                    .apply(imageOption)
                    .into(im);
        }
    }


}

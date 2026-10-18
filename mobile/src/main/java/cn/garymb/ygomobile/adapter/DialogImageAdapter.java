package cn.garymb.ygomobile.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.ImageItem;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.YGOUtil;

public class DialogImageAdapter extends BaseAdapter {
    private DialogPlus mDialogPlus;
    private Context context;
    private ImageView mImageView;
    private ArrayList<ImageItem> imageItems;
    private int itemWidth;
    private int itemHeight;
    private String mFilename;
    private AppsSettings mSettings;

    // 缩略图降采样后的解码目标尺寸(px)，在构造时按 item 显示大小计算一次并复用
    private final int thumbWidth;
    private final int thumbHeight;

    public DialogImageAdapter(DialogPlus dlg, Context context, ImageView imageView, ArrayList<ImageItem> imageItems, int[] itemWidth_itemHeight, String outFile, OnImageSelectedListener listener) {
        this.mDialogPlus = dlg;
        this.context = context;
        this.mImageView = imageView;
        this.imageItems = imageItems;
        this.itemWidth = itemWidth_itemHeight[0];
        this.itemHeight = itemWidth_itemHeight[1];
        this.mFilename = outFile;
        this.mSettings = AppsSettings.get();  // 获取全局的AppsSettings实例
        // 与 getView 中 ImageView 的 LayoutParams 保持一致的显示尺寸，用于 Glide 降采样解码
        int tw = itemWidth;
        int th = itemHeight;
        if (itemWidth >= 960) tw = itemWidth / 7;
        if (itemHeight >= 540) th = itemHeight / 7;
        this.thumbWidth = Math.max(1, tw);
        this.thumbHeight = Math.max(1, th);
        setOnImageSelectedListener(listener); // 设置监听器
    }


    // 定义回调接口
    public interface OnImageSelectedListener {
        void onImageSelected(String outFilePath, String title, int width, int height);
    }

    private OnImageSelectedListener mListener;

    // 提供一个公共方法来设置监听器
    public void setOnImageSelectedListener(OnImageSelectedListener listener) {
        this.mListener = listener;
    }

    // 由后台目录扫描完成后在主线程调用，追加图片项并刷新
    public void addItems(ArrayList<ImageItem> extra) {
        if (extra == null || extra.isEmpty()) {
            return;
        }
        imageItems.addAll(extra);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return imageItems.size();
    }

    @Override
    public Object getItem(int position) {
        return imageItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ImageView iv;
        final ImageItem item = imageItems.get(position);
        //初始化item的布局iv
        if (convertView == null) {
            iv = new ImageView(context);
            int width = itemWidth;
            int height = itemHeight;
            if (itemWidth >= 960) width = itemWidth / 7;
            if (itemHeight >= 540) height = itemHeight / 7;
            iv.setLayoutParams(new GridView.LayoutParams(width, height));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(3, 3, 3, 3);
        } else {
            iv = (ImageView) convertView;
        }

        // 复用View时先清空，避免残留上一个item的旧图（Glide 也会自动取消该 ImageView 上正在进行的旧请求）
        iv.setImageDrawable(null);

        if (position == 0) {
            // 设置特别的item的图标或文本
            iv.setImageResource(R.drawable.ic_copy);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String outFilePath = new File(mFilename).getAbsolutePath();
                    mDialogPlus.dismiss();
                    //打开系统文件相册
                    showImageCropChooser(outFilePath, context.getString(R.string.dialog_select_image), itemWidth, itemHeight);
                }
            });
        } else {
            // 加载普通图片：交给 Glide 在后台线程异步解码，并按缩略图尺寸降采样，避免 UI 线程同步解码原图导致的卡顿
            final File imgFile = new File(item.getImagePath());
            Glide.with(context)
                    .load(imgFile)
                    .apply(new RequestOptions()
                            .override(thumbWidth, thumbHeight)          // 只解码到缩略图所需尺寸，大幅降低内存与解码耗时
                            .downsample(DownsampleStrategy.CENTER_INSIDE)
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // 缓存降采样后的缩略图，重复打开秒显
                            .centerInside()
                            .placeholder(R.drawable.ic_copy))
                    .into(iv);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        FileUtils.copyFile(imgFile.getPath(), mFilename);
                    } catch (IOException e) {
                        YGOUtil.showTextToast(e + "");
                    }
                    mDialogPlus.dismiss();
                    mSettings.setImage(mFilename, itemWidth, itemHeight, mImageView);

                }
            });
        }

        return iv;
    }

    protected void showImageCropChooser(String outFilePath, String title, int width, int height) {
        if (mListener != null) {
            mListener.onImageSelected(outFilePath, title, width, height);
        } else {
            YGOUtil.showTextToast("no such listener");
        }
    }

}
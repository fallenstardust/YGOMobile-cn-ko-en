package cn.garymb.ygomobile.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.ImageItem;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.CurImageInfo;
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

    public DialogImageAdapter(DialogPlus dlg, Context context, ImageView imageView, ArrayList<ImageItem> imageItems, int[] itemWidth_itemHeight, String outFile, OnImageSelectedListener listener) {
        this.mDialogPlus = dlg;
        this.context = context;
        this.mImageView = imageView;
        this.imageItems = imageItems;
        if (itemWidth_itemHeight[0] >= 960) itemWidth_itemHeight[0]/= 7;
        if (itemWidth_itemHeight[1] >= 540) itemWidth_itemHeight[1]/= 7;
        this.itemWidth = itemWidth_itemHeight[0];
        this.itemHeight = itemWidth_itemHeight[1];
        this.mFilename = outFile;
        this.mSettings = AppsSettings.get();  // 获取全局的AppsSettings实例
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
            iv.setLayoutParams(new GridView.LayoutParams(itemWidth, itemHeight));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(3, 3, 3, 3);
        } else {
            iv = (ImageView) convertView;
        }

        if (position == 0) {
            // 设置特别的item的图标或文本
            iv.setImageResource(R.drawable.ic_copy);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // 请替换为实际的图标资源
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
            // 加载普通图片
            File imgFile = new File(item.getImagePath());
            if (imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                iv.setImageBitmap(myBitmap);
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
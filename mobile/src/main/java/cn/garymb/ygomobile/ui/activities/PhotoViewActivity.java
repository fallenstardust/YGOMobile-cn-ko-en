package cn.garymb.ygomobile.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;

import java.io.IOException;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import uk.co.senab.photoview.PhotoView;


public class PhotoViewActivity extends BaseActivity {

    PhotoView mPhotoView;
    private long cardCode;
    private String mName;
    private ImageLoader mImageLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
        final Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        enableBackHome();
        mImageLoader = ImageLoader.get(this);
        mPhotoView = $(R.id.photoview);
        if (loadItem(getIntent())) {
            loadImage();
        }
    }

    @Override
    protected void onDestroy() {
        ImageLoader.onDestory(this);
        try {
            mImageLoader.close();
        } catch (IOException e) {
        }
        super.onDestroy();
    }

    private void loadImage() {
        if (!TextUtils.isEmpty(mName)) {
            setTitle(mName);
        }
        mImageLoader.bindImage(mPhotoView, cardCode, null, true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (loadItem(intent)) {
            loadImage();
        }
    }

    public static void showImage(Context context, long code, String name) {
        Intent intent = new Intent(context, PhotoViewActivity.class);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        intent.putExtra(Intent.EXTRA_TEXT, code);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private boolean loadItem(Intent intent) {
        try {
            if (intent.hasExtra(Intent.EXTRA_TITLE)) {
                mName = intent.getStringExtra(Intent.EXTRA_TITLE);
            }
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                cardCode = intent.getLongExtra(Intent.EXTRA_TEXT, 0);
            }
        } catch (Exception e) {

        }
        return cardCode > 0;
    }
}

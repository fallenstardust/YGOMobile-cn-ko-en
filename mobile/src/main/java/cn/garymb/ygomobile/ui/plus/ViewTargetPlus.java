package cn.garymb.ygomobile.ui.plus;

import android.os.Build;
import android.view.View;

import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.ViewTarget;


public class ViewTargetPlus extends ViewTarget<View, GlideDrawable> {
    public ViewTargetPlus(View view) {
        super(view);
    }

    @Override
    public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> glideAnimation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            this.view.setBackground(resource);
        } else {
            this.view.setBackgroundDrawable(resource);
        }
    }
}

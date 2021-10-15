package cn.garymb.ygomobile.ui.plus;

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;


public class ViewTargetPlus extends CustomViewTarget<View, Drawable> {
    public ViewTargetPlus(View view) {
        super(view);
    }

    @Override
    protected void onResourceCleared(@Nullable Drawable placeholder) {
        this.view.setBackground(placeholder);
    }

    @Override
    public void onLoadFailed(@Nullable Drawable errorDrawable) {
        this.view.setBackground(errorDrawable);
    }

    @Override
    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
        this.view.setBackground(resource);
        if (transition != null) {
            transition.transition(resource, new Transition.ViewAdapter() {
                @Override
                public View getView() {
                    return view;
                }

                @Nullable
                @Override
                public Drawable getCurrentDrawable() {
                    return view.getBackground();
                }

                @Override
                public void setDrawable(Drawable drawable) {
                    view.setBackground(drawable);
                }
            });
        }
    }
}

package com.ourygo.ygomobile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.GridLayoutAnimationController;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

/**
 * RecyclerView with support for grid animations.
 *
 * Based on:
 * https://gist.github.com/Musenkishi/8df1ab549857756098ba
 * Credit to Freddie (Musenkishi) Lust-Hed
 *
 * ...which in turn is based on the GridView implementation of attachLayoutParameters(...):
 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/GridView.java
 *
 */

public class GridRecyclerView extends RecyclerView
 {
     public GridRecyclerView(Context context) {
         super(context);
     }

     public GridRecyclerView(Context context, @Nullable AttributeSet attrs) {
         super(context, attrs);
     }

     public GridRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
         super(context, attrs, defStyle);
     }

     /**
      * 支持GridLayoutManager以及StaggeredGridLayoutManager
      *
      * @param child
      * @param params
      * @param index
      * @param count
      */
     @Override
     protected void attachLayoutAnimationParameters(View child, ViewGroup.LayoutParams params,
                                                    int index, int count) {
         LayoutManager layoutManager = this.getLayoutManager();
         if (getAdapter() != null && (layoutManager instanceof GridLayoutManager
                 || layoutManager instanceof StaggeredGridLayoutManager)) {

             GridLayoutAnimationController.AnimationParameters animationParams =
                     (GridLayoutAnimationController.AnimationParameters) params.layoutAnimationParameters;

             if (animationParams == null) {
                 animationParams = new GridLayoutAnimationController.AnimationParameters();
                 params.layoutAnimationParameters = animationParams;
             }

             int columns = 0;
             if (layoutManager instanceof GridLayoutManager) {
                 columns = ((GridLayoutManager) layoutManager).getSpanCount();
             } else {
                 columns = ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
             }

             animationParams.count = count;
             animationParams.index = index;
             animationParams.columnsCount = columns;
             animationParams.rowsCount = count / columns;

             final int invertedIndex = count - 1 - index;
             animationParams.column = columns - 1 - (invertedIndex % columns);
             animationParams.row = animationParams.rowsCount - 1 - invertedIndex / columns;

         } else {
             super.attachLayoutAnimationParameters(child, params, index, count);
         }
     }
}

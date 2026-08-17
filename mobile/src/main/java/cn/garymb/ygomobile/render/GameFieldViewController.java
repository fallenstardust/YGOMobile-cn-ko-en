package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 决斗场容器控制器：布局模式（rotationX=60° 透视 LinearLayout），
 * 不再承载 Canvas 卡片渲染，仅管理容器显隐；绘制/选中/高亮接口暂为空实现
 */
public class GameFieldViewController {

    private final LinearLayout duelFieldLayout;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GameFieldViewController(Activity activity) {
        duelFieldLayout = activity.findViewById(R.id.layout_duel_field);
    }

    public void init(GameField field, ImageLoader imageLoader, GameFieldView.OnCardClickListener listener) {
        // 布局容器模式：卡片渲染已移交 XML 布局，无需 Canvas 绑定
    }

    public void show() {
        if (duelFieldLayout != null) {
            duelFieldLayout.setVisibility(View.VISIBLE);
        }
    }

    public void hide() {
        if (duelFieldLayout != null) {
            duelFieldLayout.setVisibility(View.GONE);
        }
    }

    public void invalidate() {
        // 布局容器模式：无需 Canvas 重绘
    }

    public void highlightField(int mask) {
        // 布局容器模式：暂不实现区域高亮
    }

    public void clearHighlight() {
        highlightField(0);
    }

    public void selectCard(int controler, int location, int sequence) {
        // 布局容器模式：暂不实现卡片选中
    }

    public void clearSelection() {
        // 布局容器模式：暂不实现
    }

    public void selectCardWithAutoClear(int controler, int location, int sequence, long clearDelayMs) {
        // 布局容器模式：暂不实现
    }

    public LinearLayout getView() {
        return duelFieldLayout;
    }
}
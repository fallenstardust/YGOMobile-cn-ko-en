package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 决斗场容器控制器：驱动 GameFieldView 的 Canvas 3D 渲染，
 * 负责场地/区域/场上卡/堆叠区/双方手卡的绘制，以及选中/高亮/连锁的接口转发。
 */
public class GameFieldViewController {

    private final GameFieldView fieldView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GameField field;

    public GameFieldViewController(Activity activity) {
        fieldView = activity.findViewById(R.id.game_field_view);
    }

    public void init(GameField field, ImageLoader imageLoader, GameFieldView.OnCardClickListener listener) {
        this.field = field;
        if (fieldView == null) return;
        fieldView.setField(field);
        fieldView.setImageLoader(imageLoader);
        fieldView.setCardClickListener(listener);
        if (field != null) {
            field.refreshAllCards();
        }
        fieldView.startAnimationLoop();
        fieldView.invalidate();
    }

    public void show() {
        if (field != null) {
            field.refreshAllCards();
        }
        if (fieldView != null) {
            fieldView.setVisibility(View.VISIBLE);
            fieldView.invalidate();
        }
    }

    public void hide() {
        if (fieldView != null) {
            fieldView.setVisibility(View.GONE);
        }
    }

    public void invalidate() {
        if (fieldView != null) {
            fieldView.invalidate();
        }
    }

    public void highlightField(int mask) {
        if (fieldView != null) {
            fieldView.setHighlightFieldMask(mask);
        }
    }

    public void clearHighlight() {
        if (fieldView != null) {
            fieldView.setHighlightFieldMask(0);
            fieldView.clearSelection();
        }
    }

    public void selectCard(int controler, int location, int sequence) {
        if (fieldView != null) {
            fieldView.setSelectedCard(controler, location, sequence);
        }
    }

    public void clearSelection() {
        if (fieldView != null) {
            fieldView.clearSelection();
        }
    }

    public void selectCardWithAutoClear(int controler, int location, int sequence, long clearDelayMs) {
        if (fieldView == null) return;
        fieldView.setSelectedCard(controler, location, sequence);
        mainHandler.postDelayed(() -> {
            if (fieldView != null) {
                fieldView.clearSelection();
            }
        }, clearDelayMs);
    }

    public GameFieldView getView() {
        return fieldView;
    }
}
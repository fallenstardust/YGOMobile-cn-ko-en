package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

public class GameFieldViewController {

    private final GameFieldView gameFieldView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GameFieldViewController(Activity activity) {
        gameFieldView = activity.findViewById(R.id.game_field_view);
    }

    public void init(GameField field, ImageLoader imageLoader, GameFieldView.OnCardClickListener listener) {
        if (gameFieldView != null) {
            gameFieldView.setField(field);
            gameFieldView.setImageLoader(imageLoader);
            gameFieldView.setCardClickListener(listener);
        }
    }

    public void setVisible(boolean visible) {
        if (gameFieldView != null) {
            gameFieldView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void invalidate() {
        if (gameFieldView != null) {
            mainHandler.post(gameFieldView::invalidate);
        }
    }

    public void setHighlightFieldMask(int mask) {
        if (gameFieldView != null) {
            gameFieldView.setHighlightFieldMask(mask);
            gameFieldView.invalidate();
        }
    }

    public void clearHighlight() {
        setHighlightFieldMask(0);
    }

    public void setSelectedCard(int controler, int location, int sequence) {
        if (gameFieldView != null) {
            gameFieldView.setSelectedCard(controler, location, sequence);
        }
    }

    public void clearSelection() {
        if (gameFieldView != null) {
            gameFieldView.clearSelection();
        }
    }

    public void clearSelectionDelayed(long delayMs) {
        mainHandler.postDelayed(() -> clearSelection(), delayMs);
    }

    public GameFieldView getView() {
        return gameFieldView;
    }
}
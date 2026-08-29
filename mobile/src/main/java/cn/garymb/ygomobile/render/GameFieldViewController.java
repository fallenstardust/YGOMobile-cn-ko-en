package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 决斗场容器控制器：驱动 GameFieldView 的 Canvas 3D 渲染，
 * 负责场地/区域/场上卡/堆叠区/双方手卡的绘制，以及选中/高亮/连锁的接口转发。
 * <p>
 * 监听统一收口：GameFieldView 的所有监听（卡片/区域点击与长按、阶段按钮、相机变化）
 * 均以本控制器自身实现注册，手势识别（GestureDetector）也由本控制器持有；
 * 业务方（GameFieldController 等）注册委托回调，全部回调调用经此中转。
 */
public class GameFieldViewController
        implements GameFieldView.OnCardClickListener, GameFieldView.OnPhaseButtonListener {

    private final GameFieldView fieldView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GameField field;
    private GestureDetector gestureDetector;

    // === 监听委托：业务方在此注册，控制器的监听实现方法统一调用 ===
    private GameFieldView.OnCardClickListener cardClickDelegate;
    private GameFieldView.OnPhaseButtonListener phaseButtonDelegate;
    private Runnable cameraChangedDelegate;

    /**
     * 相机变化内部代理：只向视图注册一次，触发时转发给可随时替换的业务委托
     */
    private final Runnable cameraChangedProxy = new Runnable() {
        @Override
        public void run() {
            Runnable r = cameraChangedDelegate;
            if (r != null) r.run();
        }
    };

    public GameFieldViewController(Activity activity) {
        fieldView = activity.findViewById(R.id.game_field_view);
        if (fieldView != null) {
            // 本控制器作为视图唯一监听者：全部回调调用经此中转
            fieldView.setCardClickListener(this);
            fieldView.setPhaseButtonListener(this);
            fieldView.setOnCameraChangedListener(cameraChangedProxy);
            setupGestureHandling(activity);
        }
    }

    /**
     * 手势识别（原 GameFieldView 内部匿名监听）迁移至此：
     * 点按/长按在本控制器识别，再回调视图拾取入口（dispatchTap/dispatchLongPress）
     */
    private void setupGestureHandling(Activity activity) {
        gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                fieldView.dispatchTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                fieldView.dispatchLongPress(e.getX(), e.getY());
            }
        });
        fieldView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    public void init(GameField field, ImageLoader imageLoader, GameFieldView.OnCardClickListener listener) {
        this.field = field;
        this.cardClickDelegate = listener;
        if (fieldView == null) return;
        fieldView.setField(field);
        fieldView.setImageLoader(imageLoader);
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

    /** 场地中轴锚点屏幕坐标（阶段按钮行定位用），相机未就绪返回 null */
    public float[] projectFieldMidline() {
        return fieldView != null ? fieldView.projectFieldMidline() : null;
    }

    // ==================== 监听注册（业务方使用） ====================

    /**
     * 注册卡片/区域点击与长按委托（视图监听已由本控制器实现，此处仅换委托）
     */
    public void setOnCardClickListener(GameFieldView.OnCardClickListener listener) {
        cardClickDelegate = listener;
    }

    /**
     * 注册相机重建委托（视图侧由 cameraChangedProxy 常驻，委托可随时替换）
     */
    public void setOnCameraChangedListener(Runnable listener) {
        cameraChangedDelegate = listener;
    }

    /**
     * 注册阶段按钮点击委托（视图监听已由本控制器实现，此处仅换委托）
     */
    public void setPhaseButtonListener(GameFieldView.OnPhaseButtonListener listener) {
        phaseButtonDelegate = listener;
    }

    // ==================== GameFieldView.OnCardClickListener（统一转发委托） ====================

    @Override
    public void onCardClick(int player, int location, int sequence, float tapX, float tapY) {
        GameFieldView.OnCardClickListener l = cardClickDelegate;
        if (l != null) l.onCardClick(player, location, sequence, tapX, tapY);
    }

    @Override
    public void onZoneClick(int player, int location, int sequence, float tapX, float tapY) {
        GameFieldView.OnCardClickListener l = cardClickDelegate;
        if (l != null) l.onZoneClick(player, location, sequence, tapX, tapY);
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence) {
        GameFieldView.OnCardClickListener l = cardClickDelegate;
        if (l != null) l.onFieldLongPress(player, location, sequence);
    }

    // ==================== GameFieldView.OnPhaseButtonListener（统一转发委托） ====================

    @Override
    public void onPhaseNextClicked() {
        GameFieldView.OnPhaseButtonListener l = phaseButtonDelegate;
        if (l != null) l.onPhaseNextClicked();
    }

    @Override
    public void onPhaseEpClicked() {
        GameFieldView.OnPhaseButtonListener l = phaseButtonDelegate;
        if (l != null) l.onPhaseEpClicked();
    }

    /** 场内阶段按钮显示状态全量下发（当前阶段/下一阶段/结束阶段） */
    public void setPhaseDisplay(boolean currentVisible, String currentLabel,
                                String nextLabel, boolean epVisible) {
        if (fieldView != null) {
            fieldView.setPhaseDisplay(currentVisible, currentLabel, nextLabel, epVisible);
        }
    }
}
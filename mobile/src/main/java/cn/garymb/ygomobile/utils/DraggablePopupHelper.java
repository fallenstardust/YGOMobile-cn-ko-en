package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;

public class DraggablePopupHelper {
    private static final String PREF_NAME = "popup_positions";
    private static final String KEY_X = "_x";
    private static final String KEY_Y = "_y";
    private static final long LONG_PRESS_TIMEOUT = 300;
    private static final boolean ENABLE_DRAG = false;

    private final Context context;
    private final SharedPreferences prefs;
    private final String dialogId;

    private int lastX = 0;
    private int lastY = 0;
    private boolean hasSavedPosition = false;

    public DraggablePopupHelper(Context context, String dialogId) {
        this.context = context;
        this.dialogId = dialogId;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPosition();
    }

    private void loadPosition() {
        lastX = prefs.getInt(dialogId + KEY_X, 0);
        lastY = prefs.getInt(dialogId + KEY_Y, 0);
        hasSavedPosition = prefs.contains(dialogId + KEY_X);
    }

    public void savePosition(int x, int y) {
        lastX = x;
        lastY = y;
        hasSavedPosition = true;
        prefs.edit()
            .putInt(dialogId + KEY_X, x)
            .putInt(dialogId + KEY_Y, y)
            .apply();
    }

    public void resetPosition() {
        lastX = 0;
        lastY = 0;
        hasSavedPosition = false;
        prefs.edit()
            .remove(dialogId + KEY_X)
            .remove(dialogId + KEY_Y)
            .apply();
    }

    public boolean hasSavedPosition() {
        return hasSavedPosition;
    }

    public int getLastX() {
        return lastX;
    }

    public int getLastY() {
        return lastY;
    }

    public void setupDraggablePopup(PopupWindow popupWindow, View contentView) {
        if (!ENABLE_DRAG) return;
        final float[] initialTouchX = new float[1];
        final float[] initialTouchY = new float[1];
        final int[] initialPopupX = new int[1];
        final int[] initialPopupY = new int[1];
        final boolean[] isLongPress = new boolean[]{false};
        final boolean[] hasMovedAfterLongPress = new boolean[]{false};

        contentView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX[0] = event.getRawX();
                    initialTouchY[0] = event.getRawY();
                    
                    int[] location = new int[2];
                    contentView.getLocationOnScreen(location);
                    initialPopupX[0] = location[0];
                    initialPopupY[0] = location[1];
                    
                    isLongPress[0] = false;
                    hasMovedAfterLongPress[0] = false;
                    
                    v.postDelayed(() -> {
                        isLongPress[0] = true;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }, LONG_PRESS_TIMEOUT);
                    
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!isLongPress[0]) {
                        return false;
                    }
                    
                    float currentTouchX = event.getRawX();
                    float currentTouchY = event.getRawY();
                    float deltaX = currentTouchX - initialTouchX[0];
                    float deltaY = currentTouchY - initialTouchY[0];
                    
                    if (!hasMovedAfterLongPress[0]) {
                        if (deltaX != 0 || deltaY != 0) {
                            hasMovedAfterLongPress[0] = true;
                        } else {
                            return true;
                        }
                    }
                    
                    int newPopupX = initialPopupX[0] + (int) deltaX;
                    int newPopupY = initialPopupY[0] + (int) deltaY;
                    
                    popupWindow.update(newPopupX, newPopupY, -1, -1);
                    
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isLongPress[0] && hasMovedAfterLongPress[0]) {
                        int[] finalLocation = new int[2];
                        contentView.getLocationOnScreen(finalLocation);
                        savePosition(finalLocation[0], finalLocation[1]);
                    }
                    isLongPress[0] = false;
                    hasMovedAfterLongPress[0] = false;
                    return isLongPress[0];
            }
            return false;
        });
    }

    public void setupDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        if (!(targetView instanceof ViewGroup)) {
            setupSimpleDraggableView(targetView);
            return;
        }

        final ViewGroup viewGroup = (ViewGroup) targetView;
        final float[] initialTouchX = new float[1];
        final float[] initialTouchY = new float[1];
        final int[] initialViewX = new int[1];
        final int[] initialViewY = new int[1];
        final boolean[] isLongPress = new boolean[]{false};
        final boolean[] hasMovedAfterLongPress = new boolean[]{false};

        View.OnTouchListener dragListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialTouchX[0] = event.getRawX();
                        initialTouchY[0] = event.getRawY();
                        
                        initialViewX[0] = targetView.getLeft();
                        initialViewY[0] = targetView.getTop();
                        
                        isLongPress[0] = false;
                        hasMovedAfterLongPress[0] = false;
                        
                        v.postDelayed(() -> {
                            isLongPress[0] = true;
                            targetView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        }, LONG_PRESS_TIMEOUT);
                        
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        if (!isLongPress[0]) {
                            return false;
                        }
                        
                        float currentTouchX = event.getRawX();
                        float currentTouchY = event.getRawY();
                        float deltaX = currentTouchX - initialTouchX[0];
                        float deltaY = currentTouchY - initialTouchY[0];
                        
                        if (!hasMovedAfterLongPress[0]) {
                            if (deltaX != 0 || deltaY != 0) {
                                hasMovedAfterLongPress[0] = true;
                            } else {
                                return true;
                            }
                        }
                        
                        int newViewX = initialViewX[0] + (int) deltaX;
                        int newViewY = initialViewY[0] + (int) deltaY;
                        
                        targetView.setLeft(newViewX);
                        targetView.setTop(newViewY);
                        targetView.setRight(newViewX + targetView.getWidth());
                        targetView.setBottom(newViewY + targetView.getHeight());
                        
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isLongPress[0] && hasMovedAfterLongPress[0]) {
                            savePosition(targetView.getLeft(), targetView.getTop());
                        }
                        boolean wasDragging = isLongPress[0] && hasMovedAfterLongPress[0];
                        isLongPress[0] = false;
                        hasMovedAfterLongPress[0] = false;
                        return wasDragging;
                }
                return false;
            }
        };

        setTouchListenerRecursively(viewGroup, dragListener);
        targetView.setOnTouchListener(dragListener);
    }

    private void setTouchListenerRecursively(ViewGroup parent, View.OnTouchListener listener) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setOnTouchListener(listener);
            
            if (child instanceof ViewGroup) {
                setTouchListenerRecursively((ViewGroup) child, listener);
            }
        }
    }

    private void setupSimpleDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        final float[] initialTouchX = new float[1];
        final float[] initialTouchY = new float[1];
        final int[] initialViewX = new int[1];
        final int[] initialViewY = new int[1];
        final boolean[] isLongPress = new boolean[]{false};
        final boolean[] hasMovedAfterLongPress = new boolean[]{false};

        targetView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX[0] = event.getRawX();
                    initialTouchY[0] = event.getRawY();
                    
                    initialViewX[0] = targetView.getLeft();
                    initialViewY[0] = targetView.getTop();
                    
                    isLongPress[0] = false;
                    hasMovedAfterLongPress[0] = false;
                    
                    v.postDelayed(() -> {
                        isLongPress[0] = true;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }, LONG_PRESS_TIMEOUT);
                    
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!isLongPress[0]) {
                        return false;
                    }
                    
                    float currentTouchX = event.getRawX();
                    float currentTouchY = event.getRawY();
                    float deltaX = currentTouchX - initialTouchX[0];
                    float deltaY = currentTouchY - initialTouchY[0];
                    
                    if (!hasMovedAfterLongPress[0]) {
                        if (deltaX != 0 || deltaY != 0) {
                            hasMovedAfterLongPress[0] = true;
                        } else {
                            return true;
                        }
                    }
                    
                    int newViewX = initialViewX[0] + (int) deltaX;
                    int newViewY = initialViewY[0] + (int) deltaY;
                    
                    targetView.setLeft(newViewX);
                    targetView.setTop(newViewY);
                    targetView.setRight(newViewX + targetView.getWidth());
                    targetView.setBottom(newViewY + targetView.getHeight());
                    
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isLongPress[0] && hasMovedAfterLongPress[0]) {
                        savePosition(targetView.getLeft(), targetView.getTop());
                    }
                    isLongPress[0] = false;
                    hasMovedAfterLongPress[0] = false;
                    return isLongPress[0];
            }
            return false;
        });
    }

    public void applySavedPositionToView(View targetView) {
        if (hasSavedPosition) {
            targetView.post(() -> {
                targetView.setLeft(lastX);
                targetView.setTop(lastY);
                targetView.setRight(lastX + targetView.getWidth());
                targetView.setBottom(lastY + targetView.getHeight());
            });
        }
    }

    public void showPopup(PopupWindow popupWindow, View anchorView) {
        View effectiveAnchor = anchorView;
        if (anchorView != null && anchorView.getWindowToken() == null) {
            if (context instanceof Activity) {
                effectiveAnchor = ((Activity) context).getWindow().getDecorView();
            }
        }
        try {
            if (hasSavedPosition) {
                popupWindow.showAtLocation(effectiveAnchor, Gravity.NO_GRAVITY, lastX, lastY);
            } else {
                popupWindow.showAtLocation(effectiveAnchor, Gravity.CENTER, 0, 0);
            }
        } catch (WindowManager.BadTokenException e) {
            if (context instanceof Activity) {
                View decorView = ((Activity) context).getWindow().getDecorView();
                if (hasSavedPosition) {
                    popupWindow.showAtLocation(decorView, Gravity.NO_GRAVITY, lastX, lastY);
                } else {
                    popupWindow.showAtLocation(decorView, Gravity.CENTER, 0, 0);
                }
            }
        }
    }

    public static void resetAllPositions(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}

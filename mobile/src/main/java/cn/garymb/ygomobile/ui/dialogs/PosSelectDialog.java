package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.nio.ByteBuffer;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.TextureLoader;

import ocgcore.DataManager;

/**
 * 表示形式选择弹窗，效仿 gframe game.cpp L853-864 wPosSelect 与
 * duelclient.cpp L2271-2298 MSG_SELECT_POSITION 的写法：
 * 把怪兽召唤到怪兽区域/额外怪兽区域时，若通讯（positions 位掩码）允许
 * 多种表示形式，则显示该卡各形式的图片按钮；点击后关闭弹窗并回调所选
 * 形式，由调用方发送 CTOS_RESPONSE，core 按所选形式把卡放上场并下发
 * MSG_MOVE/MSG_UPDATE_CARD 同步场地状态。
 *
 * 按钮图像规则对齐 image_manager.cpp::GetTextureButton：
 * AU 表侧攻击=卡图原图；AD 里侧攻击=卡背；
 * DU 表侧守备=卡图逆时针旋转90°（RotateImageCCW90）；DD 里侧守备=卡背旋转90°。
 */
public class PosSelectDialog {

    public static final int POS_FACEUP_ATTACK = 0x1;    // btnPSAU
    public static final int POS_FACEDOWN_ATTACK = 0x2;  // btnPSAD
    public static final int POS_FACEUP_DEFENSE = 0x4;   // btnPSDU
    public static final int POS_FACEDOWN_DEFENSE = 0x8; // btnPSDD

    public interface OnPositionSelectedListener {
        void onSelected(int position);
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    /** 按钮最大边长与间距，实际尺寸按 layout_game_right 宽度自适应收缩 */
    private static final int MAX_BUTTON_DP = 90;
    private static final int GAP_DP = 8;
    private static final int ROOT_PADDING_DP = 8;

    /** 当前正在显示的表示形式选择弹窗（去重用），由静态工厂 showPositionSelectDialog 维护 */
    private static PosSelectDialog current;

    private final Context context;
    private final ImageLoader imageLoader;

    private PopupWindow popupWindow;
    private View contentView;
    private String title;
    private OnPositionSelectedListener selectListener;
    private OnDismissListener dismissListener;
    private boolean showing;

    /**
     * MSG_SELECT_POSITION 静态工厂（供 ShowDialogUtil.showPositionSelectDialog 委托调用）：
     * data 为 GameEngine 打包的 code(4) + positions(4)。单一形式直接应答不弹窗；
     * 多形式弹出选择框，标题取系统字符串 561（对齐 game.cpp wPosSelect 的 GetSysString(561)），
     * 选择后发送 CTOS_RESPONSE，core 按所选形式把卡放上场并下发场地更新同步状态。
     */
    public static void showPositionSelectDialog(YGOProActivity activity, ImageLoader imageLoader, ByteBuffer data) {
        if (data == null || data.remaining() < 8) return;
        int code = data.getInt();
        int positions = data.getInt() & 0x0F;
        // 单一形式兜底（正常路径已在 GameEngine.onSelectPosition 拦截自动应答）
        if (positions == 0x1 || positions == 0x2 || positions == 0x4 || positions == 0x8) {
            activity.sendResponseInt(positions);
            return;
        }
        if (positions == 0) return;
        if (current != null && current.isShowing()) return;
        PosSelectDialog dialog = new PosSelectDialog(activity, imageLoader);
        current = dialog;
        dialog.setTitle(DataManager.get().getStringManager().getSystemString(561, "选择表示形式"))
                .setOnPositionSelectedListener(pos -> {
                    // 先隐藏弹窗再发送协议：core 随后将卡按所选形式放上场并同步场地状态
                    dialog.dismiss();
                    activity.sendResponseInt(pos);
                })
                .setOnDismissListener(() -> current = null);
        dialog.show(code, positions);
    }

    public PosSelectDialog(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    public PosSelectDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public PosSelectDialog setOnPositionSelectedListener(OnPositionSelectedListener listener) {
        this.selectListener = listener;
        return this;
    }

    public PosSelectDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public boolean isShowing() {
        return showing && popupWindow != null && popupWindow.isShowing();
    }

    /**
     * @param code      通讯下发的卡号（MSG_SELECT_POSITION 的 code 字段）
     * @param positions 允许选择的表示形式位掩码（0x1/0x2/0x4/0x8 组合）
     */
    public void show(int code, int positions) {
        if (isShowing()) return;
        positions &= 0x0F;
        if (positions == 0) return;
        // 只有一个形式可选则不显示弹窗（duelclient.cpp L2275-2278），直接回调唯一形式
        if (positions == POS_FACEUP_ATTACK || positions == POS_FACEDOWN_ATTACK
                || positions == POS_FACEUP_DEFENSE || positions == POS_FACEDOWN_DEFENSE) {
            if (selectListener != null) selectListener.onSelected(positions);
            return;
        }
        if (!(context instanceof Activity)) return;

        build(code, positions);
        popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 不可取消：对齐 gframe wPosSelect 无关闭按钮，必须点击形式按钮才能关闭
        // 1) outsideTouchable=false 外部点击不关闭；2) focusable=false BACK 键无法 dismiss；
        // 3) touchable=true 按钮仍正常接收触摸
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setTouchable(true);
        popupWindow.setTouchInterceptor((v, event) -> {
            // 双保险：吞掉 ACTION_OUTSIDE，防止任何窗口外触摸事件进入
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setOnDismissListener(() -> {
            showing = false;
            if (dismissListener != null) dismissListener.onDismiss();
        });

        Activity activity = (Activity) context;
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight != null && gameRight.getWidth() > 0 && gameRight.getHeight() > 0) {
            // 已布局完成：直接在 layout_game_right 内居中显示
            showCenteredInGameRight(gameRight);
            showing = true;
        } else if (gameRight != null) {
            // 宽度尚为 0（决斗 UI 刚切为可见的同帧），等布局完成后再显示
            showing = true;
            gameRight.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            gameRight.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (!showing) return; // 等待期间已被 dismiss 取消
                            showCenteredInGameRight(gameRight);
                        }
                    });
        } else {
            // 极端兜底：找不到锚点时屏幕居中
            popupWindow.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            showing = true;
        }
    }

    /** 水平+垂直均在 layout_game_right 实际范围内居中 */
    private void showCenteredInGameRight(View gameRight) {
        applyButtonSize(gameRight.getWidth());
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = contentView.getMeasuredWidth();
        int popupH = contentView.getMeasuredHeight();

        int[] loc = new int[2];
        gameRight.getLocationInWindow(loc);
        int x = loc[0] + (gameRight.getWidth() - popupW) / 2;
        int y = loc[1] + (gameRight.getHeight() - popupH) / 2;
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        popupWindow.showAtLocation(gameRight, Gravity.NO_GRAVITY, x, y);
    }

    private void build(int code, int positions) {
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_window_pos_select, null);

        ImageButton btnAU = contentView.findViewById(R.id.btn_pos_select_au);
        ImageButton btnAD = contentView.findViewById(R.id.btn_pos_select_ad);
        ImageButton btnDU = contentView.findViewById(R.id.btn_pos_select_du);
        ImageButton btnDD = contentView.findViewById(R.id.btn_pos_select_dd);

        long cardCode = code & 0xFFFFFFFFL;

        // 表侧攻击：卡图原图
        if (btnAU != null) {
            boolean visible = (positions & POS_FACEUP_ATTACK) != 0;
            btnAU.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                imageLoader.bindImage(btnAU, cardCode, ImageLoader.Type.small);
                bindPosButton(btnAU, POS_FACEUP_ATTACK);
            }
        }
        // 里侧攻击：卡背（cover）
        if (btnAD != null) {
            boolean visible = (positions & POS_FACEDOWN_ATTACK) != 0;
            btnAD.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                bindCoverImage(btnAD);
                bindPosButton(btnAD, POS_FACEDOWN_ATTACK);
            }
        }
        // 表侧守备：卡图逆时针旋转90°（RotateImageCCW90），方形按钮旋转后占位不变
        if (btnDU != null) {
            boolean visible = (positions & POS_FACEUP_DEFENSE) != 0;
            btnDU.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                imageLoader.bindImage(btnDU, cardCode, ImageLoader.Type.small);
                btnDU.setRotation(-90f);
                bindPosButton(btnDU, POS_FACEUP_DEFENSE);
            }
        }
        // 里侧守备：卡背逆时针旋转90°
        if (btnDD != null) {
            boolean visible = (positions & POS_FACEDOWN_DEFENSE) != 0;
            btnDD.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                bindCoverImage(btnDD);
                btnDD.setRotation(-90f);
                bindPosButton(btnDD, POS_FACEDOWN_DEFENSE);
            }
        }
    }

    /** 卡背图与 GL 场地渲染同源（TextureLoader 已缓存时零 IO） */
    private void bindCoverImage(ImageButton button) {
        try {
            Bitmap cover = TextureLoader.get().getCardCover(false);
            if (cover != null && !cover.isRecycled()) {
                button.setImageBitmap(cover);
            }
        } catch (Throwable ignored) {
        }
    }

    private void bindPosButton(ImageButton button, int position) {
        button.setOnClickListener(v -> {
            // 点击瞬间立即隐藏弹窗，再回调所选形式（对齐 gframe HideElement(wPosSelect, true)）
            dismiss();
            if (selectListener != null) selectListener.onSelected(position);
        });
    }

    /**
     * 按 layout_game_right 宽度自适应按钮边长：
     * 全部可见按钮 + 间距 + 根内边距不超过锚点宽度，且不超过 MAX_BUTTON_DP
     */
    private void applyButtonSize(int anchorWidth) {
        if (contentView == null || anchorWidth <= 0) return;
        ImageButton[] buttons = new ImageButton[]{
                contentView.findViewById(R.id.btn_pos_select_au),
                contentView.findViewById(R.id.btn_pos_select_ad),
                contentView.findViewById(R.id.btn_pos_select_du),
                contentView.findViewById(R.id.btn_pos_select_dd)
        };
        int count = 0;
        for (ImageButton btn : buttons) {
            if (btn != null && btn.getVisibility() == View.VISIBLE) count++;
        }
        if (count == 0) return;
        int gap = dp2px(GAP_DP);
        int avail = anchorWidth - 2 * dp2px(ROOT_PADDING_DP);
        int size = Math.min(dp2px(MAX_BUTTON_DP), (avail - (count - 1) * gap) / count);
        if (size <= 0) return;
        for (ImageButton btn : buttons) {
            if (btn == null || btn.getVisibility() != View.VISIBLE) continue;
            ViewGroup.LayoutParams lp = btn.getLayoutParams();
            if (lp != null) {
                lp.width = size;
                lp.height = size;
                btn.setLayoutParams(lp);
            }
        }
    }

    public void dismiss() {
        try {
            if (popupWindow != null && popupWindow.isShowing()) popupWindow.dismiss();
        } catch (Exception ignored) {
        }
        showing = false;
    }

    private int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
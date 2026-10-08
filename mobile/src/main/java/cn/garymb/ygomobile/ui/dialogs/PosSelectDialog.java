package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.nio.ByteBuffer;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.YGOUtil;
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
    /** 收缩模式壳层（本 dialog 私有实现）：原内容 + 底部收缩横条 + 收缩按钮 */
    private CollapseShell collapseShell;
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
        dialog.setTitle(DataManager.get().getStringManager().getSystemString(561, "请选择表示形式"))
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
        // 收缩模式：本 dialog 自带的壳层实现，点内容 XML 声明的 btn_dialog_collapse
        // 缩为与聊天输入框同高同位的底部横条，确认场地后点横条恢复
        collapseShell = new CollapseShell(contentView, title, false);
        popupWindow = new PopupWindow(collapseShell.getView(),
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        collapseShell.attachPopup(popupWindow);
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
            if (collapseShell != null) collapseShell.detach();
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
        // 记住展开态几何，收缩把手/恢复据此在展开与底部横条间 resize 窗口
        if (collapseShell != null) collapseShell.rememberExpanded(x, y, popupW, popupH);
    }

    private void build(int code, int positions) {
        contentView = LayoutInflater.from(DialogScale.wrap(context)).inflate(R.layout.popup_window_pos_select, null);

        // 标题绑 strings.conf 系统字符串 561（对齐 game.cpp wPosSelect 的 GetSysString(561)），
        // 覆盖布局 XML 的硬编码兜底文案；title 为空时保留 XML 文案
        TextView tvTitle = contentView.findViewById(R.id.tv_yes_no_title);
        if (tvTitle != null && title != null && !title.isEmpty()) {
            tvTitle.setText(title);
        }

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
        return DialogScale.dpToPx(context, dp);
    }

    // ════════════════════════════════════════════════════════════════════
    // 本 dialog 的收缩模式实现：把弹窗临时缩小成贴屏幕底部、与聊天输入框
    // 同高同位的横条（横条左侧「▲」箭头 + 标题，点击恢复），展开态由内容布局
    // XML 声明的 btn_dialog_collapse 作收缩把手；收缩/恢复经 popup.update 在
    // 「展开几何」与「底部横条几何」之间切换窗口尺寸与位置。
    // 横条保持对话框原宽与水平位置（不拉伸整屏宽）；聊天输入框不可见时
    // 兜底固定高度贴窗口底部。
    // ════════════════════════════════════════════════════════════════════

    /** 横条内标题 TextView 的 tag，供创建横条标题时定位 */
    private static final String TAG_BAR_TITLE = "collapse_bar_title";
    /** 聊天输入框不可见时的横条兜底高度（dp） */
    private static final int FALLBACK_BAR_HEIGHT_DP = 40;
    /** 横条最小高度（dp），保证可点击目标不小于舒适触控尺寸 */
    private static final int MIN_BAR_HEIGHT_DP = 28;

    private class CollapseShell {
        private final FrameLayout shell;
        private final View original;
        private final View bar;
        private final View handle;
        private PopupWindow popup;
        /** 旋转/布局变化重锚回调的宿主 decor 与监听（detach 时摘除） */
        private View decorView;
        private ViewTreeObserver.OnGlobalLayoutListener reanchorListener;
        /** 最近一次已应用的横条几何（相等则跳过重复 popup.update，防全局布局回调高频触发） */
        private int lastBarLeft = -1, lastBarWidth, lastBarTop;
        /** 展开态几何（showCenteredInGameRight 定位成功后传入，窗口坐标） */
        private boolean geometrySaved;
        private int savedX, savedY, savedW, savedH;
        private boolean collapsed;

        CollapseShell(View originalContent, String barTitle, boolean matchParentWidth) {
            this.original = originalContent;
            shell = new FrameLayout(context);
            shell.addView(originalContent, new FrameLayout.LayoutParams(
                    matchParentWidth ? ViewGroup.LayoutParams.MATCH_PARENT
                            : ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            bar = createCollapseBar((barTitle == null || barTitle.isEmpty()) ? "点击展开" : barTitle);
            styleBarWithDialogContent(bar, originalContent);
            bar.setVisibility(View.GONE);
            bar.setOnClickListener(v -> expand());
            shell.addView(bar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 把手来自 dialog 布局 XML（btn_dialog_collapse）：位置由布局声明，免程序化贴位
            handle = originalContent.findViewById(R.id.btn_dialog_collapse);
            handle.setOnClickListener(v -> collapse());
        }

        View getView() {
            return shell;
        }

        void attachPopup(PopupWindow popupWindow) {
            this.popup = popupWindow;
            // 旋转重建视图树后聊天输入框/区域重新排布，收缩态经 Activity decor 的全局布局
            // 回调重锚横条，保持与聊天输入框同高同位、在 layout_game_right 内居中
            if (context instanceof Activity) {
                decorView = ((Activity) context).getWindow().getDecorView();
                reanchorListener = this::reanchor;
                decorView.getViewTreeObserver().addOnGlobalLayoutListener(reanchorListener);
            }
        }

        /** 弹窗关闭时摘除重锚回调 */
        void detach() {
            if (decorView != null && reanchorListener != null) {
                ViewTreeObserver vto = decorView.getViewTreeObserver();
                if (vto.isAlive()) vto.removeOnGlobalLayoutListener(reanchorListener);
            }
            decorView = null;
            reanchorListener = null;
        }

        /** 记录展开态几何：showAtLocation 定位成功后调用，把手自此可收缩 */
        void rememberExpanded(int x, int y, int w, int h) {
            savedX = x;
            savedY = y;
            savedW = w;
            savedH = h;
            geometrySaved = true;
        }

        /** 收缩：窗口 resize 为与聊天输入框同高同位、在 layout_game_right 内居中的横条，内容隐藏 */
        void collapse() {
            if (collapsed || popup == null || !popup.isShowing() || !geometrySaved) return;
            int[] g = resolveBarGeometry();
            try {
                popup.update(g[0], g[2], g[1], g[3]);
            } catch (Exception ignored) {
                return;
            }
            lastBarLeft = g[0];
            lastBarWidth = g[1];
            lastBarTop = g[2];
            applyState(true);
        }

        /** 收缩态遇旋转/布局变化重锚：重解底部横条几何并应用（不变则跳过） */
        void reanchor() {
            if (!collapsed || popup == null || !popup.isShowing()) return;
            int[] g = resolveBarGeometry();
            if (g[0] == lastBarLeft && g[1] == lastBarWidth && g[2] == lastBarTop) return;
            try {
                popup.update(g[0], g[2], g[1], g[3]);
            } catch (Exception ignored) {
                return;
            }
            lastBarLeft = g[0];
            lastBarWidth = g[1];
            lastBarTop = g[2];
        }

        /**
         * 底部横条几何 {left, width, top, barHeight}（窗口坐标，供 popup.update）：
         * 水平在 layout_game_right 区域内居中（区域未布局时退回展开态原位），
         * 高度与底边按 barMetrics 与聊天输入框对齐；宽度保持展开态弹窗原宽
         * 并限幅到区域宽，使横条始终留在区域内
         */
        private int[] resolveBarGeometry() {
            int[] m = barMetrics();
            Activity activity = context instanceof Activity ? (Activity) context : null;
            View gameRight = activity != null ? activity.findViewById(R.id.layout_game_right) : null;
            int regionLeft = 0;
            int regionW = m[0];
            if (gameRight != null && gameRight.getWidth() > 0) {
                int[] loc = new int[2];
                gameRight.getLocationInWindow(loc);
                regionLeft = loc[0];
                regionW = Math.min(gameRight.getWidth(), m[0]);
            }
            int bw = savedW > 0 ? Math.min(savedW, regionW) : regionW;
            int left = gameRight != null && gameRight.getWidth() > 0
                    ? regionLeft + (regionW - bw) / 2 : savedX;
            if (left < 0) left = 0;
            if (left + bw > m[0]) left = Math.max(0, m[0] - bw);
            int top = m[1] - m[3] - m[2];
            if (top < 0) top = 0;
            return new int[]{left, bw, top, m[2]};
        }

        /** 恢复：窗口回到展开几何，内容重新显示 */
        void expand() {
            if (!collapsed || popup == null || !popup.isShowing()) return;
            try {
                popup.update(savedX, savedY, savedW, savedH);
            } catch (Exception ignored) {
                return;
            }
            applyState(false);
        }

        private void applyState(boolean doCollapse) {
            collapsed = doCollapse;
            original.setVisibility(doCollapse ? View.GONE : View.VISIBLE);
            handle.setVisibility(doCollapse ? View.GONE : View.VISIBLE);
            bar.setVisibility(doCollapse ? View.VISIBLE : View.GONE);
        }
    }

    /** 收缩横条：圆角条，左侧「▲」向上箭头图标 + 标题文本，点击恢复原始尺寸；
     *  实际使用时叠加内容根背景贴图，程序化圆角条仅作内容根无背景时的兜底 */
    private LinearLayout createCollapseBar(String title) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(YGOUtil.c(R.color.popup_frame_fill));
        bg.setCornerRadius(dp2px(6));
        bg.setStroke(dp2px(1), YGOUtil.c(R.color.popup_frame_stroke));
        bar.setBackground(bg);
        bar.setClickable(true);
        int padH = dp2px(8);
        bar.setPadding(padH, 0, padH, 0);

        ImageView arrow = createArrowButton(R.drawable.baseline_keyboard_arrow_up_24);
        // 箭头仅作“按钮”外观的指示：不可点击，否则它会吞掉横条本身的恢复点击（整条可点即恢复）
        arrow.setClickable(false);
        arrow.setFocusable(false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                dp2px(22), LinearLayout.LayoutParams.MATCH_PARENT);
        // 箭头按钮始终维持正方形：横条高（贴聊天输入框）常小于 22dp 名义宽，固定宽会把
        // 「▲」左右压窄；宽度恒等于横条实际高度（宽=高 的正方形），图标居中不裁切
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, ob, olr) -> {
            int h = b - t;
            if (h > 0 && alp.width != h) {
                alp.width = h;
                arrow.setLayoutParams(alp);
            }
        });
        bar.addView(arrow, alp);

        TextView tvTitle = new TextView(context);
        tvTitle.setTag(TAG_BAR_TITLE);
        tvTitle.setText(title == null ? "" : title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp2px(6);
        bar.addView(tvTitle, tlp);
        return bar;
    }

    /** 收缩态箭头按钮：selected_light 描边圆角底 + 指定箭头图标，图标居中自适应缩放 */
    private ImageView createArrowButton(int iconRes) {
        ImageButton button = new ImageButton(context);
        button.setBackgroundResource(R.drawable.selected_light);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp2px(2);
        button.setPadding(pad, pad, pad, pad);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setAdjustViewBounds(true);
        return button;
    }

    /** 收缩横条复用对话框内容根的背景贴图与左右内边距（克隆独立实例避免 bounds 互串） */
    private void styleBarWithDialogContent(View bar, View content) {
        Drawable bg = content.getBackground();
        if (bg != null) {
            Drawable.ConstantState st = bg.getConstantState();
            bar.setBackground(st != null ? st.newDrawable() : bg);
        }
        bar.setPadding(content.getPaddingLeft(), 0, content.getPaddingRight(), 0);
    }

    /**
     * 横条布局几何：{窗口宽, 窗口高, 横条高, 横条底边与窗口底边的间隙}（均为 px）。
     * 聊天输入框（et_chat_input）可见时与其同高、底边对齐；不可见时兜底固定高度贴窗口底部。
     */
    private int[] barMetrics() {
        Activity activity = context instanceof Activity ? (Activity) context : null;
        View decor = activity != null ? activity.getWindow().getDecorView() : null;
        int winW = decor != null && decor.getWidth() > 0
                ? decor.getWidth() : context.getResources().getDisplayMetrics().widthPixels;
        int winH = decor != null && decor.getHeight() > 0
                ? decor.getHeight() : context.getResources().getDisplayMetrics().heightPixels;
        int barH = dp2px(FALLBACK_BAR_HEIGHT_DP);
        int gap = 0;
        View chat = activity != null ? activity.findViewById(R.id.et_chat_input) : null;
        if (chat != null && chat.isShown() && chat.getHeight() > 0) {
            barH = Math.max(chat.getHeight(), dp2px(MIN_BAR_HEIGHT_DP));
            int[] loc = new int[2];
            chat.getLocationInWindow(loc);
            gap = winH - (loc[1] + chat.getHeight());
            if (gap < 0) gap = 0;
        }
        return new int[]{winW, winH, barH, gap};
    }
}
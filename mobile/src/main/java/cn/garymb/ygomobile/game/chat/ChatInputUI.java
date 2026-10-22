package cn.garymb.ygomobile.game.chat;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameTopInfoManager;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;

import android.view.View;
import android.widget.EditText;


import androidx.annotation.Nullable;

import cn.garymb.ygomobile.AppsSettings;


/**
 * 聊天输入框 UI 管理类
 * 负责管理聊天输入框的初始化、显示/隐藏、焦点控制和发送消息等功能
 */
public class ChatInputUI {
    private final Context context;
    private final GameEngine engine;
    
    private EditText etChatInput;
    private boolean isChatEnabled = true;
    /** 绑定时捕获的 XML 基准高度（px，<=0 表示 wrap/自适应），缩放不叠乘 */
    private int baseInputHeight = -2;
    /** 当前 HUD 等比缩放系数（与 gameTopInfo 同源，见 GameTopInfoManager.applyTopInfoSize） */
    private float hudScaleFactor = 1f;

    /**
     * 聊天输入框输入法显隐监听：输入法把输入框抬高时通知外部让位，收起时恢复。
     * 大厅（PlayerWaitingDialog）与决斗（是/否询问、宣言、猜拳、位置选择等各 dialog）
     * 都需要——覆盖式的 PopupWindow 与抬高后的输入框/键盘会互相遮挡，Android 12+ 的
     * FLAG_ALT_FOCUSABLE_IM 分层在实机无效，故一律由本监听驱动
     * {@link cn.garymb.ygomobile.utils.ImePopupAdjuster} 把在显弹窗抬到输入框上方。
     */
    public interface ImeVisibilityListener {
        void onImeShown();

        void onImeHidden();
    }

    @Nullable
    private ImeVisibilityListener imeVisibilityListener;
    /** 是否处于大厅等待聊天模式：仅用于输入框点击/焦点行为的大厅特例，不再门控输入法看门狗 */
    private boolean lobbyChatActive = false;
    /** 上一次判定的输入法可见态，去抖避免逐帧回调重复触发 */
    private boolean lastImeVisible = false;
    @Nullable
    private android.view.ViewTreeObserver.OnGlobalLayoutListener imeLayoutListener;
    
    /**
     * 监听器接口：用于处理发送消息事件
     */
    public interface OnChatMessageListener {
        void onSendChatMessage(String message);
    }
    
    @Nullable
    private OnChatMessageListener chatMessageListener;
    
    public ChatInputUI(Context context, GameEngine engine) {
        this.context = context;
        this.engine = engine;
    }
    
    /**
     * 绑定聊天输入框视图
     * @param chatInput 聊天输入框 EditText 实例
     */
    public void bindChatInput(EditText chatInput) {
        this.etChatInput = chatInput;
        if (etChatInput != null) {
            android.view.ViewGroup.LayoutParams lp = etChatInput.getLayoutParams();
            baseInputHeight = (lp != null) ? lp.height : -2;
        }
        setupChatInput();
        applyChatInputScale(hudScaleFactor);
        // 输入法看门狗常驻：大厅与决斗期间抬键盘都要联动在显弹窗（登记由各 dialog 完成）。
        // 旋转重建后 etChatInput 换新实例，重挂到新视图根并把去抖状态复位，
        // 使「旋转时键盘尚未收起」的场景在下一次布局回调重新判定并驱动弹窗回中央
        lastImeVisible = false;
        attachImeWatcher();
    }

    /**
     * 接入 gameTopInfo 的 HUD 缩放体系：输入框字号/高度/边距与血条面板按同一系数
     * 随 layout_game_right 实测尺寸等比缩放（修复平板高分辨率下固定 dp 显得极细、难点）
     */
    public void bindHudScale(GameTopInfoManager topInfoManager) {
        if (topInfoManager == null) return;
        hudScaleFactor = topInfoManager.getHudScaleFactor();
        applyChatInputScale(hudScaleFactor);
        topInfoManager.addHudScaleObserver(f -> {
            hudScaleFactor = f;
            applyChatInputScale(f);
        });
    }

    /** 按 HUD 系数缩放输入框：基准与 XML 一致（字号 9sp、左右边距 6dp、竖屏固定高）；
     *  手机上系数≈1 保持原样，上限 3 倍防平板过度放大；下限强为 1（不随全面屏的 HUD 内缩变小）：
     *  横屏输入框的高度由 YGOProActivity.syncChatInputHeightToToggle 锁为侧按钮列 btn_chat 实测高
     *  （与六按钮同高同基线，用户规格），本类只能改字号/边距；若让它跟着 relief 缩字就会白变小而
     *  不能回收任何纵向净空（下锚线不变），故内缩只作用于血条面板与提示行边距。
     *  竖屏不参与缩放（系数强为 1）：竖屏底部行的 6 个功能按钮为 XML 固定高，输入框
     *  若随 HUD 系数放大（30dp×系数）就会高于按钮且不再贴底（用户规格：同高且贴底） */
    private void applyChatInputScale(float f) {
        if (etChatInput == null) return;
        float scale = isPortrait() ? 1f : Math.max(1f, Math.min(3f, f));
        etChatInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f * scale);
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(6f * scale * density);
        etChatInput.setPadding(pad, 0, pad, 0);
        android.view.ViewGroup.LayoutParams lp = etChatInput.getLayoutParams();
        if (lp != null && baseInputHeight > 0) {
            lp.height = Math.round(baseInputHeight * scale);
            etChatInput.setLayoutParams(lp);
        }
    }

    /** 当前是否竖屏（竖屏底部行为「6 按钮 + 输入框」同一行，高度以按钮为基准不随屏缩放） */
    private boolean isPortrait() {
        return context.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }
    
    /**
     * 设置聊天消息发送监听器
     */
    public void setOnChatMessageListener(@Nullable OnChatMessageListener listener) {
        this.chatMessageListener = listener;
    }

    /**
     * 注册输入法显隐监听（由 YGOProActivity 接线到 {@link cn.garymb.ygomobile.utils.ImePopupAdjuster}）。
     */
    public void setImeVisibilityListener(@Nullable ImeVisibilityListener listener) {
        this.imeVisibilityListener = listener;
    }
    
    /**
     * 初始化聊天输入框的编辑器操作监听器
     */
    private void setupChatInput() {
        if (etChatInput == null) return;
        
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSend = actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN);
            
            if (isSend) {
                sendChatMessage();
                v.clearFocus();
                
                v.postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) 
                            context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }, 100);
                
                return true;
            }
            return false;
        });

        // 点击/按下输入框显式唤起输入法（大厅与决斗常驻，用户规格：决斗中显示 dialog 时也要能
        // 抬键盘）：覆盖式 PopupWindow（决斗中的是/否询问、宣言、选卡等各 dialog 与大厅
        // LanModeDialog）盖住输入框时，触摸由弹窗包装层转发进 Activity 视图树，不走 ViewRootImpl
        // 的原生触摸链路，「点到文本框自动弹键盘」的系统行为不会被触发，默认点击链路无法唤起
        // 输入法；故在回调中 post 延迟显式请求焦点并 showSoftInput，确保窗口焦点转移到 Activity
        // 并触发 IME。requestFocus 的返回值不参与判断：本就在同一时刻由用户点击发起，
        // 焦点已存在时返回 false，仍须照常唤起键盘（否则要点两次才出键盘）。
        etChatInput.setOnClickListener(v -> v.post(() -> showKeyboard(v)));
        // 按下也请求一次（与点击回调双保险）：对话框内容压住输入框上沿时，转发进来的 DOWN/UP
        // 未必配成一次 click（抬手时本层可能被置顶、手势被拆断），只挂点击监听会表现为
        // 「点了没键盘」；返回 false 不消费事件，点击、光标与焦点链路一律照旧。
        etChatInput.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.post(() -> showKeyboard(v));
            }
            return false;
        });
    }

    /**
     * 显式唤起输入法：先按隐式请求（用户主动操作的常规语义），未获准则以强制标志重试一次——
     * 触摸经弹窗层转发时系统不一定把它认作「用户显式请求」，隐式请求会被拒（表现为键盘不弹、
     * 弹窗也不抬升）。重复调用无害：键盘已在显示时 showSoftInput 为空操作，不会被收起。
     */
    private void showKeyboard(View v) {
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        try {
            v.requestFocus();
            if (!imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)) {
                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED);
            }
        } catch (Exception e) {
            android.util.Log.e("ChatInputUI", "Chat input show IME error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 发送聊天消息
     */
    private void sendChatMessage() {
        if (etChatInput == null) return;
        
        String message = etChatInput.getText().toString().trim();
        if (message.isEmpty()) return;
        
        // 通过监听器回调发送消息
        if (chatMessageListener != null) {
            chatMessageListener.onSendChatMessage(message);
        } else if (engine != null && engine.getClient() != null) {
            engine.sendChat(message);
        }
        
        etChatInput.setText("");
    }
    
    /**
     * 进入大厅聊天 UI 模式（player waiting 界面）
     */
    public void enterLobbyChatUI() {
        if (etChatInput == null) return;

        // 重新绑定发送监听与点击唤起输入法监听，避免 UI 模式切换导致监听失效
        setupChatInput();

        etChatInput.setVisibility(View.VISIBLE);
        etChatInput.setFocusable(true);
        etChatInput.setFocusableInTouchMode(true);
        etChatInput.setClickable(true);
        etChatInput.clearFocus();

        // 进入大厅等待界面时不自动抢焦点、不自动弹出输入法：
        // 由玩家自行点击输入框触发 setupChatInput 里的点击监听唤起 IME

        // 启用输入法显隐看门狗：点输入框抬起键盘时通知外部把在显弹窗抬到输入框上方
        lobbyChatActive = true;
        lastImeVisible = false;
        attachImeWatcher();
    }

    /**
     * 挂载逐帧布局监听，按可视区高度差判定输入法显隐（Activity 为 adjustResize，
     * 键盘弹起时 getWindowVisibleDisplayFrame 底边上移）。仅在状态翻转时回调一次，去抖。
     * 大厅与决斗期间常驻：任何模式下抬高键盘都可能与在显弹窗互相遮挡。
     */
    private void attachImeWatcher() {
        if (etChatInput == null) return;
        final View root = etChatInput.getRootView();
        if (root == null) return;
        if (imeLayoutListener != null) {
            android.view.ViewTreeObserver vto = root.getViewTreeObserver();
            if (vto.isAlive()) vto.removeOnGlobalLayoutListener(imeLayoutListener);
        }
        imeLayoutListener = () -> {
            boolean visible = isImeVisible();
            if (visible != lastImeVisible) {
                lastImeVisible = visible;
                if (imeVisibilityListener != null) {
                    if (visible) imeVisibilityListener.onImeShown();
                    else imeVisibilityListener.onImeHidden();
                }
            }
        };
        root.getViewTreeObserver().addOnGlobalLayoutListener(imeLayoutListener);
    }

    /** 卸载输入法显隐看门狗（视图根随视图树销毁时不再触发，重绑经 {@link #bindChatInput} 重挂） */
    private void detachImeWatcher() {
        if (etChatInput == null) return;
        View root = etChatInput.getRootView();
        if (root == null) return;
        if (imeLayoutListener != null) {
            android.view.ViewTreeObserver vto = root.getViewTreeObserver();
            if (vto.isAlive()) vto.removeOnGlobalLayoutListener(imeLayoutListener);
        }
        imeLayoutListener = null;
    }

    /** 依据可视区高度差判定输入法是否可见：被键盘侵占高度超过屏高 15% 视为弹起 */
    private boolean isImeVisible() {
        if (etChatInput == null) return false;
        View root = etChatInput.getRootView();
        if (root == null) return false;
        int total = root.getHeight();
        if (total <= 0) return false;
        android.graphics.Rect r = new android.graphics.Rect();
        root.getWindowVisibleDisplayFrame(r);
        int imeHeight = total - r.bottom;
        return imeHeight > total * 0.15f;
    }
    
    /**
     * 退出大厅聊天 UI 模式
     */
    public void exitLobbyChatUI() {
        if (etChatInput == null) return;
        
        // 重新绑定发送监听与点击唤起输入法监听（确保状态正确）
        setupChatInput();

        // 退出大厅前若键盘仍弹起，补发一次“已隐藏”让外部把弹窗还原到区域中央
        if (lobbyChatActive && lastImeVisible && imeVisibilityListener != null) {
            imeVisibilityListener.onImeHidden();
        }
        lobbyChatActive = false;
        lastImeVisible = false;
        // 输入法看门狗不随退出大厅卸载：决斗期间的抬键盘同样要联动在显弹窗（用户规格）
        // 点击唤起输入法的监听也不随退出大厅清除：决斗中显示 dialog（是/否询问、宣言等）时
        // 触摸被弹窗层转发，默认点击链路同样唤不起键盘，必须常驻显式唤起（用户规格）

        // 隐藏输入法并释放焦点
        etChatInput.clearFocus();
        
        android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) 
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etChatInput.getWindowToken(), 0);
        }
    }
    
    /**
     * 切换聊天输入框的显示/隐藏状态
     * 停用状态 → 启用：显示聊天输入框
     * 启用状态 → 停用：隐藏聊天输入框并清空聊天消息
     */
    public void toggleChatInput(boolean enable, Runnable onClearChatMessages) {
        if (etChatInput == null) return;
        
        AppsSettings settings = AppsSettings.get();
        android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) 
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        
        if (enable) {
            // 启用聊天
            settings.saveIntSettings("chkDisableChatting", 0);
            isChatEnabled = true;
            etChatInput.clearFocus();
            etChatInput.setVisibility(View.VISIBLE);
        } else {
            // 停用聊天
            settings.saveIntSettings("chkDisableChatting", 1);
            isChatEnabled = false;
            if (imm != null && etChatInput.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(etChatInput.getWindowToken(), 0);
            }
            etChatInput.clearFocus();
            // 停用聊天隐藏输入框用 INVISIBLE 而非 GONE（用户规格）：竖屏底部聊天输入框
            // 为 weight 布局，GONE 会让左侧按钮组宽度跳变，INVISIBLE 保持占位稳定
            etChatInput.setVisibility(View.INVISIBLE);
            
            if (onClearChatMessages != null) {
                onClearChatMessages.run();
            }
        }
    }
    
    /**
     * 获取当前聊天是否启用状态
     */
    public boolean isChatEnabled() {
        return isChatEnabled;
    }
    
    /**
     * 获取聊天输入框文本
     */
    @Nullable
    public String getChatText() {
        return etChatInput != null ? etChatInput.getText().toString() : null;
    }
    
    /**
     * 设置聊天输入框文本
     */
    public void setChatText(CharSequence text) {
        if (etChatInput != null) {
            etChatInput.setText(text);
        }
    }
    
    /**
     * 清空聊天输入框内容
     */
    public void clearChatInput() {
        if (etChatInput != null) {
            etChatInput.setText("");
            etChatInput.clearFocus();
        }
    }
    
    /**
     * 获取聊天输入框视图
     */
    @Nullable
    public EditText getChatInputView() {
        return etChatInput;
    }
    
    /**
     * 设置聊天输入框可见性
     */
    public void setVisibility(int visibility) {
        if (etChatInput != null) {
            etChatInput.setVisibility(visibility);
        }
    }
    
    /**
     * 显示聊天输入框
     */
    public void show() {
        if (etChatInput != null) {
            etChatInput.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 隐藏聊天输入框（用 INVISIBLE 保持竖屏底部 weight 布局占位稳定，不塌陷）
     */
    public void hide() {
        if (etChatInput != null) {
            etChatInput.setVisibility(View.INVISIBLE);
        }
    }
    
    /**
     * 销毁资源
     */
    public void destroy() {
        lobbyChatActive = false;
        detachImeWatcher();
        etChatInput = null;
        chatMessageListener = null;
    }
}
package cn.garymb.ygomobile.game;

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
        setupChatInput();
    }
    
    /**
     * 设置聊天消息发送监听器
     */
    public void setOnChatMessageListener(@Nullable OnChatMessageListener listener) {
        this.chatMessageListener = listener;
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

        // 重新绑定发送监听，避免 UI 模式切换导致 onEditorActionListener 失效
        setupChatInput();

        etChatInput.setVisibility(View.VISIBLE);
        etChatInput.setFocusable(true);
        etChatInput.setFocusableInTouchMode(true);
        etChatInput.setClickable(true);
        etChatInput.clearFocus();

        final android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);

        // 大厅等待模式下 LanModeDialog(PopupWindow) 覆盖并持有窗口焦点，
        // 默认点击链路无法唤起输入法；在点击回调中 post 延迟显式请求焦点并 showSoftInput，
        // 以确保窗口焦点正确转移到 Activity 并触发 IME
        etChatInput.setOnClickListener(v -> v.post(() -> {
            try {
                if (v.requestFocus() && imm != null) {
                    imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            } catch (Exception e) {
                android.util.Log.e("ChatInputUI", "Lobby chat click IME error: " + e.getMessage(), e);
            }
        }));

        etChatInput.post(() -> {
            try {
                if (etChatInput.requestFocus() && imm != null) {
                    imm.showSoftInput(etChatInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            } catch (Exception e) {
                android.util.Log.e("ChatInputUI", "Enter Lobby Chat IME error: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 退出大厅聊天 UI 模式
     */
    public void exitLobbyChatUI() {
        if (etChatInput == null) return;
        
        // 重新绑定 editor action listener（确保状态正确）
        setupChatInput();

        // 清除大厅模式专用的点击唤起输入法监听，恢复决斗模式默认行为（禁止自动弹出输入法）
        etChatInput.setOnClickListener(null);
        
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
            etChatInput.setVisibility(View.GONE);
            
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
     * 隐藏聊天输入框
     */
    public void hide() {
        if (etChatInput != null) {
            etChatInput.setVisibility(View.GONE);
        }
    }
    
    /**
     * 销毁资源
     */
    public void destroy() {
        etChatInput = null;
        chatMessageListener = null;
    }
}
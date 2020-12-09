package com.ourygo.assistant.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

import com.ourygo.assistant.base.listener.OnClipChangedListener;

import java.io.IOException;

import cn.garymb.ygomobile.utils.FileLogUtil;

public class ClipManagement implements ClipboardManager.OnPrimaryClipChangedListener {

    public static final int ID_CLIP_LISTENER = -1;
    private static final ClipManagement ourInstance = new ClipManagement();
    private ClipboardManager clipboardManager;
    private OnClipChangedListener onClipChangedListener;

    private ClipManagement() {
    }

    public static ClipManagement getInstance() {
        return ourInstance;
    }

    public void setOnClipChangedListener(OnClipChangedListener onClipChangedListener) {
        this.onClipChangedListener = onClipChangedListener;
    }

    public void startClipboardListener(Context context) {
        if (clipboardManager == null)
            clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null)
            return;
        clipboardManager.addPrimaryClipChangedListener(this);
    }

    public void removeClipboardListener() {
        if (clipboardManager == null)
            return;
        clipboardManager.removePrimaryClipChangedListener(this);
    }

    //获取剪贴板内容
    public String getClipMessage() {
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null) {
            try {
                FileLogUtil.writeAndTime("剪贴板为空");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        CharSequence charSequence = clipData.getItemAt(0).getText();
        if (charSequence != null)
            return charSequence.toString();
        return null;
    }

    public void clear() {
        removeClipboardListener();
        onClipChangedListener = null;
    }

    @Override
    public void onPrimaryClipChanged() {
        final String clipMessage = getClipMessage();
        //如果复制的内容为空则不执行下面的代码
        if (TextUtils.isEmpty(clipMessage)) {
            return;
        }
        if (onClipChangedListener != null)
            onClipChangedListener.onClipChanged(clipMessage, false, ID_CLIP_LISTENER);
    }

    public void onPrimaryClipChanged(boolean isCheck, int id) {
        final String clipMessage = getClipMessage();
        //如果复制的内容为空则不执行下面的代码
        if (TextUtils.isEmpty(clipMessage)) {
            return;
        }
        if (onClipChangedListener != null)
            onClipChangedListener.onClipChanged(clipMessage, isCheck, id);
    }
}

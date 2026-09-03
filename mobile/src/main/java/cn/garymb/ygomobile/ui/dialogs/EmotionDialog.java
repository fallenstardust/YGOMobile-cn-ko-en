package cn.garymb.ygomobile.ui.dialogs;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupWindow;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.TextureLoader;

/**
 * 表情面板（对应桌面版 gframe 的 wEmoticon）：
 * 4x4 宫格表情按钮直接在 popup_window_emotion.xml 中声明（对齐 game.cpp L1336-1342 的宫格结构），
 * 本类仅负责运行时绑定按钮图片（TextureLoader.getEmoticon，与 emoticonCodes 顺序一致）与点击行为。
 * 点击表情（对齐 event_handler.cpp BUTTON_EMOTICON_0..15）：
 * 播放按钮音效 → 隐藏面板 → 将表情编码（如 "&laugh"）作为聊天消息经 CTOS_CHAT 发送；
 * 入口按钮为开关切换（对齐 BUTTON_EMOTICON），点击面板外或返回键自动关闭。
 */
public class EmotionDialog {

    /** 与 XML 中 4x4 宫格按钮一一对应，顺序对齐 image_manager.cpp emoticonCodes */
    private static final int[] EMOTE_BUTTON_IDS = {
            R.id.btn_emoticon_0, R.id.btn_emoticon_1, R.id.btn_emoticon_2, R.id.btn_emoticon_3,
            R.id.btn_emoticon_4, R.id.btn_emoticon_5, R.id.btn_emoticon_6, R.id.btn_emoticon_7,
            R.id.btn_emoticon_8, R.id.btn_emoticon_9, R.id.btn_emoticon_10, R.id.btn_emoticon_11,
            R.id.btn_emoticon_12, R.id.btn_emoticon_13, R.id.btn_emoticon_14, R.id.btn_emoticon_15
    };

    private final YGOProActivity activity;
    private final PopupWindow popupWindow;
    private final View contentView;

    public EmotionDialog(YGOProActivity activity) {
        this.activity = activity;
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_window_emotion, null);
        bindEmoticonButtons();

        popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
    }

    /** 为 XML 声明的 16 个宫格按钮绑定表情图片与点击行为（图片顺序对齐 emoticonCodes） */
    private void bindEmoticonButtons() {
        String[] codes = TextureLoader.EMOTICON_KEYS;
        for (int i = 0; i < EMOTE_BUTTON_IDS.length && i < codes.length; i++) {
            ImageButton btn = contentView.findViewById(EMOTE_BUTTON_IDS[i]);
            if (btn == null) continue;
            final String code = codes[i];
            btn.setImageBitmap(TextureLoader.get().getEmoticon(code));
            btn.setOnClickListener(v -> {
                SoundManager sm = activity.getSoundManager();
                if (sm != null) sm.playSoundEffect(SoundManager.SFX.BUTTON);
                dismiss();
                sendEmoticon(code);
            });
        }
    }

    /** 对齐 event_handler.cpp BUTTON_EMOTICON_0..15：表情编码以普通聊天消息发送 */
    private void sendEmoticon(String code) {
        GameEngine engine = activity.getEngine();
        if (engine != null && engine.getClient() != null) {
            engine.sendChat(code);
        }
    }

    /** 对齐 event_handler.cpp BUTTON_EMOTICON：已显示则隐藏，否则显示 */
    public void toggle(View anchor) {
        if (popupWindow.isShowing()) {
            dismiss();
        } else {
            show(anchor);
        }
    }

    /** 面板显示在入口按钮上方（底边对齐按钮顶边），越界时钳制到屏幕内 */
    public void show(View anchor) {
        if (anchor == null || anchor.getWindowToken() == null) return;
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }

        View decor = activity.getWindow().getDecorView();
        int screenW = decor.getWidth() > 0 ? decor.getWidth()
                : activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = decor.getHeight() > 0 ? decor.getHeight()
                : activity.getResources().getDisplayMetrics().heightPixels;

        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = contentView.getMeasuredWidth();
        int height = contentView.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int left = loc[0] + anchor.getWidth() / 2 - width / 2;
        int top = loc[1] - height;
        if (left + width > screenW) left = screenW - width;
        if (left < 0) left = 0;
        if (top < 0) top = loc[1] + anchor.getHeight();
        if (top + height > screenH) top = screenH - height;
        if (top < 0) top = 0;

        popupWindow.showAtLocation(decor, Gravity.TOP | Gravity.START, left, top);
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
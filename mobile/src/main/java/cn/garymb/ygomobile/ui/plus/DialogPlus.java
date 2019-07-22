package cn.garymb.ygomobile.ui.plus;


import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.widget.WebViewPlus;

import static android.view.WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW;

public class DialogPlus extends Dialog {
    public static final int TYPE_KEYGUARD = FIRST_SYSTEM_WINDOW + 4;
    private Context context;
    private LayoutInflater mLayoutInflater;
    private View mView;
    //
    private TextView mTitleView;
    private View closeView;
    private ViewGroup mFrameLayout;
    private Button mLeft;
    private Button mRight;
    private View mContentView;
    private int mMaxHeight, mMaxWidth;
    private String mUrl, mHtml;
    private View mCancelLayout, mButtonLayout, mTitleLayout;
    private View mProgressBar;
    public ProgressBar mProgressBar2;
    private WebViewPlus mWebView;
    private final GestureDetector mGestureDetector;
    private GestureDetector.OnGestureListener mOnGestureListener;

    public DialogPlus(Context context) {
        this(context, R.style.AppTheme_Dialog_Translucent);
    }

    public DialogPlus(Context context, int style) {
        super(context, style);
        this.context = context;
        this.mGestureDetector = new GestureDetector(context, new ViewGestureListener());
        mMaxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.7f);
        mMaxWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.8f);
        mLayoutInflater = LayoutInflater.from(context);
        mView = mLayoutInflater.inflate(R.layout.dialog_plus_base, null);
        super.setContentView(mView);
        mTitleView = $(R.id.title);
        closeView = $(R.id.close);
        mFrameLayout = $(R.id.container);
        mLeft = $(R.id.button_ok);
        mRight = $(R.id.button_cancel);
        mCancelLayout = $(R.id.layout_cancel);
        mButtonLayout = $(R.id.layout_button);
        mTitleLayout = $(R.id.layout_title);
        mProgressBar = $(R.id.pb1);
        mProgressBar2 = $(R.id.pb2);
        setOnCloseLinster((dlg) -> {
            dlg.dismiss();
        });
    }

    public void setOnGestureListener(GestureDetector.OnGestureListener onGestureListener) {
        mOnGestureListener = onGestureListener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }

    public DialogPlus hideButton() {
        if (mButtonLayout != null) {
            mButtonLayout.setVisibility(View.GONE);
        }
        return this;
    }

    //显示标题栏
    public DialogPlus showTitleBar() {
        if (mTitleLayout != null) {
            mTitleLayout.setVisibility(View.VISIBLE);
        }
        return this;
    }

    public DialogPlus hideTitleBar() {
        if (mTitleLayout != null) {
            mTitleLayout.setVisibility(View.GONE);
        }
        return this;
    }

    public DialogPlus setOnCloseLinster(OnCancelListener clickListener) {
        if (closeView != null) {
            closeView.setOnClickListener((v) -> {
                if (clickListener != null) {
                    clickListener.onCancel(this);
                } else {
                    dismiss();
                }
            });
        }
        return this;
    }

    public DialogPlus setRightButtonListener(DialogInterface.OnClickListener clickListener) {
        if (mRight != null) {
            mCancelLayout.setVisibility(View.VISIBLE);
            mRight.setVisibility(View.VISIBLE);
            mRight.setOnClickListener((v) -> {
                if (clickListener != null) {
                    clickListener.onClick(this, DialogInterface.BUTTON_NEUTRAL);
                } else {
                    dismiss();
                }
            });
        }
        return this;
    }

    public DialogPlus setLeftButtonListener(DialogInterface.OnClickListener clickListener) {
        if (mLeft != null) {
            mLeft.setVisibility(View.VISIBLE);
            mLeft.setOnClickListener((v) -> {
                if (clickListener != null) {
                    clickListener.onClick(this, DialogInterface.BUTTON_POSITIVE);
                } else {
                    dismiss();
                }
            });
        }
        return this;
    }

    public static DialogPlus show(Context context, CharSequence title, CharSequence message) {
        return show(context, title, message, false);
    }

    public static DialogPlus show(Context context, CharSequence title,
                                  CharSequence message,
                                  boolean cancelable) {
        return show(context, title, message, cancelable, null);
    }

    public static DialogPlus show(Context context, CharSequence title,
                                  CharSequence message,
                                  boolean cancelable, OnCancelListener cancelListener) {
        DialogPlus dialog = new DialogPlus(context);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        dialog.setOnCancelListener(cancelListener);
        dialog.hideButton();
        dialog.showProgressBar();
        dialog.showProgressBar2();
//        dialog.getWindow().setType(TYPE_KEYGUARD);
        dialog.show();
        return dialog;
    }

    @Override
    public void setCancelable(boolean flag) {
        super.setCancelable(flag);
        if (!flag) {
            closeView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void setTitle(int id) {
        setTitle(context.getString(id));
    }

    public DialogPlus showProgressBar() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
        return this;
    }

    public DialogPlus hideProgressBar() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }
        return this;
    }

    public DialogPlus showProgressBar2() {
        if (mProgressBar2 != null) {
            mProgressBar2.setVisibility(View.VISIBLE);
        }
        return this;
    }

    public ProgressBar getProgressBar2() {
        return mProgressBar2;
    }


    @Override
    public void setTitle(@Nullable CharSequence title) {
        setTitleText(title == null ? null : title.toString());
        if (TextUtils.isEmpty(title)) {
            hideTitleBar();
        }
    }

    public DialogPlus setMessage(int id) {
        return setMessage(context.getString(id));
    }

    public DialogPlus setMessageGravity(int g) {
        TextView textView = $(R.id.text);
        textView.setGravity(g);
        return this;
    }

    public DialogPlus setMessage(CharSequence text) {
        TextView textView = $(R.id.text);
        textView.setVisibility(View.VISIBLE);
        textView.setText(text);
        return this;
    }

    public DialogPlus setTitleText(String text) {
        if (mTitleView != null) {
            mTitleView.setText(text);
        }
        return this;
    }

    public DialogPlus setRightButtonText(int id) {
        return setRightButtonText(context.getString(id));
    }

    public DialogPlus setRightButtonText(String text) {
        if (mRight != null) {
            mCancelLayout.setVisibility(View.VISIBLE);
            mRight.setVisibility(View.VISIBLE);
            mRight.setText(text);
        }
        return this;
    }

    public DialogPlus setLeftButtonText(int id) {
        return setLeftButtonText(context.getString(id));
    }

    public DialogPlus setLeftButtonText(String text) {
        if (mLeft != null) {
            mLeft.setVisibility(View.VISIBLE);
            mLeft.setText(text);
        }
        return this;
    }

    public void setView(int id) {
        View view = mLayoutInflater.inflate(id, null);
        setView(view);
    }

    public DialogPlus setView(View view) {
        super.setContentView(view);
        mContentView = view;
        mView = null;
        mTitleView = null;
        closeView = null;
        mFrameLayout = null;
        mLeft = null;
        mRight = null;
        mCancelLayout = null;
        mButtonLayout = null;
        mTitleLayout = null;
        return this;
    }

    @Override
    public void setContentView(int id) {
        View view = mLayoutInflater.inflate(id, null);
        setContentView(view);
    }

    @Override
    public void setContentView(View view) {
        this.mContentView = view;
        if (mFrameLayout != null) {
            mFrameLayout.removeAllViews();
            mFrameLayout.addView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    public void show() {
        super.show();
        if (mWebView != null)
            if (!TextUtils.isEmpty(mUrl)) {
                mWebView.loadUrl(mUrl);
            } else if (!TextUtils.isEmpty(mHtml)) {
                mWebView.loadData(mHtml, "text/html", "UTF-8");
            }
    }

    private <T extends View> T $(int id) {
        return (T) mView.findViewById(id);
    }

    public <T extends View> T bind(int id) {
        return (T) mContentView.findViewById(id);
    }

    public View getContentView() {
        return mContentView;
    }

    public WebViewPlus getWebView() {
        return mWebView;
    }

    private WebViewPlus initWebView() {
        FrameLayout frameLayout = new FrameLayout(context);
        WebViewPlus webView = new WebViewPlus(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                mMaxWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        frameLayout.addView(webView, layoutParams);
        setContentView(frameLayout);
        setLeftButtonListener((dlg, v) -> {
            dlg.dismiss();
        });
        return webView;
    }

    public DialogPlus loadHtml(String html, int bgColor) {
        if (mWebView == null) {
            mWebView = initWebView();
            mWebView.setBackgroundColor(bgColor);
        }
        mHtml = html;
        return this;
    }

    public DialogPlus loadUrl(String url, int bgColor) {
        if (mWebView == null) {
            mWebView = initWebView();
            mWebView.setBackgroundColor(bgColor);
        }
        mUrl = url;
        return this;
    }

    private class ViewGestureListener implements GestureDetector.OnGestureListener {
        // 用户轻触触摸屏，由1个MotionEvent ACTION_DOWN触发
        @Override
        public boolean onDown(MotionEvent e) {
            if (mOnGestureListener != null) {
                return mOnGestureListener.onDown(e);
            }
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mOnGestureListener != null) {
                return mOnGestureListener.onFling(e1, e2, velocityX, velocityY);
            }
            return false;
        }

        @Override
        // 用户长按触摸屏，由多个MotionEvent ACTION_DOWN触发
        public void onLongPress(MotionEvent e) {
            if (mOnGestureListener != null) {
                mOnGestureListener.onLongPress(e);
            }
        }

        @Override
        // 用户按下触摸屏，并拖动，由1个MotionEvent ACTION_DOWN, 多个ACTION_MOVE触发
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mOnGestureListener != null) {
                return mOnGestureListener.onScroll(e1, e2, distanceX, distanceY);
            }
            return false;
        }

        @Override
        // 用户轻触触摸屏，尚未松开或拖动，由一个1个MotionEvent ACTION_DOWN触发
        // 注意和onDown()的区别，强调的是没有松开或者拖动的状态
        public void onShowPress(MotionEvent e) {
            if (mOnGestureListener != null) {
                mOnGestureListener.onShowPress(e);
            }
        }

        @Override
        // 用户（轻触触摸屏后）松开，由一个1个MotionEvent ACTION_UP触发
        public boolean onSingleTapUp(MotionEvent e) {
            if (mOnGestureListener != null) {
                return mOnGestureListener.onSingleTapUp(e);
            }
            return false;
        }
    }
}
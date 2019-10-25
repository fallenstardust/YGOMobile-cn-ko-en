/*
 * EditWindowCompat.java
 *
 *  Created on: 2014年3月15日
 *      Author: mabin
 */
package cn.garymb.ygomobile.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView.OnEditorActionListener;

import cn.garymb.ygomobile.lib.R;

/**
 * @author mabin
 *
 */
public class EditWindowCompat extends PopupWindow {
	
	private Context mContext;
	private ViewGroup mContentView;
	private EditText mEditText;
	private InputMethodManager mIM;
	
	public EditWindowCompat(Context context) {
		super(context);
		mContext = context;
		mContentView = (ViewGroup) LayoutInflater.from(mContext).inflate(
				R.layout.text_input_compat_layout, null);
		mEditText = (EditText) mContentView.findViewById(R.id.global_input);
		mIM = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
		setContentView(mContentView);
	}
	
	public void fillContent(String hint) {
		mEditText.setText(hint);
		this.setWidth(LayoutParams.MATCH_PARENT);
		this.setHeight(LayoutParams.WRAP_CONTENT);
		this.setFocusable(true);
		this.update();
	}
	
	/* (non-Javadoc)
	 * @see android.widget.PopupWindow#showAtLocation(android.view.View, int, int, int)
	 */
	@Override
	public void showAtLocation(View parent, int gravity, int x, int y) {
		// TODO Auto-generated method stub
		mEditText.requestFocus();
		mIM.showSoftInput(mEditText, 0);
		super.showAtLocation(parent, gravity, x, y);
	}
	
	/* (non-Javadoc)
	 * @see android.widget.PopupWindow#dismiss()
	 */
	@Override
	public void dismiss() {
		// TODO Auto-generated method stub
		mEditText.clearFocus();
		mIM.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
		super.dismiss();
	}
	
	public void setEditActionListener(OnEditorActionListener listener) {
		mEditText.setOnEditorActionListener(listener);
	}
}

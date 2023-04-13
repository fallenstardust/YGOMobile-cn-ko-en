/*
 * ComboBoxCompat.java
 *
 *  Created on: 2014年3月15日
 *      Author: mabin
 */
package cn.garymb.ygomobile.widget;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.ViewFlipper;

import cn.garymb.ygomobile.lib.R;
import cn.garymb.ygomobile.widget.wheelview.ArrayWheelAdapter;
import cn.garymb.ygomobile.widget.wheelview.WheelView;

public class ComboBoxCompat extends PopupWindow {
	/*change this will affect C++ code, be careful!*/
	public static final int COMPAT_GUI_MODE_COMBOBOX = 0;

	/*change this will affect C++ code, be careful!*/
	public static final int COMPAT_GUI_MODE_CHECKBOXES_PANEL = 1;
	private final Context mContext;
	private final WheelView mComboBoxContent;
	private final Button mSubmitButton;
	private final Button mCancelButton;
	private final ViewFlipper mFlipper;
	private ArrayWheelAdapter<String> mAdapter;

	public ComboBoxCompat(Context context) {
		// TODO Auto-generated constructor stub
		super(context);
		mContext = context;
		View menuView = LayoutInflater.from(mContext).inflate(R.layout.combobox_compat_layout, null);
		mComboBoxContent = menuView.findViewById(R.id.combobox_content);
		mSubmitButton = menuView.findViewById(R.id.submit);
		mCancelButton = menuView.findViewById(R.id.cancel);

		mFlipper = new ViewFlipper(context);
		mFlipper.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT));
		mFlipper.addView(menuView);
		mFlipper.setFlipInterval(6000000);
		setContentView(mFlipper);
	}

	public void setButtonListener(OnClickListener listener) {
		mSubmitButton.setOnClickListener(listener);
		mCancelButton.setOnClickListener(listener);
	}

	public void fillContent(String[] items) {
		mAdapter = new ArrayWheelAdapter<String>(mContext, items);
		mComboBoxContent.setViewAdapter(mAdapter);
		this.setWidth(LayoutParams.MATCH_PARENT);
		this.setHeight(LayoutParams.WRAP_CONTENT);
		this.setFocusable(true);
		ColorDrawable dw = new ColorDrawable(0x00000000);
		this.setBackgroundDrawable(dw);
		this.update();
	}

	public int getCurrentSelection() {
		return mComboBoxContent.getCurrentItem();
	}

	@Override
	public void showAtLocation(View parent, int gravity, int x, int y) {
		super.showAtLocation(parent, gravity, x, y);
		mFlipper.startFlipping();
	}

	@Override
	public void dismiss() {
		// TODO Auto-generated method stub
		super.dismiss();
	}
}

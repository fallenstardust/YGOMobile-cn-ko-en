package cn.garymb.ygomobile.widget.overlay;

import android.content.Context;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ToggleButton;

import cn.garymb.ygomobile.lib.R;
import cn.garymb.ygomobile.widget.overlay.OverlayOvalView.OnDuelOptionsSelectListener;

public class OverlayRectView extends OverlayView implements OnCheckedChangeListener {
	
	private ToggleButton mReactButton;
	private ToggleButton mIgnoreButton;
	
	private OnDuelOptionsSelectListener mListener;
	
	public OverlayRectView(Context context) {
		super(context, R.layout.overlay_rect);
	}
	
	@Override
	protected void onInflateView() {
		super.onInflateView();
		mIgnoreButton= (ToggleButton) findViewById(R.id.overlay_ignore);
		mIgnoreButton.setOnCheckedChangeListener(this);
		mReactButton = (ToggleButton) findViewById(R.id.overlay_react);
		mReactButton.setOnCheckedChangeListener(this);
	}
	
	public void setDuelOpsListener(OnDuelOptionsSelectListener listener) {
		mListener = listener;
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (buttonView.equals(mIgnoreButton)) {
			if (isChecked && mReactButton.isChecked()) {
				mReactButton.setChecked(false);
				mListener.onDuelOptionsSelected(MODE_REACT_CHAIN_OPTION, false);
			}
			mListener.onDuelOptionsSelected(MODE_IGNORE_CHAIN_OPTION, isChecked);
		} else {
			if (isChecked && mIgnoreButton.isChecked()) {
				mIgnoreButton.setChecked(false);
				mListener.onDuelOptionsSelected(MODE_IGNORE_CHAIN_OPTION, false);
			}
			mListener.onDuelOptionsSelected(MODE_REACT_CHAIN_OPTION, isChecked);
		}		
	}

}

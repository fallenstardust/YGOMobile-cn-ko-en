package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.garymb.ygomobile.network.LanDiscoveryManager;

public class HostListAdapter extends BaseAdapterPlus<LanDiscoveryManager.HostEntry> {

    private int selectedPosition = -1;

    public HostListAdapter(Context context) {
        super(context);
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    protected View createView(int position, ViewGroup parent) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(16, 12, 16, 12);

        TextView tvMain = new TextView(context);
        tvMain.setId(android.R.id.text1);
        tvMain.setTextColor(Color.WHITE);
        tvMain.setTextSize(13f);
        layout.addView(tvMain, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvDetail = new TextView(context);
        tvDetail.setId(android.R.id.text2);
        tvDetail.setTextColor(0xAAFFFFFF);
        tvDetail.setTextSize(11f);
        tvDetail.setPadding(0, 4, 0, 0);
        layout.addView(tvDetail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return layout;
    }

    @Override
    protected void attach(View view, LanDiscoveryManager.HostEntry item, int position) {
        LinearLayout layout = (LinearLayout) view;
        TextView tvMain = layout.findViewById(android.R.id.text1);
        TextView tvDetail = layout.findViewById(android.R.id.text2);

        if (item != null) {
            tvMain.setText(item.getDisplayText());
            tvDetail.setText(item.getDetailText());
        }

        if (position == selectedPosition) {
            view.setBackgroundColor(0x5587CEEB);
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
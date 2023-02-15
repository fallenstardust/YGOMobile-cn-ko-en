package cn.garymb.ygomobile.ex_card;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class ExCardLogAdapter extends BaseExpandableListAdapter {

    public ExCardLogAdapter(Context context) {
        this.context = context;
    }

    private Context context;
    private List<ExCardLogItem> expandalbeList = new ArrayList<>();


    public void setData(List<ExCardLogItem> expandalbeList) {
        this.expandalbeList = expandalbeList;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return this.expandalbeList.get(groupPosition).getCount();
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return expandalbeList.get(groupPosition).getLogs().get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        Object result = getChild(groupPosition, childPosition);
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.item_log, null);
        }

        TextView expandedListTextView = (TextView) convertView
                .findViewById(R.id.expandedListItem);
        expandedListTextView.setText(expandalbeList.get(groupPosition).getLogs().get(childPosition));
        return convertView;

    }

    @Override
    public int getGroupCount() {
        return this.expandalbeList.size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return this.expandalbeList.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.item_log_group, null);
        }
        TextView listTitleTextView = (TextView) convertView
                .findViewById(R.id.listTitle);

        listTitleTextView.setText(expandalbeList.get(groupPosition).getDateTime());
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }


    @Override
    public boolean hasStableIds() {
        return false;
    }
}

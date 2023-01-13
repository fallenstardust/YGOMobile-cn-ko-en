package cn.garymb.ygomobile.ex_card;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class ExCardLogFragment extends Fragment {


    private View layoutView;
    private ExCardLogAdapter mExCardLogAdapter;
    private ExpandableListView mExCardLogView;
    private List<ExCardLogItem> dataList = new ArrayList<>();//TODO 把数据传入


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        layoutView = inflater.inflate(R.layout.fragment_ex_card_log, container, false);
        initView(layoutView);
        return layoutView;
    }

    public void initView(View layoutView) {
        mExCardLogView = layoutView.findViewById(R.id.expandableListView);
        mExCardLogAdapter = new ExCardLogAdapter(getContext(), dataList);
        mExCardLogView.setAdapter(mExCardLogAdapter);
        initButton();
    }

    public void initButton() {
    }
}
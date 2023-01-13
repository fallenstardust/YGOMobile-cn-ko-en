package cn.garymb.ygomobile.ex_card;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ExCardFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ExCardFragment extends Fragment {


    private View layoutView;
    private ExCardListAdapter mExCardListAdapter;
    private RecyclerView mExCardListView;
    private List<ExCard> dataList = new ArrayList<>();

    public void setDataList(){

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        layoutView = inflater.inflate(R.layout.fragment_ex_card, container, false);
        initView(layoutView);
        Log.i("webCrawler", "excardfragment onCreateView");
        return layoutView;
    }

    public void initView(View layoutView) {
        mExCardListView = layoutView.findViewById(R.id.list_ex_card);
        mExCardListAdapter = new ExCardListAdapter(R.layout.item_ex_card, dataList);
        mExCardListView.setAdapter(mExCardListAdapter);
        initButton();
    }

    public void initButton() {
    }
}
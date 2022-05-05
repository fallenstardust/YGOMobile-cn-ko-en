package com.ourygo.ygomobile.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.ourygo.ygomobile.adapter.LocalBQAdapter;
import com.ourygo.ygomobile.bean.LocalDuel;
import com.ourygo.ygomobile.util.OYUtil;
import cn.garymb.ygomobile.utils.StatUtil;
import com.ourygo.ygomobile.util.YGOUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cn.garymb.ygomobile.lite.R;

public class LocalDuelFragment extends Fragment {

    private static final int TYPE_AI_DUEL=0;
    private static final int TYPE_SINGLE=1;

    private RecyclerView rv_local_list;

    private LocalBQAdapter localBQAdapter;
    private List<LocalDuel> localDuelList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v=inflater.inflate(R.layout.local_duel_fragment,null);
        
        initView(v);
        
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        StatUtil.onResume(getClass().getName());
    }

    @Override
    public void onPause() {
        super.onPause();
        StatUtil.onPause(getClass().getName());
    }

    private void initView(View v) {
        rv_local_list=v.findViewById(R.id.rv_local_list);
        localDuelList=new ArrayList<>();

        localDuelList.add(LocalDuel.toLocalDuel(TYPE_AI_DUEL,OYUtil.s(R.string.ai_duel),OYUtil.s(R.string.ai_duel_message)));
        localDuelList.add(LocalDuel.toLocalDuel(TYPE_SINGLE,OYUtil.s(R.string.single),OYUtil.s(R.string.single_message)));

        rv_local_list.setLayoutManager(new LinearLayoutManager(getActivity()));
        localBQAdapter=new LocalBQAdapter(localDuelList);
        rv_local_list.setAdapter(localBQAdapter);

        localBQAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                LocalDuel localDuel= (LocalDuel) adapter.getItem(position);
                switch (localDuel.getId()){
                    case TYPE_AI_DUEL:
                        YGOUtil.joinGame(getActivity());
                        break;
                    case TYPE_SINGLE:
                        YGOUtil.joinGame(getActivity());
                        break;
                }
            }
        });

    }
}

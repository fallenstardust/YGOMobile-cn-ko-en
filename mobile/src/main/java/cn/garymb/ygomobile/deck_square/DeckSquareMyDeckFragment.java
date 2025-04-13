package cn.garymb.ygomobile.deck_square;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import cn.garymb.ygomobile.deck_square.api_response.ApiDeckRecord;
import cn.garymb.ygomobile.deck_square.api_response.DeckDetail;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSquareMyDeckBinding;

//打开页面后，先扫描本地的卡组，读取其是否包含deckId，是的话代表平台上可能有
//之后读取平台上的卡组，与本地卡组列表做比较。

public class DeckSquareMyDeckFragment extends Fragment {

    private FragmentDeckSquareMyDeckBinding binding;
    private MyDeckListAdapter deckListAdapter;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentDeckSquareMyDeckBinding.inflate(inflater, container, false);

        deckListAdapter = new MyDeckListAdapter(R.layout.item_my_deck);
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getContext(), 2);
        binding.listMyDeckInfo.setLayoutManager(linearLayoutManager);
        binding.listMyDeckInfo.setAdapter(deckListAdapter);
        deckListAdapter.loadData();


        binding.refreshData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deckListAdapter.loadData();
            }
        });


        deckListAdapter.setOnItemClickListener(
                (adapter, view, position) -> {
                    // Handle item click
                    MyDeckItem item = (MyDeckItem) adapter.getItem(position);

                    MyDeckDetailDialog dialog = new MyDeckDetailDialog(getContext(), item);

                    dialog.show();
                }
        );

        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}

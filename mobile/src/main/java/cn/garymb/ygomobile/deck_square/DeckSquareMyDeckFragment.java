package cn.garymb.ygomobile.deck_square;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import cn.garymb.ygomobile.deck_square.api_response.ApiDeckRecord;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentUserOnlineDeckBinding;

public class DeckSquareMyDeckFragment extends Fragment {

    private FragmentUserOnlineDeckBinding binding;
    private MyDeckListAdapter deckListAdapter;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentUserOnlineDeckBinding.inflate(inflater, container, false);

        deckListAdapter = new MyDeckListAdapter(R.layout.item_deck_info);
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

        binding.uploadDeck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //todo 打开一个dialog

            }
        });

        deckListAdapter.setOnItemClickListener(
                (adapter, view, position) -> {
                    // Handle item click
                    ApiDeckRecord item = (ApiDeckRecord) adapter.getItem(position);


                    // Show the dialog
                    //todo 询问是否删除？
                    //   SquareDeckDetailDialog dialog = new SquareDeckDetailDialog(getContext(), item);

                    // dialog.show();
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

package cn.garymb.ygomobile.deck_square;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import cn.garymb.ygomobile.deck_square.api_response.OnlineDeckDetail;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSquareBinding;

public class DeckSquareFragment extends Fragment {

    private FragmentDeckSquareBinding binding;
    private DeckSquareListAdapter deckSquareListAdapter;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentDeckSquareBinding.inflate(inflater, container, false);

        deckSquareListAdapter = new DeckSquareListAdapter(R.layout.item_deck_info);
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getContext(), 2);
        binding.listDeckInfo.setLayoutManager(linearLayoutManager);
        binding.listDeckInfo.setAdapter(deckSquareListAdapter);
        deckSquareListAdapter.loadData();


// Set click listener in your adapter
        deckSquareListAdapter.setOnItemClickListener((adapter, view, position) -> {
            // Handle item click
            OnlineDeckDetail item = (OnlineDeckDetail) adapter.getItem(position);


            // Show the dialog
            SquareDeckDetailDialog dialog = new SquareDeckDetailDialog(getContext(), item);

            dialog.show();
        });
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
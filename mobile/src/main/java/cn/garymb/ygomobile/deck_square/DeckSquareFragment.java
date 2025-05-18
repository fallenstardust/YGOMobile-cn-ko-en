package cn.garymb.ygomobile.deck_square;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.deck_square.api_response.OnlineDeckDetail;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSquareBinding;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;

public class DeckSquareFragment extends Fragment {

    private FragmentDeckSquareBinding binding;
    private DeckSquareListAdapter deckSquareListAdapter;
    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;

    public DeckSquareFragment(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

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
// 设置页码跳转监听
        binding.etGoToPage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int targetPage = 0;
                try {
                    targetPage = Integer.parseInt(v.getText().toString());
                } catch (NumberFormatException e) {

                }


                binding.tvPageInfo.setText(Integer.toString(targetPage));
                deckSquareListAdapter.loadData(targetPage, 30);
                return true;
            }
            return false;
        });
        binding.nextPageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int targetPage = 0;
                try {
                    targetPage = Integer.parseInt(binding.tvPageInfo.getText().toString());
                } catch (NumberFormatException e) {

                }
                int newPage = targetPage + 1;

                deckSquareListAdapter.loadData(newPage, 30);
                binding.tvPageInfo.setText(Integer.toString(newPage));

            }
        });
        binding.formerPageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int targetPage = 0;
                try {
                    targetPage = Integer.parseInt(binding.tvPageInfo.getText().toString());
                } catch (NumberFormatException e) {

                }
                int newPage = targetPage - 1;
                if (newPage < 1) {
                    newPage = 1;
                }
                deckSquareListAdapter.loadData(newPage, 30);
                binding.tvPageInfo.setText(Integer.toString(newPage));


            }
        });
        binding.refreshData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int targetPage = 1;
                try {
                    targetPage = Integer.parseInt(binding.tvPageInfo.getText().toString());
                } catch (NumberFormatException e) {

                }
                deckSquareListAdapter.loadData(targetPage, 30);
            }
        });
        deckSquareListAdapter.setOnItemLongClickListener((adapter, view, position) -> {

            OnlineDeckDetail item = (OnlineDeckDetail) adapter.getItem(position);

            // Show the dialog
            SquareDeckDetailDialog dialog = new SquareDeckDetailDialog(getContext(), item);

            dialog.show();
            return true;
        });
// Set click listener in your adapter
        deckSquareListAdapter.setOnItemClickListener((adapter, view, position) -> {
            OnlineDeckDetail item = (OnlineDeckDetail) adapter.getItem(position);
            //调用
            mDialogListener.onDismiss();
            DeckFile deckFile = new DeckFile(item.getDeckId(), DeckType.ServerType.SQUARE_DECK);
            onDeckMenuListener.onDeckSelect(deckFile);


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
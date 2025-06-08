package cn.garymb.ygomobile.deck_square;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private String keyWord;
    private Boolean sortLike;
    private Boolean sortRank;
    private String contributer;

    public DeckSquareFragment(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDeckSquareBinding.inflate(inflater, container, false);

        deckSquareListAdapter = new DeckSquareListAdapter(R.layout.item_deck_info);
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getContext(), 3);
        binding.listDeckInfo.setLayoutManager(linearLayoutManager);
        binding.listDeckInfo.setAdapter(deckSquareListAdapter);
        deckSquareListAdapter.loadData();
        binding.etGoToPage.setText("1");
        binding.etDeckSquareInputDeckName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Editable contributerName = binding.etInputContributerName.getText();
                if (contributerName != null) contributerName.clear();
                keyWord = v.getText().toString();
                binding.etGoToPage.setText("1");
                binding.etGoToPage.setEnabled(false);
                // 底部按钮不可用状态
                binding.formerPageBtn.setEnabled(false);
                binding.formerPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                binding.nextPageBtn.setEnabled(false);
                binding.nextPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                binding.refreshData.setEnabled(false);
                binding.refreshData.setColorFilter(R.color.navigator_dir_text_color);
                deckSquareListAdapter.loadData(1, 1000, keyWord, true, false, "");
                binding.listDeckInfo.scrollToPosition(0);
                return true;
            }
            return false;
        });
        // 添加文本变化监听器
        binding.etDeckSquareInputDeckName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClearDeckName.setVisibility(View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 当输入框内容为空时
                if (s.toString().isEmpty()) {
                    binding.btnClearDeckName.setVisibility(View.GONE);
                    // 恢复底部按钮可用状态
                    binding.formerPageBtn.setEnabled(true);
                    binding.formerPageBtn.setColorFilter(R.color.selector_text_color_white_gold);
                    binding.nextPageBtn.setEnabled(true);
                    binding.nextPageBtn.setColorFilter(R.color.selector_text_color_white_gold);
                    binding.refreshData.setEnabled(true);
                    binding.refreshData.setColorFilter(R.color.selector_text_color_white_gold);
                    // 重置页码为1
                    binding.etGoToPage.setText("1");
                    binding.etGoToPage.setEnabled(true);
                    deckSquareListAdapter.loadData();
                    binding.listDeckInfo.scrollToPosition(0);
                }
            }
        });
        binding.etInputContributerName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Editable deckName = binding.etDeckSquareInputDeckName.getText();
                if (deckName != null) deckName.clear();
                contributer = v.getText().toString();
                binding.etGoToPage.setText("1");
                binding.etGoToPage.setEnabled(false);
                // 底部按钮不可用状态
                binding.formerPageBtn.setEnabled(false);
                binding.formerPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                binding.nextPageBtn.setEnabled(false);
                binding.nextPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                binding.refreshData.setEnabled(false);
                binding.refreshData.setColorFilter(R.color.navigator_dir_text_color);
                deckSquareListAdapter.loadData(1, 1000, null, true, false, contributer);
                binding.listDeckInfo.scrollToPosition(0);
                return true;
            }
            return false;
        });
        // 添加文本变化监听器
        binding.etInputContributerName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClearContributerName.setVisibility(View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 当输入框内容为空时
                if (s.toString().isEmpty()) {
                    binding.btnClearDeckName.setVisibility(View.GONE);
                    // 恢复底部按钮可用状态
                    binding.formerPageBtn.setEnabled(true);
                    binding.formerPageBtn.setColorFilter(R.color.selector_text_color_white_gold);
                    binding.nextPageBtn.setEnabled(true);
                    binding.nextPageBtn.setColorFilter(R.color.selector_text_color_white_gold);
                    binding.refreshData.setEnabled(true);
                    binding.refreshData.setColorFilter(R.color.selector_text_color_white_gold);
                    // 重置页码为1
                    binding.etGoToPage.setText("1");
                    binding.etGoToPage.setEnabled(true);
                    deckSquareListAdapter.loadData();
                    binding.listDeckInfo.scrollToPosition(0);
                }
            }
        });
        //设置清空按钮点击清除输入内容
        binding.btnClearDeckName.setOnClickListener(view -> binding.etDeckSquareInputDeckName.getText().clear());
        binding.btnClearContributerName.setOnClickListener(view -> binding.etInputContributerName.getText().clear());
        // 设置页码跳转监听
        binding.etGoToPage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int targetPage = 0;
                try {
                    targetPage = Integer.parseInt(v.getText().toString());
                } catch (NumberFormatException e) {

                }

                binding.etGoToPage.setText(Integer.toString(targetPage));
                deckSquareListAdapter.loadData(targetPage, 30, "", false, false, "");
                binding.listDeckInfo.scrollToPosition(0);
                return true;
            }
            return false;
        });
        binding.nextPageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int targetPage = 0;
                try {
                    targetPage = Integer.parseInt(binding.etGoToPage.getText().toString());
                } catch (NumberFormatException e) {

                }
                int newPage = targetPage + 1;
                deckSquareListAdapter.loadData(newPage, 30, "", false, false, "");
                binding.etGoToPage.setText(Integer.toString(newPage));
                binding.listDeckInfo.scrollToPosition(0);

            }
        });
        binding.formerPageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int targetPage = 0;
                try {
                    targetPage = Integer.parseInt(binding.etGoToPage.getText().toString());
                } catch (NumberFormatException e) {

                }
                int newPage = targetPage - 1;
                if (newPage < 1) {
                    newPage = 1;
                }
                deckSquareListAdapter.loadData(newPage, 30, "", false, false, "");
                binding.etGoToPage.setText(Integer.toString(newPage));
                binding.listDeckInfo.scrollToPosition(0);

            }
        });
        binding.refreshData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int targetPage = 1;
                try {
                    targetPage = Integer.parseInt(binding.etGoToPage.getText().toString());
                } catch (NumberFormatException e) {

                }
                deckSquareListAdapter.loadData(targetPage, 30, "", false, false, "");
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
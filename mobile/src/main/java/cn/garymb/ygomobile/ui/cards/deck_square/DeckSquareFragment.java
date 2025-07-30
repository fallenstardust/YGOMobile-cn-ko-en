package cn.garymb.ygomobile.ui.cards.deck_square;

import android.graphics.drawable.Drawable;
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
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.OnlineDeckDetail;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSquareBinding;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;

public class DeckSquareFragment extends Fragment {
    private static final String TAG = DeckSquareFragment.class.getSimpleName();
    private FragmentDeckSquareBinding binding;
    private DeckSquareListAdapter deckSquareListAdapter;
    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;
    private String keyWord;
    private Boolean sortLike;
    private Boolean sortRank;
    private String contributor;

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
        sortLike = false;
        Drawable icon_like = getContext().getDrawable(R.drawable.ic_recommendation_order);
        Drawable icon_new = getContext().getDrawable(R.drawable.upload_time);
        binding.refreshData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //根据按钮使用的图标决定是否是按赞排序还是按上传时间排序, 切换成另一种图标
                if (sortLike == false) {
                    sortLike = true;
                    binding.tvSortMode.setText(R.string.sort_by_thumb);//当按时间顺序时，点击应切换为按点赞顺序
                    binding.refreshData.setImageDrawable(icon_new);//因为点击后会变成按点赞顺序，就需要图标显示为按时间顺序，告诉用户点它可变回时间顺序
                } else {
                    sortLike = false;
                    binding.tvSortMode.setText(R.string.sort_by_time);//当按点赞顺序时，点击应切换为按时间顺序
                    binding.refreshData.setImageDrawable(icon_like);//因为点击后会变成按时间顺序，就需要图标切换为按点赞顺序，告诉用户点它可变成点赞顺序
                }
                int targetPage = 1;
                try {
                    targetPage = Integer.parseInt(binding.etGoToPage.getText().toString());
                } catch (NumberFormatException e) {

                }
                deckSquareListAdapter.loadData(targetPage, 30, "", sortLike, false, "");
            }
        });
        // 设置页码跳转监听
        binding.etGoToPage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int targetPage = 0;
                try {
                    targetPage = Integer.parseInt(v.getText().toString());
                } catch (NumberFormatException e) {

                }

                binding.etGoToPage.setText(Integer.toString(targetPage));
                deckSquareListAdapter.loadData(targetPage, 30, "", sortLike, false, "");
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
                deckSquareListAdapter.loadData(newPage, 30, "", sortLike, false, "");
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
                deckSquareListAdapter.loadData(newPage, 30, "", sortLike, false, "");
                binding.etGoToPage.setText(Integer.toString(newPage));
                binding.listDeckInfo.scrollToPosition(0);

            }
        });
        //查询卡组名称
        binding.etDeckSquareInputDeckName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Editable contributorName = binding.etInputContributorName.getText();
                if (contributorName != null) contributorName.clear();
                keyWord = v.getText().toString();
                binding.etGoToPage.setText("1");
                binding.etGoToPage.setEnabled(false);
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
                if (s.toString().isEmpty())
                    binding.btnClearDeckName.setVisibility(View.GONE);
                else
                    binding.btnClearDeckName.setVisibility(View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 当输入框内容为空时
                if (s.toString().isEmpty()) {
                    binding.btnClearDeckName.setVisibility(View.GONE);
                    // 恢复底部按钮可用状态
                    binding.formerPageBtn.setEnabled(true);
                    binding.formerPageBtn.clearColorFilter();
                    binding.nextPageBtn.setEnabled(true);
                    binding.nextPageBtn.clearColorFilter();
                    binding.refreshData.setEnabled(true);
                    binding.refreshData.clearColorFilter();
                    // 重置页码为1
                    binding.etGoToPage.setText("1");
                    binding.etGoToPage.setEnabled(true);
                    deckSquareListAdapter.loadData();
                    binding.listDeckInfo.scrollToPosition(0);
                } else {
                    // 底部按钮不可用状态
                    binding.formerPageBtn.setEnabled(false);
                    binding.formerPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                    binding.nextPageBtn.setEnabled(false);
                    binding.nextPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                    binding.refreshData.setEnabled(false);
                    binding.refreshData.setColorFilter(R.color.navigator_dir_text_color);
                }
            }
        });
        //添加贡献者查询
        binding.etInputContributorName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                //清除卡组名称输入的内容
                Editable deckName = binding.etDeckSquareInputDeckName.getText();
                if (deckName != null) deckName.clear();
                //获取输入内容
                contributor = v.getText().toString();
                binding.etGoToPage.setText("1");
                binding.etGoToPage.setEnabled(false);

                deckSquareListAdapter.loadData(1, 1000, "", true, false, contributor);
                binding.listDeckInfo.scrollToPosition(0);
                return true;
            }
            return false;
        });
        // 添加文本变化监听器
        binding.etInputContributorName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().isEmpty()) {
                    binding.btnClearContributorName.setVisibility(View.GONE);
                } else {
                    binding.btnClearContributorName.setVisibility(View.VISIBLE);
                }

            }

            @Override
            public void afterTextChanged(Editable s) {
                // 当输入框内容为空时
                if (s.toString().isEmpty()) {
                    binding.btnClearDeckName.setVisibility(View.GONE);
                    // 恢复底部按钮可用状态
                    binding.formerPageBtn.setEnabled(true);
                    binding.formerPageBtn.clearColorFilter();
                    binding.nextPageBtn.setEnabled(true);
                    binding.nextPageBtn.clearColorFilter();
                    binding.refreshData.setEnabled(true);
                    binding.refreshData.clearColorFilter();
                    // 重置页码为1
                    binding.etGoToPage.setText("1");
                    binding.etGoToPage.setEnabled(true);
                    deckSquareListAdapter.loadData();
                    binding.listDeckInfo.scrollToPosition(0);
                } else {
                    // 底部按钮不可用状态
                    binding.formerPageBtn.setEnabled(false);
                    binding.formerPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                    binding.nextPageBtn.setEnabled(false);
                    binding.nextPageBtn.setColorFilter(R.color.navigator_dir_text_color);
                    binding.refreshData.setEnabled(false);
                    binding.refreshData.setColorFilter(R.color.navigator_dir_text_color);

                }
            }
        });
        //设置清空按钮点击清除输入内容
        binding.btnClearDeckName.setOnClickListener(view -> binding.etDeckSquareInputDeckName.getText().clear());
        binding.btnClearContributorName.setOnClickListener(view -> binding.etInputContributorName.getText().clear());
        // Set click listener in your adapter
        deckSquareListAdapter.setOnItemClickListener((adapter, view, position) -> {
            OnlineDeckDetail item = (OnlineDeckDetail) adapter.getItem(position);
            LogUtil.v("seesee", item.toString());
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
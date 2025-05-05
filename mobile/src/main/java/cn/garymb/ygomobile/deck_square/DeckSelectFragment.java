package cn.garymb.ygomobile.deck_square;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;

import cn.garymb.ygomobile.lite.databinding.FragmentDeckSelectBinding;

//卡组选择的Fragment，选择后在卡组编辑页面中显示卡片
public class DeckSelectFragment extends Fragment {

    private FragmentDeckSelectBinding binding;

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDeckSelectBinding.inflate(inflater, container, false);

        binding.rvDeck.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        binding.rvType.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        binding.rvResultList.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));


        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


}

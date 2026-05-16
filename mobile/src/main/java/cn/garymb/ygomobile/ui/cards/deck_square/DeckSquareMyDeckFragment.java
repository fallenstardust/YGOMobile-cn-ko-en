package cn.garymb.ygomobile.ui.cards.deck_square;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSquareMyDeckBinding;
import cn.garymb.ygomobile.ui.mycard.MyCardSso;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

//打开页面后，先扫描本地的卡组，读取其是否包含deckId，是的话代表平台上可能有
//之后读取平台上的卡组，与本地卡组列表做比较。

public class DeckSquareMyDeckFragment extends Fragment {
    private FragmentDeckSquareMyDeckBinding binding;
    private MyDeckListAdapter deckListAdapter;
    private String keyWord;
    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;

    public DeckSquareMyDeckFragment(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDeckSquareMyDeckBinding.inflate(inflater, container, false);

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnRegister.setOnClickListener(v -> MyCardSso.openSignup(getActivity()));
        deckListAdapter = new MyDeckListAdapter(R.layout.item_my_deck, onDeckMenuListener, mDialogListener);
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getContext(), 3);
        binding.listMyDeckInfo.setLayoutManager(linearLayoutManager);
        binding.listMyDeckInfo.setAdapter(deckListAdapter);
        updateLoginState();

        //其实仅仅是清除掉本机的token
        binding.llMcLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogPlus dialogPlus = new DialogPlus(getContext());
                dialogPlus.setMessage(R.string.logout_mycard);
                dialogPlus.setMessageGravity(Gravity.CENTER);
                dialogPlus.setLeftButtonListener((dlg, s) -> {
                    SharedPreferenceUtil.deleteServerToken();
                    SharedPreferenceUtil.setServerUserId(0);
                    SharedPreferenceUtil.setMyCardUserName("");
                    binding.llMainUi.setVisibility(View.GONE);
                    binding.llDialogLogin.setVisibility(View.VISIBLE);
                    dialogPlus.dismiss();
                });
                dialogPlus.setRightButtonListener((dlg, s) -> {
                    dialogPlus.dismiss();
                });
                dialogPlus.show();
            }
        });
        //查询卡组名称
        binding.etMyDeckInputDeckName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                keyWord = v.getText().toString();
                //直接过滤列表中的item名称，而不是像卡组广场那样重新查询
                deckListAdapter.filter(keyWord);

                return true;
            }
            return false;
        });
        // 添加文本变化监听器
        binding.etMyDeckInputDeckName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                deckListAdapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 当输入框内容为空时
                if (s.toString().isEmpty()) {
                    deckListAdapter.loadData();//悄悄留一个刷新我的云备份的方式，方便
                } else {
                    deckListAdapter.filter(s.toString());
                }
            }
        });

        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onResume() {
        super.onResume();
        updateLoginState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void attemptLogin() {
        MyCardSso.openLogin(getActivity());
    }

    private void updateLoginState() {
        if (binding == null) {
            return;
        }
        if (SharedPreferenceUtil.getServerToken() == null) {
            binding.llMainUi.setVisibility(View.GONE);
            binding.llDialogLogin.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
            binding.btnLogin.setEnabled(true);
        } else {
            binding.llMainUi.setVisibility(View.VISIBLE);
            binding.llDialogLogin.setVisibility(View.GONE);
            String userName = SharedPreferenceUtil.getMyCardUserName();
            binding.tvMycardUserName.setText(userName);
            GlideCompat.with(getActivity()).load(ChatMessage.getAvatarUrl(userName)).into(binding.myDeckAvatar);//刷新头像图片
            if (deckListAdapter != null) {
                deckListAdapter.loadData();
            }
        }
    }
}

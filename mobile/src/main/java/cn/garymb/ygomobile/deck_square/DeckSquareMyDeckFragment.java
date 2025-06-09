package cn.garymb.ygomobile.deck_square;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSquareMyDeckBinding;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

//打开页面后，先扫描本地的卡组，读取其是否包含deckId，是的话代表平台上可能有
//之后读取平台上的卡组，与本地卡组列表做比较。

public class DeckSquareMyDeckFragment extends Fragment {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private FragmentDeckSquareMyDeckBinding binding;
    private MyDeckListAdapter deckListAdapter;

    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;

    public DeckSquareMyDeckFragment(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDeckSquareMyDeckBinding.inflate(inflater, container, false);
        if (SharedPreferenceUtil.getServerToken() == null) {
            binding.llMainUi.setVisibility(View.GONE);
            binding.llDialogLogin.setVisibility(View.VISIBLE);
        } else {
            binding.llMainUi.setVisibility(View.VISIBLE);
            binding.llDialogLogin.setVisibility(View.GONE);
        }
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnRegister.setOnClickListener(v -> WebActivity.open(getContext(), getString(R.string.register), MyCard.URL_MC_SIGN_UP));
        deckListAdapter = new MyDeckListAdapter(R.layout.item_my_deck);
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getContext(), 3);
        binding.listMyDeckInfo.setLayoutManager(linearLayoutManager);
        binding.listMyDeckInfo.setAdapter(deckListAdapter);
        deckListAdapter.loadData();

        //其实仅仅是清除掉本机的token
        binding.mcLogoutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferenceUtil.deleteServerToken();
                binding.llMainUi.setVisibility(View.GONE);
                binding.llDialogLogin.setVisibility(View.VISIBLE);

            }
        });

        deckListAdapter.setOnItemLongClickListener((adapter, view, position) -> {

            MyDeckItem item = (MyDeckItem) adapter.getItem(position);

            MyDeckDetailDialog dialog = new MyDeckDetailDialog(getContext(), item);
            dialog.show();
            return true;
        });
/*
        //点击“我的卡组”中的某个卡组后，弹出dialog，dialog根据卡组的同步情况自动显示对应的下载/上传按钮
        deckListAdapter.setOnItemClickListener(
                (adapter, view, position) -> {
                    MyDeckItem item = (MyDeckItem) adapter.getItem(position);
                    mDialogListener.onDismiss();
                    DeckFile deckFile = new DeckFile(item.getDeckId(), DeckType.ServerType.MY_SQUARE);
                    onDeckMenuListener.onDeckSelect(deckFile);
                }
        );*/
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

    private void attemptLogin() {
        String username = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (username.isEmpty()) {
            binding.tvAccountWarning.setVisibility(View.VISIBLE);
            return;
        } else {
            binding.tvAccountWarning.setVisibility(View.GONE);
        }

        if (password.isEmpty()) {
            binding.tvPwdWarning.setVisibility(View.VISIBLE);
            return;
        } else {
            binding.tvPwdWarning.setVisibility(View.GONE);
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnLogin.setEnabled(false);

        VUiKit.defer().when(() -> {
            LoginResponse result = DeckSquareApiUtil.login(username, password);//执行登录
            SharedPreferenceUtil.setServerToken(result.token);
            SharedPreferenceUtil.setServerUserId(result.user.id);
            return result;

        }).fail((e) -> {
            YGOUtil.showTextToast(R.string.logining_failed);
            binding.llMainUi.setVisibility(View.GONE);
            binding.progressBar.setVisibility(View.GONE);
            binding.btnLogin.setEnabled(true);
        }).done((result) -> {
            if (result != null) {
                binding.llMainUi.setVisibility(View.VISIBLE);
                deckListAdapter.loadData();
                binding.llDialogLogin.setVisibility(View.GONE);
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);
                YGOUtil.showTextToast(R.string.login_succeed);
                McUser mcUser = new McUser();
                mcUser.setUsername(result.user.username);
                mcUser.setExternal_id(result.user.id);
                mcUser.setAvatar_url(ChatMessage.getAvatarUrl(result.user.username));
                GlideCompat.with(getActivity()).load(mcUser.getAvatar_url()).into(binding.myDeckAvatar);//刷新头像图片
                UserManagement.getDx().setMcUser(mcUser);
            } else {
                YGOUtil.showTextToast(R.string.logining_failed);
                binding.llMainUi.setVisibility(View.GONE);
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);
            }

        });
    }
}

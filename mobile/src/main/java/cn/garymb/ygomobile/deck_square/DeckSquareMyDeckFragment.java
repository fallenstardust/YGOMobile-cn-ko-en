package cn.garymb.ygomobile.deck_square;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSquareMyDeckBinding;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

//打开页面后，先扫描本地的卡组，读取其是否包含deckId，是的话代表平台上可能有
//之后读取平台上的卡组，与本地卡组列表做比较。

public class DeckSquareMyDeckFragment extends Fragment implements PrivacyDialogFragment.PrivacyAgreementListener{
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private FragmentDeckSquareMyDeckBinding binding;
    private MyDeckListAdapter deckListAdapter;

    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;
    private ProgressBar progressBar;
    private EditText etUsername, etPassword;
    private Button btnLogin, btnCancel;
    boolean privacAgree = false;
    LoginDialog loginDialog = null;

    public DeckSquareMyDeckFragment(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDeckSquareMyDeckBinding.inflate(inflater, container, false);
        binding.llMainUi.setVisibility(View.GONE);
        binding.mcLoginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!DeckSquareApiUtil.needLogin()) {
                    return;
                }

                if (privacAgree) {//如果不同意隐私协议，log提示用户，
                    loginDialog = new LoginDialog(getContext(), new LoginDialog.LoginListener() {
                        @Override
                        public void notifyResult(boolean success, LoginResponse response) {
                            // Handle login logic
                            if (success) {
                                binding.llMainUi.setVisibility(View.VISIBLE);
                                LogUtil.i(TAG, "login success" + SharedPreferenceUtil.getServerToken());
                                refreshBtn();
                                //response.token;
                            }else{

                                YGOUtil.showTextToast("登录失败：");
                            }

                        }
                    });
                    loginDialog.show();

                } else {
                    YGOUtil.showTextToast("登录内容需要用户同意协议");
                    showPrivacyDialog();
                }

            }
        });
        //其实仅仅是清除掉本机的token
        binding.mcLogoutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferenceUtil.deleteServerToken();
                binding.llMainUi.setVisibility(View.GONE);
                refreshBtn();

            }
        });
        etUsername = binding.etUsername;
        etPassword = binding.etPassword;
        btnLogin = binding.btnLogin;
        btnLogin.setOnClickListener(v -> attemptLogin());
        progressBar = binding.progressBar;
        refreshBtn();
        deckListAdapter = new MyDeckListAdapter(R.layout.item_my_deck);
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getContext(), 3);
        binding.listMyDeckInfo.setLayoutManager(linearLayoutManager);
        binding.listMyDeckInfo.setAdapter(deckListAdapter);
        deckListAdapter.loadData();


        binding.refreshData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deckListAdapter.loadData();
            }
        });

        deckListAdapter.setOnItemLongClickListener((adapter, view, position) -> {

            MyDeckItem item = (MyDeckItem) adapter.getItem(position);

            MyDeckDetailDialog dialog = new MyDeckDetailDialog(getContext(), item);
            dialog.show();
            return true;
        });

        //点击“我的卡组”中的某个卡组后，弹出dialog，dialog根据卡组的同步情况自动显示对应的下载/上传按钮
        deckListAdapter.setOnItemClickListener(
                (adapter, view, position) -> {

                    MyDeckItem item = (MyDeckItem) adapter.getItem(position);
                    mDialogListener.onDismiss();
                    DeckFile deckFile = new DeckFile(item.getDeckId(), DeckType.ServerType.MY_SQUARE);
                    onDeckMenuListener.onDeckSelect(deckFile);

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

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Please enter both username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        VUiKit.defer().when(() -> {
            LogUtil.d(TAG, "start fetch");


            LoginResponse result = DeckSquareApiUtil.login(username, password);
            SharedPreferenceUtil.setServerToken(result.token);
            SharedPreferenceUtil.setServerUserId(result.user.id);
            return result;

        }).fail((e) -> {
            Log.e(TAG, e + "");
            LogUtil.i(TAG, "login fail");
            binding.llMainUi.setVisibility(View.GONE);

        }).done((result) -> {
            if (result != null) {
                LogUtil.i(TAG, "login done");
                binding.llMainUi.setVisibility(View.VISIBLE);
                binding.llDialogLogin.setVisibility(View.GONE);
                YGOUtil.showTextToast("Login success!");

            } else {
                LogUtil.i(TAG, "login fail2");
            }

        });
    }

    public void refreshBtn() {
        if (DeckSquareApiUtil.getLoginData() != null) {
            binding.mcLoginBtn.setText("已登录");
        } else {

            binding.mcLoginBtn.setText("登录");
        }
    }

    private void showPrivacyDialog() {
        PrivacyDialogFragment dialog = new PrivacyDialogFragment();
        dialog.setPrivacyAgreementListener(this);
        dialog.show(getChildFragmentManager(), "PrivacyDialog");
    }

    @Override
    public void onAgree() {
        privacAgree = true;
    }

    @Override
    public void onDisagree() {
        privacAgree = false;
    }
}

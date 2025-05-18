package cn.garymb.ygomobile.deck_square;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import cn.garymb.ygomobile.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.lite.databinding.FragmentMcOnlineManageBinding;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

//管理用户的登录状态、缓存状态
public class MCOnlineManageFragment extends Fragment implements PrivacyDialogFragment.PrivacyAgreementListener {

    private FragmentMcOnlineManageBinding binding;

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    boolean privacAgree = false;
    LoginDialog loginDialog = null;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentMcOnlineManageBinding.inflate(inflater, container, false);
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
                refreshBtn();

            }
        });


        refreshBtn();
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
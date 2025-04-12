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

//管理用户的登录状态、缓存状态
public class MCOnlineManageFragment extends Fragment {

    private FragmentMcOnlineManageBinding binding;

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentMcOnlineManageBinding.inflate(inflater, container, false);
        binding.mcLoginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginDialog loginDialog = new LoginDialog(getContext(), new LoginDialog.LoginListener() {
                    @Override
                    public void notifyResult(boolean success, LoginResponse response) {
                        // Handle login logic
                        if (success) {

                            LogUtil.i(TAG, "login success" + SharedPreferenceUtil.getServerToken());
                            refreshBtn();
                            //response.token;
                        }

                    }
                });

                loginDialog.show();
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
        if (SharedPreferenceUtil.getServerToken() != null) {
            binding.mcLoginBtn.setText("已登录");
        } else {

            binding.mcLoginBtn.setText("登录");
        }
    }
}
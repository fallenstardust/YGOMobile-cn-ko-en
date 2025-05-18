package cn.garymb.ygomobile.deck_square;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import cn.garymb.ygomobile.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public class LoginDialog extends Dialog {

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    public interface LoginListener {
        void notifyResult(boolean success, LoginResponse response);
    }

    private ProgressBar progressBar;
    private LoginListener listener;
    private EditText etUsername, etPassword;
    private Button btnLogin, btnCancel;

    public LoginDialog(Context context, LoginListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnCancel = findViewById(R.id.btn_cancel);

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnCancel.setOnClickListener(v -> dismiss());

        progressBar = findViewById(R.id.progressBar);
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
        btnCancel.setEnabled(false);

        VUiKit.defer().when(() -> {
            LogUtil.d(TAG, "start fetch");

            LoginResponse result = DeckSquareApiUtil.login(1, "");
            SharedPreferenceUtil.setServerToken(result.token);
            SharedPreferenceUtil.setServerUserId(result.user.id);
            return result;

        }).fail((e) -> {
            Log.e(TAG, e + "");
            listener.notifyResult(false, null);


            LogUtil.i(TAG, "login fail");
            dismiss();

        }).done((result) -> {
            if (result != null) {
                LogUtil.i(TAG, "login done");
                listener.notifyResult(true, result);

                YGOUtil.showTextToast("Login success!");

            } else {
                listener.notifyResult(false, null);
            }
            dismiss();

        });
    }

}
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

import com.google.gson.Gson;

import java.io.IOException;

import cn.garymb.ygomobile.deck_square.api_response.LoginRequest;
import cn.garymb.ygomobile.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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

        // Simulate network call
//        new Handler().postDelayed(() -> {
//            if (listener != null) {
//                listener.onLogin(username, password);
//            }
//            dismiss();
//        }, 1500);

        VUiKit.defer().when(() -> {
            LogUtil.d(TAG, "start fetch");
            LoginResponse result = null;
            try {

                String url = "https://sapi.moecube.com:444/accounts/signin";
                String baseUrl = "https://sapi.moecube.com:444/accounts/signin";
                // Create request body using Gson
                Gson gson = new Gson();
                LoginRequest loginRequest = new LoginRequest("1076306278@qq.com", "Qbz95qbz96");


                String json = gson.toJson(loginRequest);//"{\"id\":1,\"name\":\"John\"}";

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"), json);

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                OkHttpClient okHttpClient = new OkHttpClient();
                Response response = okHttpClient.newCall(request).execute();

                // Read the response body
                String responseBody = response.body().string();
                LogUtil.i(TAG, "Response body: " + responseBody);

                // Process the response
                if (response.isSuccessful()) {
                    // Successful response (code 200-299)
                    // Parse the JSON response if needed
                    result = gson.fromJson(responseBody, LoginResponse.class);
                    LogUtil.i(TAG, "Login response: " + result);
                } else {
                    // Error response
                    LogUtil.e(TAG, "Request failed: " + responseBody);
                }

            } catch (IOException e) {
                Log.e(TAG, "Error occured when fetching data from pre-card server");
                return null;
            }

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
//                getData().clear();
//                addData(exCardDataList);
//                notifyDataSetChanged();
            } else {
                listener.notifyResult(false, null);
            }
            dismiss();

        });
    }
}
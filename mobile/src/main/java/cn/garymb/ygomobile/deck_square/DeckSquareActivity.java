package cn.garymb.ygomobile.deck_square;

import android.os.Bundle;
import android.view.View;

import androidx.navigation.ui.AppBarConfiguration;

import cn.garymb.ygomobile.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.lite.databinding.ActivityDeckSquareBinding;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;

public class DeckSquareActivity extends BaseActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityDeckSquareBinding binding;

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private DeckSquareTabAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDeckSquareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);


        binding.loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
// In your Activity or Fragment
                LoginDialog loginDialog = new LoginDialog(getContext(), new LoginDialog.LoginListener() {
                    @Override
                    public void notifyResult(boolean success, LoginResponse response) {
                        // Handle login logic
                        if (success) {

                            LogUtil.i(TAG, "login success" + SharedPreferenceUtil.getServerToken());
                            //response.token;
                        }

                    }
                });

                loginDialog.show();
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAnchorView(R.id.login_btn)
//                        .setAction("Action", null).show();
            }
        });
        createTabFragment();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        LogUtil.i(TAG, "deck square activity destroy");
    }

    private void createTabFragment() {
        adapter = new DeckSquareTabAdapter(getSupportFragmentManager(), binding.packagetablayout, getContext());
        binding.viewPager.setAdapter(adapter);
        /* setupWithViewPager() is used to link the TabLayout to the ViewPager */
        binding.packagetablayout.setupWithViewPager(binding.viewPager);
    }

}
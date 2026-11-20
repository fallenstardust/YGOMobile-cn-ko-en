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
import cn.garymb.ygomobile.bean.events.DeckDeleteEvent;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.cards.DeckManagerFragment;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

//打开页面后，先扫描本地的卡组，读取其是否包含deckId，是的话代表平台上可能有
//之后读取平台上的卡组，与本地卡组列表做比较。

public class DeckSquareMyDeckFragment extends Fragment {
    private static final String TAG = "DeckSquareMyDeckFragment";
    private FragmentDeckSquareMyDeckBinding binding;
    private MyOnlineDeckListAdapter deckListAdapter;
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
        if (SharedPreferenceUtil.getServerToken() == null) {
            binding.llMainUi.setVisibility(View.GONE);
            binding.llDialogLogin.setVisibility(View.VISIBLE);
        } else {
            binding.llMainUi.setVisibility(View.VISIBLE);
            binding.llDialogLogin.setVisibility(View.GONE);
            binding.tvMycardUserName.setText(SharedPreferenceUtil.getMyCardUserName());
            GlideCompat.with(getActivity()).load(ChatMessage.getAvatarUrl(SharedPreferenceUtil.getMyCardUserName())).into(binding.myDeckAvatar);
        }

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnRegister.setOnClickListener(v -> WebActivity.open(getContext(), getString(R.string.register), MyCard.URL_MC_SIGN_UP));
        deckListAdapter = new MyOnlineDeckListAdapter(onDeckMenuListener, mDialogListener);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position < deckListAdapter.getData().size()) {
                    return deckListAdapter.getData().get(position).getItemType() == DeckListItem.TYPE_SECTION_HEADER ? 3 : 1;
                }
                return 1;
            }
        });
        binding.listMyDeckInfo.setLayoutManager(gridLayoutManager);
        binding.listMyDeckInfo.setAdapter(deckListAdapter);
        deckListAdapter.setSwipeRefreshLayout(binding.srlRefresh);
        binding.srlRefresh.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        binding.srlRefresh.setOnRefreshListener(() -> deckListAdapter.loadData(true));
        deckListAdapter.loadData();

        //其实仅仅是清除掉本机的token
        binding.llMcLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogPlus dialogPlus = new DialogPlus(getContext());
                dialogPlus.setMessage(R.string.logout_mycard);
                dialogPlus.setMessageGravity(Gravity.CENTER);
                dialogPlus.setLeftButtonListener((dlg, s) -> {
                    SharedPreferenceUtil.deleteServerToken();
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeckDeleted(DeckDeleteEvent event) {
        if (deckListAdapter != null) {
            deckListAdapter.rebuildGroupedList(DeckManagerFragment.getOriginalData());
        }
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
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
            SharedPreferenceUtil.setMyCardUserName(result.user.username);
            return result;

        }).fail((e) -> {
            // 登录期间对话框/宿主可能已销毁（旋转、关闭）：binding 已置空，不能再碰视图
            if (binding == null) {
                return;
            }
            YGOUtil.showTextToast(R.string.logining_failed);
            binding.llMainUi.setVisibility(View.GONE);
            binding.progressBar.setVisibility(View.GONE);
            binding.btnLogin.setEnabled(true);
        }).done((result) -> {
            // 同上：回调可能晚于 onDestroyView，先判空避免空指针
            if (binding == null) {
                return;
            }
            if (result != null) {
                binding.llMainUi.setVisibility(View.VISIBLE);
                binding.llDialogLogin.setVisibility(View.GONE);
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);
                //储存信息
                String userName = result.user.username;
                binding.tvMycardUserName.setText(userName);
                McUser mcUser = new McUser();
                mcUser.setUsername(userName);
                mcUser.setExternal_id(result.user.id);
                mcUser.setAvatar_url(ChatMessage.getAvatarUrl(userName));
                mcUser.setToken(result.token);

                if (getActivity() != null) {
                    GlideCompat.with(getActivity()).load(mcUser.getAvatar_url()).into(binding.myDeckAvatar);//刷新头像图片
                }
                UserManagement.getDx().setMcUser(mcUser);
                // 先用服务器现有数据展示在线列表：同步需要网络往返，不能让列表在同步完成前空白
                deckListAdapter.loadData();
                YGOUtil.showTextToast(R.string.login_succeed);
                // 登录成功、token 已写入后再做双向同步：同步内部读取已保存 token，
                // 必须等登录请求返回后才启动（旧写法在登录请求发出后即并行同步，
                // 此时 token 尚未落库，synchronizeDecks 读到 null 直接跳过）
                syncDecksAfterLogin();
            } else {
                YGOUtil.showTextToast(R.string.logining_failed);
                binding.llMainUi.setVisibility(View.GONE);
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);
            }

        });
    }

    /**
     * 登录成功后的卡组双向同步：后台执行 {@link DeckSquareApiUtil#synchronizeDecks()}
     * （上传本地新增/较新的卡组、下载云端较新的卡组），完成后在主线程刷新「我的在线备份」
     * 列表，并通知宿主卡组管理对话框刷新「本地卡组」页签（云卡组可能已下载到本地目录），
     * 使登录后立即看到同步结果。
     */
    private void syncDecksAfterLogin() {
        VUiKit.defer().when(() -> {
            DeckSquareApiUtil.synchronizeDecks();
            return 0;
        }).fail((e) -> {
            LogUtil.e(TAG, "Sync decks failed: " + e);
            // 同步失败仍按服务器现有数据刷新列表，避免登录后一片空白
            if (binding != null) {
                deckListAdapter.loadData();
            }
        }).done((result) -> {
            if (binding == null) {
                return;
            }
            // synchronizeDecks 已重建 DeckManagerFragment.getOriginalData()，
            // 此处直接重建在线备份列表；同步可能把云卡组写入本地目录
            deckListAdapter.loadData();
            refreshHostLocalDeckList();
        });
    }

    /** 同步完成后通知宿主卡组管理对话框刷新「本地卡组」页签 */
    private void refreshHostLocalDeckList() {
        if (getActivity() == null) {
            return;
        }
        Fragment fragment = getActivity().getSupportFragmentManager()
                .findFragmentByTag(DeckManageDialog.DIALOG_TAG);
        if (fragment instanceof DeckManageDialog) {
            ((DeckManageDialog) fragment).refreshLocalDeckList();
        }
    }
}

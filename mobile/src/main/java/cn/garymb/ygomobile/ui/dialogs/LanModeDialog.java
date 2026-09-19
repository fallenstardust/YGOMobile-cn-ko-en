package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.ui.adapters.HostListAdapter;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * 局域网联机主界面（layout_lan_main）：昵称/IP/端口/房间密码输入、局域网房间搜索与列表、
 * 建主入口、加入游戏入口、退出。建主与玩家等待界面分别由 {@link CreateHostDialog}
 * 与 {@link PlayerWaitingDialog} 独立承载。
 */
public class LanModeDialog {
    /** 公共字符串管理器：初始化后可供整个类及外部调用 */
    public static final StringManager mStringManager = DataManager.get().getStringManager();

    private final Context context;
    private PopupWindow popupWindow;
    /** 通过“退出/内部跳转”隐藏（而非销毁）时置位：供原样重显，保留全部视图状态 */
    private boolean hiddenForReuse = false;
    /** 内部跳转（前往建主/玩家等待）时抑制外部 dismiss 回调，避免误触发“回到主菜单” */
    private boolean suppressDismiss = false;
    private PopupWindow.OnDismissListener externalDismissListener;
    private DraggablePopupHelper draggableHelper;

    private EditText etNickname;
    private EditText etHostIp;
    private EditText etHostPort;
    private EditText etRoomPassword;

    private LanDiscoveryManager discoveryManager;
    private HostListAdapter hostListAdapter;
    private final List<LanDiscoveryManager.HostEntry> discoveredHosts = new ArrayList<>();

    public interface OnLanModeListener {
        /** 点击“局域网建主”：携带当前昵称，交由外部打开 CreateHostDialog */
        void onCreateHostRequested(String nickname);

        void onJoinGameRequested(String ip, String port, String password, String nickname);
    }

    private final OnLanModeListener listener;

    private final PopupWindow.OnDismissListener internalDismissListener = () -> {
        if (suppressDismiss) {
            suppressDismiss = false;
            return;
        }
        if (externalDismissListener != null) externalDismissListener.onDismiss();
    };

    public LanModeDialog(Context context, OnLanModeListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        this.externalDismissListener = listener;
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(internalDismissListener);
        }
    }

    public void show(View anchorView) {
        View customView = LayoutInflater.from(context).inflate(R.layout.popup_window_lan_main, null);

        etNickname = customView.findViewById(R.id.et_nickname);
        etHostIp = customView.findViewById(R.id.et_host_ip);
        etHostPort = customView.findViewById(R.id.et_host_port);
        etRoomPassword = customView.findViewById(R.id.et_room_password);
        Button btnCreateHost = customView.findViewById(R.id.btn_create_host);
        Button btnRefreshLan = customView.findViewById(R.id.btn_refresh_lan);
        Button btnJoinGame = customView.findViewById(R.id.btn_join_game);
        Button btnExitLan = customView.findViewById(R.id.btn_exit_lan);
        ListView lvHostList = customView.findViewById(R.id.lv_host_list);

        discoveryManager = new LanDiscoveryManager();
        hostListAdapter = new HostListAdapter(context);
        lvHostList.setAdapter(hostListAdapter);

        lvHostList.setOnItemClickListener((parent, view, position, id) -> {
            hostListAdapter.setSelectedPosition(position);
            LanDiscoveryManager.HostEntry entry = hostListAdapter.getDataItem(position);
            if (entry != null) {
                etHostIp.setText(entry.ip);
                etHostPort.setText(String.valueOf(entry.port));
            }
        });

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(internalDismissListener);

        customView.setFocusableInTouchMode(true);
        customView.requestFocus();

        draggableHelper = new DraggablePopupHelper(context, "lan_mode_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight);

        btnCreateHost.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCreateHostRequested(etNickname.getText().toString().trim());
            }
        });

        btnExitLan.setOnClickListener(v -> hide());

        btnRefreshLan.setOnClickListener(v -> {
            if (discoveryManager == null || discoveryManager.isDiscovering()) return;

            btnRefreshLan.setEnabled(false);
            btnRefreshLan.setText(mStringManager.getSystemString(1703, "搜索中..."));
            discoveredHosts.clear();
            hostListAdapter.clear();
            hostListAdapter.notifyDataSetChanged();

            discoveryManager.startDiscovery(new LanDiscoveryManager.DiscoveryListener() {
                @Override
                public void onDiscoveryStarted() {
                    Log.i("LanModeDialog", "LAN discovery started");
                }

                @Override
                public void onHostFound(LanDiscoveryManager.HostEntry host) {
                    discoveredHosts.add(host);
                    hostListAdapter.add(host);
                    hostListAdapter.notifyDataSetChanged();
                }

                @Override
                public void onDiscoveryFinished() {
                    btnRefreshLan.setEnabled(true);
                    btnRefreshLan.setText(mStringManager.getSystemString(1217, "刷新局域网"));
                    if (discoveredHosts.isEmpty()) {
                        YGOUtil.show("未发现局域网房间");
                    }
                }

                @Override
                public void onDiscoveryError(String message) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnJoinGame.setOnClickListener(v -> {
            String ip = etHostIp.getText().toString().trim();
            String port = etHostPort.getText().toString().trim();
            String password = etRoomPassword.getText().toString().trim();
            String nickname = etNickname.getText().toString().trim();

            if (ip.isEmpty()) {
                Toast.makeText(context, mStringManager.getSystemString(1705, "请输入主机IP"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (port.isEmpty()) {
                Toast.makeText(context, mStringManager.getSystemString(1706, "请输入端口"), Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                listener.onJoinGameRequested(ip, port, password, nickname);
            }
        });

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    public void preFillConnectionFields(String nickname, String hostIp, String port, String roomPassword) {
        if (nickname != null && !nickname.isEmpty() && etNickname != null) etNickname.setText(nickname);
        if (hostIp != null && !hostIp.isEmpty() && etHostIp != null) etHostIp.setText(hostIp);
        if (port != null && !port.isEmpty() && etHostPort != null) etHostPort.setText(port);
        if (roomPassword != null && etRoomPassword != null) etRoomPassword.setText(roomPassword);
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    /** 退出到主菜单：保留实例供复用，并触发外部 dismiss 回调（恢复主菜单） */
    public void hide() {
        if (popupWindow == null) return;
        hiddenForReuse = true;
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /** 内部跳转（前往建主/玩家等待）：保留实例供复用，但不触发外部 dismiss 回调；
     * 弹窗退场动画会延迟触发 onDismiss，抑制标志由回调内消费复位，
     * 否则延迟回调会误触发 restoreMainMenu（建主后进等待界面主菜单错误叠出） */
    public void hideForNavigation() {
        if (popupWindow == null) return;
        hiddenForReuse = true;
        if (popupWindow.isShowing()) {
            suppressDismiss = true;
            popupWindow.dismiss();
        }
    }

    /** 是否为“隐藏待复用”状态：可被 reshow() 原样重显 */
    public boolean canReshow() {
        return hiddenForReuse && popupWindow != null;
    }

    /** 重新显示此前隐藏的对话框，保留其原有布局与已填信息 */
    public void reshow(View anchorView) {
        if (popupWindow == null || draggableHelper == null) return;
        if (popupWindow.isShowing()) return;
        hiddenForReuse = false;
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    /** 彻底销毁：停止搜索并关闭弹窗（触发外部 dismiss 回调，除非已置空） */
    public void dismiss() {
        hiddenForReuse = false;
        if (discoveryManager != null) {
            discoveryManager.stopDiscovery();
        }
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    // === 静态入口：由 MainMenuDialog / YGOProActivity 调用 ===

    public static void showLanModeDialog(YGOProActivity activity) {
        LanModeDialog existing = activity.getLanModeDialog();
        // 已在显示中：忽略重复点击
        if (existing != null && existing.isShowing()) return;
        activity.getMainMenuDialog().hideMainMenu();
        // 之前隐藏的对话框：原样重显，保留其布局与已填信息，不再重新创建
        if (existing != null && existing.canReshow()) {
            existing.reshow(activity.getDialogContainer());
            return;
        }
        LanModeDialog dialog = new LanModeDialog(activity, activity.getDialogNavListener());
        activity.setLanModeDialog(dialog);
        dialog.show(activity.getDialogContainer());
        dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
    }
}
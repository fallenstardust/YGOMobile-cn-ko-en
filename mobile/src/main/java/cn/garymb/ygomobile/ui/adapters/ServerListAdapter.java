package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;

import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.events.ServerInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.ServerInfoViewHolder;
import cn.garymb.ygomobile.ui.plus.DialogPlus;

public class ServerListAdapter extends BaseRecyclerAdapterPlus<ServerInfo, ServerInfoViewHolder> {
    public ServerListAdapter(Context context) {
        super(context);
    }

    @Override
    public ServerInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ServerInfoViewHolder(inflate(R.layout.item_server_info_swipe, parent, false));
    }

    @Override
    public void onBindViewHolder(ServerInfoViewHolder holder, int position) {
        ServerInfo item = getItem(position);
        holder.serverName.setText(item.getName());
        holder.serverIp.setText(item.getServerAddr());
        holder.userName.setText(item.getPlayerName());
        holder.serverPort.setText(String.valueOf(item.getPort()));
        holder.iv_fond.setOnClickListener((v) -> {
            DialogPlus builder = new DialogPlus(getContext());
            builder.setTitle(R.string.OpenTIP);
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                builder.setMessage(context.getString(R.string.join_helper_tip1) + context.getString(R.string.join_helper_tip2));
                builder.setLeftButtonText(R.string.Open_Alert_Window);
                builder.setLeftButtonListener((dlg, s) -> {
                    getContext().startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName())));
                    dlg.dismiss();
                });
            } else {
                builder.setMessage(R.string.join_helper_tip1);
            }
            builder.show();
        });
        if (position == 0) {
            holder.iv_fond.setBackgroundResource(R.drawable.cube);
        } else {
            holder.iv_fond.setBackgroundResource(R.drawable.cube2);
        }
        bindMenu(holder, position);
    }

    public void bindMenu(ServerInfoViewHolder holder, int position) {
        if (holder.contentView != null) {
            holder.contentView.setOnClickListener((v) -> {
                ServerInfoEvent event = new ServerInfoEvent(position, false);
                event.join = true;
                EventBus.getDefault().post(event);
                if (holder.mMenuLayout.isMenuOpen()) {
                    holder.mMenuLayout.smoothCloseMenu();
                }
            });
        }
        if (holder.btnEdit != null) {
            holder.btnEdit.setOnClickListener((v) -> {
                EventBus.getDefault().post(new ServerInfoEvent(position, false));
                if (holder.mMenuLayout.isMenuOpen()) {
                    holder.mMenuLayout.smoothCloseMenu();
                }
            });
        }
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener((v) -> {
                EventBus.getDefault().post(new ServerInfoEvent(position, true));
                if (holder.mMenuLayout.isMenuOpen()) {
                    holder.mMenuLayout.smoothCloseMenu();
                }
            });
        }
    }
}


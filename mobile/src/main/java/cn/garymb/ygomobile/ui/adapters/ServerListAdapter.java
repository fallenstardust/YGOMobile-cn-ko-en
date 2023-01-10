package cn.garymb.ygomobile.ui.adapters;

import static cn.garymb.ygomobile.utils.BitmapUtil.getPaint;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.transition.Visibility;

import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.tubb.smrv.SwipeHorizontalMenuLayout;

import org.greenrobot.eventbus.EventBus;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.events.ServerInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;

public class ServerListAdapter extends BaseRecyclerAdapterPlus<ServerInfo, BaseViewHolder> {
    public ServerListAdapter(Context context) {
        super(context, R.layout.item_server_info_swipe);
        bindMenu();
    }

//    @Override
//    public ServerInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
//        return new ServerInfoViewHolder(inflate(R.layout.item_server_info_swipe, parent, false));
//    }

//    @Override
//    public void onBindViewHolder(ServerInfoViewHolder holder, int position) {
//        ServerInfo item = getItem(position);
//        holder.serverName.setText(item.getName());
//        holder.serverIp.setText(item.getServerAddr());
//        holder.userName.setText(item.getPlayerName());
//        holder.serverPort.setText(String.valueOf(item.getPort()));
//        holder.iv_fond.setOnClickListener((v) -> {
//            DialogPlus builder = new DialogPlus(getContext());
//            builder.setTitle(R.string.OpenTIP);
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//                builder.setMessage(context.getString(R.string.join_helper_tip1) + context.getString(R.string.join_helper_tip2));
//                builder.setLeftButtonText(R.string.Open_Alert_Window);
//                builder.setLeftButtonListener((dlg, s) -> {
//                    getContext().startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName())));
//                    dlg.dismiss();
//                });
//            } else {
//                builder.setMessage(R.string.join_helper_tip1);
//            }
//            builder.show();
//        });
//        if (position == 0) {
//            holder.iv_fond.setBackgroundResource(R.drawable.cube);
//        } else {
//            holder.iv_fond.setBackgroundResource(R.drawable.cube2);
//        }
//        bindMenu(holder, position);
//    }

    public void bindMenu() {
        addChildClickViewIds(R.id.smContentView, R.id.btn_edit_delete, R.id.btn_delete, R.id.iv_fond);
        setOnItemChildClickListener((adapter, view, position) -> {
            switch (view.getId()) {
                case R.id.smContentView:
                    ServerInfoEvent event = new ServerInfoEvent(position, false);
                    event.join = true;
                    EventBus.getDefault().post(event);//TODO 没有监听啊。。
                    SwipeHorizontalMenuLayout menuLayout = (SwipeHorizontalMenuLayout) adapter.getViewByPosition(position, R.id.swipe_layout);
                    if (menuLayout.isMenuOpen()) {
                        menuLayout.smoothCloseMenu();
                    }
                    break;
                case R.id.btn_edit_delete:
                    EventBus.getDefault().post(new ServerInfoEvent(position, false));
                    SwipeHorizontalMenuLayout menuLayout1 = (SwipeHorizontalMenuLayout) adapter.getViewByPosition(position, R.id.swipe_layout);
                    if (menuLayout1.isMenuOpen()) {
                        menuLayout1.smoothCloseMenu();
                    }
                    break;
                case R.id.btn_delete:
                    EventBus.getDefault().post(new ServerInfoEvent(position, true));
                    SwipeHorizontalMenuLayout menuLayout2 = (SwipeHorizontalMenuLayout) adapter.getViewByPosition(position, R.id.swipe_layout);

                    if (menuLayout2.isMenuOpen()) {
                        menuLayout2.smoothCloseMenu();
                    }
                    break;/*
                case R.id.iv_fond:
                    DialogPlus builder = new DialogPlus(getContext());
                    builder.setTitle(R.string.OpenTIP);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
                    break;*/
            }
        });
    }

    @Override
    protected void convert(@NonNull com.chad.library.adapter.base.viewholder.BaseViewHolder baseViewHolder, ServerInfo serverInfo) {
        int position = baseViewHolder.getAdapterPosition()
                - getHeaderLayoutCount();
        baseViewHolder.setText(R.id.server_name, serverInfo.getName());
        baseViewHolder.setText(R.id.text_ip, serverInfo.getServerAddr());
        baseViewHolder.setText(R.id.text_player, serverInfo.getPlayerName());
        baseViewHolder.setText(R.id.text_port, String.valueOf(serverInfo.getPort()));

        if (position == 0) {
            baseViewHolder.findView(R.id.iv_fond).setVisibility(View.VISIBLE);
        } else {
            baseViewHolder.findView(R.id.iv_fond).setVisibility(View.GONE);
        }

        if (position == 4) {//假设第五个是先行卡服务器
            if (AppsSettings.get().isReadExpansions()) {
                Paint paint = getPaint(1);
                baseViewHolder.getView(R.id.swipe_layout).setLayerType(View.LAYER_TYPE_HARDWARE, paint);
            } else {
                Paint paint = getPaint(0);
                baseViewHolder.getView(R.id.swipe_layout).setLayerType(View.LAYER_TYPE_HARDWARE, paint);
            }

        }
    }
}


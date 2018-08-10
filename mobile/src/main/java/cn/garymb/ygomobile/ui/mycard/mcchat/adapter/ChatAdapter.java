package cn.garymb.ygomobile.ui.mycard.mcchat.adapter;

import android.app.Activity;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.Util;


public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private List<ChatMessage> data;
    private Context context;

    private static final int CHAT = 0;
    private static final int CHAT_ME = 1;


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup p1, int p2) {
        View v = null;
        switch (p2) {
            case CHAT:
                v = LayoutInflater.from(p1.getContext()).inflate(R.layout.item_chat, p1, false);
                break;
            case CHAT_ME:
                v = LayoutInflater.from(p1.getContext()).inflate(R.layout.item_chat_me, p1, false);
                break;
        }
        ViewHolder viewHolder = new ViewHolder(v, p2);
        // TODO: Implement this method
        return viewHolder;

    }

    @Override
    public int getItemViewType(int position) {
        if (data.get(position).getName().equals(UserManagement.getUserName())) {
            return CHAT_ME;
        } else {
            return CHAT;
        }
    }


    @Override
    public void onBindViewHolder(ViewHolder vh, int position) {
        final ChatMessage cm = data.get(position);
        vh.ic_name.setText(cm.getName());
        //vh.ic_time.setText(cm.getTime());
        vh.ic_message.setText(cm.getMessage());
        ImageUtil.tuxian(context, cm.getAvatar(), vh.ic_avatar);
        if (position != 0) {
            if (cm.getName().equals(data.get(position - 1).getName())) {
                vh.ic_name.setVisibility(View.GONE);
                vh.ic_avatar.setVisibility(View.INVISIBLE);
            } else {
                vh.ic_name.setVisibility(View.VISIBLE);
                vh.ic_avatar.setVisibility(View.VISIBLE);
            }
        } else {
            vh.ic_name.setVisibility(View.VISIBLE);
            vh.ic_avatar.setVisibility(View.VISIBLE);

        }
        vh.ic_dialog.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View p1) {
                Util.fzMessage(context, cm.getMessage());
                Util.show(context, "已复制到剪贴板");
                // TODO: Implement this method
                return true;
            }
        });
        vh.vh.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View p1) {
                Util.closeKeyboard((Activity) context);
                // TODO: Implement this method
            }
        });

        // TODO: Implement this method
    }

    @Override
    public int getItemCount() {
        // TODO: Implement this method
        return data.size();
    }


    public ChatAdapter(Context context, List<ChatMessage> data) {
        this.context = context;
        this.data = data;
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        TextView ic_name, ic_message;
        ImageView ic_avatar;
        LinearLayout ic_dialog;
        View vh;

        public ViewHolder(View v, int position) {
            super(v);
            vh = v;
            if (position == CHAT) {
                ic_dialog = (LinearLayout) v.findViewById(R.id.ic_dialog);
                ic_name = (TextView) v.findViewById(R.id.ic_name);
                ic_avatar = (ImageView) v.findViewById(R.id.ic_avatar);
                ic_message = (TextView) v.findViewById(R.id.ic_message);
            } else {
                ic_name = (TextView) v.findViewById(R.id.icm_name);
                ic_avatar = (ImageView) v.findViewById(R.id.icm_avatar);
                ic_message = (TextView) v.findViewById(R.id.icm_message);
                ic_dialog = (LinearLayout) v.findViewById(R.id.icm_dialog);
            }
        }

    }

    public void sx() {

        notifyDataSetChanged();

    }
}



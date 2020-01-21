package com.ourygo.ygomobile.util;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.feihua.dialogutils.util.DialogUtils;

import androidx.cardview.widget.CardView;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.lite.R;


public class OYDialogUtil {

    public static void dialogcreateRoom(Activity activity, ServerInfo serverInfo){
        DialogUtils du=DialogUtils.getInstance(activity);
        View v=du.dialogBottomSheet(R.layout.dialog_create_room);
        TextView tv_room,tv_ip,tv_port,tv_mode,tv_lflist,tv_password;
        Button bt_copy_room;
        ImageView iv_close;
        CardView cv_room;

        tv_room=v.findViewById(R.id.tv_room);
        tv_ip=v.findViewById(R.id.tv_ip);
        tv_port=v.findViewById(R.id.tv_port);
        tv_mode=v.findViewById(R.id.tv_mode);
        tv_lflist=v.findViewById(R.id.tv_lflist);
        tv_password=v.findViewById(R.id.tv_password);
        bt_copy_room=v.findViewById(R.id.bt_copy_room);
        iv_close=v.findViewById(R.id.iv_close);
        cv_room=v.findViewById(R.id.cv_room);

        tv_room.setText(serverInfo.getName());
        tv_ip.setText(serverInfo.getServerAddr());
        tv_port.setText(""+serverInfo.getPort());
        tv_password.setText("M#"+OYUtil.message2Base64(System.currentTimeMillis()+OYUtil.getIMEI()));

        cv_room.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                du.dis();
                YGOUtil.joinGame(activity,serverInfo,tv_password.getText().toString());
            }
        });

        bt_copy_room.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OYUtil.copyMessage(tv_password.getText().toString());
                OYUtil.show(OYUtil.s(R.string.copy_ok));
//              du.dis();
            }
        });

        iv_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                du.dis();
            }
        });


    }

}

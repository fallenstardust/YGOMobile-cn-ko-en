package com.ourygo.ygomobile.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feihua.dialogutils.adapter.IconTextRecyclerViewAdapter;
import com.feihua.dialogutils.bean.ItemData;
import com.feihua.dialogutils.util.DialogUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.adapter.RoomSpinnerAdapter;
import com.ourygo.ygomobile.bean.YGOServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;


public class OYDialogUtil {

    public static final int DECK_TYPE_MESSAGE = 0;
    public static final int DECK_TYPE_URL = 1;
    public static final int DECK_TYPE_PATH = 2;

    public static void dialogAiList(Activity activity, YGOServer ygoServer) {
        Dialog builder = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.room_ai_dialog, null);
        builder.setContentView(view);
        Window window = builder.getWindow();
        if (window != null)
            window.findViewById(R.id.design_bottom_sheet)
                    .setBackgroundResource(android.R.color.transparent);
        builder.show();

        List<ItemData> itemDataList = new ArrayList<>();
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "悠悠"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "悠悠王"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "琪露诺"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "谜之剑士LV4"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "复制植物"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "尼亚"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "永远之魂"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "比特机灵"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "复制梁龙"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "奇異果"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "奇魔果"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "MAX龍果"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "幻煌果"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "燃血鬥士"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "報社鬥士"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "我太帅了"));
        itemDataList.add(ItemData.toItemData(0, R.drawable.ic_ai, "玻璃女巫"));

        RecyclerView rv_new_file_list = view.findViewById(R.id.rv_list);
        rv_new_file_list.setLayoutManager(new LinearLayoutManager(activity));
        IconTextRecyclerViewAdapter nFAdp = new IconTextRecyclerViewAdapter(itemDataList, true);
        rv_new_file_list.setAdapter(nFAdp);
        nFAdp.setOnITItemClickListener(position -> {
            builder.dismiss();
            YGOUtil.joinGame(activity, ygoServer, "AI#" + itemDataList.get(position).getName());
        });
    }

    public static void dialogDASaveDeck(Activity activity, String deckMessage, int deckType) {
        DialogUtils du = DialogUtils.getInstance(activity);
        View v = du.dialogBottomSheet(R.layout.da_save_deck_dialog);
        TextView tv_save_deck;

        tv_save_deck = v.findViewById(R.id.tv_save_deck);
        tv_save_deck.setOnClickListener(v12 -> {
            du.dialogj1(null,"卡组保存中，请稍等");
            switch (deckType) {
                case DECK_TYPE_MESSAGE:
                    //如果是卡组文本
                    try {
                        //以当前时间戳作为卡组名保存卡组
                        File file = DeckUtils.save(OYUtil.s(R.string.rename_deck) + System.currentTimeMillis(), deckMessage);
                        OYUtil.show("保存成功");
                        openYdk(activity,file);
                    } catch (IOException e) {
                        e.printStackTrace();
                        OYUtil.show(OYUtil.s(R.string.save_failed_bcos) + e);
                    }
                    break;
                case DECK_TYPE_URL:
                    Deck deckInfo = new Deck(OYUtil.s(R.string.rename_deck) + System.currentTimeMillis(), Uri.parse(deckMessage));
                    Log.e("OYDialogUtil", "数量" + deckInfo.getMainCount() + " " + deckInfo.getExtraCount() + " " + deckInfo.getSideCount());
                    File file = deckInfo.saveTemp(AppsSettings.get().getDeckDir());
//                    try {
//                        FileUtil.copyFile(file.getAbsolutePath(), AppsSettings.get().getDeckDir(), false);
                    openYdk(activity,file);
//                    } catch (IOException e) {
//                        OYUtil.show(OYUtil.s(R.string.save_failed_bcos) + e);
//                        e.printStackTrace();
//                    }
                    break;
                case DECK_TYPE_PATH:
                    try {
                        File file1=new File(FileUtil.copyFile(deckMessage, AppsSettings.get().getDeckDir(), false));
                        openYdk(activity,file1);
                    } catch (IOException e) {
                        OYUtil.show(OYUtil.s(R.string.save_failed_bcos) + e);
                        e.printStackTrace();
                    }
                    break;
                default:
                    du.dis();
            }
        });

    }

    private static void openYdk(Activity context, File ydkFile) {
        DialogUtils dialogUtils = DialogUtils.getInstance(context);
        View[] v = dialogUtils.dialogt(ydkFile.getName(), "卡组保存成功，是否打开?");
        Button b1, b2;
        b1 = (Button) v[0];
        b2 = (Button) v[1];
        b1.setText("取消");
        b2.setText("打开");
        b1.setOnClickListener(v1 -> {
            dialogUtils.dis();
        });
        b2.setOnClickListener(v12 -> {
            dialogUtils.dis();
            String name=ydkFile.getName();
            if (name.endsWith(".ydk"))
                name=name.substring(0,name.lastIndexOf("."));
            IntentUtil.startYGODeck(context,name);
        });
    }

    public static void dialogDAJoinRoom(Activity activity, final YGOServer serverInfo, String password) {
        DialogUtils du = DialogUtils.getInstance(activity);
        View v = du.dialogBottomSheet(R.layout.da_join_room_dialog);
        TextView tv_join_room, tv_host, tv_port, tv_password;

        tv_join_room = v.findViewById(R.id.tv_join_room);
        tv_host = v.findViewById(R.id.tv_host);
        tv_port = v.findViewById(R.id.tv_port);
        tv_password = v.findViewById(R.id.tv_password);

        tv_host.setText(serverInfo.getServerAddr());
        tv_port.setText(serverInfo.getPort() + "");
        tv_password.setText(password);

        tv_join_room.setOnClickListener(v12 -> {
            if (!OYApplication.isIsInitRes()) {
                OYUtil.show("请等待资源加载完毕后加入游戏");
                return;
            }
            OYUtil.closeKeyboard(du.getDialog());
            du.dis();
            YGOUtil.joinGame(activity, serverInfo, password);
        });

    }

    public static void dialogJoinRoom(Activity activity, final YGOServer serverInfo) {
        DialogUtils du = DialogUtils.getInstance(activity);
        View v = du.dialogBottomSheet(R.layout.dialog_join_room);
        Dialog dialog = du.getDialog();
        TextView tv_join_room;
        ImageView iv_close;
        EditText et_password;
        Spinner sp_room;
        ImageView iv_switch;

        tv_join_room = v.findViewById(R.id.tv_join_room);
        iv_close = v.findViewById(R.id.iv_close);
        et_password = v.findViewById(R.id.et_password);
        sp_room = v.findViewById(R.id.sp_room);
        iv_switch = v.findViewById(R.id.iv_switch);

        iv_switch.setOnClickListener(v14 -> {
            sp_room.performClick();
        });

        du.getDialog().setOnDismissListener(dialog1 -> {
            OYUtil.closeKeyboard(du.getDialog());
        });
        YGOUtil.getYGOServerList(serverList -> {
            List<YGOServer> serverInfoList = serverList.getServerInfoList();
            int position = 0;
            if (serverInfo != null)
                for (int i = 0; i < serverInfoList.size(); i++) {
                    if (serverInfoList.get(i).getName().equals(serverInfo.getName()))
                        position = i;
                }
//            roomSpinnerAdapter.setNewData(serverInfoList);
            RoomSpinnerAdapter roomSpinnerAdapter = new RoomSpinnerAdapter(activity, serverInfoList);
            sp_room.setAdapter(roomSpinnerAdapter);
            sp_room.setSelection(position);
        });

        iv_close.setOnClickListener(v1 -> dialog.dismiss());

        new Handler().postDelayed(() -> OYUtil.showKeyboard(et_password), 100);

        et_password.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s)) {
                    tv_join_room.setText(OYUtil.s(R.string.random_match));
                } else {
                    tv_join_room.setText(OYUtil.s(R.string.join_room));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (TextUtils.isEmpty(s)) {
                    tv_join_room.setText(OYUtil.s(R.string.random_match));
                } else {
                    tv_join_room.setText(OYUtil.s(R.string.join_room));
                }
            }
        });

        et_password.setOnKeyListener((v13, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!OYApplication.isIsInitRes()) {
                    OYUtil.show("请等待资源加载完毕后加入游戏");
                    return true;
                }
                OYUtil.closeKeyboard(dialog);
                dialog.dismiss();
                YGOUtil.joinGame(activity, ((RoomSpinnerAdapter) sp_room.getAdapter()).getItem(sp_room.getSelectedItemPosition()), et_password.getText().toString().trim());
                return true;
            }
            return false;
        });
        tv_join_room.setOnClickListener(v12 -> {
            if (!OYApplication.isIsInitRes()) {
                OYUtil.show("请等待资源加载完毕后加入游戏");
                return;
            }
            OYUtil.closeKeyboard(dialog);
            dialog.dismiss();
            YGOUtil.joinGame(activity, ((RoomSpinnerAdapter) sp_room.getAdapter()).getItem(sp_room.getSelectedItemPosition()), et_password.getText().toString().trim());
        });

    }

    public static void dialogcreateRoom(Activity activity, YGOServer serverInfo) {
        DialogUtils du = DialogUtils.getInstance(activity);
        View v = du.dialogBottomSheet(R.layout.dialog_create_room);
        TextView tv_room, tv_ip, tv_port, tv_mode, tv_lflist, tv_password;
        TextView bt_copy_room, tv_join_room;
        ImageView iv_close, iv_share_qq, iv_share_wechat, iv_share_more;
        RelativeLayout rl_room;

        tv_room = v.findViewById(R.id.tv_room);
        tv_ip = v.findViewById(R.id.tv_ip);
        tv_port = v.findViewById(R.id.tv_port);
        tv_mode = v.findViewById(R.id.tv_mode);
        tv_lflist = v.findViewById(R.id.tv_lflist);
        tv_password = v.findViewById(R.id.tv_password);
        bt_copy_room = v.findViewById(R.id.bt_copy_room);
        iv_close = v.findViewById(R.id.iv_close);
        rl_room = v.findViewById(R.id.rl_room);
        iv_share_qq = v.findViewById(R.id.iv_share_qq);
        iv_share_wechat = v.findViewById(R.id.iv_share_wechat);
        iv_share_more = v.findViewById(R.id.iv_share_more);
        tv_join_room = v.findViewById(R.id.tv_join_room);

        tv_room.setText(serverInfo.getName());
        tv_ip.setText(serverInfo.getServerAddr());
        tv_port.setText("" + serverInfo.getPort());
        String pa = OYUtil.message2Base64URL(System.currentTimeMillis() + "");
        pa = pa.substring(pa.length() - 7);


        switch (serverInfo.getMode()) {
            case YGOServer.MODE_ONE:
                tv_mode.setText(OYUtil.s(R.string.duel_mode_one));
                tv_password.setText(pa);
                break;
            case YGOServer.MODE_MATCH:
                tv_mode.setText(OYUtil.s(R.string.duel_mode_match));
                tv_password.setText("M#" + pa);
                break;
            case YGOServer.MODE_TAG:
                tv_mode.setText(OYUtil.s(R.string.duel_mode_tag));
                tv_password.setText("T#" + pa);
                break;
            default:
                tv_mode.setText(OYUtil.s(R.string.duel_mode_one));
                tv_password.setText(pa);
        }

        tv_join_room.setOnClickListener(v1 -> {
            if (!OYApplication.isIsInitRes()) {
                OYUtil.show("请等待资源加载完毕后加入游戏");
                return;
            }
            du.dis();
            YGOUtil.joinGame(activity, serverInfo, tv_password.getText().toString());
        });

        bt_copy_room.setOnClickListener(v13 -> {
//            if (!OYApplication.isIsInitRes()) {
//                OYUtil.show("请等待资源加载完毕后加入游戏");
//                return;
//            }
            OYUtil.copyMessage(serverInfo.toUri(tv_password.getText().toString()));
            OYUtil.show(OYUtil.s(R.string.copy_ok));
            du.dis();
        });

        iv_share_qq.setOnClickListener(v14 -> {
            String message = serverInfo.toUri(tv_password.getText().toString());
            OYUtil.copyMessage(message);
            ShareUtil.shareQQ(activity, message);
//            OYUtil.show(OYUtil.s(R.string.copy_ok));
            du.dis();
        });
        iv_share_wechat.setOnClickListener(v15 -> {
            String message = serverInfo.toUri(tv_password.getText().toString());
            OYUtil.copyMessage(message);
            ShareUtil.shareWechatFriend(activity, message);
            du.dis();
        });
        iv_share_more.setOnClickListener(v15 -> {
            String message = serverInfo.toUri(tv_password.getText().toString());
            OYUtil.copyMessage(message);
            ShareUtil.share(activity, message);
            du.dis();
        });


        iv_close.setOnClickListener(v12 -> du.dis());


    }

}

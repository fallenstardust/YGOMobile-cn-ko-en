package com.ourygo.ygomobile.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feihua.dialogutils.adapter.IconTextRecyclerViewAdapter;
import com.feihua.dialogutils.bean.ItemData;
import com.feihua.dialogutils.util.DialogUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.ourygo.lib.duelassistant.util.YGODAUtil;
import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.adapter.RoomSpinnerAdapter;
import com.ourygo.ygomobile.base.listener.OnSetBgListener;
import com.ourygo.ygomobile.bean.CardBag;
import com.ourygo.ygomobile.bean.UpdateInfo;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.ui.activity.DeckManagementActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.utils.FileUtils;


public class OYDialogUtil {

    public static final int DECK_TYPE_MESSAGE = 0;
    public static final int DECK_TYPE_DECK = 1;
    public static final int DECK_TYPE_PATH = 2;

    public static final int BG_TYPE_DUEL = 0;
    public static final int BG_TYPE_MENU = 1;
    public static final int BG_TYPE_DECK = 2;


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
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "悠悠"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "悠悠王"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "琪露诺"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "谜之剑士LV4"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "复制植物"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "尼亚"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "永远之魂"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "比特机灵"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "复制梁龙"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "奇異果"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "奇魔果"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "MAX龍果"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "幻煌果"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "燃血鬥士"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "報社鬥士"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "我太帅了"));
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "玻璃女巫"));

        RecyclerView rv_new_file_list = view.findViewById(R.id.rv_list);
        rv_new_file_list.setLayoutManager(new LinearLayoutManager(activity));
        IconTextRecyclerViewAdapter nFAdp = new IconTextRecyclerViewAdapter(itemDataList, true);
        rv_new_file_list.setAdapter(nFAdp);
        nFAdp.setOnITItemClickListener(position -> {
            builder.dismiss();
            YGOUtil.joinGame(activity, ygoServer, "AI#" + itemDataList.get(position).getName());
        });
    }

    public static void dialogDASaveDeck(Activity activity, String deckMessage,int deckType) {
        dialogDASaveDeck(activity,deckMessage,null,deckType);
    }
    public static void dialogDASaveDeck(Activity activity, String deckMessage,Deck deck, int deckType) {
        DialogUtils du = DialogUtils.getInstance(activity);
        View v = du.dialogBottomSheet(R.layout.da_save_deck_dialog);
        Dialog dialog = du.getDialog();
        TextView tv_save_deck;

        tv_save_deck = v.findViewById(R.id.tv_save_deck);
        tv_save_deck.setOnClickListener(v12 -> {
            dialog.dismiss();
            du.dialogj1(null, "卡组保存中，请稍等");
            switch (deckType) {
                case DECK_TYPE_MESSAGE:
                    //如果是卡组文本
                    try {
                        //以当前时间戳作为卡组名保存卡组
                        File file = DeckUtils.save(OYUtil.s(R.string.rename_deck) + System.currentTimeMillis(), deckMessage);
                        du.dis();
                        openYdk(activity, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                        OYUtil.show(OYUtil.s(R.string.save_failed_bcos) + e);
                    }
                    break;
                case DECK_TYPE_DECK:
                    if (deck == null) {
                        OYUtil.show("卡组信息为空，无法保存");
                        return;
                    }
                    Log.e("OYDialogUtil", "数量" + deck.getMainCount() + " " + deck.getExtraCount() + " " + deck.getSideCount());
                    File file = deck.saveTemp(AppsSettings.get().getDeckDir());
                    if (!deck.isCompleteDeck()) {
                        OYUtil.show("当前卡组缺少完整信息，将只显示已有卡片");
                    }
//                    try {
//                        FileUtil.copyFile(file.getAbsolutePath(), AppsSettings.get().getDeckDir(), false);
                    du.dis();
                    openYdk(activity, file);
//                    } catch (IOException e) {
//                        OYUtil.show(OYUtil.s(R.string.save_failed_bcos) + e);
//                        e.printStackTrace();
//                    }
                    break;
                case DECK_TYPE_PATH:
                    try {
                        File file1 = new File(FileUtil.copyFile(deckMessage, AppsSettings.get().getDeckDir(), false));
                        du.dis();
                        openYdk(activity, file1);
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
            String name = ydkFile.getName();
            if (name.endsWith(".ydk"))
                name = name.substring(0, name.lastIndexOf("."));
            IntentUtil.startYGODeck(context, name);
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

        new Handler().postDelayed(() -> {
            OYUtil.showKeyboard(et_password);
//            et_password.performClick();
        }, 100);

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

            try {
                Log.e("feihua", "开始："+et_password.getText().toString());
                YGODAUtil.deDeckListener( Uri.parse(et_password.getText().toString().trim()), (uri1, mainList, exList, sideList, isCompleteDeck, exception) -> {
                    Log.e("feihua", "解析结果：" + uri1
                            + " \nmainList: " + mainList.size()
                            + " \nexList: " + exList.size()
                            + " \nsideList: " + sideList.size()
                            + " \nisCompleteDeck: " + isCompleteDeck
                            + " \nexception: " + exception
                    );
                });
            }catch (Exception e){
                Log.e("feihua", "异常：" + e);
            }


//            if (!OYApplication.isIsInitRes()) {
//                OYUtil.show("请等待资源加载完毕后加入游戏");
//                return;
//            }
//            OYUtil.closeKeyboard(dialog);
//            dialog.dismiss();
//            YGOUtil.joinGame(activity, ((RoomSpinnerAdapter) sp_room.getAdapter()).getItem(sp_room.getSelectedItemPosition()), et_password.getText().toString().trim());
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
            String message = serverInfo.toUri(tv_password.getText().toString());
            message = "房间密码：" + tv_password.getText().toString()
                    + "\n点击或复制打开YGO加入决斗：" + message;
            OYUtil.copyMessage(message);
            OYUtil.show(OYUtil.s(R.string.copy_ok));
            du.dis();
        });

        iv_share_qq.setOnClickListener(v14 -> {
            String message = serverInfo.toUri(tv_password.getText().toString());
            message = "房间密码：" + tv_password.getText().toString()
                    + "\n点击或复制打开YGO加入决斗：" + message;
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
            message = "房间密码：" + tv_password.getText().toString()
                    + "\n点击或复制打开YGO加入决斗：" + message;
            OYUtil.copyMessage(message);
            ShareUtil.share(activity, message);
            du.dis();
        });


        iv_close.setOnClickListener(v12 -> du.dis());
    }

    public static void dialogSetBg(Context context, String imagePath, int[] bgList, OnSetBgListener onSetBgListener) {
        DialogUtils dialogUtils = DialogUtils.getInstance(context);
        View dialogView = dialogUtils.dialogBottomSheet(R.layout.set_bg_dialog, true);
        SwitchCompat sc_blur = dialogView.findViewById(R.id.sc_blur);
        CheckBox cb_duel = dialogView.findViewById(R.id.cb_duel);
        CheckBox cb_menu = dialogView.findViewById(R.id.cb_menu);
        CheckBox cb_deck = dialogView.findViewById(R.id.cb_deck);
        ImageView iv_bg = dialogView.findViewById(R.id.iv_bg);
        TextView tv_set = dialogView.findViewById(R.id.tv_set);

//        ImageUtil.(context, imagePath, iv_bg, System.currentTimeMillis() + "");

        List<String> nameList = new ArrayList<>();

        for (int bg : bgList) {
            switch (bg) {
                case BG_TYPE_DUEL:
                    cb_duel.setChecked(true);
                    nameList.add(Constants.CORE_SKIN_BG);
                    break;
                case BG_TYPE_MENU:
                    cb_menu.setChecked(true);
                    nameList.add(Constants.CORE_SKIN_BG_MENU);
                    break;
                case BG_TYPE_DECK:
                    cb_deck.setChecked(true);
                    nameList.add(Constants.CORE_SKIN_BG_DECK);
                    break;
            }
        }
        cb_duel.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cb_duel.setChecked(true);
                nameList.add(Constants.CORE_SKIN_BG);
            } else {
                cb_duel.setChecked(false);
                nameList.remove(Constants.CORE_SKIN_BG);
            }
        });

        cb_menu.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cb_menu.setChecked(true);
                nameList.add(Constants.CORE_SKIN_BG_MENU);
            } else {
                cb_menu.setChecked(false);
                nameList.remove(Constants.CORE_SKIN_BG_MENU);
            }
        });

        cb_deck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cb_deck.setChecked(true);
                nameList.add(Constants.CORE_SKIN_BG_DECK);
            } else {
                cb_deck.setChecked(false);
                nameList.remove(Constants.CORE_SKIN_BG_DECK);
            }
        });

        sc_blur.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                ImageUtil.showBlur(context, imagePath, iv_bg, System.currentTimeMillis() + "");
            } else {
                ImageUtil.show(context, imagePath, iv_bg, System.currentTimeMillis() + "");
            }
        });

        sc_blur.setChecked(true);

        tv_set.setOnClickListener(v -> {
            dialogUtils.dis();
            dialogUtils.dialogj1(null, "设置中，请稍等");
            if (sc_blur.isChecked()) {
                ImageUtil.getBlurImage(context, imagePath, (imagePath1, exception) -> {
                    try {
                        for (String name : nameList) {
                            FileUtils.copyFile(imagePath1, new File(AppsSettings.get().getCoreSkinPath(), name).getAbsolutePath());
                        }
                        dialogUtils.dis();
                        onSetBgListener.onSetBg(null);
                    } catch (IOException e) {
                        dialogUtils.dis();
                        onSetBgListener.onSetBg(e.toString());
                    }
                });
            } else {
                try {
                    for (String name : nameList) {
                        FileUtils.copyFile(imagePath, new File(AppsSettings.get().getCoreSkinPath(), name).getAbsolutePath());
                    }
                    dialogUtils.dis();
                    onSetBgListener.onSetBg(null);
                } catch (IOException e) {
                    dialogUtils.dis();
                    onSetBgListener.onSetBg(e.toString());
                }
            }
        });


    }

    public static void dialogNewDeck(Context context) {
        DialogUtils dialogUtils = DialogUtils.getInstance(context);
        View view = dialogUtils.dialogBottomSheet(R.layout.new_deck_dialog);
        Dialog dialog = dialogUtils.getDialog();
        TextView tv_edit, tv_open_new_deck, tv_title, tv_message;
        ImageView iv_close;
        ImageView iv_image;

        CardBag cardBag = OYUtil.getNewCardBag();

        tv_edit = view.findViewById(R.id.tv_edit);
        iv_close = view.findViewById(R.id.iv_close);
        tv_open_new_deck = view.findViewById(R.id.tv_open_new_deck);
        tv_title = view.findViewById(R.id.tv_title);
        tv_message = view.findViewById(R.id.tv_message);
        iv_image = view.findViewById(R.id.iv_image);

        tv_title.setText(cardBag.getTitle());
        tv_message.setText(cardBag.getMessage());

        iv_close.setOnClickListener(view1 -> dialog.dismiss());

        tv_edit.setOnClickListener(view12 -> {
            dialog.dismiss();
            switch (SharedPreferenceUtil.getDeckEditType()) {
                case SharedPreferenceUtil.DECK_EDIT_TYPE_LOCAL:
                    IntentUtil.startYGODeck((Activity) context);
                    break;
                case SharedPreferenceUtil.DECK_EDIT_TYPE_DECK_MANAGEMENT:
                    context.startActivity(new Intent(context, DeckManagementActivity.class));
                    break;
                case SharedPreferenceUtil.DECK_EDIT_TYPE_OURYGO_EZ:
                    if (OYUtil.isApp(Record.PACKAGE_NAME_EZ))
                        context.startActivity(IntentUtil.getAppIntent(context, Record.PACKAGE_NAME_EZ));
                    else
                        context.startActivity(IntentUtil.getWebIntent(context, "http://ez.ourygo.top/"));
                    break;
            }
        });

        tv_open_new_deck.setOnClickListener(v -> {
            dialog.dismiss();
            IntentUtil.startYGODeck((Activity) context, OYUtil.s(R.string.category_pack), cardBag.getDeckName());
        });

    }

    //自动更新对话框
    public static void dialogUpdate(Context context,UpdateInfo updateInfo){
        if (updateInfo==null)
            return;
        DialogUtils dialogUtils=DialogUtils.getInstance(context);
        View dialogView=dialogUtils.dialogBottomSheet(R.layout.update_dialog);
        ImageView iv_close;
        TextView tv_title,tv_update,tv_code,tv_version,tv_size,tv_message;

        iv_close = dialogView.findViewById(R.id.iv_close);
        tv_title = dialogView.findViewById(R.id.tv_title);
        tv_update = dialogView.findViewById(R.id.tv_update);
        tv_code = dialogView.findViewById(R.id.tv_code);
        tv_version = dialogView.findViewById(R.id.tv_version);
        tv_size = dialogView.findViewById(R.id.tv_size);
        tv_message = dialogView.findViewById(R.id.tv_message);
        String code=updateInfo.getCode();

        tv_title.setText(updateInfo.getTitle());
        tv_message.setText(updateInfo.getMessage());
        tv_version.setText(updateInfo.getVersionName());
        tv_size.setText(OYUtil.getFileSizeText(updateInfo.getSize()));

        if (TextUtils.isEmpty(code)){
            tv_code.setVisibility(View.GONE);
        }else {
            tv_code.setVisibility(View.VISIBLE);
            tv_code.setText("验证码："+code);
        }

        tv_code.setOnClickListener(view -> {
            OYUtil.copyMessage(code);
            OYUtil.show("已复制验证码到剪贴板");
        });

        iv_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialogUtils.dis();
            }
        });

        tv_update.setOnClickListener(view->{
            dialogUtils.dis();
            context.startActivity(IntentUtil.getUrlIntent(updateInfo.getUrl()));
            if (!TextUtils.isEmpty(code)){
                OYUtil.copyMessage(code);
                OYUtil.show("已复制验证码到剪贴板");
            }
        });

    }

}

package cn.garymb.ygomobile.deck_square;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public class MyDeckDetailDialog extends Dialog {

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    //    private String deckId;
//    private String deckName;
//    private Integer userId;
    MyDeckItem item;

    public interface ActionListener {
        void onDownloadClicked();

        void onLikeClicked();
    }

    private ActionListener listener;

    public MyDeckDetailDialog(Context context, MyDeckItem item) {
        super(context);
//        deckId = item.getDeckId();
//        deckName = item.getDeckName();
//        userId = item.getUserId();
        this.item = item;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_square_my_deck_detail);

        Button btnDownload = findViewById(R.id.dialog_my_deck_btn_download);
        Button btnPush = findViewById(R.id.dialog_my_deck_btn_push);

        Button btnLike = findViewById(R.id.btnLike);
        LinearLayout downloadLayout = findViewById(R.id.server_download_layout);
        LinearLayout uploadLayout = findViewById(R.id.server_upload_layout);
        if (item.getDeckSouce() == 0) {//来自本地

            downloadLayout.setVisibility(View.GONE);
            uploadLayout.setVisibility(View.VISIBLE);
            //btnDownload.setBackground(R.id.ic);
        } else if (item.getDeckSouce() == 1) {//来自服务器
            downloadLayout.setVisibility(View.VISIBLE);
            uploadLayout.setVisibility(View.GONE);
        } else if (item.getDeckSouce() == 2) {//本地、服务器均存在
            downloadLayout.setVisibility(View.VISIBLE);
            uploadLayout.setVisibility(View.VISIBLE);
        }

        btnPush.setOnClickListener(v -> {

            Integer userId = SharedPreferenceUtil.getServerUserId();

            if (userId == null) {
                YGOUtil.showTextToast("Please login first!");
                return;
            }
            VUiKit.defer().when(() -> {
                DeckSquareApiUtil.pushDeck(item.getDeckPath(),item.getDeckName(), userId);
            });//.done();
        });


        btnDownload.setOnClickListener(v -> {

            Integer userId = SharedPreferenceUtil.getServerUserId();
            if (userId == null) {
                YGOUtil.showTextToast("Please login first!");
                return;
            }
            VUiKit.defer().when(() -> {

                DownloadDeckResponse response = DeckSquareApiUtil.getDeckById(item.getDeckId());
                if (response != null) {

                    return response.getData();
                } else {
                    return null;
                }

            }).fail((e) -> {
                Log.e(TAG, e + "");
//            if (dialog_read_ex.isShowing()) {//关闭异常
//                try {
//                    dialog_read_ex.dismiss();
//                } catch (Exception ex) {
//
//                }
//            }
                LogUtil.i(TAG, "square deck detail fail");

            }).done((deckData) -> {
                if (deckData != null) {
                    String path = AppsSettings.get().getDeckDir();
                    saveFileToPath(path, item.getDeckName() + ".ydk", deckData.deckYdk);
                    LogUtil.i(TAG, "square deck detail done");

                    YGOUtil.showTextToast(R.string.down_complete);
                }
//            if (dialog_read_ex.isShowing()) {
//                try {
//                    dialog_read_ex.dismiss();
//                } catch (Exception ex) {
//                }
//            }
            });
            dismiss();
        });

        btnLike.setOnClickListener(v -> {

            dismiss();
        });

    }

    public void saveFileToPath(String path, String fileName, String content) {
        try {
            // Create file object
            File file = new File(path, fileName);

            // Create file output stream
            FileOutputStream fos = new FileOutputStream(file);

            // Write content
            fos.write(content.getBytes());
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
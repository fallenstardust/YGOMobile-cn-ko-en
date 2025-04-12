package cn.garymb.ygomobile.deck_square;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Button;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.deck_square.api_response.ApiDeckRecord;
import cn.garymb.ygomobile.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Response;

public class SquareDeckDetailDialog extends Dialog {

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private String deckId;
    private String deckName;
    private Integer userId;

    public interface ActionListener {
        void onDownloadClicked();

        void onLikeClicked();
    }

    private ActionListener listener;

    public SquareDeckDetailDialog(Context context, ApiDeckRecord item) {
        super(context);
        deckId = item.getDeckId();
        deckName = item.getDeckName();
        userId = item.getUserId();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_square_deck_detail);

        Button btnDownload = findViewById(R.id.btnDownload);
        Button btnLike = findViewById(R.id.btnLike);

        btnDownload.setOnClickListener(v -> {
            VUiKit.defer().when(() -> {

                DownloadDeckResponse result = null;
                String url = "http://rarnu.xyz:38383/api/mdpro3/deck/" + deckId;

                Map<String, String> headers = new HashMap<>();

                headers.put("ReqSource", "MDPro3");

                Response response = null;
                try {
                    response = OkhttpUtil.synchronousGet(url, null, headers);
                    String responseBodyString = response.body().string();


                    Gson gson = new Gson();
                    // Convert JSON to Java object using Gson
                    result = gson.fromJson(responseBodyString, DownloadDeckResponse.class);
                    LogUtil.i(TAG, responseBodyString);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (result == null) {
                    return null;
                } else {
                    return result.getData();
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
                    saveFileToPath(path, deckName + ".ydk", deckData.deckYdk);
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
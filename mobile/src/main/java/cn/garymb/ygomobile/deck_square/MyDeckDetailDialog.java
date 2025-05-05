package cn.garymb.ygomobile.deck_square;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.PushDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.DeckPreviewListAdapter;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;

public class MyDeckDetailDialog extends Dialog {

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    DeckPreviewListAdapter mListAdapter;
    private RecyclerView mListView;
    private MyOnlineDeckDetail mMyOnlineDeckDetail = null;

    MyDeckItem mItem = null;//存储触发本dialog的卡组的基本信息

    public MyDeckDetailDialog(Context context, MyDeckItem item) {
        super(context);

        this.mItem = item;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_square_my_deck_detail);

        Button btnDownload = findViewById(R.id.dialog_my_deck_btn_download);
        Button btnPush = findViewById(R.id.dialog_my_deck_btn_push);

        LinearLayout downloadLayout = findViewById(R.id.server_download_layout);
        LinearLayout uploadLayout = findViewById(R.id.server_upload_layout);
        if (mItem.getDeckSouce() == 0) {//来自本地

            downloadLayout.setVisibility(View.GONE);
            uploadLayout.setVisibility(View.VISIBLE);
            //btnDownload.setBackground(R.id.ic);
        } else if (mItem.getDeckSouce() == 1) {//来自服务器
            downloadLayout.setVisibility(View.VISIBLE);
            uploadLayout.setVisibility(View.GONE);
            previewDeckCard();
        } else if (mItem.getDeckSouce() == 2) {//本地、服务器均存在
            downloadLayout.setVisibility(View.VISIBLE);
            uploadLayout.setVisibility(View.VISIBLE);
            previewDeckCard();
        }

        mListAdapter = new DeckPreviewListAdapter(R.layout.item_square_deck_card_preview);

        mListView = findViewById(R.id.dialog_my_deck_detail_list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(linearLayoutManager);
        mListView.setAdapter(mListAdapter);

        //上传卡组
        btnPush.setOnClickListener(v -> {

            Integer userId = SharedPreferenceUtil.getServerUserId();
            String serverToken = SharedPreferenceUtil.getServerToken();//todo serverToken要外部传入还是此处获取？考虑
            if (userId == null || serverToken == null) {
                YGOUtil.showTextToast("Please login first!");
                return;
            }

            VUiKit.defer().when(() -> {
                PushDeckResponse result = DeckSquareApiUtil.pushDeck(mItem.getDeckPath(), mItem.getDeckName(), userId, serverToken);
                return result;
            }).fail(e -> {

                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }).done(data -> {
                if (data.isData()) {
                    YGOUtil.showTextToast("push success!");
                } else {

                    YGOUtil.showTextToast(data.getMessage());
                }
            });
            //todo，改造pushDeck，传入回调，得到推送的http响应
        });

        //下载用户在平台上的卡组
        btnDownload.setOnClickListener(v -> {

            if (mMyOnlineDeckDetail != null) {
                String path = AppsSettings.get().getDeckDir();
                DeckSquareFileUtil.saveFileToPath(path, mMyOnlineDeckDetail.getDeckName() + ".ydk", mMyOnlineDeckDetail.getDeckYdk());
            }
        });


    }

    private void previewDeckCard() {
        Integer userId = SharedPreferenceUtil.getServerUserId();
        if (userId == null) {
            YGOUtil.showTextToast("Please login first!");
            return;
        }
        VUiKit.defer().when(() -> {

            DownloadDeckResponse response = DeckSquareApiUtil.getDeckById(mItem.getDeckId());
            if (response != null) {
                return response.getData();
            } else {
                return null;
            }

        }).fail((e) -> {

            LogUtil.i(TAG, "square deck detail fail" + e.getMessage());

        }).done((deckData) -> {
            if (deckData != null) {
                mMyOnlineDeckDetail = deckData;

                LogUtil.i(TAG, "square deck detail done");

                List<Card> cardList = DeckSquareFileUtil.convertTempDeckYdk(deckData.getDeckYdk());
                mListAdapter.updateData(cardList);
            }
        });

    }


}
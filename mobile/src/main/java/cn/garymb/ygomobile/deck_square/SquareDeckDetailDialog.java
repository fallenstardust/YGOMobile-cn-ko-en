package cn.garymb.ygomobile.deck_square;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.deck_square.api_response.ApiDeckRecord;
import cn.garymb.ygomobile.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.deck_square.api_response.DeckDetail;
import cn.garymb.ygomobile.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.DeckPreviewListAdapter;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;

public class SquareDeckDetailDialog extends Dialog {

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    DeckPreviewListAdapter mListAdapter;
    private RecyclerView mListView;
    private DeckDetail mDeckDetail = null;

    private ApiDeckRecord mItem = null;

    public SquareDeckDetailDialog(Context context, ApiDeckRecord item) {
        super(context);
        mItem = item;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_square_deck_detail);

        Button btnDownload = findViewById(R.id.dialog_square_deck_btn_download);
        Button btnLike = findViewById(R.id.btnLike);

        previewDeckCard();

        mListAdapter = new DeckPreviewListAdapter(R.layout.item_square_deck_card_preview);

        mListView = findViewById(R.id.dialog_square_deck_detail_list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(linearLayoutManager);
        mListView.setAdapter(mListAdapter);

        //下载卡组广场的卡组
        btnDownload.setOnClickListener(v -> {
            if (mDeckDetail != null) {
                String path = AppsSettings.get().getDeckDir();
                boolean result = DeckSquareFileUtil.saveFileToPath(path, mDeckDetail.getDeckName() + ".ydk", mDeckDetail.getDeckYdk());
                if (result) {

                    YGOUtil.showTextToast("Download deck success!");
                } else {

                    YGOUtil.showTextToast("Download deck fail!");
                }
            }
        });

        //给卡组点赞
        btnLike.setOnClickListener(v -> {
            VUiKit.defer().when(() -> {
                BasicResponse result = DeckSquareApiUtil.likeDeck(mItem.getDeckId());
                return result;
            }).fail(e -> {

                LogUtil.i(TAG, "Like deck fail" + e.getMessage());
            }).done(data -> {
                if (data.getMessage().equals("true")) {
                    YGOUtil.showTextToast("Like deck success!");
                } else {
                    YGOUtil.showTextToast(data.getMessage());
                }
            });
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
                mDeckDetail = deckData;

                LogUtil.i(TAG, "square deck detail done");

                List<Card> cardList = DeckSquareFileUtil.convertTempDeckYdk(deckData.getDeckYdk());
                mListAdapter.updateData(cardList);
            }
        });

    }


}
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

import cn.garymb.ygomobile.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.deck_square.api_response.MyOnlineDeckDetail;
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

        Button deleteMyOnlineDeckBtn = findViewById(R.id.delete_my_online_deck_btn);
        Button btnPush = findViewById(R.id.dialog_my_deck_btn_push);

        LinearLayout deleteMyDeck = findViewById(R.id.delete_my_deck);
        LinearLayout uploadLayout = findViewById(R.id.server_upload_layout);
        if (mItem.getDeckSouce() == 0) {//来自本地

            deleteMyDeck.setVisibility(View.GONE);
            uploadLayout.setVisibility(View.VISIBLE);
            //btnDownload.setBackground(R.id.ic);
        } else if (mItem.getDeckSouce() == 1) {//来自服务器
            deleteMyDeck.setVisibility(View.VISIBLE);
            uploadLayout.setVisibility(View.GONE);
            previewDeckCard();
        } else if (mItem.getDeckSouce() == 2) {//本地、服务器均存在
            deleteMyDeck.setVisibility(View.VISIBLE);
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

            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;
            }

            VUiKit.defer().when(() -> {
                PushDeckResponse result = DeckSquareApiUtil.requestIdAndPushDeck(mItem.getDeckPath(), mItem.getDeckName(), loginToken);
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
        deleteMyOnlineDeckBtn.setOnClickListener(v -> {

            if (mItem != null) {
                mItem.getDeckId();
                LoginToken loginToken = DeckSquareApiUtil.getLoginData();
                if (loginToken == null) {
                    return;

                }

                VUiKit.defer().when(() -> {
                    PushDeckResponse result = DeckSquareApiUtil.deleteDeck(mItem.getDeckId(), loginToken);
                    return result;
                }).fail(e -> {

                    LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
                }).done(data -> {
                    if (data.isData()) {
                        //服务器的api有问题：获取指定用户的卡组列表(无已删卡组)
                        //删除成功后，通过http://rarnu.xyz:38383/api/mdpro3/sync/795610/nodel接口查询用户卡组时
                        //要等待2~3秒api响应内容才会对应更新
                        YGOUtil.showTextToast("删除成功，3秒后服务器将完成同步");
                        dismiss();
                    } else {

                        YGOUtil.showTextToast("delete fail " + data.getMessage());
                    }
                });
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
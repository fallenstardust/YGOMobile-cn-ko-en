package cn.garymb.ygomobile.deck_square;


import android.util.Log;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;

//提供“我的”卡组数据，打开后先从sharePreference查询，没有则从服务器查询，然后缓存到sharePreference
public class MyDeckListAdapter extends BaseQuickAdapter<MyDeckItem, BaseViewHolder> {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private ImageLoader imageLoader;

    public MyDeckListAdapter(int layoutResId) {
        super(layoutResId);

        imageLoader = new ImageLoader();
    }

    public void loadData() {
        List<MyDeckItem> localDecks = new ArrayList<>();

        // final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_ex_card));


        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if(loginToken==null){
            return;
        }

        VUiKit.defer().when(() -> {

            MyDeckResponse result = DeckSquareApiUtil.getUserDecks(loginToken);

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
            LogUtil.i(TAG, "load mycard from server fail");
            //只展示本地卡组
            getData().clear();
            addData(localDecks);
            notifyDataSetChanged();
        }).done((serverDecks) -> {
            //  List<MyDeckItem> serverItems = new ArrayList<>();

            if (serverDecks != null) {//将服务端的卡组也放到LocalDecks中
                for (MyOnlineDeckDetail detail : serverDecks) {
                    MyDeckItem item = new MyDeckItem();
                    item.setDeckName(detail.getDeckName());
                    item.setDeckSouce(1);
                    item.setDeckId(detail.getDeckId());
                    item.setUserId(detail.getUserId());
                    item.setUpdateDate(detail.getDeckUpdateDate());
                    localDecks.add(item);
                }
            }

            LogUtil.i(TAG, "load mycard from server done");

            //展示本地卡组和服务器上的卡组
            getData().clear();
            addData(localDecks);
            notifyDataSetChanged();

//            if (dialog_read_ex.isShowing()) {
//                try {
//                    dialog_read_ex.dismiss();
//                } catch (Exception ex) {
//                }
//            }
        });

    }

    private static Boolean isMonster(List<String> list) {
        for (String data : list) {
            if (data.equals("怪兽")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void convert(BaseViewHolder helper, MyDeckItem item) {
        helper.setText(R.id.my_deck_name, item.getDeckName());
        //helper.setText(R.id.deck_upload_date, item.getDeckUploadDate());
        ImageView imageView = helper.getView(R.id.deck_upload_state_img);
        if (item.getDeckSouce() == 0) {//本地
            helper.setText(R.id.my_deck_id, "本地卡组");
            imageView.setImageResource(R.drawable.ic_server_push);
            helper.setVisible(R.id.deck_update_date, false);
            helper.setVisible(R.id.deck_upload_date, false);
        } else if (item.getDeckSouce() == 1) {
            helper.setText(R.id.my_deck_id, item.getDeckId());
            imageView.setImageResource(R.drawable.ic_server_download);
            helper.setText(R.id.deck_update_date, item.getUpdateDate());
        }


//        long code = item.getDeckCoverCard1();
//        LogUtil.i(TAG, code + " " + item.getDeckName());
//        if (code != 0) {
//            imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.small);
//        }
//

        // ImageView imageview = helper.getView(R.id.ex_card_image);
        //the function cn.garymb.ygomobile.loader.ImageLoader.bindT(...)
        //cn.garymb.ygomobile.loader.ImageLoader.setDefaults(...)
        //is a private function,so I copied the content of it to here
        /* 如果查不到版本号，则不显示图片 */
        /* 如果能查到版本号，则显示图片，利用glide的signature，将版本号和url作为signature，由glide判断是否使用缓存 */


    }

}
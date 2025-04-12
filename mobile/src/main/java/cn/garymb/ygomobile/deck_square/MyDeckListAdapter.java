package cn.garymb.ygomobile.deck_square;


import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Response;

//提供“我的”卡组数据，打开后先从sharePreference查询，没有则从服务器查询，然后缓存到sharePreference
public class MyDeckListAdapter extends BaseQuickAdapter<MyDeckResponse.MyDeckData, BaseViewHolder> {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private ImageLoader imageLoader;

    public MyDeckListAdapter(int layoutResId) {
        super(layoutResId);

        imageLoader = new ImageLoader();
    }

    public void loadData() {
        // final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_ex_card));
        String serverToken = SharedPreferenceUtil.getServerToken();
        Integer serverUserId = SharedPreferenceUtil.getServerUserId();
        if (serverToken == null) {
            YGOUtil.showTextToast("Login first", Toast.LENGTH_LONG);

            return;
        }

        VUiKit.defer().when(() -> {

            LogUtil.d(TAG, "start fetch" + serverToken + " " + serverUserId);
            MyDeckResponse result = null;
            try {
                String url = "http://rarnu.xyz:38383/api/mdpro3/sync/" + serverUserId + "/nodel";

                Map<String, String> headers = new HashMap<>();

                headers.put("ReqSource", "MDPro3");
                headers.put("token", serverToken);

                Response response = OkhttpUtil.synchronousGet(url, null, headers);
                String responseBodyString = response.body().string();
//                Type listType = new TypeToken<List<DeckInfo>>() {
//                }.getType();
                Gson gson = new Gson();
                // Convert JSON to Java object using Gson
                result = gson.fromJson(responseBodyString, MyDeckResponse.class);
                LogUtil.i(TAG, responseBodyString);
                int a = 0;
            } catch (IOException e) {
                Log.e(TAG, "Error occured when fetching data from pre-card server");
                return null;
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
            LogUtil.i(TAG, "webCrawler fail");

        }).done((exCardDataList) -> {
            if (exCardDataList != null) {
                LogUtil.i(TAG, "webCrawler done");
                getData().clear();
                addData(exCardDataList);
                notifyDataSetChanged();
            }
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
    protected void convert(BaseViewHolder helper, MyDeckResponse.MyDeckData item) {
        helper.setText(R.id.deck_info_name, item.getDeckName());
        helper.setText(R.id.deck_contributor, item.getDeckContributor());
        ImageView cardImage = helper.getView(R.id.deck_info_image);
        long code = item.getDeckCoverCard1();
        LogUtil.i(TAG, code + " " + item.getDeckName());
        if (code != 0) {
            imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.small);
        }
        // ImageView imageview = helper.getView(R.id.ex_card_image);
        //the function cn.garymb.ygomobile.loader.ImageLoader.bindT(...)
        //cn.garymb.ygomobile.loader.ImageLoader.setDefaults(...)
        //is a private function,so I copied the content of it to here
        /* 如果查不到版本号，则不显示图片 */
        /* 如果能查到版本号，则显示图片，利用glide的signature，将版本号和url作为signature，由glide判断是否使用缓存 */


    }

}
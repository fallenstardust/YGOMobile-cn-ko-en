package cn.garymb.ygomobile.deck_square;

import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.deck_square.api_response.DeckIdResponse;
import cn.garymb.ygomobile.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DeckSquareApiUtil {

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    //获取指定用户的卡组列表
    public static MyDeckResponse getUserDecks(Integer serverUserId, String serverToken) {

        if (serverToken == null) {
            YGOUtil.showTextToast("Login first", Toast.LENGTH_LONG);

            return null;
        }
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (result.code == 20) {//用户身份验证失败

            YGOUtil.showTextToast("Login first", Toast.LENGTH_LONG);

        }
        return result;
    }

    //根据卡组ID查询一个卡组
    public static DownloadDeckResponse getDeckById(String deckId) {
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

        return result;

    }

    //首先获取卡组id，之后将卡组id设置到ydk中，之后将其上传
    public static void pushDeck(String deckPath, Integer userId) {
        String url = "http://rarnu.xyz:38383/api/mdpro3/sync/single";
        String getDeckIdUrl = "http://rarnu.xyz:38383/api/mdpro3/deck/deckId";

        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "MDPro3");

        try {

            DeckIdResponse deckIdResult = null;
            {
                Response response = OkhttpUtil.synchronousGet(getDeckIdUrl, null, headers);
                String responseBodyString = response.body().string();
                Gson gson = new Gson();
                // Convert JSON to Java object using Gson
                deckIdResult = gson.fromJson(responseBodyString, DeckIdResponse.class);
            }


            String deckId = deckIdResult.getDeckId();//从服务器获取

            DeckSquareFileUtil.setDeckId(deckPath, userId, deckId);

            //todo 构造卡组的json
            OkhttpUtil.postJson(url, null, headers, 1000, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                    LogUtil.i(TAG, "push deck fail");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                    LogUtil.i(TAG, "push deck success");
                }
            });


            Gson gson = new Gson();
            // Convert JSON to Java object using Gson
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
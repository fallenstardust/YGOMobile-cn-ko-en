package cn.garymb.ygomobile.deck_square;

import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.deck_square.api_response.DeckIdResponse;
import cn.garymb.ygomobile.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.LoginRequest;
import cn.garymb.ygomobile.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.PushCardJson;
import cn.garymb.ygomobile.deck_square.api_response.PushDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.SquareDeckResponse;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DeckSquareApiUtil {

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    public static SquareDeckResponse getSquareDecks() throws IOException {
        SquareDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/list";
        Map<String, String> headers = new HashMap<>();

        headers.put("ReqSource", "MDPro3");
        Response response = OkhttpUtil.synchronousGet(url, null, headers);
        String responseBodyString = response.body().string();
//                Type listType = new TypeToken<List<DeckInfo>>() {
//                }.getType();
        Gson gson = new Gson();
        // Convert JSON to Java object using Gson
        result = gson.fromJson(responseBodyString, SquareDeckResponse.class);
        return result;
    }

    /**
     * 阻塞方法
     * 获取指定用户的卡组列表（只能用于获取登录用户本人的卡组）
     *
     * @param serverUserId
     * @param serverToken
     * @return
     */
    public static MyDeckResponse getUserDecks(Integer serverUserId, String serverToken) throws IOException {

        if (serverToken == null) {
            YGOUtil.showTextToast("Login first", Toast.LENGTH_LONG);

            return null;
        }
        MyDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/sync/" + serverUserId + "/nodel";

        Map<String, String> headers = new HashMap<>();

        headers.put("ReqSource", "MDPro3");
        headers.put("token", serverToken);

        Response response = OkhttpUtil.synchronousGet(url, null, headers);
        String responseBodyString = response.body().string();
        Gson gson = new Gson();
        result = gson.fromJson(responseBodyString, MyDeckResponse.class);


        if (result.code == 20) {//用户身份验证失败
            YGOUtil.showTextToast("Login first", Toast.LENGTH_LONG);
        }
        return result;
    }

    /**
     * 阻塞方法
     * 根据卡组ID查询一个卡组
     *
     * @param deckId
     * @return
     */
    public static DownloadDeckResponse getDeckById(String deckId) throws IOException {
        DownloadDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/" + deckId;

        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "MDPro3");

        Response response = OkhttpUtil.synchronousGet(url, null, headers);
        String responseBodyString = response.body().string();


        Gson gson = new Gson();
        // Convert JSON to Java object using Gson
        result = gson.fromJson(responseBodyString, DownloadDeckResponse.class);
        LogUtil.i(TAG, responseBodyString);


        return result;

    }


    /**
     * 阻塞方法
     * 先同步推送，之后异步推送。首先获取卡组id，之后将卡组id设置到ydk中，之后将其上传
     *
     * @param deckPath
     * @param deckName
     * @param userId
     */
    public static PushDeckResponse pushDeck(String deckPath, String deckName, Integer userId, String serverToken) throws IOException {
        PushDeckResponse result = null;

        if (serverToken == null) {
            return null;
        }

        String url = "http://rarnu.xyz:38383/api/mdpro3/sync/single";
        String getDeckIdUrl = "http://rarnu.xyz:38383/api/mdpro3/deck/deckId";

        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "MDPro3");
        headers.put("token", serverToken);

        Gson gson = new Gson();

        DeckIdResponse deckIdResult = null;
        {
            Response response = OkhttpUtil.synchronousGet(getDeckIdUrl, null, headers);
            String responseBodyString = response.body().string();
            // Convert JSON to Java object using Gson
            deckIdResult = gson.fromJson(responseBodyString, DeckIdResponse.class);
            LogUtil.i(TAG, "deck id result:" + deckIdResult.toString());
        }


        String deckId = deckIdResult.getDeckId();//从服务器获取

        String deckContent = DeckSquareFileUtil.setDeckId(deckPath, userId, deckId);

        PushCardJson pushCardJson = new PushCardJson();
        pushCardJson.setDeckContributor(userId.toString());
        pushCardJson.setUserId(userId);
        PushCardJson.DeckData deckData = new PushCardJson.DeckData();

        deckData.setDeckId(deckId);
        deckData.setDeckName(deckName);
        deckData.setDelete(false);
        deckData.setDeckYdk(deckContent);
        pushCardJson.setDeck(deckData);

        String json = gson.toJson(pushCardJson);


        Response response = OkhttpUtil.postJson(url, json, headers, 1000);
        String responseBodyString = response.body().string();

        // Convert JSON to Java object using Gson
        result = gson.fromJson(responseBodyString, PushDeckResponse.class);
        LogUtil.i(TAG, "push deck response:" + responseBodyString);


        return result;

    }

    /**
     * 异步方法，给卡组点赞
     *
     * @param deckId
     */
    public static BasicResponse likeDeck(String deckId) throws IOException {

        BasicResponse result = null;

        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/like/" + deckId;
        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "MDPro3");
        Response response = OkhttpUtil.postJson(url, null, headers, 1000);
        String responseBodyString = response.body().string();

        Gson gson = new Gson();
        result = gson.fromJson(responseBodyString, BasicResponse.class);
        LogUtil.i(TAG, responseBodyString);

        return result;


    }

    public static LoginResponse login(String userId, String password) throws IOException {
        LoginResponse result = null;

        String url = "https://sapi.moecube.com:444/accounts/signin";
        String baseUrl = "https://sapi.moecube.com:444/accounts/signin";
        // Create request body using Gson
        Gson gson = new Gson();
        userId = "1076306278@qq.com";
        password = "Qbz95qbz96";
        LoginRequest loginRequest = new LoginRequest(userId, password);

        String json = gson.toJson(loginRequest);//"{\"id\":1,\"name\":\"John\"}";

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient();
        Response response = okHttpClient.newCall(request).execute();

        // Read the response body
        String responseBody = response.body().string();
        LogUtil.i(TAG, "Login Response body: " + responseBody);

        // Process the response
        if (response.isSuccessful()) {
            // Successful response (code 200-299)
            // Parse the JSON response if needed
            result = gson.fromJson(responseBody, LoginResponse.class);
            LogUtil.i(TAG, "Login response: " + result);
        } else {
            // Error response
            LogUtil.e(TAG, "Request failed: " + responseBody);
        }


        return result;

    }

}
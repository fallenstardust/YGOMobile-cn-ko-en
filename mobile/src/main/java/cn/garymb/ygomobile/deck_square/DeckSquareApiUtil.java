package cn.garymb.ygomobile.deck_square;

import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Response;

public class DeckSquareApiUtil {

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

        if(result.code == 20){//用户身份验证失败

            YGOUtil.showTextToast("Login first", Toast.LENGTH_LONG);

        }
        return result;
    }

}
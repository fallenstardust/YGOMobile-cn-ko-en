package cn.garymb.ygomobile.ui.cards.deck_square;

import android.widget.Toast;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.DeckManagerFragment;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.DeckIdResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.DeckMultiIdResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.GetSquareDeckCondition;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginRequest;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushDeckPublicState;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushMultiDeck;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushMultiResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushSingleDeck;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushSingleDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.SquareDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.bo.MyDeckItem;
import cn.garymb.ygomobile.ui.cards.deck_square.bo.SyncMutliDeckResult;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DeckSquareApiUtil {

    private static final String TAG = "DeckSquareApiUtil";


    public static boolean needLogin() {
        String serverToken = SharedPreferenceUtil.getServerToken();
        Integer serverUserId = SharedPreferenceUtil.getServerUserId();

        if (serverToken == null || serverUserId == -1) {
            return true;
        }
        return false;
    }

    /**
     * 如果未登录（不存在token），显示toast提示用户。如果已登录，返回token
     *
     * @return
     */
    public static LoginToken getLoginData() {
        String serverToken = SharedPreferenceUtil.getServerToken();
        Integer serverUserId = SharedPreferenceUtil.getServerUserId();

        if (serverToken == null || serverUserId == -1) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return null;
        }
        return new LoginToken(serverUserId, serverToken);

    }


    /**
     * 根据条件，分页查询卡组的列表（不查询卡组的内容，只查询卡组名、卡组id等概括性信息）
     *
     * @param condition
     * @return
     * @throws IOException
     */
    public static SquareDeckResponse getSquareDecks(GetSquareDeckCondition condition) throws IOException {

        SquareDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/list";
        Map<String, String> headers = new HashMap<>();

        headers.put("ReqSource", "YGOMobile");

        Map<String, Object> paramMap = new HashMap<>();

        paramMap.put("page", condition.getPage());
        paramMap.put("size", condition.getSize());
        paramMap.put("keyWord", condition.getKeyWord());
        paramMap.put("sortLike", condition.getSortLike());
        paramMap.put("sortRank", condition.getSortRank());
        paramMap.put("contributor", condition.getContributor());
        Response response = OkhttpUtil.synchronousGet(url, paramMap, headers);
        String responseBodyString = response.body().string();

        Gson gson = new Gson();
        result = gson.fromJson(responseBodyString, SquareDeckResponse.class);
        return result;
    }

    /**
     * 阻塞方法
     * 获取指定用户的卡组列表（只能用于获取登录用户本人的卡组）
     *
     * @param loginToken
     * @return
     */
    public static MyDeckResponse getUserDecks(LoginToken loginToken) throws IOException {

        if (loginToken == null) {
            YGOUtil.showTextToast("Login first", Toast.LENGTH_LONG);

            return null;
        }
        MyDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/sync/" + loginToken.getUserId() + "/nodel";

        Map<String, String> headers = new HashMap<>();

        headers.put("ReqSource", "YGOMobile");
        headers.put("token", loginToken.getServerToken());

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
     * 根据卡组ID查询一个卡组的内容，不需要传入token，可以查询已登录用户或其它未登录用户的卡组
     *
     * @param deckId
     * @return
     */
    public static DownloadDeckResponse getDeckById(String deckId) throws IOException {
        DownloadDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/" + deckId;

        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");

        Response response = OkhttpUtil.synchronousGet(url, null, headers);
        String responseBodyString = response.body().string();


        Gson gson = new Gson();
        // Convert JSON to Java object using Gson
        result = gson.fromJson(responseBodyString, DownloadDeckResponse.class);
        LogUtil.i(TAG, responseBodyString);


        return result;

    }


    /**
     * 阻塞方法，将对应于deckId、deckName的卡组内容json推送到服务器。
     * 如果在服务器存在deckId相同的记录，则更新卡组，deckName会覆盖服务器上的卡组名
     * 如果在服务器存在deckName相同、deckId不同的记录，则更新失败
     *
     * @param deckfile
     * @param loginToken
     * @param deckId
     * @return
     * @throws IOException
     */
    private static PushSingleDeckResponse pushDeck(DeckFile deckfile, LoginToken loginToken, String deckId) throws IOException {
        String deckContent = DeckSquareFileUtil.setDeckId(deckfile.getPath(), loginToken.getUserId(), deckId);

        PushSingleDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/sync/single";
        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");
        headers.put("token", loginToken.getServerToken());


        Gson gson = new Gson();
        PushSingleDeck pushSingleDeck = new PushSingleDeck();
        pushSingleDeck.setDeckContributor(SharedPreferenceUtil.getMyCardUserName());
        pushSingleDeck.setUserId(loginToken.getUserId());
        PushSingleDeck.DeckData deckData = new PushSingleDeck.DeckData();

        deckData.setDeckId(deckId);
        deckData.setDeckName(deckfile.getName());
        deckData.setDeckCoverCard1(deckfile.getFirstCode());
        deckData.setDelete(false);
        deckData.setDeckYdk(deckContent);
        pushSingleDeck.setDeck(deckData);

        String json = gson.toJson(pushSingleDeck);
        Response response = OkhttpUtil.postJson(url, json, headers);
        String responseBodyString = response.body().string();

        result = gson.fromJson(responseBodyString, PushSingleDeckResponse.class);
        LogUtil.i(TAG, "push deck response:" + responseBodyString);

        return result;
    }

    /**
     * 阻塞方法，将对应于deckDataList、deckIdList的卡组内容json推送到服务器。
     * 如果在服务器存在deckId相同的记录，则更新卡组，deckName会覆盖服务器上的卡组名
     * 如果在服务器存在deckName相同、deckId不同的记录，则更新失败
     *
     * @param deckDataList
     * @param loginToken
     * @param deckIdList
     * @return
     * @throws IOException
     */
    private static PushMultiResponse pushDecks(List<MyDeckItem> deckDataList, LoginToken loginToken, List<String> deckIdList) throws IOException {
        List<PushMultiDeck.DeckData> decks = new ArrayList<>();
        if (deckDataList.size() != deckIdList.size()) {
            return null;
        }
        for (int i = 0; i < deckDataList.size(); i++) {
            MyDeckItem myDeckItem = deckDataList.get(i);
            String deckContent = DeckSquareFileUtil.setDeckId(myDeckItem.getDeckPath(), loginToken.getUserId(), deckIdList.get(i));
            PushMultiDeck.DeckData data = new PushMultiDeck.DeckData();
            data.setDeckYdk(deckContent);
            data.setDeckName(myDeckItem.getDeckName());
            data.setDeckCoverCard1(myDeckItem.getDeckCoverCard1());
            data.setDeckId(deckIdList.get(i));

            decks.add(data);
        }
        return pushMultiDecks(decks, loginToken);

    }


    /**
     * 阻塞方法，推送新卡组的内容时使用。首先从服务器请求一个新的卡组id，之后将卡组上传到服务器
     * 首先调用服务端api获取卡组id，之后将卡组id设置到ydk中，之后调用服务器api将卡组上传
     *
     * @param deckFile
     * @param loginToken
     */
    public static PushSingleDeckResponse requestIdAndPushNewDeck(DeckFile deckFile, LoginToken loginToken) throws IOException {

        if (loginToken == null) {
            return null;
        }

        String getDeckIdUrl = "http://rarnu.xyz:38383/api/mdpro3/deck/deckId";

        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");
        headers.put("token", loginToken.getServerToken());

        Gson gson = new Gson();

        DeckIdResponse deckIdResult = null;

        Response response = OkhttpUtil.synchronousGet(getDeckIdUrl, null, headers);
        String responseBodyString = response.body().string();
        // Convert JSON to Java object using Gson
        deckIdResult = gson.fromJson(responseBodyString, DeckIdResponse.class);
        LogUtil.i(TAG, "deck id result:" + deckIdResult.toString());


        if (deckIdResult == null) {
            return null;
        }
        String deckId = deckIdResult.getDeckId();//从服务器获取
        if (deckId == null) {
            return null;
        }

        return pushDeck(deckFile, loginToken, deckId);

    }

    /**
     * 阻塞方法，推送新卡组的内容时使用。首先从服务器请求一个新的卡组id，之后将卡组上传到服务器
     * 首先调用服务端api获取卡组id，之后将卡组id设置到ydk中，之后调用服务器api将卡组上传
     * 首先获取卡组id，之后上传新卡组
     *
     * @param deckDataList
     * @param loginToken
     * @return
     * @throws IOException
     */
    public static PushMultiResponse requestIdAndPushNewDecks(List<MyDeckItem> deckDataList, LoginToken loginToken) throws IOException {
        if (loginToken == null) {
            return null;
        }
        if (deckDataList == null || deckDataList.isEmpty()) {
            return null;
        }
        Gson gson = new Gson();
        String getDeckIdUrl = "http://rarnu.xyz:38383/api/mdpro3/deck/deckIds";


        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");
        headers.put("token", loginToken.getServerToken());

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("count", deckDataList.size());
        Response response = OkhttpUtil.synchronousGet(getDeckIdUrl, paramMap, headers);
        DeckMultiIdResponse deckIdResult = gson.fromJson(response.body().string(), DeckMultiIdResponse.class);


        if (deckIdResult == null) {
            return null;
        }
        List<String> deckId = deckIdResult.getDeckId();//从服务器获取
        if (deckId == null) {
            return null;
        }

        return pushDecks(deckDataList, loginToken, deckId);
    }

    /**
     * 批量上传已经在云上存在的卡组
     *
     * @param deckItems
     * @param loginToken
     * @return
     * @throws IOException
     */
    public static PushMultiResponse syncMyDecks(List<MyDeckItem> deckItems, LoginToken loginToken) throws IOException {
        if (deckItems == null || deckItems.isEmpty()) {
            return null;
        }
        /* 构造json */
        List<PushMultiDeck.DeckData> dataList = new ArrayList<>();
        for (MyDeckItem item : deckItems) {
            PushMultiDeck.DeckData data = new PushMultiDeck.DeckData();
            data.setDeckId(item.getDeckId());
            data.setDeckName(item.getDeckName());
            data.setDeckCoverCard1(item.getDeckCoverCard1());

            String deckContent = DeckSquareFileUtil.setDeckId(item.getDeckPath(), loginToken.getUserId(), item.getDeckId());

            data.setDeckYdk(deckContent);
            LogUtil.w(TAG, "seesee syncMyDecks*要上传的 本地卡组: " + data.getDeckName()+"//"+data.getDeckId()+"//"+data.getDeckCoverCard1()+"//"+data.getDeckYdk());
            dataList.add(data);
        }
        return pushMultiDecks(dataList, loginToken);
    }

    public static PushMultiResponse pushMultiDecks(List<PushMultiDeck.DeckData> dataList, LoginToken loginToken) throws IOException {
        if (dataList.isEmpty()) {
            return null;
        }
        String url = "http://rarnu.xyz:38383/api/mdpro3/sync/multi";
        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");
        headers.put("token", loginToken.getServerToken());

        PushMultiResponse result = null;

        Gson gson = new Gson();
        PushMultiDeck pushMultiDeck = new PushMultiDeck();
        pushMultiDeck.setDeckContributor(SharedPreferenceUtil.getMyCardUserName());
        pushMultiDeck.setUserId(loginToken.getUserId());
        pushMultiDeck.setDecks(dataList);

        String json = gson.toJson(pushMultiDeck);
        Response response = OkhttpUtil.postJson(url, json, headers);
        String responseBodyString = response.body().string();

        result = gson.fromJson(responseBodyString, PushMultiResponse.class);
        LogUtil.i(TAG, "pushMultiDecks response:" + responseBodyString);

        return result;
    }

    /**
     * 阻塞方法，给卡组点赞
     *
     * @param deckId
     */
    public static BasicResponse likeDeck(String deckId) throws IOException {

        BasicResponse result = null;

        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/like/" + deckId;
        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");
        Response response = OkhttpUtil.postJson(url, null, headers);
        String responseBodyString = response.body().string();

        Gson gson = new Gson();
        result = gson.fromJson(responseBodyString, BasicResponse.class);
        LogUtil.i(TAG, responseBodyString);

        return result;


    }

    /**
     * 阻塞方法，给卡组点赞
     *
     * @param deckId
     */
    public static BasicResponse setDeckPublic(String deckId, LoginToken loginToken, boolean publicState) throws IOException {
        BasicResponse result = null;

        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/public";
        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");
        headers.put("token", loginToken.getServerToken());

        Gson gson = new Gson();
        PushDeckPublicState pushData = new PushDeckPublicState();
        pushData.setPublic(publicState);
        pushData.setDeckId(deckId);
        pushData.setUserId(loginToken.getUserId());

        String json = gson.toJson(pushData);


        Response response = OkhttpUtil.postJson(url, json, headers);
        String responseBodyString = response.body().string();

        result = gson.fromJson(responseBodyString, BasicResponse.class);
        LogUtil.i(TAG, responseBodyString);

        return result;


    }

    public static LoginResponse login(String username, String password) throws IOException {
        LoginResponse result = null;

        String url = "https://sapi.moecube.com:444/accounts/signin";
        // Create request body using Gson
        Gson gson = new Gson();
//        userId = 107630627;
//        password = "Qbz95qbz96";
        LoginRequest loginRequest = new LoginRequest(username, password);

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

    public static void deleteDecks(List<DeckFile> deckFileList) {
        if (SharedPreferenceUtil.getServerToken() != null) {
            LoginToken loginToken = new LoginToken(
                    SharedPreferenceUtil.getServerUserId(),
                    SharedPreferenceUtil.getServerToken()
            );

            // 创建一个局部变量来持有deckFileList的引用,因为有时候异步执行会导致获取不到传参的deckFileList
            final List<DeckFile> localDeckFileList = new ArrayList<>(deckFileList);

            // 获取在线卡组列表（异步处理）
            VUiKit.defer().when(() -> {
                return DeckSquareApiUtil.getUserDecks(loginToken);
            }).fail((e) -> {
                LogUtil.e(TAG, "getUserDecks failed: " + e);
            }).done((result) -> {
                if (result == null || result.getData() == null) {
                    return;
                }
                List<MyOnlineDeckDetail> onlineDecks = result.getData();
                for (MyOnlineDeckDetail onlineDeck : onlineDecks) {
                    for (DeckFile deckFile : localDeckFileList) {
                        if (onlineDeck.getDeckName().equals(deckFile.getName())) {
                            // 删除在线卡组（异步处理）
                            VUiKit.defer().when(() -> {
                                PushSingleDeckResponse deckResponse = DeckSquareApiUtil.deleteDeck(onlineDeck.getDeckId(), loginToken);
                                return deckResponse;
                            }).fail((deleteError) -> {
                                LogUtil.e(TAG, "Delete Online Deck failed: " + deleteError);
                            }).done((deleteSuccess) -> {
                                if (deleteSuccess.isData()) {
                                    LogUtil.i(TAG, "Online deck deleted successfully");
                                }
                            });
                            break;
                        }
                    }
                }
            });
        }
    }

    public static PushSingleDeckResponse deleteDeck(String deckId, LoginToken loginToken) throws
            IOException {
        PushSingleDeckResponse result = null;
        String url = "http://rarnu.xyz:38383/api/mdpro3/sync/single";
        Map<String, String> headers = new HashMap<>();
        headers.put("ReqSource", "YGOMobile");
        headers.put("token", loginToken.getServerToken());


        Gson gson = new Gson();

        PushSingleDeck pushSingleDeck = new PushSingleDeck();
        PushSingleDeck.DeckData deckData = new PushSingleDeck.DeckData();

        deckData.setDeckId(deckId);
        deckData.setDelete(true);
        pushSingleDeck.setDeck(deckData);
        pushSingleDeck.setUserId(loginToken.getUserId());


        String json = gson.toJson(pushSingleDeck);

        Response response = OkhttpUtil.postJson(url, json, headers);
        String responseBodyString = response.body().string();

        // Convert JSON to Java object using Gson
        result = gson.fromJson(responseBodyString, PushSingleDeckResponse.class);
        LogUtil.i(TAG, "push deck response:" + responseBodyString);


        return result;
    }

    /**
     * 管理员使用，删除某卡组（听说可以删除别人的卡组，没试过）
     * 该api没有权限校验，慎用
     */
    public static void adminDelete(String deckId) {
        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/" + deckId;
    }


    public static SyncMutliDeckResult synchronizeDecksV2() throws IOException {
        SyncMutliDeckResult autoSyncResult = new SyncMutliDeckResult();
        // 检查用户是否登录
        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if (loginToken == null) {
            autoSyncResult.setFlag(false);
            autoSyncResult.setInfo("need login");
            return autoSyncResult;
        }

        // 获取本地卡组列表
        List<MyDeckItem> localDecks = DeckSquareFileUtil.getMyDeckItem();
        // 获取在线卡组列表
        MyDeckResponse onlineDecksResponse = DeckSquareApiUtil.getUserDecks(loginToken);
        if (onlineDecksResponse == null || onlineDecksResponse.getData() == null) {
            autoSyncResult.setFlag(false);
            autoSyncResult.setInfo("no online decks");
            return autoSyncResult;
        }
        List<MyOnlineDeckDetail> onlineDecks = onlineDecksResponse.getData();


        // 用于标记在线卡组是否在本地有对应
        Map<String, Boolean> onlineDeckProcessed = new HashMap<>();
        for (MyOnlineDeckDetail onlineDeck : onlineDecks) {
            onlineDeckProcessed.put(onlineDeck.getDeckName(), false);
        }
        List<MyDeckItem> syncUploadDecks = new ArrayList<>();
        List<MyDeckItem> newPushDecks = new ArrayList<>();
        // 遍历本地卡组，处理同名卡组的情况
        for (MyDeckItem localDeck : localDecks) {
            boolean foundOnlineDeck = false;
            String localDeckName = localDeck.getDeckName().replace(Constants.YDK_FILE_EX, "");
            for (MyOnlineDeckDetail onlineDeck : onlineDecks) {
                String onLineDeckName = onlineDeck.getDeckName().replace(Constants.YDK_FILE_EX, "");
                if (localDeckName.equals(onLineDeckName)) {
                    // 标记该在线卡组已处理
                    onlineDeckProcessed.put(onLineDeckName, true);
                    // 标记该本地卡组已处理
                    foundOnlineDeck = true;
                    // 比对更新时间
                    long localUpdateDate = localDeck.getUpdateTimestamp();
                    long onlineUpdateDate = DeckSquareFileUtil.convertToUnixTimestamp(onlineDeck.getDeckUpdateDate());//todo 这里应该把2025-05-19T06:11:17转成毫秒，onlineDeck.getDeckUpdateDate();
                    LogUtil.d("seesee 本地和在线时间差", localDeckName + ": " + (localUpdateDate - onlineUpdateDate > 0 ? "本地的-新" : "在线的-新"));
                    LogUtil.d("seesee 差多少", localDeckName + ": " + (localUpdateDate - onlineUpdateDate));
                    if (onlineUpdateDate > localUpdateDate) {
                        // 在线卡组更新时间更晚，下载在线卡组覆盖本地卡组
                        LogUtil.w(TAG, "seesee *下载* " + localDeck.getDeckPath());
                        autoSyncResult.syncDownload.add(localDeck);
                        downloadOnlineDeck(onlineDeck, localDeck.getDeckPath(), onlineUpdateDate);
                    } else if (onlineUpdateDate == localUpdateDate) {
                        LogUtil.w(TAG, "seesee 不需要同步: " + localDeck.getDeckName());
                        //时间戳相同，不需要更新
                    } else {
                        // 本地卡组更新时间更晚，上传本地卡组覆盖在线卡组
                        localDeck.setDeckName(localDeck.getDeckName().replace(Constants.YDK_FILE_EX, ""));//TODO 上版本很多人已经传了带.ydk的云备份，姑且只在这次再次上传时去掉.ydk
                        localDeck.setDeckCoverCard1(DeckUtil.getFirstCardCode(localDeck.getDeckPath()));
                        localDeck.setDeckId(onlineDeck.getDeckId());
                        LogUtil.w(TAG, "seesee -上传-" + localDeck.getDeckName());

                        syncUploadDecks.add(localDeck);
                        autoSyncResult.syncUpload.add(localDeck);
                    }
                    break;
                }
            }

            // 本地卡组在在线列表中不存在，则需要获取新的deckid来直接上传
            if (!foundOnlineDeck) {
                localDeck.setDeckName(localDeck.getDeckName().replace(Constants.YDK_FILE_EX, ""));
                localDeck.setDeckCoverCard1(DeckUtil.getFirstCardCode(localDeck.getDeckPath()));
                LogUtil.w(TAG, "seesee upload deck new: " + localDeck.getDeckName() + "、封面id：" + localDeck.getDeckCoverCard1());
                newPushDecks.add(localDeck);
                autoSyncResult.newUpload.add(localDeck);
            }

        }

        LogUtil.w(TAG, "before syncMyDecks-unpload syncUploadDecks size(): " + syncUploadDecks.size());
        if (!syncUploadDecks.isEmpty()) {
            PushMultiResponse response = syncMyDecks(syncUploadDecks, loginToken);
            autoSyncResult.pushResponse = response;
            LogUtil.w(TAG, "after syncMyDecks-upload");
        }


        LogUtil.w(TAG, "newPushDecks size " + newPushDecks.size());
        if (!newPushDecks.isEmpty()) {
            LogUtil.w(TAG, "before new decks-upload");
            PushMultiResponse pushNewRes = requestIdAndPushNewDecks(newPushDecks, loginToken);
            LogUtil.w(TAG, "after new decks-upload");
        }


        LogUtil.w(TAG, "before download online decks " + onlineDecks.size());
        // 处理只存在于在线的卡组（即本地没有同名卡组）
        for (MyOnlineDeckDetail onlineDeck : onlineDecks) {
            String onLineDeckName = onlineDeck.getDeckName().replace(Constants.YDK_FILE_EX, "");
            if (!onlineDeckProcessed.get(onLineDeckName)) {
                autoSyncResult.newDownload.add(onlineDeck);
                LogUtil.w(TAG, "sync-download new deck: " + onlineDeck.getDeckName());
                SyncMutliDeckResult.DownloadResult downloadResult = downloadMissingDeckToLocal(onlineDeck, DeckSquareFileUtil.convertToUnixTimestamp(onlineDeck.getDeckUpdateDate()));
                autoSyncResult.downloadResponse.add(downloadResult);
            }
        }

        return autoSyncResult;

    }

    public static void synchronizeDecks() throws IOException {
        // 检查用户是否登录
        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if (loginToken == null) {
            return;
        }

        // 获取本地卡组列表
        List<MyDeckItem> localDecks = DeckSquareFileUtil.getMyDeckItem();
        // 获取在线卡组列表
        MyDeckResponse onlineDecksResponse = DeckSquareApiUtil.getUserDecks(loginToken);
        if (onlineDecksResponse == null || onlineDecksResponse.getData() == null) {
            LogUtil.e(TAG, "load my online decks failed!");
            return;
        }
        List<MyOnlineDeckDetail> onlineDecks = onlineDecksResponse.getData();

        // 缓存原始在线卡组（使用副本避免后续修改影响缓存）
        DeckManagerFragment.getOriginalData().clear();
        DeckManagerFragment.getOriginalData().addAll(new ArrayList<>(onlineDecks));

        // 遍历本地卡组与云备份卡组，过滤差异项（使用迭代器避免ConcurrentModificationException）
        List<MyDeckItem> syncUploadDecks = new ArrayList<>();
        List<MyDeckItem> newPushDecks = new ArrayList<>();
        List<MyOnlineDeckDetail> backupDownloadDecks = new ArrayList<>();

        // 1. 使用本地卡组的迭代器遍历（支持安全删除）
        Iterator<MyDeckItem> localIterator = localDecks.iterator();
        while (localIterator.hasNext()) {
            MyDeckItem localDeck = localIterator.next();
            // 预处理本地卡组
            String localDeckName = localDeck.getDeckName().replace(Constants.YDK_FILE_EX, "");
            localDeck.setDeckName(localDeckName);
            localDeck.setDeckCoverCard1(DeckUtil.getFirstCardCode(localDeck.getDeckPath()));

            boolean isMatched = false;
            // 2. 使用在线卡组的迭代器遍历（支持安全删除）
            Iterator<MyOnlineDeckDetail> onlineIterator = onlineDecks.iterator();
            while (onlineIterator.hasNext()) {
                MyOnlineDeckDetail onlineDeck = onlineIterator.next();
                String onLineDeckName = onlineDeck.getDeckName().replace(Constants.YDK_FILE_EX, "");

                if (localDeckName.equals(onLineDeckName)) {
                    // 匹配到同名卡组：加入同步上传列表，并从原始集合中删除（避免重复处理）
                    localDeck.setDeckId(onlineDeck.getDeckId());
                    syncUploadDecks.add(localDeck);
                    localIterator.remove(); // 安全删除本地卡组（迭代器方法）
                    onlineIterator.remove(); // 安全删除在线卡组（迭代器方法）
                    isMatched = true;
                    break; // 匹配后跳出内部循环
                }
            }
            // 若未匹配到在线卡组，该本地卡组会保留在localDecks中（后续作为新卡组上传）
        }

        // 剩余的本地卡组都是新卡组（本地独有，需要上传）
        newPushDecks.addAll(localDecks);
        // 剩余的在线卡组都是新卡组（云端独有，需要下载）
        backupDownloadDecks.addAll(onlineDecks);

        LogUtil.w(TAG, "seesee +要下载的 云备份卡组: " + backupDownloadDecks);
        for (MyOnlineDeckDetail onlineDeck : backupDownloadDecks) {
            // 确保文件名包含.ydk扩展名
            String fileName = onlineDeck.getDeckName();
            if (!fileName.toLowerCase().endsWith(Constants.YDK_FILE_EX)) {
                fileName += Constants.YDK_FILE_EX;
            }

            String fileFullPath = AppsSettings.get().getDeckDir() + "/" + fileName;

            // 保存在线卡组到本地
            boolean saved = DeckSquareFileUtil.saveFileToPath(fileFullPath, onlineDeck.getDeckYdk(), DeckSquareFileUtil.convertToUnixTimestamp(onlineDeck.getDeckUpdateDate()));
            if (!saved) LogUtil.e(TAG, "seesee Failed to save deck file: " + fileFullPath);

            LogUtil.i(TAG, "seesee Deck saved to: " + fileFullPath);
        }

        // 上传本地卡组覆盖在线卡组
        syncMyDecks(syncUploadDecks, loginToken);
        if (!newPushDecks.isEmpty()) {
            requestIdAndPushNewDecks(newPushDecks, loginToken);
            LogUtil.w(TAG, "seesee +要上传的 本地卡组: " + newPushDecks);
        }
    }

    private static SyncMutliDeckResult.DownloadResult downloadMissingDeckToLocal(MyOnlineDeckDetail onlineDeck, Long onlineUpdateDate) {
        try {
            // 根据卡组ID查询在线卡组详情
            DownloadDeckResponse deckResponse = DeckSquareApiUtil.getDeckById(onlineDeck.getDeckId());
            if (deckResponse == null || deckResponse.getData() == null) {
                LogUtil.e(TAG, "seesee Failed to get deck details for: " + onlineDeck.getDeckName());
                return new SyncMutliDeckResult.DownloadResult(false, onlineDeck.getDeckId(), "Failed to get deck details for: " + onlineDeck.getDeckName());
            }


            // 构建本地文件路径
            String deckDirectory = AppsSettings.get().getDeckDir();
            File dir = new File(deckDirectory);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    LogUtil.e(TAG, "seesee Failed to create directory: " + deckDirectory);
                    return new SyncMutliDeckResult.DownloadResult(false, onlineDeck.getDeckId(), "Failed to create directory: " + deckDirectory);
                }
            }

            // 确保文件名包含.ydk扩展名
            String fileName = onlineDeck.getDeckName();
            if (!fileName.toLowerCase().endsWith(Constants.YDK_FILE_EX)) {
                fileName += Constants.YDK_FILE_EX;
            }

            String fileFullPath = deckDirectory + "/" + fileName;

            // 保存在线卡组到本地
            boolean saved = DeckSquareFileUtil.saveFileToPath(fileFullPath, deckResponse.getData().getDeckYdk(), onlineUpdateDate);
            if (!saved) {
                LogUtil.e(TAG, "seesee Failed to save deck file: " + fileFullPath);
                return new SyncMutliDeckResult.DownloadResult(false, onlineDeck.getDeckId(), "Failed to save deck file: " + fileFullPath);
            }

            LogUtil.i(TAG, "seesee Deck saved to: " + fileFullPath);

            return new SyncMutliDeckResult.DownloadResult(true, onlineDeck.getDeckId());
        } catch (Exception e) {
            LogUtil.e(TAG, "seesee Error downloading missing deck: " + e.getMessage());
            e.printStackTrace();
            return new SyncMutliDeckResult.DownloadResult(false, onlineDeck.getDeckId(), "Error downloading missing deck: " + e.getMessage());
        }
    }

    private static boolean downloadOnlineDeck(MyOnlineDeckDetail onlineDeck, String fileFullPath, Long onlineUpdateDate) {
        try {
            // 根据卡组ID查询在线卡组详情
            DownloadDeckResponse deckResponse = DeckSquareApiUtil.getDeckById(onlineDeck.getDeckId());
            if (deckResponse == null || deckResponse.getData() == null) {
                LogUtil.e(TAG, "Failed to get deck details for: " + onlineDeck.getDeckName());
                return false;
            }

            MyOnlineDeckDetail deckDetail = deckResponse.getData();
            String deckContent = deckDetail.getDeckYdk();
            // 保存在线卡组到本地
            boolean saved = DeckSquareFileUtil.saveFileToPath(fileFullPath, deckContent, onlineUpdateDate);
            if (!saved) {
                LogUtil.e(TAG, "Failed to save deck file: " + fileFullPath);
                return false;
            }

            LogUtil.i(TAG, "Deck updated: " + fileFullPath);
            return true;
        } catch (Exception e) {
            LogUtil.e(TAG, "Error downloading deck: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将MyDeckItem调用单卡组同步接口推送到服务器
     *
     * @param localDeck
     * @param onlineDeckId
     * @param loginToken
     * @return
     */
    private static boolean uploadLocalDeck(MyDeckItem localDeck, String
            onlineDeckId, LoginToken loginToken) {
        try {
            DeckFile deckFile = new DeckFile(localDeck.getDeckPath(), DeckType.ServerType.MY_SQUARE);
            deckFile.setName(localDeck.getDeckName());
            deckFile.setFirstCode(localDeck.getDeckCoverCard1());

            // 上传本地卡组，使用在线卡组的deckId
            PushSingleDeckResponse response = DeckSquareApiUtil.pushDeck(deckFile, loginToken, onlineDeckId);
            if (response == null || !response.isData()) {
                LogUtil.e(TAG, "Failed to upload deck: " + localDeck.getDeckName());
                return false;
            }

            // 上传成功，更新本地卡组的deckId和上传状态
            localDeck.setDeckId(onlineDeckId);
            localDeck.setIdUploaded(2);
            LogUtil.i(TAG, "Deck uploaded successfully: " + localDeck.getDeckName());
            return true;
        } catch (Exception e) {
            LogUtil.e(TAG, "Error uploading deck: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
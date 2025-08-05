package cn.garymb.ygomobile.ui.cards.deck_square;

import static cn.garymb.ygomobile.ui.cards.DeckManagerFragment.getOriginalData;
import static cn.garymb.ygomobile.ui.cards.DeckManagerFragment.originalData;
import static cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareFileUtil.toDeckItemList;

import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.DeckManagerFragment;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.BasicResponse;
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
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.SquareDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.bo.MyDeckItem;
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
        LogUtil.i(TAG, "getDeckById: " + responseBodyString);


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
            data.setDeckUpdateTime(myDeckItem.getUpdateTimestamp());
            decks.add(data);
        }
        return pushMultiDecks(decks, loginToken);

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
        List<String> deckIdList = deckIdResult.getDeckId();//从服务器获取
        if (deckIdList == null) {
            return null;
        } else {
            LogUtil.i(TAG, "requestIdAndPushNewDecks deckIdList" + deckIdList);
        }

        return pushDecks(deckDataList, loginToken, deckIdList);
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
            data.setDeckType(item.getDeckType());
            data.setDeckCoverCard1(item.getDeckCoverCard1());
            data.setDeckUpdateTime(item.getUpdateTimestamp());
            String deckContent = "";
            if (item.getDeckPath() == null) {//防止获取不到文件路径而出现异常（多发生在删除卡组后的同步时）
                data.setDelete(true);

            } else {
                deckContent = DeckSquareFileUtil.setDeckId(item.getDeckPath(), loginToken.getUserId(), item.getDeckId());
            }
            data.setDeckYdk(deckContent);
            LogUtil.w(TAG, "syncMyDecks *要上传的* 本地卡组: \n是否删除？： " + data.isDelete() + "\n卡组分类：" + data.getDeckType() + "\n卡组名称： " + data.getDeckName() + "\n封面id： " + data.getDeckCoverCard1() + "\n卡组id： " + data.getDeckId());
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
        LogUtil.i(TAG, "pushMultiDecks 上传多个卡组结果: " + responseBodyString);

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

    /**
     * 管理员使用，删除某卡组（听说可以删除别人的卡组，没试过）
     * 该api没有权限校验，慎用
     */
    public static void adminDelete(String deckId) {
        String url = "http://rarnu.xyz:38383/api/mdpro3/deck/" + deckId;
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
        DeckManagerFragment.getOriginalData().addAll(onlineDecks);

        // 遍历本地卡组与云备份卡组，过滤差异项（使用迭代器避免ConcurrentModificationException）
        List<MyDeckItem> syncUploadDecks = new ArrayList<>();

        // 1. 使用本地卡组的迭代器遍历（支持安全删除）
        Iterator<MyDeckItem> localIterator = localDecks.iterator();
        while (localIterator.hasNext()) {
            MyDeckItem localDeck = localIterator.next();
            // 预处理本地卡组
            String localDeckName = localDeck.getDeckName().replace(Constants.YDK_FILE_EX, "");
            localDeck.setDeckName(localDeckName);
            localDeck.setDeckCoverCard1(DeckUtil.getFirstCardCode(localDeck.getDeckPath()));
            localDeck.setDelete(false);
            LogUtil.e(TAG,"本地卡组名称："+localDeck.getDeckName()+"\n本地卡组分类："+localDeck.getDeckType()+"\n本地卡组ID："+localDeck.getDeckId());
            // 2. 使用在线卡组的迭代器遍历（支持安全删除）
            Iterator<MyOnlineDeckDetail> onlineIterator = onlineDecks.iterator();
            while (onlineIterator.hasNext()) {
                MyOnlineDeckDetail onlineDeck = onlineIterator.next();
                String onLineDeckName = onlineDeck.getDeckName().replace(Constants.YDK_FILE_EX, "");

                if (localDeckName.equals(onLineDeckName)) {//TODO 上个版本不支持卡组分类字段，新版本安装后必然会出现在线备份没卡组分类的场合与本地有分类的进行比较 && localDeck.getDeckType().equals(onlineDeck.getDeckType())
                    // 匹配到同名卡组：加入同步上传列表，并从原始集合中删除（避免重复处理）
                    localDeck.setDeckId(onlineDeck.getDeckId());
                    syncUploadDecks.add(localDeck);
                    localIterator.remove(); // 安全删除本地卡组（迭代器方法）
                    onlineIterator.remove(); // 安全删除在线卡组（迭代器方法）
                    break; // 匹配后跳出内部循环
                }
            }
            // 若未匹配到在线卡组，该本地卡组会保留在localDecks中（后续作为新卡组上传）
        }

        // 上传本地卡组覆盖在线卡组
        syncMyDecks(syncUploadDecks, loginToken);

        // 剩余的本地卡组都是新增卡组（本地独有，需要上传）
        LogUtil.w(TAG, "+上传新增的 本地卡组: " + localDecks);
        if (!localDecks.isEmpty()) {
            PushMultiResponse result = requestIdAndPushNewDecks(localDecks, loginToken);
            LogUtil.w(TAG, "上传结果数：" + result.getData());
        }

        // 剩余的在线卡组都是云端独有，需要下载
        LogUtil.i(TAG, "剩余onlineDecks：" + onlineDecks);
        for (MyOnlineDeckDetail onlineDeck : onlineDecks) {
            LogUtil.d(TAG, "synchronizeDecks +要下载的 云备份卡组: \n卡组分类：" + onlineDeck.getDeckType() + "\n卡组名：" + onlineDeck.getDeckName() + "\n卡组id：" + onlineDeck.getDeckId());
            // 确保文件名包含.ydk扩展名
            String fileName = onlineDeck.getDeckName();
            if (!fileName.toLowerCase().endsWith(Constants.YDK_FILE_EX)) {
                fileName += Constants.YDK_FILE_EX;
            }

            String fileFullPath = AppsSettings.get().getDeckDir() + "/" + fileName;
            if (!onlineDeck.getDeckType().equals(""))
                fileFullPath = AppsSettings.get().getDeckDir() + "/" + onlineDeck.getDeckType() + "/" + fileName;

            // 保存在线卡组到本地
            boolean saved = DeckSquareFileUtil.saveFileToPath(fileFullPath, onlineDeck.getDeckYdk(), onlineDeck.getDeckUpdateDate());
            if (!saved) {
                LogUtil.e(TAG, "synchronizeDecks 保存失败！的 云备份卡组: " + fileFullPath);
            } else {
                LogUtil.d(TAG, "synchronizeDecks 保存成功√的 云备份卡组: " + fileFullPath);
            }
        }
    }

    public static void deleteDecks(List<DeckFile> deckFileList) {
        if (SharedPreferenceUtil.getServerToken() != null) {
            LoginToken loginToken = new LoginToken(
                    SharedPreferenceUtil.getServerUserId(),
                    SharedPreferenceUtil.getServerToken()
            );
            // 使用VUiKit避开UI线程进行网络请求
            VUiKit.defer().when(() -> {
                // 先判断缓存表是否需要更新
                if (DeckManagerFragment.getOriginalData().isEmpty()) {
                    // 同步获取在线卡组列表（在后台线程中可以安全执行网络操作）
                    MyDeckResponse result = DeckSquareApiUtil.getUserDecks(loginToken);
                    if (result != null && result.getData() != null) {
                        DeckManagerFragment.getOriginalData().addAll(result.getData());
                    }
                }
                // 处理删除标记
                for (DeckFile deleteDeckFile : deckFileList) {
                    for (MyOnlineDeckDetail onlineDeckDetail : originalData) {
                        if (deleteDeckFile.getName().equals(onlineDeckDetail.getDeckName())
                                && deleteDeckFile.getTypeName().equals(onlineDeckDetail.getDeckType())) {

                            onlineDeckDetail.setDelete(true);

                            deleteDeckFile.setDeckId(onlineDeckDetail.getDeckId());
                        }
                    }
                }
                // 执行同步操作（在UI线程不得不进行网络请求，须转在后台线程）
                syncMyDecks(toDeckItemList(originalData), loginToken);
                return true;
            }).fail((e) -> {
                LogUtil.e(TAG, "删除卡组失败! 原因：" + e);
            }).done((result) -> {
                LogUtil.d(TAG, "卡组删除同步成功");
            });
        }
    }
}
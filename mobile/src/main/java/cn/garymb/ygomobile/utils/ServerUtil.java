package cn.garymb.ygomobile.utils;

import static cn.garymb.ygomobile.Constants.URL_YGO233_DATAVER;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE_ALT;

import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.events.ExCardEvent;
import cn.garymb.ygomobile.lite.BuildConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ServerUtil {
    public enum ExCardState {
        /* 已安装最新版扩展卡，扩展卡不是最新版本，无法查询到服务器版本 */
        UPDATED, NEED_UPDATE, ERROR;
    }

    /* 存储了当前先行卡是否需要更新的状态，UI逻辑直接读取该变量就能获知是否已安装先行卡 */
    public volatile static ExCardState exCardState = ExCardState.ERROR;//TODO 可能有并发问题
    public volatile static String serverExCardVersion = "";

    private volatile static int failCounter = 0;
    /**
     * 在可能更改先行卡状态的操作后调用，
     * 删除先行卡时入参为null，
     * 安装先行卡时入参为版本号
     *
     */
//       ++FailedCount;
//                    if (FailedCount <= 2) {
//        Toast.makeText(getActivity(), R.string.Ask_to_Change_Other_Way, Toast.LENGTH_SHORT).show();
//        downloadfromWeb(URL_YGO233_FILE_ALT);
//    }

    /**
     * 初始化本地先行卡版本的状态，
     * 比对服务器的先行卡版本号与本地先行卡版本号，
     * 更新全局变量exCardVersion（如删除先行卡、重新安装先行卡等）
     */
    public static void initExCardState() {
        String oldVer = SharedPreferenceUtil.getExpansionDataVer();
        Log.i("webCrawler", "server util, old pre-card version:" + oldVer);
        OkhttpUtil.get(URL_YGO233_DATAVER, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                exCardState = ExCardState.ERROR;
                serverExCardVersion = "";
                Log.i(BuildConfig.VERSION_NAME, "error" + e);
                Log.i("webCrawler", "network failed, pre-card version:" + exCardState);
                if (failCounter < 3) {
                    Log.i("webCrawler", "network failed, retry fetch pre-card version:");
                    failCounter++;
                    initExCardState();
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                failCounter = 0;//充值计数器
                String newVer = response.body().string();
                serverExCardVersion = newVer;

                Log.i("webCrawler", "ServerUtil fetch pre-card version:" + newVer);
                if (!TextUtils.isEmpty(newVer)) {

                    if (!newVer.equals(oldVer)) {//如果oldVer为null，也会触发
                        exCardState = ExCardState.NEED_UPDATE;
                    } else {
                        exCardState = ExCardState.UPDATED;
                    }
                } else {
                    exCardState = ExCardState.ERROR;
                }

                /* 通知homeFragment更新图标 */
                EventBus.getDefault().postSticky(new ExCardEvent(ExCardEvent.EventType.exCardPackageChange));
            }
        });
    }

    public static boolean isPreServer(int port, String addr) {
        if ((port == Constants.PORT_YGO233 && addr.equals(Constants.URL_YGO233_1)) ||
                (port == Constants.PORT_YGO233 && addr.equals(Constants.URL_YGO233_2))) {
            return true;
        } else {
            return false;
        }
    }
}

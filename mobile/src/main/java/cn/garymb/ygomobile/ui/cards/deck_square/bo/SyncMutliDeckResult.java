package cn.garymb.ygomobile.ui.cards.deck_square.bo;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushMultiResponse;

public class SyncMutliDeckResult {
    boolean flag = false;
    String info = null;
    public PushMultiResponse pushResponse;

    public List<MyDeckItem> syncUpload;//用于记录已推送的卡组
    public List<MyDeckItem> newUpload;//用于记录第一次推送到云的卡组
    public List<MyDeckItem> syncDownload;//用于记录已推送的卡组
    public List<MyOnlineDeckDetail> newDownload;
    public List<DownloadResult> downloadResponse;

    public static class DownloadResult {
        boolean flag;

        String deckId;
        String info;

        public DownloadResult(boolean flag, String deckId) {
            this.flag = flag;
            this.deckId = deckId;
        }

        public DownloadResult(boolean flag, String deckId, String info) {
            this.flag = flag;
            this.deckId = deckId;
            this.info = info;

        }
    }

    public SyncMutliDeckResult() {
        flag = true;
        downloadResponse = new ArrayList<>();
        newDownload = new ArrayList<>();
        syncUpload = new ArrayList<>();
        newUpload = new ArrayList<>();
        syncDownload = new ArrayList<>();
    }

    public SyncMutliDeckResult(boolean flag, String info) {
        this.flag = flag;
        this.info = info;
        downloadResponse = new ArrayList<>();
        newDownload = new ArrayList<>();
        syncUpload = new ArrayList<>();
        newUpload = new ArrayList<>();
        syncDownload = new ArrayList<>();
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getMessage() {

        String info = "sync decks: " + syncUpload.size() + ", push new:" + newUpload.size() + ", download " + newDownload.size();
        return info;
    }
}

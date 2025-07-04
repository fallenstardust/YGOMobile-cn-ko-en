package cn.garymb.ygomobile.deck_square.bo;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.deck_square.api_response.SyncDecksResponse;

public class SyncMutliDeckResult {
    boolean flag = false;
    String info = null;
    public SyncDecksResponse pushResponse;

    public List<MyDeckItem> toUpload;//用于记录已推送的卡组
    public List<MyOnlineDeckDetail> download;
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
        download = new ArrayList<>();
        toUpload = new ArrayList<>();
    }

    public SyncMutliDeckResult(boolean flag, String info) {
        this.flag = flag;
        this.info = info;
        downloadResponse = new ArrayList<>();
        download = new ArrayList<>();
        toUpload = new ArrayList<>();
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
}

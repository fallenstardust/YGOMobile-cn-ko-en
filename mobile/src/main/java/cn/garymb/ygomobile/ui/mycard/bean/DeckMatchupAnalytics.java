package cn.garymb.ygomobile.ui.mycard.bean;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DeckMatchupAnalytics extends ArrayList<DeckMatchupAnalytics.Item> {

    public static final String SOURCE_MYCARD_ATHLETIC = "mycard-athletic";
    public static final String SOURCE_MYCARD_ENTERTAIN = "mycard-entertain";

    public static void getDeckMatchupAnalytics(String source, OnDeckMatchupListener listener) {
        Map<String, Object> params = new HashMap<>();
        params.put("source", source);

        OkhttpUtil.get(MyCard.URL_DECK_MATCHUP_ANALYTICS, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (listener != null) {
                    listener.onDeckMatchupQuery(null, e.toString());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (listener == null) {
                    return;
                }

                if (!response.isSuccessful()) {
                    listener.onDeckMatchupQuery(null, "error:" + response.code());
                    return;
                }

                String json = response.body().string();
                DeckMatchupAnalytics result = new Gson().fromJson(json, DeckMatchupAnalytics.class);
                listener.onDeckMatchupQuery(result, null);
            }
        });
    }

    public interface OnDeckMatchupListener {
        void onDeckMatchupQuery(DeckMatchupAnalytics analytics, String exception);
    }

    public static class Item {
        private String decka;
        private String deckb;
        private int win;
        private int draw;
        private int lose;

        public String getDecka() {
            return decka;
        }

        public void setDecka(String decka) {
            this.decka = decka;
        }

        public String getDeckb() {
            return deckb;
        }

        public void setDeckb(String deckb) {
            this.deckb = deckb;
        }

        public int getWin() {
            return win;
        }

        public void setWin(int win) {
            this.win = win;
        }

        public int getDraw() {
            return draw;
        }

        public void setDraw(int draw) {
            this.draw = draw;
        }

        public int getLose() {
            return lose;
        }

        public void setLose(int lose) {
            this.lose = lose;
        }
    }
}

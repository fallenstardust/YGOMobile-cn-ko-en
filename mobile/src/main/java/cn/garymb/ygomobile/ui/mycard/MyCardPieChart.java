package cn.garymb.ygomobile.ui.mycard;

import static cn.garymb.ygomobile.ui.mycard.MyCard.URL_DECK_TYPE_ANALYTICS;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.utils.OkhttpUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MyCardPieChart extends ArrayList<MyCardPieChart.Item> {
    public static final String TYPE_DAY = "day";
    public static final String SOURCE_MYCARD_ATHLETIC = "mycard-athletic";

    public static void getDeckTypeAnalytics(String type, String source, OnMyCardPieChartListener listener) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("type", type);
        params.put("source", source);

        OkhttpUtil.get(URL_DECK_TYPE_ANALYTICS, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (listener != null) {
                    listener.onMyCardPieChartQuery(null, e.toString());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (listener == null) {
                    return;
                }

                if (!response.isSuccessful()) {
                    listener.onMyCardPieChartQuery(null, "error:" + response.code());
                    return;
                }

                String json = response.body().string();
                MyCardPieChart result = new Gson().fromJson(json, MyCardPieChart.class);
                
                //这是一个打印测试
                /*
                if (result != null) {
                    for (int i = 0; i < result.size(); i++) {
                        MyCardPieChart.Item item = result.get(i);
                        Log.e("MyCardPieChart", "index=" + i
                                + ", name=" + item.getName()
                                + ", recent_time=" + item.getRecent_time()
                                + ", source=" + item.getSource()
                                + ", count=" + item.getCount()
                                + ", tags=" + item.getTags());

                        if (item.getMatchup() != null) {
                            MyCardPieChart.First first = item.getMatchup().getFirst();
                            MyCardPieChart.Second second = item.getMatchup().getSecond();

                            if (first != null) {
                                Log.e("MyCardPieChart", "  first: decka=" + first.getDecka()
                                        + ", win=" + first.getWin()
                                        + ", draw=" + first.getDraw()
                                        + ", lose=" + first.getLose());
                            }

                            if (second != null) {
                                Log.e("MyCardPieChart", "  second: deckb=" + second.getDeckb()
                                        + ", win=" + second.getWin()
                                        + ", draw=" + second.getDraw()
                                        + ", lose=" + second.getLose());
                            }
                        }
                    }
                }
                */
                
                listener.onMyCardPieChartQuery(result, null);
            }
        });
    }

    public static void getDayAthleticDeckTypeAnalytics(OnMyCardPieChartListener listener) {
        getDeckTypeAnalytics(TYPE_DAY, SOURCE_MYCARD_ATHLETIC, listener);
    }

    public interface OnMyCardPieChartListener {
        void onMyCardPieChartQuery(MyCardPieChart pieChart, String exception);
    }

    public static class Item {
        private String name;
        private String recent_time;
        private String source;
        private String count;
        private List<String> tags = new ArrayList<>();
        private Matchup matchup;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRecent_time() {
            return recent_time;
        }

        public void setRecent_time(String recent_time) {
            this.recent_time = recent_time;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getCount() {
            return count;
        }

        public void setCount(String count) {
            this.count = count;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Matchup getMatchup() {
            return matchup;
        }

        public void setMatchup(Matchup matchup) {
            this.matchup = matchup;
        }
    }

    public static class Matchup {
        private First first;
        private Second second;

        public First getFirst() {
            return first;
        }

        public void setFirst(First first) {
            this.first = first;
        }

        public Second getSecond() {
            return second;
        }

        public void setSecond(Second second) {
            this.second = second;
        }
    }

    public static class First {
        private String decka;
        private String win;
        private String draw;
        private String lose;

        public String getDecka() {
            return decka;
        }

        public void setDecka(String decka) {
            this.decka = decka;
        }

        public String getWin() {
            return win;
        }

        public void setWin(String win) {
            this.win = win;
        }

        public String getDraw() {
            return draw;
        }

        public void setDraw(String draw) {
            this.draw = draw;
        }

        public String getLose() {
            return lose;
        }

        public void setLose(String lose) {
            this.lose = lose;
        }
    }

    public static class Second {
        private String deckb;
        private String win;
        private String draw;
        private String lose;

        public String getDeckb() {
            return deckb;
        }

        public void setDeckb(String deckb) {
            this.deckb = deckb;
        }

        public String getWin() {
            return win;
        }

        public void setWin(String win) {
            this.win = win;
        }

        public String getDraw() {
            return draw;
        }

        public void setDraw(String draw) {
            this.draw = draw;
        }

        public String getLose() {
            return lose;
        }

        public void setLose(String lose) {
            this.lose = lose;
        }
    }
}

package cn.garymb.ygomobile.ex_card;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import androidx.fragment.app.Fragment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;

public class ExCardLogFragment extends Fragment {


    private View layoutView;
    private ExCardLogAdapter mExCardLogAdapter;
    private ExpandableListView mExCardLogView;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        layoutView = inflater.inflate(R.layout.fragment_ex_card_log, container, false);
        initView(layoutView);
        loadData();
        return layoutView;
    }

    public void initView(View layoutView) {
        mExCardLogView = layoutView.findViewById(R.id.expandableListView);
        mExCardLogAdapter = new ExCardLogAdapter(getContext());
        mExCardLogView.setAdapter(mExCardLogAdapter);

    }


    public void loadData() {
        final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_ex_card));
        VUiKit.defer().when(() -> {
            String aurl = Constants.URL_YGO233_ADVANCE;
            //Connect to the website
            Document document = Jsoup.connect(aurl).get();
            Element pre_update_log = document.getElementById("pre_update_log");
            ArrayList<ExCardLogItem> exCardLogList = new ArrayList<>();
            Elements cardLogElements = pre_update_log.select("ul[class=auto-generated]").get(0).getElementsByTag("li");
            pre_update_log.getElementsByTag("ul");
            for (Element cardLog : cardLogElements) {
                String judgeData = cardLog.toString();
                Pattern p = Pattern.compile("<li>\\d{4}");
                boolean result = p.matcher(judgeData).find();
                if (result) {
                    Elements logItems = cardLog.getElementsByTag("li");
                    List<String> logs = new ArrayList<>();
                    logItems.get(0).select("ul").remove();
                    String dateTime = logItems.get(0).text();
                    for (int i = 1; i < logItems.size(); i++) {
                        logs.add(logItems.get(i).text());

                    }
                    exCardLogList.add(new ExCardLogItem(logs.size(), dateTime, logs));
                } else {
                    continue;
                }
            }

            if (exCardLogList.isEmpty()) {
                return null;
            } else {
                return exCardLogList;
            }

        }).fail((e) -> {
            //关闭异常
            if (dialog_read_ex.isShowing()) {
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {
                }
            }

            Log.i("webCrawler", "webCrawler fail");
        }).done(exCardLogList -> {
            mExCardLogAdapter.setData(exCardLogList);
            mExCardLogAdapter.notifyDataSetChanged();

            mExCardLogView.expandGroup(0);
            mExCardLogView.expandGroup(1);
            mExCardLogView.expandGroup(2);
            if (exCardLogList != null) {
                Log.i("webCrawler", "webCrawler parse html complete");
            }
            //关闭异常
            if (dialog_read_ex.isShowing()) {
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {
                }
            }
        });
    }

}
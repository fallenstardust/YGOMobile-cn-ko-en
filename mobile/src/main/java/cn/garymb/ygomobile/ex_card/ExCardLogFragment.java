package cn.garymb.ygomobile.ex_card;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;

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
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;

public class ExCardLogFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = String.valueOf(ExCardLogFragment.class);
    private Context mContext;
    private View layoutView;
    private LinearLayout ll_report;
    private ExCardLogAdapter mExCardLogAdapter;
    private ExpandableListView mExCardLogView;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        layoutView = inflater.inflate(R.layout.fragment_ex_card_log, container, false);
        this.mContext = getContext();
        initView(layoutView);
        loadData();
        return layoutView;
    }

    public void initView(View layoutView) {
        mExCardLogView = layoutView.findViewById(R.id.expandableListView);
        mExCardLogAdapter = new ExCardLogAdapter(getContext());
        mExCardLogView.setAdapter(mExCardLogAdapter);
        ll_report = layoutView.findViewById(R.id.btn_report);
        ll_report.setOnClickListener(this);

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

            Log.i(TAG, "webCrawler fail");
        }).done(exCardLogList -> {
            mExCardLogAdapter.setData(exCardLogList);
            mExCardLogAdapter.notifyDataSetChanged();

            mExCardLogView.expandGroup(0);
            mExCardLogView.expandGroup(1);
            mExCardLogView.expandGroup(2);
            if (exCardLogList != null) {
                Log.i(TAG, "webCrawler parse html complete");
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_report:
                WebActivity.open(mContext, getString(R.string.ex_card_report_title), Constants.URL_YGO233_BUG_REPORT);
                break;
        }
    }
}
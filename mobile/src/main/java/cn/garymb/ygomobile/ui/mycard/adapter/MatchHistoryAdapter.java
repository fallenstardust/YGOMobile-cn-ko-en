package cn.garymb.ygomobile.ui.mycard.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.McHistoryResponse;

public class MatchHistoryAdapter extends RecyclerView.Adapter<MatchHistoryAdapter.ViewHolder> {

    private List<McHistoryResponse.HistoryItem> mData = new ArrayList<>();
    private String username;
    private int highlightIndex = -1;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setNewData(List<McHistoryResponse.HistoryItem> data) {
        this.mData = data != null ? new ArrayList<>(data) : new ArrayList<>();
        Collections.sort(this.mData, (a, b) -> {
            String timeA = a.getStartTime();
            String timeB = b.getStartTime();
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });
        this.highlightIndex = -1;
        notifyDataSetChanged();
    }

    public void setHighlightIndex(int index) {
        this.highlightIndex = index;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_match_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        McHistoryResponse.HistoryItem item = mData.get(position);
        holder.bind(item, username, position == highlightIndex);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvResult, tvDate, tvScore, tvOpponent, tvPtInfo, tvPtDelta, tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvResult = itemView.findViewById(R.id.tv_match_result);
            tvDate = itemView.findViewById(R.id.tv_match_date);
            tvScore = itemView.findViewById(R.id.tv_match_score);
            tvOpponent = itemView.findViewById(R.id.tv_match_opponent);
            tvPtInfo = itemView.findViewById(R.id.tv_match_pt_info);
            tvPtDelta = itemView.findViewById(R.id.tv_match_pt_delta);
            tvDuration = itemView.findViewById(R.id.tv_match_duration);
        }

        void bind(McHistoryResponse.HistoryItem item, String username, boolean isHighlighted) {
            if (username == null) return;

            boolean isUserA = username.equals(item.getUsernamea());
            String opponent = isUserA ? item.getUsernameb() : item.getUsernamea();
            int userScore = isUserA ? safeInt(item.getUserscorea()) : safeInt(item.getUserscoreb());
            int oppScore = isUserA ? safeInt(item.getUserscoreb()) : safeInt(item.getUserscorea());
            Double userPtBefore = isUserA ? item.getPtaEx() : item.getPtbEx();
            Double userPtAfter = isUserA ? item.getPta() : item.getPtb();
            boolean isWin = userScore > oppScore;
            boolean isDraw = userScore == oppScore;

            tvResult.setText(isWin ? "胜" : isDraw ? "平" : "负");
            tvResult.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    isWin ? R.color.holo_green_bright : isDraw ? R.color.grayLight : R.color.holo_orange_bright));

            tvDate.setText(formatDate(item.getStartTime()));
            tvDuration.setText(calcDuration(item.getStartTime(), item.getEndTime()));
            tvScore.setText(userScore + " : " + oppScore);
            tvOpponent.setText("vs " + opponent);

            tvPtInfo.setText("D.P: "
                    + String.format(Locale.getDefault(), "%.1f", userPtBefore != null ? userPtBefore : 0.0)
                    + " → "
                    + String.format(Locale.getDefault(), "%.1f", userPtAfter != null ? userPtAfter : 0.0));

            if (userPtBefore != null && userPtAfter != null) {
                double delta = userPtAfter - userPtBefore;
                tvPtDelta.setText(delta >= 0
                        ? String.format(Locale.getDefault(), "+%.1f", delta)
                        : String.format(Locale.getDefault(), "%.1f", delta));
                tvPtDelta.setTextColor(ContextCompat.getColor(itemView.getContext(),
                        delta >= 0 ? R.color.holo_green_bright : R.color.holo_orange_bright));
            } else {
                tvPtDelta.setText("N/A");
                tvPtDelta.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grayLight));
            }

            if (isHighlighted) {
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.klein_blue));
            } else {
                itemView.setBackgroundResource(R.drawable.button_radius_black_transparents);
            }
        }

        private static int safeInt(Integer val) {
            return val != null ? val : 0;
        }

        private static String formatDate(String isoDate) {
            if (isoDate == null) return "";
            try {
                String dateTime = isoDate.replace("T", " ").replace(".000Z", "");
                return dateTime.length() >= 16 ? dateTime.substring(5, 16) : dateTime;
            } catch (Exception e) {
                return isoDate;
            }
        }

        private static String calcDuration(String startTime, String endTime) {
            if (startTime == null || endTime == null) return "";
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date start = sdf.parse(startTime);
                Date end = sdf.parse(endTime);
                if (start == null || end == null) return "";
                long diffMinutes = (end.getTime() - start.getTime()) / (1000 * 60);
                if (diffMinutes < 0) diffMinutes = 0;
                return diffMinutes + "分钟";
            } catch (Exception e) {
                return "";
            }
        }
    }
}

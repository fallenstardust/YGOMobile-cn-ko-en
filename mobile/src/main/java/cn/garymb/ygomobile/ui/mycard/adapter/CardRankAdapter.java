package cn.garymb.ygomobile.ui.mycard.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.CardTypeAnalytics;

public class CardRankAdapter extends RecyclerView.Adapter<CardRankAdapter.ViewHolder> {

    private List<CardTypeAnalytics.CardItem> dataList = new ArrayList<>();

    public void setData(List<CardTypeAnalytics.CardItem> data) {
        this.dataList = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card_rank, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CardTypeAnalytics.CardItem item = dataList.get(position);
        
        String locale = getSystemLocale();
        String cardName = item.getCardName(locale);
        holder.tvCardName.setText(cardName);
        
        holder.tvFrequency.setText("使用次数: " + item.getFrequency());
        holder.tvNumbers.setText("使用人数: " + item.getNumbers());
        
        String categoryText = getCategoryText(item.getCategory());
        holder.tvCategory.setText(categoryText);
        
        StringBuilder statsBuilder = new StringBuilder();
        statsBuilder.append("单张: ").append(item.getPutone())
                   .append(" | 两张: ").append(item.getPuttwo())
                   .append(" | 三张: ").append(item.getPutthree());
        holder.tvStats.setText(statsBuilder.toString());
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    private String getSystemLocale() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String country = locale.getCountry();
        
        if ("zh".equals(language)) {
            if ("CN".equals(country) || "TW".equals(country) || "HK".equals(country)) {
                return "zh-CN";
            }
        } else if ("ja".equals(language)) {
            return "ja-JP";
        } else if ("en".equals(language)) {
            return "en-US";
        }
        
        return "en-US";
    }

    private String getCategoryText(String category) {
        switch (category) {
            case "monster":
                return "怪兽卡";
            case "spell":
                return "魔法卡";
            case "trap":
                return "陷阱卡";
            case "ex":
                return "额外卡组";
            case "side":
                return "副卡组";
            default:
                return category;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCardName;
        TextView tvCategory;
        TextView tvFrequency;
        TextView tvNumbers;
        TextView tvStats;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCardName = itemView.findViewById(R.id.tv_card_name);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvFrequency = itemView.findViewById(R.id.tv_frequency);
            tvNumbers = itemView.findViewById(R.id.tv_numbers);
            tvStats = itemView.findViewById(R.id.tv_stats);
        }
    }
}
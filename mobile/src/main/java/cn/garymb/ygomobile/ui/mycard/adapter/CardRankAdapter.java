package cn.garymb.ygomobile.ui.mycard.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.mycard.bean.CardTypeAnalytics;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class CardRankAdapter extends RecyclerView.Adapter<CardRankAdapter.ViewHolder> {

    private List<CardTypeAnalytics.CardItem> dataList = new ArrayList<>();
    private ImageLoader imageLoader;
    private OnCardClickListener onCardClickListener;

    public interface OnCardClickListener {
        void onCardClick(CardTypeAnalytics.CardItem cardItem);
    }

    public void setOnCardClickListener(OnCardClickListener listener) {
        this.onCardClickListener = listener;
    }

    public void setImageLoader(ImageLoader loader) {
        this.imageLoader = loader;
    }

    public void setData(List<CardTypeAnalytics.CardItem> data) {
        this.dataList = data != null ? data : new ArrayList<>();
        sortDataByFrequency();
        notifyDataSetChanged();
    }

    private void sortDataByFrequency() {
        dataList.sort((item1, item2) -> {
            try {
                int freq1 = Integer.parseInt(item1.getFrequency());
                int freq2 = Integer.parseInt(item2.getFrequency());
                return Integer.compare(freq2, freq1);
            } catch (NumberFormatException e) {
                return 0;
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mycard_card_rank, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CardTypeAnalytics.CardItem item = dataList.get(position);
        
        holder.tvRank.setText(String.valueOf(position + 1));
        
        String cardName = getCardNameById(item.getId());
        if ("Unknown".equals(cardName)) {
            cardName = item.getCardName(getSystemLocale());
        }
        holder.tvCardName.setText(cardName);
        
        holder.tvFrequency.setText(item.getFrequency());
        holder.tvNumbers.setText(item.getNumbers());
        
        String categoryText = getCategoryText(item.getCategory());
        holder.tvCategory.setText(categoryText);
        
        holder.tvPutone.setText(item.getPutone());
        holder.tvPuttwo.setText(item.getPuttwo());
        holder.tvPutthree.setText(item.getPutthree());

        if (imageLoader != null) {
            Card card = DataManager.get().getCardManager().getCard(item.getId());
            if (card != null) {
                imageLoader.bindImage(holder.ivCardImage, card.Code, ImageLoader.Type.small);
            } else {
                holder.ivCardImage.setImageResource(R.drawable.unknown);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (onCardClickListener != null) {
                onCardClickListener.onCardClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    private String getCardNameById(int cardId) {
        Card card = DataManager.get().getCardManager().getCard(cardId);
        if (card != null && card.Name != null) {
            return card.Name;
        }
        
        return "Unknown";
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
                return YGOUtil.s(R.string.cat_monster);
            case "spell":
                return YGOUtil.s(R.string.cat_spell);
            case "trap":
                return YGOUtil.s(R.string.cat_trap);
            case "ex":
                return YGOUtil.s(R.string.cat_extra);
            case "side":
                return YGOUtil.s(R.string.cat_side);
            default:
                return category;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank;
        TextView tvCardName;
        TextView tvCategory;
        TextView tvFrequency;
        TextView tvNumbers;
        TextView tvPutone;
        TextView tvPuttwo;
        TextView tvPutthree;
        ImageView ivCardImage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tv_rank);
            tvCardName = itemView.findViewById(R.id.tv_card_name);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvFrequency = itemView.findViewById(R.id.tv_frequency);
            tvNumbers = itemView.findViewById(R.id.tv_numbers);
            tvPutone = itemView.findViewById(R.id.tv_putone);
            tvPuttwo = itemView.findViewById(R.id.tv_puttwo);
            tvPutthree = itemView.findViewById(R.id.tv_putthree);
            ivCardImage = itemView.findViewById(R.id.iv_card_image);
        }
    }
}
package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DeckSelectorUtil;

/**
 * 卡组选择对话框
 * 用于显示本地deck文件夹或windbot/Decks文件夹下的卡组列表
 */
public class DeckSelectorDialog {

    private Context context;
    private PopupWindow popupWindow;
    private OnDeckSelectedListener listener;
    private boolean isWindBotMode;  // true: windbot模式, false: 本地deck模式

    public interface OnDeckSelectedListener {
        void onDeckSelected(String deckPath, String deckName);
        void onCancelled();
    }

    public DeckSelectorDialog(Context context, boolean isWindBotMode) {
        this.context = context;
        this.isWindBotMode = isWindBotMode;
    }

    public void setOnDeckSelectedListener(OnDeckSelectedListener listener) {
        this.listener = listener;
    }

    public void show(View anchorView) {
        float density = context.getResources().getDisplayMetrics().density;

        // 读取卡组数据
        File deckDir;
        if (isWindBotMode) {
            deckDir = new File(context.getExternalFilesDir(null),
                "data/windbot/Decks");
        } else {
            deckDir = new File(context.getExternalFilesDir(null),
                "data/deck");
        }

        List<DeckSelectorUtil.DeckCategory> categories =
            DeckSelectorUtil.loadDeckCategories(deckDir);

        View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_deck_selector, null);

        ListView lvCategories = contentView.findViewById(R.id.lv_categories);
        ListView lvDecks = contentView.findViewById(R.id.lv_decks);
        Button btnCancel = contentView.findViewById(R.id.btn_cancel_deck);
        Button btnConfirm = contentView.findViewById(R.id.btn_confirm_deck);

        CategoryListAdapter categoryAdapter = new CategoryListAdapter(context, categories);
        lvCategories.setAdapter(categoryAdapter);

        final int[] selectedCategoryPos = {-1};
        final int[] selectedDeckPos = {-1};
        final String[] selectedDeckPath = {""};
        final String[] selectedDeckName = {""};

        lvCategories.setOnItemClickListener((parent, view, position, id) -> {
            selectedCategoryPos[0] = position;
            selectedDeckPos[0] = -1;
            categoryAdapter.setSelectedPosition(position);

            if (position >= 0 && position < categories.size()) {
                DeckSelectorUtil.DeckCategory category = categories.get(position);
                DeckListAdapter deckAdapter = new DeckListAdapter(context, category.deckList);
                lvDecks.setAdapter(deckAdapter);
            }
        });

        lvDecks.setOnItemClickListener((parent, view, position, id) -> {
            selectedDeckPos[0] = position;
            if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < categories.size()) {
                DeckSelectorUtil.DeckCategory category = categories.get(selectedCategoryPos[0]);
                if (position >= 0 && position < category.deckList.size()) {
                    DeckSelectorUtil.DeckItem deck = category.deckList.get(position);
                    selectedDeckPath[0] = deck.deckPath;
                    selectedDeckName[0] = deck.deckName;
                }
            }
        });

        btnCancel.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (listener != null) listener.onCancelled();
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedDeckPath[0].isEmpty()) {
                android.widget.Toast.makeText(context, "请先选择一个卡组",
                    android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            popupWindow.dismiss();
            if (listener != null) {
                listener.onDeckSelected(selectedDeckPath[0], selectedDeckName[0]);
            }
        });

        int popupWidth = (int) (600 * density);
        int popupHeight = (int) (450 * density);
        popupWindow = new PopupWindow(contentView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0, 0);
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /**
     * 分类列表适配器
     */
    private static class CategoryListAdapter extends BaseAdapter {
        private static final int SELECTED_BG_COLOR = 0x5587CEEB;
        private Context context;
        private List<DeckSelectorUtil.DeckCategory> categories;
        private int selectedPosition = -1;

        public CategoryListAdapter(Context context, List<DeckSelectorUtil.DeckCategory> categories) {
            this.context = context;
            this.categories = categories;
        }

        public void setSelectedPosition(int position) {
            this.selectedPosition = position;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return categories.size();
        }

        @Override
        public Object getItem(int position) {
            return categories.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(
                    R.layout.item_bot_list, parent, false);
                holder = new ViewHolder();
                holder.textView = convertView.findViewById(R.id.tv_bot_item);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            DeckSelectorUtil.DeckCategory category = categories.get(position);
            holder.textView.setText(category.categoryName);

            if (position == selectedPosition) {
                holder.textView.setBackgroundColor(SELECTED_BG_COLOR);
            } else {
                holder.textView.setBackgroundColor(Color.TRANSPARENT);
            }
            holder.textView.setTextColor(0xFFFFFFFF);

            return convertView;
        }

        private static class ViewHolder {
            TextView textView;
        }
    }

    /**
     * 卡组列表适配器
     */
    private static class DeckListAdapter extends BaseAdapter {
        private static final int SELECTED_BG_COLOR = 0x5587CEEB;
        private Context context;
        private List<DeckSelectorUtil.DeckItem> decks;
        private int selectedPosition = -1;

        public DeckListAdapter(Context context, List<DeckSelectorUtil.DeckItem> decks) {
            this.context = context;
            this.decks = decks;
        }

        public void setSelectedPosition(int position) {
            this.selectedPosition = position;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return decks.size();
        }

        @Override
        public Object getItem(int position) {
            return decks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(
                    R.layout.item_bot_list, parent, false);
                holder = new ViewHolder();
                holder.textView = convertView.findViewById(R.id.tv_bot_item);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            DeckSelectorUtil.DeckItem deck = decks.get(position);
            holder.textView.setText(deck.toString());

            if (position == selectedPosition) {
                holder.textView.setBackgroundColor(SELECTED_BG_COLOR);
            } else {
                holder.textView.setBackgroundColor(Color.TRANSPARENT);
            }
            holder.textView.setTextColor(0xFF87CEEB);

            return convertView;
        }

        private static class ViewHolder {
            TextView textView;
        }
    }
}
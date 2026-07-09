package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DeckSelectorUtil;

public class DeckSelectorDialog {

    private Context context;
    private PopupWindow popupWindow;
    private OnDeckSelectedListener listener;

    public interface OnDeckSelectedListener {
        void onDeckSelected(String deckPath, String deckName, String categoryName);
        void onCancelled();
    }

    public DeckSelectorDialog(Context context) {
        this.context = context;
    }

    public void setOnDeckSelectedListener(OnDeckSelectedListener listener) {
        this.listener = listener;
    }

    public void show(View anchorView) {
        float density = context.getResources().getDisplayMetrics().density;

        String uncatLocalName = context.getString(R.string.category_Uncategorized);
        String uncatAiName = context.getString(R.string.category_windbot_deck);

        List<DeckSelectorUtil.DeckCategory> categories = new ArrayList<>();

        File localDeckDir = new File(AppsSettings.get().getDeckDir());
        List<DeckSelectorUtil.DeckCategory> localCategories = DeckSelectorUtil.loadDeckCategories(localDeckDir);
        for (DeckSelectorUtil.DeckCategory c : localCategories) {
            String name = c.categoryName.equals("未分类卡组") ? uncatLocalName : c.categoryName;
            categories.add(new DeckSelectorUtil.DeckCategory(name));
            categories.get(categories.size() - 1).deckList.addAll(c.deckList);
        }

        File aiDeckDir = new File(AppsSettings.get().getAiDeckDir());
        List<DeckSelectorUtil.DeckCategory> aiCategories = DeckSelectorUtil.loadDeckCategories(aiDeckDir);
        for (DeckSelectorUtil.DeckCategory c : aiCategories) {
            String name = c.categoryName.equals("未分类卡组") ? uncatAiName : c.categoryName;
            categories.add(new DeckSelectorUtil.DeckCategory(name));
            categories.get(categories.size() - 1).deckList.addAll(c.deckList);
        }

        categories.sort((a, b) -> {
            boolean aUncat = a.categoryName.equals(uncatLocalName) || a.categoryName.equals(uncatAiName);
            boolean bUncat = b.categoryName.equals(uncatLocalName) || b.categoryName.equals(uncatAiName);
            if (aUncat != bUncat) return aUncat ? -1 : 1;
            return a.categoryName.compareToIgnoreCase(b.categoryName);
        });

        View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_deck_selector, null);

        ListView lvCategories = contentView.findViewById(R.id.lv_categories);
        ListView lvDecks = contentView.findViewById(R.id.lv_decks);
        Button btnConfirm = contentView.findViewById(R.id.btn_confirm_deck);

        CategoryListAdapter categoryAdapter = new CategoryListAdapter(context, categories);
        lvCategories.setAdapter(categoryAdapter);

        final DeckListAdapter[] currentDeckAdapter = {null};

        final int[] selectedCategoryPos = {-1};
        final int[] selectedDeckPos = {-1};
        final String[] selectedDeckPath = {""};
        final String[] selectedDeckName = {""};

        String lastCategory = AppsSettings.get().getLastCategory();
        String lastDeckName = AppsSettings.get().getLastDeckName();

        int lastCategoryIndex = -1;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).categoryName.equals(lastCategory)) {
                lastCategoryIndex = i;
                break;
            }
        }

        if (lastCategoryIndex >= 0) {
            selectedCategoryPos[0] = lastCategoryIndex;
            categoryAdapter.setSelectedPosition(lastCategoryIndex);

            DeckSelectorUtil.DeckCategory category = categories.get(lastCategoryIndex);
            DeckListAdapter deckAdapter = new DeckListAdapter(context, category.deckList);
            lvDecks.setAdapter(deckAdapter);
            currentDeckAdapter[0] = deckAdapter;

            int lastDeckIndex = -1;
            for (int i = 0; i < category.deckList.size(); i++) {
                if (category.deckList.get(i).deckName.equals(lastDeckName)) {
                    lastDeckIndex = i;
                    break;
                }
            }

            if (lastDeckIndex >= 0) {
                selectedDeckPos[0] = lastDeckIndex;
                deckAdapter.setSelectedPosition(lastDeckIndex);
                DeckSelectorUtil.DeckItem deck = category.deckList.get(lastDeckIndex);
                selectedDeckPath[0] = deck.deckPath;
                selectedDeckName[0] = deck.deckName;
            }

            final int catIdx = lastCategoryIndex;
            final int deckIdx = selectedDeckPos[0] >= 0 ? selectedDeckPos[0] : 0;
            lvCategories.post(() -> lvCategories.setSelection(catIdx));
            lvDecks.post(() -> lvDecks.setSelection(deckIdx));
        }

        lvCategories.setOnItemClickListener((parent, view, position, id) -> {
            selectedCategoryPos[0] = position;
            selectedDeckPos[0] = -1;
            selectedDeckPath[0] = "";
            selectedDeckName[0] = "";
            categoryAdapter.setSelectedPosition(position);

            if (position >= 0 && position < categories.size()) {
                DeckSelectorUtil.DeckCategory category = categories.get(position);
                DeckListAdapter deckAdapter = new DeckListAdapter(context, category.deckList);
                lvDecks.setAdapter(deckAdapter);
                currentDeckAdapter[0] = deckAdapter;
            }
        });

        lvDecks.setOnItemClickListener((parent, view, position, id) -> {
            selectedDeckPos[0] = position;
            if (currentDeckAdapter[0] != null) {
                currentDeckAdapter[0].setSelectedPosition(position);
            }
            if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < categories.size()) {
                DeckSelectorUtil.DeckCategory category = categories.get(selectedCategoryPos[0]);
                if (position >= 0 && position < category.deckList.size()) {
                    DeckSelectorUtil.DeckItem deck = category.deckList.get(position);
                    selectedDeckPath[0] = deck.deckPath;
                    selectedDeckName[0] = deck.deckName;
                }
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedDeckPath[0].isEmpty()) {
                android.widget.Toast.makeText(context, "请先选择一个卡组",
                    android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String categoryName = "";
            if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < categories.size()) {
                categoryName = categories.get(selectedCategoryPos[0]).categoryName;
            }
            popupWindow.dismiss();
            if (listener != null) {
                listener.onDeckSelected(selectedDeckPath[0], selectedDeckName[0], categoryName);
            }
        });

        int popupWidth = (int) (480 * density);
        int popupHeight = (int) (320 * density);
        popupWindow = new PopupWindow(contentView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

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
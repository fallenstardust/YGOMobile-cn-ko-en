package cn.garymb.ygomobile.ui.dialogs.deck;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.cards.deck.MyDeckItem;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DeckSelectorUtil;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * Deck selector popup, base layer of the split inheritance chain
 * (base <- DeckSelectorDialog). Owns all state fields, the category
 * data building/refresh/selection helpers, the cloud-sync utility
 * methods and the list adapters shared by the upper layer.
 */
abstract class DeckSelectorDialogBase {

    protected DeckSelectorDialogBase(Context context) {
        this.context = context;
    }

    protected static final String TAG = "DeckSelectorDialog";

    protected Context context;
    protected PopupWindow popupWindow;
    protected OnDeckSelectedListener listener;
    protected DraggablePopupHelper draggableHelper;

    protected final List<CategoryInfo> catInfos = new ArrayList<>();
    protected final List<DeckSelectorUtil.DeckCategory> displayCategories = new ArrayList<>();
    protected final List<String> displayCategoryNames = new ArrayList<>();

    protected CategoryListAdapter categoryAdapter;
    protected DeckListAdapter currentDeckAdapter;
    protected ListView lvCategories;
    protected ListView lvDecks;

    protected Button btnRenameCategory;
    protected Button btnDeleteCategory;
    protected Button btnNewDeck;
    protected Button btnRenameDeck;
    protected Button btnDeleteDeck;
    protected Button btnMoveToCategory;

    protected final int[] selectedCategoryPos = {-1};
    protected final int[] selectedDeckPos = {-1};
    protected final String[] selectedDeckPath = {""};
    protected final String[] selectedDeckName = {""};

    protected String localDeckDirPath;
    protected String aiDeckDirPath;

    //是否显示"卡包展示"分类（ygocore/pack）：卡组编辑器调用时为true，玩家等待界面默认false
    protected boolean includePackCategory = false;
    protected boolean disableOperationButtons = false;

    protected final List<Button> operationButtons = new ArrayList<>();

    protected final StringManager mStringManager = DataManager.get().getStringManager();

    protected static class CategoryInfo {
        DeckSelectorUtil.DeckCategory category;
        String baseDirPath;
        boolean isSystem;
        int sortPriority;

        CategoryInfo(DeckSelectorUtil.DeckCategory category, String baseDirPath,
                     boolean isSystem, int sortPriority) {
            this.category = category;
            this.baseDirPath = baseDirPath;
            this.isSystem = isSystem;
            this.sortPriority = sortPriority;
        }
    }

    public interface OnDeckSelectedListener {
        void onDeckSelected(String deckPath, String deckName, String categoryName);

        void onCancelled();

        //点击卡组item即触发（用于卡组编辑器即时加载）；默认空实现，玩家等待界面等无需处理
        default void onDeckItemClicked(String deckPath, String deckName, String categoryName) {
        }
    }
    public void setOperationButtonsEnabled(boolean enabled) {
        for (Button btn : operationButtons) {
            btn.setEnabled(enabled);
            btn.setTextColor(YGOUtil.c(enabled ? R.color.white : R.color.item_bg));
        }
    }

    protected void updateButtonStates() {
        if (disableOperationButtons) {
            setOperationButtonsEnabled(false);
            return;
        }
        if (selectedCategoryPos[0] < 0 || selectedCategoryPos[0] >= catInfos.size()) {
            setButtonEnabled(btnRenameCategory, false);
            setButtonEnabled(btnDeleteCategory, false);
            setButtonEnabled(btnNewDeck, false);
            setButtonEnabled(btnRenameDeck, false);
            setButtonEnabled(btnDeleteDeck, false);
            setButtonEnabled(btnMoveToCategory, false);
            return;
        }

        CategoryInfo ci = catInfos.get(selectedCategoryPos[0]);
        int priority = ci.sortPriority;
        boolean isPackOrAi = (priority <= 1);
        boolean isUncategorized = (priority == 2);
        boolean hasDeck = !selectedDeckPath[0].isEmpty();

        setButtonEnabled(btnRenameCategory, !isPackOrAi && !isUncategorized);
        setButtonEnabled(btnDeleteCategory, !isPackOrAi && !isUncategorized);
        setButtonEnabled(btnNewDeck, !isPackOrAi);
        setButtonEnabled(btnRenameDeck, !isPackOrAi && hasDeck);
        setButtonEnabled(btnDeleteDeck, !isPackOrAi && hasDeck);
        setButtonEnabled(btnMoveToCategory, !isPackOrAi && hasDeck);
    }

    protected void setButtonEnabled(Button btn, boolean enabled) {
        if (btn == null) return;
        btn.setEnabled(enabled);
        btn.setTextColor(YGOUtil.c(enabled ? R.color.white : R.color.item_bg));
    }
    // ==================== 数据构建 ====================

    protected void buildCategoryData(String uncatLocalName, String uncatAiName) {
        catInfos.clear();
        displayCategories.clear();
        displayCategoryNames.clear();

        List<DeckSelectorUtil.DeckCategory> rawCategories = new ArrayList<>();
        List<String> baseDirs = new ArrayList<>();
        List<Boolean> systemFlags = new ArrayList<>();
        List<String> internalNames = new ArrayList<>();
        List<String> displayNames = new ArrayList<>();
        List<Integer> sortPriorities = new ArrayList<>();

        File localDeckDir = new File(localDeckDirPath);
        for (DeckSelectorUtil.DeckCategory c : DeckSelectorUtil.loadDeckCategories(localDeckDir)) {
            rawCategories.add(c);
            baseDirs.add(localDeckDirPath);
            boolean isUncat = c.categoryName.equals("未分类卡组");
            systemFlags.add(isUncat);
            internalNames.add(c.categoryName);
            displayNames.add(isUncat ? uncatLocalName : c.categoryName);
            sortPriorities.add(isUncat ? 2 : 3);
        }

        File aiDeckDir = new File(aiDeckDirPath);
        for (DeckSelectorUtil.DeckCategory c : DeckSelectorUtil.loadDeckCategories(aiDeckDir)) {
            rawCategories.add(c);
            baseDirs.add(aiDeckDirPath);
            boolean isUncat = c.categoryName.equals("未分类卡组");
            systemFlags.add(isUncat);
            internalNames.add(c.categoryName);
            displayNames.add(isUncat ? uncatAiName : c.categoryName);
            sortPriorities.add(isUncat ? 1 : 3);
        }

        //卡包展示分类（ygocore/pack）：仅卡组编辑器调用时显示，玩家等待界面不显示
        if (includePackCategory) {
            String packDirPath = AppsSettings.get().getPackDeckDir();
            String packName = context.getString(R.string.category_pack);
            DeckSelectorUtil.DeckCategory packCategory = new DeckSelectorUtil.DeckCategory(packName);
            try {
                List<DeckFile> deckFiles = DeckUtil.getExpansionsDeckList();
                for (DeckFile df : deckFiles) {
                    packCategory.deckList.add(new DeckSelectorUtil.DeckItem(df.getName(), df.getPath()));
                }
            } catch (IOException e) {
                // ignore
            }
            // 追加ygocore/pack目录的ydk文件，不与压缩包部分混合排序
            File packDir = new File(packDirPath);
            File[] packYdks = packDir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".ydk"));
            if (packYdks != null) {
                Arrays.sort(packYdks, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : packYdks) {
                    String name = f.getName().replace(".ydk", "");
                    packCategory.deckList.add(new DeckSelectorUtil.DeckItem(name, f.getAbsolutePath()));
                }
            }
            if (!packCategory.deckList.isEmpty()) {
                rawCategories.add(packCategory);
                baseDirs.add(packDirPath);
                systemFlags.add(true);
                internalNames.add(packName);
                displayNames.add(packName);
                sortPriorities.add(0);
            }
        }

        Integer[] indices = new Integer[rawCategories.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> {
            int pa = sortPriorities.get(a);
            int pb = sortPriorities.get(b);
            if (pa != pb) return Integer.compare(pa, pb);
            return displayNames.get(a).compareToIgnoreCase(displayNames.get(b));
        });

        for (int idx : indices) {
            DeckSelectorUtil.DeckCategory cat = rawCategories.get(idx);
            cat.categoryName = displayNames.get(idx);
            displayCategories.add(cat);
            displayCategoryNames.add(displayNames.get(idx));
            catInfos.add(new CategoryInfo(cat, baseDirs.get(idx),
                    systemFlags.get(idx), sortPriorities.get(idx)));
        }
    }
    protected void reloadAndRefresh() {
        String uncatLocalName = context.getString(R.string.category_Uncategorized);
        String uncatAiName = context.getString(R.string.category_windbot_deck);
        buildCategoryData(uncatLocalName, uncatAiName);

        if (categoryAdapter != null) {
            categoryAdapter.setData(displayCategories);
        }
        selectedCategoryPos[0] = -1;
        selectedDeckPos[0] = -1;
        selectedDeckPath[0] = "";
        selectedDeckName[0] = "";
        if (categoryAdapter != null) categoryAdapter.setSelectedPosition(-1);
        if (currentDeckAdapter != null) {
            currentDeckAdapter = null;
            if (lvDecks != null) lvDecks.setAdapter(null);
        }
    }

    protected void restoreLastSelection(ListView lvCategories) {
        String lastCategory = AppsSettings.get().getLastCategory();
        String lastDeckName = AppsSettings.get().getLastDeckName();

        int lastCategoryIndex = -1;
        for (int i = 0; i < displayCategoryNames.size(); i++) {
            if (displayCategoryNames.get(i).equals(lastCategory)) {
                lastCategoryIndex = i;
                break;
            }
        }

        if (lastCategoryIndex >= 0) {
            selectedCategoryPos[0] = lastCategoryIndex;
            categoryAdapter.setSelectedPosition(lastCategoryIndex);
            updateDeckList();

            DeckSelectorUtil.DeckCategory category = displayCategories.get(lastCategoryIndex);
            int lastDeckIndex = -1;
            for (int i = 0; i < category.deckList.size(); i++) {
                if (category.deckList.get(i).deckName.equals(lastDeckName)) {
                    lastDeckIndex = i;
                    break;
                }
            }
            if (lastDeckIndex >= 0) {
                selectedDeckPos[0] = lastDeckIndex;
                if (currentDeckAdapter != null)
                    currentDeckAdapter.setSelectedPosition(lastDeckIndex);
                DeckSelectorUtil.DeckItem deck = category.deckList.get(lastDeckIndex);
                selectedDeckPath[0] = deck.deckPath;
                selectedDeckName[0] = deck.deckName;
            }

            final int catIdx = lastCategoryIndex;
            final int deckIdx = selectedDeckPos[0] >= 0 ? selectedDeckPos[0] : 0;
            lvCategories.post(() -> lvCategories.setSelection(catIdx));
            lvDecks.post(() -> lvDecks.setSelection(deckIdx));
        } else {
            // 最后分类无法匹配任何显示分类（首次安装/默认值 newcate/分类已被删）：
            // 默认停在未分类卡组并加载该分类的卡组，避免打开时列表为空、需手动切分类才出现。
            // 仅高亮展示，不触发 onDeckItemClicked，避免影响卡组编辑器等其他入口的即时加载副作用。
            String uncatName = context.getString(R.string.category_Uncategorized);
            int uncatIndex = -1;
            for (int i = 0; i < displayCategoryNames.size(); i++) {
                if (displayCategoryNames.get(i).equals(uncatName)) {
                    uncatIndex = i;
                    break;
                }
            }
            if (uncatIndex >= 0) {
                selectedCategoryPos[0] = uncatIndex;
                categoryAdapter.setSelectedPosition(uncatIndex);
                updateDeckList();
                final int catIdx = uncatIndex;
                lvCategories.post(() -> lvCategories.setSelection(catIdx));
            }
        }
        updateButtonStates();
    }

    protected void updateDeckList() {
        if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < displayCategories.size()) {
            DeckSelectorUtil.DeckCategory category = displayCategories.get(selectedCategoryPos[0]);
            currentDeckAdapter = new DeckListAdapter(context, category.deckList);
            lvDecks.setAdapter(currentDeckAdapter);
        }
    }

    protected void selectUncategorizedCategory() {
        String uncatName = context.getString(R.string.category_Uncategorized);
        int uncatIndex = -1;
        for (int i = 0; i < displayCategoryNames.size(); i++) {
            if (displayCategoryNames.get(i).equals(uncatName)) {
                uncatIndex = i;
                break;
            }
        }
        if (uncatIndex < 0) return;
        selectCategoryAndFirstDeck(uncatIndex);
    }

    protected void selectCategoryAndFirstDeck(int catIndex) {
        if (catIndex < 0 || catIndex >= displayCategories.size()) return;

        selectedCategoryPos[0] = catIndex;
        categoryAdapter.setSelectedPosition(catIndex);
        updateDeckList();

        DeckSelectorUtil.DeckCategory category = displayCategories.get(catIndex);
        if (!category.deckList.isEmpty()) {
            selectedDeckPos[0] = 0;
            DeckSelectorUtil.DeckItem deck = category.deckList.get(0);
            selectedDeckPath[0] = deck.deckPath;
            selectedDeckName[0] = deck.deckName;
            if (currentDeckAdapter != null) currentDeckAdapter.setSelectedPosition(0);
            if (listener != null) {
                String categoryName = catIndex < displayCategoryNames.size()
                        ? displayCategoryNames.get(catIndex) : "";
                listener.onDeckItemClicked(deck.deckPath, deck.deckName, categoryName);
            }
        } else {
            selectedDeckPos[0] = -1;
            selectedDeckPath[0] = "";
            selectedDeckName[0] = "";
        }

        final int idx = catIndex;
        lvCategories.post(() -> lvCategories.setSelection(idx));
        lvDecks.post(() -> lvDecks.setSelection(0));
        updateButtonStates();
    }

    protected void selectCategoryAndDeck(String categoryName, String ydkFileName) {
        int catIndex = -1;
        for (int i = 0; i < displayCategoryNames.size(); i++) {
            if (displayCategoryNames.get(i).equals(categoryName)) {
                catIndex = i;
                break;
            }
        }
        if (catIndex < 0) return;

        selectedCategoryPos[0] = catIndex;
        categoryAdapter.setSelectedPosition(catIndex);
        updateDeckList();

        DeckSelectorUtil.DeckCategory category = displayCategories.get(catIndex);
        int deckIndex = -1;
        for (int i = 0; i < category.deckList.size(); i++) {
            if (category.deckList.get(i).deckPath.endsWith(ydkFileName)) {
                deckIndex = i;
                break;
            }
        }
        if (deckIndex >= 0) {
            selectedDeckPos[0] = deckIndex;
            DeckSelectorUtil.DeckItem deck = category.deckList.get(deckIndex);
            selectedDeckPath[0] = deck.deckPath;
            selectedDeckName[0] = deck.deckName;
            if (currentDeckAdapter != null) currentDeckAdapter.setSelectedPosition(deckIndex);
            if (listener != null) {
                listener.onDeckItemClicked(deck.deckPath, deck.deckName, categoryName);
            }
        }

        final int catIdx = catIndex;
        final int deckIdx = deckIndex >= 0 ? deckIndex : 0;
        lvCategories.post(() -> lvCategories.setSelection(catIdx));
        lvDecks.post(() -> lvDecks.setSelection(deckIdx));
        updateButtonStates();
    }
    // ==================== 工具方法 ====================

    /**
     * 新建/重命名/移动单个卡组之后，同步上传到卡组广场云端（仅在萌卡账号已登录时执行），
     * 逻辑对齐 DeckManagerFragment 中对 DeckSquareApiUtil 的调用。
     *
     * @param ydkFile  操作完成后的本地卡组文件
     * @param newDeck  是否为全新卡组（新建/复制）：true 时先向服务器申请新的 deckId 再上传，
     *                 false（重命名/移动）时复用 ydk 文件中已有的 deckId 直接覆盖上传
     */
    protected void syncUploadSingleDeck(File ydkFile, boolean newDeck) {
        if (SharedPreferenceUtil.getServerToken() == null) {
            return;
        }
        LoginToken loginToken = new LoginToken(SharedPreferenceUtil.getServerUserId(),
                SharedPreferenceUtil.getServerToken());
        VUiKit.defer().when(() -> {
            try {
                List<MyDeckItem> deckItemList = new ArrayList<>();
                deckItemList.add(DeckUtil.getMyDeckItem(ydkFile));
                if (newDeck) {
                    DeckSquareApiUtil.requestIdAndPushNewDecks(deckItemList, loginToken);
                } else {
                    DeckSquareApiUtil.UploadMyDecks(deckItemList, loginToken);
                }
            } catch (IOException e) {
                return e;
            }
            return 0;
        }).fail(e -> LogUtil.e(TAG, "Upload deck failed: " + e))
                .done(result -> LogUtil.d(TAG, "Deck uploaded successfully"));
    }

    /**
     * 分类重命名后批量同步其下所有卡组（deckType 发生变化，需重新上传覆盖云端记录）。
     */
    protected void syncUploadDecks(List<File> ydkFiles) {
        if (SharedPreferenceUtil.getServerToken() == null || ydkFiles == null || ydkFiles.isEmpty()) {
            return;
        }
        LoginToken loginToken = new LoginToken(SharedPreferenceUtil.getServerUserId(),
                SharedPreferenceUtil.getServerToken());
        VUiKit.defer().when(() -> {
            try {
                List<MyDeckItem> deckItemList = new ArrayList<>();
                for (File ydk : ydkFiles) {
                    deckItemList.add(DeckUtil.getMyDeckItem(ydk));
                }
                DeckSquareApiUtil.UploadMyDecks(deckItemList, loginToken);
            } catch (IOException e) {
                return e;
            }
            return 0;
        }).fail(e -> LogUtil.e(TAG, "Upload decks failed: " + e))
                .done(result -> LogUtil.d(TAG, "Decks uploaded successfully"));
    }

    /**
     * 删除单个卡组时先删除云端对应记录；必须在 ydk 文件从磁盘删除之前构造 DeckFile，
     * 以便读取其中保存的 deckId。未登录时 DeckSquareApiUtil.deleteDecks 内部会直接跳过。
     */
    protected void syncDeleteDeckFromCloud(File ydkFile) {
        List<File> ydkFiles = new ArrayList<>();
        ydkFiles.add(ydkFile);
        syncDeleteDecksFromCloud(ydkFiles);
    }

    /**
     * 批量删除卡组（如删除整个分类）时先删除云端对应记录；同样必须在 ydk 文件从磁盘
     * 删除之前构造 DeckFile 列表，以便读取其中保存的 deckId。
     */
    protected void syncDeleteDecksFromCloud(List<File> ydkFiles) {
        if (ydkFiles == null || ydkFiles.isEmpty()) {
            return;
        }
        List<DeckFile> deckFileList = new ArrayList<>();
        for (File ydk : ydkFiles) {
            deckFileList.add(new DeckFile(ydk));
        }
        DeckSquareApiUtil.deleteDecks(deckFileList);
    }

    /**
     * 递归列出指定目录下所有的 ydk 文件（包含子目录），用于分类重命名/删除时的批量同步。
     */
    protected static List<File> listYdkFilesRecursive(File dir) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.exists()) {
            return result;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                result.addAll(listYdkFilesRecursive(f));
            } else if (f.getName().toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX)) {
                result.add(f);
            }
        }
        return result;
    }

    protected CategoryInfo getSelectedCategoryInfo() {
        if (selectedCategoryPos[0] < 0 || selectedCategoryPos[0] >= catInfos.size()) return null;
        return catInfos.get(selectedCategoryPos[0]);
    }

    protected Activity getActivity() {
        return (context instanceof Activity) ? (Activity) context : null;
    }

    protected static boolean deleteFolderRecursive(File dir) {
        if (dir == null || !dir.exists()) return false;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFolderRecursive(child);
                }
            }
        }
        return dir.delete();
    }

    protected static boolean copyFile(File src, File dst) {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    // ==================== 适配器 ====================

    protected static class CategoryListAdapter extends BaseAdapter {
        private static final int SELECTED_BG_COLOR = YGOUtil.c(R.color.ygopro_line_color);
        private Context context;
        private List<DeckSelectorUtil.DeckCategory> categories;
        private int selectedPosition = -1;

        public CategoryListAdapter(Context context, List<DeckSelectorUtil.DeckCategory> categories) {
            this.context = context;
            this.categories = categories;
        }

        public void setData(List<DeckSelectorUtil.DeckCategory> data) {
            this.categories = data;
            notifyDataSetChanged();
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
                convertView = LayoutInflater.from(DialogScale.wrap(context)).inflate(R.layout.item_bot_list, parent, false);
                holder = new ViewHolder();
                holder.textView = convertView.findViewById(R.id.tv_bot_item);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.textView.setText(categories.get(position).categoryName);
            holder.textView.setTextSize(8);
            holder.textView.setBackgroundColor(position == selectedPosition ? SELECTED_BG_COLOR : Color.TRANSPARENT);
            holder.textView.setTextColor(YGOUtil.c(position == selectedPosition ? R.color.colorNavy : R.color.white));
            return convertView;
        }

        private static class ViewHolder {
            TextView textView;
        }
    }

    protected static class DeckListAdapter extends BaseAdapter {
        private static final int SELECTED_BG_COLOR = YGOUtil.c(R.color.ygopro_line_color);
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
                convertView = LayoutInflater.from(DialogScale.wrap(context)).inflate(R.layout.item_bot_list, parent, false);
                holder = new ViewHolder();
                holder.textView = convertView.findViewById(R.id.tv_bot_item);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.textView.setText(decks.get(position).toString());
            holder.textView.setTextSize(8);
            holder.textView.setBackgroundColor(position == selectedPosition ? SELECTED_BG_COLOR : Color.TRANSPARENT);
            holder.textView.setTextColor(YGOUtil.c(position == selectedPosition ? R.color.colorNavy : R.color.white));
            return convertView;
        }

        private static class ViewHolder {
            TextView textView;
        }
    }
}

package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.utils.DeckSelectorUtil;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

public class DeckSelectorDialog {

    private Context context;
    private PopupWindow popupWindow;
    private OnDeckSelectedListener listener;
    private DraggablePopupHelper draggableHelper;

    private final List<CategoryInfo> catInfos = new ArrayList<>();
    private final List<DeckSelectorUtil.DeckCategory> displayCategories = new ArrayList<>();
    private final List<String> displayCategoryNames = new ArrayList<>();

    private CategoryListAdapter categoryAdapter;
    private DeckListAdapter currentDeckAdapter;
    private ListView lvCategories;
    private ListView lvDecks;

    private Button btnRenameCategory;
    private Button btnDeleteCategory;
    private Button btnNewDeck;
    private Button btnRenameDeck;
    private Button btnDeleteDeck;
    private Button btnMoveToCategory;

    private final int[] selectedCategoryPos = {-1};
    private final int[] selectedDeckPos = {-1};
    private final String[] selectedDeckPath = {""};
    private final String[] selectedDeckName = {""};

    private String localDeckDirPath;
    private String aiDeckDirPath;

    //是否显示"卡包展示"分类（ygocore/pack）：卡组编辑器调用时为true，玩家等待界面默认false
    private boolean includePackCategory = false;

    private final List<Button> operationButtons = new ArrayList<>();

    private final StringManager mStringManager = DataManager.get().getStringManager();

    private static class CategoryInfo {
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

    public DeckSelectorDialog(Context context) {
        this.context = context;
    }

    public void setOnDeckSelectedListener(OnDeckSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * 设置是否显示"卡包展示"分类（路径ygocore/pack）。
     * 卡组编辑器调用时设为true；玩家等待界面保持默认false不显示。
     */
    public void setIncludePackCategory(boolean include) {
        this.includePackCategory = include;
    }

    public void show(View anchorView) {
        float density = context.getResources().getDisplayMetrics().density;

        String uncatLocalName = context.getString(R.string.category_Uncategorized);
        String uncatAiName = context.getString(R.string.category_windbot_deck);

        localDeckDirPath = AppsSettings.get().getDeckDir();
        aiDeckDirPath = AppsSettings.get().getAiDeckDir();

        buildCategoryData(uncatLocalName, uncatAiName);

        View contentView = LayoutInflater.from(context).inflate(R.layout.popup_window_deck_selector, null);

        lvCategories = contentView.findViewById(R.id.lv_categories);
        lvDecks = contentView.findViewById(R.id.lv_decks);
        Button btnConfirm = contentView.findViewById(R.id.btn_confirm_deck);
        Button btnNewCategory = contentView.findViewById(R.id.btn_new_category);
        btnRenameCategory = contentView.findViewById(R.id.btn_rename_category);
        btnDeleteCategory = contentView.findViewById(R.id.btn_delete_category);
        btnNewDeck = contentView.findViewById(R.id.btn_new_deck);
        btnRenameDeck = contentView.findViewById(R.id.btn_rename_deck);
        btnDeleteDeck = contentView.findViewById(R.id.btn_delete_deck);
        btnMoveToCategory = contentView.findViewById(R.id.btn_move_to_category);
        Button btnCopyToCategory = contentView.findViewById(R.id.btn_copy_to_category);

        operationButtons.clear();
        operationButtons.add(btnNewCategory);
        operationButtons.add(btnRenameCategory);
        operationButtons.add(btnDeleteCategory);
        operationButtons.add(btnNewDeck);
        operationButtons.add(btnRenameDeck);
        operationButtons.add(btnDeleteDeck);
        operationButtons.add(btnMoveToCategory);
        operationButtons.add(btnCopyToCategory);

        categoryAdapter = new CategoryListAdapter(context, displayCategories);
        lvCategories.setAdapter(categoryAdapter);

        restoreLastSelection(lvCategories);

        lvCategories.setOnItemClickListener((parent, view, position, id) -> {
            selectedCategoryPos[0] = position;
            categoryAdapter.setSelectedPosition(position);
            updateDeckList();
            DeckSelectorUtil.DeckCategory category = displayCategories.get(position);
            if (!category.deckList.isEmpty()) {
                selectedDeckPos[0] = 0;
                DeckSelectorUtil.DeckItem deck = category.deckList.get(0);
                selectedDeckPath[0] = deck.deckPath;
                selectedDeckName[0] = deck.deckName;
                if (currentDeckAdapter != null) currentDeckAdapter.setSelectedPosition(0);
                if (listener != null) {
                    String categoryName = position < displayCategoryNames.size()
                            ? displayCategoryNames.get(position) : "";
                    listener.onDeckItemClicked(deck.deckPath, deck.deckName, categoryName);
                }
            } else {
                selectedDeckPos[0] = -1;
                selectedDeckPath[0] = "";
                selectedDeckName[0] = "";
            }
            updateButtonStates();
        });

        lvDecks.setOnItemClickListener((parent, view, position, id) -> {
            selectedDeckPos[0] = position;
            if (currentDeckAdapter != null) {
                currentDeckAdapter.setSelectedPosition(position);
            }
            if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < displayCategories.size()) {
                DeckSelectorUtil.DeckCategory category = displayCategories.get(selectedCategoryPos[0]);
                if (position >= 0 && position < category.deckList.size()) {
                    DeckSelectorUtil.DeckItem deck = category.deckList.get(position);
                    selectedDeckPath[0] = deck.deckPath;
                    selectedDeckName[0] = deck.deckName;
                    //点击即加载：通知调用方立即加载该卡组
                    if (listener != null) {
                        String categoryName = selectedCategoryPos[0] < displayCategoryNames.size()
                                ? displayCategoryNames.get(selectedCategoryPos[0]) : "";
                        listener.onDeckItemClicked(deck.deckPath, deck.deckName, categoryName);
                    }
                }
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedDeckPath[0].isEmpty()) {
                YGOUtil.show("请先选择一个卡组", Gravity.CENTER);
                return;
            }
            String categoryName = "";
            if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < displayCategoryNames.size()) {
                categoryName = displayCategoryNames.get(selectedCategoryPos[0]);
            }
            popupWindow.dismiss();
            if (listener != null) {
                listener.onDeckSelected(selectedDeckPath[0], selectedDeckName[0], categoryName);
            }
        });

        btnNewCategory.setOnClickListener(v -> doNewCategory());
        btnRenameCategory.setOnClickListener(v -> doRenameCategory());
        btnDeleteCategory.setOnClickListener(v -> doDeleteCategory());
        btnNewDeck.setOnClickListener(v -> doCreateDeck());
        btnRenameDeck.setOnClickListener(v -> doRenameDeck());
        btnDeleteDeck.setOnClickListener(v -> doDeleteDeck());
        btnMoveToCategory.setOnClickListener(v -> doMoveToCategory());
        btnCopyToCategory.setOnClickListener(v -> doCopyToCategory());

        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density * 0.7);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(contentView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "deck_selector_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, contentView, popupWidth, popupHeight);
        draggableHelper.showPopup(popupWindow, anchorView, Gravity.CENTER, (int) (100 * density), (int) (-20 * density));
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void setOperationButtonsEnabled(boolean enabled) {
        for (Button btn : operationButtons) {
            btn.setEnabled(enabled);
            btn.setTextColor(YGOUtil.c(enabled ? R.color.white : R.color.item_bg));
        }
    }

    private void updateButtonStates() {
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

    private void setButtonEnabled(Button btn, boolean enabled) {
        if (btn == null) return;
        btn.setEnabled(enabled);
        btn.setTextColor(YGOUtil.c(enabled ? R.color.white : R.color.item_bg));
    }

    // ==================== 数据构建 ====================

    private void buildCategoryData(String uncatLocalName, String uncatAiName) {
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
                java.util.Arrays.sort(packYdks, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
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
        java.util.Arrays.sort(indices, (a, b) -> {
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

    private void reloadAndRefresh() {
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

    private void restoreLastSelection(ListView lvCategories) {
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
        }
        updateButtonStates();
    }

    private void updateDeckList() {
        if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < displayCategories.size()) {
            DeckSelectorUtil.DeckCategory category = displayCategories.get(selectedCategoryPos[0]);
            currentDeckAdapter = new DeckListAdapter(context, category.deckList);
            lvDecks.setAdapter(currentDeckAdapter);
        }
    }

    private void selectUncategorizedCategory() {
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

    private void selectCategoryAndFirstDeck(int catIndex) {
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

    private void selectCategoryAndDeck(String categoryName, String ydkFileName) {
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

    // ==================== 分类操作 ====================

    private void doNewCategory() {
        Activity activity = getActivity();
        if (activity == null) return;

        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setBackground(activity.getDrawable(R.drawable.ygopro_base_background));
        editText.setSingleLine();

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1469, "请输入分类名:"));
        dialog.setContentView(editText);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setPositiveButtonText(mStringManager.getSystemString(1302, "确定"));
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setPositiveButton(v -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                YGOUtil.show("请输入分类名称", Gravity.CENTER);
                return;
            }
            for (CategoryInfo ci : catInfos) {
                if (ci.category.categoryName.equals(name)) {
                    YGOUtil.show("分类已存在", Gravity.CENTER);
                    return;
                }
            }
            File folder = new File(localDeckDirPath, name);
            if (folder.mkdirs()) {
                YGOUtil.show("创建成功", Gravity.CENTER);
                reloadAndRefresh();
            } else {
                YGOUtil.show("创建失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }

    private void doRenameCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        CategoryInfo ci = getSelectedCategoryInfo();
        if (ci == null) {
            YGOUtil.show("请先选择一个分类", Gravity.CENTER);
            return;
        }
        if (ci.isSystem) {
            YGOUtil.show("系统分类不可重命名", Gravity.CENTER);
            return;
        }

        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setBackground(activity.getDrawable(R.drawable.ygopro_base_background));
        editText.setSingleLine();
        editText.setText(ci.category.categoryName);

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1469, "请输入分类名:"));
        dialog.setContentView(editText);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setPositiveButtonText(mStringManager.getSystemString(1302, "确定"));
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setPositiveButton(v -> {
            String newName = editText.getText().toString().trim();
            if (newName.isEmpty()) {
                YGOUtil.show("请输入新名称", Gravity.CENTER);
                return;
            }
            File oldDir = new File(ci.baseDirPath, ci.category.categoryName);
            File newDir = new File(ci.baseDirPath, newName);
            if (newDir.exists()) {
                YGOUtil.show("目标分类已存在", Gravity.CENTER);
                return;
            }
            if (oldDir.renameTo(newDir)) {
                YGOUtil.show("重命名成功", Gravity.CENTER);
                reloadAndRefresh();
                int newCatIndex = displayCategoryNames.indexOf(newName);
                if (newCatIndex >= 0) {
                    selectCategoryAndFirstDeck(newCatIndex);
                }
            } else {
                YGOUtil.show("重命名失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }

    private void doDeleteCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        CategoryInfo ci = getSelectedCategoryInfo();
        if (ci == null) {
            YGOUtil.show("请先选择一个分类", Gravity.CENTER);
            return;
        }
        if (ci.isSystem) {
            YGOUtil.show("系统分类不可删除", Gravity.CENTER);
            return;
        }

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1470, "确实要删除此分类和分类下全部卡组吗？"));
        dialog.setMessage(ci.category.categoryName);
        dialog.setMessageBackgroundColor(YGOUtil.c(R.color.colorNavy));
        dialog.setMessageGravity(Gravity.CENTER);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setPositiveButtonText(mStringManager.getSystemString(1308, "删除"));
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setPositiveButton(v -> {
            File dir = new File(ci.baseDirPath, ci.category.categoryName);
            if (deleteFolderRecursive(dir)) {
                YGOUtil.show(mStringManager.getSystemString(1338, "删除成功"), Gravity.CENTER);
                reloadAndRefresh();
                selectUncategorizedCategory();
            } else {
                YGOUtil.show(mStringManager.getSystemString(1476, "删除失败"), Gravity.CENTER);
            }
        });
        dialog.show();
    }

    // ==================== 卡组操作 ====================

    private void doCreateDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        CategoryInfo ci = getSelectedCategoryInfo();
        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setBackground(activity.getDrawable(R.drawable.ygopro_base_background));
        editText.setSingleLine();

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1471, "请输入卡组名:"));
        dialog.setContentView(editText);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setPositiveButtonText(mStringManager.getSystemString(1302, "确定"));
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setPositiveButton(v -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                YGOUtil.show("请输入卡组名称", Gravity.CENTER);
                return;
            }
            String dirPath = ci.isSystem ? ci.baseDirPath : ci.baseDirPath + "/" + ci.category.categoryName;
            File deckFile = new File(dirPath, name + ".ydk");
            try {
                if (deckFile.getParentFile() != null) deckFile.getParentFile().mkdirs();
                if (deckFile.createNewFile()) {
                    try (FileOutputStream fos = new FileOutputStream(deckFile)) {
                        fos.write("#created by YGOMobile\n#main\n#extra\n!side\n".getBytes());
                    }
                    YGOUtil.show("创建成功", Gravity.CENTER);
                    String ydkFileName = deckFile.getName();
                    String catName = ci.category.categoryName;
                    reloadAndRefresh();
                    selectCategoryAndDeck(catName, ydkFileName);
                } else {
                    YGOUtil.show("创建失败", Gravity.CENTER);
                }
            } catch (IOException e) {
                YGOUtil.show("创建失败: " + e.getMessage(), Gravity.CENTER);
            }
        });
        dialog.show();
    }

    private void doRenameDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            YGOUtil.show("请先选择一个卡组", Gravity.CENTER);
            return;
        }

        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setBackground(activity.getDrawable(R.drawable.ygopro_base_background));
        editText.setSingleLine();
        editText.setText(selectedDeckName[0]);

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1471, "请输入卡组名:"));
        dialog.setContentView(editText);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setPositiveButtonText(mStringManager.getSystemString(1302, "确定"));
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setPositiveButton(v -> {
            String newName = editText.getText().toString().trim();
            if (newName.isEmpty()) {
                YGOUtil.show("请输入新名称", Gravity.CENTER);
                return;
            }
            File oldFile = new File(selectedDeckPath[0]);
            File newFile = new File(oldFile.getParent(), newName + ".ydk");
            if (newFile.exists()) {
                YGOUtil.show("目标卡组已存在", Gravity.CENTER);
                return;
            }
            if (oldFile.renameTo(newFile)) {
                YGOUtil.show("重命名成功", Gravity.CENTER);
                CategoryInfo ci = getSelectedCategoryInfo();
                String catName = ci != null ? ci.category.categoryName : "";
                String ydkFileName = newFile.getName();
                reloadAndRefresh();
                selectCategoryAndDeck(catName, ydkFileName);
            } else {
                YGOUtil.show("重命名失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }

    private void doDeleteDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            YGOUtil.show("请先选择一个卡组", Gravity.CENTER);
            return;
        }

        CategoryInfo ci = getSelectedCategoryInfo();
        String catName = ci != null ? ci.category.categoryName : "";

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1337, "是否删除这个卡组？"));
        dialog.setMessage(catName + "|" + selectedDeckName[0]);
        dialog.setMessageBackgroundColor(YGOUtil.c(R.color.colorNavy));
        dialog.setMessageGravity(Gravity.CENTER);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setPositiveButtonText(mStringManager.getSystemString(1308, "删除"));
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setPositiveButton(v -> {
            File file = new File(selectedDeckPath[0]);
            int savedCatPos = selectedCategoryPos[0];
            if (file.delete()) {
                YGOUtil.show(mStringManager.getSystemString(1338, "删除成功"), Gravity.CENTER);
                reloadAndRefresh();
                selectCategoryAndFirstDeck(savedCatPos);
            } else {
                YGOUtil.show(mStringManager.getSystemString(1476, "删除失败"), Gravity.CENTER);
            }
        });
        dialog.show();
    }

    private void doMoveToCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            YGOUtil.show("请先选择一个卡组", Gravity.CENTER);
            return;
        }

        List<String> otherNames = new ArrayList<>();
        List<CategoryInfo> otherInfos = new ArrayList<>();
        for (int i = 0; i < catInfos.size(); i++) {
            if (i != selectedCategoryPos[0] && catInfos.get(i).sortPriority > 1) {
                otherNames.add(displayCategoryNames.get(i));
                otherInfos.add(catInfos.get(i));
            }
        }
        if (otherNames.isEmpty()) {
            YGOUtil.show("没有其他分类", Gravity.CENTER);
            return;
        }

        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(otherNames);

        ListView listView = new ListView(context);
        listView.setBackgroundColor(0xFF1A3A4A);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xFF3A5A6B));
        listView.setDividerHeight(1);
        listView.setAdapter(adapter);

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1472, "请选择要移动到的分类:"));
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setContentView(listView);
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            CategoryInfo target = otherInfos.get(pos);
            String targetDir = target.isSystem ? target.baseDirPath
                    : target.baseDirPath + "/" + target.category.categoryName;
            File src = new File(selectedDeckPath[0]);
            String ydkFileName = src.getName();
            String targetCatName = target.category.categoryName;
            File dest = new File(targetDir, ydkFileName);
            if (dest.exists()) {
                YGOUtil.show(mStringManager.getSystemString(1475, "已存在同名卡组"), Gravity.CENTER);
                return;
            }
            new File(targetDir).mkdirs();
            if (src.renameTo(dest)) {
                YGOUtil.show("移动成功", Gravity.CENTER);
                dialog.dismiss();
                reloadAndRefresh();
                selectCategoryAndDeck(targetCatName, ydkFileName);
            } else {
                YGOUtil.show("移动失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }

    private void doCopyToCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            YGOUtil.show("请先选择一个卡组", Gravity.CENTER);
            return;
        }

        List<String> otherNames = new ArrayList<>();
        List<CategoryInfo> otherInfos = new ArrayList<>();
        for (int i = 0; i < catInfos.size(); i++) {
            if (i != selectedCategoryPos[0] && catInfos.get(i).sortPriority > 1) {
                otherNames.add(displayCategoryNames.get(i));
                otherInfos.add(catInfos.get(i));
            }
        }
        if (otherNames.isEmpty()) {
            YGOUtil.show("没有其他分类", Gravity.CENTER);
            return;
        }

        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(otherNames);

        ListView listView = new ListView(context);
        listView.setBackgroundColor(0xFF1A3A4A);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xFF3A5A6B));
        listView.setDividerHeight(1);
        listView.setAdapter(adapter);

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1473, "请选择要复制到的分类:"));
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setContentView(listView);
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            CategoryInfo target = otherInfos.get(pos);
            String targetDir = target.isSystem ? target.baseDirPath
                    : target.baseDirPath + "/" + target.category.categoryName;
            File src = new File(selectedDeckPath[0]);
            String ydkFileName = src.getName();
            String targetCatName = target.category.categoryName;
            File dest = new File(targetDir, ydkFileName);
            if (dest.exists()) {
                YGOUtil.show(mStringManager.getSystemString(1475, "已存在同名卡组"), Gravity.CENTER);
                return;
            }
            new File(targetDir).mkdirs();
            if (copyFile(src, dest)) {
                YGOUtil.show("复制成功", Gravity.CENTER);
                dialog.dismiss();
                reloadAndRefresh();
                selectCategoryAndDeck(targetCatName, ydkFileName);
            } else {
                YGOUtil.show("复制失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }

    // ==================== 工具方法 ====================

    private CategoryInfo getSelectedCategoryInfo() {
        if (selectedCategoryPos[0] < 0 || selectedCategoryPos[0] >= catInfos.size()) return null;
        return catInfos.get(selectedCategoryPos[0]);
    }

    private Activity getActivity() {
        return (context instanceof Activity) ? (Activity) context : null;
    }

    private static boolean deleteFolderRecursive(File dir) {
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

    private static boolean copyFile(File src, File dst) {
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

    private static class CategoryListAdapter extends BaseAdapter {
        private static final int SELECTED_BG_COLOR = YGOUtil.c(R.color.colorMain);
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
                convertView = LayoutInflater.from(context).inflate(R.layout.item_bot_list, parent, false);
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

    private static class DeckListAdapter extends BaseAdapter {
        private static final int SELECTED_BG_COLOR = YGOUtil.c(R.color.colorMain);
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
                convertView = LayoutInflater.from(context).inflate(R.layout.item_bot_list, parent, false);
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
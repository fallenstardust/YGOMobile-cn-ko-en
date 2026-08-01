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
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.DeckSelectorUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.YGOUtil;

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
    private ListView lvDecks;

    private final int[] selectedCategoryPos = {-1};
    private final int[] selectedDeckPos = {-1};
    private final String[] selectedDeckPath = {""};
    private final String[] selectedDeckName = {""};

    private String localDeckDirPath;
    private String aiDeckDirPath;

    //是否显示"卡包展示"分类（ygocore/pack）：卡组编辑器调用时为true，玩家等待界面默认false
    private boolean includePackCategory = false;

    private final List<Button> operationButtons = new ArrayList<>();

    private static class CategoryInfo {
        DeckSelectorUtil.DeckCategory category;
        String baseDirPath;
        boolean isSystem;

        CategoryInfo(DeckSelectorUtil.DeckCategory category, String baseDirPath, boolean isSystem) {
            this.category = category;
            this.baseDirPath = baseDirPath;
            this.isSystem = isSystem;
        }
    }

    public interface OnDeckSelectedListener {
        void onDeckSelected(String deckPath, String deckName, String categoryName);
        void onCancelled();
        //点击卡组item即触发（用于卡组编辑器即时加载）；默认空实现，玩家等待界面等无需处理
        default void onDeckItemClicked(String deckPath, String deckName, String categoryName) {}
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

        ListView lvCategories = contentView.findViewById(R.id.lv_categories);
        lvDecks = contentView.findViewById(R.id.lv_decks);
        Button btnConfirm = contentView.findViewById(R.id.btn_confirm_deck);
        Button btnNewCategory = contentView.findViewById(R.id.btn_new_category);
        Button btnRenameCategory = contentView.findViewById(R.id.btn_rename_category);
        Button btnDeleteCategory = contentView.findViewById(R.id.btn_delete_category);
        Button btnNewDeck = contentView.findViewById(R.id.btn_new_deck);
        Button btnRenameDeck = contentView.findViewById(R.id.btn_rename_deck);
        Button btnDeleteDeck = contentView.findViewById(R.id.btn_delete_deck);
        Button btnMoveToCategory = contentView.findViewById(R.id.btn_move_to_category);
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
            selectedDeckPos[0] = -1;
            selectedDeckPath[0] = "";
            selectedDeckName[0] = "";
            categoryAdapter.setSelectedPosition(position);
            updateDeckList();
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
                Toast.makeText(context, "请先选择一个卡组", Toast.LENGTH_SHORT).show();
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
        btnNewDeck.setOnClickListener(v -> doNewDeck());
        btnRenameDeck.setOnClickListener(v -> doRenameDeck());
        btnDeleteDeck.setOnClickListener(v -> doDeleteDeck());
        btnMoveToCategory.setOnClickListener(v -> doMoveToCategory());
        btnCopyToCategory.setOnClickListener(v -> doCopyToCategory());

        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density*0.7);
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
        draggableHelper.setupDraggablePopup(popupWindow, contentView);
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

        File localDeckDir = new File(localDeckDirPath);
        for (DeckSelectorUtil.DeckCategory c : DeckSelectorUtil.loadDeckCategories(localDeckDir)) {
            rawCategories.add(c);
            baseDirs.add(localDeckDirPath);
            systemFlags.add(c.categoryName.equals("未分类卡组"));
            internalNames.add(c.categoryName);
            displayNames.add(c.categoryName.equals("未分类卡组") ? uncatLocalName : c.categoryName);
        }

        File aiDeckDir = new File(aiDeckDirPath);
        for (DeckSelectorUtil.DeckCategory c : DeckSelectorUtil.loadDeckCategories(aiDeckDir)) {
            rawCategories.add(c);
            baseDirs.add(aiDeckDirPath);
            systemFlags.add(c.categoryName.equals("未分类卡组"));
            internalNames.add(c.categoryName);
            displayNames.add(c.categoryName.equals("未分类卡组") ? uncatAiName : c.categoryName);
        }

        //卡包展示分类（ygocore/pack）：仅卡组编辑器调用时显示，玩家等待界面不显示
        if (includePackCategory) {
            String packDirPath = AppsSettings.get().getPackDeckDir();
            String packName = context.getString(R.string.category_pack);
            DeckSelectorUtil.DeckCategory packCategory =
                    DeckSelectorUtil.loadSingleCategory(new File(packDirPath), packName);
            if (!packCategory.deckList.isEmpty()) {
                rawCategories.add(packCategory);
                baseDirs.add(packDirPath);
                systemFlags.add(true);
                internalNames.add(packName);
                displayNames.add(packName);
            }
        }

        Integer[] indices = new Integer[rawCategories.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> {
            boolean aSys = systemFlags.get(a);
            boolean bSys = systemFlags.get(b);
            if (aSys != bSys) return aSys ? -1 : 1;
            return displayNames.get(a).compareToIgnoreCase(displayNames.get(b));
        });

        for (int idx : indices) {
            DeckSelectorUtil.DeckCategory cat = rawCategories.get(idx);
            cat.categoryName = displayNames.get(idx);
            displayCategories.add(cat);
            displayCategoryNames.add(displayNames.get(idx));
            catInfos.add(new CategoryInfo(cat, baseDirs.get(idx), systemFlags.get(idx)));
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
                if (currentDeckAdapter != null) currentDeckAdapter.setSelectedPosition(lastDeckIndex);
                DeckSelectorUtil.DeckItem deck = category.deckList.get(lastDeckIndex);
                selectedDeckPath[0] = deck.deckPath;
                selectedDeckName[0] = deck.deckName;
            }

            final int catIdx = lastCategoryIndex;
            final int deckIdx = selectedDeckPos[0] >= 0 ? selectedDeckPos[0] : 0;
            lvCategories.post(() -> lvCategories.setSelection(catIdx));
            lvDecks.post(() -> lvDecks.setSelection(deckIdx));
        }
    }

    private void updateDeckList() {
        if (selectedCategoryPos[0] >= 0 && selectedCategoryPos[0] < displayCategories.size()) {
            DeckSelectorUtil.DeckCategory category = displayCategories.get(selectedCategoryPos[0]);
            currentDeckAdapter = new DeckListAdapter(context, category.deckList);
            lvDecks.setAdapter(currentDeckAdapter);
        }
    }

    // ==================== 分类操作 ====================

    private void doNewCategory() {
        Activity activity = getActivity();
        if (activity == null) return;

        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setSingleLine();

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("新建分类");
        dialog.setContentView(editText);
        dialog.setLeftButtonText("确定");
        dialog.setLeftButtonListener((d, w) -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(context, "请输入分类名称", Toast.LENGTH_SHORT).show();
                return;
            }
            for (CategoryInfo ci : catInfos) {
                if (ci.category.categoryName.equals(name)) {
                    Toast.makeText(context, "分类已存在", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            File folder = new File(localDeckDirPath, name);
            if (folder.mkdirs()) {
                Toast.makeText(context, "创建成功", Toast.LENGTH_SHORT).show();
                d.dismiss();
                reloadAndRefresh();
            } else {
                Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void doRenameCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        CategoryInfo ci = getSelectedCategoryInfo();
        if (ci == null) {
            Toast.makeText(context, "请先选择一个分类", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ci.isSystem) {
            Toast.makeText(context, "系统分类不可重命名", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setSingleLine();
        editText.setText(ci.category.categoryName);

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("重命名分类");
        dialog.setContentView(editText);
        dialog.setLeftButtonText("确定");
        dialog.setLeftButtonListener((d, w) -> {
            String newName = editText.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(context, "请输入新名称", Toast.LENGTH_SHORT).show();
                return;
            }
            File oldDir = new File(ci.baseDirPath, ci.category.categoryName);
            File newDir = new File(ci.baseDirPath, newName);
            if (newDir.exists()) {
                Toast.makeText(context, "目标分类已存在", Toast.LENGTH_SHORT).show();
                return;
            }
            if (oldDir.renameTo(newDir)) {
                Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show();
                d.dismiss();
                reloadAndRefresh();
            } else {
                Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void doDeleteCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        CategoryInfo ci = getSelectedCategoryInfo();
        if (ci == null) {
            Toast.makeText(context, "请先选择一个分类", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ci.isSystem) {
            Toast.makeText(context, "系统分类不可删除", Toast.LENGTH_SHORT).show();
            return;
        }

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("删除分类");
        dialog.setMessage("确定要删除分类\"" + ci.category.categoryName + "\"及其所有卡组吗？");
        dialog.setLeftButtonText("删除");
        dialog.setLeftButtonListener((d, w) -> {
            File dir = new File(ci.baseDirPath, ci.category.categoryName);
            if (deleteFolderRecursive(dir)) {
                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show();
                d.dismiss();
                reloadAndRefresh();
            } else {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    // ==================== 卡组操作 ====================

    private void doNewDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        CategoryInfo ci = getSelectedCategoryInfo();
        if (ci == null) {
            Toast.makeText(context, "请先选择一个分类", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setSingleLine();

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("新建卡组");
        dialog.setContentView(editText);
        dialog.setLeftButtonText("确定");
        dialog.setLeftButtonListener((d, w) -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(context, "请输入卡组名称", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(context, "创建成功", Toast.LENGTH_SHORT).show();
                    d.dismiss();
                    reloadAndRefresh();
                } else {
                    Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(context, "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void doRenameDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            Toast.makeText(context, "请先选择一个卡组", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText editText = new EditText(context);
        editText.setGravity(Gravity.CENTER);
        editText.setSingleLine();
        editText.setText(selectedDeckName[0]);

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("重命名卡组");
        dialog.setContentView(editText);
        dialog.setLeftButtonText("确定");
        dialog.setLeftButtonListener((d, w) -> {
            String newName = editText.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(context, "请输入新名称", Toast.LENGTH_SHORT).show();
                return;
            }
            File oldFile = new File(selectedDeckPath[0]);
            File newFile = new File(oldFile.getParent(), newName + ".ydk");
            if (newFile.exists()) {
                Toast.makeText(context, "目标卡组已存在", Toast.LENGTH_SHORT).show();
                return;
            }
            if (oldFile.renameTo(newFile)) {
                Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show();
                d.dismiss();
                reloadAndRefresh();
            } else {
                Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void doDeleteDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            Toast.makeText(context, "请先选择一个卡组", Toast.LENGTH_SHORT).show();
            return;
        }

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("删除卡组");
        dialog.setMessage("确定要删除卡组\"" + selectedDeckName[0] + "\"吗？");
        dialog.setLeftButtonText("删除");
        dialog.setLeftButtonListener((d, w) -> {
            File file = new File(selectedDeckPath[0]);
            if (file.delete()) {
                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show();
                d.dismiss();
                reloadAndRefresh();
            } else {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void doMoveToCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            Toast.makeText(context, "请先选择一个卡组", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> otherNames = new ArrayList<>();
        List<CategoryInfo> otherInfos = new ArrayList<>();
        for (int i = 0; i < catInfos.size(); i++) {
            if (i != selectedCategoryPos[0]) {
                otherNames.add(displayCategoryNames.get(i));
                otherInfos.add(catInfos.get(i));
            }
        }
        if (otherNames.isEmpty()) {
            Toast.makeText(context, "没有其他分类", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(otherNames);

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("移动到分类");
        ListView listView = new ListView(context);
        listView.setBackgroundColor(0xFF1A3A4A);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xFF3A5A6B));
        listView.setDividerHeight(1);
        dialog.setContentView(listView);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            CategoryInfo target = otherInfos.get(pos);
            String targetDir = target.isSystem ? target.baseDirPath
                    : target.baseDirPath + "/" + target.category.categoryName;
            File src = new File(selectedDeckPath[0]);
            File dest = new File(targetDir, src.getName());
            new File(targetDir).mkdirs();
            if (src.renameTo(dest)) {
                Toast.makeText(context, "移动成功", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                reloadAndRefresh();
            } else {
                Toast.makeText(context, "移动失败", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void doCopyToCategory() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            Toast.makeText(context, "请先选择一个卡组", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> otherNames = new ArrayList<>();
        List<CategoryInfo> otherInfos = new ArrayList<>();
        for (int i = 0; i < catInfos.size(); i++) {
            if (i != selectedCategoryPos[0]) {
                otherNames.add(displayCategoryNames.get(i));
                otherInfos.add(catInfos.get(i));
            }
        }
        if (otherNames.isEmpty()) {
            Toast.makeText(context, "没有其他分类", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(otherNames);

        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("复制到分类");
        ListView listView = new ListView(context);
        listView.setBackgroundColor(0xFF1A3A4A);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xFF3A5A6B));
        listView.setDividerHeight(1);
        dialog.setContentView(listView);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            CategoryInfo target = otherInfos.get(pos);
            String targetDir = target.isSystem ? target.baseDirPath
                    : target.baseDirPath + "/" + target.category.categoryName;
            File src = new File(selectedDeckPath[0]);
            File dest = new File(targetDir, src.getName());
            new File(targetDir).mkdirs();
            if (copyFile(src, dest)) {
                Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                reloadAndRefresh();
            } else {
                Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
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

        @Override public int getCount() { return categories.size(); }
        @Override public Object getItem(int position) { return categories.get(position); }
        @Override public long getItemId(int position) { return position; }

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

        private static class ViewHolder { TextView textView; }
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

        @Override public int getCount() { return decks.size(); }
        @Override public Object getItem(int position) { return decks.get(position); }
        @Override public long getItemId(int position) { return position; }

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

        private static class ViewHolder { TextView textView; }
    }
}
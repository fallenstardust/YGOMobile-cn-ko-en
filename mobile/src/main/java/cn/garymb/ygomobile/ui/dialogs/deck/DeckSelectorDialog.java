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
import java.util.List;

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
 * Public entry of the split inheritance chain (DeckSelectorDialogBase <- this).
 * Keeps the original class name and public API: popup assembly (show),
 * category/deck operations and window lifecycle methods.
 */
public class DeckSelectorDialog extends DeckSelectorDialogBase {

    public DeckSelectorDialog(Context context) {
        super(context);
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

    public void setDisableOperationButtons(boolean disable) {
        this.disableOperationButtons = disable;
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
        
        // 横屏时，根据 Activity 的实际宽度的 2/3 来限制弹窗宽度；竖屏保持正方形样式
        final android.app.Activity act = context instanceof android.app.Activity
                ? (android.app.Activity) context : null;
        if (act != null && act.getWindow() != null && act.getWindow().getAttributes() != null) {
            DisplayMetrics screen = new DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(screen);
            // 判断横屏还是竖屏
            boolean isLandscape = screen.widthPixels >= screen.heightPixels;
            if (isLandscape) {
                // 横屏：宽度取屏幕宽度的 2/3
                int maxWidth = (int) (screen.widthPixels * 0.66f);
                int[] fitted = DraggablePopupHelper.fitSizeToScreen(context, popupWidth, popupHeight);
                if (fitted[0] > maxWidth) {
                    // 按比例缩小高度
                    float ratio = (float) maxWidth / fitted[0];
                    fitted[0] = maxWidth;
                    fitted[1] = (int) (fitted[1] * ratio);
                }
                popupWidth = fitted[0];
                popupHeight = fitted[1];
            }
            // 竖屏：保持原有设计尺寸不变（已适配正方形）
        }
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

        if (disableOperationButtons) {
            setOperationButtonsEnabled(false);
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /** 当前是否正在显示：供调用方实现"再次点击收起"的切换式交互 */
    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    // ==================== 分类操作 ====================

    protected void doNewCategory() {
        Activity activity = getActivity();
        if (activity == null) return;

        EditText editText = new EditText(activity);
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

    protected void doRenameCategory() {
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

        EditText editText = new EditText(activity);
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
                //分类重命名会改变其下所有卡组的 deckType，需批量重新上传以覆盖云端记录
                syncUploadDecks(listYdkFilesRecursive(newDir));
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

    protected void doDeleteCategory() {
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
            //分类删除会连带删除其下所有卡组，需先同步删除云端对应记录（必须在删除磁盘文件前构造 DeckFile）
            syncDeleteDecksFromCloud(listYdkFilesRecursive(dir));
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

    protected void doCreateDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        CategoryInfo ci = getSelectedCategoryInfo();
        EditText editText = new EditText(activity);
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
                    //新建卡组：向云端申请新的 deckId 后上传（萌卡账号已登录时）
                    syncUploadSingleDeck(deckFile, true);
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

    protected void doRenameDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            YGOUtil.show("请先选择一个卡组", Gravity.CENTER);
            return;
        }

        EditText editText = new EditText(activity);
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
                //重命名不改变 ydk 中已有的 deckId，直接覆盖上传以更新云端的卡组名
                syncUploadSingleDeck(newFile, false);
                reloadAndRefresh();
                selectCategoryAndDeck(catName, ydkFileName);
            } else {
                YGOUtil.show("重命名失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }

    protected void doDeleteDeck() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (selectedDeckPath[0].isEmpty()) {
            YGOUtil.show("请先选择一个卡组", Gravity.CENTER);
            return;
        }

        CategoryInfo ci = getSelectedCategoryInfo();
        String catName = ci != null ? ci.category.categoryName : "";
        boolean isUncategorized = ci != null && ci.sortPriority <= 2;

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(mStringManager.getSystemString(1337, "是否删除这个卡组？"));
        dialog.setMessage(isUncategorized ? selectedDeckName[0] : catName + "|" + selectedDeckName[0]);
        dialog.setMessageBackgroundColor(YGOUtil.c(R.color.colorNavy));
        dialog.setMessageGravity(Gravity.CENTER);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO);
        dialog.setPositiveButtonText(mStringManager.getSystemString(1308, "删除"));
        dialog.setNegativeButtonText(mStringManager.getSystemString(1212, "取消"));
        dialog.setPositiveButton(v -> {
            File file = new File(selectedDeckPath[0]);
            int savedCatPos = selectedCategoryPos[0];
            //先同步删除云端卡组（必须在 ydk 从磁盘删除前构造 DeckFile 以读取其中 deckId）
            syncDeleteDeckFromCloud(file);
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

    protected void doMoveToCategory() {
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
        listView.setBackgroundColor(YGOUtil.c(R.color.FallGreen));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(YGOUtil.c(R.color.deck_list_divider)));
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
                //移动不改变 ydk 中已有的 deckId，只是 deckType（所属分类）发生变化，直接覆盖上传
                syncUploadSingleDeck(dest, false);
                dialog.dismiss();
                reloadAndRefresh();
                selectCategoryAndDeck(targetCatName, ydkFileName);
            } else {
                YGOUtil.show("移动失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }

    protected void doCopyToCategory() {
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
        listView.setBackgroundColor(YGOUtil.c(R.color.FallGreen));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(YGOUtil.c(R.color.deck_list_divider)));
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
                //复制得到的卡组是全新的卡组：向云端申请新的 deckId 后上传（会覆盖副本 ydk
                //中从源卡组直接复制过来的旧 deckId）
                syncUploadSingleDeck(dest, true);
                dialog.dismiss();
                reloadAndRefresh();
                selectCategoryAndDeck(targetCatName, ydkFileName);
            } else {
                YGOUtil.show("复制失败", Gravity.CENTER);
            }
        });
        dialog.show();
    }
}

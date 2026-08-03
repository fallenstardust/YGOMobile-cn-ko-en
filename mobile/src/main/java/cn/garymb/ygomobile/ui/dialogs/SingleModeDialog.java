package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.BotUtil;
import cn.garymb.ygomobile.utils.PuzzleUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public class SingleModeDialog {

    private Context context;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

    public interface OnSingleModeListener {
        void onStartBotDuel(String botCommand, String deckFile,
                            int duelRule, boolean noCheckDeck, boolean noShuffleDeck);
        void onStartSingleMode(String luaFilePath);
    }

    private OnSingleModeListener listener;

    private String selectedDeckPath = "";
    private String selectedDeckCategory = "";
    private String selectedDeckName = "";

    public SingleModeDialog(Context context, OnSingleModeListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void show(View anchorView, List<BotUtil.BotInfo> botList, List<PuzzleUtil.PuzzleInfo> puzzleList) {
        float density = context.getResources().getDisplayMetrics().density;

        View customView = LayoutInflater.from(context).inflate(R.layout.popup_window_bot_duel, null);

        TabLayout tabLayoutMode = customView.findViewById(R.id.tab_layout_mode);
        ListView lvBotList = customView.findViewById(R.id.lv_bot_list);
        TextView tvBotDesc = customView.findViewById(R.id.tv_bot_desc);
        Button btnSelectDeck = customView.findViewById(R.id.btn_select_deck);
        Spinner spinnerRule = customView.findViewById(R.id.spinner_rule);
        CheckBox chkAiOnlyScissors = customView.findViewById(R.id.chk_ai_only_scissors);
        CheckBox chkNoCheckDeck = customView.findViewById(R.id.chk_no_check_deck);
        CheckBox chkNoShuffleDeck = customView.findViewById(R.id.chk_no_shuffle_deck);
        Button btnStartBotDuel = customView.findViewById(R.id.btn_start_bot_duel);
        Button btnExitBot = customView.findViewById(R.id.btn_exit_bot);

        String[] rules = {"大师规则（2020）", "新大师规则", "大师规则3"};
        ArrayAdapter<String> ruleAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, rules);
        ruleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRule.setAdapter(ruleAdapter);
        spinnerRule.setSelection(0);

        final int[] currentMode = {0};
        final int[] selectedPosition = {-1};

        List<String> botNames = new ArrayList<>();
        for (BotUtil.BotInfo bot : botList) {
            botNames.add(bot.toString());
        }
        List<String> puzzleNames = new ArrayList<>();
        for (PuzzleUtil.PuzzleInfo puzzle : puzzleList) {
            puzzleNames.add(puzzle.toString());
        }

        final SimpleListAdapter botAdapter = new SimpleListAdapter(context);
        botAdapter.set(botNames);
        final SimpleListAdapter puzzleAdapter = new SimpleListAdapter(context);
        puzzleAdapter.set(puzzleNames);

        tabLayoutMode.addTab(tabLayoutMode.newTab().setText("人机模式(双方无禁)"));
        tabLayoutMode.addTab(tabLayoutMode.newTab().setText("残局模式(含教学局)"));

        lvBotList.setAdapter(botAdapter);
        tvBotDesc.setText("请选择一个AI查看信息");
        btnSelectDeck.setVisibility(View.GONE);
        btnStartBotDuel.setEnabled(false);
        btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
        spinnerRule.setVisibility(View.VISIBLE);
        chkAiOnlyScissors.setVisibility(View.VISIBLE);
        chkNoCheckDeck.setVisibility(View.VISIBLE);
        chkNoShuffleDeck.setVisibility(View.VISIBLE);

        loadLastDeckInfo(btnSelectDeck);

        btnSelectDeck.setOnClickListener(v -> {
            DeckSelectorDialog deckDialog = new DeckSelectorDialog(context);
            deckDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {

                @Override
                public void onDeckSelected(String deckPath, String deckName, String categoryName) {
                    selectedDeckPath = deckPath;
                    selectedDeckName = deckName;
                    selectedDeckCategory = categoryName;
                    AppsSettings.get().setLastDeckPath(deckPath);
                    updateDeckButtonText(btnSelectDeck);
                }

                @Override
                public void onCancelled() {
                }
            });
            deckDialog.show(anchorView);
        });

        lvBotList.setOnItemClickListener((parent, view, position, id) -> {
            selectedPosition[0] = position;
            btnStartBotDuel.setEnabled(true);
            btnStartBotDuel.setTextColor(YGOUtil.c(R.color.white));
            if (currentMode[0] == 0) {
                botAdapter.setSelectedPosition(position);
                puzzleAdapter.setSelectedPosition(-1);
                if (position >= 0 && position < botList.size()) {
                    BotUtil.BotInfo bot = botList.get(position);
                    tvBotDesc.setText(bot.description != null ? bot.description : "");
                    btnSelectDeck.setVisibility(bot.supportsDeckSelection ? View.VISIBLE : View.GONE);
                }
            } else {
                puzzleAdapter.setSelectedPosition(position);
                botAdapter.setSelectedPosition(-1);
                if (position >= 0 && position < puzzleList.size()) {
                    PuzzleUtil.PuzzleInfo puzzle = puzzleList.get(position);
                    tvBotDesc.setText(puzzle.description != null ? puzzle.description : "无描述");
                }
            }
        });

        tabLayoutMode.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                currentMode[0] = position;
                selectedPosition[0] = -1;

                if (position == 0) {
                    lvBotList.setAdapter(botAdapter);
                    botAdapter.setSelectedPosition(-1);
                    puzzleAdapter.setSelectedPosition(-1);
                    tvBotDesc.setText("请选择一个AI查看信息");
                    btnSelectDeck.setVisibility(View.GONE);
                    btnStartBotDuel.setEnabled(false);
                    btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
                    spinnerRule.setVisibility(View.VISIBLE);
                    chkAiOnlyScissors.setVisibility(View.VISIBLE);
                    chkNoCheckDeck.setVisibility(View.VISIBLE);
                    chkNoShuffleDeck.setVisibility(View.VISIBLE);
                } else {
                    lvBotList.setAdapter(puzzleAdapter);
                    puzzleAdapter.setSelectedPosition(-1);
                    botAdapter.setSelectedPosition(-1);
                    tvBotDesc.setText("选择一个残局开始挑战。");
                    btnSelectDeck.setVisibility(View.GONE);
                    btnStartBotDuel.setEnabled(false);
                    btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
                    spinnerRule.setVisibility(View.GONE);
                    chkAiOnlyScissors.setVisibility(View.GONE);
                    chkNoCheckDeck.setVisibility(View.GONE);
                    chkNoShuffleDeck.setVisibility(View.GONE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                return true;
            }
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "single_mode_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight);

        btnStartBotDuel.setOnClickListener(v -> {
            if (currentMode[0] == 0) {
                if (selectedPosition[0] >= botList.size()) {
                    Toast.makeText(context, "无效的AI选择", Toast.LENGTH_SHORT).show();
                    return;
                }

                BotUtil.BotInfo selectedBot = botList.get(selectedPosition[0]);
                String botCommand = selectedBot.command;
                // 参考 menu_handler.cpp BUTTON_BOT_START：勾选后为 WindBot 追加 Hand 参数
                if (chkAiOnlyScissors.isChecked()) {
                    botCommand += " Hand=1";
                }
                // 仅支持自选卡组(SELECT_DECKFILE)的 AI 才把所选卡组作为 P2 卡组传出，
                // 其余 AI 使用其在bot.conf 中通过 Deck= 指定的内置卡组。
                String deckFile = selectedBot.supportsDeckSelection ? selectedDeckPath : "";
                // spinnerRule: 0=大师规则(2020)->5, 1=新大师规则->4, 2=大师规则3->3
                int duelRule = 5 - spinnerRule.getSelectedItemPosition();
                boolean noCheckDeck = chkNoCheckDeck.isChecked();
                boolean noShuffleDeck = chkNoShuffleDeck.isChecked();

                popupWindow.dismiss();
                if (listener != null) {
                    listener.onStartBotDuel(botCommand, deckFile, duelRule, noCheckDeck, noShuffleDeck);
                }
            } else {
                if (selectedPosition[0] >= puzzleList.size()) {
                    Toast.makeText(context, "无效的残局选择", Toast.LENGTH_SHORT).show();
                    return;
                }

                PuzzleUtil.PuzzleInfo selectedPuzzle = puzzleList.get(selectedPosition[0]);
                popupWindow.dismiss();
                if (listener != null) {
                    listener.onStartSingleMode(selectedPuzzle.filePath);
                }
            }
        });

        btnExitBot.setOnClickListener(v -> popupWindow.dismiss());

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    private void loadLastDeckInfo(Button btnSelectDeck) {
        AppsSettings settings = AppsSettings.get();
        selectedDeckCategory = settings.getLastCategory();
        selectedDeckName = settings.getLastDeckName();
        selectedDeckPath = settings.getLastDeckPath();
        updateDeckButtonText(btnSelectDeck);
    }

    private void updateDeckButtonText(Button btnSelectDeck) {
        if (selectedDeckName != null && !selectedDeckName.isEmpty()) {
            String uncatName = context.getString(R.string.category_Uncategorized);
            if (selectedDeckCategory != null && !selectedDeckCategory.isEmpty()
                    && !selectedDeckCategory.equals(uncatName)) {
                btnSelectDeck.setText(selectedDeckCategory + "/" + selectedDeckName);
            } else {
                btnSelectDeck.setText(selectedDeckName);
            }
        } else {
            btnSelectDeck.setText("选择卡组");
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(listener);
        }
    }
}

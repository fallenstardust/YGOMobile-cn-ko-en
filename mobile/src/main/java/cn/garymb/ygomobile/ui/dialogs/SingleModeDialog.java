package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.adapter.BotListAdapter;
import cn.garymb.ygomobile.adapter.PuzzleListAdapter;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.BotUtil;
import cn.garymb.ygomobile.utils.PuzzleUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public class SingleModeDialog {

    private Context context;
    private PopupWindow popupWindow;

    public interface OnSingleModeListener {
        void onStartBotDuel(String botCommand, String deckFile);
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

        View customView = LayoutInflater.from(context).inflate(R.layout.dialog_bot_duel, null);

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

        final BotListAdapter botAdapter = new BotListAdapter(context, botList);
        final PuzzleListAdapter puzzleAdapter = new PuzzleListAdapter(context, puzzleList);

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
                if (position >= 0 && position < botList.size()) {
                    BotUtil.BotInfo bot = botList.get(position);
                    tvBotDesc.setText(bot.description != null ? bot.description : "");
                    btnSelectDeck.setVisibility(bot.supportsDeckSelection ? View.VISIBLE : View.GONE);
                }
            } else {
                puzzleAdapter.setSelectedPosition(position);
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

        int popupWidth = (int) (560 * density);
        int popupHeight = (int) (320 * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);

        btnStartBotDuel.setOnClickListener(v -> {
            if (selectedPosition[0] < 0) {
                Toast.makeText(context, "请先选择一个项目", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentMode[0] == 0) {
                if (selectedPosition[0] >= botList.size()) {
                    Toast.makeText(context, "无效的AI选择", Toast.LENGTH_SHORT).show();
                    return;
                }

                BotUtil.BotInfo selectedBot = botList.get(selectedPosition[0]);
                String botCommand = selectedBot.command;
                String deckFile = selectedDeckPath;

                popupWindow.dismiss();
                if (listener != null) {
                    listener.onStartBotDuel(botCommand, deckFile);
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
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0, 0);
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
            String displayText;
            if (selectedDeckCategory != null && !selectedDeckCategory.isEmpty()) {
                displayText = selectedDeckCategory + "/" + selectedDeckName;
            } else {
                displayText = selectedDeckName;
            }
            btnSelectDeck.setText(displayText);
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

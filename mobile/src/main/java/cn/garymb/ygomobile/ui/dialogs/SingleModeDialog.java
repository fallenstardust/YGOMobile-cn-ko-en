package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.utils.BotUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.PuzzleUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

public class SingleModeDialog {

    private Context context;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    // dismiss 闩锁：内部跳转（hideForNavigation）关闭时不上报外部 dismiss 回调，
    // 避免误触发 restoreMainMenu（同 PlayerWaitingDialog/LanModeDialog 模式）
    private boolean suppressDismiss = false;
    private PopupWindow.OnDismissListener externalDismissListener;
    private final PopupWindow.OnDismissListener internalDismissListener = () -> {
        if (suppressDismiss) {
            suppressDismiss = false;
            return;
        }
        if (externalDismissListener != null) externalDismissListener.onDismiss();
    };
    private final StringManager mStringManager = DataManager.get().getStringManager();

    public interface OnSingleModeListener {
        void onStartBotDuel(String botCommand, String deckFile,
                            int duelRule, boolean noCheckDeck, boolean noShuffleDeck);

        void onStartSingleMode(String luaFilePath, boolean noShuffleToDeck);

        /** 纯单人模式(左右互搏)：参数对齐 CreateHostDialog，soloMode 由接收方编码到 mode bit4(0x10) */
        void onStartSoloHost(int lflist, int cardAllowed, int modeIdx, int duelRule,
                             int startLP, int startHand, int drawCount, int timeLimit,
                             boolean noCheckDeck, boolean noShuffleDeck);
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

        // 本弹窗含 Material TabLayout，必须用 Activity（AppCompat）原始 Context 填充：
        // DialogScale.wrap 走 createConfigurationContext 返回的是脱离 AppCompat delegate 的裸
        // ContextImpl，会丢失委派主题/ViewFactory 导致 TabLayout 构造抛 InflateException。
        // 等比缩放改由下方 popupWidth/Height（density×factor）配合布局根 match_parent+weight 实现，
        // 内容随放大后的窗口按比例填满。
        View customView = LayoutInflater.from(context).inflate(R.layout.popup_window_bot_duel, null);

        TabLayout tabLayoutMode = customView.findViewById(R.id.tab_layout_mode);
        View layoutBotPuzzle = customView.findViewById(R.id.layout_bot_puzzle);
        View layoutSolo = customView.findViewById(R.id.layout_solo);

        // 人机/残局共享控件（修改前结构：单一列表 + 右列信息设置，切 tab 靠换 adapter 与可见性 toggling）
        ListView lvBotList = customView.findViewById(R.id.lv_bot_list);
        TextView tvBotDesc = customView.findViewById(R.id.tv_bot_desc);
        TextView tvBotInfoHeader = customView.findViewById(R.id.tv_bot_info_header);
        Button btnSelectDeck = customView.findViewById(R.id.btn_select_deck);
        Spinner spinnerRule = customView.findViewById(R.id.spinner_rule);
        CheckBox chkAiOnlyScissors = customView.findViewById(R.id.chk_ai_only_scissors);
        CheckBox chkNoCheckDeck = customView.findViewById(R.id.chk_no_check_deck);
        CheckBox chkNoShuffleDeck = customView.findViewById(R.id.chk_no_shuffle_deck);
        CheckBox chkReturnToTop = customView.findViewById(R.id.chk_return_to_top);
        // 人机/残局设置项与按钮文字统一复用 gframe 既有系统字符串（对齐 game.cpp tabBot / wSingle）：
        // 1382 人机信息：(tvBotInfoHeader) / 1384 AI只出剪刀(chkBotHand) / 1229 不检查卡组 / 1230 不洗切卡组
        // / 1238 不洗切时回卡组改为回顶端 / 1211 确定(btnStartBot) / 1210 退出(btnBotCancel)
        if (tvBotInfoHeader != null) tvBotInfoHeader.setText(mStringManager.getSystemString(1382, "人机信息："));
        chkAiOnlyScissors.setText(mStringManager.getSystemString(1384, "AI只出剪刀"));
        chkNoCheckDeck.setText(mStringManager.getSystemString(1229, "不检查卡组"));
        chkNoShuffleDeck.setText(mStringManager.getSystemString(1230, "不洗切卡组"));
        // 设置残局模式的 checkbox 文本
        chkReturnToTop.setText(mStringManager.getSystemString(1238, "不洗切时回卡组改为回顶端"));
        Button btnStartBotDuel = customView.findViewById(R.id.btn_start_bot_duel);
        Button btnExitBot = customView.findViewById(R.id.btn_exit_bot);
        btnStartBotDuel.setText(mStringManager.getSystemString(1211, "确定"));
        btnExitBot.setText(mStringManager.getSystemString(1210, "退出"));

        // 纯单人模式(左右互搏)控件：布局对齐 CreateHostDialog，去掉 solo 勾选/主机名/主机密
        Spinner soloSpinnerBanlist = customView.findViewById(R.id.solo_spinner_banlist);
        Spinner soloSpinnerRule = customView.findViewById(R.id.solo_spinner_rule);
        Spinner soloSpinnerCardAllowed = customView.findViewById(R.id.solo_spinner_card_allowed);
        Spinner soloSpinnerDuelMode = customView.findViewById(R.id.solo_spinner_duel_mode);
        EditText soloEtStartLp = customView.findViewById(R.id.solo_et_start_lp);
        EditText soloEtStartHand = customView.findViewById(R.id.solo_et_start_hand);
        EditText soloEtTimeLimit = customView.findViewById(R.id.solo_et_time_limit);
        EditText soloEtDrawCount = customView.findViewById(R.id.solo_et_draw_count);
        CheckBox soloChkNoCheckDeck = customView.findViewById(R.id.solo_chk_no_check_deck);
        CheckBox soloChkNoShuffleDeck = customView.findViewById(R.id.solo_chk_no_shuffle_deck);
        Button soloBtnConfirm = customView.findViewById(R.id.solo_btn_confirm);
        Button soloBtnCancel = customView.findViewById(R.id.solo_btn_cancel);
        applySoloStrings(customView, soloBtnConfirm, soloBtnCancel);

        List<SimpleSpinnerItem> ruleItems = new ArrayList<>();
        ruleItems.add(new SimpleSpinnerItem(5, mStringManager.getSystemString(1264, "大师规则（2020）")));
        ruleItems.add(new SimpleSpinnerItem(4, mStringManager.getSystemString(1263, "新大师规则")));
        ruleItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1262, "大师规则3")));

        SimpleSpinnerAdapter ruleAdapter = new SimpleSpinnerAdapter(context);
        ruleAdapter.set(ruleItems);
        spinnerRule.setAdapter(ruleAdapter);
        spinnerRule.setSelection(0);

        // 纯单人页四个下拉复用 CreateHostDialog 的初始化（禁限卡表/规则/卡片允许/决斗模式）
        CreateHostDialog.configureSpinners(context, soloSpinnerBanlist, soloSpinnerRule,
                soloSpinnerCardAllowed, soloSpinnerDuelMode);

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

        // 半宽三页签文字易溢出：Material 1.12 的 TabView 会在测量时重套文本外观，
        // tabTextAppearance 在自定义视图上不起作用；故用自定义 TextView 作页签，并在 makeTabView 中
        // 显式应用 styles.xml 的 bot_duel_tab_layout（字号/加粗/非全大写由样式统一控制）
        tabLayoutMode.addTab(tabLayoutMode.newTab()
                .setText(mStringManager.getSystemString(1380, "人机模式"))
                .setCustomView(makeTabView(mStringManager.getSystemString(1380, "人机模式"))));
        tabLayoutMode.addTab(tabLayoutMode.newTab()
                .setText(mStringManager.getSystemString(1381, "残局模式"))
                .setCustomView(makeTabView(mStringManager.getSystemString(1381, "残局模式"))));
        tabLayoutMode.addTab(tabLayoutMode.newTab()
                .setText("纯单人模式")
                .setCustomView(makeTabView("纯单人模式")));

        lvBotList.setAdapter(botAdapter);
        tvBotDesc.setText(mStringManager.getSystemString(1382, "请选择一个AI查看信息"));
        btnSelectDeck.setVisibility(View.INVISIBLE);
        btnStartBotDuel.setEnabled(false);
        btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
        spinnerRule.setVisibility(View.VISIBLE);
        chkAiOnlyScissors.setVisibility(View.VISIBLE);
        chkNoCheckDeck.setVisibility(View.VISIBLE);
        chkNoShuffleDeck.setVisibility(View.VISIBLE);
        // 初始即人机模式（首个 tab）：残局专属"不洗切时回卡顶"复选框直接隐藏，
        // 无需等到切到残局再切回人机才触发隐藏逻辑
        chkReturnToTop.setVisibility(View.GONE);
        // 人机/残局共享区显示，纯单人区隐藏（切到第三 tab 才整页切换）
        layoutBotPuzzle.setVisibility(View.VISIBLE);
        layoutSolo.setVisibility(View.GONE);

        loadLastDeckInfo(btnSelectDeck);

        btnSelectDeck.setOnClickListener(v -> {
            DeckSelectorDialog deckDialog = new DeckSelectorDialog(context);
            deckDialog.setDisableOperationButtons(true);
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
                    btnSelectDeck.setVisibility(bot.supportsDeckSelection ? View.VISIBLE : View.INVISIBLE);
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
                // 第三页（纯单人模式）切换到独立整页布局，人机/残局共享区整体隐藏
                if (position == 2) {
                    layoutSolo.setVisibility(View.VISIBLE);
                    layoutBotPuzzle.setVisibility(View.GONE);
                    return;
                }
                layoutSolo.setVisibility(View.GONE);
                layoutBotPuzzle.setVisibility(View.VISIBLE);
                currentMode[0] = position;
                selectedPosition[0] = -1;

                if (position == 0) {
                    lvBotList.setAdapter(botAdapter);
                    botAdapter.setSelectedPosition(-1);
                    puzzleAdapter.setSelectedPosition(-1);
                    tvBotDesc.setText(mStringManager.getSystemString(1382, "请选择一个AI查看信息"));
                    // 人机页：信息标题用 1382 人机信息：
                    if (tvBotInfoHeader != null) tvBotInfoHeader.setText(mStringManager.getSystemString(1382, "人机信息："));
                    btnSelectDeck.setVisibility(View.INVISIBLE);
                    btnStartBotDuel.setEnabled(false);
                    btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
                    spinnerRule.setVisibility(View.VISIBLE);
                    chkAiOnlyScissors.setVisibility(View.VISIBLE);
                    chkNoCheckDeck.setVisibility(View.VISIBLE);
                    chkNoShuffleDeck.setVisibility(View.VISIBLE);
                    // 残局专属选项在人机模式隐藏
                    chkReturnToTop.setVisibility(View.GONE);
                } else {
                    lvBotList.setAdapter(puzzleAdapter);
                    puzzleAdapter.setSelectedPosition(-1);
                    botAdapter.setSelectedPosition(-1);
                    tvBotDesc.setText("选择一个残局开始挑战。");
                    // 残局页：信息标题改用 1352 主要信息（对齐 game.cpp tabSingle 的 GetSysString(1352)）
                    if (tvBotInfoHeader != null) tvBotInfoHeader.setText(mStringManager.getSystemString(1352, "主要信息："));
                    btnSelectDeck.setVisibility(View.INVISIBLE);
                    btnStartBotDuel.setEnabled(false);
                    btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
                    // 残局模式：规则下拉与"回卡顶"复选框同行，下拉直接隐藏（GONE）让复选框占满整行，
                    // 避免半行留白并保证较长文本完整显示
                    spinnerRule.setVisibility(View.GONE);
                    chkAiOnlyScissors.setVisibility(View.GONE);
                    chkNoCheckDeck.setVisibility(View.GONE);
                    chkNoShuffleDeck.setVisibility(View.GONE);
                    chkReturnToTop.setVisibility(View.VISIBLE);
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
        // 传设计尺寸给 setupDraggablePopup：由其按当前屏宽统一限宽，并在屏幕旋转后按新屏宽重新解算
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
        // 竖屏交换宽高比为高大于宽（与横屏宽大于高对称），旋转自动恢复（用户规格）
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight, true);
        // 用户规格：本 dialog 横竖屏均不可拖动（对齐 C++ 定位弹窗），其他 dialog 仍可拖动
        draggableHelper.setDragEnabled(popupWindow, false);

        btnStartBotDuel.setOnClickListener(v -> {
            if (currentMode[0] == 0) {
                if (selectedPosition[0] >= botList.size()) {
                    Toast.makeText(context, mStringManager.getSystemString(1421, "无效的AI选择"), Toast.LENGTH_SHORT).show();
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
                int duelRule = (int) SimpleSpinnerAdapter.getSelect(spinnerRule);
                boolean noCheckDeck = chkNoCheckDeck.isChecked();
                boolean noShuffleDeck = chkNoShuffleDeck.isChecked();

                // 人机对战建主：内部跳转关闭本弹窗并抑制 dismiss → restoreMainMenu，
                // 否则主菜单会在玩家等待界面上层误显示
                hideForNavigation();
                if (listener != null) {
                    listener.onStartBotDuel(botCommand, deckFile, duelRule, noCheckDeck, noShuffleDeck);
                }
            } else {
                if (selectedPosition[0] >= puzzleList.size()) {
                    Toast.makeText(context, mStringManager.getSystemString(1421, "无效的残局选择"), Toast.LENGTH_SHORT).show();
                    return;
                }

                PuzzleUtil.PuzzleInfo selectedPuzzle = puzzleList.get(selectedPosition[0]);
                // 残局开局属内部跳转：抑制 dismiss → restoreMainMenu，否则主菜单会
                // 叠在残局决斗界面上层再次弹出（与 bot 分支同一处理）
                hideForNavigation();
                if (listener != null) {
                    listener.onStartSingleMode(selectedPuzzle.filePath, chkReturnToTop.isChecked());
                }
            }
        });

        // 纯单人模式确定：解析面板参数后回调外部建立局域网纯单人主机（soloMode 由接收方编码到 mode）
        soloBtnConfirm.setOnClickListener(v -> {
            String banlist = SimpleSpinnerAdapter.getSelectText(soloSpinnerBanlist);
            String rule = SimpleSpinnerAdapter.getSelectText(soloSpinnerRule);
            String duelMode = SimpleSpinnerAdapter.getSelectText(soloSpinnerDuelMode);
            int lflist = CreateHostDialog.parseBanlistIndex(banlist);
            int ruleIdx = CreateHostDialog.parseRuleIndex(rule);
            int modeIdx = CreateHostDialog.parseDuelModeIndex(duelMode);
            int duelRule = ruleIdx + 1;
            // 卡片允许下拉选中索引即协议 HostInfo.rule（对齐 duelclient.cpp cscg.info.rule）
            int cardAllowed = soloSpinnerCardAllowed.getSelectedItemPosition();
            int lp = CreateHostDialog.parseIntSafe(soloEtStartLp.getText().toString(), 8000);
            int hand = CreateHostDialog.parseIntSafe(soloEtStartHand.getText().toString(), 5);
            int draw = CreateHostDialog.parseIntSafe(soloEtDrawCount.getText().toString(), 1);
            int time = CreateHostDialog.parseIntSafe(soloEtTimeLimit.getText().toString(), 0);
            boolean noCheckDeck = soloChkNoCheckDeck.isChecked();
            boolean noShuffleDeck = soloChkNoShuffleDeck.isChecked();

            // 纯单人建主属内部跳转：抑制 dismiss → restoreMainMenu
            hideForNavigation();
            if (listener != null) {
                listener.onStartSoloHost(lflist, cardAllowed, modeIdx, duelRule,
                        lp, hand, draw, time, noCheckDeck, noShuffleDeck);
            }
        });

        btnExitBot.setOnClickListener(v -> popupWindow.dismiss());
        soloBtnCancel.setOnClickListener(v -> popupWindow.dismiss());

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    /**
     * 纯单人页静态文字改走 getSystemString，编号对齐 gframe game.cpp wCreateHost（同 CreateHostDialog）：
     * 1226 禁限卡表 / 1236 规则 / 1225 卡片允许 / 1231 初始LP / 1227 决斗模式 /
     * 1232 初始手卡 / 1237 每回合时间 / 1233 每回合抽卡 / 1229 不检查卡组 / 1230 不洗切卡组 /
     * 1211 确定 / 1210 退出（此页无纯单人勾选与房间名/密码行）
     */
    private void applySoloStrings(View root, Button btnConfirm, Button btnCancel) {
        setSysText(root, R.id.solo_tv_banlist_label, 1226, "禁限卡表：");
        setSysText(root, R.id.solo_tv_rule_label, 1236, "规则：");
        setSysText(root, R.id.solo_tv_card_allowed_label, 1225, "卡片允许：");
        setSysText(root, R.id.solo_tv_start_lp_label, 1231, "初始基本分：");
        setSysText(root, R.id.solo_tv_duel_mode_label, 1227, "决斗模式：");
        setSysText(root, R.id.solo_tv_start_hand_label, 1232, "初始手卡数：");
        setSysText(root, R.id.solo_tv_time_limit_label, 1237, "每回合时间：");
        setSysText(root, R.id.solo_tv_draw_count_label, 1233, "每回合抽卡：");
        setSysText(root, R.id.solo_chk_no_check_deck, 1229, "不检查卡组");
        setSysText(root, R.id.solo_chk_no_shuffle_deck, 1230, "不洗切卡组");
        setSysTextOn(btnConfirm, 1211, "确定");
        setSysTextOn(btnCancel, 1210, "退出");
    }

    private void setSysText(View root, int viewId, int sysIdx, String def) {
        View v = root.findViewById(viewId);
        if (v instanceof TextView) setSysTextOn((TextView) v, sysIdx, def);
    }

    private void setSysTextOn(TextView tv, int sysIdx, String def) {
        if (tv != null) tv.setText(mStringManager.getSystemString(sysIdx, def));
    }

    /** 半宽三页签专用页签视图：显式应用 styles.xml 的 bot_duel_tab_layout 文字外观
     * （字号/加粗/非全大写均由该样式决定），颜色仍随选中态切换白/grayDark2 */
    private TextView makeTabView(CharSequence label) {
        TextView tv = new TextView(context);
        tv.setText(label);
        // 自定义页签视图绕开了 Material 的 tabTextAppearance 机制，故在此显式把样式应用到 TextView，
        // 使 bot_duel_tab_layout 真正生效（改样式即可统一调字号，无需再动本代码）
        tv.setTextAppearance(context, R.style.bot_duel_tab_layout);
        tv.setGravity(Gravity.CENTER);
        int[][] states = new int[][]{{android.R.attr.state_selected}, new int[]{}};
        int[] colors = new int[]{YGOUtil.c(R.color.white), YGOUtil.c(R.color.grayDark2)};
        tv.setTextColor(new ColorStateList(states, colors));
        return tv;
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
            btnSelectDeck.setText(mStringManager.getSystemString(1254, "选择卡组"));
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    /**
     * 是否可原样重显：弹窗已创建且当前未显示即视为“隐藏待复用”——本窗唯一持有者是
     * Activity 字段，彻底销毁只在 onDestroy（那里紧接置空引用），无需额外隐藏标志；
     * 关闭（退出按钮/返回键）仅 dismiss 弹窗，视图与选择状态全部保留在 contentView 上
     */
    public boolean canReshow() {
        return popupWindow != null && !popupWindow.isShowing();
    }

    /** 重新显示此前隐藏的对话框：不重新 inflate/不重读 bot、残局列表，回到关闭前选择状态 */
    public void reshow(View anchorView) {
        if (popupWindow == null || draggableHelper == null || popupWindow.isShowing()) return;
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    /** 内部跳转（人机对战建主进入玩家等待界面）：抑制外部 dismiss 回调后关闭，
     * 避免误触发 restoreMainMenu 在等待界面上层弹出主菜单 */
    public void hideForNavigation() {
        if (popupWindow == null || !popupWindow.isShowing()) return;
        suppressDismiss = true;
        popupWindow.dismiss();
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        this.externalDismissListener = listener;
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(internalDismissListener);
        }
    }

    // === 静态入口：由 YGOProActivity 调用 ===

    public static void showSingleModeDialog(YGOProActivity activity) {
        SingleModeDialog existing = activity.getSingleModeDialog();
        // 已在显示中：忽略重复点击
        if (existing != null && existing.isShowing()) return;
        activity.getMainMenuDialog().hideMainMenu();
        // 此前初始化过：原样重显，保留关闭前的 tab/列表选中/复选框/卡组选择状态
        if (existing != null && existing.canReshow()) {
            existing.reshow(activity.getDialogContainer());
            return;
        }
        File botConfFile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_BOT_CONF_PATH);
        List<BotUtil.BotInfo> botList = BotUtil.parseBotConfig(botConfFile);

        File singleDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_SINGLE_PATH);
        List<PuzzleUtil.PuzzleInfo> puzzleList = PuzzleUtil.loadPuzzleFiles(singleDir);

        SingleModeDialog dialog = new SingleModeDialog(activity, new SingleModeDialog.OnSingleModeListener() {
            @Override
            public void onStartBotDuel(String botCommand, String deckFile,
                                       int duelRule, boolean noCheckDeck, boolean noShuffleDeck) {
                GameEngine engine = activity.getEngine();
                engine.setBotMode(true);
                engine.setPlayerName(Constants.PlayerName);
                // 1. 建立局域网主机：参数对齐 duelclient.cpp 中 bot_mode 的 CTOS_CreateGame
                //    rule=5(放开卡池) mode=0(单局) lflist=0 LP=8000 起手=5 抽卡=1 无时限；
                //    duel_rule / no_check_deck / no_shuffle_deck 取自人机对战面板设置
                engine.startLocalServerWithSettings(0, 5, 0, duelRule,
                        noCheckDeck, noShuffleDeck,
                        8000, 5, 1, 0,
                        "Bot Game", "");
                // 2. 切换进入 LAN 界面的 player waiting 页面（作为主机）
                PlayerWaitingDialog.showPlayerWaitingForBotHost(activity);
                // 3. 启动 WindBot 连接本地主机并加入；deckFile 为 P2 指定卡组
                engine.launchWindBot("127.0.0.1", 7911, botCommand, deckFile);
            }

            @Override
            public void onStartSingleMode(String luaFilePath, boolean noShuffleToDeck) {
                activity.getEngine().startSingleMode(luaFilePath, noShuffleToDeck);
            }

            @Override
            public void onStartSoloHost(int lflist, int cardAllowed, int modeIdx, int duelRule,
                                        int startLP, int startHand, int drawCount, int timeLimit,
                                        boolean noCheckDeck, boolean noShuffleDeck) {
                GameEngine engine = activity.getEngine();
                engine.setPlayerName(Constants.PlayerName);
                // soloMode 编码到 mode bit4(0x10)，服务端 GameRoom.createGame 从 hostInfo.soloMode() 读取，
                // 同一连接归位 players[0]==players[1]（对齐 MainMenuNavigator.onCreateHostConfirmed 纯单人分支）
                engine.soloMode = true;
                int modeWithSolo = modeIdx | 0x10;
                engine.startLocalServerWithSettings(lflist, cardAllowed, modeWithSolo, duelRule,
                        noCheckDeck, noShuffleDeck,
                        startLP, startHand, drawCount, timeLimit,
                        "Solo Game", "");
                // 房主已掌握全部房间设置：预写入引擎缓存并置 hasJoinRoomInfoCache，
                // 使等待界面就绪即刷新房间设置显示（无需等后台引导环回）
                engine.gameLflist = lflist;
                engine.gameRule = cardAllowed;
                engine.gameMode = modeIdx;
                engine.gameNoCheckDeck = noCheckDeck ? 1 : 0;
                engine.gameNoShuffleDeck = noShuffleDeck ? 1 : 0;
                engine.gameStartLp = startLP;
                engine.gameStartHand = startHand;
                engine.gameDrawCount = drawCount;
                engine.gameTimeLimit = timeLimit;
                engine.hasJoinRoomInfoCache = true;
                // 进入玩家等待界面（房主 + solo UI 开）
                PlayerWaitingDialog.showPlayerWaitingForSoloHost(activity);
            }
        });
        dialog.show(activity.getDialogContainer(), botList, puzzleList);
        dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
        activity.setSingleModeDialog(dialog);
    }
}

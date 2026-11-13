package cn.garymb.ygomobile.ui.dialogs.lobby;
import cn.garymb.ygomobile.ui.dialogs.lobby.CreateHostDialog;
import cn.garymb.ygomobile.ui.dialogs.deck.DeckSelectorDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.PlayerWaitingDialog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
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
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.network.YGOProtocol;
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
        /** 人机建主：duelRule/noCheckDeck/noShuffleDeck 取自人机页设置，duelMode 为协议 mode（0 单局 / 1 比赛 / 2 TAG），
         *  startLP/startHand/drawCount 为开局参数（非法输入由面板回落默认值）；
         *  botCommands/deckFiles 按下标一一对应待拉起的人机席位（TAG 为 3 席，顺序即席位顺序） */
        void onStartBotDuel(List<String> botCommands, List<String> deckFiles,
                            int duelRule, boolean noCheckDeck, boolean noShuffleDeck,
                            int duelMode, int startLP, int startHand, int drawCount);

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

    /** TAG 需拉起的 AI 席位数（P2/P3/P4）与对应的列表席位标记 */
    private static final int TAG_BOT_SEATS = 3;
    private static final String[] BOT_SEAT_MARKS = {"②", "③", "④"};
    /** 决斗模式联动初始基本分：TAG 两队四人每人 8000→合计 16000，其余赛型 8000 */
    private static final String TAG_START_LP = "16000";
    private static final String NORMAL_START_LP = "8000";
    /** P2 卡组按钮多行文字的最大/最小字号（sp）：按按钮尺寸自适应缩小，避免长卡组名被截断 */
    private static final float BOT_DECK_TEXT_MAX_SP = 10f;
    private static final float BOT_DECK_TEXT_MIN_SP = 5f;
    /** 人机席位（②③④）逐席选定的卡组：同一 AI 占多席时可各选一套 */
    private final String[] botSeatDeckPaths = {"", "", ""};
    private final String[] botSeatDeckNames = {"", "", ""};
    private final String[] botSeatDeckCategories = {"", "", ""};
    /** P2 卡组按钮当前逐席显示文本（用于尺寸变化时重新适配字号） */
    private final List<String> botDeckButtonLines = new ArrayList<>();
    /** TAG 复选顺序：下标即席位序（0→队友席 P2、1/2→对手席 P3/P4），元素为 botList 下标 */
    private final List<Integer> botSelectionOrder = new ArrayList<>();
    // show() 中注入的人机页引用，供复选态与席位标记刷新使用
    private List<BotUtil.BotInfo> tagBotList;
    private List<String> tagBotBaseNames;
    private SimpleListAdapter tagBotAdapter;
    private Button tagBtnSelectDeck;
    private Button tagBtnStart;
    /** P2 卡组单元（提示文字 + 按钮）：仅在选中支持自选卡组的人机时整块显示 */
    private View tagBotDeckCell;
    /** 人机列表当前是否处于 TAG 复选态 */
    private boolean tagListMode;

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
        // 人机开局参数（仅人机页显示）：初始基本分与 P2 卡组选择同行，初始手卡数与每回合抽卡同行，
        // 决斗模式与规则选择同走行3（残局页只保留行3 的“回卡顶”复选框）
        View layoutBotStartLp = customView.findViewById(R.id.layout_bot_start_lp);
        View layoutBotHandDraw = customView.findViewById(R.id.layout_bot_hand_draw);
        View layoutBotDuelMode = customView.findViewById(R.id.layout_bot_duel_mode);
        View layoutBotRule = customView.findViewById(R.id.layout_bot_rule);
        View layoutBotP2Deck = customView.findViewById(R.id.layout_bot_p2_deck);
        Spinner spinnerBotDuelMode = customView.findViewById(R.id.bot_spinner_duel_mode);
        EditText botEtStartLp = customView.findViewById(R.id.bot_et_start_lp);
        EditText botEtStartHand = customView.findViewById(R.id.bot_et_start_hand);
        EditText botEtDrawCount = customView.findViewById(R.id.bot_et_draw_count);
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
        // 参数标签复用 gframe 建主页系统串：1231 初始基本分 / 1232 初始手卡 / 1233 每回合抽卡 /
        // 1227 决斗模式 / 1236 规则；P2 卡组无对应系统串，改走 Android 资源多语言
        setSysText(customView, R.id.bot_tv_start_lp_label, 1231, "初始基本分：");
        setSysText(customView, R.id.bot_tv_start_hand_label, 1232, "初始手卡数：");
        setSysText(customView, R.id.bot_tv_draw_count_label, 1233, "每回合抽卡：");
        setSysText(customView, R.id.bot_tv_duel_mode_label, 1227, "决斗模式：");
        setSysText(customView, R.id.bot_tv_rule_label, 1236, "规则：");
        View tvP2DeckLabel = customView.findViewById(R.id.bot_tv_p2_deck_label);
        if (tvP2DeckLabel instanceof TextView) ((TextView) tvP2DeckLabel).setText(R.string.bot_p2_deck_label);

        // 纯单人模式(左右互搏)控件：布局对齐 CreateHostDialog，去掉 solo 勾选/主机名/主机密
        Spinner soloSpinnerBanlist = customView.findViewById(R.id.solo_spinner_banlist);
        Spinner soloSpinnerRule = customView.findViewById(R.id.solo_spinner_rule);
        Spinner soloSpinnerCardAllowed = customView.findViewById(R.id.solo_spinner_card_allowed);
        Spinner soloSpinnerDuelMode = customView.findViewById(R.id.solo_spinner_duel_mode);
        EditText soloEtStartLp = customView.findViewById(R.id.solo_et_start_lp);
        EditText soloEtStartHand = customView.findViewById(R.id.solo_et_start_hand);
        EditText soloEtDrawCount = customView.findViewById(R.id.solo_et_draw_count);
        CheckBox soloChkNoCheckDeck = customView.findViewById(R.id.solo_chk_no_check_deck);
        CheckBox soloChkNoShuffleDeck = customView.findViewById(R.id.solo_chk_no_shuffle_deck);
        Button soloBtnConfirm = customView.findViewById(R.id.solo_btn_confirm);
        Button soloBtnCancel = customView.findViewById(R.id.solo_btn_cancel);
        applySoloStrings(customView, soloBtnConfirm, soloBtnCancel);

        // 行3 两个下拉统一走 SimpleSpinnerAdapter 同一配置（颜色/下拉背景/字号），保证并列时观感一致
        List<SimpleSpinnerItem> ruleItems = new ArrayList<>();
        ruleItems.add(new SimpleSpinnerItem(5, mStringManager.getSystemString(1264, "大师规则（2020）")));
        ruleItems.add(new SimpleSpinnerItem(4, mStringManager.getSystemString(1263, "新大师规则")));
        ruleItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1262, "大师规则3")));

        SimpleSpinnerAdapter ruleAdapter = newBotSpinnerAdapter();
        ruleAdapter.set(ruleItems);
        spinnerRule.setAdapter(ruleAdapter);
        spinnerRule.setSelection(0);

        // 决斗模式三项与建主页/纯单人页同口径（系统串 1244/1245/1246），选中值即协议 HostInfo.mode
        List<SimpleSpinnerItem> duelModeItems = new ArrayList<>();
        duelModeItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1244, "单局模式")));
        duelModeItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1245, "比赛模式")));
        duelModeItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1246, "TAG")));
        SimpleSpinnerAdapter duelModeAdapter = newBotSpinnerAdapter();
        duelModeAdapter.set(duelModeItems);
        spinnerBotDuelMode.setAdapter(duelModeAdapter);
        spinnerBotDuelMode.setSelection(0);

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

        // TAG 复选时选中项末尾右对齐显示 ②③④ 席位标记（选择顺序即 P2/P3/P4 顺序，
        // 不足 3 席按已选顺序循环补位）；标记列布局须在首次布局前启用
        botSelectionOrder.clear();
        tagBotList = botList;
        tagBotBaseNames = botNames;
        tagBotAdapter = botAdapter;
        tagBtnSelectDeck = btnSelectDeck;
        tagBtnStart = btnStartBotDuel;
        tagBotDeckCell = layoutBotP2Deck;
        // 卡组按钮文字随按钮宽高变化重新适配字号（首次布局、横竖屏切换）
        btnSelectDeck.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitBotDeckButtonTextSize());
        applyBotListSelectMode(false, -1);
        // 选 TAG 时初始基本分自动改为 16000（两队四人、每人 8000），选其他赛型回到 8000；
        // 同时切换人机列表的单选/复选态（TAG 需选 1~3 个 AI 填三个席位）
        spinnerBotDuelMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean tag = isTagDuelMode(spinnerBotDuelMode);
                botEtStartLp.setText(tag ? TAG_START_LP : NORMAL_START_LP);
                if (currentMode[0] == 0) {
                    // 切赛制保留已选条目：单选项转为 TAG 的 ② 席位，反之亦然
                    selectedPosition[0] = applyBotListSelectMode(tag, selectedPosition[0]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

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
                .setText("左右互博")
                .setCustomView(makeTabView("左右互博")));

        lvBotList.setAdapter(botAdapter);
        tvBotDesc.setText(mStringManager.getSystemString(1382, "请选择一个AI查看信息"));
        btnStartBotDuel.setEnabled(false);
        btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
        layoutBotRule.setVisibility(View.VISIBLE);
        layoutBotDuelMode.setVisibility(View.VISIBLE);
        chkAiOnlyScissors.setVisibility(View.VISIBLE);
        chkNoCheckDeck.setVisibility(View.VISIBLE);
        chkNoShuffleDeck.setVisibility(View.VISIBLE);
        // 开局参数只服务人机建主，残局页整块隐藏
        layoutBotStartLp.setVisibility(View.VISIBLE);
        layoutBotHandDraw.setVisibility(View.VISIBLE);
        // 初始即人机模式（首个 tab）：残局专属"不洗切时回卡顶"复选框直接隐藏，
        // 无需等到切到残局再切回人机才触发隐藏逻辑
        chkReturnToTop.setVisibility(View.GONE);
        // 人机/残局共享区显示，纯单人区隐藏（切到第三 tab 才整页切换）
        layoutBotPuzzle.setVisibility(View.VISIBLE);
        layoutSolo.setVisibility(View.GONE);

        loadLastDeckInfo();

        btnSelectDeck.setOnClickListener(v -> {
            // 需要卡组的席位（按席位顺序）：单选赛型至多 1 席；TAG 下 P2 可能补位占 2~3 席
            final List<Integer> slots = deckSeatSlots(selectedPosition[0]);
            if (slots.isEmpty()) {
                return;
            }
            DeckSelectorDialog deckDialog = new DeckSelectorDialog(context);
            deckDialog.setDisableOperationButtons(true);
            if (tagListMode) {
                // TAG（含 P2 只占 1 席的情形）：对话框逐席选择，分类/卡组条目尾部标出席位号；
                // 单局/比赛为普通单选，不加任何席位标记
                String[] marks = new String[slots.size()];
                String[] paths = new String[slots.size()];
                String[] names = new String[slots.size()];
                String[] categories = new String[slots.size()];
                for (int i = 0; i < slots.size(); i++) {
                    int slot = slots.get(i);
                    marks[i] = BOT_SEAT_MARKS[slot];
                    paths[i] = botSeatDeckPaths[slot];
                    names[i] = botSeatDeckNames[slot];
                    categories[i] = botSeatDeckCategories[slot];
                }
                deckDialog.setSeatSelection(marks, paths, names, categories);
            }
            deckDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {

                @Override
                public void onDeckSelected(String deckPath, String deckName, String categoryName) {
                    // 单选：只服务首个（也唯一的）席位
                    int slot = slots.get(0);
                    botSeatDeckPaths[slot] = deckPath;
                    botSeatDeckNames[slot] = deckName;
                    botSeatDeckCategories[slot] = categoryName;
                    selectedDeckPath = deckPath;
                    selectedDeckName = deckName;
                    selectedDeckCategory = categoryName;
                    AppsSettings.get().setLastDeckPath(deckPath);
                    updateBotDeckButton(selectedPosition[0]);
                }

                @Override
                public void onSeatDecksSelected(List<String> deckPaths, List<String> deckNames,
                                                List<String> categoryNames) {
                    // 多席位：三个列表按下标对应 slots 顺序
                    for (int i = 0; i < slots.size() && i < deckPaths.size(); i++) {
                        int slot = slots.get(i);
                        botSeatDeckPaths[slot] = deckPaths.get(i) != null ? deckPaths.get(i) : "";
                        botSeatDeckNames[slot] = i < deckNames.size() && deckNames.get(i) != null ? deckNames.get(i) : "";
                        botSeatDeckCategories[slot] = i < categoryNames.size() && categoryNames.get(i) != null ? categoryNames.get(i) : "";
                    }
                    // 首个席位的卡组作为下次打开时的预填基准
                    if (!deckPaths.isEmpty() && deckPaths.get(0) != null && !deckPaths.get(0).isEmpty()) {
                        selectedDeckPath = deckPaths.get(0);
                        selectedDeckName = deckNames.isEmpty() || deckNames.get(0) == null ? "" : deckNames.get(0);
                        selectedDeckCategory = categoryNames.isEmpty() || categoryNames.get(0) == null ? "" : categoryNames.get(0);
                        AppsSettings.get().setLastDeckPath(selectedDeckPath);
                    }
                    updateBotDeckButton(selectedPosition[0]);
                }

                @Override
                public void onCancelled() {
                }
            });
            deckDialog.show(anchorView);
        });

        lvBotList.setOnItemClickListener((parent, view, position, id) -> {
            if (currentMode[0] == 0) {
                if (tagListMode) {
                    // TAG 复选：点已选项即取消；已选满 3 个时先提示取消方式，不接受新选择
                    if (botSelectionOrder.contains(position)) {
                        botSelectionOrder.remove((Integer) position);
                        botAdapter.setItemSelected(position, false);
                    } else if (botSelectionOrder.size() >= TAG_BOT_SEATS) {
                        Toast.makeText(context, R.string.bot_tag_max_select, Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        botSelectionOrder.add(position);
                        botAdapter.setItemSelected(position, true);
                    }
                    // 单选高亮与复选背景互斥：TAG 下只走复选背景，席位号取首个已选项供描述区显示
                    botAdapter.setSelectedPosition(-1);
                    puzzleAdapter.setSelectedPosition(-1);
                    selectedPosition[0] = botSelectionOrder.isEmpty() ? -1 : botSelectionOrder.get(0);
                } else {
                    selectedPosition[0] = position;
                    botAdapter.setSelectedPosition(position);
                    puzzleAdapter.setSelectedPosition(-1);
                }
                if (selectedPosition[0] >= 0 && selectedPosition[0] < botList.size()) {
                    BotUtil.BotInfo bot = botList.get(selectedPosition[0]);
                    tvBotDesc.setText(bot.description != null ? bot.description : "");
                }
                // 选择结构变化（单选换人/复选增减）后按当前席位重新基线填充逐席卡组
                seedBotSeatDecks(selectedPosition[0]);
                refreshBotListDisplay(selectedPosition[0]);
            } else {
                selectedPosition[0] = position;
                btnStartBotDuel.setEnabled(true);
                btnStartBotDuel.setTextColor(YGOUtil.c(R.color.white));
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
                    btnStartBotDuel.setEnabled(false);
                    btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
                    layoutBotRule.setVisibility(View.VISIBLE);
                    layoutBotDuelMode.setVisibility(View.VISIBLE);
                    chkAiOnlyScissors.setVisibility(View.VISIBLE);
                    chkNoCheckDeck.setVisibility(View.VISIBLE);
                    chkNoShuffleDeck.setVisibility(View.VISIBLE);
                    // 人机页显示开局参数（行3 的决斗模式随本行一同显示）
                    layoutBotStartLp.setVisibility(View.VISIBLE);
                    layoutBotHandDraw.setVisibility(View.VISIBLE);
                    // 切回人机页按当前赛制重设列表选择方式（保留当前单选态，无选中时即清空）
                    selectedPosition[0] = applyBotListSelectMode(isTagDuelMode(spinnerBotDuelMode), selectedPosition[0]);
                    // 残局专属选项在人机模式隐藏
                    chkReturnToTop.setVisibility(View.GONE);
                } else {
                    lvBotList.setAdapter(puzzleAdapter);
                    puzzleAdapter.setSelectedPosition(-1);
                    botAdapter.setSelectedPosition(-1);
                    tvBotDesc.setText("选择一个残局开始挑战。");
                    // 残局页：信息标题改用 1352 主要信息（对齐 game.cpp tabSingle 的 GetSysString(1352)）
                    if (tvBotInfoHeader != null) tvBotInfoHeader.setText(mStringManager.getSystemString(1352, "主要信息："));
                    // 残局页：P2 卡组单元与开局参数一并隐藏
                    btnStartBotDuel.setEnabled(false);
                    btnStartBotDuel.setTextColor(YGOUtil.c(R.color.grayDark2));
                    // 残局模式：行3 的决斗模式与规则整块隐藏（GONE）让“回卡顶”复选框占满整行，
                    // 避免半行留白并保证较长文本完整显示
                    layoutBotRule.setVisibility(View.GONE);
                    layoutBotDuelMode.setVisibility(View.GONE);
                    chkAiOnlyScissors.setVisibility(View.GONE);
                    chkNoCheckDeck.setVisibility(View.GONE);
                    chkNoShuffleDeck.setVisibility(View.GONE);
                    // 残局不经局域网建房，开局参数（基本分/卡组、手卡/抽卡）均隐藏
                    layoutBotStartLp.setVisibility(View.GONE);
                    layoutBotHandDraw.setVisibility(View.GONE);
                    layoutBotP2Deck.setVisibility(View.GONE);
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
        // 纯单人页含初始LP/起手/抽卡三个可编辑 EditText：PopupWindow 必须 focusable=true
        // 才能拿到输入焦点并唤起输入法（对齐同带输入框的 AnnounceCardDialog/EditTextDialog；
        // focusable=false 时窗口不接收按键，点 EditText 无法弹出输入法、无法修改文本）
        popupWindow.setFocusable(true);
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
                if (!tagListMode && selectedPosition[0] >= botList.size()) {
                    // TAG 走复选集判定（下方按已选列表循环补位），此处只管单选赛型的未选择拦截
                    Toast.makeText(context, mStringManager.getSystemString(1421, "无效的AI选择"), Toast.LENGTH_SHORT).show();
                    return;
                }

                int duelRule = (int) SimpleSpinnerAdapter.getSelect(spinnerRule);
                boolean noCheckDeck = chkNoCheckDeck.isChecked();
                boolean noShuffleDeck = chkNoShuffleDeck.isChecked();
                // 开局参数：决斗模式选中值即协议 mode；三项数值非法（空/非数字）时回到默认 8000/5/1
                int duelMode = (int) SimpleSpinnerAdapter.getSelect(spinnerBotDuelMode);
                int startLP = CreateHostDialog.parseIntSafe(botEtStartLp.getText().toString(), 8000);
                int startHand = CreateHostDialog.parseIntSafe(botEtStartHand.getText().toString(), 5);
                int drawCount = CreateHostDialog.parseIntSafe(botEtDrawCount.getText().toString(), 1);

                // 待拉起的人机席位命令与卡组：单局/比赛 1 席，TAG 3 席（复选顺序即席位顺序，
                // 不足 3 个时按已选顺序循环补位，与列表 ②③④ 标记同口径）
                boolean aiOnlyScissors = chkAiOnlyScissors.isChecked();
                List<String> botCommands = new ArrayList<>();
                List<String> deckFiles = new ArrayList<>();
                if (duelMode == YGOProtocol.MODE_TAG) {
                    if (botSelectionOrder.isEmpty()) {
                        Toast.makeText(context, mStringManager.getSystemString(1421, "无效的AI选择"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int selected = botSelectionOrder.size();
                    for (int k = 0; k < TAG_BOT_SEATS; k++) {
                        appendBotSeat(botList.get(botSelectionOrder.get(k % selected)), aiOnlyScissors, k,
                                botCommands, deckFiles);
                    }
                } else {
                    if (selectedPosition[0] >= botList.size()) {
                        Toast.makeText(context, mStringManager.getSystemString(1421, "无效的AI选择"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    appendBotSeat(botList.get(selectedPosition[0]), aiOnlyScissors, 0, botCommands, deckFiles);
                }

                // 人机对战建主：内部跳转关闭本弹窗并抑制 dismiss → restoreMainMenu，
                // 否则主菜单会在玩家等待界面上层误显示
                hideForNavigation();
                if (listener != null) {
                    listener.onStartBotDuel(botCommands, deckFiles, duelRule, noCheckDeck, noShuffleDeck,
                            duelMode, startLP, startHand, drawCount);
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
            // 纯单人模式不提供回合时间选择，固定为无限时间（timeLimit=0，对齐 GameField "0=无限时"）
            int time = 0;
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

    /** 行3 两个下拉（决斗模式/规则）的统一适配器配置：白字 + 列表背景下拉 + 同一字号 */
    private SimpleSpinnerAdapter newBotSpinnerAdapter() {
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(context);
        adapter.setColor(Color.WHITE);
        adapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        adapter.setTextSize(10f);
        return adapter;
    }

    /** 决斗模式是否为 TAG：决定人机列表进入复选态与初始基本分默认值 */
    private static boolean isTagDuelMode(Spinner spinnerDuelMode) {
        return (int) SimpleSpinnerAdapter.getSelect(spinnerDuelMode) == YGOProtocol.MODE_TAG;
    }

    /** 参考 menu_handler.cpp BUTTON_BOT_START：勾选"AI只出剪刀"时为 WindBot 追加 Hand=1 参数 */
    private static String botCommandOf(BotUtil.BotInfo bot, boolean aiOnlyScissors) {
        String command = bot.command != null ? bot.command : "";
        return aiOnlyScissors ? command + " Hand=1" : command;
    }

    /** 追加一个待拉起的人机席位：仅支持自选卡组(SELECT_DECKFILE)的 AI 带走该席位所选卡组，其余用 bot.conf 内置卡组 */
    private void appendBotSeat(BotUtil.BotInfo bot, boolean aiOnlyScissors, int seatSlot,
                               List<String> botCommands, List<String> deckFiles) {
        botCommands.add(botCommandOf(bot, aiOnlyScissors));
        boolean takeDeck = bot.supportsDeckSelection && seatSlot >= 0 && seatSlot < botSeatDeckPaths.length;
        deckFiles.add(takeDeck ? botSeatDeckPaths[seatSlot] : "");
    }

    /**
     * 切换人机列表选择方式（单选 ⇄ TAG 复选），切换时**保留已选条目**：
     * 进入 TAG 时把当前单选项作为复选首位（即 ② 席位）；离开 TAG 时把原 ② 席位（复选序首位）
     * 保留为单选，且单选卡组自动改为 ② 席位已选的卡组。
     *
     * @param selectedPosition 当前单选位置（TAG 复选下传首个已选项或 -1）
     * @return 切换后应使用的单选位置
     */
    private int applyBotListSelectMode(boolean tagMode, int selectedPosition) {
        tagListMode = tagMode;
        int keep;
        if (tagMode) {
            // 单选 → 复选：已选项占据 ② 席位，其余人机席位按循环补位规则自动填
            keep = selectedPosition;
            botSelectionOrder.clear();
            if (keep >= 0) {
                botSelectionOrder.add(keep);
            }
        } else {
            // 复选 → 单选：保留 ② 席位，单选卡组同步改为 ② 席位已选的卡组（未选则为空）
            keep = botSelectionOrder.isEmpty() ? -1 : botSelectionOrder.get(0);
            botSelectionOrder.clear();
            selectedDeckPath = botSeatDeckPaths[0];
            selectedDeckName = botSeatDeckNames[0];
            selectedDeckCategory = botSeatDeckCategories[0];
        }
        // 清一次复选集合再按需开启，避免上一轮复选高亮残留
        tagBotAdapter.setMultiSelectMode(false);
        tagBotAdapter.clearMultiSelection();
        tagBotAdapter.setMultiSelectMode(tagMode);
        tagBotAdapter.setSelectedPosition(tagMode ? -1 : keep);
        if (tagMode) {
            for (int idx : botSelectionOrder) {
                tagBotAdapter.setItemSelected(idx, true);
            }
        }
        seedBotSeatDecks(keep);
        refreshBotListDisplay(keep);
        return keep;
    }

    /**
     * 当前选择下需要卡组的席位槽（0=②、1=③、2=④）：单选赛型只有选中的人机支持自选卡组时
     * 才需一席；TAG 下 P2 被复选并因补位占多席时，它要落的每个席位各需一份卡组。
     */
    private List<Integer> deckSeatSlots(int selectedPosition) {
        List<Integer> slots = new ArrayList<>();
        if (tagBotList == null) return slots;
        if (!tagListMode) {
            if (selectedPosition >= 0 && selectedPosition < tagBotList.size()
                    && tagBotList.get(selectedPosition).supportsDeckSelection) {
                slots.add(0);
            }
            return slots;
        }
        int n = botSelectionOrder.size();
        if (n == 0) return slots;
        for (int k = 0; k < TAG_BOT_SEATS; k++) {
            int idx = botSelectionOrder.get(k % n);
            if (idx >= 0 && idx < tagBotList.size() && tagBotList.get(idx).supportsDeckSelection) {
                slots.add(k);
            }
        }
        return slots;
    }

    /**
     * 按当前选择重填逐席卡组：需要卡组的席位用上一次选定/最后使用的卡组作基线，其余席位清空。
     * 仅在「选择结构发生变化」时调用（选人机条目、切赛制/切页签），避免冲掉玩家刚逐席选好的卡组。
     */
    private void seedBotSeatDecks(int selectedPosition) {
        List<Integer> slots = deckSeatSlots(selectedPosition);
        for (int k = 0; k < TAG_BOT_SEATS; k++) {
            boolean need = slots.contains(k);
            botSeatDeckPaths[k] = need ? selectedDeckPath : "";
            botSeatDeckNames[k] = need ? selectedDeckName : "";
            botSeatDeckCategories[k] = need ? selectedDeckCategory : "";
        }
    }

    /**
     * 刷新 P2 卡组单元：没选中支持自选卡组的人机时整块（提示文字 + 按钮）隐藏；
     * 否则逐席位一行显示（仅 TAG 下带 ②/③/④ 席位号，单局/比赛不加标记），并自适应字号。
     */
    private void updateBotDeckButton(int selectedPosition) {
        if (tagBotDeckCell == null || tagBtnSelectDeck == null) return;
        List<Integer> slots = deckSeatSlots(selectedPosition);
        tagBotDeckCell.setVisibility(slots.isEmpty() ? View.GONE : View.VISIBLE);
        botDeckButtonLines.clear();
        String uncatName = context.getString(R.string.category_Uncategorized);
        for (int slot : slots) {
            String name = botSeatDeckNames[slot];
            String text;
            if (name == null || name.isEmpty()) {
                text = mStringManager.getSystemString(1254, "选择卡组");
            } else {
                String category = botSeatDeckCategories[slot];
                text = (category != null && !category.isEmpty() && !category.equals(uncatName))
                        ? category + "/" + name : name;
            }
            botDeckButtonLines.add((tagListMode ? BOT_SEAT_MARKS[slot] + " " : "") + text);
        }
        tagBtnSelectDeck.setText(TextUtils.join("\n", botDeckButtonLines));
        tagBtnSelectDeck.setSingleLine(false);
        fitBotDeckButtonTextSize();
    }

    /**
     * 按按钮实际宽高把逐席位文本缩到能完整显示（不再截断）：行宽与总行高两方向取更严的那个缩放，
     * 字号限制在 [BOT_DECK_TEXT_MIN_SP, BOT_DECK_TEXT_MAX_SP]；布局未完成时先按最大字号，等布局回调再适配。
     */
    private void fitBotDeckButtonTextSize() {
        if (tagBtnSelectDeck == null) return;
        int availW = tagBtnSelectDeck.getWidth()
                - tagBtnSelectDeck.getPaddingLeft() - tagBtnSelectDeck.getPaddingRight();
        int availH = tagBtnSelectDeck.getHeight()
                - tagBtnSelectDeck.getPaddingTop() - tagBtnSelectDeck.getPaddingBottom();
        int lines = botDeckButtonLines.size();
        if (availW <= 0 || availH <= 0 || lines == 0) {
            tagBtnSelectDeck.setTextSize(TypedValue.COMPLEX_UNIT_SP, BOT_DECK_TEXT_MAX_SP);
            return;
        }
        TextPaint paint = tagBtnSelectDeck.getPaint();
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                BOT_DECK_TEXT_MAX_SP, context.getResources().getDisplayMetrics()));
        float maxLineWidth = 0f;
        for (String line : botDeckButtonLines) {
            maxLineWidth = Math.max(maxLineWidth, paint.measureText(line));
        }
        float lineHeight = paint.getFontMetrics().bottom - paint.getFontMetrics().top;
        float byWidth = maxLineWidth <= 0f ? 1f : availW / maxLineWidth;
        float byHeight = availH / (lineHeight * lines);
        float scale = Math.min(1f, Math.min(byWidth, byHeight));
        float sizeSp = Math.max(BOT_DECK_TEXT_MIN_SP, BOT_DECK_TEXT_MAX_SP * scale);
        tagBtnSelectDeck.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    /**
     * 刷新人机列表与按钮态：TAG 下选中项末尾右对齐显示 ②③④ 席位标记（不足 3 席按已选顺序
     * 循环补位，故只选 1 个时它同时带 ②③④）；P2 卡组单元仅当选中支持自选卡组的人机时
     * 整块（提示文字 + 按钮）显示；确定按钮按“TAG 已选 1~3 个 / 其余赛型已单选 1 个”点亮。
     */
    private void refreshBotListDisplay(int selectedPosition) {
        int selected = botSelectionOrder.size();
        int count = tagBotBaseNames.size();
        // 条目文本固定为原始名称，席位标记走尾部右对齐列（单选赛型下全部为空串即不显示）
        List<String> marks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            marks.add(tagListMode ? botSeatMarks(i, selected) : "");
        }
        tagBotAdapter.set(tagBotBaseNames);
        tagBotAdapter.setTrailingMarks(marks);

        // P2 卡组单元：与所选人机是否支持自选卡组联动，并逐席位刷新按钮文字
        updateBotDeckButton(selectedPosition);

        boolean canStart = tagListMode ? selected > 0 : selectedPosition >= 0;
        tagBtnStart.setEnabled(canStart);
        tagBtnStart.setTextColor(YGOUtil.c(canStart ? R.color.white : R.color.grayDark2));
    }

    /** 该 AI 因复选顺序将占据的人机席位号标记（P2→②、P3→③、P4→④），未被选中返回空串 */
    private String botSeatMarks(int listIndex, int selectionSize) {
        if (selectionSize <= 0 || !botSelectionOrder.contains(listIndex)) return "";
        StringBuilder marks = new StringBuilder();
        for (int k = 0; k < TAG_BOT_SEATS; k++) {
            if (botSelectionOrder.get(k % selectionSize) == listIndex) {
                marks.append(BOT_SEAT_MARKS[k]);
            }
        }
        return marks.toString();
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

    private void loadLastDeckInfo() {
        AppsSettings settings = AppsSettings.get();
        selectedDeckCategory = settings.getLastCategory();
        selectedDeckName = settings.getLastDeckName();
        selectedDeckPath = settings.getLastDeckPath();
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
            public void onStartBotDuel(List<String> botCommands, List<String> deckFiles,
                                       int duelRule, boolean noCheckDeck, boolean noShuffleDeck,
                                       int duelMode, int startLP, int startHand, int drawCount) {
                GameEngine engine = activity.getEngine();
                engine.setBotMode(true);
                engine.setPlayerName(Constants.PlayerName);
                // 1. 建立局域网主机：参数对齐 duelclient.cpp 中 bot_mode 的 CTOS_CreateGame，
                //    lflist=0（无禁限）/ rule=5(放开卡池) / 时限固定 0（人机页不提供回合时间）；
                //    duel_rule / no_check_deck / no_shuffle_deck / mode / LP / 起手 / 抽卡 均取自人机页面板
                engine.startLocalServerWithSettings(0, 5, duelMode, duelRule,
                        noCheckDeck, noShuffleDeck,
                        startLP, startHand, drawCount, 0,
                        "Bot Game", "");
                // 房主建主时已掌握全部房间设置：预写入引擎缓存并置 hasJoinRoomInfoCache，
                // 使等待界面就绪即刷新房间信息，并据 duelMode 打开 TAG 的 4 席位 UI
                engine.gameLflist = 0;
                engine.gameRule = 5;
                engine.gameMode = duelMode;
                engine.field.dInfo.duelRule = duelRule;
                engine.gameNoCheckDeck = noCheckDeck ? 1 : 0;
                engine.gameNoShuffleDeck = noShuffleDeck ? 1 : 0;
                engine.gameStartLp = startLP;
                engine.gameStartHand = startHand;
                engine.gameDrawCount = drawCount;
                engine.gameTimeLimit = 0;
                engine.hasJoinRoomInfoCache = true;
                // 2. 切换进入 LAN 界面的 player waiting 页面（作为主机）
                PlayerWaitingDialog.showPlayerWaitingForBotHost(activity);
                // 3. 启动 WindBot 连接本地主机并依次入座；两个列表按下标一一对应，
                //    单局/比赛 1 个、TAG 3 个（队友席 + 对手两席）
                engine.launchWindBot("127.0.0.1", 7911, botCommands, deckFiles);
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
                // 标记本次纯单人建主来自 SingleModeDialog 纯单人页（区别于 LanMode→CreateHostDialog 勾选纯单人）：
                // 退出等待时依此路由 → MainMenuDialog；LAN 建主入口会主动置回 false 保证不串道
                activity.soloLaunchedFromSingleMode = true;
                // 进入玩家等待界面（房主 + solo UI 开）
                PlayerWaitingDialog.showPlayerWaitingForSoloHost(activity);
            }
        });
        dialog.show(activity.getDialogContainer(), botList, puzzleList);
        dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
        activity.setSingleModeDialog(dialog);
    }
}

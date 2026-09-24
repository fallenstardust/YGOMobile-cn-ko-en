package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;

/**
 * 局域网建主设置界面（layout_create_host_settings）：禁限卡表/规则/卡片允许/决斗模式等
 * 房间参数配置，点击“确定”回调 onCreateHostConfirmed 由外部创建本地服务端并进入玩家等待界面。
 * 全部界面文字与 Spinner 选项均通过 StringManager.getSystemString 获取
 * （对齐 gframe game.cpp wCreateHost 各控件的 GetSysString 编号），XML 内文本仅作兜底。
 */
public class CreateHostDialog {
    public static final StringManager mStringManager = DataManager.get().getStringManager();

    private final Context context;
    private PopupWindow popupWindow;
    private boolean hiddenForReuse = false;
    private boolean suppressDismiss = false;
    private PopupWindow.OnDismissListener externalDismissListener;
    private DraggablePopupHelper draggableHelper;

    /** 由局域网主界面传入的昵称（原 et_nickname），建主时作为玩家名回传 */
    private String nickname = "";

    private Spinner spinnerBanlist;
    private Spinner spinnerRule;
    private Spinner spinnerCardAllowed;
    private Spinner spinnerDuelMode;
    private EditText etStartLP;
    private EditText etStartHand;
    private EditText etTimeLimit;
    private EditText etDrawCount;
    private CheckBox chkNoCheckDeck;
    private CheckBox chkNoShuffleDeck;
    private EditText etHostName;
    private EditText etHostPassword;

    public interface OnCreateHostListener {
        /**
         * cardAllowed 即协议 HostInfo.rule（卡片允许，对齐 game.cpp cbRule 选中索引 0..5），
         * duelRule 为协议 HostInfo.duel_rule（game.cpp cbDuelRule 选中索引 +1，1..5）
         */
        void onCreateHostConfirmed(int lflist, int cardAllowed, int modeIdx, int duelRule,
                                   int startLP, int startHand, int drawCount, int timeLimit,
                                   boolean noCheckDeck, boolean noShuffleDeck,
                                   String hostName, String password, String nickname);

        /** 点击“取消”：交由外部返回局域网主界面 */
        void onCancelCreate();
    }

    private final OnCreateHostListener listener;

    private final PopupWindow.OnDismissListener internalDismissListener = () -> {
        if (suppressDismiss) {
            suppressDismiss = false;
            return;
        }
        if (externalDismissListener != null) externalDismissListener.onDismiss();
    };

    public CreateHostDialog(Context context, OnCreateHostListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname != null ? nickname : "";
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        this.externalDismissListener = listener;
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(internalDismissListener);
        }
    }

    public void show(View anchorView) {
        View customView = LayoutInflater.from(context).inflate(R.layout.popup_window_create_host, null);

        spinnerBanlist = customView.findViewById(R.id.spinner_banlist);
        spinnerRule = customView.findViewById(R.id.spinner_rule);
        spinnerCardAllowed = customView.findViewById(R.id.spinner_card_allowed);
        etStartLP = customView.findViewById(R.id.et_start_lp);
        spinnerDuelMode = customView.findViewById(R.id.spinner_duel_mode);
        etStartHand = customView.findViewById(R.id.et_start_hand);
        etTimeLimit = customView.findViewById(R.id.et_time_limit);
        etDrawCount = customView.findViewById(R.id.et_draw_count);
        chkNoCheckDeck = customView.findViewById(R.id.chk_no_check_deck);
        chkNoShuffleDeck = customView.findViewById(R.id.chk_no_shuffle_deck);
        etHostName = customView.findViewById(R.id.et_host_name);
        etHostPassword = customView.findViewById(R.id.et_host_password);
        Button btnConfirmCreate = customView.findViewById(R.id.btn_confirm_create);
        Button btnCancelCreate = customView.findViewById(R.id.btn_cancel_create);

        applySystemStrings(customView, btnConfirmCreate, btnCancelCreate);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(internalDismissListener);

        customView.setFocusableInTouchMode(true);
        customView.requestFocus();

        draggableHelper = new DraggablePopupHelper(context, "create_host_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight);

        setupSpinners(spinnerBanlist, spinnerRule, spinnerCardAllowed, spinnerDuelMode);

        btnConfirmCreate.setOnClickListener(v -> {
            String banlist = SimpleSpinnerAdapter.getSelectText(spinnerBanlist);
            String rule = SimpleSpinnerAdapter.getSelectText(spinnerRule);
            String duelMode = SimpleSpinnerAdapter.getSelectText(spinnerDuelMode);
            String startLPStr = etStartLP.getText().toString();
            String startHandStr = etStartHand.getText().toString();
            String timeLimitStr = etTimeLimit.getText().toString();
            String drawCountStr = etDrawCount.getText().toString();
            boolean noCheckDeck = chkNoCheckDeck.isChecked();
            boolean noShuffleDeck = chkNoShuffleDeck.isChecked();
            String hostName = etHostName.getText().toString();
            String password = etHostPassword.getText().toString();
            String nick = nickname != null ? nickname : "";

            int lflist = parseBanlistIndex(banlist);
            int ruleIdx = parseRuleIndex(rule);
            int modeIdx = parseDuelModeIndex(duelMode);
            int duelRule = ruleIdx + 1;
            // 卡片允许下拉选中项即协议 rule 字段（对齐 duelclient.cpp cscg.info.rule = cbRule->getSelected()）
            int cardAllowed = spinnerCardAllowed.getSelectedItemPosition();
            int lp = parseIntSafe(startLPStr, 8000);
            int hand = parseIntSafe(startHandStr, 5);
            int draw = parseIntSafe(drawCountStr, 1);
            int time = parseIntSafe(timeLimitStr, 0);

            if (listener != null) {
                listener.onCreateHostConfirmed(lflist, cardAllowed, modeIdx, duelRule,
                        lp, hand, draw, time,
                        noCheckDeck, noShuffleDeck, hostName, password, nick);
            }

            String localIp = LanDiscoveryManager.getLocalIpAddress();
            if (localIp != null) {
                Toast.makeText(context,
                        mStringManager.getSystemString(1701, "房间已创建，本机IP: %ls，等待玩家加入")
                                .replace("%ls", localIp + ":7911"),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, mStringManager.getSystemString(1702, "房间已创建，等待玩家加入"), Toast.LENGTH_SHORT).show();
            }
        });

        btnCancelCreate.setOnClickListener(v -> {
            if (listener != null) listener.onCancelCreate();
        });

        draggableHelper.showPopup(popupWindow, anchorView);
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    public void hideForNavigation() {
        if (popupWindow == null) return;
        hiddenForReuse = true;
        if (popupWindow.isShowing()) {
            // 弹窗退场动画会延迟触发 onDismiss，suppressDismiss 标志改由回调内消费复位
            suppressDismiss = true;
            popupWindow.dismiss();
        }
    }

    public boolean canReshow() {
        return hiddenForReuse && popupWindow != null;
    }

    public void reshow(View anchorView) {
        if (popupWindow == null || draggableHelper == null) return;
        if (popupWindow.isShowing()) return;
        hiddenForReuse = false;
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    public void dismiss() {
        hiddenForReuse = false;
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /**
     * 建主界面全部静态文字改走 getSystemString，
     * 编号对齐 gframe game.cpp wCreateHost：
     * 1226 禁限卡表 / 1236 规则 / 1225 卡片允许 / 1231 初始LP / 1227 决斗模式 /
     * 1232 初始手卡 / 1237 每回合时间 / 1233 每回合抽卡 / 1229 不检查卡组 /
     * 1230 不洗切卡组 / 1234 房间名 / 1235 房间密码 / 1211 确定 / 1212 取消
     */
    private void applySystemStrings(View root, Button btnConfirm, Button btnCancel) {
        setSysText(root, R.id.tv_banlist_label, 1226, "禁限卡表：");
        setSysText(root, R.id.tv_rule_label, 1236, "规则：");
        setSysText(root, R.id.tv_card_allowed_label, 1225, "卡片允许：");
        setSysText(root, R.id.tv_start_lp_label, 1231, "初始基本分：");
        setSysText(root, R.id.tv_duel_mode_label, 1227, "决斗模式：");
        setSysText(root, R.id.tv_start_hand_label, 1232, "初始手卡数：");
        setSysText(root, R.id.tv_time_limit_label, 1237, "每回合时间：");
        setSysText(root, R.id.tv_draw_count_label, 1233, "每回合抽卡：");
        setSysText(root, R.id.chk_no_check_deck, 1229, "不检查卡组");
        setSysText(root, R.id.chk_no_shuffle_deck, 1230, "不洗切卡组");
        setSysText(root, R.id.tv_room_name_label, 1234, "房间名称：");
        setSysText(root, R.id.tv_room_pwd_label, 1235, "房间密码：");
        setSysTextOn(btnConfirm, 1211, "确认");
        setSysTextOn(btnCancel, 1212, "取消");
    }

    private void setSysText(View root, int viewId, int sysIdx, String def) {
        View v = root.findViewById(viewId);
        if (v instanceof TextView) setSysTextOn((TextView) v, sysIdx, def);
    }

    private void setSysTextOn(TextView tv, int sysIdx, String def) {
        if (tv != null) tv.setText(mStringManager.getSystemString(sysIdx, def));
    }

    private void setupSpinners(Spinner spinnerBanlist, Spinner spinnerRule,
                               Spinner spinnerCardAllowed, Spinner spinnerDuelMode) {
        LimitManager limitManager = DataManager.get().getLimitManager();

        List<SimpleSpinnerItem> banlistItems = new ArrayList<>();
        banlistItems.add(new SimpleSpinnerItem(0, "N/A"));

        boolean isGenesysMode = AppsSettings.get().getGenesysMode() == 1;
        List<String> limitNames = isGenesysMode ?
                limitManager.getGenesysLimitNames() : limitManager.getLimitNames();

        for (String limitName : limitNames) {
            if ("N/A".equals(limitName)) continue;
            banlistItems.add(new SimpleSpinnerItem(banlistItems.size(), limitName));
        }

        SimpleSpinnerAdapter banlistAdapter = new SimpleSpinnerAdapter(context);
        banlistAdapter.setColor(Color.WHITE);
        banlistAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        banlistAdapter.set(banlistItems);
        spinnerBanlist.setAdapter(banlistAdapter);

        String lastLimit = isGenesysMode ?
                AppsSettings.get().getLastGenesysLimit() : AppsSettings.get().getLastLimit();

        int selectedIndex = 0;
        for (int i = 0; i < banlistItems.size(); i++) {
            if (banlistItems.get(i).text.equals(lastLimit)) {
                selectedIndex = i;
                break;
            }
        }
        spinnerBanlist.setSelection(selectedIndex);

        List<SimpleSpinnerItem> ruleItems = new ArrayList<>();
        ruleItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1260, "大师规则")));
        ruleItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1261, "大师规则2")));
        ruleItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1262, "大师规则３")));
        ruleItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1263, "新大师规则（2017）")));
        ruleItems.add(new SimpleSpinnerItem(4, mStringManager.getSystemString(1264, "大师规则（2020）")));

        SimpleSpinnerAdapter ruleAdapter = new SimpleSpinnerAdapter(context);
        ruleAdapter.setColor(Color.WHITE);
        ruleAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        ruleAdapter.set(ruleItems);
        spinnerRule.setAdapter(ruleAdapter);
        // 默认大师规则（2020）= 索引4（协议 duel_rule=5），对齐 game.cpp cbDuelRule->setSelected(default_rule-1)
        spinnerRule.setSelection(4);

        // 卡片允许：对齐 game.cpp cbRule 六项（1481-1486），选中索引即协议 HostInfo.rule
        List<SimpleSpinnerItem> cardAllowedItems = new ArrayList<>();
        cardAllowedItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1481, "ＯＣＧ")));
        cardAllowedItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1482, "ＴＣＧ")));
        cardAllowedItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1483, "简体中文")));
        cardAllowedItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1484, "自定义卡片")));
        cardAllowedItems.add(new SimpleSpinnerItem(4, mStringManager.getSystemString(1485, "无独有卡")));
        cardAllowedItems.add(new SimpleSpinnerItem(5, mStringManager.getSystemString(1486, "所有卡片")));

        SimpleSpinnerAdapter cardAllowedAdapter = new SimpleSpinnerAdapter(context);
        cardAllowedAdapter.setColor(Color.WHITE);
        cardAllowedAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        cardAllowedAdapter.set(cardAllowedItems);
        spinnerCardAllowed.setAdapter(cardAllowedAdapter);
        // 默认选中 ＯＣＧ（协议 rule=0，AVAIL_OCG），对齐 game.cpp defaultOT=1 时 cbRule->setSelected(0)
        spinnerCardAllowed.setSelection(0);

        List<SimpleSpinnerItem> duelModeItems = new ArrayList<>();
        duelModeItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1244, "单局模式")));
        duelModeItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1245, "比赛模式")));
        duelModeItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1246, "TAG")));

        SimpleSpinnerAdapter duelModeAdapter = new SimpleSpinnerAdapter(context);
        duelModeAdapter.setColor(Color.WHITE);
        duelModeAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        duelModeAdapter.set(duelModeItems);
        spinnerDuelMode.setAdapter(duelModeAdapter);
        spinnerDuelMode.setSelection(0);
    }

    public static int parseBanlistIndex(String banlist) {
        if (banlist == null || banlist.equals("N/A")) return 0;
        LimitManager limitManager = DataManager.get().getLimitManager();
        boolean isGenesysMode = AppsSettings.get().getGenesysMode() == 1;
        return isGenesysMode
                ? limitManager.getGenesysLimitHash(banlist)
                : limitManager.getLimitHash(banlist);
    }

    public static int parseRuleIndex(String rule) {
        if (rule == null) return 4;
        if (rule.equals(mStringManager.getSystemString(1260, "大师规则"))) return 0;
        if (rule.equals(mStringManager.getSystemString(1261, "大师规则2"))) return 1;
        if (rule.equals(mStringManager.getSystemString(1262, "大师规则３"))) return 2;
        if (rule.equals(mStringManager.getSystemString(1263, "新大师规则（2017）"))) return 3;
        if (rule.equals(mStringManager.getSystemString(1264, "大师规则（2020）"))) return 4;
        return 4;
    }

    public static int parseDuelModeIndex(String duelMode) {
        if (duelMode == null) return 0;
        if (duelMode.equals(mStringManager.getSystemString(1244, "单局模式"))) return 0;
        if (duelMode.equals(mStringManager.getSystemString(1245, "比赛模式"))) return 1;
        if (duelMode.equalsIgnoreCase(mStringManager.getSystemString(1246, "TAG"))) return 2;
        return 0;
    }

    public static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
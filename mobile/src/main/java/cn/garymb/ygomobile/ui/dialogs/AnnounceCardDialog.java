package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.CardSet;

/**
 * 宣言卡片对话框（移植 gframe wANCard，game.cpp L916-927）：
 * 输入框变更即刷新可宣言列表（event_handler.cpp EDITBOX_ANCARD → UpdateDeclarableList，
 * client_field.cpp L1500-1541），列表按 declare_opcodes 经 is_declarable 过滤
 * （client_field.cpp L1358-1499）；点击列表项展示卡信息（LISTBOX_ANCARD → ShowCardInfo）；
 * 确定发送卡号（BUTTON_ANCARD_OK → SetResponseI(ancard[sel])，event_handler.cpp L486-493）。
 */
public class AnnounceCardDialog {

    private static final int DIALOG_WIDTH_DP = 300;
    /** common.h：TYPE_TOKEN 0x4000（Java 侧 CardType.Token 同值） */
    private static final int TYPE_TOKEN = 0x4000;

    // common.h L387-399 宣言过滤操作码
    private static final int OPCODE_ADD = 0x40000000;
    private static final int OPCODE_SUB = 0x40000001;
    private static final int OPCODE_MUL = 0x40000002;
    private static final int OPCODE_DIV = 0x40000003;
    private static final int OPCODE_AND = 0x40000004;
    private static final int OPCODE_OR = 0x40000005;
    private static final int OPCODE_NEG = 0x40000006;
    private static final int OPCODE_NOT = 0x40000007;
    private static final int OPCODE_ISCODE = 0x40000100;
    private static final int OPCODE_ISSETCARD = 0x40000101;
    private static final int OPCODE_ISTYPE = 0x40000102;
    private static final int OPCODE_ISRACE = 0x40000103;
    private static final int OPCODE_ISATTRIBUTE = 0x40000104;

    /** card_data.h L17-23：双名卡/规则卡 second_code 映射（只判断键存在性） */
    private static final SparseIntArray SECOND_CODE = new SparseIntArray();

    static {
        SECOND_CODE.put(78734254, 17955766);  // CARD_MARINE_DOLPHIN
        SECOND_CODE.put(13857930, 17732278);  // CARD_TWINKLE_MOSS
        SECOND_CODE.put(1784686, 10000050);   // CARD_TIMAEUS
        SECOND_CODE.put(11082056, 10000060);  // CARD_CRITIAS
        SECOND_CODE.put(46232525, 10000070);  // CARD_HERMOS
    }

    public interface OnCardDeclaredListener {
        void onCardDeclared(int code);
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例） */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

    private String title = "宣言卡片";
    private List<Integer> opcodes = new ArrayList<>();
    private final List<Card> results = new ArrayList<>();
    private int selectedIndex = -1;

    private TextView tvTitle;
    private EditText etInput;
    private ListView lvCards;
    private Button btnOk;
    private final NameAdapter adapter = new NameAdapter();

    private OnCardDeclaredListener listener;
    private OnDismissListener dismissListener;

    public AnnounceCardDialog(Context context) {
        this.context = context;
    }

    public AnnounceCardDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    /** MSG_ANNOUNCE_CARD 下发的 declare_opcodes（duelclient.cpp L4037-4039） */
    public AnnounceCardDialog setOpcodes(List<Integer> opcodeList) {
        this.opcodes = opcodeList != null ? opcodeList : new ArrayList<>();
        return this;
    }

    public AnnounceCardDialog setOnCardDeclaredListener(OnCardDeclaredListener l) {
        this.listener = l;
        return this;
    }

    public AnnounceCardDialog setOnDismissListener(OnDismissListener l) {
        this.dismissListener = l;
        return this;
    }

    private void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_announce_card, null);
        tvTitle = root.findViewById(R.id.tv_ancard_title);
        etInput = root.findViewById(R.id.et_ancard_input);
        lvCards = root.findViewById(R.id.lv_ancard);
        btnOk = root.findViewById(R.id.btn_ancard_ok);

        tvTitle.setText(title);
        btnOk.setText(mStringManager.getSystemString(1211, "确定"));
        btnOk.setEnabled(false);

        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateList(s == null ? "" : s.toString());
            }
        });

        lvCards.setAdapter(adapter);
        lvCards.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= results.size()) return;
            selectedIndex = position;
            adapter.notifyDataSetChanged();
            btnOk.setEnabled(true);
            showCardInfo(results.get(position));
        });

        btnOk.setOnClickListener(v -> {
            if (selectedIndex < 0 || selectedIndex >= results.size()) return;
            playButtonSound();
            int code = results.get(selectedIndex).Code;
            dismiss();
            if (listener != null) listener.onCardDeclared(code);
        });

        popupWindow = new PopupWindow(root, dp(DIALOG_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setOnDismissListener(() -> {
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "announce_card");
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dp(DIALOG_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 移植 ClientField::UpdateDeclarableList（client_field.cpp L1500-1541）：
     * 输入可解析为卡号且可宣言 → 单项结果；否则按 卡名包含 或 系列名匹配（GetSetCodes）
     * 遍历全卡库过滤，完全匹配/上次使用的卡置顶。空输入列出全部可宣言卡（CardNameContains 空串恒真）。
     */
    private void updateList(String input) {
        results.clear();
        selectedIndex = -1;
        btnOk.setEnabled(false);

        CardManager cardManager = DataManager.get().getCardManager();
        String pname = input == null ? "" : input.trim();
        int tryCode = parseVal(pname);
        if (tryCode > 0) {
            Card cd = cardManager.getAllCards().get(tryCode);
            if (cd != null && isDeclarable(cd)) {
                results.add(cd);
                adapter.notifyDataSetChanged();
                return;
            }
        }

        String lower = pname.toLowerCase(Locale.US);
        List<Long> setCodes = matchingSetCodes(lower);
        android.util.SparseArray<Card> all = cardManager.getAllCards();
        for (int i = 0; i < all.size(); i++) {
            Card cd = all.valueAt(i);
            if (cd.Alias != 0) continue;
            boolean nameHit = lower.isEmpty() || cd.containsName(lower);
            boolean setHit = false;
            if (!nameHit && !setCodes.isEmpty()) {
                for (long sc : setCodes) {
                    if (cardHasSetCode(cd, sc)) {
                        setHit = true;
                        break;
                    }
                }
            }
            if ((nameHit || setHit) && isDeclarable(cd)) {
                if ((cd.Name != null && cd.Name.equals(pname)) || cd.Code == tryCode) {
                    results.add(0, cd);
                } else {
                    results.add(cd);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    /** 对齐 BufferIO::GetVal：十进制或 0x 前缀十六进制转卡号，失败返回 0 */
    private static int parseVal(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return (int) Long.parseLong(s.substring(2), 16);
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 对齐 DataManager::GetSetCodes：系列名包含输入文本的所有系列代码 */
    private List<Long> matchingSetCodes(String lower) {
        List<Long> codes = new ArrayList<>();
        if (lower.isEmpty()) return codes;
        for (CardSet set : mStringManager.getCardSets()) {
            String name = set.getName();
            if (name != null && name.toLowerCase(Locale.US).contains(lower)) {
                codes.add(set.getCode());
            }
        }
        return codes;
    }

    /**
     * 移植 is_declarable（client_field.cpp L1358-1499）：操作码栈求值，
     * 结果必须为单个非零值；TOKEN 卡（Java 侧无 rule_code 字段，仅判 TOKEN）
     * 须出现在 second_code 映射中才可宣言。
     */
    private boolean isDeclarable(Card cd) {
        if (cd.Alias != 0) return false;
        Deque<Integer> stack = new ArrayDeque<>();
        for (int op : opcodes) {
            switch (op) {
                case OPCODE_ADD: {
                    if (stack.size() >= 2) {
                        int rhs = stack.pop();
                        int lhs = stack.pop();
                        stack.push(lhs + rhs);
                    }
                    break;
                }
                case OPCODE_SUB: {
                    if (stack.size() >= 2) {
                        int rhs = stack.pop();
                        int lhs = stack.pop();
                        stack.push(lhs - rhs);
                    }
                    break;
                }
                case OPCODE_MUL: {
                    if (stack.size() >= 2) {
                        int rhs = stack.pop();
                        int lhs = stack.pop();
                        stack.push(lhs * rhs);
                    }
                    break;
                }
                case OPCODE_DIV: {
                    if (stack.size() >= 2) {
                        int rhs = stack.pop();
                        int lhs = stack.pop();
                        stack.push(rhs != 0 ? lhs / rhs : 0);
                    }
                    break;
                }
                case OPCODE_AND: {
                    if (stack.size() >= 2) {
                        int rhs = stack.pop();
                        int lhs = stack.pop();
                        stack.push(lhs != 0 && rhs != 0 ? 1 : 0);
                    }
                    break;
                }
                case OPCODE_OR: {
                    if (stack.size() >= 2) {
                        int rhs = stack.pop();
                        int lhs = stack.pop();
                        stack.push(lhs != 0 || rhs != 0 ? 1 : 0);
                    }
                    break;
                }
                case OPCODE_NEG: {
                    if (stack.size() >= 1) {
                        stack.push(-stack.pop());
                    }
                    break;
                }
                case OPCODE_NOT: {
                    if (stack.size() >= 1) {
                        stack.push(stack.pop() == 0 ? 1 : 0);
                    }
                    break;
                }
                case OPCODE_ISCODE: {
                    if (stack.size() >= 1) {
                        int code = stack.pop();
                        stack.push(cd.Code == code ? 1 : 0);
                    }
                    break;
                }
                case OPCODE_ISSETCARD: {
                    if (stack.size() >= 1) {
                        long setCode = stack.pop() & 0xffffffffL;
                        stack.push(cardHasSetCode(cd, setCode) ? 1 : 0);
                    }
                    break;
                }
                case OPCODE_ISTYPE: {
                    if (stack.size() >= 1) {
                        int val = stack.pop();
                        stack.push((int) (cd.Type & val));
                    }
                    break;
                }
                case OPCODE_ISRACE: {
                    if (stack.size() >= 1) {
                        int race = stack.pop();
                        stack.push((int) (cd.Race & race));
                    }
                    break;
                }
                case OPCODE_ISATTRIBUTE: {
                    if (stack.size() >= 1) {
                        int attribute = stack.pop();
                        stack.push((int) (cd.Attribute & attribute));
                    }
                    break;
                }
                default: {
                    stack.push(op);
                    break;
                }
            }
        }
        Integer top = stack.peek();
        if (stack.size() != 1 || top == null || top == 0) return false;
        if (SECOND_CODE.indexOfKey(cd.Code) < 0 && (cd.Type & TYPE_TOKEN) != 0) {
            return false;
        }
        return true;
    }

    /** 对齐 card_data.h check_setcode：跳过为 0 的系列槽位 */
    private static boolean cardHasSetCode(Card card, long value) {
        long settype = value & 0x0fffL;
        long setsubtype = value & 0xf000L;
        for (long x : card.getSetCode()) {
            if (x == 0) continue;
            if ((x & 0x0fffL) == settype && (x & setsubtype) == setsubtype) {
                return true;
            }
        }
        return false;
    }

    /** 对齐 LISTBOX_ANCARD（event_handler.cpp L1022-1028）：选中项展示卡信息 */
    private void showCardInfo(Card card) {
        if (context instanceof YGOProActivity) {
            ((YGOProActivity) context).getCardDetailPanel().showCard(card);
        }
    }

    private void playButtonSound() {
        if (context instanceof YGOProActivity) {
            SoundManager sm = ((YGOProActivity) context).getSoundManager();
            if (sm != null) sm.playSoundEffect(SoundManager.SFX.BUTTON);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private class NameAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public Card getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView == null) {
                tv = new TextView(context);
                tv.setTextSize(12);
                tv.setSingleLine(true);
                tv.setPadding(dp(8), dp(4), dp(8), dp(4));
            } else {
                tv = (TextView) convertView;
            }
            tv.setText(results.get(position).Name);
            if (position == selectedIndex) {
                tv.setBackgroundResource(R.color.ygopro_line_color);
                tv.setTextColor(ContextCompat.getColor(context, R.color.colorNavy));
            } else {
                tv.setBackgroundResource(android.R.color.transparent);
                tv.setTextColor(Color.WHITE);
            }
            return tv;
        }
    }

    public void show() {
        show(null);
    }

    public void show(View anchorView) {
        build();
        if (popupWindow == null) return;
        Runnable showAction = () -> {
            if (popupWindow == null || popupWindow.isShowing()) return;
            View anchor = anchorView;
            if (anchor == null && context instanceof android.app.Activity) {
                android.app.Activity act = (android.app.Activity) context;
                if (!act.isFinishing() && !act.isDestroyed()) {
                    anchor = act.getWindow().getDecorView();
                }
            }
            if (anchor == null || anchor.getWindowToken() == null) return;
            // 对齐 duelclient.cpp：弹窗前先执行一次 UpdateDeclarableList（空输入列出全部可宣言卡）
            updateList(etInput.getText().toString());
            try {
                if (draggableHelper != null) {
                    draggableHelper.showPopup(popupWindow, anchor);
                } else {
                    popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
                }
            } catch (Exception e) {
                // Token expired or window already showing
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAction.run();
        } else {
            handler.post(showAction);
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            try {
                popupWindow.dismiss();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }
}
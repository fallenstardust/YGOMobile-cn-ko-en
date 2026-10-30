package cn.garymb.ygomobile.ui.dialogs.duel;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.SparseArray;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.EffectActivatableHints;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;


/**
 * Yes/no query dialog, base layer of the split inheritance chain
 * (YesOrNoDialogBase <- YesOrNoDialog). Owns all instance state fields,
 * the OnDismissListener/CardChainItem nested types, the card-name
 * colorizing and location helpers, in-place button-swap refresh,
 * dismiss/isShowing accessors, the static showing-dialog registry with
 * chain-query state, and the private static protocol text bridges.
 */
abstract class YesOrNoDialogBase {

    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_YES_NO = 1;

    // 卡片名字着色（对齐 YGO 卡框配色：怪兽=黄、魔法=淡绿、陷阱=淡粉）
    protected static final int CARD_NAME_COLOR_MONSTER = YGOUtil.c(R.color.classic_gold); // 怪兽卡：黄色
    protected static final int CARD_NAME_COLOR_SPELL = YGOUtil.c(R.color.card_name_spell_green);   // 魔法卡：淡绿色
    protected static final int CARD_NAME_COLOR_TRAP = YGOUtil.c(R.color.card_name_trap_pink);    // 陷阱卡：淡粉色

    protected final Context context;
    protected PopupWindow popupWindow;
    protected final Handler handler = new Handler(Looper.getMainLooper());

    protected String title = "";
    protected CharSequence message = "";
    protected int type = TYPE_MESSAGE;

    protected String positiveText = "确定";
    protected String negativeText = "取消";

    protected View.OnClickListener positiveListener;
    protected View.OnClickListener negativeListener;
    protected OnDismissListener dismissListener;
    
    protected boolean cancelable = true;
    protected View contentView;
    protected View customContentView;
    protected int softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
    protected DraggablePopupHelper draggableHelper;
    protected int messageBgColor = 0;
    protected int messageGravity = -1;
    // 标题颜色（0 = 沿用布局默认）：纯单人模式下按被询问方着色
    protected int titleColor = 0;
    // 指定弹窗居中区域（如 layout_game_right）；null = 按整个窗口居中（原行为）
    protected View centerInView;
    // 用于显示卡片的卡片代码
    protected int cardCode = 0;
    // 链式询问的卡片列表
    protected List<CardChainItem> chainCards = null;
    // 是否启用左右互换「是/否」按钮位置（用户规格）
    protected boolean swapButtonPositions = false;

    // MSG_SELECT_EFFECTYN 被询问卡（askedEffectValid 时有效，用户规格：诱发效果被询问时
    // 卡角呼吸绿点 + 点卡/点询问窗卡图出对应序号段落发光）：归属半场与区域坐标快照，
    // 供 resolveActivatableClientCard 在 activatableCards/chainCards 都查不到时按坐标取卡
    private boolean askedEffectValid;
    private int askedCode;
    private int askedSide;
    private int askedLocation;
    private int askedSequence;
    /** 置入 field.effectYnAsking 的场卡引用：dismiss 时仅当仍为本窗所置才摘除（防新询问已接力） */
    private ClientCard askedCard;

    public interface OnDismissListener {
        void onDismiss();
    }

    /** 将 message 中出现的卡名按卡片类型着色，返回可显示的 CharSequence */
    protected CharSequence colorizeCardName(String message, int code, String cardName) {
        if (message == null || message.isEmpty() || cardName == null || cardName.isEmpty()) {
            return message;
        }
        int color = cardNameColor(code);
        if (color == 0) return message;
        SpannableString span = new SpannableString(message);
        int from = 0;
        int idx;
        while ((idx = message.indexOf(cardName, from)) >= 0) {
            span.setSpan(new ForegroundColorSpan(color), idx, idx + cardName.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = idx + cardName.length();
        }
        return span;
    }

    /**
     * 依卡片类型返回卡名颜色：魔法=淡绿、陷阱=淡粉、怪兽=黄；无法识别返回 0（不着色）。
     * 陷阱怪兽同时带 Trap|Monster 位，优先按陷阱着色，对齐卡框配色。
     */
    protected int cardNameColor(int code) {
        if (code <= 0) return 0;
        Card card = DataManager.get().getCardManager().getCard(code);
        if (card == null) return 0;
        if (card.isType(CardType.Spell)) return CARD_NAME_COLOR_SPELL;
        if (card.isType(CardType.Trap)) return CARD_NAME_COLOR_TRAP;
        if (card.isType(CardType.Monster)) return CARD_NAME_COLOR_MONSTER;
        return 0;
    }

    /** 卡名→着色缓存：命中/未命中都缓存（未命中存 0），避免每次弹窗重复全表扫描 */
    protected static final Map<String, Integer> sCardNameColorCache = new HashMap<>();

    /** 卡名→卡码缓存：命中/未命中都缓存（未命中存 0），供攻击宣言等无 currentChain.code 场景回退取卡图 */
    protected static final Map<String, Integer> sCardNameCodeCache = new HashMap<>();

    /** 依卡名查卡码：全表匹配 c.Name（与 cardColorByName 同一匹配口径）；找不到返回 0 并缓存 */
    protected static int cardCodeByName(String name) {
        Integer cached = sCardNameCodeCache.get(name);
        if (cached != null) return cached;
        int code = 0;
        SparseArray<Card> all = DataManager.get().getCardManager().getAllCards();
        for (int i = 0; i < all.size(); i++) {
            Card c = all.valueAt(i);
            if (c == null || !name.equals(c.Name)) continue;
            code = c.Code;
            break;
        }
        sCardNameCodeCache.put(name, code);
        return code;
    }

    /**
     * 通用着色：扫描文本中每一处 "[...]"，若方括号内的文字恰好是某张卡的名字，
     * 则按卡片类型给该卡名着色（只染名称本身，不含方括号）。
     * 位置名/计数器名等匹配不到卡片的名字不着色，天然被排除。用于覆盖所有 YesOrNoDialog
     * 显示的含 [%ls] 卡名的文本，而非仅个别已知卡名的场景。
     */
    protected static CharSequence colorizeBracketedCardNames(CharSequence text) {
        if (text == null) return null;
        String s = text.toString();
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int i = 0;
        final int n = s.length();
        while (i < n) {
            if (s.charAt(i) == '[') {
                int j = s.indexOf(']', i + 1);
                if (j > i + 1) { // 至少含 1 个字符的非空卡名
                    String name = s.substring(i + 1, j);
                    int color = cardColorByName(name);
                    if (color != 0) {
                        sb.setSpan(new ForegroundColorSpan(color), i + 1, j,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    i = j + 1;
                    continue;
                }
            }
            i++;
        }
        return sb;
    }

    /** 依卡名查类型着色：魔法=淡绿、陷阱=淡粉、怪兽=黄；非卡或未知返回 0（不着色） */
    protected static int cardColorByName(String name) {
        Integer cached = sCardNameColorCache.get(name);
        if (cached != null) return cached;
        int color = 0;
        SparseArray<Card> all = DataManager.get().getCardManager().getAllCards();
        for (int i = 0; i < all.size(); i++) {
            Card c = all.valueAt(i);
            if (c == null || !name.equals(c.Name)) continue;
            if (c.isType(CardType.Spell)) color = CARD_NAME_COLOR_SPELL;
            else if (c.isType(CardType.Trap)) color = CARD_NAME_COLOR_TRAP;
            else if (c.isType(CardType.Monster)) color = CARD_NAME_COLOR_MONSTER;
            break;
        }
        sCardNameColorCache.put(name, color);
        return color;
    }

    /** 检查 cardCode 对应的卡名是否出现在 message 中 */
    protected boolean cardNameInMessage(int code) {
        if (code <= 0 || message == null) return false;
        Card card = DataManager.get().getCardManager().getCard(code);
        if (card == null || card.Name == null || card.Name.isEmpty()) return false;
        return message.toString().contains(card.Name);
    }

    /** 询问窗左侧卡图堆栈的卡数上限（用户规格：message 出现多个卡名时全部出卡图）：卡图宽
     *  30dp、相邻重叠 1/3，4 张合计 90dp；再多会把消息文本挤到不足两行宽 */
    protected static final int MAX_MENTIONED_CARD_IMAGES = 4;
    /** 相邻卡图的叠压比例：后一张压住前一张宽度的 1/3（用户规格） */
    protected static final float CARD_STACK_OVERLAP_RATIO = 1f / 3f;
    /** 堆栈卡图基准宽（dp）与宽高比：布局解析失败时的兜底换算基准，与 dialog_yes_or_no 一致 */
    protected static final float STACK_CARD_WIDTH_DP = 30f;
    protected static final float STACK_CARD_HEIGHT_RATIO = 43f / 30f;

    /**
     * message 中出现的全部卡名对应卡码，按出现顺序去重、上限
     * {@link #MAX_MENTIONED_CARD_IMAGES}：
     * <ol>
     * <li>协议下发的本询问主卡（{@code cardCode>0} 且其卡名确实出现在文本里）排在最前；</li>
     * <li>其余取文本里每个 {@code "[XXX]"} 括号内容按卡名解析到的卡码。</li>
     * </ol>
     * 连锁/发动询问的文本常同时含被连锁卡与发动卡等多个卡名（event_string 与询问语拼接、
     * 或一条效果文提及多卡），旧实现只解析首个括号，故左侧永远只出第一个卡名的那张图
     * （用户反馈），现按本列表逐张出图。攻击宣言/直接攻击等时点没有 MSG_CHAINING
     * （{@code cardCode=0}）时，同样按括号卡名回退解析，与旧 firstBracketedCardCode 同语义
     * 但覆盖文本里的全部括号而非仅首个。
     */
    protected List<Integer> mentionedCardCodes() {
        List<Integer> codes = new ArrayList<>();
        if (cardCode > 0 && cardNameInMessage(cardCode)) codes.add(cardCode);
        String text = message != null ? message.toString() : null;
        if (text == null) return codes;
        int i = text.indexOf('[');
        while (i >= 0 && codes.size() < MAX_MENTIONED_CARD_IMAGES) {
            int j = text.indexOf(']', i + 1);
            if (j <= i + 1) {
                i = text.indexOf('[', i + 1);
                continue;
            }
            // 位置名/计数器名等匹配不到卡名的括号返回 0，天然被排除（与卡名着色同一口径）
            int code = cardCodeByName(text.substring(i + 1, j));
            if (code > 0 && !containsMaskedCode(codes, code)) codes.add(code);
            i = text.indexOf('[', j + 1);
        }
        return codes;
    }

    /** 卡码是否已在列表中（按去掉暗码位后的原码比对，避免同一张卡重复出一张图） */
    protected static boolean containsMaskedCode(List<Integer> codes, int code) {
        int masked = code & 0x7fffffff;
        for (int i = 0, n = codes.size(); i < n; i++) {
            if ((codes.get(i) & 0x7fffffff) == masked) return true;
        }
        return false;
    }

    /**
     * 构建消息行左侧卡图堆栈（用户规格：多个卡名时全部显示卡图，横向排列、每张压住前一张
     * 的 1/3，再显示 message 文本）：
     * <ul>
     * <li>首张沿用布局常驻的 {@code iv_yes_no_card_image}（协议主卡，或按旧口径从文本首个
     * 卡名回退解析出的那张），尺寸/背景/点击行为与旧单卡图完全一致；</li>
     * <li>其余按同一尺寸动态追加，{@code marginStart = -卡宽/3} 实现叠压；LinearLayout 按
     * 子视图序绘制，后追加者天然盖在前一张之上；</li>
     * <li>无任何卡名时整个容器隐藏（旧实现只隐单张图，容器留着仍占一列空白）。</li>
     * </ul>
     * 同一 root 反复显示时只摘除动态追加项，常驻首张留在原位复用。
     *
     * @param stack 卡图堆栈容器（{@code ll_yes_no_card_stack}）
     * @return 是否显示了卡图
     */
    protected boolean buildMentionedCardStack(LinearLayout stack) {
        if (stack == null) return false;
        List<Integer> codes = mentionedCardCodes();
        View first = stack.getChildCount() > 0 ? stack.getChildAt(0) : null;
        if (!(first instanceof ImageView)) {
            // 布局常驻首张缺失或被换成非 ImageView（理论不应发生）：整堆不显示，
            // 避免主卡卡图无处安放而只列出退而追加的那几张
            stack.setVisibility(View.GONE);
            return false;
        }
        if (codes.isEmpty()) {
            stack.setVisibility(View.GONE);
            bindCardDetailClick(first, 0);   // 清掉可能残留的上一次卡码，隐藏态不可点
            return false;
        }
        stack.setVisibility(View.VISIBLE);
        while (stack.getChildCount() > 1) stack.removeViewAt(stack.getChildCount() - 1);
        LinearLayout.LayoutParams base = (LinearLayout.LayoutParams) first.getLayoutParams();
        int cardW = base != null && base.width > 0
                ? base.width : DialogScale.dpToPx(context, STACK_CARD_WIDTH_DP);
        int cardH = base != null && base.height > 0
                ? base.height : Math.round(cardW * STACK_CARD_HEIGHT_RATIO);
        int overlap = Math.round(cardW * CARD_STACK_OVERLAP_RATIO);
        int pad = Math.round(context.getResources().getDisplayMetrics().density);
        ImageLoader loader = new ImageLoader();
        bindStackCardImage(loader, (ImageView) first, codes.get(0));
        for (int i = 1, n = codes.size(); i < n; i++) {
            ImageView iv = new ImageView(DialogScale.wrap(context));
            iv.setPadding(pad, pad, pad, pad);
            iv.setBackgroundResource(R.drawable.ygopro_deck_background);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.leftMargin = -overlap;   // 横向压住前一张的 1/3
            iv.setLayoutParams(lp);
            bindStackCardImage(loader, iv, codes.get(i));
            stack.addView(iv);
        }
        return true;
    }

    /** 堆栈中单张卡图：按卡码绑图并接上「点击进卡片详情」（与旧单卡图行为一致） */
    private void bindStackCardImage(ImageLoader loader, ImageView iv, int code) {
        iv.setVisibility(View.VISIBLE);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loader.bindImage(iv, code, ImageLoader.Type.small);
        bindCardDetailClick(iv, code);
    }

    /**
     * 为卡片图像绑定点击：点击后在卡片详情面板显示该卡详情（供 iv_yes_no_card_image
     * 与连锁卡片项复用）。仅当宿主为 YGOProActivity 且卡码有效时可点；否则置不可点。
     * <p>经 ClientCard 展示态路径显示（用户规格：询问窗卡图/可发动卡列表项点击的卡详
     * 也要带可发动效果发光）：连锁项自带 (controler, location, sequence)，按场上实体取卡；
     * 取不到（overlay/卡组遮蔽等）时按卡码合成临时卡（同 CardSelectController.showCardInfoFromItem
     * 口径）；仍无则退回纯卡表展示（不发光，与关键词查卡同语义）。
     */
    protected void bindCardDetailClick(View view, final int code) {
        if (view == null) return;
        if (code <= 0 || !(context instanceof YGOProActivity)) {
            view.setClickable(false);
            view.setOnClickListener(null);
            return;
        }
        view.setClickable(true);
        view.setOnClickListener(v -> {
            CardDetailPanel panel = ((YGOProActivity) context).getCardDetailPanel();
            if (panel == null) return;
            ClientCard cc = resolveActivatableClientCard(code);
            if (cc != null) {
                panel.showCard(cc);
                return;
            }
            Card card = DataManager.get().getCardManager().getCard(code & 0x7fffffff);
            if (card != null) {
                panel.showCard(card);
            }
        });
    }

    /** EFFECT_YN 询问窗展示后调用（用户规格）：①把被询问效果的段落文档序（desc 低 4 位）
     *  登记进 {@link EffectActivatableHints}，使点场上该卡/点询问窗卡图展示的卡详能发光；
     *  ②场卡存在时置 field.effectYnAsking，CardOverlayRenderer 据此在卡矩形角位强制绘
     *  呼吸绿点（不写 cmdFlag，避免点卡误弹命令菜单打断应答）；③留存归属坐标供卡图点击
     *  取卡/合成临时卡。protocolControler 为报文 c 字段的协议 seat，经 localPlayer 折本地半场 */
    void setAskedEffectCard(int protocolControler, int code, int location, int sequence, int desc) {
        if (!(context instanceof YGOProActivity)) return;
        GameEngine engine = ((YGOProActivity) context).getEngine();
        if (engine == null || code <= 0) return;
        int side = engine.localPlayer(protocolControler & 1);
        askedEffectValid = true;
        askedCode = code;
        askedSide = side;
        askedLocation = location & 0x7f;
        askedSequence = sequence;
        EffectActivatableHints.register(code & 0x7fffffff, side, desc);
        GameField field = engine.getField();
        if (field != null) {
            askedCard = field.getCard(side, askedLocation, sequence);
            if (askedCard != null) field.effectYnAsking = askedCard;
        }
        // 用户新规格3：询问登记即刷新展示中的卡详，正点着这张卡时不经重新点击就能看到
        // 对应序号段的背景呼吸高亮（新规格2：询问卡/连锁卡带点即可点出同款呼吸）
        CardDetailPanel.refreshCardDetailMarks();
    }

    /** 询问窗 dismiss 统一清理：摸掉本窗置入的角位绿点引用（归属判定：新询问窗弹窗前
     *  先关旧窗再置新卡，若已被新窗接力则不得误摘）；登记簿不在此清，随下一条
     *  命令/连锁询问的 clearCommandFlags 统一过期，保证答「是」进入连锁挑选时段落仍发光 */
    void clearAskedEffectCard() {
        if (!askedEffectValid) return;
        askedEffectValid = false;
        if (!(context instanceof YGOProActivity)) return;
        GameEngine engine = ((YGOProActivity) context).getEngine();
        GameField field = engine != null ? engine.getField() : null;
        if (field != null && askedCard != null && field.effectYnAsking == askedCard) {
            field.effectYnAsking = null;
        }
        askedCard = null;
    }

    /** 询问窗卡码对应的可发动实体卡：优先按本连锁询问入列的 activatableCards 条目查
     *  （含 chain 专属条目与 conti 待处理项），退而按场地坐标取场卡；均无则合成携带
     *  归属半场（本地 controler）与区域坐标的临时卡，使按卡码展示的详情也能命中
     *  登记簿对应半场的呼吸条目（双方同色 holo_blue_bright，半场仅作查询隔离） */
    private ClientCard resolveActivatableClientCard(int code) {
        int masked = code & 0x7fffffff;
        if (masked <= 0 || !(context instanceof YGOProActivity)) return null;
        GameEngine engine = ((YGOProActivity) context).getEngine();
        if (engine == null) return null;
        // EFFECT_YN 询问卡（用户规格：点被询问的卡/询问窗卡图都要出对应序号发光）：
        // 优先按本窗留存坐标取场上实体卡，取不到（overlay/卡组遮蔽）合成带归属半场的临时卡
        if (askedEffectValid && masked == (askedCode & 0x7fffffff)) {
            GameField askedField = engine.getField();
            ClientCard cc = askedField == null ? null
                    : askedField.getCard(askedSide, askedLocation, askedSequence);
            if (cc != null) return cc;
            ClientCard tmp = new ClientCard();
            tmp.code = askedCode;
            tmp.controler = askedSide;
            tmp.location = askedLocation;
            tmp.sequence = askedSequence;
            tmp.position = 0x1;
            return tmp;
        }
        for (GameEngine.CmdCardInfo info : engine.activatableCards) {
            if (info.card != null && (info.code & 0x7fffffff) == masked
                    && (info.flag & 0x1) == 0) {
                return info.card;
            }
        }
        GameField field = engine.getField();
        for (CardChainItem item : chainCardsSafe()) {
            if ((item.code & 0x7fffffff) != masked) continue;
            int loc = item.location & 0x7f;
            if ((item.location & 0x80) != 0 || field == null) continue;
            ClientCard cc = field.getCard(engine.localPlayer(item.controler & 1), loc, item.sequence);
            if (cc != null && (cc.displayCode() & 0x7fffffff) == masked) return cc;
            // 卡组/额外等遮蔽区场卡码可能为 0 或未同步：按报码合成临时卡仍取坐标归属
            ClientCard tmp = new ClientCard();
            tmp.code = item.code;
            tmp.controler = engine.localPlayer(item.controler & 1);
            tmp.location = loc;
            tmp.sequence = item.sequence;
            tmp.position = 0x1;
            return tmp;
        }
        return null;
    }

    /** chainCards 安全快照：null/空集统一返空列表，免循环内判空 */
    private List<CardChainItem> chainCardsSafe() {
        return chainCards != null ? chainCards : Collections.<CardChainItem>emptyList();
    }

    /**
     * 根据协议 location 值获取区域文本。此前手写十六进制映射把 ocgcore 常量对错了位
     *（0x02 实为手卡却写成墓地、0x10 实为墓地却写成除外……），改为直接复用
     * {@link DataManager#formatLocation(int, int)}（已按 0x1&lt;&lt;i → 系统串 1000+i 对齐 gframe，
     * 并对灵摆/场地/魔陷区按 sequence 细分）。去掉 0x80 等叠加位后传入。
     */
    protected String getLocationText(int location, int sequence) {
        return DataManager.get().formatLocation(location & 0x7f, sequence);
    }

    /** Chain card item container for chain query */
    public static class CardChainItem {
        public final int code;
        public final int controler;
        public final int location;
        public final int sequence;

        public CardChainItem(int code, int controler, int location, int sequence) {
            this.code = code;
            this.controler = controler;
            this.location = location;
            this.sequence = sequence;
        }
    }
    /**
     * 当 swapButtonPositions 设置变化时，原地刷新当前已显示 Dialog 的按钮左右顺序。
     * 用户规格：SettingsDialog 勾选/取消后立即生效，正在显示的 YesOrNoDialog 也要即时切换；
     * 且交换后点击事件仍对应原本「是」「否」语义——通过只调换 buttonArea 中两个 Button 子 View
     * 的物理顺序（btnPositive ↔ btnNegative 索引交换）实现，click listener 保持绑定在原按钮对象上。
     * 不 dismiss、不重建 PopupWindow，避免动画与焦点抖动。
     */
    public void refreshSwapButtonPositions() {
        if (popupWindow == null || !popupWindow.isShowing()) return;
        if (type != TYPE_YES_NO || contentView == null) return;
        boolean swap = AppsSettings.get().getIntSettings("swapButtonPositions", 0) == 1;
        this.swapButtonPositions = swap;
        LinearLayout area = contentView.findViewById(R.id.yes_no_button_area);
        Button pos = contentView.findViewById(R.id.btn_yes_no_positive);
        Button neg = contentView.findViewById(R.id.btn_yes_no_negative);
        if (area == null || pos == null || neg == null) return;
        if (neg.getVisibility() != View.VISIBLE) return; // 单按钮态不参与交换
        float density = context.getResources().getDisplayMetrics().density * DialogScale.factor(context);
        int twentyDp = (int) (density * 20 + 0.5f);
        LinearLayout.LayoutParams lpP = (LinearLayout.LayoutParams) pos.getLayoutParams();
        LinearLayout.LayoutParams lpN = (LinearLayout.LayoutParams) neg.getLayoutParams();
        lpP.weight = 1.0f;
        lpN.weight = 1.0f;
        int idxP = area.indexOfChild(pos);
        int idxN = area.indexOfChild(neg);
        if (swap) {
            // 目标：左 = neg、右 = pos；20dp 间隙归左边的 neg
            if (idxN > idxP) {
                area.removeView(pos);
                area.addView(pos, area.getChildCount());
            }
            lpP.setMarginEnd(0);
            lpN.setMarginEnd(twentyDp);
        } else {
            // 目标：左 = pos、右 = neg；20dp 间隙归左边的 pos
            if (idxP > idxN) {
                area.removeView(neg);
                area.addView(neg, area.getChildCount());
            }
            lpP.setMarginEnd(twentyDp);
            lpN.setMarginEnd(0);
        }
        pos.setLayoutParams(lpP);
        neg.setLayoutParams(lpN);
        area.requestLayout();
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

    /**
     * 获取内容视图：优先返回自定义内容（setOptionsContentView / setContentView 注入的布局），
     * 否则返回 dialog_yes_or_no 默认 root。
     */
    public View getContentView() {
        return customContentView != null ? customContentView : contentView;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 统一对话框工厂：原 ShowDialogUtil 中所有基于 YesOrNoDialog 的弹窗构建迁移至此。
    // 游戏规则回调（连锁进场 enterChainFieldMode / 放弃连锁 finishChainPass /
    // 清理高亮 clearChainSelect / 卡组载入 loadAndSendDeck）仍留在 ShowDialogUtil，
    // 经 activity.getDialogUtil() 调用，避免把协议状态机塞进 UI 组件。
    // ══════════════════════════════════════════════════════════════════════

    /** 当前连锁是/否询问窗（对齐 gframe wQuery）；保留引用以支持「取消操作」重新弹出 */
    protected static YesOrNoDialog chainQueryDialog;
    /** 当前连锁是否为强制连锁（chain_forced）：强制时不弹询问窗、不允许取消/放弃 */
    protected static boolean chainForcedMode;

    /**
     * 正在显示的 YesOrNoDialog 实例登记（show 成功后加入，PopupWindow dismiss 时移除）：
     * ①询问类工厂弹新窗前统一关闭仍在显示的旧询问窗，保证纯单人模式双方各有可发动卡片时
     * 连锁询问严格按协议到达顺序（当前回合玩家在先、答“否”后才轮到对方）逐个单窗显示，
     * 不再多窗叠加；②点击撤回上一步时即时隐藏全部在显询问窗，撤回被拒时可整体恢复。
     */
    protected static final LinkedHashSet<YesOrNoDialog> sShowingDialogs = new LinkedHashSet<>();

    /** 当前正在显示的弹窗快照（调用方可安全遍历/逐个 dismiss） */
    public static List<YesOrNoDialog> snapshotShowingDialogs() {
        return new ArrayList<>(sShowingDialogs);
    }

    /** 是否有是/否询问窗正在显示（CmdMenuDialog 据此决定黄色虚线可选卡是否隐藏「发动」按钮） */
    public static boolean isShowingAny() {
        return !sShowingDialogs.isEmpty();
    }

    /** 关闭全部正在显示的弹窗（询问工厂弹新窗前调用，保证同屏至多一个询问窗） */
    public static void dismissShowingDialogs() {
        for (YesOrNoDialog d : snapshotShowingDialogs()) {
            d.dismiss();
        }
    }

    public static YesOrNoDialog getChainQueryDialog() { return chainQueryDialog; }
    public static void setChainQueryDialog(YesOrNoDialog dialog) { chainQueryDialog = dialog; }
    public static boolean isChainForcedMode() { return chainForcedMode; }
    public static void setChainForcedMode(boolean forced) { chainForcedMode = forced; }

    /** 关闭并清空连锁询问窗、复位强制连锁标志（断线/结束/退出场上模式时调用） */
    public static void dismissChainQuery() {
        if (chainQueryDialog != null) {
            chainQueryDialog.dismiss();
            chainQueryDialog = null;
        }
        chainForcedMode = false;
    }

    // ── 私有静态桥接 ──
    protected static CardDetailPanel panel(YGOProActivity activity) { return activity.getCardDetailPanel(); }
    protected static String sysText(int index, String defText) {
        return DataManager.get().getStringManager().getSystemString(index, defText);
    }
    protected static String sysFormat(int index, String defText, Object... args) {
        return DataManager.get().formatSystemString(index, defText, args);
    }
    protected static View gameDialogRegion(YGOProActivity activity) {
        return activity.findViewById(R.id.layout_game_right);
    }

    /**
     * 在连锁询问文本前拼接 event_string（对齐 duelclient.cpp MSG_SELECT_CHAIN L2176/2178：
     * stQMessage = event_string + "\n" + 询问语）。event_string 为空时原样返回。
     */
    protected static String prependChainEvent(YGOProActivity activity, String text) {
        GameEngine engine = activity.getEngine();
        GameField field = engine != null ? engine.getField() : null;
        String event = field != null ? field.eventString : null;
        return (event == null || event.isEmpty()) ? text : event + "\n" + text;
    }
    /** playerop.cpp L624-637：按列表顺序贪心分摊玩家选定的取除总数 */
    protected static void sendCounterResponse(YGOProActivity activity, List<Integer> cardCounters, int total) {
        ByteBuffer buf = ByteBuffer.allocate(2 * cardCounters.size());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int remain = total;
        for (int i = 0; i < cardCounters.size(); i++) {
            int take = Math.min(remain, cardCounters.get(i));
            buf.putShort((short) take);
            remain -= take;
        }
        activity.getEngine().sendResponse(buf.array());
    }

    protected YesOrNoDialogBase(Context context) {
        this.context = context;
    }
}

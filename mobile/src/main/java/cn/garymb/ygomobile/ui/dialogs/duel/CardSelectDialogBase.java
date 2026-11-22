package cn.garymb.ygomobile.ui.dialogs.duel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.card.CardStatusTipHelper;

import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * Card select popup dialog, base layer of the split inheritance chain
 * (CardSelectDialogBase <- CardSelectDialogMid <- CardSelectDialog).
 * Owns every state field, the CardItem / listener nested types, the
 * selection-state queries, the display-order protocol index mapping,
 * the sum helpers, cover-bitmap decoding, location label formatting
 * and the dismiss surface. The original constructor body lives here
 * under the base name so the final context/imageLoader fields stay
 * final.
 */
abstract class CardSelectDialogBase {

    public static final int SLOT_COUNT = 5;

    public static final int MODE_SELECT = 0;
    public static final int MODE_UNSELECT = 1;
    public static final int MODE_SUM = 2;
    public static final int MODE_SORT = 3;

    // 位置标签配色，映射 client_field.h CARD_LIST_* 常量
    protected static final int COLOR_DEFAULT = YGOUtil.c(R.color.colorMain);   // CARD_LIST_DEFAULT_BACKGROUND_COLOR
    protected static final int COLOR_SELECTED = YGOUtil.c(R.color.card_list_selected_bg);  // CARD_LIST_SELECTED_BACKGROUND_COLOR
    protected static final int COLOR_OPPONENT = YGOUtil.c(R.color.card_list_opponent_gray);  // CARD_LIST_OPPONENT_BACKGROUND_COLOR

    // 卡图宽高比 177:254：ImageView 高度 = 宽度 × CARD_ASPECT
    protected static final float CARD_ASPECT = 254f / 177f;
    // 对话框宽度取 layout_game_right 实际宽度的四分之三（横屏沿用）
    protected static final float DIALOG_WIDTH_RATIO = 0.75f;
    /**
     * 竖屏弹窗宽度比例（用户规格：宽度与文本系数解耦，落在屏宽内、两侧保留少量留白）：
     * 竖屏下 {@code layout_game_right} 即整屏宽，本比例直接就是屏宽占比，0.96 即两侧各留约 2%。
     * 旧写法宽度 = 0.75 × {@link #PORTRAIT_SCALE}（文本系数同时拉宽），提到 0.82×1.25≈1.03
     * 已被屏宽限幅到满屏（两侧无留白）；现宽度只看本比例、文本只看 {@link #PORTRAIT_SCALE}，
     * 两者互不拉扯。横屏仍用 {@link #DIALOG_WIDTH_RATIO} × {@link #LANDSCAPE_SCALE}，行为不变。
     */
    protected static final float PORTRAIT_WIDTH_RATIO = 0.96f;
    /**
     * 竖屏文本放大系数（用户规格：由 1.25 调小）：现只作用于标题/卡位文本/确定按钮字号，
     * 不再参与宽度解算（见 {@link #PORTRAIT_WIDTH_RATIO}）。调小文本后：
     * 一是卡位标签（如「魔法陷阱区[6]」、合计模式的「 值:xxx/xxx」）更容易装进卡宽，
     * 少缩字、少省略；二是标签行/标题行高度变矮，整组更紧凑，卡图在屏宽内的占比反而更高。
     */
    protected static final float PORTRAIT_SCALE = 1.1f;
    /**
     * 横屏整体缩小系数（宽与文本同比）：横屏 {@code layout_game_right} 为长边、绝对宽更大，
     * 按 {@link #DIALOG_WIDTH_RATIO} (0.75) 解算后弹窗与每张卡图仍偏大。按需求把横屏弹窗再
     * 等比例小一号——弹窗宽、卡图、标题/卡位文本/确定按钮字号同乘本系数，列表观感更紧凑。
     */
    protected static final float LANDSCAPE_SCALE = 0.9f;
    /**
     * 每个卡槽的水平外边距（dp，左右各一份）：与各 dialog_card_*.xml 中槽位
     * {@code layout_marginHorizontal} 一致，由原 4dp 收紧为 2dp 使卡片列表更紧凑
     *（卡宽据此反推，需与 {@link #applyCardImageSize(int)} 扣除量同步）。卡间距恒为
     * 2×本值，不足五张时空槽 GONE、已显示槽保持同一固定宽→间距不变、整组居中。
     */
    protected static final float SLOT_MARGIN_DP = 2f;

    public static class CardItem {
        public final int code;
        public final int controler;
        public final int location;
        public final int sequence;
        public final int subSeq;
        public final int selectSeq;
        public final int opParam;
        public final int op1;
        public final int op2;

        public CardItem(int code, int controler, int location, int sequence, int subSeq, int selectSeq) {
            this(code, controler, location, sequence, subSeq, selectSeq, 0);
        }

        public CardItem(int code, int controler, int location, int sequence, int subSeq, int selectSeq, int opParam) {
            this.code = code;
            this.controler = controler;
            this.location = location;
            this.sequence = sequence;
            this.subSeq = subSeq;
            this.selectSeq = selectSeq;
            this.opParam = opParam;
            int o1 = opParam & 0xffff;
            int o2 = (opParam >> 16) & 0xffff;
            if ((o2 & 0x8000) != 0) {
                o1 = opParam & 0x7fffffff;
                o2 = 0;
            }
            this.op1 = o1;
            this.op2 = o2;
        }
    }

    public interface OnCardSelectListener {
        default void onCardsSelected(List<Integer> selectedIndices) {
        }

        default void onCardClicked(int index) {
        }

        default void onSorted(int[] respBuf) {
        }

        default void onCancel() {
        }
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    protected final Context context;
    protected final ImageLoader imageLoader;
    protected final Handler handler = new Handler(Looper.getMainLooper());

    /** 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例） */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    protected PopupWindow popupWindow;
    protected DraggablePopupHelper draggableHelper;
    /** 点击查看详情 + 常显通讯状态标签（每次 build 随包装层重建） */
    protected CardStatusTipHelper tipHelper;

    protected int mode = MODE_SELECT;
    protected String title = "选择卡片";
    protected List<CardItem> cards = new ArrayList<>();
    protected List<CardItem> mustCards = new ArrayList<>();
    protected boolean[] selected = new boolean[0];
    protected int minSelect = 0;
    protected int maxSelect = 1;
    protected boolean cancelable = false;
    protected boolean finishable = false;
    protected int sumValue = 0;
    protected int sumMode = 0;
    protected int localPlayer = -1;
    protected boolean showValues = false;
    protected int pageOffset = 0;
    /**
     * item.controler 是否为协议侧索引：ShowDialogUtil 由通讯解析构建 → true（默认）；
     * CmdMenuDialog 由场上模型构建（已是视角侧）→ false。视角侧转换见 {@link #viewControler(int)}。
     */
    protected boolean controlerProtocolSide = true;

    protected final List<Integer> clickOrder = new ArrayList<>();
    protected int[] sortList = new int[0];
    protected int sortCounter = 0;
    /**
     * 显示序 → 协议序索引的排布表（长度 = 显示总数），在 {@link #show(View)} 时按初始
     * 选择状态构建一次，本次会话内固定不变（避免点选时卡片跳动）。
     * 规则：沿用原有「协议序整体反转」的相对顺序，但把已选中的可选卡稳定地挪到列表末尾
     */
    protected int[] displayOrder = new int[0];

    protected TextView tvTitle;
    protected SeekBar sbPage;
    protected Button btnOk;
    protected final View[] slotViews = new View[SLOT_COUNT];
    protected final TextView[] tvPositions = new TextView[SLOT_COUNT];
    protected final ImageView[] ivCards = new ImageView[SLOT_COUNT];
    /** 单卡槽步进（px，卡宽 + 左右外边距 {@link #SLOT_MARGIN_DP}dp×2）：滚动条翻页动画的位移基准，随卡图在 {@link #applyCardImageSize(int)} 烘培 */
    protected int slotStepPx = 0;
    /**
     * 卡位标签可用宽（px）= 烘培后的固定槽宽，随 {@link #applyCardImageSize(int)} 更新：
     * 标签只能在这个宽度里缩字，不得反向把槽位撑宽（否则整行溢出弹窗宽，最右一张卡与其
     * 上方标签被裁掉一半，用户反馈的竖屏第 5 张卡只显示一半的根因）。
     */
    protected int slotLabelMaxPx = 0;
    /**
     * 皮肤宿主（TipHostLayout）：对话框背景贴图（sdialogl 九宫格）已由 {@code createTipHost}
     * 从内容根上移到宿主，因此内容被内缩的量就是宿主的左右内边距，卡宽反推必须以它为准
     *（见 {@link CardSelectDialogMid#containerWidthFor(int)}），不能硬编 16dp。
     */
    protected View contentHost;
    /**
     * 上一次已应用到卡图/槽位的内容可用宽（px）：插槽行是 match_parent，布局后的实测宽即真实可用宽；
     * 除宿主内边距外仍可能有行/根的 margin 等隐藏内缩，故实测值与已应用值不符时按实测重烘一次
     *（见 {@link CardSelectDialogMid#bindContainerWidthCorrection()}），确保五槽总宽恒等于可用宽。
     */
    protected int appliedContainerW = 0;
    /**
     * 标题/卡位文本/确定按钮的基准字号（px，取自 XML，横竖屏 density 相同故一致）：首次 {@link #applyContentScale()}
     * 时捕获，此后按当前方向系数以绝对字号回写，避免横竖屏来回旋转时系数叠乘使文本越放越大。
     */
    protected float baseTitlePx;
    protected float basePosPx;
    protected float baseOkPx;

    protected OnCardSelectListener listener;
    protected OnDismissListener dismissListener;

    // 卡背缓存：对齐 image_manager.cpp tButtonFacedown[0/1]（我方 cover.jpg / 对方 cover2.jpg）
    protected static Bitmap coverSelf;
    protected static Bitmap coverOpponent;

    protected CardSelectDialogBase(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    public int getSelectedCount() {
        int count = 0;
        for (boolean b : selected) {
            if (b) count++;
        }
        if (mode == MODE_SUM) count += mustCards.size();
        return count;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public boolean isReady() {
        if (mode == MODE_SUM) {
            int optCount = getSelectedCount() - mustCards.size();
            return optCount >= minSelect && optCount <= maxSelect && checkSumValue();
        }
        return getSelectedCount() >= minSelect;
    }

    public void confirm() {
        if (mode == MODE_SELECT || mode == MODE_SUM) {
            if (!isReady()) return;
            if (listener != null) listener.onCardsSelected(new ArrayList<>(clickOrder));
        } else if (mode == MODE_UNSELECT) {
            // event_handler.cpp BUTTON_CARD_SEL_OK (MSG_SELECT_UNSELECT_CARD L968-971)：
            // 完成按钮 = 发送 -1，仅关闭弹窗会导致协议挂起
            if (listener != null) listener.onCancel();
        }
        dismiss();
    }

    protected int getDisplayCount() {
        return cards.size() + mustCards.size();
    }

    /**
     * 显示序 → 协议序索引：沿用协议序整体反转的相对顺序（协议末位卡排在第一），
     * 并把已选中的可选卡稳定地排到列表末尾（见 {@link #buildDisplayOrder()}）。
     * selected/sortList/点击回调/协议响应全部经此换算取协议索引，排布仅影响槽位顺序，
     * 不影响发送协议的索引语义。排布表未构建时兜底为纯反转。
     */
    protected int displayToProto(int displayIdx) {
        int count = getDisplayCount();
        if (displayIdx < 0 || displayIdx >= count) return -1;
        if (displayOrder != null && displayOrder.length == count) return displayOrder[displayIdx];
        return count - 1 - displayIdx;
    }

    /**
     * 按初始选择状态构建显示排布：先保持既有「协议序整体反转」的相对顺序，再把已选中的
     * 可选卡（非必选卡）稳定挪到末尾。必选卡与未选卡留在前段相对顺序不变，因此无预选的普通
     * 选择弹窗排布与旧行为完全一致；仅取消选择/带预选的重弹弹窗会把已选卡沉到列表最后。
     */
    protected void buildDisplayOrder() {
        int count = getDisplayCount();
        int m = mustCards.size();
        int[] ordered = new int[count];
        int w = 0;
        // 前段：未挪到末尾的卡（必选卡 + 未选可选卡），保持协议序反转的相对顺序
        for (int d = 0; d < count; d++) {
            int p = count - 1 - d;
            if (!isMovedToLast(p, m)) ordered[w++] = p;
        }
        // 末段：已选中的可选卡，同样按协议序反转的相对顺序追加
        for (int d = 0; d < count; d++) {
            int p = count - 1 - d;
            if (isMovedToLast(p, m)) ordered[w++] = p;
        }
        displayOrder = ordered;
    }

    /** 该协议序是否为「需挪到末尾」的已选可选卡（必选卡不算，保持在原位） */
    protected boolean isMovedToLast(int proto, int mustCount) {
        if (proto < mustCount) return false;
        int sel = proto - mustCount;
        return sel >= 0 && sel < selected.length && selected[sel];
    }

    /** 显示序 → 卡片项：经 displayToProto 反转换回协议序（必选卡在前、可选卡在后），越界返回 null */
    protected CardItem getItemAt(int displayIdx) {
        int idx = displayToProto(displayIdx);
        if (idx < 0) return null;
        if (idx < mustCards.size()) return mustCards.get(idx);
        idx -= mustCards.size();
        return idx < cards.size() ? cards.get(idx) : null;
    }

    /** 显示序 → 是否已选：必选卡恒视为已选（对齐 client_field.cpp 必选卡高亮） */
    protected boolean isSelectedAt(int displayIdx) {
        int idx = displayToProto(displayIdx);
        if (idx < 0) return false;
        if (idx < mustCards.size()) return true;
        idx -= mustCards.size();
        return idx >= 0 && idx < selected.length && selected[idx];
    }

    protected GameEngine engine() {
        if (context instanceof YGOProActivity) return ((YGOProActivity) context).getEngine();
        return null;
    }

    /** 槽位 → 卡片项：槽位先加 pageOffset 换算为显示序，越界返回 null（空槽/翻页尾部） */
    protected CardItem itemAtSlot(int slot) {
        int index = pageOffset + slot;
        if (index < 0 || index >= getDisplayCount()) return null;
        return getItemAt(index);
    }

    /** 协议侧 controler → 本地视角侧（localPlayer 为对合映射，视角侧口径时原样返回） */
    protected int viewControler(int controler) {
        if (!controlerProtocolSide) return controler & 1;
        GameEngine eng = engine();
        return eng != null ? eng.localPlayer(controler & 1) : (controler & 1);
    }

    /**
     * 按下即在 CardDetailPanel 显示卡片详情：详情面板只显卡表原始数据（完全由 code 决定，
     * 对齐 game.cpp Game::ShowCardInfo(int code) 按 code 查数据库），因此直接采用与
     * updateSlotView 卡图绑定同源的 item.code & 0x7fffffff 从 CardManager 获取——
     * 场上实时卡（resolveLiveCard）暗卡时 code==0，不能用它判断，否则能看见卡图却只显示未知/卡背；
     * code==0（该槽显示的是卡背图）时对齐 C++ ClearCardInfo 显示卡背默认面板
     */
    protected void showDetailAtSlot(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item == null || !(context instanceof YGOProActivity)) return;
        ClientCard card = new ClientCard();
        card.code = item.code & 0x7fffffff;
        card.controler = viewControler(item.controler);
        card.location = item.location & 0x7f;
        card.sequence = item.sequence;
        ((YGOProActivity) context).showCardInfoPanel(card);
    }

    /** 常显标签文字：通讯状态（ShowCardInfoInList 等价物），点击即取、换卡即换文字 */
    protected String statusTextAtSlot(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item == null) return null;
        GameEngine eng = engine();
        GameField field = eng != null ? eng.getField() : null;
        ClientCard card = CardStatusTipHelper.resolveLiveCard(field,
                viewControler(item.controler), item.location, item.sequence, item.subSeq);
        if (card == null && (item.code & 0x7fffffff) != 0) {
            card = new ClientCard();
            card.code = item.code & 0x7fffffff;
        }
        return CardStatusTipHelper.buildStatusText(field, card);
    }


    /** 当前方向的内容缩放系数：横屏 {@link #LANDSCAPE_SCALE}（等比例小一号），竖屏 {@link #PORTRAIT_SCALE} */
    protected float contentScale() {
        return DialogScale.isLandscape(context) ? LANDSCAPE_SCALE : PORTRAIT_SCALE;
    }

    protected int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 卡背图（core skin 路径，与 CardDetailPanel/TextureLoader 惯例一致）；
     * 对方卡优先 cover2.jpg，缺失时回退 cover.jpg（对齐 image_manager.cpp L280-282）
     */
    protected Bitmap getCoverBitmap(boolean opponent) {
        if (coverSelf == null || coverSelf.isRecycled()) {
            coverSelf = decodeCover(Constants.CORE_SKIN_COVER);
        }
        if (opponent) {
            if (coverOpponent == null || coverOpponent.isRecycled()) {
                coverOpponent = decodeCover(Constants.CORE_SKIN_COVER2);
            }
            if (coverOpponent != null) return coverOpponent;
        }
        return coverSelf;
    }

    protected Bitmap decodeCover(String name) {
        try {
            File file = new File(AppsSettings.get().getCoreSkinPath(), name);
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Throwable ignored) {
            // 解码失败按缺失处理，回退 unknown 占位图
        }
        return null;
    }

    protected boolean checkSumValue() {
        List<int[]> values = new ArrayList<>();
        for (CardItem m : mustCards) {
            values.add(new int[]{m.op1, m.op2});
        }
        for (int idx : clickOrder) {
            if (idx >= 0 && idx < selected.length && selected[idx]) {
                CardItem c = cards.get(idx);
                values.add(new int[]{c.op1, c.op2});
            }
        }
        if (values.isEmpty()) return false;
        if (sumMode == 0) {
            return subsetSum(values, 0, sumValue);
        }
        int sum = 0;
        for (int[] v : values) {
            sum += (v[1] > 0 && v[0] > v[1]) ? v[1] : v[0];
        }
        return sum >= sumValue;
    }

    protected boolean subsetSum(List<int[]> values, int index, int target) {
        if (target == 0) return true;
        if (index >= values.size() || target < 0) return false;
        int[] v = values.get(index);
        if (subsetSum(values, index + 1, target - v[0])) return true;
        if (v[1] > 0 && subsetSum(values, index + 1, target - v[1])) return true;
        return false;
    }

    /** 合计模式括号内进度：当前值区间/目标值（对齐 client_field.cpp L1109 hint(cur/target)） */
    protected String sumProgress() {
        List<CardItem> all = new ArrayList<>(mustCards);
        for (int i = 0; i < cards.size(); i++) {
            if (selected[i]) all.add(cards.get(i));
        }
        int curL = 0, curH = 0;
        for (CardItem c : all) {
            int opmin = (c.op2 > 0 && c.op1 > c.op2) ? c.op2 : c.op1;
            curL += opmin;
            curH += Math.max(c.op1, c.op2);
        }
        String cur = (curL == curH) ? String.valueOf(curL) : curL + "-" + curH;
        String target = (sumMode == 0) ? String.valueOf(sumValue) : sumValue + "+";
        return cur + "/" + target;
    }


    protected String labelText(CardItem item) {
        String text = formatLocation(item);
        if (showValues || mode == MODE_SUM) {
            if (item.op2 > 0) {
                text += " 值:" + item.op1 + "/" + item.op2;
            } else {
                text += " 值:" + item.op1;
            }
        }
        return text;
    }

    protected String formatLocation(CardItem item) {
        if (item.location == 0x80) {
            return getLocationName(item.location, item.sequence) + "[" + (item.sequence + 1) + "](" + (item.subSeq + 1) + ")";
        }
        return getLocationName(item.location, item.sequence) + "[" + (item.sequence + 1) + "]";
    }

    /**
     * 移植 data_manager.cpp FormatLocation：STRING_ID_LOCATION=1000 按位索引；
     * LOCATION_SZONE 按 sequence 区分魔陷区/场地魔法区/灵摆区
     */
    protected String getLocationName(int location, int sequence) {
        if (location == 0x08) {
            if (sequence < 5) {
                return mStringManager.getSystemString(1003, "魔陷区");
            } else if (sequence == 5) {
                return mStringManager.getSystemString(1008, "场地魔法区");
            } else {
                return mStringManager.getSystemString(1009, "灵摆区");
            }
        }
        int stringId = 0;
        for (int i = 0; i < 10; i++) {
            if ((0x1 << i) == location) {
                stringId = 1000 + i;
                break;
            }
        }
        if (stringId != 0) {
            return mStringManager.getSystemString(stringId, "区域" + location);
        }
        return "区域" + location;
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

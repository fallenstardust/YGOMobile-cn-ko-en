package cn.garymb.ygomobile.ui.cards.deck;


import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.DeckLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.CardListProvider;
import cn.garymb.ygomobile.utils.CardSort;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;
/**
 * 在对应的recyclerview中添加了很多透明的item来“占位”，占位后便于根据“拖动卡片的位置”计算添加的卡片类型（是主卡组、额外卡组还是副卡组）
 */
public class DeckAdapater extends DeckAdapaterBase {

    public DeckAdapater(Context context, RecyclerView recyclerView, ImageLoader imageLoader) {
        super(context, recyclerView, imageLoader);
    }

    // === 批量勾选模式（长按卡组网格卡片进入 / 点击勾选 / 按住整组拖动）===

    /** 是否处于批量勾选模式：模式下 ItemTouchHelper 的单卡拖拽被接管（见 DeckItemTouchHelper） */
    private boolean mBatchMode;
    /** 已勾选卡片在 mItems 中的绝对下标（含分隔栏与占位格的下标会被过滤，不可勾选） */
    private final TreeSet<Integer> mBatchChecked = new TreeSet<>();

    public boolean isBatchMode() {
        return mBatchMode;
    }

    /**
     * 切换批量勾选模式：退出时一并清空勾选，下次进入从空集开始。
     */
    public void setBatchMode(boolean batchMode) {
        if (mBatchMode == batchMode) return;
        mBatchMode = batchMode;
        mBatchChecked.clear();
        notifyDataSetChanged();
    }

    public boolean isBatchChecked(int pos) {
        return mBatchMode && mBatchChecked.contains(pos);
    }

    public boolean hasBatchChecked() {
        return !mBatchChecked.isEmpty();
    }

    /** 当前已勾选的卡片绝对下标（升序） */
    public List<Integer> getBatchCheckedPositions() {
        return new ArrayList<>(mBatchChecked);
    }

    /**
     * 切换某格的勾选态：分隔栏与占位格不可勾选。
     *
     * @return 切换后是否为已勾选，参数非法返回 false
     */
    public boolean toggleBatchCheck(int pos) {
        if (batchCardAt(pos) == null) return false;
        if (mBatchChecked.remove(pos)) {
            notifyItemChanged(pos);
            return false;
        }
        mBatchChecked.add(pos);
        notifyItemChanged(pos);
        return true;
    }

    /** 保证该格处于已勾选状态（按住未勾选的卡准备整组拖动时先补勾上） */
    public void ensureBatchChecked(int pos) {
        if (batchCardAt(pos) == null) return;
        if (mBatchChecked.add(pos)) notifyItemChanged(pos);
    }

    /** 清空勾选但保留批量模式（整组搬运后下标己变，无法逐位映射） */
    public void clearBatchChecks() {
        if (mBatchChecked.isEmpty()) return;
        List<Integer> old = new ArrayList<>(mBatchChecked);
        mBatchChecked.clear();
        for (Integer pos : old) notifyItemChanged(pos);
    }

    /** 安全取卡片型条目：越界、分隔栏、占位格均返回 null（同包的 DeckBatchEditController 用它判定可勾选位） */
    public Card batchCardAt(int pos) {
        if (pos < 0 || pos >= mItems.size()) return null;
        DeckItem item = mItems.get(pos);
        return item == null ? null : item.getCardInfo();
    }

    /**
     * 整组搬运一次完成：把已勾选的卡按类型与容量规则移到目标卡区的落点处。
     * <ul>
     * <li>目标为主卡组：只能放额外卡组的卡（融合 / 同调 / 超量 / 连接）保持原位不动，
     *     剩下的卡整组搬；若整组都是额外卡组卡则一张也不动并返回原因供上层提示。</li>
     * <li>目标为额外卡组：主卡组向的卡保持原位不动，其余整组搬。</li>
     * <li>目标为副卡组：全部整组搬。</li>
     * <li>搬运前先预演三区张数，任一区会超出上限则整组作废并返回具体原因，不做部分生效。</li>
     * </ul>
     * 不改变卡组总张数（只在三个区之间搬运），因此不走 checkLimit 禁限/积分校验。
     *
     * @param targetType 落点卡区类型
     * @param insertPos  落点在 mItems 中的绝对下标（分隔栏落点已由调用方换为区域末尾）
     * @return 与单卡拖拽同口径的结果枚举，SUCCESS 表示已整体重建卡组视图
     */
    public DragInsertResult batchMoveCheckedTo(DeckItemType targetType, int insertPos) {
        if (!mBatchMode || targetType == null || mBatchChecked.isEmpty()) {
            return DragInsertResult.FAILED;
        }
        DeckInfo state = getCurrentState();
        List<Card> main = state.getMainCards();
        List<Card> extra = state.getExtraCards();
        List<Card> side = state.getSideCards();

        // 1. 勾选位→（源列表、区内下标、卡片），越界或已失效的勾选自动忽略
        List<BatchPick> picks = new ArrayList<>();
        collectBatchPicks(state, picks);
        if (picks.isEmpty()) return DragInsertResult.FAILED;

        // 2. 按落点卡区剔掉不允许进入的卡（保持原位）
        List<BatchPick> movable = new ArrayList<>(picks.size());
        for (BatchPick pick : picks) {
            if (acceptsInZone(targetType, pick.card)) movable.add(pick);
        }
        if (movable.isEmpty()) {
            return targetType == DeckItemType.MainCard
                    ? DragInsertResult.WRONG_PLACE_MAIN : DragInsertResult.WRONG_PLACE_EXTRA;
        }
        List<Card> targetList = zoneList(state, targetType);

        // 3. 张数上限预演：各区减去被摘除数，落点区加上插入数（同区时净为零）
        int outMain = 0, outExtra = 0, outSide = 0;
        for (BatchPick pick : movable) {
            if (pick.source == main) outMain++;
            else if (pick.source == extra) outExtra++;
            else outSide++;
        }
        int newMain = main.size() - outMain + (targetType == DeckItemType.MainCard ? movable.size() : 0);
        int newExtra = extra.size() - outExtra + (targetType == DeckItemType.ExtraCard ? movable.size() : 0);
        int newSide = side.size() - outSide + (targetType == DeckItemType.SideCard ? movable.size() : 0);
        if (newMain > Constants.DECK_MAIN_MAX) return DragInsertResult.MAIN_FULL;
        if (newExtra > Constants.DECK_EXTRA_MAX) return DragInsertResult.EXTRA_FULL;
        if (newSide > Constants.DECK_SIDE_MAX) return DragInsertResult.SIDE_FULL;

        // 4. 落点区内下标：扣除“同区内已摘除且位于落点之前”的条目，否则插入点整体后移
        int rel = insertPos - zoneStart(targetType);
        int shift = 0;
        for (BatchPick pick : movable) {
            if (pick.source == targetList && pick.index < rel) shift++;
        }
        int insert = Math.max(0, Math.min(rel - shift, targetList.size()));

        // 5. 同一源列表按下标降序摘除，避免前面的摘除影响后面的下标
        List<BatchPick> descending = new ArrayList<>(movable);
        descending.sort(Comparator.comparingInt((BatchPick p) -> p.index).reversed());
        for (BatchPick pick : descending) {
            if (pick.index < pick.source.size()) pick.source.remove(pick.index);
        }
        // 6. 按原来的勾选顺序（主→额外→副、区内升序）依次插入落点，保持组内相对顺序
        for (BatchPick pick : movable) {
            targetList.add(Math.min(insert, targetList.size()), pick.card);
            insert++;
        }

        // 7. 回到 mItems：走整体重建以同步分隔栏文案与末尾占位格
        rebuildAfterBatchEdit(state);
        return DragInsertResult.SUCCESS;
    }

    /**
     * 删除全部已勾选的卡（批量模式下把整组拖到卡区之外的二次确认入口）。
     * 与整组搬运同口径：先由 mItems 反推三个区的卡片列表、剔除勾选项，再整体重建 mItems
     * 以同步分隔栏文案与末尾占位格。
     *
     * @return 实际删除的张数；无有效勾选时返回 0
     */
    public int batchDeleteChecked() {
        if (!mBatchMode || mBatchChecked.isEmpty()) return 0;
        DeckInfo state = getCurrentState();
        // 勾选位 →（源列表、区内下标），越界或已失效的勾选自动忽略
        List<BatchPick> picks = new ArrayList<>();
        collectBatchPicks(state, picks);
        if (picks.isEmpty()) return 0;
        // 同一源列表按下标降序剔除，避免前面的剔除影响后面的下标
        picks.sort(Comparator.comparingInt((BatchPick p) -> p.index).reversed());
        for (BatchPick pick : picks) {
            if (pick.index < pick.source.size() && pick.source.get(pick.index) == pick.card) {
                pick.source.remove(pick.index);
            }
        }
        rebuildAfterBatchEdit(state);
        return picks.size();
    }

    /**
     * 把当前勾选位汇总成（源列表、区内下标、卡片）条目，越界或下标上已不是原先那张卡
     * 的勾选均丢弃，保证批量操作只会作用于原本被勾上的那些卡。
     *
     * @param state 本次批量操作要在上面改写的卡组快照（三个区的卡片列表）
     * @param out   收集结果
     */
    private void collectBatchPicks(DeckInfo state, List<BatchPick> out) {
        List<Card> main = state.getMainCards();
        List<Card> extra = state.getExtraCards();
        List<Card> side = state.getSideCards();
        for (Integer pos : mBatchChecked) {
            Card card = batchCardAt(pos);
            if (card == null) continue;
            List<Card> source;
            int index;
            if (DeckItemUtils.isMain(pos)) {
                source = main;
                index = pos - DeckItem.MainStart;
            } else if (DeckItemUtils.isExtra(pos)) {
                source = extra;
                index = pos - DeckItem.ExtraStart;
            } else if (DeckItemUtils.isSide(pos)) {
                source = side;
                index = pos - DeckItem.SideStart;
            } else {
                continue;
            }
            if (index < 0 || index >= source.size() || source.get(index) != card) continue;
            out.add(new BatchPick(source, index, card));
        }
    }

    /**
     * 应用另一编辑器同步过来的卡组内容：与批量编辑同样走整体重建以同步分隔栏文案与末尾
     * 占位格，但保留“相对 ydk 文件是否已修改”的 md5 基准与云端 deckId/userId（同步不是
     * 本侧的编辑动作，不能抹掉本侧的未保存事实），也不主动改动批量勾选模式开关
     * （setDeck 重建会自然退出批量模式，因为卡片位置已整体变化）。
     */
    public void applySharedDeck(DeckInfo state) {
        if (state == null) return;
        String prevMd5 = mDeckMd5;
        state.deckId = deckId;
        state.userId = userId;
        setDeck(state, false);
        mDeckMd5 = prevMd5;
        notifyDataSetChanged();
    }

    /**
     * 批量编辑后重建 mItems：搬运/删除不改变“相对 ydk 文件是否已修改”的 md5 基准，
     * 也不改变云端 deckId，因此逐项恢复后再告知列表全量刷新。
     */
    private void rebuildAfterBatchEdit(DeckInfo state) {
        String prevMd5 = mDeckMd5;
        state.deckId = deckId;
        state.userId = userId;
        setDeck(state, false);
        mDeckMd5 = prevMd5;
        mBatchMode = true;
        mBatchChecked.clear();
        notifyDataSetChanged();
    }

    /** 卡片能否落入目标卡区：主卡组不收额外卡组卡，额外卡组只收额外卡组卡，副卡组两者皆收 */
    private static boolean acceptsInZone(DeckItemType zone, Card card) {
        boolean extraOnly = card.isExtraCard();
        if (zone == DeckItemType.MainCard) return !extraOnly;
        if (zone == DeckItemType.ExtraCard) return extraOnly;
        return true;
    }

    private static List<Card> zoneList(DeckInfo state, DeckItemType zone) {
        if (zone == DeckItemType.MainCard) return state.getMainCards();
        if (zone == DeckItemType.ExtraCard) return state.getExtraCards();
        return state.getSideCards();
    }

    private static int zoneStart(DeckItemType zone) {
        if (zone == DeckItemType.MainCard) return DeckItem.MainStart;
        if (zone == DeckItemType.ExtraCard) return DeckItem.ExtraStart;
        return DeckItem.SideStart;
    }

    /** 一个勾选搬运条目：源卡区列表 + 区内下标 + 卡片 */
    private static final class BatchPick {
        final List<Card> source;
        final int index;
        final Card card;

        BatchPick(List<Card> source, int index, Card card) {
            this.source = source;
            this.index = index;
            this.card = card;
        }
    }

    /**
     * 获取当前卡组状态的副本
     *
     * @return 当前卡组状态的副本
     */
    public DeckInfo getCurrentState() {
        // 创建当前状态的深拷贝
        DeckInfo currentState = new DeckInfo();

        // 从 mItems 中提取不同类型的卡片
        List<Card> mainCards = new ArrayList<>();
        List<Card> extraCards = new ArrayList<>();
        List<Card> sideCards = new ArrayList<>();

        for (DeckItem item : mItems) {
            if (item != null && item.getCardInfo() != null) {
                switch (item.getType()) {
                    case MainCard:
                        mainCards.add(item.getCardInfo());
                        break;
                    case ExtraCard:
                        extraCards.add(item.getCardInfo());
                        break;
                    case SideCard:
                        sideCards.add(item.getCardInfo());
                        break;
                }
            }
        }

        // 必须通过 setMainCards/setExtraCards/setSideCards 赋值，
        // 以同步刷新 DeckInfo 内部的 mainCount/extraCount/sideCount，
        // 否则 getMainCount() 会一直是 0，导致积分上限无法随主卡组数量变化。
        currentState.setMainCards(mainCards);
        currentState.setExtraCards(extraCards);
        currentState.setSideCards(sideCards);
        // 通过 mDeckInfo 获取 source
        if (mDeckInfo != null) {
            currentState.source = mDeckInfo.source;
        }
        return currentState;
    }

    public boolean AddCard(Card cardInfo, DeckItemType type) {
        if (cardInfo == null) return false;
        if (cardInfo.isType(CardType.Token)) {
            return false;
        }
        if (type == DeckItemType.MainCard) {
            if (getMainCount() >= Constants.DECK_MAIN_MAX) {
                return false;
            }
            int id = DeckItem.MainStart + getMainCount();
            removeItem(DeckItem.MainEnd);
            addItem(id, new DeckItem(cardInfo, type));
            //notifyItemChanged(DeckItem.MainEnd);//todo 这行好像没啥用啊？？
            notifyItemChanged(id);
            notifyItemChanged(DeckItem.MainLabel);
            return true;
        }
        if (type == DeckItemType.ExtraCard) {
            if (getExtraCount() >= Constants.DECK_EXTRA_MAX) {
                return false;
            }
            int id = DeckItem.ExtraStart + getExtraCount();
            removeItem(DeckItem.ExtraEnd);
            addItem(id, new DeckItem(cardInfo, type));
            notifyItemChanged(DeckItem.ExtraEnd);
            notifyItemChanged(id);
            notifyItemChanged(DeckItem.ExtraLabel);
            return true;
        }
        if (type == DeckItemType.SideCard) {
            if (getSideCount() >= Constants.DECK_SIDE_MAX) {
                return false;
            }
            int id = DeckItem.SideStart + getSideCount();
            removeItem(DeckItem.SideEnd);
            addItem(id, new DeckItem(cardInfo, type));
            notifyItemChanged(DeckItem.SideEnd);
            notifyItemChanged(id);
            notifyItemChanged(DeckItem.SideLabel);
            return true;
        }
        return false;
    }

    /**
     * 拖拽定点插入的结果：成功 或 各类失败原因（用于toast提示）
     * 禁限/积分数量限制不在此校验，由调用方先行校验(FAILED_LIMIT)
     */
    public enum DragInsertResult {
        SUCCESS,
        FAILED_TOKEN,        // 衍生物不能加入卡组
        WRONG_PLACE_MAIN,    // 额外卡组卡牌不能插入主卡组区域
        WRONG_PLACE_EXTRA,   // 普通卡牌不能插入额外卡组区域
        MAIN_FULL,           // 主卡组已满
        EXTRA_FULL,          // 额外卡组已满
        SIDE_FULL,           // 副卡组已满
        FAILED_LIMIT,        // 卡片数量或积分限制（具体原因由校验方提示）
        FAILED               // 其他失败
    }

    /**
     * 将卡片插入到指定卡区的精确位置（拖拽松手后落点卡到停留位置，挤开此处其他卡片）。
     * 各区域item总数不变：目标区域末尾空格被移除，插入点新增卡片，副产物空格补回区域末尾。
     *
     * @param cardInfo 要插入的卡片
     * @param type     目标卡区类型（MainCard/ExtraCard/SideCard）
     * @param insertPos 在mItems中的绝对插入位置（分隔栏落点传区域末尾）
     * @return 插入结果
     */
    public DragInsertResult dragInsertAt(Card cardInfo, DeckItemType type, int insertPos) {
        if (cardInfo == null || type == null) {
            return DragInsertResult.FAILED;
        }
        if (cardInfo.isType(CardType.Token)) {
            return DragInsertResult.FAILED_TOKEN;
        }
        switch (type) {
            case MainCard: {
                if (cardInfo.isExtraCard()) {
                    return DragInsertResult.WRONG_PLACE_MAIN;
                }
                int count = getMainCount();
                if (count >= Constants.DECK_MAIN_MAX) {
                    return DragInsertResult.MAIN_FULL;
                }
                int rel = insertPos - DeckItem.MainStart;
                if (rel < 0) rel = 0;
                if (rel > count) rel = count;
                int pos = DeckItem.MainStart + rel;
                DeckItem space = removeItem(DeckItem.MainEnd);
                addItem(pos, new DeckItem(cardInfo, DeckItemType.MainCard));
                addItem(DeckItem.SideEnd, space);
                // 主区：末尾空格移除+插入点新增，插入点后的卡依次后移一位；副区末尾新增空格
                notifyItemRemoved(DeckItem.MainEnd);
                notifyItemInserted(pos);
                if (pos + 1 <= DeckItem.MainEnd - 1) {
                    notifyItemRangeChanged(pos + 1, DeckItem.MainEnd - 1 - pos);
                }
                notifyItemChanged(DeckItem.SideEnd);
                notifyItemChanged(DeckItem.MainLabel);
                notifyItemChanged(DeckItem.SideLabel);
                return DragInsertResult.SUCCESS;
            }
            case ExtraCard: {
                if (!cardInfo.isExtraCard()) {
                    return DragInsertResult.WRONG_PLACE_EXTRA;
                }
                int count = getExtraCount();
                if (count >= Constants.DECK_EXTRA_MAX) {
                    return DragInsertResult.EXTRA_FULL;
                }
                int rel = insertPos - DeckItem.ExtraStart;
                if (rel < 0) rel = 0;
                if (rel > count) rel = count;
                int pos = DeckItem.ExtraStart + rel;
                DeckItem space = removeItem(DeckItem.ExtraEnd);
                addItem(pos, new DeckItem(cardInfo, DeckItemType.ExtraCard));
                addItem(DeckItem.SideEnd, space);
                // 额外区末尾空格被移除：主区末尾空格前移顶替额外区末尾，副区末尾新增空格
                notifyItemRemoved(DeckItem.ExtraEnd);
                notifyItemInserted(pos);
                notifyItemChanged(DeckItem.MainEnd);
                if (pos + 1 <= DeckItem.ExtraEnd - 1) {
                    notifyItemRangeChanged(pos + 1, DeckItem.ExtraEnd - 1 - pos);
                }
                notifyItemChanged(DeckItem.SideEnd);
                notifyItemChanged(DeckItem.ExtraLabel);
                notifyItemChanged(DeckItem.SideLabel);
                return DragInsertResult.SUCCESS;
            }
            case SideCard: {
                int count = getSideCount();
                if (count >= Constants.DECK_SIDE_MAX) {
                    return DragInsertResult.SIDE_FULL;
                }
                int rel = insertPos - DeckItem.SideStart;
                if (rel < 0) rel = 0;
                if (rel > count) rel = count;
                int pos = DeckItem.SideStart + rel;
                DeckItem space = removeItem(DeckItem.SideEnd);
                addItem(pos, new DeckItem(cardInfo, DeckItemType.SideCard));
                // 副卡组后方仅剩尾部占位项，移除+插入已覆盖全部变化
                notifyItemRemoved(DeckItem.SideEnd);
                notifyItemInserted(pos);
                notifyItemChanged(DeckItem.SideLabel);
                return DragInsertResult.SUCCESS;
            }
            default:
                return DragInsertResult.FAILED;
        }
    }

    public void setDeck(DeckInfo deckinfo, boolean isPack) {
        mDeckInfo = deckinfo;
        if (deckinfo != null) {
            loadData(deckinfo, isPack);
        }
        mDeckMd5 = DeckItemUtils.makeMd5(mItems);
    }

    public DeckInfo read(CardLoader cardLoader, File file, LimitList limitList) {
        if (limitList != null) {
            setLimitList(limitList);
        }
        return DeckLoader.readDeck(cardLoader, file);
    }

    public boolean save(File file, boolean withoutId) {
        //保存了，记录状态
        mDeckMd5 = DeckItemUtils.makeMd5(mItems);
        return DeckItemUtils.save(mItems, withoutId ? null : deckId, withoutId ? null : userId, file);
    }

    public Deck toDeck(File file) {
        return DeckItemUtils.toDeck(mItems, file);
    }

    protected void loadData(DeckInfo deckInfo, boolean isPack) {
        // 卡组内容整体替新（加载/ undo/redo / 卡包展示），旧勾选下标已全部失去原义：退出批量模式
        mBatchMode = false;
        mBatchChecked.clear();
        deckId = null;
        userId = null;
        mCount.clear();
        mMainCount = 0;
        mExtraCount = 0;
        mSideCount = 0;
        mMainMonsterCount = 0;
        mMainSpellCount = 0;
        mMainTrapCount = 0;
        mExtraFusionCount = 0;
        mExtraXyzCount = 0;
        mExtraSynchroCount = 0;
        mExtraLinkCount = 0;
        mSideMonsterCount = 0;
        mSideSpellCount = 0;
        mSideTrapCount = 0;
        mItems.clear();
        DeckItemUtils.makeItems(deckInfo, isPack, this);
    }

    public boolean isChanged() {
        String md5 = DeckItemUtils.makeMd5(mItems);
        //Log.d("kk", mDeckMd5 + "/" + md5);
        return !TextUtils.equals(mDeckMd5, md5);
    }

    public void notifyItemChanged(Card card) {
        for (int i = 0; i < getItemCount(); i++) {
            DeckItem item = getItem(i);
            Card c = item.getCardInfo();
            if (c != null && c.Code == card.Code) {
                notifyItemChanged(i);
            }
        }
    }

    @Override
    public DeckViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflating a layout from XML and returning the holder
        View view = mLayoutInflater.inflate(R.layout.item_deck_card, parent, false);
        return new DeckViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DeckViewHolder holder, int position) {
        //Populating data into the item through holder
        //根据data的内容调整view的样式

        DeckItem item = mItems.get(position);

        // 确保每次绑定时都重置视图状态
        holder.clear(); // 清理旧图片和状态

        if (item != null && item.getCardInfo() != null) {
            // 绑定新数据
            holder.bind(item, imageLoader);
            holder.showImage();
        } else {
            // 显示空状态
            holder.showEmpty();
        }

        holder.setItemType(item.getType());
        if (item.getType() == DeckItemType.MainLabel || item.getType() == DeckItemType.SideLabel
                || item.getType() == DeckItemType.ExtraLabel) {//处理分隔栏的item view
            if (item.getType() == DeckItemType.MainLabel) {
                holder.setText(getMainString());
            } else if (item.getType() == DeckItemType.SideLabel) {
                holder.setText(getSideString());
            } else if (item.getType() == DeckItemType.ExtraLabel) {
                holder.setText(getExtraString());
            }
        } else {//处理展示卡图的item view
            /* 动态计算控件的高度，优先根据卡图的实际大小计算高度，如果结果<=0，则根据recyclerView的宽度计算控件高度 */
            if (mHeight <= 0) {
                if (holder.cardImage.getMeasuredWidth() > 0) {
                    mWidth = holder.cardImage.getMeasuredWidth();
                    if (mWidth >= 0) {
                        mHeight = scaleHeight(mWidth);
                    }
                }
                if (mHeight <= 0) {
                    makeHeight();
                }
//                Log.i("kk", "w=" + mWidth + ",h=" + mHeight);
            }
//            holder.cardImage.setLayoutParams(new RelativeLayout.LayoutParams(holder.cardImage.getMeasuredWidth(), mHeight));
            //显示卡片
            holder.showImage();
            holder.setSize(mHeight);
            if (item.getType() == DeckItemType.Space) {//占位，不显示卡图
                holder.setCardType(0);
                holder.showEmpty();
            } else {//非占位，显示卡图

                Card cardInfo = item.getCardInfo();
//                holder.cardImage.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        if(cardInfo!=null)
//                            Toast.makeText(context,"不为空点击"+cardInfo.Name,Toast.LENGTH_SHORT).show();
//                        else
//                            Toast.makeText(context,"空点击"+position,Toast.LENGTH_SHORT).show();
//                    }
//                });//将cardInfo在LimitList中检查，根据结果（限制、准限制、禁止）对应显示卡图
                if (cardInfo != null) {
                    holder.setCardType(cardInfo.Type);
                    if (mImageTop == null) {
                        mImageTop = new ImageTop(context);
                    }
                    if (mLimitList != null) {
                        if (mLimitList.check(cardInfo, LimitType.Forbidden)) {
                            holder.setRightImage(mImageTop.forbidden);
                            holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                        } else if (mLimitList.check(cardInfo, LimitType.Limit)) {
                            holder.setRightImage(mImageTop.limit);
                            holder.setLimitText("1", YGOUtil.c(R.color.yellow), 10);
                        } else if (mLimitList.check(cardInfo, LimitType.SemiLimit)) {
                            holder.setRightImage(mImageTop.semiLimit);
                            holder.setLimitText("2", YGOUtil.c(R.color.yellow), 10);
                        } else if (mLimitList.check(cardInfo, LimitType.GeneSys)) {
                            // 统一使用 getGeneSysCredit 取值：与 check(GeneSys) 同口径，既命中“宽松同名折叠键”，
                            // 也能在“卡片 code 在表中、但 alias 不在表中(|alias-code|<=20)”时回退到 code 的分值
                            Integer creditValue = mLimitList.getGeneSysCredit(cardInfo);
                            if (creditValue != null) {
                                holder.setRightImage(mImageTop.credits);
                                holder.setLimitText(creditValue.toString(), YGOUtil.c(R.color.holo_blue_bright), (creditValue > -10 && creditValue < 100) ? 8 : 6);
                            } else {
                                holder.setRightImage(null);
                                holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                            }
                        } else {
                            holder.setRightImage(null);
                            holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                        }
                    } else {
                        holder.setRightImage(null);
                        holder.setLimitText("", YGOUtil.c(R.color.white), 10);
                    }
//                    holder.useDefault();
                    imageLoader.bindImage(holder.cardImage, cardInfo, ImageLoader.Type.small);
                } else {
                    holder.setCardType(0);
                    holder.setRightImage(null);
                    holder.useDefault(imageLoader, mWidth, mHeight);
                }
            }
        }
        // 批量勾选打勾图标最后统一施加：上面任何分支（卡图/占位/分隔栏）都可能已重置过它，
        // holder 复用时必须以当前勾选集为准
        holder.setBatchChecked(mBatchMode && item.getCardInfo() != null
                && mBatchChecked.contains(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }
}

package cn.garymb.ygomobile.render.card;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.game.deck.CardSearcherManager;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.EffectActivatableHints;
import cn.garymb.ygomobile.game.gamefield.EffectActivationLedger;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.dialogs.duel.KeywordCardListDialog;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/**
 * 卡片详情绑定器（自 CardDetailPanel 拆分，逻辑零改）：
 * 卡图/卡名/字段/属性/等级/描述的视图绑定与显示状态（currentCardCode/coverBitmap）归本类管理，
 * 详情控件由 CardDetailPanel.bindViews() 经 attachViews 注入；面板留显隐协调与按钮域。
 */
class CardInfoBinder {

    private ImageLoader imageLoader;

    private LinearLayout layout;
    private LinearLayout panelRoot;
    private ImageView ivCardImage;
    private TextView tvCardName, tvCardSetname, tvCardAttr, tvCardLevel, tvCardDesc;
    private ScrollView svCardDesc;

    private int currentCardCode = -1;
    private Bitmap coverBitmap;
    private StringManager mStringManager = DataManager.get().getStringManager();

    // 关键词卡片列表弹窗支撑（详情描述「」/""高亮点击后在 layout_game_right 最左侧停靠显示命中列表）：
    // context/anchor 由 attachViews 注入，currentDisplayCard 用于 queryable 判定（唯一命中即自身则不可点）
    private Context context;
    private View anchor;
    private Card currentDisplayCard;
    /** 最近一次经 ClientCard 路径展示的场卡引用（纯卡表展示/面板收起置空）：发动账本变化
     *  的及时刷新沿此引用重算标记（半场权威，无需玩家重新点卡，用户规格1） */
    private ClientCard lastShownClientCard;
    /** 当前展示卡的半场（0=我方 1=对方 -1=半场未知按双方合并展示）：决定账本过滤与绿/红勾 */
    private int displayedSide = -1;
    /** 正在展示的卡详绑定器：引擎派发线程 notifyLedgerChanged 的回调入口（决斗面板为唯一活动实例） */
    private static volatile CardInfoBinder sActiveBinder;
    private KeywordCardListDialog keywordDialog;
    /**
     * 卡组编辑模式下的关键词导航器：非空时点击高亮词不再弹 {@link KeywordCardListDialog}，
     * 而是把关键词交由此处理器（由 {@code DeckEditorManager} 注入为填入卡组搜索框并搜索，
     * 结果显示在卡组编辑搜索结果列表上）。进入卡组编辑时注入、退出时置空恢复决斗态弹窗行为。
     */
    private KeywordNavigator deckKeywordNavigator;

    /**
     * 卡组编辑模式下的关联卡片列表展示器（{@code DeckEditorManager} 进入时注入、退出时置空）：
     * 非空即视为处于卡组编辑态，点击关联卡片按钮不再弹 {@link KeywordCardListDialog}，
     * 而是把关联卡片列表直接呈现到卡组编辑的搜索结果列表上。
     */
    private CardListHandler deckCardListHandler;

    /** 关联卡片入口（横竖屏布局同 ID btn_related_cards，ic_related 图标 ImageButton）：
     *  由 CardDetailPanel 注入，仅当前卡存在关联卡时显示；类型用 View 兼容不同布局容器 */
    private View btnRelatedCards;
    /** 当前卡描述里解析出的「」/""关键词，供 relatedCards 匹配（对齐 CardDetail.spanStringList） */
    private final List<String> spanStringList = new ArrayList<>();
    /** 当前卡已计算的关联卡列表：随卡详绑定刷新，点击按钮时直接复用避免再次全表扫描 */
    private List<Card> lastRelatedCards = Collections.emptyList();

    /** 卡详绑定代际：每次绑定/重置自增，后台任务完成时代际已变即丢弃结果——
     *  连续快速点卡时旧任务不再把过期的高亮/关联卡状态覆盖到新卡上 */
    private volatile int bindGen = 0;

    // 异步线程池用于关联卡片和高亮词存在性检查
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 关键词点击导航回调（卡组编辑模式复用卡详高亮，点击直接作用到卡组搜索列表） */
    public interface KeywordNavigator {
        void onKeywordClick(String keyword);
    }

    /** 关联卡片列表展示回调（卡组编辑模式复用卡组搜索结果列表展示关联卡，不弹关键词列表弹窗） */
    public interface CardListHandler {
        void onShowCardList(List<Card> cards);
    }

    /** 由 CardDetailPanel 转发 DeckEditorManager 的注入：非空即视为处于卡组编辑模式 */
    public void setDeckKeywordNavigator(KeywordNavigator navigator) {
        this.deckKeywordNavigator = navigator;
    }

    /** 由 CardDetailPanel 转发 DeckEditorManager 的注入：非空即卡组编辑态，关联卡直接进搜索结果列表 */
    public void setDeckCardListHandler(CardListHandler handler) {
        this.deckCardListHandler = handler;
    }

    /** 由 CardDetailPanel.bindViews 注入关联卡片入口引用（用于随当前卡刷新显隐） */
    void setRelatedButton(View button) {
        this.btnRelatedCards = button;
    }

    /** bindViews() 重绑新视图树后注入详情控件（旋转重建场景）；anchor 为关键词卡片列表停靠的 layout_game_right */
    void attachViews(LinearLayout layout, LinearLayout panelRoot, ImageView ivCardImage,
                     TextView tvCardName, TextView tvCardSetname, TextView tvCardAttr,
                     TextView tvCardLevel, TextView tvCardDesc, ScrollView svCardDesc, View anchor) {
        this.layout = layout;
        this.panelRoot = panelRoot;
        this.ivCardImage = ivCardImage;
        this.tvCardName = tvCardName;
        this.tvCardSetname = tvCardSetname;
        this.tvCardAttr = tvCardAttr;
        this.tvCardLevel = tvCardLevel;
        this.tvCardDesc = tvCardDesc;
        this.svCardDesc = svCardDesc;
        this.anchor = anchor;
        this.context = layout != null ? layout.getContext()
                : (panelRoot != null ? panelRoot.getContext() : null);
        // 关键词卡片列表弹窗已改用 DraggablePopupHelper：旋转重排/重居中由其 relayoutActivePopupsForOrientation
        //（YGOProActivity 旋转重建末尾统一调用）按 dialogId 处理，本处无需再手动重锚。
    }

    void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    int getCurrentCardCode() {
        return currentCardCode;
    }

    void setCurrentCardCode(int code) {
        currentCardCode = code;
    }

    /** 面板隐藏时由 CardDetailPanel.hide() 调用：收起关键词卡片列表弹窗并清理当前卡引用 */
    void dismissKeywordListDialog() {
        dismissKeywordDialog();
        currentDisplayCard = null;
        lastShownClientCard = null;
        displayedSide = -1;
        resetRelatedState();
    }

    void showCardInfo(ClientCard card) {
        // 移除 code<=0 检查：对于 CardSelectDialog 中的已知 code 的暗卡（里侧怪兽、卡组表侧等），
        // 只要能读取到卡片图案就应该显示详细信息，而不是当作未知卡处理
        if (card == null) return;
        showCard(card);
    }

    void showDefault() {
        currentCardCode = -1;
        currentDisplayCard = null;
        lastShownClientCard = null;
        displayedSide = -1;
        resetRelatedState();
        // 不在此收起关键词卡片列表弹窗：旋转重建流程会先经 onGameUIShown→showDefault 再回显，
        // 若此处 dismiss 会使命中列表在横竖屏切换时消失（用户要求切换方向不隐藏）。
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);
        if (ivCardImage != null) {
            Bitmap cover = getCoverBitmap();
            if (cover != null) {
                ivCardImage.setImageBitmap(cover);
            } else {
                ivCardImage.setImageResource(R.drawable.unknown);
            }
        }
        if (tvCardName != null) {
            tvCardName.setText("");
        }
        if (tvCardSetname != null) {
            tvCardSetname.setText("");
            tvCardSetname.setVisibility(View.GONE);
        }
        if (tvCardAttr != null) {
            tvCardAttr.setText("");
        }
        if (tvCardLevel != null) {
            tvCardLevel.setText("");
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText("");
        }
    }

    void showCard(ClientCard clientCard) {
        // 卡码经 displayCode 取：场上里侧卡一旦被公开（POS_REVEAL 解遮蔽 / 泡影类 POS_CHANGE 携码），
        // 后续遮蔽零码查询即便洗回 code 也留有 chain_code 备份，仍按公开码显示详情
        //（对齐 event_handler.cpp L1673 hover 只判 mcard->code 即 ShowCardInfo 的语义）
        // code<=0 视为未知/暗卡：对齐 event_handler.cpp L1130-1133 ClearCardInfo 显示卡背，
        // 而不是走 getCard(0) 失败后误报「???」；unknown 仅保留给 code>0 但卡表查无此卡
        if (clientCard == null) {
            showDefault();
            return;
        }
        int code = clientCard.displayCode();
        if (code <= 0) {
            showDefault();
            return;
        }

        Card cardData = DataManager.get().getCardManager().getCard(code);
        if (cardData == null) {
            showUnknownCard();
            return;
        }

        currentCardCode = code;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);

        bindCardImage(code);
        bindCardName(cardData, code);
        bindCardSetname(cardData);
        bindCardAttr(cardData);
        // 详情面板只显示卡表原始数据（对齐 game.cpp Game::ShowCardInfo(int code) 全部取 cd 原值），
        // 通讯修改后的等级/属性/种族/攻守/刻度一律不进详情，差异在长按标签中显示
        bindCardLevel(cardData);
        // 已发动效果标记（用户规格）：descHints（CHINT 持续提示，仅少数卡有）与决斗级账本
        // 按卡码的发动记录（MSG_CHAINING 登记）合并；半场权威：场卡/列表临时 ClientCard 的
        // controler 已是本地索引，账本按该半场过滤——我方发动绿勾、对手发动红勾，同名卡双方
        // 标记严格区分（用户规格2）；账本维度使手卡/墓地/额外/卡组/怪兽区/魔陷区
        //（含灵摆区/额外怪兽区）列表点卡重建的临时 ClientCard 也能命中
        lastShownClientCard = clientCard;
        displayedSide = clientCard.controler == 1 ? 1 : 0;
        sActiveBinder = this;
        List<Integer> ourGlow = Collections.emptyList();
        List<Integer> oppGlow = Collections.emptyList();
        if (clientCard != null) {
            // 主阶/战斗指令为全桌广播，对方轮的 SELECT 也收得到：登记簿已含对方半场条目，
            // 双半场各取一份，着色时我方绿、对方橙（用户规格）
            ourGlow = EffectActivatableHints.offsetsFor(code, cardData.Alias, 0);
            oppGlow = EffectActivatableHints.offsetsFor(code, cardData.Alias, 1);
        }
        bindCardDesc(cardData, collectEffectRefs(code, cardData, displayedSide, clientCard),
                ourGlow, oppGlow);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    void showCard(Card card) {
        if (card == null || card.Code <= 0) {
            showUnknownCard();
            return;
        }

        //卡图使用card.Code对应的文件名id（异画卡显示自己的卡图，不受RealCode影响）
        int imageCode = card.Code;
        currentCardCode = imageCode;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);

        bindCardImage(imageCode);
        bindCardNameByGameCode(card);
        bindCardSetname(card);
        bindCardAttr(card);
        bindCardLevel(card);
        // 卡表卡（关键词查卡/确认弹窗/日志/旋转恢复等）同样按卡码查决斗账本（需求2/4：
        // 旋转后 restoreAfterRebind 走本路径，标记须保留）：与最近展示的场卡同卡码时沿用其
        // 半场（旋转恢复不丢归属），否则双方合并展示（纯查卡无归属信息，我方发动段绿勾、
        // 对方发动段红勾）；卡组编辑态不查账本，避免上一局发动记录脏染卡组编辑器详情；
        // 决斗未开始/回放未发动时账本为空自然短路
        ClientCard shown = lastShownClientCard != null && lastShownClientCard.displayCode() == card.Code
                ? lastShownClientCard : null;
        displayedSide = shown != null ? (shown.controler == 1 ? 1 : 0) : -1;
        sActiveBinder = this;
        // 可发动效果发光仅走 ClientCard 展示态路径（场卡/列表临时卡）：卡组编辑态与
        // 纯卡表展示（shown==null）不发光，防过期询问脏染与无归属误发光；双半场各取一份
        List<Integer> ourGlow = Collections.emptyList();
        List<Integer> oppGlow = Collections.emptyList();
        if (shown != null && deckCardListHandler == null) {
            ourGlow = EffectActivatableHints.offsetsFor(card.Code, card.Alias, 0);
            oppGlow = EffectActivatableHints.offsetsFor(card.Code, card.Alias, 1);
        }
        bindCardDesc(card, deckCardListHandler != null ? Collections.<EffectRef>emptyList()
                : collectEffectRefs(card.Code, card, displayedSide, shown), ourGlow, oppGlow);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    /**
     * 旋转重建后回显卡片详情（用户规格：横竖屏切换前若详情正在显示，切换后继续显示同一张卡，
     * 不重置为隐藏态）。在 bindViews() 重绑新视图树之后调用；本方法在 onGameUIShown()
     * 之后执行，而其内部的 showDefault() 已把 currentCardCode 清零，故卡码以重建前
     * 快照 savedCode 为准（<=0 时回退到实例字段）。
     *
     * @param wasShowingBeforeRebind 重建前详情栏是否可见（旧视图树上采集）
     * @param savedCode              重建前正在展示的卡码（无则 <=0）
     */
    void restoreAfterRebind(boolean wasShowingBeforeRebind, int savedCode) {
        if (!wasShowingBeforeRebind) return;
        int code = savedCode > 0 ? savedCode : currentCardCode;
        if (code > 0) {
            Card cardData = DataManager.get().getCardManager().getCard(code);
            if (cardData != null) {
                showCard(cardData);
            } else {
                showUnknownCard();
            }
        } else {
            showDefault();
        }
    }

    /** 释放资源 */
    void onDestroy() {
        executorService.shutdown();
    }

    void showUnknownCard() {
        currentCardCode = -1;
        currentDisplayCard = null;
        lastShownClientCard = null;
        displayedSide = -1;
        resetRelatedState();
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);
        if (ivCardImage != null) {
            ivCardImage.setImageResource(R.drawable.unknown);
        }
        if (tvCardName != null) {
            tvCardName.setText("???");
        }
        if (tvCardSetname != null) {
            tvCardSetname.setText("");
            tvCardSetname.setVisibility(View.GONE);
        }
        if (tvCardAttr != null) {
            tvCardAttr.setText("");
        }
        if (tvCardLevel != null) {
            tvCardLevel.setText("");
            tvCardLevel.setVisibility(View.GONE);
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText(R.string.tip_card_info_diff);
        }
        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    private void bindCardImage(int code) {
        if (imageLoader != null && ivCardImage != null) {
            imageLoader.bindImage(ivCardImage, code, ImageLoader.Type.origin);
        }
    }

    private void bindCardName(Card cardData, int code) {
        if (tvCardName == null) return;
        String name = cardData.Name;
        if (name == null || name.isEmpty()) name = "Unknown Card";
        tvCardName.setText(name + "[" + code + "]");
    }

    //卡名根据getGameCode()判断显示（规则同名卡显示本家卡名），卡图仍使用card.Code
    private void bindCardNameByGameCode(Card cardData) {
        if (tvCardName == null) return;
        int gameCode = cardData.getGameCode();
        Card gameCard = DataManager.get().getCardManager().getCard(gameCode);
        String name = (gameCard != null && gameCard.Name != null && !gameCard.Name.isEmpty())
                ? gameCard.Name : cardData.Name;
        if (name == null || name.isEmpty()) name = "Unknown Card";
        tvCardName.setText(name + "[" + gameCode + "]");
    }

    private void bindCardSetname(Card cardData) {
        if (tvCardSetname == null) return;
        mStringManager = DataManager.get().getStringManager();
        long[] setCodes = cardData.getSetCode();
        StringBuilder sb = new StringBuilder();
        boolean hasSet = false;
        for (long sc : setCodes) {
            if (sc == 0) continue;
            if (hasSet) sb.append("|");
            sb.append(mStringManager.getSetName(sc));
            hasSet = true;
        }
        if (hasSet) {
            tvCardSetname.setText("字段：" + sb);
            tvCardSetname.setVisibility(View.VISIBLE);
        } else {
            tvCardSetname.setVisibility(View.GONE);
        }
    }

    private void bindCardAttr(Card cardData) {
        if (tvCardAttr == null) return;
        mStringManager = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder();

        String typeStr = CardUtils.getAllTypeString(cardData, mStringManager).replace("/", "|");
        sb.append("[").append(typeStr).append("]");

        if (cardData.isType(CardType.Monster)) {
            String raceStr = mStringManager.getRaceString(cardData.Race);
            String attrStr = mStringManager.getAttributeString(cardData.Attribute);
            sb.append(" ").append(raceStr).append("/").append(attrStr);
        }

        tvCardAttr.setText(sb.toString());
    }

    private void bindCardLevel(Card cardData) {
        if (tvCardLevel == null) return;

        if (cardData.isType(CardType.Spell) || cardData.isType(CardType.Trap)) {
            tvCardLevel.setText("");
            tvCardLevel.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (cardData.isLink()) {
            sb.append("LINK-").append(cardData.getLinkNumber());
        } else if (cardData.isType(CardType.Xyz)) {
            sb.append("☆").append(cardData.getStar());
        } else if (cardData.isType(CardType.Monster)) {
            sb.append("★").append(cardData.getStar());
        }

        if (cardData.isType(CardType.Monster)) {
            int atk = cardData.Attack;
            int def = cardData.Defense;
            String atkStr = atk < 0 ? "?" : String.valueOf(atk);
            String defStr = cardData.isLink() ? "-" : (def < 0 ? "?" : String.valueOf(def));
            if (sb.length() > 0) sb.append("  ");
            sb.append(atkStr).append("/").append(defStr);
        }

        if (cardData.LeftScale > 0 || cardData.RightScale > 0) {
            int lsc = cardData.LeftScale;
            int rsc = cardData.RightScale;
            if (sb.length() > 0) sb.append("  ");
            sb.append("灵摆 ").append(lsc).append("/").append(rsc);
        }

        tvCardLevel.setText(sb.toString());
        tvCardLevel.setVisibility(View.VISIBLE);
    }

    private void bindCardDesc(Card cardData, List<EffectRef> effectRefs,
                              List<Integer> ourGlowOffsets, List<Integer> oppGlowOffsets) {
        if (tvCardDesc == null) return;
        // 记录当前展示的卡，供关键词 queryable 判定（唯一命中即当前卡自身则视为不可查）
        currentDisplayCard = cardData;
        final int gen = ++bindGen;
        spanStringList.clear();
        lastRelatedCards = Collections.emptyList();
        updateRelatedButtonVisibility();
        final String desc = cardData.Desc == null ? "" : cardData.Desc;
        // 发动引用集在 UI 线程快照拷贝：账本由通讯/派发线程写入（内部并发安全集合），
        // 后台遍历快照避免与写入交叉；纯卡表展示传空列表则整段标记逻辑短路
        final List<EffectRef> usedIds = effectRefs == null || effectRefs.isEmpty()
                ? Collections.<EffectRef>emptyList() : new ArrayList<>(effectRefs);
        // 可发动效果段落文档序快照（同不受后台任务期间命令清空影响，空集即不发光）；
        // 我方/对方两份分开传递，着色时我方绿、对方橙（用户规格）
        final List<Integer> ourGlow = ourGlowOffsets == null || ourGlowOffsets.isEmpty()
                ? Collections.<Integer>emptyList() : new ArrayList<>(ourGlowOffsets);
        final List<Integer> oppGlow = oppGlowOffsets == null || oppGlowOffsets.isEmpty()
                ? Collections.<Integer>emptyList() : new ArrayList<>(oppGlowOffsets);
        // 纯文本立即显示（描述先可读，不等待任何扫描）
        tvCardDesc.setText(desc);
        // 关键词解析 + 高亮 spannable 构建 + queryable 判定（缓存未命中为全卡表扫描）+
        // 关联卡片计算一律在后台线程执行：此前在主线程逐关键词做全表扫描，连续点卡时
        // 主线程被反复阻塞（表现为点卡无响应“卡死”），且与后台线程并发读写普通 HashMap
        // 缓存可能死循环。结果按代际回主线程：绑定过期的任务直接丢弃。
        final Card card = cardData;
        executorService.execute(() -> {
            if (gen != bindGen) return;
            List<String> keywords = new ArrayList<>();
            // 关键词高亮构建后再叠加已发动效果标记（命中行灰+绿勾，用户规格）
            final CharSequence marked = markUsedEffects(buildStyledDesc(desc, card, keywords), desc,
                    usedIds, ourGlow, oppGlow);
            List<Card> related = relatedCards(card, keywords);
            mainHandler.post(() -> {
                if (gen != bindGen || tvCardDesc == null) return;
                tvCardDesc.setText(marked);
                tvCardDesc.setMovementMethod(LinkMovementMethod.getInstance());
                spanStringList.clear();
                spanStringList.addAll(keywords);
                lastRelatedCards = related;
                updateRelatedButtonVisibility();
            });
        });
    }

    /** 已发动效果行的显示色（用户规格：用过的效果行改灰色，未用行保持原色）；
     *  grayDark(#A7A7A7) 比正文白字略暗、比旧 chat_color_gray 亮，置灰后文本仍易读 */
    private static final int COLOR_USED_EFFECT = YGOUtil.c(R.color.grayDark);
    /** 行首插入的打勾前缀：首字符（不换行空格）由 ImageSpan 占位绘制绿圆对勾图标，次字符留间隙 */
    private static final String USED_MARK_PREFIX = "\u00A0 ";

    /**
     * 在关键词高亮结果上叠加「已发动效果」标记（上游 cdb/script 已整改为文档序：效果发动
     * 经 MSG_CHAINING 下发的描述串=code*16+段落文档序，低 4 位序号即「序号文本数组」下标）。
     * refs 为发动引用集（descHints 合成记录 + 账本 Activation），卡码型 id 直接按
     * offset==段落文档序命中，不再做 str 文本匹配（cdb str 保留原文本、不含段落全文）：
     * <ol>
     * <li>序号文本数组原则（用户规格2）：卡面带序号段落共 n 个、灵摆框段 p 个，文档序
     * 跨框全局连续——下标 0..p-1 为灵摆框段、p..n-1 为怪兽框段，offset 直指对应段。</li>
     * <li>分框分割原则（用户规格3，灵摆怪兽卡）：灵摆框段从第 1 条含「【」或「[」行的
     * 下一行开始，到第 2 条含「【」或「[」行的上一行结束（第 2 条标题行上方出现分隔线时
     * 以分隔线为界再往上一行）；第 2 条标题行及其下方不再属灵摆框，其下一行起为怪兽效果段。
     * 标题行与分隔线自身不归入任何段（邻段置灰不连带），段也不得越过第 2 条标题行上方
     * 的分隔线延续——怪兽框内仅序号行开头的文本参与段落划分，含①②③序号但非序号
     * 开头的行不会因灵摆段渗染被误标。</li>
     * </ol>
     * 段落 = 段首效果标记行（换行符后的第一个 ①②③ 圆圈序号行；全文无圆圈序号的传统卡才认 ● 项目符行）
     * 起、到下一标记行前止，其间的续行/●行等所有行归该段整段置灰。打勾图标只插段首。
     * 段落自末向首逆序处理：插入前缀只影响其后偏移，未处理段的原始
     * 行下标仍有效。在后台线程执行，不触碰视图。
     */
    private CharSequence markUsedEffects(CharSequence styled, String desc, List<EffectRef> refs,
                                         List<Integer> ourGlowOffsets, List<Integer> oppGlowOffsets) {
        boolean hasGlow = (ourGlowOffsets != null && !ourGlowOffsets.isEmpty())
                || (oppGlowOffsets != null && !oppGlowOffsets.isEmpty());
        if ((refs == null || refs.isEmpty()) && !hasGlow || desc.isEmpty()) return styled;
        // 拆行：[start, end) 不含换行符
        List<int[]> lines = new ArrayList<>();
        int lineStart = 0;
        for (int i = 0; i < desc.length(); i++) {
            if (desc.charAt(i) == '\n') {
                lines.add(new int[]{lineStart, i});
                lineStart = i + 1;
            }
        }
        lines.add(new int[]{lineStart, desc.length()});
        int lineCount = lines.size();
        // 分段规则（用户规格）：段 = 段首圆圈序号行（换行符后第一个序号）起、到下一序号行
        // 前一行止，其间的续行/●行/【】框标题行等所有行均归该段；全文无圆圈序号行的传统卡
        // 才按段首 ●/项目符行分段。先扫一遍判定有无圆圈序号行，有则项目符不再切段
        boolean hasCircled = false;
        for (int k = 0; k < lineCount && !hasCircled; k++) {
            hasCircled = leadingCircledIndex(desc.substring(lines.get(k)[0], lines.get(k)[1]).trim()) >= 0;
        }
        // 段落归属扫描（用户规格3·分框原则）：含「【」或「[」且非圆圈序号开头的行为框标题行——
        // 第1条起为灵摆框、第2条起为怪兽框；标题行与分隔线自身不归任何段（paraOf=-1，
        // 邻段被标记也不连带置灰；第2条标题行上方的分隔线天然成为灵摆框上界）。
        // 段不跨框延续：框标题行与第 2 条标题行上方的分隔线关闭当前段（para=-1，
        // 否则灵摆框最后一段会渗染到怪兽框内「含①②③序号但非序号开头」的文本行，被误标
        // 为已使用的灵摆效果）。
        // 圆圈/项目符行开启新段落，docIdx 全文连续即「序号文本数组」下标（灵摆 0..p-1、
        // 怪兽 p..n-1，用户规格2），meta 记录 {docIdx, anchor}：anchor=段首圆圈序号或项目符序位；
        // 首段之前的背景文本行（含“这个卡名的①②效果…”行）与标题行/分隔线 paraOf=-1 不参与映射
        int[] paraOf = new int[lineCount];
        List<int[]> meta = new ArrayList<>();
        int docIdx = -1, para = -1, bulletSeq = 0;
        int frameTitle = 0, firstTitleAt = -1, dividerPending = -1;
        for (int i = 0; i < lineCount; i++) {
            String raw = desc.substring(lines.get(i)[0], lines.get(i)[1]);
            String trimmed = raw.trim();
            if (isDividerLine(trimmed)) {
                paraOf[i] = -1;
                // 紧邻框标题行上方的分隔线同样是段落上界（用户规格3「以分隔线为界再往上一行」）
                if (para >= 0 && dividerPending < 0) dividerPending = i;
                continue;
            }
            int circled = leadingCircledIndex(trimmed);
            // 框标题行判据（用户规格3）：整行含 【 或 [ 即算，圆圈序号开头行除外（那是效果段）；
            // 第 1 条起灵摆框、第 2 条起怪兽框；第 2 条标题行关闭当前段（灵摆序号文本截止在
            // 其上一段），两条标题行都清空其上方待决分隔线
            if (circled < 0 && (trimmed.indexOf('【') >= 0 || trimmed.indexOf('[') >= 0)) {
                paraOf[i] = -1;
                frameTitle++;
                if (frameTitle == 1) firstTitleAt = i;
                if (frameTitle >= 2) para = -1;
                dividerPending = -1;
                continue;
            }
            // 第 2 条标题行上方的分隔线（灵摆框上界）：其下无上界时关闭当前段（同上规格）
            if (dividerPending >= 0) {
                if (frameTitle >= 2 && dividerPending > firstTitleAt) para = -1;
                dividerPending = -1;
            }
            int anchor;
            if (circled >= 0) {
                anchor = circled;
                bulletSeq = circled + 1;   // 其后若混排 ● 续编号，避开已占用的圆圈序号
            } else if (!hasCircled && !trimmed.isEmpty() && isBulletChar(trimmed.charAt(0))) {
                anchor = bulletSeq++;
            } else {
                anchor = -1;
            }
            if (anchor >= 0) {
                docIdx++;
                para = docIdx;
                meta.add(new int[]{docIdx, anchor});
            }
            paraOf[i] = para;
        }
        // 段落→段首圆圈序号：per-duel 条款判定时需要知道限制句中序号与本段的对应关系
        int[] paraAnchor = new int[docIdx + 1];
        for (int[] m : meta) paraAnchor[m[0]] = m[1];
        // 标记位域：0=未标 1=对方发动(红勾) 2=我方发动(绿勾)，同段落双方命中时绿优先
        int[] mark = new int[lineCount];
        final int nowTurn = EffectActivationLedger.getCurrentTurn();
        for (EffectRef ref : refs) {
            long uid = ref.id & 0xFFFFFFFFL;
            // offset 直指（用户规格：上游已整改为文档序编号）：系统字符串 id（≤MAX_STRING_ID）
            // 无段序含义不标；卡码型 id 低 4 位=段落文档序下标，命中 meta.docIdx 即标整段，
            // 越界（辅助/适用效果预留的 ≥n 让位槽）自然无命中。异画卡 Stringid 用原卡号，
            // uid>>>4 天然共享条目
            if (uid <= DataManager.MAX_STRING_ID) continue;
            int offset = (int) (uid & 0xf);
            List<int[]> hits = new ArrayList<>(1);
            for (int[] m : meta) {
                if (m[0] == offset) {
                    for (int i = 0; i < lineCount; i++) {
                        if (paraOf[i] == offset) {
                            hits.add(new int[]{offset, i});
                            break;
                        }
                    }
                    break;
                }
            }
            if (hits.isEmpty()) continue;
            // 回合门控（用户规格3）：1回合1次类标记随回合轮转过期（MSG_WIN/MSG_START 整账
            // 清空后只剩当回合）；引擎不下发限制类别（MSG_CHAINING 仅 code/loc/desc），
            // 故非当回合记录需命中段落块的「决斗中只能使用1次」类条款才持续显示
            if (ref.turn != nowTurn && !hitsArePerDuel(hits, paraOf, paraAnchor, lines, desc)) continue;
            int val = ref.side == 0 ? 2 : 1;
            for (int[] h : hits) markParagraph(mark, paraOf, h[1], val);
        }
        // 通讯可发动效果段落（绿/黄呼吸点与 conti_act 动画同源数据：MSG_SELECT_* 选项 desc
        // 低 4 位=段落文档序）：双半场各自记录归属（1=对方 2=我方），与已发动置灰互不影响
        int[] glowSide = new int[lineCount];
        markGlowParagraphs(glowSide, paraOf, lineCount, ourGlowOffsets, 2);
        markGlowParagraphs(glowSide, paraOf, lineCount, oppGlowOffsets, 1);
        SpannableStringBuilder sb = new SpannableStringBuilder(styled);
        boolean iconAvailable = context != null;
        // 标记行按段聚成连续块自下而上处理：同段才合并成块（背景文本两相邻标记行不并），
        // 块首插打勾前缀、整块铺灰
        int i = lineCount - 1;
        while (i >= 0) {
            // 可发动发光块先于 mark==0 短路判定（发光段不再写 mark，若放在短路之后将成为
            // 永不执行的死代码——此前「任何模式都不发光」的根因）：整段铺 TextGlowSpan
            // （setShadowLayer 外发光），不插前缀不置灰，与已发动块同款自下而上处理互不干扰；
            // 着色按归属半场：我方绿、对方橙，同段双方都可发动时绿优先（同已发动绿优先口径）
            int gi = paraOf[i];
            if (gi >= 0 && glowSide[i] != 0) {
                int g = i;
                while (g - 1 >= 0 && glowSide[g - 1] == glowSide[i] && paraOf[g - 1] == gi) g--;
                int gs = lines.get(g)[0], ge = lines.get(i)[1];
                if (gs < ge) {
                    sb.setSpan(new TextGlowSpan(glowSide[i] == 2
                                    ? COLOR_ACTIVATABLE_GLOW : COLOR_ACTIVATABLE_GLOW_OPP),
                            gs, ge,
                            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                i = g - 1;
                continue;
            }
            if (mark[i] == 0) { i--; continue; }
            int paraId = paraOf[i];
            int j = i;
            while (j - 1 >= 0 && mark[j - 1] != 0 && paraId >= 0 && paraOf[j - 1] == paraId) j--;
            int blockStart = lines.get(j)[0];
            int blockEnd = lines.get(i)[1];
            String prefix = iconAvailable ? USED_MARK_PREFIX : "";
            if (!prefix.isEmpty()) sb.insert(blockStart, prefix);
            int contentStart = blockStart + prefix.length();
            int contentEnd = blockEnd + prefix.length();
            if (contentStart < contentEnd) {
                if (iconAvailable) {
                    // 半场区分（用户规格2）：我方发动绿勾、对手发动红勾，同段双方都发动时绿优先
                    sb.setSpan(new ImageSpan(context,
                                    mark[j] == 1 ? R.drawable.ic_effect_used_check_red
                                            : R.drawable.ic_effect_used_check,
                                    ImageSpan.ALIGN_BASELINE),
                            blockStart, blockStart + 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                // 用户规格：已发动段整段铺灰，但高亮词（「」/"" 引用文本）的 ForegroundColorSpan
                // 维持原色（白/蓝）——收集范围内已有的前景 span，只在未被覆盖的空隙段铺灰；
                // ClickableSpan/下划线不受影响，高亮词仍可点击查卡
                List<int[]> keeps = new ArrayList<>();
                for (ForegroundColorSpan old : sb.getSpans(contentStart, contentEnd, ForegroundColorSpan.class)) {
                    int s = Math.max(contentStart, sb.getSpanStart(old));
                    int e = Math.min(contentEnd, sb.getSpanEnd(old));
                    if (s < e) keeps.add(new int[]{s, e});
                }
                keeps.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
                int pos = contentStart;
                for (int[] k : keeps) {
                    if (k[0] > pos) markGray(sb, pos, k[0]);
                    if (k[1] > pos) pos = k[1];
                }
                if (pos < contentEnd) markGray(sb, pos, contentEnd);
            }
            i = j - 1;
        }
        return sb;
    }

    /** 已发动段灰色前景 span（与高亮词前景 span 分段互斥，避免整段覆盖改写原色） */
    private static void markGray(SpannableStringBuilder sb, int start, int end) {
        sb.setSpan(new ForegroundColorSpan(COLOR_USED_EFFECT), start, end,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** 可发动效果段落发光颜色：与卡片角位呼吸绿点同源（colors.xml neon_green 荧光绿）；
     *  对方半场另用荧光橙（neon_orange），与己方可发动绿色区分归属（用户规格） */
    private static final int COLOR_ACTIVATABLE_GLOW = YGOUtil.c(R.color.neon_green);
    private static final int COLOR_ACTIVATABLE_GLOW_OPP = YGOUtil.c(R.color.neon_orange);

    /** 把某半场可发动的效果段落文档序整段记入发光归属数组（1=对方 2=我方，同段双发取
     *  较大值即绿优先）；只认序号段落（paraOf>=0），背景/限制行不参与 */
    private static void markGlowParagraphs(int[] glowSide, int[] paraOf, int lineCount,
                                           List<Integer> offsets, int val) {
        if (offsets == null || offsets.isEmpty()) return;
        for (int off : offsets) {
            for (int li = 0; li < lineCount; li++) {
                if (paraOf[li] == off) glowSide[li] = Math.max(glowSide[li], val);
            }
        }
    }

    /** 文字外发光 span：setShadowLayer 零偏移荧光色阴影环绕字形（与 FieldHudRenderer/SciFiRenderer
     *  发光文字同款机制），只改绘制状态不改文字尺寸，与已有前景/点击 span 叠加共存 */
    private static final class TextGlowSpan extends CharacterStyle {
        private final int color;
        TextGlowSpan(int color) { this.color = color; }
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setShadowLayer(10f, 0f, 0f, color);
        }
    }

    /** 命中的是效果段内任意行（含续行）则整段标记；背景文本（para -1）仅标记该行。
     *  值语义 1=对方(红勾) 2=我方(绿勾)，双方命中同段时取 max 即绿优先、不降级 */
    private static void markParagraph(int[] mark, int[] paraOf, int lineIdx, int val) {
        if (paraOf[lineIdx] < 0) {
            mark[lineIdx] = Math.max(mark[lineIdx], val);
            return;
        }
        for (int i = 0; i < mark.length; i++) {
            if (paraOf[i] == paraOf[lineIdx]) mark[i] = Math.max(mark[i], val);
        }
    }

    /** 「决斗中只能使用1次」类限制条款关键词（实测 assets 6 套 cdb）：中文「决斗中只能…」、
     * 繁中「決鬥中/決闘中」、日「デュエル中に１度しか…」、韩「듀얼( 중에) 1번밖에/1장밖에」、
     * 西「por Duel(o)」、英/葡「per Duel」；拉丁部分小写比较 */
    private static final String[] PER_DUEL_MARKS = {
            "决斗中", "決鬥中", "決闘中", "デュエル中", "듀얼", "per duel", "por duelo"
    };
    /** 与决斗词同现的「一度/しか」类限定词，排除仅卡名含决斗字样的行 */
    private static final String[] PER_DUEL_ONCE_WORDS = {"只能", "しか", "밖에", "만", "once", "vez"};

    /** 命中行所在段落块内是否存在 per-duel 条款（任一命中成立即跨回合持续保留）：
     *  段落块 = 该段全部行 + 段首上方紧邻的连续背景行（发动限制语句惯写在段首之前，
     *  如「这个卡名的②的效果在决斗中只能使用1次。」）；para=-1 背景命中时取该行上下紧邻背景行 */
    private static boolean hitsArePerDuel(List<int[]> hits, int[] paraOf, int[] paraAnchor, List<int[]> lines, String desc) {
        for (int[] h : hits) {
            int para = h[0];
            int lo, hi;
            if (para >= 0) {
                lo = Integer.MAX_VALUE;
                hi = -1;
                for (int k = 0; k < paraOf.length; k++) {
                    if (paraOf[k] == para) { if (k < lo) lo = k; if (k > hi) hi = k; }
                }
                if (hi < 0) continue;
                while (lo - 1 >= 0 && paraOf[lo - 1] < 0) lo--;
            } else {
                lo = h[1];
                hi = h[1];
                while (lo - 1 >= 0 && paraOf[lo - 1] < 0) lo--;
                while (hi + 1 < paraOf.length && paraOf[hi + 1] < 0) hi++;
            }
            char anchorCh = para >= 0 && para < paraAnchor.length
                    ? (char) (0x2460 + paraAnchor[para]) : 0;
            for (int k = lo; k <= hi; k++) {
                if (lineMarksPerDuel(desc.substring(lines.get(k)[0], lines.get(k)[1]), anchorCh)) return true;
            }
        }
        return false;
    }

    /** 单行是否携归属本段的 per-duel 条款：含决斗词+一度限定词的行按读句分隔符拆小句，
     *  含决斗词的小句无圆圈序号（全局限制句，如「这个卡名的效果在决斗中只能使用1次」）
     *  即命中；有小句带序号时须含本段序号（如「①的效果1回合只能使用1次，②的效果在决斗
     *  中只能使用1次」只对②持续） */
    private static boolean lineMarksPerDuel(String line, char anchorCh) {
        String low = line.toLowerCase(java.util.Locale.ROOT);
        boolean anyMark = false;
        for (String m : PER_DUEL_MARKS) {
            if (low.contains(m)) { anyMark = true; break; }
        }
        if (!anyMark) return false;
        boolean anyOnce = false;
        for (String w : PER_DUEL_ONCE_WORDS) {
            if (low.contains(w)) { anyOnce = true; break; }
        }
        if (!anyOnce) return false;
        for (String c : line.split("[，,、。．；;]")) {
            String cl = c.toLowerCase(java.util.Locale.ROOT);
            boolean hasMark = false;
            for (String m : PER_DUEL_MARKS) {
                if (cl.contains(m)) { hasMark = true; break; }
            }
            if (!hasMark) continue;
            boolean hasCircled = false;
            for (int k = 0; k < c.length(); k++) {
                char ch = c.charAt(k);
                if (ch >= 0x2460 && ch <= 0x2473) { hasCircled = true; break; }
            }
            if (!hasCircled) return true;
            if (anchorCh != 0 && c.indexOf(anchorCh) >= 0) return true;
        }
        return false;
    }

    /** 汇总标记所需的「已发动」段落引用候选：descHints（CHINT 持续提示，属当前展示卡半场，
     *  回合取当前恒可见）与账本按半场过滤的发动记录（side=-1 取双方）合并；每条记录以展示
     *  卡码重建完整字符串 id code*16+offset（供 getDesc 文本匹配与锚点映射） */
    private static List<EffectRef> collectEffectRefs(int code, Card cardData, int side, ClientCard cc) {
        List<EffectRef> refs = new ArrayList<>();
        if (cc != null && !cc.descHints.isEmpty()) {
            int cur = EffectActivationLedger.getCurrentTurn();
            // 半场以展示态 side 为准（视角切换后 cc.controler 对临时卡可能过期）；
            // side=-1（纯卡表无归属）时 descHints 无法判侧，退回卡片自身 controler
            int hintSide = side >= 0 ? side : (cc.controler == 1 ? 1 : 0);
            for (Integer id : cc.descHints.keySet()) {
                long uid = id & 0xFFFFFFFFL;
                if (uid <= 0x7fffffffL) refs.add(new EffectRef(id, hintSide, cur));
            }
        }
        long base = (long) code * 16;
        for (EffectActivationLedger.Activation a : EffectActivationLedger.activationsFor(code, cardData.Alias, side)) {
            long uid = base + a.offset;
            if (uid <= 0x7fffffffL) refs.add(new EffectRef((int) uid, a.side, a.turn));
        }
        return refs;
    }

    /** 标记候选引用：完整字符串 id + 发动半场（0=我方绿勾/1=对方红勾）+ 发动回合（回合门控基准） */
    private static final class EffectRef {
        final int id;
        final int side;
        final int turn;
        EffectRef(int id, int side, int turn) {
            this.id = id;
            this.side = side;
            this.turn = turn;
        }
    }

    /** 引擎派发线程在发动登记/回合轮转/开局胜负清零后回调（经 CardDetailPanel.refreshCardDetailMarks
     *  进入）：主线程重绑正在展示卡的描述标记（用户规格1：本回合发动无需重新点卡即时刷新） */
    static void notifyLedgerChanged() {
        final CardInfoBinder b = sActiveBinder;
        if (b == null) return;
        b.mainHandler.post(b::refreshShownMarks);
    }

    /** 效果发动专用的门控刷新（用户规格2：不同卡码的卡发动不打扰面板）：仅当发动卡与
     *  正在展示卡同码、或互为异画/同名 alias（两码共用效果段落与账本镜像）时才重绑 */
    static void notifyLedgerChanged(int activatedCode) {
        final CardInfoBinder b = sActiveBinder;
        if (b == null) return;
        b.mainHandler.post(() -> {
            if (b.affectsShownCard(activatedCode)) b.refreshShownMarks();
        });
    }

    /** 视角切换回调（观战/单人自动翻转/回放交换，用户规格3）：账本 side 语义已随视角
     *  整体对调（EffectActivationLedger.swapSides）、场卡 controler 已由 swapField 重算，
     *  展示卡半场取绑定态快照翻转后无条件重绑，绿/红勾随双方互换 */
    static void notifyViewpointSwapped() {
        final CardInfoBinder b = sActiveBinder;
        if (b == null) return;
        b.mainHandler.post(() -> {
            if (b.displayedSide >= 0) b.displayedSide = 1 - b.displayedSide;
            b.refreshShownMarks();
        });
    }

    /** 发动卡码是否与正在展示卡相关：同码直接相关；异画卡与本卡共用效果段落编号和
     *  账本 alias 镜像，任一方向 alias 指向展示卡码也相关 */
    private boolean affectsShownCard(int code) {
        int cur = currentCardCode;
        if (cur <= 0 || code <= 0) return false;
        if (code == cur) return true;
        Card shown = currentDisplayCard;
        if (shown != null && shown.Alias > 0 && shown.Alias == code) return true;
        Card other = DataManager.get().getCardManager().getCard(code);
        return other != null && other.Alias > 0 && other.Alias == cur;
    }

    /** 重绑当前展示卡描述段落标记（不动其它绑定项与滚动位置）；卡组编辑态/无展示态跳过。
     *  半场取 displayedSide 绑定态快照而非重读 cc.controler：临时卡（列表重建）不在场卡
     *  容器内、swapField 不会翻转其 controler，而 displayedSide 已随视角切换统一翻转，
     *  场卡/临时卡两条路语义一致（用户规格3） */
    private void refreshShownMarks() {
        if (deckCardListHandler != null || currentDisplayCard == null || currentCardCode <= 0) return;
        ClientCard cc = lastShownClientCard != null && lastShownClientCard.displayCode() == currentCardCode
                ? lastShownClientCard : null;
        bindCardDesc(currentDisplayCard,
                collectEffectRefs(currentCardCode, currentDisplayCard, displayedSide, cc),
                cc != null ? EffectActivatableHints.offsetsFor(currentCardCode, currentDisplayCard.Alias, 0)
                        : Collections.<Integer>emptyList(),
                cc != null ? EffectActivatableHints.offsetsFor(currentCardCode, currentDisplayCard.Alias, 1)
                        : Collections.<Integer>emptyList());
    }

    /**
     * 分隔线（用户规格：同框标题行一样不归入任何段落）：整行由横线/短横类字符组成，
     * 如英/葡「----------------------------------------」、西「━━━━━━━━…」；
     * 第2条框标题行上方的分隔线即灵摆框上界（用户规格3「再往上一行」，并关闭灵摆框末段
     * 防其渗染到怪兽框文本）
     */
    private static boolean isDividerLine(String trimmed) {
        if (trimmed.isEmpty()) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '-' || c == '—' || c == '–' || c == '─' || c == '━' || c == '═'
                    || c == '=' || c == '_' || c == '~' || c == '〜' || c == '·' || c == '*') {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isBulletChar(char c) {
        return c == '●' || c == '○' || c == '◦' || c == '・' || c == '-' || c == '–' || c == '—'
                || c == '*' || c == '▪' || c == '‣' || c == '•' || c == '◆' || c == '◇' || c == '■';
    }

    /**
     * 行首圆圈序号：①~⑳（U+2460~U+2473）映射为效果序位 0~19，非圆圈开头返回 -1。
     * 现代卡描述以段首圆圈序号标注各效果，该序位即脚本 aux.Stringid(code, n) 的 n，
     * 亦是 CHINT_DESC_ADD hint 值（code*16+n）的低 4 位，据此定位已发动效果段落。
     */
    private static int leadingCircledIndex(String trimmedLine) {
        if (trimmedLine.isEmpty()) return -1;
        char c = trimmedLine.charAt(0);
        return (c >= '\u2460' && c <= '\u2473') ? c - '\u2460' : -1;
    }

    /**
     * 将描述中「」与 "" 之间的文本渲染为高亮可点击文字（对齐 CardDetail.setHighlightTextWithClickableSpans）：
     * 命中卡集合可查（多于一个、或唯一命中非当前卡）→ holo_blue_bright 蓝色带下划线，点击在
     * layout_game_right 最左侧停靠弹出关键词卡片列表；不可查 → 白色带下划线，点击仅提示已到结尾。
     * <p>本方法在后台线程执行（queryable 判定含全卡表扫描），只读写入参局部对象与线程安全
     * 的关键词缓存，不触碰任何视图；解析出的关键词经 {@code outKeywords} 带回主线程。
     */
    private CharSequence buildStyledDesc(String text, Card card, List<String> outKeywords) {
        SpannableString spannableString = new SpannableString(text);
        QuoteType currentQuoteType = QuoteType.NONE;
        Stack<Integer> stack = new Stack<>();
        int start = -1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (currentQuoteType) {
                case NONE:
                    if (c == '「') {
                        currentQuoteType = QuoteType.ANGLE_QUOTE;
                        start = i + 1;
                        stack.push(i);
                    } else if (c == '"') {
                        currentQuoteType = QuoteType.DOUBLE_QUOTE;
                        start = i + 1;
                        stack.push(i);
                    }
                    break;
                case ANGLE_QUOTE:
                    if (c == '「') {
                        stack.push(i);
                    } else if (c == '」' && !stack.isEmpty()) {
                        stack.pop();
                        if (stack.isEmpty()) {
                            String quotedText = text.substring(start, i).trim();
                            applySpan(spannableString, start, i, quotedText,
                                    KeywordCardListDialog.isQueryable(quotedText, card)
                                            ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            outKeywords.add(quotedText);
                            currentQuoteType = QuoteType.NONE;
                        }
                    }
                    break;
                case DOUBLE_QUOTE:
                    if (c == '"' && !stack.isEmpty()) {
                        stack.pop();
                        if (stack.isEmpty()) {
                            String quotedText = text.substring(start, i).trim();
                            applySpan(spannableString, start, i, quotedText,
                                    KeywordCardListDialog.isQueryable(quotedText, card)
                                            ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            outKeywords.add(quotedText);
                            currentQuoteType = QuoteType.NONE;
                        } else {
                            stack.push(i);
                        }
                    }
                    break;
            }
        }
        return spannableString;
    }

    /** 为一段引用文本着色并挂点击：蓝色→点击弹出关键词卡片列表，白色→点击提示已到结尾 */
    private void applySpan(SpannableString spannableString, int start, int end, String keyword, int color) {
        spannableString.setSpan(new ForegroundColorSpan(color), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                if (color != Color.WHITE) {
                    openKeywordList(keyword);
                } else if (context != null) {
                    YGOUtil.showTextToast(context.getString(R.string.searchresult) + context.getString(R.string.already_end));
                }
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setUnderlineText(true);
            }
        }, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** 在 layout_game_right 最左侧停靠显示命中该关键词的卡片纵向列表（复用卡组编辑搜索结果同款 adapter） */
    private void openKeywordList(String keyword) {
        // 卡组编辑模式：点高亮词直接作用到卡组搜索列表（填入检索词并搜索），不弹关键词卡片列表弹窗
        if (deckKeywordNavigator != null) {
            deckKeywordNavigator.onKeywordClick(keyword);
            return;
        }
        if (context == null || anchor == null) return;
        List<Card> cards = KeywordCardListDialog.queryCardsByKeyword(keyword);
        // 排序与卡组编辑搜索结果同源：怪兽(通常→效果→仪式→融合→同调→超量→连接)→魔法→陷阱，
        // 复用 CardSearcherManager 默认比较器，使玩家对两处排序逻辑有一致的亲和力
        Collections.sort(cards, CardSearcherManager.defaultSearchComparator());
        if (keywordDialog != null) keywordDialog.dismiss();
        // 条目点击刷新左侧详情面板（this::showCard），弹窗保持打开以便继续链式点关键词
        keywordDialog = new KeywordCardListDialog(context, imageLoader, anchor, cards, keyword, this::showCard);
        keywordDialog.show();
    }

    /** 关闭关键词卡片列表弹窗（切换/隐藏卡详时调用，避免残留遮挡） */
    private void dismissKeywordDialog() {
        if (keywordDialog != null) {
            keywordDialog.dismiss();
            keywordDialog = null;
        }
    }

    /**
     * 同步版本：用于不需要异步处理的场景（如旋转重建时）
     */
    private void checkRelatedCardsSync(Card cardData) {
        lastRelatedCards = relatedCards(cardData);
        updateRelatedButtonVisibility();
    }

    // === 关联卡片（参考 CardDetail.relatedCards / relatable / btn_related） ===

    /** 清理关联卡状态并隐藏关联卡片按钮（无当前卡/默认态/未知卡时） */
    private void resetRelatedState() {
        // 代际自增：作废在途的后台高亮/关联卡任务，防止过期结果覆盖默认态
        bindGen++;
        spanStringList.clear();
        lastRelatedCards = Collections.emptyList();
        updateRelatedButtonVisibility();
    }

    /** 仅当前展示卡存在关联卡时显示按钮；无当前卡/未知卡/无关联卡时 INVISIBLE（用户规格：
     *  不再 GONE——按钮居于卡名/属性区 FrameLayout 右侧不影响测高，但部分布局变体下显隐
     *  切换仍会牵动下方描述区高度，恒定占位可避免逐卡跳动） */
    private void updateRelatedButtonVisibility() {
        if (btnRelatedCards == null) return;
        boolean show = currentDisplayCard != null && !lastRelatedCards.isEmpty();
        btnRelatedCards.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    /** 关联卡片按钮点击入口：按当前卡聚合的关联卡列表分流展示（卡组编辑态→搜索结果列表，决斗态→关键词列表弹窗） */
    void showRelatedCards() {
        if (currentDisplayCard == null) return;
        if (lastRelatedCards.isEmpty()) {
            // 无关联卡：与描述里不可查高亮词点击保持一致的提示，不硬编码文案
            if (context != null) {
                YGOUtil.showTextToast(context.getString(R.string.searchresult)
                        + context.getString(R.string.already_end));
            }
            return;
        }
        if (deckCardListHandler != null) {
            // 卡组编辑态：关联卡列表直接呈现到卡组搜索结果列表，不另弹弹窗
            deckCardListHandler.onShowCardList(new ArrayList<>(lastRelatedCards));
            return;
        }
        if (context == null || anchor == null) return;
        List<Card> cards = new ArrayList<>(lastRelatedCards);
        // 排序与卡组编辑搜索结果同源，使两处观感一致
        Collections.sort(cards, CardSearcherManager.defaultSearchComparator());
        if (keywordDialog != null) keywordDialog.dismiss();
        // 条目点击刷新左侧详情面板（this::showCard），弹窗保持打开以便继续链式查看
        String title = context.getString(R.string.card_related);
        keywordDialog = new KeywordCardListDialog(context, imageLoader, anchor, cards, title, this::showCard);
        keywordDialog.show();
    }

    /**
     * 计算与给定卡片关联的卡片集合（对齐 {@code CardDetail.relatedCards}）：
     * 卡名/描述包含当前卡名、与当前卡同字段、与描述关键词完全一致（卡名或「」/""引文）的卡，
     * 排除与当前卡同名的自身，结果去重。
     * <p>主线程兼容入口：对共享的 spanStringList 先做快照再扫描，避免与后台读并发。
     */
    private List<Card> relatedCards(Card cardInfo) {
        return relatedCards(cardInfo, new ArrayList<>(spanStringList));
    }

    /**
     * 关联卡计算（后台线程入口）：关键词列表以参数快照传入，不再跨线程读写共享的
     * spanStringList（旧实现后台 new ArrayList<>(spanStringList) 与主线程 clear/add
     * 并发，存在 ConcurrentModificationException 与读到半更新列表的问题）。
     */
    private List<Card> relatedCards(Card cardInfo, List<String> highlightedTexts) {
        if (cardInfo == null) return Collections.emptyList();
        SparseArray<Card> cards = DataManager.get().getCardManager().getAllCards();
        if (cards == null) return Collections.emptyList();
        Set<Card> matchingCards = new HashSet<>();
        Set<Long> cardInfoSetCodes = new HashSet<>();
        for (long setCode : cardInfo.getSetCode()) {
            if (setCode != 0) cardInfoSetCodes.add(setCode);
        }
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.valueAt(i);
            if (card.Name == null || card.Desc == null) continue;
            // 卡名或描述包含当前卡名
            if (!card.Name.equals(cardInfo.Name)) {
                if (card.Name.contains(cardInfo.Name) || card.Desc.contains(cardInfo.Name)) {
                    matchingCards.add(card);
                }
            }
            // 与当前卡有相同字段
            for (long setCode : card.getSetCode()) {
                if (cardInfoSetCodes.contains(setCode) && !card.Name.equals(cardInfo.Name)) {
                    matchingCards.add(card);
                }
            }
            // 与描述关键词完全一致（卡名相等或作为引文出现）视为关联卡
            for (String keyword : highlightedTexts) {
                boolean nameMatch = card.Name.equals(keyword);
                boolean descMatch = card.Desc.contains("\u300c" + keyword + "\u300d")
                        || card.Desc.contains("\"" + keyword + "\"");
                if ((nameMatch || descMatch) && !card.Name.equals(cardInfo.Name)) {
                    matchingCards.add(card);
                }
            }
        }
        return new ArrayList<>(matchingCards);
    }

    // 引用文本解析状态（对齐 CardDetail.QuoteType）
    private enum QuoteType {NONE, DOUBLE_QUOTE, ANGLE_QUOTE}

    private Bitmap getCoverBitmap() {
        if (coverBitmap == null || coverBitmap.isRecycled()) {
            File coverFile = new File(AppsSettings.get().getCoreSkinPath(), "cover.jpg");
            if (coverFile.exists()) {
                coverBitmap = BitmapFactory.decodeFile(coverFile.getAbsolutePath());
            }
        }
        return coverBitmap;
    }
}

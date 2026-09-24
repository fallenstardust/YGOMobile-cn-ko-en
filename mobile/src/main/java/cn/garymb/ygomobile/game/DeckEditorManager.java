package cn.garymb.ygomobile.game;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.DeckLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.cards.deck.CardTypeImage;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.cards.deck.MyDeckItem;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.dialogs.DeckSelectorDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import cn.garymb.ygomobile.ui.widget.CardView;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

/**
 * 卡组编辑器门面：视图绑定与装配、卡组数据增删改查/排序/侧换、保存与云端同步、
 * 读写与副卡组模式切换留在本类；按 // === 分栏拆出同包协作类（经包级私有直连本类共享状态）：
 * - {@link DeckGridLayoutApplier}：网格卡面尺寸计算与卡包展示模式版面
 * - {@link DeckStatsPanel}：张数 / 分类计数 / GeneSys 起源点数统计刷新
 * - {@link DeckDropHandler}：拖放落点（网格内换位、跳网格移动、搜索拖入、拖回搜索区删除）
 */
public class DeckEditorManager implements CardDragHelper.DropHandler {
    private static final String TAG = "DeckEditorManager";

    public interface DeckEditorListener {
        void onDeckModified();

        void onDeckSaved();

        void onExitEditor();

        void onCardSelected(Card card);

        void onSearchResultsUpdated(int count);

        void onSideDeckFinished(List<Integer> main, List<Integer> extra, List<Integer> side);
    }

    // 以下共享字段/方法被同包协作类（DeckGridLayoutApplier / DeckStatsPanel / DeckDropHandler）
    // 经包级私有直连访问
    final Activity activity;
    private final ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CardLoader cardLoader;
    private DeckEditorListener listener;
    final DeckInfo currentDeck;
    private final Random random = new Random();
    boolean isModified = false;
    boolean isReadonly = false;
    boolean isPackMode = false;
    private boolean isSiding = false;
    private int preMainCount = 0, preExtraCount = 0, preSideCount = 0;
    int savedNormalCardWidth = 0;
    int savedNormalCardHeight = 0;
    View rootView;
    private CardDetailPanel cardDetailPanel;
    TextView tvMainCountNum, tvExtraCountNum, tvSideCountNum;
    View llGenesysScoreboard;
    TextView tvCreditLimit, tvCreditCount, tvCreditRemain;
    TextView tvMainMonsterCount, tvMainSpellCount, tvMainTrapCount;
    TextView tvExtraFusionCount, tvExtraSynchroCount, tvExtraXyzCount, tvExtraLinkCount;
    TextView tvSideMonsterCount, tvSideSpellCount, tvSideTrapCount;
    private TextView tvLabelDeck, tvLabelType, tvLabelAttribute, tvLabelRace;
    private TextView tvLabelStar, tvLabelScale, tvLabelLimit, tvLabelAttack, tvLabelDefense, tvLabelKeyword;
    TextView tvLabelMainDeck, tvLabelExtraDeck, tvLabelSideDeck;
    CardGroupView cgvMain, cgvExtra, cgvSide;
    View layoutExtraStats, layoutSideStats;
    private View layoutDeckInfoPanel, layoutFilterPanel;
    private View llSideController, layoutDeckRightPanel;
    private EditText etDeckName;
    private Button btnSave, btnSaveAs, btnShuffle, btnSort, btnClear, btnDelete, btnExit;
    private Button btnDeckManager;
    private Button btnSideFinish, btnSideShuffle, btnSideSort, btnSideReset;
    private ImageTop mImageTop;
    private CardTypeImage mCardTypeImage;
    private ImageView ivMainMonsterType, ivMainSpellType, ivMainTrapType;
    private ImageView ivExtraFusionType, ivExtraSynchroType, ivExtraXyzType, ivExtraLinkType;
    private ImageView ivSideMonsterType, ivSideSpellType, ivSideTrapType;
    LimitList mLimitList;
    String currentDeckCategoryName = "";
    String currentDeckName = "";
    String currentDeckFilePath = "";
    private DeckSelectorDialog deckSelectorDialog;
    int touchSlop;
    final CardDragHelper dragHelper;
    private int availLm = 0;
    final StringManager mStringManager = DataManager.get().getStringManager();

    CardSearcherManager cardSearcherManager;

    // 自本类拆出的同包协作件（构造仅注入本类引用，无额外初始化顺序依赖）
    private final DeckGridLayoutApplier gridLayout;
    private final DeckStatsPanel statsPanel;
    private final DeckDropHandler dropHandler;

    public DeckEditorManager(Activity activity, ImageLoader imageLoader, CardDetailPanel cardDetailPanel) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        this.cardDetailPanel = cardDetailPanel;
        this.cardLoader = new CardLoader();
        this.currentDeck = new DeckInfo();
        this.dragHelper = new CardDragHelper(activity, this);
        this.cardSearcherManager = new CardSearcherManager(activity);
        this.gridLayout = new DeckGridLayoutApplier(this);
        this.statsPanel = new DeckStatsPanel(this);
        this.dropHandler = new DeckDropHandler(this);
    }

    public CardSearcherManager getCardSearcherManager() {
        return cardSearcherManager;
    }

    public void setListener(DeckEditorListener listener) {
        this.listener = listener;
        this.cardSearcherManager.setListener(count -> {
            if (listener != null) listener.onSearchResultsUpdated(count);
        });
    }

    public void initialize(View rootView) {
        this.rootView = rootView;
        bindViews(rootView);
        setupLabels();
        setupRecyclerViews();
        dropHandler.setup();
        setupButtons();
        cardSearcherManager.bindViews(rootView);
        cardSearcherManager.setupLabels();
        cardSearcherManager.setupSearchRecyclerView(imageLoader, dragHelper, this);
        cardSearcherManager.setupSpinners();
        cardSearcherManager.setupButtons();
        setupDeckSelectorDialog();
        loadLastDeck();
        statsPanel.updateCounts();
        isModified = false;
    }

    public void terminate() {
        if (isModified && !isReadonly) {
            showConfirmDialog(DataManager.get().getStringManager().getSystemString(1356, "此操作将放弃对当前卡组的修改，是否继续？"), this::doTerminate);
        } else {
            doTerminate();
        }
    }

    private void doTerminate() {
        saveLastCategoryAndDeck();
        if (listener != null) listener.onExitEditor();
    }

    private void bindViews(View root) {
        btnShuffle = activity.findViewById(R.id.btn_deck_shuffle);
        btnSort = activity.findViewById(R.id.btn_deck_sort);
        btnClear = activity.findViewById(R.id.btn_deck_clear);
        btnDelete = activity.findViewById(R.id.btn_deck_delete);
        btnExit = activity.findViewById(R.id.btn_deck_exit);
        tvMainCountNum = root.findViewById(R.id.tv_main_count_num);
        tvExtraCountNum = root.findViewById(R.id.tv_extra_count_num);
        tvSideCountNum = root.findViewById(R.id.tv_side_count_num);
        llGenesysScoreboard = root.findViewById(R.id.ll_genesys_scoreboard);
        tvCreditLimit = root.findViewById(R.id.tv_credit_limit);
        tvCreditCount = root.findViewById(R.id.tv_credit_count);
        tvCreditRemain = root.findViewById(R.id.tv_credit_remain);
        tvMainMonsterCount = root.findViewById(R.id.tv_main_monster_count);
        tvMainSpellCount = root.findViewById(R.id.tv_main_spell_count);
        tvMainTrapCount = root.findViewById(R.id.tv_main_trap_count);
        tvExtraFusionCount = root.findViewById(R.id.tv_extra_fusion_count);
        tvExtraSynchroCount = root.findViewById(R.id.tv_extra_synchro_count);
        tvExtraXyzCount = root.findViewById(R.id.tv_extra_xyz_count);
        tvExtraLinkCount = root.findViewById(R.id.tv_extra_link_count);
        tvSideMonsterCount = root.findViewById(R.id.tv_side_monster_count);
        tvSideSpellCount = root.findViewById(R.id.tv_side_spell_count);
        tvSideTrapCount = root.findViewById(R.id.tv_side_trap_count);
        tvLabelDeck = root.findViewById(R.id.tv_label_deck);
        tvLabelType = root.findViewById(R.id.tv_label_type);
        tvLabelAttribute = root.findViewById(R.id.tv_label_attribute);
        tvLabelRace = root.findViewById(R.id.tv_label_race);
        tvLabelStar = root.findViewById(R.id.tv_label_star);
        tvLabelScale = root.findViewById(R.id.tv_label_scale);
        tvLabelLimit = root.findViewById(R.id.tv_label_limit);
        tvLabelAttack = root.findViewById(R.id.tv_label_attack);
        tvLabelDefense = root.findViewById(R.id.tv_label_defense);
        tvLabelKeyword = root.findViewById(R.id.tv_label_keyword);
        tvLabelMainDeck = root.findViewById(R.id.tv_label_main_deck);
        tvLabelExtraDeck = root.findViewById(R.id.tv_label_extra_deck);
        tvLabelSideDeck = root.findViewById(R.id.tv_label_side_deck);
        cgvMain = root.findViewById(R.id.cgv_deck_main);
        cgvExtra = root.findViewById(R.id.cgv_deck_extra);
        cgvSide = root.findViewById(R.id.cgv_deck_side);
        layoutExtraStats = root.findViewById(R.id.layout_extra_stats);
        layoutSideStats = root.findViewById(R.id.layout_side_stats);
        layoutDeckInfoPanel = root.findViewById(R.id.layout_deck_info_panel);
        layoutFilterPanel = root.findViewById(R.id.layout_filter_panel);
        llSideController = root.findViewById(R.id.ll_side_controller);
        layoutDeckRightPanel = root.findViewById(R.id.layout_deck_right_panel);
        ivMainMonsterType = root.findViewById(R.id.iv_main_monster_type);
        ivMainSpellType = root.findViewById(R.id.iv_main_spell_type);
        ivMainTrapType = root.findViewById(R.id.iv_main_trap_type);
        ivExtraFusionType = root.findViewById(R.id.iv_extra_fusion_type);
        ivExtraSynchroType = root.findViewById(R.id.iv_extra_synchro_type);
        ivExtraXyzType = root.findViewById(R.id.iv_extra_xyz_type);
        ivExtraLinkType = root.findViewById(R.id.iv_extra_link_type);
        ivSideMonsterType = root.findViewById(R.id.iv_side_monster_type);
        ivSideSpellType = root.findViewById(R.id.iv_side_spell_type);
        ivSideTrapType = root.findViewById(R.id.iv_side_trap_type);
        btnDeckManager = root.findViewById(R.id.btn_deck_manager);
        etDeckName = root.findViewById(R.id.et_deck_name);
        btnSave = root.findViewById(R.id.btn_deck_save);
        btnSaveAs = root.findViewById(R.id.btn_deck_save_as);
        btnSideFinish = root.findViewById(R.id.btn_side_finish);
        btnSideShuffle = root.findViewById(R.id.btn_side_shuffle);
        btnSideSort = root.findViewById(R.id.btn_side_sort);
        btnSideReset = root.findViewById(R.id.btn_side_reset);
    }

    private void setupLabels() {
        if (btnSideFinish != null) {
            btnSideFinish.setText(mStringManager.getSystemString(1334, "副卡组替换完成"));
            btnSideFinish.setOnClickListener(v -> sideFinish());
        }
        if (btnSideShuffle != null) {
            btnSideShuffle.setText(mStringManager.getSystemString(1307, "打乱"));
            btnSideShuffle.setOnClickListener(v -> shuffleDeck());
        }
        if (btnSideSort != null) {
            btnSideSort.setText(mStringManager.getSystemString(1305, "排序"));
            btnSideSort.setOnClickListener(v -> sortDeck());
        }
        if (btnSideReset != null) {
            btnSideReset.setText(mStringManager.getSystemString(1309, "重置"));
            btnSideReset.setOnClickListener(v -> sideReset());
        }
        setSystemLabel(tvLabelDeck, 1300, "卡组:");
        setSystemLabel(tvLabelType, 1311, "种类:");
        setSystemLabel(tvLabelAttribute, 1319, "属性:");
        setSystemLabel(tvLabelRace, 1321, "种族:");
        setSystemLabel(tvLabelStar, 1324, "星数:");
        setSystemLabel(tvLabelScale, 1336, "刻度:");
        setSystemLabel(tvLabelLimit, 1315, "禁限:");
        setSystemLabel(tvLabelAttack, 1322, "攻击:");
        setSystemLabel(tvLabelDefense, 1323, "守备:");
        setSystemLabel(tvLabelKeyword, 1325, "关键字:");
        setSystemLabel(tvLabelMainDeck, 1330, "主卡组:");
        setSystemLabel(tvLabelExtraDeck, 1331, "额外卡组:");
        setSystemLabel(tvLabelSideDeck, 1332, "副卡组:");
    }

    private void setSystemLabel(TextView tv, int index, String def) {
        if (tv != null) tv.setText(mStringManager.getSystemString(index, def));
    }


    private void setupRecyclerViews() {
        mLimitList = isBanListActive()
                ? (AppsSettings.get().getGenesysMode() == 1
                   ? cardLoader.getGenesysLimitList() : cardLoader.getLimitList())
                : null;
        mImageTop = new ImageTop(activity);
        mCardTypeImage = new CardTypeImage(activity);
        if (mCardTypeImage != null) {
            setBitmapIfNotNull(ivMainMonsterType, mCardTypeImage.monster);
            setBitmapIfNotNull(ivMainSpellType, mCardTypeImage.spell);
            setBitmapIfNotNull(ivMainTrapType, mCardTypeImage.trap);
            setBitmapIfNotNull(ivExtraFusionType, mCardTypeImage.fusion);
            setBitmapIfNotNull(ivExtraSynchroType, mCardTypeImage.synchro);
            setBitmapIfNotNull(ivExtraXyzType, mCardTypeImage.xyz);
            setBitmapIfNotNull(ivExtraLinkType, mCardTypeImage.link);
            setBitmapIfNotNull(ivSideMonsterType, mCardTypeImage.monster);
            setBitmapIfNotNull(ivSideSpellType, mCardTypeImage.spell);
            setBitmapIfNotNull(ivSideTrapType, mCardTypeImage.trap);
        }
        cgvMain.setImageLoader(imageLoader);
        cgvMain.setLineLimit(4, 10, 15);
        cgvExtra.setImageLoader(imageLoader);
        cgvExtra.setLineLimit(1, 10, 15);
        cgvSide.setImageLoader(imageLoader);
        cgvSide.setLineLimit(1, 10, 15);
        gridLayout.requestUpdate();
        cardSearcherManager.setLimitList(mLimitList);
    }

    private void setBitmapIfNotNull(ImageView iv, Bitmap bm) {
        if (iv != null) iv.setImageBitmap(bm);
    }

    /**
     * 网格卡面尺寸计算与卡包展示模式版面（实现已拆至 {@link DeckGridLayoutApplier}）
     */
    void applyPackMode(boolean packMode) {
        gridLayout.applyPackMode(packMode);
    }

    public void refreshLimitList() {
        mLimitList = isBanListActive()
                ? (AppsSettings.get().getGenesysMode() == 1
                   ? cardLoader.getGenesysLimitList()
                   : cardLoader.getLimitList())
                : null;
        if (cgvMain != null) cgvMain.updateTopImage(mImageTop, mLimitList);
        if (cgvExtra != null) cgvExtra.updateTopImage(mImageTop, mLimitList);
        if (cgvSide != null) cgvSide.updateTopImage(mImageTop, mLimitList);
        cardSearcherManager.setLimitList(mLimitList);
        statsPanel.updateCounts();
    }

    /**
     * 禁限卡表是否生效：对应模式的开关开启（use_lflist/use_genesys_lflist）
     * 且所选禁卡表名不是N/A。未生效时不做checkLimit校验、不显示禁限角标与GeneSys记分板。
     */
    private boolean isBanListActive() {
        AppsSettings settings = AppsSettings.get();
        boolean genesys = settings.getGenesysMode() == 1;
        boolean enabled = settings.getIntSettings(
                genesys ? "use_genesys_lflist" : "use_lflist", 1) == 1;
        if (!enabled) return false;
        String name = genesys ? settings.getLastGenesysLimit() : settings.getLastLimit();
        return name != null && !name.isEmpty() && !"N/A".equals(name);
    }

    private void setupButtons() {
        if (btnSave != null) btnSave.setText(mStringManager.getSystemString(1302, "保存"));
        if (btnSaveAs != null) btnSaveAs.setText(mStringManager.getSystemString(1303, "另存"));
        setClickListener(btnExit, v -> terminate());
        setClickListener(btnShuffle, v -> shuffleDeck());
        setClickListener(btnSort, v -> sortDeck());
        setClickListener(btnClear, v -> clearDeck());
        setClickListener(btnDelete, v -> deleteDeck());
        setClickListener(btnSave, v -> saveDeck());
        setClickListener(btnSaveAs, v -> saveDeckAs());
    }

    private void setClickListener(Button btn, View.OnClickListener l) {
        if (btn != null) btn.setOnClickListener(l);
    }

    private void setupDeckSelectorDialog() {
        deckSelectorDialog = new DeckSelectorDialog(activity);
        deckSelectorDialog.setIncludePackCategory(true);
        deckSelectorDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(String p, String n, String c) {
                applySelectedDeck(p, n, c);
            }

            @Override
            public void onDeckItemClicked(String p, String n, String c) {
                applySelectedDeck(p, n, c);
            }

            @Override
            public void onCancelled() {
            }
        });
        if (btnDeckManager != null) {
            // 再次点击"选择卡组/卡组管理"按钮收起已展开的卡组选择窗（切换式交互，
            // 与 PlayerWaitingDialog 的 btnPwDeckSelect 行为一致）
            btnDeckManager.setOnClickListener(v -> {
                if (deckSelectorDialog == null) return;
                if (deckSelectorDialog.isShowing()) {
                    deckSelectorDialog.dismiss();
                    return;
                }
                if (isModified && !isReadonly) {
                    showConfirmDialog("此操作将放弃对当前卡组的修改，是否继续？",
                            () -> {
                                if (deckSelectorDialog != null)
                                    deckSelectorDialog.show(btnDeckManager);
                            });
                } else {
                    deckSelectorDialog.show(btnDeckManager);
                }
            });
        }
    }

    //应用选中的卡组：加载、刷新按钮文本并记录最后分类/卡组（点击item与确认共用）
    private void applySelectedDeck(String deckPath, String deckName, String categoryName) {
        currentDeckFilePath = deckPath;
        currentDeckCategoryName = categoryName;
        currentDeckName = deckName;
        loadDeckFromPath(deckPath);
        updateDeckManagerButtonText();
        AppsSettings.get().saveSettings("lastcategory", categoryName);
        AppsSettings.get().saveSettings("lastdeck", deckName);
    }

    private void updateDeckManagerButtonText() {
        if (btnDeckManager != null) {
            if (currentDeckName != null && !currentDeckName.isEmpty()) {
                String uncatName = activity.getString(R.string.category_Uncategorized);
                if (currentDeckCategoryName != null && !currentDeckCategoryName.isEmpty()
                        && !currentDeckCategoryName.equals(uncatName)) {
                    btnDeckManager.setText(currentDeckCategoryName + "|" + currentDeckName);
                } else {
                    btnDeckManager.setText(currentDeckName);
                }
            } else {
                btnDeckManager.setText(mStringManager.getSystemString(1460, "卡组管理"));
            }
        }
    }

    private void loadDeckFromPath(String deckPath) {
        File deckFile = new File(deckPath);
        boolean loaded = false;
        if (deckFile.exists()) {
            DeckInfo loadedDeck = DeckLoader.readDeck(cardLoader, deckFile);
            if (loadedDeck != null) {
                currentDeck.update(loadedDeck);
                currentDeck.source = deckFile;
                isModified = false;
                loaded = true;
            }
        }
        String aiDeckDir = AppsSettings.get().getAiDeckDir();
        isReadonly = deckPath.startsWith(aiDeckDir);
        if (loaded) {
            String packDir = AppsSettings.get().getPackDeckDir();
            String cacheDeckDir = AppsSettings.get().getCacheDeckDir();
            boolean packMode = deckPath.startsWith(packDir)
                    || deckPath.startsWith(cacheDeckDir);
            if (isPackMode != packMode) {
                gridLayout.applyPackMode(packMode);
            } else {
                notifyDeckChanged();
            }
        }
        refreshReadonly();
    }

    // === 对应 deck_con.cpp: push_main ===
    public boolean pushMain(Card card, int seq) {
        if (card == null || Card.isExtraCard(card.Type)) return false;
        if (!isPackMode && currentDeck.getMainCount() >= Constants.DECK_MAIN_MAX) return false;
        if (!checkLimit(card)) return false;
        boolean result = (seq >= 0 && seq <= currentDeck.mainCards.size())
                ? currentDeck.addMainCards(seq, card, isPackMode) : currentDeck.addMainCards(card);
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    public boolean pushExtra(Card card, int seq) {
        if (card == null || !Card.isExtraCard(card.Type)) return false;
        if (currentDeck.getExtraCount() >= Constants.DECK_EXTRA_MAX) return false;
        if (!checkLimit(card)) return false;
        boolean result = (seq >= 0 && seq <= currentDeck.extraCards.size())
                ? currentDeck.addExtraCards(seq, card) : currentDeck.addExtraCards(card);
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    public boolean pushSide(Card card, int seq) {
        if (card == null) return false;
        if (currentDeck.getSideCount() >= Constants.DECK_SIDE_MAX) return false;
        if (!checkLimit(card)) return false;
        boolean result = (seq >= 0 && seq <= currentDeck.sideCards.size())
                ? currentDeck.addSideCards(seq, card) : currentDeck.addSideCards(card);
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    // === 对应 deck_con.cpp: check_limit ===
    public boolean checkLimit(Card card) {
        if (card == null) return false;
        //禁限卡表未启用（关闭或选N/A）：跳过所有禁限数量与GeneSys点数校验
        if (mLimitList == null) return true;
        int gameCode = card.getGameCode();
        int limit = 3;
        if (mLimitList != null) {
            if (mLimitList.check(gameCode, gameCode, LimitType.Forbidden)) limit = 0;
            else if (mLimitList.check(gameCode, gameCode, LimitType.Limit)) limit = 1;
            else if (mLimitList.check(gameCode, gameCode, LimitType.SemiLimit)) limit = 2;
        }
        int count = 0;
        for (Card c : currentDeck.mainCards) if (c.getGameCode() == gameCode) count++;
        for (Card c : currentDeck.extraCards) if (c.getGameCode() == gameCode) count++;
        for (Card c : currentDeck.sideCards) if (c.getGameCode() == gameCode) count++;
        if (count >= limit) return false;
        if (mLimitList != null && mLimitList.getCreditLimits() != null) {
            int totalCredit = 0;
            for (Card c : currentDeck.mainCards) totalCredit += getCardCredit(c);
            for (Card c : currentDeck.extraCards) totalCredit += getCardCredit(c);
            for (Card c : currentDeck.sideCards) totalCredit += getCardCredit(c);
            if (totalCredit + getCardCredit(card) > mLimitList.getCreditLimits()) return false;
        }
        return true;
    }

    //GeneSys模式单卡起源点数：按规则同名卡code（getGameCode）查询，与禁限判断口径一致
    int getCardCredit(Card card) {
        if (card == null || mLimitList == null || mLimitList.getCredits() == null) return 0;
        Integer credit = mLimitList.getCredits().get(card.getGameCode());
        return credit != null ? credit : 0;
    }

    // === 对应 deck_con.cpp: BUTTON_SHUFFLE_DECK ===
    public void shuffleDeck() {
        if (isReadonly) return;
        Collections.shuffle(currentDeck.mainCards, random);
        isModified = true;
        notifyDeckChanged();
    }

    // === 对应 deck_con.cpp: BUTTON_SORT_DECK ===
    public void sortDeck() {
        if (isReadonly) return;
        currentDeck.sortAll();
        isModified = true;
        notifyDeckChanged();
    }

    // === 对应 duelclient.cpp: STOC_CHANGE_SIDE 进入副卡组替换模式 ===
    public void enterSideMode() {
        isSiding = true;
        isReadonly = false;
        preMainCount = currentDeck.mainCards.size();
        preExtraCount = currentDeck.extraCards.size();
        preSideCount = currentDeck.sideCards.size();
        refreshReadonly();
        //对齐C++：副卡组模式下隐藏卡组信息面板与筛选面板，只保留副卡组操作按钮
        if (layoutDeckInfoPanel != null) layoutDeckInfoPanel.setVisibility(View.GONE);
        if (layoutFilterPanel != null) layoutFilterPanel.setVisibility(View.GONE);
        //显示副卡组控制器（完成/打乱/排序/重置），隐藏右侧搜索结果面板
        if (llSideController != null) llSideController.setVisibility(View.VISIBLE);
        if (layoutDeckRightPanel != null) layoutDeckRightPanel.setVisibility(View.INVISIBLE);
        //隐藏卡片详情面板中的卡组编辑控制栏
        if (cardDetailPanel != null) cardDetailPanel.hideDeckControl();
    }

    //副卡组替换完成/取消后退出副卡组模式
    public void exitSideMode() {
        isSiding = false;
        if (layoutDeckInfoPanel != null) layoutDeckInfoPanel.setVisibility(View.VISIBLE);
        if (layoutFilterPanel != null) layoutFilterPanel.setVisibility(View.VISIBLE);
        if (llSideController != null) llSideController.setVisibility(View.GONE);
        if (layoutDeckRightPanel != null) layoutDeckRightPanel.setVisibility(View.VISIBLE);
        if (cardDetailPanel != null) cardDetailPanel.showDeckControl();
    }

    // === 对应 deck_con.cpp: BUTTON_SIDE_OK ===
    public void sideFinish() {
        if (!isSiding) return;
        if (currentDeck.mainCards.size() != preMainCount
                || currentDeck.extraCards.size() != preExtraCount
                || currentDeck.sideCards.size() != preSideCount) {
            YGOUtil.showTextToast(mStringManager.getSystemString(1410, "副卡组替换不能改变卡组张数"));
            return;
        }
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        for (Card c : currentDeck.mainCards) main.add(c.Code);
        for (Card c : currentDeck.extraCards) extra.add(c.Code);
        for (Card c : currentDeck.sideCards) side.add(c.Code);
        if (listener != null) listener.onSideDeckFinished(main, extra, side);
    }

    // === 对应 deck_con.cpp: BUTTON_SIDE_RELOAD ===
    public void sideReset() {
        if (currentDeckFilePath != null && !currentDeckFilePath.isEmpty()) {
            loadDeckFromPath(currentDeckFilePath);
        }
    }

    // === 对应 deck_con.cpp: BUTTON_CLEAR_DECK ===
    public void clearDeck() {
        if (isReadonly) return;
        showConfirmDialog(mStringManager.getSystemString(1339, "是否清空正在编辑的卡组？"), () -> {
            currentDeck.mainCards.clear();
            currentDeck.extraCards.clear();
            currentDeck.sideCards.clear();
            isModified = true;
            notifyDeckChanged();
        });
    }

    // === 对应 deck_con.cpp: BUTTON_DELETE_DECK ===
    public void deleteDeck() {
        if (isReadonly) return;
        if (currentDeckFilePath == null || currentDeckFilePath.isEmpty()) return;
        String deckName = currentDeckName != null && !currentDeckName.isEmpty()
                ? currentDeckName : new File(currentDeckFilePath).getName().replace(".ydk", "");
        String deletedCategory = currentDeckCategoryName;
        showConfirmDialog(deckName + "\n" + DataManager.get().getStringManager().getSystemString(1337, "是否删除这个卡组？"), () -> {
            File deckFile = new File(currentDeckFilePath);
            if (deckFile.exists()) {
                //先同步删除云端卡组（DeckFile必须在 ydk 文件从磁盘删除之前构造，
                //以便读取其中保存的 deckId；未登录时 deleteDecks 内部会直接跳过）
                syncDeckDeleteFromCloud(deckFile);

                deckFile.delete();
                currentDeckFilePath = "";
                currentDeckName = "";
                currentDeck.mainCards.clear();
                currentDeck.extraCards.clear();
                currentDeck.sideCards.clear();
                notifyDeckChanged();
                isModified = false;

                // 自动加载同分类第一个卡组，若分类已空则回退到未分类
                List<DeckFile> allDecks = DeckUtil.getDeckAllList();
                DeckFile targetDeck = null;
                for (DeckFile df : allDecks) {
                    if (df.getTypeName().equals(deletedCategory)) {
                        targetDeck = df;
                        break;
                    }
                }
                if (targetDeck == null) {
                    String uncatName = activity.getString(R.string.category_Uncategorized);
                    for (DeckFile df : allDecks) {
                        if (df.getTypeName().equals(uncatName)) {
                            targetDeck = df;
                            break;
                        }
                    }
                }
                if (targetDeck != null) {
                    applySelectedDeck(targetDeck.getPath(), targetDeck.getName(), targetDeck.getTypeName());
                } else {
                    updateDeckManagerButtonText();
                }

                YGOUtil.showTextToast(DataManager.get().getStringManager().getSystemString(1338, "删除成功"));
            }
        });
    }

    public void saveDeck() {
        if (isReadonly) return;
        if (currentDeckFilePath == null || currentDeckFilePath.isEmpty()) {
            YGOUtil.showTextToast("请先选择或另存卡组");
            return;
        }
        File deckFile = new File(currentDeckFilePath);
        boolean result = DeckUtils.save(currentDeck, deckFile);
        if (result) {
            isModified = false;
            YGOUtil.showTextToast(DataManager.get().getStringManager().getSystemString(1335, "保存成功"));
            if (listener != null) listener.onDeckSaved();
            //保存到本地成功后，同步上传到卡组广场云端（萌卡账号已登录时）
            syncDeckUploadToCloud(deckFile, false);
        }
    }

    public void saveDeckAs() {
        if (isReadonly) return;
        if (etDeckName == null) return;
        String name = etDeckName.getText().toString().trim();
        if (name.isEmpty()) {
            YGOUtil.showTextToast("请输入卡组名称");
            return;
        }
        File deckFile = new File(AppsSettings.get().getDeckDir(), name + ".ydk");
        boolean result = DeckUtils.save(currentDeck, deckFile);
        if (result) {
            currentDeckFilePath = deckFile.getAbsolutePath();
            currentDeckName = name;
            String uncatName = activity.getString(R.string.category_Uncategorized);
            currentDeckCategoryName = uncatName;
            isModified = false;
            updateDeckManagerButtonText();
            AppsSettings.get().saveSettings("lastcategory", uncatName);
            AppsSettings.get().saveSettings("lastdeck", name);
            YGOUtil.showTextToast("卡组已保存为: " + name);
            if (listener != null) listener.onDeckSaved();
            //另存为相当于新建卡组：向云端申请新的 deckId 后再上传（萌卡账号已登录时）
            syncDeckUploadToCloud(deckFile, true);
        }
    }

    /**
     * 保存/另存卡组成功后，将卡组同步上传到卡组广场云端（仅在萌卡账号已登录时执行），
     * 逻辑对齐 DeckManagerFragment 中 action_save / 新建卡组 两处对 DeckSquareApiUtil 的调用。
     *
     * @param ydkFile   刚保存成功的本地卡组文件
     * @param isNewDeck 是否为“另存为”新建的卡组：true 时先向服务器申请新的 deckId 再上传，
     *                  false 时复用 ydk 文件中已有的 deckId 直接覆盖上传
     */
    private void syncDeckUploadToCloud(File ydkFile, boolean isNewDeck) {
        if (SharedPreferenceUtil.getServerToken() == null) {
            return;
        }
        LoginToken loginToken = new LoginToken(SharedPreferenceUtil.getServerUserId(),
                SharedPreferenceUtil.getServerToken());
        VUiKit.defer().when(() -> {
            try {
                List<MyDeckItem> deckItemList = new ArrayList<>();
                deckItemList.add(DeckUtil.getMyDeckItem(ydkFile));
                if (isNewDeck) {
                    DeckSquareApiUtil.requestIdAndPushNewDecks(deckItemList, loginToken);
                } else {
                    DeckSquareApiUtil.UploadMyDecks(deckItemList, loginToken);
                }
            } catch (IOException e) {
                return e;
            }
            return 0;
        }).fail(e -> LogUtil.e(TAG, "Upload deck failed: " + e))
                .done(result -> LogUtil.d(TAG, "Deck uploaded successfully"));
    }

    /**
     * 删除卡组时先删除云端对应记录；必须在 ydk 文件从磁盘删除之前构造 DeckFile，
     * 以便读取其中保存的 deckId。未登录时 DeckSquareApiUtil.deleteDecks 内部会直接跳过。
     */
    private void syncDeckDeleteFromCloud(File ydkFile) {
        List<DeckFile> deckFileList = new ArrayList<>();
        deckFileList.add(new DeckFile(ydkFile));
        DeckSquareApiUtil.deleteDecks(deckFileList);
    }

    // === 对应 deck_con.cpp: RefreshReadonly ===
    public void refreshReadonly() {
        boolean disabled = isReadonly || isPackMode;
        int textColor = disabled ? Color.GRAY : Color.WHITE;
        if (btnSave != null) btnSave.setEnabled(!disabled);
        if (btnSaveAs != null) btnSaveAs.setEnabled(!disabled);
        setBtnState(btnClear, disabled, textColor);
        setBtnState(btnShuffle, disabled, textColor);
        setBtnState(btnSort, disabled, textColor);
        setBtnState(btnDelete, disabled, textColor);
    }

    private void setBtnState(Button btn, boolean disabled, int textColor) {
        if (btn != null) {
            btn.setEnabled(!disabled);
            btn.setTextColor(textColor);
        }
    }

    public void showCardInfo(Card card) {
        if (card == null) return;
        if (cardDetailPanel != null) {
            cardDetailPanel.showCard(card);
        }
        if (listener != null) listener.onCardSelected(card);
    }

    public void onSearchCardClicked(Card card) {
        if (card == null || isReadonly) return;
        showCardInfo(card);
    }

    public void onDeckCardClicked(DeckInfo.Type type, int position) {
        if (cgvMain != null) cgvMain.clearSelection();
        if (cgvExtra != null) cgvExtra.clearSelection();
        if (cgvSide != null) cgvSide.clearSelection();
        Card card = null;
        if (type == DeckInfo.Type.Main) {
            card = currentDeck.getMainCard(position);
            if (cgvMain != null) cgvMain.setSelectedIndex(position);
        } else if (type == DeckInfo.Type.Extra) {
            card = currentDeck.getExtraCard(position);
            if (cgvExtra != null) cgvExtra.setSelectedIndex(position);
        } else if (type == DeckInfo.Type.Side) {
            card = currentDeck.getSideCard(position);
            if (cgvSide != null) cgvSide.setSelectedIndex(position);
        }
        if (card != null) showCardInfo(card);
    }

    // === Private helpers ===

    // 卡片增删后统一刷新（包级私有：同包协作类 DeckGridLayoutApplier / DeckDropHandler 亦经此刷新）
    void notifyDeckChanged() {
        statsPanel.updateCounts();
        refreshCardGroupView(cgvMain, currentDeck.mainCards, DeckInfo.Type.Main);
        refreshCardGroupView(cgvExtra, currentDeck.extraCards, DeckInfo.Type.Extra);
        refreshCardGroupView(cgvSide, currentDeck.sideCards, DeckInfo.Type.Side);
        if (listener != null) listener.onDeckModified();
    }

    private void refreshCardGroupView(CardGroupView groupView, List<Card> cards, DeckInfo.Type type) {
        if (groupView == null) return;
        groupView.removeAllCards();
        for (int i = 0; i < cards.size(); i++) groupView.addCard(cards.get(i));
        groupView.updateTopImage(mImageTop, mLimitList);
        groupView.updateAvail(mImageTop, availLm);
        int count = groupView.getChildCount();
        for (int i = 0; i < count; i++) {
            CardView cardView = (CardView) groupView.getChildAt(i);
            final int index = i;
            cardView.setOnClickListener(v -> onDeckCardClicked(type, index));
            DeckCardAdapter adapter = cardSearcherManager.getSearchAdapter();
            if (adapter != null) {
                cardView.setOnTouchListener(adapter.createDragTouchListener(type, index, cardView.getCard(), touchSlop, isReadonly || isPackMode));
            }
        }
    }

    private void loadLastDeck() {
        AppsSettings settings = AppsSettings.get();
        String lastDeckPath = settings.getLastDeckPath();
        String lastDeckName = settings.getLastDeckName();
        String lastCategory = settings.getLastCategory();

        String savedPath = settings.getSettings("lastdeckpath");
        if (savedPath != null && !savedPath.isEmpty()) {
            if (!new File(savedPath).exists()) {
                String cacheDeckDir = settings.getCacheDeckDir();
                if (cacheDeckDir != null && savedPath.startsWith(cacheDeckDir)) {
                    try {
                        DeckUtil.getExpansionsDeckList();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            if (new File(savedPath).exists()) {
                currentDeckFilePath = savedPath;
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName != null ? lastDeckName : "";
                loadDeckFromPath(savedPath);
                updateDeckManagerButtonText();
                return;
            }
        }

        if (lastDeckPath != null && !lastDeckPath.isEmpty()) {
            File deckFile = new File(lastDeckPath);
            if (deckFile.exists()) {
                currentDeckFilePath = lastDeckPath;
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName != null ? lastDeckName : "";
                loadDeckFromPath(lastDeckPath);
                updateDeckManagerButtonText();
                return;
            }
        }

        if (lastDeckName != null && !lastDeckName.isEmpty()) {
            File deckFile = new File(AppsSettings.get().getDeckDir(), lastDeckName + ".ydk");
            if (deckFile.exists()) {
                currentDeckFilePath = deckFile.getAbsolutePath();
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName;
                loadDeckFromPath(deckFile.getAbsolutePath());
                updateDeckManagerButtonText();
                return;
            }
        }

        File deckDir = new File(settings.getDeckDir());
        if (deckDir.exists() && deckDir.isDirectory()) {
            File[] files = deckDir.listFiles((dir, name) -> name.endsWith(".ydk"));
            if (files != null && files.length > 0) {
                Arrays.sort(files);
                File first = files[0];
                currentDeckFilePath = first.getAbsolutePath();
                currentDeckCategoryName = "";
                currentDeckName = first.getName().replace(".ydk", "");
                loadDeckFromPath(first.getAbsolutePath());
                updateDeckManagerButtonText();
            }
        }
    }

    private void saveLastCategoryAndDeck() {
        if (currentDeckCategoryName != null && !currentDeckCategoryName.isEmpty()) {
            AppsSettings.get().saveSettings("lastcategory", currentDeckCategoryName);
        }
        if (currentDeckFilePath != null && !currentDeckFilePath.isEmpty()) {
            AppsSettings.get().saveSettings("lastdeckpath", currentDeckFilePath);
            String deckName = new File(currentDeckFilePath).getName().replace(".ydk", "");
            AppsSettings.get().saveSettings("lastdeck", deckName);
        }
    }

    private void showConfirmDialog(String message, Runnable onConfirm) {
        mainHandler.post(() -> {
            new YesOrNoDialog(activity)
                    .setMessage(message)
                    .setType(YesOrNoDialog.TYPE_YES_NO)
                    .setPositiveButtonText(mStringManager.getSystemString(1213, "是"))
                    .setNegativeButtonText(mStringManager.getSystemString(1214, "否"))
                    .setPositiveButton(v -> {
                        onConfirm.run();
                    })
                    .show(rootView);
        });
    }

    // === 拖放：网格内换位、跳网格移动、搜索结果拖入、拖回搜索区删除
    //   （实现已拆至 DeckDropHandler，本类仅作为 CardDragHelper.DropHandler 接收回调并转发）===

    @Override
    public void onCardDrop(View target, DeckInfo.Type source, int index, Card card, float rawX, float rawY) {
        dropHandler.onCardDrop(target, source, index, card, rawX, rawY);
    }
}
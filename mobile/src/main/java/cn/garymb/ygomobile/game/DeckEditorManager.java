package cn.garymb.ygomobile.game;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.DeckLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.cards.deck.CardTypeImage;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.dialogs.DeckSelectorDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import cn.garymb.ygomobile.ui.widget.CardView;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

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

    private final Activity activity;
    private final ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CardLoader cardLoader;
    private DeckEditorListener listener;
    private final DeckInfo currentDeck;
    private final Random random = new Random();
    private boolean isModified = false;
    private boolean isReadonly = false;
    private boolean isPackMode = false;
    private boolean isSiding = false;
    private int preMainCount = 0, preExtraCount = 0, preSideCount = 0;
    private int savedNormalCardWidth = 0;
    private int savedNormalCardHeight = 0;
    private View rootView;
    private CardDetailPanel cardDetailPanel;
    private TextView tvMainCountNum, tvExtraCountNum, tvSideCountNum;
    private View llGenesysScoreboard;
    private TextView tvCreditLimit, tvCreditCount, tvCreditRemain;
    private TextView tvMainMonsterCount, tvMainSpellCount, tvMainTrapCount;
    private TextView tvExtraFusionCount, tvExtraSynchroCount, tvExtraXyzCount, tvExtraLinkCount;
    private TextView tvSideMonsterCount, tvSideSpellCount, tvSideTrapCount;
    private TextView tvLabelDeck, tvLabelType, tvLabelAttribute, tvLabelRace;
    private TextView tvLabelStar, tvLabelScale, tvLabelLimit, tvLabelAttack, tvLabelDefense, tvLabelKeyword;
    private TextView tvLabelMainDeck, tvLabelExtraDeck, tvLabelSideDeck;
    private CardGroupView cgvMain, cgvExtra, cgvSide;
    private View layoutExtraStats, layoutSideStats;
    private View layoutDeckInfoPanel, layoutFilterPanel;
    private EditText etDeckName;
    private Button btnSave, btnSaveAs, btnShuffle, btnSort, btnClear, btnDelete, btnExit;
    private Button btnDeckManager;
    private Button btnSideFinish, btnSideShuffle, btnSideSort, btnSideReset;
    private ImageTop mImageTop;
    private CardTypeImage mCardTypeImage;
    private ImageView ivMainMonsterType, ivMainSpellType, ivMainTrapType;
    private ImageView ivExtraFusionType, ivExtraSynchroType, ivExtraXyzType, ivExtraLinkType;
    private ImageView ivSideMonsterType, ivSideSpellType, ivSideTrapType;
    private LimitList mLimitList;
    private String currentDeckCategoryName = "";
    private String currentDeckName = "";
    private String currentDeckFilePath = "";
    private DeckSelectorDialog deckSelectorDialog;
    private int touchSlop;
    private final CardDragHelper dragHelper;
    private int availLm = 0;
    private final StringManager mStringManager = DataManager.get().getStringManager();

    private CardSearcherManager cardSearcherManager;

    public DeckEditorManager(Activity activity, ImageLoader imageLoader, CardDetailPanel cardDetailPanel) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        this.cardDetailPanel = cardDetailPanel;
        this.cardLoader = new CardLoader();
        this.currentDeck = new DeckInfo();
        this.dragHelper = new CardDragHelper(activity, this);
        this.cardSearcherManager = new CardSearcherManager(activity);
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
        setupDragAndDrop();
        setupButtons();
        cardSearcherManager.bindViews(rootView);
        cardSearcherManager.setupLabels();
        cardSearcherManager.setupSearchRecyclerView(imageLoader, dragHelper, this);
        cardSearcherManager.setupSpinners();
        cardSearcherManager.setupButtons();
        setupDeckSelectorDialog();
        loadLastDeck();
        updateDeckCounts();
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
        requestDeckCardSizeUpdate();
        cardSearcherManager.setLimitList(mLimitList);
    }

    private void setBitmapIfNotNull(ImageView iv, Bitmap bm) {
        if (iv != null) iv.setImageBitmap(bm);
    }

    /**
     * 根据主卡组区域的实际测量宽高动态计算卡片尺寸：
     * 普通模式保证一行放下 {@link Constants#DECK_WIDTH_COUNT} 张且主卡组4行完整显示；
     * 卡包展示模式主网格铺满可用高度，行数随高度动态计算（不再限制4行/60张）。
     */
    private void requestDeckCardSizeUpdate() {
        if (cgvMain == null) return;
        cgvMain.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int mainWidth = cgvMain.getWidth();
                int mainHeight = cgvMain.getHeight();
                if (mainWidth <= 0 || mainHeight <= 0) return;
                cgvMain.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int extraHeight = cgvExtra != null ? cgvExtra.getHeight() : 0;
                int sideHeight = cgvSide != null ? cgvSide.getHeight() : 0;

                applyDeckCardSize(mainWidth, mainHeight, extraHeight, sideHeight);
            }
        });
    }

    private void applyDeckCardSize(int mainWidth, int mainHeight, int extraHeight, int sideHeight) {
        int availWidth = mainWidth - cgvMain.getPaddingLeft() - cgvMain.getPaddingRight();
        if (availWidth <= 0) return;
        float ratio = (float) Constants.CORE_SKIN_CARD_SMALL_SIZE[1] / (float) Constants.CORE_SKIN_CARD_SMALL_SIZE[0];
        int mainAvail = Math.max(0, mainHeight - cgvMain.getPaddingTop() - cgvMain.getPaddingBottom());
        int extraAvail = extraHeight > 0 && cgvExtra != null
                ? Math.max(0, extraHeight - cgvExtra.getPaddingTop() - cgvExtra.getPaddingBottom()) : 0;
        int sideAvail = sideHeight > 0 && cgvSide != null
                ? Math.max(0, sideHeight - cgvSide.getPaddingTop() - cgvSide.getPaddingBottom()) : 0;
        if (isPackMode) {
            int cardWidth, cardHeight;
            if (savedNormalCardWidth > 0 && savedNormalCardHeight > 0) {
                cardWidth = savedNormalCardWidth;
                cardHeight = savedNormalCardHeight;
            } else {
                int totalAvail = mainAvail + extraAvail + sideAvail;
                int wByCol = availWidth / Constants.DECK_WIDTH_COUNT;
                int wByH = totalAvail > 0 ? (int) ((totalAvail / 6f) / ratio) : Integer.MAX_VALUE;
                cardWidth = Math.max(1, Math.min(wByCol, wByH));
                cardHeight = Math.max(1, (int) (cardWidth * ratio));
            }
            int rows = Math.max(1, mainAvail / cardHeight);
            applyCardSizeToAll(cardWidth, cardHeight);
            cgvMain.setLineLimit(rows, Constants.DECK_WIDTH_COUNT, Constants.DECK_WIDTH_MAX_COUNT);
            notifyDeckChanged();
            return;
        }
        int totalAvail = mainAvail + extraAvail + sideAvail;
        int wByCol = availWidth / Constants.DECK_WIDTH_COUNT;
        int wByH = totalAvail > 0 ? (int) ((totalAvail / 6f) / ratio) : Integer.MAX_VALUE;
        int cardWidth = Math.max(1, Math.min(wByCol, wByH));
        int cardHeight = Math.max(1, (int) (cardWidth * ratio));
        savedNormalCardWidth = cardWidth;
        savedNormalCardHeight = cardHeight;
        applyCardSizeToAll(cardWidth, cardHeight);
        cgvMain.setLineLimit(4, 10, 15);
        applyGroupExactHeight(cgvMain, cardHeight * 4);
        applyGroupExactHeight(cgvExtra, cardHeight);
        applyGroupExactHeight(cgvSide, cardHeight);
        notifyDeckChanged();
    }

    private void applyCardSizeToAll(int w, int h) {
        cgvMain.setCardSize(w, h);
        cgvExtra.setCardSize(w, h);
        cgvSide.setCardSize(w, h);
        cardSearcherManager.setCardSize(w, h);
    }

    private void applyGroupExactHeight(CardGroupView view, int contentHeight) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        lp.height = contentHeight + view.getPaddingTop() + view.getPaddingBottom();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).weight = 0;
        }
        view.setLayoutParams(lp);
    }

    /**
     * 切换卡包展示模式：卡包卡组（ygocore/pack）隐藏额外/副卡组的统计行与网格，
     * 主卡组网格铺满剩余高度，行数与最大数量随高度动态计算（不再限制4行/60张）。
     */
    private void applyPackMode(boolean packMode) {
        if (isPackMode == packMode) return;
        isPackMode = packMode;
        if (tvLabelMainDeck != null) {
            tvLabelMainDeck.setText(mStringManager.getSystemString(packMode ? 1477 : 1330, "主卡组:"));
        }
        int vis = packMode ? View.GONE : View.VISIBLE;
        if (layoutExtraStats != null) layoutExtraStats.setVisibility(vis);
        if (cgvExtra != null) cgvExtra.setVisibility(vis);
        if (layoutSideStats != null) layoutSideStats.setVisibility(vis);
        if (cgvSide != null) cgvSide.setVisibility(vis);
        if (packMode) {
            setMainGridFillHeight();
        }
        cardSearcherManager.setDragState(touchSlop, isReadonly || isPackMode);
        requestDeckCardSizeUpdate();
    }

    //卡包模式下主卡组网格铺满剩余高度（height=0dp + weight=1），普通模式由applyGroupExactHeight恢复定高
    private void setMainGridFillHeight() {
        if (cgvMain == null) return;
        ViewGroup.LayoutParams lp = cgvMain.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            lp.height = 0;
            ((LinearLayout.LayoutParams) lp).weight = 1;
            cgvMain.setLayoutParams(lp);
        }
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
        updateDeckCounts();
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
            btnDeckManager.setOnClickListener(v -> {
                if (isModified && !isReadonly) {
                    showConfirmDialog("此操作将放弃对当前卡组的修改，是否继续？",
                            () -> {
                                if (deckSelectorDialog != null)
                                    deckSelectorDialog.show(btnDeckManager);
                            });
                } else {
                    if (deckSelectorDialog != null) deckSelectorDialog.show(btnDeckManager);
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
                applyPackMode(packMode);
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
    private int getCardCredit(Card card) {
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
    }

    //副卡组替换完成/取消后退出副卡组模式
    public void exitSideMode() {
        isSiding = false;
        if (layoutDeckInfoPanel != null) layoutDeckInfoPanel.setVisibility(View.VISIBLE);
        if (layoutFilterPanel != null) layoutFilterPanel.setVisibility(View.VISIBLE);
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
        showConfirmDialog(deckName + "\n" + DataManager.get().getStringManager().getSystemString(1337, "是否删除这个卡组？"), () -> {
            File deckFile = new File(currentDeckFilePath);
            if (deckFile.exists()) {
                deckFile.delete();
                currentDeckFilePath = "";
                currentDeckName = "";
                currentDeck.mainCards.clear();
                currentDeck.extraCards.clear();
                currentDeck.sideCards.clear();
                notifyDeckChanged();
                isModified = false;
                updateDeckManagerButtonText();
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
        }
    }

    private int getCardClassRank(Card card) {
        return Card.isType(card.Type, CardType.Monster) ? 0 : Card.isType(card.Type, CardType.Spell) ? 1 : Card.isType(card.Type, CardType.Trap) ? 2 : 3;
    }

    /**
     * 子类排序：
     * 怪兽：通常→效果→仪式→融合→同调→超量→连接
     * 魔法：通常→仪式→速攻→永续→装备→场地
     * 陷阱：通常→永续→反击
     */
    private int getCardSubTypeRank(Card card, int classRank) {
        if (classRank == 0) {
            if (Card.isType(card.Type, CardType.Link)) return 6;
            if (Card.isType(card.Type, CardType.Xyz)) return 5;
            if (Card.isType(card.Type, CardType.Synchro)) return 4;
            if (Card.isType(card.Type, CardType.Fusion)) return 3;
            if (Card.isType(card.Type, CardType.Ritual)) return 2;
            if (Card.isType(card.Type, CardType.Normal)) return 0;
            return 1;
        }
        if (classRank == 1) {
            if (Card.isType(card.Type, CardType.Ritual)) return 1;
            if (Card.isType(card.Type, CardType.QuickPlay)) return 2;
            if (Card.isType(card.Type, CardType.Continuous)) return 3;
            if (Card.isType(card.Type, CardType.Equip)) return 4;
            if (Card.isType(card.Type, CardType.Field)) return 5;
            return 0;
        }
        if (classRank == 2) {
            if (Card.isType(card.Type, CardType.Continuous)) return 1;
            if (Card.isType(card.Type, CardType.Counter)) return 2;
            return 0;
        }
        return 0;
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

    private void notifyDeckChanged() {
        updateDeckCounts();
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

    private void updateDeckCounts() {
        currentDeck.syncCounts();
        int mainCount = currentDeck.getMainCount();
        int extraCount = currentDeck.getExtraCount();
        int sideCount = currentDeck.getSideCount();
        boolean isGenesys = AppsSettings.get().getGenesysMode() == 1
                && mLimitList != null && mLimitList.getCreditLimits() != null;
        if (tvMainCountNum != null) tvMainCountNum.setText(String.valueOf(mainCount));
        if (tvExtraCountNum != null) tvExtraCountNum.setText(String.valueOf(extraCount));
        if (tvSideCountNum != null) tvSideCountNum.setText(String.valueOf(sideCount));
        if (llGenesysScoreboard != null)
            llGenesysScoreboard.setVisibility(isGenesys ? View.VISIBLE : View.GONE);
        if (isGenesys) {
            int creditLimit = mLimitList.getCreditLimits();
            int creditCount = 0;
            for (Card c : currentDeck.getMainCards()) creditCount += getCardCredit(c);
            for (Card c : currentDeck.getExtraCards()) creditCount += getCardCredit(c);
            for (Card c : currentDeck.getSideCards()) creditCount += getCardCredit(c);
            int creditRemain = creditLimit - creditCount;
            if (tvCreditLimit != null) tvCreditLimit.setText(String.valueOf(creditLimit));
            if (tvCreditCount != null) {
                tvCreditCount.setText(String.valueOf(creditCount));
                tvCreditCount.setTextColor(creditCount > creditLimit ? Color.RED : Color.WHITE);
            }
            if (tvCreditRemain != null) {
                tvCreditRemain.setText(String.valueOf(creditRemain));
                tvCreditRemain.setTextColor(creditRemain < 0 ? Color.RED : Color.WHITE);
            }
        }
        int[] mainC = countByType(currentDeck.getMainCards(), false);
        setTextIfNotNull(tvMainMonsterCount, mainC[0]);
        setTextIfNotNull(tvMainSpellCount, mainC[1]);
        setTextIfNotNull(tvMainTrapCount, mainC[2]);
        int[] extraC = countByType(currentDeck.getExtraCards(), true);
        setTextIfNotNull(tvExtraFusionCount, extraC[0]);
        setTextIfNotNull(tvExtraSynchroCount, extraC[1]);
        setTextIfNotNull(tvExtraXyzCount, extraC[2]);
        setTextIfNotNull(tvExtraLinkCount, extraC[3]);
        int[] sideC = countByType(currentDeck.getSideCards(), false);
        setTextIfNotNull(tvSideMonsterCount, sideC[0]);
        setTextIfNotNull(tvSideSpellCount, sideC[1]);
        setTextIfNotNull(tvSideTrapCount, sideC[2]);
    }

    private int[] countByType(List<Card> cards, boolean isExtra) {
        if (isExtra) {
            int fu = 0, sy = 0, xy = 0, li = 0;
            for (Card c : cards) {
                if (Card.isType(c.Type, CardType.Fusion)) fu++;
                else if (Card.isType(c.Type, CardType.Synchro)) sy++;
                else if (Card.isType(c.Type, CardType.Xyz)) xy++;
                else if (Card.isType(c.Type, CardType.Link)) li++;
            }
            return new int[]{fu, sy, xy, li};
        }
        int mo = 0, sp = 0, tr = 0;
        for (Card c : cards) {
            if (Card.isType(c.Type, CardType.Monster)) mo++;
            else if (Card.isType(c.Type, CardType.Spell)) sp++;
            else if (Card.isType(c.Type, CardType.Trap)) tr++;
        }
        return new int[]{mo, sp, tr};
    }

    private void setTextIfNotNull(TextView tv, int val) {
        if (tv != null) tv.setText(String.valueOf(val));
    }

    /**
     * 统计当前卡组（主+额外+副）已使用的起源点数合计，
     * 积分查找与其他模块一致：优先按Code，找不到再按Alias。
     */
    private int getDeckCreditCount() {
        if (mLimitList == null || mLimitList.getCredits() == null) return 0;
        int total = 0;
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(currentDeck.getMainCards());
        allCards.addAll(currentDeck.getExtraCards());
        allCards.addAll(currentDeck.getSideCards());
        for (Card card : allCards) {
            total += getCardCredit(card);
        }
        return total;
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

    // === 拖放：网格内换位、跨网格移动、搜索结果拖入、拖回搜索区删除 ===

    private void setupDragAndDrop() {
        touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        cardSearcherManager.setDragState(touchSlop, isReadonly || isPackMode);
        dragHelper.addDropTarget(cgvMain);
        dragHelper.addDropTarget(cgvExtra);
        dragHelper.addDropTarget(cgvSide);
        if (rootView != null) {
            View rvSearchResults = rootView.findViewById(R.id.rv_deck_search_results);
            if (rvSearchResults != null) dragHelper.addDropTarget(rvSearchResults);
        }
    }

    /**
     * 应用内自定义拖拽的落点回调：按落点目标完成卡片的移动/新增/删除。
     * 来自卡组网格的卡先移出原位再插入落点（复用类型/数量/禁限校验），校验失败还原原位。
     */
    @Override
    public void onCardDrop(View target, DeckInfo.Type source, int index, Card card, float rawX, float rawY) {
        if (card == null || isReadonly) return;

        View searchRV = cardSearcherManager.getSearchRecyclerView();
        if (target == searchRV) {
            if (source != null) {
                List<Card> list = getDeckList(source);
                if (index >= 0 && index < list.size()) {
                    list.remove(index);
                    isModified = true;
                    notifyDeckChanged();
                }
            }
            return;
        }

        if (!(target instanceof CardGroupView)) return;
        DeckInfo.Type targetType;
        if (target == cgvMain) targetType = DeckInfo.Type.Main;
        else if (target == cgvExtra) targetType = DeckInfo.Type.Extra;
        else targetType = DeckInfo.Type.Side;

        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        int dropIndex = ((CardGroupView) target).getIndexByPosition(rawX - loc[0], rawY - loc[1]);

        if (source != null) {
            List<Card> sourceList = getDeckList(source);
            if (index < 0 || index >= sourceList.size()) return;
            Card moved = sourceList.remove(index);

            if (source == targetType) {
                int insert = (index < dropIndex) ? dropIndex - 1 : dropIndex;
                insert = Math.max(0, Math.min(insert, sourceList.size()));
                sourceList.add(insert, moved);
                isModified = true;
                notifyDeckChanged();
                return;
            }

            currentDeck.syncCounts();
            if (!moveToDeck(targetType, moved, dropIndex)) {
                sourceList.add(Math.min(index, sourceList.size()), moved);
                notifyDeckChanged();
            }
            return;
        }

        //搜索结果拖入：直接插入目标网格
        pushToDeck(targetType, card, dropIndex);
    }

    /**
     * 跨卡组移动：跳过checkLimit禁限/分数校验，仅校验卡片类型兼容性和目标卡组容量。
     * 卡片已在卡组中（非新增），移动不改变全局同名卡总数和GeneSys总分。
     */
    private boolean moveToDeck(DeckInfo.Type type, Card card, int seq) {
        if (card == null) return false;
        if (type == DeckInfo.Type.Main && Card.isExtraCard(card.Type)) return false;
        if (type == DeckInfo.Type.Extra && !Card.isExtraCard(card.Type)) return false;
        if (type == DeckInfo.Type.Main && !isPackMode
                && currentDeck.getMainCount() >= Constants.DECK_MAIN_MAX) return false;
        if (type == DeckInfo.Type.Extra
                && currentDeck.getExtraCount() >= Constants.DECK_EXTRA_MAX) return false;
        if (type == DeckInfo.Type.Side
                && currentDeck.getSideCount() >= Constants.DECK_SIDE_MAX) return false;
        List<Card> list = getDeckList(type);
        int insert = Math.max(0, Math.min(seq, list.size()));
        list.add(insert, card);
        currentDeck.syncCounts();
        isModified = true;
        notifyDeckChanged();
        return true;
    }

    private boolean pushToDeck(DeckInfo.Type type, Card card, int seq) {
        if (type == DeckInfo.Type.Main) return pushMain(card, seq);
        if (type == DeckInfo.Type.Extra) return pushExtra(card, seq);
        return pushSide(card, seq);
    }

    private List<Card> getDeckList(DeckInfo.Type type) {
        if (type == DeckInfo.Type.Main) return currentDeck.mainCards;
        if (type == DeckInfo.Type.Extra) return currentDeck.extraCards;
        return currentDeck.sideCards;
    }

}
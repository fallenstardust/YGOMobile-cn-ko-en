package cn.garymb.ygomobile.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck.CardTypeImage;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.dialogs.DeckSelectorDialog;
import cn.garymb.ygomobile.ui.dialogs.EffectCategoryPopupWindow;
import cn.garymb.ygomobile.ui.dialogs.LinkMarkerPopupWindow;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import cn.garymb.ygomobile.ui.widget.CardView;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;
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

        //副卡组替换完成：把替换后的卡组发给服务器（对应C++ DuelClient::SendUpdateDeck）
        void onSideDeckFinished(List<Integer> main, List<Integer> extra, List<Integer> side);
    }

    private final Activity activity;
    private final ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CardLoader cardLoader;
    private DeckEditorListener listener;

    private final DeckInfo currentDeck;
    private final List<Card> searchResults;
    private final Random random = new Random();
    private boolean isModified = false;
    private boolean isReadonly = false;
    //当前加载的是否为卡包展示卡组（ygocore/pack）：隐藏额外/副卡组，主网格铺满
    private boolean isPackMode = false;
    //副卡组替换模式状态：对应C++ is_siding 与 pre_mainc/pre_extrac/pre_sidec
    private boolean isSiding = false;
    private int preMainCount = 0, preExtraCount = 0, preSideCount = 0;
    private int prevDeck = 0;

    private int savedNormalCardWidth = 0;
    private int savedNormalCardHeight = 0;

    private int filterType = 0;
    private int filterType2 = 0;
    private int filterAttrib = 0;
    private int filterRace = 0;
    private int filterAtkType = 0;
    private int filterAtk = 0;
    private int filterDefType = 0;
    private int filterDef = 0;
    private int filterLvType = 0;
    private int filterLv = 0;
    private int filterSclType = 0;
    private int filterScl = 0;
    private int filterLm = 0;
    private long filterEffect = 0;
    private int filterMarks = 0;
    private int sortType = 0;

    private View rootView;
    private CardDetailPanel cardDetailPanel;
    private TextView tvMainCountNum, tvExtraCountNum, tvSideCountNum, tvSearchResult;
    private View llGenesysScoreboard;
    private TextView tvCreditLimit, tvCreditCount, tvCreditRemain;
    private TextView tvMainMonsterCount, tvMainSpellCount, tvMainTrapCount;
    private TextView tvExtraFusionCount, tvExtraSynchroCount, tvExtraXyzCount, tvExtraLinkCount;
    private TextView tvSideMonsterCount, tvSideSpellCount, tvSideTrapCount;
    //筛选面板与卡组分区标题标签：文字取自StringManager的systemString，对齐C++ GetSysString索引
    private TextView tvLabelDeck, tvLabelType, tvLabelAttribute, tvLabelRace;
    private TextView tvLabelStar, tvLabelScale, tvLabelLimit, tvLabelAttack, tvLabelDefense, tvLabelKeyword;
    private TextView tvLabelMainDeck, tvLabelExtraDeck, tvLabelSideDeck;
    private String searchResultPrefix = "搜索结果:";
    private CardGroupView cgvMain, cgvExtra, cgvSide;
    private View layoutExtraStats, layoutSideStats;
    private View layoutDeckInfoPanel, layoutFilterPanel;
    private RecyclerView rvSearchResults;
    private Spinner spinnerFilterType;
    private Spinner spinnerFilterType2, spinnerFilterAttribute, spinnerFilterRace, spinnerFilterLimit;
    private Spinner spinnerSortType;
    private SimpleSpinnerAdapter attrAdapter, raceAdapter;
    private EditText etAttack, etDefense, etStar, etScale, etKeyword;
    private EditText etDeckName;
    private Button btnSave, btnSaveAs, btnShuffle, btnSort, btnClear, btnDelete, btnExit;
    private Button btnFilterEffect;
    private Button btnFilterSearch, btnFilterClear;
    private Button btnFilterMarks;
    private Button btnDeckManager;

    //副卡组替换模式按钮：对应C++ btnSideOK/btnSideShuffle/btnSideSort/btnSideReload
    private Button btnSideFinish, btnSideShuffle, btnSideSort, btnSideReset;

    private DeckCardAdapter searchAdapter;

    private ImageTop mImageTop;
    private CardTypeImage mCardTypeImage;
    private ImageView ivMainMonsterType, ivMainSpellType, ivMainTrapType;
    private ImageView ivExtraFusionType, ivExtraSynchroType, ivExtraXyzType, ivExtraLinkType;
    private ImageView ivSideMonsterType, ivSideSpellType, ivSideTrapType;

    private LimitList mLimitList;

    private LinkMarkerPopupWindow linkMarkerPopup;
    private EffectCategoryPopupWindow effectCategoryPopup;

    private String currentDeckCategoryName = "";
    private String currentDeckName = "";
    private String currentDeckFilePath = "";

    private DeckSelectorDialog deckSelectorDialog;
    private int touchSlop;
    private final CardDragHelper dragHelper;

    public DeckEditorManager(Activity activity, ImageLoader imageLoader, CardDetailPanel cardDetailPanel) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        this.cardDetailPanel = cardDetailPanel;
        this.cardLoader = new CardLoader();
        this.currentDeck = new DeckInfo();
        this.searchResults = new ArrayList<>();
        this.dragHelper = new CardDragHelper(activity, this);
    }

    public void setListener(DeckEditorListener listener) {
        this.listener = listener;
    }

    public void initialize(View rootView) {
        this.rootView = rootView;
        bindViews(rootView);
        setupLabels();
        setupRecyclerViews();
        setupDragAndDrop();
        setupSpinners();
        setupButtons();
        setupDeckSelectorDialog();
        loadLastDeck();
        updateDeckCounts();
        isModified = false;
    }

    public void terminate() {
        if (isModified && !isReadonly) {
            showConfirmDialog("此操作将放弃对当前卡组的修改，是否继续？", () -> {
                doTerminate();
            });
        } else {
            doTerminate();
        }
    }

    private void doTerminate() {
        saveLastCategoryAndDeck();
        if (listener != null) {
            listener.onExitEditor();
        }
    }

    private void bindViews(View root) {
        // 卡片详情面板委托给 CardDetailPanel，不再重复绑定
        // 卡组操作按钮复用 activity_ygo_game.xml 的 layout_deck_control
        btnShuffle = activity.findViewById(R.id.btn_deck_shuffle);
        btnSort = activity.findViewById(R.id.btn_deck_sort);
        btnClear = activity.findViewById(R.id.btn_deck_clear);
        btnDelete = activity.findViewById(R.id.btn_deck_delete);
        btnExit = activity.findViewById(R.id.btn_deck_exit);
        // 以下为卡组编辑器自身布局 (layout_deck_editor.xml)
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
        tvSearchResult = root.findViewById(R.id.tv_deck_search_result);

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

        rvSearchResults = root.findViewById(R.id.rv_deck_search_results);
        btnDeckManager = root.findViewById(R.id.btn_deck_manager);
        etDeckName = root.findViewById(R.id.et_deck_name);
        spinnerFilterType = root.findViewById(R.id.spinner_filter_type);
        spinnerFilterType2 = root.findViewById(R.id.spinner_filter_type2);
        spinnerFilterAttribute = root.findViewById(R.id.spinner_filter_attribute);
        spinnerFilterRace = root.findViewById(R.id.spinner_filter_race);
        spinnerFilterLimit = root.findViewById(R.id.spinner_filter_limit);
        spinnerSortType = root.findViewById(R.id.spinner_sort_type);
        etAttack = root.findViewById(R.id.et_filter_attack);
        etDefense = root.findViewById(R.id.et_filter_defense);
        etStar = root.findViewById(R.id.et_filter_star);
        etScale = root.findViewById(R.id.et_filter_scale);
        etKeyword = root.findViewById(R.id.et_filter_keyword);
        btnSave = root.findViewById(R.id.btn_deck_save);
        btnSaveAs = root.findViewById(R.id.btn_deck_save_as);
        btnFilterEffect = root.findViewById(R.id.btn_filter_effect);
        btnFilterMarks = root.findViewById(R.id.btn_filter_marks);
        btnFilterSearch = root.findViewById(R.id.btn_filter_search);
        btnFilterClear = root.findViewById(R.id.btn_filter_clear);

        btnSideFinish = root.findViewById(R.id.btn_side_finish);
        btnSideShuffle = root.findViewById(R.id.btn_side_shuffle);
        btnSideSort = root.findViewById(R.id.btn_side_sort);
        btnSideReset = root.findViewById(R.id.btn_side_reset);

    }

    /**
     * 初始化各标签TextView的本地化文字，取自StringManager的systemString，
     * 索引对齐C++端game.cpp（筛选面板）与drawing.cpp（卡组标题）的GetSysString。
     * 注："子类"标签C++端无对应label、起源记分板"限/计/余/GENESYS"在C++端为图片绘制，
     * 均无文字systemString索引，故保留布局中的硬编码文字。
     */
    private void setupLabels() {
        StringManager sm = DataManager.get().getStringManager();

        if (btnSideFinish != null) {
            btnSideFinish.setText(sm.getSystemString(1334, "副卡组替换完成"));
            btnSideFinish.setOnClickListener(v -> sideFinish());
        }
        if (btnSideShuffle != null) {
            btnSideShuffle.setText(sm.getSystemString(1307, "打乱"));
            btnSideShuffle.setOnClickListener(v -> shuffleDeck());
        }
        if (btnSideSort != null) {
            btnSideSort.setText(sm.getSystemString(1305, "排序"));
            btnSideSort.setOnClickListener(v -> sortDeck());
        }
        if (btnSideReset != null) {
            btnSideReset.setText(sm.getSystemString(1309, "重置"));
            btnSideReset.setOnClickListener(v -> sideReset());
        }

        if (tvLabelDeck != null) tvLabelDeck.setText(sm.getSystemString(1300, "卡组:"));
        if (tvLabelType != null) tvLabelType.setText(sm.getSystemString(1311, "种类:"));
        if (tvLabelAttribute != null) tvLabelAttribute.setText(sm.getSystemString(1319, "属性:"));
        if (tvLabelRace != null) tvLabelRace.setText(sm.getSystemString(1321, "种族:"));
        if (tvLabelStar != null) tvLabelStar.setText(sm.getSystemString(1324, "星数:"));
        if (tvLabelScale != null) tvLabelScale.setText(sm.getSystemString(1336, "刻度:"));
        if (tvLabelLimit != null) tvLabelLimit.setText(sm.getSystemString(1315, "禁限:"));
        if (tvLabelAttack != null) tvLabelAttack.setText(sm.getSystemString(1322, "攻击:"));
        if (tvLabelDefense != null) tvLabelDefense.setText(sm.getSystemString(1323, "守备:"));
        if (tvLabelKeyword != null) tvLabelKeyword.setText(sm.getSystemString(1325, "关键字:"));
        if (tvLabelMainDeck != null)
            tvLabelMainDeck.setText(sm.getSystemString(isPackMode ? 1477 : 1330, "主卡组:"));
        if (tvLabelExtraDeck != null) tvLabelExtraDeck.setText(sm.getSystemString(1331, "额外卡组:"));
        if (tvLabelSideDeck != null) tvLabelSideDeck.setText(sm.getSystemString(1332, "副卡组:"));
        searchResultPrefix = sm.getSystemString(1333, "搜索结果:");
    }

    private void setupRecyclerViews() {
        mLimitList = AppsSettings.get().getGenesysMode() == 1
                ? cardLoader.getGenesysLimitList()
                : cardLoader.getLimitList();
        mImageTop = new ImageTop(activity);
        mCardTypeImage = new CardTypeImage(activity);

        if (mCardTypeImage == null) return;
        if (ivMainMonsterType != null) ivMainMonsterType.setImageBitmap(mCardTypeImage.monster);
        if (ivMainSpellType != null) ivMainSpellType.setImageBitmap(mCardTypeImage.spell);
        if (ivMainTrapType != null) ivMainTrapType.setImageBitmap(mCardTypeImage.trap);
        if (ivExtraFusionType != null) ivExtraFusionType.setImageBitmap(mCardTypeImage.fusion);
        if (ivExtraSynchroType != null) ivExtraSynchroType.setImageBitmap(mCardTypeImage.synchro);
        if (ivExtraXyzType != null) ivExtraXyzType.setImageBitmap(mCardTypeImage.xyz);
        if (ivExtraLinkType != null) ivExtraLinkType.setImageBitmap(mCardTypeImage.link);
        if (ivSideMonsterType != null) ivSideMonsterType.setImageBitmap(mCardTypeImage.monster);
        if (ivSideSpellType != null) ivSideSpellType.setImageBitmap(mCardTypeImage.spell);
        if (ivSideTrapType != null) ivSideTrapType.setImageBitmap(mCardTypeImage.trap);

        cgvMain.setImageLoader(imageLoader);
        cgvMain.setLineLimit(4, 10, 15);

        cgvExtra.setImageLoader(imageLoader);
        cgvExtra.setLineLimit(1, 10, 15);

        cgvSide.setImageLoader(imageLoader);
        cgvSide.setLineLimit(1, 10, 15);

        requestDeckCardSizeUpdate();

        rvSearchResults.setLayoutManager(new LinearLayoutManager(activity));
        searchAdapter = new DeckCardAdapter(imageLoader, this, null, dragHelper);
        searchAdapter.setLimitList(mLimitList);
        rvSearchResults.setAdapter(searchAdapter);
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

        if (isPackMode) {
            int cardWidth, cardHeight;
            if (savedNormalCardWidth > 0 && savedNormalCardHeight > 0) {
                cardWidth = savedNormalCardWidth;
                cardHeight = savedNormalCardHeight;
            } else {
                int extraAvail = extraHeight > 0 && cgvExtra != null
                        ? Math.max(0, extraHeight - cgvExtra.getPaddingTop() - cgvExtra.getPaddingBottom()) : 0;
                int sideAvail = sideHeight > 0 && cgvSide != null
                        ? Math.max(0, sideHeight - cgvSide.getPaddingTop() - cgvSide.getPaddingBottom()) : 0;
                int totalAvail = mainAvail + extraAvail + sideAvail;
                int widthByColumn = availWidth / Constants.DECK_WIDTH_COUNT;
                int widthByHeight = totalAvail > 0 ? (int) ((totalAvail / 6f) / ratio) : Integer.MAX_VALUE;
                cardWidth = Math.max(1, Math.min(widthByColumn, widthByHeight));
                cardHeight = Math.max(1, (int) (cardWidth * ratio));
            }

            int rows = Math.max(1, mainAvail / cardHeight);
            cgvMain.setCardSize(cardWidth, cardHeight);
            if (cgvExtra != null) cgvExtra.setCardSize(cardWidth, cardHeight);
            if (cgvSide != null) cgvSide.setCardSize(cardWidth, cardHeight);
            if (searchAdapter != null) searchAdapter.setCardSize(cardWidth, cardHeight);
            cgvMain.setLineLimit(rows, Constants.DECK_WIDTH_COUNT, Constants.DECK_WIDTH_MAX_COUNT);
            notifyDeckChanged();
            return;
        }

        int extraAvail = extraHeight > 0 && cgvExtra != null
                ? Math.max(0, extraHeight - cgvExtra.getPaddingTop() - cgvExtra.getPaddingBottom()) : 0;
        int sideAvail = sideHeight > 0 && cgvSide != null
                ? Math.max(0, sideHeight - cgvSide.getPaddingTop() - cgvSide.getPaddingBottom()) : 0;

        int totalAvail = mainAvail + extraAvail + sideAvail;
        int totalLines = 6;

        int widthByColumn = availWidth / Constants.DECK_WIDTH_COUNT;
        int widthByHeight = totalAvail > 0 ? (int) ((totalAvail / (float) totalLines) / ratio) : Integer.MAX_VALUE;

        int cardWidth = Math.max(1, Math.min(widthByColumn, widthByHeight));
        int cardHeight = Math.max(1, (int) (cardWidth * ratio));

        savedNormalCardWidth = cardWidth;
        savedNormalCardHeight = cardHeight;

        cgvMain.setCardSize(cardWidth, cardHeight);
        cgvExtra.setCardSize(cardWidth, cardHeight);
        cgvSide.setCardSize(cardWidth, cardHeight);
        if (searchAdapter != null) searchAdapter.setCardSize(cardWidth, cardHeight);

        cgvMain.setLineLimit(4, 10, 15);

        applyGroupExactHeight(cgvMain, cardHeight * 4);
        applyGroupExactHeight(cgvExtra, cardHeight);
        applyGroupExactHeight(cgvSide, cardHeight);

        notifyDeckChanged();
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
            StringManager sm = DataManager.get().getStringManager();
            tvLabelMainDeck.setText(sm.getSystemString(packMode ? 1477 : 1330, "主卡组:"));
        }
        int vis = packMode ? View.GONE : View.VISIBLE;
        if (layoutExtraStats != null) layoutExtraStats.setVisibility(vis);
        if (cgvExtra != null) cgvExtra.setVisibility(vis);
        if (layoutSideStats != null) layoutSideStats.setVisibility(vis);
        if (cgvSide != null) cgvSide.setVisibility(vis);
        if (packMode) {
            setMainGridFillHeight();
        }
        if (searchAdapter != null) searchAdapter.setDragState(touchSlop, isReadonly || isPackMode);
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
        mLimitList = AppsSettings.get().getGenesysMode() == 1
                ? cardLoader.getGenesysLimitList()
                : cardLoader.getLimitList();
        if (cgvMain != null) cgvMain.updateTopImage(mImageTop, mLimitList);
        if (cgvExtra != null) cgvExtra.updateTopImage(mImageTop, mLimitList);
        if (cgvSide != null) cgvSide.updateTopImage(mImageTop, mLimitList);
        if (searchAdapter != null) searchAdapter.setLimitList(mLimitList);
        //模式或禁卡表切换后同步刷新起源点数记分板的显隐与数值
        updateDeckCounts();
    }

    private void setupSpinners() {
        StringManager sm = DataManager.get().getStringManager();

        List<SimpleSpinnerItem> typeItems = new ArrayList<>();
        typeItems.add(new SimpleSpinnerItem(0, "(无)"));
        typeItems.add(new SimpleSpinnerItem(1, "怪兽"));
        typeItems.add(new SimpleSpinnerItem(2, "魔法"));
        typeItems.add(new SimpleSpinnerItem(3, "陷阱"));
        SimpleSpinnerAdapter typeAdapter = new SimpleSpinnerAdapter(activity);
        typeAdapter.setColor(Color.WHITE);
        typeAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        typeAdapter.setTextSize(8f);
        typeAdapter.set(typeItems);
        if (spinnerFilterType != null) {
            spinnerFilterType.setAdapter(typeAdapter);
            //主类型切换时按C++ COMBOBOX_MAINTYPE重建子类选项
            spinnerFilterType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    updateType2Spinner(position);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    updateType2Spinner(0);
                }
            });
        }

        // 子类spinner_filter_type2初始只含（N/A），随主类型动态重建
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);

        //子类型选中"连接"时单独禁用守备力输入框（连接怪兽无守备力）
        if (spinnerFilterType2 != null) {
            spinnerFilterType2.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    updateDefenseEditState();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    updateDefenseEditState();
                }
            });
        }

        List<SimpleSpinnerItem> attrItems = new ArrayList<>();
        attrItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        for (CardAttribute attr : CardAttribute.values()) {
            attrItems.add(new SimpleSpinnerItem(attr.getId(),
                    sm.getSystemString(attr.getLanguageIndex(), attr.name())));
        }
        attrAdapter = new SimpleSpinnerAdapter(activity);
        attrAdapter.setColor(Color.WHITE);
        attrAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        attrAdapter.setTextSize(8f);
        attrAdapter.set(attrItems);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setAdapter(attrAdapter);

        List<SimpleSpinnerItem> raceItems = new ArrayList<>();
        raceItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        for (CardRace race : CardRace.values()) {
            raceItems.add(new SimpleSpinnerItem(race.value(),
                    sm.getSystemString(race.getLanguageIndex(), race.name())));
        }
        raceAdapter = new SimpleSpinnerAdapter(activity);
        raceAdapter.setColor(Color.WHITE);
        raceAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        raceAdapter.setTextSize(8f);
        raceAdapter.set(raceItems);
        if (spinnerFilterRace != null) spinnerFilterRace.setAdapter(raceAdapter);
        // 子类spinner_filter_type2初始只含（N/A），随主类型动态重建（需在属性/种族adapter创建后调用）
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);

        List<SimpleSpinnerItem> limitItems = new ArrayList<>();
        limitItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        limitItems.add(new SimpleSpinnerItem(LimitType.Forbidden.getId(),
                sm.getSystemString(LimitType.Forbidden.getLanguageIndex(), LimitType.Forbidden.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.Limit.getId(),
                sm.getSystemString(LimitType.Limit.getLanguageIndex(), LimitType.Limit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.SemiLimit.getId(),
                sm.getSystemString(LimitType.SemiLimit.getLanguageIndex(), LimitType.SemiLimit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.GeneSys.getId(),
                sm.getSystemString(LimitType.GeneSys.getLanguageIndex(), LimitType.GeneSys.name())));
        limitItems.add(new SimpleSpinnerItem(6, sm.getSystemString(1481, "OCG")));
        limitItems.add(new SimpleSpinnerItem(7, sm.getSystemString(1482, "TCG")));
        limitItems.add(new SimpleSpinnerItem(8, sm.getSystemString(1483, "简体中文")));
        limitItems.add(new SimpleSpinnerItem(9, sm.getSystemString(1484, "自定义")));
        limitItems.add(new SimpleSpinnerItem(10, sm.getSystemString(1487, "OCG独有")));
        limitItems.add(new SimpleSpinnerItem(11, sm.getSystemString(1488, "TCG独有")));
        limitItems.add(new SimpleSpinnerItem(12, sm.getSystemString(1485, "无独有卡")));
        SimpleSpinnerAdapter limitAdapter = new SimpleSpinnerAdapter(activity);
        limitAdapter.setColor(Color.WHITE);
        limitAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));

        limitAdapter.setTextSize(8f);
        limitAdapter.set(limitItems);
        if (spinnerFilterLimit != null) {
            spinnerFilterLimit.setAdapter(limitAdapter);
            //选中OCG/TCG/简体中文时，在卡组网格与搜索结果item底部显示对应赛制标识
            spinnerFilterLimit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    applyAvailDisplay(getSpinnerItemId(spinnerFilterLimit));
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    applyAvailDisplay(0);
                }
            });
        }

        List<SimpleSpinnerItem> sortItems = new ArrayList<>();
        sortItems.add(new SimpleSpinnerItem(0, "星数↑"));
        sortItems.add(new SimpleSpinnerItem(1, "攻击↑"));
        sortItems.add(new SimpleSpinnerItem(2, "守备↑"));
        sortItems.add(new SimpleSpinnerItem(3, "名称↓"));
        SimpleSpinnerAdapter sortAdapter = new SimpleSpinnerAdapter(activity);
        sortAdapter.setColor(Color.WHITE);
        sortAdapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        sortAdapter.setTextSize(8f);
        sortAdapter.set(sortItems);
        if (spinnerSortType != null) spinnerSortType.setAdapter(sortAdapter);
    }

    private void setupButtons() {
        if (btnExit != null) btnExit.setOnClickListener(v -> terminate());
        if (btnShuffle != null) btnShuffle.setOnClickListener(v -> shuffleDeck());
        if (btnSort != null) btnSort.setOnClickListener(v -> sortDeck());
        if (btnClear != null) btnClear.setOnClickListener(v -> clearDeck());
        if (btnDelete != null) btnDelete.setOnClickListener(v -> deleteDeck());
        if (btnSave != null) btnSave.setOnClickListener(v -> saveDeck());
        if (btnSaveAs != null) btnSaveAs.setOnClickListener(v -> saveDeckAs());
        if (btnFilterEffect != null)
            btnFilterEffect.setOnClickListener(v -> showEffectCategoryPopup());
        if (btnFilterMarks != null) btnFilterMarks.setOnClickListener(v -> showLinkMarkerPopup());
        if (btnFilterSearch != null) btnFilterSearch.setOnClickListener(v -> startFilter());
        if (btnFilterClear != null) btnFilterClear.setOnClickListener(v -> clearSearch());
    }

    /**
     * 按主类型（0=全部、1=怪兽、2=魔法、3=陷阱）重建子类spinner_filter_type2选项，
     * 选项文本取自StringManager，value为对应类型位组合，对齐C++ deck_con.cpp的COMBOBOX_MAINTYPE。
     */
    private void updateType2Spinner(int typePos) {
        if (spinnerFilterType2 == null) return;
        boolean enabled = typePos != 0;
        StringManager sm = DataManager.get().getStringManager();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        items.add(new SimpleSpinnerItem(0, sm.getSystemString(1080, "（N/A）")));
        long m = CardType.Monster.getId();
        switch (typePos) {
            case 1:
                items.add(new SimpleSpinnerItem(m | CardType.Normal.getId(), sm.getSystemString(1054, "通常")));
                items.add(new SimpleSpinnerItem(m | CardType.Effect.getId(), sm.getSystemString(1055, "效果")));
                items.add(new SimpleSpinnerItem(m | CardType.Fusion.getId(), sm.getSystemString(1056, "融合")));
                items.add(new SimpleSpinnerItem(m | CardType.Ritual.getId(), sm.getSystemString(1057, "仪式")));
                items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId(), sm.getSystemString(1063, "同调")));
                items.add(new SimpleSpinnerItem(m | CardType.Xyz.getId(), sm.getSystemString(1073, "超量")));
                items.add(new SimpleSpinnerItem(m | CardType.Pendulum.getId(), sm.getSystemString(1074, "灵摆")));
                items.add(new SimpleSpinnerItem(m | CardType.Link.getId(), sm.getSystemString(1076, "连接")));
                items.add(new SimpleSpinnerItem(m | CardType.Sp_Summon.getId(), sm.getSystemString(1075, "特殊召唤")));
                items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Tuner.getId(),
                        sm.getSystemString(1054, "通常") + "|" + sm.getSystemString(1062, "调整")));
                items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Pendulum.getId(),
                        sm.getSystemString(1054, "通常") + "|" + sm.getSystemString(1074, "灵摆")));
                items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId() | CardType.Tuner.getId(),
                        sm.getSystemString(1063, "同调") + "|" + sm.getSystemString(1062, "调整")));
                items.add(new SimpleSpinnerItem(m | CardType.Tuner.getId(), sm.getSystemString(1062, "调整")));
                items.add(new SimpleSpinnerItem(m | CardType.Gemini.getId(), sm.getSystemString(1061, "二重")));
                items.add(new SimpleSpinnerItem(m | CardType.Union.getId(), sm.getSystemString(1060, "同盟")));
                items.add(new SimpleSpinnerItem(m | CardType.Spirit.getId(), sm.getSystemString(1059, "灵魂")));
                items.add(new SimpleSpinnerItem(m | CardType.Flip.getId(), sm.getSystemString(1071, "反转")));
                items.add(new SimpleSpinnerItem(m | CardType.Toon.getId(), sm.getSystemString(1072, "卡通")));
                break;
            case 2:
                items.add(new SimpleSpinnerItem(CardType.Spell.getId(), sm.getSystemString(1054, "通常")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.QuickPlay.getId(), sm.getSystemString(1066, "速攻")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Continuous.getId(), sm.getSystemString(1067, "永续")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Ritual.getId(), sm.getSystemString(1057, "仪式")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Equip.getId(), sm.getSystemString(1068, "装备")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Field.getId(), sm.getSystemString(1069, "场地")));
                break;
            case 3:
                items.add(new SimpleSpinnerItem(CardType.Trap.getId(), sm.getSystemString(1054, "通常")));
                items.add(new SimpleSpinnerItem(CardType.Trap.getId() | CardType.Continuous.getId(), sm.getSystemString(1067, "永续")));
                items.add(new SimpleSpinnerItem(CardType.Trap.getId() | CardType.Counter.getId(), sm.getSystemString(1070, "反击")));
                break;
            default:
                break;
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(activity);
        adapter.setDropDownBackgroundColor(YGOUtil.c(R.color.ygopro_list_background));
        adapter.setColor(enabled ? Color.WHITE : Color.GRAY);
        adapter.setTextSize(8f);
        adapter.set(items);
        spinnerFilterType2.setAdapter(adapter);
        spinnerFilterType2.setSelection(0);
        //主类型选（无）时禁用子类spinner；属性/种族spinner及星数/刻度/ATK/DEF输入框仅在主类型为怪兽时启用
        boolean monsterEnabled = typePos == 1;
        setSpinnerEnabled(spinnerFilterType2, null, enabled);
        setSpinnerEnabled(spinnerFilterAttribute, attrAdapter, monsterEnabled);
        setSpinnerEnabled(spinnerFilterRace, raceAdapter, monsterEnabled);
        setEditTextEnabled(etStar, monsterEnabled);
        setEditTextEnabled(etScale, monsterEnabled);
        setEditTextEnabled(etAttack, monsterEnabled);
        updateDefenseEditState();
    }

    /**
     * 启用/禁用spinner：禁用时选项文字变灰、背景drawable整体染成灰白色，并重置选中项为（无）
     */
    private void setSpinnerEnabled(Spinner spinner, SimpleSpinnerAdapter adapter, boolean enabled) {
        if (spinner == null) return;
        spinner.setEnabled(enabled);
        if (!enabled) {
            spinner.setSelection(0);
        }
        if (adapter != null) {
            adapter.setColor(enabled ? Color.WHITE : Color.GRAY);
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * 启用/禁用EditText：禁用时清空内容并将文字变灰
     */
    private void setEditTextEnabled(android.widget.EditText et, boolean enabled) {
        if (et == null) return;
        et.setEnabled(enabled);
        if (!enabled) {
            et.setText("");
        }
        et.setBackground(activity.getDrawable(enabled ? R.drawable.ygopro_base_background : R.drawable.ygopro_unable_background));

    }

    /**
     * 守备力输入框状态：主类型非怪兽时禁用；主类型为怪兽但子类型为连接时也禁用（连接怪兽无守备力）
     */
    private void updateDefenseEditState() {
        boolean monster = spinnerFilterType != null && spinnerFilterType.getSelectedItemPosition() == 1;
        if (!monster) {
            setEditTextEnabled(etDefense, false);
            return;
        }
        long value = SimpleSpinnerAdapter.getSelect(spinnerFilterType2);
        boolean isLink = (value & CardType.Link.getId()) != 0;
        setEditTextEnabled(etDefense, !isLink);
    }

    private void setupDeckSelectorDialog() {
        deckSelectorDialog = new DeckSelectorDialog(activity);
        deckSelectorDialog.setIncludePackCategory(true);
        deckSelectorDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(String deckPath, String deckName, String categoryName) {
                applySelectedDeck(deckPath, deckName, categoryName);
            }

            @Override
            public void onDeckItemClicked(String deckPath, String deckName, String categoryName) {
                //点击卡组item即在编辑器中立即加载该卡组
                applySelectedDeck(deckPath, deckName, categoryName);
            }

            @Override
            public void onCancelled() {
            }
        });

        if (btnDeckManager != null) {
            btnDeckManager.setOnClickListener(v -> showDeckSelectorDialog());
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
                btnDeckManager.setText("卡组管理");
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
        if (card == null) return false;
        if (Card.isExtraCard(card.Type)) return false;
        if (!isPackMode && currentDeck.getMainCount() >= Constants.DECK_MAIN_MAX) return false;
        if (!checkLimit(card)) return false;
        boolean result;
        if (seq >= 0 && seq <= currentDeck.mainCards.size()) {
            result = currentDeck.addMainCards(seq, card, isPackMode);
        } else {
            result = currentDeck.addMainCards(card);
        }
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    // === 对应 deck_con.cpp: push_extra ===
    public boolean pushExtra(Card card, int seq) {
        if (card == null) return false;
        if (!Card.isExtraCard(card.Type)) return false;
        if (currentDeck.getExtraCount() >= Constants.DECK_EXTRA_MAX) return false;
        if (!checkLimit(card)) return false;
        boolean result;
        if (seq >= 0 && seq <= currentDeck.extraCards.size()) {
            result = currentDeck.addExtraCards(seq, card);
        } else {
            result = currentDeck.addExtraCards(card);
        }
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    // === 对应 deck_con.cpp: push_side ===
    public boolean pushSide(Card card, int seq) {
        if (card == null) return false;
        if (currentDeck.getSideCount() >= Constants.DECK_SIDE_MAX) return false;
        //与主/额外一致的禁限校验：主+额外+副中规则同名（getGameCode）数量已达禁限表上限、或GeneSys点数将超限时禁止投入副卡组
        if (!checkLimit(card)) return false;
        boolean result;
        if (seq >= 0 && seq <= currentDeck.sideCards.size()) {
            result = currentDeck.addSideCards(seq, card);
        } else {
            result = currentDeck.addSideCards(card);
        }
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    // === 对应 deck_con.cpp: pop_main ===
    public void popMain(int seq) {
        if (seq >= 0 && seq < currentDeck.mainCards.size()) {
            currentDeck.removeMain(seq);
            isModified = true;
            notifyDeckChanged();
        }
    }

    // === 对应 deck_con.cpp: pop_extra ===
    public void popExtra(int seq) {
        if (seq >= 0 && seq < currentDeck.extraCards.size()) {
            currentDeck.removeExtra(seq);
            isModified = true;
            notifyDeckChanged();
        }
    }

    // === 对应 deck_con.cpp: pop_side ===
    public void popSide(int seq) {
        if (seq >= 0 && seq < currentDeck.sideCards.size()) {
            currentDeck.removeSide(seq);
            isModified = true;
            notifyDeckChanged();
        }
    }

    // === 对应 deck_con.cpp: check_limit ===
    public boolean checkLimit(Card card) {
        if (card == null) return false;
        //规则同名卡code作为禁限与点数判断的统一口径（对应C++的get_duel_code）
        int gameCode = card.getGameCode();

        //禁限卡表决定最大投入数：禁止0/限制1/准限制2，其余默认3
        int limit = 3;
        if (mLimitList != null) {
            if (mLimitList.check(gameCode, gameCode, LimitType.Forbidden)) limit = 0;
            else if (mLimitList.check(gameCode, gameCode, LimitType.Limit)) limit = 1;
            else if (mLimitList.check(gameCode, gameCode, LimitType.SemiLimit)) limit = 2;
        }

        //统计主+额外+副中规则同名（getGameCode相同）的已有数量，已达上限则投入会超限
        int count = 0;
        for (Card c : currentDeck.mainCards) {
            if (c.getGameCode() == gameCode) count++;
        }
        for (Card c : currentDeck.extraCards) {
            if (c.getGameCode() == gameCode) count++;
        }
        for (Card c : currentDeck.sideCards) {
            if (c.getGameCode() == gameCode) count++;
        }
        if (count >= limit) return false;

        //GeneSys模式：全卡组（主+额外+副）起源点数合计加上新卡不得超上限
        if (mLimitList != null && mLimitList.getCreditLimits() != null) {
            if (getDeckCreditCount() + getCardCredit(card) > mLimitList.getCreditLimits()) {
                return false;
            }
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
        //副卡组替换不能改变主/额外/副各分区张数，否则提示1410
        if (currentDeck.mainCards.size() != preMainCount
                || currentDeck.extraCards.size() != preExtraCount
                || currentDeck.sideCards.size() != preSideCount) {
            showToast(DataManager.get().getStringManager().getSystemString(1410, "副卡组替换不能改变卡组张数"));
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
        showConfirmDialog("是否清空正在编辑的卡组？", () -> {
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
        showConfirmDialog(deckName + "\n是否删除这个卡组？", () -> {
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
                showToast("卡组已删除");
            }
        });
    }

    // === 对应 deck_con.cpp: BUTTON_SAVE_DECK ===
    public void saveDeck() {
        if (isReadonly) return;
        if (currentDeckFilePath == null || currentDeckFilePath.isEmpty()) {
            showToast("请先选择或另存卡组");
            return;
        }
        File deckFile = new File(currentDeckFilePath);
        boolean result = DeckUtils.save(currentDeck, deckFile);
        if (result) {
            isModified = false;
            showToast("卡组已保存");
            if (listener != null) listener.onDeckSaved();
        }
    }

    // === 对应 deck_con.cpp: BUTTON_SAVE_DECK_AS ===
    public void saveDeckAs() {
        if (isReadonly) return;
        if (etDeckName == null) return;
        String name = etDeckName.getText().toString().trim();
        if (name.isEmpty()) {
            showToast("请输入卡组名称");
            return;
        }
        File deckFile = getDeckFile(name);
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
            showToast("卡组已保存为: " + name);
            if (listener != null) listener.onDeckSaved();
        }
    }

    // === 对应 deck_con.cpp: StartFilter / FilterCards ===
    public void startFilter() {
        filterType = spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0;
        filterType2 = (int) getSpinnerItemId(spinnerFilterType2);
        filterLm = (int) getSpinnerItemId(spinnerFilterLimit);
        filterAttrib = (int) getSpinnerItemId(spinnerFilterAttribute);
        filterRace = (int) getSpinnerItemId(spinnerFilterRace);

        int[] atk = parseFilterType(etAttack != null ? etAttack.getText().toString() : "");
        filterAtkType = atk[0];
        filterAtk = atk[1];
        int[] def = parseFilterType(etDefense != null ? etDefense.getText().toString() : "");
        filterDefType = def[0];
        filterDef = def[1];
        int[] lv = parseFilterType(etStar != null ? etStar.getText().toString() : "");
        filterLvType = lv[0];
        filterLv = lv[1];
        int[] scl = parseFilterType(etScale != null ? etScale.getText().toString() : "");
        filterSclType = scl[0];
        filterScl = scl[1];

        filterCards();
    }

    // === 对应 deck_con.cpp: FilterCards ===
    public void filterCards() {
        searchResults.clear();
        String keyword = etKeyword != null ? etKeyword.getText().toString().trim().toLowerCase() : "";

        SparseArray<Card> allCards = getAllCardDatabase();
        for (int i = 0; i < allCards.size(); i++) {
            Card card = allCards.valueAt(i);
            if (card == null) continue;
            if (Card.isType(card.Type, CardType.Token)) continue;

            if (!matchesTypeFilter(card)) continue;
            if (!matchesType2Filter(card)) continue;
            if (!matchesKeywordFilter(card, keyword)) continue;
            if (!matchesLimitFilter(card)) continue;
            if (filterEffect != 0 && (card.Category & filterEffect) == 0) continue;
            if (filterMarks != 0 && !((card.Defense & filterMarks) == filterMarks && Card.isType(card.Type, CardType.Link)))
                continue;

            if (filterType == 1) {
                if (filterAttrib != 0 && card.Attribute != filterAttrib) continue;
                if (filterRace != 0 && card.Race != filterRace) continue;
                if (filterAtkType != 0 && !matchesNumericFilter(card.Attack, filterAtkType, filterAtk))
                    continue;
                if (filterDefType != 0) {
                    if (Card.isType(card.Type, CardType.Link)) continue;
                    if (!matchesNumericFilter(card.Defense, filterDefType, filterDef)) continue;
                }
                if (filterLvType != 0 && !matchesNumericFilter(card.getStar(), filterLvType, filterLv))
                    continue;
                if (filterSclType != 0) {
                    if (!Card.isType(card.Type, CardType.Pendulum)) continue;
                    if (!matchesNumericFilter(card.LeftScale, filterSclType, filterScl)) continue;
                }
            }

            searchResults.add(card);
        }

        sortSearchResults();
        updateSearchResultCount();
        if (searchAdapter != null) {
            searchAdapter.setCards(searchResults);
        }
        if (rvSearchResults != null) {
            rvSearchResults.scrollToPosition(0);
        }
        if (listener != null) {
            listener.onSearchResultsUpdated(searchResults.size());
        }
    }

    // === 对应 deck_con.cpp: ClearSearch ===
    public void clearSearch() {
        if (spinnerFilterType != null) spinnerFilterType.setSelection(0);
        if (spinnerFilterType2 != null) spinnerFilterType2.setSelection(0);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setSelection(0);
        if (spinnerFilterRace != null) spinnerFilterRace.setSelection(0);
        if (spinnerFilterLimit != null) spinnerFilterLimit.setSelection(0);
        if (etAttack != null) etAttack.setText("");
        if (etDefense != null) etDefense.setText("");
        if (etStar != null) etStar.setText("");
        if (etScale != null) etScale.setText("");
        if (etKeyword != null) etKeyword.setText("");
        filterEffect = 0;
        filterMarks = 0;
        filterType = 0;
        filterType2 = 0;
        filterAttrib = 0;
        filterRace = 0;
        filterLm = 0;
        filterAtkType = 0;
        filterAtk = 0;
        filterDefType = 0;
        filterDef = 0;
        filterLvType = 0;
        filterLv = 0;
        filterSclType = 0;
        filterScl = 0;
        updateFilterMarksDisplay();
        updateFilterEffectDisplay();
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) {
            linkMarkerPopup.dismiss();
        }
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing()) {
            effectCategoryPopup.dismiss();
        }
        //清空按钮同时清除搜索结果列表所有item
        searchResults.clear();
        updateSearchResultCount();
        if (searchAdapter != null) {
            searchAdapter.setCards(searchResults);
        }
        if (listener != null) {
            listener.onSearchResultsUpdated(0);
        }
    }

    // === 对应 deck_con.cpp: SortList ===
    public void sortSearchResults() {
        int sortSel = spinnerSortType != null ? spinnerSortType.getSelectedItemPosition() : 0;
        Comparator<Card> comparator = (a, b) -> {
            int classA = getCardClassRank(a);
            int classB = getCardClassRank(b);
            if (classA != classB) return Integer.compare(classA, classB);
            int cmp;
            if (classA == 0) {
                //怪兽卡内部按spinner_sort_type选择的类型排序
                cmp = compareMonsterBySortType(a, b, sortSel);
            } else {
                //魔法陷阱：先按子类型，名称模式按名称，其余按卡号
                cmp = Integer.compare(getCardSubTypeRank(a, classA), getCardSubTypeRank(b, classB));
                if (cmp == 0 && sortSel == 3) {
                    cmp = compareCardName(a, b);
                }
            }
            if (cmp != 0) return cmp;
            return Integer.compare(a.Code, b.Code);
        };
        Collections.sort(searchResults, comparator);
    }

    //大类排序：怪兽(0) < 魔法(1) < 陷阱(2)
    private int getCardClassRank(Card card) {
        if (Card.isType(card.Type, CardType.Monster)) return 0;
        if (Card.isType(card.Type, CardType.Spell)) return 1;
        if (Card.isType(card.Type, CardType.Trap)) return 2;
        return 3;
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

    // === 对应 data_manager.cpp: deck_sort_lv / deck_sort_atk / deck_sort_def ===
    private int compareMonsterBySortType(Card a, Card b, int sortSel) {
        int subA = getCardSubTypeRank(a, 0);
        int subB = getCardSubTypeRank(b, 0);
        int cmp;
        switch (sortSel) {
            case 1:
                //攻击：攻↓ → 守↓ → 星↓ → 子类型
                cmp = Integer.compare(b.Attack, a.Attack);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b.Defense, a.Defense);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b.Level & 0xff, a.Level & 0xff);
                if (cmp != 0) return cmp;
                return Integer.compare(subA, subB);
            case 2:
                //守备：守↓ → 攻↓ → 星↓ → 子类型
                cmp = Integer.compare(b.Defense, a.Defense);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b.Attack, a.Attack);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b.Level & 0xff, a.Level & 0xff);
                if (cmp != 0) return cmp;
                return Integer.compare(subA, subB);
            case 3:
                //名称：按卡名
                return compareCardName(a, b);
            default:
                //星数：子类型 → 星↓ → 攻↓ → 守↓
                cmp = Integer.compare(subA, subB);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b.Level & 0xff, a.Level & 0xff);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b.Attack, a.Attack);
                if (cmp != 0) return cmp;
                return Integer.compare(b.Defense, a.Defense);
        }
    }

    private int compareCardName(Card a, Card b) {
        String na = a.Name != null ? a.Name : "";
        String nb = b.Name != null ? b.Name : "";
        return na.compareTo(nb);
    }

    // === 对应 deck_con.cpp: RefreshReadonly ===
    public void refreshReadonly() {
        boolean disabled = isReadonly || isPackMode;
        int textColor = disabled ? Color.GRAY : Color.WHITE;
        if (btnSave != null) btnSave.setEnabled(!disabled);
        if (btnSaveAs != null) btnSaveAs.setEnabled(!disabled);
        if (btnClear != null) {
            btnClear.setEnabled(!disabled);
            btnClear.setTextColor(textColor);
        }
        if (btnShuffle != null) {
            btnShuffle.setEnabled(!disabled);
            btnShuffle.setTextColor(textColor);
        }
        if (btnSort != null) {
            btnSort.setEnabled(!disabled);
            btnSort.setTextColor(textColor);
        }
        if (btnDelete != null) {
            btnDelete.setEnabled(!disabled);
            btnDelete.setTextColor(textColor);
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
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            groupView.addCard(card);
        }
        groupView.updateTopImage(mImageTop, mLimitList);
        groupView.updateAvail(mImageTop, availLm);
        int count = groupView.getChildCount();
        for (int i = 0; i < count; i++) {
            CardView cardView = (CardView) groupView.getChildAt(i);
            final int index = i;
            cardView.setOnClickListener(v -> onDeckCardClicked(type, index));
            if (searchAdapter != null) {
                cardView.setOnTouchListener(searchAdapter.createDragTouchListener(type, index, cardView.getCard(), touchSlop, isReadonly || isPackMode));
            }
        }
    }

    private void updateDeckCounts() {
        currentDeck.syncCounts();
        int mainCount = currentDeck.getMainCount();
        int extraCount = currentDeck.getExtraCount();
        int sideCount = currentDeck.getSideCount();
        int totalCount = mainCount + extraCount + sideCount;
        boolean isGenesys = AppsSettings.get().getGenesysMode() == 1
                && mLimitList != null && mLimitList.getCreditLimits() != null;

        if (tvMainCountNum != null) tvMainCountNum.setText(String.valueOf(mainCount));
        if (tvExtraCountNum != null) tvExtraCountNum.setText(String.valueOf(extraCount));
        if (tvSideCountNum != null) tvSideCountNum.setText(String.valueOf(sideCount));

        if (llGenesysScoreboard != null) {
            llGenesysScoreboard.setVisibility(isGenesys ? View.VISIBLE : View.GONE);
        }
        if (isGenesys) {
            int creditLimit = mLimitList.getCreditLimits();
            int creditCount = getDeckCreditCount();
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

        int mainMonster = 0, mainSpell = 0, mainTrap = 0;
        for (Card card : currentDeck.getMainCards()) {
            if (Card.isType(card.Type, CardType.Monster)) mainMonster++;
            else if (Card.isType(card.Type, CardType.Spell)) mainSpell++;
            else if (Card.isType(card.Type, CardType.Trap)) mainTrap++;
        }
        if (tvMainMonsterCount != null) tvMainMonsterCount.setText(String.valueOf(mainMonster));
        if (tvMainSpellCount != null) tvMainSpellCount.setText(String.valueOf(mainSpell));
        if (tvMainTrapCount != null) tvMainTrapCount.setText(String.valueOf(mainTrap));

        int fusion = 0, synchro = 0, xyz = 0, link = 0;
        for (Card card : currentDeck.getExtraCards()) {
            if (Card.isType(card.Type, CardType.Fusion)) fusion++;
            else if (Card.isType(card.Type, CardType.Synchro)) synchro++;
            else if (Card.isType(card.Type, CardType.Xyz)) xyz++;
            else if (Card.isType(card.Type, CardType.Link)) link++;
        }
        if (tvExtraFusionCount != null) tvExtraFusionCount.setText(String.valueOf(fusion));
        if (tvExtraSynchroCount != null) tvExtraSynchroCount.setText(String.valueOf(synchro));
        if (tvExtraXyzCount != null) tvExtraXyzCount.setText(String.valueOf(xyz));
        if (tvExtraLinkCount != null) tvExtraLinkCount.setText(String.valueOf(link));

        int sideMonster = 0, sideSpell = 0, sideTrap = 0;
        for (Card card : currentDeck.getSideCards()) {
            if (Card.isType(card.Type, CardType.Monster)) sideMonster++;
            else if (Card.isType(card.Type, CardType.Spell)) sideSpell++;
            else if (Card.isType(card.Type, CardType.Trap)) sideTrap++;
        }
        if (tvSideMonsterCount != null) tvSideMonsterCount.setText(String.valueOf(sideMonster));
        if (tvSideSpellCount != null) tvSideSpellCount.setText(String.valueOf(sideSpell));
        if (tvSideTrapCount != null) tvSideTrapCount.setText(String.valueOf(sideTrap));
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

    private void updateSearchResultCount() {
        if (tvSearchResult != null)
            tvSearchResult.setText(searchResultPrefix + " " + searchResults.size());
    }

    private boolean matchesTypeFilter(Card card) {
        switch (filterType) {
            case 1:
                return Card.isType(card.Type, CardType.Monster);
            case 2:
                return Card.isType(card.Type, CardType.Spell);
            case 3:
                return Card.isType(card.Type, CardType.Trap);
            default:
                return true;
        }
    }

    // === 对应 deck_con.cpp: filter_type2 匹配 ===
    private boolean matchesType2Filter(Card card) {
        if (filterType2 == 0) return true;
        if (filterType == 1) {
            //怪兽：filter_type2的所有位都需匹配
            return (card.Type & filterType2) == filterType2;
        }
        if (filterType == 2 || filterType == 3) {
            //魔法/陷阱：精确匹配整个type
            return card.Type == filterType2;
        }
        return true;
    }

    private boolean matchesKeywordFilter(Card card, String keyword) {
        if (keyword.isEmpty()) return true;
        if (card.Name != null && card.Name.toLowerCase().contains(keyword)) return true;
        if (card.Desc != null && card.Desc.toLowerCase().contains(keyword)) return true;
        return String.valueOf(card.Code).equals(keyword);
    }

    private int[] parseFilterType(String text) {
        if (text == null || text.trim().isEmpty()) return new int[]{0, 0};
        text = text.trim();
        if (text.startsWith("=")) {
            try {
                return new int[]{1, Integer.parseInt(text.substring(1))};
            } catch (NumberFormatException e) {
                return new int[]{0, 0};
            }
        } else if (text.startsWith(">=")) {
            try {
                return new int[]{3, Integer.parseInt(text.substring(2))};
            } catch (NumberFormatException e) {
                return new int[]{0, 0};
            }
        } else if (text.startsWith(">")) {
            try {
                return new int[]{2, Integer.parseInt(text.substring(1))};
            } catch (NumberFormatException e) {
                return new int[]{0, 0};
            }
        } else if (text.startsWith("<=")) {
            try {
                return new int[]{5, Integer.parseInt(text.substring(2))};
            } catch (NumberFormatException e) {
                return new int[]{0, 0};
            }
        } else if (text.startsWith("<")) {
            try {
                return new int[]{4, Integer.parseInt(text.substring(1))};
            } catch (NumberFormatException e) {
                return new int[]{0, 0};
            }
        } else {
            try {
                return new int[]{1, Integer.parseInt(text)};
            } catch (NumberFormatException e) {
                return new int[]{0, 0};
            }
        }
    }

    private int getSpinnerItemId(Spinner spinner) {
        if (spinner == null) return 0;
        Object item = spinner.getSelectedItem();
        if (item instanceof SimpleSpinnerItem) {
            return (int) ((SimpleSpinnerItem) item).value;
        }
        return 0;
    }

    private boolean matchesNumericFilter(int value, int filterType, int filterValue) {
        switch (filterType) {
            case 1:
                return value == filterValue;
            case 2:
                return value > filterValue;
            case 3:
                return value >= filterValue;
            case 4:
                return value < filterValue;
            case 5:
                return value <= filterValue;
            case 6:
                return value < 0;
            default:
                return true;
        }
    }

    private boolean matchesLimitFilter(Card card) {
        if (filterLm == 0) return true;
        int code = (card.Alias > 0) ? card.Alias : card.Code;

        if (filterLm >= 1 && filterLm <= 3) {
            if (mLimitList == null) return false;
            LimitType type;
            if (filterLm == 1) type = LimitType.Forbidden;
            else if (filterLm == 2) type = LimitType.Limit;
            else type = LimitType.SemiLimit;
            return mLimitList.check(code, card.Alias, type);
        }

        if (filterLm == 100) {
            if (mLimitList == null || mLimitList.getCredits() == null) return false;
            boolean hasCredit = mLimitList.getCredits().containsKey(code);
            if (!hasCredit && card.Alias > 0) {
                hasCredit = mLimitList.getCredits().containsKey(card.Alias);
            }
            return hasCredit;
        }

        int ot = card.Ot;
        switch (filterLm) {
            case 6:
                return (ot & 0x1) != 0;
            case 7:
                return (ot & 0x2) != 0;
            case 8:
                return (ot & 0x8) != 0;
            case 9:
                return (ot & 0x4) != 0;
            case 10:
                return (ot & 0x1) != 0 && (ot & 0x2) == 0;
            case 11:
                return (ot & 0x2) != 0 && (ot & 0x1) == 0;
            case 12:
                return (ot & 0x1) != 0 && (ot & 0x2) != 0;
            default:
                return true;
        }
    }

    private SparseArray<Card> getAllCardDatabase() {
        return DataManager.get().getCardManager().getAllCards();
    }

    private void loadDeckByName(String name) {
        File deckFile = getDeckFile(name);
        if (deckFile != null && deckFile.exists()) {
            DeckInfo loaded = DeckLoader.readDeck(cardLoader, deckFile);
            if (loaded != null) {
                currentDeck.update(loaded);
                currentDeck.source = deckFile;
                notifyDeckChanged();
                isModified = false;
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
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName != null ? lastDeckName : "";
                loadDeckFromPath(lastDeckPath);
                updateDeckManagerButtonText();
                return;
            }
        }

        if (lastDeckName != null && !lastDeckName.isEmpty()) {
            File deckFile = getDeckFile(lastDeckName);
            if (deckFile.exists()) {
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName;
                loadDeckFromPath(deckFile.getAbsolutePath());
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

    private File getDeckDir() {
        return new File(AppsSettings.get().getResourcePath(), "deck");
    }

    private File getDeckFile(String name) {
        File dir = getDeckDir();
        if (dir == null) dir = new File(AppsSettings.get().getResourcePath(), "deck");
        return new File(dir, name + ".ydk");
    }

    private void showConfirmDialog(String message, Runnable onConfirm) {
        mainHandler.post(() -> {
            new AlertDialog.Builder(activity)
                    .setMessage(message)
                    .setPositiveButton("是", (d, w) -> onConfirm.run())
                    .setNegativeButton("否", null)
                    .show();
        });
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    public DeckInfo getCurrentDeck() {
        return currentDeck;
    }

    public boolean isModified() {
        return isModified;
    }

    private void showDeckSelectorDialog() {
        if (deckSelectorDialog == null) {
            deckSelectorDialog = new DeckSelectorDialog(activity);
            deckSelectorDialog.setIncludePackCategory(true);
        }
        deckSelectorDialog.show(btnDeckManager);
    }

    private void showLinkMarkerPopup() {
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) {
            linkMarkerPopup.dismiss();
        }
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing()) {
            effectCategoryPopup.dismiss();
        }

        linkMarkerPopup = new LinkMarkerPopupWindow(activity, filterMarks, newFilterMarks -> {
            filterMarks = newFilterMarks;
            updateFilterMarksDisplay();
        });
        linkMarkerPopup.show(btnFilterMarks);
    }

    private void updateFilterMarksDisplay() {
        if (btnFilterMarks == null) return;
        if (filterMarks != 0) {
            String[] arrows = {"↙", "↓", "↘", "←", "", "→", "↖", "↑", "↗"};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                if (i == 4) continue;
                if (((filterMarks >> i) & 1) == 1) {
                    sb.append(arrows[i]);
                }
            }
            btnFilterMarks.setText(sb.toString());
            btnFilterMarks.setBackground(activity.getDrawable(R.drawable.sbutton_p));
        } else {
            btnFilterMarks.setText("连接标记");
            btnFilterMarks.setBackground(activity.getDrawable(R.drawable.button3_bg));
        }
    }

    private void showEffectCategoryPopup() {
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) {
            linkMarkerPopup.dismiss();
        }
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing()) {
            effectCategoryPopup.dismiss();
        }

        effectCategoryPopup = new EffectCategoryPopupWindow(activity, filterEffect, newFilterEffect -> {
            filterEffect = newFilterEffect;
            updateFilterEffectDisplay();
        });
        effectCategoryPopup.show(btnFilterEffect);
    }

    /**
     * 效果分类按钮显示状态：弹窗内有checkbox被勾选（filterEffect!=0）时背景常亮为按下态，
     * 无勾选时恢复默认selector背景
     */
    private void updateFilterEffectDisplay() {
        if (btnFilterEffect == null) return;
        if (filterEffect != 0) {
            btnFilterEffect.setText("效果:*");
            btnFilterEffect.setBackgroundResource(R.drawable.sbutton_p);
        } else {
            btnFilterEffect.setText("效果分类");
            btnFilterEffect.setBackgroundResource(R.drawable.button3_bg);
        }
    }

    //当前赛制标识显示模式（spinner_filter_limit选中项id：6=OCG、7=TCG、8=简体中文，其余不显示）
    private int availLm = 0;

    private void applyAvailDisplay(int lm) {
        if (availLm == lm) return;
        availLm = lm;
        if (cgvMain != null) cgvMain.updateAvail(mImageTop, availLm);
        if (cgvExtra != null) cgvExtra.updateAvail(mImageTop, availLm);
        if (cgvSide != null) cgvSide.updateAvail(mImageTop, availLm);
        if (searchAdapter != null) searchAdapter.setAvailLm(availLm);
    }
    // === 拖放：网格内换位、跨网格移动、搜索结果拖入、拖回搜索区删除 ===

    private void setupDragAndDrop() {
        touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        if (searchAdapter != null) searchAdapter.setDragState(touchSlop, isReadonly || isPackMode);
        dragHelper.addDropTarget(cgvMain);
        dragHelper.addDropTarget(cgvExtra);
        dragHelper.addDropTarget(cgvSide);
        dragHelper.addDropTarget(rvSearchResults);
    }

    /**
     * 应用内自定义拖拽的落点回调：按落点目标完成卡片的移动/新增/删除。
     * 来自卡组网格的卡先移出原位再插入落点（复用类型/数量/禁限校验），校验失败还原原位。
     */
    @Override
    public void onCardDrop(View target, DeckInfo.Type source, int index, Card card, float rawX, float rawY) {
        if (card == null || isReadonly) return;

        //卡组网格拖到搜索结果区：删除卡片
        if (target == rvSearchResults) {
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
            //同列表换位时，移除原卡后插入下标需修正
            int insert = (source == targetType && index < dropIndex) ? dropIndex - 1 : dropIndex;
            if (!pushToDeck(targetType, moved, insert)) {
                sourceList.add(Math.min(index, sourceList.size()), moved);
                notifyDeckChanged();
            }
            return;
        }

        //搜索结果拖入：直接插入目标网格
        pushToDeck(targetType, card, dropIndex);
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
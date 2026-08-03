package cn.garymb.ygomobile.game;

import android.app.Activity;
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
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
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
    private boolean isPackMode = false;
    private boolean isSiding = false;
    private int preMainCount = 0, preExtraCount = 0, preSideCount = 0;
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
    private Button btnFilterEffect, btnFilterSearch, btnFilterClear, btnFilterMarks, btnDeckManager;
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
    private int availLm = 0;

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
            showConfirmDialog("此操作将放弃对当前卡组的修改，是否继续？", this::doTerminate);
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
        setSystemLabel(tvLabelDeck, sm, 1300, "卡组:");
        setSystemLabel(tvLabelType, sm, 1311, "种类:");
        setSystemLabel(tvLabelAttribute, sm, 1319, "属性:");
        setSystemLabel(tvLabelRace, sm, 1321, "种族:");
        setSystemLabel(tvLabelStar, sm, 1324, "星数:");
        setSystemLabel(tvLabelScale, sm, 1336, "刻度:");
        setSystemLabel(tvLabelLimit, sm, 1315, "禁限:");
        setSystemLabel(tvLabelAttack, sm, 1322, "攻击:");
        setSystemLabel(tvLabelDefense, sm, 1323, "守备:");
        setSystemLabel(tvLabelKeyword, sm, 1325, "关键字:");
        setSystemLabel(tvLabelMainDeck, sm, isPackMode ? 1477 : 1330, "主卡组:");
        setSystemLabel(tvLabelExtraDeck, sm, 1331, "额外卡组:");
        setSystemLabel(tvLabelSideDeck, sm, 1332, "副卡组:");
        searchResultPrefix = sm.getSystemString(1333, "搜索结果:");
    }

    private void setSystemLabel(TextView tv, StringManager sm, int index, String def) {
        if (tv != null) tv.setText(sm.getSystemString(index, def));
    }


    private void setupRecyclerViews() {
        mLimitList = AppsSettings.get().getGenesysMode() == 1
                ? cardLoader.getGenesysLimitList() : cardLoader.getLimitList();
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
        rvSearchResults.setLayoutManager(new LinearLayoutManager(activity));
        searchAdapter = new DeckCardAdapter(imageLoader, this, null, dragHelper);
        searchAdapter.setLimitList(mLimitList);
        rvSearchResults.setAdapter(searchAdapter);
    }

    private void setBitmapIfNotNull(ImageView iv, android.graphics.Bitmap bm) {
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
        if (searchAdapter != null) searchAdapter.setCardSize(w, h);
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
        int dropBg = YGOUtil.c(R.color.ygopro_list_background);
        List<SimpleSpinnerItem> typeItems = new ArrayList<>();
        typeItems.add(new SimpleSpinnerItem(0, "(无)"));
        typeItems.add(new SimpleSpinnerItem(1, "怪兽"));
        typeItems.add(new SimpleSpinnerItem(2, "魔法"));
        typeItems.add(new SimpleSpinnerItem(3, "陷阱"));
        if (spinnerFilterType != null) {
            spinnerFilterType.setAdapter(createSpinnerAdapter(typeItems, Color.WHITE, dropBg));
            spinnerFilterType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    updateType2Spinner(pos);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> p) {
                    updateType2Spinner(0);
                }
            });
        }
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);
        if (spinnerFilterType2 != null) {
            spinnerFilterType2.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    updateDefenseEditState();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> p) {
                    updateDefenseEditState();
                }
            });
        }
        List<SimpleSpinnerItem> attrItems = new ArrayList<>();
        attrItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        for (CardAttribute attr : CardAttribute.values())
            attrItems.add(new SimpleSpinnerItem(attr.getId(), sm.getSystemString(attr.getLanguageIndex(), attr.name())));
        attrAdapter = createSpinnerAdapter(attrItems, Color.WHITE, dropBg);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setAdapter(attrAdapter);
        List<SimpleSpinnerItem> raceItems = new ArrayList<>();
        raceItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        for (CardRace race : CardRace.values())
            raceItems.add(new SimpleSpinnerItem(race.value(), sm.getSystemString(race.getLanguageIndex(), race.name())));
        raceAdapter = createSpinnerAdapter(raceItems, Color.WHITE, dropBg);
        if (spinnerFilterRace != null) spinnerFilterRace.setAdapter(raceAdapter);
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);
        List<SimpleSpinnerItem> limitItems = new ArrayList<>();
        limitItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        limitItems.add(new SimpleSpinnerItem(LimitType.Forbidden.getId(), sm.getSystemString(LimitType.Forbidden.getLanguageIndex(), LimitType.Forbidden.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.Limit.getId(), sm.getSystemString(LimitType.Limit.getLanguageIndex(), LimitType.Limit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.SemiLimit.getId(), sm.getSystemString(LimitType.SemiLimit.getLanguageIndex(), LimitType.SemiLimit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.GeneSys.getId(), sm.getSystemString(LimitType.GeneSys.getLanguageIndex(), LimitType.GeneSys.name())));
        limitItems.add(new SimpleSpinnerItem(6, sm.getSystemString(1481, "OCG")));
        limitItems.add(new SimpleSpinnerItem(7, sm.getSystemString(1482, "TCG")));
        limitItems.add(new SimpleSpinnerItem(8, sm.getSystemString(1483, "简体中文")));
        limitItems.add(new SimpleSpinnerItem(9, sm.getSystemString(1484, "自定义")));
        limitItems.add(new SimpleSpinnerItem(10, sm.getSystemString(1487, "OCG独有")));
        limitItems.add(new SimpleSpinnerItem(11, sm.getSystemString(1488, "TCG独有")));
        limitItems.add(new SimpleSpinnerItem(12, sm.getSystemString(1485, "无独有卡")));
        if (spinnerFilterLimit != null) {
            spinnerFilterLimit.setAdapter(createSpinnerAdapter(limitItems, Color.WHITE, dropBg));
            spinnerFilterLimit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    applyAvailDisplay((int) SimpleSpinnerAdapter.getSelect(spinnerFilterLimit));
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> p) {
                    applyAvailDisplay(0);
                }
            });
        }
        List<SimpleSpinnerItem> sortItems = new ArrayList<>();
        sortItems.add(new SimpleSpinnerItem(0, "星数↑"));
        sortItems.add(new SimpleSpinnerItem(1, "攻击↑"));
        sortItems.add(new SimpleSpinnerItem(2, "守备↑"));
        sortItems.add(new SimpleSpinnerItem(3, "名称↓"));
        if (spinnerSortType != null)
            spinnerSortType.setAdapter(createSpinnerAdapter(sortItems, Color.WHITE, dropBg));
    }

    private SimpleSpinnerAdapter createSpinnerAdapter(List<SimpleSpinnerItem> items, int color, int dropBg) {
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(activity);
        adapter.setColor(color);
        adapter.setDropDownBackgroundColor(dropBg);
        adapter.setTextSize(8f);
        adapter.set(items);
        return adapter;
    }

    private void setupButtons() {
        setClickListener(btnExit, v -> terminate());
        setClickListener(btnShuffle, v -> shuffleDeck());
        setClickListener(btnSort, v -> sortDeck());
        setClickListener(btnClear, v -> clearDeck());
        setClickListener(btnDelete, v -> deleteDeck());
        setClickListener(btnSave, v -> saveDeck());
        setClickListener(btnSaveAs, v -> saveDeckAs());
        setClickListener(btnFilterEffect, v -> showEffectCategoryPopup());
        setClickListener(btnFilterMarks, v -> showLinkMarkerPopup());
        setClickListener(btnFilterSearch, v -> startFilter());
        setClickListener(btnFilterClear, v -> clearSearch());
    }

    private void setClickListener(Button btn, View.OnClickListener l) {
        if (btn != null) btn.setOnClickListener(l);
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
                addSpinnerItems(items, sm, m);
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
        boolean monsterEnabled = typePos == 1;
        setSpinnerEnabled(spinnerFilterType2, null, enabled);
        setSpinnerEnabled(spinnerFilterAttribute, attrAdapter, monsterEnabled);
        setSpinnerEnabled(spinnerFilterRace, raceAdapter, monsterEnabled);
        setEditTextEnabled(etStar, monsterEnabled);
        setEditTextEnabled(etScale, monsterEnabled);
        setEditTextEnabled(etAttack, monsterEnabled);
        updateDefenseEditState();
    }

    private void addSpinnerItems(List<SimpleSpinnerItem> items, StringManager sm, long m) {
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId(), sm.getSystemString(1054, "通常")));
        items.add(new SimpleSpinnerItem(m | CardType.Effect.getId(), sm.getSystemString(1055, "效果")));
        items.add(new SimpleSpinnerItem(m | CardType.Fusion.getId(), sm.getSystemString(1056, "融合")));
        items.add(new SimpleSpinnerItem(m | CardType.Ritual.getId(), sm.getSystemString(1057, "仪式")));
        items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId(), sm.getSystemString(1063, "同调")));
        items.add(new SimpleSpinnerItem(m | CardType.Xyz.getId(), sm.getSystemString(1073, "超量")));
        items.add(new SimpleSpinnerItem(m | CardType.Pendulum.getId(), sm.getSystemString(1074, "灵摆")));
        items.add(new SimpleSpinnerItem(m | CardType.Link.getId(), sm.getSystemString(1076, "连接")));
        items.add(new SimpleSpinnerItem(m | CardType.Sp_Summon.getId(), sm.getSystemString(1075, "特殊召唤")));
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Tuner.getId(), sm.getSystemString(1054, "通常") + "|" + sm.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Pendulum.getId(), sm.getSystemString(1054, "通常") + "|" + sm.getSystemString(1074, "灵摆")));
        items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId() | CardType.Tuner.getId(), sm.getSystemString(1063, "同调") + "|" + sm.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Tuner.getId(), sm.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Gemini.getId(), sm.getSystemString(1061, "二重")));
        items.add(new SimpleSpinnerItem(m | CardType.Union.getId(), sm.getSystemString(1060, "同盟")));
        items.add(new SimpleSpinnerItem(m | CardType.Spirit.getId(), sm.getSystemString(1059, "灵魂")));
        items.add(new SimpleSpinnerItem(m | CardType.Flip.getId(), sm.getSystemString(1071, "反转")));
        items.add(new SimpleSpinnerItem(m | CardType.Toon.getId(), sm.getSystemString(1072, "卡通")));
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
                if (deckSelectorDialog != null) deckSelectorDialog.show(btnDeckManager);
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

    public void popMain(int seq) {
        if (seq >= 0 && seq < currentDeck.mainCards.size()) {
            currentDeck.removeMain(seq);
            isModified = true;
            notifyDeckChanged();
        }
    }

    public void popExtra(int seq) {
        if (seq >= 0 && seq < currentDeck.extraCards.size()) {
            currentDeck.removeExtra(seq);
            isModified = true;
            notifyDeckChanged();
        }
    }

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
            YGOUtil.showTextToast(DataManager.get().getStringManager().getSystemString(1410, "副卡组替换不能改变卡组张数"));
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
                YGOUtil.showTextToast("卡组已删除");
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
            YGOUtil.showTextToast("卡组已保存");
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

    // === 对应 deck_con.cpp: StartFilter / FilterCards ===
    public void startFilter() {
        filterType = spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0;
        filterType2 = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterType2);
        filterLm = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterLimit);
        filterAttrib = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterAttribute);
        filterRace = (int) SimpleSpinnerAdapter.getSelect(spinnerFilterRace);
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

    public void filterCards() {
        searchResults.clear();
        String keyword = etKeyword != null ? etKeyword.getText().toString().trim().toLowerCase() : "";
        SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
        for (int i = 0; i < allCards.size(); i++) {
            Card card = allCards.valueAt(i);
            if (card == null || Card.isType(card.Type, CardType.Token)) continue;
            if (!matchesAllFilters(card, keyword)) continue;
            searchResults.add(card);
        }
        sortSearchResults();
        if (tvSearchResult != null)
            tvSearchResult.setText(searchResultPrefix + " " + searchResults.size());
        if (searchAdapter != null) searchAdapter.setCards(searchResults);
        if (rvSearchResults != null) rvSearchResults.scrollToPosition(0);
        if (listener != null) listener.onSearchResultsUpdated(searchResults.size());
    }

    private boolean matchesAllFilters(Card card, String keyword) {
        if (!matchesTypeFilter(card) || !matchesType2Filter(card)) return false;
        if (!matchesKeywordFilter(card, keyword) || !matchesLimitFilter(card)) return false;
        if (filterEffect != 0 && (card.Category & filterEffect) == 0) return false;
        if (filterMarks != 0 && !((card.Defense & filterMarks) == filterMarks && Card.isType(card.Type, CardType.Link)))
            return false;
        if (filterType == 1) {
            if (filterAttrib != 0 && card.Attribute != filterAttrib) return false;
            if (filterRace != 0 && card.Race != filterRace) return false;
            if (filterAtkType != 0 && !matchesNumericFilter(card.Attack, filterAtkType, filterAtk))
                return false;
            if (filterDefType != 0) {
                if (Card.isType(card.Type, CardType.Link)) return false;
                if (!matchesNumericFilter(card.Defense, filterDefType, filterDef)) return false;
            }
            if (filterLvType != 0 && !matchesNumericFilter(card.getStar(), filterLvType, filterLv))
                return false;
            if (filterSclType != 0) {
                if (!Card.isType(card.Type, CardType.Pendulum)) return false;
                if (!matchesNumericFilter(card.LeftScale, filterSclType, filterScl)) return false;
            }
        }
        return true;
    }

    // === 对应 deck_con.cpp: ClearSearch ===
    public void clearSearch() {
        if (spinnerFilterType != null) spinnerFilterType.setSelection(0);
        if (spinnerFilterType2 != null) spinnerFilterType2.setSelection(0);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setSelection(0);
        if (spinnerFilterRace != null) spinnerFilterRace.setSelection(0);
        if (spinnerFilterLimit != null) spinnerFilterLimit.setSelection(0);
        clearEditTexts();
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
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) linkMarkerPopup.dismiss();
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing())
            effectCategoryPopup.dismiss();
        searchResults.clear();
        if (tvSearchResult != null) tvSearchResult.setText(searchResultPrefix + " 0");
        if (searchAdapter != null) searchAdapter.setCards(searchResults);
        if (listener != null) listener.onSearchResultsUpdated(0);
    }

    private void clearEditTexts() {
        if (etAttack != null) etAttack.setText("");
        if (etDefense != null) etDefense.setText("");
        if (etStar != null) etStar.setText("");
        if (etScale != null) etScale.setText("");
        if (etKeyword != null) etKeyword.setText("");
    }

    // === 对应 deck_con.cpp: SortList ===
    public void sortSearchResults() {
        int sortSel = spinnerSortType != null ? spinnerSortType.getSelectedItemPosition() : 0;
        Comparator<Card> comparator = (a, b) -> {
            int classA = getCardClassRank(a), classB = getCardClassRank(b);
            if (classA != classB) return Integer.compare(classA, classB);
            int cmp = (classA == 0) ? compareMonsterBySortType(a, b, sortSel)
                    : Integer.compare(getCardSubTypeRank(a, classA), getCardSubTypeRank(b, classB));
            if (cmp == 0 && classA != 0 && sortSel == 3) {
                String na = a.Name != null ? a.Name : "", nb = b.Name != null ? b.Name : "";
                cmp = na.compareTo(nb);
            }
            return cmp != 0 ? cmp : Integer.compare(a.Code, b.Code);
        };
        Collections.sort(searchResults, comparator);
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

    // === 对应 data_manager.cpp: deck_sort_lv / deck_sort_atk / deck_sort_def ===
    private int compareMonsterBySortType(Card a, Card b, int sortSel) {
        int subA = getCardSubTypeRank(a, 0), subB = getCardSubTypeRank(b, 0);
        if (sortSel == 3) {
            String na = a.Name != null ? a.Name : "", nb = b.Name != null ? b.Name : "";
            return na.compareTo(nb);
        }
        int priA = (sortSel == 1) ? b.Attack : (sortSel == 2) ? b.Defense : subA;
        int priB = (sortSel == 1) ? a.Attack : (sortSel == 2) ? a.Defense : subB;
        int cmp = Integer.compare(priA, priB);
        if (cmp != 0) return cmp;
        int secA = (sortSel == 1) ? b.Defense : (sortSel == 2) ? b.Attack : (b.Level & 0xff);
        int secB = (sortSel == 1) ? a.Defense : (sortSel == 2) ? a.Attack : (a.Level & 0xff);
        cmp = Integer.compare(secA, secB);
        if (cmp != 0) return cmp;
        int terA = (sortSel <= 2) ? (b.Level & 0xff) : b.Attack;
        int terB = (sortSel <= 2) ? (a.Level & 0xff) : a.Attack;
        cmp = Integer.compare(terA, terB);
        if (cmp != 0) return cmp;
        int qA = (sortSel <= 2) ? subA : b.Defense;
        int qB = (sortSel <= 2) ? subB : a.Defense;
        return Integer.compare(qA, qB);
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
        if (text == null || (text = text.trim()).isEmpty()) return new int[]{0, 0};
        String op = "";
        for (String s : new String[]{">=", "<=", ">", "<", "="}) {
            if (text.startsWith(s)) {
                op = s;
                break;
            }
        }
        String numStr = op.isEmpty() ? text : text.substring(op.length());
        int type = parseOp(op);
        int val = parseNum(numStr);
        return val >= 0 ? new int[]{type, val} : new int[]{0, 0};
    }

    private int parseOp(String op) {
        switch (op) {
            case "=":
                return 1;
            case ">":
                return 2;
            case ">=":
                return 3;
            case "<":
                return 4;
            case "<=":
                return 5;
            default:
                return 1;
        }
    }

    private int parseNum(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
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
            File deckFile = new File(AppsSettings.get().getDeckDir(), lastDeckName + ".ydk");
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

    private void showConfirmDialog(String message, Runnable onConfirm) {
        mainHandler.post(() -> {
            new YesOrNoDialog(activity)
                    .setTitle("确认")
                    .setMessage(message)
                    .setType(YesOrNoDialog.TYPE_YES_NO)
                    .setPositiveButtonText("是")
                    .setNegativeButtonText("否")
                    .setPositiveButton(v -> {
                        onConfirm.run();
                    })
                    .show(rootView);
        });
    }

    public DeckInfo getCurrentDeck() {
        return currentDeck;
    }

    public boolean isModified() {
        return isModified;
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
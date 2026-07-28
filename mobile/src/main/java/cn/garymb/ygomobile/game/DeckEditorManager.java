package cn.garymb.ygomobile.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
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
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.dialogs.DeckSelectorDialog;
import cn.garymb.ygomobile.ui.dialogs.EffectCategoryPopupWindow;
import cn.garymb.ygomobile.ui.dialogs.LinkMarkerPopupWindow;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import cn.garymb.ygomobile.ui.widget.CardView;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class DeckEditorManager {
    private static final String TAG = "DeckEditorManager";

    public interface DeckEditorListener {
        void onDeckModified();

        void onDeckSaved();

        void onExitEditor();

        void onCardSelected(Card card);

        void onSearchResultsUpdated(int count);
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
    private int prevDeck = 0;

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
    private TextView tvLimitTotalNum, tvCreditNum, tvRemainNum, tvDeckGenesys;
    private TextView tvMainMonsterCount, tvMainSpellCount, tvMainTrapCount;
    private TextView tvExtraFusionCount, tvExtraSynchroCount, tvExtraXyzCount, tvExtraLinkCount;
    private TextView tvSideMonsterCount, tvSideSpellCount, tvSideTrapCount;
    private CardGroupView cgvMain, cgvExtra, cgvSide;
    private RecyclerView rvSearchResults;
    private Spinner spinnerFilterType;
    private Spinner spinnerFilterType2, spinnerFilterAttribute, spinnerFilterRace, spinnerFilterLimit;
    private Spinner spinnerSortType;
    private EditText etAttack, etDefense, etStar, etScale, etKeyword;
    private EditText etDeckName;
    private Button btnSave, btnSaveAs, btnShuffle, btnSort, btnClear, btnDelete, btnExit;
    private Button btnFilterEffect;
    private Button btnFilterSearch, btnFilterClear;
    private Button btnFilterMarks;
    private Button btnDeckManager;

    private DeckCardAdapter searchAdapter;
    private ImageTop mImageTop;
    private LimitList mLimitList;

    private LinkMarkerPopupWindow linkMarkerPopup;
    private EffectCategoryPopupWindow effectCategoryPopup;

    private String currentDeckCategoryName = "";
    private String currentDeckName = "";
    private String currentDeckFilePath = "";

    private DeckSelectorDialog deckSelectorDialog;

    public DeckEditorManager(Activity activity, ImageLoader imageLoader, CardDetailPanel cardDetailPanel) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        this.cardDetailPanel = cardDetailPanel;
        this.cardLoader = new CardLoader();
        this.currentDeck = new DeckInfo();
        this.searchResults = new ArrayList<>();
    }

    public void setListener(DeckEditorListener listener) {
        this.listener = listener;
    }

    public void initialize(View rootView) {
        this.rootView = rootView;
        bindViews(rootView);
        setupRecyclerViews();
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
        tvLimitTotalNum = root.findViewById(R.id.tv_limit_total_num);
        tvCreditNum = root.findViewById(R.id.tv_credit_num);
        tvRemainNum = root.findViewById(R.id.tv_remain_num);
        tvDeckGenesys = root.findViewById(R.id.tv_deck_genesys);
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
        cgvMain = root.findViewById(R.id.cgv_deck_main);
        cgvExtra = root.findViewById(R.id.cgv_deck_extra);
        cgvSide = root.findViewById(R.id.cgv_deck_side);
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
    }

    private void setupRecyclerViews() {
        mLimitList = AppsSettings.get().getGenesysMode() == 1
                ? cardLoader.getGenesysLimitList()
                : cardLoader.getLimitList();
        mImageTop = new ImageTop(activity);

        cgvMain.setImageLoader(imageLoader);
        cgvMain.setLineLimit(4, 10, 15);

        cgvExtra.setImageLoader(imageLoader);
        cgvExtra.setLineLimit(1, 10, 15);

        cgvSide.setImageLoader(imageLoader);
        cgvSide.setLineLimit(1, 10, 15);

        setupDeckCardSize();

        rvSearchResults.setLayoutManager(new LinearLayoutManager(activity));
        searchAdapter = new DeckCardAdapter(imageLoader, this, null);
        searchAdapter.setLimitList(mLimitList);
        rvSearchResults.setAdapter(searchAdapter);
    }

    /**
     * 根据主卡组区域的实际测量宽高动态计算卡片尺寸，
     * 保证一行能放下 {@link Constants#DECK_WIDTH_COUNT} 张，且主卡组 4 行能完整显示，
     * 三个卡组区域使用同一尺寸。
     */
    private void setupDeckCardSize() {
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

        // 三个网格共需 4+1+1=6 行，按总可用高度统一求卡片尺寸
        int totalAvail = mainAvail + extraAvail + sideAvail;
        int totalLines = 6;

        int widthByColumn = availWidth / Constants.DECK_WIDTH_COUNT;
        int widthByHeight = totalAvail > 0 ? (int) ((totalAvail / (float) totalLines) / ratio) : Integer.MAX_VALUE;

        int cardWidth = Math.max(1, Math.min(widthByColumn, widthByHeight));
        int cardHeight = Math.max(1, (int) (cardWidth * ratio));

        cgvMain.setCardSize(cardWidth, cardHeight);
        cgvExtra.setCardSize(cardWidth, cardHeight);
        cgvSide.setCardSize(cardWidth, cardHeight);
        if (searchAdapter != null) searchAdapter.setCardSize(cardWidth, cardHeight);

        // 将网格高度收缩为正好容纳内容，保证卡片完整显示且铺满网格
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

    public void refreshLimitList() {
        mLimitList = AppsSettings.get().getGenesysMode() == 1
                ? cardLoader.getGenesysLimitList()
                : cardLoader.getLimitList();
        if (cgvMain != null) cgvMain.updateTopImage(mImageTop, mLimitList);
        if (cgvExtra != null) cgvExtra.updateTopImage(mImageTop, mLimitList);
        if (cgvSide != null) cgvSide.updateTopImage(mImageTop, mLimitList);
        if (searchAdapter != null) searchAdapter.setLimitList(mLimitList);
    }

    private void setupSpinners() {
        StringManager sm = DataManager.get().getStringManager();

        List<SimpleSpinnerItem> typeItems = new ArrayList<>();
        typeItems.add(new SimpleSpinnerItem(0, "全部"));
        typeItems.add(new SimpleSpinnerItem(1, "怪兽"));
        typeItems.add(new SimpleSpinnerItem(2, "魔法"));
        typeItems.add(new SimpleSpinnerItem(3, "陷阱"));
        SimpleSpinnerAdapter typeAdapter = new SimpleSpinnerAdapter(activity);
        typeAdapter.setColor(Color.WHITE);
        typeAdapter.setTextSize(8f);
        typeAdapter.set(typeItems);
        if (spinnerFilterType != null) spinnerFilterType.setAdapter(typeAdapter);

        List<SimpleSpinnerItem> type2Items = new ArrayList<>();
        type2Items.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        SimpleSpinnerAdapter type2Adapter = new SimpleSpinnerAdapter(activity);
        type2Adapter.setColor(Color.WHITE);
        type2Adapter.setTextSize(8f);
        type2Adapter.set(type2Items);
        if (spinnerFilterType2 != null) spinnerFilterType2.setAdapter(type2Adapter);

        List<SimpleSpinnerItem> attrItems = new ArrayList<>();
        attrItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        for (CardAttribute attr : CardAttribute.values()) {
            attrItems.add(new SimpleSpinnerItem(attr.getId(),
                    sm.getSystemString(attr.getLanguageIndex(), attr.name())));
        }
        SimpleSpinnerAdapter attrAdapter = new SimpleSpinnerAdapter(activity);
        attrAdapter.setColor(Color.WHITE);
        attrAdapter.setTextSize(8f);
        attrAdapter.set(attrItems);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setAdapter(attrAdapter);

        List<SimpleSpinnerItem> raceItems = new ArrayList<>();
        raceItems.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        for (CardRace race : CardRace.values()) {
            raceItems.add(new SimpleSpinnerItem(race.value(),
                    sm.getSystemString(race.getLanguageIndex(), race.name())));
        }
        SimpleSpinnerAdapter raceAdapter = new SimpleSpinnerAdapter(activity);
        raceAdapter.setColor(Color.WHITE);
        raceAdapter.setTextSize(8f);
        raceAdapter.set(raceItems);
        if (spinnerFilterRace != null) spinnerFilterRace.setAdapter(raceAdapter);

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
        limitItems.add(new SimpleSpinnerItem(12, sm.getSystemString(1485, "无独有")));
        SimpleSpinnerAdapter limitAdapter = new SimpleSpinnerAdapter(activity);
        limitAdapter.setColor(Color.WHITE);
        limitAdapter.setTextSize(8f);
        limitAdapter.set(limitItems);
        if (spinnerFilterLimit != null) spinnerFilterLimit.setAdapter(limitAdapter);

        List<SimpleSpinnerItem> sortItems = new ArrayList<>();
        sortItems.add(new SimpleSpinnerItem(0, "星数↑"));
        sortItems.add(new SimpleSpinnerItem(1, "攻击↑"));
        sortItems.add(new SimpleSpinnerItem(2, "守备↑"));
        sortItems.add(new SimpleSpinnerItem(3, "名称"));
        SimpleSpinnerAdapter sortAdapter = new SimpleSpinnerAdapter(activity);
        sortAdapter.setColor(Color.WHITE);
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

    private void setupDeckSelectorDialog() {
        deckSelectorDialog = new DeckSelectorDialog(activity);
        deckSelectorDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(String deckPath, String deckName, String categoryName) {
                currentDeckFilePath = deckPath;
                currentDeckCategoryName = categoryName;
                currentDeckName = deckName;
                loadDeckFromPath(deckPath);
                updateDeckManagerButtonText();
                AppsSettings.get().saveSettings("lastcategory", categoryName);
                AppsSettings.get().saveSettings("lastdeck", deckName);
            }

            @Override
            public void onCancelled() {
            }
        });

        if (btnDeckManager != null) {
            btnDeckManager.setOnClickListener(v -> showDeckSelectorDialog());
        }
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
        if (deckFile.exists()) {
            DeckInfo loaded = DeckLoader.readDeck(cardLoader, deckFile);
            if (loaded != null) {
                currentDeck.update(loaded);
                currentDeck.source = deckFile;
                notifyDeckChanged();
                isModified = false;
            }
        }
        String aiDeckDir = AppsSettings.get().getAiDeckDir();
        isReadonly = deckPath.startsWith(aiDeckDir);
        refreshReadonly();
    }

    // === 对应 deck_con.cpp: push_main ===
    public boolean pushMain(Card card, int seq) {
        if (card == null) return false;
        if (Card.isExtraCard(card.Type)) return false;
        if (currentDeck.getMainCount() >= Constants.DECK_MAIN_MAX) return false;
        if (!checkLimit(card)) return false;
        boolean result;
        if (seq >= 0 && seq <= currentDeck.mainCards.size()) {
            result = currentDeck.addMainCards(seq, card, false);
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
        int limitCode = (card.Alias > 0) ? card.Alias : card.Code;
        int count = 0;
        for (Card c : currentDeck.mainCards) {
            int cCode = (c.Alias > 0) ? c.Alias : c.Code;
            if (cCode == limitCode) count++;
        }
        for (Card c : currentDeck.extraCards) {
            int cCode = (c.Alias > 0) ? c.Alias : c.Code;
            if (cCode == limitCode) count++;
        }
        for (Card c : currentDeck.sideCards) {
            int cCode = (c.Alias > 0) ? c.Alias : c.Code;
            if (cCode == limitCode) count++;
        }
        return count < 3;
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
        if (btnFilterEffect != null) btnFilterEffect.setText("效果分类");
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) {
            linkMarkerPopup.dismiss();
        }
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing()) {
            effectCategoryPopup.dismiss();
        }
        startFilter();
    }

    // === 对应 deck_con.cpp: SortList ===
    public void sortSearchResults() {
        int sortSel = spinnerSortType != null ? spinnerSortType.getSelectedItemPosition() : 0;
        Comparator<Card> comparator;
        switch (sortSel) {
            case 1:
                comparator = (a, b) -> Integer.compare(a.Attack, b.Attack);
                break;
            case 2:
                comparator = (a, b) -> Integer.compare(a.Defense, b.Defense);
                break;
            case 3:
                comparator = (a, b) -> {
                    String na = a.Name != null ? a.Name : "";
                    String nb = b.Name != null ? b.Name : "";
                    return na.compareTo(nb);
                };
                break;
            default:
                comparator = (a, b) -> Integer.compare(a.Level & 0xff, b.Level & 0xff);
                break;
        }
        Collections.sort(searchResults, comparator);
    }

    // === 对应 deck_con.cpp: RefreshReadonly ===
    public void refreshReadonly() {
        if (btnSave != null) btnSave.setEnabled(!isReadonly);
        if (btnSaveAs != null) btnSaveAs.setEnabled(!isReadonly);
        if (btnClear != null) btnClear.setEnabled(!isReadonly);
        if (btnShuffle != null) btnShuffle.setEnabled(!isReadonly);
        if (btnSort != null) btnSort.setEnabled(!isReadonly);
        if (btnDelete != null) btnDelete.setEnabled(!isReadonly);
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
        if (Card.isExtraCard(card.Type)) {
            if (!pushExtra(card, -1)) {
                pushSide(card, -1);
            }
        } else {
            if (!pushMain(card, -1)) {
                pushSide(card, -1);
            }
        }
    }

    public void onDeckCardClicked(DeckInfo.Type type, int position) {
        Card card = null;
        if (type == DeckInfo.Type.Main) card = currentDeck.getMainCard(position);
        else if (type == DeckInfo.Type.Extra) card = currentDeck.getExtraCard(position);
        else if (type == DeckInfo.Type.Side) card = currentDeck.getSideCard(position);
        if (card != null) showCardInfo(card);
    }

    public void onDeckCardLongClicked(DeckInfo.Type type, int position) {
        if (isReadonly) return;
        if (type == DeckInfo.Type.Main) popMain(position);
        else if (type == DeckInfo.Type.Extra) popExtra(position);
        else if (type == DeckInfo.Type.Side) popSide(position);
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
        int count = groupView.getChildCount();
        for (int i = 0; i < count; i++) {
            CardView cardView = (CardView) groupView.getChildAt(i);
            final int index = i;
            cardView.setOnClickListener(v -> onDeckCardClicked(type, index));
            cardView.setOnLongClickListener(v -> {
                onDeckCardLongClicked(type, index);
                return true;
            });
        }
    }

    private void updateDeckCounts() {
        int mainCount = currentDeck.getMainCount();
        int extraCount = currentDeck.getExtraCount();
        int sideCount = currentDeck.getSideCount();
        int totalCount = mainCount + extraCount + sideCount;
        int mainLimit = Constants.DECK_MAIN_MAX;
        boolean isGenesys = mLimitList != null && mLimitList.getCreditLimits() != null;

        if (tvMainCountNum != null) tvMainCountNum.setText(String.valueOf(mainCount));
        if (tvExtraCountNum != null) tvExtraCountNum.setText(String.valueOf(extraCount));
        if (tvSideCountNum != null) tvSideCountNum.setText(String.valueOf(sideCount));

        if (tvLimitTotalNum != null) {
            if (isGenesys) {
                tvLimitTotalNum.setText(String.valueOf(mLimitList.getCreditLimits()));
            } else {
                tvLimitTotalNum.setText(String.valueOf(mainLimit));
            }
        }
        if (tvCreditNum != null) tvCreditNum.setText(String.valueOf(totalCount));
        if (tvRemainNum != null)
            tvRemainNum.setText(String.valueOf(Math.max(0, mainLimit - mainCount)));

        if (tvDeckGenesys != null) {
            tvDeckGenesys.setVisibility(isGenesys ? View.VISIBLE : View.GONE);
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

    private void updateSearchResultCount() {
        if (tvSearchResult != null)
            tvSearchResult.setText("搜索结果: " + searchResults.size());
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
        } else {
            btnFilterMarks.setText("连接标记");
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
            if (btnFilterEffect != null) {
                btnFilterEffect.setText(filterEffect != 0 ? "效果:*" : "效果分类");
            }
        });
        effectCategoryPopup.show(btnFilterEffect);
    }
}
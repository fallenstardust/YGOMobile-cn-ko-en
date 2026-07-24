package cn.garymb.ygomobile.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.dialogs.DeckSelectorDialog;
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
    private ImageView ivCardImage;
    private TextView tvCardName, tvCardSetname, tvCardAttr, tvCardLevel, tvCardDesc;
    private TextView tvMainCount, tvExtraCount, tvSideCount, tvSearchResult;
    private CardGroupView cgvMain, cgvExtra, cgvSide;
    private RecyclerView rvSearchResults;
    private Spinner spinnerFilterType;
    private Spinner spinnerFilterType2, spinnerFilterAttribute, spinnerFilterRace, spinnerFilterLimit;
    private Spinner spinnerSortType;
    private EditText etAttack, etDefense, etStar, etScale, etKeyword;
    private EditText etDeckName;
    private Button btnSave, btnSaveAs, btnShuffle, btnSort, btnClear, btnDelete, btnExit;
    private Button btnFilterEffect;
    private Button btnDeckManager;

    private DeckCardAdapter searchAdapter;
    private ImageTop mImageTop;
    private LimitList mLimitList;

    private String currentDeckCategoryName = "";
    private String currentDeckName = "";
    private String currentDeckFilePath = "";

    private DeckSelectorDialog deckSelectorDialog;

    public DeckEditorManager(Activity activity, ImageLoader imageLoader) {
        this.activity = activity;
        this.imageLoader = imageLoader;
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
        // 卡片详情面板复用 activity_ygo_game.xml 的 layout_card_detail
        ivCardImage = activity.findViewById(R.id.iv_card_image);
        tvCardName = activity.findViewById(R.id.tv_card_name);
        tvCardSetname = activity.findViewById(R.id.tv_card_setname);
        tvCardAttr = activity.findViewById(R.id.tv_card_attr);
        tvCardLevel = activity.findViewById(R.id.tv_card_level);
        tvCardDesc = activity.findViewById(R.id.tv_card_desc);
        // 卡组操作按钮复用 activity_ygo_game.xml 的 layout_deck_control
        btnShuffle = activity.findViewById(R.id.btn_deck_shuffle);
        btnSort = activity.findViewById(R.id.btn_deck_sort);
        btnClear = activity.findViewById(R.id.btn_deck_clear);
        btnDelete = activity.findViewById(R.id.btn_deck_delete);
        btnExit = activity.findViewById(R.id.btn_deck_exit);
        // 以下为卡组编辑器自身布局 (layout_deck_editor.xml)
        tvMainCount = root.findViewById(R.id.tv_deck_main_count);
        tvExtraCount = root.findViewById(R.id.tv_deck_extra_count);
        tvSideCount = root.findViewById(R.id.tv_deck_side_count);
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
                int width = cgvMain.getWidth();
                int height = cgvMain.getHeight();
                if (width <= 0 || height <= 0) return;
                cgvMain.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                applyDeckCardSize(width, height);
            }
        });
    }

    private void applyDeckCardSize(int mainWidth, int mainHeight) {
        int availWidth = mainWidth - cgvMain.getPaddingLeft() - cgvMain.getPaddingRight();
        int availHeight = mainHeight - cgvMain.getPaddingTop() - cgvMain.getPaddingBottom();
        if (availWidth <= 0 || availHeight <= 0) return;

        float ratio = (float) Constants.CORE_SKIN_CARD_SMALL_SIZE[1] / (float) Constants.CORE_SKIN_CARD_SMALL_SIZE[0];
        // 宽度约束：一行 DECK_WIDTH_COUNT 张
        int widthByColumn = availWidth / Constants.DECK_WIDTH_COUNT;
        // 高度约束：主卡组默认 4 行必须完整显示
        int widthByRow = (int) ((availHeight / 4f) / ratio);
        int cardWidth = Math.max(1, Math.min(widthByColumn, widthByRow));
        int cardHeight = Math.round(cardWidth * ratio);

        cgvMain.setCardSize(cardWidth, cardHeight);
        cgvExtra.setCardSize(cardWidth, cardHeight);
        cgvSide.setCardSize(cardWidth, cardHeight);

        notifyDeckChanged();
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
        typeAdapter.setTextSize(8f);
        typeAdapter.set(typeItems);
        if (spinnerFilterType != null) spinnerFilterType.setAdapter(typeAdapter);

        List<SimpleSpinnerItem> type2Items = new ArrayList<>();
        type2Items.add(new SimpleSpinnerItem(0, sm.getSystemString(1310, "（无）")));
        SimpleSpinnerAdapter type2Adapter = new SimpleSpinnerAdapter(activity);
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
        limitAdapter.setTextSize(8f);
        limitAdapter.set(limitItems);
        if (spinnerFilterLimit != null) spinnerFilterLimit.setAdapter(limitAdapter);

        List<SimpleSpinnerItem> sortItems = new ArrayList<>();
        sortItems.add(new SimpleSpinnerItem(0, "星数↑"));
        sortItems.add(new SimpleSpinnerItem(1, "攻击↑"));
        sortItems.add(new SimpleSpinnerItem(2, "守备↑"));
        sortItems.add(new SimpleSpinnerItem(3, "名称"));
        SimpleSpinnerAdapter sortAdapter = new SimpleSpinnerAdapter(activity);
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
        if (btnFilterEffect != null) btnFilterEffect.setOnClickListener(v -> startFilter());
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
        filterLm = spinnerFilterLimit != null ? spinnerFilterLimit.getSelectedItemPosition() : 0;
        filterAtk = parseFilter(etAttack != null ? etAttack.getText().toString() : "");
        filterDef = parseFilter(etDefense != null ? etDefense.getText().toString() : "");
        filterLv = parseFilter(etStar != null ? etStar.getText().toString() : "");
        filterScl = parseFilter(etScale != null ? etScale.getText().toString() : "");
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
        if (etAttack != null) etAttack.setText("");
        if (etDefense != null) etDefense.setText("");
        if (etStar != null) etStar.setText("");
        if (etScale != null) etScale.setText("");
        if (etKeyword != null) etKeyword.setText("");
        filterEffect = 0;
        filterMarks = 0;
        searchResults.clear();
        updateSearchResultCount();
        if (searchAdapter != null) searchAdapter.setCards(searchResults);
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
        if (ivCardImage != null) imageLoader.bindImage(ivCardImage, card, ImageLoader.Type.middle);
        if (tvCardName != null)
            tvCardName.setText((card.Name != null ? card.Name : "Unknown") + "[" + card.Code + "]");
        bindCardSetname(card);
        if (tvCardAttr != null) tvCardAttr.setText(getCardTypeString(card));
        if (tvCardLevel != null) {
            if (Card.isType(card.Type, CardType.Spell) || Card.isType(card.Type, CardType.Trap)) {
                tvCardLevel.setText("");
                tvCardLevel.setVisibility(View.GONE);
            } else {
                tvCardLevel.setText(getCardLevelString(card));
                tvCardLevel.setVisibility(View.VISIBLE);
            }
        }
        if (tvCardDesc != null) tvCardDesc.setText(card.Desc != null ? card.Desc : "");
        if (listener != null) listener.onCardSelected(card);
    }

    private void bindCardSetname(Card card) {
        if (tvCardSetname == null) return;
        long[] setCodes = card.getSetCode();
        StringBuilder sb = new StringBuilder();
        boolean hasSet = false;
        for (long sc : setCodes) {
            if (sc == 0) continue;
            if (hasSet) sb.append("|");
            sb.append(DataManager.get().getStringManager().getSetName(sc));
            hasSet = true;
        }
        if (hasSet) {
            tvCardSetname.setText("字段：" + sb);
            tvCardSetname.setVisibility(View.VISIBLE);
        } else {
            tvCardSetname.setVisibility(View.GONE);
        }
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
        if (tvMainCount != null)
            tvMainCount.setText("主卡组: " + currentDeck.getMainCount());
        if (tvExtraCount != null)
            tvExtraCount.setText("额外卡组: " + currentDeck.getExtraCount());
        if (tvSideCount != null)
            tvSideCount.setText("副卡组: " + currentDeck.getSideCount());
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

    private int parseFilter(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            if (text.startsWith(">=")) return Integer.parseInt(text.substring(2));
            if (text.startsWith("<=")) return Integer.parseInt(text.substring(2));
            if (text.startsWith(">")) return Integer.parseInt(text.substring(1));
            if (text.startsWith("<")) return Integer.parseInt(text.substring(1));
            if (text.startsWith("=")) return Integer.parseInt(text.substring(1));
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
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
                if (etDeckName != null) {
                    etDeckName.setText(currentDeckName);
                }
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
                if (etDeckName != null) {
                    etDeckName.setText(currentDeckName);
                }
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

    private String getCardTypeString(Card card) {
        StringBuilder sb = new StringBuilder("[");
        if (Card.isType(card.Type, CardType.Monster)) sb.append("怪兽");
        else if (Card.isType(card.Type, CardType.Spell)) sb.append("魔法");
        else if (Card.isType(card.Type, CardType.Trap)) sb.append("陷阱");
        if (Card.isType(card.Type, CardType.Effect)) sb.append("|效果");
        if (Card.isType(card.Type, CardType.Fusion)) sb.append("|融合");
        if (Card.isType(card.Type, CardType.Synchro)) sb.append("|同调");
        if (Card.isType(card.Type, CardType.Xyz)) sb.append("|超量");
        if (Card.isType(card.Type, CardType.Link)) sb.append("|连接");
        if (Card.isType(card.Type, CardType.Pendulum)) sb.append("|灵摆");
        sb.append("]");
        return sb.toString();
    }

    private String getCardLevelString(Card card) {
        int star = card.Level & 0xff;
        StringBuilder sb = new StringBuilder();
        if (Card.isType(card.Type, CardType.Link)) {
            sb.append("[LINK-").append(star).append("] ");
        } else if (Card.isType(card.Type, CardType.Xyz)) {
            sb.append("[☆").append(star).append("] ");
        } else {
            sb.append("[★").append(star).append("] ");
        }
        if (Card.isType(card.Type, CardType.Monster)) {
            sb.append(card.Attack).append("/");
            if (Card.isType(card.Type, CardType.Link)) {
                sb.append("-");
            } else {
                sb.append(card.Defense);
            }
        }
        return sb.toString();
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
}
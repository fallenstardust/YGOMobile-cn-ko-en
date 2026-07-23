package cn.garymb.ygomobile.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
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
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;

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
    private int prevCategory = 0;
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
    private TextView tvCardName, tvCardAttr, tvCardLevel, tvCardDesc;
    private TextView tvMainCount, tvExtraCount, tvSideCount, tvSearchResult;
    private RecyclerView rvMain, rvExtra, rvSide, rvSearchResults;
    private Spinner spinnerCategory, spinnerDeckList, spinnerFilterType;
    private Spinner spinnerFilterType2, spinnerFilterAttribute, spinnerFilterRace, spinnerFilterLimit;
    private Spinner spinnerSortType;
    private EditText etAttack, etDefense, etStar, etScale, etKeyword;
    private Button btnSave, btnSaveAs, btnShuffle, btnSort, btnClear, btnDelete, btnExit;
    private Button btnFilterEffect;

    private DeckCardAdapter mainAdapter, extraAdapter, sideAdapter, searchAdapter;

    private List<String> categoryList = new ArrayList<>();
    private List<String> deckNameList = new ArrayList<>();
    private String currentCategoryPath = "";

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
        refreshCategoryList();
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
        ivCardImage = root.findViewById(R.id.iv_deck_card_image);
        tvCardName = root.findViewById(R.id.tv_deck_card_name);
        tvCardAttr = root.findViewById(R.id.tv_deck_card_attr);
        tvCardLevel = root.findViewById(R.id.tv_deck_card_level);
        tvCardDesc = root.findViewById(R.id.tv_deck_card_desc);
        tvMainCount = root.findViewById(R.id.tv_deck_main_count);
        tvExtraCount = root.findViewById(R.id.tv_deck_extra_count);
        tvSideCount = root.findViewById(R.id.tv_deck_side_count);
        tvSearchResult = root.findViewById(R.id.tv_deck_search_result);
        rvMain = root.findViewById(R.id.rv_deck_main);
        rvExtra = root.findViewById(R.id.rv_deck_extra);
        rvSide = root.findViewById(R.id.rv_deck_side);
        rvSearchResults = root.findViewById(R.id.rv_deck_search_results);
        spinnerCategory = root.findViewById(R.id.spinner_deck_category);
        spinnerDeckList = root.findViewById(R.id.spinner_deck_list);
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
        btnShuffle = root.findViewById(R.id.btn_deck_shuffle);
        btnSort = root.findViewById(R.id.btn_deck_sort);
        btnClear = root.findViewById(R.id.btn_deck_clear);
        btnDelete = root.findViewById(R.id.btn_deck_delete);
        btnExit = root.findViewById(R.id.btn_deck_exit);
        btnFilterEffect = root.findViewById(R.id.btn_filter_effect);
    }

    private void setupRecyclerViews() {
        int columns = Constants.DECK_WIDTH_COUNT;

        LimitList limitList = AppsSettings.get().getGenesysMode() == 1
                ? cardLoader.getGenesysLimitList()
                : cardLoader.getLimitList();

        rvMain.setLayoutManager(new GridLayoutManager(activity, columns));
        mainAdapter = new DeckCardAdapter(imageLoader, this, DeckInfo.Type.Main);
        mainAdapter.setLimitList(limitList);
        rvMain.setAdapter(mainAdapter);

        rvExtra.setLayoutManager(new GridLayoutManager(activity, columns));
        extraAdapter = new DeckCardAdapter(imageLoader, this, DeckInfo.Type.Extra);
        extraAdapter.setLimitList(limitList);
        rvExtra.setAdapter(extraAdapter);

        rvSide.setLayoutManager(new GridLayoutManager(activity, columns));
        sideAdapter = new DeckCardAdapter(imageLoader, this, DeckInfo.Type.Side);
        sideAdapter.setLimitList(limitList);
        rvSide.setAdapter(sideAdapter);

        rvSearchResults.setLayoutManager(new GridLayoutManager(activity, 3));
        searchAdapter = new DeckCardAdapter(imageLoader, this, null);
        searchAdapter.setLimitList(limitList);
        rvSearchResults.setAdapter(searchAdapter);
    }

    private void setupSpinners() {
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                new String[]{"全部", "怪兽", "魔法", "陷阱"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerFilterType != null) spinnerFilterType.setAdapter(typeAdapter);

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                new String[]{"星数↑", "攻击↑", "守备↑", "名称"});
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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
        int sel = spinnerDeckList != null ? spinnerDeckList.getSelectedItemPosition() : -1;
        if (sel < 0 || sel >= deckNameList.size()) return;
        String deckName = deckNameList.get(sel);
        showConfirmDialog(deckName + "\n是否删除这个卡组？", () -> {
            File deckFile = getDeckFile(deckName);
            if (deckFile != null && deckFile.exists()) {
                deckFile.delete();
                deckNameList.remove(sel);
                refreshDeckSpinner();
                if (!deckNameList.isEmpty()) {
                    int newSel = Math.min(sel, deckNameList.size() - 1);
                    if (spinnerDeckList != null) spinnerDeckList.setSelection(newSel);
                    loadDeckByName(deckNameList.get(newSel));
                } else {
                    currentDeck.mainCards.clear();
                    currentDeck.extraCards.clear();
                    currentDeck.sideCards.clear();
                    notifyDeckChanged();
                }
                isModified = false;
                showToast("卡组已删除");
            }
        });
    }

    // === 对应 deck_con.cpp: BUTTON_SAVE_DECK ===
    public void saveDeck() {
        if (isReadonly) return;
        int sel = spinnerDeckList != null ? spinnerDeckList.getSelectedItemPosition() : -1;
        if (sel < 0 || sel >= deckNameList.size()) return;
        String deckName = deckNameList.get(sel);
        File deckFile = getDeckFile(deckName);
        if (deckFile != null) {
            boolean result = DeckUtils.save(currentDeck, deckFile);
            if (result) {
                isModified = false;
                showToast("卡组已保存");
                if (listener != null) listener.onDeckSaved();
            }
        }
    }

    // === 对应 deck_con.cpp: BUTTON_SAVE_DECK_AS ===
    public void saveDeckAs() {
        if (isReadonly) return;
        EditText input = new EditText(activity);
        input.setHint("输入新卡组名称");
        new AlertDialog.Builder(activity)
                .setTitle("另存为")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File deckFile = getDeckFile(name);
                    if (deckFile != null) {
                        DeckUtils.save(currentDeck, deckFile);
                        isModified = false;
                        refreshDeckList();
                        showToast("卡组已保存为: " + name);
                        if (listener != null) listener.onDeckSaved();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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

    // === 对应 deck_con.cpp: ChangeCategory ===
    public void changeCategory(int catesel) {
        prevCategory = catesel;
        isReadonly = catesel < 2;
        refreshReadonly();
        refreshDeckList();
        if (!deckNameList.isEmpty()) {
            loadDeckByName(deckNameList.get(0));
        }
        isModified = false;
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

    // === 对应 deck_con.cpp: RefreshDeckList ===
    public void refreshDeckList() {
        deckNameList.clear();
        File deckDir = getDeckDir();
        if (deckDir != null && deckDir.isDirectory()) {
            File[] files = deckDir.listFiles((dir, name) -> name.endsWith(".ydk"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().replace(".ydk", "");
                    deckNameList.add(name);
                }
            }
        }
        Collections.sort(deckNameList);
        refreshDeckSpinner();
    }

    public void showCardInfo(Card card) {
        if (card == null) return;
        if (ivCardImage != null) imageLoader.bindImage(ivCardImage, card, ImageLoader.Type.middle);
        if (tvCardName != null)
            tvCardName.setText((card.Name != null ? card.Name : "Unknown") + "[" + card.Code + "]");
        if (tvCardAttr != null) tvCardAttr.setText(getCardTypeString(card));
        if (tvCardLevel != null) tvCardLevel.setText(getCardLevelString(card));
        if (tvCardDesc != null) tvCardDesc.setText(card.Desc != null ? card.Desc : "");
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
        if (mainAdapter != null) mainAdapter.setCards(currentDeck.mainCards);
        if (extraAdapter != null) extraAdapter.setCards(currentDeck.extraCards);
        if (sideAdapter != null) sideAdapter.setCards(currentDeck.sideCards);
        if (listener != null) listener.onDeckModified();
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

    private void refreshCategoryList() {
        categoryList.clear();
        categoryList.add("卡包展示");
        categoryList.add("人机卡组");
        categoryList.add("未分类卡组");
        File deckBaseDir = new File(AppsSettings.get().getResourcePath(), "deck");
        if (deckBaseDir.isDirectory()) {
            File[] dirs = deckBaseDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    categoryList.add(dir.getName());
                }
            }
        }
        if (spinnerCategory != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_item, categoryList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategory.setAdapter(adapter);
            spinnerCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    changeCategory(position);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }
    }

    private void refreshDeckSpinner() {
        if (spinnerDeckList != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_item, deckNameList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerDeckList.setAdapter(adapter);
            spinnerDeckList.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < deckNameList.size()) {
                        if (isModified && !isReadonly) {
                            showConfirmDialog("此操作将放弃对当前卡组的修改，是否继续？", () -> {
                                loadDeckByName(deckNameList.get(position));
                                isModified = false;
                            });
                        } else {
                            loadDeckByName(deckNameList.get(position));
                        }
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }
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
        String lastCategory = AppsSettings.get().getLastCategory();
        String lastDeck = AppsSettings.get().getLastDeckName();
        if (lastCategory != null && !lastCategory.isEmpty()) {
            int idx = categoryList.indexOf(lastCategory);
            if (idx >= 0 && spinnerCategory != null) {
                spinnerCategory.setSelection(idx);
            }
        }
        refreshDeckList();
        if (lastDeck != null && !lastDeck.isEmpty()) {
            int idx = deckNameList.indexOf(lastDeck);
            if (idx >= 0 && spinnerDeckList != null) {
                spinnerDeckList.setSelection(idx);
            }
        } else if (!deckNameList.isEmpty()) {
            loadDeckByName(deckNameList.get(0));
        }
    }

    private void saveLastCategoryAndDeck() {
        int catSel = spinnerCategory != null ? spinnerCategory.getSelectedItemPosition() : 0;
        if (catSel >= 0 && catSel < categoryList.size()) {
            AppsSettings.get().saveSettings("lastcategory", categoryList.get(catSel));
        }
        int deckSel = spinnerDeckList != null ? spinnerDeckList.getSelectedItemPosition() : 0;
        if (deckSel >= 0 && deckSel < deckNameList.size()) {
            AppsSettings.get().saveSettings("lastdeck", deckNameList.get(deckSel));
        }
    }

    private File getDeckDir() {
        if (prevCategory < 2) {
            return null;
        } else if (prevCategory == 2) {
            return new File(AppsSettings.get().getResourcePath(), "deck");
        } else {
            int dirIdx = prevCategory - 3;
            File deckBaseDir = new File(AppsSettings.get().getResourcePath(), "deck");
            File[] dirs = deckBaseDir.listFiles(File::isDirectory);
            if (dirs != null && dirIdx < dirs.length) {
                return dirs[dirIdx];
            }
            return new File(AppsSettings.get().getResourcePath(), "deck");
        }
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
}
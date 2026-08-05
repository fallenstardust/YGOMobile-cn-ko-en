package cn.garymb.ygomobile.game;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.dialogs.EffectCategoryPopupWindow;
import cn.garymb.ygomobile.ui.dialogs.LinkMarkerPopupWindow;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

/*
* ygopro横屏卡组编辑的搜索器部分
* 由deckeditorManager调用此处代码
* */
public class CardSearcherManager {

    private static final String TAG = "CardSearcherManager";

    public interface SearchListener {
        void onSearchResultsUpdated(int count);
    }

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final StringManager mStringManager = DataManager.get().getStringManager();

    private final List<Card> searchResults = new ArrayList<>();
    private SearchListener listener;

    private TextView tvSearchResult;
    private RecyclerView rvSearchResults;
    private Spinner spinnerFilterType;
    private Spinner spinnerFilterType2, spinnerFilterAttribute, spinnerFilterRace, spinnerFilterLimit;
    private Spinner spinnerSortType;
    private SimpleSpinnerAdapter attrAdapter, raceAdapter;
    private EditText etAttack, etDefense, etStar, etScale, etKeyword;
    private Button btnFilterEffect, btnFilterSearch, btnFilterClear, btnFilterMarks;

    private DeckCardAdapter searchAdapter;
    private LinkMarkerPopupWindow linkMarkerPopup;
    private EffectCategoryPopupWindow effectCategoryPopup;

    private String searchResultPrefix = "搜索结果:";
    private Runnable pendingKeywordSearch;
    private final AtomicInteger searchGeneration = new AtomicInteger(0);
    private TextWatcher keywordTextWatcher;

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

    private LimitList mLimitList;

    public CardSearcherManager(Activity activity) {
        this.activity = activity;
    }

    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    public void setLimitList(LimitList limitList) {
        this.mLimitList = limitList;
        if (searchAdapter != null) searchAdapter.setLimitList(mLimitList);
    }

    public void setCardSize(int w, int h) {
        if (searchAdapter != null) searchAdapter.setCardSize(w, h);
    }

    public void setDragState(int touchSlop, boolean disabled) {
        if (searchAdapter != null) searchAdapter.setDragState(touchSlop, disabled);
    }

    public RecyclerView getSearchRecyclerView() {
        return rvSearchResults;
    }

    public DeckCardAdapter getSearchAdapter() {
        return searchAdapter;
    }

    public List<Card> getSearchResults() {
        return searchResults;
    }

    public void bindViews(View root) {
        tvSearchResult = root.findViewById(R.id.tv_deck_search_result);
        rvSearchResults = root.findViewById(R.id.rv_deck_search_results);
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
        btnFilterEffect = root.findViewById(R.id.btn_filter_effect);
        btnFilterMarks = root.findViewById(R.id.btn_filter_marks);
        btnFilterSearch = root.findViewById(R.id.btn_filter_search);
        btnFilterClear = root.findViewById(R.id.btn_filter_clear);
    }

    public void setupLabels() {
        searchResultPrefix = mStringManager.getSystemString(1333, "搜索结果:");
    }

    public void setupSearchRecyclerView(ImageLoader imageLoader, CardDragHelper dragHelper,
                                        DeckEditorManager editorManager) {
        rvSearchResults.setLayoutManager(new LinearLayoutManager(activity));
        searchAdapter = new DeckCardAdapter(imageLoader, editorManager, null, dragHelper);
        searchAdapter.setLimitList(mLimitList);
        rvSearchResults.setAdapter(searchAdapter);
    }

    public void setupSpinners() {
        int dropBg = YGOUtil.c(R.color.ygopro_list_background);
        List<SimpleSpinnerItem> typeItems = new ArrayList<>();
        typeItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        typeItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1312, "怪兽")));
        typeItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1313, "魔法")));
        typeItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1314, "陷阱")));
        if (spinnerFilterType != null) {
            spinnerFilterType.setAdapter(createSpinnerAdapter(typeItems, Color.WHITE, dropBg));
            spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    updateType2Spinner(pos);
                }

                @Override
                public void onNothingSelected(AdapterView<?> p) {
                    updateType2Spinner(0);
                }
            });
        }
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);
        if (spinnerFilterType2 != null) {
            spinnerFilterType2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    updateDefenseEditState();
                }

                @Override
                public void onNothingSelected(AdapterView<?> p) {
                    updateDefenseEditState();
                }
            });
        }
        List<SimpleSpinnerItem> attrItems = new ArrayList<>();
        attrItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        for (CardAttribute attr : CardAttribute.values())
            attrItems.add(new SimpleSpinnerItem(attr.getId(), mStringManager.getSystemString(attr.getLanguageIndex(), attr.name())));
        attrAdapter = createSpinnerAdapter(attrItems, Color.WHITE, dropBg);
        if (spinnerFilterAttribute != null) spinnerFilterAttribute.setAdapter(attrAdapter);
        List<SimpleSpinnerItem> raceItems = new ArrayList<>();
        raceItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        for (CardRace race : CardRace.values())
            raceItems.add(new SimpleSpinnerItem(race.value(), mStringManager.getSystemString(race.getLanguageIndex(), race.name())));
        raceAdapter = createSpinnerAdapter(raceItems, Color.WHITE, dropBg);
        if (spinnerFilterRace != null) spinnerFilterRace.setAdapter(raceAdapter);
        updateType2Spinner(spinnerFilterType != null ? spinnerFilterType.getSelectedItemPosition() : 0);
        List<SimpleSpinnerItem> limitItems = new ArrayList<>();
        limitItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1310, "（无）")));
        limitItems.add(new SimpleSpinnerItem(LimitType.Forbidden.getId(), mStringManager.getSystemString(LimitType.Forbidden.getLanguageIndex(), LimitType.Forbidden.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.Limit.getId(), mStringManager.getSystemString(LimitType.Limit.getLanguageIndex(), LimitType.Limit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.SemiLimit.getId(), mStringManager.getSystemString(LimitType.SemiLimit.getLanguageIndex(), LimitType.SemiLimit.name())));
        limitItems.add(new SimpleSpinnerItem(LimitType.GeneSys.getId(), mStringManager.getSystemString(LimitType.GeneSys.getLanguageIndex(), LimitType.GeneSys.name())));
        limitItems.add(new SimpleSpinnerItem(6, mStringManager.getSystemString(1481, "OCG")));
        limitItems.add(new SimpleSpinnerItem(7, mStringManager.getSystemString(1482, "TCG")));
        limitItems.add(new SimpleSpinnerItem(8, mStringManager.getSystemString(1483, "简体中文")));
        limitItems.add(new SimpleSpinnerItem(9, mStringManager.getSystemString(1484, "自定义")));
        limitItems.add(new SimpleSpinnerItem(10, mStringManager.getSystemString(1487, "OCG独有")));
        limitItems.add(new SimpleSpinnerItem(11, mStringManager.getSystemString(1488, "TCG独有")));
        limitItems.add(new SimpleSpinnerItem(12, mStringManager.getSystemString(1485, "无独有卡")));
        if (spinnerFilterLimit != null) {
            spinnerFilterLimit.setAdapter(createSpinnerAdapter(limitItems, Color.WHITE, dropBg));
            spinnerFilterLimit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    // availLm 更新由 DeckEditorManager 通过 onAvailDisplayChanged 回调处理
                }

                @Override
                public void onNothingSelected(AdapterView<?> p) {
                }
            });
        }
        List<SimpleSpinnerItem> sortItems = new ArrayList<>();
        sortItems.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1370, "星数↑")));
        sortItems.add(new SimpleSpinnerItem(1, mStringManager.getSystemString(1371, "攻击↑")));
        sortItems.add(new SimpleSpinnerItem(2, mStringManager.getSystemString(1372, "守备↑")));
        sortItems.add(new SimpleSpinnerItem(3, mStringManager.getSystemString(1373, "名称↓")));
        if (spinnerSortType != null)
            spinnerSortType.setAdapter(createSpinnerAdapter(sortItems, Color.WHITE, dropBg));
    }

    public void setupButtons() {
        setClickListener(btnFilterEffect, v -> showEffectCategoryPopup());
        setClickListener(btnFilterMarks, v -> showLinkMarkerPopup());
        setClickListener(btnFilterSearch, v -> startFilter());
        setClickListener(btnFilterClear, v -> clearSearch());
        setupKeywordInput();
    }

    private void setClickListener(Button btn, View.OnClickListener l) {
        if (btn != null) btn.setOnClickListener(l);
    }

    public int getSelectedSortType() {
        return spinnerSortType != null ? spinnerSortType.getSelectedItemPosition() : 0;
    }

    public int getSelectedFilterLimit() {
        return (int) SimpleSpinnerAdapter.getSelect(spinnerFilterLimit);
    }

    // === 对应 deck_con.cpp: StartFilter / FilterCards ===
    public void startFilter() {
        if (pendingKeywordSearch != null) {
            mainHandler.removeCallbacks(pendingKeywordSearch);
            pendingKeywordSearch = null;
        }
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
        executeFilterAsync();
    }

    private void executeFilterAsync() {
        if (tvSearchResult != null) tvSearchResult.setText(searchResultPrefix + " ...");
        int gen = searchGeneration.incrementAndGet();
        List<String> keywordTerms = parseKeywordTerms();
        new Thread(() -> {
            List<Card> results = new ArrayList<>();
            SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
            for (int i = 0; i < allCards.size(); i++) {
                if (searchGeneration.get() != gen) return;
                Card card = allCards.valueAt(i);
                if (card == null || Card.isType(card.Type, CardType.Token)) continue;
                if (!matchesAllFilters(card, keywordTerms)) continue;
                results.add(card);
            }
            Collections.sort(results, buildSortComparator());
            mainHandler.post(() -> applySearchResults(gen, results));
        }).start();
    }

    private void applySearchResults(int gen, List<Card> results) {
        if (searchGeneration.get() != gen) return;
        searchResults.clear();
        searchResults.addAll(results);
        if (tvSearchResult != null)
            tvSearchResult.setText(searchResultPrefix + " " + searchResults.size());
        if (searchAdapter != null) searchAdapter.setCards(searchResults);
        if (rvSearchResults != null) rvSearchResults.scrollToPosition(0);
        if (listener != null) listener.onSearchResultsUpdated(searchResults.size());
    }

    public Comparator<Card> buildSortComparator() {
        int sortSel = spinnerSortType != null ? spinnerSortType.getSelectedItemPosition() : 0;
        return (a, b) -> {
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
    }

    private boolean matchesAllFilters(Card card, List<String> keywordTerms) {
        if (!matchesTypeFilter(card) || !matchesType2Filter(card)) return false;
        if (!matchesKeywordFilter(card, keywordTerms) || !matchesLimitFilter(card)) return false;

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
        if (pendingKeywordSearch != null) {
            mainHandler.removeCallbacks(pendingKeywordSearch);
            pendingKeywordSearch = null;
        }
        if (etKeyword != null && keywordTextWatcher != null) {
            etKeyword.removeTextChangedListener(keywordTextWatcher);
        }
        if (etAttack != null) etAttack.setText("");
        if (etDefense != null) etDefense.setText("");
        if (etStar != null) etStar.setText("");
        if (etScale != null) etScale.setText("");
        if (etKeyword != null) etKeyword.setText("");
        if (etKeyword != null && keywordTextWatcher != null) {
            etKeyword.addTextChangedListener(keywordTextWatcher);
        }
    }

    // === 对应 deck_con.cpp: SortList ===
    public void sortSearchResults() {
        Collections.sort(searchResults, buildSortComparator());
    }

    private int getCardClassRank(Card card) {
        return Card.isType(card.Type, CardType.Monster) ? 0 : Card.isType(card.Type, CardType.Spell) ? 1 : Card.isType(card.Type, CardType.Trap) ? 2 : 3;
    }

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

    private boolean matchesType2Filter(Card card) {
        if (filterType2 == 0) return true;
        if (filterType == 1) {
            return (card.Type & filterType2) == filterType2;
        }
        if (filterType == 2 || filterType == 3) {
            return card.Type == filterType2;
        }
        return true;
    }

    private boolean matchesKeywordFilter(Card card, List<String> terms) {
        if (terms.isEmpty()) return true;
        for (String term : terms) {
            boolean exclude = term.startsWith("-") && term.length() > 1;
            String body = exclude ? term.substring(1) : term;
            boolean matched = false;
            if (body.startsWith("@") && body.length() > 1) {
                String setName = body.substring(1);
                long setcode = mStringManager.getSetCode(setName, true);
                if (setcode != 0 && card.isSetCode(setcode)) matched = true;
            } else {
                String searchText = body.isEmpty() ? term : body;
                if (card.Name != null && card.Name.toLowerCase().contains(searchText))
                    matched = true;
                if (card.Desc != null && card.Desc.toLowerCase().contains(searchText))
                    matched = true;
                if (String.valueOf(card.Code).equals(searchText)) matched = true;
            }
            if (exclude && matched) return false;
            if (!exclude && !matched) return false;
        }
        return true;
    }

    private List<String> parseKeywordTerms() {
        String raw = etKeyword != null ? etKeyword.getText().toString().trim() : "";
        List<String> terms = new ArrayList<>();
        if (raw.isEmpty()) return terms;
        raw = raw.toLowerCase();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                if (current.length() > 0) {
                    terms.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) terms.add(current.toString());
        return terms;
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

    private void setupKeywordInput() {
        if (etKeyword == null) return;
        etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startFilter();
                return true;
            }
            return false;
        });
        keywordTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingKeywordSearch != null) mainHandler.removeCallbacks(pendingKeywordSearch);
                pendingKeywordSearch = () -> startFilter();
                mainHandler.postDelayed(pendingKeywordSearch, 300);
            }
        };
        etKeyword.addTextChangedListener(keywordTextWatcher);
    }

    private void updateType2Spinner(int typePos) {
        if (spinnerFilterType2 == null) return;
        boolean enabled = typePos != 0;
        List<SimpleSpinnerItem> items = new ArrayList<>();
        items.add(new SimpleSpinnerItem(0, mStringManager.getSystemString(1080, "（N/A）")));
        long m = CardType.Monster.getId();
        switch (typePos) {
            case 1:
                addSpinnerItems(items, m);
                break;
            case 2:
                items.add(new SimpleSpinnerItem(CardType.Spell.getId(), mStringManager.getSystemString(1054, "通常")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.QuickPlay.getId(), mStringManager.getSystemString(1066, "速攻")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Continuous.getId(), mStringManager.getSystemString(1067, "永续")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Ritual.getId(), mStringManager.getSystemString(1057, "仪式")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Equip.getId(), mStringManager.getSystemString(1068, "装备")));
                items.add(new SimpleSpinnerItem(CardType.Spell.getId() | CardType.Field.getId(), mStringManager.getSystemString(1069, "场地")));
                break;
            case 3:
                items.add(new SimpleSpinnerItem(CardType.Trap.getId(), mStringManager.getSystemString(1054, "通常")));
                items.add(new SimpleSpinnerItem(CardType.Trap.getId() | CardType.Continuous.getId(), mStringManager.getSystemString(1067, "永续")));
                items.add(new SimpleSpinnerItem(CardType.Trap.getId() | CardType.Counter.getId(), mStringManager.getSystemString(1070, "反击")));
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

    private void addSpinnerItems(List<SimpleSpinnerItem> items, long m) {
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId(), mStringManager.getSystemString(1054, "通常")));
        items.add(new SimpleSpinnerItem(m | CardType.Effect.getId(), mStringManager.getSystemString(1055, "效果")));
        items.add(new SimpleSpinnerItem(m | CardType.Fusion.getId(), mStringManager.getSystemString(1056, "融合")));
        items.add(new SimpleSpinnerItem(m | CardType.Ritual.getId(), mStringManager.getSystemString(1057, "仪式")));
        items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId(), mStringManager.getSystemString(1063, "同调")));
        items.add(new SimpleSpinnerItem(m | CardType.Xyz.getId(), mStringManager.getSystemString(1073, "超量")));
        items.add(new SimpleSpinnerItem(m | CardType.Pendulum.getId(), mStringManager.getSystemString(1074, "灵摆")));
        items.add(new SimpleSpinnerItem(m | CardType.Link.getId(), mStringManager.getSystemString(1076, "连接")));
        items.add(new SimpleSpinnerItem(m | CardType.Sp_Summon.getId(), mStringManager.getSystemString(1075, "特殊召唤")));
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Tuner.getId(), mStringManager.getSystemString(1054, "通常") + "|" + mStringManager.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Normal.getId() | CardType.Pendulum.getId(), mStringManager.getSystemString(1054, "通常") + "|" + mStringManager.getSystemString(1074, "灵摆")));
        items.add(new SimpleSpinnerItem(m | CardType.Synchro.getId() | CardType.Tuner.getId(), mStringManager.getSystemString(1063, "同调") + "|" + mStringManager.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Tuner.getId(), mStringManager.getSystemString(1062, "调整")));
        items.add(new SimpleSpinnerItem(m | CardType.Gemini.getId(), mStringManager.getSystemString(1061, "二重")));
        items.add(new SimpleSpinnerItem(m | CardType.Union.getId(), mStringManager.getSystemString(1060, "同盟")));
        items.add(new SimpleSpinnerItem(m | CardType.Spirit.getId(), mStringManager.getSystemString(1059, "灵魂")));
        items.add(new SimpleSpinnerItem(m | CardType.Flip.getId(), mStringManager.getSystemString(1071, "反转")));
        items.add(new SimpleSpinnerItem(m | CardType.Toon.getId(), mStringManager.getSystemString(1072, "卡通")));
    }

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

    private void setEditTextEnabled(EditText et, boolean enabled) {
        if (et == null) return;
        et.setEnabled(enabled);
        if (!enabled) {
            et.setText("");
        }
        et.setBackground(activity.getDrawable(enabled ? R.drawable.ygopro_base_background : R.drawable.ygopro_unable_background));
    }

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

    private SimpleSpinnerAdapter createSpinnerAdapter(List<SimpleSpinnerItem> items, int color, int dropBg) {
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(activity);
        adapter.setColor(color);
        adapter.setDropDownBackgroundColor(dropBg);
        adapter.setTextSize(8f);
        adapter.set(items);
        return adapter;
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
            btnFilterMarks.setText(DataManager.get().getStringManager().getSystemString(1374, "连接标记"));
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

    public void updateSearchResultCount() {
        if (tvSearchResult != null)
            tvSearchResult.setText(searchResultPrefix + " " + searchResults.size());
    }

    public void scrollToTop() {
        if (rvSearchResults != null) rvSearchResults.scrollToPosition(0);
    }

    public int getFilterMarks() {
        return filterMarks;
    }

    public long getFilterEffect() {
        return filterEffect;
    }

    public void dismissPopups() {
        if (linkMarkerPopup != null && linkMarkerPopup.isShowing()) linkMarkerPopup.dismiss();
        if (effectCategoryPopup != null && effectCategoryPopup.isShowing())
            effectCategoryPopup.dismiss();
    }
}
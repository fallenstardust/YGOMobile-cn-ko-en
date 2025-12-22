package cn.garymb.ygomobile.ui.cards;


import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView.OnEditorActionListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardSearchInfo;
import cn.garymb.ygomobile.loader.ICardSearcher;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.CardSet;
import ocgcore.data.LimitList;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardCategory;
import ocgcore.enums.CardOt;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class CardSearcher implements View.OnClickListener {
    private static final String TAG = "CardSearcher";
    final String[] BtnVals = new String[9];
    private final EditText keyWord;
    private final CheckBox chk_multi_keyword;
    private final Spinner otSpinner;
    private final Spinner limitSpinner;
    private final Spinner genesys_limitSpinner;
    private final Spinner limitListSpinner;
    private final Spinner typeSpinner;
    private final Spinner typeMonsterSpinner;
    private final Spinner typeMonsterSpinner2;
    private final Spinner typeSpellSpinner;
    private final Spinner typeTrapSpinner;
    private final Spinner setCodeSpinner;
    private final Spinner categorySpinner;
    private final Spinner raceSpinner;
    private final Spinner levelSpinner;
    private final Spinner attributeSpinner;
    private final EditText atkText;
    private final EditText defText;
    private final Spinner pScale;
    private final Button LinkMarkerButton;
    private final Button searchButton;
    private final Button resetButton;
    private final View view;
    private final View layout_monster;
    private final ICardSearcher mICardSearcher;// ICardSearcher 即为CardLoader的接口;
    private final Context mContext;
    private final Button myFavButton;
    protected StringManager mStringManager;
    protected LimitManager mLimitManager;
    protected AppsSettings mSettings;
    private int lineKey;
    private CallBack mCallBack;
    private boolean mShowFavorite;

    public CardSearcher(View view, ICardSearcher iCardSearcher) {
        this.view = view;
        this.mContext = view.getContext();
        this.mICardSearcher = iCardSearcher;
        this.mSettings = AppsSettings.get();
        mStringManager = DataManager.get().getStringManager();
        mLimitManager = DataManager.get().getLimitManager();
        keyWord = findViewById(R.id.edt_word1);
        chk_multi_keyword = findViewById(R.id.chk_multi_keyword);
        otSpinner = findViewById(R.id.sp_ot);
        limitSpinner = findViewById(R.id.sp_limit);
        genesys_limitSpinner = findViewById(R.id.sp_genesys_limit);//初始化genesys禁限选项布局
        limitListSpinner = findViewById(R.id.sp_limit_list);
        typeSpinner = findViewById(R.id.sp_type_card);
        typeMonsterSpinner = findViewById(R.id.sp_type_monster);
        typeMonsterSpinner2 = findViewById(R.id.sp_type_monster2);
        typeSpellSpinner = findViewById(R.id.sp_type_spell);
        typeTrapSpinner = findViewById(R.id.sp_type_trap);
        setCodeSpinner = findViewById(R.id.sp_setcode);
        categorySpinner = findViewById(R.id.sp_category);
        raceSpinner = findViewById(R.id.sp_race);
        levelSpinner = findViewById(R.id.sp_level);
        attributeSpinner = findViewById(R.id.sp_attribute);
        atkText = findViewById(R.id.edt_atk);
        defText = findViewById(R.id.edt_def);
        LinkMarkerButton = findViewById(R.id.btn_linkmarker);
        myFavButton = findViewById(R.id.btn_my_fav);
        searchButton = findViewById(R.id.btn_search);
        resetButton = findViewById(R.id.btn_reset);
        layout_monster = findViewById(R.id.layout_monster);
        pScale = findViewById(R.id.sp_scale);
        myFavButton.setOnClickListener(this);
        LinkMarkerButton.setOnClickListener(this);
        searchButton.setOnClickListener(this);
        resetButton.setOnClickListener(this);

        //输入即时搜索
        OnEditorActionListener searchListener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                search();
                return true;
            }
            return false;
        };

        keyWord.setOnEditorActionListener(searchListener);
        chk_multi_keyword.setChecked(mSettings.getKeyWordsSplit() != 0);
        chk_multi_keyword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSettings.setKeyWordsSplit(isChecked ? 1 : 0);
            }
        });

        myFavButton.setOnClickListener(v -> {
            if (isShowFavorite()) {
                hideFavorites(true);
            } else {
                showFavorites(true);
            }
        });

        LinkMarkerButton.setOnClickListener(v -> {
            Arrays.fill(BtnVals, "0");
            DialogPlus viewDialog = new DialogPlus(mContext);
            viewDialog.setContentView(R.layout.item_linkmarker);
            viewDialog.setTitle(R.string.ClickLinkArrows);
            viewDialog.show();
            int[] ids = new int[]{
                    R.id.button_1,
                    R.id.button_2,
                    R.id.button_3,
                    R.id.button_4,
                    R.id.button_5,
                    R.id.button_6,
                    R.id.button_7,
                    R.id.button_8,
                    R.id.button_9,
            };
            int[] enImgs = new int[]{
                    R.drawable.left_bottom_1,
                    R.drawable.bottom_1,
                    R.drawable.right_bottom_1,
                    R.drawable.left_1,
                    0,
                    R.drawable.right_1,
                    R.drawable.left_top_1,
                    R.drawable.top_1,
                    R.drawable.right_top_1,
            };
            int[] disImgs = new int[]{
                    R.drawable.left_bottom_0,
                    R.drawable.bottom_0,
                    R.drawable.right_bottom_0,
                    R.drawable.left_0,
                    0,
                    R.drawable.right_0,
                    R.drawable.left_top_0,
                    R.drawable.top_0,
                    R.drawable.right_top_0,
            };
            for (int i = 0; i < ids.length; i++) {
                final int index = i;
                viewDialog.findViewById(ids[index]).setOnClickListener((btn) -> {
                    if (index == 4) {
                        String mLinkStr = BtnVals[8] + BtnVals[7] + BtnVals[6] + BtnVals[5] + "0"
                                + BtnVals[3] + BtnVals[2] + BtnVals[1] + BtnVals[0];
                        lineKey = Integer.parseInt(mLinkStr, 2);
                        if (viewDialog.isShowing()) {
                            viewDialog.dismiss();
                        }
                    } else {
                        if ("0".equals(BtnVals[index])) {
                            btn.setBackgroundResource(enImgs[index]);
                            BtnVals[index] = "1";
                        } else {
                            btn.setBackgroundResource(disImgs[index]);
                            BtnVals[index] = "0";
                        }
                    }
                });
            }
        });


        limitListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long value = getSelect(limitListSpinner);
                if (value <= 0) {
                    reset(limitSpinner);
                    reset(genesys_limitSpinner);
                }
                LimitList limit = mLimitManager.getLimit(getSelectText(limitListSpinner));
                if (limit.getName().toLowerCase().contains("genesys")) {
                    genesys_limitSpinner.setVisibility(View.VISIBLE);
                    limitSpinner.setVisibility(View.GONE);
                } else {
                    genesys_limitSpinner.setVisibility(View.GONE);
                    limitSpinner.setVisibility(View.VISIBLE);
                }
                mICardSearcher.setLimitList(limit);
                //同时通知整个界面都显示该禁卡表的禁限情况
                mCallBack.setLimit(limit);

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        limitListSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                refreshLimitListSpinnerItems(limitListSpinner);
            }
            return false; // 返回false以允许正常的spinner行为继续
        });
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long value = getSelect(typeSpinner);
                if (value == 0) {
                    layout_monster.setVisibility(View.INVISIBLE);
                    raceSpinner.setVisibility(View.GONE);
                    typeSpellSpinner.setVisibility(View.GONE);
                    typeTrapSpinner.setVisibility(View.GONE);
                    pScale.setVisibility(View.INVISIBLE);
                    LinkMarkerButton.setVisibility(View.INVISIBLE);
                    resetMonster();
                } else if (value == CardType.Spell.getId()) {
                    layout_monster.setVisibility(View.INVISIBLE);
                    raceSpinner.setVisibility(View.GONE);
                    typeSpellSpinner.setVisibility(View.VISIBLE);
                    typeTrapSpinner.setVisibility(View.GONE);
                    pScale.setVisibility(View.INVISIBLE);
                    LinkMarkerButton.setVisibility(View.INVISIBLE);
                    resetMonster();
                } else if (value == CardType.Trap.getId()) {
                    layout_monster.setVisibility(View.INVISIBLE);
                    raceSpinner.setVisibility(View.GONE);
                    typeSpellSpinner.setVisibility(View.GONE);
                    typeTrapSpinner.setVisibility(View.VISIBLE);
                    pScale.setVisibility(View.INVISIBLE);
                    LinkMarkerButton.setVisibility(View.INVISIBLE);
                    resetMonster();
                } else {
                    layout_monster.setVisibility(View.VISIBLE);
                    raceSpinner.setVisibility(View.VISIBLE);
                    typeSpellSpinner.setVisibility(View.GONE);
                    typeTrapSpinner.setVisibility(View.GONE);
                    pScale.setVisibility(View.VISIBLE);
                    LinkMarkerButton.setVisibility(View.VISIBLE);
                }

                reset(pScale);
                reset(raceSpinner);
                reset(typeSpellSpinner);
                reset(typeTrapSpinner);
                reset(typeMonsterSpinner);
                reset(typeMonsterSpinner2);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public void setCallBack(CallBack callBack) {
        mCallBack = callBack;
    }

    public void showFavorites(boolean showList) {
        mShowFavorite = true;
        myFavButton.setSelected(true);
        if (mCallBack != null) {
            mCallBack.onSearchStart();
        }
        if (mCallBack != null) {
            VUiKit.post(() -> {
                mCallBack.onSearchResult(CardFavorites.get().getCards(mICardSearcher), !showList);
            });
        }
    }

    public void hideFavorites(boolean reload) {
        mShowFavorite = false;
        myFavButton.setSelected(false);
        if (mCallBack != null) {
            mCallBack.onSearchStart();
        }
        if (reload) {
            VUiKit.post(this::search);
        } else {
            if (mCallBack != null) {
                VUiKit.post(() -> {
                    mCallBack.onSearchResult(Collections.emptyList(), true);
                });
            }
        }
    }

    public void initItems() {
        initOtSpinners(otSpinner);
        initLimitSpinners(limitSpinner);//初始化常规禁限选项：禁止、限制、准限制
        initLimitGenesysSpinners(genesys_limitSpinner);//初始化Genesys禁限选项：Genesys、禁止
        initLimitListSpinners(limitListSpinner);
        initTypeSpinners(typeSpinner, new CardType[]{CardType.None, CardType.Monster, CardType.Spell, CardType.Trap});
        initTypeSpinners(typeMonsterSpinner, new CardType[]{CardType.None, CardType.Normal, CardType.Effect, CardType.Fusion, CardType.Ritual,
                CardType.Synchro, CardType.Pendulum, CardType.Xyz, CardType.Link, CardType.Spirit, CardType.Union,
                CardType.Dual, CardType.Tuner, CardType.Flip, CardType.Toon, CardType.Sp_Summon, CardType.Token
        });
        initTypeSpinners(typeMonsterSpinner2, new CardType[]{CardType.None, CardType.Pendulum, CardType.Tuner, CardType.Non_Effect
        });
        initTypeSpinners(typeSpellSpinner, new CardType[]{CardType.None, CardType.Normal, CardType.QuickPlay, CardType.Ritual,
                CardType.Continuous, CardType.Equip, CardType.Field
        });
        initTypeSpinners(typeTrapSpinner, new CardType[]{CardType.None, CardType.Normal, CardType.Continuous, CardType.Counter
        });
        initLevelSpinners(levelSpinner);
        initPscaleSpinners(pScale);
        initAttributes(attributeSpinner);
        initRaceSpinners(raceSpinner);
        initSetNameSpinners(setCodeSpinner);
        initCategorySpinners(categorySpinner);
    }

    protected <T extends View> T findViewById(int id) {
        T v = view.findViewById(id);
        if (v instanceof Spinner) {
            ((Spinner) v).setPopupBackgroundResource(R.color.colorNavy);
        }
        return v;
    }

    private void initOtSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (CardOt item : CardOt.values()) {
            items.add(new SimpleSpinnerItem(item.getId(),
                    mStringManager.getOtString(item.getId(), false)));
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    public boolean isShowFavorite() {
        return mShowFavorite;
    }

    protected String getString(int id) {
        return mContext.getString(id);
    }

    private void initLimitSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();

        // 添加默认选项
        items.add(new SimpleSpinnerItem(LimitType.None.getId(), getString(R.string.label_limit)));
        items.add(new SimpleSpinnerItem(LimitType.All.getId(), getString(R.string.all)));

        // 常规禁卡表下添加Forbidden（禁止）、Limit（限制）和SemiLimit（准限制）选项
        items.add(new SimpleSpinnerItem(LimitType.Forbidden.getId(), mStringManager.getLimitString(LimitType.Forbidden.getId())));
        items.add(new SimpleSpinnerItem(LimitType.Limit.getId(), mStringManager.getLimitString(LimitType.Limit.getId())));
        items.add(new SimpleSpinnerItem(LimitType.SemiLimit.getId(), mStringManager.getLimitString(LimitType.SemiLimit.getId())));

        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initLimitGenesysSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();

        // 添加默认选项
        items.add(new SimpleSpinnerItem(LimitType.None.getId(), getString(R.string.label_limit)));
        items.add(new SimpleSpinnerItem(LimitType.All.getId(), getString(R.string.all)));

        // GeneSys模式下只添加GeneSys和Forbidden选项
        items.add(new SimpleSpinnerItem(LimitType.GeneSys.getId(), mStringManager.getLimitString(LimitType.GeneSys.getId())));
        items.add(new SimpleSpinnerItem(LimitType.Forbidden.getId(), mStringManager.getLimitString(LimitType.Forbidden.getId())));

        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initLimitListSpinners(Spinner spinner) {
        // 创建一个列表用于存储下拉选项
        List<SimpleSpinnerItem> items = new ArrayList<>();
        // 获取所有禁卡表名称列表
        List<String> limits = mLimitManager.getLimitNames();
        // 初始化选中项索引为-1（表示未选中）
        int index = -1;
        // 获取禁卡表总数
        int count = mLimitManager.getCount();
        // 当前选中的禁卡表，初始化为null
        LimitList cur = null;
        // 如果卡片搜索器不为null，则获取当前使用的禁卡表
        if (mICardSearcher != null) {
            cur = mICardSearcher.getLimitList();
        }
        // 添加默认选项"选择禁卡表"
        items.add(new SimpleSpinnerItem(0, getString(R.string.label_limitlist)));
        // 遍历所有禁卡表
        for (int i = 0; i < count; i++) {
            // 计算选项索引（从1开始）
            int j = i + 1;
            // 获取禁卡表名称
            String name = limits.get(i);
            // 创建并添加禁卡表选项到列表
            items.add(new SimpleSpinnerItem(j, name));
            // 如果当前禁卡表不为null且名称匹配，则记录选中索引
            if (cur != null && TextUtils.equals(cur.getName(), name)) {
                index = j;
            }
        }
        // 创建适配器用于绑定数据到Spinner
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        // 设置文字颜色为白色
        adapter.setColor(Color.WHITE);
        // 设置适配器的数据源
        adapter.set(items);
        // 将适配器设置给Spinner
        spinner.setAdapter(adapter);
        // 如果找到了匹配的禁卡表，则设置Spinner的选中项
        Log.w(TAG, "index:" + index);
        if (index >= 0) {
            spinner.setSelection(index);
        }
    }


    private void refreshLimitListSpinnerItems(Spinner spinner) {
        // 首先清除所有现有的item
        if (spinner.getAdapter() != null && spinner.getAdapter() instanceof SimpleSpinnerAdapter) {
            //清空选项
            ((SimpleSpinnerAdapter) spinner.getAdapter()).clear();
            //重新加载禁卡表，获取可能存在的变动后情况
            mLimitManager.load();
        }
        initLimitListSpinners(spinner);
    }


    private void initPscaleSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (int i = -1; i <= 13; i++) {
            if (i == -1) {
                items.add(new SimpleSpinnerItem(i, getString(R.string.label_pendulum)));
            } else {
                items.add(new SimpleSpinnerItem(i, "" + i));
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initLevelSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (int i = 0; i <= 13; i++) {
            if (i == 0) {
                items.add(new SimpleSpinnerItem(i, getString(R.string.label_level)));
            } else {
                items.add(new SimpleSpinnerItem(i, "" + i));
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initSetNameSpinners(Spinner spinner) {
        List<CardSet> setnames = mStringManager.getCardSets();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        items.add(new SimpleSpinnerItem(0, getString(R.string.label_set)));
        items.add(new SimpleSpinnerItem(-1, getString(R.string.label_set_No_Setcode)));
        for (CardSet set : setnames) {
            items.add(new SimpleSpinnerItem(set.getCode(), set.getName()));
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initTypeSpinners(Spinner spinner, CardType[] eitems) {
        if (eitems == null) {
            eitems = CardType.values();
        }
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (CardType item : eitems) {
            if (item == CardType.None) {
                items.add(new SimpleSpinnerItem(item.getId(), getString(R.string.label_type)));
            } else {
                items.add(new SimpleSpinnerItem(item.getId(), mStringManager.getTypeString(item.getId())));
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initAttributes(Spinner spinner) {
        CardAttribute[] attributes = CardAttribute.values();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (CardAttribute item : attributes) {
            if (item == CardAttribute.None) {
                items.add(new SimpleSpinnerItem(CardAttribute.None.getId(), getString(R.string.label_attr)));
            } else {
                items.add(new SimpleSpinnerItem(item.getId(), mStringManager.getAttributeString(item.getId())));
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initRaceSpinners(Spinner spinner) {
        CardRace[] attributes = CardRace.values();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (CardRace item : attributes) {
            long val = item.value();
            if (val == 0) {
                items.add(new SimpleSpinnerItem(val, mContext.getString(R.string.label_race)));
            } else {
                items.add(new SimpleSpinnerItem(val, mStringManager.getRaceString(val)));
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initCategorySpinners(Spinner spinner) {
        CardCategory[] attributes = CardCategory.values();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (CardCategory item : attributes) {
            long val = item.value();
            if (val == 0) {
                items.add(new SimpleSpinnerItem(val, mContext.getString(R.string.label_category)));
            } else {
                items.add(new SimpleSpinnerItem(val, mStringManager.getCategoryString(val)));
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void reset(Spinner spinner) {
        if (spinner.getCount() > 0) {
            spinner.setSelection(0);
        }
    }

    private int getIntSelect(Spinner spinner) {
        return (int) getSelect(spinner);
    }

    private long getSelect(Spinner spinner) {
        return SimpleSpinnerAdapter.getSelect(spinner);
    }

    private String getSelectText(Spinner spinner) {
        return SimpleSpinnerAdapter.getSelectText(spinner);
    }

    protected String text(EditText editText) {
        CharSequence charSequence = editText.getText();
        if (charSequence == null) {
            return null;
        }
        return charSequence.toString();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_search) {
            hideFavorites(true);
        } else if (v.getId() == R.id.btn_reset) {
            resetAll();
        }
    }

    public void search(String message) {
        if (TextUtils.isEmpty(message)) {
            message = "";
        }
        keyWord.setText(message);
        search();
    }

    private void search() {
        if (mICardSearcher != null) {
            CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                    .keyword(text(keyWord))
                    .attribute(getIntSelect(attributeSpinner))
                    .level(getIntSelect(levelSpinner))
                    .race(getSelect(raceSpinner))
                    .atk(text(atkText))
                    .def(text(defText))
                    .pscale(getIntSelect(pScale))
                    .limitType(getIntSelect(limitSpinner))
                    .limitType(getIntSelect(genesys_limitSpinner))
                    .limitName(getSelectText(limitListSpinner))
                    .setcode(getSelect(setCodeSpinner))
                    .category(getSelect(categorySpinner))
                    .ot(getIntSelect(otSpinner))
                    .types(new long[]{
                            getSelect(typeSpinner),
                            getSelect(typeMonsterSpinner),
                            getSelect(typeSpellSpinner),
                            getSelect(typeTrapSpinner),
                            getSelect(typeMonsterSpinner2)
                    })
                    .linkKey(lineKey)
                    .build();
            Log.i(TAG, searchInfo.toString());
            mICardSearcher.search(searchInfo);
        }
    }

    private void resetAll() {
        if (mICardSearcher != null) {
            mICardSearcher.onReset();
        }
        keyWord.setText(null);
        reset(otSpinner);
//        reset(limitListSpinner);
//        if (limitListSpinner.getAdapter().getCount() > 1) {//因为禁卡表选择记录已变为保存形式，所以这里不再重置为第一个禁卡表
//            limitListSpinner.setSelection(1);
//        }
        reset(limitSpinner);
        reset(typeSpinner);
        reset(typeSpellSpinner);
        reset(typeTrapSpinner);
        reset(setCodeSpinner);
        reset(categorySpinner);
        resetMonster();
    }

    private void resetMonster() {
        reset(pScale);
        reset(typeMonsterSpinner);
        reset(typeMonsterSpinner2);
        reset(raceSpinner);
        reset(levelSpinner);
        reset(attributeSpinner);
        atkText.setText(null);
        defText.setText(null);
        lineKey = 0;
    }

    public interface CallBack {
        void setLimit(LimitList limit);

        void onSearchStart();

        void onSearchResult(List<Card> Cards, boolean isHide);
    }
}

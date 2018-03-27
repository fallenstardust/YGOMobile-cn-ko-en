package cn.garymb.ygomobile.ui.cards;


import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.loader.ICardLoader;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import ocgcore.data.CardSet;
import ocgcore.data.LimitList;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardCategory;
import ocgcore.enums.CardOt;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class CardSearcher implements View.OnClickListener {

    private EditText prefixWord;
    private EditText suffixWord;
    private Spinner otSpinner;
    private Spinner limitSpinner;
    private Spinner limitListSpinner;
    private Spinner typeSpinner;
    private Spinner typeMonsterSpinner;
    private Spinner typeMonsterSpinner2;
    private Spinner typeSTSpinner;

    private Spinner setcodeSpinner;
    private Spinner categorySpinner;
    private Spinner raceSpinner;
    private Spinner levelSpinner;
    private Spinner attributeSpinner;
    private EditText atkText;
    private EditText defText;
    private Spinner pScale;
    private Button LinkMarkerButton;
    private Button searchButton;
    private Button resetButton;
    private View view;
    private View layout_monster;
    private ICardLoader dataLoader;
    private Context mContext;
    protected StringManager mStringManager;
    protected LimitManager mLimitManager;
    protected AppsSettings mSettings;

    private Button button_1;
    private Button button_2;
    private Button button_3;
    private Button button_4;
    private Button button_5;
    private Button button_6;
    private Button button_7;
    private Button button_8;
    private Button button_9;

    String Btn_1;
    String Btn_2;
    String Btn_3;
    String Btn_4;
    String Btn_5;
    String Btn_6;
    String Btn_7;
    String Btn_8;
    String Btn_9;
    int lineKey;

    public CardSearcher(View view, ICardLoader dataLoader) {
        this.view = view;
        this.mContext = view.getContext();
        this.dataLoader = dataLoader;
        this.mSettings = AppsSettings.get();
        mStringManager = StringManager.get();
        mLimitManager = LimitManager.get();
        prefixWord = findViewById(R.id.edt_word1);
        suffixWord = findViewById(R.id.edt_word2);
        otSpinner = findViewById(R.id.sp_ot);
        limitSpinner = findViewById(R.id.sp_limit);
        limitListSpinner = findViewById(R.id.sp_limit_list);
        typeSpinner = findViewById(R.id.sp_type_card);
        typeMonsterSpinner = findViewById(R.id.sp_type_monster);
        typeMonsterSpinner2 = findViewById(R.id.sp_type_monster2);
        typeSTSpinner = findViewById(R.id.sp_type_st);
        setcodeSpinner = findViewById(R.id.sp_setcode);
        categorySpinner = findViewById(R.id.sp_category);
        raceSpinner = findViewById(R.id.sp_race);
        levelSpinner = findViewById(R.id.sp_level);
        attributeSpinner = findViewById(R.id.sp_attribute);
        atkText = findViewById(R.id.edt_atk);
        defText = findViewById(R.id.edt_def);
        LinkMarkerButton = findViewById(R.id.btn_linkmarker);
        searchButton = findViewById(R.id.btn_search);
        resetButton = findViewById(R.id.btn_reset);
        layout_monster = findViewById(R.id.layout_monster);
        pScale = findViewById(R.id.sp_scale);
        LinkMarkerButton.setOnClickListener(this);
        searchButton.setOnClickListener(this);
        resetButton.setOnClickListener(this);

        OnEditorActionListener searchListener = new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    search();
                    return true;
                }
                return false;
            }
        };

        prefixWord.setOnEditorActionListener(searchListener);
        suffixWord.setOnEditorActionListener(searchListener);

        LinkMarkerButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Btn_1 = "0";
                Btn_2 = "0";
                Btn_3 = "0";
                Btn_4 = "0";
                Btn_5 = "0";
                Btn_6 = "0";
                Btn_7 = "0";
                Btn_8 = "0";
                Btn_9 = "0";
                Dialog builder = new Dialog(v.getContext());
                builder.show();
                LayoutInflater inflater = LayoutInflater.from(v.getContext());
                View viewDialog = inflater.inflate(R.layout.item_linkmarker, null);
                button_1 = (Button) viewDialog.findViewById(R.id.button_1);
                button_2 = (Button) viewDialog.findViewById(R.id.button_2);
                button_3 = (Button) viewDialog.findViewById(R.id.button_3);
                button_4 = (Button) viewDialog.findViewById(R.id.button_4);
                button_5 = (Button) viewDialog.findViewById(R.id.button_5);
                button_6 = (Button) viewDialog.findViewById(R.id.button_6);
                button_7 = (Button) viewDialog.findViewById(R.id.button_7);
                button_8 = (Button) viewDialog.findViewById(R.id.button_8);
                button_9 = (Button) viewDialog.findViewById(R.id.button_9);

                button_1.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_1.equals("0")) {
                            button_1.setBackgroundResource(R.drawable.left_bottom_1);
                            Btn_1 = "1";
                        } else {
                            button_1.setBackgroundResource(R.drawable.left_bottom_0);
                            Btn_1 = "0";
                        }
                    }
                });
                button_2.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_2.equals("0")) {
                            button_2.setBackgroundResource(R.drawable.bottom_1);
                            Btn_2 = "1";
                        } else {
                            button_2.setBackgroundResource(R.drawable.bottom_0);
                            Btn_2 = "0";
                        }
                    }
                });
                button_3.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_3.equals("0")) {
                            button_3.setBackgroundResource(R.drawable.right_bottom_1);
                            Btn_3 = "1";
                        } else {
                            button_3.setBackgroundResource(R.drawable.right_bottom_0);
                            Btn_3 = "0";
                        }
                    }
                });
                button_4.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_4.equals("0")) {
                            button_4.setBackgroundResource(R.drawable.left_1);
                            Btn_4 = "1";
                        } else {
                            button_4.setBackgroundResource(R.drawable.left_0);
                            Btn_4 = "0";
                        }
                    }
                });
                button_6.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_6.equals("0")) {
                            button_6.setBackgroundResource(R.drawable.right_1);
                            Btn_6 = "1";
                        } else {
                            button_6.setBackgroundResource(R.drawable.right_0);
                            Btn_6 = "0";
                        }
                    }
                });
                button_7.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_7.equals("0")) {
                            button_7.setBackgroundResource(R.drawable.left_top_1);
                            Btn_7 = "1";
                        } else {
                            button_7.setBackgroundResource(R.drawable.left_top_0);
                            Btn_7 = "0";
                        }
                    }
                });
                button_8.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_8.equals("0")) {
                            button_8.setBackgroundResource(R.drawable.top_1);
                            Btn_8 = "1";
                        } else {
                            button_8.setBackgroundResource(R.drawable.top_0);
                            Btn_8 = "0";
                        }
                    }
                });
                button_9.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_9.equals("0")) {
                            button_9.setBackgroundResource(R.drawable.right_top_1);
                            Btn_9 = "1";
                        } else {
                            button_9.setBackgroundResource(R.drawable.right_top_0);
                            Btn_9 = "0";
                        }
                    }
                });

                button_5.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Btn_5.equals("0")) {
                            String mLinkStr = Btn_9 + Btn_8 + Btn_7 + Btn_6 + "0" + Btn_4 + Btn_3 + Btn_2 + Btn_1;
                            lineKey = Integer.parseInt(mLinkStr, 2);
                            builder.dismiss();
                        }
                    }
                });
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(600, 600);
                builder.setContentView(viewDialog, layoutParams);
            }

        });


        limitListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long value = getSelect(limitListSpinner);
                if (value <= 0) {
                    reset(limitSpinner);
                    limitSpinner.setVisibility(View.INVISIBLE);
                } else {
                    limitSpinner.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long value = getSelect(typeSpinner);
                if (value == 0) {
                    layout_monster.setVisibility(View.INVISIBLE);
                    raceSpinner.setVisibility(View.GONE);
                    typeSTSpinner.setVisibility(View.INVISIBLE);
                    pScale.setVisibility(View.INVISIBLE);
                    LinkMarkerButton.setVisibility(View.INVISIBLE);
                    resetMonster();
                } else if (value == CardType.Spell.value() || value == CardType.Trap.value()) {
                    layout_monster.setVisibility(View.INVISIBLE);
                    raceSpinner.setVisibility(View.GONE);
                    typeSTSpinner.setVisibility(View.VISIBLE);
                    pScale.setVisibility(View.INVISIBLE);
                    LinkMarkerButton.setVisibility(View.INVISIBLE);
                    resetMonster();
                } else {
                    layout_monster.setVisibility(View.VISIBLE);
                    raceSpinner.setVisibility(View.VISIBLE);
                    typeSTSpinner.setVisibility(View.GONE);
                    pScale.setVisibility(View.VISIBLE);
                    LinkMarkerButton.setVisibility(View.VISIBLE);
                }

                reset(pScale);
                reset(raceSpinner);
                reset(typeSTSpinner);
                reset(typeMonsterSpinner);
                reset(typeMonsterSpinner2);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public void initItems() {
        initOtSpinners(otSpinner);
        initLimitSpinners(limitSpinner);
        initLimitListSpinners(limitListSpinner);
        initTypeSpinners(typeSpinner, new CardType[]{CardType.None, CardType.Monster, CardType.Spell, CardType.Trap});
        initTypeSpinners(typeMonsterSpinner, new CardType[]{CardType.None, CardType.Normal, CardType.Effect, CardType.Fusion, CardType.Ritual,
                CardType.Synchro, CardType.Pendulum, CardType.Xyz, CardType.Link, CardType.Spirit, CardType.Union,
                CardType.Dual, CardType.Tuner, CardType.Flip, CardType.Toon, CardType.Token
        });
        initTypeSpinners(typeMonsterSpinner2, new CardType[]{CardType.None, CardType.Pendulum, CardType.Tuner, CardType.Effect, CardType.Normal
        });
        initTypeSpinners(typeSTSpinner, new CardType[]{CardType.None, CardType.Normal, CardType.QuickPlay, CardType.Ritual,
                CardType.Continuous, CardType.Equip, CardType.Field, CardType.Counter
        });
        initLevelSpinners(levelSpinner);
        initPscaleSpinners(pScale);
        initAttributes(attributeSpinner);
        initRaceSpinners(raceSpinner);
        initSetNameSpinners(setcodeSpinner);
        initCategorySpinners(categorySpinner);
    }

    protected <T extends View> T findViewById(int id) {
        return (T) view.findViewById(id);
    }

    /*public void showDeckList() {
        findViewById(R.id.layout_deck_list).setVisibility(View.VISIBLE);
    }*/

    private void initOtSpinners(Spinner spinner) {
        CardOt[] ots = CardOt.values();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        items.add(new SimpleSpinnerItem(0, getString(R.string.label_ot)));
        for (CardOt item : ots) {
            if (item.ordinal() != 0) {
                items.add(new SimpleSpinnerItem(item.ordinal(),
                        mStringManager.getOtString(item.ordinal(), item.toString()))
                );
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    protected String getString(int id) {
        return mContext.getString(id);
    }

    private void initLimitSpinners(Spinner spinner) {
        LimitType[] eitems = LimitType.values();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (LimitType item : eitems) {
            long val = item.value();
            if (val == 0) {
                items.add(new SimpleSpinnerItem(val, getString(R.string.label_limit)));
            } else if (val == LimitType.All.value()) {
                items.add(new SimpleSpinnerItem(val, getString(R.string.all)));
            } else {
                items.add(new SimpleSpinnerItem(val, mStringManager.getLimitString(val)));
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    private void initLimitListSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        List<LimitList> limitLists = mLimitManager.getLimitLists();
        int index = -1;
        int count = mLimitManager.getCount();
        LimitList cur = null;
        if (dataLoader != null) {
            cur = dataLoader.getLimitList();
        }
        for (int i = 0; i < count; i++) {
            LimitList list = limitLists.get(i);
            if (i == 0) {
                items.add(new SimpleSpinnerItem(i, getString(R.string.label_limitlist)));
            } else {
                items.add(new SimpleSpinnerItem(i, list.getName()));
            }
            if (cur != null) {
                if (TextUtils.equals(cur.getName(), list.getName())) {
                    index = i;
                }
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
        if (index >= 0) {
            spinner.setSelection(index);
        }
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
            long val = item.value();
            if (val == 0) {
                items.add(new SimpleSpinnerItem(val, getString(R.string.label_type)));
            } else {
                items.add(new SimpleSpinnerItem(val, mStringManager.getTypeString(val)));
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
            long val = item.value();
            if (val == 0) {
                items.add(new SimpleSpinnerItem(val, getString(R.string.label_attr)));
            } else {
                items.add(new SimpleSpinnerItem(val, mStringManager.getAttributeString(val)));
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

    private long getSelect(Spinner spinner) {
        return SimpleSpinnerAdapter.getSelect(spinner);
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
        if (v == searchButton) {
            search();
        } else if (v == resetButton) {
            resetAll();
        }
    }

    private void search() {
        if (dataLoader != null) {
            dataLoader.search(text(prefixWord), text(suffixWord), getSelect(attributeSpinner)
                    , getSelect(levelSpinner), getSelect(raceSpinner), getSelect(limitListSpinner), getSelect(limitSpinner),
                    text(atkText), text(defText),
                    getSelect(pScale),
                    getSelect(setcodeSpinner)
                    , getSelect(categorySpinner), getSelect(otSpinner), lineKey, getSelect(typeSpinner), getSelect(typeMonsterSpinner), getSelect(typeSTSpinner)
                    , getSelect(typeMonsterSpinner2));
            lineKey = 0;
        }
    }

    private void resetAll() {
        if (dataLoader != null) {
            dataLoader.onReset();
        }
        prefixWord.setText(null);
        suffixWord.setText(null);
        reset(otSpinner);
        reset(limitSpinner);
//        reset(limitListSpinner);
        if (limitListSpinner.getAdapter().getCount() > 1) {
            limitListSpinner.setSelection(1);
        }
        reset(limitSpinner);
        reset(typeSpinner);
        reset(typeSTSpinner);
        reset(setcodeSpinner);
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
    }
}

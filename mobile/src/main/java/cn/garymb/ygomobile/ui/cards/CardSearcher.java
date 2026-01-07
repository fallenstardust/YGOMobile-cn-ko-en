package cn.garymb.ygomobile.ui.cards;


import static cn.garymb.ygomobile.Constants.ASSET_ATTR_RACE;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import android.widget.Switch;
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
import cn.garymb.ygomobile.utils.BitmapUtil;
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
    private final Switch genesys_Switch;
    private final Spinner limitSpinner;
    private final Spinner genesys_limitSpinner;
    private final Spinner limitListSpinner;
    private final Spinner genesys_limitListSpinner;
    private final Spinner typeSpinner;
    // 属性筛选按钮
    private Button[] attributeButtons;
    private List<Integer> attributeList;
    // 种族筛选按钮
    private Button[] raceButtons;
    private List<Long> raceList;
    private Button[] iconButtons;
    private List<Long> iconList;
    // 怪兽类型按钮
    private Button[] typeButtons;
    private List<Long> typeList;
    private Button[] exclude_typeButtons;
    private List<Long> excludTypeList;
    // 等级\阶级\连接数
    private Button[] levelButtons;
    private List<Integer> levelList;
    // 灵摆刻度数
    private Button[] pendulumScaleButtons;
    private List<Integer> pendulumScaleList;
    private final Spinner typeMonsterSpinner;
    private final Spinner typeMonsterSpinner2;
    private final Spinner typeSpellSpinner;
    private final Spinner typeTrapSpinner;
    private final Spinner setCodeSpinner;
    List<Long> setCodeList;
    private final Spinner categorySpinner;
    List<Long> categoryList;
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
        genesys_Switch = findViewById(R.id.sw_genesys_mode);//genesys模式开关
        limitSpinner = findViewById(R.id.sp_limit);
        genesys_limitSpinner = findViewById(R.id.sp_genesys_limit);//初始化genesys禁限选项布局
        limitListSpinner = findViewById(R.id.sp_limit_list);
        genesys_limitListSpinner = findViewById(R.id.sp_genesys_limit_list);//初始化genesys禁卡表布局
        typeSpinner = findViewById(R.id.sp_type_card);

        // 初始化种族按钮数组
        raceButtons = new Button[]{
                view.findViewById(R.id.btn_race_warrior),// 战士
                view.findViewById(R.id.btn_race_spellcaster),// 魔法师
                view.findViewById(R.id.btn_race_fairy),// 天使
                view.findViewById(R.id.btn_race_fiend),// 恶魔
                view.findViewById(R.id.btn_race_zombie),// 不死
                view.findViewById(R.id.btn_race_machine),// 机械
                view.findViewById(R.id.btn_race_aqua),// 水
                view.findViewById(R.id.btn_race_pyro),// 炎
                view.findViewById(R.id.btn_race_rock),// 岩石
                view.findViewById(R.id.btn_race_wingedBeast),// 鸟兽
                view.findViewById(R.id.btn_race_plant),// 植物
                view.findViewById(R.id.btn_race_insect),// 昆虫
                view.findViewById(R.id.btn_race_thunder),// 雷
                view.findViewById(R.id.btn_race_dragon),// 龙
                view.findViewById(R.id.btn_race_beast),// 兽
                view.findViewById(R.id.btn_race_beastWarrior),// 兽战士
                view.findViewById(R.id.btn_race_dinosaur),// 恐龙
                view.findViewById(R.id.btn_race_fish),// 鱼
                view.findViewById(R.id.btn_race_seaSerpent),// 海龙
                view.findViewById(R.id.btn_race_reptile),// 爬虫
                view.findViewById(R.id.btn_race_psychic),// 念动力
                view.findViewById(R.id.btn_race_divineBeast),// 幻神兽
                view.findViewById(R.id.btn_race_creatorGod),// 创造神
                view.findViewById(R.id.btn_race_wyrm),// 幻龙
                view.findViewById(R.id.btn_race_cyberse),// 电子界
                view.findViewById(R.id.btn_race_illusion)// 幻想魔
        };
        raceList = new ArrayList<>();
        // 初始化类型按钮
        typeButtons = new Button[]{
                view.findViewById(R.id.btn_type_normal),// 通常
                view.findViewById(R.id.btn_type_effect),// 效果
                view.findViewById(R.id.btn_type_fusion),// 融合
                view.findViewById(R.id.btn_type_ritual),// 仪式
                view.findViewById(R.id.btn_type_spirit),// 灵魂
                view.findViewById(R.id.btn_type_union),// 同盟
                view.findViewById(R.id.btn_type_tuner),// 调整
                view.findViewById(R.id.btn_type_synchro),// 同调
                view.findViewById(R.id.btn_type_flip),// 反转
                view.findViewById(R.id.btn_type_toon),// 卡通
                view.findViewById(R.id.btn_type_xyz),// 超量
                view.findViewById(R.id.btn_type_pendulum),// 灵摆
                view.findViewById(R.id.btn_type_specialSummon),// 特殊召唤
                view.findViewById(R.id.btn_type_link),// 连接
                view.findViewById(R.id.btn_type_token)
        };
        typeList = new ArrayList<>();
        exclude_typeButtons = new Button[]{
                view.findViewById(R.id.btn_exclude_type_normal),
                view.findViewById(R.id.btn_exclude_type_effect),
                view.findViewById(R.id.btn_exclude_type_fusion),
                view.findViewById(R.id.btn_exclude_type_ritual),
                view.findViewById(R.id.btn_exclude_type_spirit),
                view.findViewById(R.id.btn_exclude_type_union),
                view.findViewById(R.id.btn_exclude_type_tuner),
                view.findViewById(R.id.btn_exclude_type_synchro),
                view.findViewById(R.id.btn_exclude_type_flip),
                view.findViewById(R.id.btn_exclude_type_toon),
                view.findViewById(R.id.btn_exclude_type_xyz),
                view.findViewById(R.id.btn_exclude_type_pendulum),
                view.findViewById(R.id.btn_exclude_type_specialSummon),
                view.findViewById(R.id.btn_exclude_type_link),
                view.findViewById(R.id.btn_exclude_type_token)
        };
        excludTypeList = new ArrayList<>();
        iconButtons = new Button[]{
                view.findViewById(R.id.btn_icon_quickPlay),// 速攻
                view.findViewById(R.id.btn_icon_continuous),// 永续
                view.findViewById(R.id.btn_icon_equip),// 装备
                view.findViewById(R.id.btn_icon_field),// 场地
                view.findViewById(R.id.btn_icon_counter),// 反击
                view.findViewById(R.id.btn_icon_ritual),// 仪式
        };
        iconList = new ArrayList<>();
        levelButtons = new Button[]{
                view.findViewById(R.id.btn_LRA_1),
                view.findViewById(R.id.btn_LRA_2),
                view.findViewById(R.id.btn_LRA_3),
                view.findViewById(R.id.btn_LRA_4),
                view.findViewById(R.id.btn_LRA_5),
                view.findViewById(R.id.btn_LRA_6),
                view.findViewById(R.id.btn_LRA_7),
                view.findViewById(R.id.btn_LRA_8),
                view.findViewById(R.id.btn_LRA_9),
                view.findViewById(R.id.btn_LRA_10),
                view.findViewById(R.id.btn_LRA_11),
                view.findViewById(R.id.btn_LRA_12),
        };
        levelList = new ArrayList<>();
        pendulumScaleButtons = new Button[]{
                view.findViewById(R.id.btn_Pscale_0),
                view.findViewById(R.id.btn_Pscale_1),
                view.findViewById(R.id.btn_Pscale_2),
                view.findViewById(R.id.btn_Pscale_3),
                view.findViewById(R.id.btn_Pscale_4),
                view.findViewById(R.id.btn_Pscale_5),
                view.findViewById(R.id.btn_Pscale_6),
                view.findViewById(R.id.btn_Pscale_7),
                view.findViewById(R.id.btn_Pscale_8),
                view.findViewById(R.id.btn_Pscale_9),
                view.findViewById(R.id.btn_Pscale_10),
                view.findViewById(R.id.btn_Pscale_11),
                view.findViewById(R.id.btn_Pscale_12),
                view.findViewById(R.id.btn_Pscale_13),
        };
        pendulumScaleList = new ArrayList<>();
        //TODO这些组件需要替换成多选界面
        typeMonsterSpinner = findViewById(R.id.sp_type_monster);
        typeMonsterSpinner2 = findViewById(R.id.sp_type_monster2);
        typeSpellSpinner = findViewById(R.id.sp_type_spell);
        typeTrapSpinner = findViewById(R.id.sp_type_trap);
        setCodeSpinner = findViewById(R.id.sp_setcode);
        categorySpinner = findViewById(R.id.sp_category);
        raceSpinner = findViewById(R.id.sp_race);
        levelSpinner = findViewById(R.id.sp_level);
        attributeSpinner = findViewById(R.id.sp_attribute);
        layout_monster = findViewById(R.id.layout_monster);
        //
        atkText = findViewById(R.id.edt_atk);
        defText = findViewById(R.id.edt_def);
        LinkMarkerButton = findViewById(R.id.btn_linkmarker);
        myFavButton = findViewById(R.id.btn_my_fav);
        searchButton = findViewById(R.id.btn_search);
        resetButton = findViewById(R.id.btn_reset);
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
        genesys_Switch.setChecked(mSettings.getGenesysMode() != 0);
        genesys_Switch.setText(mSettings.getGenesysMode() != 0 ? R.string.switch_genesys_mode : R.string.switch_banlist_mode);
        genesys_Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            //同时通知整个界面都显示该禁卡表的禁限情况
            LimitList limit = isChecked ? mLimitManager.getGenesysLimit(getSelectText(genesys_limitListSpinner)) : mLimitManager.getLimit(getSelectText(limitListSpinner));
            if (limit != null) {
                //同时通知整个界面都显示该禁卡表的禁限情况
                mCallBack.setLimit(limit, "genesy切换开关");
                mICardSearcher.setLimitList(limit);
            } else {
                mCallBack.setLimit(new LimitList(), "genesy切换开关 - null禁卡表");
                mICardSearcher.setLimitList(new LimitList());
            }

            // 重置禁限筛选条件，以免切换时出现不合预期的结果
            reset(isChecked ? genesys_limitSpinner : limitSpinner);
            genesys_Switch.setText(isChecked ? R.string.switch_genesys_mode : R.string.switch_banlist_mode);

            //根据开关切换两种模式禁卡表的显示和隐藏
            genesys_limitListSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            genesys_limitSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            limitListSpinner.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            limitSpinner.setVisibility(isChecked ? View.GONE : View.VISIBLE);

        });
        limitListSpinner.setVisibility(genesys_Switch.isChecked() ? View.GONE : View.VISIBLE);
        limitListSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                refreshLimitListSpinnerItems(limitListSpinner);
            }
            return false; // 返回false以允许正常的spinner行为继续
        });
        limitSpinner.setVisibility(genesys_Switch.isChecked() ? View.GONE : View.VISIBLE);
        genesys_limitSpinner.setVisibility(genesys_Switch.isChecked() ? View.VISIBLE : View.GONE);
        genesys_limitListSpinner.setVisibility(genesys_Switch.isChecked() ? View.VISIBLE : View.GONE);
        genesys_limitListSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                refreshGenesysLimitListSpinnerItems(genesys_limitListSpinner);
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
        initGenesysLimitListSpinners(genesys_limitListSpinner);
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
        initAttributeButtons();
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
        spinner.setOnItemSelectedListener(null);
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
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long value = getSelect(spinner);
                if (value <= 0) {
                    reset(spinner);
                }
                LimitList limit = mLimitManager.getLimit(getSelectText(spinner));
                // 添加空值检查
                if (limit != null) {
                    //同时通知整个界面都显示该禁卡表的禁限情况
                    mCallBack.setLimit(limit, "初始化 常规 禁卡表");
                    mICardSearcher.setLimitList(limit);
                } else {
                    // 可以选择设置一个默认的LimitList或空的LimitList
                    mCallBack.setLimit(new LimitList(), "初始化 常规 禁卡表 - 标题");
                    mICardSearcher.setLimitList(new LimitList());
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initGenesysLimitListSpinners(Spinner spinner) {
        spinner.setOnItemSelectedListener(null);
        // 创建一个列表用于存储下拉选项
        List<SimpleSpinnerItem> items = new ArrayList<>();
        // 获取所有禁卡表名称列表
        List<String> genesys_limit_names = mLimitManager.getGenesysLimitNames();
        // 初始化选中项索引为-1（表示未选中）
        int index = -1;
        // 获取禁卡表总数
        int genesys_count = mLimitManager.getGenesysCount();
        // 当前选中的禁卡表，初始化为null
        LimitList cur = null;
        // 如果卡片搜索器不为null，则获取当前使用的禁卡表
        if (mICardSearcher != null) {
            cur = mICardSearcher.getGenesysLimitList();
        }
        // 添加默认选项"禁卡表"
        items.add(new SimpleSpinnerItem(0, getString(R.string.label_limitlist)));
        // 遍历所有禁卡表
        for (int i = 0; i < genesys_count; i++) {
            // 计算选项索引（从1开始）
            int j = i + 1;
            // 获取禁卡表名称
            String name = genesys_limit_names.get(i);
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
        Log.w(TAG, " genesys index:" + index);
        if (index >= 0) {
            spinner.setSelection(index);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long value = getSelect(spinner);
                if (value <= 0) {
                    reset(spinner);
                }

                LimitList genesyslimit = mLimitManager.getGenesysLimit(getSelectText(spinner));
                // 添加空值检查
                if (genesyslimit != null) {
                    //同时通知整个界面都显示该禁卡表的禁限情况
                    mCallBack.setLimit(genesyslimit, "初始化 genesys 禁卡表");
                    mICardSearcher.setLimitList(genesyslimit);
                } else {
                    // 可以选择设置一个默认的LimitList或空的LimitList
                    mCallBack.setLimit(new LimitList(), "初始化 常规 禁卡表 - 标题");
                    mICardSearcher.setLimitList(new LimitList());
                }


            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
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

    private void refreshGenesysLimitListSpinnerItems(Spinner spinner) {
        // 首先清除所有现有的item
        if (spinner.getAdapter() != null && spinner.getAdapter() instanceof SimpleSpinnerAdapter) {
            //清空选项
            ((SimpleSpinnerAdapter) spinner.getAdapter()).clear();
            //重新加载禁卡表，获取可能存在的变动后情况
            mLimitManager.load();
        }
        initGenesysLimitListSpinners(spinner);
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

    // 辅助方法：更新按钮背景
    private void updateButtonBackground(Button button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundColor(Color.parseColor("#40808080")); // 选中时的背景色
            button.setAlpha(0.7f); // 选中时稍微透明
        } else {
            button.setBackgroundColor(Color.TRANSPARENT); // 默认透明背景
            button.setAlpha(1.0f); // 正常透明度
        }
    }

    private void initAttributeButtons() {
        // 定义图标资源ID数组
        final Drawable[] attributeIcons = {
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "attribute_icon_dark.png", 0, 0)),// 暗属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "attribute_icon_light.png", 0, 0)),// 光属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "attribute_icon_earth.png", 0, 0)),// 地属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "attribute_icon_water.png", 0, 0)),// 水属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "attribute_icon_fire.png", 0, 0)),// 火属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "attribute_icon_wind.png", 0, 0)),// 风属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "attribute_icon_divine.png", 0, 0)),// 神属性图标
        };

        // 初始化属性按钮
        attributeButtons = new Button[]{
                view.findViewById(R.id.btn_attr_dark),// 暗
                view.findViewById(R.id.btn_attr_light),// 光
                view.findViewById(R.id.btn_attr_earth),// 地
                view.findViewById(R.id.btn_attr_water),// 水
                view.findViewById(R.id.btn_attr_fire),// 火
                view.findViewById(R.id.btn_attr_wind),// 风
                view.findViewById(R.id.btn_attr_divine)// 神
        };

        // 定义属性对应的ID值，使用long类型
        final Integer[] attributeIds = {
                1,    // 暗
                2,   // 光
                3,   // 地
                4,   // 水
                5,    // 火
                6,    // 风
                7   // 神
        };
        // 定义说明文字
        final String[] attributeTexts = {
                mStringManager.getAttributeString(CardAttribute.Dark.getId()),
                mStringManager.getAttributeString(CardAttribute.Light.getId()),
                mStringManager.getAttributeString(CardAttribute.Earth.getId()),
                mStringManager.getAttributeString(CardAttribute.Water.getId()),
                mStringManager.getAttributeString(CardAttribute.Fire.getId()),
                mStringManager.getAttributeString(CardAttribute.Wind.getId()),
                mStringManager.getAttributeString(CardAttribute.Divine.getId())
        };
        for (int i = 0; i < attributeButtons.length; i++) {
            final int index = i;
            final Integer attributeId = attributeIds[i];
            Button button = attributeButtons[index];
            button.setCompoundDrawablePadding(4); // 图标和文字间距
            // 设置图标和文字
            button.setCompoundDrawablesWithIntrinsicBounds(null, attributeIcons[index], null, null);
            button.setText(attributeTexts[index]);

            attributeButtons[i].setOnClickListener(v -> {
                if (attributeList == null) {
                    attributeList = new ArrayList<>();
                }

                if (button.isSelected()) {
                    button.setSelected(false);
                    attributeList.remove(attributeId);
                } else {
                    button.setSelected(true);
                    attributeList.add(attributeId);
                }
            });
        }
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
            /*
            CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                    .keyword(text(keyWord))
                    .attribute(getIntSelect(attributeSpinner))
                    .level(getIntSelect(levelSpinner))
                    .race(getSelect(raceSpinner))
                    .atk(text(atkText))
                    .def(text(defText))
                    .pscale(getIntSelect(pScale))
                    .limitType(genesys_Switch.isChecked() ? getIntSelect(genesys_limitSpinner) : getIntSelect(limitSpinner))
                    .limitName(genesys_Switch.isChecked() ? getSelectText(genesys_limitListSpinner) : getSelectText(limitListSpinner))
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
                    .build();*/

            //这是一个调用示例
            CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                    .keyword(text(keyWord))
                    .attribute(attributeList)
                    .level(levelList)
                    .race(raceList)
                    .atk(text(atkText))
                    .def(text(defText))
                    .pscale(pendulumScaleList)
                    .limitType(genesys_Switch.isChecked() ? getIntSelect(genesys_limitSpinner) : getIntSelect(limitSpinner))
                    .limitName(genesys_Switch.isChecked() ? getSelectText(genesys_limitListSpinner) : getSelectText(limitListSpinner))
                    .setcode(setCodeList)
                    .category(categoryList)
                    .ot(getIntSelect(otSpinner))
                    .types(typeList)
                    .except_types(excludTypeList)
                    .linkKey(lineKey)
                    .type_logic(false)//or逻辑
                    .setcode_logic(false)
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
        reset(limitSpinner.getVisibility() == View.VISIBLE ? limitSpinner : genesys_limitSpinner);
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
        void setLimit(LimitList limit, String caller);

        void onSearchStart();

        void onSearchResult(List<Card> Cards, boolean isHide);
    }
}

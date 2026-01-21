package cn.garymb.ygomobile.ui.cards;

import static cn.garymb.ygomobile.Constants.ASSETS_PATH;
import static cn.garymb.ygomobile.Constants.ASSET_ATTR_RACE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.android.flexbox.FlexboxLayout;

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
import cn.garymb.ygomobile.ui.widget.SearchableListDialog;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
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
    // 效果类型按钮
    private GridLayout gl_category;
    private ImageView iv_hide_category;
    private Button[] categoryButtons;
    private CardCategory[] categories;
    List<Long> categoryList;
    // 卡片类型按钮
    private GridLayout gl_cardType;
    private ImageView iv_hide_cardType;
    private Button[] cardTypeButtons;
    private long[] typeIds;
    // 魔陷图标
    private LinearLayout ll_icon;
    private GridLayout gl_icon;
    private ImageView iv_hide_spelltrap;
    private Button[] iconButtons;
    private Button[] spellButtons;
    private Button[] trapButtons;
    private List<Long> spellTrapTypeList;
    private long[] iconIds;
    private long[] spellIds;
    private long[] trapIds;
    // 属性筛选按钮
    private Button[] attributeButtons;
    private GridLayout gl_attr;
    private ImageView iv_hide_attr;
    private CardAttribute[] attributeIds;
    private List<Long> attributeList;
    // 种族筛选按钮
    private Button[] raceButtons;
    private GridLayout gl_race;
    private ImageView iv_hide_race;
    private CardRace[] raceIds;
    private List<Long> raceList;
    //怪兽类型按钮
    private GridLayout gl_monsterType;
    private ImageView iv_hide_monsterType;
    private Button[] monsterTypeButtons;
    private long[] monsterTypeIds;
    private Drawable[] TypeIcon;
    private List<Long> typeList;
    private boolean isAnd;
    // 排除怪兽类型按钮
    private final GridLayout gl_exclude_type;
    private ImageView iv_hide_exclude_type;
    private Button[] exclude_typeButtons;
    private List<Long> excludeTypeList;
    // 等级\阶级\连接数
    private GridLayout gl_level_rank_link;
    private ImageView iv_hide_level_rank_link;
    private ImageButton[] levelButtons;
    private List<Integer> levelList;
    // 灵摆刻度数
    private GridLayout gl_pendulum_scale;
    private ImageView iv_hide_pendulum_scale;
    private ImageButton[] pendulumScaleButtons;
    private List<Integer> pendulumScaleList;
    // 字段
    private final FlexboxLayout tag_setcode;
    private ImageButton btn_clear_setcode;
    List<Long> setCodeList;
    boolean setcode_isAnd;

    private final EditText atkText;
    private final EditText defText;
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

        // 效果类型宫格布局
        gl_category = findViewById(R.id.gl_category);
        iv_hide_category = findViewById(R.id.iv_hide_category);
        categories = CardCategory.values();
        categoryList = new ArrayList<>();
        // 卡片类型宫格布局
        gl_cardType = findViewById(R.id.gl_cardType);
        iv_hide_cardType = findViewById(R.id.iv_hide_cardType);

        // 魔陷类型宫格布局
        ll_icon = findViewById(R.id.ll_icon);//需要隐藏时控制整个布局
        gl_icon = findViewById(R.id.gl_icon);
        iv_hide_spelltrap = findViewById(R.id.iv_hide_spelltrap);
        spellTrapTypeList = new ArrayList<>();

        // 怪兽类型总布局-----------------------
        layout_monster = findViewById(R.id.layout_monster);
        // 属性宫格布局
        gl_attr = findViewById(R.id.gl_attr);
        iv_hide_attr = findViewById(R.id.iv_hide_attr);
        attributeIds = CardAttribute.values();
        attributeList = new ArrayList<>();
        // 种族宫格布局
        gl_race = findViewById(R.id.gl_race);
        iv_hide_race = findViewById(R.id.iv_hide_race);
        raceIds = CardRace.values();
        raceList = new ArrayList<>();
        // 怪兽种类宫格布局
        gl_monsterType = findViewById(R.id.gl_monsterType);
        iv_hide_monsterType = findViewById(R.id.iv_hide_monsterType);
        typeList = new ArrayList<>();
        isAnd = false;
        // 排除种类宫格布局
        gl_exclude_type = findViewById(R.id.gl_excludeType);
        iv_hide_exclude_type = findViewById(R.id.iv_hide_excludeType);
        excludeTypeList = new ArrayList<>();
        // 等级、阶级、连接数宫格布局
        gl_level_rank_link = findViewById(R.id.gl_level);
        iv_hide_level_rank_link = findViewById(R.id.iv_hide_level);
        levelList = new ArrayList<>();
        // 等级、阶级、连接数宫格布局
        gl_pendulum_scale = findViewById(R.id.gl_pScale);
        iv_hide_pendulum_scale = findViewById(R.id.iv_hide_pScale);
        pendulumScaleList = new ArrayList<>();

        //TODO这些组件需要替换成多选界面
        // 字段
        btn_clear_setcode = findViewById(R.id.btn_clear_setcode);
        tag_setcode = findViewById(R.id.tag_setcode);
        setcode_isAnd = false;
        setCodeList = new ArrayList<>();
        //
        atkText = findViewById(R.id.edt_atk);
        defText = findViewById(R.id.edt_def);
        LinkMarkerButton = findViewById(R.id.btn_linkmarker);
        myFavButton = findViewById(R.id.btn_my_fav);
        searchButton = findViewById(R.id.btn_search);
        resetButton = findViewById(R.id.btn_reset);
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
        initCategoryButtons();
        initTypeButtons();
        initAttributeButtons();
        initRaceButtons();
        initIconButtons();
        initMonsterTypeButton();
        initExcludeTypeButton();
        initLevelButtons();
        initPendulumScaleButtons();
        initOtSpinners(otSpinner);
        initLimitSpinners(limitSpinner);//初始化常规禁限选项：禁止、限制、准限制
        initLimitGenesysSpinners(genesys_limitSpinner);//初始化Genesys禁限选项：Genesys、禁止
        initLimitListSpinners(limitListSpinner);
        initGenesysLimitListSpinners(genesys_limitListSpinner);
        initSetnameSearchFeature();
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

    private void initSetnameSearchFeature() {
        // 以布局点击事件作为初始化
        tag_setcode.setOnClickListener(v -> {
            showSetnameSearchableDialog();
        });
        btn_clear_setcode.setOnClickListener(v -> {
            resetSetcode();
        });
        // 初始化时确保提示文本正确显示
        updateSetcodeHintVisibility();
    }

    private void showSetnameSearchableDialog() {
        // 获取所有 setname 数据
        List<CardSet> setnames = mStringManager.getCardSets();

        // 创建用于显示的列表和映射
        List<Object> displayItems = new ArrayList<>();
        List<CardSet> setcode = new ArrayList<>();

        // 添加"无字段"选项
        displayItems.add(getString(R.string.label_set_No_Setcode));
        setcode.add(null); // 对应"无字段"选项

        // 添加所有 setname
        for (CardSet set : setnames) {
            displayItems.add(set.getName());
            setcode.add(set);
        }

        // 创建 SearchableListDialog
        SearchableListDialog dialog = new SearchableListDialog(mContext);
        dialog.setTitle(getString(R.string.label_set));

        // 设置标签删除监听器
        dialog.setOnTagDeleteListener(tagName -> {
            // 在 tag_setcode 中查找并删除对应的标签
            for (int i = 0; i < tag_setcode.getChildCount(); i++) {
                View child = tag_setcode.getChildAt(i);
                if (child instanceof LinearLayout) {
                    LinearLayout tagLayout = (LinearLayout) child;
                    if (tagLayout.getChildCount() >= 1 &&
                            tagLayout.getChildAt(0) instanceof TextView) {
                        TextView textView = (TextView) tagLayout.getChildAt(0);
                        if (tagName.equals(textView.getText().toString())) {
                            // 找到匹配的标签，从 tag_setcode 中移除
                            tag_setcode.removeViewAt(i);

                            // 同时从 setCodeList 中移除对应的 ID
                            if (tagName.equals(getString(R.string.label_set_No_Setcode))) {
                                setCodeList.remove(Long.valueOf(-1L));
                            } else {
                                // 查找对应的 CardSet 并移除其 code
                                for (CardSet set : setnames) {
                                    if (set.getName().equals(tagName)) {
                                        setCodeList.remove(Long.valueOf(set.getCode()));
                                        break;
                                    }
                                }
                            }
                            break; // 找到并删除后退出循环
                        }
                    }
                }
            }
            // 更新提示文本的可见性
            updateSetcodeHintVisibility();
        });

        // 在显示对话框前，将当前已选的标签添加到对话框
        for (long setCodeId : setCodeList) {
            if (setCodeId == -1L) {
                // 处理"无字段"选项
                dialog.addTagToListFrom(getString(R.string.label_set_No_Setcode));
            } else {
                // 查找对应的 setname 并添加
                for (CardSet set : setnames) {
                    if (set.getCode() == setCodeId) {
                        dialog.addTagToListFrom(set.getName());
                        break;
                    }
                }
            }
        }

        // 设置点击监听器
        dialog.setOnSearchableItemClickListener((item, position) -> {
            // 通过 item 内容判断是否为"无字段"选项
            if (item.toString().equals(getString(R.string.label_set_No_Setcode))) {
                // 处理"无setcode"选项
                if (!setCodeList.contains(-1L)) {
                    setCodeList.add(-1L);
                    addSetcodeTag(getString(R.string.label_set_No_Setcode), -1L);
                    // 如果对话框中有相同标签，也要添加
                    dialog.addTagToListFrom(getString(R.string.label_set_No_Setcode));
                }
            } else {
                // 从映射中获取 CardSet 对象
                // 遍历 setcode 列表找到匹配的项
                CardSet selectedSet = null;
                for (int i = 0; i < setcode.size(); i++) {
                    CardSet set = setcode.get(i);
                    if (set != null && set.getName().equals(item.toString())) {
                        selectedSet = set;
                        break;
                    }
                }

                if (selectedSet != null) {
                    long setCode = selectedSet.getCode();
                    String setName = selectedSet.getName();

                    // 添加到 setCodeList (避免重复)
                    if (!setCodeList.contains(setCode)) {
                        setCodeList.add(setCode);
                        addSetcodeTag(setName, setCode);
                        // 如果对话框中有相同标签，也要添加
                        dialog.addTagToListFrom(setName);
                    }
                }
            }
        });

        // 显示对话框
        dialog.show(displayItems);
    }

    // 在界面上添加 setcode 标签
    private void addSetcodeTag(String setName, long setCode) {
        // 创建标签容器布局
        LinearLayout tagLayout = new LinearLayout(mContext);
        tagLayout.setOrientation(LinearLayout.HORIZONTAL);
        tagLayout.setBackgroundResource(R.drawable.selected); // 使用适当的背景资源
        tagLayout.setPadding(8, 4, 8, 4);

        // 创建标签文本
        TextView tagView = new TextView(mContext);
        tagView.setText(setName);
        tagView.setGravity(Gravity.CENTER);
        tagView.setTextSize(14);
        tagView.setPadding(8, 0, 8, 0);
        tagView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 将文本添加到标签容器
        tagLayout.addView(tagView);

        // 将新标签添加到容器中
        tag_setcode.addView(tagLayout);

        // 检查是否需要隐藏提示文本
        updateSetcodeHintVisibility();
    }

    // 更新 setcode 提示文本的可见性
    private void updateSetcodeHintVisibility() {
        // 计算除了初始的 tv_setcode 之外的标签数量
        int actualTagCount = tag_setcode.getChildCount() - 1; // 减去1是因为保留了初始的提示标签

        // 获取 tv_setcode 引用（它是布局中的第一个子视图）
        if (tag_setcode.getChildCount() > 0) {
            View firstChild = tag_setcode.getChildAt(0);
            if (firstChild instanceof TextView &&
                    firstChild.getId() == R.id.tv_setcode) {
                // 如果实际标签数量大于0，隐藏提示文本；否则显示提示文本
                firstChild.setVisibility(actualTagCount > 0 ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void initCategoryButtons() {
        Drawable[] categoryIcon = new Drawable[]{
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_destroySpellTrap.png", 0, 0)),// 魔陷破坏
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_destroyMonster.png", 0, 0)),// 怪兽破坏
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_banish.png", 0, 0)),// 卡片除外
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_sendToGraveyard.png", 0, 0)),// 送去墓地
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_returnToHand.png", 0, 0)),// 返回手卡
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_returnToDeck.png", 0, 0)),// 返回卡组
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_destroyHand.png", 0, 0)),// 手卡破坏
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_destroyDeck.png", 0, 0)),// 卡组破坏
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_draw.png", 0, 0)),// 抽卡辅助
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_search.png", 0, 0)),// 卡组检索
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_recovery.png", 0, 0)),// 卡片回收
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_position.png", 0, 0)),// 表示形式
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_control.png", 0, 0)),// 控制权
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_changeAtkDef.png", 0, 0)),// 攻守变化
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_piercing.png", 0, 0)),// 穿刺伤害
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_repeatAttack.png", 0, 0)),// 多次攻击
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_limitAttack.png", 0, 0)),// 攻击限制
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_directAttack.png", 0, 0)),// 直接攻击
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_specialSummon.png", 0, 0)),// 特殊召唤
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_token.png", 0, 0)),// 衍生物
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_raceRelated.png", 0, 0)),// 种族相关
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_attributeRelated.png", 0, 0)),// 属性相关
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_damageLP.png", 0, 0)),// LP伤害
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_recoverLP.png", 0, 0)),// LP回复
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_undestroyable.png", 0, 0)),// 破坏耐性
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_ineffective.png", 0, 0)),// 效果耐性
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_counter.png", 0, 0)),// 指示物
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_gamble.png", 0, 0)),// 幸运
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_fusionRelated.png", 0, 0)),// 融合相关
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_synchroRelated.png", 0, 0)),// 同调相关
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_xyzRelated.png", 0, 0)),// 超量相关
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "category_negateEffect.png", 0, 0)),// 效果无效
        };
        categoryButtons = new Button[]{
                view.findViewById(R.id.btn_category_destroySpellTrap),// 魔陷破坏
                view.findViewById(R.id.btn_category_destroyMonster),// 怪兽破坏
                view.findViewById(R.id.btn_category_banish),// 卡片除外
                view.findViewById(R.id.btn_category_sendToGraveyard),// 送去墓地
                view.findViewById(R.id.btn_category_returnToHand),// 返回手卡
                view.findViewById(R.id.btn_category_returnToDeck),// 返回卡组
                view.findViewById(R.id.btn_category_destroyHand),// 手卡破坏
                view.findViewById(R.id.btn_category_destroyDeck),// 卡组破坏
                view.findViewById(R.id.btn_category_draw),// 抽卡辅助
                view.findViewById(R.id.btn_category_search),// 卡组检索
                view.findViewById(R.id.btn_category_recovery),// 卡片回收
                view.findViewById(R.id.btn_category_position),// 表示形式
                view.findViewById(R.id.btn_category_control),// 控制权
                view.findViewById(R.id.btn_category_changeAtkDef),// 攻守变化
                view.findViewById(R.id.btn_category_piercing),// 穿刺伤害
                view.findViewById(R.id.btn_category_repeatAttack),// 多次攻击
                view.findViewById(R.id.btn_category_limitAttack),// 攻击限制
                view.findViewById(R.id.btn_category_directAttack),// 直接攻击
                view.findViewById(R.id.btn_category_specialSummon),// 特殊召唤
                view.findViewById(R.id.btn_category_token),// 衍生物
                view.findViewById(R.id.btn_category_raceRelated),// 种族相关
                view.findViewById(R.id.btn_category_attributeRelated),// 属性相关
                view.findViewById(R.id.btn_category_damageLP),// LP伤害
                view.findViewById(R.id.btn_category_recoverLP),// LP回复
                view.findViewById(R.id.btn_category_undestroyable),// 破坏耐性
                view.findViewById(R.id.btn_category_ineffective),// 效果耐性
                view.findViewById(R.id.btn_category_counter),// 指示物
                view.findViewById(R.id.btn_category_gamble),// 幸运
                view.findViewById(R.id.btn_category_fusionRelated),// 融合相关
                view.findViewById(R.id.btn_category_synchroRelated),// 同调相关
                view.findViewById(R.id.btn_category_xyzRelated),// 超量相关
                view.findViewById(R.id.btn_category_negateEffect)// 效果无效
        };

        for (int i = 0; i < categoryButtons.length; i++) {
            //设置按钮样式
            Button button = categoryButtons[i];
            button.setCompoundDrawablePadding(4); // 图标和文字间距
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, categoryIcon[i], null, null);

            // 定义说明文字(从strings.conf提取以便随着语言切换而变化)
            button.setText(mStringManager.getCategoryString(categories[i].value()));
            int index = i;
            button.setOnClickListener(v -> {
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                    categoryList.remove(categories[index].value());
                } else {
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                    if (!categoryList.contains(categories[index].value())) {
                        categoryList.add(categories[index].value());
                    }
                }
            });
        }
        view.findViewById(R.id.ll_category).setOnClickListener(v -> {
            if (gl_category.getVisibility() == View.VISIBLE) {
                gl_category.setVisibility(View.GONE);
                iv_hide_category.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_category.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_category.setVisibility(View.VISIBLE);
                iv_hide_category.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        // 解除所有卡片种类的选中状态
        view.findViewById(R.id.btn_clear_category).setOnClickListener(v -> {
            resetCategory();
        });
    }

    private void initTypeButtons() {
        // 定义图标资源ID数组
        final Drawable[] cardTypeIcon = {
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "cardType_monster.png", 0, 0)),// 速攻图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "cardType_spell.png", 0, 0)),// 永续图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "cardType_trap.png", 0, 0)),// 装备图标
        };

        cardTypeButtons = new Button[]{
                view.findViewById(R.id.btn_type_monster),
                view.findViewById(R.id.btn_type_spell),
                view.findViewById(R.id.btn_type_trap)
        };
        typeIds = new long[]{
                CardType.Monster.getId(),
                CardType.Spell.getId(),
                CardType.Trap.getId()
        };
        for (int i = 0; i < cardTypeButtons.length; i++) {
            final int index = i;
            //设置按钮样式
            Button button = cardTypeButtons[index];
            button.setCompoundDrawablePadding(4); // 图标和文字间距
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, cardTypeIcon[index], null, null);

            // 定义说明文字(从strings.conf提取以便随着语言切换而变化)
            button.setText(mStringManager.getTypeString(typeIds[index]));

            button.setOnClickListener(v -> {
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                } else {
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                }

                if (!cardTypeButtons[0].isSelected()) {// 怪兽卡
                    layout_monster.setVisibility(View.GONE);
                    resetMonster();
                } else {
                    layout_monster.setVisibility(View.VISIBLE);
                    if (!typeList.contains(typeIds[0])) {
                        typeList.add(typeIds[0]);
                    }
                }

                if (cardTypeButtons[1].isSelected() || cardTypeButtons[2].isSelected()) {// 魔法陷阱卡被选中时显示图标栏
                    ll_icon.setVisibility(View.VISIBLE);
                } else {
                    ll_icon.setVisibility(View.GONE);//不选择魔法和陷阱类型时隐藏图标栏
                    // 取消选择所有可能被选中的图标，以免视觉上误导条件
                    for (int j = 0; j < iconButtons.length; j++) {
                        if (iconButtons[j].isSelected()) {
                            iconButtons[j].setSelected(false);
                            iconButtons[j].setTextColor(YGOUtil.c(R.color.gray));
                            iconButtons[j].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                        }

                    }
                    typeList.remove(typeIds[1]);
                    typeList.remove(typeIds[2]);
                }
                if (cardTypeButtons[1].isSelected()) {// 魔法卡
                    iconButtons[0].setVisibility(View.VISIBLE);// 速攻0
                    iconButtons[2].setVisibility(View.VISIBLE);// 装备2
                    iconButtons[3].setVisibility(View.VISIBLE);// 场地3
                    iconButtons[5].setVisibility(View.VISIBLE);// 仪式5

                    if (!typeList.contains(typeIds[1])) {
                        typeList.add(typeIds[1]);
                    }
                } else {
                    iconButtons[0].setVisibility(View.GONE);//速攻0
                    iconButtons[2].setVisibility(View.GONE);//装备2
                    iconButtons[3].setVisibility(View.GONE);//场地3
                    iconButtons[5].setVisibility(View.GONE);//仪式5

                    resetSpell();// 重置魔法相关按钮的选中状态
                }

                if (cardTypeButtons[2].isSelected()) {// 陷阱卡
                    iconButtons[4].setVisibility(View.VISIBLE);// 反击4
                    if (!typeList.contains(typeIds[2])) {
                        typeList.add(typeIds[2]);
                    }
                } else {
                    //反击陷阱4
                    iconButtons[4].setVisibility(View.GONE);
                    resetTrap();
                }

                if (!cardTypeButtons[0].isSelected() && !cardTypeButtons[1].isSelected() && !cardTypeButtons[2].isSelected()) {// 全没选中时显示全部
                    layout_monster.setVisibility(View.VISIBLE);
                    ll_icon.setVisibility(View.VISIBLE);
                    //魔法图标
                    iconButtons[0].setVisibility(View.VISIBLE);// 速攻0
                    iconButtons[2].setVisibility(View.VISIBLE);// 装备2
                    iconButtons[3].setVisibility(View.VISIBLE);// 场地3
                    iconButtons[5].setVisibility(View.VISIBLE);// 仪式5
                    //陷阱图标
                    iconButtons[4].setVisibility(View.VISIBLE);// 反击4
                }
                Log.i("CardSearcher", "[魔陷图标 卡片种类]:" + typeList);
            });
        }
        view.findViewById(R.id.ll_cardType).setOnClickListener(v -> {
            if (gl_cardType.getVisibility() == View.VISIBLE) {
                gl_cardType.setVisibility(View.GONE);
                iv_hide_cardType.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_cardType.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_cardType.setVisibility(View.VISIBLE);
                iv_hide_cardType.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        // 解除所有卡片种类的选中状态
        view.findViewById(R.id.btn_clear_card_type).setOnClickListener(v -> {
            resetCardType();
        });
    }

    private void initIconButtons() {
        // 定义图标资源ID数组
        final Drawable[] Icons = {
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "quickplay.png", 0, 0)),// 速攻图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "continuous.png", 0, 0)),// 永续图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "equip.png", 0, 0)),// 装备图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "field.png", 0, 0)),// 场地图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "counter.png", 0, 0)),// 反击图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "ritual.png", 0, 0)),// 仪式图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "normal.png", 0, 0)),// 通常图标
        };
        iconButtons = new Button[]{
                view.findViewById(R.id.btn_icon_quickPlay),// 速攻0
                view.findViewById(R.id.btn_icon_continuous),// 永续1
                view.findViewById(R.id.btn_icon_equip),// 装备2
                view.findViewById(R.id.btn_icon_field),// 场地3
                view.findViewById(R.id.btn_icon_counter),// 反击4
                view.findViewById(R.id.btn_icon_ritual),// 仪式5
                view.findViewById(R.id.btn_icon_normal),// 通常6
        };
        // 定义属性对应的ID值，使用long类型
        iconIds = new long[]{
                CardType.QuickPlay.getId(),// 速攻
                CardType.Continuous.getId(),// 永续
                CardType.Equip.getId(),// 装备
                CardType.Field.getId(),// 场地
                CardType.Counter.getId(),// 反击
                CardType.Ritual.getId(),// 仪式
                CardType.Normal.getId()// 通常（是指通常怪兽，这里只用于获取strings.conf对应的文本）
        };

        for (int i = 0; i < iconButtons.length; i++) {
            final int index = i;
            //设置按钮样式
            Button button = iconButtons[index];
            button.setCompoundDrawablePadding(4); // 图标和文字间距
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, Icons[index], null, null);

            // 定义说明文字(从strings.conf提取以便随着语言切换而变化)
            button.setText(mStringManager.getTypeString(iconIds[index]));

            iconButtons[i].setOnClickListener(v -> {
                if (typeList == null) {
                    typeList = new ArrayList<>();
                }

                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                }
                for (int j = 0; j < iconButtons.length; j++) {
                    if (iconButtons[j].isSelected()) {
                        if (!spellTrapTypeList.contains(iconIds[j])) {
                            spellTrapTypeList.add(iconIds[j]);
                        }
                    } else {
                        spellTrapTypeList.remove(iconIds[j]);
                    }
                }
                Log.d("CardSearcher", "[魔陷图标]包含种类:" + spellTrapTypeList);
            });
        }
        view.findViewById(R.id.ll_icon).setOnClickListener(v -> {
            if (gl_icon.getVisibility() == View.VISIBLE) {
                gl_icon.setVisibility(View.GONE);
                iv_hide_spelltrap.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_icon.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_icon.setVisibility(View.VISIBLE);
                iv_hide_spelltrap.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        view.findViewById(R.id.btn_clear_spelltrap_type).setOnClickListener(v -> {
            for (int i = 0; i < iconButtons.length; i++) {
                iconButtons[i].setSelected(false);
                iconButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                iconButtons[i].setTextColor(YGOUtil.c(R.color.gray));
                spellTrapTypeList.remove(iconIds[i]);
            }
        });
    }

    private void initAttributeButtons() {
        // 定义图标资源ID数组
        final Drawable[] attributeIcons = {
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "earth.png", 0, 0)),// 地属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "water.png", 0, 0)),// 水属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "fire.png", 0, 0)),// 火属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "wind.png", 0, 0)),// 风属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "light.png", 0, 0)),// 光属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "dark.png", 0, 0)),// 暗属性图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "divine.png", 0, 0)),// 神属性图标
        };

        // 初始化属性按钮
        attributeButtons = new Button[]{
                view.findViewById(R.id.btn_attr_earth),// 地
                view.findViewById(R.id.btn_attr_water),// 水
                view.findViewById(R.id.btn_attr_fire),// 火
                view.findViewById(R.id.btn_attr_wind),// 风
                view.findViewById(R.id.btn_attr_light),// 光
                view.findViewById(R.id.btn_attr_dark),// 暗
                view.findViewById(R.id.btn_attr_divine)// 神
        };

        for (int i = 0; i < attributeButtons.length; i++) {
            final int index = i;
            final long attributeId = attributeIds[i].getId();
            //设置按钮样式
            Button button = attributeButtons[index];
            button.setCompoundDrawablePadding(4); // 图标和文字间距
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, attributeIcons[index], null, null);

            // 定义说明文字(从strings.conf提取以便随着语言切换而变化)
            button.setText(mStringManager.getAttributeString(attributeId));

            button.setOnClickListener(v -> {
                if (attributeList == null) {
                    attributeList = new ArrayList<>();
                }

                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                    attributeList.remove(attributeId);
                } else {
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                    if (!attributeList.contains(attributeId)) {
                        attributeList.add(attributeId);
                    }
                }
            });
        }
        view.findViewById(R.id.ll_attr).setOnClickListener(v -> {
            if (gl_attr.getVisibility() == View.VISIBLE) {
                gl_attr.setVisibility(View.GONE);
                iv_hide_attr.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_attr.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_attr.setVisibility(View.VISIBLE);
                iv_hide_attr.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        // 点击解除所有选择的属性
        view.findViewById(R.id.btn_clear_attr).setOnClickListener(v -> {
            resetAttribute();
        });
    }

    private void initRaceButtons() {
        // 定义图标资源ID数组
        final Drawable[] raceIcons = {
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "warrior.png", 0, 0)),// 战士族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "spellcaster.png", 0, 0)),// 魔法师族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "fairy.png", 0, 0)),// 天使族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "fiend.png", 0, 0)),// 恶魔族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "zombie.png", 0, 0)),// 不死族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "machine.png", 0, 0)),// 机械族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "aqua.png", 0, 0)),// 水族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "pyro.png", 0, 0)),// 炎族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "rock.png", 0, 0)),// 岩石族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "winged_beast.png", 0, 0)),// 鸟兽族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "plant.png", 0, 0)),// 植物族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "insect.png", 0, 0)),// 昆虫族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "thunder.png", 0, 0)),// 雷族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "dragon.png", 0, 0)),// 龙族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "beast.png", 0, 0)),// 兽族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "beast_warrior.png", 0, 0)),// 兽战士族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "dinosaur.png", 0, 0)),// 恐龙族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "fish.png", 0, 0)),// 鱼族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "sea_serpent.png", 0, 0)),// 海龙族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "reptile.png", 0, 0)),// 爬虫类族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "psychic.png", 0, 0)),// 念动力族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "divine_beast.png", 0, 0)),// 幻神兽族图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "creator_god.png", 0, 0)),// 创造神图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "wyrm.png", 0, 0)),// 幻龙图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "cyberse.png", 0, 0)),// 电子界图标
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "illusion.png", 0, 0)),// 幻想魔图标
        };

        // 初始化属性按钮
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
                view.findViewById(R.id.btn_race_reptile),// 爬虫类
                view.findViewById(R.id.btn_race_psychic),// 念动力
                view.findViewById(R.id.btn_race_divineBeast),// 幻神兽
                view.findViewById(R.id.btn_race_creatorGod),// 创造神
                view.findViewById(R.id.btn_race_wyrm),// 幻龙
                view.findViewById(R.id.btn_race_cyberse),// 电子界
                view.findViewById(R.id.btn_race_illusion),// 幻想魔
        };

        for (int i = 0; i < raceButtons.length; i++) {
            final int index = i;
            final long raceId = raceIds[i].value();
            //设置按钮样式
            Button button = raceButtons[index];
            button.setCompoundDrawablePadding(4); // 图标和文字间距
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, raceIcons[index], null, null);

            // 定义说明文字(从strings.conf提取以便随着语言切换而变化)
            button.setText(mStringManager.getRaceString(raceId));

            raceButtons[i].setOnClickListener(v -> {
                if (raceList == null) {
                    raceList = new ArrayList<>();
                }

                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                    raceList.remove(raceId);
                } else {
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                    if (!raceList.contains(raceId))
                        raceList.add(raceId);
                }
            });
        }
        view.findViewById(R.id.ll_race).setOnClickListener(v -> {
            if (gl_race.getVisibility() == View.VISIBLE) {
                gl_race.setVisibility(View.GONE);
                iv_hide_race.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_race.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_race.setVisibility(View.VISIBLE);
                iv_hide_race.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        // 解除所有选中的种族
        view.findViewById(R.id.btn_clear_race).setOnClickListener(v -> {
            resetRace();
        });
    }

    private void initMonsterTypeButton() {
        TypeIcon = new Drawable[]{
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_normal.png", 0, 0)),// 通常
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_effect.png", 0, 0)),// 效果
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_fusion.png", 0, 0)),// 融合
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_ritual.png", 0, 0)),// 仪式
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_spirit.png", 0, 0)),// 灵魂
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_union.png", 0, 0)),// 同盟
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_gemini.png", 0, 0)),// 二重
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_tuner.png", 0, 0)),// 调整
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_synchro.png", 0, 0)),// 同调
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_flip.png", 0, 0)),// 反转
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_toon.png", 0, 0)),// 卡通
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_xyz.png", 0, 0)),// 超量
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_pendulum.png", 0, 0)),// 灵摆
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_specialSummon.png", 0, 0)),// 特殊召唤
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_link.png", 0, 0)),// 连接
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "type_token.png", 0, 0)),// 衍生物
        };
        monsterTypeButtons = new Button[]{
                view.findViewById(R.id.btn_type_normal),// 通常
                view.findViewById(R.id.btn_type_effect),// 效果
                view.findViewById(R.id.btn_type_fusion),// 融合
                view.findViewById(R.id.btn_type_ritual),// 仪式
                view.findViewById(R.id.btn_type_spirit),// 灵魂
                view.findViewById(R.id.btn_type_union),// 同盟
                view.findViewById(R.id.btn_type_gemini),// 二重
                view.findViewById(R.id.btn_type_tuner),// 调整
                view.findViewById(R.id.btn_type_synchro),// 同调
                view.findViewById(R.id.btn_type_flip),// 反转
                view.findViewById(R.id.btn_type_toon),// 卡通
                view.findViewById(R.id.btn_type_xyz),// 超量
                view.findViewById(R.id.btn_type_pendulum),// 灵摆
                view.findViewById(R.id.btn_type_specialSummon),// 特殊召唤
                view.findViewById(R.id.btn_type_link),// 连接
                view.findViewById(R.id.btn_type_token)// 衍生物
        };
        monsterTypeIds = new long[]{
                CardType.Normal.getId(),
                CardType.Effect.getId(),
                CardType.Fusion.getId(),
                CardType.Ritual.getId(),
                CardType.Spirit.getId(),
                CardType.Union.getId(),
                CardType.Gemini.getId(),
                CardType.Tuner.getId(),
                CardType.Synchro.getId(),
                CardType.Flip.getId(),
                CardType.Toon.getId(),
                CardType.Xyz.getId(),
                CardType.Pendulum.getId(),
                CardType.Sp_Summon.getId(),
                CardType.Link.getId(),
                CardType.Token.getId()
        };
        for (int i = 0; i < monsterTypeButtons.length; i++) {
            final int index = i;
            //设置按钮样式
            Button button = monsterTypeButtons[index];
            button.setText(mStringManager.getTypeString(monsterTypeIds[i]));
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, TypeIcon[index], null, null);
            button.setOnClickListener(v -> {
                if (typeList == null) {
                    typeList = new ArrayList<>();
                }
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    typeList.remove(monsterTypeIds[index]);
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    if (!typeList.contains(monsterTypeIds[index]))
                        typeList.add(monsterTypeIds[index]);
                }
                Log.w("CardSearcher", "[怪兽 种类]:" + typeList);
            });
        }
        RadioGroup radioGroup = findViewById(R.id.radio_group);// 切换怪兽类型内部逻辑
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_and) {
                isAnd = true;
            } else if (checkedId == R.id.rb_or) {
                isAnd = false;
            }
        });
        view.findViewById(R.id.ll_monsterType).setOnClickListener(v -> {
            if (gl_monsterType.getVisibility() == View.VISIBLE) {
                gl_monsterType.setVisibility(View.GONE);
                iv_hide_monsterType.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_monsterType.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_monsterType.setVisibility(View.VISIBLE);
                iv_hide_monsterType.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        // 解除所有选中的怪兽子种类
        view.findViewById(R.id.btn_clear_monsterType).setOnClickListener(v -> {
            resetMonsterType();
        });
    }

    private void initExcludeTypeButton() {
        exclude_typeButtons = new Button[]{
                view.findViewById(R.id.btn_exclude_type_normal),// 通常
                view.findViewById(R.id.btn_exclude_type_effect),// 效果
                view.findViewById(R.id.btn_exclude_type_fusion),// 融合
                view.findViewById(R.id.btn_exclude_type_ritual),// 仪式
                view.findViewById(R.id.btn_exclude_type_spirit),// 灵魂
                view.findViewById(R.id.btn_exclude_type_union),// 同盟
                view.findViewById(R.id.btn_exclude_type_gemini),// 二重
                view.findViewById(R.id.btn_exclude_type_tuner),// 调整
                view.findViewById(R.id.btn_exclude_type_synchro),// 同调
                view.findViewById(R.id.btn_exclude_type_flip),// 反转
                view.findViewById(R.id.btn_exclude_type_toon),// 卡通
                view.findViewById(R.id.btn_exclude_type_xyz),// 超量
                view.findViewById(R.id.btn_exclude_type_pendulum),// 灵摆
                view.findViewById(R.id.btn_exclude_type_specialSummon),// 特殊召唤
                view.findViewById(R.id.btn_exclude_type_link),// 连接
                view.findViewById(R.id.btn_exclude_type_token)// 衍生物
        };
        for (int i = 0; i < exclude_typeButtons.length; i++) {
            final int index = i;
            //设置按钮样式
            Button button = exclude_typeButtons[index];
            button.setText(mStringManager.getTypeString(monsterTypeIds[i]));
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, TypeIcon[index], null, null);
            button.setOnClickListener(v -> {
                if (excludeTypeList == null) {
                    excludeTypeList = new ArrayList<>();
                }
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    excludeTypeList.remove(monsterTypeIds[index]);
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius_p));
                    if (!excludeTypeList.contains(monsterTypeIds[index]))
                        excludeTypeList.add(monsterTypeIds[index]);
                }
                Log.w("CardSearcher", "[排除种类]:" + excludeTypeList);
            });
        }
        view.findViewById(R.id.ll_excludeType).setOnClickListener(v -> {
            if (gl_exclude_type.getVisibility() == View.VISIBLE) {
                gl_exclude_type.setVisibility(View.GONE);
                iv_hide_exclude_type.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_exclude_type.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_exclude_type.setVisibility(View.VISIBLE);
                iv_hide_exclude_type.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        // 解除所有选中的排除怪兽子种类
        view.findViewById(R.id.btn_clear_excludeType).setOnClickListener(v -> {
            resetExcludeType();
        });
    }

    private void initLevelButtons() {
        //从一个整体图片中裁切出13个数字图片
        Bitmap chainNumber = BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/number.png", 0, 0);
        int width = chainNumber.getWidth() / 5;
        int height = chainNumber.getHeight() / 4;

        final Drawable[] numberIcon = new Drawable[]{
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, 0, 0, width, height)),// 1
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width, 0, width, height)),// 2
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width * 2, 0, width, height)),// 3
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width * 3, 0, width, height)),// 4
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width * 4, 0, width, height)),// 5
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, 0, height, width, height)),// 6
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width, height, width, height)),// 7
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width * 2, height, width, height)),// 8
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width * 3, height, width, height)),// 9
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width * 4, height, width, height)),// 10
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, 0, height * 2, width, height)),// 11
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width, height * 2, width, height)),// 12
                new BitmapDrawable(mContext.getResources(), Bitmap.createBitmap(chainNumber, width * 2, height * 2, width, height)),// 13
        };

        // 创建不同等级范围的背景图片数组
        final Drawable[] backgrounds = new Drawable[13];
        for (int i = 0; i < backgrounds.length; i++) {
            if (i < 8) { // 等级1-8 (索引0-7)
                Bitmap star1_8Bitmap = BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "star_1_8.png", 0, 0);
                backgrounds[i] = new BitmapDrawable(mContext.getResources(), star1_8Bitmap);
            } else if (i < 12) { // 等级9-12 (索引8-11)
                Bitmap star9_12Bitmap = BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "star_9_12.png", 0, 0);
                backgrounds[i] = new BitmapDrawable(mContext.getResources(), star9_12Bitmap);
            } else { // 等级13 (索引12)
                Bitmap rankBitmap = BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "star_rank.png", 0, 0);
                backgrounds[i] = new BitmapDrawable(mContext.getResources(), rankBitmap);
            }
        }

        levelButtons = new ImageButton[]{
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
                view.findViewById(R.id.btn_LRA_13)
        };
        for (int i = 0; i < levelButtons.length; i++) {
            final int index = i;
            //设置按钮样式
            ImageButton button = levelButtons[index];
            // 设置图标
            // 创建图层：背景在下，前景（数字）在上
            Drawable[] layers = new Drawable[2];
            layers[0] = backgrounds[index]; // 背景
            layers[1] = numberIcon[index];  // 数字
            LayerDrawable layerDrawable = new LayerDrawable(layers);
            // 设置前景的位置（居中）
            int padding = 4; // 可调整边距
            layerDrawable.setLayerInset(1, padding, padding, padding, padding);
            button.setImageDrawable(layerDrawable);

            button.setOnClickListener(v -> {
                if (levelList == null) {
                    levelList = new ArrayList<>();
                }
                Integer levelValue = index + 1;
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    levelList.remove(levelValue);
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    if (!levelList.contains(levelValue)) {
                        levelList.add(levelValue);
                    }
                }
            });
        }
        view.findViewById(R.id.ll_level).setOnClickListener(v -> {
            if (gl_level_rank_link.getVisibility() == View.VISIBLE) {
                gl_level_rank_link.setVisibility(View.GONE);
                iv_hide_level_rank_link.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_level_rank_link.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_level_rank_link.setVisibility(View.VISIBLE);
                iv_hide_level_rank_link.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        view.findViewById(R.id.btn_clear_level).setOnClickListener(v -> {
            resetLevel();
        });
    }

    private void initPendulumScaleButtons() {
        final Drawable[] PScaleIcon = new Drawable[]{
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/lscale_0.png", 0, 0)),// 0
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/lscale_1.png", 0, 0)),// 1
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/lscale_2.png", 0, 0)),// 2
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/lscale_3.png", 0, 0)),// 3
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/lscale_4.png", 0, 0)),// 4
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/lscale_5.png", 0, 0)),// 5
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/lscale_6.png", 0, 0)),// 6
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/rscale_7.png", 0, 0)),// 7
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/rscale_8.png", 0, 0)),// 8
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/rscale_9.png", 0, 0)),// 9
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/rscale_10.png", 0, 0)),// 10
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/rscale_11.png", 0, 0)),// 11
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/rscale_12.png", 0, 0)),// 12
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSETS_PATH + "textures/extra/rscale_13.png", 0, 0)),// 13
        };
        pendulumScaleButtons = new ImageButton[]{
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
        for (int i = 0; i < pendulumScaleButtons.length; i++) {
            final Integer index = i;
            //设置按钮样式
            ImageButton button = pendulumScaleButtons[index];
            // 设置图标
            button.setImageDrawable(PScaleIcon[index]);
            button.setOnClickListener(v -> {
                if (pendulumScaleList == null) {
                    pendulumScaleList = new ArrayList<>();
                }
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    pendulumScaleList.remove(index);
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    if (!pendulumScaleList.contains(index)) {
                        pendulumScaleList.add(index);
                    }
                }
            });
        }
        view.findViewById(R.id.ll_pScale).setOnClickListener(v -> {
            if (gl_pendulum_scale.getVisibility() == View.VISIBLE) {
                gl_pendulum_scale.setVisibility(View.GONE);
                iv_hide_pendulum_scale.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
            } else {
                gl_pendulum_scale.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_pendulum_scale.setVisibility(View.VISIBLE);
                iv_hide_pendulum_scale.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
        view.findViewById(R.id.btn_clear_pScale).setOnClickListener(v -> {
            resetPScale();
        });
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
            Log.i("cardsearcher", "setcodeList: " + setCodeList.toString());
            // 判断基本卡片类型选择状态
            boolean hasBasicSpell = typeList.contains(CardType.Spell.getId());
            boolean hasBasicTrap = typeList.contains(CardType.Trap.getId());
            boolean hasBasicMonster = typeList.contains(CardType.Monster.getId());

            // 判断是否有怪兽具体类型选择
            boolean hasMonsterSpecificType = typeList.stream()
                    .anyMatch(id -> Arrays.stream(monsterTypeIds).anyMatch(mid -> mid == id));

            // 判断是否有魔法陷阱具体类型选择
            boolean hasSpellTrapSpecificType = !spellTrapTypeList.isEmpty();

            // 确定需要搜索哪些类型
            boolean shouldSearchSpells = hasBasicSpell || (!hasBasicMonster && !hasBasicTrap && hasSpellTrapSpecificType);
            boolean shouldSearchTraps = hasBasicTrap || (!hasBasicMonster && !hasBasicSpell && hasSpellTrapSpecificType);
            boolean shouldSearchMonsters = hasBasicMonster || hasMonsterSpecificType;

            boolean normalSpellTrap = spellTrapTypeList.contains(CardType.Normal.getId());

            int ot = getIntSelect(otSpinner);
            int limitType = genesys_Switch.isChecked() ? getIntSelect(genesys_limitSpinner) : getIntSelect(limitSpinner);
            String limitName = genesys_Switch.isChecked() ? getSelectText(genesys_limitListSpinner) : getSelectText(limitListSpinner);
            String keyword = text(keyWord);
            List<CardSearchInfo> searchInfos = new ArrayList<>();
            // 魔法
            if (shouldSearchSpells) {
                List<Long> types = new ArrayList<>();
                List<Long> excludTypes = new ArrayList<>(Arrays.asList(CardType.Monster.getId(), CardType.Trap.getId()));

                spellIds = new long[]{
                        CardType.QuickPlay.getId(),
                        CardType.Continuous.getId(),
                        CardType.Equip.getId(),
                        CardType.Field.getId(),
                        CardType.Ritual.getId()
                };
                for (long selected : spellIds) {
                    if (normalSpellTrap && !spellTrapTypeList.contains(selected)) {
                        excludTypes.add(selected);
                    } else if (!normalSpellTrap && spellTrapTypeList.contains(selected)) {
                        types.add(selected);
                    }
                }

                if (spellTrapTypeList.isEmpty() || normalSpellTrap || !types.isEmpty()) {
                    CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                            .keyword(keyword)
                            .attribute(attributeList)
                            .level(levelList) // 注意：魔法卡实际上不使用等级，也没有魔法怪兽，可以不传
                            .race(raceList)   // 同上，可以注释掉不传
                            .atk(text(atkText))// 同上，可以注释掉不传
                            .def(text(defText))// 同上，可以注释掉不传
                            .ot(ot)
                            .limitType(limitType)
                            .limitName(limitName)
                            .setcode(setCodeList)
                            .setcode_logic(setcode_isAnd)
                            .category(categoryList)
                            .types(types)
                            .except_types(excludTypes)
                            .type_logic(false)
                            .build();
                    searchInfos.add(searchInfo);
                }
            }
            // 陷阱
            if (shouldSearchTraps) {
                List<Long> types = new ArrayList<>();
                List<Long> excludTypes = new ArrayList<>(Arrays.asList(CardType.Monster.getId(), CardType.Spell.getId()));

                trapIds = new long[]{
                        CardType.Continuous.getId(),
                        CardType.Counter.getId()
                };
                for (long selected : trapIds) {
                    if (normalSpellTrap && !spellTrapTypeList.contains(selected)) {
                        excludTypes.add(selected);
                    } else if (!normalSpellTrap && spellTrapTypeList.contains(selected)) {
                        types.add(selected);
                    }
                }

                if (spellTrapTypeList.isEmpty() || normalSpellTrap || !types.isEmpty()) {
                    CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                            .keyword(keyword)
                            .attribute(attributeList)
                            .level(levelList) // 如果需要允许搜索到陷阱怪兽，可以允许传值等级
                            .race(raceList)   // 同上，可以允许传值种族
                            .atk(text(atkText))// 同上，可以允许传攻击力
                            .def(text(defText))// 同上，可以允许传守备力
                            .ot(ot)
                            .limitType(limitType)
                            .limitName(limitName)
                            .setcode(setCodeList)
                            .setcode_logic(setcode_isAnd)
                            .category(categoryList)
                            .types(types)
                            .except_types(excludTypes)
                            .type_logic(false)
                            .build();
                    searchInfos.add(searchInfo);
                }
            }
            // 怪兽
            if (shouldSearchMonsters) {
                List<Long> excludTypes = new ArrayList<>(Arrays.asList(CardType.Spell.getId(), CardType.Trap.getId()));
                excludTypes.addAll(excludeTypeList);

                // 只包括真正的怪兽类型（排除基本的Spell/Trap/Monster类型）
                List<Long> monsterTypes = new ArrayList<>();
                for (Long typeId : typeList) {
                    if (Arrays.stream(monsterTypeIds).anyMatch(id -> id == typeId)) {
                        monsterTypes.add(typeId);
                    }
                }

                CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                        .keyword(keyword)
                        .attribute(attributeList)
                        .level(levelList)
                        .race(raceList)
                        .atk(text(atkText))
                        .def(text(defText))
                        .pscale(pendulumScaleList)
                        .limitType(limitType)
                        .limitName(limitName)
                        .setcode(setCodeList)
                        .setcode_logic(setcode_isAnd)
                        .category(categoryList)
                        .ot(ot)
                        .types(monsterTypes)
                        .type_logic(isAnd)
                        .except_types(excludTypes)
                        .linkKey(lineKey)
                        .build();
                searchInfos.add(searchInfo);
            }

            // 如果没有任何搜索条件，执行通用搜索
            if (searchInfos.isEmpty()) {
                CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                        .keyword(keyword)
                        .attribute(attributeList)
                        .level(levelList)
                        .race(raceList)
                        .atk(text(atkText))
                        .def(text(defText))
                        .pscale(pendulumScaleList)
                        .limitType(limitType)
                        .limitName(limitName)
                        .setcode(setCodeList)
                        .setcode_logic(setcode_isAnd)
                        .category(categoryList)
                        .ot(ot)
                        .linkKey(lineKey)
                        .build();
                searchInfos.add(searchInfo);
            }

            Log.i(TAG, searchInfos.toString());
            mICardSearcher.search(searchInfos);
        }
    }

    private void resetAll() {
        if (mICardSearcher != null) {
            mICardSearcher.onReset();
        }
        keyWord.setText(null);
        reset(otSpinner);
        reset(limitSpinner.getVisibility() == View.VISIBLE ? limitSpinner : genesys_limitSpinner);
        resetCategory();
        resetCardType();
        resetMonster();
        resetIcons();
        if (layout_monster.getVisibility() == View.GONE) layout_monster.setVisibility(View.VISIBLE);
        if (ll_icon.getVisibility() == View.GONE) ll_icon.setVisibility(View.VISIBLE);
    }

    private void resetSetcode() {
        // 清空 setCodeList
        setCodeList.clear();

        // 移除所有标签，但保留第一个提示标签 (tv_setcode)
        while (tag_setcode.getChildCount() > 1) {
            tag_setcode.removeViewAt(1); // 从索引1开始移除，保留索引0的提示标签
        }

        // 确保提示标签是可见的
        if (tag_setcode.getChildCount() > 0) {
            View firstChild = tag_setcode.getChildAt(0);
            if (firstChild instanceof TextView &&
                    firstChild.getId() == R.id.tv_setcode) {
                firstChild.setVisibility(View.VISIBLE);
            }
        }
    }

    private void resetCategory() {
        for (int i = 0; i < categoryButtons.length; i++) {
            categoryButtons[i].setSelected(false);
            categoryButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            categoryButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            categoryList.remove(categories[i].value());
        }
    }

    private void resetCardType() {
        for (int i = 0; i < cardTypeButtons.length; i++) {
            cardTypeButtons[i].setSelected(false);
            cardTypeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            cardTypeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            typeList.remove(typeIds[i]);
        }
    }

    private void resetMonster() {
        // 重置怪兽卡按钮为未选中
        cardTypeButtons[0].setSelected(false);
        cardTypeButtons[0].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        cardTypeButtons[0].setTextColor(YGOUtil.c(R.color.gray));
        typeList.remove(CardType.Monster.getId()); // 从typeList中移除怪兽相关的ID

        resetAttribute();// 重置属性按钮为未选中
        resetRace();// 重置种族按钮为未选中
        resetMonsterType();// 重置怪兽子种类按钮为未选中
        resetExcludeType();// 重置排除种类按钮为未选中
        resetLevel();// 重置等级按钮为未选中
        resetPScale();// 重置灵摆刻度按钮为未选中

        atkText.setText(null);// 清除输入的攻击力
        defText.setText(null);// 清除输入的守备力
        lineKey = 0;// 清除灵摆键值
    }

    private void resetAttribute() {
        for (int i = 0; i < attributeButtons.length; i++) {
            attributeButtons[i].setSelected(false);
            attributeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            attributeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            attributeList.remove(attributeIds[i]);
        }
    }

    private void resetRace() {
        for (int i = 0; i < raceButtons.length; i++) {
            raceButtons[i].setSelected(false);
            raceButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            raceButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            raceList.remove(raceIds[i]);
        }
    }

    private void resetMonsterType() {
        for (int i = 0; i < monsterTypeButtons.length; i++) {
            monsterTypeButtons[i].setSelected(false);
            monsterTypeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            monsterTypeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            typeList.remove(monsterTypeIds[i]);
        }
    }

    private void resetExcludeType() {
        for (int i = 0; i < exclude_typeButtons.length; i++) {
            exclude_typeButtons[i].setSelected(false);
            exclude_typeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            exclude_typeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            excludeTypeList.remove(monsterTypeIds[i]);
        }
    }

    private void resetLevel() {
        for (int i = 0; i < levelButtons.length; i++) {
            levelButtons[i].setSelected(false);
            levelButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            Integer levelValue = i + 1;
            levelList.remove(levelValue);
        }
    }

    private void resetPScale() {
        for (int i = 0; i < pendulumScaleButtons.length; i++) {
            pendulumScaleButtons[i].setSelected(false);
            pendulumScaleButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            Integer pendulumScaleValue = i;
            pendulumScaleList.remove(pendulumScaleValue);
        }
    }

    private void resetSpell() {
        //解除魔法卡选中状态
        cardTypeButtons[1].setSelected(false);
        cardTypeButtons[1].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        cardTypeButtons[1].setTextColor(YGOUtil.c(R.color.gray));
        spellButtons = new Button[]{
                view.findViewById(R.id.btn_icon_quickPlay),// 速攻0
                view.findViewById(R.id.btn_icon_equip),// 装备2
                view.findViewById(R.id.btn_icon_field),// 场地3
                view.findViewById(R.id.btn_icon_ritual),// 仪式5
        };
        //解除魔法相关图标按钮的选中状态
        for (Button spellButton : spellButtons) {
            spellButton.setSelected(false);
            spellButton.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            spellButton.setTextColor(YGOUtil.c(R.color.gray));
        }

        typeList.remove(CardType.Spell.getId());
    }

    private void resetTrap() {
        //解除陷阱卡选中状态
        cardTypeButtons[2].setSelected(false);
        cardTypeButtons[2].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        cardTypeButtons[2].setTextColor(YGOUtil.c(R.color.gray));
        //解除陷阱相关图标按钮的选中状态
        Button trapButton = view.findViewById(R.id.btn_icon_counter);// 反击4
        trapButton.setSelected(false);
        trapButton.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        trapButton.setTextColor(YGOUtil.c(R.color.gray));

        typeList.remove(CardType.Trap.getId());
    }

    private void resetIcons() {
        resetSpell();// 解除魔法独有类型的选中状态
        resetTrap();// 解除陷阱独有类型的选中状态（也就反击陷阱）
        Button[] conti_normal_icons = new Button[]{
                view.findViewById(R.id.btn_icon_continuous),// 永续
                view.findViewById(R.id.btn_icon_normal),// 通常
        };
        //解除魔法陷阱共有种类永续、通常图标按钮的选中状态
        for (Button bothButton : conti_normal_icons) {
            bothButton.setSelected(false);
            bothButton.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            bothButton.setTextColor(YGOUtil.c(R.color.gray));
        }
        spellTrapTypeList.remove(CardType.Continuous.getId());
        spellTrapTypeList.remove(CardType.Normal.getId());
        // 将所有icon全部显示
        for (Button iconButton : iconButtons) {
            iconButton.setVisibility(View.VISIBLE);
        }
    }

    public interface CallBack {
        void setLimit(LimitList limit, String caller);

        void onSearchStart();

        void onSearchResult(List<Card> Cards, boolean isHide);
    }
}

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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardSearchInfo;
import cn.garymb.ygomobile.loader.ICardSearcher;
import cn.garymb.ygomobile.ui.adapters.KeywordHistoryAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.SearchableListDialog;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
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

/**
 * 卡牌搜索面板主类：控件装配、点击分发、搜索入口与历史条件恢复。
 * <p>
 * 原 2769 行大类按职责拆分至继承链 {@link CardSearcherBase} → {@link CardSearcherFilterHistory}
 * → {@link CardSearcherOptionButtons} → {@link CardSearcherExtraFilters}，
 * 对外公开 API（含嵌套 CallBack 接口）与全部行为保持不变。
 */
public class CardSearcher extends CardSearcherExtraFilters implements View.OnClickListener {
    public CardSearcher(View view, ICardSearcher iCardSearcher) {
        this.view = view;
        this.mContext = view.getContext();
        this.mICardSearcher = iCardSearcher;
        this.mSettings = AppsSettings.get();
        mStringManager = DataManager.get().getStringManager();
        mLimitManager = DataManager.get().getLimitManager();
        keyWord = findViewById(R.id.edt_word1);
        chk_multi_keyword = findViewById(R.id.chk_multi_keyword);
        genesys_Switch = findViewById(R.id.sw_genesys_mode);//genesys模式开关
        limitSpinner = findViewById(R.id.sp_limit);
        genesys_limitSpinner = findViewById(R.id.sp_genesys_limit);//初始化genesys禁限选项布局
        limitListSpinner = findViewById(R.id.sp_limit_list);
        genesys_limitListSpinner = findViewById(R.id.sp_genesys_limit_list);//初始化genesys禁卡表布局
        // 专属（OCG TCG OCG|TCG SC_OCG CUSTOM）
        gl_ot = findViewById(R.id.gl_ot);
        iv_hide_ot = findViewById(R.id.iv_hide_ot);
        otIds = CardOt.values();
        otList = new ArrayList<>();
        // 字段
        iv_hide_setCode = findViewById(R.id.iv_hide_setCode);
        tag_setcode = findViewById(R.id.tag_setcode);
        setcode_isAnd = false;
        setCodeList = new ArrayList<>();
        // 效果类型宫格布局
        gl_category = findViewById(R.id.gl_category);
        iv_hide_category = findViewById(R.id.iv_hide_category);
        categories = CardCategory.values();
        categoryList = new ArrayList<>();
        // 卡片类型宫格布局
        gl_cardType = findViewById(R.id.gl_cardType);
        iv_hide_cardType = findViewById(R.id.iv_hide_cardType);
        cardTypeList = new ArrayList<>();
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
        monsterTypeList = new ArrayList<>();
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
        // 连接箭头布局
        ll_linkControl = findViewById(R.id.ll_linkcontrol);
        iv_hide_linkmarker = findViewById(R.id.iv_hide_linkmarker);
        // 攻击力、守备力
        atkText = findViewById(R.id.edt_atk);
        defText = findViewById(R.id.edt_def);

        isEqual = false;// 攻击力和守备力相等满足的逻辑开关
        btn_equal = findViewById(R.id.btn_equal);
        btn_equal.setOnClickListener(this);

        til_atk = findViewById(R.id.til_atk);
        ll_equal_def = findViewById(R.id.ll_equal_def);
        chk_atkDef_sum = findViewById(R.id.chk_atkDef_sum);
        isSum = false;// 攻击力与守备力之和满足的逻辑开关

        chk_atkDef_or = findViewById(R.id.chk_atkDef_or);
        isOr = false;// 攻击力或守备力其中之一满足的逻辑开关

        myFavButton = findViewById(R.id.btn_my_fav);
        searchButton = findViewById(R.id.btn_search);
        resetButton = findViewById(R.id.btn_reset);
        myFavButton.setOnClickListener(this);
        searchButton.setOnClickListener(this);
        resetButton.setOnClickListener(this);

        btnLastSearch = findViewById(R.id.btn_last_search);
        btnNextSearch = findViewById(R.id.btn_next_search);
        btnLastSearch.setOnClickListener(this);
        btnNextSearch.setOnClickListener(this);
        updateSearchNavButtons();

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
        chk_atkDef_sum.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                String hintText = isChecked ? YGOUtil.s(R.string.sum_atkDef) : (chk_atkDef_or.isChecked() ? YGOUtil.s(R.string.or_atkDef) : "ATK");
                til_atk.setHint(hintText);
                atkText.setHint(hintText);
                // 只有当chk_atkDef_sum和chk_atkDef_or都没被勾选时才显示ll_equal_def
                ll_equal_def.setVisibility(!isChecked && !chk_atkDef_or.isChecked() ? View.VISIBLE : View.GONE);
                isSum = isChecked;
                if (isChecked) {
                    til_atk.setHint(YGOUtil.s(R.string.sum_atkDef));
                    atkText.setHint(YGOUtil.s(R.string.sum_atkDef));
                    defText.setText("");
                    resetEqualButton();//重置相等模式按钮
                    isOr = false;
                    chk_atkDef_or.setChecked(false);
                }
            }
        });
        chk_atkDef_or.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                String hintText = isChecked ? YGOUtil.s(R.string.or_atkDef) : (chk_atkDef_sum.isChecked() ? YGOUtil.s(R.string.sum_atkDef) : "ATK");
                til_atk.setHint(hintText);
                atkText.setHint(hintText);
                // 只有当chk_atkDef_sum和chk_atkDef_or都没被勾选时才显示ll_equal_def
                ll_equal_def.setVisibility(!isChecked && !chk_atkDef_sum.isChecked() ? View.VISIBLE : View.GONE);
                isOr = isChecked;
                if (isChecked) {
                    defText.setText("");
                    resetEqualButton();//重置相等模式按钮
                    isSum = false;
                    chk_atkDef_sum.setChecked(false);

                }
            }
        });

        keyWord.setOnEditorActionListener(searchListener);
        // 关键字输入框：同时支持输入与下拉显示搜索历史记录
        keyWord.setThreshold(1);
        // 点击历史记录项：自动填入关键词并搜索
        //keyWord.setOnItemClickListener((parent, v, position, id) -> search());
        // 点击输入框：直接弹出历史记录下拉列表（已聚焦时再次点按也生效）
        keyWord.setOnClickListener(v -> showKeywordDropdown());
        // 获得焦点：弹出历史记录下拉列表
        keyWord.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showKeywordDropdown();
            }
        });
        // 清空输入内容时恢复显示全部历史记录
        keyWord.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 0 && keyWord.isPopupShowing()) {
                    refreshKeywordHistory();
                }
            }
        });
        // 初始加载搜索历史记录
        refreshKeywordHistory();

        chk_multi_keyword.setChecked(mSettings.getKeyWordsSplit() != 0);
        chk_multi_keyword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSettings.setKeyWordsSplit(isChecked ? 1 : 0);
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


    public void onClick(View v) {
        if (v.getId() == R.id.btn_search) {
            search();
            // 如果搜索历史不为空，跳转到最后一条之后，以便显示"上一次搜索"按钮
            if (!searchHistory.isEmpty()) {
                searchIndex = searchHistory.size();
                updateSearchNavButtons();
            }
        } else if (v.getId() == R.id.btn_reset) {
            resetAll();
        } else if (v.getId() == myFavButton.getId()) {
            if (isShowFavorite()) {
                hideFavorites(true);
            } else {
                showFavorites(true);
            }
        } else if (v.getId() == R.id.btn_last_search) {
            onLastSearch();
        } else if (v.getId() == R.id.btn_next_search) {
            onNextSearch();
        } else if (v.getId() == R.id.btn_equal) {
            if (btn_equal.isSelected()) {
                resetEqualButton();//重置相等模式按钮
            } else {//未选中时的逻辑
                btn_equal.setSelected(true);
                btn_equal.setTextColor(YGOUtil.c(R.color.yellow));
                btn_equal.setBackground(mContext.getDrawable(R.drawable.radius));
                isEqual = true;
                //开启时，将空的输入框填写上有内容的输入框的文本
                if (!text(atkText).isEmpty() && text(defText).isEmpty()) {
                    defText.setText(atkText.getText());
                }
                if (text(atkText).isEmpty() && !text(defText).isEmpty()) {
                    atkText.setText(defText.getText());
                }
            }
        }
    }

    public void search(String message) {
        if (TextUtils.isEmpty(message)) {
            message = "";
        }
        keyWord.setText(message, false);
        search();
    }

    public String getCurrentKeyword() {
        return text(keyWord);
    }
    protected void search() {
        if (mICardSearcher != null) {
            keyWord.dismissDropDown();
            String keyword = text(keyWord);
            if (!TextUtils.isEmpty(keyword)) {
                SharedPreferenceUtil.addKeywordHistory(keyword);
            }
            int limitType = genesys_Switch.isChecked() ? getIntSelect(genesys_limitSpinner) : getIntSelect(limitSpinner);
            String limitName = genesys_Switch.isChecked() ? getSelectText(genesys_limitListSpinner) : getSelectText(limitListSpinner);
            CardSearchInfo searchInfo = new CardSearchInfo.Builder()
                    .ot(otList)
                    .limitName(limitName)
                    .limitType(limitType)
                    .setcode(setCodeList)
                    .setcode_logic(setcode_isAnd)
                    .category(categoryList)
                    .cardTypes(cardTypeList)
                    .spellTrapTypes(spellTrapTypeList)
                    .attribute(attributeList)
                    .level(levelList)
                    .race(raceList)
                    .monsterTypes(monsterTypeList)
                    .type_logic(isAnd)
                    .except_types(excludeTypeList)
                    .pscale(pendulumScaleList)
                    .linkKey(lineKey)
                    .atk(text(atkText))
                    .def(text(defText))
                    .equal_logic(isEqual)
                    .sum_logic(isSum)
                    .atk_or_def_logic(isOr)
                    .keyword(keyword)
                    .build();

            performSearch(searchInfo, true);
        }
    }

    protected void performSearch(CardSearchInfo searchInfo, boolean record) {
        // 任何普通搜索都将收藏按钮复位为默认状态，
        // 之后点击收藏按钮可重新进入按下状态并显示收藏卡片列表
        resetFavoriteState();
        if (mICardSearcher != null) {
            mICardSearcher.search(searchInfo);
        }
        if (record && !isDefaultSearch(searchInfo)) {
            // 检查是否已经存在相同的搜索条件
            int sameIndex = findSameSearchInfo(searchHistory, searchInfo);
            if (sameIndex >= 0) {
                // 找到相同的搜索条件，将其设为最后一条
                searchHistory.remove(sameIndex);
                searchHistory.add(searchInfo);
                searchIndex = searchHistory.size() - 1;
            } else {
                // 没有相同条件，正常追加
                searchHistory.add(searchInfo);
                searchIndex = searchHistory.size() - 1;
            }
        }
        updateSearchNavButtons();
    }

    /**
     * 将一次由卡片详情触发的外部关键词搜索（点击高亮文字、点击关联卡片按钮）记入搜索历史。
     * 仅更新历史列表与导航按钮状态，不重新执行搜索、不改动搜索面板的筛选条件，
     * 以保持各调用方当前的结果展示不变。记录的条目为仅含关键词的 CardSearchInfo，
     * 因此通过“上一次/下一次”导航可复现为对该关键词的普通搜索。
     *
     * @param keyword 关键词；为空时不记录
     */

    protected void applySearchInfo(CardSearchInfo info) {
        if (info == null) {
            return;
        }
        // 先重置所有条件
        resetAll();
        // 关键词
        String keywordValue = info.getKeyWord() != null ? info.getKeyWord().getValue() : "";
        keyWord.setText(keywordValue, false);
        keyWord.dismissDropDown();
        // 恢复genesys模式开关（根据禁卡表名称判断）
        boolean genesysMode = false;
        if (!TextUtils.isEmpty(info.getLimitName())) {
            genesysMode = mLimitManager.getGenesysLimitNames().contains(info.getLimitName());
        }
        if (genesysMode != genesys_Switch.isChecked()) {
            genesys_Switch.setChecked(genesysMode);
        }
        // 禁限类型与禁卡表
        setSpinnerSelectByValue(limitSpinner, info.getLimitType());
        setSpinnerSelectByText(limitListSpinner, info.getLimitName());
        setSpinnerSelectByValue(genesys_limitSpinner, info.getLimitType());
        setSpinnerSelectByText(genesys_limitListSpinner, info.getLimitName());
        // OT
        if (info.getOt() != null) {
            for (int i = 0; i < otButtons.length; i++) {
                setButtonSelected(otButtons[i], info.getOt().contains(otIds[i].getId()), otIds[i].getId(), otList);
            }
        }
        // 字段
        setcode_isAnd = info.isSetcodeLogic();
        RadioGroup radioGroupSetcode = findViewById(R.id.radio_group_setcode);
        radioGroupSetcode.check(setcode_isAnd ? R.id.rb_and_setcode : R.id.rb_or_setcode);
        if (info.getSetcode() != null) {
            List<CardSet> setnames = mStringManager.getCardSets();
            for (long code : info.getSetcode()) {
                if (setCodeList.contains(code)) {
                    continue;
                }
                if (code == -1L) {
                    setCodeList.add(code);
                    addSetcodeTag(getString(R.string.label_set_No_Setcode), code);
                } else {
                    for (CardSet set : setnames) {
                        if (set.getCode() == code) {
                            setCodeList.add(code);
                            addSetcodeTag(set.getName(), code);
                            break;
                        }
                    }
                }
            }
        }
        // 效果分类
        if (info.getCategory() != null) {
            for (int i = 0; i < categoryButtons.length; i++) {
                setButtonSelected(categoryButtons[i], info.getCategory().contains(categories[i].value()), categories[i].value(), categoryList);
            }
        }
        // 卡片类型（含魔陷图标栏、怪兽区布局联动）
        if (info.getCardTypes() != null) {
            boolean hasMonster = info.getCardTypes().contains(typeIds[0]);
            boolean hasSpell = info.getCardTypes().contains(typeIds[1]);
            boolean hasTrap = info.getCardTypes().contains(typeIds[2]);
            boolean noType = !hasMonster && !hasSpell && !hasTrap;
            setButtonSelected(cardTypeButtons[0], hasMonster, typeIds[0], cardTypeList);
            setButtonSelected(cardTypeButtons[1], hasSpell, typeIds[1], cardTypeList);
            setButtonSelected(cardTypeButtons[2], hasTrap, typeIds[2], cardTypeList);
            layout_monster.setVisibility(hasMonster || noType ? View.VISIBLE : View.GONE);
            ll_icon.setVisibility(hasSpell || hasTrap || noType ? View.VISIBLE : View.GONE);
            iconButtons[0].setVisibility(hasSpell || noType ? View.VISIBLE : View.GONE);
            iconButtons[2].setVisibility(hasSpell || noType ? View.VISIBLE : View.GONE);
            iconButtons[3].setVisibility(hasSpell || noType ? View.VISIBLE : View.GONE);
            iconButtons[5].setVisibility(hasSpell || noType ? View.VISIBLE : View.GONE);
            iconButtons[4].setVisibility(hasTrap || noType ? View.VISIBLE : View.GONE);
        }
        // 魔陷图标
        if (info.getSpellTrapTypes() != null) {
            for (int i = 0; i < iconButtons.length; i++) {
                setButtonSelected(iconButtons[i], info.getSpellTrapTypes().contains(iconIds[i]), iconIds[i], spellTrapTypeList);
            }
        }
        // 属性
        if (info.getAttribute() != null) {
            for (int i = 0; i < attributeButtons.length; i++) {
                setButtonSelected(attributeButtons[i], info.getAttribute().contains(attributeIds[i].getId()), attributeIds[i].getId(), attributeList);
            }
        }
        // 种族
        if (info.getRace() != null) {
            for (int i = 0; i < raceButtons.length; i++) {
                setButtonSelected(raceButtons[i], info.getRace().contains(raceIds[i].value()), raceIds[i].value(), raceList);
            }
        }
        // 怪兽种类
        if (info.getMonsterType() != null) {
            for (int i = 0; i < monsterTypeButtons.length; i++) {
                setButtonSelected(monsterTypeButtons[i], info.getMonsterType().contains(monsterTypeIds[i]), monsterTypeIds[i], monsterTypeList);
            }
        }
        // 怪兽种类逻辑(and/or)
        isAnd = info.isTypeLogic();
        RadioGroup radioGroup = findViewById(R.id.radio_group);
        radioGroup.check(isAnd ? R.id.rb_and : R.id.rb_or);
        // 排除种类
        if (info.getExceptTypes() != null) {
            for (int i = 0; i < exclude_typeButtons.length; i++) {
                setButtonSelected(exclude_typeButtons[i], info.getExceptTypes().contains(monsterTypeIds[i]), monsterTypeIds[i], excludeTypeList, R.drawable.radius_p);
            }
        }
        // 等级
        if (info.getLevel() != null) {
            for (int i = 0; i < levelButtons.length; i++) {
                boolean selected = info.getLevel().contains(i + 1);
                levelButtons[i].setSelected(selected);
                levelButtons[i].setBackground(mContext.getDrawable(selected ? R.drawable.radius : R.drawable.button_radius_black_transparents));
                if (selected) {
                    if (!levelList.contains(i + 1)) {
                        levelList.add(i + 1);
                    }
                } else {
                    levelList.remove(Integer.valueOf(i + 1));
                }
            }
        }
        // 灵摆刻度
        if (info.getPscale() != null) {
            for (int i = 0; i < pendulumScaleButtons.length; i++) {
                boolean selected = info.getPscale().contains(i);
                pendulumScaleButtons[i].setSelected(selected);
                pendulumScaleButtons[i].setBackground(mContext.getDrawable(selected ? R.drawable.radius : R.drawable.button_radius_black_transparents));
                if (selected) {
                    if (!pendulumScaleList.contains(i)) {
                        pendulumScaleList.add(i);
                    }
                } else {
                    pendulumScaleList.remove(Integer.valueOf(i));
                }
            }
        }
        // 连接箭头
        if (info.getLinkKey() > 0) {
            String binary = String.format("%9s", Integer.toBinaryString(info.getLinkKey())).replace(' ', '0');
            for (int i = 0; i < linkButton.length; i++) {
                if (i == 4) {
                    BtnVals[i] = "0";
                    continue;
                }
                boolean selected = binary.charAt(8 - i) == '1';
                BtnVals[i] = selected ? "1" : "0";
                linkButton[i].setBackgroundResource(selected ? enImgs[i] : disImgs[i]);
            }
            lineKey = info.getLinkKey();
        }
        // 攻击力、守备力
        atkText.setText(info.getAtk());
        defText.setText(info.getDef());
        // 攻守相等逻辑
        if (info.isEqualLogic()) {
            btn_equal.setSelected(true);
            btn_equal.setTextColor(YGOUtil.c(R.color.yellow));
            btn_equal.setBackground(mContext.getDrawable(R.drawable.radius));
            isEqual = true;
            if (!text(atkText).isEmpty() && text(defText).isEmpty()) {
                defText.setText(atkText.getText());
            }
            if (text(atkText).isEmpty() && !text(defText).isEmpty()) {
                atkText.setText(defText.getText());
            }
        }
        // 攻守合计、攻或守逻辑（通过checkbox监听器同步状态）
        chk_atkDef_sum.setChecked(info.isSumLogic());
        chk_atkDef_or.setChecked(info.isAtkOrDefLogic());
        // 恢复各筛选栏目的展开/收起状态：有选中项则展开，否则收起
        updateSectionVisibility(gl_ot, iv_hide_ot, !otList.isEmpty());
        updateSectionVisibility(tag_setcode, iv_hide_setCode, !setCodeList.isEmpty());
        updateSectionVisibility(gl_category, iv_hide_category, !categoryList.isEmpty());
        updateSectionVisibility(gl_cardType, iv_hide_cardType, !cardTypeList.isEmpty());
        updateSectionVisibility(gl_icon, iv_hide_spelltrap, !spellTrapTypeList.isEmpty());
        updateSectionVisibility(gl_attr, iv_hide_attr, !attributeList.isEmpty());
        updateSectionVisibility(gl_race, iv_hide_race, !raceList.isEmpty());
        updateSectionVisibility(gl_monsterType, iv_hide_monsterType, !monsterTypeList.isEmpty());
        updateSectionVisibility(gl_exclude_type, iv_hide_exclude_type, !excludeTypeList.isEmpty());
        updateSectionVisibility(gl_level_rank_link, iv_hide_level_rank_link, !levelList.isEmpty());
        updateSectionVisibility(gl_pendulum_scale, iv_hide_pendulum_scale, !pendulumScaleList.isEmpty());
        updateSectionVisibility(ll_linkControl, iv_hide_linkmarker, lineKey > 0);
    }


}

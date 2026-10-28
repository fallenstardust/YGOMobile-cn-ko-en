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
 * 卡牌搜索面板基类（拆分自原 CardSearcher 大类）。
 * <p>
 * 承载全部筛选状态字段、收藏与回调控制、通用界面小工具以及所有重置（reset）逻辑；
 * 文末抽象钩子由继承链中的子类实现，以保持原有调用行为不变。
 */
public abstract class CardSearcherBase {
    protected static final String TAG = "CardSearcher";
    final String[] BtnVals = new String[9];
    protected AutoCompleteTextView keyWord;
    protected CheckBox chk_multi_keyword;

    protected Switch genesys_Switch;
    protected Spinner limitSpinner;
    protected Spinner genesys_limitSpinner;
    protected Spinner limitListSpinner;
    protected Spinner genesys_limitListSpinner;
    protected GridLayout gl_ot;
    protected ImageView iv_hide_ot;
    protected Button[] otButtons;
    protected CardOt[] otIds;
    List<Integer> otList;
    // 字段标签栏
    protected ImageView iv_hide_setCode;
    protected FlexboxLayout tag_setcode;
    List<Long> setCodeList;
    boolean setcode_isAnd;
    // 效果类型按钮
    protected GridLayout gl_category;
    protected ImageView iv_hide_category;
    protected Button[] categoryButtons;
    protected CardCategory[] categories;
    List<Long> categoryList;
    // 卡片类型按钮
    protected GridLayout gl_cardType;
    protected ImageView iv_hide_cardType;
    protected Button[] cardTypeButtons;
    protected long[] typeIds;
    protected List<Long> cardTypeList;
    // 魔陷图标
    protected LinearLayout ll_icon;
    protected GridLayout gl_icon;
    protected ImageView iv_hide_spelltrap;
    protected Button[] iconButtons;
    protected Button[] spellButtons;
    protected Button[] trapButtons;
    protected List<Long> spellTrapTypeList;
    protected long[] iconIds;
    protected long[] spellIds;
    protected long[] trapIds;
    // 属性筛选按钮
    protected Button[] attributeButtons;
    protected GridLayout gl_attr;
    protected ImageView iv_hide_attr;
    protected CardAttribute[] attributeIds;
    protected List<Long> attributeList;
    // 种族筛选按钮
    protected Button[] raceButtons;
    protected GridLayout gl_race;
    protected ImageView iv_hide_race;
    protected CardRace[] raceIds;
    protected List<Long> raceList;
    //怪兽类型按钮
    protected GridLayout gl_monsterType;
    protected ImageView iv_hide_monsterType;
    protected Button[] monsterTypeButtons;
    protected long[] monsterTypeIds;
    protected Drawable[] TypeIcon;
    protected List<Long> monsterTypeList;
    protected boolean isAnd;
    // 排除怪兽类型按钮
    protected GridLayout gl_exclude_type;
    protected ImageView iv_hide_exclude_type;
    protected Button[] exclude_typeButtons;
    protected List<Long> excludeTypeList;
    // 等级\阶级\连接数
    protected GridLayout gl_level_rank_link;
    protected ImageView iv_hide_level_rank_link;
    protected ImageButton[] levelButtons;
    protected List<Integer> levelList;
    // 灵摆刻度数
    protected GridLayout gl_pendulum_scale;
    protected ImageView iv_hide_pendulum_scale;
    protected ImageButton[] pendulumScaleButtons;
    protected List<Integer> pendulumScaleList;
    // 连接箭头
    protected LinearLayout ll_linkControl;
    protected ImageView iv_hide_linkmarker;
    protected Button[] linkButton;
    protected int[] disImgs;
    protected int[] enImgs;
    protected int lineKey;

    protected EditText atkText;
    protected EditText defText;
    protected TextInputLayout til_atk;
    protected LinearLayout ll_equal_def;
    protected CheckBox chk_atkDef_sum;
    protected CheckBox chk_atkDef_or;
    protected Boolean isOr;
    protected Boolean isSum;
    protected Button btn_equal;
    protected Boolean isEqual;
    protected boolean isUpdating = false; // 防止两个文本框相互触发更新造成无限循环

    protected ImageButton searchButton;
    protected ImageButton resetButton;
    protected ImageButton btnLastSearch;
    protected ImageButton btnNextSearch;
    protected final List<CardSearchInfo> searchHistory = new ArrayList<>();
    protected int searchIndex = -1;
    protected View view;
    protected View layout_monster;
    protected ICardSearcher mICardSearcher;// ICardSearcher 即为CardLoader的接口;
    protected Context mContext;
    protected Button myFavButton;
    protected StringManager mStringManager;
    protected LimitManager mLimitManager;
    protected AppsSettings mSettings;

    protected CallBack mCallBack;
    protected boolean mShowFavorite;
    // 关键词历史记录下拉适配器（支持每条记录右侧删除图标）
    protected KeywordHistoryAdapter keywordHistoryAdapter;


    public void setCallBack(CallBack callBack) {
        mCallBack = callBack;
    }

    public void showFavorites(boolean showList) {
        mShowFavorite = true;
        myFavButton.setSelected(true);
        myFavButton.setBackground(mContext.getDrawable(R.drawable.radius));
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
        myFavButton.setBackground(mContext.getDrawable(R.drawable.selected_dark));
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

    /**
     * 仅复位收藏按钮的状态与外观（回到默认状态），不触发任何回调与搜索
     */
    protected void resetFavoriteState() {
        mShowFavorite = false;
        myFavButton.setSelected(false);
        myFavButton.setBackground(mContext.getDrawable(R.drawable.selected_dark));
    }

    public void initItems() {
        initCategoryButtons();
        initTypeButtons();
        initAttributeButtons();
        initRaceButtons();
        initIconButtons();
        initMonsterTypeButtons();
        initExcludeTypeButtons();
        initLevelButtons();
        initPendulumScaleButtons();
        initLinkMarkerButtons();
        initOtButtons();
        initLimitSpinners(limitSpinner);//初始化常规禁限选项：禁止、限制、准限制
        initLimitGenesysSpinners(genesys_limitSpinner);//初始化Genesys禁限选项：Genesys、禁止
        initLimitListSpinners(limitListSpinner);
        initGenesysLimitListSpinners(genesys_limitListSpinner);
        initSetnameSearchFeature();
        initAttackDefenseSync();
    }

    protected <T extends View> T findViewById(int id) {
        T v = view.findViewById(id);
        if (v instanceof Spinner) {
            ((Spinner) v).setPopupBackgroundResource(R.color.colorNavy);
        }
        return v;
    }

    public boolean isShowFavorite() {
        return mShowFavorite;
    }

    protected String getString(int id) {
        return mContext.getString(id);
    }


    protected void reset(Spinner spinner) {
        if (spinner.getCount() > 0) {
            spinner.setSelection(0);
        }
    }

    protected int getIntSelect(Spinner spinner) {
        return (int) getSelect(spinner);
    }

    protected long getSelect(Spinner spinner) {
        return SimpleSpinnerAdapter.getSelect(spinner);
    }

    protected String getSelectText(Spinner spinner) {
        return SimpleSpinnerAdapter.getSelectText(spinner);
    }

    protected String text(EditText editText) {
        CharSequence charSequence = editText.getText();
        if (charSequence == null) {
            return null;
        }
        return charSequence.toString();
    }

    protected void updateSectionVisibility(View section, ImageView arrow, boolean expand) {
        section.setVisibility(expand ? View.VISIBLE : View.GONE);
        arrow.setImageResource(expand ? R.drawable.baseline_keyboard_arrow_up_24 : R.drawable.baseline_keyboard_arrow_down_24);
    }

    protected <T> void setButtonSelected(Button button, boolean selected, T id, List<T> list) {
        setButtonSelected(button, selected, id, list, R.drawable.radius);
    }

    protected <T> void setButtonSelected(Button button, boolean selected, T id, List<T> list, int selectedBg) {
        button.setSelected(selected);
        button.setBackground(mContext.getDrawable(selected ? selectedBg : R.drawable.button_radius_black_transparents));
        button.setTextColor(YGOUtil.c(selected ? R.color.yellow : R.color.gray));
        if (selected) {
            if (!list.contains(id)) {
                list.add(id);
            }
        } else {
            list.remove(id);
        }
    }

    protected void setSpinnerSelectByValue(Spinner spinner, long value) {
        if (spinner.getAdapter() != null) {
            for (int i = 0; i < spinner.getCount(); i++) {
                Object item = spinner.getItemAtPosition(i);
                if (item instanceof SimpleSpinnerItem && ((SimpleSpinnerItem) item).value == value) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }
    }

    protected void setSpinnerSelectByText(Spinner spinner, String text) {
        if (TextUtils.isEmpty(text)) {
            if (spinner.getCount() > 0) {
                spinner.setSelection(0);
            }
            return;
        }
        if (spinner.getAdapter() != null) {
            for (int i = 0; i < spinner.getCount(); i++) {
                Object item = spinner.getItemAtPosition(i);
                if (item instanceof SimpleSpinnerItem && TextUtils.equals(((SimpleSpinnerItem) item).text, text)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }
    }

    protected void resetAll() {
        if (mICardSearcher != null) {
            mICardSearcher.onReset();
        }
        keyWord.setText(null);
        reset(limitSpinner.getVisibility() == View.VISIBLE ? limitSpinner : genesys_limitSpinner);
        resetOt();
        resetSetcode();
        resetCategory();
        resetCardType();
        resetMonster();
    }

    protected void resetOt() {
        gl_ot.setVisibility(View.GONE);
        iv_hide_ot.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < otButtons.length; i++) {
            otButtons[i].setSelected(false);
            otButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            otButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            otList.remove(Integer.valueOf(otIds[i].getId()));//需要int转Integer再移除以免超过索引
        }
    }

    protected void resetSetcode() {
        tag_setcode.setVisibility(View.GONE);
        iv_hide_setCode.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
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

    protected void resetCategory() {
        gl_category.setVisibility(View.GONE);
        iv_hide_category.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < categoryButtons.length; i++) {
            categoryButtons[i].setSelected(false);
            categoryButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            categoryButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            categoryList.remove(categories[i].value());
        }
    }

    protected void resetCardType() {
        gl_cardType.setVisibility(View.GONE);
        iv_hide_cardType.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < cardTypeButtons.length; i++) {
            cardTypeButtons[i].setSelected(false);
            cardTypeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            cardTypeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            cardTypeList.remove(typeIds[i]);
        }
        resetIcons();
        if (ll_icon.getVisibility() == View.GONE) ll_icon.setVisibility(View.VISIBLE);
        if (layout_monster.getVisibility() == View.GONE) layout_monster.setVisibility(View.VISIBLE);
    }

    protected void resetMonster() {
        // 重置怪兽卡按钮为未选中
        cardTypeButtons[0].setSelected(false);
        cardTypeButtons[0].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        cardTypeButtons[0].setTextColor(YGOUtil.c(R.color.gray));
        cardTypeList.remove(CardType.Monster.getId()); // 从monstertypeList中移除怪兽相关的ID

        resetAttribute();// 重置属性按钮为未选中
        resetRace();// 重置种族按钮为未选中
        resetMonsterType();// 重置怪兽子种类按钮为未选中
        resetExcludeType();// 重置排除种类按钮为未选中
        resetLevel();// 重置等级按钮为未选中
        resetPScale();// 重置灵摆刻度按钮为未选中
        resetLinkMarker(); // 重置链接标记按钮为未选中
        resetEqualButton();//重置相等模式按钮
        resetSumCheckbox();// 重置攻守合计checkbox为未选中
        resetOrCheckbox();// 重置攻或守满足checkbox为未选中
        atkText.setText(null);// 清除输入的攻击力
        defText.setText(null);// 清除输入的守备力

        lineKey = 0;// 清除灵摆键值
    }

    protected void resetAttribute() {
        gl_attr.setVisibility(View.GONE);
        iv_hide_attr.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < attributeButtons.length; i++) {
            attributeButtons[i].setSelected(false);
            attributeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            attributeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            attributeList.remove(attributeIds[i].getId());
        }
    }

    protected void resetRace() {
        gl_race.setVisibility(View.GONE);
        iv_hide_race.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < raceButtons.length; i++) {
            raceButtons[i].setSelected(false);
            raceButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            raceButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            raceList.remove(raceIds[i].value());
        }
    }

    protected void resetMonsterType() {
        gl_monsterType.setVisibility(View.GONE);
        iv_hide_monsterType.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < monsterTypeButtons.length; i++) {
            monsterTypeButtons[i].setSelected(false);
            monsterTypeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            monsterTypeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            monsterTypeList.remove(monsterTypeIds[i]);
        }
    }

    protected void resetExcludeType() {
        gl_exclude_type.setVisibility(View.GONE);
        iv_hide_exclude_type.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < exclude_typeButtons.length; i++) {
            exclude_typeButtons[i].setSelected(false);
            exclude_typeButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            exclude_typeButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            excludeTypeList.remove(monsterTypeIds[i]);
        }
    }

    protected void resetLevel() {
        gl_level_rank_link.setVisibility(View.GONE);
        iv_hide_level_rank_link.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < levelButtons.length; i++) {
            levelButtons[i].setSelected(false);
            levelButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            Integer levelValue = i + 1;
            levelList.remove(levelValue);
        }
    }

    protected void resetPScale() {
        gl_pendulum_scale.setVisibility(View.GONE);
        iv_hide_pendulum_scale.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < pendulumScaleButtons.length; i++) {
            pendulumScaleButtons[i].setSelected(false);
            pendulumScaleButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            Integer pendulumScaleValue = i;
            pendulumScaleList.remove(pendulumScaleValue);
        }
    }

    protected void resetLinkMarker() {
        ll_linkControl.setVisibility(View.GONE);
        iv_hide_linkmarker.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        for (int i = 0; i < linkButton.length; i++) {
            linkButton[i].setSelected(false);
            if (linkButton[i] != linkButton[4]) {// 跳过第5个按钮
                linkButton[i].setBackground(mContext.getDrawable(disImgs[i]));
            }

        }
        Arrays.fill(BtnVals, "0");
        lineKey = 0;
    }

    protected void resetSpell() {
        //解除魔法卡种类选中状态
        cardTypeButtons[1].setSelected(false);
        cardTypeButtons[1].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        cardTypeButtons[1].setTextColor(YGOUtil.c(R.color.gray));
        cardTypeList.remove(CardType.Spell.getId());

        spellButtons = new Button[]{
                view.findViewById(R.id.btn_icon_quickPlay),// 速攻0
                view.findViewById(R.id.btn_icon_equip),// 装备2
                view.findViewById(R.id.btn_icon_field),// 场地3
                view.findViewById(R.id.btn_icon_ritual),// 仪式5
        };
        long[] spellIds = new long[]{
                CardType.QuickPlay.getId(),
                CardType.Equip.getId(),
                CardType.Field.getId(),
                CardType.Ritual.getId()
        };
        //解除魔法相关图标按钮的选中状态
        for (int i = 0; i < spellButtons.length; i++) {
            spellButtons[i].setSelected(false);
            spellButtons[i].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
            spellButtons[i].setTextColor(YGOUtil.c(R.color.gray));
            spellTrapTypeList.remove(spellIds[i]);
        }

    }

    protected void resetTrap() {
        //解除卡片分类的陷阱卡选中状态
        cardTypeButtons[2].setSelected(false);
        cardTypeButtons[2].setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        cardTypeButtons[2].setTextColor(YGOUtil.c(R.color.gray));
        cardTypeList.remove(CardType.Trap.getId());

        //解除陷阱相关图标按钮的选中状态
        Button trapButton = view.findViewById(R.id.btn_icon_counter);// 反击4
        trapButton.setSelected(false);
        trapButton.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
        trapButton.setTextColor(YGOUtil.c(R.color.gray));
        spellTrapTypeList.remove(CardType.Counter.getId());
    }

    protected void resetIcons() {
        gl_icon.setVisibility(View.GONE);
        iv_hide_spelltrap.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 重置为折叠状态
        resetSpell();// 解除魔法独有类型的选中状态
        resetTrap();// 解除陷阱独有类型的选中状态（也就反击陷阱）

        Button[] conti_normal_icons = new Button[]{// 单独处理魔法和陷阱都有的种类
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

    protected void resetEqualButton() {
        btn_equal.setSelected(false);
        btn_equal.setTextColor(YGOUtil.c(R.color.default_bmb_shadow_color));
        btn_equal.setBackground(mContext.getDrawable(R.drawable.selected_dark));
        isEqual = false;
    }

    protected void resetSumCheckbox() {
        chk_atkDef_sum.setChecked(false);
        ll_equal_def.setVisibility(View.VISIBLE);
        isSum = false;
    }

    protected void resetOrCheckbox() {
        chk_atkDef_or.setChecked(false);
        ll_equal_def.setVisibility(View.VISIBLE);
        isOr = false;
    }

    public interface CallBack {
        void setLimit(LimitList limit, String caller);

        void onSearchStart();

        void onSearchResult(List<Card> Cards, boolean isHide);
    }

    // ==== abstract hooks: implemented by subclasses, declared here so base-class code can invoke them ====
    protected abstract void initCategoryButtons();
    protected abstract void initTypeButtons();
    protected abstract void initAttributeButtons();
    protected abstract void initRaceButtons();
    protected abstract void initIconButtons();
    protected abstract void initMonsterTypeButtons();
    protected abstract void initExcludeTypeButtons();
    protected abstract void initLevelButtons();
    protected abstract void initPendulumScaleButtons();
    protected abstract void initLinkMarkerButtons();
    protected abstract void initOtButtons();
    protected abstract void initLimitSpinners(Spinner spinner);
    protected abstract void initLimitGenesysSpinners(Spinner spinner);
    protected abstract void initLimitListSpinners(Spinner spinner);
    protected abstract void initGenesysLimitListSpinners(Spinner spinner);
    protected abstract void initSetnameSearchFeature();
    protected abstract void initAttackDefenseSync();
    protected abstract void search();
    protected abstract void applySearchInfo(CardSearchInfo info);

}

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
 * 卡牌搜索面板——禁限卡表下拉、字段（archetype）标签与搜索历史导航集群（拆分自原 CardSearcher）。
 */
public abstract class CardSearcherFilterHistory extends CardSearcherBase {
    protected void initLimitSpinners(Spinner spinner) {
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
        adapter.setTextSize(14);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    protected void initLimitGenesysSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();

        // 添加默认选项
        items.add(new SimpleSpinnerItem(LimitType.None.getId(), getString(R.string.label_limit)));
        items.add(new SimpleSpinnerItem(LimitType.All.getId(), getString(R.string.all)));

        // GeneSys模式下只添加GeneSys和Forbidden选项
        items.add(new SimpleSpinnerItem(LimitType.GeneSys.getId(), mStringManager.getLimitString(LimitType.GeneSys.getId())));
        items.add(new SimpleSpinnerItem(LimitType.Forbidden.getId(), mStringManager.getLimitString(LimitType.Forbidden.getId())));

        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(mContext);
        adapter.setColor(Color.WHITE);
        adapter.setTextSize(14);
        adapter.set(items);
        spinner.setAdapter(adapter);
    }

    protected void initLimitListSpinners(Spinner spinner) {
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
        for (int i = 0; i < limits.size(); i++) {
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
        adapter.setTextSize(12);
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

    protected void initGenesysLimitListSpinners(Spinner spinner) {
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
        for (int i = 0; i < genesys_limit_names.size(); i++) {
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
        adapter.setTextSize(11);
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

    protected void refreshLimitListSpinnerItems(Spinner spinner) {
        // 首先清除所有现有的item
        if (spinner.getAdapter() != null && spinner.getAdapter() instanceof SimpleSpinnerAdapter) {
            //清空选项
            ((SimpleSpinnerAdapter) spinner.getAdapter()).clear();
            //重新加载禁卡表，获取可能存在的变动后情况
            mLimitManager.load();
        }
        initLimitListSpinners(spinner);
    }

    protected void refreshGenesysLimitListSpinnerItems(Spinner spinner) {
        // 首先清除所有现有的item
        if (spinner.getAdapter() != null && spinner.getAdapter() instanceof SimpleSpinnerAdapter) {
            //清空选项
            ((SimpleSpinnerAdapter) spinner.getAdapter()).clear();
            //重新加载禁卡表，获取可能存在的变动后情况
            mLimitManager.load();
        }
        initGenesysLimitListSpinners(spinner);
    }


    protected void initSetnameSearchFeature() {
        // 以布局点击事件作为初始化
        tag_setcode.setVisibility(View.GONE);
        iv_hide_setCode.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        view.findViewById(R.id.ll_setcode).setOnClickListener(v -> {
            if (tag_setcode.getVisibility() == View.VISIBLE) {
                resetSetcode();
            } else {
                tag_setcode.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                tag_setcode.setVisibility(View.VISIBLE);
                iv_hide_setCode.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
                showSetnameSearchableDialog();
            }
        });
        tag_setcode.setOnClickListener(v -> {
            showSetnameSearchableDialog();
        });
        RadioGroup radioGroupSetcode = findViewById(R.id.radio_group_setcode);// 切换怪兽类型内部逻辑
        radioGroupSetcode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_and_setcode) {
                setcode_isAnd = true;
            } else if (checkedId == R.id.rb_or_setcode) {
                setcode_isAnd = false;
            }
        });
        // 初始化时确保提示文本正确显示
        updateSetcodeHintVisibility();
    }

    protected void showSetnameSearchableDialog() {
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
    protected void addSetcodeTag(String setName, long setCode) {
        // 创建标签容器布局
        LinearLayout tagLayout = new LinearLayout(mContext);
        tagLayout.setOrientation(LinearLayout.HORIZONTAL);
        tagLayout.setBackgroundResource(R.drawable.selected); // 使用适当的背景资源
        tagLayout.setPadding(8, 4, 8, 4);
        // 创建布局参数并设置边距
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(2, 2, 2, 2);
        tagLayout.setLayoutParams(params); // 应用布局参数

        // 创建标签文本
        TextView tagView = new TextView(mContext);
        tagView.setText(setName);
        tagView.setGravity(Gravity.CENTER);
        tagView.setTextColor(YGOUtil.c(R.color.gold));
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
    protected void updateSetcodeHintVisibility() {
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


    public void recordHistoryKeyword(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return;
        }
        CardSearchInfo info = new CardSearchInfo.Builder()
                .keyword(keyword)
                .cardTypes(new ArrayList<>())
                .build();
        // 与 performSearch 保持一致的去重：若已存在相同条件则将其移到末尾，避免重复条目
        int sameIndex = findSameSearchInfo(searchHistory, info);
        if (sameIndex >= 0) {
            searchHistory.remove(sameIndex);
        }
        searchHistory.add(info);
        searchIndex = searchHistory.size() - 1;
        updateSearchNavButtons();
    }

    protected boolean isDefaultSearch(CardSearchInfo info) {
        if (info.getKeyWord() != null && !TextUtils.isEmpty(info.getKeyWord().getValue())) {
            return false;
        }
        if (info.getOt() != null && !info.getOt().isEmpty()) return false;
        if (info.getCategory() != null && !info.getCategory().isEmpty()) return false;
        if (info.getCardTypes() != null && !info.getCardTypes().isEmpty()) return false;
        if (info.getSpellTrapTypes() != null && !info.getSpellTrapTypes().isEmpty()) return false;
        if (info.getAttribute() != null && !info.getAttribute().isEmpty()) return false;
        if (info.getLevel() != null && !info.getLevel().isEmpty()) return false;
        if (info.getRace() != null && !info.getRace().isEmpty()) return false;
        if (info.getMonsterType() != null && !info.getMonsterType().isEmpty()) return false;
        if (info.getExceptTypes() != null && !info.getExceptTypes().isEmpty()) return false;
        if (info.getSetcode() != null && !info.getSetcode().isEmpty()) return false;
        if (info.getPscale() != null && !info.getPscale().isEmpty()) return false;
        if (info.getLinkKey() > 0) return false;
        if (!TextUtils.isEmpty(info.getAtk())) return false;
        if (!TextUtils.isEmpty(info.getDef())) return false;
        if (info.isTypeLogic()) return false;
        if (info.isSetcodeLogic()) return false;
        if (info.isEqualLogic()) return false;
        if (info.isSumLogic()) return false;
        if (info.isAtkOrDefLogic()) return false;
        return true;
    }

    protected void onLastSearch() {
        if (searchIndex > 0) {
            searchIndex--;
            CardSearchInfo searchInfo = searchHistory.get(searchIndex);
            applySearchInfo(searchInfo);
            performSearch(searchInfo, false);
        }
    }

    protected void onNextSearch() {
        if (searchIndex >= 0 && searchIndex < searchHistory.size() - 1) {
            searchIndex++;
            CardSearchInfo searchInfo = searchHistory.get(searchIndex);
            applySearchInfo(searchInfo);
            performSearch(searchInfo, false);
        }
    }

    protected void updateSearchNavButtons() {
        btnLastSearch.setVisibility(searchIndex > 0 ? View.VISIBLE : View.INVISIBLE);
        btnNextSearch.setVisibility(searchIndex >= 0 && searchIndex < searchHistory.size() - 1 ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * 查找搜索历史中与给定 searchInfo 完全相同的条目索引
     * @param searchInfo 新的搜索条件
     * @return 如果找到返回索引，否则返回 -1
     */
    protected int findSameSearchInfo(List<CardSearchInfo> history, CardSearchInfo searchInfo) {
        if (searchInfo == null || history == null || history.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < history.size(); i++) {
            CardSearchInfo existing = history.get(i);
            if (isSameSearchInfo(existing, searchInfo)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断两个 CardSearchInfo 是否完全相同（所有字段都相等）
     */
    protected boolean isSameSearchInfo(CardSearchInfo info1, CardSearchInfo info2) {
        if (info1 == null || info2 == null) {
            return false;
        }
        if (info1 == info2) {
            return true; // 同一个对象引用
        }

        // 比较关键词
        String keyword1 = info1.getKeyWord() != null ? info1.getKeyWord().getValue() : "";
        String keyword2 = info2.getKeyWord() != null ? info2.getKeyWord().getValue() : "";
        if (!TextUtils.equals(keyword1, keyword2)) {
            return false;
        }

        // 比较 OT
        if (!listEquals(info1.getOt(), info2.getOt())) {
            return false;
        }

        // 比较类别
        if (!listEquals(info1.getCategory(), info2.getCategory())) {
            return false;
        }

        // 比较卡片类型
        if (!listEquals(info1.getCardTypes(), info2.getCardTypes())) {
            return false;
        }

        // 比较魔陷类型
        if (!listEquals(info1.getSpellTrapTypes(), info2.getSpellTrapTypes())) {
            return false;
        }

        // 比较属性
        if (!listEquals(info1.getAttribute(), info2.getAttribute())) {
            return false;
        }

        // 比较等级
        if (!listEquals(info1.getLevel(), info2.getLevel())) {
            return false;
        }

        // 比较种族
        if (!listEquals(info1.getRace(), info2.getRace())) {
            return false;
        }

        // 比较怪兽种类
        if (!listEquals(info1.getMonsterType(), info2.getMonsterType())) {
            return false;
        }

        // 比较排除类型
        if (!listEquals(info1.getExceptTypes(), info2.getExceptTypes())) {
            return false;
        }

        // 比较字段
        if (!listEquals(info1.getSetcode(), info2.getSetcode())) {
            return false;
        }

        // 比较灵摆刻度
        if (!listEquals(info1.getPscale(), info2.getPscale())) {
            return false;
        }

        // 比较攻击力、守备力
        if (!TextUtils.equals(info1.getAtk(), info2.getAtk())) {
            return false;
        }
        if (!TextUtils.equals(info1.getDef(), info2.getDef())) {
            return false;
        }

        // 比较链接值
        if (info1.getLinkKey() != info2.getLinkKey()) {
            return false;
        }

        // 比较禁卡表
        if (info1.getLimitType() != info2.getLimitType()) {
            return false;
        }
        if (!TextUtils.equals(info1.getLimitName(), info2.getLimitName())) {
            return false;
        }

        // 比较各种逻辑开关
        if (info1.isTypeLogic() != info2.isTypeLogic()) {
            return false;
        }
        if (info1.isSetcodeLogic() != info2.isSetcodeLogic()) {
            return false;
        }
        if (info1.isEqualLogic() != info2.isEqualLogic()) {
            return false;
        }
        if (info1.isSumLogic() != info2.isSumLogic()) {
            return false;
        }
        if (info1.isAtkOrDefLogic() != info2.isAtkOrDefLogic()) {
            return false;
        }

        return true;
    }

    protected boolean listEquals(List<?> l1, List<?> l2) {
        if (l1 == null && l2 == null) {
            return true;
        }
        if (l1 == null || l2 == null) {
            return false;
        }
        if (l1.size() != l2.size()) {
            return false;
        }
        return l1.equals(l2);
    }

    /**
     * 刷新历史数据：重建 adapter 并重新设置（空输入=显示全部历史）
     */
    protected boolean refreshKeywordHistory() {
        List<String> history = SharedPreferenceUtil.getKeywordHistory();
        keywordHistoryAdapter = new KeywordHistoryAdapter(mContext, history, this::removeKeywordHistory);
        keyWord.setAdapter(keywordHistoryAdapter);
        return !history.isEmpty();
    }

    /**
     * 删除指定的关键词历史记录：持久化移除后，就地更新下拉列表数据，
     * 保持下拉展开状态以便连续删除；若已无记录则收起下拉。
     */
    protected void removeKeywordHistory(String keyword) {
        SharedPreferenceUtil.removeKeywordHistory(keyword);
        if (keywordHistoryAdapter != null) {
            keywordHistoryAdapter.remove(keyword);
        }
        if (keywordHistoryAdapter == null || keywordHistoryAdapter.getCount() == 0) {
            keyWord.dismissDropDown();
        }
    }

    protected void showKeywordDropdown() {
        if (refreshKeywordHistory()) {
            keyWord.showDropDown();
        }
    }



    // cross-layer hook: implemented by downstream CardSearcher leaf
    protected abstract void performSearch(CardSearchInfo searchInfo, boolean record);
}

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
 * 卡牌搜索面板——专属/效果类型/卡片类型/属性/种族/怪兽种类按钮筛选集群（拆分自原 CardSearcher）。
 */
public abstract class CardSearcherOptionButtons extends CardSearcherFilterHistory {
    protected void initOtButtons() {
        Drawable[] otIcon = new Drawable[]{
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "ot_ocg.png", 0, 0)),// OCG
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "ot_tcg.png", 0, 0)),// TCG
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "ot_custom.png", 0, 0)),// 自定义卡片
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "ot_sc.png", 0, 0)),// 简中
                new BitmapDrawable(mContext.getResources(), BitmapUtil.getBitmapFormAssets(mContext, ASSET_ATTR_RACE + "ot_ocgtcg.png", 0, 0)),// 非独有卡
        };
        otButtons = new Button[]{
                view.findViewById(R.id.btn_ot_OCG),// OCG
                view.findViewById(R.id.btn_ot_TCG),// TCG
                view.findViewById(R.id.btn_ot_CUSTOM),// 自定义卡片
                view.findViewById(R.id.btn_ot_SC),// 简中
                view.findViewById(R.id.btn_ot_OCGTCG),// 非独有卡

        };
        for (int i = 0; i < otButtons.length; i++) {
            //设置按钮样式
            Button button = otButtons[i];
            button.setCompoundDrawablePadding(4); // 图标和文字间距
            // 设置图标
            button.setCompoundDrawablesWithIntrinsicBounds(null, otIcon[i], null, null);

            // 定义说明文字(从strings.conf提取以便随着语言切换而变化)
            button.setText(mStringManager.getOtString(otIds[i].getId(), false));
            int index = i;
            button.setOnClickListener(v -> {
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                    otList.remove(Integer.valueOf(otIds[index].getId()));
                } else {
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                    if (!otList.contains(otIds[index].getId())) {
                        otList.add(otIds[index].getId());
                    }
                }
            });
        }
        gl_ot.setVisibility(View.GONE);
        iv_hide_ot.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 初始化时默认为折叠状态
        view.findViewById(R.id.ll_ot).setOnClickListener(v -> {
            if (gl_ot.getVisibility() == View.VISIBLE) {
                resetOt();
            } else {
                gl_ot.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_ot.setVisibility(View.VISIBLE);
                iv_hide_ot.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }


    protected void initCategoryButtons() {
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
            button.setCompoundDrawablesWithIntrinsicBounds(categoryIcon[i], null, null, null);

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
        gl_category.setVisibility(View.GONE);
        iv_hide_category.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 初始化时默认为折叠状态
        view.findViewById(R.id.ll_category).setOnClickListener(v -> {
            if (gl_category.getVisibility() == View.VISIBLE) {
                resetCategory();
            } else {
                gl_category.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_category.setVisibility(View.VISIBLE);
                iv_hide_category.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }

    protected void initTypeButtons() {
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
            button.setCompoundDrawablesWithIntrinsicBounds(cardTypeIcon[index], null, null, null);

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
                    if (!cardTypeList.contains(typeIds[0])) {
                        cardTypeList.add(typeIds[0]);
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
                    cardTypeList.remove(typeIds[1]);
                    cardTypeList.remove(typeIds[2]);
                    spellTrapTypeList.clear();
                }
                if (cardTypeButtons[1].isSelected()) {// 魔法卡
                    iconButtons[0].setVisibility(View.VISIBLE);// 速攻0
                    iconButtons[2].setVisibility(View.VISIBLE);// 装备2
                    iconButtons[3].setVisibility(View.VISIBLE);// 场地3
                    iconButtons[5].setVisibility(View.VISIBLE);// 仪式5

                    if (!cardTypeList.contains(typeIds[1])) {
                        cardTypeList.add(typeIds[1]);
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
                    if (!cardTypeList.contains(typeIds[2])) {
                        cardTypeList.add(typeIds[2]);
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
                Log.i("CardSearcher", "[卡片种类]:" + cardTypeList);
            });
        }
        gl_cardType.setVisibility(View.GONE);
        iv_hide_cardType.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 默认折叠状态
        view.findViewById(R.id.ll_cardType).setOnClickListener(v -> {
            if (gl_cardType.getVisibility() == View.VISIBLE) {
                resetCardType();
            } else {
                gl_cardType.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_cardType.setVisibility(View.VISIBLE);
                iv_hide_cardType.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }


    protected void initAttributeButtons() {
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
        gl_attr.setVisibility(View.GONE);
        iv_hide_attr.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        view.findViewById(R.id.ll_attr).setOnClickListener(v -> {
            if (gl_attr.getVisibility() == View.VISIBLE) {
                resetAttribute();// 点击解除所有选择的属性
            } else {
                gl_attr.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_attr.setVisibility(View.VISIBLE);
                iv_hide_attr.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }

    protected void initRaceButtons() {
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
            button.setCompoundDrawablesWithIntrinsicBounds(raceIcons[index], null, null, null);

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
        gl_race.setVisibility(View.GONE);
        iv_hide_race.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 初始化时默认为折叠状态
        view.findViewById(R.id.ll_race).setOnClickListener(v -> {
            if (gl_race.getVisibility() == View.VISIBLE) {
                resetRace();
            } else {
                gl_race.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_race.setVisibility(View.VISIBLE);
                iv_hide_race.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }

    protected void initMonsterTypeButtons() {
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
            button.setCompoundDrawablesWithIntrinsicBounds(TypeIcon[index], null, null, null);
            button.setOnClickListener(v -> {
                if (monsterTypeList == null) {
                    monsterTypeList = new ArrayList<>();
                }
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                    monsterTypeList.remove(monsterTypeIds[index]);
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                    if (!monsterTypeList.contains(monsterTypeIds[index]))
                        monsterTypeList.add(monsterTypeIds[index]);
                }
                Log.w("CardSearcher", "[怪兽 种类]:" + monsterTypeList);
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
        gl_monsterType.setVisibility(View.GONE);
        iv_hide_monsterType.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 初始化时默认为折叠状态
        view.findViewById(R.id.ll_monsterType).setOnClickListener(v -> {
            if (gl_monsterType.getVisibility() == View.VISIBLE) {
                resetMonsterType();
            } else {
                gl_monsterType.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_monsterType.setVisibility(View.VISIBLE);
                iv_hide_monsterType.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }


}

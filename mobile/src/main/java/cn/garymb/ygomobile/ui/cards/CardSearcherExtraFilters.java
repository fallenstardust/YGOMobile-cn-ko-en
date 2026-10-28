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
 * 卡牌搜索面板——魔陷图标、排除种类、等级/灵摆刻度/连接箭头与攻守同步筛选集群（拆分自原 CardSearcher）。
 */
public abstract class CardSearcherExtraFilters extends CardSearcherOptionButtons {
    protected void initIconButtons() {
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

            button.setOnClickListener(v -> {
                if (spellTrapTypeList == null) {
                    spellTrapTypeList = new ArrayList<>();
                }

                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                    spellTrapTypeList.remove(iconIds[index]);
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                    if (!spellTrapTypeList.contains(iconIds[index])) {
                        spellTrapTypeList.add(iconIds[index]);
                    }
                }
                Log.d("CardSearcher", "[魔陷图标]包含种类:" + spellTrapTypeList);
            });
        }
        gl_icon.setVisibility(View.GONE);
        iv_hide_spelltrap.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 默认为折叠状态
        view.findViewById(R.id.ll_icon).setOnClickListener(v -> {
            if (gl_icon.getVisibility() == View.VISIBLE) {
                resetIcons();
            } else {
                gl_icon.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_icon.setVisibility(View.VISIBLE);
                iv_hide_spelltrap.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }


    protected void initExcludeTypeButtons() {
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
            button.setCompoundDrawablesWithIntrinsicBounds(TypeIcon[index], null, null, null);
            button.setOnClickListener(v -> {
                if (excludeTypeList == null) {
                    excludeTypeList = new ArrayList<>();
                }
                if (button.isSelected()) {
                    button.setSelected(false);
                    button.setBackground(mContext.getDrawable(R.drawable.button_radius_black_transparents));
                    button.setTextColor(YGOUtil.c(R.color.gray));
                    excludeTypeList.remove(monsterTypeIds[index]);
                } else {//未选中时的逻辑
                    button.setSelected(true);
                    button.setBackground(mContext.getDrawable(R.drawable.radius_p));
                    button.setTextColor(YGOUtil.c(R.color.yellow));
                    if (!excludeTypeList.contains(monsterTypeIds[index]))
                        excludeTypeList.add(monsterTypeIds[index]);
                }
                Log.w("CardSearcher", "[排除种类]:" + excludeTypeList);
            });
        }
        gl_exclude_type.setVisibility(View.GONE);
        iv_hide_exclude_type.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 初始化时默认为折叠状态
        view.findViewById(R.id.ll_excludeType).setOnClickListener(v -> {
            if (gl_exclude_type.getVisibility() == View.VISIBLE) {
                resetExcludeType();// 解除所有选中的排除怪兽子种类
            } else {
                gl_exclude_type.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_exclude_type.setVisibility(View.VISIBLE);
                iv_hide_exclude_type.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }

    protected void initLevelButtons() {
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
        gl_level_rank_link.setVisibility(View.GONE);
        iv_hide_level_rank_link.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 初始化时默认为折叠状态
        view.findViewById(R.id.ll_level).setOnClickListener(v -> {
            if (gl_level_rank_link.getVisibility() == View.VISIBLE) {
                resetLevel();
            } else {
                gl_level_rank_link.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_level_rank_link.setVisibility(View.VISIBLE);
                iv_hide_level_rank_link.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }

    protected void initPendulumScaleButtons() {
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
        gl_pendulum_scale.setVisibility(View.GONE);
        iv_hide_pendulum_scale.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);// 初始化时默认为折叠状态
        view.findViewById(R.id.ll_pScale).setOnClickListener(v -> {
            if (gl_pendulum_scale.getVisibility() == View.VISIBLE) {
                resetPScale();
            } else {
                gl_pendulum_scale.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                gl_pendulum_scale.setVisibility(View.VISIBLE);
                iv_hide_pendulum_scale.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }

    protected void initLinkMarkerButtons() {
        Arrays.fill(BtnVals, "0");
        linkButton = new Button[]{
                view.findViewById(R.id.button_1),
                view.findViewById(R.id.button_2),
                view.findViewById(R.id.button_3),
                view.findViewById(R.id.button_4),
                view.findViewById(R.id.button_5),
                view.findViewById(R.id.button_6),
                view.findViewById(R.id.button_7),
                view.findViewById(R.id.button_8),
                view.findViewById(R.id.button_9)
        };
        enImgs = new int[]{
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
        disImgs = new int[]{
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
        for (int i = 0; i < linkButton.length; i++) {
            final int index = i;
            Button button = linkButton[index];
            button.setOnClickListener((btn) -> {
                if ("0".equals(BtnVals[index])) {
                    btn.setBackgroundResource(enImgs[index]);
                    BtnVals[index] = "1";
                } else {
                    btn.setBackgroundResource(disImgs[index]);
                    BtnVals[index] = "0";
                }
                String mLinkStr = BtnVals[8] + BtnVals[7] + BtnVals[6] + BtnVals[5] + "0"
                        + BtnVals[3] + BtnVals[2] + BtnVals[1] + BtnVals[0];
                lineKey = Integer.parseInt(mLinkStr, 2);
            });
        }
        ll_linkControl.setVisibility(View.GONE);// 初始化时默认为折叠状态
        iv_hide_linkmarker.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        view.findViewById(R.id.ll_linkmarker).setOnClickListener(v -> {
            if (ll_linkControl.getVisibility() == View.VISIBLE) {
                resetLinkMarker();
            } else {
                ll_linkControl.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.push_in));
                ll_linkControl.setVisibility(View.VISIBLE);
                iv_hide_linkmarker.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
            }
        });
    }

    protected void initAttackDefenseSync() {
        // 监听攻击力文本框
        atkText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isEqual && !isUpdating) {
                    isUpdating = true; // 防止循环更新
                    defText.setText(s);
                    defText.setSelection(s.length()); // 保持光标位置
                    isUpdating = false;
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        // 监听守备力文本框
        defText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isEqual && !isUpdating) {
                    isUpdating = true; // 防止循环更新
                    atkText.setText(s);
                    atkText.setSelection(s.length()); // 保持光标位置
                    isUpdating = false;
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }


}

package cn.garymb.ygomobile.game.deck;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.DeckLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.ui.cards.deck.CardTypeImage;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.cards.deck.MyDeckItem;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.dialogs.deck.DeckSelectorDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import cn.garymb.ygomobile.ui.widget.CardView;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.YGOProActivity;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;


/**
 * Middle layer of the deck editor split chain (DeckEditorManagerBase <-
 * DeckEditorManagerMid <- DeckEditorManager). Owns the view binding and
 * assembly: findViewById wiring, label/button texts and click listeners,
 * recycler/grid setup, the pack-mode layout passthrough, the deck
 * selector dialog, the exit-editor confirm flow and the siding screen
 * state application.
 */
abstract class DeckEditorManagerMid extends DeckEditorManagerBase {

    public void terminate() {
        if (isModified && !isReadonly) {
            showConfirmDialog(DataManager.get().getStringManager().getSystemString(1356, "此操作将放弃对当前卡组的修改，是否继续？"), this::doTerminate);
        } else {
            doTerminate();
        }
    }

    protected void doTerminate() {
        saveLastCategoryAndDeck();
        if (listener != null) listener.onExitEditor();
    }
    protected void bindViews(View root) {
        btnShuffle = activity.findViewById(R.id.btn_deck_shuffle);
        btnSort = activity.findViewById(R.id.btn_deck_sort);
        btnClear = activity.findViewById(R.id.btn_deck_clear);
        btnDelete = activity.findViewById(R.id.btn_deck_delete);
        btnExit = activity.findViewById(R.id.btn_deck_exit);
        tvMainCountNum = root.findViewById(R.id.tv_main_count_num);
        tvExtraCountNum = root.findViewById(R.id.tv_extra_count_num);
        tvSideCountNum = root.findViewById(R.id.tv_side_count_num);
        llGenesysScoreboard = root.findViewById(R.id.ll_genesys_scoreboard);
        tvCreditLimit = root.findViewById(R.id.tv_credit_limit);
        tvCreditCount = root.findViewById(R.id.tv_credit_count);
        tvCreditRemain = root.findViewById(R.id.tv_credit_remain);
        tvMainMonsterCount = root.findViewById(R.id.tv_main_monster_count);
        tvMainSpellCount = root.findViewById(R.id.tv_main_spell_count);
        tvMainTrapCount = root.findViewById(R.id.tv_main_trap_count);
        tvExtraFusionCount = root.findViewById(R.id.tv_extra_fusion_count);
        tvExtraSynchroCount = root.findViewById(R.id.tv_extra_synchro_count);
        tvExtraXyzCount = root.findViewById(R.id.tv_extra_xyz_count);
        tvExtraLinkCount = root.findViewById(R.id.tv_extra_link_count);
        tvSideMonsterCount = root.findViewById(R.id.tv_side_monster_count);
        tvSideSpellCount = root.findViewById(R.id.tv_side_spell_count);
        tvSideTrapCount = root.findViewById(R.id.tv_side_trap_count);
        tvLabelDeck = root.findViewById(R.id.tv_label_deck);
        tvLabelType = root.findViewById(R.id.tv_label_type);
        tvLabelAttribute = root.findViewById(R.id.tv_label_attribute);
        tvLabelRace = root.findViewById(R.id.tv_label_race);
        tvLabelStar = root.findViewById(R.id.tv_label_star);
        tvLabelScale = root.findViewById(R.id.tv_label_scale);
        tvLabelLimit = root.findViewById(R.id.tv_label_limit);
        tvLabelAttack = root.findViewById(R.id.tv_label_attack);
        tvLabelDefense = root.findViewById(R.id.tv_label_defense);
        tvLabelKeyword = root.findViewById(R.id.tv_label_keyword);
        tvLabelMainDeck = root.findViewById(R.id.tv_label_main_deck);
        tvLabelExtraDeck = root.findViewById(R.id.tv_label_extra_deck);
        tvLabelSideDeck = root.findViewById(R.id.tv_label_side_deck);
        cgvMain = root.findViewById(R.id.cgv_deck_main);
        cgvExtra = root.findViewById(R.id.cgv_deck_extra);
        cgvSide = root.findViewById(R.id.cgv_deck_side);
        layoutExtraStats = root.findViewById(R.id.layout_extra_stats);
        layoutSideStats = root.findViewById(R.id.layout_side_stats);
        layoutDeckInfoPanel = root.findViewById(R.id.layout_deck_info_panel);
        layoutFilterPanel = root.findViewById(R.id.layout_filter_panel);
        llSideController = root.findViewById(R.id.ll_side_controller);
        layoutDeckRightPanel = root.findViewById(R.id.layout_deck_right_panel);
        ivMainMonsterType = root.findViewById(R.id.iv_main_monster_type);
        ivMainSpellType = root.findViewById(R.id.iv_main_spell_type);
        ivMainTrapType = root.findViewById(R.id.iv_main_trap_type);
        ivExtraFusionType = root.findViewById(R.id.iv_extra_fusion_type);
        ivExtraSynchroType = root.findViewById(R.id.iv_extra_synchro_type);
        ivExtraXyzType = root.findViewById(R.id.iv_extra_xyz_type);
        ivExtraLinkType = root.findViewById(R.id.iv_extra_link_type);
        ivSideMonsterType = root.findViewById(R.id.iv_side_monster_type);
        ivSideSpellType = root.findViewById(R.id.iv_side_spell_type);
        ivSideTrapType = root.findViewById(R.id.iv_side_trap_type);
        btnDeckManager = root.findViewById(R.id.btn_deck_manager);
        etDeckName = root.findViewById(R.id.et_deck_name);
        btnSave = root.findViewById(R.id.btn_deck_save);
        btnSaveAs = root.findViewById(R.id.btn_deck_save_as);
        btnSideFinish = root.findViewById(R.id.btn_side_finish);
        btnSideShuffle = root.findViewById(R.id.btn_side_shuffle);
        btnSideSort = root.findViewById(R.id.btn_side_sort);
        btnSideReset = root.findViewById(R.id.btn_side_reset);
        // 侧边按钮列双副本（横屏 dock 在卡组编辑器子树外），经全树查找：
        // DFS 先命中当前方向被点亮的可见副本，另一副本恒 GONE 绑定无害
        btnDeckSettings = activity.findViewById(R.id.btn_deck_settings);
        btnDeckSound = activity.findViewById(R.id.btn_deck_sound);
    }
    protected void setupLabels() {
        if (btnSideFinish != null) {
            btnSideFinish.setOnClickListener(v -> {
                if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
                sideFinish();
            });
        }
        if (btnSideShuffle != null) {
            btnSideShuffle.setOnClickListener(v -> {
                if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE);
                shuffleDeck();
            });
        }
        if (btnSideSort != null) {
            btnSideSort.setOnClickListener(v -> {
                if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
                sortDeck();
            });
        }
        if (btnSideReset != null) {
            btnSideReset.setOnClickListener(v -> {
                if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
                sideReset();
            });
        }
        setSystemLabel(tvLabelDeck, 1300, "卡组:");
        setSystemLabel(tvLabelType, 1311, "种类:");
        setSystemLabel(tvLabelAttribute, 1319, "属性:");
        setSystemLabel(tvLabelRace, 1321, "种族:");
        setSystemLabel(tvLabelStar, 1324, "星数:");
        setSystemLabel(tvLabelScale, 1336, "刻度:");
        setSystemLabel(tvLabelLimit, 1315, "禁限:");
        setSystemLabel(tvLabelAttack, 1322, "攻击:");
        setSystemLabel(tvLabelDefense, 1323, "守备:");
        setSystemLabel(tvLabelKeyword, 1325, "关键字:");
        setSystemLabel(tvLabelMainDeck, 1330, "主卡组:");
        setSystemLabel(tvLabelExtraDeck, 1331, "额外卡组:");
        setSystemLabel(tvLabelSideDeck, 1332, "副卡组:");
    }

    protected void setSystemLabel(TextView tv, int index, String def) {
        if (tv != null) tv.setText(mStringManager.getSystemString(index, def));
    }
    protected void setupRecyclerViews() {
        mLimitList = isBanListActive()
                ? (AppsSettings.get().getGenesysMode() == 1
                   ? cardLoader.getGenesysLimitList() : cardLoader.getLimitList())
                : null;
        mImageTop = new ImageTop(activity);
        mCardTypeImage = new CardTypeImage(activity);
        if (mCardTypeImage != null) {
            setBitmapIfNotNull(ivMainMonsterType, mCardTypeImage.monster);
            setBitmapIfNotNull(ivMainSpellType, mCardTypeImage.spell);
            setBitmapIfNotNull(ivMainTrapType, mCardTypeImage.trap);
            setBitmapIfNotNull(ivExtraFusionType, mCardTypeImage.fusion);
            setBitmapIfNotNull(ivExtraSynchroType, mCardTypeImage.synchro);
            setBitmapIfNotNull(ivExtraXyzType, mCardTypeImage.xyz);
            setBitmapIfNotNull(ivExtraLinkType, mCardTypeImage.link);
            setBitmapIfNotNull(ivSideMonsterType, mCardTypeImage.monster);
            setBitmapIfNotNull(ivSideSpellType, mCardTypeImage.spell);
            setBitmapIfNotNull(ivSideTrapType, mCardTypeImage.trap);
        }
        cgvMain.setImageLoader(imageLoader);
        // line_max_count 取换侧可容纳上限（见 SIDE_DISPLAY_MAIN_COLS/EXTRA_COLS 注释），
        // 否则 isSiding 临时超限时第 61 张 / 第 16 张因 getMaxCardCount 截断不显示
        cgvMain.setLineLimit(4, 10, SIDE_DISPLAY_MAIN_COLS);
        cgvExtra.setImageLoader(imageLoader);
        cgvExtra.setLineLimit(1, 10, SIDE_DISPLAY_EXTRA_COLS);
        cgvSide.setImageLoader(imageLoader);
        cgvSide.setLineLimit(1, 10, SIDE_DISPLAY_EXTRA_COLS);
        gridLayout.requestUpdate();
        cardSearcherManager.setLimitList(mLimitList);
    }

    protected void setBitmapIfNotNull(ImageView iv, Bitmap bm) {
        if (iv != null) iv.setImageBitmap(bm);
    }

    /**
     * 网格卡面尺寸计算与卡包展示模式版面（实现已拆至 {@link DeckGridLayoutApplier}）
     */
    void applyPackMode(boolean packMode) {
        gridLayout.applyPackMode(packMode);
    }
    protected void setupButtons() {
        if (btnSave != null) btnSave.setText(mStringManager.getSystemString(1302, "保存"));
        if (btnSaveAs != null) btnSaveAs.setText(mStringManager.getSystemString(1303, "另存"));
        // 卡组操作按钮文字复用 gframe 既有系统字符串（对齐 game.cpp wDeckEdit 各按钮）：
        // 洗牌(打乱) 1307 / 排序 1305 / 清空 1304 / 删除 1308 / 退出编辑 1306
        // （btnDeckManager 文本由 updateDeckManagerButtonText 动态维护，缺省用 1460 卡组管理）
        if (btnShuffle != null) btnShuffle.setText(mStringManager.getSystemString(1307, "打乱"));
        if (btnSort != null) btnSort.setText(mStringManager.getSystemString(1305, "排序"));
        if (btnClear != null) btnClear.setText(mStringManager.getSystemString(1304, "清空"));
        if (btnDelete != null) btnDelete.setText(mStringManager.getSystemString(1308, "删除"));
        if (btnExit != null) btnExit.setText(mStringManager.getSystemString(1306, "退出编辑"));
        setClickListener(btnExit, v -> terminate());
        setClickListener(btnShuffle, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE);
            shuffleDeck();
        });
        setClickListener(btnSort, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            sortDeck();
        });
        setClickListener(btnClear, v -> clearDeck());
        setClickListener(btnDelete, v -> deleteDeck());
        setClickListener(btnSave, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            saveDeck();
        });
        setClickListener(btnSaveAs, v -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            saveDeckAs();
        });
        setupDeckSideButtons();
    }

    /** 卡组编辑侧边按钮列（layout_deck_side_buttons）：设置直开设置对话框、
     *  音乐开关走与决斗场同款 toggleSoundMute 链路；图标与决斗场侧边按钮列同源纹理
     *  （tSettings/tPlay/tMute，对齐 CardDetailPanel.bindSideButtonIcons），纹理缺失保留 XML 默认图兜底 */
    protected void setupDeckSideButtons() {
        if (btnDeckSettings != null) {
            Bitmap bmp = TextureLoader.get().getSettingsTexture();
            if (bmp != null) btnDeckSettings.setImageBitmap(bmp);
            btnDeckSettings.setOnClickListener(v ->
                    ((YGOProActivity) activity).showSettingsDialog());
        }
        if (btnDeckSound != null) {
            btnDeckSound.setOnClickListener(v -> {
                ((YGOProActivity) activity).toggleSoundMute();
                updateDeckSoundIcon();
            });
            updateDeckSoundIcon();
        }
    }

    /** 音乐按钮图标随 chkEnableSound/chkEnableMusic（与 CardDetailPanel imgVol 同语义）；
     *  设置对话框在卡组编辑中修改声音开关后也需刷新（GameSettingsApplier.apply 回调本方法） */
    public void updateDeckSoundIcon() {
        if (btnDeckSound == null) return;
        boolean soundOn = AppsSettings.get().getIntSettings("chkEnableSound", 1) == 1
                || AppsSettings.get().getIntSettings("chkEnableMusic", 1) == 1;
        Bitmap bmp = soundOn ? TextureLoader.get().getPlayTexture() : TextureLoader.get().getMuteTexture();
        if (bmp != null) btnDeckSound.setImageBitmap(bmp);
    }

    protected void setClickListener(Button btn, View.OnClickListener l) {
        if (btn != null) btn.setOnClickListener(l);
    }
    protected void setupDeckSelectorDialog() {
        deckSelectorDialog = new DeckSelectorDialog(activity);
        deckSelectorDialog.setIncludePackCategory(true);
        deckSelectorDialog.setOnDeckSelectedListener(new DeckSelectorDialog.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(String p, String n, String c) {
                applySelectedDeck(p, n, c);
            }

            @Override
            public void onDeckItemClicked(String p, String n, String c) {
                applySelectedDeck(p, n, c);
            }

            @Override
            public void onCancelled() {
            }
        });
        if (btnDeckManager != null) {
            // 再次点击"选择卡组/卡组管理"按钮收起已展开的卡组选择窗（切换式交互，
            // 与 PlayerWaitingDialog 的 btnPwDeckSelect 行为一致）
            btnDeckManager.setOnClickListener(v -> {
                if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
                if (deckSelectorDialog == null) return;
                if (deckSelectorDialog.isShowing()) {
                    deckSelectorDialog.dismiss();
                    return;
                }
                if (isModified && !isReadonly) {
                    showConfirmDialog("此操作将放弃对当前卡组的修改，是否继续？",
                            () -> {
                                if (deckSelectorDialog != null)
                                    deckSelectorDialog.show(btnDeckManager);
                            });
                } else {
                    deckSelectorDialog.show(btnDeckManager);
                }
            });
        }
    }
    // === 对应 duelclient.cpp: STOC_CHANGE_SIDE 进入副卡组替换模式 ===
    public void enterSideMode() {
        isSiding = true;
        isReadonly = false;
        preMainCount = currentDeck.mainCards.size();
        preExtraCount = currentDeck.extraCards.size();
        preSideCount = currentDeck.sideCards.size();
        refreshReadonly();
        applySidingScreenState();
    }

    /**
     * 副卡组替换态统一施加界面显隐（enterSideMode 与旋转重绑 rebindAfterRotation 共用，
     * 保证切换方向后仍符合换侧态）：
     * - 隐藏卡组信息面板与筛选面板、显示副卡组控制行（完成/打乱/排序/重置）；
     * - 右侧搜索结果面板（搜索结果标题+排序 + 搜索结果列表）仅竖屏以 GONE 隐藏，
     *   让副卡组网格扩展占满；横屏保持 INVISIBLE 占位；
     * - 卡详面板的卡组控制按钮（退出编辑/打乱/排序/清空/删除）以 INVISIBLE 暂时收起。
     */
    protected void applySidingScreenState() {
        if (layoutDeckInfoPanel != null) layoutDeckInfoPanel.setVisibility(View.GONE);
        if (layoutFilterPanel != null) layoutFilterPanel.setVisibility(View.GONE);
        if (llSideController != null) llSideController.setVisibility(View.VISIBLE);
        if (layoutDeckRightPanel != null)
            layoutDeckRightPanel.setVisibility(isPortrait() ? View.GONE : View.INVISIBLE);
        if (cardDetailPanel != null) cardDetailPanel.hideDeckControl();
    }

    /** 当前是否竖屏显示（供换侧态右侧搜索结果面板按方向决定 GONE / INVISIBLE） */
    protected boolean isPortrait() {
        return activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }

    //副卡组替换完成/取消后退出副卡组模式
    public void exitSideMode() {
        isSiding = false;
        if (layoutDeckInfoPanel != null) layoutDeckInfoPanel.setVisibility(View.VISIBLE);
        if (layoutFilterPanel != null) layoutFilterPanel.setVisibility(View.VISIBLE);
        if (llSideController != null) llSideController.setVisibility(View.GONE);
        if (layoutDeckRightPanel != null) layoutDeckRightPanel.setVisibility(View.VISIBLE);
        if (cardDetailPanel != null) cardDetailPanel.showDeckControl();
    }
}

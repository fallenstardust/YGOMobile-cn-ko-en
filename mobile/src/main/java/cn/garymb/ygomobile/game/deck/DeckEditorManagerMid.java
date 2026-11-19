package cn.garymb.ygomobile.game.deck;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
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
import cn.garymb.ygomobile.ui.cards.deck.DeckHistory;
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
            // “退出编辑”与“选择卡组”是用户直接操作的入口，也是两侧唯一的放弃确认点：
            // 对端（卡组编辑页）现在静默跟随本侧切换，不再重复弹同一句（gframe 系统字符串 1356）
            showConfirmDialog(activity.getString(R.string.deck_discard_switch_tip), this::doTerminate);
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
        // 侧边按钮列在横屏是 layout_deck_editor 顶层水平行的首列、竖屏是根末尾悬浮 include，
        // 统一经 activity 全树查找，不依赖 rootView 当前的层级位置（每个方向运行时只有一份副本）
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
                    showConfirmDialog(activity.getString(R.string.deck_discard_switch_tip),
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
    /** Solo match 换 side：按席位卡组文件路径载入该副卡组到编辑器，
     *  供 {@code EngineCallbackDelegate} 在 SIDING 时先载入再 enterSideMode。路径为空则不动。
     *  <p>
     *  载入全程标为换 side 会话（本方法先于 enterSideMode，那之后由 isSiding 接棒）：这次载入
     *  既不 bindDeck 换户、也不把读盘内容写成快照，否则换 side 一起手就把卡组编辑页那份卡组的
     *  修改历史清零，或把页上的未保存修改采纳成换侧起点（换侧结果只提交给本局，不入卡组修改历史）。*/
    public void loadDeckForSideSwap(String deckPath) {
        if (deckPath == null || deckPath.isEmpty()) return;
        if (historyPanel != null) historyPanel.setSideSwapLoading(true);
        try {
            // 同步路径元信息：换侧界面的“重置”键与标题按当前席位卡组显示，不能停留在上一份卡组
            currentDeckFilePath = deckPath;
            currentDeckName = DeckUtil.ydkDisplayName(deckPath);
            currentDeckCategoryName = DeckUtil.getDeckTypeName(deckPath);
            loadDeckFromPath(deckPath);
            updateDeckManagerButtonText();
        } finally {
            if (historyPanel != null) historyPanel.setSideSwapLoading(false);
        }
    }

    /**
     * 换 side：按“本场对局卡组快照”（主/额外/副卡码）重建编辑器内容，不读盘。
     * <p>
     * 第 2/3 局之间磁盘 ydk 仍是原始卡组，而当前对局卡组是上一局换侧提交的结果（换侧只提交
     * 给本局、不落盘），从磁盘重读会让玩家在错误的卡组上换备卡，提交后服务端按各区张数与
     * 开局卡组比对而回 ERRMSG_SIDEERROR。{@code deckPath} 仅用于同步路径元信息（可为空）。
     *
     * @return 卡码全部能解析并载入时为 true；任一卡缺失（本地卡库与提交时不一致）返回 false，
     *         由调用方回退到磁盘路径载入
     */
    public boolean loadDeckForSideSwap(List<Integer> mainCodes, List<Integer> extraCodes, List<Integer> sideCodes, String deckPath) {
        DeckInfo loaded = buildDeckInfoFromCodes(mainCodes, extraCodes, sideCodes);
        if (loaded == null) return false;
        if (historyPanel != null) historyPanel.setSideSwapLoading(true);
        try {
            currentDeck.update(loaded);
            currentDeck.source = (deckPath == null || deckPath.isEmpty()) ? null : new File(deckPath);
            isModified = false;
            if (deckPath != null && !deckPath.isEmpty()) {
                currentDeckFilePath = deckPath;
                currentDeckName = DeckUtil.ydkDisplayName(deckPath);
                currentDeckCategoryName = DeckUtil.getDeckTypeName(deckPath);
                updateDeckManagerButtonText();
            }
            boolean packMode = deckPath != null && !deckPath.isEmpty() && DeckHistory.isPackDeck(deckPath);
            if (isPackMode != packMode) {
                gridLayout.applyPackMode(packMode);
            } else {
                notifyDeckChanged();
            }
            refreshReadonly();
            return true;
        } finally {
            if (historyPanel != null) historyPanel.setSideSwapLoading(false);
        }
    }

    /** 把三区卡码解析为 DeckInfo（卡库缺卡或超出各区上限时返回 null） */
    private DeckInfo buildDeckInfoFromCodes(List<Integer> mainCodes, List<Integer> extraCodes, List<Integer> sideCodes) {
        if (mainCodes == null || mainCodes.isEmpty()) return null;
        List<Integer> all = new ArrayList<>();
        all.addAll(mainCodes);
        if (extraCodes != null) all.addAll(extraCodes);
        if (sideCodes != null) all.addAll(sideCodes);
        SparseArray<Card> cards = cardLoader.readCards(all, true);
        if (cards == null) return null;
        DeckInfo loaded = new DeckInfo();
        for (Integer code : mainCodes) {
            Card c = code == null ? null : cards.get(code);
            if (c == null || !loaded.addMainCards(c)) return null;
        }
        if (extraCodes != null) {
            for (Integer code : extraCodes) {
                Card c = code == null ? null : cards.get(code);
                if (c == null || !loaded.addExtraCards(c)) return null;
            }
        }
        if (sideCodes != null) {
            for (Integer code : sideCodes) {
                Card c = code == null ? null : cards.get(code);
                if (c == null || !loaded.addSideCards(c)) return null;
            }
        }
        return loaded;
    }

    /** 换 side 编辑器重新显示前调用：括号内不采纳共享历史内容（卡组编辑页的未保存修改），
     *  否则换侧一起手编辑器就被刷成另一份内容；enterSideMode 置上 isSiding 后由其接棒 */
    public void beginSideSwapSession() {
        if (historyPanel != null) historyPanel.setSideSwapLoading(true);
    }

    /** 换 side 兜底：快照/席位路径都不可用且编辑器尚无卡组时退回“上次卡组”，避免空网格无法编辑 */
    public void ensureSidingDeckLoaded() {
        if (currentDeck != null && (currentDeck.getMainCount() > 0
                || currentDeck.getExtraCount() > 0 || currentDeck.getSideCount() > 0)) {
            return;
        }
        loadLastDeck();
    }

    /** 换侧重置：恢复到本次换 side 的起点（当前对局卡组），而不是磁盘上的原始 ydk
     * （第 2/3 局之间磁盘版本已不是当前对局使用的卡组） */
    public void sideReset() {
        if (isSiding && !sidingBaseMain.isEmpty()) {
            DeckInfo base = buildDeckInfoFromCodes(sidingBaseMain, sidingBaseExtra, sidingBaseSide);
            if (base != null) {
                currentDeck.update(base);
                isModified = false;
                notifyDeckChanged();
                return;
            }
        }
        if (currentDeckFilePath != null && !currentDeckFilePath.isEmpty()) {
            loadDeckFromPath(currentDeckFilePath);
        }
    }

    /** 当前是否处于换 side 编辑态（供 applySidingScreen 识别重复的 SIDING 派发） */
    public boolean isSidingMode() {
        return isSiding;
    }

    /** 当前席位是否已点过“完成”（提交闸门状态：区分“换侧界面已打开待编辑”与“已提交待下一席位”） */
    public boolean isSideSubmitting() {
        return sideSubmitting;
    }

    // 换侧期间的每次增删拖拽都不写卡组修改历史（见 DeckEditorHistoryPanel#isSidingSession），
    // 换侧结果只经 onSideDeckFinished 提交给本局，故 refreshReadonly 里两键也随会话态熄灭。
    public void enterSideMode() {
        isSiding = true;
        isReadonly = false;
        sideSubmitting = false;
        preMainCount = currentDeck.mainCards.size();
        preExtraCount = currentDeck.extraCards.size();
        preSideCount = currentDeck.sideCards.size();
        // 换侧起点快照：“重置”键恢复到这份内容（第 2/3 局之间即上一局换侧结果），
        // 不从磁盘重读（磁盘上那份 ydk 只在第 1 局等于当前对局卡组）
        sidingBaseMain.clear();
        sidingBaseExtra.clear();
        sidingBaseSide.clear();
        for (Card c : currentDeck.mainCards) sidingBaseMain.add(c.Code);
        for (Card c : currentDeck.extraCards) sidingBaseExtra.add(c.Code);
        for (Card c : currentDeck.sideCards) sidingBaseSide.add(c.Code);
        // 换侧会话由 isSiding 接棒：结束“席位卡组载入期”免采纳共享历史内容的括号
        if (historyPanel != null) historyPanel.setSideSwapLoading(false);
        refreshReadonly();
        applySidingScreenState();
        // 换侧必须可编辑：载入时可能带着只读态（如人机卡组目录）把网格触摸监听置成只读，
        // 此处按 isReadonly=false 重建一次网格（isSiding 已置位，不会写卡组历史）
        notifyDeckChanged();
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
        // 无论是否在换侧态，都结束“席位卡组载入期”括号（幂等）：载入中途异常/提前退出时
        // 不得让下次编辑一直误以为还在换侧而不落卡组修改历史
        if (historyPanel != null) historyPanel.setSideSwapLoading(false);
        // 幂等：除了换侧提交完成，编辑器关闭（DeckEditorViewHost.hide）也会调本方法收口换侧会话，
        // 未处于换侧态时（普通卡组编辑退出）无事可做，不得去改动布局显隐
        if (!isSiding) return;
        isSiding = false;
        sideSubmitting = false;
        // 换侧会话结束：会话期间被压住的 undo/redo 两键按现态重新点亮（编辑器仍可见时生效）
        if (historyPanel != null) historyPanel.refresh();
        if (layoutDeckInfoPanel != null) layoutDeckInfoPanel.setVisibility(View.VISIBLE);
        if (layoutFilterPanel != null) layoutFilterPanel.setVisibility(View.VISIBLE);
        if (llSideController != null) llSideController.setVisibility(View.GONE);
        if (layoutDeckRightPanel != null) layoutDeckRightPanel.setVisibility(View.VISIBLE);
        if (cardDetailPanel != null) cardDetailPanel.showDeckControl();
    }
}

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
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;


/**
 * Deck editor facade, base layer of the split inheritance chain
 * (DeckEditorManagerBase <- DeckEditorManagerMid <- DeckEditorManager).
 * Owns all shared state fields and the DeckEditorListener contract, the
 * limit-list refresh and ban-list gate, deck loading / last-deck restore,
 * the push / checkLimit / shuffle / sort / side data rules, clear / delete
 * / save / save-as with cloud sync, readonly state, card-info callbacks,
 * the unified deck-changed refresh and the confirm-dialog helper.
 */
abstract class DeckEditorManagerBase {

    protected static final String TAG = "DeckEditorManager";

    /**
     * 比赛模式局间换副卡组（isSiding）时临时放宽主/额外/副卡组最大上限的张数：
     * 对齐 C++ deck_con.cpp push_main/extra/side 的 "is_siding ? MAX + n : MAX"，
     * n 按需求取 15，使换侧过程中可临时超限存放待调换的卡，完成时由 sideFinish 校验张数还原。
     */
    protected static final int DECK_SIDE_SWAP_EXTRA = 15;

    /**
     * 换副卡组（isSiding）时主/额外/副网格需能容纳显示的最大列数（每行列数上限）：
     * 对齐 C++ drawing.cpp DrawDeckBd 的动态列数算法——主卡组超过 40 张时增列
     * (lx=(mainsize-41)/4+11) 保持 4 行不变，额外/副卡组单行按 dx=436/(size-1) 压缩铺满。
     * CardGroupView 容量 = lines × line_max_count 必须 ≥ 换侧放宽后的上限
     * (DECK_*_MAX + DECK_SIDE_SWAP_EXTRA)，否则第 61 张 / 第 16 张会被 getMaxCardCount
     * 截断而不显示。主卡组 4 行需 19 列(4×19=76≥75)，额外/副卡组单行需 30 列(1×30=30≥30)。
     * 供同包 DeckGridLayoutApplier 在按实测尺寸重设主网格 line limit 时复用。
     */
    static final int SIDE_DISPLAY_MAIN_COLS = (Constants.DECK_MAIN_MAX + DECK_SIDE_SWAP_EXTRA + 3) / 4; // ceil(75/4)=19
    static final int SIDE_DISPLAY_EXTRA_COLS = Constants.DECK_EXTRA_MAX + DECK_SIDE_SWAP_EXTRA; // 30

    public interface DeckEditorListener {
        void onDeckModified();

        void onDeckSaved();

        void onExitEditor();

        void onCardSelected(Card card);

        void onSearchResultsUpdated(int count);

        void onSideDeckFinished(List<Integer> main, List<Integer> extra, List<Integer> side);
    }

    // 以下共享字段/方法被同包协作类（DeckGridLayoutApplier / DeckStatsPanel / DeckDropHandler）
    // 经包级私有直连访问
    Activity activity;
    protected ImageLoader imageLoader;
    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected CardLoader cardLoader;
    protected DeckEditorListener listener;
    DeckInfo currentDeck;
    protected final Random random = new Random();
    boolean isModified = false;
    boolean isReadonly = false;
    boolean isPackMode = false;
    protected boolean isSiding = false;
    protected int preMainCount = 0, preExtraCount = 0, preSideCount = 0;
    int savedNormalCardWidth = 0;
    int savedNormalCardHeight = 0;
    View rootView;
    protected CardDetailPanel cardDetailPanel;
    TextView tvMainCountNum, tvExtraCountNum, tvSideCountNum;
    View llGenesysScoreboard;
    TextView tvCreditLimit, tvCreditCount, tvCreditRemain;
    TextView tvMainMonsterCount, tvMainSpellCount, tvMainTrapCount;
    TextView tvExtraFusionCount, tvExtraSynchroCount, tvExtraXyzCount, tvExtraLinkCount;
    TextView tvSideMonsterCount, tvSideSpellCount, tvSideTrapCount;
    protected TextView tvLabelDeck, tvLabelType, tvLabelAttribute, tvLabelRace;
    protected TextView tvLabelStar, tvLabelScale, tvLabelLimit, tvLabelAttack, tvLabelDefense, tvLabelKeyword;
    TextView tvLabelMainDeck, tvLabelExtraDeck, tvLabelSideDeck;
    CardGroupView cgvMain, cgvExtra, cgvSide;
    View layoutExtraStats, layoutSideStats;
    protected View layoutDeckInfoPanel, layoutFilterPanel;
    protected View llSideController, layoutDeckRightPanel;
    protected EditText etDeckName;
    protected Button btnSave, btnSaveAs, btnShuffle, btnSort, btnClear, btnDelete, btnExit;
    protected Button btnDeckManager;
    protected Button btnSideFinish, btnSideShuffle, btnSideSort, btnSideReset;
    // 卡组编辑侧边按钮列双副本：横屏占位 dock（activity_ygo_game 的 layout_game_content 内，
    // id=layout_deck_side_buttons_dock，按钮列在左卡组布局在右）/竖屏悬浮
    // （layout_deck_editor 根末尾，id=layout_deck_side_buttons，横向一行左下角），
    // 按方向各点亮其一；按钮同 ID 双副本时全树 findViewById 按 DFS 先命中当前方向的
    // 可见副本（dock 在水平行内先于 deck_editor），故经 activity 绑定）：
    // 仅接线 设置/音乐 两键，其余四键 XML 静态 INVISIBLE 占位；用独立 ID
    // （btn_deck_settings 等）避免与决斗场按钮列同名致全树 findViewById 错绑
    protected ImageView btnDeckSettings, btnDeckSound;
    protected ImageTop mImageTop;
    protected CardTypeImage mCardTypeImage;
    protected ImageView ivMainMonsterType, ivMainSpellType, ivMainTrapType;
    protected ImageView ivExtraFusionType, ivExtraSynchroType, ivExtraXyzType, ivExtraLinkType;
    protected ImageView ivSideMonsterType, ivSideSpellType, ivSideTrapType;
    LimitList mLimitList;
    String currentDeckCategoryName = "";
    String currentDeckName = "";
    String currentDeckFilePath = "";
    protected DeckSelectorDialog deckSelectorDialog;
    int touchSlop;
    CardDragHelper dragHelper;
    protected int availLm = 0;
    final StringManager mStringManager = DataManager.get().getStringManager();
    protected SoundManager soundManager;

    CardSearcherManager cardSearcherManager;

    // 自本类拆出的同包协作件（构造仅注入本类引用，无额外初始化顺序依赖）
    protected DeckGridLayoutApplier gridLayout;
    protected DeckStatsPanel statsPanel;
    protected DeckDropHandler dropHandler;

    public CardSearcherManager getCardSearcherManager() {
        return cardSearcherManager;
    }

    public void setListener(DeckEditorListener listener) {
        this.listener = listener;
        this.cardSearcherManager.setListener(count -> {
            if (listener != null) listener.onSearchResultsUpdated(count);
        });
    }
    public void refreshLimitList() {
        mLimitList = isBanListActive()
                ? (AppsSettings.get().getGenesysMode() == 1
                   ? cardLoader.getGenesysLimitList()
                   : cardLoader.getLimitList())
                : null;
        if (cgvMain != null) cgvMain.updateTopImage(mImageTop, mLimitList);
        if (cgvExtra != null) cgvExtra.updateTopImage(mImageTop, mLimitList);
        if (cgvSide != null) cgvSide.updateTopImage(mImageTop, mLimitList);
        cardSearcherManager.setLimitList(mLimitList);
        statsPanel.updateCounts();
    }

    /**
     * 禁限卡表是否生效：对应模式的开关开启（use_lflist/use_genesys_lflist）
     * 且所选禁卡表名不是N/A。未生效时不做checkLimit校验、不显示禁限角标与GeneSys记分板。
     */
    protected boolean isBanListActive() {
        AppsSettings settings = AppsSettings.get();
        boolean genesys = settings.getGenesysMode() == 1;
        boolean enabled = settings.getIntSettings(
                genesys ? "use_genesys_lflist" : "use_lflist", 1) == 1;
        if (!enabled) return false;
        String name = genesys ? settings.getLastGenesysLimit() : settings.getLastLimit();
        return name != null && !name.isEmpty() && !"N/A".equals(name);
    }
    //应用选中的卡组：加载、刷新按钮文本并记录最后分类/卡组（点击item与确认共用）
    protected void applySelectedDeck(String deckPath, String deckName, String categoryName) {
        currentDeckFilePath = deckPath;
        currentDeckCategoryName = categoryName;
        currentDeckName = deckName;
        loadDeckFromPath(deckPath);
        updateDeckManagerButtonText();
        AppsSettings.get().saveSettings("lastcategory", categoryName);
        AppsSettings.get().saveSettings("lastdeck", deckName);
        // 同步持久化路径（此前仅在退出编辑器时保存）：会话中换卡组后若 Activity 被系统
        // 整体重建，loadLastDeck 才能回到同一卡组/卡包，而非停留在旧 lastdeckpath
        AppsSettings.get().saveSettings("lastdeckpath", deckPath);
    }

    protected void updateDeckManagerButtonText() {
        if (btnDeckManager != null) {
            if (currentDeckName != null && !currentDeckName.isEmpty()) {
                String uncatName = activity.getString(R.string.category_Uncategorized);
                if (currentDeckCategoryName != null && !currentDeckCategoryName.isEmpty()
                        && !currentDeckCategoryName.equals(uncatName)) {
                    btnDeckManager.setText(currentDeckCategoryName + "|" + currentDeckName);
                } else {
                    btnDeckManager.setText(currentDeckName);
                }
            } else {
                btnDeckManager.setText(mStringManager.getSystemString(1460, "卡组管理"));
            }
        }
    }

    protected void loadDeckFromPath(String deckPath) {
        File deckFile = new File(deckPath);
        boolean loaded = false;
        if (deckFile.exists()) {
            DeckInfo loadedDeck = DeckLoader.readDeck(cardLoader, deckFile);
            if (loadedDeck != null) {
                currentDeck.update(loadedDeck);
                currentDeck.source = deckFile;
                isModified = false;
                loaded = true;
            }
        }
        String aiDeckDir = AppsSettings.get().getAiDeckDir();
        isReadonly = deckPath.startsWith(aiDeckDir);
        if (loaded) {
            String packDir = AppsSettings.get().getPackDeckDir();
            String cacheDeckDir = AppsSettings.get().getCacheDeckDir();
            boolean packMode = deckPath.startsWith(packDir)
                    || deckPath.startsWith(cacheDeckDir);
            if (isPackMode != packMode) {
                gridLayout.applyPackMode(packMode);
            } else {
                notifyDeckChanged();
            }
        }
        refreshReadonly();
    }

    /** Solo match 换 side：按席位卡组文件路径载入该副卡组到编辑器，
     *  供 {@code EngineCallbackDelegate} 在 SIDING 时先载入再 enterSideMode。路径为空则不动。 */
    public void loadDeckForSideSwap(String deckPath) {
        if (deckPath == null || deckPath.isEmpty()) return;
        loadDeckFromPath(deckPath);
    }

    // === 对应 deck_con.cpp: push_main ===
    // 副卡组替换（isSiding）期间按 +DECK_SIDE_SWAP_EXTRA 临时放宽上限，其余场景用常规上限。
    // 包级私有：同包协作类 DeckDropHandler 跨网格移动校验亦复用此口径。
    int getMaxMainCount() {
        return isSiding ? Constants.DECK_MAIN_MAX + DECK_SIDE_SWAP_EXTRA : Constants.DECK_MAIN_MAX;
    }

    int getMaxExtraCount() {
        return isSiding ? Constants.DECK_EXTRA_MAX + DECK_SIDE_SWAP_EXTRA : Constants.DECK_EXTRA_MAX;
    }

    int getMaxSideCount() {
        return isSiding ? Constants.DECK_SIDE_MAX + DECK_SIDE_SWAP_EXTRA : Constants.DECK_SIDE_MAX;
    }
    public boolean pushMain(Card card, int seq) {
        if (card == null || Card.isExtraCard(card.Type)) return false;
        if (!isPackMode && currentDeck.getMainCount() >= getMaxMainCount()) return false;
        if (!checkLimit(card)) return false;
        boolean result = (seq >= 0 && seq <= currentDeck.mainCards.size())
                ? currentDeck.addMainCards(seq, card, isPackMode) : currentDeck.addMainCards(card);
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    public boolean pushExtra(Card card, int seq) {
        if (card == null || !Card.isExtraCard(card.Type)) return false;
        if (currentDeck.getExtraCount() >= getMaxExtraCount()) return false;
        if (!checkLimit(card)) return false;
        boolean result = (seq >= 0 && seq <= currentDeck.extraCards.size())
                ? currentDeck.addExtraCards(seq, card) : currentDeck.addExtraCards(card);
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    public boolean pushSide(Card card, int seq) {
        if (card == null) return false;
        if (currentDeck.getSideCount() >= getMaxSideCount()) return false;
        if (!checkLimit(card)) return false;
        boolean result = (seq >= 0 && seq <= currentDeck.sideCards.size())
                ? currentDeck.addSideCards(seq, card) : currentDeck.addSideCards(card);
        if (result) {
            isModified = true;
            notifyDeckChanged();
        }
        return result;
    }

    // === 对应 deck_con.cpp: check_limit ===
    public boolean checkLimit(Card card) {
        if (card == null) return false;
        //禁限卡表未启用（关闭或选N/A）：跳过所有禁限数量与GeneSys点数校验
        if (mLimitList == null) return true;
        int gameCode = card.getGameCode();
        int limit = 3;
        if (mLimitList != null) {
            if (mLimitList.check(gameCode, gameCode, LimitType.Forbidden)) limit = 0;
            else if (mLimitList.check(gameCode, gameCode, LimitType.Limit)) limit = 1;
            else if (mLimitList.check(gameCode, gameCode, LimitType.SemiLimit)) limit = 2;
        }
        int count = 0;
        for (Card c : currentDeck.mainCards) if (c.getGameCode() == gameCode) count++;
        for (Card c : currentDeck.extraCards) if (c.getGameCode() == gameCode) count++;
        for (Card c : currentDeck.sideCards) if (c.getGameCode() == gameCode) count++;
        if (count >= limit) return false;
        if (mLimitList != null && mLimitList.getCreditLimits() != null) {
            int totalCredit = 0;
            for (Card c : currentDeck.mainCards) totalCredit += getCardCredit(c);
            for (Card c : currentDeck.extraCards) totalCredit += getCardCredit(c);
            for (Card c : currentDeck.sideCards) totalCredit += getCardCredit(c);
            if (totalCredit + getCardCredit(card) > mLimitList.getCreditLimits()) return false;
        }
        return true;
    }

    //GeneSys模式单卡起源点数：按规则同名卡code（getGameCode）查询，与禁限判断口径一致
    int getCardCredit(Card card) {
        if (card == null || mLimitList == null || mLimitList.getCredits() == null) return 0;
        Integer credit = mLimitList.getCredits().get(card.getGameCode());
        return credit != null ? credit : 0;
    }

    // === 对应 deck_con.cpp: BUTTON_SHUFFLE_DECK ===
    public void shuffleDeck() {
        if (isReadonly) return;
        Collections.shuffle(currentDeck.mainCards, random);
        isModified = true;
        notifyDeckChanged();
    }

    // === 对应 deck_con.cpp: BUTTON_SORT_DECK ===
    public void sortDeck() {
        if (isReadonly) return;
        currentDeck.sortAll();
        isModified = true;
        notifyDeckChanged();
    }
    // === 对应 deck_con.cpp: BUTTON_SIDE_OK ===
    public void sideFinish() {
        if (!isSiding) return;
        if (currentDeck.mainCards.size() != preMainCount
                || currentDeck.extraCards.size() != preExtraCount
                || currentDeck.sideCards.size() != preSideCount) {
            YGOUtil.showTextToast(mStringManager.getSystemString(1410, "副卡组替换不能改变卡组张数"));
            return;
        }
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        for (Card c : currentDeck.mainCards) main.add(c.Code);
        for (Card c : currentDeck.extraCards) extra.add(c.Code);
        for (Card c : currentDeck.sideCards) side.add(c.Code);
        if (listener != null) listener.onSideDeckFinished(main, extra, side);
    }

    // === 对应 deck_con.cpp: BUTTON_SIDE_RELOAD ===
    public void sideReset() {
        if (currentDeckFilePath != null && !currentDeckFilePath.isEmpty()) {
            loadDeckFromPath(currentDeckFilePath);
        }
    }
    // === 对应 deck_con.cpp: BUTTON_CLEAR_DECK ===
    public void clearDeck() {
        if (isReadonly) return;
        showConfirmDialog(mStringManager.getSystemString(1339, "是否清空正在编辑的卡组？"), () -> {
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            currentDeck.mainCards.clear();
            currentDeck.extraCards.clear();
            currentDeck.sideCards.clear();
            isModified = true;
            notifyDeckChanged();
        });
    }

    // === 对应 deck_con.cpp: BUTTON_DELETE_DECK ===
    public void deleteDeck() {
        if (isReadonly) return;
        if (currentDeckFilePath == null || currentDeckFilePath.isEmpty()) return;
        String deckName = currentDeckName != null && !currentDeckName.isEmpty()
                ? currentDeckName : new File(currentDeckFilePath).getName().replace(".ydk", "");
        String deletedCategory = currentDeckCategoryName;
        showConfirmDialog(deckName + "\n" + DataManager.get().getStringManager().getSystemString(1337, "是否删除这个卡组？"), () -> {
            File deckFile = new File(currentDeckFilePath);
            if (deckFile.exists()) {
                //先同步删除云端卡组（DeckFile必须在 ydk 文件从磁盘删除之前构造，
                //以便读取其中保存的 deckId；未登录时 deleteDecks 内部会直接跳过）
                syncDeckDeleteFromCloud(deckFile);

                deckFile.delete();
                currentDeckFilePath = "";
                currentDeckName = "";
                currentDeck.mainCards.clear();
                currentDeck.extraCards.clear();
                currentDeck.sideCards.clear();
                notifyDeckChanged();
                isModified = false;

                // 自动加载同分类第一个卡组，若分类已空则回退到未分类
                List<DeckFile> allDecks = DeckUtil.getDeckAllList();
                DeckFile targetDeck = null;
                for (DeckFile df : allDecks) {
                    if (df.getTypeName().equals(deletedCategory)) {
                        targetDeck = df;
                        break;
                    }
                }
                if (targetDeck == null) {
                    String uncatName = activity.getString(R.string.category_Uncategorized);
                    for (DeckFile df : allDecks) {
                        if (df.getTypeName().equals(uncatName)) {
                            targetDeck = df;
                            break;
                        }
                    }
                }
                if (targetDeck != null) {
                    applySelectedDeck(targetDeck.getPath(), targetDeck.getName(), targetDeck.getTypeName());
                } else {
                    updateDeckManagerButtonText();
                }

                if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
                YGOUtil.showTextToast(DataManager.get().getStringManager().getSystemString(1338, "删除成功"));
            }
        });
    }

    public void saveDeck() {
        if (isReadonly) return;
        if (currentDeckFilePath == null || currentDeckFilePath.isEmpty()) {
            YGOUtil.showTextToast("请先选择或另存卡组");
            return;
        }
        File deckFile = new File(currentDeckFilePath);
        boolean result = DeckUtils.save(currentDeck, deckFile);
        if (result) {
            isModified = false;
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            YGOUtil.showTextToast(DataManager.get().getStringManager().getSystemString(1335, "保存成功"));
            if (listener != null) listener.onDeckSaved();
            //保存到本地成功后，同步上传到卡组广场云端（萌卡账号已登录时）
            syncDeckUploadToCloud(deckFile, false);
        }
    }

    public void saveDeckAs() {
        if (isReadonly) return;
        if (etDeckName == null) return;
        String name = etDeckName.getText().toString().trim();
        if (name.isEmpty()) {
            YGOUtil.showTextToast("请输入卡组名称");
            return;
        }
        File deckFile = new File(AppsSettings.get().getDeckDir(), name + ".ydk");
        boolean result = DeckUtils.save(currentDeck, deckFile);
        if (result) {
            currentDeckFilePath = deckFile.getAbsolutePath();
            currentDeckName = name;
            String uncatName = activity.getString(R.string.category_Uncategorized);
            currentDeckCategoryName = uncatName;
            isModified = false;
            updateDeckManagerButtonText();
            AppsSettings.get().saveSettings("lastcategory", uncatName);
            AppsSettings.get().saveSettings("lastdeck", name);
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            YGOUtil.showTextToast("卡组已保存为：" + name);
            if (listener != null) listener.onDeckSaved();
            //另存为相当于新建卡组：向云端申请新的 deckId 后再上传（萌卡账号已登录时）
            syncDeckUploadToCloud(deckFile, true);
        }
    }
    /**
     * 保存/另存卡组成功后，将卡组同步上传到卡组广场云端（仅在萌卡账号已登录时执行），
     * 逻辑对齐 DeckManagerFragment 中 action_save / 新建卡组 两处对 DeckSquareApiUtil 的调用。
     *
     * @param ydkFile   刚保存成功的本地卡组文件
     * @param isNewDeck 是否为“另存为”新建的卡组：true 时先向服务器申请新的 deckId 再上传，
     *                  false 时复用 ydk 文件中已有的 deckId 直接覆盖上传
     */
    protected void syncDeckUploadToCloud(File ydkFile, boolean isNewDeck) {
        if (SharedPreferenceUtil.getServerToken() == null) {
            return;
        }
        LoginToken loginToken = new LoginToken(SharedPreferenceUtil.getServerUserId(),
                SharedPreferenceUtil.getServerToken());
        VUiKit.defer().when(() -> {
            try {
                List<MyDeckItem> deckItemList = new ArrayList<>();
                deckItemList.add(DeckUtil.getMyDeckItem(ydkFile));
                if (isNewDeck) {
                    DeckSquareApiUtil.requestIdAndPushNewDecks(deckItemList, loginToken);
                } else {
                    DeckSquareApiUtil.UploadMyDecks(deckItemList, loginToken);
                }
            } catch (IOException e) {
                return e;
            }
            return 0;
        }).fail(e -> LogUtil.e(TAG, "Upload deck failed: " + e))
                .done(result -> LogUtil.d(TAG, "Deck uploaded successfully"));
    }

    /**
     * 删除卡组时先删除云端对应记录；必须在 ydk 文件从磁盘删除之前构造 DeckFile，
     * 以便读取其中保存的 deckId。未登录时 DeckSquareApiUtil.deleteDecks 内部会直接跳过。
     */
    protected void syncDeckDeleteFromCloud(File ydkFile) {
        List<DeckFile> deckFileList = new ArrayList<>();
        deckFileList.add(new DeckFile(ydkFile));
        DeckSquareApiUtil.deleteDecks(deckFileList);
    }
    // === 对应 deck_con.cpp: RefreshReadonly ===
    public void refreshReadonly() {
        boolean disabled = isReadonly || isPackMode;
        int textColor = disabled ? Color.GRAY : Color.WHITE;
        if (btnSave != null) btnSave.setEnabled(!disabled);
        if (btnSaveAs != null) btnSaveAs.setEnabled(!disabled);
        setBtnState(btnClear, disabled, textColor);
        setBtnState(btnShuffle, disabled, textColor);
        setBtnState(btnSort, disabled, textColor);
        setBtnState(btnDelete, disabled, textColor);
    }

    protected void setBtnState(Button btn, boolean disabled, int textColor) {
        if (btn != null) {
            btn.setEnabled(!disabled);
            btn.setTextColor(textColor);
        }
    }

    public void showCardInfo(Card card) {
        if (card == null) return;
        if (cardDetailPanel != null) {
            cardDetailPanel.showCard(card);
        }
        if (listener != null) listener.onCardSelected(card);
    }

    public void onSearchCardClicked(Card card) {
        if (card == null || isReadonly) return;
        showCardInfo(card);
    }

    public void onDeckCardClicked(DeckInfo.Type type, int position) {
        if (cgvMain != null) cgvMain.clearSelection();
        if (cgvExtra != null) cgvExtra.clearSelection();
        if (cgvSide != null) cgvSide.clearSelection();
        Card card = null;
        if (type == DeckInfo.Type.Main) {
            card = currentDeck.getMainCard(position);
            if (cgvMain != null) cgvMain.setSelectedIndex(position);
        } else if (type == DeckInfo.Type.Extra) {
            card = currentDeck.getExtraCard(position);
            if (cgvExtra != null) cgvExtra.setSelectedIndex(position);
        } else if (type == DeckInfo.Type.Side) {
            card = currentDeck.getSideCard(position);
            if (cgvSide != null) cgvSide.setSelectedIndex(position);
        }
        if (card != null) showCardInfo(card);
    }
    // === Private helpers ===

    // 卡片增删后统一刷新（包级私有：同包协作类 DeckGridLayoutApplier / DeckDropHandler 亦经此刷新）
    void notifyDeckChanged() {
        statsPanel.updateCounts();
        refreshCardGroupView(cgvMain, currentDeck.mainCards, DeckInfo.Type.Main);
        refreshCardGroupView(cgvExtra, currentDeck.extraCards, DeckInfo.Type.Extra);
        refreshCardGroupView(cgvSide, currentDeck.sideCards, DeckInfo.Type.Side);
        if (listener != null) listener.onDeckModified();
    }

    protected void refreshCardGroupView(CardGroupView groupView, List<Card> cards, DeckInfo.Type type) {
        if (groupView == null) return;
        groupView.removeAllCards();
        for (int i = 0; i < cards.size(); i++) groupView.addCard(cards.get(i));
        groupView.updateTopImage(mImageTop, mLimitList);
        groupView.updateAvail(mImageTop, availLm);
        int count = groupView.getChildCount();
        for (int i = 0; i < count; i++) {
            CardView cardView = (CardView) groupView.getChildAt(i);
            final int index = i;
            cardView.setOnClickListener(v -> onDeckCardClicked(type, index));
            DeckCardAdapter adapter = cardSearcherManager.getSearchAdapter();
            if (adapter != null) {
                cardView.setOnTouchListener(adapter.createDragTouchListener(type, index, cardView.getCard(), touchSlop, isReadonly || isPackMode));
            }
        }
    }

    protected void loadLastDeck() {
        AppsSettings settings = AppsSettings.get();
        String lastDeckPath = settings.getLastDeckPath();
        String lastDeckName = settings.getLastDeckName();
        String lastCategory = settings.getLastCategory();

        String savedPath = settings.getSettings("lastdeckpath");
        if (savedPath != null && !savedPath.isEmpty()) {
            if (!new File(savedPath).exists()) {
                String cacheDeckDir = settings.getCacheDeckDir();
                if (cacheDeckDir != null && savedPath.startsWith(cacheDeckDir)) {
                    try {
                        DeckUtil.getExpansionsDeckList();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            if (new File(savedPath).exists()) {
                currentDeckFilePath = savedPath;
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName != null ? lastDeckName : "";
                loadDeckFromPath(savedPath);
                updateDeckManagerButtonText();
                return;
            }
        }

        if (lastDeckPath != null && !lastDeckPath.isEmpty()) {
            File deckFile = new File(lastDeckPath);
            if (deckFile.exists()) {
                currentDeckFilePath = lastDeckPath;
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName != null ? lastDeckName : "";
                loadDeckFromPath(lastDeckPath);
                updateDeckManagerButtonText();
                return;
            }
        }

        if (lastDeckName != null && !lastDeckName.isEmpty()) {
            File deckFile = new File(AppsSettings.get().getDeckDir(), lastDeckName + ".ydk");
            if (deckFile.exists()) {
                currentDeckFilePath = deckFile.getAbsolutePath();
                currentDeckCategoryName = lastCategory != null ? lastCategory : "";
                currentDeckName = lastDeckName;
                loadDeckFromPath(deckFile.getAbsolutePath());
                updateDeckManagerButtonText();
                return;
            }
        }

        File deckDir = new File(settings.getDeckDir());
        if (deckDir.exists() && deckDir.isDirectory()) {
            File[] files = deckDir.listFiles((dir, name) -> name.endsWith(".ydk"));
            if (files != null && files.length > 0) {
                Arrays.sort(files);
                File first = files[0];
                currentDeckFilePath = first.getAbsolutePath();
                currentDeckCategoryName = "";
                currentDeckName = first.getName().replace(".ydk", "");
                loadDeckFromPath(first.getAbsolutePath());
                updateDeckManagerButtonText();
            }
        }
    }

    protected void saveLastCategoryAndDeck() {
        if (currentDeckCategoryName != null && !currentDeckCategoryName.isEmpty()) {
            AppsSettings.get().saveSettings("lastcategory", currentDeckCategoryName);
        }
        if (currentDeckFilePath != null && !currentDeckFilePath.isEmpty()) {
            AppsSettings.get().saveSettings("lastdeckpath", currentDeckFilePath);
            String deckName = new File(currentDeckFilePath).getName().replace(".ydk", "");
            AppsSettings.get().saveSettings("lastdeck", deckName);
        }
    }

    protected void showConfirmDialog(String message, Runnable onConfirm) {
        mainHandler.post(() -> {
            new YesOrNoDialog(activity)
                    .setMessage(message)
                    .setType(YesOrNoDialog.TYPE_YES_NO)
                    .setPositiveButtonText(mStringManager.getSystemString(1213, "是"))
                    .setNegativeButtonText(mStringManager.getSystemString(1214, "否"))
                    .setPositiveButton(v -> {
                        onConfirm.run();
                    })
                    .show(rootView);
        });
    }
}

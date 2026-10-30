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
 * 卡组编辑器门面：视图绑定与装配、卡组数据增删改查/排序/侧换、保存与云端同步、
 * 读写与副卡组模式切换留在本类；按 // === 分栏拆出同包协作类（经包级私有直连本类共享状态）：
 * - {@link DeckGridLayoutApplier}：网格卡面尺寸计算与卡包展示模式版面
 * - {@link DeckStatsPanel}：张数 / 分类计数 / GeneSys 起源点数统计刷新
 * - {@link DeckDropHandler}：拖放落点（网格内换位、跳网格移动、搜索拖入、拖回搜索区删除）
 */
public class DeckEditorManager extends DeckEditorManagerMid implements CardDragHelper.DropHandler {

    public DeckEditorManager(Activity activity, ImageLoader imageLoader, CardDetailPanel cardDetailPanel) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        this.cardDetailPanel = cardDetailPanel;
        this.cardLoader = new CardLoader();
        this.currentDeck = new DeckInfo();
        // 获取 SoundManager（对齐 C++ deck_con.cpp 中的音效调用）：须先于 dragHelper 赋值，
        // 供拖拽拾起/放置音效（CARD_PICK/CARD_DROP）使用
        this.soundManager = ((YGOProActivity) activity).getSoundManager();
        this.dragHelper = new CardDragHelper(activity, this, soundManager);
        this.cardSearcherManager = new CardSearcherManager(activity);
        this.gridLayout = new DeckGridLayoutApplier(this);
        this.statsPanel = new DeckStatsPanel(this);
        this.dropHandler = new DeckDropHandler(this);
    }
    public void initialize(View rootView) {
        bindAndSetupViews(rootView);
        loadLastDeck();
        statsPanel.updateCounts();
        isModified = false;
    }
    /**
     * 横竖屏切换专用重绑：旋转后 Activity 不重建、本实例常驻，currentDeck/isPackMode/
     * isModified/searchResults 等内存态均仍在，只需把视图与监听重挂到新视图树并按
     * 现态重新渲染。绝不可走 initialize 的 loadLastDeck 路径：那会用持久化设置里的
     * 旧卡组覆盖内存中已编辑的 currentDeck（丢修改）、并因未持久化 lastdeckpath
     * 而加载到另一个卡组/卡包（丢卡包展示）。
     */
    public void rebindAfterRotation(View rootView) {
        bindAndSetupViews(rootView);
        // 当前卡组名/分类/按钮文本随实例保留，仅需刷到新视图树的控件上
        updateDeckManagerButtonText();
        if (isPackMode) {
            // 新视图树额外/副卡组区默认 VISIBLE，卡包模式需按现态重新压回 GONE 并铺满主网格
            // （applyPackMode 的 isPackMode==packMode 守卫会跳过，走无守卫重应用变体）
            gridLayout.reapplyPackModeForNewTree();
        }
        // 把内存中的卡组（含未保存的增删/拖拽改序）重新渲染到新网格
        notifyDeckChanged();
        // 搜索结果列表：setupSearchRecyclerView 新建了空适配器，把旧列表回填
        cardSearcherManager.rebindResultsToNewAdapter();
        refreshReadonly();
        // 副卡组替换（side）模式：旋转经 setContentView 重 inflate activity_ygo_game（layout_deck_editor
        // 为其 include），卡组信息面板/右侧搜索结果面板在新树里回到 XML 默认 VISIBLE，会把换侧态下
        // 应隐藏的布局漏显；须按现态整体重挂（信息/筛选面板隐藏、侧栏控制行显示、结果面板按方向隐藏、
        // 卡详卡组控制按钮收起），故与 enterSideMode 共用 applySidingScreenState。
        if (isSiding) {
            applySidingScreenState();
        }
        statsPanel.updateCounts();
        // 注意：不重置 isModified、不重载卡组，保持编辑态与当前卡组/卡包不变
    }
    /** initialize / rebindAfterRotation 共用的视图绑定与装配（不含数据加载与状态重置） */
    protected void bindAndSetupViews(View rootView) {
        this.rootView = rootView;
        bindViews(rootView);
        setupLabels();
        setupRecyclerViews();
        dropHandler.setup();
        setupButtons();
        cardSearcherManager.bindViews(rootView);
        cardSearcherManager.setupLabels();
        // 传递 soundManager 给 CardSearcherManager
        if (soundManager != null) {
            cardSearcherManager.setSoundManager(soundManager);
        }
        cardSearcherManager.setupSearchRecyclerView(imageLoader, dragHelper, this);
        cardSearcherManager.setupSpinners();
        cardSearcherManager.setupButtons();
        // 卡组编辑态：卡详描述里高亮词点击直接填入卡组检索词并搜索（结果呈现在 rv_deck_search_results），
        // 不再另弹 KeywordCardListDialog；退出卡组编辑（DeckEditorViewHost.hide）时置空恢复决斗态弹窗行为
        if (cardDetailPanel != null) {
            cardDetailPanel.setDeckKeywordNavigator(cardSearcherManager::searchByKeyword);
            // 卡组编辑态：点击卡详关联卡片按钮把关联卡列表直接填到卡组搜索结果列表，不另弹关键词列表弹窗
            cardDetailPanel.setDeckCardListHandler(cardSearcherManager::showRelatedCards);
        }
        setupDeckSelectorDialog();
    }
    // === 拖放：网格内换位、跳网格移动、搜索结果拖入、拖回搜索区删除
    //   （实现已拆至 DeckDropHandler，本类仅作为 CardDragHelper.DropHandler 接收回调并转发）===

    @Override
    public void onCardDrop(View target, DeckInfo.Type source, int index, Card card, float rawX, float rawY) {
        dropHandler.onCardDrop(target, source, index, card, rawX, rawY);
    }
}

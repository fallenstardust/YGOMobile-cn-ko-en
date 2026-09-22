package cn.garymb.ygomobile;

import android.view.View;

import cn.garymb.ygomobile.game.DeckEditorManager;
import cn.garymb.ygomobile.lite.R;
import ocgcore.data.Card;

import java.util.List;

/**
 * === 卡组编辑器视图切换（对齐 gframe DeckBuilder 窗口的显示 / 关闭）===
 * 自 YGOProActivity 拆出：负责 layout_deck_editor / layout_deck_control 的显隐、
 * {@link DeckEditorManager} 的懒建与初始化、左侧卡片详情面板的编辑器模式切换，
 * 以及副卡组替换完成后回投 CTOS_DECK_UPDATE。对外入口仍是
 * {@link YGOProActivity#showDeckEditorView()}（hideGameUI / setWindowBackground /
 * updateBGM 等 UI 编排仍由门面提供）。
 */
class DeckEditorViewHost {

    private final YGOProActivity activity;

    DeckEditorViewHost(YGOProActivity activity) {
        this.activity = activity;
    }

    void show() {
        activity.setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_DECK);
        activity.getMainMenuDialog().hideMainMenu();
        activity.hideGameUI();
        if (activity.layoutGameContent != null) activity.layoutGameContent.setVisibility(View.VISIBLE);
        if (activity.layoutDeckEditor == null) {
            activity.layoutDeckEditor = activity.findViewById(R.id.layout_deck_editor);
        }
        if (activity.layoutDeckEditor != null) {
            activity.layoutDeckEditor.setVisibility(View.VISIBLE);
        }

        // 隐藏右侧决斗场区，让卡组编辑器占据其空间
        if (activity.layoutGameRight != null) activity.layoutGameRight.setVisibility(View.GONE);

        if (activity.layoutDeckControl == null) {
            activity.layoutDeckControl = activity.findViewById(R.id.layout_deck_control);
        }
        if (activity.layoutDeckControl != null) activity.layoutDeckControl.setVisibility(View.VISIBLE);

        // 立刻显示左侧卡片详情面板（默认内容），并切换为卡组编辑器模式
        activity.cardDetailPanel.enterDeckEditorMode();

        if (activity.deckEditorManager == null) {
            activity.deckEditorManager = new DeckEditorManager(activity, activity.imageLoader,
                    activity.cardDetailPanel);
            activity.deckEditorManager.setListener(new DeckEditorManager.DeckEditorListener() {
                @Override
                public void onDeckModified() {
                }

                @Override
                public void onDeckSaved() {
                }

                @Override
                public void onExitEditor() {
                    hide();
                    activity.getMainMenuDialog().restoreMainMenu();
                }

                @Override
                public void onCardSelected(Card card) {
                }

                @Override
                public void onSearchResultsUpdated(int count) {
                }

                @Override
                public void onSideDeckFinished(List<Integer> main, List<Integer> extra, List<Integer> side) {
                    if (activity.engine != null) {
                        activity.engine.sendDeckUpdate(main, extra, side);
                    }
                    // 副卡组替换完成：退出副卡组模式并隐藏整个卡组编辑器布局，
                    // 等待下次 STOC_CHANGE_SIDE 进入副卡组替换模式时再显示
                    if (activity.deckEditorManager != null) {
                        activity.deckEditorManager.exitSideMode();
                    }
                    hide();
                }
            });
        }
        if (activity.layoutDeckEditor != null) {
            activity.deckEditorManager.initialize(activity.layoutDeckEditor);
        }
        // 卡组编辑器（含副卡组替换）布局已显示：切换 DECK 场景（对齐 Game::playBGM 的 is_building 分支）
        activity.updateBGM();
    }

    void hide() {
        if (activity.layoutDeckEditor != null) {
            activity.layoutDeckEditor.setVisibility(View.GONE);
        }
        if (activity.layoutDeckControl != null) activity.layoutDeckControl.setVisibility(View.GONE);
        activity.cardDetailPanel.exitDeckEditorMode();
        // 卡组编辑器隐藏后重算场景（无其他布局显示 → MENU）
        activity.updateBGM();
    }
}

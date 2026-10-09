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

        // 隐藏右侧决斗场区，让卡组编辑器占据其空间；
        // 竖屏顶部面板保留可见（承载卡片详情栏与 layout_deck_control 卡组操作按钮）
        if (activity.layoutGameRight != null) activity.layoutGameRight.setVisibility(View.GONE);
        activity.setGameTopPanelVisible(true);

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
                    // Solo match 换 side：改由客户端本地逐席位驱动，不再依赖服务端每提交一份
                    // 回一条 CHANGE_SIDE 的跨端回环（该回环在共享连接上重入时序脆弱，导致点“完成”
                    // 后无响应、无法切到下一席位）。提交一份后，若仍有席位待换，直接延后重新载入
                    // 下一席位；required 与服务端一致：TAG 4 份、其余 2 份。
                    boolean solo = activity.engine != null && activity.engine.soloMode;
                    if (solo) {
                        int required = activity.engine.gameMode == 2 ? 4 : 2;
                        if (activity.engine.soloSideSession < required) {
                            // applySidingScreen 内含 showDeckEditorView → deckEditorManager.initialize，
                            // 直接在点击回调栈内重入会破坏当前布局，故延后到本帧之后执行。
                            activity.engine.mainHandler.post(() -> {
                                if (activity.engineCallback != null) {
                                    activity.engineCallback.applySidingScreen();
                                }
                            });
                            return;
                        }
                    }
                    // 副卡组替换完成（非 solo，或 solo 已全部席位提交完毕）：
                    // 退出副卡组模式并隐藏整个卡组编辑器布局，等待服务端就绪后的下一局 STOC_DUEL_START
                    //（非 solo 则等待对侧玩家换完）。全部席位换完后服务端收齐 required 份会直接下发
                    // DUEL_START 进入 match 下一局。
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
        // 卡组编辑属聊天抑制场景（对齐 gframe is_building 隐藏 wChat）：隐藏聊天输入框与开关
        activity.updateChatUIVisibility();
    }

    void hide() {
        if (activity.layoutDeckEditor != null) {
            activity.layoutDeckEditor.setVisibility(View.GONE);
        }
        if (activity.layoutDeckControl != null) activity.layoutDeckControl.setVisibility(View.GONE);
        // 退出卡组编辑：清除关键词导航器，使卡详描述高亮词点击恢复为决斗态的 KeywordCardListDialog 弹窗
        if (activity.cardDetailPanel != null) activity.cardDetailPanel.setDeckKeywordNavigator(null);
        activity.cardDetailPanel.exitDeckEditorMode();
        // 卡组编辑器隐藏后重算场景（无其他布局显示 → MENU）
        activity.updateBGM();
        // layoutDeckEditor 已置 GONE、btnChat 被 exitDeckEditorMode 重绑为默认可见：
        // 按退出后的实际场景重算聊天输入框与开关显隐
        activity.updateChatUIVisibility();
    }
}

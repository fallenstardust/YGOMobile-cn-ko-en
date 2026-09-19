package cn.garymb.ygomobile;

import java.util.List;

import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.ui.dialogs.CreateHostDialog;
import cn.garymb.ygomobile.ui.dialogs.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.PlayerWaitingDialog;
import cn.garymb.ygomobile.ui.dialogs.SingleModeDialog;

/**
 * 局域网主菜单导航协作类（由 YGOProActivity 按 // === 分栏拆分而来）：
 * 承载 Main Menu 分栏下沉的三个对话框监听接口——{@link LanModeDialog.OnLanModeListener}、
 * {@link CreateHostDialog.OnCreateHostListener}、{@link PlayerWaitingDialog.OnPlayerWaitingListener}——
 * 的回调实现，以及建主/玩家等待对话框的导航入口 showCreateHost/showPlayerWaiting。
 * 与门面同包，通过包级私有直连 YGOProActivity 的对话框字段、engine 与连接信息、UI 编排方法。
 */
class MainMenuNavigator implements
        LanModeDialog.OnLanModeListener,
        CreateHostDialog.OnCreateHostListener,
        PlayerWaitingDialog.OnPlayerWaitingListener {

    private final YGOProActivity activity;

    MainMenuNavigator(YGOProActivity activity) {
        this.activity = activity;
    }

    // === 局域网三对话框导航（LanModeDialog / CreateHostDialog） ===

    @Override
    public void onCreateHostRequested(String nickname) {
        if (activity.lanModeDialog != null) activity.lanModeDialog.hideForNavigation();
        showCreateHost(nickname);
    }

    @Override
    public void onCreateHostConfirmed(int lflist, int cardAllowed, int modeIdx, int duelRule,
                                      int startLP, int startHand, int drawCount, int timeLimit,
                                      boolean noCheckDeck, boolean noShuffleDeck,
                                      String hostName, String password, String nickname) {
        String roomName = (hostName != null && !hostName.isEmpty()) ? hostName : "Local Game";
        String userName = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;

        String localIp = LanDiscoveryManager.getLocalIpAddress();
        activity.saveLastConnectionInfo(userName, localIp != null ? localIp : "127.0.0.1", 7911, roomName);

        activity.engine.setPlayerName(userName);
        // cardAllowed 即协议 HostInfo.rule（卡片允许 0..5，对齐 duelclient.cpp cscg.info.rule），
        // duelRule 为协议 HostInfo.duel_rule（1..5）
        activity.engine.startLocalServerWithSettings(lflist, cardAllowed, modeIdx, duelRule,
                noCheckDeck, noShuffleDeck,
                startLP, startHand, drawCount, timeLimit,
                roomName, password != null ? password : "");

        if (activity.createHostDialog != null) activity.createHostDialog.hideForNavigation();
        showPlayerWaiting(userName, modeIdx == 2);
    }

    @Override
    public void onCancelCreate() {
        if (activity.createHostDialog != null) activity.createHostDialog.hideForNavigation();
        LanModeDialog.showLanModeDialog(activity);
    }

    @Override
    public void onJoinGameRequested(String ip, String port, String password, String nickname) {
        int portNum;
        try {
            portNum = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            portNum = 7911;
        }
        String userName = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;
        activity.saveLastConnectionInfo(userName, ip, portNum, password);
        activity.engine.setPlayerName(userName);
        activity.engine.connectToServer(ip, portNum, false, "", password,
                0, 0, 5, 8000, 5, 1, 0, false, false);

        if (activity.lanModeDialog != null) activity.lanModeDialog.hideForNavigation();
        showPlayerWaiting(userName, false);
    }

    // === PlayerWaitingDialog.OnPlayerWaitingListener ===

    @Override
    public void onPlayerWaitingReady() {
        if (activity.engine != null) activity.engine.sendReady();
    }

    @Override
    public void onPlayerWaitingNotReady() {
        if (activity.engine != null) activity.engine.sendNotReady();
    }

    @Override
    public void onPlayerWaitingToDuelist() {
        if (activity.engine != null) activity.engine.sendToDuelist();
    }

    @Override
    public void onPlayerWaitingToObserver() {
        if (activity.engine != null) activity.engine.sendToObserver();
    }

    @Override
    public void onExitWaiting() {
        // 人机模式（从 SingleModeDialog 建主进入等待界面）退出后应回 SingleModeDialog，
        // 而非 LanModeDialog；且不得出现 MainMenuDialog
        final boolean botMode = activity.engine != null && activity.engine.isBotMode;
        // 先关闭等待界面并抑制其 dismiss→restoreMainMenu 兜底，再断开连接；
        // DISCONNECTED 的自动 returnToLanMain（会连带弹 LanMode/MainMenu）已被
        // suppressNextDisconnectedReturn 抑制，返回导航由本入口独占
        if (activity.playerWaitingDialog != null) {
            activity.playerWaitingDialog.hideForNavigation();
            activity.setPlayerWaitingDialog(null);
        }
        activity.hideGameUI();
        if (activity.engine != null) {
            activity.suppressNextDisconnectedReturn();
            activity.engine.disconnect();
            if (botMode) activity.engine.setBotMode(false);
        }
        if (botMode) {
            SingleModeDialog.showSingleModeDialog(activity);
            return;
        }
        LanModeDialog.showLanModeDialog(activity);
        if (activity.lanModeDialog != null) {
            activity.lanModeDialog.preFillConnectionFields(activity.lastJoinNickname, activity.lastJoinHost,
                    String.valueOf(activity.lastJoinPort), activity.lastJoinRoomName);
        }
    }

    @Override
    public void onPlayerWaitingDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side) {
        if (activity.engine != null) {
            activity.engine.sendDeckUpdate(main, extra, side);
        }
    }

    @Override
    public void onStartGameRequested() {
        if (activity.engine != null) {
            activity.engine.sendStart();
        }
    }

    @Override
    public void onKickPlayerRequested(int pos) {
        if (activity.engine != null) {
            activity.engine.sendKick(pos);
        }
    }

    @Override
    public void onPlayerWaitingShown() {
        activity.runOnUiThread(() -> {
            activity.enterLobbyChatUI();
            // 兼容时序竞态：STOC_JOIN_GAME 若在等待界面创建前到达（handleJoinGame 被丢弃），
            // 界面就绪后用引擎缓存的房间信息补发房间规则显示（对齐 gframe 进入 wHostPrepare 即刷新 stHostPrepRule）
            if (activity.engine != null && activity.engine.hasJoinRoomInfoCache
                    && activity.playerWaitingDialog != null) {
                activity.playerWaitingDialog.updateRoomInfo(activity.engine.gameLflist,
                        activity.engine.gameRule, activity.engine.gameMode,
                        activity.engine.field.dInfo.duelRule,
                        activity.engine.gameNoCheckDeck, activity.engine.gameNoShuffleDeck,
                        activity.engine.gameStartLp, activity.engine.gameStartHand,
                        activity.engine.gameDrawCount, activity.engine.gameTimeLimit);
            }
        });
    }

    // === 建主 / 玩家等待对话框显示入口 ===

    private void showCreateHost(String nickname) {
        if (activity.createHostDialog == null) {
            activity.createHostDialog = new CreateHostDialog(activity, this);
            // 点击外部/返回键意外关闭建主界面时回到局域网主界面
            activity.createHostDialog.setOnDismissListener(() -> LanModeDialog.showLanModeDialog(activity));
        }
        activity.createHostDialog.setNickname(nickname);
        if (activity.createHostDialog.canReshow()) {
            activity.createHostDialog.reshow(activity.dialogContainer);
        } else if (!activity.createHostDialog.isShowing()) {
            activity.createHostDialog.show(activity.dialogContainer);
        }
    }

    private void showPlayerWaiting(String nickname, boolean tagMode) {
        if (activity.playerWaitingDialog != null) {
            activity.playerWaitingDialog.hideForNavigation();
        }
        PlayerWaitingDialog dialog = new PlayerWaitingDialog(activity, this);
        activity.setPlayerWaitingDialog(dialog);
        dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
        dialog.show(activity.dialogContainer);
        String name = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;
        dialog.setPlayerName(0, name);
        dialog.setTagPlayersVisible(tagMode);
    }
}

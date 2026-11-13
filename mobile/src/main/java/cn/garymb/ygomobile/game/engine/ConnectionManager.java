package cn.garymb.ygomobile.game.engine;
import cn.garymb.ygomobile.game.replay.ReplayPlayer;

import android.content.Intent;
import android.util.Log;


import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.GameApplication;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.network.server.LanGameServer;
import cn.garymb.ygomobile.utils.CrashHandler;

/**
 * === Connection ===
 * 自 GameEngine 拆分而来：联机加入 / 本地房主 / 残局（single mode）/ WindBot 人机 /
 * 录像加载控制 / 断开的连接生命周期管理。
 */
public class ConnectionManager {
    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    /** 纯 Java 局域网主机（建主/残局/人机共用），懒启动、断线时停止。
     * volatile：launchWindBot 在独立线程轮询房间就绪状态，需跨线程可见性 */
    private volatile LanGameServer localServer;

    /** 本次人机需要入座的 AI 席位数（1=单局/比赛，3=TAG），加入超时按此判定是否全部入座。 */
    private volatile int expectedBotSeats = 1;

    public ConnectionManager(GameEngine engine) {
        this.engine = engine;
        this.botJoinTimeout = () -> {
            LanGameServer server = localServer;
            if (server == null || !server.isRunning()) {
                return;
            }
            if (!server.isBotSeatsJoined(expectedBotSeats) && engine.listener != null) {
                engine.listener.onNoticeMessage("AI 连接失败：WindBot 未在预期时间内加入");
            }
        };
    }

    /**
     * 启动（或复用）纯 Java 局域网主机。失败时经 listener 显式反馈，不再静默死线程。
     *
     * @param port  监听端口
     * @param scene 触发场景（用于提示文案）
     * @return 主机已就绪返回 true
     */
    private boolean ensureLocalServer(int port, String scene) {
        LanGameServer server = localServer;
        if (server == null) {
            server = new LanGameServer();
            localServer = server;
        }
        if (server.isRunning()) {
            return true;
        }
        boolean started;
        try {
            started = server.start(port);
        } catch (Throwable t) {
            Log.e(TAG, "启动局域网主机异常: " + scene, t);
            started = false;
        }
        if (!started) {
            final String reason = "建立主机失败: 决斗引擎不可用或端口被占用";
            Log.w(TAG, reason + " (" + scene + ")");
            engine.setState(GameEngine.GameState.DISCONNECTED);
            engine.mainHandler.post(() -> {
                if (engine.listener != null) {
                    engine.listener.onNoticeMessage(reason);
                }
            });
        }
        return started;
    }

    /** AI 加入超时（毫秒）：启动 WindBot 后若在此时长内所需席位仍未全部入座则提示失败。 */
    private static final long BOT_JOIN_TIMEOUT_MS = 25000L;

    /** 逐席拉起时等上一席入座的时限（毫秒）：保证复选顺序与 P2/P3/P4 席位顺序一致。 */
    private static final long BOT_SEAT_ORDER_WAIT_MS = 10000L;

    /** AI 加入超时任务（在构造函数中初始化，以引用已赋值的 engine）。 */
    private final Runnable botJoinTimeout;

    /** 启动/重置 AI 加入超时定时器（主线程）。 */
    private void scheduleBotJoinTimeout() {
        engine.mainHandler.removeCallbacks(botJoinTimeout);
        engine.mainHandler.postDelayed(botJoinTimeout, BOT_JOIN_TIMEOUT_MS);
    }

    /** 取消未触发的 AI 加入超时（正常加入或断线时）。 */
    private void cancelBotJoinTimeout() {
        engine.mainHandler.removeCallbacks(botJoinTimeout);
    }

    /**
     * 起连接/建主后台线程的统一入口：挂上 {@link CrashHandler} 线程级处理器，
     * 使横屏对局建立阶段工作线程的未捕获异常先落盘 ygocore/log（带场景名）再走全局崩溃流程。
     */
    private void spawn(String threadName, String scene, Runnable body) {
        Thread t = new Thread(body, threadName);
        CrashHandler.getInstance().hookThread(t, scene);
        t.start();
    }

    public void connectToServer(String host, int port, boolean createGame,
                                String roomName, String password,
                                int rule, int mode, int duelRule,
                                int startLp, int startHand, int drawCount, int timeLimit,
                                boolean noCheckDeck, boolean noShuffleDeck) {
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.isHost = createGame;
        engine.maxMatch = (mode == YGOProtocol.MODE_MATCH) ? 3 : 1;

        spawn("GameConnect", "网络-连接服务器", () -> {
            boolean connected = engine.client.connect(host, port);
            if (!connected) {
                engine.setState(GameEngine.GameState.DISCONNECTED);
                return;
            }
            engine.client.sendExternalAddress(host);
            engine.client.sendPlayerInfo(engine.playerName);
            engine.client.sendJoinGame(0x1362, password);
        });
    }

    public void startLocalServer() {
        Log.i(TAG, "Starting local server...");
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.isHost = true;
        engine.maxMatch = 1;
        spawn("LocalServer", "网络-局域网建主", () -> {
            try {
                if (!ensureLocalServer(7911, "局域网建主")) {
                    return;
                }
                LanDiscoveryManager.acquireHostMulticastLock();
                // start() 已同步完成 ServerSocket 绑定与 accept 线程拉起，无需额外等待即可环回连接
                boolean connected = engine.client.connect("127.0.0.1", 7911);
                if (!connected) {
                    engine.setState(GameEngine.GameState.DISCONNECTED);
                    engine.mainHandler.post(() -> {
                        if (engine.listener != null) engine.listener.onNoticeMessage("无法连接到本地游戏服务器");
                    });
                    return;
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(0, 0, 0, 5,
                        false, false,
                        8000, 5, 1, 0,
                        "Local Game", "");
            } catch (Throwable t) {
                CrashHandler.getInstance().report("网络-建立主机", t);
                Log.e(TAG, "建立主机失败", t);
                engine.setState(GameEngine.GameState.DISCONNECTED);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onNoticeMessage("建立主机失败: " + t.getMessage());
                });
            }
        });
    }

    public void startLocalServerWithSettings(int lflist, int rule, int mode, int duelRule,
                                              boolean noCheckDeck, boolean noShuffleDeck,
                                              int startLp, int startHand, int drawCount, int timeLimit,
                                              String roomName, String password) {
        Log.i(TAG, "Starting local server with settings: " + roomName);
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.isHost = true;
        engine.maxMatch = (mode == YGOProtocol.MODE_MATCH) ? 3 : 1;
        spawn("LocalServer", "网络-局域网建主(自定义设置)", () -> {
            try {
                if (!ensureLocalServer(7911, "局域网建主")) {
                    return;
                }
                LanDiscoveryManager.acquireHostMulticastLock();
                // start() 已同步完成 ServerSocket 绑定与 accept 线程拉起，无需额外等待即可环回连接
                boolean connected = engine.client.connect("127.0.0.1", 7911);
                if (!connected) {
                    engine.setState(GameEngine.GameState.DISCONNECTED);
                    engine.mainHandler.post(() -> {
                        if (engine.listener != null) engine.listener.onNoticeMessage("无法连接到本地游戏服务器");
                    });
                    return;
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(lflist, rule, mode, duelRule,
                        noCheckDeck, noShuffleDeck,
                        startLp, startHand, drawCount, timeLimit,
                        roomName, password);
            } catch (Throwable t) {
                CrashHandler.getInstance().report("网络-建立主机", t);
                Log.e(TAG, "建立主机失败", t);
                engine.setState(GameEngine.GameState.DISCONNECTED);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onNoticeMessage("建立主机失败: " + t.getMessage());
                });
            }
        });
    }

    /**
     * 启动残局（对齐 gframe SingleMode::StartPlay）：不经局域网主机，由
     * {@link SingleModeRunner} 泵线程直驱本地引擎跑 ./single/xxx.lua，消息投喂实况管线。
     *
     * @param luaPath 残局脚本绝对路径（资源目录 single/ 下）
     * @param noShuffleToDeck 是否启用「不洗切时回卡组改为回顶端」标志：勾选时使用
     *        {@code OcgDuelEngine.DUEL_RETURN_DECK_TOP(0x80)} 而非默认 0
     */
    public void startSingleMode(String luaPath, boolean noShuffleToDeck) {
        Log.i(TAG, "Starting single mode: " + luaPath);
        engine.isBotMode = false;
        engine.isHost = false;
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage("正在加载残局...");
        });
        // 失败/终止路径（脚本缺失、引擎不可用等）由 runner 内部提示并回 IDLE
        engine.singleRunner.start(luaPath, noShuffleToDeck);
    }

    public void startBotDuel(String host, int port, String botCommand, String deckFile) {
        Log.i(TAG, "Starting bot duel via native WindBot: " + botCommand);
        engine.isBotMode = true;
        engine.setState(GameEngine.GameState.CONNECTING);

        spawn("BotDuel", "网络-启动人机", () -> {
            try {
                if (!ensureLocalServer(port, "人机模式")) {
                    return;
                }
                // start() 已同步完成 ServerSocket 绑定与 accept 线程拉起（引擎引导改在其后
                // 后台进行），环回连接可立即建立，不再需要固定缓冲等待

                boolean connected = engine.client.connect(host, port);
                if (!connected) {
                    engine.setState(GameEngine.GameState.DISCONNECTED);
                    engine.mainHandler.post(() -> {
                        if (engine.listener != null) engine.listener.onNoticeMessage("无法连接到本地游戏服务器"); });
                    return;
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(0, 0, 0, 5,
                        true, false,
                        8000, 5, 1, 0,
                        "Bot Duel", "");

                // 等房间就绪（房主已入座）再广播启动 WindBot，替代原固定 1500ms 缓冲：
                // 快设备即时启动，老设备最多轮询 10 秒（与 launchWindBot 同策略，
                // 20ms 细粒度），避免 WindBot 在房未建好时连入无房可加撞上探测超时
                LanGameServer server = localServer;
                long readyDeadline = System.currentTimeMillis() + 10000L;
                while (System.currentTimeMillis() < readyDeadline
                        && (server == null || !server.isRoomReady())) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        break;
                    }
                    server = localServer;
                }

                String windbotArgs = "WindBotHost:" + host + " Port:" + port
                        + " Name:WindBot"                    + (botCommand != null && !botCommand.isEmpty() ? " " + botCommand : "");
                Log.i(TAG, "Launching WindBot: " + windbotArgs);
                // 人机席位不实现 MSG_RELOAD_FIELD：预先标记本房间，服务端对它的撤回重同步改用整局重放
                server = localServer;
                if (server != null) {
                    server.markPeerNeedsReplayResync();
                }

                engine.mainHandler.post(() -> {
                    try {
                        Intent intent = new Intent();
                        intent.putExtra("args", windbotArgs);
                        intent.setAction("RUN_WINDBOT");
                        GameApplication.get().sendBroadcast(intent);
                        scheduleBotJoinTimeout();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to launch WindBot", e);
                        if (engine.listener != null) engine.listener.onNoticeMessage("启动AI失败: " + e.getMessage());
                    }
                });
            } catch (Throwable t) {
                CrashHandler.getInstance().report("网络-启动人机", t);
                Log.e(TAG, "启动人机失败", t);
                engine.setState(GameEngine.GameState.DISCONNECTED);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onNoticeMessage("启动人机失败: " + t.getMessage());
                });
            }
        });
    }

    /**
     * 启动 WindBot 连接到指定主机并加入房间。
     * 用于人机对战：本地已通过 startLocalServerWithSettings 建立主机后，
     * 让 AI 作为决斗位加入，主机端在 player waiting 页面即可看到其加入。
     *
     * @param botCommands 每个待拉起 AI 的 bot.conf 命令行（已含 Hand=1 等追加参数），下标即席位顺序
     * @param deckFiles   与 botCommands 一一对应的 AI 卡组覆盖路径，空串表示用 AI 内置卡组
     *                    （对应 WindBot 内部 Deck.Load(DeckFile ?? Executor.Deck)）。
     */
    public void launchWindBot(String host, int port, List<String> botCommands, List<String> deckFiles) {
        engine.isBotMode = true;
        final List<String> commands = (botCommands == null || botCommands.isEmpty())
                ? Collections.singletonList("") : botCommands;
        expectedBotSeats = commands.size();
        spawn("WindBotLauncher", "网络-启动WindBot", () -> {
            // 等待本地主机房间就绪再广播启动 WindBot：startLocalServerWithSettings 的
            // 引擎引导（ensureLocalServer）+ createGame 在另一线程完成，房间未建好时 AI 连入无房可加。
            // isRoomReady() 为权威信号（房间已建且房主已入座），accept 循环已随 start() 启动，
            // 故一旦就绪即可立即广播——采用细粒度轮询、去掉固定缓冲，使 AI 在房间就绪后尽快加入。
            LanGameServer server = localServer;
            long deadline = System.currentTimeMillis() + 10000L;
            while (System.currentTimeMillis() < deadline
                    && (server == null || !server.isRoomReady())) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    break;
                }
                server = localServer;
            }
            // WindBot.RunAndroid 以空格拆分参数(保留单引号片段)，再以 '=' 拆 key/value，故参数一律 Key=Value，
            // 含空格的值用单引号包裹；逐席参数由 buildWindBotArgs 拼装
            Log.i(TAG, "Launching " + commands.size() + " WindBot seats");
            // 同上：人机作为决斗位加入本房间，标记它们靠整局重放接受撤回后的重同步
            server = localServer;
            if (server != null) {
                server.markPeerNeedsReplayResync();
            }
            final int seats = commands.size();
            LanGameServer readyServer = server;
            // 逐席拉起：上一个 AI 入座后才启下一个，保证席位号 1..3 与列表复选顺序一一对应
            //（同时广播时三个实例的 TCP 接入会排队，先连到哪席不可控）；
            // 单席超时不再等，宁可序位错也不能不让 AI 进来
            for (int i = 0; i < seats; i++) {
                String deckFile = (deckFiles != null && i < deckFiles.size()) ? deckFiles.get(i) : "";
                String args = buildWindBotArgs(host, port, commands.get(i), deckFile, i);
                Log.i(TAG, "Launching WindBot seat " + (i + 1) + ": " + args);
                try {
                    final Intent intent = new Intent();
                    intent.putExtra("args", args);
                    intent.setAction("RUN_WINDBOT");
                    GameApplication.get().sendBroadcast(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch WindBot", e);
                    if (engine.listener != null) engine.listener.onNoticeMessage("启动AI失败: " + e.getMessage());
                }
                if (i < seats - 1) {
                    waitBotSeatJoined(readyServer, i + 1);
                }
            }
            engine.mainHandler.post(this::scheduleBotJoinTimeout);
        });
    }

    /** 等待本房间前 {@code botSeats} 个人机席位都已入座，最长 {@link #BOT_SEAT_ORDER_WAIT_MS}。 */
    private void waitBotSeatJoined(LanGameServer server, int botSeats) {
        if (server == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + BOT_SEAT_ORDER_WAIT_MS;
        while (System.currentTimeMillis() < deadline && !server.isBotSeatsJoined(botSeats)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
        }
        if (!server.isBotSeatsJoined(botSeats)) {
            Log.w(TAG, "AI 席位排队超时（等待 " + botSeats + " 席入座），继续拉起余下 AI");
        }
    }

    /** 组装单个 WindBot 实例的启动参数：Host/Port/Name 由本端给定，再拼 bot.conf 命令行与卡组覆盖 */
    private static String buildWindBotArgs(String host, int port, String botCommand, String deckFile, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("Host=").append(host)
          .append(" Port=").append(port)
          .append(" Name=WindBot").append(index == 0 ? "" : String.valueOf(index + 1));
        if (botCommand != null && !botCommand.isEmpty()) {
            sb.append(' ').append(botCommand);
        }
        if (deckFile != null && !deckFile.isEmpty()) {
            // DeckFile 覆盖 AI 默认卡组，作为本席位实际使用的卡组
            sb.append(" DeckFile='").append(deckFile).append('\'');
        }
        return sb.toString();
    }

    // ==== 录像回放控制 ====

    public void loadReplay(String replayPath) {
        Log.i(TAG, "Loading replay: " + replayPath);
        // 回放唯一入口：ReplayPlayer 作为本引擎协作件构造时就已完成接入（无需逐个 setEngine），
        // 取出的消息一律投喂本引擎实况管线，与观战同一渲染路径
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.replayPlayer.loadAndPlay(replayPath);
        engine.setState(GameEngine.GameState.DUELING);
    }

    public void pauseReplay() {
        engine.replayPlayer.pause();
    }

    public void resumeReplay() {
        engine.replayPlayer.resume();
    }

    public void stopReplay() {
        engine.replayPlayer.stop();
        engine.setState(GameEngine.GameState.IDLE);
    }

    public void skipReplayAhead() {
        engine.replayPlayer.skipAhead();
    }

    public void disconnect() {
        engine.singleRunner.stop();
        engine.client.disconnect();
        cancelBotJoinTimeout();
        if (engine.isHost) {
            LanGameServer server = localServer;
            if (server != null) {
                try {
                    server.stopServer();
                    Log.i(TAG, "Local game server stopped");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to stop local game server", e);
                }
            }
        }
        LanDiscoveryManager.releaseHostMulticastLock();
        engine.setState(GameEngine.GameState.DISCONNECTED);
    }
}

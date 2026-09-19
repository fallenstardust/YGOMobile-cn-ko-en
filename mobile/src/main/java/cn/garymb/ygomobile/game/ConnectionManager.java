package cn.garymb.ygomobile.game;

import android.content.Intent;
import android.util.Log;

import java.io.File;

import cn.garymb.ygomobile.GameApplication;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.network.server.LanGameServer;

/**
 * === Connection ===
 * 自 GameEngine 拆分而来：联机加入 / 本地房主 / 残局（single mode）/ WindBot 人机 /
 * 录像加载控制 / 断开的连接生命周期管理。
 */
public class ConnectionManager {
    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    /** 纯 Java 局域网主机（建主/残局/人机共用），懒启动、断线时停止。 */
    private LanGameServer localServer;

    public ConnectionManager(GameEngine engine) {
        this.engine = engine;
        this.botJoinTimeout = () -> {
            LanGameServer server = localServer;
            if (server == null || !server.isRunning()) {
                return;
            }
            if (!server.isSecondPlayerJoined() && engine.listener != null) {
                engine.listener.onHintMessage("AI 连接失败：WindBot 未在预期时间内加入");
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
                    engine.listener.onHintMessage(reason);
                }
            });
        }
        return started;
    }

    /** AI 加入超时（毫秒）：启动 WindBot 后若在此时长内第二玩家仍未入座则提示失败。 */
    private static final long BOT_JOIN_TIMEOUT_MS = 25000L;

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

    public void connectToServer(String host, int port, boolean createGame,
                                String roomName, String password,
                                int rule, int mode, int duelRule,
                                int startLp, int startHand, int drawCount, int timeLimit,
                                boolean noCheckDeck, boolean noShuffleDeck) {
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.isHost = createGame;
        engine.maxMatch = (mode == YGOProtocol.MODE_MATCH) ? 3 : 1;

        new Thread(() -> {
            boolean connected = engine.client.connect(host, port);
            if (!connected) {
                engine.setState(GameEngine.GameState.DISCONNECTED);
                return;
            }
            engine.client.sendExternalAddress(host);
            engine.client.sendPlayerInfo(engine.playerName);
            engine.client.sendJoinGame(0x1362, password);
        }, "GameConnect").start();
    }

    public void startLocalServer() {
        Log.i(TAG, "Starting local server...");
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.isHost = true;
        engine.maxMatch = 1;
        new Thread(() -> {
            try {
                if (!ensureLocalServer(7911, "局域网建主")) {
                    return;
                }
                LanDiscoveryManager.acquireHostMulticastLock();
                try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
                boolean connected = engine.client.connect("127.0.0.1", 7911);
                if (!connected) {
                    engine.setState(GameEngine.GameState.DISCONNECTED);
                    engine.mainHandler.post(() -> {
                        if (engine.listener != null) engine.listener.onHintMessage("无法连接到本地游戏服务器");
                    });
                    return;
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(0, 0, 0, 5,
                        false, false,
                        8000, 5, 1, 0,
                        "Local Game", "");
            } catch (Throwable t) {
                Log.e(TAG, "建立主机失败", t);
                engine.setState(GameEngine.GameState.DISCONNECTED);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onHintMessage("建立主机失败: " + t.getMessage());
                });
            }
        }, "LocalServer").start();
    }

    public void startLocalServerWithSettings(int lflist, int rule, int mode, int duelRule,
                                              boolean noCheckDeck, boolean noShuffleDeck,
                                              int startLp, int startHand, int drawCount, int timeLimit,
                                              String roomName, String password) {
        Log.i(TAG, "Starting local server with settings: " + roomName);
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.isHost = true;
        engine.maxMatch = (mode == YGOProtocol.MODE_MATCH) ? 3 : 1;
        new Thread(() -> {
            try {
                if (!ensureLocalServer(7911, "局域网建主")) {
                    return;
                }
                LanDiscoveryManager.acquireHostMulticastLock();
                try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
                boolean connected = engine.client.connect("127.0.0.1", 7911);
                if (!connected) {
                    engine.setState(GameEngine.GameState.DISCONNECTED);
                    engine.mainHandler.post(() -> {
                        if (engine.listener != null) engine.listener.onHintMessage("无法连接到本地游戏服务器");
                    });
                    return;
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(lflist, rule, mode, duelRule,
                        noCheckDeck, noShuffleDeck,
                        startLp, startHand, drawCount, timeLimit,
                        roomName, password);
            } catch (Throwable t) {
                Log.e(TAG, "建立主机失败", t);
                engine.setState(GameEngine.GameState.DISCONNECTED);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onHintMessage("建立主机失败: " + t.getMessage());
                });
            }
        }, "LocalServer").start();
    }

    public void startSingleMode(String luaPath) {
        Log.i(TAG, "Starting single mode: " + luaPath);
        byte[] scriptData = engine.scriptEngine.loadSingleScript(new File(luaPath).getName());
        if (scriptData == null || scriptData.length == 0) {
            Log.e(TAG, "Failed to load single mode script: " + luaPath);
            engine.setState(GameEngine.GameState.IDLE);
            engine.mainHandler.post(() -> {
                if (engine.listener != null) engine.listener.onHintMessage("无法加载残局脚本: " + new File(luaPath).getName());
            });
            return;
        }
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHintMessage("正在加载残局...");
        });
        engine.isBotMode = false;
        new Thread(() -> {
            try {
                if (!ensureLocalServer(7911, "残局模式")) {
                    return;
                }
                try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
                boolean connected = engine.client.connect("127.0.0.1", 7911);
                if (!connected) {
                    engine.setState(GameEngine.GameState.DISCONNECTED);
                    engine.mainHandler.post(() -> {
                        if (engine.listener != null) engine.listener.onHintMessage("无法连接到本地游戏服务器");
                    });
                    return;
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(0, 0, 1, 5,
                        true, false,
                        8000, 5, 1, 0,
                        "Single Play", "");
            } catch (Throwable t) {
                Log.e(TAG, "启动残局失败", t);
                engine.setState(GameEngine.GameState.DISCONNECTED);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onHintMessage("启动残局失败: " + t.getMessage());
                });
            }
        }, "SingleMode").start();
    }

    public void startBotDuel(String host, int port, String botCommand, String deckFile) {
        Log.i(TAG, "Starting bot duel via native WindBot: " + botCommand);
        engine.isBotMode = true;
        engine.setState(GameEngine.GameState.CONNECTING);

        new Thread(() -> {
            try {
                if (!ensureLocalServer(port, "人机模式")) {
                    return;
                }
                try { Thread.sleep(800); } catch (InterruptedException e) { /* ignore */ }

                boolean connected = engine.client.connect(host, port);
                if (!connected) {
                    engine.setState(GameEngine.GameState.DISCONNECTED);
                    engine.mainHandler.post(() -> {
                        if (engine.listener != null) engine.listener.onHintMessage("无法连接到本地游戏服务器"); });
                    return;
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(0, 0, 0, 5,
                        true, false,
                        8000, 5, 1, 0,
                        "Bot Duel", "");

                try { Thread.sleep(1500); } catch (InterruptedException e) { /* ignore */ }

                String windbotArgs = "WindBotHost:" + host + " Port:" + port
                        + " Name:WindBot"                    + (botCommand != null && !botCommand.isEmpty() ? " " + botCommand : "");
                Log.i(TAG, "Launching WindBot: " + windbotArgs);

                engine.mainHandler.post(() -> {
                    try {
                        Intent intent = new Intent();
                        intent.putExtra("args", windbotArgs);
                        intent.setAction("RUN_WINDBOT");
                        GameApplication.get().sendBroadcast(intent);
                        scheduleBotJoinTimeout();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to launch WindBot", e);
                        if (engine.listener != null) engine.listener.onHintMessage("启动AI失败: " + e.getMessage());
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "启动人机失败", t);
                engine.setState(GameEngine.GameState.DISCONNECTED);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onHintMessage("启动人机失败: " + t.getMessage());
                });
            }
        }, "BotDuel").start();
    }

    /**
     * 启动 WindBot 连接到指定主机并加入房间。
     * 用于人机对战：本地已通过 startLocalServerWithSettings 建立主机后，
     * 让 AI 作为第二名玩家加入，主机端在 player waiting 页面即可看到其加入。
     *
     * @param deckFile 为 P2(WindBot) 指定的卡组文件绝对路径；非空时通过 DeckFile 参数
     *                 覆盖 AI 自带卡组（对应 WindBot 内部 Deck.Load(DeckFile ?? Executor.Deck)）。
     */
    public void launchWindBot(String host, int port, String botCommand, String deckFile) {
        engine.isBotMode = true;
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException e) { /* ignore */ }
            // WindBot.RunAndroid 以空格拆分参数(保留单引号片段)，再以 '=' 拆 key/value。
            // 因此所有参数必须是 Key=Value 形式；含空格的值需用单引号包裹。
            StringBuilder sb = new StringBuilder();
            sb.append("Host=").append(host)
              .append(" Port=").append(port)
              .append(" Name=WindBot");
            if (botCommand != null && !botCommand.isEmpty()) {
                sb.append(' ').append(botCommand);
            }
            if (deckFile != null && !deckFile.isEmpty()) {
                // DeckFile 覆盖 AI 默认卡组，作为 P2 实际使用的卡组
                sb.append(" DeckFile='").append(deckFile).append('\'');
            }
            String windbotArgs = sb.toString();
            Log.i(TAG, "Launching WindBot: " + windbotArgs);
            engine.mainHandler.post(() -> {
                try {
                    Intent intent = new android.content.Intent();
                    intent.putExtra("args", windbotArgs);
                    intent.setAction("RUN_WINDBOT");
                    GameApplication.get().sendBroadcast(intent);
                    scheduleBotJoinTimeout();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch WindBot", e);
                    if (engine.listener != null) engine.listener.onHintMessage("启动AI失败: " + e.getMessage());
                }
            });
        }, "WindBotLauncher").start();
    }

    // ==== 录像回放控制 ====

    public void loadReplay(String replayPath) {
        Log.i(TAG, "Loading replay: " + replayPath);
        if (engine.replayEngine == null) {
            engine.replayEngine = new ReplayEngine(engine.field, engine.soundManager);
        }
        engine.setState(GameEngine.GameState.CONNECTING);
        engine.replayEngine.loadAndPlay(replayPath);
        engine.setState(GameEngine.GameState.DUELING);
    }

    public void pauseReplay() {
        if (engine.replayEngine != null) engine.replayEngine.pause();
    }

    public void resumeReplay() {
        if (engine.replayEngine != null) engine.replayEngine.resume();
    }

    public void stopReplay() {
        if (engine.replayEngine != null) engine.replayEngine.stop();
        engine.setState(GameEngine.GameState.IDLE);
    }

    public void skipReplayAhead() {
        if (engine.replayEngine != null) engine.replayEngine.skipAhead();
    }

    public void disconnect() {
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

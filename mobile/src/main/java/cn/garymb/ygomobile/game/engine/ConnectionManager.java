package cn.garymb.ygomobile.game.engine;
import cn.garymb.ygomobile.game.replay.ReplayPlayer;

import android.content.Intent;
import android.util.Log;


import java.io.File;
import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameApplication;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.network.server.LanGameServer;
import cn.garymb.ygomobile.utils.AiDeckUtil;
import cn.garymb.ygomobile.utils.CrashHandler;
import ocgcore.DataManager;

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

    // === 建立连接后的「等待 STOC_JOIN_GAME 回执」闸门（对齐 gframe duelclient.cpp）===
    /**
     * 加入他人主机时的回执上限：对齐 {@code StartClient} 里仅在 {@code !create_game} 时布下的
     * {@code connect_timeout_event}（{@code timeval timeout = {5, 0}}）；到点仍未收到
     * STOC_JOIN_GAME 即按连接失败处理（C++ {@code ConnectTimeout}：恢复按钮 + 回到 wLanWindow
     * + 弹 sys 1400）。
     */
    private static final long JOIN_ACK_TIMEOUT_MS = 5000L;
    /**
     * 本机建主（环回）的回执上限：C++ {@code BUTTON_HOST_CONFIRM} 不布超时（服务端就在本进程，
     * 失败会直接 return false），此处只作防御性兜底：端口已绑定但房间/环回迟迟不回执时，
     * 界面不得永久锁在“连接中”。旧设备引擎引导偏慢时也不能误报失败，故取明显宽于加入路径的 15s。
     */
    private static final long HOST_ACK_TIMEOUT_MS = 15000L;

    /** 本次连接是否仍在等 STOC_JOIN_GAME（对齐 C++ {@code connect_state} 未含 {@code CONNECT_STATE_JOINED}） */
    private volatile boolean awaitingJoinAck;
    /**
     * 本次入房是否已经把 TCP 连起来（对齐 C++ {@code connect_state} 已含 {@code CONNECTED}）：
     * 区分“始终连不上”（sys 1400）与“连上之后才断”（sys 1402）两类弹窗文案。
     */
    private volatile boolean joinTcpEstablished;

    /**
     * 回执看门狗：到点仍未收到 STOC_JOIN_GAME 即认定未能入房。文案与 C++
     * {@code ConnectTimeout} 同串号（sys 1400）；收尾走 {@link #abortConnect}（含停本机服务端）——
     * 调用方（MainMenuNavigator）据 DISCONNECTED 停在局域网/建主界面，不会进玩家等待界面。
     */
    private final Runnable joinAckTimeoutTask = new Runnable() {
        @Override
        public void run() {
            if (!awaitingJoinAck) return;
            Log.w(TAG, "join ack timeout: no STOC_JOIN_GAME received");
            abortConnect(1400, "无法连接到主机。");
        }
    };

    /** 停止本机内置服务端并释放组播锁（建主失败/超时收尾用），与 {@link #disconnect} 同一口径 */
    private void stopLocalServerQuietly() {
        LanGameServer server = localServer;
        if (server != null) {
            try {
                server.stopServer();
                Log.i(TAG, "Local game server stopped");
            } catch (Exception e) {
                Log.w(TAG, "Failed to stop local game server", e);
            }
        }
        LanDiscoveryManager.releaseHostMulticastLock();
    }

    /**
     * 建立连接阶段的统一收尾（主线程与连接工作线程均可调）：撤回执看门狗→本机建主时停本机
     * 服务端（对齐 C++ 失败分支的 {@code NetServer::StopServer()}，不留占着 7911 的空房间）
     * →关掉可能已建立的半截连接→置 DISCONNECTED（驱动界面停在局域网/建主窗口）。
     */
    private void teardownConnectAttempt() {
        notifyJoinAck();
        if (engine.isHost) {
            stopLocalServerQuietly();
        }
        engine.client.disconnect();
        engine.setState(GameEngine.GameState.DISCONNECTED);
    }

    /**
     * 网络原因的建立失败收尾：收尾后按 gframe 系统串弹错（与 C++ 同一故障同一串）。
     *
     * @param sysId gframe 系统串号，如 1400/1402/1412
     */
    private void abortConnect(int sysId, String fallback) {
        teardownConnectAttempt();
        notifyConnectFailed(sysId, fallback);
    }

    /**
     * 非网络故障（引擎异常、拉起人机失败等）的收尾：同样断连并置 DISCONNECTED，但知会直接用
     * 原文而不是系统串——这类原因在 gframe 里没有对应串号，不该硬塑成 1401/1402 一类网络文案。
     */
    private void abortWithReason(String reason) {
        teardownConnectAttempt();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage(reason);
        });
    }

    /**
     * 已建立 TCP但尚未收到 STOC_JOIN_GAME 时链路断开（主机重启/中途拔线）：按
     * {@code duelclient.cpp ClientEvent(BEV_EVENT_EOF|ERROR)} 中“已 CONNECTED 但未 JOINED”
     * 那一分支弹 sys 1402（网络传输发生错误。）并交给调用方停在原弹窗。
     *
     * <p>与 {@link #joinAckTimeoutTask} 的分工：定时器对应 C++ 的 {@code ConnectTimeout}（恒 1400），
     * 本入口对应真实收到断开事件（已连上后才是 1402）；两者只可能命中一个（都先清 {@code awaitingJoinAck}）。
     *
     * @return true 表示本次断开发生在入房阶段并已按上述弹错收尾
     */
    boolean onJoinLinkLost() {
        if (!awaitingJoinAck) {
            return false;
        }
        Log.w(TAG, "link lost before join ack: "
                + (joinTcpEstablished ? "connected then closed" : "never connected"));
        // 从未建立成功过仍按 1400（与 C++ connect_state == CONNECT_STATE_CONNECTING 同口径）
        abortConnect(joinTcpEstablished ? 1402 : 1400,
                joinTcpEstablished ? "网络传输发生错误。" : "无法连接到主机。");
        return true;
    }

    /** 发起连接前布下回执看门狗（主线程调用）；建主与加入用不同上限，见两个常量注释 */
    private void beginJoinWatch(boolean createGame) {
        engine.mainHandler.removeCallbacks(joinAckTimeoutTask);
        awaitingJoinAck = true;
        engine.mainHandler.postDelayed(joinAckTimeoutTask,
                createGame ? HOST_ACK_TIMEOUT_MS : JOIN_ACK_TIMEOUT_MS);
    }

    /** 收到 STOC_JOIN_GAME：连接已真正建立并入房，撤掉回执看门狗（{@code DuelStocHandler#onJoinGame} 调用） */
    public void notifyJoinAck() {
        awaitingJoinAck = false;
        joinTcpEstablished = false;
        engine.mainHandler.removeCallbacks(joinAckTimeoutTask);
    }

    /** 建立连接失败的统一知会（对齐 C++ {@code addMessageBox} + {@code PlaySoundEffect(SFX::INFO)}）：
     *  文案取 gframe 既有系统字符串，经 {@code onNoticeMessage} 弹「确定」询问窗 */
    private void notifyConnectFailed(int sysId, String fallback) {
        final String text = sysNotice(sysId, fallback);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage(text);
        });
    }

    /** 取 gframe 既有系统串作知会文案（卡表未加载/串号缺失时回退传入的中文兜底），供跨包的
     *  {@code DuelStocHandler} 同样复用，避免在两套代码里各写一套断线文案 */
    public String sysNotice(int sysId, String fallback) {
        try {
            return DataManager.get().getStringManager().getSystemString(sysId, fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

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
        // 聊天 “/ai 卡组名” 命令的拉起实现：仅本地建主才有 launcher，加入他人房间时命令由对方主机处理
        server.setChatBotLauncher(this::launchWindBotFromChat);
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
            // 弹错文案对齐 C++ BUTTON_HOST_CONFIRM 的 StartServer 失败分支（sys 1402），
            // 具体原因只写日志：同一种网络故障在两端不该看到两套不同的弹窗文案
            Log.w(TAG, "建立主机失败：决斗引擎不可用或端口被占用 (" + scene + ")");
            abortConnect(1402, "网络传输发生错误。");
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
        // 记下本次入房的地址与密码：对局中掉线时的重连靠它们重建同样的会话
        resumeHost = host;
        resumePort = port;
        resumePass = password != null ? password : "";
        // 连接已建立不等于已入房：界面得等到 STOC_JOIN_GAME 才进玩家等待界面，本看门狗
        // 保证 TCP 已连但服务端迟迟不回执（盘满/无房/卡死）时也能停在局域网界面报错
        beginJoinWatch(createGame);

        spawn("GameConnect", "网络-连接服务器", () -> {
            boolean connected = engine.client.connect(host, port);
            if (!connected) {
                // 对齐 C++ 两步：menu_handler.cpp L150-156 主机名解析不出地址弹 sys 1412；
                // 地址已解出但 StartClient/ConnectTimeout 连不上才弹 sys 1400（无法连接到主机。）
                if (engine.client.wasLastConnectResolveFailure()) {
                    abortConnect(1412, "无法解析主机地址。");
                } else {
                    abortConnect(1400, "无法连接到主机。");
                }
                return;
            }
            // 已 CONNECTED：此后断开按 C++ 同位置分支报 sys 1402，而不是 1400
            joinTcpEstablished = true;
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
        beginJoinWatch(true);
        spawn("LocalServer", "网络-局域网建主", () -> {
            try {
                if (!ensureLocalServer(7911, "局域网建主")) {
                    return;
                }
                LanDiscoveryManager.acquireHostMulticastLock();
                // start() 已同步完成 ServerSocket 绑定与 accept 线程拉起，无需额外等待即可环回连接
                boolean connected = engine.client.connect("127.0.0.1", 7911);
                if (!connected) {
                    // 对齐 C++ BUTTON_HOST_CONFIRM（menu_handler.cpp L194-199）：环回连接未能建立
                    // → 停服端 + 弹 sys 1402，不进等待界面（停服端已由收尾统一负责）
                    abortConnect(1402, "网络传输发生错误。");
                    return;
                }
                joinTcpEstablished = true;
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(0, 0, 0, 5,
                        false, false,
                        8000, 5, 1, 0,
                        "Local Game", "");
            } catch (Throwable t) {
                CrashHandler.getInstance().report("网络-建立主机", t);
                Log.e(TAG, "建立主机失败", t);
                stopLocalServerQuietly();
                abortWithReason("建立主机失败: " + t.getMessage());
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
        beginJoinWatch(true);
        spawn("LocalServer", "网络-局域网建主(自定义设置)", () -> {
            try {
                if (!ensureLocalServer(7911, "局域网建主")) {
                    return;
                }
                LanDiscoveryManager.acquireHostMulticastLock();
                // start() 已同步完成 ServerSocket 绑定与 accept 线程拉起，无需额外等待即可环回连接
                boolean connected = engine.client.connect("127.0.0.1", 7911);
                if (!connected) {
                    // 同上：建主后环回未能建立 → 弹 sys 1402（对齐 menu_handler.cpp L194-199），
                    // 界面停在建主界面；停本机服务端由 abortConnect 的收尾统一负责
                    abortConnect(1402, "网络传输发生错误。");
                    return;
                }
                joinTcpEstablished = true;
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendCreateGame(lflist, rule, mode, duelRule,
                        noCheckDeck, noShuffleDeck,
                        startLp, startHand, drawCount, timeLimit,
                        roomName, password);
            } catch (Throwable t) {
                CrashHandler.getInstance().report("网络-建立主机", t);
                Log.e(TAG, "建立主机失败", t);
                stopLocalServerQuietly();
                abortWithReason("建立主机失败: " + t.getMessage());
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
                    // 本机拉起人机时连不上自建服端：同样按 C++ 建主失败分支报 sys 1402
                    //（menu_handler.cpp L437-447 的 StartServer/StartClient 失败两处均为 1402）
                    abortConnect(1402, "网络传输发生错误。");
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
                abortWithReason("启动人机失败: " + t.getMessage());
            }
        });
    }

    /** 主机端收到聊天 “/ai [卡组名]” 后在本机拉起 WindBot 入房；卡组名为空时从合规人机卡组中随机挑选（加入者无 localServer，命令由对方主机处理）。 */
    private void launchWindBotFromChat(String deckName, int lflist) {
        final LanGameServer server = localServer;
        if (server == null || !server.isRunning()) return;
        final File aiDeckDir = new File(AppsSettings.get().getAiDeckDir());
        final String chosenDeck;
        if (deckName == null || deckName.isEmpty()) {
            // 纯 /ai：随机挑一个 bot.conf 有人机名且符合房间禁卡表的卡组
            File botConf = new File(AppsSettings.get().getResourcePath(), Constants.CORE_BOT_CONF_PATH);
            chosenDeck = AiDeckUtil.pickRandomDeckName(aiDeckDir, botConf, AiDeckUtil.resolveLimitList(lflist));
            if (chosenDeck == null) {
                Log.w(TAG, "/ai random: no compliant bot deck available");
                return;
            }
        } else {
            // 卡组名经聊天转发而来，只接受安全字符，避免借 “AI_<名>.ydk” 拼出目录穿越路径
            if (!deckName.matches("[A-Za-z0-9_.\\-]+")) {
                Log.w(TAG, "Rejected /ai deck name: " + deckName);
                return;
            }
            chosenDeck = deckName;
        }
        final File deckFile = new File(aiDeckDir, "AI_" + chosenDeck + Constants.YDK_FILE_EX);
        if (!deckFile.exists()) {
            Log.w(TAG, "/ai deck not found: " + deckFile.getAbsolutePath());
            return;
        }
        // 人机作为决斗位加入本房间：预先标记其靠整局重放接受撤回后的重同步（同 launchWindBot）
        server.markPeerNeedsReplayResync();
        // 参数格式同 buildWindBotArgs：Key=Value，含空格的路径用单引号包裹
        String args = "Host=127.0.0.1 Port=" + server.getPort()
                + " Name=WindBot DeckFile='" + deckFile.getAbsolutePath() + "'";
        try {
            Intent intent = new Intent();
            intent.putExtra("args", args);
            intent.setAction("RUN_WINDBOT");
            GameApplication.get().sendBroadcast(intent);
            Log.i(TAG, "Launching WindBot from /ai chat: " + args);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch WindBot from /ai", e);
        }
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

    // === 对局中链路中断的重连（用户规格：我方与主机通信超时/断开则重新请求，对方掉线则由服务端通知后显示对手重连）===
    /** 重连重试间隔（用户选定）：每 2s 试一次「connect + PLAYER_INFO + RESUME_GAME」。 */
    private static final long RESUME_RETRY_INTERVAL_MS = 2000L;
    /** 重连总时限（用户选定）：30s 内接不回原对局即按断线收尾（sys 1502）。 */
    private static final long RESUME_TOTAL_MS = 30000L;

    /** 本次入房用的主机地址/端口/房间密码，供掉线后原席恢复重连。 */
    private volatile String resumeHost;
    private volatile int resumePort;
    private volatile String resumePass = "";
    /** 本轮重连的截止时间（超过即放弃）。 */
    private volatile long resumeDeadlineMs;
    /**
     * 重连会话是否仍在进行：从 {@link #beginLinkResume} 起置真，直到接回成功（RESUME_ACK）或
     * 终止会话（{@link #abortResume}）才置假。
     * <p>不能用 {@code selfLinkLost} 代替：接回成功后它会被立即清掉，而多个并发尝试中某一条
     * 迟到的拒绝仍需被重连流程消费（按用户规格退出本局并回到局域网界面，不能只在场上弹个窗）。
     */
    private volatile boolean resumeInProgress;
    /**
     * 对局中各席位的链路状态（下标即协议席位号）：服务端用 STOC_PEER_LINK_STATE 逐席告知
     * 掉线/重连/离开，本地按「本方队/对方队」聚合后交给头像指示。
     */
    private final boolean[] seatLinkLost = new boolean[4];

    private final Runnable resumeRetryTask = new Runnable() {
        @Override
        public void run() {
            if (!engine.selfLinkLost) {
                return;  // 已接回或已收尾
            }
            if (System.currentTimeMillis() >= resumeDeadlineMs) {
                abortResume(1502, "连接已断开。");
                return;
            }
            tryResumeOnce();
            engine.mainHandler.postDelayed(this, RESUME_RETRY_INTERVAL_MS);
        }
    };

    /**
     * 本端与主机的链路在对局中中断时进入重连：不退界面、不清场，只在我方头像上显示重连中，
     * 并每 {@link #RESUME_RETRY_INTERVAL_MS} 重试一次接回原席位；累计 {@link #RESUME_TOTAL_MS}
     * 仍接不回则按 C++「决斗中断开」收尾（sys 1502 + 回局域网界面）。
     *
     * <p>触发源两处（用户选定的“应答无响应 + 套接字断开”）：{@code onDisconnected} 里的读线程/写失败
     * 收尾，与 {@code AskWatchdog} 求援用尽——后者才能发现“socket 看着还在但已半开”的靠掉包场景。
     *
     * <p>不适用本机的三种情况：本机建主（自己就是服务端，回环掉了等于本局作废）、回放/残局（无对端）、
     * 观战（重连也只是个新观战会话）。
     *
     * @return true 表示已进入重连（调用方不得再按断线退界面）
     */
    boolean beginLinkResume(String reason) {
        if (engine.isHost || engine.replayMode || engine.isSingleMode || engine.isSpectator()) {
            return false;
        }
        if (!engine.duelStarted && !engine.inDuel && !engine.siding) {
            return false;   // 还没开局，按原“入房失败”流程处理
        }
        final String host = resumeHost;
        final int port = resumePort;
        if (host == null || port <= 0 || engine.client.selfType < 0 || engine.client.selfType > 3) {
            return false;
        }
        if (engine.selfLinkLost) {
            return true;    // 已在重连中，不另开一轮
        }
        engine.selfLinkLost = true;
        resumeInProgress = true;
        resumeDeadlineMs = System.currentTimeMillis() + RESUME_TOTAL_MS;
        Log.w(TAG, "link lost during duel (" + reason + "): resume to " + host + ":" + port);
        notifyLinkState(0, true);
        tryResumeOnce();
        engine.mainHandler.removeCallbacks(resumeRetryTask);
        engine.mainHandler.postDelayed(resumeRetryTask, RESUME_RETRY_INTERVAL_MS);
        return true;
    }

    /**
     * 一次重连尝试：重新建 TCP → 报昵称 → CTOS_RESUME_GAME(原席位号, 原房间密码)。
     * 成功与否不在本线程判定——接回与否由服务端的 STOC_RESUME_ACK 在主线程序列里确认；
     * 不支持恢复的第三方主机既不会回包也不会报错，到时自然走 {@link #abortResume()}。
     */
    private void tryResumeOnce() {
        final String host = resumeHost;
        final int port = resumePort;
        final String pass = resumePass;
        final int seat = engine.client.selfType;
        if (host == null || port <= 0 || seat < 0) {
            return;
        }
        spawn("GameResume", "网络-断线重连", () -> {
            try {
                if (!engine.client.connect(host, port)) {
                    return;     // 连不上：等下一轮重试（不过早报错干扰用户）
                }
                engine.client.sendPlayerInfo(engine.playerName);
                engine.client.sendResumeGame(seat, pass);
            } catch (Throwable t) {
                Log.w(TAG, "resume attempt failed", t);
            }
        });
    }

    /**
     * 终止重连并直接结束本局会话（用户规格）：解除头像指示→收尾对局标志→{@code disconnect()}
     * （置 DISCONNECTED，由门面 {@code returnToLanMain} 收起决斗 UI 并重新显示隐藏的
     * LanModeDialog，同时回显上次的主机/端口/房间密码）→最后送达知会。
     *
     * <p>顺序要点：知会必须在 {@code disconnect()} 之后才发——否则它会先弹在决斗场区上，
     * 随后的导航收弹窗（{@code dismissOpenGameDialogs}）又把它关掉，玩家看不到原因；
     * 而 {@code setState} 对同值去重，先置 DISCONNECTED 再调 disconnect 反而会错过导航。
     *
     * @param sysId 知会串号：1502=重连超时，1405=主机拒绝接入
     */
    private void abortResume(int sysId, String fallback) {
        engine.selfLinkLost = false;
        resumeInProgress = false;
        engine.mainHandler.removeCallbacks(resumeRetryTask);
        notifyLinkState(0, false);
        Log.w(TAG, "resume aborted (sys " + sysId + ")");
        // 本局对本端已不可能继续：清掉对局标志，避免下一次会话里残留状态误触发重连
        engine.duelStarted = false;
        engine.inDuel = false;
        engine.siding = false;
        // 断线反馈由本方法的知会承担，DISCONNECTED 的返回流程不再重复 toast 同义文案
        engine.linkLossNoticeShown = true;
        engine.disconnect();
        notifyConnectFailed(sysId, fallback);
    }

    /** STOC_RESUME_ACK：重连成功，接回原对局。 */
    void onServerResumeAck(int seat, int turn, int currentPlayer, int phase) {
        engine.mainHandler.removeCallbacks(resumeRetryTask);
        engine.selfLinkLost = false;
        // 已接回：此后迟到的拒绝属于其它并发尝试的旧包，不再据此结束对局
        resumeInProgress = false;
        if (seat >= 0 && seat < seatLinkLost.length) {
            seatLinkLost[seat] = false;
        }
        notifyLinkState(0, false);
        Log.i(TAG, "resume ok: seat=" + seat + " turn=" + turn + " phase=0x"
                + Integer.toHexString(phase));
        // 服务端紧接着下发 reload + 各区 refresh：这三项真值需被重同步落地时消费（与撤回同源路径）
        engine.applyResumeAckState(turn, currentPlayer, phase);
    }

    /**
     * 重连期间收到 {@code ERRMSG_JOINERROR}（“主机拒绝了连接”）：说明服务端已不再保留本席位
     * （宽限期耗尽/房间已散），继续重试没有意义——按用户规格直接结束本局并回到局域网主界面
     * （隐藏中的 LanModeDialog 仍保留域名/端口/房间密码，由 {@code returnToLanMain} 回显）。
     * <p>必须走本入口而不是把错误原文报出去：每 2s 一次重试会反复命中拒绝，不拦就会按秒重弹。
     *
     * @return true 表示本错误已被重连流程消费（调用方不再另弹错）
     */
    boolean onLinkResumeRefused() {
        if (!resumeInProgress) {
            return false;
        }
        Log.w(TAG, "resume refused: server no longer holds this seat, leaving duel");
        // 文案按用户规格取 sys 1401（与 C++ duelclient.cpp L329 的 BEV_EVENT_EOF 同一串）：
        // 席位被拒等于这条会话已断，不该再向玩家区分“密码错/人满/被拒绝”这些入房阶段的原因
        abortResume(1401, "连接已断开。");
        return true;
    }

    /** STOC_PEER_LINK_STATE：某席位掉线/重连/宽限耗尽。按本地视角归到两侧头像之一。 */
    void onServerPeerLinkState(int seat, int state) {
        if (seat < 0 || seat >= seatLinkLost.length) {
            return;
        }
        seatLinkLost[seat] = state == YGOProtocol.LINK_STATE_RECONNECTING;
        final int side = linkSideOfSeat(seat);
        boolean lost = false;
        for (int i = 0; i < seatLinkLost.length; i++) {
            if (seatLinkLost[i] && linkSideOfSeat(i) == side) {
                lost = true;
            }
        }
        engine.peerLinkLost[side] = lost;
        notifyLinkState(side, lost);
    }

    /**
     * 席位属于本地视角那一队？与聊天分边同一口径（STOC_CHAT 的 chat_player_type 就是席位号）：
     * TAG 下 0/1 为本方队、2/3 为对方队，其余赛制只有两席，非己即人。
     * <p>观战与席位未定时本方队无从谈起，按席位号直接分左右（避免把 selfType=7 参比到
     * {@code >= 2} 判出反的一侧）。
     */
    private int linkSideOfSeat(int seat) {
        int self = engine.client.selfType;
        if (self < 0 || self >= 4) {
            return seat >= 2 ? 1 : 0;
        }
        return (seat >= 2) == (self >= 2) ? 0 : 1;
    }

    /** 把某一侧的链路指示送到主线程回调（side：0=我方含队友，1=对方）。 */
    private void notifyLinkState(final int side, final boolean lost) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onLinkStateChanged(side, lost);
        });
    }

    /**
     * 新一场连接/退出对局：终止重连尝试并全量作废链路指示，头像不留残影。
     * <p>两套数组长度不同、语义也不同，不能共用一个下标：{@code seatLinkLost} 按协议席位号（4 席），
     * {@code engine.peerLinkLost} 按本地视角两侧（2）——拿席位下标写两侧数组会在 TAG/新连接时
     * 抛 ArrayIndexOutOfBoundsException（length=2; index=2）。
     */
    void resetLinkStates() {
        engine.mainHandler.removeCallbacks(resumeRetryTask);
        engine.selfLinkLost = false;
        resumeInProgress = false;
        for (int i = 0; i < seatLinkLost.length; i++) {
            seatLinkLost[i] = false;
        }
        // 只在原本就处于重连中时补一次解除回调，避开回放/残局这类从不置位的会话白白创建叠层视图
        final boolean side0 = engine.peerLinkLost[0];
        final boolean side1 = engine.peerLinkLost[1];
        engine.peerLinkLost[0] = false;
        engine.peerLinkLost[1] = false;
        if (side0) notifyLinkState(0, false);
        if (side1) notifyLinkState(1, false);
    }

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
        // 用户主动断开同样作废尚未到期的回执看门狗，不再误报“无法连接到主机。”
        notifyJoinAck();
        // 主动退出同样终止重连与头像指示：不能把「重连中」残影带入下一次会话
        resetLinkStates();
        engine.client.disconnect();
        cancelBotJoinTimeout();
        if (engine.isHost) {
            stopLocalServerQuietly();
        } else {
            // 非主机侧断开时同样释放可能持有的组播锁（与重构前一致：释放无条件执行）
            LanDiscoveryManager.releaseHostMulticastLock();
        }
        engine.setState(GameEngine.GameState.DISCONNECTED);
    }
}

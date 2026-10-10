package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.render.SpecEffectOverlay;
import cn.garymb.ygomobile.render.TextureLoader;

/**
 * 聊天/弹幕/大厅/表情协作类（由 GameFieldController 按 // === 分栏拆分而来）：
 * 决斗内分侧玩家聊天、系统/观战消息弹幕、player waiting 大厅聊天模式、表情气泡。
 * 通过包级私有直连门面 GameFieldController 的共享状态（engine/topInfoManager/
 * mainHandler/layoutChatMessages/tvChatMessage1/2/layoutDanmaku）。
 * 对齐 gframe game.cpp AddChatMsg + drawing.cpp DrawChatMsg/DrawEmoticon。
 */
class FieldChatBoard {

    private final GameFieldController ctl;

    /** 每侧玩家聊天最大行数：超过 5 行向上滚动（清除第一条，最新一条落在最下行） */
    private static final int MAX_CHAT_LINES = 5;

    /**
     * 单条玩家聊天在屏存活时长（毫秒），超时后从最早一行开始自动清除——对齐
     * drawing.cpp DrawChatMsg：addChatMsg 置 chatTiming=1200，绘制每帧 chatTiming-- 至 0 即不再绘制，
     * 约 1200 帧 @60fps ≈ 20s。轮询周期 CHAT_EXPIRE_CHECK_MS 到点即移除已超时最旧行。
     */
    private static final long CHAT_LINE_LIFETIME_MS = 20000L;
    /** 聊天过期轮询周期（毫秒）：每 500ms 检查一次是否有最旧行超时需清除 */
    private static final long CHAT_EXPIRE_CHECK_MS = 500L;

    /** 一条带入场时间戳的血条下方聊天行（供按时间从最早行自动清除） */
    private static final class TimedLine {
        final String text;
        final long addedAt;
        TimedLine(String text, long addedAt) {
            this.text = text;
            this.addedAt = addedAt;
        }
    }

    private final LinkedList<TimedLine> myChatLines = new LinkedList<>();
    private final LinkedList<TimedLine> opChatLines = new LinkedList<>();

    /** 尚未落进血条下方聊天行容器的我方/对方消息（血条/覆盖层未就绪时暂存，
     *  容器可用后按旧→新顺序补挂；每侧暂存不超 MAX_CHAT_LINES 条） */
    private final LinkedList<TimedLine> myChatPending = new LinkedList<>();
    private final LinkedList<TimedLine> opChatPending = new LinkedList<>();

    /** 聊天按时间清除的轮询任务是否在排：保证只挂一个 postDelayed 链 */
    private boolean chatExpireScheduled = false;

    // 表情气泡：显示在发送方头像下方（对齐 gframe drawing.cpp DrawEmoticon），超时自动隐藏
    private static final long EMOTE_BUBBLE_DURATION_MS = 3000;
    ImageView ivPlayerEmoteBubble, ivOpponentEmoteBubble;
    private final Runnable hidePlayerEmoteBubble = () -> {
        if (ivPlayerEmoteBubble != null) ivPlayerEmoteBubble.setVisibility(View.GONE);
    };
    private final Runnable hideOpponentEmoteBubble = () -> {
        if (ivOpponentEmoteBubble != null) ivOpponentEmoteBubble.setVisibility(View.GONE);
    };

    // === 系统/观战消息弹幕（对齐 drawing.cpp DrawChatMsg chatType>=4 分支） ===

    /** 弹幕最大行数：屏幕顶部从上往下最多 3 行，超过后循环回第 1 行 */
    private static final int DANMAKU_MAX_ROWS = 3;
    /** 弹幕匀速（dp/ms）：时长 = 总路程 / 速度，所有消息速度一致；
     *  0.04（约 14 秒横滚一屏，与 gframe 1200帧×4px 节奏更接近） */
    private static final float DANMAKU_SPEED_DP_PER_MS = 0.04f;
    /** 首帧未布局时的最大重试次数（每次 post 约一帧，≈ 2 秒），防止窗口 token
     *  不可用时无限 post 黑洞堆消息 */
    private static final int DANMAKU_MAX_LAYOUT_RETRIES = 120;
    /** 弹幕行高（dp）：3 行带总高约 48dp，贴屏幕顶部自上而下排列 */
    private static final float DANMAKU_ROW_HEIGHT_DP = 16f;
    /** 观战弹幕颜色，逐一对齐 drawing.cpp chatColor[11..19]（11=红 12=绿 13=蓝 14=青 15=品红 16=黄 17=白 18=灰 19=深灰） */
    private static final int[] DANMAKU_OBS_COLORS = {
            YGOUtil.c(R.color.chat_color_red), YGOUtil.c(R.color.chat_color_green),
            YGOUtil.c(R.color.chat_color_blue), YGOUtil.c(R.color.chat_color_cyan),
            YGOUtil.c(R.color.chat_color_magenta), YGOUtil.c(R.color.chat_color_yellow),
            YGOUtil.c(R.color.white), YGOUtil.c(R.color.chat_color_gray),
            YGOUtil.c(R.color.chat_color_dark_gray)
    };
    /** 聊天消息半透明黑底（对齐 drawing.cpp L1597 draw2DRectangle 0xa0000000） */
    private static final int CHAT_BG_COLOR = YGOUtil.c(R.color.black_a0);

    private int danmakuRowIndex = 0;
    private final List<TextView> danmakuViews = new ArrayList<>();

    /** 大厅（player waiting）聊天模式：全部消息在 layout_danmaku 静态列表显示 */
    private boolean lobbyChatMode = false;
    /** 大厅聊天最大条数：超过时移除最上方最旧的一条 */
    private static final int MAX_LOBBY_CHAT_LINES = 10;
    /** 大厅聊天列表容器：每条消息一个 TextView（独立半透明黑底），旧→新从上往下排列 */
    private LinearLayout lobbyChatContainer;
    /**
     * 决斗开始前（等待界面尚未弹出、lobbyChatMode 未置位）收到的非玩家消息缓存：
     * 内置服务端把「XX 加入了房间」的 type=8 广播发在 STOC_JOIN_GAME 回执之前
     *（GameRoom.joinGame），新客机必然先收到它、后进入大厅模式；旧实现此时把它当
     * 决斗弹幕处理，随后 enterLobbyChatMode 的 clearDanmaku 又将其抹掉——客机永远
     * 看不到主机系统消息的第二重根因。对齐 drawing.cpp DrawChatMsg：!dInfo.isStarted
     * 时全部 chatMsg（含 8/9/10/11-19）均以静态纵列逐行绘制，故本缓存按到达顺序在
     * enterLobbyChatMode 就绪后回灌 appendLobbyChat，消息不丢。
     */
    private static final class PendingLobbyMsg {
        final int type;
        final String text;
        PendingLobbyMsg(int type, String text) {
            this.type = type;
            this.text = text;
        }
    }
    private final LinkedList<PendingLobbyMsg> pendingLobbyMsgs = new LinkedList<>();

    FieldChatBoard(GameFieldController ctl) {
        this.ctl = ctl;
    }

    // === 聊天消息（对齐 gframe game.cpp AddChatMsg + drawing.cpp DrawChatMsg） ===

    void appendChat(int playerType, String message) {
        AppsSettings settings = AppsSettings.get();
        if (message == null) message = "";
        // 严格对齐 gframe duelclient.cpp STOC_CHAT 对 chat_player_type 的门控分组：
        // · 0-3 决斗座位与 8 系统消息受 chkIgnore1（本端 chkDisableChatting）管辖；
        // · 11-19 观战编号不受任何屏蔽开关管辖（原样透传，弹幕用观战配色）；
        // · 其余全部非玩家类型（4-7/9/10/20+，含内置服务端的观战 type=7）受 chkIgnore2
        //   （本端 chkMuteSpectators）管辖，通过门控后归一为 10，显示为隐藏名「[********]: 」。
        // 历史缺陷修复点①：旧实现在决斗态先用 chkDisableChatting 丢弃“全部”消息，
        // 系统/观战消息被一并吞掉；②：观战发言 type=7 既不落 11-19 判定区间，也无归一
        // 分支，颜色与前缀都不对；③：服务端从不下发 8/9/10，故这些分支永不触发
        //（现由 GameRoom.broadcastSystemChat 在进入/离开/被踢事件点生产 type=8）。
        boolean ignorePlayerOrSystem = settings.getIntSettings("chkDisableChatting", 0) == 1;
        boolean ignoreSpectator = settings.getIntSettings("chkMuteSpectators", 0) == 1;
        int showType = playerType;
        if (playerType < 4) {
            if (ignorePlayerOrSystem) return;
        } else if (playerType == 8) {
            if (ignorePlayerOrSystem) {
                android.util.Log.d("ChatRoute", "appendChat drop: type=8 blocked by chkDisableChatting");
                return;
            }
        } else if (playerType >= 11 && playerType <= 19) {
            // 观战编号：原样透传，由 showChatDanmaku 按 DANMAKU_OBS_COLORS 着色
        } else {
            if (ignoreSpectator) {
                android.util.Log.d("ChatRoute", "appendChat drop: type=" + playerType
                        + " blocked by chkMuteSpectators (归一10路径)");
                return;
            }
            showType = 10;                        // 对齐 gframe：归一为隐藏名桶
        }
        // 大厅（player waiting）模式：全部消息——玩家(0-3)与系统/脚本错误/隐藏名/
        // 观战(8/9/10/11-19，含归一后的 7)——一律进 lobbyChatContainer 静态纵列，
        // 严格对齐 drawing.cpp DrawChatMsg 的 !dInfo.isStarted 分支：等待界面下所有
        // chatType 都在 wHostPrepare 位置逐行静态绘制，只有 isStarted 且 chatType>=4
        // 才走 offsetX 横向弹幕（且其带顶 y=10 贴玩家信息区，对应本端血条下方锚定，
        // 绝不得锚屏幕正中——决斗态正中区域被特效大图/弹窗层覆盖，弹幕穿了会“看不见”）。
        if (lobbyChatMode) {
            appendLobbyChat(showType, message);
            return;
        }
        // 决斗尚未开始（连接中/等待界面未弹出）：非玩家消息不进弹幕，缓存待
        // enterLobbyChatMode 回灌静态列表；玩家消息(0-3)维持原路径不受影响
        if (playerType >= 4 && !duelUiStarted()) {
            pendingLobbyMsgs.addLast(new PendingLobbyMsg(showType, message));
            while (pendingLobbyMsgs.size() > MAX_LOBBY_CHAT_LINES) {
                pendingLobbyMsgs.removeFirst();
            }
            return;
        }
        if (playerType >= 0 && playerType < 4) {
            // 玩家消息（座位号：0/1 我方队首+tag，2/3 对方队首+tag）：
            // 表情编码不走文字行，在发送方头像下方显示图片气泡（对齐 gframe DrawEmoticon）
            if (isEmoticonCode(message)) {
                showEmoteBubble(playerType, message);
                return;
            }
            // 发言中内嵌 &xxx 表情码（如对方发“好的&laugh”）：提取出码转表情气泡，
            // 剩余文字照常入聊天行；剥离后无剩余文本则只显示气泡不显示空行
            StringBuilder textSb = new StringBuilder();
            List<String> embeddedCodes = extractEmbeddedEmoticonCodes(message, textSb);
            if (!embeddedCodes.isEmpty()) {
                for (String code : embeddedCodes) showEmoteBubble(playerType, code);
                message = textSb.toString().trim();
                if (message.isEmpty()) return;
            }
            // 我方（含我方 tag 同伴）→ tv_chat_message_1；对方（含对方 tag）→ tv_chat_message_2。
            // 先按 gframe ChatLocalPlayer 把发送方决斗序号转为命名槽位 chatType，
            // 再据 chatType 分边与取名——修复后攻时对方消息被拼上我方昵称的错位。
            // 分边：chatType∈{0,2}=我方队（左）、{1,3}=对方队（右）（对齐 AddChatMsg L2325 player==0||2）。
            int chatType = chatLocalType(playerType);
            boolean selfSide = (chatType == 0 || chatType == 2);
            appendSideChat(selfSide, chatNameByLocalType(chatType) + ": " + message);
        } else {
            // 系统/脚本错误/观战消息：门控与归一已在方法开头按 gframe 分组完成，此处只渲染
            showChatDanmaku(showType, message);
        }
    }

    /** 聊天昵称：优先 STOC_HS_PLAYER_ENTER 记录的座位名（对齐 game.cpp AddChatMsg 的昵称前缀） */
    private String chatNickname(int seat) {
        if (ctl.engine != null && seat >= 0 && seat < ctl.engine.seatNames.length) {
            String name = ctl.engine.seatNames[seat];
            if (name != null && !name.isEmpty()) return name;
        }
        if (ctl.engine != null && ctl.engine.getClient() != null
                && seat == ctl.engine.getClient().selfType) {
            return ctl.engine.getPlayerName();
        }
        return "Player" + (seat + 1);
    }

    /** 对齐 gframe OppositePlayer：tag 翻队伍位(0x2)、1v1 翻单边位(0x1) */
    private int oppositeChatPlayer(int player, boolean isTag) {
        int sideBit = isTag ? 0x2 : 0x1;
        return player ^ sideBit;
    }

    /**
     * 复刻 gframe game.cpp ChatLocalPlayer——把 STOC_CHAT 的发送方座位号（对局内为决斗序号
     * dp->type）转换为 AddChatMsg 用于命名槽位与分边的 chatType（0-3）。分边规则：chatType∈{0,2}=
     * 我方队（左）、{1,3}=对方队（右）（对齐 AddChatMsg L2325 player==0||2）。不返回 is_self 位
     * （本端口音效在引擎层统一处理）。
     */
    private int chatLocalType(int player) {
        if (player > 3) return player;
        if (ctl.engine == null) return player;
        boolean isTag = ctl.engine.getGameMode() == 2;
        int selftype = ctl.engine.getSelfType();
        if (ctl.engine.replayMode) {
            // 回放：0xF1 聊天帧 body[0] 恒为协议侧发送者座位（绝对值），分边只随先攻视角走——
            // duelIsFirst 由 ReplayPlayer.performSwapField 翻转（playerInfos 昵称已同步对调），
            // 映射式与实况对局内分支一致；不走 isStarted 门是因为回放恒不置 duelStarted/inDuel
            // （ReplayPlayer.startSession 依 C++ 回放态不置 dInfo.isStarted），修复切视角后聊天
            // 消息漂移到另一侧血条下（用户反馈：聊天记录切换视角后变成另一方发送）
            player = ctl.engine.isDuelFirst() ? player : oppositeChatPlayer(player, isTag);
        } else if (ctl.engine.isStarted() || ctl.engine.isSiding()) {
            if (ctl.engine.isInDuel()) {
                // 对局中：按先攻判定是否需要换边
                player = ctl.engine.isDuelFirst() ? player : oppositeChatPlayer(player, isTag);
            } else {
                // 换备卡 / 等待猜拳结果：按原始座位边界换边
                int selftypeBoundary = isTag ? 2 : 1;
                if (selftype >= selftypeBoundary && selftype < 4)
                    player = oppositeChatPlayer(player, isTag);
            }
        }
        // tag 座位 1<->2 互换（对齐 ChatLocalPlayer 末尾，无论对局内外均执行）
        if (isTag && (player == 1 || player == 2)) {
            player = 3 - player;
        }
        return player;
    }

    /**
     * chatType（0-3）→ 原始大厅座位 → seatNames 昵称。复刻 duelclient.cpp STOC_DUEL_START 对
     * hostname/clientname/hostname_tag/clientname_tag 四个槽位的赋值
     * （chatType0→hostname、1→clientname、2→hostname_tag、3→clientname_tag），修复后攻时命名错位。
     */
    private String chatNameByLocalType(int chatType) {
        if (ctl.engine == null) return "Player" + (chatType + 1);
        // 回放：录像不经 STOC_HS_PLAYER_ENTER，seatNames 恒空，昵称改取 playerInfos——
        // 其名字由 ReplayPlayer.startSession 从录像头部（等价于通讯下发的玩家名）写入，
        // 左右侧映射 chatType0/2=左(playerInfos[0])、1/3=右(playerInfos[1])；chatType2/3 为该队
        // tag 队友，优先取 nameTag（对齐 hostname_tag/clientname_tag）。修复切横竖屏/重渲染后
        // 聊天前缀退化为 Player1/Player2（用户反馈：玩家名称丢失只显示默认名）。
        if (ctl.engine.replayMode) {
            int localIdx = (chatType == 0 || chatType == 2) ? 0 : 1;
            GameEngine.PlayerInfo info = ctl.engine.playerInfos[localIdx];
            String rn = (chatType >= 2 && info.nameTag != null && !info.nameTag.isEmpty())
                    ? info.nameTag : info.name;
            if (rn != null && !rn.isEmpty()) return rn;
        }
        boolean isTag = ctl.engine.getGameMode() == 2;
        int selftype = ctl.engine.getSelfType();
        int origSeat;
        if (!isTag) {
            // selftype!=1：hostname←seat0、clientname←seat1；selftype==1：hostname←seat1、clientname←seat0
            origSeat = (selftype != 1) ? chatType : (chatType == 0 ? 1 : 0);
        } else if (selftype > 1 && selftype < 4) {
            // hostname←seat2、clientname←seat0、hostname_tag←seat3、clientname_tag←seat1
            switch (chatType) {
                case 0: origSeat = 2; break;
                case 1: origSeat = 0; break;
                case 2: origSeat = 3; break;
                default: origSeat = 1; break;
            }
        } else {
            // hostname←seat0、clientname←seat2、hostname_tag←seat1、clientname_tag←seat3
            switch (chatType) {
                case 0: origSeat = 0; break;
                case 1: origSeat = 2; break;
                case 2: origSeat = 1; break;
                default: origSeat = 3; break;
            }
        }
        if (origSeat >= 0 && origSeat < ctl.engine.seatNames.length) {
            String name = ctl.engine.seatNames[origSeat];
            if (name != null && !name.isEmpty()) return name;
        }
        if (origSeat == selftype) {
            return ctl.engine.getPlayerName();
        }
        return "Player" + (origSeat + 1);
    }

    /**
     * 我方/对方侧玩家聊天（含同队 tag 队友）：对局玩家聊天不是弹幕——在发送方
     * LP 血条正下方自上而下逐行显示（我方队→我方血条下、对方队→对方血条下，
     * 横竖屏同规格），最多 MAX_CHAT_LINES 行、超出移除最上方最旧一条（等效上滚）。
     * 容器由 SpecEffectOverlay 的 PopupWindow 承载（在 GL 曲面之上；tv_chat_message_1/2
     * 是普通 View，会被 setZOrderOnTop(true) 的 GameFieldView 整层遮挡，历史根因），
     * 颜色对齐 drawing.cpp chatColor[0..3]（玩家消息白色），前缀已由调用方拼好「昵称: 内容」。
     * 仅系统/观战消息在 showChatDanmaku 中以弹幕形式横向滚动。
     */
    private void appendSideChat(boolean selfSide, String line) {
        TimedLine entry = new TimedLine(line, System.currentTimeMillis());
        LinkedList<TimedLine> lines = selfSide ? myChatLines : opChatLines;
        lines.addLast(entry);
        while (lines.size() > MAX_CHAT_LINES) {
            lines.removeFirst();
        }
        LinkedList<TimedLine> pending = selfSide ? myChatPending : opChatPending;
        pending.addLast(entry);
        while (pending.size() > MAX_CHAT_LINES) {
            pending.removeFirst();
        }
        rebuildSideChatLayer(selfSide, 0);
        // 启动/维持按时间从最早行自动清除的轮询（对齐 drawing.cpp chatTiming 逐帧倒计时清除）
        scheduleChatExpire();
    }

    /** 安排/维持聊天按时间清除的轮询：每 CHAT_EXPIRE_CHECK_MS 移除超时最旧行，两侧均空时停止。 */
    private void scheduleChatExpire() {
        if (chatExpireScheduled) return;
        chatExpireScheduled = true;
        ctl.mainHandler.postDelayed(chatExpireTask, CHAT_EXPIRE_CHECK_MS);
    }

    private final Runnable chatExpireTask = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            boolean changed = trimExpiredChat(myChatLines, myChatPending, now);
            changed |= trimExpiredChat(opChatLines, opChatPending, now);
            if (changed) {
                // 有行超时：整列重建两侧血条下方聊天容器，使被移除的最旧行同步离场
                relayoutSideChatRows(true, 0);
                relayoutSideChatRows(false, 0);
            }
            if (!myChatLines.isEmpty() || !opChatLines.isEmpty()) {
                ctl.mainHandler.postDelayed(this, CHAT_EXPIRE_CHECK_MS);
            } else {
                chatExpireScheduled = false;
            }
        }
    };

    /** 移除 lines/pending 队首已超时（addedAt + 存活时长 ≤ now）的行，返回 lines 是否有移除。 */
    private static boolean trimExpiredChat(LinkedList<TimedLine> lines, LinkedList<TimedLine> pending, long now) {
        boolean removed = false;
        while (!lines.isEmpty() && now - lines.peekFirst().addedAt >= CHAT_LINE_LIFETIME_MS) {
            lines.removeFirst();
            removed = true;
        }
        while (!pending.isEmpty() && now - pending.peekFirst().addedAt >= CHAT_LINE_LIFETIME_MS) {
            pending.removeFirst();
        }
        return removed;
    }

    /** 重建指定侧聊天行容器：把 pending 消息自上而下追加到该侧血条正下方；
     *  血条尚未布局时经 mainHandler 延后重试（上限同弹幕），重试用尽/容器不可用
     *  则回退为弹幕带显示，不丢消息 */
    private void rebuildSideChatLayer(boolean selfSide, int retries) {
        SpecEffectOverlay overlay = ctl.activity.obtainSpecOverlay();
        if (overlay == null) {
            flushSideChatPendingToDanmaku(selfSide);
            return;
        }
        int[] bar = ctl.topInfoManager != null
                ? ctl.topInfoManager.getLpBarRectInWindow(selfSide ? 0 : 1) : null;
        if (bar == null || bar[2] <= 0 || bar[3] <= 0) {
            if (retries < DANMAKU_MAX_LAYOUT_RETRIES) {
                final int next = retries + 1;
                ctl.mainHandler.post(() -> rebuildSideChatLayer(selfSide, next));
            } else {
                flushSideChatPendingToDanmaku(selfSide);
            }
            return;
        }
        LinearLayout layer = overlay.obtainChatRowLayer(
                selfSide, bar[0], bar[1] + bar[3], bar[2]);
        if (layer == null) {
            flushSideChatPendingToDanmaku(selfSide);
            return;
        }
        LinkedList<TimedLine> pending = selfSide ? myChatPending : opChatPending;
        if (pending.isEmpty()) return;
        for (TimedLine entry : pending) {
            layer.addView(createChatRowView(entry));
        }
        pending.clear();
        // 在屏不超 MAX_CHAT_LINES 行：超出时最上方最旧一条消失（等效上滚）
        while (layer.getChildCount() > MAX_CHAT_LINES) {
            layer.removeViewAt(0);
        }
    }

    /** 构造一条血条下方聊天行 TextView（昵称前缀已由调用方拼好入 line）：9sp 白字 + 半透明黑底，
     *  规格对齐 drawing.cpp chatColor[0..3] 玩家消息白色 / draw2DRectangle 0xa0000000 */
    private TextView createChatRowView(TimedLine entry) {
        float density = ctl.activity.getResources().getDisplayMetrics().density;
        TextView tv = new TextView(ctl.activity);
        tv.setText(entry.text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);   // 与系统弹幕/大厅聊天统一 9sp
        tv.setTextColor(YGOUtil.c(R.color.white));                     // chatColor[0..3] 玩家消息白色
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setBackgroundColor(CHAT_BG_COLOR);            // 半透明黑底，对齐 drawing.cpp 0xa0000000
        int hPadding = (int) (3 * density);
        tv.setPadding(hPadding, 0, hPadding, 0);
        tv.setShadowLayer(1f, 1f, 1f, YGOUtil.c(R.color.black));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (2 * density);
        tv.setLayoutParams(lp);
        return tv;
    }

    /**
     * 整列重建指定侧血条下方聊天容器：清空后按该侧 myChatLines/opChatLines 全量重挂，
     * 用真实数据（非仅 pending）渲染——视角切换左右对调、旋转重建回灌共用。
     * 血条未布局时经 mainHandler 延后重试（上限同弹幕），容器不可用则静默跳过（数据不丢）。
     */
    private void relayoutSideChatRows(boolean selfSide, int retries) {
        SpecEffectOverlay overlay = ctl.activity.obtainSpecOverlay();
        if (overlay == null) return;
        int[] bar = ctl.topInfoManager != null
                ? ctl.topInfoManager.getLpBarRectInWindow(selfSide ? 0 : 1) : null;
        if (bar == null || bar[2] <= 0 || bar[3] <= 0) {
            if (retries < DANMAKU_MAX_LAYOUT_RETRIES) {
                final int next = retries + 1;
                ctl.mainHandler.post(() -> relayoutSideChatRows(selfSide, next));
            }
            return;
        }
        LinearLayout layer = overlay.obtainChatRowLayer(
                selfSide, bar[0], bar[1] + bar[3], bar[2]);
        if (layer == null) return;
        layer.removeAllViews();
        LinkedList<TimedLine> lines = selfSide ? myChatLines : opChatLines;
        for (TimedLine entry : lines) layer.addView(createChatRowView(entry));
        // lines 已含全部在屏消息（pending 与 lines 同源），全量重挂后清 pending 防重复补挂
        (selfSide ? myChatPending : opChatPending).clear();
    }

    /**
     * 切换视角（观战/录像 ReplaySwap）时左右对调双方聊天内容：仅对调 gametopinfo 昵称会让
     * player1 的消息错显示到 player2 一侧，故连同两侧聊天行一并对调，再按新视角整列重挂。
     * 聊天行文本内已内嵌发送者昵称前缀，整行随左右搬迁即与对调后的玩家名天然对齐。
     */
    void swapChatSides() {
        swapListContent(myChatLines, opChatLines);
        swapListContent(myChatPending, opChatPending);
        relayoutSideChatRows(true, 0);
        relayoutSideChatRows(false, 0);
    }

    private static <T> void swapListContent(LinkedList<T> a, LinkedList<T> b) {
        java.util.List<T> tmp = new ArrayList<>(a);
        a.clear();
        a.addAll(b);
        b.clear();
        b.addAll(tmp);
    }

    /**
     * 屏幕旋转（rebindAfterRotation 复用同一实例）后回灌双方聊天：覆盖层/回退层旧视图随旋转销毁，
     * 撤销本类持有的弹幕视图引用与暂存（不丢 myChatLines/opChatLines 聊天历史），
     * 再把保存的双方聊天行整列重挂到新覆盖层，避免横竖屏切换后聊天记录丢失（用户反馈）。
     */
    void retainAcrossRotation() {
        for (TextView tv : danmakuViews) {
            tv.animate().cancel();
        }
        danmakuViews.clear();
        danmakuRowIndex = 0;
        myChatPending.clear();
        opChatPending.clear();
        // 大厅聊天容器挂在旧 layout_danmaku 上，重建后失效：置空待下次 enterLobbyChatMode 重建
        lobbyChatContainer = null;
        if (!lobbyChatMode) {
            relayoutSideChatRows(true, 0);
            relayoutSideChatRows(false, 0);
        }
    }

    /** 聊天行容器不可用（无覆盖层/重试用尽）时，把未落容器的消息回退为弹幕显示，不丢消息 */
    private void flushSideChatPendingToDanmaku(boolean selfSide) {
        LinkedList<TimedLine> pending = selfSide ? myChatPending : opChatPending;
        if (pending.isEmpty()) return;
        for (TimedLine entry : pending) showDanmakuLine(entry.text, YGOUtil.c(R.color.white), 0);
        pending.clear();
    }

    /** 清除全部进行中的弹幕与分侧聊天行（停止聊天/离开决斗界面时调用）：从当前宿主容器
     *  （drawspec 弹幕层/聊天行层或回退的 layout_danmaku）移除，并通知覆盖层空闲收口
     *  （无特效时关闭 PopupWindow） */
    private void clearDanmaku() {
        // 停止聊天/离开决斗/进大厅：取消按时间清除的轮询链，避免残留 postDelayed
        ctl.mainHandler.removeCallbacks(chatExpireTask);
        chatExpireScheduled = false;
        myChatPending.clear();
        opChatPending.clear();
        for (TextView tv : danmakuViews) {
            tv.animate().cancel();
            Object p = tv.getParent();
            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(tv);
            else if (ctl.layoutDanmaku != null) ctl.layoutDanmaku.removeView(tv);
        }
        danmakuViews.clear();
        danmakuRowIndex = 0;
        SpecEffectOverlay overlay = ctl.activity.getSpecOverlay();
        if (overlay != null) overlay.clearChatRowLayers();
    }

    /**
     * 决斗界面是否已进入（对齐 C++ dInfo.isStarted 语义）：仅 HAND_SELECT 起算决斗态，
     * 此时非玩家消息才属于弹幕横滚；IDLE/CONNECTING/LOBBY（含等待界面弹出前的窗口期）
     * 一律缓存进 pendingLobbyMsgs，由 enterLobbyChatMode 回灌大厅静态列表。
     */
    private boolean duelUiStarted() {
        if (ctl.engine == null) return false;
        switch (ctl.engine.getState()) {
            case HAND_SELECT:
            case TP_SELECT:
            case DUELING:
            case SIDING:
            case DUEL_END:
                return true;
            default:
                return false;
        }
    }

    // === player waiting 大厅聊天模式 ===

    /**
     * 进入大厅聊天模式：全部消息在 layout_danmaku 中按
     * 从上往下、旧到新的静态列表显示（每条一个 TextView、半透明黑底），
     * 最多 10 条，超出移除最上方最旧的一条；系统/观战消息(8/9/10/11-19)与玩家消息
     * 同列表渲染（对齐 drawing.cpp !dInfo.isStarted 的全类型静态纵列），不再走
     * 会被 PlayerWaitingDialog 盖住的 drawspec 弹幕带；
     * 等待界面弹出前先行到达的系统消息（加入房间广播）在此按序回灌；
     * 决斗内分侧聊天（tv_chat_message_1/2）与弹幕滚动在此期间停用
     */
    void enterLobbyChatMode() {
        lobbyChatMode = true;
        clearDanmaku();
        if (ctl.layoutChatMessages != null) ctl.layoutChatMessages.setVisibility(View.GONE);
        if (ctl.layoutDanmaku == null) return;
        if (lobbyChatContainer == null) {
            lobbyChatContainer = new LinearLayout(ctl.activity);
            lobbyChatContainer.setOrientation(LinearLayout.VERTICAL);
            float density = ctl.activity.getResources().getDisplayMetrics().density;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            lp.leftMargin = (int) (8 * density);
            lp.rightMargin = (int) (8 * density);
            ctl.layoutDanmaku.addView(lobbyChatContainer, lp);
        }
        lobbyChatContainer.removeAllViews();
        ctl.layoutDanmaku.setVisibility(View.VISIBLE);
        // 回灌等待界面就绪前先行到达的系统/观战消息（旧→新，appendLobbyChat 内部
        // 仍有超 10 条裁剪）；容器就绪才清空，未就绪（layoutDanmaku==null 早退）则保留
        if (lobbyChatContainer != null) {
            while (!pendingLobbyMsgs.isEmpty()) {
                PendingLobbyMsg p = pendingLobbyMsgs.removeFirst();
                appendLobbyChat(p.type, p.text);
            }
        }
    }

    /** 决斗开始：退出大厅聊天模式，恢复决斗内玩家分侧聊天 + 系统/观战弹幕；
     *  未回灌完的缓存随大厅界面一并作废，防止残留到下一次建连串会话 */
    void exitLobbyChatMode() {
        if (!lobbyChatMode) return;
        lobbyChatMode = false;
        pendingLobbyMsgs.clear();
        if (lobbyChatContainer != null) lobbyChatContainer.removeAllViews();
        clearDanmaku();
    }

    /** 大厅聊天列表追加：旧→新从上往下排列，每条独立半透明黑底；超过 10 条移除最上方旧消息；颜色对齐 drawing.cpp chatColor */
    private void appendLobbyChat(int playerType, String message) {
        if (lobbyChatContainer == null) return;
        if (message == null) message = "";
        // 表情编码在大厅没有头像气泡可依附，直接忽略
        if (isEmoticonCode(message)) return;
        String text;
        int color;
        if (playerType >= 0 && playerType < 4) {
            // 玩家消息：昵称: 内容（颜色对齐 chatColor[0..3] 白色）
            text = chatNickname(playerType) + ": " + message;
            color = YGOUtil.c(R.color.white);
        } else if (playerType == 8) {
            text = "[System]: " + message;
            color = YGOUtil.c(R.color.chat_color_lavender);                       // chatColor[8]
        } else if (playerType == 9) {
            text = "[Script Error]: " + message;
            color = YGOUtil.c(R.color.chat_color_red);                       // chatColor[9]
        } else if (playerType == 10) {
            text = "[********]: " + message;
            color = YGOUtil.c(R.color.chat_color_red);                       // chatColor[10]
        } else {
            // 观战者 11-19（无前缀）与其他未知类型
            text = message;
            color = (playerType >= 11 && playerType <= 19)
                    ? DANMAKU_OBS_COLORS[playerType - 11] : YGOUtil.c(R.color.white);
        }
        TextView tv = new TextView(ctl.activity);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);  // 与 tv_chat_message 统一 9sp
        tv.setTextColor(color);
        tv.setBackgroundColor(CHAT_BG_COLOR);               // 对齐 drawing.cpp draw2DRectangle 0xa0000000
        tv.setShadowLayer(1f, 1f, 1f, YGOUtil.c(R.color.black));
        float density = ctl.activity.getResources().getDisplayMetrics().density;
        int hPadding = (int) (3 * density);
        tv.setPadding(hPadding, 0, hPadding, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (2 * density);          // 每条之间留间隙，黑底不连成整块
        lobbyChatContainer.addView(tv, lp);
        // 超过 10 条：移除最上方最旧的一条
        while (lobbyChatContainer.getChildCount() > MAX_LOBBY_CHAT_LINES) {
            lobbyChatContainer.removeViewAt(0);
        }
    }

    /**
     * 系统/脚本错误/观战消息以弹幕形式横向滚动显示：
     * 对齐 drawing.cpp L1587-1593：chatType>=4 时 offsetX = (1200 - chatTiming[i]) * 4，
     * 消息自右向左匀速移动直至离场消失；颜色对齐 chatColor[chatType]：
     * 8 系统=chat_color_lavender，9 脚本错误/10 隐藏名=chat_color_red，11-19 观战=chatColor[11..19] 轮换。
     * 前缀对齐 game.cpp AddChatMsg：8→"[System]: "、9→"[Script Error]: "、10→"[********]: "、
     * 观战 11-19 无前缀（default 分支不追加）。
     * 弹幕宿主取 drawspec 覆盖层（SpecEffectOverlay 的 PopupWindow 层，不受 GameFieldView
     * setZOrderOnTop 的 GL 曲面遮挡）：双方 LP 血条正下方的全屏宽横带，高 = 3 行 × 行高，
     * 不再依赖 layout_top_info 的布局状态（历史上该依赖导致弹幕落回被遮挡的回退层而永不可见）。
     */
    private void showChatDanmaku(int playerType, String message) {
        if (message == null || message.isEmpty()) return;
        String text;
        int color;
        if (playerType == 8) {
            text = "[System]: " + message;
            color = YGOUtil.c(R.color.chat_color_lavender);                       // chatColor[8]
        } else if (playerType == 9) {
            text = "[Script Error]: " + message;
            color = YGOUtil.c(R.color.chat_color_red);                       // chatColor[9]
        } else if (playerType == 10) {
            text = "[********]: " + message;
            color = YGOUtil.c(R.color.chat_color_red);                       // chatColor[10]
        } else {
            text = message;
            color = (playerType >= 11 && playerType <= 19)
                    ? DANMAKU_OBS_COLORS[playerType - 11] : YGOUtil.c(R.color.white);
        }
        showDanmakuLine(text, color, 0);
    }

    /**
     * 弹幕入场（系统/观战消息与分侧聊天行容器的回退路径共用）：带顶锚定我方 LP 血条
     * 底边（对齐 drawing.cpp DrawChatMsg chatType>=4 分支 y=10 贴玩家信息区的绘制基准；
     *  obtainDanmakuLayer 设计语义即「血条正下方全屏宽横带」；真机验证过的最终版规格）。
     * 历史回归：一度改为锚定屏幕正中（heightPixels/2），决斗态弹幕带恰落入居中特效大图/
     * 决斗弹窗层区域而不可见——决斗中 8/9/10/11-19 「看不到弹幕」的直接根因，改回血条下方。
     * 血条未布局时经 mainHandler 有限次 post 重试（上限 DANMAKU_MAX_LAYOUT_RETRIES），
     * 与 rebuildSideChatLayer 同一套就绪等待模式；宿主层未布局时同样有限重试。
     */
    private void showDanmakuLine(String text, int color, int retries) {
        int[] bar = ctl.topInfoManager != null
                ? ctl.topInfoManager.getLpBarRectInWindow(0) : null;
        if (bar == null || bar[2] <= 0 || bar[3] <= 0) {
            // 血条尚未布局（进决斗瞬间/回退路径）：延后重试，不用旧坐标抢先入场
            if (retries < DANMAKU_MAX_LAYOUT_RETRIES) {
                final int next = retries + 1;
                ctl.mainHandler.post(() -> showDanmakuLine(text, color, next));
            } else {
                android.util.Log.d("Danmaku", "skip: lp bar not ready, retries=" + retries);
            }
            return;
        }
        SpecEffectOverlay overlay = ctl.activity.obtainSpecOverlay();
        int bandHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DANMAKU_ROW_HEIGHT_DP * DANMAKU_MAX_ROWS,
                ctl.activity.getResources().getDisplayMetrics());
        // 带顶 = 我方 LP 血条顶边（窗口坐标 bar[1]）：对齐 gframe drawing.cpp 弹幕画在
        // 屏幕顶部玩家信息带（与血条同行）；横屏双方血条同在顶部横排、竖屏我方血条
        // 也在决斗区上排，故取 bar[1] 即把整条弹幕带抬到血条所在行（旧锚 bar[1]+bar[3]
        // 血条底边）。消息自屏幕右缘入场、匀速左移，直至整个
        // 文本完全从最左侧离场后移除。
        int bandTop = bar[1];
        FrameLayout layer = overlay != null ? overlay.obtainDanmakuLayer(bandHeight, bandTop) : null;
        final FrameLayout parent = layer != null ? layer : ctl.layoutDanmaku;
        if (parent == null) {
            android.util.Log.d("Danmaku", "skip: no host layer (overlay=" + overlay
                    + " layoutDanmaku=" + ctl.layoutDanmaku + ")");
            return;
        }
        if (parent.getWindowToken() == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            // 窗口未 attach（PopupWindow 正在 showWindow 的 token 重试中）或尚未布局：
            // 实机铁证 spawn ok 日志 token=false winVis=8 root=0x0——窗口没在屏上，
            // 弹幕挂进去永不可见。View.post 未 attach 时进挂起队列、attach 后 flush，
            // 天然等到覆盖层真正显示；重试用尽才丢弃，避免无限 post 黑洞
            if (retries < DANMAKU_MAX_LAYOUT_RETRIES) {
                final int next = retries + 1;
                parent.post(() -> showDanmakuLine(text, color, next));
            } else {
                android.util.Log.d("Danmaku", "skip: host not attached/laid-out, retries=" + retries);
            }
            return;
        }
        TextView tv = new TextView(ctl.activity);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);   // 与 tv_chat_message 统一 9sp
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setBackgroundColor(CHAT_BG_COLOR); // 对齐 drawing.cpp draw2DRectangle 0xa0000000
        int hPadding = (int) (3 * ctl.activity.getResources().getDisplayMetrics().density);
        tv.setPadding(hPadding, 0, hPadding, 0);
        // 对齐 drawing.cpp shadowloc：黑色 1px 偏移阴影，保证血条背景上可读
        tv.setShadowLayer(1f, 1f, 1f, YGOUtil.c(R.color.black));
        int row = danmakuRowIndex % DANMAKU_MAX_ROWS; // 超过 3 行循环回第 1 行
        danmakuRowIndex++;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        if (layer != null) {
            // drawspec 层：宿主即血条下方全屏宽横带，3 行均分其高度（行高等于带高/3），
            // 容器默认裁剪子 View，弹幕恰以该带为界出入
            lp.topMargin = row * Math.max(1, parent.getHeight() / DANMAKU_MAX_ROWS);
        } else {
            int rowHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    DANMAKU_ROW_HEIGHT_DP, ctl.activity.getResources().getDisplayMetrics());
            // 回退层：垂直锚定到 LP 血条所在的顶部透明带（历史层级，受 GL 遮挡观感受限）
            lp.topMargin = danmakuRowTopMargin(row, rowHeight);
        }
        // 历史缺陷终极根因（实机日志 tv=0x43 铁证）：旧实现用 lp.leftMargin=父宽 做起跑点，
        // FrameLayout 对 WRAP_CONTENT 子 View 的测量约束 = AT_MOST(父宽 - leftMargin) = 0，
        // 文字宽度被量成 0——弹幕虽已入场、动画也在跑，但视图 0 宽永不可见。起跑点改由
        // translationX 提供（不参与测量），文本按内容正常量宽；入场后右缘外起步、
        // 匀速左移至 -文本宽（整个文本完全从最左侧离场）后移除。
        lp.leftMargin = 0;
        parent.addView(tv, lp);
        tv.setTranslationX(parent.getWidth());        // 起点：屏幕右缘之外（仅平移，不改布局宽）
        danmakuViews.add(tv);
        float density = ctl.activity.getResources().getDisplayMetrics().density;
        tv.post(() -> {
            // 匀速：总路程 = 宿主层宽度 + 自身宽度（从屏幕右缘外入场，到完全从最左侧离场）；
            // 终点 translationX = -自身宽（leftMargin 已为 0，不能再按旧公式多减一个宿主宽）
            int distance = parent.getWidth() + tv.getWidth();
            long duration = Math.max(1, (long) (distance / (DANMAKU_SPEED_DP_PER_MS * density)));
            tv.animate().translationX(-tv.getWidth()).setDuration(duration)
                    .withEndAction(() -> {
                        danmakuViews.remove(tv);
                        parent.removeView(tv);
                        // 最后一条弹幕离场且无特效在播 → 覆盖层自行关闭（不占消息闸门）
                        if (layer != null) overlay.notifyDanmakuRemoved();
                    }).start();
        });
    }

    /**
     * 弹幕行 topMargin（相对 layout_danmaku 顶边，px）：
     * 把 DANMAKU_MAX_ROWS 行压缩进「弹幕层顶 ～ LP 血条底边」这条 GL 透明带内，
     * 使弹幕与血条同一高度水平滚动，且不被 GameFieldView 的场地/手卡纹理遮挡。
     * 行距 = min(设定行高 DANMAKU_ROW_HEIGHT_DP, 透明带高度 / 行数)；
     * 血条尚未就绪时兜底为按设定行高从顶累积。
     */
    private int danmakuRowTopMargin(int row, int rowHeight) {
        int[] lpBar = ctl.topInfoManager != null ? ctl.topInfoManager.getLpBarPositionAndHeight() : null;
        if (lpBar == null) {
            return row * rowHeight;
        }
        int[] dmLoc = new int[2];
        ctl.layoutDanmaku.getLocationInWindow(dmLoc);
        int lpTopRel = lpTopRel(lpBar, dmLoc);
        int bandBottom = lpTopRel + lpBar[1];     // 可见透明带下沿＝血条底边
        if (bandBottom <= 0) {
            return row * rowHeight;
        }
        int spacing = Math.min(rowHeight, bandBottom / DANMAKU_MAX_ROWS);
        if (spacing <= 0) spacing = rowHeight;
        return row * spacing;
    }

    /** 血条顶边相对弹幕层顶边的偏移（两者同为窗口坐标，作差即相对值） */
    private int lpTopRel(int[] lpBar, int[] dmLoc) {
        return lpBar[0] - dmLoc[1];
    }

    /** 隐藏聊天消息文本（对齐 gframe BUTTON_CHATTING 切换关闭时的 ClearChatMsg：清空聊天显示） */
    void clearChatMessages() {
        myChatLines.clear();
        opChatLines.clear();
        clearDanmaku();   // clearChatMessages() 末尾：停止聊天时清空弹幕
        if (ctl.tvChatMessage1 != null) {
            ctl.tvChatMessage1.setText("");
            ctl.tvChatMessage1.setVisibility(View.GONE);
        }
        if (ctl.tvChatMessage2 != null) {
            ctl.tvChatMessage2.setText("");
            ctl.tvChatMessage2.setVisibility(View.GONE);
        }
    }

    private boolean isEmoticonCode(String message) {
        if (message == null || message.isEmpty()) return false;
        for (String code : TextureLoader.EMOTICON_KEYS) {
            if (code.equals(message)) return true;
        }
        return false;
    }

    /**
     * 扫描文本中内嵌的表情码（EMOTICON_KEYS 表内 & 前缀码，同一位置最长匹配），
     * 命中的码返回、非码文本原样拼入 remaining；无命中时返回空列表。
     */
    private List<String> extractEmbeddedEmoticonCodes(String text, StringBuilder remaining) {
        List<String> codes = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&') {
                String matched = null;
                for (String code : TextureLoader.EMOTICON_KEYS) {
                    if (text.startsWith(code, i)
                            && (matched == null || code.length() > matched.length())) {
                        matched = code;
                    }
                }
                if (matched != null) {
                    codes.add(matched);
                    i += matched.length();
                    continue;
                }
            }
            remaining.append(c);
            i++;
        }
        return codes;
    }

    /** 将表情图片气泡显示到发送方头像下方，并刷新自动隐藏计时 */
    private void showEmoteBubble(int playerType, String code) {
        if (ctl.engine == null) return;
        // 与文字聊天同一套 chatType 分边规则（chatType∈{0,2}=我方）
        int chatType = chatLocalType(playerType);
        boolean selfSide = (chatType == 0 || chatType == 2);
        ImageView bubble = selfSide ? ivPlayerEmoteBubble : ivOpponentEmoteBubble;
        if (bubble == null) return;
        Bitmap bmp = TextureLoader.get().getEmoticon(code);
        if (bmp == null || bmp.isRecycled()) return;
        bubble.setImageBitmap(bmp);
        bubble.setVisibility(View.VISIBLE);
        Runnable hide = selfSide ? hidePlayerEmoteBubble : hideOpponentEmoteBubble;
        ctl.mainHandler.removeCallbacks(hide);
        ctl.mainHandler.postDelayed(hide, EMOTE_BUBBLE_DURATION_MS);
    }

    /** 清场（GameFieldController.hide）：清空双方聊天记录、进行中的弹幕、大厅聊天与表情气泡 */
    void resetOnHide() {
        myChatLines.clear();
        opChatLines.clear();
        clearDanmaku();
        // 清场时兜底退出大厅聊天模式（下次 showPlayerWaiting 会重新进入）
        lobbyChatMode = false;
        if (lobbyChatContainer != null) lobbyChatContainer.removeAllViews();
        // 清场时隐藏表情气泡并撤销延时隐藏任务
        if (ivPlayerEmoteBubble != null) ivPlayerEmoteBubble.setVisibility(View.GONE);
        if (ivOpponentEmoteBubble != null) ivOpponentEmoteBubble.setVisibility(View.GONE);
        ctl.mainHandler.removeCallbacks(hidePlayerEmoteBubble);
        ctl.mainHandler.removeCallbacks(hideOpponentEmoteBubble);
    }
}

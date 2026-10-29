package cn.garymb.ygomobile.game.chat;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameFieldController;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.render.spec.SpecEffectOverlay;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.lobby.PlayerWaitingDialog;

/**
 * Field chat board, base layer of the split inheritance chain
 * (FieldChatBoardBase <- FieldChatBoard). Owns all state fields, the
 * nested item/runner types, the danmaku spawn/pump/animation engine,
 * the per-side chat row helpers, nickname resolution, long-press
 * hit-testing and the emoticon bubble helpers shared by the upper layer.
 */
abstract class FieldChatBoardBase {

    protected final GameFieldController ctl;

    /** 每侧玩家聊天最大行数：超过 5 行向上滚动（清除第一条，最新一条落在最下行） */
    protected static final int MAX_CHAT_LINES = 5;

    /** 每侧聊天行数大于此值时，最早一条的在屏存活时长按 2 倍速消耗（提前一半即消失，
     *  用户规格：刷屏时最早那条记录消失速度加快为现在的 2 倍） */
    protected static final int CHAT_ACCELERATE_LINE_COUNT = 3;

    /**
     * 单条玩家聊天在屏存活时长（毫秒），超时后从最早一行开始自动清除——对齐
     * drawing.cpp DrawChatMsg：addChatMsg 置 chatTiming=1200，绘制每帧 chatTiming-- 至 0 即不再绘制，
     * 约 1200 帧 @60fps ≈ 20s。轮询周期 CHAT_EXPIRE_CHECK_MS 到点即移除已超时最旧行。
     */
    protected static final long CHAT_LINE_LIFETIME_MS = 20000L;
    /** 聊天过期轮询周期（毫秒）：每 500ms 检查一次是否有最旧行超时需清除 */
    protected static final long CHAT_EXPIRE_CHECK_MS = 500L;

    /** 一条带入场时间戳的血条下方聊天行（供按时间从最早行自动清除） */
    protected static final class TimedLine {
        final String text;
        final long addedAt;
        TimedLine(String text, long addedAt) {
            this.text = text;
            this.addedAt = addedAt;
        }
    }

    protected final LinkedList<TimedLine> myChatLines = new LinkedList<>();
    protected final LinkedList<TimedLine> opChatLines = new LinkedList<>();

    /** 尚未落进血条下方聊天行容器的我方/对方消息（血条/覆盖层未就绪时暂存，
     *  容器可用后按旧→新顺序补挂；每侧暂存不超 MAX_CHAT_LINES 条） */
    protected final LinkedList<TimedLine> myChatPending = new LinkedList<>();
    protected final LinkedList<TimedLine> opChatPending = new LinkedList<>();

    /** 聊天按时间清除的轮询任务是否在排：保证只挂一个 postDelayed 链 */
    protected boolean chatExpireScheduled = false;

    // 表情气泡：显示在发送方头像下方（对齐 gframe drawing.cpp DrawEmoticon），超时自动隐藏
    protected static final long EMOTE_BUBBLE_DURATION_MS = 3000;
    public ImageView ivPlayerEmoteBubble, ivOpponentEmoteBubble;
    protected final Runnable hidePlayerEmoteBubble = () -> {
        if (ivPlayerEmoteBubble != null) ivPlayerEmoteBubble.setVisibility(View.GONE);
    };
    protected final Runnable hideOpponentEmoteBubble = () -> {
        if (ivOpponentEmoteBubble != null) ivOpponentEmoteBubble.setVisibility(View.GONE);
    };

    // === 系统/观战消息弹幕（对齐 drawing.cpp DrawChatMsg chatType>=4 分支） ===

    /** 弹幕横带行数（= 一组条数）：血条同行横带自上而下 3 行，观战进场的大量消息按
     *  「上中下」每 3 条一组入场，整组滑入屏幕后下一组才进（见 pumpDanmakuGroup） */
    protected static final int DANMAKU_MAX_ROWS = 3;
    /** 弹幕匀速（dp/ms）：时长 = 总路程 / 速度，所有消息速度一致；
     *  0.04（约 14 秒横滚一屏，与 gframe 1200帧×4px 节奏更接近） */
    protected static final float DANMAKU_SPEED_DP_PER_MS = 0.04f;
    /** 首帧未布局时的最大重试次数（每次 post 约一帧，≈ 2 秒），防止窗口 token
     *  不可用时无限 post 黑洞堆消息 */
    protected static final int DANMAKU_MAX_LAYOUT_RETRIES = 120;
    /** 弹幕行高（dp）：3 行带总高约 48dp，贴屏幕顶部自上而下排列 */
    protected static final float DANMAKU_ROW_HEIGHT_DP = 16f;
    /** 观战弹幕颜色，逐一对齐 drawing.cpp chatColor[11..19]（11=红 12=绿 13=蓝 14=青 15=品红 16=黄 17=白 18=灰 19=深灰） */
    protected static final int[] DANMAKU_OBS_COLORS = {
            YGOUtil.c(R.color.chat_color_red), YGOUtil.c(R.color.chat_color_green),
            YGOUtil.c(R.color.chat_color_blue), YGOUtil.c(R.color.chat_color_cyan),
            YGOUtil.c(R.color.chat_color_magenta), YGOUtil.c(R.color.chat_color_yellow),
            YGOUtil.c(R.color.white), YGOUtil.c(R.color.chat_color_gray),
            YGOUtil.c(R.color.chat_color_dark_gray)
    };
    /** 聊天消息半透明黑底（对齐 drawing.cpp L1597 draw2DRectangle 0xa0000000） */
    protected static final int CHAT_BG_COLOR = YGOUtil.c(R.color.black_a0);

    protected final List<TextView> danmakuViews = new ArrayList<>();
    /** 弹幕待入场队列：观战进场会瞬时涌入大量系统/观战消息，逐条即时入场会使同行
     *  多条以相同起点重叠滚出；故先进队列，由分组闸门按「上中下」每 3 条一组批量放行 */
    protected final LinkedList<DanmakuItem> danmakuQueue = new LinkedList<>();
    /** 当前是否有一组弹幕正在入场/在屏：为真时后续入队只排队，等本组全部滑入屏幕后由
     *  {@link #onDanmakuOneEntered()} 放行下一组，实现「一组进完下一组才进」的顺序 */
    protected boolean danmakuGroupRunning = false;
    /** 本组尚在入场阶段（未完全滑入屏幕）的消息条数，减到 0 即整组入场完毕、放行下一组 */
    protected int danmakuGroupEnterPending = 0;
    /** 弹幕分组代际：clearDanmaku/旋转清场时自增，使清场前已 postDelayed 的入场完成回调作废，
     *  避免过期回调驱动新清场后的队列错乱 */
    protected int danmakuPumpGeneration = 0;
    /** 一条待入场弹幕（文本 + 颜色） */
    protected static final class DanmakuItem {
        final String text;
        final int color;
        DanmakuItem(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    /**
     * 一条在屏弹幕的运行数据：目标位移、宿主层与覆盖层、密度。弹幕用 ViewPropertyAnimator 匀速左移，
     * 无法中途改速；「按住弹幕全体减速至一半 / 松开恢复」经此按当前 translationX 到目标的剩余距离、
     * 以新速度取消并重启动画（存于 TextView.tag）。layer 为 null 表示回退层 layout_danmaku（不入覆盖层收口）。
     */
    protected static final class DanmakuRunner {
        final TextView tv;
        final ViewGroup parent;
        final FrameLayout layer;
        final SpecEffectOverlay overlay;
        final float density;
        float targetX;
        DanmakuRunner(TextView tv, ViewGroup parent, FrameLayout layer,
                      SpecEffectOverlay overlay, float density) {
            this.tv = tv;
            this.parent = parent;
            this.layer = layer;
            this.overlay = overlay;
            this.density = density;
        }
    }

    /** 当前是否处于「按住弹幕」状态：为真时在屏与新入场弹幕一律以一半速度滚动，松开恢复原速 */
    protected boolean danmakuHeld = false;

    /** 长按观察：待触发的长按任务（ACTION_DOWN 排期，超时且未移动即命中复制/减速） */
    protected Runnable pendingLongPress;
    /** 本次按下起点窗口坐标（与 getLocationInWindow 同基准，判定移动是否超 slop） */
    protected float downWinX, downWinY;
    /** 长按判定时是否已初始化触摸阈值 */
    protected boolean touchConfigReady = false;
    protected int longPressTimeoutMs = 500;
    protected int touchSlopPx = 24;

    /** 大厅（player waiting）聊天模式：全部消息在 layout_danmaku 静态列表显示 */
    protected boolean lobbyChatMode = false;
    /** 大厅聊天最大条数：超过时移除最上方最旧的一条 */
    protected static final int MAX_LOBBY_CHAT_LINES = 10;
    /** 大厅聊天列表容器：每条消息一个 TextView（独立半透明黑底），旧→新从上往下排列 */
    protected LinearLayout lobbyChatContainer;
    /**
     * 决斗开始前（等待界面尚未弹出、lobbyChatMode 未置位）收到的非玩家消息缓存：
     * 内置服务端把「XX 加入了房间」的 type=8 广播发在 STOC_JOIN_GAME 回执之前
     *（GameRoom.joinGame），新客机必然先收到它、后进入大厅模式；旧实现此时把它当
     * 决斗弹幕处理，随后 enterLobbyChatMode 的 clearDanmaku 又将其抹掉——客机永远
     * 看不到主机系统消息的第二重根因。对齐 drawing.cpp DrawChatMsg：!dInfo.isStarted
     * 时全部 chatMsg（含 8/9/10/11-19）均以静态纵列逐行绘制，故本缓存按到达顺序在
     * enterLobbyChatMode 就绪后回灌 appendLobbyChat，消息不丢。
     */
    protected static final class PendingLobbyMsg {
        final int type;
        final String text;
        PendingLobbyMsg(int type, String text) {
            this.type = type;
            this.text = text;
        }
    }
    protected final LinkedList<PendingLobbyMsg> pendingLobbyMsgs = new LinkedList<>();
    /** 聊天昵称：优先 STOC_HS_PLAYER_ENTER 记录的座位名（对齐 game.cpp AddChatMsg 的昵称前缀） */
    protected String chatNickname(int seat) {
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
    protected int oppositeChatPlayer(int player, boolean isTag) {
        int sideBit = isTag ? 0x2 : 0x1;
        return player ^ sideBit;
    }

    /**
     * 复刻 gframe game.cpp ChatLocalPlayer——把 STOC_CHAT 的发送方座位号（对局内为决斗序号
     * dp->type）转换为 AddChatMsg 用于命名槽位与分边的 chatType（0-3）。分边规则：chatType∈{0,2}=
     * 我方队（左）、{1,3}=对方队（右）（对齐 AddChatMsg L2325 player==0||2）。不返回 is_self 位
     * （本端口音效在引擎层统一处理）。
     */
    protected int chatLocalType(int player) {
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
    protected String chatNameByLocalType(int chatType) {
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
    /** 安排/维持聊天按时间清除的轮询：每 CHAT_EXPIRE_CHECK_MS 移除超时最旧行，两侧均空时停止。 */
    protected void scheduleChatExpire() {
        if (chatExpireScheduled) return;
        chatExpireScheduled = true;
        ctl.mainHandler.postDelayed(chatExpireTask, CHAT_EXPIRE_CHECK_MS);
    }

    protected final Runnable chatExpireTask = new Runnable() {
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

    /** 移除 lines/pending 队首已超时的行，返回 lines 是否有移除。当行数大于
     *  {@link #CHAT_ACCELERATE_LINE_COUNT} 时按半程时长（2 倍速）判定，使最早一条更快消失。 */
    protected static boolean trimExpiredChat(LinkedList<TimedLine> lines, LinkedList<TimedLine> pending, long now) {
        boolean removed = false;
        long limit = lines.size() > CHAT_ACCELERATE_LINE_COUNT
                ? CHAT_LINE_LIFETIME_MS / 2 : CHAT_LINE_LIFETIME_MS;
        while (!lines.isEmpty() && now - lines.peekFirst().addedAt >= limit) {
            lines.removeFirst();
            removed = true;
        }
        while (!pending.isEmpty() && now - pending.peekFirst().addedAt >= limit) {
            pending.removeFirst();
        }
        return removed;
    }
    /** 构造一条血条下方聊天行 TextView（昵称前缀已由调用方拼好入 line）：9sp 白字 + 半透明黑底，
     *  规格对齐 drawing.cpp chatColor[0..3] 玩家消息白色 / draw2DRectangle 0xa0000000 */
    protected TextView createChatRowView(TimedLine entry) {
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
    protected void relayoutSideChatRows(boolean selfSide, int retries) {
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
     * 聊天输入框降回静置位、双方血条回到原处后，把血条下方各带重锚到当前实测血条位置
     *（用户反馈：抬键盘期间发出的聊天会顶在屏幕顶端显示，输入框降回后位置不回位）。
     * <p>两步：① 无论有无在屏内容先把实测锚点回灌为静置基准（否则抬高期间第一条记录无基准
     * 可用，只能退回到被键盘压缩的实测值）；② 仅将真正错位的带重建到当前位置，带顶与实测
     * 一致时直接跳过，避免每次 HUD/输入框布局变化都整列重建聊天行或重排弹幕带。
     * 大厅聊天模式下不处理（其列表挂在视图树 layout_danmaku 内，随布局自然回位）。
     */
    public void repinFieldBands() {
        if (lobbyChatMode || ctl.topInfoManager == null) return;
        SpecEffectOverlay overlay = ctl.activity.getSpecOverlay();
        if (overlay == null) return;
        int[] me = ctl.topInfoManager.getLpBarRectInWindow(0);
        int[] op = ctl.topInfoManager.getLpBarRectInWindow(1);
        boolean meOk = me != null && me[2] > 0 && me[3] > 0;
        boolean opOk = op != null && op[2] > 0 && op[3] > 0;
        if (meOk) {
            overlay.rememberBandAnchor(SpecEffectOverlay.BAND_SELF_CHAT, me[1] + me[3]);
            overlay.rememberBandAnchor(SpecEffectOverlay.BAND_DANMAKU, me[1]);
        }
        if (opOk) {
            overlay.rememberBandAnchor(SpecEffectOverlay.BAND_OPP_CHAT, op[1] + op[3]);
        }
        if (meOk && !myChatLines.isEmpty()
                && overlay.getBandTop(SpecEffectOverlay.BAND_SELF_CHAT) != me[1] + me[3]) {
            // 整列重挂：容器移到新锚点同时保留同一数据源的旧→新行序（旋转回灌同源）
            relayoutSideChatRows(true, 0);
        }
        if (opOk && !opChatLines.isEmpty()
                && overlay.getBandTop(SpecEffectOverlay.BAND_OPP_CHAT) != op[1] + op[3]) {
            relayoutSideChatRows(false, 0);
        }
        repinDanmakuBand(overlay, me);
    }

    /** 重锚系统/观战弹幕带（带顶锚我方血条顶边，与 pumpDanmakuGroup 同一口径） */
    private void repinDanmakuBand(SpecEffectOverlay overlay, int[] bar) {
        if (danmakuViews.isEmpty() || bar == null || bar[2] <= 0 || bar[3] <= 0) return;
        if (overlay.getBandTop(SpecEffectOverlay.BAND_DANMAKU) == bar[1]) return;
        int bandHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DANMAKU_ROW_HEIGHT_DP * DANMAKU_MAX_ROWS,
                ctl.activity.getResources().getDisplayMetrics());
        // 只重设带的位置与尺寸（只改 topMargin）：在屏弹幕 TextView 的 translationX 与动画不受影响
        overlay.obtainDanmakuLayer(bandHeight, bar[1]);
    }
    protected static <T> void swapListContent(LinkedList<T> a, LinkedList<T> b) {
        java.util.List<T> tmp = new ArrayList<>(a);
        a.clear();
        a.addAll(b);
        b.clear();
        b.addAll(tmp);
    }
    /** 聊天行容器不可用（无覆盖层/重试用尽）时，把未落容器的消息回退为弹幕显示，不丢消息 */
    protected void flushSideChatPendingToDanmaku(boolean selfSide) {
        LinkedList<TimedLine> pending = selfSide ? myChatPending : opChatPending;
        if (pending.isEmpty()) return;
        for (TimedLine entry : pending) enqueueDanmaku(entry.text, YGOUtil.c(R.color.white));
        pending.clear();
    }

    /** 清除全部进行中的弹幕与分侧聊天行（停止聊天/离开决斗界面时调用）：从当前宿主容器
     *  （drawspec 弹幕层/聊天行层或回退的 layout_danmaku）移除，并通知覆盖层空闲收口
     *  （无特效时关闭 PopupWindow） */
    protected void clearDanmaku() {
        // 停止聊天/离开决斗/进大厅：取消按时间清除的轮询链，避免残留 postDelayed
        ctl.mainHandler.removeCallbacks(chatExpireTask);
        chatExpireScheduled = false;
        // 清场撤销长按观察：取消待触发长按并复位按住减速标志，防止残留 postDelayed/状态
        cancelPendingLongPress();
        danmakuHeld = false;
        myChatPending.clear();
        opChatPending.clear();
        for (TextView tv : danmakuViews) {
            tv.animate().cancel();
            Object p = tv.getParent();
            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(tv);
            else if (ctl.layoutDanmaku != null) ctl.layoutDanmaku.removeView(tv);
        }
        danmakuViews.clear();
        danmakuQueue.clear();
        danmakuGroupRunning = false;
        danmakuGroupEnterPending = 0;
        danmakuPumpGeneration++;   // 作废清场前已排队的入场完成回调
        SpecEffectOverlay overlay = ctl.activity.getSpecOverlay();
        if (overlay != null) overlay.clearChatRowLayers();
    }

    /**
     * 决斗界面是否已进入（对齐 C++ dInfo.isStarted 语义）：仅 HAND_SELECT 起算决斗态，
     * 此时非玩家消息才属于弹幕横滚；IDLE/CONNECTING/LOBBY（含等待界面弹出前的窗口期）
     * 一律缓存进 pendingLobbyMsgs，由 enterLobbyChatMode 回灌大厅静态列表。
     */
    protected boolean duelUiStarted() {
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
    protected void showChatDanmaku(int playerType, String message) {
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
        enqueueDanmaku(text, color);
    }

    /**
     * 请求显示一条弹幕：先入队，再由分组闸门 {@link #pumpDanmakuGroup(int)} 按「上中下」
     * 每 3 条一组批量入场。观战进场会瞬时涌入大量系统/观战消息，若逐条即时入场，同一行的
     * 多条弹幕会以相同起点重叠滚出（用户反馈的「混乱重叠」根因）；入队后一组全部滑入屏幕，
     * 下一组才从屏幕右缘入场，组间因速度一致保持间距、永不相撞。
     */
    protected void enqueueDanmaku(String text, int color) {
        if (text == null || text.isEmpty()) return;
        danmakuQueue.addLast(new DanmakuItem(text, color));
        pumpDanmakuGroup(0);
    }

    /**
     * 弹幕分组入场闸门：从队列头部取最多 {@link #DANMAKU_MAX_ROWS} 条作为一组，依次落在
     * 带顶行→中行→底行（row = 组内序号，天然实现「上→中→下」，不再需要行计数器），同批入场；
     * 本组全部滑入屏幕后由 {@link #onDanmakuOneEntered()} 放行下一组。
     * 带顶锚定我方 LP 血条顶边 bar[1]（对齐 drawing.cpp DrawChatMsg chatType>=4 与血条同行；
     * 历史回归：锚屏幕正中会落入居中特效/弹窗区而不可见）。宿主（血条/drawspec 层）未就绪时
     * 经有限次 post 重试（上限 DANMAKU_MAX_LAYOUT_RETRIES），重试等待期不拆散整组。
     */
    protected void pumpDanmakuGroup(int retries) {
        if (danmakuGroupRunning || danmakuQueue.isEmpty()) return;
        int[] bar = ctl.topInfoManager != null
                ? ctl.topInfoManager.getLpBarRectInWindow(0) : null;
        if (bar == null || bar[2] <= 0 || bar[3] <= 0) {
            // 血条尚未布局（进决斗瞬间/回退路径）：延后重试，不用旧坐标抢先入场
            if (retries < DANMAKU_MAX_LAYOUT_RETRIES) {
                final int next = retries + 1;
                ctl.mainHandler.post(() -> pumpDanmakuGroup(next));
            } else {
                android.util.Log.d("Danmaku", "skip group: lp bar not ready, retries=" + retries);
            }
            return;
        }
        SpecEffectOverlay overlay = ctl.activity.obtainSpecOverlay();
        int bandHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DANMAKU_ROW_HEIGHT_DP * DANMAKU_MAX_ROWS,
                ctl.activity.getResources().getDisplayMetrics());
        // 带顶 = 我方 LP 血条顶边（窗口坐标 bar[1]）：横屏双方血条同在顶部横排、竖屏我方血条
        // 也在决斗区上排，取 bar[1] 把整条弹幕带抬到血条所在行（旧锚 bar[1]+bar[3] 血条底边）。
        int bandTop = bar[1];
        FrameLayout layer = overlay != null ? overlay.obtainDanmakuLayer(bandHeight, bandTop) : null;
        final FrameLayout parent = layer != null ? layer : ctl.layoutDanmaku;
        if (parent == null) {
            android.util.Log.d("Danmaku", "skip group: no host layer (overlay=" + overlay
                    + " layoutDanmaku=" + ctl.layoutDanmaku + ")");
            danmakuQueue.clear();
            return;
        }
        if (parent.getWindowToken() == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            // 窗口未 attach（PopupWindow 正在 showWindow 的 token 重试中）或尚未布局：
            // View.post 未 attach 进挂起队列、attach 后 flush，天然等到覆盖层真正显示
            if (retries < DANMAKU_MAX_LAYOUT_RETRIES) {
                final int next = retries + 1;
                parent.post(() -> pumpDanmakuGroup(next));
            } else {
                android.util.Log.d("Danmaku", "skip group: host not attached/laid-out, retries=" + retries);
            }
            return;
        }
        // 整组同批入场：最多 3 条，row=组内序号 0/1/2 即上/中/下；running 置位后新入队只排队
        int groupSize = Math.min(DANMAKU_MAX_ROWS, danmakuQueue.size());
        danmakuGroupRunning = true;
        danmakuGroupEnterPending = groupSize;
        for (int row = 0; row < groupSize; row++) {
            spawnDanmakuView(parent, layer, overlay, danmakuQueue.removeFirst(), row);
        }
    }

    /**
     * 在 parent 的指定行（row：0=带顶、1=中、2=底）入场一条弹幕：自屏幕右缘起步、匀速左移至
     * 完全从最左侧离场后移除。起跑点用 translationX（不参与测量）——严禁 leftMargin=父宽，
     * FrameLayout 对 WRAP_CONTENT 子 View 的 AT_MOST(父宽-左距)=0 会把文字量成 0 宽永不可见。
     * 同时登记「完全滑入屏幕」到点回调（入场时长 = 自身宽 / 速度），供分组闸门在整组进完后放行下一组。
     */
    protected void spawnDanmakuView(final FrameLayout parent, final FrameLayout layer,
                                  final SpecEffectOverlay overlay, DanmakuItem item, int row) {
        TextView tv = new TextView(ctl.activity);
        tv.setText(item.text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);   // 与 tv_chat_message 统一 9sp
        tv.setTextColor(item.color);
        tv.setSingleLine(true);
        tv.setBackgroundColor(CHAT_BG_COLOR); // 对齐 drawing.cpp draw2DRectangle 0xa0000000
        int hPadding = (int) (3 * ctl.activity.getResources().getDisplayMetrics().density);
        tv.setPadding(hPadding, 0, hPadding, 0);
        // 对齐 drawing.cpp shadowloc：黑色 1px 偏移阴影，保证血条背景上可读
        tv.setShadowLayer(1f, 1f, 1f, YGOUtil.c(R.color.black));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        if (layer != null) {
            // drawspec 层：宿主即血条同行全屏宽横带，3 行均分其高度（行高 = 带高/3），
            // 容器默认裁剪子 View，弹幕恰以该带为界出入
            lp.topMargin = row * Math.max(1, parent.getHeight() / DANMAKU_MAX_ROWS);
        } else {
            int rowHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    DANMAKU_ROW_HEIGHT_DP, ctl.activity.getResources().getDisplayMetrics());
            // 回退层：垂直锚定到 LP 血条所在的顶部透明带（历史层级，受 GL 遮挡观感受限）
            lp.topMargin = danmakuRowTopMargin(row, rowHeight);
        }
        lp.leftMargin = 0;
        parent.addView(tv, lp);
        tv.setTranslationX(parent.getWidth());        // 起点：屏幕右缘之外（仅平移，不改布局宽）
        danmakuViews.add(tv);
        final float density = ctl.activity.getResources().getDisplayMetrics().density;
        final int gen = danmakuPumpGeneration;
        tv.post(() -> {
            if (gen != danmakuPumpGeneration) return;   // 清场后旧视图回调作废
            int w = tv.getWidth();
            // 登记运行数据到 tag：目标位移 = -自身宽（leftMargin 已为 0，完全从最左侧离场），
            // 供长按减速/恢复按剩余距离重新起 anim；总路程 = 宿主层宽 + 自身宽
            DanmakuRunner runner = new DanmakuRunner(tv, parent, layer, overlay, density);
            runner.targetX = -w;
            tv.setTag(runner);
            // 完全滑入屏幕所需时间：路程 = 自身宽；按住时速度减半，入场计时同样翻倍
            float speed = DANMAKU_SPEED_DP_PER_MS * (danmakuHeld ? 0.5f : 1f);
            long enterMs = Math.max(1, (long) (w / (speed * density)));
            parent.postDelayed(() -> {
                if (gen == danmakuPumpGeneration) onDanmakuOneEntered();
            }, enterMs);
            startDanmakuAnim(runner);
        });
    }

    /**
     * 从当前 translationX 匀速推进到 targetX（完全离场），速度随 {@link #danmakuHeld} 减半；
     * 到终点移除视图并在覆盖层空闲时收口。首次入场与「按住减速 / 松开恢复」均经此按剩余
     * 距离重新起 anim（取消旧 ViewPropertyAnimator 后以新速度续跑，视觉上为即时变速）。
     */
    protected void startDanmakuAnim(DanmakuRunner r) {
        float cur = r.tv.getTranslationX();
        float speed = DANMAKU_SPEED_DP_PER_MS * (danmakuHeld ? 0.5f : 1f);
        float remaining = cur - r.targetX;   // 自右向左：cur > targetX，px
        long duration = Math.max(1, (long) (remaining / (speed * r.density)));
        r.tv.animate().cancel();
        r.tv.animate().translationX(r.targetX).setDuration(duration)
                .withEndAction(() -> {
                    danmakuViews.remove(r.tv);
                    r.parent.removeView(r.tv);
                    // 最后一条弹幕离场且无特效在播 → 覆盖层自行关闭（不占消息闸门）
                    if (r.layer != null) r.overlay.notifyDanmakuRemoved();
                }).start();
    }

    /**
     * 切换「按住弹幕」减速状态：置位/复位 {@link #danmakuHeld} 并对所有在屏弹幕按剩余距离
     * 以新速度重启动画（进入减半、恢复原速）。新入场弹幕在 spawnDanmakuView 中读取本标志自动跟随。
     */
    protected void setDanmakuHeld(boolean held) {
        if (danmakuHeld == held) return;
        danmakuHeld = held;
        for (TextView tv : new ArrayList<>(danmakuViews)) {
            Object tag = tv.getTag();
            if (tag instanceof DanmakuRunner) startDanmakuAnim((DanmakuRunner) tag);
        }
    }
    protected void cancelPendingLongPress() {
        if (pendingLongPress != null) {
            ctl.mainHandler.removeCallbacks(pendingLongPress);
            pendingLongPress = null;
        }
    }

    /** 长按到点：优先命中弹幕（复制并全体减速），其次血条下方聊天行（仅复制）。 */
    protected void onLongPressHit(float x, float y) {
        pendingLongPress = null;
        TextView dm = hitTestDanmaku(x, y);
        if (dm != null) {
            copyChatText(dm.getText().toString());
            setDanmakuHeld(true);
            return;
        }
        SpecEffectOverlay overlay = ctl.activity.getSpecOverlay();
        if (overlay != null) {
            TextView row = overlay.hitTestChatRow(x, y);
            if (row != null) copyChatText(row.getText().toString());
        }
    }

    /** 命中某条在屏弹幕（用其当前窗口矩形，随滚动实时更新；弹幕宿主弹窗与 Activity 窗同原点）。 */
    protected TextView hitTestDanmaku(float x, float y) {
        int[] loc = new int[2];
        for (TextView tv : danmakuViews) {
            if (tv.getWidth() <= 0 || tv.getHeight() <= 0) continue;
            tv.getLocationInWindow(loc);
            if (x >= loc[0] && x <= loc[0] + tv.getWidth()
                    && y >= loc[1] && y <= loc[1] + tv.getHeight()) {
                return tv;
            }
        }
        return null;
    }

    /** 复制聊天文本到剪贴板并 Toast 提示（长按聊天行/弹幕触发）。 */
    protected void copyChatText(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager)
                ctl.activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("chat", text));
        Toast.makeText(ctl.activity, "已复制聊天内容", Toast.LENGTH_SHORT).show();
    }

    /** 懒初始化长按时长与移动取消阈值（系统设定，含无障碍加长时）。 */
    protected void initTouchConfig() {
        if (touchConfigReady) return;
        touchConfigReady = true;
        longPressTimeoutMs = ViewConfiguration.getLongPressTimeout();
        touchSlopPx = ViewConfiguration.get(ctl.activity).getScaledTouchSlop();
    }

    /** 本组一条弹幕完全滑入屏幕：计数减到 0 表示整组已进完，放行下一组「上中下」弹幕入场 */
    protected void onDanmakuOneEntered() {
        if (danmakuGroupEnterPending > 0) danmakuGroupEnterPending--;
        if (danmakuGroupEnterPending > 0) return;
        danmakuGroupRunning = false;
        pumpDanmakuGroup(0);
    }

    /**
     * 弹幕行 topMargin（相对 layout_danmaku 顶边，px）：
     * 把 DANMAKU_MAX_ROWS 行压缩进「弹幕层顶 ～ LP 血条底边」这条 GL 透明带内，
     * 使弹幕与血条同一高度水平滚动，且不被 GameFieldView 的场地/手卡纹理遮挡。
     * 行距 = min(设定行高 DANMAKU_ROW_HEIGHT_DP, 透明带高度 / 行数)；
     * 血条尚未就绪时兜底为按设定行高从顶累积。
     */
    protected int danmakuRowTopMargin(int row, int rowHeight) {
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
    protected int lpTopRel(int[] lpBar, int[] dmLoc) {
        return lpBar[0] - dmLoc[1];
    }

    protected FieldChatBoardBase(GameFieldController ctl) {
        this.ctl = ctl;
    }
}

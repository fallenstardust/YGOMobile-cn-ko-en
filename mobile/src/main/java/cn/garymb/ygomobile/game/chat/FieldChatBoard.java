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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.render.spec.SpecEffectOverlay;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.duel.DuelLogDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.PlayerWaitingDialog;

/**
 * 聊天/弹幕/大厅/表情协作类（由 GameFieldController 按 // === 分栏拆分而来）：
 * 决斗内分侧玩家聊天、系统/观战消息弹幕、player waiting 大厅聊天模式、表情气泡。
 * 通过包级私有直连门面 GameFieldController 的共享状态（engine/topInfoManager/
 * mainHandler/layoutChatMessages/tvChatMessage1/2/layoutDanmaku）。
 * 对齐 gframe game.cpp AddChatMsg + drawing.cpp DrawChatMsg/DrawEmoticon。
 */
/**
 * Public entry of the split inheritance chain (FieldChatBoardBase <- this).
 * Keeps the original class name and public API: chat routing (appendChat),
 * lobby chat mode, side-chat rebuild/swap/rotation, touch observation and
 * lifecycle clearing.
 */
public class FieldChatBoard extends FieldChatBoardBase {

    public FieldChatBoard(GameFieldController ctl) {
        super(ctl);
    }

    // === 聊天消息（对齐 gframe game.cpp AddChatMsg + drawing.cpp DrawChatMsg） ===

    public void appendChat(int playerType, String message) {
        // 消息记录·聊天tab（用户规格：始终记录全部到达的聊天，独立于下方显示屏蔽门控；表情码
        // 按原文入表）。回放模式的逐帧重现不再记录：录像聊天在打开时已经 preloadReplayChat
        // 一次性全量预载，避免重复入表
        if (ctl.engine == null || !ctl.engine.replayMode)
            DuelLogDialog.addChat(formatChatLogLine(playerType, message));
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
    /**
     * 我方/对方侧玩家聊天（含同队 tag 队友）：对局玩家聊天不是弹幕——在发送方
     * LP 血条正下方自上而下逐行显示（我方队→我方血条下、对方队→对方血条下，
     * 横竖屏同规格），最多 MAX_CHAT_LINES 行、超出移除最上方最旧一条（等效上滚）。
     * 容器由 SpecEffectOverlay 的 PopupWindow 承载（在 GL 曲面之上；tv_chat_message_1/2
     * 是普通 View，会被 setZOrderOnTop(true) 的 GameFieldView 整层遮挡，历史根因），
     * 颜色对齐 drawing.cpp chatColor[0..3]（玩家消息白色），前缀已由调用方拼好「昵称: 内容」。
     * 仅系统/观战消息在 showChatDanmaku 中以弹幕形式横向滚动。
     */
    protected void appendSideChat(boolean selfSide, String line) {
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
    /** 重建指定侧聊天行容器：把 pending 消息自上而下追加到该侧血条正下方；
     *  血条尚未布局时经 mainHandler 延后重试（上限同弹幕），重试用尽/容器不可用
     *  则回退为弹幕带显示，不丢消息 */
    protected void rebuildSideChatLayer(boolean selfSide, int retries) {
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
    /**
     * 切换视角（观战/录像 ReplaySwap）时左右对调双方聊天内容：仅对调 gametopinfo 昵称会让
     * player1 的消息错显示到 player2 一侧，故连同两侧聊天行一并对调，再按新视角整列重挂。
     * 聊天行文本内已内嵌发送者昵称前缀，整行随左右搬迁即与对调后的玩家名天然对齐。
     */
    public void swapChatSides() {
        swapListContent(myChatLines, opChatLines);
        swapListContent(myChatPending, opChatPending);
        relayoutSideChatRows(true, 0);
        relayoutSideChatRows(false, 0);
    }
    /**
     * 屏幕旋转（rebindAfterRotation 复用同一实例）后回灌双方聊天：覆盖层/回退层旧视图随旋转销毁，
     * 撤销本类持有的弹幕视图引用与暂存（不丢 myChatLines/opChatLines 聊天历史），
     * 再把保存的双方聊天行整列重挂到新覆盖层，避免横竖屏切换后聊天记录丢失（用户反馈）。
     */
    public void retainAcrossRotation() {
        cancelPendingLongPress();
        danmakuHeld = false;   // 旋转清场：撤销按住减速态，避免残留标志使新弹幕错速
        for (TextView tv : danmakuViews) {
            tv.animate().cancel();
        }
        danmakuViews.clear();
        danmakuQueue.clear();
        danmakuGroupRunning = false;
        danmakuGroupEnterPending = 0;
        danmakuPumpGeneration++;
        myChatPending.clear();
        opChatPending.clear();
        // 大厅聊天容器挂在旧 layout_danmaku 上，重建后失效：置空待下次 enterLobbyChatMode 重建
        lobbyChatContainer = null;
        if (!lobbyChatMode) {
            relayoutSideChatRows(true, 0);
            relayoutSideChatRows(false, 0);
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
    public void enterLobbyChatMode() {
        lobbyChatMode = true;
        clearDanmaku();
        // 聊天/提示容器用 INVISIBLE 而非 GONE：GONE 会让整行退出布局而改变相机上锚线（HUD
        // 视觉底边），决斗场取景随之重解；大厅聊天不显示此列内容，保留占位不影响任何观感
        if (ctl.layoutChatMessages != null)
            ctl.layoutChatMessages.setVisibility(View.INVISIBLE);
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
    public void exitLobbyChatMode() {
        if (!lobbyChatMode) return;
        lobbyChatMode = false;
        pendingLobbyMsgs.clear();
        if (lobbyChatContainer != null) lobbyChatContainer.removeAllViews();
        clearDanmaku();
    }

    /** 大厅聊天列表追加：旧→新从上往下排列，每条独立半透明黑底；超过 10 条移除最上方旧消息；颜色对齐 drawing.cpp chatColor */
    protected void appendLobbyChat(int playerType, String message) {
        if (lobbyChatContainer == null) return;
        if (message == null) message = "";
        // 玩家(0-3)表情在等待界面对应席位名右对齐处显示气泡（用户规格：player waiting 可
        // 发表情，气泡跟随席位）：纯表情码直接转气泡；内嵌 &xxx 表情码提取转气泡、
        // 剩余文字照常入大厅列表；剥离后无剩余文字则只显示气泡不显空行
        if (playerType >= 0 && playerType < 4) {
            PlayerWaitingDialog dlg = ctl.activity.getPlayerWaitingDialog();
            if (isEmoticonCode(message)) {
                if (dlg != null) dlg.showSeatEmote(playerType, message);
                return;
            }
            StringBuilder emoteSb = new StringBuilder();
            List<String> embeddedCodes = extractEmbeddedEmoticonCodes(message, emoteSb);
            if (!embeddedCodes.isEmpty()) {
                if (dlg != null) {
                    for (String code : embeddedCodes) dlg.showSeatEmote(playerType, code);
                }
                message = emoteSb.toString().trim();
                if (message.isEmpty()) return;
            }
        }
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
        // 等待界面（PlayerWaitingDialog 显示中）聊天/系统消息长按复制：大厅聊天行挂在
        // Activity 树的 layout_danmaku 上（排在 GameFieldView 之后，逆序派发优先命中），
        // 而等待弹窗对内容区以外的触摸经 DragFrameLayout 透传回 decorView 时不走
        // Activity.dispatchTouchEvent，决斗内的全局长按观察者收不到事件；改用视图自身的
        // 系统长按判定（透传手势同样投递到 decorView 子树），命中即复制该行并 Toast
        // 提示，与决斗内聊天行/弹幕长按复制同规格
        tv.setLongClickable(true);
        tv.setOnLongClickListener(v -> {
            copyChatText(((TextView) v).getText().toString());
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (2 * density);          // 每条之间留间隙，黑底不连成整块
        lobbyChatContainer.addView(tv, lp);
        // 超过 10 条：移除最上方最旧的一条
        while (lobbyChatContainer.getChildCount() > MAX_LOBBY_CHAT_LINES) {
            lobbyChatContainer.removeViewAt(0);
        }
    }
    // === 决斗内聊天行/弹幕长按观察（覆盖层事件穿透，触摸由 Activity 派发后命中文字） ===

    /**
     * 由 {@code YGOProActivity.dispatchTouchEvent} 转发的全局触摸观察（不消费事件，普通点按仍穿透到
     * 下层决斗场）：
     * · 按住血条下方聊天行 → 复制该行文本（静态不滚动，无需减速）；
     * · 按住横向弹幕 → 复制该条文本，并把全部弹幕减速至当前速度的一半，松开（ACTION_UP/CANCEL）恢复原速。
     * 起点命中判定在长按超时（ViewConfiguration 长按时长）且未超 slop 移动时成立。
     */
    public void onFieldTouch(MotionEvent e) {
        initTouchConfig();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downWinX = e.getX();
                downWinY = e.getY();
                final float x = downWinX, y = downWinY;
                cancelPendingLongPress();
                pendingLongPress = () -> onLongPressHit(x, y);
                ctl.mainHandler.postDelayed(pendingLongPress, longPressTimeoutMs);
                return;
            }
            case MotionEvent.ACTION_MOVE: {
                // 长按成立前移动超 slop 视为滑动/正常操作，取消长按；成立后（已 held）保持到松开
                if (pendingLongPress != null) {
                    float dx = e.getX() - downWinX;
                    float dy = e.getY() - downWinY;
                    if (dx * dx + dy * dy > touchSlopPx * touchSlopPx) {
                        cancelPendingLongPress();
                    }
                }
                return;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                cancelPendingLongPress();
                if (danmakuHeld) setDanmakuHeld(false);   // 松开恢复原速
                return;
            }
            default:
        }
    }
    /**
     * 聊天tab行格式化（与 appendLobbyChat 的前缀/取名口径一致，但不受任何屏蔽设置影响）：
     * 玩家(0-3)→「昵称: 内容」（昵称经 chatLocalType/chatNameByLocalType 按先攻视角取值，
     * 实况与回放预载同源）；8→[System]；9→[Script Error]；观战 11-19 无前缀（对齐大厅列表，
     * 观战昵称服务端不下发）；其余（4-7/10/20+ 含归一前的隐藏名路径）→[********]。
     */
    protected String formatChatLogLine(int playerType, String message) {
        if (message == null) message = "";
        if (playerType >= 0 && playerType < 4)
            return chatNameByLocalType(chatLocalType(playerType)) + ": " + message;
        if (playerType == 8) return "[System]: " + message;
        if (playerType == 9) return "[Script Error]: " + message;
        if (playerType >= 11 && playerType <= 19) return message;
        return "[********]: " + message;
    }

    /**
     * 录像聊天预载（用户规格：打开录像即全部可见）：一次性把聊天时间线内全部 0xF1 帧体
     * 按帧序（即时间序）解析格式化后灌入消息记录·聊天tab，无需播放到对应时间点；先清旧表
     * 防重复点播叠加。在引擎主线程调用（EngineCallbackDelegate 转发），此时 replayMode 与
     * playerInfos 昵称已由 ReplayPlayer.load 的 startSession 先行写好，取名与实况同源。
     */
    public void preloadReplayChat(List<byte[]> bodies) {
        DuelLogDialog.clearChats();
        if (bodies == null) return;
        for (byte[] body : bodies) {
            if (body == null || body.length < 2) continue;
            int playerType = body[0] & 0xFF;
            String text = new String(body, 1, body.length - 1,
                    StandardCharsets.UTF_8);
            if (text.isEmpty()) continue;
            DuelLogDialog.addChat(formatChatLogLine(playerType, text));
        }
    }

    /** 隐藏聊天消息文本（对齐 gframe BUTTON_CHATTING 切换关闭时的 ClearChatMsg：清空聊天显示） */
    public void clearChatMessages() {
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
    protected boolean isEmoticonCode(String message) {
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
    protected List<String> extractEmbeddedEmoticonCodes(String text, StringBuilder remaining) {
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
    protected void showEmoteBubble(int playerType, String code) {
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
    public void resetOnHide() {
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

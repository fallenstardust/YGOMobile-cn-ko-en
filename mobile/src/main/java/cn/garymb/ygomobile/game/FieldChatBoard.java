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

    private final LinkedList<String> myChatLines = new LinkedList<>();
    private final LinkedList<String> opChatLines = new LinkedList<>();

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
    /** 弹幕匀速（dp/ms）：时长 = 总路程 / 速度，所有消息速度一致 */
    private static final float DANMAKU_SPEED_DP_PER_MS = 0.08f;
    /** 弹幕行高（dp）：3 行带总高约 48dp，贴屏幕顶部自上而下排列 */
    private static final float DANMAKU_ROW_HEIGHT_DP = 16f;
    /** 观战弹幕颜色，逐一对齐 drawing.cpp chatColor[11..19]（11=红 12=绿 13=蓝 14=青 15=品红 16=黄 17=白 18=灰 19=深灰） */
    private static final int[] DANMAKU_OBS_COLORS = {
            0xFFFF4040, 0xFF40FF40, 0xFF4040FF, 0xFF40FFFF, 0xFFFF40FF,
            0xFFFFFF40, 0xFFFFFFFF, 0xFF808080, 0xFF404040
    };
    /** 聊天消息半透明黑底（对齐 drawing.cpp L1597 draw2DRectangle 0xa0000000） */
    private static final int CHAT_BG_COLOR = 0xA0000000;

    private int danmakuRowIndex = 0;
    private final List<TextView> danmakuViews = new ArrayList<>();

    /** 大厅（player waiting）聊天模式：全部消息在 layout_danmaku 静态列表显示 */
    private boolean lobbyChatMode = false;
    /** 大厅聊天最大条数：超过时移除最上方最旧的一条 */
    private static final int MAX_LOBBY_CHAT_LINES = 10;
    /** 大厅聊天列表容器：每条消息一个 TextView（独立半透明黑底），旧→新从上往下排列 */
    private LinearLayout lobbyChatContainer;

    FieldChatBoard(GameFieldController ctl) {
        this.ctl = ctl;
    }

    // === 聊天消息（对齐 gframe game.cpp AddChatMsg + drawing.cpp DrawChatMsg） ===

    void appendChat(int playerType, String message) {
        // player waiting 大厅模式：所有系统消息与玩家聊天进入 layout_danmaku 静态列表，
        // 不走决斗内的分侧聊天/弹幕逻辑（决斗开始时由 exitLobbyChatMode 切回）
        if (lobbyChatMode) {
            appendLobbyChat(playerType, message);
            return;
        }
        AppsSettings settings = AppsSettings.get();
        // 对齐 gframe duelclient.cpp STOC_CHAT：停用聊天（chkDisableChatting，对应 chkIgnore1）时丢弃全部消息
        if (settings.getIntSettings("chkDisableChatting", 0) == 1) return;
        if (message == null) message = "";
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
            // 系统/脚本错误/观战消息：对齐 chkIgnore2，观战者（11-19）可屏蔽
            if (playerType >= 11 && playerType <= 19
                    && settings.getIntSettings("chkMuteSpectators", 0) == 1) return;
            showChatDanmaku(playerType, message);
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
        if (ctl.engine.isStarted() || ctl.engine.isSiding()) {
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

    /** 我方/对方聊天各占一个 TextView：每条换行，超过 5 行清除第一条（向上滚动），宽度不超过上方 LPbar */
    private void appendSideChat(boolean selfSide, String line) {
        TextView tv = selfSide ? ctl.tvChatMessage1 : ctl.tvChatMessage2;
        if (tv == null) return;
        LinkedList<String> lines = selfSide ? myChatLines : opChatLines;
        lines.addLast(line);
        while (lines.size() > MAX_CHAT_LINES) {
            lines.removeFirst();
        }
        // 对齐 drawing.cpp 玩家聊天 maxwidth：最大长度不超过上方 LPbar
        int maxW = selfSide ? ctl.topInfoManager.getPlayerLpBarWidth()
                : ctl.topInfoManager.getOpponentLpBarWidth();
        if (maxW > 0) tv.setMaxWidth(maxW);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        tv.setText(sb.toString());
        tv.setVisibility(View.VISIBLE);
        if (ctl.layoutChatMessages != null) ctl.layoutChatMessages.setVisibility(View.VISIBLE);
    }

    /** 清除全部进行中的弹幕（停止聊天/离开决斗界面时调用）：从当前宿主容器（drawspec 弹幕层
     *  或回退的 layout_danmaku）移除，并通知覆盖层空闲收口（无特效时关闭 PopupWindow） */
    private void clearDanmaku() {
        for (TextView tv : danmakuViews) {
            tv.animate().cancel();
            Object p = tv.getParent();
            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(tv);
            else if (ctl.layoutDanmaku != null) ctl.layoutDanmaku.removeView(tv);
        }
        danmakuViews.clear();
        danmakuRowIndex = 0;
        SpecEffectOverlay overlay = ctl.activity.getSpecOverlay();
        if (overlay != null) overlay.notifyDanmakuRemoved();
    }

    // === player waiting 大厅聊天模式 ===

    /**
     * 进入大厅聊天模式：所有系统消息与玩家聊天在 layout_danmaku 中按
     * 从上往下、旧到新的静态列表显示（每条一个 TextView、半透明黑底），
     * 最多 10 条，超出移除最上方最旧的一条；
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
    }

    /** 决斗开始：退出大厅聊天模式，恢复决斗内玩家分侧聊天 + 系统/观战弹幕 */
    void exitLobbyChatMode() {
        if (!lobbyChatMode) return;
        lobbyChatMode = false;
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
            color = 0xFFFFFFFF;
        } else if (playerType == 8) {
            text = "[System]: " + message;
            color = 0xFF8080FF;                       // chatColor[8]
        } else if (playerType == 9) {
            text = "[Script Error]: " + message;
            color = 0xFFFF4040;                       // chatColor[9]
        } else if (playerType == 10) {
            text = "[********]: " + message;
            color = 0xFFFF4040;                       // chatColor[10]
        } else {
            // 观战者 11-19（无前缀）与其他未知类型
            text = message;
            color = (playerType >= 11 && playerType <= 19)
                    ? DANMAKU_OBS_COLORS[playerType - 11] : 0xFFFFFFFF;
        }
        TextView tv = new TextView(ctl.activity);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);  // 与 tv_chat_message 统一 9sp
        tv.setTextColor(color);
        tv.setBackgroundColor(CHAT_BG_COLOR);               // 对齐 drawing.cpp draw2DRectangle 0xa0000000
        tv.setShadowLayer(1f, 1f, 1f, 0xFF000000);
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
     * 8 系统=0xFF8080FF，9 脚本错误/10 隐藏名=0xFFFF4040，11-19 观战=chatColor[11..19] 轮换。
     * 前缀对齐 game.cpp AddChatMsg：8→"[System]: "、9→"[Script Error]: "、10→"[********]: "、
     * 观战 11-19 无前缀（default 分支不追加）。
     * 弹幕宿主取 drawspec 覆盖层（SpecEffectOverlay 的 PopupWindow 层，不受 GameFieldView
     * setZOrderOnTop 的 GL 曲面遮挡）：贴屏幕顶部的全屏宽横带，高 = 3 行 × 行高，
     * 不再依赖 layout_top_info 的布局状态（历史上该依赖导致弹幕落回被遮挡的回退层而永不可见）。
     */
    private void showChatDanmaku(int playerType, String message) {
        if (message == null || message.isEmpty()) return;
        SpecEffectOverlay overlay = ctl.activity.obtainSpecOverlay();
        int bandHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DANMAKU_ROW_HEIGHT_DP * DANMAKU_MAX_ROWS,
                ctl.activity.getResources().getDisplayMetrics());
        FrameLayout layer = overlay != null ? overlay.obtainDanmakuLayer(bandHeight) : null;
        final FrameLayout parent = layer != null ? layer : ctl.layoutDanmaku;
        if (parent == null) return;
        if (parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            // 首帧尚未布局完成：延后到布局后再入场；回退层本身不可见（GONE/宽 0）时
            // 不无限重试，直接丢弃本条，避免消息堆积在永不执行的 post 队列里
            if (layer != null) {
                parent.post(() -> showChatDanmaku(playerType, message));
            } else {
                android.util.Log.d("Danmaku", "skip: no visible host, type=" + playerType);
            }
            return;
        }
        String text;
        int color;
        if (playerType == 8) {
            text = "[System]: " + message;
            color = 0xFF8080FF;                       // chatColor[8]
        } else if (playerType == 9) {
            text = "[Script Error]: " + message;
            color = 0xFFFF4040;                       // chatColor[9]
        } else if (playerType == 10) {
            text = "[********]: " + message;
            color = 0xFFFF4040;                       // chatColor[10]
        } else {
            text = message;
            color = (playerType >= 11 && playerType <= 19)
                    ? DANMAKU_OBS_COLORS[playerType - 11] : 0xFFFFFFFF;
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
        tv.setShadowLayer(1f, 1f, 1f, 0xFF000000);
        int row = danmakuRowIndex % DANMAKU_MAX_ROWS; // 超过 5 行循环回第 1 行
        danmakuRowIndex++;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        if (layer != null) {
            // drawspec 层：宿主即屏幕顶部全屏宽横带，3 行均分其高度（行高等于带高/3），
            // 容器默认裁剪子 View，弹幕恰以该带为界出入
            lp.topMargin = row * Math.max(1, parent.getHeight() / DANMAKU_MAX_ROWS);
        } else {
            int rowHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    DANMAKU_ROW_HEIGHT_DP, ctl.activity.getResources().getDisplayMetrics());
            // 回退层：垂直锚定到 LP 血条所在的顶部透明带（历史层级，受 GL 遮挡观感受限）
            lp.topMargin = danmakuRowTopMargin(row, rowHeight);
        }
        lp.leftMargin = parent.getWidth();            // 起点：宿主区右缘之外
        parent.addView(tv, lp);
        danmakuViews.add(tv);
        float density = ctl.activity.getResources().getDisplayMetrics().density;
        tv.post(() -> {
            // 匀速：总路程 = 宿主层宽度 + 自身宽度（一直移动到最左侧消失）
            int distance = parent.getWidth() + tv.getWidth();
            long duration = Math.max(1, (long) (distance / (DANMAKU_SPEED_DP_PER_MS * density)));
            tv.animate().translationX(-distance).setDuration(duration)
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

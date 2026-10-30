package cn.garymb.ygomobile.ui.dialogs.lobby;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.ui.dialogs.deck.DeckSelectorDialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;

/**
 * 玩家等待（大厅）界面（layout_player_waiting）：玩家/观战席位、准备状态、卡组选择、
 * 踢人、开始游戏、房间信息展示，以及来自引擎的玩家/房间/卡组校验事件处理。
 */
public class PlayerWaitingDialog extends PlayerWaitingDialogMid {

    /**
     * Public entry of the split inheritance chain
     * (PlayerWaitingDialogBase <- PlayerWaitingDialogMid <- this).
     * Keeps the original class name and public API: engine event
     * forwarding handlers and the static show entries used by
     * YGOProActivity / SingleModeDialog / MainMenuNavigator.
     */

    public PlayerWaitingDialog(Context context, OnPlayerWaitingListener listener) {
        super(context, listener);
    }

    // === 引擎事件处理（由 YGOProActivity 转发） ===
    // 注：以下 handleXxx 不得以 isShowing() 丢弃事件——弹窗窗口经 DraggablePopupHelper
    // bringHostPopupToFront 重排（removeViewImmediate+addView）后 isShowing 会误报 false，
    // 导致主机侧玩家名/ready 回显与 STOC_TYPE_CHANGE 被静默丢弃，
    // 开始按钮永远灰色、WindBot 加入不可见（同 handleJoinGame 修复缘由）；
    // 视图均为 show() 中同步创建且各 setter 自带判空，事件早到/窗已关时写入无害

    public void handlePlayerEnter(String name, int pos) {
        removeObserver(name);
        setPlayerName(pos, name);
        refreshPlayerDisplay();
    }

    public void handlePlayerChange(int status) {
        int pos = (status >> 4) & 0x0F;
        int state = status & 0x0F;

        if (state < 8) {
            String oldName = getPlayerName(pos);
            movePlayer(pos, state);
            if (!oldName.isEmpty() && state >= 4) {
                addObserver(oldName);
            }
            if (pos >= 4 && !oldName.isEmpty() && state < 4) {
                removeObserver(oldName);
            }
        } else if (state == 0x8) {
            String observerName = getPlayerName(pos);
            clearPlayerPos(pos);
            if (!observerName.isEmpty()) {
                addObserver(observerName);
            }
        } else if (state == 0x9) {
            setPlayerReady(pos, true);
        } else if (state == 0xa) {
            setPlayerReady(pos, false);
        } else if (state == 0xb) {
            String leavingName = getPlayerName(pos);
            clearPlayerPos(pos);
            if (!leavingName.isEmpty()) {
                removeObserver(leavingName);
            }
        }
        refreshPlayerDisplay();
    }

    public void handleWatchChange(int watchCount) {
        updateWatchCount(watchCount);
        refreshPlayerDisplay();
    }

    public void handleJoinGame(int lflist, int rule, int mode, int duelRule, int noCheckDeck, int noShuffleDeck, int startLp, int startHand, int drawCount, int timeLimit) {
        // 视图在 show() 中已同步创建：不再以 isShowing() 丢弃回调
        //（弹窗真正就绪可能晚于 STOC_JOIN_GAME 到达，丢弃后房间信息永久停留“----”）
        updateRoomInfo(lflist, rule, mode, duelRule, noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, timeLimit);
    }

    public void handleTypeChange(int type, boolean isTag) {
        int selfType = type & 0x0F;
        boolean isHost = ((type >> 4) & 0x0F) != 0;
        updateTypeChange(selfType, isTag, isHost);
    }

    public void handleDeckError(int errorType, int cardCode) {
        // 卡名直接拼入错误文本的「%ls」占位（解析不到卡名时回退显示 code），不再另起一行提示卡名
        String cardName = "";
        if (cardCode > 0 && errorType != YGOProtocol.DECKERROR_MAINCOUNT
                && errorType != YGOProtocol.DECKERROR_EXTRACOUNT
                && errorType != YGOProtocol.DECKERROR_SIDECOUNT
                && cardNameResolver != null) {
            String resolved = cardNameResolver.resolve(cardCode);
            cardName = resolved != null ? resolved : "";
        }
        String nameOrCode = cardName.isEmpty() ? String.valueOf(cardCode) : cardName;
        String errorDesc;
        switch (errorType) {
            case YGOProtocol.DECKERROR_LFLIST:
                errorDesc = mStringManager.getSystemString(1407, "「%ls」的数量不符合当前禁限卡表设定。")
                        .replace("%ls", nameOrCode);
                break;
            case YGOProtocol.DECKERROR_OCGONLY:
                errorDesc = mStringManager.getSystemString(1413, "「%ls」为OCG独有卡，不允许在当前设定下使用。")
                        .replace("%ls", nameOrCode);
                break;
            case YGOProtocol.DECKERROR_TCGONLY:
                errorDesc = mStringManager.getSystemString(1414, "「%ls」为TCG独有卡，不允许在当前设定下使用。")
                        .replace("%ls", nameOrCode);
                break;
            case YGOProtocol.DECKERROR_UNKNOWNCARD:
                errorDesc = mStringManager.getSystemString(1415, "卡组中「%ls(%d)」尚不支持在本主机使用")
                        .replace("%ls", nameOrCode)
                        .replace("%d", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_CARDCOUNT:
                errorDesc = mStringManager.getSystemString(1416, "卡组中「%ls」的总数量超过3张。")
                        .replace("%ls", nameOrCode);
                break;
            case YGOProtocol.DECKERROR_MAINCOUNT:
                errorDesc = mStringManager.getSystemString(1417, "主卡组数量应为40-60张，当前卡组数量为%d张。")
                        .replace("%d", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_EXTRACOUNT:
                errorDesc = mStringManager.getSystemString(1418, "额外卡组数量超限(%ls张)")
                        .replace("%ls", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_SIDECOUNT:
                errorDesc = mStringManager.getSystemString(1419, "副卡组数量应不超过15张，当前卡组数量为%d张。")
                        .replace("%d", String.valueOf(cardCode));
                break;
            case YGOProtocol.DECKERROR_NOTAVAIL:
                errorDesc = mStringManager.getSystemString(1420, "有额外卡组卡片存在于主卡组，可能是额外卡组数量超过15张。");
                break;
            default:
                errorDesc = mStringManager.getSystemString(1421, "未知卡组错误(type=%ls)")
                        .replace("%ls", String.valueOf(errorType));
                break;
        }

        String title = mStringManager.getSystemString(1725, "卡组验证失败");
        YesOrNoDialog dialog = new YesOrNoDialog(context);
        dialog.setTitle(title).setMessage(errorDesc);
        dialog.show();
    }
    // === 静态入口：由 YGOProActivity 调用（直连/人机直接进入玩家等待界面） ===

    public static void showPlayerWaitingForDirectJoin(YGOProActivity activity, YGOGameOptions options) {
        activity.hideGameUI();
        String name = Constants.PlayerName;
        if (options != null && options.mUserName != null && !options.mUserName.isEmpty()) {
            name = options.mUserName;
        }
        final String playerName = name;
        View anchor = activity.getDialogContainer();
        anchor.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            PlayerWaitingDialog dialog = new PlayerWaitingDialog(activity, activity.getPlayerWaitingListener());
            activity.setPlayerWaitingDialog(dialog);
            dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
            dialog.show(anchor);
            dialog.setPlayerName(0, playerName);
        });
    }

    public static void showPlayerWaitingForBotHost(YGOProActivity activity) {
        activity.hideGameUI();
        final String playerName = Constants.PlayerName;
        View anchor = activity.getDialogContainer();
        anchor.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            PlayerWaitingDialog dialog = new PlayerWaitingDialog(activity, activity.getPlayerWaitingListener());
            activity.setPlayerWaitingDialog(dialog);
            dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
            dialog.show(anchor);
            dialog.setPlayerName(0, playerName);
		});
    }

    /**
     * 纯单人模式(左右互搏)建主后进入玩家等待界面（房主）：与人机主机入口同形，
     * 额外据 engine.soloMode 开关 solo UI（选择对方卡组），对齐 MainMenuNavigator.showPlayerWaiting。
     */
    public static void showPlayerWaitingForSoloHost(YGOProActivity activity) {
        activity.hideGameUI();
        final String playerName = Constants.PlayerName;
        View anchor = activity.getDialogContainer();
        anchor.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            PlayerWaitingDialog dialog = new PlayerWaitingDialog(activity, activity.getPlayerWaitingListener());
            activity.setPlayerWaitingDialog(dialog);
            dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
            dialog.show(anchor);
            dialog.setPlayerName(0, playerName);
            final GameEngine engine = activity.getEngine();
            dialog.setSoloMode(engine != null && engine.soloMode);
        });
    }
}

package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.game.ReplayPlayer;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.activities.ShareFileActivity;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.Constants;
import ocgcore.DataManager;

public class ReplayModeDialog {

    /**
     * 进入回放前选中的录像绝对路径：退出回放重新打开本界面时用于还原列表选中
     * （一次性消费：还原后置空，避免下次从主菜单进入时也预选中）
     */
    private static String lastSelectedReplayPath;

    private Context context;
    private PopupWindow popupWindow;
    private File selectedReplayFile;
    private int startTurn = 1;
    private File replayDir;
    private SimpleListAdapter replayAdapter;
    private DraggablePopupHelper draggableHelper;
    
    private Button btnShareReplay;
    private Button btnExtractDeck;
    private Button btnDeleteReplay;
    private Button btnLoadReplay;
    private Button btnRenameReplay;
    private Button btnExitReplay;
    private EditText etStartTurn;

    public interface OnReplaySelectedListener {
        void onReplaySelected(String replayFilePath, int startTurn);
    }

    private OnReplaySelectedListener listener;

    public ReplayModeDialog(Context context, OnReplaySelectedListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void show(View anchorView, File replayDir) {
        this.replayDir = replayDir;
        View customView = LayoutInflater.from(DialogScale.wrap(context)).inflate(R.layout.popup_window_replay_mode, null);

        ListView lvReplayList = customView.findViewById(R.id.lv_replay_list);
        TextView tvReplayInfo = customView.findViewById(R.id.tv_replay_info);
        etStartTurn = customView.findViewById(R.id.et_start_turn);
        btnShareReplay = customView.findViewById(R.id.btn_share_replay);
        btnExtractDeck = customView.findViewById(R.id.btn_extract_deck);
        btnDeleteReplay = customView.findViewById(R.id.btn_delete_replay);
        btnLoadReplay = customView.findViewById(R.id.btn_load_replay);
        btnRenameReplay = customView.findViewById(R.id.btn_rename_replay);
        btnExitReplay = customView.findViewById(R.id.btn_exit_replay);

        setupLabels(customView);

        replayAdapter = new SimpleListAdapter(context);
        refreshReplayList();
        lvReplayList.setAdapter(replayAdapter);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        
        // 横屏时，根据 Activity 的实际宽度的 2/3 来限制弹窗宽度；竖屏保持正方形样式
        final android.app.Activity act = context instanceof android.app.Activity
                ? (android.app.Activity) context : null;
        if (act != null && act.getWindow() != null && act.getWindow().getAttributes() != null) {
            DisplayMetrics screen = new DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(screen);
            // 判断横屏还是竖屏
            boolean isLandscape = screen.widthPixels >= screen.heightPixels;
            if (isLandscape) {
                // 横屏：宽度取屏幕宽度的 2/3
                int maxWidth = (int) (screen.widthPixels * 0.66f);
                int[] fitted = DraggablePopupHelper.fitSizeToScreen(context, popupWidth, popupHeight);
                if (fitted[0] > maxWidth) {
                    // 按比例缩小高度
                    float ratio = (float) maxWidth / fitted[0];
                    fitted[0] = maxWidth;
                    fitted[1] = (int) (fitted[1] * ratio);
                }
                popupWidth = fitted[0];
                popupHeight = fitted[1];
            }
            // 竖屏：保持原有设计尺寸不变（已适配正方形）
        }
        // 传设计尺寸给 setupDraggablePopup：由其按当前屏宽统一限宽，并在屏幕旋转后按新屏宽重新解算
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                return true;
            }
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "replay_mode_dialog");
        // 竖屏宽铺满屏宽（与 Activity 同宽，按钮文字不被挤换行）并按高大于宽比例解算，转回横屏恢复（用户规格）
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight, true);

        // 初始状态下禁用所有按钮（除了退出按钮）和EditText
        updateControlsState(false);

        lvReplayList.setOnItemClickListener((parent, view, position, id) -> {
            File[] files = getReplayFiles();
            if (files != null && position < files.length) {
                selectedReplayFile = files[position];
                replayAdapter.setSelectedPosition(position);
                updateReplayInfo(tvReplayInfo, selectedReplayFile);
                // 选中录像后启用控件
                updateControlsState(true);
            }
        });

        etStartTurn.setOnEditorActionListener((v, actionId, event) -> {
            try {
                String text = etStartTurn.getText().toString();
                if (!text.isEmpty()) {
                    startTurn = Integer.parseInt(text);
                    if (startTurn < 1) {
                        startTurn = 1;
                        etStartTurn.setText("1");
                    }
                }
            } catch (NumberFormatException e) {
                etStartTurn.setText("1");
                startTurn = 1;
            }
            return false;
        });

        btnShareReplay.setOnClickListener(v -> {
            if (selectedReplayFile == null) {
                Toast.makeText(context, "请先选择录像", Toast.LENGTH_SHORT).show();
                return;
            }
            shareReplay(selectedReplayFile);
        });

        btnExtractDeck.setOnClickListener(v -> {
            if (selectedReplayFile == null) {
                Toast.makeText(context, "请先选择录像", Toast.LENGTH_SHORT).show();
                return;
            }
            extractDeck(selectedReplayFile);
        });

        btnDeleteReplay.setOnClickListener(v -> {
            if (selectedReplayFile == null) {
                Toast.makeText(context, "请先选择录像", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmDeleteReplay(selectedReplayFile, lvReplayList);
        });

        btnLoadReplay.setOnClickListener(v -> {
            if (selectedReplayFile == null) {
                Toast.makeText(context, "请先选择录像", Toast.LENGTH_SHORT).show();
                return;
            }
            loadReplay(selectedReplayFile);
        });

        btnRenameReplay.setOnClickListener(v -> {
            if (selectedReplayFile == null) {
                Toast.makeText(context, "请先选择录像", Toast.LENGTH_SHORT).show();
                return;
            }
            showRenameDialog(selectedReplayFile, lvReplayList);
        });

        // 退出回放重新进入时还原上一次播放的录像选中状态（对应桌面版回放窗口关闭回到录像选择界面）
        restoreLastSelectedReplay(lvReplayList, tvReplayInfo);

        btnExitReplay.setOnClickListener(v -> popupWindow.dismiss());

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
    }

    /** 界面文字统一复用 gframe 既有系统字符串（对齐 game.cpp wReplay），XML 中的中文仅作兜底 */
    private void setupLabels(View root) {
        ocgcore.StringManager sm = DataManager.get().getStringManager();
        TextView tvInfoTitle = root.findViewById(R.id.tv_replay_info_title);
        if (tvInfoTitle != null) tvInfoTitle.setText(sm.getSystemString(1349, "录像信息："));
        TextView tvStartTurn = root.findViewById(R.id.tv_start_turn_label);
        if (tvStartTurn != null) tvStartTurn.setText(sm.getSystemString(1353, "播放起始于回合："));
        if (btnShareReplay != null) btnShareReplay.setText(sm.getSystemString(1368, "分享录像"));
        if (btnExtractDeck != null) btnExtractDeck.setText(sm.getSystemString(1369, "提取卡组"));
        if (btnDeleteReplay != null) btnDeleteReplay.setText(sm.getSystemString(1361, "删除录像"));
        if (btnLoadReplay != null) btnLoadReplay.setText(sm.getSystemString(1348, "载入录像"));
        if (btnRenameReplay != null) btnRenameReplay.setText(sm.getSystemString(1362, "重命名"));
        if (btnExitReplay != null) btnExitReplay.setText(sm.getSystemString(1347, "退出"));
    }

    /** 若存在退出回放前播放过的录像且文件仍在列表中，则还原其选中与信息展示 */
    private void restoreLastSelectedReplay(ListView lvReplayList, TextView tvReplayInfo) {
        if (lastSelectedReplayPath == null) {
            return;
        }
        String path = lastSelectedReplayPath;
        lastSelectedReplayPath = null;
        File[] files = getReplayFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            if (path.equals(files[i].getAbsolutePath())) {
                selectedReplayFile = files[i];
                replayAdapter.setSelectedPosition(i);
                updateReplayInfo(tvReplayInfo, selectedReplayFile);
                updateControlsState(true);
                lvReplayList.setSelection(i);
                break;
            }
        }
    }

    private void updateControlsState(boolean enabled) {
        int textColor = enabled ? 0xFFFFFFFF : 0x88FFFFFF;
        
        btnShareReplay.setEnabled(enabled);
        btnShareReplay.setTextColor(textColor);
        
        btnExtractDeck.setEnabled(enabled);
        btnExtractDeck.setTextColor(textColor);
        
        btnDeleteReplay.setEnabled(enabled);
        btnDeleteReplay.setTextColor(textColor);
        
        btnLoadReplay.setEnabled(enabled);
        btnLoadReplay.setTextColor(textColor);
        
        btnRenameReplay.setEnabled(enabled);
        btnRenameReplay.setTextColor(textColor);
        
        etStartTurn.setEnabled(enabled);
        etStartTurn.setTextColor(textColor);
        if (!enabled) {
            etStartTurn.setHintTextColor(0x88FFFFFF);
        } else {
            etStartTurn.setHintTextColor(0x88FFFFFF);
        }
    }

    private File[] getReplayFiles() {
        if (replayDir == null || !replayDir.exists()) return null;
        File[] files = replayDir.listFiles((dir, name) -> name.endsWith(".yrp"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        return files;
    }

    private void refreshReplayList() {
        File[] files = getReplayFiles();
        List<String> nameList = new ArrayList<>();
        if (files != null && files.length > 0) {
            for (File f : files) {
                nameList.add(f.getName());
            }
        } else {
            nameList.add("（暂无录像文件）");
        }
        replayAdapter.set(nameList);
        replayAdapter.setSelectedPosition(-1);
        // 刷新列表时重置选中状态并禁用控件
        selectedReplayFile = null;
        updateControlsState(false);
    }

    private void shareReplay(File replayFile) {
        Intent intent = new Intent(context, ShareFileActivity.class);
        intent.putExtra(IrrlichtBridge.EXTRA_SHARE_TYPE, "yrp");
        intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, replayFile.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void extractDeck(File replayFile) {
        ReplayReader.ReplayData replayData = ReplayReader.loadReplay(replayFile.getAbsolutePath());
        if (replayData == null) {
            Toast.makeText(context, "无法加载录像", Toast.LENGTH_SHORT).show();
            return;
        }

        int playerCount = ReplayReader.getPlayerCount(replayData);
        List<String> playerNames = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            playerNames.add(ReplayReader.getPlayerName(replayData, i));
        }

        float density = context.getResources().getDisplayMetrics().density * DialogScale.factor(context);
        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(playerNames);
        adapter.setMultiSelectMode(true);

        ListView listView = new ListView(context);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (200 * density)));
        listView.setOnItemClickListener((parent, view, position, id) -> adapter.toggleSelection(position));

        // 外层容器承载 ListView 的固定高度：YesOrNoDialog 会把直接内容视图的 LayoutParams 覆盖为
        // MATCH_PARENT，用容器保留 200dp 多选列表高度，避免被撑满整屏
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(listView);

        YesOrNoDialog dialog = new YesOrNoDialog(context);
        dialog.setTitle("选择要提取的卡组（可多选）")
                .setContentView(box)
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("确定")
                .setNegativeButtonText("取消")
                .setCenterInView(windowCenterRegion())
                .setPositiveButton(v -> {
                    Set<Integer> selectedPositions = adapter.getMultiSelectedPositions();
                    if (selectedPositions.isEmpty()) {
                        Toast.makeText(context, "未选择要提取的卡组", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int successCount = 0;
                    for (int pos : selectedPositions) {
                        String deckFileName = replayFile.getName().replace(".yrp", "") + "_" + playerNames.get(pos) + ".ydk";
                        File deckFile = new File(replayDir.getParentFile(), "deck/" + deckFileName);
                        deckFile.getParentFile().mkdirs();
                        if (ReplayReader.saveDeck(replayData, pos, deckFile.getAbsolutePath())) {
                            successCount++;
                        }
                    }
                    Toast.makeText(context, successCount > 0
                            ? "成功提取 " + successCount + " 个卡组" : "提取失败",
                            Toast.LENGTH_SHORT).show();
                });
        dialog.show();
    }

    private void confirmDeleteReplay(File replayFile, ListView listView) {
        YesOrNoDialog dialog = new YesOrNoDialog(context);
        dialog.setTitle("确认删除")
                .setMessage("确定要删除录像 \"" + replayFile.getName() + "\" 吗？")
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("删除")
                .setNegativeButtonText("取消")
                .setCenterInView(windowCenterRegion())
                .setPositiveButton(v -> {
                    boolean deleted = ReplayReader.deleteReplay(replayFile.getAbsolutePath());
                    if (deleted) {
                        Toast.makeText(context, "已删除: " + replayFile.getName(), Toast.LENGTH_SHORT).show();
                        selectedReplayFile = null;
                        refreshReplayList();
                    } else {
                        Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
        dialog.show();
    }

    /**
     * 录像选择处于菜单语境（layout_game_right 隐藏），YesOrNoDialog 默认居中到该区域会错位，
     * 故取 Activity 内容根视图作为居中区域，使提取/删除/重命名子弹窗按整屏居中（对齐原 DialogPlus）
     */
    private View windowCenterRegion() {
        if (context instanceof Activity) {
            View content = ((Activity) context).findViewById(android.R.id.content);
            if (content != null) return content;
        }
        return null;
    }

    private void loadReplay(File replayFile) {
        // 记录本次播放的录像，供退出回放后重新打开本界面时还原选中
        lastSelectedReplayPath = replayFile.getAbsolutePath();
        if (popupWindow != null) {
            // 载入回放属内部跳转：先摘除 dismiss 回调，避免退场动画延迟触发 restoreMainMenu
            // 把主菜单叠在回放画面上
            popupWindow.setOnDismissListener(null);
            popupWindow.dismiss();
        }
        if (listener != null) {
            listener.onReplaySelected(replayFile.getAbsolutePath(), startTurn);
        }
    }

    private void showRenameDialog(File replayFile, ListView listView) {
        EditText editText = new EditText(DialogScale.wrap(context));
        editText.setText(replayFile.getName().replace(".yrp", ""));
        editText.selectAll();
        editText.setTextColor(0xFFFFFFFF);
        editText.setHintTextColor(0x88FFFFFF);

        YesOrNoDialog dialog = new YesOrNoDialog(context);
        dialog.setTitle("重命名录像")
                .setContentView(editText)
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("确定")
                .setNegativeButtonText("取消")
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                .setCenterInView(windowCenterRegion())
                .setPositiveButton(v -> {
                    String newName = editText.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean success = ReplayReader.renameReplay(replayFile.getAbsolutePath(), newName);
                    if (success) {
                        Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show();
                        selectedReplayFile = null;
                        refreshReplayList();
                    } else {
                        Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show();
                    }
                });
        dialog.show();
    }

    private void updateReplayInfo(TextView tvReplayInfo, File replayFile) {
        ReplayReader.ReplayData data = ReplayReader.loadReplay(replayFile.getAbsolutePath());
        if (data == null) {
            tvReplayInfo.setText("无法读取录像信息");
            return;
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = sdf.format(new Date(replayFile.lastModified()));
        sb.append(dateStr).append("\n");

        if (!data.playerNames.isEmpty()) {
            if (data.isTag && data.playerNames.size() >= 4) {
                // Tag模式：两两组队显示
                sb.append(data.playerNames.get(0)).append("\n");
                sb.append(data.playerNames.get(1)).append("\n");
                sb.append("===VS===\n");
                sb.append(data.playerNames.get(2)).append("\n");
                sb.append(data.playerNames.get(3)).append("\n");
            } else {
                // 普通模式
                for (int i = 0; i < data.playerNames.size(); i++) {
                    if (i > 0) sb.append("\n===VS===\n");
                    sb.append(data.playerNames.get(i));
                }
                sb.append("\n");
            }
        }
        /*TODO: 解析出来的其他信息暂时先不显示
        if (data.isTag) sb.append("[双打模式] ");
        if (data.isSingleMode) sb.append("[残局模式] ");
        if (!data.isTag && !data.isSingleMode) sb.append("[普通模式] ");
        sb.append("LP: ").append(data.params.startLp);
        sb.append(" | 手牌: ").append(data.params.startHand);
        sb.append(" | 抽卡: ").append(data.params.drawCount).append("\n");
        if (!data.decks.isEmpty()) {
            sb.append("\n主卡组: ");
            for (int i = 0; i < data.decks.size(); i++) {
                if (i > 0) sb.append(" / ");
                sb.append(data.decks.get(i).main.size()).append("张");
            }
        }*/

        tvReplayInfo.setText(sb.toString());
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(listener);
        }
    }

    // === 静态入口：由 YGOProActivity 调用 ===

    public static void showReplayModeDialog(YGOProActivity activity) {
        activity.getMainMenuDialog().hideMainMenu();
        File replayDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_REPLAY_PATH);
        ReplayModeDialog dialog = new ReplayModeDialog(activity, (replayPath, startTurn) -> {
            ReplayModeDialog.startReplayPlayback(activity, replayPath, startTurn);
        });
        dialog.show(activity.getDialogContainer(), replayDir);
        dialog.setOnDismissListener(() -> activity.getMainMenuDialog().restoreMainMenu());
    }

    /**
     * 起播录像：交给 {@link ReplayPlayer}（GameEngine 常驻协作件），本方法只负责
     * 切入决斗场 UI、挂 UI 回调与异常/结束弹窗。
     *
     * <p>不再新建回放专用引擎：回放的场地刷新、玩家信息、回合/阶段文字、召唤与连锁大图
     * 全部经实况管线（GameEngine → EngineCallbackDelegate）派发，与联机对局、观战同一条路径。
     */
    public static void startReplayPlayback(YGOProActivity activity, String replayPath, int startTurn) {
        final ReplayPlayer player = activity.getReplayPlayer();
        if (player == null) return;
        // 重复进入回放（外部再次打开 .yrp / 录像选择窗连续点播）：先摘掉旧回调，
        // loadAndPlay 内部会停掉旧投喂线程，剩余回调静默丢弃
        player.detachListener();
        // 进入回放先释放上一局残留的场上卡片局面：清 GameField + 重绘空场，
        // 避免异步加载窗口期 GameFieldView 仍显示上次决斗/回放的最后一帧（MSG_START 前的空白）
        activity.getFieldCtl().resetField();
        // 对齐 game.cpp Main::Replay → showFieldWindow：先切入决斗场 UI（隐藏主菜单/局域网弹窗），
        // 否则回放开始后主菜单仍覆盖在画面上
        activity.enterReplayUI();
        // 结束/错误弹窗只弹一次；quitReplay 触发的二次 FINISHED 状态被此标志拦截
        final AtomicBoolean endDlgShown = new AtomicBoolean(false);

        player.setListener(new ReplayPlayer.Listener() {
            @Override
            public void onReplayStateChanged(ReplayPlayer.State state) {
                activity.runOnUiThread(() -> {
                    switch (state) {
                        case PLAYING:
                            activity.getFieldCtl().setPhaseText("\u25b6");
                            activity.getCardDetailPanel().showReplayControls();
                            activity.getCardDetailPanel().updateReplayButtonStates(false);
                            // 回放会话直到本回调才真正建立（startSession 置 replayMode 在
                            // 加载线程，晚于 enterReplayUI 的聊天 UI 核算）：起播后重算，
                            // 录像观看属抑制场景隐藏聊天输入框与开关（对齐 gframe）
                            activity.updateChatUIVisibility();
                            break;
                        case PAUSED:
                            activity.getFieldCtl().setPhaseText("\u23f8");
                            activity.getCardDetailPanel().updateReplayButtonStates(true);
                            break;
                        case FINISHED:
                            activity.getFieldCtl().setPhaseText("\u23f9");
                            // 回放自然结束：即时隐藏录像控制条与残留时点/洗切按钮，
                            // 再弹结束提示框（确认后才 quitReplay 回录像选择窗）
                            activity.getCardDetailPanel().hideReplayControls();
                            activity.getCardDetailPanel().closeGameButtons();
                            showReplayEndDialog(activity, player, endDlgShown, false);
                            break;
                        case ERROR:
                            activity.getFieldCtl().setPhaseText("\u23f9");
                            // 对齐 MSG_RETRY 分支 L311-316："Error occurs." 提示后等待确认
                            showReplayEndDialog(activity, player, endDlgShown, true);
                            break;
                        default:
                            break;
                    }
                });
            }

            @Override
            public void onReplayHintMessage(String hint) {
                // 回放不显示顶部消息提示（对齐实况无 hint 浮层），仅保留日志便于排查
                Log.d("ReplayModeDialog", "replay hint: " + hint);
            }

            @Override
            public void onReplayFinished(int winner, int reason) {
                activity.runOnUiThread(() -> {
                    // 取回放中胜者的名字，用于胜利说明 "[胜者名] 原因" 前缀（reason<0x10 时）
                    String winnerName = null;
                    if (winner == 0 || winner == 1) {
                        ReplayReader.ReplayData rd = player.getReplayData();
                        if (rd != null && winner < rd.playerNames.size()) {
                            winnerName = rd.playerNames.get(winner);
                        }
                    }
                    // 不再在此处隐藏控制条：胜负文字先显示，结束提示弹窗由 FINISHED 状态统一弹出，
                    // 用户确认后经 quitReplay 退出回放回录像选择窗（对齐 C++ EndDuel 流程）
                    activity.showReplayResult(winner, reason, winnerName);
                });
            }
        });
        player.loadAndPlay(replayPath, startTurn);
    }

    /**
     * 回放结束提示框（对齐 replay_mode.cpp：EndDuel L228-232 弹系统串 1501、
     * MSG_RETRY L311-316 弹 "Error occurs."）：确认后退出回放回录像选择界面。
     * 为便于 debug，结束弹窗始终附带总步数与执行到的步数（旧格式重跑无法预知
     * 总步数时标注未知）；提前结束（重跑失步响应耗尽、截断转码产物等）另附原因
     */
    private static void showReplayEndDialog(YGOProActivity activity, ReplayPlayer player,
                                            AtomicBoolean shown, boolean forceError) {
        if (player == null || !shown.compareAndSet(false, true)) return;
        String err = player.getLastErrorMessage();
        if (forceError && err == null) err = "回放未能启动";
        // 步数描述：总步数不可知（旧格式重跑/V1 流）时标注未知；另附本会话跳过不播的 MSG_RETRY 条数
        int step = player.getCurrentStep();
        int total = player.getTotalSteps();
        int retries = player.getSkippedRetryCount();
        String totalText = total >= 0 ? String.valueOf(total) : "未知";
        String stepInfo = "总步数 " + totalText + "，执行到第 " + step + " 步"
                + (retries > 0 ? "，已跳过 " + retries + " 个 MSG_RETRY 步" : "");
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        if (err != null) {
            // 真正开始投喂过（步数大于0或总步数已知）才附带步数定位；加载失败不提示
            dialog.setTitle("回放异常")
                    .setMessage("Error occurs.\n" + err
                            + (step > 0 || total >= 0 ? "\n" + stepInfo + "结束" : ""));
        } else {
            String endText = DataManager.get().getStringManager().getSystemString(1501, "录像播放结束");
            if (player.isPlaybackCompleted()) {
                dialog.setMessage(endText + "\n" + stepInfo);
            } else {
                String note = player.getEarlyEndNote();
                dialog.setMessage(endText + "：录像未播完，" + stepInfo + "结束"
                        + (note == null ? "" : "（" + note + "）"));
            }
        }
        dialog.setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText("确定")
                .setPositiveButton(v -> quitReplay(activity))
                .setCenterInView(activity.findViewById(R.id.layout_game_right))
                .setCancelable(false)
                .show();
    }

    public static void hideReplayControls(YGOProActivity activity) {
        activity.getCardDetailPanel().hideReplayControls();
    }

    public static void quitReplay(YGOProActivity activity) {
        ReplayPlayer player = activity.getReplayPlayer();
        if (player != null) {
            player.detachListener();
            player.stop();
        }
        hideReplayControls(activity);
        // 退出回放不再直接回主菜单，而是重新打开录像选择界面并还原上次选中的录像：
        // 先做与主菜单显示等价的界面清理（隐藏决斗场/恢复菜单背景/菜单 BGM），
        // 但不弹出主菜单——主菜单留待本录像界面 dismiss 时再恢复
        activity.hideGameUI();
        activity.setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
        activity.getSoundManager().playBGM(SoundManager.BGM.MENU);
        showReplayModeDialog(activity);
    }
}

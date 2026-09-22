package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.activities.ShareFileActivity;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
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
        View customView = LayoutInflater.from(context).inflate(R.layout.popup_window_replay_mode, null);

        ListView lvReplayList = customView.findViewById(R.id.lv_replay_list);
        TextView tvReplayInfo = customView.findViewById(R.id.tv_replay_info);
        etStartTurn = customView.findViewById(R.id.et_start_turn);
        btnShareReplay = customView.findViewById(R.id.btn_share_replay);
        btnExtractDeck = customView.findViewById(R.id.btn_extract_deck);
        btnDeleteReplay = customView.findViewById(R.id.btn_delete_replay);
        btnLoadReplay = customView.findViewById(R.id.btn_load_replay);
        btnRenameReplay = customView.findViewById(R.id.btn_rename_replay);
        btnExitReplay = customView.findViewById(R.id.btn_exit_replay);

        replayAdapter = new SimpleListAdapter(context);
        refreshReplayList();
        lvReplayList.setAdapter(replayAdapter);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
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
        draggableHelper.setupDraggablePopup(popupWindow, customView, popupWidth, popupHeight);

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

        DialogPlus dialog = new DialogPlus(context);
        dialog.setTitle("选择要提取的卡组（可多选）");
        
        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(playerNames);
        adapter.setMultiSelectMode(true);
        
        ListView listView = new ListView(context);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                (int) (200 * context.getResources().getDisplayMetrics().density));
        listView.setLayoutParams(lp);
        
        dialog.setContentView(listView);
        dialog.setLeftButtonText("确定");
        dialog.setRightButtonText("取消");
        
        // 获取确定按钮并初始化为禁用状态
        Button btnOk = dialog.findViewById(android.R.id.button1);
        if (btnOk == null) {
            // 尝试通过布局ID获取
            View contentView = dialog.getContentView();
            if (contentView != null) {
                btnOk = contentView.findViewById(R.id.button_ok);
            }
        }
        
        final Button finalBtnOk = btnOk;
        if (finalBtnOk != null) {
            finalBtnOk.setEnabled(false);
            finalBtnOk.setTextColor(0x88FFFFFF);
        }
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            adapter.toggleSelection(position);
            
            // 根据选中数量更新确定按钮状态
            Set<Integer> selectedPositions = adapter.getMultiSelectedPositions();
            boolean hasSelection = !selectedPositions.isEmpty();
            
            if (finalBtnOk != null) {
                finalBtnOk.setEnabled(hasSelection);
                finalBtnOk.setTextColor(hasSelection ? 0xFFFFFFFF : 0x88FFFFFF);
            }
        });
        
        dialog.setLeftButtonListener((d, w) -> {
            Set<Integer> selectedPositions = adapter.getMultiSelectedPositions();
            
            int successCount = 0;
            for (int pos : selectedPositions) {
                String deckFileName = replayFile.getName().replace(".yrp", "") + "_" + playerNames.get(pos) + ".ydk";
                File deckFile = new File(replayDir.getParentFile(), "deck/" + deckFileName);
                deckFile.getParentFile().mkdirs();

                boolean success = ReplayReader.saveDeck(replayData, pos, deckFile.getAbsolutePath());
                if (success) {
                    successCount++;
                }
            }
            
            if (successCount > 0) {
                Toast.makeText(context, "成功提取 " + successCount + " 个卡组", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "提取失败", Toast.LENGTH_SHORT).show();
            }
            d.dismiss();
        });
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void confirmDeleteReplay(File replayFile, ListView listView) {
        DialogPlus dialog = new DialogPlus(context);
        dialog.setTitle("确认删除");
        dialog.setMessage("确定要删除录像 \"" + replayFile.getName() + "\" 吗？");
        dialog.setLeftButtonText("删除");
        dialog.setLeftButtonListener((d, w) -> {
            boolean deleted = ReplayReader.deleteReplay(replayFile.getAbsolutePath());
            if (deleted) {
                Toast.makeText(context, "已删除: " + replayFile.getName(), Toast.LENGTH_SHORT).show();
                selectedReplayFile = null;
                refreshReplayList();
            } else {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
            }
            d.dismiss();
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
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
        EditText editText = new EditText(context);
        editText.setText(replayFile.getName().replace(".yrp", ""));
        editText.selectAll();
        editText.setTextColor(0xFFFFFFFF);
        editText.setHintTextColor(0x88FFFFFF);

        DialogPlus dialog = new DialogPlus(context);
        dialog.setTitle("重命名录像");
        dialog.setContentView(editText);
        dialog.setLeftButtonText("确定");
        dialog.setLeftButtonListener((d, w) -> {
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
            d.dismiss();
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
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

    public static void startReplayPlayback(YGOProActivity activity, String replayPath, int startTurn) {
        if (activity.getEngine() == null) return;
        // 重复进入回放（外部再次打开 .yrp / 录像选择窗连续点播）：先静默并停掉旧引擎，
        // 避免旧回放线程的刷帧/弹窗回调干扰新回放
        ReplayEngine previous = activity.getCurrentReplayEngine();
        if (previous != null) {
            previous.detachListener();
            previous.stop();
            activity.setCurrentReplayEngine(null);
        }
        // 对齐 game.cpp Main::Replay → showFieldWindow：先切入决斗场 UI（隐藏主菜单/局域网弹窗），
        // 否则回放开始后主菜单仍覆盖在画面上
        activity.enterReplayUI();
        ReplayEngine replayEngine = new ReplayEngine(activity.getEngine().getField(), activity.getSoundManager());
        // 接入实况管线宿主：纯消息录像的切片消息投喂给 GameEngine（卡片动画/音效/大图全由实况侧产生）
        replayEngine.setEngine(activity.getEngine());
        activity.getEngine().setReplayEngine(replayEngine);
        activity.setCurrentReplayEngine(replayEngine);
        // 结束/错误弹窗只弹一次；quitReplay 触发的二次 FINISHED 状态被此标志拦截
        final AtomicBoolean endDlgShown = new AtomicBoolean(false);

        replayEngine.setListener(new ReplayEngine.ReplayListener() {
            @Override
            public void onReplayStateChanged(ReplayEngine.ReplayState state) {
                activity.runOnUiThread(() -> {
                    switch (state) {
                        case PLAYING:
                            activity.getFieldCtl().setPhaseText("▶");
                            activity.getCardDetailPanel().showReplayControls();
                            activity.getCardDetailPanel().updateReplayButtonStates(false);
                            break;
                        case PAUSED:
                            activity.getFieldCtl().setPhaseText("⏸");
                            activity.getCardDetailPanel().updateReplayButtonStates(true);
                            break;
                        case FINISHED:
                            activity.getFieldCtl().setPhaseText("⏹");
                            // 对齐 EndDuel（replay_mode.cpp L223-251）：结束后先弹提示框，
                            // 确认后才回录像选择窗并隐藏控制条；不再在 FINISHED 立即隐藏
                            // 控制按钮（修复回放提前终止时按钮莫名消失无法继续操作）
                            showReplayEndDialog(activity, replayEngine, endDlgShown, false);
                            break;
                        case ERROR:
                            activity.getFieldCtl().setPhaseText("⏹");
                            // 对齐 MSG_RETRY 分支 L311-316："Error occurs." 提示后等待确认
                            showReplayEndDialog(activity, replayEngine, endDlgShown, true);
                            break;
                    }
                });
            }

            @Override
            public void onReplayFieldChanged() {
                activity.getFieldCtl().invalidate();
                // 录像堆叠区查看列表弹窗即时刷新（与 EngineCallbackDelegate.onFieldChanged 同一入口），
                // invalidate 在 GL 线程安全，refreshLiveDialogs 回主线程取新列表
                activity.runOnUiThread(() -> CardDisplayDialog.refreshLiveDialogs());
            }

            @Override
            public void onReplayPlayerInfoUpdated(int player) {
                activity.runOnUiThread(() -> {
                    GameField.PlayerField pf = activity.getEngine().getField().players[player];
                    ReplayReader.ReplayData rd = replayEngine.getReplayData();
                    String name = (rd != null && player < rd.playerNames.size()) ? rd.playerNames.get(player) : "Player " + (player + 1);
                    activity.getTopInfoManager().setPlayerDisplay(player, name, String.valueOf(pf.lp));
                    activity.getTopInfoManager().updateLpBars(activity.getEngine().getField());
                });
            }

            @Override
            public void onReplayPhaseChanged(int phase) {
                activity.runOnUiThread(() -> {
                    activity.getFieldCtl().setPhaseByValue(phase);
                    // 回合数纯数字显示 + 回合方高亮（对齐实况 updateTurn；修复窄列 "Turn N" 被裁成 "Tu"）
                    GameField field = activity.getEngine().getField();
                    activity.getTopInfoManager().updateTurn(field.turnCount, field.currentPlayer == 0);
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
                        ReplayReader.ReplayData rd = replayEngine.getReplayData();
                        if (rd != null && winner < rd.playerNames.size()) {
                            winnerName = rd.playerNames.get(winner);
                        }
                    }
                    // 不再在此处隐藏控制条：胜负文字先显示，结束提示弹窗由 FINISHED 状态统一弹出，
                    // 用户确认后经 quitReplay 退出回放回录像选择窗（对齐 C++ EndDuel 流程）
                    activity.showReplayResult(winner, reason, winnerName);
                });
            }

            @Override
            public void onReplaySummonAnimation(int code, int summonType) {
                activity.showReplaySummonAnimation(code, summonType);
            }

            @Override
            public void onReplayPhaseText(int textCode) {
                activity.showReplayPhaseText(textCode);
            }

            @Override
            public void onReplayChainAnimation(int code, int controler, int location, int sequence) {
                activity.showReplayChainAnimation(code, controler, location, sequence);
            }

            @Override
            public void onReplayNegateAnimation(int code) {
                activity.showReplayNegateAnimation(code);
            }

            @Override
            public void onReplayTurnChanged(int turn, int currentPlayer) {
                // MSG_NEW_TURN 到达即更新回合数与回合方高亮（先于阶段切换）
                activity.runOnUiThread(() ->
                        activity.getTopInfoManager().updateTurn(turn, currentPlayer == 0));
            }
        });
        replayEngine.loadAndPlay(replayPath, startTurn);
    }

    /**
     * 回放结束提示框（对齐 replay_mode.cpp：EndDuel L228-232 弹系统串 1501、
     * MSG_RETRY L311-316 弹 "Error occurs."）：确认后退出回放回录像选择界面
     */
    private static void showReplayEndDialog(YGOProActivity activity, ReplayEngine engine,
                                            AtomicBoolean shown, boolean forceError) {
        // 仅当前活跃的回放引擎才弹窗：用户已退出（current 置 null）或已被新回放替换时拦截，
        // 避免旧引擎的 FINISHED/ERROR 回调对新回放弹出无关提示
        if (engine == null || activity.getCurrentReplayEngine() != engine) return;
        if (!shown.compareAndSet(false, true)) return;
        String err = engine != null ? engine.getLastErrorMessage() : null;
        if (forceError && err == null) err = "回放未能启动";
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        if (err != null) {
            dialog.setTitle("回放异常")
                    .setMessage("Error occurs.\n" + err);
        } else {
            String endText = DataManager.get().getStringManager().getSystemString(1501, "录像播放结束");
            dialog.setTitle(endText).setMessage(endText);
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
        activity.setCurrentReplayEngine(null);
    }

    public static void quitReplay(YGOProActivity activity) {
        if (activity.getCurrentReplayEngine() != null) activity.getCurrentReplayEngine().stop();
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

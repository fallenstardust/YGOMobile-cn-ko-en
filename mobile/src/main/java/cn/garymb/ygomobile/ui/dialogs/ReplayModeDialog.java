package cn.garymb.ygomobile.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.ShareFileActivity;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.Constants;

public class ReplayModeDialog {

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
        View customView = LayoutInflater.from(context).inflate(R.layout.dialog_replay_mode, null);

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
        draggableHelper.setupDraggablePopup(popupWindow, customView);

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

        btnExitReplay.setOnClickListener(v -> popupWindow.dismiss());

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
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
        popupWindow.dismiss();
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
        String dateStr = sdf.format(new java.util.Date(replayFile.lastModified()));
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
}

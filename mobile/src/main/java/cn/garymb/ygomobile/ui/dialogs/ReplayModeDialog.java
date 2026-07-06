package cn.garymb.ygomobile.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.ShareFileActivity;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;

public class ReplayModeDialog {

    private Context context;
    private PopupWindow popupWindow;
    private File selectedReplayFile;
    private int startTurn = 1;
    private File replayDir;

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
        EditText etStartTurn = customView.findViewById(R.id.et_start_turn);
        Button btnShareReplay = customView.findViewById(R.id.btn_share_replay);
        Button btnExtractDeck = customView.findViewById(R.id.btn_extract_deck);
        Button btnDeleteReplay = customView.findViewById(R.id.btn_delete_replay);
        Button btnLoadReplay = customView.findViewById(R.id.btn_load_replay);
        Button btnRenameReplay = customView.findViewById(R.id.btn_rename_replay);
        Button btnExitReplay = customView.findViewById(R.id.btn_exit_replay);

        refreshReplayList(lvReplayList);

        float density = context.getResources().getDisplayMetrics().density;
        int popupWidth = (int) (560 * density);
        int popupHeight = (int) (320 * density);
        popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        lvReplayList.setOnItemClickListener((parent, view, position, id) -> {
            File[] files = getReplayFiles();
            if (files != null && position < files.length) {
                selectedReplayFile = files[position];
                updateReplayInfo(tvReplayInfo, selectedReplayFile);
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
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0, 0);
    }

    private File[] getReplayFiles() {
        if (replayDir == null || !replayDir.exists()) return null;
        File[] files = replayDir.listFiles((dir, name) -> name.endsWith(".yrp"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        return files;
    }

    private void refreshReplayList(ListView listView) {
        File[] files = getReplayFiles();
        List<String> nameList = new ArrayList<>();
        if (files != null && files.length > 0) {
            for (File f : files) {
                nameList.add(f.getName());
            }
        } else {
            nameList.add("（暂无录像文件）");
        }

        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(nameList);
        listView.setAdapter(adapter);
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
        String[] playerNames = new String[playerCount];
        for (int i = 0; i < playerCount; i++) {
            playerNames[i] = ReplayReader.getPlayerName(replayData, i);
        }

        new AlertDialog.Builder(context)
                .setTitle("选择要提取的卡组")
                .setItems(playerNames, (dialog, which) -> {
                    String deckFileName = replayFile.getName().replace(".yrp", "") + "_" + playerNames[which] + ".ydk";
                    File deckFile = new File(replayDir.getParentFile(), "deck/" + deckFileName);
                    deckFile.getParentFile().mkdirs();

                    boolean success = ReplayReader.saveDeck(replayData, which, deckFile.getAbsolutePath());
                    if (success) {
                        Toast.makeText(context, "卡组已保存: " + deckFileName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteReplay(File replayFile, ListView listView) {
        new AlertDialog.Builder(context)
                .setTitle("确认删除")
                .setMessage("确定要删除录像 \"" + replayFile.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean deleted = ReplayReader.deleteReplay(replayFile.getAbsolutePath());
                    if (deleted) {
                        Toast.makeText(context, "已删除: " + replayFile.getName(), Toast.LENGTH_SHORT).show();
                        selectedReplayFile = null;
                        refreshReplayList(listView);
                    } else {
                        Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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

        new AlertDialog.Builder(context)
                .setTitle("重命名录像")
                .setView(editText)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean success = ReplayReader.renameReplay(replayFile.getAbsolutePath(), newName);
                    if (success) {
                        Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show();
                        selectedReplayFile = null;
                        refreshReplayList(listView);
                    } else {
                        Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateReplayInfo(TextView tvReplayInfo, File replayFile) {
        ReplayReader.ReplayData data = ReplayReader.loadReplay(replayFile.getAbsolutePath());
        if (data == null) {
            tvReplayInfo.setText("无法读取录像信息");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("文件名: ").append(replayFile.getName()).append("\n");

        if (!data.playerNames.isEmpty()) {
            for (int i = 0; i < data.playerNames.size(); i++) {
                if (i > 0) sb.append(" vs ");
                sb.append(data.playerNames.get(i));
            }
            sb.append("\n");
        }

        sb.append("LP: ").append(data.params.startLp);
        sb.append(" | 手牌: ").append(data.params.startHand);
        sb.append(" | 抽卡: ").append(data.params.drawCount).append("\n");

        if (data.isTag) sb.append("[双打模式] ");
        if (data.isSingleMode) sb.append("[残局模式] ");
        if (!data.isTag && !data.isSingleMode) sb.append("[普通模式] ");

        if (!data.decks.isEmpty()) {
            sb.append("\n主卡组: ");
            for (int i = 0; i < data.decks.size(); i++) {
                if (i > 0) sb.append(" / ");
                sb.append(data.decks.get(i).main.size()).append("张");
            }
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = sdf.format(new java.util.Date(replayFile.lastModified()));
        sb.append("\n录制时间: ").append(dateStr);

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

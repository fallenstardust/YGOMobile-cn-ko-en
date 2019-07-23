package cn.garymb.ygomobile.ui.home;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.SystemUtils;
import cn.garymb.ygomobile.utils.XmlUtils;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;

public class ServerListManager {
    private ServerListAdapter mAdapter;
    private Context mContext;
    private final File xmlFile;

    public ServerListManager(Context context, ServerListAdapter adapter) {
        mContext = context;
        mAdapter = adapter;
        xmlFile = new File(context.getFilesDir(), Constants.SERVER_FILE);
    }

    public Context getContext() {
        return mContext;
    }

    public void syncLoadData() {
        VUiKit.defer().when(() -> {
            ServerList assetList = readList(getContext().getAssets().open(ASSET_SERVER_LIST));
            ServerList fileList = xmlFile.exists() ? readList(new FileInputStream(xmlFile)) : null;
            if (fileList == null) {
                return assetList;
            }
            if (fileList.getVercode() < assetList.getVercode()) {
                xmlFile.delete();
                return assetList;
            }
            return fileList;
        }).done((list) -> {
            if (list != null) {
                mAdapter.set(list.getServerInfoList());
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    public static ServerList readList(InputStream in) {
        ServerList list = null;
        try {
            list = XmlUtils.get().getObject(ServerList.class, in);
        } catch (Exception e) {

        } finally {
            IOUtils.close(in);
        }
        return list;
    }

    public void saveItems() {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(xmlFile);
            XmlUtils.get().saveXml(new ServerList(SystemUtils.getVersion(getContext()), mAdapter.getItems()), outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(outputStream);
        }
        //修复 拖动 异常
//            notifyDataSetChanged();
    }

    public void addServer() {
        showEditDialog(-1);
    }

    public void delete(int position) {
        mAdapter.remove(position);
        mAdapter.notifyItemRemoved(position);
        saveItems();
    }

    public void showEditDialog(int position) {
        final boolean isAdd = position < 0;
        final DialogPlus dialog = new DialogPlus(getContext());
        dialog.setContentView(R.layout.dialog_server_edit);
        dialog.show();
        ServerInfoViewHolder editViewHolder = new ServerInfoViewHolder(dialog.getContentView());
        if (isAdd) {
            dialog.setTitle(R.string.action_add_server);
        } else {
            ServerInfo serverInfo = mAdapter.getItem(position);
            if (serverInfo != null) {
                editViewHolder.serverName.setText(serverInfo.getName());
                editViewHolder.serverIp.setText(serverInfo.getServerAddr());
                editViewHolder.userName.setText(serverInfo.getPlayerName());
                editViewHolder.serverPort.setText(String.valueOf(serverInfo.getPort()));
            }
            dialog.setTitle(R.string.server_info_edit);
        }
        dialog.setLeftButtonListener((dlg, v) -> {
            //保存
            String serverName = "" + editViewHolder.serverName.getText();
            ServerInfo info;
            if (!isAdd) {
                info = mAdapter.getItem(position);
            } else {
                info = new ServerInfo();
            }
            info.setName("" + serverName);
            info.setServerAddr("" + editViewHolder.serverIp.getText());
            info.setPlayerName("" + editViewHolder.userName.getText());
            if (TextUtils.isEmpty(info.getName())
                    || TextUtils.isEmpty(info.getServerAddr())
                    || TextUtils.isEmpty(editViewHolder.serverPort.getText())) {
                Toast.makeText(getContext(), R.string.server_is_exist, Toast.LENGTH_SHORT).show();
                return;
            }
            if (isAdd) {
                mAdapter.add(info);
                mAdapter.notifyDataSetChanged();
            } else {
                if (position >= 0) {
                    mAdapter.notifyItemChanged(position);
                }
            }
            info.setPort(Integer.valueOf("" + editViewHolder.serverPort.getText()));
//            info.setPassword("" + editViewHolder.userPassword.getText());
            saveItems();
            dialog.dismiss();
        });
    }

    private boolean mChanged = false;

    public void bind(RecyclerView recyclerView) {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (mChanged && actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    saveItems();
                    mAdapter.notifyDataSetChanged();
                }
                mChanged = false;
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int left = viewHolder.getAdapterPosition();
                int right = target.getAdapterPosition();
                if (left >= 0) {
                    mChanged = true;
                    mAdapter.notifyItemMoved(left, right);
                    Collections.swap(mAdapter.getItems(), left, right);
                    mAdapter.bindMenu((ServerInfoViewHolder) viewHolder, right);
                    mAdapter.bindMenu((ServerInfoViewHolder) target, left);
                    return true;
                }
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

            }
        });
        helper.attachToRecyclerView(recyclerView);
    }
}

package cn.garymb.ygomobile.ex_card;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;
import static cn.garymb.ygomobile.Constants.URL_YGO233_DATAVER;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE_ALT;
import static cn.garymb.ygomobile.utils.DownloadUtil.TYPE_DOWNLOAD_EXCEPTION;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.home.HomeFragment;
import cn.garymb.ygomobile.ui.home.ServerListManager;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.SystemUtils;
import cn.garymb.ygomobile.utils.UnzipUtils;
import cn.garymb.ygomobile.utils.XmlUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ExCardActivity extends BaseActivity {

    private View layoutView;

    public static String dataVer;
    private String mTitle;
    private Button btn_download;
    private List<ServerInfo> serverInfos;
    private ServerInfo mServerInfo;
    private File xmlFile;
    private int FailedCount;
    private static final int DOWNLOAD_ING = 0;
    public static final int DOWNLOAD_COMPLETE = 1;

    /**
     * 用于标志当前下载状态，用于防止用户多次重复点击“下载按钮”
     * Mark the download state, which can prevent user from clicking the download button
     * repeatedly.
     */
    enum DownloadState {
        DOWNLOAD_ING,
        DOWNLOAD_COMPLETE;
    }

    private DownloadState downloadState;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DownloadUtil.TYPE_DOWNLOAD_ING:
                    btn_download.setText(msg.arg1 + "%");
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_EXCEPTION:
                    ++FailedCount;
                    if (FailedCount <= 2) {
                        Toast.makeText(getActivity(), R.string.Ask_to_Change_Other_Way, Toast.LENGTH_SHORT).show();
                        downloadfromWeb(URL_YGO233_FILE_ALT);
                    }
                    YGOUtil.show("error" + msg.obj);
                    break;
                case UnzipUtils.ZIP_READY:
                    btn_download.setText(R.string.title_use_ex);
                    break;
                case UnzipUtils.ZIP_UNZIP_OK:
                    /* 如果未开启扩展卡，根本打不开本activity。因此此处的检查不是必须的 */
                  /*  if (!AppsSettings.get().isReadExpansions()) {
                        Intent startSetting = new Intent(getContext(), MainActivity.class);
                        startSetting.putExtra("flag", 4);
                        startActivity(startSetting);
                        Toast.makeText(getContext(), R.string.ypk_go_setting, Toast.LENGTH_LONG).show();
                        Log.i("webCrawler", "Ex-card setting is closed");
                    } else {*/
                    EventBus.getDefault().postSticky(new ExCardEvent(ExCardEvent.EventType.exCardPackageChange));
                    DataManager.get().load(true);
                    Toast.makeText(getContext(), R.string.ypk_installed, Toast.LENGTH_LONG).show();
                    Log.i("webCrawler", "Ex-card package is installed");

                    /* 将先行服务器信息添加到服务器列表中 */
                    String servername = "";
                    if (AppsSettings.get().getDataLanguage() == 0)
                        servername = "23333先行服务器";
                    if (AppsSettings.get().getDataLanguage() == 1)
                        servername = "YGOPRO 사전 게시 중국서버";
                    if (AppsSettings.get().getDataLanguage() == 2)
                        servername = "Mercury23333 OCG/TCG Pre-release";
                    AddServer(servername, "s1.ygo233.com", 23333, "Knight of Hanoi");
                    btn_download.setText(R.string.tip_redownload);
                    SharedPreferenceUtil.setExpansionDataVer(WebActivity.exCardVer);

                    break;
                case UnzipUtils.ZIP_UNZIP_EXCEPTION:
                    Toast.makeText(getContext(), getString(R.string.install_failed_bcos) + msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case HomeFragment.TYPE_GET_DATA_VER_OK:
                    WebActivity.exCardVer = msg.obj.toString();
                    String oldVer = SharedPreferenceUtil.getExpansionDataVer();
                    if (!TextUtils.isEmpty(WebActivity.exCardVer)) {
                        if (!WebActivity.exCardVer.equals(oldVer)) {
                            //btn_download展示默认视图
                        } else {
                            btn_download.setText(R.string.tip_redownload);
                        }
                    } else {
                        showExNew();
                    }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("webCrawler", "ExCardActivity onCreate");
        setContentView(R.layout.activity_ex_card);

        /* show the recyclerView */
        //get ex card data from intent
        List<ExCard> exCardList = this.getIntent()
                .getParcelableArrayListExtra("exCardList");
        List<ExCardLogItem> exCardLogItemList = this.getIntent()
                .getParcelableArrayListExtra("exCardLogList");
        ExCardListAdapter exCardListAdapter = new ExCardListAdapter(R.layout.item_ex_card, exCardList);
        ExCardLogAdapter exCardLogAdapter = new ExCardLogAdapter(this, exCardLogItemList);

        RecyclerView exCardListView = (RecyclerView) findViewById(R.id.list_ex_cards);
        ExpandableListView expandableListView = findViewById(R.id.expandableListView);
        exCardListView.setLayoutManager(new LinearLayoutManager(this));
        exCardListView.setAdapter(exCardListAdapter);
        expandableListView.setAdapter(exCardLogAdapter);
        expandableListView.setGroupIndicator(null);
        expandableListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {

            @Override
            public void onGroupExpand(int groupPosition) {
                Log.i("webCrawler",
                        exCardLogItemList.get(groupPosition) + " List Expanded.");
            }
        });

        expandableListView.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {

            @Override
            public void onGroupCollapse(int groupPosition) {
                Log.i("webCrawler", exCardLogItemList.get(groupPosition) + " List Collapsed.");
            }
        });

        expandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
                ExCardLogItem exCardLogItem = exCardLogItemList.get(groupPosition);
                String log = exCardLogItem.getLogs().get(childPosition);
                Log.i("webCrawler", "log is:" + log);

                return false;
            }
        });


        final Toolbar toolbar = $(R.id.toolbar);

        setSupportActionBar(toolbar);

        enableBackHome();

        serverInfos = new ArrayList<>();
        xmlFile = new

                File(this.getFilesDir(), Constants.SERVER_FILE);//读取文件路径下的server_list.xml

        initButton();

    }


    public void initButton() {
        showExNew();
        //检测是否下载过
        btn_download = $(R.id.web_btn_download_prerelease);
        btn_download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (downloadState != DownloadState.DOWNLOAD_ING) {
                    Log.i("webCrawler", "start downloading");
                    downloadState = DownloadState.DOWNLOAD_ING;
                    downloadfromWeb(URL_YGO233_FILE);
                }
            }
        });
    }

    /**
     * 从资源文件serverlist.xml（或本地文件server_list.xml)解析服务器列表，并将新添加的服务器信息（name，addr，port）合并到服务器列表中。
     *
     * @param name
     * @param Addr
     * @param port
     * @param playerName
     */
    public void AddServer(String name, String Addr, int port, String playerName) {
        mServerInfo = new ServerInfo();
        mServerInfo.setName(name);
        mServerInfo.setServerAddr(Addr);
        mServerInfo.setPort(port);
        mServerInfo.setPlayerName(playerName);
        VUiKit.defer().when(() -> {
            /* 读取本地文件server_list.xml和资源文件（assets）下的serverlist.xml，返回其中版本最新的 */
            ServerList assetList = ServerListManager.readList(this.getAssets().open(ASSET_SERVER_LIST));//读取serverlist.xml文件
            ServerList fileList = xmlFile.exists() ? ServerListManager.readList(new FileInputStream(xmlFile)) : null;
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
                serverInfos.clear();
                serverInfos.addAll(list.getServerInfoList());
                boolean hasServer = false;
                for (int i = 0; i < list.getServerInfoList().size(); i++) {
                    if (mServerInfo.getPort() != serverInfos.get(i).getPort() && mServerInfo.getServerAddr() != serverInfos.get(i).getServerAddr()) {
                        continue;
                    } else {
                        hasServer = true;
                        break;
                    }

                }
                if (!hasServer && !serverInfos.contains(mServerInfo)) {
                    serverInfos.add(mServerInfo);
                }
                saveItems();
            }
        });
    }

    /**
     * 将最新的服务器列表存储到本地文件server_list.xml中
     */
    public void saveItems() {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(xmlFile);
            XmlUtils.get().saveXml(new ServerList(SystemUtils.getVersion(getContext()), serverInfos), outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(outputStream);
        }
    }

    private void downloadfromWeb(String fileUrl) {
        File file = new File(AppsSettings.get().getResourcePath() + "-preRlease.zip");
        if (file.exists()) {
            FileUtils.deleteFile(file);
        }
        DownloadUtil.get().download(fileUrl, file.getParent(), file.getName(), new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                downloadState = DownloadState.DOWNLOAD_COMPLETE;
                Message message = new Message();
                message.what = UnzipUtils.ZIP_READY;
                try {
                    File ydks = new File(AppsSettings.get().getDeckDir());
                    File[] subYdks = ydks.listFiles();
                    for (File files : subYdks) {
                        if (files.getName().contains("-") && files.getName().contains(" new cards"))
                            files.delete();
                    }
                    UnzipUtils.upZipFile(file, AppsSettings.get().getResourcePath());
                } catch (Exception e) {
                    message.what = UnzipUtils.ZIP_UNZIP_EXCEPTION;
                } finally {
                    message.what = UnzipUtils.ZIP_UNZIP_OK;
                }
                handler.sendMessage(message);
            }


            @Override
            public void onDownloading(int progress) {
                Message message = new Message();
                message.what = DownloadUtil.TYPE_DOWNLOAD_ING;
                message.arg1 = progress;
                handler.sendMessage(message);
            }

            @Override
            public void onDownloadFailed(Exception e) {
                //下载失败后删除下载的文件
                FileUtils.deleteFile(file);
                Message message = new Message();
                message.what = TYPE_DOWNLOAD_EXCEPTION;
                message.obj = e.toString();
                handler.sendMessage(message);
            }
        });

    }

    public void showExNew() {
        if (AppsSettings.get().isReadExpansions()) {
            OkhttpUtil.get(URL_YGO233_DATAVER, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.i(BuildConfig.VERSION_NAME, "error" + e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String json = response.body().string();
                    Message message = new Message();
                    message.what = HomeFragment.TYPE_GET_DATA_VER_OK;
                    message.obj = json;
                    handler.sendMessage(message);
                }
            });
        }
    }
}

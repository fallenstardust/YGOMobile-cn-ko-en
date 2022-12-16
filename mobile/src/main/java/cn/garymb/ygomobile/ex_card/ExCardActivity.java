package cn.garymb.ygomobile.ex_card;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE_ALT;
import static cn.garymb.ygomobile.utils.DownloadUtil.TYPE_DOWNLOAD_EXCEPTION;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.ui.home.ServerListManager;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.SystemUtils;
import cn.garymb.ygomobile.utils.UnzipUtils;
import cn.garymb.ygomobile.utils.XmlUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;

public class ExCardActivity extends BaseActivity {

    private View layoutView;

    public static String dataVer;
    private String mTitle;
    private Button btn_download;
    private List<ServerInfo> serverInfos;
    private ServerInfo mServerInfo;
    private File xmlFile;
    private int FailedCount;

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
                    if (!AppsSettings.get().isReadExpansions()) {
                        Intent startSetting = new Intent(getContext(), MainActivity.class);
                        startSetting.putExtra("flag", 4);
                        startActivity(startSetting);
                        Toast.makeText(getContext(), R.string.ypk_go_setting, Toast.LENGTH_LONG).show();
                    } else {
                        DataManager.get().load(true);
                        Toast.makeText(getContext(), R.string.ypk_installed, Toast.LENGTH_LONG).show();
                    }
                    String servername = "";
                    if (getPackageName().equals(BuildConfig.APPLICATION_ID))
                        servername = "23333先行服务器";
                    if (getPackageName().equals((BuildConfig.APPLICATION_ID) + ".KO"))
                        servername = "YGOPRO 사전 게시 중국서버";
                    if (getPackageName().equals((BuildConfig.APPLICATION_ID) + ".EN"))
                        servername = "Mercury23333 OCG/TCG Pre-release";
                    AddServer(servername, "s1.ygo233.com", 23333, "Knight of Hanoi");
                    btn_download.setVisibility(View.GONE);
                    SharedPreferenceUtil.setExpansionDataVer(WebActivity.dataVer);
                    break;
                case UnzipUtils.ZIP_UNZIP_EXCEPTION:
                    Toast.makeText(getContext(), getString(R.string.install_failed_bcos) + msg.obj, Toast.LENGTH_SHORT).show();
                    break;

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
        List<ExCard> exCards = this.getIntent()
                .getParcelableArrayListExtra("exCards");
        ExCardListAdapter exCardListAdapter = new ExCardListAdapter(R.layout.item_ex_card, exCards);
        RecyclerView exCardListView = (RecyclerView) findViewById(R.id.list_ex_cards);
        exCardListView.setLayoutManager(new LinearLayoutManager(this));
        exCardListView.setAdapter(exCardListAdapter);


        final Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        enableBackHome();
        serverInfos = new ArrayList<>();
        xmlFile = new File(this.getFilesDir(), Constants.SERVER_FILE);
        initButton();
    }


    public void initButton() {
        btn_download = $(R.id.web_btn_download_prerelease);
        btn_download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadfromWeb(URL_YGO233_FILE);
            }
        });
    }

    public void AddServer(String name, String Addr, int port, String playerName) {
        mServerInfo = new ServerInfo();
        mServerInfo.setName(name);
        mServerInfo.setServerAddr(Addr);
        mServerInfo.setPort(port);
        mServerInfo.setPlayerName(playerName);
        VUiKit.defer().when(() -> {
            ServerList assetList = ServerListManager.readList(this.getAssets().open(ASSET_SERVER_LIST));
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

}

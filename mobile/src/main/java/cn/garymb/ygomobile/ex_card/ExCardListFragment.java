package cn.garymb.ygomobile.ex_card;

import static cn.garymb.ygomobile.utils.DownloadUtil.TYPE_DOWNLOAD_EXCEPTION;
import static cn.garymb.ygomobile.utils.ServerUtil.AddServer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.events.ExCardEvent;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.ServerUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;


public class ExCardListFragment extends Fragment {
    private static final String TAG = String.valueOf(ExCardListFragment.class);
    /** 下载已完成但旧先行卡包替换失败，与下载失败分开处理，不重跑下载 */
    private static final int TYPE_INSTALL_FAILED = 4;
    public static DownloadState downloadState;
    private Context context;
    private View layoutView;
    private ExCardListAdapter mExCardListAdapter;
    private RecyclerView mExCardListView;
    private SwipeRefreshLayout srlRefresh;
    private TextView textDownload;
    private int FailedCount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        layoutView = inflater.inflate(R.layout.fragment_ex_card_list, container, false);

        this.context = getContext();

        initView(layoutView);
        if (!EventBus.getDefault().isRegistered(this)) {//加上判断
            EventBus.getDefault().register(this);
        }
        return layoutView;
    }

    @Override
    public void onStop() {
        super.onStop();
        LogUtil.i(TAG, "excard fragment on stop");
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        
        handler.removeCallbacksAndMessages(null);
    }

    public void initView(View layoutView) {
        mExCardListView = layoutView.findViewById(R.id.list_ex_card);
        mExCardListAdapter = new ExCardListAdapter(R.layout.item_ex_card);
        //RecyclerView: No layout manager attached; skipping layout
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        mExCardListView.setLayoutManager(linearLayoutManager);
        mExCardListView.setAdapter(mExCardListAdapter);
        // 列表加载态交给下拉刷新：首次拉取失败只收起转圈，不再弹无法关闭的模态弹窗，
        // 用户可下拉重试或直接离开/返回本页
        srlRefresh = layoutView.findViewById(R.id.srl_ex_card);
        srlRefresh.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        srlRefresh.setOnRefreshListener(mExCardListAdapter::loadData);
        mExCardListAdapter.setSwipeRefreshLayout(srlRefresh);
        mExCardListAdapter.loadData();
        textDownload = layoutView.findViewById(R.id.text_download_precard);

        LinearLayout clickLayout = layoutView.findViewById(R.id.layout_download_precard);
        clickLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownload(false);
            }
        });
        /* 长按：能增量更新时让用户自己选，"完整下载"就是原来的方式；不能增量时按普通点按处理 */
        clickLayout.setOnLongClickListener(v -> {
            if (downloadState == DownloadState.DOWNLOAD_ING || incrementalBase() == null) {
                return false;
            }
            DialogPlus dialog = new DialogPlus(context);
            dialog.setTitleText(getString(R.string.superpre_inc_choose_title));
            dialog.setMessage(R.string.superpre_inc_choose_message);
            dialog.setLeftButtonText(R.string.superpre_inc_choose_incremental);
            dialog.setLeftButtonListener((dlg, which) -> {
                dlg.dismiss();
                startDownload(false);
            });
            dialog.setRightButtonText(R.string.superpre_inc_choose_full);
            dialog.setRightButtonListener((dlg, which) -> {
                dlg.dismiss();
                startDownload(true);
            });
            dialog.show();
            return true;
        });

        changeDownloadText();
    }

    /** @param full 用户在长按菜单里选了完整下载：不走增量 */
    private void startDownload(boolean full) {
        LogUtil.i(TAG, "start download" + (full ? "（用户选择完整下载）" : ""));
        if (downloadState != DownloadState.DOWNLOAD_ING) {
            downloadState = DownloadState.DOWNLOAD_ING;
            downloadfromWeb(ServerUtil.downloadUrl(), full);
        }
    }

    /**
     * 能增量更新时返回已装的旧包，否则 null（走完整下载）：开关打开、中文先行卡、需要更新、已装旧包。
     * 非中文、重新下载（已是最新）、没有旧包都照旧下载完整包。
     */
    private File incrementalBase() {
        File installed = new File(AppsSettings.get().getExpansionsPath(), Constants.officialExCardPackageName + Constants.YPK_FILE_EX);
        boolean usable = BuildConfig.SUPERPRE_INCREMENTAL
                && AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Chinese.code
                && ServerUtil.exCardState == ServerUtil.ExCardState.NEED_UPDATE
                && installed.isFile();
        return usable ? installed : null;
    }

    /**
     * 根据先行卡包状态改变按钮样式
     */
    public void changeDownloadText() {
        if (ServerUtil.exCardState == ServerUtil.ExCardState.UPDATED) {
            //btn_download展示默认视图
            textDownload.setText(R.string.tip_redownload);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.NEED_UPDATE) {
            textDownload.setText(R.string.Download);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.ERROR) {
            /* 查询不到版本号时，提示toast */
            textDownload.setText(R.string.Download);
            YGOUtil.showTextToast("error" + getString(R.string.ex_card_check_toast_message_iii));
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.UNCHECKED) {
            //do nothing
            //状态UNCHECKED仅在app启动后调用哦你Create()之前短暂存在，所以该情况进行处理
            //the UNCHECKED state only exists temporarily before the check action, so we need not handle it.
        }
    }

    /**
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageReceived(ExCardEvent event) {
        if (event.getType() == ExCardEvent.EventType.exCardPackageChange) {
            changeDownloadText();
        }
    }

    private void downloadfromWeb(String fileUrl) {
        downloadfromWeb(fileUrl, false);
    }

    /** @param full 用户在长按菜单里选了完整下载：不走增量 */
    private void downloadfromWeb(String fileUrl, boolean full) {
        textDownload.setText("0%");//点击下载后，距离onDownloading触发要等几秒，这一延迟会造成软件响应慢的错觉，因此在下载函数开始就设置文本
        String fileName = Constants.officialExCardPackageName + Constants.YPK_FILE_EX;
        /* 新先行卡先下到应用现有 Cache 里，下载成功后才替换 expansions 下的旧包，以免下载失败时旧先行卡一并没了 */
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = context.getCacheDir();//外部存储不可用时退回内部缓存
        }
        File tempFile = new File(cacheDir, fileName);

        /* 能增量更新就先走增量：只下有变化的文件再与旧包合成新包；否则照旧下载完整包 */
        File installed = full ? null : incrementalBase();
        if (installed != null) {
            downloadIncremental(installed, cacheDir, tempFile);
            return;
        }
        downloadFull(fileUrl, cacheDir, fileName, tempFile);
    }

    /**
     * 增量更新先行卡包，按钮下的文字跟着显示当前阶段。成功后与完整下载一样落位；增量走不通（服务器或
     * 数据问题）问用户要不要改下完整包；网络问题提示已保留多少，按钮恢复为“下载”，网络好了再点就接着下。
     */
    private void downloadIncremental(File installed, File cacheDir, File tempFile) {
        ExCardIncrementalUpdate.start(installed, tempFile, cacheDir, ServerUtil.serverExCardVersion, new ExCardIncrementalUpdate.Listener() {
            @Override
            public void onStage(Stage stage) {
                int text = stage == Stage.CHECKING ? R.string.superpre_inc_checking
                        : stage == Stage.COMPARING ? R.string.superpre_inc_comparing
                        : stage == Stage.REQUESTING ? R.string.superpre_inc_requesting : R.string.superpre_inc_merging;
                onUi(() -> textDownload.setText(text));
            }

            @Override
            public void onDownloadStart(int files, long bytes) {
                onUi(() -> YGOUtil.showTextToast(getString(R.string.superpre_inc_start, files,
                        Formatter.formatShortFileSize(context, bytes))));
            }

            @Override
            public void onProgress(int percent) {
                onUi(() -> textDownload.setText(getString(R.string.superpre_inc_downloading, percent)));
            }

            @Override
            public void onRetry(boolean serverSide) {
                onUi(() -> textDownload.setText(serverSide ? R.string.superpre_inc_retry_server : R.string.superpre_inc_retry_network));
            }

            @Override
            public void onSuccess(File ypk) {
                Message message = new Message();
                message.what = installExCardPackage(ypk) ? DownloadUtil.TYPE_DOWNLOAD_OK : TYPE_INSTALL_FAILED;
                handler.sendMessage(message);
            }

            @Override
            public void onFallback(String reason) {
                //改下完整包前问用户；页面已经离开就不问，下次再点下载
                downloadState = DownloadState.NO_DOWNLOAD;
                onUi(() -> {
                    textDownload.setText(R.string.Download);
                    DialogPlus dialog = new DialogPlus(context);
                    dialog.setTitle(R.string.tip);
                    dialog.setMessage(R.string.superpre_inc_fallback);
                    dialog.setLeftButtonText(R.string.superpre_inc_choose_full);
                    dialog.setLeftButtonListener((dlg, which) -> {
                        dlg.dismiss();
                        startDownload(true);
                    });
                    dialog.setRightButtonText(R.string.Cancel);
                    dialog.setRightButtonListener((dlg, which) -> dlg.dismiss());
                    dialog.show();
                });
            }

            @Override
            public void onNetworkFailure(Exception e, long keptBytes) {
                downloadState = DownloadState.NO_DOWNLOAD;
                onUi(() -> {
                    textDownload.setText(R.string.Download);
                    showEndDialog((keptBytes > 0
                            ? getString(R.string.superpre_inc_network_failed, Formatter.formatShortFileSize(context, keptBytes))
                            : getString(R.string.superpre_inc_network_failed_nothing_kept))
                            + "\n\n" + getString(R.string.superpre_inc_long_press_tip));
                });
            }

            @Override
            public void onTryLater(String reason) {
                downloadState = DownloadState.NO_DOWNLOAD;
                onUi(() -> {
                    textDownload.setText(R.string.Download);
                    showEndDialog(getString(R.string.superpre_inc_try_later)
                            + "\n\n" + getString(R.string.superpre_inc_long_press_tip));
                });
            }
        });
    }

    /** 增量更新结束时的提示用对话框，点确定才关，不像 Toast 一闪而过就错过了 */
    private void showEndDialog(String message) {
        DialogPlus dialog = new DialogPlus(context);
        dialog.setTitle(R.string.tip);
        dialog.setMessage(message);
        dialog.setLeftButtonText(R.string.OK);
        dialog.setLeftButtonListener((dlg, which) -> dlg.dismiss());
        dialog.show();
    }

    /** 增量更新的回调在后台线程，界面改动交给主线程；页面已离开就不再更新 */
    private void onUi(Runnable action) {
        handler.post(() -> {
            if (isAdded() && context != null) {
                action.run();
            }
        });
    }

    private void downloadFull(String fileUrl, File cacheDir, String fileName, File tempFile) {
        DownloadUtil.get().downloadSuperpre(fileUrl, cacheDir.getAbsolutePath(), fileName, new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                Message message = new Message();
                message.what = installExCardPackage(tempFile) ? DownloadUtil.TYPE_DOWNLOAD_OK : TYPE_INSTALL_FAILED;
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
                //下载失败只删临时档案，旧先行卡从未被动过
                FileUtils.deleteFile(tempFile);
                Message message = new Message();
                message.what = TYPE_DOWNLOAD_EXCEPTION;
                handler.sendMessage(message);
            }
        });

    }

    /**
     * 下载成功后才替换：把临时档案挪到 expansions 的正式包名（同存储卷改名即覆盖旧包，
     * 改名不可用再退回复制），落位成功后才清理历史命名的残留先行卡包，避免具备相同id
     * 的数据库之间引起冲突。任一步失败都保留旧包与临时档案，仅记日志。
     *
     * @param tempFile 下载完成的临时档案
     * @return 是否完成落位替换
     */
    private boolean installExCardPackage(File tempFile) {
        if (!tempFile.isFile() || tempFile.length() <= 0) {
            LogUtil.e(TAG, "先行卡临时档案无效: " + tempFile.getAbsolutePath());
            return false;
        }
        File dir = AppsSettings.get().getExpansionsPath();
        File target = new File(dir, tempFile.getName());
        if (!tempFile.renameTo(target) && !FileUtils.copyFile(tempFile, target)) {
            LogUtil.e(TAG, "先行卡包替换失败，保留旧包: " + target.getAbsolutePath());
            return false;
        }
        FileUtils.deleteFile(tempFile);//改名落位时本已不存在，复制兜底后把临时档案清掉
        String fileName = Constants.officialExCardPackageName;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File oldfile : files) {
                if (oldfile.exists() && !oldfile.equals(target) && (oldfile.getName().contains(fileName) || oldfile.getName().contains(Constants.cacheExCardPackageName) || oldfile.getName().contains(Constants.mercuryExCardPackageName))) {
                    FileUtils.deleteFile(oldfile);
                }
            }
        }
        return true;
    }

    /**
     * 用于标志当前下载状态，用于防止用户多次重复点击“下载按钮”
     * Mark the download state, which can prevent user from clicking the download button
     * repeatedly.
     */
    enum DownloadState {
        DOWNLOAD_ING,
        NO_DOWNLOAD
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            
            if (!isAdded() || context == null) {
                return;
            }
            
            switch (msg.what) {
                case DownloadUtil.TYPE_DOWNLOAD_ING:
                    textDownload.setText(msg.arg1 + "%");
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_EXCEPTION:
                    downloadState = DownloadState.NO_DOWNLOAD;
                    ++FailedCount;
                    if (FailedCount <= 2) {
                        YGOUtil.showTextToast(getString(R.string.Ask_to_Change_Other_Way));
                        downloadfromWeb(ServerUtil.downloadUrl());
                    }
                    YGOUtil.showTextToast("error:" + getString(R.string.Download_Precard_Failed));
                    break;
                case TYPE_INSTALL_FAILED:
                    downloadState = DownloadState.NO_DOWNLOAD;
                    YGOUtil.showTextToast(getString(R.string.Download_Precard_Failed));
                    break;
//                case UnzipUtils.ZIP_READY:
//                    textDownload.setText(R.string.title_use_ex);
//                    break;
                case DownloadUtil.TYPE_DOWNLOAD_OK:
                    downloadState = DownloadState.NO_DOWNLOAD;
                    AddServer(context, context.getString(R.string.Pre_Server_Name), context.getString(R.string.tip_superpre_desc),
                            Constants.URL_Mycard_Super_Pre_Server, Constants.PORT_Mycard_Super_Pre_Server, "Knight of Hanoi");
                    //changeDownloadButton();在下载完成后，通过EventBus通知下载完成（加入用户点击下载后临时切出本fragment，又在下载完成后切回，通过eventbus能保证按钮样式正确更新
                    /* 注意，要先更新版本号 */
                    SharedPreferenceUtil.setExpansionDataVer(ServerUtil.serverExCardVersion);
                    ServerUtil.exCardState = ServerUtil.ExCardState.UPDATED;
                    EventBus.getDefault().postSticky(new ExCardEvent(ExCardEvent.EventType.exCardPackageChange));//安装后，通知UI做更新
                    DataManager.get().load(true);


                    YGOUtil.showTextToast(getString(R.string.ypk_installed));
                    LogUtil.i("webCrawler", "Ex-card package is installed");

                    /* 如果未开启先行卡设置，则跳转到设置页面 */
                    if (!AppsSettings.get().isReadExpansions()) {//解压完毕，但此时
                        LogUtil.i("webCrawler", "Ex-card setting is not opened");
                        Intent startSetting = new Intent(context, MainActivity.class);
                        startSetting.putExtra("flag", 4);
                        startActivity(startSetting);
                        YGOUtil.showTextToast(getString(R.string.ypk_go_setting));
                    }

                    break;

            }
        }
    };


}
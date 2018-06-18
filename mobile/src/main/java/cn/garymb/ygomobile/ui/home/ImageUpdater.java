package cn.garymb.ygomobile.ui.home;

import android.content.DialogInterface;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/**
 * Created by keyongyu on 2017/1/31.
 */

public class ImageUpdater implements DialogInterface.OnCancelListener {
    private BaseActivity mContext;
    private CardLoader mCardLoader;
    private final static int SubThreads = 4;
    private int mDownloading = 0;
    private final List<Item> mCardStatus = new ArrayList<>();
    private ExecutorService mExecutorService = Executors.newFixedThreadPool(SubThreads);
    private DialogPlus mDialog;
    private int mIndex;
    private int mCount;
    private int mCompleted;
    private boolean isRun = false;
    private boolean mStop = false;
    private ZipFile mZipFile;
    private int mError = 0;

    File mPicsPath;
    File mPicsExPath;

    public ImageUpdater(BaseActivity context) {
        mContext = context;
        mCardLoader = new CardLoader(context);
        mPicsPath = new File(AppsSettings.get().getResourcePath(), Constants.CORE_IMAGE_PATH);
        mPicsExPath = new File(AppsSettings.get().getResourcePath(), Constants.CORE_EXPANSIONS_IMAGE_PATH);
    }

    public boolean isRunning() {
        if (isRun) return true;
        synchronized (mCardStatus) {
            if (mDownloading > 0) {
                return true;
            }
        }
        return false;
    }

    public void close() {
        mExecutorService.shutdown();
    }

    public void start() {
        if (isRunning()) return;
        isRun = true;
        mCompleted = 0;
        mIndex = 0;
        mDownloading = 0;
        mStop = false;
        mError = 0;
        if (mDialog != null) {
            if (!mDialog.isShowing()) {
                mDialog.show();
            }
        } else {
            mDialog = DialogPlus.show(mContext, mContext.getString(R.string.reset_game_res), mContext.getString(R.string.download_image_progress, mCompleted, mCount), true);
            mDialog.setOnCloseLinster(this);
            mDialog.show();
        }
        VUiKit.defer().when(() -> {
            synchronized (mCardStatus) {
                if (mCardStatus.size() == 0) {
                    loadCardsLocked();
                }
            }
        }).done((res) -> {
            File zip = new File(AppsSettings.get().getResourcePath(), Constants.CORE_PICS_ZIP);
            if (mZipFile == null) {
                if (zip.exists()) {
                    try {
                        mZipFile = new ZipFile(zip);
                    } catch (IOException e) {
                    }
                }
            }
//        Log.i("kk", "download " + mCompleted + "/" + mCount);
            for (int i = 0; i < SubThreads; i++) {
                Item item = nextCard();
                if (item != null) {
                    if (!submit(item)) {
                        i--;
                    }
                }
            }
            synchronized (mCardStatus) {
                if (mDownloading <= 0) {
                    onEnd();
                }
            }
        });
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        synchronized (mCardStatus) {
            mStop = true;
        }
        dialog.dismiss();
    }

    private boolean submit(Item item) {
        if (item != null) {
//            Log.i("kk", "submit " + id);
            if (!mExecutorService.isShutdown()) {
                synchronized (mCardStatus) {
                    mDownloading++;
                }
                mExecutorService.submit(new DownloadTask(item));
                return true;
            }
        }
        return false;
    }

    private long lasttime = 0;
    private static final long MIN_TIME = 100;

    private class DownloadTask implements Runnable {
        Item item;
        File tmpFile;

        private DownloadTask(Item item) {
            this.item = item;
            this.tmpFile = new File(item.file + ".tmp");
        }

        private boolean existImage() {
            String name;
            if (item.isField) {
                name = Constants.CORE_IMAGE_FIELD_PATH + "/" + item.code;
            } else {
                name = "" + item.code;
            }
            for (String ex : Constants.IMAGE_EX) {
                File file = new File(mPicsPath, name + ex);
                if (file.exists()) {
                    return true;
                }
                File fileex = new File(mPicsExPath, name + ex);
                if (fileex.exists()) {
                    return true;
                }
            }
            if (mZipFile != null) {
                ZipEntry entry = null;
                for (String ex : Constants.IMAGE_EX) {
                    entry = mZipFile.getEntry(Constants.CORE_IMAGE_PATH + "/" + name + ex);
                    if (entry != null) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void run() {
            boolean needNext;
            synchronized (mCardStatus) {
                needNext = !mStop;
            }
            if (needNext) {
                if (existImage()) {

                } else {
                    if (download(item.url, tmpFile)) {
                        File file = new File(item.file);
                        if (tmpFile.exists() && !file.exists()) {
                            tmpFile.renameTo(file);
                            Log.d("kk", "download ok:" + item.url + " ->" + item.file);
                        } else {
                            Log.e("kk", "download fail:" + item.url + " ->" + item.file);
                        }
                    } else {
                        synchronized (mCardStatus) {
                            mError++;
                        }
                        Log.e("kk", "download error:" + item.url + " ->" + item.file);
                    }
                }
                synchronized (mCardStatus) {
                    mDownloading--;
                    mCompleted++;
                    if (mDialog != null) {
                        VUiKit.post(() -> {
//                            Log.d("kk", mCompleted+"/"+mCount);
                            if (mCompleted != mCount) {
                                if (System.currentTimeMillis() - lasttime > MIN_TIME) {
                                    lasttime = System.currentTimeMillis();
                                    mDialog.setMessage(mContext.getString(R.string.download_image_progress, mCompleted, mCount));
                                }
                            } else {
                                mDialog.setMessage(mContext.getString(R.string.download_image_progress, mCompleted, mCount));
                            }
                        });
                    }
                }
                synchronized (mCardStatus) {
                    needNext = !mStop;
                }
            }
            if (needNext) {
                Item item = nextCard();
                if (item != null) {
                    submit(item);
                } else {
                    //当前没任务
                    synchronized (mCardStatus) {
                        if (mDownloading <= 0) {
                            onEnd();
                        }
                    }
                }
            } else {
                synchronized (mCardStatus) {
                    if (mDownloading <= 0) {
                        onEnd();
                    }
                }
            }
        }
    }

    private boolean download(String url, File file) {
        FileOutputStream outputStream = null;
        InputStream inputStream = null;
        HttpURLConnection mConnection = null;
        boolean ok = false;
        try {
            if (file.exists()) {
                file.delete();
            } else {
                File dir = file.getParentFile();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }
            file.createNewFile();
            mConnection = (HttpURLConnection) new URL(url).openConnection();
            mConnection.setConnectTimeout(30 * 1000);
            mConnection.setReadTimeout(15 * 1000);
            mConnection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36 Edge/15.15063");
            mConnection.connect();
            if (mConnection.getResponseCode() == 200) {
                inputStream = mConnection.getInputStream();
                outputStream = new FileOutputStream(file);
                byte[] tmp = new byte[8192];
                int len;
                while ((len = inputStream.read(tmp)) != -1) {
                    outputStream.write(tmp, 0, len);
                }
                outputStream.flush();
                ok = true;
            } else {
                Log.w("kk", "download:" + mConnection.getResponseCode() + ":url=" + url);
            }
        } catch (IOException e) {
            Log.w("kk", "download:" + url, e);
            if (file.exists()) {
                file.delete();
            }
        } finally {
            IOUtils.close(inputStream);
            IOUtils.close(outputStream);
            if (mConnection != null) {
                mConnection.disconnect();
            }
        }
        return ok;
    }

    private Item nextCard() {
        synchronized (mCardStatus) {
//            Log.i("kk", "submit " + mIndex);
            if (mIndex >= mCount) {
                return null;
            }
            mIndex++;
            return mCardStatus.get(mIndex);
        }
    }

    private void onEnd() {
        synchronized (mCardStatus) {
            mCardStatus.clear();
        }
        if (mDialog != null) {
            mDialog.dismiss();
        }
        isRun = false;
        if (mZipFile != null) {
            try {
                mZipFile.close();
            } catch (IOException e) {
            }
            mZipFile = null;
        }
        VUiKit.post(() -> {
            if (mError == 0) {
                mContext.showToast(R.string.downloading_images_ok, Toast.LENGTH_SHORT);
            } else {
                mContext.showToast(mContext.getString(R.string.download_image_error, mError), Toast.LENGTH_SHORT);
            }
        });
    }

    private void loadCardsLocked() {
        if (!mCardLoader.isOpen()) {
            mCardLoader.openDb();
        }
        SparseArray<Card> cards = mCardLoader.readAllCardCodes();
        mCardStatus.clear();
        mPicsPath = new File(AppsSettings.get().getResourcePath(), Constants.CORE_IMAGE_PATH);
        File picsPath = mPicsPath;
        File fieldPath = new File(mPicsPath, Constants.CORE_IMAGE_FIELD_PATH);
        IOUtils.createNoMedia(picsPath.getAbsolutePath());
        IOUtils.createNoMedia(fieldPath.getAbsolutePath());
        int count = cards.size();
        for (int i = 0; i < count; i++) {
            int code = cards.keyAt(i);
            Card card = cards.valueAt(i);
            if (Card.isType(card.Type, CardType.Field)) {
                String png = new File(fieldPath, code + Constants.IMAGE_FIELD_URL_EX).getAbsolutePath();
                String pngUrl = String.format(Constants.IMAGE_FIELD_URL, code + "");
                mCardStatus.add(new Item(pngUrl, png, code, true));
            }
            String jpg = new File(picsPath, code + Constants.IMAGE_URL_EX).getAbsolutePath();
            String jpgUrl = String.format(Constants.IMAGE_URL, code + "");
            mCardStatus.add(new Item(jpgUrl, jpg, code, false));
        }
        mCount = mCardStatus.size();
    }

    private static class Item {
        String url;
        String file;
        long code;
        boolean isField;

        public Item(String url, String file, long code, boolean isField) {
            this.url = url;
            this.file = file;
            this.code = code;
            this.isField = isField;
        }
    }
}

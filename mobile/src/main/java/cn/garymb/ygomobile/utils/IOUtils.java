package cn.garymb.ygomobile.utils;


import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.Constants;

public class IOUtils {
    private static final String TAG = "ioUtils";

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void closeZip(ZipFile closeable) {
        if (closeable == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    delete(f);
                }
            }
            file.delete();
        }
    }

    public static boolean rename(String src, String to) {
        return new File(src).renameTo(new File(to));
    }

    public static boolean isDirectory(Context context, String assets) {
        String[] files = new String[0];
        try {
            files = context.getAssets().list(assets);
        } catch (IOException e) {

        }
        return files != null && files.length > 0;
    }

    public static String tirmName(String name, String ex) {
        if (name.toLowerCase(Locale.US).endsWith(ex)) {
            int i = name.lastIndexOf(".");
            if (i >= 0) {
                return name.substring(0, i);
            }
        }
        return name;
    }

    public static String join(String path1, String path2) {
        if (TextUtils.isEmpty(path1)) {
            return path2;
        }
        if (TextUtils.isEmpty(path2)) {
            return path1;
        }
        if (!path1.endsWith("/")) {
            path1 += "/";
        }
        if (path2.startsWith("/")) {
            path2 = path2.substring(1);
        }
        return path1 + path2;
    }

    public static String getName(String path) {
        return new File(path).getName();
    }

    /**
     * 资源拷贝进度回调：{@code done}/{@code total} 为当前一段工作已完成的条目数或字节数。
     * 一个阶段可能分多段上报（如先读资源算校验和、再读目标、最后重写），各段都从 0 跑到
     * total，由调用方自己保证单调（见 {@code ResCheckTask#subStep}）；total&lt;=0 表示无法预估。
     */
    public interface CopyProgress {
        void onProgress(long done, long total);
    }

    /**
     * 将文件从工程的assets文件夹拷贝到目录中
     */
    public static int copyFilesFromAssets(Context context, String assets, String toPath, boolean update) throws IOException {
        return copyFilesFromAssets(context, assets, toPath, update, null);
    }

    /**
     * 同上，额外上报进度：先预统计整棵资源子树的条目数作为 total（此类目录都是几十到
     * 几百量级，计数开销可忽略），每处理完一个条目（含跳过的已存在项）上报一次。
     */
    public static int copyFilesFromAssets(Context context, String assets, String toPath, boolean update,
            CopyProgress progress) throws IOException {
        int[] counter = progress == null ? null : new int[]{0, countAssetEntries(context, assets)};
        return copyFilesFromAssets(context, assets, toPath, update, progress, counter);
    }

    /** 资源子树的条目总数（目录递归，单文件计 1），供进度条算百分比。 */
    private static int countAssetEntries(Context context, String path) {
        String[] files;
        try {
            files = context.getAssets().list(path);
        } catch (IOException e) {
            return 0;
        }
        if (files == null) {
            return 0;
        }
        if (files.length == 0) {
            return 1;     // 指到单个资源文件
        }
        int n = 0;
        for (String f : files) {
            String p = join(path, f);
            n += isDirectory(context, p) ? countAssetEntries(context, p) : 1;
        }
        return n;
    }

    private static int copyFilesFromAssets(Context context, String assets, String toPath, boolean update,
            CopyProgress progress, int[] counter) throws IOException {
        AssetManager am = context.getAssets();
        String[] files = am.list(assets);
        if (files == null) {
            return 0;
        }
        if (files.length == 0) {
            //is file
            String file = getName(assets);
            File tofile = new File(toPath, file);
            if (update || !tofile.exists()) {
                if (Constants.DEBUG)
                    Log.i(TAG, "copy1:" + assets + "-->" + tofile);
                createFolderByFile(tofile);
                InputStream inputStream = null;
                try {
                    inputStream = am.open(assets);
                } catch (Exception e) {
                }
                if (inputStream != null) {
                    copyToFile(inputStream, tofile.getAbsolutePath(), progress);
                } else {
                    return 0;
                }
            }
            step(counter, progress);
            return 1;
        } else {
            int count = 0;
            File toDir = new File(toPath);
            createFolder(toDir);
            for (String file : files) {
                String path = join(assets, file);
                if (isDirectory(context, path)) {
                    if (Constants.DEBUG)
                        Log.i(TAG, "copy dir:" + path + "-->" + join(toPath, file));
                    createFolderByFile(new File(toPath, file));
                    count += copyFilesFromAssets(context, path, join(toPath, file), update, progress, counter);
                } else {
                    File f = new File(join(toPath, file));
                    createFolderByFile(f);
                    if (update || !f.exists()) {
                        if (Constants.DEBUG)
                            Log.d(TAG, "copy2:" + path + "-->" + f.getAbsolutePath());
                        copyToFile(am.open(path), f.getAbsolutePath(), progress);
                    } else {
                        if (Constants.DEBUG)
                            Log.d(TAG, "copy ignore:" + path + "-->" + f.getAbsolutePath());
                    }
                    step(counter, progress);
                    count++;
                }
            }
            return count;
        }
    }

    /** 条目计数上报：counter[0]=已完成，counter[1]=预估总数。 */
    private static void step(int[] counter, CopyProgress progress) {
        if (progress == null || counter == null) {
            return;
        }
        progress.onProgress(++counter[0], counter[1]);
    }

    /**
     * 单个大文件（zip/ypk 等档案）的资源拷贝：只在目标缺失、或本 APK 内的资源内容<b>逐字节
     * 变化</b>时才重写目标文件。
     *
     * <p>存在的理由：{@code ResCheckTask} 在版本升级（needsUpdate）时按旧逻辑会无条件重拷
     * {@code scripts.zip}/{@code pics.zip}，重拷把目标的 lastModified 刷新，而
     * {@code NativeScriptBootstrap} 的解压/物化指纹是“大小+lastModified”，于是每次升级都会
     * 白白重做一轮全量解压（老设备实测约 15s/次）。本方法先比长度再比 CRC32，内容没变就
     * 完全不碰目标文件，指纹因此保持稳定，升级启动不再触发重解压。
     *
     * <p>校验和记录在目标同目录的隐藏文件 {@code .<目标文件名>.sig} 中；没有记录时不直接
     * 判定“已变化”，而是再读一次目标文件算校验和比对，避免升级首轮白拷几十 MB。
     *
     * @param update 为 false 时与 {@link #copyFilesFromAssets} 语义一致：目标已存在则不动
     * @return true 表示本次确实重写了目标文件
     */
    public static boolean copyFileFromAssetsIfChanged(Context context, String assets,
            File targetDir, boolean update) throws IOException {
        return copyFileFromAssetsIfChanged(context, assets, targetDir, update, null);
    }

    /**
     * 同上，额外上报字节进度。一轮最多跑“读资源算校验和”“读目标兜底比对”“重写”三段，
     * 每段占整轮刻度的三分之一（{@code grand = 3 * 档案长}）：上报值因此永远单调且不会跑在
     * 真实进度前面（宁可少报），避免“校验和读完就显示本阶段已完成”的假象。
     */
    public static boolean copyFileFromAssetsIfChanged(Context context, String assets,
            File targetDir, boolean update, CopyProgress progress) throws IOException {
        AssetManager am = context.getAssets();
        File target = new File(targetDir, getName(assets));
        File sigFile = signatureFile(targetDir, target.getName());
        if (!target.isFile()) {
            // 目标不存在：重写就是本轮全部工作，直接按自段 0..档案长 上报
            copyToFile(am.open(assets), target.getAbsolutePath(), progress);
            writeFileSignature(sigFile, fileSignature(target));
            if (Constants.DEBUG)
                Log.i(TAG, "copy new:" + assets + "-->" + target.getAbsolutePath());
            return true;
        }
        if (!update) {
            return false;
        }
        // 长度探测：AssetInputStream.available() 给出解压后的总长，与目标文件长度不等即
        // 内容必已变化，可直接重写，省掉“资源+目标”两遍全量读
        long probeLen = -1;
        try (InputStream probe = am.open(assets)) {
            probeLen = probe.available();
        } catch (Exception e) {
            Log.w(TAG, "available() 探测失败，退回校验和比对: " + assets, e);
        }
        final long assetLen = probeLen;
        // 多段共用一个大刻度；档案长度不可得时不分段（各段自报 0..自身总长）
        final long grand = assetLen > 0 ? assetLen * 3 : -1;
        if (assetLen >= 0 && assetLen != target.length()) {
            copyToFile(am.open(assets), target.getAbsolutePath(), scaled(progress, 2 * assetLen, grand));
            writeFileSignature(sigFile, fileSignature(target));
            Log.i(TAG, "资源长度变化，重拷: " + assets + " " + target.length() + "->" + assetLen);
            return true;
        }
        String sig;
        try (InputStream is = am.open(assets)) {
            sig = streamSignature(is, scaled(progress, 0, grand));
        }
        String recorded = readFileSignature(sigFile);
        if (sig.equals(recorded)) {
            Log.i(TAG, "资源内容未变，跳过重拷: " + target.getAbsolutePath());
            return false;
        }
        // 无历史记录（首次跑到本逻辑的升级）：目标与资源内容其实一致时只补记录，不重拷
        if (recorded == null && sig.equals(fileSignature(target, scaled(progress, assetLen, grand)))) {
            writeFileSignature(sigFile, sig);
            Log.i(TAG, "资源内容与目标一致，补记校验和并跳过重拷: " + target.getAbsolutePath());
            return false;
        }
        copyToFile(am.open(assets), target.getAbsolutePath(), scaled(progress, 2 * assetLen, grand));
        writeFileSignature(sigFile, fileSignature(target));
        Log.i(TAG, "资源内容已变，重拷: " + target.getAbsolutePath());
        return true;
    }

    /** 把本段字节进度接到大刻度 {@code grand} 上（{@code base} 为本段起点）；不可预估时原样返回。 */
    private static CopyProgress scaled(final CopyProgress progress, final long base, final long grand) {
        if (progress == null || grand <= 0) {
            return progress;
        }
        return new CopyProgress() {
            @Override
            public void onProgress(long done, long total) {
                progress.onProgress(Math.min(grand, base + done), grand);
            }
        };
    }

    /** 一次流式读取同时算出“长度:CRC32(十六进制)”签名，读完即关闭入流。 */
    private static String streamSignature(InputStream in) throws IOException {
        return streamSignature(in, null);
    }

    /** 同上，{@code progress} 非空时按已读字节上报（total 取起始 available()，不可得则不报）。 */
    private static String streamSignature(InputStream in, CopyProgress progress) throws IOException {
        CRC32 crc = new CRC32();
        byte[] buf = new byte[8192];
        long len = 0;
        int n;
        long total = -1;
        if (progress != null) {
            try {
                total = in.available();
            } catch (Exception e) {
                total = -1;
            }
        }
        try {
            while ((n = in.read(buf)) > 0) {
                crc.update(buf, 0, n);
                len += n;
                if (progress != null && total > 0) {
                    progress.onProgress(len, total);
                }
            }
        } finally {
            close(in);
        }
        return len + ":" + Long.toHexString(crc.getValue());
    }

    /** 读文件算“长度:CRC32”签名；文件不可读返回 null（按未匹配处理，宁可重拷）。 */
    @Nullable
    private static String fileSignature(File file) {
        return fileSignature(file, null);
    }

    @Nullable
    private static String fileSignature(File file, CopyProgress progress) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return streamSignature(in, progress);
        } catch (IOException e) {
            Log.w(TAG, "读取文件校验和失败: " + file, e);
            return null;
        }
    }

    /** 档案拷贝的校验和记录文件：与目标同目录的隐藏文件 {@code .<目标文件名>.sig}。 */
    private static File signatureFile(File targetDir, String fileName) {
        return new File(targetDir, "." + fileName + ".sig");
    }

    @Nullable
    private static String readFileSignature(File sigFile) {
        if (!sigFile.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(sigFile)) {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            if (n <= 0) {
                return null;
            }
            return new String(buf, 0, n, "UTF-8").trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeFileSignature(File sigFile, String sig) {
        if (sig == null) {
            return;
        }
        try (OutputStream out = new FileOutputStream(sigFile)) {
            out.write(sig.getBytes("UTF-8"));
        } catch (IOException e) {
            Log.w(TAG, "写入校验和记录失败: " + sigFile, e);
        }
    }

    public static void createFolderByFile(File file) {
        createFolder(file.getParentFile());
    }

    /**
     * 根据file创建目录，如果file指向的目录已存在，则直接返回true。
     * 创建失败时返回false。
     * 创建成功时返回true
     * @param file
     * @return
     */
    public static boolean createFolder(@Nullable File file) {
        if (file == null) {
            return false;
        }
        if (!file.exists()) {
            return file.mkdirs();
        }
        return true;
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        copy(in, out, null);
    }

    /** 同上，{@code progress} 非空时按已写字节上报（total 取起始 available()，不可得则不报）。 */
    public static void copy(InputStream in, OutputStream out, CopyProgress progress) throws IOException {
        byte[] cache = new byte[1024 * 8];
        int len;
        long done = 0;
        long total = progress == null ? -1 : available(in);
        while ((len = in.read(cache)) != -1) {
            out.write(cache, 0, len);
            if (progress != null && total > 0) {
                done += len;
                progress.onProgress(done, total);
            }
        }
    }

    /** 起始可读总长；不可得（非资源流、实现不支持）返回 -1，调用方据此不报进度。 */
    private static long available(InputStream in) {
        try {
            return in.available();
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean hasAssets(Context context, String name) {
        InputStream is = null;
        try {
            is = context.getAssets().open(name);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            close(is);
        }
    }

    public static void copyToFile(InputStream in, String file) throws IOException {
        copyToFile(in, file, null);
    }

    /** 同上，{@code progress} 非空时按写入字节上报总长与进度（拷贝段在大档案上最耗时）。 */
    public static void copyToFile(InputStream in, String file, CopyProgress progress) throws IOException {
        if (in == null) {
            throw new IOException("copyToFile: source stream is null");
        }
        File target = new File(file);
        File dir = target.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        // 先写入临时文件，完整写完并校验后再替换目标文件。
        // 直接使用 new FileOutputStream(file) 会立刻把已存在的文件（如可用的 pics.zip/scripts.zip）
        // 截断为 0 字节，一旦拷贝中途失败（磁盘满、IO 错误、进程被杀）旧文件就被破坏且无法恢复。
        File tmp = new File(file + ".tmp");
        FileOutputStream outputStream = null;
        boolean success = false;
        try {
            outputStream = new FileOutputStream(tmp);
            copy(in, outputStream, progress);
            outputStream.flush();
            outputStream.getFD().sync();
            outputStream.close();
            outputStream = null;
            if (tmp.length() <= 0) {
                throw new IOException("copied file is empty: " + tmp.getAbsolutePath());
            }
            // 完整写入成功后才替换真正的目标文件
            if (target.exists() && !target.delete()) {
                throw new IOException("cannot remove existing file: " + file);
            }
            if (!tmp.renameTo(target)) {
                throw new IOException("cannot rename temp file to: " + file);
            }
            success = true;
        } finally {
            close(outputStream);
            close(in);
            // 失败时清理临时文件，保留原有可用文件
            if (!success && tmp.exists()) {
                tmp.delete();
            }
        }
    }

    public static boolean createNoMedia(String path) {
        File file = new File(path);
        createFolder(file);
        if (file.isDirectory()) {
            //
            File n = new File(file, ".nomedia");
            if (n.exists()) {
                return true;
            }
            try {
                n.createNewFile();
                return true;
            } catch (IOException e) {
            }
        }
        return false;
    }


    public static File asFile(InputStream is, String outPath) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(outPath);
            int len = 0;
            byte[] buffer = new byte[8192];

            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        } finally {
            if (os != null)
                os.close();
            is.close();
        }
        return new File(outPath);
    }


}

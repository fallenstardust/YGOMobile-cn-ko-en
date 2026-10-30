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
     * 将文件从工程的assets文件夹拷贝到目录中
     */
    public static int copyFilesFromAssets(Context context, String assets, String toPath, boolean update) throws IOException {
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
                    copyToFile(inputStream, tofile.getAbsolutePath());
                } else {
                    return 0;
                }
            }
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
                    count += copyFilesFromAssets(context, path, join(toPath, file), update);
                } else {
                    File f = new File(join(toPath, file));
                    createFolderByFile(f);
                    if (update || !f.exists()) {
                        if (Constants.DEBUG)
                            Log.d(TAG, "copy2:" + path + "-->" + f.getAbsolutePath());
                        copyToFile(am.open(path), f.getAbsolutePath());
                    } else {
                        if (Constants.DEBUG)
                            Log.d(TAG, "copy ignore:" + path + "-->" + f.getAbsolutePath());
                    }
                    count++;
                }
            }
            return count;
        }
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
        AssetManager am = context.getAssets();
        File target = new File(targetDir, getName(assets));
        File sigFile = signatureFile(targetDir, target.getName());
        if (!target.isFile()) {
            copyToFile(am.open(assets), target.getAbsolutePath());
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
        long assetLen = -1;
        try (InputStream probe = am.open(assets)) {
            assetLen = probe.available();
        } catch (Exception e) {
            Log.w(TAG, "available() 探测失败，退回校验和比对: " + assets, e);
        }
        if (assetLen >= 0 && assetLen != target.length()) {
            copyToFile(am.open(assets), target.getAbsolutePath());
            writeFileSignature(sigFile, fileSignature(target));
            Log.i(TAG, "资源长度变化，重拷: " + assets + " " + target.length() + "->" + assetLen);
            return true;
        }
        String sig;
        try (InputStream is = am.open(assets)) {
            sig = streamSignature(is);
        }
        String recorded = readFileSignature(sigFile);
        if (sig.equals(recorded)) {
            Log.i(TAG, "资源内容未变，跳过重拷: " + target.getAbsolutePath());
            return false;
        }
        // 无历史记录（首次跑到本逻辑的升级）：目标与资源内容其实一致时只补记录，不重拷
        if (recorded == null && sig.equals(fileSignature(target))) {
            writeFileSignature(sigFile, sig);
            Log.i(TAG, "资源内容与目标一致，补记校验和并跳过重拷: " + target.getAbsolutePath());
            return false;
        }
        copyToFile(am.open(assets), target.getAbsolutePath());
        writeFileSignature(sigFile, fileSignature(target));
        Log.i(TAG, "资源内容已变，重拷: " + target.getAbsolutePath());
        return true;
    }

    /** 一次流式读取同时算出“长度:CRC32(十六进制)”签名，读完即关闭入流。 */
    private static String streamSignature(InputStream in) throws IOException {
        CRC32 crc = new CRC32();
        byte[] buf = new byte[8192];
        long len = 0;
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                crc.update(buf, 0, n);
                len += n;
            }
        } finally {
            close(in);
        }
        return len + ":" + Long.toHexString(crc.getValue());
    }

    /** 读文件算“长度:CRC32”签名；文件不可读返回 null（按未匹配处理，宁可重拷）。 */
    @Nullable
    private static String fileSignature(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return streamSignature(in);
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
        byte[] cache = new byte[1024 * 8];
        int len;
        while ((len = in.read(cache)) != -1) {
            out.write(cache, 0, len);
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
            copy(in, outputStream);
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

package cn.garymb.ygomobile.utils;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UnzipUtils {
    private static final int BUFFER_SIZE = 1024 * 1024;//1M Byte
    public static final int ZIP_READY = 600;
    public static final int ZIP_UNZIP_OK = 601;
    public static final int ZIP_UNZIP_EXCEPTION = 602;

    /**
     * 解压缩一个文件
     *
     * @param zipFile    压缩文件
     * @param folderPath 解压缩的目标目录
     * @return
     * @throws IOException 当解压缩过程出错时抛出
     */
    public static ArrayList<File> upZipFile(File zipFile, String folderPath) throws IOException {
        ArrayList<File> fileList = new ArrayList<File>();
        File desDir = new File(folderPath);
        if (!desDir.exists()) {
            desDir.mkdirs();
        }
        ZipFile zf = new ZipFile(zipFile);
        for (Enumeration<?> entries = zf.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            InputStream is = zf.getInputStream(entry);
            String str = folderPath + File.separator + entry.getName();
            str = new String(str.getBytes("8859_1"), StandardCharsets.UTF_8);
            File desFile = new File(str);
            if (!desFile.exists()) {
                File fileParentDir = desFile.getParentFile();
                if (!fileParentDir.exists()) {
                    fileParentDir.mkdirs();
                }
                desFile.createNewFile();
            }
            OutputStream os = new FileOutputStream(desFile);
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
            os.close();
            is.close();
            fileList.add(desFile);
        }
        return fileList;
    }

    /**
     * 解压文件名包含传入文字的文件
     *
     * @param zipFile      压缩文件
     * @param folderPath   目标文件夹
     * @param nameContains 传入的文件匹配名
     * @return
     * @throws IOException 当解压缩过程出错时抛出
     */
    public static ArrayList<File> upZipSelectFile(File zipFile, String folderPath, String nameContains) throws IOException {
        ArrayList<File> fileList = new ArrayList<File>();
        File desDir = new File(folderPath);
        if (!desDir.exists()) {
            desDir.mkdirs();
        }
        ZipFile zf = new ZipFile(zipFile);
        for (Enumeration<?> entries = zf.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (entry.getName().contains(nameContains)) {//如果zip中包含.ypk格式的文件，则将其放到/storage/emulated/0/Android/data/cn.garymb.ygomobile.CN/files/ygocore目录下
                InputStream is = zf.getInputStream(entry);
                String str = folderPath + File.separator + entry.getName();
                str = new String(str.getBytes("8859_1"), StandardCharsets.UTF_8);
                File desFile = new File(str);
                if (!desFile.exists()) {
                    File fileParentDir = desFile.getParentFile();
                    if (!fileParentDir.exists()) {
                        fileParentDir.mkdirs();
                    }
                    desFile.createNewFile();
                }
                OutputStream os = new FileOutputStream(desFile);
                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                is.close();
                os.flush();
                os.close();
                fileList.add(desFile);
            }
        }
        return fileList;
    }

    /** {@link #readTar} 每读到一个普通文件回调一次 */
    public interface TarEntryVisitor {
        /**
         * @param content 只含这个文件的内容，不必读完，也不要关闭
         */
        void onEntry(String path, long size, InputStream content) throws IOException;
    }

    /**
     * 顺序读 GNU tar 流（如 {@code tar --zstd -cf} 产出，解压由调用方套上）：只认普通文件（类型 0）
     * 与长文件名（类型 L），其他类型跳过内容。流在文件中间结束时抛出 {@link EOFException}。
     */
    public static void readTar(InputStream in, TarEntryVisitor visitor) throws IOException {
        byte[] header = new byte[512];
        String longName = null;
        while (readFully(in, header) && header[0] != 0) {
            long size = tarOctal(header, 124, 12);
            long padding = (512 - size % 512) % 512;
            if (header[156] == 'L') {
                if (size > 64 * 1024) {
                    throw new IOException("tar 长文件名有 " + size + " 字节，不像正常的包");
                }
                byte[] name = new byte[(int) size];
                if (!readFully(in, name)) {
                    throw new EOFException("tar 长文件名没读完");
                }
                longName = tarString(name, 0, name.length);
                tarSkip(in, padding);
                continue;
            }
            String path = longName != null ? longName : tarString(header, 0, 100);
            longName = null;
            if (header[156] == '0' || header[156] == 0) {
                TarEntryStream content = new TarEntryStream(in, size, path);
                visitor.onEntry(path, size, content);
                tarSkip(content, content.left);//回调没读完的部分
                tarSkip(in, padding);
            } else {
                tarSkip(in, size + padding);
            }
        }
    }

    /** 读满 buf；一个字节都没读到就结束返回 false，读到一半结束抛出 */
    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        for (int off = 0; off < buf.length; ) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                if (off == 0) {
                    return false;
                }
                throw new EOFException("tar 头没读完");
            }
            off += n;
        }
        return true;
    }

    /** 读掉 n 字节，流提前结束抛出 */
    private static void tarSkip(InputStream in, long n) throws IOException {
        byte[] buf = new byte[(int) Math.min(64 * 1024, Math.max(1, n))];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) {
                throw new EOFException("tar 没读完就结束了（还差 " + n + " 字节）");
            }
            n -= r;
        }
    }

    private static long tarOctal(byte[] b, int off, int len) throws IOException {
        long v = 0;
        for (int i = off; i < off + len && b[i] != 0 && b[i] != ' '; i++) {
            if (b[i] < '0' || b[i] > '7') {
                throw new IOException("tar 头里大小字段不是八进制（不是 GNU tar 打的包？）");
            }
            v = v * 8 + (b[i] - '0');
        }
        return v;
    }

    private static String tarString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) {
            end++;
        }
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    /** tar 里一个文件的内容：读到文件末尾返回 -1，底层流提前结束抛出，关闭不影响底层流 */
    private static final class TarEntryStream extends FilterInputStream {
        private final String path;
        private long left;

        TarEntryStream(InputStream in, long size, String path) {
            super(in);
            this.left = size;
            this.path = path;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n < 0) {
                throw new EOFException("tar 在 " + path + " 中间就结束了（还差 " + left + " 字节）");
            }
            left -= n;
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skip = Math.max(0, Math.min(n, left));
            tarSkip(this, skip);
            return skip;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(in.available(), left);
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void close() {
            //底层流还要接着读下一个条目
        }
    }
}

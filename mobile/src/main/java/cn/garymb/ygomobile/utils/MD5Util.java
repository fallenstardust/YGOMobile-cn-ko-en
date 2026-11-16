package cn.garymb.ygomobile.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Util {
    private static final char[] hexChar = "0123456789abcdef".toCharArray();

    public static String toHexString(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(hexChar[(b[i] & 0xf0) >>> 4]);
            sb.append(hexChar[b[i] & 0x0f]);
        }
        return sb.toString();
    }
    public static String getMD5(byte[] data) {
        String hash = null;
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            hash = getStreamMD5(in);
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hash;
    }
    public static String getStringMD5(String source) {
        String hash = null;
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(
                    source.getBytes());
            hash = getStreamMD5(in);
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hash;
    }

    /**
     * 获取文件的MD5
     */
    public static String getFileMD5(String file) {
        String hash = null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            hash = getStreamMD5(in);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(in);
        }
        return hash;
    }

    private static String getStreamMD5(InputStream stream) {
        String hash = null;
        byte[] buffer = new byte[1024];
        BufferedInputStream in = null;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            in = new BufferedInputStream(stream);
            int numRead = 0;
            while ((numRead = in.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }
            hash = toHexString(md5.digest());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(in);
        }
        return hash;
    }

    /** 新的 SHA-256 摘要器；每个 Android 都有 SHA-256 */
    public static MessageDigest newSHA256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 读完并关闭 stream，返回 SHA-256 的十六进制小写；读失败时抛出，不返回 null
     */
    public static String getStreamSHA256(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            MessageDigest sha256 = newSHA256();
            byte[] buffer = new byte[64 * 1024];
            int numRead;
            while ((numRead = in.read(buffer)) != -1) {
                sha256.update(buffer, 0, numRead);
            }
            return toHexString(sha256.digest());
        }
    }

    /** 字符串按 UTF-8 编码后的 SHA-256 */
    public static String getStringSHA256(String source) {
        return toHexString(newSHA256().digest(source.getBytes(StandardCharsets.UTF_8)));
    }
}
package cn.garymb.ygomobile.network;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class BufferIO {

    public static ByteBuffer createPacket(int proto) {
        ByteBuffer buf = ByteBuffer.allocate(0x20000);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 1);
        buf.put((byte) proto);
        return buf;
    }

    // ==================== Read / Write<T> ====================

    public static void writeInt8(ByteBuffer buf, int val) {
        buf.put((byte) val);
    }

    public static void writeInt16(ByteBuffer buf, int val) {
        buf.putShort((short) val);
    }

    public static void writeInt32(ByteBuffer buf, int val) {
        buf.putInt(val);
    }

    public static int readInt8(ByteBuffer buf) {
        return buf.get() & 0xFF;
    }

    public static int readInt16(ByteBuffer buf) {
        return buf.getShort() & 0xFFFF;
    }

    public static int readInt32(ByteBuffer buf) {
        return buf.getInt();
    }

    // ==================== VectorWrite<T> / VectorWriteBlock ====================

    public static void vectorWriteBlock(ByteArrayOutputStream buffer, byte[] src) {
        buffer.write(src, 0, src.length);
    }

    public static void vectorWriteInt8(ByteArrayOutputStream buffer, int val) {
        buffer.write(val & 0xFF);
    }

    public static void vectorWriteInt16(ByteArrayOutputStream buffer, int val) {
        ByteBuffer tmp = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        tmp.putShort((short) val);
        buffer.write(tmp.array(), 0, 2);
    }

    public static void vectorWriteInt32(ByteArrayOutputStream buffer, int val) {
        ByteBuffer tmp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        tmp.putInt(val);
        buffer.write(tmp.array(), 0, 4);
    }

    // ==================== UTF-16 读写 ====================

    public static void writeUTF16(ByteBuffer buf, String str, int maxLen) {
        int charCount = Math.min(str.length(), maxLen);
        for (int i = 0; i < charCount; i++) {
            buf.putChar(str.charAt(i));
        }
        for (int i = charCount; i < maxLen; i++) {
            buf.putChar('\0');
        }
    }

    public static String readUTF16(ByteBuffer buf, int maxLen) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            char c = buf.getChar();
            if (c == 0) {
                buf.position(buf.position() + (maxLen - 1 - i) * 2);
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ==================== CopyWStr / CopyCharArray ====================

    public static String copyWStr(String src, int bufSize) {
        int len = Math.min(src.length(), bufSize - 1);
        return src.substring(0, len);
    }

    public static int copyWStrToBuffer(String src, char[] dst, int bufSize) {
        int len = Math.min(src.length(), bufSize - 1);
        for (int i = 0; i < len; i++) {
            dst[i] = src.charAt(i);
        }
        if (len < dst.length) {
            dst[len] = 0;
        }
        return len;
    }

    public static int copyWStrToIntBuffer(String src, int[] dst, int bufSize) {
        int len = Math.min(src.length(), bufSize - 1);
        for (int i = 0; i < len; i++) {
            dst[i] = src.charAt(i);
        }
        if (len < dst.length) {
            dst[len] = 0;
        }
        return len;
    }

    public static <T> int copyCharArray(String src, T[] dst) {
        int len = Math.min(src.length(), dst.length - 1);
        for (int i = 0; i < len; i++) {
            @SuppressWarnings("unchecked")
            T val = (T) (Object) (int) src.charAt(i);
            dst[i] = val;
        }
        return len;
    }

    // ==================== CopyString / CopyWideString ====================

    public static String copyString(String src, int maxLen) {
        if (src.length() >= maxLen) {
            return src.substring(0, maxLen - 1);
        }
        return src;
    }

    public static String copyWideString(String src, int maxLen) {
        return copyString(src, maxLen);
    }

    // ==================== EncodeUTF8String (wstring → UTF-8) ====================

    public static byte[] encodeUTF8String(String wstr) {
        if (wstr == null || wstr.isEmpty()) {
            return new byte[0];
        }
        return wstr.getBytes(StandardCharsets.UTF_8);
    }

    public static int encodeUTF8String(String wsrc, byte[] dst, int size) {
        if (size == 0) return 0;
        byte[] encoded = wsrc.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(encoded.length, size - 1);
        System.arraycopy(encoded, 0, dst, 0, len);
        dst[len] = 0;
        return len;
    }

    // ==================== DecodeUTF8String (UTF-8 → wstring) ====================

    public static String decodeUTF8String(byte[] utf8Bytes) {
        if (utf8Bytes == null || utf8Bytes.length == 0) {
            return "";
        }
        return new String(utf8Bytes, StandardCharsets.UTF_8);
    }

    public static String decodeUTF8String(byte[] utf8Bytes, int offset, int length) {
        if (utf8Bytes == null || length == 0) {
            return "";
        }
        return new String(utf8Bytes, offset, length, StandardCharsets.UTF_8);
    }

    public static int decodeUTF8String(byte[] src, char[] dst, int size) {
        if (size == 0) return 0;
        String decoded = new String(src, StandardCharsets.UTF_8);
        int len = Math.min(decoded.length(), size - 1);
        decoded.getChars(0, len, dst, 0);
        dst[len] = 0;
        return len;
    }

    // ==================== EncodeUTF8 / DecodeUTF8 (convenience) ====================

    public static byte[] encodeUTF8(String src) {
        return encodeUTF8String(src);
    }

    public static String decodeUTF8(byte[] src) {
        return decodeUTF8String(src);
    }

    // ==================== NullTerminate ====================

    public static void nullTerminate(char[] str) {
        if (str != null && str.length > 0) {
            str[str.length - 1] = 0;
        }
    }

    public static void nullTerminate(byte[] str) {
        if (str != null && str.length > 0) {
            str[str.length - 1] = 0;
        }
    }

    public static void nullTerminate(int[] str) {
        if (str != null && str.length > 0) {
            str[str.length - 1] = 0;
        }
    }

    // ==================== Unicode Surrogate Utilities ====================

    public static boolean isHighSurrogate(int c) {
        return (c >= 0xD800 && c <= 0xDBFF);
    }

    public static boolean isLowSurrogate(int c) {
        return (c >= 0xDC00 && c <= 0xDFFF);
    }

    public static boolean isUnicodeChar(int c) {
        if (isHighSurrogate(c)) return false;
        if (isLowSurrogate(c)) return false;
        return c <= 0x10FFFF;
    }

    // ==================== GetVal ====================

    public static int getVal(String pstr) {
        if (pstr == null || pstr.isEmpty()) return 0;
        char first = pstr.charAt(0);
        if (first >= '0' && first <= '9') {
            try {
                return Integer.parseInt(pstr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    // ==================== Packet Finalize ====================

    public static byte[] finalizePacket(ByteBuffer buf) {
        int pos = buf.position();
        int dataLen = pos - 2;
        buf.putShort(0, (short) dataLen);
        byte[] result = new byte[pos];
        buf.flip();
        buf.get(result);
        return result;
    }
}

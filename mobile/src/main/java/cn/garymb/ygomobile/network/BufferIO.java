package cn.garymb.ygomobile.network;

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

    public static void writeInt16(ByteBuffer buf, int val) {
        buf.putShort((short) val);
    }

    public static void writeInt32(ByteBuffer buf, int val) {
        buf.putInt(val);
    }

    public static void writeInt8(ByteBuffer buf, int val) {
        buf.put((byte) val);
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

    public static void writeUTF16(ByteBuffer buf, String str, int maxLen) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_16LE);
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

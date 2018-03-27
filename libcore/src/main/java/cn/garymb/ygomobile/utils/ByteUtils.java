package cn.garymb.ygomobile.utils;

public class ByteUtils {

    public static void reverse(byte[] data) {
        int size = data.length;
        for (int i = 0; i < size / 2; i++) {
            byte tmp = data[i];
            data[i] = data[size - i - 1];
            data[size - i - 1] = tmp;
        }
    }

    public static int byte2int(byte[] res) {
        String str =String.format("%02x%02x%02x%02x",res[3],res[2],res[1],res[0]);
        return Integer.parseInt(str, 16);
    }

    public static byte[] short2byte(short i) {
        return new byte[]{(byte) (i & 0xff), (byte) (i >> 8 & 0xff)};
    }

    public static int byte2uint(byte b) {
        int i = b;
        if (b < 0) {
            i = 0xff +1+ b;
        }
        return i;
//        String str = String.format("%02x", b);
//        return Integer.parseInt(str, 16);
    }

    public static byte[] int2byte(int i) {
        return new byte[]{(byte) (i & 0xff), (byte) ((i & 0xff00) >> 8), (byte) ((i & 0xff0000) >> 16),
                          (byte) ((i >> 24) & 0xff),
                          };
    }

    public static int str2byte(String str) {
        return Integer.parseInt(str, 16);
    }

//	public static byte[] int2byte2(int i) {
//		String str = String.format("%08x", i);
//		// System.out.println(str);
//		return new byte[] { (byte) str2byte(str.substring(6, 8)), (byte) str2byte(str.substring(4, 6)),
//				(byte) str2byte(str.substring(2, 4)), (byte) str2byte(str.substring(0, 2)), };
//	}
    // public static int byte2int2(byte b) {
    // String str = String.format("%02x", b);
    //// System.out.println(str);
    //// byte b1 = (byte) ((b & 0xf0) >> 8);
    //// byte b2 = (byte) (b & 0xf);
    //// System.out.println(String.format("%01x%01x", b1, b2));
    // return Integer.parseInt(str, 16);
    // }

    // public static int byte2int2(byte[] res) {
    // return (int) res[3] * 0x1000000 + (int) res[2] * 0x10000 + (int) res[1] *
    // 0x100 + (int) res[0];
    // }
    //
    // public static byte[] short2byte2(short i) {
    // String str = String.format("%04x", i);
    // return new byte[] { (byte) str2byte(str.substring(2, 4)), (byte)
    // str2byte(str.substring(0, 2)), };
    // }
}

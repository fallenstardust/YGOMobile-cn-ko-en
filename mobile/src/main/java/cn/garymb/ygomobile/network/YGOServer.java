package cn.garymb.ygomobile.network;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;

public final class YGOServer {
    static {
        try {
            System.loadLibrary("ygoserver");
        } catch (Throwable e) {
        }
    }

    private static native int startServer(String args);
    private static native void stopServer();

    public static int start(
        boolean noChk,
        boolean noShuffle,
        int lp,
        int hand,
        int draw
    ) {
        String path = AppsSettings.get().getResourcePath();
        String cmd = String.format(
            "7911 -1 5 0 F %s %s %d %d %d 0 0 %s",
            noChk ? 'T' : 'F',
            noShuffle ? 'T' : 'F',
            lp, hand, draw, path);
        return startServer(cmd);
    }

    public static void stop() {
        stopServer();
    }
}
package cn.garymb.ygomobile.core;


public class GameConfig {
    static boolean error = false;

    static {
        try {
            System.loadLibrary("game_version");
        } catch (Throwable e) {
            //ignore
            error = true;
        }
    }

    public static int getVersion(){
        if(!error){
            return getGameVersion();
        }
        return 0;
    }

    private static native int getGameVersion();
}

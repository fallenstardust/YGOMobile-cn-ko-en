package cn.garymb.ygomobile.tool;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.SoundPool;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GameSoundPlayer {
    private AssetManager mAssetManager;
    private SoundPool mSoundEffectPool;
    private final Map<String, Integer> mSoundIdMap;
    private boolean mInit = false;

    public GameSoundPlayer(AssetManager mAssetManager) {
        this.mAssetManager = mAssetManager;
        mSoundIdMap = new HashMap<>();
    }

    @SuppressWarnings("deprecation")
    public void initSoundEffectPool() {
        synchronized (this) {
            if (mInit) {
                return;
            }
            mInit = true;
        }
        mSoundEffectPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        AssetManager am = mAssetManager;
        String[] sounds = null;
        try {
            sounds = am.list("sound");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sounds != null) {
            int ret = 0;
            String path = null;
            for (String sound : sounds) {
                path = "sound" + File.separator + sound;
                AssetFileDescriptor fd = null;
                ret = 0;
                try {
                    fd = am.openFd(path);
                    ret = mSoundEffectPool.load(fd, 1);
                } catch (Throwable e) {
                    //ignore
                } finally {
                    if (fd != null) {
                        try {
                            fd.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (ret != 0) {
                        mSoundIdMap.put(path, ret);
                    }
                }
            }
        }
    }

    public void playSoundEffect(String path) {
        if (mSoundEffectPool == null) {
            return;
        }
        Integer id = mSoundIdMap.get(path);
        if (id != null) {
            mSoundEffectPool.play(id, 0.5f, 0.5f, 2, 0, 1.0f);
        }
    }

    public void release() {
        synchronized (this) {
            if (!mInit) {
                return;
            }
            mInit = false;
        }
        mSoundIdMap.clear();
        if (mSoundEffectPool != null) {
            mSoundEffectPool.release();
            mSoundEffectPool = null;
        }
    }
}

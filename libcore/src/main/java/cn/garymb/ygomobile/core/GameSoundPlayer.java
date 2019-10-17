package cn.garymb.ygomobile.core;

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

    public GameSoundPlayer(AssetManager mAssetManager) {
        this.mAssetManager = mAssetManager;
        mSoundIdMap = new HashMap<String, Integer>();
    }

    @SuppressWarnings("deprecation")
    public void initSoundEffectPool() {
        if(mSoundEffectPool==null) {
            mSoundEffectPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        }
        AssetManager am = mAssetManager;
        String[] sounds;
        try {
            sounds = am.list("sound");
            for (String sound : sounds) {
                String path = "sound" + File.separator + sound;
                mSoundIdMap
                        .put(path, mSoundEffectPool.load(am.openFd(path), 1));
            }
        } catch (IOException e) {
            e.printStackTrace();
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
        if (mSoundEffectPool != null) {
            mSoundEffectPool.release();
        }
    }
}

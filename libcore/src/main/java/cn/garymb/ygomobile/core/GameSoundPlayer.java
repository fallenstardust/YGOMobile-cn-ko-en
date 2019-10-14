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
    private Map<String, Integer> mSoundIdMap;

    public GameSoundPlayer(AssetManager mAssetManager) {
        this.mAssetManager = mAssetManager;
    }

    @SuppressWarnings("deprecation")
    public void initSoundEffectPool() {
        mSoundEffectPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        AssetManager am = mAssetManager;
        String[] sounds;
        mSoundIdMap = new HashMap<String, Integer>();
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
        Integer id = mSoundIdMap.get(path);
        if (id != null) {
            mSoundEffectPool.play(id, 0.5f, 0.5f, 2, 0, 1.0f);
        }
    }

    public void release(){
        mSoundEffectPool.release();
    }
}

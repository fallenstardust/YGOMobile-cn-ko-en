package cn.garymb.ygomobile.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;

public class SoundManager {
    private static final String TAG = "SoundManager";

    public enum SFX {
        SUMMON("summon.wav"),
        SPECIAL_SUMMON("specialsummon.wav"),
        ACTIVATE("activate.wav"),
        SET("set.wav"),
        FLIP("flip.wav"),
        REVEAL("reveal.wav"),
        EQUIP("equip.wav"),
        DESTROYED("destroyed.wav"),
        BANISHED("banished.wav"),
        TOKEN("token.wav"),
        NEGATE("negate.wav"),
        ATTACK("attack.wav"),
        DIRECT_ATTACK("directattack.wav"),
        DRAW("draw.wav"),
        SHUFFLE("shuffle.wav"),
        DAMAGE("damage.wav"),
        RECOVER("gainlp.wav"),
        COUNTER_ADD("addcounter.wav"),
        COUNTER_REMOVE("removecounter.wav"),
        COIN("coinflip.wav"),
        DICE("diceroll.wav"),
        NEXT_TURN("nextturn.wav"),
        PHASE("phase.wav"),
        SOUND_MENU("menu.wav"),
        BUTTON("button.wav"),
        INFO("info.wav"),
        QUESTION("question.wav"),
        CARD_PICK("cardpick.wav"),
        CARD_DROP("carddrop.wav"),
        PLAYER_ENTER("playerenter.wav"),
        CHAT("chatmessage.wav");

        final String fileName;

        SFX(String fileName) {
            this.fileName = fileName;
        }
    }

    public enum BGM {
        ALL("BGM"),
        DUEL("BGM"),
        MENU("BGM"),
        DECK("BGM"),
        ADVANTAGE("BGM"),
        DISADVANTAGE("BGM"),
        WIN("BGM"),
        LOSE("BGM");

        final String dirName;

        BGM(String dirName) {
            this.dirName = dirName;
        }
    }

    private SoundPool soundPool;
    private final Map<SFX, Integer> sfxMap = new HashMap<>();
    private MediaPlayer bgmPlayer;
    private final Context context;
    private final Random random = new Random();
    private boolean soundsEnabled = true;
    private boolean musicEnabled = true;
    private float soundVolume = 1.0f;
    private float musicVolume = 1.0f;
    private final Map<BGM, List<String>> bgmList = new HashMap<>();
    private String currentBgm = "";

    public SoundManager(Context context) {
        this.context = context;
    }

    public void init(double soundVol, double musicVol, boolean soundsOn, boolean musicOn) {
        this.soundVolume = (float) soundVol;
        this.musicVolume = (float) musicVol;
        this.soundsEnabled = soundsOn;
        this.musicEnabled = musicOn;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build();

        loadAllSFX();
        refreshBGMList();
    }

    private String getSoundDir() {
        return AppsSettings.get().getResourcePath() + "/" + Constants.CORE_SOUND_PATH;
    }

    private void loadAllSFX() {
        String soundDir = getSoundDir();
        for (SFX sfx : SFX.values()) {
            File file = new File(soundDir, sfx.fileName);
            if (file.exists()) {
                int id = soundPool.load(file.getAbsolutePath(), 1);
                sfxMap.put(sfx, id);
            } else {
                Log.w(TAG, "SFX file not found: " + file.getAbsolutePath());
            }
        }
    }

    public void playSoundEffect(SFX sound) {
        if (!soundsEnabled || soundPool == null) return;
        Integer id = sfxMap.get(sound);
        if (id != null && id != 0) {
            soundPool.play(id, soundVolume, soundVolume, 1, 0, 1.0f);
        }
    }

    public void refreshBGMList() {
        String bgmDir = getSoundDir() + "/BGM";
        File dir = new File(bgmDir);
        if (!dir.exists() || !dir.isDirectory()) return;

        List<String> allFiles = new ArrayList<>();
        File[] files = dir.listFiles((d, name) ->
                name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav"));
        if (files != null) {
            for (File f : files) {
                allFiles.add(f.getAbsolutePath());
            }
        }
        bgmList.clear();
        for (BGM scene : BGM.values()) {
            bgmList.put(scene, new ArrayList<>(allFiles));
        }
    }

    public void playBGM(BGM scene) {
        if (!musicEnabled) return;
        List<String> list = bgmList.get(scene);
        if (list == null || list.isEmpty()) return;
        String path = list.get(random.nextInt(list.size()));
        if (path.equals(currentBgm)) return;
        playMusic(path, true);
    }

    public void playMusic(String path, boolean loop) {
        stopBGM();
        if (!musicEnabled) return;
        try {
            bgmPlayer = new MediaPlayer();
            bgmPlayer.setDataSource(path);
            bgmPlayer.setLooping(loop);
            bgmPlayer.setVolume(musicVolume, musicVolume);
            bgmPlayer.prepareAsync();
            bgmPlayer.setOnPreparedListener(mp -> mp.start());
            currentBgm = path;
        } catch (IOException e) {
            Log.e(TAG, "Failed to play BGM: " + path, e);
        }
    }

    public void stopBGM() {
        if (bgmPlayer != null) {
            try {
                if (bgmPlayer.isPlaying()) {
                    bgmPlayer.stop();
                }
                bgmPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            bgmPlayer = null;
            currentBgm = "";
        }
    }

    public void stopSound() {
        if (soundPool != null) {
            soundPool.autoPause();
        }
    }

    public void setSoundVolume(double volume) {
        this.soundVolume = (float) volume;
    }

    public void setMusicVolume(double volume) {
        this.musicVolume = (float) volume;
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(musicVolume, musicVolume);
        }
    }

    public void enableSounds(boolean enable) {
        this.soundsEnabled = enable;
    }

    public void enableMusic(boolean enable) {
        this.musicEnabled = enable;
        if (!enable) stopBGM();
    }

    public void release() {
        stopBGM();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        sfxMap.clear();
    }
}

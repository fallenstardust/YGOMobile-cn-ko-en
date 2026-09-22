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

    /**
     * BGM 场景。dirName 为 sound/BGM 下的子目录名（对齐 gframe sound_manager.cpp
     * RefreshBGMList 创建的 duel/menu/deck/advantage/disadvantage/win/lose 目录）；
     * ALL 无独立目录，代表根目录与所有子目录音乐的合集。
     */
    public enum BGM {
        ALL(""),
        DUEL("duel"),
        MENU("menu"),
        DECK("deck"),
        ADVANTAGE("advantage"),
        DISADVANTAGE("disadvantage"),
        WIN("win"),
        LOSE("lose");

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
    // 对齐 C++ chkMusicMode（strings.conf 1281「按场景切换音乐」）：
    // true=各场景从自己子目录选曲；false=所有场景统一走 ALL 曲池
    private boolean musicMode = false;
    private float soundVolume = 1.0f;
    private float musicVolume = 1.0f;
    private final Map<BGM, List<String>> bgmList = new HashMap<>();
    private String currentBgm = "";
    // 当前已播放的场景（对齐 C++ bgm_scene）：同场景不重复切歌
    private BGM bgmScene = null;

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

    /** 回放快进重排期间的音效静默开关：置位时 playSoundEffect 全部丢弃，
     *  避免逐帧重放历史消息时音效爆音（ReplayEngine 快进前置位、落点后复位） */
    private volatile boolean effectsSuppressed = false;

    public void setEffectsSuppressed(boolean suppressed) {
        this.effectsSuppressed = suppressed;
    }

    public void playSoundEffect(SFX sound) {
        if (effectsSuppressed) return;
        if (!soundsEnabled || soundPool == null) return;
        Integer id = sfxMap.get(sound);
        if (id != null && id != 0) {
            soundPool.play(id, soundVolume, soundVolume, 1, 0, 1.0f);
        }
    }

    public void refreshBGMList() {
        bgmList.clear();
        for (BGM scene : BGM.values()) bgmList.put(scene, new ArrayList<>());
        File root = new File(getSoundDir(), "BGM");
        if (!root.exists() || !root.isDirectory()) return;
        List<String> all = bgmList.get(BGM.ALL);
        List<String> duel = bgmList.get(BGM.DUEL);
        // 根目录音乐：同时计入 ALL 与 DUEL（对齐 C++ RefreshBGMDir("", DUEL)）
        for (File f : listMusicFiles(root)) {
            all.add(f.getAbsolutePath());
            duel.add(f.getAbsolutePath());
        }
        // 各场景子目录：同时计入 ALL 与对应场景（对齐 RefreshBGMDir(sub, scene)）
        for (BGM scene : BGM.values()) {
            if (scene == BGM.ALL || scene == BGM.DUEL) continue;
            File sub = new File(root, scene.dirName);
            if (sub.exists() && sub.isDirectory()) {
                for (File f : listMusicFiles(sub)) {
                    all.add(f.getAbsolutePath());
                    bgmList.get(scene).add(f.getAbsolutePath());
                }
            }
        }
    }

    /** 目录下音频文件（mp3/ogg/wav，忽略大小写） */
    private static File[] listMusicFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav");
        });
        return files != null ? files : new File[0];
    }

    public void playBGM(BGM scene) {
        if (!musicEnabled) return;
        // 对齐 C++ PlayBGM：未勾选「按场景切换音乐」时所有场景统一走 ALL 曲池
        BGM eff = musicMode ? scene : BGM.ALL;
        List<String> list = bgmList.get(eff);
        if ((list == null || list.isEmpty()) && eff != BGM.ALL) {
            eff = BGM.ALL;                 // 该场景无曲时回退 ALL
            list = bgmList.get(eff);
        }
        if (list == null || list.isEmpty()) return;
        // 同场景且仍在播放则不切歌（对齐 C++ scene!=bgm_scene || !exists(current)）
        if (eff == bgmScene && bgmPlayer != null) return;
        String path = list.get(random.nextInt(list.size()));
        playMusic(path, true);
        bgmScene = eff;
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

    /** 「按场景切换音乐」开关（chkMusicMode，strings.conf 1281）：
     *  true=各场景从自己子目录选曲；false=统一 ALL 曲池 */
    public void setMusicMode(boolean musicMode) {
        this.musicMode = musicMode;
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

package cn.garymb.ygomobile.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.CrashHandler;

public class SoundManager {
    private static final String TAG = "SoundManager";
    /** 曲池整轮不可播后的重试冷却（ms） */
    private static final long BGM_RETRY_COOLDOWN_MS = 10000L;

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
    // BGM 播放异常仅首次落盘 ygocore/log（避免每次场景刷新重复写）
    private boolean bgmFailureReported = false;
    // 整轮选曲全部失败后的重试冷却截止时刻（refreshBGMList 重新扫盘时清零）
    private long bgmRetryAfterMs = 0L;

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
     *  避免逐帧重放历史消息时音效爆音（ReplayPlayer 快进前置位、落点后复位） */
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
        bgmRetryAfterMs = 0L;
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
        // 曲池整体不可播时短暂冷却，避免每次场景刷新都扫全表并重建 MediaPlayer
        if (System.currentTimeMillis() < bgmRetryAfterMs) return;
        // 从随机起点依次尝试：单个文件损坏 / 格式不支持时不再整体静默失声
        int start = random.nextInt(list.size());
        for (int i = 0; i < list.size(); i++) {
            if (playMusic(list.get((start + i) % list.size()), true)) {
                bgmScene = eff;
                return;
            }
        }
        bgmRetryAfterMs = System.currentTimeMillis() + BGM_RETRY_COOLDOWN_MS;
        Log.w(TAG, "no playable BGM in scene " + eff);
    }

    /**
     * 播放一首 BGM，返回是否成功启动准备。
     * 失败绝不向上抛（音频异常不应顶掉主线程），并把首次异常经
     * {@link CrashHandler#report} 落盘 ygocore/log 供定位。
     */
    private boolean playMusic(String path, boolean loop) {
        stopBGM();
        if (!musicEnabled) return false;
        MediaPlayer mp = null;
        try {
            File file = new File(path);
            if (!file.isFile() || file.length() == 0) {
                Log.w(TAG, "BGM file missing or empty: " + path);
                return false;
            }
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(path);
            mp.setLooping(loop);
            mp.setVolume(musicVolume, musicVolume);
            // 监听必须在 prepareAsync 之前挂上：异步准备可能在下一行执行前就完成
            final MediaPlayer player = mp;
            mp.setOnPreparedListener(m -> {
                try {
                    m.start();
                } catch (IllegalStateException e) {
                    // 准备完成与停止/释放竞态：忽略即可
                    Log.w(TAG, "BGM start skipped", e);
                }
            });
            mp.setOnErrorListener((m, what, extra) -> {
                Log.e(TAG, "BGM error what=" + what + " extra=" + extra + " path=" + path);
                if (bgmPlayer == player) {
                    // 复位场景与当前曲，使下一次 updateBGM 能重新选曲
                    bgmPlayer = null;
                    currentBgm = "";
                    bgmScene = null;
                }
                releaseQuietly(m);
                return true;
            });
            mp.prepareAsync();
            bgmPlayer = mp;
            currentBgm = path;
            return true;
        } catch (Exception e) {
            // IOException / IllegalStateException / IllegalArgumentException 全部兜住
            Log.e(TAG, "Failed to play BGM: " + path, e);
            if (!bgmFailureReported) {
                bgmFailureReported = true;
                CrashHandler.getInstance().report("音频-BGM播放", e);
            }
            releaseQuietly(mp);
            if (bgmPlayer == mp) bgmPlayer = null;
            currentBgm = "";
            return false;
        }
    }

    public void stopBGM() {
        MediaPlayer mp = bgmPlayer;
        bgmPlayer = null;
        currentBgm = "";
        if (mp == null) return;
        try {
            // 未进入 Started 状态（准备中 / 出错）时 stop() 会抛 IllegalStateException，
            // 旧写法因此跳过 release() 造成原生实例泄漏，泄漏后又使 prepareAsync 抛异常
            mp.stop();
        } catch (Exception ignored) {
        } finally {
            releaseQuietly(mp);
        }
    }

    private static void releaseQuietly(MediaPlayer mp) {
        if (mp == null) return;
        try {
            mp.release();
        } catch (Exception ignored) {
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
        MediaPlayer mp = bgmPlayer;
        if (mp != null) {
            try {
                mp.setVolume(musicVolume, musicVolume);
            } catch (Exception ignored) {
            }
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

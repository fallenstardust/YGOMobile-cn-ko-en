package cn.garymb.ygomobile.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
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
import ocgcore.DataManager;
import ocgcore.data.Card;

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
    // true=各场景从自己子目录选曲；false=所有场景统一走 ALL 曲池。
    // 默认 true：未显式设置时也按场景切换（菜单/卡组/决斗/胜负各自子目录），
    // 否则所有场景统一走 ALL 且被去重锁定，表现为「BGM 不随场景切换」。
    private boolean musicMode = true;
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
    /** 退后台暂停标志（onPause 置位 / onResume 清零）：暂停不释放播放器，
     *  置位期间 prepareAsync 完成也不起播，避免切到新曲时后台外声 */
    private boolean bgmPausedByBackground = false;
    // === 召唤主题歌（chants，对齐 C++ ChantsList / bgm_process）===
    /** code（文件名数字，含 alias）→ 主题歌文件绝对路径 */
    private final Map<Integer, String> chantsMap = new HashMap<>();
    /** 主题歌播放期间禁止场景切歌打断（对齐 C++ bgm_process=false） */
    private boolean chantPlaying = false;
    /** 主题歌播完回调：由外部（Activity）重算场景恢复 BGM */
    private Runnable chantFinishListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        // 各场景子目录：同时计入 ALL 与对应场景（对齐 RefreshBGMDir(sub, scene)）。
        // DUEL 子目录同样要扫（对齐 sound_manager.cpp L39 RefreshBGMDir("duel", DUEL)）：
        // 此前跳过 DUEL 使 sound/BGM/duel 下的音乐从未入曲池，决斗场景只能播根目录曲或回退 ALL
        for (BGM scene : BGM.values()) {
            if (scene == BGM.ALL) continue;
            File sub = new File(root, scene.dirName);
            if (sub.exists() && sub.isDirectory()) {
                for (File f : listMusicFiles(sub)) {
                    all.add(f.getAbsolutePath());
                    bgmList.get(scene).add(f.getAbsolutePath());
                }
            }
        }
        refreshChantsList();
    }

    /**
     * 扫描召唤主题歌目录（对齐 C++ RefreshChantsList 扫 ./sound/chants；
     * 按需求支持 sound/BGM/chants，两个目录都扫）。文件名（去扩展名）解析为
     * int 卡码/alias 存入曲表；非数字文件名忽略。
     */
    private void refreshChantsList() {
        chantsMap.clear();
        String soundDir = getSoundDir();
        scanChantsDir(new File(soundDir, "BGM/chants"));
        scanChantsDir(new File(soundDir, "chants"));
    }

    private void scanChantsDir(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        for (File f : listMusicFiles(dir)) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) continue;
            try {
                int code = Integer.parseInt(name.substring(0, dot));
                if (code != 0 && !chantsMap.containsKey(code))
                    chantsMap.put(code, f.getAbsolutePath());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * 召唤主题歌（对齐 C++ SoundManager::PlayChant）：卡片有 alias 时优先用 alias
     * 查曲表（兼容直接以本卡 code 命名），命中且非当前曲则切 BGM 播放该曲（不循环），
     * 置 chantPlaying 阻止场景切歌打断，播完后经回调恢复场景 BGM。返回是否成功播放。
     */
    public boolean playChant(int code) {
        if (!musicEnabled || chantsMap.isEmpty()) return false;
        int key = code;
        try {
            Card card = DataManager.get().getCardManager().getCard(code);
            if (card != null && card.Alias != 0) key = card.Alias;
        } catch (Exception ignored) {
        }
        String path = chantsMap.get(key);
        if (path == null && key != code) path = chantsMap.get(code);
        if (path == null || path.equals(currentBgm)) return false;
        if (!playMusic(path, false)) return false;
        chantPlaying = true;
        // 主题歌不属于任何场景曲池：置空场景使播完后按当前场景重新选曲
        bgmScene = null;
        return true;
    }

    /** 主题歌播放结束回调（主线程）：外部据此重算并恢复场景 BGM */
    public void setOnChantFinishListener(Runnable listener) {
        this.chantFinishListener = listener;
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
        // 召唤主题歌播放期间不允许场景切歌打断（对齐 C++ PlayBGM 的 bgm_process 条件）
        if (chantPlaying && bgmPlayer != null) return;
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
                    // 已退后台：不起播，回前台经 resumeBGM 从 Prepared 状态续播
                    if (bgmPausedByBackground) return;
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
                    chantPlaying = false;
                }
                releaseQuietly(m);
                return true;
            });
            // 非循环曲目（chants 主题歌）播完：复位状态并回调恢复场景 BGM
            if (!loop) {
                mp.setOnCompletionListener(m -> {
                    if (bgmPlayer == player) {
                        bgmPlayer = null;
                        currentBgm = "";
                        bgmScene = null;
                        chantPlaying = false;
                    }
                    releaseQuietly(m);
                    final Runnable r = chantFinishListener;
                    if (r != null) mainHandler.post(r);
                });
            }
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

    /**
     * 退出应用（桌面/其他应用，Activity onPause）：暂停 BGM 而不释放播放器，
     * 保留 bgmScene/currentBgm 使回前台原曲续播（updateBGM 同场景去重不会重新选曲）；
     * 播放器尚未就绪（prepareAsync 未完成）时仅置标志，onPrepared 据此不起播
     */
    public void pauseBGM() {
        bgmPausedByBackground = true;
        MediaPlayer mp = bgmPlayer;
        if (mp == null) return;
        try {
            if (mp.isPlaying()) mp.pause();
        } catch (Exception ignored) {
        }
    }

    /** 回前台（onResume）：续播 pauseBGM 暂停的曲目；无播放器/状态异常时复位标志，
     *  由调用方随后的 updateBGM 按当前场景重算（无曲起新曲、异常清场景锁重新选曲） */
    public void resumeBGM() {
        if (!bgmPausedByBackground) return;
        bgmPausedByBackground = false;
        MediaPlayer mp = bgmPlayer;
        if (mp == null) return;
        try {
            mp.start();
        } catch (Exception e) {
            // 错误/已释放等异常态：清场景锁使 updateBGM 能重新选曲起播
            bgmScene = null;
        }
    }

    public void stopBGM() {
        MediaPlayer mp = bgmPlayer;
        bgmPlayer = null;
        currentBgm = "";
        chantPlaying = false;
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
     *  true=各场景从自己子目录选曲；false=统一 ALL 曲池。
     *  切换后由调用方 updateBGM 重算场景立即生效 */
    public void setMusicMode(boolean musicMode) {
        if (this.musicMode == musicMode) return;
        this.musicMode = musicMode;
        // 曲池语义变化：清场景锁定使下一帧按新池重新选曲
        bgmScene = null;
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

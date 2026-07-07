package cn.garymb.ygomobile.game;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ReplayReader {
    private static final String TAG = "ReplayReader";

    public static final int REPLAY_COMPRESSED = 0x1;
    public static final int REPLAY_TAG = 0x2;
    public static final int REPLAY_DECODED = 0x4;
    public static final int REPLAY_SINGLE_MODE = 0x8;
    public static final int REPLAY_UNIFORM = 0x10;
    public static final int REPLAY_ID_YRP1 = 0x31707279;
    public static final int REPLAY_ID_YRP2 = 0x32707279;

    public static class ReplayHeader {
        public int id;
        public int version;
        public int flag;
        public int seed;
        public int datasize;
        public int startTime;
        public byte[] props = new byte[8];
    }

    public static class ExtendedReplayHeader {
        public ReplayHeader base = new ReplayHeader();
        public int[] seedSequence = new int[8];
        public int headerVersion = 1;
        public int value1, value2, value3;
    }

    public static class DuelParameters {
        public int startLp;
        public int startHand;
        public int drawCount;
        public int duelFlag;
    }

    public static class DeckInfo {
        public List<Integer> main = new ArrayList<>();
        public List<Integer> extra = new ArrayList<>();
    }

    public static class ReplayData {
        public ExtendedReplayHeader header = new ExtendedReplayHeader();
        public List<String> playerNames = new ArrayList<>();
        public DuelParameters params = new DuelParameters();
        public List<DeckInfo> decks = new ArrayList<>();
        public String scriptName = "";
        public ByteBuffer replayBuffer;
        public boolean isTag = false;
        public boolean isSingleMode = false;

        public boolean hasFlag(int flag) {
            return (header.base.flag & flag) != 0;
        }
    }

    public static ReplayData loadReplay(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "Replay file not found: " + filePath);
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] allBytes = readAll(fis);
            return parseReplay(allBytes);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read replay file", e);
            return null;
        }
    }

    private static byte[] readAll(FileInputStream fis) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = fis.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    public static ReplayData parseReplay(byte[] data) {
        if (data == null || data.length < 32) return null;

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        ReplayData replay = new ReplayData();

        ExtendedReplayHeader eh = replay.header;
        ReplayHeader h = eh.base;

        h.id = buf.getInt();
        h.version = buf.getInt();
        h.flag = buf.getInt();
        h.seed = buf.getInt();
        h.datasize = buf.getInt();
        h.startTime = buf.getInt();
        buf.get(h.props);

        if (h.id != REPLAY_ID_YRP1 && h.id != REPLAY_ID_YRP2) {
            Log.e(TAG, "Invalid replay ID: " + Integer.toHexString(h.id));
            return null;
        }
        if (h.version < 0x12d0) {
            Log.e(TAG, "Replay version too old: " + Integer.toHexString(h.version));
            return null;
        }
        if (h.version >= 0x1353 && (h.flag & REPLAY_UNIFORM) == 0) {
            Log.e(TAG, "Missing REPLAY_UNIFORM flag for version >= 0x1353");
            return null;
        }

        if (h.id == REPLAY_ID_YRP2) {
            for (int i = 0; i < 8; i++) eh.seedSequence[i] = buf.getInt();
            eh.headerVersion = buf.getInt();
            eh.value1 = buf.getInt();
            eh.value2 = buf.getInt();
            eh.value3 = buf.getInt();
        }

        replay.isTag = (h.flag & REPLAY_TAG) != 0;
        replay.isSingleMode = (h.flag & REPLAY_SINGLE_MODE) != 0;

        int remaining = data.length - buf.position();
        byte[] compressedOrRaw = new byte[remaining];
        buf.get(compressedOrRaw);

        byte[] replayBytes;
        if ((h.flag & REPLAY_COMPRESSED) != 0) {
            replayBytes = lzmaDecompress(compressedOrRaw, h.datasize, h.props);
            if (replayBytes == null) {
                Log.e(TAG, "LZMA decompression failed");
                return null;
            }
        } else {
            replayBytes = compressedOrRaw;
        }

        replay.replayBuffer = ByteBuffer.wrap(replayBytes).order(ByteOrder.LITTLE_ENDIAN);

        if (!readInfo(replay)) {
            Log.e(TAG, "Failed to read replay info");
            return null;
        }

        return replay;
    }

    private static boolean readInfo(ReplayData replay) {
        ByteBuffer buf = replay.replayBuffer;
        int playerCount = replay.isTag ? 4 : 2;

        for (int i = 0; i < playerCount; i++) {
            String name = readName(buf);
            if (name == null) return false;
            replay.playerNames.add(name);
        }

        replay.params.startLp = buf.getInt();
        replay.params.startHand = buf.getInt();
        replay.params.drawCount = buf.getInt();
        replay.params.duelFlag = buf.getInt();

        // 使用 header flag 作为主要的 tag 判断依据，duelFlag 仅作为参考
        // 移除严格的一致性检查，避免误判合法的 tag 录像
        boolean duelFlagTag = (replay.params.duelFlag & 0x2000) != 0;
        if (replay.isTag && !duelFlagTag) {
            Log.w(TAG, "Header indicates tag mode but duelFlag does not, trusting header");
        } else if (!replay.isTag && duelFlagTag) {
            Log.w(TAG, "DuelFlag indicates tag mode but header does not, trusting header");
        }

        if (replay.isSingleMode) {
            int slen = buf.getShort() & 0xFFFF;
            if (slen == 0 || slen > 255) return false;
            byte[] filename = new byte[slen];
            buf.get(filename);
            String fn = new String(filename, StandardCharsets.UTF_8);
            if (fn.startsWith("./single/")) {
                replay.scriptName = fn.substring(9);
            } else {
                return false;
            }
        } else {
            for (int p = 0; p < playerCount; p++) {
                DeckInfo deck = new DeckInfo();
                int mainCount = buf.getInt();
                if (mainCount > 256) return false;
                for (int i = 0; i < mainCount; i++) {
                    deck.main.add(buf.getInt());
                }
                int extraCount = buf.getInt();
                if (extraCount > 256) return false;
                for (int i = 0; i < extraCount; i++) {
                    deck.extra.add(buf.getInt());
                }
                replay.decks.add(deck);
            }
        }

        replay.replayBuffer = ByteBuffer.wrap(
                buf.array(), buf.position(), buf.remaining()
        ).order(ByteOrder.LITTLE_ENDIAN);

        return true;
    }

    private static String readName(ByteBuffer buf) {
        if (buf.remaining() < 40) return null;
        char[] chars = new char[20];
        int len = 0;
        for (int i = 0; i < 20; i++) {
            int c = buf.getShort() & 0xFFFF;
            if (c != 0) {
                chars[len++] = (char) c;
            }
        }
        return new String(chars, 0, len);
    }

    private static byte[] lzmaDecompress(byte[] compressed, int expectedSize, byte[] props) {
        try {
            return org.tukaani.xz.LZMAInputStream.class != null
                    ? lzmaDecompressTukaani(compressed, expectedSize, props)
                    : lzmaDecompressManual(compressed, expectedSize, props);
        } catch (NoClassDefFoundError e) {
            return lzmaDecompressManual(compressed, expectedSize, props);
        }
    }

    private static byte[] lzmaDecompressTukaani(byte[] compressed, int expectedSize, byte[] props) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            byte propsByte = props[0];
            int dictSize = 0;
            for (int i = 0; i < 4; i++) {
                dictSize |= (props[1 + i] & 0xFF) << (8 * i);
            }

            org.tukaani.xz.LZMAInputStream lzmaIn = new org.tukaani.xz.LZMAInputStream(
                    bais, -1L, propsByte, dictSize);

            byte[] output = new byte[expectedSize];
            int totalRead = 0;
            while (totalRead < expectedSize) {
                int read = lzmaIn.read(output, totalRead, expectedSize - totalRead);
                if (read < 0) break;
                totalRead += read;
            }
            lzmaIn.close();
            return totalRead == expectedSize ? output : null;
        } catch (Exception e) {
            Log.e(TAG, "LZMA tukaani failed", e);
            return null;
        }
    }

    private static byte[] lzmaDecompressManual(byte[] compressed, int expectedSize, byte[] props) {
        Log.w(TAG, "LZMA decompression not available. Add org.tukaani:xz:1.9 dependency.");
        return null;
    }

    public static List<String> listReplays(String replayDir) {
        List<String> names = new ArrayList<>();
        File dir = new File(replayDir);
        if (!dir.exists()) return names;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yrp"));
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                names.add(f.getName());
            }
        }
        return names;
    }

    public static boolean deleteReplay(String replayPath) {
        File file = new File(replayPath);
        if (!file.exists()) return false;
        String name = file.getName();
        if (name.contains("/") || name.contains("\\")) return false;
        return file.delete();
    }

    public static boolean renameReplay(String oldPath, String newName) {
        File oldFile = new File(oldPath);
        if (!oldFile.exists()) return false;
        String oldName = oldFile.getName();
        if (oldName.contains("/") || oldName.contains("\\") || 
            newName.contains("/") || newName.contains("\\")) {
            return false;
        }
        if (!newName.endsWith(".yrp")) {
            newName = newName + ".yrp";
        }
        File parentDir = oldFile.getParentFile();
        File newFile = new File(parentDir, newName);
        return oldFile.renameTo(newFile);
    }

    public static boolean saveDeck(ReplayData replayData, int deckIndex, String outputPath) {
        if (replayData == null || replayData.decks == null) return false;
        if (deckIndex < 0 || deckIndex >= replayData.decks.size()) return false;
        
        DeckInfo deck = replayData.decks.get(deckIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("#created by YGOMobile\n");
        sb.append("#main\n");
        for (int i = deck.main.size() - 1; i >= 0; i--) {
            sb.append(deck.main.get(i)).append("\n");
        }
        sb.append("#extra\n");
        for (int i = deck.extra.size() - 1; i >= 0; i--) {
            sb.append(deck.extra.get(i)).append("\n");
        }
        sb.append("!side\n");
        
        try (java.io.FileWriter writer = new java.io.FileWriter(outputPath)) {
            writer.write(sb.toString());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save deck", e);
            return false;
        }
    }

    public static int getPlayerCount(ReplayData replayData) {
        if (replayData == null) return 0;
        return replayData.isTag ? 4 : 2;
    }

    public static String getPlayerName(ReplayData replayData, int index) {
        if (replayData == null || replayData.playerNames == null) return "";
        if (index < 0 || index >= replayData.playerNames.size()) return "";
        return replayData.playerNames.get(index);
    }
}

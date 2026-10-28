package ocgcore;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.WorkerThread;

import com.file.zip.ZipEntry;
import com.file.zip.ZipFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipInputStream;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.data.Card;


public class CardManager {
    private static int cdbNum = 0;
    /** 卡表引用：loadCards 重建时整表替换（volatile 发布），并发读者要么看到完整旧表、
     *  要么看到完整新表，不会读到 clear+逐项put 半途的空表/脏表（SparseArray 本身非线程安全） */
    private volatile SparseArray<Card> cardDataHashMap = new SparseArray<>();
    private final String dbDir;
    private final String exDbPath;
    private static final String TAG = String.valueOf(CardManager.class);

    /** texts 表脚本提示文字列（str1~str16），对齐 C++ data_manager.cpp SELECT_STMT；
     * 供 MSG_SELECT_OPTION 按 卡号*16+n 解析卡片脚本提示文字 */
    private static final String STR_COLUMNS =
            ", str1, str2, str3, str4, str5, str6, str7, str8" +
            ", str9, str10, str11, str12, str13, str14, str15, str16";

    /**
     * @see DataManager#getCardManager()
     * @param dbDir
     * @param exPath
     */
    CardManager(String dbDir, String exPath) {
        this.dbDir = dbDir;
        this.exDbPath = exPath;
    }

    private static SQLiteDatabase openDatabase(String file) {
        return SQLiteDatabase.openDatabase(file, null,
                SQLiteDatabase.NO_LOCALIZED_COLLATORS | SQLiteDatabase.OPEN_READONLY);
    }

    public static boolean checkDataBase(File file) {
        if (!file.exists()) {
            return false;
        }
        Cursor reader = null;
        SQLiteDatabase db = null;
        boolean rs = false;
        try {
            db = openDatabase(file.getPath());
            reader = db.rawQuery("select datas.id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,\"desc\" from datas,texts  where datas.id = texts.id limit 1;", null);
            rs = reader != null;
        } catch (Throwable e) {
            //ignore
        } finally {
            IOUtils.close(reader);
        }
        if (!rs) {
            try {
                reader = db.rawQuery("select datas._id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,\"desc\" from datas,texts where datas._id = texts._id  limit 1;", null);
                rs = reader != null;
            } catch (Throwable e) {
                //ignore
            } finally {
                IOUtils.close(reader);
            }
        }
        IOUtils.close(db);
        return rs;
    }

    public static List<File> readZipCdb(String zipPath) throws IOException {
        String savePath = App.get().getExternalCacheDir().getAbsolutePath();
        List<File> fileList = new ArrayList<>();

        ZipFile zf = new ZipFile(zipPath, "GBK");
        InputStream in = new BufferedInputStream(new FileInputStream(zipPath));
        ZipInputStream zin = new ZipInputStream(in);
        ZipEntry ze;
        Enumeration<ZipEntry> entris = zf.getEntries();
        while (entris.hasMoreElements()) {
            ze = entris.nextElement();
            if (ze.isDirectory()) {
                //Do nothing
            } else {
                if (ze.getName().endsWith(".cdb")) {
                    File file = new File(savePath, "cards" + cdbNum + ".cdb");
                    InputStream inputStream = zf.getInputStream(ze);
                    OutputStream os = new FileOutputStream(file);
                    int bytesRead = 0;
                    byte[] buffer = new byte[8192];
                    while ((bytesRead = inputStream.read(buffer, 0, 8192)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    os.close();
                    inputStream.close();
                    fileList.add(file);
                    cdbNum++;
                }
            }
        }
        zin.closeEntry();
        return fileList;
    }

    public synchronized Card getCard(int code) {
        Card card = cardDataHashMap.get(code);
        if (card == null) {
            // 未知卡缓存空壳（修复旧实现 new 两次导致返回对象与缓存对象不是同一个）；
            // 下次 loadCards 整表替换时空壳自然被弃，不会永久污染真实卡数据
            card = new Card(code);
            cardDataHashMap.put(code, card);
            return card;
        }
        return card;
    }

    public int getCount() {
        return cardDataHashMap.size();
    }

    public SparseArray<Card> getAllCards() {
        return cardDataHashMap;
    }

    /**
     * 清空cardDataHashMap，之后从cdb文件读取卡牌，到cardDataHashMap
     * 如果开启了先行卡，
     *
     * <p>读取顺序与引擎侧 {@link cn.garymb.ygomobile.AppsSettings#makeCdbList}、
     * cdb 统一优先级一致（后读取覆盖同名卡，低→高）：主 cards.cdb → 卡包
     * zip/ypk（按文件名升序，靠后名包覆盖靠前名包）→ 卡包物化 cdb
     *（{@code expansions/cdb/}，与包内 cdb 同源）→ expansions 散 cdb（最高）。
     */
    @WorkerThread
    public void loadCards() {
        //先在全新表上完整构建，最后一次性发布：消除外部线程读到空表/半表的窗口
        SparseArray<Card> map = new SparseArray<>();
        int count = readAllCards(AppsSettings.get().getDatabaseFile(), map);
        Log.i(TAG, "load defualt cdb:" + count);
        if (!TextUtils.isEmpty(exDbPath)) {
            if (AppsSettings.get().isReadExpansions()) {
                File dir = new File(exDbPath);
                if (dir.exists()) {
                    File[] files = dir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            File file = new File(dir, name);
                            return file.isFile() && ((name.endsWith(".cdb") || (name.endsWith(".zip") || name.endsWith(Constants.YPK_FILE_EX))));
                        }
                    });
                    if (files != null) {
                        //按文件名升序，与 getExpansionFiles/makeCdbList 同一基准
                        try {
                            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                        } catch (Exception e) {
                            //
                        }
                        //1.先读卡包压缩包（升序=靠后名包后读覆盖靠前名包）
                        for (File file : files) {
                            if (file.getName().endsWith(".zip") || file.getName().endsWith(Constants.YPK_FILE_EX)) {
                                Log.e("CardManager", "读取压缩包");
                                try {
                                    for (File file1 : readZipCdb(file.getAbsolutePath())) {
                                        count = readAllCards(file1, map);
                                    }
                                } catch (IOException e) {
                                    Log.e("CardManager", "读取压缩包错误" + e);
                                }
                            }
                            Log.i(TAG, "load " + count + " cdb:" + file);
                        }
                        //2.再读卡包物化 cdb 目录（与包内同源，重复读无害；旧版未物化时为空）
                        File packCdbDir = new File(dir, Constants.CORE_PACK_CDB_DIR);
                        File[] packed = packCdbDir.listFiles(f -> f.isFile()
                                && f.getName().toLowerCase().endsWith(".cdb"));
                        if (packed != null) {
                            Arrays.sort(packed, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                            for (File file : packed) {
                                count = readAllCards(file, map);
                                Log.i(TAG, "load " + count + " pack cdb:" + file);
                            }
                        }
                        //3.最后读 expansions 散 cdb（最高优先级，覆盖全部同名卡）
                        for (File file : files) {
                            if (file.getName().endsWith(".cdb")) {
                                count = readAllCards(file, map);
                                Log.i(TAG, "load " + count + " cdb:" + file);
                            }
                        }
                    }
                }
            }
        }
        synchronized (this) {
            cardDataHashMap = map;   // volatile 整表发布（与 getCard 同一把锁，发布后别名计算期间无并发写）
            buildAliasCards();
        }
    }

    private void buildAliasCards() {
        int N = getCount();
        for (int i = 0; i < N; i++) {
            Card c = cardDataHashMap.valueAt(i);
            if (c.Alias == 0) {
                continue;
            }
            //规则同名，或者多图同名
            Card alias = getCard(c.Alias);
            if (alias != null) {
                if (c.isSame(alias)) {
                    //多图同名，它们属性必定是一致
                    c.setRealCode(alias.Code);
                } else if (Math.abs(c.Alias - c.Code) <= 17) {
                    Log.w(TAG, c.Name + ":" + c.Code + " is same card " + c.Alias);
                }
            }
        }
    }

    @WorkerThread
    protected int readAllCards(File file, SparseArray<Card> cardMap) {
        if (!file.exists()) {
            return 0;
        }
        int i = 0;
        Cursor reader = null;
        SQLiteDatabase db = null;
        boolean hasStrs = false;
        try {
            db = openDatabase(file.getPath());
            try {
                reader = db.rawQuery("select datas.id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,\"desc\"" + STR_COLUMNS + " from datas,texts where datas.id = texts.id;", null);
                hasStrs = true;
            } catch (Throwable e) {
                try {
                    reader = db.rawQuery("select datas._id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,\"desc\"" + STR_COLUMNS + " from datas,texts where datas._id = texts._id;", null);
                    hasStrs = true;
                } catch (Throwable e2) {
                    //ignore
                    // 旧版无 str1~str16 的 cdb：退化为原查询，不缓存脚本提示文字
                    try {
                        reader = db.rawQuery("select datas.id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,\"desc\" from datas,texts where datas.id = texts.id;", null);
                    } catch (Throwable e3) {
                        reader = db.rawQuery("select datas._id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,\"desc\" from datas,texts where datas._id = texts._id;", null);
                    }
                }
            }
            if (reader != null && reader.moveToFirst()) {
                do {
                    Card cardData = new Card();
                    cardData.Code = reader.getInt(0);
                    cardData.Ot = reader.getInt(1);
                    cardData.Alias = reader.getInt(2);
                    cardData.SetCode = reader.getLong(3);
                    cardData.Type = reader.getLong(4);
                    int levelInfo = reader.getInt(5);
                    cardData.Level = levelInfo & 0xff;
                    cardData.LeftScale = (levelInfo >> 24) & 0xff;
                    cardData.RightScale = (levelInfo >> 16) & 0xff;
                    cardData.Race = reader.getLong(6);
                    cardData.Attribute = reader.getInt(7);
                    cardData.Attack = reader.getInt(8);
                    cardData.Defense = reader.getInt(9);
                    cardData.Category = reader.getLong(10);
                    cardData.Name = reader.getString(11);
                    cardData.Desc = reader.getString(12);
                    if (hasStrs) {
                        String[] strs = new String[16];
                        boolean any = false;
                        for (int s = 0; s < 16; s++) {
                            String str = reader.getString(13 + s);
                            strs[s] = str;
                            if (!TextUtils.isEmpty(str)) {
                                any = true;
                            }
                        }
                        // 仅在存在非空脚本提示文字时缓存，避免全空卡占用空数组内存
                        cardData.Strs = any ? strs : null;
                    }
                    //put
                    i++;
                    cardMap.put(cardData.Code, cardData);
                } while (reader.moveToNext());
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, "read cards " + file, e);
        } finally {
            IOUtils.close(reader);
            IOUtils.close(db);
        }
        return i;
    }
}

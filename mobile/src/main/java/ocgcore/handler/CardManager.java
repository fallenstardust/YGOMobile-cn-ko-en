package ocgcore.handler;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.MD5Util;
import ocgcore.data.Card;


public class CardManager {
    private String dbDir, exDbPath;
    private final SparseArray<Card> cardDataHashMap = new SparseArray<>();
    private final Map<String, String> mCardCache = new HashMap<>();

    public CardManager(String dbDir, String exPath) {
        this.dbDir = dbDir;
        this.exDbPath = exPath;
    }

    public Card getCard(int code) {
        return cardDataHashMap.get(Integer.valueOf(code));
    }

    public int getCount() {
        return cardDataHashMap.size();
    }

    public SparseArray<Card> getAllCards() {
        return cardDataHashMap;
    }

    @WorkerThread
    public void loadCards() {
        int count = readAllCards(AppsSettings.get().getDataBaseFile(), cardDataHashMap);
        Log.i("Irrlicht", "load defualt cdb:" + count);
        if (AppsSettings.get().isReadExpansions()) {
            File dir = new File(exDbPath);
            if (dir.exists()) {
                File[] files = dir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        File file = new File(dir, name);
                        return file.isFile() && name.endsWith(".cdb");
                    }
                });
                //读取全部卡片
                if (files != null) {
                    Log.i("数量","数量" + files.length);
                    for (File file : files) {
                        final String path = file.getAbsolutePath();
                        String md5 = MD5Util.getFileMD5(path);
                        String last = mCardCache.get(path);
                        if (!TextUtils.equals(md5, last)) {
                            mCardCache.put(path, md5);
                            count = readAllCards(file, cardDataHashMap);
                            Log.i("Irrlicht", "load " + count + " cdb:" + file);
                        }
                    }
                }
            }
        }
    }

    public static boolean checkDataBase(File file) {
        if (!file.exists()) {
            return false;
        }
        Cursor reader = null;
        SQLiteDatabase db = null;
        boolean rs = false;
        try {
            db = SQLiteDatabase.openOrCreateDatabase(file, null);
            reader = db.rawQuery("select datas.id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,desc from datas,texts  where datas.id = texts.id limit 1;", null);
            rs = reader != null;
        } catch (Throwable e) {
            //ignore
        } finally {
            IOUtils.close(reader);
        }
        if (!rs) {
            try {
                reader = db.rawQuery("select datas._id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,desc from datas,texts where datas._id = texts._id  limit 1;", null);
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

    @WorkerThread
    protected int readAllCards(File file, SparseArray<Card> cardMap) {
        if (!file.exists()) {
            return 0;
        }
        int i = 0;
        Cursor reader = null;
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openOrCreateDatabase(file, null);
            if (db.isOpen()) {
                try {
                    reader = db.rawQuery("select datas.id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,desc from datas,texts where datas.id = texts.id;", null);
                } catch (Throwable e) {
                    //ignore
                    reader = db.rawQuery("select datas._id, ot, alias, setcode, type, level, race, attribute, atk, def,category,name,desc from datas,texts where datas._id = texts._id;", null);
                }
                if (reader != null && reader.moveToFirst()) {
                    do {
                        Card cardData = new Card();
                        cardData.Code = reader.getInt(0);
                        cardData.Ot = reader.getInt(1);
                        cardData.Alias = reader.getInt(2);
                        cardData.Setcode = reader.getLong(3);
                        cardData.Type = reader.getLong(4);
                        int levelInfo = reader.getInt(5);
                        cardData.Level = levelInfo & 0xff;
                        cardData.LScale = (levelInfo >> 24) & 0xff;
                        cardData.RScale = (levelInfo >> 16) & 0xff;
                        cardData.Race = reader.getLong(6);
                        cardData.Attribute = reader.getInt(7);
                        cardData.Attack = reader.getInt(8);
                        cardData.Defense = reader.getInt(9);
                        cardData.Category = reader.getLong(10);
                        cardData.Name = reader.getString(11);
                        cardData.Desc = reader.getString(12);
                        //put
                        i++;
                        cardMap.put(cardData.Code, cardData);
                    } while (reader.moveToNext());
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e("Irrlicht", "read cards " + file, e);
        } finally {
            IOUtils.close(reader);
            IOUtils.close(db);
        }
        return i;
    }
}

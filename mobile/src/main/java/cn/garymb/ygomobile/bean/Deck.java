package cn.garymb.ygomobile.bean;

import static cn.garymb.ygomobile.Constants.ARG_DECK;
import static cn.garymb.ygomobile.Constants.NUM_40_LIST;
import static cn.garymb.ygomobile.Constants.QUERY_DECK;
import static cn.garymb.ygomobile.Constants.QUERY_EXTRA_ALL;
import static cn.garymb.ygomobile.Constants.QUERY_MAIN_ALL;
import static cn.garymb.ygomobile.Constants.QUERY_SIDE_ALL;
import static cn.garymb.ygomobile.Constants.QUERY_VERSION;
import static cn.garymb.ygomobile.Constants.QUERY_YDK;
import static cn.garymb.ygomobile.Constants.QUERY_YGO_TYPE;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public class Deck implements Parcelable {
    public static final Creator<Deck> CREATOR = new Creator<Deck>() {
        @Override
        public Deck createFromParcel(Parcel source) {
            return new Deck(source);
        }

        @Override
        public Deck[] newArray(int size) {
            return new Deck[size];
        }
    };
    private static final int YGO_DECK_PROTOCOL_0 = 0;
    private static final int YGO_DECK_PROTOCOL_1 = 1;
    private static final String CARD_DIVIDE_ID = "_";
    private static final String CARD_DIVIDE_NUM = "*";

    private ArrayList<Integer> allList;
    private final ArrayList<Integer> mainlist;
    private final ArrayList<Integer> extraList;
    private final ArrayList<Integer> sideList;
    private String name;
    private boolean isCompleteDeck = true;

    public Deck() {
        mainlist = new ArrayList<>();
        extraList = new ArrayList<>();
        sideList = new ArrayList<>();
        allList = new ArrayList<>();
    }

    public Deck(String name, Uri uri) {
        this(name);
        int version = YGO_DECK_PROTOCOL_0;

        try {
            String ygoType = uri.getQueryParameter(QUERY_YGO_TYPE);
            if (ygoType.equals(ARG_DECK)) {
                version = Integer.parseInt(uri.getQueryParameter(QUERY_VERSION));
            }
        } catch (Exception exception) {
            version = YGO_DECK_PROTOCOL_0;
        }
        String main = null, extra = null, side = null;

        List<String> mList = new ArrayList<>();
        List<String> eList = new ArrayList<>();
        List<String> sList = new ArrayList<>();

        String[] mains, extras, sides;

        switch (version) {
            case YGO_DECK_PROTOCOL_0:
                main = uri.getQueryParameter(QUERY_MAIN_ALL);
                extra = uri.getQueryParameter(QUERY_EXTRA_ALL);
                side = uri.getQueryParameter(QUERY_SIDE_ALL);

                mains = main.split(CARD_DIVIDE_ID);
                mList.addAll(Arrays.asList(mains));

                extras = extra.split(CARD_DIVIDE_ID);
                eList.addAll(Arrays.asList(extras));

                sides = side.split(CARD_DIVIDE_ID);
                sList.addAll(Arrays.asList(sides));

                for (String m : mList) {
                    int[] idNum = toIdAndNum(m, version);
                    if (idNum[0] > 0) {
                        for (int i = 0; i < idNum[1]; i++) {
                            mainlist.add(idNum[0]);
                        }
                    }
                }

                for (String m : eList) {
                    int[] idNum = toIdAndNum(m, version);
                    if (idNum[0] > 0) {
                        for (int i = 0; i < idNum[1]; i++) {
                            extraList.add(idNum[0]);
                        }
                    }
                }

                for (String m : sList) {
                    int[] idNum = toIdAndNum(m, version);
                    if (idNum[0] > 0) {
                        for (int i = 0; i < idNum[1]; i++) {
                            sideList.add(idNum[0]);
                        }
                    }
                }

                break;
            case YGO_DECK_PROTOCOL_1:

                String deck = uri.getQueryParameter(QUERY_DECK);
                deck = deck.replace("-", "+");
                deck = deck.replace("_", "/");
                byte[] bytes = Base64.decode(deck, Base64.NO_WRAP);
                Log.e("Deck", deck.length() + "字符位数" + bytes.length);
                String[] bits = new String[bytes.length * 8];

                for (int i = 0; i < bytes.length; i++) {

                    String b = Integer.toBinaryString(bytes[i]);

                    b = YGOUtil.toNumLength(b, 8);
                    if (b.length() > 8)
                        b = b.substring(b.length() - 8);
                    if (i < 8)
                        Log.e("Deck", b + "  byte：" + bytes[i]);
                    for (int x = 0; x < 8; x++)
                        bits[i * 8 + x] = b.substring(x, x + 1);
                }
                bits = YGOUtil.toNumLength(bits, 16);


                int mNum = Integer.valueOf(YGOUtil.getArrayString(bits, 0, 8), 2);
                int eNum = Integer.valueOf(YGOUtil.getArrayString(bits, 8, 12), 2);
                int sNum = Integer.valueOf(YGOUtil.getArrayString(bits, 12, 16), 2);
                try {
                    FileLogUtil.write("种类数量" + mNum + " " + eNum + " " + sNum + " ");
                    FileLogUtil.write("m：" + YGOUtil.getArrayString(bits, 0, 8));
                    FileLogUtil.write("s：" + YGOUtil.getArrayString(bits, 8, 12));
                    FileLogUtil.write("e：" + YGOUtil.getArrayString(bits, 12, 16));

                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.e("Deck", "种类数量" + mNum + " " + eNum + " " + sNum + " ");
                Log.e("Deck", "m：" + YGOUtil.getArrayString(bits, 0, 8));
                Log.e("Deck", "e：" + YGOUtil.getArrayString(bits, 8, 12));
                Log.e("Deck", "s：" + YGOUtil.getArrayString(bits, 12, 16));
                if (bits.length < (16 + (mNum * 29))) {
                    mNum = (bits.length - 16) / 29;
                    isCompleteDeck = false;
                }
                for (int i = 0; i < mNum; i++) {
                    int cStart = 16 + (i * 29);
                    int cardNum = Integer.valueOf(YGOUtil.getArrayString(bits, cStart, cStart + 2), 2);
                    int cardId = Integer.valueOf(YGOUtil.getArrayString(bits, cStart + 2, cStart + 29), 2);
                    for (int x = 0; x < cardNum; x++) {
                        mainlist.add(cardId);
                    }
                }
                if (!isCompleteDeck)
                    return;
                if (bits.length < (16 + mNum * 29 + (eNum * 29))) {
                    eNum = (bits.length - 16 - (mNum * 29)) / 29;
                    isCompleteDeck = false;
                }
                for (int i = 0; i < eNum; i++) {
                    int cStart = 16 + mNum * 29 + (i * 29);
                    int cardNum = Integer.valueOf(YGOUtil.getArrayString(bits, cStart, cStart + 2), 2);
                    Log.e("DeckSetting", eNum + " 当前 " + i + "  " + cStart);
                    int cardId = Integer.valueOf(YGOUtil.getArrayString(bits, cStart + 2, cStart + 29), 2);
                    for (int x = 0; x < cardNum; x++) {
                        extraList.add(cardId);
                    }
                }

                if (!isCompleteDeck)
                    return;
                if (bits.length < (16 + mNum * 29 + (eNum * 29) + (sNum * 29))) {
                    sNum = (bits.length - 16 - (mNum * 29) - (eNum * 29)) / 29;
                    isCompleteDeck = false;
                }
                for (int i = 0; i < sNum; i++) {
                    int cStart = 16 + mNum * 29 + eNum * 29 + (i * 29);
                    int cardNum = Integer.valueOf(YGOUtil.getArrayString(bits, cStart, cStart + 2), 2);
                    int cardId = Integer.valueOf(YGOUtil.getArrayString(bits, cStart + 2, cStart + 29), 2);
                    for (int x = 0; x < cardNum; x++) {
                        sideList.add(cardId);
                    }
                }
                break;
        }


    }

    public Deck(Uri uri) {
        this(uri.getQueryParameter(QUERY_YDK), uri);
    }


    public Deck(String name) {
        this();
        this.name = name;
    }


    protected Deck(Parcel in) {
        this.name = in.readString();
        this.mainlist = new ArrayList<Integer>();
        in.readList(this.mainlist, Integer.class.getClassLoader());
        this.extraList = new ArrayList<Integer>();
        in.readList(this.extraList, Integer.class.getClassLoader());
        this.sideList = new ArrayList<Integer>();
        in.readList(this.sideList, Integer.class.getClassLoader());
    }

    private int[] toIdAndNum(String m, int protocol) {
        //元素0为卡密，元素1为卡片数量
        int[] idNum;

        switch (protocol) {
            case YGO_DECK_PROTOCOL_0:
                idNum = toIdAndNum0(m);
                break;
            default:
                idNum = toIdAndNum0(m);
                break;
        }
        return idNum;
    }

    public boolean isCompleteDeck() {
        return isCompleteDeck;
    }

    private int[] toIdAndNum0(String m) {
        //元素0为卡密，元素1为卡片数量
        int[] idNum = {0, 1};
        if (m.contains(CARD_DIVIDE_NUM)) {
            try {
                idNum[1] = Integer.parseInt(m.substring(m.length() - 1));
            } catch (Exception e) {

            }
            idNum[0] = toId(m.substring(0, m.length() - 2), YGO_DECK_PROTOCOL_0);
        } else {
            idNum[0] = toId(m, YGO_DECK_PROTOCOL_0);
        }
        return idNum;
    }

    public Uri toAppUri() {
        return toUri(Constants.SCHEME_APP);
    }

    public Uri toHttpUri() {
        return toUri(Constants.SCHEME_HTTP);
    }

    public Uri toUri(String host) {
        Uri.Builder uri = Uri.parse(host + "://")
                .buildUpon()
                .authority(Constants.URI_HOST);
        //.path(Constants.PATH_DECK);
        if (!TextUtils.isEmpty(name)) {
            uri.appendQueryParameter(Constants.QUERY_NAME, name);
        }
        uri.appendQueryParameter(QUERY_YGO_TYPE, ARG_DECK);
        uri.appendQueryParameter(Constants.QUERY_VERSION, "1");

//        if (mainlist.size() != 0)
//            deck+=toString(mainlist);
//        if (extraList.size() != 0)
//            deck+=toString(extraList);
//        if (sideList.size() != 0)
//            deck+=toString(sideList);
        int mNum = getTypeNum(mainlist);
        int eNum = getTypeNum(extraList);
        int sNum = getTypeNum(sideList);

        String deck = toBit(mainlist, extraList, sideList, mNum, eNum, sNum);

        String m = Integer.toBinaryString(mNum);
        String e = Integer.toBinaryString(eNum);
        String s = Integer.toBinaryString(sNum);

        m = YGOUtil.toNumLength(m, 8);
        e = YGOUtil.toNumLength(e, 4);
        s = YGOUtil.toNumLength(s, 4);

        Log.e("Deck", "分享数量" + mNum + " " + eNum + "  " + sNum);

        deck = m + e + s + deck;
        byte[] bytes = YGOUtil.toBytes(deck);
        String message = Base64.encodeToString(bytes, Base64.NO_WRAP);
        Log.e("Deck", message.length() + " 转换时位数 " + bytes.length);
        message = message.replace("+", "-");
        message = message.replace("/", "_");
        message = message.replace("=", "");
        Log.e("Deck", "转换后数据" + message);
        for (int i = 0; i < 8; i++) {

        }
        uri.appendQueryParameter(QUERY_DECK, message);

        return uri.build();
    }

    private String toBit(ArrayList<Integer> mainlist, ArrayList<Integer> extraList, ArrayList<Integer> sideList, int mNum, int eNum, int sNum) {
        String mains = tobyte(mainlist, mNum);
        String extras = tobyte(extraList, eNum);
        String sides = tobyte(sideList, sNum);
        String message = mains + extras + sides;
        return message;
    }

    public int getTypeNum(List<Integer> list) {
        int num = 0;
        for (int i = 0; i < list.size(); i++) {
            Integer id = list.get(i);
            if (id > 0) {
                num++;
                //如果是最后一张就不用对比下张卡
                if (i != list.size() - 1) {
                    int id1 = list.get(i + 1);
                    //如果下张是同名卡
                    if (id1 == id) {
                        //如果是倒数第二张就不用对比下下张卡
                        if (i != list.size() - 2) {
                            id1 = list.get(i + 2);
                            //如果下下张是同名卡
                            if (id1 == id) {
                                i++;
                            }
                        }
                        i++;
                    }
                }
            }
        }
        return num;
    }

    private String tobyte(List<Integer> ids, int typeNum) {
        String bytes = "";
        for (int i = 0; i < ids.size(); i++) {
            Integer id = ids.get(i);
            if (id > 0) {
                //转换为29位二进制码
                String idB = toB(id);
                //如果是最后一张就不用对比下张卡
                if (i != ids.size() - 1) {
                    int id1 = ids.get(i + 1);
                    //同名卡张数
                    int tNum = 1;
                    //如果下张是同名卡
                    if (id1 == id) {
                        tNum++;
                        //如果是倒数第二张就不用对比下下张卡
                        if (i != ids.size() - 2) {
                            id1 = ids.get(i + 2);
                            //如果下下张是同名卡
                            if (id1 == id) {
                                tNum++;
                                i++;
                            }
                        }
                        i++;
                    }
                    tNum = Math.min(3, tNum);
                    switch (tNum) {
                        case 1:
                            idB = "01" + idB;
                            break;
                        case 2:
                            idB = "10" + idB;
                            break;
                        case 3:
                            idB = "11" + idB;
                            break;
                    }
                } else {
                    idB = "01" + idB;
                }
                bytes += idB;
            }
        }
        return bytes;
    }


    private String toB(int id) {
        return YGOUtil.toNumLength(Integer.toBinaryString(id), 27);
    }

    //压缩卡密,目前直接转换为40进制
    private String compressedId(int id) {
        StringBuilder compressedId1 = new StringBuilder();

        while (id > 40) {
            compressedId1.insert(0, NUM_40_LIST[id % 40]);
            id /= 40;
        }
        compressedId1.insert(0, NUM_40_LIST[id]);
        if (compressedId1.length() < 5)
            for (int i = compressedId1.length(); i < 5; i++)
                compressedId1.insert(0, "0");
        return compressedId1.toString();
    }


    private int getCardIdUnCompressedId(String compressedNum) {
        for (int i = 0; i < NUM_40_LIST.length; i++) {
            if (compressedNum.equals(NUM_40_LIST[i]))
                return i;
        }
        return 0;
    }

    //解析卡密，目前直接16进制转换为10进制
    private int unId(String id) {
        int num = 0;
        id = id.trim();
        String[] sList = new String[id.length()];
        for (int i = 0; i < id.length(); i++) {
            sList[i] = id.charAt(i) + "";
        }

        for (int i = sList.length - 1; i >= 0; i--)
            num += (getCardIdUnCompressedId(sList[i]) * Math.pow(40, sList.length - 1 - i));
//        Log.e("DeckUU",(getCardIdUnCompressedId(sList[0]) * Math.pow(40, sList.length-1)+"   "+sList.length+"   num"+num));
        return num;
    }

    public String getName() {
        return name;
    }

    public int getMainCount() {
        return mainlist.size();
    }

    public int getExtraCount() {
        return extraList.size();
    }

    public int getSideCount() {
        return sideList.size();
    }

    public int getDeckCount() {
        return getMainCount() + getExtraCount() + getSideCount();
    }

    public File saveTemp(String dir) {
        if (TextUtils.isEmpty(name)) {
            name = "__noname.ydk";
        }
        if (!name.endsWith(YDK_FILE_EX)) {
            name += YDK_FILE_EX;
        }
        File file = new File(dir, name);
        DeckUtils.save(this, file);
        return file;
    }

    private int toId(String str, int version) {
        if (TextUtils.isEmpty(str)) return 0;
        try {
            switch (version) {
                case YGO_DECK_PROTOCOL_0:
                    return Integer.parseInt(str);
                case YGO_DECK_PROTOCOL_1:
                    //如果需要返回40进制码：
                    return unId(str);
                default:
                    return Integer.parseInt(str);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Integer> getSideList() {
        return sideList;
    }

    public List<Integer> getMainlist() {
        return mainlist;
    }

    public List<Integer> getExtraList() {
        return extraList;
    }

    public List<Integer> getAlllist() {
        if (allList.size() == 0) {
            allList.addAll(mainlist);
            allList.addAll(extraList);
            allList.addAll(sideList);
        }
        return allList;
    }

    public void addMain(Integer id) {
        if (mainlist.size() >= Constants.DECK_MAIN_MAX) {
            return;
        }
        mainlist.add(id);
    }

    public void addExtra(Integer id) {
        if (extraList.size() >= Constants.DECK_EXTRA_MAX) {
            return;
        }
        extraList.add(id);
    }

    public void addSide(Integer id) {
        if (sideList.size() >= Constants.DECK_SIDE_MAX) {
            return;
        }
        sideList.add(id);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeList(this.mainlist);
        dest.writeList(this.extraList);
        dest.writeList(this.sideList);
    }
}

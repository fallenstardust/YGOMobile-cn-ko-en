package cn.garymb.ygomobile.bean;

import static cn.garymb.ygomobile.Constants.ARG_DECK;
import static cn.garymb.ygomobile.Constants.QUERY_EXTRA;
import static cn.garymb.ygomobile.Constants.QUERY_EXTRA_ALL;
import static cn.garymb.ygomobile.Constants.QUERY_MAIN;
import static cn.garymb.ygomobile.Constants.QUERY_MAIN_ALL;
import static cn.garymb.ygomobile.Constants.QUERY_SIDE;
import static cn.garymb.ygomobile.Constants.QUERY_SIDE_ALL;
import static cn.garymb.ygomobile.Constants.QUERY_VERSION;
import static cn.garymb.ygomobile.Constants.QUERY_YDK;
import static cn.garymb.ygomobile.Constants.QUERY_YGO_TYPE;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;

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
    private static final int YGO_PROTOCOL_0 = 0;
    private static final int YGO_PROTOCOL_1 = 1;
    private static final String CARD_DIVIDE_ID = "_";
    private static final String CARD_DIVIDE_NUM = "*";
    private static final String CARD_NUM_2 = "-";
    private static final String CARD_NUM_3 = "!";
    private static List<CardIdNum> cardIdNumList;

    static {
        cardIdNumList = new ArrayList<>();
        cardIdNumList.add(new CardIdNum("10", "a"));
        cardIdNumList.add(new CardIdNum("11", "b"));
        cardIdNumList.add(new CardIdNum("12", "c"));
        cardIdNumList.add(new CardIdNum("13", "d"));
        cardIdNumList.add(new CardIdNum("14", "e"));
        cardIdNumList.add(new CardIdNum("15", "f"));
        cardIdNumList.add(new CardIdNum("16", "g"));
        cardIdNumList.add(new CardIdNum("17", "h"));
        cardIdNumList.add(new CardIdNum("18", "i"));
        cardIdNumList.add(new CardIdNum("19", "j"));
        cardIdNumList.add(new CardIdNum("20", "k"));
        cardIdNumList.add(new CardIdNum("21", "l"));
        cardIdNumList.add(new CardIdNum("22", "m"));
        cardIdNumList.add(new CardIdNum("23", "n"));
        cardIdNumList.add(new CardIdNum("24", "o"));
        cardIdNumList.add(new CardIdNum("25", "p"));
        cardIdNumList.add(new CardIdNum("26", "q"));
        cardIdNumList.add(new CardIdNum("27", "r"));
        cardIdNumList.add(new CardIdNum("28", "s"));
        cardIdNumList.add(new CardIdNum("29", "t"));
        cardIdNumList.add(new CardIdNum("30", "u"));
        cardIdNumList.add(new CardIdNum("31", "v"));
        cardIdNumList.add(new CardIdNum("32", "w"));
        cardIdNumList.add(new CardIdNum("33", "x"));
        cardIdNumList.add(new CardIdNum("34", "y"));
        cardIdNumList.add(new CardIdNum("35", "z"));
        cardIdNumList.add(new CardIdNum("36", "A"));
        cardIdNumList.add(new CardIdNum("37", "B"));
        cardIdNumList.add(new CardIdNum("38", "C"));
        cardIdNumList.add(new CardIdNum("39", "D"));
        cardIdNumList.add(new CardIdNum("40", "E"));
        cardIdNumList.add(new CardIdNum("41", "F"));
        cardIdNumList.add(new CardIdNum("42", "G"));
        cardIdNumList.add(new CardIdNum("43", "H"));
        cardIdNumList.add(new CardIdNum("44", "I"));
        cardIdNumList.add(new CardIdNum("45", "J"));
        cardIdNumList.add(new CardIdNum("46", "K"));
        cardIdNumList.add(new CardIdNum("47", "L"));
        cardIdNumList.add(new CardIdNum("48", "M"));
        cardIdNumList.add(new CardIdNum("49", "N"));
        cardIdNumList.add(new CardIdNum("50", "O"));
        cardIdNumList.add(new CardIdNum("51", "P"));
        cardIdNumList.add(new CardIdNum("52", "Q"));
        cardIdNumList.add(new CardIdNum("53", "R"));
        cardIdNumList.add(new CardIdNum("54", "S"));
        cardIdNumList.add(new CardIdNum("55", "T"));
        cardIdNumList.add(new CardIdNum("56", "U"));
        cardIdNumList.add(new CardIdNum("57", "V"));
        cardIdNumList.add(new CardIdNum("58", "W"));
        cardIdNumList.add(new CardIdNum("59", "X"));
        cardIdNumList.add(new CardIdNum("60", "Y"));
        cardIdNumList.add(new CardIdNum("61", "Z"));
    }

    private final ArrayList<Integer> mainlist;
    private final ArrayList<Integer> extraList;
    private final ArrayList<Integer> sideList;
    private String name;

    public Deck() {
        mainlist = new ArrayList<>();
        extraList = new ArrayList<>();
        sideList = new ArrayList<>();
    }

    public Deck(String name, Uri uri) {
        this(name);
        int version = YGO_PROTOCOL_0;

        try {
            String ygoType = uri.getQueryParameter(QUERY_YGO_TYPE);
            if (ygoType.equals(ARG_DECK)) {
                version = Integer.parseInt(uri.getQueryParameter(QUERY_VERSION));
            }
        } catch (Exception exception) {
            version = YGO_PROTOCOL_0;
        }
        String main=null, extra=null, side=null;
        switch (version) {
            case YGO_PROTOCOL_0:
                try {
                    main = uri.getQueryParameter(QUERY_MAIN_ALL);
                }catch (Exception e){}

                try {
                    extra = uri.getQueryParameter(QUERY_EXTRA_ALL);
                }catch (Exception e){}

                try {
                    side = uri.getQueryParameter(QUERY_SIDE_ALL);
                }catch (Exception e){}

                break;
            case YGO_PROTOCOL_1:
                try {
                    main = uri.getQueryParameter(QUERY_MAIN);
                }catch (Exception e){}

                try {
                    extra = uri.getQueryParameter(QUERY_EXTRA);
                }catch (Exception e){}

                try {
                    side = uri.getQueryParameter(QUERY_SIDE);
                }catch (Exception e){}
                break;
            default:
                try {
                    main = uri.getQueryParameter(QUERY_MAIN_ALL);
                }catch (Exception e){}

                try {
                    extra = uri.getQueryParameter(QUERY_EXTRA_ALL);
                }catch (Exception e){}

                try {
                    side = uri.getQueryParameter(QUERY_SIDE_ALL);
                }catch (Exception e){}
        }

        if (!TextUtils.isEmpty(main)) {
            String[] mains = main.split(CARD_DIVIDE_ID);
            for (String m : mains) {
                int[] idNum = toIdAndNum(m, version);
                if (idNum[0] > 0) {
                    for (int i = 0; i < idNum[1]; i++) {
                        mainlist.add(idNum[0]);
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(extra)) {
            String[] extras = extra.split(CARD_DIVIDE_ID);
            for (String m : extras) {
                int[] idNum = toIdAndNum(m, version);
                if (idNum[0] > 0) {
                    for (int i = 0; i < idNum[1]; i++) {
                        extraList.add(idNum[0]);
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(side)) {
            String[] sides = side.split(CARD_DIVIDE_ID);
            for (String m : sides) {
                int[] idNum = toIdAndNum(m, version);
                if (idNum[0] > 0) {
                    for (int i = 0; i < idNum[1]; i++) {
                        sideList.add(idNum[0]);
                    }
                }
            }
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
            case YGO_PROTOCOL_0:
                idNum=toIdAndNum0(m);
                break;
            case YGO_PROTOCOL_1:
                idNum=toIdAndNum1(m);
                break;
            default:
                idNum=toIdAndNum0(m);
                break;
        }
        return idNum;
    }

    private int[] toIdAndNum1(String m) {
        //元素0为卡密，元素1为卡片数量
        int[] idNum = {0, 1};
        if (m.contains(CARD_NUM_2)) {
            idNum[0] = toId(m.substring(0, m.length() - 1));
            idNum[1] = 2;
        } else if (m.contains(CARD_NUM_3)) {
            idNum[0] = toId(m.substring(0, m.length() - 1));
            idNum[1] = 3;
        } else {
            idNum[0] = toId(m);
        }

        return idNum;
    }

    private int[] toIdAndNum0(String m) {
        //元素0为卡密，元素1为卡片数量
        int[] idNum = {0, 1};
        if (m.contains(CARD_DIVIDE_NUM)) {
            try {
                idNum[1] = Integer.parseInt(m.substring(m.length() - 1));
            } catch (Exception e) {

            }
            idNum[0] = toId(m.substring(0, m.length() - 2));
        } else {
            idNum[0] = toId(m);
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
        if (mainlist.size() != 0)
            uri.appendQueryParameter(Constants.QUERY_MAIN, toString(mainlist));
        if (extraList.size() != 0)
            uri.appendQueryParameter(Constants.QUERY_EXTRA, toString(extraList));
        if (sideList.size() != 0)
            uri.appendQueryParameter(Constants.QUERY_SIDE, toString(sideList));
        return uri.build();
    }

    private String toString(List<Integer> ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            Integer id = ids.get(i);
            if (i > 0) {
                builder.append(CARD_DIVIDE_ID);
            }
            if (id > 0) {
                //如果需要使用十六进制码：
                builder.append(compressedId(id));
//                builder.append(id);
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
                    //如果有同名卡
                    if (tNum > 1) {
                        if (tNum == 2) {
                            builder.append(CARD_NUM_2);
                        } else {
                            builder.append(CARD_NUM_3);
                        }

                    }
                }
            }
        }
        return builder.toString();
    }

    //压缩卡密,目前直接转换为16进制
    private String compressedId(int id) {
        StringBuilder compressedId1 = new StringBuilder();
        StringBuilder compressedId2 = new StringBuilder();
        String ids = id + "";
        while (ids.startsWith("0")) {
            ids = ids.substring(1);
        }
        int lenght = ids.length();


        for (int i = 0; i < lenght / 2; i++) {
            int start = i * 2;
            int end = Math.min(start + 2, lenght);
            compressedId1.append(getCardIdCompressedId(ids.substring(start, end)));
        }

        int currentPosition = 0;
        while (currentPosition < lenght) {
            int start = currentPosition;
            int end = Math.min(start + 2, lenght);
            String message = ids.substring(start, end);
            String result = getCardIdCompressedId(message);
            if (message.equals(result)) {
                compressedId2.append(ids.charAt(start));
                currentPosition++;
            } else {
                compressedId2.append(result);
                currentPosition = currentPosition + 2;
            }
        }


        return compressedId2.length() < compressedId1.length() ? compressedId2.toString() : compressedId1.toString();
    }

    private String getCardIdCompressedId(String idNum) {
        for (CardIdNum cardIdNum : cardIdNumList) {
            if (cardIdNum.getCardIdNum().equals(idNum)) {
                return cardIdNum.getCardIdNumCompressed();
            }
        }
        return idNum;
    }

    private String getCardIdUnCompressedId(String compressedNum) {
        for (CardIdNum cardIdNum : cardIdNumList) {
            if (cardIdNum.getCardIdNumCompressed().equals(compressedNum)) {
                return cardIdNum.getCardIdNum();
            }
        }
        return compressedNum;
    }

    //解析卡密，目前直接16进制转换为10进制
    private int unId(String id) {
        StringBuilder compressedId = new StringBuilder();
        String[] sList = id.split("");
        for (String s : sList)
            compressedId.append(getCardIdUnCompressedId(s));
        int cardId;
        try {
            cardId = Integer.parseInt(compressedId.toString());
        } catch (Exception e) {
            cardId = 0;
        }

        return cardId;
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
        return getMainCount() + getExtraCount();
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

    private int toId(String str) {
        if (TextUtils.isEmpty(str)) return 0;
        try {
            //如果需要返回16进制码：
            return unId(str);
//            return Integer.parseInt(str);
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

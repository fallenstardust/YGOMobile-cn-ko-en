package cn.garymb.ygomobile.bean;

import static cn.garymb.ygomobile.Constants.ARG_DECK;
import static cn.garymb.ygomobile.Constants.NUM_40_LIST;
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
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
        String main = null, extra = null, side = null;

        List<String> mList = new ArrayList<>();
        List<String> eList = new ArrayList<>();
        List<String> sList = new ArrayList<>();

        switch (version) {
            case YGO_PROTOCOL_0:
                try {
                    main = uri.getQueryParameter(QUERY_MAIN_ALL);
                    String[] mains = main.split(CARD_DIVIDE_ID);
                    mList.addAll(Arrays.asList(mains));
                } catch (Exception ignored) {
                }

                try {
                    extra = uri.getQueryParameter(QUERY_EXTRA_ALL);
                    String[] extras = extra.split(CARD_DIVIDE_ID);
                    eList.addAll(Arrays.asList(extras));
                } catch (Exception ignored) {
                }

                try {
                    side = uri.getQueryParameter(QUERY_SIDE_ALL);
                    String[] sides = side.split(CARD_DIVIDE_ID);
                    sList.addAll(Arrays.asList(sides));
                } catch (Exception ignored) {
                }


                break;
            case YGO_PROTOCOL_1:
                try {
                    main = uri.getQueryParameter(QUERY_MAIN);
                    for (int i = 0; i < main.length() / 6; i++)
                        mList.add(main.substring(i * 6, i * 6 + 6));

                } catch (Exception e) {
                }

                try {
                    extra = uri.getQueryParameter(QUERY_EXTRA);
                    for (int i = 0; i < extra.length() / 6; i++)
                        eList.add(extra.substring(i * 6, i * 6 + 6));
                } catch (Exception e) {
                }

                try {
                    side = uri.getQueryParameter(QUERY_SIDE);
                    for (int i = 0; i < side.length() / 6; i++)
                        sList.add(side.substring(i * 6, i * 6 + 6));
                } catch (Exception e) {
                }


                break;
            default:
                try {
                    main = uri.getQueryParameter(QUERY_MAIN_ALL);
                    String[] mains = main.split(CARD_DIVIDE_ID);
                    mList.addAll(Arrays.asList(mains));
                } catch (Exception ignored) {
                }

                try {
                    extra = uri.getQueryParameter(QUERY_EXTRA_ALL);
                    String[] extras = extra.split(CARD_DIVIDE_ID);
                    eList.addAll(Arrays.asList(extras));
                } catch (Exception ignored) {
                }

                try {
                    side = uri.getQueryParameter(QUERY_SIDE_ALL);
                    String[] sides = side.split(CARD_DIVIDE_ID);
                    sList.addAll(Arrays.asList(sides));
                } catch (Exception ignored) {
                }
        }

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
                idNum = toIdAndNum0(m);
                break;
            case YGO_PROTOCOL_1:
                idNum = toIdAndNum1(m);
                break;
            default:
                idNum = toIdAndNum0(m);
                break;
        }
        return idNum;
    }

    private int[] toIdAndNum1(String m) {
        //元素0为卡密，元素1为卡片数量
        int[] idNum = {0, 1};
        idNum[0] = toId(m.substring(0, m.length() - 1));
        idNum[1] = Integer.parseInt(m.substring(m.length() - 1));
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
//            if (i > 0) {
//                builder.append(CARD_DIVIDE_ID);
//            }
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
                    tNum = Math.min(3, tNum);
                    builder.append(tNum);
                } else {
                    builder.append(1);
                }
            }
        }
        return builder.toString();
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

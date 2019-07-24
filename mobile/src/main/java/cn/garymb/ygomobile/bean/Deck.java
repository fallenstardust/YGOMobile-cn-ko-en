package cn.garymb.ygomobile.bean;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;

import static cn.garymb.ygomobile.Constants.QUERY_EXTRA;
import static cn.garymb.ygomobile.Constants.QUERY_MAIN;
import static cn.garymb.ygomobile.Constants.QUERY_SIDE;
import static cn.garymb.ygomobile.Constants.QUERY_YDK;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

public class Deck implements Parcelable {
    private String name;
    private final ArrayList<Integer> mainlist;
    private final ArrayList<Integer> extraList;
    private final ArrayList<Integer> sideList;

    public Deck() {
        mainlist = new ArrayList<>();
        extraList = new ArrayList<>();
        sideList = new ArrayList<>();
    }

    public Deck(String name, Uri uri) {
        this(name);
        String main = uri.getQueryParameter(QUERY_MAIN);
        String extra = uri.getQueryParameter(QUERY_EXTRA);
        String side = uri.getQueryParameter(QUERY_SIDE);
        if (!TextUtils.isEmpty(main)) {
            String[] mains = main.split("'");
            for (String m : mains) {
                int []idNum=toIdAndNum(m);
                if (idNum[0] > 0) {
                    for (int i=0;i<idNum[1];i++){
                        mainlist.add(idNum[0]);
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(extra)) {
            String[] extras = extra.split("'");
            for (String m : extras) {
                int []idNum=toIdAndNum(m);
                if (idNum[0] > 0) {
                    for (int i=0;i<idNum[1];i++){
                        extraList.add(idNum[0]);
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(side)) {
            String[] sides = side.split("'");
            for (String m : sides) {
                int []idNum=toIdAndNum(m);
                if (idNum[0] > 0) {
                    for (int i=0;i<idNum[1];i++){
                        sideList.add(idNum[0]);
                    }
                }
            }
        }
    }

    private int[] toIdAndNum(String m) {
        int[] idNum={0,1};
        if (m.contains("*")){
            try{
                idNum[1]=Integer.parseInt(m.substring(m.length()-1));
            }catch (Exception e){

            }
            idNum[0]=toId(m.substring(0,m.length()-2));
        }else {
            idNum[0]=toId(m);
        }

        return idNum;
    }

    public Deck(Uri uri) {
        this(uri.getQueryParameter(QUERY_YDK), uri);
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
        uri.appendQueryParameter(Constants.QUERY_MAIN, toString(mainlist))
                .appendQueryParameter(Constants.QUERY_EXTRA, toString(extraList))
                .appendQueryParameter(Constants.QUERY_SIDE, toString(sideList));
        return uri.build();
    }

    private String toString(List<Integer> ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            Integer id = ids.get(i);
            if (i > 0) {
                builder.append("'");
            }
            if (id > 0) {
                //如果需要使用十六进制码：builder.append(compressedId(id));
                builder.append(id);
                //如果是最后一张就不用对比下张卡
                if(i!=ids.size()-1) {
                    int id1 = ids.get(i + 1);
                    //同名卡张数
                    int tNum = 1;
                    //如果下张是同名卡
                    if (id1 == id) {
                        tNum++;
                        //如果是倒数第二张就不用对比下下张卡
                        if(i!=ids.size()-2) {
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
                        builder.append("*" + tNum);
                    }
                }
            }
        }
        return builder.toString();
    }

    //压缩卡密,目前直接转换为16进制
    private String compressedId(int id){
        return Integer.toHexString(id);
    }

    //解析卡密，目前直接16进制转换为10进制
    private int unId(String id){
        return Integer.parseInt(id,16);
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
            //如果需要返回16进制码：return unId(str)
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 0;
        }
    }

    public Deck(String name) {
        this();
        this.name = name;
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

    protected Deck(Parcel in) {
        this.name = in.readString();
        this.mainlist = new ArrayList<Integer>();
        in.readList(this.mainlist, Integer.class.getClassLoader());
        this.extraList = new ArrayList<Integer>();
        in.readList(this.extraList, Integer.class.getClassLoader());
        this.sideList = new ArrayList<Integer>();
        in.readList(this.sideList, Integer.class.getClassLoader());
    }

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
}

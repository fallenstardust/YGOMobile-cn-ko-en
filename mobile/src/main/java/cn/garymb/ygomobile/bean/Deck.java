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

    public Deck(Uri uri) {
        this(uri.getQueryParameter(QUERY_YDK));
        String main = uri.getQueryParameter(QUERY_MAIN);
        String extra = uri.getQueryParameter(QUERY_EXTRA);
        String side = uri.getQueryParameter(QUERY_SIDE);
        if (!TextUtils.isEmpty(main)) {
            String[] mains = main.split(",");
            for (String m : mains) {
                int id = toId(m);
                if (id > 0) {
                    mainlist.add(id);
                }
            }
        }
        if (!TextUtils.isEmpty(extra)) {
            String[] extras = extra.split(",");
            for (String m : extras) {
                int id = toId(m);
                if (id > 0) {
                    extraList.add(id);
                }
            }
        }
        if (!TextUtils.isEmpty(side)) {
            String[] sides = side.split(",");
            for (String m : sides) {
                int id = toId(m);
                if (id > 0) {
                    sideList.add(id);
                }
            }
        }
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
                .authority(Constants.URI_HOST)
                .path(Constants.PATH_DECK);
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
        int i = 0;
        for (Integer id : ids) {
            if (i > 0) {
                builder.append(",");
            }
            if (id > 0) {
                builder.append(id);
                i++;
            }
        }
        return builder.toString();
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
        if(mainlist.size()>=Constants.DECK_MAIN_MAX){
            return;
        }
        mainlist.add(id);
    }

    public void addExtra(Integer id) {
        if(extraList.size()>=Constants.DECK_EXTRA_MAX){
            return;
        }
        extraList.add(id);
    }

    public void addSide(Integer id) {
        if(sideList.size()>=Constants.DECK_SIDE_MAX){
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

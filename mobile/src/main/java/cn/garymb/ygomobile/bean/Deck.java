package cn.garymb.ygomobile.bean;

import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.ourygo.lib.duelassistant.util.YGODAUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
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


    private ArrayList<Integer> allList;
    private final ArrayList<Integer> mainlist;
    private final ArrayList<Integer> extraList;
    private final ArrayList<Integer> sideList;
    private String name;
    /**
     * 是否是完整卡组，部分卡组码可能保存出来是不完整的卡组
     */
    private boolean isCompleteDeck = true;

    /**
     * 卡组广场用的卡组id，可以为空
     */
    public String deckId = null;

    public Deck() {
        mainlist = new ArrayList<>();
        extraList = new ArrayList<>();
        sideList = new ArrayList<>();
        allList = new ArrayList<>();
    }

    public Deck(String name, List<Integer> mainList, List<Integer> exList, List<Integer> sideList) {
        this(name);
        this.mainlist.addAll(mainList);
        this.extraList.addAll(exList);
        this.sideList.addAll(sideList);
    }

    public Deck(Uri uri, List<Integer> mainList, List<Integer> exList, List<Integer> sideList) {
        this(TextUtils.isEmpty(uri.getQueryParameter(Constants.QUERY_NAME))
                ? App.get().getString(R.string.rename_deck) + System.currentTimeMillis()
                : uri.getQueryParameter(Constants.QUERY_NAME), mainList, exList, sideList);
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

    public Uri toUri() {
        Map<String,String> map=new HashMap<>();
        String deckName = name;
        int ydkIndex = deckName.indexOf(YDK_FILE_EX);
        if (ydkIndex != -1) {
            deckName = deckName.substring(0, ydkIndex);
        }
        map.put(Constants.QUERY_NAME, deckName);
        return YGODAUtil.toUri(mainlist,extraList,sideList,map);
    }

    public void setCompleteDeck(boolean completeDeck) {
        isCompleteDeck = completeDeck;
    }

    public boolean isCompleteDeck() {
        return isCompleteDeck;
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

    public void addPack(Integer id) {
        mainlist.add(id);
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

    public void setId(String id) {
        deckId = id;
    }
}

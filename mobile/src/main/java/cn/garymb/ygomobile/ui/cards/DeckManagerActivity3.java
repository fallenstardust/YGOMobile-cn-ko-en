package cn.garymb.ygomobile.ui.cards;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.OnItemDragListener;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.DeckLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.deck2.DeckAdapter;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;

public class DeckManagerActivity3 extends BaseActivity implements OnItemDragListener, CardLoader.CallBack {
    private RecyclerView mRecyclerView;
    private DeckAdapter mDeckAdapter;
    protected StringManager mStringManager = DataManager.get().getStringManager();
    protected LimitManager mLimitManager = DataManager.get().getLimitManager();
    protected CardLoader mCardLoader;
    private AppsSettings mSettings = AppsSettings.get();
    private String mPreLoad;
    private File mYdkFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck_cards3);
        Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        enableBackHome();
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        mDeckAdapter = new DeckAdapter(this, mRecyclerView, this);
        mRecyclerView.setAdapter(mDeckAdapter);
        findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDeckAdapter.notifyDataSetChanged();
            }
        });

        mCardLoader = new CardLoader(this);
        mCardLoader.setCallBack(this);

        DialogPlus dlg = DialogPlus.show(this, null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            mCardLoader.setLimitList(mLimitManager.getTopLimit());
            File file = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH + "/" + mSettings.getLastDeck() + Constants.YDK_FILE_EX);
            if (!TextUtils.isEmpty(mPreLoad)) {
                file = new File(mPreLoad);
                mPreLoad = null;
            }
            if (!file.exists()) {
                //当默认卡组不存在的时候
                File[] files = getYdkFiles();
                if (files != null && files.length > 0) {
                    file = files[0];
                }
            }
            //EXTRA_DECK
            if (file == null) {
                return new DeckInfo();
            }
            mYdkFile = file;
            if (mCardLoader.isOpen() && file.exists()) {
                return DeckLoader.readDeck(mCardLoader, file, mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            dlg.dismiss();
            mDeckAdapter.setDeckInfo(rs);
            mDeckAdapter.notifyDataSetChanged();
        });
    }

    private File[] getYdkFiles() {
        File dir = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> {
            return s.toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX);
        });
        return files;
    }

    @Override
    public void onDragStart() {

    }

    @Override
    public void onDragLongPress(int pos) {
        Toast.makeText(this, "on long press :" + pos, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (getRequestedOrientation() != newConfig.orientation) {
            mDeckAdapter.notifyDataSetChanged();
        }
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDragLongPressEnd() {

    }

    @Override
    public void onDragEnd() {

    }

    @Override
    public void onSearchStart() {

    }

    @Override
    public void onLimitListChanged(LimitList limitList) {

    }

    @Override
    public void onSearchResult(List<Card> Cards) {

    }

    @Override
    public void onResetSearch() {

    }
}

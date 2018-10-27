package cn.garymb.ygomobile.ui.cards;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.FastScrollLinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerViewItemListener;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.DeckLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.DeckGroupView;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.ShareUtil;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

class DeckManagerActivityImpl2 extends BaseActivity implements CardLoader.CallBack {
    private DeckGroupView mDeckView;
    protected CardSearcher mCardSelector;
    protected StringManager mStringManager = DataManager.get().getStringManager();
    protected LimitManager mLimitManager = DataManager.get().getLimitManager();
    protected CardLoader mCardLoader;
    protected boolean isLoad = false;
    private String mPreLoad;
    private LimitList mLimitList;
    private File mYdkFile;
    private ImageLoader mImageLoader;
    private AppsSettings mSettings = AppsSettings.get();
    private AppCompatSpinner mLimitSpinner;
    private AppCompatSpinner mDeckSpinner;
    private SimpleSpinnerAdapter mSimpleSpinnerAdapter;
    protected DrawerLayout mDrawerlayout;
    private RecyclerView mListView;
    protected CardListAdapter mCardListAdapater;
    private String mDeckMd5;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck_cards2);
        Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        enableBackHome();
        mLimitSpinner = $(R.id.sp_limit_list);
        mDeckSpinner = $(R.id.toolbar_list);
        mDeckView = $(R.id.deck_group);
        mDrawerlayout = $(R.id.drawer_layout);
        mImageLoader = ImageLoader.get(this);
        mCardLoader = new CardLoader(this);
        mCardLoader.setCallBack(this);
        mCardSelector = new CardSearcher($(R.id.nav_view_list), mCardLoader);
        mListView = $(R.id.list_cards);
        mCardListAdapater = new CardListAdapter(this, mImageLoader);
        mCardListAdapater.setEnableSwipe(true);
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(this));
        mListView.setAdapter(mCardListAdapater);
        setListeners();
        //
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawerlayout, toolbar, R.string.search_open, R.string.search_close);
        toggle.setDrawerIndicatorEnabled(false);
        mDrawerlayout.addDrawerListener(toggle);
        toggle.setToolbarNavigationClickListener((v) -> {
            onBackHome();
        });
        toggle.syncState();
        mDeckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                File file = getSelectDeck(mDeckSpinner);
                if (file != null) {
                    loadDeck(file);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        if (getIntent().hasExtra(Intent.EXTRA_TEXT)) {
            String path = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(path)) {
                mPreLoad = path;
            }
        }
        View btnDel = $(R.id.btn_delete);
        CheckBox checkBox = $(R.id.chk_autosort);
        mDeckView.setAutoSort(checkBox.isChecked());
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDeckView.setAutoSort(isChecked);
            }
        });
        $(R.id.btn_edit_delete).setOnClickListener(v -> {
            if (mDeckView.getEditMode() != DeckGroupView.EditMode.None) {
                mDeckView.setEditMode(DeckGroupView.EditMode.None);
                btnDel.setEnabled(false);
            } else {
                mDeckView.setEditMode(DeckGroupView.EditMode.Delete);
                btnDel.setEnabled(true);
            }
        });

        $(R.id.btn_edit_main2side).setOnClickListener(v -> {
            if (mDeckView.getEditMode() != DeckGroupView.EditMode.None) {
                mDeckView.setEditMode(DeckGroupView.EditMode.None);
                btnDel.setEnabled(false);
            } else {
                mDeckView.setEditMode(DeckGroupView.EditMode.Main2Side);
                btnDel.setEnabled(true);
            }
        });


        $(R.id.btn_edit_side2main).setOnClickListener(v -> {
            if (mDeckView.getEditMode() != DeckGroupView.EditMode.None) {
                mDeckView.setEditMode(DeckGroupView.EditMode.None);
                btnDel.setEnabled(false);
            } else {
                mDeckView.setEditMode(DeckGroupView.EditMode.Side2Main);
                btnDel.setEnabled(true);
            }
        });

        btnDel.setOnClickListener((v) -> {
            if (mDeckView.getEditMode() != DeckGroupView.EditMode.None) {
                mDeckView.completedEdit();
                mDeckView.notifyDataSetChanged();
            }
        });
        EventBus.getDefault().register(this);
        DialogPlus dlg = DialogPlus.show(this, null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            DataManager.get().load(false);
            if (mLimitManager.getCount() > 0) {
                mCardLoader.setLimitList(mLimitManager.getTopLimit());
            }
            File file = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH + "/" + mSettings.getLastDeck() + YDK_FILE_EX);
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
                return DeckLoader.readDeck(mCardLoader, file, mLimitList);
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            isLoad = true;
            dlg.dismiss();
            mCardSelector.initItems();
            mLimitList = mCardLoader.getLimitList();
            isLoad = true;
            setCurYdkFile(mYdkFile, false);
            initLimitListSpinners(mLimitSpinner);
            initDecksListSpinners(mDeckSpinner);
            mDeckView.setDeck(rs);
            mDeckMd5 = rs.makeMd5();
            mDeckView.notifyDataSetChanged();
        });
    }

    @Override
    protected void onDestroy() {
        ImageLoader.onDestory(this);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCardInfoEvent(CardInfoEvent event) {
        int pos = event.position;
        Card cardInfo = mCardListAdapater.getItem(pos);
        if (cardInfo == null) {
            mCardListAdapater.hideMenu(null);
        } else if (event.toMain) {
            if (!addMainCard(cardInfo)) {// || !checkLimit(cardInfo, false)) {
                mCardListAdapater.hideMenu(null);
            }
        } else {
            if (!addSideCard(cardInfo)) {// || !checkLimit(cardInfo, false)) {
                mCardListAdapater.hideMenu(null);
            }
        }
    }

    private File getSelectDeck(Spinner spinner) {
        Object o = SimpleSpinnerAdapter.getSelectTag(spinner);
        if (o != null && o instanceof File) {
            return (File) o;
        }
        return null;
    }

    private void loadDeck(File file) {
        loadDeck(file, false);
    }

    private void loadDeck(File file, boolean noSaveLast) {
        DialogPlus dlg = DialogPlus.show(this, null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            if (file == null) {
                return new DeckInfo();
            }
            if (mCardLoader.isOpen() && file.exists()) {
                return DeckLoader.readDeck(mCardLoader, file, mLimitList);
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            dlg.dismiss();
            setCurYdkFile(file, noSaveLast);
            mDeckView.setDeck(rs);
            mDeckMd5 = rs.makeMd5();
            mDeckView.notifyDataSetChanged();
        });
    }

    private void initDecksListSpinners(Spinner spinner) {
        File[] files = getYdkFiles();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        String name = mYdkFile != null ? mYdkFile.getName() : null;
        int index = -1;
        if (files != null) {
            int i = 0;
            for (File file : files) {
                if (name != null && TextUtils.equals(name, file.getName())) {
                    index = i;
                }
                String filename = IOUtils.tirmName(file.getName(), YDK_FILE_EX);
                items.add(new SimpleSpinnerItem(i++, filename).setTag(file));
            }
        }
        mSimpleSpinnerAdapter = new SimpleSpinnerAdapter(this);
        mSimpleSpinnerAdapter.set(items);
        mSimpleSpinnerAdapter.setColor(Color.WHITE);
        mSimpleSpinnerAdapter.setSingleLine(true);
        spinner.setAdapter(mSimpleSpinnerAdapter);
        if (index >= 0) {
            spinner.setSelection(index);
        }
    }

    private void initLimitListSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        List<String> limitLists = mLimitManager.getLimitNames();
        int index = -1;
        int count = mLimitManager.getCount();
        LimitList cur = mLimitList;
        items.add(new SimpleSpinnerItem(0, getString(R.string.label_limitlist)));
        for (int i = 0; i < count; i++) {
            int j = i + 1;
            String name = limitLists.get(i);
            items.add(new SimpleSpinnerItem(j, name));
            if (cur != null && TextUtils.equals(cur.getName(), name)) {
                index = j;
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(this);
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
        if (index >= 0) {
            spinner.setSelection(index);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setLimitList(mLimitManager.getLimit(SimpleSpinnerAdapter.getSelectText(spinner)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void setLimitList(LimitList limitList) {
        if (limitList == null) return;
        boolean nochanged = mLimitList != null && TextUtils.equals(mLimitList.getName(), limitList.getName());
        mLimitList = limitList;
        mDeckView.setLimitList(limitList);
        runOnUiThread(() -> {
            mDeckView.notifyDataSetChanged();
        });
    }

    private void setCurYdkFile(File file) {
        setCurYdkFile(file, false);
    }

    private void setCurYdkFile(File file, boolean noSaveLast) {
        mYdkFile = file;
        if (file != null && file.exists()) {
            String name = IOUtils.tirmName(file.getName(), YDK_FILE_EX);
            setActionBarSubTitle(name);
            if (!noSaveLast) {
                mSettings.setLastDeck(name);
            }
        } else {
            setActionBarSubTitle(getString(R.string.noname));
        }
    }

    private File[] getYdkFiles() {
        File dir = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> {
            return s.toLowerCase(Locale.US).endsWith(YDK_FILE_EX);
        });
        return files;
    }

    @Override
    public void onSearchStart() {
        hideDrawers();
    }

    @Override
    public void onLimitListChanged(LimitList limitList) {

    }

    @Override
    public void onSearchResult(List<Card> cardInfos) {
        mCardListAdapater.set(cardInfos);
        mCardListAdapater.notifyDataSetChanged();
        if (cardInfos != null && cardInfos.size() > 0) {
            mListView.smoothScrollToPosition(0);
        }
        showResult(false);
    }

    @Override
    public void onResetSearch() {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.deck_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//            case R.id.action_refresh:
//                mDeckAdapater.notifyDataSetChanged();
//                break;
            case R.id.action_search:
                //弹条件对话框
                showSearch(true);
                break;
            case R.id.action_card_list:
                showResult(true);
                break;
            case R.id.action_save:
                if (mYdkFile == null) {
                    inputDeckName(null);
                } else {
                    save();
                }
                break;
            case R.id.action_rename:
                inputDeckName(null);
                break;
            case R.id.action_deck_new: {
                final String old = mYdkFile == null ? null : mYdkFile.getAbsolutePath();
                setCurYdkFile(null);
                DialogPlus builder = new DialogPlus(this);
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.question_keep_cur_deck);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonListener((dlg, rs) -> {
                    dlg.dismiss();
                    inputDeckName(old);
                });
                builder.setRightButtonListener((dlg, rs) -> {
                    dlg.dismiss();
                    loadDeck(null);
                    inputDeckName(old);
                });
                builder.setOnCloseLinster((dlg) -> {
                    dlg.dismiss();
                    loadDeck(null);
                    inputDeckName(old);
                });
                builder.show();
            }
            break;
            case R.id.action_clear_deck: {
                DialogPlus builder = new DialogPlus(this);
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.question_clear_deck);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonListener((dlg, rs) -> {
                    mDeckView.setDeck(new DeckInfo());
                    mDeckMd5 = mDeckView.getDeckInfo().makeMd5();
                    mDeckView.notifyDataSetChanged();
                    dlg.dismiss();
                });
                builder.show();
            }
            break;
            case R.id.action_delete_deck: {
                DialogPlus builder = new DialogPlus(this);
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.question_delete_deck);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonListener((dlg, rs) -> {
                    if (mYdkFile != null && mYdkFile.exists()) {
                        mYdkFile.delete();
                    }
                    dlg.dismiss();
                    initDecksListSpinners(mDeckSpinner);
                    loadDeck(null);
                });
                builder.show();
            }
            break;
            case R.id.action_unsort:
                //打乱
                mDeckView.unSort();
                mDeckView.notifyDataSetChanged();
                break;
            case R.id.action_sort:
                mDeckView.sort();
                mDeckView.notifyDataSetChanged();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareDeck() {
//        DeckUtils.save(mDeckView.getDeckInfo(), mYdkFile);
//        String label = TextUtils.isEmpty(deck.getName()) ? getString(R.string.share_deck) : deck.getName();
//        final String uriString = deck.toAppUri().toString();
//        final String httpUri = deck.toHttpUri().toString();
//        shareUrl(uriString, label);
    }

    private void shareUrl(String uri, String label) {
        String url = getString(R.string.deck_share_head) + "  " + uri;
        ShareUtil.shareText(this, getString(R.string.share_deck), url, null);
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (Build.VERSION.SDK_INT > 19) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(label, uri));
        } else {
            clipboardManager.setText(uri);
        }
        showToast(R.string.copy_to_clipbroad, Toast.LENGTH_SHORT);
    }

    private void inputDeckName(String old) {
        DialogPlus builder = new DialogPlus(this);
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.intpu_name);
        EditText editText = new EditText(this);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setSingleLine();
        if (mYdkFile != null) {
            editText.setText(mYdkFile.getName());
        }
        builder.setContentView(editText);
        builder.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
            if (old != null) {
                loadDeck(new File(old));
            }
        });
        builder.setLeftButtonListener((dlg, s) -> {
            CharSequence name = editText.getText();
            if (!TextUtils.isEmpty(name)) {
                String filename = String.valueOf(name);
                if (!filename.endsWith(YDK_FILE_EX)) {
                    filename += YDK_FILE_EX;
                }
                File ydk = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH + "/" + filename);
                if (ydk.exists()) {
                    showToast(R.string.file_exist, Toast.LENGTH_SHORT);
                    return;
                }
                if (mYdkFile != null && mYdkFile.exists()) {
                    if (mYdkFile.renameTo(ydk)) {
                        mYdkFile = ydk;
                        initDecksListSpinners(mDeckSpinner);
                        dlg.dismiss();
                        loadDeck(ydk);
                    }
                } else {
                    dlg.dismiss();
                    try {
                        ydk.createNewFile();
                    } catch (IOException e) {
                    }
                    mYdkFile = ydk;
                    initDecksListSpinners(mDeckSpinner);
                    save();
                    setCurYdkFile(mYdkFile);
                }
            } else {
                dlg.dismiss();
            }
        });
        builder.show();
    }

    private void save() {
        mDeckMd5 = mDeckView.getDeckInfo().makeMd5();
        //保存了，记录状态
        if (DeckUtils.save(mDeckView.getDeckInfo(), mYdkFile)) {
            showToast(R.string.save_tip_ok, Toast.LENGTH_SHORT);
        } else {
            showToast(R.string.save_tip_fail, Toast.LENGTH_SHORT);
        }
    }

    protected void showSearch(boolean autoclose) {
        if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
        }
        if (autoclose && mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (isLoad) {
            mDrawerlayout.openDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
    }

    protected void showResult(boolean autoclose) {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
        if (autoclose && mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
        } else if (isLoad) {
            mDrawerlayout.openDrawer(Gravity.LEFT);
        }
    }

    protected void onCardClick(View view, Card cardInfo, int pos) {
        if (mCardListAdapater.isShowMenu(view)) {
            return;
        }
        if (cardInfo != null) {
            //TODO 显示详情
        }
    }

    protected void onCardLongClick(View view, Card cardInfo, int pos) {
        //  mCardListAdapater.showMenu(view);
    }

    protected void hideDrawers() {
        if (mDrawerlayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerlayout.closeDrawer(Gravity.RIGHT);
        }
        if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
        }
    }

    protected void setListeners() {
        mListView.addOnItemTouchListener(new RecyclerViewItemListener(mListView, new RecyclerViewItemListener.OnItemListener() {
            @Override
            public void onItemClick(View view, int pos) {
                onCardClick(view, mCardListAdapater.getItem(pos), pos);
            }

            @Override
            public void onItemLongClick(View view, int pos) {
                onCardLongClick(view, mCardListAdapater.getItem(pos), pos);
            }

            @Override
            public void onItemDoubleClick(View view, int pos) {

            }
        }));
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_IDLE:
                        Glide.with(getContext()).resumeRequests();
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        Glide.with(getContext()).pauseRequests();
                        break;
                    case RecyclerView.SCROLL_STATE_SETTLING:
                        Glide.with(getContext()).resumeRequests();
                        break;
                }
            }
        });
    }

    private boolean checkLimit(Card cardInfo, boolean tip) {
        int count = mDeckView.getDeckInfo().getCardUseCount(cardInfo.Code);
        if (mLimitList != null && mLimitList.check(cardInfo, LimitType.Forbidden)) {
            if (tip) {
                showToast(getString(R.string.tip_card_max, 0), Toast.LENGTH_SHORT);
            }
            return false;
        }
        if (mLimitList != null && mLimitList.check(cardInfo, LimitType.Limit)) {
            if (count >= 1) {
                if (tip) {
                    showToast(getString(R.string.tip_card_max, 1), Toast.LENGTH_SHORT);
                }
                return false;
            }
        } else if (mLimitList != null && mLimitList.check(cardInfo, LimitType.SemiLimit)) {
            if (count >= 2) {
                if (tip) {
                    showToast(getString(R.string.tip_card_max, 2), Toast.LENGTH_SHORT);
                }
                return false;
            }
        } else if (count >= Constants.CARD_MAX_COUNT) {
            if (tip) {
                showToast(getString(R.string.tip_card_max, 3), Toast.LENGTH_SHORT);
            }
            return false;
        }
        return true;
    }

    private boolean addSideCard(Card cardInfo) {
        if (checkLimit(cardInfo, true)) {
            boolean rs = mDeckView.addSideCards(cardInfo);
            if (rs) {
                showToast(R.string.add_card_tip_ok, Toast.LENGTH_SHORT);
            } else {
                showToast(R.string.add_card_tip_fail, Toast.LENGTH_SHORT);
            }
            return rs;
        }
        return false;
    }

    private boolean addMainCard(Card cardInfo) {
        if (checkLimit(cardInfo, true)) {
            boolean rs;
            if (cardInfo.isExtraCard()) {
                rs = mDeckView.addExtraCards(cardInfo);
            } else {
                rs = mDeckView.addMainCards(cardInfo);
            }
            if (rs) {
                showToast(R.string.add_card_tip_ok, Toast.LENGTH_SHORT);
            } else {
                showToast(R.string.add_card_tip_fail, Toast.LENGTH_SHORT);
            }
            return rs;
        }
        return false;
    }
}

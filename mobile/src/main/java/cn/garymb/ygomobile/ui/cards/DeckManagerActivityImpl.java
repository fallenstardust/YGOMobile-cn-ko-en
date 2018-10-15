package cn.garymb.ygomobile.ui.cards;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerViewItemListener;
import android.support.v7.widget.helper.ItemTouchHelperPlus;
import android.support.v7.widget.helper.OnItemDragListener;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.nightonke.boommenu.BoomButtons.BoomButton;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.BoomMenuButton;

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
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckAdapater;
import cn.garymb.ygomobile.ui.cards.deck.DeckItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemTouchHelper;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.ui.cards.deck.DeckLayoutManager;
import cn.garymb.ygomobile.ui.plus.AOnGestureListener;
import cn.garymb.ygomobile.ui.plus.DefaultOnBoomListener;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.ShareUtil;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

class DeckManagerActivityImpl extends BaseCardsAcitivity implements RecyclerViewItemListener.OnItemListener, OnItemDragListener {
    private RecyclerView mRecyclerView;
    private DeckAdapater mDeckAdapater;
    private AppsSettings mSettings = AppsSettings.get();
    private LimitList mLimitList;
    private File mYdkFile;
    private DeckItemTouchHelper mDeckItemTouchHelper;
    private AppCompatSpinner mDeckSpinner;
    private SimpleSpinnerAdapter mSimpleSpinnerAdapter;
    private AppCompatSpinner mLimitSpinner;
    private String mPreLoad;
    private CardDetail mCardDetail;
    private DialogPlus mDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDeckSpinner = $(R.id.toolbar_list);
        mDeckSpinner.setPopupBackgroundResource(R.color.colorNavy);
        mLimitSpinner = $(R.id.sp_limit_list);
        mLimitSpinner.setPopupBackgroundResource(R.color.colorNavy);
        mRecyclerView = $(R.id.grid_cards);
        mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(), 0, mRecyclerView.getPaddingRight(), mRecyclerView.getPaddingBottom());
        mRecyclerView.setAdapter((mDeckAdapater = new DeckAdapater(this, mRecyclerView, getImageLoader())));
        mRecyclerView.setLayoutManager(new DeckLayoutManager(this, Constants.DECK_WIDTH_COUNT));
        mDeckItemTouchHelper = new DeckItemTouchHelper(mDeckAdapater);
        ItemTouchHelperPlus touchHelper = new ItemTouchHelperPlus(this, mDeckItemTouchHelper);
        touchHelper.setItemDragListener(this);
        touchHelper.setEnableClickDrag(Constants.DECK_SINGLE_PRESS_DRAG);
        touchHelper.attachToRecyclerView(mRecyclerView);
        mRecyclerView.addOnItemTouchListener(new RecyclerViewItemListener(mRecyclerView, this));
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
        initBoomMenuButton($(R.id.bmb));

        $(R.id.btn_nav_search).setOnClickListener((v) -> {
            doMenu(R.id.action_search);
        });
        $(R.id.btn_nav_list).setOnClickListener((v) -> {
            doMenu(R.id.action_card_list);
        });
        //
        DialogPlus dlg = DialogPlus.show(this, null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            StringManager.get().load();//loadFile(stringfile.getAbsolutePath());
            LimitManager.get().load();//loadFile(stringfile.getAbsolutePath());
            if (mLimitManager.getCount() > 1) {
                mCardLoader.setLimitList(mLimitManager.getLimit(1));
            }
            mCardLoader.openDb();
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
                return mDeckAdapater.read(mCardLoader, file, mLimitList);
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
            mDeckAdapater.setDeck(rs);
            mDeckAdapater.notifyDataSetChanged();
        });
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onLimitListChanged(LimitList limitList) {

    }

    @Override
    public void onDragStart() {

    }

    @Override
    public void onDragLongPress(int pos) {
        if (pos < 0) return;
        if (Constants.DEBUG)
            Log.d("kk", "delete " + pos);
        if (mSettings.isDialogDelete()) {

            DeckItem deckItem = mDeckAdapater.getItem(pos);
            if (deckItem == null || deckItem.getCardInfo() == null) {
                return;
            }
            DialogPlus dialogPlus = new DialogPlus(this);
            dialogPlus.setTitle(R.string.question);
            dialogPlus.setMessage(getString(R.string.delete_card, deckItem.getCardInfo().Name));
            dialogPlus.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            dialogPlus.setLeftButtonListener((dlg, v) -> {
                dlg.dismiss();
                mDeckItemTouchHelper.remove(pos);
            });
            dialogPlus.show();
        } else {
            mDeckAdapater.showHeadView();
        }
    }

    @Override
    public void onDragLongPressEnd() {
        mDeckAdapater.hideHeadView();
    }

    @Override
    public void onDragEnd() {
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
                return mDeckAdapater.read(mCardLoader, file, mLimitList);
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            dlg.dismiss();
            setCurYdkFile(file, noSaveLast);
            mDeckAdapater.setDeck(rs);
            mDeckAdapater.notifyDataSetChanged();
        });
    }

    private void setCurYdkFile(File file) {
        setCurYdkFile(file, false);
    }

    private void setCurYdkFile(File file, boolean noSaveLast) {
        mYdkFile = file;
        if (file != null && file.exists()) {
            String name = IOUtils.tirmName(file.getName(), Constants.YDK_FILE_EX);
            setActionBarSubTitle(name);
            if (!noSaveLast) {
                mSettings.setLastDeck(name);
            }
        } else {
            setActionBarSubTitle(getString(R.string.noname));
        }
    }

    @Override
    public void onSearchStart() {
        hideDrawers();
    }

    @Override
    protected void onCardClick(View view, Card cardInfo, int pos) {
        if (mCardListAdapater.isShowMenu(view)) {
            return;
        }
        if (cardInfo != null) {
            showCardDialog(mCardListAdapater, cardInfo, pos);
        }
    }

    @Override
    protected void onCardLongClick(View view, Card cardInfo, int pos) {
        //  mCardListAdapater.showMenu(view);
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

    @Override
    public void onResetSearch() {
        super.onResetSearch();
    }

    @Override
    public void onSearchResult(List<Card> cardInfos) {
        super.onSearchResult(cardInfos);
        showResult(false);
    }

    @Override
    public void onItemClick(View view, int pos) {
        if (isShowDrawer()) {
            return;
        }

        showDeckCard(view, pos);

    }

    @Override
    public void onItemLongClick(View view, int pos) {
        if (isShowDrawer()) {
            return;
        }
        //拖拽中，就不显示
        if (Constants.DECK_SINGLE_PRESS_DRAG) {
        }
    }

    @Override
    public void onItemDoubleClick(View view, int pos) {
        //拖拽中，就不显示
        if (isShowDrawer()) {
            return;
        }
        if (Constants.DECK_SINGLE_PRESS_DRAG) {
            showDeckCard(view, pos);
        }
    }

    private void showDeckCard(View view, int pos) {
        DeckItem deckItem = mDeckAdapater.getItem(pos);
        if (deckItem != null && deckItem.getCardInfo() != null) {
            showCardDialog(mDeckAdapater, deckItem.getCardInfo(), mDeckAdapater.getCardPosByView(pos));
        }
    }

    private boolean isShowDrawer() {
        return mDrawerlayout.isDrawerOpen(Gravity.LEFT)
                || mDrawerlayout.isDrawerOpen(Gravity.RIGHT);
    }

    private boolean isShowCard() {
        return mDialog != null && mDialog.isShowing();
    }

    protected void showCardDialog(CardListProvider provider, Card cardInfo, int pos) {
        if (cardInfo != null) {
            if (isShowCard()) return;
            if (mCardDetail == null) {
                mCardDetail = new CardDetail(this, getImageLoader(), mStringManager);
                mCardDetail.setOnCardClickListener(new CardDetail.OnCardClickListener() {
                    @Override
                    public void onOpenUrl(Card cardInfo) {
                        String uri = Constants.WIKI_SEARCH_URL + String.format("%08d", cardInfo.Code);
                        WebActivity.open(getContext(), cardInfo.Name, uri);
                    }

                    @Override
                    public void onClose() {
                        mDialog.dismiss();
                    }

                    @Override
                    public void onAddSideCard(Card cardInfo) {
                        addSideCard(cardInfo);
                    }

                    @Override
                    public void onAddMainCard(Card cardInfo) {
                        addMainCard(cardInfo);
                    }
                });
            }
            mCardDetail.showAdd();
            if (mDialog == null) {
                mDialog = new DialogPlus(this);
                mDialog.setView(mCardDetail.getView());
                mDialog.hideButton();
                mDialog.hideTitleBar();
                mDialog.setOnGestureListener(new AOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        if (isLeftFling(e1, e2, velocityX, velocityY)) {
                            mCardDetail.onNextCard();
                            return true;
                        } else if (isRightFling(e1, e2, velocityX, velocityY)) {
                            mCardDetail.onPreCard();
                            return true;
                        }
                        return super.onFling(e1, e2, velocityX, velocityY);
                    }
                });
            }
            if (!mDialog.isShowing()) {
                mDialog.show();
            }
            mCardDetail.bind(cardInfo, pos, provider);
        }
    }

    private boolean addSideCard(Card cardInfo) {
        if (checkLimit(cardInfo, true)) {
            boolean rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.SideCard);
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
                rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.ExtraCard);
            } else {
                rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.MainCard);
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

    private boolean isExit = false;
//
//    @Override
//    public void finish() {
//        if (!isExit) {
//            if (mYdkFile != null && mYdkFile.exists()) {
//                DialogPlus builder = new DialogPlus(this);
//                builder.setTitle(R.string.question);
//                builder.setMessage(R.string.quit_deck_tip);
//                builder.setLeftButtonListener((dlg, s) -> {
//                    dlg.dismiss();
//                    isExit = true;
//                    finish();
//                });
//                builder.show();
//                return;
//            }
//        }
//        super.finish();
//    }

    @Override
    protected void onBackHome() {
        if (mDeckAdapater.isChanged()) {
            if (mYdkFile != null && mYdkFile.exists()) {
                DialogPlus builder = new DialogPlus(this);
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.quit_deck_tip);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setRightButtonText(getString(R.string.save_quit));
                builder.setLeftButtonText(getString(R.string.quit));
                builder.setRightButtonListener((dlg, s) -> {
                    doMenu(R.id.action_save);
                    dlg.dismiss();
                    isExit = true;
                    finish();
                });
                builder.setLeftButtonListener((dlg, s) -> {
                    dlg.dismiss();
                    isExit = true;
                    finish();
                });
                builder.show();
            }
        } else {
            super.onBackHome();
        }
    }

    @Override
    public void onBackPressed() {
        if (mDrawerlayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerlayout.closeDrawer(Gravity.RIGHT);
        } else if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
        } else if (!isExit) {
            if (mDeckAdapater.isChanged()) {
                if (mYdkFile != null && mYdkFile.exists()) {
                    DialogPlus builder = new DialogPlus(this);
                    builder.setTitle(R.string.question);
                    builder.setMessage(R.string.quit_deck_tip);
                    builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                    builder.setLeftButtonListener((dlg, s) -> {
                        dlg.dismiss();
                        isExit = true;
                        finish();
                    });
                    builder.show();
                }
            } else {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    private boolean checkLimit(Card cardInfo, boolean tip) {
        SparseArray<Integer> mCount = mDeckAdapater.getCardCount();
        if (mLimitList != null && mLimitList.check(cardInfo, LimitType.Forbidden)) {
            if (tip) {
                showToast(getString(R.string.tip_card_max, 0), Toast.LENGTH_SHORT);
            }
            return false;
        }
        Integer id = cardInfo.Alias > 0 ? cardInfo.Alias : cardInfo.Code;
        Integer count = mCount.get(id);
        if (count != null) {
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
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (doMenu(item.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(item);

    }

    public boolean doMenu(int menuId) {
        switch (menuId) {
            case R.id.action_quit:
                onBackHome();
                break;
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
                    mDeckAdapater.setDeck(new DeckInfo());
                    mDeckAdapater.notifyDataSetChanged();
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
                mDeckAdapater.unSort();
                break;
            case R.id.action_sort:
                mDeckAdapater.sort();
                break;
//            case R.id.action_share_deck:
//                shareDeck();
//                break;
            default:
                return false;
        }
        return true;
    }

    private void shareDeck() {
        Deck deck = mDeckAdapater.toDeck(mYdkFile);
        String label = TextUtils.isEmpty(deck.getName()) ? getString(R.string.share_deck) : deck.getName();
        final String uriString = deck.toAppUri().toString();
        final String httpUri = deck.toHttpUri().toString();
        shareUrl(uriString, label);
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

    private File getSelectDeck(Spinner spinner) {
        Object o = SimpleSpinnerAdapter.getSelectTag(spinner);
        if (o != null && o instanceof File) {
            return (File) o;
        }
        return null;
    }

//    private LimitList getSelectLimitList(Spinner spinner) {
//        int val = (int) SimpleSpinnerAdapter.getSelect(spinner);
//        if (val > 0) {
//            return mLimitManager.getLimit(val);
//        }
//        return null;
//    }

    private File[] getYdkFiles() {
        File dir = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> {
            return s.toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX);
        });
        return files;
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
                String filename = IOUtils.tirmName(file.getName(), Constants.YDK_FILE_EX);
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

    //    private void initLimitListSpinners(Spinner spinner) {
//        List<SimpleSpinnerItem> items = new ArrayList<>();
//        List<Integer> ids = mLimitManager.getLists();
//        int index = -1;
//        int i = 0;
//        for (Integer id : ids) {
//            LimitList list = mLimitManager.getLimitFromIndex(id);
//            if (list == mLimitList) {
//                index = i;
//            }
//            items.add(new SimpleSpinnerItem(id, list.getName()));
//            i++;
//        }
//        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(this);
//        adapter.set(items);
//        spinner.setAdapter(adapter);
//        if (index >= 0) {
//            spinner.setSelection(index);
//        }
    private void initLimitListSpinners(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        List<LimitList> limitLists = mLimitManager.getLimitLists();
        int index = -1;
        int count = mLimitManager.getCount();
        LimitList cur = mLimitList;
        for (int i = 0; i < count; i++) {
            LimitList list = limitLists.get(i);
            if (i == 0) {
                items.add(new SimpleSpinnerItem(i, getString(R.string.label_limitlist)));
            } else {
                items.add(new SimpleSpinnerItem(i, list.getName()));
            }
            if (cur != null) {
                if (TextUtils.equals(cur.getName(), list.getName())) {
                    index = i;
                }
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
                setLimitList(mLimitManager.getLimit(position));
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
        if (!nochanged) {
            mDeckAdapater.setLimitList(mLimitList);
            runOnUiThread(() -> {
                mDeckAdapater.notifyItemRangeChanged(DeckItem.MainStart, DeckItem.MainEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.ExtraStart, DeckItem.ExtraEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.SideStart, DeckItem.SideEnd);
            });
        }
        mCardListAdapater.setLimitList(limitList);
        runOnUiThread(() -> {
            mCardListAdapater.notifyDataSetChanged();
        });
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
        if (mDeckAdapater.save(mYdkFile)) {
            showToast(R.string.save_tip_ok, Toast.LENGTH_SHORT);
        } else {
            showToast(R.string.save_tip_fail, Toast.LENGTH_SHORT);
        }
    }


    private void initBoomMenuButton(BoomMenuButton menu) {
        final SparseArray<Integer> mMenuIds = new SparseArray<>();
        addMenuButton(mMenuIds, menu, R.id.action_card_search, R.string.deck_list, R.drawable.listicon);
        addMenuButton(mMenuIds, menu, R.id.action_save, R.string.save_deck, R.drawable.save);
        addMenuButton(mMenuIds, menu, R.id.action_clear_deck, R.string.clear_deck, R.drawable.clear_deck);

        addMenuButton(mMenuIds, menu, R.id.action_deck_new, R.string.new_deck, R.drawable.addsever);
        addMenuButton(mMenuIds, menu, R.id.action_rename, R.string.rename_deck, R.drawable.rename);
        addMenuButton(mMenuIds, menu, R.id.action_delete_deck, R.string.delete_deck, R.drawable.delete);

        addMenuButton(mMenuIds, menu, R.id.action_unsort, R.string.unsort, R.drawable.unsort);
        addMenuButton(mMenuIds, menu, R.id.action_sort, R.string.sort, R.drawable.sort);
        addMenuButton(mMenuIds, menu, R.id.action_quit, R.string.quit, R.drawable.quit);

        menu.setOnBoomListener(new DefaultOnBoomListener() {
            @Override
            public void onClicked(int index, BoomButton boomButton) {
                doMenu(mMenuIds.get(index));
            }
        });
    }

    private void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, int stringId, int image) {
        addMenuButton(mMenuIds, menuButton, menuId, getString(stringId), image);
    }

    private void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, String str, int image) {
        TextOutsideCircleButton.Builder builder = new TextOutsideCircleButton.Builder()
                .shadowColor(Color.TRANSPARENT)
                .normalColor(Color.TRANSPARENT)
                .normalImageRes(image)
                .normalText(str);
        menuButton.addBuilder(builder);
        mMenuIds.put(mMenuIds.size(), menuId);
    }
}

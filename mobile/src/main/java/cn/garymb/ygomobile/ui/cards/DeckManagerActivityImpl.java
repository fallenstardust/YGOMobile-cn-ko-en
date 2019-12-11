package cn.garymb.ygomobile.ui.cards;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.RecyclerViewItemListener;
import androidx.recyclerview.widget.ItemTouchHelperPlus;
import androidx.recyclerview.widget.OnItemDragListener;
import androidx.recyclerview.widget.RecyclerView;

import com.feihua.dialogutils.util.DialogUtils;
import com.nightonke.boommenu.BoomButtons.BoomButton;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.BoomMenuButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckAdapater;
import cn.garymb.ygomobile.ui.cards.deck.DeckItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemTouchHelper;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.ui.cards.deck.DeckLayoutManager;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.Util;
import cn.garymb.ygomobile.ui.plus.AOnGestureListener;
import cn.garymb.ygomobile.ui.plus.DefaultOnBoomListener;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.ServiceDuelAssistant;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.ShareUtil;
import cn.garymb.ygomobile.utils.YGODialogUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

class DeckManagerActivityImpl extends BaseCardsAcitivity implements RecyclerViewItemListener.OnItemListener, OnItemDragListener, YGODialogUtil.OnDeckMenuListener {

    //region ui onCreate/onDestroy
    private RecyclerView mRecyclerView;
    private DeckAdapater mDeckAdapater;
    private AppsSettings mSettings = AppsSettings.get();

    private File mPreLoadFile;
    private DeckItemTouchHelper mDeckItemTouchHelper;
    private AppCompatSpinner mDeckSpinner;
    private TextView tv_deck;
    private SimpleSpinnerAdapter mSimpleSpinnerAdapter;
    private AppCompatSpinner mLimitSpinner;
    private CardDetail mCardDetail;
    private DialogPlus mDialog;
    private DialogPlus builderShareLoading;
    private boolean isExit = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tv_deck = $(R.id.tv_deck);
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
                    loadDeckFromFile(file);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        String preLoadFile = null;
        if (getIntent().hasExtra(Intent.EXTRA_TEXT)) {
            preLoadFile = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        }
        initBoomMenuButton($(R.id.bmb));

        $(R.id.btn_nav_search).setOnClickListener((v) -> {
            doMenu(R.id.action_search);
        });
        $(R.id.btn_nav_list).setOnClickListener((v) -> {
            doMenu(R.id.action_card_list);
        });
        //
        final File _file;
        //打开指定卡组
        if (!TextUtils.isEmpty(preLoadFile) && (mPreLoadFile = new File(preLoadFile)).exists()) {
            //外面卡组
            _file = mPreLoadFile;
        } else {
            mPreLoadFile = null;
            //最后卡组
            _file = new File(mSettings.getLastDeckPath());
        }
        init(_file);
        EventBus.getDefault().register(this);
        tv_deck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                YGODialogUtil.dialogDeckSelect(DeckManagerActivityImpl.this, AppsSettings.get().getLastDeckPath(), DeckManagerActivityImpl.this);
            }
        });
    }
    //endregion

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    //region card edit
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
    //endregion

    @Override
    public void onDragEnd() {
    }
    //endregion

    //region load deck
    private void loadDeckFromFile(File file) {
        if (!mCardLoader.isOpen() || file == null || !file.exists()) {
            setCurDeck(new DeckInfo());
            return;
        }
        DialogPlus dlg = DialogPlus.show(this, null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            if (mCardLoader.isOpen() && file.exists()) {
                return mDeckAdapater.read(mCardLoader, file, mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            dlg.dismiss();
            setCurDeck(rs);
        });
    }
    //endregion

    //region init
    private void init(File ydk) {
        DialogPlus dlg = DialogPlus.show(this, null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            DataManager.get().load(false);
            //默认第一个卡表
            if (mLimitManager.getCount() > 0) {
                mCardLoader.setLimitList(mLimitManager.getTopLimit());
            }
            File file = ydk;
            if (!file.exists()) {
                //当默认卡组不存在的时候
                List<File> files = getYdkFiles();
                if (files != null && files.size() > 0) {
                    file = files.get(0);
                }
            }
            //EXTRA_DECK
            if (file == null) {
                return new DeckInfo();
            }
            Log.i("kk", "load ydk " + file);
            if (mCardLoader.isOpen() && file.exists()) {
                return mDeckAdapater.read(mCardLoader, file, mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            isLoad = true;
            dlg.dismiss();
            mCardSelector.initItems();
            initLimitListSpinners(mLimitSpinner, mCardLoader.getLimitList());
            initDecksListSpinners(mDeckSpinner, rs.source);
            //设置当前卡组
            setCurDeck(rs);
        });
    }

    /**
     * 设置当前卡组
     *
     * @param deckInfo
     */
    private void setCurDeck(DeckInfo deckInfo) {
        if (deckInfo == null) {
            deckInfo = new DeckInfo();
        }
        File file = deckInfo.source;
        if (file != null && file.exists()) {
            String name = IOUtils.tirmName(file.getName(), Constants.YDK_FILE_EX);
            setActionBarSubTitle(name);
//            if (inDeckDir(file)) {
            //记住最后打开的卡组
            mSettings.setLastDeckPath(file.getAbsolutePath());

            tv_deck.setText(name);
//            }
        } else {
            setActionBarSubTitle(getString(R.string.noname));
        }
        mDeckAdapater.setDeck(deckInfo);
        mDeckAdapater.notifyDataSetChanged();
    }

    private boolean inDeckDir(File file) {
        String deck = new File(AppsSettings.get().getDeckDir()).getAbsolutePath();
        return TextUtils.equals(deck, file.getParentFile().getAbsolutePath());
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
        return mDrawerlayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)
                || mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY);
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
                        String uri;
                        if (cardInfo.Alias != 0) {
                            uri = Constants.WIKI_SEARCH_URL + String.format("%08d", cardInfo.Alias);
                        } else {
                            uri = Constants.WIKI_SEARCH_URL + String.format("%08d", cardInfo.Code);
                        }
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

    @Override
    protected void onBackHome() {
        if (mDeckAdapater.isChanged()) {
            File ydk = mDeckAdapater.getYdkFile();
            if (ydk != null && ydk.exists()) {
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
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
        } else if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (!isExit) {
            if (mDeckAdapater.isChanged()) {
                File ydk = mDeckAdapater.getYdkFile();
                if (ydk != null && ydk.exists()) {
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
        LimitList limitList = mDeckAdapater.getLimitList();
        Integer id = cardInfo.Alias > 0 ? cardInfo.Alias : cardInfo.Code;
        Integer count = mCount.get(id);
        if (limitList == null) {
            return count != null && count <= 3;
        }
        if (limitList.check(cardInfo, LimitType.Forbidden)) {
            if (tip) {
                showToast(getString(R.string.tip_card_max, 0), Toast.LENGTH_SHORT);
            }
            return false;
        }
        if (count != null) {
            if (limitList.check(cardInfo, LimitType.Limit)) {
                if (count >= 1) {
                    if (tip) {
                        showToast(getString(R.string.tip_card_max, 1), Toast.LENGTH_SHORT);
                    }
                    return false;
                }
            } else if (limitList.check(cardInfo, LimitType.SemiLimit)) {
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
            case R.id.action_share_deck:
                if (mDeckAdapater.getYdkFile() == null) {
                    Toast.makeText(this, R.string.unable_to_edit_empty_deck, Toast.LENGTH_SHORT).show();
                    return true;
                }
                shareDeck();
                break;
            case R.id.action_save:
                if (mPreLoadFile != null && mPreLoadFile == mDeckAdapater.getYdkFile()) {
                    //需要保存到deck文件夹
                    inputDeckName(mPreLoadFile, null, true);
                } else {
                    if (mDeckAdapater.getYdkFile() == null) {
                        inputDeckName(null, null, true);
                    } else {
                        if (mDeckAdapater.getYdkFile().getParent().equals(mSettings.getAiDeckDir())) {
                            Toast.makeText(this, R.string.donot_editor_bot_Deck, Toast.LENGTH_SHORT).show();
                        } else {
                            save(mDeckAdapater.getYdkFile());
                        }
                    }
                }
                break;
            case R.id.action_rename:
                if (mDeckAdapater.getYdkFile() == null) {
                    Toast.makeText(this, R.string.unable_to_edit_empty_deck, Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (mDeckAdapater.getYdkFile().getParent().equals(mSettings.getAiDeckDir())) {
                    Toast.makeText(this, R.string.donot_editor_bot_Deck, Toast.LENGTH_SHORT).show();
                } else {
                    inputDeckName(mDeckAdapater.getYdkFile(), mDeckAdapater.getYdkFile().getParent(), false);
                }
                break;
            case R.id.action_deck_new:
                createDeck(null);

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
                if (mDeckAdapater.getYdkFile() == null) {
                    Toast.makeText(this, R.string.unable_to_edit_empty_deck, Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (mDeckAdapater.getYdkFile().getParent().equals(mSettings.getAiDeckDir())) {
                    Toast.makeText(this, R.string.donot_editor_bot_Deck, Toast.LENGTH_SHORT).show();
                } else {
                    DialogPlus builder = new DialogPlus(this);
                    builder.setTitle(R.string.question);
                    builder.setMessage(R.string.question_delete_deck);
                    builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                    builder.setLeftButtonListener((dlg, rs) -> {

                        if (mDeckAdapater.getYdkFile() != null) {
                            FileUtils.deleteFile(mDeckAdapater.getYdkFile());
                            dlg.dismiss();
                            File file = getFirstYdk();
                            initDecksListSpinners(mDeckSpinner, file);
                            loadDeckFromFile(file);
                        } else {
                            return;
                        }
                    });
                    builder.show();
                }
            }
            break;
            case R.id.action_unsort:
                //打乱
                mDeckAdapater.unSort();
                break;
            case R.id.action_sort:
                mDeckAdapater.sort();
                break;
            default:
                return false;
        }
        return true;
    }

    private void createDeck(String savePath) {
        final File old = mDeckAdapater.getYdkFile();
        DialogPlus builder = new DialogPlus(this);
        builder.setTitle(R.string.question);
        builder.setMessage(R.string.question_keep_cur_deck);
        builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
        builder.setLeftButtonListener((dlg, rs) -> {
            dlg.dismiss();
            //复制当前卡组
            inputDeckName(old, savePath, true);
        });
        builder.setRightButtonListener((dlg, rs) -> {
            dlg.dismiss();
            setCurDeck(null);
            inputDeckName(null, savePath, true);
        });
        builder.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
            setCurDeck(null);
            inputDeckName(null, savePath, true);
        });
        builder.show();
    }

    private File getFirstYdk() {
        List<File> files = getYdkFiles();
        return files == null || files.size() == 0 ? null : files.get(0);
    }

    private void shareDeck() {
        builderShareLoading = new DialogPlus(this);
        builderShareLoading.showProgressBar();
        builderShareLoading.hideTitleBar();
        builderShareLoading.setMessage(R.string.Pre_share);
        builderShareLoading.show();

        //先排序
//        mDeckAdapater.sort();
        //保存
//        if (mPreLoadFile != null && mPreLoadFile == mDeckAdapater.getYdkFile()) {
//            //需要保存到deck文件夹
//            inputDeckName(mPreLoadFile, null, true);
//        } else {
//            if (mDeckAdapater.getYdkFile() == null) {
//                inputDeckName(null, null, true);
//            } else {
//                save(mDeckAdapater.getYdkFile());
//            }
//        }
//        //保存成功后重新加载卡组
//        File file = getSelectDeck(mDeckSpinner);
//        if (file != null) {
//            loadDeckFromFile(file);
//        }
        //延时半秒，使整体看起来更流畅
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                shareDeck1();
            }
        }, 500);
    }

    private void shareDeck1() {
        //开启绘图缓存
        mRecyclerView.setDrawingCacheEnabled(true);
        //这个方法可调可不调，因为在getDrawingCache()里会自动判断有没有缓存有没有准备好，
        //如果没有，会自动调用buildDrawingCache()
        mRecyclerView.buildDrawingCache();
        //获取绘图缓存 这里直接创建了一个新的bitmap
        //因为我们在最后需要释放缓存资源，会释放掉缓存中创建的bitmap对象
        Bitmap bitmap = BitmapUtil.drawBg4Bitmap(Color.parseColor("#e6f3fd"), Bitmap.createBitmap(mRecyclerView.getDrawingCache(), 0, 0, mRecyclerView.getMeasuredWidth(),
                mRecyclerView.getMeasuredHeight()));

        //清理绘图缓存，释放资源
        mRecyclerView.destroyDrawingCache();
//        shotRecyclerView(mRecyclerView)

        Deck deck = mDeckAdapater.toDeck(mDeckAdapater.getYdkFile());
        String deckName = deck.getName();
        int end = deckName.lastIndexOf(".");
        if (end != -1) {
            deckName = deckName.substring(0, end);
        }
        String savePath = new File(AppsSettings.get().getDeckSharePath(), deckName + ".jpg").getAbsolutePath();
        BitmapUtil.saveBitmap(bitmap, savePath, 50);
        builderShareLoading.dismiss();
        DialogUtils du = DialogUtils.getdx(DeckManagerActivityImpl.this);
        View viewDialog = du.dialogBottomSheet(R.layout.dialog_deck_share, 0);
        ImageView iv_image = viewDialog.findViewById(R.id.iv_image);
        Button bt_image_share = viewDialog.findViewById(R.id.bt_image_share);
        Button bt_code_share = viewDialog.findViewById(R.id.bt_code_share);
        EditText et_code = viewDialog.findViewById(R.id.et_code);
        et_code.setText(mDeckAdapater.getDeckInfo().toDeck().toAppUri().toString());
        ImageUtil.setImage(DeckManagerActivityImpl.this, savePath, iv_image);

        bt_code_share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                du.dis();
                stopService(new Intent(DeckManagerActivityImpl.this, ServiceDuelAssistant.class));
                Util.fzMessage(DeckManagerActivityImpl.this, et_code.getText().toString().trim());
                showToast(getString(R.string.deck_text_copyed));
                //复制完毕开启决斗助手
                Util.startDuelService(DeckManagerActivityImpl.this);

            }
        });

        bt_image_share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                du.dis();
                ShareUtil.shareImage(DeckManagerActivityImpl.this, getContext().getString(R.string.screenshoot), savePath, null);

            }
        });


        //复制前关闭决斗助手


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

    private List<File> getYdkFiles() {
        File dir = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> {
            return s.toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX);
        });
        if (files != null) {
            List<File> list = new ArrayList<>(Arrays.asList(files));
            if (mPreLoadFile != null && mPreLoadFile.exists()) {
                boolean hasCur = false;
                for (File f : list) {
                    if (TextUtils.equals(f.getAbsolutePath(), mPreLoadFile.getAbsolutePath())) {
                        hasCur = true;
                        break;
                    }
                }
                if (!hasCur) {
                    list.add(mPreLoadFile);
                }
            }
            return list;
        }
        return null;
    }

    private void initDecksListSpinners(Spinner spinner, File curYdk) {
        List<File> files = getYdkFiles();
        List<SimpleSpinnerItem> items = new ArrayList<>();
        String name = curYdk != null ? curYdk.getName() : null;
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File ydk1, File ydk2) {
                if (ydk1.isDirectory() && ydk2.isFile())
                    return -1;
                if (ydk1.isFile() && ydk2.isDirectory())
                    return 1;
                return ydk1.getName().compareTo(ydk2.getName());
            }
        });
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
    private void initLimitListSpinners(Spinner spinner, LimitList cur) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        List<String> limitLists = mLimitManager.getLimitNames();
        int index = -1;
        int count = mLimitManager.getCount();
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
        LimitList last = mDeckAdapater.getLimitList();
        boolean nochanged = last != null && TextUtils.equals(last.getName(), limitList.getName());
        if (!nochanged) {
            mDeckAdapater.setLimitList(limitList);
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

    private void inputDeckName(File oldYdk, String savePath, boolean keepOld) {
        DialogPlus builder = new DialogPlus(this);
        builder.setTitle(R.string.intpu_name);
        EditText editText = new EditText(this);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setSingleLine();
        if (oldYdk != null) {
            editText.setText(oldYdk.getName());
        }
        builder.setContentView(editText);
        builder.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
        });
        builder.setLeftButtonListener((dlg, s) -> {
            CharSequence name = editText.getText();
            if (!TextUtils.isEmpty(name)) {
                String filename = String.valueOf(name);
                if (!filename.endsWith(YDK_FILE_EX)) {
                    filename += YDK_FILE_EX;
                }
                File ydk;
                if (TextUtils.isEmpty(savePath))
                    ydk = new File(mSettings.getDeckDir(), filename);
                else
                    ydk = new File(savePath, filename);
                if (ydk.exists()) {
                    showToast(R.string.file_exist, Toast.LENGTH_SHORT);
                    return;
                }
                if (!keepOld && oldYdk != null && oldYdk.exists()) {
                    if (oldYdk.renameTo(ydk)) {
                        initDecksListSpinners(mDeckSpinner, ydk);
                        dlg.dismiss();
                        loadDeckFromFile(ydk);
                    }
                } else {
                    if (oldYdk == mPreLoadFile) {
                        mPreLoadFile = null;
                    }
                    dlg.dismiss();
                    try {
                        ydk.createNewFile();
                    } catch (IOException e) {
                    }
                    initDecksListSpinners(mDeckSpinner, ydk);
                    save(ydk);
                    loadDeckFromFile(ydk);
                }
            } else {
                dlg.dismiss();
            }
        });
        builder.show();
    }

    private void save(File ydk) {
        if (mDeckAdapater.save(ydk)) {
            showToast(R.string.save_tip_ok, Toast.LENGTH_SHORT);
        } else {
            showToast(R.string.save_tip_fail, Toast.LENGTH_SHORT);
        }
    }


    private void initBoomMenuButton(BoomMenuButton menu) {
        final SparseArray<Integer> mMenuIds = new SparseArray<>();
        // addMenuButton(mMenuIds, menu, R.id.action_card_search, R.string.deck_list, R.drawable.listicon);
        addMenuButton(mMenuIds, menu, R.id.action_share_deck, R.string.share_deck, R.drawable.shareicon);
        addMenuButton(mMenuIds, menu, R.id.action_save, R.string.save_deck, R.drawable.save);
        addMenuButton(mMenuIds, menu, R.id.action_clear_deck, R.string.clear_deck, R.drawable.clear_deck);

        addMenuButton(mMenuIds, menu, R.id.action_deck_new, R.string.new_deck, R.drawable.addsever);
        addMenuButton(mMenuIds, menu, R.id.action_rename, R.string.rename_deck, R.drawable.rename);
        addMenuButton(mMenuIds, menu, R.id.action_delete_deck, R.string.delete_deck, R.drawable.delete);

        addMenuButton(mMenuIds, menu, R.id.action_unsort, R.string.unsort, R.drawable.unsort);
        addMenuButton(mMenuIds, menu, R.id.action_sort, R.string.sort, R.drawable.sort);
        addMenuButton(mMenuIds, menu, R.id.action_quit, R.string.quit, R.drawable.quit);

        //设置展开或隐藏的延时。 默认值为 800ms。
        menu.setDuration(150);
        //设置每两个子按钮之间动画的延时（ms为单位）。 比如，如果延时设为0，那么所有子按钮都会同时展开或隐藏，默认值为100ms。
        menu.setDelay(10);

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

    @Override
    public void onDeckSelect(DeckFile deckFile) {
        loadDeckFromFile(new File(deckFile.getPath()));
    }

    @Override
    public void onDeckDel(List<DeckFile> deckFileList) {
        File deck = mDeckAdapater.getYdkFile();
        if (deck == null)
            return;
        String currentDeckPath = deck.getAbsolutePath();
        for (DeckFile deckFile : deckFileList) {
            if (deckFile.getPath().equals(currentDeckPath)) {
                List<File> files = getYdkFiles();
                File file = null;
                if (files != null && files.size() > 0) {
                    file = files.get(0);
                }
                if (file != null) {
                    loadDeckFromFile(file);
                } else {
                    setCurDeck(new DeckInfo());
                }
                return;
            }
        }
    }

    @Override
    public void onDeckMove(List<DeckFile> deckFileList, DeckType toDeckType) {
        String currentDeckPath = mDeckAdapater.getYdkFile().getAbsolutePath();
        for (DeckFile deckFile : deckFileList) {
            if (deckFile.getPath().equals(currentDeckPath)) {
                loadDeckFromFile(new File(toDeckType.getPath(), deckFile.getName() + ".ydk"));
                return;
            }
        }
    }

    @Override
    public void onDeckCopy(List<DeckFile> deckFileList, DeckType toDeckType) {
        String currentDeckPath = mDeckAdapater.getYdkFile().getAbsolutePath();
        for (DeckFile deckFile : deckFileList) {
            if (deckFile.getPath().equals(currentDeckPath)) {
                loadDeckFromFile(new File(toDeckType.getPath(), deckFile.getName() + ".ydk"));
                return;
            }
        }
    }

    @Override
    public void onDeckNew(DeckType currentDeckType) {
        createDeck(currentDeckType.getPath());
    }
}

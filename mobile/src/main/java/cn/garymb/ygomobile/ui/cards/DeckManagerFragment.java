package cn.garymb.ygomobile.ui.cards;

import static android.content.Context.CLIPBOARD_SERVICE;
import static cn.garymb.ygomobile.Constants.ORI_DECK;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;
import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_SHARE_FILE;
import static cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareFileUtil.convertToUnixTimestamp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelperPlus;
import androidx.recyclerview.widget.OnItemDragListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerViewItemListener;

import com.app.hubert.guide.NewbieGuide;
import com.app.hubert.guide.model.GuidePage;
import com.app.hubert.guide.model.HighLight;
import com.app.hubert.guide.model.HighlightOptions;
import com.feihua.dialogutils.util.DialogUtils;
import com.nightonke.boommenu.BoomButtons.BoomButton;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.BoomMenuButton;
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.CardSearchInfo;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckAdapater;
import cn.garymb.ygomobile.ui.cards.deck.DeckItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemTouchHelper;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.ui.cards.deck.DeckLayoutManager;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckManageDialog;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareFileUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.AOnGestureListener;
import cn.garymb.ygomobile.ui.plus.DefaultOnBoomListener;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.ShareUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.PackManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

/**
 * 卡组编辑页面，在本页面中显示某个卡组的内容
 * 注意，卡组编辑页面中的长按事件回调在ItemTouchHelperPlus中实现，而非在
 * RecyclerViewItemListener.OnItemListener中
 */
public class DeckManagerFragment extends BaseFragemnt implements RecyclerViewItemListener.OnItemListener, OnItemDragListener, YGODeckDialogUtil.OnDeckMenuListener, CardLoader.CallBack, CardSearcher.CallBack {
    private static final String TAG = "DeckManagerFragment";
    protected DrawerLayout mDrawerLayout;
    protected RecyclerView mListView;
    protected CardLoader mCardLoader;
    protected CardSearcher mCardSearcher;
    protected CardManager mCardManager;
    protected PackManager mPackManager;
    protected CardListAdapter mCardListAdapter;
    protected boolean isLoad = false;
    private HomeActivity activity;
    private String mDeckId;
    private LinearLayout ll_click_like;
    private TextView tv_add_1;
    public static List<MyOnlineDeckDetail> originalData; // 保存原始数据
    protected int screenWidth;

    //region ui onCreate/onDestroy
    private RecyclerView mRecyclerView;
    public DeckAdapater mDeckAdapater;
    private final AppsSettings mSettings = AppsSettings.get();
    private BaseActivity mContext;
    long exitLasttime = 0;

    private File mPreLoadFile;//预加载卡组，用于外部打开ydk文件或通过卡组广场预览卡组时，值为file。当未通过预加载打开ydk（打开卡组时），值为null
    private DeckItemTouchHelper mDeckItemTouchHelper;
    private TextView tv_deck;
    private TextView tv_result_count;
    private AppCompatSpinner mLimitSpinner;
    private CardDetail mCardDetail;
    private DialogPlus mDialog;
    private DialogPlus builderShareLoading;

    private View layoutView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        activity = (HomeActivity) getActivity();
        originalData = new ArrayList<>();

        layoutView = inflater.inflate(R.layout.fragment_deck_cards, container, false);
        AnimationShake2(layoutView);
        initView(layoutView);
        //检查外部调用方是否传入了ydk文件路径，如果传入，则打开外部ydk文件
        preLoadFile();
        //event
        if (!EventBus.getDefault().isRegistered(this)) {//加上判断
            EventBus.getDefault().register(this);
        }
        showNewbieGuide("deckmain");
        return layoutView;
    }

    public void initView(View layoutView) {
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        mDrawerLayout = layoutView.findViewById(R.id.drawer_layout);
        mListView = layoutView.findViewById(R.id.list_cards);
        mCardListAdapter = new CardListAdapter(getContext(), activity.getImageLoader());
        mCardListAdapter.setEnableSwipe(true);
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        mListView.setAdapter(mCardListAdapter);
        setListeners();

        mPackManager = DataManager.get().getPackManager();
        mCardManager = DataManager.get().getCardManager();
        mCardLoader = new CardLoader(getContext());
        mCardLoader.setCallBack(this);
        mCardSearcher = new CardSearcher(layoutView.findViewById(R.id.nav_view_list), mCardLoader);
        mCardSearcher.setCallBack(this);

        tv_deck = layoutView.findViewById(R.id.tv_deck);
        tv_result_count = layoutView.findViewById(R.id.result_count);

        mLimitSpinner = layoutView.findViewById(R.id.sp_limit_list);
        mLimitSpinner.setPopupBackgroundResource(R.color.colorNavy);
        mRecyclerView = layoutView.findViewById(R.id.grid_cards);
        mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(), 0, mRecyclerView.getPaddingRight(), mRecyclerView.getPaddingBottom());
        mRecyclerView.setAdapter((mDeckAdapater = new DeckAdapater(getContext(), mRecyclerView, activity.getImageLoader())));
        mRecyclerView.setLayoutManager(new DeckLayoutManager(getContext(), Constants.DECK_WIDTH_COUNT));

        mDeckItemTouchHelper = new DeckItemTouchHelper(mDeckAdapater);
        ItemTouchHelperPlus touchHelper = new ItemTouchHelperPlus(getContext(), mDeckItemTouchHelper);
        touchHelper.setItemDragListener(this);
        touchHelper.setEnableClickDrag(Constants.DECK_SINGLE_PRESS_DRAG);
        touchHelper.attachToRecyclerView(mRecyclerView);

        mRecyclerView.addOnItemTouchListener(new RecyclerViewItemListener(mRecyclerView, this));

        initBoomMenuButton(layoutView.findViewById(R.id.bmb));
        layoutView.findViewById(R.id.btn_nav_search).setOnClickListener((v) -> doMenu(R.id.action_search));
        layoutView.findViewById(R.id.btn_nav_list).setOnClickListener((v) -> doMenu(R.id.action_card_list));
        //只有当加载的是具有id的deck时才显示点赞按钮
        tv_add_1 = layoutView.findViewById(R.id.tv_add_1);
        ll_click_like = layoutView.findViewById(R.id.ll_click_like);
        ll_click_like.setOnClickListener(v -> {
            if (mDeckId != null) {
                VUiKit.defer().when(() -> {
                    BasicResponse result = DeckSquareApiUtil.likeDeck(mDeckId);
                    return result;
                }).fail(e -> {
                    LogUtil.i(TAG, "Like deck fail" + e.getMessage());
                    YGOUtil.showTextToast("点赞失败");
                }).done(data -> {
                    if (data != null && data.getMessage() != null && data.getMessage().equals("true")) {
                        // 显示点赞动画
                        tv_add_1.setText("+1");
                        ll_click_like.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.fade_out));
                        ll_click_like.setVisibility(View.GONE);
                    } else {
                        YGOUtil.showTextToast(data != null ? data.getMessage() : "点赞失败");
                    }
                    mDeckId = null;
                });
            } else {
                ll_click_like.setVisibility(View.GONE);
            }
        });
        tv_deck.setOnClickListener(v -> {
            new DeckManageDialog(this).show(
                    getActivity().getSupportFragmentManager(), "pagerDialog");
        });
        //  YGODeckDialogUtil.dialogDeckSelect(getActivity(), AppsSettings.get().getLastDeckPath(), this));
        mContext = (BaseActivity) getActivity();
        /** 自动同步 */
        if (SharedPreferenceUtil.getServerToken() != null) {
            VUiKit.defer().when(() -> {
                try {
                    DeckSquareApiUtil.synchronizeDecks();
                } catch (IOException e) {
                    return e;
                }
                return 0;
            }).fail((e) -> {
                LogUtil.e(TAG, "Sync decks failed: " + e);
                //YGOUtil.showTextToast("Sync decks failed: " + e);
            }).done((result) -> {
            });
        }
    }


    /**
     * 外部调用fragment时，如果通过setArguments(mBundle)方法设置了ydk文件路径，则直接打开该ydk文件
     * 将mPreLoadFile设置为对应的File
     */
    public void preLoadFile() {
        String preLoadFilePath = "";
        if (getArguments() != null) {
            preLoadFilePath = getArguments().getString("setDeck");
            getArguments().clear();
        }
        preLoadFile(preLoadFilePath);

    }

    /**
     * 传入外部ydk文件的路径，临时在本页面中打开该ydk的内容，用于后续的保存
     *
     * @param preLoadFilePath 外部ydk文件的路径
     */
    public void preLoadFile(String preLoadFilePath) {

        final File _file;
        //打开指定卡组
        if (!TextUtils.isEmpty(preLoadFilePath) && (mPreLoadFile = new File(preLoadFilePath)).exists()) {
            //外面卡组
            _file = mPreLoadFile;
        } else {
            mPreLoadFile = null;
            String path = mSettings.getLastDeckPath();
            if (TextUtils.isEmpty(path)) {
                _file = null;
            } else {
                //最后卡组
                _file = new File(path);
            }
        }
        File oriDeckFiles = new File(ORI_DECK);
        File deckFiles = new File(AppsSettings.get().getDeckDir());
        if (oriDeckFiles.exists() && deckFiles.list().length <= 1) {
            mContext.startPermissionsActivity();
        }
        init(_file);
    }

    protected void setListeners() {
        mCardListAdapter.setOnItemClickListener((adapter, view, position) -> {
            onCardClick(view, mCardListAdapter.getItem(position), position);
        });
        mCardListAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            onCardLongClick(view, mCardListAdapter.getItem(position), position);
            return true;
        });

        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_IDLE:
                    case RecyclerView.SCROLL_STATE_SETTLING:
                        GlideCompat.with(getContext()).resumeRequests();
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        GlideCompat.with(getContext()).pauseRequests();
                        break;
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onFirstUserVisible() {
    }

    @Override
    public void onUserVisible() {
    }

    @Override
    public void onFirstUserInvisible() {
    }

    @Override
    public void onUserInvisible() {
    }

    @Override
    public void onBackHome() {

    }

    @Override
    public void onStop() {
        super.onStop();
        CardFavorites.get().save();
    }

    @Override
    public void onDestroy() {
        //mImageLoader.close();
        super.onDestroy();
    }

    @Override
    public void onResetSearch() {

    }

    /**
     * 在此处处理卡组中卡片的长按删除
     *
     * @param pos
     */
    @Override
    public void onDragLongPress(int pos) {
        if (pos < 0) return;
        if (Constants.DEBUG)
            Log.d(TAG, "delete " + pos);
        if (mSettings.isDialogDelete()) {
            DeckItem deckItem = mDeckAdapater.getItem(pos);
            if (deckItem == null || deckItem.getCardInfo() == null) {
                return;
            }
            DialogPlus dialogPlus = new DialogPlus(getContext());
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

    public void AnimationShake2(View view) {
        Animation shake = AnimationUtils.loadAnimation(getContext(), R.anim.shake);//加载动画资源文件
        view.findViewById(R.id.cube2).startAnimation(shake); //给组件播放动画效果
    }

    public void hideDrawers() {
        if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
        }
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
        }
    }

    protected void showSearch(boolean autoClose) {
        if (mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
        }
        if (autoClose && mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (isLoad) {
            mDrawerLayout.openDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
    }

    protected void showResult(boolean autoClose) {
        if (mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
        if (autoClose && mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
            //showNewbieGuide("searchResult");
        } else if (isLoad) {
            mDrawerLayout.openDrawer(Constants.CARD_RESULT_GRAVITY);
        }
    }


    //region load deck
    //从文件file中读取deck
    private void loadDeckFromFile(File file) {
        if (!mCardLoader.isOpen() || file == null || !file.exists()) {
            setCurDeck(new DeckInfo(), false);
            return;
        }
        DialogPlus dlg = DialogPlus.show(getContext(), null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            if (mCardLoader.isOpen() && file.exists()) {
                return mDeckAdapater.read(mCardLoader, file, mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            dlg.dismiss();
            //setCurDeck(rs, file.getParent().equals(mSettings.getPackDeckDir()) || file.getParent().equals(mSettings.getCacheDeckDir()));
            setCurDeck(rs, file.getParent().equals(mSettings.getPackDeckDir()));
        });
    }

    //region init
    public void init(@Nullable File ydk) {
        DialogPlus dlg = DialogPlus.show(mContext, null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            DataManager.get().load(true);
            //默认第一个卡表
            if (activity.getmLimitManager().getCount() > 0) {
                mCardLoader.setLimitList(activity.getmLimitManager().getTopLimit());
            }
            File file = ydk;
            if (file == null || !file.exists()) {
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
            Log.i(TAG, "load ydk " + file);
            if (mCardLoader.isOpen() && file.exists()) {
                return mDeckAdapater.read(mCardLoader, file, mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            isLoad = true;
            dlg.dismiss();
            mCardSearcher.initItems();
            initLimitListSpinners(mLimitSpinner, mCardLoader.getLimitList());
            //设置当前卡组
            if (rs.source != null) {
                setCurDeck(rs, rs.source.getParent().equals(mSettings.getPackDeckDir()));
            } else {
                setCurDeck(rs, false);
            }
            //设置收藏夹
            mCardSearcher.showFavorites(false);
        });
    }

    /**
     * 用户选中某个卡组后，更新当前界面，显示已选中的卡组。包括更新界面显示（tv_deck）、更新AppsSettings、通知DeckAdapter
     */
    private void setCurDeck(DeckInfo deckInfo, boolean isPack) {
        if (deckInfo == null) {
            deckInfo = new DeckInfo();
        }
        File file = deckInfo.source;
        if (file != null && file.exists()) {
            String name = IOUtils.tirmName(file.getName(), Constants.YDK_FILE_EX);
            mSettings.setLastDeckPath(file.getAbsolutePath());
            tv_deck.setText(name);
        }
        mDeckAdapater.setDeck(deckInfo, isPack);
        mDeckAdapater.notifyDataSetChanged();
    }

    private boolean inDeckDir(File file) {
        String deck = new File(AppsSettings.get().getDeckDir()).getAbsolutePath();
        return TextUtils.equals(deck, file.getParent());
    }

    @Override
    public void onSearchStart() {
        hideDrawers();
    }

    protected void onCardClick(View view, Card cardInfo, int pos) {
        if (mCardListAdapter.isShowMenu(view)) {
            return;
        }
        if (cardInfo != null) {
            showCardDialog(mCardListAdapter, cardInfo, pos);
        }
    }

    protected void onCardLongClick(View view, Card cardInfo, int pos) {
        //  mCardListAdapater.showMenu(view);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCardInfoEvent(CardInfoEvent event) {
        int pos = event.position;
        Card cardInfo = mCardListAdapter.getItem(pos);
        if (cardInfo == null) {
            mCardListAdapter.hideMenu(null);
        } else if (event.toMain) {
            if (!addMainCard(cardInfo)) {// || !checkLimit(cardInfo, false)) {
                mCardListAdapter.hideMenu(null);
            }
        } else {
            if (!addSideCard(cardInfo)) {// || !checkLimit(cardInfo, false)) {
                mCardListAdapter.hideMenu(null);
            }
        }
    }

    @Override
    public void onSearchResult(List<Card> cardInfos, boolean isHide) {
        //super.onSearchResult(cardInfos, isHide);
        tv_result_count.setText(String.valueOf(cardInfos.size()));
        mCardListAdapter.set(cardInfos);
        mCardListAdapter.notifyDataSetChanged();
        if (cardInfos != null && cardInfos.size() > 0) {
            mListView.smoothScrollToPosition(0);
        }
        if (!isHide)
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
        return mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)
                || mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY);
    }

    private boolean isShowCard() {
        return mDialog != null && mDialog.isShowing();
    }

    protected void showCardDialog(CardListProvider provider, Card cardInfo, int pos) {
        if (cardInfo != null) {
            if (isShowCard()) return;
            if (mCardDetail == null) {
                mCardDetail = new CardDetail((BaseActivity) getActivity(), activity.getImageLoader(), activity.getStringManager());
                mCardDetail.setOnCardClickListener(new CardDetail.OnDeckManagerCardClickListener() {
                    @Override
                    public void onOpenUrl(Card cardInfo) {
                        WebActivity.openFAQ(getContext(), cardInfo);
                    }

                    @Override
                    public void onImageUpdate(Card cardInfo) {
                        mDeckAdapater.notifyItemChanged(cardInfo);
                        mCardListAdapter.notifyItemChanged(cardInfo);
                    }

                    @Override
                    public void onSearchKeyWord(String keyword) {
                        showSearchKeyWord(keyword);//根据关键词搜索
                    }

                    @Override
                    public void onShowCardList(List<Card> cardList, boolean sort) {
                        showCardList(cardList, sort);//卡包展示不排序，其他情况排序
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
                mCardDetail.setCallBack((card, favorite) -> {
                    if (mCardSearcher.isShowFavorite()) {
                        mCardSearcher.showFavorites(false);
                    }
                });
            }
            mCardDetail.showAdd();
            if (mDialog == null) {
                mDialog = new DialogPlus(getContext());
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

    private void showSearchKeyWord(String keyword) {//使用此方法，可以适用关键词查询逻辑，让完全符合关键词的卡名置顶显示，并同时搜索字段和效果文本
        CardSearchInfo searchInfo = new CardSearchInfo.Builder().keyword(keyword).types(new long[]{}).build();//构建CardSearchInfo时type不能为null
        mCardLoader.search(searchInfo);
    }

    private void showCardList(List<Card> cardList, boolean sort) {
        if (!cardList.isEmpty()) {
            onSearchResult(sort ? mCardLoader.sort(cardList) : cardList, false);//根据情况不同，判断是否调用CardLoader的sort方法排序List<Card>
        } else {
            Log.w("cc", "No card found");
        }
    }

    private boolean addSideCard(Card cardInfo) {
        if (checkLimit(cardInfo)) {
            boolean rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.SideCard);
            if (rs) {
                YGOUtil.showTextToast(R.string.add_card_tip_ok);
            } else {
                YGOUtil.showTextToast(R.string.add_card_tip_fail);
            }
            return rs;
        }
        return false;
    }

    private boolean addMainCard(Card cardInfo) {
        if (checkLimit(cardInfo)) {
            boolean rs;
            if (cardInfo.isExtraCard()) {
                rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.ExtraCard);
            } else {
                rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.MainCard);
            }
            if (rs) {
                YGOUtil.showTextToast(R.string.add_card_tip_ok);
            } else {
                YGOUtil.showTextToast(R.string.add_card_tip_fail);
            }
            return rs;
        }
        return false;
    }

    public void askBeforeQuit() {
        File ydk = mDeckAdapater.getYdkFile();
        if (ydk != null && ydk.exists()) {
            DialogPlus builder = new DialogPlus(getContext());
            builder.setTitle(R.string.question);
            builder.setMessage(R.string.quit_deck_tip);
            builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            builder.setLeftButtonListener((dlg, s) -> {
                getActivity().finish();
                dlg.dismiss();
            });
            builder.setRightButtonListener((dlg, s) -> {
                dlg.dismiss();
            });
            builder.show();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
        } else if (mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (mDeckAdapater.isChanged()) {
            askBeforeQuit();
        } else {
            //与home相同双击返回
            if (System.currentTimeMillis() - exitLasttime <= 3000) {
                return false;
            } else {
                exitLasttime = System.currentTimeMillis();
                YGOUtil.showTextToast(R.string.back_tip);
            }
        }
        return true;
    }

    private boolean checkLimit(Card cardInfo) {
        SparseArray<Integer> mCount = mDeckAdapater.getCardCount();
        LimitList limitList = mDeckAdapater.getLimitList();
        int id = cardInfo.getGameCode();
        Integer count = mCount.get(id);
        if (limitList == null) {
            return count != null && count <= 3;
        }
        if (limitList.check(cardInfo, LimitType.Forbidden)) {
            YGOUtil.showTextToast(getString(R.string.tip_card_max, 0));
            return false;
        }
        if (count != null) {
            if (limitList.check(cardInfo, LimitType.Limit)) {
                if (count >= 1) {
                    YGOUtil.showTextToast(getString(R.string.tip_card_max, 1));
                    return false;
                }
            } else if (limitList.check(cardInfo, LimitType.SemiLimit)) {
                if (count >= 2) {
                    YGOUtil.showTextToast(getString(R.string.tip_card_max, 2));
                    return false;
                }
            } else if (count >= Constants.CARD_MAX_COUNT) {
                YGOUtil.showTextToast(getString(R.string.tip_card_max, 3));
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
        DialogPlus builder = new DialogPlus(getContext());
        switch (menuId) {
            case R.id.action_deck_backup_n_restore:
                mContext.startPermissionsActivity();
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.deck_explain);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonText(R.string.deck_restore);
                builder.setRightButtonText(R.string.deck_back_up);
                builder.setRightButtonListener((dialog, which) -> {
                    dialog.dismiss();
                    doBackUpDeck();
                });
                builder.setLeftButtonListener((dialog, which) -> {
                    dialog.dismiss();
                    doRestoreDeck();
                });
                builder.show();
                break;
            case R.id.action_search:
                //弹条件对话框
                showSearch(true);
                break;
            case R.id.action_card_list:
                showResult(true);
                break;
            case R.id.action_share_deck:
                if (mDeckAdapater.getYdkFile() == null) {
                    YGOUtil.showTextToast(R.string.unable_to_edit_empty_deck);
                    return true;
                }
                shareDeck();
                break;
            case R.id.action_save://如果是通过“预加载”打开ydk，则将ydk保存到
                if (mPreLoadFile != null && mPreLoadFile == mDeckAdapater.getYdkFile()) {//代表通过预加载功能打开的ydk
                    //需要保存到deck文件夹
                    inputDeckName(mPreLoadFile, null, true);
                } else {
                    if (mDeckAdapater.getYdkFile() == null) {
                        inputDeckName(null, null, true);
                    } else {
                        if (TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getAiDeckDir()) ||
                                TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getPackDeckDir())) {
                            YGOUtil.showTextToast(R.string.donot_edit_Deck);
                        } else {
                            save(mDeckAdapater.getYdkFile());
                        }
                    }
                }
                break;
            case R.id.action_rename:
                if (mDeckAdapater.getYdkFile() == null) {
                    YGOUtil.showTextToast(R.string.unable_to_edit_empty_deck);
                    return true;
                }
                if (TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getAiDeckDir()) ||
                        TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getPackDeckDir())) {
                    YGOUtil.showTextToast(R.string.donot_edit_Deck);
                } else {
                    inputDeckName(mDeckAdapater.getYdkFile(), mDeckAdapater.getYdkFile().getParent(), false);
                }
                break;
            case R.id.action_deck_new:
                createDeck(null);

                break;
            case R.id.action_clear_deck: {
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.question_clear_deck);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonListener((dlg, rs) -> {
                    mDeckAdapater.setDeck(new DeckInfo(), false);
                    mDeckAdapater.notifyDataSetChanged();
                    dlg.dismiss();
                });
                builder.show();
            }
            break;
            case R.id.action_delete_deck: {
                if (mDeckAdapater.getYdkFile() == null) {
                    YGOUtil.showTextToast(R.string.unable_to_edit_empty_deck);
                    return true;
                }
                if (TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getAiDeckDir()) ||
                        TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getPackDeckDir())) {
                    YGOUtil.showTextToast(R.string.donot_edit_Deck);
                } else {
                    builder.setTitle(R.string.question);
                    builder.setMessage(R.string.question_delete_deck);
                    builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                    builder.setLeftButtonListener((dlg, rs) -> {
                        if (mDeckAdapater.getYdkFile() != null) {
                            FileUtils.deleteFile(mDeckAdapater.getYdkFile());
                            //统一调用批量删除在线卡组（这里只有1个）
                            List<DeckFile> deckFileList = new ArrayList<>();
                            deckFileList.add(new DeckFile(mDeckAdapater.getYdkFile()));

                            onDeckDel(deckFileList);

                            YGOUtil.showTextToast(R.string.done);
                            dlg.dismiss();
                            File file = getFirstYdk();
                            loadDeckFromFile(file);
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
        DialogPlus builder = new DialogPlus(getContext());
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
            setCurDeck(null, false);
            inputDeckName(null, savePath, true);
        });
        builder.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
            setCurDeck(null, false);
            inputDeckName(null, savePath, true);
        });
        builder.show();
    }

    private File getFirstYdk() {
        List<File> files = getYdkFiles();
        return files == null || files.size() == 0 ? null : files.get(0);
    }

    private void shareDeck() {
        builderShareLoading = new DialogPlus(getContext());
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
        Deck deck = mDeckAdapater.toDeck(mDeckAdapater.getYdkFile());
        if (deck.getDeckCount() == 0) {
            builderShareLoading.dismiss();
            YGOUtil.showTextToast("卡组中没有卡片");
            return;
        }

        //延时半秒，使整体看起来更流畅
        new Handler().postDelayed(() -> shareDeck1(deck), 500);
    }

    private void shareDeck1(Deck deck) {
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


        String deckName = deck.getName();
        int end = deckName.lastIndexOf(".");
        if (end != -1) {
            deckName = deckName.substring(0, end);
        }
        String savePath = new File(AppsSettings.get().getDeckSharePath(), deckName + ".jpg").getAbsolutePath();
        BitmapUtil.saveBitmap(bitmap, savePath, 50);
        builderShareLoading.dismiss();
        DialogUtils du = DialogUtils.getdx(getContext());
        View viewDialog = du.dialogBottomSheet(R.layout.dialog_deck_share, true);
        ImageView iv_image = viewDialog.findViewById(R.id.iv_image);
        Button bt_image_share = viewDialog.findViewById(R.id.bt_image_share);
        Button bt_code_share = viewDialog.findViewById(R.id.bt_code_share);
        TextView tv_code = viewDialog.findViewById(R.id.et_code);
        tv_code.setText(deck.toUri().toString());
        ImageUtil.show(getContext(), savePath, iv_image, System.currentTimeMillis() + "");

        bt_code_share.setOnClickListener(v -> {
            du.dis();
            YGOUtil.copyMessage(getContext(), tv_code.getText().toString().trim());
            DuelAssistantManagement.getInstance().setLastMessage(tv_code.getText().toString().trim());
            YGOUtil.showTextToast(R.string.deck_text_copyed);
        });

        bt_image_share.setOnClickListener(v -> {
            du.dis();
            String category = mDeckAdapater.getYdkFile().getParent();
            String fname = deck.getName();
            Intent intent = new Intent(ACTION_SHARE_FILE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(IrrlichtBridge.EXTRA_SHARE_TYPE, "ydk");
            if (TextUtils.equals(category, mSettings.getDeckDir())) {
                intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, fname);
            } else {
                String cname = DeckUtil.getDeckTypeName(mDeckAdapater.getYdkFile().getAbsolutePath());
                intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, cname + "/" + fname);
            }
            intent.setPackage(getActivity().getPackageName());
            try {
                startActivity(intent);
            } catch (Throwable e) {
                YGOUtil.showTextToast("dev error:not found activity." + e);
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
        ShareUtil.shareText(getContext(), getString(R.string.share_deck), url, null);
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(CLIPBOARD_SERVICE);
        if (Build.VERSION.SDK_INT > 19) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(label, uri));
        } else {
            clipboardManager.setText(uri);
        }
        YGOUtil.showTextToast(R.string.copy_to_clipbroad);
    }

    private File getSelectDeck(Spinner spinner) {
        Object o = SimpleSpinnerAdapter.getSelectTag(spinner);
        if (o instanceof File) {
            return (File) o;
        }
        return null;
    }

    //从存储卡组的文件夹中获取所有名字以.ydk结尾的文件，并将mPreLoadFile也加入到返回结果中
    private List<File> getYdkFiles() {
        File dir = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> s.toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX));
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

    private void initLimitListSpinners(Spinner spinner, LimitList cur) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        List<String> limitLists = activity.getmLimitManager().getLimitNames();
        int index = -1;
        int count = activity.getmLimitManager().getCount();
        items.add(new SimpleSpinnerItem(0, getString(R.string.label_limitlist)));
        for (int i = 0; i < count; i++) {
            int j = i + 1;
            String name = limitLists.get(i);
            items.add(new SimpleSpinnerItem(j, name));
            if (cur != null && TextUtils.equals(cur.getName(), name)) {
                index = j;
            }
        }
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(getContext());
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);
        if (index >= 0) {
            spinner.setSelection(index);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setLimitList(activity.getmLimitManager().getLimit(SimpleSpinnerAdapter.getSelectText(spinner)));
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
            getActivity().runOnUiThread(() -> {
                mDeckAdapater.notifyItemRangeChanged(DeckItem.MainStart, DeckItem.MainEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.ExtraStart, DeckItem.ExtraEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.SideStart, DeckItem.SideEnd);
            });
        }
        mCardListAdapter.setLimitList(limitList);
        getActivity().runOnUiThread(() -> mCardListAdapter.notifyDataSetChanged());
    }

    private void inputDeckName(File oldYdk, String savePath, boolean keepOld) {
        DialogPlus builder = new DialogPlus(getContext());
        builder.setTitle(R.string.intpu_name);
        EditText editText = new EditText(getContext());
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setSingleLine();
        if (oldYdk != null) {
            editText.setText(oldYdk.getName());
        }
        builder.setContentView(editText);
        builder.setOnCloseLinster(DialogInterface::dismiss);
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
                    YGOUtil.showTextToast(R.string.file_exist);
                    return;
                }
                if (!keepOld && oldYdk != null && oldYdk.exists()) {
                    if (oldYdk.renameTo(ydk)) {
                        dlg.dismiss();
                        loadDeckFromFile(ydk);
                    }
                } else {
                    if (oldYdk == mPreLoadFile) {
                        mPreLoadFile = null;
                    }
                    dlg.dismiss();
                    try {
                        boolean ret = ydk.createNewFile();
                    } catch (Throwable ignore) {
                    }
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
            YGOUtil.showTextToast(R.string.save_tip_ok);
        } else {
            YGOUtil.showTextToast(R.string.save_tip_fail);
        }
    }


    private void initBoomMenuButton(BoomMenuButton menu) {
        final SparseArray<Integer> mMenuIds = new SparseArray<>();
        addMenuButton(mMenuIds, menu, R.id.action_share_deck, R.string.share_deck, R.drawable.shareicon);
        addMenuButton(mMenuIds, menu, R.id.action_save, R.string.save_deck, R.drawable.save);
        addMenuButton(mMenuIds, menu, R.id.action_clear_deck, R.string.clear_deck, R.drawable.clear_deck);

        addMenuButton(mMenuIds, menu, R.id.action_deck_new, R.string.new_deck, R.drawable.add);
        addMenuButton(mMenuIds, menu, R.id.action_rename, R.string.rename_deck, R.drawable.rename);
        addMenuButton(mMenuIds, menu, R.id.action_delete_deck, R.string.delete_deck, R.drawable.delete);

        addMenuButton(mMenuIds, menu, R.id.action_unsort, R.string.unsort, R.drawable.unsort);
        addMenuButton(mMenuIds, menu, R.id.action_sort, R.string.sort, R.drawable.sort);
        addMenuButton(mMenuIds, menu, R.id.action_deck_backup_n_restore, R.string.deck_backup_n_restore, R.drawable.downloadimages);

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

    private void doBackUpDeck() {
        FileUtils.delFile(ORI_DECK);//备份前删除原备份
        try {
            FileUtils.copyDir(mSettings.getDeckDir(), ORI_DECK, true);
            File ydks = new File(ORI_DECK);
            File[] subYdks = ydks.listFiles();
            for (File files : subYdks) {
                if (files.getName().contains("-") && files.getName().contains(" new cards"))
                    files.delete();
            }
        } catch (Throwable e) {
            YGOUtil.showTextToast(e + "");
        }
        YGOUtil.showTextToast(R.string.done);
    }

    private void doRestoreDeck() {
        try {
            FileUtils.copyDir(ORI_DECK, mSettings.getDeckDir(), false);
        } catch (Throwable e) {
            YGOUtil.showTextToast(e + "");
        }
        YGOUtil.showTextToast(R.string.done);
    }

    public static List<MyOnlineDeckDetail> getOriginalData() {
        return originalData;
    }

    //在卡组选择的dialog中点击某个卡组（来自本地或服务器）后，dialog通过本回调函数通知本页面。
    //在本页面中根据卡组来源（本地或服务器）显示卡组内容
    @Override
    public void onDeckSelect(DeckFile deckFile) {
        if (!deckFile.isLocal()) {//不在本地，在云上（卡组广场中或用户的云上）
            VUiKit.defer().when(() -> {
                DownloadDeckResponse response = DeckSquareApiUtil.getDeckById(deckFile.getDeckId());
                if (response != null) {
                    return response.getData();
                } else {
                    return null;
                }
            }).fail((e) -> {
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }).done((deckData) -> {
                if (deckData != null) {
                    mDeckId = deckData.getDeckId();
                    LogUtil.i(TAG,deckData.toString());
                    String fileFullName = deckData.getDeckName() + YDK_FILE_EX;
                    File dir = new File(getActivity().getApplicationInfo().dataDir, "cache");
                    //将卡组存到cache缓存目录中
                    boolean result = DeckSquareFileUtil.saveFileToPath(dir.getPath(), fileFullName, deckData.getDeckYdk(), deckData.getDeckUpdateDate());
                    if (result) {//存储成功，使用预加载功能
                        LogUtil.i(TAG, "square deck detail done");
                        preLoadFile(dir.getPath() + "/" + fileFullName);
                        tv_add_1.setText(R.string.like_deck_thumb);
                        ll_click_like.setVisibility(View.VISIBLE);
                    }
                }
            });

        } else {
            loadDeckFromFile(deckFile.getPathFile());
            ll_click_like.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDeckDel(List<DeckFile> deckFileList) {
        File deck = mDeckAdapater.getYdkFile();
        if (deck == null)
            return;
        String currentDeckPath = deck.getAbsolutePath();
        for (DeckFile deckFile : deckFileList) {
            if (TextUtils.equals(deckFile.getPath(), currentDeckPath)) {
                List<File> files = getYdkFiles();
                File file = null;
                if (files != null && files.size() > 0) {
                    file = files.get(0);
                }
                if (file != null) {
                    loadDeckFromFile(file);
                } else {
                    setCurDeck(new DeckInfo(), false);
                }
                return;
            }
        }
        //删除在线的同名卡组们
        DeckSquareApiUtil.deleteDecks(deckFileList);
        YGOUtil.showTextToast(R.string.done);
    }

    @Override
    public void onDeckMove(List<DeckFile> deckFileList, DeckType toDeckType) {
        File ydk = mDeckAdapater.getYdkFile();
        if (ydk == null) {
            return;
        }
        String currentDeckPath = ydk.getPath();
        for (DeckFile deckFile : deckFileList) {
            if (TextUtils.equals(currentDeckPath, deckFile.getPath())) {
                loadDeckFromFile(new File(toDeckType.getPath(), deckFile.getFileName()));
                return;
            }
        }
    }

    @Override
    public void onDeckCopy(List<DeckFile> deckFileList, DeckType toDeckType) {
        File ydk = mDeckAdapater.getYdkFile();
        if (ydk == null) {
            return;
        }
        String currentDeckPath = ydk.getPath();
        for (DeckFile deckFile : deckFileList) {
            if (TextUtils.equals(currentDeckPath, deckFile.getPath())) {
                loadDeckFromFile(new File(toDeckType.getPath(), deckFile.getFileName()));
                return;
            }
        }
    }

    @Override
    public void onDeckNew(DeckType currentDeckType) {
        createDeck(currentDeckType.getPath());
    }

    //region card edit
    @Override
    public void onLimitListChanged(LimitList limitList) {

    }

    @Override
    public void onDragStart() {

    }

    @Override
    public void onDragEnd() {
    }

    //https://www.jianshu.com/p/99649af3b191
    public void showNewbieGuide(String scene) {
        HighlightOptions options = new HighlightOptions.Builder()//绘制一个高亮虚线圈
                .setOnHighlightDrewListener((canvas, rectF) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(20);
                    paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                    canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2 + 10, paint);
                }).build();
        HighlightOptions options2 = new HighlightOptions.Builder()//绘制一个高亮虚线矩形
                .setOnHighlightDrewListener((canvas, rectF) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(20);
                    paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                    canvas.drawRect(rectF, paint);
                }).build();
        if (scene.equals("deckmain")) {
            NewbieGuide.with(this)//with方法可以传入Activity或者Fragment，获取引导页的依附者
                    .setLabel("deckmainGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.deck_menu), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        //可只创建一个引导layout并把相关内容都放在其中并GONE，获得ID并初始化相应为显示
                                        view.findViewById(R.id.view_abt_bmb).setVisibility(View.VISIBLE);
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.nav_search), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_about);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_button_search);
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.nav_list), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_about);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_button_search_result);
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.tv_deckmanger), HighLight.Shape.CIRCLE, options2)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_about);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_view_deck_manager);
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(new RectF(screenWidth / 10.0f, screenWidth / 20.0f, screenWidth / 5.0f, screenWidth / 20.0f + screenWidth / 10.0f * 254.0f / 177.0f), HighLight.Shape.RECTANGLE, options2)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_abt_mid);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_view_move_card);
                                    })
                    )
                    //.alwaysShow(true)//总是显示，调试时可以打开
                    .show();

        }
    }
}

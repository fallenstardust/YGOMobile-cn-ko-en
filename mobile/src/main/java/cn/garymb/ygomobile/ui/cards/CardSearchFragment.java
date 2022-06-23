package cn.garymb.ygomobile.ui.cards;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.RecyclerViewItemListener;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.hubert.guide.NewbieGuide;
import com.app.hubert.guide.model.GuidePage;
import com.app.hubert.guide.model.HighLight;
import com.app.hubert.guide.model.HighlightOptions;
import com.ourygo.assistant.util.DuelAssistantManagement;

import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import cn.garymb.ygomobile.ui.plus.AOnGestureListener;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;

public class CardSearchFragment extends BaseFragemnt implements CardLoader.CallBack, CardSearcher.CallBack {
    public static final String SEARCH_MESSAGE = "searchMessage";
    protected DrawerLayout mDrawerlayout;
    protected CardSearcher mCardSelector;
    protected CardListAdapter mCardListAdapter;
    protected CardLoader mCardLoader;
    protected boolean isLoad = false;
    protected StringManager mStringManager = DataManager.get().getStringManager();
    protected LimitManager mLimitManager = DataManager.get().getLimitManager();
    private RecyclerView mListView;
    private ImageLoader mImageLoader;

    private String intentSearchMessage;
    private boolean isInitCdbOk = false;
    private String currentCardSearchMessage = "";
    private DuelAssistantManagement duelAssistantManagement;
    private CardDetail mCardDetail;
    private DialogPlus mDialog;
    private TextView mResult_count;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View layoutView;
        layoutView = inflater.inflate(R.layout.fragment_search, container, false);
        initView(layoutView);
        //showNewbieGuide();
        return layoutView;
    }

    public void initView(View layoutView){
        duelAssistantManagement = DuelAssistantManagement.getInstance();
        intentSearchMessage = getActivity().getIntent().getStringExtra(CardSearchFragment.SEARCH_MESSAGE);
        mResult_count = layoutView.findViewById(R.id.search_result_count);
        mDrawerlayout = layoutView.findViewById(R.id.drawer_layout);
        mListView = layoutView.findViewById(R.id.list_cards);
        mImageLoader = new ImageLoader(true);
        mCardListAdapter = new CardListAdapter(getContext(), mImageLoader);
        mCardListAdapter.setItemBg(true);
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        mListView.setAdapter(mCardListAdapter);
        Button btn_search = layoutView.findViewById(R.id.btn_search);
        btn_search.setOnClickListener((v) -> showSearch(true));
        mCardLoader = new CardLoader(getContext());
        mCardLoader.setCallBack(this);
        mCardSelector = new CardSearcher(layoutView.findViewById(R.id.nav_view_list), mCardLoader);
        mCardSelector.setCallBack(this);
        setListeners();
        DialogPlus dlg = DialogPlus.show(getContext(), null, getString(R.string.loading));
        VUiKit.defer().when(() -> {
            DataManager.get().load(true);
            if (mLimitManager.getCount() > 0) {
                mCardLoader.setLimitList(mLimitManager.getTopLimit());
            }
        }).fail((e)->{
            Toast.makeText(getContext(), R.string.tip_load_cdb_error, Toast.LENGTH_SHORT).show();
            Log.e(IrrlichtBridge.TAG, "load cdb", e);
        }).done((rs) -> {
            dlg.dismiss();
            isLoad = true;
            mCardLoader.loadData();
            mCardSelector.initItems();
            //数据库初始化完毕后搜索被传入的关键字
            intentSearch(intentSearchMessage);
            isInitCdbOk = true;
        });
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

    private void intentSearch(String searchMessage) {
        //如果要求搜索的关键字为空，就搜索决斗助手保存的卡查关键字
        if (TextUtils.isEmpty(searchMessage)) {
            currentCardSearchMessage = duelAssistantManagement.getCardSearchMessage();
        } else {
            currentCardSearchMessage = searchMessage;
        }
        //卡查关键字为空不卡查
        if (TextUtils.isEmpty(currentCardSearchMessage))
            return;
        mCardSelector.search(currentCardSearchMessage);
    }

    protected void setListeners() {
        mListView.addOnItemTouchListener(new RecyclerViewItemListener(mListView, new RecyclerViewItemListener.OnItemListener() {
            @Override
            public void onItemClick(View view, int pos) {
                onCardClick(pos, mCardListAdapter);
            }

            @Override
            public void onItemLongClick(View view, int pos) {
                onCardLongClick(view, pos);
            }

            @Override
            public void onItemDoubleClick(View view, int pos) {

            }
        }));
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

    private void onBack() {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
            return;
        }
        if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
            return;
        }
    }

    @Override
    public void onDestroy() {
        mImageLoader.close();
        super.onDestroy();
    }

    @Override
    public void onSearchResult(List<Card> cardInfos, boolean isHide) {
//        Log.d("kk", "find " + (cardInfos == null ? -1 : cardInfos.size()));
        mCardListAdapter.set(cardInfos);
        mResult_count.setText(String.valueOf(cardInfos.size()));
        mCardListAdapter.notifyDataSetChanged();
        if (cardInfos.size() > 0) {
            mListView.smoothScrollToPosition(0);
        }
    }

    @Override
    public void onResetSearch() {

    }

    private boolean isShowDrawer() {
        return mDrawerlayout.isDrawerOpen(Gravity.LEFT)
                || mDrawerlayout.isDrawerOpen(Gravity.RIGHT);
    }

    @Override
    public void onSearchStart() {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
    }

    @Override
    public void onLimitListChanged(LimitList limitList) {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
        mCardListAdapter.setLimitList(limitList);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_search) {//弹条件对话框
            showSearch(true);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackHome() {
        onBack();
    }

    @Override
    public void onBackPressed() {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else {
            //super.onBackPressed();
        }
    }

    protected void onCardClick(int pos, CardListProvider clt) {
        if (isShowDrawer()) return;
        showCard(clt, clt.getCard(pos), pos);
    }

    protected void onCardLongClick(View view, int pos) {

    }

    private boolean isShowCard() {
        return mDialog != null && mDialog.isShowing();
    }

    protected void showCard(CardListProvider provider, Card cardInfo, final int position) {
        if (cardInfo != null) {
            if (mCardDetail == null) {
                mCardDetail = new CardDetail((BaseActivity) getActivity(), mImageLoader, mStringManager);
                mCardDetail.setCallBack((card, favorite) -> {
                    if(mCardSelector.isShowFavorite()) {
                        mCardSelector.showFavorites(false);
                    }
                });
                mCardDetail.setOnCardClickListener(new CardDetail.DefaultOnCardClickListener() {
                    @Override
                    public void onOpenUrl(Card cardInfo) {
                        WebActivity.openFAQ(getContext(), cardInfo);
                    }

                    @Override
                    public void onImageUpdate(Card cardInfo) {
                        mCardListAdapter.notifyItemChanged(cardInfo);
                    }

                    @Override
                    public void onClose() {
                        mDialog.dismiss();
                    }
                });
            }
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
            mCardDetail.bind(cardInfo, position, provider);
        }
    }

    protected void showSearch(boolean autoclose) {
        if (autoclose && mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (isLoad) {
            mDrawerlayout.openDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        CardFavorites.get().save();
    }
/*
    //https://www.jianshu.com/p/99649af3b191
    public void showNewbieGuide() {
        HighlightOptions options = new HighlightOptions.Builder()//绘制一个高亮虚线圈
                .setOnHighlightDrewListener((canvas, rectF) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(20);
                    paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                    canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2 + 10, paint);
                }).build();
        NewbieGuide.with(this)//with方法可以传入Activity或者Fragment，获取引导页的依附者
                .setLabel("searchCardGuide")
                .addGuidePage(
                        GuidePage.newInstance().setEverywhereCancelable(true)
                                .setBackgroundColor(0xbc000000)
                                .addHighLightWithOptions(findViewById(R.id.btn_search), HighLight.Shape.CIRCLE, options)
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
                                .addHighLightWithOptions(findViewById(R.id.search_result_count), HighLight.Shape.CIRCLE, options)
                                .setLayoutRes(R.layout.view_guide_home)
                                .setOnLayoutInflatedListener((view, controller) -> {
                                    TextView tv = view.findViewById(R.id.text_about);
                                    tv.setVisibility(View.VISIBLE);
                                    tv.setText(R.string.guide_search_result_count);
                                })

                )
                //.alwaysShow(true)//总是显示，调试时可以打开
                .show();
    }*/
}

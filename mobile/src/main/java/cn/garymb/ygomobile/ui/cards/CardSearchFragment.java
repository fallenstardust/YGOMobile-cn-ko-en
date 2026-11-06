package cn.garymb.ygomobile.ui.cards;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerViewItemListener;

import com.app.hubert.guide.NewbieGuide;
import com.app.hubert.guide.model.GuidePage;
import com.app.hubert.guide.model.HighLight;
import com.app.hubert.guide.model.HighlightOptions;
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement;

import java.util.List;
import java.util.ArrayList;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.CardSearchInfo;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.plus.AOnGestureListener;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.PackManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;

public class CardSearchFragment extends BaseFragemnt implements CardLoader.CallBack, CardSearcher.CallBack {
    private static final String TAG = "CardSearchFragment";
    public static final String SEARCH_MESSAGE = "searchMessage";
    long exitLasttime = 0;

    private HomeActivity activity;
    protected CardLoader mCardLoader;
    protected DrawerLayout mDrawerlayout;
    protected CardSearcher mCardSearcher;
    protected PackManager mPackManager;
    protected CardManager mCardManager;
    protected CardListAdapter mCardListAdapter;
    protected boolean isLoad = false;
    private RecyclerView mListView;
    private String intentSearchMessage;
    private boolean isInitCdbOk = false;
    private String currentCardSearchMessage = "";
    private DuelAssistantManagement duelAssistantManagement;
    private CardDetail mCardDetail;
    private DialogPlus mDialog;
    private TextView mResult_count;
    /**
     * 顶部计数行的排序规则切换下拉：选项与比较器与决斗内卡组编辑器同源，
     * 位序即 0=星数↑ / 1=攻击↑ / 2=守备↑ / 3=名称↓
     */
    private Spinner mSortSpinner;
    private int resultSortSel;
    /**
     * 顶部计数行的展现模式切换按钮：默认列表，点一下换宫格；
     * 图标始终表示当前模式，见 {@link #refreshResultViewModeIcon()}
     */
    private ImageButton mBtnViewMode;

    private View layoutView;
    private ImageButton btn_search;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        activity = (HomeActivity) getActivity();
        layoutView = inflater.inflate(R.layout.fragment_search, container, false);
        initView(layoutView);
        showNewbieGuide();
        return layoutView;
    }

    public void initView(View layoutView) {
        duelAssistantManagement = DuelAssistantManagement.getInstance();
        intentSearchMessage = getActivity().getIntent().getStringExtra(CardSearchFragment.SEARCH_MESSAGE);
        mResult_count = layoutView.findViewById(R.id.search_result_count);
        mDrawerlayout = layoutView.findViewById(R.id.drawer_layout);
        mListView = layoutView.findViewById(R.id.list_cards);
        // 展现模式按本页设置项初始化（用户规格：三页各存各的开关，未设置过走原列表）；
        // 宫格态只留卡图与禁限/起源点角标，列数按列表宽度自适应；
        // 本页不读卡组，所以行模式也不装配加卡按钮，但保留条目背景
        mCardListAdapter = new CardListAdapter(getContext(), activity.getImageLoader());
        mCardListAdapter.setShowAddButtons(false); // 隐藏添加按钮
        mCardListAdapter.setItemBg(true);
        mCardListAdapter.setGridLayout(isResultGridPreferred());
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        mListView.setAdapter(mCardListAdapter);
        // 顶部计数同一行的排序规则切换与展现模式切换
        mSortSpinner = layoutView.findViewById(R.id.sp_result_sort);
        setupResultSortSpinner();
        mBtnViewMode = layoutView.findViewById(R.id.btn_result_view_mode);
        mBtnViewMode.setOnClickListener(v -> toggleResultViewMode());
        refreshResultViewModeIcon();
        // 初始化态可能是宫格（从设置读回），行 LM 必须当场换掉；
        // 列数在宽度未量出时先给 1 列兜底，下一次填结果数据时再复核
        mListView.post(() -> mCardListAdapter.applyListLayout(mListView));
        btn_search = layoutView.findViewById(R.id.btn_search);
        btn_search.setOnClickListener((v) -> showSearch(true));
        mPackManager = DataManager.get().getPackManager();
        mCardManager = DataManager.get().getCardManager();
        mCardLoader = new CardLoader();
        mCardLoader.setCallBack(this);
        mCardSearcher = new CardSearcher(layoutView.findViewById(R.id.nav_view_list), mCardLoader);
        mCardSearcher.setCallBack(this);
        setListeners();
        isLoad = true;
        mCardLoader.loadData();
        mCardSearcher.initItems();
        //数据库初始化完毕后搜索被传入的关键字
        intentSearch(intentSearchMessage);
        isInitCdbOk = true;

    }

    /**
     * 顶部计数行的排序规则切换：选项与样式由 {@link CardListAdapter#setupSortSpinner}
     * 统一装配（与卡组编辑页同一份），比较器与决斗内卡组编辑器同源；
     * 切换只对当前结果重排，不重新发起搜索
     */
    private void setupResultSortSpinner() {
        if (mSortSpinner == null) return;
        CardListAdapter.setupSortSpinner(getContext(), mSortSpinner);
        mSortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                resultSortSel = position;
                resortSearchResults();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * 结果填入与刷新：先记住搜索线程交来的原始顺序，再按本页当前档位填入，
     * 最后复核一次展现布局。第 0 档（星数↑）就是改造前的原样顺序（含关键词完全命中置顶），
     * 只有攻击/守备/名称三档才去套与决斗内卡组编辑器同源的规则
     */
    private void fillSearchResultList(List<Card> cards) {
        mCardListAdapter.setRawCards(cards);
        mCardListAdapter.applySortRule(resultSortSel);
        // 布局管理器与列数变化会 requestLayout，填数据路径故 post 到本轮布局之外；
        // 尚未量出宽度时适配器先给 1 列兜底，等下一次填结果数据时再算
        mListView.post(() -> mCardListAdapter.applyListLayout(mListView));
    }

    /** 本页上一次的展现模式开关（与卡组管理页、决斗内卡组编辑器各存一份） */
    private boolean isResultGridPreferred() {
        return AppsSettings.get().getIntSettings(Constants.PREF_CARD_SEARCH_RESULT_GRID,
                Constants.PREF_DEF_RESULT_GRID) == 1;
    }

    /**
     * 切换搜索结果展现模式（列表/宫格）并同步按钮图标：模式由点击当场切换，
     * 条目布局与布局管理器必须同一帧换完（toggleListLayout 里成对处理），
     * 不能走填数据那条 post 路径，否则中间会有一帧宫格条目配在线性布局上、卡图被拉成满宽；
     * 而点击回调本身不在布局过程中，可以直接换 LM；开关结果写回本页设置项供下次初始化
     */
    private void toggleResultViewMode() {
        if (mCardListAdapter == null) return;
        boolean grid = mCardListAdapter.toggleListLayout(mListView);
        AppsSettings.get().saveIntSettings(Constants.PREF_CARD_SEARCH_RESULT_GRID, grid ? 1 : 0);
        refreshResultViewModeIcon();
    }

    /** 展现模式按钮的图标即当前模式：宫格→宫格图标，列表→列表图标 */
    private void refreshResultViewModeIcon() {
        if (mBtnViewMode == null || mCardListAdapter == null) return;
        mBtnViewMode.setImageResource(mCardListAdapter.isGridLayout()
                ? R.drawable.ic_view_grid_24 : R.drawable.ic_view_list_24);
    }

    /** 排序规则变更后只重排当前结果，不重新搜索：以适配器里记下的原始顺序为底 */
    private void resortSearchResults() {
        if (mCardListAdapter == null) return;
        mCardListAdapter.applySortRule(resultSortSel);
        mListView.post(() -> mCardListAdapter.applyListLayout(mListView));
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
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        // 底部页签切走：HomeActivity.switchFragment 以 hide 本页使根视图 GONE，而关键词记录的下拉
        // 是独立窗体不会随之消失，会卡在切换后的页面上，故本页退场时主动收起自己那份下拉
        if (hidden && mCardSearcher != null) {
            mCardSearcher.dismissKeywordDropdown();
        }
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
        mCardSearcher.search(currentCardSearchMessage);
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
        //mImageLoader.close();
        super.onDestroy();
    }

    @Override
    public void onSearchResult(List<Card> cardInfos, boolean isHide) {
        // 关键词搜索与收藏列表直接交搜索结果线程的原样顺序，
        // 由 fillSearchResultList 按本页档位决定要不要重排（第 0 档保持原样）
        showSearchResultCards(cardInfos);
    }

    /**
     * 结果填入 + 顶部计数。卡包展示需要保留卡包原来的顺序，不能拿它当关键词结果重排
     */
    private void showSearchResultCards(List<Card> cardInfos) {
        String keyword = mCardSearcher.getCurrentKeyword();
        if (keyword != null && !keyword.isEmpty()) {
            currentCardSearchMessage = keyword;
        }

        mCardListAdapter.setSearchKeyword(currentCardSearchMessage);
        mResult_count.setText(String.valueOf(cardInfos.size()));
        fillSearchResultList(cardInfos);
        if (!cardInfos.isEmpty()) {
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
    public void setLimit(LimitList limit, String caller) {
        mCardListAdapter.setLimitList(limit);
        mCardListAdapter.notifyDataSetChanged();
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
    public boolean onBackPressed() {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else {
            //与home相同双击返回
            if (System.currentTimeMillis() - exitLasttime <= 3000) {
                return false;
            } else {
                exitLasttime = System.currentTimeMillis();
                YGOUtil.showTextToast(R.string.back_tip, Toast.LENGTH_SHORT);
            }
        }
        return true;
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
                mCardDetail = new CardDetail((BaseActivity) getActivity(), activity.getImageLoader(), activity.getStringManager());
                mCardDetail.setCallBack((card, favorite) -> {
                    if (mCardSearcher.isShowFavorite()) {
                        mCardSearcher.showFavorites(false);
                    }
                });
                mCardDetail.setOnCardClickListener(new CardDetail.OnCardSearcherCardClickListener() {
                    @Override
                    public void onOpenUrl(Card cardInfo) {
                        WebActivity.openFAQ(getContext(), cardInfo);
                    }

                    @Override
                    public void onSearchKeyWord(String keyword) {
                        showSearchKeyWord(keyword);//根据关键词搜索
                    }

                    @Override
                    public void onShowCardList(List<Card> cardList, boolean sort) {
                        showCardList(cardList, sort);//卡包展示不排序，关联卡片排序
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
                mDialog.hideButton(true);
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
            mCardDetail.setCurrentSearchKeyword(currentCardSearchMessage);
            mCardDetail.bind(cardInfo, position, provider);
        }
    }

    private void showSearchKeyWord(String keyword) {//使用此方法，可以适用关键词查询逻辑，让完全符合关键词的卡置顶显示，并同时搜索字段和效果文本
        currentCardSearchMessage = keyword;
        CardSearchInfo searchInfo = new CardSearchInfo.Builder().keyword(keyword).cardTypes(new ArrayList<>()).build();//构建CardSearchInfo时type不能为null
        mCardLoader.search(searchInfo);
        //点击卡片详情中的高亮文字进行的关键词搜索，同样计入搜索历史
        mCardSearcher.recordHistoryKeyword(keyword);
    }

    private void showCardList(List<Card> cardList, boolean sort) {
        if (!cardList.isEmpty()) {
            // 关联卡片走移动端自己的固定序（mCardLoader.sort，高星/高攻在前那一族规则），
            // 卡包展示(sort=false)则保留卡包原顺序：两者都是本页的“原始顺序”，
            // 顶部规则下拉只在挑了攻击/守备/名称时才在其上重排（用户规格：默认档维持改造前排序）
            showSearchResultCards(sort ? mCardLoader.sort(cardList) : cardList);
            //sort为true代表点击“关联卡片”按钮（卡包展示为false），以当前卡片名作为关键词计入搜索历史
            if (sort && mCardDetail != null && mCardDetail.getCardInfo() != null) {
                mCardSearcher.recordHistoryKeyword(mCardDetail.getCardInfo().Name);
            }
        } else {
            Log.w("cc", "No card found");
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
                                .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                .addHighLightWithOptions(layoutView.findViewById(R.id.btn_search), HighLight.Shape.CIRCLE, options)
                                .setLayoutRes(R.layout.view_guide_home)
                                .setOnLayoutInflatedListener((view, controller) -> {
                                    TextView tv = view.findViewById(R.id.text_about);
                                    tv.setVisibility(View.VISIBLE);
                                    tv.setText(R.string.guide_button_search);
                                })

                )
                .addGuidePage(
                        GuidePage.newInstance().setEverywhereCancelable(true)
                                .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                .addHighLightWithOptions(layoutView.findViewById(R.id.search_result_count), HighLight.Shape.CIRCLE, options)
                                .setLayoutRes(R.layout.view_guide_home)
                                .setOnLayoutInflatedListener((view, controller) -> {
                                    TextView tv = view.findViewById(R.id.text_about);
                                    tv.setVisibility(View.VISIBLE);
                                    tv.setText(R.string.guide_search_result_count);
                                })

                )
                //.alwaysShow(true)//总是显示，调试时可以打开
                .show();
    }
}

package cn.garymb.ygomobile.ui.cards;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

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
import com.bumptech.glide.Glide;

import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;
import ocgcore.data.Card;

public abstract class BaseCardsActivity extends BaseActivity implements CardLoader.CallBack, CardSearcher.CallBack {
    protected DrawerLayout mDrawerLayout;
    protected RecyclerView mListView;
    protected CardSearcher mCardSelector;
    protected CardListAdapter mCardListAdapter;
    protected CardLoader mCardLoader;
    protected boolean isLoad = false;
    protected ImageLoader mImageLoader;
    protected StringManager mStringManager = DataManager.get().getStringManager();
    protected LimitManager mLimitManager = DataManager.get().getLimitManager();
    protected int screenWidth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck_cards);
        AnimationShake2();
        mImageLoader = new ImageLoader(true);
        mDrawerLayout = $(R.id.drawer_layout);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        mListView = $(R.id.list_cards);
        mCardListAdapter = new CardListAdapter(this, mImageLoader);
        mCardListAdapter.setEnableSwipe(true);
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(this));
        mListView.setAdapter(mCardListAdapter);
        setListeners();

        mCardLoader = new CardLoader(this);
        mCardLoader.setCallBack(this);
        mCardSelector = new CardSearcher($(R.id.nav_view_list), mCardLoader);
        mCardSelector.setCallBack(this);
        showNewbieGuide("deckmain");
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
                                    .addHighLightWithOptions(findViewById(R.id.deck_menu), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        //可只创建一个引导layout并把相关内容都放在其中并GONE，获得ID并初始化相应为显示
                                        view.findViewById(R.id.view_abt_bmb).setVisibility(View.VISIBLE);
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(findViewById(R.id.nav_search), HighLight.Shape.CIRCLE, options)
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
                                    .addHighLightWithOptions(findViewById(R.id.nav_list), HighLight.Shape.CIRCLE, options)
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
                                    .addHighLightWithOptions(findViewById(R.id.tv_deckmanger), HighLight.Shape.CIRCLE, options2)
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

    protected void setListeners() {
        mListView.addOnItemTouchListener(new RecyclerViewItemListener(mListView, new RecyclerViewItemListener.OnItemListener() {
            @Override
            public void onItemClick(View view, int pos) {
                onCardClick(view, mCardListAdapter.getItem(pos), pos);
            }

            @Override
            public void onItemLongClick(View view, int pos) {
                onCardLongClick(view, mCardListAdapter.getItem(pos), pos);
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
                        Glide.with(getContext()).resumeRequests();
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        Glide.with(getContext()).pauseRequests();
                        break;
                }
            }
        });
    }

    public ImageLoader getImageLoader() {
        return mImageLoader;
    }

    @Override
    protected void onBackHome() {
        if (mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
            return;
        }
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
            return;
        }
        finish();
    }

    public void onSearchResult(List<Card> cardInfos, boolean isHide) {
//        Log.d("kk", "find " + (cardInfos == null ? -1 : cardInfos.size()));
        mCardListAdapter.set(cardInfos);
        mCardListAdapter.notifyDataSetChanged();
        if (cardInfos != null && cardInfos.size() > 0) {
            mListView.smoothScrollToPosition(0);
        }
    }

    @Override
    protected void onResume() {
//        mImageLoader.resume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        //仅退出的时候才关闭zip
//        mImageLoader.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mImageLoader.close();
        super.onDestroy();
    }

    @Override
    public void onResetSearch() {

    }

    public void AnimationShake2() {
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);//加载动画资源文件
        findViewById(R.id.cube2).startAnimation(shake); //给组件播放动画效果
    }

    protected void hideDrawers() {
        if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
        }
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
        }
    }

    protected abstract void onCardClick(View view, Card cardInfo, int pos);

    protected abstract void onCardLongClick(View view, Card cardInfo, int pos);

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
            showNewbieGuide("searchResult");
        } else if (isLoad) {
            mDrawerLayout.openDrawer(Constants.CARD_RESULT_GRAVITY);
        }
    }
}

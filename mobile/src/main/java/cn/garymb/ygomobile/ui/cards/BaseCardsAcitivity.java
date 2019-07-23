package cn.garymb.ygomobile.ui.cards;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.RecyclerViewItemListener;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.IOException;
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

public abstract class BaseCardsAcitivity extends BaseActivity implements CardLoader.CallBack {
    protected DrawerLayout mDrawerlayout;
    protected RecyclerView mListView;
    protected CardSearcher mCardSelector;
    protected CardListAdapter mCardListAdapater;
    protected CardLoader mCardLoader;
    protected boolean isLoad = false;
    private ImageLoader mImageLoader;
    protected StringManager mStringManager = DataManager.get().getStringManager();
    protected LimitManager mLimitManager = DataManager.get().getLimitManager();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck_cards);
        AnimationShake2();

        mImageLoader = ImageLoader.get(this);
        mDrawerlayout = $(R.id.drawer_layout);

        mListView = $(R.id.list_cards);
        mCardListAdapater = new CardListAdapter(this, mImageLoader);
        mCardListAdapater.setEnableSwipe(true);
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(this));
        mListView.setAdapter(mCardListAdapater);
        setListeners();

        mCardLoader = new CardLoader(this);
        mCardLoader.setCallBack(this);
        mCardSelector = new CardSearcher($(R.id.nav_view_list), mCardLoader);
    }

    protected int getDimen(int id) {
        return (int) getResources().getDimension(id);
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

    public ImageLoader getImageLoader() {
        return mImageLoader;
    }

    @Override
    protected void onBackHome() {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
            return;
        }
        if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
            return;
        }
        finish();
    }

    @Override
    public void onSearchResult(List<Card> cardInfos) {
//        Log.d("kk", "find " + (cardInfos == null ? -1 : cardInfos.size()));
        mCardListAdapater.set(cardInfos);
        mCardListAdapater.notifyDataSetChanged();
        if (cardInfos != null && cardInfos.size() > 0) {
            mListView.smoothScrollToPosition(0);
        }
    }

    @Override
    protected void onDestroy() {
        ImageLoader.onDestory(this);
        try {
            mImageLoader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public void onResetSearch() {

    }

    public void AnimationShake2(){
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);//加载动画资源文件
        findViewById(R.id.cube2).startAnimation(shake); //给组件播放动画效果
    }

    protected void hideDrawers() {
        if (mDrawerlayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerlayout.closeDrawer(Gravity.RIGHT);
        }
        if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
        }
    }

    protected abstract void onCardClick(View view, Card cardInfo, int pos);

    protected abstract void onCardLongClick(View view, Card cardInfo, int pos);

    protected void showSearch(boolean autoClose) {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
        }
        if (autoClose && mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (isLoad) {
            mDrawerlayout.openDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
    }

    protected void showResult(boolean autoClose) {
        if (mDrawerlayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
        if (autoClose && mDrawerlayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerlayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
        } else if (isLoad) {
            mDrawerlayout.openDrawer(Constants.CARD_RESULT_GRAVITY);
        }
    }
}

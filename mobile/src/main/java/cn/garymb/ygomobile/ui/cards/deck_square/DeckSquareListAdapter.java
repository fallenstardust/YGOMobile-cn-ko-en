package cn.garymb.ygomobile.ui.cards.deck_square;

import android.util.Log;
import android.widget.ImageView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import cn.garymb.ygomobile.ui.cards.deck_square.api_response.GetSquareDeckCondition;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.OnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.SquareDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.LogUtil;

//提供recyclerview的数据
public class DeckSquareListAdapter extends BaseQuickAdapter<OnlineDeckDetail, BaseViewHolder> {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private ImageLoader imageLoader;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Integer lastPage = 1;
    private Integer lastSize = 30;
    private String lastKeyWord = "";
    private Boolean lastSortLike = false;
    private Boolean lastSortRank = false;
    private String lastContributor = "";

    public DeckSquareListAdapter(int layoutResId) {
        super(layoutResId);

        imageLoader = new ImageLoader();
    }

    public void setSwipeRefreshLayout(SwipeRefreshLayout swipeRefreshLayout) {
        this.swipeRefreshLayout = swipeRefreshLayout;
    }

    public void loadData() {
        loadData(1, 30, "", false, false, "");
    }

    //下拉刷新时按最近一次查询条件重新加载
    public void reload() {
        loadData(lastPage, lastSize, lastKeyWord, lastSortLike, lastSortRank, lastContributor);
    }

    public void loadData(Integer page, Integer size, String keyWord, Boolean sortLike, Boolean sortRank, String contributor) {
        lastPage = page;
        lastSize = size;
        lastKeyWord = keyWord;
        lastSortLike = sortLike;
        lastSortRank = sortRank;
        lastContributor = contributor;
        setRefreshing(true);
        VUiKit.defer().when(() -> {
            SquareDeckResponse result = DeckSquareApiUtil.getSquareDecks(new GetSquareDeckCondition(page, size, keyWord, sortLike, sortRank, contributor));
            if (result == null) {
                return null;
            } else {
                return result.getData().getRecords();
            }

        }).fail((e) -> {
            Log.e(TAG, e + "");
            setRefreshing(false);
            LogUtil.i(TAG, "Get square deck fail");

        }).done((result) -> {
            if (result != null) {
                LogUtil.i(TAG, "Get square deck success");
                getData().clear();
                addData(result);
                notifyDataSetChanged();
            }
            setRefreshing(false);
        });

    }

    private void setRefreshing(boolean refreshing) {
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing() != refreshing) {
            swipeRefreshLayout.setRefreshing(refreshing);
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, OnlineDeckDetail item) {
        helper.setText(R.id.deck_info_name, item.getDeckName());
        helper.setText(R.id.deck_contributor, item.getDeckContributor());
        helper.setText(R.id.deck_last_date, DeckUtil.convertToGMTDate(item.getLastDate()));
        helper.setText(R.id.like_count, item.getDeckLike()+"");
        ImageView cardImage = helper.getView(R.id.deck_info_image);
        long code = item.getDeckCoverCard1();
        LogUtil.i(TAG, code + " " + item.getDeckName());
        if (code != 0) {
            imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.small);
        } else {
            imageLoader.bindImage(cardImage, -1, null, ImageLoader.Type.small);
        }

    }
}
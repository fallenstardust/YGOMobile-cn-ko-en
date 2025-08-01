package cn.garymb.ygomobile.ui.cards.deck_square;


import static cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareFileUtil.convertToGMTDate;

import android.util.Log;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import cn.garymb.ygomobile.ui.cards.deck_square.api_response.GetSquareDeckCondition;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.OnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.SquareDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;

//提供recyclerview的数据
public class DeckSquareListAdapter extends BaseQuickAdapter<OnlineDeckDetail, BaseViewHolder> {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private ImageLoader imageLoader;

    public DeckSquareListAdapter(int layoutResId) {
        super(layoutResId);

        imageLoader = new ImageLoader();
    }

    public void loadData() {
        loadData(1, 30, "", false, false, "");
    }

    public void loadData(Integer page, Integer size, String keyWord, Boolean sortLike, Boolean sortRank, String contributor) {
        final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_online_deck));
        VUiKit.defer().when(() -> {
            SquareDeckResponse result = DeckSquareApiUtil.getSquareDecks(new GetSquareDeckCondition(page, size, keyWord, sortLike, sortRank, contributor));
            if (result == null) {
                return null;
            } else {
                return result.getData().getRecords();
            }

        }).fail((e) -> {
            Log.e(TAG, e + "");
            if (dialog_read_ex.isShowing()) {//关闭异常
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {

                }
            }
            LogUtil.i(TAG, "Get square deck fail");

        }).done((result) -> {
            if (result != null) {
                LogUtil.i(TAG, "Get square deck success");
                getData().clear();
                addData(result);
                notifyDataSetChanged();
            }
            if (dialog_read_ex.isShowing()) {
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {
                }
            }
        });

    }

    @Override
    protected void convert(BaseViewHolder helper, OnlineDeckDetail item) {
        helper.setText(R.id.deck_info_name, item.getDeckName());
        helper.setText(R.id.deck_contributor, item.getDeckContributor());
        helper.setText(R.id.deck_last_date, convertToGMTDate(item.getLastDate()));
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
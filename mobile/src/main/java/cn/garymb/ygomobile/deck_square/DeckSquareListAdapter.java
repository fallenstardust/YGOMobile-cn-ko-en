package cn.garymb.ygomobile.deck_square;


import android.util.Log;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;

import cn.garymb.ygomobile.deck_square.api_response.GetSquareDeckCondition;
import cn.garymb.ygomobile.deck_square.api_response.OnlineDeckDetail;
import cn.garymb.ygomobile.deck_square.api_response.SquareDeckResponse;
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

    public void loadData(Integer page, Integer size, String keyWord, Boolean sortLike, Boolean sortRank, String contributer) {
        final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_online_deck));
        VUiKit.defer().when(() -> {
            SquareDeckResponse result = DeckSquareApiUtil.getSquareDecks(new GetSquareDeckCondition(page, size, keyWord, sortLike, sortRank, contributer));
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

        }).done((exCardDataList) -> {
            if (exCardDataList != null) {
                LogUtil.i(TAG, "Get square deck success");
                getData().clear();
                addData(exCardDataList);
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
        helper.setText(R.id.deck_last_date, item.getLastDate());
        helper.setText(R.id.like_count, item.getDeckLike()+"");
        ImageView cardImage = helper.getView(R.id.deck_info_image);
        long code = item.getDeckCoverCard1();
        LogUtil.i(TAG, code + " " + item.getDeckName());
        if (code != 0) {
            imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.small);
        } else {
            imageLoader.bindImage(cardImage, -1, null, ImageLoader.Type.small);
        }
        // ImageView imageview = helper.getView(R.id.ex_card_image);
        //the function cn.garymb.ygomobile.loader.ImageLoader.bindT(...)
        //cn.garymb.ygomobile.loader.ImageLoader.setDefaults(...)
        //is a private function,so I copied the content of it to here
        /* 如果查不到版本号，则不显示图片 */
        /* 如果能查到版本号，则显示图片，利用glide的signature，将版本号和url作为signature，由glide判断是否使用缓存 */


    }
}
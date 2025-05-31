package cn.garymb.ygomobile.ui.adapters;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;

import cn.garymb.ygomobile.ex_card.ExCardListAdapter;
import cn.garymb.ygomobile.lite.R;
import ocgcore.data.Card;

//卡组预览的adapter
public class DeckPreviewListAdapter extends BaseQuickAdapter<Card, BaseViewHolder> {

    private static final String TAG = ExCardListAdapter.class.getSimpleName();

    public DeckPreviewListAdapter(int layoutResId) {
        super(layoutResId);
    }

    public void updateData(List<Card> dataList) {
        getData().clear();
        addData(dataList);
        notifyDataSetChanged();
    }


    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, Card item) {
        baseViewHolder.setText(R.id.preview_card_name, item.Name);
        baseViewHolder.setText(R.id.preview_card_id, Integer.toString(item.Code));
    }
}

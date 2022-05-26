package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import ocgcore.data.LimitList;

public class DeckListAdapter extends BaseRecyclerAdapterPlus<DeckFile, BaseViewHolder>{
    private ImageLoader imageLoader;
    private ImageTop mImageTop;
    private LimitList mLimitList;
    private boolean mEnableSwipe = false;
    private DeckInfo deckInfo;

    public DeckListAdapter(Context context) {
        super(context, R.layout.item_server_info_swipe);
        

    }

    @Override
    protected void convert(@NonNull com.chad.library.adapter.base.viewholder.BaseViewHolder baseViewHolder, DeckFile deck) {
        imageLoader.bindImage(baseViewHolder.getView(R.id.card_image), deck.getFirstCode(), ImageLoader.Type.small);
        baseViewHolder.setText(R.id.tv_name,deck.getName());
        baseViewHolder.setText(R.id.count_main,deckInfo.getMainCount());
        baseViewHolder.setText(R.id.count_ex,deckInfo.getExtraCount());
        baseViewHolder.setText(R.id.count_main,deckInfo.getSideCount());
    }

    public void setEnableSwipe(boolean enableSwipe) {
        mEnableSwipe = enableSwipe;
    }
}
class DeckViewHolder extends BaseRecyclerAdapterPlus.BaseViewHolder {
    ImageView cardImage;
    ImageView prerelease_star;
    TextView deckName;
    TextView main;
    TextView extra;
    TextView side;

    public DeckViewHolder(View view) {
        super(view);
        view.setTag(view.getId(), this);
        cardImage = $(R.id.card_image);
        deckName = $(R.id.deck_name);
        main = $(R.id.count_main);
        extra = $(R.id.count_ex);
        side = $(R.id.count_ex);
        prerelease_star = $(R.id.prerelease_star);
    }
}
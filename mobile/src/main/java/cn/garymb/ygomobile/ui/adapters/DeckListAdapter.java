package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.TextSelect;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import ocgcore.data.LimitList;

public class DeckListAdapter<T extends TextSelect> extends BaseRecyclerAdapterPlus<DeckFile, BaseViewHolder>{
    private ImageLoader imageLoader;
    private ImageTop mImageTop;
    private LimitList mLimitList;
    private boolean mEnableSwipe = false;
    private DeckInfo deckInfo;
    private DeckFile deckFile;
    private TextSelectAdapter.OnItemSelectListener onItemSelectListener;
    private int selectPosition;
    private boolean isSelect;
    private boolean isManySelect;
    private List<T> selectList;

    public DeckListAdapter(Context context, List<T> data, int select) {
        super(context, R.layout.item_server_info_swipe);
        this.selectPosition = select;
        if (select >= 0)
            isSelect = true;
        else
            isSelect = false;
        isManySelect = false;
        selectList = new ArrayList<>();
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (isSelect && position == selectPosition)
                    return;
                selectPosition = position;
                notifyDataSetChanged();
                if (onItemSelectListener != null)
                    onItemSelectListener.onItemSelect(position, data.get(position).getObject());
            }
        });
        

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
    public void setSelectPosition(int selectPosition) {
        this.selectPosition = selectPosition;
    }

    public boolean isSelect() {
        return isSelect;
    }

    public boolean isManySelect() {
        return isManySelect;
    }

    public void addManySelect(T t) {
        if (selectList.contains(t))
            selectList.remove(t);
        else
            selectList.add(t);
    }

    public void setManySelect(boolean manySelect) {
        isManySelect = manySelect;
        if (!isManySelect) {
            selectList.clear();
            notifyDataSetChanged();
        }
    }

    public List<T> getSelectList() {
        return selectList;
    }

    public int getSelectPosition() {
        return selectPosition;
    }

    public void setOnItemSelectListener(TextSelectAdapter.OnItemSelectListener onItemSelectListener) {
        this.onItemSelectListener = onItemSelectListener;
    }

    public interface OnItemSelectListener<T> {
        void onItemSelect(int position, T item);
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
package cn.garymb.ygomobile.ex_card;


import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.RequestBuilder;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

public class ExCardListAdapter extends BaseQuickAdapter<ExCard, BaseViewHolder> {

    private ImageLoader imageLoader;

    public ExCardListAdapter(int layoutResId, List<ExCard> data) {
        super(layoutResId, data);
        //use the imageLoader to load image from url
        imageLoader = new ImageLoader(true);
    }

    @Override
    protected void convert(BaseViewHolder helper, ExCard item) {
        helper.setText(R.id.ex_card_name, item.getName());
        helper.setText(R.id.ex_card_description, item.getDescription());

        if (item.isUpdatingLog()) {
            helper.setGone(R.id.ex_card_image, true);
        } else {
            helper.setGone(R.id.ex_card_image, false);
            ImageView imageview = helper.getView(R.id.ex_card_image);
            //the function cn.garymb.ygomobile.loader.ImageLoader.bindT(...)
            //cn.garymb.ygomobile.loader.ImageLoader.setDefaults(...)
            //is a private function,so I copied the content of it to here
            RequestBuilder<Drawable> resource = GlideCompat.with(imageview.getContext()).load(item.getImageUrl());
            resource.placeholder(R.drawable.unknown);
            resource.error(R.drawable.unknown);
            resource.into(imageview);
        }
    }
}

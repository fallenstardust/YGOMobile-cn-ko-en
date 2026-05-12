package cn.garymb.ygomobile.ui.mycard.adapter;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.RequestBuilder;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.McNews;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

public class McNewsAdapter extends BaseQuickAdapter<McNews, BaseViewHolder> {

    private OnNewsClickListener mListener;

    public interface OnNewsClickListener {
        void onNewsClick(McNews news);
    }

    public McNewsAdapter(OnNewsClickListener listener) {
        super(R.layout.item_mc_news);
        this.mListener = listener;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, McNews item) {
        ImageView ivNewsImage = helper.getView(R.id.iv_news_image);
        TextView tvTitle = helper.getView(R.id.tv_news_title);

        String displayText = "[" + item.getCreate_time() + "] " + (item.getTitle() != null ? item.getTitle() : item.getMessage());
        tvTitle.setText(displayText);

        String imageUrl = item.getImage_url();
        if (!TextUtils.isEmpty(imageUrl)) {
            RequestBuilder<Drawable> resource = GlideCompat.with(ivNewsImage.getContext())
                    .load(imageUrl);
            resource.placeholder(R.drawable.unknown);
            resource.error(R.drawable.unknown);
            resource.into(ivNewsImage);
        } else {
            ivNewsImage.setImageResource(R.drawable.unknown);
        }

        helper.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onNewsClick(item);
            }
        });
    }
}

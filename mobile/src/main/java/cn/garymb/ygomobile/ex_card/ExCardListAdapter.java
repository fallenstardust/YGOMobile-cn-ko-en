package cn.garymb.ygomobile.ex_card;


import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.RequestBuilder;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

public class ExCardListAdapter extends BaseQuickAdapter<ExCard, BaseViewHolder> {
    private static final String TAG = String.valueOf(ExCardListAdapter.class);
    private ImageLoader imageLoader;

    public ExCardListAdapter(int layoutResId) {
        super(layoutResId);
    }

    public void loadData() {
        final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_ex_card));
        VUiKit.defer().when(() -> {
            String aurl = Constants.URL_YGO233_ADVANCE;
            //Connect to the website
            Document document = Jsoup.connect(aurl).get();
            Element pre_card_content = document.getElementById("pre_release_cards");
            Element tbody = pre_card_content.getElementsByTag("tbody").get(0);
            Elements cards = tbody.getElementsByTag("tr");
            if (cards.size() > 5000) {//Considering the efficiency of html parse, if the size of
                // pre cards list is to large, return null directly.
                return null;
            }
            ArrayList<ExCard> exCardList = new ArrayList<>();
            for (Element card : cards) {
                Elements card_attributes = card.getElementsByTag("td");
                String imageUrl = card_attributes.get(0).getElementsByTag("a").attr("href") + "!half";
                String name = card_attributes.get(1).text();
                String description = card_attributes.get(2).text();
                ExCard exCard = new ExCard(name, imageUrl, description, 0);
                exCardList.add(exCard);

            }


            if (exCardList.isEmpty()) {
                return null;
            } else {
                return exCardList;
            }

        }).fail((e) -> {
            //关闭异常
            if (dialog_read_ex.isShowing()) {
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {
                }
            }

            Log.i(TAG, "webCrawler fail");
        }).done(exCardList -> {

            if (exCardList != null) {
                Log.i(TAG, "webCrawler done");

                getData().clear();
                addData(exCardList);
                notifyDataSetChanged();
            }
            //关闭异常
            if (dialog_read_ex.isShowing()) {
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {
                }
            }
        });
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

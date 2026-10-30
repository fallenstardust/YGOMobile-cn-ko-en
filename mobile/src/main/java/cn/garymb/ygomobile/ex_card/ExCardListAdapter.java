package cn.garymb.ygomobile.ex_card;


import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.signature.ObjectKey;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.ServerUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import cn.garymb.ygomobile.utils.glide.HostAgnosticGlideUrl;
import cn.garymb.ygomobile.utils.glide.StringSignature;
import okhttp3.Response;

public class ExCardListAdapter extends BaseQuickAdapter<ExCardData, BaseViewHolder> {
    private static final String TAG = ExCardListAdapter.class.getSimpleName();

    /** 加载态由下拉刷新的转圈承担（不再弹模态 DialogPlus，拉不到数据时不会卡在页面）。 */
    private SwipeRefreshLayout swipeRefreshLayout;

    public ExCardListAdapter(int layoutResId) {
        super(layoutResId);
    }

    public void setSwipeRefreshLayout(SwipeRefreshLayout swipeRefreshLayout) {
        this.swipeRefreshLayout = swipeRefreshLayout;
    }

    public void loadData() {
        setRefreshing(true);
        VUiKit.defer().when(() -> {
            LogUtil.d(TAG, "start fetch");
            List<ExCardData> exCardDataList = null;
            try {
                Response response = OkhttpUtil.synchronousGet(ServerUtil.preCardListJson(), null, null);
                String responseBodyString = response.body().string();
                Type listType = new TypeToken<List<ExCardData>>() {
                }.getType();
                Gson gson = new Gson();
                // Convert JSON to Java object using Gson
                exCardDataList = gson.fromJson(responseBodyString, listType);
            } catch (IOException e) {
                Log.e(TAG, "Error occured when fetching data from pre-card server");
                return null;
            }

            if (exCardDataList == null || exCardDataList.isEmpty()) {
                // gson 对非列表响应（错误页、空体）会返回 null，旧写法直接 .isEmpty() 会抛
                // NPE；现按“无数据”收尾，转圈正常停止，用户可再次下拉重试
                return null;
            } else {
                return exCardDataList;
            }

        }).fail((e) -> {
            Log.e(TAG,e+"");
            setRefreshing(false);
            LogUtil.i(TAG, "webCrawler fail");

        }).done((exCardDataList) -> {
            if (exCardDataList != null) {
                LogUtil.i(TAG, "webCrawler done");
                getData().clear();
                addData(exCardDataList);
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

    private static Boolean isMonster(List<String> list) {
        for (String data : list) {
            if (data.equals("怪兽")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void convert(BaseViewHolder helper, ExCardData item) {
        helper.setText(R.id.ex_card_name, item.getName());
        helper.setText(R.id.ex_card_description, item.getDesc());
        helper.setText(R.id.ex_card_overall, item.getOverallString());

        ImageView imageview = helper.getView(R.id.ex_card_image);
        //the function cn.garymb.ygomobile.loader.ImageLoader.bindT(...)
        //cn.garymb.ygomobile.loader.ImageLoader.setDefaults(...)
        //is a private function,so I copied the content of it to here
        /* 如果查不到版本号，则不显示图片 */
        /* 如果能查到版本号，则显示图片，利用glide的signature，将版本号和url作为signature，由glide判断是否使用缓存 */
        if (ServerUtil.exCardState == ServerUtil.ExCardState.NEED_UPDATE
                || ServerUtil.exCardState == ServerUtil.ExCardState.UPDATED) {
            StringSignature signature = new StringSignature(ServerUtil.serverExCardVersion);
            ObjectKey key = new ObjectKey(ServerUtil.serverExCardVersion);
            // picUrl 是服务端 JSON 直接给出的 cdntx 固定源站地址，Glide 自带 HTTP 栈、不经
            // OkhttpUtil/DownloadUtil 的改写入口；用 HostAgnosticGlideUrl 让请求走可用节点、
            // 而缓存键保持原始地址，避免节点列表换序时先行卡图全量重下
            RequestBuilder<Drawable> resource = GlideCompat.with(imageview.getContext()).
                    load(new HostAgnosticGlideUrl(item.getPicUrl()))
                    .signature(signature);
            resource.placeholder(R.drawable.unknown);
            resource.error(R.drawable.unknown);
            resource.into(imageview);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.UNCHECKED || ServerUtil.exCardState == ServerUtil.ExCardState.ERROR) {
            /* 版本号都拿不到，就不显示图片了 */
        }


    }
}

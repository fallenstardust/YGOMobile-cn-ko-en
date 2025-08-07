package cn.garymb.ygomobile.ui.cards.deck_square;

import static cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareFileUtil.convertToGMTDate;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.ui.cards.DeckManagerFragment;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushMultiResponse;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

//提供“我的”卡组数据，打开后先从sharePreference查询，没有则从服务器查询，然后缓存到sharePreference
public class MyDeckListAdapter extends BaseQuickAdapter<MyOnlineDeckDetail, BaseViewHolder> {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;
    private ImageLoader imageLoader;

    public MyDeckListAdapter(int layoutResId, YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        super(layoutResId);
        imageLoader = new ImageLoader();
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

    public void loadData() {
        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if (loginToken == null) {
            return;
        }
        if (DeckManagerFragment.getOriginalData().isEmpty()){
            final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_online_deck));
            VUiKit.defer().when(() -> {
                MyDeckResponse result = DeckSquareApiUtil.getUserDecks(loginToken);
                if (result == null) return null;
                else return result.getData();
            }).fail((e) -> {
                Log.e(TAG, e + "");
                if (dialog_read_ex.isShowing()) {//关闭异常
                    try {
                        dialog_read_ex.dismiss();
                    } catch (Exception ex) {

                    }
                }
                LogUtil.i(TAG, "load mycard from server failed:" + e);
            }).done((serverDecks) -> {
                if (serverDecks != null) {//将服务端的卡组也放到缓存中
                    DeckManagerFragment.getOriginalData().clear();//虽然判断originalData是空的才会执行到这里，但还是写上保险
                    DeckManagerFragment.getOriginalData().addAll(serverDecks); // 保存原始数据
                }

                LogUtil.i(TAG, "load mycard from server done");

                getData().clear();
                addData(serverDecks);
                notifyDataSetChanged();

                if (dialog_read_ex.isShowing()) {
                    try {
                        dialog_read_ex.dismiss();
                    } catch (Exception ex) {
                    }
                }
            });
        } else {
            LogUtil.i(TAG, "load originalData done");
            getData().clear();
            addData(DeckManagerFragment.getOriginalData());
            notifyDataSetChanged();
        }


    }

    // 在适配器中添加成员变量保存当前搜索关键词
    private String currentKeyword = "";

    // 修改筛选函数，保存当前关键词
    public void filter(String keyword) {
        currentKeyword = keyword; // 保存关键词
        List<MyOnlineDeckDetail> filteredList = new ArrayList<>();
        if (keyword.isEmpty()) {
            filteredList.addAll(DeckManagerFragment.getOriginalData());
        } else {
            String lowerKeyword = keyword.toLowerCase(); // 转为小写，实现不区分大小写搜索
            for (MyOnlineDeckDetail item : DeckManagerFragment.getOriginalData()) {
                if (item.getDeckName().toLowerCase().contains(lowerKeyword) ||
                        item.getDeckType().toLowerCase().contains(lowerKeyword)) {
                    filteredList.add(item);
                }
            }
        }
        getData().clear();
        addData(filteredList);
        notifyDataSetChanged();
    }

    /**
     * 将文本中包含关键词的部分高亮显示
     * @param text 原始文本
     * @param keyword 关键词
     * @return 处理后的SpannableString
     */
    private SpannableString getHighlightedText(String text, String keyword) {
        if (text == null || keyword.isEmpty()) {
            return new SpannableString(text == null ? "" : text);
        }

        SpannableString spannable = new SpannableString(text);
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int index = lowerText.indexOf(lowerKeyword);

        // 循环查找所有匹配的关键词并设置高亮
        while (index >= 0) {
            int start = index;
            int end = index + keyword.length();

            // 设置高亮样式，可以自定义颜色和样式
            spannable.setSpan(new ForegroundColorSpan(YGOUtil.c(R.color.holo_blue_bright)),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            // 查找下一个匹配
            index = lowerText.indexOf(lowerKeyword, end);
        }

        return spannable;
    }

    private void deleteMyDeckOnLine(MyOnlineDeckDetail item) {
        if (item != null) {
            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;

            }
            List<DeckFile> deleteList = new ArrayList<>();
            DeckFile deleteDeckFile = new DeckFile(item.getDeckId(), DeckType.ServerType.MY_SQUARE);
            deleteDeckFile.setTypeName(item.getDeckType());
            deleteDeckFile.setName(item.getDeckName());
            deleteList.add(deleteDeckFile);
            try {
                DeckSquareApiUtil.deleteDecks(deleteList);
            }catch (Throwable e){
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }
            remove(item);
        }
    }

    /**
     * 注意，更新卡组状态要过很久才生效（实测延迟偶尔达5s）
     * @param item
     */
    private void changeDeckPublicState(MyOnlineDeckDetail item) {
        if (item != null) {
            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;
            }
            VUiKit.defer().when(() -> {
                BasicResponse result = DeckSquareApiUtil.setDeckPublic(item.getDeckId(), loginToken, item.isPublic());
                return result;
            }).fail(e -> {
                LogUtil.i(TAG, "切换显示失败" + e.getMessage());
            }).done(data -> {
                LogUtil.i(TAG, "切换显示成功" + data.getMessage());
            });
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, MyOnlineDeckDetail item) {
        ImageView iv_box = helper.findView(R.id.iv_box);
        ImageView deck_info_image = helper.findView(R.id.deck_info_image);
        ImageView delete_my_online_deck_btn = helper.findView(R.id.delete_my_online_deck_btn);
        LinearLayout ll_switch_show = helper.findView(R.id.ll_switch_show);
        TextView change_show_or_hide = helper.findView(R.id.change_show_or_hide);
        ImageView show_on_deck_square = helper.findView(R.id.show_on_deck_square);
        long code = item.getDeckCoverCard1();

        if (item.isDelete()) {
            deck_info_image.setColorFilter(YGOUtil.c(R.color.bottom_bg));
            iv_box.setColorFilter(YGOUtil.c(R.color.bottom_bg));
            delete_my_online_deck_btn.setVisibility(View.GONE);
            ll_switch_show.setVisibility(View.GONE);
        } else {
            deck_info_image.clearColorFilter();
            iv_box.clearColorFilter();
            delete_my_online_deck_btn.setVisibility(View.VISIBLE);
            ll_switch_show.setVisibility(View.VISIBLE);
        }
        // 处理卡组类型高亮, 需要判断卡组分类的内容
        helper.setText(R.id.my_online_deck_type, getHighlightedText(item.getDeckType().equals("") ? "" : "-"+item.getDeckType()+"-", currentKeyword));
        // 处理卡组名称高亮
        helper.setText(R.id.my_deck_name, getHighlightedText(item.getDeckName(), currentKeyword));

        helper.setText(R.id.deck_update_date, convertToGMTDate(item.getDeckUpdateDate()));

        if (item.isPublic()) {
            change_show_or_hide.setText(R.string.in_public);
            show_on_deck_square.setBackgroundResource(R.drawable.baseline_remove_red_eye_24);
            ll_switch_show.setBackgroundResource(R.drawable.button_radius_red);
        } else {
            change_show_or_hide.setText(R.string.in_personal_use);
            show_on_deck_square.setBackgroundResource(R.drawable.closed_eyes_24);
            ll_switch_show.setBackgroundResource(R.drawable.button_radius_n);
        }
        if (code != 0) {
            imageLoader.bindImage(deck_info_image, code, null, ImageLoader.Type.small);
        } else {
            imageLoader.bindImage(deck_info_image, -1, null, ImageLoader.Type.small);
        }
        delete_my_online_deck_btn.setOnClickListener(view -> {
            deleteMyDeckOnLine(item);
        });
        helper.getView(R.id.ll_switch_show).setOnClickListener(view -> {
            if (item.isPublic()) {
                change_show_or_hide.setText(R.string.in_personal_use);
                show_on_deck_square.setBackgroundResource(R.drawable.closed_eyes_24);
                ll_switch_show.setBackgroundResource(R.drawable.button_radius_n);
                item.setPublic(false); // 关闭公开状态
            } else {
                change_show_or_hide.setText( R.string.in_public);
                show_on_deck_square.setBackgroundResource(R.drawable.baseline_remove_red_eye_24);
                ll_switch_show.setBackgroundResource(R.drawable.button_radius_red);
                item.setPublic(true); // 开启公开状态
            }
            LogUtil.i(TAG, "current " + item.toString());
            changeDeckPublicState(item);
        });
        helper.getView(R.id.ll_download).setOnLongClickListener(view -> {
            //TODO
            //点击“我的卡组”中的某个卡组后，弹出dialog，dialog根据卡组的同步情况自动显示对应的下载/上传按钮
            DeckFile deckFile = new DeckFile(item.getDeckId(), DeckType.ServerType.MY_SQUARE);
            mDialogListener.onDismiss();
            onDeckMenuListener.onDeckSelect(deckFile);
            return true;
        });

    }

}
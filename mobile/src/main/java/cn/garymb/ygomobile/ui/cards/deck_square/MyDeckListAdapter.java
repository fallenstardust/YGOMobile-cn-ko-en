package cn.garymb.ygomobile.ui.cards.deck_square;

import android.util.Log;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.PushSingleDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.bo.MyDeckItem;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

//提供“我的”卡组数据，打开后先从sharePreference查询，没有则从服务器查询，然后缓存到sharePreference
public class MyDeckListAdapter extends BaseQuickAdapter<MyDeckItem, BaseViewHolder> {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;
    private ImageLoader imageLoader;
    private List<MyDeckItem> originalData; // 保存原始数据

    public MyDeckListAdapter(int layoutResId, YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        super(layoutResId);
        originalData = new ArrayList<>();
        imageLoader = new ImageLoader();
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

    public void loadData() {
        List<MyDeckItem> myOnlineDecks = new ArrayList<>();
        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if (loginToken == null) {
            return;
        }

        final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_online_deck));
        VUiKit.defer().when(() -> {

            MyDeckResponse result = DeckSquareApiUtil.getUserDecks(loginToken);

            if (result == null) {
                return null;
            } else {
                return result.getData();
            }

        }).fail((e) -> {
            Log.e(TAG, e + "");
            if (dialog_read_ex.isShowing()) {//关闭异常
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {

                }
            }
            LogUtil.i(TAG, "load mycard from server fail");

        }).done((serverDecks) -> {
            if (serverDecks != null) {//将服务端的卡组也放到LocalDecks中
                for (MyOnlineDeckDetail detail : serverDecks) {
                    MyDeckItem item = new MyDeckItem();
                    item.setDeckName(detail.getDeckName());
                    item.setDeckId(detail.getDeckId());
                    item.setUserId(detail.getUserId());
                    item.setDeckCoverCard1(detail.getDeckCoverCard1());
                    item.setUpdateDate(detail.getDeckUpdateDate());
                    item.setPublic(detail.isPublic());
                    myOnlineDecks.add(item);
                }
            }

            LogUtil.i(TAG, "load mycard from server done");
            originalData.clear();
            originalData.addAll(myOnlineDecks); // 保存原始数据
            getData().clear();
            addData(myOnlineDecks);
            notifyDataSetChanged();

            if (dialog_read_ex.isShowing()) {
                try {
                    dialog_read_ex.dismiss();
                } catch (Exception ex) {
                }
            }
        });

    }

    // 筛选函数
    public void filter(String keyword) {
        List<MyDeckItem> filteredList = new ArrayList<>();
        if (keyword.isEmpty()) {
            // 如果关键词为空，则显示所有数据
            filteredList.addAll(originalData);
        } else {
            // 遍历原始数据，筛选出包含关键词的item
            for (MyDeckItem item : originalData) {
                if (item.getDeckName().contains(keyword)) {
                    filteredList.add(item);
                }
            }
        }
        // 更新显示的数据
        getData().clear();
        addData(filteredList);
        notifyDataSetChanged();
    }

    public List<MyDeckItem> getOriginalData(){
        return this.originalData;
    }
    private void deleteMyDeckOnLine(MyDeckItem item) {
        if (item != null) {
            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;

            }
            VUiKit.defer().when(() -> {
                PushSingleDeckResponse result = DeckSquareApiUtil.deleteDeck(item.getDeckId(), loginToken);
                return result;
            }).fail(e -> {
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }).done(data -> {
                if (data.isData()) {
                    remove(item);
                } else {
                    YGOUtil.showTextToast("delete fail " + data.getMessage());
                }
            });
        }
    }

    /**
     * 注意，更新卡组状态要过很久才生效（实测延迟偶尔达5s）
     * @param item
     */
    private void changeDeckPublicState(MyDeckItem item) {
        if (item != null) {
            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;
            }
            VUiKit.defer().when(() -> {
                BasicResponse result = DeckSquareApiUtil.setDeckPublic(item.getDeckId(), loginToken, !item.getPublic());
                return result;
            }).fail(e -> {
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }).done(data -> {

            });
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, MyDeckItem item) {
        helper.setText(R.id.my_deck_name, item.getDeckName());
        helper.setText(R.id.deck_update_date, item.getUpdateDate());
        ImageView cardImage = helper.getView(R.id.deck_info_image);
        long code = item.getDeckCoverCard1();
        boolean isPublic = item.getPublic();
        if (isPublic) {
            helper.setText(R.id.change_show_or_hide, R.string.in_public);
            helper.getView(R.id.show_on_deck_square).setBackgroundResource(R.drawable.baseline_remove_red_eye_24);
            helper.getView(R.id.ll_switch_show).setBackgroundResource(R.drawable.button_radius_red);
        } else {
            helper.setText(R.id.change_show_or_hide, R.string.in_personal_use);
            helper.getView(R.id.show_on_deck_square).setBackgroundResource(R.drawable.closed_eyes_24);
            helper.getView(R.id.ll_switch_show).setBackgroundResource(R.drawable.button_radius_n);
        }
        if (code != 0) {
            imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.small);
        } else {
            imageLoader.bindImage(cardImage, -1, null, ImageLoader.Type.small);
        }
        helper.getView(R.id.delete_my_online_deck_btn).setOnClickListener(view -> {
            deleteMyDeckOnLine(item);
        });
        helper.getView(R.id.ll_switch_show).setOnClickListener(view -> {
            if (isPublic) {
                helper.setText(R.id.change_show_or_hide, R.string.in_personal_use);
                helper.getView(R.id.show_on_deck_square).setBackgroundResource(R.drawable.closed_eyes_24);
                helper.getView(R.id.ll_switch_show).setBackgroundResource(R.drawable.button_radius_n);
                item.setPublic(!isPublic);
            } else {
                helper.setText(R.id.change_show_or_hide, R.string.in_public);
                helper.getView(R.id.show_on_deck_square).setBackgroundResource(R.drawable.baseline_remove_red_eye_24);
                helper.getView(R.id.ll_switch_show).setBackgroundResource(R.drawable.button_radius_red);
                item.setPublic(isPublic);
            }
            LogUtil.i(TAG, "current " + item.toString());
            changeDeckPublicState(item);
        });
        helper.getView(R.id.ll_download).setOnClickListener(view -> {
            //TODO
            //点击“我的卡组”中的某个卡组后，弹出dialog，dialog根据卡组的同步情况自动显示对应的下载/上传按钮
            DeckFile deckFile = new DeckFile(item.getDeckId(), DeckType.ServerType.MY_SQUARE);
            mDialogListener.onDismiss();
            onDeckMenuListener.onDeckSelect(deckFile);
        });

    }

}
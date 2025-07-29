package cn.garymb.ygomobile.ui.cards.deck_square;

import static cn.garymb.ygomobile.ui.cards.DeckManagerFragment.originalData;
import static cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareFileUtil.convertToGMTDate;

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
        if (originalData.isEmpty()){
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
                if (serverDecks != null) {//将服务端的卡组也放到LocalDecks中
                    originalData.clear();//虽然判断originalData是空的才会执行到这里，但还是写上保险
                    originalData.addAll(serverDecks); // 保存原始数据
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
            addData(originalData);
            notifyDataSetChanged();
        }


    }

    // 筛选函数
    public void filter(String keyword) {
        List<MyOnlineDeckDetail> filteredList = new ArrayList<>();
        if (keyword.isEmpty()) {
            // 如果关键词为空，则显示所有数据
            filteredList.addAll(originalData);
        } else {
            // 遍历原始数据，筛选出包含关键词的item
            for (MyOnlineDeckDetail item : originalData) {
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

    private void deleteMyDeckOnLine(MyOnlineDeckDetail item) {
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
        helper.setText(R.id.my_online_deck_type,item.getDeckType().equals("") ? "" : "-"+item.getDeckType()+"-");
        helper.setText(R.id.my_deck_name, item.getDeckName());
        helper.setText(R.id.deck_update_date, convertToGMTDate(item.getDeckUpdateDate()));
        ImageView cardImage = helper.getView(R.id.deck_info_image);
        long code = item.getDeckCoverCard1();
        if (item.isPublic()) {
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
            if (item.isPublic()) {
                helper.setText(R.id.change_show_or_hide, R.string.in_personal_use);
                helper.getView(R.id.show_on_deck_square).setBackgroundResource(R.drawable.closed_eyes_24);
                helper.getView(R.id.ll_switch_show).setBackgroundResource(R.drawable.button_radius_n);
                item.setPublic(false); // 关闭公开状态
            } else {
                helper.setText(R.id.change_show_or_hide, R.string.in_public);
                helper.getView(R.id.show_on_deck_square).setBackgroundResource(R.drawable.baseline_remove_red_eye_24);
                helper.getView(R.id.ll_switch_show).setBackgroundResource(R.drawable.button_radius_red);
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
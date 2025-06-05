package cn.garymb.ygomobile.deck_square;


import android.os.CountDownTimer;
import android.util.Log;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.deck_square.api_response.PushDeckResponse;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

//提供“我的”卡组数据，打开后先从sharePreference查询，没有则从服务器查询，然后缓存到sharePreference
public class MyDeckListAdapter extends BaseQuickAdapter<MyDeckItem, BaseViewHolder> {
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();
    private ImageLoader imageLoader;
    // 添加监听器变量
    private OnDeckDeleteListener deleteListener;
    private OnDeckManipulateListener manipulateListener;

    public MyDeckListAdapter(int layoutResId) {
        super(layoutResId);

        imageLoader = new ImageLoader();
    }

    public interface OnDeckManipulateListener {
        void onDeckHide();

        void onDeckCancelHide();

        void onDeckDeleteFinished();
    }

    // 在MyDeckListAdapter中添加接口
    public interface OnDeckDeleteListener {
        void onDeckDeleteStarted();

        void onDeckDeleteProgress(int secondsRemaining);

        void onDeckDeleteFinished();
    }

    // 添加设置监听器的方法
    public void setOnDeckDeleteListener(OnDeckDeleteListener listener) {
        this.deleteListener = listener;
    }

    private void startButtonCountdown() {
        final int countdownSeconds = 3;

        new CountDownTimer(countdownSeconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                if (deleteListener != null) {
                    deleteListener.onDeckDeleteProgress(secondsLeft);
                }
            }

            @Override
            public void onFinish() {
                if (deleteListener != null) {
                    deleteListener.onDeckDeleteFinished();
                }
            }
        }.start();
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
                    item.setDeckSouce(1);
                    item.setDeckId(detail.getDeckId());
                    item.setUserId(detail.getUserId());
                    item.setUpdateDate(detail.getDeckUpdateDate());
                    item.setPublic(detail.isPublic());
                    myOnlineDecks.add(item);
                }
            }

            LogUtil.i(TAG, "load mycard from server done");
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

    private void deleteMyDeckOnLine(MyDeckItem item) {

        if (item != null) {


            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;

            }
            //放在登录检查后面，否则：如果未通过登录检查，本函数return后将无法调用onDeckDeleteFinished()
            if (deleteListener != null) {
                deleteListener.onDeckDeleteStarted();
            }
            VUiKit.defer().when(() -> {
                PushDeckResponse result = DeckSquareApiUtil.deleteDeck(item.getDeckId(), loginToken);
                return result;
            }).fail(e -> {
                if (deleteListener != null) {
                    deleteListener.onDeckDeleteFinished();
                }
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }).done(data -> {
                if (data.isData()) {
                    /*
                     *服务器的api有问题：获取指定用户的卡组列表(无已删卡组)
                     *删除成功后，通过http://rarnu.xyz:38383/api/mdpro3/sync/795610/nodel接口查询用户卡组时
                     *要等待2~3秒api响应内容才会对应更新
                     */
                    YGOUtil.showTextToast("删除成功，3秒后服务器将完成同步");
                    remove(item);
                    // 开始倒计时
                    startButtonCountdown();
                } else {
                    if (deleteListener != null) {
                        deleteListener.onDeckDeleteFinished();
                    }
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

            //放在登录检查后面，否则：如果未通过登录检查，本函数return后将无法调用onDeckDeleteFinished()
            if (deleteListener != null) {
                deleteListener.onDeckDeleteStarted();
            }

            VUiKit.defer().when(() -> {
                BasicResponse result = DeckSquareApiUtil.setDeckPublic(item.getDeckId(), loginToken, !item.getPublic());
                return result;
            }).fail(e -> {
                if (deleteListener != null) {
                    deleteListener.onDeckDeleteFinished();
                }
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }).done(data -> {
                if (data.isMessageTrue()) {
                    /*
                     *服务器的api有问题：获取指定用户的卡组列表(无已删卡组)
                     *删除成功后，通过http://rarnu.xyz:38383/api/mdpro3/sync/795610/nodel接口查询用户卡组时
                     *要等待2~3秒api响应内容才会对应更新
                     */
                    if (item.getPublic()) {//注意，item是修改前的值，因此item.isPublic为true代表修改前为公开卡组
                        YGOUtil.showTextToast("已将卡组设为私有");
                    } else {

                        YGOUtil.showTextToast("已将卡组公开");
                    }
                    startButtonCountdown();
                } else {
                    YGOUtil.showTextToast("操作失败");
                }
            });
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, MyDeckItem item) {
        helper.setText(R.id.my_deck_name, item.getDeckName());
        helper.setText(R.id.deck_update_date, item.getUpdateDate());
        ImageView cardImage = helper.getView(R.id.deck_info_image);
        long code = item.getDeckCoverCard1();

        if (item.getPublic()) {
            helper.setText(R.id.change_show_or_hide, R.string.in_public);
        } else {

            helper.setText(R.id.change_show_or_hide, R.string.in_personal_use);
        }
        LogUtil.i(TAG, code + " " + item.getDeckName());
        if (code != 0) {
            imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.small);
        } else {

            imageLoader.bindImage(cardImage, -1, null, ImageLoader.Type.small);
        }
        helper.getView(R.id.delete_my_online_deck_btn).setOnClickListener(view -> {
            deleteMyDeckOnLine(item);
        });
        helper.getView(R.id.ll_switch_show).setOnClickListener(view -> {

            LogUtil.i(TAG, "current " + item.toString());
            changeDeckPublicState(item);
        });
//        else if (item.getDeckSouce() == 1) {
//            helper.setText(R.id.my_deck_id, item.getDeckId());
//            imageView.setImageResource(R.drawable.ic_server_download);
//        }


//        long code = item.getDeckCoverCard1();
//        LogUtil.i(TAG, code + " " + item.getDeckName());
//        if (code != 0) {
//            imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.small);
//        }
//

        // ImageView imageview = helper.getView(R.id.ex_card_image);
        //the function cn.garymb.ygomobile.loader.ImageLoader.bindT(...)
        //cn.garymb.ygomobile.loader.ImageLoader.setDefaults(...)
        //is a private function,so I copied the content of it to here
        /* 如果查不到版本号，则不显示图片 */
        /* 如果能查到版本号，则显示图片，利用glide的signature，将版本号和url作为signature，由glide判断是否使用缓存 */


    }

}
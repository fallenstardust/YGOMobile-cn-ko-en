package cn.garymb.ygomobile.ui.cards;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.feihua.dialogutils.util.DialogUtils;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.adapters.BaseAdapterPlus;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.CardManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/***
 * 卡片详情
 */
public class CardDetail extends BaseAdapterPlus.BaseViewHolder {

    private static final int TYPE_DOWNLOAD_CARD_IMAGE_OK = 0;
    private static final int TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION = 1;
    private static final int TYPE_DOWNLOAD_CARD_IMAGE_ING = 2;

    private static final String TAG = "CardDetail";
    private static CardManager cardManager;
    private ImageView cardImage;
    private TextView name;
    private TextView desc;
    private TextView level;
    private TextView type;
    private TextView race;
    private TextView cardAtk;
    private TextView cardDef;

    private TextView setname;
    private TextView otView;
    private TextView attrView;
    private View monsterlayout;
    private View close;
    private View faq;
    private View addMain;
    private View addSide;
    private TextView cardcode;
    private View lb_setcode;
    private ImageLoader imageLoader;
    private View mImageOpen, atkdefView;

    private BaseActivity mContext;
    private StringManager mStringManager;
    private int curPosition;
    private Card mCardInfo;
    private CardListProvider mProvider;
    private OnCardClickListener mListener;
    private DialogUtils dialog;
    private ImageView photoView;
    private LinearLayout ll_bar;
    private ProgressBar pb_loading;
    private TextView tv_loading;
    private boolean isDownloadCardImage = true;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_DOWNLOAD_CARD_IMAGE_OK:
                    isDownloadCardImage = true;
                    ll_bar.startAnimation(AnimationUtils.loadAnimation(context, R.anim.out_from_bottom));
                    ll_bar.setVisibility(View.GONE);
                    imageLoader.bindImage(photoView, msg.arg1, null, true);
                    imageLoader.bindImage(cardImage, msg.arg1, null, true);
                    break;
                case TYPE_DOWNLOAD_CARD_IMAGE_ING:
                    tv_loading.setText(msg.arg1 + "%");
                    pb_loading.setProgress(msg.arg1);
                    break;
                case TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION:
                    isDownloadCardImage = true;
                    ll_bar.startAnimation(AnimationUtils.loadAnimation(context, R.anim.out_from_bottom));
                    ll_bar.setVisibility(View.GONE);
                    YGOUtil.show("error" + msg.obj);
                    break;

            }
        }
    };


    public CardDetail(BaseActivity context, ImageLoader imageLoader, StringManager stringManager) {
        super(LayoutInflater.from(context).inflate(R.layout.dialog_cardinfo, null));
        mContext = context;
        cardImage = bind(R.id.card_image);
        this.imageLoader = imageLoader;
        mStringManager = stringManager;
        name = bind(R.id.text_name);
        desc = bind(R.id.text_desc);
        close = bind(R.id.btn_close);
        cardcode = bind(R.id.card_code);
        level = bind(R.id.card_level);
        type = bind(R.id.card_type);
        faq = bind(R.id.btn_faq);
        cardAtk = bind(R.id.card_atk);
        cardDef = bind(R.id.card_def);
        atkdefView = bind(R.id.layout_atkdef2);
        mImageOpen = bind(R.id.image_control);

        monsterlayout = bind(R.id.layout_monster);
        race = bind(R.id.card_race);
        setname = bind(R.id.card_setname);
        addMain = bind(R.id.btn_add_main);
        addSide = bind(R.id.btn_add_side);
        otView = bind(R.id.card_ot);
        attrView = bind(R.id.card_attribute);
        lb_setcode = bind(R.id.label_setcode);

        if (cardManager == null) {
            Log.e("CardDetail","加载卡片信息");
            cardManager = new CardManager(AppsSettings.get().getDataBaseFile().getAbsolutePath(), null);
            //加载数据库中所有卡片卡片
            cardManager.loadCards();
        }
        close.setOnClickListener((v) -> {
            if (mListener != null) {
                mListener.onClose();
            }
        });
        addMain.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                mListener.onAddMainCard(cardInfo);
            }
        });
        addSide.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                mListener.onAddSideCard(cardInfo);
            }
        });
        faq.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                mListener.onOpenUrl(cardInfo);
            }
        });
        bind(R.id.lastone).setOnClickListener((v) -> {
            onPreCard();
        });
        bind(R.id.nextone).setOnClickListener((v) -> {
            onNextCard();
        });
    }

    public ImageView getCardImage() {
        return cardImage;
    }

    public void hideClose() {
        close.setVisibility(View.GONE);
    }

    public void showAdd() {
        addSide.setVisibility(View.VISIBLE);
        addMain.setVisibility(View.VISIBLE);
    }

    public View getView() {
        return view;
    }

    public BaseActivity getContext() {
        return mContext;
    }

    public void setOnCardClickListener(OnCardClickListener listener) {
        mListener = listener;
    }

    public void bind(Card cardInfo, final int position, final CardListProvider provider) {
        curPosition = position;
        mProvider = provider;
        if (cardInfo != null) {
            setCardInfo(cardInfo);
        }
    }

    public int getCurPosition() {
        return curPosition;
    }

    public CardListProvider getProvider() {
        return mProvider;
    }

    public Card getCardInfo() {
        return mCardInfo;
    }

    private void setCardInfo(Card cardInfo) {
        if (cardInfo == null) return;
        mCardInfo = cardInfo;
        imageLoader.bindImage(cardImage, cardInfo.Code, null, true);
        dialog = DialogUtils.getdx(context);
        cardImage.setOnClickListener((v) -> {
            showCardImageDetail(cardInfo.Code);
        });
        name.setText(cardInfo.Name);
        desc.setText(cardInfo.Desc);
        if (cardInfo.Alias != 0) {
            cardcode.setText(String.format("%08d", cardInfo.Alias));
        } else {
            cardcode.setText(String.format("%08d", cardInfo.Code));
        }
        type.setText(CardUtils.getAllTypeString(cardInfo, mStringManager).replace("/", "|"));
        attrView.setText(mStringManager.getAttributeString(cardInfo.Attribute));
        otView.setText(mStringManager.getOtString(cardInfo.Ot, "" + cardInfo.Ot));
        long[] sets = cardInfo.getSetCode();
        setname.setText("");
        int index = 0;
        for (long set : sets) {
            if (set > 0) {
                if (index != 0) {
                    setname.append("\n");
                }
                setname.append("" + mStringManager.getSetName(set));
                index++;
            }
        }

        if (TextUtils.isEmpty(setname.getText())) {
            setname.setVisibility(View.INVISIBLE);
            lb_setcode.setVisibility(View.INVISIBLE);
        } else {
            setname.setVisibility(View.VISIBLE);
            lb_setcode.setVisibility(View.VISIBLE);
        }
        if (cardInfo.isType(CardType.Monster)) {
            if (cardInfo.isType(CardType.Link)) {
                level.setVisibility(View.INVISIBLE);
            } else {
                level.setVisibility(View.VISIBLE);
            }
            atkdefView.setVisibility(View.VISIBLE);
            monsterlayout.setVisibility(View.VISIBLE);
            race.setVisibility(View.VISIBLE);
            String star = "★" + cardInfo.getStar();
           /* for (int i = 0; i < cardInfo.getStar(); i++) {
                star += "★";
            }*/
            level.setText(star);
            if (cardInfo.isType(CardType.Xyz)) {
                level.setTextColor(context.getResources().getColor(R.color.star_rank));
            } else {
                level.setTextColor(context.getResources().getColor(R.color.star));
            }
            cardAtk.setText((cardInfo.Attack < 0 ? "?" : String.valueOf(cardInfo.Attack)));
            if (cardInfo.isType(CardType.Link)) {
                cardDef.setText((cardInfo.getStar() < 0 ? "?" : "LINK-" + String.valueOf(cardInfo.getStar())));
            } else {
                cardDef.setText((cardInfo.Defense < 0 ? "?" : String.valueOf(cardInfo.Defense)));
            }
            race.setText(mStringManager.getRaceString(cardInfo.Race));
        } else {
            atkdefView.setVisibility(View.GONE);
            race.setVisibility(View.GONE);
            monsterlayout.setVisibility(View.GONE);
            level.setVisibility(View.GONE);
        }
    }

    private void showCardImageDetail(int code) {
        AppsSettings appsSettings = AppsSettings.get();
        File file = new File(appsSettings.getCardImagePath(code));
        View view = dialog.initDialog(context, R.layout.dialog_photo);
        dialog.setDialogWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        photoView = view.findViewById(R.id.photoView);
        ll_bar = view.findViewById(R.id.ll_bar);
        pb_loading = view.findViewById(R.id.pb_loading);
        tv_loading = view.findViewById(R.id.tv_name);
        pb_loading.setMax(100);
        photoView.setOnClickListener(View -> {
            dialog.dis();
        });

        photoView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isDownloadCardImage || cardManager.getCard(code) == null)
                    return false;
                DialogPlus dialogPlus = new DialogPlus(context);
                dialogPlus.setMessage(R.string.tip_redownload);
                dialogPlus.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                dialogPlus.setLeftButtonText(R.string.Download);
                dialogPlus.setRightButtonText(R.string.Cancel);
                dialogPlus.setRightButtonListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialogPlus.setLeftButtonListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        downloadCardImage(code, file);
                    }
                });
                dialogPlus.show();
                return true;
            }
        });

        //先显示普通卡片大图，判断如果没有高清图就下载
        imageLoader.bindImage(photoView, code, null, true);

        if (!file.exists()) {
            downloadCardImage(code, file);
        }

    }

    private void downloadCardImage(int code, File file) {
        if (cardManager.getCard(code) == null) {
            YGOUtil.show(context.getString(R.string.tip_expansions_image));
            return;
        }
        isDownloadCardImage = false;
        ll_bar.setVisibility(View.VISIBLE);
        ll_bar.startAnimation(AnimationUtils.loadAnimation(context, R.anim.in_from_top));
        DownloadUtil.get().download(YGOUtil.getCardImageDetailUrl(code), file.getParent(), file.getName(), new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                if (file.length() < 50 * 1024) {
                    FileUtils.deleteFile(file);
                    Message message = new Message();
                    message.what = TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION;
                    message.obj = context.getString(R.string.download_image_error);
                    handler.sendMessage(message);
                } else {
                    Message message = new Message();
                    message.what = TYPE_DOWNLOAD_CARD_IMAGE_OK;
                    message.arg1 = code;
                    handler.sendMessage(message);
                }
            }

            @Override
            public void onDownloading(int progress) {
                Message message = new Message();
                message.what = TYPE_DOWNLOAD_CARD_IMAGE_ING;
                message.arg1 = progress;
                handler.sendMessage(message);
            }

            @Override
            public void onDownloadFailed(Exception e) {
                //下载失败后删除下载的文件
                FileUtils.deleteFile(file);
                downloadCardImage(code, file);

                Message message = new Message();
                message.what = TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION;
                message.obj = e.toString();
                handler.sendMessage(message);
            }
        });

    }

    public void onPreCard() {
        int position = getCurPosition();
        CardListProvider provider = getProvider();
        if (position == 0) {
            getContext().showToast(R.string.already_top, Toast.LENGTH_SHORT);
        } else {
            int index = position;
            do {
                if (index == 0) {
                    getContext().showToast(R.string.already_top, Toast.LENGTH_SHORT);
                    return;
                } else {
                    index--;
                }
            } while (provider.getCard(index) == null || provider.getCard(index).Name == null || provider.getCard(position).Name.equals(provider.getCard(index).Name));

            bind(provider.getCard(index), index, provider);
            if (position == 1) {
                getContext().showToast(R.string.already_top, Toast.LENGTH_SHORT);
            }
        }
    }

    public void onNextCard() {
        int position = getCurPosition();
        CardListProvider provider = getProvider();
        if (position < provider.getCardsCount() - 1) {
            int index = position;
            do {
                if (index == provider.getCardsCount() - 1) {
                    getContext().showToast(R.string.already_end, Toast.LENGTH_SHORT);
                    return;
                } else {
                    index++;
                }
            } while (provider.getCard(index) == null || provider.getCard(index).Name == null || provider.getCard(position).Name.equals(provider.getCard(index).Name));

            bind(provider.getCard(index), index, provider);
            if (position == provider.getCardsCount() - 1) {
                getContext().showToast(R.string.already_end, Toast.LENGTH_SHORT);
            }
        } else {
            getContext().showToast(R.string.already_end, Toast.LENGTH_SHORT);
        }
    }

    private <T extends View> T bind(int id) {
        return (T) findViewById(id);
    }

    public interface OnCardClickListener {
        void onOpenUrl(Card cardInfo);

        void onAddMainCard(Card cardInfo);

        void onAddSideCard(Card cardInfo);

        void onClose();
    }

    public static class DefaultOnCardClickListener implements OnCardClickListener {
        public DefaultOnCardClickListener() {
        }

        @Override
        public void onOpenUrl(Card cardInfo) {

        }

        @Override
        public void onClose() {
        }

        @Override
        public void onAddSideCard(Card cardInfo) {

        }

        @Override
        public void onAddMainCard(Card cardInfo) {

        }
    }

}

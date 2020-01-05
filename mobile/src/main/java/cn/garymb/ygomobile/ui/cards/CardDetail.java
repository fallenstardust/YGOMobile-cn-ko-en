package cn.garymb.ygomobile.ui.cards;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.feihua.dialogutils.util.DialogUtils;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.adapters.BaseAdapterPlus;
import cn.garymb.ygomobile.utils.CardUtils;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/***
 * 卡片详情
 */
public class CardDetail extends BaseAdapterPlus.BaseViewHolder {
    private static final String TAG = "CardDetail";
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
            View view = dialog.initDialog(context, R.layout.dialog_photo);
            ImageView photoView = view.findViewById(R.id.photoView);
            photoView.setOnClickListener(View -> {
                dialog.dis();
            });
            imageLoader.bindImage(photoView, cardInfo.Code, null, true);
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

    public void onPreCard() {
        int position = getCurPosition();
        CardListProvider provider = getProvider();
        if (position == 0) {
            getContext().showToast("已经是第一张啦", Toast.LENGTH_SHORT);
        } else {
            int index = position;
            do {
                if (index == 0) {
                    getContext().showToast("已经是第一张啦", Toast.LENGTH_SHORT);
                    return;
                } else {
                    index--;
                }
            } while (provider.getCard(index) == null || provider.getCard(index).Name == null || provider.getCard(position).Name.equals(provider.getCard(index).Name));

            bind(provider.getCard(index), index, provider);
            if (position == 1) {
                getContext().showToast("已经是第一张啦", Toast.LENGTH_SHORT);
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
                    getContext().showToast("已经是最后一张啦", Toast.LENGTH_SHORT);
                    return;
                } else {
                    index++;
                }
            } while (provider.getCard(index) == null || provider.getCard(index).Name == null || provider.getCard(position).Name.equals(provider.getCard(index).Name));

            bind(provider.getCard(index), index, provider);
            if (position == provider.getCardsCount() - 1) {
                getContext().showToast("已经是最后一张啦", Toast.LENGTH_SHORT);
            }
        } else {
            getContext().showToast("已经是最后一张啦", Toast.LENGTH_SHORT);
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

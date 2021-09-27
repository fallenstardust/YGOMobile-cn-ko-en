package cn.garymb.ygomobile.ui.cards;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.CardUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

import static android.view.View.inflate;
import static android.view.View.resolveSize;

public class CardDetailRandom {
    private View viewCardDetail;
    private ImageView cardImage;
    private TextView name;
    private TextView desc;
    private TextView level;
    private TextView type;
    private TextView race;
    private TextView cardAtk;
    private TextView cardDef;
    private TextView attrView;
    private View monsterlayout;
    private View atkdefView, textdefView;
    private StringManager mStringManager;
    private Context mContext;

    private static CardDetailRandom sCardDetailRandom = null;

    private CardDetailRandom(Context context, Card cardInfo) {
        mContext = context;
        viewCardDetail = inflate(context, R.layout.dialog_cardinfo_small, null);
        cardImage = viewCardDetail.findViewById(R.id.card_image_toast);
        name = viewCardDetail.findViewById(R.id.card_name_toast);
        monsterlayout = viewCardDetail.findViewById(R.id.star_attr_race_toast);
        level = viewCardDetail.findViewById(R.id.card_level_toast);
        race = viewCardDetail.findViewById(R.id.card_race_toast);
        attrView = viewCardDetail.findViewById(R.id.card_attr_toast);
        type = viewCardDetail.findViewById(R.id.card_type_toast);
        cardAtk = viewCardDetail.findViewById(R.id.card_atk_toast);
        cardDef = viewCardDetail.findViewById(R.id.card_def_toast);
        atkdefView = viewCardDetail.findViewById(R.id.layout_atkdef2_toast);
        textdefView = viewCardDetail.findViewById(R.id.TextDef_toast);
        desc = viewCardDetail.findViewById(R.id.text_desc_toast);

        mStringManager = DataManager.get().getStringManager();
        name.setText(cardInfo.Name);
        type.setText(CardUtils.getAllTypeString(cardInfo, mStringManager).replace("/", "|"));
        attrView.setText(mStringManager.getAttributeString(cardInfo.Attribute));
        if (cardInfo.Desc.length() >= 100) desc.setTextSize(10);
        if (cardInfo.Desc.length() >= 160) desc.setTextSize(9);
        if (cardInfo.Desc.length() >= 220) desc.setTextSize(8);
        desc.setText(cardInfo.Desc);
        if (cardInfo.isType(CardType.Monster)) {
            atkdefView.setVisibility(View.VISIBLE);
            race.setVisibility(View.VISIBLE);
            String star = "★" + cardInfo.getStar();
            level.setText(star);
            if (cardInfo.isType(CardType.Xyz)) {
                level.setTextColor(context.getResources().getColor(R.color.star_rank));
            } else {
                level.setTextColor(context.getResources().getColor(R.color.star));
            }
            cardAtk.setText((cardInfo.Attack < 0 ? "?" : String.valueOf(cardInfo.Attack)));
            //连接怪兽设置
            if (cardInfo.isType(CardType.Link)) {
                level.setVisibility(View.GONE);
                textdefView.setVisibility(View.INVISIBLE);
                cardDef.setText((cardInfo.getStar() < 0 ? "?" : "LINK-" + String.valueOf(cardInfo.getStar())));
            } else {
                level.setVisibility(View.VISIBLE);
                textdefView.setVisibility(View.VISIBLE);
                cardDef.setText((cardInfo.Defense < 0 ? "?" : String.valueOf(cardInfo.Defense)));
            }
            race.setText(mStringManager.getRaceString(cardInfo.Race));
        } else {
            atkdefView.setVisibility(View.GONE);
            monsterlayout.setVisibility(View.GONE);
        }
        viewCardDetail.setRotationY(5);
    }

    public static CardDetailRandom genRandomCardDetail(Context context, ImageLoader imageLoader, Card cardInfo) {
        if (cardInfo == null) return null;
        CardDetailRandom cardDetailRandom = new CardDetailRandom(context, cardInfo);
        cardDetailRandom.bindCardImage(imageLoader, cardInfo);
        sCardDetailRandom = cardDetailRandom;
        return cardDetailRandom;
    }

    public void bindCardImage(ImageLoader imageLoader, Card cardInfo) {
        imageLoader.bindImage(cardImage, cardInfo, ImageLoader.Type.origin);
    }

    public View getView() {
        return viewCardDetail;
    }

    public void show(){
        Toast toast = new Toast(mContext);
        toast.setView(viewCardDetail);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.LEFT, 0, 0);
        toast.show();
    }
}

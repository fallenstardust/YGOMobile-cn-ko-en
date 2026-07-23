package cn.garymb.ygomobile.render;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

public class CardDetailPanel {

    private final LinearLayout layout;
    private final ImageView ivCardImage;
    private final TextView tvCardName;
    private final TextView tvCardSetname;
    private final TextView tvCardAttr;
    private final TextView tvCardLevel;
    private final TextView tvCardDesc;
    private final ScrollView svCardDesc;
    private final ImageLoader imageLoader;

    private int currentCardCode = -1;

    public CardDetailPanel(View rootView, ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
        layout = rootView.findViewById(R.id.layout_card_detail);
        ivCardImage = rootView.findViewById(R.id.iv_card_image);
        tvCardName = rootView.findViewById(R.id.tv_card_name);
        tvCardSetname = rootView.findViewById(R.id.tv_card_setname);
        tvCardAttr = rootView.findViewById(R.id.tv_card_attr);
        tvCardLevel = rootView.findViewById(R.id.tv_card_level);
        tvCardDesc = rootView.findViewById(R.id.tv_card_desc);
        svCardDesc = rootView.findViewById(R.id.sv_card_desc);
    }

    public void showDefault() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (ivCardImage != null) {
            ivCardImage.setImageResource(R.drawable.unknown);
        }
        if (tvCardName != null) {
            tvCardName.setText("???");
        }
        if (tvCardSetname != null) {
            tvCardSetname.setText("");
            tvCardSetname.setVisibility(View.GONE);
        }
        if (tvCardAttr != null) {
            tvCardAttr.setText("");
        }
        if (tvCardLevel != null) {
            tvCardLevel.setText("");
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText("点击场上的卡片查看详细信息");
        }
    }

    public void showCard(GameField.ClientCard clientCard) {
        if (clientCard == null || clientCard.code <= 0) {
            showDefault();
            return;
        }

        int code = clientCard.code;
        Card cardData = DataManager.get().getCardManager().getCard(code);
        if (cardData == null) {
            showDefault();
            return;
        }

        currentCardCode = code;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }

        bindCardImage(code);
        bindCardName(cardData, code);
        bindCardSetname(cardData);
        bindCardAttr(cardData, clientCard);
        bindCardLevel(cardData, clientCard);
        bindCardDesc(cardData);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    public void hide() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.GONE);
        }
    }

    public boolean isShowing() {
        return layout != null && layout.getVisibility() == View.VISIBLE;
    }

    public int getCurrentCardCode() {
        return currentCardCode;
    }

    private void bindCardImage(int code) {
        if (imageLoader != null && ivCardImage != null) {
            imageLoader.bindImage(ivCardImage, code, ImageLoader.Type.origin);
        }
    }

    private void bindCardName(Card cardData, int code) {
        if (tvCardName == null) return;
        String name = cardData.Name;
        if (name == null || name.isEmpty()) name = "Unknown Card";
        tvCardName.setText(name + "[" + code + "]");
    }

    private void bindCardSetname(Card cardData) {
        if (tvCardSetname == null) return;
        StringManager sm = DataManager.get().getStringManager();
        long[] setCodes = cardData.getSetCode();
        StringBuilder sb = new StringBuilder();
        boolean hasSet = false;
        for (long sc : setCodes) {
            if (sc == 0) continue;
            if (hasSet) sb.append("|");
            sb.append(sm.getSetName(sc));
            hasSet = true;
        }
        if (hasSet) {
            tvCardSetname.setText("字段：" + sb);
            tvCardSetname.setVisibility(View.VISIBLE);
        } else {
            tvCardSetname.setVisibility(View.GONE);
        }
    }

    private void bindCardAttr(Card cardData, GameField.ClientCard clientCard) {
        if (tvCardAttr == null) return;
        StringManager sm = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder();

        String typeStr = sm.getTypeString(cardData.Type);
        sb.append("[").append(typeStr).append("]");

        if (cardData.isType(CardType.Monster)) {
            String raceStr = sm.getRaceString(cardData.Race);
            String attrStr = sm.getAttributeString(cardData.Attribute);
            sb.append(" ").append(raceStr).append("/").append(attrStr);
        }

        tvCardAttr.setText(sb.toString());
    }

    private void bindCardLevel(Card cardData, GameField.ClientCard clientCard) {
        if (tvCardLevel == null) return;

        if (cardData.isType(CardType.Spell) || cardData.isType(CardType.Trap)) {
            tvCardLevel.setText("");
            tvCardLevel.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (cardData.isLink()) {
            sb.append("LINK-").append(cardData.getLinkNumber());
        } else if (cardData.isType(CardType.Xyz)) {
            int rank = clientCard.rank > 0 ? clientCard.rank : cardData.getStar();
            sb.append("☆").append(rank);
        } else if (cardData.isType(CardType.Monster)) {
            int level = clientCard.level > 0 ? clientCard.level : cardData.getStar();
            sb.append("★").append(level);
        }

        if (cardData.isType(CardType.Monster)) {
            int atk = clientCard.isFaceUp() ? clientCard.attack : cardData.Attack;
            int def = clientCard.isFaceUp() ? clientCard.defense : cardData.Defense;
            String atkStr = atk < 0 ? "?" : String.valueOf(atk);
            String defStr = cardData.isLink() ? "-" : (def < 0 ? "?" : String.valueOf(def));
            if (sb.length() > 0) sb.append("  ");
            sb.append(atkStr).append("/").append(defStr);
        }

        if (cardData.LeftScale > 0 || cardData.RightScale > 0) {
            int lsc = clientCard.lScale > 0 ? clientCard.lScale : cardData.LeftScale;
            int rsc = clientCard.rScale > 0 ? clientCard.rScale : cardData.RightScale;
            if (sb.length() > 0) sb.append("  ");
            sb.append("灵摆 ").append(lsc).append("/").append(rsc);
        }

        tvCardLevel.setText(sb.toString());
        tvCardLevel.setVisibility(View.VISIBLE);
    }

    private void bindCardDesc(Card cardData) {
        if (tvCardDesc == null) return;
        String desc = cardData.Desc;
        if (desc == null || desc.isEmpty()) {
            tvCardDesc.setText("");
        } else {
            tvCardDesc.setText(desc);
        }
    }
}
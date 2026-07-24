package cn.garymb.ygomobile.game;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;

import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class DeckCardAdapter extends RecyclerView.Adapter<DeckCardAdapter.CardViewHolder> {

    private final ImageLoader imageLoader;
    private final DeckEditorManager editorManager;
    private final DeckInfo.Type deckType;
    private final List<Card> cards = new ArrayList<>();
    private ImageTop mImageTop;
    private LimitList mLimitList;
    private int mCardWidth = -1;
    private int mCardHeight = -1;

    public DeckCardAdapter(ImageLoader imageLoader, DeckEditorManager editorManager, DeckInfo.Type deckType) {
        this.imageLoader = imageLoader;
        this.editorManager = editorManager;
        this.deckType = deckType;
    }

    public void setLimitList(LimitList limitList) {
        this.mLimitList = limitList;
        notifyDataSetChanged();
    }

    public void setCards(List<Card> newCards) {
        cards.clear();
        if (newCards != null) {
            cards.addAll(newCards);
        }
        notifyDataSetChanged();
    }

    public void setCardSize(int width, int height) {
        mCardWidth = width;
        mCardHeight = height;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_deck_card_horizontal, parent, false);
        if (mImageTop == null) {
            mImageTop = new ImageTop(parent.getContext());
        }
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        if (mCardWidth > 0 && mCardHeight > 0) {
            ViewGroup.LayoutParams lp = holder.cardContainer.getLayoutParams();
            lp.width = mCardWidth;
            lp.height = mCardHeight;
            holder.cardContainer.setLayoutParams(lp);
        }

        Card card = cards.get(position);
        if (card == null) {
            holder.ivCard.setImageResource(R.drawable.unknown);
            holder.ivLimitTop.setVisibility(View.GONE);
            holder.tvLimitNum.setVisibility(View.GONE);
            if (holder.tvName != null) holder.tvName.setText("");
            if (holder.tvInfo != null) holder.tvInfo.setText("");
            if (holder.tvAtkDef != null) holder.tvAtkDef.setText("");
            return;
        }

        imageLoader.bindImage(holder.ivCard, card, ImageLoader.Type.small);
        bindLimitOverlay(holder, card);
        bindCardInfo(holder, card);

        holder.itemView.setOnClickListener(v -> {
            if (deckType == null) {
                editorManager.onSearchCardClicked(card);
            } else {
                editorManager.onDeckCardClicked(deckType, holder.getAdapterPosition());
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (deckType != null) {
                editorManager.onDeckCardLongClicked(deckType, holder.getAdapterPosition());
                return true;
            }
            return false;
        });
    }

    private void bindCardInfo(@NonNull CardViewHolder holder, Card card) {
        if (holder.tvName == null || holder.tvInfo == null || holder.tvAtkDef == null) {
            return;
        }
        holder.tvName.setText(card.Name);
        StringManager sm = DataManager.get().getStringManager();
        if (card.isSpellTrap()) {
            holder.tvInfo.setText(CardUtils.getAllTypeString(card, sm));
            holder.tvAtkDef.setText("");
        } else {
            String attr = sm.getAttributeString(card.Attribute);
            String race = sm.getRaceString(card.Race);
            String atk = card.Attack < 0 ? "?" : String.valueOf(card.Attack);
            if (card.isType(CardType.Link)) {
                holder.tvInfo.setText(attr + "/" + race + "  LINK-" + card.getStar());
                holder.tvAtkDef.setText(atk + "/-");
            } else {
                String def = card.Defense < 0 ? "?" : String.valueOf(card.Defense);
                holder.tvInfo.setText(attr + "/" + race + "  ★" + card.getStar());
                holder.tvAtkDef.setText(atk + "/" + def);
            }
        }
    }

    private void bindLimitOverlay(@NonNull CardViewHolder holder, Card card) {
        if (mImageTop == null || mLimitList == null) {
            holder.ivLimitTop.setVisibility(View.GONE);
            holder.tvLimitNum.setVisibility(View.GONE);
            return;
        }

        if (mLimitList.check(card, LimitType.Forbidden)) {
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.forbidden);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText("");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.white));
        } else if (mLimitList.check(card, LimitType.Limit)) {
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.limit);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText("1");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
        } else if (mLimitList.check(card, LimitType.SemiLimit)) {
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.semiLimit);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText("2");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.yellow));
        } else if (mLimitList.check(card, LimitType.GeneSys)) {
            Integer creditValue = 0;
            if (mLimitList.getCredits() != null) {
                creditValue = mLimitList.getCredits().get(card.getCode());
            }
            holder.ivLimitTop.setVisibility(View.VISIBLE);
            holder.ivLimitTop.setImageBitmap(mImageTop.credits);
            holder.tvLimitNum.setVisibility(View.VISIBLE);
            holder.tvLimitNum.setText(creditValue != null ? creditValue.toString() : "0");
            holder.tvLimitNum.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
            holder.tvLimitNum.setTextSize((creditValue != null && creditValue > -10 && creditValue < 100) ? 8 : 6);
        } else {
            holder.ivLimitTop.setVisibility(View.GONE);
            holder.tvLimitNum.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        ViewGroup cardContainer;
        ImageView ivCard;
        ImageView ivLimitTop;
        TextView tvLimitNum;
        TextView tvName;
        TextView tvInfo;
        TextView tvAtkDef;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardContainer = itemView.findViewById(R.id.card_container);
            ivCard = itemView.findViewById(R.id.iv_deck_card_item);
            ivLimitTop = itemView.findViewById(R.id.iv_deck_card_limit_top);
            tvLimitNum = itemView.findViewById(R.id.tv_deck_limit_num);
            tvName = itemView.findViewById(R.id.tv_deck_card_name);
            tvInfo = itemView.findViewById(R.id.tv_deck_card_info);
            tvAtkDef = itemView.findViewById(R.id.tv_deck_card_atkdef);
        }
    }
}
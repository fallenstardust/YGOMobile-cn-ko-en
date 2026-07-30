package cn.garymb.ygomobile.ui.cards.deck_square;

import com.chad.library.adapter.base.entity.MultiItemEntity;

import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;

public class DeckListItem implements MultiItemEntity {
    public static final int TYPE_SECTION_HEADER = 0;
    public static final int TYPE_DECK_ITEM = 1;

    private int itemType;
    private String sectionName;
    private int sectionItemCount;
    private boolean expanded;
    private MyOnlineDeckDetail deckDetail;

    public DeckListItem(String sectionName, int sectionItemCount, boolean expanded) {
        this.itemType = TYPE_SECTION_HEADER;
        this.sectionName = sectionName;
        this.sectionItemCount = sectionItemCount;
        this.expanded = expanded;
    }

    public DeckListItem(MyOnlineDeckDetail deckDetail) {
        this.itemType = TYPE_DECK_ITEM;
        this.deckDetail = deckDetail;
    }

    @Override
    public int getItemType() {
        return itemType;
    }

    public String getSectionName() {
        return sectionName;
    }

    public int getSectionItemCount() {
        return sectionItemCount;
    }

    public void setSectionItemCount(int count) {
        this.sectionItemCount = count;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public MyOnlineDeckDetail getDeckDetail() {
        return deckDetail;
    }
}

package cn.garymb.ygomobile.ui.cards.deck;

/**
 * DeckItemType的不同值对应着，recyclerView中的item的不同布局形式
 */
public enum DeckItemType {
    MainLabel,//对应“主卡组：60怪兽：21...“这种分隔标签
    MainCard,//对应主卡组中的卡图控件
    ExtraLabel,//额外卡组
    ExtraCard,//对应额外卡组中的卡图控件
    SideLabel,//副卡组
    SideCard,//对应副卡组中的卡图控件
    Space,//对应占位的卡图控件
    Pack
}

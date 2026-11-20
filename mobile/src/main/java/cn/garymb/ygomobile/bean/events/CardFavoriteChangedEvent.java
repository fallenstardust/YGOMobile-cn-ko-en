package cn.garymb.ygomobile.bean.events;

/**
 * 收藏夹内容变化事件：收藏卡片被加入或移除时（{@link cn.garymb.ygomobile.ui.cards.CardFavorites}
 * 的 toggle/add/remove）广播一次。卡查页与卡组管理页在收藏列表显示中收到本事件后，
 * 重新拉取收藏并按当前排序规则重排、重建分段，避免新加入的卡落在列表末尾、
 * 与同类型卡不在同一分段而单独形成新的“魔法卡/陷阱卡”等分段。
 */
public class CardFavoriteChangedEvent {
}

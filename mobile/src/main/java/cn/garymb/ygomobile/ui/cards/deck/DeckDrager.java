package cn.garymb.ygomobile.ui.cards.deck;

import android.util.Log;

import cn.garymb.ygomobile.Constants;
import ocgcore.data.Card;

class DeckDrager {
    private DeckAdapater deckAdapater;

    public DeckDrager(DeckAdapater deckAdapater) {
        this.deckAdapater = deckAdapater;
    }

    public void resotre(int id, DeckItem deckItem) {
        if (DeckItemUtils.isMain(id)) {
            resotreMain(id, deckItem);
        } else if (DeckItemUtils.isExtra(id)) {
            resotreExtra(id, deckItem);
        } else if (DeckItemUtils.isSide(id)) {
            resotreSide(id, deckItem);
        }
    }

    public DeckItem delete(int left) {
        if (Constants.DEBUG)
            Log.i("drag", "delete " + left);
        //处理数据
        if (DeckItemUtils.isMain(left)) {
            return removeMain(left);
        } else if (DeckItemUtils.isExtra(left)) {
            return removeExtra(left);
        } else if (DeckItemUtils.isSide(left)) {
            return removeSide(left);
        }
        return null;
    }

    private int mLast = -1;

    public void onDragStart() {
        mLast = -1;
    }

    public void onDragEnd() {
        mLast = -1;
    }

    public boolean move(DeckViewHolder viewHolder, DeckViewHolder target) {
        //处理view
        int left = viewHolder.getAdapterPosition();
        int right = target.getAdapterPosition();
        if (left < 0 || DeckItemUtils.isLabel(right)) {
            return false;
        }
        if (DeckItemUtils.isMain(left)) {
            if (DeckItemUtils.isMain(right)) {
                return moveMain(left, right);
            }
            if (DeckItemUtils.isSide(right)) {
                return moveMainToSide(left, right);
            }
        } else if (DeckItemUtils.isExtra(left)) {
            if (DeckItemUtils.isExtra(right)) {
                return moveExtra(left, right);
            }
            if (DeckItemUtils.isSide(right)) {
                return moveExtraToSide(left, right);
            }
        } else if (DeckItemUtils.isSide(left)) {
            if (DeckItemUtils.isSide(right)) {
                return moveSide(left, right);
            }
            if (DeckItemUtils.isMain(right)) {
                if (Card.isExtraCard(viewHolder.getCardType())) {
                    return false;
                }
                if (deckAdapater.getMainCount() >= Constants.DECK_MAIN_MAX) {
                    return false;
                }
                return moveSideToMain(left, right);
            }
            if (DeckItemUtils.isExtra(right)) {
                if (!Card.isExtraCard(viewHolder.getCardType())) {
                    return false;
                }
                if (deckAdapater.getExtraCount() >= Constants.DECK_EXTRA_MAX) {
                    return false;
                }
                return moveSideToExtra(left, right);
            }
        }
        return false;
    }

    public void resotreMain(int pos, DeckItem deckItem) {
        deckAdapater.removeItem(DeckItem.MainEnd);
        deckAdapater.addItem(pos, deckItem);
        deckAdapater.notifyItemRemoved(DeckItem.MainEnd);
        deckAdapater.notifyItemInserted(pos);
        deckAdapater.notifyItemChanged(DeckItem.MainLabel);
    }

    public void resotreExtra(int pos, DeckItem deckItem) {
        deckAdapater.removeItem(DeckItem.ExtraEnd);
        deckAdapater.addItem(pos, deckItem);
        deckAdapater.notifyItemRemoved(DeckItem.ExtraEnd);
        deckAdapater.notifyItemInserted(pos);
        deckAdapater.notifyItemChanged(DeckItem.ExtraLabel);
    }

    public void resotreSide(int pos, DeckItem deckItem) {
        deckAdapater.removeItem(DeckItem.SideEnd);
        deckAdapater.addItem(pos, deckItem);
        deckAdapater.notifyItemRemoved(DeckItem.SideEnd);
        deckAdapater.notifyItemInserted(pos);
        deckAdapater.notifyItemChanged(DeckItem.SideLabel);
    }

    public DeckItem removeMain(int pos) {
        int left = pos - DeckItem.MainStart;
        if (left >= 0 && left < deckAdapater.getMainCount()) {
            DeckItem deckItem = deckAdapater.removeItem(pos);
            deckAdapater.addItem(DeckItem.MainEnd, new DeckItem());
            deckAdapater.notifyItemRemoved(pos);
            deckAdapater.notifyItemInserted(DeckItem.MainEnd);
            deckAdapater.notifyItemChanged(DeckItem.MainLabel);
            return deckItem;
        }
        return null;
    }

    public DeckItem removeExtra(int pos) {
        int left = pos - DeckItem.ExtraStart;
        if (left >= 0 && left < deckAdapater.getExtraCount()) {
            DeckItem deckItem = deckAdapater.removeItem(pos);
            deckAdapater.addItem(DeckItem.ExtraEnd, new DeckItem());
            deckAdapater.notifyItemRemoved(pos);
            deckAdapater.notifyItemInserted(DeckItem.ExtraEnd);
            deckAdapater.notifyItemChanged(DeckItem.ExtraLabel);
            return deckItem;
        }
        return null;
    }

    public DeckItem removeSide(int pos) {
        int left = pos - DeckItem.SideStart;
        if (left >= 0 && left < deckAdapater.getSideCount()) {
            DeckItem deckItem = deckAdapater.removeItem(pos);
            deckAdapater.addItem(DeckItem.SideEnd, new DeckItem());
            deckAdapater.notifyItemRemoved(pos);
            deckAdapater.notifyItemInserted(DeckItem.SideEnd);
            deckAdapater.notifyItemChanged(DeckItem.SideLabel);
            return deckItem;
        }
        return null;
    }

    public boolean moveMain(int src, int to) {
        int left = src - DeckItem.MainStart;
        int right = to - DeckItem.MainStart;
        int count = deckAdapater.getMainCount();
        if (left >= count && right >= count) {
            return false;
        }
        if (left >= count) {
            left = count - 1;
        }
        if (right >= count) {
            right = count - 1;
        }
        DeckItem deckItem = deckAdapater.removeItem(DeckItem.MainStart + left);
        deckAdapater.addItem(DeckItem.MainStart + right, deckItem);
        deckAdapater.notifyItemMoved(DeckItem.MainStart + left, DeckItem.MainStart + right);
        return true;
    }

    public boolean moveSide(int src, int to) {
        int left = src - DeckItem.SideStart;
        int right = to - DeckItem.SideStart;
        int count = deckAdapater.getSideCount();
        if (left >= count && right >= count) {
            return false;
        }
        if (left >= count) {
            left = count - 1;
        }
        if (right >= count) {
            right = count - 1;
        }
        DeckItem deckItem = deckAdapater.removeItem(DeckItem.SideStart + left);
        deckAdapater.addItem(DeckItem.SideStart + right, deckItem);
        deckAdapater.notifyItemMoved(DeckItem.SideStart + left, DeckItem.SideStart + right);
        return true;
    }

    public boolean moveExtra(int src, int to) {
        int left = src - DeckItem.ExtraStart;
        int right = to - DeckItem.ExtraStart;
        int count = deckAdapater.getExtraCount();
        if (left >= count && right > count) {
            return false;
        }
        if (left >= count) {
            left = count - 1;
        }
        if (right >= count) {
            right = count - 1;
        }
        DeckItem deckItem = deckAdapater.removeItem(DeckItem.ExtraStart + left);
        deckAdapater.addItem(DeckItem.ExtraStart + right, deckItem);
        deckAdapater.notifyItemMoved(DeckItem.ExtraStart + left, DeckItem.ExtraStart + right);
        return true;
    }

    public boolean moveSideToExtra(int src, int to) {
        int left = src - DeckItem.SideStart;
        int right = to - DeckItem.ExtraStart;
        int maincount = deckAdapater.getExtraCount();
        if (right >= maincount) {
            right = maincount;
        }
        //index最大的在前面
        DeckItem deckItem = deckAdapater.removeItem(DeckItem.SideStart + left);
        DeckItem space = deckAdapater.removeItem(DeckItem.ExtraEnd);
        deckItem.setType(DeckItemType.ExtraCard);
        deckAdapater.addItem(DeckItem.ExtraStart + right, deckItem);
        deckAdapater.addItem(DeckItem.SideEnd, space);
        //空白向后移
        //move
        deckAdapater.notifyItemMoved(DeckItem.SideStart + left, DeckItem.ExtraStart + right);
        deckAdapater.notifyItemRemoved(DeckItem.ExtraEnd);
        if (deckAdapater.getMainCount() == Constants.DECK_MAIN_MAX) {
            deckAdapater.notifyItemChanged(DeckItem.ExtraEnd);
        }
        deckAdapater.notifyItemInserted(DeckItem.SideEnd);
        //label
        deckAdapater.notifyItemChanged(DeckItem.ExtraLabel);
        deckAdapater.notifyItemChanged(DeckItem.SideLabel);

        return true;
    }

    public boolean moveExtraToSide(int src, int to) {
        int left = src - DeckItem.ExtraStart;
        int right = to - DeckItem.SideStart;
        int count = deckAdapater.getSideCount();
        if (right >= count) {
            right = count - 1;
        }

        //交换
        DeckItem space = deckAdapater.removeItem(DeckItem.SideEnd);
        DeckItem deckItem = deckAdapater.removeItem(DeckItem.ExtraStart + left);
        deckItem.setType(DeckItemType.SideCard);
        deckAdapater.addItem(DeckItem.SideStart + right, deckItem);
        deckAdapater.addItem(DeckItem.ExtraEnd, space);
        //空白向后移
        //move
        deckAdapater.notifyItemMoved(DeckItem.ExtraStart + left, DeckItem.SideStart + right);
        deckAdapater.notifyItemRemoved(DeckItem.SideEnd);
        deckAdapater.notifyItemInserted(DeckItem.ExtraEnd);
        //label
        deckAdapater.notifyItemChanged(DeckItem.ExtraLabel);
        deckAdapater.notifyItemChanged(DeckItem.SideLabel);

        return true;
    }

    public boolean moveSideToMain(int src, int to) {
        int left = src - DeckItem.SideStart;
        int right = to - DeckItem.MainStart;
        int maincount = deckAdapater.getMainCount();
        int sidecount = deckAdapater.getSideCount();
        int extracount = deckAdapater.getExtraCount();
        if (maincount >= Constants.DECK_MAIN_MAX) {
            return false;
        }
        if (right > maincount) {
            right = maincount;
        }
        //index最大的在前面
        DeckItem deckItem = deckAdapater.removeItem(DeckItem.SideStart + left);
        deckItem.setType(DeckItemType.MainCard);
        DeckItem space = deckAdapater.removeItem(DeckItem.MainEnd);
        deckAdapater.addItem(DeckItem.MainStart + right, deckItem);
        deckAdapater.addItem(DeckItem.SideEnd, space);
        //空白向后移
        //move
        deckAdapater.notifyItemMoved(DeckItem.SideStart + left, DeckItem.MainStart + right);
        deckAdapater.notifyItemRemoved(DeckItem.MainEnd);
        if (deckAdapater.getMainCount() == Constants.DECK_MAIN_MAX) {
            deckAdapater.notifyItemChanged(DeckItem.MainEnd);
        }
        deckAdapater.notifyItemInserted(DeckItem.SideEnd);
        //label
        deckAdapater.notifyItemChanged(DeckItem.MainLabel);
        deckAdapater.notifyItemChanged(DeckItem.SideLabel);
        return true;
    }

    public boolean moveMainToSide(int src, int to) {
        int left = src - DeckItem.MainStart;
        int right = to - DeckItem.SideStart;
        int sidecount = deckAdapater.getSideCount();
        int maincount = deckAdapater.getMainCount();
        if (right > sidecount) {
            right = sidecount - 1;
        }
        //交换
        DeckItem space = deckAdapater.removeItem(DeckItem.SideEnd);
        DeckItem deckItem = deckAdapater.removeItem(DeckItem.MainStart + left);
        deckItem.setType(DeckItemType.SideCard);
        deckAdapater.addItem(DeckItem.SideStart + right, deckItem);
        deckAdapater.addItem(DeckItem.MainEnd, space);
        //空白向后移
        //move
        deckAdapater.notifyItemMoved(DeckItem.MainStart + left, DeckItem.SideStart + right);
        deckAdapater.notifyItemRemoved(DeckItem.SideEnd);
        deckAdapater.notifyItemInserted(DeckItem.MainEnd);
        //label
        deckAdapater.notifyItemChanged(DeckItem.MainLabel);
        deckAdapater.notifyItemChanged(DeckItem.SideLabel);
        return true;
    }
}

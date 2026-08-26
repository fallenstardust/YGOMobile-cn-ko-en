package cn.garymb.ygomobile.utils;

import java.util.List;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;

public class YGODeckDialogUtil {

    //注册listener，发生点击卡组事件后，通知外部的activity进行对应的显示更新
    public interface OnDeckMenuListener {
        void onDeckSelect(DeckFile deckFile);

        void onDeckDel(List<DeckFile> deckFileList);

        void onDeckMove(List<DeckFile> deckFileList, DeckType toDeckType);

        void onDeckCopy(List<DeckFile> deckFileList, DeckType toDeckType);

        void onDeckNew(DeckType currentDeckType);

    }

    public interface OnDeckDialogListener {

        void onDismiss();

        void onShow();
    }

    public interface OnDeckTypeListener {
        void onDeckTypeListener(int position);
    }
}

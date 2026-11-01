package cn.garymb.ygomobile.utils;

import java.util.List;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;

public class YGODeckDialogUtil {

    //注册listener，发生点击卡组事件后，通知外部的activity进行对应的显示更新
    public interface OnDeckMenuListener {
        void onDeckSelect(DeckFile deckFile);

        /**
         * 选中某个卡组，并把“收起卡组选择对话框”的时机交给接收方。
         * <p>
         * 接收方若要先弹“此操作将放弃当前卡组的修改，是否继续？”，就应保持对话框展开，
         * 到用户点确定或取消时才 run()（默认实现保持旧行为：处理完立即收起）。
         */
        default void onDeckSelect(DeckFile deckFile, Runnable dismissDialog) {
            onDeckSelect(deckFile);
            dismissDialog.run();
        }

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

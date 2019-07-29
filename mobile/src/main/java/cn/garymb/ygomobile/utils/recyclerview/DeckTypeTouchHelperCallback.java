package cn.garymb.ygomobile.utils.recyclerview;


import android.content.DialogInterface;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.TextSelectAdapter;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.YGODialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public class DeckTypeTouchHelperCallback extends ItemTouchHelper.Callback {

    private int dragFlags;
    private int swipeFlags;
    private RecyclerView recyclerView;
    private YGODialogUtil.OnDeckTypeListener onDeckTypeListener;

    public DeckTypeTouchHelperCallback(YGODialogUtil.OnDeckTypeListener onDeckTypeListener) {
        this.onDeckTypeListener = onDeckTypeListener;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder p2) {
        dragFlags = 0;
        swipeFlags = 0;
        this.recyclerView = recyclerView;

        if (p2.getAdapterPosition() > 2)
            swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(RecyclerView p1, RecyclerView.ViewHolder p2, RecyclerView.ViewHolder p3) {

        return false;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder vh, int dire) {
        int positon = vh.getAdapterPosition();
        TextSelectAdapter textSelectAdapter = ((TextSelectAdapter) recyclerView.getAdapter());

        DialogPlus dialogPlus = new DialogPlus(recyclerView.getContext());
        dialogPlus.setMessage(R.string.delete_confirm);
        dialogPlus.setLeftButtonText(R.string.delete);
        dialogPlus.setRightButtonText(R.string.Cancel);
        dialogPlus.setLeftButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialogPlus.dismiss();

                onDeckTypeListener.onDeckTypeListener(positon);
            }
        });
        dialogPlus.setRightButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                textSelectAdapter.notifyItemChanged(positon);
                dialog.dismiss();
            }
        });
        dialogPlus.show();
    }


}

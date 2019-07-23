package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feihua.dialogutils.util.DialogUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.TextSelectAdapter;

public class YGODialogUtil {

    public static void dialogDeckSelect(Context context, String selectDeckPath) {
        View viewDialog = DialogUtils.getdx(context).dialogBottomSheet(R.layout.dialog_deck_select);
        RecyclerView rv_type, rv_deck;
        rv_deck = viewDialog.findViewById(R.id.rv_deck);
        rv_type = viewDialog.findViewById(R.id.rv_type);

        rv_deck.setLayoutManager(new LinearLayoutManager(context));
        rv_type.setLayoutManager(new LinearLayoutManager(context));
        TextSelectAdapter typeAdp, deckAdp;
        List<DeckType> typeList = DeckUtil.getDeckTypeList();

        int typeSelectPosition=2;
        int deckSelectPosition=-1;
        List<DeckFile> deckList ;
        if (selectDeckPath != null) {
            String name = new File(selectDeckPath).getParentFile().getName();
            if (name.equals("pack") || name.equals("cacheDeck")) {
                //卡包
                typeSelectPosition=0;
            } else if (name.equals("Decks")) {
                //ai卡组
                typeSelectPosition=1;
            } else if (name.equals("deck") && new File(selectDeckPath).getParentFile().getParentFile().getName().equals(Constants.PREF_DEF_GAME_DIR)) {
                //如果是deck并且上一个目录是ygocore的话，保证不会把名字为deck的卡包识别为未分类
                typeSelectPosition=2;
            } else {
                //其他卡包
                for (int i=3;i<typeList.size();i++){
                    DeckType deckType=typeList.get(i);
                    if(deckType.getName().equals(name)){
                        typeSelectPosition=i;
                        break;
                    }
                }
            }
        }
        deckList = DeckUtil.getDeckList(typeList.get(typeSelectPosition).getPath());
        typeAdp = new TextSelectAdapter(typeList, typeSelectPosition);
        deckAdp = new TextSelectAdapter(deckList, deckSelectPosition);
        rv_type.setAdapter(typeAdp);
        rv_deck.setAdapter(deckAdp);
        typeAdp.setOnItemSelectListener(new TextSelectAdapter.OnItemSelectListener<DeckType>() {
            @Override
            public void onItemSelect(int position, DeckType item) {
                deckList.clear();
                deckList.addAll(DeckUtil.getDeckList(item.getPath()));
                if (position == 0) {
                    if (AppsSettings.get().isReadExpansions()) {
                        try {
                            deckList.addAll(DeckUtil.getExpansionsDeckList());
                        } catch (IOException e) {
                            YGOUtil.show("额外卡库加载失败,愿意为" + e);
                        }
                    }
                }
                deckAdp.notifyDataSetChanged();
            }
        });
        deckAdp.setOnItemSelectListener(new TextSelectAdapter.OnItemSelectListener<DeckFile>() {
            @Override
            public void onItemSelect(int position, DeckFile item) {
                DialogUtils.getdx(context).dis();
            }
        });
//        rv_deck.setAdapter();


    }


}

package cn.garymb.ygomobile.ui.cards.deck_square;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.ui.cards.DeckManagerFragment;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public class MyDeckListAdapter extends BaseMultiItemQuickAdapter<DeckListItem, BaseViewHolder> {
    private static final String TAG = MyDeckListAdapter.class.getSimpleName();
    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;
    private ImageLoader imageLoader;
    private String currentKeyword = "";
    private final LinkedHashMap<String, Boolean> sectionExpandedMap = new LinkedHashMap<>();

    public MyDeckListAdapter(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener mDialogListener) {
        super(null);
        addItemType(DeckListItem.TYPE_SECTION_HEADER, R.layout.item_deck_type_header);
        addItemType(DeckListItem.TYPE_DECK_ITEM, R.layout.item_my_deck);
        imageLoader = new ImageLoader();
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = mDialogListener;
    }

    public void loadData() {
        LoginToken loginToken = DeckSquareApiUtil.getLoginData();
        if (loginToken == null) {
            return;
        }
        if (DeckManagerFragment.getOriginalData().isEmpty()) {
            final DialogPlus dialog_read_ex = DialogPlus.show(getContext(), null, getContext().getString(R.string.fetch_online_deck));
            VUiKit.defer().when(() -> {
                MyDeckResponse result = DeckSquareApiUtil.getUserDecks(loginToken);
                if (result == null) return null;
                else return result.getData();
            }).fail((e) -> {
                Log.e(TAG, e + "");
                if (dialog_read_ex.isShowing()) {
                    try {
                        dialog_read_ex.dismiss();
                    } catch (Exception ex) {
                    }
                }
                LogUtil.i(TAG, "load mycard from server failed:" + e);
            }).done((serverDecks) -> {
                if (serverDecks != null) {
                    DeckManagerFragment.getOriginalData().clear();
                    DeckManagerFragment.getOriginalData().addAll(serverDecks);
                }
                LogUtil.i(TAG, "load mycard from server done");
                rebuildGroupedList(DeckManagerFragment.getOriginalData());
                if (dialog_read_ex.isShowing()) {
                    try {
                        dialog_read_ex.dismiss();
                    } catch (Exception ex) {
                    }
                }
            });
        } else {
            LogUtil.i(TAG, "load originalData done");
            rebuildGroupedList(DeckManagerFragment.getOriginalData());
        }
    }

    public void filter(String keyword) {
        currentKeyword = keyword;
        List<MyOnlineDeckDetail> filteredList = new ArrayList<>();
        if (keyword.isEmpty()) {
            filteredList.addAll(DeckManagerFragment.getOriginalData());
        } else {
            String lowerKeyword = keyword.toLowerCase();
            for (MyOnlineDeckDetail item : DeckManagerFragment.getOriginalData()) {
                if (item.getDeckName().toLowerCase().contains(lowerKeyword) ||
                        item.getDeckType().toLowerCase().contains(lowerKeyword)) {
                    filteredList.add(item);
                }
            }
        }
        rebuildGroupedList(filteredList);
    }

    public void rebuildGroupedList(List<MyOnlineDeckDetail> deckList) {
        LinkedHashMap<String, List<MyOnlineDeckDetail>> groupedMap = new LinkedHashMap<>();
        if (deckList != null) {
            for (MyOnlineDeckDetail deck : deckList) {
                String type = deck.getDeckType();
                if (type == null) type = "";
                if (!groupedMap.containsKey(type)) {
                    groupedMap.put(type, new ArrayList<>());
                }
                groupedMap.get(type).add(deck);
            }
        }

        for (String key : groupedMap.keySet()) {
            if (!sectionExpandedMap.containsKey(key)) {
                sectionExpandedMap.put(key, true);
            }
        }

        List<DeckListItem> flatList = new ArrayList<>();
        for (Map.Entry<String, List<MyOnlineDeckDetail>> entry : groupedMap.entrySet()) {
            String typeName = entry.getKey();
            List<MyOnlineDeckDetail> decks = entry.getValue();
            boolean isExpanded = sectionExpandedMap.getOrDefault(typeName, true);

            DeckListItem header = new DeckListItem(typeName, decks.size(), isExpanded);
            flatList.add(header);

            if (isExpanded) {
                for (MyOnlineDeckDetail deck : decks) {
                    flatList.add(new DeckListItem(deck));
                }
            }
        }

        submitList(flatList);
    }

    private void submitList(List<DeckListItem> newList) {
        getData().clear();
        addData(newList);
        notifyDataSetChanged();
    }

    public void toggleSection(int headerPosition) {
        DeckListItem header = getData().get(headerPosition);
        if (header.getItemType() != DeckListItem.TYPE_SECTION_HEADER) return;

        boolean willExpand = !header.isExpanded();
        header.setExpanded(willExpand);
        sectionExpandedMap.put(header.getSectionName(), willExpand);

        if (willExpand) {
            List<MyOnlineDeckDetail> decksInSection = getDecksByType(header.getSectionName());
            int insertPos = headerPosition + 1;
            List<DeckListItem> itemsToInsert = new ArrayList<>();
            for (MyOnlineDeckDetail deck : decksInSection) {
                itemsToInsert.add(new DeckListItem(deck));
            }
            addData(insertPos, itemsToInsert);
        } else {
            int removeCount = header.getSectionItemCount();
            for (int i = 0; i < removeCount; i++) {
                remove(headerPosition + 1);
            }
        }
        notifyItemChanged(headerPosition);
    }

    private List<MyOnlineDeckDetail> getDecksByType(String typeName) {
        List<MyOnlineDeckDetail> result = new ArrayList<>();
        String keyword = currentKeyword;
        List<MyOnlineDeckDetail> source;
        if (keyword != null && !keyword.isEmpty()) {
            source = new ArrayList<>();
            String lowerKeyword = keyword.toLowerCase();
            for (MyOnlineDeckDetail item : DeckManagerFragment.getOriginalData()) {
                if (item.getDeckName().toLowerCase().contains(lowerKeyword) ||
                        item.getDeckType().toLowerCase().contains(lowerKeyword)) {
                    ((List<MyOnlineDeckDetail>) source).add(item);
                }
            }
        } else {
            source = DeckManagerFragment.getOriginalData();
        }
        for (MyOnlineDeckDetail deck : source) {
            String type = deck.getDeckType();
            if (type == null) type = "";
            if (type.equals(typeName)) {
                result.add(deck);
            }
        }
        return result;
    }

    private SpannableString getHighlightedText(String text, String keyword) {
        if (text == null || keyword.isEmpty()) {
            return new SpannableString(text == null ? "" : text);
        }
        SpannableString spannable = new SpannableString(text);
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int index = lowerText.indexOf(lowerKeyword);
        while (index >= 0) {
            int start = index;
            int end = index + keyword.length();
            spannable.setSpan(new ForegroundColorSpan(YGOUtil.c(R.color.holo_blue_bright)),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = lowerText.indexOf(lowerKeyword, end);
        }
        return spannable;
    }

    private void deleteMyDeckOnLine(MyOnlineDeckDetail item) {
        if (item != null) {
            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;
            }
            List<DeckFile> deleteList = new ArrayList<>();
            DeckFile deleteDeckFile = new DeckFile(item.getDeckId(), DeckType.ServerType.MY_SQUARE);
            deleteDeckFile.setTypeName(item.getDeckType());
            deleteDeckFile.setName(item.getDeckName());
            deleteList.add(deleteDeckFile);
            try {
                DeckSquareApiUtil.deleteDecks(deleteList);
            } catch (Throwable e) {
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }
            item.setDelete(true);
            rebuildGroupedList(DeckManagerFragment.getOriginalData());
        }
    }

    private void changeDeckPublicState(MyOnlineDeckDetail item) {
        if (item != null) {
            LoginToken loginToken = DeckSquareApiUtil.getLoginData();
            if (loginToken == null) {
                return;
            }
            VUiKit.defer().when(() -> {
                BasicResponse result = DeckSquareApiUtil.setDeckPublic(item.getDeckId(), loginToken, item.isPublic());
                return result;
            }).fail(e -> {
                LogUtil.i(TAG, "切换显示失败" + e.getMessage());
            }).done(data -> {
                LogUtil.i(TAG, "切换显示成功" + data.getMessage());
            });
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, DeckListItem item) {
        switch (item.getItemType()) {
            case DeckListItem.TYPE_SECTION_HEADER:
                convertHeader(helper, item);
                break;
            case DeckListItem.TYPE_DECK_ITEM:
                convertDeckItem(helper, item);
                break;
        }
    }

    private void convertHeader(BaseViewHolder helper, DeckListItem item) {
        String displayName = item.getSectionName().isEmpty()
                ? getContext().getString(R.string.category_Uncategorized)
                : item.getSectionName();
        helper.setText(R.id.tv_section_name, getHighlightedText(displayName, currentKeyword));
        helper.setText(R.id.tv_section_count, "(" + item.getSectionItemCount() + ")");
        ImageView arrow = helper.findView(R.id.iv_arrow);
        if (arrow != null) {
            arrow.setImageResource(item.isExpanded() ? R.drawable.baseline_keyboard_arrow_down_24 : R.drawable.baseline_keyboard_arrow_up_24);
        }
        helper.itemView.setOnClickListener(v -> {
            int pos = helper.getBindingAdapterPosition();
            if (pos >= 0) {
                toggleSection(pos);
            }
        });
    }

    private void convertDeckItem(BaseViewHolder helper, DeckListItem item) {
        MyOnlineDeckDetail deckItem = item.getDeckDetail();
        ImageView iv_box = helper.findView(R.id.iv_box);
        ImageView deck_info_image = helper.findView(R.id.deck_info_image);
        ImageView delete_my_online_deck_btn = helper.findView(R.id.delete_my_online_deck_btn);
        LinearLayout ll_switch_show = helper.findView(R.id.ll_switch_show);
        TextView change_show_or_hide = helper.findView(R.id.change_show_or_hide);
        ImageView show_on_deck_square = helper.findView(R.id.show_on_deck_square);
        long code = deckItem.getDeckCoverCard1();

        if (deckItem.isDelete()) {
            iv_box.setColorFilter(YGOUtil.c(R.color.bottom_bg));
            delete_my_online_deck_btn.setVisibility(View.GONE);
            ll_switch_show.setVisibility(View.GONE);
        } else {
            deck_info_image.clearColorFilter();
            iv_box.clearColorFilter();
            delete_my_online_deck_btn.setVisibility(View.VISIBLE);
            ll_switch_show.setVisibility(View.VISIBLE);
        }

        helper.setText(R.id.my_online_deck_type, getHighlightedText(
                deckItem.getDeckType().equals("") ? "" : "-" + deckItem.getDeckType() + "-", currentKeyword));
        helper.setText(R.id.my_deck_name, getHighlightedText(deckItem.getDeckName(), currentKeyword));
        helper.setText(R.id.deck_update_date, DeckUtil.convertToGMTDate(deckItem.getDeckUpdateDate()));

        if (deckItem.isPublic()) {
            change_show_or_hide.setText(R.string.in_public);
            show_on_deck_square.setBackgroundResource(R.drawable.baseline_remove_red_eye_24);
            ll_switch_show.setBackgroundResource(R.drawable.button_radius_red);
        } else {
            change_show_or_hide.setText(R.string.in_personal_use);
            show_on_deck_square.setBackgroundResource(R.drawable.closed_eyes_24);
            ll_switch_show.setBackgroundResource(R.drawable.button_radius_n);
        }
        if (code != 0) {
            imageLoader.bindImage(deck_info_image, code, null, ImageLoader.Type.small);
        } else {
            imageLoader.bindImage(deck_info_image, -1, null, ImageLoader.Type.small);
        }
        delete_my_online_deck_btn.setOnClickListener(view -> deleteMyDeckOnLine(deckItem));
        helper.getView(R.id.ll_switch_show).setOnClickListener(view -> {
            if (deckItem.isPublic()) {
                change_show_or_hide.setText(R.string.in_personal_use);
                show_on_deck_square.setBackgroundResource(R.drawable.closed_eyes_24);
                ll_switch_show.setBackgroundResource(R.drawable.button_radius_n);
                deckItem.setPublic(false);
            } else {
                change_show_or_hide.setText(R.string.in_public);
                show_on_deck_square.setBackgroundResource(R.drawable.baseline_remove_red_eye_24);
                ll_switch_show.setBackgroundResource(R.drawable.button_radius_red);
                deckItem.setPublic(true);
            }
            LogUtil.i(TAG, "current " + deckItem.toString());
            changeDeckPublicState(deckItem);
        });
        helper.getView(R.id.ll_download).setOnLongClickListener(view -> {
            DeckFile deckFile = new DeckFile(deckItem.getDeckId(), DeckType.ServerType.MY_SQUARE);
            mDialogListener.onDismiss();
            onDeckMenuListener.onDeckSelect(deckFile);
            return true;
        });
    }
}
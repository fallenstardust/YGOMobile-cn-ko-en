package cn.garymb.ygomobile.utils;

import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemLongClickListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.DeckListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.adapters.TextSelectAdapter;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.recyclerview.DeckTypeTouchHelperCallback;

public class YGODialogUtil {
    private static final String TAG = "YGODialogUtil";

    public static void dialogDeckSelect(Context context, String selectDeckPath, OnDeckMenuListener onDeckMenuListener) {
        ViewHolder viewHolder = new ViewHolder(context, selectDeckPath, onDeckMenuListener);
        viewHolder.show();
    }


    public interface OnDeckMenuListener {
        void onDeckSelect(DeckFile deckFile);

        void onDeckDel(List<DeckFile> deckFileList);

        void onDeckMove(List<DeckFile> deckFileList, DeckType toDeckType);

        void onDeckCopy(List<DeckFile> deckFileList, DeckType toDeckType);

        void onDeckNew(DeckType currentDeckType);

    }

    public interface OnDeckTypeListener {
        void onDeckTypeListener(int position);
    }

    private static class ViewHolder {

        private final int IMAGE_MOVE = 0;
        private final int IMAGE_COPY = 1;
        private final int IMAGE_DEL = 2;
        private final EditText et_input_deck_name;
        private final ImageView iv_search_deck_name;
        private final LinearLayout ll_main_ui;
        private final LinearLayout ll_move;
        private final LinearLayout ll_copy;
        private final LinearLayout ll_del;
        private final LinearLayout ll_add;
        private final ImageView iv_move;
        private final ImageView iv_copy;
        private final ImageView iv_del;
        private final TextView tv_move;
        private final TextView tv_copy;
        private final TextView tv_del;
        private final TextSelectAdapter<DeckType> typeAdp;
        private final DeckListAdapter<DeckFile> deckAdp;
        private final DeckListAdapter<DeckFile> resultListAdapter;
        private final List<DeckFile> allDeckList;
        private final RecyclerView rv_type, rv_deck, rv_result_list;

        private final List<DeckFile> resultList;
        private final DialogPlus ygoDialog;

        public ViewHolder(Context context, String selectDeckPath, OnDeckMenuListener onDeckMenuListener) {
            ygoDialog = new DialogPlus(context);
            ygoDialog.setContentView(R.layout.dialog_deck_select);
            ygoDialog.setTitle(R.string.category_manager);

            allDeckList = new ArrayList<>();
            resultList = new ArrayList<>();
            resultListAdapter = new DeckListAdapter<DeckFile>(context, resultList, -1);
            et_input_deck_name = ygoDialog.findViewById(R.id.input_deck_name);
            iv_search_deck_name = ygoDialog.findViewById(R.id.iv_search_deck_name);

            ll_main_ui = ygoDialog.findViewById(R.id.ll_main_ui);
            rv_deck = ygoDialog.findViewById(R.id.rv_deck);
            rv_type = ygoDialog.findViewById(R.id.rv_type);
            rv_result_list = ygoDialog.findViewById(R.id.rv_result_list);
            ll_move = ygoDialog.findViewById(R.id.ll_move);
            ll_copy = ygoDialog.findViewById(R.id.ll_copy);
            ll_del = ygoDialog.findViewById(R.id.ll_del);
            ll_add = ygoDialog.findViewById(R.id.ll_add);
            iv_copy = ygoDialog.findViewById(R.id.iv_copy);
            iv_move = ygoDialog.findViewById(R.id.iv_move);
            iv_del = ygoDialog.findViewById(R.id.iv_del);
            tv_move = ygoDialog.findViewById(R.id.tv_move);
            tv_copy = ygoDialog.findViewById(R.id.tv_copy);
            tv_del = ygoDialog.findViewById(R.id.tv_del);

            hideAllDeckUtil();
            rv_deck.setLayoutManager(new FastScrollLinearLayoutManager(context));
            rv_type.setLayoutManager(new FastScrollLinearLayoutManager(context));
            rv_result_list.setLayoutManager(new FastScrollLinearLayoutManager(context));

            List<DeckType> typeList = DeckUtil.getDeckTypeList(context);

            int typeSelectPosition = 2;
            int deckSelectPosition = -1;
            List<DeckFile> deckList;
            if (!TextUtils.isEmpty(selectDeckPath)) {
                File file = new File(selectDeckPath);
                if (file.exists()) {
                    String cateName = file.getParentFile().getName();
                    String parentName = file.getParentFile().getParentFile().getName();
                    if (cateName.equals("pack") || cateName.equals("cacheDeck")) {
                        //卡包
                        typeSelectPosition = 0;
                    } else if (cateName.equals("Decks") && parentName.equals(Constants.WINDBOT_PATH)) {
                        //ai卡组
                        typeSelectPosition = 1;
                    } else if (cateName.equals("deck") && parentName.equals(Constants.PREF_DEF_GAME_DIR)) {
                        //如果是deck并且上一个目录是ygocore的话，保证不会把名字为deck的卡包识别为未分类
                    } else {
                        //其他卡包
                        for (int i = 3; i < typeList.size(); i++) {
                            DeckType deckType = typeList.get(i);
                            if (deckType.getName().equals(cateName)) {
                                typeSelectPosition = i;
                                break;
                            }
                        }
                    }
                }
            }
            deckList = DeckUtil.getDeckList(typeList.get(typeSelectPosition).getPath());
            for (int i = 0; i < typeList.size(); i++) {
                allDeckList.addAll(DeckUtil.getDeckList(typeList.get(i).getPath()));//把所有分类里的卡组全部纳入，用于关键词查询目标
            }
            if (typeSelectPosition == 0) {
                if (AppsSettings.get().isReadExpansions()) {
                    try {
                        deckList.addAll(0, DeckUtil.getExpansionsDeckList());//置顶ypk缓存的cacheDeck下的先行卡ydk
                    } catch (IOException e) {
                        YGOUtil.showTextToast("额外卡库加载失败,原因为" + e);
                    }
                }
            }
            typeAdp = new TextSelectAdapter<>(typeList, typeSelectPosition);
            deckAdp = new DeckListAdapter<>(context, deckList, deckSelectPosition);
            rv_type.setAdapter(typeAdp);
            rv_deck.setAdapter(deckAdp);
            typeAdp.setOnItemSelectListener(new TextSelectAdapter.OnItemSelectListener<DeckType>() {
                @Override
                public void onItemSelect(int position, DeckType item) {
                    clearDeckSelect();
                    deckList.clear();
                    deckList.addAll(DeckUtil.getDeckList(item.getPath()));
                    if (position == 0) {
                        if (AppsSettings.get().isReadExpansions()) {
                            try {
                                if (!DeckUtil.getExpansionsDeckList().isEmpty()) {
                                    deckList.addAll(0, DeckUtil.getExpansionsDeckList());
                                }
                            } catch (IOException e) {
                                YGOUtil.showTextToast("额外卡库加载失败,原因为" + e);
                            }
                        }
                    }
                    deckAdp.notifyDataSetChanged();
                }
            });
            deckAdp.setOnItemSelectListener(new DeckListAdapter.OnItemSelectListener<DeckFile>() {
                @Override
                public void onItemSelect(int position, DeckFile item) {
                    if (deckAdp.isManySelect()) {
                        deckAdp.addManySelect(item);
                        if (deckAdp.getSelectList().size() == 0) {
                            clearDeckSelect();
                        }
                        deckAdp.notifyItemChanged(position);
                    } else {
                        dismiss();
                        onDeckMenuListener.onDeckSelect(item);
                    }
                }
            });
            deckAdp.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                    if (deckAdp.isSelect() || typeAdp.getSelectPosition() == 0)
                        return true;

                    deckAdp.setManySelect(true);
                    if (typeAdp.getSelectPosition() == 1) {
                        showCopyDeckUtil();
                    } else {
                        showAllDeckUtil();
                    }
                    deckAdp.addManySelect((DeckFile) adapter.getItem(position));
                    if (deckAdp.getSelectList().size() == 0) {
                        clearDeckSelect();
                    }
                    deckAdp.notifyItemChanged(position);
                    return true;
                }
            });

            rv_result_list.setAdapter(resultListAdapter);
            resultListAdapter.setOnItemSelectListener(new DeckListAdapter.OnItemSelectListener<DeckFile>() {
                @Override
                public void onItemSelect(int position, DeckFile item) {
                    rv_result_list.setVisibility(View.GONE);
                    ll_main_ui.setVisibility(View.VISIBLE);
                    et_input_deck_name.getEditableText().clear();
                    dismiss();
                    onDeckMenuListener.onDeckSelect(item);
                }
            });
            iv_search_deck_name.setOnClickListener(v -> {
                searchDeck();
            });

            et_input_deck_name.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    searchDeck();
                    return true;
                }
                return false;
            });

            et_input_deck_name.addTextChangedListener(new TextWatcher() {

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // 输入中监听
                    if (s.toString().isEmpty()) {
                        ll_main_ui.setVisibility(View.VISIBLE);
                        rv_result_list.setVisibility(View.GONE);
                        iv_search_deck_name.setVisibility(View.GONE);
                    } else {
                        iv_search_deck_name.setVisibility(View.VISIBLE);
                    }

                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    //输入前监听
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // 输入后的监听

                }
            });

            ll_add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    List list = new ArrayList();
                    list.add(context.getString(R.string.category_name));
                    list.add(context.getString(R.string.deck_name));
                    SimpleListAdapter catelistadapter = new SimpleListAdapter(context);
                    catelistadapter.set(list);
                    DialogPlus dialog = new DialogPlus(context);
                    dialog.setTitle(R.string.new_deck);
                    dialog.setContentView(R.layout.dialog_edit_and_list);
                    EditText edit = dialog.bind(R.id.room_name);
                    edit.setVisibility(View.GONE);//不显示输入框
                    ListView listView = dialog.bind(R.id.room_list);
                    listView.setAdapter(catelistadapter);
                    listView.setOnItemClickListener((a, v, pos, index) -> {
                        switch ((int) index) {
                            case 0:
                                dialog.dismiss();
                                //if (deckList.size()>=8){
                                //    YGOUtil.show("最多只能有5个自定义分类");
                                //}
                                DialogPlus builder = new DialogPlus(context);
                                builder.setTitle(R.string.please_input_category_name);
                                EditText editText = new EditText(context);
                                editText.setGravity(Gravity.TOP | Gravity.LEFT);
                                editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                                editText.setSingleLine();
                                builder.setContentView(editText);
                                builder.setOnCloseLinster(DialogInterface::dismiss);
                                builder.setLeftButtonListener((dlg, s) -> {
                                    CharSequence catename = editText.getText();
                                    if (TextUtils.isEmpty(catename)) {
                                        YGOUtil.showTextToast(context.getString(R.string.invalid_category_name));
                                        return;
                                    }
                                    File file = new File(AppsSettings.get().getDeckDir(), catename.toString());
                                    if (IOUtils.createFolder(file)) {
                                        typeList.add(new DeckType(catename.toString(), file.getAbsolutePath()));
                                        typeAdp.notifyItemInserted(typeList.size() - 1);
                                        dlg.dismiss();
                                    } else {
                                        YGOUtil.showTextToast(context.getString(R.string.create_new_failed));
                                    }
                                });
                                builder.show();
                                break;
                            case 1:
                                dialog.dismiss();
                                onDeckMenuListener.onDeckNew(typeList.get(typeAdp.getSelectPosition()));
                                break;
                        }
                    });
                    dialog.show();
                }
            });

            ll_move.setOnClickListener(view -> {
                List<DeckType> otherType = getOtherTypeList();
                List<String> cateNameList = getStringTypeList(otherType);
                SimpleListAdapter simpleListAdapter = new SimpleListAdapter(context);
                simpleListAdapter.set(cateNameList);

                DialogPlus dialog = new DialogPlus(context);
                dialog.setTitle(R.string.please_select_target_category);
                dialog.setContentView(R.layout.dialog_edit_and_list);
                EditText edit = dialog.bind(R.id.room_name);
                edit.setVisibility(View.GONE);//不显示输入框
                ListView listView = dialog.bind(R.id.room_list);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    listView.setForegroundGravity(View.TEXT_ALIGNMENT_CENTER);
                }
                listView.setAdapter(simpleListAdapter);
                listView.setOnItemClickListener((a, v, pos, index) -> {
                    String name = simpleListAdapter.getItemById(index);
                    int position = simpleListAdapter.findItem(name);
                    DeckType toType = otherType.get(position);
                    IOUtils.createFolder(new File(toType.getPath()));
                    List<DeckFile> deckFileList = deckAdp.getSelectList();
                    for (DeckFile deckFile : deckFileList) {
                        try {
                            FileUtils.moveFile(deckFile.getPath(), new File(toType.getPath(), deckFile.getFileName()).getPath());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        deckList.remove(deckFile);
                    }
                    YGOUtil.showTextToast(context.getString(R.string.done));
                    onDeckMenuListener.onDeckMove(deckAdp.getSelectList(), toType);
                    clearDeckSelect();
                    dialog.dismiss();
                });
                dialog.show();
            });

            ll_copy.setOnClickListener(view -> {
                List<DeckType> otherType = getOtherTypeList();
                List<String> cateNameList = getStringTypeList(otherType);
                SimpleListAdapter simpleListAdapter = new SimpleListAdapter(context);
                simpleListAdapter.set(cateNameList);

                DialogPlus dialog = new DialogPlus(context);
                dialog.setTitle(R.string.please_select_target_category);
                dialog.setContentView(R.layout.dialog_edit_and_list);
                EditText edit = dialog.bind(R.id.room_name);
                edit.setVisibility(View.GONE);//不显示输入框
                ListView listView = dialog.bind(R.id.room_list);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    listView.setForegroundGravity(View.TEXT_ALIGNMENT_CENTER);
                }
                listView.setAdapter(simpleListAdapter);
                listView.setOnItemClickListener((a, v, pos, index) -> {
                    DeckType toType = otherType.get(pos);
                    IOUtils.createFolder(new File(toType.getPath()));
                    List<DeckFile> deckFileList = deckAdp.getSelectList();
                    for (DeckFile deckFile : deckFileList) {
                        try {
                            FileUtils.copyFile(deckFile.getPath(), new File(toType.getPath(), deckFile.getFileName()).getPath());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    YGOUtil.showTextToast(context.getString(R.string.done));
                    onDeckMenuListener.onDeckCopy(deckAdp.getSelectList(), toType);
                    clearDeckSelect();
                    dialog.dismiss();
                });
                dialog.show();
            });

            ll_del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (deckAdp.getSelectList().size() == 0) {
                        YGOUtil.showTextToast(context.getString(R.string.no_deck_is_selected));
                        return;
                    }
                    DialogPlus dialogPlus = new DialogPlus(context);
                    dialogPlus.setMessage(R.string.question_delete_deck);
                    dialogPlus.setLeftButtonText(YGOUtil.s(R.string.delete));
                    dialogPlus.setRightButtonText(R.string.Cancel);
                    dialogPlus.setLeftButtonListener(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            List<DeckFile> selectDeckList = deckAdp.getSelectList();
                            for (DeckFile deckFile : selectDeckList) {
                                deckFile.getPathFile().delete();
                                deckList.remove(deckFile);
                            }
                            YGOUtil.showTextToast(context.getString(R.string.done));
                            dialogPlus.dismiss();
                            onDeckMenuListener.onDeckDel(selectDeckList);
                            clearDeckSelect();
                        }
                    });
                    dialogPlus.setRightButtonListener(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialogPlus.dismiss();
                        }
                    });
                    dialogPlus.show();
                }
            });
            ygoDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    clearDeckSelect();
                }
            });
            ygoDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        if (deckAdp.isManySelect()) {
                            clearDeckSelect();
                            return true;
                        }

                    }
                    return false;
                }
            });
            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new DeckTypeTouchHelperCallback(new OnDeckTypeListener() {
                @Override
                public void onDeckTypeListener(int positon) {
                    File file = new File(typeList.get(positon).getPath());
                    File[] files = file.listFiles();
                    List<DeckFile> deckFileList = new ArrayList<>();
                    if (files != null) {
                        for (File file1 : files) {
                            deckFileList.add(new DeckFile(file1));
                        }
                    }
                    IOUtils.delete(file);
                    YGOUtil.showTextToast(context.getString(R.string.done));
                    onDeckMenuListener.onDeckDel(deckFileList);
                    typeAdp.remove(positon);
                    if (typeAdp.getSelectPosition() == positon) {
                        typeAdp.setSelectPosition(2);
                        typeAdp.notifyItemChanged(2);
                    }
                    clearDeckSelect();
                    deckList.clear();
                    deckList.addAll(DeckUtil.getDeckList(typeList.get(2).getPath()));
                    deckAdp.notifyDataSetChanged();
                }
            }));
            itemTouchHelper.attachToRecyclerView(rv_type);
            if (!TextUtils.isEmpty(selectDeckPath)) {
                for (int i = 0; i < deckList.size(); i++) {
                    if (selectDeckPath.endsWith(deckList.get(i).getName() + YDK_FILE_EX)) {
                        rv_deck.scrollToPosition(i);
                        break;
                    }
                }
            }
        }

        private void searchDeck() {
            resultList.clear();
            et_input_deck_name.clearFocus();
            String keyword = et_input_deck_name.getText().toString();
            if (keyword.isEmpty()) {
                ll_main_ui.setVisibility(View.VISIBLE);
                rv_result_list.setVisibility(View.GONE);
            } else {
                resultList.addAll(getResultList(keyword, allDeckList));
                rv_result_list.setVisibility(View.VISIBLE);
                ll_main_ui.setVisibility(View.GONE);
                resultListAdapter.notifyDataSetChanged();
            }
        }

        private String[] getStringType(List<DeckType> deckTypeList) {
            String[] types = new String[deckTypeList.size()];
            for (int i = 0; i < types.length; i++) {
                types[i] = deckTypeList.get(i).getName();
            }
            return types;
        }

        private List<DeckFile> getResultList(String keyword, List<DeckFile> deckList) {
            List<DeckFile> resultList = new ArrayList<>();
            for (int i = 0; i < deckList.size(); i++) {
                if (deckList.get(i).getFileName().toLowerCase().contains(keyword.toLowerCase()))
                    resultList.add(deckList.get(i));
            }
            return resultList;
        }

        private List<String> getStringTypeList(List<DeckType> deckTypeList) {
            List<String> typeList = new ArrayList<>();
            for (int i = 0; i < deckTypeList.size(); i++) {
                typeList.add(deckTypeList.get(i).getName());
            }
            return typeList;
        }

        //获取可以移动的分类
        private List<DeckType> getOtherTypeList() {
            List<DeckType> typeList = typeAdp.getData();
            List<DeckType> moveTypeList = new ArrayList<>();
            DeckType selectType = typeList.get(typeAdp.getSelectPosition());
            for (int i = 2; i < typeList.size(); i++) {
                DeckType deckType = typeList.get(i);
                if (!deckType.getPath().equals(selectType.getPath())) {
                    moveTypeList.add(deckType);
                }
            }
            return moveTypeList;
        }

        private void showAllDeckUtil() {
            ImageUtil.reImageColor(IMAGE_MOVE, iv_move);//可用时用原图标色
            ImageUtil.reImageColor(IMAGE_DEL, iv_del);
            ImageUtil.reImageColor(IMAGE_COPY, iv_copy);
            tv_del.setTextColor(YGOUtil.c(R.color.holo_blue_bright));//可用时字色蓝
            tv_copy.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
            tv_move.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
            ll_del.setEnabled(true);
            ll_copy.setEnabled(true);
            ll_move.setEnabled(true);
        }

        private void hideAllDeckUtil() {
            ImageUtil.setGrayImage(IMAGE_MOVE, iv_move);
            ImageUtil.setGrayImage(IMAGE_DEL, iv_del);
            ImageUtil.setGrayImage(IMAGE_COPY, iv_copy);
            tv_del.setTextColor(YGOUtil.c(R.color.star_rank));//不可用时字色灰
            tv_copy.setTextColor(YGOUtil.c(R.color.star_rank));
            tv_move.setTextColor(YGOUtil.c(R.color.star_rank));
            ll_del.setEnabled(false);
            ll_copy.setEnabled(false);
            ll_move.setEnabled(false);
        }

        private void showCopyDeckUtil() {
            ImageUtil.setGrayImage(IMAGE_MOVE, iv_move);
            ImageUtil.setGrayImage(IMAGE_DEL, iv_del);
            ImageUtil.reImageColor(IMAGE_COPY, iv_copy);
            tv_del.setTextColor(YGOUtil.c(R.color.star_rank));
            tv_copy.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
            tv_move.setTextColor(YGOUtil.c(R.color.star_rank));
            ll_del.setEnabled(false);
            ll_copy.setEnabled(true);
            ll_move.setEnabled(false);
        }

        private void clearDeckSelect() {
            deckAdp.setManySelect(false);
            hideAllDeckUtil();
        }

        public void show() {
            if (ygoDialog != null && !ygoDialog.isShowing()) {
                ygoDialog.show();
            }
        }

        public void dismiss() {
            if (ygoDialog != null && ygoDialog.isShowing())
                ygoDialog.dismiss();
        }

    }

}

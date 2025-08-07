package cn.garymb.ygomobile.ui.cards.deck_square;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;

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
import cn.garymb.ygomobile.lite.databinding.FragmentDeckSelectBinding;
import cn.garymb.ygomobile.ui.adapters.DeckListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.adapters.TextSelectAdapter;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.recyclerview.DeckTypeTouchHelperCallback;

//在dialog中卡组选择的Fragment，选中页面中某项后，在卡组编辑页面中显示卡片
public class DeckSelectFragment extends Fragment {

    private FragmentDeckSelectBinding binding;

    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    private TextSelectAdapter<DeckType> typeAdp;//卡组dialog中，左列的adapter
    private DeckListAdapter<DeckFile> deckAdp;//卡组dialog中，右列的adapter
    private DeckListAdapter<DeckFile> resultListAdapter;
    private List<DeckFile> allDeckList = new ArrayList<>();//存储所有卡组DeckFile，用于支持“根据关键词搜索卡组”功能

    private List<DeckFile> resultList = new ArrayList<>();//存储卡组“根据关键词搜索”的结果

    List<DeckType> typeList = null;
    List<DeckFile> deckList = null;     //存储当前卡组分类下的所有卡组
    private YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener;//通知外部调用方，（如调用本fragment的activity）
    private YGODeckDialogUtil.OnDeckDialogListener mDialogListener;

    /**
     * @param onDeckMenuListener 通知容纳dialog的外部页面，已选中了某项卡组
     * @param dialogListener     通知容纳本fragment的dialog，调用dismiss()方法，关闭dialog显示
     */
    DeckSelectFragment(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, YGODeckDialogUtil.OnDeckDialogListener dialogListener) {
        super();
        this.onDeckMenuListener = onDeckMenuListener;
        this.mDialogListener = dialogListener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDeckSelectBinding.inflate(inflater, container, false);

        binding.rvDeck.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        binding.rvType.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        binding.rvResultList.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));

        initAdapter();

        binding.rvType.setAdapter(typeAdp);
        binding.rvDeck.setAdapter(deckAdp);
        binding.rvResultList.setAdapter(resultListAdapter);

        hideAllDeckUtil();


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
                    //dismiss();

                    mDialogListener.onDismiss();
                    onDeckMenuListener.onDeckSelect(item);
                }
            }
        });


        //对话框中长点击某一卡组名称后，触发该事件
        deckAdp.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                DeckFile item = (DeckFile) adapter.getItem(position);
                //即使为local，也有可能为卡包预览，因此过滤掉selectposition==0
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
        resultListAdapter.setOnItemSelectListener(new DeckListAdapter.OnItemSelectListener<DeckFile>() {
            @Override
            public void onItemSelect(int position, DeckFile item) {
                binding.rvResultList.setVisibility(View.GONE);
                binding.llMainUi.setVisibility(View.VISIBLE);
                binding.inputDeckName.getEditableText().clear();
                //dismiss();
                mDialogListener.onDismiss();
                onDeckMenuListener.onDeckSelect(item);
            }
        });
        binding.ivSearchDeckName.setOnClickListener(v -> {
            searchDeck();
        });

        binding.inputDeckName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                searchDeck();
                return true;
            }
            return false;
        });

        binding.inputDeckName.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 输入中监听
                if (s.toString().isEmpty()) {
                    binding.llMainUi.setVisibility(View.VISIBLE);
                    binding.rvResultList.setVisibility(View.GONE);
                    binding.ivSearchDeckName.setVisibility(View.GONE);
                } else {
                    binding.ivSearchDeckName.setVisibility(View.VISIBLE);
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

        binding.llAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List list = new ArrayList();
                list.add(getContext().getString(R.string.category_name));
                list.add(getContext().getString(R.string.deck_name));
                SimpleListAdapter catelistadapter = new SimpleListAdapter(getContext());
                catelistadapter.set(list);
                DialogPlus dialog = new DialogPlus(getContext());
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
                            DialogPlus builder = new DialogPlus(getContext());
                            builder.setTitle(R.string.please_input_category_name);
                            EditText editText = new EditText(getContext());
                            editText.setGravity(Gravity.TOP | Gravity.LEFT);
                            editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                            editText.setSingleLine();
                            builder.setContentView(editText);
                            builder.setOnCloseLinster(DialogInterface::dismiss);
                            builder.setLeftButtonListener((dlg, s) -> {
                                CharSequence catename = editText.getText();
                                if (TextUtils.isEmpty(catename)) {
                                    YGOUtil.showTextToast(getContext().getString(R.string.invalid_category_name));
                                    return;
                                }
                                File file = new File(AppsSettings.get().getDeckDir(), catename.toString());
                                if (IOUtils.createFolder(file)) {
                                    typeList.add(new DeckType(catename.toString(), file.getAbsolutePath()));
                                    typeAdp.notifyItemInserted(typeList.size() - 1);
                                    dlg.dismiss();
                                } else {
                                    YGOUtil.showTextToast(getContext().getString(R.string.create_new_failed));
                                }
                            });
                            builder.show();
                            break;
                        case 1:
                            dialog.dismiss();
                            onDeckMenuListener.onDeckNew((typeAdp.getSelectPosition() > 1 ? typeList.get(typeAdp.getSelectPosition()) : typeList.get(2)));//如果选中卡包展示和人机卡组的场合创建卡组则在未分类下创建这个新卡组
                            break;
                    }
                });
                dialog.show();
            }
        });

        binding.llMove.setOnClickListener(view -> {
            List<DeckType> otherType = getOtherTypeList();
            List<String> cateNameList = getStringTypeList(otherType);
            SimpleListAdapter simpleListAdapter = new SimpleListAdapter(getContext());
            simpleListAdapter.set(cateNameList);

            DialogPlus dialog = new DialogPlus(getContext());
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
                YGOUtil.showTextToast(getContext().getString(R.string.done));
                onDeckMenuListener.onDeckMove(deckAdp.getSelectList(), toType);
                clearDeckSelect();
                dialog.dismiss();
            });
            dialog.show();
        });

        binding.llCopy.setOnClickListener(view -> {
            List<DeckType> otherType = getOtherTypeList();
            List<String> cateNameList = getStringTypeList(otherType);
            SimpleListAdapter simpleListAdapter = new SimpleListAdapter(getContext());
            simpleListAdapter.set(cateNameList);

            DialogPlus dialog = new DialogPlus(getContext());
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
                YGOUtil.showTextToast(getContext().getString(R.string.done));
                onDeckMenuListener.onDeckCopy(deckAdp.getSelectList(), toType);
                clearDeckSelect();
                dialog.dismiss();
            });
            dialog.show();
        });

        binding.llDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deckAdp.getSelectList().size() == 0) {
                    YGOUtil.showTextToast(getContext().getString(R.string.no_deck_is_selected));
                    return;
                }
                DialogPlus dialogPlus = new DialogPlus(getContext());
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
        //todo
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new DeckTypeTouchHelperCallback(new YGODeckDialogUtil.OnDeckTypeListener() {
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
        itemTouchHelper.attachToRecyclerView(binding.rvType);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initAdapter() {
        String selectDeckPath = AppsSettings.get().getLastDeckPath();
        typeList = DeckUtil.getDeckTypeList(getContext());

        int typeSelectPosition = 2;//卡组分类选择，默认值为2（未分类卡组）。0代表“卡包展示”，1代表“人机卡组”
        int deckSelectPosition = -1;

        //根据卡组路径selectDeckPath得到该卡组所属分类
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
                    typeSelectPosition = 2;
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
        //根据卡组分类查出属于该分类下的所有卡组
        deckList = DeckUtil.getDeckList(typeList.get(typeSelectPosition).getPath());

        for (int i = 0; i < typeList.size(); i++) {
            if (typeList.get(i).isLocal()) {
                allDeckList.addAll(DeckUtil.getDeckList(typeList.get(i).getPath()));//把所有分类里的卡组全部纳入，用于关键词查询目标
            }//在线卡组对应的项的path是空字符串
        }
        if (typeSelectPosition == 0) {//如果选中卡包，判断是否开启了先行卡，是的话加载先行卡
            if (AppsSettings.get().isReadExpansions()) {
                try {
                    deckList.addAll(0, DeckUtil.getExpansionsDeckList());//置顶ypk缓存的cacheDeck下的先行卡ydk
                } catch (IOException e) {
                    YGOUtil.showTextToast("额外卡库加载失败,原因为" + e);
                }
            }
        }
        resultListAdapter = new DeckListAdapter<DeckFile>(getContext(), resultList, -1);
        typeAdp = new TextSelectAdapter<>(typeList, typeSelectPosition);
        deckAdp = new DeckListAdapter<>(getContext(), deckList, deckSelectPosition);
    }

    /**
     * 根据et_input_deck_name的当前值，在allDeckList中搜索卡组
     */
    private void searchDeck() {
        resultList.clear();
        binding.inputDeckName.clearFocus();
        String keyword = binding.inputDeckName.getText().toString();
        if (keyword.isEmpty()) {
            binding.llMainUi.setVisibility(View.VISIBLE);
            binding.rvResultList.setVisibility(View.GONE);
        } else {
            resultList.addAll(resultListAdapter.getResultList(keyword, allDeckList));
            binding.rvResultList.setVisibility(View.VISIBLE);
            binding.llMainUi.setVisibility(View.GONE);
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

    private final int IMAGE_MOVE = 0;
    private final int IMAGE_COPY = 1;
    private final int IMAGE_DEL = 2;

    //将界面上的iv_move、iv_del、iv_copy等图标颜色恢复，启用其点击事件
    private void showAllDeckUtil() {
        ImageUtil.reImageColor(IMAGE_MOVE, binding.ivMove);//可用时用原图标色
        ImageUtil.reImageColor(IMAGE_DEL, binding.ivDel);
        ImageUtil.reImageColor(IMAGE_COPY, binding.ivCopy);
        binding.tvDel.setTextColor(YGOUtil.c(R.color.holo_blue_bright));//可用时字色蓝
        binding.tvCopy.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
        binding.tvMove.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
        binding.llDel.setEnabled(true);
        binding.llCopy.setEnabled(true);
        binding.llMove.setEnabled(true);
    }

    //将界面上的iv_move、iv_del、iv_copy等图标颜色改为灰色，禁用其点击事件
    private void hideAllDeckUtil() {
        ImageUtil.setGrayImage(IMAGE_MOVE, binding.ivMove);
        ImageUtil.setGrayImage(IMAGE_DEL, binding.ivDel);
        ImageUtil.setGrayImage(IMAGE_COPY, binding.ivCopy);
        binding.tvDel.setTextColor(YGOUtil.c(R.color.star_rank));//不可用时字色灰
        binding.tvCopy.setTextColor(YGOUtil.c(R.color.star_rank));
        binding.tvMove.setTextColor(YGOUtil.c(R.color.star_rank));
        binding.llDel.setEnabled(false);
        binding.llCopy.setEnabled(false);
        binding.llMove.setEnabled(false);
    }

    //将界面上的iv_copy图标颜色恢复，启用其点击事件
    private void showCopyDeckUtil() {
        ImageUtil.setGrayImage(IMAGE_MOVE, binding.ivMove);
        ImageUtil.setGrayImage(IMAGE_DEL, binding.ivDel);
        ImageUtil.reImageColor(IMAGE_COPY, binding.ivCopy);
        binding.tvDel.setTextColor(YGOUtil.c(R.color.star_rank));
        binding.tvCopy.setTextColor(YGOUtil.c(R.color.holo_blue_bright));
        binding.tvMove.setTextColor(YGOUtil.c(R.color.star_rank));
        binding.llDel.setEnabled(false);
        binding.llCopy.setEnabled(true);
        binding.llMove.setEnabled(false);
    }

    //清除选中卡组的记录，隐藏对卡组进行复制、移动、删除的控件
    private void clearDeckSelect() {
        deckAdp.setManySelect(false);
        hideAllDeckUtil();
    }

//    public void show() {
//        if (ygoDialog != null && !ygoDialog.isShowing()) {
//            ygoDialog.show();
//        }
//    }
//
//    public void dismiss() {
//        if (ygoDialog != null && ygoDialog.isShowing())
//            ygoDialog.dismiss();
//    }

}

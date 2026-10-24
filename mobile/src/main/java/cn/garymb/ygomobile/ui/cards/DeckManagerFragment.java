package cn.garymb.ygomobile.ui.cards;

import static android.content.Context.CLIPBOARD_SERVICE;
import static cn.garymb.ygomobile.Constants.ORI_DECK;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;
import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_SHARE_FILE;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RotateDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.FastScrollLinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelperPlus;
import androidx.recyclerview.widget.OnItemDragListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerViewItemListener;

import com.app.hubert.guide.NewbieGuide;
import com.app.hubert.guide.model.GuidePage;
import com.app.hubert.guide.model.HighLight;
import com.app.hubert.guide.model.HighlightOptions;
import com.feihua.dialogutils.util.DialogUtils;
import com.nightonke.boommenu.BoomButtons.BoomButton;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.BoomMenuButton;
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.bean.events.DeckDeleteEvent;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.CardSearchInfo;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.cards.deck.DeckAdapater;
import cn.garymb.ygomobile.ui.cards.deck.DeckDragDropHelper;
import cn.garymb.ygomobile.ui.cards.deck.DeckItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemTouchHelper;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.ui.cards.deck.DeckLayoutManager;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckManageDialog;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginToken;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck.MyDeckItem;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.AOnGestureListener;
import cn.garymb.ygomobile.ui.plus.DefaultOnBoomListener;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.ShareUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.PackManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

/**
 * 卡组编辑页面（拆分后的具体叶子类，保持原类名）。
 *
 * <p>原单一文件共 2259 行，为符合单类规模规范拆为继承链：
 * {@link DeckManagerFragmentBase} ← {@link DeckManagerFragmentDeck} ← {@link DeckManagerFragmentSearch} ←
 * {@link DeckManagerFragmentMenu} ← 本类。本类持有浮动菜单按钮、卡组管理对话框回调
 * 与新手引导等叶子逻辑。</p>
 *
 * <p>注意，卡组编辑页面中的长按事件回调在ItemTouchHelperPlus中实现，而非在
 * RecyclerViewItemListener.OnItemListener中；外部调用方式（静态 getOriginalData、
 * 实例化等）与拆分前完全一致。</p>
 */
public class DeckManagerFragment extends DeckManagerFragmentMenu {
    protected void initBoomMenuButton(BoomMenuButton menu) {
        final SparseArray<Integer> mMenuIds = new SparseArray<>();
        addMenuButton(mMenuIds, menu, R.id.action_share_deck, R.string.share_deck, R.drawable.shareicon);
        addMenuButton(mMenuIds, menu, R.id.action_save, R.string.save_deck, R.drawable.save);
        addMenuButton(mMenuIds, menu, R.id.action_clear_deck, R.string.clear_deck, R.drawable.clear_deck);

        addMenuButton(mMenuIds, menu, R.id.action_deck_new, R.string.new_deck, R.drawable.ic_add_2);
        addMenuButton(mMenuIds, menu, R.id.action_rename, R.string.rename_deck, R.drawable.rename);
        addMenuButton(mMenuIds, menu, R.id.action_delete_deck, R.string.delete_deck, R.drawable.delete);

        addMenuButton(mMenuIds, menu, R.id.action_unsort, R.string.unsort, R.drawable.unsort);
        addMenuButton(mMenuIds, menu, R.id.action_sort, R.string.sort, R.drawable.sort);
        addMenuButton(mMenuIds, menu, R.id.action_deck_backup_n_restore, R.string.deck_backup_n_restore, R.drawable.back_restore);

        //设置展开或隐藏的延时。 默认值为 800ms。
        menu.setDuration(150);
        //设置每两个子按钮之间动画的延时（ms为单位）。 比如，如果延时设为0，那么所有子按钮都会同时展开或隐藏，默认值为100ms。
        menu.setDelay(10);

        menu.setOnBoomListener(new DefaultOnBoomListener() {
            @Override
            public void onClicked(int index, BoomButton boomButton) {
                doMenu(mMenuIds.get(index));
            }
        });
    }

    protected void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, int stringId, int image) {
        addMenuButton(mMenuIds, menuButton, menuId, getString(stringId), image);
    }

    protected void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, String str, int image) {
        // 创建一个 LayerDrawable 来组合背景和图标
        LayerDrawable layerDrawable = createDrawableWithBackground(image);
        TextOutsideCircleButton.Builder builder = new TextOutsideCircleButton.Builder()
                .shadowColor(Color.TRANSPARENT)
                .normalColor(Color.TRANSPARENT)
                .normalImageDrawable(layerDrawable)
                .normalText(str);
        menuButton.addBuilder(builder);
        mMenuIds.put(mMenuIds.size(), menuId);
    }

    // 创建带背景的 drawable
    protected LayerDrawable createDrawableWithBackground(int imageRes) {
        Drawable background = ContextCompat.getDrawable(getContext(), R.drawable.button_cube);
        Drawable icon = ContextCompat.getDrawable(getContext(), imageRes);

        Drawable[] layers = new Drawable[2];
        layers[0] = background;
        layers[1] = icon;

        LayerDrawable layerDrawable = new LayerDrawable(layers);
        // 可以调整图标的位置和大小
        layerDrawable.setLayerInset(1, 200, 200, 200, 200); // left, top, right, bottom insets

        return layerDrawable;
    }

    protected void animRotate(View view) {
        // 获取背景并启动旋转动画
        LayerDrawable layerDrawable = (LayerDrawable) view.getBackground();
        RotateDrawable rotateDrawable = (RotateDrawable) layerDrawable.findDrawableByLayerId(R.id.background_layer);

        // 使用属性动画控制旋转
        ObjectAnimator animator = ObjectAnimator.ofInt(rotateDrawable, "level", 0, 10000);
        animator.setDuration(25000);//控制旋转速度
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.start();
    }

    //在卡组选择的dialog中点击某个卡组（来自本地或服务器）后，dialog通过本回调函数通知本页面。
    //在本页面中根据卡组来源（本地或服务器）显示卡组内容
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onDeckSelect(DeckFile deckFile) {
        if (!deckFile.isLocal()) {//不在本地，在云上（卡组广场中或用户的云上）
            VUiKit.defer().when(() -> {
                DownloadDeckResponse response = DeckSquareApiUtil.getDeckById(deckFile.getDeckId());
                if (response != null) {
                    return response.getData();
                } else {
                    return null;
                }
            }).fail((e) -> {
                LogUtil.i(TAG, "square deck detail fail" + e.getMessage());
            }).done((deckData) -> {
                if (deckData != null) {
                    mDeckId = deckData.getDeckId();
                    LogUtil.i(TAG, deckData.toString());
                    String fileFullName = deckData.getDeckName() + YDK_FILE_EX;
                    File dir = new File(getActivity().getApplicationInfo().dataDir, "cache");
                    //将卡组存到cache缓存目录中
                    boolean result = DeckUtil.saveFileToPath(dir.getPath(), fileFullName, deckData.getDeckYdk(), deckData.getDeckUpdateDate());
                    if (result) {//存储成功，使用预加载功能
                        LogUtil.i(TAG, "square deck detail done");
                        preLoadFile(dir.getPath() + "/" + fileFullName);
                        tv_add_1.setText(R.string.like_deck_thumb);
                        ll_click_like.setVisibility(View.VISIBLE);
                    }
                }
            });

        } else {
            Log.d(TAG, deckFile.getPathFile().getAbsolutePath());
            loadDeckFromFile(deckFile.getPathFile());
            ll_click_like.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDeckDel(List<DeckFile> deckFileList) {
        File deck = mDeckAdapater.getYdkFile();
        if (deck == null)
            return;
        String currentDeckPath = deck.getAbsolutePath();

        for (DeckFile deckFile : deckFileList) {
            String deckId = deckFile.getDeckId();
            for (MyOnlineDeckDetail onlineDeck : originalData) {
                boolean matched = false;
                if (deckId != null && !deckId.isEmpty()
                        && deckId.equals(onlineDeck.getDeckId())) {
                    matched = true;
                } else if (deckFile.getName() != null && deckFile.getTypeName() != null
                        && deckFile.getName().equals(onlineDeck.getDeckName())
                        && deckFile.getTypeName().equals(onlineDeck.getDeckType())) {
                    matched = true;
                }
                if (matched) {
                    onlineDeck.setDelete(true);
                    break;
                }
            }
        }
        EventBus.getDefault().post(new DeckDeleteEvent());

        for (DeckFile deckFile : deckFileList) {
            LogUtil.w(TAG, "要删除的卡组：" + "\n卡组分类: " + deckFile.getTypeName() + "\n卡组名称：" + deckFile.getFileName() + "\n卡组id: " + deckFile.getDeckId());
            if (TextUtils.equals(deckFile.getPath(), currentDeckPath)) {
                List<File> files = getYdkFiles();
                File file = null;
                if (files != null && !files.isEmpty()) {
                    file = files.get(0);
                }
                if (file != null) {
                    loadDeckFromFile(file);
                } else {
                    setCurDeck(new DeckInfo(), false);
                }
                DeckSquareApiUtil.deleteDecks(List.of(deckFile));
                return;
            }
        }
        DeckSquareApiUtil.deleteDecks(deckFileList);
        YGOUtil.showTextToast(R.string.done);
    }

    @Override
    public void onDeckMove(List<DeckFile> deckFileList, DeckType toDeckType) {
        File ydk = mDeckAdapater.getYdkFile();
        if (ydk == null) {
            return;
        }
        String currentDeckPath = ydk.getPath();
        for (DeckFile deckFile : deckFileList) {
            if (TextUtils.equals(currentDeckPath, deckFile.getPath())) {
                loadDeckFromFile(new File(toDeckType.getPath(), deckFile.getFileName()));
                return;
            }
        }
    }

    @Override
    public void onDeckCopy(List<DeckFile> deckFileList, DeckType toDeckType) {
        File ydk = mDeckAdapater.getYdkFile();
        if (ydk == null) {
            return;
        }
        String currentDeckPath = ydk.getPath();
        for (DeckFile deckFile : deckFileList) {
            if (TextUtils.equals(currentDeckPath, deckFile.getPath())) {
                loadDeckFromFile(new File(toDeckType.getPath(), deckFile.getFileName()));
                return;
            }
        }
    }

    @Override
    public void onDeckNew(DeckType currentDeckType) {
        createDeck(currentDeckType.getPath());
    }

    //region card edit
    @Override
    public void onLimitListChanged(LimitList limitList) {

    }

    @Override
    public void onDragStart() {

    }

    @Override
    public void onDragEnd() {
        // 在拖拽结束后添加当前状态到历史记录中
        addDeckToHistory(mDeckAdapater.getCurrentState());
    }

    //https://www.jianshu.com/p/99649af3b191
    public void showNewbieGuide(String scene) {
        HighlightOptions options = new HighlightOptions.Builder()//绘制一个高亮虚线圈
                .setOnHighlightDrewListener((canvas, rectF) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(20);
                    paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                    canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2 + 10, paint);
                }).build();
        HighlightOptions options2 = new HighlightOptions.Builder()//绘制一个高亮虚线矩形
                .setOnHighlightDrewListener((canvas, rectF) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(20);
                    paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                    canvas.drawRect(rectF, paint);
                }).build();
        if (scene.equals("deckmain")) {
            NewbieGuide.with(this)//with方法可以传入Activity或者Fragment，获取引导页的依附者
                    .setLabel("deckmainGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.deck_menu), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        //可只创建一个引导layout并把相关内容都放在其中并GONE，获得ID并初始化相应为显示
                                        view.findViewById(R.id.view_abt_bmb).setVisibility(View.VISIBLE);
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.nav_search), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_about);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_button_search);
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.nav_list), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_about);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_button_search_result);
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.tv_deckmanger), HighLight.Shape.CIRCLE, options2)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_about);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_view_deck_manager);
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(new RectF(screenWidth / 10.0f, screenWidth / 20.0f, screenWidth / 5.0f, screenWidth / 20.0f + screenWidth / 10.0f * 254.0f / 177.0f), HighLight.Shape.RECTANGLE, options2)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        TextView tv = view.findViewById(R.id.text_abt_mid);
                                        tv.setVisibility(View.VISIBLE);
                                        tv.setText(R.string.guide_view_move_card);
                                    })
                    )
                    //.alwaysShow(true)//总是显示，调试时可以打开
                    .show();

        }
    }

}

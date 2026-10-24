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
 * DeckManagerFragment 拆分后的抽象基类（继承链顶端）。
 *
 * <p>由原 2259 行的 {@link DeckManagerFragment} 拆分而来，功能与实现细节完全保持不变。
 * 继承链：本类 ← {@link DeckManagerFragmentDeck} ← {@link DeckManagerFragmentSearch} ←
 * {@link DeckManagerFragmentMenu} ← {@link DeckManagerFragment}。
 * 本类持有全部共享字段与静态数据、onCreateView/initView、预加载、监听器注册、
 * 生命周期回调、拖拽长按删除与抽屉开关等骨架逻辑；文末抽象钩子由链下游子类实现。</p>
 */
public abstract class DeckManagerFragmentBase extends BaseFragemnt implements RecyclerViewItemListener.OnItemListener, OnItemDragListener, YGODeckDialogUtil.OnDeckMenuListener, CardLoader.CallBack, CardSearcher.CallBack {
    protected static final String TAG = "DeckManagerFragment";
    public static List<MyOnlineDeckDetail> originalData; // 保存原始数据
    protected final AppsSettings mSettings = AppsSettings.get();
    public DeckAdapater mDeckAdapater;
    protected DrawerLayout mDrawerLayout;
    protected RecyclerView mListView;
    protected CardLoader mCardLoader;
    protected CardSearcher mCardSearcher;
    protected CardManager mCardManager;
    protected PackManager mPackManager;
    protected CardListAdapter mCardListAdapter;
    protected boolean isLoad = false;
    protected int screenWidth;
    long exitLasttime = 0;
    protected HomeActivity activity;
    protected String mDeckId;
    protected LinearLayout ll_click_like;
    //region ui onCreate/onDestroy
    protected RecyclerView mRecyclerView;
    protected BaseActivity mContext;
    // 历史记录列表，用于存储卡组状态
    protected final List<DeckInfo> deckHistory = new ArrayList<>();
    // 当前历史记录索引
    protected int historyIndex = -1;
    protected boolean isPackMode;
    protected File mPreLoadFile;//预加载卡组，用于外部打开ydk文件或通过卡组广场预览卡组时，值为file。当未通过预加载打开ydk（打开卡组时），值为null
    protected DeckItemTouchHelper mDeckItemTouchHelper;
    // 搜索结果卡片长按拖拽到卡组的浮层拖拽辅助
    protected DeckDragDropHelper mDeckDragHelper;
    protected TextView tv_add_1, tv_deck, tv_credit_limit, tv_credit_remain, tv_result_count, tv_credit_count;
    protected LinearLayout ll_genesys_scoreboard;
    protected AppCompatSpinner mCardSearchLimitSpinner;
    protected CardDetail mCardDetail;
    protected DialogPlus mDialog, builderShareLoading;
    protected ImageButton btnUndo, btnRedo;
    protected String currentCardSearchMessage = "";


    protected View layoutView;

    public static List<MyOnlineDeckDetail> getOriginalData() {
        return originalData;
    }

    /**
     * 创建并返回Fragment的视图层次结构
     *
     * @param inflater           用于将XML布局文件转换为View对象的LayoutInflater实例，不可为空
     * @param container          父容器ViewGroup，可为空
     * @param savedInstanceState 保存Fragment状态的Bundle对象，可为空
     * @return 返回创建的View对象，如果创建失败则返回null
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        activity = (HomeActivity) getActivity();
        originalData = new ArrayList<>();

        // 初始化布局视图
        layoutView = inflater.inflate(R.layout.fragment_deck_cards, container, false);
        AnimationShake2(layoutView);
        initView(layoutView);

        // 检查并预加载外部ydk文件
        preLoadFile();
        // 注册事件总线监听器
        if (!EventBus.getDefault().isRegistered(this)) {//加上判断
            EventBus.getDefault().register(this);
        }
        // 显示新手引导
        showNewbieGuide("deckmain");
        return layoutView;
    }

    /**
     * 初始化视图组件和相关逻辑
     * 此方法负责初始化布局中的各个UI控件，并设置它们的属性、适配器及事件监听器。
     * 同时还初始化了数据管理器、搜索器、加载器等业务组件，并处理了一些用户交互逻辑，
     * 如点赞功能与自动同步远程卡组信息。
     *
     * @param layoutView 布局视图对象，用于查找子控件
     */
    public void initView(View layoutView) {
        // 获取屏幕宽度
        screenWidth = getResources().getDisplayMetrics().widthPixels;

        // 初始化抽屉布局和列表视图
        mDrawerLayout = layoutView.findViewById(R.id.drawer_layout);
        mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                updateUndoRedoButtonVisibility();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                updateUndoRedoButtonVisibility();
            }

            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        mListView = layoutView.findViewById(R.id.list_cards);


        // 设置卡片列表适配器并启用滑动删除功能
        mCardListAdapter = new CardListAdapter(getContext(), activity.getImageLoader());
        mCardListAdapter.setShowAddButtons(true); // 显示添加按钮
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        mListView.setAdapter(mCardListAdapter);

        // 设置各类监听器
        setListeners();

        // 初始化数据管理器
        mPackManager = DataManager.get().getPackManager();
        mCardManager = DataManager.get().getCardManager();

        // 初始化卡片加载器和回调接口
        mCardLoader = new CardLoader();
        mCardLoader.setCallBack(this);

        // 初始化卡片搜索器及其回调接口
        mCardSearcher = new CardSearcher(layoutView.findViewById(R.id.nav_view_list), mCardLoader);
        mCardSearcher.setCallBack(this);

        //初始化genesys计分板
        ll_genesys_scoreboard = layoutView.findViewById(R.id.ll_genesys_scoreboard);
        tv_credit_count = layoutView.findViewById(R.id.tv_credit_count);
        tv_credit_limit = layoutView.findViewById(R.id.tv_credit_limit);
        tv_credit_remain = layoutView.findViewById(R.id.tv_credit_remain);

        // 查找底部卡组名称展示区域的文本控件
        tv_deck = layoutView.findViewById(R.id.tv_deck);
        tv_result_count = layoutView.findViewById(R.id.result_count);

        // 初始化右侧抽屉的条件搜索禁卡表下拉框并设置背景颜色
        mCardSearchLimitSpinner = mCardSearcher.findViewById(R.id.sp_limit_list);
        mCardSearchLimitSpinner.setPopupBackgroundResource(R.color.colorNavy);

        // 初始化网格形式的卡组显示区域
        mRecyclerView = layoutView.findViewById(R.id.grid_cards);
        mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(), 0, mRecyclerView.getPaddingRight(), mRecyclerView.getPaddingBottom());

        // 设置卡组适配器和自定义布局管理器
        mRecyclerView.setAdapter((mDeckAdapater = new DeckAdapater(getContext(), mRecyclerView, activity.getImageLoader())));
        mRecyclerView.setLayoutManager(new DeckLayoutManager(getContext(), Constants.DECK_WIDTH_COUNT));

        // 配置拖拽支持与触摸助手
        mDeckItemTouchHelper = new DeckItemTouchHelper(mDeckAdapater, mRecyclerView);
        ItemTouchHelperPlus touchHelper = new ItemTouchHelperPlus(getContext(), mDeckItemTouchHelper);
        touchHelper.setItemDragListener(this);
        touchHelper.setEnableClickDrag(Constants.DECK_SINGLE_PRESS_DRAG);
        touchHelper.attachToRecyclerView(mRecyclerView);

        // 初始化搜索结果卡片长按拖入卡组的浮层拖拽辅助
        mDeckDragHelper = new DeckDragDropHelper(getContext(), mDrawerLayout, mRecyclerView,
                mDeckAdapater, activity.getImageLoader(), new DeckDragDropHelper.OnDeckDropListener() {
                    @Override
                    public void onDragBegin() {
                        // 拖动开始：抽屉即将收起，无需额外处理
                    }

                    @Override
                    public void onCardDropped(Card card, DeckDragDropHelper.DropTarget target) {
                        handleDeckCardDropped(card, target);
                    }
                });

        // 添加RecyclerView的点击监听器
        mRecyclerView.addOnItemTouchListener(new RecyclerViewItemListener(mRecyclerView, this));

        // 初始化弹出菜单按钮及相关导航按钮点击事件
        initBoomMenuButton(layoutView.findViewById(R.id.bmb));
        layoutView.findViewById(R.id.btn_nav_search).setOnClickListener((v) -> doMenu(R.id.action_search));
        layoutView.findViewById(R.id.btn_nav_list).setOnClickListener((v) -> doMenu(R.id.action_card_list));

        // 点赞按钮逻辑：仅在当前加载的是有效卡组时可用
        tv_add_1 = layoutView.findViewById(R.id.tv_add_1);
        ll_click_like = layoutView.findViewById(R.id.ll_click_like);
        ll_click_like.setOnClickListener(v -> {
            if (mDeckId != null) {
                VUiKit.defer().when(() -> {
                    return DeckSquareApiUtil.likeDeck(mDeckId);
                }).fail(e -> {
                    LogUtil.i(TAG, "Like deck fail" + e.getMessage());
                    YGOUtil.showTextToast("点赞失败");
                }).done(data -> {
                    if (data != null && data.getMessage() != null && data.getMessage().equals("true")) {
                        // 显示点赞动画效果
                        tv_add_1.setText("+1");
                        ll_click_like.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.fade_out));
                        ll_click_like.setVisibility(View.GONE);
                    } else {
                        YGOUtil.showTextToast(data != null ? data.getMessage() : "点赞失败");
                    }
                    mDeckId = null;
                });
            } else {
                ll_click_like.setVisibility(View.GONE);
            }
        });

        // 卡组标题点击打开管理对话框
        tv_deck.setOnClickListener(v -> {
            DeckManageDialog.showDeckManageDialog(
                    requireActivity().getSupportFragmentManager(), this, 0, null);
        });

        // 初始化撤销/重做按钮
        btnUndo = layoutView.findViewById(R.id.btn_undo);
        animRotate(btnUndo);
        btnRedo = layoutView.findViewById(R.id.btn_redo);
        animRotate(btnRedo);
        btnUndo.setOnClickListener(v -> undo());
        btnRedo.setOnClickListener(v -> redo());

        mContext = (BaseActivity) getActivity();

        /**
         * 自动同步服务器上的卡组信息（如果已登录）
         */
        // 如果用户已登录（存在服务器令牌），则同步用户的在线卡组
        if (SharedPreferenceUtil.getServerToken() != null) {
            VUiKit.defer().when(() -> {
                try {
                    // 调用API同步卡组
                    DeckSquareApiUtil.synchronizeDecks();
                } catch (IOException e) {
                    // 发生IO异常时返回异常对象
                    return e;
                }
                // 正常执行完成后返回0表示成功
                return 0;
            }).fail((e) -> {
                // 同步失败时记录错误日志
                LogUtil.e(TAG, "Sync decks failed: " + e);
                //YGOUtil.showTextToast("Sync decks failed: " + e);
            }).done((result) -> {
                // 同步成功后的处理逻辑（目前为空）
            });
        }
    }

    /**
     * 预加载文件方法
     * 当外部调用fragment时，如果通过setArguments(mBundle)方法设置了ydk文件路径，则直接打开该ydk文件
     * 将mPreLoadFile设置为对应的File
     */
    public void preLoadFile() {
        String preLoadFilePath = "";
        // 从参数中获取预加载文件路径
        if (getArguments() != null) {
            preLoadFilePath = getArguments().getString("setDeck");
            getArguments().clear();
        }
        // 调用重载方法加载文件
        preLoadFile(preLoadFilePath);

    }

    /**
     * 传入外部ydk文件的路径，临时在本页面中打开该ydk的内容，用于后续的保存
     *
     * @param preLoadFilePath 外部ydk文件的路径
     */
    public void preLoadFile(String preLoadFilePath) {

        final File _file;
        // 打开指定卡组文件，如果路径有效则使用指定文件，否则使用最后访问的卡组文件
        if (!TextUtils.isEmpty(preLoadFilePath) && (mPreLoadFile = new File(preLoadFilePath)).exists()) {
            //外面卡组
            _file = mPreLoadFile;
        } else {
            mPreLoadFile = null;
            String path = mSettings.getLastDeckPath();
            if (TextUtils.isEmpty(path)) {
                _file = null;
            } else {
                //最后卡组
                _file = new File(path);
            }
        }
        // 检查原始卡组目录是否存在且当前卡组目录文件数量较少时，启动权限获取活动
        File oriDeckFiles = new File(ORI_DECK);
        File deckFiles = new File(AppsSettings.get().getDeckDir());
        if (oriDeckFiles.exists() && deckFiles.list().length <= 1) {
            mContext.startPermissionsActivity();
        }
        // 初始化卡组数据
        init(_file);
    }

    /**
     * 设置列表项点击监听器和滚动监听器
     * <p>
     * 该方法主要完成以下功能：
     * 1. 为卡片列表适配器设置点击和长按事件监听器
     * 2. 为列表视图添加滚动监听器，用于控制图片加载框架的暂停和恢复
     */
    protected void setListeners() {
        // 设置列表项点击监听器，当用户点击列表项时触发onCardClick回调
        mCardListAdapter.setOnItemClickListener((adapter, view, position) -> {
            onCardClick(view, mCardListAdapter.getItem(position), position);
        });

        // 设置列表项长按监听器，当用户长按列表项时触发onCardLongClick回调
        mCardListAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            onCardLongClick(view, mCardListAdapter.getItem(position), position);
            return true;
        });

        // 添加滚动监听器，根据滚动状态控制Glide图片加载的暂停和恢复
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_IDLE:
                    case RecyclerView.SCROLL_STATE_SETTLING:
                        // 列表停止滚动或正在减速时，恢复图片加载请求
                        GlideCompat.with(getContext()).resumeRequests();
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        // 用户正在拖拽列表时，暂停图片加载请求以提升滑动流畅度
                        GlideCompat.with(getContext()).pauseRequests();
                        break;
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onFirstUserVisible() {
    }

    @Override
    public void onUserVisible() {
    }

    @Override
    public void onFirstUserInvisible() {
    }

    @Override
    public void onUserInvisible() {
    }

    @Override
    public void onBackHome() {

    }

    @Override
    public void onStop() {
        super.onStop();
        CardFavorites.get().save();
    }

    @Override
    public void onDestroy() {
        //mImageLoader.close();
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        // 视图销毁时取消进行中的浮动卡片拖拽，避免浮层残留在根布局上
        if (mDeckDragHelper != null) {
            mDeckDragHelper.cancel();
            mDeckDragHelper = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onResetSearch() {

    }

    @Override
    public void onDragLongPress(int pos) {
        // 检查位置索引有效性
        if (pos < 0) return;
        if (Constants.DEBUG)
            Log.d(TAG, "delete " + pos);
        // 获取要删除的卡组项
        DeckItem deckItem = mDeckAdapater.getItem(pos);
        if (deckItem == null || deckItem.getCardInfo() == null) {
            return;
        }
        // 根据设置决定删除方式：弹窗确认删除 或 直接显示删除头部视图
        if (mSettings.isDialogDelete()) {
            // 创建并显示删除确认对话框
            DialogPlus dialogPlus = new DialogPlus(getContext());
            dialogPlus.setTitle(R.string.question);
            dialogPlus.setMessage(getString(R.string.delete_card, deckItem.getCardInfo().Name));
            dialogPlus.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            dialogPlus.setLeftButtonListener((dlg, v) -> {
                dlg.dismiss();
                mDeckItemTouchHelper.remove(pos);
            });
            dialogPlus.show();
        } else {
            mDeckItemTouchHelper.remove(pos);
            // 直接显示删除头部视图
            mDeckAdapater.showHeadView();
        }
        // 添加到历史记录
        addDeckToHistory(mDeckAdapater.getCurrentState());

    }

    @Override
    public void onDragLongPressEnd() {
        mDeckAdapater.hideHeadView();
    }

    public void AnimationShake2(View view) {
        Animation shake = AnimationUtils.loadAnimation(getContext(), R.anim.shake);//加载动画资源文件
        view.findViewById(R.id.cube2).startAnimation(shake); //给组件播放动画效果
    }

    /**
     * 隐藏所有打开的抽屉菜单
     * <p>
     * 该方法会检查左右两侧的抽屉是否处于打开状态，如果打开则将其关闭。
     * 主要用于在特定场景下统一关闭所有抽屉菜单，确保界面的一致性。
     */
    public void hideDrawers() {
        // 关闭右侧抽屉（如果已打开）
        if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
        }
        // 关闭左侧抽屉（如果已打开）
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
        }
    }

    protected void showSearch(boolean autoClose) {
        if (mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
        }
        if (autoClose && mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (isLoad) {
            mDrawerLayout.openDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
    }

    //region load deck

    protected void showResult(boolean autoClose) {
        if (mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        }
        if (autoClose && mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
            //showNewbieGuide("searchResult");
        } else if (isLoad) {
            mDrawerLayout.openDrawer(Constants.CARD_RESULT_GRAVITY);
        }
    }

    // ==== hooks implemented by subclasses in the chain (split artifact) ====
    abstract void init(File ydk);
    abstract void loadDeckFromFile(File file);
    abstract void setCurDeck(DeckInfo deckInfo, boolean isPack);
    abstract void addDeckToHistory(DeckInfo deckInfo);
    abstract void undo();
    abstract void redo();
    abstract void updateUndoRedoButtonVisibility();
    abstract void refreshDeckCreditCount();
    abstract int getCreditCount(DeckInfo deckinfo);
    abstract List<File> getYdkFiles();
    abstract void setLimitList(LimitList limitList);
    abstract boolean doMenu(int menuId);
    abstract void initBoomMenuButton(BoomMenuButton menu);
    abstract void animRotate(View view);
    abstract void showNewbieGuide(String scene);
    abstract void handleDeckCardDropped(Card card, DeckDragDropHelper.DropTarget target);
    abstract void onCardClick(View view, Card cardInfo, int pos);
    abstract void onCardLongClick(View view, Card cardInfo, int pos);

}

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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
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
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.CardInfoEvent;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.CardSearchInfo;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckAdapater;
import cn.garymb.ygomobile.ui.cards.deck.DeckItem;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemTouchHelper;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.ui.cards.deck.DeckLayoutManager;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckManageDialog;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareFileUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.BasicResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.DownloadDeckResponse;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
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
 * 卡组编辑页面，在本页面中显示某个卡组的内容
 * 注意，卡组编辑页面中的长按事件回调在ItemTouchHelperPlus中实现，而非在
 * RecyclerViewItemListener.OnItemListener中
 */
public class DeckManagerFragment extends BaseFragemnt implements RecyclerViewItemListener.OnItemListener, OnItemDragListener, YGODeckDialogUtil.OnDeckMenuListener, CardLoader.CallBack, CardSearcher.CallBack {
    private static final String TAG = "DeckManagerFragment";
    public static List<MyOnlineDeckDetail> originalData; // 保存原始数据
    private final AppsSettings mSettings = AppsSettings.get();
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
    private HomeActivity activity;
    private String mDeckId;
    private LinearLayout ll_click_like;
    private TextView tv_add_1;
    //region ui onCreate/onDestroy
    private RecyclerView mRecyclerView;
    private BaseActivity mContext;
    // 历史记录列表，用于存储卡组状态
    private List<DeckInfo> deckHistory = new ArrayList<>();
    // 当前历史记录索引
    private int historyIndex = -1;
    private boolean isPackMode;
    private File mPreLoadFile;//预加载卡组，用于外部打开ydk文件或通过卡组广场预览卡组时，值为file。当未通过预加载打开ydk（打开卡组时），值为null
    private DeckItemTouchHelper mDeckItemTouchHelper;
    private TextView tv_deck;
    private TextView tv_result_count;
    private AppCompatSpinner mLimitSpinner;
    private CardDetail mCardDetail;
    private DialogPlus mDialog;
    private DialogPlus builderShareLoading;

    private ImageButton btnUndo;
    private ImageButton btnRedo;


    private View layoutView;

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
        mListView = layoutView.findViewById(R.id.list_cards);

        // 设置卡片列表适配器并启用滑动删除功能
        mCardListAdapter = new CardListAdapter(getContext(), activity.getImageLoader());
        mCardListAdapter.setEnableSwipe(true);
        mListView.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        mListView.setAdapter(mCardListAdapter);

        // 设置各类监听器
        setListeners();

        // 初始化数据管理器
        mPackManager = DataManager.get().getPackManager();
        mCardManager = DataManager.get().getCardManager();

        // 初始化卡片加载器和回调接口
        mCardLoader = new CardLoader(getContext());
        mCardLoader.setCallBack(this);

        // 初始化卡片搜索器及其回调接口
        mCardSearcher = new CardSearcher(layoutView.findViewById(R.id.nav_view_list), mCardLoader);
        mCardSearcher.setCallBack(this);

        // 查找顶部展示区域的文本控件
        tv_deck = layoutView.findViewById(R.id.tv_deck);
        tv_result_count = layoutView.findViewById(R.id.result_count);

        // 初始化限制条件选择下拉框并设置背景颜色
        mLimitSpinner = layoutView.findViewById(R.id.sp_limit_list);
        mLimitSpinner.setPopupBackgroundResource(R.color.colorNavy);

        // 初始化网格形式的卡组显示区域
        mRecyclerView = layoutView.findViewById(R.id.grid_cards);
        mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(), 0, mRecyclerView.getPaddingRight(), mRecyclerView.getPaddingBottom());

        // 设置卡组适配器和自定义布局管理器
        mRecyclerView.setAdapter((mDeckAdapater = new DeckAdapater(getContext(), mRecyclerView, activity.getImageLoader())));
        mRecyclerView.setLayoutManager(new DeckLayoutManager(getContext(), Constants.DECK_WIDTH_COUNT));

        // 配置拖拽支持与触摸助手
        mDeckItemTouchHelper = new DeckItemTouchHelper(mDeckAdapater);
        ItemTouchHelperPlus touchHelper = new ItemTouchHelperPlus(getContext(), mDeckItemTouchHelper);
        touchHelper.setItemDragListener(this);
        touchHelper.setEnableClickDrag(Constants.DECK_SINGLE_PRESS_DRAG);
        touchHelper.attachToRecyclerView(mRecyclerView);

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
                    BasicResponse result = DeckSquareApiUtil.likeDeck(mDeckId);
                    return result;
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
            new DeckManageDialog(this).show(
                    getActivity().getSupportFragmentManager(), "pagerDialog");
        });

        // 初始化撤销/重做按钮
        btnUndo = layoutView.findViewById(R.id.btn_undo);
        animRotate(btnUndo);
        btnRedo = layoutView.findViewById(R.id.btn_redo);
        animRotate(btnRedo);
        btnUndo.setOnClickListener(v -> undo());
        btnRedo.setOnClickListener(v -> redo());

        // 初始化按钮状态
        updateUndoRedoButtons();

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
    public void onResetSearch() {

    }

    @Override
    public void onDragLongPress(int pos) {
        // 检查位置索引有效性
        if (pos < 0) return;
        if (Constants.DEBUG)
            Log.d(TAG, "delete " + pos);

        // 根据设置决定删除方式：弹窗确认删除 或 直接显示删除头部视图
        if (mSettings.isDialogDelete()) {
            // 获取要删除的卡组项
            DeckItem deckItem = mDeckAdapater.getItem(pos);
            if (deckItem == null || deckItem.getCardInfo() == null) {
                return;
            }

            // 创建并显示删除确认对话框
            DialogPlus dialogPlus = new DialogPlus(getContext());
            dialogPlus.setTitle(R.string.question);
            dialogPlus.setMessage(getString(R.string.delete_card, deckItem.getCardInfo().Name));
            dialogPlus.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            dialogPlus.setLeftButtonListener((dlg, v) -> {
                dlg.dismiss();
                mDeckItemTouchHelper.remove(pos);
                // 添加到历史记录
                addDeckToHistory(mDeckAdapater.getCurrentState());
            });
            dialogPlus.show();
        } else {
            // 直接显示删除头部视图
            mDeckAdapater.showHeadView();
        }
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

    //region init

    /**
     * 从指定文件中加载卡牌包数据
     *
     * @param file 包含卡牌数据的文件对象，如果文件不存在或为null则会设置空的卡牌包
     */
    private void loadDeckFromFile(File file) {
        // 检查卡牌加载器是否打开以及文件是否存在，如果条件不满足则设置空卡牌包
        if (!mCardLoader.isOpen() || file == null || !file.exists()) {
            setCurDeck(new DeckInfo(), false);
            return;
        }

        // 显示加载对话框
        DialogPlus dlg = DialogPlus.show(getContext(), null, getString(R.string.loading));

        // 异步加载卡牌包数据
        VUiKit.defer().when(() -> {
            // 双重检查卡牌加载器状态和文件存在性，确保数据加载安全
            if (mCardLoader.isOpen() && file.exists()) {
                return mDeckAdapater.read(mCardLoader, file, mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            // 关闭加载对话框并设置当前卡牌包
            dlg.dismiss();
            // 根据资源路径判断是否进入卡包展示模式
            if (rs != null && rs.source != null) {
                String parentPath = rs.source.getParent();
                isPackMode = parentPath != null &&
                        (parentPath.equals(mSettings.getPackDeckDir()) ||
                                parentPath.equals(mSettings.getCacheDeckDir()));
            } else {
                isPackMode = false;
            }
            setCurDeck(rs, isPackMode);
        });
    }

    /**
     * 初始化函数，用于加载卡组数据和相关配置
     *
     * @param ydk 卡组文件，可以为null，如果为null则会尝试加载默认卡组文件
     */
    public void init(@Nullable File ydk) {
        // 显示加载对话框
        DialogPlus dlg = DialogPlus.show(mContext, null, getString(R.string.loading));

        VUiKit.defer().when(() -> {
            // 加载数据管理器
            DataManager.get().load(true);

            // 设置限制卡表列表，使用第一个可用的限制列表
            if (activity.getmLimitManager().getCount() > 0) {
                mCardLoader.setLimitList(activity.getmLimitManager().getTopLimit());
            }

            // 处理卡组文件加载逻辑
            File file = ydk;
            if (file == null || !file.exists()) {
                // 当默认卡组不存在的时候，尝试获取其他可用的ydk文件
                List<File> files = getYdkFiles();
                if (files != null && files.size() > 0) {
                    file = files.get(0);
                }
            }

            // 如果没有找到有效的卡组文件，返回空的卡组信息
            if (file == null) {
                return new DeckInfo();
            }

            Log.i(TAG, "load ydk " + file);

            // 读取卡组文件内容
            if (mCardLoader.isOpen() && file.exists()) {
                return mDeckAdapater.read(mCardLoader, file, mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            // 初始化完成后的处理逻辑
            isLoad = true;
            dlg.dismiss();
            mCardSearcher.initItems();
            initLimitListSpinners(mLimitSpinner, mCardLoader.getLimitList());
            // 根据资源路径判断是否进入卡包展示模式
            if (rs != null && rs.source != null) {
                String parentPath = rs.source.getParent();
                isPackMode = parentPath != null &&
                        (parentPath.equals(mSettings.getPackDeckDir()) ||
                                parentPath.equals(mSettings.getCacheDeckDir()));
            } else {
                isPackMode = false;
            }
            // 设置当前卡组显示
            if (rs != null && rs.source != null) {
                setCurDeck(rs, isPackMode);
            } else {
                setCurDeck(rs, false);
            }

            // 设置并显示收藏夹
            mCardSearcher.showFavorites(false);
        });
    }

    /**
     * 设置当前选中的卡组并更新界面显示
     *
     * @param deckInfo 选中的卡组信息，如果为null则创建新的空卡组信息对象
     * @param isPack   是否为卡包模式
     */
    private void setCurDeck(DeckInfo deckInfo, boolean isPack) {
        // 处理空卡组信息情况
        if (deckInfo == null) {
            deckInfo = new DeckInfo();
        }
        File file = deckInfo.source;
        // 更新最后选择的卡组路径和界面显示名称
        if (file != null && file.exists()) {
            String name = IOUtils.tirmName(file.getName(), Constants.YDK_FILE_EX);
            mSettings.setLastDeckPath(file.getAbsolutePath());
            tv_deck.setText(name);
        }
        // 通知适配器更新卡组数据和界面
        mDeckAdapater.setDeck(deckInfo, isPack);
        mDeckAdapater.notifyDataSetChanged();
        isPackMode = isPack;

        // 清空历史记录并添加初始状态
        clearDeckHistory();
        addDeckToHistory(deckInfo);

        // 更新按钮状态
        updateUndoRedoButtons();
    }

    /**
     * 清空卡组历史记录
     */
    private void clearDeckHistory() {
        deckHistory.clear();
        historyIndex = -1;
    }

    /**
     * 将当前卡组状态添加到历史记录中（仅当与上一个状态不同时）
     *
     * @param deckInfo 当前卡组状态
     */
    private void addDeckToHistory(DeckInfo deckInfo) {
        // 如果当前不在历史记录的最新位置，需要删除之后的历史记录
        if (historyIndex < deckHistory.size() - 1) {
            deckHistory.subList(historyIndex + 1, deckHistory.size()).clear();
        }

        // 检查当前状态是否与最近的历史记录相同
        boolean isSameAsLast = false;
        if (!deckHistory.isEmpty()) {
            DeckInfo lastState = deckHistory.get(deckHistory.size() - 1);
            // 这里将DeckInfo.toLongString来实现了 equals 方法来比较内容
            isSameAsLast = lastState.toLongString().equals(deckInfo.toLongString());
        }

        // 只有在状态不同时才添加新的历史记录
        if (!isSameAsLast) {
            deckHistory.add(deckInfo);
            historyIndex = deckHistory.size() - 1;
        }

        // 更新按钮状态
        updateUndoRedoButtons();
    }

    /**
     * 撤销操作
     */
    private void undo() {
        if (canUndo()) {
            historyIndex--;
            DeckInfo previousState = deckHistory.get(historyIndex);
            mDeckAdapater.setDeck(previousState, false);
            mDeckAdapater.notifyDataSetChanged();
            // 更新按钮状态
            updateUndoRedoButtons();
        }
    }

    /**
     * 重做操作
     */
    private void redo() {
        if (canRedo()) {
            historyIndex++;
            DeckInfo nextState = deckHistory.get(historyIndex);
            mDeckAdapater.setDeck(nextState, false);
            mDeckAdapater.notifyDataSetChanged();
            // 更新按钮状态
            updateUndoRedoButtons();
        }
    }

    /**
     * 检查是否可以撤销
     *
     * @return 如果可以撤销返回true，否则返回false
     */
    private boolean canUndo() {
        return historyIndex > 0;
    }

    /**
     * 检查是否可以重做
     *
     * @return 如果可以重做返回true，否则返回false
     */
    private boolean canRedo() {
        return historyIndex < deckHistory.size() - 1;
    }

    /**
     * 更新撤销/重做按钮的状态和可见性
     */
    private void updateUndoRedoButtons() {
        if (btnUndo != null && btnRedo != null) {
            // 只有历史记录大于1且不是卡包展示模式时才显示按钮
            if (deckHistory.size() > 1 && !isPackMode) {
                btnUndo.setVisibility(View.VISIBLE);
                btnRedo.setVisibility(View.VISIBLE);

                // 根据是否可以撤销/重做来启用/禁用按钮
                btnUndo.setEnabled(canUndo());
                btnRedo.setEnabled(canRedo());

                // 当无法撤销或重做时隐藏对应按钮
                if (!canUndo()) {
                    btnUndo.setVisibility(View.INVISIBLE);
                }
                if (!canRedo()) {
                    btnRedo.setVisibility(View.INVISIBLE);
                }
            } else {
                // 历史记录小于等于1或为卡包展示模式时隐藏按钮
                btnUndo.setVisibility(View.INVISIBLE);
                btnRedo.setVisibility(View.INVISIBLE);
            }
        }
    }


    /**
     * 判断指定文件是否位于卡牌目录中
     *
     * @param file 要检查的文件对象
     * @return 如果文件的父目录等于卡牌目录则返回true，否则返回false
     */
    private boolean inDeckDir(File file) {
        // 获取卡牌目录的绝对路径
        String deck = new File(AppsSettings.get().getDeckDir()).getAbsolutePath();
        // 比较文件父目录与卡牌目录是否相同
        return TextUtils.equals(deck, file.getParent());
    }

    @Override
    public void onSearchStart() {
        hideDrawers();
    }

    protected void onCardClick(View view, Card cardInfo, int pos) {
        if (mCardListAdapter.isShowMenu(view)) {
            return;
        }
        if (cardInfo != null) {
            showCardDialog(mCardListAdapter, cardInfo, pos);
        }
    }

    protected void onCardLongClick(View view, Card cardInfo, int pos) {
        //  mCardListAdapater.showMenu(view);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCardInfoEvent(CardInfoEvent event) {
        int pos = event.position;
        Card cardInfo = mCardListAdapter.getItem(pos);
        if (cardInfo == null) {
            mCardListAdapter.hideMenu(null);
        } else if (event.toMain) {
            if (!addMainCard(cardInfo)) {// || !checkLimit(cardInfo, false)) {
                mCardListAdapter.hideMenu(null);
            }
        } else {
            if (!addSideCard(cardInfo)) {// || !checkLimit(cardInfo, false)) {
                mCardListAdapter.hideMenu(null);
            }
        }
    }

    @Override
    public void onSearchResult(List<Card> cardInfos, boolean isHide) {
        //super.onSearchResult(cardInfos, isHide);
        tv_result_count.setText(String.valueOf(cardInfos.size()));
        mCardListAdapter.set(cardInfos);
        mCardListAdapter.notifyDataSetChanged();
        if (cardInfos != null && cardInfos.size() > 0) {
            mListView.smoothScrollToPosition(0);
        }
        if (!isHide)
            showResult(false);
    }

    @Override
    public void onItemClick(View view, int pos) {
        if (isShowDrawer()) {
            return;
        }
        showDeckCard(view, pos);
    }

    @Override
    public void onItemLongClick(View view, int pos) {
    }

    @Override
    public void onItemDoubleClick(View view, int pos) {
        //拖拽中，就不显示
        if (isShowDrawer()) {
            return;
        }
        if (Constants.DECK_SINGLE_PRESS_DRAG) {
            showDeckCard(view, pos);
        }
    }

    private void showDeckCard(View view, int pos) {
        DeckItem deckItem = mDeckAdapater.getItem(pos);
        if (deckItem != null && deckItem.getCardInfo() != null) {
            showCardDialog(mDeckAdapater, deckItem.getCardInfo(), mDeckAdapater.getCardPosByView(pos));
        }
    }

    private boolean isShowDrawer() {
        return mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)
                || mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY);
    }

    private boolean isShowCard() {
        return mDialog != null && mDialog.isShowing();
    }

    /**
     * 显示卡片详情对话框
     *
     * @param provider 卡片列表提供者，用于获取当前卡片的上下文数据
     * @param cardInfo 要显示的卡片信息，如果为null则不执行任何操作
     * @param pos      当前卡片在列表中的位置
     */
    protected void showCardDialog(CardListProvider provider, Card cardInfo, int pos) {
        // 如果卡片信息为空，则不进行任何操作
        if (cardInfo != null) {
            // 如果卡片已经显示，则直接返回
            if (isShowCard()) return;

            // 初始化卡片详情视图（如果尚未初始化）
            if (mCardDetail == null) {
                mCardDetail = new CardDetail((BaseActivity) getActivity(), activity.getImageLoader(), activity.getStringManager());

                // 设置卡片点击监听器，处理各种卡片交互事件
                mCardDetail.setOnCardClickListener(new CardDetail.OnDeckManagerCardClickListener() {
                    @Override
                    public void onOpenUrl(Card cardInfo) {
                        WebActivity.openFAQ(getContext(), cardInfo);
                    }

                    @Override
                    public void onImageUpdate(Card cardInfo) {
                        mDeckAdapater.notifyItemChanged(cardInfo);
                        mCardListAdapter.notifyItemChanged(cardInfo);
                    }

                    @Override
                    public void onSearchKeyWord(String keyword) {
                        showSearchKeyWord(keyword);//根据关键词搜索
                    }

                    @Override
                    public void onShowCardList(List<Card> cardList, boolean sort) {
                        showCardList(cardList, sort);//卡包展示不排序，其他情况排序
                    }

                    @Override
                    public void onClose() {
                        mDialog.dismiss();
                    }

                    @Override
                    public void onAddSideCard(Card cardInfo) {
                        addSideCard(cardInfo);
                    }

                    @Override
                    public void onAddMainCard(Card cardInfo) {
                        addMainCard(cardInfo);
                    }
                });

                // 设置回调函数，当卡片收藏状态改变时触发
                mCardDetail.setCallBack((card, favorite) -> {
                    if (mCardSearcher.isShowFavorite()) {
                        mCardSearcher.showFavorites(false);
                    }
                });
            }

            // 显示添加按钮
            mCardDetail.showAdd();

            // 初始化对话框（如果尚未初始化）
            if (mDialog == null) {
                mDialog = new DialogPlus(getContext());
                mDialog.setView(mCardDetail.getView());
                mDialog.hideButton();
                mDialog.hideTitleBar();

                // 设置手势监听器，支持左右滑动切换卡片
                mDialog.setOnGestureListener(new AOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        if (isLeftFling(e1, e2, velocityX, velocityY)) {
                            mCardDetail.onNextCard();
                            return true;
                        } else if (isRightFling(e1, e2, velocityX, velocityY)) {
                            mCardDetail.onPreCard();
                            return true;
                        }
                        return super.onFling(e1, e2, velocityX, velocityY);
                    }
                });
            }

            // 如果对话框未显示，则显示它
            if (!mDialog.isShowing()) {
                mDialog.show();
            }

            // 绑定卡片数据到详情视图
            mCardDetail.bind(cardInfo, pos, provider);
        }
    }

    private void showSearchKeyWord(String keyword) {//使用此方法，可以适用关键词查询逻辑，让完全符合关键词的卡名置顶显示，并同时搜索字段和效果文本
        CardSearchInfo searchInfo = new CardSearchInfo.Builder().keyword(keyword).types(new long[]{}).build();//构建CardSearchInfo时type不能为null
        mCardLoader.search(searchInfo);
    }

    private void showCardList(List<Card> cardList, boolean sort) {
        if (!cardList.isEmpty()) {
            onSearchResult(sort ? mCardLoader.sort(cardList) : cardList, false);//根据情况不同，判断是否调用CardLoader的sort方法排序List<Card>
        } else {
            Log.w("cc", "No card found");
        }
    }

    /**
     * 添加到副卡组
     *
     * @param cardInfo 要添加的卡片信息
     * @return 添加成功返回true，添加失败返回false
     */
    private boolean addSideCard(Card cardInfo) {
        // 检查卡片添加限制
        if (checkLimit(cardInfo)) {
            // 执行添加卡片操作
            boolean rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.SideCard);
            if (rs) {
                YGOUtil.showTextToast(R.string.add_card_tip_ok);
                // 添加到历史记录
                addDeckToHistory(mDeckAdapater.getCurrentState());
            } else {
                YGOUtil.showTextToast(R.string.add_card_tip_fail);
            }
            return rs;
        }
        return false;
    }

    /**
     * 添加主卡组卡片
     *
     * @param cardInfo 要添加的卡片信息
     * @return 添加成功返回true，否则返回false
     */
    private boolean addMainCard(Card cardInfo) {
        // 检查卡片添加限制
        if (checkLimit(cardInfo)) {
            boolean rs;
            // 根据卡片类型选择添加位置
            if (cardInfo.isExtraCard()) {
                rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.ExtraCard);
            } else {
                rs = mDeckAdapater.AddCard(cardInfo, DeckItemType.MainCard);
            }
            // 处理添加结果
            if (rs) {
                YGOUtil.showTextToast(R.string.add_card_tip_ok);
                // 添加到历史记录
                addDeckToHistory(mDeckAdapater.getCurrentState());
            } else {
                YGOUtil.showTextToast(R.string.add_card_tip_fail);
            }
            return rs;
        }
        return false;
    }

    public void askBeforeQuit() {
        File ydk = mDeckAdapater.getYdkFile();
        if (ydk != null && ydk.exists()) {
            DialogPlus builder = new DialogPlus(getContext());
            builder.setTitle(R.string.question);
            builder.setMessage(R.string.quit_deck_tip);
            builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            builder.setLeftButtonListener((dlg, s) -> {
                getActivity().finish();
                dlg.dismiss();
            });
            builder.setRightButtonListener((dlg, s) -> {
                dlg.dismiss();
            });
            builder.show();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_RESULT_GRAVITY);
        } else if (mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)) {
            mDrawerLayout.closeDrawer(Constants.CARD_SEARCH_GRAVITY);
        } else if (mDeckAdapater.isChanged()) {
            askBeforeQuit();
        } else {
            //与home相同双击返回
            if (System.currentTimeMillis() - exitLasttime <= 3000) {
                return false;
            } else {
                exitLasttime = System.currentTimeMillis();
                YGOUtil.showTextToast(R.string.back_tip);
            }
        }
        return true;
    }

    private boolean checkLimit(Card cardInfo) {
        SparseArray<Integer> mCount = mDeckAdapater.getCardCount();
        LimitList limitList = mDeckAdapater.getLimitList();
        int id = cardInfo.getGameCode();
        Integer count = mCount.get(id);

        if (limitList == null) {
            return count != null && count <= 3;
        }

        if (limitList.check(cardInfo, LimitType.Forbidden)) {
            YGOUtil.showTextToast(getString(R.string.tip_card_max, 0));
            return false;
        }

        if (limitList.check(cardInfo, LimitType.GeneSys)) {
            // 检查GeneSys信用分限制
            if (limitList.getCredits() != null && limitList.getCreditLimits() != null) {
                // 获取当前卡片的信用分值
                Integer cardCreditValue = limitList.getCredits().get(cardInfo.Alias == 0 ? cardInfo.Code : cardInfo.Alias);

                if (cardCreditValue != null && cardCreditValue > 0) {
                    // 计算当前卡组中所有GeneSys卡片的信用分总和
                    int totalCredit = 0;
                    SparseArray<Integer> cardCounts = mDeckAdapater.getCardCount();

                    for (int i = 0; i < cardCounts.size(); i++) {
                        int cardId = cardCounts.keyAt(i);
                        int cardQuantity = cardCounts.valueAt(i);

                        // 检查这张卡是否是GeneSys卡
                        if (limitList.getCredits().containsKey(cardId)) {
                            Integer creditValue = limitList.getCredits().get(cardId);
                            if (creditValue != null) {
                                // 如果是当前要添加的卡片，需要考虑添加后的数量
                                if (cardId == cardInfo.Code || cardId == cardInfo.Alias) {
                                    totalCredit += creditValue * (cardQuantity + 1);
                                } else {
                                    totalCredit += creditValue * cardQuantity;
                                }
                            }
                        }
                    }

                    // 检查是否超过信用分上限
                    boolean overLimit = false;
                    for (Map.Entry<String, Integer> entry : limitList.getCreditLimits().entrySet()) {
                        Integer creditLimit = entry.getValue();

                        if (creditLimit != null && totalCredit > creditLimit) {
                            overLimit = true;
                            break;
                        }
                    }
                    if (overLimit) {
                        YGOUtil.showTextToast("超过总分上限:" + limitList.getCreditLimits().entrySet());
                        return false;
                    }
                }
            }
        }

        if (count != null) {
            if (limitList.check(cardInfo, LimitType.Limit)) {
                if (count >= 1) {
                    YGOUtil.showTextToast(getString(R.string.tip_card_max, 1));
                    return false;
                }
            } else if (limitList.check(cardInfo, LimitType.SemiLimit)) {
                if (count >= 2) {
                    YGOUtil.showTextToast(getString(R.string.tip_card_max, 2));
                    return false;
                }
            } else if (count >= Constants.CARD_MAX_COUNT) {
                YGOUtil.showTextToast(getString(R.string.tip_card_max, 3));
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (doMenu(item.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(item);

    }

    /**
     * 处理菜单项点击事件，根据传入的菜单ID执行对应的操作。
     *
     * @param menuId 菜单项ID，用于区分不同的菜单操作
     * @return 如果处理了该菜单项则返回true，否则返回false
     */
    public boolean doMenu(int menuId) {
        DialogPlus builder = new DialogPlus(getContext());
        switch (menuId) {
            case R.id.action_deck_backup_n_restore:
                // 启动权限请求页面
                mContext.startPermissionsActivity();
                // 显示备份与恢复卡组的选择对话框
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.deck_explain);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonText(R.string.deck_restore);
                builder.setRightButtonText(R.string.deck_back_up);
                builder.setRightButtonListener((dialog, which) -> {
                    dialog.dismiss();
                    doBackUpDeck(); // 执行卡组备份
                });
                builder.setLeftButtonListener((dialog, which) -> {
                    dialog.dismiss();
                    doRestoreDeck(); // 执行卡组恢复
                });
                builder.show();
                break;
            case R.id.action_search:
                // 弹出搜索条件对话框
                showSearch(true);
                break;
            case R.id.action_card_list:
                // 显示卡牌列表结果
                showResult(true);
                break;
            case R.id.action_share_deck:
                // 分享当前卡组
                if (mDeckAdapater.getYdkFile() == null) {
                    YGOUtil.showTextToast(R.string.unable_to_edit_empty_deck);
                    return true;
                }
                shareDeck();
                break;
            case R.id.action_save:
                // 保存卡组逻辑：判断是否为预加载文件并进行相应处理
                if (mPreLoadFile != null && mPreLoadFile == mDeckAdapater.getYdkFile()) {
                    // 预加载文件需要保存到deck文件夹
                    inputDeckName(mPreLoadFile, null, true);
                } else {
                    if (mDeckAdapater.getYdkFile() == null) {
                        inputDeckName(null, null, true);
                    } else {
                        if (TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getAiDeckDir()) ||
                                TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getPackDeckDir())) {
                            YGOUtil.showTextToast(R.string.donot_edit_Deck);
                        } else {
                            save(mDeckAdapater.getYdkFile()); // 保存卡组文件
                        }
                    }
                }
                break;
            case R.id.action_rename:
                // 重命名卡组
                if (mDeckAdapater.getYdkFile() == null) {
                    YGOUtil.showTextToast(R.string.unable_to_edit_empty_deck);
                    return true;
                }
                if (TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getAiDeckDir()) ||
                        TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getPackDeckDir())) {
                    YGOUtil.showTextToast(R.string.donot_edit_Deck);
                } else {
                    inputDeckName(mDeckAdapater.getYdkFile(), mDeckAdapater.getYdkFile().getParent(), false);
                }
                break;
            case R.id.action_deck_new:
                // 新建卡组
                createDeck(null);
                break;
            case R.id.action_clear_deck: {
                // 清空当前卡组内容
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.question_clear_deck);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonListener((dlg, rs) -> {
                    mDeckAdapater.setDeck(new DeckInfo(), false);
                    mDeckAdapater.notifyDataSetChanged();
                    dlg.dismiss();
                });
                builder.show();
            }
            break;
            case R.id.action_delete_deck: {
                // 删除当前卡组
                if (mDeckAdapater.getYdkFile() == null) {
                    YGOUtil.showTextToast(R.string.unable_to_edit_empty_deck);
                    return true;
                }
                if (TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getAiDeckDir()) ||
                        TextUtils.equals(mDeckAdapater.getYdkFile().getParent(), mSettings.getPackDeckDir())) {
                    YGOUtil.showTextToast(R.string.donot_edit_Deck);
                } else {
                    builder.setTitle(R.string.question);
                    builder.setMessage(R.string.question_delete_deck);
                    builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                    builder.setLeftButtonListener((dlg, rs) -> {
                        if (mDeckAdapater.getYdkFile() != null) {
                            // 先删除在线卡组
                            List<DeckFile> deckFileList = new ArrayList<>();
                            deckFileList.add(new DeckFile(mDeckAdapater.getYdkFile()));
                            DeckSquareApiUtil.deleteDecks(deckFileList);

                            // 再删除本地文件
                            FileUtils.deleteFile(mDeckAdapater.getYdkFile());
                            YGOUtil.showTextToast(R.string.done);
                            dlg.dismiss();
                            File file = getFirstYdk();
                            loadDeckFromFile(file);
                        }
                    });
                    builder.show();
                }
            }
            break;
            case R.id.action_unsort:
                // 打乱卡组顺序
                mDeckAdapater.unSort();
                break;
            case R.id.action_sort:
                // 对卡组进行排序
                mDeckAdapater.sort();
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * 创建新的卡组文件
     *
     * @param savePath 新卡组文件的保存路径
     */
    private void createDeck(String savePath) {
        final File old = mDeckAdapater.getYdkFile();
        DialogPlus builder = new DialogPlus(getContext());
        builder.setTitle(R.string.question);
        builder.setMessage(R.string.question_keep_cur_deck);
        builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);

        // 设置左按钮监听器 - 选择保留当前卡组
        builder.setLeftButtonListener((dlg, rs) -> {
            dlg.dismiss();
            //复制当前卡组
            inputDeckName(old, savePath, true);
        });

        // 设置右按钮监听器 - 不保留当前卡组
        builder.setRightButtonListener((dlg, rs) -> {
            dlg.dismiss();
            setCurDeck(null, false);
            inputDeckName(null, savePath, true);
        });

        // 设置关闭监听器 - 当对话框关闭时的处理
        builder.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
            setCurDeck(null, false);
            inputDeckName(null, savePath, true);
        });
        builder.show();
    }

    private File getFirstYdk() {
        List<File> files = getYdkFiles();
        return files == null || files.size() == 0 ? null : files.get(0);
    }

    private void shareDeck() {
        builderShareLoading = new DialogPlus(getContext());
        builderShareLoading.showProgressBar();
        builderShareLoading.hideTitleBar();
        builderShareLoading.setMessage(R.string.Pre_share);
        builderShareLoading.show();

        //先排序
//        mDeckAdapater.sort();
        //保存
//        if (mPreLoadFile != null && mPreLoadFile == mDeckAdapater.getYdkFile()) {
//            //需要保存到deck文件夹
//            inputDeckName(mPreLoadFile, null, true);
//        } else {
//            if (mDeckAdapater.getYdkFile() == null) {
//                inputDeckName(null, null, true);
//            } else {
//                save(mDeckAdapater.getYdkFile());
//            }
//        }
//        //保存成功后重新加载卡组
//        File file = getSelectDeck(mDeckSpinner);
//        if (file != null) {
//            loadDeckFromFile(file);
//        }
        Deck deck = mDeckAdapater.toDeck(mDeckAdapater.getYdkFile());
        if (deck.getDeckCount() == 0) {
            builderShareLoading.dismiss();
            YGOUtil.showTextToast("卡组中没有卡片");
            return;
        }

        //延时半秒，使整体看起来更流畅
        new Handler().postDelayed(() -> shareDeck1(deck), 500);
    }

    private void shareDeck1(Deck deck) {
        //开启绘图缓存
        mRecyclerView.setDrawingCacheEnabled(true);
        //这个方法可调可不调，因为在getDrawingCache()里会自动判断有没有缓存有没有准备好，
        //如果没有，会自动调用buildDrawingCache()
        mRecyclerView.buildDrawingCache();
        //获取绘图缓存 这里直接创建了一个新的bitmap
        //因为我们在最后需要释放缓存资源，会释放掉缓存中创建的bitmap对象
        Bitmap bitmap = BitmapUtil.drawBg4Bitmap(Color.parseColor("#e6f3fd"), Bitmap.createBitmap(mRecyclerView.getDrawingCache(), 0, 0, mRecyclerView.getMeasuredWidth(),
                mRecyclerView.getMeasuredHeight()));

        //清理绘图缓存，释放资源
        mRecyclerView.destroyDrawingCache();
//        shotRecyclerView(mRecyclerView)


        String deckName = deck.getName();
        int end = deckName.lastIndexOf(".");
        if (end != -1) {
            deckName = deckName.substring(0, end);
        }
        String savePath = new File(AppsSettings.get().getDeckSharePath(), deckName + ".jpg").getAbsolutePath();
        BitmapUtil.saveBitmap(bitmap, savePath, 50);
        builderShareLoading.dismiss();
        DialogUtils du = DialogUtils.getdx(getContext());
        View viewDialog = du.dialogBottomSheet(R.layout.dialog_deck_share, true);
        ImageView iv_image = viewDialog.findViewById(R.id.iv_image);
        Button bt_image_share = viewDialog.findViewById(R.id.bt_image_share);
        Button bt_code_share = viewDialog.findViewById(R.id.bt_code_share);
        TextView tv_code = viewDialog.findViewById(R.id.et_code);
        tv_code.setText(deck.toUri().toString());
        ImageUtil.show(getContext(), savePath, iv_image, System.currentTimeMillis() + "");

        bt_code_share.setOnClickListener(v -> {
            du.dis();
            YGOUtil.copyMessage(getContext(), tv_code.getText().toString().trim());
            DuelAssistantManagement.getInstance().setLastMessage(tv_code.getText().toString().trim());
            YGOUtil.showTextToast(R.string.deck_text_copyed);
        });

        bt_image_share.setOnClickListener(v -> {
            du.dis();
            String category = mDeckAdapater.getYdkFile().getParent();
            String fname = deck.getName();
            Intent intent = new Intent(ACTION_SHARE_FILE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(IrrlichtBridge.EXTRA_SHARE_TYPE, "ydk");
            if (TextUtils.equals(category, mSettings.getDeckDir())) {
                intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, fname);
            } else {
                String cname = DeckUtil.getDeckTypeName(mDeckAdapater.getYdkFile().getAbsolutePath());
                intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, cname + "/" + fname);
            }
            intent.setPackage(getActivity().getPackageName());
            try {
                startActivity(intent);
            } catch (Throwable e) {
                YGOUtil.showTextToast("dev error:not found activity." + e);
            }
        });


        //复制前关闭决斗助手


//        String label = TextUtils.isEmpty(deck.getName()) ? getString(R.string.share_deck) : deck.getName();
//        final String uriString = deck.toAppUri().toString();
//        final String httpUri = deck.toHttpUri().toString();
//        shareUrl(uriString, label);
    }

    private void shareUrl(String uri, String label) {
        String url = getString(R.string.deck_share_head) + "  " + uri;
        ShareUtil.shareText(getContext(), getString(R.string.share_deck), url, null);
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(CLIPBOARD_SERVICE);
        if (Build.VERSION.SDK_INT > 19) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(label, uri));
        } else {
            clipboardManager.setText(uri);
        }
        YGOUtil.showTextToast(R.string.copy_to_clipbroad);
    }

    private File getSelectDeck(Spinner spinner) {
        Object o = SimpleSpinnerAdapter.getSelectTag(spinner);
        if (o instanceof File) {
            return (File) o;
        }
        return null;
    }

    /**
     * 从存储卡组的文件夹中获取所有名字以.ydk结尾的文件，并将mPreLoadFile也加入到返回结果中
     *
     * @return 包含所有.ydk文件的文件列表，如果目录不存在或无法访问则返回null
     */
    private List<File> getYdkFiles() {
        // 获取卡组文件夹路径并列出所有.ydk文件
        File dir = new File(mSettings.getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> s.toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX));
        if (files != null) {
            List<File> list = new ArrayList<>(Arrays.asList(files));
            // 检查并添加预加载文件（如果存在且不在当前列表中）
            if (mPreLoadFile != null && mPreLoadFile.exists()) {
                boolean hasCur = false;
                for (File f : list) {
                    if (TextUtils.equals(f.getAbsolutePath(), mPreLoadFile.getAbsolutePath())) {
                        hasCur = true;
                        break;
                    }
                }
                if (!hasCur) {
                    list.add(mPreLoadFile);
                }
            }
            return list;
        }
        return null;
    }

    /**
     * 初始化限制列表下拉框
     *
     * @param spinner 要初始化的Spinner控件
     * @param cur     当前选中的限制列表对象，用于设置默认选中项
     */
    private void initLimitListSpinners(Spinner spinner, LimitList cur) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        List<String> limitLists = activity.getmLimitManager().getLimitNames();
        int index = -1;
        int count = activity.getmLimitManager().getCount();

        // 添加默认选项
        items.add(new SimpleSpinnerItem(0, getString(R.string.label_limitlist)));

        // 遍历所有限制列表，构建下拉项并查找当前选中项的索引
        for (int i = 0; i < count; i++) {
            int j = i + 1;
            String name = limitLists.get(i);
            items.add(new SimpleSpinnerItem(j, name));
            if (cur != null && TextUtils.equals(cur.getName(), name)) {
                index = j;
            }
        }

        // 设置适配器
        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(getContext());
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setAdapter(adapter);

        // 设置默认选中项
        if (index >= 0) {
            spinner.setSelection(index);
        }

        // 设置选择监听器
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setLimitList(activity.getmLimitManager().getLimit(SimpleSpinnerAdapter.getSelectText(spinner)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    /**
     * 设置限制列表并更新相关适配器
     *
     * @param limitList 限制列表对象，如果为null则直接返回
     */
    private void setLimitList(LimitList limitList) {
        if (limitList == null) return;

        // 检查新的限制列表是否与当前列表相同
        LimitList last = mDeckAdapater.getLimitList();
        boolean nochanged = last != null && TextUtils.equals(last.getName(), limitList.getName());

        // 如果限制列表发生变化，则更新牌组适配器并通知数据变更
        if (!nochanged) {
            mDeckAdapater.setLimitList(limitList);
            getActivity().runOnUiThread(() -> {
                mDeckAdapater.notifyItemRangeChanged(DeckItem.MainStart, DeckItem.MainEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.ExtraStart, DeckItem.ExtraEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.SideStart, DeckItem.SideEnd);
            });
        }

        // 更新卡片列表适配器的限制列表并通知数据变更
        mCardListAdapter.setLimitList(limitList);
        getActivity().runOnUiThread(() -> mCardListAdapter.notifyDataSetChanged());
    }

    private void inputDeckName(File oldYdk, String savePath, boolean keepOld) {
        DialogPlus builder = new DialogPlus(getContext());
        builder.setTitle(R.string.intpu_name);
        EditText editText = new EditText(getContext());
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setSingleLine();
        if (oldYdk != null) {
            editText.setText(oldYdk.getName());
        }
        builder.setContentView(editText);
        builder.setOnCloseLinster(DialogInterface::dismiss);
        builder.setLeftButtonListener((dlg, s) -> {
            CharSequence name = editText.getText();
            if (!TextUtils.isEmpty(name)) {
                String filename = String.valueOf(name);
                if (!filename.endsWith(YDK_FILE_EX)) {
                    filename += YDK_FILE_EX;
                }
                File ydk;
                if (TextUtils.isEmpty(savePath))
                    ydk = new File(mSettings.getDeckDir(), filename);
                else
                    ydk = new File(savePath, filename);
                if (ydk.exists()) {
                    YGOUtil.showTextToast(R.string.file_exist);
                    return;
                }
                if (!keepOld && oldYdk != null && oldYdk.exists()) {
                    if (oldYdk.renameTo(ydk)) {
                        dlg.dismiss();
                        loadDeckFromFile(ydk);
                    }
                } else {
                    if (oldYdk == mPreLoadFile) {
                        mPreLoadFile = null;
                    }
                    dlg.dismiss();
                    try {
                        boolean ret = ydk.createNewFile();
                    } catch (Throwable ignore) {
                    }
                    //新建卡组保留卡片不保留卡组id
                    save(ydk, true);
                    loadDeckFromFile(ydk);
                }
            } else {
                dlg.dismiss();
            }
        });
        builder.show();
    }

    private void save(File ydk, boolean withoutId) {
        YGOUtil.showTextToast(mDeckAdapater.save(ydk, withoutId) ? R.string.save_tip_ok : R.string.save_tip_fail);
    }

    private void save(File ydk) {
        save(ydk, false);
    }

    private void initBoomMenuButton(BoomMenuButton menu) {
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

    private void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, int stringId, int image) {
        addMenuButton(mMenuIds, menuButton, menuId, getString(stringId), image);
    }

    private void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, String str, int image) {
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
    private LayerDrawable createDrawableWithBackground(int imageRes) {
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

    private void animRotate(View view) {
        // 获取背景并启动旋转动画
        LayerDrawable layerDrawable = (LayerDrawable) view.getBackground();
        RotateDrawable rotateDrawable = (RotateDrawable) layerDrawable.findDrawableByLayerId(R.id.background_layer);

        // 使用属性动画控制旋转
        ObjectAnimator animator = ObjectAnimator.ofInt(rotateDrawable, "level", 0, 10000);
        animator.setDuration(25000);//控制旋转速度
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.start();
    }

    private void doBackUpDeck() {
        FileUtils.delFile(ORI_DECK);//备份前删除原备份
        try {
            FileUtils.copyDir(mSettings.getDeckDir(), ORI_DECK, true);
            File ydks = new File(ORI_DECK);
            File[] subYdks = ydks.listFiles();
            for (File files : subYdks) {
                if (files.getName().contains("-") && files.getName().contains(" new cards"))
                    files.delete();
            }
        } catch (Throwable e) {
            YGOUtil.showTextToast(e + "");
        }
        YGOUtil.showTextToast(R.string.done);
    }

    private void doRestoreDeck() {
        try {
            FileUtils.copyDir(ORI_DECK, mSettings.getDeckDir(), false);
        } catch (Throwable e) {
            YGOUtil.showTextToast(e + "");
        }
        YGOUtil.showTextToast(R.string.done);
    }

    //在卡组选择的dialog中点击某个卡组（来自本地或服务器）后，dialog通过本回调函数通知本页面。
    //在本页面中根据卡组来源（本地或服务器）显示卡组内容
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
                    boolean result = DeckSquareFileUtil.saveFileToPath(dir.getPath(), fileFullName, deckData.getDeckYdk(), deckData.getDeckUpdateDate());
                    if (result) {//存储成功，使用预加载功能
                        LogUtil.i(TAG, "square deck detail done");
                        preLoadFile(dir.getPath() + "/" + fileFullName);
                        tv_add_1.setText(R.string.like_deck_thumb);
                        ll_click_like.setVisibility(View.VISIBLE);
                    }
                }
            });

        } else {
            Log.d("seesee", deckFile.getPathFile().getAbsolutePath());
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
            LogUtil.w(TAG, "要删除的卡组：" + "\n卡组分类: " + deckFile.getTypeName() + "\n卡组名称：" + deckFile.getFileName() + "\n卡组id: " + deckFile.getDeckId());
            if (TextUtils.equals(deckFile.getPath(), currentDeckPath)) {
                List<File> files = getYdkFiles();
                File file = null;
                if (files != null && files.size() > 0) {
                    file = files.get(0);
                }
                if (file != null) {
                    loadDeckFromFile(file);
                } else {
                    setCurDeck(new DeckInfo(), false);
                }
                // 对于当前卡组，也应该删除在线卡组
                DeckSquareApiUtil.deleteDecks(Arrays.asList(deckFile));
                return;
            }
        }
        // 删除在线的同名卡组们
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
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.deck_menu), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener((view, controller) -> {
                                        //可只创建一个引导layout并把相关内容都放在其中并GONE，获得ID并初始化相应为显示
                                        view.findViewById(R.id.view_abt_bmb).setVisibility(View.VISIBLE);
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
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
                                    .setBackgroundColor(0xbc000000)
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
                                    .setBackgroundColor(0xbc000000)
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
                                    .setBackgroundColor(0xbc000000)
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

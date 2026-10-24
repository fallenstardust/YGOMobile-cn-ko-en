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
 * DeckManagerFragment 拆分继承链的卡组加载与撤销历史层。
 *
 * <p>承载卡组文件加载（loadDeckFromFile/init）、当前卡组设置（setCurDeck）、
 * 撤销/重做历史记录与信用分计数刷新等功能集群。拆分仅为控制单类规模，
 * 所有方法实现与原文件逐行一致。</p>
 */
public abstract class DeckManagerFragmentDeck extends DeckManagerFragmentBase {
    //region init

    /**
     * 从指定文件中加载卡牌包数据
     *
     * @param file 包含卡牌数据的文件对象，如果文件不存在或为null则会设置空的卡牌包
     */
    protected void loadDeckFromFile(File file) {
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
                return mDeckAdapater.read(mCardLoader, file, mSettings.getGenesysMode() == 1 ? mCardLoader.getGenesysLimitList() : mCardLoader.getLimitList());
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
            if (activity.getmLimitManager().getCount() > 0 || activity.getmLimitManager().getGenesysCount() > 0) {
                mCardLoader.setLimitList(mSettings.getGenesysMode() == 1 ? activity.getmLimitManager().getLastGenesysLimit() : activity.getmLimitManager().getLastLimit());
            }

            // 处理卡组文件加载逻辑
            File file = ydk;
            if (file == null || !file.exists()) {
                // 当默认卡组不存在的时候，尝试获取其他可用的ydk文件
                List<File> files = getYdkFiles();
                if (files != null && !files.isEmpty()) {
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
                return mDeckAdapater.read(mCardLoader, file, mSettings.getGenesysMode() == 1 ? mCardLoader.getGenesysLimitList() : mCardLoader.getLimitList());
            } else {
                return new DeckInfo();
            }
        }).done((rs) -> {
            // 初始化完成后的处理逻辑
            isLoad = true;
            dlg.dismiss();
            // 初始化卡片搜索器中的项目列表,为卡片搜索功能准备基础数据
            mCardSearcher.initItems();
            // 根据资源路径判断是否进入卡包展示模式
            if (rs != null && rs.source != null) {
                String parentPath = rs.source.getParent();
                isPackMode = parentPath.equals(mSettings.getPackDeckDir()) || parentPath.equals(mSettings.getCacheDeckDir());
            } else {
                isPackMode = false;
            }
            // 设置当前卡组显示
            setCurDeck(rs, isPackMode);

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
    protected void setCurDeck(DeckInfo deckInfo, boolean isPack) {
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

        // 更新按钮状态，初始化完毕后再执行，以便确保所有数据已加载
        updateUndoRedoButtons();
    }

    /**
     * 清空卡组历史记录
     */
    protected void clearDeckHistory() {
        deckHistory.clear();
        historyIndex = -1;
    }

    /**
     * 将当前卡组状态添加到历史记录中（仅当与上一个状态不同时）
     *
     * @param deckInfo 当前卡组状态
     */
    protected void addDeckToHistory(DeckInfo deckInfo) {
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
    protected void undo() {
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
    protected void redo() {
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
    protected boolean canUndo() {
        return historyIndex > 0;
    }

    /**
     * 检查是否可以重做
     *
     * @return 如果可以重做返回true，否则返回false
     */
    protected boolean canRedo() {
        return historyIndex < deckHistory.size() - 1;
    }

    /**
     * 更新撤销/重做按钮的状态和可见性
     */
    protected void updateUndoRedoButtons() {
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
        //同时刷新信用分总计
        refreshDeckCreditCount();
    }

    protected void updateUndoRedoButtonVisibility() {
        if (btnUndo == null || btnRedo == null) return;
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)
                || mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY);
        if (drawerOpen) {
            btnUndo.setVisibility(View.INVISIBLE);
            btnRedo.setVisibility(View.INVISIBLE);
        } else {
            // 抽屉关闭时，根据原有逻辑恢复按钮状态
            if (deckHistory.size() > 1 && !isPackMode) {
                btnUndo.setVisibility(canUndo() ? View.VISIBLE : View.INVISIBLE);
                btnRedo.setVisibility(canRedo() ? View.VISIBLE : View.INVISIBLE);
            } else {
                btnUndo.setVisibility(View.INVISIBLE);
                btnRedo.setVisibility(View.INVISIBLE);
            }
        }
    }


    /**
     * 刷新卡组信用分显示
     * <p>
     * 该方法用于更新界面上的信用分相关显示，包括当前信用分、信用分限制和剩余信用分。
     * 根据当前信用分是否超过限制来设置相应的文本颜色。
     */
    protected void refreshDeckCreditCount() {
        LimitList limitList = mDeckAdapater.getLimitList();
        int currentCredit = 0;
        int creditLimit = 0;

        // 禁卡表为空值以防止空指针异常
        if (limitList == null) {
            // 如果limitList为null，隐藏计分板并返回
            if (ll_genesys_scoreboard != null) {
                ll_genesys_scoreboard.setVisibility(View.GONE);
            }
            return;
        }
        if (limitList.getCreditLimits() != null && limitList.getCreditLimits() > 0) {
            // 根据当前主卡组数量即时计算实际生效的积分上限（配合"$__extra_score__"，取整显示）
            DeckInfo currentState = mDeckAdapater.getCurrentState();
            int mainCount = currentState != null ? currentState.getMainCount() : 0;
            Integer effectiveLimit = limitList.getEffectiveCreditLimit(mainCount);
            creditLimit = effectiveLimit != null ? effectiveLimit : limitList.getCreditLimits();
            currentCredit = getCreditCount(mDeckAdapater.getCurrentState());

            // 当当前点数超过限制时，设置文本为红色
            if (currentCredit > creditLimit) {
                tv_credit_count.setTextColor(Color.RED);
                tv_credit_remain.setTextColor(Color.RED);
            } else {
                // 否则使用默认颜色
                tv_credit_count.setTextColor(Color.WHITE);
                tv_credit_remain.setTextColor(Color.WHITE);
            }

        }

        // 更新信用分相关文本显示
        tv_credit_count.setText(String.valueOf(currentCredit));
        tv_credit_limit.setText(String.valueOf(creditLimit));
        tv_credit_remain.setText(String.valueOf(creditLimit - currentCredit));
    }


    /**
     * 判断指定文件是否位于卡牌目录中
     *
     * @param file 要检查的文件对象
     * @return 如果文件的父目录等于卡牌目录则返回true，否则返回false
     */
    protected boolean inDeckDir(File file) {
        // 获取卡牌目录的绝对路径
        String deck = new File(AppsSettings.get().getDeckDir()).getAbsolutePath();
        // 比较文件父目录与卡牌目录是否相同
        return TextUtils.equals(deck, file.getParent());
    }

}

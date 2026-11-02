package cn.garymb.ygomobile.ui.cards;

import static android.content.Context.CLIPBOARD_SERVICE;
import static cn.garymb.ygomobile.Constants.ORI_DECK;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;
import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_SHARE_FILE;

import android.animation.ObjectAnimator;
import android.app.Dialog;
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
import android.view.KeyEvent;
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
import cn.garymb.ygomobile.ui.cards.deck.DeckHistory;
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
        // 发起一次载入就推一次序号：异步回调只能在序号未变时落地（防旧读盘结果覆盖对端同步）
        final int seq = ++deckLoadSeq;
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
            if (seq != deckLoadSeq) {
                // 期间又发生过一次卡组载入（典型为对端切换组卡的联动跟随），本次旧结果作废
                return;
            }
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
        final int seq = ++deckLoadSeq;
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
            if (seq != deckLoadSeq) {
                // 期间发生过更新的卡组载入（如对端联动），本次旧结果不再落地
                return;
            }
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

        // 先取共享会话里属于这份卡组的内容（必须在换户前取：bindDeck 在 key 变化时会清零历史），
        // 再按当前卡组文件换户（切到另一个 ydk 即整段清零并通知对端跟随）
        String path = file != null ? file.getAbsolutePath() : "";
        // 只读（人机/AI）与卡包展示/缓存卡组不入会话（口径与编辑器侧共用 DeckHistory）：
        // 否则“页内看一眼卡包”会换户清掉自己那份卡组的未保存历史，并把决斗内编辑器的页头抢走
        boolean inScope = !isPack && !DeckHistory.isOutsideSyncScope(path);
        DeckInfo shared = inScope && path.equals(deckHistory.getDeckKey())
                ? deckHistory.current() : null;
        if (inScope) {
            deckHistory.bindDeck(path, DeckHistory.PUBLISHER_DECK_PAGE);
        }
        if (shared != null && !deckHistory.currentMatches(deckInfo)) {
            // 另一编辑器（或本页上一轮会话）已有更新的未保存内容：直接加载到最后修改状态，
            // 也不把刚读到的磁盘版本写进历史，否则会截掉对端的修改链
            mDeckAdapater.applySharedDeck(shared);
            updateUndoRedoButtons();
            return;
        }

        // 记录初始快照（内部去重）
        addDeckToHistory(deckInfo);

        // 更新按钮状态，初始化完毕后再执行，以便确保所有数据已加载
        updateUndoRedoButtons();
    }

    /**
     * 将当前卡组状态记入撤销历史（去重与截尾规则见 {@code DeckHistory#push}）。
     * <p>
     * 写入前先确认会话归属：若对端已切到另一份卡组而本页未能跟上（如对端卡组文件已被
     * 删除或移动，本页保持原样），直接 push 会把本页这份卡组的内容写进对方的时间线并广播
     * 过去，造成跨卡组污染；因此由本页重新换户回自己的卡组，让两边停在同一份。
     * （DeckHistory 内部已按游标内容去重，回换后本页随后的 push 不会重复新增快照。）
     */
    protected void addDeckToHistory(DeckInfo deckInfo) {
        if (deckInfo == null) return;
        File ydkFile = mDeckAdapater.getYdkFile();
        String path = ydkFile != null ? ydkFile.getAbsolutePath() : "";
        // 只读（人机/AI）与卡包展示/缓存卡组不入会话（口径与编辑器侧共用 DeckHistory）：
        // 否则“页内看一眼卡包”会换户清掉自己那份卡组的未保存历史，并把决斗内编辑器的页头抢走
        if (DeckHistory.isOutsideSyncScope(path)) return;
        if (!path.equals(deckHistory.getDeckKey())) {
            deckHistory.bindDeck(path, DeckHistory.PUBLISHER_DECK_PAGE);
        }
        deckHistory.push(deckInfo, DeckHistory.PUBLISHER_DECK_PAGE);
        // 更新按钮状态
        updateUndoRedoButtons();
    }

    /**
     * 本页有未保存修改（isChanged：当前 mItems 与 ydk 文件的 md5 不一致）时先弹
     * “此操作将放弃对当前卡组的修改，是否继续？”（DialogPlus，左键确定/右键取消）：
     * <b>只有确定才执行 onConfirm</b>，取消/返回键/点窗外空白/点标题栏关闭叉一律只关掉
     * 本确认窗（走 onDismissed），卡组选择对话框保持展开，让用户接着挑。
     * <p>
     * 只给发起侧的用户动作入口用：本页三处选卡组入口（本地卡组、卡组广场的卡组、长按
     * 在线备份的卡组）均汇到 DeckManagerFragment#onDeckSelect，在那里问一次即可，
     * 并将对话框的收起时机一起交给本方法，确认窗才能弹在它上面而不是“先收再问”。
     * 对端广播过来的联动切换不走这里：两套编辑器只有一份会话，发起侧 bindDeck 换户时
     * 上一份卡组的时间线已被清空，跟随侧再问只会把同一句话问两遍。
     */
    protected void confirmDiscardThen(Runnable onConfirm, Runnable onDismissed) {
        if (mDeckAdapater == null || !mDeckAdapater.isChanged()) {
            onConfirm.run();
            return;
        }
        // 拿不到 context 就弹不出确认窗：不加载卡组，但要还原被这次点击预先改掉的选中高亮
        if (getContext() == null) {
            onDismissed.run();
            return;
        }
        final DialogPlus dialog = new DialogPlus(getContext());
        dialog.setTitle(R.string.question);
        dialog.setMessage(getString(R.string.deck_discard_switch_tip));
        dialog.setMessageGravity(Gravity.CENTER_HORIZONTAL);
        dialog.setLeftButtonListener((dlg, v) -> {
            dlg.dismiss();
            onConfirm.run();
        });
        // 取消：只关掉确认窗，不执行任何换卡组动作，也不收起卡组选择对话框
        dialog.setRightButtonListener((dlg, v) -> {
            dlg.dismiss();
            onDismissed.run();
        });
        // 标题栏的关闭叉：与取消同义（DialogPlus 默认只 dismiss，这里补上还原高亮）
        dialog.setOnCloseLinster(dlg -> {
            dlg.dismiss();
            onDismissed.run();
        });
        // 返回键必须由确认窗自己吃掉：否则它会落到下层仍处于展开态的卡组选择对话框窗口，
        // 触发 DeckManageDialog.onCreateDialog 里重写的 onBackPressed → onDismiss()，
        // 把选择窗一并收掉（用户反馈的“按返回键还是收起了”即此路径）
        dialog.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dialog.dismiss();
                onDismissed.run();
            }
            return true;
        });
        // 点确认窗以外的空白处：与取消同义——只关掉确认窗，不加载也不收选择窗。
        // DialogPlus 把 onTouchEvent 整条交给自己的 GestureDetector、不走 Dialog 原生的
        // outside-touch 收起，所以只能经这个手势钩子做。
        dialog.setOnGestureListener(new AOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (isInsideConfirmCard(dialog, e)) {
                    //落在卡片内（非按钮的文本/背景区）：不算“点空白处”，什么都不做
                    return false;
                }
                dialog.dismiss();
                onDismissed.run();
                return true;
            }
        });
        dialog.show();
    }

    /**
     * 未确认就关掉确认窗时，把卡组管理对话框里本地卡组列表的高亮还原到“当前真正在显示的那份卡组”。
     * <p>
     * 列表适配器在点击那一刻就把选中位改到了被点的 item 上（先选后通知），而本次换卡组被取消、
     * 并没有加载它，高亮不能停在那份卡组上。refreshLocalDeckSelection 内部按 getLastDeckPath
     * （= 当前已显示卡组）重算选中位，与“重新打开对话框时的高亮”同源；当前若是卡包展示/在线
     * 卡组（本地列表无匹配项）则返回 -1，即清掉残留高亮。
     */
    protected void restoreDeckManageSelection() {
        if (!isAdded()) {
            return;
        }
        Fragment fragment = requireActivity().getSupportFragmentManager()
                .findFragmentByTag(DeckManageDialog.DIALOG_TAG);
        if (fragment instanceof DeckManageDialog) {
            ((DeckManageDialog) fragment).refreshLocalDeckSelection();
        }
    }

    /**
     * 本次单击是否落在确认窗的内容卡片内。
     * <p>
     * 取的是 dialog_plus_base 的根布局（带 sdialogl 背景那张卡片），它是 android.R.id.content
     * 的唯一子 View：不能直接用 content，因为主题若为非浮动（全屏透明）窗时 content 会铺满整屏，
     * 那样任何单击都被判成“在卡片内”，点空白永远关不掉确认窗。取它在窗口内的矩形与单击坐标比对。
     */
    private boolean isInsideConfirmCard(Dialog dialog, MotionEvent event) {
        View content = dialog.findViewById(android.R.id.content);
        View card = content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0
                ? ((ViewGroup) content).getChildAt(0) : content;
        if (card == null || card.getWidth() == 0) {
            //拿不到卡片矩形时偏保守：当成点在卡片内，不做任何收起
            return true;
        }
        int[] loc = new int[2];
        card.getLocationInWindow(loc);
        return event.getX() >= loc[0] && event.getX() <= loc[0] + card.getWidth()
                && event.getY() >= loc[1] && event.getY() <= loc[1] + card.getHeight();
    }

    /**
     * 跟随决斗内卡组编辑器切换到同一份卡组：静默加载，不重复弹“放弃修改”确认。
     * <p>
     * 两套编辑器只有一份会话：对端 bindDeck 换户时上一份卡组的时间线已被整段清空，
     * 本页再问一次也防不住任何数据（要保留必须在切换前保存），只会让用户在 MainActivity
     * 里看到一个与决斗内编辑器完全相同的询问（且本页在后台，弹窗要等到回到主页才显形，
     * 看上去就像退出编辑后又问一遍）。本页自己的选卡组入口历史上也是直接换组，语义一致。
     */
    @Override
    void onRemoteDeckSwitch(String path) {
        if (mDeckAdapater == null || path == null || path.isEmpty()) {
            return;
        }
        File current = mDeckAdapater.getYdkFile();
        if (current != null && path.equals(current.getAbsolutePath())) {
            return;
        }
        File target = new File(path);
        if (!target.exists()) {
            //对端卡组文件已被删除或移动：保持现状
            return;
        }
        loadDeckFromFile(target);
    }

    /**
     * 跟随决斗内卡组编辑器对本份卡组的修改：以共享会话游标处的内容重建本页卡组网格。
     * 两边的 path 不一致（本页停在只读/卡包展示卡组，或对端已换组而本次载入尚未落地）时保持不动。
     */
    @Override
    void onRemoteDeckContent() {
        if (mDeckAdapater == null) {
            return;
        }
        File current = mDeckAdapater.getYdkFile();
        if (current == null
                || !current.getAbsolutePath().equals(deckHistory.getDeckKey())) {
            return;
        }
        DeckInfo shared = deckHistory.current();
        if (shared == null || deckHistory.currentMatches(mDeckAdapater.getCurrentState())) {
            return;
        }
        mDeckAdapater.applySharedDeck(shared);
        updateUndoRedoButtons();
    }

    /**
     * 撤销操作：回到上一个快照（仅回退内存与界面，不写 ydk）
     */
    protected void undo() {
        // 只读分类（人机卡组/卡包展示）不入会话：历史里那条快照可能还属于上一份可编辑卡组，
        // 在这里回退会把别人的内容灌进这份只读卡组的视图（与按钮可见性口径保持一致）
        if (!isDeckEditable()) {
            return;
        }
        DeckInfo previousState = deckHistory.undo();
        if (previousState == null) {
            return;
        }
        mDeckAdapater.setDeck(previousState, false);
        mDeckAdapater.notifyDataSetChanged();
        // 更新按钮状态
        updateUndoRedoButtons();
    }

    /**
     * 重做操作：前进到下一个快照
     */
    protected void redo() {
        // 同 undo：只读分类不得采纳属于别份卡组的历史游标
        if (!isDeckEditable()) {
            return;
        }
        DeckInfo nextState = deckHistory.redo();
        if (nextState == null) {
            return;
        }
        mDeckAdapater.setDeck(nextState, false);
        mDeckAdapater.notifyDataSetChanged();
        // 更新按钮状态
        updateUndoRedoButtons();
    }

    /**
     * 检查是否可以撤销
     *
     * @return 如果可以撤销返回true，否则返回false
     */
    protected boolean canUndo() {
        return deckHistory.canUndo();
    }

    /**
     * 检查是否可以重做
     *
     * @return 如果可以重做返回true，否则返回false
     */
    protected boolean canRedo() {
        return deckHistory.canRedo();
    }

    /**
     * 更新撤销/重做按钮的状态和可见性
     */
    protected void updateUndoRedoButtons() {
        //垃圾桶键与 undo/redo 同位互斥：批量模式只亮垃圾桶
        applyBatchDeleteButtonVisibility();
        boolean batchMode = mDeckBatchController != null && mDeckBatchController.isBatchMode();
        if (btnUndo != null && btnRedo != null) {
            if (batchMode) {
                //批量模式下本行留给垃圾桶，undo/redo 不占位也不可点
                btnUndo.setVisibility(View.GONE);
                btnRedo.setVisibility(View.GONE);
            } else if (deckHistory.size() > 1 && isDeckEditable()) {
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
                // 历史记录小于等于1，或当前是不可编辑的只读卡组（人机/卡包展示）时隐藏按钮
                btnUndo.setVisibility(View.INVISIBLE);
                btnRedo.setVisibility(View.INVISIBLE);
            }
        }
        //同时刷新信用分总计
        refreshDeckCreditCount();
    }

    protected void updateUndoRedoButtonVisibility() {
        //垃圾桶键仅在批量模式可见
        applyBatchDeleteButtonVisibility();
        if (btnUndo == null || btnRedo == null) return;
        // 批量勾选模式：本行改为只亮垃圾桶，undo/redo 直接不可见不可点
        if (mDeckBatchController != null && mDeckBatchController.isBatchMode()) {
            btnUndo.setVisibility(View.GONE);
            btnRedo.setVisibility(View.GONE);
            return;
        }
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY)
                || mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY);
        if (drawerOpen) {
            btnUndo.setVisibility(View.INVISIBLE);
            btnRedo.setVisibility(View.INVISIBLE);
        } else {
            // 抽屉关闭时，根据原有逻辑恢复按钮状态
            if (deckHistory.size() > 1 && isDeckEditable()) {
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

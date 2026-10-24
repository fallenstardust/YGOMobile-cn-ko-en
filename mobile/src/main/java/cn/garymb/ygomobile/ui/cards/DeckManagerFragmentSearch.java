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
 * DeckManagerFragment 拆分继承链的搜索结果与卡片编辑层。
 *
 * <p>承载搜索结果展示、卡片详情对话框、卡片点/长按、加卡/side 卡、
 * 拖拽插入、禁限与信用分校验、返回键处理等功能集群。
 * 拆分仅为控制单类规模，所有方法实现与原文件逐行一致。</p>
 */
public abstract class DeckManagerFragmentSearch extends DeckManagerFragmentDeck {
    @Override
    public void setLimit(LimitList limit, String caller) {//由cardsearcher通知更新适用禁卡表的方法
        Log.w(TAG, caller + " override: setLimitList genesys模式为: " + mSettings.getGenesysMode());
        setLimitList(limit);
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
        // 长按搜索结果列表中的卡片：收起左侧结果抽屉，在手指位置生成可拖动的浮动卡片，
        // 松手后根据停留位置插入主卡组/额外卡组/副卡组（校验与提示在handleDeckCardDropped中处理）
        if (cardInfo == null || isPackMode || mDeckDragHelper == null || mDeckDragHelper.isDragging()) {
            return;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        // 以卡片item中心作为浮动卡片的初始位置
        mDeckDragHelper.beginDrag(cardInfo, loc[0] + view.getWidth() / 2f, loc[1] + view.getHeight() / 2f);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCardInfoEvent(CardInfoEvent event) {
        int pos = event.position;
        Card cardInfo = mCardListAdapter.getItem(pos);
        if (cardInfo == null) {

        } else if (event.toMain) {
            if (!addMainCard(cardInfo)) {

            }
        } else {
            if (!addSideCard(cardInfo)) {

            }
        }
    }

    @Override
    public void onSearchResult(List<Card> cardInfos, boolean isHide) {
        tv_result_count.setText(String.valueOf(cardInfos.size()));
        String keyword = mCardSearcher.getCurrentKeyword();
        if (keyword != null && !keyword.isEmpty()) {
            currentCardSearchMessage = keyword;
        }

        mCardListAdapter.setSearchKeyword(currentCardSearchMessage);
        mCardListAdapter.set(cardInfos);
        mCardListAdapter.notifyDataSetChanged();
        if (!cardInfos.isEmpty()) {
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

    protected void showDeckCard(View view, int pos) {
        DeckItem deckItem = mDeckAdapater.getItem(pos);
        if (deckItem != null && deckItem.getCardInfo() != null) {
            showCardDialog(mDeckAdapater, deckItem.getCardInfo(), mDeckAdapater.getCardPosByView(pos));
        }
    }

    protected boolean isShowDrawer() {
        return mDrawerLayout.isDrawerOpen(Constants.CARD_RESULT_GRAVITY)
                || mDrawerLayout.isDrawerOpen(Constants.CARD_SEARCH_GRAVITY);
    }

    protected boolean isShowCard() {
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
                mDialog.hideButton(true);
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

            mCardDetail.setCurrentSearchKeyword(currentCardSearchMessage);
            // 绑定卡片数据到详情视图
            mCardDetail.bind(cardInfo, pos, provider);
        }
    }

    protected void showSearchKeyWord(String keyword) {//使用此方法，可以适用关键词查询逻辑，让完全符合关键词的卡名置顶显示，并同时搜索字段和效果文本
        currentCardSearchMessage = keyword;
        CardSearchInfo searchInfo = new CardSearchInfo.Builder().keyword(keyword).cardTypes(new ArrayList<>()).build();//构建CardSearchInfo时type不能为null
        mCardLoader.search(searchInfo);
        //点击卡片详情中的高亮文字进行的关键词搜索，同样计入搜索历史
        mCardSearcher.recordHistoryKeyword(keyword);
    }

    protected void showCardList(List<Card> cardList, boolean sort) {
        if (!cardList.isEmpty()) {
            onSearchResult(sort ? mCardLoader.sort(cardList) : cardList, false);//根据情况不同，判断是否调用CardLoader的sort方法排序List<Card>
            //sort为true代表点击“关联卡片”按钮（卡包展示为false），以当前卡片名作为关键词计入搜索历史
            if (sort && mCardDetail != null && mCardDetail.getCardInfo() != null) {
                mCardSearcher.recordHistoryKeyword(mCardDetail.getCardInfo().Name);
            }
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
    protected boolean addSideCard(Card cardInfo) {
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
     * 添加主卡组卡片，判断如果属于额外卡组则添加到额外卡组
     *
     * @param cardInfo 要添加的卡片信息
     * @return 添加成功返回true，否则返回false
     */
    protected boolean addMainCard(Card cardInfo) {
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

    /**
     * 处理搜索结果卡片长按拖动后的松手落点：
     * 先校验禁限/积分数量限制（checkLimit内部已toast具体原因），
     * 再按落点卡区执行定点插入，成功则记录历史并刷新计分，失败则toast原因，浮动卡片无论成败均消失
     *
     * @param card   被拖动的卡片
     * @param target 落点解析结果（卡区类型+插入位置）
     */
    protected void handleDeckCardDropped(Card card, DeckDragDropHelper.DropTarget target) {
        if (card == null || target == null) {
            return;
        }
        // 卡片数量/积分限制校验（失败原因已由checkLimit内部toast）
        if (!checkLimit(card)) {
            return;
        }
        DeckAdapater.DragInsertResult result = mDeckAdapater.dragInsertAt(card, target.type, target.insertPos);
        if (result == DeckAdapater.DragInsertResult.SUCCESS) {
            YGOUtil.showTextToast(R.string.add_card_tip_ok);
            addDeckToHistory(mDeckAdapater.getCurrentState());
            updateUndoRedoButtonVisibility();
        } else {
            showDragInsertFailToast(result);
        }
    }

    /**
     * 根据拖拽插入失败原因toast提示
     */
    protected void showDragInsertFailToast(DeckAdapater.DragInsertResult result) {
        int msgRes;
        switch (result) {
            case WRONG_PLACE_MAIN:
                msgRes = R.string.tip_drag_extra_in_main;
                break;
            case WRONG_PLACE_EXTRA:
                msgRes = R.string.tip_drag_normal_in_extra;
                break;
            case MAIN_FULL:
                msgRes = R.string.tip_drag_main_full;
                break;
            case EXTRA_FULL:
                msgRes = R.string.tip_drag_extra_full;
                break;
            case SIDE_FULL:
                msgRes = R.string.tip_drag_side_full;
                break;
            case FAILED_TOKEN:
                msgRes = R.string.add_card_tip_fail;
                break;
            default:
                msgRes = R.string.add_card_tip_fail;
                break;
        }
        YGOUtil.showTextToast(getString(msgRes));
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

    protected boolean checkLimit(Card cardInfo) {
        SparseArray<Integer> mCount = mDeckAdapater.getCardCount();
        Log.w("checkLimit", "mCount=" + mCount);
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
        //检查卡片（也要判断不在卡组时，所以count是可以为null的情况）意图加入卡组是否会超限制，超过则return false
        if (limitList.check(cardInfo, LimitType.GeneSys)) {// 检查GeneSys信用分限制
            // 获取当前卡片的信用分值（统一走 getGeneSysCredit，与显示/总分口径一致）
            Integer cardCreditValue = limitList.getGeneSysCredit(cardInfo);

            if (cardCreditValue != null && cardCreditValue > 0) {//genesys表中的卡需要进行检查，否则就是纯普通卡只需遵循最大3的规则
                // 获取当前禁卡表的信用分上限，一般是100，但可能不同genesys禁卡表给的上限分不同；
                // 再根据当前主卡组数量即时计算实际生效上限（配合"$__extra_score__"，取整适用）
                DeckInfo currentState = mDeckAdapater.getCurrentState();
                int mainCount = currentState != null ? currentState.getMainCount() : 0;
                Integer creditLimit = limitList.getEffectiveCreditLimit(mainCount);//
                // 计算当前卡组的信用总分
                int totalCredit = getCreditCount(mDeckAdapater.getCurrentState()) + cardCreditValue;//计算目前卡组信用分合计+当前卡的信用分的和，用于下面和上限值比较

                // 计算该卡片信用分+卡组信用总分的和是否大于上限值，是的话就无法加入卡组，不是就通过，会被加入卡组
                if (creditLimit != null && totalCredit > creditLimit) {
                    YGOUtil.showTextToast(getString(R.string.tip_credit_max, creditLimit));
                    return false;
                }

            }
        }
        return true;
    }

    /**
     * 获取指定卡组中所有GeneSys卡片的信用分总和
     *
     * @param deckinfo 卡组信息对象，包含卡组中的所有卡片信息
     * @return 返回卡组中所有GeneSys卡片的信用分总和，如果输入参数为空或获取限制列表失败则返回0
     */
    protected int getCreditCount(DeckInfo deckinfo) {
        // 处理空值情况
        if (deckinfo == null || mDeckAdapater == null || mDeckAdapater.getLimitList() == null) {
            return 0;
        }
        LimitList limitList = mDeckAdapater.getLimitList();

        // 计算当前卡组中所有GeneSys卡片的信用分总和
        int totalCredit = 0;
        List<Card> deck_info = deckinfo.getAllCards();
        for (int i = 0; i < deck_info.size(); i++) {
            // 统一走 getGeneSysCredit，与卡面显示/加卡校验完全同口径（含“code在表中、但折叠键不在表中”时的回退）
            Integer creditValue = limitList.getGeneSysCredit(deck_info.get(i));
            if (creditValue != null) {
                totalCredit += creditValue;
            }
        }

        return totalCredit;
    }

}

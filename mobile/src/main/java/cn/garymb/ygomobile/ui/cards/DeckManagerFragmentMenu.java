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
 * DeckManagerFragment 拆分继承链的菜单与卡组文件操作层。
 *
 * <p>承载 doMenu 菜单分发、卡组新建/重命名/删除/保存、分享、禁卡表应用、
 * ydk 文件枚举与备份恢复等功能集群。拆分仅为控制单类规模，
 * 所有方法实现与原文件逐行一致。</p>
 */
public abstract class DeckManagerFragmentMenu extends DeckManagerFragmentSearch {
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
                            save(mDeckAdapater.getYdkFile(), false); // 保存卡组文件
                            if (SharedPreferenceUtil.getServerToken() != null) {
                                LoginToken loginToken = new LoginToken(SharedPreferenceUtil.getServerUserId(), SharedPreferenceUtil.getServerToken());
                                VUiKit.defer().when(() -> {
                                    try {
                                        // 构建当前卡组的 MyDeckItem 对象
                                        List<MyDeckItem> deckItemList = new ArrayList<>();
                                        MyDeckItem currentItem = DeckUtil.getMyDeckItem(mDeckAdapater.getYdkFile());
                                        deckItemList.add(currentItem);
                                        // 上传卡组
                                        DeckSquareApiUtil.UploadMyDecks(deckItemList, loginToken);

                                    } catch (IOException e) {
                                        // 发生 IO 异常时返回异常对象
                                        return e;
                                    }
                                    // 正常执行完成后返回 0 表示成功
                                    return 0;
                                }).fail((e) -> {
                                    LogUtil.e(TAG, "Upload deck failed: " + e);
                                }).done((result) -> {

                                });

                            }
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
                            // 记录被删除卡组所属分类目录，删除后据此定位该分类的第一个卡组
                            String categoryDir = mDeckAdapater.getYdkFile().getParent();

                            // 先删除在线卡组
                            List<DeckFile> deckFileList = new ArrayList<>();
                            deckFileList.add(new DeckFile(mDeckAdapater.getYdkFile()));
                            DeckSquareApiUtil.deleteDecks(deckFileList);

                            // 再删除本地文件
                            FileUtils.deleteFile(mDeckAdapater.getYdkFile());
                            YGOUtil.showTextToast(R.string.done);
                            dlg.dismiss();

                            // 优先加载被删除卡组所属分类的第一个卡组，保持分类一致；
                            // 若该分类已无卡组，则回退到全局第一个卡组
                            File file = getFirstYdkInCategory(categoryDir);
                            if (file == null) {
                                file = getFirstYdk();
                            }
                            if (file != null) {
                                // loadDeckFromFile 内部异步才更新“最后卡组路径”，这里先同步写入，
                                // 保证随后刷新对话框时能正确定位到该分类并高亮该卡组
                                mSettings.setLastDeckPath(file.getAbsolutePath());
                            }
                            loadDeckFromFile(file);
                            // 同步刷新卡组管理对话框的本地卡组列表
                            syncDeckManageDialog();
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
    protected void createDeck(String savePath) {
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

    protected File getFirstYdk() {
        List<File> files = getYdkFiles();
        return files == null || files.size() == 0 ? null : files.get(0);
    }

    /**
     * 获取指定卡组分类目录下的第一个ydk卡组文件，其顺序与卡组管理对话框右列一致（DeckUtil.getDeckList，按名称排序）。
     *
     * @param categoryDir 卡组分类目录的绝对路径
     * @return 该分类下的第一个卡组文件，若该分类无卡组则返回null
     */
    protected File getFirstYdkInCategory(String categoryDir) {
        if (TextUtils.isEmpty(categoryDir)) {
            return null;
        }
        List<DeckFile> deckFiles = DeckUtil.getDeckList(categoryDir);
        if (deckFiles != null && !deckFiles.isEmpty()) {
            return deckFiles.get(0).getPathFile();
        }
        return null;
    }

    /**
     * 卡组新建/重命名/删除后，同步刷新卡组管理对话框中的本地卡组列表，使其与磁盘保持一致。
     * 若对话框当前不存在，则下次创建时会以最新数据构建，无需处理。
     */
    protected void syncDeckManageDialog() {
        if (!isAdded()) {
            return;
        }
        Fragment fragment = requireActivity().getSupportFragmentManager()
                .findFragmentByTag(DeckManageDialog.DIALOG_TAG);
        if (fragment instanceof DeckManageDialog) {
            ((DeckManageDialog) fragment).refreshLocalDeckList();
        }
    }

    protected void shareDeck() {
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

    protected void shareDeck1(Deck deck) {
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

    protected void shareUrl(String uri, String label) {
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

    protected File getSelectDeck(Spinner spinner) {
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
    protected List<File> getYdkFiles() {
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
     * 设置传参禁卡表的禁限，并更新相关适配器
     *
     * @param limitList 限制列表对象，如果为null则直接返回
     */
    protected void setLimitList(LimitList limitList) {
        if (limitList == null) return;
        // 检查新的限制列表是否与当前列表相同
        LimitList last = mDeckAdapater.getLimitList();//deckadapter只存在一种适用的表，可能是常规禁限表，可能是genesys表
        boolean nochanged = last != null && TextUtils.equals(last.getName(), limitList.getName());

        // 如果限制列表发生变化，则更新牌组适配器并通知数据变更
        if (!nochanged) {
            mDeckAdapater.setLimitList(limitList);
            requireActivity().runOnUiThread(() -> {
                mDeckAdapater.notifyItemRangeChanged(DeckItem.MainStart, DeckItem.MainEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.ExtraStart, DeckItem.ExtraEnd);
                mDeckAdapater.notifyItemRangeChanged(DeckItem.SideStart, DeckItem.SideEnd);
            });
        }
        // 更新卡片列表适配器的限制列表并通知数据变更
        mCardListAdapter.setLimitList(limitList);
        requireActivity().runOnUiThread(() -> mCardListAdapter.notifyDataSetChanged());

        // 根据是否有Genesys信用分上限值来显示计分板
        if (limitList.getCreditLimits() != null && limitList.getCreditLimits() > 0) {
            refreshDeckCreditCount();
            ll_genesys_scoreboard.setVisibility(View.VISIBLE);
        } else {
            ll_genesys_scoreboard.setVisibility(View.GONE);
        }

    }

    protected void inputDeckName(File oldYdk, String savePath, boolean keepOld) {
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
                        // loadDeckFromFile 内部异步才更新“最后卡组路径”，这里先同步写入，
                        // 保证随后刷新对话框时能正确定位到重命名后卡组所在的分类并高亮该卡组
                        mSettings.setLastDeckPath(ydk.getAbsolutePath());
                        loadDeckFromFile(ydk);
                        // 重命名后同步刷新卡组管理对话框
                        syncDeckManageDialog();
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
                    // loadDeckFromFile 内部异步才更新“最后卡组路径”，这里先同步写入，
                    // 保证随后刷新对话框时能正确定位到新卡组所在的分类并高亮该卡组
                    mSettings.setLastDeckPath(ydk.getAbsolutePath());
                    loadDeckFromFile(ydk);
                    // 新建卡组后同步刷新卡组管理对话框
                    syncDeckManageDialog();
                    //为新建卡组请求云端deckid并上传
                    if (SharedPreferenceUtil.getServerToken() != null) {
                        LoginToken loginToken = new LoginToken(SharedPreferenceUtil.getServerUserId(), SharedPreferenceUtil.getServerToken());
                        VUiKit.defer().when(() -> {
                            try {
                                List<MyDeckItem> deckItemList = new ArrayList<>();
                                MyDeckItem newItem = DeckUtil.getMyDeckItem(ydk);
                                deckItemList.add(newItem);
                                DeckSquareApiUtil.requestIdAndPushNewDecks(deckItemList, loginToken);
                            } catch (IOException e) {
                                return e;
                            }
                            return 0;
                        }).fail((e) -> {
                            LogUtil.e(TAG, "Upload new deck failed: " + e);
                        }).done((result) -> {
                            LogUtil.d(TAG, "New deck uploaded successfully");
                        });
                    }
                }
            } else {
                dlg.dismiss();
            }
        });
        builder.show();
    }

    protected void save(File ydk, boolean withoutId) {
        YGOUtil.showTextToast(mDeckAdapater.save(ydk, withoutId) ? R.string.save_tip_ok : R.string.save_tip_fail);
    }

    protected void save(File ydk) {
        save(ydk, false);
    }

    protected void doBackUpDeck() {
        try {
            FileUtils.delFile(ORI_DECK);//备份前删除原备份
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

    protected void doRestoreDeck() {
        try {
            FileUtils.copyDir(ORI_DECK, mSettings.getDeckDir(), false);
        } catch (Throwable e) {
            YGOUtil.showTextToast(e + "");
        }
        YGOUtil.showTextToast(R.string.done);
    }

}

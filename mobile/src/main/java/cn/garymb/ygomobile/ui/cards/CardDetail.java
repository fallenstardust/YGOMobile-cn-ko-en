package cn.garymb.ygomobile.ui.cards;

import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_SHARE_FILE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bm.library.PhotoView;
import com.feihua.dialogutils.util.DialogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.adapters.BaseAdapterPlus;
import cn.garymb.ygomobile.ui.widget.Shimmer;
import cn.garymb.ygomobile.ui.widget.ShimmerTextView;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.PackManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/***
 * 卡片详情
 */
public class CardDetail extends BaseAdapterPlus.BaseViewHolder {

    private static final int TYPE_DOWNLOAD_CARD_IMAGE_OK = 0;
    private static final int TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION = 1;
    private static final int TYPE_DOWNLOAD_CARD_IMAGE_ING = 2;

    private static final String TAG = String.valueOf(CardDetail.class);
    private final CardManager cardManager;
    private final PackManager packManager;
    private final CardLoader cardLoader;
    private final ImageView cardImage;
    private final TextView name;
    private final TextView btn_related;
    private final TextView desc;
    private final TextView level;
    private final TextView type;
    private final TextView race;
    private final TextView cardAtk;
    private final TextView cardDef;
    private final LinearLayout ll_pack;
    private final ShimmerTextView packName;
    private final TextView setName;
    private final TextView otView;
    private final TextView attrView;
    private final View monsterLayout;
    private final View close;
    private final View faq;
    private final View addMain;
    private final View addSide;
    private final View linkArrow;
    private final View layoutDetailPScale;
    private final TextView detailCardScale;
    private final TextView cardCode;
    private final View lbSetCode;
    private final ImageLoader imageLoader;
    private final View mImageFav, atkdefView;

    private final StringManager mStringManager;
    private final BaseActivity mContext;
    private int curPosition;
    private Card mCardInfo;
    private CardListProvider mProvider;
    private OnDeckManagerCardClickListener mListener;
    private DialogUtils dialog;
    private PhotoView photoView;
    private LinearLayout ll_bar;
    private ProgressBar pb_loading;
    private TextView tv_loading;
    private LinearLayout ll_btn;
    private Button btn_redownload;
    private Button btn_share;
    private boolean isDownloadCardImage = true;
    private List<String> spanStringList = new ArrayList<>();
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_DOWNLOAD_CARD_IMAGE_OK:
                    isDownloadCardImage = true;
                    ll_bar.startAnimation(AnimationUtils.loadAnimation(context, R.anim.out_from_bottom));
                    ll_bar.setVisibility(View.GONE);
                    imageLoader.bindImage(photoView, msg.arg1, ImageLoader.Type.origin);
                    imageLoader.bindImage(cardImage, msg.arg1, ImageLoader.Type.middle);
                    if (mListener != null) {
                        mListener.onImageUpdate(mCardInfo);
                    }
                    break;
                case TYPE_DOWNLOAD_CARD_IMAGE_ING:
                    tv_loading.setText(msg.arg1 + "%");
                    pb_loading.setProgress(msg.arg1);
                    break;
                case TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION:
                    isDownloadCardImage = true;
                    ll_bar.startAnimation(AnimationUtils.loadAnimation(context, R.anim.out_from_bottom));
                    ll_bar.setVisibility(View.GONE);
                    YGOUtil.showTextToast("error" + msg.obj);
                    break;

            }
        }
    };
    private Shimmer shimmer;
    private boolean mShowAdd = false;
    private OnFavoriteChangedListener mOnFavoriteChangedListener;

    public CardDetail(BaseActivity context, ImageLoader imageLoader, StringManager stringManager) {
        super(context.getLayoutInflater().inflate(R.layout.dialog_cardinfo, null));
        mContext = context;
        cardImage = findViewById(R.id.card_image);
        this.imageLoader = imageLoader;
        mStringManager = stringManager;
        ll_pack = findViewById(R.id.ll_pack);
        packName = findViewById(R.id.pack_name);
        toggleAnimation(packName);
        name = findViewById(R.id.text_name);
        btn_related = findViewById(R.id.btn_related);
        desc = findViewById(R.id.text_desc);
        close = findViewById(R.id.btn_close);
        cardCode = findViewById(R.id.card_code);
        level = findViewById(R.id.card_level);
        linkArrow = findViewById(R.id.detail_link_arrows);
        type = findViewById(R.id.card_type);
        faq = findViewById(R.id.btn_faq);
        cardAtk = findViewById(R.id.card_atk);
        cardDef = findViewById(R.id.card_def);
        atkdefView = findViewById(R.id.layout_atkdef2);
        mImageFav = findViewById(R.id.image_fav);

        monsterLayout = findViewById(R.id.layout_monster);
        layoutDetailPScale = findViewById(R.id.detail_p_scale);
        detailCardScale = findViewById(R.id.detail_cardscale);
        race = findViewById(R.id.card_race);
        setName = findViewById(R.id.card_setname);
        addMain = findViewById(R.id.btn_add_main);
        addSide = findViewById(R.id.btn_add_side);
        otView = findViewById(R.id.card_ot);
        attrView = findViewById(R.id.card_attribute);
        lbSetCode = findViewById(R.id.label_setcode);
        cardManager = DataManager.get().getCardManager();
        packManager = DataManager.get().getPackManager();
        cardLoader = new CardLoader(context);
        close.setOnClickListener((v) -> {
            if (mListener != null) {
                mListener.onClose();
            }
        });
        addMain.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                mListener.onAddMainCard(cardInfo);
            }
        });
        addSide.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                mListener.onAddSideCard(cardInfo);
            }
        });
        faq.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                mListener.onOpenUrl(cardInfo);
            }
        });
        btn_related.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                mListener.onGetRelatedCardList(relatedCards(cardInfo));
            }
        });
        findViewById(R.id.lastone).setOnClickListener((v) -> {
            onPreCard();
        });
        findViewById(R.id.nextone).setOnClickListener((v) -> {
            onNextCard();
        });
        mImageFav.setOnClickListener((v) -> {
            doMyFavorites(getCardInfo());
        });
        ll_pack.setOnClickListener((v) -> {
            if (mListener != null) {
                Card cardInfo = getCardInfo();
                if (cardInfo == null) {
                    return;
                }
                showPackList(cardInfo);
            }
        });
    }

    public void toggleAnimation(ShimmerTextView target) {
        if (shimmer != null && shimmer.isAnimating()) {
            shimmer.cancel();
        } else {
            shimmer = new Shimmer();
            shimmer.start(target);
        }
    }

    /**
     * 收藏卡片
     */
    public void doMyFavorites(Card cardInfo) {
        boolean ret = CardFavorites.get().toggle(cardInfo.Code);
        mImageFav.setSelected(ret);
        if (mOnFavoriteChangedListener != null) {
            mOnFavoriteChangedListener.onFavoriteChange(cardInfo, ret);
        }
    }

    public ImageView getCardImage() {
        return cardImage;
    }

    public void hideClose() {
        close.setVisibility(View.GONE);
    }

    public void showAdd() {
        mShowAdd = true;
        addSide.setVisibility(View.VISIBLE);
        addMain.setVisibility(View.VISIBLE);
    }

    public View getView() {
        return view;
    }

    public BaseActivity getContext() {
        return mContext;
    }

    public void setOnCardClickListener(OnDeckManagerCardClickListener listener) {
        mListener = listener;
    }

    public void setCallBack(OnFavoriteChangedListener callBack) {
        mOnFavoriteChangedListener = callBack;
    }

    public void bind(Card cardInfo, final int position, final CardListProvider provider) {
        curPosition = position;
        mProvider = provider;
        if (cardInfo != null) {
            setCardInfo(cardInfo, view);
        }
    }

    public int getCurPosition() {
        return curPosition;
    }

    public CardListProvider getProvider() {
        return mProvider;
    }

    public Card getCardInfo() {
        return mCardInfo;
    }

    private void showPackList(Card cardInfo) {
        Integer idToUse = cardInfo.Alias != 0 ? cardInfo.Alias : cardInfo.Code;
        mListener.onShowPackList(packManager.getCards(cardLoader, idToUse));
    }

    public void setHighlightTextWithClickableSpans(String text) {
        SpannableString spannableString = new SpannableString(text);
        spanStringList.clear(); // 清空之前的高亮文本列表
        // 解析器状态
        QuoteType currentQuoteType = QuoteType.NONE;
        Stack<Integer> stack = new Stack<>();
        int start = -1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (currentQuoteType) {
                case NONE:
                    if (c == '「') {
                        currentQuoteType = QuoteType.ANGLE_QUOTE;
                        start = i + 1;
                        stack.push(i);
                    } else if (c == '"') {
                        currentQuoteType = QuoteType.DOUBLE_QUOTE;
                        start = i + 1;
                        stack.push(i);
                    }
                    break;

                case ANGLE_QUOTE:
                    if (c == '「') {
                        stack.push(i);
                    } else if (c == '」' && !stack.isEmpty()) {
                        stack.pop();
                        if (stack.isEmpty()) {
                            String quotedText = text.substring(start, i).trim();
                            // 使用 queryable 方法判断是否高亮
                            applySpan(spannableString, start, i, queryable(quotedText)? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            spanStringList.add(quotedText);
                            currentQuoteType = QuoteType.NONE;
                        }
                    }
                    break;

                case DOUBLE_QUOTE:
                    if (c == '"' && !stack.isEmpty()) {
                        stack.pop();
                        if (stack.isEmpty()) {
                            String quotedText = text.substring(start, i).trim();
                            applySpan(spannableString, start, i, queryable(quotedText)? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            spanStringList.add(quotedText);
                            currentQuoteType = QuoteType.NONE;
                        } else {
                            stack.push(i);
                            // 对于嵌套的情况，只增加/减少栈中的元素而不应用样式
                        }
                    }
                    break;
            }
        }
        desc.setText(spannableString);
        desc.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void applySpan(SpannableString spannableString, int start, int end, int color) {
        // 设置颜色
        spannableString.setSpan(new ForegroundColorSpan(color), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 设置点击监听
        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                if (color != Color.WHITE) {
                    // 获取被点击的文本内容
                    String clickedText = ((TextView) widget).getText().subSequence(start, end).toString();
                    mListener.onSearchKeyWord(clickedText);
                } else {
                    YGOUtil.showTextToast(context.getString(R.string.searchresult) + context.getString(R.string.already_end));
                }

            }

            @Override
            public void updateDrawState(TextPaint ds) {
                // 可以在这里自定义点击状态下的样式，如去掉下划线
                ds.setUnderlineText(true);
            }
        }, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private boolean queryable(String keyword) {
        // 检查关键词是否为空或仅包含空白字符
        if (TextUtils.isEmpty(keyword)) {
            return false;
        }

        SparseArray<Card> cards = cardManager.getAllCards();
        List<Card> matchingCards = new ArrayList<>();

        // 检查关键词是否存在于卡片名字或描述中
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.valueAt(i);
            // 确保 card.Name 和 card.Desc 不为 null
            if ((card.Name != null && card.Name.contains(keyword)) ||
                    (card.Desc != null && card.Desc.contains(keyword))) {
                matchingCards.add(card);
            }
        }
        // 检查匹配结果
        if (matchingCards.isEmpty()) {
            return false; // 如果没有找到匹配的卡片，返回 false
        } else if (matchingCards.size() == 1) {
            // 如果只有一个匹配项，检查是否是当前卡片
            Card matchedCard = matchingCards.get(0);
            if (getCardInfo() != null && getCardInfo().equals(matchedCard)) {
                return false; // 如果是当前卡片，返回 false
            } else {
                return true; // 否则返回 true
            }
        } else {
            return true; // 如果有多个匹配项，返回 true
        }
    }

    private boolean relatable(Card cardInfo) {
        List<Card> matchingCards = relatedCards(cardInfo);
        if (!matchingCards.isEmpty())
            return true;
        return false;
    }

    private List<Card> relatedCards(Card cardInfo) {
        SparseArray<Card> cards = cardManager.getAllCards();
        // 新创建一个表避免外部修改原本的表
        List<String> highlightedTexts = new ArrayList<>(spanStringList);
        // 使用 ArrayList 来保存匹配的卡片
        List<Card> matchingCards = new ArrayList<>();

        // 将 cardInfo 的 setCode 转换为 List<Long>
        List<Long> cardInfoSetCodes = new ArrayList<>();
        for (long setCode : cardInfo.getSetCode()) {
            if (setCode != 0) cardInfoSetCodes.add(setCode);
        }
        Log.w("cc cardInfoSetCodes", cardInfoSetCodes.toString());

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.valueAt(i);

                // 检查卡片名或描述是否包含给定卡片的名字
                if (!card.Name.equals(cardInfo.Name) && (card.Name.contains(cardInfo.Name) || card.Desc.contains(cardInfo.Name))) {
                    // 检查卡片是否已经存在于匹配列表中
                    if (!matchingCards.contains(card)) matchingCards.add(card);
                }

                // 获取卡片的字段并检查是否有相同的字段
                for (long setCode : card.getSetCode()) {
                    if (cardInfoSetCodes.contains(setCode)) {
                        if (!matchingCards.contains(card) && !card.Name.equals(cardInfo.Name)) matchingCards.add(card);
                    }
                }

                for (String keyword : highlightedTexts) {
                    if ((card.Name != null && card.Name.equals(keyword)) //和关键词完全一致的视为关联卡
                     || (card.Desc != null && (card.Desc.contains("「" + keyword + "」") || card.Desc.contains("\"" + keyword + "\"")))) {//描述中关键词指向的字段一致的视为关联卡
                        if (!matchingCards.contains(card) && !card.Name.equals(cardInfo.Name)) matchingCards.add(card);
                    }
                }

        }
        return matchingCards;
    }

    private void setCardInfo(Card cardInfo, View view) {
        if (cardInfo == null) return;
        mCardInfo = cardInfo;
        imageLoader.bindImage(cardImage, cardInfo, ImageLoader.Type.middle);
        dialog = DialogUtils.getdx(context);
        cardImage.setOnClickListener((v) -> {
            showCardImageDetail(cardInfo.Code);
        });
        packName.setText(packManager.findPackNameById((cardInfo.Alias != 0 && Math.abs(cardInfo.Alias - cardInfo.Code) <= 20) ? cardInfo.Alias : cardInfo.Code));
        name.setText(cardInfo.Name);
        setHighlightTextWithClickableSpans(cardInfo.Name.equals("Unknown") ? context.getString(R.string.tip_card_info_diff) : cardInfo.Desc);
        btn_related.setVisibility(relatable(cardInfo) ? View.VISIBLE : View.GONE);
        cardCode.setText(String.format("%08d", cardInfo.getCode()));
        if (cardInfo.isType(CardType.Token)) {
            faq.setVisibility(View.INVISIBLE);
            ll_pack.setVisibility(View.INVISIBLE);
        } else {
            faq.setVisibility(View.VISIBLE);
            ll_pack.setVisibility(View.VISIBLE);
        }
        if (mShowAdd) {
            if (cardInfo.isType(CardType.Token)) {
                addSide.setVisibility(View.INVISIBLE);
                addMain.setVisibility(View.INVISIBLE);
            } else {
                addSide.setVisibility(View.VISIBLE);
                addMain.setVisibility(View.VISIBLE);
            }
        }
        //按是否存在于收藏夹切换显示图标
        mImageFav.setSelected(CardFavorites.get().hasCard(cardInfo.Code));

        type.setText(CardUtils.getAllTypeString(cardInfo, mStringManager).replace("/", "|"));
        attrView.setText(mStringManager.getAttributeString(cardInfo.Attribute));
        otView.setText(mStringManager.getOtString(cardInfo.Ot, true));
        long[] sets = cardInfo.getSetCode();
        setName.setText("");
        int index = 0;
        for (long set : sets) {
            if (set > 0) {
                if (index != 0) {
                    setName.append("\n");
                }
                setName.append("" + mStringManager.getSetName(set));
                index++;
            }
        }

        if (TextUtils.isEmpty(setName.getText())) {
            setName.setVisibility(View.INVISIBLE);
            lbSetCode.setVisibility(View.INVISIBLE);
        } else {
            setName.setVisibility(View.VISIBLE);
            lbSetCode.setVisibility(View.VISIBLE);
        }

        if (cardInfo.isType(CardType.Monster)) {
            atkdefView.setVisibility(View.VISIBLE);
            monsterLayout.setVisibility(View.VISIBLE);
            race.setVisibility(View.VISIBLE);
            String star = "★" + cardInfo.getStar();
           /* for (int i = 0; i < cardInfo.getStar(); i++) {
                star += "★";
            }*/
            level.setText(star);
            if (cardInfo.isType(CardType.Xyz)) {
                level.setTextColor(context.getResources().getColor(R.color.star_rank));
            } else {
                level.setTextColor(context.getResources().getColor(R.color.star));
            }
            if (cardInfo.isType(CardType.Pendulum)) {
                layoutDetailPScale.setVisibility(View.VISIBLE);
                detailCardScale.setText(String.valueOf(cardInfo.LeftScale));
            } else {
                layoutDetailPScale.setVisibility(View.GONE);
            }
            cardAtk.setText((cardInfo.Attack < 0 ? "?" : String.valueOf(cardInfo.Attack)));
            //连接怪兽设置
            if (cardInfo.isType(CardType.Link)) {
                level.setVisibility(View.GONE);
                linkArrow.setVisibility(View.VISIBLE);
                cardDef.setText((cardInfo.getStar() < 0 ? "?" : "LINK-" + cardInfo.getStar()));
                BaseActivity.showLinkArrows(cardInfo, view);
            } else {
                level.setVisibility(View.VISIBLE);
                linkArrow.setVisibility(View.GONE);
                cardDef.setText((cardInfo.Defense < 0 ? "?" : String.valueOf(cardInfo.Defense)));
            }
            race.setText(mStringManager.getRaceString(cardInfo.Race));
        } else {
            atkdefView.setVisibility(View.GONE);
            race.setVisibility(View.GONE);
            monsterLayout.setVisibility(View.GONE);
            level.setVisibility(View.GONE);
            linkArrow.setVisibility(View.GONE);
        }
    }

    private void showCardImageDetail(int code) {
        View view = dialog.initDialog(context, R.layout.dialog_photo);

        dialog.setDialogWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        Window dialogWindow = dialog.getDialog().getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        dialogWindow.setAttributes(lp);

        photoView = view.findViewById(R.id.photoView);
        ll_bar = view.findViewById(R.id.ll_bar);
        pb_loading = view.findViewById(R.id.pb_loading);
        tv_loading = view.findViewById(R.id.tv_name);
        pb_loading.setMax(100);

        ll_btn = view.findViewById(R.id.ll_btn);
        btn_redownload = view.findViewById(R.id.btn_redownload);
        btn_share = view.findViewById(R.id.btn_share);

        // 启用图片缩放功能
        photoView.enable();

        photoView.setOnClickListener(View -> {
            if (ll_btn.getVisibility() == android.view.View.VISIBLE) {
                ll_btn.startAnimation(AnimationUtils.loadAnimation(context, R.anim.push_out));
                ll_btn.setVisibility(android.view.View.GONE);
            } else {
                dialog.dis();
            }
        });

        photoView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isDownloadCardImage || cardManager.getCard(code) == null)
                    return false;
                ll_btn.startAnimation(AnimationUtils.loadAnimation(context, R.anim.push_in));
                ll_btn.setVisibility(View.VISIBLE);
                btn_redownload.setOnClickListener((s) -> {
                    ll_btn.startAnimation(AnimationUtils.loadAnimation(context, R.anim.push_out));
                    ll_btn.setVisibility(View.GONE);
                    downloadCardImage(code, true);
                });

                btn_share.setOnClickListener((s) -> {
                    ll_btn.startAnimation(AnimationUtils.loadAnimation(context, R.anim.push_out));
                    ll_btn.setVisibility(View.GONE);
                    String fname = String.valueOf(code);
                    Intent intent = new Intent(ACTION_SHARE_FILE);
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.putExtra(IrrlichtBridge.EXTRA_SHARE_TYPE, "jpg");
                    intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, fname + Constants.IMAGE_URL_EX);
                    intent.setPackage(context.getPackageName());
                    try {
                        context.startActivity(intent);
                    } catch (Throwable e) {
                        YGOUtil.showTextToast("dev error:not found activity.", Toast.LENGTH_SHORT);
                    }
                });

                imageLoader.bindImage(cardImage, code, null, ImageLoader.Type.origin);
                return true;
            }
        });

        //先显示普通卡片大图，判断如果没有高清图就下载
        imageLoader.bindImage(photoView, code, null, ImageLoader.Type.middle);

        if (null == imageLoader.getImageFile(code)) {
            downloadCardImage(code, false);
        }

    }

    private void downloadCardImage(int code, boolean force) {
        if (cardManager.getCard(code) == null) {
            YGOUtil.showTextToast(context.getString(R.string.tip_expansions_image));
            return;
        }
        File imgFile = new File(AppsSettings.get().getCardImagePath(code));
        final File tmp = new File(imgFile.getAbsolutePath() + ".tmp");
        if (tmp.exists()) {
            if (force) {
                //强制下载，就删除tmp,重新下载
                FileUtils.deleteFile(tmp);
                //删除原来卡图
                FileUtils.deleteFile(imgFile);
            } else {
                return;
            }
        }
        isDownloadCardImage = false;
        ll_bar.setVisibility(View.VISIBLE);
        ll_bar.startAnimation(AnimationUtils.loadAnimation(context, R.anim.in_from_top));
        DownloadUtil.get().download(YGOUtil.getCardImageDetailUrl(code), tmp.getParent(), tmp.getName(), new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                boolean bad = file.length() < 50 * 1024;
                if (bad || !tmp.renameTo(imgFile)) {
                    FileUtils.deleteFile(file);
                    Message message = new Message();
                    message.what = TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION;
                    message.obj = context.getString(R.string.download_image_error);
                    handler.sendMessage(message);
                } else {
                    Message message = new Message();
                    message.what = TYPE_DOWNLOAD_CARD_IMAGE_OK;
                    message.arg1 = code;
                    handler.sendMessage(message);
                }
            }

            @Override
            public void onDownloading(int progress) {
                Message message = new Message();
                message.what = TYPE_DOWNLOAD_CARD_IMAGE_ING;
                message.arg1 = progress;
                handler.sendMessage(message);
            }

            @Override
            public void onDownloadFailed(Exception e) {
                Log.w(IrrlichtBridge.TAG, "download image error:" + e.getMessage());
                //下载失败后删除下载的文件
                FileUtils.deleteFile(tmp);
//                downloadCardImage(code, file);

                Message message = new Message();
                message.what = TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION;
                message.obj = e.toString();
                handler.sendMessage(message);
            }
        });

    }

    public void onPreCard() {
        int position = getCurPosition();
        CardListProvider provider = getProvider();
        if (position == 0) {
            getContext().showToast(R.string.already_top, Toast.LENGTH_SHORT);
        } else {
            int index = position;
            do {
                if (index == 0) {
                    getContext().showToast(R.string.already_top, Toast.LENGTH_SHORT);
                    return;
                } else {
                    index--;
                }
            } while (provider.getCard(index) == null || provider.getCard(index).Name == null || provider.getCard(position).Name.equals(provider.getCard(index).Name));

            bind(provider.getCard(index), index, provider);
            if (position == 1) {
                getContext().showToast(R.string.already_top, Toast.LENGTH_SHORT);
            }
        }
    }

    public void onNextCard() {
        int position = getCurPosition();
        CardListProvider provider = getProvider();
        if (position < provider.getCardsCount() - 1) {
            int index = position;
            do {
                if (index == provider.getCardsCount() - 1) {
                    getContext().showToast(R.string.already_end, Toast.LENGTH_SHORT);
                    return;
                } else {
                    index++;
                }
            } while (provider.getCard(index) == null || provider.getCard(index).Name == null || provider.getCard(position).Name.equals(provider.getCard(index).Name));

            bind(provider.getCard(index), index, provider);
            if (position == provider.getCardsCount() - 1) {
                getContext().showToast(R.string.already_end, Toast.LENGTH_SHORT);
            }
        } else {
            getContext().showToast(R.string.already_end, Toast.LENGTH_SHORT);
        }
    }

    // 定义引号类型
    private enum QuoteType {NONE, DOUBLE_QUOTE, ANGLE_QUOTE}

    public interface OnFavoriteChangedListener {
        void onFavoriteChange(Card card, boolean favorite);
    }

    public interface OnDeckManagerCardClickListener {
        void onOpenUrl(Card cardInfo);

        void onAddMainCard(Card cardInfo);

        void onAddSideCard(Card cardInfo);

        void onImageUpdate(Card cardInfo);

        void onShowPackList(List<Card> packList);

        void onSearchKeyWord(String keyword);

        void onGetRelatedCardList(List<Card> cardList);

        void onClose();
    }

    public static class OnCardSearcherCardClickListener implements OnDeckManagerCardClickListener {
        public OnCardSearcherCardClickListener() {
        }

        @Override
        public void onOpenUrl(Card cardInfo) {

        }

        @Override
        public void onClose() {
        }

        @Override
        public void onImageUpdate(Card cardInfo) {

        }

        @Override
        public void onShowPackList(List<Card> packList) {

        }

        @Override
        public void onSearchKeyWord(String keyword) {

        }

        @Override
        public void onGetRelatedCardList(List<Card> cardList) {

        }

        @Override
        public void onAddSideCard(Card cardInfo) {

        }

        @Override
        public void onAddMainCard(Card cardInfo) {

        }
    }

}

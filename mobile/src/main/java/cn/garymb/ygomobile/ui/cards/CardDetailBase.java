package cn.garymb.ygomobile.ui.cards;

import static cn.garymb.ygomobile.Constants.ASSET_ATTR_RACE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.bm.library.PhotoView;
import com.feihua.dialogutils.util.DialogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.adapters.BaseAdapterPlus;
import cn.garymb.ygomobile.ui.widget.Shimmer;
import cn.garymb.ygomobile.ui.widget.ShimmerTextView;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.PackManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;


/**
 * Card detail view holder, base layer of the split inheritance chain
 * (CardDetailBase <- CardDetailMid <- CardDetail). Owns all shared view
 * and state fields, the download handler, the shimmer/animation and
 * favorite toggles, the small public accessor API, the quoted-keyword
 * span highlighters, the related/query card resolvers and the nested
 * listener contracts.
 */
abstract class CardDetailBase extends BaseAdapterPlus.BaseViewHolder {

    protected static final int TYPE_DOWNLOAD_CARD_IMAGE_OK = 0;
    protected static final int TYPE_DOWNLOAD_CARD_IMAGE_EXCEPTION = 1;
    protected static final int TYPE_DOWNLOAD_CARD_IMAGE_ING = 2;

    protected static final String TAG = String.valueOf(CardDetail.class);
    protected CardManager cardManager;
    protected PackManager packManager;
    protected CardLoader cardLoader;
    protected ImageView cardImage, icon, icon_star, icon_attribute, icon_race;
    protected TextView name, desc, type, level, attrView, race, cardAtk, cardDef, setName, otView, detailCardScale, cardCode;
    protected TextView tv_loading;
    protected LinearLayout btn_related, ll_pack, faq, addMain, addSide;
    protected ShimmerTextView packName;
    protected ImageButton close;
    protected View linkArrow, layoutDetailPScale, monsterLayout, lbSetCode, mImageFav, atkdefView;
    protected ImageLoader imageLoader;

    protected StringManager mStringManager;
    protected BaseActivity mContext;
    protected int curPosition;
    protected Card mCardInfo;
    protected CardListProvider mProvider;
    protected OnDeckManagerCardClickListener mListener;
    protected DialogUtils dialog;
    protected PhotoView photoView;
    protected LinearLayout ll_bar, ll_btn;
    protected ProgressBar pb_loading;
    protected Button btn_redownload, btn_share;
    protected boolean isDownloadCardImage = true;
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
    protected List<String> spanStringList = new ArrayList<>();
    protected Shimmer shimmer;
    protected boolean mShowAdd = false;
    protected OnFavoriteChangedListener mOnFavoriteChangedListener;
    protected String mCurrentSearchKeyword = null;
    protected boolean mHideRelatedButton = false;
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

    public void hideRelatedButton() {
        mHideRelatedButton = true;
        btn_related.setVisibility(View.INVISIBLE);
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

    public void setCurrentSearchKeyword(String keyword) {
        mCurrentSearchKeyword = keyword;
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

    protected void showPackList(Card cardInfo) {
        Integer idToUse = cardInfo.getCode();
        mListener.onShowCardList(packManager.getCards(cardLoader, idToUse), false);
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
                            applySpan(spannableString, start, i, queryable(quotedText) ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
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
                            applySpan(spannableString, start, i, queryable(quotedText) ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
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

    protected void applySpan(SpannableString spannableString, int start, int end, int color) {
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
    protected SpannableString highlightKeywordBackground(String text, String keyword) {
        SpannableString spannableString = new SpannableString(text);
        if (text == null || keyword == null || keyword.isEmpty()) {
            return spannableString;
        }

        // 按空格分割关键词
        String[] keywords = keyword.trim().split("\\s+");
        
        // 对每个关键词进行高亮处理
        for (String singleKeyword : keywords) {
            if (singleKeyword.isEmpty()) {
                continue;
            }
            
            int startIndex = 0;
            while (startIndex < text.length()) {
                int index = text.indexOf(singleKeyword, startIndex);
                if (index == -1) {
                    break;
                }

                int endIndex = index + singleKeyword.length();
                BackgroundColorSpan bgSpan = new BackgroundColorSpan(YGOUtil.c(R.color.item_bg));
                spannableString.setSpan(bgSpan, index, endIndex, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);

                startIndex = endIndex;
            }
        }

        return spannableString;
    }

    /**
     * 同时应用搜索关键词背景高亮和引用文本可点击高亮
     * 先处理引用文本的可点击高亮，再叠加搜索关键词的背景高亮
     *
     * @param text          原始文本
     * @param searchKeyword 搜索关键词
     * @return 包含两种高亮效果的SpannableString
     */
    protected SpannableString applyBothHighlights(String text, String searchKeyword) {
        SpannableString spannableString = new SpannableString(text);
        spanStringList.clear();

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
                            if (!isAlreadyHighlighted(spannableString, start, i)) {
                                applySpanForCombined(spannableString, start, i, queryable(quotedText) ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            }
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
                            if (!isAlreadyHighlighted(spannableString, start, i)) {
                                applySpanForCombined(spannableString, start, i, queryable(quotedText) ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            }
                            spanStringList.add(quotedText);
                            currentQuoteType = QuoteType.NONE;
                        } else {
                            stack.push(i);
                        }
                    }
                    break;
            }
        }

        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            SpannableString keywordHighlighted = highlightKeywordBackground(text, searchKeyword);
            BackgroundColorSpan[] spans = keywordHighlighted.getSpans(0, text.length(), BackgroundColorSpan.class);
            for (BackgroundColorSpan span : spans) {
                int spanStart = keywordHighlighted.getSpanStart(span);
                int spanEnd = keywordHighlighted.getSpanEnd(span);
                spannableString.setSpan(new BackgroundColorSpan(YGOUtil.c(R.color.item_bg)), spanStart, spanEnd, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        desc.setText(spannableString);
        desc.setMovementMethod(LinkMovementMethod.getInstance());

        return spannableString;
    }

    /**
     * 为合并高亮应用颜色和点击事件
     *
     * @param spannableString 要应用的SpannableString
     * @param start           起始位置
     * @param end             结束位置
     * @param color           文字颜色
     */
    protected void applySpanForCombined(SpannableString spannableString, int start, int end, int color) {
        spannableString.setSpan(new ForegroundColorSpan(color), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                if (color != Color.WHITE) {
                    String clickedText = ((TextView) widget).getText().subSequence(start, end).toString();
                    mListener.onSearchKeyWord(clickedText);
                } else {
                    YGOUtil.showTextToast(context.getString(R.string.searchresult) + context.getString(R.string.already_end));
                }
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setUnderlineText(true);
            }
        }, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected boolean isAlreadyHighlighted(SpannableString spannableString, int start, int end) {
        ForegroundColorSpan[] spans = spannableString.getSpans(start, end, ForegroundColorSpan.class);
        return spans.length > 0;
    }
    protected List<Card> queryList(String keyword) {
        // 获取关键词对应的 setcode
        long setcode = DataManager.get().getStringManager().getSetCode(keyword, true);
        // 从 cardManager 获取所有卡片
        SparseArray<Card> cards = cardManager.getAllCards();
        if (cards == null) {
            return new ArrayList<>();
        }
        // 使用 HashSet 来保存匹配的卡片，避免重复并提高查找效率
        Set<Card> matchingCards = new HashSet<>();
        // 建立CardInfo拥有的字段表
        List<Long> cardInfoSetCodes = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.valueAt(i);
            // 如果 card.Name 或 card.Desc 为 null，则跳过该卡片
            if (card.Name == null && card.Desc == null) continue;
            // 清空字段表以防重复累加
            cardInfoSetCodes.clear();
            // 将 card 的 setCode 转换为 List<Long>
            for (long setCode : card.getSetCode()) {
                if (setCode > 0) cardInfoSetCodes.add(setCode);
            }
            // 检查关键词是否有对应字段
            if (setcode > 0 && cardInfoSetCodes.contains(setcode)) {
                matchingCards.add(card);
            }
            // 检查关键词是否存在于卡片名字或描述中
            if ((card.Name != null && card.Name.contains(keyword)) ||
                    (card.Desc != null && card.Desc.contains(keyword))) {
                matchingCards.add(card);
            }
        }
        // 将结果转换回 List<Card>
        return new ArrayList<>(matchingCards);
    }

    protected boolean queryable(String keyword) {
        List<Card> matchingCards = queryList(keyword);
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

    protected boolean relatable(Card cardInfo) {
        List<Card> matchingCards = relatedCards(cardInfo);
        if (!matchingCards.isEmpty())
            return true;
        return false;
    }

    protected List<Card> relatedCards(Card cardInfo) {
        SparseArray<Card> cards = cardManager.getAllCards();
        // 使用 HashSet 来保存匹配的卡片，避免重复并提高查找效率
        Set<Card> matchingCards = new HashSet<>();
        // 新创建一个表避免外部修改原本的表
        List<String> highlightedTexts = new ArrayList<>(spanStringList);
        // 将 cardInfo 的 setCode 转换为 Set<Long>
        Set<Long> cardInfoSetCodes = new HashSet<>();
        for (long setCode : cardInfo.getSetCode()) {
            if (setCode != 0) cardInfoSetCodes.add(setCode);
        }
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.valueAt(i);
            // 空值检查
            if (card.Name == null || card.Desc == null) continue;
            // 检查卡片名或描述是否包含给定卡片的名字
            if (!card.Name.equals(cardInfo.Name)) {
                if (card.Name.contains(cardInfo.Name) || card.Desc.contains(cardInfo.Name)) {
                    matchingCards.add(card);
                }
            }
            // 获取卡片的字段并检查是否有相同的字段
            for (long setCode : card.getSetCode()) {
                if (cardInfoSetCodes.contains(setCode)) {
                    if (!card.Name.equals(cardInfo.Name))
                        matchingCards.add(card);
                }
            }
            for (String keyword : highlightedTexts) {
                boolean nameMatch = card.Name != null && card.Name.equals(keyword);
                boolean descMatch = card.Desc != null && (card.Desc.contains("「" + keyword + "」") || card.Desc.contains("\"" + keyword + "\""));

                if (nameMatch || descMatch) { // 和关键词完全一致的视为关联卡
                    if (!card.Name.equals(cardInfo.Name))
                        matchingCards.add(card);
                }
            }
        }
        // 将结果转换回 List<Card>
        return new ArrayList<>(matchingCards);
    }
    // 定义引号类型
    protected enum QuoteType {NONE, DOUBLE_QUOTE, ANGLE_QUOTE}

    public interface OnFavoriteChangedListener {
        void onFavoriteChange(Card card, boolean favorite);
    }

    public interface OnDeckManagerCardClickListener {
        void onOpenUrl(Card cardInfo);

        void onAddMainCard(Card cardInfo);

        void onAddSideCard(Card cardInfo);

        void onImageUpdate(Card cardInfo);

        void onSearchKeyWord(String keyword);

        void onShowCardList(List<Card> cardList, boolean sort);

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
        public void onSearchKeyWord(String keyword) {

        }

        @Override
        public void onShowCardList(List<Card> cardList, boolean sort) {

        }

        @Override
        public void onAddSideCard(Card cardInfo) {

        }

        @Override
        public void onAddMainCard(Card cardInfo) {

        }
    }

    protected CardDetailBase(View itemView) {
        super(itemView);
    }
}

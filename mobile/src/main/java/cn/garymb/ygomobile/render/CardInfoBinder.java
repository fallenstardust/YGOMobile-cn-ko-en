package cn.garymb.ygomobile.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.game.CardSearcherManager;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.dialogs.KeywordCardListDialog;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/**
 * 卡片详情绑定器（自 CardDetailPanel 拆分，逻辑零改）：
 * 卡图/卡名/字段/属性/等级/描述的视图绑定与显示状态（currentCardCode/coverBitmap）归本类管理，
 * 详情控件由 CardDetailPanel.bindViews() 经 attachViews 注入；面板留显隐协调与按钮域。
 */
class CardInfoBinder {

    private ImageLoader imageLoader;

    private LinearLayout layout;
    private LinearLayout panelRoot;
    private ImageView ivCardImage;
    private TextView tvCardName, tvCardSetname, tvCardAttr, tvCardLevel, tvCardDesc;
    private ScrollView svCardDesc;

    private int currentCardCode = -1;
    private Bitmap coverBitmap;
    private StringManager mStringManager = DataManager.get().getStringManager();

    // 关键词卡片列表弹窗支撑（详情描述「」/""高亮点击后在 layout_game_right 最左侧停靠显示命中列表）：
    // context/anchor 由 attachViews 注入，currentDisplayCard 用于 queryable 判定（唯一命中即自身则不可点）
    private Context context;
    private View anchor;
    private Card currentDisplayCard;
    private KeywordCardListDialog keywordDialog;
    /**
     * 卡组编辑模式下的关键词导航器：非空时点击高亮词不再弹 {@link KeywordCardListDialog}，
     * 而是把关键词交由此处理器（由 {@code DeckEditorManager} 注入为填入卡组搜索框并搜索，
     * 结果显示在卡组编辑搜索结果列表上）。进入卡组编辑时注入、退出时置空恢复决斗态弹窗行为。
     */
    private KeywordNavigator deckKeywordNavigator;

    /** 关键词点击导航回调（卡组编辑模式复用卡详高亮，点击直接作用到卡组搜索列表） */
    public interface KeywordNavigator {
        void onKeywordClick(String keyword);
    }

    /** 由 CardDetailPanel 转发 DeckEditorManager 的注入：非空即视为处于卡组编辑模式 */
    public void setDeckKeywordNavigator(KeywordNavigator navigator) {
        this.deckKeywordNavigator = navigator;
    }

    /** bindViews() 重绑新视图树后注入详情控件（旋转重建场景）；anchor 为关键词卡片列表停靠的 layout_game_right */
    void attachViews(LinearLayout layout, LinearLayout panelRoot, ImageView ivCardImage,
                     TextView tvCardName, TextView tvCardSetname, TextView tvCardAttr,
                     TextView tvCardLevel, TextView tvCardDesc, ScrollView svCardDesc, View anchor) {
        this.layout = layout;
        this.panelRoot = panelRoot;
        this.ivCardImage = ivCardImage;
        this.tvCardName = tvCardName;
        this.tvCardSetname = tvCardSetname;
        this.tvCardAttr = tvCardAttr;
        this.tvCardLevel = tvCardLevel;
        this.tvCardDesc = tvCardDesc;
        this.svCardDesc = svCardDesc;
        this.anchor = anchor;
        this.context = layout != null ? layout.getContext()
                : (panelRoot != null ? panelRoot.getContext() : null);
        // 关键词卡片列表弹窗已改用 DraggablePopupHelper：旋转重排/重居中由其 relayoutActivePopupsForOrientation
        //（YGOProActivity 旋转重建末尾统一调用）按 dialogId 处理，本处无需再手动重锚。
    }

    void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    int getCurrentCardCode() {
        return currentCardCode;
    }

    void setCurrentCardCode(int code) {
        currentCardCode = code;
    }

    /** 面板隐藏时由 CardDetailPanel.hide() 调用：收起关键词卡片列表弹窗并清理当前卡引用 */
    void dismissKeywordListDialog() {
        dismissKeywordDialog();
        currentDisplayCard = null;
    }

    void showCardInfo(GameField.ClientCard card) {
        // 移除 code<=0 检查：对于 CardSelectDialog 中的已知 code 的暗卡（里侧怪兽、卡组表侧等），
        // 只要能读取到卡片图案就应该显示详细信息，而不是当作未知卡处理
        if (card == null) return;
        showCard(card);
    }

    void showDefault() {
        currentCardCode = -1;
        currentDisplayCard = null;
        // 不在此收起关键词卡片列表弹窗：旋转重建流程会先经 onGameUIShown→showDefault 再回显，
        // 若此处 dismiss 会使命中列表在横竖屏切换时消失（用户要求切换方向不隐藏）。
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);
        if (ivCardImage != null) {
            Bitmap cover = getCoverBitmap();
            if (cover != null) {
                ivCardImage.setImageBitmap(cover);
            } else {
                ivCardImage.setImageResource(R.drawable.unknown);
            }
        }
        if (tvCardName != null) {
            tvCardName.setText("");
        }
        if (tvCardSetname != null) {
            tvCardSetname.setText("");
            tvCardSetname.setVisibility(View.GONE);
        }
        if (tvCardAttr != null) {
            tvCardAttr.setText("");
        }
        if (tvCardLevel != null) {
            tvCardLevel.setText("");
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText("");
        }
    }

    void showCard(GameField.ClientCard clientCard) {
        // code<=0 视为未知/暗卡：对齐 event_handler.cpp L1130-1133 ClearCardInfo 显示卡背，
        // 而不是走 getCard(0) 失败后误报「???」；unknown 仅保留给 code>0 但卡表查无此卡
        if (clientCard == null || clientCard.code <= 0) {
            showDefault();
            return;
        }

        int code = clientCard.code;
        Card cardData = DataManager.get().getCardManager().getCard(code);
        if (cardData == null) {
            showUnknownCard();
            return;
        }

        currentCardCode = code;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);

        bindCardImage(code);
        bindCardName(cardData, code);
        bindCardSetname(cardData);
        bindCardAttr(cardData);
        // 详情面板只显示卡表原始数据（对齐 game.cpp Game::ShowCardInfo(int code) 全部取 cd 原值），
        // 通讯修改后的等级/属性/种族/攻守/刻度一律不进详情，差异在长按标签中显示
        bindCardLevel(cardData);
        bindCardDesc(cardData);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    void showCard(Card card) {
        if (card == null || card.Code <= 0) {
            showUnknownCard();
            return;
        }

        //卡图使用card.Code对应的文件名id（异画卡显示自己的卡图，不受RealCode影响）
        int imageCode = card.Code;
        currentCardCode = imageCode;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);

        bindCardImage(imageCode);
        bindCardNameByGameCode(card);
        bindCardSetname(card);
        bindCardAttr(card);
        bindCardLevel(card);
        bindCardDesc(card);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    /**
     * 旋转重建后回显卡片详情（用户规格：横竖屏切换前若详情正在显示，切换后继续显示同一张卡，
     * 不重置为隐藏态）。在 bindViews() 重绑新视图树之后调用；本方法在 onGameUIShown()
     * 之后执行，而其内部的 showDefault() 已把 currentCardCode 清零，故卡码以重建前
     * 快照 savedCode 为准（<=0 时回退到实例字段）。
     *
     * @param wasShowingBeforeRebind 重建前详情栏是否可见（旧视图树上采集）
     * @param savedCode              重建前正在展示的卡码（无则 <=0）
     */
    void restoreAfterRebind(boolean wasShowingBeforeRebind, int savedCode) {
        if (!wasShowingBeforeRebind) return;
        int code = savedCode > 0 ? savedCode : currentCardCode;
        if (code > 0) {
            Card cardData = DataManager.get().getCardManager().getCard(code);
            if (cardData != null) {
                showCard(cardData);
            } else {
                showUnknownCard();
            }
        } else {
            showDefault();
        }
    }

    void showUnknownCard() {
        currentCardCode = -1;
        currentDisplayCard = null;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);
        if (ivCardImage != null) {
            ivCardImage.setImageResource(R.drawable.unknown);
        }
        if (tvCardName != null) {
            tvCardName.setText("???");
        }
        if (tvCardSetname != null) {
            tvCardSetname.setText("");
            tvCardSetname.setVisibility(View.GONE);
        }
        if (tvCardAttr != null) {
            tvCardAttr.setText("");
        }
        if (tvCardLevel != null) {
            tvCardLevel.setText("");
            tvCardLevel.setVisibility(View.GONE);
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText(R.string.tip_card_info_diff);
        }
        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    private void bindCardImage(int code) {
        if (imageLoader != null && ivCardImage != null) {
            imageLoader.bindImage(ivCardImage, code, ImageLoader.Type.origin);
        }
    }

    private void bindCardName(Card cardData, int code) {
        if (tvCardName == null) return;
        String name = cardData.Name;
        if (name == null || name.isEmpty()) name = "Unknown Card";
        tvCardName.setText(name + "[" + code + "]");
    }

    //卡名根据getGameCode()判断显示（规则同名卡显示本家卡名），卡图仍使用card.Code
    private void bindCardNameByGameCode(Card cardData) {
        if (tvCardName == null) return;
        int gameCode = cardData.getGameCode();
        Card gameCard = DataManager.get().getCardManager().getCard(gameCode);
        String name = (gameCard != null && gameCard.Name != null && !gameCard.Name.isEmpty())
                ? gameCard.Name : cardData.Name;
        if (name == null || name.isEmpty()) name = "Unknown Card";
        tvCardName.setText(name + "[" + gameCode + "]");
    }

    private void bindCardSetname(Card cardData) {
        if (tvCardSetname == null) return;
        mStringManager = DataManager.get().getStringManager();
        long[] setCodes = cardData.getSetCode();
        StringBuilder sb = new StringBuilder();
        boolean hasSet = false;
        for (long sc : setCodes) {
            if (sc == 0) continue;
            if (hasSet) sb.append("|");
            sb.append(mStringManager.getSetName(sc));
            hasSet = true;
        }
        if (hasSet) {
            tvCardSetname.setText("字段：" + sb);
            tvCardSetname.setVisibility(View.VISIBLE);
        } else {
            tvCardSetname.setVisibility(View.GONE);
        }
    }

    private void bindCardAttr(Card cardData) {
        if (tvCardAttr == null) return;
        mStringManager = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder();

        String typeStr = CardUtils.getAllTypeString(cardData, mStringManager).replace("/", "|");
        sb.append("[").append(typeStr).append("]");

        if (cardData.isType(CardType.Monster)) {
            String raceStr = mStringManager.getRaceString(cardData.Race);
            String attrStr = mStringManager.getAttributeString(cardData.Attribute);
            sb.append(" ").append(raceStr).append("/").append(attrStr);
        }

        tvCardAttr.setText(sb.toString());
    }

    private void bindCardLevel(Card cardData) {
        if (tvCardLevel == null) return;

        if (cardData.isType(CardType.Spell) || cardData.isType(CardType.Trap)) {
            tvCardLevel.setText("");
            tvCardLevel.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (cardData.isLink()) {
            sb.append("LINK-").append(cardData.getLinkNumber());
        } else if (cardData.isType(CardType.Xyz)) {
            sb.append("☆").append(cardData.getStar());
        } else if (cardData.isType(CardType.Monster)) {
            sb.append("★").append(cardData.getStar());
        }

        if (cardData.isType(CardType.Monster)) {
            int atk = cardData.Attack;
            int def = cardData.Defense;
            String atkStr = atk < 0 ? "?" : String.valueOf(atk);
            String defStr = cardData.isLink() ? "-" : (def < 0 ? "?" : String.valueOf(def));
            if (sb.length() > 0) sb.append("  ");
            sb.append(atkStr).append("/").append(defStr);
        }

        if (cardData.LeftScale > 0 || cardData.RightScale > 0) {
            int lsc = cardData.LeftScale;
            int rsc = cardData.RightScale;
            if (sb.length() > 0) sb.append("  ");
            sb.append("灵摆 ").append(lsc).append("/").append(rsc);
        }

        tvCardLevel.setText(sb.toString());
        tvCardLevel.setVisibility(View.VISIBLE);
    }

    private void bindCardDesc(Card cardData) {
        if (tvCardDesc == null) return;
        // 记录当前展示的卡，供关键词 queryable 判定（唯一命中即当前卡自身时视为不可查）
        currentDisplayCard = cardData;
        String desc = cardData.Desc;
        if (desc == null || desc.isEmpty()) {
            tvCardDesc.setText("");
        } else {
            setHighlightTextWithClickableSpans(desc);
        }
    }

    /**
     * 将描述中「」与 "" 之间的文本渲染为高亮可点击文字（对齐 CardDetail.setHighlightTextWithClickableSpans）：
     * 命中卡集合可查（多于一个、或唯一命中非当前卡）→ holo_blue_bright 蓝色带下划线，点击在
     * layout_game_right 最左侧停靠弹出关键词卡片列表；不可查 → 白色带下划线，点击仅提示已到结尾。
     */
    private void setHighlightTextWithClickableSpans(String text) {
        SpannableString spannableString = new SpannableString(text);
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
                            applySpan(spannableString, start, i, quotedText,
                                    KeywordCardListDialog.isQueryable(quotedText, currentDisplayCard)
                                            ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            currentQuoteType = QuoteType.NONE;
                        }
                    }
                    break;
                case DOUBLE_QUOTE:
                    if (c == '"' && !stack.isEmpty()) {
                        stack.pop();
                        if (stack.isEmpty()) {
                            String quotedText = text.substring(start, i).trim();
                            applySpan(spannableString, start, i, quotedText,
                                    KeywordCardListDialog.isQueryable(quotedText, currentDisplayCard)
                                            ? YGOUtil.c(R.color.holo_blue_bright) : Color.WHITE);
                            currentQuoteType = QuoteType.NONE;
                        } else {
                            stack.push(i);
                        }
                    }
                    break;
            }
        }
        tvCardDesc.setText(spannableString);
        tvCardDesc.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /** 为一段引用文本着色并挂点击：蓝色→点击弹出关键词卡片列表，白色→点击提示已到结尾 */
    private void applySpan(SpannableString spannableString, int start, int end, String keyword, int color) {
        spannableString.setSpan(new ForegroundColorSpan(color), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                if (color != Color.WHITE) {
                    openKeywordList(keyword);
                } else if (context != null) {
                    YGOUtil.showTextToast(context.getString(R.string.searchresult) + context.getString(R.string.already_end));
                }
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setUnderlineText(true);
            }
        }, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** 在 layout_game_right 最左侧停靠显示命中该关键词的卡片纵向列表（复用卡组编辑搜索结果同款 adapter） */
    private void openKeywordList(String keyword) {
        // 卡组编辑模式：点高亮词直接作用到卡组搜索列表（填入检索词并搜索），不弹关键词卡片列表弹窗
        if (deckKeywordNavigator != null) {
            deckKeywordNavigator.onKeywordClick(keyword);
            return;
        }
        if (context == null || anchor == null) return;
        List<Card> cards = KeywordCardListDialog.queryCardsByKeyword(keyword);
        // 排序与卡组编辑搜索结果同源：怪兽(通常→效果→仪式→融合→同调→超量→连接)→魔法→陷阱，
        // 复用 CardSearcherManager 默认比较器，使玩家对两处排序逻辑有一致的亲和力
        Collections.sort(cards, CardSearcherManager.defaultSearchComparator());
        if (keywordDialog != null) keywordDialog.dismiss();
        // 条目点击刷新左侧详情面板（this::showCard），弹窗保持打开以便继续链式点关键词
        keywordDialog = new KeywordCardListDialog(context, imageLoader, anchor, cards, keyword, this::showCard);
        keywordDialog.show();
    }

    /** 关闭关键词卡片列表弹窗（切换/隐藏卡详时调用，避免残留遮挡） */
    private void dismissKeywordDialog() {
        if (keywordDialog != null) {
            keywordDialog.dismiss();
            keywordDialog = null;
        }
    }

    // 引用文本解析状态（对齐 CardDetail.QuoteType）
    private enum QuoteType {NONE, DOUBLE_QUOTE, ANGLE_QUOTE}

    private Bitmap getCoverBitmap() {
        if (coverBitmap == null || coverBitmap.isRecycled()) {
            File coverFile = new File(AppsSettings.get().getCoreSkinPath(), "cover.jpg");
            if (coverFile.exists()) {
                coverBitmap = BitmapFactory.decodeFile(coverFile.getAbsolutePath());
            }
        }
        return coverBitmap;
    }
}

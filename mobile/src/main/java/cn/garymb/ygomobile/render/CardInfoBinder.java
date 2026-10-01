package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.CardUtils;
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

    /** bindViews() 重绑新视图树后注入详情控件（旋转重建场景） */
    void attachViews(LinearLayout layout, LinearLayout panelRoot, ImageView ivCardImage,
                     TextView tvCardName, TextView tvCardSetname, TextView tvCardAttr,
                     TextView tvCardLevel, TextView tvCardDesc, ScrollView svCardDesc) {
        this.layout = layout;
        this.panelRoot = panelRoot;
        this.ivCardImage = ivCardImage;
        this.tvCardName = tvCardName;
        this.tvCardSetname = tvCardSetname;
        this.tvCardAttr = tvCardAttr;
        this.tvCardLevel = tvCardLevel;
        this.tvCardDesc = tvCardDesc;
        this.svCardDesc = svCardDesc;
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

    void showCardInfo(GameField.ClientCard card) {
        // 移除 code<=0 检查：对于 CardSelectDialog 中的已知 code 的暗卡（里侧怪兽、卡组表侧等），
        // 只要能读取到卡片图案就应该显示详细信息，而不是当作未知卡处理
        if (card == null) return;
        showCard(card);
    }

    void showDefault() {
        currentCardCode = -1;
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
        String desc = cardData.Desc;
        if (desc == null || desc.isEmpty()) {
            tvCardDesc.setText("");
        } else {
            tvCardDesc.setText(desc);
        }
    }

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

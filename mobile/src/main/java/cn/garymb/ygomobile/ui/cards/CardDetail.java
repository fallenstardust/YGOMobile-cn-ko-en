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

/***
 * 卡片详情
 */
public class CardDetail extends CardDetailMid {

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
        // 等级/阶级/连接箭头显示
        icon_star = findViewById(R.id.icon_star);
        level = findViewById(R.id.card_level);
        linkArrow = findViewById(R.id.detail_link_arrows);
        icon = findViewById(R.id.icon);

        type = findViewById(R.id.card_type);
        faq = findViewById(R.id.btn_faq);
        cardAtk = findViewById(R.id.card_atk);
        cardDef = findViewById(R.id.card_def);
        atkdefView = findViewById(R.id.layout_atkdef2);
        mImageFav = findViewById(R.id.image_fav);

        monsterLayout = findViewById(R.id.layout_monster);
        layoutDetailPScale = findViewById(R.id.detail_p_scale);
        detailCardScale = findViewById(R.id.detail_cardscale);
        // 属性
        icon_attribute = findViewById(R.id.icon_attribute);
        attrView = findViewById(R.id.card_attribute);
        // 种族
        icon_race = findViewById(R.id.icon_race);
        race = findViewById(R.id.card_race);
        setName = findViewById(R.id.card_setname);
        addMain = findViewById(R.id.btn_add_main);
        addSide = findViewById(R.id.btn_add_side);
        otView = findViewById(R.id.card_ot);

        lbSetCode = findViewById(R.id.label_setcode);
        cardManager = DataManager.get().getCardManager();
        packManager = DataManager.get().getPackManager();
        cardLoader = new CardLoader();
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
                mListener.onShowCardList(relatedCards(cardInfo), true);
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
    public void bind(Card cardInfo, final int position, final CardListProvider provider) {
        curPosition = position;
        mProvider = provider;
        if (cardInfo != null) {
            setCardInfo(cardInfo, view);
        }
    }
    public void onPreCard() {
        int position = getCurPosition();
        CardListProvider provider = getProvider();
        int cardsCount = provider.getCardsCount();
        Log.w("cc onPreCard", position + "/" + cardsCount);
        // 如果已经在顶部，显示提示并返回
        if (position <= 0) {
            YGOUtil.showTextToast(R.string.already_top, Toast.LENGTH_SHORT);
            return;
        }
        // 向前查找有效卡片
        for (int index = position - 1; index >= 0; index--) {
            //当进行高亮词、关联卡片查询到的结果比原列表少时，进行以下判断处理，避免index溢出错误
            if (index > cardsCount) {
                index = 0;
                position = cardsCount - 1;
            }
            Card card = provider.getCard(index);
            if (card != null && card.Name != null && !provider.getCard(position).Name.equals(card.Name)) {
                bind(card, index, provider);
                return;
            }
        }
        // 如果没有找到合适的前一张卡片（所有卡片名称相同或为null），显示提示
        YGOUtil.showTextToast(R.string.already_top, Toast.LENGTH_SHORT);
    }

    public void onNextCard() {
        int position = getCurPosition();
        CardListProvider provider = getProvider();
        int cardsCount = provider.getCardsCount();
        // 如果已经在底部，显示提示并返回
        if (position >= cardsCount - 1) {
            YGOUtil.showTextToast(R.string.already_end, Toast.LENGTH_SHORT);
            return;
        }
        // 向后查找有效卡片
        for (int index = position + 1; index < cardsCount; index++) {
            Card card = provider.getCard(index);
            if (card != null && card.Name != null && !provider.getCard(position).Name.equals(card.Name)) {
                bind(card, index, provider);
                return;
            }
        }
        // 如果没有找到合适的下一张卡片（所有卡片名称相同或为null），显示提示
        YGOUtil.showTextToast(R.string.already_end, Toast.LENGTH_SHORT);
    }
}

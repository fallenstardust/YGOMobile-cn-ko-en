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
 * Middle layer of the card detail split chain (CardDetailBase <-
 * CardDetailMid <- CardDetail). Owns the full card-info rendering into
 * the bound views, the full-size card image dialog and the expansion
 * image download flow.
 */
abstract class CardDetailMid extends CardDetailBase {

    protected void setCardInfo(Card cardInfo, View view) {
        if (cardInfo == null) return;
        mCardInfo = cardInfo;
        imageLoader.bindImage(cardImage, cardInfo, ImageLoader.Type.middle);
        dialog = DialogUtils.getdx(context);
        cardImage.setOnClickListener((v) -> {
            showCardImageDetail(cardInfo.Code);
        });
        packName.setText(packManager.findPackNameById((cardInfo.getCode())));

        if (mCurrentSearchKeyword != null && !mCurrentSearchKeyword.isEmpty()) {
            name.setText(highlightKeywordBackground(cardInfo.Name, mCurrentSearchKeyword));
            String descText = cardInfo.Name.equals("Unknown") ? context.getString(R.string.tip_card_info_diff) : cardInfo.Desc;
            desc.setText(applyBothHighlights(descText, mCurrentSearchKeyword));
        } else {
            name.setText(cardInfo.Name);
            setHighlightTextWithClickableSpans(cardInfo.Name.equals("Unknown") ? context.getString(R.string.tip_card_info_diff) : cardInfo.Desc);
        }

        if (!mHideRelatedButton) {
            btn_related.setVisibility(relatable(cardInfo) ? View.VISIBLE : View.INVISIBLE);
        }
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

        // 属性
        if (cardInfo.Attribute == CardAttribute.Dark.getId())
            icon_attribute.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dark.png", 0, 0));
        if (cardInfo.Attribute == CardAttribute.Light.getId())
            icon_attribute.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "light.png", 0, 0));
        if (cardInfo.Attribute == CardAttribute.Earth.getId())
            icon_attribute.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "earth.png", 0, 0));
        if (cardInfo.Attribute == CardAttribute.Wind.getId())
            icon_attribute.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "wind.png", 0, 0));
        if (cardInfo.Attribute == CardAttribute.Water.getId())
            icon_attribute.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "water.png", 0, 0));
        if (cardInfo.Attribute == CardAttribute.Fire.getId())
            icon_attribute.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fire.png", 0, 0));
        if (cardInfo.Attribute == CardAttribute.Divine.getId())
            icon_attribute.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "divine.png", 0, 0));
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
                setName.append(mStringManager.getSetName(set));
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
            // 种族
            icon_race.setVisibility(View.VISIBLE);
            race.setVisibility(View.VISIBLE);

            level.setText(String.valueOf(cardInfo.getStar()));
            if (cardInfo.isType(CardType.Xyz)) {
                icon_star.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "star_rank.png", 0, 0));
                level.setTextColor(YGOUtil.c(R.color.star_rank));
            } else {
                icon_star.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "star_level.png", 0, 0));
                level.setTextColor(YGOUtil.c(R.color.star));
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
                icon_star.setVisibility(View.GONE);
                level.setVisibility(View.GONE);
                linkArrow.setVisibility(View.VISIBLE);
                cardDef.setText((cardInfo.getStar() < 0 ? "?" : "LINK-" + cardInfo.getStar()));
                BaseActivity.showLinkArrows(cardInfo, view);
            } else {
                icon_star.setVisibility(View.VISIBLE);
                level.setVisibility(View.VISIBLE);
                linkArrow.setVisibility(View.GONE);
                cardDef.setText((cardInfo.Defense < 0 ? "?" : String.valueOf(cardInfo.Defense)));
            }
            // 种族
            if (cardInfo.Race == CardRace.Warrior.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "warrior.png", 0, 0));
            if (cardInfo.Race == CardRace.SpellCaster.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "spellcaster.png", 0, 0));
            if (cardInfo.Race == CardRace.Fairy.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fairy.png", 0, 0));
            if (cardInfo.Race == CardRace.Fiend.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fiend.png", 0, 0));
            if (cardInfo.Race == CardRace.Zombie.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "zombie.png", 0, 0));
            if (cardInfo.Race == CardRace.Machine.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "machine.png", 0, 0));
            if (cardInfo.Race == CardRace.Aqua.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "aqua.png", 0, 0));
            if (cardInfo.Race == CardRace.Pyro.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "pyro.png", 0, 0));
            if (cardInfo.Race == CardRace.Rock.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "rock.png", 0, 0));
            if (cardInfo.Race == CardRace.WingedBeast.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "winged_beast.png", 0, 0));
            if (cardInfo.Race == CardRace.Plant.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "plant.png", 0, 0));
            if (cardInfo.Race == CardRace.Insect.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "insect.png", 0, 0));
            if (cardInfo.Race == CardRace.Thunder.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "thunder.png", 0, 0));
            if (cardInfo.Race == CardRace.Dragon.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dragon.png", 0, 0));
            if (cardInfo.Race == CardRace.Beast.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "beast.png", 0, 0));
            if (cardInfo.Race == CardRace.BeastWarrior.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "beast_warrior.png", 0, 0));
            if (cardInfo.Race == CardRace.Dinosaur.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "dinosaur.png", 0, 0));
            if (cardInfo.Race == CardRace.Fish.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "fish.png", 0, 0));
            if (cardInfo.Race == CardRace.SeaSerpent.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "sea_serpent.png", 0, 0));
            if (cardInfo.Race == CardRace.Reptile.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "reptile.png", 0, 0));
            if (cardInfo.Race == CardRace.Psychic.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "psychic.png", 0, 0));
            if (cardInfo.Race == CardRace.DivineBeast.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "divine_beast.png", 0, 0));
            if (cardInfo.Race == CardRace.CreatorGod.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "creator_god.png", 0, 0));
            if (cardInfo.Race == CardRace.Wyrm.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "wyrm.png", 0, 0));
            if (cardInfo.Race == CardRace.Cyberse.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cyberse.png", 0, 0));
            if (cardInfo.Race == CardRace.Illusion.value())
                icon_race.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "illusion.png", 0, 0));
            race.setText(mStringManager.getRaceString(cardInfo.Race));
            icon.setVisibility(View.GONE);
        } else {
            icon.setVisibility(View.VISIBLE);
            icon_star.setVisibility(View.VISIBLE);
            if (cardInfo.isType(CardType.Spell))
                icon_star.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_spell.png", 0, 0));
            if (cardInfo.isType(CardType.Trap))
                icon_star.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "cardType_trap.png", 0, 0));
            if (cardInfo.onlyType(CardType.Spell)) {
                icon.setImageBitmap(null);
            } else if (cardInfo.onlyType(CardType.Trap)) {
                icon.setImageBitmap(null);
            }
            if (cardInfo.isType(CardType.QuickPlay))
                icon.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "quickplay.png", 0, 0));
            if (cardInfo.isType(CardType.Continuous))
                icon.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "continuous.png", 0, 0));
            if (cardInfo.isType(CardType.Equip))
                icon.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "equip.png", 0, 0));
            if (cardInfo.isType(CardType.Field))
                icon.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "field.png", 0, 0));
            if (cardInfo.isType(CardType.Counter))
                icon.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "counter.png", 0, 0));
            if (cardInfo.isType(CardType.Ritual))
                icon.setImageBitmap(BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "ritual.png", 0, 0));

            atkdefView.setVisibility(View.GONE);
            icon_race.setVisibility(View.GONE);
            race.setVisibility(View.GONE);
            monsterLayout.setVisibility(View.GONE);
            level.setVisibility(View.GONE);
            linkArrow.setVisibility(View.GONE);
        }
    }
    protected void showCardImageDetail(int code) {
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

                    // 构造分享意图
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("image/jpeg");

                    // 使用 FileProvider 生成安全 URI
                    Uri imageUri = FileProvider.getUriForFile(context, context.getPackageName() + ".gamefiles",
                            new File(AppsSettings.get().getCardExpansionsImagePath(code)));// 图片文件路径
                    intent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // 授予读取权限

                    try {
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.screenshoot)));
                    } catch (Throwable e) {
                        YGOUtil.showTextToast("dev error:" + e, Toast.LENGTH_LONG);
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
    protected void downloadCardImage(int code, boolean force) {
        if (String.valueOf(Math.abs(code)).length() >= 9) {
            YGOUtil.showTextToast(context.getString(R.string.tip_expansions_image));
            return;
        }
        File imgFile = new File(AppsSettings.get().getCardExpansionsImagePath(code));
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

    protected CardDetailMid(View itemView) {
        super(itemView);
    }
}

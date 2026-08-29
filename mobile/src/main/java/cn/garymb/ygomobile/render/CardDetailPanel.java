package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.utils.CardUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/**
 * 左侧卡片详情面板与全部控制按钮的管理类：
 * 卡片详情展示 / 左列功能按钮 / 底部行动按钮 / 录像控制按钮 / 取消或完成按钮 / 卡组操作栏
 */
public class CardDetailPanel {

    private final YGOProActivity activity;
    private ImageLoader imageLoader;

    private LinearLayout layout;
    private ImageView ivCardImage;
    private TextView tvCardName, tvCardSetname, tvCardAttr, tvCardLevel, tvCardDesc;
    private ScrollView svCardDesc;

    private ImageButton btnSettings, btnChat, btnSound, btnSpeed, btnEmote, btnNote;

    private LinearLayout layoutBottomActions;
    private Button btnSurrender, btnIgnoreTiming, btnShowTiming, btnAvailableTiming;
    private Button btnCancelOrFinish;

    private LinearLayout layoutReplayControl;
    private Button btnReplayPlay, btnReplayPause, btnReplayNext, btnReplayLast, btnReplayShuffle, btnReplayQuit;
    private LinearLayout layoutDeckControl;

    private int currentCardCode = -1;
    private Bitmap coverBitmap;
    private StringManager mStringManager = DataManager.get().getStringManager();

    // 选择上下文（cancelOrFinish 决策所需，由 YGOProActivity 注册同步）
    private int currentSelectType = -1;
    private YesOrNoDialog currentDialog;
    private CardSelectDialog cardSelectDialog;
    private CardDisplayDialog cardDisplayDialog;

    public CardDetailPanel(YGOProActivity activity) {
        this.activity = activity;
    }

    public void bindViews() {
        layout = activity.findViewById(R.id.layout_card_detail);
        ivCardImage = activity.findViewById(R.id.iv_card_image);
        tvCardName = activity.findViewById(R.id.tv_card_name);
        tvCardSetname = activity.findViewById(R.id.tv_card_setname);
        tvCardAttr = activity.findViewById(R.id.tv_card_attr);
        tvCardLevel = activity.findViewById(R.id.tv_card_level);
        tvCardDesc = activity.findViewById(R.id.tv_card_desc);
        svCardDesc = activity.findViewById(R.id.sv_card_desc);

        btnSettings = activity.findViewById(R.id.btn_settings);
        btnChat = activity.findViewById(R.id.btn_chat);
        btnSound = activity.findViewById(R.id.btn_sound);
        btnSpeed = activity.findViewById(R.id.btn_speed);
        btnEmote = activity.findViewById(R.id.btn_emote);
        btnNote = activity.findViewById(R.id.btn_note);

        layoutBottomActions = activity.findViewById(R.id.layout_bottom_actions);
        btnSurrender = activity.findViewById(R.id.btn_surrender);
        btnIgnoreTiming = activity.findViewById(R.id.btn_ignore_timing);
        btnShowTiming = activity.findViewById(R.id.btn_show_timing);
        btnAvailableTiming = activity.findViewById(R.id.btn_available_timing);
        btnCancelOrFinish = activity.findViewById(R.id.btn_cancel_or_finish);

        layoutReplayControl = activity.findViewById(R.id.layout_replay_control);
        btnReplayPlay = activity.findViewById(R.id.btn_replay_play);
        btnReplayPause = activity.findViewById(R.id.btn_replay_pause);
        btnReplayNext = activity.findViewById(R.id.btn_replay_next);
        btnReplayLast = activity.findViewById(R.id.btn_replay_last);
        btnReplayShuffle = activity.findViewById(R.id.btn_replay_shuffle);
        btnReplayQuit = activity.findViewById(R.id.btn_replay_quit);
        layoutDeckControl = activity.findViewById(R.id.layout_deck_control);

        setupListeners();
    }

    public void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    private void setupListeners() {
        btnSurrender.setOnClickListener(v -> {
            if (activity.getEngine() != null) activity.getEngine().sendSurrender();
        });
        btnIgnoreTiming.setOnClickListener(v -> activity.sendResponseInt(-1));
        btnShowTiming.setOnClickListener(v -> {
        });
        btnAvailableTiming.setOnClickListener(v -> {
        });
        btnSettings.setOnClickListener(v -> activity.showSettingsDialog());
        btnChat.setOnClickListener(v -> activity.toggleChatInput());
        btnSound.setOnClickListener(v -> activity.toggleSoundMute());
        btnSpeed.setOnClickListener(v -> {
        });
        // 表情入口（对齐 gframe BUTTON_EMOTICON）：开关切换 4x4 表情面板
        btnEmote.setOnClickListener(v -> activity.toggleEmotionDialog(btnEmote));
        btnNote.setOnClickListener(v -> activity.getMainMenuDialog().showMainMenu());

        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setOnClickListener(v -> cancelOrFinish());
        }

        if (btnReplayPlay != null) {
            btnReplayPlay.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.resume();
            });
        }
        if (btnReplayPause != null) {
            btnReplayPause.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.pause();
            });
        }
        if (btnReplayNext != null) {
            btnReplayNext.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.skipAhead();
            });
        }
        if (btnReplayLast != null) {
            btnReplayLast.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.undo();
            });
        }
        if (btnReplayShuffle != null) {
            btnReplayShuffle.setOnClickListener(v -> {
                ReplayEngine re = activity.getCurrentReplayEngine();
                if (re != null) re.swapField();
            });
        }
        if (btnReplayQuit != null) {
            btnReplayQuit.setOnClickListener(v -> activity.quitReplay());
        }
    }

    // === 卡片详情面板 ===

    public void showCardInfo(GameField.ClientCard card) {
        if (card == null || card.code <= 0) return;
        showCard(card);
    }

    public void showDefault() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
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

    public void showCard(GameField.ClientCard clientCard) {
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

        bindCardImage(code);
        bindCardName(cardData, code);
        bindCardSetname(cardData);
        bindCardAttr(cardData);
        bindCardLevel(cardData, clientCard);
        bindCardDesc(cardData);

        if (svCardDesc != null) {
            svCardDesc.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    public void showCard(Card card) {
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

    public void hide() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.GONE);
        }
    }

    public boolean isShowing() {
        return layout != null && layout.getVisibility() == View.VISIBLE;
    }

    private void showUnknownCard() {
        currentCardCode = -1;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
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

    public int getCurrentCardCode() {
        return currentCardCode;
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

    private void bindCardLevel(Card cardData, GameField.ClientCard clientCard) {
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
            int rank = clientCard.rank > 0 ? clientCard.rank : cardData.getStar();
            sb.append("☆").append(rank);
        } else if (cardData.isType(CardType.Monster)) {
            int level = clientCard.level > 0 ? clientCard.level : cardData.getStar();
            sb.append("★").append(level);
        }

        if (cardData.isType(CardType.Monster)) {
            int atk = clientCard.isFaceUp() ? clientCard.attack : cardData.Attack;
            int def = clientCard.isFaceUp() ? clientCard.defense : cardData.Defense;
            String atkStr = atk < 0 ? "?" : String.valueOf(atk);
            String defStr = cardData.isLink() ? "-" : (def < 0 ? "?" : String.valueOf(def));
            if (sb.length() > 0) sb.append("  ");
            sb.append(atkStr).append("/").append(defStr);
        }

        if (cardData.LeftScale > 0 || cardData.RightScale > 0) {
            int lsc = clientCard.lScale > 0 ? clientCard.lScale : cardData.LeftScale;
            int rsc = clientCard.rScale > 0 ? clientCard.rScale : cardData.RightScale;
            if (sb.length() > 0) sb.append("  ");
            sb.append("灵摆 ").append(lsc).append("/").append(rsc);
        }

        tvCardLevel.setText(sb.toString());
        tvCardLevel.setVisibility(View.VISIBLE);
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

    public void closeGameButtons() {
        hideCancelOrFinishButton();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
    }

    // === 取消或完成按钮 (对应 C++ ClientField::CancelOrFinish) ===

    // 选择上下文注册（由 YGOProActivity 在创建/关闭选择对话框时同步）
    public void setSelectType(int selectType) {
        this.currentSelectType = selectType;
    }

    public int getSelectType() {
        return currentSelectType;
    }

    public void setCurrentDialog(YesOrNoDialog dialog) {
        this.currentDialog = dialog;
    }

    public void setCardSelectDialog(CardSelectDialog dialog) {
        this.cardSelectDialog = dialog;
    }

    public void setCardDisplayDialog(CardDisplayDialog dialog) {
        this.cardDisplayDialog = dialog;
    }

    public void showCancelOrFinishButton(String text) {
        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setText(text);
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        }
    }

    public void hideCancelOrFinishButton() {
        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setVisibility(View.GONE);
        }
    }

    public void updateCancelOrFinishButton(boolean ready, boolean cancelable, boolean hasSelection) {
        if (btnCancelOrFinish == null) return;
        if (ready) {
            btnCancelOrFinish.setText("完成选择");
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        } else if (cancelable && !hasSelection) {
            btnCancelOrFinish.setText("取消");
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        } else {
            btnCancelOrFinish.setVisibility(View.GONE);
        }
    }

    // 对齐 C++ ClientField::CancelOrFinish：按当前选择类型执行完成/取消
    public void cancelOrFinish() {
        switch (currentSelectType) {
            case 13:
            case 12: {
                activity.sendResponseInt(0);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 15:
            case 20: {
                if (cardSelectDialog != null) {
                    if (cardSelectDialog.getSelectedCount() >= cardSelectDialog.getMinSelect()) {
                        cardSelectDialog.confirm();
                    } else if (cardSelectDialog.isCancelable() && cardSelectDialog.getSelectedCount() == 0) {
                        activity.sendResponseInt(-1);
                        hideCancelOrFinishButton();
                        cardSelectDialog.dismiss();
                    }
                }
                break;
            }
            case 23: {
                if (cardSelectDialog != null && cardSelectDialog.isReady()) {
                    cardSelectDialog.confirm();
                }
                break;
            }
            case 26: {
                // event_handler.cpp L968-971：UNSELECT 的完成/取消按钮 = 发送 -1
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (cardSelectDialog != null) cardSelectDialog.dismiss();
                break;
            }
            case 27: {
                hideCancelOrFinishButton();
                if (cardDisplayDialog != null) cardDisplayDialog.dismiss();
                break;
            }
            case 16:
            case 25: {
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 18:
            case 24: {
                GameFieldController fieldCtl = activity.getFieldCtl();
                if (fieldCtl != null && fieldCtl.cancelPlaceSelect()) {
                    hideCancelOrFinishButton();
                }
                break;
            }
            default: {
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
        }
        currentSelectType = -1;
    }

    // === 整体可见性 ===

    public void onGameUIShown() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
        showDefault();
        hideCancelOrFinishButton();
    }

    public void onGameUIHidden() {
        hide();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        hideCancelOrFinishButton();
    }

    /**
     * 统一关闭当前打开的选择/确认/展示对话框（断线或决斗结束时清理），
     * 避免残留弹窗遮挡重新显示的局域网主界面
     */
    public void dismissOpenDialogs() {
        if (currentDialog != null) currentDialog.dismiss();
        if (cardSelectDialog != null) cardSelectDialog.dismiss();
        if (cardDisplayDialog != null) cardDisplayDialog.dismiss();
        currentDialog = null;
        cardSelectDialog = null;
        cardDisplayDialog = null;
        currentSelectType = -1;
        hideCancelOrFinishButton();
    }

    public void showBottomActions() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
    }

    // === 卡组编辑器模式 ===

    public void enterDeckEditorMode() {
        if (btnNote != null) btnNote.setVisibility(View.INVISIBLE);
        if (btnSpeed != null) btnSpeed.setVisibility(View.INVISIBLE);
        if (btnEmote != null) btnEmote.setVisibility(View.INVISIBLE);
        if (btnChat != null) btnChat.setVisibility(View.INVISIBLE);
        showDefault();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);
    }

    public void exitDeckEditorMode() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        hide();
        if (btnNote != null) btnNote.setVisibility(View.VISIBLE);
        if (btnSpeed != null) btnSpeed.setVisibility(View.VISIBLE);
        if (btnEmote != null) btnEmote.setVisibility(View.VISIBLE);
        if (btnChat != null) btnChat.setVisibility(View.VISIBLE);
    }

    /** 副卡组替换模式下隐藏卡组编辑控制栏（洗牌/排序/清空/删除/退出按钮区） */
    public void hideDeckControl() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
    }

    /** 退出副卡组替换模式后恢复卡组编辑控制栏 */
    public void showDeckControl() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);
    }

    // === 录像控制条 ===

    public void showReplayControls() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.VISIBLE);
    }

    public void hideReplayControls() {
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
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
package cn.garymb.ygomobile.render;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;
import ocgcore.enums.DuelPhase;

/**
 * 左侧卡片详情面板与全部控制按钮的管理类：
 * 卡片详情展示 / 左列功能按钮 / 底部行动按钮 / 阶段按钮 / 录像控制按钮 / 取消或完成按钮 / 卡组操作栏
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

    private FrameLayout layoutPhaseButtons;
    private Button btnPhaseCurrent, btnPhaseNext, btnEp;

    private LinearLayout layoutReplayControl;
    private Button btnReplayPlay, btnReplayPause, btnReplayNext, btnReplayLast, btnReplayShuffle, btnReplayQuit;
    private LinearLayout layoutDeckControl;

    private int currentCardCode = -1;

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

        layoutPhaseButtons = activity.findViewById(R.id.layout_phase_buttons);
        btnPhaseCurrent = activity.findViewById(R.id.btn_phase_current);
        btnPhaseNext = activity.findViewById(R.id.btn_phase_next);
        btnEp = activity.findViewById(R.id.btn_ep);

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
        btnEmote.setOnClickListener(v -> {
        });
        btnNote.setOnClickListener(v -> activity.showMainMenu());

        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setOnClickListener(v -> activity.cancelOrFinish());
        }

        if (btnPhaseNext != null) {
            btnPhaseNext.setOnClickListener(v -> {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                String label = btnPhaseNext.getText().toString();
                if ("BP".equals(label) && activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(6);
                } else if ("M2".equals(label) && activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(2);
                }
            });
        }

        if (btnEp != null) {
            btnEp.setOnClickListener(v -> {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                if (activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(3);
                } else if (activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(7);
                }
            });
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
        }
        if (tvCardDesc != null) {
            tvCardDesc.setText("点击场上的卡片查看详细信息");
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
            showDefault();
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
        if (card == null || card.getCode() <= 0) {
            showDefault();
            return;
        }

        int code = card.getCode();
        currentCardCode = code;
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }

        bindCardImage(code);
        bindCardName(card, code);
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

    private void bindCardSetname(Card cardData) {
        if (tvCardSetname == null) return;
        StringManager sm = DataManager.get().getStringManager();
        long[] setCodes = cardData.getSetCode();
        StringBuilder sb = new StringBuilder();
        boolean hasSet = false;
        for (long sc : setCodes) {
            if (sc == 0) continue;
            if (hasSet) sb.append("|");
            sb.append(sm.getSetName(sc));
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
        StringManager sm = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder();

        String typeStr = sm.getTypeString(cardData.Type);
        sb.append("[").append(typeStr).append("]");

        if (cardData.isType(CardType.Monster)) {
            String raceStr = sm.getRaceString(cardData.Race);
            String attrStr = sm.getAttributeString(cardData.Attribute);
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

    // === 阶段按钮 ===

    public void updateActionButtonsForPhase(int phase, boolean isMyTurn) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;

        if (layoutPhaseButtons != null) {
            layoutPhaseButtons.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
        }
        if (btnPhaseCurrent == null) return;

        if (btnPhaseNext != null) btnPhaseNext.setVisibility(View.GONE);
        if (btnEp != null) btnEp.setVisibility(View.GONE);

        switch (dp) {
            case Draw:
                btnPhaseCurrent.setText("DP");
                break;
            case Standby:
                btnPhaseCurrent.setText("SP");
                break;
            case Main1:
                btnPhaseCurrent.setText("M1");
                if (isMyTurn) {
                    if (btnPhaseNext != null) {
                        btnPhaseNext.setText("BP");
                        btnPhaseNext.setVisibility(View.VISIBLE);
                    }
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case BattleStart:
            case BattleStep:
            case Damage:
            case DamageCal:
            case Battle:
                btnPhaseCurrent.setText("BP");
                if (isMyTurn) {
                    if (btnPhaseNext != null) {
                        btnPhaseNext.setText("M2");
                        btnPhaseNext.setVisibility(View.VISIBLE);
                    }
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case Main2:
                btnPhaseCurrent.setText("M2");
                if (isMyTurn) {
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case End:
                btnPhaseCurrent.setText("EP");
                break;
            default:
                btnPhaseCurrent.setText(dp.name());
                break;
        }
    }

    /**
     * 录像回放时仅更新阶段文字，不处理可见性
     */
    public void setPhaseByValue(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (btnPhaseCurrent == null || dp == null) return;
        switch (dp) {
            case Draw:
                btnPhaseCurrent.setText("DP");
                break;
            case Standby:
                btnPhaseCurrent.setText("SP");
                break;
            case Main1:
                btnPhaseCurrent.setText("M1");
                break;
            case BattleStart:
            case BattleStep:
            case Battle:
            case Damage:
            case DamageCal:
                btnPhaseCurrent.setText("BP");
                break;
            case Main2:
                btnPhaseCurrent.setText("M2");
                break;
            case End:
                btnPhaseCurrent.setText("EP");
                break;
            default:
                btnPhaseCurrent.setText(dp.name());
                break;
        }
    }

    public void setPhaseText(String text) {
        if (btnPhaseCurrent != null) btnPhaseCurrent.setText(text);
    }

    public void closeGameButtons() {
        hideCancelOrFinishButton();
        if (btnPhaseCurrent != null) btnPhaseCurrent.setText("");
        if (btnPhaseNext != null) btnPhaseNext.setVisibility(View.GONE);
        if (btnEp != null) btnEp.setVisibility(View.GONE);
        if (layoutPhaseButtons != null) layoutPhaseButtons.setVisibility(View.GONE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
    }

    // === 取消或完成按钮 (对应 C++ ClientField::CancelOrFinish) ===

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

    // === 整体可见性 ===

    public void onGameUIShown() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
        showDefault();
        hideCancelOrFinishButton();
    }

    public void onGameUIHidden() {
        hide();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutPhaseButtons != null) layoutPhaseButtons.setVisibility(View.GONE);
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

    // === 录像控制条 ===

    public void showReplayControls() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.VISIBLE);
    }

    public void hideReplayControls() {
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
    }
}
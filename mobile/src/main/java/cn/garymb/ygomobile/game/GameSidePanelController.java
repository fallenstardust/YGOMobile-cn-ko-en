package cn.garymb.ygomobile.game;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import ocgcore.enums.DuelPhase;

/**
 * 左侧卡片详情面板与全部控制按钮的管理类：
 * 左列功能按钮 / 底部行动按钮 / 阶段按钮 / 录像控制按钮 / 取消或完成按钮 / 卡组操作栏
 */
public class GameSidePanelController {

    private final YGOProActivity activity;

    private CardDetailPanel cardDetailPanel;
    private LinearLayout layoutCardDetail;
    private ImageView ivCardImage;
    private TextView tvCardName, tvCardAttr, tvCardLevel, tvCardDesc;

    private LinearLayout layoutLeftButtons;
    private ImageButton btnSettings, btnChat, btnSound, btnSpeed, btnEmote, btnNote;

    private LinearLayout layoutBottomActions;
    private Button btnSurrender, btnIgnoreTiming, btnShowTiming, btnAvailableTiming;
    private Button btnCancelOrFinish;

    private FrameLayout layoutPhaseButtons;
    private Button btnPhaseCurrent, btnPhaseNext, btnEp;

    private LinearLayout layoutReplayControl;
    private Button btnReplayPlay, btnReplayPause, btnReplayNext, btnReplayLast, btnReplayShuffle, btnReplayQuit;
    private LinearLayout layoutDeckControl;

    public GameSidePanelController(YGOProActivity activity) {
        this.activity = activity;
    }

    public void bindViews() {
        layoutCardDetail = activity.findViewById(R.id.layout_card_detail);
        ivCardImage = activity.findViewById(R.id.iv_card_image);
        tvCardName = activity.findViewById(R.id.tv_card_name);
        tvCardAttr = activity.findViewById(R.id.tv_card_attr);
        tvCardLevel = activity.findViewById(R.id.tv_card_level);
        tvCardDesc = activity.findViewById(R.id.tv_card_desc);

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

    public void initCardDetail(ImageLoader imageLoader) {
        cardDetailPanel = new CardDetailPanel(activity.findViewById(android.R.id.content), imageLoader);
    }

    public CardDetailPanel getCardDetailPanel() {
        return cardDetailPanel;
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
        if (cardDetailPanel != null) {
            cardDetailPanel.showCard(card);
        }
    }

    void showDefaultDetail() {
        if (cardDetailPanel != null) cardDetailPanel.showDefault();
    }

    void hideDetail() {
        if (cardDetailPanel != null) cardDetailPanel.hide();
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
        if (layoutLeftButtons != null) layoutLeftButtons.setVisibility(View.VISIBLE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
        showDefaultDetail();
        hideCancelOrFinishButton();
    }

    public void onGameUIHidden() {
        if (layoutLeftButtons != null) layoutLeftButtons.setVisibility(View.GONE);
        hideDetail();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutPhaseButtons != null) layoutPhaseButtons.setVisibility(View.GONE);
        hideCancelOrFinishButton();
    }

    public void showBottomActions() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
    }

    // === 卡组编辑器模式 ===

    void enterDeckEditorMode() {
        if (layoutLeftButtons != null) layoutLeftButtons.setVisibility(View.VISIBLE);
        if (btnNote != null) btnNote.setVisibility(View.INVISIBLE);
        if (btnSpeed != null) btnSpeed.setVisibility(View.INVISIBLE);
        if (btnEmote != null) btnEmote.setVisibility(View.INVISIBLE);
        if (btnChat != null) btnChat.setVisibility(View.INVISIBLE);
        if (layoutCardDetail != null) layoutCardDetail.setVisibility(View.VISIBLE);
        showDefaultDetail();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);
    }

    public void exitDeckEditorMode() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        hideDetail();
        if (layoutLeftButtons != null) layoutLeftButtons.setVisibility(View.GONE);
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
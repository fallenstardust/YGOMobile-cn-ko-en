package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import cn.garymb.ygomobile.ui.widget.CardView;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.CardLocation;

/**
 * 决斗场区域管理器：绑定 layout_game_right 中所有区域 ID，
 * 从 GameField 数据模型同步状态，处理区域点击回调。
 * 替代原 GameFieldView 的 Canvas 渲染，使用 XML 视图展示。
 */
public class DuelFieldManager {

    private final Activity activity;

    public interface OnZoneClickListener {
        void onZoneClick(int player, int location, int sequence);
    }

    // 我方 (player 0)
    private final TextView[] myMonster = new TextView[5];
    private final TextView[] mySpell = new TextView[5];
    private TextView myField, myExtra, myBanish, myGrave, myDeck;
    private TextView emz1, emz2;

    // 对方 (player 1)
    private final TextView[] oppMonster = new TextView[5];
    private final TextView[] oppSpell = new TextView[5];
    private TextView oppField, oppExtra, oppBanish, oppGrave, oppDeck;

    private OnZoneClickListener listener;

    // 手卡区域
    private CardGroupView myHandView, oppHandView;
    private ImageLoader imageLoader;
    private int lastSlotHeight;
    private static final int HAND_CODE_BACK = -1;

    public DuelFieldManager(Activity activity) {
        this.activity = activity;
        // 我方怪兽区
        myMonster[0] = activity.findViewById(R.id.zone_p0_m0);
        myMonster[1] = activity.findViewById(R.id.zone_p0_m1);
        myMonster[2] = activity.findViewById(R.id.zone_p0_m2);
        myMonster[3] = activity.findViewById(R.id.zone_p0_m3);
        myMonster[4] = activity.findViewById(R.id.zone_p0_m4);
        // 我方魔陷区
        mySpell[0] = activity.findViewById(R.id.zone_p0_s0);
        mySpell[1] = activity.findViewById(R.id.zone_p0_s1);
        mySpell[2] = activity.findViewById(R.id.zone_p0_s2);
        mySpell[3] = activity.findViewById(R.id.zone_p0_s3);
        mySpell[4] = activity.findViewById(R.id.zone_p0_s4);
        // 我方堆叠区
        myField = activity.findViewById(R.id.zone_p0_field);
        myExtra = activity.findViewById(R.id.zone_p0_extra);
        myBanish = activity.findViewById(R.id.zone_p0_banish);
        myGrave = activity.findViewById(R.id.zone_p0_grave);
        myDeck = activity.findViewById(R.id.zone_p0_deck);
        // 额外怪兽区
        emz1 = activity.findViewById(R.id.zone_p0_m5);
        emz2 = activity.findViewById(R.id.zone_p0_m6);

        // 对方怪兽区
        oppMonster[0] = activity.findViewById(R.id.zone_p1_m0);
        oppMonster[1] = activity.findViewById(R.id.zone_p1_m1);
        oppMonster[2] = activity.findViewById(R.id.zone_p1_m2);
        oppMonster[3] = activity.findViewById(R.id.zone_p1_m3);
        oppMonster[4] = activity.findViewById(R.id.zone_p1_m4);
        // 对方魔陷区
        oppSpell[0] = activity.findViewById(R.id.zone_p1_s0);
        oppSpell[1] = activity.findViewById(R.id.zone_p1_s1);
        oppSpell[2] = activity.findViewById(R.id.zone_p1_s2);
        oppSpell[3] = activity.findViewById(R.id.zone_p1_s3);
        oppSpell[4] = activity.findViewById(R.id.zone_p1_s4);
        // 对方堆叠区
        oppField = activity.findViewById(R.id.zone_p1_field);
        oppExtra = activity.findViewById(R.id.zone_p1_extra);
        oppBanish = activity.findViewById(R.id.zone_p1_banish);
        oppGrave = activity.findViewById(R.id.zone_p1_grave);
        oppDeck = activity.findViewById(R.id.zone_p1_deck);

        // 手卡区域
        myHandView = activity.findViewById(R.id.zone_p0_hand);
        oppHandView = activity.findViewById(R.id.zone_p1_hand);
        initHandCardSize();
        syncHandCardSizeToFieldSlot();

        setupClickListeners();
    }

    public void setOnZoneClickListener(OnZoneClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置图片加载器并初始化手卡卡背（TextureLoader 需已 init）
     */
    public void setImageLoader(ImageLoader loader) {
        this.imageLoader = loader;
        if (oppHandView != null) oppHandView.setCardBackImage(TextureLoader.get().getCardCover(true));
        if (myHandView != null) myHandView.setCardBackImage(TextureLoader.get().getCardCover(false));
    }

    /**
     * 手卡卡片尺寸初始值（布局完成前兜底）
     */
    private void initHandCardSize() {
        float density = activity.getResources().getDisplayMetrics().density;
        applyHandCardSize(Math.round(48 * density), Math.round(68 * density));
    }

    /**
     * 手卡卡片尺寸与场地槽位（zone_p0_m0）高度联动：
     * 卡高 = 槽位高 * 0.68，卡宽按卡图 177:254 比例
     */
    private void syncHandCardSizeToFieldSlot() {
        View slotRef = activity.findViewById(R.id.zone_p0_m0);
        if (slotRef == null) return;
        slotRef.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int h = slotRef.getHeight();
            if (h <= 0 || h == lastSlotHeight) return;
            lastSlotHeight = h;
            int cardH = Math.round(h * 0.68f);
            int cardW = Math.round(cardH * 0.70f);
            applyHandCardSize(cardW, cardH);
        });
    }

    private void applyHandCardSize(int w, int h) {
        if (myHandView != null) {
            myHandView.setCardSize(w, h);
            myHandView.refreshLayout();
        }
        if (oppHandView != null) {
            oppHandView.setCardSize(w, h);
            oppHandView.refreshLayout();
        }
    }

    private void setupClickListeners() {
        View.OnClickListener click = v -> {
            if (listener == null) return;
            int id = v.getId();
            int[] result = resolveZone(id);
            if (result != null) listener.onZoneClick(result[0], result[1], result[2]);
        };

        // 我方
        for (int i = 0; i < 5; i++) {
            if (myMonster[i] != null) myMonster[i].setOnClickListener(click);
            if (mySpell[i] != null) mySpell[i].setOnClickListener(click);
        }
        setClick(myField, click);
        setClick(myExtra, click);
        setClick(myBanish, click);
        setClick(myGrave, click);
        setClick(myDeck, click);
        setClick(emz1, click);
        setClick(emz2, click);

        // 对方
        for (int i = 0; i < 5; i++) {
            if (oppMonster[i] != null) oppMonster[i].setOnClickListener(click);
            if (oppSpell[i] != null) oppSpell[i].setOnClickListener(click);
        }
        setClick(oppField, click);
        setClick(oppExtra, click);
        setClick(oppBanish, click);
        setClick(oppGrave, click);
        setClick(oppDeck, click);
    }

    private void setClick(TextView tv, View.OnClickListener click) {
        if (tv != null) tv.setOnClickListener(click);
    }

    /**
     * 将区域 ID 解析为 (player, location, sequence)
     */
    private int[] resolveZone(int id) {
        if (id == R.id.zone_p0_m0) return new int[]{0, 0x04, 0};
        if (id == R.id.zone_p0_m1) return new int[]{0, 0x04, 1};
        if (id == R.id.zone_p0_m2) return new int[]{0, 0x04, 2};
        if (id == R.id.zone_p0_m3) return new int[]{0, 0x04, 3};
        if (id == R.id.zone_p0_m4) return new int[]{0, 0x04, 4};
        if (id == R.id.zone_p0_m5) return new int[]{0, 0x04, 5};
        if (id == R.id.zone_p0_m6) return new int[]{0, 0x04, 6};
        if (id == R.id.zone_p0_s0) return new int[]{0, 0x08, 0};
        if (id == R.id.zone_p0_s1) return new int[]{0, 0x08, 1};
        if (id == R.id.zone_p0_s2) return new int[]{0, 0x08, 2};
        if (id == R.id.zone_p0_s3) return new int[]{0, 0x08, 3};
        if (id == R.id.zone_p0_s4) return new int[]{0, 0x08, 4};
        if (id == R.id.zone_p0_field) return new int[]{0, 0x08, 5};
        if (id == R.id.zone_p0_extra) return new int[]{0, 0x40, 0};
        if (id == R.id.zone_p0_banish) return new int[]{0, 0x20, 0};
        if (id == R.id.zone_p0_grave) return new int[]{0, 0x10, 0};
        if (id == R.id.zone_p0_deck) return new int[]{0, 0x01, 0};

        if (id == R.id.zone_p1_m0) return new int[]{1, 0x04, 0};
        if (id == R.id.zone_p1_m1) return new int[]{1, 0x04, 1};
        if (id == R.id.zone_p1_m2) return new int[]{1, 0x04, 2};
        if (id == R.id.zone_p1_m3) return new int[]{1, 0x04, 3};
        if (id == R.id.zone_p1_m4) return new int[]{1, 0x04, 4};
        if (id == R.id.zone_p1_s0) return new int[]{1, 0x08, 0};
        if (id == R.id.zone_p1_s1) return new int[]{1, 0x08, 1};
        if (id == R.id.zone_p1_s2) return new int[]{1, 0x08, 2};
        if (id == R.id.zone_p1_s3) return new int[]{1, 0x08, 3};
        if (id == R.id.zone_p1_s4) return new int[]{1, 0x08, 4};
        if (id == R.id.zone_p1_field) return new int[]{1, 0x08, 5};
        if (id == R.id.zone_p1_extra) return new int[]{1, 0x40, 0};
        if (id == R.id.zone_p1_banish) return new int[]{1, 0x20, 0};
        if (id == R.id.zone_p1_grave) return new int[]{1, 0x10, 0};
        if (id == R.id.zone_p1_deck) return new int[]{1, 0x01, 0};

        return null;
    }

    /**
     * 从 GameField 同步所有区域状态
     */
    public void updateFromField(GameField field) {
        if (field == null) return;

        // 我方怪兽区
        for (int i = 0; i < 5; i++) {
            updateZoneView(myMonster[i], field.players[0].monsterZone, i);
        }
        // 我方魔陷区
        for (int i = 0; i < 5; i++) {
            updateZoneView(mySpell[i], field.players[0].spellZone, i);
        }
        // 额外怪兽区
        updateZoneView(emz1, field.players[0].monsterZone, 5);
        updateZoneView(emz2, field.players[0].monsterZone, 6);

        // 我方堆叠区
        updatePileView(myDeck, field, 0, 0x01);
        updatePileView(myGrave, field, 0, 0x10);
        updatePileView(myBanish, field, 0, 0x20);
        updatePileView(myExtra, field, 0, 0x40);
        updateZoneView(myField, field.players[0].spellZone, 5);

        // 对方怪兽区
        for (int i = 0; i < 5; i++) {
            updateZoneView(oppMonster[i], field.players[1].monsterZone, i);
        }
        // 对方魔陷区
        for (int i = 0; i < 5; i++) {
            updateZoneView(oppSpell[i], field.players[1].spellZone, i);
        }

        // 对方堆叠区
        updatePileView(oppDeck, field, 1, 0x01);
        updatePileView(oppGrave, field, 1, 0x10);
        updatePileView(oppBanish, field, 1, 0x20);
        updatePileView(oppExtra, field, 1, 0x40);
        updateZoneView(oppField, field.players[1].spellZone, 5);

        // 手卡区域
        updateHandView(myHandView, field.players[0].hand, false);
        updateHandView(oppHandView, field.players[1].hand, true);
    }

    /**
     * 同步手卡区域：差异更新，仅当卡牌列表变化时重建
     * 我方手卡显示正面；对方手卡默认显示卡背，公开卡（isPublic）显示正面
     */
    private void updateHandView(CardGroupView groupView, List<GameField.ClientCard> hand, boolean opponent) {
        if (groupView == null || imageLoader == null) return;
        List<Integer> codes = new ArrayList<>();
        for (GameField.ClientCard cc : hand) {
            if (cc == null || cc.code == 0 || (opponent && !cc.isPublic)) {
                codes.add(HAND_CODE_BACK);
            } else {
                codes.add(cc.code);
            }
        }
        int childCount = groupView.getChildCount();
        if (childCount == codes.size()) {
            boolean same = true;
            for (int i = 0; i < childCount; i++) {
                CardView cv = (CardView) groupView.getChildAt(i);
                Card card = cv.getCard();
                int cur = (card != null) ? card.Code : HAND_CODE_BACK;
                if (cur != codes.get(i)) {
                    same = false;
                    break;
                }
            }
            if (same) return;
        }
        groupView.removeAllCards();
        for (int code : codes) {
            if (code == HAND_CODE_BACK) {
                groupView.addCardBack();
            } else {
                groupView.addCard(DataManager.get().getCardManager().getCard(code));
            }
        }
        final int player = opponent ? 1 : 0;
        int n = groupView.getChildCount();
        for (int i = 0; i < n; i++) {
            CardView cv = (CardView) groupView.getChildAt(i);
            final int seq = i;
            cv.setOnClickListener(v -> {
                if (listener != null) listener.onZoneClick(player, CardLocation.Hand.value(), seq);
            });
        }
    }

    private void updateZoneView(TextView view, List<GameField.ClientCard> list, int index) {
        if (view == null) return;
        if (index < 0 || index >= list.size()) {
            view.setText("");
            return;
        }
        GameField.ClientCard card = list.get(index);
        if (card != null && card.code != 0) {
            view.setText(String.valueOf(card.code));
            view.setAlpha(1f);
        } else {
            view.setText("");
        }
    }

    private void updatePileView(TextView view, GameField field, int player, int location) {
        if (view == null) return;
        int count = field.getCardCount(player, location);
        view.setText(count > 0 ? String.valueOf(count) : "0");
    }

    // === 高亮控制 ===

    /**
     * 根据 selectFieldMask 高亮/恢复区域
     * selectFieldMask 已归一化：bit 0-15 = 我方区域，bit 16-31 = 对方区域
     */
    public void applyHighlightMask(int mask) {
        for (int p = 0; p < 2; p++) {
            int base = p * 16;
            // 怪物区 0-6
            for (int seq = 0; seq < 7; seq++) {
                int bit = base + seq;
                boolean highlight = (mask & (1 << bit)) != 0;
                TextView tv = getMonsterView(p, seq);
                setHighlight(tv, highlight);
            }
            // 魔陷区 0-4
            for (int seq = 0; seq < 5; seq++) {
                int bit = base + 8 + seq;
                boolean highlight = (mask & (1 << bit)) != 0;
                TextView tv = getSpellView(p, seq);
                setHighlight(tv, highlight);
            }
        }
    }

    public void clearAllHighlights() {
        for (int p = 0; p < 2; p++) {
            for (int seq = 0; seq < 5; seq++) {
                setHighlight(getMonsterView(p, seq), false);
                setHighlight(getSpellView(p, seq), false);
            }
            // 我方额外怪兽区
            if (p == 0) {
                setHighlight(emz1, false);
                setHighlight(emz2, false);
            }
        }
    }

    private void setHighlight(TextView tv, boolean highlight) {
        if (tv == null) return;
        Drawable bg = tv.getBackground();
        if (highlight) {
            if (bg != null) bg.setColorFilter(Color.argb(120, 0, 255, 100), PorterDuff.Mode.SRC_ATOP);
            tv.setAlpha(1f);
        } else {
            if (bg != null) bg.clearColorFilter();
            tv.setAlpha(1f);
        }
    }

    private TextView getMonsterView(int player, int seq) {
        if (player == 0) {
            if (seq < 5) return myMonster[seq];
            if (seq == 5) return emz1;
            if (seq == 6) return emz2;
        } else {
            if (seq < 5) return oppMonster[seq];
        }
        return null;
    }

    private TextView getSpellView(int player, int seq) {
        return player == 0 ? (seq < 5 ? mySpell[seq] : null) : (seq < 5 ? oppSpell[seq] : null);
    }
}
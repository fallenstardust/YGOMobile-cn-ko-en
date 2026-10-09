package cn.garymb.ygomobile.game;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

/**
 * === 卡组统计面板（张数 / 分类计数 / GeneSys 起源点数记分板）===
 * 自 DeckEditorManager 拆出：主·额外·副卡组张数与类型分布计数、禁限表生效时的
 * 起源点数额度与余量展示，全部控件由 DeckEditorManager#bindViews 绑定后经本类刷新。
 * 仅在 UI 线程调用。
 */
class DeckStatsPanel {

    private final DeckEditorManager owner;

    DeckStatsPanel(DeckEditorManager owner) {
        this.owner = owner;
    }

    void updateCounts() {
        owner.currentDeck.syncCounts();
        int mainCount = owner.currentDeck.getMainCount();
        int extraCount = owner.currentDeck.getExtraCount();
        int sideCount = owner.currentDeck.getSideCount();
        boolean isGenesys = AppsSettings.get().getGenesysMode() == 1
                && owner.mLimitList != null && owner.mLimitList.getCreditLimits() != null;
        if (owner.tvMainCountNum != null) owner.tvMainCountNum.setText(String.valueOf(mainCount));
        if (owner.tvExtraCountNum != null) owner.tvExtraCountNum.setText(String.valueOf(extraCount));
        if (owner.tvSideCountNum != null) owner.tvSideCountNum.setText(String.valueOf(sideCount));
        if (owner.llGenesysScoreboard != null)
            owner.llGenesysScoreboard.setVisibility(isGenesys ? View.VISIBLE : View.GONE);
        if (isGenesys) {
            int creditLimit = owner.mLimitList.getCreditLimits();
            int creditCount = 0;
            for (Card c : owner.currentDeck.getMainCards()) creditCount += owner.getCardCredit(c);
            for (Card c : owner.currentDeck.getExtraCards()) creditCount += owner.getCardCredit(c);
            for (Card c : owner.currentDeck.getSideCards()) creditCount += owner.getCardCredit(c);
            int creditRemain = creditLimit - creditCount;
            if (owner.tvCreditLimit != null) owner.tvCreditLimit.setText(String.valueOf(creditLimit));
            if (owner.tvCreditCount != null) {
                owner.tvCreditCount.setText(String.valueOf(creditCount));
                owner.tvCreditCount.setTextColor(creditCount > creditLimit ? Color.RED : Color.WHITE);
            }
            if (owner.tvCreditRemain != null) {
                owner.tvCreditRemain.setText(String.valueOf(creditRemain));
                owner.tvCreditRemain.setTextColor(creditRemain < 0 ? Color.RED : Color.WHITE);
            }
        }
        int[] mainC = countByType(owner.currentDeck.getMainCards(), false);
        setTextIfNotNull(owner.tvMainMonsterCount, mainC[0]);
        setTextIfNotNull(owner.tvMainSpellCount, mainC[1]);
        setTextIfNotNull(owner.tvMainTrapCount, mainC[2]);
        int[] extraC = countByType(owner.currentDeck.getExtraCards(), true);
        setTextIfNotNull(owner.tvExtraFusionCount, extraC[0]);
        setTextIfNotNull(owner.tvExtraSynchroCount, extraC[1]);
        setTextIfNotNull(owner.tvExtraXyzCount, extraC[2]);
        setTextIfNotNull(owner.tvExtraLinkCount, extraC[3]);
        int[] sideC = countByType(owner.currentDeck.getSideCards(), false);
        setTextIfNotNull(owner.tvSideMonsterCount, sideC[0]);
        setTextIfNotNull(owner.tvSideSpellCount, sideC[1]);
        setTextIfNotNull(owner.tvSideTrapCount, sideC[2]);
    }

    private int[] countByType(List<Card> cards, boolean isExtra) {
        if (isExtra) {
            int fu = 0, sy = 0, xy = 0, li = 0;
            for (Card c : cards) {
                if (Card.isType(c.Type, CardType.Fusion)) fu++;
                else if (Card.isType(c.Type, CardType.Synchro)) sy++;
                else if (Card.isType(c.Type, CardType.Xyz)) xy++;
                else if (Card.isType(c.Type, CardType.Link)) li++;
            }
            return new int[]{fu, sy, xy, li};
        }
        int mo = 0, sp = 0, tr = 0;
        for (Card c : cards) {
            if (Card.isType(c.Type, CardType.Monster)) mo++;
            else if (Card.isType(c.Type, CardType.Spell)) sp++;
            else if (Card.isType(c.Type, CardType.Trap)) tr++;
        }
        return new int[]{mo, sp, tr};
    }

    private void setTextIfNotNull(TextView tv, int val) {
        if (tv != null) tv.setText(String.valueOf(val));
    }
}

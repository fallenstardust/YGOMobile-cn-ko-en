package cn.garymb.ygomobile.game.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 本场 match 中某席位“当前对局卡组”的三区卡码快照（主 / 额外 / 副）。
 * <p>
 * 来源有二：大厅开局前按席位提交的卡组（{@code MainMenuNavigator.onPlayerWaitingDeckUpdate}），
 * 与换 side 提交成功后回写的换侧结果（{@code DeckEditorViewHost.onSideDeckFinished}）。
 * <p>
 * 换 side 时编辑器以这份内容为编辑起点，而不是从磁盘 ydk 重读：换侧结果只提交给本局、不落盘，
 * 第二、三局之间磁盘上那份 ydk 已不是当前对局使用的卡组，重读会让玩家在错误的卡组上换备卡；
 * 服务端按“主/额外/副张数与开局卡组一致”校验提交内容，张数不同即回 ERRMSG_SIDEERROR（副卡组错误）。
 */
public final class MatchDeckSnapshot {

    public final List<Integer> main = new ArrayList<>();
    public final List<Integer> extra = new ArrayList<>();
    public final List<Integer> side = new ArrayList<>();

    /** 以提交内容整体替换快照（拷贝入内，调用方后续修改源列表不影响本快照）。 */
    public void update(List<Integer> main, List<Integer> extra, List<Integer> side) {
        this.main.clear();
        this.extra.clear();
        this.side.clear();
        if (main != null) this.main.addAll(main);
        if (extra != null) this.extra.addAll(extra);
        if (side != null) this.side.addAll(side);
    }

    /** 是否尚未登记过卡组（三者皆空）。 */
    public boolean isEmpty() {
        return main.isEmpty() && extra.isEmpty() && side.isEmpty();
    }
}

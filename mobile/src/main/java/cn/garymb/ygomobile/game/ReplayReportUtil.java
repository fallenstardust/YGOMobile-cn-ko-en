package cn.garymb.ygomobile.game;

import android.util.Log;

import cn.garymb.ygomobile.utils.CrashHandler;

/** 回放文本报告构造：开场对局信息串与本次播放的卡码/异常诊断落盘，纯函数便于单测与复用。 */
final class ReplayReportUtil {

    private ReplayReportUtil() {
    }

    /** 开场信息串：对战双方、LP/手牌/抽卡、tag/残局标记、起始回合与聊天条数（chatFrameCount<0 时不显示聊天项）。 */
    static String buildReplayInfo(ReplayReader.ReplayData replayData, int startTurn, int chatFrameCount) {
        StringBuilder sb = new StringBuilder("录像回放\n");
        for (int i = 0; i < replayData.playerNames.size(); i++) {
            if (i > 0) sb.append(" vs ");
            sb.append(replayData.playerNames.get(i));
        }
        sb.append("\nLP: ").append(replayData.params.startLp)
                .append(" | 手牌: ").append(replayData.params.startHand)
                .append(" | 抽卡: ").append(replayData.params.drawCount);
        if (replayData.isTag) sb.append(" [双打]");
        if (replayData.isSingleMode) sb.append(" [残局]");
        if (startTurn > 1) sb.append(" | 从第").append(startTurn).append("回合开始");
        if (chatFrameCount >= 0) sb.append(" | 聊天").append(chatFrameCount).append("条");
        return sb.toString();
    }

    /** 本次播放的卡码/异常诊断落盘（ygocore/log）；无错误且无卡码映射发现时直接跳过。 */
    static void writeDiagnostics(String tag, String error, ReplaySource source,
                                 int currentStep, ReplayReader.ReplayData replayData) {
        try {
            if (error == null && !ReplayCodeMapper.hasFindings()) return;
            StringBuilder sb = new StringBuilder();
            sb.append("== 回放诊断 ==\n源: ")
                    .append(source == null ? "未建立" : (source.engineDriven() ? "引擎重跑(旧格式)" : "消息流"))
                    .append("\n步数=").append(currentStep)
                    .append("\n错误=").append(error == null ? "无" : error).append('\n')
                    .append(ReplayCodeMapper.buildReport());
            if (replayData != null) {
                sb.append("录像: seed=").append(replayData.header.base.seed)
                        .append(" flag=0x").append(Integer.toHexString(replayData.header.base.flag))
                        .append(" decks=").append(replayData.decks.size()).append('\n');
            }
            CrashHandler.getInstance().writeReport("replay", sb.toString());
        } catch (Throwable t) {
            Log.w(tag, "writeDiagnostics failed", t);
        }
    }
}

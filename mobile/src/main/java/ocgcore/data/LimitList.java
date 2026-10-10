package ocgcore.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ocgcore.enums.LimitType;

/**
 * 用于存储禁止卡、限制卡、准限制卡
 * 本类功能包括：
 * 1设置禁止卡、限制卡、准限制卡
 * 2读取禁止卡、限制卡、准限制卡
 * 3判断某张卡是否属于禁止卡、限制卡、准限制卡
 */
public class LimitList {
    // GeneSys: 主卡组少于该数量时不触发 extra_score 加成，与 C++ 端 DECK_MIN_SIZE 保持一致
    private static final int GENESYS_MIN_MAIN = 40;
    private String name = "?";
    private Integer credit_limits;//GeneSys模式特有的上限值
    // GeneSys: 由lflist.conf中的"$__extra_score__"行解析而来，
    // 表示主卡组超过40张后，每多一张卡可为积分上限提高的分数（小数）。未配置则为null/0。
    private Double extra_score;
    private Map<Integer, Integer> credits;//GeneSys模式特有的单张卡ID和其点数
    /**
     * 0
     */
    public final List<Integer> forbidden;
    /**
     * 1
     */
    public final List<Integer> limit;
    /**
     * 2
     */
    public final List<Integer> semiLimit;

    public final List<Integer> allList;

    public LimitList() {
        forbidden = new ArrayList<>();
        limit = new ArrayList<>();
        semiLimit = new ArrayList<>();
        credits = new HashMap<>();
        allList = new ArrayList<>();
    }

    public List<Integer> getForbidden() {
        return forbidden;
    }

    public List<Integer> getLimit() {
        return limit;
    }

    public List<Integer> getSemiLimit() {
        return semiLimit;
    }

    public List<String> getStringForbidden() {
        List<String> strFobidden = new ArrayList<>();
        for (int i = 0; i < forbidden.size(); i++) {
            strFobidden.add(forbidden.get(i).toString());
        }
        return strFobidden;
    }

    public List<String> getStringLimit() {
        List<String> strLimit = new ArrayList<>();
        for (int i = 0; i < limit.size(); i++) {
            strLimit.add(limit.get(i).toString());
        }
        return strLimit;
    }

    public List<String> getStringSemiLimit() {
        List<String> strSemiLimit = new ArrayList<>();
        for (int i = 0; i < semiLimit.size(); i++) {
            strSemiLimit.add(semiLimit.get(i).toString());
        }
        return strSemiLimit;
    }

    public Integer getCreditLimits() {
        return credit_limits;
    }

    public Double getExtraScore() {
        return extra_score;
    }

    public void addExtraScore(Double score) {
        extra_score = score;
    }

    /**
     * 根据当前主卡组数量即时计算实际生效的GeneSys积分上限。
     * <p>
     * 规则：主卡组超过40张后，每多一张卡上限提高 extra_score 分；
     * 主卡组不足40张时不向下扣减，仍为基础上限；
     * 最终上限取整（丢弃小数部分），例如100.3334仍显示/适用为100。
     *
     * @param mainCount 当前主卡组卡片数量
     * @return 实际生效的积分上限；若未设置基础上限则返回null
     */
    public Integer getEffectiveCreditLimit(int mainCount) {
        if (credit_limits == null) {
            return null;
        }
        if (extra_score == null || extra_score <= 0 || mainCount <= GENESYS_MIN_MAIN) {
            return credit_limits;
        }
        double effective = credit_limits + (double) (mainCount - GENESYS_MIN_MAIN) * extra_score;
        if (effective < credit_limits) {
            return credit_limits; // 不做向下扣减保护
        }
        return (int) Math.floor(effective); // 丢弃小数部分
    }

    public Map<Integer, Integer> getCredits() {
        return credits;
    }

    public String getName() {
        return name;
    }

    public LimitList(String name) {
        this();
        this.name = name;
    }

    public void addSemiLimit(Integer id) {
        if (!semiLimit.contains(id)) {
            semiLimit.add(id);
        }
    }

    public void addLimit(Integer id) {
        if (!limit.contains(id)) {
            limit.add(id);
        }
    }

    public void addCreditLimit(Integer limit) {
        credit_limits = limit ;
    }

    public void addCredits(Integer cardId, Integer creditCost) {
        if (credits == null) {
            credits = new HashMap<>();
        }
        credits.put(cardId, creditCost);
    }

    public boolean has(Long id) {
        return allList.contains(id);
    }

    public void addForbidden(Integer id) {
        if (!forbidden.contains(id)) {
            forbidden.add(id);
        }
    }

    public List<Integer> getCodeList() {
        if (allList.isEmpty()) {
            allList.addAll(forbidden);
            allList.addAll(limit);
            allList.addAll(semiLimit);
            allList.addAll(credits.keySet());
        }
        return allList;
    }

    /**
     * 获取按信用分值卡牌ID列表（不含禁止卡）
     *
     * @return 包含信用分卡牌ID列表
     */
    public List<Integer> getGeneSysCodeList() {
        if (credits == null || credits.isEmpty()) {//防止空指针异常
            return new ArrayList<>();
        }

        // 按照分数从高到低排序
        return credits.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }


    /**
     * 解析一张卡在当前 GeneSys 禁卡表中应对应的点数。
     * <p>
     * 本方法是“是否为计分卡”“卡面/搜索结果点数显示”“卡组总分累计”“加卡校验”的
     * 唯一取值口径，避免各处因使用不同的键而导致行为分叉。
     * <p>
     * 取值优先级：
     * 1) 宽松同名折叠键（等价 {@code CardData.getCode()}：|alias-code|<=20 时取 alias，否则取 code），
     *    保证同卡异画共享表中同一分值；
     * 2) 若折叠键未命中但卡片自身 code 在表中命中，则用 code 的分值——
     *    修复“卡片 code 在表中、但 alias 不在表中(|alias-code|<=20)”时该卡不显示点数的 BUG。
     *
     * @return 命中的点数；两键均未命中返回 null（视为不计分卡）
     */
    public Integer getGeneSysCredit(Integer code, Integer alias) {
        if (credits == null || code == null) {
            return null;
        }
        int ali = alias == null ? 0 : alias;
        int key = (ali > 0 && Math.abs(ali - code) <= 20) ? ali : code;
        Integer value = credits.get(key);
        if (value == null && key != code) {
            value = credits.get(code);
        }
        return value;
    }

    public Integer getGeneSysCredit(Card card) {
        return card == null ? null : getGeneSysCredit(card.Code, card.Alias);
    }

    public boolean check(Card cardInfo, LimitType type) {
        return check(cardInfo.Code, cardInfo.Alias, type);
    }

    /**
     * 判断入参code或alias对应的卡片是否属于限制类型x，x由type确定
     *
     * @param code
     * @param alias
     * @param type
     * @return
     */
    public boolean check(Integer code, Integer alias, LimitType type) {
        if (type == LimitType.All) {
            getCodeList();
            return allList.contains(code) || allList.contains(alias);
        } else if (type == LimitType.Limit) {
            return limit.contains(code) || limit.contains(alias);
        } else if (type == LimitType.SemiLimit) {
            return semiLimit.contains(code) || semiLimit.contains(alias);
        } else if (type == LimitType.Forbidden) {
            return forbidden.contains(code) || forbidden.contains(alias);
        } else if (type == LimitType.GeneSys) {
            // 与分值取值共用同一口径 getGeneSysCredit，保证“判定为计分卡”与“能取到分值”同进同出
            return getGeneSysCredit(code, alias) != null;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = forbidden != null ? forbidden.hashCode() : 0;
        result = 31 * result + (limit != null ? limit.hashCode() : 0);
        result = 31 * result + (semiLimit != null ? semiLimit.hashCode() : 0);
        result = 31 * result + (credits != null ? credits.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LimitList{" +
                "name='" + name + '\'' +
                ", credit_limits=" + credit_limits +
                ", credits=" + credits +
                ", forbidden=" + forbidden +
                ", limit=" + limit +
                ", semiLimit=" + semiLimit +
                '}';
    }
}

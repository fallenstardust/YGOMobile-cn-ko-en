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
    private static final int LFLIST_HASH_SEED = 0x7dfcee6a;
    private String name = "?";
    private Integer credit_limits;//GeneSys模式特有的上限值
    // GeneSys: 由lflist.conf中的"$__extra_score__"行解析而来，
    // 表示主卡组超过40张后，每多一张卡可为积分上限提高的分数（小数）。未配置则为null/0。
    private Double extra_score;
    private Map<Integer, Integer> credits;//GeneSys模式特有的单张卡ID和其点数
    private int lfHash = LFLIST_HASH_SEED;
    private final Map<Integer, Integer> contentMap = new HashMap<>();
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

    public int getLfHash() {
        return lfHash;
    }

    public void setLfHash(int hash) {
        lfHash = hash;
    }

    private static int updateHash(int hash, int code, int count) {
        return hash ^ ((code << 18) | (code >>> 14))
                ^ ((code << (27 + count)) | (code >>> (5 - count)));
    }

    private void addContentCode(int code, int count) {
        Integer old = contentMap.put(code, count);
        if (old != null && old != count) {
            lfHash = updateHash(lfHash, code, old);
        }
        lfHash = updateHash(lfHash, code, count);
    }

    public void addSemiLimit(Integer id) {
        if (!semiLimit.contains(id)) {
            semiLimit.add(id);
        }
        addContentCode(id, 2);
    }

    public void addLimit(Integer id) {
        if (!limit.contains(id)) {
            limit.add(id);
        }
        addContentCode(id, 1);
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
        addContentCode(id, 0);
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
            return credits != null && (credits.containsKey(code) || credits.containsKey(alias));
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

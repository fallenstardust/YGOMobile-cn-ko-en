package ocgcore.data;

import java.util.ArrayList;
import java.util.List;

import ocgcore.enums.LimitType;

public class LimitList {
    private String name = "?";
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
        allList = new ArrayList<>();
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

    public boolean has(Long id) {
        return allList.contains(id);
    }

    public void addForbidden(Integer id) {
        if (!forbidden.contains(id)) {
            forbidden.add(id);
        }
    }

    public List<Integer> getCodeList() {
        if (allList.size() == 0) {
            allList.addAll(forbidden);
            allList.addAll(limit);
            allList.addAll(semiLimit);
        }
        return allList;
    }

    public boolean check(Card cardInfo, LimitType type) {
        return check(cardInfo.Code, cardInfo.Alias, type);
    }

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
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = forbidden != null ? forbidden.hashCode() : 0;
        result = 31 * result + (limit != null ? limit.hashCode() : 0);
        result = 31 * result + (semiLimit != null ? semiLimit.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LimitList{" +
                "name='" + name + '\'' +
                ", forbidden=" + forbidden +
                ", limit=" + limit +
                ", semiLimit=" + semiLimit +
                '}';
    }
}

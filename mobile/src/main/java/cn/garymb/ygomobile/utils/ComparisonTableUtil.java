package cn.garymb.ygomobile.utils;

/**
 * 23333先行密码与正式密码对照表工具类。
 * <p>
 * 原始数据已按职责拆分至 {@link OldIDsTable} 与 {@link NewIDsTable}，
 * 本类仅保留对外常量引用，调用方式（含静态导入）保持不变。
 * 来源网站：https://ygocdb.com/api/v0/idChangelogArray.jsonp
 */
public final class ComparisonTableUtil {

    /**
     * 先行（临时）密码数组
     */
    public static final int[] oldIDsArray = OldIDsTable.DATA;

    /**
     * 正式密码数组
     */
    public static final int[] newIDsArray = NewIDsTable.DATA;

    private ComparisonTableUtil() {
    }
}

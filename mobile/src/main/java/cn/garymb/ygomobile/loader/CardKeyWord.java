package cn.garymb.ygomobile.loader;

import cn.garymb.ygomobile.AppsSettings;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ocgcore.DataManager;
import ocgcore.data.Card;

public class CardKeyWord {
    private static final String TAG = "CardKeyWord";
    private final String word;
    private final List<ICardFilter> filterList = new ArrayList<>();
    private final boolean empty;

    public CardKeyWord(String word) {
        // 保存原始搜索关键词
        this.word = word;

        // 如果关键词不为空，则进行解析和过滤器构建
        if (!TextUtils.isEmpty(word)) {

            // 判断是否为纯数字且长度大于等于5，若是则作为卡密搜索
            if (TextUtils.isDigitsOnly(word) && word.length() >= 5) {
                // 添加卡密过滤器
                filterList.add(new CodeFilter(Long.parseLong(word)));
            } else {
                // 按空格分割关键词
                String[] ws = word.split(" ");

                // 如果设置中的关键词分割符是"%%"，则重新分割
                if (AppsSettings.get().getKeyWordsSplit() == AppsSettings.keyWordsSplitEnum.Percent.code) {
                    ws = word.split("%%");
                }

                // 遍历每个分割后的关键词
                for (String w : ws) {
                    // 跳过空字符串
                    if (TextUtils.isEmpty(w)) {
                        continue;
                    }

                    // 初始化排除标记为false
                    boolean exclude = false;
                    // 如果关键词以"-"开头，则表示排除模式
                    if (w.startsWith("-") && w.length() > 1) {
                        exclude = true;
                        // 去掉"-"前缀
                        w = w.substring(1);
                    }

                    // 初始化仅文本标记为false（当前被注释掉的功能）
                    boolean onlyText = false;
                /*
                // 如果关键词以引号开头，则只搜索文本内容
                if (w.startsWith("\"") || w.startsWith("“") || w.startsWith("”")) {
                    // 设置仅文本标志
                    onlyText = true;
                    // 如果也以引号结尾，则去掉首尾引号
                    if (w.endsWith("\"") || w.endsWith("“") || w.endsWith("”")) {
                        w = w.substring(1, w.length() - 1);
                    } else {
                        // 否则只去掉开始的引号
                        w = w.substring(1);
                    }
                }*/

                    // 记录当前过滤器参数的日志
                    Log.d(TAG, "filter:word=" + w + ", exclude=" + exclude + ", onlyText=" + onlyText);
                    // 添加名称过滤器
                    filterList.add(new NameFilter(w, exclude, onlyText));
                }
            }
        }
        // 判断是否有有效的过滤器
        empty = filterList.isEmpty();
    }


    public String getValue() {
        return word;
    }

    public boolean isValid(Card card) {
        if (empty) {
            return true;
        }
        for (ICardFilter filter : filterList) {
            if (!filter.isValid(card)) {
                return false;
            }
        }
        return true;
    }

    private static class NameFilter implements ICardFilter {
        private final boolean exclude;
        private final String word;
        private final long setcode;

        //包含系列，或者包含名字、描述
        public NameFilter(@NonNull String word, boolean exclude, boolean onlyText) {
            this.setcode = onlyText ? 0 : DataManager.get().getStringManager().getSetCode(word, true);
            this.exclude = exclude;
            this.word = word.toLowerCase(Locale.US);
            if (this.setcode > 0) {
                Log.d(TAG, "filter:setcode=" + setcode + ", exclude=" + exclude + ", word=" + word);
            }
        }

        @Override
        public boolean isValid(Card card) {
            boolean ret = (setcode != 0 && card.isSetCode(setcode)) || card.containsName(word) || card.containsDesc(word);
            if (exclude) {
                return !ret;
            } else {
                return ret;
            }
        }
    }

    private static class CodeFilter implements ICardFilter {
        private final long code;

        public CodeFilter(long code) {
            this.code = code;
        }

        @Override
        public boolean isValid(Card card) {
            return card.isSame(code);
        }
    }
}

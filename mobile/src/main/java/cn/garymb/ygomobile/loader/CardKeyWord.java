package cn.garymb.ygomobile.loader;

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.core.IrrlichtBridge;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class CardKeyWord {
    private final String word;
    private final List<ICardFilter> filterList = new ArrayList<>();
    private final boolean empty;

    public CardKeyWord(String word) {
        this.word = word;
        if (!TextUtils.isEmpty(word)) {
            if (TextUtils.isDigitsOnly(word)) {
                //搜索卡密
                filterList.add(new CodeFilter(Long.parseLong(word)));
            } else {
                String[] ws = word.split(" ");
                for (String w : ws) {
                    if (TextUtils.isEmpty(w)) {
                        continue;
                    }
                    boolean exclude = false;
                    if (w.startsWith("-")) {
                        exclude = true;
                        w = w.substring(1);
                    }
                    boolean onlyText = false;
                    if (w.startsWith("\"") || w.startsWith("“") || w.startsWith("”")) {
                        //只搜索文字
                        onlyText = true;
                        if (w.endsWith("\"") || w.endsWith("“") || w.endsWith("”")) {
                            w = w.substring(1, w.length() - 1);
                        } else {
                            w = w.substring(1);
                        }
                    }
                    if (!onlyText) {
                        long setcode = DataManager.get().getStringManager().getSetCode(w);
                        if (setcode != 0) {
                            //如果是系列名
                            filterList.add(new SetcodeFilter(setcode, exclude));
                        }
                    }
//                    Log.d(IrrlichtBridge.TAG, "filter:word=" + w + ", exclude=" + exclude + ", onlyText=" + onlyText);
                    filterList.add(new NameFilter(w, exclude));
                }
            }
        }
        empty = filterList.size() == 0;
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

        public NameFilter(String word, boolean exclude) {
            this.exclude = exclude;
            this.word = word;
        }

        @Override
        public boolean isValid(Card card) {
            if (exclude) {
                return !card.Name.contains(word);
            } else {
                return card.Name.contains(word);
            }
        }
    }

    private static class SetcodeFilter implements ICardFilter {
        private final boolean exclude;
        private final long setcode;

        public SetcodeFilter(long setcode, boolean exclude) {
            this.exclude = exclude;
            this.setcode = setcode;
        }

        @Override
        public boolean isValid(Card card) {
            if (exclude) {
                return !card.isSetCode(setcode);
            } else {
                return card.isSetCode(setcode);
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
            return card.Code == code || card.Alias == code;
        }
    }
}

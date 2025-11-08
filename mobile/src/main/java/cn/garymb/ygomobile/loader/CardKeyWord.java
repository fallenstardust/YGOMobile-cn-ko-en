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
        this.word = word;
        if (!TextUtils.isEmpty(word)) {
            if (TextUtils.isDigitsOnly(word) && word.length() >= 5) {
                //搜索卡密
                filterList.add(new CodeFilter(Long.parseLong(word)));
            } else {
                String[] ws = word.split(" ");
                if (AppsSettings.get().getKeyWordsSplit() == AppsSettings.keyWordsSplitEnum.Percent.code) {
                    ws = word.split("%%");
                }
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
                    /*
                    if (w.startsWith("\"") || w.startsWith("“") || w.startsWith("”")) {
                        //只搜索文字
                        onlyText = true;
                        if (w.endsWith("\"") || w.endsWith("“") || w.endsWith("”")) {
                            w = w.substring(1, w.length() - 1);
                        } else {
                            w = w.substring(1);
                        }
                    }*/
                    Log.d(TAG, "filter:word=" + w + ", exclude=" + exclude + ", onlyText=" + onlyText);
                    filterList.add(new NameFilter(w, exclude, onlyText));
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
        private final long setcode;

        //包含系列，或者包含名字、描述
        public NameFilter(@NonNull String word, boolean exclude, boolean onlyText) {
            this.setcode = onlyText ? 0 : DataManager.get().getStringManager().getSetCode(word, true);
            this.exclude = exclude;
            this.word = word.toLowerCase(Locale.US);
            if(this.setcode > 0){
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

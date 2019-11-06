package cn.garymb.ygomobile.ourygo.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ourygo.base.OnDuelClipBoardListener;

public class DuelAssistantManagement {
    private static final DuelAssistantManagement ourInstance = new DuelAssistantManagement();
    private String lastMessage;
    private String cardSearchMessage;

    public static DuelAssistantManagement getInstance() {
        return ourInstance;
    }

    //是否启动助手
    private boolean isStart;
    private boolean isListener;
    private ClipboardManager clipboardManager;
    //其他单独监听是否能触发
    private boolean isOtherListener;

    private List<OnDuelClipBoardListener> onDuelClipBoardListenerList;
    //卡查关键字
    public static final String[] cardSearchKey = new String[]{"?", "？"};
    //卡组url前缀
    private final static String DECK_URL_PREFIX = Constants.SCHEME_APP+"://"+Constants.URI_HOST;
    //卡组复制
    public static final String[] DeckTextKey = new String[]{"#main"};
    //加房关键字
    public static final String[] passwordPrefix = {
            "M,", "m,",
            "T,",
            "PR,", "pr,",
            "AI,", "ai,",
            "LF2,", "lf2,",
            "M#", "m#",
            "T#", "t#",
            "PR#", "pr#",
            "NS#", "ns#",
            "S#", "s#",
            "AI#", "ai#",
            "LF2#", "lf2#",
            "R#", "r#"
    };

    private DuelAssistantManagement() {
        isStart=false;
        isOtherListener=true;
        isListener=true;
        onDuelClipBoardListenerList =new ArrayList<>();
        clipboardManager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager!=null)
        clipboardManager.addPrimaryClipChangedListener(onPrimaryClipChangedListener);
    }


    public boolean isStart() {
        return isStart;
    }

    public void setStart(boolean start) {
        isStart = start;
    }

    public boolean isListener() {
        return isListener;
    }

    public void setListener(boolean listener) {
        isListener = listener;
    }

    public void addListener(OnDuelClipBoardListener onDuelClipBoardListener){
        onDuelClipBoardListenerList.add(onDuelClipBoardListener);
    }

    public void removeListener(OnDuelClipBoardListener onDuelClipBoardListener){
        onDuelClipBoardListenerList.remove(onDuelClipBoardListener);
    }

    public boolean isOtherListener() {
        return isOtherListener;
    }

    public void setOtherListener(boolean otherListener) {
        isOtherListener = otherListener;
    }

    public String getCardSearchMessage() {
        return cardSearchMessage;
    }

    public void setCardSearchMessage(String cardSearchMessage) {
        this.cardSearchMessage = cardSearchMessage;
    }

    ClipboardManager.OnPrimaryClipChangedListener onPrimaryClipChangedListener=new ClipboardManager.OnPrimaryClipChangedListener() {

        @Override
        public void onPrimaryClipChanged() {
            if (!isListener)
                return;
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null)
                return;
            CharSequence cs = clipData.getItemAt(0).getText();
            final String clipMessage;
            if (cs != null) {
                clipMessage = cs.toString();
            } else {
                clipMessage = null;
            }
            //如果复制的内容为空则不执行下面的代码
            if (TextUtils.isEmpty(clipMessage)) {
                return;
            }
            checkMessage(clipMessage,null);
        }
    };

    /**
     * 检查复制内容
     * @param clipMessage 复制内容
     */
    public void checkMessage(String clipMessage,OnDuelClipBoardListener onDuelClipBoardListener1) {
        if (TextUtils.isEmpty(clipMessage) )
            return;

        boolean isDebounce;
        if (clipMessage.equals(lastMessage))
            isDebounce=true;
        else
            isDebounce=false;
        lastMessage=clipMessage;
        //如果复制的内容是多行作为卡组去判断
        if (clipMessage.contains("\n")) {
            for (String s : DeckTextKey) {
                //只要包含其中一个关键字就视为卡组
                if (clipMessage.contains(s)) {
                    if (onDuelClipBoardListener1==null) {
                        isOtherListener=false;
                        for (int i = 0; i < onDuelClipBoardListenerList.size(); i++) {
                            OnDuelClipBoardListener onDuelClipBoardListener = onDuelClipBoardListenerList.get(i);
                            if (onDuelClipBoardListener.isEffective()) {
                                onDuelClipBoardListener.onDeckCode(clipMessage, isDebounce);
                            } else {
                                onDuelClipBoardListenerList.remove(i);
                                i--;
                            }
                        }
                        isOtherListener=true;
                    }else if (isOtherListener){
                        onDuelClipBoardListener1.onDeckCode(clipMessage, isDebounce);
                    }
                    return;
                }
            }
            return;
        }
        //如果是卡组url
        int deckStart = clipMessage.indexOf(DECK_URL_PREFIX);
        if (deckStart != -1) {
            if (onDuelClipBoardListener1==null) {
                isOtherListener=false;
                for (int i = 0; i < onDuelClipBoardListenerList.size(); i++) {
                    OnDuelClipBoardListener onDuelClipBoardListener = onDuelClipBoardListenerList.get(i);
                    if (onDuelClipBoardListener.isEffective()) {
                        onDuelClipBoardListener.onDeckUrl(clipMessage.substring(deckStart + DECK_URL_PREFIX.length()), isDebounce);
                    } else {
                        onDuelClipBoardListenerList.remove(i);
                        i--;
                    }
                }
                isOtherListener=true;
            }else if (isOtherListener) {
                onDuelClipBoardListener1.onDeckUrl(clipMessage.substring(deckStart + DECK_URL_PREFIX.length()), isDebounce);
            }
            return;
        }

        int start = -1;
        int end;
        String passwordPrefixKey = null;
        for (String s : passwordPrefix) {
            start = clipMessage.indexOf(s);
            passwordPrefixKey = s;
            if (start != -1) {
                break;
            }
        }

        if (start != -1) {
            //如果密码含有空格，则以空格结尾
            end = clipMessage.indexOf(" ", start);
            //如果不含有空格则取片尾所有
            if (end == -1) {
                end = clipMessage.length();
            } else {
                //如果只有密码前缀而没有密码内容则不跳转
                if (end - start == passwordPrefixKey.length())
                    return;
            }
            if (onDuelClipBoardListener1==null) {
                isOtherListener=false;
                for (int i = 0; i < onDuelClipBoardListenerList.size(); i++) {
                    OnDuelClipBoardListener onDuelClipBoardListener = onDuelClipBoardListenerList.get(i);
                    if (onDuelClipBoardListener.isEffective()) {
                        onDuelClipBoardListener.onDuelPassword(clipMessage.substring(start, end), isDebounce);
                    } else {
                        onDuelClipBoardListenerList.remove(i);
                        i--;
                    }
                }
                isOtherListener=true;
            }else if (isOtherListener) {
                onDuelClipBoardListener1.onDuelPassword(clipMessage.substring(start, end), isDebounce);
            }
        } else {
            for (String s : cardSearchKey) {
                int cardSearchStart = clipMessage.indexOf(s);
                if (cardSearchStart != -1) {
                    //卡查内容
                     cardSearchMessage = clipMessage.substring(cardSearchStart + s.length(), clipMessage.length());
                    //如果复制的文本里带？号后面没有内容则不跳转
                    if (TextUtils.isEmpty(cardSearchMessage)) {
                        return;
                    }
                    //如果卡查内容包含“=”并且复制的内容包含“.”不卡查
                    if (cardSearchMessage.contains("=") && clipMessage.contains(".")) {
                        return;
                    }
                    if (onDuelClipBoardListener1==null) {
                        isOtherListener=false;
                        for (int i = 0; i < onDuelClipBoardListenerList.size(); i++) {
                            OnDuelClipBoardListener onDuelClipBoardListener = onDuelClipBoardListenerList.get(i);
                            if (onDuelClipBoardListener.isEffective()) {
                                onDuelClipBoardListener.onCardQuery(cardSearchMessage, isDebounce);
                            } else {
                                onDuelClipBoardListenerList.remove(i);
                                i--;
                            }
                        }
                        isOtherListener=true;
                    }else if (isOtherListener) {
                        onDuelClipBoardListener1.onCardQuery(cardSearchMessage, isDebounce);
                    }
                }
            }
        }
    }

}

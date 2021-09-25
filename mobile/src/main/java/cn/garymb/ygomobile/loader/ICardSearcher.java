package cn.garymb.ygomobile.loader;

import ocgcore.data.LimitList;

public interface ICardSearcher extends ICardLoader{
    void search(CardSearchInfo searchInfo);
    void onReset();
    void setLimitList(LimitList limit);
    LimitList getLimitList();
}

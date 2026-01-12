package cn.garymb.ygomobile.loader;

import java.util.List;

import ocgcore.data.LimitList;

public interface ICardSearcher extends ICardLoader{
    void search(List<CardSearchInfo> searchInfos);
    void search(CardSearchInfo searchInfo);
    void onReset();
    void setLimitList(LimitList limit);
    LimitList getLimitList();
    LimitList getGenesysLimitList();
}

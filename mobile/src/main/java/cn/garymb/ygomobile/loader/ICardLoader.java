package cn.garymb.ygomobile.loader;

import ocgcore.data.LimitList;

public interface ICardLoader{
    void search(String prefixWord, String suffixWord,
                long attribute, long level, long race,String limitName,long limit,
                String atk, String def,long pscale,
                long setcode, long category, long ot,int link, long... types);
    void onReset();
    void setLimitList(LimitList limit);
    LimitList getLimitList();
}

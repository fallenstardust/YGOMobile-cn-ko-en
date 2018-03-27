package cn.garymb.ygomobile.loader;

public interface IDataLoader {
    void setCallBack(ILoadCallBack loadCallBack);
    void loadData();
    ILoadCallBack getCallBack();
}

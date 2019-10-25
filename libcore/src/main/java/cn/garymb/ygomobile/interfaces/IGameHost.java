package cn.garymb.ygomobile.interfaces;

public interface IGameHost {
    String getSetting(String key);
    int getIntSetting(String key, int def);
    void saveIntSetting(String key, int value);
    void saveSetting(String key, String value);
    void runWindbot(String cmd);
    int getLocalAddr();
}

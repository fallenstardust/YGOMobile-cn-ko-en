package cn.garymb.ygomobile.utils.rom;

import android.text.TextUtils;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/09
 *    desc   :
 * </pre>
 */
public class FuntouchOsChecker extends Checker {
    @Override
    protected String getManufacturer() {
        return ManufacturerList.VIVO;
    }

    @Override
    protected String[] getAppList() {
        return AppList.FUNTOUCH_OS_APPS;
    }

    @Override
    public ROM getRom() {
        return ROM.FuntouchOS;
    }

    @Override
    public ROMInfo checkBuildProp(RomProperties properties) throws Exception {
        ROMInfo info = null;
        String versionStr = properties.getProperty(BuildPropKeyList.FUNTOUCHOS_OS_VERSION);
        if (!TextUtils.isEmpty(versionStr) && versionStr.matches("[\\d.]+")) { // 3.0
            try {
                info = new ROMInfo(getRom());
                info.setVersion(versionStr);
                info.setBaseVersion(Integer.parseInt(versionStr.split("\\.")[0]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return info;
    }
}

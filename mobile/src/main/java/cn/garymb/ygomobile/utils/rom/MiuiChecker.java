package cn.garymb.ygomobile.utils.rom;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/01
 *    desc   :
 * </pre>
 */
class MiuiChecker extends Checker {

    @Override
    public ROM getRom() {
        return ROM.MIUI;
    }

    @Override
    protected String getManufacturer() {
        return ManufacturerList.XIAOMI;
    }

    @Override
    protected String[] getAppList() {
        return AppList.MIUI_APPS;
    }

    @Override
    public ROMInfo checkBuildProp(RomProperties properties) throws Exception {
        ROMInfo info = null;
        String versionName = properties.getProperty(BuildPropKeyList.MIUI_VERSION_NANE);
        if (!TextUtils.isEmpty(versionName) && versionName.matches("[Vv]\\d+")) { // V9
            try {
                info = new ROMInfo(getRom());
                info.setBaseVersion(Integer.parseInt(versionName.substring(1)));

                String versionStr = properties.getProperty(BuildPropKeyList.MIUI_VERSION);
                if (!TextUtils.isEmpty(versionStr)) {
                    // 参考: 8.1.25 & V9.6.2.0.ODECNFD & V10.0.1.0.OAACNFH
                    Matcher matcher = Pattern.compile("[Vv]?(\\d+(\\.\\d+)*)[.A-Za-z]*").matcher(versionStr);
                    if (matcher.matches()) {
                        info.setVersion(matcher.group(1));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return info;
    }
}

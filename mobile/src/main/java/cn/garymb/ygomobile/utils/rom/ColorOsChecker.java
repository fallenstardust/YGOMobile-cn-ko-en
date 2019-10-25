package cn.garymb.ygomobile.utils.rom;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/09
 *    desc   :
 * </pre>
 */
public class ColorOsChecker extends Checker {

    @Override
    protected String getManufacturer() {
        return ManufacturerList.OPPO;
    }

    @Override
    protected String[] getAppList() {
        return AppList.COLOR_OS_APPS;
    }

    @Override
    public ROM getRom() {
        return ROM.ColorOS;
    }

    @Override
    public ROMInfo checkBuildProp(RomProperties properties) throws Exception {
        ROMInfo info = null;
        String versionStr = properties.getProperty(BuildPropKeyList.COLOROS_ROM_VERSION);
        if (!TextUtils.isEmpty(versionStr)) {
            Matcher matcher = Pattern.compile("ColorOS([\\d.]+)").matcher(versionStr); // ColorOS2.1
            if (matcher.find()) {
                try {
                    String version = matcher.group(1);
                    info = new ROMInfo(getRom());
                    info.setVersion(version);
                    info.setBaseVersion(Integer.parseInt(version.split("\\.")[0]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return info;
    }
}

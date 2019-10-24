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
public class AmigoOsChecker extends Checker {
    @Override
    protected String getManufacturer() {
        return ManufacturerList.AMIGO;
    }

    @Override
    protected String[] getAppList() {
        return AppList.AMIGO_OS_APPS;
    }

    @Override
    public ROM getRom() {
        return ROM.AmigoOS;
    }

    @Override
    public ROMInfo checkBuildProp(RomProperties properties) throws Exception {
        ROMInfo info = null;
        String versionStr = properties.getProperty(BuildPropKeyList.AMIGO_DISPLAY_ID);
        if (!TextUtils.isEmpty(versionStr)) {
            Matcher matcher = Pattern.compile("amigo([\\d.]+)[a-zA-Z]*").matcher(versionStr); // "amigo3.5.1"
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

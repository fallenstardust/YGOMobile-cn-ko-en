package cn.garymb.ygomobile.utils.rom;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/01
 *    desc   :
 * </pre>
 */
public class RomIdentifier {

    private static ROM sRomType;
    private static ROMInfo sRomInfo;

    private RomIdentifier() {
    }

    private static IChecker[] getICheckers() {
        return new IChecker[]{
                new MiuiChecker(),
                new EmuiChecker(),
                new ColorOsChecker(),
                new FuntouchOsChecker(),
                new FlymeChecker(),
                new EuiChecker(),
                new AmigoOsChecker(),
                new GoogleChecker()
        };
    }

    /**
     * 获取 ROM 类型
     *
     * @param context Context
     * @return ROM 类型
     */
    public static ROM getRomType(Context context) {
        if (sRomType == null)
            sRomType = doGetRomType(context);
        return sRomType;
    }


    private static ROM doGetRomType(Context context) {
        IChecker[] checkers = getICheckers();

        // 优先检查 Manufacturer
        String manufacturer = Build.MANUFACTURER;
        Log.i("kk", "manufacturer="+manufacturer);
        for (IChecker checker : checkers) {
            if (checker.checkManufacturer(manufacturer)) {
                // 检查完 Manufacturer 后, 再核对一遍应用列表
                if (checker.checkApplication(context))
                    return checker.getRom();
            }
        }
        // 如果 Manufacturer 和 应用列表对不上, 则以应用列表为准, 重新检查一遍应用列表
        List<ApplicationInfo> appInfos = context.getPackageManager().getInstalledApplications(0);
        HashSet<String> installPkgs = new HashSet<>();
        for (ApplicationInfo appInfo : appInfos) {
            installPkgs.add(appInfo.packageName);
        }
        for (IChecker checker : checkers) {
            if (checker.checkApplication(installPkgs))
                return checker.getRom();
        }
        return ROM.Other;
    }

    /**
     * 获取 ROM 信息 (包括 ROM 类型和版本信息)
     *
     * @param context Context
     * @return ROM 信息
     */
    public static ROMInfo getRomInfo(Context context) {
        if (sRomInfo == null)
            sRomInfo = doGetRomInfo(context);
        return sRomInfo;
    }

    private static ROMInfo doGetRomInfo(Context context) {
        ROM rom = getRomType(context);

        IChecker[] checkers = getICheckers();
        RomProperties properties = new RomProperties();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            FileInputStream is = null;
            try {
                // 获取 build.prop 配置
                Properties buildProperties = new Properties();
                is = new FileInputStream(new File(Environment.getRootDirectory(), "build.prop"));
                buildProperties.load(is);
                properties.setBuildProp(buildProperties);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // 检查配置
        for (int i = 0; i < checkers.length; i++) {
            if (rom == checkers[i].getRom()) {
                if (i != 0) {
                    IChecker temp = checkers[0];
                    checkers[0] = checkers[i];
                    checkers[i] = temp;
                }
                break;
            }
        }
        try {
            ROMInfo temp;
            for (IChecker checker : checkers) {
                if ((temp = checker.checkBuildProp(properties)) != null) {
                    return temp;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ROMInfo(rom);
    }

}

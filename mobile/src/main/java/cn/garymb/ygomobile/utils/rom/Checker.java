package cn.garymb.ygomobile.utils.rom;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Set;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/09
 *    desc   :
 * </pre>
 */
public abstract class Checker implements IChecker {

    protected abstract String getManufacturer();

    protected abstract String[] getAppList();

    @Override
    public boolean checkManufacturer(String manufacturer) {
        return manufacturer.equalsIgnoreCase(getManufacturer());
    }

    @Override
    public boolean checkApplication(Context context) {
        PackageManager manager = context.getPackageManager();
        for (String pkg : getAppList()) {
            try {
                manager.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public boolean checkApplication(Set<String> installedPackages) {
        int count = 0;
        String[] list = getAppList();
        int aim = (list.length + 1) / 2;
        for (String pkg : list) {
            if (installedPackages.contains(pkg)) {
                count++;
                if (count >= aim)
                    return true;
            }
        }
        return false;
    }
}

package cn.garymb.ygomobile.utils.rom;

import android.content.Context;

import java.util.Set;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/03
 *    desc   :
 * </pre>
 */
public interface IChecker {

    ROM getRom();

    boolean checkManufacturer(String manufacturer);

    boolean checkApplication(Context context);

    boolean checkApplication(Set<String> installedPackages);

    ROMInfo checkBuildProp(RomProperties properties) throws Exception;
}

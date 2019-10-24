package cn.garymb.ygomobile.utils.rom;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/17
 *    desc   :
 * </pre>
 */
public class SonyChecker extends Checker {
    @Override
    protected String getManufacturer() {
        return ManufacturerList.SONY;
    }

    @Override
    protected String[] getAppList() {
        return AppList.SONY_APPS;
    }

    @Override
    public ROM getRom() {
        return ROM.Sony;
    }

    @Override
    public ROMInfo checkBuildProp(RomProperties properties) throws Exception {
        return null;
    }
}

package cn.garymb.ygomobile.utils.rom;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/03
 *    desc   :
 * </pre>
 */
interface BuildPropKeyList {

    String KEY_DISPLAY_ID = "ro.build.display.id";
    String KEY_BASE_OS_VERSION = "ro.build.version.base_os";

    // 小米 : MIUI
    String MIUI_VERSION = "ro.build.version.incremental"; // "7.6.15"
    String MIUI_VERSION_NANE = "ro.miui.ui.version.name"; // "V8"

    // 华为 : EMUI
    String EMUI_VERSION = "ro.build.version.emui"; // "EmotionUI_3.0"

    // 魅族 : Flyme
    String FLYME_DISPLAY_ID = KEY_DISPLAY_ID; // "Flyme OS 4.5.4.2U"

    // OPPO : ColorOS
    String COLOROS_ROM_VERSION = "ro.rom.different.version"; // "ColorOS2.1"

    // vivo : FuntouchOS
    String FUNTOUCHOS_OS_VERSION = "ro.vivo.os.version"; // "3.0"
    String FUNTOUCHOS_DISPLAY_ID = "ro.vivo.os.build.display.id"; // "FuntouchOS_3.0"
    String FUNTOUCHOS_ROM_VERSION = "ro.vivo.rom.version"; // "rom_3.1"

    // Samsung

    // Sony

    // 乐视 : eui
    String EUI_VERSION = "ro.letv.release.version"; // "5.9.023S"
    String EUI_VERSION_DATE = "ro.letv.release.version_date"; // "5.9.023S_03111"

    // 金立 : amigo
    String AMIGO_ROM_VERSION = "ro.gn.gnromvernumber"; // "GIONEE ROM5.0.16"
    String AMIGO_DISPLAY_ID = KEY_DISPLAY_ID;

    // 酷派 : yulong

    // HTC : Sense

    // LG : LG

    // 联想
}

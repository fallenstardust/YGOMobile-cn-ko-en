package cn.garymb.ygomobile.utils.rom;

import android.os.Build;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleChecker extends Checker {
    @Override
    protected String getManufacturer() {
        return ManufacturerList.GOOGLE;
    }

    @Override
    protected String[] getAppList() {
        return new String[]{"com.google.android.apps.nexuslauncher"};
    }

    @Override
    public ROM getRom() {
        return ROM.Google;
    }


    @Override
    public ROMInfo checkBuildProp(RomProperties properties) throws Exception {
        ROMInfo info = new ROMInfo(getRom());
        info.setVersion(Build.VERSION.RELEASE);
        info.setBaseVersion(Build.VERSION.SDK_INT);
        return info;
    }
}

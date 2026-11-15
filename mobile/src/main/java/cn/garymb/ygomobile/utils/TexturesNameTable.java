package cn.garymb.ygomobile.utils;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.garymb.ygomobile.lite.R;

/**
 * textures素材文件名 -> 展示名的映射表
 */
public class TexturesNameTable {
    private static final Map<String, Integer> NAME_MAP = new HashMap<>();

    private static final Pattern LSCALE_PATTERN = Pattern.compile("^lscale_(\\d+)\\.png$");
    private static final Pattern RSCALE_PATTERN = Pattern.compile("^rscale_(\\d+)\\.png$");
    private static final Pattern LINK_MARKER_PATTERN = Pattern.compile("^link_marker_on_(\\d+)\\.png$");

    static {
        // textures
        put("act.png", R.string.textures_act);
        put("attack.png", R.string.textures_attack);
        put("bg.jpg", R.string.textures_bg);
        put("bg_deck.jpg", R.string.textures_bg_deck);
        put("bg_menu.jpg", R.string.textures_bg_menu);
        put("cardtype.png", R.string.textures_cardtype);
        put("chain.png", R.string.textures_chain);
        put("chaintarget.png", R.string.textures_chaintarget);
        put("cover.jpg", R.string.textures_cover);
        put("cover2.jpg", R.string.textures_cover2);
        put("equip.png", R.string.textures_equip);
        put("f1.jpg", R.string.textures_f1);
        put("f2.jpg", R.string.textures_f2);
        put("f3.jpg", R.string.textures_f3);
        put("field-transparent2.png", R.string.textures_field_transparent2);
        put("field-transparent3.png", R.string.textures_field_transparent3);
        put("field2.png", R.string.textures_field2);
        put("field3.png", R.string.textures_field3);
        put("icon_lim.png", R.string.textures_icon_lim);
        put("lp3.png", R.string.textures_lp3);
        put("lpbarf.png", R.string.textures_lpbarf);
        put("mask.png", R.string.textures_mask);
        put("me.jpg", R.string.textures_me);
        put("negated.png", R.string.textures_negated);
        put("number.png", R.string.textures_number);
        put("opponent.jpg", R.string.textures_opponent);
        put("ot.png", R.string.textures_ot);
        put("selfield.png", R.string.textures_selfield);
        put("target.png", R.string.textures_target);
        put("tiktok.png", R.string.textures_tiktok);
        put("totalAtk.png", R.string.textures_totalatk);
        put("unknown.jpg", R.string.textures_unknown);

        // textures/extra/
        put("emoticons.png", R.string.textures_emoticons);
        put("gsc.png", R.string.textures_gsc);
        put("sButton_C.png", R.string.textures_sbutton_c);
        put("sButton_C_pressed.png", R.string.textures_sbutton_c_pressed);
        put("sButton_L.png", R.string.textures_sbutton_l);
        put("sButton_L_pressed.png", R.string.textures_sbutton_l_pressed);
        put("sButton_S.png", R.string.textures_sbutton_s);
        put("sButton_S_pressed.png", R.string.textures_sbutton_s_pressed);
        put("sDialog_L.png", R.string.textures_sdialog_l);
        put("sDialog_S.png", R.string.textures_sdialog_s);
        put("sWindow.png", R.string.textures_swindow);
        put("sWindow_V.png", R.string.textures_swindow_v);
        put("stitlebar.png", R.string.textures_stitlebar);
        put("tclose.png", R.string.textures_tclose);
        put("tdoublex.png", R.string.textures_tdoublex);
        put("temoticon.png", R.string.textures_temoticon);
        put("tlogs.png", R.string.textures_tlogs);
        put("tmute.png", R.string.textures_tmute);
        put("tonex.png", R.string.textures_tonex);
        put("tplay.png", R.string.textures_tplay);
        put("tsettings.png", R.string.textures_tsettings);
        put("tshut.png", R.string.textures_tshut);
        put("ttalk.png", R.string.textures_ttalk);
    }

    private static void put(String fileName, int resId) {
        NAME_MAP.put(fileName, resId);
    }

    /**
     * 根据文件名返回中文展示名，未映射则返回原文件名。
     */
    public static String getShowName(Context context, String fileName) {
        if (fileName == null) {
            return "";
        }
        Integer resId = NAME_MAP.get(fileName);
        if (resId != null) {
            return context.getString(resId);
        }
        Matcher m = LSCALE_PATTERN.matcher(fileName);
        if (m.matches()) {
            return context.getString(R.string.textures_lscale, Integer.parseInt(m.group(1)));
        }
        m = RSCALE_PATTERN.matcher(fileName);
        if (m.matches()) {
            return context.getString(R.string.textures_rscale, Integer.parseInt(m.group(1)));
        }
        m = LINK_MARKER_PATTERN.matcher(fileName);
        if (m.matches()) {
            return context.getString(R.string.textures_link_marker, Integer.parseInt(m.group(1)));
        }
        return fileName;
    }
}

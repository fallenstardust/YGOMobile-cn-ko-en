package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import cn.garymb.ygomobile.App;

public class YGOUtil {

    //提示
    public static void show(String message) {
        Toast.makeText(App.get(), message, Toast.LENGTH_SHORT).show();
    }

    public static int c(int colorId){
        return ContextCompat.getColor(App.get(),colorId);
    }
}

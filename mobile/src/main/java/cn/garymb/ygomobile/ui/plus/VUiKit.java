package cn.garymb.ygomobile.ui.plus;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.widget.Toast;

import org.jdeferred.android.AndroidDeferredManager;

/**
 * @author Lody
 * <p>
 * A set of tools for UI.
 */
public class VUiKit {
    private static final AndroidDeferredManager gDM = new AndroidDeferredManager();
    private static final Handler gUiHandler = new Handler(Looper.getMainLooper());

    public static AndroidDeferredManager defer() {
        return gDM;
    }

    public static void post(Runnable r) {
        gUiHandler.post(r);
    }

    public static void postDelayed(long delay, Runnable r) {
        gUiHandler.postDelayed(r, delay);
    }

}

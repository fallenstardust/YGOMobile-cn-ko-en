package com.ourygo.ygomobile.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Dialog;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.Type;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.bean.CardBag;

import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.lite.R;

public class OYUtil {

    private static final String URL_AIFAFIAN = "https://afdian.net/@ourygo";

    public static void initToolbar(final AppCompatActivity activity, Toolbar toolbar, String s, boolean isBack) {
        toolbar.setTitle(s);
        activity.setSupportActionBar(toolbar);

        if (isBack) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);//  context.getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha));
            toolbar.setNavigationOnClickListener(p1 -> {
                activity.finish();
                // TODO: Implement this method
            });
        }
    }

    //显示虚拟键盘
    public static void showKeyboard(View v) {
        v.requestFocus();
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        // imm.showSoftInput(v,InputMethodManager.SHOW_FORCED);

        imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    //关闭输入法
    public static void closeKeyboard(Activity activity) {
        if (activity == null)
            return;
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view != null)
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    //关闭输入法
    public static void closeKeyboard(Dialog dialog) {
        if (dialog == null)
            return;
        InputMethodManager inputMethodManager = (InputMethodManager) dialog.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = dialog.getCurrentFocus();
        if (view != null)
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    //复制字符串到剪贴板
    public static void copyMessage(String s) {
        ClipboardManager cmb = (ClipboardManager) App.get().getSystemService(App.get().CLIPBOARD_SERVICE);
        cmb.setText(s);//复制命令

    }

    public static void show(String toastMessage) {
        Toast.makeText(App.get(), toastMessage, Toast.LENGTH_SHORT).show();
    }

    public static String s(int string) {
        return App.get().getResources().getString(string);
    }

    public static int c(int color) {
        return ContextCompat.getColor(App.get(), color);
    }

    public static int px(int dimen) {
        return OYApplication.get().getResources().getDimensionPixelOffset(dimen);
    }

    public static int dp(int dimen) {
        return ScaleUtils.px2dp(px(dimen));
    }

    public static int sp(int dimen) {
        return ScaleUtils.px2sp(px(dimen));
    }

    public static void snackShow(View v, String toastMessage) {
        SnackbarUtil.ShortSnackbar(v, toastMessage, c(R.color.colorAccent), SnackbarUtil.white).show();
    }

    public static void snackWarning(View v, String toastMessage) {
        SnackbarUtil.ShortSnackbar(v, toastMessage, SnackbarUtil.white, SnackbarUtil.red).show();
    }

    public static Spanned getNameText(String name) {
        return Html.fromHtml(name);
    }

    public static Spanned getMessageText(String message) {
        return Html.fromHtml(message.replaceAll("\\n", "<br>"));
    }

    /**
     * 获取手机IMEI号
     */
    public static String getIMEI() {
        TelephonyManager telephonyManager = (TelephonyManager) App.get().getSystemService(App.get().TELEPHONY_SERVICE);
        String imei = telephonyManager.getDeviceId();

        return imei;
    }

    public static boolean joinQQGroup(Context context, String key) {
        Intent intent = new Intent();
        if (key.indexOf("mqqopensdkapi") == -1) {
            key = "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D" + key;
        }
        intent.setData(Uri.parse(key));
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            // 未安装手Q或安装的版本不支持
            return false;
        }
    }

    //是否是新版本
//    public static boolean getIsNewVersion() {
//        String versionName = App.get().getResources().getString(R.string.app_version_name);
//        SharedPreferences sh = App.get().getSharedPreferences("AppVersion", App.get().MODE_PRIVATE);
//        int vercode = SystemUtils.getVersion(OYApplication.get());
//        int vn = sh.getInt("versionCode", 0);
//        if (vn<vercode) {
//            sh.edit().putString("versionName", versionName).commit();
//            return true;
//        } else {
//            return false;
//        }
//    }

    public static void snackExceptionToast(final Context context, View view, final String toast, final String exception) {
        SnackbarUtil.ShortSnackbar(view, toast, SnackbarUtil.white, SnackbarUtil.red)
                .setActionTextColor(c(R.color.black)).setAction(s(R.string.start_exception), new View.OnClickListener() {
            @Override
            public void onClick(View p1) {
                final DialogUtils du = DialogUtils.getInstance(context);
                Button b1 = du.dialogt1(toast, exception);
                b1.setText((R.string.copy_exception));
                b1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View p1) {
                        copyMessage(exception);
                        du.dis();
                        // TODO: Implement this method
                    }
                });
                // TODO: Implement this method
            }
        }).show();
    }

    public static boolean isServiceExisted(Context context, String className) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList = activityManager
                .getRunningServices(Integer.MAX_VALUE);

        if (!(serviceList.size() > 0)) {
            return false;
        }

        for (int i = 0; i < serviceList.size(); i++) {
            ActivityManager.RunningServiceInfo serviceInfo = serviceList.get(i);
            ComponentName serviceName = serviceInfo.service;

            if (serviceName.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isContextExisted(Context context) {
        if (context != null) {
            if (context instanceof Activity) {
                if (!((Activity) context).isFinishing()) {
                    return true;
                }
            } else if (context instanceof Service) {
                if (isServiceExisted(context, context.getClass().getName())) {
                    return true;
                }
            } else if (context instanceof Application) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dp2px(float dpValue) {
        final float scale = App.get().getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
    public static int px2dp(float pxValue) {
        final float scale = App.get().getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    public static String message2Base64(String message) {
        return Base64.encodeToString(message.getBytes(), Base64.NO_WRAP);
    }

    public static String message2Base64URL(String message) {
        return Base64.encodeToString(message.getBytes(), Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }

    public static String base642Message(String base64) {
        return new String(base64);
    }

    public static Drawable getRadiusBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
//        drawable.setGradientType(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(OYApplication.get().getResources().getDimension(R.dimen.corner_radius));
        drawable.setColor(color);
        return drawable;
    }

    public static String[] getArray(int id) {
        return OYApplication.get().getResources().getStringArray(id);
    }

    public static String getWatchDuelPassword(String password, int userId) {
        byte[] bytes = new byte[6];
        bytes[1] = (byte) (3 << 4);
        int checksum = 0;
        for (int i = 1; i < bytes.length; i++) {
            checksum -= bytes[i];
        }
        bytes[0] = (byte) (checksum & 0xff);
        int secret = (userId % 65535) + 1;
        for (int i = 0; i < bytes.length; i += 2) {
            int x = 0;
            x |= ((int) bytes[i]) & 0xff;
            x |= bytes[i + 1] << 8;
            x ^= secret;
            bytes[i] = (byte) (x & 0xff);
            bytes[i + 1] = (byte) (x >> 8);
        }

        String messageString = Base64.encodeToString(bytes, Base64.NO_WRAP);
        return messageString + password;
    }

    public static boolean isApp(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        try {
            PackageInfo info = OYApplication.get().getPackageManager().getPackageInfo(packageName, 0);
            if (info != null)
                return true;
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    /**
     * map对象转换为json
     *
     * @param map
     * @return json字符串
     */
    public static String map2jsonStr(Map<String, String> map) {
        return new JSONObject(map).toString();
    }

    /**
     * map对象转换为json
     *
     * @param map
     * @return json字符串
     */
    public static String mapObejct2jsonStr(Map<String, Object> map) {
        return new JSONObject(map).toString();
    }

    public static void startAifadian(Context context) {
        context.startActivity(IntentUtil.getUrlIntent(OYUtil.URL_AIFAFIAN));
    }

    public static void checkUpdate(Activity activity, final boolean b) {
        UpdateUtil.checkUpdate(activity,b);
//        Beta.checkUpgrade(isManual, !b);
    }

    public static String getFileSizeText(long fileSize) {
        String dx;
        long ddx1 = fileSize / 1024 / 1024;
        if (ddx1 < 1) {
            dx = fileSize / 1024 % 1024 + "K";
        } else {
            String iii = fileSize / 1024 % 1024 + "";
            switch (iii.length()) {
                case 1:
                    iii = "00" + iii;
                    break;
                case 2:
                    iii = "0" + iii;
                    break;
            }
            iii = iii.substring(0, 2);
            dx = ddx1 + "." + iii + "M";
        }
        return dx;
    }

    public static class MyItemDecoration extends RecyclerView.ItemDecoration {

        private int mDividerHeight;

        /**
         * @param outRect 边界
         * @param view    recyclerView ItemView
         * @param parent  recyclerView
         * @param state   recycler 内部数据管理
         */
        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            //设定底部边距为1px
            //outRect.set(0, 0, 0, 3);
            //第一个ItemView不需要在上面绘制分割线
            if (parent.getChildAdapterPosition(view) != 0) {
                //这里直接硬编码为1px
                outRect.top = 1;
                mDividerHeight = 1;
            }
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent) {
            // TODO: Implement this method
            super.onDraw(c, parent);
            int childCount = parent.getChildCount();

            for (int i = 0; i < childCount; i++) {
                View view = parent.getChildAt(i);

                int index = parent.getChildAdapterPosition(view);
                //第一个ItemView不需要绘制
                if (index == 0) {
                    continue;
                }

                float dividerTop = view.getTop() - mDividerHeight;
                float dividerLeft = parent.getPaddingLeft();
                float dividerBottom = view.getTop();
                float dividerRight = parent.getWidth() - parent.getPaddingRight();
                Paint mPaint = new Paint();
                mPaint.setColor(c(R.color.colorDivider));
                c.drawRect(dividerLeft, dividerTop, dividerRight, dividerBottom, mPaint);

            }
        }

    }



    public static Bitmap blurBitmap(Bitmap bitmap, float radius, Context context) {
        //Create renderscript
        RenderScript rs = RenderScript.create(context);
        //Create allocation from Bitmap
        Allocation allocation = Allocation.createFromBitmap(rs, bitmap);

        Type t = allocation.getType();
        //Create allocation with the same type
        Allocation blurredAllocation = Allocation.createTyped(rs, t);
        //Create script
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        //Set blur radius (maximum 25.0)
        blurScript.setRadius(radius);
        //Set input for script
        blurScript.setInput(allocation);
        //Call script for output allocation
        blurScript.forEach(blurredAllocation);
        //Copy script result into bitmap
        blurredAllocation.copyTo(bitmap);
        //Destroy everything to free memory
        allocation.destroy();
        blurredAllocation.destroy();
        blurScript.destroy();
        t.destroy();
        rs.destroy();
        return bitmap;
    }

    public static Bitmap rsBlur(Context context,Bitmap source,int radius){

        Bitmap inputBmp = source;
        //(1)
        RenderScript renderScript =  RenderScript.create(context);

//        Log.i(TAG,"scale size:"+inputBmp.getWidth()+"*"+inputBmp.getHeight());

        // Allocate memory for Renderscript to work with
        //(2)
        final Allocation input = Allocation.createFromBitmap(renderScript,inputBmp);
        final Allocation output = Allocation.createTyped(renderScript,input.getType());
        //(3)
        // Load up an instance of the specific script that we want to use.
        ScriptIntrinsicBlur scriptIntrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        //(4)
        scriptIntrinsicBlur.setInput(input);
        //(5)
        // Set the blur radius
        scriptIntrinsicBlur.setRadius(radius);
        //(6)
        // Start the ScriptIntrinisicBlur
        scriptIntrinsicBlur.forEach(output);
        //(7)
        // Copy the output to the blurred bitmap
        output.copyTo(inputBmp);
        //(8)
        renderScript.destroy();

        return inputBmp;
    }

    public static CardBag getNewCardBag(){
        return getNewCardBagList().get(0);
    }

    public static List<CardBag> getNewCardBagList(){
        List<CardBag> cardBagList=new ArrayList<>();
        CardBag cardBag;

        cardBag =new CardBag();
        cardBag.setTitle("SD46 王者归来");
        cardBag.setMessage("杰克的塔玛希回来了");
        cardBag.setDeckName("SD46");
        cardBagList.add(cardBag);

        cardBag =new CardBag();
        cardBag.setTitle("1111 哥布林版舞台旋转来临");
        cardBag.setMessage("K语言甚至让你读不懂他的效果");
        cardBag.setDeckName("1111");
        cardBagList.add(cardBag);

        cardBag =new CardBag();
        cardBag.setTitle("WPP3 三幻神加强！");
        cardBag.setMessage("幻神专属卡片助你再魂一把");
        cardBag.setDeckName("WPP3+VJ");
        cardBagList.add(cardBag);

        cardBag =new CardBag();
        cardBag.setTitle("DBAD 消防栓带妖精");
        cardBag.setMessage("效果强力，令人绝望！");
        cardBag.setDeckName("DBAD+VJ+YCSW");
        cardBagList.add(cardBag);

        cardBag=new CardBag();
        cardBag.setTitle("SR13 恶魔之门，暗黑界回归！");
        cardBag.setMessage("暗黑界的龙神王，珠泪新打手");
        cardBag.setDeckName("SR13+T1109");
        cardBagList.add(cardBag);

        return cardBagList;
    }

    private static Object createViewPropertyAnimatorRT(View view) {
        try {
            final Class<?> animRtCalzz = Class.forName("android.view.ViewPropertyAnimatorRT");
            final Constructor<?> animRtConstructor = animRtCalzz.getDeclaredConstructor(View.class);
            animRtConstructor.setAccessible(true);
            return animRtConstructor.newInstance(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void setViewPropertyAnimatorRT(ViewPropertyAnimator animator, Object rt) {
        try {
            final Class<?> animClazz = Class.forName("android.view.ViewPropertyAnimator");
            final Field animRtField = animClazz.getDeclaredField("mRTBackend");
            animRtField.setAccessible(true);
            animRtField.set(animator, rt);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void onStartBefore(ViewPropertyAnimator viewPropertyAnimator, View view) {
        Object object = createViewPropertyAnimatorRT(view);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && object != null) {
            setViewPropertyAnimatorRT(viewPropertyAnimator, object);
        }
    }

    /**
     * 是否是今天第一次打开软件
     */
    public static boolean isTodayFirstStart() {
        long todayStartTime = SharedPreferenceUtil.getTodayStartTime();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(todayStartTime);

        Calendar calendar1 = Calendar.getInstance();
        if (calendar.get(Calendar.YEAR) == calendar1.get(Calendar.YEAR)
                && calendar.get(Calendar.MONTH) == calendar1.get(Calendar.MONTH)
                && calendar.get(Calendar.DAY_OF_MONTH) == calendar1.get(Calendar.DAY_OF_MONTH)) {
            SharedPreferenceUtil.setTodayStartTime(System.currentTimeMillis());
            return true;
        }
        return false;
    }

}

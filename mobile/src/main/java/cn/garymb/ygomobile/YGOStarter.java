package cn.garymb.ygomobile;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.ViewTarget;
import com.bumptech.glide.signature.StringSignature;

import java.io.File;
import java.util.HashMap;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.plus.ViewTargetPlus;
import cn.garymb.ygomobile.utils.CardUtils;
import cn.garymb.ygomobile.utils.ComponentUtils;
import cn.garymb.ygomobile.utils.ThreeDLayoutUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

import static android.view.View.inflate;


public class YGOStarter {
    private static Bitmap mLogo;
    private static ImageView cardImage;
    private static TextView name;
    private static TextView desc;
    private static TextView level;
    private static TextView type;
    private static TextView race;
    private static TextView cardAtk;
    private static TextView cardDef;
    private static TextView attrView;
    private static View linkArrow;
    private static View monsterlayout;
    private static View layout_detail_p_scale;
    private static View atkdefView;
    private static TextView detail_cardscale;
    private static CardManager mCardManager;
    private static ImageLoader imageLoader;
    private static StringManager mStringManager;
    private static SparseArray<Card> cards;
    private static Context mContext;
    private static View view;

    public static void RandomCardDetail(Context context, Card cardInfo) {
        mContext = context;
        View viewCardDetail = inflate(mContext, R.layout.dialog_cardinfo_small, null);
        cardImage = viewCardDetail.findViewById(R.id.card_image);
        name = viewCardDetail.findViewById(R.id.text_name);
        level = viewCardDetail.findViewById(R.id.card_level);
        linkArrow = viewCardDetail.findViewById(R.id.detail_link_arrows);
        race = viewCardDetail.findViewById(R.id.card_race);
        attrView = viewCardDetail.findViewById(R.id.card_attribute);
        type = viewCardDetail.findViewById(R.id.card_type);
        cardAtk = viewCardDetail.findViewById(R.id.card_atk);
        cardDef = viewCardDetail.findViewById(R.id.card_def);
        atkdefView = viewCardDetail.findViewById(R.id.layout_atkdef2);
        desc = viewCardDetail.findViewById(R.id.text_desc);

        monsterlayout = viewCardDetail.findViewById(R.id.layout_monster);
        layout_detail_p_scale = viewCardDetail.findViewById(R.id.detail_p_scale);
        detail_cardscale = viewCardDetail.findViewById(R.id.detail_cardscale);

        if (cardInfo == null) return;
        imageLoader.bindImage(cardImage, cardInfo.Code, null, true);
        name.setText(cardInfo.Name);
        type.setText(CardUtils.getAllTypeString(cardInfo, mStringManager).replace("/", "|"));
        attrView.setText(mStringManager.getAttributeString(cardInfo.Attribute));
        desc.setText(cardInfo.Desc);
        if (cardInfo.isType(CardType.Monster)) {
            atkdefView.setVisibility(View.VISIBLE);
            monsterlayout.setVisibility(View.VISIBLE);
            race.setVisibility(View.VISIBLE);
            String star = "★" + cardInfo.getStar();
            level.setText(star);
            if (cardInfo.isType(CardType.Xyz)) {
                level.setTextColor(mContext.getResources().getColor(R.color.star_rank));
            } else {
                level.setTextColor(mContext.getResources().getColor(R.color.star));
            }
            if (cardInfo.isType(CardType.Pendulum)) {
                layout_detail_p_scale.setVisibility(View.VISIBLE);
                detail_cardscale.setText(String.valueOf(cardInfo.LScale));
            } else {
                layout_detail_p_scale.setVisibility(View.GONE);
            }
            cardAtk.setText((cardInfo.Attack < 0 ? "?" : String.valueOf(cardInfo.Attack)));
            //连接怪兽设置
            if (cardInfo.isType(CardType.Link)) {
                level.setVisibility(View.GONE);
                linkArrow.setVisibility(View.VISIBLE);
                cardDef.setText((cardInfo.getStar() < 0 ? "?" : "LINK-" + String.valueOf(cardInfo.getStar())));
                BaseActivity.showLinkArrows(cardInfo, view);
            } else {
                level.setVisibility(View.VISIBLE);
                linkArrow.setVisibility(View.GONE);
                cardDef.setText((cardInfo.Defense < 0 ? "?" : String.valueOf(cardInfo.Defense)));
            }
            race.setText(mStringManager.getRaceString(cardInfo.Race));
        } else {
            atkdefView.setVisibility(View.GONE);
            race.setVisibility(View.GONE);
            monsterlayout.setVisibility(View.GONE);
            level.setVisibility(View.GONE);
            linkArrow.setVisibility(View.GONE);
        }
        Toast toast = new Toast(mContext);
        toast.setView(viewCardDetail);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }

    private static void setFullScreen(Activity activity, ActivityShowInfo activityShowInfo) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (activity instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        } else {
            android.app.ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        }
    }

    private static void quitFullScreen(Activity activity, ActivityShowInfo activityShowInfo) {
        if (activity instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (activityShowInfo.hasSupperbar && actionBar != null) {
                actionBar.show();
            }
        } else {
            android.app.ActionBar actionBar = activity.getActionBar();
            if (activityShowInfo.hasBar && actionBar != null) {
                actionBar.show();
            }
        }
        final WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
        attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().setAttributes(attrs);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private static void showLoadingBg(Activity activity) {
        ActivityShowInfo activityShowInfo = Infos.get(activity);
        if (activityShowInfo == null) {
            return;
        }
        activityShowInfo.isRunning = true;
        activityShowInfo.rootOld = activityShowInfo.mRoot.getBackground();
        activityShowInfo.mContentView.setVisibility(View.INVISIBLE);
        //读取当前的背景图，如果卡的话，可以考虑缓存bitmap
        File bgfile = new File(AppsSettings.get().getCoreSkinPath(), Constants.CORE_SKIN_BG);
        if (bgfile.exists()) {
//            .getApplicationContext()
            Glide.with(activity).load(bgfile)
                    .signature(new StringSignature(bgfile.getName() + bgfile.lastModified()))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(activityShowInfo.mViewTarget);
        } else {
            Glide.with(activity.getApplicationContext()).load(R.drawable.bg).into(activityShowInfo.mViewTarget);
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);//强制为横屏
        setFullScreen(activity, activityShowInfo);
    }

    private static void hideLoadingBg(Activity activity, ActivityShowInfo activityShowInfo) {
        mLogo = null;
        activityShowInfo.mContentView.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 16) {
            activityShowInfo.mRoot.setBackground(activityShowInfo.rootOld);
        } else {
            activityShowInfo.mRoot.setBackgroundDrawable(activityShowInfo.rootOld);
        }
        activity.setRequestedOrientation(activityShowInfo.oldRequestedOrientation);
        quitFullScreen(activity, activityShowInfo);
    }

    public static ActivityShowInfo onCreated(Activity activity) {
        ActivityShowInfo activityShowInfo = Infos.get(activity);
        if (activityShowInfo == null) {
            activityShowInfo = new ActivityShowInfo();
            Infos.put(activity, activityShowInfo);
//            Log.i("checker", "init:" + activity);
        }
        activityShowInfo.oldRequestedOrientation = activity.getRequestedOrientation();
//        Log.w("checker", "activityShowInfo.oldRequestedOrientation=" + activityShowInfo.oldRequestedOrientation);
        if (activityShowInfo.oldRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activityShowInfo.oldRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        activityShowInfo.mRoot = activity.getWindow().getDecorView();
        activityShowInfo.mViewTarget = new ViewTargetPlus(activityShowInfo.mRoot);
        activityShowInfo.mContentView = activityShowInfo.mRoot.findViewById(android.R.id.content);
        activityShowInfo.rootOld = activityShowInfo.mRoot.getBackground();
        if (activity instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                activityShowInfo.hasSupperbar = actionBar.isShowing();
            }
        } else {
            android.app.ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                activityShowInfo.hasBar = actionBar.isShowing();
            }
        }
        return activityShowInfo;
    }

    public static void onDestroy(Activity activity) {
        Infos.remove(activity);
    }

    public static void onResumed(Activity activity) {
        ActivityShowInfo activityShowInfo = Infos.get(activity);
//        Log.i("checker", "resume:" + activity);
        if (activityShowInfo == null) {
            return;
        }
        if (!activityShowInfo.isFirst) {
            hideLoadingBg(activity, activityShowInfo);
        }
        activityShowInfo.isFirst = false;
        activityShowInfo.isRunning = false;
    }

    private static long lasttime = 0;

    public static void startGame(Activity activity, YGOGameOptions options) {
        //如果距离上次加入游戏的时间大于1秒才处理
        if (System.currentTimeMillis() - lasttime >= 1000) {
            lasttime = System.currentTimeMillis();
            Log.e("YGOStarter", "设置背景前" + System.currentTimeMillis());
            //显示加载背景
            showLoadingBg(activity);
            Log.e("YGOStarter", "设置背景后" + System.currentTimeMillis());
            if (!ComponentUtils.isActivityRunning(activity, new ComponentName(activity, YGOMobileActivity.class))) {
                //random carddetail first
                VUiKit.defer().when(() -> {
                    mCardManager = DataManager.get().getCardManager();
                    mStringManager = DataManager.get().getStringManager();
                    cards = mCardManager.getAllCards();
                }).fail((e) -> {
                    //if failed, random tips second
                    String[] tipsList = activity.getResources().getStringArray(R.array.tips);
                    int x = (int) (Math.random() * tipsList.length);
                    String tips = tipsList[x];
                    Toast.makeText(activity, e + "", Toast.LENGTH_LONG).show();
                }).done((list) -> {
                    int y = (int) (Math.random() * cards.size());
                    Card cardinfo = cards.valueAt(y);
                    RandomCardDetail(activity, cardinfo);
                });
            }
        }
        Intent intent = new Intent(activity, YGOMobileActivity.class);
        if (options != null) {
            intent.putExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY, options);
            intent.putExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, System.currentTimeMillis());
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.e("YGOStarter", "跳转前" + System.currentTimeMillis());
        activity.startActivity(intent);
        Log.e("YGOStarter", "跳转后" + System.currentTimeMillis());
    }

    private static HashMap<Activity, ActivityShowInfo> Infos = new HashMap<>();

    private static class ActivityShowInfo {
        //根布局
        View mRoot;
        ViewTarget mViewTarget;
        //是否显示了标题栏
        boolean hasSupperbar;
        //是否显示了标题栏
        boolean hasBar;
        View mContentView;
        //activity背景
        Drawable rootOld;
        boolean isFirst = true;
        //屏幕方向
//        screenOrientations属性共有7中可选值(常量定义在 android.content.pm.ActivityInfo类中)：
//1.landscape：横屏(风景照)，显示时宽度大于高度；
//2.portrait：竖屏(肖像照)， 显示时高度大于宽度；
//3.user：用户当前的首选方向；
//4.behind：继承Activity堆栈中当前Activity下面的那个Activity的方向；
//5.sensor：由物理感应器决定显示方向，它取决于用户如何持有设备，当设备被旋转时方向会随之变化——在横屏与竖屏之间；
//6.nosensor：忽略物理感应器——即显示方向与物理感应器无关，不管用户如何旋转设备显示方向都不会随着改变("unspecified"设置除外)；
//7.unspecified：未指定，此为默认值，由Android系统自己选择适当的方向，选择策略视具体设备的配置情况而定，因此不同的设备会有不同的方向选择；
        int oldRequestedOrientation;
        boolean isRunning = false;
    }
}

package cn.garymb.ygomobile.ui.cards;

import static android.view.View.inflate;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.CardUtils;
import ocgcore.DataManager;
import ocgcore.PackManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

public class CardDetailRandom {
    private final View viewCardDetail;
    private final ImageView cardImage;
    private final TextView pack_name;
    private final TextView name;
    private final TextView desc;
    private final TextView level;
    private final TextView type;
    private final TextView race;
    private final TextView cardAtk;
    private final TextView cardDef;
    private final TextView attrView;
    private final View monsterlayout;
    private final View atkdefView;
    private final View textdefView;
    private final StringManager mStringManager;
    private final PackManager mPackManager;
    private final Context mContext;

    private static CardDetailRandom sCardDetailRandom = null;

    /**
     * 随机一卡的总显示时长：取 {@code Toast.LENGTH_LONG} 的实际观感时长（3.5s）。
     * 自行计时而非依赖 Toast 自身时长，旋转重投时才能只补剩余时间而不把总时长拉长。
     */
    private static final long SHOW_DURATION_MS = 3500L;
    /** 重投后的最少可见时间，避免贴到期时重 show 成一闪而过 */
    private static final long MIN_SHOW_MS = 500L;

    /** 当前正在显示的随机一卡 Toast（独立窗口，会盖在随后启动的 YGOProActivity 之上） */
    private static Toast sShowingToast;
    /** 本次显示的到期时刻（uptimeMillis 基准），重 show 时只补到该时刻 */
    private static long sDeadlineUptime;
    /** 上一次已生效的屏幕方向，方向未变时重定位直接跳过 */
    private static int sAppliedOrientation = Configuration.ORIENTATION_UNDEFINED;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    /** 到期收起：显式 cancel，保证总时长由本类拍板而不是 Toast 自己的时长 */
    private static final Runnable sAutoHide = new Runnable() {
        @Override
        public void run() {
            hideNow();
        }
    };

    private CardDetailRandom(Context context, Card cardInfo) {
        mContext = context;
        viewCardDetail = inflate(context, R.layout.dialog_cardinfo_small, null);
        cardImage = viewCardDetail.findViewById(R.id.card_image_toast);
        pack_name = viewCardDetail.findViewById(R.id.pack_name);
        name = viewCardDetail.findViewById(R.id.card_name_toast);
        monsterlayout = viewCardDetail.findViewById(R.id.star_attr_race_toast);
        level = viewCardDetail.findViewById(R.id.card_level_toast);
        race = viewCardDetail.findViewById(R.id.card_race_toast);
        attrView = viewCardDetail.findViewById(R.id.card_attr_toast);
        type = viewCardDetail.findViewById(R.id.card_type_toast);
        cardAtk = viewCardDetail.findViewById(R.id.card_atk_toast);
        cardDef = viewCardDetail.findViewById(R.id.card_def_toast);
        atkdefView = viewCardDetail.findViewById(R.id.layout_atkdef2_toast);
        textdefView = viewCardDetail.findViewById(R.id.TextDef_toast);
        desc = viewCardDetail.findViewById(R.id.text_desc_toast);

        mStringManager = DataManager.get().getStringManager();
        mPackManager = DataManager.get().getPackManager();

        pack_name.setText(mPackManager.findPackNameById(cardInfo.getCode()));
        name.setText(cardInfo.Name);
        type.setText(CardUtils.getAllTypeString(cardInfo, mStringManager).replace("/", "|"));
        attrView.setText(mStringManager.getAttributeString(cardInfo.Attribute));
        if (cardInfo.Desc.length() >= 100) desc.setTextSize(10);
        if (cardInfo.Desc.length() >= 160) desc.setTextSize(8);
        if (cardInfo.Desc.length() >= 220) desc.setTextSize(6);
        desc.setText(cardInfo.Desc);
        if (cardInfo.isType(CardType.Monster)) {
            atkdefView.setVisibility(View.VISIBLE);
            race.setVisibility(View.VISIBLE);
            String star = "★" + cardInfo.getStar();
            level.setText(star);
            if (cardInfo.isType(CardType.Xyz)) {
                level.setTextColor(context.getResources().getColor(R.color.star_rank));
            } else {
                level.setTextColor(context.getResources().getColor(R.color.star));
            }
            cardAtk.setText((cardInfo.Attack < 0 ? "?" : String.valueOf(cardInfo.Attack)));
            //连接怪兽设置
            if (cardInfo.isType(CardType.Link)) {
                level.setVisibility(View.GONE);
                textdefView.setVisibility(View.INVISIBLE);
                cardDef.setText((cardInfo.getStar() < 0 ? "?" : "LINK-" + cardInfo.getStar()));
            } else {
                level.setVisibility(View.VISIBLE);
                textdefView.setVisibility(View.VISIBLE);
                cardDef.setText((cardInfo.Defense < 0 ? "?" : String.valueOf(cardInfo.Defense)));
            }
            race.setText(mStringManager.getRaceString(cardInfo.Race));
        } else {
            atkdefView.setVisibility(View.GONE);
            monsterlayout.setVisibility(View.GONE);
        }
        viewCardDetail.setRotationY(5);
    }

    public static CardDetailRandom genRandomCardDetail(Context context, ImageLoader imageLoader, Card cardInfo) {
        if (cardInfo == null) return null;
        // 新的随机卡取代旧展示：先收掉上一颗 Toast 与它的计时，否则旧浮层会与新内容叠在一起
        hideNow();
        CardDetailRandom cardDetailRandom = new CardDetailRandom(context, cardInfo);
        cardDetailRandom.bindCardImage(imageLoader, cardInfo);
        sCardDetailRandom = cardDetailRandom;
        return cardDetailRandom;
    }

    public void bindCardImage(ImageLoader imageLoader, Card cardInfo) {
        imageLoader.bindImage(cardImage, cardInfo, ImageLoader.Type.origin);
    }

    public View getView() {
        return viewCardDetail;
    }

    public void show(){
        showWithOrientation(currentDeviceOrientation(), SHOW_DURATION_MS);
    }

    /**
     * 按给定方向显示（或重新显示）随机一卡，并在 remainingMs 后收起。
     * <p>
     * 方向取设备全局配置而非 {@code mContext.getResources()}：本类的宿主是
     * ui.home.MainActivity，它在清单里写定 {@code screenOrientation="portrait"}，
     * 其配置恒为竖屏，拿它判方向会在“设备横着点开始游戏”时错放到顶部。
     */
    private void showWithOrientation(int orientation, long remainingMs) {
        long duration = Math.max(remainingMs, MIN_SHOW_MS);
        sAppliedOrientation = orientation;
        Toast toast = sShowingToast;
        if (toast == null) {
            toast = new Toast(mContext);
            toast.setView(viewCardDetail);
            // 自定义 Toast 的 duration 只接受 SHORT/LONG，真正的收放由 sAutoHide 按到期时刻控制
            toast.setDuration(Toast.LENGTH_LONG);
            sShowingToast = toast;
        }
        applyGravity(toast, orientation);
        toast.show();
        sMainHandler.removeCallbacks(sAutoHide);
        sDeadlineUptime = SystemClock.uptimeMillis() + duration;
        sMainHandler.postDelayed(sAutoHide, duration);
    }

    /**
     * 横屏贴屏幕左侧垂直居中（沿用原表现，不回归），竖屏贴屏幕顶部水平居中并避开状态栏。
     */
    private void applyGravity(Toast toast, int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, statusBarHeight());
        } else {
            toast.setGravity(Gravity.LEFT, 0, 0);
        }
    }

    /** 状态栏高度（供竖屏顶部下推，不压在状态栏上）；取不到时返回 0 */
    private int statusBarHeight() {
        int id = mContext.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id <= 0) return 0;
        return mContext.getResources().getDimensionPixelSize(id);
    }

    /** 设备当前方向（全局配置，跟随真实旋转） */
    private static int currentDeviceOrientation() {
        return Resources.getSystem().getConfiguration().orientation;
    }

    /**
     * 屏幕方向变化后重定位（由 YGOProActivityUi 的重建入口转发）：
     * 仍在显示期内且方向确实变了才重投，剩余时长接着原到期时刻走，总显示时间不变。
     * cancel + re-show 会有一次“瞬移”，自定义 Toast 无入场动画，观感即为位置直接切换。
     *
     * @param orientation {@link Configuration#ORIENTATION_PORTRAIT} / {@code ORIENTATION_LANDSCAPE}
     */
    public static void onOrientationChanged(int orientation) {
        CardDetailRandom shown = sCardDetailRandom;
        if (shown == null || sShowingToast == null || orientation == sAppliedOrientation) {
            return;
        }
        long remaining = sDeadlineUptime - SystemClock.uptimeMillis();
        //剩余不足一次重投的最短可见量：不再重 show。既不会因为把 remaining 拔高到下限而
        //把总时长拉长，也封住了“系统已按时收起、但我们的 autoHide 还差几毫秒未到”时
        //把已经结束的提示又显回去的尾巴窗口
        if (remaining < MIN_SHOW_MS) {
            return;
        }
        sShowingToast.cancel();
        shown.showWithOrientation(orientation, remaining);
    }

    /** 立即收起当前显示（幂等）：同时清掉未到期计时与 Toast 引用 */
    private static void hideNow() {
        sMainHandler.removeCallbacks(sAutoHide);
        if (sShowingToast == null) return;
        sShowingToast.cancel();
        sShowingToast = null;
    }
}

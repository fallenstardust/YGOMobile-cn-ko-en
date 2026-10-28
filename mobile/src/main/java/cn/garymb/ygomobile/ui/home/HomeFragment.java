package cn.garymb.ygomobile.ui.home;

/**
 * Public entry of the split inheritance chain (HomeFragmentBase <- this).
 * Keeps the fragment view creation, the banner carousel init, the MyCard
 * news lookup (qualified HomeFragment.this) and the click dispatcher.
 */

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;
import static cn.garymb.ygomobile.ui.home.HomeActivity.pre_code_list;
import static cn.garymb.ygomobile.ui.home.HomeActivity.released_code_list;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.newIDsArray;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.oldIDsArray;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ourygo.lib.duelassistant.listener.OnDuelAssistantListener;
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement;
import com.ourygo.lib.duelassistant.util.Util;
import com.stx.xhb.androidx.XBanner;
import com.tubb.smrv.SwipeMenuRecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.bean.events.ExCardEvent;
import cn.garymb.ygomobile.bean.events.ServerInfoEvent;
import cn.garymb.ygomobile.ex_card.ExCardActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.cards.CardDetailRandom;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckManageDialog;
import cn.garymb.ygomobile.ui.mycard.bean.McNews;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.DefWebViewClient;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.Shimmer;
import cn.garymb.ygomobile.ui.widget.ShimmerTextView;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.ServerUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.hutool.core.util.ArrayUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class HomeFragment extends HomeFragmentBase {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (isHorizontal)
            layoutView = inflater.inflate(R.layout.fragment_home_horizontal, container, false);
        else
            layoutView = inflater.inflate(R.layout.fragment_home, container, false);
        initView(layoutView);
        initBanner(layoutView, savedInstanceState);
        //初始化决斗助手
        initDuelAssistant();
        activity = (HomeActivity) getActivity();
        mBundle = new Bundle();
        //event
        if (!EventBus.getDefault().isRegistered(this)) {//加上判断
            EventBus.getDefault().register(this);
        }
        ServerUtil.initExCardState();//LogoActivity中会调用一次本函数，此处再次调用的原因：有时HomeFragment的onCreateView()函数执行较慢，导致initExCardState()中eventbus事件发布完毕后仍未注册，因此在此处再调用一次检查，再次发布
        changeColor();
        try {
            ServerUtil.refreshServer(activity);
        } catch (IOException e) {
            Log.e(TAG, e + "");
        }
        //showNewbieGuide("homePage");
        return layoutView;
    }
    //轮播图
    public void initBanner(View view, Bundle saveBundle) {
        xb_banner = view.findViewById(R.id.xb_banner);
        cv_banner = view.findViewById(R.id.cv_banner);
        cv_banner.post(() -> {
            ViewGroup.LayoutParams layoutParams = cv_banner.getLayoutParams();
            if (isHorizontal) {
                layoutParams.width = cv_banner.getWidth();
                layoutParams.height = layoutParams.width / 5;
            } else {
                layoutParams.width = cv_banner.getWidth();
                layoutParams.height = layoutParams.width / 3;
            }
            cv_banner.setLayoutParams(layoutParams);
        });
        tv_banner_loading = view.findViewById(R.id.tv_banner_loading);
        tv_banner_loading.setOnClickListener(this);
        xb_banner.setOnItemClickListener((banner, model, v, position) -> {
            String newsUrl = mcNewsList.get(position).getNews_url();
            
            // 检查URL是否有效
            if (newsUrl == null || newsUrl.isEmpty()) {
                YGOUtil.showTextToast(R.string.loading_failed);
                return;
            }
            
            if (newsUrl.startsWith(MyCard.MYCARD_POST_URL)) {
                WebActivity.open(getContext(), getString(R.string.McNews), newsUrl);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(newsUrl));
                
                // 检查是否有可以处理该Intent的Activity
                if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    YGOUtil.showTextToast(R.string.loading_failed);
                }
            }

        });
        xb_banner.loadImage((banner, model, v, position) -> {
            TextView tv_time, tv_title, tv_type;
            ImageView iv_image;

            tv_time = v.findViewById(R.id.tv_time);
            tv_title = v.findViewById(R.id.tv_title);
            tv_type = v.findViewById(R.id.tv_type);
            iv_image = v.findViewById(R.id.iv_image);

            mcNews = mcNewsList.get(position);
            ImageUtil.setImageAndBackground(getContext(), mcNews.getImage_url(), iv_image);
            tv_time.setText(mcNews.getCreate_time());
            tv_title.setText(mcNews.getTitle());
            tv_type.setVisibility(View.GONE);
        });
        if (saveBundle == null) {
            findMcNews();
        } else {
            mcNewsList = (ArrayList<McNews>) saveBundle.getSerializable(ARG_MC_NEWS_LIST);
            if (mcNewsList != null)
                handler.sendEmptyMessage(TYPE_BANNER_QUERY_OK);
            else
                findMcNews();
            handler.sendEmptyMessage(TYPE_RES_LOADING_OK);
        }
    }
    protected void findMcNews() {
        isMcNewsLoadException = false;
        tv_banner_loading.setVisibility(View.VISIBLE);
        tv_banner_loading.setText(R.string.loading);
        MyCard.findMyCardNews((myCardNewsList, exception) -> {
            Message message = new Message();
            if (TextUtils.isEmpty(exception)) {
                while (myCardNewsList.size() > 5) {
                    myCardNewsList.remove(myCardNewsList.size() - 1);
                }
                HomeFragment.this.mcNewsList = (ArrayList<McNews>) myCardNewsList;
                message.what = TYPE_BANNER_QUERY_OK;
            } else {
                Log.e("HomeFragemnt", "查询失败" + exception);
                message.obj = exception;
                message.what = TYPE_BANNER_QUERY_EXCEPTION;
            }
            handler.sendMessage(message);
        });
    }
    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.action_game:
                setRandomCardDetail();
                if (mCardDetailRandom != null) {
                    mCardDetailRandom.show();
                }
                openGame();
                break;
            case R.id.action_replay:
                YGOStarter.startGame(getActivity(), null, "-k", "-r");
                //getFragmentManager().beginTransaction().remove(activity.fragment_deck_cards).commit();
                break;
            case R.id.action_bot:
                YGOStarter.startGame(getActivity(), null, "-k", "-s");
                //getFragmentManager().beginTransaction().remove(activity.fragment_deck_cards).commit();
                break;
            case R.id.action_download_ex:

                /*不检查先行卡设置，无论是否开启先行卡都要求能够打开先行卡页面，因此注释掉下面代码*/
//                if (!AppsSettings.get().isReadExpansions()) {//如果未开启扩展卡设置，直接跳过
//                    Toast.makeText(getActivity(), R.string.ypk_go_setting, Toast.LENGTH_LONG).show();
//                    break;
//                }

                /* 查不到版本号也打开excard安卓原生activity */
                Intent exCardIntent = new Intent(getActivity(), ExCardActivity.class);
                startActivity(exCardIntent);

                break;
            case R.id.action_help: {
                final DialogPlus dialog = new DialogPlus(getContext());
                dialog.setContentView(R.layout.dialog_help);
                dialog.setTitle(R.string.question);
                dialog.show();
                View viewDialog = dialog.getContentView();
                Button btnMasterRule = viewDialog.findViewById(R.id.masterrule);
                Button btnTutorial = viewDialog.findViewById(R.id.tutorial);

                btnMasterRule.setOnClickListener(v1 -> {
                    WebActivity.open(getContext(), getString(R.string.masterrule), Constants.URL_MASTER_RULE_CN);
                    dialog.dismiss();
                });
                btnTutorial.setOnClickListener(v1 -> {
                    WebActivity.open(getContext(), getString(R.string.help), Constants.URL_HELP);
                    dialog.dismiss();
                });

            }
            break;
            case R.id.nav_webpage: {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(Constants.URL_DONATE));
                YGOUtil.showTextToast(R.string.donatefor, Toast.LENGTH_LONG);
                startActivity(intent);
            }
            break;
            case R.id.return_to_duel: {
                openGame();
            }
            break;
            case R.id.add_server:
                mServerListManager.addServer();
                break;
            case R.id.tv_banner_loading:
                if (isMcNewsLoadException)
                    findMcNews();
                break;
        }
    }
    /*//https://www.jianshu.com/p/99649af3b191
    public void showNewbieGuide(String scene) {
        HighlightOptions options = new HighlightOptions.Builder()//绘制一个高亮虚线圈
                .setOnHighlightDrewListener(new OnHighlightDrewListener() {
                    @Override
                    public void onHighlightDrew(Canvas canvas, RectF rectF) {
                        Paint paint = new Paint();
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(20);
                        paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                        canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2 + 10, paint);
                    }
                }).build();
        HighlightOptions options2 = new HighlightOptions.Builder()//绘制一个高亮虚线矩形
                .setOnHighlightDrewListener(new OnHighlightDrewListener() {
                    @Override
                    public void onHighlightDrew(Canvas canvas, RectF rectF) {
                        Paint paint = new Paint();
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(20);
                        paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                        canvas.drawRect(rectF, paint);
                    }
                }).build();
        if (scene == "homePage") {
            NewbieGuide.with(this)//with方法可以传入Activity或者Fragment，获取引导页的依附者
                    .setLabel("homepageGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.cv_banner), HighLight.Shape.RECTANGLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            //可只创建一个引导layout并把相关内容都放在其中并GONE，获得ID并初始化相应为显示
                                            TextView tv = view.findViewById(R.id.text_about);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_view_banner);
                                        }
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.list_server), HighLight.Shape.ROUND_RECTANGLE, options2)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_right);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_serverlist);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_right);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_serverlist);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_right);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_serverlist);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.action_help), HighLight.Shape.RECTANGLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_left);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_help);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.nav_webpage), HighLight.Shape.RECTANGLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_left);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_help);
                                        }
                                    })
                    )
                    //.alwaysShow(true)//总是显示，调试时可以打开
                    .show();
        } else if (scene == "joinRoom") {
            NewbieGuide.with(this)
                    .setLabel("joinRoomGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(YGOUtil.c(R.color.black_bc))
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            view.findViewById(R.id.view_abt_join_room).setVisibility(View.VISIBLE);
                                        }
                                    })

                    )
                    .show();
        }
    }*/
}

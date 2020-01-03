package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;

import com.base.bj.paysdk.domain.TrPayResult;
import com.base.bj.paysdk.listener.PayResultListener;
import com.base.bj.paysdk.utils.TrPay;

import java.net.URLEncoder;
import java.util.UUID;

import cn.garymb.ygomobile.ui.plus.DialogPlus;

import static cn.garymb.ygomobile.Constants.ALIPAY_URL;

public class PayUtils {
    /***
     *支付宝（弃用
     */
    public static boolean openAlipayPayPage(Context context, String qrcode) {
        try {
            qrcode = URLEncoder.encode(ALIPAY_URL, "utf-8");
        } catch (Exception e) {
        }
        try {
            final String alipayqr = "alipayqr://platformapi/startapp?saId=10000007&clientVersion=3.7.0.0718&qrcode=" + qrcode;
            openUri(context, alipayqr + "%3F_s%3Dweb-other&_t=" + System.currentTimeMillis());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void openUri(Context context, String s) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(s));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /***
     *  图灵trpay
     */
    public static void inputMoney(Activity activity) {
        DialogPlus dialog = new DialogPlus(activity);
        dialog.setTitle("输入捐赠金额(元)");
        EditText editText = new EditText(activity);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine();
        dialog.show();
        dialog.setContentView(editText);
        dialog.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
        });
        dialog.setLeftButtonListener((dlg, s) -> {
            String message = editText.getText().toString().trim();
            if (!message.equals("")) {
                long money = Long.parseLong(message);
                if (money != 0) {
                    money = money * 100;
                    callPay(activity, money);
                    dlg.dismiss();
                }
            }
        });
    }


    public static void callPay(Activity activity, Long money) {
        String id = UUID.randomUUID() + "";
        String notifyurl = "192.168.1.1";
        TrPay.getInstance(activity).callPay("感谢您对YGOMobile的支持", id, money, "", notifyurl, PayUtils.getID(activity), new PayResultListener() {
            public void onPayFinish(Context context, String outtradeno, int resultCode, String resultString, int payType, Long amount, String tradename) {
                if (resultCode == TrPayResult.RESULT_CODE_SUCC.getId()) {
                } else if (resultCode == TrPayResult.RESULT_CODE_FAIL.getId()) {
                }
            }
        });
    }

    public static String getID(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = telephonyManager.getDeviceId();
        return imei;
    }
}

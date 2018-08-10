package cn.garymb.ygomobile.ui.mycard.mcchat.util;

/**
 * 连接监听类
 */

import android.util.Log;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;

import java.util.Timer;
import java.util.TimerTask;

import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;


public class TaxiConnectionListener implements ConnectionListener {

    @Override
    public void connected(XMPPConnection p1) {
        Log.e("TaxiConnectionListener", "开始连接");
        //连接
        // TODO: Implement this method
    }

    @Override
    public void authenticated(XMPPConnection p1, boolean p2) {
        Log.e("TaxiConnectionListener", "通过身份验证");
        //通过身份验证
        // TODO: Implement this method
    }

    @Override
    public void reconnectionSuccessful() {
        //重新连接成功
        Log.e("TaxiConnectionListener", "重连成功");
        // TODO: Implement this method
    }

    @Override
    public void reconnectingIn(int p1) {
        //正在重连
        Log.e("TaxiConnectionListener", "正在重连" + p1);
        // TODO: Implement this method
    }

    @Override
    public void reconnectionFailed(Exception p1) {
        //重新连接失败
        Log.e("重连失败", "失败" + p1);
        // TODO: Implement this method
    }

    private Timer tExit;
    private String username;
    private String password;
    private int logintime = 2000;
    private ServiceManagement sm = ServiceManagement.getDx();

    @Override
    public void connectionClosed() {
        //正常关闭连接
        Log.e("TaxiConnectionListener", "连接关闭");
        // 重连服务器
        //tExit = new Timer();
        //tExit.schedule(new timetask(), logintime);
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        //非正常关闭连接
        Log.e("TaxiConnectionListener", "连接关闭异常" + e);
        sm.setIsListener(false);
        // 重连服务器
        tExit = new Timer();
        tExit.schedule(new timetask(), logintime);

    }

    class timetask extends TimerTask {
        @Override
        public void run() {
            username = UserManagement.getUserName();
            password = UserManagement.getUserPassword();
            if (username != null && password != null) {
                sm.setreLogin(false);
                Log.e("TaxiConnectionListener", "尝试登录");
                // 连接服务器
                try {
                    if (sm.login(username, password)) {
                        sm.setreLogin(true);
                        Log.e("TaxiConnectionListener", "登录成功");
                        tExit.schedule(new timeJoin(), logintime);
                    } else {
                        Log.e("TaxiConnectionListener", "重新登录");
                        tExit.schedule(new timetask(), logintime);
                    }
                } catch (Exception e) {
                    tExit.schedule(new timetask(), logintime);
                }

            }
        }

        class timeJoin extends TimerTask {
            @Override
            public void run() {
                sm.setreJoin(false);
                Log.e("TaxiConnectionListener", "尝试加入房间");
                try {
                    sm.joinChat();
                    sm.setreJoin(true);
                    Log.e("TaxiConnectionListener", "加入房间成功");
                } catch (Exception e) {
                    Log.e("TaxiConnectionListener", "重新加入房间");
                    tExit.schedule(new timeJoin(), logintime);
                }


                // TODO: Implement this method
            }
        }

    }

}

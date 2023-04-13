package cn.garymb.ygomobile.ui.mycard.mcchat.management;

import cn.garymb.ygomobile.ui.mycard.bean.McUser;

public class UserManagement {
    private static final UserManagement userManagement = new UserManagement();
    private McUser mcUser;
    private UserManagement() {
    }

    public McUser getMcUser() {
        return mcUser;
    }

    public void setMcUser(McUser mcUser) {
        this.mcUser = mcUser;
    }

    public static UserManagement getDx() {
        return userManagement;
    }

    public void logout() {
        mcUser=null;
    }
}

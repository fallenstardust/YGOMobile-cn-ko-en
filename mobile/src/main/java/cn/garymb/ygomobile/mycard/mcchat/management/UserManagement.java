package cn.garymb.ygomobile.mycard.mcchat.management;

public class UserManagement {
    private static UserManagement um = new UserManagement();
    private static String userName, userPassword;

    private UserManagement() {


    }


    public static String getUserName() {
        return userName;
    }

    public static void setUserName(String name) {

        userName = name;

    }

    public static String getUserPassword() {
        return userPassword;
    }

    public static void setUserPassword(String password) {

        userPassword = password;
    }

    public static UserManagement getDx() {
        return um;
    }
}

package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

public class LoginToken {
    Integer userId;
    String serverToken;

    public LoginToken(Integer userId, String serverToken) {
        this.userId = userId;
        this.serverToken = serverToken;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getServerToken() {
        return serverToken;
    }

    public void setServerToken(String serverToken) {
        this.serverToken = serverToken;
    }

    @Override
    public String toString() {
        return "LoginToken{" +
                "userId=" + userId +
                ", serverToken='" + serverToken + '\'' +
                '}';
    }
}

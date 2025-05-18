package cn.garymb.ygomobile.deck_square.api_response;

public class LoginRequest {
    public Integer account;
    public String password;

    public LoginRequest(Integer account, String password) {
        this.account = account;
        this.password = password;
    }
}

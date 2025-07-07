package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

public class LoginRequest {
    public String account;
    public String password;

    public LoginRequest(String account, String password) {
        this.account = account;
        this.password = password;
    }
}

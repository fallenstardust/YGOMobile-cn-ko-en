package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

public class LoginResponse {
    public String token;

    public Boolean success;

    public User user;

    public static class User {
        public int id;
        public String username;
    }

}

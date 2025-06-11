package cn.garymb.ygomobile.ui.mycard.bean;

import java.io.Serializable;

/**
 * Create By feihua  On 2022/8/27
 */
public class McUser implements Serializable {

    private int id;
    private int external_id;
    private String username;

    private String token;
    private String email;
    private String avatar_url;
    private boolean admin;
    private boolean moderator;

    public McUser() {

    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getJID() {
        return username + "@mycard.moe";
    }

    public String getPassword() {
        return String.valueOf(external_id);
    }

    public String getConference() {
        return "ygopro_china_north@conference.mycard.moe";
    }

    public int getExternal_id() {
        return external_id;
    }

    public void setExternal_id(int external_id) {
        this.external_id = external_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setName(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatar_url() {
        return avatar_url;
    }

    public void setAvatar_url(String avatar_url) {
        this.avatar_url = avatar_url;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isModerator() {
        return moderator;
    }

    public void setModerator(boolean moderator) {
        this.moderator = moderator;
    }
}
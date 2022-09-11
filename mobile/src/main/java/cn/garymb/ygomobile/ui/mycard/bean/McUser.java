package cn.garymb.ygomobile.ui.mycard.bean;

import org.litepal.crud.LitePalSupport;

/**
 * Create By feihua  On 2021/10/26
 */
public class McUser extends LitePalSupport {
    private int id;
   private int external_id;
    private String username;
    private String name;
    private String email;
    private String avatar_url;
    private boolean admin;
    private boolean moderator;
    private boolean login;

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

    /**
     * 不要用这个，用getUserName
     * @return
     */
    @Deprecated
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isLogin() {
        return login;
    }

    public void setLogin(boolean login) {
        this.login = login;
    }
}
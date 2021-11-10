package com.ourygo.ygomobile.bean;

import android.text.TextUtils;
import android.util.Log;

import java.util.List;

/**
 * Create By feihua  On 2021/11/4
 */

public class DuelRoom {
    public static final int TYPE_ARENA_NO = -1;
    public static final int TYPE_ARENA_MATCH = 0;
    public static final int TYPE_ARENA_FUN = 1;
    public static final int TYPE_ARENA_FUN_TAG = 2;
    public static final int TYPE_ARENA_FUN_SINGLE = 3;
    public static final int TYPE_ARENA_FUN_MATCH = 4;
    public static final int TYPE_ARENA_AI = 5;



    public static final String EVENT_INIT = "init";
    public static final String EVENT_CREATE = "create";
    public static final String EVENT_UPDATE = "update";
    public static final String EVENT_DELETE = "delete";

    public static final int MODE_SINGLE=0;
    public static final int MODE_MATCH=1;
    public static final int MODE_TAG=2;

    private String id;
    private String title;
    private List<UserBean> users;
    private OptionsBean options;
    private String arena;
    private int arenaType;

    public DuelRoom() {
        arenaType = TYPE_ARENA_NO;
    }

    public int getArenaType() {
        return arenaType;
    }

    public void setArenaType(int arenaType) {
        this.arenaType = arenaType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<UserBean> getUsers() {
        return users;
    }

    public void setUsers(List<UserBean> users) {
        this.users = users;
    }

    public OptionsBean getOptions() {
        return options;
    }

    public void setOptions(OptionsBean options) {
        this.options = options;
    }

    public String getArena() {
        return arena;
    }

    public void setArena(String arena) {
        this.arena = arena;

    }

    public void setArenaType(String arena,String password,OptionsBean options){
        if (!TextUtils.isEmpty(arena)&&arena.equals("athletic")) {
            arenaType = TYPE_ARENA_MATCH;
        } else if (!TextUtils.isEmpty(arena)&&arena.equals("entertain")) {
            arenaType = TYPE_ARENA_FUN;
        }else if (password.startsWith("AI#")) {
                arenaType = TYPE_ARENA_AI;
        }else {
            switch (options.getMode()){
                case MODE_SINGLE:
                    arenaType = TYPE_ARENA_FUN_SINGLE;
                    break;
                case MODE_MATCH:
                    arenaType = TYPE_ARENA_FUN_MATCH;
                    break;
                case TYPE_ARENA_FUN_TAG:
                    arenaType = TYPE_ARENA_FUN_TAG;
                    break;
                default:
                    arenaType=TYPE_ARENA_NO;
            }
        }
    }

    public static class OptionsBean {
        private Integer lflist;
        private Integer rule;
        private Integer mode;
        private Integer duel_rule;
        private Boolean no_check_deck;
        private Boolean no_shuffle_deck;
        private Integer start_lp;
        private Integer start_hand;
        private Integer draw_count;
        private Integer time_limit;
        private Boolean no_watch;
        private Boolean auto_death;
        private Integer replay_mode;

        public Integer getLflist() {
            return lflist;
        }

        public void setLflist(Integer lflist) {
            this.lflist = lflist;
        }

        public Integer getRule() {
            return rule;
        }

        public void setRule(Integer rule) {
            this.rule = rule;
        }

        public Integer getMode() {
            return mode;
        }

        public void setMode(Integer mode) {
            this.mode = mode;
        }

        public Integer getDuel_rule() {
            return duel_rule;
        }

        public void setDuel_rule(Integer duel_rule) {
            this.duel_rule = duel_rule;
        }

        public Boolean getNo_check_deck() {
            return no_check_deck;
        }

        public void setNo_check_deck(Boolean no_check_deck) {
            this.no_check_deck = no_check_deck;
        }

        public Boolean getNo_shuffle_deck() {
            return no_shuffle_deck;
        }

        public void setNo_shuffle_deck(Boolean no_shuffle_deck) {
            this.no_shuffle_deck = no_shuffle_deck;
        }

        public Integer getStart_lp() {
            return start_lp;
        }

        public void setStart_lp(Integer start_lp) {
            this.start_lp = start_lp;
        }

        public Integer getStart_hand() {
            return start_hand;
        }

        public void setStart_hand(Integer start_hand) {
            this.start_hand = start_hand;
        }

        public Integer getDraw_count() {
            return draw_count;
        }

        public void setDraw_count(Integer draw_count) {
            this.draw_count = draw_count;
        }

        public Integer getTime_limit() {
            return time_limit;
        }

        public void setTime_limit(Integer time_limit) {
            this.time_limit = time_limit;
        }

        public Boolean getNo_watch() {
            return no_watch;
        }

        public void setNo_watch(Boolean no_watch) {
            this.no_watch = no_watch;
        }

        public Boolean getAuto_death() {
            return auto_death;
        }

        public void setAuto_death(Boolean auto_death) {
            this.auto_death = auto_death;
        }

        public Integer getReplay_mode() {
            return replay_mode;
        }

        public void setReplay_mode(Integer replay_mode) {
            this.replay_mode = replay_mode;
        }
    }

    public static class UserBean {
        private String username;
        private Integer position;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Integer getPosition() {
            return position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }
    }


}

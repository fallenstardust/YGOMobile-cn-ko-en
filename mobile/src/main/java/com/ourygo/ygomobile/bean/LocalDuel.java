package com.ourygo.ygomobile.bean;

public class LocalDuel {

    private int id;
    private String name;
    private String message;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static LocalDuel toLocalDuel(int id,String name, String message) {
        LocalDuel localDuel = new LocalDuel();
        localDuel.setId(id);
        localDuel.setName(name);
        localDuel.setMessage(message);
        return localDuel;
    }

}

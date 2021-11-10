package com.ourygo.ygomobile.bean;

public class OYSelect {
    public static final int ID_NULL=-500;

    private int id;
    private String name;
    private String message;
    private Object object;
    private int position;

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public  static OYSelect tOYSelect(String name, String message, Object object){
        return tOYSelect(ID_NULL,name,message,object);
    }

    public  static OYSelect tOYSelect(int id,String name,String message,Object object){
        OYSelect oySelect=new OYSelect();
        oySelect.setId(id);
        oySelect.setName(name);
        oySelect.setMessage(message);
        oySelect.setObject(object);
        return oySelect;
    }

}

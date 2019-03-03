package com.ourygo.ygomobile.bean;

public class OYSelect {
    private String name;
    private String message;
    private Object object;

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

    public  static OYSelect tOYSelect(String name,String message,Object object){
        OYSelect oySelect=new OYSelect();
        oySelect.setName(name);
        oySelect.setMessage(message);
        oySelect.setObject(object);
        return oySelect;
    }

}

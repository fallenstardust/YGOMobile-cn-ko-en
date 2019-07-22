package cn.garymb.ygomobile.bean;

public class TextSelect {

    private String name;
    private Object object;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public static TextSelect toTextSelect(String name,Object object){
        TextSelect textSelect=new TextSelect();
        textSelect.setName(name);
        textSelect.setObject(object);
        return textSelect;
    }

}

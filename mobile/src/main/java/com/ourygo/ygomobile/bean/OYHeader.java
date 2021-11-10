package com.ourygo.ygomobile.bean;

/**
 * Create By feihua  On 2021/11/8
 */
public class OYHeader {

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final int HEADER_POSITION_AUTHORIZATION = 0;

    private String name;
    private String value;

    public OYHeader(int namePosition, String value) {
        this.value = value;
        setName(namePosition);
    }

    public String getName() {
        return name;
    }

    public void setName(int namePosition) {
        switch (namePosition) {
            case HEADER_POSITION_AUTHORIZATION:
                this.name = HEADER_AUTHORIZATION;
                break;
            default:
                this.name = "";
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

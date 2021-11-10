package com.ourygo.ygomobile.bean;

/**
 * Create By feihua  On 2021/11/3
 */
public class WebSocketEvent {
    private String message;

    public WebSocketEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
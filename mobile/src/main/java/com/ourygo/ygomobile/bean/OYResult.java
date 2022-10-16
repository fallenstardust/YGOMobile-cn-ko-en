package com.ourygo.ygomobile.bean;

import org.json.JSONObject;

import com.ourygo.ygomobile.util.JsonUtil;
import com.ourygo.ygomobile.util.Record;

import android.text.TextUtils;

/**
 * Create By feihua  On 2022/10/15
 */
public class OYResult {
    public static final int CODE_OK=200;

    private int code;
    private String message;
    private String exception;
    private JSONObject data;

    public OYResult(JSONObject jsonObject) {
        code = jsonObject.optInt(Record.ARG_CODE);
        message = jsonObject.optString(Record.ARG_MESSAGE);
        if (isException()) {
            exception = this.message;
        } else {
            data = jsonObject.optJSONObject(Record.ARG_DATA);
        }
        if (!TextUtils.isEmpty(message)&&message.startsWith("Error:")){
            exception = this.message;
        }
    }

    public OYResult(String message) {
        this.message = message;
        this.exception = message;
        code = -1;
    }

    public OYResult(Exception exception){
        this(exception+"");
    }

    public OYResult(int code){
        this.code=code;
    }

    public boolean isException(){
        return code != JsonUtil.CODE_OK && code !=0;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

package cn.garymb.ygomobile.deck_square.api_response;

import java.util.List;

public class MyDeckResponse {
    public Integer code;
    public String message;
    public List<MyOnlineDeckDetail> data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<MyOnlineDeckDetail> getData() {
        return data;
    }

    public void setData(List<MyOnlineDeckDetail> data) {
        this.data = data;
    }


}

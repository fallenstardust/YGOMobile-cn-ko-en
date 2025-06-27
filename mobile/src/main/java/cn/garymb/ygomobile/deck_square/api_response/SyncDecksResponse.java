package cn.garymb.ygomobile.deck_square.api_response;
/* 同步卡组响应体类*/
public class SyncDecksResponse {
    private Integer code;
    private String message;
    private Integer data;

    public SyncDecksResponse(Integer code, String message, Integer data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // getters and setters
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

    public Integer getData() {
        return data;
    }

    public void setData(Integer data) {
        this.data = data;
    }
}

package cn.garymb.ygomobile.deck_square.api_response;

public class DownloadDeckResponse {
    private Integer code;
    private String message;
    private MyOnlineDeckDetail data;

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

    public MyOnlineDeckDetail getData() {
        return data;
    }

    public void setData(MyOnlineDeckDetail data) {
        this.data = data;
    }
}

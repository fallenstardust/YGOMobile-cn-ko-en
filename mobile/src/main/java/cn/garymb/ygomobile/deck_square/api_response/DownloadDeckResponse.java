package cn.garymb.ygomobile.deck_square.api_response;

public class DownloadDeckResponse {
    public Integer code;
    public String message;
    public DeckDetail data;

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

    public DeckDetail getData() {
        return data;
    }

    public void setData(DeckDetail data) {
        this.data = data;
    }
}

package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

public class PushMultiResponse {


    private Integer code;
    private String message;
    //!!!!注意，本字段是integer，与PushSingleDeckResponse的不同！
    private Integer data;

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

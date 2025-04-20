package cn.garymb.ygomobile.deck_square.api_response;

//将卡组上传后，返回的响应
//对应接口http://rarnu.xyz:38383/api/mdpro3/sync/single
public class PushDeckResponse {


    private Integer code;
    private String message;
    private boolean data;

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

    public boolean isData() {
        return data;
    }

    public void setData(boolean data) {
        this.data = data;
    }
}

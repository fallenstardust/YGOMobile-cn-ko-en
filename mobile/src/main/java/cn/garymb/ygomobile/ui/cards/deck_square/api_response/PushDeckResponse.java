package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

//将卡组上传后，返回的响应
//对应接口http://rarnu.xyz:38383/api/mdpro3/sync/single
public class PushDeckResponse {


    private Integer code;
    private String message;
    private boolean data;//服务器的执行结果，true代表卡组上传成功。false代表卡组上传失败

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

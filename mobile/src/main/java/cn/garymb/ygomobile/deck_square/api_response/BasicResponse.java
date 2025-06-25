package cn.garymb.ygomobile.deck_square.api_response;

public class BasicResponse {

    private Integer code;
    private String message;

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

    /**
     * 后端设计的有点怪，接口成功时，message的内容为“true”
     * @return
     */
    public boolean isMessageTrue(){
        return message.equals("true");

    }
}

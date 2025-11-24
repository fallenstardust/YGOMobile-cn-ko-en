package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

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
        List<MyOnlineDeckDetail> data = this.data;
        //根据deckType排序，提高观感
        if (!data.isEmpty() || data != null) {
            data.sort((o1, o2) -> {
                String type1 = o1.getDeckType();
                String type2 = o2.getDeckType();

                if (type1 == null && type2 == null) return 0;
                if (type1 == null) return 1;
                if (type2 == null) return -1;
                return type1.compareTo(type2);
            });
        }

        return data;
    }

    public void setData(List<MyOnlineDeckDetail> data) {
        this.data = data;
    }


}

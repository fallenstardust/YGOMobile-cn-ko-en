package cn.garymb.ygomobile.deck_square.api_response;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DeckMultiIdResponse {


    @Expose
    public int code;
    @Expose
    public String message;

    @Expose
    @SerializedName("data")
    public List<String> deckId;

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

    public List<String> getDeckId() {
        return deckId;
    }

    public void setDeckId(List<String> deckId) {
        this.deckId = deckId;
    }

    @Override
    public String toString() {
        return "DeckMultiIdResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", deckId=" + deckId +
                '}';
    }
}

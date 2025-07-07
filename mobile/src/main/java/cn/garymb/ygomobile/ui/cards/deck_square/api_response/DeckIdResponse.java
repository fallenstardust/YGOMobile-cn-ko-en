package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class DeckIdResponse {


    @Expose
    public int code;
    @Expose
    public String message;

    @Expose
    @SerializedName("data")
    public String deckId;

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

    public String getDeckId() {
        return deckId;
    }

    public void setDeckId(String deckId) {
        this.deckId = deckId;
    }

    @Override
    public String toString() {
        return "DeckIdResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", deckId='" + deckId + '\'' +
                '}';
    }
}

package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

import java.util.List;

public class SquareDeckResponse {
    private Integer code;
    private String message;
    private ApiData data;

    public static class ApiData {
        private int current;
        private int size;
        private int total;
        private int pages;
        private List<OnlineDeckDetail> records;

        // Getters and setters
        public int getCurrent() {
            return current;
        }

        public int getSize() {
            return size;
        }

        public int getTotal() {
            return total;
        }

        public int getPages() {
            return pages;
        }

        public List<OnlineDeckDetail> getRecords() {
            return records;
        }
    }

    // Getters and setters
    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public ApiData getData() {
        return data;
    }
}

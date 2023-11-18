package cn.garymb.ygomobile.utils;

import android.text.TextUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ex_card.ExCard;

/**
 * 用于解析html网页内容的工具类
 */
public class WebParseUtil {
    /**
     * 检查字符串是否是IPv4
     */
    public static boolean isValidIP(String s) {
        if (TextUtils.isEmpty(s)) {
            return false;
        }
        String[] arr = s.split("\\.");
        if (arr.length != 4) {
            return false;
        }
        for (String value : arr) {
            try {
                int n = Integer.parseInt(value);
                if (!(n >= 0 && n <= 255)) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }


    public static List<ExCard> loadData() throws IOException {

        //Connect to the website
        Document document = Jsoup.connect(Constants.URL_YGO233_ADVANCE).get();
        Element pre_card_content = document.getElementById("pre_release_cards");
        Element tbody = pre_card_content.getElementsByTag("tbody").get(0);
        Elements cards = tbody.getElementsByTag("tr");
        if (cards.size() > 5000) {//Considering the efficiency of html parse, if the size of
            // pre cards list is to large, return null directly.
            return null;
        }
        ArrayList<ExCard> exCardList = new ArrayList<>();
        for (Element card : cards) {
            Elements card_attributes = card.getElementsByTag("td");
            String imageUrl = card_attributes.get(0).getElementsByTag("a").attr("href") + "!half";
            String name = card_attributes.get(1).text();
            String description = card_attributes.get(2).text();
            ExCard exCard = new ExCard(name, imageUrl, description, 0);
            exCardList.add(exCard);
        }

        if (exCardList.isEmpty()) {
            return null;
        } else {
            return exCardList;
        }


    }


}


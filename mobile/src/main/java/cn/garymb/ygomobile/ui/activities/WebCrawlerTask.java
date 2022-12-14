package cn.garymb.ygomobile.ui.activities;

import android.os.AsyncTask;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

import cn.garymb.ygomobile.Constants;

/**
 * Use the Web crawler Jsoup library to extract the information of pre card
 */
public class WebCrawlerTask extends AsyncTask<Void, Void, Void> {

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        //TODO add progress bar
//            progressDialog = new ProgressDialog(MainActivity.this);
//            progressDialog.show();
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            String aurl = Constants.URL_YGO233_ADVANCE;
            //Connect to the website
            Document document = Jsoup.connect(aurl).get();
            Element pre_card_content = document.getElementById("pre_release_cards");
            Element tbody = pre_card_content.getElementsByTag("tbody").get(0);
            Elements cards = tbody.getElementsByTag("tr");
            if (cards.size() > 10000) {//If the size of pre cards list is to large, return null directly.
                return null;
            }
            for (Element card : cards) {
                Elements card_attributes = card.getElementsByTag("td");
                String data0 = card_attributes.get(0).getElementsByTag("a").attr("href");
                String data1 = card_attributes.get(1).text();
                String data2 = card_attributes.get(2).text();

            }

            Log.i("webCrawler", pre_card_content.toString());

        } catch (IOException e) {
            e.printStackTrace();
        } catch (IndexOutOfBoundsException e) {
            //This exception may occur when the crawler code cannot parse the DOM correctly, so
            //we need to do some remedy.
            //TODO
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);


    }
}
package cn.garymb.ygomobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.ui.cards.DeckManagerActivity;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.lite.R;

import static cn.garymb.ygomobile.Constants.ACTION_OPEN_DECK;
import static cn.garymb.ygomobile.Constants.ACTION_OPEN_GAME;
import static cn.garymb.ygomobile.Constants.PATH_DECK;
import static cn.garymb.ygomobile.Constants.PATH_ROOM;
import static cn.garymb.ygomobile.Constants.QUERY_NAME;


public class GameUriManager {
    private Activity activity;

    public GameUriManager(Activity activity) {
        this.activity = activity;
    }

    public boolean doIntent(Intent intent) {
        if (ACTION_OPEN_DECK.equals(intent.getAction())) {
            if (intent.getData() != null) {
                doUri(intent.getData());
            } else {
                String name = intent.getStringExtra(Intent.EXTRA_TEXT);
                doOpenPath(name);
            }
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (intent.getData() != null) {
                doUri(intent.getData());
            } else {
                activity.finish();
            }
        } else if (ACTION_OPEN_GAME.equals(intent.getAction())) {
            try {
                YGOGameOptions options = new YGOGameOptions();
                options.mServerAddr = intent.getStringExtra(Constants.QUERY_HOST);
                options.mUserName = intent.getStringExtra(Constants.QUERY_USER);
                options.mPort = intent.getIntExtra(Constants.QUERY_PORT, 0);
                options.mRoomName = intent.getStringExtra(Constants.QUERY_ROOM);
                YGOStarter.startGame(getActivity(), options);
            } catch (Exception e) {
                Toast.makeText(getActivity(), R.string.start_game_error, Toast.LENGTH_SHORT).show();
                activity.finish();
            }
        }else{
            return false;
        }
        return true;
    }

    public Activity getActivity() {
        return activity;
    }

    private void doUri(Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            File file = new File(uri.getPath());
            Intent startdeck = new Intent(getActivity(), DeckManagerActivity.getDeckManager());
            startdeck.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
            activity.startActivity(startdeck);
        } else {
            String host = uri.getHost();
            if (!Constants.URI_HOST.equalsIgnoreCase(host)) {
                return;
            }
            String path = uri.getPath();
            if (PATH_DECK.equals(path)) {
                String name = uri.getQueryParameter(QUERY_NAME);
                if (!TextUtils.isEmpty(name)) {
                    doOpenPath(name);
                } else {
                    Deck deckInfo = new Deck(uri);
                    File file = deckInfo.saveTemp(AppsSettings.get().getDeckDir());
                    Intent startdeck = new Intent(getActivity(), DeckManagerActivity.getDeckManager());
                    startdeck.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
                    activity.startActivity(startdeck);
                }
            } else if (PATH_ROOM.equals(path)) {
                try {
                    YGOGameOptions options = new YGOGameOptions();
                    options.mServerAddr = uri.getQueryParameter(Constants.QUERY_HOST);
                    options.mUserName = uri.getQueryParameter(Constants.QUERY_USER);
                    options.mPort = Integer.parseInt(uri.getQueryParameter(Constants.QUERY_PORT));
                    options.mRoomName = uri.getQueryParameter(Constants.QUERY_ROOM);
                    YGOStarter.startGame(getActivity(), options);
                } catch (Exception e) {
                    Toast.makeText(getActivity(), R.string.start_game_error, Toast.LENGTH_SHORT).show();
                    activity.finish();
                }
            }
        }
    }

    private void doOpenPath(String name) {
        File deck = null;
        if (!TextUtils.isEmpty(name)) {
            deck = new File(name);
            if (!deck.exists()) {
                deck = new File(AppsSettings.get().getDeckDir(), name);
            }
        }
        if (deck != null && deck.exists()) {
            Intent startdeck = new Intent(getActivity(), DeckManagerActivity.getDeckManager());
            startdeck.putExtra(Intent.EXTRA_TEXT, deck.getAbsolutePath());
            activity.startActivity(startdeck);
        } else {
            Log.w("kk", "no find " + name);
            activity.finish();
        }
    }
}

package cn.garymb.ygomobile.ui.cards;

import android.app.Activity;

import cn.garymb.ygomobile.AppsSettings;

public class DeckManagerActivity extends DeckManagerActivityImpl {

    public static Class<? extends Activity> getDeckManager() {
        if (AppsSettings.get().isUseDeckManagerV2()) {
            return DeckManagerActivity3.class;
        }
        return DeckManagerActivity.class;
    }
}

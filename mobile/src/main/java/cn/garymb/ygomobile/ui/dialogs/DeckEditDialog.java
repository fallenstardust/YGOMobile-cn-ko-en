package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.content.Intent;

import cn.garymb.ygomobile.ui.home.HomeActivity;

public class DeckEditDialog {

    private Context context;

    public DeckEditDialog(Context context) {
        this.context = context;
    }

    public void show() {
        Intent intent = new Intent(context, HomeActivity.class);
        intent.putExtra("tab", 2);
        context.startActivity(intent);
    }


}

package cn.garymb.ygomobile.deck_square;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import cn.garymb.ygomobile.deck_square.api_response.ApiDeckRecord;
import cn.garymb.ygomobile.lite.R;

public class SquareDeckDetailDialog extends Dialog {

    public interface ActionListener {
        void onDownloadClicked();
        void onLikeClicked();
    }

    private ActionListener listener;

    public SquareDeckDetailDialog(Context context, ApiDeckRecord item) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_square_deck_detail);

        Button btnDownload = findViewById(R.id.btnDownload);
        Button btnLike = findViewById(R.id.btnLike);

        btnDownload.setOnClickListener(v -> {

            dismiss();
        });

        btnLike.setOnClickListener(v -> {

            dismiss();
        });
    }
}
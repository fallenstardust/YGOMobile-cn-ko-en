package cn.garymb.ygomobile.deck_square;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import cn.garymb.ygomobile.lite.R;

public class PrivacyDialogFragment extends DialogFragment {

    private PrivacyAgreementListener listener;

    public interface PrivacyAgreementListener {
        void onAgree();

        void onDisagree();
    }

    public void setPrivacyAgreementListener(PrivacyAgreementListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_privacy, null);
        builder.setView(view);

        // 禁止点击外部关闭
        setCancelable(false);

        Button btnAgree = view.findViewById(R.id.btn_agree);
        Button btnDisagree = view.findViewById(R.id.btn_disagree);

        btnAgree.setOnClickListener(v -> {
            if (listener != null) listener.onAgree();
            dismiss();
        });

        btnDisagree.setOnClickListener(v -> {
            if (listener != null) listener.onDisagree();
            dismiss();
        });

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 设置对话框宽度占屏幕 90%
        if (getDialog() != null && getDialog().getWindow() != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int width = (int) (metrics.widthPixels * 0.9);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
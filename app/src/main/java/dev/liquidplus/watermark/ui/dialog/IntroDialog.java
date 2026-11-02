package dev.liquidplus.watermark.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;

import dev.liquidplus.watermark.R;

public class IntroDialog extends Dialog {

    public interface OnStartClickListener {
        void onStartClick();
    }

    private final OnStartClickListener listener;

    public IntroDialog(@NonNull Context context, OnStartClickListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_intro);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(android.R.style.Animation_InputMethod);
        }

        AppCompatButton btnStart = findViewById(R.id.btnStartIntro);
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onStartClick();
            });
        }
    }
}

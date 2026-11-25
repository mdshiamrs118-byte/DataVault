package com.rs118.datavault;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A small themed dialog: title, message, optional extra views and a stack of pill buttons. */
final class Card {
    final Dialog dialog;
    final LinearLayout body;
    final TextView message;
    private final Activity act;
    private final FrameLayout root;

    Card(Activity a, String title, String msg) {
        act = a;
        dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        root = new FrameLayout(a);

        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20));
        body.setBackground(Ui.shape(a, 28, 0xFF4A4F57, 0, 0));
        body.setClickable(true); // swallow taps so they do not reach the scrim

        body.addView(Ui.text(a, title, 22, Ui.WHITE));
        message = Ui.text(a, msg == null ? "" : msg, 15, Ui.SUB);
        message.setLineSpacing(0, 1.15f);
        body.addView(message, Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, 10));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int m = Ui.dp(a, 28);
        lp.setMargins(m, m, m, m);
        root.addView(body, lp);
        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x88000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        cancelable(true);
    }

    Card cancelable(boolean c) {
        dialog.setCancelable(c);
        root.setOnClickListener(c ? new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        } : null);
        root.setClickable(c);
        return this;
    }

    Card add(View v, int topDp) {
        body.addView(v, Ui.lp(act, ViewGroup.LayoutParams.MATCH_PARENT, topDp));
        return this;
    }

    Card button(String label, Runnable action) {
        return button(label, action, false);
    }

    Card button(String label, final Runnable action, boolean danger) {
        TextView b = Ui.pill(act, label);
        if (danger) b.setTextColor(Ui.DANGER);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (action != null) action.run();
            }
        });
        body.addView(b, Ui.lp(act, ViewGroup.LayoutParams.MATCH_PARENT, 10));
        return this;
    }

    void show() {
        try {
            dialog.show();
        } catch (Throwable ignored) {
        }
    }

    void dismiss() {
        try {
            dialog.dismiss();
        } catch (Throwable ignored) {
        }
    }
}

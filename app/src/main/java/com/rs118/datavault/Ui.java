package com.rs118.datavault;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view factory. Everything is built in code because the module's resources are not visible inside other apps. */
final class Ui {
    private Ui() {}

    static final int PANEL_BG = 0xF0484D55;
    static final int STROKE = 0xFFC8CCD2;
    static final int WHITE = 0xFFFFFFFF;
    static final int SUB = 0xFFD3D6DB;
    static final int MUTED = 0xFFA9AEB6;
    static final int ACCENT = 0xFF74D7CC;
    static final int DANGER = 0xFFFF9A90;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, float radiusDp, int fill, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        return g;
    }

    static Drawable pressable(Context c, float radiusDp, int fill, int stroke) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, shape(c, radiusDp, 0x44FFFFFF, stroke, 1.2f));
        sl.addState(new int[]{}, shape(c, radiusDp, fill, stroke, 1.2f));
        return sl;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView pill(Context c, String label) {
        TextView t = text(c, label, 16, WHITE);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 46));
        t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        t.setBackground(pressable(c, 40, 0, STROKE));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Outlined rounded container, like the sections of the reference design. */
    static LinearLayout section(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 16));
        l.setBackground(shape(c, 24, 0, STROKE, 1.2f));
        return l;
    }

    static LinearLayout.LayoutParams lp(Context c, int width, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0x99D3D6DB);
        e.setTextColor(WHITE);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setBackground(shape(c, 40, 0x22000000, STROKE, 1.2f));
        e.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        return e;
    }

    /** Outlined card with a title, a description and an on/off switch on the right. */
    static View toggleRow(Context c, String title, String desc, boolean checked, final ToggleView.Listener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        row.setBackground(shape(c, 24, 0, STROKE, 1.2f));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, title, 19, WHITE));
        TextView d = text(c, desc, 13, SUB);
        d.setPadding(0, dp(c, 4), 0, 0);
        col.addView(d);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final ToggleView tv = new ToggleView(c, checked, l);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(c, 46), dp(c, 26));
        tp.leftMargin = dp(c, 12);
        row.addView(tv, tp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tv.toggle();
            }
        });
        return row;
    }
}

package com.rs118.datavault;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Minimal switch drawn by hand so it looks the same regardless of the host app's theme. */
final class ToggleView extends View {
    interface Listener {
        void onChanged(boolean checked);
    }

    private boolean checked;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    ToggleView(Context c, boolean checked, Listener l) {
        super(c);
        this.checked = checked;
        this.listener = l;
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });
    }

    void toggle() {
        checked = !checked;
        invalidate();
        if (listener != null) listener.onChanged(checked);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? 0xFF4FB3A8 : 0x55FFFFFF);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, h / 2, h / 2, paint);
        float r = h / 2 - Ui.dp(getContext(), 3);
        float cx = checked ? w - h / 2 : h / 2;
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, h / 2, r, paint);
    }
}

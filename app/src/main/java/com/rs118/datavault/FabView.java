package com.rs118.datavault;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** The round folder button that opens the panel. */
final class FabView extends android.view.View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path folder = new Path();

    FabView(Context c) {
        super(c);
        folder.moveTo(3f, 6f);
        folder.lineTo(9f, 6f);
        folder.lineTo(11f, 8.5f);
        folder.lineTo(21f, 8.5f);
        folder.lineTo(21f, 19f);
        folder.lineTo(3f, 19f);
        folder.close();
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float stroke = Ui.dp(getContext(), 1.5f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF0262A30);
        canvas.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - stroke, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(0xFF9AA0A8);
        canvas.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - stroke, paint);

        float s = Ui.dp(getContext(), 26) / 24f;
        canvas.save();
        canvas.translate((w - 24 * s) / 2f, (h - 24 * s) / 2f);
        canvas.scale(s, s);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Ui.WHITE);
        canvas.drawPath(folder, paint);
        canvas.restore();
    }
}

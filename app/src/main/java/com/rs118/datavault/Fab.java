package com.rs118.datavault;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Adds the draggable DataVault button to an activity's window. */
final class Fab {
    private Fab() {}

    private static final String TAG = "datavault_fab";
    private static float savedX, savedY; // remembered while dragging between activities

    static void attach(final Activity a) {
        if (a.isFinishing() || a.getWindow() == null) return;
        if (a.getClass().getName().startsWith("com.google.android.gms.")) return;

        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout host = (FrameLayout) decor;

        String m = MainModule.startupMessage;
        if (m != null) {
            MainModule.startupMessage = null;
            Toast.makeText(a, m, Toast.LENGTH_LONG).show();
        }

        if (host.findViewWithTag(TAG) != null) return;

        final FabView fab = new FabView(a);
        fab.setTag(TAG);
        fab.setAlpha(0.88f);
        fab.setElevation(Ui.dp(a, 6));
        int size = Ui.dp(a, 48);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size,
                android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.dp(a, 32);
        host.addView(fab, lp);
        fab.setTranslationX(savedX);
        fab.setTranslationY(savedY);

        final int slop = ViewConfiguration.get(a).getScaledTouchSlop();
        fab.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, startTx, startTy;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startTx = v.getTranslationX();
                        startTy = v.getTranslationY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                            ViewGroup p = (ViewGroup) v.getParent();
                            if (p != null) p.requestDisallowInterceptTouchEvent(true);
                        }
                        if (dragging) {
                            v.setTranslationX(startTx + dx);
                            v.setTranslationY(startTy + dy);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) {
                            savedX = v.getTranslationX();
                            savedY = v.getTranslationY();
                        } else {
                            Panel.show(a);
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }
}

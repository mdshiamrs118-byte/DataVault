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
    private static float savedX, savedY;

    static void attach(final Activity a) {
        if (a.isFinishing() || a.getWindow() == null) return;
        if (a.getClass().getName().startsWith("com.google.android.gms.")) return;

        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        final FrameLayout host = (FrameLayout) decor;

        String m = MainModule.startupMessage;
        if (m != null) {
            MainModule.startupMessage = null;
            Toast.makeText(a, m, Toast.LENGTH_LONG).show();
        }

        if (host.findViewWithTag(TAG) != null) {
            // Already attached for this activity — just re-clamp in case of rotation.
            clampIntoView((FabView) host.findViewWithTag(TAG), host);
            return;
        }

        final FabView fab = new FabView(a);
        fab.setTag(TAG);
        fab.setAlpha(0.88f);
        fab.setElevation(Ui.dp(a, 6));
        int size = Ui.dp(a, 48);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size,
                android.view.Gravity.TOP | android.view.Gravity.START);
        lp.leftMargin = Ui.dp(a, 16);
        lp.topMargin = Ui.dp(a, 32);
        host.addView(fab, lp);
        fab.setTranslationX(savedX);
        fab.setTranslationY(savedY);
        clampIntoView(fab, host);

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
                            clampIntoView((FabView) v, host);
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

        // Re-clamp whenever the window size changes (rotation, multi-window, fold).
        host.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) {
                    clampIntoView(fab, host);
                }
            }
        });
    }

    /** Keep the button fully inside the host view; pull it back if it drifted off-screen. */
    static void clampIntoView(FabView fab, FrameLayout host) {
        int hw = host.getWidth(), hh = host.getHeight();
        if (hw == 0 || hh == 0) return;
        float x = fab.getTranslationX();
        float y = fab.getTranslationY();
        float left = fab.getLeft() + x;
        float top  = fab.getTop()  + y;
        float right = left + fab.getWidth();
        float bottom = top + fab.getHeight();
        float dx = 0, dy = 0;
        if (left < 0) dx = -left;
        else if (right > hw) dx = hw - right;
        if (top < 0) dy = -top;
        else if (bottom > hh) dy = hh - bottom;
        if (dx != 0 || dy != 0) {
            fab.setTranslationX(x + dx);
            fab.setTranslationY(y + dy);
        }
    }
}
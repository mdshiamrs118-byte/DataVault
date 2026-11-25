package com.rs118.datavault;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Launcher screen of the module app: just tells you how to use it. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF1E2126);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        l.setPadding(p, Ui.dp(this, 48), p, p);
        sv.addView(l, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        l.addView(Ui.text(this, "DataVault", 32, Ui.WHITE));
        l.addView(Ui.text(this, "Developed by Rs118", 14, Ui.SUB), Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, 4));

        LinearLayout s = Ui.section(this);
        s.addView(Ui.text(this, "How to use", 20, Ui.WHITE));
        TextView t = Ui.text(this,
                "1. Open LSPosed, enable DataVault and tick the apps you want it in.\n\n"
                        + "2. Force-stop those apps and open them again.\n\n"
                        + "3. Tap the round folder button at the top of the app. Drag it if it is in the way.\n\n"
                        + "4. Log into account 1 and tap \"Create backup\". Log into account 2 and create another one. "
                        + "Tap any saved backup and choose \"Load & restart\" to switch.\n\n"
                        + "Backups live inside each app's own data folder, so clearing that app's data deletes them.",
                15, Ui.SUB);
        t.setLineSpacing(0, 1.15f);
        s.addView(t, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, 10));
        l.addView(s, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, 22));
        setContentView(sv);
    }
}

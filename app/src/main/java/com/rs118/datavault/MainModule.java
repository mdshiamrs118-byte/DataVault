package com.rs118.datavault;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.View;
import android.widget.FrameLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Entry point. Enable the module in LSPosed and tick the apps you want a DataVault button in. */
public class MainModule implements IXposedHookLoadPackage {

    /** Message left by a queued operation that ran at process start; shown once the UI is up. */
    static volatile String startupMessage;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp == null || lp.packageName == null || lp.appInfo == null) return;
        if (!lp.isFirstApplication) return;
        if (lp.packageName.equals("android") || lp.packageName.equals("com.rs118.datavault")) return;

        try {
            startupMessage = ProcessControl.vaultFor(lp.appInfo).runPendingIfAny();
        } catch (Throwable t) {
            XposedBridge.log("[DataVault] pending operation failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Fab.attach((Activity) param.thisObject);
                    } catch (Throwable t) {
                        XposedBridge.log("[DataVault] attach failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[DataVault] onResume hook failed: " + t);
        }

        // Re-clamp the FAB when the activity rotates / resizes.
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onConfigurationChanged",
                    Configuration.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity a = (Activity) param.thisObject;
                                if (a.getWindow() == null) return;
                                View decor = a.getWindow().getDecorView();
                                if (!(decor instanceof FrameLayout)) return;
                                View fab = decor.findViewWithTag("datavault_fab");
                                if (fab instanceof FabView) {
                                    Fab.clampIntoView((FabView) fab, (FrameLayout) decor);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[DataVault] onConfigurationChanged hook failed: " + t);
        }
    }
}
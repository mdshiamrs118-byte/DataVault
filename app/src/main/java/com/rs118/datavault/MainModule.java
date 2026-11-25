package com.rs118.datavault;

import android.app.Activity;

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

        // 1) Run a queued "load backup" / "clean backup" before the app touches its own files.
        try {
            startupMessage = ProcessControl.vaultFor(lp.appInfo).runPendingIfAny();
        } catch (Throwable t) {
            XposedBridge.log("[DataVault] pending operation failed: " + t);
        }

        // 2) Put the floating button into every activity of the app.
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
            XposedBridge.log("[DataVault] hook failed: " + t);
        }
    }
}

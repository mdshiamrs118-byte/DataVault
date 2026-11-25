package com.rs118.datavault;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Process;

import java.io.File;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;

/** Android-specific glue: locating the target app's folders and restarting it. */
final class ProcessControl {
    private ProcessControl() {}

    static Vault vaultFor(ApplicationInfo ai) {
        File data = new File(ai.dataDir);
        File de = ai.deviceProtectedDataDir != null
                ? new File(ai.deviceProtectedDataDir)
                : null;
        Context app = currentAppContext();
        File ext = app != null ? app.getExternalFilesDir(null) : null;
        return new Vault(data, de, ext);
    }

    /** The HOOKED app's Application context — never the module's own context. */
    static Context currentAppContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = XposedHelpers.callStaticMethod(
                    at,
                    "currentApplication"
            );
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {}
        return null;
    }

    static void restartApp(Context ctx) {
        String pkg = ctx.getPackageName();

        try {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pi = PendingIntent.getActivity(
                        ctx,
                        7421,
                        launch,
                        PendingIntent.FLAG_CANCEL_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );
                AlarmManager am =
                        (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                long at = System.currentTimeMillis() + 700;

                try {
                    am.setExact(AlarmManager.RTC, at, pi);
                } catch (SecurityException e) {
                    am.set(AlarmManager.RTC, at, pi);
                }
            }
        } catch (Throwable ignored) {}

        try {
            ActivityManager am =
                    (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> list =
                    am.getRunningAppProcesses();

            if (list != null) {
                for (ActivityManager.RunningAppProcessInfo info : list) {
                    if (info.pid != Process.myPid()
                            && pkg.equals(info.processName.split(":")[0])) {
                        Process.killProcess(info.pid);
                    }
                }
            }
        } catch (Throwable ignored) {}

        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}
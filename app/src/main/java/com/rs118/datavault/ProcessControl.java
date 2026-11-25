package com.rs118.datavault;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.os.Process;

import java.io.File;
import java.util.List;

/** Android-specific glue: locating the target app's folders and restarting it. */
final class ProcessControl {
    private ProcessControl() {}

    static Vault vaultFor(ApplicationInfo ai) {
        File data = new File(ai.dataDir);
        File de = ai.deviceProtectedDataDir != null ? new File(ai.deviceProtectedDataDir) : null;
        File ext = new File(Environment.getExternalStorageDirectory(), "Android/data/" + ai.packageName);
        return new Vault(data, de, ext);
    }

    /**
     * Stops every process of the app and asks the system to start it again shortly after.
     * Any queued DataVault operation then runs at the very start of the new process, before the
     * app opens its databases, so nothing can overwrite the restored files.
     */
    static void restartApp(Context ctx) {
        try {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pi = PendingIntent.getActivity(ctx, 7421, launch,
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                long at = System.currentTimeMillis() + 700;
                try {
                    am.setExact(AlarmManager.RTC, at, pi);
                } catch (SecurityException e) {
                    am.set(AlarmManager.RTC, at, pi);
                }
            }
        } catch (Throwable ignored) {
            // worst case the user opens the app manually; the queued operation still runs on that start
        }

        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
            if (list != null) {
                for (ActivityManager.RunningAppProcessInfo info : list) {
                    if (info.pid != Process.myPid()) Process.killProcess(info.pid);
                }
            }
        } catch (Throwable ignored) {
        }
        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}

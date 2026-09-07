package com.anurag.devbannerkiller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(DevBannerKillerService.PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoKill = prefs.getBoolean(DevBannerKillerService.KEY_AUTO_KILL, true);
            if (autoKill && DevBannerKillerService.hasWriteSecureSettings(context)) {
                boolean killed = DevBannerKillerService.killDevBannerDirect(context);
                Log.i(TAG, "Boot dev banner kill executed: " + killed);
            }
        }
    }
}

package com.anurag.devbannerkiller;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class DevBannerKillerService extends NotificationListenerService {

    private static final String TAG = "DevBannerKiller";
    public static final String PREFS_NAME = "DevBannerKillerPrefs";
    public static final String KEY_AUTO_KILL = "auto_kill_enabled";
    public static final String KEY_KILL_COUNT = "kill_count";

    private static DevBannerKillerService sInstance;

    public static DevBannerKillerService getInstance() {
        return sInstance;
    }

    public static boolean hasWriteSecureSettings(Context context) {
        if (context == null) return false;
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean restoreDevOptionsDirect(Context context) {
        if (context == null) return false;
        boolean success = false;
        try {
            Settings.Global.putInt(context.getContentResolver(), "development_settings_enabled", 1);
            Settings.Global.putInt(context.getContentResolver(), "vivo_development_show", 1);
            Settings.Global.putInt(context.getContentResolver(), "adb_enabled", 1);
            success = true;
            Log.i(TAG, "Dev options restored directly via Settings.Global (dev=1, vivo_show=1)");
        } catch (SecurityException se) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error writing Settings.Global: " + e.getMessage());
        }
        return success;
    }

    public static boolean killDevBannerDirect(Context context) {
        if (context == null) return false;
        boolean success = false;
        try {
            Settings.Global.putInt(context.getContentResolver(), "development_settings_enabled", 0);
            Settings.Global.putInt(context.getContentResolver(), "vivo_development_show", 1);
            Settings.Global.putInt(context.getContentResolver(), "adb_enabled", 1);
            success = true;
            Log.i(TAG, "Dev banner killed directly via Settings.Global (dev=0, vivo_show=1, adb=1)");
        } catch (SecurityException se) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error writing Settings.Global: " + e.getMessage());
        }
        return success;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "DevBannerKillerService created");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sInstance = this;
        Log.d(TAG, "NotificationListenerService connected");

        // Perform an immediate scan on connect
        dismissAllDevBanners();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        sInstance = null;
        Log.d(TAG, "NotificationListenerService disconnected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        Log.d(TAG, "DevBannerKillerService destroyed");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean autoKill = prefs.getBoolean(KEY_AUTO_KILL, true);

        if (!autoKill) {
            return;
        }

        if (isVivoDevModeNotification(sbn)) {
            Log.i(TAG, "Dev Mode banner notification detected! Key: " + sbn.getKey());
            try {
                cancelNotification(sbn.getKey());
            } catch (Exception ignored) {}

            int count = prefs.getInt(KEY_KILL_COUNT, 0) + 1;
            prefs.edit().putInt(KEY_KILL_COUNT, count).apply();
        }
    }

    public boolean isVivoDevModeNotification(StatusBarNotification sbn) {
        if (sbn == null) return false;

        String pkg = sbn.getPackageName();
        int id = sbn.getId();

        // Specific Vivo Daemon Service Dev Mode notification
        if ("com.vivo.daemonService".equals(pkg) && id == 10100) {
            return true;
        }

        // Android System Developer notification
        if ("android".equals(pkg) && (id == 26 || id == 2147483647)) {
            Notification n = sbn.getNotification();
            if (n != null && "DEVELOPER".equals(n.getChannelId())) {
                return true;
            }
        }

        Notification notification = sbn.getNotification();
        if (notification != null) {
            // Check Notification Channel
            String channelId = notification.getChannelId();
            if ("DEVELOPMENT_MODE".equals(channelId) || "DEVELOPER".equals(channelId)) {
                return true;
            }

            // Check Title / Ticker Text
            CharSequence title = notification.extras != null ? notification.extras.getCharSequence(Notification.EXTRA_TITLE) : null;
            if (title != null) {
                String titleStr = title.toString().toLowerCase();
                if (titleStr.contains("development mode") || titleStr.contains("dev mode")) {
                    return true;
                }
            }

            CharSequence ticker = notification.tickerText;
            if (ticker != null) {
                String tickerStr = ticker.toString().toLowerCase();
                if (tickerStr.contains("development mode") || tickerStr.contains("dev mode")) {
                    return true;
                }
            }
        }

        return false;
    }

    public int dismissAllDevBanners() {
        int killed = 0;
        try {
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            if (activeNotifications != null) {
                for (StatusBarNotification sbn : activeNotifications) {
                    if (isVivoDevModeNotification(sbn)) {
                        try {
                            cancelNotification(sbn.getKey());
                        } catch (Exception ignored) {}
                        killed++;
                        Log.i(TAG, "Manual scan caught dev notification: " + sbn.getKey());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in dismissAllDevBanners: " + e.getMessage());
        }

        return killed;
    }
}

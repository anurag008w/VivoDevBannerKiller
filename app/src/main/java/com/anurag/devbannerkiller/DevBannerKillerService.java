package com.anurag.devbannerkiller;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
    private ContentObserver mSettingsObserver;

    public static DevBannerKillerService getInstance() {
        return sInstance;
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

        // Register observer to keep vivo_development_show at 0
        registerSettingsObserver();

        // Perform an immediate scan and kill on connect
        dismissAllDevBanners();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        sInstance = null;
        unregisterSettingsObserver();
        Log.d(TAG, "NotificationListenerService disconnected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        unregisterSettingsObserver();
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
            Log.i(TAG, "Dev Mode banner notification detected! Cancelling immediately: " + sbn.getKey());
            try {
                cancelNotification(sbn.getKey());
            } catch (Exception e) {
                Log.e(TAG, "Failed to cancel notification: " + e.getMessage());
            }

            // Also reset settings toggle to 0
            resetVivoDevShow();

            // Increment kill count
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

        Notification notification = sbn.getNotification();
        if (notification != null) {
            // Check Notification Channel
            if ("DEVELOPMENT_MODE".equals(notification.getChannelId())) {
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
                        cancelNotification(sbn.getKey());
                        killed++;
                        Log.i(TAG, "Manual scan killed notification: " + sbn.getKey());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in dismissAllDevBanners: " + e.getMessage());
        }

        resetVivoDevShow();
        return killed;
    }

    private void resetVivoDevShow() {
        try {
            Settings.Global.putInt(getContentResolver(), "vivo_development_show", 0);
        } catch (Exception ignored) {
        }
    }

    private void registerSettingsObserver() {
        if (mSettingsObserver != null) return;
        try {
            mSettingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    if (prefs.getBoolean(KEY_AUTO_KILL, true)) {
                        resetVivoDevShow();
                        dismissAllDevBanners();
                    }
                }
            };
            getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor("vivo_development_show"),
                    false,
                    mSettingsObserver
            );
            getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor("development_settings_enabled"),
                    false,
                    mSettingsObserver
            );
        } catch (Exception e) {
            Log.w(TAG, "Could not register settings observer: " + e.getMessage());
        }
    }

    private void unregisterSettingsObserver() {
        if (mSettingsObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mSettingsObserver);
            } catch (Exception ignored) {
            }
            mSettingsObserver = null;
        }
    }
}

package com.anurag.devbannerkiller;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
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

    private ContentObserver mAccessibilityObserver;
    private BroadcastReceiver mScreenUnlockReceiver;

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
            Log.i(TAG, "Dev options preserved via Settings.Global (dev=1, vivo_show=1, adb=1)");
        } catch (SecurityException se) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error writing Settings.Global: " + e.getMessage());
        }
        return success;
    }

    public static boolean killDevBannerViaPmClear() {
        boolean success = false;
        try {
            // Direct shell execution
            Process process = Runtime.getRuntime().exec(new String[]{"pm", "clear", "com.vivo.daemonService"});
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                success = true;
                Log.i(TAG, "Successfully executed pm clear com.vivo.daemonService");
            }
        } catch (Exception ignored) {}

        if (!success) {
            try {
                // Fallback via su if device has root
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm clear com.vivo.daemonService"});
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    success = true;
                    Log.i(TAG, "Successfully executed su -c pm clear com.vivo.daemonService");
                }
            } catch (Exception ignored) {}
        }

        return success;
    }

    public static boolean killDevBannerDirect(Context context) {
        if (context == null) return false;

        // 1. Ensure Developer Options and USB Debugging stay 100% active
        restoreDevOptionsDirect(context);

        // 2. Clear daemonService user data to purge the red Dev Mode status bar notification
        boolean cleared = killDevBannerViaPmClear();

        // 3. Dismiss any active notification via NotificationListenerService if running
        DevBannerKillerService service = getInstance();
        if (service != null) {
            service.dismissAllDevBanners();
        }

        Log.i(TAG, "killDevBannerDirect executed (dev=1, vivo_show=1, pm_clear=" + cleared + ")");
        return cleared;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "DevBannerKillerService created");

        // 1. Register ContentObserver to watch Accessibility Settings (Infinity Gestures Watchdog)
        try {
            mAccessibilityObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    if (AppGuardianHelper.isInfinityGuardianEnabled(DevBannerKillerService.this)) {
                        AppGuardianHelper.ensureInfinityGesturesActive(DevBannerKillerService.this);
                    }
                }
            };
            getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    false,
                    mAccessibilityObserver
            );
            getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                    false,
                    mAccessibilityObserver
            );
            Log.i(TAG, "Infinity Gestures Guardian ContentObserver registered");
        } catch (Exception e) {
            Log.e(TAG, "Error registering accessibility observer: " + e.getMessage());
        }

        // 2. Register Dynamic Receiver for Screen On / Unlock to enforce guarded apps
        try {
            mScreenUnlockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getAction();
                    if (Intent.ACTION_USER_PRESENT.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
                        AppGuardianHelper.enforceAllGuardedApps(context);
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(mScreenUnlockReceiver, filter);
            Log.i(TAG, "App Guardian Screen Unlock Receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Error registering screen receiver: " + e.getMessage());
        }

        // 3. Initial enforcement
        AppGuardianHelper.enforceAllGuardedApps(this);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sInstance = this;
        Log.d(TAG, "NotificationListenerService connected");

        // Enforce guarded apps on listener connect
        AppGuardianHelper.enforceAllGuardedApps(this);

        // Perform an immediate scan on connect only if auto-kill is enabled
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean autoKill = prefs.getBoolean(KEY_AUTO_KILL, true);
        if (autoKill) {
            dismissAllDevBanners();
        }
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

        // Unregister observer
        if (mAccessibilityObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mAccessibilityObserver);
            } catch (Exception ignored) {}
            mAccessibilityObserver = null;
        }

        // Unregister receiver
        if (mScreenUnlockReceiver != null) {
            try {
                unregisterReceiver(mScreenUnlockReceiver);
            } catch (Exception ignored) {}
            mScreenUnlockReceiver = null;
        }

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

            // Execute pm clear to purge daemon notification cache
            killDevBannerViaPmClear();

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

        // Also purge via pm clear
        killDevBannerViaPmClear();

        return killed;
    }
}

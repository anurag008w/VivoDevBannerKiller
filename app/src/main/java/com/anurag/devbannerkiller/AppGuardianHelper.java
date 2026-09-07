package com.anurag.devbannerkiller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppGuardianHelper {

    private static final String TAG = "AppGuardianHelper";

    public static final String INFINITY_GESTURES_PACKAGE = "app.le.miui10gestures";
    public static final String INFINITY_GESTURES_SERVICE = "app.le.miui10gestures/.MiAccessibilityService";
    public static final String INFINITY_GESTURES_FULL_SERVICE = "app.le.miui10gestures/app.le.miui10gestures.MiAccessibilityService";

    public static final String PREFS_GUARDIAN = "AppGuardianPrefs";
    public static final String KEY_INFINITY_GUARDIAN_ENABLED = "infinity_guardian_enabled";
    public static final String KEY_GUARDED_PACKAGES = "guarded_packages_set";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    public static boolean isInfinityGesturesInstalled(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo(INFINITY_GESTURES_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isInfinityGesturesActive(Context context) {
        if (context == null) return false;
        try {
            int enabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled != 1) return false;

            String services = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services == null) return false;

            for (String s : services.split(":")) {
                String trimmed = s.trim();
                if (trimmed.equalsIgnoreCase(INFINITY_GESTURES_SERVICE) ||
                    trimmed.equalsIgnoreCase(INFINITY_GESTURES_FULL_SERVICE) ||
                    trimmed.contains(INFINITY_GESTURES_PACKAGE)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if Infinity Gestures is active: " + e.getMessage());
        }
        return false;
    }

    public static boolean isInfinityGuardianEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_GUARDIAN, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_INFINITY_GUARDIAN_ENABLED, true);
    }

    public static void setInfinityGuardianEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_GUARDIAN, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_INFINITY_GUARDIAN_ENABLED, enabled).apply();

        if (enabled) {
            ensureInfinityGesturesActive(context);
        }
    }

    public static boolean ensureInfinityGesturesActive(Context context) {
        if (context == null) return false;
        if (!isInfinityGesturesInstalled(context)) {
            Log.w(TAG, "Infinity Gestures is not installed on this device");
            return false;
        }

        boolean restored = false;
        try {
            String services = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean alreadyInList = false;

            if (services != null && !services.isEmpty()) {
                for (String s : services.split(":")) {
                    String trimmed = s.trim();
                    if (trimmed.equalsIgnoreCase(INFINITY_GESTURES_SERVICE) ||
                        trimmed.equalsIgnoreCase(INFINITY_GESTURES_FULL_SERVICE) ||
                        trimmed.contains(INFINITY_GESTURES_PACKAGE)) {
                        alreadyInList = true;
                        break;
                    }
                }
            }

            if (!alreadyInList) {
                String updatedServices = (services == null || services.trim().isEmpty())
                        ? INFINITY_GESTURES_SERVICE
                        : services + ":" + INFINITY_GESTURES_SERVICE;

                Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updatedServices);
                restored = true;
                Log.i(TAG, "Infinity Gestures accessibility service auto-injected successfully!");
            } else {
                int enabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
                if (enabled != 1) {
                    Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                    restored = true;
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Missing WRITE_SECURE_SETTINGS for accessibility: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring Infinity Gestures active: " + e.getMessage());
        }

        // Also ensure it is whitelisted from battery saver & standby
        makeAppUnkillableAsync(INFINITY_GESTURES_PACKAGE, true);

        return restored;
    }

    public static Set<String> getGuardedPackages(Context context) {
        if (context == null) return new HashSet<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_GUARDIAN, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_GUARDED_PACKAGES, new HashSet<>()));
    }

    public static boolean isAppGuarded(Context context, String packageName) {
        if (context == null || packageName == null) return false;
        return getGuardedPackages(context).contains(packageName);
    }

    public static void setAppUnkillable(Context context, String packageName, boolean unkillable) {
        if (context == null || packageName == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_GUARDIAN, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_GUARDED_PACKAGES, new HashSet<>()));

        if (unkillable) {
            set.add(packageName);
        } else {
            set.remove(packageName);
        }

        prefs.edit().putStringSet(KEY_GUARDED_PACKAGES, set).apply();

        // Apply device-level rules asynchronously
        makeAppUnkillableAsync(packageName, unkillable);
    }

    public static void makeAppUnkillableAsync(String packageName, boolean unkillable) {
        sExecutor.execute(() -> {
            try {
                if (unkillable) {
                    // 1. Android Doze Whitelist
                    executeCommand(new String[]{"dumpsys", "deviceidle", "whitelist", "+" + packageName});
                    // 2. High priority standby bucket (Bucket 10 / Active)
                    executeCommand(new String[]{"am", "set-standby-bucket", packageName, "active"});
                    // 3. Unlimited background execution
                    executeCommand(new String[]{"cmd", "appops", "set", packageName, "RUN_IN_BACKGROUND", "allow"});
                    executeCommand(new String[]{"cmd", "appops", "set", packageName, "RUN_ANY_IN_BACKGROUND", "allow"});
                    Log.i(TAG, "Made package unkillable: " + packageName);
                } else {
                    // 1. Remove from Doze Whitelist
                    executeCommand(new String[]{"dumpsys", "deviceidle", "whitelist", "-" + packageName});
                    // 2. Restore normal standby bucket
                    executeCommand(new String[]{"am", "set-standby-bucket", packageName, "working_set"});
                    // 3. Reset background appops
                    executeCommand(new String[]{"cmd", "appops", "set", packageName, "RUN_IN_BACKGROUND", "default"});
                    executeCommand(new String[]{"cmd", "appops", "set", packageName, "RUN_ANY_IN_BACKGROUND", "default"});
                    Log.i(TAG, "Removed unkillable protection from: " + packageName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error applying unkillable state for " + packageName + ": " + e.getMessage());
            }
        });
    }

    public static void enforceAllGuardedApps(Context context) {
        if (context == null) return;

        if (isInfinityGuardianEnabled(context)) {
            ensureInfinityGesturesActive(context);
        }

        Set<String> guarded = getGuardedPackages(context);
        for (String pkg : guarded) {
            makeAppUnkillableAsync(pkg, true);
        }
    }

    private static void executeCommand(String[] cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        } catch (Exception ignored) {}
    }

    public static void openBatteryOptimizationSettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    public static void openAppDetails(Context context, String packageName) {
        if (context == null || packageName == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }
}

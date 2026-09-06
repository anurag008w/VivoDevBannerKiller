package com.anurag.devbannerkiller;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheHelper {

    private static final String TAG = "CacheHelper";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CacheCallback {
        void onSuccess(String message, long bytesFreed);
        void onError(String error);
    }

    public static long getAvailableBytes() {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            return stat.getAvailableBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Native instant cache purge using PackageManager reflection
     */
    public static void clearAllViaReflection(Context context, CacheCallback callback) {
        executor.execute(() -> {
            long before = getAvailableBytes();
            boolean success = false;
            String errorMsg = "";

            try {
                PackageManager pm = context.getPackageManager();
                Method[] methods = pm.getClass().getDeclaredMethods();
                for (Method m : methods) {
                    if ("freeStorageAndNotify".equals(m.getName())) {
                        m.setAccessible(true);
                        m.invoke(pm, null, Long.MAX_VALUE, null);
                        success = true;
                        break;
                    }
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
                Log.e(TAG, "Native freeStorage failed: " + e.getMessage());
            }

            long after = getAvailableBytes();
            long freed = Math.max(0, after - before);

            final boolean finalSuccess = success;
            final String finalError = errorMsg;
            final String msg = freed > 0
                    ? "Purged " + formatSize(freed) + " of cache across all apps!"
                    : "Cache trimmed successfully.";

            mainHandler.post(() -> {
                if (finalSuccess) {
                    callback.onSuccess(msg, freed);
                } else {
                    callback.onError("Native trim failed: " + finalError);
                }
            });
        });
    }

    /**
     * Start the automated accessibility-driven cache cleaner (No Root / No Shizuku needed)
     */
    public static void startAutomatedClean(Context context, List<String> packageNames) {
        CacheCleanerAccessibilityService service = CacheCleanerAccessibilityService.getInstance();
        if (service != null) {
            service.startCleaning(packageNames);
        } else {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}

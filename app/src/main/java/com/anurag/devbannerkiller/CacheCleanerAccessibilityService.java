package com.anurag.devbannerkiller;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class CacheCleanerAccessibilityService extends AccessibilityService {

    private static final String TAG = "CacheCleanerAcc";
    private static CacheCleanerAccessibilityService sInstance;

    private enum Step {
        IDLE,
        LOOKING_FOR_STORAGE,
        LOOKING_FOR_CLEAR_CACHE,
        ADVANCING
    }

    private final Queue<String> mPackageQueue = new LinkedList<>();
    private boolean mIsCleaning = false;
    private Step mCurrentStep = Step.IDLE;
    private String mCurrentPackage = null;
    private String mCurrentAppName = null;
    private long mAppLaunchTime = 0;
    private int mTotalToClean = 0;
    private int mCleanedCount = 0;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private static final long APP_WATCHDOG_TIMEOUT = 5000; // 5s watchdog per app
    private static final long STORAGE_WAIT_TIMEOUT = 3500; // 3.5s waiting for storage page

    private final Runnable mWatchdogRunnable = () -> {
        if (mIsCleaning && mCurrentStep != Step.IDLE) {
            Log.w(TAG, "Watchdog timeout for " + mCurrentPackage + ", advancing to next app.");
            advanceToNextApp();
        }
    };

    // If cache button remains disabled for 900ms after opening Storage, cache is genuinely 0B
    private final Runnable mZeroCacheFallbackRunnable = () -> {
        if (mIsCleaning && mCurrentStep == Step.LOOKING_FOR_CLEAR_CACHE) {
            Log.d(TAG, "Cache button remained disabled (already 0 B) for " + mCurrentPackage + ", advancing.");
            advanceToNextApp();
        }
    };

    public static CacheCleanerAccessibilityService getInstance() {
        return sInstance;
    }

    public static boolean isRunning() {
        return sInstance != null;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d(TAG, "CacheCleanerAccessibilityService connected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        mIsCleaning = false;
        mCurrentStep = Step.IDLE;
        mHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "CacheCleanerAccessibilityService destroyed");
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "CacheCleanerAccessibilityService interrupted");
        mIsCleaning = false;
        mCurrentStep = Step.IDLE;
        mHandler.removeCallbacksAndMessages(null);
    }

    public synchronized void startCleaning(List<String> packages) {
        if (packages == null || packages.isEmpty()) return;

        mPackageQueue.clear();
        mPackageQueue.addAll(packages);
        mTotalToClean = packages.size();
        mCleanedCount = 0;
        mIsCleaning = true;

        Toast.makeText(this, "Starting automated cache cleaner for " + mTotalToClean + " apps...", Toast.LENGTH_SHORT).show();
        processNextApp();
    }

    private synchronized void processNextApp() {
        mHandler.removeCallbacks(mWatchdogRunnable);
        mHandler.removeCallbacks(mZeroCacheFallbackRunnable);

        if (!mIsCleaning) {
            mCurrentStep = Step.IDLE;
            return;
        }

        if (mPackageQueue.isEmpty()) {
            finishCleaning();
            return;
        }

        mCurrentPackage = mPackageQueue.poll();
        mCurrentStep = Step.LOOKING_FOR_STORAGE;
        mAppLaunchTime = System.currentTimeMillis();

        int currentIdx = mTotalToClean - mPackageQueue.size();
        Log.d(TAG, "[" + currentIdx + "/" + mTotalToClean + "] Opening settings for: " + mCurrentPackage);

        // Arm watchdog
        mHandler.postDelayed(mWatchdogRunnable, APP_WATCHDOG_TIMEOUT);

        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + mCurrentPackage));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch settings for " + mCurrentPackage + ": " + e.getMessage());
            advanceToNextApp();
        }
    }

    private synchronized void advanceToNextApp() {
        mCurrentStep = Step.ADVANCING;
        mHandler.removeCallbacks(mWatchdogRunnable);
        mHandler.removeCallbacks(mZeroCacheFallbackRunnable);

        // Directly launch next app without back key delay/race condition
        mHandler.postDelayed(this::processNextApp, 200);
    }

    private void finishCleaning() {
        mIsCleaning = false;
        mCurrentStep = Step.IDLE;
        mHandler.removeCallbacksAndMessages(null);

        Toast.makeText(this, "🎉 Completed! Cleaned cache for " + mCleanedCount + " apps.", Toast.LENGTH_LONG).show();

        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to return to MainActivity: " + e.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mIsCleaning || mCurrentStep == Step.IDLE || mCurrentStep == Step.ADVANCING) {
            return;
        }

        AccessibilityNodeInfo root = getSettingsRoot(event);
        if (root == null) return;

        // Step 1: In LOOKING_FOR_STORAGE, find and click "Storage"
        if (mCurrentStep == Step.LOOKING_FOR_STORAGE) {
            long elapsedSinceLaunch = System.currentTimeMillis() - mAppLaunchTime;
            if (elapsedSinceLaunch < 150) {
                // Allow brief transition window
                return;
            }

            AccessibilityNodeInfo storageNode = findStorageNode(root);
            if (storageNode != null) {
                Log.d(TAG, "Found Storage for: " + mCurrentPackage + ", clicking...");
                mCurrentStep = Step.LOOKING_FOR_CLEAR_CACHE;
                clickNode(storageNode);

                // Arm watchdog for storage page load
                mHandler.removeCallbacks(mWatchdogRunnable);
                mHandler.postDelayed(mWatchdogRunnable, STORAGE_WAIT_TIMEOUT);

                // Start 0B fallback timer (900ms) to allow cache calculation
                mHandler.removeCallbacks(mZeroCacheFallbackRunnable);
                mHandler.postDelayed(mZeroCacheFallbackRunnable, 900);
            }
            return;
        }

        // Step 2: In LOOKING_FOR_CLEAR_CACHE, wait for button to enable, then click
        if (mCurrentStep == Step.LOOKING_FOR_CLEAR_CACHE) {
            AccessibilityNodeInfo clearCacheNode = findClearCacheNode(root);
            if (clearCacheNode != null) {
                if (clearCacheNode.isEnabled()) {
                    // Cache calculation finished and button is active: clear it!
                    Log.d(TAG, "Clicking 'Clear Cache' for: " + mCurrentPackage);
                    mHandler.removeCallbacks(mZeroCacheFallbackRunnable);
                    clickNode(clearCacheNode);
                    mCleanedCount++;
                    advanceToNextApp();
                } else {
                    // Button is present but disabled.
                    // Keep waiting; if it enables once calculated, the event above triggers.
                    // If it stays disabled for 900ms, mZeroCacheFallbackRunnable advances.
                }
            }
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean rootContainsText(AccessibilityNodeInfo root, String target) {
        if (root == null || target == null) return false;
        String lowerTarget = target.toLowerCase().trim();

        CharSequence text = root.getText();
        if (text != null && text.toString().toLowerCase().contains(lowerTarget)) {
            return true;
        }

        int count = root.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null && rootContainsText(child, target)) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo getSettingsRoot(AccessibilityEvent event) {
        if (event != null) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                if (isSettingsPackage(source.getPackageName())) {
                    AccessibilityNodeInfo curr = source;
                    while (curr.getParent() != null) {
                        curr = curr.getParent();
                    }
                    return curr;
                }
            }
        }

        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null && isSettingsPackage(root.getPackageName())) {
                        return root;
                    }
                }
            }
        } catch (Exception ignored) {}

        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null && isSettingsPackage(activeRoot.getPackageName())) {
            return activeRoot;
        }

        return null;
    }

    private boolean isSettingsPackage(CharSequence pkg) {
        if (pkg == null) return false;
        return pkg.toString().toLowerCase().contains("settings");
    }

    private static final String[] CLEAR_CACHE_TEXTS = {
            "clear cache", "empty cache", "clean cache", "wipe cache",
            "कैश साफ़ करें", "कैश खाली करें", "कैश मिटाएं"
    };

    private static final String[] CLEAR_CACHE_IDS = {
            "clear_cache_button", "button_clear_cache", "clear_cache"
    };

    private static final String[] STORAGE_TEXTS = {
            "storage", "storage & cache", "space", "स्टोरेज", "मेमोरी"
    };

    private static final String[] STORAGE_IDS = {
            "storage_settings", "storage_size", "storage"
    };

    private AccessibilityNodeInfo findClearCacheNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        for (String idPart : CLEAR_CACHE_IDS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/" + idPart);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null) return n;
                }
            }
        }

        return findNodeByTextFuzzy(root, CLEAR_CACHE_TEXTS);
    }

    private AccessibilityNodeInfo findStorageNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        for (String idPart : STORAGE_IDS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/" + idPart);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null) return n;
                }
            }
        }

        return findNodeByTextFuzzy(root, STORAGE_TEXTS);
    }

    private AccessibilityNodeInfo findNodeByTextFuzzy(AccessibilityNodeInfo root, String[] targetTexts) {
        if (root == null) return null;

        CharSequence text = root.getText();
        CharSequence desc = root.getContentDescription();

        if (text != null) {
            String s = text.toString().toLowerCase().trim();
            for (String target : targetTexts) {
                if (s.contains(target)) {
                    return root;
                }
            }
        }

        if (desc != null) {
            String s = desc.toString().toLowerCase().trim();
            for (String target : targetTexts) {
                if (s.contains(target)) {
                    return root;
                }
            }
        }

        int count = root.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findNodeByTextFuzzy(child, targetTexts);
                if (res != null) return res;
            }
        }

        return null;
    }

    private void clickNode(AccessibilityNodeInfo node) {
        if (node == null) return;

        if (node.isClickable()) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return;
            }
        }

        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return;
                }
            }
            parent = parent.getParent();
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.width() > 0 && bounds.height() > 0) {
            clickAt(bounds.centerX(), bounds.centerY());
        }
    }

    private void clickAt(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            dispatchGesture(builder.build(), null, null);
        }
    }
}

package com.anurag.devbannerkiller;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class CacheCleanerAccessibilityService extends AccessibilityService {

    private static final String TAG = "CacheCleanerAcc";
    private static CacheCleanerAccessibilityService sInstance;

    private final Queue<String> mPackageQueue = new LinkedList<>();
    private boolean mIsCleaning = false;
    private int mTotalToClean = 0;
    private int mCleanedCount = 0;
    private boolean mHasClickedStorage = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

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
        Log.d(TAG, "CacheCleanerAccessibilityService destroyed");
    }

    @Override
    public void onInterrupt() {
        mIsCleaning = false;
    }

    public void startCleaning(List<String> packages) {
        if (packages == null || packages.isEmpty()) return;

        mPackageQueue.clear();
        mPackageQueue.addAll(packages);
        mTotalToClean = packages.size();
        mCleanedCount = 0;
        mIsCleaning = true;
        mHasClickedStorage = false;

        Toast.makeText(this, "Starting automated cache cleaner for " + mTotalToClean + " apps...", Toast.LENGTH_SHORT).show();
        processNextApp();
    }

    private void processNextApp() {
        if (!mIsCleaning) return;

        if (mPackageQueue.isEmpty()) {
            mIsCleaning = false;
            Toast.makeText(this, "🎉 Completed! Cache cleared for " + mCleanedCount + " apps.", Toast.LENGTH_LONG).show();

            // Return to our app
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            return;
        }

        String nextPkg = mPackageQueue.poll();
        mHasClickedStorage = false;

        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + nextPkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open settings for " + nextPkg + ": " + e.getMessage());
            processNextApp();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mIsCleaning) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // Step 1: Click "Storage" if we are in App Info page
        if (!mHasClickedStorage) {
            AccessibilityNodeInfo storageNode = findNodeByTextOrId(rootNode,
                    new String[]{"storage", "स्टोरेज", "space"},
                    new String[]{"storage_settings", "storage_size"});

            if (storageNode != null) {
                mHasClickedStorage = true;
                performClick(storageNode);
                return;
            }
        }

        // Step 2: Click "Clear Cache" once inside Storage page
        AccessibilityNodeInfo clearCacheNode = findNodeByTextOrId(rootNode,
                new String[]{"clear cache", "कैश साफ़ करें", "empty cache", "clean cache"},
                new String[]{"clear_cache_button", "button_clear_cache", "clear_cache"});

        if (clearCacheNode != null && clearCacheNode.isEnabled()) {
            performClick(clearCacheNode);
            mCleanedCount++;

            // Wait 250ms, then press BACK twice to go to next app
            mHandler.postDelayed(() -> {
                performGlobalAction(GLOBAL_ACTION_BACK);
                mHandler.postDelayed(() -> {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    mHandler.postDelayed(this::processNextApp, 200);
                }, 200);
            }, 250);
        }
    }

    private AccessibilityNodeInfo findNodeByTextOrId(AccessibilityNodeInfo root, String[] texts, String[] viewIds) {
        if (root == null) return null;

        // Check view IDs first
        for (String idPart : viewIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/" + idPart);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.isEnabled()) return n;
                }
            }
        }

        // Check text content
        for (String text : texts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.isEnabled()) {
                        // Find clickable parent if node itself is not clickable
                        AccessibilityNodeInfo clickable = n;
                        while (clickable != null && !clickable.isClickable()) {
                            clickable = clickable.getParent();
                        }
                        return clickable != null ? clickable : n;
                    }
                }
            }
        }

        // Recursive search for nested text matches
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                CharSequence nodeText = child.getText();
                if (nodeText != null) {
                    String str = nodeText.toString().toLowerCase();
                    for (String t : texts) {
                        if (str.contains(t) && child.isEnabled()) {
                            AccessibilityNodeInfo clickable = child;
                            while (clickable != null && !clickable.isClickable()) {
                                clickable = clickable.getParent();
                            }
                            return clickable != null ? clickable : child;
                        }
                    }
                }
                AccessibilityNodeInfo res = findNodeByTextOrId(child, texts, viewIds);
                if (res != null) return res;
            }
        }

        return null;
    }

    private void performClick(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.isClickable()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            AccessibilityNodeInfo parent = node.getParent();
            while (parent != null) {
                if (parent.isClickable()) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
                parent = parent.getParent();
            }
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }
}

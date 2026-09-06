package com.anurag.devbannerkiller;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private final String appName;
    private final String packageName;
    private final Drawable icon;
    private final boolean isSystemApp;
    private boolean isSelected;

    public AppInfo(String appName, String packageName, Drawable icon, boolean isSystemApp) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
        this.isSelected = false;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isSystemApp() {
        return isSystemApp;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}

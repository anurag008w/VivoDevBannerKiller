package com.anurag.devbannerkiller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity implements AppListAdapter.OnSelectionChangedListener {

    private static final int SHIZUKU_REQ_CODE = 1001;

    // Tabs
    private Button tabBanner;
    private Button tabCache;
    private View layoutBannerSection;
    private View layoutCacheSection;

    // Banner Section Views
    private SwitchCompat switchAutoKill;
    private TextView tvStatus;
    private TextView tvKillCount;
    private CardView cardPermission;
    private Button btnGrantPermission;
    private Button btnManualKill;
    private Button btnKillProcess;

    // Cache Section Views
    private TextView tvShizukuStatus;
    private Button btnClearAllCache;
    private Button btnSelectAll;
    private Button btnDeselectAll;
    private EditText etSearchApp;
    private ProgressBar progressLoadingApps;
    private RecyclerView rvAppsList;
    private Button btnClearSelectedCache;

    private AppListAdapter mAdapter;
    private SharedPreferences mPrefs;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final Shizuku.OnRequestPermissionResultListener mShizukuPermissionListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_REQ_CODE) {
            updateShizukuUI();
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku Permission Granted!", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(DevBannerKillerService.PREFS_NAME, Context.MODE_PRIVATE);

        initViews();
        setupTabs();
        setupBannerSection();
        setupCacheSection();

        // Shizuku permission listener
        try {
            Shizuku.addRequestPermissionResultListener(mShizukuPermissionListener);
        } catch (Throwable ignored) {}
    }

    private void initViews() {
        tabBanner = findViewById(R.id.tabBanner);
        tabCache = findViewById(R.id.tabCache);
        layoutBannerSection = findViewById(R.id.layoutBannerSection);
        layoutCacheSection = findViewById(R.id.layoutCacheSection);

        // Banner
        switchAutoKill = findViewById(R.id.switchAutoKill);
        tvStatus = findViewById(R.id.tvStatus);
        tvKillCount = findViewById(R.id.tvKillCount);
        cardPermission = findViewById(R.id.cardPermission);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
        btnManualKill = findViewById(R.id.btnManualKill);
        btnKillProcess = findViewById(R.id.btnKillProcess);

        // Cache
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        btnClearAllCache = findViewById(R.id.btnClearAllCache);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnDeselectAll = findViewById(R.id.btnDeselectAll);
        etSearchApp = findViewById(R.id.etSearchApp);
        progressLoadingApps = findViewById(R.id.progressLoadingApps);
        rvAppsList = findViewById(R.id.rvAppsList);
        btnClearSelectedCache = findViewById(R.id.btnClearSelectedCache);
    }

    private void setupTabs() {
        tabBanner.setOnClickListener(v -> {
            tabBanner.setBackgroundTintList(getColorStateList(R.color.colorPrimary));
            tabBanner.setTextColor(getResources().getColor(android.R.color.white));

            tabCache.setBackgroundTintList(getColorStateList(android.R.color.transparent));
            tabCache.setTextColor(getResources().getColor(R.color.colorAccent));

            layoutBannerSection.setVisibility(View.VISIBLE);
            layoutCacheSection.setVisibility(View.GONE);
        });

        tabCache.setOnClickListener(v -> {
            tabCache.setBackgroundTintList(getColorStateList(R.color.colorPrimary));
            tabCache.setTextColor(getResources().getColor(android.R.color.white));

            tabBanner.setBackgroundTintList(getColorStateList(android.R.color.transparent));
            tabBanner.setTextColor(getResources().getColor(R.color.colorAccent));

            layoutBannerSection.setVisibility(View.GONE);
            layoutCacheSection.setVisibility(View.VISIBLE);

            // Check Shizuku status & load apps if needed
            updateShizukuUI();
            if (mAdapter == null || mAdapter.getItemCount() == 0) {
                loadInstalledApps();
            }
        });
    }

    private void setupBannerSection() {
        boolean isAutoKill = mPrefs.getBoolean(DevBannerKillerService.KEY_AUTO_KILL, true);
        switchAutoKill.setChecked(isAutoKill);

        switchAutoKill.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean(DevBannerKillerService.KEY_AUTO_KILL, isChecked).apply();
            Toast.makeText(this, isChecked ? "Auto-Kill Activated" : "Auto-Kill Paused", Toast.LENGTH_SHORT).show();
            updateStatusUI();
        });

        btnManualKill.setOnClickListener(v -> {
            int count = 0;
            DevBannerKillerService service = DevBannerKillerService.getInstance();
            if (service != null) {
                count = service.dismissAllDevBanners();
            } else {
                try {
                    Settings.Global.putInt(getContentResolver(), "vivo_development_show", 0);
                } catch (Exception ignored) {}
            }

            int totalKills = mPrefs.getInt(DevBannerKillerService.KEY_KILL_COUNT, 0) + (count > 0 ? count : 1);
            mPrefs.edit().putInt(DevBannerKillerService.KEY_KILL_COUNT, totalKills).apply();
            tvKillCount.setText("Banners Blocked: " + totalKills);

            Toast.makeText(this, "Dev Banner Kill Triggered!", Toast.LENGTH_SHORT).show();
        });

        btnGrantPermission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        btnKillProcess.setOnClickListener(v -> {
            Toast.makeText(this, "Process Stopped (0 MB RAM)", Toast.LENGTH_SHORT).show();
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        });
    }

    private void setupCacheSection() {
        rvAppsList.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new AppListAdapter(this);
        rvAppsList.setAdapter(mAdapter);

        // Search text watcher
        etSearchApp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) {
                    mAdapter.filter(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSelectAll.setOnClickListener(v -> {
            if (mAdapter != null) mAdapter.selectAll(true);
        });

        btnDeselectAll.setOnClickListener(v -> {
            if (mAdapter != null) mAdapter.selectAll(false);
        });

        // Clear All Apps Cache (1-Click)
        btnClearAllCache.setOnClickListener(v -> {
            checkShizukuAndRun(() -> {
                btnClearAllCache.setEnabled(false);
                btnClearAllCache.setText("Clearing All Caches...");
                CacheHelper.clearAllAppsCache(this, new CacheHelper.CacheCallback() {
                    @Override
                    public void onSuccess(String message, long bytesFreed) {
                        btnClearAllCache.setEnabled(true);
                        btnClearAllCache.setText("⚡ Clear ALL Apps Cache (1-Click)");
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String error) {
                        btnClearAllCache.setEnabled(true);
                        btnClearAllCache.setText("⚡ Clear ALL Apps Cache (1-Click)");
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        // Clear Selected Apps Cache
        btnClearSelectedCache.setOnClickListener(v -> {
            List<String> selected = mAdapter != null ? mAdapter.getSelectedPackages() : Collections.emptyList();
            if (selected.isEmpty()) {
                Toast.makeText(this, "Please select at least 1 app from the list below", Toast.LENGTH_SHORT).show();
                return;
            }

            checkShizukuAndRun(() -> {
                btnClearSelectedCache.setEnabled(false);
                btnClearSelectedCache.setText("Clearing Selected...");
                CacheHelper.clearSelectedAppsCache(this, selected, new CacheHelper.CacheCallback() {
                    @Override
                    public void onSuccess(String message, long bytesFreed) {
                        btnClearSelectedCache.setEnabled(true);
                        updateSelectedCount(selected.size());
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String error) {
                        btnClearSelectedCache.setEnabled(true);
                        updateSelectedCount(selected.size());
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    private void checkShizukuAndRun(Runnable action) {
        if (!CacheHelper.isShizukuRunning()) {
            Toast.makeText(this, "Shizuku is not running on device! Please start Shizuku.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!CacheHelper.isShizukuReady()) {
            try {
                Shizuku.requestPermission(SHIZUKU_REQ_CODE);
            } catch (Exception e) {
                Toast.makeText(this, "Failed to request Shizuku permission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        action.run();
    }

    private void updateShizukuUI() {
        if (CacheHelper.isShizukuReady()) {
            tvShizukuStatus.setText("Shizuku: Active ✅");
            tvShizukuStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
        } else if (CacheHelper.isShizukuRunning()) {
            tvShizukuStatus.setText("Shizuku: Tap to Grant ⚠️");
            tvShizukuStatus.setTextColor(getResources().getColor(R.color.colorWarning));
            tvShizukuStatus.setOnClickListener(v -> {
                try {
                    Shizuku.requestPermission(SHIZUKU_REQ_CODE);
                } catch (Exception ignored) {}
            });
        } else {
            tvShizukuStatus.setText("Shizuku: Stopped ❌");
            tvShizukuStatus.setTextColor(getResources().getColor(R.color.colorError));
        }
    }

    private void loadInstalledApps() {
        progressLoadingApps.setVisibility(View.VISIBLE);
        rvAppsList.setVisibility(View.GONE);

        mExecutor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppInfo> appList = new ArrayList<>();

            for (ApplicationInfo info : apps) {
                // Filter out essential system packages, show user installed & major apps
                boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                String name = info.loadLabel(pm).toString();
                String pkg = info.packageName;
                Drawable icon = info.loadIcon(pm);

                // Ignore this app itself
                if (pkg.equals(getPackageName())) continue;

                // Include user apps or apps with launch intents
                if (!isSystem || pm.getLaunchIntentForPackage(pkg) != null) {
                    appList.add(new AppInfo(name, pkg, icon, isSystem));
                }
            }

            // Sort alphabetically by app name
            Collections.sort(appList, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

            runOnUiThread(() -> {
                progressLoadingApps.setVisibility(View.GONE);
                rvAppsList.setVisibility(View.VISIBLE);
                if (mAdapter != null) {
                    mAdapter.setApps(appList);
                }
            });
        });
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        updateSelectedCount(selectedCount);
    }

    private void updateSelectedCount(int count) {
        btnClearSelectedCache.setText("🗑️ Clear Selected Cache (" + count + ")");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusUI();
        updateShizukuUI();
    }

    private void updateStatusUI() {
        boolean hasPermission = isNotificationServiceEnabled();
        int totalKills = mPrefs.getInt(DevBannerKillerService.KEY_KILL_COUNT, 0);
        tvKillCount.setText("Banners Blocked: " + totalKills);

        if (!hasPermission) {
            cardPermission.setVisibility(View.VISIBLE);
            tvStatus.setText("Status: Missing Notification Permission");
            tvStatus.setTextColor(getResources().getColor(R.color.colorError));
        } else {
            cardPermission.setVisibility(View.GONE);
            boolean isAuto = mPrefs.getBoolean(DevBannerKillerService.KEY_AUTO_KILL, true);
            if (isAuto) {
                tvStatus.setText("Status: Protected & Auto-Kill Running");
                tvStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
            } else {
                tvStatus.setText("Status: Auto-Kill Paused (Idle)");
                tvStatus.setTextColor(getResources().getColor(R.color.colorWarning));
            }
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeRequestPermissionResultListener(mShizukuPermissionListener);
        } catch (Throwable ignored) {}
    }
}

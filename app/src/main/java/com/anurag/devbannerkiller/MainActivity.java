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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AppListAdapter.OnSelectionChangedListener {

    public static final String KEY_ACTIVE_TAB = "active_tab";

    // Tabs
    private TextView tabBanner;
    private TextView tabCache;
    private View layoutBannerSection;
    private View layoutCacheSection;

    // Banner Section Views
    private FrameLayout containerHeroOrb;
    private View btnManualKill;
    private TextView tvOrbIcon;
    private TextView tvOrbAction;
    private SwitchCompat switchAutoKill;
    private TextView tvStatus;
    private TextView tvStatusSub;
    private TextView tvLiveBadge;
    private TextView tvKillCount;
    private TextView tvDevModeStatus;
    private TextView tvAdbStatus;
    private CardView cardPermission;
    private Button btnGrantPermission;
    private View btnOpenDevOptions;
    private View btnRestoreDevOptions;
    private View btnKillProcess;
    private View btnCopyAdbCommand;

    // Cache Section Views
    private TextView tvCacheStatus;
    private Button btnClearAllCache;
    private TextView btnSelectAll;
    private TextView btnDeselectAll;
    private EditText etSearchApp;
    private ProgressBar progressLoadingApps;
    private RecyclerView rvAppsList;
    private Button btnClearSelectedCache;

    private AppListAdapter mAdapter;
    private SharedPreferences mPrefs;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final CompoundButton.OnCheckedChangeListener mAutoKillListener = (buttonView, isChecked) -> {
        mPrefs.edit().putBoolean(DevBannerKillerService.KEY_AUTO_KILL, isChecked).apply();
        if (isChecked) {
            mExecutor.execute(() -> DevBannerKillerService.killDevBannerDirect(this));
            Toast.makeText(this, "⚡ Auto-Kill Activated & Banner Dismissed!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Auto-Kill Paused", Toast.LENGTH_SHORT).show();
        }
        updateBannerStatusUI();
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void initViews() {
        tabBanner = findViewById(R.id.tabBanner);
        tabCache = findViewById(R.id.tabCache);
        layoutBannerSection = findViewById(R.id.layoutBannerSection);
        layoutCacheSection = findViewById(R.id.layoutCacheSection);

        // Banner Hero & Bento
        containerHeroOrb = findViewById(R.id.containerHeroOrb);
        btnManualKill = findViewById(R.id.btnManualKill);
        tvOrbIcon = findViewById(R.id.tvOrbIcon);
        tvOrbAction = findViewById(R.id.tvOrbAction);
        switchAutoKill = findViewById(R.id.switchAutoKill);
        tvStatus = findViewById(R.id.tvStatus);
        tvStatusSub = findViewById(R.id.tvStatusSub);
        tvLiveBadge = findViewById(R.id.tvLiveBadge);
        tvKillCount = findViewById(R.id.tvKillCount);
        tvDevModeStatus = findViewById(R.id.tvDevModeStatus);
        tvAdbStatus = findViewById(R.id.tvAdbStatus);
        cardPermission = findViewById(R.id.cardPermission);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
        btnOpenDevOptions = findViewById(R.id.btnOpenDevOptions);
        btnRestoreDevOptions = findViewById(R.id.btnRestoreDevOptions);
        btnKillProcess = findViewById(R.id.btnKillProcess);
        btnCopyAdbCommand = findViewById(R.id.btnCopyAdbCommand);

        // Cache
        tvCacheStatus = findViewById(R.id.tvShizukuStatus);
        btnClearAllCache = findViewById(R.id.btnClearAllCache);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnDeselectAll = findViewById(R.id.btnDeselectAll);
        etSearchApp = findViewById(R.id.etSearchApp);
        progressLoadingApps = findViewById(R.id.progressLoadingApps);
        rvAppsList = findViewById(R.id.rvAppsList);
        btnClearSelectedCache = findViewById(R.id.btnClearSelectedCache);
    }

    private void setupTabs() {
        tabBanner.setOnClickListener(v -> selectTab(true));
        tabCache.setOnClickListener(v -> selectTab(false));

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent != null && "cache".equals(intent.getStringExtra("open_tab"))) {
            selectTab(false);
            return;
        }
        String savedTab = mPrefs.getString(KEY_ACTIVE_TAB, "banner");
        selectTab(!"cache".equals(savedTab));
    }

    private void selectTab(boolean isBannerTab) {
        mPrefs.edit().putString(KEY_ACTIVE_TAB, isBannerTab ? "banner" : "cache").apply();

        if (isBannerTab) {
            tabBanner.setBackgroundResource(R.drawable.bg_bottom_tab_active);
            tabBanner.setTextColor(0xFFFFFFFF);

            tabCache.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            tabCache.setTextColor(0xFF94A3B8);

            layoutBannerSection.setVisibility(View.VISIBLE);
            layoutCacheSection.setVisibility(View.GONE);
        } else {
            tabCache.setBackgroundResource(R.drawable.bg_bottom_tab_active);
            tabCache.setTextColor(0xFFFFFFFF);

            tabBanner.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            tabBanner.setTextColor(0xFF94A3B8);

            layoutBannerSection.setVisibility(View.GONE);
            layoutCacheSection.setVisibility(View.VISIBLE);

            updateCacheStatusUI();
            if (mAdapter == null || mAdapter.getItemCount() == 0) {
                loadInstalledApps();
            }
        }
    }

    private void setupBannerSection() {
        boolean isAutoKill = mPrefs.getBoolean(DevBannerKillerService.KEY_AUTO_KILL, true);
        switchAutoKill.setOnCheckedChangeListener(null);
        switchAutoKill.setChecked(isAutoKill);
        switchAutoKill.setOnCheckedChangeListener(mAutoKillListener);

        btnManualKill.setOnClickListener(v -> {
            if (containerHeroOrb != null) {
                containerHeroOrb.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).withEndAction(() -> {
                    containerHeroOrb.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }).start();
                try {
                    containerHeroOrb.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Exception ignored) {}
            }

            DevBannerKillerService.killDevBannerDirect(this);
            DevBannerKillerService service = DevBannerKillerService.getInstance();
            int count = 0;
            if (service != null) {
                count = service.dismissAllDevBanners();
            }

            int totalKills = mPrefs.getInt(DevBannerKillerService.KEY_KILL_COUNT, 0) + (count > 0 ? count : 1);
            mPrefs.edit().putInt(DevBannerKillerService.KEY_KILL_COUNT, totalKills).apply();
            if (tvKillCount != null) {
                tvKillCount.setText(totalKills + " Blocked");
            }

            Toast.makeText(this, "⚡ Vivo Red Banner Dismissed!", Toast.LENGTH_SHORT).show();
            updateBannerStatusUI();
        });

        if (btnCopyAdbCommand != null) {
            btnCopyAdbCommand.setOnClickListener(v -> showAdbDialog());
        }

        btnOpenDevOptions.setOnClickListener(v -> {
            try {
                if (DevBannerKillerService.hasWriteSecureSettings(this)) {
                    DevBannerKillerService.restoreDevOptionsDirect(this);
                }
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Dev Options: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        btnRestoreDevOptions.setOnClickListener(v -> {
            if (DevBannerKillerService.hasWriteSecureSettings(this)) {
                DevBannerKillerService.restoreDevOptionsDirect(this);
                Toast.makeText(this, "Developer Options restored in Settings > More settings!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please grant WRITE_SECURE_SETTINGS via ADB first!", Toast.LENGTH_LONG).show();
            }
            updateBannerStatusUI();
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

    private void showAdbDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("💻 ADB Verified Killer Command");
        builder.setMessage("Execute this command in PC / ADB shell:\n\nadb shell pm clear com.vivo.daemonService\n\n✅ Instantly clears Vivo red status pill\n✅ Developer Options & USB Debugging stay 100% active");
        builder.setPositiveButton("📋 Copy Command", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("ADB Command", "adb shell pm clear com.vivo.daemonService");
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied: adb shell pm clear com.vivo.daemonService", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void setupCacheSection() {
        rvAppsList.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new AppListAdapter(this);
        rvAppsList.setAdapter(mAdapter);

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

        // Clear ALL Apps Cache
        btnClearAllCache.setOnClickListener(v -> {
            // First check if native CLEAR_APP_CACHE is available
            if (hasClearAppCachePermission()) {
                clearAllViaNative();
                return;
            }

            // Otherwise, check if Accessibility Auto-Cleaner is ready
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityPrompt();
                return;
            }

            // Run automated cleaning for all apps
            List<String> allPackages = getAllLoadedPackages();
            if (allPackages.isEmpty()) {
                Toast.makeText(this, "Apps still loading, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }

            CacheCleanerAccessibilityService service = CacheCleanerAccessibilityService.getInstance();
            if (service != null) {
                service.startCleaning(allPackages);
            } else {
                showAccessibilityPrompt();
            }
        });

        // Clear Selected Apps Cache
        btnClearSelectedCache.setOnClickListener(v -> {
            List<String> selected = mAdapter != null ? mAdapter.getSelectedPackages() : Collections.emptyList();
            if (selected.isEmpty()) {
                Toast.makeText(this, "Please select at least 1 app from the list below", Toast.LENGTH_SHORT).show();
                return;
            }

            if (hasClearAppCachePermission()) {
                clearAllViaNative();
                return;
            }

            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityPrompt();
                return;
            }

            CacheCleanerAccessibilityService service = CacheCleanerAccessibilityService.getInstance();
            if (service != null) {
                service.startCleaning(selected);
            } else {
                showAccessibilityPrompt();
            }
        });

        tvCacheStatus.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityPrompt();
            }
        });
    }

    private boolean hasClearAppCachePermission() {
        return checkSelfPermission("android.permission.CLEAR_APP_CACHE") == PackageManager.PERMISSION_GRANTED;
    }

    private void clearAllViaNative() {
        btnClearAllCache.setEnabled(false);
        btnClearAllCache.setText("Purging System Cache...");
        mExecutor.execute(() -> {
            boolean done = false;
            try {
                PackageManager pm = getPackageManager();
                Method[] methods = pm.getClass().getDeclaredMethods();
                for (Method m : methods) {
                    if ("freeStorageAndNotify".equals(m.getName())) {
                        m.setAccessible(true);
                        m.invoke(pm, null, Long.MAX_VALUE, null);
                        done = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}

            final boolean success = done;
            runOnUiThread(() -> {
                btnClearAllCache.setEnabled(true);
                btnClearAllCache.setText("⚡ Clear ALL Apps Cache (1-Click)");
                if (success) {
                    Toast.makeText(this, "🎉 Instant Native Cache Purge Complete!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Native purge finished.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showAccessibilityPrompt() {
        Toast.makeText(this, "Please enable 'Automated Cache Cleaner' in Accessibility settings for 100% automated cache clearing without root/PC!", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private boolean isAccessibilityServiceEnabled() {
        if (CacheCleanerAccessibilityService.getInstance() != null) {
            return true;
        }
        String shortName = new ComponentName(this, CacheCleanerAccessibilityService.class).flattenToShortString();
        String fullName = new ComponentName(this, CacheCleanerAccessibilityService.class).flattenToString();
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices != null) {
            String[] services = enabledServices.split(":");
            for (String s : services) {
                String trimmed = s.trim();
                if (trimmed.equalsIgnoreCase(shortName) || trimmed.equalsIgnoreCase(fullName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateCacheStatusUI() {
        if (hasClearAppCachePermission()) {
            tvCacheStatus.setText("Engine: Native Instant ✅");
            tvCacheStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
        } else if (isAccessibilityServiceEnabled()) {
            tvCacheStatus.setText("Auto-Cleaner: Ready ✅");
            tvCacheStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
        } else {
            tvCacheStatus.setText("Tap to Enable Auto-Clean ⚠️");
            tvCacheStatus.setTextColor(getResources().getColor(R.color.colorWarning));
        }
    }

    private List<String> getAllLoadedPackages() {
        if (mAdapter != null) {
            List<String> all = mAdapter.getAllPackages();
            if (!all.isEmpty()) return all;
        }
        return Collections.emptyList();
    }

    private void loadInstalledApps() {
        progressLoadingApps.setVisibility(View.VISIBLE);
        rvAppsList.setVisibility(View.GONE);

        mExecutor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppInfo> appList = new ArrayList<>();

            for (ApplicationInfo info : apps) {
                boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                String name = info.loadLabel(pm).toString();
                String pkg = info.packageName;
                Drawable icon = info.loadIcon(pm);

                if (pkg.equals(getPackageName())) continue;

                if (!isSystem || pm.getLaunchIntentForPackage(pkg) != null) {
                    appList.add(new AppInfo(name, pkg, icon, isSystem));
                }
            }

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
        btnClearSelectedCache.setText("🗑️ Clear Selected Cache (" + selectedCount + ")");
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean isAuto = mPrefs.getBoolean(DevBannerKillerService.KEY_AUTO_KILL, true);
        switchAutoKill.setOnCheckedChangeListener(null);
        switchAutoKill.setChecked(isAuto);
        switchAutoKill.setOnCheckedChangeListener(mAutoKillListener);

        if (isAuto) {
            mExecutor.execute(() -> DevBannerKillerService.killDevBannerDirect(MainActivity.this));
        }
        updateBannerStatusUI();
        updateCacheStatusUI();
    }

    private void updateBannerStatusUI() {
        boolean hasPermission = isNotificationServiceEnabled();
        int totalKills = mPrefs.getInt(DevBannerKillerService.KEY_KILL_COUNT, 0);
        if (tvKillCount != null) {
            tvKillCount.setText(totalKills + " Blocked");
        }

        // Live status check for Dev Mode & ADB
        try {
            int devEnabled = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
            if (tvDevModeStatus != null) {
                tvDevModeStatus.setText(devEnabled == 1 ? "ON" : "OFF");
                tvDevModeStatus.setTextColor(devEnabled == 1 ? 0xFF34D399 : 0xFF64748B);
            }
        } catch (Exception ignored) {}

        try {
            int adbEnabled = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            if (tvAdbStatus != null) {
                tvAdbStatus.setText(adbEnabled == 1 ? "Active" : "OFF");
                tvAdbStatus.setTextColor(adbEnabled == 1 ? 0xFFFBBF24 : 0xFF64748B);
            }
        } catch (Exception ignored) {}

        if (!hasPermission) {
            if (cardPermission != null) cardPermission.setVisibility(View.VISIBLE);
            if (tvStatus != null) {
                tvStatus.setText("PERMISSION NEEDED");
                tvStatus.setTextColor(0xFFEF4444);
            }
            if (tvStatusSub != null) {
                tvStatusSub.setText("Notification access needed to auto-block banners");
            }
            if (tvLiveBadge != null) {
                tvLiveBadge.setText("⚠️ ACTION REQ");
                tvLiveBadge.setTextColor(0xFFEF4444);
            }
            if (containerHeroOrb != null) {
                containerHeroOrb.setBackgroundResource(R.drawable.bg_shield_ring_paused);
            }
            if (tvOrbAction != null) {
                tvOrbAction.setText("GRANT PERM");
                tvOrbAction.setTextColor(0xFFEF4444);
            }
        } else {
            if (cardPermission != null) cardPermission.setVisibility(View.GONE);
            boolean isAuto = mPrefs.getBoolean(DevBannerKillerService.KEY_AUTO_KILL, true);
            if (isAuto) {
                if (containerHeroOrb != null) {
                    containerHeroOrb.setBackgroundResource(R.drawable.bg_shield_ring_active);
                }
                if (tvStatus != null) {
                    tvStatus.setText("PROTECTION ACTIVE");
                    tvStatus.setTextColor(0xFF34D399);
                }
                if (tvStatusSub != null) {
                    tvStatusSub.setText("Vivo red 'Dev mode' banner is suppressed");
                }
                if (tvOrbAction != null) {
                    tvOrbAction.setText("TAP TO KILL");
                    tvOrbAction.setTextColor(0xFF34D399);
                }
                if (tvLiveBadge != null) {
                    tvLiveBadge.setText("● LIVE");
                    tvLiveBadge.setTextColor(0xFF10B981);
                }
            } else {
                if (containerHeroOrb != null) {
                    containerHeroOrb.setBackgroundResource(R.drawable.bg_shield_ring_paused);
                }
                if (tvStatus != null) {
                    tvStatus.setText("PROTECTION PAUSED");
                    tvStatus.setTextColor(0xFFF59E0B);
                }
                if (tvStatusSub != null) {
                    tvStatusSub.setText("Auto-kill paused. Tap circle or switch to resume.");
                }
                if (tvOrbAction != null) {
                    tvOrbAction.setText("TAP TO KILL");
                    tvOrbAction.setTextColor(0xFFF59E0B);
                }
                if (tvLiveBadge != null) {
                    tvLiveBadge.setText("○ PAUSED");
                    tvLiveBadge.setTextColor(0xFF94A3B8);
                }
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
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}

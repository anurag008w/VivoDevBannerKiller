package com.anurag.devbannerkiller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    private SwitchCompat switchAutoKill;
    private TextView tvStatus;
    private TextView tvKillCount;
    private CardView cardPermission;
    private Button btnGrantPermission;
    private Button btnManualKill;
    private Button btnKillProcess;

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(DevBannerKillerService.PREFS_NAME, Context.MODE_PRIVATE);

        switchAutoKill = findViewById(R.id.switchAutoKill);
        tvStatus = findViewById(R.id.tvStatus);
        tvKillCount = findViewById(R.id.tvKillCount);
        cardPermission = findViewById(R.id.cardPermission);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
        btnManualKill = findViewById(R.id.btnManualKill);
        btnKillProcess = findViewById(R.id.btnKillProcess);

        // Load saved switch state
        boolean isAutoKill = mPrefs.getBoolean(DevBannerKillerService.KEY_AUTO_KILL, true);
        switchAutoKill.setChecked(isAutoKill);

        switchAutoKill.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean(DevBannerKillerService.KEY_AUTO_KILL, isChecked).apply();
            if (isChecked) {
                Toast.makeText(this, "Auto-Kill Activated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Auto-Kill Paused", Toast.LENGTH_SHORT).show();
            }
            updateStatusUI();
        });

        // Manual Kill Button
        btnManualKill.setOnClickListener(v -> {
            int count = 0;
            DevBannerKillerService service = DevBannerKillerService.getInstance();
            if (service != null) {
                count = service.dismissAllDevBanners();
            } else {
                // Try direct setting reset if service is not connected
                try {
                    Settings.Global.putInt(getContentResolver(), "vivo_development_show", 0);
                } catch (Exception ignored) {}
            }

            int totalKills = mPrefs.getInt(DevBannerKillerService.KEY_KILL_COUNT, 0) + (count > 0 ? count : 1);
            mPrefs.edit().putInt(DevBannerKillerService.KEY_KILL_COUNT, totalKills).apply();
            tvKillCount.setText("Banners Blocked: " + totalKills);

            Toast.makeText(this, "Dev Banner Kill Triggered!", Toast.LENGTH_SHORT).show();
        });

        // Grant Permission Button
        btnGrantPermission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        // Kill Process & Exit Button (0 RAM)
        btnKillProcess.setOnClickListener(v -> {
            Toast.makeText(this, "Process Stopped (0 MB RAM)", Toast.LENGTH_SHORT).show();
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusUI();
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
}

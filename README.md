# Vivo DevBanner Killer 🚀

A lightweight, zero-bloat Android utility app designed specifically for Vivo (Funtouch OS) devices to intercept, dismiss, and block the persistent red "Dev Mode" warning badge from the status bar.

---

## ✨ Features

- **⚡ Instant Auto-Kill:** Uses Android's `NotificationListenerService` to detect Vivo's `com.vivo.daemonService` (ID `10100` / Channel `DEVELOPMENT_MODE`) and immediately destroys the notification (0ms delay).
- **🔄 Auto-Kill ON/OFF Toggle:** Easily toggle background auto-killing on or off directly from the UI.
- **🎯 1-Tap Manual Kill:** Clean the banner on-demand anytime with the "Kill Dev Banner Now" button.
- **🛑 0 MB RAM Mode:** Tap "Stop App Process" to completely terminate the app and reclaim 100% of RAM whenever you don't need it running.
- **🛡️ Setting Sync:** Keeps `Settings.Global.vivo_development_show = 0` automatically.

---

## 🏗️ How to Build with GitHub Actions

1. Create a new repository on GitHub (e.g. `VivoDevBannerKiller`).
2. Push this project code:
   ```bash
   git init
   git add .
   git commit -m "Initial commit of VivoDevBannerKiller"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/VivoDevBannerKiller.git
   git push -u origin main
   ```
3. Go to the **Actions** tab on your GitHub repository.
4. The workflow **"Build Android APK"** will run automatically and produce your ready-to-install `VivoDevBannerKiller-Debug.apk` under **Artifacts**!

---

## 📲 Setup on Phone

1. Install the APK on your Vivo phone.
2. Open the app and tap **"Grant Notification Access"** (or enable in *Settings > Status Bar & Notification > Notification Access*).
3. (Optional via ADB for direct settings sync):
   ```bash
   adb shell pm grant com.anurag.devbannerkiller android.permission.WRITE_SECURE_SETTINGS
   ```
4. Done! Developer Options can now stay ON without the annoying red banner.

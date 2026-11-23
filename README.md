# TECNO Showroom by TECH Hub (Android project)

This project wraps the supplied showroom HTML in an Android WebView.

## Build an APK without installing Android Studio (GitHub Actions)
1. Create a new repository on GitHub.
2. Extract this ZIP, then upload the contents of this folder to the repository's `main` branch. Keep the `.github/workflows/build-apk.yml` file and its folder structure.
3. Open the repository's **Actions** tab.
4. Select **Build Android APK** and press **Run workflow** (or wait for the first push build).
5. Open the completed workflow run. Under **Artifacts**, download **TECNO-Showroom-APK**.
6. Extract the downloaded artifact ZIP and install `app-debug.apk` on your Android phone. If prompted, allow installation from that source.

## Important
- This is a debug APK for personal/internal use, not a Play Store release.
- App data is stored on the device in WebView local storage. Clearing app data or uninstalling can erase it. Export backups regularly.
- Excel export uses the existing XLSX CDN and requires internet access.

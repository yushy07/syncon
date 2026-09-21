# SyncOn personal release handoff

SyncOn is currently distributed only for personal use. No Play Store, Chrome Web Store or GitHub Pages setup is required.

## Android

Build and install the normal debug APK:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

For a long-lived signed build, create a private keystore, copy `keystore.properties.example` to the repository root as `keystore.properties`, and fill in the local values. Keystores and `keystore.properties` are ignored and must never be committed.

## Chrome

Load the `extension` directory directly from `chrome://extensions` using **Load unpacked**. To create a convenient personal backup ZIP:

```powershell
powershell -ExecutionPolicy Bypass -File release\package-release.ps1 -Version 1.0.0 -SkipAndroid
```

The extension must be connected to the Android app by QR code before its dashboard or tracking activates.

The packaging command expects the ignored local `extension/manifest.json` and `extension/lib/backend-config.js` files. Use their tracked `.example` templates when configuring a fresh checkout.

## Reference documents

The privacy, permission, listing and screenshot files in this directory are retained as product documentation and as optional references if distribution is considered later. They are not current publication tasks.

Generated files under `release/artifacts/` and `release/screenshots/` are intentionally ignored by Git.

# CART3R Android Bridge — Phase 1

Android companion foundation for **Realme C71 RMX5303 / Android 16**.

## Phase 1 features

- Dark crimson Jetpack Compose dashboard.
- Device model, manufacturer, device/product identifiers, Android version and build fingerprint.
- KernelSU/root availability check using only `su -c id`.
- No arbitrary shell input, remote command endpoint, or system modifications.

The root check can trigger the KernelSU superuser approval dialog. Approve it only if you trust this APK. Denying it does not affect normal dashboard functionality.

## Build with GitHub Actions

1. Create a GitHub repository.
2. Upload the contents of this ZIP to the repository root.
3. Push to `main` or `master`, or open **Actions → Build CART3R Android Bridge → Run workflow**.
4. Download the `CART3R-Bridge-debug` artifact from the completed workflow.
5. Extract `app-debug.apk` and install it on the device.

## Build locally

Requirements: JDK 17 and Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Technical details

- Application ID: `com.cart3r.bridge`
- Version: `1.0.0` (versionCode 1)
- minSdk: 26
- targetSdk: 35
- compileSdk: 35
- Kotlin 2.0.21
- Android Gradle Plugin 8.7.3
- Gradle 8.9

## Security notes

This is a diagnostic companion, not a remote root shell. Phase 1 does not include a network listener, AI command execution, or write access to protected system paths. Later phases should add a fixed operation registry, strict argument validation, explicit on-device confirmation, audit logging, and rollback support before introducing any system-changing action.

# CART3R Android Bridge — Phase 3
**Developer / project owner: Novvaaa**

Phase 3 carries forward the Phase 2 project for Realme C71 RMX5303 / Android 16.
The package ID remains `com.cart3r.bridge`; the dashboard retains the dark crimson
visual style and the existing KernelSU diagnostic implementation.

## Included
- Phase 2 diagnostics retained: CPU, Mali GPU, memory, thermal zones, kernel, uptime and KernelSU modules.
- Dashboard identifies the developer as **Novvaaa**.
- Phase 3 System Controller panel documents the controlled-write boundary.
- Version updated to 3.0.0 (versionCode 3).

## Important implementation status
This build is a **Phase 3 foundation / controller shell**, not full remote root control.
It does not yet disable, uninstall, or modify applications, modules, CPU/GPU settings,
or system files. The panel deliberately does not expose arbitrary shell input.
A later implementation must add a package inventory, protected-package safeguards,
a local confirmation screen, operation-specific allowlists, audit records, and rollback.
ChatGPT does not have direct access to the phone from this APK; an authenticated AI Bridge
would be a separate future component.

## Safety model
- Root checks and diagnostics use fixed, app-authored commands.
- No network listener or remote command endpoint.
- No arbitrary command entry.
- No system write is performed by this build.
- Any future write operation must be individually allowlisted and confirmed locally.

## Build
Use JDK 17 and Gradle 8.9:
`gradle --no-daemon :app:assembleDebug`

GitHub Actions workflow: `.github/workflows/build.yml`.
Artifact output: `app/build/outputs/apk/debug/app-debug.apk`.

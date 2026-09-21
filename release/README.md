# SyncOn release handoff

1. Read `PRIVACY_POLICY.md`, `ANDROID_DATA_SAFETY.md`, `CHROME_PERMISSION_JUSTIFICATIONS.md`, and `STORE_LISTINGS.md`.
2. Create a private Android upload keystore and copy `keystore.properties.example` to the repository root as `keystore.properties`.
3. Run `powershell -ExecutionPolicy Bypass -File release/package-release.ps1 -Version 1.0.0`.
4. Capture the truthful runtime screenshots in `SCREENSHOT_PLAN.md`.
5. Enable GitHub Pages with **GitHub Actions** as its source once; the included workflow publishes the policy at `https://yushy07.github.io/syncon/privacy.html`. Use that URL in both store consoles.
6. Upload the signed `.aab` to a Play internal-testing track and the extension `.zip` to Chrome Web Store draft review.

`release/artifacts/`, private keystores, and `keystore.properties` are intentionally ignored by Git.

# Metrom Android 1.0.7 — Production Release

Production Android shell for Metrom.

- Final applicationId: `ir.msbmusic.metrom`
- Start URL: `https://msbmusic.ir/metrom/login.php?next=studio.php`
- Verified TWA only; no intentional normal-browser fallback.
- AndroidX SplashScreen is kept on-screen until the trusted TWA is ready, preventing the intermediate white frame.
- Launcher and splash artwork use the exact site icon source in `brand/metrom-icon-512.png`.
- Release workflow verifies the expected signing certificate and the live Digital Asset Links file before building.

Use **Android CI** for source validation only. Use **Build Signed Release** for distributable APK/AAB artifacts.

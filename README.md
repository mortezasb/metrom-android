# Metrom Android TWA

Production-oriented Android shell for **Metrom** (`https://msbmusic.ir/metrom/`).

- TWA UI with a narrow `/metrom/` App Link scope
- Android 6.0+ (`minSdk 23`), target API 36
- AndroidX Browser 1.10.0
- Native `mediaPlayback` foreground service for the metronome
- Low-overhead static PCM loop; no wake lock or background network polling
- TWA postMessage bridge plus a user-gesture activation Activity for Android 12+ foreground-service restrictions
- Exact Metrom web icon included for launcher branding
- GitHub Actions for debug APK and signed APK/AAB releases

Read `README-FA.md` and `docs/GITHUB-BUILD-FA.md` for the full deployment flow.

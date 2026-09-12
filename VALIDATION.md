# Validation

Static release checks completed in the ChatGPT build environment:

- Android XML resources/manifest: 15 files parsed successfully
- GitHub Actions YAML: 2 workflows parsed successfully
- assetlinks template JSON: parsed successfully
- Java source: 3 files passed package/brace structural checks
- Web companion JavaScript: `node --check` passed
- Web 5.5.3 companion patch JavaScript/PHP: syntax checks passed
- Current Metrom 512×512 web icon copied byte-for-byte to `brand/metrom-icon-512.png`
- Legacy launcher PNGs generated from that current Metrom icon
- Manifest permission audit: INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS only
- No WAKE_LOCK, location, microphone, camera, WorkManager, Retrofit or OkHttp dependency added
- App Link scope is restricted to `https://msbmusic.ir/metrom/`
- minSdk 23, targetSdk 36, AndroidX Browser 1.10.0, AGP 9.4.0, Java 17

A full Android Gradle compile cannot be run in this container because Android SDK/Gradle are not installed. The included GitHub Actions workflows install API 36 + Build Tools 36.0.0 + Gradle 9.6.0 and perform the real Android lint/build after the repository is pushed.

# Metrom Android 1.0.4 validation targets

- Package: `ir.msbmusic.metrom`
- minSdk 23 / targetSdk 36
- App Link host: `msbmusic.ir`
- App Link path: exact `/Metrom` plus prefix `/Metrom/`
- TWA start: `https://msbmusic.ir/Metrom/studio.php?source=twa`
- Release SHA-256 present in assetlinks template
- AndroidX SplashScreen dependency: 1.2.0
- MainActivity installs SplashScreen before `super.onCreate`
- No dedicated splash Activity (prevents double splash on Android 12+)
- Background receiver uses `RECEIVER_NOT_EXPORTED`
- Android 27-only navigation-bar attribute isolated in values-v27
- Debug and signed release GitHub workflows retained

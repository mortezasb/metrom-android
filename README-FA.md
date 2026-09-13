# Metrom Android — سورس نهایی Release 1.0.4

این مخزن پوسته اندروید رسمی **Metrom** برای مسیر قطعی:

`https://msbmusic.ir/Metrom/`

است.

## ویژگی‌های نسخه نهایی

- نام برنامه: **Metrom**
- Package: `ir.msbmusic.metrom`
- Android 6+ (`minSdk 23`)
- Target Android API 36
- TWA با App Link محدود به `/Metrom` و `/Metrom/`
- Splash استاندارد Android با `androidx.core:core-splashscreen:1.2.0`
- همان هویت بصری و آیکون Metrom
- موتور Native مترونوم برای ادامه پخش در پس‌زمینه/صفحه خاموش
- بدون Polling، WorkManager، Location، Camera یا Microphone
- Signed APK برای انتشار مستقیم/مایکت و AAB برای Google Play
- GitHub Actions برای Debug و Signed Release
- Asset Links با SHA-256 کلید Release فعلی آماده شده است

## قبل از Release

فایل سایت **Metrom Studio Beat 5.5.4** را روی `/Metrom/` Direct Overwrite کنید.
فایل مشترک `https://msbmusic.ir/.well-known/assetlinks.json` باید شامل package
`ir.msbmusic.metrom` و SHA زیر باشد:

`AA:16:46:81:E2:22:06:31:F1:02:A5:64:8A:35:F1:42:72:96:3F:86:14:31:67:3E:F0:0C:91:B7:60:74:EF:1D`

## GitHub Secrets

- `METROM_KEYSTORE_BASE64`
- `METROM_KEYSTORE_PASSWORD`
- `METROM_KEY_ALIAS` = `metrom`
- `METROM_KEY_PASSWORD`

سپس از **Actions → Build Signed Release** نسخه Release را بسازید.

برای مایکت، فایل Signed APK خروجی Workflow مناسب انتشار است.

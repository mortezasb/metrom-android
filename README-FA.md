# Metrom Android 1.0.5 — نسخه نهایی انتشار

- دامنه: `msbmusic.ir`
- Scope اپ: `https://msbmusic.ir/metrom/`
- صفحه شروع اپ: `https://msbmusic.ir/metrom/login.php?next=studio.php`
- پکیج: `ir.msbmusic.metrom`
- لانچر و Splash از همان فایل `icon-512.png` سایت ساخته شده‌اند.
- TWA بعد از Digital Asset Links معتبر، بدون نوار مرورگر و بدون نمایش URL اجرا می‌شود.
- Signed Release با چهار Secret گیت‌هاب ساخته می‌شود.
- خطای Signing نسخه 1.0.4 ناشی از هم‌نام بودن متغیر Groovy `keyPassword` با property DSL در 1.0.5 رفع شده است.

## انتشار
1. فایل سایت 5.5.5 را روی `/metrom/` Direct Overwrite کنید.
2. `assetlinks.json` مشترک MSB + Metrom باید در `https://msbmusic.ir/.well-known/assetlinks.json` باقی بماند.
3. سورس 1.0.5 را روی GitHub جایگزین کنید.
4. Android CI را اجرا کنید.
5. Build Signed Release را با `version_code=1` و `version_name=1.0.0` اجرا کنید.
6. APK برای مایکت/نصب مستقیم و AAB برای Google Play استفاده می‌شود.

نکته: Debug APK با گواهی Debug امضا می‌شود و چون SHA آن در assetlinks تولید نیست، ممکن است نوار مرورگر نشان دهد. معیار تست Full-screen، Signed Release است.

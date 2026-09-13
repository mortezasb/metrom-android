# Metrom Android 1.0.6 — Production Release

این شاخه برای ساخت نسخه نهایی امضاشده Metrom است.

## تفاوت Debug و Release
- `Android CI` فقط صحت سورس را با APK دیباگ بررسی می‌کند. نسخه Debug برای انتشار نیست و به علت `applicationIdSuffix=.debug` و امضای Debug، TWA تولید را Verify نمی‌کند.
- `Build Signed Release` خروجی واقعی انتشار را با package نهایی `ir.msbmusic.metrom` و کلید Release می‌سازد.

## رفتار نهایی 1.0.6
- شروع اپ: `https://msbmusic.ir/metrom/login.php?next=studio.php`
- Splash فقط یک سطح استاندارد Android است و تا آماده شدن TWA روی همان سطح می‌ماند؛ فریم سفید بین Splash و اپ حذف شده است.
- آیکون و Splash از Artwork اصلی سایت `brand/metrom-icon-512.png` استفاده می‌کنند.
- قبل از نمایش محتوای وب، رابطه `handle_all_urls` با Digital Asset Links بررسی می‌شود.
- اگر رابطه امن تایید نشود، Metrom یک صفحه Native داخلی با Retry نشان می‌دهد و عمدا URL سایت را در Browser/Custom Tab باز نمی‌کند.
- Workflow انتشار SHA-256 کلید Release و `assetlinks.json` زنده سایت را قبل از Build بررسی می‌کند.

## ساخت نسخه نهایی
GitHub > Actions > Build Signed Release > Run workflow

برای اولین انتشار:
- version_code: `1`
- version_name: `1.0.0`

Artifactهای نهایی:
- `METROM-FINAL-1.0.0-SIGNED-APK`
- `METROM-FINAL-1.0.0-AAB`
- `METROM-FINAL-1.0.0-RELEASE-INFO`

APK امضاشده برای نصب مستقیم/مایکت است. AAB برای Google Play است.

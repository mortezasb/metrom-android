# Metrom Android 1.0.9 — Production Release

این شاخه برای ساخت نسخه نهایی امضاشده Metrom است.

## تفاوت Debug و Release
- `Android CI` فقط صحت سورس را با APK دیباگ بررسی می‌کند. نسخه Debug برای انتشار نیست و به علت `applicationIdSuffix=.debug` و امضای Debug، TWA تولید را Verify نمی‌کند.
- `Build Signed Release` خروجی واقعی انتشار را با package نهایی `ir.msbmusic.metrom` و کلید Release می‌سازد.

## رفتار نهایی 1.0.7
- شروع اپ: `https://msbmusic.ir/metrom/login.php?next=studio.php`
- Splash اول همان برند اصلی Android است؛ بعد از آن به جای لوگوی تکراری، Loading واقعی Native نمایش داده می‌شود و Splash خود TWA نیز نشانگر Loading دارد.
- آیکون و Splash اولیه از Artwork اصلی سایت `brand/metrom-icon-512.png` استفاده می‌کنند.
- در اجرای آنلاین، قبل از نمایش محتوای وب رابطه `handle_all_urls` با Digital Asset Links بررسی می‌شود.
- بعد از یک تایید موفق، اجرای آفلاین مجاز است تا Service Worker همان Studio کش شده را باز کند؛ اولین اجرای برنامه همچنان برای اعتماد امن و کش اولیه به اینترنت نیاز دارد.
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

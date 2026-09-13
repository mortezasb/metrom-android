# Metrom Android 1.0.7 — Final App Shell

این شاخه نسخه نهایی Android برای Metrom است.

## تغییرات اصلی 1.0.7
- شروع آنلاین همچنان از `https://msbmusic.ir/metrom/login.php?next=studio.php` است.
- Launch Cover هم‌شکل Splash اصلی اضافه شده تا در Cold Start، نوار URL/Toolbar مرورگر در لحظه تحویل TWA دیده نشود.
- هنگام بازگشت از TWA، Activity میزبان بلافاصله بسته می‌شود تا صفحه سیاه/اسپلش دوم در Back Stack دیده نشود.
- در رابط وب نصب‌شده، خروج از برنامه در ریشه با دو بار Back انجام می‌شود.
- اگر اینترنت در شروع برنامه وجود نداشته باشد، Metrom مستقیم یک مترونوم Native آفلاین باز می‌کند؛ بدون درخواست شبکه، Polling یا فشار به سرور.
- مترونوم آفلاین BPM، TAP، میزان 2/4، 3/4، 4/4 و 6/8، پخش/توقف و ادامه پخش در پس‌زمینه را دارد.
- آیکون Launcher، Splash و Brand از بازسازی باکیفیت لوگوی مرجع Metrom استفاده می‌کنند.
- Digital Asset Links و امضای Release قبلی بدون تغییر باقی مانده‌اند.

## Debug و Release
`Android CI` فقط برای تست سورس است. خروجی انتشار واقعی فقط از:

GitHub > Actions > Build Signed Release > Run workflow

برای اولین انتشار عمومی:
- version_code: `1`
- version_name: `1.0.0`

Artifactهای انتشار:
- `METROM-FINAL-1.0.0-SIGNED-APK`
- `METROM-FINAL-1.0.0-AAB`
- `METROM-FINAL-1.0.0-RELEASE-INFO`

APK برای مایکت/نصب مستقیم و AAB برای Google Play است.

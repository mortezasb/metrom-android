# Metrom Android TWA

سورس اندروید **Metrom** برای انتشار روی مارکت‌ها و اجرای رابط Metrom Studio Beat به شکل Trusted Web Activity.

## معماری

- رابط اصلی: TWA روی `https://msbmusic.ir/metrom/`
- Application ID: `ir.msbmusic.metrom`
- نام روی گوشی: `Metrom`
- حداقل Android: API 23 / Android 6.0
- Target SDK: API 36 / Android 16
- AndroidX Browser: 1.10.0
- Java: 17
- Android Gradle Plugin: 9.4.0
- Gradle موردنیاز: 9.6.0
- مسیرهای App Link فقط `/metrom/` هستند تا با PWA/TWAهای دیگر روی `msbmusic.ir` تداخل نداشته باشند.

## پخش مترونوم با صفحه خاموش

TWA به تنهایی تضمین نمی‌کند WebAudio هنگام خاموش شدن صفحه روی همه نسخه‌های Android و همه سازندگان گوشی ادامه پیدا کند. این سورس برای صدای مترونوم یک `mediaPlayback` Foreground Service بومی دارد.

سرویس بومی:

- فقط هنگام پخش مترونوم فعال می‌شود.
- هیچ Polling یا درخواست شبکه‌ای ندارد.
- Wake Lock درخواست نمی‌کند.
- میکروفون، دوربین، Location و Storage درخواست نمی‌کند.
- یک میزان PCM را فقط هنگام تغییر BPM/میزان/ولوم می‌سازد و با `AudioTrack.MODE_STATIC` لوپ می‌کند.
- هنگام خاموش شدن صفحه، صدا توسط سرویس بومی ادامه پیدا می‌کند.
- Notification دارای دکمه Stop است.
- با Audio Focus سیستم هماهنگ است.

برای Android 12+، شروع Foreground Service از callback پس‌زمینه TWA قابل اتکا نیست. به همین علت `PlaybackBridgeActivity` فقط در لحظه لمس واقعی Play باز می‌شود، سرویس را شروع می‌کند و بلافاصله به TWA برمی‌گردد. برای این مسیر باید Web Companion Patch نسخه 5.5.3 روی سایت نصب باشد.

## فشار روی سرور

کد Android هیچ درخواست دوره‌ای به سرور ایجاد نمی‌کند. `MainActivity` فقط browser process را Warm-up می‌کند و عمدا `mayLaunchUrl()` استفاده نمی‌کند تا صفحه را پیشاپیش Fetch نکند.

Static assetهای Web App توسط Service Worker خود Metrom Cache می‌شوند. درخواست‌های سرور فقط زمانی اتفاق می‌افتند که واقعا لازم باشند؛ مانند ورود، بارگذاری صفحه پویا، ذخیره/Sync پروژه یا پرداخت. Foreground Metronome کاملا محلی است.

## قبل از Build واقعی

1. Web App نسخه 5.5.2 را داشته باشید.
2. Patch همراه `Metrom-Studio-Beat-5.5.3-Android-Background-Bridge-DIRECT-OVERWRITE.zip` را روی `/metrom/` Direct Overwrite کنید.
3. یک Release Keystore بسازید.
4. SHA-256 گواهی Release/Store را در `assetlinks.json` سایت ادغام کنید.
5. Repository را در GitHub قرار دهید.
6. Secrets امضا را تعریف کنید.
7. Workflow `Build Signed Release` را اجرا کنید.

راهنمای کامل فارسی در `docs/GITHUB-BUILD-FA.md` و `docs/SIGNING-ASSETLINKS-FA.md` است.

## نکته درباره «همه گوشی‌های اندروید»

این پروژه Android 6.0 و بالاتر را هدف می‌گیرد و هیچ کتابخانه Native وابسته به معماری CPU ندارد، پس APK از نظر ABI روی ARM و x86 محدود نشده است. با این حال هیچ اپی نمی‌تواند اجرای یکسان روی تمام نسخه‌های قدیمی، ROMهای دستکاری‌شده و سیاست‌های باتری همه سازندگان را تضمین کند. روی دستگاهی که TWA/PostMessage مدرن پشتیبانی نشود، رابط به Browser/Custom Tab برمی‌گردد؛ قابلیت Native Screen-off برای مسیر تاییدشده TWA و Browser سازگار طراحی شده است.

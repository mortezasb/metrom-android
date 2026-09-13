# معماری کم‌فشار روی سرور

هدف این پروژه این نیست که سایت را در Background مدام بازخوانی کند.

## Android Native

- Foreground Metronome هیچ HTTP request ندارد.
- هیچ Polling، WorkManager، JobScheduler یا Timer شبکه‌ای اضافه نشده است.
- Browser فقط Warm-up می‌شود؛ صفحه با `mayLaunchUrl()` پیش‌واکشی نمی‌شود.
- AudioTrack یک میزان PCM را در حافظه Loop می‌کند.

## TWA/Web App

خود TWA محتوای `/metrom/` را از Web App نمایش می‌دهد، بنابراین درخواست‌های واقعی برنامه مثل Login، لود صفحه پویا، Cloud Project Save/Sync و Payment طبیعتا به سرور می‌رسند. Static assetها توسط Service Worker Metrom Cache می‌شوند و Navigation داخل Studio تا حد زیادی SPA است.

اگر هدف صفر کردن درخواست سرور باشد باید برنامه کاملا Native/Offline بازنویسی شود؛ TWA ذاتا رابط Web را نمایش می‌دهد. معماری فعلی تعادل بین سرعت توسعه، Update فوری Web App، مصرف کم و فشار منطقی روی سرور است.

## Offline-first 1.0.7

- تشخیص Online/Offline فقط با `ConnectivityManager` روی خود دستگاه انجام می شود و هیچ request ایجاد نمی کند.
- بعد از اولین Digital Asset Links موفق، در شروع کاملا آفلاین درخواست Validation دستی جدیدی به سایت تحمیل نمی شود و TWA از اعتماد قبلی/کش مرورگر استفاده می کند.
- Loading Native و TWA کاملا محلی هستند.
- Snapshot صفحه Studio و Cache Storage توسط Service Worker وب انجام می شوند و برای ساخت Snapshot هیچ درخواست دوم PHP/Database ایجاد نمی شود.

### محافظ URL در Offline-first
- نام Browser Provider تاییدشده فقط در SharedPreferences محلی دستگاه نگهداری می شود.
- این کنترل هیچ Request، API، Query، Polling یا Telemetry ایجاد نمی کند.
- در حالت آفلاین فقط همان Provider قبلا تاییدشده اجازه شروع TWA دارد.

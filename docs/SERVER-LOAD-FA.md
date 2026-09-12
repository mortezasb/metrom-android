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

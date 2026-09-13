# Offline-first و Loading — Android source 1.0.7

این Revision برای اولین انتشار فروشگاهی همچنان با `versionCode=1` و `versionName=1.0.0` ساخته می شود.

- Splash اول Android با لوگوی اصلی حفظ شده است.
- سطح دوم دیگر لوگوی کوچک نیست: Activity یک ProgressBar واقعی نشان می دهد و TWA در مرورگرهای سازگار نیز Splash Loading اختصاصی دارد.
- اولین اجرای موفق باید آنلاین باشد تا Digital Asset Links تایید شود و Web Studio توسط Service Worker ذخیره شود.
- بعد از اولین تایید، اگر Android اتصال اینترنت Validation شده نداشته باشد، TWA فقط با همان Browser Provider که قبلا آنلاین تایید شده است از مسیر Offline-first اجرا می شود.
- اگر Provider مرورگر تغییر کرده باشد، برنامه آفلاین داخل سطح Native می ماند تا هیچ Custom Tab یا نوار آدرسی به عنوان fallback نمایش داده نشود.
- موتور `BackgroundMetronomeService` هیچ HTTP request، Polling، WorkManager یا Query دیتابیس ندارد و همان PCM loop داخلی را استفاده می کند.
- URL سایت عمدا در fallback مرورگر باز نمی شود؛ در خطای اعتماد، صفحه Native داخلی نمایش داده می شود.
- `ACCESS_NETWORK_STATE` فقط برای تشخیص Online/Offline محلی استفاده می شود و هیچ داده ای ارسال نمی کند.

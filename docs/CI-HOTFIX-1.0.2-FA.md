# Metrom Android 1.0.2 — Android Lint API compatibility

این اصلاح `android:windowLightNavigationBar` را از resources عمومی حذف می‌کند و فقط در `values-v27` قرار می‌دهد.
به این ترتیب minSdk 23 حفظ می‌شود و Android 6 تا 8.0 برای inflate کردن Theme به attribute مربوط به API 27 دسترسی پیدا نمی‌کنند.

فایل‌های تغییرکرده:
- app/src/main/res/values/styles.xml
- app/src/main/res/values-v27/styles.xml
- VERSION

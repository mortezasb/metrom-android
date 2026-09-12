# Hotfix 1.0.3 — Dynamic BroadcastReceiver

این اصلاح خطای Android Lint با شناسه `UnspecifiedRegisterReceiverFlag` را رفع می‌کند.

`BackgroundMetronomeService` اکنون Receiver کنترل داخلی را با `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` ثبت می‌کند. در نتیجه Broadcast فقط برای داخل همان برنامه قابل استفاده است و روی Android 6 تا نسخه‌های جدید سازگاری حفظ می‌شود.

Lint خاموش نشده و minSdk نیز همچنان 23 است.

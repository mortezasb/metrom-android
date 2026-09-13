# چک‌لیست انتشار Metrom در مایکت

1. ابتدا فایل سایت 5.5.4 را روی `https://msbmusic.ir/metrom/` نصب کنید.
2. بررسی کنید `https://msbmusic.ir/.well-known/assetlinks.json` بدون Redirect و با JSON معتبر باز شود.
3. GitHub Secrets امضای Release را تنظیم کنید.
4. از Actions → Build Signed Release با:
   - version_code = 1
   - version_name = 1.0.0
   خروجی بگیرید.
5. برای مایکت از `app-release.apk` امضاشده استفاده کنید.
6. APK را قبل از ارسال روی حداقل یک Android قدیمی (6–9) و یک Android جدید (13–16) تست کنید.
7. سناریوهای ورود/عضویت، قوانین/حریم خصوصی، Studio، Play/Stop، صفحه خاموش، Notification Stop و خروجی WAV/MP3 را تست کنید.
8. لینک حریم خصوصی:
   `https://msbmusic.ir/metrom/privacy.php`
9. لینک قوانین:
   `https://msbmusic.ir/metrom/terms.php`
10. مجوزهای برنامه فقط برای اینترنت، Foreground media playback و Notification استفاده می‌شوند؛ Location/Camera/Microphone/Storage درخواست نمی‌شوند.
11. آیکون، نام برنامه و اسکرین‌شات‌های فروشگاه باید با محصول واقعی Metrom یکسان باشند.
12. فایل Keystore و رمزهای امضا را در GitHub یا فایل عمومی قرار ندهید.

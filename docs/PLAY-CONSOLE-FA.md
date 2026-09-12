# چک‌لیست انتشار در Google Play

1. Package name: `ir.msbmusic.metrom`
2. App name: `Metrom`
3. Target API: 36
4. نسخه Release را با AAB ارسال کنید.
5. Privacy Policy می‌تواند صفحه عمومی Metrom باشد: `https://msbmusic.ir/metrom/privacy.php?lang=en`
6. بخش Data safety را مطابق داده‌هایی که واقعا در Login، پروژه‌ها، پرداخت و Analytics خودتان جمع‌آوری/پردازش می‌کنید تکمیل کنید؛ چیزی را حدس نزنید.
7. چون برنامه برای ادامه مترونوم با صفحه خاموش از `mediaPlayback` Foreground Service استفاده می‌کند، Declaration مربوط به Foreground Service را در Play Console کامل کنید و توضیح/ویدیوی موردنیاز را بر اساس رفتار واقعی برنامه ارائه دهید.
8. SHA-256 App Signing Certificate گوگل پلی را بعد از فعال شدن Play App Signing در `assetlinks.json` سایت اضافه کنید.
9. قبل از Production، روی Android 6/8/10/12/13/14/15/16 و حداقل چند برند مختلف تست Smoke انجام دهید.

موارد تست ضروری:

- ورود/عضویت با موبایل و ایمیل
- TWA بدون نوار مرورگر بعد از DAL verification
- Play/Pause/Stop
- تغییر BPM و میزان
- خاموش کردن صفحه هنگام مترونوم
- Notification و Stop
- قطع/وصل هدفون و Audio Focus
- ذخیره و Sync پروژه
- Export WAV/MP3
- پرداخت و بازگشت از درگاه
- Deep Link فقط داخل `/metrom/`

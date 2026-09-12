# قرار دادن Metrom در GitHub و گرفتن APK/AAB

## 1) ساخت Repository

در GitHub یک Repository جدید بسازید. پیشنهاد نام:

`metrom-android`

برای شروع می‌توانید آن را Private نگه دارید. README یا .gitignore جدید از سمت GitHub نسازید چون این سورس خودش آنها را دارد.

## 2) Push سورس

ZIP سورس را Extract کنید و داخل پوشه پروژه Terminal/Git Bash باز کنید:

```bash
git init
git branch -M main
git add .
git commit -m "Initial Metrom Android TWA"
git remote add origin https://github.com/YOUR_USERNAME/metrom-android.git
git push -u origin main
```

بعد از Push، Workflow `Android CI` خودکار اجرا می‌شود و یک Debug APK به عنوان Artifact می‌سازد.

**توجه:** Debug APK برای تست Build/UI مناسب است، ولی TWA verification و Native postMessage کامل به گواهی‌ای نیاز دارد که SHA-256 آن در assetlinks سایت ثبت شده باشد. برای تست نهایی از Signed Release استفاده کنید.

## 3) ساخت کلید Release

روی سیستمی که Java JDK دارد:

```bash
keytool -genkeypair -v -keystore metrom-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias metrom
```

این فایل را در GitHub Commit نکنید و نسخه پشتیبان امن از آن نگه دارید.

## 4) تبدیل Keystore به Base64 در Windows PowerShell

از ریشه‌ای که فایل `metrom-upload.jks` در آن است:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("metrom-upload.jks")) | Set-Clipboard
```

یا اسکریپت `scripts/keystore-to-base64.ps1` را اجرا کنید.

## 5) GitHub Actions Secrets

Repository > Settings > Secrets and variables > Actions > New repository secret

این چهار Secret را بسازید:

- `METROM_KEYSTORE_BASE64` = Base64 فایل keystore
- `METROM_KEYSTORE_PASSWORD` = رمز keystore
- `METROM_KEY_ALIAS` = `metrom`
- `METROM_KEY_PASSWORD` = رمز Key/Alias

## 6) Build نسخه Release

GitHub > Actions > `Build Signed Release` > Run workflow

برای اولین انتشار:

- version_code: `1`
- version_name: `1.0.0`

Workflow دو Artifact می‌دهد:

- AAB: برای Google Play و هر مارکتی که App Bundle می‌پذیرد
- APK امضاشده: برای نصب مستقیم و مارکت‌هایی که APK می‌پذیرند

برای هر Update باید `version_code` حتما افزایش پیدا کند؛ مثلا 2، 3، 4 و ... .

## 7) Build محلی در Android Studio

پروژه از AGP 9.4.0، Gradle 9.6 و JDK 17 استفاده می‌کند. اگر Android Studio از شما Gradle Distribution خواست، Gradle 9.6 را انتخاب کنید.

برای Command Line بدون Gradle Wrapper:

```bash
gradle :app:assembleDebug
```

GitHub Actions خودش Gradle 9.6 را نصب می‌کند و نیاز به Wrapper ندارد.

# امضا و Digital Asset Links

## Application ID

Application ID این پروژه:

`ir.msbmusic.metrom`

اگر برنامه هنوز در هیچ مارکتی منتشر نشده و می‌خواهید آن را عوض کنید، قبل از اولین انتشار انجام دهید. بعد از انتشار، Application ID هویت اصلی برنامه است و نباید برای Update تغییر کند.

## گرفتن SHA-256 کلید Release

Linux/macOS:

```bash
./scripts/print-signing-sha256.sh metrom-upload.jks metrom
```

Windows PowerShell:

```powershell
./scripts/print-signing-sha256.ps1 -Keystore metrom-upload.jks -Alias metrom
```

یا مستقیما:

```bash
keytool -list -v -keystore metrom-upload.jks -alias metrom
```

مقدار `SHA256:` را بردارید.

## assetlinks.json

فایل باید در این مسیر عمومی باشد:

`https://msbmusic.ir/.well-known/assetlinks.json`

فایل `assetlinks/assetlinks-metrom-template.json` را به فایل موجود روی سایت **ادغام** کنید. چون روی همین دامنه برنامه‌های PWA/TWA دیگری دارید، هرگز assetlinks قبلی را کامل با Template متروم جایگزین نکنید.

رابطه‌های Metrom باید هر دو مورد را داشته باشند:

- `delegate_permission/common.handle_all_urls`
- `delegate_permission/common.use_as_origin`

اولی برای App Links/TWA verification و دومی برای postMessage بومی Metrom استفاده می‌شود.

## Google Play App Signing

اگر Google Play App Signing فعال باشد، APK نهایی که Play به کاربر می‌دهد ممکن است با **App Signing Certificate** گوگل امضا شود، نه Upload Key شما. در این حالت برای کاربران Google Play باید SHA-256 مربوط به `App signing key certificate` از Play Console را هم داخل `sha256_cert_fingerprints` قرار دهید.

اگر همان برنامه را با کلید Release خودتان در مارکت دیگری منتشر می‌کنید، می‌توانید چند SHA-256 را در همان Array قرار دهید تا هر دو امضا تایید شوند.

نمونه:

```json
"sha256_cert_fingerprints": [
  "PLAY_APP_SIGNING_SHA256",
  "DIRECT_MARKET_RELEASE_SHA256"
]
```

بعد از آپلود assetlinks مطمئن شوید URL با Status 200، بدون Login و بدون Redirect غیرضروری و با JSON معتبر باز می‌شود.

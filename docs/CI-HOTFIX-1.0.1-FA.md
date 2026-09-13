# اصلاح CI نسخه 1.0.1

این اصلاح برای سازگاری پروژه Metrom با AGP 9.4 و Gradle 9.6 است.

پروژه Native فعلی Java-only است و به Kotlin نیاز ندارد. در AGP 9.x قابلیت Built-in Kotlin به صورت پیش فرض فعال است و در برخی ترکیب های Source Set می تواند قبل از مرحله Compile باعث توقف Build شود. برای نسخه فعلی، Built-in Kotlin و DSL جدید در gradle.properties غیرفعال شده اند تا Build پایدار بماند.

Workflow دیباگ نیز خروجی کامل Gradle را در فایل gradle-debug.log ذخیره می کند و در صورت شکست آن را به عنوان Artifact با نام metrom-gradle-debug-log منتشر می کند.

پس از جایگزینی فایل ها، در GitHub Actions اجرای Android CI را دوباره Run کنید.

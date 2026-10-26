# راهنمای جلوگیری از شکست ورک‌فلو (Android Build & Release) — VaamYar

این فایل را قبل از هر تغییر کد یا ورک‌فلو بخوانید. تمام موارد زیر از شکست‌های **واقعی** همین ریپو استخراج شده‌اند
(run ها و علت‌ها در جدول بخش ۲ آمده است).

## ۱. قانون‌های طلایی (قبل از هر push)

1. **هر push به `main` یک ریلیز عمومی می‌سازد** (bump نسخه + تگ + GitHub Release). پس هرگز کد تأییدنشده را مستقیم روی `main` نبرید.
   مسیر درست: شاخه‌ی `fix/...` → اجرای سبز ورک‌فلوی **Unit Tests (no release)** → سپس merge به `main`.
2. **روی کامیت‌های کد هرگز `[skip ci]` نگذارید.** این برچسب فقط برای کامیت‌های «صرفاً مستندات» (`.md`/`.txt`) است.
   ماجرای واقعی: حدود ۹۶ کامیت کد با `[skip ci]` بین دو ریلیز رفت، هیچ بیلدی روی آن‌ها اجرا نشد، و خطای کامپایل تا اولین اجرای بعدی پنهان ماند.
3. **هیچ فایل کدی را «بازنویسی دستی/ابزاری» منتقل نکنید.** برای تغییر نام پکیج یا جابه‌جایی فایل از `git mv` + `sed` استفاده کنید،
   نه خواندن و دوباره‌نوشتن محتوا با ابزار/API (محتوا ممکن است وسط فایل بریده شود).
4. **بعد از هر نوشتن یا انتقال فایل بزرگ، سالم‌بودن را بررسی کنید:** تعداد خطوط با نسخه‌ی قبل برابر/منطقی باشد،
   آکولاد `{ }` متوازن باشد، و آخرین خط فایل همان چیزی باشد که انتظار دارید (`tail -n 5 file`).
5. **اگر شبکه ندارید و نمی‌توانید بیلد بگیرید، کد را «تأییدنشده» اعلام کنید** و حتماً روی شاخه push کنید تا CI تست را اجرا کند؛
   هرگز با فرض «احتمالاً کامپایل می‌شود» به `main` merge نکنید.
6. **هر کار/تغییر را در `PROJECT_HANDOFF.txt` ثبت کنید** (تاریخ، مدل/عامل، علت، فایل‌ها، نتیجه‌ی واقعی). چیزی که اجرا نشده «موفق» ثبت نشود.

## ۲. شکست‌های واقعی گذشته، علت و پیشگیری

| Run | مرحله‌ی شکست | علت واقعی | پیشگیری |
|---|---|---|---|
| 40083693731 | Build | `gradlew`/`gradle-wrapper.jar` در ریپو نبود و runner با Gradle نصب‌شده‌ی خودش (9.8.1، نه 9.3.1 پین‌شده) wrapper می‌ساخت؛ خطای KSP `ApplicationManager.getApplication() is null` دیده شد. (ارتباط قطعی KSP با نسخه‌ی Gradle اثبات نشده؛ پس از پین‌شدن 9.3.1 دیگر ظاهر نشد) | `gradlew`، `gradlew.bat` و `gradle/wrapper/gradle-wrapper.jar` باید commit باشند؛ ورک‌فلو اگر نباشند صریحاً fail می‌شود |
| 40084497108 | Build (`validateSigningRelease`) | `KEYSTORE_FILE=VaamYar.jks` مسیر نسبی بود ولی `app/build.gradle.kts` نسبت به پوشه‌ی `app/` حل می‌کند | همیشه مسیر **مطلق**: `${{ github.workspace }}/VaamYar.jks` |
| 40226910444 | Build (`processReleaseMainManifest`) | `AndroidManifest.xml` خراب بود (`SAXParseException`، خط ۱۳: تگ `application` ناقص) | بعد از هر ویرایش manifest/XML: `python3 -c "import xml.dom.minidom,sys; xml.dom.minidom.parse('app/src/main/AndroidManifest.xml')"` |
| 40227292528 | Build (`compileReleaseKotlin`) | خطای کامپایل Kotlin (در Annotation فقط «Compilation error» ثبت شد و جزئیات `e:` ذخیره نشد) | قبل از merge، ورک‌فلوی Unit Tests روی شاخه سبز باشد؛ خروجی `e:` اکنون به Annotation تبدیل می‌شود |
| 40225404421 | Commit and Push Version Bump (exit 1) | طبق توضیح P4-7 در ورک‌فلو: وسط بیلد کامیت دیگری روی `main` نشسته و push رد شده (non-fast-forward) | `git pull --rebase --autostash origin main` قبل از push (در ورک‌فلو اعمال شده) |
| 40374838798 | Commit and Push Version Bump (exit 128) | `gradlew` با mode `100644` در git بود؛ `chmod +x` در CI فایل را «تغییر‌یافته» کرد و `git pull --rebase` با تغییر commitنشده خطا داد | `git update-index --chmod=+x gradlew` (انجام شده) + `--autostash` |
| 40403171266 | Run Unit Tests (`compileDebugKotlin`) | هنگام تغییر نام پکیج (`com.example` → `com.aistudio.vamyar`) فایل `LoanRepository.kt` بریده شد (۶۴ خط انتهایی از دست رفت، `Syntax error: Expecting '}'` و چند `Unresolved reference`) | قانون‌های ۲، ۳ و ۴ بالا |

هشدارهای زیر **علت شکست نیستند** و نباید وقت صرفشان شود: `Failed to save cache entry … services aren't available` (قطعی سرویس کش GitHub)،
هشدارهای deprecation اکشن‌ها، نبود `mapping.txt` (چون `isMinifyEnabled=false`)، اعلان «Gradle is out of date».

## ۳. چطور خطای واقعی را ببینیم (وقتی لاگ کامل 403 می‌دهد)

لاگ کامل job معمولاً از محیط‌های AI قابل دریافت نیست (`gh run view --log-failed` → 403). به‌جای آن:

```bash
# وضعیت مرحله‌ها
gh run view <RUN_ID> --json jobs --jq '.jobs[].steps[]|"\(.conclusion)\t\(.name)"'
# خطای واقعی از Annotation ها
JOB=$(gh api repos/Arsalani2/vaamyar/actions/runs/<RUN_ID>/jobs --jq '.jobs[0].id')
gh api repos/Arsalani2/vaamyar/check-runs/$JOB/annotations --jq '.[]|select(.annotation_level=="failure")|.message'
```

- ورک‌فلوها خطاهای `e: ...` کامپایل Kotlin و تست‌های fail‌شده را به Annotation تبدیل می‌کنند (فایل لاگ هم به‌صورت artifact آپلود می‌شود).
- اگر مرحله‌ای بدون هیچ Annotation مفیدی fail شد، اول **تشخیص** را درست کنید (tee لاگ + `::error::`) و بعد حدس نزنید.
- برای تست بدون ریلیز: ورک‌فلوی `unit-tests.yml` را روی شاخه اجرا کنید (push به `fix/**` یا `gh workflow run unit-tests.yml --ref <branch>`).
  **هرگز `android-release.yml` را روی شاخه‌ی غیر `main` با `workflow_dispatch` اجرا نکنید** (مرحله‌ی bump به `main` push می‌کند).

## ۴. چک‌لیست قبل از merge به `main`

- [ ] فایل‌های تغییر‌کرده کامل‌اند (تعداد خطوط، آکولاد متوازن، `tail` سالم).
- [ ] اگر manifest/XML/Gradle/`libs.versions.toml` عوض شده، syntax آن جدا بررسی شده است.
- [ ] ورک‌فلوی **Unit Tests (no release)** روی همین شاخه سبز است (نه فقط «فکر می‌کنم درست است»).
- [ ] پیام کامیت نهایی برای ریلیز `[skip ci]` ندارد؛ فقط کامیت‌های مستندات‌محض می‌توانند داشته باشند.
- [ ] قبل از push: `git fetch origin main` و در صورت وجود کامیت جدید، rebase.
- [ ] `PROJECT_HANDOFF.txt` به‌روز شده و هیچ secret (keystore/password/API key) در هیچ فایلی نیست.

## ۵. الگوی پیشنهادی برای ثبت در `PROJECT_HANDOFF.txt`

```
[تاریخ و ساعت UTC — مدل/عامل]
اقدام / علت / فایل‌های درگیر
یافته‌ها (مشاهده‌ی مستقیم، نه حدس؛ اگر استنباط است صریحاً «استنباط» بنویسید)
نتیجه‌ی راستی‌آزمایی (شماره‌ی run و وضعیت واقعی)
گام بعدی
```

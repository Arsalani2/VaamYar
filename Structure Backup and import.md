# ساختار جامع و تخصصی پشتیبان‌گیری و بازیابی اطلاعات اپلیکیشن وام‌یار (VaamYar)
## Data Architecture, Backup/Restore Schema & Forward-Compatible Migration Guide

---

## ۱. مقدمه و هدف
این سند مرجع رسمی و فنی ساختار داده‌ها، نحوه تولید، اعتبارسنجی یکپارچگی (Data Integrity) و بازیابی (Restore) فایل‌های پشتیبان در اپلیکیشن اندروید **«وام‌یار» (VaamYar)** است. 

تمامی توسعه‌دهندگان، مهندسان نرم‌افزار و مدل‌های هوش مصنوعی که در آینده بر روی این پروژه کار می‌کنند، **موظف به تبعیت از استانداردهای این سند هستند** تا اطمینان حاصل شود که:
1. هیچ نسخه‌ای از اپلیکیشن در آینده فایل‌های پشتیبان قبلی را خراب نمی‌کند (**Backward Compatibility**).
2. هرگونه تغییر در امکانات، فیلدهای جدول‌ها یا معماری، به صورت خودکار فایل‌های قدیمی را ترمیم و تطبیق می‌دهد (**Self-Healing & Auto-Migration**).
3. دستکاری دستی فایل، خطاهای انتقال داده و فایل‌های ناقص پیش از اعمال به پایگاه‌داده شناسایی و مسدود می‌شوند (**Cryptographic SHA-256 Integrity Verification**).

---

## ۲. فرمت و ساختار فایل پشتیبان (Standard JSON Container)

فایل پشتیبان به صورت یک شیء JSON با ساختار دو لایه‌ای ذخیره می‌شود:
- **لایه اول (Envelope / Metadata):** متادیتای سیستم، نسخه اسکیما و هش اعتبارسنجی.
- **لایه دوم (Payload):** داده‌های واقعی دیتابیس و تنظیمات.

### ۲.۱. نمونه کامل ساختار JSON (Schema Version 1)

```json
{
  "app": "VaamYar",
  "appIdentifier": "com.aistudio.vamyar",
  "schemaVersion": 1,
  "appVersion": "1.0.3",
  "exportTimestamp": 1729854000000,
  "exportDateShamsi": "۳ آبان ۱۴۰۵",
  "checksum": "a3f5b7c891e44293f019058b874416db92ef88b14e64f7b605809f67a21f7c32",
  "payload": {
    "users": [
      {
        "id": 1,
        "name": "علی رضایی",
        "phoneNumber": "09123456789",
        "isPrimary": true,
        "avatarColorHex": 1100417,
        "notes": "کاربر اصلی و مبنا"
      },
      {
        "id": 2,
        "name": "محمد",
        "phoneNumber": "09129876543",
        "isPrimary": false,
        "avatarColorHex": 16086850,
        "notes": "شریک وام مسکن"
      }
    ],
    "loans": [
      {
        "id": 101,
        "title": "وام مسکن ملی",
        "dueDay": 5,
        "monthlyAmount": 800000,
        "totalMonths": 12,
        "paidMonths": 4,
        "startYear": 1404,
        "startMonth": 8,
        "endYear": 1405,
        "endMonth": 7,
        "category": "بانکی",
        "colorHex": 1738997,
        "notes": "شعبه مرکزی",
        "myShareAmount": 0,
        "hasPartners": true,
        "isArchived": false,
        "isCurrentMonthPaid": false,
        "iconName": "home",
        "iconShape": "ROUNDED_SQUARE",
        "customIconPath": null
      }
    ],
    "shares": [
      {
        "id": 201,
        "loanId": 101,
        "loanTitle": "وام مسکن ملی",
        "personName": "محمد",
        "shareAmount": 800000,
        "dueDay": 5,
        "isPaidForCurrentMonth": false,
        "lastPaidMonth": "مهر ۱۴۰۵",
        "shareDescription": "سهم کامل قسط ماهانه"
      }
    ],
    "records": [
      {
        "id": 301,
        "loanId": 101,
        "loanTitle": "وام مسکن ملی",
        "installmentNumber": 1,
        "amountPaid": 800000,
        "paymentDateShamsi": "۱۴۰۴/۰۸/۰۵",
        "timestamp": 1729854000000,
        "note": "پرداخت قسط اول توسط شریک"
      }
    ],
    "preferences": {
      "themeMode": "DARK",
      "themePreset": "EMERALD_GOLD",
      "fontStyle": "SYSTEM",
      "usePersianDigits": true,
      "compactThousands": false,
      "soundEnabled": true,
      "customYear": 1405,
      "customMonth": 8,
      "customDay": 3,
      "isCustomDateEnabled": false
    }
  }
}
```

---

## ۳. تعریف دقیق فیلدها و موجودیت‌ها

### ۳.۱. جدول متادیتا (Envelope)
| نام فیلد | نوع داده | الزامی؟ | توضیحات |
| :--- | :--- | :--- | :--- |
| `app` | String | بله | باید دقیقاً `"VaamYar"` باشد. |
| `appIdentifier` | String | بله | شناسه یکتای بسته نرم‌افزاری (`com.aistudio.vamyar`). |
| `schemaVersion` | Integer | بله | نسخه مدل داده فایل پشتیبان (برای کنترل ارتقای آینده). |
| `appVersion` | String | بله | نسخه فعلی کلاینت (مطابق `version.properties`). |
| `exportTimestamp` | Long | بله | زمان یونیکس بر حسب میلی‌ثانیه. |
| `exportDateShamsi`| String | اختیاری| تاریخ شمسی خوانا در زمان خروجی گرفتن. |
| `checksum` | String | بله | هش SHA-256 حاصل از رشته خام شیء `payload`. |

---

### ۳.۲. جدول کاربران (`payload.users`)
- `id` (Long): شناسه داخلی (در بازیابی بازتولید می‌شود).
- `name` (String): نام شخص (یکتا، بدون پرانتزهای زائد نظیر `(من)`).
- `phoneNumber` (String): شماره تماس شخص برای یادآوری پیامکی.
- `isPrimary` (Boolean): آیا کاربر مبنا و صاحب اپلیکیشن است؟ (تنها یک کاربر `isPrimary: true` دارد).
- `avatarColorHex` (Long): رنگ تم آواتار دایره‌ای.
- `notes` (String): توضیحات اختیاری.

---

### ۳.۳. جدول وام‌ها (`payload.loans`)
- `id` (Long): شناسه وام.
- `title` (String): عنوان وام یا تسهیلات.
- `dueDay` (Int): روز سررسید در ماه (۱ تا ۳۱).
- `monthlyAmount` (Long): مبلغ کل قسط ماهانه وام (تومان).
- `totalMonths` (Int): تعداد کل ماه‌های اقساط.
- `paidMonths` (Int): تعداد اقساط پرداخت شده تاکنون.
- `startYear` / `startMonth` (Int): سال و ماه شروع پرداخت قسط (تقویم خورشیدی).
- `endYear` / `endMonth` (Int): سال و ماه محاسبه‌شده پایان اقساط.
- `myShareAmount` (Long): سهم اختصاصی کاربر مبنا از این قسط (اگر شریک ۱۰۰٪ قسط را بدهد، این مقدار **صفر** است).
- `hasPartners` (Boolean): پرچم نشان‌دهنده وجود شریک.
- `iconName` (String): شناسه آیکون برداری متریال (مثلاً `account_balance`, `home`, `directions_car`, `payments`).
- `iconShape` (String): فرم نمایش آیکون: `ROUNDED_SQUARE` (مربع دور گرد مینیمال) یا `CIRCLE` (دایره مینیمال).
- `customIconPath` (String?): مسیر تصویر اختصاصی از گالری (در صورت حذف فایل از حافظه، به آیکون پیش‌فرض برمی‌گردد).

---

### ۳.۴. جدول سهم شرکا (`payload.shares`)
- `id` (Long): شناسه سهم.
- `loanId` (Long): ارجاع به وام والد.
- `loanTitle` (String): عنوان وام برای نمایش سریع.
- `personName` (String): نام شریک مورد نظر (مطابق یکی از کاربران).
- `shareAmount` (Long): مبلغ سهم شریک از قسط ماهانه (تومان).
- `dueDay` (Int): روز سررسید پرداخت سهم.
- `isPaidForCurrentMonth` (Boolean): آیا شریک سهم ماه جاری را واریز کرده است؟
- `lastPaidMonth` (String): آخرین ماهی که تسویه کرده است.
- `shareDescription` (String): یادداشت کمکی (مثلاً شماره کارت یا شماره پیگیری).

---

### ۳.۵. جدول سوابق پرداخت (`payload.records`)
- `id` (Long): شناسه تراکنش.
- `loanId` (Long): شناسه وام.
- `installmentNumber` (Int): شماره قسط پرداخت شده (مثلاً قسط شماره ۴).
- `amountPaid` (Long): مبلغ پرداخت شده در این نوبت.
- `paymentDateShamsi` (String): تاریخ پرداخت شمسی (`۱۴۰۴/۰۸/۰۵`).
- `timestamp` (Long): زمان بر حسب میلی‌ثانیه.
- `note` (String): توضیحات همراه پرداخت.

---

## ۴. مکانیسم امنیت و اعتبارسنجی یکپارچگی (SHA-256 Checksum)

### ۴.۱. الگوریتم تولید هش در زمان Export
1. ابتدا کلیه جداول در شیء JSON به نام `payload` قرار می‌گیرند.
2. رشته متنی فشرده یا استاندارد `payload.toString()` به بایت‌های UTF-8 تبدیل می‌شود.
3. تابع رمزنگاری `MessageDigest.getInstance("SHA-256")` روی این بایت‌ها اجرا می‌شود.
4. خروجی به صورت رشته هگزادسیمال ۶۴ کاراکتری در کلید `checksum` در سطح ریشه قرار می‌گیرد:
```kotlin
val payloadString = payload.toString()
val checksum = calculateSha256(payloadString)
root.put("checksum", checksum)
```

### ۴.۲. الگوریتم صحت‌سنجی در زمان Import
1. پیش از هرگونه دستکاری در پایگاه‌داده، رشته داخل `payload` استخراج و مجدداً هش‌گیری می‌شود.
2. اگر هش محاسبه‌شده با مقدار `root.getString("checksum")` مغایرت داشته باشد:
   - عملیات بازیابی متوقف می‌شود (`SecurityException`).
   - خطای مشخص «فایل پشتیبان دستکاری شده یا ناقص است» به کاربر نمایش داده می‌شود.
   - پایگاه داده دست‌نخورده باقی می‌ماند.

---

## ۵. قوانین حیاتی سازگاری رو به جلو و عقب (Forward & Backward Compatibility)

هر زمان در آینده فیلدی اضافه شد یا منطقی تغییر کرد، این ۵ قانون باید رعایت شود:

### قانون ۱: فیلدهای جدید اختیاری با مقدار پیش‌فرض عاقلانه (Default Value Strategy)
اگر در نسخه ۲ یا ۳ فیلد جدیدی به وام‌ها اضافه شد (مثلاً `interestRate: Double` یا `bankBranch: String`):
- خواندن فایل باید با `optDouble("interestRate", 0.0)` و `optString("bankBranch", "")` انجام شود تا فایل‌های قدیمی نسخه ۱ بدون باگ یا کرش خوانده شوند.

### قانون ۲: تصحیح خودکار و بازسازی سهم کاربر مبنا (Self-Repairing Partner Shares)
اگر فایلی ایمپورت شد که در آن شریک تمام مبلغ وام را می‌دهد (`shareAmount == monthlyAmount`) اما در نسخه قدیمی مقدار `myShareAmount` روی کل وام باقی مانده بود، یا اصلاً کلید `myShareAmount` در فایل نبود (بکاپ‌های بسیار قدیمی):
- متد `parseAndValidateBackup` سهم کاربر مبنا را به‌صورت خودکار و سه‌مسیره بازسازی می‌کند (فیکس bug.md/P2-14، کامیت `7235d33`):
```kotlin
val hasPartnersInJson = if (l.has("hasPartners")) l.getBoolean("hasPartners")
    else sharesList.filter { it.loanId == loanId }.isNotEmpty()
val migratedMyShare = when {
    !hasPartnersInJson -> {                                   // وام بدون شریک → ۱۰۰٪ قسط ماهانه
        val raw = l.optLong("myShareAmount", 0L)
        if (raw == 0L) monthlyAmount else raw
    }
    l.has("myShareAmount") -> l.optLong("myShareAmount", 0L)  // مقدار صریح فایل عیناً حفظ می‌شود
    else -> (monthlyAmount - sharesList.filter { it.loanId == loanId }
        .sumOf { it.shareAmount }).coerceAtLeast(0L)          // بازسازی: قسط منهای سهم شرکا
}
```
- یعنی: کلیدِ موجود → همان مقدار دقیق فایل؛ کلیدِ غایب + شریک → `monthlyAmount − Σ سهم شرکا` (clamp در ≥۰ تا سهم‌های خراب سهم من را منفی نکنند)؛ کلیدِ غایب + بی‌شریک → کل قسط ماهانه. پرچم `hasPartners` غایب هم از خودِ سهم‌های همان وام استنتاج می‌شود.
- این کار هم مانع از دوباره بروز کردن باگ «محاسبه ۸۰۰ در دو حساب» می‌شود و هم وام شراکتیِ قدیمیِ فاقد این فیلد دیگر با سهمِ صفر بازیابی نمی‌شود — صفرِ «نبودِ کلید» هرگز «سهم من صفر است» تلقی نمی‌شود.

### قانون ۳: تضمین وجود کاربر مبنا (Primary User Guarantee)
اگر فایل پشتیبان به هر دلیلی کاربری به عنوان `isPrimary: true` نداشت (یا جدول کاربران خالی بود):
- سیستم به صورت خودکار اولین کاربر را مبنا قرار می‌دهد یا یک کاربر پیش‌فرض با نام سیستم ایجاد می‌کند تا اپلیکیشن هرگز وارد حالت خطای بدون کاربر نشود.

### قانون ۴: تطبیق شناسه‌ها (ID Remapping & Collision Prevention)
در زمان بازیابی در پایگاه‌داده:
- شناسه‌ها به صورت پیوسته ثبت می‌شوند تا تداخل کلیدهای خارجی با دیتابیس فعلی رخ ندهد.
- ابتدا جداول فعلی پاکسازی امن می‌شوند (`clearAllDataAndReset`) و سپس داده‌های پشتیبان با اتوژنریت ترتیبی درج می‌گردند.
- تطبیق نام‌ها از این پس با استانداردسازی `PersianNameNormalizer` انجام می‌شود (فیکس bug.md/P3-1): نام کاربران و شرکا هنگام بازگردانی یکسان‌سازی می‌شوند (ي→ی، ك→ک، ة→ه، فاصله‌ها/نیم‌فاصله) تا بکاپِ دارای حروف عربی بعد از restore هم‌نامِ تکراری یا سهمِ یتیم نسازد. هویت نام‌پایه باقی می‌ماند (بدون userId، طبق تصمیم A4 در bug.md).

### قانون ۵: کنترل نسخه اسکیما (Schema Versioning Barrier)
- اگر `fileSchemaVersion <= CURRENT_SCHEMA_VERSION`: فایل پشتیبان معتبر است و با توابع Migration لود می‌شود.
- اگر `fileSchemaVersion > CURRENT_SCHEMA_VERSION`: کاربر با این پیام راهنمایی می‌شود:
  *«این فایل با نسخه جدیدتری از وام‌یار ساخته شده است. لطفاً برنامه را از بازار/مایکت یا گیت‌هاب به‌روزرسانی کنید.»*

---

## ۶. نحوه استفاده در کد و ViewModel

```kotlin
// خروجی گرفتن در قالب JSON
val backupJsonString = BackupHelper.createBackupJson(
    users = usersList,
    loans = loansList,
    shares = sharesList,
    records = recordsList,
    preferences = currentPreferences,
    appVersion = BuildConfig.VERSION_NAME,
    shamsiDateFormatted = currentShamsiDate.formatted()
)

// بازیابی و اعتبارسنجی
val restoreResult = BackupHelper.parseAndValidateBackup(jsonString)
if (restoreResult.isSuccess) {
    val data = restoreResult.getOrThrow()
    repository.restoreDatabaseSnapshot(data.users, data.loans, data.shares, data.records)
    applyPreferences(data.preferences)
} else {
    showError(restoreResult.exceptionOrNull()?.message)
}
```

---

## ۷. جمع‌بندی
با رعایت این ساختار، فایل‌های پشتیبان وام‌یار برای همیشه پایدار، قابل اتکا و مقاوم در برابر تغییرات نسخه‌های آتی خواهند بود. هر مهندس نرم‌افزار یا مدل هوش مصنوعی در ادامه توسعه، باید تغییرات مدل دیتابیس را ابتدا در بخش قوانین ارتقای این سند ثبت نماید.

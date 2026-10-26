# 🐛 گزارش باگ‌های وام‌یار — bug.md (نسخهٔ ۳)

> **بازبینی ۴ — ۲۰۲۶-۱۰-۲۶** — بر پایهٔ نوک main (کامیت `87d8796`)، version.properties: 1.0.5 (کد ۶)، آخرین ریلیز: v1.0.5.
> این فایل «TODO زنده» است: باگ‌های باز با شاهد دقیق، متد فیکس گام‌به‌گام، معیار پذیرش و فایل تست. شرح کامل باگ‌های رفع‌شده در Update.md؛ این‌جا فقط فهرست فشرده (انتهای فایل).

## 📊 وضعیت کلی (پس از بازبینی ۴)
- ✅ **همهٔ P1 (۹ مورد)، P2 (۱۴ مورد)، P3 بازمانده و NB-1 تا NB-13 رفع شده‌اند** — شماره کامیت‌ها در فهرست انتهای فایل. ریلیز 1.0.5 منتشر و سبز است.
- ❌ **باز از قبل:** P4-4، P4-5، P4-6.
- 🆕 **جدید بازبینی ۴: NB-14 تا NB-30** — ۲ مورد P2، ۷ مورد P3، ۸ مورد P4.
- 📈 ارزیابی: باگ‌های جدید عمدتاً کیفیت/UX/عملیات‌های خطرناک‌اند (اعتبارسنجی، فایل آیکون، I/O، تأییدیه‌ها)؛ هیچ کرش یا از‌دست‌رفتن داده در مسیر اصلی دیده نشد. منطق شمسی، تراکنش‌ها و تست‌های موجود سالم‌اند.

---

## ❌ موارد باز از بازبینی‌های قبل

- [ ] **P4-4 | package naming** (namespace = com.example ولی applicationId = com.aistudio.vamyar.zqnmxk).
  - **متد فیکس:** namespace و applicationId هر دو به **com.aistudio.vamyar** (حذف پسوند تصادفی zqnmxk)؛ اصلاح سطرهای package/import همهٔ فایل‌های سورس، Manifest و ارجاع‌ها. ⚠️ هر تغییر کامل در Update.md مستند شود (شرط A5). ⚠️ هم‌خوان با NB-9: چک شناسهٔ بکاپ باید مقدار قدیمی را هم بپذیرد تا بکاپ‌های قبل از rename بی‌اعتبار نشوند.
  - **معیار پذیرش:** بیلد/تست سبز؛ بکاپِ قبل از rename در نسخهٔ جدید قابل restore باشد.

- [ ] **P4-5 | وابستگی‌های آنلاین در اپ آفلاین** — firebase.ai/firebase-appcheck/google-services/secrets-gradle/retrofit/okhttp/moshi در اپ «کاملاً آفلاین» حجم و مجوز می‌آورند.
  - **متد فیکس:** حذف پلاگین‌ها و وابستگی‌ها از build.gradle.kts و settings.gradle.kts؛ جستجوی کل ریپو برای ارجاع پیش از حذف (تجربهٔ P3-7/P3-9: همه حذف‌ها ارجاع‌صفر بودند)؛ پاک‌سازی بقایای قالب AI Studio: `.env.example`، دایرکتوری `.aistudio`، `metadata.json`، `public/`.
  - **معیار پذیرش:** بیلد سبز؛ APK بدون این وابستگی‌ها؛ grep ارجاع صفر.

- [ ] **P4-6 | فایل‌های غول‌پیکر** — SettingsScreen (~46KB)، AddEditLoanDialog (~47KB)، LoanItemCard (~38KB).
  - **متد فیکس:** افزودن ktlint یا detekt به CI با آستانهٔ هشدار طول فایل؛ تفکیک تدریجی هنگام لمسِ هر فایل (نه refactor یکجا).

---

## 🆕 باگ‌های جدید بازبینی ۴

### 🟠 سطح P2

- [ ] **NB-14 | «پاکسازی تمام وام‌ها» رکوردهای تاریخچهٔ پرداخت را پاک نمی‌کند** — `clearAllLoansAndReset` در LoanRepository فقط وام‌ها و سهم‌ها را حذف می‌کند؛ رکوردهای `payment_records` یتیم می‌مانند و در هر بکاپ بعدی هم می‌آیند.
  - **شاهد:** `LoanRepository.clearAllLoansAndReset` (فقط loanDao/shareDao؛ مقایسه با `clearAllDataAndReset` که کامل است).
  - **متد فیکس:** ۱) اگر متد پاک‌سازی در PaymentRecordDao نیست، `clearAllRecords(): suspend fun` به آن اضافه شود. ۲) داخل همان `database.withTransaction`، **قبل از حذف وام‌ها** (رکوردها وابسته به وام‌اند) فراخوانی شود.
  - **معیار پذیرش:** پس از «پاکسازی تمام وام‌ها»، جدول payment_records خالی و بکاپِ بعدی آرایهٔ records خالی داشته باشد.
  - **تست:** `LoanRepositoryIntegrityTest` → تابع `clearAllLoansLeavesNoOrphanRecords`.

- [ ] **NB-15 | دکمهٔ «اشتراک‌گذاری» بکاپ فایل نمی‌فرستد** — ACTION_SEND با `EXTRA_TEXT=json` و type application/json → متن خام در پیام‌رسان‌ها می‌چسبد، نه فایل.
  - **شاهد:** SettingsScreen بخش اشتراک بکاپ.
  - **متد فیکس:** ۱) نوشتن JSON در `cacheDir/backup/vaamyar-backup-<date>.json`. ۲) تعریف FileProvider در AndroidManifest (authorities=`${applicationId}.fileprovider`) + `res/xml/file_paths.xml` با مسیر cache-path. ۳) intent با `EXTRA_STREAM` (Uri از FileProvider.getUriForFile) + `FLAG_GRANT_READ_URI_PERMISSION`.
  - **معیار پذیرش:** منوی share فایل JSON واقعی را باز کند و فایل در تلگرام/واتساپ قابل ذخیره باشد.
  - **تست:** دستی (روتین share intent).

### 🟡 سطح P3

- [ ] **NB-16 | وضعیت «سررسید سپری‌شده» هرگز نمایش داده نمی‌شود** — `daysUntilDue` (فیکس P2-6) همیشه ≥ 0 برمی‌گرداند؛ شاخهٔ «موعد این ماه سپری شده» در `smartFinancialTips` مرده است و چیپ LoanItemCard برای وام عقب‌افتاده «X روز مانده» (تا سررسید ماه بعد!) نشان می‌دهد.
  - **شاهد:** `ShamsiDateHelper.daysUntilDue` + `MainViewModel.smartFinancialTips` (شاخهٔ else مرده) + چیپ `LoanItemCard`.
  - **متد فیکس:** ۱) تابع متمایز `daysUntilDueThisMonth(y,m,d): Int` که برای سررسیدِ گذشتهٔ همین ماه مقدار **منفی** برگرداند (بدون لمس رفتار daysUntilDue که برای شمارش تا سررسید بعدی درست است). ۲) در smartFinancialTips شاخهٔ overdue با تابع جدید فعال شود. ۳) در چیپ کارت وام: مقدار منفی → متن «X روز عقب» با رنگ error.
  - **معیار پذیرش:** وامِ با dueDay گذشته و قسط پرداخت‌نشده، در نکات هوشمند «سررسید این ماه سپری شده» و چیپ قرمز/عقب‌افتاده نشان دهد.
  - **تست:** `PureLogicUnitTest` → `daysUntilDueThisMonth`.

- [ ] **NB-17 | اعلان سررسید بدون clamp طول ماه** — تطبیق `dueDay == امروز/فردا` در InstallmentAlarmReceiver خام است؛ وام با dueDay=31 در ماه ۳۰روزه (یا ۳۰/۳۱ در اسفند ۲۹روزه) هیچ‌وقت اعلان نمی‌گیرد — ناسازگار با clamp درون daysUntilDue.
  - **شاهد:** `InstallmentAlarmReceiver` (فیلترهای dueToday/dueTomorrow).
  - **متد فیکس:** ۱) تابع مشترک `ShamsiDateHelper.effectiveDueDay(year, month, dueDay) = dueDay.coerceIn(1, getDaysInShamsiMonth(year, month))`. ۲) در Receiver هر دو مقایسه (امروز/فردا) روی مقدار clampشده انجام شود؛ برای «فردا» اگر فردا روز اولِ ماه بعد است، سررسیدِ clampشدهٔ روز آخر ماه جاری ملاک باشد.
  - **معیار پذیرش:** وام dueDay=31 در شهریور (۳۰روزه) در روز ۳۰ اعلان بگیرد؛ اسفند ۲۹روزه هم مشابه.
  - **تست:** `PureLogicUnitTest` → `effectiveDueDay`.

- [ ] **NB-18 | آیکون سفارشی وام در بکاپ فقط path است، خود فایل نه** — createBackupJson مسیر absolute حافظهٔ داخلی را ذخیره می‌کند؛ restore روی دستگاه/نصب دیگر فایل را ندارد → آیکون بی‌صدا به vector برمی‌گردد + نشت مسیر فایلسیستم در JSON.
  - **شاهد:** `BackupHelper.createBackupJson` (فیلد مسیر آیکون) + `LoanIconHelper`.
  - **متد فیکس:** در JSON آرایهٔ `icons: [{loanId, fileName, dataBase64}]` اضافه شود؛ هنگام export فایل‌های آیکون خوانده و embed شوند، هنگام restore بازنویسی شوند. حداقلِ سازگار (اگر embed کامل نشد): در export، وقتی فایل روی دیسک نبود فیلد path حذف شود و restore صریحاً به fallback برداری بیفتد.
  - **معیار پذیرش:** بکاپ گرفته و روی نصب تازه restore شود؛ آیکون سفارشی برگردد و هیچ مسیر absolute در JSON نباشد.
  - **تست:** `BackupValidationTest` برای ساختار icons + دستی.

- [ ] **NB-19 | بازنشانی‌ها فایل‌های آیکون سفارشیِ یتیم را نگه می‌دارند** — clearAllLoansAndReset و restoreDatabaseSnapshot فایل‌های آیکون را پاک نمی‌کنند (NB-6 فقط deleteLoan را فیکس کرد).
  - **شاهد:** همان دو تابع در LoanRepository؛ الگوی درست: `deleteLoan` (NB-6).
  - **متد فیکس:** پیش از تراکنش، مسیر آیکون‌ها را جمع کن؛ پس از commit موفق فایل‌ها را حذف کن (عیناً الگوی NB-6). در restore، پیش از جایگزینی، آیکون‌های قدیمی حذف شوند.
  - **معیار پذیرش:** پس از هر دو عملیات، دایرکتوری آیکون‌های سفارشی خالی باشد.
  - **تست:** دستی/ابزار.

- [ ] **NB-20 | AddEditUserDialog نام تکراری می‌پذیرد** — با مدل هویتِ نام‌پایه (PersianNameNormalizer) دو «علی» سهم‌ها را قاطی می‌کنند.
  - **شاهد:** `AddEditUserDialog` (فقط blank چک می‌شود).
  - **متد فیکس:** در save، نام جدید با همهٔ کاربران موجود (به‌جز خود کاربر در حالت ویرایش) با `PersianNameNormalizer.namesMatch` مقایسه شود؛ در صورت تکرار error زیر فیلد نمایش یابد و ذخیره رد شود.
  - **معیار پذیرش:** «علی»، «علي» (ي عربی) و « علی » به‌عنوان تکراری رد شوند.
  - **تست:** `PureLogicUnitTest` (جدول namesMatch موجود).

- [ ] **NB-21 | اعتبارسنجی نام شریک با equals خام** — در AddEditLoanDialog هر دو چکِ «کاربر مبنا» و «شریک تکراری» `equals(ignoreCase=true)` دارند → نقض اینوارینت #۳؛ «علی» با ي عربی رد نمی‌شود.
  - **شاهد:** `AddEditLoanDialog` (دو شرط equals).
  - **متد فیکس:** جایگزینی هر دو شرط با `PersianNameNormalizer.namesMatch`.
  - **معیار پذیرش:** ثبت شریک هم‌نام با مبنا یا هم‌نویسهٔ حرف‌های عربی (ي/ک) رد شود.
  - **تست:** `PureLogicUnitTest`.

- [ ] **NB-22 | حذف وام بدون دیالوگ تأیید** — دکمهٔ delete در LoanItemCard مستقیم `deleteLoan` + حذف فایل آیکون؛ بدون undo و بدون بازگشت.
  - **شاهد:** `LoanItemCard.onDelete` → `MainViewModel.deleteLoan`.
  - **متد فیکس:** AlertDialog تأیید («حذف وام X؟ سابقهٔ پرداخت‌ها و آیکون هم پاک می‌شود») با الگوی دیالوگ‌های موجود SettingsScreen؛ فراخوانی onDelete فقط پس از تأیید.
  - **معیار پذیرش:** حذف فقط با تأیید صریح؛ cancel هیچ تغییری ایجاد نکند.
  - **تست:** دستی.

### 🔵 سطح P4

- [ ] **NB-23 | کد مرده** — DebtDonutChart و MonthlyInstallmentsBarChart در CustomCharts.kt بی‌استفاده‌اند؛ `clearAllDataAndStartFresh` و `resetToDuplicates` در MainViewModel دوباره همان کار را می‌کنند؛ `getCurrentShamsiDate` الیاس مرده؛ متغیر `shortYear` در InstallmentNodesView؛ if/else با بدنهٔ یکسان در `LoanRepository.saveUser`.
  - **متد فیکس:** حذف توابع/کامپوننت‌های بی‌ارجاع در یک کامیت (پیش از حذف، grep ارجاع صفر تأیید شود)؛ ساده‌سازی saveUser.
  - **معیار پذیرش:** بیلد و تست سبز؛ grep ارجاع صفر.

- [ ] **NB-24 | reminderHour/reminderMinute قابل تنظیم نیستند** — هیچ UI‌ای این‌ها را نمی‌نویسد؛ ساعت اعلان همیشه ۱۰:۰۰ است.
  - **شاهد:** `AppPreferences.reminderHour/reminderMinute` + SettingsScreen.
  - **متد فیکس:** گام ۱ (کمینه): حذف ابهام یا نگه‌داشتن ثابت با کامنت. گام ۲ (کامل): TimePicker در تنظیمات + ذخیره در همان DataStore + مصرف در زمان‌بندی InstallmentAlarmReceiver.
  - **معیار پذیرش (گام ۲):** ساعت تنظیم‌شده در اعلان روز بعد اعمال شود.

- [ ] **NB-25 | دادهٔ seed با calculateEndDate ناسازگار** — loan1 (شروع 1403/4، ۲۴ ماه → پایان باید 1405/03، seed: 1405/04) و loan3 (شروع 1403/8، ۱۲ ماه → 1404/07، seed: 1404/08) → payoffCountdownText ماهِ غلط نشان می‌دهد.
  - **شاهد:** دادهٔ seed در LoanRepository + `ShamsiDateHelper.calculateEndDate`.
  - **متد فیکس:** محاسبهٔ endYear/endMonth در seed با calculateEndDate (به‌جای ثابت‌های دستی).
  - **معیار پذیرش:** در نصب تازه، «پایان وام» seed با خروجی calculateEndDate یکی باشد.
  - **تست:** `PureLogicUnitTest` در صورت استخراج seed به تابع خالص.

- [ ] **NB-26 | hasInvalidSeparator در PriceInputField چسبنده است** — خطای جداکننده فقط با خالی‌شدن کامل فیلد ریست می‌شود.
  - **شاهد:** کامپوننت `PriceInputField`.
  - **متد فیکس:** در onChange دوباره ارزیابی شود؛ اگر جداکننده معتبر شد، خطا پاک شود.
  - **معیار پذیرش:** تایپ «100000..» و اصلاح آن، بدون خالی‌کردن فیلد، error را بردارد.

- [ ] **NB-27 | نام‌های شخصی در کامنت‌ها** — در AddEditUserDialog کامنت‌هایی مثل «// Gold (Arsalan)» و «// Ruby (Zahra)».
  - **شاهد:** `AddEditUserDialog` کامنت‌ها.
  - **متد فیکس:** جایگزینی با برچسب عمومی («سهم اول/دوم») یا حذف سطرهای بی‌استفاده.
  - **معیار پذیرش:** grep نام‌های شخصی در سورس صفر.

- [ ] **NB-28 | هشدارهای Node 20 deprecated در ورک‌فلو** — checkout@v4، setup-java@v4، upload-artifact@v4، setup-gradle@v3، gh-release@v2 (پیوست به P4-1).
  - **شاهد:** `android-release.yml`.
  - **متد فیکس:** ارتقای همهٔ actionها به آخرین major پشتیبانی‌شده در اولین لمسِ بعدی workflow + ثبت در Update.md.
  - **معیار پذیرش:** اجرای بعدی CI بدون هشدار Node deprecation.

- [ ] **NB-29 | Import بکاپ بدون هشدار «جایگزینی کل داده»** — عملیات خطرناک فقط Toast بعد از انجام دارد؛ کاربر قبل از عمل نمی‌داند همه‌چیز جایگزین می‌شود.
  - **شاهد:** SettingsScreen بخش restore + `MainViewModel.importBackupJson`.
  - **متد فیکس:** پیش از restore، AlertDialog: «کل داده‌های فعلی با محتوای بکاپ جایگزین می‌شود (X وام، Y کاربر). ادامه می‌دهید؟» — شمارش از JSON پارس‌شده؛ اجرای restore فقط پس از تأیید.
  - **معیار پذیرش:** restore فقط با تأیید؛ cancel بدون تغییر.
  - **تست:** دستی.

- [ ] **NB-30 | I/O دیسک روی Main thread** — `LoanIconHelper.saveCustomIconImage` در callback فتوپیکر روی main اجرا می‌شود و `loadSampledBitmap` داخل `remember{}` حین کامپوزیشن (اولین رندر jank دارد؛ کش LruCache فقط دفعات بعد را می‌پوشاند).
  - **شاهد:** `LoanIconHelper.saveCustomIconImage`/`loadSampledBitmap` و فراخواننده‌ها.
  - **متد فیکس:** ۱) در فتوپیکر، ذخیره در `withContext(Dispatchers.IO)` (کوروتین scope موجود UI). ۲) در Compose، لود نمونه از `remember{}` به `LaunchedEffect` منتقل شود (state پیش‌فرض null → آیکون پس از لود).
  - **معیار پذیرش:** StrictMode بدون DiskRead/WriteViolation روی main؛ انتخاب آیکون بدون گیر انیمیشن.

---

## ✅ درستی‌آزمایی‌شده‌ها (وقت صرفشان نکنید)

**بازبینی ۳ (خلاصه):** ۱) MIGRATION_1_2 لازم نیست (اسکما از ابتدا v2). ۲) SmartTips از daysUntilDue درست استفاده می‌کند. ۳) seed بعد از restore دوباره اجرا نمی‌شود. ۴) getPrimaryUser قطعی است (ORDER BY id ASC). ۵) پرمیشن POST_NOTIFICATIONS درست درخواست می‌شود. ۶) exact alarm فول‌بک SecurityException سالم دارد. ۷) dueDay=31 در محاسبات daysUntilDue clamp می‌شود. ۸) حذف checksum از بکاپ tradeoff عمدی است؛ لایهٔ دفاعی داده = اعتبارسنجی NB-4.

**بازبینی ۴ (جدید):**
9. **«checksum SHA-256 بکاپ روی Android ناپایدار است» — رد شد.** AOSP JSONObject از LinkedHashMap می‌سازد → ترتیب کلیدها stable؛ checksum روی ترتیبِ ثابت درست کار می‌کند.
10. **«insert با id صریح + AUTOINCREMENT باعث تصادم می‌شود» — رد شد.** sqlite_sequence با insert با id صریح به‌روز می‌شود؛ id بعدی صحیح تخصیص می‌یابد.
11. **«daysUntilDue برای وام عقب‌افتاده غلط است» — رد شد (بخشی).** رفتار «شمارش تا سررسید بعدی» برای چیپ درست است؛ مشکل واقعی فقط مرده‌بودن شاخهٔ overdue است (NB-16).
12. **منطق rollover/seed/lastKey سالم است** و تست‌های PureLogicUnitTest و LoanRepositoryIntegrityTest جامع‌اند.
13. **key=personName در ReceivablesScreen یگانه است** (groupBy روی نام کانونی).

---

## ✨ پیشنهاد امکانات (جانبی، خارج از باگ‌ها — بدون تغییر)

- [ ] **F1 — جدول LoanInstallment** به جای شمارندهٔ paidMonths: رکورد به‌ازای هر قسط (loanId، شماره، ماه شمسی، مبلغ، تاریخ واقعی). ریشه‌کارِ NB-14/18، سابقه‌های قبلی و پیش‌نیاز هر گزارش‌گیری.
- [ ] **F2 — بکاپ خودکار زمان‌بندی‌شده + رمزنگاری AES-256-GCM** فایل بکاپ (به سبک گاوصندوق AvaTTS).
- [ ] **F3 — شبیه‌ساز تسویه (What-if):** «اگر ماهی X اضافه بپردازم چند ماه زودتر تمام می‌شود؟»
- [ ] **F4 — دکمهٔ ارسال مستقیم به شماره شریک (smsto:)** — با حذف قابلیت تلفن منتفی شد؛ فقط اگر در آینده فیلد تماس برگردد.
- [ ] **F5 — گزارش ماهانهٔ قابل اشتراک (PDF/عکس)** برای شرکا (پس از NB-15 ساده‌تر می‌شود).

---

## ✅ فهرست فشردهٔ رفع‌شده‌های قبلی (شرح کامل در Update.md)

**P1:** P1-1 (کامیت 2fa03bf) — P1-2 (77018be) — P1-3 و P1-4 (6ccaf77) — P1-5 و P1-6 (0db3f48) — P1-7 و P1-8 (9a540d2) — P1-9 (dda6cdf)
**P2:** P2-1 و P2-7 (937c8a2) — P2-2 و P2-3 (8910cc4) — P2-4 و P2-8 (d190f43) — P2-5 و P2-6 (b20e944) — P2-9/P2-10/P2-11 حذف امکان (2a3b89a + حذف SoundHelper در c264263) — P2-12 و P2-13 (73b5eb1 + 77bf75a + اصلاح bf3a96f) — P2-14 (7235d33)
**P3:** P3-1 (9963ed3 + a9aaf24 + d13411d + 1a8948c + پاکسازی fe4f0b2) — P3-5 حذف امکان (f5f19fa، اسکمای v4) — P3-7 حذف (2e1920e) — P3-8 (f5f19fa + اصلاح منیفست 82ef55e) — P3-9 حذف (f5f19fa + 9b017fe تا 3869a12) — P3-10 (c746009)
**P4:** P4-1 (گام تست در CI) — P4-2 (PureLogicUnitTest) — P4-3 (اصلاح README) — P4-7 (9de7f56)
**NB بازبینی ۳:** NB-1 تا NB-13 همه رفع شدند (شرح و کامیت‌ها در Update.md و bug.md نسخهٔ ۲ در تاریخچهٔ گیت).
**سوال‌های باز:** A1–A5 همه حل‌شده.

---

## 🧭 راهنمای فیکس برای ایجنت‌های کد

### اینوارینت‌ها (هر فیکس باید این قواعد را حفظ کند)
1. **تراکنش:** هر عملیات نوشتنیِ چندمرحله‌ای روی Room → database.withTransaction (الگو: restore، saveLoanWithShares، saveUser). هیچ استثنایی.
2. **مهاجرت:** هرگز destructive migration؛ هر تغییر اسکیما = Migration واقعی (الگو: MIGRATION_3_4 با بازسازی جدول و حفظ داده).
3. **نام‌ها:** تطبیق هویت اشخاص فقط و فقط با PersianNameNormalizer.namesMatch — هیچ == خام یا equals روی نام‌ها (NB-20/21 همین را در ورودی‌ها کامل می‌کنند).
4. **سهم من:** از یک تابع واحد تغذیه کن؛ myShareAmount ذخیره‌شده فقط برای وام بی‌شریک معتبر است.
5. **تقویم:** هر محاسبهٔ شمسی فقط از ShamsiDateHelper (daysUntilDue، getDaysInShamsiMonth، calculateEndDate) — هیچ فرض ۳۰روزه یا تفریق خام روز.
6. **ورودی عددی:** همیشه نرمال‌سازی ارقام فارسی/عربی + اعتبارسنجی صریح (NB-5)؛ هیچ clamp یا حذفِ نویسهٔ بی‌صدایی که عدد را جابجا کند.
7. **seed:** فقط یک‌بار در هر نصب (پرچم initialDataSeeded)؛ دست‌نخوردن پرچم در restore.
8. **زبان و فرمت:** رشته‌های کاربر فارسی؛ اعداد نمایشی از NumberFormatter با تنظیمات ارقام/فشرده‌سازی (و مصرف از StateFlow نه value).
9. **بازنشانی/حذف کامل (جدید):** هر مسیر «حذف همه/بازنشانی/restore» باید همهٔ اجزای هم‌زیرساختی را پاک کند (رکوردهای پرداخت + فایل‌های آیکون — NB-14/19) و پیش از اجرا تأییدیهٔ صریح کاربر بگیرد (NB-22/29).

### دستور کار هر فیکس
1. **شاهد:** قبل از تغییر، ارجاع دقیق فایل/تابع را در bug.md تأیید کن؛ اگر سورس عوض شده، اول متن باگ را به‌روز کن.
2. **فیکس مینیمال:** یک باگ = یک کامیت؛ بدون refactor سرخورد و بدون لمس فایل‌های بی‌ارتباط.
3. **تست رگرسیون:** جایی که ممکن است unit/Robolectric (الگوی LoanRepositoryIntegrityTest و جدول‌های تست NB-5)؛ نام فایل تست با شمارهٔ باگ.
4. **راستی‌آزمایی:** `./gradlew assembleDebug testDebugUnitTest` سبز شود؛ اگر در محیط ایجنت بیلد ممکن نبود، صریحاً در Update.md بنویس که اجرا نشده.
5. **ثبت:** تیک + شماره کامیت در همین فایل + یک بخش در Update.md با شرح مشکل/راه‌حل/محدودیت.
6. **پوش:** کامیت‌های مستندات با `[skip ci]`؛ کامیت بدون `[skip ci]` = trigger ریلیز خودکار — فقط وقتی عمدی است.

### ترتیب پیشنهادی فیکس (بازبینی ۴)
1. **NB-14 + NB-19** (یک خانواده: بازنشانی کامل — رکورد یتیم + فایل یتیم)
2. **NB-20، NB-21، NB-26** (اعتبارسنجی ورودی — سبک و ارزان)
3. **NB-22 + NB-29** (دیالوگ‌های تأیید برای عملیات‌های تخریبی)
4. **NB-16 + NB-17** (تقویم/اعلان — با تابع مشترک effectiveDueDay؛ NB-17 به NB-16 وابسته است)
5. **NB-15 + NB-18** (هر دو در مسیر بکاپ/restore — با هم انجام شود)
6. **NB-25، NB-30، NB-24، NB-27، NB-23** (P4ها)
7. **P4-4، P4-5، P4-6، NB-28** از بازبینی‌های قبل
8. سپس فاز امکانات (F1 به بالا).

---

*این فایل معمولاً با کامیت [skip ci] پوش می‌شود تا ورک‌فلو انتشارِ خودکار trigger نشود؛ استثناهای عمدی (مثل کامیت آغاز ریلیز) در همان کامیت مستند می‌شوند.*

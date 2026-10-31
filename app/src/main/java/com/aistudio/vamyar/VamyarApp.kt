package com.aistudio.vamyar

import android.app.Application
import com.aistudio.vamyar.data.AppDatabase
import com.aistudio.vamyar.data.LoanRepository
import com.aistudio.vamyar.notification.NotificationHelper
import com.aistudio.vamyar.util.AppPreferences
import com.aistudio.vamyar.util.AutoBackupHelper
import com.aistudio.vamyar.util.CrashLogHelper
import com.aistudio.vamyar.util.MonthlyRolloverHelper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VamyarApp : Application() {

    lateinit var repository: LoanRepository
        private set

    // فیکس bug.md/NB-10: CoroutineScope پایدار با SupervisorJob برای کارهای پس‌زمینه اپلیکیشن
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // فیکس bug.md/SB-04: کرش‌لاگ محلی — اولین کار قبل از هر چیز دیگر، تا حتی
        // کرش‌های اولیهٔ راه‌اندازی هم لاگ شوند؛ delegate به handler قبلی در پایان.
        CrashLogHelper.installDefaultHandler(this)

        val database = AppDatabase.getDatabase(this)
        repository = LoanRepository(
            loanDao = database.loanDao(),
            debtorShareDao = database.debtorShareDao(),
            paymentRecordDao = database.paymentRecordDao(),
            userProfileDao = database.userProfileDao(),
            database = database,
            // فیکس bug.md/P3-10: مخزن مالکِ قاعدهٔ «seed فقط یک‌بار در هر نصب» است؛
            // پرچم persisted در AppPreferences نگه داشته می‌شود.
            appPreferences = AppPreferences(this)
        )

        NotificationHelper.createNotificationChannel(this)
        // فیکس bug.md/P2-7: ساعت چکِ روزانه دیگر هاردکد نیست؛ از AppPreferences
        // خوانده می‌شود (پیش‌فرض ۱۰:۰۰) و هر اجرای اپ هم آلارم را دوباره برپا می‌کند.
        NotificationHelper.scheduleDailyCheck(this)

        // فیکس bug.md/P1-6 و NB-10: رِسِت خودکار پرچم‌های «پرداخت ماه جاری»
        // و seed اولیه داخل runCatching تا هیچ خطایی مانع بالا آمدن UI اپ نشود.
        // فیکس bug.md/F6: پس از seed، شناسه‌های عمومی به رکوردهای مهاجرت‌خورده/تازه
        // تخصیص می‌یابند (idempotent — اجرای دوباره بی‌اثر است).
        appScope.launch {
            runCatching {
                MonthlyRolloverHelper.rolloverIfNeeded(this@VamyarApp)
                repository.checkAndSeedInitialData()
                repository.backfillMissingPublicIds()
                // فیکس bug.md/SB-07 + NB-63: پشتیبان خودکار هفتگی — اگر از آخرین
                // پشتیبان بیش از ۷ روز گذشته باشد، یک JSON کامل نوشته می‌شود؛
                // در Android 10+ در پوشهٔ عمومی Documents/VaamYar (تا با حذف نصب
                // برنامه پشتیبان‌ها بمانند؛ در اندروید قدیمی‌تر fallback به حافظهٔ
                // داخلی). ۳ نسخهٔ آخر می‌ماند و شکست هرگز راه‌اندازی اپ را
                // نمی‌شکند (مثل بقیهٔ این بلوک داخل runCatching است).
                AutoBackupHelper.performAutoBackupIfNeeded(this@VamyarApp, repository)
            }.onFailure { e ->
                Log.e("VamyarApp", "Startup initialization failed", e)
            }
        }
    }
}

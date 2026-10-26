package com.aistudio.vamyar

import android.app.Application
import com.aistudio.vamyar.data.AppDatabase
import com.aistudio.vamyar.data.LoanRepository
import com.aistudio.vamyar.notification.NotificationHelper
import com.aistudio.vamyar.util.AppPreferences
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
            }.onFailure { e ->
                Log.e("VamyarApp", "Startup initialization failed", e)
            }
        }
    }
}

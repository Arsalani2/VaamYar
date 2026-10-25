package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.LoanRepository
import com.example.notification.NotificationHelper
import com.example.util.MonthlyRolloverHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VamyarApp : Application() {

    lateinit var repository: LoanRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getDatabase(this)
        repository = LoanRepository(
            loanDao = database.loanDao(),
            debtorShareDao = database.debtorShareDao(),
            paymentRecordDao = database.paymentRecordDao(),
            userProfileDao = database.userProfileDao(),
            database = database
        )

        NotificationHelper.createNotificationChannel(this)
        // فیکس bug.md/P2-7: ساعت چکِ روزانه دیگر هاردکد نیست؛ از AppPreferences
        // خوانده می‌شود (پیش‌فرض ۱۰:۰۰) و هر اجرای اپ هم آلارم را دوباره برپا می‌کند.
        NotificationHelper.scheduleDailyCheck(this)

        // فیکس bug.md/P1-6: رِسِت خودکار پرچم‌های «پرداخت ماه جاری»
        // وقتی ماه شمسی عوض شده باشد — قبل از seed اجرا می‌شود
        // تا دادهٔ نمایشی هم با پرچم سالم ساخته شود.
        CoroutineScope(Dispatchers.IO).launch {
            MonthlyRolloverHelper.rolloverIfNeeded(this@VamyarApp)
            repository.checkAndSeedInitialData()
        }
    }
}

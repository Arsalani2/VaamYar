package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.LoanRepository
import com.example.notification.NotificationHelper
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
            userProfileDao = database.userProfileDao()
        )

        NotificationHelper.createNotificationChannel(this)
        NotificationHelper.scheduleDailyCheck(this, hour = 10, minute = 0)

        // Seed initial loans if empty
        CoroutineScope(Dispatchers.IO).launch {
            repository.checkAndSeedInitialData()
        }
    }
}

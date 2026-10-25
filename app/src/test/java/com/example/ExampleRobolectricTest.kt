package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LoanRepository
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.UserProfile
import com.example.ui.components.LoanViewMode
import com.example.util.AppPreferences
import com.example.util.NumberFormatter
import com.example.util.ShamsiDate
import com.example.util.ShamsiDateHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: LoanRepository
    private lateinit var preferences: AppPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = AppPreferences(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LoanRepository(
            loanDao = db.loanDao(),
            debtorShareDao = db.debtorShareDao(),
            paymentRecordDao = db.paymentRecordDao(),
            userProfileDao = db.userProfileDao(),
            database = db,
            appPreferences = preferences
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("وام‌یار", appName)
    }

    @Test
    fun testSeedingAndDataPersistence() = runBlocking {
        // Initial run seeds 3 guide loans and sets up primary user "من"
        repository.checkAndSeedInitialData()
        val loans = repository.allLoans.first()
        assertEquals(3, loans.size)

        val primary = repository.primaryUser.first()
        assertNotNull(primary)
        assertEquals("من", primary!!.name)
        assertTrue(primary.isPrimary)

        // Add a user created custom loan
        val customLoan = Loan(
            title = "وام جدید شخصی",
            dueDay = 15,
            monthlyAmount = 3500000L,
            totalMonths = 10,
            paidMonths = 1
        )
        repository.saveLoanWithShares(customLoan, emptyList())
        assertEquals(4, repository.allLoans.first().size)

        // CRITICAL BUG VERIFICATION: Restarting app/re-calling checkAndSeedInitialData must NEVER wipe data!
        repository.checkAndSeedInitialData()
        val loansAfterRestart = repository.allLoans.first()
        assertEquals(4, loansAfterRestart.size)
    }

    @Test
    fun testAppPreferencesPersistence() {
        // Default theme mode should follow SYSTEM
        assertEquals(com.example.ui.theme.AppThemeMode.SYSTEM, preferences.themeMode)

        // Digit format toggles
        preferences.usePersianDigits = false
        assertFalse(preferences.usePersianDigits)
        preferences.usePersianDigits = true
        assertTrue(preferences.usePersianDigits)

        // Tour guide persistence
        assertFalse(preferences.hasSeenAppGuide)
        preferences.hasSeenAppGuide = true
        assertTrue(preferences.hasSeenAppGuide)
    }

    @Test
    fun testSafeUserDeletion() = runBlocking {
        repository.checkAndSeedInitialData()

        // Create partner user
        repository.saveUser(UserProfile(name = "زهرا"))
        val users = repository.allUsers.first()
        val zahra = users.find { it.name == "زهرا" }!!

        // Initially no loans, so user can be deleted
        var checkResult = repository.checkCanDeleteUser(zahra.id)
        assertTrue(checkResult.canDelete)

        // Create loan where Zahra is partner
        val loan = Loan(
            title = "بانک پاسارگاد",
            dueDay = 10,
            monthlyAmount = 2000000L,
            totalMonths = 12,
            paidMonths = 0,
            myShareAmount = 1000000L
        )
        val share = DebtorShare(
            loanId = 0L,
            loanTitle = "بانک پاسارگاد",
            personName = "زهرا",
            shareAmount = 1000000L,
            dueDay = 10
        )
        repository.saveLoanWithShares(loan, listOf(share))

        // Now Zahra cannot be deleted!
        checkResult = repository.checkCanDeleteUser(zahra.id)
        assertFalse(checkResult.canDelete)
        assertEquals(1, checkResult.blockingLoanTitles.size)
        assertEquals("بانک پاسارگاد", checkResult.blockingLoanTitles[0])
    }

    @Test
    fun testIntelligentElapsedInstallments() {
        // Start date: 1405/06/07, Current: 1405/08/10 -> 3 installments (Shahrivar, Mehr, Aban)
        val elapsed = ShamsiDateHelper.calculateElapsedInstallments(
            startYear = 1405,
            startMonth = 6,
            startDay = 7,
            curYear = 1405,
            curMonth = 8,
            curDay = 10,
            totalMonths = 12
        )
        assertEquals(3, elapsed)

        // If today is 1405/08/05 (before due day 7 in Aban) -> 2 installments
        val elapsedBeforeDue = ShamsiDateHelper.calculateElapsedInstallments(
            startYear = 1405,
            startMonth = 6,
            startDay = 7,
            curYear = 1405,
            curMonth = 8,
            curDay = 5,
            totalMonths = 12
        )
        assertEquals(2, elapsedBeforeDue)
    }

    @Test
    fun testPersistentViewMode() {
        preferences.loanViewMode = LoanViewMode.CHART
        assertEquals(LoanViewMode.CHART, preferences.loanViewMode)

        preferences.loanViewMode = LoanViewMode.SQUARE
        assertEquals(LoanViewMode.SQUARE, preferences.loanViewMode)
    }

    @Test
    fun testDateSyncAndOverride() {
        preferences.clearCustomDate()
        assertFalse(preferences.isCustomDateEnabled)

        preferences.setCustomDate(1405, 8, 15)
        assertTrue(preferences.isCustomDateEnabled)
        assertEquals(1405, preferences.customYear)
        assertEquals(8, preferences.customMonth)
        assertEquals(15, preferences.customDay)
    }

    @Test
    fun testPriceFormattingWithCommas() {
        assertEquals("۲,۸۲۲,۰۰۰", NumberFormatter.formatWithCommas(2822000L, usePersian = true))
        assertEquals("2,822,000", NumberFormatter.formatWithCommas(2822000L, usePersian = false))
        assertEquals(2822000L, NumberFormatter.parseAmount("۲,۸۲۲,۰۰۰ تومان"))
    }
}

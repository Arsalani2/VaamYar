package com.aistudio.vamyar.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.ui.components.PriceInputField
import com.aistudio.vamyar.ui.theme.LoanFormColors
import com.aistudio.vamyar.ui.theme.LoanFormDimensions
import com.aistudio.vamyar.util.InputLimits
import com.aistudio.vamyar.util.LoanIconHelper
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.PersianNameNormalizer
import com.aistudio.vamyar.util.ShamsiDate
import com.aistudio.vamyar.util.ShamsiDateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * فرم تمام‌صفحهٔ افزودن/ویرایش وام — بازطراحی AddLoanScreen مطابق Design
 * Specification (۲۰۲۶-۱۰-۲۸):
 *
 * ساختار: هدر ثابت (LoanFormTopBar) → یک اسکرول عمودی اصلی شامل کارت‌های
 * LoanIdentityCard / PaymentScheduleCard / RepaymentSummaryCard /
 * MonthlyInstallmentCalendarCard / LoanPartnersCard → نوار اقدامات ثابت
 * (LoanFormBottomBar). کل state واقعی در همین تابع است و همهٔ کارت‌ها به
 * همان state متصل‌اند (هیچ دادهٔ ساختگی/ثابتی در UI نیست).
 *
 * امضای عمومی تابع و همهٔ قابلیت‌های موجود (آیکون/رنگ اختصاصی، تقویم شمسی
 * اقساط با رکوردهای واقعی LoanInstallment، شرکا و سهم‌بندی، اعتبارسنجی‌های
 * زندهٔ NB-33/36/40 و منطق ذخیرهٔ F1/NB-37) دقیقاً حفظ شده‌اند؛ فقط لایهٔ
 * ظاهری بازطراحی شده است.
 */
@Composable
fun AddEditLoanDialog(
    loanToEdit: Loan? = null,
    initialShares: List<DebtorShare> = emptyList(),
    // فیکس bug.md/NB-37: رکوردهای واقعی اقساط وامِ در حال ویرایش — منبع واحدِ
    // تیک‌های دیالوگ (نه شمارندهٔ ترتیبی).
    initialInstallments: List<LoanInstallment> = emptyList(),
    allUsers: List<UserProfile> = emptyList(),
    // فیکس bug.md/NB-36: عناوین وام‌های موجود برای چک زندهٔ «عنوان تکراری» (self = ویرایش‌شونده)
    allLoanTitles: List<String> = emptyList(),
    currentShamsiDate: ShamsiDate,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onDismiss: () -> Unit,
    onAddNewUser: (UserProfile) -> Unit,
    onSave: (Loan, List<DebtorShare>, Set<Pair<Int, Int>>) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(loanToEdit?.title ?: "") }
    var totalMonthsText by remember { mutableStateOf(loanToEdit?.totalMonths?.toString() ?: "12") }
    var selectedColor by remember { mutableLongStateOf(loanToEdit?.colorHex ?: 0xFF1976D2L) }

    // Icon & Shape & Custom Image State
    var selectedIconName by remember { mutableStateOf(loanToEdit?.iconName ?: "account_balance") }
    var selectedIconShape by remember { mutableStateOf(loanToEdit?.iconShape ?: "ROUNDED_SQUARE") }
    var customIconPath by remember { mutableStateOf(loanToEdit?.customIconPath) }

    // فیکس bug.md/NB-30: کپی/دکود/کمپرس تصویر انتخابی قبلاً مستقیم در callbackِ
    // فتوپیکر روی نخ اصلی اجرا می‌شد و برای عکس‌های بزرگ UI را قفل می‌کرد —
    // کارِ دیسک/دکود به Dispatchers.IO منتقل شد.
    val iconScope = rememberCoroutineScope()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            iconScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    LoanIconHelper.saveCustomIconImage(context, uri)
                }
                if (saved != null) {
                    // If there was an older custom image, clean this up
                    val older = customIconPath
                    if (older != null && older != saved) {
                        withContext(Dispatchers.IO) { LoanIconHelper.deleteCustomIconFile(older) }
                    }
                    customIconPath = saved
                }
            }
        }
    }

    // Start Date
    var startDate by remember {
        mutableStateOf(
            if (loanToEdit != null) ShamsiDate(loanToEdit.startYear, loanToEdit.startMonth, loanToEdit.dueDay)
            else currentShamsiDate
        )
    }

    var showCalendarPicker by remember { mutableStateOf(false) }

    // Stable raw digits for installment amount
    var monthlyAmountDigits by remember {
        mutableStateOf(
            if (loanToEdit != null && loanToEdit.monthlyAmount > 0)
                loanToEdit.monthlyAmount.toString()
            else ""
        )
    }

    val totalMonths = remember(totalMonthsText) {
        NumberFormatter.parseInt(totalMonthsText).coerceIn(1, 120)
    }

    // فیکس bug.md/NB-33: اعتبارسنجی صریح با بازخورد زنده — هیچ مقداری بی‌صدا clamp یا
    // تغییر نمی‌شود؛ مقدار خارج از بازه فقط با اصلاحِ خود کاربر ذخیره می‌شود (ذخیره مسدود).
    // coerceIn بالا فقط برای محاسبات نمایشی میانی است؛ مقدار ذخیره‌شده همان ورودی معتبر کاربر است.
    val durationError = InputLimits.validateDurationMonths(totalMonthsText)
    // فیکس bug.md/NB-36: خطای زندهٔ عنوان = خالی/طول (NB-33) + تکراری‌بودن در وام‌های
    // موجود با کلید استاندارد. هنگام ویرایش، عنوانِ خودِ وام از بررسی کنار گذاشته می‌شود.
    val titleError = InputLimits.validateTitle(title)
        ?: InputLimits.validateUniqueTitle(
            raw = title,
            existingTitles = allLoanTitles,
            selfKey = loanToEdit?.let { PersianNameNormalizer.matchKey(it.title) }
        )

    // Auto-detected paid months from start date to current date
    val autoElapsed = remember(startDate.year, startDate.month, startDate.day, currentShamsiDate.year, currentShamsiDate.month, currentShamsiDate.day, totalMonths) {
        ShamsiDateHelper.calculateElapsedInstallments(
            startYear = startDate.year,
            startMonth = startDate.month,
            startDay = startDate.day,
            curYear = currentShamsiDate.year,
            curMonth = currentShamsiDate.month,
            curDay = currentShamsiDate.day,
            totalMonths = totalMonths
        )
    }

    // فیکس bug.md/P1-5: «قسط رسیده» با «قسط پرداخت‌شده» یکی نیست — autoElapsed فقط
    // نمایشِ سررسیدرسیده‌هاست؛ تیک‌ها تنها با تأیید صریح کاربر (چیپ ثبتِ رسیده‌ها
    // یا لمس ماه‌ها) تغییر می‌کنند.
    // فیکس bug.md/NB-37: تیک‌های فرم مجموعهٔ (سال، ماه) واقعی از رکوردهای
    // LoanInstallment هستند (مثل LoanItemCard) — نه شمارندهٔ ترتیبی؛ وام جدید از صفر.
    var paidYearMonths by remember {
        mutableStateOf(initialInstallments.map { it.year to it.month }.toSet())
    }

    // Dynamic list of debtor shares (سهم شرکا)
    val sharesList = remember {
        mutableStateListOf<DebtorShare>().apply {
            addAll(initialShares)
        }
    }

    var showAddPartnerDialog by remember { mutableStateOf(false) }
    var showCreateUserDialog by remember { mutableStateOf(false) }

    val rawMonthlyAmount = monthlyAmountDigits.toLongOrNull() ?: 0L
    val hasPartners = sharesList.isNotEmpty()
    val othersTotalShare = sharesList.sumOf { it.shareAmount }
    val calculatedMyShare = if (hasPartners) {
        (rawMonthlyAmount - othersTotalShare).coerceAtLeast(0L)
    } else {
        rawMonthlyAmount
    }

    // فیکس bug.md/P1-8: مجموع سهم شرکا هرگز نباید از مبلغ کل قسط ماهانه بیشتر شود
    // (قبلاً سهم کاربر مبنا بی‌صدا صفر می‌شد). این حالت هم موقع افزودن شریک چک می‌شود
    // و هم اینجا — مثلاً اگر کاربر بعداً مبلغ قسط را کم کند — ذخیره‌ی وام را مسدود می‌کند.
    val sharesExceedMonthly = hasPartners && othersTotalShare > rawMonthlyAmount
    val primaryUserName = allUsers.firstOrNull { it.isPrimary }?.name?.trim()

    // فیکس bug.md/NB-37: ماه‌های سررسیدگذشتهٔ بدون تیک (ماه‌های عقب‌افتاده) — همان
    // منطق LoanItemCard؛ ماهِ جاریِ تقویم «عقب‌افتاده» نیست (رسیدگی جداگانه).
    val overdueYearMonths = remember(totalMonths, startDate.year, startDate.month, currentShamsiDate.year, currentShamsiDate.month, paidYearMonths) {
        val curTotal = currentShamsiDate.year * 12 + (currentShamsiDate.month - 1)
        (1..totalMonths).mapNotNull { k ->
            val m = ShamsiDateHelper.installmentMonth(startDate.year, startDate.month, k)
            val key = m.year to m.month
            if (m.year * 12 + (m.month - 1) < curTotal && key !in paidYearMonths) key else null
        }.toSet()
    }
    // فیکس bug.md/NB-37: ماه‌های رسیدهٔ پرداخت‌نشده (ماه جاریِ رسیده هم شامل است) —
    // چیپ quick-sync همهٔ همین‌ها را یک‌جا تیک می‌زند (معادل payArrearsAndCurrentMonth).
    val unpaidArrivedMonths = remember(autoElapsed, startDate.year, startDate.month, paidYearMonths) {
        (1..autoElapsed).mapNotNull { k ->
            val m = ShamsiDateHelper.installmentMonth(startDate.year, startDate.month, k)
            val key = m.year to m.month
            if (key !in paidYearMonths) key else null
        }.toSet()
    }
    // فیکس bug.md/NB-37: شمارندهٔ نمایشی فقط تیک‌های داخل بازهٔ ۱..totalMonths است؛
    // «پرداخت‌شده > کل» در فرم ممکن نیست (ماه‌ها تا totalMonths رندر می‌شوند)
    // و دفاعِ نهایی P1-9 در Repository می‌ماند.
    val paidInRangeCount = paidYearMonths.count { (y, m) ->
        val n = ShamsiDateHelper.installmentNumberForMonth(startDate.year, startDate.month, y, m)
        n != null && n in 1..totalMonths
    }

    val endDate = remember(startDate, totalMonths) {
        ShamsiDateHelper.calculateEndDate(startDate.year, startDate.month, totalMonths)
    }

    val colorOptions = listOf(
        0xFF1976D2L, // Blue
        0xFFD4AF37L, // Gold
        0xFF0288D1L, // Cyan
        0xFF388E3CL, // Green
        0xFFF57C00L, // Orange
        0xFFC2185BL, // Ruby
        0xFF7B1FA2L  // Purple
    )

    // دادهٔ تقویم ماه‌های شمسی — از همان منابع واقعی (رکوردها + سررسید + ماه جاری)؛
    // هیچ وضعیتی به چرخهٔ Recomposition وابسته نیست (کلید‌های پایدار).
    val currentYearMonthKey = currentShamsiDate.year to currentShamsiDate.month
    val monthChips = remember(totalMonths, startDate.year, startDate.month, paidYearMonths, overdueYearMonths, currentYearMonthKey) {
        (1..totalMonths).map { k ->
            val m = ShamsiDateHelper.installmentMonth(startDate.year, startDate.month, k)
            val key = m.year to m.month
            val status = when {
                key in paidYearMonths -> LoanMonthStatus.PAID
                key in overdueYearMonths -> LoanMonthStatus.OVERDUE
                key == currentYearMonthKey -> LoanMonthStatus.CURRENT
                else -> LoanMonthStatus.UPCOMING
            }
            LoanMonthChipData(
                installmentNumber = k,
                year = m.year,
                month = m.month,
                monthName = m.monthName,
                status = status
            )
        }
    }

    val totalInstallmentsAmount = rawMonthlyAmount * totalMonths

    // ─── فرم تمام‌صفحه: هدر ثابت + اسکرول عمودی اصلی + نوار اقدامات ثابت ───
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LoanFormColors.ScreenBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Insets سیستم (نوار وضعیت/ناوبری/کیبورد) یک‌بار و به‌صورت
                    // idempotent اعمال می‌شود — اگر پنجره خودش inset را اعمال کرده
                    // باشد، این padding صفر می‌شود و دوباره اعمال نمی‌گردد.
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // ۱) هدر ثابت
                LoanFormTopBar(
                    title = if (loanToEdit == null) "افزودن وام" else "ویرایش وام",
                    subtitle = "مشخصات و برنامه بازپرداخت",
                    iconName = selectedIconName,
                    iconShape = selectedIconShape,
                    customIconPath = customIconPath,
                    tintColor = Color(selectedColor),
                    onClose = onDismiss
                )

                // ۲) محتوای اسکرول عمودی اصلی — تمام کارت‌ها؛ آخرین بخش (شرکا)
                // با فاصله ۱۶dp از نوار پایین قابل مشاهده است.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = LoanFormDimensions.ScreenHorizontalPadding)
                        .padding(top = LoanFormDimensions.SectionSpacing, bottom = LoanFormDimensions.ScrollBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.SectionSpacing)
                ) {
                    // ۲.۱ مشخصات و ظاهر وام
                    LoanIdentityCard(
                        title = title,
                        onTitleChange = { title = it },
                        titleError = titleError,
                        colorOptions = colorOptions,
                        selectedColor = selectedColor,
                        onColorSelected = { selectedColor = it },
                        selectedIconName = selectedIconName,
                        onIconSelected = { selectedIconName = it },
                        selectedIconShape = selectedIconShape,
                        onShapeChange = { selectedIconShape = it },
                        customIconPath = customIconPath,
                        onPickCustomImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveCustomImage = {
                            LoanIconHelper.deleteCustomIconFile(customIconPath)
                            customIconPath = null
                        }
                    )

                    // ۲.۲ مبلغ قسط و زمان‌بندی
                    PaymentScheduleCard(
                        monthlyAmountDigits = monthlyAmountDigits,
                        onMonthlyAmountChange = { monthlyAmountDigits = it },
                        amountErrorText = if (rawMonthlyAmount <= 0L) {
                            "مبلغ قسط ماهانه باید بزرگ‌تر از صفر باشد."
                        } else null,
                        startDateText = startDate.toStandardFormat(usePersianDigits = usePersianDigits),
                        onPickStartDate = { showCalendarPicker = true },
                        dueDayHint = "سررسید هر ماه: ${NumberFormatter.formatNumber(startDate.day, usePersianDigits)}ام",
                        totalMonthsText = totalMonthsText,
                        onTotalMonthsChange = { totalMonthsText = it.filter { c -> c.isDigit() } },
                        durationError = durationError,
                        quickDurationOptions = listOf(6, 10, 12, 24, 36),
                        selectedDuration = totalMonths,
                        onQuickDurationSelected = { duration -> totalMonthsText = duration.toString() },
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands
                    )

                    // ۲.۳ خلاصه مالی — کاملاً از state واقعی فرم محاسبه می‌شود
                    RepaymentSummaryCard(
                        perInstallmentAmount = rawMonthlyAmount,
                        monthsCount = totalMonths,
                        totalAmount = totalInstallmentsAmount,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands
                    )

                    // ۲.۴ تقویم ماه‌های شمسی و وضعیت پرداخت اقساط
                    MonthlyInstallmentCalendarCard(
                        monthChips = monthChips,
                        paidCount = paidInRangeCount,
                        totalMonths = totalMonths,
                        arrivedCount = autoElapsed,
                        currentMonthLabel = "${currentShamsiDate.monthName} ${NumberFormatter.formatNumber(currentShamsiDate.year, usePersianDigits)}",
                        unpaidArrivedCount = unpaidArrivedMonths.size,
                        onQuickSyncArrived = { paidYearMonths = paidYearMonths + unpaidArrivedMonths },
                        onToggleMonth = { clickedInstallment ->
                            val m = ShamsiDateHelper.installmentMonth(startDate.year, startDate.month, clickedInstallment)
                            val key = m.year to m.month
                            paidYearMonths = if (key in paidYearMonths) paidYearMonths - key else paidYearMonths + key
                        },
                        usePersianDigits = usePersianDigits
                    )

                    // ۲.۵ شرکا و سهم‌بندی (پس از تقویم، همراه فرم اسکرول می‌شود)
                    LoanPartnersCard(
                        shares = sharesList,
                        onRemoveShare = { index -> sharesList.removeAt(index) },
                        onAddPartner = { showAddPartnerDialog = true },
                        myShareAmount = calculatedMyShare,
                        othersTotalShare = othersTotalShare,
                        monthlyAmount = rawMonthlyAmount,
                        sharesExceedMonthly = sharesExceedMonthly,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands
                    )
                }

                // ۳) نوار اقدامات ثابت پایین — خارج از محتوای اسکرولی؛ فعال بودن
                // ذخیره به اعتبارسنجی واقعی فرم وابسته است.
                LoanFormBottomBar(
                    onCancel = onDismiss,
                    saveEnabled = !sharesExceedMonthly && titleError == null && durationError == null && rawMonthlyAmount > 0L,
                    onSave = {
                        // فیکس bug.md/NB-37: مجموعهٔ (سال، ماه) واقعی مستقیم به
                        // Repository می‌رود تا رکوردها هم‌ارز و شمارنده‌ها از روی
                        // همان رکوردها sync شوند (دفاع P1-9 در Repository می‌ماند).
                        val loan = (loanToEdit ?: Loan(
                            title = title.ifBlank { "وام جدید" },
                            dueDay = startDate.day,
                            monthlyAmount = rawMonthlyAmount,
                            totalMonths = totalMonths,
                            paidMonths = paidInRangeCount
                        )).copy(
                            title = title.ifBlank { "وام جدید" },
                            dueDay = startDate.day,
                            monthlyAmount = rawMonthlyAmount,
                            totalMonths = totalMonths,
                            paidMonths = paidInRangeCount,
                            isCurrentMonthPaid = (currentShamsiDate.year to currentShamsiDate.month) in paidYearMonths,
                            startYear = startDate.year,
                            startMonth = startDate.month,
                            endYear = endDate.year,
                            endMonth = endDate.month,
                            myShareAmount = calculatedMyShare,
                            hasPartners = hasPartners,
                            colorHex = selectedColor,
                            iconName = selectedIconName,
                            iconShape = selectedIconShape,
                            customIconPath = customIconPath
                        )

                        onSave(loan, sharesList, paidYearMonths)
                    }
                )
            }
        }
    }

    // Calendar Picker Dialog
    if (showCalendarPicker) {
        ShamsiDatePickerDialog(
            initialDate = startDate,
            usePersianDigits = usePersianDigits,
            onDismiss = { showCalendarPicker = false },
            onDateSelected = { pickedDate ->
                startDate = pickedDate
                showCalendarPicker = false
                // فیکس bug.md/P1-5: انتخاب تاریخ شروع دیگر paidMonths را به‌صورت خودکار
                // برابر «اقساط رسیده» نمی‌کند؛ پرداخت‌شده‌ها فقط با تأیید صریح کاربر ثبت می‌شوند.
            }
        )
    }

    // Add Partner Modal Dialog
    if (showAddPartnerDialog) {
        // فیکس bug.md/P1-8(ج): کاربر مبنا در فهرست انتخاب شرکا نیست؛ سهم او به‌صورت خودکار
        // از باقی‌مانده‌ی قسط محاسبه می‌شود و شریکِ همان وام ثبت نمی‌شود (شمارش دوبرابر).
        val partnerCandidates = allUsers.filter { !it.isPrimary }
        var selectedUserName by remember { mutableStateOf(partnerCandidates.firstOrNull()?.name ?: "") }
        var partnerAmountDigits by remember { mutableStateOf("") }
        var partnerDesc by remember { mutableStateOf("") }
        // فیکس bug.md/NB-40 (بند ج): اعتبارسنجی سهم شریک «زنده» است — نه فقط هنگام ثبت.
        // هر خطا زیر همان فیلدش با isError دیده می‌شود و دکمهٔ «ثبت سهم» تا اصلاح
        // غیرفعال می‌ماند؛ دفاع نهایی همچنان در Repository است (OwnerlessShareException).
        val partnerName = selectedUserName.trim()
        val partnerAmount = partnerAmountDigits.toLongOrNull() ?: 0L
        val partnerNameError: String? = when {
            // اگر هنوز کاربری تعریف نشده، راهنمای «+ کاربر جدید» همان‌جا هست — خطای اضافه نده
            partnerCandidates.isEmpty() -> null
            partnerName.isBlank() ->
                "نام شریک را انتخاب کنید."
            // فیکس bug.md/NB-35 (اینوارینت #۱۱): شریک باید کاربر تعریف‌شده باشد
            !InputLimits.isDefinedPartner(partnerName, partnerCandidates.map { it.name }) ->
                "شریک باید از کاربران تعریف‌شده انتخاب شود؛ ابتدا با دکمهٔ «+ کاربر جدید» ثبتش کنید."
            // فیکس bug.md/NB-21 — مقایسهٔ نام با namesMatch (نه equals):
            // «علی» و «علي» (ي/ك عربی) و فاصله‌های زائد یکسان شمرده می‌شوند
            primaryUserName != null && PersianNameNormalizer.namesMatch(partnerName, primaryUserName) ->
                "«$partnerName» کاربر مبناست؛ سهم او به‌صورت خودکار از باقی‌مانده‌ی قسط محاسبه می‌شود و نمی‌تواند شریکِ همین وام ثبت شود."
            sharesList.any { PersianNameNormalizer.namesMatch(it.personName.trim(), partnerName) } ->
                "«$partnerName» قبلاً شریکِ همین وام ثبت شده است؛ برای تغییر مبلغ، سهم قبلی را حذف و دوباره ثبت کنید."
            else -> null
        }
        val partnerAmountError: String? = when {
            partnerAmount <= 0L ->
                "مبلغ سهم شریک باید بزرگ‌تر از صفر باشد."
            partnerAmount + othersTotalShare > rawMonthlyAmount ->
                "مجموع سهم شرکا (${NumberFormatter.formatPrice(partnerAmount + othersTotalShare, usePersian = usePersianDigits, compactThousands = compactThousands)}) از مبلغ کل قسط ماهانه (${NumberFormatter.formatPrice(rawMonthlyAmount, usePersian = usePersianDigits, compactThousands = compactThousands)}) بیشتر می‌شود؛ سهم کاربر مبنا نمی‌تواند منفی باشد."
            else -> null
        }

        AlertDialog(
            onDismissRequest = { showAddPartnerDialog = false },
            title = { Text("تعریف سهم شریک") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("انتخاب فرد:", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = { showCreateUserDialog = true }
                        ) {
                            Text("+ کاربر جدید", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // فیکس bug.md/NB-35 (اینوارینت #۱۱): فیلد آزاد «نام شریک» حذف شد —
                    // شریک فقط از میان کاربران تعریف‌شده انتخاب می‌شود (چیپ‌ها یا
                    // «+ کاربر جدید») تا سهمِ بی‌مالک (ownerPublicId == "") ممکن نباشد.
                    // فهرست هم LazyRow شد تا با زیادشدن شرکا از دیالوگ بیرون نزند.
                    if (partnerCandidates.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(partnerCandidates) { user ->
                                val isSelected = selectedUserName == user.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedUserName = user.name },
                                    label = { Text(user.name) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "هنوز کاربری غیر از کاربر مبنا تعریف نشده؛ با دکمهٔ «+ کاربر جدید» شریک را ثبت کنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // فیکس bug.md/NB-40: خطای «انتخاب فرد» همان‌جا زیر فهرست شرکا
                    partnerNameError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    PriceInputField(
                        rawDigitsValue = partnerAmountDigits,
                        onRawDigitsChange = { partnerAmountDigits = it },
                        label = "مبلغ سهم شریک (تومان)",
                        placeholder = "مثلاً: ۸۰۰,۰۰۰",
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands,
                        modifier = Modifier.fillMaxWidth(),
                        // فیکس bug.md/NB-40: خطای مبلغ سهم زیر همین فیلد با isError
                        isError = partnerAmountError != null,
                        errorText = partnerAmountError
                    )

                    OutlinedTextField(
                        value = partnerDesc,
                        onValueChange = { partnerDesc = it },
                        label = { Text("یادداشت / شماره تماس / کارت") },
                        placeholder = { Text("توضیحات اختیاری...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    // فیکس bug.md/NB-40: دکمهٔ ثبت تا رفع خطاهای زندهٔ بالا غیرفعال می‌ماند
                    enabled = partnerCandidates.isNotEmpty() &&
                        partnerNameError == null && partnerAmountError == null,
                    onClick = {
                        // فیکس bug.md/P1-8 — اعتبارسنجی سه‌لایه حالا «زنده» بالای همین
                        // دکمه ارزیابی می‌شود (NB-40) و اجازهٔ رسیدن به اینجا با دادهٔ
                        // نامعتبر را نمی‌دهد؛ دفاع نهایی در Repository است
                        // (OwnerlessShareException / DuplicateNameException).
                        //
                        // فیکس bug.md/NB-35 (اینوارینت #۱۱): هویت سهم = publicId کاربرِ
                        // انتخاب‌شده از فهرست (نه نام آزاد) تا پیوند سهم↔کاربر هویتی
                        // باشد؛ خالی فقط حالت transient است و Repository با fallback
                        // همان کاربر را resolve می‌کند (وگرنه کل ثبت را رد می‌کند).
                        val partnerPublicId = partnerCandidates
                            .firstOrNull { PersianNameNormalizer.namesMatch(it.name.trim(), partnerName) }
                            ?.publicId?.takeIf { it.isNotBlank() } ?: ""
                        sharesList.add(
                            DebtorShare(
                                loanId = loanToEdit?.id ?: 0L,
                                loanTitle = title.ifBlank { "وام جدید" },
                                personName = partnerName,
                                shareAmount = partnerAmount,
                                dueDay = startDate.day,
                                shareDescription = partnerDesc.trim(),
                                ownerPublicId = partnerPublicId
                            )
                        )
                        showAddPartnerDialog = false
                    }
                ) {
                    Text("ثبت سهم")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddPartnerDialog = false }) {
                    Text("انصراف")
                }
            }
        )

        // فیکس bug.md/NB-35: دیالوگ «کاربر جدید» به داخل دیالوگ شریک منتقل شد تا بعد
        // از ثبت، همان کاربرِ جدید به‌عنوان شریک انتخاب شود (نه انتخاب دوباره).
        if (showCreateUserDialog) {
            AddEditUserDialog(
                userToEdit = null,
                // فیکس bug.md/NB-36: چک یکتایی نام (NB-20) در دیالوگِ درونِ وام هم فعال است —
                // پیش از این، این فراخوانی بدون existingUsers بود و چک تکراری دور زده می‌شد.
                existingUsers = allUsers,
                onDismiss = { showCreateUserDialog = false },
                onSave = { newUser ->
                    onAddNewUser(newUser)
                    selectedUserName = newUser.name.trim()
                    showCreateUserDialog = false
                }
            )
        }
    }
}

package com.aistudio.vamyar.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.ui.components.InstallmentNodesView
import com.aistudio.vamyar.ui.components.PriceInputField
import com.aistudio.vamyar.util.InputLimits
import com.aistudio.vamyar.util.LoanIconDisplay
import com.aistudio.vamyar.util.LoanIconHelper
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.PersianNameNormalizer
import com.aistudio.vamyar.util.ShamsiDate
import com.aistudio.vamyar.util.ShamsiDateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLoanDialog(
    loanToEdit: Loan? = null,
    initialShares: List<DebtorShare> = emptyList(),
    allUsers: List<UserProfile> = emptyList(),
    // فیکس bug.md/NB-36: عناوین وام‌های موجود برای چک زندهٔ «عنوان تکراری» (self = ویرایش‌شونده)
    allLoanTitles: List<String> = emptyList(),
    currentShamsiDate: ShamsiDate,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onDismiss: () -> Unit,
    onAddNewUser: (UserProfile) -> Unit,
    onSave: (Loan, List<DebtorShare>) -> Unit
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
                    // If there was an older custom image, clean it up
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

    // فیکس bug.md/P1-5: «قسط رسیده» با «قسط پرداخت‌شده» یکی نیست.
    // autoElapsed فقط تعداد اقساطِ سررسیدرسیده را «نمایش» می‌دهد؛ paidMonths تنها با
    // تأیید صریح کاربر (دکمهٔ ثبتِ رسیده‌ها یا لمس دایرهٔای اقساط) تغییر می‌کند؛
    // افکت قدیمی که هنگام تغییر تاریخ شروع/مدت، paidMonths را بازنویسی می‌کرد حذف شد.
    var paidMonths by remember {
        mutableIntStateOf(loanToEdit?.paidMonths ?: 0)
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

    // فیکس bug.md/P1-9: کاهش «مدت وام» زیر «اقساط پرداخت‌شده» دیگر بی‌صدا ذخیره نمی‌شود؛
    // ذخیره مسدود و دلیلش به کاربر نمایش داده می‌شود تا داده‌ی ناسازگار ساخته نشود.
    val paidExceedsTotal = paidMonths > totalMonths

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LoanIconDisplay(
                            iconName = selectedIconName,
                            iconShape = selectedIconShape,
                            customIconPath = customIconPath,
                            tintColor = Color(selectedColor),
                            size = 40.dp,
                            iconSize = 22.dp
                        )
                        Column {
                            Text(
                                text = if (loanToEdit == null) "ثبت وام جدید" else "ویرایش وام و سهم‌ها",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مشخصات قسط، تاریخ شمسی، آیکون و سهم شرکا",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Title + Color Picker — فیکس bug.md/NB-32:
                    // فیلد نام (وزن‌دار به‌جای تمام‌عرض) و انتخاب رنگ وام (بخش ۸ قدیمی)
                    // در یک ردیف فشرده ادغام شدند تا ارتفاع دیالوگ کوتاه‌تر شود.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // فیکس bug.md/NB-36: خطای عنوان (خالی/طول/تکراری) زیر همان فیلد با
                        // isError نمایش داده می‌شود (الگوی AddEditUserDialog/NB-20).
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("نام بانک یا وام") },
                            placeholder = { Text("مثلاً: بانک رسالت") },
                            isError = titleError != null,
                            supportingText = { titleError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "رنگ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                colorOptions.forEach { colorVal ->
                                    val isSelected = selectedColor == colorVal
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorVal))
                                            .clickable { selectedColor = colorVal },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Icon, Shape & Gallery Image Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "نماد و ظاهر وام (آیکون و فرم نمایش):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Shape selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = selectedIconShape == "ROUNDED_SQUARE",
                                    onClick = { selectedIconShape = "ROUNDED_SQUARE" },
                                    label = { Text("مربع دور گرد مینیمال") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )

                                FilterChip(
                                    selected = selectedIconShape == "CIRCLE",
                                    onClick = { selectedIconShape = "CIRCLE" },
                                    label = { Text("دایره مینیمال") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            // Preset Vector Icons
                            Text("انتخاب از آیکون‌های پیش‌فرض:", style = MaterialTheme.typography.labelSmall)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(LoanIconHelper.PRESET_ICONS) { item ->
                                    val isSelected = selectedIconName == item.id && customIconPath == null
                                    Surface(
                                        shape = if (selectedIconShape == "CIRCLE") CircleShape else RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(selectedColor).copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                                        border = if (isSelected) BorderStroke(1.5.dp, Color(selectedColor)) else null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clickable {
                                                selectedIconName = item.id
                                                customIconPath = null // Clear custom image if preset selected
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.titleFa,
                                                tint = if (isSelected) Color(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Custom Gallery / Logo Picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (customIconPath != null) "تغییر تصویر از گالری" else "انتخاب لوگو/عکس از گالری",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                if (customIconPath != null) {
                                    TextButton(
                                        onClick = {
                                            LoanIconHelper.deleteCustomIconFile(customIconPath)
                                            customIconPath = null
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حذف تصویر", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // 3+4. تاریخ شروع + مبلغ قسط — فیکس bug.md/NB-32:
                    // هر دو فیلد به‌جای دو ردیف تمام‌عرض، در یک ردیف با وزن مساوی قرار گرفتند.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startDate.toStandardFormat(usePersianDigits = usePersianDigits),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("تاریخ شروع وام") },
                            supportingText = {
                                Text("سررسید هر ماه: ${NumberFormatter.formatNumber(startDate.day, usePersianDigits)}ام")
                            },
                            trailingIcon = {
                                IconButton(onClick = { showCalendarPicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "انتخاب تاریخ",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showCalendarPicker = true }
                        )

                        // Stable Price Input Field (بدون پرش کرسر و بدون خطا)
                        PriceInputField(
                            rawDigitsValue = monthlyAmountDigits,
                            onRawDigitsChange = { monthlyAmountDigits = it },
                            label = "مبلغ قسط ماهانه (تومان)",
                            placeholder = "مثلاً: ۲,۸۲۲,۰۰۰",
                            usePersianDigits = usePersianDigits,
                            compactThousands = compactThousands,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 5. Total Installment Count (Quick Chips + Input)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تعداد کل اقساط (ماه):", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = totalMonthsText,
                                onValueChange = { totalMonthsText = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        // فیکس bug.md/NB-33: خطای صریح بازهٔ ۱..۱۲۰ با بازخورد زنده —
                        // جایگزین clamp بی‌صدای P3-12؛ ذخیره تا اصلاح کاربر مسدود می‌ماند.
                        durationError?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // Quick chips for common loan durations
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(6, 10, 12, 24, 36).forEach { duration ->
                                val isSelected = totalMonths == duration
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { totalMonthsText = duration.toString() },
                                    label = { Text("${NumberFormatter.formatNumber(duration, usePersianDigits)} ماهه") },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // 6. Interactive Installment Circles & Auto-Detection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "اقساط پرداخت‌شده (ثبت‌شده توسط شما):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "اقساط رسیده تا امروز: ${NumberFormatter.formatNumber(autoElapsed, usePersianDigits)} قسط (${currentShamsiDate.monthName} ${currentShamsiDate.year}) — صرفاً محاسبهٔ سررسید؛ پرداخت‌شده‌ها را جداگانه ثبت کنید",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${NumberFormatter.formatNumber(paidMonths, usePersianDigits)} از ${NumberFormatter.formatNumber(totalMonths, usePersianDigits)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Quick sync chip button
                            if (paidMonths != autoElapsed) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { paidMonths = autoElapsed },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ثبت ${autoElapsed} قسط رسیده به‌عنوان پرداخت‌شده", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Interactive Nodes View inside dialog
                            InstallmentNodesView(
                                totalMonths = totalMonths,
                                paidMonths = paidMonths,
                                startYear = startDate.year,
                                startMonth = startDate.month,
                                accentColor = Color(selectedColor),
                                usePersianDigits = usePersianDigits,
                                onNodeClick = { clickedInstallment ->
                                    paidMonths = if (paidMonths == clickedInstallment) {
                                        clickedInstallment - 1
                                    } else {
                                        clickedInstallment
                                    }
                                }
                            )

                            Text(
                                text = "💡 با لمس هر دایره ماه، می‌توانید وضعیت پرداخت اقساط را کم یا زیاد کنید.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )

                            // فیکس bug.md/P1-9: هشدار ناسازگاریِ «اقساط پرداخت‌شده > کل اقساط»
                            if (paidExceedsTotal) {
                                Text(
                                    text = "⚠️ تعداد اقساط پرداخت‌شده (${NumberFormatter.formatNumber(paidMonths, usePersianDigits)}) از کل اقساط (${NumberFormatter.formatNumber(totalMonths, usePersianDigits)}) بیشتر است؛ مدت وام را بیشتر کنید یا با لمس دایره‌ها اقساط پرداخت‌شده را کاهش دهید.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // 7. Partners Management (بدون پرانتزهای اضافه)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "شراکت در قسط",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "افزودن سهم دوستان یا خانواده",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { showAddPartnerDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ افزودن شریک", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            if (sharesList.isEmpty()) {
                                Text(
                                    text = "این وام بدون شریک است و کل قسط متعلق به کاربر مبنا است.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                sharesList.forEachIndexed { index, share ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${share.personName}: ${NumberFormatter.formatPrice(share.shareAmount, usePersianDigits, compactThousands)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (share.shareDescription.isNotBlank()) {
                                                Text(share.shareDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        IconButton(onClick = { sharesList.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            // Baseline Share Indicator (بدون پرانتزهای اضافی)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("سهم کاربر مبنا:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = NumberFormatter.formatPrice(calculatedMyShare, usePersianDigits, compactThousands),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // فیکس bug.md/P1-8(الف): سهم صفرِ بی‌صدا دیگر ممکن نیست —
                            // اگر مجموع سهم شرکا از مبلغ قسط بیشتر شده (مثلاً با کم‌کردن مبلغ قسط)،
                            // ذخیره‌ی وام مسدود و دلیلش نمایش داده می‌شود.
                            if (sharesExceedMonthly) {
                                Text(
                                    text = "⚠️ مجموع سهم شرکا (${NumberFormatter.formatPrice(othersTotalShare, usePersianDigits, compactThousands)}) از مبلغ کل قسط ماهانه (${NumberFormatter.formatPrice(rawMonthlyAmount, usePersianDigits, compactThousands)}) بیشتر است؛ مبلغ قسط را افزایش دهید یا سهم شرکا را کم/حذف کنید.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // 8. Color Picker — فیکس bug.md/NB-32: به داخل ردیف فیلد نام منتقل شد (بخش ۱)
                }

                // فیکس bug.md/NB-36: خطای عنوان به زیر خودِ فیلد (بالا) منتقل شد؛
                // بلوک خطای «مبلغ قسط صفر» تا NB-40 همین‌جا می‌ماند.
                if (rawMonthlyAmount <= 0L) {
                    Text(
                        text = "مبلغ قسط ماهانه باید بزرگ‌تر از صفر باشد.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Bottom Save & Cancel Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("انصراف")
                    }

                    Button(
                        // فیکس bug.md/P1-8/P1-9 و P3-12 و NB-33: ذخیره برای عنوان/مدت نامعتبر یا قسط صفر مسدود است
                        enabled = !sharesExceedMonthly && !paidExceedsTotal && titleError == null && durationError == null && rawMonthlyAmount > 0L,
                        onClick = {
                            // فیکس bug.md/P1-9: شمارنده‌ی پرداخت‌شده‌ها همیشه درون بازه‌ی
                            // 0..totalMonths نگه داشته می‌شود (دکمه در حالت ناسازگار غیرفعال است
                            // و این coerceIn صرفاً دفاع در عمق است)…
                            val effectivePaidMonths = paidMonths.coerceIn(0, totalMonths)
                            // …و پرچمِ «پرداخت ماه جاری» با ویرایشِ کاربر روی شمارنده sync می‌شود:
                            // اگر کاربر شمارنده را تغییر داده باشد، مقدار قطعی از مقایسه‌ی پرداخت‌شده‌ها
                            // با اقساطِ رسیده/کل اقساط محاسبه می‌شود؛ اگر فقط مشخصات دیگر را ویرایش کرده
                            // باشد، پرچم فعلی وام دست‌نخورده می‌ماند.
                            val paidCountChanged = effectivePaidMonths != (loanToEdit?.paidMonths ?: 0)
                            val effectiveCurrentMonthPaid = when {
                                effectivePaidMonths >= totalMonths -> true // وام تسویه شده است
                                paidCountChanged -> autoElapsed > 0 && effectivePaidMonths >= autoElapsed
                                else -> loanToEdit?.isCurrentMonthPaid ?: false
                            }
                            val loan = (loanToEdit ?: Loan(
                                title = title.ifBlank { "وام جدید" },
                                dueDay = startDate.day,
                                monthlyAmount = rawMonthlyAmount,
                                totalMonths = totalMonths,
                                paidMonths = effectivePaidMonths
                            )).copy(
                                title = title.ifBlank { "وام جدید" },
                                dueDay = startDate.day,
                                monthlyAmount = rawMonthlyAmount,
                                totalMonths = totalMonths,
                                paidMonths = effectivePaidMonths,
                                isCurrentMonthPaid = effectiveCurrentMonthPaid,
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

                            onSave(loan, sharesList)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ذخیره وام")
                    }
                }
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
        // فیکس bug.md/P1-8: پیام خطای اعتبارسنجی سهم شریک (به‌جای بی‌صدا بی‌اثر ماندن دکمه)
        var partnerError by remember { mutableStateOf<String?>(null) }

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

                    PriceInputField(
                        rawDigitsValue = partnerAmountDigits,
                        onRawDigitsChange = { partnerAmountDigits = it },
                        label = "مبلغ سهم شریک (تومان)",
                        placeholder = "مثلاً: ۸۰۰,۰۰۰",
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = partnerDesc,
                        onValueChange = { partnerDesc = it },
                        label = { Text("یادداشت / شماره تماس / کارت") },
                        placeholder = { Text("توضیحات اختیاری...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // فیکس bug.md/P1-8: نمایش دلیل رد شدنِ ثبت سهم به‌جای سکوت
                    partnerError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = partnerAmountDigits.toLongOrNull() ?: 0L
                        val partnerName = selectedUserName.trim()
                        // فیکس bug.md/P1-8 — اعتبارسنجی سه‌لایه:
                        // (ج) کاربر مبنا نمی‌تواند شریکِ وام خودش شود (شمارش دوبرابر)،
                        // (ب) یک نفر نمی‌تواند دو بار شریکِ همان وام شود،
                        // (الف) مجموع سهم شرکا نمی‌تواند از مبلغ کل قسط ماهانه بیشتر شود
                        //      (قبلاً سهم کاربر مبنا بی‌صدا صفر می‌شد).
                        partnerError = when {
                            partnerName.isBlank() ->
                                "نام شریک را انتخاب کنید."
                            // فیکس bug.md/NB-35 (اینوارینت #۱۱): شریک باید کاربر تعریف‌شده
                            // باشد — با حذف فیلد آزاد، این چک دفاع نمایشیِ همان قاعده است.
                            !InputLimits.isDefinedPartner(partnerName, partnerCandidates.map { it.name }) ->
                                "شریک باید از کاربران تعریف‌شده انتخاب شود؛ ابتدا با دکمهٔ «+ کاربر جدید» ثبتش کنید."
                            // فیکس bug.md/NB-21 — مقایسهٔ نام با namesMatch (نه equals):
                            // «علی» و «علي» (ي/ك عربی) و فاصله‌های زائد یکسان شمرده می‌شوند
                            primaryUserName != null && PersianNameNormalizer.namesMatch(partnerName, primaryUserName) ->
                                "«$partnerName» کاربر مبناست؛ سهم او به‌صورت خودکار از باقی‌مانده‌ی قسط محاسبه می‌شود و نمی‌تواند شریکِ همین وام ثبت شود."
                            sharesList.any { PersianNameNormalizer.namesMatch(it.personName.trim(), partnerName) } ->
                                "«$partnerName» قبلاً شریکِ همین وام ثبت شده است؛ برای تغییر مبلغ، سهم قبلی را حذف و دوباره ثبت کنید."
                            amount <= 0L ->
                                "مبلغ سهم شریک باید بزرگ‌تر از صفر باشد."
                            amount + othersTotalShare > rawMonthlyAmount ->
                                "مجموع سهم شرکا (${NumberFormatter.formatPrice(amount + othersTotalShare, usePersianDigits, compactThousands)}) از مبلغ کل قسط ماهانه (${NumberFormatter.formatPrice(rawMonthlyAmount, usePersianDigits, compactThousands)}) بیشتر می‌شود؛ سهم کاربر مبنا نمی‌تواند منفی باشد."
                            else -> null
                        }
                        if (partnerError == null) {
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
                                    shareAmount = amount,
                                    dueDay = startDate.day,
                                    shareDescription = partnerDesc.trim(),
                                    ownerPublicId = partnerPublicId
                                )
                            )
                            showAddPartnerDialog = false
                        }
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


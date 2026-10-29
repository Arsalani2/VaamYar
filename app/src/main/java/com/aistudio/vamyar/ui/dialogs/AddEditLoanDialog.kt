package com.aistudio.vamyar.ui.dialogs

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
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

/**
 * بازطراحی صفحه ثبت وام جدید بر مبنای دیزاین سیستم مینیمال ۳ مرحله‌ای (Stepper):
 * - گام ۱: اطلاعات مالی وام (مبلغ هر قسط، تعداد اقساط و تاریخ اولین سررسید)
 * - گام ۲: نام و نماد وام (عنوان، پیش‌نمایش آیکون، انتخاب شکل، گالری و پالت رنگی)
 * - گام ۳: شراکت و تایید نهایی (حالت تک‌نفره/چندنفره، سهم شرکا، خلاصه بازپرداخت و جدول اقساط)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLoanDialog(
    loanToEdit: Loan? = null,
    initialShares: List<DebtorShare> = emptyList(),
    initialInstallments: List<LoanInstallment> = emptyList(),
    allUsers: List<UserProfile> = emptyList(),
    allLoanTitles: List<String> = emptyList(),
    currentShamsiDate: ShamsiDate,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onDismiss: () -> Unit,
    onAddNewUser: (UserProfile) -> Unit,
    onSave: (Loan, List<DebtorShare>, Set<Pair<Int, Int>>) -> Unit
) {
    val context = LocalContext.current
    val iconScope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) }

    // State: Title, duration, color
    var title by remember { mutableStateOf(loanToEdit?.title ?: "") }
    var totalMonthsText by remember { mutableStateOf(loanToEdit?.totalMonths?.toString() ?: "12") }
    var selectedColor by remember { mutableLongStateOf(loanToEdit?.colorHex ?: 0xFF00685FL) }

    // State: Icon & Shape & Custom Image
    var selectedIconName by remember { mutableStateOf(loanToEdit?.iconName ?: "account_balance") }
    var selectedIconShape by remember { mutableStateOf(loanToEdit?.iconShape ?: "ROUNDED_SQUARE") }
    var customIconPath by remember { mutableStateOf(loanToEdit?.customIconPath) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            iconScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    LoanIconHelper.saveCustomIconImage(context, uri)
                }
                if (saved != null) {
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

    // Monthly Amount Digits
    var monthlyAmountDigits by remember {
        mutableStateOf(
            if (loanToEdit != null && loanToEdit.monthlyAmount > 0)
                loanToEdit.monthlyAmount.toString()
            else ""
        )
    }

    val rawMonthlyAmount = monthlyAmountDigits.toLongOrNull() ?: 0L

    val totalMonths = remember(totalMonthsText) {
        NumberFormatter.parseInt(totalMonthsText).coerceIn(1, 120)
    }

    val durationError = InputLimits.validateDurationMonths(totalMonthsText)
    val titleError = InputLimits.validateTitle(title)
        ?: InputLimits.validateUniqueTitle(
            raw = title,
            existingTitles = allLoanTitles,
            selfKey = loanToEdit?.let { PersianNameNormalizer.matchKey(it.title) }
        )

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

    var paidYearMonths by remember {
        mutableStateOf(initialInstallments.map { it.year to it.month }.toSet())
    }

    // Partnership Shares
    val sharesList = remember {
        mutableStateListOf<DebtorShare>().apply {
            addAll(initialShares)
        }
    }

    var isSharedMode by remember {
        mutableStateOf(sharesList.isNotEmpty() || (loanToEdit?.hasPartners == true))
    }

    var showAddPartnerDialog by remember { mutableStateOf(false) }
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var showScheduleTable by remember { mutableStateOf(false) }

    val hasPartners = isSharedMode && sharesList.isNotEmpty()
    val othersTotalShare = if (isSharedMode) sharesList.sumOf { it.shareAmount } else 0L
    val calculatedMyShare = if (hasPartners) {
        (rawMonthlyAmount - othersTotalShare).coerceAtLeast(0L)
    } else {
        rawMonthlyAmount
    }

    val sharesExceedMonthly = hasPartners && othersTotalShare > rawMonthlyAmount
    val primaryUserName = allUsers.firstOrNull { it.isPrimary }?.name?.trim()

    val overdueYearMonths = remember(totalMonths, startDate.year, startDate.month, currentShamsiDate.year, currentShamsiDate.month, paidYearMonths) {
        val curTotal = currentShamsiDate.year * 12 + (currentShamsiDate.month - 1)
        (1..totalMonths).mapNotNull { k ->
            val m = ShamsiDateHelper.installmentMonth(startDate.year, startDate.month, k)
            val key = m.year to m.month
            if (m.year * 12 + (m.month - 1) < curTotal && key !in paidYearMonths) key else null
        }.toSet()
    }

    val unpaidArrivedMonths = remember(autoElapsed, startDate.year, startDate.month, paidYearMonths) {
        (1..autoElapsed).mapNotNull { k ->
            val m = ShamsiDateHelper.installmentMonth(startDate.year, startDate.month, k)
            val key = m.year to m.month
            if (key !in paidYearMonths) key else null
        }.toSet()
    }

    val paidInRangeCount = paidYearMonths.count { (y, m) ->
        val n = ShamsiDateHelper.installmentNumberForMonth(startDate.year, startDate.month, y, m)
        n != null && n in 1..totalMonths
    }

    val endDate = remember(startDate, totalMonths) {
        ShamsiDateHelper.calculateEndDate(startDate.year, startDate.month, totalMonths)
    }

    // Modern 6 Minimal Palettes from Design Tokens + Presets
    val paletteOptions = listOf(
        0xFF00685FL to "زمردی",
        0xFF283044L to "سرمه‌ای",
        0xFFB45309L to "کهربایی",
        0xFFB90538L to "یاقوتی",
        0xFF4338CAL to "نیلی",
        0xFF475569L to "دودی",
        0xFF1976D2L to "آبی"
    )

    BackHandler {
        if (currentStep > 1) {
            currentStep--
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                // Header (Top Navigation Anchor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = if (loanToEdit == null) "ثبت وام جدید" else "ویرایش وام",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = LoanIconHelper.getIconVector(selectedIconName),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                // Stepper Header Bar
                StepperIndicator(
                    currentStep = currentStep,
                    usePersianDigits = usePersianDigits
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Animated Multi-Step Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(animationSpec = tween(280)) { width -> -width } + fadeIn(animationSpec = tween(280))).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(280)) { width -> width } + fadeOut(animationSpec = tween(280))
                                )
                            } else {
                                (slideInHorizontally(animationSpec = tween(280)) { width -> width } + fadeIn(animationSpec = tween(280))).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(280)) { width -> -width } + fadeOut(animationSpec = tween(280))
                                )
                            }
                        },
                        label = "AddLoanStepAnimation"
                    ) { step ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (step) {
                                1 -> {
                                    StepOneFinancialContent(
                                        monthlyAmountDigits = monthlyAmountDigits,
                                        onMonthlyAmountDigitsChange = { monthlyAmountDigits = it },
                                        rawMonthlyAmount = rawMonthlyAmount,
                                        totalMonthsText = totalMonthsText,
                                        onTotalMonthsTextChange = { totalMonthsText = it },
                                        totalMonths = totalMonths,
                                        durationError = durationError,
                                        startDate = startDate,
                                        onOpenCalendarPicker = { showCalendarPicker = true },
                                        usePersianDigits = usePersianDigits,
                                        compactThousands = compactThousands
                                    )
                                }
                                2 -> {
                                    StepTwoEmblemContent(
                                        title = title,
                                        onTitleChange = { title = it },
                                        titleError = titleError,
                                        selectedIconName = selectedIconName,
                                        onSelectIconName = {
                                            selectedIconName = it
                                            customIconPath = null
                                        },
                                        selectedIconShape = selectedIconShape,
                                        onSelectIconShape = { selectedIconShape = it },
                                        customIconPath = customIconPath,
                                        onPickFromGallery = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onDeleteCustomIcon = {
                                            LoanIconHelper.deleteCustomIconFile(customIconPath)
                                            customIconPath = null
                                        },
                                        selectedColor = selectedColor,
                                        onSelectColor = { selectedColor = it },
                                        paletteOptions = paletteOptions
                                    )
                                }
                                3 -> {
                                    StepThreeConfirmationContent(
                                        title = title,
                                        rawMonthlyAmount = rawMonthlyAmount,
                                        totalMonths = totalMonths,
                                        endDate = endDate,
                                        isSharedMode = isSharedMode,
                                        onToggleSharedMode = { isSharedMode = it },
                                        sharesList = sharesList,
                                        calculatedMyShare = calculatedMyShare,
                                        othersTotalShare = othersTotalShare,
                                        sharesExceedMonthly = sharesExceedMonthly,
                                        onAddPartnerClick = { showAddPartnerDialog = true },
                                        onRemovePartner = { index -> sharesList.removeAt(index) },
                                        showScheduleTable = showScheduleTable,
                                        onToggleScheduleTable = { showScheduleTable = !showScheduleTable },
                                        paidInRangeCount = paidInRangeCount,
                                        startDate = startDate,
                                        selectedColor = selectedColor,
                                        currentShamsiDate = currentShamsiDate,
                                        paidYearMonths = paidYearMonths,
                                        overdueYearMonths = overdueYearMonths,
                                        unpaidArrivedMonths = unpaidArrivedMonths,
                                        onNodeClick = { clickedInstallment ->
                                            val m = ShamsiDateHelper.installmentMonth(startDate.year, startDate.month, clickedInstallment)
                                            val key = m.year to m.month
                                            paidYearMonths = if (key in paidYearMonths) paidYearMonths - key else paidYearMonths + key
                                        },
                                        onMarkAllUnpaidArrived = {
                                            paidYearMonths = paidYearMonths + unpaidArrivedMonths
                                        },
                                        usePersianDigits = usePersianDigits,
                                        compactThousands = compactThousands
                                    )
                                }
                            }
                        }
                    }
                }

                // Sticky Bottom Action Footer
                HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (currentStep) {
                        1 -> {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("انصراف و خروج")
                            }

                            Button(
                                onClick = { currentStep = 2 },
                                enabled = rawMonthlyAmount > 0L && durationError == null,
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("مرحله بعد", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        2 -> {
                            OutlinedButton(
                                onClick = { currentStep = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بازگشت")
                            }

                            Button(
                                onClick = { currentStep = 3 },
                                enabled = titleError == null && title.trim().isNotBlank(),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("مرحله بعد", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        3 -> {
                            OutlinedButton(
                                onClick = { currentStep = 2 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بازگشت")
                            }

                            Button(
                                enabled = !sharesExceedMonthly && titleError == null && durationError == null && rawMonthlyAmount > 0L && title.isNotBlank(),
                                onClick = {
                                    val finalShares = if (isSharedMode) sharesList.toList() else emptyList()
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
                                        myShareAmount = if (isSharedMode) calculatedMyShare else rawMonthlyAmount,
                                        hasPartners = isSharedMode && finalShares.isNotEmpty(),
                                        colorHex = selectedColor,
                                        iconName = selectedIconName,
                                        iconShape = selectedIconShape,
                                        customIconPath = customIconPath
                                    )

                                    onSave(loan, finalShares, paidYearMonths)
                                },
                                modifier = Modifier
                                    .weight(1.8f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ذخیره و ثبت نهایی وام", fontWeight = FontWeight.Bold)
                            }
                        }
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
            }
        )
    }

    // Add Partner Modal Dialog
    if (showAddPartnerDialog) {
        val partnerCandidates = allUsers.filter { !it.isPrimary }
        var selectedUserName by remember { mutableStateOf(partnerCandidates.firstOrNull()?.name ?: "") }
        var partnerAmountDigits by remember { mutableStateOf("") }
        var partnerDesc by remember { mutableStateOf("") }

        val partnerName = selectedUserName.trim()
        val partnerAmount = partnerAmountDigits.toLongOrNull() ?: 0L
        val partnerNameError: String? = when {
            partnerCandidates.isEmpty() -> null
            partnerName.isBlank() -> "نام شریک را انتخاب کنید."
            !InputLimits.isDefinedPartner(partnerName, partnerCandidates.map { it.name }) ->
                "شریک باید از کاربران تعریف‌شده انتخاب شود؛ ابتدا با دکمهٔ «+ کاربر جدید» ثبتش کنید."
            primaryUserName != null && PersianNameNormalizer.namesMatch(partnerName, primaryUserName) ->
                "«$partnerName» کاربر مبناست؛ سهم او به‌صورت خودکار از باقی‌مانده‌ی قسط محاسبه می‌شود و نمی‌تواند شریکِ همین وام ثبت شود."
            sharesList.any { PersianNameNormalizer.namesMatch(it.personName.trim(), partnerName) } ->
                "«$partnerName» قبلاً شریکِ همین وام ثبت شده است؛ برای تغییر مبلغ، سهم قبلی را حذف و دوباره ثبت کنید."
            else -> null
        }
        val partnerAmountError: String? = when {
            partnerAmount <= 0L -> "مبلغ سهم شریک باید بزرگ‌تر از صفر باشد."
            partnerAmount + othersTotalShare > rawMonthlyAmount ->
                "مجموع سهم شرکا (${NumberFormatter.formatPrice(partnerAmount + othersTotalShare, usePersianDigits, compactThousands)}) از مبلغ کل قسط ماهانه (${NumberFormatter.formatPrice(rawMonthlyAmount, usePersianDigits, compactThousands)}) بیشتر می‌شود؛ سهم کاربر مبنا نمی‌تواند منفی باشد."
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
                        TextButton(onClick = { showCreateUserDialog = true }) {
                            Text("+ کاربر جدید", style = MaterialTheme.typography.labelSmall)
                        }
                    }

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
                        isError = partnerAmountError != null,
                        errorText = partnerAmountError
                    )

                    OutlinedTextField(
                        value = partnerDesc,
                        onValueChange = { partnerDesc = it },
                        label = { Text("یادداشت / توضیحات") },
                        placeholder = { Text("توضیحات اختیاری...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = partnerCandidates.isNotEmpty() && partnerNameError == null && partnerAmountError == null,
                    onClick = {
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

        if (showCreateUserDialog) {
            AddEditUserDialog(
                userToEdit = null,
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

/**
 * نوار نشانگر پیشرفت ۳ مرحله‌ای (Stepper)
 */
@Composable
private fun StepperIndicator(
    currentStep: Int,
    usePersianDigits: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "گام ${NumberFormatter.formatNumber(currentStep, usePersianDigits)} از ${NumberFormatter.formatNumber(3, usePersianDigits)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = when (currentStep) {
                    1 -> "اطلاعات مالی"
                    2 -> "نام و نماد"
                    else -> "شراکت و تایید"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3 Segmented Progress Bars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (step in 1..3) {
                val isActive = currentStep >= step
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

/**
 * گام ۱: اطلاعات مالی وام (Financial Details)
 */
@Composable
private fun StepOneFinancialContent(
    monthlyAmountDigits: String,
    onMonthlyAmountDigitsChange: (String) -> Unit,
    rawMonthlyAmount: Long,
    totalMonthsText: String,
    onTotalMonthsTextChange: (String) -> Unit,
    totalMonths: Int,
    durationError: String?,
    startDate: ShamsiDate,
    onOpenCalendarPicker: () -> Unit,
    usePersianDigits: Boolean,
    compactThousands: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Step Title
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "اطلاعات مالی وام",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "مبلغ، تعداد اقساط و موعد اولین سررسید را مشخص کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Amount Per Installment Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "مبلغ هر قسط",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                PriceInputField(
                    rawDigitsValue = monthlyAmountDigits,
                    onRawDigitsChange = onMonthlyAmountDigitsChange,
                    label = "مبلغ قسط ماهانه (تومان)",
                    placeholder = "۰",
                    usePersianDigits = usePersianDigits,
                    compactThousands = compactThousands,
                    modifier = Modifier.fillMaxWidth(),
                    isError = rawMonthlyAmount <= 0L && monthlyAmountDigits.isNotBlank(),
                    errorText = if (rawMonthlyAmount <= 0L && monthlyAmountDigits.isNotBlank()) {
                        "مبلغ قسط ماهانه باید بزرگ‌تر از صفر باشد."
                    } else null
                )

                if (rawMonthlyAmount > 0L) {
                    Text(
                        text = "معادل: ${NumberFormatter.formatNumber(rawMonthlyAmount * 10L, usePersianDigits)} ریال",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Quick Suggestion Pills
                Text(
                    text = "مبالغ پیشنهادی سریع:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L, 20_000_000L)
                    items(presets) { preset ->
                        val isSelected = rawMonthlyAmount == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onMonthlyAmountDigitsChange(preset.toString()) },
                            label = {
                                Text("${NumberFormatter.formatNumber(preset, usePersianDigits)} تومان")
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Section 2: Number of Installments Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تعداد اقساط",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${NumberFormatter.formatNumber(totalMonths, usePersianDigits)} قسط ماهانه",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Segmented duration chips (6, 12, 24, 36)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(6, 12, 24, 36).forEach { duration ->
                        val isSelected = totalMonths == duration
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onTotalMonthsTextChange(duration.toString()) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${NumberFormatter.formatNumber(duration, usePersianDigits)} ماه",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Custom duration text field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تعداد دلخواه (ماه):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = totalMonthsText,
                        onValueChange = { onTotalMonthsTextChange(it.filter { c -> c.isDigit() }) },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        isError = durationError != null,
                        supportingText = { durationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }

        // Section 3: First Due Date Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "تاریخ اولین قسط",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenCalendarPicker() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = startDate.formatted(usePersianDigits),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "شروع بازپرداخت منظم (سررسید روز ${NumberFormatter.formatNumber(startDate.day, usePersianDigits)}ام هر ماه)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        IconButton(onClick = onOpenCalendarPicker) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "تغییر تاریخ",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * گام ۲: نام و نماد وام (Loan Name & Symbol)
 */
@Composable
private fun StepTwoEmblemContent(
    title: String,
    onTitleChange: (String) -> Unit,
    titleError: String?,
    selectedIconName: String,
    onSelectIconName: (String) -> Unit,
    selectedIconShape: String,
    onSelectIconShape: (String) -> Unit,
    customIconPath: String?,
    onPickFromGallery: () -> Unit,
    onDeleteCustomIcon: () -> Unit,
    selectedColor: Long,
    onSelectColor: (Long) -> Unit,
    paletteOptions: List<Pair<Long, String>>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "نام و نماد وام",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "نام وام، نماد و رنگ اختصاصی را تعیین کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Title Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "نام بانک یا وام",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("نام وام") },
                    placeholder = { Text("مثال: وام مسکن، وام قرض‌الحسنه خانواده") },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    isError = titleError != null,
                    supportingText = { titleError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Section 2: Visual Emblem Preview & Icon Picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نماد و ظاهر وام",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Shape selector chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedIconShape == "ROUNDED_SQUARE",
                            onClick = { onSelectIconShape("ROUNDED_SQUARE") },
                            label = { Text("مربع گرد", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        FilterChip(
                            selected = selectedIconShape == "CIRCLE",
                            onClick = { onSelectIconShape("CIRCLE") },
                            label = { Text("دایره", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Hero Preview Badge
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoanIconDisplay(
                        iconName = selectedIconName,
                        iconShape = selectedIconShape,
                        customIconPath = customIconPath,
                        tintColor = Color(selectedColor),
                        size = 80.dp,
                        iconSize = 42.dp
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Preset Categorized Icons
                Text(
                    text = "دسته‌بندی‌های پیشنهادی:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(LoanIconHelper.PRESET_ICONS) { item ->
                        val isSelected = selectedIconName == item.id && customIconPath == null
                        Surface(
                            shape = if (selectedIconShape == "CIRCLE") CircleShape else RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(selectedColor).copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) BorderStroke(2.dp, Color(selectedColor)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .size(46.dp)
                                .clickable { onSelectIconName(item.id) }
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

                // Gallery Upload Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPickFromGallery,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (customIconPath != null) "تغییر تصویر از گالری" else "انتخاب از گالری",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (customIconPath != null) {
                        TextButton(onClick = onDeleteCustomIcon) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف تصویر", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Section 3: Color Palette Picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "پالت رنگی وام",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    paletteOptions.forEach { (colorVal, colorLabel) ->
                        val isSelected = selectedColor == colorVal
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectColor(colorVal) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = colorLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * گام ۳: تایید نهایی و شراکت (Confirmation & Partnership)
 */
@Composable
private fun StepThreeConfirmationContent(
    title: String,
    rawMonthlyAmount: Long,
    totalMonths: Int,
    endDate: ShamsiDate,
    isSharedMode: Boolean,
    onToggleSharedMode: (Boolean) -> Unit,
    sharesList: List<DebtorShare>,
    calculatedMyShare: Long,
    othersTotalShare: Long,
    sharesExceedMonthly: Boolean,
    onAddPartnerClick: () -> Unit,
    onRemovePartner: (Int) -> Unit,
    showScheduleTable: Boolean,
    onToggleScheduleTable: () -> Unit,
    paidInRangeCount: Int,
    startDate: ShamsiDate,
    selectedColor: Long,
    currentShamsiDate: ShamsiDate,
    paidYearMonths: Set<Pair<Int, Int>>,
    overdueYearMonths: Set<Pair<Int, Int>>,
    unpaidArrivedMonths: Set<Pair<Int, Int>>,
    onNodeClick: (Int) -> Unit,
    onMarkAllUnpaidArrived: () -> Unit,
    usePersianDigits: Boolean,
    compactThousands: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "تایید نهایی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "مشخصات نهایی بازپرداخت و سهم‌بندی اقساط را بررسی کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Partnership Management Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "شریک",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Toggle: تک‌نفره / چندنفره
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (!isSharedMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.clickable { onToggleSharedMode(false) }
                            ) {
                                Text(
                                    text = "تک‌نفره",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (!isSharedMode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isSharedMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSharedMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.clickable { onToggleSharedMode(true) }
                            ) {
                                Text(
                                    text = "چندنفره",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSharedMode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSharedMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (isSharedMode) {
                    val userSharePercent = if (rawMonthlyAmount > 0L) {
                        ((calculatedMyShare.toDouble() / rawMonthlyAmount) * 100).toInt().coerceIn(0, 100)
                    } else 100

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "من",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }

                                Column {
                                    Text(
                                        text = "سهم شما (${NumberFormatter.formatNumber(userSharePercent, usePersianDigits)}٪):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = NumberFormatter.formatPrice(calculatedMyShare, usePersianDigits, compactThousands),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onAddPartnerClick,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ افزودن شریک", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (sharesList.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            sharesList.forEachIndexed { index, share ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                                Text(
                                                    text = share.shareDescription,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { onRemovePartner(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف شریک",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (sharesExceedMonthly) {
                        Text(
                            text = "⚠️ مجموع سهم شرکا (${NumberFormatter.formatPrice(othersTotalShare, usePersianDigits, compactThousands)}) از مبلغ کل قسط ماهانه (${NumberFormatter.formatPrice(rawMonthlyAmount, usePersianDigits, compactThousands)}) بیشتر است.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = "وام در حالت تک‌نفره است و کل بازپرداخت اقساط توسط شما انجام می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 2: Loan Repayment Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "خلاصه بازپرداخت",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${title.ifBlank { "وام جدید" }} · قرض‌الحسنه",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 3-Column Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("مبلغ هر قسط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${NumberFormatter.formatNumber(rawMonthlyAmount, usePersianDigits)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("تومان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("مدت اقساط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${NumberFormatter.formatNumber(totalMonths, usePersianDigits)} ماهه",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("پایان اقساط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${endDate.monthName} ${NumberFormatter.formatNumber(endDate.year, usePersianDigits)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

                // Toggle Full Schedule Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleScheduleTable() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showScheduleTable) "بستن جدول اقساط" else "مشاهده جدول کامل اقساط",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showScheduleTable) Icons.Default.ExpandLess else Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Expandable Interactive Schedule Table
                AnimatedVisibility(
                    visible = showScheduleTable,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "اقساط پرداخت‌شده:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${NumberFormatter.formatNumber(paidInRangeCount, usePersianDigits)} از ${NumberFormatter.formatNumber(totalMonths, usePersianDigits)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (unpaidArrivedMonths.isNotEmpty()) {
                            TextButton(
                                onClick = onMarkAllUnpaidArrived,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ثبت همهٔ ${NumberFormatter.formatNumber(unpaidArrivedMonths.size, usePersianDigits)} قسط رسیدهٔ پرداخت‌نشده", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        InstallmentNodesView(
                            totalMonths = totalMonths,
                            paidMonths = paidInRangeCount,
                            startYear = startDate.year,
                            startMonth = startDate.month,
                            accentColor = Color(selectedColor),
                            usePersianDigits = usePersianDigits,
                            paidYearMonths = paidYearMonths,
                            overdueYearMonths = overdueYearMonths,
                            currentYearMonth = currentShamsiDate.year to currentShamsiDate.month,
                            onNodeClick = onNodeClick
                        )
                    }
                }
            }
        }
    }
}

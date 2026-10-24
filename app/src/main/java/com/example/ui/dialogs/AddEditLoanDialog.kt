package com.example.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.UserProfile
import com.example.ui.components.InstallmentNodesView
import com.example.util.NumberFormatter
import com.example.util.ShamsiDate
import com.example.util.ShamsiDateHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLoanDialog(
    loanToEdit: Loan? = null,
    initialShares: List<DebtorShare> = emptyList(),
    allUsers: List<UserProfile> = emptyList(),
    currentShamsiDate: ShamsiDate,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onDismiss: () -> Unit,
    onAddNewUser: (UserProfile) -> Unit,
    onSave: (Loan, List<DebtorShare>) -> Unit
) {
    var title by remember { mutableStateOf(loanToEdit?.title ?: "") }
    var totalMonthsText by remember { mutableStateOf(loanToEdit?.totalMonths?.toString() ?: "12") }
    var selectedColor by remember { mutableLongStateOf(loanToEdit?.colorHex ?: 0xFF1976D2L) }

    // Start Date (Unified: Year, Month, Day)
    var startDate by remember {
        mutableStateOf(
            if (loanToEdit != null) ShamsiDate(loanToEdit.startYear, loanToEdit.startMonth, loanToEdit.dueDay)
            else currentShamsiDate
        )
    }

    var showCalendarPicker by remember { mutableStateOf(false) }

    // Total Installment Amount with live comma formatting
    var monthlyAmountText by remember {
        mutableStateOf(
            if (loanToEdit != null && loanToEdit.monthlyAmount > 0)
                NumberFormatter.formatWithCommas(loanToEdit.monthlyAmount, usePersian = false)
            else ""
        )
    }

    val totalMonths = remember(totalMonthsText) {
        NumberFormatter.parseInt(totalMonthsText).coerceIn(1, 120)
    }

    // Auto-detected paid months from start date to current date
    val autoElapsed = remember(startDate, currentShamsiDate, totalMonths) {
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

    // Interactive paid installments (defaults to existing or auto-detected)
    var paidMonths by remember {
        mutableIntStateOf(loanToEdit?.paidMonths ?: autoElapsed)
    }

    // Whenever user changes start date on a new loan, update suggested paid months
    LaunchedEffect(startDate, totalMonths) {
        if (loanToEdit == null) {
            paidMonths = autoElapsed
        }
    }

    // Dynamic list of debtor shares (سهم شرکا)
    val sharesList = remember {
        mutableStateListOf<DebtorShare>().apply {
            addAll(initialShares)
        }
    }

    var showAddPartnerDialog by remember { mutableStateOf(false) }
    var showCreateUserDialog by remember { mutableStateOf(false) }

    val rawMonthlyAmount = NumberFormatter.parseAmount(monthlyAmountText)
    val othersTotalShare = sharesList.sumOf { it.shareAmount }
    val calculatedMyShare = (rawMonthlyAmount - othersTotalShare).coerceAtLeast(0L)

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
                .fillMaxHeight(0.92f),
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
                    Column {
                        Text(
                            text = if (loanToEdit == null) "ثبت وام جدید" else "ویرایش وام و سهم‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مشخصات قسط، تاریخ شمسی و سهم شرکا",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("نام بانک یا وام") },
                        placeholder = { Text("مثلاً: بانک رسالت یا وام خودرو") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 2. Unified Start Date Field (with elegant minimal calendar picker)
                    OutlinedTextField(
                        value = startDate.toStandardFormat(usePersianDigits = usePersianDigits),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("تاریخ شروع وام و روز سررسید (سال/ماه/روز)") },
                        supportingText = {
                            Text("روز سررسید هر ماه: ${NumberFormatter.formatNumber(startDate.day, usePersianDigits)}ام")
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
                            .fillMaxWidth()
                            .clickable { showCalendarPicker = true }
                    )

                    // 3. Amount with live comma separation
                    OutlinedTextField(
                        value = monthlyAmountText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            val num = clean.toLongOrNull() ?: 0L
                            monthlyAmountText = if (clean.isEmpty()) "" else NumberFormatter.formatWithCommas(num, usePersian = false)
                        },
                        label = { Text("مبلغ کل قسط ماهانه (تومان)") },
                        placeholder = { Text("مثلاً: ۲,۸۲۲,۰۰۰") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            if (rawMonthlyAmount > 0) {
                                Text(
                                    text = NumberFormatter.formatPrice(rawMonthlyAmount, usePersian = usePersianDigits, compactThousands = compactThousands),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    )

                    // 4. Total Installment Count (Quick Chips + Input)
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

                    // 5. INTERACTIVE INSTALLMENT CIRCLES (دایره‌های هوشمند اقساط)
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
                                        text = "اقساط پرداخت‌شده تا الان:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "تشخیص هوشمند: ${NumberFormatter.formatNumber(autoElapsed, usePersianDigits)} قسط تا امروز (${currentShamsiDate.monthName})",
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
                        }
                    }

                    // 6. PARTNERS MANAGEMENT (سهم افراد و اتصال به کاربران سیستم)
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
                                    text = "این وام بدون شریک است (کل قسط سهم خود کاربر مبنا است).",
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

                            // Baseline Share Indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("سهم کاربر مبنا (من):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = NumberFormatter.formatPrice(calculatedMyShare, usePersianDigits, compactThousands),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // 7. Color Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("رنگ نشانگر وام:", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorOptions.forEach { colorHex ->
                                val color = Color(colorHex)
                                val isSelected = selectedColor == colorHex
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { selectedColor = colorHex }
                                        .then(
                                            if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                        )
                                )
                            }
                        }
                    }
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
                        onClick = {
                            val loan = (loanToEdit ?: Loan(
                                title = title.ifBlank { "وام جدید" },
                                dueDay = startDate.day,
                                monthlyAmount = rawMonthlyAmount,
                                totalMonths = totalMonths,
                                paidMonths = paidMonths
                            )).copy(
                                title = title.ifBlank { "وام جدید" },
                                dueDay = startDate.day,
                                monthlyAmount = rawMonthlyAmount,
                                totalMonths = totalMonths,
                                paidMonths = paidMonths,
                                startYear = startDate.year,
                                startMonth = startDate.month,
                                endYear = endDate.year,
                                endMonth = endDate.month,
                                myShareAmount = calculatedMyShare,
                                colorHex = selectedColor
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

    // Virtual Minimal Shamsi Calendar Picker Dialog
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

    // Add Partner Modal Dialog (Connected to User Profiles)
    if (showAddPartnerDialog) {
        var selectedUserName by remember { mutableStateOf(allUsers.firstOrNull { !it.isPrimary }?.name ?: allUsers.firstOrNull()?.name ?: "") }
        var partnerAmountText by remember { mutableStateOf("") }
        var partnerDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPartnerDialog = false },
            title = { Text("تعریف سهم شریک") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Pick from existing users or create new
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("انتخاب فرد:", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = {
                                showCreateUserDialog = true
                            }
                        ) {
                            Text("+ کاربر جدید", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (allUsers.isNotEmpty()) {
                        // User chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allUsers.forEach { user ->
                                val isSelected = selectedUserName == user.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedUserName = user.name },
                                    label = { Text(user.name) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = selectedUserName,
                        onValueChange = { selectedUserName = it },
                        label = { Text("نام شریک") },
                        placeholder = { Text("نام شخص...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = partnerAmountText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            val num = clean.toLongOrNull() ?: 0L
                            partnerAmountText = if (clean.isEmpty()) "" else NumberFormatter.formatWithCommas(num, usePersian = false)
                        },
                        label = { Text("مبلغ سهم شریک (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = partnerDesc,
                        onValueChange = { partnerDesc = it },
                        label = { Text("توضیحات (اختیاری)") },
                        placeholder = { Text("مثلاً: نصف قسط") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = NumberFormatter.parseAmount(partnerAmountText)
                        if (selectedUserName.isNotBlank() && amount > 0) {
                            sharesList.add(
                                DebtorShare(
                                    loanId = loanToEdit?.id ?: 0L,
                                    loanTitle = title.ifBlank { "وام" },
                                    personName = selectedUserName.trim(),
                                    shareAmount = amount,
                                    shareDescription = partnerDesc.trim(),
                                    dueDay = startDate.day
                                )
                            )
                            showAddPartnerDialog = false
                        }
                    }
                ) {
                    Text("تایید سهم")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPartnerDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Quick Add New User Dialog
    if (showCreateUserDialog) {
        AddEditUserDialog(
            userToEdit = null,
            onDismiss = { showCreateUserDialog = false },
            onSave = { newUser ->
                onAddNewUser(newUser)
                showCreateUserDialog = false
            }
        )
    }
}

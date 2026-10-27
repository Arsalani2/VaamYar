@file:OptIn(ExperimentalMaterial3Api::class)

package com.aistudio.vamyar.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.aistudio.vamyar.data.UserDeleteCheckResult
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.ui.ImportMode
import com.aistudio.vamyar.ui.ImportPreview
import com.aistudio.vamyar.ui.dialogs.AddEditUserDialog
import com.aistudio.vamyar.ui.dialogs.ShamsiDatePickerDialog
import com.aistudio.vamyar.ui.theme.AppThemeMode
import com.aistudio.vamyar.ui.theme.ThemePreset
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.ShamsiDate

enum class SettingsCategory(val titleFa: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    USERS("کاربران", Icons.Default.People),
    DATE_SYNC("تاریخ شمسی", Icons.Default.CalendarMonth),
    APPEARANCE("ظاهر و تم", Icons.Default.Palette),
    DATA_MANAGEMENT("داده‌ها", Icons.Default.Storage)
}

@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    currentThemePreset: ThemePreset,
    usePersianDigits: Boolean,
    compactThousands: Boolean,
    currentShamsiDate: ShamsiDate,
    users: List<UserProfile>,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemePresetChange: (ThemePreset) -> Unit,
    onDigitsChange: (Boolean) -> Unit,
    onCompactThousandsChange: (Boolean) -> Unit,
    onSetPrimaryUser: (Long) -> Unit,
    onSaveUser: (UserProfile) -> Unit,
    onCheckDeleteUser: suspend (Long) -> UserDeleteCheckResult,
    onConfirmDeleteUser: (Long) -> Unit,
    onSyncDateWithSystem: () -> Unit,
    onSetCustomDate: (ShamsiDate) -> Unit,
    onClearAllLoans: () -> Unit,
    // فیکس bug.md/NB-41: «بازنشانی کامل برنامه» — همهٔ داده‌ها شامل کاربران
    // پاک و کاربر مبنا پیش‌فرض ساخته می‌شود؛ با دیالوگ تأیید صریح.
    onFullReset: () -> Unit = {},
    onTestNotification: () -> Unit,
    // فیکس bug.md/NB-24: ساعت چکِ روزانهٔ اعلان سررسید از تنظیمات تغییر می‌کند؛
    // پس از ثبت ساعت جدید، آلارم روزانه با همان ساعت از نو زمان‌بندی می‌شود.
    reminderHour: Int = 10,
    reminderMinute: Int = 0,
    onReminderTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onExportBackup: () -> String = { "" },
    // فیکس bug.md/NB-34+NB-29: قبل از اجرای ایمپورت، پیش‌نمایش (شمارش‌ها + فهرست
    // جدید/تکراری) به کاربر نشان داده می‌شود؛ خودِ ایمپورت فقط پس از انتخاب
    // صریح حالت (جایگزینی کامل / ادغام) و تأیید اجرا می‌شود.
    onPreviewImport: suspend (String) -> Result<ImportPreview> = { Result.failure(IllegalStateException("پیش‌نمایش پشتیبانی نمی‌شود")) },
    onImportBackup: suspend (String, ImportMode) -> Result<String> = { _, _ -> Result.success("") },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(SettingsCategory.USERS) }

    var userToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }

    var showClearLoansConfirmDialog by remember { mutableStateOf(false) }
    // فیکس bug.md/NB-41: دیالوگ تأیید «بازنشانی کامل برنامه» (شامل کاربران)
    var showFullResetConfirmDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // فیکس bug.md/NB-24: دیالوگ انتخاب ساعت اعلان روزانه (TimePicker متریال ۳)
    var showReminderTimePicker by remember { mutableStateOf(false) }
    // فیکس bug.md/NB-42: ساعت اعلان هم از تنظیم ارقام کاربر پیروی می‌کند
    // (قبلاً همیشهٔ toPersianDigits هاردکد بود و در حالت EN برعکس تنظیم دیده می‌شد).
    val reminderTimeLabel = remember(reminderHour, reminderMinute, usePersianDigits) {
        NumberFormatter.formatClock(reminderHour, reminderMinute, usePersianDigits)
    }

    // فیکس bug.md/NB-34+NB-29: وضعیت پیش‌نمایش ایمپورت — فایل انتخاب‌شده + دادهٔ
    // پیش‌نمایش؛ تا وقتی این null نیست دیالوگِ تأیید/انتخاب حالت باز است و هیچ
    // تغییری در داده رخ نداده است (لغو در هر مرحله بدون تغییر).
    var pendingImport by remember { mutableStateOf<Pair<String, ImportPreview>?>(null) }
    var selectedImportMode by remember { mutableStateOf(ImportMode.REPLACE_ALL) }
    var importInProgress by remember { mutableStateOf(false) }

    // Safe Deletion state
    val coroutineScope = rememberCoroutineScope()
    var userBlockedDeletionDialog by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var userConfirmDeleteDialog by remember { mutableStateOf<UserProfile?>(null) }

    // فیکس bug.md/NB-7: اجرای export و import فایل روی Dispatchers.IO به جای Main thread
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val json = onExportBackup()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    Toast.makeText(context, "فایل پشتیبان با موفقیت ذخیره شد", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "خطا در ذخیره فایل پشتیبان: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // فیکس bug.md/NB-34+NB-29: انتخاب فایل فقط «پیش‌نمایش» می‌سازد؛ خودِ ایمپورت
    // در دیالوگ تأیید (پایین همین فایل) پس از انتخاب صریح حالت اجرا می‌شود.
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonString = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).readText()
                        } ?: ""
                    }

                    if (jsonString.isNotBlank()) {
                        val previewResult = onPreviewImport(jsonString)
                        val preview = previewResult.getOrNull()
                        if (previewResult.isSuccess && preview != null) {
                            selectedImportMode = ImportMode.REPLACE_ALL
                            pendingImport = jsonString to preview
                        } else {
                            Toast.makeText(context, "خطا در خواندن پشتیبان: ${previewResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Horizontal Segmented Category Navigation Bar
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            SettingsCategory.values().forEachIndexed { index, category ->
                SegmentedButton(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SettingsCategory.values().size),
                    icon = {
                        Icon(imageVector = category.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                ) {
                    Text(category.titleFa, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedCategory) {
                // CATEGORY 1: USERS & PRIMARY BASELINE
                SettingsCategory.USERS -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "مدیریت کاربران و کاربر مبنا",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "کاربر مبنا حسابی است که مبالغ از دیدگاه او سنجیده می‌شوند",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = { showAddUserDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ کاربر جدید", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Users List
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    users.forEach { user ->
                                        val isPrimary = user.isPrimary
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(user.avatarColorHex)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = user.name.take(1),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }

                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = user.name,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            if (isPrimary) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = MaterialTheme.colorScheme.primary
                                                                ) {
                                                                    Text(
                                                                        text = "کاربر مبنا",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (user.notes.isNotBlank()) {
                                                            Text(
                                                                text = user.notes,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (!isPrimary) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                onSetPrimaryUser(user.id)
                                                                Toast.makeText(context, "${user.name} به عنوان کاربر مبنا تنظیم شد", Toast.LENGTH_SHORT).show()
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text("مبنا قرار دادن", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = { userToEdit = user },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(16.dp))
                                                    }

                                                    if (users.size > 1 && !isPrimary) {
                                                        IconButton(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    val check = onCheckDeleteUser(user.id)
                                                                    if (!check.canDelete) {
                                                                        userBlockedDeletionDialog = Pair(user.name, check.blockingLoanTitles)
                                                                    } else {
                                                                        userConfirmDeleteDialog = user
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "حذف",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // CATEGORY 2: DATE SYNC & SHAMSI CALENDAR
                SettingsCategory.DATE_SYNC -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(
                                            text = "تنظیم و همگام‌سازی تاریخ شمسی",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "مبنای سررسیدهای روزانه، روزشمار و تشخیص اقساط پرداخت‌شده",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("تاریخ فعال برنامه:", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = currentShamsiDate.formatted(usePersianDigits = usePersianDigits),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        FilledTonalButton(
                                            onClick = { showDatePickerDialog = true },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تغییر دستی تاریخ")
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        onSyncDateWithSystem()
                                        Toast.makeText(context, "تاریخ برنامه با ساعت سیستم همگام شد", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("همگام‌سازی با تاریخ امروز دستگاه (سینک ساعت)")
                                }
                            }
                        }
                    }
                }

                // CATEGORY 3: APPEARANCE & FONTS
                SettingsCategory.APPEARANCE -> {
                    // Persian / English digits & 3 zeros less
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "فرمت اعداد و مبالغ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("نوع نمایش اعداد:", style = MaterialTheme.typography.bodyMedium)
                                    SingleChoiceSegmentedButtonRow {
                                        SegmentedButton(
                                            selected = usePersianDigits,
                                            onClick = { onDigitsChange(true) },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                        ) {
                                            Text("فارسی", style = MaterialTheme.typography.labelSmall)
                                        }
                                        SegmentedButton(
                                            selected = !usePersianDigits,
                                            onClick = { onDigitsChange(false) },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                        ) {
                                            Text("EN", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("حالت سه صفر کمتر (هزار تومان)", style = MaterialTheme.typography.bodyMedium)
                                        Text("نمایش مبالغ به صورت خلاصه‌شده (مثلاً ۸۹۰ هزار ت)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = compactThousands, onCheckedChange = onCompactThousandsChange)
                                }
                            }
                        }
                    }

                    // Theme Mode & Color Presets (Default follows system)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("پوسته و رنگ‌بندی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppThemeMode.values().forEach { mode ->
                                        FilterChip(
                                            selected = currentThemeMode == mode,
                                            onClick = { onThemeModeChange(mode) },
                                            label = { Text(mode.titleFa) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }

                                HorizontalDivider()

                                ThemePreset.values().forEach { preset ->
                                    val isSelected = currentThemePreset == preset
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onThemePresetChange(preset) },
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(preset.previewColor)
                                                )
                                                Text(preset.titleFa, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // CATEGORY 4: DATA MANAGEMENT, BACKUP & RESET
                SettingsCategory.DATA_MANAGEMENT -> {
                    // Backup & Restore Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("پشتیبان‌گیری و بازگردانی اطلاعات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "خروجی استاندارد JSON با هش اعتبارسنجی SHA-256 جهت امنیت و سازگاری دائم",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "می‌توانید از کل اطلاعات برنامه (وام‌ها، کاربران، سهم‌های شراکت، سوابق اقساط و تنظیمات) فایل پشتیبان تهیه کنید و در هر زمان به راحتی آن را بازیابی نمایید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val timestamp = System.currentTimeMillis()
                                            createDocumentLauncher.launch("VaamYar_Backup_${timestamp}.json")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ذخیره پشتیبان", style = MaterialTheme.typography.labelSmall)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            // فیکس bug.md/NB-15: اشتراک‌گذاری «فایل» واقعی به‌جای متن خام —
                                            // JSON در cacheDir/backup نوشته می‌شود و با EXTRA_STREAM (Uri امنِ
                                            // FileProvider + FLAG_GRANT_READ_URI_PERMISSION) ارسال می‌شود تا
                                            // پیام‌رسان‌ها آن را به‌عنوان فایل پیوست قبول کنند.
                                            coroutineScope.launch {
                                                try {
                                                    val shareUri = withContext(Dispatchers.IO) {
                                                        val backupDir = File(context.cacheDir, "backup").apply { mkdirs() }
                                                        // فایل‌های قدیمیِ این مسیرِ موقت پاک شوند (cacheDir هم خودش قابل پاک‌شدن است)
                                                        backupDir.listFiles()?.forEach { it.delete() }
                                                        val backupFile = File(backupDir, "VaamYar_Backup_${System.currentTimeMillis()}.json")
                                                        backupFile.writeText(onExportBackup(), Charsets.UTF_8)
                                                        FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            backupFile
                                                        )
                                                    }
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/json"
                                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                                        putExtra(Intent.EXTRA_SUBJECT, "فایل پشتیبان وام‌یار")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "ارسال و اشتراک فایل پشتیبان"))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("اشتراک‌گذاری", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                HorizontalDivider()

                                OutlinedButton(
                                    onClick = {
                                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("بازگردانی اطلاعات از فایل پشتیبان (ایمپورت)")
                                }
                            }
                        }
                    }

                    // Reset & Notifications Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("مدیریت داده‌ها و بازنشانی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                Text(
                                    text = "برای شروع کاملاً خام جهت ثبت وام‌های واقعی جدید، می‌توانید تمام وام‌های ثبت‌شده را پاکسازی فرمایید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = { showClearLoansConfirmDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("پاکسازی تمام وام‌ها (شروع خام برنامه)")
                                }

                                // فیکس bug.md/NB-41: «بازنشانی کامل برنامه» — برخلاف
                                // پاکسازی وام‌ها، همهٔ داده‌ها شامل کاربران پاک می‌شود و
                                // برنامه خودش یک کاربر مبنا پیش‌فرض می‌سازد؛ اجرا فقط
                                // پس از دیالوگ تأیید صریح (اینوارینت #۹).
                                OutlinedButton(
                                    onClick = { showFullResetConfirmDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("بازنشانی کامل برنامه (همهٔ داده‌ها + کاربران)")
                                }

                                HorizontalDivider()

                                Button(
                                    onClick = {
                                        onTestNotification()
                                        Toast.makeText(context, "اعلان آزمایشی ارسال شد", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.NotificationAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ارسال اعلان آزمایشی سررسید")
                                }

                                HorizontalDivider()

                                // فیکس bug.md/NB-24: ساعت چکِ روزانهٔ اعلان‌ها از
                                // تنظیمات قابل تغییر است (پیش‌فرض ۱۰:۰۰)؛ تغییر ساعت،
                                // آلارم را همان‌جا با ساعت جدید از نو زمان‌بندی می‌کند.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "ساعت اعلان روزانهٔ سررسید",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "چکِ روزانهٔ اقساط هر روز ساعت $reminderTimeLabel انجام می‌شود.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedButton(
                                        onClick = { showReminderTimePicker = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(reminderTimeLabel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Virtual Shamsi Date Picker Dialog
    if (showDatePickerDialog) {
        ShamsiDatePickerDialog(
            initialDate = currentShamsiDate,
            usePersianDigits = usePersianDigits,
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = { picked ->
                onSetCustomDate(picked)
                showDatePickerDialog = false
                Toast.makeText(context, "تاریخ برنامه به ${picked.formatted(usePersianDigits)} تغییر یافت", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // فیکس bug.md/NB-24: دیالوگ انتخاب ساعت اعلان روزانه — TimePicker متریال ۳
    // با مقدار اولیهٔ ذخیره‌شده؛ انصراف هیچ تغییری نمی‌کند (فقط پس از «ثبت»
    // onReminderTimeChange اجرا و آلارم از نو زمان‌بندی می‌شود).
    if (showReminderTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour.coerceIn(0, 23),
            initialMinute = reminderMinute.coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showReminderTimePicker = false },
            title = { Text("ساعت اعلان روزانه") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "چکِ روزانهٔ اقساط و سررسید هر روز در این ساعت انجام می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val h = timePickerState.hour
                        val m = timePickerState.minute
                        showReminderTimePicker = false
                        onReminderTimeChange(h, m)
                        // فیکس bug.md/NB-42: Toast تغییر ساعت هم از تنظیم ارقام کاربر پیروی می‌کند
                        Toast.makeText(
                            context,
                            "ساعت اعلان روزانه به ${NumberFormatter.formatClock(h, m, usePersianDigits)} تغییر یافت",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("ثبت")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderTimePicker = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Modal: Blocked User Deletion Alert (if user has active shares in loans)
    userBlockedDeletionDialog?.let { (personName, loanTitles) ->
        AlertDialog(
            onDismissRequest = { userBlockedDeletionDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("عدم امکان حذف کاربر")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("کاربر «$personName» در وام‌های زیر دارای سهم فعال است:")
                    loanTitles.forEach { title ->
                        Text("• $title", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "جهت جلوگیری از بهم‌ریختگی محاسبات، ابتدا این کاربر را از شراکت در وام‌های مذکور خارج کنید یا وام‌ها را ویرایش نمایید، سپس کاربر را حذف فرمایید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { userBlockedDeletionDialog = null }) {
                    Text("متوجه شدم")
                }
            }
        )
    }

    // Modal: Confirm User Deletion (safe)
    userConfirmDeleteDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { userConfirmDeleteDialog = null },
            title = { Text("تایید حذف کاربر") },
            text = { Text("آیا از حذف کاربر «${user.name}» مطمئن هستید؟ این کاربر در هیچ وامی سهم ندارد.") },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmDeleteUser(user.id)
                        userConfirmDeleteDialog = null
                        Toast.makeText(context, "کاربر با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف کاربر")
                }
            },
            dismissButton = {
                TextButton(onClick = { userConfirmDeleteDialog = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Modal: Add / Edit User Dialog
    if (showAddUserDialog) {
        AddEditUserDialog(
            userToEdit = null,
            existingUsers = users,
            onDismiss = { showAddUserDialog = false },
            onSave = { newUser ->
                onSaveUser(newUser)
                showAddUserDialog = false
                Toast.makeText(context, "کاربر ${newUser.name} با موفقیت افزوده شد", Toast.LENGTH_SHORT).show()
            }
        )
    }

    userToEdit?.let { user ->
        AddEditUserDialog(
            userToEdit = user,
            existingUsers = users,
            onDismiss = { userToEdit = null },
            onSave = { updatedUser ->
                onSaveUser(updatedUser)
                userToEdit = null
                Toast.makeText(context, "اطلاعات ${updatedUser.name} بروزرسانی شد", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal: Confirm Clear All Loans
    if (showClearLoansConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearLoansConfirmDialog = false },
            title = { Text("پاکسازی کامل وام‌ها") },
            text = { Text("آیا مطمئن هستید؟ تمامی وام‌های ثبت‌شده پاک شده و برنامه برای ثبت وام‌های واقعی شما از صفر آغاز به کار خواهد کرد.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllLoans()
                        showClearLoansConfirmDialog = false
                        Toast.makeText(context, "تمام وام‌ها با موفقیت پاکسازی شدند", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("بله، پاکسازی شود")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLoansConfirmDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Modal: فیکس bug.md/NB-41 — تأیید «بازنشانی کامل برنامه» (همهٔ داده‌ها + کاربران)
    if (showFullResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFullResetConfirmDialog = false },
            title = { Text("بازنشانی کامل برنامه") },
            text = {
                Text(
                    "همهٔ وام‌ها، سهم‌های شرکا، تاریخچهٔ پرداخت‌ها و همهٔ کاربران (حتی کاربر مبنا) " +
                        "برای همیشه پاک می‌شوند و برنامه یک کاربر مبنا پیش‌فرض می‌سازد. " +
                        "این عملیات فقط با فایل پشتیبان قابل بازگشت است. ادامه می‌دهید؟"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onFullReset()
                        showFullResetConfirmDialog = false
                        Toast.makeText(context, "برنامه کامل بازنشانی شد و کاربر مبنا ساخته شد", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("بله، بازنشانی کامل شود")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullResetConfirmDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // فیکس bug.md/NB-34+NB-29: دیالوگ پیش‌نمایش + انتخاب حالت ایمپورت —
    // پیش از تأییدِ صریح کاربر هیچ تغییری در داده انجام نمی‌شود.
    pendingImport?.let { (importJson, preview) ->
        AlertDialog(
            onDismissRequest = {
                if (!importInProgress) pendingImport = null
            },
            title = { Text("پیش‌نمایش ایمپورت پشتیبان") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تاریخ فایل: ${preview.exportedDate}")
                    Text("کاربران: ${preview.userCount}  |  وام‌ها: ${preview.loanCount}  |  سهم‌ها: ${preview.shareCount}")
                    Text("سوابق پرداخت: ${preview.recordCount}  |  اقساط: ${preview.installmentCount}  |  آیکون‌ها: ${preview.iconCount}")

                    if (preview.newUsers.isNotEmpty()) {
                        Text("کاربران جدید:", fontWeight = FontWeight.Bold)
                        Text(preview.newUsers.joinToString("، "), fontSize = 12.sp)
                    }
                    if (preview.existingUsers.isNotEmpty()) {
                        Text("کاربران تکراری (موجود):", fontWeight = FontWeight.Bold)
                        Text(preview.existingUsers.joinToString("، "), fontSize = 12.sp)
                    }
                    if (preview.newLoans.isNotEmpty()) {
                        Text("وام‌های جدید:", fontWeight = FontWeight.Bold)
                        Text(preview.newLoans.joinToString("، "), fontSize = 12.sp)
                    }
                    if (preview.existingLoans.isNotEmpty()) {
                        Text("وام‌های تکراری (موجود):", fontWeight = FontWeight.Bold)
                        Text(preview.existingLoans.joinToString("، "), fontSize = 12.sp)
                    }
                    if (preview.newShareCount > 0) {
                        Text("سهم‌های شراکت جدید: ${preview.newShareCount}")
                    }

                    if (preview.legacyMatch) {
                        Text(
                            "⚠️ برخی موارد تکراری با «کلید جایگزین» (نام کانونی / عنوان+مبلغ+ماه شروع) شناخته شده‌اند نه با شناسهٔ یکتای بکاپ؛ در ادغام، این موارد به‌جای درج دوباره به‌روزرسانی/رد می‌شوند.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }

                    HorizontalDivider()
                    Text("حالت ایمپورت را انتخاب کنید:", fontWeight = FontWeight.Bold)
                    ImportMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier

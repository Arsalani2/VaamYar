@file:OptIn(ExperimentalMaterial3Api::class)

package com.aistudio.vamyar.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import com.aistudio.vamyar.data.UserDeleteCheckResult
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.ui.ImportMode
import com.aistudio.vamyar.ui.ImportPreview
import com.aistudio.vamyar.ui.dialogs.AddEditUserDialog
import com.aistudio.vamyar.ui.dialogs.ShamsiDatePickerDialog
import com.aistudio.vamyar.ui.theme.AppThemeMode
import com.aistudio.vamyar.ui.theme.SuccessGreen
import com.aistudio.vamyar.ui.theme.ThemePreset
import com.aistudio.vamyar.util.CrashLogHelper
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.ShamsiDate

enum class SettingsCategory(val titleFa: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    USERS("کاربران", Icons.Default.People),
    DATE_SYNC("تقویم", Icons.Default.CalendarMonth),
    APPEARANCE("ظاهر", Icons.Default.Palette),
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
    onFullReset: () -> Unit = {},
    onTestNotification: () -> Unit,
    reminderHour: Int = 10,
    reminderMinute: Int = 0,
    onReminderTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onExportBackup: () -> String = { "" },
    onPreviewImport: suspend (String) -> Result<ImportPreview> = { Result.failure(IllegalStateException("پیش‌نمایش پشتیبانی نمی‌شود")) },
    onImportBackup: suspend (String, ImportMode) -> Result<String> = { _, _ -> Result.success("") },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(SettingsCategory.USERS) }

    var userToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }

    var showClearLoansConfirmDialog by remember { mutableStateOf(false) }
    var showFullResetConfirmDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }

    val reminderTimeLabel = remember(reminderHour, reminderMinute, usePersianDigits) {
        NumberFormatter.formatClock(reminderHour, reminderMinute, usePersianDigits)
    }

    var pendingImport by remember { mutableStateOf<Pair<String, ImportPreview>?>(null) }
    var selectedImportMode by remember { mutableStateOf(ImportMode.REPLACE_ALL) }
    var importInProgress by remember { mutableStateOf(false) }
    // فیکس bug.md/SB-02: تا آماده‌شدن پیش‌نمایش فایل پشتیبانِ (احتمالاً سنگین)، UI
    // بلاک نمی‌شود؛ دیالوگ «در حال آماده‌سازی» با دکمهٔ لغو نشان داده می‌شود و لغو
    // واقعاً Job را cancel می‌کند.
    var importPreviewInProgress by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    // فیکس bug.md/SB-04: عیب‌یابی — تعداد لاگ‌های کرش محلی برای فعال/غیرفعال‌شدن
    // دکمه‌های «اشتراک گزارش خطا» و «پاک‌کردن لاگ‌ها» در تب «داده‌ها».
    var crashLogCount by remember { mutableStateOf(CrashLogHelper.listCrashLogs(context).size) }

    val coroutineScope = rememberCoroutineScope()
    var userBlockedDeletionDialog by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var userConfirmDeleteDialog by remember { mutableStateOf<UserProfile?>(null) }

    val brandViolet = MaterialTheme.colorScheme.primary
    val brandIndigo = MaterialTheme.colorScheme.secondary

    // Document Launchers for Backup Export & Import
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

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importPreviewInProgress = true
            previewJob = coroutineScope.launch {
                try {
                    val jsonString = withContext(Dispatchers.IO) {
                        // فیکس bug.md/SB-01: رد فایل‌های عظیم قبل از خواندن در حافظه —
                        // سایز سند از provider پرسیده می‌شود (بدون دانلود/باز کردن فایل)
                        val fileSize: Long? = context.contentResolver
                            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
                            }
                        if (fileSize != null && fileSize > com.aistudio.vamyar.util.BackupHelper.MAX_BACKUP_JSON_CHARS) {
                            return@withContext null
                        }
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).readText()
                        } ?: ""
                    }

                    if (jsonString == null) {
                        // فایل از سقف مجاز (۱۰ مگابایت) بزرگ‌تر بود
                        Toast.makeText(context, com.aistudio.vamyar.util.BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, Toast.LENGTH_LONG).show()
                    } else if (jsonString.isNotBlank()) {
                        val previewResult = onPreviewImport(jsonString)
                        val preview = previewResult.getOrNull()
                        if (previewResult.isSuccess && preview != null) {
                            selectedImportMode = ImportMode.REPLACE_ALL
                            pendingImport = jsonString to preview
                        } else {
                            Toast.makeText(context, "خطا در خواندن پشتیبان: ${previewResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: CancellationException) {
                    // لغو توسط کاربر — بی‌صدا
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    importPreviewInProgress = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Top 4 Segmented Navigation Tabs matching Stitch Prototypes
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        onClick = { selectedCategory = category },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) brandViolet.copy(alpha = 0.12f) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_tab_${category.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.titleFa,
                                tint = if (isSelected) brandViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = category.titleFa,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) brandViolet else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedCategory) {
                // ==========================================
                // 1. USERS & BENCHMARK MANAGEMENT (کاربران)
                // ==========================================
                SettingsCategory.USERS -> {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_users_management"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Header: Title + Add User Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(brandViolet)
                                        )
                                        Text(
                                            text = "مدیریت کاربران",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        onClick = { showAddUserDialog = true },
                                        shape = RoundedCornerShape(20.dp),
                                        color = brandViolet.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, brandViolet.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = brandViolet,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "کاربر جدید",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = brandViolet
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                // Users List
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    users.forEach { user ->
                                        UserManagementItemCard(
                                            user = user,
                                            canDelete = users.size > 1 && !user.isPrimary,
                                            onSetPrimary = {
                                                onSetPrimaryUser(user.id)
                                                Toast.makeText(context, "${user.name} به عنوان کاربر مبنا تنظیم شد", Toast.LENGTH_SHORT).show()
                                            },
                                            onEdit = { userToEdit = user },
                                            onDelete = {
                                                coroutineScope.launch {
                                                    val check = onCheckDeleteUser(user.id)
                                                    if (!check.canDelete) {
                                                        userBlockedDeletionDialog = Pair(user.name, check.blockingLoanTitles)
                                                    } else {
                                                        userConfirmDeleteDialog = user
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                // Subtle Informative Note at bottom
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = brandViolet,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "کاربر مبنا حسابی است که مبالغ کل، سهم‌ها و گزارش‌ها از دیدگاه او محاسبه و سنجیده می‌شوند.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. CALENDAR & DATE SYNC (تقویم شمسی)
                // ==========================================
                SettingsCategory.DATE_SYNC -> {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_date_sync"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Title with Icon Box
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(brandViolet.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = brandViolet,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "همگام‌سازی تاریخ",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "مبنای سررسیدهای روزانه، روزشمار و تشخیص اقساط",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Active Date Box (Soft Gradient Container)
                                val dayOfWeek = remember(currentShamsiDate) {
                                    getShamsiDayOfWeek(currentShamsiDate.year, currentShamsiDate.month, currentShamsiDate.day)
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "تاریخ برنامه:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = brandViolet.copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, brandViolet.copy(alpha = 0.25f))
                                            ) {
                                                Text(
                                                    text = dayOfWeek,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = brandViolet,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = currentShamsiDate.formatted(usePersianDigits = usePersianDigits),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black,
                                            color = brandViolet,
                                            letterSpacing = (-0.5).sp
                                        )

                                        Surface(
                                            onClick = { showDatePickerDialog = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface, // فیکس bug.md/NB-45
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 10.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EditCalendar,
                                                    contentDescription = null,
                                                    tint = brandViolet,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "تغییر دستی تاریخ",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick Sync Gradient Action Button
                                Surface(
                                    onClick = {
                                        onSyncDateWithSystem()
                                        Toast.makeText(context, "تاریخ برنامه با ساعت سیستم همگام شد", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Transparent,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(brandViolet, brandIndigo)
                                                )
                                            )
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "همگام‌سازی با تاریخ دستگاه",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. APPEARANCE, THEME & NUMERALS (ظاهر و تم)
                // ==========================================
                SettingsCategory.APPEARANCE -> {
                    // Card 1: Numbers & Currency
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_numbers_format"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "اعداد و مبالغ",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Row 1: Persian vs EN Digits Switcher
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "نوع نمایش اعداد:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Surface(
                                                onClick = { onDigitsChange(true) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (usePersianDigits) brandViolet.copy(alpha = 0.15f) else Color.Transparent,
                                                shadowElevation = if (usePersianDigits) 1.dp else 0.dp
                                            ) {
                                                Text(
                                                    text = "فارسی",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (usePersianDigits) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (usePersianDigits) brandViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }

                                            Surface(
                                                onClick = { onDigitsChange(false) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (!usePersianDigits) brandViolet.copy(alpha = 0.15f) else Color.Transparent,
                                                shadowElevation = if (!usePersianDigits) 1.dp else 0.dp
                                            ) {
                                                Text(
                                                    text = "EN",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (!usePersianDigits) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (!usePersianDigits) brandViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                // Row 2: 3 Zeros Less Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "حالت سه صفر کمتر",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "نمایش مبالغ به صورت خلاصه‌شده (مثلاً ۲۵۰ هزار ت)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = compactThousands,
                                        onCheckedChange = onCompactThousandsChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = brandViolet
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Theme Mode (System, Light, Dark)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_theme_mode"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "حالت نمایش",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppThemeMode.values().forEach { mode ->
                                        val isSelected = currentThemeMode == mode
                                        Surface(
                                            onClick = { onThemeModeChange(mode) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) brandViolet.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) brandViolet.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = mode.titleFa,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) brandViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card 3: Color Schemes & Presets
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_theme_presets"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "پوسته و رنگ‌بندی",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ThemePreset.values().forEach { preset ->
                                        val isSelected = currentThemePreset == preset
                                        Surface(
                                            onClick = { onThemePresetChange(preset) },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) preset.previewColor.copy(alpha = 0.08f) else Color.Transparent,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) preset.previewColor.copy(alpha = 0.35f) else Color.Transparent
                                            ),
                                            modifier = Modifier.fillMaxWidth()
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
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(preset.previewColor)
                                                            .border(2.dp, Color.White, CircleShape)
                                                    )
                                                    Text(
                                                        text = preset.titleFa,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = preset.previewColor,
                                                        modifier = Modifier.size(20.dp)
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

                // ==========================================
                // 4. DATA, BACKUP & NOTIFICATIONS (داده‌ها)
                // ==========================================
                SettingsCategory.DATA_MANAGEMENT -> {
                    // Card 1: Backup & Restore
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_backup_restore"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "پشتیبان‌گیری",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "خروجی استاندارد JSON همراه با هش SHA-256",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(brandViolet.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = brandViolet,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "شامل: وام‌ها، طرفین حساب، اقساط و تنظیمات کلی",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                // Action 1: Save Backup (Gradient Button)
                                Surface(
                                    onClick = {
                                        val timestamp = System.currentTimeMillis()
                                        createDocumentLauncher.launch("VaamYar_Backup_${timestamp}.json")
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Transparent,
                                    shadowElevation = 3.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(brandViolet, brandIndigo)
                                                )
                                            )
                                            .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Save,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "ذخیره پشتیبان",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Action 2: Restore Data
                                Surface(
                                    onClick = {
                                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface, // فیکس bug.md/NB-45
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileOpen,
                                            contentDescription = null,
                                            tint = brandViolet,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "بازگردانی اطلاعات",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = brandViolet
                                        )
                                    }
                                }

                                // Action 3: Share Backup
                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val shareUri = withContext(Dispatchers.IO) {
                                                    val backupDir = File(context.cacheDir, "backup").apply { mkdirs() }
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
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "اشتراک‌گذاری فایل پشتیبان",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 2: Data Management & Reset
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_reset_management"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "مدیریت داده‌ها و بازنشانی",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Soft Rose Clean Button for Clear Loans
                                Surface(
                                    onClick = { showClearLoansConfirmDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "پاکسازی وام‌ها (شروع خام برنامه)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                // Full Reset Button
                                Surface(
                                    onClick = { showFullResetConfirmDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface, // فیکس bug.md/NB-45
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "بازنشانی کامل (شامل کاربران)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 2.5: عیب‌یابی و گزارش خطا (فیکس bug.md/SB-04)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_troubleshooting"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "عیب‌یابی و گزارش خطا",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BugReport,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // خلاصهٔ آخرین کرش (یا «هیچ خطایی ثبت نشده است.»)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (crashLogCount > 0) {
                                            CrashLogHelper.latestCrashSummary(context, usePersianDigits)
                                        } else {
                                            CrashLogHelper.NO_CRASH_RECORDED_TEXT
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                // اشتراک آخرین گزارش خطا — فقط با خواست کاربر و از طریق
                                // FileProvider (متن لاگ هرگز خودکار به بیرون ارسال نمی‌شود).
                                Surface(
                                    onClick = {
                                        val latest = CrashLogHelper.latestCrashLog(context)
                                        if (latest != null) {
                                            val shareUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                latest
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                putExtra(Intent.EXTRA_SUBJECT, "گزارش خطای وام‌یار")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "اشتراک گزارش خطا"))
                                        } else {
                                            Toast.makeText(context, CrashLogHelper.NO_CRASH_RECORDED_TEXT, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = crashLogCount > 0,
                                    shape = RoundedCornerShape(16.dp),
                                    color = brandViolet.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, brandViolet.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = brandViolet,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "اشتراک گزارش خطا",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = brandViolet
                                        )
                                    }
                                }

                                // پاک‌کردن لاگ‌ها
                                Surface(
                                    onClick = {
                                        if (CrashLogHelper.clearCrashLogs(context)) {
                                            crashLogCount = 0
                                            Toast.makeText(context, "لاگ‌های خطا پاک شدند", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = crashLogCount > 0,
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "پاک‌کردن لاگ‌ها",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 3: Notifications & Daily Reminder Time
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_notifications"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "اعلان‌ها",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(brandViolet.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = brandViolet,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        onTestNotification()
                                        Toast.makeText(context, "اعلان آزمایشی سررسید با موفقیت ارسال شد 🔔", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = brandViolet,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationAdd,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ارسال اعلان آزمایشی",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ساعت اعلان روزانه سررسید",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Surface(
                                        onClick = { showReminderTimePicker = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = brandViolet.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, brandViolet.copy(alpha = 0.25f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = brandViolet,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = reminderTimeLabel,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = brandViolet
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

    // Modal: Reminder Time Picker Dialog
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
                    Spacer(modifier = Modifier.height(10.dp))
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

    // Modal: Blocked User Deletion Alert
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
                        Text("• $title", fontWeight = FontWeight.Bold, color = brandViolet)
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

    // Modal: Confirm Full Reset
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

    // Modal: Import Preview Dialog
    // فیکس bug.md/SB-02: دیالوگ وضعیت «در حال آماده‌سازی پیش‌نمایش» — پارس فایل‌های
    // سنگین روی IO طول می‌کشد؛ به‌جای انگشت‌روی‌شدنِ UI، وضعیت شفاف + امکان لغو.
    if (importPreviewInProgress) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("در حال آماده‌سازی پیش‌نمایش") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("در حال خواندن و بررسی فایل پشتیبان…")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    previewJob?.cancel()
                    previewJob = null
                    importPreviewInProgress = false
                }) {
                    Text("لغو")
                }
            }
        )
    }

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
                            "⚠️ برخی موارد تکراری با «کلید جایگزین» شناخته شده‌اند نه با شناسهٔ یکتای بکاپ؛ در ادغام، این موارد به‌جای درج دوباره به‌روزرسانی/رد می‌شوند.",
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
                                .fillMaxWidth()
                                .clickable(enabled = !importInProgress) { selectedImportMode = mode }
                        ) {
                            RadioButton(
                                selected = selectedImportMode == mode,
                                onClick = null,
                                enabled = !importInProgress
                            )
                            Text(mode.titleFa, fontSize = 13.sp)
                        }
                    }
                    if (selectedImportMode == ImportMode.REPLACE_ALL) {
                        Text(
                            "⚠️ جایگزینی کامل، تمام داده‌های فعلی را پاک می‌کند.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !importInProgress,
                    onClick = {
                        importInProgress = true
                        coroutineScope.launch {
                            val result = onImportBackup(importJson, selectedImportMode)
                            importInProgress = false
                            if (result.isSuccess) {
                                Toast.makeText(context, result.getOrNull() ?: "ایمپورت با موفقیت انجام شد", Toast.LENGTH_LONG).show()
                                pendingImport = null
                            } else {
                                Toast.makeText(context, "خطا در ایمپورت پشتیبان: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = if (selectedImportMode == ImportMode.REPLACE_ALL) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text("شروع ایمپورت")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !importInProgress,
                    onClick = { pendingImport = null }
                ) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun UserManagementItemCard(
    user: UserProfile,
    canDelete: Boolean,
    onSetPrimary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val brandViolet = MaterialTheme.colorScheme.primary
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (user.isPrimary) brandViolet.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (user.isPrimary) brandViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Line 1: Avatar, Name & Accordion Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(user.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.take(1),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        if (user.isPrimary) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                                    .border(1.5.dp, Color.White, CircleShape)
                                    .align(Alignment.BottomStart)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = user.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (user.isPrimary) "حساب پیش‌فرض سیستم" else if (user.notes.isNotBlank()) user.notes else "شریک در وام‌ها",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expand button
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "جزییات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Line 2: Status Badge and Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user.isPrimary) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                            Text(
                                text = "کاربر مبنا",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = onSetPrimary,
                        shape = RoundedCornerShape(20.dp),
                        color = brandViolet.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, brandViolet.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = brandViolet,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "انتخاب به عنوان مبنا",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandViolet
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ویرایش",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Line 3: Expandable Details Accordion
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    if (user.notes.isNotBlank()) {
                        Text(
                            text = "یادداشت: ${user.notes}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (user.isPrimary) "این کاربر به عنوان مبنای کل محاسبات مالی سیستم تنظیم شده است."
                        else "این کاربر به عنوان طرف حساب در وام‌ها می‌تواند دارای سهم اختصاصی باشد.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Calculates Persian day of the week from Jalali date
 */
private fun getShamsiDayOfWeek(year: Int, month: Int, day: Int): String {
    return try {
        var jY = year - 979
        var jM = month - 1
        var jD = day - 1
        var jDayNo = 365 * jY + (jY / 33) * 8 + ((jY % 33 + 3) / 4)
        for (i in 0 until jM) {
            jDayNo += if (i < 6) 31 else 30
        }
        jDayNo += jD
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++
            else leap = false
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }
        val gDaysInMonth = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && gDayNo >= gDaysInMonth[gm + 1]) {
            gDayNo -= gDaysInMonth[gm + 1]
            gm++
        }
        val gd = gDayNo + 1
        val cal = Calendar.getInstance().apply {
            set(gy, gm, gd)
        }
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنج‌شنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> "امروز"
        }
    } catch (_: Exception) {
        "امروز"
    }
}

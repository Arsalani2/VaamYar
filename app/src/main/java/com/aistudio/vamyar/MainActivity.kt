package com.aistudio.vamyar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.ui.MainViewModel
import com.aistudio.vamyar.ui.dialogs.AddEditUserDialog
import com.aistudio.vamyar.ui.dialogs.AppGuideDialog
import com.aistudio.vamyar.ui.screens.*
import com.aistudio.vamyar.ui.theme.MyApplicationTheme

// 3 Main Tabs as explicitly requested by user (Timeline & Analytics removed)
enum class AppTab(
    val titleFa: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME("اقساط", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance),
    RECEIVABLES("مطالبات", Icons.Filled.People, Icons.Outlined.People),
    SETTINGS("تنظیمات", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()

            MyApplicationTheme(
                themeMode = themeMode,
                preset = themePreset
            ) {
                // Persian Right-To-Left layout
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    VamyarMainApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VamyarMainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var showUserPickerModal by remember { mutableStateOf(false) }
    var showAddUserFromSwitcher by remember { mutableStateOf(false) }

    // Request Notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // BackHandler: return to HOME if on another tab
    BackHandler(enabled = selectedTab != AppTab.HOME) {
        selectedTab = AppTab.HOME
    }

    val loans by viewModel.allLoans.collectAsStateWithLifecycle()
    val allShares by viewModel.allShares.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط + دیالوگ عقب‌ماندگی
    val allInstallments by viewModel.allInstallments.collectAsStateWithLifecycle()
    val arrearsPrompt by viewModel.arrearsPrompt.collectAsStateWithLifecycle()
    val primaryUser by viewModel.primaryUser.collectAsStateWithLifecycle()
    val overview by viewModel.financialOverview.collectAsStateWithLifecycle()
    val personSummaries by viewModel.personSummaries.collectAsStateWithLifecycle()
    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currentThemePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val usePersianDigits by viewModel.usePersianDigits.collectAsStateWithLifecycle()
    val compactThousands by viewModel.compactThousands.collectAsStateWithLifecycle()
    val loanViewMode by viewModel.loanViewMode.collectAsStateWithLifecycle()
    val smartTips by viewModel.smartFinancialTips.collectAsStateWithLifecycle()
    // فیکس bug.md/F7: پیش‌بینی نقدینگی ۳/۶ ماه آینده برای دیالوگ دستیار هوشمند
    val liquidityForecast by viewModel.liquidityForecast.collectAsStateWithLifecycle()
    val currentShamsiDate by viewModel.currentShamsiDate.collectAsStateWithLifecycle()
    val hasSeenAppGuide by viewModel.hasSeenAppGuide.collectAsStateWithLifecycle()

    val activeUserName = primaryUser?.name ?: "من"
    val activeUserColor = primaryUser?.avatarColorHex ?: 0xFF10B981L

    if (!hasSeenAppGuide) {
        AppGuideDialog(
            onDismiss = {
                viewModel.dismissAppGuide()
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "وام‌یار",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                lineHeight = 18.sp
                            )
                            Text(
                                text = "مدیریت هوشمند اقساط",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    // Quick User Switcher Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { showUserPickerModal = true }
                            .testTag("user_switcher_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(activeUserColor))
                            )
                            Text(
                                text = "مبنا: $activeUserName",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "تغییر کاربر",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (selectedTab != tab) {
                                selectedTab = tab
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.titleFa
                            )
                        },
                        label = {
                            Text(
                                text = tab.titleFa,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${tab.name}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(animationSpec = tween(180)))
                },
                label = "tabTransition"
            ) { targetTab ->
                when (targetTab) {
                    AppTab.HOME -> HomeScreen(
                        loans = loans,
                        allShares = allShares,
                        allUsers = allUsers,
                        allInstallments = allInstallments,
                        currentShamsiDate = currentShamsiDate,
                        overview = overview,
                        smartTips = smartTips,
                        liquidityForecast = liquidityForecast,
                        viewMode = loanViewMode,
                        onViewModeChange = { viewModel.setLoanViewMode(it) },
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands,
                        onTogglePaidThisMonth = { viewModel.toggleLoanCurrentMonthPaid(it) },
                        onIncrementPaid = { viewModel.incrementPaidInstallment(it) },
                        onDecrementPaid = { viewModel.decrementPaidInstallment(it) },
                        onToggleInstallmentMonth = { loan, year, month ->
                            viewModel.toggleInstallmentMonth(loan, year, month)
                        },
                        arrearsPrompt = arrearsPrompt,
                        onPayOnlyCurrentMonth = { viewModel.payOnlyCurrentMonth(it) },
                        onPayAllArrears = { viewModel.payAllArrearsAndCurrent(it) },
                        onDismissArrearsPrompt = { viewModel.dismissArrearsPrompt() },
                        onSaveLoan = { loan, shares, paidYearMonths -> viewModel.saveLoan(loan, shares, paidYearMonths) },
                        onDeleteLoan = { viewModel.deleteLoan(it) },
                        onAddNewUser = { viewModel.saveUser(it) },
                        onTipAction = { tip ->
                            if (tip.type == com.aistudio.vamyar.ui.TipType.COLLECTION_REMINDER) {
                                selectedTab = AppTab.RECEIVABLES
                            }
                        }
                    )

                    AppTab.RECEIVABLES -> ReceivablesScreen(
                        personSummaries = personSummaries,
                        activeUserName = activeUserName,
                        currentShamsiDate = currentShamsiDate,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands,
                        onToggleSharePaid = { id, isPaid -> viewModel.toggleDebtorPaid(id, isPaid) }
                    )

                    AppTab.SETTINGS -> SettingsScreen(
                        currentThemeMode = currentThemeMode,
                        currentThemePreset = currentThemePreset,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands,
                        currentShamsiDate = currentShamsiDate,
                        users = allUsers,
                        onThemeModeChange = { viewModel.setThemeMode(it) },
                        onThemePresetChange = { viewModel.setThemePreset(it) },
                        onDigitsChange = { viewModel.setUsePersianDigits(it) },
                        onCompactThousandsChange = { viewModel.setCompactThousands(it) },
                        onSetPrimaryUser = { viewModel.setPrimaryUser(it) },
                        onSaveUser = { viewModel.saveUser(it) },
                        onCheckDeleteUser = { id -> viewModel.checkCanDeleteUser(id) },
                        onConfirmDeleteUser = { id -> viewModel.deleteUser(id) },
                        onSyncDateWithSystem = { viewModel.syncDateWithSystem() },
                        onSetCustomDate = { viewModel.setCustomShamsiDate(it) },
                        onClearAllLoans = { viewModel.clearAllLoansAndStartFresh() },
                        // فیکس bug.md/NB-41: «بازنشانی کامل برنامه» — همهٔ داده‌ها
                        // شامل کاربران پاک و کاربر مبنا پیش‌فرض ساخته می‌شود.
                        onFullReset = { viewModel.fullResetAllDataAndRecreateBaseUser() },
                        onTestNotification = { viewModel.sendTestNotification(context) },
                        // فیکس bug.md/NB-24: ساعت اعلان روزانه از تنظیمات تغییر
                        // می‌کند و پس از ثبت، آلارم با ساعت جدید از نو زمان‌بندی می‌شود.
                        reminderHour = viewModel.reminderHour,
                        reminderMinute = viewModel.reminderMinute,
                        onReminderTimeChange = { h, m -> viewModel.setReminderTime(h, m) },
                        onExportBackup = { viewModel.exportBackupJson() },
                        // فیکس bug.md/NB-34+NB-29: انتخاب فایل اول پیش‌نمایش می‌سازد؛
                        // ایمپورت با حالت انتخابی کاربر در دیالوگ تأیید اجرا می‌شود.
                        onPreviewImport = { json -> viewModel.prepareImportPreview(json) },
                        onImportBackup = { json, mode -> viewModel.importBackupJson(json, mode) }
                    )
                }
            }
        }
    }

    if (showUserPickerModal) {
        UserSwitcherModalDialog(
            users = allUsers,
            currentPrimaryUser = primaryUser,
            onSelectUser = { userId ->
                viewModel.setPrimaryUser(userId)
                showUserPickerModal = false
            },
            onAddNewUser = {
                showUserPickerModal = false
                showAddUserFromSwitcher = true
            },
            onDismiss = { showUserPickerModal = false }
        )
    }

    if (showAddUserFromSwitcher) {
        AddEditUserDialog(
            userToEdit = null,
            existingUsers = allUsers,
            onDismiss = { showAddUserFromSwitcher = false },
            onSave = { newUser ->
                viewModel.saveUser(newUser)
                viewModel.setPrimaryUser(newUser.id)
                showAddUserFromSwitcher = false
            }
        )
    }
}

@Composable
fun UserSwitcherModalDialog(
    users: List<UserProfile>,
    currentPrimaryUser: UserProfile?,
    onSelectUser: (Long) -> Unit,
    onAddNewUser: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "انتخاب و سوییچ کاربر مبنا",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "محاسبات سهم شخصی و مطالبات بر پایه این کاربر است",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // User list
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(users.size) { index ->
                        val user = users[index]
                        val isSelected = user.id == currentPrimaryUser?.id
                        val avatarColor = Color(user.avatarColorHex)

                        Surface(
                            onClick = { onSelectUser(user.id) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                width = if (isSelected) 1.8.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(avatarColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = user.name.take(1),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = user.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (user.isPrimary) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "حساب اصلی",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            val noteText = if (user.notes.isNotBlank()) user.notes else "شریک مالی"
                                            Text(
                                                text = noteText,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "انتخاب‌شده",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add User Button
                OutlinedButton(
                    onClick = onAddNewUser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+ افزودن کاربر یا ذی‌نفع جدید",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.vamyar.ui.MainViewModel
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
    var showUserPickerMenu by remember { mutableStateOf(false) }

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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "وام‌یار",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Quick User Switcher Chip
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clickable { showUserPickerMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Dropdown for switching primary user
                        DropdownMenu(
                            expanded = showUserPickerMenu,
                            onDismissRequest = { showUserPickerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "انتخاب کاربر مبنا:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            allUsers.forEach { user ->
                                val isSelected = user.id == primaryUser?.id
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(user.avatarColorHex))
                                            )
                                            Text(
                                                text = user.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setPrimaryUser(user.id)
                                        showUserPickerMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
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
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.titleFa
                            )
                        },
                        label = { Text(tab.titleFa) },
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
                        arrearsPrompt = arrearsPrompt,
                        onPayOnlyCurrentMonth = { viewModel.payOnlyCurrentMonth(it) },
                        onPayAllArrears = { viewModel.payAllArrearsAndCurrent(it) },
                        onDismissArrearsPrompt = { viewModel.dismissArrearsPrompt() },
                        onSaveLoan = { loan, shares -> viewModel.saveLoan(loan, shares) },
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
                        onToggleSharePaid = { id, isPaid -> viewModel.toggleDebtorPaid(id, isPaid) },
                        onResetMonthlyStatus = { viewModel.resetMonthlyDebtorStatus() }
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
}

package com.igorganapolsky.answerguard

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.igorganapolsky.answerguard.BuildConfig
import com.igorganapolsky.answerguard.analytics.AnalyticsService
import com.igorganapolsky.answerguard.analytics.AnalyticsEvents
import com.igorganapolsky.answerguard.billing.ProManager
import com.igorganapolsky.answerguard.privacy.DataDeletion
import com.igorganapolsky.answerguard.review.StoreReviewManager
import com.igorganapolsky.answerguard.billing.EntitlementLevel
import com.igorganapolsky.answerguard.screening.CallEventBackfill
import com.igorganapolsky.answerguard.screening.CallSource
import com.igorganapolsky.answerguard.screening.ScreenedCall
import com.igorganapolsky.answerguard.screening.ScreeningLog
import com.igorganapolsky.answerguard.screening.SpamVerdict
import com.igorganapolsky.answerguard.screening.UserBlocklist
import com.igorganapolsky.answerguard.screening.CarrierResolver
import com.igorganapolsky.answerguard.screening.CallerIdDatabase
import com.igorganapolsky.answerguard.ui.CallTimestampFormatter
import com.igorganapolsky.answerguard.ui.screens.PaywallSheet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.igorganapolsky.answerguard.screening.PauseState
import com.igorganapolsky.answerguard.screening.RoleOnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.igorganapolsky.answerguard.ui.screens.holdForHiddenUnlock
import com.igorganapolsky.answerguard.ui.screens.HIDDEN_UNLOCK_HOLD_DURATION_MS

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var analyticsService: AnalyticsService
    @Inject lateinit var proManager: ProManager
    @Inject lateinit var storeReviewManager: StoreReviewManager

    private var callScreeningEnabled by mutableStateOf(false)
    private var screeningPaused by mutableStateOf(false)
    private var contactsPermissionGranted by mutableStateOf(false)
    private var callLogPermissionGranted by mutableStateOf(false)
    private var voicemailPermissionGranted by mutableStateOf(false)
    private var recentCalls by mutableStateOf<List<ScreenedCall>>(emptyList())
    private var proActionInProgress by mutableStateOf(false)
    private var proStatusMessage by mutableStateOf<String?>(null)
    private var transientFeedback by mutableStateOf<Pair<Long, String>?>(null)
    private var showDisableInstruction by mutableStateOf(false)
    private var pendingDisableCheck = false

    private fun emitFeedback(message: String) {
        transientFeedback = System.currentTimeMillis() to message
    }

    private val roleRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
            if (callScreeningEnabled) {
                analyticsService.track(AnalyticsEvents.CALL_SCREENING_ENABLED)
                analyticsService.trackFirstProtectionEnabledIfNeeded()
                emitFeedback("Call screening enabled")
            } else {
                emitFeedback("Call screening was not enabled")
            }
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            contactsPermissionGranted = isGranted
            if (isGranted) {
                analyticsService.track("contacts_permission_granted")
                emitFeedback("Contacts access granted")
            } else {
                emitFeedback("Contacts access denied - enable it in Settings")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        refreshStatus()
        // Firebase App Distribution: check for tester updates on each launch.
        // Self-prompts user to install latest internal build. No-op for users
        // who installed via Play Store (no FAD tester credentials).
        runCatching {
            FirebaseAppDistribution.getInstance().updateIfNewReleaseAvailable()
                .addOnFailureListener { /* silent — non-tester user or no update */ }
        }

        setContent {
            AnswerGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AnswerGuardColors.Background,
                ) {
                    AnswerGuardHome(
                        callScreeningEnabled = callScreeningEnabled,
                        screeningPaused = screeningPaused,
                        contactsPermissionGranted = contactsPermissionGranted,
                        onEnable = ::requestCallScreeningRole,
                        onTogglePause = ::togglePause,
                        onSwitchApp = { showDisableInstruction = true },
                        onEnableContacts = ::requestContactsPermission,
                        onUpgrade = ::launchProPurchase,
                        onRestore = ::restorePurchases,
                        recentCalls = recentCalls,
                        onRefreshCalls = {
                            refreshStatus()
                            emitFeedback("Activity updated")
                        },
                        onBlockNumber = ::blockNumber,
                        onUnblockNumber = ::unblockNumber,
                        proActionInProgress = proActionInProgress,
                        proStatusMessage = proStatusMessage,
                        transientFeedback = transientFeedback,
                        proManager = proManager,
                        onSecretUnlock = {
                            proManager.forcePro()
                            emitFeedback("Backdoor activated: Entitlement changed")
                        },
                        showDisableInstruction = showDisableInstruction,
                        onDismissDisableInstruction = { showDisableInstruction = false },
                        onConfirmDisable = {
                            showDisableInstruction = false
                            requestDisableCallScreening()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        val wasEnabled = callScreeningEnabled
        refreshStatus()
        
        // 2026 Best Practice: Silent entitlement sync on every resume
        restorePurchasesSilently()
        
        // Check if eligible for review prompt
        storeReviewManager.requestReview(this)

        if (pendingDisableCheck) {
            pendingDisableCheck = false
            if (callScreeningEnabled) {
                emitFeedback(
                    "Still active. In Settings: Default apps > Caller ID & spam app > None",
                )
            } else {
                emitFeedback("AnswerGuard is now off")
            }
        } else if (wasEnabled && !callScreeningEnabled) {
            emitFeedback("AnswerGuard is now off")
        }
    }

    private fun refreshStatus() {
        storeReviewManager.recordAction()
        callScreeningEnabled =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                    roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            } else {
                false
            }
        screeningPaused = PauseState.isPaused(this)
        contactsPermissionGranted =
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        callLogPermissionGranted = CallEventBackfill.hasCallLogPermission(this)
        voicemailPermissionGranted = CallEventBackfill.hasVoicemailPermission(this)

        // Public Play builds do not request restricted Call Log / Voicemail
        // permissions. This remains a no-op unless a non-Play build declares
        // and receives those permissions.
        if (callLogPermissionGranted || voicemailPermissionGranted) {
            // CallEventBackfill.merge() queries two content providers and reads
            // the screening log from disk — both blocking I/O. Run off the main
            // thread to avoid UI jank / ANR on devices with large call logs,
            // then publish the refreshed list back on the main thread.
            lifecycleScope.launch(Dispatchers.IO) {
                CallEventBackfill.merge(this@MainActivity)
                val recent = ScreeningLog.getRecent()
                withContext(Dispatchers.Main) {
                    recentCalls = recent
                }
            }
        } else {
            recentCalls = ScreeningLog.getRecent()
        }
    }

    private fun togglePause() {
        val nowPaused = !screeningPaused
        PauseState.setPaused(this, nowPaused)
        screeningPaused = nowPaused
        analyticsService.track(
            if (nowPaused) "screening_paused" else "screening_resumed",
        )
        emitFeedback(if (nowPaused) "Paused. No calls will be screened." else "Resumed. Screening is active.")
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        roleRequestLauncher.launch(Intent(this, RoleOnboardingActivity::class.java))
    }

    private fun requestContactsPermission() {
        contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
    }

    private fun requestDisableCallScreening() {
        // Android does not let an app revoke its own RoleManager role. Open the
        // system Default Apps screen so the user can switch away from AnswerGuard.
        // A Toast (LENGTH_LONG ~3.5s) reinforces the dialog instructions because
        // the dialog dismisses when Settings opens.
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                android.widget.Toast.makeText(
                    this,
                    "Tap \"Caller ID & spam app\", then choose another app or None",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                pendingDisableCheck = true
                return
            } catch (_: Exception) {
                // try next fallback
            }
        }
        emitFeedback("Could not open system settings")
    }

    private fun launchProPurchase() {
        if (proActionInProgress) return
        lifecycleScope.launch {
            proActionInProgress = true
            proStatusMessage = "Connecting to Google Play..."
            try {
                val launched =
                    proManager.launchProPurchase(
                        activity = this@MainActivity,
                        entryPoint = "home_pro_card",
                    )
                proStatusMessage =
                    if (launched) {
                        "Google Play is open. Complete your purchase there."
                    } else {
                        "Could not open Google Play billing. Check Play Store setup and try again."
                    }
            } catch (_: Exception) {
                proStatusMessage = "Could not start the Pro upgrade. Try again."
            } finally {
                proActionInProgress = false
            }
        }
    }

    private fun restorePurchases() {
        if (proActionInProgress) return
        lifecycleScope.launch {
            proActionInProgress = true
            proStatusMessage = "Checking Google Play purchases..."
            try {
                val restored = proManager.restorePurchasesFromPaywall(entryPoint = "home_pro_card")
                proStatusMessage =
                    if (restored) {
                        "Pro purchase restored."
                    } else {
                        "No active Pro purchase found for this Google Play account."
                    }
            } catch (_: Exception) {
                proStatusMessage = "Could not restore purchases. Try again."
            } finally {
                proActionInProgress = false
            }
        }
    }

    private fun restorePurchasesSilently() {
        lifecycleScope.launch {
            try {
                proManager.restorePurchasesFromPaywall(entryPoint = "auto_resume_sync")
            } catch (_: Exception) { }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        analyticsService.trackDeepLink(uri)
    }

    private fun blockNumber(number: String) {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return
        UserBlocklist.add(digits)
        analyticsService.track("number_blocked_from_log", mapOf("number_length" to digits.length))
        refreshStatus()
        emitFeedback("Number blocked")
    }

    private fun unblockNumber(number: String) {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return
        UserBlocklist.remove(digits)
        analyticsService.track("number_unblocked_from_log", mapOf("number_length" to digits.length))
        refreshStatus()
        emitFeedback("Number unblocked")
    }
}

private object AnswerGuardColors {
    val Background = Color(0xFF0B1014)
    val Surface = Color(0xFF121A20)
    val SurfaceMuted = Color(0xFF182229)
    val Primary = Color(0xFF2DD4BF)
    val Warning = Color(0xFFF59E0B)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFB6C2CC)
}

@Composable
private fun AnswerGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AnswerGuardHome(
    callScreeningEnabled: Boolean,
    screeningPaused: Boolean,
    contactsPermissionGranted: Boolean,
    onEnable: () -> Unit,
    onTogglePause: () -> Unit,
    onSwitchApp: () -> Unit,
    onEnableContacts: () -> Unit,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    recentCalls: List<ScreenedCall>,
    onRefreshCalls: () -> Unit,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit,
    proActionInProgress: Boolean,
    proStatusMessage: String?,
    transientFeedback: Pair<Long, String>? = null,
    proManager: ProManager,
    onSecretUnlock: () -> Unit,
    showDisableInstruction: Boolean,
    onDismissDisableInstruction: () -> Unit,
    onConfirmDisable: () -> Unit,
) {
    // rememberSaveable so rotation / process-death doesn't yank the user back
    // to Home in the middle of editing the blocklist or settings.
    var showBlocklist by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    // Once the dynamic-HVA paywall fires on hvaCount == 3, don't re-fire on
    // every rotation (LaunchedEffect would otherwise re-trigger because the
    // key — hvaCount — is unchanged). Survives process death so a user who
    // already dismissed it doesn't keep seeing it on every relaunch.
    var dynamicPaywallShown by rememberSaveable { mutableStateOf(false) }
    var showDeleteDataConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    
    var isRefreshing by remember { mutableStateOf(false) }
    
    val entitlementLevel by proManager.entitlementLevel.collectAsStateWithLifecycle()
    val blockedNumbers by UserBlocklist.blockedNumbers.collectAsStateWithLifecycle()
    val hvaCount by proManager.hvaCount.collectAsStateWithLifecycle()
    
    val showSnackbar: (String) -> Unit = { msg ->
        snackbarScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
    }
    androidx.compose.runtime.LaunchedEffect(transientFeedback) {
        transientFeedback?.let { (_, msg) -> showSnackbar(msg) }
    }

    Scaffold(
        containerColor = AnswerGuardColors.Background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.testTag("home_snackbar_host"),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = AnswerGuardColors.SurfaceMuted,
                    contentColor = AnswerGuardColors.TextPrimary,
                )
            }
        },
    ) { innerPadding ->
        when {
            showBlocklist -> {
                BlocklistScreen(
                    onBack = { showBlocklist = false },
                    showSnackbar = showSnackbar,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            showSettings -> {
                SettingsScreen(
                    onBack = { showSettings = false },
                    callScreeningEnabled = callScreeningEnabled,
                    screeningPaused = screeningPaused,
                    onTogglePause = onTogglePause,
                    onManageBlocklist = { showBlocklist = true },
                    entitlementLevel = entitlementLevel,
                    onUpgrade = { showPaywall = true },
                    onRestore = onRestore,
                    proActionInProgress = proActionInProgress,
                    proStatusMessage = proStatusMessage,
                    onDeleteData = { showDeleteDataConfirm = true },
                    showSnackbar = showSnackbar,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        snackbarScope.launch {
                            onRefreshCalls()
                            kotlinx.coroutines.delay(800)
                            isRefreshing = false
                        }
                    },
                    state = rememberPullToRefreshState(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(AnswerGuardColors.Background)
                                .padding(24.dp),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Header(
                                onSecretUnlock = onSecretUnlock,
                                onSettingsClick = { showSettings = true }
                            )
                            StatusCard(
                                callScreeningEnabled = callScreeningEnabled,
                                screeningPaused = screeningPaused,
                                onEnable = onEnable,
                                onTogglePause = onTogglePause,
                                onSwitchApp = onSwitchApp,
                            )
                            ContactsCard(
                                permissionGranted = contactsPermissionGranted,
                                onEnable = onEnableContacts,
                            )
                            RecentActivityCard(
                                calls = recentCalls,
                                blockedNumbers = blockedNumbers,
                                isPro = entitlementLevel.isPro,
                                onBlock = { 
                                    onBlockNumber(it)
                                    proManager.recordHighValueAction("ai_protection")
                                },
                                onUnblock = onUnblockNumber
                            )
                            // Blocklist, subscription, and Privacy & Data now live behind the
                            // gear (SettingsScreen) so Home stays focused on protection status.
                        }
                    }
                }
            }
        }
    }

    if (showDisableInstruction) {
        AlertDialog(
            onDismissRequest = onDismissDisableInstruction,
            containerColor = AnswerGuardColors.Surface,
            titleContentColor = AnswerGuardColors.TextPrimary,
            textContentColor = AnswerGuardColors.TextSecondary,
            title = { Text("How to Turn Off") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Android requires you to manually change the default app in System Settings.")
                    Text("1. Tap 'Go to Settings' below.")
                    Text("2. Find 'Caller ID & spam app'.")
                    Text("3. Select 'None' or another app.")
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDisable,
                    colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary)
                ) {
                    Text("Go to Settings", color = Color(0xFF06211E))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDisableInstruction) {
                    Text("Cancel", color = AnswerGuardColors.TextSecondary)
                }
            }
        )
    }

    if (showDeleteDataConfirm) {
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { showDeleteDataConfirm = false },
            containerColor = AnswerGuardColors.Surface,
            titleContentColor = AnswerGuardColors.TextPrimary,
            textContentColor = AnswerGuardColors.TextSecondary,
            title = { Text("Delete all on-device data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This clears your screening history, blocklist, contacts " +
                            "allowlist, and screening pause state on this device.",
                    )
                    Text(
                        "Your paid subscription (if any) is restored from Google Play " +
                            "on the next launch. Uninstalling the app does the same thing.",
                        color = AnswerGuardColors.TextSecondary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleared = DataDeletion.deleteAllUserData(ctx)
                        showDeleteDataConfirm = false
                        onRefreshCalls()
                        android.widget.Toast.makeText(
                            ctx,
                            "All on-device data deleted ($cleared stores cleared)",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Delete my data", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDataConfirm = false }) {
                    Text("Cancel", color = AnswerGuardColors.TextSecondary)
                }
            },
        )
    }

    if (showPaywall) {
        val isFirstTime = hvaCount < 10
        PaywallSheet(
            entryPoint = if (isFirstTime) "dynamic_hva_intro" else "standard_pro",
            familyPrice = if (isFirstTime) "$0.99 1st Mo, then $29.99/yr" else "$29.99/yr",
            onPurchase = { _ ->
                showPaywall = false
                onUpgrade() 
            },
            onRestore = {
                showPaywall = false
                onRestore()
            },
            onDismiss = { showPaywall = false },
            onSecretUnlock = onSecretUnlock
        )
    }
    
    // Dynamic Micro-Paywall Trigger: After 3 high-value actions, exactly once.
    // `dynamicPaywallShown` is rememberSaveable so once we show the paywall we
    // never re-fire it (otherwise a rotation re-triggers the LaunchedEffect
    // because hvaCount hasn't changed, and the user keeps getting the same
    // paywall they already dismissed).
    androidx.compose.runtime.LaunchedEffect(hvaCount) {
        if (
            hvaCount == 3 &&
            entitlementLevel == EntitlementLevel.NONE &&
            !dynamicPaywallShown
        ) {
            showPaywall = true
            dynamicPaywallShown = true
        }
    }
}

@Composable
private fun BlocklistCard(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Personal Blocklist",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Manually block specific numbers. These calls will be rejected immediately.",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.SurfaceMuted),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Manage Blocklist",
                    color = AnswerGuardColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PrivacyAndDataCard(onDeleteData: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Privacy & Data",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "AnswerGuard stores your screening history, blocklist, and " +
                    "contacts allowlist on this device only. Delete them any time.",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onDeleteData,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_my_data_button"),
            ) {
                Text(
                    text = "Delete my data",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BlocklistScreen(
    onBack: () -> Unit,
    showSnackbar: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Intercept Android's back gesture / button so swipe-back returns to the
    // home screen instead of falling through to Activity.finish() and closing
    // the whole app. Predictive-back animation runs at PRIORITY_DEFAULT.
    BackHandler(enabled = true) { onBack() }

    // Observe the source-of-truth Flow instead of holding a local snapshot —
    // otherwise a call landing while the user is on this screen mutates
    // UserBlocklist via AnswerGuardScreeningService but the list doesn't
    // re-render. `.sorted()` keeps stable display order.
    val numbersSet by UserBlocklist.blockedNumbers.collectAsStateWithLifecycle()
    val numbers = remember(numbersSet) { numbersSet.sorted() }
    // rememberSaveable so a rotation doesn't wipe a half-typed phone number.
    var newNumber by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnswerGuardColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.SurfaceMuted)) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Your Blocklist", color = AnswerGuardColors.TextPrimary, style = MaterialTheme.typography.headlineSmall)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextField(
                value = newNumber,
                onValueChange = { newNumber = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter number") },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    unfocusedContainerColor = AnswerGuardColors.Surface,
                    focusedContainerColor = AnswerGuardColors.Surface
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val digits = newNumber.filter { it.isDigit() }
                    if (digits.isBlank()) {
                        showSnackbar("Enter a phone number first")
                    } else {
                        com.igorganapolsky.answerguard.screening.UserBlocklist.add(digits)
                        // Flow above re-emits and the list re-renders automatically.
                        newNumber = ""
                        showSnackbar("Blocked $digits")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary)
            ) {
                Text("Add", color = Color(0xFF06211E))
            }
        }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(numbers.size) { index ->
                val number = numbers[index]
                Card(
                    colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(number, color = AnswerGuardColors.TextPrimary, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                com.igorganapolsky.answerguard.screening.UserBlocklist.remove(number)
                                // Flow above re-emits and the list re-renders automatically.
                                showSnackbar("Removed $number from blocklist")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f))
                        ) {
                            Text("Remove", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsCard(
    permissionGranted: Boolean,
    onEnable: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(enabled = permissionGranted)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Contact Identification",
                        color = AnswerGuardColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (permissionGranted) "Active" else "Recommended",
                        color = if (permissionGranted) AnswerGuardColors.Primary else AnswerGuardColors.Warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            
            Text(
                text = "Allows AnswerGuard to identify your contacts so they are never accidentally silenced or blocked.",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            if (!permissionGranted) {
                Button(
                    onClick = onEnable,
                    colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Allow Contacts Access",
                        color = Color(0xFF06211E),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProCard(
    entitlementLevel: EntitlementLevel,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    actionInProgress: Boolean,
    statusMessage: String?,
    showSnackbar: (String) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            showSnackbar(statusMessage)
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val title = when (entitlementLevel) {
                EntitlementLevel.BUSINESS -> "AnswerGuard Business"
                EntitlementLevel.FAMILY -> "AnswerGuard Family"
                EntitlementLevel.PRO -> "AnswerGuard Pro"
                EntitlementLevel.NONE -> "AnswerGuard Free"
            }
            
            val description = when (entitlementLevel) {
                EntitlementLevel.BUSINESS -> "Enterprise-grade call defense active. Advanced scam shielding and priority B2B support fully engaged."
                EntitlementLevel.FAMILY -> "Advanced protection active across your household. Upgrade to Business for stricter spam rules and priority support."
                EntitlementLevel.PRO -> "Premium protection active. Compare Family and Business plans to add household sharing or business-tuned rules."
                EntitlementLevel.NONE -> "Basic protection active. Upgrade to unlock advanced spam rules, family sharing, and business-tuned rules."
            }

            Text(
                text = title,
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            
            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            
            when (entitlementLevel) {
                EntitlementLevel.NONE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onUpgrade,
                            enabled = !actionInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary),
                            modifier = Modifier.weight(1f).testTag("home_pro_upgrade_button"),
                        ) {
                            Text(
                                text = "Upgrade",
                                color = Color(0xFF06211E),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = onRestore,
                            enabled = !actionInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.SurfaceMuted),
                            modifier = Modifier.weight(1f).testTag("home_pro_restore_button"),
                        ) {
                            Text(
                                text = "Restore",
                                color = AnswerGuardColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                EntitlementLevel.PRO -> {
                    Button(
                        onClick = onUpgrade,
                        enabled = !actionInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary),
                        modifier = Modifier.fillMaxWidth().testTag("home_pro_upgrade_button"),
                    ) {
                        Text(
                            text = "See plans",
                            color = Color(0xFF06211E),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                EntitlementLevel.FAMILY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onUpgrade,
                            enabled = !actionInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary),
                            modifier = Modifier.weight(1f).testTag("home_pro_upgrade_button"),
                        ) {
                            Text(
                                text = "Upgrade to Business",
                                color = Color(0xFF06211E),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = {
                                val packageName = context.packageName
                                uriHandler.openUri("https://play.google.com/store/account/subscriptions?package=$packageName")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.SurfaceMuted),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Manage",
                                color = AnswerGuardColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                EntitlementLevel.BUSINESS -> {
                    Button(
                        onClick = {
                            val packageName = context.packageName
                            uriHandler.openUri("https://play.google.com/store/account/subscriptions?package=$packageName")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.SurfaceMuted),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Manage Subscription",
                            color = AnswerGuardColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage,
                    color = AnswerGuardColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("home_pro_status_message"),
                )
            }
        }
    }
}

@Composable
private fun Header(
    onSecretUnlock: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val haptic = LocalHapticFeedback.current
                Text(
                    text = "AnswerGuard",
                    color = AnswerGuardColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("home_title")
                        .then(
                            if (ProManager.canUseDebugUnlock()) {
                                Modifier.holdForHiddenUnlock(
                                    holdDurationMs = HIDDEN_UNLOCK_HOLD_DURATION_MS,
                                    haptic = haptic,
                                    onHoldComplete = onSecretUnlock
                                )
                            } else {
                                Modifier
                            }
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(AnswerGuardColors.Primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ON-DEVICE SCREENING",
                            color = AnswerGuardColors.Primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% Private",
                        color = AnswerGuardColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = AnswerGuardColors.TextSecondary
                )
            }
        }
        Text(
            text = "Autonomous protection that runs locally on your phone.",
            color = AnswerGuardColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StatusCard(
    callScreeningEnabled: Boolean,
    screeningPaused: Boolean,
    onEnable: () -> Unit,
    onTogglePause: () -> Unit,
    onSwitchApp: () -> Unit,
) {
    val active = callScreeningEnabled && !screeningPaused
    val statusText = when {
        !callScreeningEnabled -> "Not enabled"
        screeningPaused -> "Paused"
        else -> "Active"
    }
    val statusColor = when {
        active -> AnswerGuardColors.Primary
        else -> AnswerGuardColors.Warning
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(enabled = active)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Call Screening",
                        color = AnswerGuardColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("home_call_screening_status"),
                    )
                }
            }

            if (!callScreeningEnabled) {
                Button(
                    onClick = onEnable,
                    colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary),
                    modifier = Modifier.fillMaxWidth().testTag("home_enable_call_screening_button"),
                ) {
                    Text(
                        text = "Enable Call Screening",
                        color = Color(0xFF06211E),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onTogglePause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (screeningPaused) AnswerGuardColors.Primary else AnswerGuardColors.SurfaceMuted,
                        ),
                        modifier = Modifier.weight(1f).testTag("home_pause_resume_button"),
                    ) {
                        Text(
                            text = if (screeningPaused) "Resume" else "Pause",
                            color = if (screeningPaused) Color(0xFF06211E) else AnswerGuardColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = onSwitchApp,
                        colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.SurfaceMuted),
                        modifier = Modifier.weight(1f).testTag("home_turn_off_button"),
                    ) {
                        Text(
                            text = "Turn Off",
                            color = AnswerGuardColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(enabled: Boolean) {
    Box(
        modifier =
            Modifier
                .size(14.dp)
                .background(
                    color = if (enabled) AnswerGuardColors.Primary else AnswerGuardColors.Warning,
                    shape = RoundedCornerShape(7.dp),
                ),
    )
}

@Composable
private fun HowItWorks() {
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.SurfaceMuted),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "How it works",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Step("1", "Enable AnswerGuard as your call screening app in Android settings.")
            Step("2", "Incoming calls are checked locally against confirmed spam patterns.")
            Step("3", "Unknown calls are allowed by default unless they match a conservative spam rule.")
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = number,
            color = AnswerGuardColors.Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = text,
            color = AnswerGuardColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecentActivityCard(
    calls: List<ScreenedCall>,
    blockedNumbers: Set<String>,
    isPro: Boolean,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit,
) {
    val context = LocalContext.current
    // Android only invokes our CallScreeningService when a call actually rings.
    // With DND silencing calls, the OS / carrier can route incoming calls
    // straight to voicemail without firing onScreenCall — those calls never
    // reach our log. Surface that to the user when DND is on so they don't
    // think activity is missing or pull-to-refresh is broken.
    val dndOn = remember(calls) {
        runCatching {
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as? android.app.NotificationManager
            nm?.currentInterruptionFilter?.let {
                it != android.app.NotificationManager.INTERRUPTION_FILTER_ALL &&
                    it != android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            } ?: false
        }.getOrDefault(false)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Recent Activity",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (dndOn) {
                Text(
                    text = "Do Not Disturb is on. Calls your carrier sends " +
                        "straight to voicemail won't appear here — they bypass " +
                        "Android's call-screening hook entirely.",
                    color = AnswerGuardColors.Warning,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("recent_activity_dnd_hint"),
                )
            }

            if (calls.isEmpty()) {
                Text(
                    text = "No calls screened yet. Blocked or suspicious calls will appear here.",
                    color = AnswerGuardColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val uniqueCalls = calls.distinctBy { it.number.filter { c -> c.isDigit() } }
                    uniqueCalls.take(5).forEach { call ->
                        val digits = call.number.filter { it.isDigit() }
                        val historyForNumber = calls.filter { it.number.filter { c -> c.isDigit() } == digits }
                        val totalCalls = historyForNumber.size
                        val blockedCalls = historyForNumber.count { it.verdict == com.igorganapolsky.answerguard.screening.SpamVerdict.BLOCK || it.verdict == com.igorganapolsky.answerguard.screening.SpamVerdict.SILENCE || blockedNumbers.contains(digits) }
                        val allowedCalls = totalCalls - blockedCalls
                        
                        ActivityRow(
                            call = call,
                            isBlocked = blockedNumbers.contains(digits),
                            isPro = isPro,
                            totalCalls = totalCalls,
                            blockedCalls = blockedCalls,
                            allowedCalls = allowedCalls,
                            onBlock = { onBlock(call.number) },
                            onUnblock = { onUnblock(call.number) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatPhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return when {
        digits.length == 11 && digits.startsWith("1") -> {
            "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
        }
        digits.length == 10 -> {
            "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        }
        else -> {
            if (number.startsWith("+") && digits.length == 11) {
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            } else {
                number
            }
        }
    }
}

@Composable
private fun ActivityRow(
    call: ScreenedCall,
    isBlocked: Boolean,
    isPro: Boolean,
    totalCalls: Int,
    blockedCalls: Int,
    allowedCalls: Int,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val carrier = CarrierResolver.resolve(call.number)
            val isSms = call.callType == "SMS"
            val typePrefix = if (isSms) "SMS: " else ""
            val formattedNumber = formatPhoneNumber(call.number)

            if (isPro && call.senderName != null) {
                Text(
                    text = "$typePrefix${call.senderName}",
                    color = AnswerGuardColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                val carrierText = if (carrier != null) " • $carrier" else ""
                Text(
                    text = "$formattedNumber$carrierText",
                    color = AnswerGuardColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    text = "$typePrefix$formattedNumber",
                    color = AnswerGuardColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val time = CallTimestampFormatter.format(call.timestamp)
            val eventTypeLabel = if (isSms) "Received" else "Called"
            val historyText = "$eventTypeLabel $totalCalls time${if (totalCalls > 1) "s" else ""} ($blockedCalls blocked, $allowedCalls allowed)"
            
            // Prepend carrier to metadata sub-line for non-Pro/non-name calls
            val carrierPrefix = if (!isPro || call.senderName == null) {
                if (carrier != null) "$carrier \u2022 " else ""
            } else ""

            Text(
                text = "$time \u2022 $carrierPrefix$historyText",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        val (label, color) = when {
            isBlocked -> "Blocked" to Color.Red
            call.source == CallSource.VOICEMAIL ->
                "Carrier voicemail" to AnswerGuardColors.Warning
            call.source == CallSource.SYSTEM_CALL_LOG &&
                call.verdict == SpamVerdict.SILENCE ->
                "Silenced (DND)" to AnswerGuardColors.Warning
            call.verdict == SpamVerdict.SILENCE -> "Silenced" to AnswerGuardColors.Warning
            else -> "Allowed" to AnswerGuardColors.Primary
        }

        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val actionLabel = if (isBlocked) "Unblock" else "Block"
        val actionColor = if (isBlocked) AnswerGuardColors.Primary else Color.Red

        Box(
            modifier = Modifier
                .background(actionColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .clickable { if (isBlocked) onUnblock() else onBlock() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionLabel,
                color = actionColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    callScreeningEnabled: Boolean,
    screeningPaused: Boolean,
    onTogglePause: () -> Unit,
    onManageBlocklist: () -> Unit,
    entitlementLevel: EntitlementLevel,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    proActionInProgress: Boolean,
    proStatusMessage: String?,
    onDeleteData: () -> Unit,
    showSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept Android's back gesture / button so swipe-back returns to the
    // home screen instead of closing the whole app. See BlocklistScreen for
    // the full rationale; this is the same fix.
    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnswerGuardColors.Background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = AnswerGuardColors.Primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Pause/resume the screener — only meaningful once it's the active
        // call-screening app, so gate the control on that.
        if (callScreeningEnabled) {
            SettingsSectionLabel("SCREENING")
            PauseScreeningCard(
                screeningPaused = screeningPaused,
                onTogglePause = onTogglePause,
            )
        }

        SettingsSectionLabel("BLOCKLIST")
        BlocklistCard(onClick = onManageBlocklist)

        SettingsSectionLabel("SUBSCRIPTION")
        ProCard(
            entitlementLevel = entitlementLevel,
            onUpgrade = onUpgrade,
            onRestore = onRestore,
            actionInProgress = proActionInProgress,
            statusMessage = proStatusMessage,
            showSnackbar = showSnackbar,
        )

        SettingsSectionLabel("DATA")
        PrivacyAndDataCard(onDeleteData = onDeleteData)

        SettingsSectionLabel("ABOUT")
        HowItWorks()
        PrivacyCard()

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = AnswerGuardColors.TextSecondary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PauseScreeningCard(
    screeningPaused: Boolean,
    onTogglePause: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (screeningPaused) "Screening paused" else "Screening active",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (screeningPaused)
                    "Incoming calls are not being screened. Resume to re-enable on-device protection."
                else
                    "Incoming calls are screened on-device against confirmed spam patterns.",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onTogglePause,
                colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.SurfaceMuted),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_pause_toggle"),
            ) {
                Text(
                    text = if (screeningPaused) "Resume screening" else "Pause screening",
                    color = AnswerGuardColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
@Composable
private fun PrivacyCard() {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Privacy first",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "AnswerGuard does not upload your call history. Screening decisions happen on-device.",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})  ${BuildConfig.GIT_SHA}",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("home_build_info"),
            )
            Text(
                text = "Package: ${context.packageName}",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

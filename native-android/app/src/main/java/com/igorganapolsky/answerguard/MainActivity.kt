package com.igorganapolsky.answerguard

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.igorganapolsky.answerguard.analytics.AnalyticsService
import com.igorganapolsky.answerguard.analytics.AnalyticsEvents
import com.igorganapolsky.answerguard.billing.ProManager
import com.igorganapolsky.answerguard.screening.RoleOnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var analyticsService: AnalyticsService
    @Inject lateinit var proManager: ProManager

    private var callScreeningEnabled by mutableStateOf(false)
    private var contactsPermissionGranted by mutableStateOf(false)
    private var proActionInProgress by mutableStateOf(false)
    private var proStatusMessage by mutableStateOf<String?>(null)

    private val roleRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
            if (callScreeningEnabled) {
                analyticsService.track(AnalyticsEvents.CALL_SCREENING_ENABLED)
                analyticsService.trackFirstProtectionEnabledIfNeeded()
            }
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            contactsPermissionGranted = isGranted
            if (isGranted) {
                analyticsService.track("contacts_permission_granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        refreshStatus()

        setContent {
            AnswerGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AnswerGuardColors.Background,
                ) {
                    AnswerGuardHome(
                        callScreeningEnabled = callScreeningEnabled,
                        contactsPermissionGranted = contactsPermissionGranted,
                        onEnable = ::requestCallScreeningRole,
                        onRefresh = ::refreshStatus,
                        onEnableContacts = ::requestContactsPermission,
                        onUpgrade = ::launchProPurchase,
                        onRestore = ::restorePurchases,
                        proActionInProgress = proActionInProgress,
                        proStatusMessage = proStatusMessage,
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
        refreshStatus()
    }

    private fun refreshStatus() {
        callScreeningEnabled =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                    roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            } else {
                false
            }
        contactsPermissionGranted = 
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        roleRequestLauncher.launch(Intent(this, RoleOnboardingActivity::class.java))
    }

    private fun requestContactsPermission() {
        contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
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

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        analyticsService.trackDeepLink(uri)
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

@Composable
private fun AnswerGuardHome(
    callScreeningEnabled: Boolean,
    contactsPermissionGranted: Boolean,
    onEnable: () -> Unit,
    onRefresh: () -> Unit,
    onEnableContacts: () -> Unit,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    proActionInProgress: Boolean,
    proStatusMessage: String?,
) {
    var showBlocklist by androidx.compose.runtime.remember { mutableStateOf(false) }

    if (showBlocklist) {
        BlocklistScreen(onBack = { showBlocklist = false })
    } else {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
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
                Header()
                StatusCard(callScreeningEnabled = callScreeningEnabled, onEnable = onEnable, onRefresh = onRefresh)
                ContactsCard(permissionGranted = contactsPermissionGranted, onEnable = onEnableContacts)
                BlocklistCard(onClick = { showBlocklist = true })
                ProCard(
                    onUpgrade = onUpgrade,
                    onRestore = onRestore,
                    actionInProgress = proActionInProgress,
                    statusMessage = proStatusMessage,
                )
                HowItWorks()
                PrivacyCard()
            }
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
private fun BlocklistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var numbers by androidx.compose.runtime.remember { mutableStateOf(com.igorganapolsky.answerguard.screening.UserBlocklist.getAll().toList()) }
    var newNumber by androidx.compose.runtime.remember { mutableStateOf("") }

    Column(
        modifier = Modifier
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
                    if (newNumber.isNotBlank()) {
                        com.igorganapolsky.answerguard.screening.UserBlocklist.add(newNumber.filter { it.isDigit() })
                        numbers = com.igorganapolsky.answerguard.screening.UserBlocklist.getAll().toList()
                        newNumber = ""
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
                                numbers = com.igorganapolsky.answerguard.screening.UserBlocklist.getAll().toList()
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
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    actionInProgress: Boolean,
    statusMessage: String?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AnswerGuardColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "AnswerGuard Pro",
                color = AnswerGuardColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Unlock advanced spam rules and family protection as they roll out.",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
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
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "AnswerGuard",
            color = AnswerGuardColors.TextPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Spam and scam call protection that runs locally on your phone.",
            color = AnswerGuardColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StatusCard(
    callScreeningEnabled: Boolean,
    onEnable: () -> Unit,
    onRefresh: () -> Unit,
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
                StatusDot(enabled = callScreeningEnabled)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Call Screening",
                        color = AnswerGuardColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (callScreeningEnabled) "Active" else "Not enabled",
                        color = if (callScreeningEnabled) AnswerGuardColors.Primary else AnswerGuardColors.Warning,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("home_call_screening_status"),
                    )
                }
            }

            Button(
                onClick = if (callScreeningEnabled) onRefresh else onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = AnswerGuardColors.Primary),
                modifier = Modifier.fillMaxWidth().testTag("home_enable_call_screening_button"),
            ) {
                Text(
                    text = if (callScreeningEnabled) "Refresh Status" else "Enable Call Screening",
                    color = Color(0xFF06211E),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
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
                text = "Package: ${context.packageName}",
                color = AnswerGuardColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

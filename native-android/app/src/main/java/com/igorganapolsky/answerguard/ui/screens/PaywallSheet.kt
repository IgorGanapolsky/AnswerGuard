package com.igorganapolsky.answerguard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.igorganapolsky.answerguard.billing.ProManager
import com.igorganapolsky.answerguard.billing.EntitlementLevel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.withTimeoutOrNull

internal const val HIDDEN_UNLOCK_HOLD_DURATION_MS = 8_000L

internal const val PAYWALL_HEADLINE = "Upgrade your AnswerGuard plan"
internal const val PAYWALL_SUBHEADLINE =
    "Pick the plan that fits how you want to be protected. Cancel anytime in Google Play."
internal const val PAYWALL_PRICING_FOOTER = "Cancel anytime. Subscription auto-renews until cancelled."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
    entryPoint: String = "unknown",
    familyPrice: String = "$29.99/yr",
    businessPrice: String = "$49.99/yr",
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    onSecretUnlock: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0B1014),
    ) {
        Scaffold(
            containerColor = Color(0xFF0B1014),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onPurchase(ProManager.ELITE_PRODUCT_ID) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Family Protection Plan \u2022 $familyPrice",
                            color = Color(0xFF06211E),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = { onPurchase(ProManager.BUSINESS_PRODUCT_ID) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF2DD4BF).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2DD4BF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Business Shield Plan \u2022 $businessPrice",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(onClick = { 
                        uriHandler.openUri("https://igorganapolsky.github.io/AnswerGuard/upgrade/")
                    }) {
                        Text("Direct Support (Web Checkout)", color = Color(0xFF2DD4BF), style = MaterialTheme.typography.labelMedium)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onRestore) {
                            Text("Restore purchase", color = Color(0xFFB6C2CC))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("|", color = Color(0xFF182229))
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onDismiss) {
                            Text("Not now", color = Color(0xFFB6C2CC))
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = PAYWALL_HEADLINE,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.then(
                        if (onSecretUnlock != null && ProManager.canUseDebugUnlock()) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = PAYWALL_SUBHEADLINE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB6C2CC),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                PlanComparisonCard()

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Available today",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FeatureRow(title = "Advanced spam rules", desc = "Heuristics tuned for 2026's spam landscape, plus priority routing for high-risk numbers.", planBadge = "ALL PAID PLANS")
                    FeatureRow(title = "Google Play family sharing", desc = "Share your subscription with up to 6 family members via Google Play.", planBadge = "FAMILY & BUSINESS")
                    FeatureRow(title = "Business-tuned rules + priority support", desc = "Stricter defaults for high-volume professional use plus direct support.", planBadge = "BUSINESS ONLY")

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Coming soon (not yet shipped)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB6C2CC),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FeatureRow(title = "On-device AI intent analysis", desc = "Gemini Nano decodes call intent in real-time.", planBadge = "ROADMAP")
                    FeatureRow(title = "Voice deepfake defense", desc = "Detect AI-cloned voices with local biometrics.", planBadge = "ROADMAP")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = PAYWALL_PRICING_FOOTER,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB6C2CC).copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB6C2CC),
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://igorganapolsky.github.io/AnswerGuard/privacy-policy/")
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Terms of Use",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB6C2CC),
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://igorganapolsky.github.io/AnswerGuard/eula/")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, desc: String, planBadge: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
                .background(Color(0xFF2DD4BF).copy(alpha = 0.1f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u2713",
                color = Color(0xFF2DD4BF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )
                if (planBadge != null) {
                    val isBusiness = "BUSINESS" in planBadge.uppercase()
                    Box(
                        modifier = Modifier
                            .background(
                                if (isBusiness) Color(0xFF2DD4BF).copy(alpha = 0.15f) else Color(0xFFB6C2CC).copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = planBadge,
                            color = if (isBusiness) Color(0xFF2DD4BF) else Color(0xFFB6C2CC),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB6C2CC)
            )
        }
    }
}

@Composable
private fun PlanComparisonCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111820)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Which plan is right for you?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("👨‍👩‍👧‍👦", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Family Protection ($29.99/yr)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB6C2CC)
                        )
                        Text(
                            text = "Ultimate security for up to 5 household devices. Protect loved ones from deepfake scams and voice cloning.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                
                HorizontalDivider(color = Color(0xFF1E293B))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text("💼", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Business Plan ($49.99/yr)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2DD4BF)
                        )
                        Text(
                            text = "Adds strict scam defense and B2B Compliance. Professional-grade call filtering and priority support for business owners.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

internal fun Modifier.holdForHiddenUnlock(
    holdDurationMs: Long,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onHoldComplete: () -> Unit,
): Modifier =
    pointerInput(holdDurationMs, onHoldComplete) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                val success =
                    withTimeoutOrNull(holdDurationMs) {
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.changedToUp() }) {
                                released = true
                            }
                        }
                        false // Released before timeout
                    } ?: true

                if (success) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHoldComplete()
                    // Wait for the final up event before allowing next hold
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.changedToUp() }) break
                    }
                }
            }
        }
    }

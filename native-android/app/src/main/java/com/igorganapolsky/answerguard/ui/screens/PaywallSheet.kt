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

internal const val PAYWALL_HEADLINE = "Upgrade to Family Protection"
internal const val PAYWALL_SUBHEADLINE =
    "Unlock on-device AI intent analysis, voice biometrics, and multi-device coverage for your whole household."
internal const val PAYWALL_PRICING_FOOTER = "Cancel anytime. Subscription auto-renews until cancelled."
internal val PAYWALL_FEATURE_ROWS =
    listOf(
        "Gemini-powered intent analysis of unknown callers",
        "Voice biometrics to detect AI-generated voice clones",
        "Multi-device coverage (up to 5 household members)",
        "Priority updates for local spam reputation data",
        "Advanced blocklist rules (prefix/wildcard matching)",
        "Premium support for family security setup",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
    entryPoint: String = "unknown",
    proPrice: String = "$29.99/yr",
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

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
                            text = "Start Family Plan \u2022 $proPrice",
                            color = Color(0xFF06211E),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
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
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = PAYWALL_SUBHEADLINE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB6C2CC),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PAYWALL_FEATURE_ROWS.forEach { feature ->
                        FeatureRow(text = feature)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

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
private fun FeatureRow(text: String) {
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
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF8FAFC)
        )
    }
}

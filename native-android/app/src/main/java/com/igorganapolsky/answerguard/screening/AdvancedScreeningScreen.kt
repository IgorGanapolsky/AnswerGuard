package com.igorganapolsky.answerguard.screening

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreeningScreen(
    viewModel: ScreeningViewModel,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Protection") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B1014),
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF2DD4BF)
            ) {
                Text("+", color = Color(0xFF06211E), style = MaterialTheme.typography.headlineMedium)
            }
        },
        containerColor = Color(0xFF0B1014)
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Using a simple list toggle for now
            var showHistory by remember { mutableStateOf(true) }
            
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = showHistory,
                    onClick = { showHistory = true },
                    label = { Text("Recent Activity") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White,
                        selectedLabelColor = Color(0xFF06211E),
                        selectedContainerColor = Color(0xFF2DD4BF)
                    )
                )
                FilterChip(
                    selected = !showHistory,
                    onClick = { showHistory = false },
                    label = { Text("Personal Blocklist") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White,
                        selectedLabelColor = Color(0xFF06211E),
                        selectedContainerColor = Color(0xFF2DD4BF)
                    )
                )
            }

            if (showHistory) {
                ActivityList(viewModel.recentEvents)
            } else {
                BlocklistContent(viewModel.blockedNumbers, onRemove = { viewModel.removeBlockedNumber(it) })
            }
        }
    }

    if (showAddDialog) {
        AddNumberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { 
                viewModel.addBlockedNumber(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ActivityList(events: List<ScreeningEvent>) {
    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recent calls screened", color = Color(0xFFB6C2CC))
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(events) { event ->
                ActivityItem(event)
            }
        }
    }
}

@Composable
fun ActivityItem(event: ScreeningEvent) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val date = dateFormat.format(Date(event.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.phoneNumber, color = Color.White, fontWeight = FontWeight.Bold)
                Text(event.reason, color = Color(0xFFB6C2CC), style = MaterialTheme.typography.bodySmall)
                Text(date, color = Color(0xFFB6C2CC), style = MaterialTheme.typography.labelSmall)
            }
            VerdictBadge(event.verdict)
        }
    }
}

@Composable
fun VerdictBadge(verdict: ScreeningVerdict) {
    val (color, label) = when (verdict) {
        ScreeningVerdict.BLOCKED -> Color(0xFFEF4444) to "BLOCKED"
        ScreeningVerdict.SILENCED -> Color(0xFFF59E0B) to "SILENCED"
        ScreeningVerdict.ALLOWED -> Color(0xFF10B981) to "ALLOWED"
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BlocklistContent(numbers: Set<String>, onRemove: (String) -> Unit) {
    if (numbers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Your blocklist is empty", color = Color(0xFFB6C2CC))
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(numbers.toList()) { number ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A20)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(number, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(number) }) {
                            Text("Delete", color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddNumberDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Number", color = Color.White) },
        text = {
            TextField(
                value = number,
                onValueChange = { number = it },
                placeholder = { Text("e.g. +18005550199") },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF182229),
                    unfocusedContainerColor = Color(0xFF182229)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(number) }) {
                Text("Block", color = Color(0xFF2DD4BF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF121A20)
    )
}

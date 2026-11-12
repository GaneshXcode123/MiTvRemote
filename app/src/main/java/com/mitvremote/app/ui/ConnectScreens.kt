package com.mitvremote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitvremote.app.PairStage
import com.mitvremote.app.UiState
import com.mitvremote.app.tv.TvDevice

@Composable
fun ConnectScreen(
    ui: UiState,
    onPick: (TvDevice) -> Unit,
    onRepair: (TvDevice) -> Unit,
    onManual: (String) -> Unit,
    onRescan: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    var ip by remember { mutableStateOf("") }
    val savedHosts = ui.saved.map { it.host }.toSet()
    val found = ui.devices.filter { it.host !in savedHosts }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(32.dp))
            Text("Mi TV Remote", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MiOrange)
            Text(
                "Phone and TV must be on the same Wi-Fi, and the TV switched on.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.message != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(ui.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onDismissMessage) { Text("Dismiss") }
                    }
                }
            }
        }
        if (ui.saved.isNotEmpty()) {
            item { Text("My TVs", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
            items(ui.saved, key = { "s" + it.host }) { tv ->
                TvRow(tv, onClick = { onPick(tv) }, trailing = {
                    TextButton(onClick = { onRepair(tv) }) { Text("Re-pair") }
                })
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("TVs found on your Wi-Fi", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (ui.scanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRescan) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Scan again")
                    }
                }
            }
        }
        if (found.isEmpty()) {
            item {
                Text(
                    if (ui.scanning) "Searching…" else "No new TV found. Enter its IP address below.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(found, key = { "d" + it.host }) { tv ->
            TvRow(tv, onClick = { onPick(tv) }, trailing = {})
        }
        item {
            Text("Connect by IP address", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text(
                "On the TV: Settings → Network → your Wi-Fi → IP address",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("e.g. 192.168.1.25") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onManual(ip) }, enabled = ip.isNotBlank()) { Text("Connect") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TvRow(tv: TvDevice, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tv, contentDescription = null, tint = MiOrange)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tv.name, fontWeight = FontWeight.SemiBold)
                Text(tv.host, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            trailing()
        }
    }
}

@Composable
fun PairScreen(
    ui: UiState,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pair with ${ui.pairTv?.name ?: "TV"}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        when (ui.pairStage) {
            PairStage.STARTING -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Contacting the TV…")
            }
            PairStage.ENTER_CODE, PairStage.VERIFYING -> {
                Text(
                    "A 6-character code is now showing on your TV screen. Type it here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                    },
                    singleLine = true,
                    enabled = ui.pairStage == PairStage.ENTER_CODE,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 6.sp),
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                if (ui.message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(ui.message, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                if (ui.pairStage == PairStage.VERIFYING) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = { onSubmit(code) }, enabled = code.length == 6) { Text("Pair") }
                }
            }
            PairStage.FAILED -> {
                Text(ui.message ?: "Pairing failed", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { code = ""; onRetry() }) { Text("Try again") }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

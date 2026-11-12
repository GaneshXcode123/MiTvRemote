package com.mitvremote.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mitvremote.app.Link
import com.mitvremote.app.UiState
import com.mitvremote.app.tv.Apps
import com.mitvremote.app.tv.Key
import com.mitvremote.app.tv.TvApp
import kotlin.math.abs

private enum class Tab(val label: String, val icon: ImageVector) {
    REMOTE("Remote", Icons.Filled.Tv),
    TOUCHPAD("Touchpad", Icons.Filled.TouchApp),
    KEYBOARD("Keyboard", Icons.Filled.Keyboard),
    APPS("Apps", Icons.Filled.Apps),
}

@Composable
fun RemoteScreen(
    ui: UiState,
    onKey: (Int) -> Unit,
    onText: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onReconnect: () -> Unit,
    onSwitchTv: () -> Unit,
    onRepair: () -> Unit,
    onVolumeKeys: (Boolean) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.REMOTE) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dot = when (ui.link) {
                    Link.CONNECTED -> Color(0xFF4CAF50)
                    Link.CONNECTING -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(ui.tvName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val sub = when (ui.link) {
                        Link.CONNECTED -> ui.volume ?: "Connected"
                        Link.CONNECTING -> "Connecting…"
                        else -> "Not connected"
                    }
                    Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.link == Link.LOST) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ui.message ?: "Disconnected", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = onReconnect) { Text("Reconnect") }
                }
            } else if (ui.message != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ui.message, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = onDismissMessage) { Text("OK") }
                }
            }
            when (tab) {
                Tab.REMOTE -> RemoteTab(ui, onKey, onLaunch, onVoiceStart, onVoiceStop)
                Tab.TOUCHPAD -> TouchpadTab(onKey)
                Tab.KEYBOARD -> KeyboardTab(onKey, onText, onVoiceStart, onVoiceStop, ui.voiceActive)
                Tab.APPS -> AppsTab(onLaunch)
            }
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Close") } },
            title = { Text("Settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Phone volume buttons control the TV", modifier = Modifier.weight(1f))
                        Switch(checked = ui.volumeKeys, onCheckedChange = onVolumeKeys)
                    }
                    TextButton(onClick = { showMenu = false; onReconnect() }) { Text("Reconnect") }
                    TextButton(onClick = { showMenu = false; onSwitchTv() }) { Text("Switch TV") }
                    TextButton(onClick = { showMenu = false; onRepair() }) { Text("Re-pair this TV") }
                }
            },
        )
    }
}

// ---- Remote tab (mirrors the physical Mi remote) ---------------------------------------------

@Composable
private fun MicKey(
    active: Boolean,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val start by rememberUpdatedState(onVoiceStart)
    val stop by rememberUpdatedState(onVoiceStop)
    val bg = if (active) MiOrange else MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).pointerInput(Unit) {
            detectTapGestures(onPress = {
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    start()
                    tryAwaitRelease()
                    stop()
                }
            })
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Hold to talk", modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
private fun RemoteTab(
    ui: UiState,
    onKey: (Int) -> Unit,
    onLaunch: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Power + Assistant (hold to talk), like the top of the real remote
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            IconKey(Icons.Filled.PowerSettingsNew, "Power", color = Color(0xFF5A1F1F), tint = Color(0xFFFF8A80)) {
                onKey(Key.POWER)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MicKey(ui.voiceActive, onVoiceStart, onVoiceStop)
                Text(
                    if (ui.voiceActive) "Listening…" else "Hold to talk",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconKey(Icons.Filled.VolumeOff, "Mute") { onKey(Key.MUTE) }
        }

        // D-pad ring with OK in the middle
        Box(
            Modifier.size(260.dp).clip(CircleShape).background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            PressButton(
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(76.dp),
                color = Color.Transparent, repeat = true, onPress = { onKey(Key.UP) },
            ) { Icon(Icons.Filled.KeyboardArrowUp, "Up", modifier = Modifier.size(42.dp)) }
            PressButton(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(76.dp),
                color = Color.Transparent, repeat = true, onPress = { onKey(Key.DOWN) },
            ) { Icon(Icons.Filled.KeyboardArrowUp, "Down", modifier = Modifier.size(42.dp).rotate(180f)) }
            PressButton(
                Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(76.dp),
                color = Color.Transparent, repeat = true, onPress = { onKey(Key.LEFT) },
            ) { Icon(Icons.Filled.KeyboardArrowUp, "Left", modifier = Modifier.size(42.dp).rotate(270f)) }
            PressButton(
                Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(76.dp),
                color = Color.Transparent, repeat = true, onPress = { onKey(Key.RIGHT) },
            ) { Icon(Icons.Filled.KeyboardArrowUp, "Right", modifier = Modifier.size(42.dp).rotate(90f)) }
            PressButton(
                Modifier.size(104.dp), color = scheme.surface, onPress = { onKey(Key.CENTER) },
            ) { Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }

        // Menu, Back, Home
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            IconKey(Icons.Filled.Menu, "Menu") { onKey(Key.MENU) }
            IconKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onKey(Key.BACK) }
            IconKey(Icons.Filled.Home, "Home") { onKey(Key.HOME) }
        }

        // Netflix / Prime Video shortcuts (same as the physical buttons)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextKey("NETFLIX", width = 140.dp, color = Color(0xFFE50914), textColor = Color.White) {
                onLaunch(Apps.NETFLIX.id)
            }
            TextKey("prime video", width = 140.dp, color = Color(0xFF00A8E1), textColor = Color.White) {
                onLaunch(Apps.PRIME.id)
            }
        }

        // Volume
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconKey(Icons.Filled.Remove, "Volume down", size = 64.dp, repeat = true) { onKey(Key.VOLUME_DOWN) }
            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = scheme.onSurfaceVariant)
            IconKey(Icons.Filled.Add, "Volume up", size = 64.dp, repeat = true) { onKey(Key.VOLUME_UP) }
        }

        // Media controls
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconKey(Icons.Filled.SkipPrevious, "Previous", size = 48.dp) { onKey(Key.PREVIOUS) }
            IconKey(Icons.Filled.FastRewind, "Rewind", size = 48.dp) { onKey(Key.REWIND) }
            IconKey(Icons.Filled.PlayArrow, "Play/Pause", size = 48.dp) { onKey(Key.PLAY_PAUSE) }
            IconKey(Icons.Filled.FastForward, "Fast forward", size = 48.dp) { onKey(Key.FAST_FORWARD) }
            IconKey(Icons.Filled.SkipNext, "Next", size = 48.dp) { onKey(Key.NEXT) }
        }

        // Extras
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextKey("Settings", width = 96.dp, height = 44.dp) { onKey(Key.SETTINGS) }
            TextKey("Input", width = 80.dp, height = 44.dp) { onKey(Key.TV_INPUT) }
            TextKey("Info", width = 72.dp, height = 44.dp) { onKey(Key.INFO) }
            TextKey("Guide", width = 80.dp, height = 44.dp) { onKey(Key.GUIDE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextKey("CH +", width = 80.dp, height = 44.dp) { onKey(Key.CHANNEL_UP) }
            TextKey("CH −", width = 80.dp, height = 44.dp) { onKey(Key.CHANNEL_DOWN) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---- Touchpad tab ---------------------------------------------------------------------------

@Composable
private fun TouchpadTab(onKey: (Int) -> Unit) {
    val key by rememberUpdatedState(onKey)
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) { detectTapGestures(onTap = { key(Key.CENTER) }) }
                .pointerInput(Unit) {
                    var ax = 0f
                    var ay = 0f
                    val step = 70f
                    detectDragGestures(
                        onDragStart = { ax = 0f; ay = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            ax += drag.x
                            ay += drag.y
                            if (abs(ax) >= abs(ay)) {
                                if (ax > step) { key(Key.RIGHT); ax = 0f; ay = 0f }
                                else if (ax < -step) { key(Key.LEFT); ax = 0f; ay = 0f }
                            } else {
                                if (ay > step) { key(Key.DOWN); ax = 0f; ay = 0f }
                                else if (ay < -step) { key(Key.UP); ax = 0f; ay = 0f }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Swipe to move\nTap = OK",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onKey(Key.BACK) }
            IconKey(Icons.Filled.Home, "Home") { onKey(Key.HOME) }
            IconKey(Icons.Filled.Menu, "Menu") { onKey(Key.MENU) }
            IconKey(Icons.Filled.Remove, "Volume down", repeat = true) { onKey(Key.VOLUME_DOWN) }
            IconKey(Icons.Filled.Add, "Volume up", repeat = true) { onKey(Key.VOLUME_UP) }
        }
    }
}

// ---- Keyboard tab ---------------------------------------------------------------------------

@Composable
private fun KeyboardTab(
    onKey: (Int) -> Unit,
    onText: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    voiceActive: Boolean,
) {
    var text by remember { mutableStateOf("") }
    var live by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "On the TV, open a search box or any text field first, then type here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = live, onCheckedChange = { live = it; text = "" })
            Text("  Type live (each letter goes to the TV)")
        }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                if (live) {
                    if (new.startsWith(text) && new.length > text.length) {
                        onText(new.substring(text.length))
                    } else if (text.startsWith(new) && new.length < text.length) {
                        repeat(text.length - new.length) { onKey(Key.DEL) }
                    }
                }
                text = new
            },
            label = { Text(if (live) "Type here" else "Write, then press Send") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (live) onKey(Key.ENTER) else { onText(text); text = "" }
            }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!live) {
                Button(onClick = { onText(text); text = "" }, enabled = text.isNotEmpty()) { Text("Send") }
            }
            TextKey("Enter", width = 90.dp) { onKey(Key.ENTER) }
            TextKey("Delete", width = 90.dp) { onKey(Key.DEL) }
            TextKey("Space", width = 90.dp) { onKey(Key.SPACE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MicKey(voiceActive, onVoiceStart, onVoiceStop, size = 64.dp)
            Text(
                if (voiceActive) "Listening… speak to the TV" else "Hold the mic and speak (TV voice search)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Apps tab -------------------------------------------------------------------------------

@Composable
private fun AppsTab(onLaunch: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(Apps.all, key = { it.id }) { app -> AppTile(app, onLaunch) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Open any app or link", fontWeight = FontWeight.SemiBold)
                Text(
                    "Type an app package name (e.g. in.startv.hotstar) or a web link.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Package or link") },
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { onLaunch(custom) }, enabled = custom.isNotBlank()) { Text("Open") }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: TvApp, onLaunch: (String) -> Unit) {
    PressButton(
        Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onPress = { onLaunch(app.id) },
    ) {
        Text(app.name, fontWeight = FontWeight.SemiBold)
    }
}

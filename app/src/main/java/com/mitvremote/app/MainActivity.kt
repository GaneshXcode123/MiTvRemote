package com.mitvremote.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitvremote.app.tv.Key
import com.mitvremote.app.tv.TvDevice
import com.mitvremote.app.ui.ConnectScreen
import com.mitvremote.app.ui.MiTheme
import com.mitvremote.app.ui.PairScreen
import com.mitvremote.app.ui.RemoteScreen

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    // Phone volume buttons -> TV volume (can be switched off in settings)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (vm.captureVolumeKeys()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                vm.key(Key.VOLUME_UP)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                vm.key(Key.VOLUME_DOWN)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (vm.captureVolumeKeys() &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) return true
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun AppRoot(vm: RemoteViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    BackHandler(enabled = ui.screen == Screen.PAIR) { vm.cancelPairing() }

    when (ui.screen) {
        Screen.CONNECT -> {
            LaunchedEffect(Unit) { vm.startDiscovery() }
            DisposableEffect(Unit) { onDispose { vm.stopDiscovery() } }
            ConnectScreen(
                ui = ui,
                onPick = vm::pick,
                onRepair = vm::forgetAndRepair,
                onManual = vm::connectManual,
                onRescan = vm::rescan,
                onDismissMessage = vm::dismissMessage,
            )
        }
        Screen.PAIR -> PairScreen(
            ui = ui,
            onSubmit = vm::submitCode,
            onRetry = { ui.pairTv?.let { vm.startPairing(it) } },
            onCancel = vm::cancelPairing,
        )
        Screen.REMOTE -> RemoteScreen(
            ui = ui,
            onKey = vm::key,
            onText = vm::typeText,
            onLaunch = vm::launch,
            onVoiceStart = vm::startVoice,
            onVoiceStop = vm::stopVoice,
            onReconnect = vm::reconnect,
            onSwitchTv = vm::switchTv,
            onRepair = { vm.forgetAndRepair(TvDevice(ui.tvHost, ui.tvName)) },
            onVolumeKeys = vm::setVolumeKeys,
            onDismissMessage = vm::dismissMessage,
        )
    }
}

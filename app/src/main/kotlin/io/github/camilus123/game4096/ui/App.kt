package io.github.camilus123.game4096.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import io.github.camilus123.game4096.data.ThemeChoice
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.camilus123.game4096.game.GameViewModel
import io.github.camilus123.game4096.game.Screen

@Composable
fun App(vm: GameViewModel = viewModel()) {
    val dark = when (vm.settings.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    // Status and navigation bar icons must contrast with our theme, which may differ from the phone's.
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(dark, activity) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }

    // The Time attack clock runs only while the app is on screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.setForeground(false) }

    GameTheme(dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            when (vm.screen) {
                Screen.GAME -> {
                    BackHandler { vm.showMenu() }
                    if (vm.match != null) {
                        VersusScreen(vm = vm, dark = dark, onMenu = vm::showMenu)
                    } else {
                        GameScreen(vm = vm, dark = dark, onMenu = vm::showMenu)
                    }
                }
                Screen.SCORES -> {
                    BackHandler { vm.showMenu() }
                    ScoresScreen(vm = vm, onBack = vm::showMenu)
                }
                Screen.SETTINGS -> {
                    BackHandler { vm.showMenu() }
                    SettingsScreen(vm = vm, onBack = vm::showMenu)
                }
                Screen.MENU -> MenuScreen(vm)
            }
        }
    }
}

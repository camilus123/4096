package io.github.camilus123.game4096.ui

import androidx.activity.compose.BackHandler
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
    val dark = isSystemInDarkTheme()

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
                    GameScreen(vm = vm, dark = dark, onMenu = vm::showMenu)
                }
                Screen.SCORES -> {
                    BackHandler { vm.showMenu() }
                    ScoresScreen(vm = vm, onBack = vm::showMenu)
                }
                Screen.MENU -> MenuScreen(
                    gridSize = vm.gridSize,
                    rules = vm.rules,
                    hasSavedGame = vm.hasSavedGame(vm.gridSize, vm.rules),
                    onGridSize = vm::selectGrid,
                    onRules = vm::selectRules,
                    onPlay = vm::play,
                    onScores = vm::showScores,
                )
            }
        }
    }
}

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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.camilus123.game4096.game.GameViewModel
import io.github.camilus123.game4096.game.Screen

@Composable
fun App(vm: GameViewModel = viewModel()) {
    val dark = isSystemInDarkTheme()

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
                    hasSavedGame = vm.hasSavedGame(vm.gridSize),
                    onGridSize = vm::selectGrid,
                    onPlay = vm::play,
                    onScores = vm::showScores,
                )
            }
        }
    }
}

package io.github.camilus123.game4096.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.camilus123.game4096.game.GameViewModel

@Composable
fun App(vm: GameViewModel = viewModel()) {
    val dark = isSystemInDarkTheme()
    var inGame by rememberSaveable { mutableStateOf(false) }
    var gridSize by rememberSaveable { mutableIntStateOf(4) }

    GameTheme(dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            if (inGame && vm.game != null) {
                BackHandler { inGame = false }
                GameScreen(vm = vm, dark = dark, onMenu = { inGame = false })
            } else {
                MenuScreen(
                    gridSize = gridSize,
                    onGridSize = { gridSize = it },
                    onPlay = {
                        vm.newGame(gridSize)
                        inGame = true
                    },
                )
            }
        }
    }
}

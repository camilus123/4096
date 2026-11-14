package io.github.camilus123.game4096.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.data.HapticLevel
import io.github.camilus123.game4096.MainActivity
import io.github.camilus123.game4096.data.Languages
import io.github.camilus123.game4096.data.QualityChoice
import io.github.camilus123.game4096.data.ThemeChoice
import io.github.camilus123.game4096.game.GameViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: GameViewModel, onBack: () -> Unit) {
    val settings = vm.settings
    val activity = LocalActivity.current
    val language = Languages.current(LocalContext.current)
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings),
                modifier = Modifier.weight(1f),
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                color = scheme.onBackground,
            )
            QuietButton(onClick = onBack) { Text(stringResource(R.string.menu)) }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Heading(stringResource(R.string.settings_sound))
            VolumeRow(
                label = stringResource(R.string.settings_effects),
                value = settings.effectsVolume,
                enabled = !settings.muted,
                onChange = { vm.updateSettings(settings.copy(effectsVolume = it), tap = false) },
                onDone = vm::previewEffects,
            )
            VolumeRow(
                label = stringResource(R.string.settings_music),
                value = settings.musicVolume,
                enabled = !settings.muted,
                onChange = { vm.updateSettings(settings.copy(musicVolume = it), tap = false) },
                onDone = {},
            )
            SwitchRow(stringResource(R.string.settings_mute), settings.muted) {
                vm.updateSettings(settings.copy(muted = it))
            }

            Heading(stringResource(R.string.settings_haptics))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Option(stringResource(R.string.settings_off), settings.haptics == HapticLevel.OFF) {
                    vm.setHaptics(HapticLevel.OFF)
                }
                Option(stringResource(R.string.settings_normal), settings.haptics == HapticLevel.NORMAL) {
                    vm.setHaptics(HapticLevel.NORMAL)
                }
                Option(stringResource(R.string.settings_high), settings.haptics == HapticLevel.HIGH) {
                    vm.setHaptics(HapticLevel.HIGH)
                }
            }

            Heading(stringResource(R.string.settings_graphics))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Auto shows the level it has settled on for this phone.
                val autoLabel = stringResource(R.string.quality_auto) + "\n(" + stringResource(qualityNameRes(vm.autoQuality)) + ")"
                Option(autoLabel, settings.quality == QualityChoice.AUTO) {
                    vm.updateSettings(settings.copy(quality = QualityChoice.AUTO))
                }
                Option(stringResource(R.string.quality_low), settings.quality == QualityChoice.LOW) {
                    vm.updateSettings(settings.copy(quality = QualityChoice.LOW))
                }
                Option(stringResource(R.string.quality_medium), settings.quality == QualityChoice.MEDIUM) {
                    vm.updateSettings(settings.copy(quality = QualityChoice.MEDIUM))
                }
                Option(stringResource(R.string.quality_high), settings.quality == QualityChoice.HIGH) {
                    vm.updateSettings(settings.copy(quality = QualityChoice.HIGH))
                }
            }
            Spacer(Modifier.height(6.dp))
            if ((activity as? MainActivity)?.supportsHighRefreshRate() != false) {
                SwitchRow(stringResource(R.string.settings_limit_fps), settings.limitFps) {
                    vm.updateSettings(settings.copy(limitFps = it))
                }
            }
            SwitchRow(stringResource(R.string.settings_frame_stats), settings.showFrameStats) {
                vm.updateSettings(settings.copy(showFrameStats = it))
            }
            Spacer(Modifier.height(6.dp))
            QuietButton(onClick = vm::showBenchmark, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.benchmark_title))
            }

            Heading(stringResource(R.string.settings_theme))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Option(stringResource(R.string.settings_system), settings.theme == ThemeChoice.SYSTEM) {
                    vm.updateSettings(settings.copy(theme = ThemeChoice.SYSTEM))
                }
                Option(stringResource(R.string.settings_light), settings.theme == ThemeChoice.LIGHT) {
                    vm.updateSettings(settings.copy(theme = ThemeChoice.LIGHT))
                }
                Option(stringResource(R.string.settings_dark), settings.theme == ThemeChoice.DARK) {
                    vm.updateSettings(settings.copy(theme = ThemeChoice.DARK))
                }
            }

            Heading(stringResource(R.string.settings_language))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Language names are shown in their own language, as is usual for a language picker.
                Option(stringResource(R.string.settings_system), language == Languages.SYSTEM) {
                    activity?.let { Languages.set(it, Languages.SYSTEM) }
                }
                Option("English", language == "en") {
                    activity?.let { Languages.set(it, "en") }
                }
                Option("Slovenščina", language == "sl") {
                    activity?.let { Languages.set(it, "sl") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun VolumeRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(0.9f), fontSize = 17.sp, color = scheme.onBackground)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            enabled = enabled,
            modifier = Modifier.weight(1.6f),
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outline.copy(alpha = 0.45f),
            ),
        )
        Text(
            text = (value * 100).roundToInt().toString(),
            modifier = Modifier.padding(start = 10.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f), fontSize = 17.sp, color = scheme.onBackground)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = scheme.primary,
                uncheckedThumbColor = scheme.outline,
                uncheckedTrackColor = scheme.background,
                uncheckedBorderColor = scheme.outline,
            ),
        )
    }
}

@Composable
private fun RowScope.Option(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primary else scheme.background,
        contentColor = if (selected) scheme.onPrimary else scheme.onBackground,
        border = BorderStroke(2.dp, if (selected) scheme.primary else scheme.outline),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp).fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

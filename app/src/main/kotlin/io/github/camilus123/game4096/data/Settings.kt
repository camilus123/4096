package io.github.camilus123.game4096.data

enum class ThemeChoice(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun parse(id: String?): ThemeChoice = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class HapticLevel(val id: String) {
    OFF("off"),
    NORMAL("normal"),
    HIGH("high");

    companion object {
        fun parse(id: String?): HapticLevel? = entries.firstOrNull { it.id == id }
    }
}

/** The player's settings. Immutable: change one with [copy] and hand it back to the view model. */
data class Settings(
    /** 0 to 1. */
    val effectsVolume: Float = 0.5f,
    /** 0 to 1. */
    val musicVolume: Float = 0.5f,
    val muted: Boolean = false,
    val haptics: HapticLevel = HapticLevel.NORMAL,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
)
